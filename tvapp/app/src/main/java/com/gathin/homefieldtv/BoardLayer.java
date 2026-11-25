package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.BroadcastData;
import com.gathin.broadcast.BroadcastView;
import com.gathin.broadcast.LivePlay;
import com.gathin.broadcast.Moments;
import com.gathin.broadcast.Sfx;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.BoardTime;
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.FotMob;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.engine.LogoPack;

import org.json.JSONObject;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The scoreboard over Homefield TV's own player, with the TV launcher's logic for its board over TiviMate (BoardCover):
 * the game is found from the channel playing (its name, else its guide entry: Matching.gameOn), and the board shows it as
 * the picture does, not as the scores are: each reading of the scores is kept with when it was seen and the board shows
 * the one the channel has reached, its wait behind (InStep), its clock counting on in minutes and seconds with the
 * minutes added beside it (BoardClock), half time and full time as soon as the whistle reaches the picture (BoardTime:
 * Whistle, the launcher's and the phone's one rule from the data), never early. At each kick-off a card asks for OK as it
 * happens on the screen: from then the clock is the channel's own and the wait is set. It comes and goes as a broadcaster's does (BoardBug, the launcher's
 * board copied whole, in the Scoreboard Lab's looks or By competition): up twenty minutes before kick-off, resting at
 * half time once its moment has passed, gone a minute after full time; a goal sweeps across it with the family's sounds
 * (or plays the goal animation chosen in the hold-OK menu: GoalLooks, each club's own among them).
 *
 * <p>Out of it, in its look (BoardDrop): the cards, the substitutions, VAR explained, the minutes added, half and full
 * time, each once the picture reaches it (AsSeen: the channel's delay since the scores had it, and the board's clock past
 * its minute; with no delay set for the channel and its clock not set at kick-off, the longest a channel runs behind),
 * "Checking VAR" off when play goes on (BoardVar: VarCheck);
 * the +N beside the clock by the shared rule for every look (AddedTime); the kick-off pop-up, the pop-up after a goal and at the second half, and the
 * classic line-ups seven minutes before kick-off (the shared broadcast pages, BroadcastView), the board away while a
 * moment's pop-up is on. The pop-ups in my scoreboard's look unless set otherwise: a strap in the board's own look that
 * folds into it where the family put it on this channel, at its size (boardAt; the board away at once while the strap
 * draws it, back at once there after). Over the channel's
 * own logo, a cover (top right, where most broadcasters put theirs).
 *
 * <p>Where they sit: the standard places (the board top left over the broadcaster's own, the cover top right) until moved
 * for a channel. OK held while the channel plays full screen with nothing over it opens the scoreboard and logo menu
 * (BoardMenu, the launcher's over TiviMate: "hold down ok when I'm in full screen ... and then I can turn the scoreboard
 * and logo on and off just like it is on tivimate"), on every channel, over a match or not: Scoreboard and Logo's cover
 * first, then the look, moving and sizing, the line-ups, My scoreboards (the editor, Generate, Ask AI) and a new one,
 * timing, the alerts, VAR, the pop-ups, the sounds and the standard places. It is the only place for these settings.
 * Scoreboard off is the launcher's boardShown: the board, what slides out beside it and its pop-ups away at once and until
 * it is on again, the cover staying as its own switch says, the game followed underneath; Logo's cover off, the cover
 * goes. One of My scoreboards in use (MyBoardStore, a BoardSpec) is drawn by the board in its base look, as the
 * launcher's and the phone's boards draw it, the strap too; the editor (BoardEditor) sits down the right over the match,
 * the board changing with it as it is edited. Kind to the screen: once a minute both step a pixel or two round a
 * circle, and after five minutes without a goal they dim.
 *
 * <p>All of it shows only while the channel plays full screen with nothing over it (BoardGate: the live screen says
 * when, showOnlyWhen and coverChanged): the info card, the OK overlay, the bar, the channel list or anything else over
 * the picture takes it away at once, and it comes back with plain playback; its pop-ups wait for then, or are let go
 * when stale; after the live screen was away (another screen over it) for longer than that, it catches up quietly.
 */
final class BoardLayer extends FrameLayout implements LiveTv.Listener {
    /** How often the scores are read: live (the launcher's poll), in the half hour before kick-off, otherwise; the summary's. */
    static final long POLL_LIVE_MS = 20_000L, POLL_SOON_MS = 30_000L, POLL_IDLE_MS = 60_000L, SUMMARY_MS = 45_000L;
    /** Looking for the channel's game: while none is found, and now and then while one is. */
    static final long FIND_MS = 60_000L, REFIND_MS = 5 * 60_000L;
    /** An alert out beside the board this long at least (the launcher's DROP_MS). */
    static final long DROP_MS = 8_000L;
    /** The in-step card waits this long for OK, and says what OK did for this long. */
    static final long CARD_WAIT_MS = 4 * 60_000L, CARD_DONE_MS = 4_500L;
    /** What slides out beside it: its words TEXT of the board's height, never longer than SHORT of its heights, nor wider than STRIP_W. */
    static final float TEXT = 0.86f, SHORT = 8f, STRIP_W = 15f;
    /** Each press of an arrow while moving, as a share of the screen; each step of a size. */
    static final float MOVE_STEP = 0.005f, SIZE_STEP = 1.08f;
    /** Alert chips' colours, as the launcher's kindColour: the minutes added's in added time's one red (AddedTime.RED). */
    static final int GOAL = 0xFF3DDC84, RED = 0xFFFF5A5F, SUB = 0xFF5CC8FF, ADDED = com.gathin.broadcast.AddedTime.RED;

    private final TvActivity tv;
    private final Handler h = new Handler(Looper.getMainLooper());
    final BoardBug bug;
    final BoardDrop drop;
    private final LogoCover logo;
    private final Card card;
    /** The scoreboard and logo menu (OK held in plain playback). */
    final BoardMenu menu;
    /** The menu's picture of the cover, and a board in the looks either side of the one now (for their strips' colours). */
    private final CoverArt menuArt;
    private BoardBug looks;
    /** The menu's Back to the standard places: on every channel (else this one's). */
    private boolean resetEvery;
    /** The scoreboard editor over the match (My scoreboards), down the right; the board shows the board being edited. */
    final BoardEditor editor;
    /** The custom board drawn now (null: the look as chosen): the one in use, or the editor's. */
    private com.gathin.broadcast.BoardSpec shownCustom;
    private BroadcastView popup;
    private String popupKind = "";
    private boolean started, popupMaking, lineupsMaking;

    // ---- the channel and its game ----------------------------------------------------------------------------------------

    private Channel ch;
    private String chKey = "";
    private Game game;
    /**
     * The game as the picture shows it (BoardTime): the readings kept, the clock as last shown, the clock set at a kick-off
     * on the screen (OK at the card), and half and full time as the whistle reaches the picture (Whistle).
     */
    private final BoardTime time = new BoardTime();
    private final ArrayDeque<BoardClock.Reading> readings = time.readings;
    private final BoardClock.Steady steady = time.steady;
    private final int[] announced = new int[4];
    /** "Checking VAR" out beside the board, and when it comes off (VarCheck). */
    private final BoardVar varOut = new BoardVar();
    /** The check whose game was seen gone on past it (the commentary's next line), once each. */
    private String varMovedKey;
    private long nextFind, nextPoll, nextSummary;
    private boolean finding, polling, summarising, firstSummary = true;
    /** When the scores last saw the game change (a goal, a card, the kick-off): "It's just happened" goes by it. */
    private long changeSeen;
    /** When the scores saw the kick-off (or the second half's), for the card; whether it was the second half's. */
    private long kickSeen;
    private boolean kickSecond;
    /** ESPN's elevens and shapes, for the line-ups beside the board when FotMob has none. */
    private List<String[]> espnHome = new ArrayList<>(), espnAway = new ArrayList<>();
    private String homeShape = "", awayShape = "";

    // ---- what the screen has shown -------------------------------------------------------------------------------------

    private String shownScore, phaseWas;
    private long htSince = -1, ftSince = -1, lastChange, lineupsTried;
    private boolean up, swishBack, dimmed;
    /** The board coming back as the strap that folded into it goes: at once, all there. */
    private boolean strapBack;
    private final Set<String> shown = new HashSet<>(), eventKeys = new HashSet<>(), varKeys = new HashSet<>();
    /**
     * The alerts the summary brings (cards, substitutions, VAR), each held until the picture reaches it (AsSeen); the
     * minutes added as read, a few minutes of them, the same way; where the picture is now (each look at the board).
     */
    private final com.gathin.broadcast.AsSeen.Held pending = new com.gathin.broadcast.AsSeen.Held();
    private final com.gathin.broadcast.AsSeen.Reads<int[]> addedReads = new com.gathin.broadcast.AsSeen.Reads<>();
    private com.gathin.broadcast.AsSeen.Picture picture = new com.gathin.broadcast.AsSeen.Picture();
    private final ArrayDeque<BoardDrop.Drop> drops = new ArrayDeque<>();
    private boolean dropping;
    /** The halves (1 to 4, as bits) whose minutes added have come out beside the board: once each. */
    private int addedOutFor;
    /** A goal's pop-up still to come: until when (uptime), and its words. */
    private long restartUntil;
    private final Runnable nextDropRun = this::nextDrop, hideDropRun = this::hideDrop;

    // ---- where it sits ----------------------------------------------------------------------------------------------------

    private BoardPrefs.Place place;
    private int edge = 2, boardH = 48, tuck = 6, sideGap, sideMax = 600;
    private boolean sideLeft;
    private final Rect boardRect = new Rect(), logoRect = new Rect();
    /** Moving with the arrows: 0 not, 1 the board, 2 the logo's cover. */
    private int moving;
    private final Paint dimPaint = new Paint();

    BoardLayer(TvActivity tv) {
        super(tv);
        this.tv = tv;
        setClipChildren(false);
        logo = new LogoCover(tv);
        logo.setVisibility(INVISIBLE);
        addView(logo, new LayoutParams(1, 1));
        drop = new BoardDrop(tv);
        addView(drop, new LayoutParams(1, 1));
        bug = new BoardBug(tv);
        bug.setVisibility(INVISIBLE);
        addView(bug, new LayoutParams(1, 1));
        card = new Card(tv);
        addView(card, new LayoutParams(-1, -1));
        menuArt = new CoverArt(tv);
        menu = new BoardMenu(tv, menuHost);
        addView(menu, new LayoutParams(-1, -1));
        editor = new BoardEditor(tv, new BoardEditor.Host() {
            @Override public void showing(com.gathin.broadcast.BoardSpec live) {
                restyle(live == null ? MyBoardStore.inUse(getContext()) : live);
            }

            @Override public com.gathin.broadcast.BoardDesigner.Brief brief() {
                return briefNow();
            }

            @Override public void closed() {
                restyle(MyBoardStore.inUse(getContext()));
                if (started) update();
            }
        });
        addView(editor, new LayoutParams(-1, -1));
        ColorMatrix m = new ColorMatrix();
        m.setScale(BoardClock.DIM, BoardClock.DIM, BoardClock.DIM, 1f);
        dimPaint.setColorFilter(new ColorMatrixColorFilter(m));
        restyle(MyBoardStore.inUse(tv));
    }

    /** What the board is drawn as: a custom board (in its base look), or null for the look as chosen. */
    void restyle(com.gathin.broadcast.BoardSpec custom) {
        if (custom == null) {
            bug.custom(null);
            bug.style(BoardPrefs.style(getContext()));
        } else {
            bug.custom(custom);
        }
        shownCustom = custom;
        layoutAll();
        bug.invalidate();
    }

    /** The match on the board for the editor (its clubs' colours and names, the competition), else the invented clubs. */
    com.gathin.broadcast.BoardDesigner.Brief briefNow() {
        com.gathin.broadcast.BoardDesigner.Brief b = new com.gathin.broadcast.BoardDesigner.Brief();
        Game g = game;
        if (g != null) {
            b.home = hex(g.homeColor);
            b.away = hex(g.awayColor);
            b.homeName = full(g.homeFull, g.homeName);
            b.awayName = full(g.awayFull, g.awayName);
            b.comp = boardComp(g);
            b.compSlug = g.slug == null ? "" : g.slug;
        } else {
            b.home = com.gathin.broadcast.EditorStill.HOME_COLOUR;
            b.away = com.gathin.broadcast.EditorStill.AWAY_COLOUR;
            b.homeName = com.gathin.broadcast.EditorStill.HOME_NAME;
            b.awayName = com.gathin.broadcast.EditorStill.AWAY_NAME;
            b.comp = "Northern Shield";
        }
        return b;
    }

    // ---- shown only over plain full-screen playback (BoardGate) -----------------------------------------------------

    /** Whether the picture is full screen with nothing over it (the live screen says); null: always (previews). */
    private java.util.function.BooleanSupplier plain;
    /** Something covers the picture: everything of the board is away (at once) until it is clear again. */
    private boolean covered;
    /** A moment's pop-up held back while covered: shown once the picture is clear, if still fresh. */
    private BoardGate.Held held;
    /** When each alert waiting to slide out was queued, and when the board was last covered. */
    private final java.util.IdentityHashMap<BoardDrop.Drop, Long> queuedAt = new java.util.IdentityHashMap<>();

    /** The live screen's test for plain full-screen playback with nothing over the picture. */
    void showOnlyWhen(java.util.function.BooleanSupplier s) {
        plain = s;
    }

    /** The live screen's panels came or went (or a number, or a question): away at once, or back. */
    void coverChanged() {
        if (started) update();
    }

    private boolean clear() {
        return plain == null || plain.getAsBoolean();
    }

    /**
     * When the live screen last went (another screen over it); back after longer than a moment stays fresh, the board
     * catches up (the first reading and summary since) without popping up what happened meanwhile.
     */
    private long stoppedAt;
    private boolean catchingUp, summaryCatchUp, readSinceStart;

    /** Still catching up after coming back: a goal, a kick-off or an event seen now is not popped up. */
    private boolean staleSinceStop(long now) {
        return catchingUp;
    }

    /**
     * Covered now: everything away at once (the layer hidden, a moment's pop-up let go, the menu closed, a move kept).
     * Clear again: shown, alerts gone stale meanwhile let go, and a pop-up held back shown if it is still fresh.
     */
    private boolean gate(long now) {
        boolean clear = clear();
        if (!clear && !covered) {
            covered = true;
            setVisibility(INVISIBLE);
            menu.close();
            editor.close();
            endMove(true);
            if (popup != null) {
                BroadcastView v = popup;
                popup = null;
                popupKind = "";
                v.stop();
                removeView(v);
            }
        } else if (clear && covered) {
            covered = false;
            setVisibility(VISIBLE);
            for (Iterator<BoardDrop.Drop> it = drops.iterator(); it.hasNext(); ) {
                BoardDrop.Drop d = it.next();
                if (BoardGate.stale(queuedAt.get(d), now)) {
                    it.remove();
                    queuedAt.remove(d);
                }
            }
        }
        if (clear && held != null && game != null && !popupShowing()) {
            BoardGate.Held p = held;
            held = null;
            if (BoardGate.fresh(p.kind, p.at, now)) popup(p.kind, p.score, p.scorer, p.minute);
        }
        return clear;
    }

    // ---- on and off ----------------------------------------------------------------------------------------------------

    /** The live screen is up: the board looks after the channel playing. */
    void start() {
        if (started) return;
        started = true;
        // Back after longer than a moment stays fresh: what changed meanwhile is caught up with, not popped up.
        boolean longAway = stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > BoardGate.MOMENT_MS;
        catchingUp = longAway;
        summaryCatchUp = longAway;
        readSinceStart = false;
        // The channel's places read again (Settings may have put them back to the standard ones meanwhile): the board, and
        // the strap folding into it, where the family put it.
        place = null;
        tv.live.addListener(this);
        restyle(MyBoardStore.inUse(getContext()));
        Sfx.warm(getContext(), Sfx.SWISH, Sfx.SCORE, "goal-" + BoardPrefs.goalSound(getContext()));
        h.post(tick);
    }

    /** The live screen gone: everything away at once. */
    void stop() {
        started = false;
        stoppedAt = System.currentTimeMillis();
        tv.live.removeListener(this);
        h.removeCallbacksAndMessages(null);
        if (popup != null) popup.stop();
        menu.close();
        editor.close();
        card.away();
        endMove(false);
        hideAll();
        // Covered by whatever came over the live screen: shown again once it is back and the picture is clear.
        covered = true;
        setVisibility(INVISIBLE);
    }

    @Override public void liveChanged() {
        if (started) update();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            // A reading that comes back wrong never closes the app: noted (If something goes wrong), and the board goes on.
            try {
                update();
            } catch (RuntimeException e) {
                Exec.trouble(e);
            }
            h.postDelayed(this, 1000L);
        }
    };

    /**
     * The board's drawing (the bug, the cover, what slides out, the cards): an error in any of it is noted and the board
     * put away until its next reading, never the app closed. Nothing is made here.
     */
    @Override protected void dispatchDraw(Canvas c) {
        int saved = c.getSaveCount();
        try {
            super.dispatchDraw(c);
        } catch (RuntimeException e) {
            c.restoreToCount(saved);
            Exec.trouble(e);
            drawFailed = true;
            h.post(drawAway);
        }
    }

    /** The board's drawing failed: away until the channel changes or the board comes back. */
    private boolean drawFailed;
    private final Runnable drawAway = () -> {
        if (!drawFailed) return;
        drawFailed = false;
        hideAll();
    };

    private void update() {
        long now = System.currentTimeMillis();
        Channel c = tv.live.channel();
        String key = c == null ? "" : c.key;
        if (!key.equals(chKey)) channelChanged(c, now);
        ch = c;
        boolean playing = c != null && tv.live.state() == LiveTv.PLAYING;
        // The hub holds a game back to the channel watched now (or just before it was opened).
        if (playing) InStep.watching(c.key, now);
        if (c != null && !finding && now >= nextFind) find(c, now);
        if (game != null && !polling && now >= nextPoll) poll();
        if (game != null && !summarising && now >= nextSummary && (game.state != Game.PRE || game.kickoff - now < 3_600_000L)) summary();
        if (!editor.open) {
            com.gathin.broadcast.BoardSpec want = MyBoardStore.inUse(getContext());
            if (want != shownCustom) restyle(want);
        }
        show(now, playing);
        varTick(now);
        // The menu up as the match is found or read: its line under the title says so.
        if (menu.open && !noteNow().equals(menuNote)) menu.refresh();
        // Caught up once a reading taken since coming back has been shown (what it brought was not popped up).
        if (catchingUp && readSinceStart) catchingUp = false;
    }

    private void channelChanged(Channel c, long now) {
        chKey = c == null ? "" : c.key;
        place = null;
        letGo();
        nextFind = now;
        card.away();
        endMove(false);
    }

    // ---- the game: found, read, summarised (off the main thread) ---------------------------------------------------

    /** Pure: the games a channel can be showing for the board: live, or kicking off soon (twenty minutes, or a little late). */
    static List<Game> relevant(List<Game> games, long now) {
        List<Game> out = new ArrayList<>();
        if (games == null) return out;
        for (Game g : games) {
            if (g == null || g.id == null) continue;
            if (g.state == Game.LIVE) out.add(g);
            else if (g.state == Game.PRE && g.kickoff > 0 && g.kickoff - now <= BoardClock.PRE_MS + 5 * 60_000L
                    && now - g.kickoff <= BoardClock.LATE_MS) out.add(g);
        }
        return out;
    }

    private void find(Channel c, long now) {
        finding = true;
        Library lib = Library.get(tv);
        String key = c.key;
        Exec.io(() -> {
            Game g = null;
            try {
                g = Matching.gameOn(lib, c, now, relevant(SportsData.football(5 * 60_000L), now));
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // nothing found this time
            }
            Game got = g;
            Exec.main(() -> {
                finding = false;
                if (!started || !key.equals(chKey)) return;
                long t = System.currentTimeMillis();
                nextFind = t + (got == null && game == null ? FIND_MS : REFIND_MS);
                // Another game on the channel now (a double-header): taken once the one on is no longer live.
                if (got != null && (game == null || !got.id.equals(game.id) && game.state != Game.LIVE)) take(got, t);
            });
        });
    }

    /** The board on for this game from now. */
    private void take(Game g, long now) {
        letGo();
        game = g;
        time.anchor = BoardClock.anchorOf(BoardPrefs.anchor(getContext()), g.id);
        lastChange = now;
        bug.game(g.id);
        bug.competition(g.slug, boardComp(g));
        bug.names(full(g.homeFull, g.homeName), full(g.awayFull, g.awayName));
        bug.teams(g.homeId, g.awayId);
        bug.goalLook(BoardPrefs.goalLook(getContext()));
        bug.crests(g.homeLogo != null || g.awayLogo != null);
        loadImages();
        nextPoll = 0;
        nextSummary = 0;
    }

    /** The game let go (another channel, or its day done): what was kept of it too. */
    private void letGo() {
        game = null;
        time.reset();
        Arrays.fill(announced, 0);
        varOut.off();
        varMovedKey = null;
        shownScore = null;
        phaseWas = null;
        htSince = ftSince = -1;
        changeSeen = kickSeen = 0;
        firstSummary = true;
        eventKeys.clear();
        varKeys.clear();
        pending.clear();
        addedReads.clear();
        drops.clear();
        queuedAt.clear();
        held = null;
        espnHome = new ArrayList<>();
        espnAway = new ArrayList<>();
        restartUntil = 0;
        addedOutFor = 0;
        lineupsTried = 0;
        h.removeCallbacks(nextDropRun);
        h.removeCallbacks(hideDropRun);
        dropping = false;
        drop.gone();
    }

    private static String full(String a, String b) {
        return a != null && !a.isEmpty() ? a : b == null ? "" : b;
    }

    /**
     * Pure: the competition's name the board is given for g (By competition's look picks by it, the competitions' looks
     * title it), and the strap with it: its name, else its tag.
     */
    static String boardComp(Game g) {
        return full(g.leagueName, g.league);
    }

    private void poll() {
        polling = true;
        Game g0 = game;
        Exec.io(() -> {
            Game fresh = null;
            try {
                LocalDate day = g0.kickoff > 0 ? Instant.ofEpochMilli(g0.kickoff).atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();
                for (Game x : Espn.scoreboard(g0.slug, day, 15_000L)) {
                    if (g0.id.equals(x.id)) {
                        fresh = x;
                        break;
                    }
                }
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // read again next time
            }
            Game got = fresh;
            long seen = System.currentTimeMillis();
            Exec.main(() -> {
                polling = false;
                if (game == null || !game.id.equals(g0.id)) return;
                if (got != null) took(got, seen);
                long t = System.currentTimeMillis();
                Game cur = game;
                nextPoll = t + (cur.state == Game.LIVE ? POLL_LIVE_MS
                        : cur.state == Game.PRE && cur.kickoff - t < 30 * 60_000L ? POLL_SOON_MS : POLL_IDLE_MS);
            });
        });
    }

    /** A reading of the scores, seen at seen (main thread). */
    void took(Game g, long seen) {
        BoardClock.Reading last = readings.peekLast();
        BoardClock.Reading r = BoardClock.Reading.of(g, seen);
        if (last != null) {
            // The scores saw a kick-off (or the second half's): the card asks for OK as it happens on the screen.
            if (last.state == Game.PRE && r.state == Game.LIVE) kickoffSeen(seen, false);
            // (Not extra time's: the end of normal time is a break too now, Whistle.atBreak, and the card sets the second half.)
            else if (last.halftime && r.state == Game.LIVE && !r.halftime && periodOf(g) == 2) kickoffSeen(seen, true);
            if (last.home != r.home || last.away != r.away || last.state != r.state || last.halftime != r.halftime) changeSeen = seen;
        }
        // Kept, and for the whistle: its flip (half time, full time) as it came; the board puts the channel's wait on it.
        time.took(g, seen);
        SportsData.note(g, seen);
        game = g;
        readSinceStart = true;
    }

    private void summary() {
        summarising = true;
        Game g0 = game;
        // FotMob only while the game is on and the board or the cover shows (its clock is what the whistle stops; with
        // the board switched off it goes on underneath, so it is right when it comes back).
        boolean live = g0.state == Game.LIVE && (BoardPrefs.on(getContext()) || BoardPrefs.logoOn(getContext()));
        Exec.io(() -> {
            Espn.Summary s = null;
            try {
                s = Espn.summary(g0.slug, g0.id, 30_000L);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // read again next time
            }
            Espn.Summary got = s;
            long seen = System.currentTimeMillis();
            // FotMob's status while the game is on (its own times give each half's length to the second: the whistle) and
            // its flag on a VAR check; its once-a-minute read, shared with the game's page.
            BoardTime.FotMobRead fm = null;
            if (live) {
                try {
                    fm = BoardTime.readFotMob(g0);
                } catch (RuntimeException | OutOfMemoryError ignored) {
                    // read again next time
                }
            }
            BoardTime.FotMobRead fotmob = fm;
            Exec.main(() -> {
                summarising = false;
                nextSummary = System.currentTimeMillis() + (g0.state == Game.LIVE ? SUMMARY_MS : 120_000L);
                if (game == null || !game.id.equals(g0.id)) return;
                if (fotmob != null) fotmobRead(fotmob, seen);
                if (got == null) return;
                summarised(got, seen);
            });
        });
    }

    /**
     * FotMob's read for the board's game (main thread): its status for the whistle (as it came: the whistle puts the
     * channel's wait on it), and its flag on a VAR check once the picture has reached the read (FotMob runs ahead).
     */
    void fotmobRead(BoardTime.FotMobRead fm, long seen) {
        Game g = game;
        if (g == null || fm == null) return;
        long at = fm.at > 0 ? fm.at : seen;
        time.fotmob(at, g.id, fm.status);
        boolean flag = fm.varPending;
        String id = g.id;
        if (flag || varOut.isOut(id)) pending.add(id, stamp(g, at, -1), () -> varOut.flag(id, flag));
    }

    /** The game's summary read at seen: the minutes added, the elevens, and what happened, each waiting for the picture. */
    void summarised(Espn.Summary s, long seen) {
        for (int i = 0; i < 4; i++) if (s.added[i] > 0) announced[i] = s.added[i];
        // The referee plays at least the minutes added: the whistle never has a half over on the channel's clock before them.
        if (game != null) time.added(game.id, announced);
        // The minutes added as this read had them: on the board once the picture reaches it (they give nothing else away).
        Game g0 = game;
        addedReads.add(new com.gathin.broadcast.AsSeen.Stamp(seen, phaseOf(g0), periodOf(g0), -1, -1, -1), announced.clone());
        if (!s.homeLineup.isEmpty()) espnHome = new ArrayList<>(s.homeLineup);
        if (!s.awayLineup.isEmpty()) espnAway = new ArrayList<>(s.awayLineup);
        homeShape = s.homeFormation == null ? "" : s.homeFormation;
        awayShape = s.awayFormation == null ? "" : s.awayFormation;
        // The first summary after a long time away is caught up with too (its events not said again).
        boolean first = firstSummary || summaryCatchUp;
        firstSummary = false;
        summaryCatchUp = false;
        List<String[]> subs = new ArrayList<>();
        for (String[] e : s.events) {
            if (e == null || e.length < 6 || !eventKeys.add(e[4])) continue;
            // What happened before the board came on is not said again.
            if (first) continue;
            changeSeen = seen;
            String what = e[1] == null ? "" : e[1].toLowerCase(Locale.ROOT);
            if (what.contains("substitution")) subs.add(new String[]{"sub", e[0], e[3], e[2], "", e[5], ""});
            else if (what.contains("red card")) pend(seen, cardDrop("RED CARD", RED, e), e[0]);
            else if (what.contains("yellow card")) pend(seen, cardDrop("YELLOW CARD", BoardPrefs.YELLOW, e), e[0]);
        }
        for (List<String[]> grp : SubsPage.group(subs)) pend(seen, subsDrop(grp), grp.get(0)[1]);
        if (BoardPrefs.varExplainedOn(getContext())) {
            List<VarExplained.Seen> seenVar = VarExplained.scan(s.varLines);
            // The commentary gone on past a check read before (its next line is not VAR's): once the picture reaches this
            // read, the check out beside the board is over (VarCheck); a check new in this read comes out first.
            String check = null;
            for (VarExplained.Seen v : seenVar) if (v != null && v.about != null) check = v.key;
            if (check != null && varKeys.contains(check) && !check.equals(varMovedKey) && com.gathin.broadcast.VarCheck.movedOn(s.varLines)) {
                varMovedKey = check;
                String id = g0 == null ? null : g0.id;
                if (id != null) pending.add(id, stamp(g0, seen, -1), () -> varOn(id));
            }
            for (VarExplained.Seen v : seenVar) {
                if (v == null || !varKeys.add(v.key) || first) continue;
                BoardDrop.Drop d = varDrop(v);
                if (d != null) pend(seen, d, v.call != null ? v.call.minute : null);
            }
        }
    }

    /**
     * An alert the summary read at seen brought, held until the picture reaches it (AsSeen): the channel's delay since then
     * (the longest a channel runs behind while it has none of its own and its clock was not set at kick-off), and the
     * board's clock past its minute when it has one; one gone stale meanwhile is let go.
     */
    private void pend(long seen, BoardDrop.Drop d, String minute) {
        Game g = game;
        if (d == null || g == null) return;
        pending.add(g.id, stamp(g, seen, com.gathin.broadcast.AsSeen.minuteAt(minute)), () -> drop(d));
    }

    /** Pure: what a read of game g at seen could give away (AsSeen): the game as the scores had it, its moment (upTo). */
    static com.gathin.broadcast.AsSeen.Stamp stamp(Game g, long seen, int upTo) {
        return new com.gathin.broadcast.AsSeen.Stamp(seen, phaseOf(g), periodOf(g), upTo, -1, -1);
    }

    /** Pure: the game's state for AsSeen (BEFORE, PLAY, BREAK, OVER). */
    static int phaseOf(Game g) {
        if (g == null) return com.gathin.broadcast.AsSeen.PLAY;
        return g.state == Game.POST ? com.gathin.broadcast.AsSeen.OVER : g.state == Game.PRE ? com.gathin.broadcast.AsSeen.BEFORE
                : g.halftime ? com.gathin.broadcast.AsSeen.BREAK : com.gathin.broadcast.AsSeen.PLAY;
    }

    /** Pure: the game's half for AsSeen: ESPN's own, else its minute's (1 when neither says). */
    static int periodOf(Game g) {
        if (g == null) return 0;
        if (g.period >= 1 && g.period <= 4) return g.period;
        int p = BoardClock.periodOfDetail(g.detail);
        return p > 0 ? p : 1;
    }

    /**
     * Pure: where the picture is for AsSeen: the reading the screen has reached (before kick-off, in play, half time, full
     * time), the board's clock in step with it (seconds; none at a break), whether that clock is the channel's own (set at
     * kick-off, or the channel's delay set for it), the channel's delay, the board's score.
     */
    static com.gathin.broadcast.AsSeen.Picture picture(BoardClock.Reading r, String clock, long now, boolean known, long waitMs) {
        com.gathin.broadcast.AsSeen.Picture p = new com.gathin.broadcast.AsSeen.Picture();
        p.now = now;
        p.known = known;
        p.lagMs = waitMs;
        if (r == null) return p;
        p.state = r.state == Game.POST ? com.gathin.broadcast.AsSeen.OVER : r.state == Game.PRE ? com.gathin.broadcast.AsSeen.BEFORE
                : r.halftime ? com.gathin.broadcast.AsSeen.BREAK : com.gathin.broadcast.AsSeen.PLAY;
        p.period = BoardClock.periodOf(clock, r.detail);
        p.secs = p.state == com.gathin.broadcast.AsSeen.PLAY ? com.gathin.broadcast.SubsTiming.clockSeconds(clock) : -1;
        p.home = r.state == Game.PRE ? 0 : r.home;
        p.away = r.state == Game.PRE ? 0 : r.away;
        return p;
    }

    /** The minutes added as they stood when the picture was where it is now (none put up until a read is reached). */
    private int[] addedNow() {
        int[] a = addedReads.pick(picture);
        return a == null ? new int[4] : a;
    }

    // ---- the alerts' words (as the launcher's) ------------------------------------------------------------------------

    private BoardDrop.Drop own(String chip, int colour) {
        BoardDrop.Drop d = new BoardDrop.Drop();
        Game g = game;
        d.gameId = g == null ? null : g.id;
        d.chip = chip;
        d.colour = colour;
        d.own = true;
        if (g != null) {
            d.homeColour = BoardBug.colour(hex(g.homeColor), hex(g.homeAlt));
            d.awayColour = BoardBug.colour(hex(g.awayColor), hex(g.awayAlt));
            d.homeCrest = crestHome;
            d.awayCrest = crestAway;
        }
        return d;
    }

    /** A card ({minute, what, player, side, key, second}): who and when, the club's colour at its far end. */
    BoardDrop.Drop cardDrop(String chip, int colour, String[] e) {
        BoardDrop.Drop d = own(chip, colour);
        d.side = "home".equals(e[3]) ? 1 : "away".equals(e[3]) ? 2 : 0;
        d.line = (nz(e[2]) + " " + nz(e[0])).trim();
        d.title = d.line;
        d.what = chip + " " + d.line;
        return d;
    }

    /** One club's substitutions at once ({type, minute, side, on, on id, off, off id}): OFF, then ON. */
    BoardDrop.Drop subsDrop(List<String[]> group) {
        if (group == null || group.isEmpty()) return null;
        BoardDrop.Drop d = own("SUBSTITUTION", SUB);
        String side = group.get(0)[2];
        d.side = "home".equals(side) ? 1 : "away".equals(side) ? 2 : 0;
        d.subs = SubsPage.of(group);
        d.subs.colour = d.side == 2 ? d.awayColour : d.homeColour;
        d.title = String.join(", ", d.subs.on);
        d.line = d.subs.minute;
        d.what = "SUBSTITUTION " + d.title;
        return d;
    }

    /** VAR explained: a check under way (out until its decision), or a decision with its why on the page after. */
    BoardDrop.Drop varDrop(VarExplained.Seen v) {
        BoardDrop.Drop d = own("VAR", VarExplained.CYAN);
        if (v.call != null) {
            String why = VarExplained.whyOf(v.call, 0).text;
            String head = VarExplained.outcome(v.call), line = VarExplained.callLine(v.call.onField);
            d.title = head;
            d.line = line;
            d.pages = why == null || why.isEmpty() ? new String[][]{{head, line}} : new String[][]{{head, line}, {"Why", why}};
            d.what = "VAR " + head;
            return d;
        }
        if (v.about == null || v.about.length < 3) return null;
        d.varCheck = true;
        d.title = VarExplained.checkTitle(v.about[0]);
        d.line = VarExplained.checkLine(v.about[1], v.about[2]);
        d.what = "VAR " + d.title;
        return d;
    }

    /** A goal beside the board (the pop-up after it switched off, or it didn't come): the scorer and the minute. */
    BoardDrop.Drop goalDrop(int side, String scorer, String minute) {
        BoardDrop.Drop d = own("GOAL", GOAL);
        d.side = side;
        d.line = (nz(scorer) + " " + nz(minute)).trim();
        d.title = d.line.isEmpty() ? "GOAL" : d.line;
        d.key = game == null ? null : game.id + "|" + shownScore;
        d.what = "GOAL " + d.line;
        return d;
    }

    /** The kick-off, half time, the second half or full time beside the board (the pop-ups switched off for the first). */
    BoardDrop.Drop momentDrop(String chip, String score) {
        BoardDrop.Drop d = own(chip, BoardDrop.PLAIN);
        Game g = game;
        String home = g == null ? "" : full(g.homeName, g.home), away = g == null ? "" : full(g.awayName, g.away);
        d.title = score == null ? home + " v " + away : home + " " + score + " " + away;
        d.what = chip + " " + d.title;
        return d;
    }

    /**
     * The minutes added (an ADDED TIME alert, its +N counting up), in added time's one red as the board paints it: its tab's
     * (AddedTime.RED, or one of My scoreboards' own), so the board and what comes out of it are the same red.
     */
    BoardDrop.Drop addedDrop(int n) {
        if (n <= 0) return null;
        BoardDrop.Drop d = own("ADDED TIME", bug.addedRed());
        d.addedMins = n;
        d.title = "+" + n + (n == 1 ? " minute" : " minutes");
        d.what = "ADDED TIME " + d.title;
        return d;
    }

    /** Both starting elevens beside the board (ESPN's, when FotMob has no line-ups for the pages yet); null without. */
    BoardDrop.Drop lineupsDrop() {
        Game g = game;
        if (g == null || starters(espnHome) < 11 || starters(espnAway) < 11) return null;
        BoardDrop.Drop d = own("LINE-UPS", BoardDrop.PLAIN);
        d.own = false;
        d.lineups = true;
        String home = full(g.homeName, g.home), away = full(g.awayName, g.away);
        d.title = home + " v " + away + (g.kickoff > 0 ? " · " + hhmm(g.kickoff) : "");
        d.line = eleven(espnHome, home, homeShape);
        d.line2 = eleven(espnAway, away, awayShape);
        d.what = "line-ups " + d.title;
        return d;
    }

    private static int starters(List<String[]> side) {
        int n = 0;
        for (String[] p : side) if (p != null && p.length > Espn.P_STARTER && "1".equals(p[Espn.P_STARTER])) n++;
        return n;
    }

    /** Pure: a club's eleven as the line-ups beside the board say it: "Arsenal (4-3-3): Raya, White, Saliba…". */
    static String eleven(List<String[]> side, String club, String shape) {
        StringBuilder b = new StringBuilder();
        for (String[] p : side) {
            if (p == null || p.length <= Espn.P_SHORT || !"1".equals(p[Espn.P_STARTER])) continue;
            String n = p[Espn.P_SHORT] != null && !p[Espn.P_SHORT].isEmpty() ? p[Espn.P_SHORT] : nz(p[Espn.P_NAME]);
            if (n.isEmpty()) continue;
            if (b.length() > 0) b.append(", ");
            b.append(n);
        }
        return club + (shape == null || shape.isEmpty() ? "" : " (" + shape + ")") + ": " + b;
    }

    // ---- the board as the screen shows it ---------------------------------------------------------------------------------

    /** Pure: an ESPN colour ("ffff00", "#ff0000") as ARGB; 0 when there is none. */
    static int hex(String hex) {
        if (hex == null) return 0;
        String s = hex.trim().replace("#", "");
        if (s.length() != 6) return 0;
        try {
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static BoardBug.Look base(Game g) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = BoardClock.code(g.home, g.homeName);
        l.away = BoardClock.code(g.away, g.awayName);
        l.tag = BoardClock.tag(g.leagueName);
        int hc = hex(g.homeColor), ha = hex(g.homeAlt), ac = hex(g.awayColor), aa = hex(g.awayAlt);
        l.homeColour = BoardBug.colour(hc, ha);
        l.awayColour = BoardBug.colour(ac, aa);
        l.homeOwn = BoardBug.onWhite(hc, ha);
        l.awayOwn = BoardBug.onWhite(ac, aa);
        return l;
    }

    /** Pure: the board for game g as reading r shows it (its running clock is set after, as the screen has it). */
    static BoardBug.Look look(Game g, BoardClock.Reading r) {
        BoardBug.Look l = base(g);
        l.score = r.home + "–" + r.away;
        if (r.state == Game.POST) {
            l.clock = "FT";
        } else if (r.halftime) {
            l.clock = "HT";
        } else {
            l.live = true;
            l.clock = r.detail;
        }
        return l;
    }

    /** Pure: the board before kick-off: the clubs, "v", and the kick-off's time on its clock. */
    static BoardBug.Look preLook(Game g) {
        BoardBug.Look l = base(g);
        l.score = "v";
        l.clock = g.kickoff > 0 ? hhmm(g.kickoff) : "";
        return l;
    }

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private static String hhmm(long t) {
        return Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(HH_MM);
    }

    /** The wait for the channel playing, in ms. */
    private long waitMs() {
        return InStep.delay(getContext(), chKey) * 1000L;
    }

    private void show(long now, boolean playing) {
        Context c = getContext();
        Game g = game;
        // Only over plain full-screen playback: covered, all of it away at once (BoardGate); the game is still followed.
        boolean clear = gate(now);
        // The menu's two switches, as the launcher's over TiviMate: Scoreboard off (boardShown), the cover alone (as its
        // own switch says), nothing popping up, the game still followed underneath so it is right when it comes back.
        boolean only = !BoardPrefs.on(c);
        if (g == null || !playing) {
            if (moving == 0) {
                boardUp(false);
                logoUp(false);
            }
            return;
        }
        long screenNow = now - waitMs();
        // The reading the screen has reached, with half time or full time as soon as the whistle reaches the picture
        // (BoardTime: Whistle, as the launcher's board), never before the half's end; and the clock while the half runs.
        BoardTime.Shown at = time.at(g.id, now, waitMs());
        BoardClock.Reading r = at.r;
        if (r != null && r.halftime) {
            if (htSince < 0) htSince = now;
        } else {
            htSince = -1;
        }
        if (r != null && r.state == Game.POST && ftSince < 0) ftSince = now;
        int phase = BoardClock.phase(r, g.kickoff, now, htSince < 0 ? -1 : now - htSince, ftSince < 0 ? -1 : now - ftSince,
                dropping || drop.isOut());
        if (phase == BoardClock.AWAY && r != null && r.state == Game.POST) {
            // Its day done: the board lets the game go (another may come on the channel).
            boardUp(false);
            logoUp(false);
            letGo();
            nextFind = now + REFIND_MS;
            return;
        }
        BoardBug.Look l = r == null || r.state == Game.PRE ? preLook(g) : look(g, r);
        // The clock: counted from the kick-off on the screen (OK at the card), the channel's own; else the scores', counting
        // on through the minutes added too, never stopping or stepping back by a little (BoardTime).
        if (l.live && r != null && at.secs >= 0) l.clock = BoardClock.clockWords(at.secs);
        // Where the picture is (its clock the channel's own once set at kick-off, or the channel's delay set for it): what
        // waits for it (the minutes added, the summary's alerts) comes once it has reached it, never as the scores have it.
        picture = picture(r, l.clock, now, time.anchor != null || InStep.own(c, chKey), waitMs());
        BoardClock.Reading latest = readings.peekLast();
        if (latest != null && latest.state != Game.PRE) {
            picture.scoresHome = latest.home;
            picture.scoresAway = latest.away;
        }
        if (l.live && r != null) {
            // The minutes added as the scores had them when the picture was where it is now, in the board's tab (AddedTime:
            // the same for every look; no panel stands out here, so the tab keeps them).
            l.added = com.gathin.broadcast.AddedTime.boardAdded(BoardClock.addedWords(l.clock, r.detail, addedNow()), false);
            l.stoppage = BoardClock.inAddedTime(l.clock, r.detail);
            if (!l.added.isEmpty()) addedOut(BoardClock.periodOf(l.clock, r.detail), l.added);
        }
        // Followed with the board off too (nothing popped up then), so nothing stale pops up when it is on again.
        moments(r, screenNow, now, l.score, !only);
        pending.release(g.id, picture);
        if (r != null && r.state != Game.PRE) {
            if (shownScore != null && !shownScore.equals(l.score)) {
                // The score changed on the board (a goal, or one ruled out): a VAR check out is over (VarCheck).
                varOn(g.id);
                int side = BoardClock.goalSide(shownScore, l.score);
                if (side > 0 && !staleSinceStop(now) && !only) goalNow(side, l.score, r, now);
            }
            shownScore = l.score;
        }
        bug.style(BoardPrefs.style(c));
        bug.set(l);
        boolean popupOn = popup != null && !popup.isOver();
        boolean strap = popupOn && "strap".equals(popupKind);
        boolean moment = popupOn && Moments.boardMoment(popupKind), lineupsOn = popupOn && !moment && !strap;
        boolean awayForPopup = Moments.boardAwayForPopup(moment, restartWaiting(), !up);
        // The strap draws the board itself as it folds into it: the board away at once from the strap's first frame.
        boolean strapOver = strap && popup.started();
        if (strapOver && moving != 1) boardAway();
        boolean want = moving == 1 || (phase == BoardClock.PRE || phase == BoardClock.UP) && !awayForPopup && !lineupsOn && !strapOver;
        if (awayForPopup) swishBack = true;
        layoutAll();
        boardUp(want && clear && (!only || moving == 1));
        logoUp(clear && (moving == 2 || phase != BoardClock.AWAY && BoardPrefs.logoOn(c)));
        logo.live = l.live;
        logo.invalidate();
        guard(now);
    }

    /**
     * The minutes added out beside the board once a half (extra time's too), as soon as the picture has them (their tab
     * beside it after), as on the launcher and the phone.
     */
    private void addedOut(int period, String added) {
        if (period < 1 || period > 4 || (addedOutFor & (1 << period)) != 0) return;
        addedOutFor |= 1 << period;
        try {
            drop(addedDrop(Integer.parseInt(added.replace("+", "").trim())));
        } catch (NumberFormatException ignored) {
            // no minutes in it
        }
    }

    /**
     * The moments as the picture reaches them: the kick-off, half time, the second half, full time (the board's own, as
     * BoardTime has the whistle on the picture: its pop-up once, then the board goes as it does after it); the line-ups.
     * Followed always; popped up (or out beside the board) only when pop (the board shown).
     */
    private void moments(BoardClock.Reading r, long screenNow, long now, String score, boolean pop) {
        Game g = game;
        if (g == null) return;
        Context c = getContext();
        String was = phaseWas;
        String ph = r == null ? (g.state == Game.PRE ? "pre" : "")
                : Moments.phase(false, r.state, r.halftime, was, BoardClock.periodOfDetail(r.detail));
        if (!ph.isEmpty()) phaseWas = ph;
        String kind = was == null || ph.isEmpty() ? null : Moments.moment(was, ph);
        // Seen only now after the live screen was away too long (BoardGate): not popped up stale.
        boolean stale = staleSinceStop(now) || !pop;
        if (!stale) {
            if (kind != null) lastChange = now;
            String[] out = momentOut(was, ph, kind, BoardPrefs.popupOn(c));
            if (out[0] != null) popup(out[0], "kickoff".equals(kind) || r == null ? null : new int[]{r.home, r.away}, null, null);
            if (out[1] != null) drop(momentDrop(out[1], "KICK-OFF".equals(out[1]) ? null : score));
        }
        if (pop && "pre".equals(ph) && g.kickoff > 0 && BoardPrefs.lineupsOn(c) && Moments.lineupsDue(g.kickoff - screenNow)) {
            lineups(false);
        }
    }

    /**
     * Pure: what a change of phase on the picture (was to now; kind the pop-up it brings: Moments.moment) puts up: the
     * moment's pop-up when they are on (popups), else its alert beside the board; half time's alert, and full time's unless
     * its pop-up says it. {the pop-up's kind or null, the alert's chip or null}.
     */
    static String[] momentOut(String was, String now, String kind, boolean popups) {
        String pop = kind != null && popups ? kind : null, chip = null;
        if (kind != null && !popups) chip = "kickoff".equals(kind) ? "KICK-OFF" : "second".equals(kind) ? "SECOND HALF" : null;
        if (was != null && !was.equals(now)) {
            if ("ht".equals(now)) chip = "HALF TIME";
            else if ("ft".equals(now) && pop == null) chip = "FULL TIME";
        }
        return new String[]{pop, chip};
    }

    /** A goal on the screen: its moment on the board, its sound, and what it brings (the pop-up after it, or its scorer). */
    private void goalNow(int side, String score, BoardClock.Reading r, long now) {
        Context c = getContext();
        lastChange = now;
        if (up) {
            // drawn as the family chose (GoalLooks), the clubs' own animations found by ESPN's ids
            bug.goalLook(BoardPrefs.goalLook(c));
            if (game != null) bug.teams(game.homeId, game.awayId);
            bug.goal(side);
            Sfx.goal(c, BoardPrefs.goalSound(c));
        }
        String[] goal = nthGoal(game == null ? null : game.goals, side, score);
        String scorer = Moments.scorer(goal), minute = goal == null || goal.length < 3 ? "" : BroadcastData.minute(goal[2]);
        if (BoardPrefs.popupOn(c) && game != null) {
            restartLater(side, new int[]{r.home, r.away}, Moments.popupScorer(goal), scorer, minute);
        } else {
            drop(goalDrop(side, scorer, goal == null || goal.length < 3 ? "" : nz(goal[2])));
        }
    }

    /** Pure: the goal a new score brings for a side (its n-th, n the side's goals now), else its latest; null for none. */
    static String[] nthGoal(List<String[]> goals, int side, String score) {
        if (goals == null) return null;
        String want = side == 1 ? "home" : "away";
        int n = 0;
        try {
            String[] p = score.split("–");
            n = Integer.parseInt(p[side == 1 ? 0 : 1].trim());
        } catch (RuntimeException ignored) {
            // the latest, then
        }
        int k = 0;
        for (String[] g : goals) {
            if (g == null || g.length < 4 || !want.equals(g[3])) continue;
            if (++k == n) return g;
        }
        return Moments.lastGoal(goals, side);
    }

    /**
     * The pop-up after a goal, once play starts again (Moments: forty seconds after the goal reached the picture): the score,
     * then its scorer as the pop-up names them (popScorer); the alert beside the board (scorer) when it does not come.
     */
    private void restartLater(int side, int[] score, String popScorer, String scorer, String minute) {
        long at = SystemClock.uptimeMillis();
        restartUntil = at + Moments.RESTART_WAIT_MS;
        String id = game.id;
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (!started || game == null || !id.equals(game.id)) {
                    restartUntil = 0;
                    return;
                }
                int go = Moments.restartNow(SystemClock.uptimeMillis() - at, false, LivePlay.BLIND);
                if (go == 0) {
                    h.postDelayed(this, 1_000L);
                    return;
                }
                restartUntil = go > 0 ? SystemClock.uptimeMillis() + 3_000L : 0;
                if (go > 0) popup("restart", score, popScorer, minute);
                // Held back while the picture is covered: the pop-up comes then (or not at all), not an alert as well.
                if (go < 0 || popup == null && !popupMaking && held == null) drop(goalDrop(side, scorer, minute));
            }
        }, LivePlay.FIRST_MS);
    }

    private boolean restartWaiting() {
        return SystemClock.uptimeMillis() < restartUntil;
    }

    // ---- the pop-ups and the line-ups (the shared broadcast pages) ----------------------------------------------------

    static BroadcastData.Match match(Game g) {
        BroadcastData.Match m = new BroadcastData.Match();
        m.homeName = full(g.homeFull, g.homeName);
        m.awayName = full(g.awayFull, g.awayName);
        m.homeShort = g.home;
        m.awayShort = g.away;
        m.homeColor = hex(g.homeColor);
        m.awayColor = hex(g.awayColor);
        m.homeAlt = hex(g.homeAlt);
        m.awayAlt = hex(g.awayAlt);
        m.homeCrestUrl = g.homeLogo;
        m.awayCrestUrl = g.awayLogo;
        m.compLogoUrl = g.leagueLogo;
        m.compId = g.slug == null ? "" : g.slug;
        m.compName = g.leagueName == null ? "" : g.leagueName;
        m.venue = g.venue == null ? "" : g.venue;
        m.kickoff = g.kickoff;
        m.homeId = g.homeId == null ? "" : g.homeId;
        m.awayId = g.awayId == null ? "" : g.awayId;
        com.gathin.broadcast.Worn.Kit[] kits = com.gathin.broadcast.Worn.get(g.id);
        m.homeKit = kits == null || kits[0] == null ? "" : kits[0].type;
        m.awayKit = kits == null || kits[1] == null ? "" : kits[1].type;
        return m;
    }

    private boolean popupShowing() {
        return popup != null && !popup.isOver() || popupMaking || lineupsMaking;
    }

    /**
     * A moment's pop-up ("kickoff", "restart", "second", "full"), once each: in my scoreboard's look (the strap, which folds
     * into the board where it is), or the classic.
     */
    private void popup(String kind, int[] score, String scorer, String minute) {
        Game g = game;
        // The board's own pop-ups go with it: none while the menu's Scoreboard is off.
        if (g == null || !BoardPrefs.on(getContext())) return;
        String key = g.id + "|" + kind + (score == null ? "" : "|" + score[0] + "-" + score[1]);
        if (shown.contains(key) || popupShowing()) return;
        if (!clear()) {
            // Something covers the picture: held until it is clear (shown then while still fresh: BoardGate).
            held = new BoardGate.Held(kind, score, scorer, minute, System.currentTimeMillis());
            return;
        }
        shown.add(key);
        popupMaking = true;
        Context c = getContext();
        boolean strap = "strap".equals(BoardPrefs.popupStyle(c));
        BroadcastData.Match m = match(g);
        BoardBug.Style style = BoardPrefs.style(c);
        com.gathin.broadcast.BoardSpec custom = shownCustom;
        Bitmap comp = compPic, light = bug.competitionLight();
        float[] at = strap ? boardAt() : null;
        android.content.res.AssetManager assets = c.getAssets();
        Exec.io(() -> {
            JSONObject cfg = null;
            try {
                cfg = strap ? strapCfg(g, m, kind, score, scorer, minute, style, comp, light, at, custom)
                        : BroadcastData.withMark(assets, BroadcastData.kickoff(kind, m, score, scorer, minute));
            } catch (Exception | OutOfMemoryError ignored) {
                // none, then
            }
            JSONObject got = cfg;
            Exec.main(() -> {
                popupMaking = false;
                if (got == null || !started || popup != null || !BoardPrefs.on(getContext())) return;
                if (strap) clockFromBoard(got);
                showPopup(BroadcastView.page(getContext(), strap ? "strap" : "kickoff", got, this::popupGone), strap ? "strap" : kind);
            });
        });
    }

    /** The strap's clock from the board's own as it shows now, while it runs: the two agree as the strap folds into it. */
    private void clockFromBoard(JSONObject cfg) {
        BoardBug.Look l = bug.look();
        int secs = l.live ? clockSecs(l.clock) : -1;
        if (secs < 0) return;
        try {
            cfg.put("clock", secs);
        } catch (org.json.JSONException ignored) {
            // the strap's own clock, then
        }
    }

    private static final java.util.regex.Pattern MM_SS = java.util.regex.Pattern.compile("^(\\d{1,3}):(\\d{2})$");

    /** Pure: a running clock's words in seconds ("23:41": 1421, "90:05": 5405); -1 for any other ("HT", "FT", "1'", ""). */
    static int clockSecs(String clock) {
        java.util.regex.Matcher m = MM_SS.matcher(clock == null ? "" : clock.trim());
        return m.matches() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : -1;
    }

    /**
     * The strap's settings for game g: its board as it is drawn (the look, By competition's as the board resolves it for g;
     * the competition's logos it draws, for dark grounds and for light ones, null for none yet; the competition's name it
     * is given; each side's colour on it and the name it gives the club) and where it is (at: the board itself on a
     * 1920 x 1080 picture; null for the standard place). Off the main thread.
     */
    static JSONObject strapCfg(Game g, BroadcastData.Match m, String kind, int[] score, String scorer, String minute, BoardBug.Style style,
                               Bitmap compLogo, Bitmap compLight, float[] at) throws org.json.JSONException {
        return strapCfg(g, m, kind, score, scorer, minute, style, compLogo, compLight, at, null);
    }

    /** The same for one of My scoreboards in use (null: the look's own strap): Classic's strap in its colours (strap.js S.custom). */
    static JSONObject strapCfg(Game g, BroadcastData.Match m, String kind, int[] score, String scorer, String minute, BoardBug.Style style,
                               Bitmap compLogo, Bitmap compLight, float[] at, com.gathin.broadcast.BoardSpec custom)
            throws org.json.JSONException {
        m.compLogo = compLogo;
        m.compLight = compLight;
        m.compName = boardComp(g);
        m.homeBoard = BoardBug.colour(hex(g.homeColor), hex(g.homeAlt));
        m.awayBoard = BoardBug.colour(hex(g.awayColor), hex(g.awayAlt));
        m.homeBoardName = full(g.homeFull, g.homeName);
        m.awayBoardName = full(g.awayFull, g.awayName);
        return BroadcastData.strap(kind, m, score, scorer, minute, strapLook(style, g.slug, m.compName), at, "", custom);
    }

    /** Pure: the look the strap is drawn in: the board's own, By competition's as the board resolves it for the game. */
    static String strapLook(BoardBug.Style style, String slug, String leagueName) {
        return BroadcastData.strapLook(style.name(), slug, leagueName);
    }

    /**
     * The board itself on a 1920 x 1080 picture, for the strap to fold into and the board to come back to: where this
     * channel's places put it (the family's own from Move and resize first, else the last set on any channel, else the
     * standard place), at the size set, with the step it takes against burn-in, whether the board is up now or away (half
     * time resting, a pop-up, the live screen just back); never the strap page's own corner.
     */
    float[] boardAt() {
        int sw = getWidth(), sh = getHeight();
        if (sw <= 0 || sh <= 0) {
            // Not laid out yet: the place on the picture's own size.
            sw = 1920;
            sh = 1080;
        }
        int e = Math.max(2, Math.round(2f * sw / 1920f));
        int[] r = placeOf(place(), sw, sh, bug.tall(), bug::natural);
        int[] d = moving != 0 ? new int[]{0, 0} : BoardClock.drift(System.currentTimeMillis(), e);
        float k = 1920f / sw, kh = 1080f / sh;
        return new float[]{(r[0] + d[0]) * k, (r[1] + d[1]) * kh, r[2] * k, r[3] * kh};
    }

    /**
     * Pure: the board's place on a w x h screen from the channel's places p, as layoutAll puts it there: {x, y, width,
     * height}; tall its look's height for the height set (Stacked's taller), natural its own width for a height.
     */
    static int[] placeOf(BoardPrefs.Place p, int w, int h, float tall, java.util.function.IntUnaryOperator natural) {
        int bh = Math.max(8, Math.round(p.bh * h * tall));
        int bw = Math.max(1, Math.min(Math.round(w * 0.5f), natural.applyAsInt(bh)));
        int bx = Math.max(0, Math.min(Math.round(p.bx * w), w - bw)), by = Math.max(0, Math.min(Math.round(p.by * h), h - bh));
        return new int[]{bx, by, bw, bh};
    }

    /** The classic line-ups (FotMob's elevens), once, from seven minutes before kick-off on the picture; or now, asked. */
    void lineups(boolean asked) {
        Game g = game;
        // Not over a covered picture: asked for again each second while they are due.
        if (g == null || !asked && !clear()) return;
        String key = g.id + "|lineups";
        long now = System.currentTimeMillis();
        if (!asked && (shown.contains(key) || now - lineupsTried < Moments.RETRY_MS)) return;
        if (popupShowing()) return;
        lineupsMaking = true;
        lineupsTried = now;
        BroadcastData.Match m = match(g);
        File cache = getContext().getCacheDir();
        Exec.io(() -> {
            JSONObject cfg = null;
            try {
                cfg = BroadcastData.lineups(m, FotMob.detailsFor(g), cache);
            } catch (Exception | OutOfMemoryError ignored) {
                // asked again in a minute
            }
            JSONObject got = cfg;
            Exec.main(() -> {
                lineupsMaking = false;
                if (!started || game == null || !game.id.equals(g.id)) return;
                // The scoreboard switched off meanwhile: only asked for (the menu's OK) do they come.
                boolean boardOn = BoardPrefs.on(getContext());
                if (!asked && !boardOn) return;
                if (got == null) {
                    // FotMob has none yet: ESPN's elevens beside the board, once (or now, asked); none with it off.
                    BoardDrop.Drop d = lineupsDrop();
                    if (d != null && !boardOn) {
                        card.note("The line-ups go beside the scoreboard", "The scoreboard is off: switch it on to see them");
                        return;
                    }
                    if (d != null && (asked || !shown.contains(key))) {
                        shown.add(key);
                        drop(d);
                    } else if (asked) {
                        card.note("No line-ups yet", "The line-ups come about an hour before kick-off.");
                    }
                    return;
                }
                if (popup != null) return;
                shown.add(key);
                showPopup(BroadcastView.page(getContext(), "lineups", got, this::popupGone), "lineups");
            });
        });
    }

    private void showPopup(BroadcastView v, String kind) {
        popup = v;
        popupKind = kind;
        // Over the picture and the board, under the menu and the card.
        addView(v, indexOfChild(card), new LayoutParams(-1, -1));
        drop.fold(null);
        if ("strap".equals(kind)) h.postDelayed(strapWatch, STRAP_WATCH_MS);
    }

    /** How often a strap loading is looked at, for its first frame on the screen. */
    private static final long STRAP_WATCH_MS = 40L;

    /** The strap's first frame on the screen (its page loading meanwhile): the board away then, at once (show). */
    private final Runnable strapWatch = new Runnable() {
        @Override public void run() {
            BroadcastView v = popup;
            if (!started || v == null || v.isOver() || !"strap".equals(popupKind)) return;
            if (v.started()) update();
            else h.postDelayed(this, STRAP_WATCH_MS);
        }
    };

    private void popupGone(BroadcastView v) {
        if (v != popup) return;
        boolean wasStrap = "strap".equals(popupKind);
        popup = null;
        popupKind = "";
        removeView(v);
        h.removeCallbacks(strapWatch);
        if (wasStrap && started) {
            // The strap folded into the board and ends on its drawing of it: the board itself back at once, as it was.
            strapBack = true;
            update();
            strapBack = false;
        }
    }

    // ---- what slides out beside it ---------------------------------------------------------------------------------------

    void drop(BoardDrop.Drop d) {
        if (d == null) return;
        if (!d.lineups && !BoardPrefs.dropsOn(getContext())) return;
        // What slides out beside the board goes with it: nothing queued while the menu's Scoreboard is off.
        if (!BoardPrefs.on(getContext())) return;
        // A goal is said once.
        if (d.key != null && !shown.add("drop|" + d.key)) return;
        // Another event of the game (a card, a substitution, a goal, half or full time) while its VAR check is out or
        // waiting: play went on, the check is over (VarCheck).
        if (!"VAR".equals(d.chip) && d.addedMins <= 0 && !d.lineups && d.gameId != null) varOn(d.gameId);
        queuedAt.put(d, System.currentTimeMillis());
        if ("VAR".equals(d.chip)) {
            drops.addFirst(d);
            while (drops.size() > 4) queuedAt.remove(drops.removeLast());
        } else {
            drops.addLast(d);
            while (drops.size() > 4) queuedAt.remove(drops.removeFirst());
        }
        // A VAR decision while its check is out: the check turns into it now, not after the check's longest.
        if ("VAR".equals(d.chip) && !d.varCheck && varOut.isOut(d.gameId) && drop.isOut()) {
            hideDrop();
            return;
        }
        if (!dropping) nextDrop();
    }

    /**
     * The game has gone on past its VAR check, as the picture has it (the commentary's next line, FotMob's flag gone,
     * another event of the game, the score changing on the board): the check out comes off (VarCheck, never before its
     * least), and one still waiting its turn is let go.
     */
    private void varOn(String gameId) {
        if (gameId == null) return;
        varOut.movedOn(gameId);
        if (varOut.isOut(gameId)) return;
        boolean gone = false;
        for (Iterator<BoardDrop.Drop> it = drops.iterator(); it.hasNext(); ) {
            BoardDrop.Drop q = it.next();
            if (q.varCheck && gameId.equals(q.gameId)) {
                it.remove();
                queuedAt.remove(q);
                gone = true;
            }
        }
        if (gone) VarExplained.checkEnded(gameId, System.currentTimeMillis());
    }

    /** The VAR check out beside the board, each second: off as soon as it is over (VarCheck), or turned into its decision. */
    private void varTick(long now) {
        BoardDrop.Drop out = drop.isOut() ? drop.showing() : null;
        if (out == null) varOut.off();
        // None out, or the one out already going (hideDrop let it go).
        if (out == null || !out.varCheck || !varOut.isOut(out.gameId)) return;
        BoardDrop.Drop next = drops.peekFirst();
        boolean decided = next != null && "VAR".equals(next.chip) && !next.varCheck && out.gameId != null && out.gameId.equals(next.gameId);
        if (!varOut.over(now, decided)) return;
        if (!decided) VarExplained.checkEnded(out.gameId, now);
        varOut.off();
        hideDrop();
    }

    /** Pure: the longest a pop-out beside a board bh high may be, within room. */
    static int shortRoom(int bh, int room) {
        return Math.max(bh * 3, Math.min(room, Math.round(bh * SHORT)));
    }

    private void nextDrop() {
        h.removeCallbacks(nextDropRun);
        if (drops.isEmpty()) {
            dropping = false;
            return;
        }
        dropping = true;
        BoardDrop.Drop first = drops.peekFirst();
        boolean var = first != null && first.varCheck;
        // The board away (a pop-up, half time resting), or a goal's pop-up still to come: the alert waits for it.
        if (!up || bug.closing() || popup != null && !popup.isOver() || !var && restartWaiting()) {
            h.postDelayed(nextDropRun, 1_500L);
            return;
        }
        long wait = bug.bandLeft(System.currentTimeMillis());
        if (wait > 0) {
            h.postDelayed(nextDropRun, wait);
            return;
        }
        BoardDrop.Drop d = drops.pollFirst();
        if (d == null) {
            dropping = false;
            return;
        }
        queuedAt.remove(d);
        LayoutParams b = (LayoutParams) bug.getLayoutParams();
        int bh = b.height;
        drop.place(bug.stripLook(), boardH * TEXT, tuck, sideLeft, 0);
        BoardDrop.Drop out = drop.showing();
        boolean many = d.subs != null && Math.max(d.subs.on.length, d.subs.off.length) > 1;
        int dw = drop.widthFor(d, bh, many ? Math.max(shortRoom(bh, sideMax), Math.min(sideMax, bh * 16)) : shortRoom(bh, sideMax));
        beside(dw);
        // A VAR check out now turns over into its decision.
        if (out != null && out.varCheck && "VAR".equals(d.chip) && !d.varCheck) drop.turnTo(d);
        else drop.show(d);
        // A check out from now until it is over (varTick: VarCheck), its longest at most.
        if (d.varCheck) varOut.out(d.gameId, System.currentTimeMillis());
        else varOut.off();
        h.removeCallbacks(hideDropRun);
        h.postDelayed(hideDropRun, d.varCheck ? VarExplained.CHECK_MAX_MS
                : "VAR".equals(d.chip) ? Math.max(VarExplained.WHY_MS, drop.showFor(DROP_MS)) : drop.showFor(DROP_MS));
    }

    private void hideDrop() {
        h.removeCallbacks(hideDropRun);
        // A check whose decision is waiting: the decision turns it over in place.
        BoardDrop.Drop next = drops.peekFirst(), out = drop.showing();
        // The check out goes (folded away, or turned into its decision): nothing more to wait for on it.
        if (out != null && out.varCheck) varOut.off();
        if (out != null && out.varCheck && next != null && "VAR".equals(next.chip) && !next.varCheck) {
            nextDrop();
            return;
        }
        if (!drop.isOut()) {
            nextDrop();
            return;
        }
        drop.fold(this::nextDrop);
    }

    /** What slides out w wide, as high as the board and beside it, its near end tucked under it. */
    private void beside(int w) {
        LayoutParams b = (LayoutParams) bug.getLayoutParams();
        int left = sideLeft ? b.leftMargin + tuck - sideGap - w : b.leftMargin + b.width - tuck + sideGap;
        size(drop, w, b.height, left, b.topMargin);
    }

    // ---- up and away ----------------------------------------------------------------------------------------------------

    private void boardUp(boolean want) {
        if (want) {
            if (bug.getVisibility() != VISIBLE || bug.closing()) {
                boolean fresh = bug.getVisibility() != VISIBLE;
                bug.setVisibility(VISIBLE);
                // Back after the strap: all there at once, where the strap's drawing of it ended (no coming in, no swish).
                if (fresh) bug.shownAt(strapBack ? 1f : 0f);
                bug.open();
                // Back after a moment's pop-up: with the swish.
                if (fresh && swishBack && !strapBack && BoardPrefs.swishOn(getContext())) Sfx.play(getContext(), Sfx.SWISH, -0.5f, 0.7f);
            }
            swishBack = false;
            up = true;
            return;
        }
        if (!up) return;
        up = false;
        drop.fold(null);
        if (!bug.closing()) bug.close(() -> {
            if (!up) bug.setVisibility(INVISIBLE);
        });
    }

    /** The board away at once, not closing as a broadcaster's goes (the strap over it draws it itself). */
    private void boardAway() {
        if (!up && bug.getVisibility() != VISIBLE) return;
        up = false;
        drop.fold(null);
        bug.setVisibility(INVISIBLE);
    }

    private void logoUp(boolean want) {
        logo.setVisibility(want ? VISIBLE : INVISIBLE);
    }

    private void hideAll() {
        up = false;
        bug.setVisibility(INVISIBLE);
        drop.gone();
        logo.setVisibility(INVISIBLE);
        if (popup != null) {
            BroadcastView v = popup;
            popup = null;
            removeView(v);
        }
    }

    /** Burn-in: a step round a slow circle each minute; dimmer after five minutes without a goal. */
    private void guard(long now) {
        int[] d = moving != 0 ? new int[]{0, 0} : BoardClock.drift(now, edge);
        bug.setTranslationX(d[0]);
        bug.setTranslationY(d[1]);
        drop.setTranslationX(d[0]);
        drop.setTranslationY(d[1]);
        logo.setTranslationX(d[0]);
        logo.setTranslationY(d[1]);
        boolean dim = moving == 0 && !menu.open && !editor.open && BoardClock.dimmed(now - lastChange);
        if (dim != dimmed) {
            dimmed = dim;
            bug.setLayerType(LAYER_TYPE_HARDWARE, dim ? dimPaint : null);
            logo.setLayerType(dim ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE, dim ? dimPaint : null);
        }
    }

    // ---- where it sits ----------------------------------------------------------------------------------------------------

    private BoardPrefs.Place place() {
        if (place == null) place = BoardPrefs.place(getContext(), chKey);
        return place;
    }

    /** The board as wide as its words need for its height, the cover where it goes, and the room beside the board. */
    private void layoutAll() {
        int sw = getWidth(), sh = getHeight();
        if (sw <= 0 || sh <= 0) return;
        BoardPrefs.Place p = place();
        edge = Math.max(2, Math.round(2f * sw / 1920f));
        bug.inset(edge);
        int[] at = placeOf(p, sw, sh, bug.tall(), bug::natural);
        int bx = at[0], by = at[1], bw = at[2], bh = at[3];
        boardH = Math.max(8, Math.round(p.bh * sh));
        int vw = bw + 2 * edge, vh = bh + 2 * edge;
        size(bug, vw, vh, bx - edge, by - edge);
        boardRect.set(bx - edge, by - edge, bx + bw + edge, by + bh + edge);
        int lx = Math.round(p.lx * sw), ly = Math.round(p.ly * sh), lw = Math.round(p.lw * sw), lh = Math.round(p.lh * sh);
        size(logo, lw + 2 * edge, lh + 2 * edge, lx - edge, ly - edge);
        logoRect.set(lx - edge, ly - edge, lx + lw + edge, ly + lh + edge);
        BoardBug.StripLook look = bug.stripLook();
        tuck = look.apart ? 0 : Math.max(2, Math.round(vh * look.round)) + 1;
        sideGap = look.apart ? Math.max(2, Math.round(boardH * 0.1f)) : 0;
        int margin = Math.round(sw * 0.02f), left = bx - edge, right = bx + bw + edge;
        int roomR = sw - margin - right, roomL = left - margin;
        boolean level = ly < by + bh && ly + lh > by;
        if (logo.getVisibility() == VISIBLE && level && lx >= right) roomR = Math.min(roomR, lx - margin / 2 - right);
        if (logo.getVisibility() == VISIBLE && level && lx + lw <= left) roomL = Math.min(roomL, left - lx - lw - margin / 2);
        sideLeft = roomR < boardH * 6 && roomL > roomR;
        int room = Math.max(boardH * 3, Math.min(Math.round(boardH * STRIP_W), sideLeft ? roomL : roomR) - sideGap);
        sideMax = room + tuck;
        if (drop.isOut()) beside(drop.getLayoutParams().width);
    }

    private static void size(View v, int w, int hh, int left, int top) {
        LayoutParams l = (LayoutParams) v.getLayoutParams();
        if (l.width == w && l.height == hh && l.leftMargin == left && l.topMargin == top) return;
        l.width = w;
        l.height = hh;
        l.leftMargin = left;
        l.topMargin = top;
        v.setLayoutParams(l);
    }

    private void moveBy(int dx, int dy) {
        BoardPrefs.Place p = place();
        float aspect = boardRect.height() > 0 && getHeight() > 0
                ? boardRect.width() / (float) boardRect.height() * getHeight() / (float) Math.max(1, getWidth()) : 3f;
        if (moving == 1) p.moveBoard(dx * MOVE_STEP, dy * MOVE_STEP, aspect);
        else p.moveLogo(dx * MOVE_STEP, dy * MOVE_STEP);
        layoutAll();
    }

    private void startMove(int what) {
        moving = what;
        bug.outline(what == 1);
        logo.ringed = what == 2;
        logo.invalidate();
        card.note(what == 1 ? "Move the scoreboard" : "Move the logo cover", "The arrows move it · OK when it's in place, Back to keep it");
        update();
    }

    private void endMove(boolean keep) {
        if (moving == 0) return;
        moving = 0;
        bug.outline(false);
        logo.ringed = false;
        logo.invalidate();
        if (keep && place != null) BoardPrefs.setPlace(getContext(), chKey, place);
        card.away();
    }

    // ---- the crests --------------------------------------------------------------------------------------------------------

    /** The crests and the competition's logo as the board draws them (the strap draws the same logo). */
    private Bitmap crestHome, crestAway, compPic;

    private void loadImages() {
        Game g = game;
        if (g == null) return;
        int px = Math.max(48, Ui.px(96));
        Runnable again = () -> {
            if (game == g) loadImages();
        };
        crestHome = Pics.get(g.homeLogo, px, px, Pics.FIT, again);
        crestAway = Pics.get(g.awayLogo, px, px, Pics.FIT, again);
        compPic = Pics.get(g.leagueLogo, px, px, Pics.FIT, again);
        bug.images(crestHome, crestAway, compPic);
    }

    // ---- the in-step card at each kick-off ---------------------------------------------------------------------------------

    private void kickoffSeen(long seen, boolean second) {
        kickSeen = seen;
        kickSecond = second;
        Context c = getContext();
        if (!InStep.asking(c) || !BoardPrefs.on(c) || ch == null || game == null) return;
        card.ask(second);
    }

    /** OK at the kick-off on the screen: the channel's wait from the scores' kick-off, the board's clock from this moment. */
    private void lineUp() {
        Context c = getContext();
        long pressed = System.currentTimeMillis();
        int d = InStep.justHappened(c, chKey, kickSeen);
        if (game != null) {
            long[] anchor = new long[]{kickSecond ? 45 * 60 : 0, pressed, kickSecond ? 2 : 1};
            time.anchor = anchor;
            BoardPrefs.setAnchor(c, BoardClock.anchorWords(game.id, anchor[0], anchor[1], (int) anchor[2]));
            steady.reset();
        }
        card.note("Clock set", "The scoreboard's clock is your channel's from here" + (d >= 0 ? " · " + InStep.words(d) : ""));
    }

    // ---- the remote (the live screen asks first) ---------------------------------------------------------------------------

    /** Whether the board has the remote now: its menu is up, the editor, or it is being moved. */
    boolean takesKeys() {
        return !covered && (moving != 0 || menu.open || editor.open);
    }

    /** Whether the scoreboard and logo menu is up. */
    boolean menuOpen() {
        return menu.open;
    }

    /** What the menu's lines say now (its values as they stand). */
    BoardMenu.MenuState menuState() {
        return menuHost.state();
    }

    /**
     * OK held while the channel plays full screen with nothing over it (the live screen says when): the scoreboard and logo
     * menu, on every channel, over a match or not, as the launcher's holdInPlayer over TiviMate (with no match on the
     * channel what it sets is kept for the next one: "No match on this channel now", "Reading the match", "Reading the
     * scores" until there is one). False when it is up already, or the editor or a move has the remote (OK held does
     * nothing more then).
     */
    boolean okLong() {
        // The picture's state as it is now (a panel just gone, say), so the layer is there to draw the menu.
        coverChanged();
        if (covered || menu.open || editor.open || moving != 0) return false;
        resetEvery = false;
        menu.show();
        return true;
    }

    /** Pure: the menu's line under its title, for a game (null: none found) and whether it is being looked for / read. */
    static String menuLine(Game g, boolean finding, boolean read) {
        if (g == null) return finding ? "Reading the match" : "No match on this channel now";
        if (!read) return "Reading the scores";
        return full(g.homeName, g.home) + " v " + full(g.awayName, g.away);
    }

    boolean ok() {
        // Covered (a panel over the picture): the remote is the live screen's.
        if (covered) return false;
        if (moving != 0) {
            endMove(true);
            return true;
        }
        if (editor.open) {
            editor.ok();
            return true;
        }
        if (menu.open) {
            menu.ok();
            return true;
        }
        if (card.asking) {
            lineUp();
            return true;
        }
        return false;
    }

    boolean key(int code) {
        if (covered) return false;
        if (moving != 0) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_UP: moveBy(0, -1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: moveBy(0, 1); return true;
                default: return false;
            }
        }
        if (editor.open) return editor.key(code);
        return menu.open && menu.key(code);
    }

    boolean back() {
        if (covered) return false;
        if (moving != 0) {
            endMove(true);
            return true;
        }
        if (editor.open) {
            editor.back();
            return true;
        }
        if (menu.open) {
            menu.close();
            return true;
        }
        if (card.getVisibility() == VISIBLE) {
            card.away();
            return true;
        }
        // The line-ups fade away; a moment's pop-up (either style: the strap too) is short and Back is the live screen's.
        if (popup != null && !popup.isOver() && !Moments.boardMoment(popupKind) && !"strap".equals(popupKind)) {
            popup.back();
            return true;
        }
        if (drop.showingLineups()) {
            hideDrop();
            return true;
        }
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    // ---- the cover over the channel's logo ---------------------------------------------------------------------------------

    /** The cover's logo is read at most this many pixels across (it is never drawn much larger on a 1080p picture). */
    static final int LOGO_MAX = 320;

    /** Pure: a size rounded up to a step of 16 pixels (at least 16). */
    static int step16(float px) {
        return Math.max(16, (int) Math.ceil(px / 16f) * 16);
    }

    /**
     * The cover's drawing: a plate in the board's own look, the channel's own mark on it (else its name), LIVE under it
     * while the match is, a white ring while it is being moved. The cover over the picture draws it, and the menu's picture
     * of the cover the same, smaller.
     */
    static final class CoverArt {
        private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG), pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG), tag = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        CoverArt(Context c) {
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xFFFFFFFF);
            name.setTypeface(BoardPrefs.outfit(c, 700));
            tag.setTypeface(BoardPrefs.outfit(c, 800));
            pill.setColor(0xFFE3061B);
        }

        /** The cover w x hh at (0, 0) in look, for channel c (null: the plate alone), e its edge; again when its logo comes. */
        void draw(Canvas cv, float w, float hh, BoardBug.StripLook look, Channel c, float e, boolean live, boolean ringed, Runnable again) {
            if (w <= 0 || hh <= 0) return;
            ground.setColor(0xFF000000 | look.ground);
            float round = Math.min(hh, w) * Math.max(0.12f, look.round);
            r.set(0, 0, w, hh);
            cv.drawRoundRect(r, round, round, ground);
            float tagH = live ? hh * 0.26f : 0f;
            String url = c == null ? null : LogoPack.url(c);
            int bw = Math.max(1, Math.round(w - 4 * e - w * 0.16f)), bh = Math.max(1, Math.round(hh - 4 * e - tagH - hh * 0.12f));
            // Read at most LOGO_MAX across and in steps of 16 (a cover made bigger a press at a time reads its logo once, not
            // at every size), then drawn to the size it is.
            float k = Math.min(1f, (float) LOGO_MAX / Math.max(bw, bh));
            Bitmap b = Pics.get(url, step16(bw * k), step16(bh * k), Pics.FIT, again);
            float midY = (hh - tagH) / 2f;
            if (b != null) {
                float s = Math.min((float) bw / b.getWidth(), (float) bh / b.getHeight());
                float dw = b.getWidth() * s, dh = b.getHeight() * s;
                r.set((w - dw) / 2f, midY - dh / 2f, (w + dw) / 2f, midY + dh / 2f);
                cv.drawBitmap(b, null, r, bmp);
            } else if (c != null) {
                name.setColor(look.ink);
                name.setTextSize(Math.min(hh * 0.3f, w * 0.16f));
                String n = TextFit.fit(ChannelEdits.name(c), name, w - 4 * e);
                cv.drawText(n, (w - name.measureText(n)) / 2f, midY + name.getTextSize() * 0.35f, name);
            }
            if (live) {
                tag.setTextSize(tagH * 0.62f);
                tag.setColor(0xFFFFFFFF);
                String t = "LIVE";
                float tw = tag.measureText(t) + tagH * 0.8f;
                r.set((w - tw) / 2f, hh - tagH - 2 * e, (w + tw) / 2f, hh - 2 * e);
                cv.drawRoundRect(r, tagH * 0.2f, tagH * 0.2f, pill);
                cv.drawText(t, (w - tag.measureText(t)) / 2f, r.bottom - tagH * 0.28f, tag);
            }
            if (ringed) {
                ring.setStrokeWidth(Math.max(2f, e));
                r.set(e / 2f, e / 2f, w - e / 2f, hh - e / 2f);
                cv.drawRoundRect(r, round, round, ring);
            }
        }
    }

    /** The cover over where the channel's logo is (CoverArt), in the board's look now. */
    private final class LogoCover extends View {
        boolean live, ringed;
        private final CoverArt art;
        private final Runnable again = this::invalidate;

        LogoCover(Context c) {
            super(c);
            art = new CoverArt(c);
        }

        @Override protected void onDraw(Canvas cv) {
            art.draw(cv, getWidth(), getHeight(), bug.stripLook(), ch, edge, live, ringed, again);
        }
    }

    /** Words cut to a width with an ellipsis. */
    private static final class TextFit {
        static String fit(String s, TextPaint p, float w) {
            if (s == null) return "";
            if (p.measureText(s) <= w) return s;
            String t = s;
            while (t.length() > 1 && p.measureText(t + "…") > w) t = t.substring(0, t.length() - 1);
            return t + "…";
        }
    }

    // ---- the card: lining the board up with the channel, and short notes -----------------------------------------------

    /**
     * Top right, as the launcher's InStepCard: at a kick-off by the scores, "Set the scoreboard's clock with your
     * channel's · Press OK as the game kicks off on your screen"; OK sets it and says so for a few seconds; Back, or four
     * minutes with no OK, takes it away with nothing changed. Also a short note (what OK did, how to move the board).
     */
    private final class Card extends View {
        boolean asking;
        private String title = "", line = "";
        private final Paint ground = Dark.fill(0xF20B1033), dot = Dark.fill(0xFF3DDC84);
        private final TextPaint titleP = Dark.text(30, 700, 0xFFFFFFFF), lineP = Dark.text(23, 500, 0xD0FFFFFF);
        private final RectF r = new RectF();
        private final Runnable gone = this::away;

        Card(Context c) {
            super(c);
            setVisibility(GONE);
        }

        void ask(boolean second) {
            asking = true;
            Game g = game;
            title = "Set the scoreboard's clock with your channel's";
            line = "Press OK as the " + (second ? "second half starts" : "game kicks off") + " on your screen"
                    + (g == null ? "" : " · " + full(g.homeName, g.home) + " v " + full(g.awayName, g.away));
            setVisibility(VISIBLE);
            invalidate();
            h.removeCallbacks(gone);
            h.postDelayed(gone, CARD_WAIT_MS);
        }

        void note(String t, String l) {
            asking = false;
            title = t;
            line = l;
            setVisibility(VISIBLE);
            invalidate();
            h.removeCallbacks(gone);
            if (moving == 0) h.postDelayed(gone, CARD_DONE_MS);
        }

        void away() {
            asking = false;
            h.removeCallbacks(gone);
            setVisibility(GONE);
        }

        @Override protected void onDraw(Canvas c) {
            float w = Math.max(titleP.measureText(title), lineP.measureText(line)) + Ui.f(110);
            w = Math.min(w, Ui.f(1100));
            float right = getWidth() - Ui.f(60), top = Ui.f(60);
            r.set(right - w, top, right, top + Ui.f(118));
            c.drawRoundRect(r, Ui.f(22), Ui.f(22), ground);
            dot.setColor(asking ? 0xFF3DDC84 : Dark.ACCENT);
            c.drawCircle(r.left + Ui.f(40), r.top + Ui.f(44), Ui.f(10), dot);
            float tx = r.left + Ui.f(70), room = w - Ui.f(100);
            c.drawText(TextFit.fit(title, titleP, room), tx, r.top + Ui.f(54), titleP);
            c.drawText(TextFit.fit(line, lineP, room), tx, r.top + Ui.f(92), lineP);
        }
    }

    // ---- the scoreboard and logo menu (BoardMenu): what its lines say and do -------------------------------------------------

    /** The menu's line under its title as it was last worked out (worked out again as the match is found or read). */
    private String menuNote = "";

    /** The line under the menu's title now: the match, or that there is none yet (what is set is kept for the next one). */
    private String noteNow() {
        String line = menuLine(game, finding, !readings.isEmpty());
        return game == null ? line + "  ·  what you set here is kept for the next match" : line;
    }

    /** The board's looks in the menu's order, the one step away from the look chosen (wrapping round). */
    private BoardBug.Style lookAt(int step) {
        BoardBug.Style[] all = BoardPrefs.looks();
        BoardBug.Style now = BoardPrefs.style(getContext());
        int i = 0;
        for (int k = 0; k < all.length; k++) if (all[k] == now) i = k;
        return all[Math.floorMod(i + step, all.length)];
    }

    /** The menu's My scoreboards: the look as chosen (none) or one of the saved boards, step on from the one in use. */
    private void stepMine(int step) {
        Context c = getContext();
        java.util.List<com.gathin.broadcast.MyBoards.Saved> all = MyBoardStore.all(c);
        if (all.isEmpty()) return;
        String id = BoardPrefs.customId(c);
        int i = -1;
        for (int k = 0; k < all.size(); k++) if (all.get(k).id.equals(id)) i = k;
        int n = Math.floorMod(i + 1 + step, all.size() + 1) - 1;
        BoardPrefs.setCustom(c, n < 0 ? null : all.get(n).id);
        restyle(MyBoardStore.inUse(c));
    }

    /**
     * The menu's Scoreboard switch, the launcher's boardShown: off, the board, what slides out beside it and its pop-ups
     * (the strap, a moment's, the line-ups, one held back) away at once and until it is on again, the kick-off's card too;
     * the cover stays as its own switch says and the game is followed underneath. On, the board comes back as it does.
     */
    void boardShown(boolean on) {
        BoardPrefs.setOn(getContext(), on);
        if (!on) {
            held = null;
            drops.clear();
            queuedAt.clear();
            h.removeCallbacks(nextDropRun);
            h.removeCallbacks(hideDropRun);
            h.removeCallbacks(strapWatch);
            dropping = false;
            restartUntil = 0;
            varOut.off();
            drop.gone();
            if (popup != null) {
                BroadcastView v = popup;
                popup = null;
                popupKind = "";
                v.stop();
                removeView(v);
            }
            if (card.asking) card.away();
            boardAway();
        }
        if (started) update();
    }

    private final BoardMenu.Host menuHost = new BoardMenu.Host() {
        @Override public BoardMenu.MenuState state() {
            Context c = getContext();
            BoardMenu.MenuState m = new BoardMenu.MenuState();
            m.boardShown = BoardPrefs.on(c);
            m.logoOn = BoardPrefs.logoOn(c);
            BoardBug.Style look = BoardPrefs.style(c);
            m.look = look.label;
            String id = BoardPrefs.customId(c);
            com.gathin.broadcast.MyBoards.Saved mine = id == null ? null : MyBoardStore.get(c, id);
            m.mine = mine == null ? "" : mine.name;
            m.lookNow = shownCustom != null ? (mine != null ? mine.name : shownCustom.name) : look.label;
            m.lookBefore = lookAt(-1).label;
            m.lookAfter = lookAt(1).label;
            BoardPrefs.Place p = place(), std = BoardPrefs.Place.standard();
            m.boardSize = Math.round(p.bh / std.bh * 100);
            m.logoSize = Math.round(p.lw / std.lw * 100);
            m.lineups = BoardPrefs.lineupsOn(c);
            int d = InStep.delay(c, chKey);
            m.timing = d <= 0 ? "Live" : d + " s behind";
            m.usual = BoardMenu.usualWords(InStep.usual(c));
            m.ask = InStep.asking(c);
            m.alerts = BoardPrefs.dropsOn(c);
            m.var = BoardPrefs.varExplainedOn(c);
            m.popups = BoardPrefs.popupWords(BoardPrefs.popupStyle(c));
            m.sound = Sfx.goalName(BoardPrefs.goalSound(c));
            m.goalLook = com.gathin.broadcast.GoalLooks.name(BoardPrefs.goalLook(c));
            m.swish = BoardPrefs.swishOn(c);
            m.every = resetEvery;
            m.live = game != null && logo.live;
            m.channel = ch == null ? "" : ChannelEdits.name(ch);
            menuNote = noteNow();
            m.note = menuNote;
            return m;
        }

        @Override public void step(int line, int step) {
            Context c = getContext();
            switch (line) {
                case BoardMenu.M_BOARD: boardShown(!BoardPrefs.on(c)); return;
                case BoardMenu.M_LOGO: BoardPrefs.setLogoOn(c, !BoardPrefs.logoOn(c)); break;
                case BoardMenu.M_LOOK:
                    BoardPrefs.setStyle(c, lookAt(step));
                    restyle(null);
                    break;
                case BoardMenu.M_MOVE: {
                    float aspect = boardRect.height() > 0 ? boardRect.width() / (float) boardRect.height() * getHeight() / (float) Math.max(1, getWidth()) : 3f;
                    place().sizeBoard(step > 0 ? SIZE_STEP : 1f / SIZE_STEP, aspect);
                    BoardPrefs.setPlace(c, chKey, place());
                    break;
                }
                case BoardMenu.M_MOVE_LOGO:
                    place().sizeLogo(step > 0 ? SIZE_STEP : 1f / SIZE_STEP);
                    BoardPrefs.setPlace(c, chKey, place());
                    break;
                case BoardMenu.M_LINEUPS: BoardPrefs.setLineupsOn(c, !BoardPrefs.lineupsOn(c)); break;
                case BoardMenu.M_MINE: stepMine(step); break;
                case BoardMenu.M_TIMING: timing(step); break;
                case BoardMenu.M_USUAL: InStep.setUsual(c, BoardMenu.nextUsual(InStep.usual(c), step)); break;
                case BoardMenu.M_ASK: InStep.setAsking(c, !InStep.asking(c)); break;
                case BoardMenu.M_ALERTS: BoardPrefs.setDropsOn(c, !BoardPrefs.dropsOn(c)); break;
                case BoardMenu.M_VAR: BoardPrefs.setVarExplainedOn(c, !BoardPrefs.varExplainedOn(c)); break;
                case BoardMenu.M_POPUPS: BoardPrefs.setPopupStyle(c, BoardPrefs.nextPopup(BoardPrefs.popupStyle(c), step)); break;
                case BoardMenu.M_GOAL:
                    // the goal's animation on the board (GoalLooks; no 3D shirt here), for the goals to come
                    BoardPrefs.setGoalLook(c, com.gathin.broadcast.GoalLooks.next(BoardPrefs.goalLook(c), step, false));
                    bug.goalLook(BoardPrefs.goalLook(c));
                    break;
                case BoardMenu.M_SOUND: {
                    String now = BoardPrefs.goalSound(c);
                    int i = 0;
                    for (int k = 0; k < Sfx.GOALS.length; k++) if (Sfx.GOALS[k].equals(now)) i = k;
                    String next = Sfx.GOALS[Math.floorMod(i + step, Sfx.GOALS.length)];
                    BoardPrefs.setGoalSound(c, next);
                    Sfx.goal(c, next);
                    break;
                }
                case BoardMenu.M_SWISH: {
                    boolean on = !BoardPrefs.swishOn(c);
                    BoardPrefs.setSwishOn(c, on);
                    BoardPrefs.setScoreSwishOn(c, on);
                    break;
                }
                case BoardMenu.M_RESET: resetEvery = !resetEvery; return;
                default:
                    // New scoreboard and It's just happened: OK only.
                    return;
            }
            if (started) update();
            else layoutAll();
        }

        @Override public boolean ok(int line) {
            Context c = getContext();
            // What needs the match itself, with none on this channel now.
            if (game == null && (line == BoardMenu.M_NOW || line == BoardMenu.M_MOVE || line == BoardMenu.M_MOVE_LOGO
                    || line == BoardMenu.M_LINEUPS)) {
                menu.close();
                card.note("No match on this channel now", "The match on this channel, when there is one");
                return true;
            }
            switch (line) {
                case BoardMenu.M_MOVE: menu.close(); startMove(1); return true;
                case BoardMenu.M_MOVE_LOGO: menu.close(); startMove(2); return true;
                case BoardMenu.M_LINEUPS: menu.close(); lineups(true); return true;
                case BoardMenu.M_MINE:
                    // The board in use (or the look's own colours) in the editor, the board over the match changing with it.
                    menu.close();
                    editor.show(BoardPrefs.customId(c), false);
                    return true;
                case BoardMenu.M_NEW:
                    menu.close();
                    editor.show(null, true);
                    return true;
                case BoardMenu.M_NOW: {
                    int d = InStep.justHappened(c, chKey, changeSeen);
                    steady.reset();
                    menu.close();
                    card.note(d >= 0 ? "Lined up" : "Nothing to line up with",
                            d >= 0 ? InStep.words(d) + " · the scoreboard and alerts wait for your channel" : "Press it as a goal, a card or the kick-off shows");
                    return true;
                }
                case BoardMenu.M_TIMING:
                    return true;
                case BoardMenu.M_RESET:
                    if (resetEvery) BoardPrefs.resetAllPlaces(c);
                    else BoardPrefs.resetPlace(c, chKey);
                    place = null;
                    if (started) update();
                    else layoutAll();
                    card.note("Back in the standard places", "The scoreboard and the logo's cover, " + (resetEvery ? "on every channel" : "on this channel"));
                    return true;
                default:
                    return false;
            }
        }

        @Override public void cover(Canvas cv, float w, float hh, int look) {
            BoardBug.StripLook strip;
            if (look == 0) {
                strip = bug.stripLook();
            } else {
                // The board in the look either side of the one chosen, for its colours (never drawn itself).
                if (looks == null) looks = new BoardBug(getContext());
                looks.custom(null);
                looks.style(lookAt(look));
                Game g = game;
                looks.competition(g == null ? null : g.slug, g == null ? null : boardComp(g));
                strip = looks.stripLook();
            }
            menuArt.draw(cv, w, hh, strip, ch, Math.max(2f, Ui.f(2)), game != null && logo.live, false, menu::invalidate);
        }

        @Override public float coverAspect() {
            BoardPrefs.Place p = place();
            int sw = getWidth() > 0 ? getWidth() : 1920, sh = getHeight() > 0 ? getHeight() : 1080;
            return p.lw * sw / Math.max(1f, p.lh * sh);
        }
    };

    /** Timing: the board (its clock, its score and its alerts) a step later (1) or sooner (-1), as the launcher's. */
    private void timing(int step) {
        Context c = getContext();
        InStep.nudge(c, chKey, step * InStep.STEP_S);
        steady.reset();
        if (time.anchor != null && game != null) {
            long[] anchor = BoardClock.moved(time.anchor, step * InStep.STEP_S * 1000L);
            time.anchor = anchor;
            BoardPrefs.setAnchor(c, BoardClock.anchorWords(game.id, anchor[0], anchor[1], (int) anchor[2]));
        }
    }

    // ---- previews ------------------------------------------------------------------------------------------------------------

    /**
     * Tests and previews: game g taken as the one found for the channel playing now, with the readings so far (as the
     * scores' poll had them), and the board shown as the screen has it; not looked for or read again for a while.
     */
    void found(Game g, List<BoardClock.Reading> rs) {
        Channel c = tv.live.channel();
        long now = System.currentTimeMillis();
        chKey = c == null ? "" : c.key;
        ch = c;
        place = null;
        take(g, now);
        for (BoardClock.Reading r : rs) BoardClock.add(readings, r);
        nextFind = now + REFIND_MS;
        nextPoll = now + POLL_IDLE_MS;
        nextSummary = now + SUMMARY_MS;
        if (started) update();
    }

    /** Whether the cover over the channel's logo is on the screen now. */
    boolean coverUp() {
        return logo.getVisibility() == VISIBLE;
    }

    /** Previews: game g with these readings, as the screen shows it at now (delayS behind), what's out beside it, the menu. */
    void preview(Game g, List<BoardClock.Reading> rs, long now, int delayS, BoardDrop.Drop d, boolean withMenu, boolean withCard) {
        chKey = "preview";
        InStep.setDelay(getContext(), chKey, delayS);
        take(g, now);
        for (BoardClock.Reading r : rs) BoardClock.add(readings, r);
        announced[1] = 4;
        long screenNow = now - delayS * 1000L;
        BoardClock.Reading r = BoardClock.atScreen(readings, screenNow);
        BoardBug.Look l = r == null || r.state == Game.PRE ? preLook(g) : look(g, r);
        if (l.live && r != null) {
            double secs = BoardClock.clockSecs(r, screenNow);
            if (secs >= 0) l.clock = BoardClock.clockWords(secs);
            l.added = com.gathin.broadcast.AddedTime.boardAdded(BoardClock.addedWords(l.clock, r.detail, announced), false);
            l.stoppage = BoardClock.inAddedTime(l.clock, r.detail);
        }
        bug.set(l);
        layoutAll();
        bug.setVisibility(VISIBLE);
        bug.shownAt(1f);
        up = true;
        logo.setVisibility(BoardPrefs.logoOn(getContext()) ? VISIBLE : INVISIBLE);
        logo.live = l.live;
        layoutAll();
        if (d != null) {
            LayoutParams b = (LayoutParams) bug.getLayoutParams();
            drop.place(bug.stripLook(), boardH * TEXT, tuck, sideLeft, 0);
            beside(drop.widthFor(d, b.height, shortRoom(b.height, sideMax)));
            drop.showNow(d, 0, 0f, 1f);
        }
        if (withMenu) {
            resetEvery = false;
            menu.show();
        }
        if (withCard) card.ask(false);
    }
}
