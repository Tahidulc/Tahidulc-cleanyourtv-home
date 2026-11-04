package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two cards over the apps, in the bottom left corner as every pop-up over them (black, white writing, as the release
 * cards), from the media sessions WatchHistory reads:
 * <ul>
 * <li>How was it? (theme "how_was_it", on unless set false): a film reaches its credits in any app (Cinema mode's rule:
 * 70 minutes or more, the last 6% and at least four minutes; not live TV or YouTube), having been watched here, or the
 * last episode of a season ends. Its poster, "YOU JUST FINISHED", its name, length and when it ended, and three
 * choices: Not for me, Liked it (where the remote starts) and Loved it. Left and Right choose, OK saves (Ratings), Back
 * skips; gone after 20 seconds. Asked once per film, once per series' season.</li>
 * <li>The pause card (theme "pause_card", on unless set false): a film or episode stays paused ten seconds: PAUSED, its
 * name, how far in and what is left, when it ends if carried on now, how that sits with bedtime (the theme's
 * "bedtime"), and that Cinema mode brought the lights up. Play or Back hides it; once per pause. Not for music, live TV
 * or YouTube clips under 20 minutes, nor sport.</li>
 * </ul>
 * Both are kinds in the Control Centre's Notifications ("howwasit", "pausecard"). With Home in front they show there
 * the same way. They wait for another pop-up in that corner; a goal's card closes the pause card and waits for How was
 * it?. Keys: through the key helper (InputKeyService) where it runs, else the card's own window takes the remote.
 */
final class WatchCards {
    private WatchCards() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final PathInterpolator SLIDE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final long ASK_MS = 20_000L, PAUSE_SHOW_MS = 120_000L;
    private static final int WHITE = 0xFFFFFFFF, SOFT = 0xFFC9CBE0, FAINT = 0xFFA6A9C2, PINK = 0xFFFF4FA3, AMBER = 0xFFFFB238;
    private static float k = 1f;

    static boolean askOn(Context c) {
        return Config.themeFlag(c, "how_was_it", true);
    }

    static boolean pauseOn(Context c) {
        return Config.themeFlag(c, "pause_card", true);
    }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    // ---- the media sessions -----------------------------------------------------------------------------------------

    /** One session followed: what it plays, the earliest position seen playing it, and its pause. */
    private static final class Track {
        MediaController mc;
        MediaController.Callback cb;
        String item;
        long firstSeen = -1;
        boolean asked;
        long pausedAt;
        boolean pauseShown;
    }

    private static final Map<MediaSession.Token, Track> tracks = new HashMap<>();
    private static Context app;
    private static final Runnable CHECK = WatchCards::checkAll;

    /** The media sessions now (WatchHistory, as they change): each followed for playing, pausing and its credits. */
    static void sessions(Context c, List<MediaController> list) {
        app = c.getApplicationContext();
        List<MediaSession.Token> gone = new ArrayList<>(tracks.keySet());
        for (MediaController mc : list) {
            MediaSession.Token tok = mc.getSessionToken();
            if (tracks.containsKey(tok)) { gone.remove(tok); continue; }
            Track t = new Track();
            t.mc = mc;
            t.cb = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState state) { check(t); schedule(); }
                @Override public void onMetadataChanged(MediaMetadata metadata) { check(t); schedule(); }
                @Override public void onSessionDestroyed() { if (pauseFor == t) closeHost(false); }
            };
            try {
                mc.registerCallback(t.cb, MAIN);
                tracks.put(tok, t);
            } catch (RuntimeException ignored) {
                // a session that went as it came
            }
        }
        for (MediaSession.Token g : gone) {
            Track t = tracks.remove(g);
            if (t == null) continue;
            if (pauseFor == t) closeHost(false);
            try { t.mc.unregisterCallback(t.cb); } catch (RuntimeException ignored) { /* gone already */ }
        }
        checkAll();
    }

    /** Every half minute too (WatchHistory's tick). */
    static void tick(Context c, List<MediaController> list) {
        app = c.getApplicationContext();
        checkAll();
    }

    private static void checkAll() {
        for (Track t : new ArrayList<>(tracks.values())) check(t);
        schedule();
    }

    private static long position(PlaybackState st) {
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING && st.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        return pos;
    }

    private static String title(MediaMetadata md) {
        String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null || t.trim().isEmpty()) t = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t == null ? "" : t.trim();
    }

    private static String sub(MediaMetadata md) {
        String s = md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        if (s == null) s = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return s == null ? "" : s.trim();
    }

    /** One session looked at: the earliest position seen, the credits or the end reached, a pause long enough. */
    private static void check(Track t) {
        Context c = app;
        if (c == null) return;
        PlaybackState st;
        MediaMetadata md;
        try {
            st = t.mc.getPlaybackState();
            md = t.mc.getMetadata();
        } catch (RuntimeException e) {
            return;
        }
        String pkg = t.mc.getPackageName();
        if (st == null || md == null) {
            if (pauseFor == t) closeHost(false);
            return;
        }
        long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        String title = title(md), sub = sub(md);
        String item = pkg + "|" + title + "|" + sub + "|" + dur;
        if (!item.equals(t.item)) {
            t.item = item;
            t.firstSeen = -1;
            t.asked = false;
            t.pausedAt = 0;
            t.pauseShown = false;
            if (pauseFor == t) closeHost(false);
        }
        if (title.isEmpty() || !WatchRules.watchable(pkg, dur, ViewingLog.live(pkg))) return;
        long pos = position(st);
        int state = st.getState();
        if (state == PlaybackState.STATE_PLAYING) {
            t.firstSeen = t.firstSeen < 0 ? pos : Math.min(t.firstSeen, pos);
            t.pausedAt = 0;
            t.pauseShown = false;
            // Play hides the pause card.
            if (pauseFor == t) closeHost(false);
        } else if (state == PlaybackState.STATE_PAUSED) {
            if (t.pausedAt == 0) t.pausedAt = SystemClock.uptimeMillis();
        } else if (state != PlaybackState.STATE_BUFFERING) {
            t.pausedAt = 0;
            if (pauseFor == t) closeHost(false);
        }
        // How was it?: the credits (a film) or the end (an episode) reached, after watching it here.
        if (!t.asked && t.firstSeen >= 0 && askOn(c)) {
            if (WatchRules.filmFinished(dur, pos, t.firstSeen) || WatchRules.episodeFinished(dur, pos, t.firstSeen)) {
                t.asked = true;
                boolean film = dur >= WatchRules.FILM_MS;
                long ended = System.currentTimeMillis();
                IO.execute(() -> ask(c, pkg, title, sub, dur, film, ended));
            }
        }
        // The pause card: ten seconds paused, after playing here (a session left paused long ago gets none).
        if (state == PlaybackState.STATE_PAUSED && t.firstSeen >= 0 && !t.pauseShown && WatchRules.pauseDue(t.pausedAt, SystemClock.uptimeMillis())
                && pauseOn(c) && !WatchHistory.sport(pkg, title + " " + sub)) {
            t.pauseShown = true;
            long pausedWall = System.currentTimeMillis() - (SystemClock.uptimeMillis() - t.pausedAt);
            IO.execute(() -> pause(c, t, pkg, title, sub, pos, dur, pausedWall));
        }
    }

    /** Looked at again when the next thing is due: a pause's ten seconds, a film's credits, an episode's end. */
    private static void schedule() {
        long next = 30 * 60_000L;
        long now = SystemClock.uptimeMillis();
        for (Track t : tracks.values()) {
            PlaybackState st;
            MediaMetadata md;
            try {
                st = t.mc.getPlaybackState();
                md = t.mc.getMetadata();
            } catch (RuntimeException e) {
                continue;
            }
            if (st == null || md == null) continue;
            if (st.getState() == PlaybackState.STATE_PAUSED && t.pausedAt > 0 && !t.pauseShown) {
                next = Math.min(next, Math.max(200L, t.pausedAt + WatchRules.PAUSE_MS - now + 200L));
            }
            if (st.getState() == PlaybackState.STATE_PLAYING && !t.asked) {
                long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long at = dur >= WatchRules.FILM_MS ? WatchRules.creditsAt(dur) : WatchRules.episodeEndAt(dur);
                long left = at - position(st);
                float speed = st.getPlaybackSpeed() > 0 ? st.getPlaybackSpeed() : 1f;
                if (left > 0) next = Math.min(next, (long) (left / speed) + 1_000L);
            }
        }
        MAIN.removeCallbacks(CHECK);
        MAIN.postDelayed(CHECK, next);
    }

    // ---- How was it?: what it asks about ----------------------------------------------------------------------------

    /** What How was it? shows: the title, its poster, the line under it, and what a rating is kept against. */
    private static final class Ask {
        int id;
        boolean tv;
        String name, line, genre, asked;
        Bitmap poster;
        boolean test;
    }

    /** Off the main thread: the title matched on TMDB (WatchHistory's entry for it), then the card if not asked yet. */
    private static void ask(Context c, String pkg, String title, String sub, long dur, boolean film, long ended) {
        String key = Config.themeString(c, "tmdb_key", "").trim();
        if (key.isEmpty()) return;
        WatchHistory.Entry e = entry(c, pkg, title, sub);
        if (e != null && e.tmdbId == 0) {
            WatchHistory.match(c, key);
            e = entry(c, pkg, title, sub);
        }
        if (e == null || e.tmdbId <= 0) {
            AlertLog.note(c, "how was it: " + title + " not matched on TMDB, not asked");
            return;
        }
        String when = WatchRules.clock(LocalTime.now().toSecondOfDay() / 60);
        Ask a = new Ask();
        a.id = e.tmdbId;
        a.tv = e.tv;
        a.name = e.show != null ? e.show : e.title;
        if (!e.tv) {
            if (!film) return;
            a.asked = Ratings.key(false, e.tmdbId);
            if (Ratings.asked(c, a.asked) || Ratings.get(c, false, e.tmdbId) != 0) return;
            a.line = WatchRules.length(dur) + " · ended " + when;
            a.genre = movieGenre(key, e.tmdbId);
            a.poster = picture(c, e.poster);
        } else {
            // A series: only its season's last episode asks, about the series.
            int[] at = YourShows.position(e);
            JSONObject d = YourShows.details(key, e.tmdbId);
            if (at == null || d == null || at[1] < episodes(d, at[0])) return;
            a.asked = Ratings.key(true, e.tmdbId) + ":s" + at[0];
            if (Ratings.asked(c, a.asked)) return;
            a.name = d.optString("name", a.name);
            a.line = "Season " + at[0] + "'s last episode · ended " + when;
            a.genre = firstGenre(d);
            String p = d.optString("poster_path", "");
            a.poster = picture(c, p.isEmpty() || "null".equals(p) ? e.poster : "https://image.tmdb.org/t/p/w342" + p);
        }
        Ratings.markAsked(c, a.asked);
        MAIN.post(() -> showAsk(c, a, SystemClock.uptimeMillis()));
    }

    /** The episodes in a season of a series (TMDB's details); a very large number when it does not say. */
    private static int episodes(JSONObject d, int season) {
        JSONArray s = d.optJSONArray("seasons");
        for (int i = 0; s != null && i < s.length(); i++) {
            JSONObject o = s.optJSONObject(i);
            if (o != null && o.optInt("season_number", -1) == season && o.optInt("episode_count") > 0) return o.optInt("episode_count");
        }
        return Integer.MAX_VALUE;
    }

    private static String firstGenre(JSONObject d) {
        JSONArray g = d == null ? null : d.optJSONArray("genres");
        JSONObject first = g == null ? null : g.optJSONObject(0);
        return first == null ? "" : first.optString("name", "");
    }

    private static String movieGenre(String key, int id) {
        try {
            return firstGenre(new JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + id + "?language=en-GB&api_key=" + key)));
        } catch (Exception e) {
            return "";
        }
    }

    private static WatchHistory.Entry entry(Context c, String pkg, String title, String sub) {
        for (WatchHistory.Entry e : WatchHistory.load(c)) if (e.pkg.equals(pkg) && e.title.equals(title) && e.sub.equals(sub)) return e;
        return null;
    }

    private static Bitmap picture(Context c, String url) {
        if (url == null || url.isEmpty() || !url.startsWith("http")) return null;
        return ScoresTicker.cachedLogo(c, url, 300);
    }

    // ---- the window in the corner -----------------------------------------------------------------------------------

    private static FrameLayout host;
    private static boolean asking;
    private static Track pauseFor;
    private static long shownAt;
    private static final int[] passing = {0};
    private static Runnable closeLater;

    static boolean isShowing() {
        return host != null;
    }

    /**
     * A score card about to come up in this corner (ScoreCard.next): the pause card makes way at once; true while How
     * was it? is up, so the card waits for it (20 seconds at most).
     */
    static boolean holds() {
        if (host != null && !asking) closeHost(false);
        return host != null && asking;
    }

    /** Another pop-up up in this corner, or one of the others that show over the apps. */
    private static boolean othersUp() {
        return ScoreCard.busy() || RichAlerts.isShowing() || LineupCard.isShowing() || GoalWatchService.alertShowing() || CameraAlert.isShowing();
    }

    private static boolean allowed(Context c, String kind, String what) {
        boolean home = NotifyPrefs.homeNow();
        if (NotifyPrefs.allowed(c, kind, home)) return true;
        NotifyPrefs.held(c, kind, home, null, what);
        return false;
    }

    private static TextView text(Context c, CharSequence s, float size, int colour, int weight) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k);
        v.setTextColor(colour);
        v.setTypeface(ScoreCard.outfit(c, weight));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static void rounded(View v, float r) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** The card's black ground, its fine rim and round corners; its poster on the left. */
    private static LinearLayout cardBox(Context c, Bitmap poster) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(px(20), px(20), px(20), px(20));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF006060A);
        g.setCornerRadius(px(16));
        g.setStroke(Math.max(1, px(1)), 0x24FFFFFF);
        card.setBackground(g);
        if (poster != null) {
            ImageView p = new ImageView(c);
            poster.setHasMipMap(true);
            p.setImageBitmap(poster);
            p.setScaleType(ImageView.ScaleType.CENTER_CROP);
            rounded(p, px(10));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(128), px(192));
            pl.rightMargin = px(22);
            card.addView(p, pl);
        }
        return card;
    }

    /** The card shown in the corner (taking the remote where no key helper runs), gone after ms. */
    private static boolean showHost(Context c, View card, int w, long ms, boolean ask) {
        closeHost(false);
        int m = px(10);
        FrameLayout win = new FrameLayout(c);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, FrameLayout.LayoutParams.WRAP_CONTENT);
        cl.setMargins(m, m, m, m);
        win.addView(card, cl);
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), 2 * m));
        back.setTag(PopupMotion.BACK_KEY);
        win.addView(back, new FrameLayout.LayoutParams(2 * m, 2 * m, Gravity.TOP | Gravity.END));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w + 2 * m, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = px(30);
        lp.y = px(AmbientScene.showing() ? AmbientScene.ALERT_FOOT : 30);
        lp.setTitle(ask ? "WatchCardsAsk" : "WatchCardsPause");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "a watch card could not be shown: " + e);
            return false;
        }
        host = win;
        asking = ask;
        shownAt = SystemClock.uptimeMillis();
        card.setTranslationX(-px(36));
        card.setAlpha(0f);
        card.animate().translationX(0f).alpha(1f).setDuration(320).setInterpolator(SLIDE).start();
        closeLater = () -> closeHost(false);
        MAIN.postDelayed(closeLater, ms);
        // Without the key helper (the TCL): the card's own window takes the remote, after its first moments.
        if (!InputKeyService.running) MAIN.postDelayed(() -> { if (host == win) takeKeys(c, win); }, PopupMotion.FRESH_MS);
        return true;
    }

    private static void closeHost(boolean byBack) {
        FrameLayout v = host;
        host = null;
        pauseFor = null;
        if (closeLater != null) MAIN.removeCallbacks(closeLater);
        closeLater = null;
        choices.clear();
        if (v == null) return;
        final boolean[] went = {false};
        Runnable gone = () -> {
            if (went[0]) return;
            went[0] = true;
            try {
                v.getContext().getSystemService(WindowManager.class).removeView(v);
            } catch (RuntimeException ignored) {
                // gone already
            }
        };
        MAIN.postDelayed(gone, 900L);
        if (byBack) {
            PopupMotion.backOut(v, v.getWidth(), 0f, gone);
            return;
        }
        View content = v.getChildAt(0);
        if (content == null) { gone.run(); return; }
        content.animate().translationX(-px(36)).alpha(0f).setDuration(240).withEndAction(gone).start();
    }

    private static boolean ok(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_BUTTON_A;
    }

    private static boolean play(int code) {
        return code == KeyEvent.KEYCODE_MEDIA_PLAY || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    }

    /**
     * The remote while a card shows, through the key helper (InputKeyService, before any app). How was it? takes Left,
     * Right, OK and Back; the pause card takes Back only, and goes as OK or Play reaches the app. True when it took it.
     */
    static boolean key(KeyEvent e) {
        if (host == null) return false;
        int code = e.getKeyCode();
        boolean back = code == KeyEvent.KEYCODE_BACK;
        if (!asking) {
            if (ok(code) || play(code)) {
                if (e.getAction() == KeyEvent.ACTION_UP) MAIN.post(() -> closeHost(false));
                return false;
            }
            if (!back || PopupMotion.passOn(e, shownAt, passing)) return false;
            backKey(e);
            return true;
        }
        boolean lr = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!back && !lr && !ok(code)) return false;
        if (PopupMotion.passOn(e, shownAt, passing)) return false;
        if (back) { backKey(e); return true; }
        if (lr && e.getAction() == KeyEvent.ACTION_DOWN) MAIN.post(() -> move(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
        if (ok(code) && e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) MAIN.post(WatchCards::save);
        return true;
    }

    private static void backKey(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) MAIN.post(() -> PopupMotion.press(host));
        else if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) MAIN.post(() -> closeHost(true));
    }

    /** No key helper: the card's window takes the remote. The pause card: OK or Play plays on, anything else hides it. */
    private static void takeKeys(Context c, FrameLayout win) {
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) win.getLayoutParams();
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            c.getSystemService(WindowManager.class).updateViewLayout(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        win.setFocusable(true);
        win.setFocusableInTouchMode(true);
        win.requestFocus();
        final boolean[] down = {false};
        win.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                down[0] = true;
                if (asking && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    move(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                }
                if (code == KeyEvent.KEYCODE_BACK && e.getRepeatCount() == 0) PopupMotion.press(host);
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_UP || !down[0]) return true;
            if (asking) {
                if (ok(code)) save();
                else if (code == KeyEvent.KEYCODE_BACK) closeHost(true);
                return true;
            }
            Track t = pauseFor;
            closeHost(code == KeyEvent.KEYCODE_BACK);
            if ((ok(code) || play(code)) && t != null) {
                try {
                    t.mc.getTransportControls().play();
                } catch (RuntimeException ignored) {
                    // the app went
                }
            }
            return true;
        });
    }

    // ---- How was it?: the card ----------------------------------------------------------------------------------------

    private static final List<LinearLayout> choices = new ArrayList<>();
    private static final int[] VALUES = {WatchRules.NOT_FOR_ME, WatchRules.LIKED, WatchRules.LOVED};
    private static int chosen = 1;
    private static Ask showingAsk;
    private static TextView question;

    private static void showAsk(Context c, Ask a, long since) {
        if (!a.test && !askOn(c)) return;
        if (!allowed(c, "howwasit", "how was it: " + a.name)) return;
        // Another pop-up up: waited for, a minute and a half at most.
        if ((othersUp() || host != null && asking) && SystemClock.uptimeMillis() - since < 90_000L) {
            MAIN.postDelayed(() -> showAsk(c, a, since), 1_500L);
            return;
        }
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        LinearLayout card = cardBox(c, a.poster);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView top = text(c, "YOU JUST FINISHED", 13, FAINT, 700);
        top.setLetterSpacing(0.15f);
        col.addView(top);
        col.addView(text(c, a.name, 32, WHITE, 800), margins(6));
        col.addView(text(c, a.line, 16, SOFT, 500), margins(6));
        question = text(c, "How was it?", 22, WHITE, 600);
        col.addView(question, margins(14));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        choices.clear();
        String[] words = {"Not for me", "Liked it", "Loved it"};
        for (int i = 0; i < 3; i++) {
            LinearLayout b = new LinearLayout(c);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setPadding(px(18), 0, px(18), 0);
            ImageView icon = new ImageView(c);
            icon.setImageDrawable(i == 0 ? PathIcon.thumbDown(WHITE) : i == 1 ? PathIcon.thumbUp(WHITE) : PathIcon.heart(WHITE));
            b.addView(icon, new LinearLayout.LayoutParams(px(20), px(20)));
            TextView t = text(c, words[i], 18, WHITE, 500);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
            tl.leftMargin = px(10);
            b.addView(t, tl);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, px(50));
            if (i > 0) bl.leftMargin = px(12);
            row.addView(b, bl);
            choices.add(b);
        }
        col.addView(row, margins(12));
        col.addView(text(c, "◀ ▶ choose · OK save · Back skip", 14, FAINT, 500), margins(12));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        chosen = 1;
        showingAsk = a;
        if (!showHost(c, card, px(740), ASK_MS, true)) return;
        paintChoices();
        AlertLog.note(c, "how was it shown: " + a.name + (a.test ? " (test)" : ""));
    }

    private static LinearLayout.LayoutParams margins(int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.topMargin = px(top);
        return l;
    }

    /** The three choices drawn: the one the remote is on ringed in white and bold, Loved it always pink. */
    private static void paintChoices() {
        for (int i = 0; i < choices.size(); i++) {
            LinearLayout b = choices.get(i);
            boolean on = i == chosen;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(25));
            g.setColor(i == 2 ? PINK : on ? 0x33FFFFFF : 0x1AFFFFFF);
            g.setStroke(Math.max(1, px(on ? 2.5f : 1.5f)), on ? WHITE : i == 2 ? 0x00FFFFFF : 0x38FFFFFF);
            b.setBackground(g);
            TextView t = (TextView) b.getChildAt(1);
            t.setTypeface(ScoreCard.outfit(b.getContext(), on ? 700 : i == 2 ? 600 : 500));
            b.animate().scaleX(on ? 1.04f : 1f).scaleY(on ? 1.04f : 1f).setDuration(120).start();
        }
    }

    private static void move(int by) {
        if (host == null || !asking || choices.isEmpty()) return;
        int was = chosen;
        chosen = Math.max(0, Math.min(choices.size() - 1, chosen + by));
        if (chosen != was) paintChoices();
        // The 20 seconds start again while choosing.
        if (closeLater != null) {
            MAIN.removeCallbacks(closeLater);
            MAIN.postDelayed(closeLater, ASK_MS);
        }
    }

    /** OK: the choice kept (a test's is not), said on the card for a moment, then the card goes. */
    private static void save() {
        Ask a = showingAsk;
        if (host == null || !asking || a == null) return;
        int value = VALUES[chosen];
        Context c = host.getContext().getApplicationContext();
        if (a.test) AlertLog.note(c, "how was it (test): " + WatchRules.word(value) + ", not saved");
        else Ratings.set(c, a.tv, a.id, value, a.genre, a.name);
        if (question != null) question.setText("Saved: " + WatchRules.word(value));
        if (closeLater != null) MAIN.removeCallbacks(closeLater);
        closeLater = () -> closeHost(false);
        MAIN.postDelayed(closeLater, 1_100L);
        asking = false;
    }

    // ---- the pause card ---------------------------------------------------------------------------------------------

    /** Off the main thread: the paused title's name and poster (WatchHistory's entry), then the card. */
    private static void pause(Context c, Track t, String pkg, String title, String sub, long pos, long dur, long pausedWall) {
        WatchHistory.Entry e = entry(c, pkg, title, sub);
        String name = e != null && e.show != null ? e.show : title;
        if (e != null && e.tv) {
            int[] at = YourShows.position(e);
            if (at != null) name = name + " · S" + at[0] + " E" + at[1];
        }
        Bitmap poster = e == null ? null : picture(c, e.poster != null ? e.poster : e.art);
        boolean lights = Cinema.raisedAt >= pausedWall - 1_000L;
        String shown = name;
        MAIN.post(() -> {
            PlaybackState st;
            try {
                st = t == null ? null : t.mc.getPlaybackState();
            } catch (RuntimeException ex) {
                return;
            }
            // Played on meanwhile: no card.
            if (t != null && (st == null || st.getState() != PlaybackState.STATE_PAUSED)) return;
            showPause(c, t, shown, poster, t == null ? pos : position(st), dur, lights, false, SystemClock.uptimeMillis());
        });
    }

    private static void showPause(Context c, Track t, String name, Bitmap poster, long pos, long dur, boolean lights, boolean test, long since) {
        if (!test && !pauseOn(c)) return;
        if (host != null && asking) return;
        if (othersUp()) {
            // A goal's card up: tried again while still paused, for half a minute.
            if (SystemClock.uptimeMillis() - since < 30_000L) MAIN.postDelayed(() -> {
                try {
                    if (t == null || t.mc.getPlaybackState() != null && t.mc.getPlaybackState().getState() == PlaybackState.STATE_PAUSED) {
                        showPause(c, t, name, poster, pos, dur, lights, test, since);
                    }
                } catch (RuntimeException ignored) {
                    // the app went
                }
            }, 2_000L);
            return;
        }
        if (!allowed(c, "pausecard", "pause card: " + name)) return;
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        LinearLayout card = cardBox(c, poster);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout chip = new LinearLayout(c);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(px(9), px(4), px(9), px(4));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(AMBER);
        cg.setCornerRadius(px(5));
        chip.setBackground(cg);
        View dot = new View(c);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xFF1A1205);
        dot.setBackground(dg);
        chip.addView(dot, new LinearLayout.LayoutParams(px(7), px(7)));
        TextView pw = text(c, "PAUSED", 12, 0xFF1A1205, 800);
        pw.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams pwl = new LinearLayout.LayoutParams(-2, -2);
        pwl.leftMargin = px(6);
        chip.addView(pw, pwl);
        head.addView(chip, new LinearLayout.LayoutParams(-2, -2));
        TextView nm = text(c, name, 28, WHITE, 800);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = px(12);
        head.addView(nm, nl);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));
        // How far in, as a bar.
        float share = dur <= 0 ? 0f : Math.max(0f, Math.min(1f, pos / (float) dur));
        FrameLayout bar = new FrameLayout(c);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x33FFFFFF);
        track.setCornerRadius(px(3));
        bar.setBackground(track);
        View fill = new View(c);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(WHITE);
        fg.setCornerRadius(px(3));
        fill.setBackground(fg);
        bar.addView(fill, new FrameLayout.LayoutParams(0, -1));
        bar.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> {
            int w = Math.round((r - l) * share);
            FrameLayout.LayoutParams fl = (FrameLayout.LayoutParams) fill.getLayoutParams();
            if (fl.width != w) { fl.width = w; fill.setLayoutParams(fl); }
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, px(6));
        bl.topMargin = px(16);
        col.addView(bar, bl);
        LinearLayout under = new LinearLayout(c);
        under.setOrientation(LinearLayout.HORIZONTAL);
        under.addView(text(c, WatchRules.inLine(pos), 15, SOFT, 500), new LinearLayout.LayoutParams(0, -2, 1f));
        under.addView(text(c, WatchRules.leftLine(pos, dur), 15, SOFT, 500));
        LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-1, -2);
        ul.topMargin = px(8);
        col.addView(under, ul);
        int now = LocalTime.now().toSecondOfDay() / 60;
        int end = WatchRules.endMinute(now, dur - pos);
        col.addView(text(c, "Ends " + WatchRules.clock(end) + " if you carry on now", 30, WHITE, 700), margins(10));
        int bed = FitsTonight.bedMinutes(c);
        int spare = WatchRules.beforeBed(end, bed);
        String bedWords = WatchRules.bedLine(spare);
        TextView line = text(c, bedWords + (lights ? " · the lights came back up and dim again on Play" : ""), 17, 0xFFE3E5F3, 500);
        if (spare < 0) line.setTextColor(0xFFFFCF8A);
        col.addView(line, margins(4));
        col.addView(text(c, "Play hides it · Back hides it", 14, FAINT, 500), margins(14));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!showHost(c, card, px(780), PAUSE_SHOW_MS, false)) return;
        pauseFor = t;
        // The chip's dot blinks while it is up.
        dot.animate().alpha(0.3f).setDuration(800).withEndAction(new Runnable() {
            boolean low = true;
            @Override public void run() {
                if (host == null || !dot.isAttachedToWindow()) return;
                low = !low;
                dot.animate().alpha(low ? 0.3f : 1f).setDuration(800).withEndAction(this).start();
            }
        }).start();
        AlertLog.note(c, "pause card shown: " + name + (test ? " (test)" : ""));
    }

    // ---- adb ---------------------------------------------------------------------------------------------------------

    /**
     * adb only, through GoalWatchService: --es test_howwasit auto (a film, Wildwood), season (a series' last episode of
     * a season, MobLand's second), or a film's name; --es test_pause auto (The Dog Stars, 1h 12m into 1h 59m), or a
     * film's name. Looked up on TMDB for the real poster; How was it?'s choice is not saved.
     */
    static void test(Context ctx, Intent i) {
        Context c = ctx.getApplicationContext();
        if (i.hasExtra("test_howwasit")) {
            String v = String.valueOf(i.getStringExtra("test_howwasit")).trim();
            boolean season = "season".equalsIgnoreCase(v);
            String name = v.isEmpty() || "auto".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v) ? "Wildwood" : season ? "MobLand" : v;
            AlertLog.note(c, "test how was it: " + name);
            IO.execute(() -> {
                PagesV2.Title t = lookUp(c, name, season);
                Ask a = new Ask();
                a.test = true;
                a.tv = season;
                a.id = t == null ? 0 : t.id;
                a.name = t == null ? name : t.title;
                long dur = season ? 58 * 60_000L : 132 * 60_000L;
                String when = WatchRules.clock(LocalTime.now().toSecondOfDay() / 60);
                a.line = season ? "Season 2's last episode · ended " + when : WatchRules.length(dur) + " · ended " + when;
                a.poster = t == null ? null : picture(c, t.poster);
                MAIN.post(() -> showAsk(c, a, SystemClock.uptimeMillis()));
            });
        }
        if (i.hasExtra("test_pause")) {
            String v = String.valueOf(i.getStringExtra("test_pause")).trim();
            String name = v.isEmpty() || "auto".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v) ? "The Dog Stars" : v;
            AlertLog.note(c, "test pause card: " + name);
            IO.execute(() -> {
                PagesV2.Title t = lookUp(c, name, false);
                Bitmap poster = t == null ? null : picture(c, t.poster);
                String shown = t == null ? name : t.title;
                MAIN.post(() -> showPause(c, null, shown, poster, 72 * 60_000L + 30_000L, 119 * 60_000L, true, true,
                        SystemClock.uptimeMillis()));
            });
        }
    }

    private static PagesV2.Title lookUp(Context c, String name, boolean tv) {
        String key = Config.themeString(c, "tmdb_key", "").trim();
        if (key.isEmpty()) return null;
        try {
            List<PagesV2.Title> got = PagesV2.tmdb(key, "search/" + (tv ? "tv" : "movie") + "?query=" + java.net.URLEncoder.encode(name, "UTF-8"), tv);
            return got.isEmpty() ? null : got.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- the cards' small line icons --------------------------------------------------------------------------------

    /** A line icon drawn from a path in a 24-unit square (as the mock-ups' SVG icons), stroked or filled. */
    static final class PathIcon extends Drawable {
        private final Path path;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float stroke;

        PathIcon(Path path, int colour, boolean fill, float stroke) {
            this.path = path;
            this.stroke = stroke;
            paint.setColor(colour);
            paint.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override public void draw(Canvas cv) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            cv.save();
            cv.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
            cv.scale(s, s);
            paint.setStrokeWidth(stroke);
            cv.drawPath(path, paint);
            cv.restore();
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @SuppressWarnings("deprecation")
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        private static Path thumb() {
            Path p = new Path();
            // The hand, then the cuff.
            p.moveTo(7, 11);
            p.lineTo(11.5f, 4);
            p.quadTo(13.6f, 4.3f, 13.5f, 6.3f);
            p.lineTo(12.8f, 10);
            p.lineTo(18.5f, 10);
            p.quadTo(20.6f, 10.2f, 20.5f, 12.4f);
            p.lineTo(19.1f, 18.4f);
            p.quadTo(18.7f, 20, 17.1f, 20);
            p.lineTo(7, 20);
            p.addRect(4, 10, 7, 20, Path.Direction.CW);
            return p;
        }

        static PathIcon thumbUp(int colour) {
            return new PathIcon(thumb(), colour, false, 1.9f);
        }

        static PathIcon thumbDown(int colour) {
            Path p = thumb();
            Matrix m = new Matrix();
            m.setScale(1f, -1f, 12f, 12f);
            p.transform(m);
            return new PathIcon(p, colour, false, 1.9f);
        }

        static PathIcon heart(int colour) {
            Path p = new Path();
            p.moveTo(12, 21);
            p.cubicTo(5, 16, 2.5f, 12.5f, 2.5f, 9);
            p.cubicTo(2.5f, 6, 4.8f, 4, 7.3f, 4);
            p.cubicTo(9.2f, 4, 11, 5.2f, 12, 7);
            p.cubicTo(13, 5.2f, 14.8f, 4, 16.7f, 4);
            p.cubicTo(19.2f, 4, 21.5f, 6, 21.5f, 9);
            p.cubicTo(21.5f, 12.5f, 19, 16, 12, 21);
            p.close();
            return new PathIcon(p, colour, true, 0f);
        }

        /** Round again (Because you watched's pill): a circle nearly closed, its arrow at the top left. */
        static PathIcon again(int colour) {
            Path p = new Path();
            p.addArc(new RectF(4, 4, 20, 20), 225, 315);
            p.moveTo(4, 4);
            p.lineTo(4, 8.5f);
            p.lineTo(8.5f, 8.5f);
            return new PathIcon(p, colour, false, 2.2f);
        }
    }
}
