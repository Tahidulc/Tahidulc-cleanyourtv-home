package com.gathin.homefieldtv;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.Looper;
import android.util.LruCache;

import com.gathin.homefieldtv.engine.Exec;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Posters, backdrops, programme pictures and channel logos: fetched off the main thread (the newest asked for first, so
 * what is on screen comes before what was scrolled past; what a screen only expects to need next, prefetch, after
 * everything asked for), kept on disk in the app's cache and in memory at the size they are drawn, so a row draws each
 * one with a single copy and the TV's graphics chip does little work.
 *
 * <p>Memory: the pictures in memory are a least-recently-drawn cache bounded in bytes (BUDGET: at most 40 MB, less on a
 * small heap), each decoded at the size it is drawn (sampled down while it is read, then scaled), opaque ones in 16-bit
 * colour; trim() lets them go when Android asks (onTrimMemory). A picture is read from its file on disk, never as a
 * whole copy in memory, and handed to the graphics chip as soon as it is made (prepareToDraw), not on the first frame
 * that draws it. Looking one up as a frame draws makes nothing new.
 */
final class Pics {
    private Pics() {}

    /**
     * CROP fills the size (posters, backdrops); FIT keeps the whole picture inside it (logos). TILE is a channel's logo
     * on its black tile, the size given (LogoTile); READY is FIT with the logo made ready for black (for a screen that
     * draws the black itself).
     */
    static final int CROP = 0, FIT = 1, TILE = 4, READY = 5;
    /**
     * POSTER is a poster card as the dark screens draw it (PosterArt): CROP with its corners rounded and its foot faded
     * into the poster's own colour, all done once as it loads. DARK is CROP faded into the dark ground on the left and at
     * the foot (a title's backdrop behind the writing); DARK_FULL fades only at the foot and a little at the left.
     */
    static final int POSTER = 6, DARK = 7, DARK_FULL = 8;
    /**
     * CARD is a programme's picture as a channel's tile on the live screens: CROP with its corners rounded, a little
     * darker at the top (for the channel's logo) and much darker towards its foot (for the words), done once as it loads.
     */
    static final int CARD = 9;
    /**
     * POSTER_WIDE is a landscape poster card (Settings, Posters: Landscape): POSTER's rounding and fade on a 16:9 card,
     * its corners a share of its height and its fade rising from 24% of the way down (PosterArt.fadeShader).
     */
    static final int POSTER_WIDE = 10;
    /**
     * ART is a programme's picture behind a panel's words (the guide's details, the player's info card: PanelArt): CROP,
     * darkened a little, faded to nothing over its left part and at its foot (so it sinks into the panel's own colour,
     * whatever the theme), its right-hand corners rounded; done once as it loads.
     */
    static final int ART = 11;
    /**
     * LOGO_ART stands in for a programme's picture where there is none (ART's size, shape and fades): the channel's logo
     * (made ready for a dark ground, as READY) on a deep ground tinted by the logo's own colour, a soft round glow of that
     * colour behind it, the logo itself low on the right, clear of most of the words. Asked for with the logo's address.
     */
    static final int LOGO_ART = 12;

    /** Each poster's fade colour as worked out when it loaded, by its address (0 until then). */
    private static final java.util.Map<String, Integer> FADES = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Integer>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> e) {
            return size() > 800;
        }
    });

    /** A loaded poster's fade colour, or 0 when it hasn't loaded. */
    static int fadeOf(String url) {
        Integer c = url == null ? null : FADES.get(url);
        return c == null ? 0 : c;
    }

    /** Channel logos on black tiles (Settings, Channel logos): on unless switched off. */
    static volatile boolean tiles = true;

    /** A channel logo's mode: on its tile, else as it comes. */
    static int logoMode() {
        return tiles ? TILE : FIT;
    }

    static void setTiles(Context c, boolean on) {
        tiles = on;
        c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).edit().putBoolean("logo_tiles", on).apply();
    }

    private static final int THREADS = 3, QUEUE = 48, PREFETCH = 24;
    private static final long DISK_MAX = 160L << 20, DISK_KEEP = 120L << 20;
    /** The pictures in memory: at most this many bytes. */
    static final int BUDGET = budget();

    /** A picture as it is kept: its address, mode and size (one is looked up without making a new one). */
    private static final class Key {
        String url;
        int mode, w, h, hash;

        Key set(String u, int m, int ww, int hh) {
            url = u;
            mode = m;
            w = ww;
            h = hh;
            hash = ((u.hashCode() * 31 + m) * 31 + ww) * 31 + hh;
            return this;
        }

        Key copy() {
            return new Key().set(url, mode, w, h);
        }

        @Override public int hashCode() {
            return hash;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return hash == k.hash && mode == k.mode && w == k.w && h == k.h && url.equals(k.url);
        }
    }

    private static File disk;
    private static final LruCache<Key, Bitmap> mem = new LruCache<Key, Bitmap>(BUDGET) {
        @Override protected int sizeOf(Key k, Bitmap b) {
            return b.getAllocationByteCount();
        }
    };
    /** The main thread's key for looking up (the screens ask as they draw: nothing new each frame). */
    private static final Key PROBE = new Key();
    private static final Map<Key, List<Runnable>> waiting = new HashMap<>();
    private static final ArrayDeque<Job> queue = new ArrayDeque<>(), later = new ArrayDeque<>();
    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<>());
    private static boolean started;
    /** For the tests and the memory log: pictures found in memory, read, and the bytes read from disk or the network. */
    static volatile int hits, loads;

    private static final class Job {
        final Key key;

        Job(Key key) {
            this.key = key;
        }
    }

    private static int budget() {
        long max = Runtime.getRuntime().maxMemory();
        return (int) Math.max(12L << 20, Math.min(40L << 20, max / 8));
    }

    static synchronized void init(Context c) {
        if (disk != null) return;
        tiles = c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE).getBoolean("logo_tiles", true);
        disk = new File(c.getCacheDir(), "pics");
        //noinspection ResultOfMethodCallIgnored
        disk.mkdirs();
        if (!Exec.inline) Exec.io(Pics::prune);
    }

    /** True when a picture could not be read (Pics.get gives null for it from then on, not "still loading"). */
    static boolean failed(String url) {
        return url != null && failed.contains(url);
    }

    private static Key probe(String url, int w, int h, int mode) {
        Key k = Looper.getMainLooper().isCurrentThread() ? PROBE : new Key();
        return k.set(url, mode, w, h);
    }

    /**
     * The picture at w x h, or null while it loads (then whenReady runs on the main thread). Several asking for the
     * same picture share one load.
     */
    static Bitmap get(String url, int w, int h, int mode, Runnable whenReady) {
        return want(url, w, h, mode, whenReady, true);
    }

    /**
     * A picture a screen expects to need next (the programme after, the channel below): read after everything asked for
     * and kept, so it is there at once when the remote gets to it. Nothing happens when it is already in memory.
     */
    static void prefetch(String url, int w, int h, int mode) {
        want(url, w, h, mode, null, false);
    }

    private static Bitmap want(String url, int w, int h, int mode, Runnable whenReady, boolean now) {
        if (url == null || url.isEmpty() || w <= 0 || h <= 0) return null;
        Key probe = probe(url, w, h, mode);
        Bitmap b = mem.get(probe);
        if (b != null) {
            hits++;
            return b;
        }
        if (failed.contains(url)) return null;
        if (Exec.inline) {
            Key key = probe.copy();
            Bitmap got = load(key);
            if (got == null) failed.add(url);
            else mem.put(key, got);
            return now ? got : null;
        }
        synchronized (waiting) {
            List<Runnable> l = waiting.get(probe);
            if (l != null) {
                if (whenReady != null && !l.contains(whenReady)) l.add(whenReady);
                // Asked for again (on screen now): to the front of the queue, unless it is there already.
                if (now && (queue.isEmpty() || !queue.peekFirst().key.equals(probe))) {
                    Job moved = take(queue, probe);
                    if (moved == null) moved = take(later, probe);
                    if (moved != null) queue.addFirst(moved);
                }
                return null;
            }
            Key key = probe.copy();
            l = new ArrayList<>(2);
            if (whenReady != null) l.add(whenReady);
            waiting.put(key, l);
            if (now) {
                queue.addFirst(new Job(key));
                // Long gone from the screen: dropped (asked for again when it comes back).
                while (queue.size() > QUEUE) waiting.remove(queue.removeLast().key);
            } else {
                later.addLast(new Job(key));
                while (later.size() > PREFETCH) waiting.remove(later.removeFirst().key);
            }
            if (!started) {
                started = true;
                for (int i = 0; i < THREADS; i++) {
                    Thread t = new Thread(Pics::work, "pics-" + i);
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    t.start();
                }
            }
            waiting.notifyAll();
        }
        return null;
    }

    private static Job take(ArrayDeque<Job> q, Key k) {
        for (Iterator<Job> it = q.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (j.key.equals(k)) {
                it.remove();
                return j;
            }
        }
        return null;
    }

    /** Already loaded at that size (no load started). */
    static Bitmap cached(String url, int w, int h, int mode) {
        if (url == null || url.isEmpty()) return null;
        return mem.get(probe(url, w, h, mode));
    }

    /** The bytes the pictures in memory take now (the memory log). */
    static int bytes() {
        return mem.size();
    }

    /**
     * Android is short of memory (onTrimMemory's level): the pictures not on screen let go — all of them once the app
     * is out of sight or memory is critical, half of them while it runs low; the queue of pictures to read emptied.
     */
    static void trim(int level) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            mem.evictAll();
        } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            mem.trimToSize(mem.size() / 2);
        } else {
            return;
        }
        synchronized (waiting) {
            for (Job j : later) waiting.remove(j.key);
            later.clear();
        }
        FADES.clear();
    }

    private static void work() {
        while (true) {
            Job j;
            synchronized (waiting) {
                while (queue.isEmpty() && later.isEmpty()) {
                    try {
                        waiting.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                j = !queue.isEmpty() ? queue.removeFirst() : later.removeFirst();
            }
            Bitmap b = null;
            try {
                b = load(j.key);
                // Handed to the graphics chip now, on its own thread, not on the frame that first draws it.
                if (b != null) b.prepareToDraw();
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // left out
            }
            if (b == null) failed.add(j.key.url);
            Bitmap got = b;
            Exec.main(() -> {
                if (got != null) mem.put(j.key, got);
                List<Runnable> l;
                synchronized (waiting) {
                    l = waiting.remove(j.key);
                }
                if (l != null) for (Runnable r : l) r.run();
            });
        }
    }

    /** Where a picture's bytes come from: its file (read twice: its size, then the picture), never all in memory. */
    private interface Source {
        InputStream open() throws IOException;
    }

    private static Bitmap load(Key k) {
        Source src = source(k.url);
        if (src == null) return null;
        loads++;
        try {
            return decode(src, k.url, k.w, k.h, k.mode);
        } catch (IOException e) {
            return null;
        }
    }

    private static Bitmap decode(Source in, String url, int w, int h, int mode) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream s = in.open()) {
            BitmapFactory.decodeStream(s, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        boolean fit = mode == FIT || mode == TILE || mode == READY || mode == LOGO_ART;
        if (!fit) {
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        } else {
            while (o.outWidth / (sample * 2) >= w || o.outHeight / (sample * 2) >= h) sample *= 2;
        }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = fit ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        Bitmap src;
        try (InputStream s = in.open()) {
            src = BitmapFactory.decodeStream(s, null, d);
        }
        if (src == null) return null;
        if (mode == TILE || mode == READY || mode == LOGO_ART) {
            // Made ready for black and trimmed (LogoTile); nothing of it to draw: no logo (the name instead).
            Bitmap ready = com.gathin.broadcast.LogoTile.ready(src);
            src.recycle();
            if (ready == null) return null;
            if (mode == TILE) {
                Bitmap t = com.gathin.broadcast.LogoTile.tileOf(ready, w, h, Math.min(w, h) * 0.12f);
                ready.recycle();
                return t;
            }
            if (mode == LOGO_ART) return logoArt(ready, w, h);
            src = ready;
        }
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        if (!fit) {
            float sc = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            Bitmap out = Bitmap.createBitmap(w, h, src.hasAlpha() ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            Matrix m = new Matrix();
            m.setScale(sc, sc);
            // Posters and backdrops keep their middle; a tall picture in a wide box keeps its top third (faces).
            float dx = (w - src.getWidth() * sc) / 2f;
            float dy = src.getHeight() * sc > h * 1.4f ? (h - src.getHeight() * sc) / 4f : (h - src.getHeight() * sc) / 2f;
            m.postTranslate(dx, dy);
            new Canvas(out).drawBitmap(src, m, p);
            if (src != out) src.recycle();
            if (mode == POSTER || mode == POSTER_WIDE) return poster(url, out, mode == POSTER_WIDE);
            if (mode == CARD) return card(out);
            if (mode == DARK || mode == DARK_FULL) return darkFade(out, mode == DARK_FULL);
            if (mode == ART) return art(out, true);
            return out;
        }
        float sc = Math.min(w / (float) src.getWidth(), h / (float) src.getHeight());
        sc = Math.min(sc, 2.5f);
        int ow = Math.max(1, Math.round(src.getWidth() * sc)), oh = Math.max(1, Math.round(src.getHeight() * sc));
        if (ow == src.getWidth() && oh == src.getHeight()) return src;
        Bitmap out = Bitmap.createScaledBitmap(src, ow, oh, true);
        if (out != src) src.recycle();
        return out;
    }

    /**
     * A poster card: its corners rounded (PosterArt.RADIUS of its width) and its foot faded into its own colour (the
     * average of its lowest third, made deep: PosterArt.fadeColour), clear at 42% of the way down, solid at the foot.
     */
    private static Bitmap poster(String url, Bitmap src, boolean wide) {
        int w = src.getWidth(), h = src.getHeight();
        int third = Math.max(1, h / 3);
        int[] px = new int[w * third];
        src.getPixels(px, 0, w, 0, h - third, w, third);
        int colour = PosterArt.fadeColour(PosterArt.average(px));
        FADES.put(url, colour);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        shape.setShader(new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = (wide ? h : w) * PosterArt.RADIUS;
        c.drawRoundRect(0, 0, w, h, r, r, shape);
        Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        fade.setShader(PosterArt.fadeShader(colour, h, wide));
        c.drawRect(0, 0, w, h, fade);
        src.recycle();
        return out;
    }

    /** A programme's picture as a channel's tile: its corners rounded, darkened at the top a little and the foot a lot. */
    private static Bitmap card(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        shape.setShader(new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = h * 0.09f;
        c.drawRoundRect(0, 0, w, h, r, r, shape);
        Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        shade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        shade.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x59000000, 0x14000000, 0x8C000000, 0xF0000000},
                new float[]{0f, 0.32f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, shade);
        src.recycle();
        return out;
    }

    /** A backdrop faded into the dark ground: on its left (all but full) and at its foot, and a touch darker. */
    private static Bitmap darkFade(Bitmap src, boolean full) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(src, 0, 0, null);
        src.recycle();
        Paint m = new Paint();
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float left = full ? 0.22f : 0.55f;
        m.setShader(new LinearGradient(0, 0, w * left, 0, new int[]{0x00000000, 0x99000000, 0xFF000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w * left, h, m);
        m.setShader(new LinearGradient(0, h * 0.45f, 0, h, new int[]{0xFF000000, 0x8C000000, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.45f, w, h, m);
        Paint dim = new Paint();
        dim.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        dim.setColor(0x38000000);
        c.drawRect(0, 0, w, h, dim);
        return out;
    }

    /** The share of an ART picture's width its left fade takes, and of its height its foot's. */
    static final float ART_FADE_LEFT = 0.62f, ART_FADE_FOOT = 0.42f;

    /**
     * A programme's picture behind a panel's words (ART): darkened a little (the words over it stay white on dark),
     * faded to nothing over its left part and at its foot, its right-hand corners rounded.
     */
    private static Bitmap art(Bitmap src, boolean darker) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        shape.setShader(new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = h * 0.05f;
        // Rounded at the right only: the left is faded away anyway.
        c.drawRoundRect(-r * 2f, 0, w, h, r, r, shape);
        src.recycle();
        if (darker) {
            Paint dim = new Paint();
            dim.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            dim.setColor(0x5C000000);
            c.drawRect(0, 0, w, h, dim);
        }
        Paint m = new Paint();
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float left = w * ART_FADE_LEFT;
        m.setShader(new LinearGradient(0, 0, left, 0, new int[]{0x00000000, 0x40000000, 0xB3000000, 0xFF000000},
                new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, left, h, m);
        float foot = h * (1f - ART_FADE_FOOT);
        m.setShader(new LinearGradient(0, foot, 0, h, new int[]{0xFF000000, 0x80000000, 0x00000000},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, foot, w, h, m);
        return out;
    }

    /**
     * The stand-in for a programme's picture (LOGO_ART): a deep ground tinted by the logo's own colour, a soft round glow
     * of it with no edge anywhere, the logo itself low on the right and a little faded, then ART's shape and fades.
     */
    private static Bitmap logoArt(Bitmap ready, int w, int h) {
        int tint = tintOf(ready);
        Bitmap ground = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(ground);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, Theme.mix(0xFF000000, tint, 0.42f), Theme.mix(0xFF000000, tint, 0.18f),
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // Where the logo sits: low on the right, clear of most of the words over it.
        float cx = w * 0.80f, cy = h * 0.64f;
        // Its soft glow: the logo's own colour, fading out all round (no edge anywhere).
        p.setShader(new android.graphics.RadialGradient(cx, cy, h * 0.62f, Palette.alpha(Theme.mix(0xFF000000, tint, 0.85f), 0xB0),
                Palette.alpha(tint, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float lh = h * 0.30f, lw = Math.min(w * 0.30f, lh * ready.getWidth() / (float) Math.max(1, ready.getHeight()));
        lh = Math.min(lh, lw * ready.getHeight() / (float) Math.max(1, ready.getWidth()));
        p.setAlpha(0xA6);
        c.drawBitmap(ready, null, new android.graphics.RectF(cx - lw / 2f, cy - lh / 2f, cx + lw / 2f, cy + lh / 2f), p);
        ready.recycle();
        return art(ground, true);
    }

    /** Pure: a logo's own colour, the average of its solid, coloured pixels (a grey for a white or black logo). */
    static int tintOf(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), step = Math.max(1, Math.max(w, h) / 48);
        long r = 0, g = 0, bl = 0, n = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = b.getPixel(x, y);
                if ((c >>> 24) < 0xC0) continue;
                int cr = (c >> 16) & 0xFF, cg = (c >> 8) & 0xFF, cb = c & 0xFF;
                int max = Math.max(cr, Math.max(cg, cb)), min = Math.min(cr, Math.min(cg, cb));
                // Coloured pixels count four times as much as greys (a white logo with a red mark is red).
                int k = max - min > 40 ? 4 : 1;
                r += (long) cr * k;
                g += (long) cg * k;
                bl += (long) cb * k;
                n += k;
            }
        }
        if (n == 0) return 0xFF5A6390;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (bl / n);
    }

    // ---- the bytes: a file (previews), the disk cache, or the network --------------------------------------------

    /** Where a picture is read from: its own file, the disk copy, or downloaded to the disk first (null: it can't be). */
    private static Source source(String url) {
        if (url.startsWith("file:")) {
            File f = new File(url.substring(5));
            return f.exists() ? () -> new BufferedInputStream(new FileInputStream(f), 32 * 1024) : null;
        }
        File f = disk == null ? null : new File(disk, name(url));
        if (f != null && f.exists() && f.length() > 0) {
            //noinspection ResultOfMethodCallIgnored
            f.setLastModified(System.currentTimeMillis());
            return () -> new BufferedInputStream(new FileInputStream(f), 32 * 1024);
        }
        if (f == null) {
            byte[] b = download(url, null);
            return b == null ? null : () -> new ByteArrayInputStream(b);
        }
        File tmp = new File(f.getPath() + "." + Thread.currentThread().getId() + ".part");
        try (OutputStream o = new FileOutputStream(tmp)) {
            if (download(url, o) == null) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return null;
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return null;
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            if (!f.exists()) return null;
        }
        return () -> new BufferedInputStream(new FileInputStream(f), 32 * 1024);
    }

    /** Fetched into out as it comes (an empty array back for done), or into memory when out is null; null: it can't be. */
    private static byte[] download(String url, OutputStream out) {
        String u = url;
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(10_000);
                c.setReadTimeout(20_000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", "HomefieldTV/1.0");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String next = c.getHeaderField("Location");
                    if (next == null) return null;
                    u = new URL(new URL(u), next).toString();
                    continue;
                }
                if (code != 200) return null;
                try (InputStream in = c.getInputStream()) {
                    return copy(in, out, 8 << 20);
                }
            } catch (IOException | RuntimeException e) {
                return null;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private static byte[] copy(InputStream in, OutputStream out, int max) throws IOException {
        java.io.ByteArrayOutputStream mem = out == null ? new java.io.ByteArrayOutputStream(64 * 1024) : null;
        OutputStream to = out != null ? out : mem;
        byte[] buf = new byte[32 * 1024];
        long total = 0;
        for (int n; (n = in.read(buf)) > 0; ) {
            to.write(buf, 0, n);
            total += n;
            if (total > max) return null;
        }
        return mem != null ? mem.toByteArray() : new byte[0];
    }

    private static String name(String url) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    /** The disk cache kept under its size: the pictures not drawn for longest go first. */
    private static void prune() {
        File[] all = disk == null ? null : disk.listFiles();
        if (all == null) return;
        long total = 0;
        for (File f : all) total += f.length();
        if (total <= DISK_MAX) return;
        Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : all) {
            if (total <= DISK_KEEP) break;
            total -= f.length();
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
