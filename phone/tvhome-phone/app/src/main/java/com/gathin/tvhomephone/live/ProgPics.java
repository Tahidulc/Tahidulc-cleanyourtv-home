package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.LruCache;

import com.bumptech.glide.Glide;
import com.gathin.broadcast.PicArt;
import com.gathin.broadcast.PicPlace;
import com.gathin.broadcast.PicSources;
import com.gathin.broadcast.PicVet;

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
 * kept in memory within a budget (16-bit where they are opaque, about a screenful many times over), placed by the rule
 * shared with Homefield TV (PicPlace: covering only a place close to the picture's own shape, else the whole picture at
 * the place's height beside the words, the place's own colour from the picture's edges continuing smoothly behind it;
 * faces whole; never enlarged past 1.5 times):
 *
 * <pre>
 *   FILL     a guide cell: the picture alone as PicPlace puts it in the whole card (its edges feathered to nothing) and
 *            its edges' colours (Made.ground, Made.lay), so the card draws its ground and the picture glides along it as
 *            the guide scrolls (PicDraw.cell: PicPlace.slide), never made again, never re-cropped
 *   PANEL    the programme's panel upright: the picture placed in the part right of its words (PicFill.PANEL_ROOM),
 *            the panel's own colour from it behind
 *   HEADER   the programme's panel in the pane: the picture across its top, faces whole, fading to nothing at its foot
 *   BAND     the folded band over the guide held upright (band(room): PinBackdrop): as PANEL, right of the channel line's
 *            words (room of its width)
 *   LOGO     a channel's logo, as large as fits w x h at its own shape (the panel's and the cells' stand-in)
 * </pre>
 *
 * An address may be a chain of pictures, best first (ProgArt: the guide's own, Sky's, TMDB's, then a game's crests or a
 * kind's picture: PicSources); each place takes the first in it that is large enough for it, the made-up ones drawn here
 * (PicArt). A picture whose faces can't be whole at that size comes back as its own colour only (Made.b null,
 * Made.colour), so a cell too small shows a clean colour, never half a face. The bytes come from Glide's disk cache
 * (fetched once), or a file (previews). One that can't be read is not asked for again for a few minutes.
 */
final class ProgPics {
    private ProgPics() {}

    static final int FILL = 1, PANEL = 2, HEADER = 3, LOGO = 4;
    /**
     * BAND + the share of its width its words take at its left, in hundredths (band()): the folded band over the guide
     * held upright (PinBackdrop), laid out as the panel is, its faces right of the words (under the small picture on a
     * phone, beside the channel's line on a tablet).
     */
    static final int BAND = 100;

    /** The mode for a band whose words take room (0 to 1) of its width at its left. */
    static int band(float room) {
        return BAND + Math.max(1, Math.min(99, Math.round(room * 100)));
    }
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
        /**
         * FILL: where PicPlace put the picture in the whole card (lay), where the picture made starts across it (at); ground
         * is then its edges' colours (PicArt.edges, null when it covers the card). Null lay: ground is its own colours.
         */
        final float[] lay;
        final float at;
        private BitmapShader shader, groundShader;

        Made(Bitmap b, Bitmap ground, int colour, boolean faces, float facesLeft, float facesRight) {
            this(b, ground, colour, faces, facesLeft, facesRight, null, 0f);
        }

        Made(Bitmap b, Bitmap ground, int colour, boolean faces, float facesLeft, float facesRight, float[] lay, float at) {
            this.b = b;
            this.ground = ground;
            this.colour = colour;
            this.faces = faces;
            this.facesLeft = facesLeft;
            this.facesRight = facesRight;
            this.lay = lay;
            this.at = at;
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
            return (m.b == null ? 64 : m.b.getAllocationByteCount()) + (m.ground == null ? 0 : m.ground.getAllocationByteCount());
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
    /** The application's context (never an activity's). */
    @android.annotation.SuppressLint("StaticFieldLeak")
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
        java.util.List<String> parts = PicSources.parts(k.url);
        for (int i = 0; i < parts.size(); i++) {
            String u = parts.get(i);
            Long failed = FAILED.get(u);
            if (failed != null && System.currentTimeMillis() - failed < RETRY_MS) continue;
            // A chain's pictures before its last are taken only where they are large enough (never enlarged past 1.5).
            Made m = one(u, k, i < parts.size() - 1);
            if (m == null) continue;
            if (!u.equals(k.url) && m.colour != 0) COLOURS.put(k.url, m.colour);
            return m;
        }
        FAILED.put(k.url, System.currentTimeMillis());
        return NONE;
    }

    /** One picture made for k; null when it can't be read, or (strict) is too small for k's place. */
    private static Made one(String url, Key k, boolean strict) {
        try {
            if (PicSources.madeUp(url)) {
                Faces.known(url, PicPlace.NO_FACES);
                Bitmap src = madeUp(url, k.w, k.h);
                return src == null ? null : fill(src, url, k.w, k.h, k.mode);
            }
            File f = file(url);
            if (f == null || !f.isFile()) throw new java.io.IOException("not read");
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) throw new java.io.IOException("not a picture");
            if (k.mode == LOGO) return logo(f, o, k.w, k.h);
            float[] lay = new float[PicPlace.SIZE];
            PicPlace.place(o.outWidth, o.outHeight, Faces.known(url), k.w, k.h, room(k.mode, k.w, k.h), 1f, lay);
            if (strict && PicPlace.tooSmall(lay)) return null;
            // A catalogue's picture that is no programme's (a badge, a tracking pixel): never shown, the next instead.
            if (PicVet.tiny(o.outWidth, o.outHeight)) throw new java.io.IOException("too small to be a programme's");
            int sample = 1;
            while (o.outHeight / (sample * 2) >= lay[PicPlace.PH] && o.outWidth / (sample * 2) >= lay[PicPlace.PW]) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), d);
            if (src == null) return null;
            if (placeholder(src)) {
                // A placeholder card ("Image unavailable", a plain colour): never shown, the next instead.
                src.recycle();
                throw new java.io.IOException("a placeholder");
            }
            return fill(src, url, k.w, k.h, k.mode);
        } catch (Exception | OutOfMemoryError e) {
            FAILED.put(url, System.currentTimeMillis());
            return null;
        }
    }

    /** Whether a picture read is a placeholder card: a small even sample of it nearly all one colour (PicVet.flat). */
    static boolean placeholder(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 24, 14, true);
        int[] px = new int[24 * 14];
        s.getPixels(px, 0, 24, 0, 0, 24, 14);
        if (s != src) s.recycle();
        return PicVet.flat(px);
    }

    /** A made-up picture (a game's crests on their colours, a kind's picture: PicArt) drawn for a place w x h. */
    private static Bitmap madeUp(String url, int w, int h) throws Exception {
        int[] size = PicArt.size(w, h);
        String kind = PicSources.genreOf(url);
        if (kind != null) return PicArt.genre(kind, size[0], size[1]);
        if (url.startsWith(PicSources.BRAND_AT)) return PicArt.brand(PicSources.brandColour(url), size[0], size[1]);
        String[] crests = PicSources.fixtureCrests(url);
        int[] cols = PicSources.fixtureColours(url);
        if (crests == null || cols == null) return null;
        int cs = Math.round(size[1] * 0.6f);
        Bitmap home = crest(crests[0], cs), away = crest(crests[1], cs);
        Bitmap b = PicArt.fixture(home, away, cols[0], cols[1], size[0], size[1]);
        if (home != null) home.recycle();
        if (away != null) away.recycle();
        return b;
    }

    /** A club's crest read for size x size (null with none, or when it can't be read). */
    private static Bitmap crest(String url, int size) {
        if (url == null || url.isEmpty()) return null;
        try {
            File f = file(url);
            if (f == null || !f.isFile()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= size && o.outHeight / (sample * 2) >= size) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** The picture's bytes: its own file (previews), or Glide's disk copy (fetched the first time). */
    private static File file(String url) throws Exception {
        if (url.startsWith("file:")) return new File(url.startsWith("file://") ? url.substring(7) : url.substring(5));
        Context c = app;
        if (c == null) return null;
        return Glide.with(c).asFile().load(url).submit().get(25, TimeUnit.SECONDS);
    }

    /** Where a mode's words end at its place's left (its free part from there to its right). */
    static float room(int mode, float w, float h) {
        if (mode == PANEL) return PicFill.PANEL_ROOM;
        if (mode == HEADER) return 0f;
        if (mode > BAND) return (mode - BAND) / 100f;
        return PicPlace.room(w, h);
    }

    /**
     * FILL, PANEL, HEADER and BAND: the picture (read at the size it is shown), its faces found once (kept by its
     * address), placed by PicPlace in w x h: FILL the picture alone and its edges' colours (it glides along its card);
     * the others made once whole, the place's own colour from the picture behind it. Its own colour only when the faces
     * can't be whole.
     */
    private static Made fill(Bitmap src, String url, int w, int h, int mode) {
        float[] box = Faces.of(url, src);
        float sw = src.getWidth(), sh = src.getHeight();
        float[] lay = new float[PicPlace.SIZE];
        PicPlace.place(sw, sh, box, w, h, room(mode, w, h), 1f, lay);
        // Its own colours: a few pixels of it, a little quieter (drawn up smooth, a clean ground with no faces).
        Bitmap small = Bitmap.createScaledBitmap(src, 8, 3, true);
        Bitmap tiny = small.copy(Bitmap.Config.RGB_565, true);
        if (small != src && small != tiny) small.recycle();
        new Canvas(tiny).drawColor(0x40000000);
        int colour = deep(average(tiny));
        COLOURS.put(url, colour);
        boolean faces = !PicPlace.none(box);
        if (faces && !PicPlace.whole(lay, w, h)) {
            // Its faces would be cut here: its own colours, clean, never half a face.
            src.recycle();
            return new Made(null, tiny, colour, true, 0f, w);
        }
        float fl = faces ? lay[PicPlace.FL] : lay[PicPlace.KIND] == PicPlace.COVER ? w : Math.max(0f, lay[PicPlace.X]);
        float fr = faces ? lay[PicPlace.FR] : w;
        if (mode == FILL) {
            if (lay[PicPlace.KIND] == PicPlace.SHOW && lay[PicPlace.FEATHER_T] == 0f && lay[PicPlace.FEATHER_B] == 0f) {
                // It glides along its card: both its edges always feathered (never a hard edge wherever it stops).
                float least = Math.min(PicPlace.FEATHER * lay[PicPlace.PW], Math.max(PicPlace.FEATHER_MIN_PX, PicPlace.FEATHER_MIN * h));
                lay[PicPlace.FEATHER_L] = Math.max(lay[PicPlace.FEATHER_L], least);
                lay[PicPlace.FEATHER_R] = Math.max(lay[PicPlace.FEATHER_R], least);
            }
            float[] at = new float[2];
            Bitmap out = PicArt.sharp(src, lay, w, h, at);
            Bitmap edges = lay[PicPlace.KIND] == PicPlace.COVER ? null : PicArt.edges(src, lay);
            src.recycle();
            tiny.recycle();
            return new Made(out, edges, colour, faces, fl, fr, lay, at[0]);
        }
        boolean header = mode == HEADER;
        Bitmap out = PicArt.compose(src, lay, w, h, header ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        if (header) {
            // Its foot fades to nothing, into the pane under it.
            Paint cut = new Paint(Paint.DITHER_FLAG);
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            cut.setShader(new LinearGradient(0, h * 0.5f, 0, h, new int[]{0xFF000000, 0x99000000, 0x00000000}, new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP));
            new Canvas(out).drawRect(0, h * 0.5f, w, h, cut);
        }
        src.recycle();
        tiny.recycle();
        return new Made(out, null, colour, faces, fl, fr);
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
}
