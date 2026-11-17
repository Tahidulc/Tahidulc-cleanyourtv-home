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
import com.gathin.homefieldtv.sports.Espn;
import com.gathin.homefieldtv.sports.FotMob;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;

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
 * minutes added beside it (BoardClock). At each kick-off a card asks for OK as it happens on the screen: from then the
 * clock is the channel's own and the wait is set. It comes and goes as a broadcaster's does (BoardBug, the launcher's
 * board copied whole, in the Scoreboard Lab's looks or By competition): up twenty minutes before kick-off, resting at
 * half time once its moment has passed, gone a minute after full time; a goal sweeps across it with the family's sounds.
 *
 * <p>Out of it, in its look (BoardDrop): the cards, the substitutions, VAR explained, the minutes added, half and full
 * time, each once the picture reaches it; the kick-off pop-up, the pop-up after a goal and at the second half, and the
 * classic line-ups seven minutes before kick-off (the shared broadcast pages, BroadcastView), the board away while a
 * moment's pop-up is on. Over the channel's own logo, a cover (top right, where most broadcasters put theirs).
 *
 * <p>Where they sit: the standard places (the board top left over the broadcaster's own, the cover top right) until moved
 * for a channel; OK held over the match opens the menu (on or off, the look, timing, move and size, the line-ups, the
 * sounds). Kind to the screen: once a minute both step a pixel or two round a circle, and after five minutes without a
 * goal they dim.
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
    /** Alert chips' colours, as the launcher's kindColour. */
    static final int GOAL = 0xFF3DDC84, RED = 0xFFFF5A5F, SUB = 0xFF5CC8FF, ADDED = 0xFFE3061B;

    private final TvActivity tv;
    private final Handler h = new Handler(Looper.getMainLooper());
    final BoardBug bug;
    final BoardDrop drop;
    private final LogoCover logo;
    private final Card card;
    private final Menu menu;
    private BroadcastView popup;
    private String popupKind = "";
    private boolean started, popupMaking, lineupsMaking;

    // ---- the channel and its game ----------------------------------------------------------------------------------------

    private Channel ch;
    private String chKey = "";
    private Game game;
    private final ArrayDeque<BoardClock.Reading> readings = new ArrayDeque<>();
    private final BoardClock.Steady steady = new BoardClock.Steady();
    private final int[] announced = new int[4];
    /** The clock set at a kick-off on the screen (OK at the card): {base seconds, when, half}; null for none. */
    private long[] anchor;
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
    private final Set<String> shown = new HashSet<>(), eventKeys = new HashSet<>(), varKeys = new HashSet<>();
    /** Alerts waiting for the picture to reach them: when the scores saw each. */
    private final List<Object[]> pending = new ArrayList<>();
    private final ArrayDeque<BoardDrop.Drop> drops = new ArrayDeque<>();
    private boolean dropping, addedOutFor1, addedOutFor2;
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
        menu = new Menu(tv);
        addView(menu, new LayoutParams(-1, -1));
        ColorMatrix m = new ColorMatrix();
        m.setScale(BoardClock.DIM, BoardClock.DIM, BoardClock.DIM, 1f);
        dimPaint.setColorFilter(new ColorMatrixColorFilter(m));
        bug.style(BoardPrefs.style(tv));
    }

    // ---- on and off ----------------------------------------------------------------------------------------------------

    /** The live screen is up: the board looks after the channel playing. */
    void start() {
        if (started) return;
        started = true;
        tv.live.addListener(this);
        bug.style(BoardPrefs.style(getContext()));
        Sfx.warm(getContext(), Sfx.SWISH, Sfx.SCORE, "goal-" + BoardPrefs.goalSound(getContext()));
        h.post(tick);
    }

    /** The live screen gone: everything away at once. */
    void stop() {
        started = false;
        tv.live.removeListener(this);
        h.removeCallbacksAndMessages(null);
        if (popup != null) popup.stop();
        menu.close();
        card.away();
        endMove(false);
        hideAll();
    }

    @Override public void liveChanged() {
        if (started) update();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!started) return;
            update();
            h.postDelayed(this, 1000L);
        }
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
        show(now, playing);
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
            } catch (RuntimeException ignored) {
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
        anchor = BoardClock.anchorOf(BoardPrefs.anchor(getContext()), g.id);
        lastChange = now;
        bug.game(g.id);
        bug.competition(g.slug, g.leagueName);
        bug.names(full(g.homeFull, g.homeName), full(g.awayFull, g.awayName));
        bug.crests(g.homeLogo != null || g.awayLogo != null);
        loadImages();
        nextPoll = 0;
        nextSummary = 0;
    }

    /** The game let go (another channel, or its day done): what was kept of it too. */
    private void letGo() {
        game = null;
        readings.clear();
        steady.reset();
        Arrays.fill(announced, 0);
        anchor = null;
        shownScore = null;
        phaseWas = null;
        htSince = ftSince = -1;
        changeSeen = kickSeen = 0;
        firstSummary = true;
        eventKeys.clear();
        varKeys.clear();
        pending.clear();
        drops.clear();
        espnHome = new ArrayList<>();
        espnAway = new ArrayList<>();
        restartUntil = 0;
        addedOutFor1 = addedOutFor2 = false;
        lineupsTried = 0;
        h.removeCallbacks(nextDropRun);
        h.removeCallbacks(hideDropRun);
        dropping = false;
        drop.gone();
    }

    private static String full(String a, String b) {
        return a != null && !a.isEmpty() ? a : b == null ? "" : b;
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
            } catch (RuntimeException ignored) {
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
            else if (last.halftime && r.state == Game.LIVE && !r.halftime) kickoffSeen(seen, true);
            if (last.home != r.home || last.away != r.away || last.state != r.state || last.halftime != r.halftime) changeSeen = seen;
        }
        BoardClock.add(readings, r);
        SportsData.note(g, seen);
        game = g;
    }

    private void summary() {
        summarising = true;
        Game g0 = game;
        Exec.io(() -> {
            Espn.Summary s = null;
            try {
                s = Espn.summary(g0.slug, g0.id, 30_000L);
            } catch (RuntimeException ignored) {
                // read again next time
            }
            Espn.Summary got = s;
            long seen = System.currentTimeMillis();
            Exec.main(() -> {
                summarising = false;
                nextSummary = System.currentTimeMillis() + (g0.state == Game.LIVE ? SUMMARY_MS : 120_000L);
                if (got == null || game == null || !game.id.equals(g0.id)) return;
                summarised(got, seen);
            });
        });
    }

    /** The game's summary read at seen: the minutes added, the elevens, and what happened, each waiting for the picture. */
    void summarised(Espn.Summary s, long seen) {
        for (int i = 0; i < 4; i++) if (s.added[i] > 0) announced[i] = s.added[i];
        if (!s.homeLineup.isEmpty()) espnHome = new ArrayList<>(s.homeLineup);
        if (!s.awayLineup.isEmpty()) espnAway = new ArrayList<>(s.awayLineup);
        homeShape = s.homeFormation == null ? "" : s.homeFormation;
        awayShape = s.awayFormation == null ? "" : s.awayFormation;
        boolean first = firstSummary;
        firstSummary = false;
        List<String[]> subs = new ArrayList<>();
        for (String[] e : s.events) {
            if (e == null || e.length < 6 || !eventKeys.add(e[4])) continue;
            // What happened before the board came on is not said again.
            if (first) continue;
            changeSeen = seen;
            String what = e[1] == null ? "" : e[1].toLowerCase(Locale.ROOT);
            if (what.contains("substitution")) subs.add(new String[]{"sub", e[0], e[3], e[2], "", e[5], ""});
            else if (what.contains("red card")) pend(seen, cardDrop("RED CARD", RED, e));
            else if (what.contains("yellow card")) pend(seen, cardDrop("YELLOW CARD", BoardPrefs.YELLOW, e));
        }
        for (List<String[]> grp : SubsPage.group(subs)) pend(seen, subsDrop(grp));
        if (BoardPrefs.varExplainedOn(getContext())) {
            for (VarExplained.Seen v : VarExplained.scan(s.varLines)) {
                if (v == null || !varKeys.add(v.key) || first) continue;
                BoardDrop.Drop d = varDrop(v);
                if (d != null) pend(seen, d);
            }
        }
    }

    private void pend(long seen, BoardDrop.Drop d) {
        if (d != null) pending.add(new Object[]{seen, d});
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

    /** The minutes added (an ADDED TIME alert, its +N counting up), in added time's red. */
    BoardDrop.Drop addedDrop(int n) {
        if (n <= 0) return null;
        BoardDrop.Drop d = own("ADDED TIME", ADDED);
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
        if (g == null || !playing || !BoardPrefs.on(c) && moving == 0) {
            if (moving == 0) {
                boardUp(false);
                logoUp(false);
            }
            return;
        }
        long screenNow = now - waitMs();
        BoardClock.Reading r = BoardClock.atScreen(readings, screenNow);
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
        if (l.live && r != null) {
            // The clock: counted from the kick-off on the screen (OK at the card), the channel's own; else the scores',
            // counting on through the minutes added too, never stopping or stepping back by a little.
            double secs = BoardClock.anchoredSecs(anchor, now, r.detail);
            if (secs < 0) secs = BoardClock.clockSecs(r, screenNow);
            if (secs >= 0) l.clock = BoardClock.clockWords(steady.shown(g.id, r.detail, secs, now));
            l.added = BoardClock.addedWords(l.clock, r.detail, announced);
            l.stoppage = BoardClock.inAddedTime(l.clock, r.detail);
            if (!l.added.isEmpty()) addedOut(BoardClock.periodOf(l.clock, r.detail), l.added);
        }
        moments(r, screenNow, now, l.score);
        release(screenNow);
        if (r != null && r.state != Game.PRE) {
            if (shownScore != null && !shownScore.equals(l.score)) {
                int side = BoardClock.goalSide(shownScore, l.score);
                if (side > 0) goalNow(side, l.score, r, now);
            }
            shownScore = l.score;
        }
        bug.style(BoardPrefs.style(c));
        bug.set(l);
        boolean popupOn = popup != null && !popup.isOver();
        boolean moment = popupOn && Moments.boardMoment(popupKind), lineupsOn = popupOn && !moment;
        boolean awayForPopup = Moments.boardAwayForPopup(moment, restartWaiting(), !up);
        boolean want = moving == 1 || (phase == BoardClock.PRE || phase == BoardClock.UP) && !awayForPopup && !lineupsOn;
        if (awayForPopup) swishBack = true;
        layoutAll();
        boardUp(want);
        logoUp(moving == 2 || phase != BoardClock.AWAY && BoardPrefs.logoOn(c));
        logo.live = l.live;
        logo.invalidate();
        guard(now);
    }

    /** The minutes added out beside the board once a half, as soon as they are known (their tab beside it after). */
    private void addedOut(int period, String added) {
        if (period != 1 && period != 2) return;
        if (period == 1 ? addedOutFor1 : addedOutFor2) return;
        if (period == 1) addedOutFor1 = true;
        else addedOutFor2 = true;
        try {
            drop(addedDrop(Integer.parseInt(added.replace("+", "").trim())));
        } catch (NumberFormatException ignored) {
            // no minutes in it
        }
    }

    /** The moments as the picture reaches them: the kick-off, half time, the second half, full time; the line-ups. */
    private void moments(BoardClock.Reading r, long screenNow, long now, String score) {
        Game g = game;
        if (g == null) return;
        Context c = getContext();
        String was = phaseWas;
        String ph = r == null ? (g.state == Game.PRE ? "pre" : "")
                : Moments.phase(false, r.state, r.halftime, was, BoardClock.periodOfDetail(r.detail));
        if (!ph.isEmpty()) phaseWas = ph;
        String kind = was == null || ph.isEmpty() ? null : Moments.moment(was, ph);
        if (kind != null) {
            lastChange = now;
            int[] sc = "kickoff".equals(kind) || r == null ? null : new int[]{r.home, r.away};
            if (BoardPrefs.popupOn(c)) popup(kind, sc, null, null);
            else drop(momentDrop("kickoff".equals(kind) ? "KICK-OFF" : "SECOND HALF", sc == null ? null : score));
        }
        if (was != null && !was.equals(ph)) {
            if ("ht".equals(ph)) drop(momentDrop("HALF TIME", score));
            else if ("ft".equals(ph)) drop(momentDrop("FULL TIME", score));
        }
        if ("pre".equals(ph) && g.kickoff > 0 && BoardPrefs.lineupsOn(c) && Moments.lineupsDue(g.kickoff - screenNow)) lineups(false);
    }

    /** Alerts the picture has reached now go out beside the board. */
    private void release(long screenNow) {
        for (Iterator<Object[]> it = pending.iterator(); it.hasNext(); ) {
            Object[] p = it.next();
            if ((Long) p[0] > screenNow) continue;
            it.remove();
            drop((BoardDrop.Drop) p[1]);
        }
    }

    /** A goal on the screen: its moment on the board, its sound, and what it brings (the pop-up after it, or its scorer). */
    private void goalNow(int side, String score, BoardClock.Reading r, long now) {
        Context c = getContext();
        lastChange = now;
        if (up) {
            bug.goal(side);
            Sfx.goal(c, BoardPrefs.goalSound(c));
        }
        String[] goal = nthGoal(game == null ? null : game.goals, side, score);
        String scorer = Moments.scorer(goal), minute = goal == null || goal.length < 3 ? "" : BroadcastData.minute(goal[2]);
        if (BoardPrefs.popupOn(c) && game != null) {
            restartLater(side, new int[]{r.home, r.away}, scorer, minute);
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

    /** The pop-up after a goal, once play starts again (Moments: forty seconds after the goal reached the picture). */
    private void restartLater(int side, int[] score, String scorer, String minute) {
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
                if (go > 0) popup("restart", score, scorer, minute);
                if (go < 0 || popup == null && !popupMaking) drop(goalDrop(side, scorer, minute));
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

    /** A moment's pop-up ("kickoff", "restart", "second"), once each. */
    private void popup(String kind, int[] score, String scorer, String minute) {
        Game g = game;
        if (g == null) return;
        String key = g.id + "|" + kind + (score == null ? "" : "|" + score[0] + "-" + score[1]);
        if (shown.contains(key) || popupShowing()) return;
        shown.add(key);
        popupMaking = true;
        BroadcastData.Match m = match(g);
        android.content.res.AssetManager assets = getContext().getAssets();
        Exec.io(() -> {
            JSONObject cfg = null;
            try {
                cfg = BroadcastData.withMark(assets, BroadcastData.kickoff(kind, m, score, scorer, minute));
            } catch (Exception ignored) {
                // none, then
            }
            JSONObject got = cfg;
            Exec.main(() -> {
                popupMaking = false;
                if (got == null || !started || popup != null) return;
                showPopup(BroadcastView.page(getContext(), "kickoff", got, this::popupGone), kind);
            });
        });
    }

    /** The classic line-ups (FotMob's elevens), once, from seven minutes before kick-off on the picture; or now, asked. */
    void lineups(boolean asked) {
        Game g = game;
        if (g == null) return;
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
            } catch (Exception ignored) {
                // asked again in a minute
            }
            JSONObject got = cfg;
            Exec.main(() -> {
                lineupsMaking = false;
                if (!started || game == null || !game.id.equals(g.id)) return;
                if (got == null) {
                    // FotMob has none yet: ESPN's elevens beside the board, once (or now, asked).
                    BoardDrop.Drop d = lineupsDrop();
                    if (d != null && (asked || !shown.contains(key))) {
                        shown.add(key);
                        drop(d);
                    } else if (asked) {
                        card.note("No line-ups yet", "They come about an hour before kick-off");
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
    }

    private void popupGone(BroadcastView v) {
        if (v != popup) return;
        popup = null;
        popupKind = "";
        removeView(v);
    }

    // ---- what slides out beside it ---------------------------------------------------------------------------------------

    void drop(BoardDrop.Drop d) {
        if (d == null) return;
        if (!d.lineups && !BoardPrefs.dropsOn(getContext())) return;
        // A goal is said once.
        if (d.key != null && !shown.add("drop|" + d.key)) return;
        if ("VAR".equals(d.chip)) {
            drops.addFirst(d);
            while (drops.size() > 4) drops.removeLast();
        } else {
            drops.addLast(d);
            while (drops.size() > 4) drops.removeFirst();
        }
        if (!dropping) nextDrop();
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
        h.removeCallbacks(hideDropRun);
        h.postDelayed(hideDropRun, d.varCheck ? VarExplained.CHECK_MAX_MS
                : "VAR".equals(d.chip) ? Math.max(VarExplained.WHY_MS, drop.showFor(DROP_MS)) : drop.showFor(DROP_MS));
    }

    private void hideDrop() {
        h.removeCallbacks(hideDropRun);
        // A check whose decision is waiting: the decision turns it over in place.
        BoardDrop.Drop next = drops.peekFirst(), out = drop.showing();
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
                if (fresh) bug.shownAt(0f);
                bug.open();
                // Back after a moment's pop-up: with the swish.
                if (fresh && swishBack && BoardPrefs.swishOn(getContext())) Sfx.play(getContext(), Sfx.SWISH, -0.5f, 0.7f);
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
        boolean dim = moving == 0 && !menu.open && BoardClock.dimmed(now - lastChange);
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
        int bh = Math.max(8, Math.round(p.bh * sh * bug.tall()));
        int bw = Math.min(Math.round(sw * 0.5f), bug.natural(bh));
        int bx = Math.max(0, Math.min(Math.round(p.bx * sw), sw - bw)), by = Math.max(0, Math.min(Math.round(p.by * sh), sh - bh));
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

    private Bitmap crestHome, crestAway;

    private void loadImages() {
        Game g = game;
        if (g == null) return;
        int px = Math.max(48, Ui.px(96));
        Runnable again = () -> {
            if (game == g) loadImages();
        };
        crestHome = Pics.get(g.homeLogo, px, px, Pics.FIT, again);
        crestAway = Pics.get(g.awayLogo, px, px, Pics.FIT, again);
        Bitmap comp = Pics.get(g.leagueLogo, px, px, Pics.FIT, again);
        bug.images(crestHome, crestAway, comp);
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
            anchor = new long[]{kickSecond ? 45 * 60 : 0, pressed, kickSecond ? 2 : 1};
            BoardPrefs.setAnchor(c, BoardClock.anchorWords(game.id, anchor[0], anchor[1], (int) anchor[2]));
            steady.reset();
        }
        card.note("Clock set", "The scoreboard's clock is your channel's from here" + (d >= 0 ? " · " + InStep.words(d) : ""));
    }

    // ---- the remote (the live screen asks first) ---------------------------------------------------------------------------

    /** OK held over the match: the scoreboard's menu (true), or nothing to set (false: the live screen's own). */
    boolean okLong() {
        if (game == null || menu.open) return false;
        menu.show();
        return true;
    }

    boolean ok() {
        if (moving != 0) {
            endMove(true);
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
        if (moving != 0) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1, 0); return true;
                case KeyEvent.KEYCODE_DPAD_UP: moveBy(0, -1); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: moveBy(0, 1); return true;
                default: return false;
            }
        }
        return menu.open && menu.key(code);
    }

    boolean back() {
        if (moving != 0) {
            endMove(true);
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
        if (popup != null && !popup.isOver() && !Moments.boardMoment(popupKind)) {
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

    /** A plate in the board's own look over where the channel's logo is, the channel's own mark on it, LIVE under it. */
    private final class LogoCover extends View {
        boolean live, ringed;
        private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG), pill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG), tag = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        LogoCover(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setColor(0xFFFFFFFF);
            name.setTypeface(BoardPrefs.outfit(c, 700));
            tag.setTypeface(BoardPrefs.outfit(c, 800));
            pill.setColor(0xFFE3061B);
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), hh = getHeight();
            if (w <= 0 || hh <= 0) return;
            BoardBug.StripLook look = bug.stripLook();
            ground.setColor(0xFF000000 | look.ground);
            float e = edge, round = Math.min(hh, w) * Math.max(0.12f, look.round);
            r.set(0, 0, w, hh);
            cv.drawRoundRect(r, round, round, ground);
            float tagH = live ? hh * 0.26f : 0f;
            Channel c = ch;
            String url = c == null ? null : c.logo;
            int bw = Math.max(1, Math.round(w - 4 * e - w * 0.16f)), bh = Math.max(1, Math.round(hh - 4 * e - tagH - hh * 0.12f));
            Bitmap b = Pics.get(url, bw, bh, Pics.FIT, this::invalidate);
            float midY = (hh - tagH) / 2f;
            if (b != null) {
                cv.drawBitmap(b, (w - b.getWidth()) / 2f, midY - b.getHeight() / 2f, bmp);
            } else if (c != null) {
                name.setColor(look.ink);
                name.setTextSize(Math.min(hh * 0.3f, w * 0.16f));
                String n = TextFit.fit(c.name, name, w - 4 * e);
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

    // ---- the menu, OK held over the match ---------------------------------------------------------------------------------

    private static final int R_ON = 0, R_LOOK = 1, R_LOGO = 2, R_TIMING = 3, R_NOW = 4, R_MOVE = 5, R_SIZE = 6, R_MOVE_LOGO = 7,
            R_LOGO_SIZE = 8, R_LINEUPS = 9, R_ALERTS = 10, R_POPUPS = 11, R_ASK = 12, R_SOUND = 13, R_RESET = 14;
    private static final String[] ROWS = {"Scoreboard", "Look", "Logo cover", "Timing", "It's just happened", "Move the scoreboard",
            "Scoreboard size", "Move the logo cover", "Logo cover size", "Show the line-ups", "Alerts beside the board",
            "Kick-off and goal pop-ups", "Ask at each kick-off", "Goal sound", "Back to the standard places"};

    /** The scoreboard's settings over the match: Up and Down along them, Left and Right to change one, OK to do it. */
    private final class Menu extends View {
        boolean open;
        int at;
        private final Paint panel = Dark.fill(0xF20A0D1A), hi = Dark.fill(0x33FFFFFF), ringP = Dark.line(0xFFFFFFFF, 3f);
        private final TextPaint titleP = Dark.text(40, 700, 0xFFFFFFFF), subP = Dark.text(24, 500, Dark.SOFT),
                label = Dark.text(27, 500, 0xFFFFFFFF), value = Dark.text(27, 700, Dark.ACCENT), hint = Dark.text(22, 500, Dark.MUTED);
        private final RectF r = new RectF(), rowR = new RectF();

        Menu(Context c) {
            super(c);
            setVisibility(GONE);
        }

        void show() {
            open = true;
            at = 0;
            setVisibility(VISIBLE);
            invalidate();
        }

        void close() {
            open = false;
            setVisibility(GONE);
        }

        boolean key(int code) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    at = (at + ROWS.length - 1) % ROWS.length;
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    at = (at + 1) % ROWS.length;
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    change(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    change(1);
                    return true;
                default:
                    // Channel up and down, the numbers: the live screen's own.
                    return false;
            }
        }

        private void change(int step) {
            Context c = getContext();
            switch (at) {
                case R_ON: BoardPrefs.setOn(c, !BoardPrefs.on(c)); break;
                case R_LOGO: BoardPrefs.setLogoOn(c, !BoardPrefs.logoOn(c)); break;
                case R_ALERTS: BoardPrefs.setDropsOn(c, !BoardPrefs.dropsOn(c)); break;
                case R_POPUPS: BoardPrefs.setPopupOn(c, !BoardPrefs.popupOn(c)); break;
                case R_ASK: InStep.setAsking(c, !InStep.asking(c)); break;
                case R_LOOK: {
                    BoardBug.Style[] all = BoardPrefs.looks();
                    BoardBug.Style now = BoardPrefs.style(c);
                    int i = 0;
                    for (int k = 0; k < all.length; k++) if (all[k] == now) i = k;
                    BoardPrefs.setStyle(c, all[(i + step + all.length) % all.length]);
                    bug.style(BoardPrefs.style(c));
                    break;
                }
                case R_TIMING: timing(step); break;
                case R_SIZE: {
                    float aspect = boardRect.height() > 0 ? boardRect.width() / (float) boardRect.height() * getHeight() / (float) Math.max(1, getWidth()) : 3f;
                    place().sizeBoard(step > 0 ? SIZE_STEP : 1f / SIZE_STEP, aspect);
                    BoardPrefs.setPlace(c, chKey, place());
                    break;
                }
                case R_LOGO_SIZE:
                    place().sizeLogo(step > 0 ? SIZE_STEP : 1f / SIZE_STEP);
                    BoardPrefs.setPlace(c, chKey, place());
                    break;
                case R_SOUND: {
                    String now = BoardPrefs.goalSound(c);
                    int i = 0;
                    for (int k = 0; k < Sfx.GOALS.length; k++) if (Sfx.GOALS[k].equals(now)) i = k;
                    String next = Sfx.GOALS[(i + step + Sfx.GOALS.length) % Sfx.GOALS.length];
                    BoardPrefs.setGoalSound(c, next);
                    Sfx.goal(c, next);
                    break;
                }
                default:
                    return;
            }
            update();
            invalidate();
        }

        void ok() {
            Context c = getContext();
            switch (at) {
                case R_NOW: {
                    int d = InStep.justHappened(c, chKey, changeSeen);
                    steady.reset();
                    close();
                    card.note(d >= 0 ? "Lined up" : "Nothing to line up with",
                            d >= 0 ? InStep.words(d) + " · the scoreboard and alerts wait for your channel" : "Press it as a goal, a card or the kick-off shows");
                    return;
                }
                case R_MOVE: close(); startMove(1); return;
                case R_MOVE_LOGO: close(); startMove(2); return;
                case R_LINEUPS: close(); lineups(true); return;
                case R_RESET:
                    BoardPrefs.resetPlace(c, chKey);
                    place = null;
                    update();
                    invalidate();
                    return;
                case R_TIMING:
                case R_SIZE:
                case R_LOGO_SIZE:
                    return;
                default:
                    change(1);
            }
        }

        private String valueOf(int row) {
            Context c = getContext();
            switch (row) {
                case R_ON: return BoardPrefs.on(c) ? "On" : "Off";
                case R_LOGO: return BoardPrefs.logoOn(c) ? "On" : "Off";
                case R_ALERTS: return BoardPrefs.dropsOn(c) ? "On" : "Off";
                case R_POPUPS: return BoardPrefs.popupOn(c) ? "On" : "Off";
                case R_ASK: return InStep.asking(c) ? "On" : "Off";
                case R_LOOK: return "‹  " + BoardPrefs.style(c).label + "  ›";
                case R_TIMING: {
                    int d = InStep.delay(c, chKey);
                    return "‹  Sooner   " + (d <= 0 ? "Live" : d + " s behind") + "   Later  ›";
                }
                case R_SIZE: return "‹  " + Math.round(place().bh / BoardPrefs.Place.standard().bh * 100) + "%  ›";
                case R_LOGO_SIZE: return "‹  " + Math.round(place().lw / BoardPrefs.Place.standard().lw * 100) + "%  ›";
                case R_SOUND: return "‹  " + Sfx.goalName(BoardPrefs.goalSound(c)) + "  ›";
                default: return "";
            }
        }

        @Override protected void onDraw(Canvas c) {
            float w = Ui.f(760), right = getWidth() - Ui.f(48), top = Ui.f(48), rowH = Ui.f(54);
            r.set(right - w, top, right, top + Ui.f(150) + rowH * ROWS.length + Ui.f(70));
            c.drawRoundRect(r, Ui.f(30), Ui.f(30), panel);
            float x = r.left + Ui.f(40);
            c.drawText("Scoreboard", x, r.top + Ui.f(70), titleP);
            Game g = game;
            String sub = g == null ? "" : full(g.homeName, g.home) + " v " + full(g.awayName, g.away);
            c.drawText(TextFit.fit(sub, subP, w - Ui.f(80)), x, r.top + Ui.f(110), subP);
            float y = r.top + Ui.f(140);
            for (int i = 0; i < ROWS.length; i++) {
                RectF row = rowR;
                row.set(r.left + Ui.f(20), y, r.right - Ui.f(20), y + rowH - Ui.f(6));
                if (i == at) {
                    c.drawRoundRect(row, Ui.f(14), Ui.f(14), hi);
                    c.drawRoundRect(row, Ui.f(14), Ui.f(14), ringP);
                }
                float base = row.centerY() + Ui.f(10);
                c.drawText(ROWS[i], x, base, label);
                String v = valueOf(i);
                if (!v.isEmpty()) c.drawText(v, row.right - Ui.f(20) - value.measureText(v), base, value);
                y += rowH;
            }
            String tip = "Back to close · Left and Right to change";
            c.drawText(tip, x, r.bottom - Ui.f(28), hint);
        }
    }

    /** Timing: the board (its clock, its score and its alerts) a step later (1) or sooner (-1), as the launcher's. */
    private void timing(int step) {
        Context c = getContext();
        InStep.nudge(c, chKey, step * InStep.STEP_S);
        steady.reset();
        if (anchor != null && game != null) {
            anchor = BoardClock.moved(anchor, step * InStep.STEP_S * 1000L);
            BoardPrefs.setAnchor(c, BoardClock.anchorWords(game.id, anchor[0], anchor[1], (int) anchor[2]));
        }
    }

    // ---- previews ------------------------------------------------------------------------------------------------------------

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
            l.added = BoardClock.addedWords(l.clock, r.detail, announced);
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
        if (withMenu) menu.show();
        if (withCard) card.ask(false);
    }
}
