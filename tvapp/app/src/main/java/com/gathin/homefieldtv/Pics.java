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

import com.gathin.broadcast.PicArt;
import com.gathin.broadcast.PicPlace;
import com.gathin.broadcast.PicSources;
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
 *
 * <p>Programme pictures (FILL, FILL_CARD, SLIDE, PANEL, ART, FACE_MID, CARD) are placed by the rule shared with the phone
 * (PicPlace): covering only a place close to their own shape, else shown whole at the place's height with the place's
 * own colour from the picture continuing behind it (PicArt), never enlarged past 1.5 times. A programme's address may be
 * a chain of them, best first (PicSources: the guide's own, Sky's, TMDB's, then one always there: a game's crests, its
 * kind's picture or the channel's own), and each place takes the first in it that is large enough there; the made-up
 * ones ("fixture:", "genre:", "brand:") are drawn here (PicArt) as any other picture is read.
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
     * CARD is a programme's picture as a channel's tile on the live screens: placed by PicPlace (covering a tile of its
     * shape, else shown whole on its own colour), its corners rounded, a little darker at the top (for the channel's
     * logo) and much darker towards its foot (for the words), done once as it loads.
     */
    static final int CARD = 9;
    /**
     * POSTER_WIDE is a landscape poster card (Settings, Posters: Landscape): POSTER's rounding and fade on a 16:9 card,
     * its corners a share of its height and its fade rising from 24% of the way down (PosterArt.fadeShader).
     */
    static final int POSTER_WIDE = 10;
    /**
     * ART is a programme's picture behind a panel's words (PanelArt): placed by PicPlace after the panel's left fade,
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
     * FACE is a picture cropped to w x h round its faces (found once a picture as it is read: Faces; the upper middle
     * when none), whole, toward the right (FACE_BIAS) so words on the left stay clear of them (Home's and the films'
     * pictures). FACE_MID is a programme's picture on a card whose words are under it: placed by PicPlace in the
     * card's middle (covering a card of its shape, else shown whole on its own colour, faces whole). Pics.faces(url)
     * gives the box found.
     */
    static final int FACE = 16, FACE_MID = 17;
    /**
     * PANEL is 07's panel picture (the guide's details panel): placed by PicPlace after its left fade (covering the box
     * when the picture is of its shape, else shown whole at its height on its own colour, never zoomed in), its left
     * edge faded to nothing over at most PANEL_FADE of its width and ending before the faces, its right-hand corners
     * rounded (PANEL_RADIUS of its height); no darkening and no foot fade (the family: "you can see faces clearly").
     */
    static final int PANEL = 18;
    static final float PANEL_FADE = 0.3f, PANEL_RADIUS = 34f / 330f;
    /**
     * FILL is a programme's picture filling a whole place of any size, w x h (the recent channels' tiles, the channel
     * list's card), placed by PicPlace (PicFill): covering a place close to its own shape, else the whole picture (or
     * nearly) at the place's height beside the words, its faces whole, the place's own colour from its edges drawn out
     * behind it (PicArt.compose); FILL_CARD the same for the info card and the reminder's, its faces between its words
     * and what's next. Opaque, nothing darkened: the words' shade is drawn over it where the words are (PicTile.shade).
     */
    static final int FILL = 19, FILL_CARD = 20;
    /**
     * SLIDE is a guide cell's picture (PicTile.fill): the picture alone as PicPlace puts it in a cell w x h (its whole
     * programme's width), its inner edges faded to nothing, with where it goes and its edges' colours (placed()), so the
     * cell draws its ground and the picture glides along the cell as the guide scrolls (PicPlace.slide), never made again.
     */
    static final int SLIDE = 26;
    /**
     * FILL and FILL_CARD as Motion's Lite tier asks for them (kept apart from Full's, the tier changing): made the same
     * way now, as no blur is made for either (the ground is the picture's edges' colours drawn out). Asked for as FILL or
     * FILL_CARD; Pics picks these itself.
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

    /**
     * A programme picture as made (FILL, FILL_CARD, SLIDE, PANEL...): where PicPlace put it in its place (lay), and for
     * SLIDE where the picture made sits across the place (at) and its edges' colours (edges, null when it covers).
     */
    static final class Placed {
        final float[] lay;
        final float at;
        final Bitmap edges;
        final boolean faces;
        private BitmapShader ground;

        Placed(float[] lay, float at, Bitmap edges, boolean faces) {
            this.lay = lay;
            this.at = at;
            this.edges = edges;
            this.faces = faces;
        }

        /** The ground's shader (made the first time it is drawn: main thread); null when the picture covers its place. */
        BitmapShader ground() {
            if (ground == null && edges != null) ground = new BitmapShader(edges, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            return ground;
        }
    }

    private static final java.util.Map<Bitmap, Placed> PLACED = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Where a programme picture made by this went in its place, or null (not one of them). */
    static Placed placed(Bitmap b) {
        return b == null ? null : PLACED.get(b);
    }

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
        if (PicSources.isChain(k.url)) return chained(k);
        return one(k.url, k.w, k.h, k.mode, false);
    }

    /**
     * A chain of pictures (PicSources.chain), best first: the first that can be read and, for a programme picture, is
     * large enough for this place (PicPlace.MAX_UP); the last one as it is. Its faces kept for the chain's address too.
     */
    private static Bitmap chained(Key k) {
        List<String> parts = PicSources.parts(k.url);
        for (int i = 0; i < parts.size(); i++) {
            String u = parts.get(i);
            Bitmap b = failed.contains(u) ? null : one(u, k.w, k.h, k.mode, i < parts.size() - 1);
            if (b == null) continue;
            float[] f = FACES.get(u);
            if (f != null) FACES.put(k.url, f);
            return b;
        }
        return null;
    }

    /** One picture at w x h in mode; strict: null when it is too small for a programme picture's place. */
    private static Bitmap one(String url, int w, int h, int mode, boolean strict) {
        if (url.startsWith(ON_COLOUR)) return onColour(url.substring(ON_COLOUR.length()), w, h);
        Source src = PicSources.madeUp(url) ? madeUp(url, w, h) : source(url);
        if (src == null) return null;
        loads++;
        try {
            return decode(src, url, w, h, mode, strict);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Whether a mode is a programme picture's, placed by PicPlace. */
    static boolean placedMode(int mode) {
        return mode == FILL || mode == FILL_CARD || mode == FILL_LITE || mode == FILL_CARD_LITE || mode == SLIDE || mode == PANEL
                || mode == ART || mode == FACE_MID || mode == CARD;
    }

    /**
     * A made-up picture (a game's crests, a kind's picture, a channel's colour: PicArt) drawn for a place w x h, 16:9 and
     * a little taller than it, handed over as a picture's bytes so it is placed and finished as any other.
     */
    private static Source madeUp(String url, int w, int h) {
        int[] size = PicArt.size(w, h);
        Bitmap b;
        String kind = PicSources.genreOf(url);
        if (kind != null) {
            b = PicArt.genre(kind, size[0], size[1]);
        } else if (url.startsWith(PicSources.BRAND_AT)) {
            b = PicArt.brand(PicSources.brandColour(url), size[0], size[1]);
        } else {
            String[] crests = PicSources.fixtureCrests(url);
            int[] cols = PicSources.fixtureColours(url);
            int cs = Math.round(size[1] * 0.6f);
            Bitmap home = crest(crests[0], cs), away = crest(crests[1], cs);
            b = PicArt.fixture(home, away, cols[0], cols[1], size[0], size[1]);
            if (home != null) home.recycle();
            if (away != null) away.recycle();
        }
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(64 * 1024);
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        b.recycle();
        // Nothing in it to keep whole: no faces looked for.
        FACES.put(url, new float[]{Faces.FALLBACK[0], Faces.FALLBACK[1], Faces.FALLBACK[2], Faces.FALLBACK[3], size[0] / (float) size[1]});
        byte[] bytes = o.toByteArray();
        return () -> new ByteArrayInputStream(bytes);
    }

    /** A club's crest read sharp for size x size (null with none, or when it can't be read). */
    private static Bitmap crest(String url, int size) {
        if (url == null || url.isEmpty() || failed.contains(url)) return null;
        Source src = source(url);
        if (src == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream s = src.open()) {
                BitmapFactory.decodeStream(s, null, o);
            }
            return o.outWidth > 0 && o.outHeight > 0 ? sharp(src, o, size, size) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Bitmap decode(Source in, String url, int w, int h, int mode, boolean strict) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream s = in.open()) {
            BitmapFactory.decodeStream(s, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        if (mode == BLUR) return blurred(in, url, o, w, h);
        if (placedMode(mode)) return placed(in, o, url, w, h, mode, strict);
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
        if (!fit && mode == FACE) {
            // A programme's picture: its own black bars cropped away, its faces found once (kept by its address), the crop
            // round them, whole.
            src = trimmed(src);
            float[] box = facesOf(url, src);
            float sw = src.getWidth(), sh = src.getHeight();
            android.graphics.RectF win = new android.graphics.RectF();
            Faces.crop(sw, sh, w / (float) h, box, FACE_BIAS, win);
            Bitmap out = Bitmap.createBitmap(w, h, src.hasAlpha() ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
            new Canvas(out).drawBitmap(src, new android.graphics.Rect(Math.round(win.left), Math.round(win.top), Math.round(win.right),
                    Math.round(win.bottom)), new android.graphics.RectF(0, 0, w, h), p);
            src.recycle();
            return out;
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

    /** A programme picture's free part in its place: where its words end at the left, and where the free part ends. */
    static float roomOf(int mode, float w, float h) {
        if (mode == FILL_CARD || mode == FILL_CARD_LITE) return PicFill.CARD_ROOM;
        if (mode == PANEL || mode == ART) return PANEL_FADE;
        if (mode == FACE_MID || mode == CARD) return 0f;
        return PicPlace.room(w, h);
    }

    static float endOf(int mode) {
        return mode == FILL_CARD || mode == FILL_CARD_LITE ? PicFill.CARD_END : 1f;
    }

    /**
     * A programme picture (FILL, FILL_CARD, SLIDE, PANEL, ART, FACE_MID, CARD) placed in w x h by PicPlace: read at the
     * size it is shown (sampled down while it is read, never enlarged past PicPlace.MAX_UP: null when strict and it would
     * be, the chain's next then), its own black bars cropped away, its faces found once (kept by its address); then
     * covering its place, or shown whole with its edges' colours continuing behind it (PicArt.compose), and finished as
     * its mode asks (the panel's left fade before the faces, the card's rounding and shade). SLIDE: the picture alone,
     * with where it sits and its edges' colours (placed()). Where it went is kept with the picture made (placed()).
     */
    private static Bitmap placed(Source in, BitmapFactory.Options o, String url, int w, int h, int mode, boolean strict) throws IOException {
        float room = roomOf(mode, w, h), end = endOf(mode);
        float[] lay = new float[PicPlace.SIZE];
        float[] known = FACES.get(url);
        PicPlace.place(o.outWidth, o.outHeight, known, w, h, room, end, lay);
        if (strict && PicPlace.tooSmall(lay)) return null;
        int sample = 1;
        while (o.outHeight / (sample * 2) >= lay[PicPlace.PH] && o.outWidth / (sample * 2) >= lay[PicPlace.PW]) sample *= 2;
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
        PicPlace.place(src.getWidth(), src.getHeight(), f, w, h, room, end, lay);
        boolean faces = !PicPlace.none(f);
        Bitmap out;
        if (mode == SLIDE) {
            if (lay[PicPlace.KIND] == PicPlace.SHOW && lay[PicPlace.FEATHER_T] == 0f && lay[PicPlace.FEATHER_B] == 0f) {
                // It glides along its cell: both its edges always feathered (never a hard edge wherever it stops).
                float least = Math.min(PicPlace.FEATHER * lay[PicPlace.PW], Math.max(PicPlace.FEATHER_MIN_PX, PicPlace.FEATHER_MIN * h));
                lay[PicPlace.FEATHER_L] = Math.max(lay[PicPlace.FEATHER_L], least);
                lay[PicPlace.FEATHER_R] = Math.max(lay[PicPlace.FEATHER_R], least);
            }
            float[] at = new float[2];
            out = PicArt.sharp(src, lay, w, h, at);
            Bitmap edges = lay[PicPlace.KIND] == PicPlace.COVER ? null : PicArt.edges(src, lay);
            src.recycle();
            PLACED.put(out, new Placed(lay, at[0], edges, faces));
            return out;
        }
        out = PicArt.compose(src, lay, w, h, Bitmap.Config.RGB_565);
        src.recycle();
        float facesLeft = faces ? Math.max(0f, lay[PicPlace.FL]) : lay[PicPlace.KIND] == PicPlace.COVER ? w : Math.max(0f, lay[PicPlace.X]);
        if (mode == PANEL) out = panel(out, facesLeft);
        else if (mode == ART) out = art(out, true, facesLeft);
        else if (mode == CARD) out = card(out);
        PLACED.put(out, new Placed(lay, 0f, null, faces));
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
