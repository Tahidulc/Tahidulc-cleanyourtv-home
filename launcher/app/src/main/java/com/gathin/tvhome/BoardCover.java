package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The phone's scoreboard and its cover over the channel's logo (Homefield's player), over TiviMate on the TV. Never on
 * by itself: it is switched on by hand for one game (the Control Centre's Scoreboard tile, for the game on now or the
 * next one put on; or the Scoreboard card, for the game chosen there), shows while that game is on the screen, and
 * switches itself off ten minutes after the game's full time (four hours at most). The next game needs it switching
 * on again.
 *
 * <p>It shows the game as the picture does, not as the scores are: each reading of the scores' poll is kept with when it
 * was seen, and the board shows the one the channel has reached, that channel's wait (InStep) behind, its clock counting
 * on from there in minutes and seconds, the minutes added beside it in added time. The TV cannot read the channel's own
 * clock off the picture as the phone does, so at each kick-off (and the second half's) a card asks for OK as it happens
 * on the screen (InStepCard): from that moment the board's clock is counted exactly as the channel's; Move and resize's
 * Timing puts it a second or five later or sooner. It comes in and goes as a broadcaster's does (BoardBug); a goal on
 * the screen sweeps the scorers' colour across it with their crest and GOAL, the new score popping up after. Up twenty
 * minutes before kick-off with the kick-off's time; at half time a little longer for its alert and the half's stats,
 * then it rests (gone); full time shows for two minutes.
 *
 * <p>Out beside it, in line with it and in its look: the game's stats and facts a few times a game (BoardStats: the
 * chances, form and table before kick-off, a few of each half's, the player of the match), and while it is up the
 * football alerts (goals, cards, VAR, half and full time, the kick-off and both line-ups) slide out of it sideways
 * (BoardDrop) in place of the usual cards; each can be switched off on the Scoreboard card.
 *
 * <p>Where it sits: the TV cannot see the picture under TiviMate (its video is on a plane of its own), so it takes the
 * standard places (the scoreboard top left, the logo's cover top right) until Move and resize sets a channel's own
 * (BoardPlace): the arrows move, OK steps through moving and sizing each, Back keeps them.
 *
 * <p>Kind to the screen (an OLED's burn-in): dark grounds and soft white words only; once a minute both move a pixel or
 * two round a slow circle, never far enough to uncover what they cover (each has an edge of its ground for it); after
 * five minutes without a goal they dim (solid still, darker), bright again at the next; and they rest at half time.
 * They are two small windows over the app, which never take its keys or its focus but while Move and resize is open.
 */
final class BoardCover {
    private BoardCover() { }

    /** How long it stays on at most, how long after full time on the screen, and how long full time shows. */
    /**
     * The longest it stays on, how long after full time it switches off, and how long full time stays on the board
     * (its alert and its stats inside that; gone by FT_GONE_MS whatever is out).
     */
    static final long MAX_MS = 4 * 3_600_000L, AFTER_FT_MS = 10 * 60_000L, FT_SHOWN_MS = 50_000L, FT_GONE_MS = 60_000L;
    /** Burn-in: a step round the circle each minute; dimmer after five minutes without a change. */
    static final long STEP_MS = 60_000L, DIM_AFTER_MS = 5 * 60_000L;
    /** How dim: the colours at this share of their brightness, never see-through. */
    static final float DIM = 0.7f;
    /** The readings kept for each game: one a poll (20 s apart while live), more than the longest wait. */
    static final int KEEP = 16;
    /** The apps it shows over, each playing the channel full screen: TiviMate, Kleva TV, Homefield TV. */
    static final String[] PLAYERS = {"ar.tvplayer.tv", "com.plaintextdev.klevaTv", "com.gathin.homefieldtv"};
    private static final String FILE = "board_cover";

    private static Handler main;
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static Context app;

    /** Made when first wanted, so the sums below load in the tests. */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    // ---- on and off ------------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Switched on, and not run out. */
    static boolean active(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("on", false) && System.currentTimeMillis() < p.getLong("until", 0);
    }

    /** The game it is on for: its id; "" while it waits for the next game put on; null when it is off. */
    static String game(Context c) {
        return active(c) ? prefs(c).getString("game", "") : null;
    }

    static String label(Context c) {
        return prefs(c).getString("label", "");
    }

    /** On for this game (null: the next game put on, from Home or in Match mode, within four hours). */
    static void start(Context c, ScoresTicker.Game g) {
        long now = System.currentTimeMillis();
        long from = g == null ? now : Math.max(now, g.kickoff);
        prefs(c).edit().putBoolean("on", true).putString("game", g == null || g.id == null ? "" : g.id)
                .putString("label", g == null ? "" : MatchMode.label(g))
                .putLong("until", Math.min(from + MAX_MS, now + MAX_MS + 3_600_000L)).remove("ft").apply();
        AlertLog.note(c, "scoreboard on: " + (g == null ? "for the next game put on" : AlertLog.game(g)));
        resume(c);
    }

    static void stop(Context c, String why) {
        boolean was = prefs(c).getBoolean("on", false);
        prefs(c).edit().putBoolean("on", false).remove("ft").apply();
        if (was) AlertLog.note(c, "scoreboard off: " + why);
        if (!adjusting && testUntil <= System.currentTimeMillis()) hideAll();
    }

    /**
     * The Control Centre's tile: on for the game on now (Match mode's, else the one on the channel last tuned, looked
     * for at once), else for the next game put on; or off again. Main thread.
     */
    static void toggle(Context c) {
        if (active(c)) {
            stop(c, "switched off");
            return;
        }
        ScoresTicker.Game mm = matchModeGame(c);
        start(c, mm);
        if (mm == null) findGameOnNow(c);
    }

    /** Match mode's game, while it is on and the game not over (main thread). */
    private static ScoresTicker.Game matchModeGame(Context c) {
        String mm = MatchMode.game(c);
        ScoresTicker.Game g = mm == null ? null : GoalWatchService.latestGame(mm);
        return g != null && g.state != 2 ? g : null;
    }

    private static boolean finding;

    /**
     * While it waits for a game: the one on the channel tuned, looked for off the main thread (that can read the TV
     * guide), and taken up when there is one.
     */
    private static void findGameOnNow(Context c) {
        if (finding) return;
        List<ScoresTicker.Game> games = GoalWatchService.latestGames();
        if (games.isEmpty()) return;
        finding = true;
        Context a = c.getApplicationContext();
        io.execute(() -> {
            ScoresTicker.Game on = null;
            try {
                on = GoalWatchService.watchedAmong(games);
            } catch (RuntimeException ignored) {
                // not found this time: the next poll looks again
            }
            ScoresTicker.Game found = on;
            main().post(() -> {
                finding = false;
                String id = game(a);
                if (found == null || found.id == null || id == null || !id.isEmpty()) return;
                take(a, found);
            });
        });
    }

    /** The game waited for, found: the board is on for it from now, for four hours from its kick-off at most. */
    private static void take(Context c, ScoresTicker.Game g) {
        long now = System.currentTimeMillis();
        prefs(c).edit().putString("game", g.id).putString("label", MatchMode.label(g))
                .putLong("until", Math.min(Math.max(now, g.kickoff) + MAX_MS, now + MAX_MS + 3_600_000L)).apply();
        AlertLog.note(c, "scoreboard on for the game put on: " + AlertLog.game(g));
        resume(c);
    }

    /** The tile's line: "Off", "On · Everton v Spurs", "On · the game you put on". */
    static String words(Context c) {
        String id = game(c);
        if (id == null) return "Off · OK for the game on";
        if (id.isEmpty()) return "On · the game you put on";
        String l = label(c);
        return "On · " + (l.isEmpty() ? "this game" : l);
    }

    // ---- its looks -------------------------------------------------------------------------------------------------

    static boolean logoOn(Context c) {
        return prefs(c).getBoolean("logo", true);
    }

    static void setLogoOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo", on).apply();
        restyle();
    }

    static BoardBug.Style style(Context c) {
        return BoardBug.Style.of(prefs(c).getString("style", ""));
    }

    static void nextStyle(Context c) {
        BoardBug.Style[] all = BoardBug.Style.values();
        setStyle(c, all[(style(c).ordinal() + 1) % all.length]);
    }

    /** A look chosen: the board drawn in it at once where it is up (no coming in again). */
    static void setStyle(Context c, BoardBug.Style s) {
        prefs(c).edit().putString("style", s.name()).apply();
        if (bug != null && !adjusting) {
            bug.style(s);
            layout(c);
        } else {
            restyle();
        }
    }

    /** The look step looks along: on (step 1) or back (-1). */
    static void stepStyle(Context c, int step) {
        BoardBug.Style[] all = BoardBug.Style.values();
        setStyle(c, all[((style(c).ordinal() + step) % all.length + all.length) % all.length]);
    }

    static LogoMark.Look logoLook(Context c) {
        return LogoMark.Look.of(prefs(c).getString("look", ""));
    }

    static void nextLogoLook(Context c) {
        stepLogoLook(c, 1);
    }

    static void setLogoLook(Context c, LogoMark.Look l) {
        prefs(c).edit().putString("look", l.name()).apply();
        if (mark != null) mark.look(l);
        else restyle();
    }

    static void stepLogoLook(Context c, int step) {
        LogoMark.Look[] all = LogoMark.Look.values();
        setLogoLook(c, all[((logoLook(c).ordinal() + step) % all.length + all.length) % all.length]);
    }

    /** LIVE's red bar along the logo cover's foot while the game is live (on unless switched off). */
    static boolean logoLive(Context c) {
        return prefs(c).getBoolean("live_bar", true);
    }

    static void setLogoLive(Context c, boolean on) {
        prefs(c).edit().putBoolean("live_bar", on).apply();
        if (mark != null) mark.liveBar(on);
    }

    /** The competition's logo beside the logo cover's look (on unless switched off). */
    static boolean logoComp(Context c) {
        return prefs(c).getBoolean("comp_on", true);
    }

    static void setLogoComp(Context c, boolean on) {
        prefs(c).edit().putBoolean("comp_on", on).apply();
        if (mark != null) mark.compOn(on);
    }

    /** The picture tag on the logo cover: Auto (from the channel's name), none, or one set (HD, UHD, 4K, with HDR or not). */
    static ChannelMark.Tag logoTag(Context c) {
        return ChannelMark.Tag.of(prefs(c).getString("tag", ""));
    }

    static void setLogoTag(Context c, ChannelMark.Tag t) {
        prefs(c).edit().putString("tag", t.name()).apply();
        if (mark != null) mark.tag(t.on(InStep.channel(c)));
        F1Tower.retag(c);
    }

    static void stepLogoTag(Context c, int step) {
        ChannelMark.Tag[] all = ChannelMark.Tag.values();
        setLogoTag(c, all[((logoTag(c).ordinal() + step) % all.length + all.length) % all.length]);
    }

    /** OK held over the match while the board is up: its menu (move it, hide it, the looks, off). */
    static boolean holdMenuOn(Context c) {
        return prefs(c).getBoolean("hold_menu", true);
    }

    static void setHoldMenuOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("hold_menu", on).apply();
        holdKeys(boardWin != null && on);
    }

    /** The board's look for the card's pickers: the game chosen there (its clubs, colours, score), else a made-up one. */
    static BoardBug.Look previewLook(ScoresTicker.Game g) {
        if (g == null) return sample();
        BoardBug.Look l = preLook(g);
        l.score = g.state == 1 ? "1–0" : score(g.homeScore) + "–" + score(g.awayScore);
        l.clock = "67:23";
        l.live = true;
        return l;
    }

    /** The game's crests and competition logo for the card's pickers, read off the main thread: {home, away, competition}. */
    static void previewImages(Context c, ScoresTicker.Game g, java.util.function.Consumer<Bitmap[]> got) {
        if (g == null) return;
        String home = g.homeLogo, away = g.awayLogo, league = g.leagueLogo != null ? g.leagueLogo : GoalWatchService.leagueLogoFor(g);
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Bitmap[] b = {crest(a, home), crest(a, away), compLogo(a, league)};
            main().post(() -> got.accept(b));
        });
    }

    /**
     * The scoreboard itself over the channel (the Scoreboard card's switch): off, only the cover over the logo shows,
     * the board's clock and score going on underneath.
     */
    static boolean boardShown(Context c) {
        return prefs(c).getBoolean("board_shown", true);
    }

    static void setBoardShown(Context c, boolean on) {
        prefs(c).edit().putBoolean("board_shown", on).apply();
        restyle();
    }

    /** The football alerts sliding out beside it while it is up (off: the usual cards). */
    static boolean dropsOn(Context c) {
        return prefs(c).getBoolean("drops", true);
    }

    static void setDropsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("drops", on).apply();
    }

    /** The game's stats sliding out beside it now and then. */
    static boolean statsOn(Context c) {
        return prefs(c).getBoolean("stats", true);
    }

    static void setStatsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("stats", on).apply();
        if (!on && stats != null) stats.fold(null);
    }

    /** FotMob's figures added to the stats (expected goals, big chances, ratings, momentum) and its players' pictures. */
    static boolean fotmobOn(Context c) {
        return prefs(c).getBoolean("fotmob", true);
    }

    static void setFotmobOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("fotmob", on).apply();
    }

    /** Each substitution slides out beside the board, with the picture of who comes on. */
    static boolean subsOn(Context c) {
        return prefs(c).getBoolean("subs", true);
    }

    static void setSubsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("subs", on).apply();
    }

    /** Whether each kick-off of its game asks for OK as it shows, to set its clock with the channel's (InStepCard). */
    static boolean asksAtKickoff(Context c) {
        return prefs(c).getBoolean("ask_clock", true);
    }

    static void setAsksAtKickoff(Context c, boolean on) {
        prefs(c).edit().putBoolean("ask_clock", on).apply();
    }

    /** A look changed: the windows made again in it, should they be up. */
    private static void restyle() {
        if (boardWin == null || adjusting) return;
        hideAll();
        main().removeCallbacks(tick);
        main().post(tick);
    }

    // ---- the game as the screen shows it ---------------------------------------------------------------------------

    /** One reading of a game by the scores' poll, and when it was seen. */
    static final class Reading {
        final long seen;
        final String home, away, detail;
        final int state;
        final boolean halftime;
        final double clockSec;
        /** When this clock was first seen (ESPN's moves a minute at a time): the seconds count on from it. */
        long since;

        Reading(long seen, String home, String away, int state, boolean halftime, double clockSec, String detail) {
            this.seen = seen;
            this.home = home;
            this.away = away;
            this.state = state;
            this.halftime = halftime;
            this.clockSec = clockSec;
            this.detail = detail == null ? "" : detail;
            since = seen;
        }
    }

    /** Each game's readings, oldest first (main thread). */
    private static final Map<String, ArrayDeque<Reading>> readings = new HashMap<>();

    /** Pure: a reading kept after the others (none out of order), its clock's first sight carried on while it has not moved. */
    static void add(ArrayDeque<Reading> list, Reading r) {
        Reading last = list.peekLast();
        if (last != null && r.seen < last.seen) return;
        if (last != null && last.state == r.state && last.halftime == r.halftime && last.clockSec == r.clockSec
                && minuteOf(last.detail) == minuteOf(r.detail)) r.since = last.since;
        list.addLast(r);
        while (list.size() > KEEP) list.removeFirst();
    }

    /** Pure: the reading the screen shows at screenNow: the last one seen by then, else the first there is (null: none). */
    static Reading atScreen(Iterable<Reading> list, long screenNow) {
        Reading first = null, pick = null;
        for (Reading r : list) {
            if (first == null) first = r;
            if (r.seen <= screenNow) pick = r;
        }
        return pick != null ? pick : first;
    }

    /** Pure: the clock the screen shows for r at screenNow: {"67:23", ""} or {"45:00", "+2"}; null when not running. */
    static String[] clock(Reading r, long screenNow) {
        double secs = clockSecs(r, screenNow);
        return secs < 0 ? null : clockWords(secs, r.detail);
    }

    /**
     * Pure: the clock's seconds from a reading at screenNow (-1: none), counted on from when it was first seen for as long
     * as the next reading takes to come, so it never stops. Only the minute ("67'"): the minute began a minute before. In
     * added time ESPN's clock can stand at the period's end while its minute goes on ("90'+3'"): the later of the two.
     */
    static double clockSecs(Reading r, long screenNow) {
        if (r == null || r.state != 0 || r.halftime) return -1;
        double secs = Math.max(r.clockSec, (minuteOf(r.detail) - 1) * 60.0);
        if (secs <= 0) return -1;
        return secs + Math.max(0, (screenNow - r.since) / 1000);
    }

    /** The clock as last shown: its game and half, its seconds and when, so it counts on from there. */
    private static String steadyKey;
    private static double steadySecs;
    private static long steadyAt;

    /**
     * The clock to show (main thread): what the scores (or the kick-off's OK) say, but never stopping or stepping back by
     * a little: counted on from the one shown last in the same game and half, unless that one is half a minute or more
     * ahead of what they say now (a real correction).
     */
    private static double steady(String id, String detail, double said, long now) {
        int first = firstMinute(detail);
        int period = first > 0 ? (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) : periodOfSecs((long) said);
        String key = id + "|" + period;
        double running = key.equals(steadyKey) ? steadySecs + (now - steadyAt) / 1000.0 : -1;
        double shown = steadied(said, running);
        steadyKey = key;
        steadySecs = shown;
        steadyAt = now;
        return shown;
    }

    /**
     * Pure: the clock to show from what is said now and the one running on from the last shown (-1: none): the later of
     * the two, unless the running one is half a minute or more ahead.
     */
    static double steadied(double said, double running) {
        if (running < 0 || said >= running || running - said >= 30) return said;
        return running;
    }

    /**
     * Pure: a clock of total seconds as broadcasters show it, "67:23", running on through the minutes added ("92:15")
     * until half time or full time is on the screen. The box beside it (the second word) is the added time put up, which
     * addedWords gives; none here.
     */
    static String[] clockWords(double total, String detail) {
        int whole = Math.max(0, (int) total);
        return new String[]{String.format(Locale.UK, "%02d:%02d", whole / 60, whole % 60), ""};
    }

    /**
     * Pure: the box beside the clock from the half's last minute (the 45th, the 90th) on: the minutes the fourth official
     * put up for that half ("+4"), once known (announced: {first half, second half, extra time's two}); none before, or
     * when not known.
     */
    static String addedWords(String clock, String detail, int[] announced) {
        int m = minuteOfClock(clock);
        if (m < 0 || announced == null) return "";
        int first = firstMinute(detail);
        int period = first > 0 ? (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4)
                : m < 45 ? 1 : m < 90 ? 2 : m < 105 ? 3 : 4;
        int end = period == 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
        // The half's last minute (the 45th, the 90th: from 44:00, 89:00) on.
        if (m < end - 1 || period > announced.length) return "";
        int n = announced[period - 1];
        return n > 0 ? "+" + n : "";
    }

    /** Pure: whether a clock is in its half's added time: its minute at or past the half's end (the half by ESPN's minute). */
    static boolean inAddedTime(String clock, String detail) {
        int m = minuteOfClock(clock);
        if (m < 0) return false;
        int first = firstMinute(detail);
        int period = first > 0 ? (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) : m < 45 ? 1 : m < 90 ? 2 : m < 105 ? 3 : 4;
        return m >= (period == 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120);
    }

    private static final Pattern ADDED_TIME = Pattern.compile("(\\d+)\\s+minutes?\\s+of\\s+(?:added|stoppage|injury)\\s+time",
            Pattern.CASE_INSENSITIVE);

    /** Pure: the minutes of added time a line of commentary puts up ("Fourth official has announced 4 minutes of added time."); 0 for none. */
    static int addedIn(String text) {
        Matcher m = ADDED_TIME.matcher(text == null ? "" : text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** The added time put up in each half from the match summary's commentary: {first, second, extra time's two}. */
    private static int[] announcedOf(org.json.JSONObject summary) {
        int[] out = new int[4];
        org.json.JSONArray com = summary.optJSONArray("commentary");
        for (int i = 0; com != null && i < com.length(); i++) {
            org.json.JSONObject x = com.optJSONObject(i);
            int n = x == null ? 0 : addedIn(x.optString("text"));
            if (n <= 0) continue;
            org.json.JSONObject t = x.optJSONObject("time");
            int first = t == null ? 0 : firstMinute(t.optString("displayValue"));
            if (first <= 0) continue;
            out[(first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) - 1] = n;
        }
        return out;
    }

    /** Each game's added time put up so far, read with its stats. */
    private static final Map<String, int[]> announced = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Pattern MINUTE = Pattern.compile("(\\d+)'?\\s*(?:\\+\\s*(\\d+))?");

    /** Pure: the minute in ESPN's words, the minutes added counted ("45'+2'": 47); 0 when none. */
    static int minuteOf(String detail) {
        Matcher m = MINUTE.matcher(detail == null ? "" : detail);
        if (!m.find()) return 0;
        return Integer.parseInt(m.group(1)) + (m.group(2) == null ? 0 : Integer.parseInt(m.group(2)));
    }

    /** Pure: the minute in ESPN's words before any minutes added ("45'+2'": 45); 0 when none. */
    static int firstMinute(String detail) {
        Matcher m = MINUTE.matcher(detail == null ? "" : detail);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    /** Pure: the minutes added in ESPN's words ("45'+2'": 2); 0 when none. */
    static int added(String detail) {
        if (detail == null) return 0;
        int plus = detail.indexOf('+');
        if (plus < 0) return 0;
        int n = 0;
        for (int i = plus + 1; i < detail.length() && Character.isDigit(detail.charAt(i)); i++) n = n * 10 + (detail.charAt(i) - '0');
        return n;
    }

    /** Pure: which side scored between two scores ("1–0" to "1–1": 2), 0 for neither. */
    static int goalSide(String before, String after) {
        int[] a = goals(before), b = goals(after);
        if (a == null || b == null) return 0;
        return b[0] > a[0] ? 1 : b[1] > a[1] ? 2 : 0;
    }

    private static int[] goals(String score) {
        if (score == null) return null;
        String[] p = score.split("–");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Pure: "EVE" from ESPN's abbreviation, else the name's first three letters. */
    static String code(String abbr, String name) {
        if (abbr != null && !abbr.isEmpty() && abbr.length() <= 4) return abbr.toUpperCase(Locale.ROOT);
        String n = name == null ? "" : name.replaceAll("[^\\p{L}]", "");
        return (n.length() > 3 ? n.substring(0, 3) : n).toUpperCase(Locale.ROOT);
    }

    /** Pure: the competition in letters, while its logo is not there: "PREMIER LEAGUE" "PL", "BUNDESLIGA" "BUN". */
    static String tag(String league) {
        if (league == null || league.trim().isEmpty()) return "";
        String[] w = league.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (w.length == 1) return w[0].length() > 3 ? w[0].substring(0, 3) : w[0];
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length && b.length() < 3; i++) b.append(w[i].charAt(0));
        return b.toString();
    }

    /** Pure: the board for game g as reading r shows it at screenNow. */
    static BoardBug.Look look(ScoresTicker.Game g, Reading r, long screenNow) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = code(g.home, g.homeName);
        l.away = code(g.away, g.awayName);
        l.tag = tag(g.league);
        l.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        l.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        l.homeOwn = BoardBug.onWhite(g.homeColor, g.homeAlt);
        l.awayOwn = BoardBug.onWhite(g.awayColor, g.awayAlt);
        l.score = r.home + "–" + r.away;
        if (r.state == 2) {
            l.clock = "FT";
        } else if (r.halftime) {
            l.clock = "HT";
        } else {
            l.live = true;
            String[] ck = clock(r, screenNow);
            if (ck != null) {
                l.clock = ck[0];
                l.added = ck[1];
            } else {
                l.clock = r.detail;
            }
            l.late = late(l.clock, r.home, r.away);
        }
        return l;
    }

    /**
     * Pure: the last minutes of a close game (the 85th on, a goal or none between them): the board's clock beats then.
     */
    static boolean late(String clock, String home, String away) {
        int m = minuteOfClock(clock);
        if (m < 85 || m > 130) return false;
        int h = goalsOf(score(home) + "–" + score(away), 1), a = goalsOf(score(home) + "–" + score(away), 2);
        return h >= 0 && a >= 0 && Math.abs(h - a) <= 1;
    }

    /** A made-up game, to place it by with no game on (Move and resize, the adb test). */
    private static BoardBug.Look sample() {
        BoardBug.Look l = new BoardBug.Look();
        l.home = "HOM";
        l.away = "AWY";
        l.score = "1–0";
        l.clock = "45:00";
        l.live = true;
        l.tag = "PL";
        l.homeColour = 0xFFD8323B;
        l.awayColour = 0xFF4F8BFF;
        return l;
    }

    private static String score(String s) {
        return s == null || s.trim().isEmpty() ? "0" : s.trim();
    }

    /** Pure: the board before kick-off: the clubs, "v", and the kick-off's time on its clock. */
    static BoardBug.Look preLook(ScoresTicker.Game g) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = code(g.home, g.homeName);
        l.away = code(g.away, g.awayName);
        l.tag = tag(g.league);
        l.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        l.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        l.homeOwn = BoardBug.onWhite(g.homeColor, g.homeAlt);
        l.awayOwn = BoardBug.onWhite(g.awayColor, g.awayAlt);
        l.score = "v";
        l.clock = g.kickoff > 0 ? java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
        return l;
    }

    // ---- its clock set with the channel's ----------------------------------------------------------------------------

    /**
     * OK at a kick-off on the screen (InStepCard, while the board is on for that game): from that moment its clock is
     * counted as the channel's, base seconds then (0, or 2700 for the second half), not from the scores (main thread).
     */
    static void anchor(Context c, String gameId, int base, long at) {
        if (gameId == null || gameId.isEmpty()) return;
        prefs(c).edit().putString("anchor", gameId + "|" + base + "|" + at).apply();
        // Counted from this moment now, not on from the one shown before.
        steadyKey = null;
        AlertLog.note(c, "scoreboard clock set with the channel's: " + (base / 60) + ":00 at the OK");
        poke();
        resume(c);
    }

    /** Pure: the clock set by hand for this game, {base seconds, when}, from "id|base|at"; null for none, or another game's. */
    static long[] anchorOf(String s, String gameId) {
        if (s == null || gameId == null) return null;
        String[] f = s.split("\\|");
        if (f.length != 3 || !f[0].equals(gameId)) return null;
        try {
            return new long[]{Long.parseLong(f[1]), Long.parseLong(f[2])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Pure: the period a clock of total seconds is in (1, 2, then extra time's 3 and 4). */
    static int periodOfSecs(long secs) {
        return secs < 45 * 60 ? 1 : secs < 90 * 60 ? 2 : secs < 105 * 60 ? 3 : 4;
    }

    /**
     * Pure: the clock counted from a moment set by hand (a = {base seconds, when}) to now, as broadcasters show it; null
     * when the period on the screen (ESPN's minute) is another than the one it was set in, or there is none.
     */
    static String[] anchored(long[] a, long now, String detail) {
        double secs = anchoredSecs(a, now, detail);
        return secs < 0 ? null : clockWords(secs, detail);
    }

    /** Pure: the same as seconds (-1: none). */
    static double anchoredSecs(long[] a, long now, String detail) {
        if (a == null || now < a[1]) return -1;
        int period = periodOfSecs(a[0]), first = firstMinute(detail);
        if (first > 0 && (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) != period) return -1;
        return a[0] + (now - a[1]) / 1000.0;
    }

    /** Move and resize's Timing: the board (its clock, its score and the alerts) delta ms later, or sooner when negative. */
    static void timing(Context c, long delta) {
        if (delta == 0) return;
        InStep.nudge(c, delta);
        // A second later or sooner, as asked: not held to the clock shown before.
        steadyKey = null;
        String id = game(c);
        long[] a = anchorOf(prefs(c).getString("anchor", null), id);
        if (a != null) prefs(c).edit().putString("anchor", id + "|" + a[0] + "|" + (a[1] + delta)).apply();
    }

    /** Timing's line: how far the board is held behind the live scores, and whether its clock was set at kick-off. */
    static String timingWords(Context c) {
        String id = game(c);
        boolean set = anchorOf(prefs(c).getString("anchor", null), id) != null;
        return "Held " + InStep.words(InStep.holdMs(c, true)) + " behind the scores" + (set ? ", its clock set at kick-off" : "");
    }

    // ---- the alerts, as they slide out beside it -------------------------------------------------------------------

    /** Pure: an alert kind's colour for its chip (a goal's green, a red card's red, VAR's amber); PLAIN for the rest. */
    static int kindColour(String kind) {
        switch (kind == null ? "" : kind) {
            case "goal": return 0xFF3DDC84;
            case "red": return 0xFFFF5A5F;
            case "var": case "pen": case "drama": case "incident": return 0xFFFFB547;
            case "sub": return 0xFF5CC8FF;
            default: return BoardDrop.PLAIN;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    /** Pure: the parts there are, with " · " between. */
    static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(p.trim());
        }
        return b.toString();
    }

    /**
     * Pure: a game's card (ScoreCard) as it slides out beside the board: its label as the chip ("GOAL", "RED CARD",
     * "FULL TIME"), the score as the title, who and when as the line; the clubs' colours down its edge, the scorers'
     * alone for a goal.
     */
    static BoardDrop.Drop dropOf(ScoreCard.Data d, int homeColour, int awayColour) {
        BoardDrop.Drop o = new BoardDrop.Drop();
        o.gameId = d.gameId;
        o.chip = (nz(d.label) + " " + nz(d.labelSmall)).trim();
        o.colour = kindColour(d.kind);
        String home = nz(d.homeName), away = nz(d.awayName);
        boolean kickoff = "kickoff".equals(d.kind);
        o.title = kickoff || nz(d.score).isEmpty() ? home + " v " + away : home + " " + nz(d.score) + " " + away;
        String name = (nz(d.first) + " " + nz(d.last)).trim();
        if ("ft".equals(d.kind)) o.line = join(d.last, d.first, d.line);
        else if (kickoff) o.line = join(d.first, d.line);
        else if ("goal".equals(d.kind) || "red".equals(d.kind) || "pen".equals(d.kind)) o.line = (name + " " + nz(d.line)).trim();
        else o.line = join(d.first, d.last, d.line);
        o.homeColour = homeColour;
        o.awayColour = awayColour;
        int scored = "goal".equals(d.kind) ? goalSide(d.scoreBefore, d.score) : 0;
        if ("goal".equals(d.kind) && d.gameId != null && !nz(d.score).isEmpty()) o.key = d.gameId + "|" + nz(d.score);
        o.side = scored > 0 ? scored : d.crest == null ? 0 : d.crest == d.homeCrest ? 1 : d.crest == d.awayCrest ? 2 : 0;
        // As the card has them: the player's picture, both crests.
        o.photo = d.photo;
        o.homeCrest = d.homeCrest;
        o.awayCrest = d.awayCrest;
        o.what = (o.chip + " " + o.title + " " + o.line).trim();
        return o;
    }

    /** Pure: a pop-up's sound as the chip's words. */
    static String chipOf(String sound) {
        switch (sound == null ? "" : sound) {
            case "goal": return "GOAL";
            case "red": return "RED CARD";
            case "fulltime": return "FULL TIME";
            case "halftime": return "HALF TIME";
            case "incident": return "VAR";
            case "kickoff": case "reminder": return "KICK-OFF";
            default: return sound == null ? "" : sound.toUpperCase(Locale.ROOT);
        }
    }

    /** Pure: a football pop-up's line ("⚽  Salah  67'") as it slides out beside the board. */
    static BoardDrop.Drop dropOfLine(String head, ScoresTicker.Game g, String sound, String scoredBy) {
        return dropOfLine(head, g, sound, scoredBy, null, null);
    }

    /** The same, with both clubs' crests as the pop-up had them. */
    static BoardDrop.Drop dropOfLine(String head, ScoresTicker.Game g, String sound, String scoredBy, Bitmap homeCrest, Bitmap awayCrest) {
        BoardDrop.Drop o = new BoardDrop.Drop();
        o.gameId = g.id;
        o.chip = chipOf(sound);
        o.colour = kindColour("incident".equals(sound) ? "var" : sound);
        String home = g.homeName != null ? g.homeName : nz(g.home), away = g.awayName != null ? g.awayName : nz(g.away);
        o.title = home + " " + score(g.homeScore) + "–" + score(g.awayScore) + " " + away;
        o.line = (head == null ? "" : head).replaceAll("^[^\\p{L}\\p{N}]+", "").replaceAll("\\s{2,}", " ").trim();
        o.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        o.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        o.side = "home".equals(scoredBy) ? 1 : "away".equals(scoredBy) ? 2 : 0;
        if ("goal".equals(sound) && g.id != null) o.key = g.id + "|" + score(g.homeScore) + "–" + score(g.awayScore);
        o.homeCrest = homeCrest == null ? null : ScoreCard.onNavy(homeCrest);
        o.awayCrest = awayCrest == null ? null : ScoreCard.onNavy(awayCrest);
        o.what = (o.chip + " " + o.title + " " + o.line).trim();
        return o;
    }

    /** Pure: both starting elevens as they slide out: each club's name and shape, then its players' surnames. */
    static BoardDrop.Drop lineupsDrop(ScoresTicker.Game g, ScoresTicker.Side[] sides) {
        return lineupsDrop(g, sides, null, null);
    }

    /** The same, each club's crest before its eleven. */
    static BoardDrop.Drop lineupsDrop(ScoresTicker.Game g, ScoresTicker.Side[] sides, Bitmap homeCrest, Bitmap awayCrest) {
        BoardDrop.Drop o = new BoardDrop.Drop();
        o.gameId = g.id;
        o.chip = "LINE-UPS";
        o.lineups = true;
        String home = g.homeName != null ? g.homeName : nz(g.home), away = g.awayName != null ? g.awayName : nz(g.away);
        String when = g.kickoff > 0 ? java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
        o.title = home + " v " + away + (when.isEmpty() ? "" : " · " + when);
        o.line = eleven(sides[0], home);
        o.line2 = eleven(sides[1], away);
        o.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        o.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        o.homeCrest = homeCrest;
        o.awayCrest = awayCrest;
        o.what = "line-ups " + o.title;
        return o;
    }

    private static String eleven(ScoresTicker.Side s, String fallback) {
        StringBuilder b = new StringBuilder();
        for (String[] p : s.players) {
            if (p == null || p.length == 0 || p[0] == null || p[0].isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(p[0]);
        }
        String name = s.name != null && !s.name.isEmpty() ? s.name : fallback;
        return name + (s.formation == null || s.formation.isEmpty() ? "" : " (" + s.formation + ")") + ": " + b;
    }

    /** Whether the board is up over the channel to take an alert: shown, not going, not being placed, switched on for it. */
    static boolean up(Context c) {
        return boardWin != null && bug != null && column != null && column.getVisibility() == View.VISIBLE && !bug.closing()
                && !adjusting && testUntil <= System.currentTimeMillis() && dropsOn(c);
    }

    /** A game's card while the board is up: slid out beside it instead, in its look; true when taken (main thread). */
    static boolean take(Context c, ScoreCard.Data d) {
        if (d == null || d.f1 || d.gameId == null || d.homeName == null || !up(c)) return false;
        ScoresTicker.Game g = GoalWatchService.latestGame(d.gameId);
        drop(c, dropOf(d, g == null ? 0 : BoardBug.colour(g.homeColor, g.homeAlt), g == null ? 0 : BoardBug.colour(g.awayColor, g.awayAlt)));
        return true;
    }

    /** A football pop-up's line while the board is up: slid out beside it instead; true when taken (main thread). */
    static boolean takeLine(Context c, CharSequence head, ScoresTicker.Game g, String sound, String scoredBy, Bitmap home, Bitmap away) {
        if (g == null || g.id == null || !up(c)) return false;
        drop(c, dropOfLine(head == null ? "" : head.toString(), g, sound, scoredBy, home, away));
        return true;
    }

    /** The kick-off's line-ups while the board is up: both elevens slid out beside it; true when taken (main thread). */
    static boolean takeLineups(Context c, ScoresTicker.Game g, ScoresTicker.Side[] sides, Bitmap home, Bitmap away) {
        if (g == null || g.id == null || sides == null || sides.length < 2 || sides[0] == null || sides[1] == null || !up(c)) return false;
        if (!NotifyPrefs.allowed(c, "kickoff", false, g.slug, g.id)) return false;
        drop(c, lineupsDrop(g, sides, home, away));
        return true;
    }

    // ---- the stats ---------------------------------------------------------------------------------------------------

    /** Pure: the stats worth a strip, in the broadcasters' order, from each side's {name: shown value} (ESPN's names). */
    static List<String[]> statsList(Map<String, String> h, Map<String, String> a) {
        String[][] wanted = {{"possessionPct", "Possession"}, {"totalShots", "Shots"}, {"shotsOnTarget", "On target"},
                {"wonCorners", "Corners"}, {"foulsCommitted", "Fouls"}, {"yellowCards", "Yellow cards"}, {"saves", "Saves"},
                {"offsides", "Offsides"}};
        List<String[]> out = new ArrayList<>();
        for (String[] w : wanted) {
            String x = h.get(w[0]), y = a.get(w[0]);
            if (x == null || y == null || x.trim().isEmpty() || y.trim().isEmpty()) continue;
            if (w[0].equals("possessionPct")) {
                x = pct(x);
                y = pct(y);
            }
            out.add(new String[]{w[1], x.trim(), y.trim()});
        }
        String ph = passes(h), pa = passes(a);
        if (!ph.isEmpty() && !pa.isEmpty()) out.add(new String[]{"Pass accuracy", ph, pa});
        return out;
    }

    /** Pure: the share of a side's passes that found a team-mate ("84%"); "" without its figures. */
    static String passes(Map<String, String> m) {
        int ok = number(m.get("accuratePasses")), all = number(m.get("totalPasses"));
        return all > 0 ? Math.round(ok * 100f / all) + "%" : "";
    }

    private static String pct(String s) {
        try {
            return Math.round(Float.parseFloat(s.replace("%", "").trim())) + "%";
        } catch (NumberFormatException e) {
            return s;
        }
    }

    /**
     * The stats and facts from ESPN's match summary, by name: the match's (each side's by its team's id, else the first
     * is the home side's), the pass accuracy, each side's form (its last five), its place in the table, the chances
     * before kick-off and the player of the match so far.
     */
    private static Map<String, String[]> statsOf(org.json.JSONObject summary, ScoresTicker.Game g) {
        Map<String, String> h = new HashMap<>(), a = new HashMap<>();
        org.json.JSONObject box = summary.optJSONObject("boxscore");
        org.json.JSONArray teams = box == null ? null : box.optJSONArray("teams");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            org.json.JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            org.json.JSONObject team = t.optJSONObject("team");
            String tid = team == null ? "" : team.optString("id");
            boolean home = g.homeId != null && !g.homeId.isEmpty() && !tid.isEmpty() ? tid.equals(g.homeId) : i == 0;
            Map<String, String> m = home ? h : a;
            org.json.JSONArray st = t.optJSONArray("statistics");
            for (int k = 0; st != null && k < st.length(); k++) {
                org.json.JSONObject s = st.optJSONObject(k);
                if (s != null) m.put(s.optString("name"), s.optString("displayValue"));
            }
        }
        Map<String, String[]> by = new HashMap<>();
        if (g.state != 1) for (String[] s : statsList(h, a)) by.put(s[0], s);
        String[] f = form(summary.optJSONArray("lastFiveGames"), g);
        if (f != null) by.put(f[0], f);
        String[] t = table(summary.optJSONObject("standings"), g);
        if (t != null) by.put(t[0], t);
        String[] w = winChance(summary.optJSONArray("odds"));
        if (w != null) by.put(w[0], w);
        if (g.state != 1) {
            String[] p = star(summary.optJSONArray("rosters"), g);
            if (p != null) by.put(p[0], p);
        }
        return by;
    }

    /** Each side's last five results ("WWDLW"), as ESPN lists them; null without both. */
    private static String[] form(org.json.JSONArray five, ScoresTicker.Game g) {
        String fh = null, fa = null;
        for (int i = 0; five != null && i < five.length(); i++) {
            org.json.JSONObject x = five.optJSONObject(i);
            if (x == null) continue;
            String id = x.optJSONObject("team") == null ? "" : x.optJSONObject("team").optString("id");
            StringBuilder r = new StringBuilder();
            org.json.JSONArray ev = x.optJSONArray("events");
            for (int k = 0; ev != null && k < ev.length() && r.length() < 5; k++) {
                org.json.JSONObject e = ev.optJSONObject(k);
                String res = e == null ? "" : e.optString("gameResult", "");
                if (!res.isEmpty()) r.append(Character.toUpperCase(res.charAt(0)));
            }
            if (r.length() == 0) continue;
            if (id.equals(g.homeId)) fh = r.toString();
            else if (id.equals(g.awayId)) fa = r.toString();
        }
        return fh != null && fa != null ? new String[]{"Form", fh, fa, "form"} : null;
    }

    /** Each side's place in its league's table and its points ("3rd · 64 pts"); null without both. */
    private static String[] table(org.json.JSONObject standings, ScoresTicker.Game g) {
        org.json.JSONArray groups = standings == null ? null : standings.optJSONArray("groups");
        String ph = null, pa = null;
        for (int gi = 0; groups != null && gi < groups.length(); gi++) {
            org.json.JSONObject grp = groups.optJSONObject(gi);
            org.json.JSONObject st = grp == null ? null : grp.optJSONObject("standings");
            org.json.JSONArray entries = st == null ? null : st.optJSONArray("entries");
            for (int i = 0; entries != null && i < entries.length(); i++) {
                org.json.JSONObject e = entries.optJSONObject(i);
                if (e == null) continue;
                Map<String, String> v = new HashMap<>();
                org.json.JSONArray es = e.optJSONArray("stats");
                for (int k = 0; es != null && k < es.length(); k++) {
                    org.json.JSONObject x = es.optJSONObject(k);
                    if (x != null) v.put(x.optString("name"), x.optString("displayValue"));
                }
                int rank = number(v.get("rank"));
                if (rank <= 0) continue;
                String place = ordinal(rank) + " · " + v.getOrDefault("points", "0") + " pts";
                String id = e.optString("id");
                if (id.equals(g.homeId)) ph = place;
                if (id.equals(g.awayId)) pa = place;
            }
        }
        return ph != null && pa != null ? new String[]{"League table", ph, pa, "text"} : null;
    }

    /** Each side's chance of winning as the prices before kick-off put them (with the draw's, the three make 100); null without. */
    private static String[] winChance(org.json.JSONArray odds) {
        org.json.JSONObject o = odds == null || odds.length() == 0 ? null : odds.optJSONObject(0);
        if (o == null) return null;
        double h = chance(o.optJSONObject("homeTeamOdds")), a = chance(o.optJSONObject("awayTeamOdds")), d = chance(o.optJSONObject("drawOdds"));
        if (h <= 0 || a <= 0 || d <= 0) return null;
        double all = h + a + d;
        return new String[]{"Win chance", Math.round(100 * h / all) + "%", Math.round(100 * a / all) + "%"};
    }

    /** A price (American odds, +260 or -500) as the chance it stands for. */
    private static double chance(org.json.JSONObject x) {
        if (x == null || !x.has("moneyLine")) return 0;
        double m = x.optDouble("moneyLine", 0);
        if (m == 0 || Double.isNaN(m)) return 0;
        return m > 0 ? 100 / (m + 100) : -m / (-m + 100);
    }

    /**
     * The player of the match so far from both line-ups' own figures (goals count most, then assists, shots on target,
     * saves, less cards and own goals): "Saka (ARS) · 2 goals · 1 assist". Null until someone has done enough.
     */
    private static String[] star(org.json.JSONArray rosters, ScoresTicker.Game g) {
        double best = 3.99;
        String pick = null;
        for (int r = 0; rosters != null && r < rosters.length(); r++) {
            org.json.JSONObject side = rosters.optJSONObject(r);
            if (side == null) continue;
            boolean home = "home".equals(side.optString("homeAway"));
            org.json.JSONArray players = side.optJSONArray("roster");
            for (int i = 0; players != null && i < players.length(); i++) {
                org.json.JSONObject pl = players.optJSONObject(i);
                if (pl == null) continue;
                Map<String, Double> v = new HashMap<>();
                org.json.JSONArray st = pl.optJSONArray("stats");
                for (int k = 0; st != null && k < st.length(); k++) {
                    org.json.JSONObject x = st.optJSONObject(k);
                    if (x != null) v.put(x.optString("name"), x.optDouble("value", 0));
                }
                double goals = v.getOrDefault("totalGoals", 0d), assists = v.getOrDefault("goalAssists", 0d);
                double onTarget = v.getOrDefault("shotsOnTarget", 0d), shots = v.getOrDefault("totalShots", 0d), saves = v.getOrDefault("saves", 0d);
                double score = 10 * goals + 6 * assists + 2 * onTarget + 0.5 * Math.max(0, shots - onTarget) + 1.5 * saves
                        - 2 * v.getOrDefault("yellowCards", 0d) - 6 * v.getOrDefault("redCards", 0d) - 6 * v.getOrDefault("ownGoals", 0d);
                org.json.JSONObject who = pl.optJSONObject("athlete");
                if (score <= best || who == null) continue;
                List<String> did = new ArrayList<>();
                if (goals > 0) did.add(count((int) goals, "goal"));
                if (assists > 0) did.add(count((int) assists, "assist"));
                if (goals == 0 && onTarget > 0) did.add(count((int) onTarget, "shot") + " on target");
                if (saves > 0) did.add(count((int) saves, "save"));
                best = score;
                pick = GoalWatchService.surname(who.optString("displayName")) + " ("
                        + (home ? code(g.home, g.homeName) : code(g.away, g.awayName)) + ") · " + android.text.TextUtils.join(" · ", did);
            }
        }
        return pick == null ? null : new String[]{"Player of the match", pick, "", "one"};
    }

    private static String count(int n, String what) {
        return n + " " + what + (n == 1 ? "" : "s");
    }

    private static int number(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Pure: 1st, 2nd, 3rd, 4th, 11th, 21st. */
    static String ordinal(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    /**
     * The scores' poll read these games (main thread): each live game's reading kept with when it was seen, and a game
     * waited for taken up once one is on (its channel tuned, or Match mode on for it).
     */
    static void read(Context c, List<ScoresTicker.Game> games) {
        long now = System.currentTimeMillis();
        for (ScoresTicker.Game g : games) {
            if (g == null || g.id == null || g.state == 1) continue;
            ArrayDeque<Reading> list = readings.get(g.id);
            // Full time kept once, for a game read while it was live.
            if (g.state == 2 && (list == null || (list.peekLast() != null && list.peekLast().state == 2))) continue;
            if (list == null) {
                list = new ArrayDeque<>();
                readings.put(g.id, list);
            }
            add(list, new Reading(now, score(g.homeScore), score(g.awayScore), g.state, g.halftime, g.clockSec, g.detail));
        }
        // A game not read for half an hour is over: its readings go.
        readings.values().removeIf(l -> l.isEmpty() || now - l.peekLast().seen > 30 * 60_000L);
        if (!active(c)) return;
        String id = game(c);
        if (id != null && id.isEmpty()) {
            ScoresTicker.Game mm = matchModeGame(c);
            if (mm != null) take(c, mm);
            else findGameOnNow(c);
        }
        resume(c);
    }

    /**
     * The game's reading the screen shows now. With none kept yet (switched on as the TV started), one from the scores
     * as they are, taken as on the screen already, so its clock runs at once rather than waiting the channel's wait.
     */
    private static Reading screen(Context c, String id, long now) {
        long hold = InStep.holdMs(c, true);
        ArrayDeque<Reading> list = readings.get(id);
        if (list == null || list.isEmpty()) {
            ScoresTicker.Game g = GoalWatchService.latestGame(id);
            if (g == null || g.state == 1) return null;
            list = new ArrayDeque<>();
            add(list, new Reading(now - hold, score(g.homeScore), score(g.awayScore), g.state, g.halftime, g.clockSec, g.detail));
            readings.put(id, list);
        }
        return atScreen(list, now - hold);
    }

    // ---- showing it ------------------------------------------------------------------------------------------------

    private static FrameLayout boardWin, logoWin, hintWin;
    /** The board and, beside it in line with it, what slides out of it (the stats, an alert), drawn over their tucked ends. */
    private static FrameLayout column;
    private static BoardBug bug;
    private static BoardStats stats;
    private static BoardDrop drop;
    private static LogoMark mark;
    /** The windows just made: the board comes in as a broadcaster's does at its first showing. */
    private static boolean fresh;
    /**
     * The board's height set; the widest what slides out beside it may be, how far its near end tucks under the board
     * (beside a pill: the gap between them instead), and where the board sits along its window's row, in pixels.
     */
    private static int boardH = 48, sideMax = 600, tuck = 6, sideGap, boardAt, head;
    /** The game's crests as the board has them, for its own goal captions. */
    private static Bitmap homeCrest, awayCrest;
    /** The game's competition logo and short name, for the logo cover's Competition look. */
    private static Bitmap compImage;
    private static String compTag = "";
    /** What slides out comes out on the board's left (a board nearer the screen's right). */
    private static boolean sideLeft;
    /** How long before kick-off it is up (the kick-off and the line-ups slide out of it), and how long into half time. */
    static final long PRE_MS = 20 * 60_000L, HT_SHOWN_MS = 20_000L;
    /** The stats: how often they are read, and how long before kick-off their first moment may come. */
    static final long STATS_READ_MS = 120_000L, PRE_STATS_MS = 10 * 60_000L;
    /** How long an alert stays out at least (a longer one longer, for its words to run to their end). */
    static final long DROP_MS = 8_000L;
    /** The widest what slides out beside the board may be, in board heights. */
    static final float STRIP_W = 15f;
    private static TextView hint;
    /** This channel's places, and which channel they are for. */
    private static BoardPlace place;
    private static String placeKey;
    /** The screen, and the edge of ground round each (the burn-in step's room), in pixels. */
    private static int sw = 1920, sh = 1080, edge = 2;
    /** The game whose crests are on the board, and the score shown last (a goal is its change). */
    private static String imagesFor, shownId, shownScore;
    /** adb's test hook: shown over whatever is on until then. */
    private static long testUntil;
    private static String lastError;

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            main().removeCallbacks(this);
            Context c = app;
            if (c == null) return;
            long next;
            try {
                next = update(c);
            } catch (RuntimeException e) {
                if (!String.valueOf(e).equals(lastError)) AlertLog.note(c, "scoreboard: " + e);
                lastError = String.valueOf(e);
                hideAll();
                next = 5_000L;
            }
            if (next > 0) main().postDelayed(this, next);
        }
    };

    /** Looked at again: when it is on (the service starting, a switch, a poll), Move and resize, the test. */
    static void resume(Context c) {
        app = c.getApplicationContext();
        if (!active(app) && !adjusting && testUntil <= System.currentTimeMillis()) return;
        main().removeCallbacks(tick);
        main().post(tick);
    }

    /** adb only (--ez test_board_cover true): the board and the cover over whatever is on for a minute. */
    static void test(Context c) {
        app = c.getApplicationContext();
        testUntil = System.currentTimeMillis() + 60_000L;
        hideAll();
        main().removeCallbacks(tick);
        main().post(tick);
    }

    /** One look at what should show: the windows up or away. Returns when to look again (0: not until switched on). */
    private static long update(Context c) {
        long now = System.currentTimeMillis();
        if (adjusting) {
            // Home pressed: what was set is kept. (Home still in front just after it opened the app: a moment more.)
            if (GoalWatchService.homeInFront) {
                if (SystemClock.uptimeMillis() - adjustAt < 5_000L) return 500L;
                endAdjust(true);
                return 0;
            }
            if (!ensureWindows(c)) {
                endAdjust(false);
                return 0;
            }
            showGame(c, now, true, null);
            return 1_000L;
        }
        if (testUntil > now) {
            if (ensureWindows(c)) showGame(c, now, true, null);
            return 1_000L;
        }
        if (testUntil != 0) {
            testUntil = 0;
            hideAll();
        }
        if (!active(c)) {
            if (prefs(c).getBoolean("on", false)) stop(c, "four hours on");
            hideAll();
            return 0;
        }
        String id = game(c);
        if (id == null || id.isEmpty()) {
            hideAll();
            return 5_000L;
        }
        if (!canShow(c)) {
            hideAll();
            return 1_000L;
        }
        Reading r = screen(c, id, now);
        if (r == null || r.state == 1) {
            // Before kick-off: up in its last minutes with the kick-off's time on its clock (the kick-off and the
            // line-ups slide out of it).
            ScoresTicker.Game g = GoalWatchService.latestGame(id);
            if (g != null && g.state == 1 && g.kickoff > now && g.kickoff - now <= PRE_MS) {
                if (ensureWindows(c)) showGame(c, now, false, preLook(g));
                return 1_000L;
            }
            closeAll();
            return 1_000L;
        }
        if (r.state == 2) {
            // Full time on the screen: shown under a minute (its alert and its stats first), off ten minutes after.
            long ft = prefs(c).getLong("ft", 0);
            if (ft == 0) {
                ft = now;
                prefs(c).edit().putLong("ft", ft).apply();
                poke();
            }
            if (now - ft > AFTER_FT_MS) {
                stop(c, "full time");
                return 0;
            }
            if (now - ft > FT_SHOWN_MS && (!dropping || now - ft > FT_GONE_MS)) {
                closeAll();
                return 5_000L;
            }
        }
        if (r.halftime) {
            // Half time on the screen: up a little longer for its alert and the half's stats, then it rests.
            if (!id.equals(htFor)) {
                htFor = id;
                htAt = now;
            }
            if (now - htAt > HT_SHOWN_MS && !dropping && (stats == null || !stats.isOut())) {
                closeAll();
                return 1_000L;
            }
        }
        if (!ensureWindows(c)) return 5_000L;
        showGame(c, now, false, null);
        return 1_000L;
    }

    /** The game whose half time is on the screen, and since when. */
    private static String htFor;
    private static long htAt;

    /** The game (the made-up one; before kick-off, pre) on the board, where this channel's places put it. */
    private static void showGame(Context c, long now, boolean orSample, BoardBug.Look pre) {
        String id = game(c);
        ScoresTicker.Game g = id == null || id.isEmpty() ? null : GoalWatchService.latestGame(id);
        Reading r = g == null || pre != null ? null : screen(c, id, now);
        BoardBug.Look l;
        if (pre != null) {
            l = pre;
        } else if (g != null && r != null && r.state != 1) {
            long screenNow = now - InStep.holdMs(c, true);
            l = look(g, r, screenNow);
            if (l.live) {
                // The clock: counted from the kick-off on the screen (OK when asked), the channel's own; else the scores'.
                // Always counting on, through the minutes added too, never stopping or stepping back by a little.
                double secs = anchoredSecs(anchorOf(prefs(c).getString("anchor", null), id), now, r.detail);
                if (secs < 0) secs = clockSecs(r, screenNow);
                if (secs >= 0) l.clock = clockWords(steady(id, r.detail, secs, now), r.detail)[0];
                // From the half's last minute, the added time put up beside it once it is known.
                l.added = addedWords(l.clock, r.detail, announced.get(id));
                // In its half's added time: every look moves, the minutes added beside it.
                l.stoppage = inAddedTime(l.clock, r.detail);
            }
        } else if (orSample) {
            l = sample();
        } else {
            return;
        }
        String channel = InStep.channel(c), key = BoardPlace.key(channel);
        if (place == null || !key.equals(placeKey)) {
            place = BoardPlace.get(c, channel);
            placeKey = key;
        }
        if (g != null && !g.id.equals(imagesFor)) loadImages(c, g);
        // A goal on the screen: its moment on the board (the scorers' colour across it, the new score popping up), and
        // the board bright again.
        if (pre == null && g != null && r != null && id.equals(shownId) && shownScore != null && !shownScore.equals(l.score)) {
            int side = goalSide(shownScore, l.score);
            if (side > 0) {
                bug.goal(side);
                goalShownAt = now;
                expectScorer(c, g, l.score, side);
            }
            poke();
        }
        if (pre == null && g != null && r != null) {
            shownId = id;
            shownScore = l.score;
        }
        bug.crests(g != null && (g.homeLogo != null || g.awayLogo != null));
        bug.set(l);
        boolean board = adjusting || testUntil > now || boardShown(c);
        column.setVisibility(board ? View.VISIBLE : View.INVISIBLE);
        if (board && (fresh || bug.closing())) {
            // In as a broadcaster's comes in: just up, its first block opening out of a line; going, back again.
            if (fresh) bug.shownAt(0f);
            bug.open();
        }
        fresh = false;
        stats.colours(l.homeColour, l.awayColour, l.homeOwn, l.awayOwn);
        // The logo's cover in Broadcast: the picture's tag, LIVE while the game is, the scoreboard's colours.
        if (mark != null) {
            mark.tag(logoTag(c).on(InStep.channel(c)));
            mark.live(l.live);
            mark.boardLook(bug.stripLook());
        }
        layout(c);
        guard(now);
        if (g != null && board && !adjusting && testUntil <= now) {
            readStats(c, g, now);
            if (pre != null) maybeStats(c, g, null, now, true);
            else if (r != null) maybeStats(c, g, r, now, false);
        }
    }

    /** Whether the channel app has the screen to itself: not Home, the screensaver, another of the launcher's frames. */
    /** Whether the player has the screen with nothing else of the launcher's up over it (the overlays button asks then). */
    static boolean playerUp(Context c) {
        return canShow(c);
    }

    private static boolean canShow(Context c) {
        if (GoalWatchService.homeInFront || GoalWatchService.dreamingNow()) return false;
        PowerManager pm = c.getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return false;
        if (LiveCentre.isShowing() || F1Tower.isShowing() || CreditsCover.isShowing() || HalfTimeBreak.isShowing()
                || PopupFrame.isShowing() || TwoUp.active()) return false;
        return playerInFront(c);
    }

    private static long frontAt;
    private static boolean frontOk = true;

    /** Whether a channel app is in front (looked at every two seconds); true when that cannot be told. */
    private static boolean playerInFront(Context c) {
        long now = SystemClock.uptimeMillis();
        if (now - frontAt < 2_000L) return frontOk;
        frontAt = now;
        String f = AppSearchService.front();
        // The launcher's own window in front over the app (a pop-up with the remote): the app below, by usage.
        if (f == null || f.equals(c.getPackageName())) f = usageFront(c);
        frontOk = f == null || isPlayer(f);
        return frontOk;
    }

    static boolean isPlayer(String pkg) {
        for (String p : PLAYERS) if (p.equals(pkg)) return true;
        return false;
    }

    private static long usageFrom;
    private static String usageLast;

    /** The last app moved to the front, by usage access (only what is new since the last look is read); null unknown. */
    private static String usageFront(Context c) {
        try {
            android.app.usage.UsageStatsManager um = c.getSystemService(android.app.usage.UsageStatsManager.class);
            if (um == null) return null;
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = um.queryEvents(usageFrom == 0 ? now - 30 * 60_000L : usageFrom - 2_000L, now);
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) usageLast = e.getPackageName();
            }
            usageFrom = now;
            return usageLast;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Both windows up (the logo's cover only when it is on); false when the TV will not let them show. */
    private static boolean ensureWindows(Context c) {
        if (boardWin != null) return true;
        if (!Settings.canDrawOverlays(c)) return false;
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);
        edge = Math.max(2, Math.round(2f * sw / 1920f));
        bug = new BoardBug(c);
        bug.style(style(c));
        bug.inset(edge);
        stats = new BoardStats(c);
        drop = new BoardDrop(c);
        // The board, and beside it what slides out of it, drawn under the board where their near ends tuck in.
        column = new FrameLayout(c);
        column.addView(stats, new FrameLayout.LayoutParams(1, 1));
        column.addView(drop, new FrameLayout.LayoutParams(1, 1));
        column.addView(bug, new FrameLayout.LayoutParams(1, 1));
        boardWin = new FrameLayout(c);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        cl.leftMargin = edge;
        cl.topMargin = edge;
        boardWin.addView(column, cl);
        if (!addWindow(c, boardWin)) {
            boardWin = null;
            column = null;
            bug = null;
            stats = null;
            drop = null;
            return false;
        }
        if (logoOn(c)) {
            mark = new LogoMark(c);
            mark.look(logoLook(c));
            mark.competition(compImage, compTag);
            mark.compOn(logoComp(c));
            mark.liveBar(logoLive(c));
            mark.inset(edge);
            logoWin = frame(c, mark);
            if (!addWindow(c, logoWin)) {
                logoWin = null;
                mark = null;
            }
        }
        imagesFor = null;
        shownScore = null;
        stepAt = System.currentTimeMillis();
        changedAt = stepAt;
        dimmed = false;
        fresh = true;
        outline();
        holdKeys(holdMenuOn(c));
        return true;
    }

    /**
     * The keys for OK held over the match, while the board's windows are up: the key helper's (the Streamer) has them
     * anyway; the channel helper takes them on the TCL, which costs a held key its repeat over the app meanwhile.
     */
    private static void holdKeys(boolean on) {
        if (!InputKeyService.running) AppSearchService.keysFor("board", on);
    }

    private static FrameLayout frame(Context c, View v) {
        FrameLayout f = new FrameLayout(c);
        f.addView(v, new FrameLayout.LayoutParams(1, 1));
        return f;
    }

    private static WindowManager.LayoutParams params(int w, int h, boolean focus) {
        // Not FLAG_NOT_TOUCHABLE: Android 12 and later turn such an overlay down to 80% (it is meant to let touches
        // through), and the channel's own scoreboard then showed through the board. A TV has no touch screen, and not
        // focusable keeps every key with the app, as the alerts do.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                (focus ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("BoardCover");
        return lp;
    }

    private static boolean addWindow(Context c, View win) {
        try {
            c.getSystemService(WindowManager.class).addView(win, params(1, 1, false));
            return true;
        } catch (RuntimeException e) {
            AlertLog.note(c, "the scoreboard could not show: " + e);
            return false;
        }
    }

    private static void removeWindow(Context c, View win) {
        if (win == null || c == null) return;
        try {
            c.getSystemService(WindowManager.class).removeView(win);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }

    /** Both away at once (Home, another app, switched off): what was out beside it with them. */
    private static void hideAll() {
        Context c = app;
        if (main != null) {
            main.removeCallbacks(nextDropRun);
            main.removeCallbacks(hideDropRun);
        }
        drops.clear();
        dropping = false;
        if (stats != null) stats.gone();
        if (drop != null) drop.gone();
        holdKeys(false);
        if (main != null) main.removeCallbacks(menuOnHold);
        okDown = -1;
        removeWindow(c, boardWin);
        removeWindow(c, logoWin);
        boardWin = null;
        logoWin = null;
        column = null;
        bug = null;
        stats = null;
        drop = null;
        mark = null;
        imagesFor = null;
        shownScore = null;
    }

    /**
     * Away as a broadcaster's goes (before kick-off, half time, full time shown): what is out beside it back in, the
     * board wiped back into its first block and closed to a line, then both windows gone.
     */
    private static void closeAll() {
        if (boardWin == null) return;
        if (bug == null || column == null || column.getVisibility() != View.VISIBLE || adjusting) {
            hideAll();
            return;
        }
        if (bug.closing()) return;
        if (stats != null) stats.fold(null);
        if (drop != null) drop.fold(null);
        bug.close(BoardCover::hideAll);
    }

    /**
     * Both windows where the places put them, the board as wide as its words need for its height (or as wide as Move and
     * resize's Height kept it, filling that box).
     */
    private static void layout(Context c) {
        if (boardWin == null || bug == null) return;
        BoardPlace p = adjusting && editing != null ? editing : place;
        if (p == null) p = BoardPlace.standard();
        // Stacked is drawn taller than the height set (its clubs one above the other), and kept on the screen.
        int bh = Math.max(8, Math.round(p.bh * sh * bug.style().tall));
        int bw = p.bw > 0 ? Math.max(bh * 2, Math.min(Math.round(sw * 0.6f), Math.round(p.bw * sw)))
                : Math.min(Math.round(sw * 0.5f), bug.natural(bh));
        int bx = Math.min(Math.round(p.bx * sw), sw - bw), by = Math.min(Math.round(p.by * sh), sh - bh);
        boardH = Math.max(8, Math.round(p.bh * sh));
        positionBoard(c, Math.max(0, bx), Math.max(0, by), bw, bh);
        if (logoWin != null && mark != null) {
            position(c, logoWin, mark, Math.round(p.lx * sw), Math.round(p.ly * sh), Math.round(p.lw * sw), Math.round(p.lh * sh));
        }
    }

    /**
     * One at its place (x, y, w, h): the view that big with its edge round it, in a window with room besides for the
     * burn-in step, so the view never moves out of it.
     */
    private static void position(Context c, FrameLayout win, View v, int x, int y, int w, int h) {
        int vw = w + 2 * edge, vh = h + 2 * edge;
        FrameLayout.LayoutParams vl = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (vl.width != vw || vl.height != vh || vl.leftMargin != edge || vl.topMargin != edge) {
            vl.width = vw;
            vl.height = vh;
            vl.leftMargin = edge;
            vl.topMargin = edge;
            v.setLayoutParams(vl);
        }
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) win.getLayoutParams();
        int wx = x - 2 * edge, wy = y - 2 * edge, ww = w + 4 * edge, wh = h + 4 * edge;
        if (lp == null || (lp.x == wx && lp.y == wy && lp.width == ww && lp.height == wh)) return;
        lp.x = wx;
        lp.y = wy;
        lp.width = ww;
        lp.height = wh;
        try {
            c.getSystemService(WindowManager.class).updateViewLayout(win, lp);
        } catch (RuntimeException ignored) {
            // the window went meanwhile
        }
    }

    /**
     * The board at (x, y, w, h) with its edge round it, in a window with room besides for the burn-in step and, beside the
     * board and in line with it, for what slides out of it: out to its right, or to its left when there is more room there
     * (a board nearer the screen's right), short of the logo's cover where it is level with the board, and no wider than
     * STRIP_W of its heights. A size that holds while the game goes on, so the window over the playing picture is not
     * made again for each alert.
     */
    private static void positionBoard(Context c, int x, int y, int w, int h) {
        int vw = w + 2 * edge, vh = h + 2 * edge, unit = boardH;
        // Room over the board for the scorer's picture to rise above an alert (what there is, up to over half its height).
        head = Math.max(0, Math.min(Math.round(vh * 0.6f), y - 2 * edge));
        boardRect.set(x - edge, y - edge, x + w + edge, y + h + edge);
        BoardBug.StripLook look = bug.stripLook();
        tuck = look.apart ? 0 : Math.max(2, Math.round(vh * look.round)) + 1;
        sideGap = look.apart ? Math.max(2, Math.round(unit * 0.1f)) : 0;
        int margin = Math.round(sw * 0.02f), left = x - edge, right = x + w + edge;
        int roomR = sw - margin - right, roomL = left - margin;
        BoardPlace p = adjusting && editing != null ? editing : place;
        if (logoWin != null && p != null) {
            int lx = Math.round(p.lx * sw), ly = Math.round(p.ly * sh), lw = Math.round(p.lw * sw), lh = Math.round(p.lh * sh);
            boolean level = ly < y + h && ly + lh > y;
            if (level && lx >= right) roomR = Math.min(roomR, lx - margin / 2 - right);
            if (level && lx + lw <= left) roomL = Math.min(roomL, left - lx - lw - margin / 2);
        }
        sideLeft = roomR < unit * 6 && roomL > roomR;
        int room = Math.max(unit * 3, Math.min(Math.round(unit * STRIP_W), sideLeft ? roomL : roomR) - sideGap);
        sideMax = room + tuck;
        boardAt = sideLeft ? room + sideGap : 0;
        sizeAt(bug, vw, vh, boardAt, head);
        if (stats.isOut()) beside(stats, stats.getLayoutParams().width);
        if (drop.isOut()) beside(drop, drop.getLayoutParams().width);
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) boardWin.getLayoutParams();
        int wx = x - 2 * edge - boardAt, wy = y - 2 * edge - head, ww = boardAt + vw + (sideLeft ? 0 : sideGap + room) + 2 * edge,
                wh = head + vh + 2 * edge;
        if (lp == null || (lp.x == wx && lp.y == wy && lp.width == ww && lp.height == wh)) return;
        lp.x = wx;
        lp.y = wy;
        lp.width = ww;
        lp.height = wh;
        try {
            c.getSystemService(WindowManager.class).updateViewLayout(boardWin, lp);
        } catch (RuntimeException ignored) {
            // the window went meanwhile
        }
    }

    private static void sizeAt(View v, int w, int h, int left, int top) {
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (l.width == w && l.height == h && l.leftMargin == left && l.topMargin == top) return;
        l.width = w;
        l.height = h;
        l.leftMargin = left;
        l.topMargin = top;
        v.setLayoutParams(l);
    }

    /**
     * What slides out (the stats, an alert) w wide, as high as the board and beside it, its near end tucked under it; an
     * alert with the room over the board as well, for the scorer's picture to rise into.
     */
    private static void beside(View v, int w) {
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) bug.getLayoutParams();
        int left = sideLeft ? boardAt + tuck - sideGap - w : boardAt + b.width - tuck + sideGap;
        if (v == drop) sizeAt(v, w, b.height + head, left, 0);
        else sizeAt(v, w, b.height, left, head);
    }

    /**
     * Where a card w by h stands beside the board, in line with it (its top level with the board's), on the side with
     * room, short of the logo's cover, else under the board: {left, top} on the screen; null when the board is not up.
     */
    static int[] besideBoard(int w, int h) {
        if (boardWin == null || bug == null || column == null || column.getVisibility() != View.VISIBLE || bug.closing()) return null;
        int gap = Math.max(4, Math.round(boardH * 0.2f)), margin = Math.round(sw * 0.02f);
        int right = sw - margin, left = margin;
        BoardPlace p = adjusting && editing != null ? editing : place;
        if (logoWin != null && p != null) {
            int lx = Math.round(p.lx * sw), ly = Math.round(p.ly * sh), lw = Math.round(p.lw * sw), lh = Math.round(p.lh * sh);
            boolean level = ly < boardRect.top + h && ly + lh > boardRect.top;
            if (level && lx >= boardRect.right) right = Math.min(right, lx - margin / 2);
            if (level && lx + lw <= boardRect.left) left = Math.max(left, lx + lw + margin / 2);
        }
        int top = Math.max(0, Math.min(boardRect.top, sh - h));
        if (boardRect.right + gap + w <= right) return new int[]{boardRect.right + gap, top};
        if (boardRect.left - gap - w >= left) return new int[]{boardRect.left - gap - w, top};
        return new int[]{Math.max(margin, Math.min(boardRect.left, sw - margin - w)), Math.min(boardRect.bottom + gap, sh - h)};
    }

    /** The board's place on the screen, its edge round it (for a card beside it). */
    private static final android.graphics.Rect boardRect = new android.graphics.Rect();

    /** The board's left on the screen (a card to its left comes in from the right). */
    static int boardLeft() {
        return boardRect.left;
    }

    // ---- what slides out beside it: the alerts --------------------------------------------------------------------------

    private static final ArrayDeque<BoardDrop.Drop> drops = new ArrayDeque<>();
    private static boolean dropping;
    private static final Runnable nextDropRun = BoardCover::nextDrop, hideDropRun = BoardCover::hideDrop;

    private static void drop(Context c, BoardDrop.Drop d) {
        // A goal is captioned once: from its card, or (no card come) from the scores.
        if (d.key != null && !captioned.add(d.key)) return;
        if (captioned.size() > 200) captioned.clear();
        // The board's own game: the board beside it says the clubs and the score already.
        d.own = d.gameId != null && d.gameId.equals(game(c));
        drops.addLast(d);
        while (drops.size() > 4) drops.removeFirst();
        if (d.own && bug != null && "VAR".equals(d.chip)) bug.varCheck(8_000L);
        AlertLog.note(c, "alert from the scoreboard: " + d.what);
        if (d.gameId != null && d.gameId.equals(game(c))) poke();
        if (!dropping) nextDrop();
    }

    /**
     * The next alert out from beside the board: the stats in first (it comes in their place), and after a goal's sweep on
     * the board (as broadcasters caption a goal once they have shown it).
     */
    private static void nextDrop() {
        main().removeCallbacks(nextDropRun);
        if (drops.isEmpty() || drop == null || bug == null || column == null) {
            drops.clear();
            dropping = false;
            return;
        }
        dropping = true;
        if (stats != null && stats.isOut()) {
            stats.fold(() -> main().post(nextDropRun));
            return;
        }
        // The line-up card beside the board: the alert waits for it.
        if (LineupCard.isShowing()) {
            main().postDelayed(nextDropRun, 1_500L);
            return;
        }
        long wait = bug.bandLeft(System.currentTimeMillis());
        if (wait > 0) {
            main().postDelayed(nextDropRun, wait);
            return;
        }
        BoardDrop.Drop d = drops.pollFirst();
        poke();
        drop.place(bug.stripLook(), boardH, tuck, sideLeft, head);
        beside(drop, drop.widthFor(d, bug.getLayoutParams().height, sideMax));
        drop.show(d);
        main().postDelayed(hideDropRun, drop.showFor(DROP_MS));
    }

    private static void hideDrop() {
        main().removeCallbacks(hideDropRun);
        if (drop == null || !drop.isOut()) {
            nextDrop();
            return;
        }
        drop.fold(BoardCover::nextDrop);
    }

    // ---- a goal's scorer, captioned beside it --------------------------------------------------------------------------

    /** The goals captioned beside the board, "game|score": each once, from its card or else from the scores. */
    private static final java.util.Set<String> captioned = new java.util.HashSet<>();
    /** The goal on the board waiting for its caption ("game|score"), its side, and its scorer's picture once found. */
    private static String scorerFor;
    private static int scorerSide, scorerTries;
    private static Bitmap scorerPhoto;
    private static final Runnable ownScorerRun = BoardCover::ownScorer;

    /**
     * A goal on the board (main thread): its caption comes with its card (the scorer's picture, who and when); should no
     * card come by just after the goal's sweep (a game the alerts don't follow), the board captions it itself from the
     * scores, the scorer's picture looked up meanwhile.
     */
    private static void expectScorer(Context c, ScoresTicker.Game g, String score, int side) {
        scorerFor = g.id + "|" + score;
        scorerSide = side;
        scorerTries = 0;
        scorerPhoto = null;
        main().removeCallbacks(ownScorerRun);
        main().postDelayed(ownScorerRun, BoardBug.BAND_OUT + BoardBug.BAND_MS + 2_000L);
        String[] who = scorerOf(g.goals, side == 1 ? "home" : "away", goalsOf(score, side));
        if (who == null || who[1].isEmpty()) return;
        String key = scorerFor, team = side == 1 ? g.homeName : g.awayName;
        int kit = side == 1 ? g.homeColor : g.awayColor;
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Bitmap b = GoalWatchService.photoFor(who[1], team, kit);
            main().post(() -> {
                if (key.equals(scorerFor)) scorerPhoto = b;
            });
        });
    }

    /** Pure: a side's goals in a score ("2–1", side 1: 2); -1 when it is not a score. */
    static int goalsOf(String score, int side) {
        String[] g = score == null ? new String[0] : score.split("\\s*[–-]\\s*");
        if (g.length != 2) return -1;
        try {
            return Integer.parseInt(g[side == 1 ? 0 : 1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Pure: the scores' entry for a side's n-th goal ({key, scorer, minute, side, kind}, ESPN listing an own goal for the
     * side it counted for), null when the scores do not have it yet.
     */
    static String[] scorerOf(List<String[]> goals, String side, int n) {
        if (goals == null || n <= 0) return null;
        int seen = 0;
        for (String[] x : goals) {
            if (x == null || x.length < 5 || !side.equals(x[3])) continue;
            if (++seen == n) return x;
        }
        return null;
    }

    /** Pure: a scorer's line as the cards have it: "Bukayo Saka 67'", "… 52' · penalty", "… 30' · own goal". */
    static String scorerLine(String[] goal) {
        if (goal == null) return "";
        return (goal[1] + " " + goal[2]).trim() + ("pen".equals(goal[4]) ? " · penalty" : "og".equals(goal[4]) ? " · own goal" : "");
    }

    /** No card came for the goal on the board: its caption from the scores (asked again a few times while they lag). */
    private static void ownScorer() {
        Context c = app;
        String key = scorerFor;
        if (c == null || key == null || captioned.contains(key) || bug == null) return;
        String id = key.substring(0, key.indexOf('|')), score = key.substring(key.indexOf('|') + 1);
        ScoresTicker.Game g = GoalWatchService.latestGame(id);
        if (g == null || !id.equals(game(c))) return;
        String[] goal = scorerOf(g.goals, scorerSide == 1 ? "home" : "away", goalsOf(score, scorerSide));
        if (goal == null && ++scorerTries < 4) {
            main().postDelayed(ownScorerRun, 3_000L);
            return;
        }
        BoardDrop.Drop d = new BoardDrop.Drop();
        d.gameId = id;
        d.key = key;
        d.chip = "GOAL";
        d.colour = kindColour("goal");
        String home = g.homeName != null ? g.homeName : nz(g.home), away = g.awayName != null ? g.awayName : nz(g.away);
        d.title = home + " " + score + " " + away;
        d.line = scorerLine(goal);
        d.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        d.side = scorerSide;
        d.photo = scorerPhoto;
        d.homeCrest = homeCrest;
        d.awayCrest = awayCrest;
        d.what = ("GOAL " + d.title + " " + d.line).trim();
        drop(c, d);
    }

    // ---- what slides out beside it: the stats --------------------------------------------------------------------------

    /** The game's stats and facts read last, by their names ("Possession", "Form"…), each {name, home, away[, kind]}. */
    private static Map<String, String[]> statsBy = new HashMap<>();
    private static String statsFor = "";
    private static long statsReadAt, goalShownAt;
    private static boolean statsReading;
    /** The stats moments shown, "game|moment": each once a game. */
    private static final java.util.Set<String> statsShown = new java.util.HashSet<>();

    /**
     * The game's match summary read again every two minutes while the board is up (off the main thread): its stats and
     * facts, and the added time put up in each half.
     */
    private static void readStats(Context c, ScoresTicker.Game g, long now) {
        if (statsReading || g.slug == null || g.id == null) return;
        // Near a half's end, every half a minute: the added time put up comes on the board soon after.
        long every = nearHalfsEnd(minuteOfClock(bug == null ? null : bug.look().clock)) ? 30_000L : STATS_READ_MS;
        if (g.id.equals(statsFor) && now - statsReadAt < every) return;
        statsReading = true;
        statsReadAt = now;
        String id = g.id, slug = g.slug;
        boolean fotmob = fotmobOn(c);
        io.execute(() -> {
            Map<String, String[]> got = null;
            try {
                org.json.JSONObject summary = new org.json.JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                        + slug + "/summary?event=" + id));
                announced.put(id, announcedOf(summary));
                got = statsOf(summary, g);
            } catch (Exception ignored) {
                // read again in two minutes
            }
            // FotMob's figures on top: expected goals, big chances, ratings, who is on top, its player of the match.
            FotMob.Match fm = null;
            if (fotmob) {
                fm = FotMob.forGame(id, g.homeName, g.awayName, g.kickoff);
                if (fm != null) {
                    if (got == null) got = new HashMap<>();
                    got.putAll(FotMob.statsOf(fm, code(g.home, g.homeName), code(g.away, g.awayName)));
                }
            }
            Map<String, String[]> by = got;
            FotMob.Match read = fm;
            main().post(() -> {
                statsReading = false;
                if (read != null) fotmobRead(c, g, read);
                if (by == null || by.isEmpty()) return;
                statsBy = by;
                statsFor = id;
            });
        });
    }

    /** The substitutions seen, "game|minute|who came on": each slides out once, those before the board came up never. */
    private static final java.util.Set<String> subsSeen = new java.util.HashSet<>();
    private static final java.util.Set<String> subsGames = new java.util.HashSet<>();

    /** FotMob's game read (main thread): its VAR check on the board, and each new substitution beside it. */
    private static void fotmobRead(Context c, ScoresTicker.Game g, FotMob.Match m) {
        boolean own = g.id.equals(game(c));
        if (own && m.varPending && bug != null) bug.varCheck(8_000L);
        boolean first = subsGames.add(g.id);
        if (subsGames.size() > 40) subsGames.clear();
        for (String[] e : m.events) {
            if (!"Substitution".equals(e[0])) continue;
            String key = g.id + "|" + e[1] + "|" + e[4];
            if (!subsSeen.add(key) || first) continue;
            if (subsSeen.size() > 400) subsSeen.clear();
            if (!own || !dropsOn(c) || !subsOn(c)) continue;
            boolean home = "1".equals(e[2]);
            String team = home ? g.homeName : g.awayName;
            int kit = home ? g.homeColor : g.awayColor;
            BoardDrop.Drop d = subDrop(g, e);
            Context a = c.getApplicationContext();
            io.execute(() -> {
                Bitmap b = GoalWatchService.photoFor(e[3], team, kit);
                if (b == null) b = fotmobPhoto(a, e[4]);
                Bitmap photo = b;
                main().post(() -> {
                    d.photo = photo;
                    d.homeCrest = homeCrest;
                    d.awayCrest = awayCrest;
                    drop(a, d);
                });
            });
        }
    }

    /** Pure: a substitution's alert ({type, minute, home, on, on id, off, off id}): who came on, who went off, when. */
    static BoardDrop.Drop subDrop(ScoresTicker.Game g, String[] e) {
        BoardDrop.Drop d = new BoardDrop.Drop();
        boolean home = "1".equals(e[2]);
        d.gameId = g.id;
        d.chip = "SUBSTITUTION";
        d.colour = kindColour("sub");
        String team = home ? (g.homeName != null ? g.homeName : nz(g.home)) : (g.awayName != null ? g.awayName : nz(g.away));
        d.title = team;
        d.line = join("On " + nz(e[3]), "off " + nz(e[5]), nz(e[1]).isEmpty() ? "" : e[1] + "'");
        d.homeColour = BoardBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = BoardBug.colour(g.awayColor, g.awayAlt);
        d.side = home ? 1 : 2;
        d.what = ("SUBSTITUTION " + team + " " + d.line).trim();
        return d;
    }

    /** A player's FotMob picture by its id (off the main thread); null when there is none. */
    static Bitmap fotmobPhoto(Context c, String id) {
        try {
            int n = Integer.parseInt(id == null ? "" : id.trim());
            return n > 0 ? ScoresTicker.cachedLogo(c, FotMob.photoUrl(n), 350) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Pure: which of the game's few stats moments it is (null: none): its last ten minutes before kick-off ("pre"), the
     * middle of the first half ("h1", minutes 25 to 41), half time ("ht"), the middle of the second half ("h2", 64 to
     * 82) and full time ("ft"). Each comes once a game: enough to tell its story, never so often it is in the way.
     */
    static String statsSlot(boolean pre, long toKickoff, int state, boolean halftime, int minute) {
        if (pre) return toKickoff > 60_000L && toKickoff <= PRE_STATS_MS ? "pre" : null;
        if (state == 2) return "ft";
        if (halftime) return "ht";
        if (state != 0) return null;
        if (minute >= 25 && minute <= 41) return "h1";
        if (minute >= 64 && minute <= 82) return "h2";
        return null;
    }

    /**
     * Pure: what a stats moment shows, from the stats and facts there are, by name: before kick-off the chances, the
     * form and the table; in the first half the possession and the shots; at half time five of the half's; in the second
     * half the player of the match so far; at full time the game's in short. Empty when too few are there yet.
     */
    static List<String[]> statsFor(String slot, Map<String, String[]> by) {
        String[] want;
        int most = 3, least = 2;
        switch (slot == null ? "" : slot) {
            case "pre":
                want = new String[]{"Win chance", "Form", "League table"};
                least = 1;
                break;
            case "h1":
                want = new String[]{"Possession", "Expected goals", "Shots", "On target", "Momentum", "Corners"};
                most = 4;
                break;
            case "ht":
                want = new String[]{"Possession", "Expected goals", "Shots", "Big chances", "On target", "Top rated", "Corners",
                        "Pass accuracy", "Fouls"};
                most = 5;
                break;
            case "h2":
                want = new String[]{"Expected goals", "Momentum", "Possession", "Touches in box", "Player of the match", "Corners",
                        "On target", "Shots"};
                most = 4;
                break;
            case "ft":
                want = new String[]{"Expected goals", "Possession", "Shots", "Big chances", "Player of the match", "Distance run",
                        "Insight", "Pass accuracy", "On target"};
                most = 5;
                break;
            default:
                return new ArrayList<>();
        }
        List<String[]> out = new ArrayList<>();
        for (String w : want) {
            String[] s = by.get(w);
            if (s != null && out.size() < most) out.add(s);
        }
        return out.size() >= least ? out : new ArrayList<>();
    }

    /** Pure: whether a clock's minute is near a half's end, when the added time is put up (42 to 52, 87 to 100, extra time's). */
    static boolean nearHalfsEnd(int minute) {
        return minute >= 42 && minute <= 52 || minute >= 87 && minute <= 100 || minute >= 102 && minute <= 110
                || minute >= 117 && minute <= 126;
    }

    /** Pure: the minute a clock shows ("67:23": 67); -1 when it shows none ("HT", ""). */
    static int minuteOfClock(String clock) {
        int colon = clock == null ? -1 : clock.indexOf(':');
        if (colon <= 0) return -1;
        try {
            return Integer.parseInt(clock.substring(0, colon).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * The game's stats sliding out from beside it at one of its few moments (statsSlot). Never while an alert is out,
     * just after a goal, or while the scores have a goal the picture hasn't shown yet (its shot on target would give it
     * away).
     */
    private static void maybeStats(Context c, ScoresTicker.Game g, Reading r, long now, boolean pre) {
        if (!statsOn(c) || dropping || stats == null || stats.isOut() || bug == null || bug.closing() || bug.getWidth() <= 0
                || LineupCard.isShowing()) return;
        if (!g.id.equals(statsFor) || statsBy.isEmpty() || now - goalShownAt < 90_000L) return;
        if (!pre && (r == null || ahead(g.id, r))) return;
        int minute = minuteOfClock(bug.look().clock);
        if (minute < 0 && r != null) minute = minuteOf(r.detail);
        String slot = statsSlot(pre, g.kickoff - now, r == null ? 1 : r.state, r != null && r.halftime, minute);
        if (slot == null || statsShown.contains(g.id + "|" + slot)) return;
        List<String[]> pick = statsFor(slot, statsBy);
        if (pick.isEmpty()) return;
        if (statsShown.size() > 60) statsShown.clear();
        statsShown.add(g.id + "|" + slot);
        poke();
        stats.place(bug.stripLook(), boardH, tuck, sideLeft);
        beside(stats, stats.widthFor(pick, bug.getLayoutParams().height, sideMax));
        stats.show(pick);
    }

    /** Whether the scores have a goal the screen hasn't shown yet: their last reading's score not the one on the screen. */
    private static boolean ahead(String id, Reading screen) {
        ArrayDeque<Reading> list = readings.get(id);
        Reading last = list == null ? null : list.peekLast();
        return last != null && screen != null && (!last.home.equals(screen.home) || !last.away.equals(screen.away));
    }

    // ---- crests ----------------------------------------------------------------------------------------------------

    private static android.util.LruCache<String, Bitmap> images;

    private static synchronized android.util.LruCache<String, Bitmap> images() {
        if (images == null) {
            images = new android.util.LruCache<String, Bitmap>(3 * 1024 * 1024) {
                @Override protected int sizeOf(String k, Bitmap b) {
                    return b.getByteCount();
                }
            };
        }
        return images;
    }

    /** The game's crests and competition logo onto the board, read off the main thread. */
    private static void loadImages(Context c, ScoresTicker.Game g) {
        imagesFor = g.id;
        String id = g.id, home = g.homeLogo, away = g.awayLogo;
        String league = g.leagueLogo != null ? g.leagueLogo : GoalWatchService.leagueLogoFor(g);
        Context a = c.getApplicationContext();
        io.execute(() -> {
            Bitmap h = crest(a, home), w = crest(a, away), l = compLogo(a, league);
            main().post(() -> {
                if (!id.equals(imagesFor)) return;
                homeCrest = h;
                awayCrest = w;
                compImage = l;
                compTag = g.league == null ? "" : g.league;
                if (bug != null) bug.images(h, w, l);
                if (mark != null) mark.competition(l, compTag);
            });
        });
    }

    private static Bitmap crest(Context c, String url) {
        if (url == null) return null;
        Bitmap have = images().get(url);
        if (have != null) return have;
        Bitmap b = ScoresTicker.cachedLogo(c, url, 96);
        if (b == null) return null;
        Bitmap n = ScoreCard.onNavy(b);
        if (n != null) images().put(url, n);
        return n;
    }

    /** A competition's logo for the dark block: ESPN's for dark grounds; the Premier League's lion alone, in white. */
    private static Bitmap compLogo(Context c, String url) {
        if (url == null) return null;
        String key = "comp|" + url;
        Bitmap have = images().get(key);
        if (have != null) return have;
        Bitmap b;
        if (GoalWatchService.premier(url)) {
            Bitmap full = ScoresTicker.cachedLogo(c, url.replace("/500-dark/", "/500/"), 160);
            b = full == null ? null : GoalWatchService.lionOnly(full, true);
        } else {
            Bitmap dark = url.contains("/500/") ? ScoresTicker.cachedLogo(c, url.replace("/500/", "/500-dark/"), 128) : null;
            b = ScoreCard.onNavy(dark != null ? dark : ScoresTicker.cachedLogo(c, url, 128));
        }
        if (b != null) images().put(key, b);
        return b;
    }

    // ---- kind to the screen ----------------------------------------------------------------------------------------

    private static int step;
    private static long stepAt, changedAt;
    private static boolean dimmed;

    /** Pure: step n's place on the circle, {x, y} each -1, 0 or 1: nine places, back to the start. */
    static int[] orbit(int n) {
        int[][] path = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        int[] p = path[((n % path.length) + path.length) % path.length];
        return new int[]{p[0], p[1]};
    }

    /** Pure: whether it is dimmed, changedAt ms ago at now. */
    static boolean dims(long changedAt, long now) {
        return now - changedAt >= DIM_AFTER_MS;
    }

    /** Each second: a step round the circle once a minute (still while being placed), dimmer when nothing happens. */
    private static void guard(long now) {
        if (adjusting) {
            setDim(false);
            return;
        }
        if (now - stepAt >= STEP_MS) {
            stepAt = now;
            step++;
            int[] o = orbit(step);
            move(column, o);
            move(mark, o);
        }
        setDim(dims(changedAt, now));
    }

    private static void move(View v, int[] o) {
        if (v != null) v.animate().translationX(o[0] * edge).translationY(o[1] * edge).setDuration(1500).start();
    }

    /** Something happened on it (a goal, full time, Move and resize): bright again, five more minutes before it dims. */
    private static void poke() {
        changedAt = System.currentTimeMillis();
        setDim(false);
    }

    private static void setDim(boolean on) {
        if (on == dimmed) return;
        dimmed = on;
        dimLayer(bug, on);
        dimLayer(mark, on);
    }

    /** A view dimmed through its layer: solid, darker, never see-through. */
    private static void dimLayer(View v, boolean on) {
        if (v == null) return;
        if (!on) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            return;
        }
        ColorMatrix m = new ColorMatrix();
        m.setScale(DIM, DIM, DIM, 1f);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(m));
        v.setLayerType(View.LAYER_TYPE_HARDWARE, p);
    }

    // ---- Move and resize -------------------------------------------------------------------------------------------

    private static boolean adjusting, keysTaken;
    private static long adjustAt;
    /** What the arrows do: 0 move the board, 1 size it, 2 its height, 3 its timing, 4 move the logo's cover, 5 size it. */
    private static int part;
    private static BoardPlace editing;
    private static String editingChannel = "";

    static boolean adjusting() {
        return adjusting;
    }

    /** Move and resize over the channel (the Scoreboard card): the channel app brought back, then both ringed to place. */
    static void adjust(Context c) {
        app = c.getApplicationContext();
        if (adjusting || !Settings.canDrawOverlays(app)) return;
        Intent open = null;
        // Brought back as it was (no fresh start): full screen on the channel last watched.
        for (String p : PLAYERS) {
            open = app.getPackageManager().getLeanbackLaunchIntentForPackage(p);
            if (open == null) open = app.getPackageManager().getLaunchIntentForPackage(p);
            if (open != null) break;
        }
        if (open != null) {
            try {
                app.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException e) {
                open = null;
            }
        }
        // The app's own Home leaving first: Home in front would end it at once.
        main().postDelayed(BoardCover::beginAdjust, open != null ? 1_800L : 0L);
    }

    private static void beginAdjust() {
        Context c = app;
        if (c == null || adjusting) return;
        adjusting = true;
        adjustAt = SystemClock.uptimeMillis();
        part = 0;
        editingChannel = InStep.channel(c);
        editing = BoardPlace.get(c, editingChannel).copy();
        hideAll();
        if (!ensureWindows(c)) {
            adjusting = false;
            editing = null;
            return;
        }
        if (column != null) {
            column.setTranslationX(0);
            column.setTranslationY(0);
        }
        if (mark != null) {
            mark.setTranslationX(0);
            mark.setTranslationY(0);
        }
        showHint(c);
        outline();
        AlertLog.note(c, "scoreboard: Move and resize" + (editingChannel.isEmpty() ? "" : " for " + editingChannel));
        main().removeCallbacks(tick);
        main().post(tick);
    }

    /** The hint along the foot, and the remote's keys taken while it shows. */
    private static void showHint(Context c) {
        float u = sw / 1920f;
        hint = new TextView(c);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f * u);
        hint.setTextColor(BoardBug.INK);
        hint.setTypeface(c.getResources().getFont(R.font.inter_semibold));
        int px = Math.round(30 * u), py = Math.round(16 * u);
        hint.setPadding(px, py, px, py);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00B0F24);
        bg.setCornerRadius(60 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x40FFFFFF);
        hint.setBackground(bg);
        hint.setGravity(Gravity.CENTER);
        hintWin = new FrameLayout(c);
        hintWin.addView(hint, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        // The keys: the key helper's (the Streamer), else the channel helper takes them while this is open (the TCL),
        // else this window takes the focus.
        keysTaken = !InputKeyService.running && !AppSearchService.running();
        WindowManager.LayoutParams lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, keysTaken);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = Math.round(sh * 0.07f);
        try {
            c.getSystemService(WindowManager.class).addView(hintWin, lp);
        } catch (RuntimeException e) {
            hintWin = null;
        }
        if (!InputKeyService.running && !keysTaken) AppSearchService.keysFor("cover", true);
        if (keysTaken && hintWin != null) {
            hintWin.setFocusable(true);
            hintWin.setFocusableInTouchMode(true);
            hintWin.requestFocus();
            hintWin.setOnKeyListener((v, code, e) -> key(e));
        }
        hintWords();
    }

    /** Pure: the hint for each part ("logo": the logo's cover is on, so its parts come next). */
    static String hintFor(int part, boolean logo) {
        switch (part) {
            case 1:
                return "Sizing the scoreboard  ·  Up bigger, Down smaller  ·  OK: its height  ·  Back: keep it";
            case 2:
                return "The scoreboard's height  ·  Up taller, Down flatter, its width kept  ·  OK: its timing  ·  Back: keep it";
            case 3:
                return "Timing  ·  Right a second later, Left a second sooner, Down and Up five  ·  OK: "
                        + (logo ? "the logo's cover" : "move it") + "  ·  Back: keep it";
            case 4:
                return "Moving the cover over the channel's logo  ·  arrows move it  ·  OK: its size  ·  Back: keep it";
            case 5:
                return "Sizing the logo's cover  ·  Left and Right its width, Up and Down its height  ·  OK: the scoreboard"
                        + "  ·  Back: keep it";
            default:
                return "Moving the scoreboard  ·  arrows move it  ·  OK: its size  ·  Back: keep it";
        }
    }

    private static void hintWords() {
        if (hint == null) return;
        String s = hintFor(part, mark != null);
        if (part == 3 && app != null) s = timingWords(app) + "  ·  " + s;
        Keys.set(hint, s);
    }

    /** The one being placed ringed. */
    private static void outline() {
        if (bug != null) bug.outline(adjusting && part <= 3);
        if (mark != null) mark.outline(adjusting && part >= 4);
    }

    /**
     * The remote while Move and resize is open (from the key helper, or the channel helper on a TV without it): the
     * arrows move or size, OK the next part, Back keeps the places; the rest (the volume) go on to the TV.
     */
    static boolean key(KeyEvent e) {
        if (menuWin != null) return menuKey(e);
        if (!adjusting) return holdOk(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (up) main().post(() -> endAdjust(true));
            return true;
        }
        if (QuietHold.isOk(code)) {
            if (up) main().post(BoardCover::nextPart);
            return true;
        }
        int dx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int dy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (dx != 0 || dy != 0) {
            int repeat = e.getRepeatCount();
            if (down) main().post(() -> nudge(dx, dy, repeat));
            return true;
        }
        return false;
    }

    private static void nextPart() {
        if (!adjusting) return;
        part = (part + 1) % (mark != null ? 6 : 4);
        outline();
        hintWords();
    }

    /** One press: about six pixels' move (four times that while held), or a size step of 4%. */
    private static void nudge(int dx, int dy, int repeat) {
        if (!adjusting || editing == null || app == null) return;
        float fast = repeat >= 3 ? 4f : 1f;
        float x = dx * 6f * fast / 1920f, y = dy * 6f * fast / 1080f;
        float aspect = aspect();
        switch (part) {
            case 0:
                editing.moveBoard(x, y, aspect);
                break;
            case 1:
                editing.sizeBoard(dy < 0 || dx > 0 ? 1.04f : 1 / 1.04f, aspect);
                break;
            case 2:
                if (dy == 0) return;
                // Taller or flatter, its width kept as it is now: the board fills that box (two rows when near square).
                editing.heightBoard(dy < 0 ? 1.04f : 1 / 1.04f, bug == null ? 0f : Math.max(0f, bug.getWidth() - 2f * edge) / sw);
                break;
            case 3:
                timing(app, dx * 1_000L + (dy > 0 ? 5_000L : dy < 0 ? -5_000L : 0L));
                hintWords();
                return;
            case 4:
                editing.moveLogo(x, y);
                break;
            default:
                editing.sizeLogo(dx > 0 ? 1.04f : dx < 0 ? 1 / 1.04f : 1f, dy < 0 ? 1.04f : dy > 0 ? 1 / 1.04f : 1f);
        }
        layout(app);
    }

    /** The board's width as a share of the screen's, for each share of its height set (Stacked drawn taller). */
    private static float aspect() {
        if (editing.bw > 0) return editing.bw / Math.max(0.001f, editing.bh);
        float tall = bug == null ? 1f : bug.style().tall;
        int h = Math.max(1, Math.round(editing.bh * sh * tall));
        float ratio = bug == null ? 9f : bug.natural(h) / (float) h;
        return ratio * tall * sh / (float) sw;
    }

    /** Move and resize closed: the places kept for the channel (save), the windows as the game has them again. */
    private static void endAdjust(boolean save) {
        if (!adjusting) return;
        adjusting = false;
        Context c = app;
        if (save && editing != null && c != null) {
            BoardPlace.put(c, editingChannel, editing);
            place = editing.copy();
            placeKey = BoardPlace.key(editingChannel);
            AlertLog.note(c, "scoreboard places kept" + (editingChannel.isEmpty() ? "" : " for " + editingChannel) + ": " + editing.encode());
            Toast.makeText(c, editingChannel.isEmpty() ? "Scoreboard places kept" : "Scoreboard places kept for " + editingChannel,
                    Toast.LENGTH_SHORT).show();
        }
        editing = null;
        removeWindow(c, hintWin);
        hintWin = null;
        hint = null;
        if (!InputKeyService.running && !keysTaken) AppSearchService.keysFor("cover", false);
        keysTaken = false;
        hideAll();
        if (c != null) resume(c);
    }

    // ---- its menu over the match: OK held -----------------------------------------------------------------------------

    private static FrameLayout menuWin;
    private static LinearLayout menuRows;
    private static int menuAt;
    /** The OK being watched for a hold (its press's time), and whether the hold opened the menu. */
    private static long okDown = -1, menuOkDown = -1;
    private static boolean okFired;
    private static final long MENU_MS = 15_000L;
    private static final Runnable menuOnHold = () -> {
        if (okDown < 0) return;
        okFired = true;
        menuOkDown = okDown;
        openMenu();
    };
    private static final Runnable menuAway = BoardCover::closeMenu;

    /**
     * OK while the board is up over the match: held, its menu; a short press goes on to the app as it was meant (on
     * its release). Not while a card or another of the launcher's frames has the remote.
     */
    private static boolean holdOk(KeyEvent e) {
        if (!QuietHold.isOk(e.getKeyCode()) || android.os.Build.VERSION.SDK_INT < 33 || boardWin == null || app == null
                || !holdMenuOn(app) || QuietHold.up() || InStepCard.isShowing() || LiveCentre.isShowing() || F1Tower.isShowing()
                || HalfTimeBreak.isShowing() || CreditsCover.isShowing() || ScoreCard.busy() || LineupCard.isShowing()) {
            return false;
        }
        if (SystemClock.uptimeMillis() - QuietHold.okSentAt < 400L) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getDownTime() == okDown) return true;
            okDown = e.getDownTime();
            okFired = false;
            main().removeCallbacks(menuOnHold);
            main().postDelayed(menuOnHold, QuietHold.HOLD_MS);
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && e.getDownTime() == okDown) {
            main().removeCallbacks(menuOnHold);
            okDown = -1;
            if (okFired || e.isCanceled()) return true;
            main().post(AppSearchService::ok);
            return true;
        }
        return false;
    }

    /** Pure: the menu's lines, as the switches stand. */
    static String[] menuLines(boolean boardShown, boolean logoOn, String look, String logoLook, String tag) {
        return new String[]{"Move and resize", boardShown ? "Hide the scoreboard" : "Show the scoreboard",
                logoOn ? "Hide the logo's cover" : "Show the logo's cover", "Scoreboard look:  ‹  " + look + "  ›",
                "Logo cover look:  ‹  " + logoLook + "  ›", "Picture tag:  ‹  " + tag + "  ›", "Turn the scoreboard off"};
    }

    private static void openMenu() {
        Context c = app;
        if (c == null || menuWin != null) return;
        float u = sw / 1920f;
        Context soft = new GoalWatchService.SoftwareWindow(c);
        FrameLayout win = new FrameLayout(soft);
        LinearLayout box = new LinearLayout(soft);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(22 * u);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF141B3A, 0xFF0A0E22});
        bg.setCornerRadius(28 * u);
        bg.setStroke(Math.max(1, Math.round(u)), 0x40FFFFFF);
        box.setBackground(bg);
        TextView head = new TextView(soft);
        head.setText("Scoreboard  ·  Up and Down, OK  ·  Back to close");
        head.setTextColor(0xB3FFFFFF);
        head.setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * u);
        head.setPadding(Math.round(10 * u), 0, Math.round(10 * u), Math.round(12 * u));
        box.addView(head);
        menuRows = new LinearLayout(soft);
        menuRows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < MENU_LINES; i++) {
            TextView t = new TextView(soft);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26f * u);
            t.setTypeface(c.getResources().getFont(R.font.inter_semibold));
            t.setPadding(Math.round(22 * u), Math.round(12 * u), Math.round(40 * u), Math.round(12 * u));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Math.round(4 * u);
            menuRows.addView(t, lp);
        }
        box.addView(menuRows, new LinearLayout.LayoutParams(-2, -2));
        win.addView(box, new FrameLayout.LayoutParams(-2, -2));
        WindowManager.LayoutParams lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = Math.round(sh * 0.08f);
        lp.setTitle("BoardMenu");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "the scoreboard's menu could not open: " + e);
            return;
        }
        menuWin = win;
        menuAt = 0;
        if (!InputKeyService.running) AppSearchService.keysFor("boardmenu", true);
        paintMenu();
        poke();
        main().removeCallbacks(menuAway);
        main().postDelayed(menuAway, MENU_MS);
        AlertLog.note(c, "scoreboard menu opened over the match");
    }

    private static void paintMenu() {
        Context c = app;
        if (menuRows == null || c == null) return;
        float u = sw / 1920f;
        String[] lines = menuLines(boardShown(c), logoOn(c), style(c).label, logoLook(c).label, logoTag(c).label);
        for (int i = 0; i < menuRows.getChildCount(); i++) {
            TextView t = (TextView) menuRows.getChildAt(i);
            t.setText(lines[i]);
            boolean on = i == menuAt;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(16 * u);
            g.setColor(on ? 0x33FFFFFF : 0x00000000);
            if (on) g.setStroke(Math.max(2, Math.round(3 * u)), 0xFFFFFFFF);
            t.setBackground(g);
            t.setTextColor(on ? 0xFFFFFFFF : 0xCCFFFFFF);
        }
    }

    private static boolean menuKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled();
        // The release (and repeats) of the OK whose hold opened it.
        if (QuietHold.isOk(code) && e.getDownTime() == menuOkDown) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                menuOkDown = -1;
                okDown = -1;
            }
            return true;
        }
        main().removeCallbacks(menuAway);
        main().postDelayed(menuAway, MENU_MS);
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (up) main().post(BoardCover::closeMenu);
            return true;
        }
        if (QuietHold.isOk(code)) {
            if (up) main().post(BoardCover::menuOk);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (down) main().post(() -> {
                menuAt = Math.max(0, Math.min(MENU_LINES - 1, menuAt + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1)));
                paintMenu();
            });
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int step = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            if (down) main().post(() -> {
                Context c = app;
                if (c == null) return;
                if (menuAt == 3) stepStyle(c, step);
                else if (menuAt == 4) stepLogoLook(c, step);
                else if (menuAt == 5) stepLogoTag(c, step);
                paintMenu();
            });
            return true;
        }
        // The rest (the volume) on to the TV.
        return false;
    }

    private static void menuOk() {
        Context c = app;
        if (c == null || menuWin == null) return;
        switch (menuAt) {
            case 0:
                closeMenu();
                beginAdjust();
                return;
            case 1:
                setBoardShown(c, !boardShown(c));
                break;
            case 2:
                setLogoOn(c, !logoOn(c));
                break;
            case 3:
                stepStyle(c, 1);
                break;
            case 4:
                stepLogoLook(c, 1);
                break;
            case 5:
                stepLogoTag(c, 1);
                break;
            default:
                closeMenu();
                stop(c, "switched off over the match");
                return;
        }
        paintMenu();
    }

    /** The menu's lines: move, the board, the logo's cover, the two looks, the picture tag, off. */
    private static final int MENU_LINES = 7;

    static boolean menuShowing() {
        return menuWin != null;
    }

    private static void closeMenu() {
        main().removeCallbacks(menuAway);
        FrameLayout w = menuWin;
        menuWin = null;
        menuRows = null;
        if (!InputKeyService.running) AppSearchService.keysFor("boardmenu", false);
        removeWindow(app, w);
    }
}
