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
 * <p>Out from under it, in its look, as on the phone: the game's stats now and then (BoardStats), and while it is up the
 * football alerts (goals, cards, VAR, half and full time, the kick-off and both line-ups) fold down out of it (BoardDrop)
 * in place of the usual cards; each can be switched off on the Scoreboard card.
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
    static final long MAX_MS = 4 * 3_600_000L, AFTER_FT_MS = 10 * 60_000L, FT_SHOWN_MS = 2 * 60_000L;
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
        prefs(c).edit().putString("style", all[(style(c).ordinal() + 1) % all.length].name()).apply();
        restyle();
    }

    static LogoMark.Look logoLook(Context c) {
        return LogoMark.Look.of(prefs(c).getString("look", ""));
    }

    static void nextLogoLook(Context c) {
        LogoMark.Look[] all = LogoMark.Look.values();
        prefs(c).edit().putString("look", all[(logoLook(c).ordinal() + 1) % all.length].name()).apply();
        restyle();
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

    /** The football alerts folding out from under it while it is up (off: the usual cards). */
    static boolean dropsOn(Context c) {
        return prefs(c).getBoolean("drops", true);
    }

    static void setDropsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("drops", on).apply();
    }

    /** The game's stats folding out from under it now and then. */
    static boolean statsOn(Context c) {
        return prefs(c).getBoolean("stats", true);
    }

    static void setStatsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("stats", on).apply();
        if (!on && stats != null) stats.fold(null);
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
        if (r == null || r.state != 0 || r.halftime) return null;
        // Only the minute ("67'"): the minute began a minute before.
        double secs = r.clockSec > 0 ? r.clockSec : (minuteOf(r.detail) - 1) * 60.0;
        if (secs <= 0) return null;
        // ESPN's clock in whole minutes counts on to the minute's end at most; a finer one a little further.
        long most = r.clockSec > 0 && r.clockSec % 60 != 0 ? 119 : 59;
        long elapsed = Math.max(0, Math.min(most, (screenNow - r.since) / 1000));
        return clockWords(secs + elapsed, r.detail);
    }

    /**
     * Pure: a clock of total seconds as broadcasters show it: "67:23"; in added time the period's end with the minutes
     * added beside it ("45:00", "+2"). The period is ESPN's minute's ("45'+2'" is still the first half, "90'" the
     * second however far the clock has run), else the clock's own.
     */
    static String[] clockWords(double total, String detail) {
        int whole = (int) total;
        int plus = Math.max(0, added(detail)), first = firstMinute(detail), period;
        if (first > 0) {
            period = first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4;
        } else {
            int base = whole - plus * 60, in = plus > 0 ? 1 : 0;
            period = base < 45 * 60 + in ? 1 : base < 90 * 60 + in ? 2 : base < 105 * 60 + in ? 3 : 4;
        }
        int end = period == 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
        if (whole >= end * 60) {
            int a = plus > 0 ? plus : Math.max(1, (whole - end * 60) / 60 + 1);
            return new String[]{String.format(Locale.UK, "%02d:00", end), "+" + a};
        }
        return new String[]{String.format(Locale.UK, "%02d:%02d", whole / 60, whole % 60), ""};
    }

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
        }
        return l;
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
        if (a == null || now < a[1]) return null;
        int period = periodOfSecs(a[0]), first = firstMinute(detail);
        if (first > 0 && (first <= 45 ? 1 : first <= 90 ? 2 : first <= 105 ? 3 : 4) != period) return null;
        return clockWords(a[0] + (now - a[1]) / 1000.0, detail);
    }

    /** Move and resize's Timing: the board (its clock, its score and the alerts) delta ms later, or sooner when negative. */
    static void timing(Context c, long delta) {
        if (delta == 0) return;
        InStep.nudge(c, delta);
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

    // ---- the alerts, as they fold out from under it -------------------------------------------------------------------

    /** Pure: an alert kind's colour for its chip (a goal's green, a red card's red, VAR's amber); PLAIN for the rest. */
    static int kindColour(String kind) {
        switch (kind == null ? "" : kind) {
            case "goal": return 0xFF3DDC84;
            case "red": return 0xFFFF5A5F;
            case "var": case "pen": case "drama": case "incident": return 0xFFFFB547;
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
     * Pure: a game's card (ScoreCard) as it folds out from under the board: its label as the chip ("GOAL", "RED CARD",
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
        o.side = scored > 0 ? scored : d.crest == null ? 0 : d.crest == d.homeCrest ? 1 : d.crest == d.awayCrest ? 2 : 0;
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

    /** Pure: a football pop-up's line ("⚽  Salah  67'") as it folds out from under the board. */
    static BoardDrop.Drop dropOfLine(String head, ScoresTicker.Game g, String sound, String scoredBy) {
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
        o.what = (o.chip + " " + o.title + " " + o.line).trim();
        return o;
    }

    /** Pure: both starting elevens as they fold out: each club's name and shape, then its players' surnames. */
    static BoardDrop.Drop lineupsDrop(ScoresTicker.Game g, ScoresTicker.Side[] sides) {
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

    /** A game's card while the board is up: folded out from under it instead, in its look; true when taken (main thread). */
    static boolean take(Context c, ScoreCard.Data d) {
        if (d == null || d.f1 || d.gameId == null || d.homeName == null || !up(c)) return false;
        ScoresTicker.Game g = GoalWatchService.latestGame(d.gameId);
        drop(c, dropOf(d, g == null ? 0 : BoardBug.colour(g.homeColor, g.homeAlt), g == null ? 0 : BoardBug.colour(g.awayColor, g.awayAlt)));
        return true;
    }

    /** A football pop-up's line while the board is up: folded out from under it instead; true when taken (main thread). */
    static boolean takeLine(Context c, CharSequence head, ScoresTicker.Game g, String sound, String scoredBy) {
        if (g == null || g.id == null || !up(c)) return false;
        drop(c, dropOfLine(head == null ? "" : head.toString(), g, sound, scoredBy));
        return true;
    }

    /** The kick-off's line-ups while the board is up: both elevens folded out from under it; true when taken (main thread). */
    static boolean takeLineups(Context c, ScoresTicker.Game g, ScoresTicker.Side[] sides) {
        if (g == null || g.id == null || sides == null || sides.length < 2 || sides[0] == null || sides[1] == null || !up(c)) return false;
        if (!NotifyPrefs.allowed(c, "kickoff", false, g.slug, g.id)) return false;
        drop(c, lineupsDrop(g, sides));
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
        return out;
    }

    private static String pct(String s) {
        try {
            return Math.round(Float.parseFloat(s.replace("%", "").trim())) + "%";
        } catch (NumberFormatException e) {
            return s;
        }
    }

    /** The stats from ESPN's match summary, each side's by its team's id (else the first is the home side's). */
    private static List<String[]> statsOf(org.json.JSONObject summary, String homeId) {
        Map<String, String> h = new HashMap<>(), a = new HashMap<>();
        org.json.JSONObject box = summary.optJSONObject("boxscore");
        org.json.JSONArray teams = box == null ? null : box.optJSONArray("teams");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            org.json.JSONObject t = teams.optJSONObject(i);
            if (t == null) continue;
            org.json.JSONObject team = t.optJSONObject("team");
            String tid = team == null ? "" : team.optString("id");
            boolean home = homeId != null && !homeId.isEmpty() && !tid.isEmpty() ? tid.equals(homeId) : i == 0;
            Map<String, String> m = home ? h : a;
            org.json.JSONArray st = t.optJSONArray("statistics");
            for (int k = 0; st != null && k < st.length(); k++) {
                org.json.JSONObject s = st.optJSONObject(k);
                if (s != null) m.put(s.optString("name"), s.optString("displayValue"));
            }
        }
        return statsList(h, a);
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
    /** The board and, under it, what folds out of it: the stats, an alert. */
    private static LinearLayout column;
    private static BoardBug bug;
    private static BoardStats stats;
    private static BoardDrop drop;
    private static LogoMark mark;
    /** The windows just made: the board comes in as a broadcaster's does at its first showing. */
    private static boolean fresh;
    /** The board's height set, and the widest the strips under it may be, in pixels. */
    private static int boardH = 48, stripMax = 600;
    /** How long before kick-off it is up (the kick-off and the line-ups fold out of it), and how long into half time. */
    static final long PRE_MS = 20 * 60_000L, HT_SHOWN_MS = 20_000L;
    /** The stats: how often they fold out while the game runs, how often they are read, and how many each time. */
    static final long STATS_EVERY_MS = 4 * 60_000L, STATS_READ_MS = 120_000L;
    static final int STATS_EACH_TIME = 3;
    /** How long an alert stays down (the line-ups longer). */
    static final long DROP_MS = 8_000L, LINEUPS_MS = 14_000L;
    /** The room under the board for what folds out, in board heights: as wide as an alert can be, as tall as the line-ups. */
    static final float STRIP_W = 15f, STRIP_ROOM = 5.6f;
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
            // line-ups fold out of it).
            ScoresTicker.Game g = GoalWatchService.latestGame(id);
            if (g != null && g.state == 1 && g.kickoff > now && g.kickoff - now <= PRE_MS) {
                if (ensureWindows(c)) showGame(c, now, false, preLook(g));
                return 1_000L;
            }
            closeAll();
            return 1_000L;
        }
        if (r.state == 2) {
            // Full time on the screen: shown two minutes (its alert first), off ten minutes after.
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
            if (now - ft > FT_SHOWN_MS && !dropping) {
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
            l = look(g, r, now - InStep.holdMs(c, true));
            // Set at the kick-off on the screen (OK when asked): the clock counted from that moment, the channel's own.
            if (l.live) {
                String[] set = anchored(anchorOf(prefs(c).getString("anchor", null), id), now, r.detail);
                if (set != null) {
                    l.clock = set[0];
                    l.added = set[1];
                }
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
            if (fresh) {
                bug.shownAt(0f);
                // The stats' first time a minute after it comes, then every few minutes.
                statsShownAt = now - STATS_EVERY_MS + 60_000L;
            }
            bug.open();
        }
        fresh = false;
        stats.look(bug.stripLook(), l.homeColour, l.awayColour);
        layout(c);
        guard(now);
        if (pre == null && g != null && r != null && board && !adjusting && testUntil <= now) {
            readStats(c, g, now);
            maybeStats(c, g, r, now);
        }
    }

    /** Whether the channel app has the screen to itself: not Home, the screensaver, another of the launcher's frames. */
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
        // The board, and under it what folds out of it.
        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(bug, new LinearLayout.LayoutParams(1, 1));
        column.addView(stats, new LinearLayout.LayoutParams(1, 1));
        column.addView(drop, new LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.WRAP_CONTENT));
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
        return true;
    }

    private static FrameLayout frame(Context c, View v) {
        FrameLayout f = new FrameLayout(c);
        f.addView(v, new FrameLayout.LayoutParams(1, 1));
        return f;
    }

    private static WindowManager.LayoutParams params(int w, int h, boolean focus) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                (focus ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
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

    /** Both away at once (Home, another app, switched off): what was folding out of it with them. */
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
     * Away as a broadcaster's goes (before kick-off, half time, full time shown): what is out under it folded, the
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
     * The board at (x, y, w, h) with its edge round it, in a window with room besides for the burn-in step and, under
     * the board, for what folds out of it (as wide as an alert may be, as tall as the line-ups): a size that holds while
     * the game goes on, so the window over the playing picture is not made again for each alert.
     */
    private static void positionBoard(Context c, int x, int y, int w, int h) {
        int vw = w + 2 * edge, vh = h + 2 * edge, unit = boardH;
        size(bug, vw, vh, 0);
        // The stats as wide as the board, a little less high; under a pill, a pill of their own a little apart.
        int gap = bug.stripLook().apart ? Math.max(2, Math.round(unit * 0.08f)) : 0;
        size(stats, vw, Math.max(12, Math.round(unit * 0.82f)), gap);
        stripMax = Math.max(vw, Math.min(Math.round(unit * STRIP_W), sw - x));
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) boardWin.getLayoutParams();
        int wx = x - 2 * edge, wy = y - 2 * edge, ww = stripMax + 2 * edge, wh = vh + gap + Math.round(unit * STRIP_ROOM) + 2 * edge;
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

    private static void size(View v, int w, int h, int top) {
        LinearLayout.LayoutParams l = (LinearLayout.LayoutParams) v.getLayoutParams();
        if (l.width == w && l.height == h && l.topMargin == top) return;
        l.width = w;
        l.height = h;
        l.topMargin = top;
        v.setLayoutParams(l);
    }

    // ---- what folds out under it: the alerts ---------------------------------------------------------------------------

    private static final ArrayDeque<BoardDrop.Drop> drops = new ArrayDeque<>();
    private static boolean dropping;
    private static final Runnable nextDropRun = BoardCover::nextDrop, hideDropRun = BoardCover::hideDrop;

    private static void drop(Context c, BoardDrop.Drop d) {
        drops.addLast(d);
        while (drops.size() > 4) drops.removeFirst();
        AlertLog.note(c, "alert from the scoreboard: " + d.what);
        if (d.gameId != null && d.gameId.equals(game(c))) poke();
        if (!dropping) nextDrop();
    }

    /**
     * The next alert down from under the board: the stats folded first (it comes in their place), and after a goal's
     * sweep on the board (as broadcasters caption a goal once they have shown it).
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
        long wait = bug.bandLeft(System.currentTimeMillis());
        if (wait > 0) {
            main().postDelayed(nextDropRun, wait);
            return;
        }
        BoardDrop.Drop d = drops.pollFirst();
        poke();
        int bw = bug.getWidth() > 0 ? bug.getWidth() : Math.round(boardH * 10f);
        int w = Math.min(stripMax, Math.max(bw, Math.round(boardH * (d.lineups ? STRIP_W : 11f))));
        boolean apart = bug.stripLook().apart;
        size(drop, w, LinearLayout.LayoutParams.WRAP_CONTENT, apart ? Math.max(2, Math.round(boardH * 0.08f)) : 0);
        drop.show(d, bug.stripLook(), boardH, true, w <= bw + 1);
        main().postDelayed(hideDropRun, d.lineups ? LINEUPS_MS : DROP_MS);
    }

    private static void hideDrop() {
        main().removeCallbacks(hideDropRun);
        if (drop == null || !drop.isOut()) {
            nextDrop();
            return;
        }
        drop.fold(BoardCover::nextDrop);
    }

    // ---- what folds out under it: the stats ----------------------------------------------------------------------------

    private static List<String[]> statList = new ArrayList<>();
    private static String statsFor = "", htStatsFor = "";
    private static long statsReadAt, statsShownAt, goalShownAt;
    private static int statsFrom;
    private static boolean statsReading;

    /** The game's stats read again every two minutes while it is on (off the main thread), for the next fold-out. */
    private static void readStats(Context c, ScoresTicker.Game g, long now) {
        if (!statsOn(c) || statsReading || g.slug == null || g.id == null || g.state == 1) return;
        if (g.id.equals(statsFor) && now - statsReadAt < STATS_READ_MS) return;
        statsReading = true;
        statsReadAt = now;
        String id = g.id, slug = g.slug, homeId = g.homeId;
        io.execute(() -> {
            List<String[]> got = null;
            try {
                got = statsOf(new org.json.JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/"
                        + slug + "/summary?event=" + id)), homeId);
            } catch (Exception ignored) {
                // read again in two minutes
            }
            List<String[]> list = got;
            main().post(() -> {
                statsReading = false;
                if (list == null || list.isEmpty()) return;
                statList = list;
                statsFor = id;
            });
        });
    }

    /**
     * The game's stats folding out from under it, as broadcasters do: three every four minutes while the game runs (the
     * next three each time), all of them once at half time. Never while an alert is down, just after a goal, or while
     * the scores have a goal the picture hasn't shown yet (its shot on target would give it away).
     */
    private static void maybeStats(Context c, ScoresTicker.Game g, Reading r, long now) {
        if (!statsOn(c) || dropping || stats == null || stats.isOut() || bug == null || bug.closing()) return;
        if (statList.size() < 2 || !g.id.equals(statsFor) || bug.getWidth() <= 0 || ahead(g.id, r)) return;
        List<String[]> pick = new ArrayList<>();
        if (r.halftime) {
            if (g.id.equals(htStatsFor)) return;
            htStatsFor = g.id;
            for (String[] s : statList) pick.add(new String[]{"Half-time · " + s[0], s[1], s[2]});
        } else {
            if (r.state != 0 || now - statsShownAt < STATS_EVERY_MS || now - goalShownAt < 90_000L) return;
            for (int i = 0; i < Math.min(STATS_EACH_TIME, statList.size()); i++) pick.add(statList.get((statsFrom + i) % statList.size()));
            statsFrom = (statsFrom + STATS_EACH_TIME) % statList.size();
        }
        statsShownAt = now;
        poke();
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
                if (bug != null && id.equals(imagesFor)) bug.images(h, w, l);
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
        if (!adjusting) return false;
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
}
