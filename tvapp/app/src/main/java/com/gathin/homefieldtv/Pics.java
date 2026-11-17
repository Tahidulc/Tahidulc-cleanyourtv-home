package com.gathin.homefieldtv;

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
import android.util.LruCache;

import com.gathin.homefieldtv.engine.Exec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
 * Posters, backdrops and channel logos: fetched off the main thread (the newest asked for first, so what is on screen
 * comes before what was scrolled past), kept on disk in the app's cache and in memory at the size they are drawn, so a
 * row draws each one with a single copy and the TV's graphics chip does little work.
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
    private static final int THREADS = 3, QUEUE = 48;
    private static final long DISK_MAX = 160L << 20, DISK_KEEP = 120L << 20;

    private static File disk;
    private static final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(budget()) {
        @Override protected int sizeOf(String k, Bitmap b) {
            return b.getAllocationByteCount();
        }
    };
    private static final Map<String, List<Runnable>> waiting = new HashMap<>();
    private static final ArrayDeque<Job> queue = new ArrayDeque<>();
    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<>());
    private static boolean started;

    private static final class Job {
        final String key, url;
        final int w, h, mode;

        Job(String key, String url, int w, int h, int mode) {
            this.key = key;
            this.url = url;
            this.w = w;
            this.h = h;
            this.mode = mode;
        }
    }

    private static int budget() {
        long max = Runtime.getRuntime().maxMemory();
        return (int) Math.max(16L << 20, Math.min(72L << 20, max / 5));
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

    /**
     * The picture at w x h, or null while it loads (then whenReady runs on the main thread). Several asking for the
     * same picture share one load.
     */
    static Bitmap get(String url, int w, int h, int mode, Runnable whenReady) {
        if (url == null || url.isEmpty() || w <= 0 || h <= 0) return null;
        String key = mode + "|" + w + "x" + h + "|" + url;
        Bitmap b = mem.get(key);
        if (b != null) return b;
        if (failed.contains(url)) return null;
        if (Exec.inline) {
            Bitmap got = load(url, w, h, mode);
            if (got == null) failed.add(url);
            else mem.put(key, got);
            return got;
        }
        synchronized (waiting) {
            List<Runnable> l = waiting.get(key);
            if (l != null) {
                if (whenReady != null && !l.contains(whenReady)) l.add(whenReady);
                // Asked for again: to the front of the queue.
                for (Iterator<Job> it = queue.iterator(); it.hasNext(); ) {
                    Job j = it.next();
                    if (j.key.equals(key)) {
                        it.remove();
                        queue.addFirst(j);
                        break;
                    }
                }
                return null;
            }
            l = new ArrayList<>(2);
            if (whenReady != null) l.add(whenReady);
            waiting.put(key, l);
            queue.addFirst(new Job(key, url, w, h, mode));
            // Long gone from the screen: dropped (asked for again when it comes back).
            while (queue.size() > QUEUE) {
                Job old = queue.removeLast();
                waiting.remove(old.key);
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

    /** Already loaded at that size (no load started). */
    static Bitmap cached(String url, int w, int h, int mode) {
        if (url == null || url.isEmpty()) return null;
        return mem.get(mode + "|" + w + "x" + h + "|" + url);
    }

    private static void work() {
        while (true) {
            Job j;
            synchronized (waiting) {
                while (queue.isEmpty()) {
                    try {
                        waiting.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                j = queue.removeFirst();
            }
            Bitmap b = null;
            try {
                b = load(j.url, j.w, j.h, j.mode);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // left out
            }
            if (b == null) failed.add(j.url);
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

    private static Bitmap load(String url, int w, int h, int mode) {
        byte[] data = bytes(url);
        if (data == null) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        boolean fit = mode == FIT || mode == TILE || mode == READY;
        if (!fit) {
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        } else {
            while (o.outWidth / (sample * 2) >= w || o.outHeight / (sample * 2) >= h) sample *= 2;
        }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = fit ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, d);
        if (src == null) return null;
        if (mode == TILE || mode == READY) {
            // Made ready for black and trimmed (LogoTile); nothing of it to draw: no logo (the name instead).
            Bitmap ready = com.gathin.broadcast.LogoTile.ready(src);
            src.recycle();
            if (ready == null) return null;
            if (mode == TILE) {
                Bitmap t = com.gathin.broadcast.LogoTile.tileOf(ready, w, h, Math.min(w, h) * 0.12f);
                ready.recycle();
                return t;
            }
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

    // ---- the bytes: a file (previews), the disk cache, or the network --------------------------------------------

    private static byte[] bytes(String url) {
        if (url.startsWith("file:")) return read(new File(url.substring(5)));
        File f = disk == null ? null : new File(disk, name(url));
        if (f != null && f.exists()) {
            byte[] b = read(f);
            if (b != null) {
                //noinspection ResultOfMethodCallIgnored
                f.setLastModified(System.currentTimeMillis());
                return b;
            }
        }
        byte[] b = download(url);
        if (b != null && f != null) {
            File tmp = new File(f.getPath() + ".part");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(b);
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return b;
            }
            if (!tmp.renameTo(f)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
        return b;
    }

    private static byte[] read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return all(in, 16 << 20);
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] download(String url) {
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
                    return all(in, 8 << 20);
                }
            } catch (IOException | RuntimeException e) {
                return null;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private static byte[] all(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[32 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) {
            b.write(buf, 0, n);
            if (b.size() > max) return null;
        }
        return b.toByteArray();
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
