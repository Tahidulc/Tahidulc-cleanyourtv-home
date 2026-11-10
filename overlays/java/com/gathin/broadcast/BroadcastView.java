package com.gathin.broadcast;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
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
 * One of the live overlays playing once: the kick-off pop-up (kickoff.html) or the classic line-ups (lineups.html), drawn
 * by the page over a see-through view (put it over the channel's picture, the size of the picture), its sounds played
 * here as the page calls for them. It takes no keys or touches; Back for the line-ups is the app's, through back().
 * When it is over (or fails, or runs too long) the listener is told once and the view is empty: take it down then.
 */
@SuppressLint("ViewConstructor")
public final class BroadcastView extends FrameLayout {
    /** Told once, on the main thread, when the overlay is over. */
    public interface Listener {
        void onDone(BroadcastView v);
    }

    private static final String TAG = "Broadcast";
    private static final String[] SOUNDS = {"popup-kickoff", "popup-restart", "popup-second", "swish-tab", "swish-out", "swish-coin"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final String page, cfg;
    private final Listener listener;
    private WebView web;
    private SoundPool pool;
    private final Map<String, Integer> sounds = new HashMap<>();
    private float volume = 1f;
    private boolean started, over;
    private final Runnable tooLong = this::finish;

    /** The kick-off pop-up (BroadcastData.kickoff): about eight seconds. */
    public static BroadcastView kickoff(Context c, JSONObject cfg, Listener l) {
        return new BroadcastView(c, "kickoff", cfg.toString(), 25_000L, l);
    }

    /** The classic line-ups (BroadcastData.lineups): both teams, about a minute and a half. */
    public static BroadcastView lineups(Context c, JSONObject cfg, Listener l) {
        return new BroadcastView(c, "lineups", cfg.toString(), 180_000L, l);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private BroadcastView(Context c, String page, String cfg, long maxMs, Listener l) {
        super(c);
        this.page = page;
        this.cfg = cfg;
        this.listener = l;
        setBackgroundColor(Color.TRANSPARENT);
        setFocusable(false);
        setClickable(false);
        pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        for (String s : SOUNDS) {
            try (AssetFileDescriptor fd = c.getAssets().openFd("broadcast/sfx/" + s + ".ogg")) {
                sounds.put(s, pool.load(fd, 1));
            } catch (Exception e) {
                Log.w(TAG, "sound " + s + ": " + e);
            }
        }
        web = new WebView(c);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(false);
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
        main.postDelayed(tooLong, maxMs);
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

    /** Whether its first frame has been drawn (it is on the screen). */
    public boolean started() {
        return started;
    }

    public boolean isOver() {
        return over;
    }

    /** Back: the line-ups fade away to the channel at once (the pop-up, being short, just goes). */
    public void back() {
        if (over) return;
        if (web != null && "lineups".equals(page) && started) web.evaluateJavascript("BROADCAST.back()", null);
        else finish();
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
        if (pool != null) {
            SoundPool p = pool;
            pool = null;
            main.postDelayed(p::release, 3000);   // a swish or rumble still playing ends on its own
        }
        if (listener != null) listener.onDone(this);
    }

    /** What the page calls (on WebView's own thread: everything is sent on to the main thread). */
    private final class Bridge {
        @JavascriptInterface
        public void ready() {
            main.post(() -> {
                if (!over && web != null) web.evaluateJavascript("BROADCAST.start(" + JSONObject.quote(cfg) + ")", null);
            });
        }

        @JavascriptInterface
        public void started() {
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
        public void log(String m) {
            Log.i(TAG, page + ": " + m);
        }
    }
}
