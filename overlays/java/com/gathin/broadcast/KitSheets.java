package com.gathin.broadcast;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 3D shirts as pictures for the scoreboards: the "3D kits" look's two shirts in their cells, and a goal's shirt turning
 * to show the scorer's name and number. A hidden page (assets broadcast/kits.html: three.js, the shirt of
 * src/kits/kit3d.js in the kit worn, src/kits/designs.js) draws a side's shirt turned through the angles asked for, all the
 * frames on one sheet, and hands the sheet back; here it is cut into frames. One page, made for the work and let go a
 * little after the last of it (a WebView costs the TVs memory), out of sight where the app puts it (Host: a window of
 * its own on the TV, the player's layout on the phone). Main thread.
 */
public final class KitSheets {
    private KitSheets() { }

    private static final String TAG = "KitSheets";
    /** The page let go this long after its last sheet. */
    private static final long IDLE_MS = 25_000L;

    /** Where the hidden page lives: shown (but unseen, alpha 0) while it works, taken away after. */
    public interface Host {
        void attach(View page);

        void detach(View page);
    }

    /** Told once, on the main thread, with the sheet (null: none could be made). */
    public interface Done {
        void sheet(Sheet s);
    }

    /** A sheet of frames: frame i is the shirt at angles[i] (radians round its upright: 0 its front, pi its back). */
    public static final class Sheet {
        public final Bitmap bitmap;
        public final int cols, rows, size;
        public final float[] angles;
        public final String key;
        private Rect src;

        Sheet(String key, Bitmap bitmap, int cols, int rows, int size, float[] angles) {
            this.key = key;
            this.bitmap = bitmap;
            this.cols = cols;
            this.rows = rows;
            this.size = size;
            this.angles = angles;
        }

        /** The frame nearest an angle, among frames from..to (exclusive). */
        public int nearest(float angle, int from, int to) {
            int best = -1;
            double d = Double.MAX_VALUE;
            for (int i = Math.max(0, from); i < Math.min(angles.length, to); i++) {
                double x = Math.abs(wrap(angle - angles[i]));
                if (x < d) { d = x; best = i; }
            }
            return best;
        }

        public int nearest(float angle) {
            return nearest(angle, 0, angles.length);
        }

        /** Frame i drawn into dst. */
        public void draw(Canvas c, int i, RectF dst, Paint p) {
            if (i < 0 || i >= angles.length || bitmap.isRecycled()) return;
            int x = (i % cols) * size, y = (i / cols) * size;
            if (src == null) src = new Rect();
            src.set(x, y, x + size, y + size);
            c.drawBitmap(bitmap, src, dst, p);
        }
    }

    /** Pure: an angle brought into -pi..pi. */
    static double wrap(double a) {
        double x = a % (Math.PI * 2);
        if (x > Math.PI) x -= Math.PI * 2;
        if (x < -Math.PI) x += Math.PI * 2;
        return x;
    }

    /**
     * Pure: the board's frames for a side: its turn in (a whole turn, every 15 degrees from its rest) then its sway (nine
     * frames either side of its rest, 4 degrees apart). rest: the side's own angle (the home side turned a little one way,
     * the away side the other).
     */
    public static float[] boardAngles(float rest) {
        float[] a = new float[24 + 9];
        for (int i = 0; i < 24; i++) a[i] = (float) (rest + i * Math.PI * 2 / 24);
        for (int i = 0; i < 9; i++) a[24 + i] = rest + (i - 4) * 0.07f;
        return a;
    }

    /** The first sway frame in boardAngles' sheets, and their count. */
    public static final int SWAY_AT = 24, SWAY_N = 9, TURN_N = 24;

    /** Pure: a goal's frames: from its front (a little either side) round to its back and a little past, every 0.13 rad. */
    public static float[] turnAngles() {
        int n = 28;
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = -0.45f + i * ((float) Math.PI + 0.75f) / (n - 1);
        return a;
    }

    /** A side for a sheet: the kit it wears (its club's design when its home kit) and its crest. */
    public static JSONObject team(String espnId, String name, String kitType, int color, int color2, String crestUrl) {
        JSONObject o = new JSONObject();
        try {
            o.put("espnId", espnId == null ? "" : espnId);
            o.put("name", name == null ? "" : name);
            o.put("kitType", kitType == null ? "" : kitType);
            if (color != 0) o.put("color", String.format("#%06x", color & 0xFFFFFF));
            if (color2 != 0) o.put("color2", String.format("#%06x", color2 & 0xFFFFFF));
            if (crestUrl != null) o.put("crestUrl", crestUrl);
        } catch (Exception ignored) {
            // the plain kit
        }
        return o;
    }

    // ---- the page ---------------------------------------------------------------------------------------------------

    private static Handler mainHandler;
    private static WebView web;
    /** Where the next page goes (the host last asked with); the page up now keeps its own as its tag, taken out of that. */
    private static Host host;
    private static boolean pageReady;
    private static final ArrayDeque<Job> queue = new ArrayDeque<>();
    private static final Map<String, Job> running = new HashMap<>();
    private static int nextId = 1;
    /** The last sheets made, by key (two boards' and a goal's at most). */
    private static final Map<String, Sheet> made = new LinkedHashMap<String, Sheet>(8, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Sheet> e) {
            return size() > 4;
        }
    };
    private static final Runnable letGo = KitSheets::letGo;

    private static Handler main() {
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        return mainHandler;
    }

    private static final class Job {
        final String key;
        final JSONObject json;
        final float[] angles;
        final java.util.List<Done> done = new java.util.ArrayList<>();
        long sent;

        Job(String key, JSONObject json, float[] angles) {
            this.key = key;
            this.json = json;
            this.angles = angles;
        }
    }

    /**
     * A board's shirts that could not be made: asked for again a minute later, not on every refresh, and given up after a
     * few tries (its flat shirts for that game then). Both sides failing in one go count as one try. Plain Java.
     */
    public static final class Tries {
        public static final long WAIT_MS = 60_000L;
        public static final int MAX = 3;
        private String key;
        private int fails;
        /** When it may be asked for again; 0: not waiting. */
        private long at;

        /** key's shirts not made just now. */
        public void failed(String k, long now) {
            if (!k.equals(key)) {
                key = k;
                fails = 0;
                at = 0;
            }
            if (at == 0) fails++;
            at = fails < MAX ? now + WAIT_MS : Long.MAX_VALUE;
        }

        /** True once when key's shirts failed and their minute has gone by: ask for them again now. */
        public boolean due(String k, long now) {
            if (!k.equals(key) || at == 0 || now < at) return false;
            at = 0;
            return true;
        }

        /** Tried enough for key: its flat shirts. */
        public boolean given(String k) {
            return k.equals(key) && fails >= MAX;
        }
    }

    /** A sheet made already for this key, or null. */
    public static Sheet have(String key) {
        return made.get(key);
    }

    /**
     * A sheet for a side (team()): size px a frame, at these angles, its back with a name and number (both null: none).
     * key names it (the same key: the same sheet, made once). The crest is fetched here, off the main thread, and put in.
     */
    public static void sheet(Context c, Host h, String key, JSONObject team, int size, float[] angles, String backName,
                             String backNumber, Done done) {
        Sheet s = made.get(key);
        if (s != null) { done.sheet(s); return; }
        for (Job j : queue) if (j.key.equals(key)) { j.done.add(done); return; }
        for (Job j : running.values()) if (j.key.equals(key)) { j.done.add(done); return; }
        Context app = c.getApplicationContext();
        JSONObject job = new JSONObject();
        try {
            JSONArray a = new JSONArray();
            for (float f : angles) a.put(Math.round(f * 10000) / 10000.0);
            job.put("size", size).put("angles", a).put("tilt", 0.08).put("webp", true);
            if (backNumber != null && !backNumber.isEmpty()) job.put("back", new JSONObject().put("name", backName == null ? "" : backName).put("number", backNumber));
        } catch (Exception e) {
            done.sheet(null);
            return;
        }
        Job j = new Job(key, job, angles);
        j.done.add(done);
        host = h;
        // the crest as a picture the page can draw, fetched off the main thread
        new Thread(() -> {
            JSONObject t = new JSONObject();
            try {
                t = new JSONObject(team.toString());
                String url = t.optString("crestUrl", "");
                t.remove("crestUrl");
                if (!url.isEmpty()) {
                    String crest = BroadcastData.image(null, url, false);
                    if (crest != null) t.put("crest", crest);
                }
                job.put("team", t);
            } catch (Exception ignored) {
                // no crest on it
            }
            main().post(() -> {
                queue.add(j);
                pump(app);
            });
        }, "kit-crest").start();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private static void pump(Context app) {
        main().removeCallbacks(letGo);
        if (queue.isEmpty()) {
            if (running.isEmpty()) main().postDelayed(letGo, IDLE_MS);
            return;
        }
        // the page up in another host than the one now asked with (the phone's settings, then its player; a player gone):
        // let go once it has nothing on, and a new one made in the new host
        if (web != null && web.getTag() != host && running.isEmpty()) drop(web);
        if (web == null) {
            if (host == null) { fail(); return; }
            pageReady = false;
            WebView w = new WebView(app);
            w.setTag(host);
            w.setBackgroundColor(Color.TRANSPARENT);
            w.setAlpha(0f);
            w.setFocusable(false);
            w.setClickable(false);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(false);
            w.addJavascriptInterface(new Bridge(), "AndroidKits");
            w.setWebViewClient(new WebViewClient() {
                @Override
                public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    main().post(() -> {
                        Log.w(TAG, "the page's process went");
                        drop(view);
                        fail();
                    });
                    return true;
                }
            });
            web = w;
            try {
                host.attach(w);
            } catch (RuntimeException e) {
                Log.w(TAG, "no room for the page: " + e);
                drop(w);
                fail();
                return;
            }
            w.loadUrl("file:///android_asset/broadcast/kits.html");
            // a page that never says it is ready is let go
            main().postDelayed(() -> {
                if (web == w && !pageReady) {
                    Log.w(TAG, "the page did not get ready");
                    drop(w);
                    fail();
                }
            }, 30_000L);
            return;
        }
        if (!pageReady || !running.isEmpty()) return;
        Job j = queue.poll();
        if (j == null) return;
        String id = String.valueOf(nextId++);
        try {
            j.json.put("id", id);
        } catch (Exception ignored) { }
        j.sent = SystemClock.uptimeMillis();
        running.put(id, j);
        web.evaluateJavascript("KITS.job(" + JSONObject.quote(j.json.toString()) + ")", null);
        // a sheet that never comes is let go
        main().postDelayed(() -> {
            Job r = running.remove(id);
            if (r != null) {
                Log.w(TAG, "no sheet for " + r.key + " in time");
                for (Done d : r.done) d.sheet(null);
                pump(app);
            }
        }, 45_000L);
    }

    /** Every job waiting told none could be made. */
    private static void fail() {
        java.util.List<Job> all = new java.util.ArrayList<>(queue);
        all.addAll(running.values());
        queue.clear();
        running.clear();
        for (Job j : all) for (Done d : j.done) d.sheet(null);
    }

    private static void drop(WebView w) {
        if (w == null) return;
        if (web == w) { web = null; pageReady = false; }
        try {
            // out of the host it was put in, whichever has been asked with since
            if (w.getTag() instanceof Host) ((Host) w.getTag()).detach(w);
        } catch (RuntimeException ignored) { }
        try {
            w.removeJavascriptInterface("AndroidKits");
            w.destroy();
        } catch (RuntimeException ignored) { }
    }

    private static void letGo() {
        if (!queue.isEmpty() || !running.isEmpty()) return;
        drop(web);
    }

    /** The sheets let go (the game over, the look changed): their memory back. */
    public static void forget() {
        made.clear();
    }

    /** One sheet let go from the kept ones (a goal's, once played): its memory back when nothing draws it. */
    public static void release(String key) {
        if (key != null) made.remove(key);
    }

    /** What the page calls (on WebView's own thread). */
    private static final class Bridge {
        @JavascriptInterface
        public void ready() {
            main().post(() -> {
                pageReady = true;
                if (web != null) pump(web.getContext());
            });
        }

        @JavascriptInterface
        public void sheet(String id, String url, int cols, int rows, int size, int n, int ms) {
            Bitmap bm = null;
            try {
                int comma = url.indexOf(',');
                byte[] b = Base64.decode(url.substring(comma + 1), Base64.DEFAULT);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                bm = BitmapFactory.decodeByteArray(b, 0, b.length, o);
            } catch (Throwable e) {
                Log.w(TAG, "sheet not read: " + e);
            }
            Bitmap got = bm;
            main().post(() -> {
                Job j = running.remove(id);
                if (j == null) { if (got != null) got.recycle(); return; }
                Sheet s = got == null ? null : new Sheet(j.key, got, cols, rows, size, j.angles);
                if (s != null) made.put(j.key, s);
                Log.i(TAG, j.key + ": " + n + " frames of " + size + " px, drawn in " + ms + " ms, here after "
                        + (SystemClock.uptimeMillis() - j.sent) + " ms");
                for (Done d : j.done) d.sheet(s);
                if (web != null) pump(web.getContext());
            });
        }

        @JavascriptInterface
        public void failed(String id, String why) {
            main().post(() -> {
                Job j = running.remove(id);
                Log.w(TAG, "no sheet: " + why);
                if (j != null) for (Done d : j.done) d.sheet(null);
                if (web != null) pump(web.getContext());
            });
        }

        @JavascriptInterface
        public void log(String m) {
            Log.i(TAG, m);
        }
    }

    // ---- a shirt without its pictures ---------------------------------------------------------------------------------

    private static Path flat;

    /**
     * A plain shirt in its colours, flat, size high about (cx, cy): drawn while the 3D frames are still being made (and in
     * the looks' previews). turn: how far round it is (its width narrows as it turns side on).
     */
    public static void flatShirt(Canvas c, float cx, float cy, float size, int body, int trim, float turn, Paint p) {
        float w = size * 0.92f * Math.max(0.12f, Math.abs((float) Math.cos(turn))), h = size * 0.78f;
        float x0 = cx - w / 2, y0 = cy - h / 2;
        if (flat == null) flat = new Path();
        flat.reset();
        // the outline as kit3d.js draws it, in a w by h box
        float[][] pts = {{0.40f, 0}, {0.6f, 0}, {0.79f, 0.05f}, {1, 0.27f}, {0.9f, 0.39f}, {0.76f, 0.29f}, {0.76f, 1}, {0.24f, 1},
                {0.24f, 0.29f}, {0.1f, 0.39f}, {0, 0.27f}, {0.21f, 0.05f}};
        flat.moveTo(x0 + pts[0][0] * w, y0 + pts[0][1] * h);
        for (int i = 1; i < pts.length; i++) flat.lineTo(x0 + pts[i][0] * w, y0 + pts[i][1] * h);
        flat.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000 | body);
        c.drawPath(flat, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, size * 0.04f));
        p.setColor(0xFF000000 | trim);
        c.drawPath(flat, p);
        p.setStyle(Paint.Style.FILL);
    }
}
