package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The ad-break screen over the channel in the player, through the whole of what is on it: the game the channel's
 * programme in the guide names (the playlist's own guide, as TiviMate shows it, then Sky's), from the programme's start
 * to its end; else the game found another way (the channel's name, Sky's guide, Match mode, the scoreboard read on the
 * channel, the player's banner), from an hour before kick-off to half an hour after full time; else, the channel's own
 * scoreboard seen lately, a general screen (Session, find). When the channel's own logo goes (the adverts) a strip
 * rises along the foot, its line by the game's phase (BEFORE KICK-OFF and the countdown, HALF-TIME and the score, AD
 * BREAK with the score and the minute, FULL TIME; AD BREAK and the programme for the general screen), with three cards
 * in the line-up card's look taking turns through what is really known (a widget with nothing behind it is left out);
 * for a game, the line-up card (before kick-off and at half-time), the table and fans' posts (FanPosts) too. It goes
 * when the logo comes back, when the programme's time is over, or when the channel or the app changes. It covers every
 * break, half-time's too: there the first half and the hop are in its cards, the half-time card and the hop's card give
 * way (AdBreakLogic.breaks), and its last card gets the half-time widgets (HalfTimeFun): the quiz played with the
 * remote for points, fun facts and today's prayer times. Before kick-off it keeps to the usual widgets.
 *
 * <p>The logo is told by its own learned shape (LogoShape), from pictures of its place of the player's window alone
 * (ScreenShots), about one a second from half an hour before the window opens; where such pictures cannot be had
 * (before Android 14) the screen never comes by itself. The remote stays with the player: Down goes into the cards (a
 * white ring on the card picked), Left and Right pick, Up and Down scroll it (it holds still while picked), OK does what
 * the widget offers (another game's channel, Match mode, the hop), Back hands the remote back. adb can bring it up over
 * anything (AdBreakReceiver) for ten minutes, or log what would count now ("session").
 */
final class AdBreak {
    private AdBreak() { }

    private static final String FILE = "ad_break", TAG = "AdBreak";
    /** The test's showing, at most. */
    static final long TEST_MS = 10 * 60_000L;
    /** Fan posts: read again this often while up; one more comes this often; at most this many at once. */
    static final long POSTS_EVERY_MS = 60_000L, POST_GAP_MS = 8_000L;
    static final int POSTS_SHOWN = 3;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AdBreak");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static Context app;
    private static float s = 1f;
    private static volatile List<ScoresTicker.Game> latest = new ArrayList<>();
    /** The game whose build-up is on the channel watched (the scores poll's word), its channels. */
    private static boolean checking, looking, forced;
    private static long forcedUntil;
    /** The test as at half-time: the half-time widgets in the last card whatever the game's state. */
    private static boolean forcedHalf;
    private static final AdBreakLogic.Trigger trigger = new AdBreakLogic.Trigger();
    /**
     * Your own logo over the channel's (BoardCover's cover) stepping aside while the channel's logo is gone (an ad
     * break) and back with it: its own trigger from the same looks, whatever the ad-break screen does, on any channel.
     */
    private static final AdBreakLogic.Trigger away = new AdBreakLogic.Trigger();
    private static LogoShape.Here logoHere;
    private static String logoKey = "";

    // ---- the switch ----------------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean on(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
        if (!on) hide("switched off");
    }

    static boolean isShowing() {
        return strip != null;
    }

    // ---- what is on the channel watched (each scores poll) --------------------------------------------------------------

    /**
     * What is on the channel watched, and when its breaks count: the game (null: none known, the general screen), where
     * that came from (the playlist's guide, Sky's guide, the channel's name, the scoreboard seen, Match mode), the window
     * (the programme in the guide; else an hour before kick-off to half an hour after full time; else while the
     * channel's scoreboard is seen) and the programme's title when the guide gives one.
     */
    static final class Session {
        final ScoresTicker.Game g;
        final String source, title, channel;
        final long from, to;

        Session(ScoresTicker.Game g, String source, long[] window, String title, String channel) {
            this.g = g;
            this.source = source;
            this.from = window[0];
            this.to = window[1];
            this.title = title;
            this.channel = channel == null ? "" : channel;
        }

        long[] window() {
            return new long[]{from, to};
        }

        /** The same thing on (its end moves on while a game runs on, so only where it starts counts). */
        boolean same(Session o) {
            return o != null && source.equals(o.source) && InStep.base(channel).equals(InStep.base(o.channel)) && from == o.from
                    && (g == null ? o.g == null : o.g != null && g.id.equals(o.g.id));
        }

        @Override public String toString() {
            java.text.SimpleDateFormat hm = new java.text.SimpleDateFormat("HH:mm", Locale.UK);
            return source + (g == null ? ", no game" : ", " + AlertLog.game(g)) + ", on " + channel + ", "
                    + hm.format(new java.util.Date(from)) + "–" + hm.format(new java.util.Date(to)) + (title == null ? "" : ", \"" + title + "\"");
        }
    }

    private static volatile Session session;
    /** When the scores last had each game at half-time, and on. */
    private static final Map<String, Long> halfSeen = new java.util.concurrent.ConcurrentHashMap<>(),
            liveSeen = new java.util.concurrent.ConcurrentHashMap<>();

    /** Each scores poll (main thread): what is on the channel watched, worked out off the main thread. */
    static void games(Context c, List<ScoresTicker.Game> games) {
        app = c.getApplicationContext();
        if (games != null) {
            latest = new ArrayList<>(games);
            long t = System.currentTimeMillis();
            for (ScoresTicker.Game g : games) {
                if (g == null || g.id == null || g.state != 0) continue;
                liveSeen.put(g.id, t);
                if (g.halftime) halfSeen.put(g.id, t);
            }
        }
        if (checking || games == null) return;
        if (!on(app)) {
            session = null;
            if (!forced) hide("switched off");
            return;
        }
        String channel = InStep.channel(app);
        boolean front = BoardCover.playerInFront(app) && !GoalWatchService.homeInFront;
        if (!front || channel.isEmpty()) {
            // Nothing new to work out with no player in front; a session whose window is over is let go.
            Session ss = session;
            if (ss != null && System.currentTimeMillis() > ss.to) session = null;
            return;
        }
        checking = true;
        Context a = app;
        List<ScoresTicker.Game> all = new ArrayList<>(games);
        io.execute(() -> {
            Session found = null;
            try {
                found = find(a, channel, all, System.currentTimeMillis());
            } catch (RuntimeException e) {
                android.util.Log.w(TAG, "what is on not worked out: " + e.getClass().getSimpleName());
            }
            Session ss = found;
            main.post(() -> {
                checking = false;
                Session was = session;
                session = ss;
                if (ss != null && !ss.same(was)) {
                    android.util.Log.i(TAG, "on the channel: " + ss);
                    AlertLog.note(a, "ad breaks counted: " + ss);
                }
                // Ready before the break (again at kick-off, half-time, the second half and full time).
                if (ss != null && ss.g != null) prefetch(ss.g);
                else if (ss != null) prefetchGeneral();
                if (ss != null) startLooking();
                else if (was != null && isShowing() && !forced) hide("nothing on to break from");
            });
        });
    }

    /**
     * What is on the channel (off the main thread): a game the guide's programme names (the playlist's own guide, as
     * TiviMate shows it, then Sky's), the programme's whole time; else the game found another way (the channel's name,
     * the scoreboard the TV has been reading on it, Sky's guide or ESPN for the game's own channels, Match mode, the
     * player's banner), an hour before kick-off to half an hour after full time; else, the channel's scoreboard seen
     * lately with no game known, the general screen while it is. Null when none.
     */
    static Session find(Context c, String channel, List<ScoresTicker.Game> games, long now) {
        List<String> tuned = InStep.tunedNames(c);
        if (tuned.isEmpty()) tuned = java.util.Collections.singletonList(channel);
        String[] sky = c.getResources().getStringArray(R.array.football_tv_channels);
        List<ScoresTicker.Game> day = new ArrayList<>();
        for (ScoresTicker.Game g : games) {
            if (g == null || g.id == null || g.drivers != null || g.text != null || g.kickoff <= 0) continue;
            if (g.kickoff > now - 8 * 3_600_000L && g.kickoff < now + 14 * 3_600_000L) day.add(g);
        }
        // a. The guide's programme on the channel now (or starting within a quarter of an hour).
        List<Listings.Prog> progs = new ArrayList<>();
        List<String> from = new ArrayList<>();
        for (Listings.Prog p : Listings.onNow(c, tuned, now, AdBreakLogic.SOON_MS)) { progs.add(p); from.add("listings"); }
        for (Object[] e : TvGuide.around(sky, tuned, now, AdBreakLogic.SOON_MS)) {
            progs.add(new Listings.Prog((String) e[0], "", (Long) e[1], (Long) e[2]));
            from.add("guide");
        }
        String title = null;
        for (int i = 0; i < progs.size(); i++) {
            Listings.Prog p = progs.get(i);
            if (title == null && p.on(now) && !p.title.isEmpty()) title = p.title;
            List<ScoresTicker.Game> in = new ArrayList<>();
            for (ScoresTicker.Game g : day) if (g.kickoff >= p.start - 30 * 60_000L && g.kickoff <= p.end) in.add(g);
            int k = AdBreakLogic.pick(p.title, names(in));
            if (k < 0 && !p.desc.isEmpty()) k = AdBreakLogic.pick(p.desc, names(in));
            if (k >= 0) return new Session(in.get(k), from.get(i), AdBreakLogic.listedWindow(p.start, p.end, now), p.title, channel);
        }
        // b. No guide's word: the game found another way, round its kick-off.
        List<ScoresTicker.Game> near = new ArrayList<>();
        for (ScoresTicker.Game g : day) {
            if (g.kickoff >= now - AdBreakLogic.AFTER_MS - 60 * 60_000L && g.kickoff <= now + AdBreakLogic.WINDOW_MS + AdBreakLogic.LEARN_AHEAD_MS) near.add(g);
        }
        ScoresTicker.Game g = null;
        String how = null;
        // The channel's own name (an event channel: "03. England vs Croatia 19:45").
        int k = AdBreakLogic.pick(channel, names(near));
        if (k >= 0) { g = near.get(k); how = "channel"; }
        // Sky's guide (or ESPN) has the game on this very channel.
        Map<String, List<String>> all = new HashMap<>(), own = new HashMap<>();
        if (g == null) {
            for (ScoresTicker.Game x : near) {
                TvGuide.Watch w = TvGuide.watch(sky, x);
                if (w == null || w.names.isEmpty()) continue;
                all.put("game:" + x.id, w.names);
                own.put("game:" + x.id, new ArrayList<>(w.own()));
            }
            for (ScoresTicker.Game x : near) {
                if (InStep.onChannel(tuned.subList(0, 1), own.get("game:" + x.id))) { g = x; how = "guide"; break; }
            }
        }
        // Match mode's game.
        if (g == null) {
            g = byId(near, MatchMode.game(c));
            if (g != null) how = "match mode";
        }
        // The game whose scoreboard the TV has been reading on this channel (ChannelSight), or the board cover's.
        if (g == null) {
            String id = ChannelSight.seenAt(channel) >= now - AdBreakLogic.GENERAL_MS ? ChannelSight.seenFor(channel) : null;
            if (id == null || id.isEmpty()) id = BoardCover.game(c);
            g = byId(near, id);
            if (g != null) how = "board";
        }
        // The player's banner, as it was last read on this channel (its programme's title).
        if (g == null) {
            List<String> seen = bannerFor(channel, now);
            for (String t : seen) {
                k = AdBreakLogic.pick(t, names(near));
                if (k >= 0) { g = near.get(k); how = "banner"; break; }
            }
        }
        // The competition's usual broadcaster, when only one game can be on it.
        if (g == null) {
            OverlayPrompt.Found f = OverlayPrompt.watched(near, MatchMode.game(c), tuned, own, all);
            if (f != null && f.sure) { g = f.game; how = "broadcaster"; }
        }
        if (g != null) {
            Long live = liveSeen.get(g.id);
            long[] w = AdBreakLogic.gameWindow(g.kickoff, g.state, now, live == null ? 0 : live);
            if (now <= w[1]) return new Session(g, how, w, title, channel);
        }
        // c. No game known, but the channel's own scoreboard seen lately: a match on, whatever it is.
        long seen = ChannelSight.seenAt(channel);
        if (seen > 0 && now - seen < AdBreakLogic.GENERAL_MS) {
            return new Session(null, "general", AdBreakLogic.generalWindow(seen), title, channel);
        }
        return null;
    }

    /** The player's banner's words as last read (OverlayPrompt.seen), with the channel then watched and when. */
    private static volatile List<String> banner = new ArrayList<>();
    private static volatile String bannerChannel = "";
    private static volatile long bannerAt;

    /** The player's banner read (main thread or any): kept ten minutes for the channel then watched. */
    static void banner(Context c, List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.size() > 40) return;
        List<String> keep = new ArrayList<>();
        for (String t : texts) if (t != null && t.length() >= 5 && t.length() <= 120) keep.add(t);
        if (keep.isEmpty()) return;
        banner = keep;
        bannerChannel = InStep.channel(c);
        bannerAt = System.currentTimeMillis();
    }

    private static List<String> bannerFor(String channel, long now) {
        if (now - bannerAt > 10 * 60_000L || !InStep.base(bannerChannel).equals(InStep.base(channel))) return java.util.Collections.emptyList();
        return banner;
    }

    private static List<String[][]> names(List<ScoresTicker.Game> games) {
        List<String[][]> out = new ArrayList<>();
        for (ScoresTicker.Game g : games) out.add(new String[][]{{g.homeName, g.home}, {g.awayName, g.away}});
        return out;
    }

    private static ScoresTicker.Game byId(List<ScoresTicker.Game> games, String id) {
        if (id == null || id.isEmpty()) return null;
        for (ScoresTicker.Game g : games) if (id.equals(g.id)) return g;
        return null;
    }

    /** adb: what would count on the channel watched now, logged (nothing shown). */
    static void logSession(Context c) {
        Context a = c.getApplicationContext();
        String channel = InStep.channel(a);
        List<ScoresTicker.Game> all = new ArrayList<>(latest);
        io.execute(() -> {
            Session ss = channel.isEmpty() ? null : find(a, channel, all, System.currentTimeMillis());
            android.util.Log.i(TAG, "session now: " + (channel.isEmpty() ? "no channel tuned" : ss == null ? "nothing on " + channel : ss.toString())
                    + "; player in front: " + BoardCover.playerInFront(a));
        });
    }

    // ---- the channel's logo, a look a second ----------------------------------------------------------------------------

    private static void startLooking() {
        if (looking) return;
        looking = true;
        main.post(lookRun);
    }

    private static final Runnable lookRun = AdBreak::look;

    private static void look() {
        Context c = app;
        long now = System.currentTimeMillis();
        if (forced) {
            if (now > forcedUntil) {
                forced = false;
                forcedHalf = false;
                hide("the test's time is up");
            } else {
                main.postDelayed(lookRun, 1_000L);
                return;
            }
        }
        Session ss = session;
        boolean live = c != null && ss != null && now <= ss.to;
        // Your logo up over the channel's: looked for on any channel, a game on it or not.
        boolean cover = c != null && BoardCover.logoCoverUp() && BoardCover.logoAwayOn(c);
        if (!live && !cover) {
            looking = false;
            int a = trigger.step(now, true, false, false, false, -1);
            if (a == AdBreakLogic.HIDE || isShowing()) hide(ss == null ? "nothing on to break from" : "the programme's time is over");
            if (ss != null && session == ss) session = null;
            awayAct(away.step(now, false, false, false, false, -1));
            return;
        }
        boolean front = BoardCover.playerInFront(c) && !GoalWatchService.homeInFront;
        String channel = InStep.channel(c);
        boolean onChannel = live && !channel.isEmpty() && InStep.base(channel).equals(InStep.base(ss.channel));
        boolean inWindow = live && AdBreakLogic.inside(ss.window(), now) && mayCome(c, ss, now);
        // The logo is looked at (and learned) from LEARN_AHEAD_MS before the window opens; the screen comes only inside it.
        boolean forScreen = live && on(c) && front && onChannel && AdBreakLogic.watch(ss.window(), now);
        boolean forCover = cover && front && !channel.isEmpty();
        if (live && !forScreen) act(c, ss, trigger.step(now, on(c), front, onChannel, inWindow, -1));
        if (!forCover) awayAct(away.step(now, cover, front, true, true, -1));
        if (!forScreen && !forCover) {
            main.postDelayed(lookRun, 2_000L);
            return;
        }
        if (!ScreenShots.available() || !ScreenShots.windowOnly()) {
            // No pictures of the player's window alone here: the screen never comes by itself.
            main.postDelayed(lookRun, 5_000L);
            return;
        }
        BoardPlace place = BoardPlace.get(c, channel);
        int sw = 1920, sh = 1080;
        int bx = Math.round(place.lx * sw), by = Math.round(place.ly * sh), bw = Math.round(place.lw * sw), bh = Math.round(place.lh * sh);
        int m = LogoShape.MARGIN, mx = Math.max(m, Math.round(0.4f * bw)), my = Math.max(m, bh / 2 + m);
        int x0 = Math.max(0, bx - mx), y0 = Math.max(0, by - my), x1 = Math.min(sw, bx + bw + mx), y1 = Math.min(sh, by + bh + my);
        String key = ChannelOverlays.key(channel);
        if (logoHere == null || !logoHere.same(key, sw, sh, x0, y0, x1 - x0, y1 - y0, bx - x0, by - y0, bx - x0 + bw, by - y0 + bh)) {
            logoHere = new LogoShape.Here(new java.io.File(c.getFilesDir(), "adbreak_logos"), key, sw, sh, x0, y0, x1 - x0, y1 - y0,
                    bx - x0, by - y0, bx - x0 + bw, by - y0 + bh);
            if (!key.equals(logoKey)) {
                trigger.set(isShowing());
                // Another channel: your logo back at once, its looks from the start.
                away.set(false);
                BoardCover.logoAway(false);
            }
            logoKey = key;
        }
        LogoShape.Here at = logoHere;
        RectF part = new RectF(x0 / (float) sw, y0 / (float) sh, x1 / (float) sw, y1 / (float) sh);
        boolean asked = ScreenShots.take(new RectF[]{part}, new int[][]{{at.w, at.h}}, (parts, when, own) -> {
            Bitmap got = parts[0];
            float presence = -1f;
            if (got != null && own && !ScreenShots.black(got)) {
                int[] px = new int[at.w * at.h];
                if (got.getWidth() == at.w && got.getHeight() == at.h) {
                    got.getPixels(px, 0, at.w, 0, 0, at.w, at.h);
                    // Learned from as it goes (the logo is up most of the time), then asked whether it is up now.
                    at.take(px, SystemClock.uptimeMillis());
                    presence = at.presence(px);
                }
            }
            if (got != null) got.recycle();
            Session now2 = session;
            long t = System.currentTimeMillis();
            if (forScreen && now2 != null) act(c, now2, trigger.step(t, on(c), true, true, AdBreakLogic.inside(now2.window(), t) && mayCome(c, now2, t), presence));
            if (forCover) awayAct(away.step(t, BoardCover.logoCoverUp(), true, true, true, presence));
            // Away: looked at twice as often, so your logo is back hardly a moment after the channel's.
            main.postDelayed(lookRun, away.showing ? 500L : 1_000L);
        });
        if (!asked) main.postDelayed(lookRun, 400L);
    }

    /** The watch started for your logo over the channel's (BoardCover, as it comes up): it looks while the cover is up. */
    static void watchCover(Context c) {
        if (app == null) app = c.getApplicationContext();
        startLooking();
    }

    /** Your logo away as the channel's goes (SHOW) and back as it returns (HIDE). */
    private static void awayAct(int what) {
        if (what == AdBreakLogic.SHOW) {
            android.util.Log.i(TAG, "the channel's logo gone: your logo steps aside");
            BoardCover.logoAway(true);
        } else if (what == AdBreakLogic.HIDE) {
            android.util.Log.i(TAG, "the channel's logo back: your logo too");
            BoardCover.logoAway(false);
        }
    }

    /**
     * Whether the screen may come for this session now (AdBreakLogic.breaks): at every break, half-time's too; not for
     * another game than Match mode's while its quiet is on.
     */
    private static boolean mayCome(Context c, Session ss, @SuppressWarnings("unused") long now) {
        ScoresTicker.Game g = fresh(ss.g);
        boolean quiet = MatchMode.keepsBack(c, "adbreak", g == null ? null : g.id);
        int m = AdBreakLogic.breaks(isShowing(), quiet);
        return (m & AdBreakLogic.AD) != 0;
    }

    /** Whether the game watched is at half-time as it shows on the channel. */
    private static boolean halfTimeShows(Context c, ScoresTicker.Game g, long now) {
        Long seen = halfSeen.get(g.id);
        return AdBreakLogic.halfTimeShows(g.halftime, seen == null ? 0 : seen, InStep.holdMs(c, true), now);
    }

    /** Whether the half-time card or the hop's card may show now (one of AdBreakLogic's): not while this screen is up. */
    static boolean allows(@SuppressWarnings("unused") Context c, int what) {
        int m = AdBreakLogic.breaks(isShowing(), false);
        return (m & what) != 0;
    }

    /** The break's screen waiting for the intros over the break to end (ClassicIntro: never on the screen with them). */
    private static Session afterIntro;

    private static void act(Context c, Session ss, int what) {
        if (what == AdBreakLogic.SHOW) {
            android.util.Log.i(TAG, "the channel's logo gone: up (" + ss.source + ")");
            // The half-time card or the hop's card gives way: the screen covers the break.
            if (LineupCard.halfOrHopShowing()) {
                AlertLog.note(c, "half-time card closed: the ad-break screen covers the break");
                LineupCard.close(false);
            }
            // An intro on, or the intros this break gets before the game (every break): the screen after them.
            ScoresTicker.Game g = fresh(ss.g);
            if (ClassicIntro.introShowing() || g != null && ClassicIntro.breakIntros(c, g)) {
                afterIntro = ss;
                return;
            }
            show(c, ss);
        } else if (what == AdBreakLogic.HIDE) {
            android.util.Log.i(TAG, "away");
            afterIntro = null;
            ClassicIntro.breakOver();
            hide("the logo back, or the programme, channel or app changed");
        }
    }

    /** An intro has come over the channel (ClassicIntro): the screen steps aside for it, back after it while the break goes on. */
    static void introUp() {
        if (!isShowing()) return;
        afterIntro = shown;
        hide("an intro over the channel");
    }

    /** The intros over a break have ended: the break's own screen, while the break goes on. Main thread. */
    static void introGone(Context c) {
        Session ss = afterIntro;
        afterIntro = null;
        if (ss != null && trigger.showing && session == ss) show(c, ss);
    }

    // ---- the test (adb) -------------------------------------------------------------------------------------------------

    /** adb: up now over anything for TEST_MS, for what is on the channel or else the soonest game to come. */
    static void test(Context c, boolean up) {
        test(c, up, false);
    }

    /** adb: as test, with the half-time widgets (the quiz, fun facts, prayer times) when half is asked for. */
    static void test(Context c, boolean up, boolean half) {
        forcedHalf = up && half;
        app = c.getApplicationContext();
        if (!up) {
            forced = false;
            trigger.set(false);
            hide("asked over adb");
            return;
        }
        long now = System.currentTimeMillis();
        Session ss = session;
        if (ss == null) {
            ScoresTicker.Game g = null;
            for (ScoresTicker.Game x : latest) {
                if (x == null || x.id == null || x.drivers != null || x.text != null || x.state != 1 || x.kickoff <= now) continue;
                if (g == null || score(x) > score(g) || score(x) == score(g) && x.kickoff < g.kickoff) g = x;
            }
            if (g == null) {
                android.util.Log.i(TAG, "test: no game to come in the scores");
                return;
            }
            ss = new Session(g, "test", new long[]{now, now + TEST_MS}, null, InStep.channel(app));
        }
        forced = true;
        forcedUntil = now + TEST_MS;
        trigger.set(true);
        if (ss.g != null) prefetch(ss.g); else prefetchGeneral();
        show(app, ss);
        if (!looking) {
            looking = true;
            main.postDelayed(lookRun, 1_000L);
        }
    }

    /** The test's pick: a Premier League game first, then any. */
    private static int score(ScoresTicker.Game g) {
        return "eng.1".equals(g.slug) ? 2 : 1;
    }

    // ---- what it shows: fetched once a game (off the main thread) ------------------------------------------------------

    /** Everything known about the game for the widgets. */
    static final class Pack {
        ScoresTicker.Game g;
        JSONObject summary;
        String homeForm = "", awayForm = "";
        List<AdBreakLogic.Row> table = new ArrayList<>();
        List<String[]> h2h = new ArrayList<>(), homeNext = new ArrayList<>(), awayNext = new ArrayList<>(), absent = new ArrayList<>();
        String referee;
        Bitmap mark;
        final Map<String, Bitmap> crests = new HashMap<>(), faces = new HashMap<>();
        String[][] leaders = new String[2][];
        String weatherHead, weatherLine;
        List<String> headlines = new ArrayList<>();
        Kits.Kit homeKit, awayKit;
        String homeKitName, awayKitName;
        String[] clip;
        Bitmap clipStill;
        List<Object[]> alsoNow = new ArrayList<>();
        /** Today's F1 sessions still to come: {"HH:mm", the session, the Grand Prix}; and F1's logo for them. */
        List<String[]> f1Later = new ArrayList<>();
        Bitmap f1Logo;
        /** Players you follow (Players) whose club plays today: {name, club id, opponent id, when, the club's name}. */
        List<String[]> mine = new ArrayList<>();
        LineupCard.Data lineups;
        long at;
        /** The general screen (no game known on the channel): games on now (followed clubs first), today's results, later today. */
        List<ScoresTicker.Game> liveNow = new ArrayList<>(), doneToday = new ArrayList<>(), laterToday = new ArrayList<>();
        /** At half-time: the first half (the half-time card's numbers: scorers, possession, shots). */
        MoreAlerts.Stats half;
        /** At full time: the whole game (the final score, every scorer, possession, shots). */
        MoreAlerts.Stats full;
        /** For the half-time widgets (HalfTimeFun): today's prayer times, the next one, and whose times they are. */
        List<Prayer.Slot> prayers = new ArrayList<>();
        Prayer.Slot prayerNext;
        String prayerFoot;
    }

    private static Pack pack;
    private static String packFor = "";
    private static long packAt;

    /** The game's widgets fetched (once each ten minutes at most), so they are ready when the break comes. */
    private static void prefetch(ScoresTicker.Game g) {
        if (g == null) return;
        long now = System.currentTimeMillis();
        // Read again when the game moves on (kick-off, half-time, the second half, full time), else each ten minutes.
        String key = g.id + "|" + g.state + (g.halftime ? "h" : "");
        if (key.equals(packFor) && now - packAt < 10 * 60_000L) return;
        packFor = key;
        packAt = now;
        Context c = app;
        io.execute(() -> {
            Pack p = load(c, g);
            main.post(() -> {
                if (!key.equals(packFor)) return;
                pack = p;
                if (isShowing() && shownFor != null && shownFor.id.equals(g.id)) fill(c, p);
            });
        });
        fetchPosts(g);
    }

    private static Pack generalPack;
    private static long generalAt;

    /** The general screen's widgets fetched (once each three minutes at most). */
    private static void prefetchGeneral() {
        long now = System.currentTimeMillis();
        if (now - generalAt < 3 * 60_000L) return;
        generalAt = now;
        Context c = app;
        io.execute(() -> {
            Pack p = loadGeneral(c);
            main.post(() -> {
                generalPack = p;
                if (isShowing() && shown != null && shown.g == null) fill(c, p);
            });
        });
    }

    static final String BBC_FOOTBALL = "https://feeds.bbci.co.uk/sport/football/rss.xml";

    /**
     * The general screen (a match on with no game known): the scores now, the clubs you follow first; today's results;
     * the games and F1 later today; your players today; BBC Sport's football headlines. No posts, line-ups or table.
     */
    private static Pack loadGeneral(Context c) {
        Pack p = new Pack();
        p.at = System.currentTimeMillis();
        int crestPx = 96;
        java.util.Set<String> clubs = new java.util.HashSet<>(java.util.Arrays.asList(SyncHub.starredClubs(c)));
        Calendar day = Calendar.getInstance();
        for (ScoresTicker.Game x : latest) {
            if (x == null || x.id == null || x.drivers != null || x.text != null) continue;
            if (x.state == 0) { p.liveNow.add(x); continue; }
            Calendar k = Calendar.getInstance();
            k.setTimeInMillis(x.kickoff);
            if (k.get(Calendar.DAY_OF_YEAR) != day.get(Calendar.DAY_OF_YEAR) || k.get(Calendar.YEAR) != day.get(Calendar.YEAR)) continue;
            if (x.state == 2) p.doneToday.add(x);
            else if (x.state == 1 && x.kickoff > p.at) p.laterToday.add(x);
        }
        java.util.Comparator<ScoresTicker.Game> mineFirst = (a, b) -> {
            boolean ma = clubs.contains(a.homeId) || clubs.contains(a.awayId), mb = clubs.contains(b.homeId) || clubs.contains(b.awayId);
            if (ma != mb) return ma ? -1 : 1;
            return Long.compare(a.kickoff, b.kickoff);
        };
        p.liveNow.sort(mineFirst);
        p.doneToday.sort(mineFirst);
        p.laterToday.sort((a, b) -> Long.compare(a.kickoff, b.kickoff));
        while (p.liveNow.size() > 9) p.liveNow.remove(p.liveNow.size() - 1);
        while (p.doneToday.size() > 6) p.doneToday.remove(p.doneToday.size() - 1);
        while (p.laterToday.size() > 6) p.laterToday.remove(p.laterToday.size() - 1);
        today(c, p, null);
        // Every crest named: the scores' own logos, else ESPN's by id.
        List<ScoresTicker.Game> named = new ArrayList<>(p.liveNow);
        named.addAll(p.doneToday);
        named.addAll(p.laterToday);
        for (ScoresTicker.Game x : named) {
            for (String[] e : new String[][]{{x.homeId, x.homeLogo}, {x.awayId, x.awayLogo}}) {
                if (e[0] == null || e[0].isEmpty() || p.crests.containsKey(e[0])) continue;
                Bitmap b = ScoresTicker.cachedLogo(c, e[1] != null ? e[1] : crestUrl(e[0]), crestPx);
                if (b != null) p.crests.put(e[0], b);
            }
        }
        for (String[] m : p.mine) {
            for (String id : new String[]{m[1], m[2]}) {
                if (id == null || id.isEmpty() || p.crests.containsKey(id)) continue;
                Bitmap b = ScoresTicker.cachedLogo(c, crestUrl(id), crestPx);
                if (b != null) p.crests.put(id, b);
            }
        }
        // BBC Sport's football headlines.
        try {
            for (String h : AdBreakLogic.rssTitles(ScoresTicker.get(BBC_FOOTBALL), 12)) {
                if (FanFilter.ok(h)) p.headlines.add(FanFilter.clip(h, 70));
                if (p.headlines.size() >= 6) break;
            }
        } catch (Exception e) {
            android.util.Log.i(TAG, "headlines not read: " + e.getClass().getSimpleName());
        }
        android.util.Log.i(TAG, "general screen: " + p.liveNow.size() + " on now, " + p.doneToday.size() + " results, "
                + p.laterToday.size() + " later, " + p.f1Later.size() + " F1, " + p.mine.size() + " players, " + p.headlines.size() + " headlines");
        return p;
    }

    private static String espn(String url) {
        try {
            return ScoresTicker.get(url);
        } catch (Exception e) {
            return null;
        }
    }

    static String crestUrl(String id) {
        return "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png";
    }

    private static Pack load(Context c, ScoresTicker.Game g) {
        Pack p = new Pack();
        p.g = g;
        p.at = System.currentTimeMillis();
        int crestPx = 96, facePx = 160;
        String base = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug;
        try {
            String sum = espn(base + "/summary?event=" + g.id);
            if (sum != null) p.summary = new JSONObject(sum);
            if (p.summary != null) com.gathin.broadcast.Worn.note(g.id, com.gathin.broadcast.Worn.of(p.summary, g.homeId));
            if (p.summary != null) com.gathin.broadcast.Shirts.note(g.id, p.summary.optJSONArray("rosters"));
        } catch (Exception ignored) {
            // the widgets from it are left out
        }
        p.mark = GoalWatchService.bandMark(c, g, 96);
        // Form and fixtures, this competition only: each club's own list in it.
        for (int side = 0; side < 2; side++) {
            String id = side == 0 ? g.homeId : g.awayId;
            if (id == null) continue;
            try {
                String body = espn(base + "/teams/" + id + "/schedule");
                if (body == null) continue;
                JSONObject sched = new JSONObject(body);
                String form = AdBreakLogic.form(sched, id, g.kickoff, 5);
                List<String[]> next = AdBreakLogic.comingUp(sched, id, g.kickoff + 60_000L, 3);
                if (side == 0) { p.homeForm = form; p.homeNext = next; } else { p.awayForm = form; p.awayNext = next; }
            } catch (Exception ignored) {
                // no form for that club
            }
        }
        if (g.state == 2) {
            try {
                p.full = MoreAlerts.fullTime(g, p.summary);
            } catch (RuntimeException ignored) {
                // no full time's numbers
            }
        }
        if (p.summary != null && g.state == 0 && g.halftime) {
            try {
                p.half = MoreAlerts.firstHalf(g, p.summary);
            } catch (RuntimeException ignored) {
                // no first half's numbers
            }
        }
        if (p.summary != null) {
            p.table = AdBreakLogic.table(p.summary);
            p.h2h = AdBreakLogic.headToHead(p.summary, g.homeId);
            p.referee = AdBreakLogic.referee(p.summary);
            p.absent = AdBreakLogic.absent(p.summary);
            p.leaders = leaders(p.summary, g);
            org.json.JSONObject news = p.summary.optJSONObject("news");
            org.json.JSONArray arts = news == null ? null : news.optJSONArray("articles");
            for (int i = 0; arts != null && i < arts.length() && p.headlines.size() < 3; i++) {
                String h = arts.optJSONObject(i) == null ? "" : arts.optJSONObject(i).optString("headline");
                if (!h.isEmpty() && FanFilter.ok(h)) p.headlines.add(FanFilter.clip(h, 70));
            }
        }
        today(c, p, g);
        // Today's prayer times for the half-time widgets (the mosque's page once a day).
        {
            try {
                Prayer.refresh(c, true);
                p.prayers = Prayer.slots(c, java.time.LocalDate.now());
                p.prayerNext = Prayer.next(c);
                String h = Prayer.hijri(c);
                if (!p.prayers.isEmpty()) p.prayerFoot = (Prayer.source(c) + (h == null ? "" : " · " + h)).toUpperCase(Locale.UK);
            } catch (RuntimeException ignored) {
                // no prayer times widget
            }
        }
        // Every crest a widget names.
        List<String> ids = new ArrayList<>();
        for (AdBreakLogic.Row r : p.table) ids.add(r.id);
        for (List<String[]> l : java.util.Arrays.asList(p.homeNext, p.awayNext)) for (String[] n : l) ids.add(n[0]);
        for (String[] m : p.mine) { ids.add(m[1]); ids.add(m[2]); }
        if (g.homeId != null) ids.add(g.homeId);
        if (g.awayId != null) ids.add(g.awayId);
        for (String id : ids) {
            if (id == null || id.isEmpty() || p.crests.containsKey(id)) continue;
            Bitmap b = ScoresTicker.cachedLogo(c, crestUrl(id), crestPx);
            if (b != null) p.crests.put(id, b);
        }
        for (ScoresTicker.Game x : latest) {
            for (String[] e : new String[][]{{x.homeId, x.homeLogo}, {x.awayId, x.awayLogo}}) {
                if (e[0] == null || e[1] == null || p.crests.containsKey(e[0])) continue;
                Bitmap b = ScoresTicker.cachedLogo(c, e[1], crestPx);
                if (b != null) p.crests.put(e[0], b);
            }
        }
        for (String[] l : p.leaders) {
            if (l == null) continue;
            Bitmap face = PlayerPhotos.find(c, l[0], l[3], facePx);
            if (face != null) p.faces.put(l[0], face);
        }
        // The ground's weather now and to full time.
        String[] w = MatchExtras.weatherParts(c, g);
        if (w != null) {
            p.weatherHead = w[0];
            p.weatherLine = w[1];
        }
        // Their kits today, as the line-up card has them.
        try {
            Kits.Kit[] hk = Kits.of(c, g.homeName != null ? g.homeName : g.home), ak = Kits.of(c, g.awayName != null ? g.awayName : g.away);
            com.gathin.broadcast.Worn.Kit[] named = com.gathin.broadcast.Worn.get(g.id);
            com.gathin.broadcast.Worn.Kit hn = named == null ? null : named[0], an = named == null ? null : named[1];
            p.homeKit = Kits.worn(hk, ak, false, hn);
            p.awayKit = Kits.worn(ak, hk, true, an);
            p.homeKitName = hn != null ? hn.label() : kitName(hk, p.homeKit);
            p.awayKitName = an != null ? an.label() : kitName(ak, p.awayKit);
        } catch (RuntimeException ignored) {
            // no kits
        }
        // The other games kicking off at the same time, with where they are on.
        for (ScoresTicker.Game x : latest) {
            if (x == null || x.id == null || x.id.equals(g.id) || x.state != 1 || Math.abs(x.kickoff - g.kickoff) > 60_000L) continue;
            Object[] where = GoalWatchService.watchOn(x);
            p.alsoNow.add(new Object[]{x, where});
            if (p.alsoNow.size() >= 3) break;
        }
        // The last meeting's goal, to play silently in its widget.
        if (!p.h2h.isEmpty()) {
            String[] last = p.h2h.get(p.h2h.size() - 1);
            for (String[] clip : GoalClips.clips(g.slug, last[3])) {
                if (!GoalClips.goalish(clip[1])) continue;
                p.clip = new String[]{clip[2], clip[1], last[0], last[2]};
                if (clip[3] != null && !clip[3].isEmpty()) p.clipStill = ScoresTicker.cachedLogo(c, clip[3], 360);
                break;
            }
        }
        p.lineups = GoalWatchService.breakLineups(g);
        return p;
    }

    /**
     * Today's F1 sessions still to come (the race calendar the TV already keeps), and the players you follow whose club
     * plays today (on now or still to come) with their photos: for a game's screen (g) and the general one (g null).
     */
    private static void today(Context c, Pack p, ScoresTicker.Game g) {
        int crestPx = 96, facePx = 160;
        // Today's F1 sessions still to come, for later today (the race calendar the TV already keeps).
        try {
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.LocalDate today = java.time.LocalDate.now(zone);
            java.time.Instant now = java.time.Instant.now();
            java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            for (RaceWeekend.Race race : Config.load(c).raceCalendar) {
                for (RaceWeekend.Session s : race.sessions) {
                    if (!s.start.isAfter(now) || !s.start.atZone(zone).toLocalDate().equals(today)) continue;
                    p.f1Later.add(new String[]{s.start.atZone(zone).format(hm), s.title(c.getResources()), race.name});
                }
            }
            if (!p.f1Later.isEmpty()) p.f1Logo = ScoresTicker.cachedLogo(c, F1Results.F1_LOGO, crestPx);
            if (p.f1Logo != null) p.crests.put("f1", p.f1Logo);
        } catch (RuntimeException ignored) {
            // no F1 today, then
        }
        // Players you follow whose club plays today (on now or still to come), with their photos.
        Calendar day = Calendar.getInstance();
        for (Players.Followed f : Players.list(c)) {
            if (f.teamId == null || f.teamId.isEmpty() || p.mine.size() >= 3) continue;
            for (ScoresTicker.Game x : latest) {
                if (x == null || x.state == 2 || !(f.teamId.equals(x.homeId) || f.teamId.equals(x.awayId))) continue;
                Calendar k = Calendar.getInstance();
                k.setTimeInMillis(x.kickoff);
                if (k.get(Calendar.DAY_OF_YEAR) != day.get(Calendar.DAY_OF_YEAR) || k.get(Calendar.YEAR) != day.get(Calendar.YEAR)) continue;
                boolean home = f.teamId.equals(x.homeId);
                String when = x.state == 0 ? "on now" : new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(x.kickoff));
                if (g != null && x.id != null && x.id.equals(g.id)) when = "in this game";
                p.mine.add(new String[]{f.name, f.teamId, home ? x.awayId : x.homeId, when, f.team});
                Bitmap face = PlayerPhotos.find(c, f.name, f.team, facePx);
                if (face != null) p.faces.put(f.name, face);
                break;
            }
        }
    }

    private static String kitName(Kits.Kit[] kits, Kits.Kit wears) {
        if (kits == null || wears == null) return null;
        for (int i = 0; i < kits.length; i++) if (kits[i] == wears) return i == 0 ? "Home kit" : i == 1 ? "Away kit" : "Third kit";
        return null;
    }

    /** Each club's leading scorer in the competition: {name, "9 goals", club id, club name}; null where ESPN names none. */
    private static String[][] leaders(JSONObject summary, ScoresTicker.Game g) {
        String[][] out = new String[2][];
        org.json.JSONArray teams = summary.optJSONArray("leaders");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            JSONObject team = t == null ? null : t.optJSONObject("team");
            if (team == null) continue;
            String id = team.optString("id");
            int side = id.equals(g.homeId) ? 0 : id.equals(g.awayId) ? 1 : -1;
            if (side < 0) continue;
            org.json.JSONArray cats = t.optJSONArray("leaders");
            for (int k = 0; cats != null && k < cats.length(); k++) {
                JSONObject cat = cats.optJSONObject(k);
                if (cat == null || !cat.optString("name").toLowerCase(Locale.ROOT).contains("goal")) continue;
                org.json.JSONArray top = cat.optJSONArray("leaders");
                JSONObject first = top == null ? null : top.optJSONObject(0);
                JSONObject ath = first == null ? null : first.optJSONObject("athlete");
                if (ath == null) continue;
                String v = first.optString("displayValue");
                String what = v.isEmpty() ? cat.optString("displayName") : v + " " + ("1".equals(v) ? "goal" : "goals");
                out[side] = new String[]{ath.optString("displayName"), what, id, team.optString("displayName")};
                break;
            }
        }
        return out;
    }

    // ---- fan posts --------------------------------------------------------------------------------------------------------

    private static volatile List<FanPosts.Post> posts = new ArrayList<>();
    private static long postsAt;
    private static final java.util.Set<String> postsShown = new java.util.HashSet<>();
    private static final Map<String, Bitmap> avatars = new HashMap<>();

    private static void fetchPosts(ScoresTicker.Game g) {
        long now = System.currentTimeMillis();
        if (now - postsAt < POSTS_EVERY_MS) return;
        postsAt = now;
        io.execute(() -> {
            List<FanPosts.Post> got = FanPosts.fetch(g.homeName != null ? g.homeName : g.home, g.awayName != null ? g.awayName : g.away);
            for (FanPosts.Post p : got) {
                if (p.avatar == null || avatars.containsKey(p.avatar)) continue;
                Bitmap b = avatar(p.avatar);
                if (b != null) avatars.put(p.avatar, b);
            }
            android.util.Log.i(TAG, "fan posts: " + got.size());
            posts = got;
        });
    }

    /** A poster's picture, small, held in memory only (never kept on the TV). */
    private static Bitmap avatar(String url) {
        try {
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            con.setConnectTimeout(6_000);
            con.setReadTimeout(8_000);
            con.setRequestProperty("User-Agent", FanPosts.AGENT);
            try (java.io.InputStream in = con.getInputStream()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                Bitmap b = BitmapFactory.decodeStream(in, null, o);
                if (b == null) return null;
                Bitmap small = Bitmap.createScaledBitmap(b, 96, 96, true);
                if (small != b) b.recycle();
                return small;
            } finally {
                con.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ---- the screen ------------------------------------------------------------------------------------------------------

    private static FrameLayout strip, table, postsBox;
    private static ScoresTicker.Game shownFor;
    private static Session shown;
    private static int shownPhase = -1;
    private static TextView countdown, hint, phaseLine, titleLine, rightLine, homeLine, awayLine, scoreLine;
    private static LinearLayout scoreBox;
    private static ImageView homeCrest, awayCrest;
    private static final Card[] cards = new Card[3];
    private static boolean inside, lineupOpened;
    private static int picked;

    private static int px(float v) {
        return Math.round(v * s);
    }

    private static WindowManager.LayoutParams params(int w, int h, int gravity, int x, int y) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.x = x;
        lp.y = y;
        lp.setTitle("AdBreak");
        return lp;
    }

    private static void add(Context c, View v, WindowManager.LayoutParams lp) {
        try {
            c.getSystemService(WindowManager.class).addView(v, lp);
        } catch (RuntimeException e) {
            android.util.Log.w(TAG, "window refused: " + e.getClass().getSimpleName());
        }
    }

    private static void remove(View v) {
        if (v == null) return;
        try {
            v.getContext().getSystemService(WindowManager.class).removeView(v);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }

    /**
     * Up (main thread): the strip with its line by the game's phase (BEFORE KICK-OFF and the countdown; HALF-TIME and
     * the score with both crests; AD BREAK, the score and the minute; FULL TIME and the score; for no game known, AD
     * BREAK and the programme), its three cards, and for a game the table, the line-up card (before kick-off and at
     * half-time only) and the posts.
     */
    static void show(Context ctx, Session ss) {
        Context c = ctx.getApplicationContext();
        if (ss == null) return;
        if (isShowing() && shown != null && (ss.g == null ? shown.g == null : shown.g != null && shown.g.id.equals(ss.g.id))) return;
        hideNow();
        app = c;
        shown = ss;
        shownFor = ss.g;
        shownPhase = -1;
        ScoresTicker.Game g = ss.g;
        s = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        AlertLog.note(c, "ad-break screen up: " + (g == null ? "no game known, on " + ss.channel : AlertLog.game(g)) + " (" + ss.source + ")");
        // The strip along the foot.
        int sw = c.getResources().getDisplayMetrics().widthPixels, stripH = px(294);
        strip = new FrameLayout(c);
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF5070912, 0xEB0B0F24, 0xEB0B0F24, 0x000B0F24});
        strip.setBackground(shade);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.BOTTOM);
        col.setPadding(px(60), 0, px(60), px(36));
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        phaseLine = text(c, "", 19.5f, 0xFFFF8A8A, Typeface.create("sans-serif", Typeface.BOLD));
        phaseLine.setLetterSpacing(0.1f);
        head.addView(phaseLine);
        titleLine = text(c, "", 24, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(21);
        head.addView(titleLine, tl);
        // The score with both crests (on, half-time, full time).
        scoreBox = new LinearLayout(c);
        scoreBox.setGravity(Gravity.CENTER_VERTICAL);
        homeCrest = new ImageView(c);
        homeCrest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        awayCrest = new ImageView(c);
        awayCrest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        homeLine = text(c, "", 24, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        awayLine = text(c, "", 24, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        scoreLine = text(c, "", 30, Color.WHITE, ScoreCard.outfit(c, 700));
        scoreLine.setPadding(px(15), 0, px(15), px(2));
        scoreLine.setBackground(fill(0x26FFFFFF, px(8)));
        View[] parts = {homeCrest, homeLine, scoreLine, awayLine, awayCrest};
        for (int i = 0; i < parts.length; i++) {
            boolean crest = parts[i] instanceof ImageView;
            LinearLayout.LayoutParams l = crest ? new LinearLayout.LayoutParams(px(39), px(39)) : new LinearLayout.LayoutParams(-2, -2);
            if (i > 0) l.leftMargin = px(12);
            scoreBox.addView(parts[i], l);
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = px(21);
        head.addView(scoreBox, sl);
        hint = text(c, InputKeyService.running ? "Down: widgets" : "", 19.5f, 0xFFA9B1CC, Typeface.DEFAULT);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, -2, 1f);
        hl.leftMargin = px(21);
        head.addView(hint, hl);
        rightLine = text(c, "", 19.5f, 0xFFA9B1CC, Typeface.DEFAULT);
        head.addView(rightLine);
        countdown = text(c, "", 45, Color.WHITE, ScoreCard.outfit(c, 700));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = px(12);
        head.addView(countdown, cl);
        col.addView(head, new LinearLayout.LayoutParams(-1, px(60)));
        LinearLayout row = new LinearLayout(c);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, px(168));
        rl.topMargin = px(18);
        col.addView(row, rl);
        for (int i = 0; i < 3; i++) {
            cards[i] = new Card(c, i);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -1, 1f);
            if (i > 0) l.leftMargin = px(24);
            row.addView(cards[i].root, l);
        }
        strip.addView(col, new FrameLayout.LayoutParams(-1, -1));
        add(c, strip, params(sw, stripH, Gravity.BOTTOM | Gravity.START, 0, 0));
        strip.setTranslationY(stripH);
        strip.animate().translationY(0).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
        inside = false;
        picked = 0;
        tick.run();
        if (g == null) {
            // The general screen: no posts, no table, no line-ups.
            Pack p = generalPack;
            if (p != null) fill(c, p);
            prefetchGeneral();
            return;
        }
        // The posts' column, above the strip on the left.
        postsBox = new FrameLayout(c);
        add(c, postsBox, params(px(705), POSTS_H_PX(), Gravity.BOTTOM | Gravity.START, px(60), stripH + px(27)));
        Pack p = pack;
        if (p != null && p.g != null && p.g.id.equals(g.id)) fill(c, p);
        else prefetch(g);
        main.postDelayed(postRun, 3_000L);
    }

    /** The widgets filled in once the data is in. */
    private static void fill(Context c, Pack p) {
        if (!isShowing() || shown == null) return;
        if (p.g == null) {
            if (shown.g != null) return;
            List<List<Widget>> lists = Widgets.general(c, p, s);
            for (int i = 0; i < 3; i++) cards[i].set(lists.get(i));
            return;
        }
        if (shown.g == null || !shown.g.id.equals(p.g.id)) return;
        List<List<Widget>> lists = Widgets.build(c, p, s);
        // At half-time (as it shows on screen) and at full time the last card gets the quiz, fun facts and prayer times
        // in among its own: every widget there is.
        filledHalf = funNow(c, p);
        if (filledHalf) lists.set(2, HalfTimeFun.mix(HalfTimeFun.widgets(c, p, s), lists.get(2)));
        for (int i = 0; i < 3; i++) cards[i].set(lists.get(i));
        hintNow();
        Bitmap hc = p.g.homeId == null ? null : p.crests.get(p.g.homeId), ac = p.g.awayId == null ? null : p.crests.get(p.g.awayId);
        if (homeCrest != null && hc != null) homeCrest.setImageBitmap(hc);
        if (awayCrest != null && ac != null) awayCrest.setImageBitmap(ac);
        // The table's card above the line-up card on the right, its size.
        remove(table);
        table = null;
        View t = Widgets.tableCard(c, p, s);
        if (t != null) {
            table = new FrameLayout(c);
            table.addView(t, new FrameLayout.LayoutParams(px(616), px(204)));
            add(c, table, params(px(616), px(204), Gravity.TOP | Gravity.END, px(60), px(42)));
            table.setAlpha(0f);
            table.setTranslationX(px(40));
            table.animate().alpha(1f).translationX(0).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        openLineups(c, p);
    }

    /** Whether the cards were last filled for half-time (the half-time widgets in the last card). */
    private static boolean filledHalf;

    /** Whether the half-time widgets join the rest now: half-time as it shows on screen, or full time (or the test's). */
    private static boolean funNow(Context c, Pack p) {
        ScoresTicker.Game g = fresh(p.g);
        return forcedHalf || g != null && (g.state == 2 || halfTimeShows(c, g, System.currentTimeMillis()));
    }

    /** The last card again when half-time starts or ends on screen (or full time comes) while the screen is up. */
    private static void refillHalf(Context c, Pack p) {
        boolean ht = funNow(c, p);
        if (ht == filledHalf || cards[2] == null) return;
        filledHalf = ht;
        List<Widget> last = Widgets.build(c, p, s).get(2);
        cards[2].set(ht ? HalfTimeFun.mix(HalfTimeFun.widgets(c, p, s), last) : last);
        hintNow();
    }

    /** The line-up card, before kick-off and at half-time only. */
    private static void openLineups(Context c, Pack p) {
        if (lineupOpened || p == null || p.lineups == null || !AdBreakLogic.lineups(shownPhase)) return;
        float k = s * 0.88f;
        int w = Math.round((700 + 20) * k);
        int x = c.getResources().getDisplayMetrics().widthPixels - px(60) - w + Math.round(10 * k);
        LineupCard.showForBreak(c, p.lineups, x, px(264) - Math.round(10 * k));
        lineupOpened = true;
    }

    /** The game as the scores have it now. */
    private static ScoresTicker.Game fresh(ScoresTicker.Game g) {
        if (g == null) return null;
        for (ScoresTicker.Game x : latest) if (x != null && g.id.equals(x.id)) return x;
        return g;
    }

    /** The strip's line, each second: the phase, then the countdown, or the score and the minute. */
    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!isShowing() || shown == null || phaseLine == null) return;
            long now = System.currentTimeMillis();
            ScoresTicker.Game g = fresh(shown.g);
            int ph = g == null ? AdBreakLogic.GENERAL : AdBreakLogic.phase(true, g.state, g.halftime, g.kickoff, now);
            phaseLine.setText(AdBreakLogic.label(ph));
            if (ph == AdBreakLogic.BEFORE) {
                titleLine.setVisibility(View.VISIBLE);
                scoreBox.setVisibility(View.GONE);
                titleLine.setText(name(g, true) + " v " + name(g, false) + " · "
                        + new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(g.kickoff)));
                rightLine.setText("Kick-off in");
                countdown.setText(AdBreakLogic.countdown(g.kickoff - now));
            } else if (ph == AdBreakLogic.GENERAL) {
                titleLine.setVisibility(View.VISIBLE);
                scoreBox.setVisibility(View.GONE);
                titleLine.setText(shown.title != null && !shown.title.isEmpty() ? shown.title : shown.channel);
                rightLine.setText("");
                countdown.setText("");
            } else {
                titleLine.setVisibility(View.GONE);
                scoreBox.setVisibility(View.VISIBLE);
                homeLine.setText(name(g, true));
                awayLine.setText(name(g, false));
                scoreLine.setText((g.homeScore == null ? "0" : g.homeScore) + " – " + (g.awayScore == null ? "0" : g.awayScore));
                rightLine.setText("");
                String clock = ph == AdBreakLogic.LIVE ? ScoresTicker.liveClock(g) : "";
                countdown.setText(clock == null ? "" : clock);
            }
            if (g != null && pack != null && pack.g != null && pack.g.id.equals(g.id) && app != null) refillHalf(app, pack);
            if (ph != shownPhase) {
                int was = shownPhase;
                shownPhase = ph;
                if (was >= 0) android.util.Log.i(TAG, "phase now " + AdBreakLogic.label(ph));
                if (!AdBreakLogic.lineups(ph) && lineupOpened) {
                    LineupCard.close(true);
                    lineupOpened = false;
                } else if (g != null && pack != null && pack.g != null && pack.g.id.equals(g.id)) {
                    openLineups(app, pack);
                }
            }
            main.postDelayed(this, 1_000L - System.currentTimeMillis() % 1000);
        }
    };

    /** Away (main thread). */
    static void hide(String why) {
        if (!isShowing()) return;
        if (app != null) AlertLog.note(app, "ad-break screen away: " + why);
        hideNow();
    }

    private static void hideNow() {
        main.removeCallbacks(tick);
        main.removeCallbacks(postRun);
        for (int i = 0; i < 3; i++) if (cards[i] != null) cards[i].stop();
        if (strip != null) {
            FrameLayout st = strip;
            st.animate().translationY(st.getHeight()).alpha(0f).setDuration(300).withEndAction(() -> remove(st)).start();
        }
        if (table != null) {
            FrameLayout t = table;
            t.animate().alpha(0f).translationX(px(40)).setDuration(260).withEndAction(() -> remove(t)).start();
        }
        if (postsBox != null) {
            FrameLayout pb = postsBox;
            pb.animate().alpha(0f).setDuration(260).withEndAction(() -> remove(pb)).start();
        }
        if (lineupOpened) LineupCard.close(true);
        lineupOpened = false;
        strip = null;
        table = null;
        postsBox = null;
        shownFor = null;
        shown = null;
        shownPhase = -1;
        phaseLine = null;
        inside = false;
    }

    // ---- the posts popping up ------------------------------------------------------------------------------------------

    private static final Runnable postRun = new Runnable() {
        @Override public void run() {
            if (!isShowing() || postsBox == null) return;
            ScoresTicker.Game g = shownFor;
            if (g != null) fetchPosts(g);
            FanPosts.Post next = null;
            for (FanPosts.Post p : posts) {
                if (!postsShown.contains(p.key)) {
                    next = p;
                    break;
                }
            }
            if (next != null) {
                postsShown.add(next.key);
                pop(postsBox.getContext(), next);
            }
            main.postDelayed(this, POST_GAP_MS);
        }
    };

    private static void pop(Context c, FanPosts.Post p) {
        LinearLayout card = new LinearLayout(c);
        card.setGravity(Gravity.TOP);
        card.setPadding(px(24), px(21), px(24), px(21));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE00B0F24);
        bg.setCornerRadius(px(14));
        bg.setStroke(Math.max(1, px(1.5f)), 0x24FFFFFF);
        card.setBackground(bg);
        card.addView(Widgets.ringed(c, avatars.get(p.avatar), initials(p.name), 0xFF2B3170, px(60)), new LinearLayout.LayoutParams(px(60), px(60)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = text(c, p.name, 21, Color.WHITE, Typeface.create("sans-serif", Typeface.BOLD));
        top.addView(n, new LinearLayout.LayoutParams(-2, -2));
        TextView h = text(c, "  " + p.handle + " · " + FanPosts.ago(p.at, System.currentTimeMillis()), 18, 0xFFA9B1CC, Typeface.DEFAULT);
        top.addView(h, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView src = text(c, p.source, 16.5f, 0xFFC9CFE3, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        src.setLetterSpacing(0.06f);
        src.setPadding(px(9), px(2), px(9), px(3));
        src.setBackground(fill(0x29FFFFFF, px(6)));
        top.addView(src);
        words.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView body = text(c, p.text, 22, 0xFFEEF1FA, Typeface.DEFAULT);
        body.setSingleLine(false);
        body.setMaxLines(3);
        body.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = px(6);
        words.addView(body, bl);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -2, 1f);
        wl.leftMargin = px(18);
        card.addView(words, wl);
        // Newest at the foot: the others move up and fade; past three, the oldest goes.
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        postsBox.addView(card, fl);
        card.measure(View.MeasureSpec.makeMeasureSpec(px(705), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int hgt = card.getMeasuredHeight() + px(15);
        card.setTag(hgt);
        int count = postsBox.getChildCount();
        // From the newest up: each older post moves up by the new one's height; one that would no longer fit in the
        // column (three long posts can be taller than it) goes now, with any older, rather than being cut off at the top.
        int used = hgt - px(15);
        boolean full = false;
        for (int i = count - 2; i >= 0; i--) {
            View v = postsBox.getChildAt(i);
            int age = count - 1 - i;
            used += v.getTag() instanceof Integer ? (Integer) v.getTag() : hgt;
            if (used > POSTS_H_PX()) full = true;
            if (age >= POSTS_SHOWN || full) {
                v.animate().alpha(0f).translationYBy(-hgt).setDuration(300).withEndAction(() -> postsBox_remove(v)).start();
            } else {
                v.animate().translationYBy(-hgt).alpha(age == 1 ? 0.8f : 0.6f).setDuration(380).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
        }
        card.setAlpha(0f);
        card.setTranslationX(-px(40));
        card.animate().alpha(1f).translationX(0).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    /** The posts' column's height: room for three short posts, or two long ones. */
    private static int POSTS_H_PX() {
        return px(430);
    }

    private static void postsBox_remove(View v) {
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
    }

    static String initials(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] w = name.replaceAll("[^A-Za-z ]", " ").trim().split("\\s+");
        StringBuilder b = new StringBuilder();
        for (String x : w) if (!x.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(x.charAt(0)));
        return b.toString();
    }

    // ---- the remote ----------------------------------------------------------------------------------------------------

    /** Keys from the key helper: Down goes in; once in, the arrows, OK and Back are the cards'. */
    static boolean key(KeyEvent e) {
        if (!isShowing()) return false;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (!inside) {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (down && e.getRepeatCount() == 0) {
                inside = true;
                picked = firstCard();
                ring();
                hintNow();
            }
            return true;
        }
        // The widget on the picked card first (the quiz: OK plays, the arrows pick, Back stops).
        Widget w = cards[picked] == null ? null : cards[picked].current();
        if (w != null && w.keys != null && w.keys.key(code, down)) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    for (int i = picked + step; i >= 0 && i < 3; i += step) {
                        if (cards[i] != null && cards[i].has()) {
                            picked = i;
                            break;
                        }
                    }
                    ring();
                    hintNow();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && cards[picked] != null) {
                    cards[picked].turn(code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                    hintNow();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!down && cards[picked] != null) cards[picked].ok();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    inside = false;
                    ring();
                    hintNow();
                    if (forced) {
                        // The test: Back puts it away.
                        forced = false;
                        forcedHalf = false;
                        trigger.set(false);
                        hide("Back, on the test");
                    }
                }
                return true;
            default:
                return false;
        }
    }

    /** The strip's hint as things stand: the way in, the cards' keys, or the picked widget's own (the quiz). */
    static void hintNow() {
        if (hint == null) return;
        if (!inside) {
            hint.setText(InputKeyService.running ? "Down: widgets" : "");
            return;
        }
        Widget w = cards[picked] == null ? null : cards[picked].current();
        String own = w != null && w.keys != null ? w.keys.hint() : null;
        hint.setText(own != null ? own : "Left, Right: card · Up, Down: scroll · Back: out");
    }

    /** Whether the picked card, held by the remote, shows a widget with these keys now. */
    static boolean pickedShows(Keys k) {
        if (!isShowing() || !inside || k == null || cards[picked] == null) return false;
        Widget w = cards[picked].current();
        return w != null && w.keys == k;
    }

    private static int firstCard() {
        for (int i = 0; i < 3; i++) if (cards[i] != null && cards[i].has()) return i;
        return 0;
    }

    private static void ring() {
        for (int i = 0; i < 3; i++) if (cards[i] != null) cards[i].ring(inside && i == picked);
    }

    // ---- one card of the three -----------------------------------------------------------------------------------------

    /** A widget: its band's mark and name, how its lines are made, how long it stays, and what OK does on it. */
    static final class Widget {
        final Bitmap mark;
        final String caption;
        final Widgets.Lines lines;
        final Runnable ok;
        final String clipUrl;
        final Bitmap clipStill;
        /** The remote's keys while it shows on the picked card, before the cards' own (the quiz); null none. */
        final Keys keys;

        Widget(Bitmap mark, String caption, Widgets.Lines lines, Runnable ok, String clipUrl, Bitmap clipStill) {
            this(mark, caption, lines, ok, clipUrl, clipStill, null);
        }

        Widget(Bitmap mark, String caption, Widgets.Lines lines, Runnable ok, String clipUrl, Bitmap clipStill, Keys keys) {
            this.mark = mark;
            this.caption = caption;
            this.lines = lines;
            this.ok = ok;
            this.clipUrl = clipUrl;
            this.clipStill = clipStill;
            this.keys = keys;
        }
    }

    /** A widget that takes the remote while it shows on the picked card (HalfTimeFun's quiz). */
    interface Keys {
        /** A key (down or up); true when it was the widget's, so the cards don't see it. */
        boolean key(int code, boolean down);

        /** The strip's hint while it shows on the picked card. */
        String hint();
    }

    private static final class Card {
        final FrameLayout root, band, stage;
        final View wipe;
        final int index;
        List<Widget> list = new ArrayList<>();
        int at;
        MediaPlayer player;
        private final Runnable next = () -> turn(1);
        private boolean ringed;

        Card(Context c, int index) {
            this.index = index;
            root = new FrameLayout(c);
            float r = px(12);
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
            });
            root.setClipToOutline(true);
            root.setBackgroundColor(0xFF0D1360);
            band = new FrameLayout(c);
            band.setBackground(SkyStyle.band(SkyStyle.NAVY, SkyStyle.NAVY_DEEP, px(18), px(3)));
            root.addView(band, new FrameLayout.LayoutParams(px(90), -1));
            stage = new FrameLayout(c);
            stage.setBackground(new SkyStyle.Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(75), px(32)));
            stage.setClipChildren(true);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -1);
            sl.leftMargin = px(90);
            root.addView(stage, sl);
            wipe = new WipeView(c);
            wipe.setVisibility(View.GONE);
            stage.addView(wipe, new FrameLayout.LayoutParams(-1, -1));
            root.setVisibility(View.INVISIBLE);
        }

        boolean has() {
            return !list.isEmpty();
        }

        Widget current() {
            return at >= 0 && at < list.size() ? list.get(at) : null;
        }

        void set(List<Widget> widgets) {
            list = widgets == null ? new ArrayList<>() : widgets;
            root.setVisibility(list.isEmpty() ? View.INVISIBLE : View.VISIBLE);
            at = 0;
            if (list.isEmpty()) return;
            put(list.get(0));
            main.removeCallbacks(next);
            main.postDelayed(next, AdBreakLogic.firstTurn(index));
        }

        void ring(boolean on) {
            ringed = on;
            if (on) {
                GradientDrawable g = new GradientDrawable();
                g.setColor(0);
                g.setCornerRadius(px(12));
                g.setStroke(px(4.5f), Color.WHITE);
                root.setForeground(g);
                root.setElevation(px(18));
                root.animate().scaleX(1.03f).scaleY(1.03f).setDuration(160).start();
            } else {
                root.setForeground(null);
                root.setElevation(0);
                root.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
            }
        }

        /** The next (or the one before) behind the wipe; the picked card waits for the remote. */
        void turn(int step) {
            main.removeCallbacks(next);
            if (list.isEmpty() || !isShowing()) return;
            int to = AdBreakLogic.next(at, step, list.size());
            boolean held = inside && cards[picked] == this;
            if (list.size() > 1) {
                at = to;
                Widget w = list.get(to);
                int sw = stage.getWidth(), sh = stage.getHeight();
                float slant = sh * 0.42f;
                ((WipeView) wipe).span = sw + 2 * slant;
                ViewGroup.LayoutParams wl = wipe.getLayoutParams();
                if (wl.width != Math.round(sw + 2 * slant)) {
                    wl.width = Math.round(sw + 2 * slant);
                    wipe.setLayoutParams(wl);
                }
                wipe.setVisibility(View.VISIBLE);
                wipe.bringToFront();
                wipe.setTranslationX(-(sw + 2 * slant));
                boolean[] swapped = {false};
                wipe.animate().translationX(sw).setDuration(900).setInterpolator(new AccelerateDecelerateInterpolator())
                        .setUpdateListener(a -> {
                            if (swapped[0] || a.getAnimatedFraction() < 0.5f) return;
                            swapped[0] = true;
                            put(w);
                            wipe.bringToFront();
                        })
                        .withEndAction(() -> wipe.setVisibility(View.GONE)).start();
            }
            long stay = list.get(at).clipUrl != null ? 30_000L : AdBreakLogic.PERIOD_MS;
            if (!held) main.postDelayed(next, stay);
        }

        void ok() {
            if (list.isEmpty()) return;
            Widget w = list.get(at);
            if (w.ok != null) w.ok.run();
        }

        /** A widget in: its band, its lines coming in from the right one after another. */
        private void put(Widget w) {
            Context c = root.getContext();
            stopClip();
            band.removeAllViews();
            LinearLayout b = new LinearLayout(c);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            b.setPadding(0, px(18), 0, px(12));
            if (w.mark != null) {
                ImageView m = new ImageView(c);
                m.setImageBitmap(w.mark);
                m.setScaleType(ImageView.ScaleType.FIT_CENTER);
                b.addView(m, new LinearLayout.LayoutParams(px(33), px(33)));
            } else {
                b.addView(new HouseMark(c), new LinearLayout.LayoutParams(px(36), px(36)));
            }
            TextView cap = text(c, w.caption, 12.75f, 0xE6FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            cap.setSingleLine(false);
            cap.setMaxLines(3);
            cap.setGravity(Gravity.CENTER);
            cap.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(84), -2);
            cl.topMargin = px(12);
            b.addView(cap, cl);
            View gap = new View(c);
            b.addView(gap, new LinearLayout.LayoutParams(1, 0, 1f));
            TextView pos = text(c, (at + 1) + " / " + list.size(), 13.5f, 0xB3FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            pos.setGravity(Gravity.CENTER);
            b.addView(pos, new LinearLayout.LayoutParams(-1, -2));
            band.addView(b, new FrameLayout.LayoutParams(-1, -1));
            for (int i = stage.getChildCount() - 1; i >= 0; i--) if (stage.getChildAt(i) != wipe) stage.removeViewAt(i);
            if (w.clipUrl != null) {
                stage.addView(clipView(c, w), 0, new FrameLayout.LayoutParams(-1, -1));
                return;
            }
            LinearLayout lines = new LinearLayout(c);
            lines.setOrientation(LinearLayout.VERTICAL);
            lines.setGravity(Gravity.CENTER_VERTICAL);
            lines.setPadding(px(18), px(12), px(18), px(12));
            List<View> made = w.lines.make(c);
            for (int i = 0; i < made.size(); i++) {
                View v = made.get(i);
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) l.topMargin = px(9);
                lines.addView(v, l);
                v.setAlpha(0f);
                v.setTranslationX(px(39));
                v.animate().alpha(1f).translationX(0).setStartDelay(120L + 70L * i).setDuration(420)
                        .setInterpolator(new DecelerateInterpolator(2.2f)).start();
            }
            stage.addView(lines, 0, new FrameLayout.LayoutParams(-1, -1));
        }

        /** The last meeting's goal at the card's full height, playing with no sound (no hold on the sound either). */
        private View clipView(Context c, Widget w) {
            LinearLayout row = new LinearLayout(c);
            FrameLayout box = new FrameLayout(c);
            box.setBackgroundColor(Color.BLACK);
            int ch = px(168), cw = Math.round(ch * 16f / 9f);
            ImageView still = new ImageView(c);
            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (w.clipStill != null) still.setImageBitmap(w.clipStill);
            box.addView(still, new FrameLayout.LayoutParams(-1, -1));
            TextureView video = new TextureView(c);
            box.addView(video, new FrameLayout.LayoutParams(-1, -1));
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int sw2, int sh2) {
                    try {
                        MediaPlayer mp = new MediaPlayer();
                        player = mp;
                        mp.setDataSource(w.clipUrl);
                        mp.setSurface(new android.view.Surface(st));
                        mp.setVolume(0f, 0f);
                        mp.setOnPreparedListener(m -> {
                            if (player != m) return;
                            m.setVolume(0f, 0f);
                            m.start();
                        });
                        // The decoder cannot start (two videos at once): its still stays.
                        mp.setOnErrorListener((m, what, extra) -> {
                            video.setVisibility(View.GONE);
                            return true;
                        });
                        mp.prepareAsync();
                    } catch (Exception e) {
                        video.setVisibility(View.GONE);
                    }
                }
                @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int a, int b2) { }
                @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { stopClip(); return true; }
                @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) { }
            });
            row.addView(box, new LinearLayout.LayoutParams(cw, ch));
            LinearLayout side = new LinearLayout(c);
            side.setOrientation(LinearLayout.VERTICAL);
            side.setGravity(Gravity.CENTER_VERTICAL);
            side.setPadding(px(15), 0, px(12), 0);
            for (View v : w.lines.make(c)) {
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
                l.bottomMargin = px(8);
                side.addView(v, l);
            }
            row.addView(side, new LinearLayout.LayoutParams(0, -1, 1f));
            return row;
        }

        void stopClip() {
            MediaPlayer mp = player;
            player = null;
            if (mp != null) {
                try { mp.release(); } catch (RuntimeException ignored) { }
            }
        }

        void stop() {
            main.removeCallbacks(next);
            stopClip();
        }
    }

    /** The slanted navy panel, edged light blue and white, crossing a card as its widget changes (the line-up card's). */
    private static final class WipeView extends View {
        float span;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        WipeView(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            int h = getHeight();
            float slant = h * 0.42f, w = span > 0 ? span : getWidth(), body = w - slant, edge = px(10);
            panel(cv, 0, body, h, slant, 0xFF151A63);
            panel(cv, body - edge, body, h, slant, 0xFF3D4BD6);
            panel(cv, body - edge * 0.35f, body, h, slant, Color.WHITE);
            panel(cv, 0, edge, h, slant, 0xFF3D4BD6);
            panel(cv, 0, edge * 0.35f, h, slant, 0xE6FFFFFF);
        }

        private void panel(Canvas cv, float from, float to, int h, float slant, int colour) {
            Path path = new Path();
            path.moveTo(from, h);
            path.lineTo(to, h);
            path.lineTo(to + slant, 0);
            path.lineTo(from + slant, 0);
            path.close();
            p.setColor(colour);
            cv.drawPath(path, p);
        }

    }

    /** The house with its play mark (Homefield's own) for the widgets about this house and its day. */
    static final class HouseMark extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        HouseMark(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), u = w / 24f;
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * u);
            p.setStrokeJoin(Paint.Join.ROUND);
            Path h = new Path();
            h.moveTo(3 * u, 11 * u);
            h.lineTo(12 * u, 3 * u);
            h.lineTo(21 * u, 11 * u);
            h.lineTo(21 * u, 21 * u);
            h.lineTo(3 * u, 21 * u);
            h.close();
            cv.drawPath(h, p);
            p.setStyle(Paint.Style.FILL);
            Path t = new Path();
            t.moveTo(10 * u, 11 * u);
            t.lineTo(15.5f * u, 14.5f * u);
            t.lineTo(10 * u, 18 * u);
            t.close();
            cv.drawPath(t, p);
        }
    }

    // ---- small things ------------------------------------------------------------------------------------------------------

    static String name(ScoresTicker.Game g, boolean home) {
        String n = home ? (g.homeName != null ? g.homeName : g.home) : (g.awayName != null ? g.awayName : g.away);
        return n == null ? "" : n;
    }

    static TextView text(Context c, String t, float size1920, int colour, Typeface tf) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(colour);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size1920 * s);
        v.setTypeface(tf);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable fill(int colour, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radius);
        return g;
    }

    static List<ScoresTicker.Game> latestGames() {
        return latest;
    }

    static float scale() {
        return s;
    }

    static Handler main() {
        return main;
    }
}
