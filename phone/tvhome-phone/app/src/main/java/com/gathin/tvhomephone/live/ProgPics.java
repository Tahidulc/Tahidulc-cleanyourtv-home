package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.LruCache;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Programme pictures made at the size they are drawn (the guide's cells, the programme's panel), off the main thread,
 * kept in memory within a budget (16-bit, about a screenful many times over):
 *
 * <pre>
 *   FILL     a guide cell: PicFill's row layout, its faces whole (covering, or the ambient fill: the picture's own
 *            colours behind, the sharp picture over them with its inner edges feathered into them)
 *   PANEL    the programme's panel upright: the same, its faces in the part right of its words (PicFill.PANEL_ROOM)
 *   HEADER   the programme's panel in the pane: the picture across its top, faces whole, fading to nothing at its foot
 *   LOGO     a channel's logo, as large as fits w x h at its own shape (the panel's and the cells' stand-in)
 * </pre>
 *
 * A picture whose faces can't be whole at that size comes back as its own colour only (Made.b null, Made.colour), so a
 * cell too small shows a clean colour, never half a face. The bytes come from Glide's disk cache (fetched once), or a
 * file (previews). One that can't be read is not asked for again for a few minutes.
 */
final class ProgPics {
    private ProgPics() {}

    static final int FILL = 1, PANEL = 2, HEADER = 3, LOGO = 4;
    private static final int THREADS = 2, QUEUE_MAX = 40;

    /**
     * A picture as made: the bitmap (null: its own colours only, or none), its own colours (a few pixels of it, drawn up
     * smooth: a clean ground with no faces) and their average, whether faces were found and where they are across
     * (b's px).
     */
    static final class Made {
        final Bitmap b, ground;
        final int colour;
        final boolean faces;
        final float facesLeft, facesRight;
        private BitmapShader shader, groundShader;

        Made(Bitmap b, Bitmap ground, int colour, boolean faces, float facesLeft, float facesRight) {
            this.b = b;
            this.ground = ground;
            this.colour = colour;
            this.faces = faces;
            this.facesLeft = facesLeft;
            this.facesRight = facesRight;
        }

        /** Its shader (made the first time it is drawn: main thread). */
        BitmapShader shader() {
            if (shader == null && b != null) shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            return shader;
        }

        /** Its ground's shader (as shader()); null with none. */
        BitmapShader groundShader() {
            if (groundShader == null && ground != null) groundShader = new BitmapShader(ground, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            return groundShader;
        }
    }

    /** Nothing to show (it can't be read). */
    static final Made NONE = new Made(null, null, 0, false, 0f, 0f);

    private static final class Key {
        String url;
        int w, h, mode;

        Key set(String u, int ww, int hh, int m) {
            url = u;
            w = ww;
            h = hh;
            mode = m;
            return this;
        }

        Key copy() {
            return new Key().set(url, w, h, mode);
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return w == k.w && h == k.h && mode == k.mode && url.equals(k.url);
        }

        @Override public int hashCode() {
            return ((url.hashCode() * 31 + w) * 31 + h) * 31 + mode;
        }
    }

    private static final Key PROBE = new Key();
    private static final LruCache<Key, Made> MEM = new LruCache<Key, Made>(budget()) {
        @Override protected int sizeOf(Key k, Made m) {
            return m.b == null ? 64 : m.b.getAllocationByteCount();
        }
    };
    private static final Map<Key, List<Runnable>> WAITING = new HashMap<>();
    private static final ArrayDeque<Key> QUEUE = new ArrayDeque<>();
    /** Pictures that couldn't be read, and when: not asked for again for a while (away from the network, say). */
    private static final Map<String, Long> FAILED = Collections.synchronizedMap(new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 500;
        }
    });
    /** Each picture's own colour once read (the cells too small for it), by its address. */
    private static final Map<String, Integer> COLOURS = Collections.synchronizedMap(new LinkedHashMap<String, Integer>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Integer> e) {
            return size() > 800;
        }
    });
    private static final long RETRY_MS = 3 * 60_000L;
    private static volatile Context app;
    private static int running;

    private static int budget() {
        return (int) Math.min(24L << 20, Runtime.getRuntime().maxMemory() / 8);
    }

    /**
     * The picture at url made w x h in mode, or null while it is made (whenReady is run on the main thread then);
     * NONE when it can't be. Main thread; nothing made when it is kept.
     */
    static Made get(Context c, String url, int w, int h, int mode, Runnable whenReady) {
        if (url == null || url.isEmpty() || w < 2 || h < 2) return NONE;
        if (app == null) app = c.getApplicationContext();
        Made m = MEM.get(PROBE.set(url, w, h, mode));
        if (m != null) return m;
        Long failed = FAILED.get(url);
        if (failed != null) {
            if (System.currentTimeMillis() - failed < RETRY_MS) return NONE;
            FAILED.remove(url);
        }
        synchronized (WAITING) {
            List<Runnable> wait = WAITING.get(PROBE);
            if (wait == null) {
                Key k = PROBE.copy();
                WAITING.put(k, wait = new ArrayList<>(2));
                // The newest first (what is on screen now); the oldest let go past QUEUE_MAX (asked again when drawn).
                QUEUE.addFirst(k);
                while (QUEUE.size() > QUEUE_MAX) WAITING.remove(QUEUE.removeLast());
                if (running < THREADS) {
                    running++;
                    Thread t = new Thread(ProgPics::work, "prog-pics");
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    t.setDaemon(true);
                    t.start();
                }
            }
            if (whenReady != null && !wait.contains(whenReady)) wait.add(whenReady);
        }
        return null;
    }

    /** A picture's own colour once it has been read (0 before). */
    static int colourOf(String url) {
        Integer c = url == null ? null : COLOURS.get(url);
        return c == null ? 0 : c;
    }

    private static void work() {
        while (true) {
            Key k;
            synchronized (WAITING) {
                k = QUEUE.pollFirst();
                if (k == null) {
                    running--;
                    return;
                }
            }
            Made m = load(k);
            main().post(() -> {
                MEM.put(k, m);
                List<Runnable> then;
                synchronized (WAITING) {
                    then = WAITING.remove(k);
                }
                if (then != null) for (Runnable r : then) r.run();
            });
        }
    }

    private static volatile android.os.Handler main;

    /** The main thread's handler (made again should the main looper be a new one: the previews' runs). */
    static android.os.Handler main() {
        android.os.Handler h = main;
        android.os.Looper l = android.os.Looper.getMainLooper();
        if (h == null || h.getLooper() != l) main = h = new android.os.Handler(l);
        return h;
    }

    private static Made load(Key k) {
        try {
            File f = file(k.url);
            if (f == null || !f.isFile()) throw new java.io.IOException("not read");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) throw new java.io.IOException("not a picture");
            Made m = k.mode == LOGO ? logo(f, o, k.w, k.h) : fill(f, o, k.url, k.w, k.h, k.mode);
            if (m == null) throw new java.io.IOException("not decoded");
            return m;
        } catch (Exception | OutOfMemoryError e) {
            FAILED.put(k.url, System.currentTimeMillis());
            return NONE;
        }
    }

    /** The picture's bytes: its own file (previews), or Glide's disk copy (fetched the first time). */
    private static File file(String url) throws Exception {
        if (url.startsWith("file:")) return new File(url.startsWith("file://") ? url.substring(7) : url.substring(5));
        Context c = app;
        if (c == null) return null;
        return Glide.with(c).asFile().load(url).submit().get(25, TimeUnit.SECONDS);
    }

    /**
     * FILL, PANEL and HEADER: read sharp enough to be zoomed as PicFill may (ZOOM past covering), its faces found once
     * (kept by its address), laid out by PicFill and made once at w x h. Its own colour only when the faces can't be
     * whole.
     */
    private static Made fill(File f, BitmapFactory.Options o, String url, int w, int h, int mode) {
        float cover = Math.max(w * o.outHeight / (float) o.outWidth, h), need = cover * PicFill.ZOOM;
        int sample = 1;
        while (o.outHeight / (sample * 2) >= need) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap src = BitmapFactory.decodeFile(f.getPath(), d);
        if (src == null) return null;
        float[] box = Faces.of(url, src);
        float sw = src.getWidth(), sh = src.getHeight();
        float[] lay = new float[PicFill.SIZE];
        if (mode == PANEL) PicFill.layout(sw / sh, box, w, h, PicFill.PANEL_ROOM, 1f, true, lay);
        else if (mode == HEADER) PicFill.layout(sw / sh, box, w, h, 0f, 1f, true, lay);
        else PicFill.row(sw / sh, box, w, h, lay);
        // Its own colours: a few pixels of it, a little quieter (drawn up smooth, a clean ground with no faces).
        Bitmap small = Bitmap.createScaledBitmap(src, 8, 3, true);
        Bitmap tiny = small.copy(Bitmap.Config.RGB_565, true);
        if (small != src && small != tiny) small.recycle();
        new Canvas(tiny).drawColor(0x40000000);
        int colour = deep(average(tiny));
        COLOURS.put(url, colour);
        boolean faces = !PicFill.none(box);
        if (faces && !PicFill.whole(lay, w, h)) {
            // Its faces would be cut here: its own colours, clean, never half a face.
            src.recycle();
            return new Made(null, tiny, colour, true, 0f, w);
        }
        boolean header = mode == HEADER;
        Bitmap out = Bitmap.createBitmap(w, h, header ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Matrix m = new Matrix();
        float k = lay[PicFill.K] / sh;
        m.setScale(k, k);
        m.postTranslate(lay[PicFill.X], lay[PicFill.Y]);
        if (lay[PicFill.KIND] == PicFill.AMBIENT) {
            // The picture's own colours behind (a few pixels of it, drawn up smooth, a little quieter) and the sharp
            // picture over them, its inner edges faded into them as it is drawn (no blur, no second sheet).
            c.drawBitmap(tiny, null, new RectF(0, 0, w, h), p);
            BitmapShader sharp = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            sharp.setLocalMatrix(m);
            float x0 = lay[PicFill.X], x1 = x0 + sw * k, fl = lay[PicFill.FEATHER_L], fr = lay[PicFill.FEATHER_R];
            float w0 = Math.max(1f, x1 - x0), a = Math.max(0f, Math.min(0.49f, fl / w0)), b = Math.max(0f, Math.min(0.49f, fr / w0));
            Shader mask = new LinearGradient(x0, 0, x1, 0, new int[]{a > 0f ? 0 : 0xFF000000, 0xFF000000, 0xFF000000, b > 0f ? 0 : 0xFF000000},
                    new float[]{0f, Math.max(0.0001f, a), Math.min(0.9999f, 1f - b), 1f}, Shader.TileMode.CLAMP);
            Paint q = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            q.setShader(new ComposeShader(sharp, mask, PorterDuff.Mode.DST_IN));
            c.drawRect(Math.max(0f, x0), 0, Math.min(w, x1), h, q);
        } else {
            c.drawBitmap(src, m, p);
        }
        if (header) {
            // Its foot fades to nothing, into the pane under it.
            Paint cut = new Paint(Paint.DITHER_FLAG);
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            cut.setShader(new LinearGradient(0, h * 0.5f, 0, h, new int[]{0xFF000000, 0x99000000, 0x00000000}, new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawRect(0, h * 0.5f, w, h, cut);
        }
        float fl = lay[PicFill.FL], fr = lay[PicFill.FR];
        if (!faces) {
            fl = 0f;
            fr = w;
        }
        src.recycle();
        // The ground kept for a cell (one cut so its faces would be under its words takes it instead).
        if (mode != FILL) tiny.recycle();
        return new Made(out, mode == FILL ? tiny : null, colour, faces, fl, fr);
    }

    /** A logo as large as fits w x h at its own shape, read sharp for that size. */
    private static Made logo(File f, BitmapFactory.Options o, int w, int h) {
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap src = BitmapFactory.decodeFile(f.getPath(), d);
        if (src == null) return null;
        float s = Math.min(w / (float) src.getWidth(), h / (float) src.getHeight());
        int lw = Math.max(1, Math.round(src.getWidth() * s)), lh = Math.max(1, Math.round(src.getHeight() * s));
        Bitmap out = Bitmap.createScaledBitmap(src, lw, lh, true);
        if (out != src) src.recycle();
        return new Made(out, null, 0, false, 0f, lw);
    }

    /** Pure: the average colour of a few pixels. */
    static int average(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        long r = 0, g = 0, bl = 0;
        int n = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = b.getPixel(x, y);
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                bl += c & 0xFF;
                n++;
            }
        }
        if (n == 0) return 0xFF202024;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
    }

    /** Pure: a colour made deep enough for white writing on it (its brightest part at most about 40 %). */
    static int deep(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF, max = Math.max(r, Math.max(g, b));
        float k = max <= 100 ? 1f : 100f / max;
        return 0xFF000000 | Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
    }

    /** Memory is short: everything kept let go (made again as it is drawn). */
    static void trim() {
        MEM.evictAll();
    }
}
