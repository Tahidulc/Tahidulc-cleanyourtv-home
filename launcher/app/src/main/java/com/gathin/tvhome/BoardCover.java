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
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
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
 * on from there in minutes and seconds, the minutes added beside it in added time. A goal lights the scoring side as it
 * goes in on the screen. It rests (gone) at half time, and full time shows for two minutes.
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
    private static BoardBug bug;
    private static LogoMark mark;
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
            showGame(c, now, true);
            return 1_000L;
        }
        if (testUntil > now) {
            if (ensureWindows(c)) showGame(c, now, true);
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
        Reading r = screen(c, id, now);
        if (r != null && r.state == 2) {
            // Full time on the screen: shown two minutes, off ten minutes after.
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
            if (now - ft > FT_SHOWN_MS) {
                hideAll();
                return 5_000L;
            }
        }
        if (r == null || r.state == 1 || r.halftime || !canShow(c)) {
            hideAll();
            return 1_000L;
        }
        if (!ensureWindows(c)) return 5_000L;
        showGame(c, now, false);
        return 1_000L;
    }

    /** The game (or the made-up one) on the board, where this channel's places put it. */
    private static void showGame(Context c, long now, boolean orSample) {
        String id = game(c);
        ScoresTicker.Game g = id == null || id.isEmpty() ? null : GoalWatchService.latestGame(id);
        Reading r = g == null ? null : screen(c, id, now);
        BoardBug.Look l;
        if (g != null && r != null && r.state != 1) l = look(g, r, now - InStep.holdMs(c, true));
        else if (orSample) l = sample();
        else return;
        String channel = InStep.channel(c), key = BoardPlace.key(channel);
        if (place == null || !key.equals(placeKey)) {
            place = BoardPlace.get(c, channel);
            placeKey = key;
        }
        if (g != null && !g.id.equals(imagesFor)) loadImages(c, g);
        // A goal on the screen: its side lit, the board bright again.
        if (g != null && r != null && id.equals(shownId) && shownScore != null && !shownScore.equals(l.score)) {
            int side = goalSide(shownScore, l.score);
            if (side > 0) bug.goal(side);
            poke();
        }
        if (g != null && r != null) {
            shownId = id;
            shownScore = l.score;
        }
        bug.crests(g != null && (g.homeLogo != null || g.awayLogo != null));
        bug.set(l);
        layout(c);
        guard(now);
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
        boardWin = frame(c, bug);
        if (!addWindow(c, boardWin)) {
            boardWin = null;
            bug = null;
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

    /** Both away (half time, Home, another app, switched off). */
    private static void hideAll() {
        Context c = app;
        removeWindow(c, boardWin);
        removeWindow(c, logoWin);
        boardWin = null;
        logoWin = null;
        bug = null;
        mark = null;
        imagesFor = null;
        shownScore = null;
    }

    /** Both windows where the places put them, the board as wide as its words need for its height. */
    private static void layout(Context c) {
        if (boardWin == null || bug == null) return;
        BoardPlace p = adjusting && editing != null ? editing : place;
        if (p == null) p = BoardPlace.standard();
        // Stacked is drawn taller than the height set (its clubs one above the other), and kept on the screen.
        int bh = Math.max(8, Math.round(p.bh * sh * bug.style().tall)), bw = Math.min(Math.round(sw * 0.5f), bug.natural(bh));
        int bx = Math.min(Math.round(p.bx * sw), sw - bw), by = Math.min(Math.round(p.by * sh), sh - bh);
        position(c, boardWin, bug, Math.max(0, bx), Math.max(0, by), bw, bh);
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
            move(bug, o);
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
    /** What the arrows do: 0 move the board, 1 size it, 2 move the logo's cover, 3 size it. */
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
        if (bug != null) {
            bug.setTranslationX(0);
            bug.setTranslationY(0);
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
                return "Sizing the scoreboard  ·  Up bigger, Down smaller  ·  OK: " + (logo ? "the logo's cover" : "move it")
                        + "  ·  Back: keep it";
            case 2:
                return "Moving the cover over the channel's logo  ·  arrows move it  ·  OK: its size  ·  Back: keep it";
            case 3:
                return "Sizing the logo's cover  ·  Left and Right its width, Up and Down its height  ·  OK: the scoreboard"
                        + "  ·  Back: keep it";
            default:
                return "Moving the scoreboard  ·  arrows move it  ·  OK: its size  ·  Back: keep it";
        }
    }

    private static void hintWords() {
        if (hint != null) Keys.set(hint, hintFor(part, mark != null));
    }

    /** The one being placed ringed. */
    private static void outline() {
        if (bug != null) bug.outline(adjusting && part <= 1);
        if (mark != null) mark.outline(adjusting && part >= 2);
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
        part = (part + 1) % (mark != null ? 4 : 2);
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
                editing.moveLogo(x, y);
                break;
            default:
                editing.sizeLogo(dx > 0 ? 1.04f : dx < 0 ? 1 / 1.04f : 1f, dy < 0 ? 1.04f : dy > 0 ? 1 / 1.04f : 1f);
        }
        layout(app);
    }

    /** The board's width as a share of the screen's, for each share of its height set (Stacked drawn taller). */
    private static float aspect() {
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
