package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.broadcast.Sfx;
import com.gathin.broadcast.SportBoard;
import com.gathin.broadcast.SportBug;
import com.gathin.broadcast.SportFeeds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The scoreboard for tennis, boxing and UFC over TiviMate, in the look chosen on the Scoreboard card (SportBug: the
 * same boards as the phone's, each look customised to the sport), where the football board goes on that channel
 * (BoardPlace) and as big. It comes the way the football board does (OverlayPrompt: the hold-OK button, or by itself on
 * Automatic, once a programme) when the programme on the channel watched is tennis, boxing or UFC by Sky's guide, or
 * for a few seconds from the card's Try rows.
 *
 * <p>Tennis and UFC are read from ESPN's scores every 15 seconds (the match or fight the programme is showing: by its
 * names when the title has them, else the one on now), UFC's round clock counted on between reads. Boxing has no scores
 * to read: the fighters come from the title, and the round clock from the first bell as seen on the screen, one OK on a
 * small card at the top right; at each bell after it the card comes back for a few seconds, and OK there puts the clock
 * right again. A set, the match, a round beginning and a fight's winner sweep across the board in the side's colour.
 *
 * <p>It goes when the channel changes, when the programme ends (and the next is not the same sport), ten minutes after
 * the match or fight is over, and with Back on the button that comes with it on Automatic. Hidden (silent too) while
 * anything else of the launcher's is over the player or Home is in front. Once a minute it steps a pixel or two round a
 * small circle, as the football board does, for the screen's sake. Never the keys, but OK and Back while the bell card
 * is up.
 */
final class SportCover {
    /** Scores read this often; the clock and the screen looked at each second. */
    static final long READ_MS = 15_000L, TICK_MS = 1_000L;
    /** A moment's band, the tab's first stay, how long it stays after the end, a Try, and the longest it stays at all. */
    static final long MOMENT_MS = 3_500L, TAB_MS = 20_000L, OVER_KEEP_MS = 10 * 60_000L, TRY_MS = 12_000L, MOST_MS = 6 * 3_600_000L;
    /** No match or fight found to show for this long: it goes. */
    static final long NONE_MS = 3 * 60_000L;
    /** The first bell waited for this long; the card's word of thanks; how far into a round the card still takes OK. */
    static final long BELL_WAIT_MS = 20 * 60_000L, BELL_DONE_MS = 4_500L;
    static final int BELL_LATE_S = 12;
    /** Its coming in and the tab sliding out under it. */
    static final long WIPE_MS = 420L, TAB_IN_MS = 320L;
    private static final String FILE = "sport_cover";

    private static Handler main;
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    /** Flags, photos and UFC's logo, by address, once fetched. */
    private static final Map<String, Bitmap> pics = new HashMap<>();

    private static Context app;
    private static FrameLayout win;
    private static Face face;
    private static SportBug bug;
    private static SportBug.Skin skin;
    private static Bitmap logo;
    private static int sport;
    private static String title = "", channel = "";
    private static long start, end;
    /** As last read (boxing: as made from the title), when, and as drawn now. */
    private static SportBoard board, now;
    private static long readAt, triedAt, openedAt, overAt, firstShownAt;
    /** Each Try counted, so a Try's steps never land on the next one's board. */
    private static int tryGen;
    private static boolean trying, reading, hidden = true, checkingEnd;
    private static long bellAt, bellPressedAt, endGrace;
    private static int bellFor;
    private static int sw = 1920, sh = 1080, edge = 2, step;
    private static long stepAt;
    private static float u, bw, bh;
    private static SportBoard.Moment moment;
    private static long momentAt;
    private static boolean tabWas;
    private static long tabAt;

    private SportCover() { }

    /** The main thread's handler, made when first wanted (the pure parts load without one, for the tests). */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** "Tennis, boxing and UFC boards" on the Scoreboard card: on unless switched off. */
    static boolean on(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
        if (!on) close();
    }

    static boolean isShowing() {
        return win != null;
    }

    // ---- up and away -----------------------------------------------------------------------------------------------------

    /** The board for the programme on the channel watched (title, its sport, its start and end), as recognised there. */
    static void open(Context c, int kind, String programme, long from, long to) {
        if (c == null || kind == 0 || !on(c)) return;
        // The football board up for a game has the place: never over it.
        String g = BoardCover.game(c);
        if (g != null && !g.isEmpty() && !BoardCover.NO_GAME.equals(g)) return;
        close();
        app = c.getApplicationContext();
        trying = false;
        sport = kind;
        title = programme == null ? "" : programme;
        start = from;
        end = to;
        channel = InStep.channel(app);
        board = kind == SportBoard.BOXING ? SportFeeds.boxing(title, from) : null;
        bellAt = 0;
        bellFor = 0;
        endGrace = 0;
        if (!up()) return;
        AlertLog.note(app, "tennis/boxing/UFC board on: " + title + (channel.isEmpty() ? "" : ", " + channel));
        if (kind != SportBoard.BOXING) read();
        main().post(tick);
    }

    /** A sample board in the chosen look for a few seconds, over whatever is on, with one moment: the card's Try rows. */
    static void tryIt(Context c, int kind) {
        close();
        app = c.getApplicationContext();
        trying = true;
        sport = kind;
        channel = InStep.channel(app);
        title = "";
        board = sample(kind);
        bellAt = 0;
        int gen = ++tryGen;
        if (!up()) return;
        main().post(tick);
        // One moment while it is up: the match won, a round begun.
        main().postDelayed(() -> {
            if (!trying || gen != tryGen || win == null || board == null) return;
            SportBoard after = board.copy();
            if (kind == SportBoard.TENNIS) {
                // The deciding set won: the match, its winner ticked.
                after.a.games = new int[]{6, 4, 6};
                after.b.games = new int[]{4, 6, 3};
                after.a.points = after.b.points = "";
                after.state = "post";
                after.a.winner = true;
            } else {
                after.round = board.round + 1;
                after.secsIn = 0;
            }
            apply(after, System.currentTimeMillis());
        }, 3_500L);
        // And a game won before it, for its swish (tennis).
        if (kind == SportBoard.TENNIS) main().postDelayed(() -> {
            if (!trying || gen != tryGen || win == null || board == null) return;
            SportBoard after = board.copy();
            after.a.games = new int[]{6, 4, 5};
            after.a.points = "0";
            after.b.points = "0";
            after.a.serving = false;
            after.b.serving = true;
            apply(after, System.currentTimeMillis());
        }, 1_600L);
        main().postDelayed(tryEnd, TRY_MS);
    }

    private static final Runnable tryEnd = () -> {
        if (trying) close();
    };

    /** Made-up players and fighters for the Try rows (never real ones). */
    static SportBoard sample(int kind) {
        SportBoard b;
        if (kind == SportBoard.TENNIS) {
            b = new SportBoard();
            b.sport = SportBoard.TENNIS;
            b.state = "in";
            b.id = "try:tennis";
            b.event = "WTA Finals";
            b.stage = "Semifinal";
            b.division = "Women's Singles";
            side(b.a, "Mira Kovalenko", "Kovalenko", "UKR", "2");
            side(b.b, "Lena Hartwell", "Hartwell", "GBR", "6");
            b.a.games = new int[]{6, 4, 4};
            b.b.games = new int[]{4, 6, 3};
            b.a.tiebreak = new int[]{-1, -1, -1};
            b.b.tiebreak = new int[]{-1, -1, -1};
            b.a.points = "40";
            b.b.points = "15";
            b.a.serving = true;
            b.set = 3;
        } else if (kind == SportBoard.BOXING) {
            b = SportFeeds.boxing("Live Boxing: Tom Archer v Rio Mendes - Heavyweight", 0L);
            b.id = "try:boxing";
            b.state = "in";
            b.round = 6;
            b.secsIn = 168;
            b.a.country = "GBR";
            b.b.country = "BRA";
        } else {
            b = new SportBoard();
            b.sport = SportBoard.UFC;
            b.state = "in";
            b.id = "try:ufc";
            b.event = "UFC 900";
            b.stage = "Lightweight";
            b.mainEvent = true;
            b.rounds = 5;
            b.round = 2;
            b.secsIn = 288;
            side(b.a, "Dario Vance", "Vance", "USA", "");
            side(b.b, "Kenji Morrow", "Morrow", "JPN", "");
            b.a.colour = SportBoard.RED_CORNER;
            b.b.colour = SportBoard.BLUE_CORNER;
            b.a.record = "24-3-0";
            b.b.record = "19-5-1";
        }
        return b;
    }

    private static void side(SportBoard.Side s, String name, String shortName, String country, String seed) {
        s.name = name;
        s.shortName = shortName;
        s.country = country;
        s.colour = SportFeeds.colourOf(country);
        s.seed = seed;
    }

    /** Its window made (hidden till there is a board to show and the player is up for it). */
    private static boolean up() {
        Context c = app;
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);
        edge = Math.max(2, Math.round(2f * sw / 1920f));
        BoardBug.Style style = BoardCover.style(c);
        skin = SportBug.skin(style.name(), style.kind);
        bug = new SportBug(c, ScoreCard.outfit(c, 800));
        FrameLayout f = new FrameLayout(c);
        face = new Face(c);
        f.addView(face, new FrameLayout.LayoutParams(-1, -1));
        // Not FLAG_NOT_TOUCHABLE (Android 12 and later dim such a window); not focusable: the keys stay with the app.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("SportCover");
        try {
            c.getSystemService(WindowManager.class).addView(f, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "the tennis/boxing/UFC board could not show: " + e);
            return false;
        }
        win = f;
        hidden = true;
        f.setVisibility(View.INVISIBLE);
        openedAt = System.currentTimeMillis();
        firstShownAt = 0;
        overAt = 0;
        readAt = 0;
        triedAt = 0;
        now = null;
        moment = null;
        tabWas = false;
        stepAt = openedAt;
        step = 0;
        // The sounds loaded now, so the first is not lost.
        Sfx.warm(c, Sfx.SWISH, Sfx.SCORE, "goal-" + BoardCover.goalSound(c));
        if (sport == SportBoard.UFC) loadLogo();
        else logo = null;
        return true;
    }

    /** Away (the channel changed, the programme or the match is over, Back on its button, a Try's time up). */
    static void close() {
        main().removeCallbacks(tick);
        main().removeCallbacks(tryEnd);
        closeBell();
        FrameLayout f = win;
        Face v = face;
        win = null;
        face = null;
        board = null;
        now = null;
        moment = null;
        trying = false;
        if (f == null) return;
        Runnable gone = () -> {
            try {
                f.getContext().getSystemService(WindowManager.class).removeView(f);
            } catch (RuntimeException ignored) {
                // gone already
            }
        };
        if (v == null || f.getVisibility() != View.VISIBLE) {
            gone.run();
            return;
        }
        v.out(gone);
    }

    // ---- the scores ------------------------------------------------------------------------------------------------------

    /** ESPN's scores for the sport read off the main thread, the match or fight the programme is showing picked. */
    private static void read() {
        if (reading || trying || sport == SportBoard.BOXING || app == null) return;
        reading = true;
        triedAt = System.currentTimeMillis();
        int kind = sport;
        String t = title;
        Context c = app;
        String showing = board == null ? "" : board.id;
        long overFor = overAt == 0 ? 0 : System.currentTimeMillis() - overAt;
        io.execute(() -> {
            SportBoard found = null;
            try {
                List<SportBoard> all = new ArrayList<>();
                if (kind == SportBoard.TENNIS) {
                    all.addAll(SportFeeds.tennis(SportFeeds.fetch("tennis/atp"), "ATP"));
                    all.addAll(SportFeeds.tennis(SportFeeds.fetch("tennis/wta"), "WTA"));
                } else {
                    all.addAll(SportFeeds.ufc(SportFeeds.fetch("mma/ufc")));
                }
                // UFC's programme names its main event but shows the whole card: the fight on, not the one named.
                SportBoard picked = SportFeeds.pick(all, kind == SportBoard.UFC ? "" : t, "", System.currentTimeMillis());
                found = keep(all, showing, picked, overFor);
                if (found != null) pictures(c, found);
            } catch (Exception ignored) {
                // read again at the next turn
            }
            SportBoard f = found;
            long at = System.currentTimeMillis();
            main().post(() -> {
                reading = false;
                if (win == null || trying || kind != sport) return;
                if (f != null) apply(f, at);
            });
        });
    }

    /** How long a finished match or fight stays before the board moves on to the next one on. */
    static final long RESULT_MS = 2 * 60_000L;

    /**
     * Pure: what to show after a read: the one shown, while it has just finished (its result up for RESULT_MS, unless
     * nothing else is on); else the one picked.
     */
    static SportBoard keep(List<SportBoard> all, String showing, SportBoard picked, long overFor) {
        SportBoard same = null;
        if (showing != null && !showing.isEmpty()) for (SportBoard s : all) if (showing.equals(s.id)) same = s;
        if (same != null && same.over() && (picked == null || !picked.live() || overFor < RESULT_MS)) return same;
        return picked != null ? picked : same;
    }

    /** Its flags (and UFC's photos) fetched once each, off the main thread, and put on the board read. */
    private static void pictures(Context c, SportBoard b) {
        for (SportBoard.Side s : new SportBoard.Side[]{b.a, b.b}) {
            s.flag = picture(c, s.flagUrl, 120);
            if (b.sport == SportBoard.UFC) s.photo = picture(c, s.photoUrl, 200);
        }
    }

    private static Bitmap picture(Context c, String url, int size) {
        if (url == null || url.isEmpty()) return null;
        synchronized (pics) {
            if (pics.containsKey(url)) return pics.get(url);
        }
        Bitmap b = null;
        try {
            b = ScoresTicker.cachedLogo(c, url, size);
        } catch (RuntimeException ignored) {
            // none: the country's letters instead
        }
        synchronized (pics) {
            if (pics.size() > 80) pics.clear();
            pics.put(url, b);
        }
        return b;
    }

    private static void loadLogo() {
        Context c = app;
        io.execute(() -> {
            Bitmap m = picture(c, SportFeeds.UFC_LOGO, 200);
            Bitmap t = m == null ? null : HomeV2.trim(m);
            main().post(() -> {
                logo = t;
                if (face != null) face.invalidate();
            });
        });
    }

    /** A new reading (or a Try's next step): its moment and sounds, then shown. */
    private static void apply(SportBoard b, long at) {
        SportBoard was = board;
        board = b;
        readAt = at;
        // Over since: the one shown finished (a new one, or one on again, starts the count afresh).
        if (!b.over()) overAt = 0;
        else if (overAt == 0 || was == null || !was.id.equals(b.id)) overAt = at;
        SportBoard.Moment m = SportBoard.moment(was, b);
        if (m != null) {
            momentNow(m);
        } else if (gameWon(was, b)) {
            sound(Sfx.SCORE);
        }
        relayout();
    }

    /** Pure: a tennis game won between two readings of the same match: the set in play's games up, no set or match won. */
    static boolean gameWon(SportBoard was, SportBoard b) {
        if (was == null || b == null || b.sport != SportBoard.TENNIS || !was.id.equals(b.id) || !b.live()) return false;
        int i = b.setColumns() - 1;
        if (i != was.setColumns() - 1) return false;
        return gamesAt(b.a, i) > gamesAt(was.a, i) || gamesAt(b.b, i) > gamesAt(was.b, i);
    }

    private static int gamesAt(SportBoard.Side s, int i) {
        return i >= 0 && i < s.games.length ? s.games[i] : 0;
    }

    /** A moment's band across the board, with its sound: the goal sound for a set, the match, a winner; the swish for a round. */
    private static void momentNow(SportBoard.Moment m) {
        moment = m;
        momentAt = System.currentTimeMillis();
        // The round a bell press has just started: its band, silent (the press is the family's own).
        if (m.words.startsWith("ROUND ")) {
            if (momentAt - bellPressedAt > 3_000L) sound(Sfx.SWISH);
        }
        else if (!hidden && app != null) Sfx.goal(app, BoardCover.goalSound(app));
        if (face != null) face.postInvalidateOnAnimation();
    }

    /** A sound panned to the board, by the football board's own switches; never while it is hidden. */
    private static void sound(String name) {
        Context c = app;
        if (c == null || hidden || face == null) return;
        // The football board's switches: the swish's; the score swish's too for a tennis game won.
        boolean wanted = BoardCover.swishOn(c) && (!Sfx.SCORE.equals(name) || BoardCover.scoreSwishOn(c));
        if (!wanted) return;
        int[] at = new int[2];
        face.getLocationOnScreen(at);
        float pan = sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + face.getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
        Sfx.play(c, name, pan, Sfx.SCORE.equals(name) ? 0.85f : 0.7f);
    }

    // ---- each second -----------------------------------------------------------------------------------------------------

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            if (win == null || app == null) return;
            long t = System.currentTimeMillis();
            if (!trying && gone(t)) return;
            if (sport == SportBoard.BOXING && !trying && board != null) boxing(t);
            if (!trying && sport != SportBoard.BOXING && t - triedAt >= READ_MS) read();
            now = board == null ? null : board.at(readAt, t);
            show(trying || (now != null && BoardCover.playerUp(app) && !footballUp(app)));
            if (!hidden && t - stepAt >= BoardCover.STEP_MS) {
                stepAt = t;
                step++;
                if (face != null) face.step(BoardCover.orbit(step));
            }
            tab(t);
            if (face != null) face.invalidate();
            main().postDelayed(this, TICK_MS);
        }
    };

    /** Whether the football board is up for a game (it has the place: this one waits hidden). */
    private static boolean footballUp(Context c) {
        String g = BoardCover.game(c);
        return g != null && !g.isEmpty() && !BoardCover.NO_GAME.equals(g);
    }

    /** Whether it has gone: the channel changed, the programme or the match over, or none found to show. */
    private static boolean gone(long t) {
        String ch = InStep.channel(app);
        if (!channel.isEmpty() && !ch.isEmpty() && !InStep.base(ch).equals(InStep.base(channel))) {
            AlertLog.note(app, "tennis/boxing/UFC board away: the channel changed");
            close();
            return true;
        }
        if (overAt > 0 && t - overAt >= OVER_KEEP_MS || t - openedAt >= MOST_MS) {
            AlertLog.note(app, "tennis/boxing/UFC board away: over");
            close();
            return true;
        }
        if (board == null && t - openedAt >= NONE_MS) {
            AlertLog.note(app, "tennis/boxing/UFC board away: nothing on ESPN for " + title);
            close();
            return true;
        }
        if (end > 0 && t > end + 60_000L) programmeEnded();
        return false;
    }

    /** The programme's time up: the one on now looked at; the same sport goes on (its next part), anything else ends it. */
    private static void programmeEnded() {
        if (checkingEnd) return;
        checkingEnd = true;
        Context c = app;
        int kind = sport;
        io.execute(() -> {
            Object[] on = null;
            try {
                String[] channels = c.getResources().getStringArray(R.array.sport_tv_channels);
                List<Object[]> around = TvGuide.around(channels, InStep.tunedNames(c), System.currentTimeMillis(), 0);
                if (!around.isEmpty()) on = around.get(0);
            } catch (RuntimeException ignored) {
                // looked at again in a minute
            }
            Object[] o = on;
            main().post(() -> {
                checkingEnd = false;
                if (win == null || trying) return;
                String t = o == null ? "" : (String) o[0];
                if (o != null && SportFeeds.sportOf(t) == kind) {
                    title = t;
                    start = (Long) o[1];
                    end = (Long) o[2];
                    return;
                }
                if (o == null) {
                    // The guide not read: ten minutes past the programme's end at most.
                    if (endGrace == 0) endGrace = end + 10 * 60_000L;
                    if (System.currentTimeMillis() < endGrace) {
                        end += 60_000L;
                        return;
                    }
                }
                AlertLog.note(c, "tennis/boxing/UFC board away: the programme ended");
                close();
            });
        });
    }

    /** Boxing's round and clock from the first bell; the bell card up when a bell is due. */
    private static void boxing(long t) {
        SportBoard b = board.copy();
        int[] st = SportBoard.bell(bellAt, t, b.roundSecs, b.restSecs, b.rounds);
        if (bellAt <= 0 || st[0] == 0) {
            b.state = "pre";
            b.round = 0;
            b.secsIn = -1;
        } else if (st[3] == 1) {
            b.state = "post";
            b.method = "End";
            b.round = b.rounds;
            b.secsIn = -1;
            b.resting = false;
        } else {
            b.state = "in";
            b.round = st[0];
            b.secsIn = st[1];
            b.resting = st[2] == 1;
        }
        SportBoard was = board;
        board = b;
        readAt = t;
        if (!b.over()) overAt = 0;
        else if (overAt == 0) overAt = t;
        SportBoard.Moment m = SportBoard.moment(was, b);
        if (m != null) momentNow(m);
        // The card at a bell: the first; then through each minute between rounds and the first seconds of the next.
        int due = bellDue(bellAt, b, t - openedAt);
        if (due > 0 && due != bellFor && bellWin == null && !hidden) showBell(due);
        else if (due == 0 && bellWin != null && !bellDone) closeBell();
    }

    /**
     * Pure: the round whose bell the card asks for now (0: none): the first until the first bell (for BELL_WAIT_MS after
     * it opened); then, between rounds, the next; and in a round's first BELL_LATE_S seconds, that round.
     */
    static int bellDue(long bellAt, SportBoard b, long sinceOpen) {
        if (b.over()) return 0;
        if (bellAt <= 0) return sinceOpen < BELL_WAIT_MS ? 1 : 0;
        if (!b.live()) return 0;
        if (b.resting) return b.round + 1;
        return b.secsIn >= 0 && b.secsIn < BELL_LATE_S ? b.round : 0;
    }

    /** Pure: the first bell's time that makes round r begin at pressed (each round and the rest after it before it). */
    static long bellFrom(long pressed, int r, int roundSecs, int restSecs) {
        return pressed - (long) Math.max(0, r - 1) * (roundSecs + restSecs) * 1000L;
    }

    // ---- shown or hidden, the tab ------------------------------------------------------------------------------------------

    private static void show(boolean want) {
        FrameLayout f = win;
        if (f == null) return;
        if (want && hidden) {
            hidden = false;
            relayout();
            f.setVisibility(View.VISIBLE);
            if (firstShownAt == 0) firstShownAt = System.currentTimeMillis();
            if (face != null) face.in();
            sound(Sfx.SWISH);
            long t = System.currentTimeMillis();
            tabWas = tabWanted(now, t - firstShownAt, trying);
            tabAt = t + WIPE_MS;
        } else if (!want && !hidden) {
            hidden = true;
            f.setVisibility(View.INVISIBLE);
            closeBell();
        }
    }

    /** The tab under it: its first seconds up, between rounds, after the end, in a tiebreak; sliding out with a swish. */
    private static void tab(long t) {
        boolean want = tabWanted(now, firstShownAt == 0 ? 0 : t - firstShownAt, trying);
        if (want && !tabWas && !hidden) {
            tabAt = t;
            sound(Sfx.SWISH);
        }
        tabWas = want;
    }

    /** Pure: the tab wanted under the board: its first TAB_MS up (a Try's whole time), between rounds, after the end, a tiebreak. */
    static boolean tabWanted(SportBoard b, long upFor, boolean trying) {
        if (b == null) return false;
        if (trying || upFor < TAB_MS) return true;
        return b.over() || b.fight() && b.resting || b.sport == SportBoard.TENNIS && b.tiebreakNow();
    }

    /** Its window sized and placed for the board as it is now: where the football board goes on the channel, as high. */
    private static void relayout() {
        FrameLayout f = win;
        SportBoard b = board;
        if (f == null || b == null || bug == null || skin == null) return;
        BoardPlace p = BoardPlace.get(app, channel.isEmpty() ? InStep.channel(app) : channel);
        if (p == null) p = BoardPlace.standard();
        u = Math.max(12f, p.bh * sh);
        float[] z = bug.size(skin, b, u);
        bw = Math.min(z[0], sw * 0.9f);
        bh = z[1];
        int w = Math.round(bw) + 4 * edge, h = Math.round(bh) + 4 * edge;
        int x = Math.max(0, Math.min(sw - w, Math.round(p.bx * sw) - 2 * edge)), y = Math.max(0, Math.min(sh - h, Math.round(p.by * sh) - 2 * edge));
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) f.getLayoutParams();
        if (lp == null || lp.x == x && lp.y == y && lp.width == w && lp.height == h) return;
        lp.x = x;
        lp.y = y;
        lp.width = w;
        lp.height = h;
        try {
            app.getSystemService(WindowManager.class).updateViewLayout(f, lp);
        } catch (RuntimeException ignored) {
            // the window went meanwhile
        }
    }

    /** The look chosen anew on the Scoreboard card: the board drawn in it at once where it is up. */
    static void restyle(Context c) {
        if (win == null) return;
        BoardBug.Style style = BoardCover.style(c);
        skin = SportBug.skin(style.name(), style.kind);
        relayout();
        if (face != null) face.invalidate();
    }

    // ---- the board itself ------------------------------------------------------------------------------------------------

    /** Pure: eased in and out (0 to 1). */
    static float ease(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }

    /** The board drawn: coming in from the left, its tab sliding out under it, a moment's band, the step against burn-in. */
    private static final class Face extends View {
        private float shown;
        private long inAt = -1, outAt = -1;
        /** The board last drawn, for the frames of its going once it is let go. */
        private SportBoard last;
        private Runnable after;
        private int ox, oy;

        Face(Context c) {
            super(c);
        }

        void in() {
            outAt = -1;
            inAt = System.currentTimeMillis();
            shown = 0f;
            postInvalidateOnAnimation();
        }

        void out(Runnable done) {
            after = done;
            outAt = System.currentTimeMillis();
            postInvalidateOnAnimation();
        }

        void step(int[] o) {
            ox = o[0] * edge;
            oy = o[1] * edge;
            invalidate();
        }

        @Override protected void onDraw(Canvas cv) {
            SportBoard b = now != null ? now : board;
            if (b != null) last = b;
            else b = last;
            if (b == null || bug == null || skin == null) {
                if (outAt >= 0 && after != null) {
                    Runnable r = after;
                    after = null;
                    post(r);
                }
                return;
            }
            long t = System.currentTimeMillis();
            boolean animating = false;
            if (outAt >= 0) {
                float k = Math.min(1f, (t - outAt) / (float) (WIPE_MS * 0.7f));
                shown = 1f - k;
                if (k >= 1f) {
                    Runnable r = after;
                    after = null;
                    outAt = -1;
                    if (r != null) post(r);
                    return;
                }
                animating = true;
            } else if (inAt >= 0) {
                shown = Math.min(1f, (t - inAt) / (float) WIPE_MS);
                animating = shown < 1f;
            } else {
                shown = 1f;
            }
            float w = bw, h = bh;
            float tabH = SportBug.tabHeight(u) + u * 0.08f, bodyH = h - tabH;
            float wipe = ease(shown);
            cv.save();
            cv.translate(2 * edge + ox, 2 * edge + oy);
            // The board, wiping in from the left.
            cv.save();
            cv.clipRect(0, 0, w * wipe, bodyH + 1);
            bug.draw(cv, skin, b, logo, w, h, u, false);
            cv.restore();
            // The tab, sliding out under it.
            if (tabWas && wipe >= 1f) {
                float k = Math.max(0f, Math.min(1f, (t - tabAt) / (float) TAB_IN_MS));
                if (k < 1f) animating = true;
                cv.save();
                cv.clipRect(0, bodyH, w * ease(k), h);
                bug.draw(cv, skin, b, logo, w, h, u, true);
                cv.restore();
            }
            // A moment's band.
            SportBoard.Moment m = moment;
            if (m != null && wipe >= 1f) {
                float k = (t - momentAt) / (float) MOMENT_MS;
                if (k >= 1f) {
                    moment = null;
                } else {
                    bug.band(cv, skin, b, m.words, m.colour, w, u, Math.max(0.001f, k));
                    animating = true;
                }
            }
            cv.restore();
            if (animating) postInvalidateOnAnimation();
        }
    }

    // ---- boxing's bell -----------------------------------------------------------------------------------------------------

    private static FrameLayout bellWin;
    private static TextView bellTitle, bellUnder;
    private static boolean bellDone;
    private static int bellAsk;

    /** The card at the top right asking for OK at round r's bell, as the football board's line-up card asks at a kick-off. */
    private static void showBell(int r) {
        closeBell();
        Context c = app;
        if (c == null || board == null) return;
        bellAsk = r;
        bellDone = false;
        Context sw2 = new GoalWatchService.SoftwareWindow(c);
        float d = c.getResources().getDisplayMetrics().density;
        FrameLayout f = new FrameLayout(sw2);
        LinearLayout box = new LinearLayout(sw2);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Math.round(22 * d), Math.round(16 * d), Math.round(28 * d), Math.round(16 * d));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF70E1426, 0xF7080B18});
        glass.setCornerRadius(20 * d);
        glass.setStroke(Math.max(1, Math.round(d)), 0x80E8A33D);
        box.setBackground(glass);
        View dot = new View(sw2);
        GradientDrawable g1 = new GradientDrawable();
        g1.setShape(GradientDrawable.OVAL);
        g1.setColor(0xFFE8A33D);
        dot.setBackground(g1);
        int ds = Math.round(14 * d);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ds, ds);
        dl.rightMargin = Math.round(18 * d);
        box.addView(dot, dl);
        LinearLayout lines = new LinearLayout(sw2);
        lines.setOrientation(LinearLayout.VERTICAL);
        bellTitle = line(sw2, bellWords(r), 20, 0xFFFFFFFF, true);
        lines.addView(bellTitle);
        String names = board.a.shortName.isEmpty() ? "" : " · " + board.a.shortName + " v " + board.b.shortName;
        bellUnder = line(sw2, "Press OK as the bell goes on your screen" + names, 15, 0xD0FFFFFF, false);
        bellUnder.setPadding(0, Math.round(4 * d), 0, 0);
        lines.addView(bellUnder);
        box.addView(lines, new LinearLayout.LayoutParams(-2, -2));
        f.addView(box, new FrameLayout.LayoutParams(-2, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(40 * d);
        lp.y = Math.round(40 * d);
        lp.setTitle("SportBell");
        try {
            c.getSystemService(WindowManager.class).addView(f, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "the bell card could not open: " + e);
            return;
        }
        bellWin = f;
        PopupMotion.in(box, 10 * d);
        if (!InputKeyService.running) AppSearchService.keysFor("sportbell", true);
    }

    /** Pure: the card's words for round r's bell. */
    static String bellWords(int r) {
        return r <= 1 ? "Start the round clock with your channel's" : "Round " + r + " · keep the clock with your channel's";
    }

    private static void closeBell() {
        main().removeCallbacks(bellAway);
        FrameLayout f = bellWin;
        bellWin = null;
        bellDone = false;
        if (f == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("sportbell", false);
        try {
            f.getContext().getSystemService(WindowManager.class).removeView(f);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }

    private static final Runnable bellAway = SportCover::closeBell;

    /** OK and Back while the bell card is up; every other key, and every key without it, goes on to the app. */
    static boolean key(KeyEvent e) {
        if (bellWin == null) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main().post(() -> {
                // Not asked again until the next bell.
                bellFor = bellAsk;
                closeBell();
            });
            return true;
        }
        if (!QuietHold.isOk(code)) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) {
            long pressed = System.currentTimeMillis();
            main().post(() -> bell(pressed));
        }
        return true;
    }

    /** OK at a bell: the clock counted from it, that round beginning now (silent: the press is the family's own). */
    private static void bell(long pressed) {
        if (bellWin == null || bellDone || board == null) return;
        int r = Math.max(1, bellAsk);
        bellAt = bellFrom(pressed, r, board.roundSecs, board.restSecs);
        bellPressedAt = pressed;
        bellFor = r;
        bellDone = true;
        if (bellTitle != null) bellTitle.setText(r <= 1 ? "Round clock started" : "Round " + r + " · clock kept");
        if (bellUnder != null) bellUnder.setText("Three minutes a round, a minute between · OK at any bell puts it right");
        AlertLog.note(app, "boxing bell for round " + r + " set from the screen");
        main().removeCallbacks(bellAway);
        main().postDelayed(bellAway, BELL_DONE_MS);
        if (face != null) face.invalidate();
    }

    private static TextView line(Context c, String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colour);
        v.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** For the log: what is up. */
    static String state() {
        SportBoard b = board;
        return win == null ? "none" : String.format(Locale.ROOT, "%s %s", title, b == null ? "(nothing read yet)" : b.toString());
    }
}
