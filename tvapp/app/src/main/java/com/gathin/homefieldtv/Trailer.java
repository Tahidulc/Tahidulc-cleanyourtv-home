package com.gathin.homefieldtv;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.prefs.TrailerPrefs;

import java.util.List;

/**
 * A title's trailer playing in the poster card the remote rests on, on the Editorial Home, Movies and TV Shows: "Can you
 * implement trailers to automatically play over a poster when I'm on it"; "put trailers of what panel I'm on after 3
 * seconds"). Once the remote has rested on a card for REST_MS (3 s, on every motion tier) and the card has grown into its
 * wide picture (or the carousel's card has slid in), its trailer plays inside
 * the card, muted unless Settings › Appearance › Home says Sound on; the card's picture cross-fades to the video once it
 * is really playing (Lite: no cross-fade) and back as the remote moves on or the trailer ends. On the carousel the window
 * goes under its words (under()), so the trailer plays behind them.
 *
 * <p>The trailers are YouTube videos (Videos.trailers: TMDB's for the title, the official trailer, then a teaser, the
 * family's language first, then the playlist's own trailer), played with YouTube's own embedded player (the IFrame
 * player: no controls, muted autoplay) in ONE WebView kept for the app and moved to whichever page needs it, its page
 * loaded from an https origin that names the app (its package), so the embed is allowed. One that won't play (embedding
 * turned off, an error, nothing within START_MS) is let go quietly and the next tried, or none: the poster stays and no
 * error is ever shown. The WebView is made only when a trailer is first to play; paused and hidden whenever nothing is
 * shown; stopped as the remote moves, as the page goes and when anything comes over it (a watch every WATCH_MS); and let
 * go of whole when Homefield TV goes out of sight, so nothing plays in the background and its memory is given back.
 */
final class Trailer {
    /** What plays a trailer: YouTube's player in the WebView (Web), or a stand-in in the tests and previews. */
    interface Player {
        /** Its view (moved into the page's window for it). */
        View view();

        /** A video by its YouTube key from its start, muted or with its sound; it answers on the listener. */
        void play(String key, boolean sound);

        /** Stopped and its work paused (its window hidden). */
        void stop();

        /** Let go of whole. */
        void release();

        void listen(Listener l);
    }

    /** What a player says, on the main thread. */
    interface Listener {
        void playing();

        void failed();

        void ended();
    }

    /** The page it plays on. */
    interface Host {
        /** Where the remote's card is on the page (screen px) once it has settled there; false while it moves. */
        boolean spot(RectF out);

        /** Whether a trailer may play now: the page on screen and in front, the app in front, the remote in the page. */
        boolean mayPlay();

        /** A trailer is now shown in the card (true), or no longer is (false). */
        void showing(boolean on);

        /** The card's trailer is over (it ended, or none would play); played: one was tried. Not when the remote moved. */
        void finished(boolean played);
    }

    /** How long the remote rests before its trailer starts (Full, Lite), how long it may take to start, the watch. */
    static final long REST_MS = 3000, START_MS = 4000, WATCH_MS = 400, FADE_IN_MS = 320, FADE_OUT_MS = 180;
    /** At most this many of a title's trailers are tried. */
    static final int TRIES = 3;
    /** Where it is with the card the remote is on. */
    static final int IDLE = 0, RESTING = 1, LOADING = 2, SHOWN = 3, DONE = 4;

    /** Tests and previews: what plays instead of YouTube's player (null: as it comes). */
    static Player standIn;
    /** The one player, kept while the app is on screen. */
    private static Player shared;
    private static boolean cannot;

    private final TvActivity tv;
    private final FrameLayout parent;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final RectF spot = new RectF(), now = new RectF();
    private FrameLayout box;
    /** The view the window goes just under (the carousel's words over its trailer), or null: on top. */
    private View above;
    private Player player;
    private Object card;
    private List<String> keys;
    private int tried, state = IDLE;
    /** Its rest was over before its trailers were known: it starts as they come. */
    private boolean due;
    /** One of the card's trailers was tried (asked of the player). */
    private boolean played;
    private final Runnable start = this::start, timeout = this::timedOut, watch = this::watch;
    private final Listener listener = new Listener() {
        @Override public void playing() {
            if (state != LOADING) return;
            h.removeCallbacks(timeout);
            state = SHOWN;
            fadeIn();
        }

        @Override public void failed() {
            if (state == LOADING || state == SHOWN) next();
        }

        @Override public void ended() {
            if (state == SHOWN) {
                h.removeCallbacks(watch);
                state = DONE;
                hide(true);
                host.finished(true);
            }
        }
    };

    Trailer(TvActivity tv, FrameLayout parent, Host host) {
        this.tv = tv;
        this.parent = parent;
        this.host = host;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    /** The remote rests on a card (what it stands for: a title's key): its trailer plays after the rest, if it has one. */
    void rest(Object on) {
        if (on == null) {
            moved();
            return;
        }
        if (on.equals(card) && state != IDLE) return;
        stop(true);
        card = on;
        keys = null;
        tried = 0;
        due = false;
        if (!TrailerPrefs.on(tv)) return;
        state = RESTING;
        played = false;
        h.postDelayed(start, REST_MS);
    }

    /** A card's trailers (YouTube keys, best first; empty: none), as they become known. */
    void keys(Object on, List<String> ks) {
        if (on == null || !on.equals(card) || ks == null) return;
        if (state == DONE && tried == 0 && (keys == null || keys.isEmpty()) && !ks.isEmpty() && TrailerPrefs.on(tv)) {
            // It had none when its rest was over; one came since (its playlist's page read): it plays now.
            state = RESTING;
            due = true;
        }
        if (state != RESTING && state != IDLE) return;
        keys = ks;
        if (due && state == RESTING) start();
    }

    /** The remote moved on (or up to the tab bar): the trailer goes, the card's picture fading back. */
    void moved() {
        stop(true);
        card = null;
        keys = null;
    }

    /** The page went (another screen, or the app out of sight): stopped at once; let go of whole when the app is. */
    void hidden() {
        stop(false);
        card = null;
        keys = null;
        if (!tv.showing(tv.top())) release();
    }

    /** The window goes just under this view (the carousel's words, drawn again over its trailer). */
    void under(View v) {
        above = v;
    }

    /** Whether a trailer is starting or playing (the carousel's own steps wait for it). */
    boolean busy() {
        return state == LOADING || state == SHOWN;
    }

    /** Tests: where it is with the card (IDLE, RESTING, LOADING, SHOWN, DONE), and the trailer playing (or tried last). */
    int state() {
        return state;
    }

    String playing() {
        return keys != null && tried < keys.size() && (state == LOADING || state == SHOWN) ? keys.get(tried) : "";
    }

    // ---- playing --------------------------------------------------------------------------------------------------

    private void start() {
        if (state != RESTING || card == null) return;
        due = true;
        if (keys == null) return;
        if (tried >= Math.min(TRIES, keys.size()) || !host.mayPlay()) {
            quiet();
            return;
        }
        if (!host.spot(spot)) {
            // The card is still growing or the page still gliding: a moment more.
            h.postDelayed(start, 200);
            return;
        }
        Player p = player();
        if (p == null) {
            quiet();
            return;
        }
        place(p);
        state = LOADING;
        played = true;
        p.play(keys.get(tried), TrailerPrefs.sound(tv));
        h.postDelayed(timeout, START_MS);
        h.removeCallbacks(watch);
        h.postDelayed(watch, WATCH_MS);
    }

    /** It didn't start in time: the next one, or none. */
    private void timedOut() {
        if (state == LOADING) next();
    }

    /** The one playing (or trying to) let go, the next tried; none left: the poster stays. */
    private void next() {
        h.removeCallbacks(timeout);
        if (player != null) player.stop();
        tried++;
        if (keys != null && tried < Math.min(TRIES, keys.size())) {
            hide(false);
            state = RESTING;
            h.post(start);
        } else {
            quiet();
        }
    }

    /** Nothing (more) to play for this card: the poster stays, quietly. */
    private void quiet() {
        h.removeCallbacks(start);
        h.removeCallbacks(timeout);
        h.removeCallbacks(watch);
        if (player != null && (state == LOADING || state == SHOWN)) player.stop();
        state = DONE;
        hide(false);
        host.finished(played);
    }

    /** While one plays: still allowed, and the card still where it was? Else it goes. */
    private void watch() {
        if (state != LOADING && state != SHOWN) return;
        if (!host.mayPlay() || !host.spot(now) || Math.abs(now.left - spot.left) > 1f || Math.abs(now.top - spot.top) > 1f) {
            stop(true);
            state = DONE;
            return;
        }
        h.postDelayed(watch, WATCH_MS);
    }

    /** Whatever is under way stopped; the window faded (or hidden at once). */
    private void stop(boolean fade) {
        h.removeCallbacks(start);
        h.removeCallbacks(timeout);
        h.removeCallbacks(watch);
        boolean was = state == LOADING || state == SHOWN;
        state = IDLE;
        due = false;
        if (was && player != null) player.stop();
        hide(fade);
    }

    // ---- the window --------------------------------------------------------------------------------------------------

    /** The player, made the first time (null where this TV has no WebView: no trailers, nothing said). */
    private Player player() {
        if (standIn != null) player = standIn;
        else if (shared == null && !cannot) {
            try {
                shared = new Web(tv);
            } catch (RuntimeException | LinkageError e) {
                // No WebView on this TV (or it is being updated): no trailers.
                cannot = true;
            }
            player = shared;
        } else {
            player = shared;
        }
        if (player != null) player.listen(listener);
        return player;
    }

    /** The window over the card: rounded as the card is, the player's view moved into it, hidden until it plays. */
    private void place(Player p) {
        if (box == null) {
            box = new FrameLayout(tv);
            box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            box.setFocusable(false);
            box.setBackgroundColor(0xFF000000);
            final float rad = Ui.f(Editorial.CARD_R);
            box.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
                }
            });
            box.setClipToOutline(true);
            box.setVisibility(View.GONE);
            int at = above == null ? -1 : parent.indexOfChild(above);
            parent.addView(box, at, new FrameLayout.LayoutParams(1, 1));
        }
        View v = p.view();
        if (v.getParent() != box) {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            box.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        lp.width = Math.round(spot.width());
        lp.height = Math.round(spot.height());
        lp.leftMargin = Math.round(spot.left);
        lp.topMargin = Math.round(spot.top);
        box.setLayoutParams(lp);
        box.animate().cancel();
        box.setAlpha(0f);
        box.setVisibility(View.VISIBLE);
    }

    private void fadeIn() {
        if (box == null) return;
        host.showing(true);
        box.animate().cancel();
        if (Motion.lite() || Exec.inline) box.setAlpha(1f);
        else box.animate().alpha(1f).setDuration(FADE_IN_MS).start();
    }

    private void hide(boolean fade) {
        if (box == null || box.getVisibility() != View.VISIBLE) return;
        host.showing(false);
        box.animate().cancel();
        if (fade && box.getAlpha() > 0.01f && !Motion.lite() && !Exec.inline) {
            box.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction(() -> {
                if (state != LOADING && state != SHOWN) box.setVisibility(View.GONE);
            }).start();
        } else {
            box.setAlpha(0f);
            box.setVisibility(View.GONE);
        }
    }

    /** The one player let go of whole (the app out of sight): made again when a trailer is next to play. */
    static void release() {
        Player p = shared;
        shared = null;
        if (p == null) return;
        View v = p.view();
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        p.release();
    }

    // ---- YouTube's embedded player --------------------------------------------------------------------------------

    /**
     * Pure: the page YouTube's IFrame player is made in: the player filling it, no controls, muted autoplay, its origin
     * the page's own; go(key, sound, n) plays a video, stop() stops it; what the player says comes back as
     * Homefield.ready(), state(s, n) and error(e, n), n the play it was for.
     */
    static String page(String origin) {
        String o = origin.replace("'", "");
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden}"
                + "#p{position:absolute;left:0;top:0;width:100%;height:100%}</style></head><body><div id=\"p\"></div><script>"
                + "var p=null,ready=false,want=null,n=-1;"
                + "function onYouTubeIframeAPIReady(){p=new YT.Player('p',{width:'100%',height:'100%',"
                + "playerVars:{autoplay:1,mute:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,playsinline:1,rel:0,enablejsapi:1,"
                + "origin:'" + o + "',widget_referrer:'" + o + "'},"
                + "events:{onReady:function(){ready=true;Homefield.ready();if(want)go(want.k,want.s,want.n);},"
                + "onStateChange:function(e){Homefield.state(e.data,n);},onError:function(e){Homefield.error(e.data,n);}}});}"
                + "function go(k,s,m){want={k:k,s:s,n:m};n=m;if(!ready)return;if(s){p.unMute();p.setVolume(100);}else{p.mute();}"
                + "p.loadVideoById({videoId:k,startSeconds:0,suggestedQuality:'hd720'});}"
                + "function stop(){want=null;n=-1;if(ready){p.stopVideo();}}"
                + "</script><script src=\"https://www.youtube.com/iframe_api\"></script></body></html>";
    }

    /** Pure: the page's https origin, named for the app (its package), as YouTube asks an app's embed to say who it is. */
    static String origin(String packageName) {
        return "https://" + packageName;
    }

    /**
     * Pure: a generic browser's user agent from the WebView's own: the TV's model and build and the WebView's marks left
     * out ("Linux; Android 10; K", as Chrome itself reduces it), its Chrome version kept.
     */
    static String agent(String own) {
        String ua = own == null ? "" : own;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Chrome/([0-9.]+)").matcher(ua);
        String chrome = m.find() ? m.group(1) : "120.0.0.0";
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome + " Safari/537.36";
    }

    /** YouTube's IFrame player in the one WebView. */
    static final class Web implements Player {
        private final WebView w;
        private final Handler main = new Handler(Looper.getMainLooper());
        private Listener l;
        private boolean loaded, ready;
        private String want;
        private boolean wantSound;
        /** Each play's number: what the player says about an earlier one is let go. */
        private int n;
        private final Runnable pause = this::pauseNow;

        @SuppressLint("SetJavaScriptEnabled")
        Web(Context c) {
            w = new WebView(c);
            w.setBackgroundColor(0xFF000000);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            s.setUserAgentString(agent(s.getUserAgentString()));
            w.addJavascriptInterface(new Bridge(), "Homefield");
            w.setWebChromeClient(new WebChromeClient() {
                @Override public android.graphics.Bitmap getDefaultVideoPoster() {
                    // No grey play mark while a video loads: a single dark pixel.
                    return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                }
            });
            w.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    // Nowhere else: a tap on the player never takes it off to YouTube's site.
                    return true;
                }
            });
        }

        @Override public View view() {
            return w;
        }

        @Override public void play(String key, boolean sound) {
            if (key == null || !key.matches("[A-Za-z0-9_-]{11}")) {
                main.post(() -> {
                    if (l != null) l.failed();
                });
                return;
            }
            main.removeCallbacks(pause);
            w.onResume();
            want = key;
            wantSound = sound;
            n++;
            if (!loaded) {
                loaded = true;
                String o = origin(w.getContext().getPackageName());
                w.loadDataWithBaseURL(o + "/", page(o), "text/html", "utf-8", null);
                return;
            }
            if (ready) go();
        }

        private void go() {
            if (want == null) return;
            w.evaluateJavascript("go('" + want + "'," + wantSound + "," + n + ")", null);
        }

        @Override public void stop() {
            want = null;
            if (ready) w.evaluateJavascript("stop()", null);
            // Its work paused once the player has had its word (unless another plays first).
            main.removeCallbacks(pause);
            main.postDelayed(pause, 600);
        }

        private void pauseNow() {
            if (want == null) w.onPause();
        }

        @Override public void release() {
            main.removeCallbacks(pause);
            l = null;
            want = null;
            try {
                w.stopLoading();
                w.loadUrl("about:blank");
                w.destroy();
            } catch (RuntimeException ignored) {
                // gone already
            }
        }

        @Override public void listen(Listener listener) {
            l = listener;
        }

        /** What the page says, from its own thread, handed to the main one; about an earlier play: let go. */
        final class Bridge {
            @JavascriptInterface public void ready() {
                main.post(() -> {
                    ready = true;
                    go();
                });
            }

            @JavascriptInterface public void state(int s, int of) {
                main.post(() -> {
                    if (of != n || want == null || l == null) return;
                    if (s == 1) l.playing();
                    else if (s == 0) l.ended();
                });
            }

            @JavascriptInterface public void error(int e, int of) {
                main.post(() -> {
                    if (of == n && want != null && l != null) l.failed();
                });
            }
        }
    }
}
