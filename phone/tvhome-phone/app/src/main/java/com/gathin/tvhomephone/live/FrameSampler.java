package com.gathin.tvhomephone.live;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

/**
 * The picture's frames, copied small from the player's own surface (PixelCopy: the video alone, never what is drawn
 * over it, so the scoreboard covering the channel's does not hide it from here), for BugFinder. One copy at a time,
 * handed over on its own thread.
 */
final class FrameSampler {
    interface Got {
        /** The copy (null when it failed: nothing on the surface yet) and when it was on the screen. */
        void frame(Bitmap b, long at);
    }

    private final HandlerThread thread = new HandlerThread("frames");
    private final Handler worker;
    private volatile boolean busy;

    FrameSampler() {
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    void quit() {
        thread.quitSafely();
    }

    boolean busy() {
        return busy;
    }

    /**
     * The picture copied into the bitmap (scaled to it). Main thread; got runs on the sampler's thread. False when no
     * copy was asked for (one under way, no picture yet): got is not called.
     */
    boolean copy(View surface, Bitmap into, Got got) {
        if (busy || surface == null || into == null) return false;
        long at = System.currentTimeMillis();
        if (surface instanceof TextureView) {
            Bitmap b;
            try {
                b = ((TextureView) surface).getBitmap(into);
            } catch (RuntimeException e) {
                return false;
            }
            busy = true;
            worker.post(() -> hand(got, b, at));
            return true;
        }
        if (!(surface instanceof SurfaceView)) return false;
        SurfaceView sv = (SurfaceView) surface;
        if (sv.getHolder().getSurface() == null || !sv.getHolder().getSurface().isValid()) return false;
        busy = true;
        try {
            PixelCopy.request(sv, into, result -> hand(got, result == PixelCopy.SUCCESS ? into : null, at), worker);
            return true;
        } catch (RuntimeException e) {
            busy = false;
            return false;
        }
    }

    /**
     * Part of the picture (src, in the stream's own pixels) copied into into at that sharpness: the see-through patch's
     * place and the picture round it. A SurfaceView's only (PixelCopy reads its buffer); false otherwise, or when busy.
     */
    boolean copyPart(View surface, android.graphics.Rect src, Bitmap into, Got got) {
        if (busy || !(surface instanceof SurfaceView) || into == null || src == null || src.isEmpty()) return false;
        SurfaceView sv = (SurfaceView) surface;
        if (sv.getHolder().getSurface() == null || !sv.getHolder().getSurface().isValid()) return false;
        long at = System.currentTimeMillis();
        busy = true;
        try {
            PixelCopy.request(sv, src, into, result -> hand(got, result == PixelCopy.SUCCESS ? into : null, at), worker);
            return true;
        } catch (RuntimeException e) {
            busy = false;
            return false;
        }
    }

    /**
     * The copy handed over, on the sampler's thread. Whatever goes wrong in reading it (a frame of another size, the
     * reader's own trouble) stays here: that frame is let go and the next is taken, and the stream plays on.
     */
    private void hand(Got got, Bitmap b, long at) {
        try {
            got.frame(b, at);
        } catch (Throwable e) {
            // Only the kind of error (never its words).
            android.util.Log.w("LiveTv", "frame: " + e.getClass().getSimpleName());
        } finally {
            busy = false;
        }
    }

    /** The grey values (0 to 255) of part of a bitmap, row by row. */
    static byte[] grey(Bitmap b, int x, int y, int w, int h) {
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, x, y, w, h);
        byte[] out = new byte[w * h];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            out[i] = (byte) ((((p >> 16) & 0xFF) * 77 + ((p >> 8) & 0xFF) * 150 + (p & 0xFF) * 29) >> 8);
        }
        return out;
    }
}
