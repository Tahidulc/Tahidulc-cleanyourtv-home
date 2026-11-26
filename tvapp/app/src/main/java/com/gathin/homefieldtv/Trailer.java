package com.gathin.homefieldtv;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.Videos;
import com.gathin.homefieldtv.prefs.TrailerPrefs;

import java.util.ArrayList;
import java.util.List;

/**
 * A title's trailer playing in the poster card the remote rests on, on the Editorial Home, Movies and TV Shows ("Can you
 * implement trailers to automatically play over a poster when I'm on it"; "Can you have trailers playing quicker so maybe
 * don't wait 3 seconds. Also I don't want to see when you click play on YouTube"; "Make the trailers have sound too and no
 * subtitles"). Once the remote has rested on a card for REST_MS (0.7 s, the card grown into its wide picture or the
 * carousel's card slid in, never while a key is held) its trailer starts, hidden and muted. YouTube's player shows its own
 * things over the first seconds of every play (its title bar and channel, a pause button in the middle, Share, More
 * videos, its logo, and captions when it thinks they are wanted: Chromium's frames of the embed, 2026), and again after
 * any seek, pause or stall; the button in the middle can't be cropped away. So the window stays hidden until the video
 * has played HOLD_MS (4.5 s: those were gone by 4.0 to 4.4 s in every capture) with its time moving past REVEAL_AT, and
 * only then does the card's picture cross-fade to it (Lite: at once), its sound fading in over SOUND_IN_MS where
 * Settings › Appearance › Home says Sound (on as it comes). The video fills its window as the card's picture does,
 * scaled up to at least CROP (1.2) of its height so the strips YouTube draws at its top and foot fall outside the card,
 * and its player laid out small enough that YouTube sends a picture the size of the card (480p in a row's card, 720p in
 * the carousel's: fit). Captions are turned off (cc_load_policy, and the captions module unloaded once it plays). It
 * cross-fades back to the poster, its sound fading with it, before YouTube's end screen can come (END_SCREEN_S before
 * the end; a short one END_EARLY_S), and on any pause, stall or error once shown. As the remote moves, the page goes,
 * anything comes over it or the app leaves the screen, the trailer and its sound stop at once. On the carousel the window
 * goes under its words (under()); a row's card has its logo, its episode and its progress drawn over its trailer too.
 *
 * <p>The trailers are YouTube videos (Videos.trailers: TMDB's for the title, the official trailer, then a teaser, the
 * family's language first, then the playlist's own trailer), played with YouTube's own embedded player (the IFrame
 * player: no controls) in ONE WebView kept for the app and moved to whichever page needs it, its page loaded from an
 * https origin that names the app (its package), so the embed is allowed. One that won't play (embedding turned off, an
 * error, nothing within START_MS) is let go quietly and the next tried, or none: the poster stays and no error is ever
 * shown. The WebView is made only when a trailer is first to play; paused and hidden whenever nothing is shown; stopped
 * as the remote moves, as the page goes and when anything comes over it (a watch every WATCH_MS); and let go of whole
 * when Homefield TV goes out of sight, so nothing plays in the background and its memory is given back. While its window
 * is up the page under it is told (Host.window) so it can keep itself as one still picture: a video's frames then cost
 * the TV one picture of the page, not the page drawn again.
 *
 * <p>The Trailer button on a title's page plays it here too, full screen (fullScreen: black until it is clean, sound on,
 * Back closes it), falling back to the TV's YouTube app only when none of the title's trailers will play embedded.
 */
final class Trailer {
    /** What plays a trailer: YouTube's player in the WebView (Web), or a stand-in in the tests and previews. */
    interface Player {
        /** Its view (moved into the page's window for it). */
        View view();

        /**
         * A video by its YouTube key from its start; it answers on the listener. It always starts muted (it is hidden
         * then): sound says whether its sound is to come once it is shown (sound()).
         */
        void play(String key, boolean sound);

        /** Stopped, its sound cut at once, and its work paused (its window hidden). */
        void stop();

        /** Let go of whole. */
        void release();

        void listen(Listener l);

        /** Its sound faded up to the TV's own level (on) or down to nothing (off) over ms. */
        default void sound(boolean on, long ms) {}

        /**
         * Where its video sits in its window (px, the window's own: fit's left, top, width, height and the factor its
         * player is laid out smaller by) and the window's corners' radius.
         */
        default void fit(float[] place, float radius) {}
    }

    /** What a player says, on the main thread. */
    interface Listener {
        /** YouTube's player plays (its state PLAYING). */
        void playing();

        void failed();

        void ended();

        /** Where it is and how long it is (seconds), a few times a second while it plays. */
        default void time(float at, float length) {}

        /** It paused or is buffering after it had played. */
        default void stalled() {}
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

        /**
         * The trailer's window is up over the card (hidden while it starts, then shown), or gone: the page under it is
         * still meanwhile and may keep itself as one picture (a hardware layer), so a video frame doesn't draw it again.
         */
        default void window(boolean up) {}

        /** The radius of the card's corners (screen px). */
        default float corner() {
            return Ui.f(Editorial.CARD_R);
        }
    }

    /**
     * How long the remote rests on a card before its trailer starts (Full, Lite; the card has grown in GROW_MS and the
     * carousel's slid in HERO_MS by then), how soon the card is looked at again while it still settles, how long a
     * trailer may take to start playing, how long it plays hidden before it is shown (YouTube's own things over its
     * start), the watch, the picture's fades in and out, the sound's fade in, and the fade back at its end.
     */
    static final long REST_MS = 700, SETTLE_MS = 100, START_MS = 5000, HOLD_MS = 4500, WATCH_MS = 250, FADE_IN_MS = 320,
            FADE_OUT_MS = 180, SOUND_IN_MS = 500, ENDING_MS = 450;
    /** Shown only once its time is past this (s) and moving; back to the poster this long before its end (s). */
    static final float REVEAL_AT = 0.3f, END_EARLY_S = 1f, END_SCREEN_S = 20f;
    /**
     * The video scaled up in its window to at least this much of the window's height (and covering it), centred: the top
     * and foot 8 % of YouTube's player (its title strip, its Share, More videos and logo row, a caption) outside the card.
     */
    static final float CROP = 1.2f;
    /**
     * How tall YouTube's player is laid out (px) in a row's card, the carousel's and full screen: YouTube sends one size
     * up from it (360: 480p, 540: 720p, 720: 1080p), the picture then scaled to the window (fit).
     */
    static final float LAYOUT_CARD = 360f, LAYOUT_HERO = 540f, LAYOUT_FULL = 720f;
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
    /** Full screen (the Trailer button): no rest, always its sound, a bigger picture, and on after a stall. */
    private boolean full;
    /** When it last began to play while hidden (uptime ms; -1: not playing), and its last two times and its length (s). */
    private long playingSince = -1, timeAt;
    private float before = -1f, at = -1f, length;
    /** Fading back to the poster at its end: the player still to be stopped. */
    private boolean fading;
    private final Runnable start = this::start, timeout = this::timedOut, watch = this::watch, reveal = this::revealIfClean,
            over = this::fadedBack;
    private final Listener listener = new Listener() {
        @Override public void playing() {
            if (state != LOADING) return;
            // One that plays but never comes clean (its time stuck) is let go in the end too.
            h.removeCallbacks(timeout);
            h.postDelayed(timeout, HOLD_MS + START_MS);
            if (playingSince < 0) {
                // YouTube's things over its start: hidden until they have gone.
                playingSince = SystemClock.uptimeMillis();
                before = -1f;
                h.removeCallbacks(reveal);
                h.postDelayed(reveal, HOLD_MS);
            }
        }

        @Override public void time(float a, float len) {
            if (state != LOADING && state != SHOWN) return;
            before = at;
            at = a;
            length = len;
            timeAt = SystemClock.uptimeMillis();
            if (state == LOADING) revealIfClean();
            else if (len > 0f && a >= outAt(len)) back();
        }

        @Override public void stalled() {
            if (state == LOADING) {
                // Its things come back with it: the hold starts again when it plays again.
                playingSince = -1;
                h.removeCallbacks(reveal);
                h.removeCallbacks(timeout);
                h.postDelayed(timeout, START_MS);
            } else if (state == SHOWN) {
                if (full) hideAgain();
                else back();
            }
        }

        @Override public void failed() {
            if (state == LOADING) next();
            else if (state == SHOWN) back();
        }

        @Override public void ended() {
            if (state == SHOWN) back();
            else if (state == LOADING) next();
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

    /** Full screen: these trailers at once (no rest), with their sound. */
    void now(List<String> ks) {
        stop(false);
        full = true;
        card = ks;
        keys = ks;
        tried = 0;
        played = false;
        state = RESTING;
        due = true;
        h.post(start);
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

    /** Tests: whether its window is up over the card (hidden or shown), and how opaque it is. */
    boolean windowUp() {
        return box != null && box.getVisibility() == View.VISIBLE;
    }

    float windowAlpha() {
        return box == null ? 0f : box.getAlpha();
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
            // The card is still growing, the page still gliding or a key held: a moment more.
            h.postDelayed(start, SETTLE_MS);
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
        playingSince = -1;
        before = at = -1f;
        length = 0f;
        p.play(keys.get(tried), sound());
        h.postDelayed(timeout, START_MS);
        h.removeCallbacks(watch);
        h.postDelayed(watch, WATCH_MS);
    }

    /** Whether its sound is to come once it is shown: Settings' Sound; always full screen. */
    private boolean sound() {
        return full || TrailerPrefs.sound(tv);
    }

    /** It didn't start in time: the next one, or none. */
    private void timedOut() {
        if (state == LOADING) next();
    }

    /** Hidden while it plays: shown once it has played HOLD_MS with its time moving past REVEAL_AT. */
    private void revealIfClean() {
        if (state != LOADING || playingSince < 0) return;
        long t = SystemClock.uptimeMillis();
        if (t - timeAt > 4 * WATCH_MS || !clean(t - playingSince, before, at)) return;
        h.removeCallbacks(reveal);
        h.removeCallbacks(timeout);
        state = SHOWN;
        fadeIn();
        if (sound()) player.sound(true, SOUND_IN_MS);
    }

    /**
     * Pure: whether a trailer playing hidden may be shown: it has played long enough for YouTube's things over its start
     * to have gone (HOLD_MS) and its time is past REVEAL_AT and moving (frames are coming, not a still).
     */
    static boolean clean(long playedMs, float before, float at) {
        return playedMs >= HOLD_MS && at >= REVEAL_AT && before >= 0f && at > before;
    }

    /**
     * Pure: when a trailer of this length (s) goes back to the poster: END_SCREEN_S before its end (YouTube's end screen
     * may cover its last 5 to 20 s), one of under twice that END_EARLY_S before (it would hardly be seen otherwise).
     */
    static float outAt(float length) {
        return length > 2 * END_SCREEN_S ? length - END_SCREEN_S : length - END_EARLY_S;
    }

    /**
     * Pure: where the video goes in a window w x h (px), as {left, top, width, height, k}: 16:9, covering the window and
     * at least crop times its height, centred (as the card crops its picture: YouTube's strips at the top and foot of its
     * player outside the window); and k, how much smaller its player is laid out (its width and height over k, scaled up
     * by k again) so that YouTube picks a picture for a player no taller than layout.
     */
    static float[] fit(float w, float h, float crop, float layout) {
        float ph = Math.max(Math.max(h * crop, w * 9f / 16f), 1f), pw = ph * 16f / 9f;
        float k = Math.max(1f, ph / Math.max(1f, layout));
        return new float[]{(w - pw) / 2f, (h - ph) / 2f, pw, ph, k};
    }

    /** One that is shown goes back to the poster, its sound fading with it; its trailer is over. */
    private void back() {
        h.removeCallbacks(watch);
        h.removeCallbacks(reveal);
        state = DONE;
        fading = true;
        if (player != null) player.sound(false, ENDING_MS);
        hide(true, ENDING_MS);
        h.removeCallbacks(over);
        h.postDelayed(over, ENDING_MS);
        host.finished(true);
    }

    /** Faded back: the player stopped. */
    private void fadedBack() {
        if (!fading) return;
        fading = false;
        if (player != null) player.stop();
    }

    /** Full screen, a stall once shown: black again (its sound gone) until it plays clean again. */
    private void hideAgain() {
        state = LOADING;
        playingSince = -1;
        if (player != null) player.sound(false, FADE_OUT_MS);
        if (box != null) {
            box.animate().cancel();
            if (Motion.lite() || Exec.inline) box.setAlpha(0f);
            else box.animate().alpha(0f).setDuration(FADE_OUT_MS).start();
        }
        host.showing(false);
        h.removeCallbacks(timeout);
        h.postDelayed(timeout, START_MS);
    }

    /** The one playing (or trying to) let go, the next tried; none left: the poster stays. */
    private void next() {
        h.removeCallbacks(timeout);
        h.removeCallbacks(reveal);
        if (player != null) player.stop();
        tried++;
        if (keys != null && tried < Math.min(TRIES, keys.size())) {
            hide(false, 0);
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
        h.removeCallbacks(reveal);
        if (player != null && (state == LOADING || state == SHOWN)) player.stop();
        state = DONE;
        hide(false, 0);
        host.finished(played);
    }

    /** While one plays: still allowed, and the card still where it was? Else it goes (its sound at once). */
    private void watch() {
        if (state != LOADING && state != SHOWN) return;
        if (!host.mayPlay() || !host.spot(now) || Math.abs(now.left - spot.left) > 1f || Math.abs(now.top - spot.top) > 1f) {
            stop(true);
            state = DONE;
            return;
        }
        h.postDelayed(watch, WATCH_MS);
    }

    /** Whatever is under way stopped, its sound cut at once; the window faded (or hidden at once). */
    private void stop(boolean fade) {
        h.removeCallbacks(start);
        h.removeCallbacks(timeout);
        h.removeCallbacks(watch);
        h.removeCallbacks(reveal);
        h.removeCallbacks(over);
        boolean was = state == LOADING || state == SHOWN || fading;
        state = IDLE;
        due = false;
        fading = false;
        playingSince = -1;
        if (was && player != null) player.stop();
        hide(fade, FADE_OUT_MS);
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

    /**
     * The window over the card, the player's view moved into it, hidden until it plays clean. Its video is placed, cropped
     * and rounded inside the player's own page (fit), so the window itself clips nothing and draws no ground.
     */
    private void place(Player p) {
        if (box == null) {
            box = new FrameLayout(tv);
            box.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            box.setFocusable(false);
            box.setWillNotDraw(true);
            box.setVisibility(View.INVISIBLE);
            int at = above == null ? -1 : parent.indexOfChild(above);
            parent.addView(box, at, new FrameLayout.LayoutParams(1, 1));
        }
        View v = p.view();
        if (v.getParent() != box) {
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            box.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        int w = Math.round(spot.width()), ht = Math.round(spot.height());
        if (lp.width != w || lp.height != ht || lp.leftMargin != Math.round(spot.left) || lp.topMargin != Math.round(spot.top)) {
            lp.width = w;
            lp.height = ht;
            lp.leftMargin = Math.round(spot.left);
            lp.topMargin = Math.round(spot.top);
            box.setLayoutParams(lp);
        }
        float layout = full ? LAYOUT_FULL : ht > Ui.f(Editorial.CARD_H * 1.1f) ? LAYOUT_HERO : LAYOUT_CARD;
        p.fit(fit(w, ht, CROP, Ui.f(layout)), full ? 0f : host.corner());
        box.animate().cancel();
        box.setAlpha(0f);
        box.setVisibility(View.VISIBLE);
        host.window(true);
    }

    private void fadeIn() {
        if (box == null) return;
        host.showing(true);
        box.animate().cancel();
        if (Motion.lite() || Exec.inline) box.setAlpha(1f);
        else box.animate().alpha(1f).setDuration(FADE_IN_MS).start();
    }

    private void hide(boolean fade, long ms) {
        if (box == null || box.getVisibility() != View.VISIBLE) return;
        host.showing(false);
        host.window(false);
        box.animate().cancel();
        if (fade && box.getAlpha() > 0.01f && !Motion.lite() && !Exec.inline) {
            box.animate().alpha(0f).setDuration(ms).withEndAction(() -> {
                if (state != LOADING && state != SHOWN) box.setVisibility(View.INVISIBLE);
            }).start();
        } else {
            box.setAlpha(0f);
            box.setVisibility(View.INVISIBLE);
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

    // ---- the Trailer button: full screen ---------------------------------------------------------------------------

    /** The Trailer button on a title's page: its trailers full screen, here in the app (fullScreen(tv, keys)). */
    static void fullScreen(TvActivity tv, Catalog.Title title) {
        fullScreen(tv, title, "");
    }

    /**
     * As fullScreen(tv, title), with first (a YouTube key or address) tried first: one of the title's trailers first, or a
     * video of its own (a clip from a series' Videos row: then only it).
     */
    static void fullScreen(TvActivity tv, Catalog.Title title, String first) {
        Tmdb.Extras x = title == null ? null : Tmdb.keptExtras(tv, title);
        Catalog.Details d = title == null ? null : Catalog.get(tv).cachedDetails(title);
        fullScreen(tv, keysFor(x == null ? null : x.videos, Tmdb.language(tv), d == null ? "" : d.trailer, first));
    }

    /** Pure: the trailers a title's full-screen player tries: first (when given) first; a video not among them: only it. */
    static List<String> keysFor(List<Videos.Video> videos, String lang, String playlist, String first) {
        List<String> ks = new ArrayList<>(Videos.trailers(videos, lang, playlist));
        String f = Videos.key(first);
        if (f.isEmpty()) return ks;
        if (!ks.contains(f)) {
            List<String> one = new ArrayList<>();
            one.add(f);
            return one;
        }
        ks.remove(f);
        ks.add(0, f);
        return ks;
    }

    /** These trailers (YouTube keys, best first) full screen; none: nothing to play. */
    static void fullScreen(TvActivity tv, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            Toast.makeText(tv, "There's no trailer for this one", Toast.LENGTH_SHORT).show();
            return;
        }
        tv.push(new Full(tv, keys));
    }

    /** A video (its YouTube key) in the TV's YouTube app: only when none would play here. */
    static void youTube(TvActivity tv, String key) {
        try {
            tv.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + key))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(tv, "There's no app on this TV to play the trailer", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The full-screen trailer: black until it plays clean (the same hold, crop and captions off as in a card), with its
     * sound; Back closes it, back to the page; at its end it closes itself. None of its trailers would play embedded:
     * the first in the TV's YouTube app instead. The remote's other keys do nothing (a seek would bring YouTube's own
     * things back over it).
     */
    static final class Full extends Screen implements Host {
        private final List<String> keys;
        private FrameLayout f;
        private Trailer t;
        private boolean shown, done;

        Full(TvActivity tv, List<String> keys) {
            super(tv);
            this.keys = new ArrayList<>(keys);
        }

        @Override View build() {
            f = new FrameLayout(tv);
            f.setBackgroundColor(0xFF000000);
            t = new Trailer(tv, f, this);
            return f;
        }

        @Override boolean opaque() {
            return true;
        }

        @Override void shown() {
            if (!done) t.now(keys);
        }

        @Override void hidden() {
            if (t != null) t.hidden();
        }

        @Override boolean key(int code, KeyEvent e) {
            return true;
        }

        @Override void ok() {}

        @Override public boolean spot(RectF out) {
            if (f == null || f.getWidth() <= 0) return false;
            out.set(0, 0, f.getWidth(), f.getHeight());
            return true;
        }

        @Override public boolean mayPlay() {
            return tv.showing(this);
        }

        @Override public void showing(boolean on) {
            shown |= on;
        }

        @Override public void finished(boolean played) {
            if (done) return;
            done = true;
            // None would play here (embedding refused, or none started): the TV's YouTube app after all.
            boolean app = !shown;
            Runnable close = () -> {
                if (tv.top() == this) tv.pop();
                if (app) youTube(tv, keys.get(0));
            };
            if (shown && !Exec.inline) new Handler(Looper.getMainLooper()).postDelayed(close, ENDING_MS);
            else close.run();
        }

        /** Tests: its trailer. */
        Trailer trailer() {
            return t;
        }
    }

    // ---- YouTube's embedded player --------------------------------------------------------------------------------

    /**
     * Pure: the page YouTube's IFrame player is made in: no controls, muted autoplay, no captions, its origin the page's
     * own. size(l, t, w, h, k, r) places the player in the page (CSS px: fit's, over the TV's density) inside a black
     * window rounded r; go(key, n) plays a video muted; sound(on, ms) fades its sound up to the TV's own level or down
     * to nothing; hush() cuts it at once; stop() stops it. Once it plays, the captions module is unloaded and its track
     * set to none (again whenever a module loads). What the player says comes back as Homefield.ready(), state(s, n),
     * error(e, n) and time(at, length, n) (every 250 ms while it plays), n the play it was for.
     */
    static String page(String origin) {
        String o = origin.replace("'", "");
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden}"
                + "#w{position:absolute;left:0;top:0;right:0;bottom:0;overflow:hidden;background:#000}"
                + "#p{position:absolute;left:0;top:0;width:100%;height:100%;transform-origin:0 0}</style></head>"
                + "<body><div id=\"w\"><div id=\"p\"></div></div><script>"
                + "var p=null,ready=false,want=null,n=-1,tick=null,fade=null,vol=0,fit=null;"
                + "function onYouTubeIframeAPIReady(){p=new YT.Player('p',{width:'100%',height:'100%',"
                + "playerVars:{autoplay:1,mute:1,controls:0,disablekb:1,fs:0,iv_load_policy:3,cc_load_policy:0,playsinline:1,rel:0,"
                + "enablejsapi:1,origin:'" + o + "',widget_referrer:'" + o + "'},"
                + "events:{onReady:function(){ready=true;place();Homefield.ready();if(want)go(want.k,want.n);},"
                + "onStateChange:function(e){if(e.data==1)quiet();times(e.data==1);Homefield.state(e.data,n);},"
                + "onApiChange:function(){quiet();},onError:function(e){Homefield.error(e.data,n);}}});}"
                + "function quiet(){try{p.unloadModule('captions');p.unloadModule('cc');p.setOption('captions','track',{});}catch(x){}}"
                + "function times(on){clearInterval(tick);tick=null;if(on)tick=setInterval(function(){"
                + "Homefield.time(p.getCurrentTime(),p.getDuration(),n);},250);}"
                + "function size(l,t,w,h,k,r){fit={l:l,t:t,w:w,h:h,k:k,r:r};place();}"
                + "function place(){var f=document.getElementById('p'),q=document.getElementById('w');if(!f||!fit)return;"
                + "q.style.borderRadius=fit.r+'px';f.style.left=fit.l+'px';f.style.top=fit.t+'px';f.style.width=(fit.w/fit.k)+'px';"
                + "f.style.height=(fit.h/fit.k)+'px';f.style.transform='scale('+fit.k+')';}"
                + "function go(k,m){want={k:k,n:m};n=m;if(!ready)return;hush();p.loadVideoById({videoId:k,startSeconds:0});}"
                + "function sound(on,ms){if(!ready)return;clearInterval(fade);fade=null;var to=on?100:0;"
                + "if(on&&p.isMuted()){p.setVolume(Math.round(vol));p.unMute();}"
                + "var steps=Math.max(1,Math.round(ms/50)),d=(to-vol)/steps,i=0;fade=setInterval(function(){i++;"
                + "vol=i>=steps?to:vol+d;p.setVolume(Math.round(vol));if(i>=steps){clearInterval(fade);fade=null;if(!on)p.mute();}},50);}"
                + "function hush(){clearInterval(fade);fade=null;vol=0;if(ready){p.mute();p.setVolume(0);}}"
                + "function stop(){want=null;n=-1;times(false);hush();if(ready){p.stopVideo();}}"
                + "</script><script src=\"https://www.youtube.com/iframe_api\"></script></body></html>";
    }

    /** Pure: the page's https origin, named for the app (its package), as YouTube asks an app's embed to say who it is. */
    static String origin(String packageName) {
        return "https://" + packageName;
    }

    /** Pure: the size() call that places the video (fit's px, the window's radius) in the page's CSS px. */
    static String sizeCall(float[] place, float radius, float density) {
        float d = density <= 0f ? 1f : density;
        return String.format(java.util.Locale.ROOT, "size(%.2f,%.2f,%.2f,%.2f,%.4f,%.2f)", place[0] / d, place[1] / d, place[2] / d,
                place[3] / d, place[4], radius / d);
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
        private boolean loaded, ready, played;
        private String want, placed = "";
        /** Each play's number: what the player says about an earlier one is let go. */
        private int n;
        private final Runnable pause = this::pauseNow;

        @SuppressLint("SetJavaScriptEnabled")
        Web(Context c) {
            w = new WebView(c);
            // See-through: the page rounds its own window, the card's picture showing at its corners.
            w.setBackgroundColor(0);
            w.setFocusable(false);
            w.setFocusableInTouchMode(false);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            // Its sound never waits for a press: no play button ever shows.
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            s.setUserAgentString(agent(s.getUserAgentString()));
            w.addJavascriptInterface(new Bridge(), "Homefield");
            w.setWebChromeClient(new WebChromeClient() {
                @Override public android.graphics.Bitmap getDefaultVideoPoster() {
                    // No grey play mark while a video loads: a single see-through pixel.
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

        /** Tests: its WebView. */
        WebView web() {
            return w;
        }

        @Override public View view() {
            return w;
        }

        @Override public void fit(float[] place, float radius) {
            placed = sizeCall(place, radius, w.getResources().getDisplayMetrics().density);
            if (ready) w.evaluateJavascript(placed, null);
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
            played = false;
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
            if (!placed.isEmpty()) w.evaluateJavascript(placed, null);
            w.evaluateJavascript("go('" + want + "'," + n + ")", null);
        }

        @Override public void sound(boolean on, long ms) {
            if (ready && want != null) w.evaluateJavascript("sound(" + on + "," + ms + ")", null);
        }

        @Override public void stop() {
            want = null;
            // Its sound cut and the video stopped at once.
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
                    if (s == 1) {
                        played = true;
                        l.playing();
                    } else if (s == 0) {
                        l.ended();
                    } else if ((s == 2 || s == 3) && played) {
                        l.stalled();
                    }
                });
            }

            @JavascriptInterface public void time(double at, double length, int of) {
                main.post(() -> {
                    if (of == n && want != null && l != null) l.time((float) at, (float) length);
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
