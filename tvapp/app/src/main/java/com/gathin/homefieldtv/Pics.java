package com.gathin.homefieldtv;

import android.content.ComponentCallbacks2;
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
     * (as LOGO makes it: its own colours, the shared halo behind a dark one) on a deep ground tinted by the logo's own
     * colour, a soft round glow of that colour behind it, the logo itself low on the right, clear of most of the words.
     * Asked for with the logo's address.
     */
    static final int LOGO_ART = 12;
    /**
     * BLUR is the 07 page's picture behind everything (Ground, Frost): read at an eighth of the size asked (w x h, the
     * size it is drawn), stack-blurred once and its colour pushed as the mock-ups' blur(50px) saturate(1.75)
     * brightness(.86); drawn scaled up, it costs a few hundred KB of the budget, never a blur while drawing.
     */
    static final int BLUR = 13;
    /**
     * LOGO is a channel's logo with no tile (Logos): a plain box round it taken away and its empty margins trimmed, its
     * colours as they are, at its size in a w x h box (as on a tile); one mostly too dark for a dark ground has tv-logos'
     * white version instead where there is one (LogoPack.lighter), else the shared soft halo behind its shape (LogoHalo:
     * never a box), the picture a little larger than the logo for it.
     */
    static final int LOGO = 14;
    /**
     * TINT is a channel's logo on a tile of its own colour, w x h (Logos: Behind logos: Logo's colour, as it comes): the
     * shared rule's colours (LogoColour, from the logo as it comes: a deep shade of its main colour with a soft fall,
     * lighter or deeper until the logo reads on it; a neutral slate for a logo with no real colour), the logo as it is.
     * Worked out once as it is read; its colours kept for its address (Logos.tint) for its channel's cell.
     */
    static final int TINT = 15;
    /**
     * FACE is a programme's picture cropped to w x h round its faces (found once a picture as it is read: Faces; the
     * upper middle when none), whole, toward the right (FACE_BIAS) so words on the left stay clear of them; FACE_MID
     * centres them (a card whose words are under it). ART does the same for a panel's picture, its left fade ending
     * before the faces. Pics.faces(url) gives the box found.
     */
    static final int FACE = 16, FACE_MID = 17;
    /**
     * PANEL is 07's panel picture (the guide's details panel, the shelf guide's sheet, catch-up's and the reminder's
     * cards): FACE's crop round the faces, at its own shape, its left edge faded to nothing over at most PANEL_FADE of
     * its width and ending before the faces, its right-hand corners rounded (PANEL_RADIUS of its height); no darkening
     * and no foot fade (the family: "you can see faces clearly").
     */
    static final int PANEL = 18;
    static final float PANEL_FADE = 0.3f, PANEL_RADIUS = 34f / 330f;
    /**
     * FILL is a programme's picture filling a whole cell of any size, w x h (the guide's cells, the recent channels' tiles), its
     * faces whole (PicFill: covering, else the ambient fill: the picture blurred behind and sharp where its faces are);
     * FILL_CARD the same for the info card, its faces between its words and what's next. Opaque, nothing darkened: the
     * words' shade is drawn over it where the words are (PicTile.shade).
     */
    static final int FILL = 19, FILL_CARD = 20;
    /**
     * FILL and FILL_CARD as Motion's Lite tier makes them (kept apart from Full's, the tier changing): an ambient fill's
     * ground is the picture's own colour rather than its blur, and its sharp picture's edges fade into that colour as it
     * is drawn (no blur and no second full-size sheet while it is read: lighter on the TV's processor and memory). Asked
     * for as FILL or FILL_CARD; Pics picks these itself.
     */
    static final int FILL_LITE = 24, FILL_CARD_LITE = 25;
    /**
     * CELL_COLOUR, CELL_NONE and CELL_BLACK are a channel's logo alone on its cell (ChannelLook), as large as w x h
     * allows at its own shape (the box is the cell less its room), read sharp from the logo itself at that size: on its
     * colour's deep cell (its colours worked out as it is read; a logo that would not read there takes tv-logos' white
     * version, else the shared halo judged on the cell), bare on the plain row (the halo judged on 07's ground), or made
     * ready for black (Behind logos: Black tile).
     */
    static final int CELL_COLOUR = 21, CELL_NONE = 22, CELL_BLACK = 23;
    /** Where across a FACE crop the faces' middle goes (words on the left). */
    static final float FACE_BIAS = 0.62f;

    /** Each picture's faces as found when it was read: {left, top, right, bottom (shares), its width / height}. */
    private static final java.util.Map<String, float[]> FACES = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, float[]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, float[]> e) {
            return size() > 800;
        }
    });

    /** A read picture's faces: {left, top, right, bottom} as shares of it, then its width / height; null before. */
    static float[] faces(String url) {
        return url == null ? null : FACES.get(url);
    }

    /** Previews: a drawn picture's faces given straight in (no detector there). */
    static void knownFaces(String url, float l, float t, float r, float b, float aspect) {
        FACES.put(url, new float[]{l, t, r, b, aspect});
    }

    /** Found once for a picture (its reader's thread), then kept by its address. */
    private static float[] facesOf(String url, Bitmap src) {
        float[] had = FACES.get(url);
        if (had != null) return had;
        float[] f = Faces.find(src);
        float[] kept = {f[0], f[1], f[2], f[3], src.getWidth() / (float) Math.max(1, src.getHeight())};
        FACES.put(url, kept);
        return kept;
    }

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

    /**
     * Whether Settings › Appearance › Logos › Behind logos is Black tile (the old look's), kept by Logos (which holds the
     * choice: Logo's colour as it comes, None, Black tile).
     */
    static volatile boolean tiles = false;

    /** A channel logo's mode for the setting in use (Logos.mode: TINT, LOGO or TILE). */
    static int logoMode() {
        return Logos.mode();
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
        Logos.load(c);
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
        if (Motion.lite() && (mode == FILL || mode == FILL_CARD)) mode = mode == FILL ? FILL_LITE : FILL_CARD_LITE;
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

    /**
     * A channel's own picture (ProgrammePictures, for a programme nothing was found for): ON_COLOUR and its logo's address,
     * drawn as the logo on its own colour at the size asked, so no programme is blank.
     */
    static final String ON_COLOUR = "on-colour:";

    private static Bitmap load(Key k) {
        if (k.url.startsWith(ON_COLOUR)) return onColour(k.url.substring(ON_COLOUR.length()), k.w, k.h);
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
        if (mode == BLUR) return blurred(in, url, o, w, h);
        if (mode == FILL || mode == FILL_CARD || mode == FILL_LITE || mode == FILL_CARD_LITE) return filled(in, o, url, w, h, mode);
        if (mode == CELL_COLOUR || mode == CELL_NONE || mode == CELL_BLACK) {
            Bitmap logo = sharp(in, o, w, h);
            return logo == null ? null : Logos.cell(url, logo, w, h, mode);
        }
        int sample = 1;
        boolean fit = mode == FIT || mode == TILE || mode == READY || mode == LOGO_ART || mode == LOGO || mode == TINT;
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
        if (mode == LOGO || mode == TINT || mode == LOGO_ART) {
            // Its colours from the logo as it comes (the shared rule, LogoColour: the same on the phone and the
            // launcher), kept for its address; then its box taken away and trimmed.
            Logos.Tint tint = mode == LOGO ? null : Logos.colours(url, src);
            Bitmap clear = com.gathin.broadcast.LogoTile.clear(src);
            src.recycle();
            if (clear == null) return mode == TINT ? Logos.onColour(tint, null, w, h) : null;
            // On its own colour's tile, as it is (the tile is the one made to read under it).
            if (mode == TINT) return Logos.onColour(tint, clear, w, h);
            // Bare: too dark for a dark ground, tv-logos' white version where there is one; else LogoHalo's halo.
            if (darkLogo(clear)) {
                Bitmap white = lighter(url, w, h);
                if (white != null) {
                    clear.recycle();
                    clear = white;
                }
            }
            if (mode == LOGO) return Logos.bare(clear, w, h);
            // The stand-in for a programme's picture: the logo bare at its size there, on its own colour.
            return logoArt(Logos.bare(clear, Math.max(2, Math.round(w * 0.40f)), Math.max(2, Math.round(h * 0.48f))), tint, w, h);
        }
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
        if (!fit && (mode == FACE || mode == FACE_MID || mode == ART || mode == PANEL)) {
            // A programme's picture: its own black bars cropped away, its faces found once (kept by its address), the crop
            // round them, whole.
            src = trimmed(src);
            float[] box = facesOf(url, src);
            float sw = src.getWidth(), sh = src.getHeight(), bias = mode == FACE_MID ? 0.5f : FACE_BIAS;
            android.graphics.RectF win = new android.graphics.RectF();
            Faces.crop(sw, sh, w / (float) h, box, bias, win);
            Bitmap out = Bitmap.createBitmap(w, h, src.hasAlpha() ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            new Canvas(out).drawBitmap(src, new android.graphics.Rect(Math.round(win.left), Math.round(win.top), Math.round(win.right),
                    Math.round(win.bottom)), new android.graphics.RectF(0, 0, w, h), p);
            src.recycle();
            if (mode != ART && mode != PANEL) return out;
            // The panel's picture: its left fade ends before the faces.
            android.graphics.RectF f = new android.graphics.RectF();
            Faces.place(sw, sh, w / (float) h, box, bias, w, h, f);
            return mode == PANEL ? panel(out, f.left) : art(out, true, f.left);
        }
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
        return art(src, darker, Float.MAX_VALUE);
    }

    /** ART with its left fade ending before faceLeft (px across it: the faces stay clear), a fifth of it at least. */
    private static Bitmap art(Bitmap src, boolean darker, float faceLeft) {
        int w = src.getWidth(), h = src.getHeight();
        float fadeTo = Math.max(w * 0.2f, Math.min(w * ART_FADE_LEFT, faceLeft - w * 0.03f));
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
        float left = Math.min(w * ART_FADE_LEFT, fadeTo);
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
     * 07's panel picture (PANEL): its left edge faded to nothing over PANEL_FADE of its width at most, the fade ending
     * before faceLeft (px across it), a sixth of it at least; its right-hand corners rounded. Nothing darkened.
     */
    private static Bitmap panel(Bitmap src, float faceLeft) {
        int w = src.getWidth(), h = src.getHeight();
        float fade = Math.max(w / 6f, Math.min(w * PANEL_FADE, faceLeft - w * 0.02f));
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        shape.setShader(new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        float r = h * PANEL_RADIUS;
        // Rounded at the right only: the left is faded away anyway.
        c.drawRoundRect(-r * 2f, 0, w, h, r, r, shape);
        src.recycle();
        Paint m = new Paint();
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        m.setShader(new LinearGradient(0, 0, fade, 0, new int[]{0x00000000, 0x8C000000, 0xFF000000}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, fade, h, m);
        return out;
    }

    /**
     * The stand-in for a programme's picture (LOGO_ART): a deep ground tinted by the logo's own colour, a soft round glow
     * of it with no edge anywhere, the logo itself low on the right and a little faded, then ART's shape and fades.
     */
    private static Bitmap logoArt(Bitmap ready, Logos.Tint colours, int w, int h) {
        // The logo's own colour (its channel cell's hue, LogoColour's rule, brought up bright), else its average.
        int tint = tintOf(ready);
        if (colours != null && !colours.neutral) {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(colours.cellTop, hsv);
            hsv[2] = 0.9f;
            tint = android.graphics.Color.HSVToColor(hsv);
        }
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
        // The logo as Logos.bare placed it (its halo round it where it is dark), at its own size.
        p.setAlpha(0xA6);
        c.drawBitmap(ready, cx - ready.getWidth() / 2f, cy - ready.getHeight() / 2f, p);
        ready.recycle();
        return art(ground, true);
    }

    /** Pure enough: whether a made-clear logo is mostly too dark for a dark ground. */
    private static boolean darkLogo(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        return Logos.dark(px);
    }

    /**
     * A dark logo's white version from tv-logos (LogoPack.lighter), read and made clear as the logo itself is, when there
     * is one and it reads on a dark ground; else null.
     */
    private static Bitmap lighter(String url, int w, int h) {
        String alt;
        try {
            alt = com.gathin.homefieldtv.engine.LogoPack.lighter(url);
        } catch (RuntimeException e) {
            alt = null;
        }
        if (alt == null || alt.equals(url) || failed.contains(alt)) return null;
        Source s = source(alt);
        if (s == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = s.open()) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w || o.outHeight / (sample * 2) >= h) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap src;
            try (InputStream in = s.open()) {
                src = BitmapFactory.decodeStream(in, null, d);
            }
            if (src == null) return null;
            Bitmap clear = com.gathin.broadcast.LogoTile.clear(src);
            src.recycle();
            if (clear == null) return null;
            if (darkLogo(clear)) {
                clear.recycle();
                return null;
            }
            return clear;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * BLUR: the picture read at an eighth of w x h, filling it (its middle kept), stack-blurred once and its colour pushed
     * (Look07: blur 50, saturate 1.75, brightness 0.86); drawn at w x h it is the 07 page's blurred picture.
     */
    private static Bitmap blurred(Source in, String url, BitmapFactory.Options o, int w, int h) throws IOException {
        int bw = Math.max(2, Math.round(w / (float) Look07.BLUR_DOWNSCALE)), bh = Math.max(2, Math.round(h / (float) Look07.BLUR_DOWNSCALE));
        int sample = 1;
        while (o.outWidth / (sample * 2) >= bw && o.outHeight / (sample * 2) >= bh) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap src;
        try (InputStream s = in.open()) {
            src = BitmapFactory.decodeStream(s, null, d);
        }
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        float sc = Math.max(bw / (float) src.getWidth(), bh / (float) src.getHeight());
        Matrix m = new Matrix();
        m.setScale(sc, sc);
        m.postTranslate((bw - src.getWidth() * sc) / 2f, (bh - src.getHeight() * sc) / 2f);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        int[] px = new int[bw * bh];
        out.getPixels(px, 0, bw, 0, 0, bw, bh);
        // The blur's deviation at this size (the mock-ups' 50 px of a 1920 page), as a stack blur's radius.
        float sigma = Look07.BLUR_RADIUS * (w / 1920f) / Look07.BLUR_DOWNSCALE;
        StackBlur.blur(px, bw, bh, Math.max(1, Math.round(sigma * 2.45f)));
        StackBlur.colour(px, Look07.BLUR_SATURATE, Look07.BLUR_BRIGHTNESS);
        out.setPixels(px, 0, bw, 0, 0, bw, bh);
        TINTS.put(url, average(px));
        return out;
    }

    /** Each page picture's own colour (the average of its blurred copy), by its address, for Ground's Lite tint. */
    private static final java.util.Map<String, Integer> TINTS = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Integer>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> e) {
            return size() > 800;
        }
    });

    /** A page picture's own colour once its blurred copy has been made (Ground, Lite), or 0 before. */
    static int tintOf(String url) {
        Integer c = url == null || url.isEmpty() ? null : TINTS.get(url);
        return c == null ? 0 : c;
    }

    /** Pure: the average colour of some pixels (opaque). */
    static int average(int[] px) {
        long r = 0, g = 0, b = 0;
        for (int c : px) {
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        int n = Math.max(1, px.length);
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /** The ambient fill's blurred ground: its brightness and saturation (a little quieter than the sharp picture over it). */
    static final float FILL_GROUND_BRIGHT = 0.82f, FILL_GROUND_SATURATE = 1.15f;

    /**
     * FILL and FILL_CARD: the picture read sharp enough to be zoomed as PicFill may (ZOOM past covering), its faces found
     * once (kept by its address), laid out by PicFill and made once at w x h: covering, or the ambient fill (the picture
     * blurred to cover the cell, the sharp picture over it with its inner edges feathered into the blur). Opaque.
     */
    private static Bitmap filled(Source in, BitmapFactory.Options o, String url, int w, int h, int mode) throws IOException {
        float cover = Math.max(w * o.outHeight / (float) o.outWidth, h), need = cover * PicFill.ZOOM;
        int sample = 1;
        while (o.outHeight / (sample * 2) >= need) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap src;
        try (InputStream s = in.open()) {
            src = BitmapFactory.decodeStream(s, null, d);
        }
        if (src == null) return null;
        // Its own black bars (pillarbox, letterbox, a border) and a pixel or two of its edge cropped away first: never a
        // dark line through a cell (the faces are found in what is left).
        src = trimmed(src);
        float[] f = facesOf(url, src);
        float sw = src.getWidth(), sh = src.getHeight();
        float[] lay = new float[PicFill.SIZE];
        boolean card = mode == FILL_CARD || mode == FILL_CARD_LITE, lite = mode == FILL_LITE || mode == FILL_CARD_LITE;
        PicFill.layout(sw / sh, f, w, h, card, lay);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        Matrix m = new Matrix();
        float k = lay[PicFill.K] / sh;
        m.setScale(k, k);
        m.postTranslate(lay[PicFill.X], lay[PicFill.Y]);
        if (lay[PicFill.KIND] == PicFill.AMBIENT && lite) {
            // Lite: the picture's own colour behind, the sharp picture over it, its edges faded into that colour as it
            // is drawn (a mask composed with it: no blur, no second sheet).
            c.drawColor(ambientColour(src));
            BitmapShader sharp = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            sharp.setLocalMatrix(m);
            float x0 = lay[PicFill.X], x1 = x0 + sw * k, fl = lay[PicFill.FEATHER_L], fr = lay[PicFill.FEATHER_R];
            float w0 = Math.max(1f, x1 - x0), a = Math.max(0f, Math.min(0.49f, fl / w0)), b = Math.max(0f, Math.min(0.49f, fr / w0));
            Shader mask = new LinearGradient(x0, 0, x1, 0, new int[]{a > 0f ? 0 : 0xFF000000, 0xFF000000, 0xFF000000, b > 0f ? 0 : 0xFF000000},
                    new float[]{0f, Math.max(0.0001f, a), Math.min(0.9999f, 1f - b), 1f}, Shader.TileMode.CLAMP);
            Paint q = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            q.setShader(new ComposeShader(sharp, mask, PorterDuff.Mode.DST_IN));
            c.drawRect(Math.max(0f, x0), 0, Math.min(w, x1), h, q);
        } else if (lay[PicFill.KIND] == PicFill.AMBIENT) {
            fillGround(c, src, w, h, p);
            // The sharp picture on a clear sheet of its own (the 16-bit picture has no clear), its edges feathered.
            Bitmap sheet = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas sc = new Canvas(sheet);
            sc.drawBitmap(src, m, p);
            Paint cut = new Paint();
            cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            float x0 = lay[PicFill.X], x1 = x0 + sw * k, fl = lay[PicFill.FEATHER_L], fr = lay[PicFill.FEATHER_R];
            int[] ramp = {0x00000000, 0x8C000000, 0xFF000000};
            float[] at = {0f, 0.55f, 1f};
            if (fl > 1f) {
                cut.setShader(new LinearGradient(x0, 0, x0 + fl, 0, ramp, at, Shader.TileMode.CLAMP));
                sc.drawRect(x0, 0, x0 + fl, h, cut);
            }
            if (fr > 1f) {
                cut.setShader(new LinearGradient(x1, 0, x1 - fr, 0, ramp, at, Shader.TileMode.CLAMP));
                sc.drawRect(x1 - fr, 0, x1, h, cut);
            }
            c.drawBitmap(sheet, 0, 0, p);
            sheet.recycle();
        } else {
            // Covering: over its own colour, so a part-pixel at an edge never comes out black.
            c.drawColor(ambientColour(src));
            c.drawBitmap(src, m, p);
        }
        src.recycle();
        return out;
    }

    /** The bars are looked for on a copy at most this many pixels across. */
    static final int LOOK_BARS = 320;

    /** A programme's picture with its own black bars and a pixel or two of its edge cropped away (Pillarbox). */
    static Bitmap trimmed(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        // Looked at small (at most LOOK_BARS across: a few hundred kilobytes, never the whole picture's pixels), the crop
        // taken back to the picture's size rounded outward.
        float s = Math.min(1f, LOOK_BARS / (float) Math.max(w, h));
        int lw = Math.max(1, Math.round(w * s)), lh = Math.max(1, Math.round(h * s));
        Bitmap look = s < 1f ? Bitmap.createScaledBitmap(src, lw, lh, false) : src;
        int[] px = new int[lw * lh];
        look.getPixels(px, 0, lw, 0, 0, lw, lh);
        if (look != src) look.recycle();
        int[] small = Pillarbox.crop(px, lw, lh), cut = new int[4];
        for (int i = 0; i < 4; i++) {
            boolean bar = small[i] > Math.min(Pillarbox.INSET, Math.min(lw, lh) / 16);
            cut[i] = bar ? (int) Math.ceil((small[i] + 1) / s) : Math.min(Pillarbox.INSET, Math.min(w, h) / 16);
        }
        int cw = w - cut[0] - cut[2], ch = h - cut[1] - cut[3];
        if (cut[0] + cut[1] + cut[2] + cut[3] == 0 || cw < 8 || ch < 8) return src;
        Bitmap out = Bitmap.createBitmap(src, cut[0], cut[1], cw, ch);
        if (out != src) src.recycle();
        return out;
    }

    /** Tests: a picture read now on this thread as a screen would have it (w x h, mode), or null. */
    static Bitmap readNow(String url, int w, int h, int mode) {
        return load(probe(url, w, h, mode).copy());
    }

    /** Lite's ambient ground: the picture's own colour (its average, quietened as the blurred ground is). */
    private static int ambientColour(Bitmap src) {
        Bitmap one = Bitmap.createScaledBitmap(src, 4, 4, true);
        int[] px = new int[16];
        one.getPixels(px, 0, 4, 0, 0, 4, 4);
        if (one != src) one.recycle();
        int[] avg = {average(px)};
        StackBlur.colour(avg, FILL_GROUND_SATURATE, FILL_GROUND_BRIGHT);
        return avg[0] | 0xFF000000;
    }

    /** The ambient fill's ground: the picture covering w x h at an eighth of its size, stack-blurred once, drawn back up. */
    private static void fillGround(Canvas c, Bitmap src, int w, int h, Paint p) {
        int gw = Math.max(2, w / 8), gh = Math.max(2, h / 8);
        Bitmap g = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
        float sc = Math.max(gw / (float) src.getWidth(), gh / (float) src.getHeight());
        Matrix m = new Matrix();
        m.setScale(sc, sc);
        m.postTranslate((gw - src.getWidth() * sc) / 2f, (gh - src.getHeight() * sc) * 0.38f);
        new Canvas(g).drawBitmap(src, m, p);
        int[] px = new int[gw * gh];
        g.getPixels(px, 0, gw, 0, 0, gw, gh);
        StackBlur.blur(px, gw, gh, Math.max(2, Math.round(Math.min(gw, gh) * 0.6f)));
        StackBlur.colour(px, FILL_GROUND_SATURATE, FILL_GROUND_BRIGHT);
        g.setPixels(px, 0, gw, 0, 0, gw, gh);
        c.drawBitmap(g, null, new android.graphics.RectF(0, 0, w, h), p);
        g.recycle();
    }

    /**
     * The logo (read as any picture is) on its own colour, w x h: its colour's fall, the logo at about half the height, in
     * the middle, or (a wide cell or card, its words at the left) toward the right, clear of them.
     */
    private static Bitmap onColour(String logo, int w, int h) {
        if (w < 2 || h < 2 || logo.isEmpty()) return null;
        Bitmap mark = null;
        Source src = source(logo);
        if (src != null) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream s = src.open()) {
                    BitmapFactory.decodeStream(s, null, o);
                }
                if (o.outWidth > 0 && o.outHeight > 0) mark = sharp(src, o, Math.max(1, w / 2), Math.max(1, h / 2));
            } catch (IOException e) {
                mark = null;
            }
        }
        Logos.Tint t = mark == null ? Logos.Tint.WAITING : Logos.colours(logo, mark);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.DITHER_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, w, h, t.rowTop, t.rowFoot, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        if (mark != null) {
            boolean wide = w >= 2 * h;
            float k = Math.min(w * (wide ? 0.24f : 0.42f) / mark.getWidth(), h * 0.46f / mark.getHeight());
            float mw = mark.getWidth() * k, mh = mark.getHeight() * k, cx = wide ? w * 0.72f : w / 2f;
            android.graphics.RectF dst = new android.graphics.RectF(cx - mw / 2f, (h - mh) / 2f, cx + mw / 2f, (h + mh) / 2f);
            c.drawBitmap(mark, null, dst, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            mark.recycle();
        }
        return out;
    }

    /**
     * A channel logo read sharp for a cell's box w x h: sampled down only while both its sides stay at least the box's
     * (so even after its margins are trimmed it is drawn from enough pixels), in full colour; null when it can't be.
     */
    private static Bitmap sharp(Source in, BitmapFactory.Options o, int w, int h) throws IOException {
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream s = in.open()) {
            return BitmapFactory.decodeStream(s, null, d);
        }
    }

    /**
     * A logo's white version from tv-logos (LogoPack.lighter), read sharp for a cell's box w x h as the logo itself is,
     * or null (none, not one of the project's, or it can't be read). Pics' readers only.
     */
    static Bitmap lighterSharp(String url, int w, int h) {
        String alt;
        try {
            alt = com.gathin.homefieldtv.engine.LogoPack.lighter(url);
        } catch (RuntimeException e) {
            alt = null;
        }
        if (alt == null || alt.equals(url) || failed.contains(alt)) return null;
        Source s = source(alt);
        if (s == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream i = s.open()) {
                BitmapFactory.decodeStream(i, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            return sharp(s, o, w, h);
        } catch (IOException | RuntimeException e) {
            return null;
        }
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
