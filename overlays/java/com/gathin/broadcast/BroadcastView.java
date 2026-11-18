package com.gathin.broadcast;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * One of the live overlays playing once: the kick-off pop-up (kickoff.html) or the line-ups (lineups.html, stadium.html,
 * studio.html), drawn
 * by the page over a see-through view (put it over the channel's picture, the size of the picture), its sounds played
 * here as the page calls for them. It takes no keys or touches; Back for the line-ups is the app's, through back().
 * When it is over (or fails, or runs too long) the listener is told once and the view is empty: take it down then.
 * <p>Held (prepare): the page gets everything ready out of sight (the TCL took four to twelve seconds), says so, and
 * waits for go(), so that the app can show it on the moment: keep it full size but unseen meanwhile (a window of alpha 0;
 * a WebView laid out a pixel square and then stretched kept drawing at that size on the TVs), and seen at go().
 */
@SuppressLint("ViewConstructor")
public final class BroadcastView extends FrameLayout {
    /** Told once, on the main thread, when the overlay is over. */
    public interface Listener {
        void onDone(BroadcastView v);
    }

    /** Told once, on the main thread, when a held overlay is ready to go. */
    public interface Ready {
        void onReady(BroadcastView v);
    }

    private static final String TAG = "Broadcast";
    /** Each page's sounds in broadcast/sfx: the names that start so (the strap only its look's: BroadcastData.strapSound). */
    private static String[] sounds(Context c, String page, String cfg) {
        String prefix = "kickoff".equals(page) ? "popup-" : "lineups".equals(page) ? "swish-" : page + "-";
        if ("strap".equals(page)) {
            try {
                JSONObject o = new JSONObject(cfg);
                String sfx = o.optString("sfx", "");
                if (sfx.matches("strap-[a-z0-9_]+")) return new String[]{sfx};
                return new String[]{BroadcastData.strapSound(o.optString("look", "CLASSIC"))};
            } catch (Exception ignored) {
                // all of them, then
            }
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            String[] all = c.getAssets().list("broadcast/sfx");
            for (String f : all == null ? new String[0] : all) {
                if (f.startsWith(prefix) && f.endsWith(".ogg")) out.add(f.substring(0, f.length() - 4));
            }
        } catch (Exception ignored) {
            // none
        }
        return out.toArray(new String[0]);
    }

    /** The pages there are, and the longest each may run before it is taken down. */
    private static long maxMs(String page) {
        switch (page) {
            case "lineups": return 180_000L;
            case "stadium": return 150_000L;
            case "studio": return 90_000L;    // both teams on the beat (45 s), its music played here
            case "strap": return 20_000L;
            case "reveal": return 60_000L;   // a competition's intro (20 s), or a family's own video and the tail
            case "match": return 75_000L;    // the match intro (48 s, or the Champions League's 38), drawn live
            default: return 25_000L;
        }
    }

    private static String pageOf(String page) {
        return "lineups".equals(page) || "strap".equals(page) || "stadium".equals(page) || "studio".equals(page) || "reveal".equals(page)
                || "match".equals(page) ? page : "kickoff";
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final String page, cfg;
    private final Listener listener;
    private WebView web;
    private SoundPool pool;
    /** A long sound the page asked for (an intro's music): the page's own media would take the channel's sound for good. */
    private android.media.MediaPlayer music;
    private boolean musicReady, musicWanted;
    private final Map<String, Integer> sounds = new HashMap<>();
    private float volume = 1f;
    private boolean started, over, held, ready, going;
    private Ready onReady;
    private final long maxMs;
    /** When it was made (uptime), for the log of how long it took to show. */
    private long born;
    private final Runnable tooLong = this::finish;

    /** Any page ("kickoff", "lineups", "strap", "stadium", "studio", "reveal", "match") playing as soon as it is ready. */
    public static BroadcastView page(Context c, String page, JSONObject cfg, Listener l) {
        return page(c, page, cfg.toString(), l);
    }

    /**
     * The same with its settings already written out: a match intro's are megabytes (its pictures), written once off the
     * main thread rather than read and written again on it as a break begins.
     */
    public static BroadcastView page(Context c, String page, String cfgJson, Listener l) {
        String p = pageOf(page);
        return new BroadcastView(c, p, cfgJson, maxMs(p), l, null);
    }

    /** The kick-off pop-up (BroadcastData.kickoff): about eight seconds. */

    public static BroadcastView kickoff(Context c, JSONObject cfg, Listener l) {
        return new BroadcastView(c, "kickoff", cfg.toString(), 25_000L, l, null);
    }

    /**
     * Held: "kickoff" (BroadcastData.kickoff) or "lineups" (BroadcastData.lineups) made ready, then shown by go(). Not
     * gone by itself while it waits: stop() it if its moment never comes.
     */
    public static BroadcastView prepare(Context c, String page, JSONObject cfg, Listener l, Ready r) throws org.json.JSONException {
        return prepare(c, page, cfg.toString(), l, r);
    }

    /** The same with its settings already written out (see page): "hold" is added to them without reading them again. */
    public static BroadcastView prepare(Context c, String page, String cfgJson, Listener l, Ready r) throws org.json.JSONException {
        String p = pageOf(page);
        return new BroadcastView(c, p, BroadcastData.held(cfgJson), maxMs(p), l, r);
    }

    /** The classic line-ups (BroadcastData.lineups): both teams, about a minute and a half. */
    public static BroadcastView lineups(Context c, JSONObject cfg, Listener l) {
        return new BroadcastView(c, "lineups", cfg.toString(), 180_000L, l, null);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private BroadcastView(Context c, String page, String cfg, long maxMs, Listener l, Ready r) {
        super(c);
        this.page = page;
        this.cfg = cfg;
        this.listener = l;
        this.maxMs = maxMs;
        this.held = r != null;
        this.onReady = r;
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        setClickable(false);
        // the studio line-ups' plucks come four at a time over a swish and a stab
        pool = new SoundPool.Builder().setMaxStreams("studio".equals(page) ? 8 : 4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        for (String s : sounds(c, page, cfg)) {
            try (AssetFileDescriptor fd = c.getAssets().openFd("broadcast/sfx/" + s + ".ogg")) {
                sounds.put(s, pool.load(fd, 1));
            } catch (Exception e) {
                Log.w(TAG, "sound " + s + ": " + e);
            }
        }
        web = new WebView(c);
        web.setBackgroundColor(Color.TRANSPARENT);
        // No layer of its own: a full-screen hardware layer is one more copy of the screen each frame (the page draws
        // its own canvas on the GPU already). "hwlayer" in the q of the settings puts it back, to compare.
        if (wantsLayer(cfg)) web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setClickable(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        // The page's own viewport (width=device-width), never zoomed: with the overview mode a TV's WebView (1.75 CSS
        // pixels to a screen pixel on the TCL) zoomed out to the 1920-wide drawing, which then sat mostly off the screen.
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "AndroidBroadcast");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                web = null;   // the page's own process went: nothing more can be drawn
                main.post(BroadcastView.this::finish);
                return true;
            }
        });
        addView(web, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        if (!held) main.postDelayed(tooLong, maxMs);
        born = SystemClock.uptimeMillis();
        web.loadUrl("file:///android_asset/broadcast/" + page + ".html");
    }

    /** Never takes a touch: the player's own taps (its tile, its buttons) go on to what is under it. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        return false;
    }

    /** The sounds' level, 0 to 1 (1: as made, about as loud as the channel). */
    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    private static boolean wantsLayer(String cfg) {
        // only settings that ask for it are read for it (a match intro's are megabytes, not read again for this)
        if (cfg == null || !cfg.contains("\"hwlayer\"")) return false;
        try {
            JSONObject q = new JSONObject(cfg).optJSONObject("q");
            return q != null && q.optBoolean("hwlayer");
        } catch (Exception e) {
            return false;
        }
    }

    /** A held one shown now (at once if it is ready, else as soon as it is); the others are already going. */
    public void go() {
        if (over || going) return;
        going = true;
        main.removeCallbacks(tooLong);
        main.postDelayed(tooLong, maxMs);
        if (web != null) web.evaluateJavascript("BROADCAST.go()", null);
    }

    /** Whether a held one is ready to go (everything loaded and made). */
    public boolean isReady() {
        return ready;
    }

    /** Whether it was made held (prepare) and go() has not been said. */
    public boolean isHeld() {
        return held && !going;
    }

    /** Whether its first frame has been drawn (it is on the screen). */
    public boolean started() {
        return started;
    }

    public boolean isOver() {
        return over;
    }

    /** Back: the line-ups and the intros fade away to the channel at once (the pop-up, being short, just goes). */
    public void back() {
        if (over) return;
        if (web != null && ("lineups".equals(page) || "stadium".equals(page) || "studio".equals(page) || "reveal".equals(page)
                || "match".equals(page)) && started) {
            web.evaluateJavascript("BROADCAST.back()", null);
        } else {
            finish();
        }
    }

    /** Taken down now, without its ending. */
    public void stop() {
        finish();
    }

    private void finish() {
        if (over) return;
        over = true;
        main.removeCallbacks(tooLong);
        if (web != null) {
            WebView w = web;
            web = null;
            removeView(w);
            try {
                w.stopLoading();
                w.removeJavascriptInterface("AndroidBroadcast");
                w.destroy();
            } catch (Exception ignored) { }
        }
        stopMusic();
        if (pool != null) {
            SoundPool p = pool;
            pool = null;
            main.postDelayed(p::release, 3000);   // a swish or rumble still playing ends on its own
        }
        if (listener != null) listener.onDone(this);
    }

    // ---- a long sound, played here (the intros' music) ---------------------------------------------------------------

    /** Loaded ahead (broadcast/NAME from the assets, or a file's path), to start with the picture (startMusic). */
    private void loadMusic(String name, float gain) {
        stopMusic();
        if (over || name == null || name.isEmpty()) return;
        android.media.MediaPlayer mp = new android.media.MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if (name.startsWith("/")) mp.setDataSource(name);
            else try (AssetFileDescriptor fd = getContext().getAssets().openFd("broadcast/" + name)) {
                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            }
            float v = Math.max(0f, Math.min(1f, gain)) * volume;
            mp.setVolume(v, v);
            mp.setOnPreparedListener(p -> {
                musicReady = true;
                if (musicWanted && !over) p.start();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, page + ": music " + name + " failed " + what + "/" + extra);
                return true;
            });
            mp.prepareAsync();
            music = mp;
        } catch (Exception e) {
            Log.w(TAG, page + ": music " + name + ": " + e);
            mp.release();
        }
    }

    private void startMusic() {
        musicWanted = true;
        if (music != null && musicReady && !music.isPlaying()) music.start();
    }

    private void stopMusic() {
        musicWanted = false;
        musicReady = false;
        if (music == null) return;
        android.media.MediaPlayer mp = music;
        music = null;
        try {
            mp.stop();
        } catch (Exception ignored) {
            // not started yet
        }
        mp.release();
    }

    /** What the page calls (on WebView's own thread: everything is sent on to the main thread). */
    private final class Bridge {
        @JavascriptInterface
        public void ready() {
            Log.i(TAG, page + ": page ready in " + (SystemClock.uptimeMillis() - born) + " ms");
            main.post(() -> {
                if (!over && web != null) web.evaluateJavascript("BROADCAST.start(" + JSONObject.quote(cfg) + ")", null);
            });
        }

        @JavascriptInterface
        public void prepared() {
            Log.i(TAG, page + ": ready to go " + (SystemClock.uptimeMillis() - born) + " ms after it was made");
            main.post(() -> {
                if (over) return;
                ready = true;
                // go() said before the page could hear it: said again now
                if (going && web != null) web.evaluateJavascript("BROADCAST.go()", null);
                Ready r = onReady;
                onReady = null;
                if (r != null) r.onReady(BroadcastView.this);
            });
        }

        @JavascriptInterface
        public void started() {
            Log.i(TAG, page + ": on the screen " + (SystemClock.uptimeMillis() - born) + " ms after it was made");
            main.post(() -> started = true);
        }

        @JavascriptInterface
        public void done() {
            main.post(BroadcastView.this::finish);
        }

        @JavascriptInterface
        public void sound(String name, float gain, float pan) {
            main.post(() -> {
                Integer id = sounds.get(name);
                if (over || pool == null || id == null) return;
                float g = Math.max(0f, gain) * volume, p = Math.max(-1f, Math.min(1f, pan));
                pool.play(id, g * Math.min(1f, 1f - p), g * Math.min(1f, 1f + p), 1, 0, 1f);
            });
        }

        @JavascriptInterface
        public void musicLoad(String name, float gain) {
            main.post(() -> loadMusic(name, gain));
        }

        @JavascriptInterface
        public void music() {
            main.post(BroadcastView.this::startMusic);
        }

        @JavascriptInterface
        public void musicStop() {
            main.post(BroadcastView.this::stopMusic);
        }

        @JavascriptInterface
        public void log(String m) {
            Log.i(TAG, page + ": " + m);
        }
    }
}
