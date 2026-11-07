package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.F1;
import com.gathin.tvhomephone.sports.F1Live;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.Kinds;
import com.gathin.tvhomephone.sports.LiveClock;
import com.gathin.tvhomephone.sports.LiveGames;
import com.gathin.tvhomephone.sports.OnScreen;
import com.gathin.tvhomephone.sports.PlayerPhotos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The scoreboard while something is watched, and the alerts that drop from it: in the player over the picture
 * (ScoreOverlay) and over other apps (FloatService). It shows the channel's own game when it has one, read every 15
 * seconds and shown as the stream shows it (OnScreen: the score changes as the goal goes in on the screen, the clock is
 * the screen's); on an F1 channel, the session (F1Live), in step the same way; otherwise the first followed club's game
 * on now. Goals and the other football alerts, F1 and the other sport, and breaking news slide out sideways beside it,
 * in line with it and in its own look (BoardDrop), each as switched on here; a goal on it is shown first, as
 * broadcasters do, then its scorer standing up out of the caption. The line-ups come as the TV's line-up card beside it,
 * top to top (BoardLineups). Its stats and facts a few times a game (BoardStats). Its clock counts on through added
 * time, the minutes put up beside it from the half's last minute; full time shows a little under a minute, then it goes.
 */
public final class ScoreBoard implements LiveAlerts.Listener {
    /** What it is shown over. */
    interface Source {
        /** The channel being watched; null over other apps or a film. */
        Channel channel();

        /** The channel's own game, from its tile; null when none. */
        Game game();

        /** How far the picture runs behind the real game, in ms: the channel's game (true) or anything else. */
        long behindMs(boolean channelGame);

        /** The scoreboard tapped; held. */
        void tapped();

        void held();

        /** An alert's drop tapped (a goal elsewhere: its game, to watch). */
        void alertTapped(Events.Alert a);
    }

    static final String FOOTBALL = "football", SPORT = "sport", NEWS = "news";
    private static final long READ_MS = 15_000L, DROP_MS = 7_000L;
    /** Full time on the screen: up this long (its alert, its stats), then away; never longer than the most, an alert or not. */
    static final long FT_SHOWN_MS = 50_000L, FT_MOST_MS = 60_000L;
    /** A goal's scorer's photo is waited for this long before the caption goes without it. */
    private static final long PHOTO_WAIT_MS = 2_500L;
    /** The match summary (stats, facts, the added time put up) is read again this often, and near the ends of halves this often. */
    static final long STATS_READ_MS = 120_000L, ENDS_READ_MS = 30_000L;
    /** The first of the stats' moments: this long before kick-off at most. */
    static final long PRE_STATS_MS = 10 * 60_000L;
    private static final Pattern F1_CHANNEL = Pattern.compile("(?i)\\bf1\\b|formula ?(1|one)");

    // ---- the switches -------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("score_overlay", Context.MODE_PRIVATE);
    }

    /** The scoreboard over the player's picture (on until switched off). */
    static boolean on(Context c) { return prefs(c).getBoolean("on", true); }

    static void setOn(Context c, boolean v) { prefs(c).edit().putBoolean("on", v).apply(); }

    /** Over the channel's own scoreboard, its size, when one is found (on until switched off). */
    static boolean cover(Context c) { return prefs(c).getBoolean("cover", true); }

    static void setCover(Context c, boolean v) { prefs(c).edit().putBoolean("cover", v).apply(); }

    /**
     * Kept still: the scoreboard and the mark over the logo stay where they are put (no burn-in drifting, and once
     * over the channel's own they aren't moved when it is looked for again). On until switched off.
     */
    static boolean still(Context c) { return prefs(c).getBoolean("still", true); }

    static void setStill(Context c, boolean v) { prefs(c).edit().putBoolean("still", v).apply(); }

    /** The scoreboard's size in its own corner (not over the channel's, nor placed by hand): 1 as it was. */
    static float size(Context c) { return prefs(c).getFloat("size", 1f); }

    static void setSize(Context c, float v) { prefs(c).edit().putFloat("size", v).apply(); }

    /** The sizes offered, with their names. */
    static final float[] SIZES = {0.8f, 1f, 1.25f, 1.5f};
    static final String[] SIZE_NAMES = {"Small", "Medium", "Large", "Extra large"};

    /** Which alerts drop from it: FOOTBALL, SPORT (F1 and the rest), NEWS (breaking news, the weather). */
    static boolean shows(Context c, String group) { return prefs(c).getBoolean("a." + group, true); }

    static void setShows(Context c, String group, boolean v) { prefs(c).edit().putBoolean("a." + group, v).apply(); }

    /** Over other apps (off until switched on; Android's "Display over other apps" with it). */
    static boolean floating(Context c) { return prefs(c).getBoolean("float", false); }

    static void setFloating(Context c, boolean v) { prefs(c).edit().putBoolean("float", v).apply(); }

    /** Its look (OverlayBug.Style). */
    static OverlayBug.Style style(Context c) { return OverlayBug.Style.of(prefs(c).getString("style", "CLASSIC")); }

    static void setStyle(Context c, OverlayBug.Style s) { prefs(c).edit().putString("style", s.name()).apply(); }

    /** The channel's logo covered with Homefield's, and how (LogoCover.Look; Badge until chosen). */
    static LogoCover.Look logoCover(Context c) { return LogoCover.Look.of(prefs(c).getString("logo", "BADGE")); }

    static void setLogoCover(Context c, LogoCover.Look l) {
        SharedPreferences.Editor e = prefs(c).edit().putString("logo", l.name());
        // Its look kept, so switching it back on (Move and resize) puts the same one back.
        if (l != LogoCover.Look.OFF) e.putString("logo_last", l.name());
        e.apply();
    }

    /** LIVE along the logo cover's foot while the game is live (on unless switched off). */
    static boolean logoLive(Context c) {
        return prefs(c).getBoolean("logo_live", true);
    }

    static void setLogoLive(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo_live", on).apply();
    }

    /** The competition's logo beside the logo cover's look (on unless switched off). */
    static boolean logoComp(Context c) {
        return prefs(c).getBoolean("logo_comp", true);
    }

    static void setLogoComp(Context c, boolean on) {
        prefs(c).edit().putBoolean("logo_comp", on).apply();
    }

    /** The picture tag on the logo's cover: Auto (from the channel's name), none, or one set (HD, UHD, 4K, with HDR or not). */
    static ChannelMark.Tag logoTag(Context c) {
        return ChannelMark.Tag.of(prefs(c).getString("logo_tag", ""));
    }

    static void setLogoTag(Context c, ChannelMark.Tag t) {
        prefs(c).edit().putString("logo_tag", t.name()).apply();
    }

    /** The mark over the channel's logo as it was before it was switched off. */
    static LogoCover.Look lastLogo(Context c) {
        LogoCover.Look l = LogoCover.Look.of(prefs(c).getString("logo_last", "BADGE"));
        return l == LogoCover.Look.OFF ? LogoCover.Look.BADGE : l;
    }

    /**
     * The scoreboard itself over the picture (Move and resize's switch): off, it is hidden while its clock and score
     * still run and the mark over the logo stays as it is.
     */
    static boolean boardShown(Context c) { return prefs(c).getBoolean("board_shown", true); }

    static void setBoardShown(Context c, boolean v) { prefs(c).edit().putBoolean("board_shown", v).apply(); }

    /** Whether the board reads the channel's own scoreboard to keep its time and score (on until switched off). */
    static boolean reads(Context c) { return prefs(c).getBoolean("read", true); }

    static void setReads(Context c, boolean v) { prefs(c).edit().putBoolean("read", v).apply(); }

    /**
     * Broadcast mode: when the channel goes to its adverts before kick-off, at half time or after the game (its logo
     * gone), Homefield's own break instead (BreakScreen). On until switched off.
     */
    static boolean broadcast(Context c) { return prefs(c).getBoolean("broadcast", true); }

    static void setBroadcast(Context c, boolean v) { prefs(c).edit().putBoolean("broadcast", v).apply(); }

    /** What the break can show, {key, name}: each on until switched off in Settings. */
    static final String[][] BREAK_KINDS = {{"tv", "Your TV"}, {"facts", "Fun facts"}, {"prayer", "Prayer times"}, {"quiz", "Quiz"},
            {"reminders", "Reminders"}, {"games", "Other games"}, {"news", "Headlines"}, {"trailer", "Trailers"}, {"today", "Today & weather"}};

    static boolean breakShows(Context c, String kind) {
        return !prefs(c).getStringSet("break_off", java.util.Collections.emptySet()).contains(kind);
    }

    static void setBreakShows(Context c, String kind, boolean on) {
        java.util.Set<String> off = new java.util.HashSet<>(prefs(c).getStringSet("break_off", java.util.Collections.emptySet()));
        if (on) off.remove(kind);
        else off.add(kind);
        prefs(c).edit().putStringSet("break_off", off).apply();
        BreakScreen.forget();
    }

    /** How far behind the other apps' pictures run (their streams' delay), in seconds. */
    static int otherDelay(Context c) { return prefs(c).getInt("other_delay", 0); }

    static void setOtherDelay(Context c, int s) { prefs(c).edit().putInt("other_delay", LiveAlerts.clamp(s)).apply(); }

    /** Pure: the switch group an alert's kind is under. */
    static String group(String kind) {
        String g = Kinds.group(kind);
        return Kinds.SPORT.equals(g) ? SPORT : Kinds.NEWS.equals(g) ? NEWS : FOOTBALL;
    }

    /** Whether a channel's name says F1. */
    static boolean f1Channel(Channel ch) {
        return ch != null && ch.name != null && F1_CHANNEL.matcher(ch.name).find();
    }

    // ---- the parts ----------------------------------------------------------------------------------------------

    private final Context c;
    private final Source src;
    private final Handler h = new Handler(Looper.getMainLooper());
    /**
     * The scoreboard and, beside it in line with it, what slides out of it (the stats, an alert) and the line-ups' card;
     * the board drawn over their tucked ends. The host places it at the board's place less (boardDx(), boardDy()).
     */
    final FrameLayout view;
    final OverlayBug bug;
    /** The alert sliding out beside it, in its look. */
    final BoardDrop strip;
    /** The game's stats beside it, a few times a game. */
    final BoardStats stats;
    /** The line-ups, as the TV's card, beside it. */
    final BoardLineups lineups;
    /** The game's stats and facts read last, by their names ("Possession", "Form"…), each {name, home, away[, kind]}. */
    private Map<String, String[]> statsBy = new HashMap<>();
    /** The added time put up in each half (minutes; -1 until it is), for the game read. */
    private int[] addedUp = {-1, -1, -1, -1};
    private String statsFor = "";
    private long statsReadAt, goalShownAt;
    private boolean statsReading;
    /** The stats moments shown, "game|moment": each once a game. */
    private final Set<String> statsShown = new java.util.HashSet<>();
    /** The goals captioned (their keys): each once, by its alert or by the board itself. */
    private final Set<String> captioned = new java.util.HashSet<>();
    /** Full time: the game it showed for, and since when. */
    private String ftFor = "";
    private long ftAt;
    /** The minute the board's clock shows (-1: none), for how often the summary is read. */
    private int boardMinute = -1;
    private final OnScreen screen = new OnScreen();
    /** F1 reads as they came: {when, session}. */
    private final ArrayDeque<Object[]> f1Reads = new ArrayDeque<>();
    private final BreakingNews news = BreakingNews.shared();
    private final ArrayDeque<Drop> drops = new ArrayDeque<>();
    private final Map<String, String> f1Teams = new HashMap<>();
    private boolean running, dropping, channelGame;
    /** Something new on it (a goal shown, an alert dropped): the burn-in guard brightens it again. */
    Runnable changed;
    private int mode;
    private static final int NONE = 0, FOOTBALL_MODE = 1, F1_MODE = 2;
    private String crestsFor = "";
    private int lastHome = -1, lastAway = -1;
    private String lastId = "";
    /**
     * Where the board stands in view (from its left, and the room above it for a scorer standing up); what slides out:
     * on the board's left or right, its widest, how far it tucks under the board, the gap beside a pill.
     */
    private int boardAt, head, sideMax, tuck, sideGap;
    private boolean sideLeft;
    /** The room round the board as the host last said, in pixels: to its left, right, above and below. */
    private int roomLeft, roomRight = 100_000, roomAbove, roomBelow = 100_000;

    /** One alert's drop. */
    static final class Drop {
        String chip = "", title = "", line = "", line2 = "";
        int colour;
        /** The game's clubs' colours (0 when it is no game's), and the side it is about: 1 home, 2 away, 0 both or neither. */
        int homeColour, awayColour, side;
        /** Both starting elevens, the home side's in line and the away side's in line2, each led by its club and shape. */
        boolean lineups;
        /** The board's own game: the board beside it already shows the clubs and the score. */
        boolean own;
        /** The goal it captions (its key), to caption each goal once; null for the rest. */
        String key;
        /** The scorer's (or the player's) photo, head and shoulders; both clubs' crests; each may be null. */
        Bitmap photo, homeCrest, awayCrest;
        Events.Alert alert;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            h.removeCallbacks(this);
            if (!running) return;
            draw(System.currentTimeMillis());
            h.postDelayed(this, 250);
        }
    };

    private final Runnable read = this::read;
    private final Runnable hide = this::hideDrop;
    private final Runnable nextDrop = this::next;

    ScoreBoard(Context ctx, Source src) {
        this.c = ctx;
        this.src = src;
        float d = ctx.getResources().getDisplayMetrics().density;
        view = new FrameLayout(ctx);
        view.setClipChildren(false);
        // What slides out first, the board over it: their near ends tuck under the board.
        stats = new BoardStats(ctx);
        view.addView(stats, new FrameLayout.LayoutParams(Math.round(260 * d), Math.round(30 * d)));
        strip = new BoardDrop(ctx);
        strip.setOnClickListener(v -> {
            Drop now = strip.drop();
            hideDrop();
            if (now != null && now.alert != null) src.alertTapped(now.alert);
        });
        view.addView(strip, new FrameLayout.LayoutParams(Math.round(280 * d), Math.round(30 * d)));
        lineups = new BoardLineups(ctx);
        view.addView(lineups, new FrameLayout.LayoutParams(Math.round(300 * d), Math.round(100 * d)));
        bug = new OverlayBug(ctx);
        bug.style(style(ctx));
        bug.setVisibility(View.GONE);
        bug.setOnClickListener(v -> src.tapped());
        bug.setOnLongClickListener(v -> {
            src.held();
            return true;
        });
        view.addView(bug, new FrameLayout.LayoutParams(-2, Math.round(30 * d)));
    }

    // ---- where it stands, and the room round it -------------------------------------------------------------------

    /**
     * The room round the board where it stands, in pixels (to its left, right, above and below: the host's own edges,
     * short of the logo's mark where it is level with the board): what slides out goes beside it on the side with room
     * (its right, else its left when there's more room there), its scorer standing up into the room above it. The host
     * then places view at the board's place less (boardDx(), boardDy()).
     */
    void room(int left, int right, int above, int below) {
        roomLeft = Math.max(0, left);
        roomRight = Math.max(0, right);
        roomAbove = Math.max(0, above);
        roomBelow = Math.max(0, below);
        layoutSide();
    }

    /** How far the board stands from view's left and top. */
    int boardDx() {
        return boardAt;
    }

    int boardDy() {
        return head;
    }

    /** The board's height in pixels. */
    private int boardH() {
        ViewGroup.LayoutParams lp = bug.getLayoutParams();
        if (lp != null && lp.height > 0) return lp.height;
        return bug.getHeight() > 0 ? bug.getHeight() : Math.round(30 * c.getResources().getDisplayMetrics().density);
    }

    /** Its width, as drawn or as its words need. */
    private int boardW() {
        ViewGroup.LayoutParams lp = bug.getLayoutParams();
        if (lp != null && lp.width > 0) return lp.width;
        return bug.getWidth() > 0 ? bug.getWidth() : bug.natural(boardH());
    }

    /** What the strips' words are sized from: the board's height as set (Stacked and Card are drawn taller). */
    private float unit() {
        return boardH() / Math.max(1f, bug.style().tall);
    }

    /** Pure: which side what slides out goes: its left only when there's little on its right and more on its left. */
    static boolean outLeft(int roomLeft, int roomRight, float unit) {
        return roomRight < unit * 6 && roomLeft > roomRight;
    }

    private void layoutSide() {
        int bh = boardH();
        float unit = unit();
        OverlayBug.StripLook look = bug.stripLook();
        float dp = c.getResources().getDisplayMetrics().density;
        tuck = look.apart ? 0 : Math.round(Math.min(8 * dp, bh * look.round)) + 1;
        sideGap = look.apart ? Math.round(3 * dp) : 0;
        sideLeft = outLeft(roomLeft, roomRight, unit);
        int room = Math.max(Math.round(unit * 3), Math.min(Math.round(unit * 15), sideLeft ? roomLeft : roomRight) - sideGap);
        sideMax = room + tuck;
        int at = sideLeft ? room + sideGap : 0, up = Math.min(Math.round(bh * 0.55f), roomAbove);
        if (at != boardAt || up != head) {
            boardAt = at;
            head = up;
        }
        FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) bug.getLayoutParams();
        if (bl.leftMargin != boardAt || bl.topMargin != head) {
            bl.leftMargin = boardAt;
            bl.topMargin = head;
            bug.setLayoutParams(bl);
        }
        if (stats.isOut()) beside(stats, stats.getLayoutParams().width, false);
        if (strip.isOut()) beside(strip, strip.getLayoutParams().width, strip.head > 0);
    }

    /**
     * How far what slides out tucks under the board, and the gap beside it: on its right with the added time in a tab
     * beside it, none and a little gap, so it starts after the tab; else as laid out.
     */
    private int nearTuck() {
        return tabOut() ? 0 : tuck;
    }

    private int nearGap() {
        return tabOut() ? Math.round(boardH() * 0.06f) : sideGap;
    }

    private boolean tabOut() {
        return !sideLeft && bug.tabIn(boardW(), boardH()) > 0;
    }

    /** What slides out (the stats, an alert) w wide, as high as the board and beside it, its near end tucked under it. */
    private void beside(View v, int w, boolean withHead) {
        int bh = boardH(), bw = boardW();
        boolean onBoard = boardUp();
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) v.getLayoutParams();
        int left = !onBoard ? boardAt : sideLeft ? boardAt + tuck - sideGap - w : boardAt + bw - nearTuck() + nearGap();
        int height = bh + (withHead ? head : 0), top = withHead ? 0 : head;
        if (l.width == w && l.height == height && l.leftMargin == left && l.topMargin == top) return;
        l.width = w;
        l.height = height;
        l.leftMargin = left;
        l.topMargin = top;
        v.setLayoutParams(l);
    }

    private boolean live;

    /** Whether the board shows a game being played now (the logo's Broadcast mark says LIVE). */
    boolean live() {
        return live && bug.getVisibility() == View.VISIBLE;
    }

    /** Whether the board itself is up to come out of. */
    private boolean boardUp() {
        return bug.getVisibility() == View.VISIBLE && !bug.closing() && bug.getWidth() > 0;
    }

    void start() {
        bug.style(style(c));
        if (running) return;
        running = true;
        LiveAlerts.addListener(this);
        h.post(read);
        h.post(tick);
    }

    void stop() {
        running = false;
        ScreenTime.clear(screen);
        LiveAlerts.removeListener(this);
        h.removeCallbacksAndMessages(null);
        drops.clear();
        strip.gone();
        dropping = false;
        stats.gone();
        lineups.gone();
    }

    /** The small window: the board alone, no drops (they'd cover the picture). */
    void small(boolean on) {
        small = on;
        if (on) {
            drops.clear();
            h.removeCallbacks(hide);
            h.removeCallbacks(nextDrop);
            strip.gone();
            dropping = false;
            stats.fold(null);
            lineups.gone();
        }
    }

    private boolean small;

    /** The channel or its game changed: everything read again at once. */
    void changed() {
        confirmedId = "";
        ScreenTime.clear(screen);
        screen.clear();
        f1Reads.clear();
        mode = NONE;
        lastId = "";
        bug.setVisibility(View.GONE);
        h.removeCallbacks(read);
        if (running) h.post(read);
    }

    /** The next of the followed games on now (a tap over other apps). */
    void cycle() {
        cycle = true;
        h.removeCallbacks(read);
        if (running) h.post(read);
    }

    private boolean cycle;

    /** Whether a football game is on the board (the channel's own or a followed club's). */
    boolean football() {
        return mode == FOOTBALL_MODE;
    }

    /** The competition on the board: its logo (null while read, or none) and its name, for the logo cover's Competition look. */
    private Bitmap compPic;
    private String compName = "";

    Bitmap compPic() {
        return compPic;
    }

    String compName() {
        return compName;
    }

    /** Whether a Formula 1 session is on the board (the channel looks' Matchday says GRAND PRIX). */
    boolean f1() {
        return mode == F1_MODE;
    }

    /**
     * The game on the board is the one the channel shows (its scoreboard read off the picture fits it): from now it is
     * shown as the channel shows it, with the channel's delay, until another game or channel.
     */
    void confirm() {
        confirmedId = lastId;
        channelGame = true;
        screen.behind(src.behindMs(true));
        if (src instanceof ScoreOverlay) ScreenTime.set(screen, lastId);
    }

    private String confirmedId = "";

    /** Whether it shows a scoreboard now (not only alerts). */
    boolean showing() {
        return bug.getVisibility() == View.VISIBLE;
    }

    /** Whether the board is the channel's own game (the one the picture shows: worth covering its scoreboard). */
    boolean channelGame() {
        return mode == FOOTBALL_MODE && channelGame;
    }

    OnScreen screen() {
        return screen;
    }

    /** The picture's delay changed (the alerts' chips): the board follows. */
    void behind() {
        screen.behind(src.behindMs(channelGame));
    }

    // ---- reading ---------------------------------------------------------------------------------------------------

    private void read() {
        h.removeCallbacks(read);
        if (!running) return;
        Channel ch = src.channel();
        Game own = src.game();
        boolean f1 = own == null && f1Channel(ch);
        boolean news = Kinds.on(c, "news") && this.news.due(System.currentTimeMillis());
        int px = Math.round(28 * c.getResources().getDisplayMetrics().density * 2);
        String had = crestsFor, was = lastId;
        boolean next = cycle;
        cycle = false;
        App.io.execute(() -> {
            Game g = null;
            F1Live.Session s = null;
            boolean mine = false;
            try {
                if (own != null) {
                    g = LiveGames.again(own);
                    if (g == null) g = own;
                    mine = true;
                } else if (f1) {
                    s = F1Live.now();
                } else {
                    List<Game> followed = LiveGames.followed(c);
                    int at = -1;
                    for (int i = 0; i < followed.size(); i++) if (was.equals(followed.get(i).id)) at = i;
                    if (next && followed.size() > 1) g = followed.get((at + 1) % followed.size());
                    else if (at >= 0) g = followed.get(at);
                    else if (!followed.isEmpty()) g = followed.get(0);
                    if (g == null && shows(c, SPORT)) s = F1Live.now();
                }
                if (s != null && f1Teams.isEmpty()) {
                    for (F1.Standing d : F1.drivers()) f1Teams.put(F1Live.code(d.family), d.teamId);
                }
            } catch (RuntimeException ignored) {
                // nothing this time
            }
            Bitmap[] crests = null;
            if (g != null && g.id != null && !g.id.equals(had)) {
                crests = new Bitmap[]{LiveGames.picture(c, LiveGames.homeCrest(g), px), LiveGames.picture(c, LiveGames.awayCrest(g), px),
                        LiveGames.picture(c, com.gathin.tvhomephone.sports.Competitions.logo(g), px)};
            }
            List<String[]> stories = news ? this.news.read(System.currentTimeMillis()) : null;
            Game got = g;
            F1Live.Session session = s;
            boolean channel = mine;
            Bitmap[] pics = crests;
            App.main.post(() -> {
                if (!running) return;
                long now = System.currentTimeMillis();
                if (got != null) {
                    // The channel's own game: its tile's, or a followed one its scoreboard was read showing.
                    boolean mineNow = channel || (got.id != null && got.id.equals(confirmedId));
                    channelGame = mineNow;
                    screen.behind(src.behindMs(mineNow));
                    screen.add(got, now);
                    readStats(got, now);
                    mode = FOOTBALL_MODE;
                    lastId = got.id == null ? "" : got.id;
                    // The player's board on the channel's own game: the tile and the small window read the same time.
                    if (src instanceof ScoreOverlay) {
                        if (mineNow) ScreenTime.set(screen, lastId);
                        else ScreenTime.clear(screen);
                    }
                    if (pics != null) {
                        crestsFor = lastId;
                        bug.crests(pics[0], pics[1], pics[2]);
                        compPic = pics[2];
                    }
                    compName = got.leagueName == null ? "" : got.leagueName;
                } else if (session != null) {
                    channelGame = false;
                    f1Reads.addLast(new Object[]{now, session});
                    while (f1Reads.size() > 40) f1Reads.removeFirst();
                    mode = F1_MODE;
                    compPic = null;
                    compName = "F1";
                } else if (own == null) {
                    mode = NONE;
                }
                // A breaking story: an alert like the rest (its notification, its pop-up, its drop here, once).
                if (stories != null) BreakingNews.post(c, stories);
                draw(now);
                h.postDelayed(read, READ_MS);
            });
        });
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private void draw(long now) {
        OverlayBug.Look l = null;
        Game shown = null;
        if (mode == FOOTBALL_MODE) {
            Game g = screen.game(now);
            if (g != null) {
                shown = g;
                l = look(g, screen.boardClock(now));
                // From the half's last minute, the added time put up beside the clock (the channel's own when it shows it).
                if (l.live) l.added = addedBox(period(g, l.clock), minuteOfClock(l.clock), addedUp, l.added);
                // A goal as the screen shows it: that side lit, then its scorer captioned.
                if (g.id != null && g.id.equals(lastShownId)) {
                    int side = g.homeScore > lastHome && lastHome >= 0 ? 1 : g.awayScore > lastAway && lastAway >= 0 ? 2 : 0;
                    if (side > 0) {
                        bug.goal(side);
                        goalShownAt = now;
                        if (changed != null) changed.run();
                        caption(g, side);
                    }
                }
                lastShownId = g.id;
                lastHome = g.homeScore;
                lastAway = g.awayScore;
                boardMinute = minuteOfClock(l.clock);
                // Full time on the screen: shown a little under a minute (its alert, its stats), then away.
                String id = g.id == null ? "" : g.id;
                if (g.state == Game.POST) {
                    if (!id.equals(ftFor)) {
                        ftFor = id;
                        ftAt = now;
                        if (changed != null) changed.run();
                    }
                    if (ftGone(now - ftAt, dropping || lineups.isOut())) l = null;
                } else if (id.equals(ftFor)) {
                    ftFor = "";
                }
            }
        } else if (mode == F1_MODE && !f1Reads.isEmpty()) {
            long at = now - src.behindMs(false);
            Object[] pick = f1Reads.peekFirst();
            for (Object[] r : f1Reads) if ((Long) r[0] <= at) pick = r;
            l = f1Look((F1Live.Session) pick[1], at);
        }
        if (l == null && preview) l = sample();
        if (l == null || hidden) {
            // Away as a broadcaster's goes: what is out beside it back in, wiped back into its first block, closed to a line.
            if (bug.getVisibility() == View.VISIBLE && !bug.closing()) {
                stats.fold(null);
                if (strip.isOut()) strip.fold(null);
                lineups.gone();
                bug.close(() -> bug.setVisibility(View.GONE));
            }
            return;
        }
        bug.set(l);
        live = l.live;
        if (bug.getVisibility() != View.VISIBLE || bug.closing()) {
            boolean fresh = bug.getVisibility() != View.VISIBLE;
            // From nothing when it was away (a new channel, switched back on); from where it was if it was going.
            if (fresh) bug.shownAt(0f);
            bug.setVisibility(View.VISIBLE);
            bug.open();
        }
        stats.colours(l.homeColour, l.awayColour);
        if (mode == FOOTBALL_MODE && shown != null) maybeStats(shown, now);
    }

    /** Pure: whether full time has been shown long enough: a little under a minute, or a minute at most whatever is out. */
    static boolean ftGone(long sinceFt, boolean busy) {
        return sinceFt > FT_MOST_MS || (sinceFt > FT_SHOWN_MS && !busy);
    }

    /**
     * Pure: the period a clock is in (1, 2; 3 and 4 in extra time): the game's own as ESPN gives it; else its minute's
     * first number ("90'+3'": the second half's, not the 93rd minute's); else, knowing neither, the clock's own minute.
     */
    static int period(Game g, String clock) {
        if (g.period >= 1 && g.period <= 4) return g.period;
        int n = firstNumber(g.displayClock);
        if (n <= 0) n = firstNumber(g.detail);
        if (n > 0) return n <= 45 ? 1 : n <= 90 ? 2 : n <= 105 ? 3 : 4;
        int m = minuteOfClock(clock);
        return m < 45 ? 1 : m < 90 ? 2 : m < 105 ? 3 : 4;
    }

    /** Pure: the minute a period ends at: 45, 90; 105 and 120 in extra time. */
    static int periodEnd(int period) {
        return period <= 1 ? 45 : period == 2 ? 90 : period == 3 ? 105 : 120;
    }

    /**
     * Pure: whether the board is in added time: the game running (not at half time, nor in a shoot-out) and its clock
     * past its period's end ("45:47" in the first half, "92:15" in the second; not "45:30" as the second begins).
     */
    static boolean stoppage(Game g, String clock) {
        if (g == null || g.state != Game.LIVE || g.halftime || g.period >= 5) return false;
        int m = minuteOfClock(clock);
        return m >= 0 && m >= periodEnd(period(g, clock));
    }

    /** "90'+3'" as 90: its first run of digits; 0 when it has none. */
    private static int firstNumber(String s) {
        if (s == null) return 0;
        int n = 0, i = 0;
        while (i < s.length() && !Character.isDigit(s.charAt(i))) i++;
        for (; i < s.length() && Character.isDigit(s.charAt(i)) && n < 1000; i++) n = n * 10 + (s.charAt(i) - '0');
        return n;
    }

    /**
     * Pure: the "+N" beside the clock: from the half's last minute (the 45th, the 90th; 105th and 120th in extra time),
     * the added time put up once known (the channel's own when it shows one); before then, or until it is known, none.
     */
    static String addedBox(int half, int minute, int[] put, String channels) {
        int last = half <= 1 ? 44 : half == 2 ? 89 : half == 3 ? 104 : 119;
        if (minute < last) return "";
        if (channels != null && !channels.isEmpty()) return channels;
        int n = put != null && half >= 1 && half <= put.length ? put[half - 1] : -1;
        return n > 0 ? "+" + n : "";
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

    /** Pure: how often the summary is read: every half minute near the ends of halves (for the added time), else every two. */
    static long readEvery(int minute) {
        boolean ends = minute >= 42 && minute <= 52 || minute >= 87 && minute <= 100 || minute >= 102 && minute <= 110
                || minute >= 117 && minute <= 126;
        return ends ? ENDS_READ_MS : STATS_READ_MS;
    }

    /**
     * Pure: which of the game's few stats moments it is (null: none): its last ten minutes before kick-off ("pre"), the
     * middle of the first half ("h1", minutes 25 to 41), half time ("ht"), the middle of the second half ("h2", 64 to
     * 82) and full time ("ft"). Each comes once a game: enough to tell its story, never so often it is in the way.
     */
    static String statsSlot(boolean pre, long toKickoff, int state, boolean halftime, int minute) {
        if (pre) return toKickoff > 60_000L && toKickoff <= PRE_STATS_MS ? "pre" : null;
        if (state == Game.POST) return "ft";
        if (halftime) return "ht";
        if (state != Game.LIVE) return null;
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
                want = new String[]{"Possession", "Shots", "On target", "Corners"};
                break;
            case "ht":
                want = new String[]{"Possession", "Shots", "On target", "Corners", "Pass accuracy", "Fouls"};
                most = 5;
                break;
            case "h2":
                want = new String[]{"Possession", "Player of the match", "Corners", "On target", "Shots"};
                break;
            case "ft":
                want = new String[]{"Possession", "Shots", "Player of the match", "Pass accuracy", "On target"};
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

    /**
     * The game's stats sliding out beside it at one of its few moments (statsSlot). Never while an alert or the line-ups
     * are out, just after a goal, in the small window or while it is placed by hand; and never while the scores have a
     * goal the picture hasn't shown yet (its shot on target would give it away).
     */
    private void maybeStats(Game g, long now) {
        if (small || preview || hidden || dropping || lineups.isOut() || stats.isOut() || bug.closing() || !boardUp()) return;
        if (statsBy.isEmpty() || !lastId.equals(statsFor) || now - goalShownAt < 90_000L) return;
        boolean pre = g.state == Game.PRE;
        if (!pre && screen.ahead(now)) return;
        String slot = statsSlot(pre, g.kickoff - now, g.state, g.halftime, boardMinute);
        String key = lastId + "|" + slot;
        if (slot == null || statsShown.contains(key)) return;
        List<String[]> pick = statsFor(slot, statsBy);
        if (pick.isEmpty()) return;
        if (statsShown.size() > 60) statsShown.clear();
        statsShown.add(key);
        if (changed != null) changed.run();
        stats.place(bug.stripLook(), unit(), nearTuck(), sideLeft, 0);
        stats.alone(false);
        beside(stats, stats.widthFor(pick, boardH(), sideMax), false);
        stats.show(pick);
    }

    /** The game's summary read again now and then (off the main thread): its stats and facts, the added time put up. */
    private void readStats(Game g, long now) {
        if (g == null || g.id == null || g.slug == null || statsReading) return;
        long every = readEvery(boardMinute);
        if (g.id.equals(statsFor) && now - statsReadAt < every) return;
        statsReadAt = now;
        statsReading = true;
        String id = g.id, slug = g.slug;
        String homeCode = code(g.home, g.homeName), awayCode = code(g.away, g.awayName);
        int state = g.state;
        App.io.execute(() -> {
            Espn.Summary sum = null;
            try {
                sum = Espn.summary(slug, id, every - 5_000L);
            } catch (RuntimeException ignored) {
                // another time
            }
            Espn.Summary got = sum;
            Map<String, String[]> by = got == null ? new HashMap<>() : statsOf(got, state, homeCode, awayCode);
            App.main.post(() -> {
                statsReading = false;
                if (got == null) return;
                if (!id.equals(statsFor)) addedUp = new int[]{-1, -1, -1, -1};
                for (int i = 0; i < 4; i++) if (got.added[i] > 0) addedUp[i] = got.added[i];
                if (!by.isEmpty()) statsBy = by;
                statsFor = id;
            });
        });
    }

    /**
     * Pure: the stats and facts worth a strip from the game's summary, by name: the match's (its possession, shots, on
     * target, corners, fouls), the pass accuracy, each side's form (its last five), its place in the table, the chances
     * before kick-off and the player of the match so far. Before kick-off only the facts.
     */
    static Map<String, String[]> statsOf(Espn.Summary s, int state, String homeCode, String awayCode) {
        Map<String, String[]> by = new HashMap<>();
        if (state != Game.PRE) {
            for (String[] x : s.stats) {
                if (x.length < 3) continue;
                String name = x[0], hv = x[1], av = x[2];
                if ("Possession".equals(name)) {
                    hv = pct(hv);
                    av = pct(av);
                }
                by.put(name, new String[]{name, hv, av});
            }
            String ph = passes(s.homeNumbers), pa = passes(s.awayNumbers);
            if (!ph.isEmpty() && !pa.isEmpty()) by.put("Pass accuracy", new String[]{"Pass accuracy", ph, pa});
            String[] star = star(s, homeCode, awayCode);
            if (star != null) by.put(star[0], star);
        }
        String fh = form(s.homeLast), fa = form(s.awayLast);
        if (!fh.isEmpty() && !fa.isEmpty()) by.put("Form", new String[]{"Form", fh, fa, "form"});
        String th = place(s.table, s.homeId), ta = place(s.table, s.awayId);
        if (!th.isEmpty() && !ta.isEmpty()) by.put("League table", new String[]{"League table", th, ta, "text"});
        if (!s.winHome.isEmpty() && !s.winAway.isEmpty()) by.put("Win chance", new String[]{"Win chance", s.winHome, s.winAway});
        return by;
    }

    private static String pct(String v) {
        try {
            return Math.round(Float.parseFloat(v.replace("%", "").trim())) + "%";
        } catch (RuntimeException e) {
            return v == null ? "" : v;
        }
    }

    /** Pure: the share of a side's passes that found a team-mate ("84%"); "" without its figures. */
    static String passes(Map<String, String> m) {
        int ok = number(m.get("accuratePasses")), all = number(m.get("totalPasses"));
        return all > 0 ? Math.round(ok * 100f / all) + "%" : "";
    }

    private static int number(String v) {
        try {
            return v == null ? 0 : (int) Float.parseFloat(v.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Pure: a side's last five as their letters, oldest first ("WWDLW"); "" without them. */
    static String form(List<String[]> last) {
        StringBuilder b = new StringBuilder();
        for (String[] g : last) {
            if (g.length == 0 || g[0] == null || g[0].isEmpty()) continue;
            if (b.length() < 5) b.append(Character.toUpperCase(g[0].charAt(0)));
        }
        return b.toString();
    }

    /** Pure: a side's place in the table and its points ("2nd · 61 pts"); "" when it isn't in it. */
    static String place(List<String[]> table, String teamId) {
        for (String[] t : table) {
            if (teamId == null || teamId.isEmpty() || !teamId.equals(t[0])) continue;
            int rank = number(t[2]);
            if (rank <= 0) return "";
            return ordinal(rank) + " · " + (t[3] == null || t[3].isEmpty() ? "0" : t[3]) + " pts";
        }
        return "";
    }

    /** Pure: 1st, 2nd, 3rd, 4th, 11th, 21st. */
    static String ordinal(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    /**
     * Pure: the player of the match so far, from both line-ups (goals count most, then his live rating): "Saka (ARS) ·
     * 2 goals"; null until someone has done enough (a goal, or a rating of 7.8 or more).
     */
    static String[] star(Espn.Summary s, String homeCode, String awayCode) {
        double best = 0;
        String pick = null;
        for (int side = 0; side < 2; side++) {
            for (String[] p : side == 0 ? s.homeLineup : s.awayLineup) {
                if (p.length <= Espn.P_RATING) continue;
                int goals = number(p[Espn.P_GOALS]);
                double rating = 0;
                try {
                    rating = p[Espn.P_RATING] == null || p[Espn.P_RATING].isEmpty() ? 0 : Double.parseDouble(p[Espn.P_RATING]);
                } catch (NumberFormatException ignored) {
                    // no rating
                }
                if (goals == 0 && rating < 7.8) continue;
                double score = goals * 10 + rating;
                if (score <= best) continue;
                best = score;
                String name = p[Espn.P_SHORT] != null && !p[Espn.P_SHORT].isEmpty() ? p[Espn.P_SHORT] : p[Espn.P_NAME];
                pick = name + " (" + (side == 0 ? homeCode : awayCode) + ")"
                        + (goals > 0 ? " · " + goals + (goals == 1 ? " goal" : " goals") : " · rated " + String.format(Locale.UK, "%.1f", rating));
            }
        }
        return pick == null ? null : new String[]{"Player of the match", pick, "", "one"};
    }

    private String lastShownId;

    /** Being moved and sized with no game on: a made-up game shows, to place. */
    private boolean preview;
    /** Switched off by hand (boardShown): drawn no more, its clock and score going on underneath. */
    private boolean hidden;

    void hidden(boolean on) {
        hidden = on;
        draw(System.currentTimeMillis());
    }

    void preview(boolean on) {
        preview = on;
        draw(System.currentTimeMillis());
    }

    private static OverlayBug.Look sample() {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "HOM";
        l.away = "AWY";
        l.score = "1–0";
        l.clock = "45:00";
        l.live = true;
        l.tag = "PL";
        l.homeColour = 0xFFE5333B;
        l.awayColour = 0xFF4F8BFF;
        return l;
    }

    /** The board's look for a game as the screen shows it; clock as OnScreen gives it (null: not running). */
    static OverlayBug.Look look(Game g, String[] clock) {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = code(g.home, g.homeName);
        l.away = code(g.away, g.awayName);
        l.tag = g.league == null ? "" : g.league;
        l.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        l.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        if (g.state == Game.PRE) {
            l.score = "v";
            l.clock = g.kickoff > 0 ? java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
            return l;
        }
        l.score = g.homeScore + "–" + g.awayScore;
        if (g.state == Game.POST) {
            l.clock = "FT";
        } else if (g.halftime) {
            l.clock = "HT";
        } else if (clock != null) {
            l.live = true;
            l.clock = clock[0];
            l.added = clock[1];
        } else {
            l.live = true;
            l.clock = LiveClock.words(g, System.currentTimeMillis());
        }
        // In added time every look's score comes alive (decided here, where the clock's words are made).
        l.stoppage = stoppage(g, l.clock);
        return l;
    }

    /** "EVE" from ESPN's abbreviation, else the name's first three letters. */
    static String code(String abbr, String name) {
        if (abbr != null && !abbr.isEmpty() && abbr.length() <= 4) return abbr.toUpperCase(Locale.ROOT);
        String n = name == null ? "" : name.replaceAll("[^\\p{L}]", "");
        return (n.length() > 3 ? n.substring(0, 3) : n).toUpperCase(Locale.ROOT);
    }

    /** F1's tower read off the picture (ScoreOverlay): its lap and laps, or its time left, at the moment at. */
    void f1Screen(int lap, int laps, int left, long at) {
        f1Lap = lap;
        f1Laps = laps;
        f1Left = left;
        f1At = at;
    }

    private int f1Lap = -1, f1Laps = -1, f1Left = -1;
    private long f1At;

    private OverlayBug.Look f1Look(F1Live.Session s, long at) {
        OverlayBug.Look l = new OverlayBug.Look();
        l.f1 = true;
        l.live = true;
        l.label = F1Live.label(s.abbr);
        String[] corner = F1Live.corner(s, at);
        l.cornerA = corner[0];
        l.cornerB = corner[1];
        // What the tower itself shows, read within the last 20 seconds: its lap of its laps, its time left going down.
        long now = System.currentTimeMillis();
        if (now - f1At < 20_000L) {
            if (f1Lap > 0 && s.race()) {
                l.cornerA = "LAP";
                l.cornerB = f1Lap + (f1Laps > 0 ? "/" + f1Laps : "");
            } else if (f1Left >= 0 && !s.race()) {
                long left = Math.max(0, f1Left * 1000L - (now - f1At));
                l.cornerB = String.format(Locale.UK, "%02d:%02d", left / 60_000, left / 1000 % 60);
            }
        }
        for (int i = 0; i < Math.min(3, s.top.size()); i++) {
            String code = F1Live.code(s.top.get(i));
            l.codes.add(code);
            l.colours.add(F1.colour(f1Teams.get(code)));
        }
        return l;
    }

    // ---- alerts --------------------------------------------------------------------------------------------------

    @Override public void onGoalElsewhere(Events.Alert a) {
        // Shown as it goes up (onShown), like every other alert.
    }

    @Override public void onShown(Events.Alert a) {
        if (!running || a == null || !shows(c, group(a.kind))) return;
        // Over a film or another app, the alert pops up instead (Popups); only the live player's board drops it.
        if (!(src instanceof ScoreOverlay) && com.gathin.tvhomephone.sports.Popups.wants(c, a)) return;
        if (small) return;
        Drop d = drop(a);
        Game g = a.game;
        d.own = g != null && g.id != null && g.id.equals(lastId) && mode == FOOTBALL_MODE;
        // The line-ups, while the board is up: as the TV's card beside it.
        if (d.lineups && a.summary != null && g != null && boardUp() && !(src instanceof FloatService)) {
            lineupsCard(a);
            return;
        }
        if ("goal".equals(a.kind) || "red".equals(a.kind)) {
            String[] who = player(a);
            if ("goal".equals(a.kind)) {
                d.key = a.key;
                if (d.key != null && !captioned.add(d.key)) return;
            }
            // The board's own game: its scorer and minute (the board says the clubs and the score).
            if (d.own && who != null) d.line = who[1] + " " + who[2] + kindWords(who[4]);
            if (d.own) {
                d.homeCrest = bug.homeCrest;
                d.awayCrest = bug.awayCrest;
            }
            enrich(d, g, who == null ? null : who[1], who == null ? null : "home".equals(who[3]) ? g.homeName : g.awayName);
            return;
        }
        if (d.own) {
            d.homeCrest = bug.homeCrest;
            d.awayCrest = bug.awayCrest;
            drop(d);
            return;
        }
        enrich(d, g, null, null);
    }

    /** Pure: what a goal's (or a red card's) alert is about: its entry in the game's goals or reds, {key, player, minute, side, kind}. */
    static String[] player(Events.Alert a) {
        if (a == null || a.game == null || a.key == null) return null;
        for (String[] x : "red".equals(a.kind) ? a.game.reds : a.game.goals) if (a.key.equals(x[0])) return x;
        return null;
    }

    /** Pure: a goal's kind as words after its minute (" (pen)", " (og)"). */
    static String kindWords(String kind) {
        return "pen".equals(kind) ? " (pen)" : "og".equals(kind) ? " (og)" : "";
    }

    /**
     * The alert's pictures fetched off the main thread (the player's photo, another game's crests), then it slides out;
     * after PHOTO_WAIT_MS it goes without what hasn't come.
     */
    private void enrich(Drop d, Game g, String who, String club) {
        boolean crests = !d.own && g != null && (d.homeCrest == null || d.awayCrest == null);
        if (who == null && !crests) {
            drop(d);
            return;
        }
        boolean[] done = {false};
        Runnable go = () -> {
            if (done[0]) return;
            done[0] = true;
            drop(d);
        };
        h.postDelayed(go, PHOTO_WAIT_MS);
        int px = Math.round(28 * c.getResources().getDisplayMetrics().density * 2);
        App.io.execute(() -> {
            Bitmap photo = null, home = null, away = null;
            try {
                if (who != null) photo = PlayerPhotos.get(c, who, club, null);
                if (crests) {
                    home = LiveGames.picture(c, LiveGames.homeCrest(g), px);
                    away = LiveGames.picture(c, LiveGames.awayCrest(g), px);
                }
            } catch (RuntimeException ignored) {
                // without them
            }
            Bitmap p = photo, hc = home, ac = away;
            App.main.post(() -> {
                if (done[0]) return;
                d.photo = p;
                if (hc != null) d.homeCrest = hc;
                if (ac != null) d.awayCrest = ac;
                h.removeCallbacks(go);
                go.run();
            });
        });
    }

    /**
     * A goal on the board for side: once its band has gone and a little more, its scorer captioned beside it from the
     * scores' own goals (who, when, a penalty or own goal), unless its alert has captioned it already.
     */
    private void caption(Game g, int side) {
        String[] goal = lastGoal(g, side);
        if (goal == null || captioned.contains(goal[0])) return;
        long wait = bug.bandLeft(System.currentTimeMillis()) + 2_000L;
        Game at = g;
        h.postDelayed(() -> {
            if (!running || small || !captioned.add(goal[0])) return;
            Drop d = goalDrop(at, goal);
            d.homeCrest = bug.homeCrest;
            d.awayCrest = bug.awayCrest;
            enrich(d, at, goal[1], "home".equals(goal[3]) ? at.homeName : at.awayName);
        }, wait);
    }

    /** Pure: the latest goal for a side in a game's goals ({key, player, minute, side, kind}); null when none. */
    static String[] lastGoal(Game g, int side) {
        String want = side == 1 ? "home" : "away";
        String[] last = null;
        for (String[] x : g.goals) if (x.length > 3 && want.equals(x[3])) last = x;
        return last;
    }

    /** Pure: the board's own caption for one of its game's goals: GOAL, the scorer and his minute, his side's colour. */
    static Drop goalDrop(Game g, String[] goal) {
        Drop d = new Drop();
        d.chip = chip("goal");
        d.colour = colour("goal");
        d.own = true;
        d.key = goal[0];
        d.side = "home".equals(goal[3]) ? 1 : 2;
        d.title = g.homeName + " " + g.homeScore + "–" + g.awayScore + " " + g.awayName;
        d.line = goal[1] + " " + goal[2] + kindWords(goal.length > 4 ? goal[4] : "");
        d.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        return d;
    }

    /**
     * The line-ups as the TV's card beside the board: both elevens with their photos fetched first (off the main
     * thread, a few seconds at most), the alerts and stats waiting while it shows.
     */
    private void lineupsCard(Events.Alert a) {
        if (dropping || lineups.isOut()) {
            // After what is out now: queued as a drop that turns into the card when its turn comes.
            Drop d = drop(a);
            d.own = true;
            drops.addLast(d);
            return;
        }
        dropping = true;
        Game g = a.game;
        Espn.Summary sum = a.summary;
        float dp = c.getResources().getDisplayMetrics().density;
        int px = Math.round(28 * dp * 2);
        App.io.execute(() -> {
            BoardLineups.Data data = lineupsData(c, g, sum, px);
            App.main.post(() -> showLineups(data));
        });
    }

    /** Everything the card shows: each side's eleven in its lines with their photos, crests, kits and shapes. */
    static BoardLineups.Data lineupsData(Context c, Game g, Espn.Summary sum, int px) {
        BoardLineups.Data d = new BoardLineups.Data();
        int[] kits = com.gathin.tvhomephone.sports.Glyphs.kits(sum.homeKit, sum.homeKitAlt, g.homeColor, sum.awayKit, sum.awayKitAlt, g.awayColor);
        d.home.name = g.homeName == null ? "" : g.homeName;
        d.away.name = g.awayName == null ? "" : g.awayName;
        d.home.formation = sum.homeFormation;
        d.away.formation = sum.awayFormation;
        d.home.kit = kits[0];
        d.away.kit = kits[1];
        d.home.rows.addAll(BoardLineups.rows(sum.homeFormation, sum.homeLineup));
        d.away.rows.addAll(BoardLineups.rows(sum.awayFormation, sum.awayLineup));
        d.competition = g.leagueName == null ? "" : g.leagueName;
        try {
            d.home.crest = LiveGames.picture(c, LiveGames.homeCrest(g), px);
            d.away.crest = LiveGames.picture(c, LiveGames.awayCrest(g), px);
            d.leagueLogo = LiveGames.picture(c, com.gathin.tvhomephone.sports.Competitions.logo(g), px);
        } catch (RuntimeException ignored) {
            // without them
        }
        // Each starter's photo: a few at a time, eight seconds at most for them all.
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Callable<Void>> jobs = new ArrayList<>();
        for (int side = 0; side < 2; side++) {
            BoardLineups.Team t = side == 0 ? d.home : d.away;
            List<String[]> lineup = side == 0 ? sum.homeLineup : sum.awayLineup;
            List<String[]> starters = new ArrayList<>();
            for (String[] p : lineup) if ("1".equals(p[Espn.P_STARTER])) starters.add(p);
            for (List<BoardLineups.Player> row : t.rows) {
                for (BoardLineups.Player pl : row) {
                    String[] p = null;
                    for (String[] x : starters) if (pl.number.equals(x[Espn.P_NUMBER]) && BoardLineups.player(x).last.equals(pl.last)) p = x;
                    if (p == null) continue;
                    String name = p[Espn.P_NAME], id = p.length > Espn.P_ID ? p[Espn.P_ID] : null;
                    jobs.add(() -> {
                        pl.photo = PlayerPhotos.get(c, name, t.name, id);
                        return null;
                    });
                }
            }
        }
        try {
            pool.invokeAll(jobs, 8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            // with what has come
        }
        pool.shutdownNow();
        return d;
    }

    /** The card in line with the board, top to top: on its right, else its left, else under it. */
    private void showLineups(BoardLineups.Data data) {
        if (!running || small || !boardUp()) {
            dropping = false;
            h.post(nextDrop);
            return;
        }
        float dp = c.getResources().getDisplayMetrics().density;
        int bh = boardH(), bw = boardW(), gap = Math.round(4 * dp);
        int ch = Math.max(Math.round(84 * dp), Math.min(Math.round(150 * dp), Math.round(unit() * 4.2f)));
        int cw = BoardLineups.widthFor(ch);
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) lineups.getLayoutParams();
        if (roomRight >= cw + gap) {
            l.leftMargin = boardAt + bw + gap;
            l.topMargin = head;
        } else if (sideLeft && boardAt >= cw + gap) {
            l.leftMargin = boardAt - gap - cw;
            l.topMargin = head;
        } else {
            // Under it, as wide as there is room for.
            cw = Math.min(cw, bw + roomRight);
            ch = Math.round(cw / 2.95f);
            l.leftMargin = boardAt;
            l.topMargin = head + bh + gap;
        }
        l.width = cw;
        l.height = ch;
        lineups.setLayoutParams(l);
        if (changed != null) changed.run();
        stats.fold(null);
        lineups.show(data, () -> {
            dropping = false;
            h.post(nextDrop);
        });
    }

    /**
     * Pure: an alert as its drop: the kind's chip, the title without its "GOAL · " head, the first line (the line-ups:
     * both, one for each club), and the game's clubs' colours with the side it is about.
     */
    static Drop drop(Events.Alert a) {
        Drop d = new Drop();
        d.alert = a;
        d.chip = chip(a.kind);
        d.colour = colour(a.kind);
        String t = a.title == null ? "" : a.title;
        int dot = t.indexOf(" · ");
        d.title = dot > 0 && dot < 24 && t.substring(0, dot).equals(t.substring(0, dot).toUpperCase(Locale.ROOT)) ? t.substring(dot + 3) : t;
        String[] lines = a.text == null ? new String[]{""} : a.text.split("\n");
        d.line = lines.length > 0 ? lines[0] : "";
        d.lineups = a.key != null && a.key.startsWith("L|");
        if (d.lineups) {
            d.chip = "LINE-UPS";
            d.line2 = lines.length > 1 ? lines[1] : "";
        }
        Game g = a.game;
        if (g != null) {
            d.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
            d.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
            d.side = side(a, g);
        }
        return d;
    }

    /** Pure: the side an alert is about: a goal's scorers ("… for Everton"), a red card's club; 0 for both or neither. */
    static int side(Events.Alert a, Game g) {
        String about = "goal".equals(a.kind) && a.text != null ? a.text.split("\n")[0] : "red".equals(a.kind) ? a.title : null;
        if (about == null) return 0;
        String lead = "goal".equals(a.kind) ? " for " : "· ";
        if (g.homeName != null && !g.homeName.isEmpty() && about.endsWith(lead + g.homeName)) return 1;
        if (g.awayName != null && !g.awayName.isEmpty() && about.endsWith(lead + g.awayName)) return 2;
        return 0;
    }

    /** Whether Homefield's own player has its scoreboard up (it drops the alerts itself). */
    public static boolean playerUp() {
        return ScoreOverlay.up;
    }

    public static String chip(String kind) {
        switch (kind == null ? "" : kind) {
            case "goal": return "GOAL";
            case "red": return "RED CARD";
            case "var": return "VAR";
            case "ht": return "HALF TIME";
            case "ft": return "FULL TIME";
            case "kickoff": return "KICK-OFF";
            case "drama": return "LATE DRAMA";
            case "table": return "LIVE TABLE";
            case "milestone": return "MILESTONE";
            case "f1live": return "F1 · LIGHTS OUT";
            case "f1grid": return "F1 · GRID";
            case "f1result": return "F1 · RESULT";
            case "weather": return "WEATHER";
            case "reminder": return "STARTS SOON";
            case "news": return "BREAKING NEWS";
            default: return kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        }
    }

    public static int colour(String kind) {
        switch (kind == null ? "" : kind) {
            case "news": return 0xFFFFC21A;
            case "goal": return 0xFF3DDC84;
            case "red": return 0xFFFF5A5F;
            case "var": case "drama": return 0xFFFFB547;
            case "weather": return 0xFF8FC3FF;
            default: return kind != null && kind.startsWith("f1") ? 0xFFE10600 : PLAIN;
        }
    }

    /** The colour of the kinds with none of their own (half time, the line-ups, the table…): the board's block instead. */
    public static final int PLAIN = 0xFFDDE3FF;

    private void drop(Drop d) {
        if (small) return;
        drops.addLast(d);
        while (drops.size() > 4) drops.removeFirst();
        if (!dropping) next();
    }

    private void next() {
        h.removeCallbacks(nextDrop);
        if (small || drops.isEmpty()) {
            drops.clear();
            dropping = false;
            return;
        }
        dropping = true;
        // The stats back in beside the board first, the alert coming out in their place.
        if (stats.isOut()) {
            stats.fold(() -> h.post(nextDrop));
            return;
        }
        // A goal on the board: the alert once its band has gone, as broadcasters caption a goal after showing it.
        long wait = bug.getVisibility() == View.VISIBLE ? bug.bandLeft(System.currentTimeMillis()) : 0;
        if (wait > 0) {
            h.postDelayed(nextDrop, wait);
            return;
        }
        Drop d = drops.pollFirst();
        if (d.lineups && d.alert != null && d.alert.summary != null && d.alert.game != null && boardUp() && !(src instanceof FloatService)) {
            dropping = false;
            lineupsCard(d.alert);
            return;
        }
        if (changed != null) changed.run();
        boolean onBoard = boardUp();
        int bh = boardH();
        // On its own (no board up): where the board would be, round at both ends.
        strip.place(bug.stripLook(), unit(), onBoard ? nearTuck() : 0, onBoard && sideLeft, d.photo != null ? head : 0);
        strip.alone(!onBoard);
        int w = strip.widthFor(d, bh, onBoard ? sideMax : Math.max(Math.round(unit() * 6), sideMax - tuck));
        beside(strip, w, true);
        if (d.photo == null) {
            // No one to stand up out of it: the strip alone, as high as the board.
            FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) strip.getLayoutParams();
            l.height = bh;
            l.topMargin = head;
            strip.setLayoutParams(l);
        }
        strip.show(d);
        h.postDelayed(hide, strip.showFor(DROP_MS));
    }

    private void hideDrop() {
        h.removeCallbacks(hide);
        if (!strip.isOut()) {
            next();
            return;
        }
        strip.fold(this::next);
    }

    /** For the tests and the previews: an alert dropped now. */
    void show(Drop d) {
        drop(d);
    }

    List<Drop> waiting() {
        return new ArrayList<>(drops);
    }
}
