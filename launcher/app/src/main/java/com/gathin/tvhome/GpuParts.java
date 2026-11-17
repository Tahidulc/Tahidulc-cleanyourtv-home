package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of a picture of the screen made small by the graphics chip (ScreenShots), with one renderer kept for each
 * size of part and used again for every picture, instead of a new one each time.
 *
 * <p>Before (to 5.33), every part went through a Picture and Bitmap.createBitmap(Picture, ..., HARDWARE): each part
 * started a renderer of its own for one frame (a render node, a drawing context, an image reader and its buffers, a
 * surface for the graphics chip), read the result back, and threw it all away, up to eight times a second while the
 * scoreboard and the logo's cover were over a channel. The Picture could only be let go by Java's finalizer, and until
 * then it held the whole picture of the screen it was cut from (8 MB at 1080p). Here a part is drawn by a renderer kept
 * for its size into an image reader kept with it, read straight off the reader's image (closed at once), and the
 * renderers not used for half a minute are let go (MemoryWatch asks too, on a memory warning). Android 10 and later;
 * ScreenShots itself needs Android 11. All on ScreenShots' own thread.
 */
@android.annotation.TargetApi(30)
final class GpuParts {
    private GpuParts() { }

    /** Sizes kept at once (see-through, the ad-break screen, the channel's scoreboard, the whole picture small). */
    private static final int MOST = 4;
    /** A renderer not used this long is let go. */
    static final long IDLE_MS = 30_000L;
    /** Failures running before the old way is used for the rest of the process's life. */
    private static final int GIVE_UP = 3;

    private static final class Slot {
        final int w, h;
        final ImageReader reader;
        final RenderNode node;
        final HardwareRenderer renderer;
        long usedAt;

        Slot(int w, int h) {
            this.w = w;
            this.h = h;
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            node = new RenderNode("ScreenShotsPart");
            node.setPosition(0, 0, w, h);
            renderer = new HardwareRenderer();
            renderer.setName("ScreenShotsPart");
            renderer.setOpaque(false);
            renderer.setLightSourceAlpha(0f, 0f);
            renderer.setContentRoot(node);
            renderer.setSurface(reader.getSurface());
        }

        void release() {
            try { renderer.destroy(); } catch (RuntimeException ignored) { }
            try { node.discardDisplayList(); } catch (RuntimeException ignored) { }
            try { reader.close(); } catch (RuntimeException ignored) { }
        }
    }

    private static final LinkedHashMap<Long, Slot> slots = new LinkedHashMap<>(8, 0.75f, true);
    private static int fails;
    private static volatile boolean off;
    /** Parts made this way since the start (the self-check counts them a minute at a time). */
    static volatile long made;
    private static volatile int held;

    /** Whether the old way is used now (this way failed too often here). */
    static boolean off() {
        return off;
    }

    /** Renderers held now. */
    static int held() {
        return held;
    }

    /**
     * src of hw (a picture of the screen, a HARDWARE bitmap) drawn ow by oh and read back as an ordinary bitmap; null when
     * it could not be made this way (the caller then makes it the old way). ScreenShots' thread only.
     */
    static synchronized Bitmap part(Bitmap hw, Rect src, int ow, int oh, Paint paint) {
        if (off || hw == null || ow < 1 || oh < 1) return null;
        Slot s = null;
        try {
            s = slot(ow, oh);
            RecordingCanvas c = s.node.beginRecording(ow, oh);
            try {
                c.drawBitmap(hw, src, new Rect(0, 0, ow, oh), paint);
            } finally {
                s.node.endRecording();
            }
            s.renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw();
            Image img = acquire(s.reader);
            if (img == null) throw new IllegalStateException("no image came back");
            Bitmap out;
            try {
                out = copy(img, ow, oh);
            } finally {
                img.close();
            }
            if (out == null) throw new IllegalStateException("the image could not be read");
            fails = 0;
            made++;
            return out;
        } catch (RuntimeException | OutOfMemoryError e) {
            if (s != null) {
                slots.values().remove(s);
                s.release();
            }
            if (++fails >= GIVE_UP) {
                off = true;
                releaseAll();
                android.util.Log.w("ScreenShots", "parts on the graphics chip failed " + fails + " times: the old way from now on (" + e + ")");
            }
            return null;
        } finally {
            if (s != null) s.usedAt = SystemClock.uptimeMillis();
            held = slots.size();
        }
    }

    private static Slot slot(int w, int h) {
        long key = ((long) w << 32) | (h & 0xFFFFFFFFL);
        Slot s = slots.get(key);
        if (s != null) return s;
        while (slots.size() >= MOST) {
            Iterator<Slot> it = slots.values().iterator();
            Slot eldest = it.next();
            it.remove();
            eldest.release();
        }
        s = new Slot(w, h);
        slots.put(key, s);
        return s;
    }

    /** The reader's newest image (the frame just drawn): waited for a moment if it is not there yet. */
    private static Image acquire(ImageReader r) {
        for (int i = 0; i < 20; i++) {
            Image img = r.acquireLatestImage();
            if (img != null) return img;
            SystemClock.sleep(5);
        }
        return null;
    }

    /** The image's pixels (RGBA, as an ARGB_8888 bitmap holds them) into a new bitmap; null when they cannot be read. */
    private static Bitmap copy(Image img, int w, int h) {
        Image.Plane[] planes = img.getPlanes();
        if (planes == null || planes.length == 0) return null;
        Image.Plane p = planes[0];
        if (p.getPixelStride() != 4) return null;
        ByteBuffer buf = p.getBuffer();
        int rowStride = p.getRowStride(), row = w * 4;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        buf.rewind();
        if (rowStride == row && buf.remaining() >= row * h) {
            out.copyPixelsFromBuffer(buf);
            return out;
        }
        ByteBuffer packed = ByteBuffer.allocate(row * h);
        byte[] line = new byte[row];
        for (int y = 0; y < h; y++) {
            buf.position(y * rowStride);
            buf.get(line, 0, row);
            packed.put(line);
        }
        packed.rewind();
        out.copyPixelsFromBuffer(packed);
        return out;
    }

    /** The renderers not used for IDLE_MS let go; true while any is still held. ScreenShots' thread. */
    static synchronized boolean releaseIdle(long now) {
        List<Slot> gone = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Slot>> it = slots.entrySet().iterator(); it.hasNext(); ) {
            Slot s = it.next().getValue();
            if (now - s.usedAt >= IDLE_MS) {
                it.remove();
                gone.add(s);
            }
        }
        for (Slot s : gone) s.release();
        held = slots.size();
        return !slots.isEmpty();
    }

    /** Every renderer let go (a memory warning, or this way given up). ScreenShots' thread. */
    static synchronized void releaseAll() {
        for (Slot s : slots.values()) s.release();
        slots.clear();
        held = 0;
    }
}
