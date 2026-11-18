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

        /** The channel's own game as the picture shows it, each time the board draws (the classic pop-up follows it). */
        default void shown(Game g, long screenNow) {}

        /** A goal of the channel's own game reached the picture: 1 home, 2 away. */
        default void goalShown(Game g, int side) {}

        /** The player's layout, for the 3D kits' hidden page and a goal's shirt turning over the picture; null: none. */
        default FrameLayout kitRoot() { return null; }
    }

    static final String FOOTBALL = "football", SPORT = "sport", NEWS = "news";
    private static final long READ_MS = 15_000L, DROP_MS = 7_000L;
    /** Full time on the screen: up this long (its alert, its stats), then away; never longer than the most, an alert or not. */
    static final long FT_SHOWN_MS = 50_000L, FT_MOST_MS = 60_000L;
    /** Half time: shown this long for its alert, or this long at most whatever is out of it, then away till the second half. */
    static final long HT_SHOWN_MS = 25_000L, HT_MOST_MS = 60_000L;
    /** A goal's scorer's photo is waited for this long before the caption goes without it. */
    private static final long PHOTO_WAIT_MS = 2_500L;
    /** The match summary (stats, facts, the added time put up) is read again this often, and near the ends of halves this often. */
    static final long STATS_READ_MS = 120_000L, ENDS_READ_MS = 30_000L;
    /** The first of the stats' moments: this long before kick-off at most. */
    static final long PRE_STATS_MS = 10 * 60_000L;
    /** During a penalty shootout the game is read this often (each kick in step with the picture). */
    static final long PENS_READ_MS = 10_000L;
    /** The shootout's strip stays this long after the game ends on the screen. */
    static final long PENS_STAY_MS = 60_000L;
    private static final Pattern F1_CHANNEL = Pattern.compile("(?i)\\bf1\\b|formula ?(1|one)");

    // ---- the switches -------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("score_overlay", Context.MODE_PRIVATE);
    }

    /** The scoreboard over the player's picture (on until switched off). */
    static boolean on(Context c) { return prefs(c).getBoolean("on", true); }

    static void setOn(Context c, boolean v) { prefs(c).edit().putBoolean("on", v).apply(); }

    /**
     * Up by hand (the player's options): the scoreboard and the mark over the logo over the picture now, whatever is
     * found on the channel, until switched off (twelve hours at most); the board for the game chosen ("": the channel's
     * own, NO_GAME: none, the mark alone).
     */
    static final long HAND_MS = 12 * 3_600_000L;
    static final String NO_GAME = "-";

    static boolean handOn(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("hand", false) && System.currentTimeMillis() < p.getLong("hand_until", 0);
    }

    static String handGame(Context c) {
        return prefs(c).getString("hand_game", "");
    }

    static String handLabel(Context c) {
        return prefs(c).getString("hand_label", "");
    }

    static void setHand(Context c, boolean on, String gameId, String label) {
        prefs(c).edit().putBoolean("hand", on).putString("hand_game", gameId == null ? "" : gameId)
                .putString("hand_label", label == null ? "" : label)
                .putLong("hand_until", on ? System.currentTimeMillis() + HAND_MS : 0).apply();
    }

    /** Settings' "Scoreboard over matches" as chosen for when it is on: "auto" or "ask" ("" until chosen: OverMatches). */
    static String overMatches(Context c) { return prefs(c).getString("over_matches", ""); }

    static void setOverMatches(Context c, String v) { prefs(c).edit().putString("over_matches", v).apply(); }

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

    /** What is on the Whitebrook Road Sports wordmark: "" (from the game) or one chosen ("premier league", "f1"). */
    static String logoWhat(Context c) {
        return prefs(c).getString("what", "");
    }

    static void setLogoWhat(Context c, String what) {
        prefs(c).edit().putString("what", what == null ? "" : what).apply();
    }

    /** Pure: what is on, the one chosen or from the board's competition (its name) and whether F1 is on it. */
    static String whatFor(String chosen, String compName, boolean f1) {
        if (chosen != null && !chosen.isEmpty()) return chosen;
        if (f1) return "f1";
        String n = compName == null ? "" : compName.toLowerCase(java.util.Locale.ROOT);
        return n.contains("premier league") && !n.contains("scottish") ? "premier league" : "football";
    }

    /** The family's name alone on the logo's cover (WHITEBROOK ROAD), or with SPORTS (as it was). */
    static boolean logoNameOnly(Context c) {
        return prefs(c).getBoolean("name_only", false);
    }

    static void setLogoNameOnly(Context c, boolean on) {
        prefs(c).edit().putBoolean("name_only", on).apply();
    }

    /** See-through: Whitebrook Road Sports and the styles over the picture itself, the channel's logo filled in (LogoShape). */
    static boolean logoSeeThrough(Context c) {
        return prefs(c).getBoolean("see_through", false);
    }

    static void setLogoSeeThrough(Context c, boolean on) {
        prefs(c).edit().putBoolean("see_through", on).apply();
    }

    /** Which of the 54 Whitebrook Road styles the cover shows in that look. */
    static int logoStyle(Context c) {
        return BrandStyles.clamp(prefs(c).getInt("brand_style", 0));
    }

    static void setLogoStyle(Context c, int n) {
        prefs(c).edit().putInt("brand_style", BrandStyles.clamp(n)).apply();
    }

    /** The line-ups on screen (out of the board, before kick-off): on unless switched off. */
    /** Back over the player: the line-ups closed if they are out (true), so the press goes no further. */
    boolean closeLineups() {
        if (!lineups.isOut()) return false;
        lineups.close();
        return true;
    }

    static boolean lineupsOn(Context c) {
        return prefs(c).getBoolean("lineups", true);
    }

    static void setLineupsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("lineups", on).apply();
    }

    /** FotMob's figures added to the stats (expected goals, big chances, ratings, momentum) and its players' pictures. */
    public static boolean fotmobOn(Context c) {
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
     * The ad-break screen: when the channel goes to its adverts around a game (its logo gone: the build-up, half time,
     * after the game), the widgets, the line-ups and the fans' posts over the adverts (AdBreakView), the channel playing
     * on; at half time its half-time widgets too (the quiz, fun facts, prayer times). On until switched off.
     */
    static boolean adBreak(Context c) { return prefs(c).getBoolean("ad_break", true); }

    static void setAdBreak(Context c, boolean v) { prefs(c).edit().putBoolean("ad_break", v).apply(); }

    /** Your logo over the channel's popping out while the channel's is gone (its adverts) and back with it: on unless off. */
    static boolean logoAway(Context c) { return prefs(c).getBoolean("logo_away", true); }

    /** The board away in the channel's replays (its own scoreboard gone), back for live play: on unless switched off. */
    static boolean awayInReplays(Context c) { return prefs(c).getBoolean("replays_away", true); }

    static void setAwayInReplays(Context c, boolean v) { prefs(c).edit().putBoolean("replays_away", v).apply(); }

    /** What grows out of the channel's own scoreboard covered where it is, in the board's look: on unless switched off. */
    static boolean coverPopOuts(Context c) { return prefs(c).getBoolean("pop_outs", true); }

    static void setCoverPopOuts(Context c, boolean v) { prefs(c).edit().putBoolean("pop_outs", v).apply(); }

    static void setLogoAway(Context c, boolean v) { prefs(c).edit().putBoolean("logo_away", v).apply(); }

    /** The minutes added stay out beside the board until the half is over (over the channel's own): on unless switched off. */
    static boolean addedStays(Context c) { return prefs(c).getBoolean("added_stays", true); }

    static void setAddedStays(Context c, boolean v) { prefs(c).edit().putBoolean("added_stays", v).apply(); }

    /**
     * Stride's "Sponsored by" strap under the alerts (the family's made-up sponsor): off unless switched on (taken off at
     * the family's ask in 4.98, under a new key so it is off whatever the old one held).
     */
    static boolean sponsored(Context c) { return prefs(c).getBoolean("sponsor_on", false); }

    static void setSponsored(Context c, boolean v) { prefs(c).edit().putBoolean("sponsor_on", v).apply(); }

    /** The shot map beside the board at half time and full time: on unless switched off. */
    static boolean shotMap(Context c) { return prefs(c).getBoolean("shot_map", true); }

    static void setShotMap(Context c, boolean v) { prefs(c).edit().putBoolean("shot_map", v).apply(); }

    /** The referee's name and country beside the board at kick-off: on unless switched off. */
    static boolean referee(Context c) { return prefs(c).getBoolean("referee", true); }

    static void setReferee(Context c, boolean v) { prefs(c).edit().putBoolean("referee", v).apply(); }

    /** The penalty card: a penalty given in the game, out of the board until its kick (on unless switched off). */
    static boolean penaltyCard(Context c) { return prefs(c).getBoolean("penalty_card", true); }

    static void setPenaltyCard(Context c, boolean v) { prefs(c).edit().putBoolean("penalty_card", v).apply(); }

    /** VAR explained: the check out of the board, then its decision with a WHY under it: on unless switched off. */
    static boolean varExplained(Context c) { return prefs(c).getBoolean("var_explained", true); }

    static void setVarExplained(Context c, boolean v) { prefs(c).edit().putBoolean("var_explained", v).apply(); }

    /** How far behind the other apps' pictures run (their streams' delay), in seconds. */
    static int otherDelay(Context c) { return prefs(c).getInt("other_delay", 0); }

    static void setOtherDelay(Context c, int s) { prefs(c).edit().putInt("other_delay", LiveAlerts.clamp(s)).apply(); }

    /** A penalty shootout's kicks under the board. */
    static boolean pensOn(Context c) { return prefs(c).getBoolean("pens", true); }

    static void setPensOn(Context c, boolean v) { prefs(c).edit().putBoolean("pens", v).apply(); }

    /** The milestones on the pop-outs: a goal's place in the season, a yellow card's ban. */
    static boolean milestonesOn(Context c) { return prefs(c).getBoolean("milestones", true); }

    static void setMilestonesOn(Context c, boolean v) { prefs(c).edit().putBoolean("milestones", v).apply(); }

    /** One of the board's stats (BoardLab.KEYS, by its key, as the TV's) shown: on unless switched off ("": no switch). */
    static boolean statOn(Context c, String key) {
        return key == null || key.isEmpty() || prefs(c).getBoolean("stat_" + key, true);
    }

    static void setStatOn(Context c, String key, boolean on) { prefs(c).edit().putBoolean("stat_" + key, on).apply(); }

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
    /** Under it: a penalty shootout's kicks while one is on. */
    final BoardUnder under;
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
    /** Half time as the screen shows it: for which game, since when. */
    private String htFor = "";
    private long htAt;
    private long ftAt;
    /** The minute the board's clock shows (-1: none), for how often the summary is read. */
    private int boardMinute = -1;
    /** The board's clock in seconds into the game (in step with the picture; -1 none): a substitution waits for its minute. */
    private int boardSecs = -1;
    /** The game as last read, to read its summary again at once when the channel shows a substitution. */
    private Game lastGot;
    /** Until when the channel has just shown a substitution (its graphic): the data read often, one found out at once. */
    private long subsSeenUntil;
    private final Runnable subsLook = this::subsLook;
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
        /** A goal's caption: which of its side's goals it is (its after-goal pop-up's, Moments.restartKey); 0 for the rest. */
        int goalN;
        /** The scorer's (or the player's) photo, head and shoulders; both clubs' crests; each may be null. */
        Bitmap photo, homeCrest, awayCrest;
        Events.Alert alert;
        /** A substitution (or several at once): its OFF page, then its ON page, each player with picture and name. */
        SubsPage.Subs subs;
        /** The minutes added (an ADDED TIME alert): a big +N counting up to them as it comes out, MINUTES ADDED by it. */
        int addedMins;
        /** A milestone's tag at the end of its words ("3rd of the season", "One more and he's banned"), its kind (BoardDrop.TAG_*). */
        String tag = "";
        int tagKind;
        /** Its milestone, looked up off the main thread before it comes out: {tag, kind}, or null for none. */
        java.util.concurrent.Callable<Object[]> milestone;
        /** VAR explained: a check under way (its title, three dots beating beside it), out until its decision. */
        boolean varCheck;
        /** VAR explained: the decision's WHY, folding down under it (null for any other alert). */
        VarExplained.Why why;
        /** VAR explained: the game it is about. */
        String gameId;
        /** The penalty card: out until its kick is in (PENALTY_MAX_MS at most), turning over to each of turns in order. */
        boolean penalty;
        Drop[] turns;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            h.removeCallbacks(this);
            if (!running) return;
            draw(System.currentTimeMillis());
            h.postDelayed(this, 250);
        }
    };

    /** VAR explained's WHY under a decision. */
    private final VarWhy why;
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
        // VAR explained's WHY, folding down under a decision beside the board.
        why = new VarWhy(ctx);
        view.addView(why, new FrameLayout.LayoutParams(Math.round(280 * d), Math.round(60 * d)));
        lineups = new BoardLineups(ctx);
        view.addView(lineups, new FrameLayout.LayoutParams(Math.round(300 * d), Math.round(100 * d)));
        // Under the board, its top tucked under the board's foot.
        under = new BoardUnder(ctx);
        view.addView(under, new FrameLayout.LayoutParams(Math.round(200 * d), Math.round(20 * d)));
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

    /** The board's own height inside, in pixels (its 3D kits' frames are made a little taller). */
    private int kitInner() {
        android.view.ViewGroup.LayoutParams lp = bug.getLayoutParams();
        int h = bug.getHeight() > 0 ? bug.getHeight() : lp != null ? lp.height : 0;
        return Math.max(1, h);
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
        return boardH() / Math.max(1f, bug.tall());
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

    /**
     * The alerts and stats beside the board: as high as the board, in line with it whatever size it is set to, their
     * words a little smaller than its own (TEXT of its height), and never longer than SHORT of its heights (longer words
     * run along instead).
     */
    static final float TEXT = 0.86f, SHORT = 8f;

    /** Pure: the longest a pop-out beside a board bh high may be, within room. */
    static int shortRoom(int bh, int room) {
        return Math.max(bh * 3, Math.min(room, Math.round(bh * SHORT)));
    }

    /** What slides out (the stats, an alert) w wide, as high as the board and in line with it, its near end tucked under it. */
    private void beside(View v, int w, boolean withHead) {
        int bh = boardH(), bw = boardW();
        boolean onBoard = boardUp();
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) v.getLayoutParams();
        int left = !onBoard ? boardAt : sideLeft ? boardAt + tuck - sideGap - w : boardAt + bw - nearTuck() + nearGap();
        int height = bh + (withHead ? head : 0) + (v == strip ? strip.strap : 0), top = withHead ? 0 : head;
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
        testUntil = 0;
        addedUntil = 0;
        ScreenTime.clear(screen);
        LiveAlerts.removeListener(this);
        h.removeCallbacksAndMessages(null);
        drops.clear();
        standing = null;
        strip.gone();
        dropping = false;
        stats.gone();
        lineups.gone();
        under.gone();
        yellowsWaiting.clear();
        held.clear();
        varHeld.clear();
        PhoneKits.stop(src.kitRoot());
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
            under.gone();
            PhoneKits.stop(src.kitRoot());   // a goal's shirt would cover the small picture
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
        under.gone();
        yellowsWaiting.clear();
        held.clear();
        varHeld.clear();
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

    /** Something out of the board now (an alert, the stats, the line-ups, VAR's why, what is under it): the logo's extras wait. */
    boolean outNow() {
        return strip.isOut() || stats.isOut() || lineups.getVisibility() == View.VISIBLE || under.getVisibility() == View.VISIBLE
                || why.getVisibility() == View.VISIBLE;
    }

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
                String light = com.gathin.tvhomephone.sports.Competitions.lightLogo(g.slug);
                crests = new Bitmap[]{LiveGames.picture(c, LiveGames.homeCrest(g), px), LiveGames.picture(c, LiveGames.awayCrest(g), px),
                        LiveGames.picture(c, com.gathin.tvhomephone.sports.Competitions.logo(g), px),
                        light == null ? null : LiveGames.picture(c, light, px)};
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
                    lastGot = got;
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
                        if (pics.length > 3) bug.competitionLight(pics[3]);
                    }
                    compName = got.leagueName == null ? "" : got.leagueName;
                    // The game's competition: By competition draws its own look, and its logo with it; the retro looks name the clubs.
                    bug.competition(got.slug, got.leagueName != null && !got.leagueName.isEmpty() ? got.leagueName : got.league);
                    bug.names(got.homeName, got.awayName);
                    // the 3D kits: the look's two shirts made for the kits worn, a new goal's shirt made ahead of its moment
                    FrameLayout kitRoot = src.kitRoot();
                    if (style(c) == OverlayBug.Style.KITS) PhoneKits.board(c, got, bug, kitInner(), kitRoot);
                    PhoneKits.feed(c, got, kitRoot);
                    prefetchGoals(got);
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
                // A shootout's kicks read more often, each in step with the picture.
                h.postDelayed(read, got != null && inShootout(got) ? PENS_READ_MS : READ_MS);
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
                // Its goals' set, half time's face and the shootout's place are the game's own (BoardFx.pick).
                bug.game(g.id);
                if (channelGame) src.shown(g, now - src.behindMs(true));
                l = look(g, screen.boardClock(now));
                // From the half's last minute, the added time put up beside the clock (the channel's own when it shows it).
                if (l.live) l.added = addedBox(period(g, l.clock), minuteOfClock(l.clock), addedNow(), l.added);
                // The minutes added, once known for this half: out beside the board once as an alert, then in its tab.
                if (l.live && !l.added.isEmpty()) addedOut(g, l.added, period(g, l.clock));
                keepAdded(g, l);
                // The minutes added standing out beside the board for the rest of the half (their panel says them): no tab
                // of the board's own meanwhile, the panel tucked under its end as the other pop-outs are.
                l.added = boardAdded(l.added, standing != null && g.id != null && g.id.equals(standingGame));
                // Another game on the board, half time, full time: nothing after a goal waits any more (LivePlay).
                if (g.id == null || !g.id.equals(lastShownId) || g.halftime || g.state != Game.LIVE) play.clear();
                else play.var(VarExplained.checking(g.id, now));
                // A goal as the screen shows it: that side lit, then its scorer captioned once live play is back.
                if (g.id != null && g.id.equals(lastShownId)) {
                    int side = g.homeScore > lastHome && lastHome >= 0 ? 1 : g.awayScore > lastAway && lastAway >= 0 ? 2 : 0;
                    if (side > 0) {
                        play.goal(now);
                        labGoal(g, side, l.clock, lastHome, lastAway);
                        bug.goal(side);
                        com.gathin.broadcast.Sfx.goal(c, PlayerIntro.goalSound(c));
                        // the scorer's shirt out of the board and round to his name and number (its own switch)
                        PhoneKits.goal(c, g, side, bug, small ? null : src.kitRoot());
                        goalShownAt = now;
                        if (changed != null) changed.run();
                        caption(g, side);
                        if (channelGame) src.goalShown(g, side);
                    }
                }
                lastShownId = g.id;
                lastHome = g.homeScore;
                lastAway = g.awayScore;
                boardMinute = minuteOfClock(l.clock);
                boardSecs = com.gathin.broadcast.SubsTiming.clockSeconds(l.clock);
                releaseYellows(g, l.clock);
                // Where the picture is (its clock the channel's own while it is read off it): what waits for it comes now.
                if (g.id != null && g.id.equals(lastId)) {
                    picture = picture(g, l.clock, now, channelGame && screen.reading(now), screen.behind());
                    if (lastGot != null && g.id.equals(lastGot.id) && lastGot.state != Game.PRE) {
                        picture.scoresHome = lastGot.homeScore;
                        picture.scoresAway = lastGot.awayScore;
                    }
                    held.release(g.id, picture);
                    varHeld.release(g.id, picture);
                }
                // Full time on the screen: shown a little under a minute (its alert, its stats), then away.
                String id = g.id == null ? "" : g.id;
                if (g.state == Game.POST) {
                    if (!id.equals(ftFor)) {
                        ftFor = id;
                        ftAt = now;
                        if (changed != null) changed.run();
                    }
                    if (ftGone(now - ftAt, dropping || lineups.isOut() || under.mode() == BoardUnder.PENS || bug.pensShown() || mapView != null)) l = null;
                } else if (id.equals(ftFor)) {
                    ftFor = "";
                }
                // Half time on the screen: shown a little (its alert, the half's stats), then away until the second half
                // kicks off on the screen (the screen's own game, in step), whether it came by itself or was put up by hand.
                if (g.state != Game.POST && g.halftime) {
                    if (!id.equals(htFor)) {
                        htFor = id;
                        htAt = now;
                        if (changed != null) changed.run();
                    }
                    if (htGone(now - htAt, dropping || lineups.isOut() || stats.isOut() || mapView != null)) l = null;
                } else if (id.equals(htFor)) {
                    htFor = "";
                }
            }
        } else if (mode == F1_MODE && !f1Reads.isEmpty()) {
            long at = now - src.behindMs(false);
            Object[] pick = f1Reads.peekFirst();
            for (Object[] r : f1Reads) if ((Long) r[0] <= at) pick = r;
            l = f1Look((F1Live.Session) pick[1], at);
        }
        if (l == null && preview) l = sample();
        // A test from the player's options with nothing on the board: its made-up game, until what it put out is back in.
        if (l == null && testing(now)) {
            l = look(testGame, null);
            if (testGame != null) bug.game(testGame.id);
        }
        if (l != null && !l.f1 && now < addedUntil) testAdded(l, now);
        // A replay on the channel (its own scoreboard gone, BugCover): away the same way, never in its own goal moment.
        boolean replay = replayAway && l != null && !l.f1 && bug.bandLeft(now) <= 0;
        // A moment's pop-up (PlayerIntro: the kick-off, the restart after a goal, the second half) the same: the board is
        // never on the picture with it. Away in a goal's replays with the restart's pop-up still to come, it stays away until
        // that has been and gone, so it never comes back only to go again; then in with its swish.
        boolean popup = l != null && !l.f1 && bug.bandLeft(now) <= 0 && com.gathin.broadcast.Moments.boardAwayForPopup(
                momentUp.getAsBoolean(), restartNext.getAsBoolean(), bug.getVisibility() != View.VISIBLE);
        if (popup) swishBack = true;
        if (l == null || hidden || replay || popup) {
            // Away as a broadcaster's goes: what is out beside it back in, wiped back into its first block, closed to a line.
            under.away();
            hideMap();
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
            // back after a moment's pop-up: with the swish, as what slides out beside it comes
            if (fresh && swishBack) swishIn();
        }
        swishBack = false;
        stats.colours(l.homeColour, l.awayColour);
        mapHome = l.homeColour;
        mapAway = l.awayColour;
        if (mode == FOOTBALL_MODE && shown != null) {
            maybeStats(shown, now);
            labTick(shown, now);
            maybeShotMap(shown);
        }
        underNow(mode == FOOTBALL_MODE ? shown : null, l, now);
    }

    // ---- the shot map at half time and full time, the referee at kick-off ----------------------------------------------

    private String shotsFor = "";
    private List<com.gathin.broadcast.ShotMap.Shot> shots = new java.util.ArrayList<>();
    private int mapHome = 0xFFDDE3FF, mapAway = 0xFF9AA4C8;
    private final Set<String> mapShown = new java.util.HashSet<>(), refShown = new java.util.HashSet<>();
    private android.widget.ImageView mapView;
    private final Runnable mapGone = this::hideMap;

    /**
     * At half time and full time, once the stats beside the board have had their turn (or have none): the shot map
     * beside the board (under it where there is no room beside), the half's at half time, the game's at full time.
     */
    private void maybeShotMap(Game g) {
        if (small || preview || hidden || !shotMap(c) || g == null || g.id == null || !g.id.equals(shotsFor) || shots.isEmpty()) return;
        if (dropping || mapView != null || lineups.isOut() || stats.isOut() || bug.closing() || !boardUp()) return;
        String half = g.state == Game.POST ? "ft" : g.halftime ? "ht" : null;
        if (half == null || holdBreak) return;
        boolean statsDone = statsShown.contains(lastId + "|" + half) || statsPick(half).isEmpty();
        if (!statsDone || !mapShown.add(g.id + "|" + half)) return;
        if (mapShown.size() > 60) mapShown.clear();
        List<com.gathin.broadcast.ShotMap.Shot> these = com.gathin.broadcast.ShotMap.of(shots, "ht".equals(half));
        if (these.isEmpty()) return;
        int bh = boardH(), mh = Math.max(Math.round(bh * 5.2f), Math.round(150 * c.getResources().getDisplayMetrics().density));
        int mw = Math.round(mh * 1.4f);
        int roomRight = view.getWidth() - (boardAt + boardW() + sideGap), roomLeft = boardAt - sideGap;
        boolean left = sideLeft ? roomLeft >= mw : roomRight < mw && roomLeft >= mw;
        boolean beside = (left ? roomLeft : roomRight) >= mw;
        if (!beside && view.getHeight() - (head + bh + sideGap) < mh) return;
        int ground = bug.stripLook().ground | 0xFF000000;
        android.graphics.Bitmap map = com.gathin.broadcast.ShotMap.draw(these, mw, mh, (0xF0 << 24) | (ground & 0xFFFFFF),
                "ht".equals(half) ? "First half" : "Full time", code(g.home, g.homeName), mapHome, code(g.away, g.awayName), mapAway);
        android.widget.ImageView v = new android.widget.ImageView(c);
        v.setImageBitmap(map);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(mw, mh);
        lp.leftMargin = beside ? (left ? boardAt - sideGap - mw : boardAt + boardW() + sideGap) : Math.max(0, Math.min(boardAt, view.getWidth() - mw));
        lp.topMargin = beside ? head : head + bh + sideGap;
        view.addView(v, lp);
        mapView = v;
        if (changed != null) changed.run();
        v.setAlpha(0f);
        v.setTranslationX(left ? mw * 0.18f : -mw * 0.18f);
        v.animate().alpha(1f).translationX(0f).setDuration(380).start();
        if (PlayerIntro.swishOn(c)) com.gathin.broadcast.Sfx.play(c, com.gathin.broadcast.Sfx.SWISH, left ? -0.4f : 0.4f, 0.7f);
        h.postDelayed(mapGone, 12_000L);
    }

    /** The shot map away (its time up, or the board going). */
    private void hideMap() {
        h.removeCallbacks(mapGone);
        android.widget.ImageView v = mapView;
        mapView = null;
        if (v == null) return;
        v.animate().alpha(0f).setDuration(300).withEndAction(() -> view.removeView(v)).start();
    }

    // ---- the penalty card ---------------------------------------------------------------------------------------------

    /** A penalty's card out at most this long; each of its pages this long before it turns over to the next. */
    static final long PENALTY_MAX_MS = 180_000L, PENALTY_PAGE_MS = 6_000L;
    /** Penalties carded, "game|minute|side": each comes out once. */
    private static final java.util.Set<String> pensCarded = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    /** A penalty given and its kick not yet read: the summary read every few seconds until then (penaltyFor's). */
    private long penaltyUntil;
    private String penaltyFor;
    private Runnable penaltyTurn;

    /**
     * Off the main thread: the penalty given in the game whose kick is still to come, {its key ("" for none), its card's
     * pages when new (records and pictures looked up), else null}; null when the card is off.
     */
    private Object[] penaltyLook(Game g, Espn.Summary s, int state) {
        if (s == null || !penaltyCard(c) || state != Game.LIVE || small || !shows(c, FOOTBALL)) return null;
        String hn = g.homeFull != null && !g.homeFull.isEmpty() ? g.homeFull : g.homeName;
        String an = g.awayFull != null && !g.awayFull.isEmpty() ? g.awayFull : g.awayName;
        com.gathin.broadcast.Penalty.Given pen = com.gathin.broadcast.Penalty.pending(s.penaltyLines, hn, an);
        int side = com.gathin.broadcast.Penalty.side(pen, hn, an);
        if (pen == null || side == 0) return new Object[]{"", null};
        String key = g.id + "|" + pen.key();
        if (!pensCarded.add(key)) return new Object[]{key, null};
        if (pensCarded.size() > 60) pensCarded.clear();
        com.gathin.broadcast.Penalty.Records r = Espn.penaltyRecords(g.slug, g.id, side == 1);
        Drop[] pages = penaltyDrops(g, pen, side, r);
        String team = side == 1 ? g.homeName : g.awayName, other = side == 1 ? g.awayName : g.homeName;
        for (Drop p : pages) {
            if (p == pages[0]) continue;
            boolean taker = p.line.startsWith("Likely taker");
            p.photo = face(taker ? r.taker : r.keeper, "", taker ? team : other);
        }
        return new Object[]{key, pages};
    }

    /**
     * Pure: the penalty card's pages: PENALTY, the side it is for, who won it and who gave it away; then the likely
     * taker's record and the keeper's, each where ESPN has it.
     */
    static Drop[] penaltyDrops(Game g, com.gathin.broadcast.Penalty.Given pen, int side, com.gathin.broadcast.Penalty.Records r) {
        java.util.List<Drop> out = new java.util.ArrayList<>();
        String team = side == 1 ? g.homeName : g.awayName;
        out.add(penaltyPage(g, side, team == null ? "" : team, com.gathin.broadcast.Penalty.wonLine(pen)));
        // The board's own game: its pop-outs show their line, not their title, so each name is in its line.
        if (r != null && r.takerLine != null) {
            String who = com.gathin.broadcast.Penalty.surname(r.taker);
            out.add(penaltyPage(g, side, who, "Likely taker " + who + " · " + lower(r.takerLine)));
        }
        if (r != null && r.keeperLine != null) {
            String who = com.gathin.broadcast.Penalty.surname(r.keeper);
            out.add(penaltyPage(g, side == 1 ? 2 : 1, who, who + " in goal · " + lower(r.keeperLine)));
        }
        return out.toArray(new Drop[0]);
    }

    private static String lower(String s) {
        return s == null || s.isEmpty() ? "" : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static Drop penaltyPage(Game g, int side, String title, String line) {
        Drop d = new Drop();
        d.chip = "PENALTY";
        d.title = title;
        d.line = line == null ? "" : line;
        d.side = side;
        d.penalty = true;
        d.own = true;
        d.gameId = g.id;
        d.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        return d;
    }

    /** The penalty read (main thread): a new one's card out of the board; once its kick is in, the card goes. */
    private void penaltyRead(Game g, String key, Drop[] pages) {
        long now = System.currentTimeMillis();
        if (!key.isEmpty()) {
            penaltyFor = g.id;
            penaltyUntil = now + PENALTY_MAX_MS;
            if (pages != null && pages.length > 0) {
                Drop d = pages[0];
                d.turns = pages.length > 1 ? java.util.Arrays.copyOfRange(pages, 1, pages.length) : null;
                for (Drop p : pages) {
                    p.homeCrest = bug.homeCrest;
                    p.awayCrest = bug.awayCrest;
                }
                drop(d);
            }
            return;
        }
        if (g.id.equals(penaltyFor)) penaltyTaken(g.id);
    }

    /** The penalty card's first page as it last came out (it comes out again after a VAR moment that stepped it aside). */
    private Drop penaltyCard;

    /**
     * A VAR moment for the game while its penalty card is out: the card goes in for it, and comes out again after it
     * (until its kick), rather than the check waiting behind a card that stays out until the kick.
     */
    private void penaltyAside(String gameId) {
        Drop out = strip.isOut() ? strip.drop() : null;
        if (out == null || !out.penalty || gameId == null || !gameId.equals(out.gameId)) return;
        drops.addFirst(penaltyCard != null && gameId.equals(penaltyCard.gameId) ? penaltyCard : out);
        if (penaltyTurn != null) h.removeCallbacks(penaltyTurn);
        hideDrop();
    }

    /** VAR took the penalty away: its card (waiting to come out again) goes, and nothing waits for its kick. */
    private void penaltyGone(String gameId) {
        drops.removeIf(q -> q.penalty && gameId.equals(q.gameId));
        if (gameId.equals(penaltyFor)) {
            penaltyFor = null;
            penaltyUntil = 0;
        }
    }

    /** The kick is in (or a goal came first): the card goes, whether out or waiting its turn. */
    private void penaltyTaken(String gameId) {
        if (gameId.equals(penaltyFor)) {
            penaltyFor = null;
            penaltyUntil = 0;
        }
        drops.removeIf(q -> q.penalty && gameId.equals(q.gameId));
        Drop out = strip.isOut() ? strip.drop() : null;
        if (out != null && out.penalty && gameId.equals(out.gameId)) {
            if (penaltyTurn != null) h.removeCallbacks(penaltyTurn);
            hideDrop();
        }
    }

    /** The card turning over to its next page every PENALTY_PAGE_MS, while it is still the one out. */
    private void turnPenalty(Drop first, int i) {
        if (penaltyTurn != null) h.removeCallbacks(penaltyTurn);
        if (first.turns == null || i >= first.turns.length) return;
        penaltyTurn = () -> {
            Drop out = strip.isOut() ? strip.drop() : null;
            if (!running || out == null || !out.penalty || first.gameId == null || !first.gameId.equals(out.gameId)) return;
            strip.turnTo(first.turns[i]);
            turnPenalty(first, i + 1);
        };
        h.postDelayed(penaltyTurn, PENALTY_PAGE_MS);
    }

    /** At kick-off (its first ten minutes, once): the referee's name and country out beside the board as an alert. */
    private void refereeOut(Game g, FotMob.Match m) {
        if (!referee(c) || m.referee.isEmpty() || g.state != Game.LIVE || small || !shows(c, FOOTBALL)) return;
        long since = System.currentTimeMillis() - g.kickoff;
        if (since < 0 || since > 10 * 60_000L || !refShown.add(g.id)) return;
        if (refShown.size() > 60) refShown.clear();
        Drop d = refereeDrop(g, m.referee, m.refereeCountry);
        d.own = true;
        d.homeCrest = bug.homeCrest;
        d.awayCrest = bug.awayCrest;
        drop(d);
    }

    /** Pure: the referee's alert ("REFEREE", the name, the country under it). */
    static Drop refereeDrop(Game g, String name, String country) {
        Drop d = new Drop();
        d.chip = "REFEREE";
        d.title = name;
        d.line = country == null ? "" : country;
        d.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        return d;
    }

    /** Pure: whether half time has been shown long enough: 25 seconds, or a minute at most whatever is out of it. */
    static boolean htGone(long sinceHt, boolean busy) {
        return sinceHt > HT_MOST_MS || (sinceHt > HT_SHOWN_MS && !busy);
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
                want = new String[]{"Win chance", "Form", "League table", "Head to head", "Streak home", "Streak away"};
                least = 1;
                most = 5;
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

    /**
     * The game's stats sliding out beside it at one of its few moments (statsSlot). Never while an alert or the line-ups
     * are out, just after a goal, in the small window or while it is placed by hand; and never while the scores have a
     * goal the picture hasn't shown yet (its shot on target would give it away). What they say is as it stood at the
     * moment on the picture (statsNow): no total moves, no name comes, before the goal or chance behind it is seen.
     */
    private void maybeStats(Game g, long now) {
        if (small || preview || hidden || dropping || lineups.isOut() || stats.isOut() || bug.closing() || !boardUp()) return;
        if (!lastId.equals(statsFor) || now - goalShownAt < 90_000L || statsNow().isEmpty()) return;
        boolean pre = g.state == Game.PRE;
        if (!pre && screen.ahead(now)) return;
        String slot = statsSlot(pre, g.kickoff - now, g.state, g.halftime, boardMinute);
        String key = lastId + "|" + slot;
        if (slot == null || statsShown.contains(key)) return;
        // Half time's and full time's wait while the ad-break screen is up (shown once it has gone, still in their time).
        if (holdBreak && waitsForAdBreak(slot)) return;
        List<String[]> pick = statsPick(slot);
        if (pick.isEmpty()) return;
        if (statsShown.size() > 60) statsShown.clear();
        statsShown.add(key);
        showStats(pick);
    }

    // ---- the Scoreboard Lab's moments: the weather touch, the momentum wave, its stats (as the TV's) ------------------

    /** The Lab's moments shown, by "game|moment" (each once a game); the stats waiting for their turn, each {game, stat...}. */
    private final Set<String> labShown = new java.util.HashSet<>();
    private final List<String[]> labQueue = new ArrayList<>();
    /** The board's game and FotMob's match for it (head to head, runs, the referee), and the sky over each game's ground. */
    private String labFor = "";
    private FotMob.Match labMatch;
    private final Map<String, com.gathin.broadcast.BoardFx.Sky> labSky = new HashMap<>();
    /** The shots and the games whose first read has been seen (a big save from before the board came up is never news). */
    private final Set<String> labShots = new java.util.HashSet<>(), labGames = new java.util.HashSet<>();

    // ---- no spoilers: what the board says from the live data waits for the picture (AsSeen) ---------------------------

    /** Where the picture is now: the board's own game as it shows it, its clock, the channel's lag (each look at the board). */
    private com.gathin.broadcast.AsSeen.Picture picture = new com.gathin.broadcast.AsSeen.Picture();
    /** The game's stats and facts as read, a few minutes of them: a stats moment says them as they stood on the picture. */
    private final com.gathin.broadcast.AsSeen.Reads<Map<String, String[]>> statsReads = new com.gathin.broadcast.AsSeen.Reads<>();
    /** FotMob's reads for the Lab's moments, {momentum, the cards so far}, as they stood. */
    private final com.gathin.broadcast.AsSeen.Reads<Object[]> labReads = new com.gathin.broadcast.AsSeen.Reads<>();
    /** FotMob's shot map as read last (for a big save or a great finish once the picture has it), when each shot came, its home side. */
    private org.json.JSONArray labShotsNow;
    private final Map<String, Long> labFirstSeen = new HashMap<>();
    private int labHomeTeamId;
    /** What a read says at once (a penalty's card, VAR's check and decision), held until the picture reaches its read. */
    private final com.gathin.broadcast.AsSeen.Held held = new com.gathin.broadcast.AsSeen.Held(), varHeld = new com.gathin.broadcast.AsSeen.Held();

    /**
     * Pure: where the picture is for AsSeen: the board's game as it shows it (before kick-off, in play, half time, full
     * time), its half, its clock in step with the picture (seconds; none at a break), whether that clock is the channel's
     * own (read off it), how far the channel runs behind the scores, and the board's score.
     */
    static com.gathin.broadcast.AsSeen.Picture picture(Game g, String clock, long now, boolean known, long lagMs) {
        com.gathin.broadcast.AsSeen.Picture p = new com.gathin.broadcast.AsSeen.Picture();
        p.now = now;
        p.known = known;
        p.lagMs = lagMs;
        if (g == null) return p;
        p.state = g.state == Game.POST ? com.gathin.broadcast.AsSeen.OVER : g.state == Game.PRE ? com.gathin.broadcast.AsSeen.BEFORE
                : g.halftime ? com.gathin.broadcast.AsSeen.BREAK : com.gathin.broadcast.AsSeen.PLAY;
        p.period = g.state == Game.LIVE && g.period >= 5 ? g.period : period(g, clock);
        p.secs = p.state == com.gathin.broadcast.AsSeen.PLAY ? com.gathin.broadcast.SubsTiming.clockSeconds(clock) : -1;
        p.home = g.state == Game.PRE ? 0 : g.homeScore;
        p.away = g.state == Game.PRE ? 0 : g.awayScore;
        return p;
    }

    /** Pure: a read's stamp for AsSeen: when it came, the game as the scores had it then, its latest moment, its goals. */
    static com.gathin.broadcast.AsSeen.Stamp stamp(Game g, long at, int upTo, int home, int away) {
        int phase = g == null ? com.gathin.broadcast.AsSeen.PLAY : g.state == Game.POST ? com.gathin.broadcast.AsSeen.OVER
                : g.state == Game.PRE ? com.gathin.broadcast.AsSeen.BEFORE : g.halftime ? com.gathin.broadcast.AsSeen.BREAK
                : com.gathin.broadcast.AsSeen.PLAY;
        int period = g == null ? 0 : period(g, "");
        int h = g == null ? home : Math.max(home, g.state == Game.PRE ? 0 : g.homeScore);
        int a = g == null ? away : Math.max(away, g.state == Game.PRE ? 0 : g.awayScore);
        return new com.gathin.broadcast.AsSeen.Stamp(at, phase, period, upTo, h, a, true);
    }

    /** The minutes added put up in each half, as read (ESPN's commentary, FotMob's where it has none), a few minutes of them. */
    private final com.gathin.broadcast.AsSeen.Reads<int[]> addedReads = new com.gathin.broadcast.AsSeen.Reads<>();

    /**
     * The minutes added as they stood at the moment on the picture: part of the board's own clock, so by its own lag (the
     * channel's, as the board's clock is held), not the longest one; none put up until a read has been reached.
     */
    private int[] addedNow() {
        int[] a = addedReads.pick(picture.inStep());
        return a == null ? new int[]{-1, -1, -1, -1} : a;
    }

    /** Pure: the minutes added's stamp: the read's time and game, nothing else (they give no goal or chance away). */
    static com.gathin.broadcast.AsSeen.Stamp addedStamp(com.gathin.broadcast.AsSeen.Stamp st) {
        return new com.gathin.broadcast.AsSeen.Stamp(st.at, st.phase, st.period, -1, -1, -1);
    }

    /** The stats and facts as they stood at the moment on the picture (none until a read has been reached). */
    private Map<String, String[]> statsNow() {
        Map<String, String[]> m = statsReads.pick(picture);
        return m == null ? new HashMap<>() : m;
    }
    /** The board's game's goals as they came ({side}, {minute}), when it has been seen from 0-0 (a comeback needs them all). */
    private String labGoalsFor = "";
    private final List<Integer> labSides = new ArrayList<>(), labMinutes = new ArrayList<>();
    private boolean labFromStart;

    /**
     * FotMob's read for the board's game (main thread): its momentum and cards kept as they stood with the read (AsSeen),
     * its match for the Lab's moments, the sky over its ground (FotMob's weather only: none when it gives none), and its
     * shots with when each was first read. A big save or a great finish, and the referee's first booking, come in their
     * turn once the picture has shown them (labTick; none from before the board came up), never as soon as FotMob has
     * them: the picture runs behind it.
     */
    private void labRead(Game g, FotMob.Match m) {
        long now = System.currentTimeMillis();
        if (!g.id.equals(labFor)) {
            labReads.clear();
            labFirstSeen.clear();
        }
        labFor = g.id;
        labMatch = m;
        labShotsNow = m.shotsRaw;
        labHomeTeamId = m.homeTeamId;
        com.gathin.broadcast.MatchStats.firstSeen(m.shotsRaw, labFirstSeen, now);
        int[] goals = com.gathin.broadcast.AsSeen.goals(m.shotsRaw, m.homeTeamId);
        labReads.add(stamp(g, now, com.gathin.broadcast.AsSeen.latest(m.shotsRaw, m.events), goals[0], goals[1]),
                new Object[]{new ArrayList<>(m.momentum), BoardLab.cards(m.events)});
        com.gathin.broadcast.BoardFx.Sky sky = FotMob.sky(m);
        if (sky != null) labSky.put(g.id, sky);
        else labSky.remove(g.id);
        if (labSky.size() > 20) labSky.clear();
        boolean first = labGames.add(g.id);
        if (labGames.size() > 40) labGames.clear();
        if (first) {
            // What was there before the board came up: seen, not news.
            while (com.gathin.broadcast.MatchStats.shotMoment(m.shotsRaw, labShots) != null) {
                // each call marks one shot seen
            }
            int[] cards = BoardLab.cards(m.events);
            if (cards[0] + cards[1] > 0) labShown.add(g.id + "|referee");
        }
        if (labShots.size() > 600) labShots.clear();
    }

    /** FotMob's momentum as it stood at the moment on the picture (null until a read has been reached). */
    @SuppressWarnings("unchecked")
    private List<int[]> labMomentumNow() {
        Object[] r = labReads.pick(picture);
        return r == null ? null : (List<int[]>) r[0];
    }

    /** The referee's cards so far as they stood at the moment on the picture, {yellows, reds} (null until a read is). */
    private int[] labCardsNow() {
        Object[] r = labReads.pick(picture);
        return r == null ? null : (int[]) r[1];
    }

    /** A Lab stat waiting for its turn beside the board (a few at most). */
    private void labWait(String gameId, String[] stat) {
        String[] q = new String[stat.length + 1];
        q[0] = gameId;
        System.arraycopy(stat, 0, q, 1, stat.length);
        labQueue.add(q);
        while (labQueue.size() > 4) labQueue.remove(0);
    }

    /**
     * A goal on the board (main thread, before its moment): the Lab's early goal (inside the first five minutes) and
     * comeback (a side back level or ahead from behind), waiting for their turn after the goal's own moment. A comeback
     * only for a game seen from 0-0, so every goal is known.
     */
    private void labGoal(Game g, int side, String clock, int homeBefore, int awayBefore) {
        if (g.id == null) return;
        int had = homeBefore >= 0 && awayBefore >= 0 ? homeBefore + awayBefore : -1;
        if (!g.id.equals(labGoalsFor)) {
            labGoalsFor = g.id;
            labSides.clear();
            labMinutes.clear();
            labFromStart = had == 0;
        }
        labSides.add(side);
        labMinutes.add(Math.max(0, minuteOfClock(clock)));
        String[] early = com.gathin.broadcast.MatchStats.early(clock);
        if (early != null) labWait(g.id, early);
        // Every goal of the game seen (none missed while the board was away, none taken back since), as the TV's: else no
        // comeback all game.
        if (everyGoalSeen(labFromStart, labSides.size(), had)) {
            int[] sides = new int[labSides.size()], minutes = new int[labMinutes.size()];
            for (int i = 0; i < sides.length; i++) {
                sides[i] = labSides.get(i);
                minutes[i] = labMinutes.get(i);
            }
            String[] back = com.gathin.broadcast.MatchStats.comeback(sides, minutes, code(g.home, g.homeName), code(g.away, g.awayName));
            if (back != null) labWait(g.id, back);
        }
    }

    /**
     * Pure: whether every goal of a game is known at its latest: seen from 0-0 (fromStart), the goals seen (this one's
     * with them) one more than the goals there were before it.
     */
    static boolean everyGoalSeen(boolean fromStart, int seen, int had) {
        return fromStart && had >= 0 && seen == had + 1;
    }

    /** The Lab's stats for the game now, by name (BoardLab.pick): the head to head and runs, each side's drought, the table as it stands. */
    private Map<String, String[]> labStatsNow(Game g, int minute) {
        Map<String, String[]> out = new HashMap<>();
        String hc = code(g.home, g.homeName), ac = code(g.away, g.awayName);
        int hg = g.homeScore, ag = g.awayScore;
        if (g.id.equals(labFor) && labMatch != null) {
            out.putAll(BoardLab.before(labMatch, hc, ac));
            String[] dh = com.gathin.broadcast.MatchStats.drought(labMatch.homeForm, hc, hg, minute);
            String[] da = com.gathin.broadcast.MatchStats.drought(labMatch.awayForm, ac, ag, minute);
            if (dh != null) out.put("Goal drought home", dh);
            if (da != null) out.put("Goal drought away", da);
        }
        if (lastSum != null && g.id.equals(sumFor) && lastSum.standings != null && hg >= 0 && ag >= 0) {
            String[] t = com.gathin.broadcast.MatchStats.asItStands(lastSum.standings, g.homeId, g.awayId, hg, ag, hc, ac);
            if (t != null) out.put("As it stands", t);
        }
        return out;
    }

    /**
     * Each look at the board for its game (main thread): the Scoreboard Lab's moments, as the TV's. The ground's weather
     * lightly over the board two or three times a game (BoardFx.weatherMinutes), the sky as FotMob has it then; the
     * momentum wave once a half; the Lab stats' own moments (early on, after the break, late on); and what
     * happened worth a word (an early goal, a comeback, a big save or great finish, the referee's first booking), each in
     * its turn. Each under its own switch (Stats to show); never in the small window, over an alert or just after a goal.
     */
    private void labTick(Game g, long now) {
        if (g == null || g.id == null || small || preview || hidden || !boardUp()) return;
        int minute = boardMinute;
        boolean live = g.state == Game.LIVE && !g.halftime;
        if (labShown.size() > 300) labShown.clear();
        // The weather touch, the sky as it is now: its minute kept open (within its few minutes) until FotMob has a sky.
        com.gathin.broadcast.BoardFx.Sky sky = labSky.get(g.id);
        if (live && minute >= 0 && sky != null && statOn(c, "weather") && com.gathin.broadcast.BoardFx.weatherDue(g.id, minute, labShown)) {
            bug.weather(sky);
        }
        // The stats wait: something out beside the board, a goal just in, the scores ahead of the picture.
        if (dropping || lineups.isOut() || stats.isOut() || bug.closing()) return;
        if (now - goalShownAt < 90_000L || screen.ahead(now)) return;
        String hc = code(g.home, g.homeName), ac = code(g.away, g.awayName);
        // The momentum wave once a half: FotMob's momentum as it stood at the moment on the picture, its minutes up to the
        // one before the board's (a minute's pressure before a goal is no hint of it).
        if (live && minute >= 0 && statOn(c, "momentum") && g.id.equals(labFor)) {
            List<int[]> mom = labMomentumNow();
            String[] w = mom == null ? null : BoardLab.wave(mom, minute - 1, hc, ac);
            if (w != null && com.gathin.broadcast.BoardFx.waveDue(g.id, minute, labShown) != null) {
                showStats(java.util.Collections.singletonList(w));
                return;
            }
        }
        // The Lab stats' own moments.
        String slot = BoardLab.slot(g.state, g.halftime, minute);
        if (slot != null && labShown.add(g.id + "|" + slot)) {
            List<String[]> pick = BoardLab.pick(slot, labStatsNow(g, minute));
            pick.removeIf(st -> !statOn(c, BoardLab.statKey(st[0])));
            if (!pick.isEmpty()) {
                showStats(pick);
                return;
            }
        }
        // What happened worth a word, in turn.
        for (int i = 0; i < labQueue.size(); i++) {
            String[] q = labQueue.get(i);
            if (!g.id.equals(q[0])) continue;
            labQueue.remove(i);
            String[] st = java.util.Arrays.copyOfRange(q, 1, q.length);
            if (statOn(c, BoardLab.statKey(st[0]))) showStats(java.util.Collections.singletonList(st));
            return;
        }
        labQueue.removeIf(q -> !g.id.equals(q[0]));
        if (!g.id.equals(labFor)) return;
        // A big save or a great finish once the picture has shown it (its minute over on the board's clock, a goal on the
        // board), never as FotMob has it: the picture runs behind (AsSeen). One gone stale is dropped.
        if (statOn(c, "shots")) {
            String[] shot = com.gathin.broadcast.MatchStats.shotMoment(labShotsNow, labShots, picture, labFirstSeen, labHomeTeamId);
            if (shot != null) {
                showStats(java.util.Collections.singletonList(shot));
                return;
            }
        }
        // The referee's first booking, with the cards so far, once the picture has shown them.
        int[] cards = labCardsNow();
        if (cards != null && cards[0] + cards[1] > 0 && labMatch != null && !labMatch.referee.isEmpty() && labShown.add(g.id + "|referee")) {
            String[] ref = com.gathin.broadcast.MatchStats.refCards(labMatch.referee, cards[0], cards[1]);
            if (ref != null && statOn(c, "referee")) showStats(java.util.Collections.singletonList(ref));
        }
    }

    /**
     * A stats moment's stats now: statsFor's, as they stood at the moment on the picture (statsNow), each under its own
     * switch (the settings' Stats to show).
     */
    private List<String[]> statsPick(String slot) {
        List<String[]> pick = statsFor(slot, statsNow());
        pick.removeIf(st -> !statOn(c, BoardLab.statKey(st[0])));
        return pick;
    }

    /** These stats out beside the board now, in its look. */
    private void showStats(List<String[]> pick) {
        if (changed != null) changed.run();
        stats.place(bug.stripLook(), unit() * TEXT, nearTuck(), sideLeft, 0);
        stats.alone(false);
        beside(stats, stats.widthFor(pick, boardH(), shortRoom(boardH(), sideMax)), false);
        stats.show(pick);
    }

    /** The game's summary read again now and then (off the main thread): its stats and facts, the added time put up. */
    private void readStats(Game g, long now) {
        if (g == null || g.id == null || g.slug == null || statsReading) return;
        // A shootout: every ten seconds. While VAR checks, every half a minute: its decision comes out of the board soon after.
        // In play: every half minute, so a substitution is known soon after it is made; just after the channel showed one:
        // every few seconds until it is in the data (SubsTiming).
        long every = pensDue(g) ? PENS_READ_MS
                : now < subsSeenUntil || now < penaltyUntil ? com.gathin.broadcast.SubsTiming.SEEN_READ_MS
                : g.state == Game.LIVE || VarExplained.checking(g.id, now) ? Math.min(com.gathin.broadcast.SubsTiming.LIVE_READ_MS, readEvery(boardMinute))
                : readEvery(boardMinute);
        if (g.id.equals(statsFor) && now - statsReadAt < every) return;
        statsReadAt = now;
        statsReading = true;
        String id = g.id, slug = g.slug;
        String homeCode = code(g.home, g.homeName), awayCode = code(g.away, g.awayName);
        int state = g.state;
        boolean fotmob = fotmobOn(c), varOn = varExplained(c);
        String homeName = g.homeName, awayName = g.awayName;
        long kickoff = g.kickoff;
        boolean last = state == Game.POST;
        App.io.execute(() -> {
            Espn.Summary sum = null;
            try {
                sum = Espn.summary(slug, id, every - 5_000L);
            } catch (RuntimeException ignored) {
                // another time
            }
            Espn.Summary got = sum;
            boolean recoloured = Espn.wornRead(c, id, got);
            Object[] pen = penaltyLook(g, got, state);
            Map<String, String[]> by = got == null ? new HashMap<>() : statsOf(got, state, homeCode, awayCode);
            // FotMob's figures on top: expected goals, big chances, ratings, who is on top, its player of the match.
            FotMob.Match fm = null;
            // Before kick-off only in its last quarter of an hour: the head to head and each side's run for its stats.
            if (fotmob && (state != Game.PRE || kickoff - System.currentTimeMillis() <= PRE_STATS_MS + 5 * 60_000L)) {
                fm = FotMob.forGame(id, homeName, awayName, kickoff);
                if (fm != null) {
                    by.putAll(FotMob.statsOf(fm, homeCode, awayCode));
                    by.putAll(BoardLab.before(fm, homeCode, awayCode));
                }
            }
            FotMob.Match read = fm;
            List<VarExplained.Seen> var = got == null ? null : VarExplained.scan(got.varLines);
            // FotMob's live commentary while the game is on: the quick VAR checks that change nothing have a line only there.
            List<VarExplained.Seen> ltc = null;
            if (fm != null && fm.ticker && state == Game.LIVE && varOn) {
                List<String[]> tick = FotMob.ticker(fm.id);
                if (tick != null) ltc = VarExplained.notInEspn(VarExplained.ticker(tick, got == null ? null : got.varLines), var);
            }
            List<VarExplained.Seen> ltcSeen = ltc;
            // What the read could give away (its latest moment, FotMob's goals), for what waits for the picture (AsSeen).
            int upTo = Math.max(fm == null ? -1 : com.gathin.broadcast.AsSeen.latest(fm.shotsRaw, fm.events),
                    got == null ? -1 : com.gathin.broadcast.AsSeen.latestMinute(got.events, 0));
            int[] fg = fm == null ? new int[]{-1, -1} : com.gathin.broadcast.AsSeen.goals(fm.shotsRaw, fm.homeTeamId);
            App.main.post(() -> {
                statsReading = false;
                com.gathin.broadcast.AsSeen.Stamp st = stamp(g, System.currentTimeMillis(), upTo, fg[0], fg[1]);
                if (recoloured) Espn.wornOn(g);   // the kits they wear in this game, from the next look on
                // A penalty given (its card out until the kick) and VAR's checks and decisions: once the picture reaches this read.
                if (pen != null) held.add(id, st, () -> penaltyRead(g, (String) pen[0], (Drop[]) pen[1]));
                if (read != null) fotmobRead(g, read, st);
                if (var != null) varFromEspn(g, var, st);
                if (ltcSeen != null) varFromTicker(g, ltcSeen, st);
                // Another game: the reads before it go.
                if (!id.equals(statsFor)) {
                    statsReads.clear();
                    addedReads.clear();
                }
                if (got == null) {
                    if (!by.isEmpty()) {
                        statsFor = id;
                        statsReads.add(st, by);
                        addedReads.add(addedStamp(st), addedUp.clone());
                    }
                    return;
                }
                if (!id.equals(statsFor)) addedUp = new int[]{-1, -1, -1, -1};
                for (int i = 0; i < 4; i++) if (got.added[i] > 0) addedUp[i] = got.added[i];
                addedReads.add(addedStamp(st), addedUp.clone());
                if (!by.isEmpty()) statsReads.add(st, by);
                statsFor = id;
                lastSum = got;
                sumFor = id;
                pensRead(id, got, System.currentTimeMillis(), last);
                yellowsRead(g, got);
            });
        });
    }

    // ---- under the board: a penalty shootout; the milestones on the pop-outs -----------------------------------------

    /** A shootout's kicks as read for one game, each side's with when each came; whether the last read was after it ended. */
    private final List<Boolean> pensHome = new ArrayList<>(), pensAway = new ArrayList<>();
    private final List<Long> pensHomeAt = new ArrayList<>(), pensAwayAt = new ArrayList<>();
    private String pensFor = "", pensEndFor = "", pensShownFor = "";
    private boolean pensFinal;
    private long pensEndAt;
    /** The kicks the strip shows (to pop the newest in): for which shootout, how many a side. */
    private int pensShownHome, pensShownAway;
    /** The game's summary read last (its line-ups for ESPN's player ids, its table for the matchweek), and for which game. */
    private Espn.Summary lastSum;
    private String sumFor = "";
    /** The yellow cards seen ("E|…" keys) and the games read for them; those waiting for the picture: {minute, Drop}. */
    private final Set<String> yellowsSeen = new java.util.HashSet<>(), yellowGames = new java.util.HashSet<>();
    private final ArrayDeque<Object[]> yellowsWaiting = new ArrayDeque<>();
    /** The goals whose scorer's season has been asked for, and the games seen for it. */
    private final Set<String> milestoneAsked = new java.util.HashSet<>(), milestoneGames = new java.util.HashSet<>();
    /** Each player's season read for a game, "game|player": {goals, yellow cards, tonight in it 1/0}, or none (empty). */
    private static final Map<String, int[]> SEASONS = new HashMap<>();
    /** The shootout's test under the board: from when, until when. */
    private long pensTestFrom, pensTestUntil;

    /**
     * Pure: whether a game is in its penalty shootout: ESPN's fifth period, or its words say so while it is on.
     */
    static boolean inShootout(Game g) {
        if (g == null || g.state != Game.LIVE) return false;
        if (g.period >= 5) return true;
        String d = g.detail == null ? "" : g.detail.toLowerCase(Locale.ROOT);
        return d.contains("pen") || d.contains("shootout");
    }

    /** Whether the game's summary is wanted often: in its shootout, or just over with the shootout's last read not in. */
    private boolean pensDue(Game g) {
        if (inShootout(g)) return true;
        return g.state == Game.POST && g.id != null && g.id.equals(pensFor) && !pensFinal && !(pensHome.isEmpty() && pensAway.isEmpty());
    }

    /** A summary read (main thread): its shootout's kicks, each new one kept with when it came. */
    private void pensRead(String id, Espn.Summary s, long at, boolean last) {
        if (!id.equals(pensFor)) {
            pensFor = id;
            pensHome.clear();
            pensAway.clear();
            pensHomeAt.clear();
            pensAwayAt.clear();
            pensFinal = false;
        }
        Shootout.arrived(pensHome, pensHomeAt, Shootout.kicks(s.homePens), at);
        Shootout.arrived(pensAway, pensAwayAt, Shootout.kicks(s.awayPens), at);
        if (last && !(pensHome.isEmpty() && pensAway.isEmpty())) pensFinal = true;
    }

    /**
     * What the strip under the board shows now, in the board's look: a shootout's kicks as the picture shows them (a
     * test's made-up ones); nothing in the small window, the preview, while the line-ups are out or with no room below
     * the board.
     */
    private void underNow(Game g, OverlayBug.Look l, long now) {
        if (l == null || l.f1 || small || preview || hidden || lineups.isOut() || !boardUp()) {
            under.away();
            bug.pens(null, null);
            return;
        }
        under.clubs(l.home, l.away, l.homeColour, l.awayColour, bug.homeCrest, bug.awayCrest);
        if (now < pensTestUntil) {
            List<Boolean>[] k = Shootout.test(now - pensTestFrom);
            if (!pensOut("TEST", k[0], k[1])) {
                under.away();
                bug.pens(null, null);
            }
            return;
        }
        if (g == null || !pensNow(g, now)) {
            under.away();
            bug.pens(null, null);
        }
    }

    /** The shootout under the board for g, its kicks in step with the picture; false when there's none to show. */
    private boolean pensNow(Game g, long now) {
        if (g.id == null || !pensOn(c)) return false;
        boolean ours = g.id.equals(pensFor), on = inShootout(g);
        if (g.state == Game.POST) {
            if (!ours || pensHome.isEmpty() && pensAway.isEmpty()) return false;
            // Over on the screen: it stays a minute, then goes with the board.
            if (!g.id.equals(pensEndFor)) {
                pensEndFor = g.id;
                pensEndAt = now;
            }
            on = now - pensEndAt < PENS_STAY_MS;
        }
        if (!on) return false;
        long seen = now - screen.behind();
        List<Boolean> hk = ours ? Shootout.shown(pensHome, pensHomeAt, seen) : new ArrayList<>();
        List<Boolean> ak = ours ? Shootout.shown(pensAway, pensAwayAt, seen) : new ArrayList<>();
        return pensOut(g.id, hk, ak);
    }

    /**
     * These kicks under the board: the newest popping in with the swish (none for those there when it came). The games
     * that have it (BoardFx.pensInClock, as the TV's) show them beside the clock on the board instead, in a tab of their
     * own, each new kick in with the swish.
     */
    private boolean pensOut(String key, List<Boolean> hk, List<Boolean> ak) {
        if (bug.pensInClock()) {
            boolean fresh = key.equals(pensShownFor) && (hk.size() > pensShownHome || ak.size() > pensShownAway);
            pensShownFor = key;
            pensShownHome = hk.size();
            pensShownAway = ak.size();
            under.away();
            bug.pens(hk, ak);
            if (fresh) swishIn();
            return true;
        }
        bug.pens(null, null);
        if (!placeUnder(Math.max(5, Math.max(hk.size(), ak.size())))) return false;
        int newSide = -1, newIndex = -1;
        if (!key.equals(pensShownFor)) {
            pensShownFor = key;
        } else {
            if (ak.size() > pensShownAway) {
                newSide = 1;
                newIndex = ak.size() - 1;
            }
            if (hk.size() > pensShownHome) {
                newSide = 0;
                newIndex = hk.size() - 1;
            }
        }
        pensShownHome = hk.size();
        pensShownAway = ak.size();
        under.pens(Shootout.dots(hk, ak), Shootout.scored(hk), Shootout.scored(ak), newSide, newIndex);
        return true;
    }

    /**
     * The strip under the board placed for a shootout of so many dots a row: its top tucked under the board's foot (a
     * little apart under a pill), as wide as its dots; drawn smaller rather than past the screen's edge. False when
     * there's no room below.
     */
    private boolean placeUnder(int dots) {
        float u = unit(), dp = c.getResources().getDisplayMetrics().density;
        int bh = boardH(), bw = boardW(), most = bw + Math.max(0, roomRight);
        OverlayBug.StripLook look = bug.stripLook();
        int need = BoardUnder.pensWidth(u, dots);
        if (need > most) u = Math.max(u * 0.6f, u * most / (float) need);
        int w = Math.min(BoardUnder.pensWidth(u, dots), Math.max(most, bw));
        int h = BoardUnder.heightFor(BoardUnder.PENS, u);
        int into = look.apart ? -Math.round(3 * dp) : Math.round(Math.min(8 * dp, bh * look.round)) + 1;
        if (roomBelow < h - into) return false;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) under.getLayoutParams();
        int top = head + bh - into;
        if (lp.width != w || lp.height != h || lp.leftMargin != boardAt || lp.topMargin != top) {
            lp.width = w;
            lp.height = h;
            lp.leftMargin = boardAt;
            lp.topMargin = top;
            under.setLayoutParams(lp);
        }
        under.place(look, u, bw);
        return true;
    }

    // ---- milestones ------------------------------------------------------------------------------------------------

    /** ESPN's id for a player of the board's game by his name and side, from its line-ups read last; null when not known. */
    private String athlete(Game g, String name, String side) {
        if (lastSum == null || g == null || g.id == null || !g.id.equals(sumFor)) return null;
        List<String[]> lineup = "home".equals(side) ? lastSum.homeLineup : "away".equals(side) ? lastSum.awayLineup : null;
        return Milestones.athleteId(lineup, name, Espn.P_NAME, Espn.P_SHORT, Espn.P_ID);
    }

    /**
     * Each new goal of the board's game (as the scores have it, ahead of the picture): its scorer's season looked up now,
     * off the main thread, so his caption has it when the picture gets there. Those before the board came up: never.
     */
    private void prefetchGoals(Game g) {
        if (!milestonesOn(c) || g.id == null || !g.id.equals(sumFor) || lastSum == null) return;
        boolean first = milestoneGames.add(g.id);
        for (String[] goal : g.goals) {
            if (goal.length < 4 || !milestoneAsked.add(goal[0]) || first) continue;
            if (milestoneAsked.size() > 400) milestoneAsked.clear();
            if (goal.length > 4 && "og".equals(goal[4])) continue;
            String aid = athlete(g, goal[1], goal[3]);
            if (aid == null) continue;
            String slug = g.slug, id = g.id, league = g.leagueName;
            App.io.execute(() -> seasonOf(slug, id, league, aid));
        }
    }

    /** A goal's milestone, looked up off the main thread: {"3rd of the season", TAG_GOAL}, or null for none. */
    private java.util.concurrent.Callable<Object[]> goalMilestone(Game g, String[] goal) {
        if (!milestonesOn(c) || g == null || g.id == null || goal == null || goal.length < 4) return null;
        if (goal.length > 4 && "og".equals(goal[4])) return null;
        int inGame = Milestones.inGame(g.goals, goal[0], goal[1]);
        String aid = inGame >= 2 ? null : athlete(g, goal[1], goal[3]);
        String slug = g.slug, id = g.id, league = g.leagueName;
        return () -> {
            int season = -1;
            boolean counted = false;
            int[] s = aid == null ? null : seasonOf(slug, id, league, aid);
            if (s != null && s.length == 3) {
                season = s[0];
                counted = s[2] == 1;
            }
            String t = Milestones.goal(inGame, Milestones.seasonWithTonight(season, counted, inGame), false);
            return t.isEmpty() ? null : new Object[]{t, BoardDrop.TAG_GOAL};
        };
    }

    /** A player's season in the game's competition (off the main thread), read once a game: null when ESPN has none. */
    private static int[] seasonOf(String slug, String gameId, String league, String athleteId) {
        String key = gameId + "|" + athleteId;
        synchronized (SEASONS) {
            if (SEASONS.containsKey(key)) {
                int[] had = SEASONS.get(key);
                return had == null || had.length == 0 ? null : had;
            }
        }
        org.json.JSONObject o = Espn.athleteOverview(slug, athleteId);
        int[] s = o == null ? null : Milestones.season(o, gameId, Espn.name(slug), league == null ? "" : league);
        synchronized (SEASONS) {
            if (SEASONS.size() > 200) SEASONS.clear();
            // Not read (no answer): asked again next time; read with no figures: none for the game.
            if (o != null) SEASONS.put(key, s == null ? new int[0] : s);
        }
        return s;
    }

    /**
     * A summary read (main thread): each new yellow card of the board's game where the competition counts them; its
     * player's yellows this season looked up, and out beside the board once the picture has shown it, only when it says
     * something (one more and he's banned; banned for the next game). Those before the board came up: never.
     */
    private void yellowsRead(Game g, Espn.Summary s) {
        if (g.id == null) return;
        boolean own = g.id.equals(lastId) && mode == FOOTBALL_MODE, first = yellowGames.add(g.id);
        for (String[] e : s.events) {
            if (e.length < 5 || !"yellow card".equalsIgnoreCase(e[1])) continue;
            if (!yellowsSeen.add(e[4]) || first) continue;
            if (yellowsSeen.size() > 400) yellowsSeen.clear();
            if (!own || small || !milestonesOn(c) || !shows(c, FOOTBALL) || !Milestones.counts(g.slug)) continue;
            String aid = athlete(g, e[2], e[3]);
            if (aid == null) continue;
            int played = 0;
            String team = "home".equals(e[3]) ? g.homeId : g.awayId;
            for (String[] row : s.table) if (row.length > 4 && row[0].equals(team)) played = parse(row[4]);
            int week = Milestones.matchweek(played, java.util.Calendar.getInstance());
            String slug = g.slug, id = g.id, league = g.leagueName;
            int minute = cardMinute(e[0]);
            App.io.execute(() -> {
                int[] season = seasonOf(slug, id, league, aid);
                if (season == null || season.length < 3) return;
                int yellows = Milestones.seasonWithTonight(season[1], season[2] == 1, 1);
                int kind = Milestones.yellow(slug, yellows, week);
                if (kind == Milestones.NONE) return;
                App.main.post(() -> {
                    if (!running || !id.equals(lastId)) return;
                    Drop d = yellowDrop(g, e, kind);
                    d.homeCrest = bug.homeCrest;
                    d.awayCrest = bug.awayCrest;
                    yellowsWaiting.addLast(new Object[]{minute, d, id});
                });
            });
        }
    }

    /** The yellow cards waiting for the picture: out once the board's clock (in step with it) has passed their minute. */
    private void releaseYellows(Game g, String clock) {
        if (yellowsWaiting.isEmpty()) return;
        int m = minuteOfClock(clock);
        for (java.util.Iterator<Object[]> it = yellowsWaiting.iterator(); it.hasNext(); ) {
            Object[] w = it.next();
            if (g.id == null || !g.id.equals(w[2]) || g.state == Game.POST) {
                it.remove();
                continue;
            }
            if (m < 0 || m < (Integer) w[0] || g.halftime) continue;
            it.remove();
            if (!small) drop((Drop) w[1]);
        }
    }

    /** Pure: a card's minute as the board's clock passes it ("34'": 34; "45'+2'": 47); 0 when it has none. */
    static int cardMinute(String minute) {
        if (minute == null) return 0;
        String[] parts = minute.split("\\+");
        int base = firstNumber(parts[0]), extra = parts.length > 1 ? firstNumber(parts[1]) : 0;
        return base + extra;
    }

    /** Pure: a yellow card's pop-out with its ban's words: YELLOW CARD, the player and his minute, the tag. */
    static Drop yellowDrop(Game g, String[] e, int kind) {
        Drop d = new Drop();
        d.chip = "YELLOW CARD";
        d.colour = BoardDrop.CARD_YELLOW;
        d.own = true;
        d.side = "home".equals(e[3]) ? 1 : "away".equals(e[3]) ? 2 : 0;
        d.line = (e[2] + " " + e[0]).trim();
        d.tag = Milestones.yellowWords(kind);
        d.tagKind = kind == Milestones.BANNED ? BoardDrop.TAG_BAN : BoardDrop.TAG_WARN;
        d.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        return d;
    }

    /** The substitutions seen, "game|minute|who came on": each slides out once, those before the board came up never. */
    private final Set<String> subsSeen = new java.util.HashSet<>(), subsGames = new java.util.HashSet<>();

    /** FotMob's game read (main thread): its VAR check on the board, and each new substitution beside it. */
    private void fotmobRead(Game g, FotMob.Match m, com.gathin.broadcast.AsSeen.Stamp st) {
        boolean own = g.id != null && g.id.equals(lastId) && mode == FOOTBALL_MODE;
        // FotMob's VAR events kept for VAR explained (the reason and the on-field call ESPN's words lack).
        VarExplained.fotmob(g.id, m.vars, m.reds);
        // Its check under way, its decisions: on the board once the picture has reached this read.
        if (own) varFromFotMob(g, m, st);
        // The minutes added FotMob has where ESPN's commentary has none (not every game has commentary).
        if (own) for (int i = 0; i < 4; i++) if (addedUp[i] <= 0 && m.added[i] > 0) addedUp[i] = m.added[i];
        if (own) {
            shotsFor = g.id;
            shots = new java.util.ArrayList<>(m.shots);
            refereeOut(g, m);
            labRead(g, m);
        }
        boolean first = subsGames.add(g.id);
        List<String[]> fresh = new java.util.ArrayList<>();
        for (String[] e : m.events) {
            if (!"Substitution".equals(e[0])) continue;
            // known by its two players, never the minute (the data can put it right a minute later)
            if (!subsSeen.add(com.gathin.broadcast.SubsTiming.key(g.id, e)) || first) continue;
            if (subsSeen.size() > 400) subsSeen.clear();
            if (!own || small || !subsOn(c) || !shows(c, FOOTBALL)) continue;
            fresh.add(e);
        }
        // A club's changes in the same minute together: a double, triple or more on one OFF page and one ON page.
        for (List<String[]> group : SubsPage.group(fresh)) subsOut(g, group);
    }

    /** One club's substitution(s) out beside the board, once every player's picture has been looked for (off the main thread). */
    private void subsOut(Game g, List<String[]> group) {
        Drop d = subDrop(g, group);
        d.own = true;
        d.homeCrest = bug.homeCrest;
        d.awayCrest = bug.awayCrest;
        String club = "1".equals(group.get(0)[2]) ? g.homeName : g.awayName;
        SubsPage.Subs s = d.subs;
        String minute = group.get(0)[1];
        long known = System.currentTimeMillis();
        App.io.execute(() -> {
            for (int i = 0; i < s.on.length; i++) s.onFaces[i] = face(s.onFull[i], s.onIds[i], club);
            for (int i = 0; i < s.off.length; i++) s.offFaces[i] = face(s.offFull[i], s.offIds[i], club);
            App.main.post(() -> subDue(d, minute, known));
        });
    }

    /**
     * A substitution out beside the board once the picture has reached it: the board's clock at its minute (it can be
     * known before the channel shows it), at once when the channel has just shown one (its graphic), or after waiting
     * its longest (SubsTiming).
     */
    private void subDue(Drop d, String minute, long known) {
        if (!running) return;
        long now = System.currentTimeMillis();
        if (com.gathin.broadcast.SubsTiming.due(minute, boardSecs, now < subsSeenUntil, now - known)) {
            drop(d);
            return;
        }
        h.postDelayed(() -> subDue(d, minute, known), 1_000L);
    }

    /**
     * The channel's own graphic for a substitution seen (the words BugCover read off what grew out of its scoreboard):
     * the match data read now and every few seconds for a while, and the substitution out as soon as it is in it.
     */
    void subOnScreen() {
        long now = System.currentTimeMillis();
        boolean fresh = now >= subsSeenUntil;
        subsSeenUntil = now + com.gathin.broadcast.SubsTiming.SEEN_MS;
        if (!fresh) return;
        android.util.Log.i("ScoreBoard", "the channel shows a substitution: its data read now");
        statsReadAt = 0;
        subsLook();
    }

    /** While the channel's substitution is fresh: the summary read as often as SubsTiming says (readStats keeps the pace). */
    private void subsLook() {
        h.removeCallbacks(subsLook);
        long now = System.currentTimeMillis();
        if (!running || now >= subsSeenUntil) return;
        if (lastGot != null && lastGot.state == Game.LIVE) readStats(lastGot, now);
        h.postDelayed(subsLook, 2_000L);
    }

    /** A player's picture (off the main thread): the cut-out the cards use, else FotMob's; null when there is neither. */
    private Bitmap face(String name, String id, String club) {
        try {
            Bitmap b = name == null || name.isEmpty() ? null : PlayerPhotos.get(c, name, club, null);
            return b != null ? b : fotmobPhoto(c, parse(id));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Pure: a substitution's alert ({type, minute, home, on, on id, off, off id}): who came on, who went off, when. */
    static Drop subDrop(Game g, String[] e) {
        return subDrop(g, java.util.Collections.singletonList(e));
    }

    /**
     * Pure: a club's substitution or substitutions at once: its OFF page (who goes off, each with their picture and
     * name) and its ON page (who comes on), the whole name for one, the surname for more (SubsPage).
     */
    static Drop subDrop(Game g, List<String[]> group) {
        Drop d = new Drop();
        String[] e = group.get(0);
        boolean home = "1".equals(e[2]);
        d.subs = SubsPage.of(group);
        d.subs.colour = home ? OverlayBug.colour(g.homeColor, g.homeAlt) : OverlayBug.colour(g.awayColor, g.awayAlt);
        d.chip = "SUBSTITUTION";
        d.colour = 0xFF5CC8FF;
        String team = home ? (g.homeName != null ? g.homeName : g.home) : (g.awayName != null ? g.awayName : g.away);
        d.title = team == null ? "" : team;
        StringBuilder line = new StringBuilder("On ");
        for (int i = 0; i < d.subs.onFull.length; i++) line.append(i > 0 ? ", " : "").append(d.subs.onFull[i].trim());
        for (int i = 0; i < d.subs.offFull.length; i++) line.append(i > 0 ? ", " : " · off ").append(d.subs.offFull[i].trim());
        if (!d.subs.minute.isEmpty()) line.append(" · ").append(d.subs.minute).append("'");
        d.line = line.toString();
        d.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        d.side = home ? 1 : 2;
        return d;
    }

    private static int parse(String n) {
        try {
            return Integer.parseInt(n == null ? "" : n.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A player's FotMob picture by its id (off the main thread); null when there is none. */
    private static Bitmap fotmobPhoto(Context c, int id) {
        return id > 0 ? PlayerPhotos.picture(c, FotMob.photoUrl(id)) : null;
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

    /** The channel's own scoreboard gone (a replay, BugCover): the board away as a broadcaster's goes, back with it. */
    private boolean replayAway;
    /**
     * Whether the picture is back in live play after a goal (LivePlay, from the whole frame twice a second: ScoreOverlay),
     * so what a goal brings (its scorer, the restart's pop-up) never comes over its replays, also where the channel's
     * own scoreboard stays up in them.
     */
    final com.gathin.broadcast.LivePlay play = new com.gathin.broadcast.LivePlay();
    /** The classic pop-up up, or a goal's restart pop-up still to come (PlayerIntro): what comes out of the board waits. */
    private java.util.function.BooleanSupplier introBusy = () -> false;

    void introBusy(java.util.function.BooleanSupplier s) {
        introBusy = s == null ? () -> false : s;
    }

    /** Whether the after-goal pop-up showed a side's n-th goal of a game (PlayerIntro.restartShowed). */
    interface GoalShown {
        boolean showed(String gameId, int side, int n);
    }

    /** The after-goal pop-up's goals: their scorer beside the board is left out (only with it switched off does it come). */
    private GoalShown afterGoal = (id, side, n) -> false;

    void afterGoal(GoalShown s) {
        afterGoal = s == null ? (id, side, n) -> false : s;
    }

    /** A moment's pop-up on the picture now (PlayerIntro): the board away meanwhile. */
    private java.util.function.BooleanSupplier momentUp = () -> false;
    /** A goal's restart pop-up still to come (PlayerIntro): away in the replays, the board stays away until it has been. */
    private java.util.function.BooleanSupplier restartNext = () -> false;
    /** Away for a moment's pop-up: back in with the swish. */
    private boolean swishBack;

    void moments(java.util.function.BooleanSupplier up, java.util.function.BooleanSupplier next) {
        momentUp = up == null ? () -> false : up;
        restartNext = next == null ? () -> false : next;
    }

    /** A moment's pop-up came or went (PlayerIntro): the board away or back in now. */
    void momentChanged() {
        draw(System.currentTimeMillis());
    }

    /** The swish as the board comes back in, panned to where it is on the screen; not with the switch off. */
    private void swishIn() {
        if (!PlayerIntro.swishOn(bug.getContext())) return;
        int[] at = new int[2];
        bug.getLocationOnScreen(at);
        int sw = bug.getResources().getDisplayMetrics().widthPixels;
        float pan = sw > 0 ? Math.max(-0.6f, Math.min(0.6f, ((at[0] + bug.getWidth() / 2f) / sw * 2f - 1f) * 0.6f)) : 0f;
        com.gathin.broadcast.Sfx.play(bug.getContext(), com.gathin.broadcast.Sfx.SWISH, pan, 0.7f);
    }

    void replay(boolean away) {
        if (away == replayAway) return;
        replayAway = away;
        play.bug(away, System.currentTimeMillis());
        draw(System.currentTimeMillis());
        // Back for live play: what waited comes out.
        if (!away && !drops.isEmpty()) h.post(nextDrop);
    }

    /** Whether the board is away in a replay now. */
    boolean replayAway() {
        return replayAway;
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
        // VAR explained has the board's own game's checks and decisions from the commentary: the plain alert not as well.
        if (d.own && "var".equals(a.kind) && varExplained(c) && (VarExplained.decidedLately(g.id, System.currentTimeMillis())
                || VarExplained.checking(g.id, System.currentTimeMillis()) || varHeld.waiting(g.id))) return;
        if (d.own && "var".equals(a.kind)) bug.varCheck(8_000L);
        // The line-ups, while the board is up: as the TV's card beside it.
        if (d.lineups && lineupsOn(c) && a.summary != null && g != null && boardUp() && !(src instanceof FloatService)) {
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
                if ("goal".equals(a.kind) && who != null) d.milestone = goalMilestone(g, who);
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
     * The alert's pictures fetched off the main thread (the player's photo, another game's crests) and its milestone
     * looked up beside them, then it slides out; after PHOTO_WAIT_MS it goes without what hasn't come.
     */
    private void enrich(Drop d, Game g, String who, String club) {
        boolean crests = !d.own && g != null && (d.homeCrest == null || d.awayCrest == null);
        if (who == null && !crests && d.milestone == null) {
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
        // Out once both have come (the pictures, the milestone), or at PHOTO_WAIT_MS without what hasn't.
        int[] left = {d.milestone != null ? 2 : 1};
        Runnable one = () -> {
            if (done[0] || --left[0] > 0) return;
            h.removeCallbacks(go);
            go.run();
        };
        if (d.milestone != null) {
            java.util.concurrent.Callable<Object[]> work = d.milestone;
            App.io.execute(() -> {
                Object[] mark = null;
                try {
                    mark = work.call();
                } catch (Exception ignored) {
                    // without it
                }
                Object[] m = mark;
                App.main.post(() -> {
                    if (done[0]) return;
                    if (m != null) {
                        d.tag = (String) m[0];
                        d.tagKind = (Integer) m[1];
                    }
                    one.run();
                });
            });
        }
        int px = Math.round(28 * c.getResources().getDisplayMetrics().density * 2);
        App.io.execute(() -> {
            Bitmap photo = null, home = null, away = null;
            try {
                if (who != null) photo = PlayerPhotos.get(c, who, club, null);
                if (photo == null && who != null && fotmobOn(c)) photo = fotmobPhoto(c, FotMob.playerId(who));
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
                one.run();
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
            d.milestone = goalMilestone(at, goal);
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
        d.gameId = g.id;
        d.goalN = com.gathin.broadcast.Moments.goalNumber(g.goals, goal);
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
    public static BoardLineups.Data lineupsData(Context c, Game g, Espn.Summary sum, int px) {
        return lineupsData(c, g, sum, px, true);
    }

    /**
     * Everything the card shows, the players' photos only when asked for (the pitch alone, as a notification draws it,
     * needs none). Off the main thread.
     */
    public static BoardLineups.Data lineupsData(Context c, Game g, Espn.Summary sum, int px, boolean photos) {
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
        if (!photos) return d;
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
            case "lineups": return "LINE-UPS";
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

    /** The ad-break screen up: the half-time and full-time pop-outs (alerts and stats) wait until it has gone. */
    private boolean holdBreak;

    /** The ad-break screen up (true) or gone (false): what waited comes out then. */
    void holdBreakMoments(boolean hold) {
        if (holdBreak == hold) return;
        holdBreak = hold;
        if (!hold && !dropping && !drops.isEmpty()) next();
    }

    /** Pure: whether an alert's kind (or a stats moment) waits while the ad-break screen is up: half time's and full time's. */
    static boolean waitsForAdBreak(String kind) {
        return "ht".equals(kind) || "ft".equals(kind);
    }

    private static boolean waits(Drop d) {
        return d != null && d.alert != null && waitsForAdBreak(d.alert.kind);
    }

    /** The halves whose minutes added have come out beside the board, "game|half": each once. */
    private final Set<String> addedShown = new java.util.HashSet<>();

    /** The minutes added out beside the board as an alert, once a half (the tab beside the board keeps them after). */
    private void addedOut(Game g, String added, int half) {
        if (g.id == null || !addedShown.add(g.id + "|" + half)) return;
        if (addedShown.size() > 200) addedShown.clear();
        if (small || !shows(c, FOOTBALL)) return;
        Drop d = addedDrop(g, added);
        if (d == null) return;
        d.own = true;
        d.homeCrest = bug.homeCrest;
        d.awayCrest = bug.awayCrest;
        // Kept out for the rest of the half (over the channel's own added-time board), unless switched off.
        if (addedStays(c)) {
            standing = d;
            standingGame = g.id;
            standingPeriod = half;
        }
        drop(d);
    }

    // ---- the minutes added kept out beside the board until the half is over -------------------------------------------

    /** The minutes added out beside the board for the rest of their half: the alert, its game, its half. */
    private Drop standing;
    private String standingGame;
    private int standingPeriod;
    /** The alert out beside the board last; whether the minutes added are on their way in for another alert. */
    private Drop lastOut;
    private boolean addedGoing;

    /** Pure: whether the minutes added stay out now: the game live, not half or full time, still the half they came in. */
    /** Pure: the minutes added in the board's own tab: none while their panel stands out beside it (it says them). */
    static String boardAdded(String added, boolean panelStands) {
        return com.gathin.broadcast.AddedTime.boardAdded(added, panelStands);
    }

    static boolean addedStands(int state, boolean halftime, boolean live, int period, int standingPeriod) {
        return com.gathin.broadcast.AddedTime.stands(state == Game.LIVE, halftime, live, period, standingPeriod);
    }

    /**
     * Each frame drawn: the minutes added kept out beside the board (in for another alert and back out after it, back
     * out with the board after a replay), or let go once the half is over on the screen.
     */
    private void keepAdded(Game g, OverlayBug.Look l) {
        Drop s = standing;
        if (s == null) return;
        boolean on = !small && g != null && l != null && g.id != null && g.id.equals(standingGame)
                && addedStands(g.state, g.halftime, l.live, period(g, l.clock), standingPeriod);
        if (!on) {
            endAdded();
            return;
        }
        if (!dropping) {
            next();
            return;
        }
        boolean out = strip.isOut() && strip.drop() == s;
        if (out && !drops.isEmpty() && !addedGoing) {
            addedGoing = true;
            hideDrop();
        } else if (!out && lastOut == s && !strip.isOut()) {
            next();
        }
    }

    /** The half is over (or the game another): the minutes added go back in under the board. */
    private void endAdded() {
        Drop s = standing;
        standing = null;
        if (s != null && strip.isOut() && strip.drop() == s && !addedGoing) {
            addedGoing = true;
            hideDrop();
        }
    }

    /** Pure: the minutes added's alert ("ADDED TIME", "+4 minutes"), in added time's red; null for words that are no minutes. */
    static Drop addedDrop(Game g, String added) {
        int n;
        try {
            n = Integer.parseInt(added.replace("+", "").trim());
        } catch (RuntimeException e) {
            return null;
        }
        if (n <= 0) return null;
        Drop d = new Drop();
        d.chip = "ADDED TIME";
        d.colour = 0xFFE3061B;
        d.addedMins = n;
        d.title = "+" + n + (n == 1 ? " minute" : " minutes");
        d.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        d.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        return d;
    }

    private void drop(Drop d) {
        if (small) return;
        // A goal in the game: its penalty waiting for the kick is taken (scored, or play went on).
        if (d.key != null && penaltyFor != null && (d.own || penaltyFor.equals(d.gameId))) penaltyTaken(penaltyFor);
        if ("VAR".equals(d.chip)) {
            // VAR's check and decision go first: they are about now, and over in a minute or two.
            drops.addFirst(d);
            while (drops.size() > 4) drops.removeLast();
        } else {
            drops.addLast(d);
            while (drops.size() > 4) drops.removeFirst();
        }
        if (!dropping) next();
    }

    private void next() {
        h.removeCallbacks(nextDrop);
        addedGoing = false;
        // Nothing else to say: the minutes added back out, while their half lasts.
        if (!small && drops.isEmpty() && standing != null) drops.addLast(standing);
        if (small || drops.isEmpty()) {
            drops.clear();
            dropping = false;
            return;
        }
        dropping = true;
        // In a replay (the board away), or after a goal until play is back (its replays the channel's scoreboard stays
        // up in): the alert waits for live play. A VAR check's own comes at once.
        Drop first = drops.peekFirst();
        boolean var = first != null && (first.varCheck || first.why != null);
        // And while the classic pop-up is up, or a goal's restart pop-up is still to come: that first, then this.
        if (replayAway || !var && (play.holding(System.currentTimeMillis()) || introBusy.getAsBoolean())) {
            h.postDelayed(nextDrop, 1000);
            return;
        }
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
        // The ad-break screen up: half time's and full time's wait (in their place), the rest come out.
        Drop d = null;
        if (holdBreak) {
            for (Drop x : drops) if (!waits(x)) { d = x; break; }
            if (d == null) {
                dropping = false;
                return;
            }
            drops.remove(d);
        } else {
            d = drops.pollFirst();
        }
        // A goal's scorer, when the after-goal pop-up showed that goal (its scorer, its minute): left out, the pop-up said it.
        if (d.own && d.goalN > 0 && d.gameId != null && afterGoal.showed(d.gameId, d.side, d.goalN)) {
            h.post(nextDrop);
            return;
        }
        if (d.lineups && d.alert != null && d.alert.summary != null && d.alert.game != null && boardUp() && !(src instanceof FloatService)) {
            dropping = false;
            lineupsCard(d.alert);
            return;
        }
        lastOut = d;
        if (changed != null) changed.run();
        boolean onBoard = boardUp();
        int bh = boardH();
        // A player's picture standing up out of it (on any of its pages): the room above the strip for it.
        boolean pictured = d.photo != null || d.subs != null;
        if (d.turns != null) for (Drop t : d.turns) pictured |= t.photo != null;
        // On its own (no board up): where the board would be, round at both ends.
        strip.place(bug.stripLook(), unit() * TEXT, onBoard ? nearTuck() : 0, onBoard && sideLeft, pictured ? head : 0);
        strip.alone(!onBoard);
        int room = onBoard ? sideMax : Math.max(Math.round(unit() * 6), sideMax - tuck);
        // Two or more substitutions at once: the room beside the board, wider than an alert's, for every player on a page.
        boolean many = d.subs != null && Math.max(d.subs.on.length, d.subs.off.length) > 1;
        int w = strip.widthFor(d, bh, many ? Math.max(shortRoom(bh, room), Math.min(room, bh * 16)) : shortRoom(bh, room));
        // The penalty card as wide as its widest page, so it turns over without changing size (the widest measured last:
        // the strip keeps the last width it measured for its lines' runs).
        if (d.turns != null) {
            Drop widest = d;
            for (Drop t : d.turns) {
                int tw = strip.widthFor(t, bh, shortRoom(bh, room));
                if (tw > w) {
                    w = tw;
                    widest = t;
                }
            }
            w = strip.widthFor(widest, bh, shortRoom(bh, room));
        }
        // Stride's strap under it: the alert at least as wide as the strap.
        boolean strap = sponsored(c);
        strip.strap(strap ? BoardSide.strapRoom(bh) : 0);
        if (strap) w = Math.min(room, Math.max(w, tuck + Math.round(com.gathin.broadcast.Stride.width(com.gathin.broadcast.Stride.strapHeight(bh)))));
        beside(strip, w, pictured);
        strip.show(d);
        // The minutes added: out until their half is over (keepAdded takes them in), not for a few seconds.
        if (d == standing) return;
        // VAR explained: a check stays out until its decision (CHECK_MAX_MS at most); a decision with its WHY under it.
        if (d.varCheck) {
            h.postDelayed(hide, VarExplained.CHECK_MAX_MS);
            return;
        }
        // The penalty card: out until its kick is in (penaltyRead takes it in), turning over to the taker and the keeper.
        if (d.penalty) {
            if (d.turns != null || penaltyCard == null || !java.util.Objects.equals(penaltyCard.gameId, d.gameId)) penaltyCard = d;
            turnPenalty(d, 0);
            h.postDelayed(hide, PENALTY_MAX_MS);
            return;
        }
        if (d.why != null) {
            Drop shown = d;
            h.postDelayed(() -> showWhy(shown), BoardSide.SLIDE_MS);
            h.postDelayed(hide, Math.max(VarExplained.WHY_MS, strip.showFor(DROP_MS)));
            return;
        }
        h.postDelayed(hide, strip.showFor(DROP_MS));
    }

    private void hideDrop() {
        h.removeCallbacks(hide);
        if (!strip.isOut()) {
            why.gone();
            next();
            return;
        }
        Drop out = strip.drop();
        // A check gone without its decision: the scan along the board stops with it.
        if (out != null && out.varCheck) bug.varCheck(0L);
        // The WHY folds back up under the decision first, then the decision goes in.
        if (why.isUp()) {
            why.close(() -> strip.fold(this::next));
            return;
        }
        strip.fold(this::next);
    }

    // ---- VAR explained: the check out of the board, then its decision with a WHY --------------------------------------

    /** Pure: VAR explained's alert beside the board: the chip in VAR's cyan, the game's clubs' colours at its far end. */
    static Drop varDrop(Game g, String title, String line) {
        Drop o = new Drop();
        o.chip = "VAR";
        o.colour = VarExplained.CYAN;
        o.title = title;
        o.line = line == null ? "" : line;
        o.own = true;
        if (g != null) {
            o.gameId = g.id;
            o.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
            o.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        }
        return o;
    }

    /** A check under way ({about, reason, player}): out of the board until its decision, the scan along the board with it. */
    private void varCheckOut(Game g, String[] about) {
        if (g == null || g.id == null || small) return;
        long now = System.currentTimeMillis();
        if (VarExplained.decidedLately(g.id, now) && !VarExplained.checking(g.id, now)) return;
        Drop out = strip.isOut() ? strip.drop() : null;
        if (out != null && out.varCheck && g.id.equals(out.gameId)) return;
        for (Drop q : drops) if (q.varCheck && g.id.equals(q.gameId)) return;
        VarExplained.checkStarted(g.id, now);
        bug.varCheck(VarExplained.CHECK_MAX_MS);
        Drop d = varDrop(g, VarExplained.checkTitle(about[0]), VarExplained.checkLine(about[1], about[2]));
        d.varCheck = true;
        penaltyAside(g.id);
        drop(d);
    }

    /** A decision: in its check's place (turning over), else out of the board; its WHY folding down under it. */
    private void varDecisionOut(Game g, VarExplained.Call call) {
        if (g == null || g.id == null || small || call == null) return;
        long now = System.currentTimeMillis();
        // One decision a moment, whichever of ESPN and FotMob has it first.
        if (VarExplained.decidedLately(g.id, now) && !VarExplained.checking(g.id, now)) return;
        VarExplained.decided(g.id, now);
        long took = VarExplained.checkLength(g.id, now);
        bug.varCheck(0L);
        Drop d = varDrop(g, VarExplained.outcome(call), VarExplained.callLine(call.onField));
        d.why = VarExplained.whyOf(call, took);
        Drop out = strip.isOut() ? strip.drop() : null;
        if (out != null && out.varCheck && g.id.equals(out.gameId)) {
            // The check out now turns over into its decision, wider or narrower as its words need.
            h.removeCallbacks(hide);
            int bh = boardH();
            boolean onBoard = boardUp();
            int room = onBoard ? sideMax : Math.max(Math.round(unit() * 6), sideMax - tuck);
            beside(strip, strip.widthFor(d, bh, shortRoom(bh, room)), false);
            strip.turnTo(d);
            h.postDelayed(() -> showWhy(d), 360L);
            h.postDelayed(hide, VarExplained.WHY_MS);
            if ("no_penalty".equals(call.kind)) penaltyGone(g.id);
            return;
        }
        // A check still waiting its turn: the decision in its place.
        drops.removeIf(q -> q.varCheck && g.id.equals(q.gameId));
        penaltyAside(g.id);
        drop(d);
        if ("no_penalty".equals(call.kind)) penaltyGone(g.id);
    }

    /** The decision's WHY folding down from under it, in the board's look, while it is still the one out. */
    private void showWhy(Drop d) {
        if (!running || small || !strip.isOut() || strip.drop() != d || d.why == null) return;
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) strip.getLayoutParams();
        float u = unit() * TEXT;
        boolean left = boardUp() && sideLeft;
        int tk = boardUp() ? nearTuck() : 0;
        int width = VarWhy.widthFor(u, sl.width - tk);
        int height = why.set(bug.stripLook(), d.why, u, width, left);
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) why.getLayoutParams();
        l.width = width;
        l.height = height;
        l.leftMargin = left ? sl.leftMargin + sl.width - tk - width : sl.leftMargin + tk;
        l.topMargin = sl.topMargin + sl.height;
        why.setLayoutParams(l);
        if (changed != null) changed.run();
        why.open();
    }

    /**
     * ESPN's commentary as the board read it (main thread): a VAR check or decision for its own game not seen before, on
     * the board once the picture has reached the read (st): the data has it before the picture shows it.
     */
    private void varFromEspn(Game g, List<VarExplained.Seen> all, com.gathin.broadcast.AsSeen.Stamp st) {
        List<VarExplained.Seen> fresh = VarExplained.fresh(g.id, all);
        boolean own = g.id != null && g.id.equals(lastId) && mode == FOOTBALL_MODE;
        if (!own || !running || !varExplained(c) || !shows(c, FOOTBALL) || fresh.isEmpty()) return;
        varHeld.add(g.id, st, () -> sayVar(g, fresh));
    }

    /**
     * FotMob's live commentary as the board read it (main thread): a VAR check or decision for its own game not seen
     * before (none from before the board came up), once the picture has reached the read. A quick check that leaves the
     * referee's call as it was is written up there and nowhere else: neither ESPN's commentary nor FotMob's events have it.
     */
    private void varFromTicker(Game g, List<VarExplained.Seen> all, com.gathin.broadcast.AsSeen.Stamp st) {
        List<VarExplained.Seen> fresh = VarExplained.fresh(g.id + "|ltc", all);
        boolean own = g.id != null && g.id.equals(lastId) && mode == FOOTBALL_MODE;
        if (!own || !running || !varExplained(c) || !shows(c, FOOTBALL) || fresh.isEmpty()) return;
        varHeld.add(g.id, st, () -> sayVar(g, fresh));
    }

    /** VAR's checks and decisions read before, out of the board now the picture has reached them (still its own game). */
    private void sayVar(Game g, List<VarExplained.Seen> fresh) {
        boolean own = g.id != null && g.id.equals(lastId) && mode == FOOTBALL_MODE;
        if (!own || !running || !varExplained(c) || !shows(c, FOOTBALL)) return;
        for (VarExplained.Seen s : fresh) {
            if (s.call != null) {
                VarExplained.enrich(s.call, VarExplained.fotmobVars(g.id));
                varDecisionOut(g, s.call);
            } else if (s.about != null) {
                varCheckOut(g, s.about);
            }
        }
    }

    /**
     * FotMob's read for the board's own game (main thread): a check FotMob has under way (its scan along the board without
     * VAR explained), and a decision only it has; on the board once the picture has reached the read (st).
     */
    private void varFromFotMob(Game g, FotMob.Match m, com.gathin.broadcast.AsSeen.Stamp st) {
        List<String[]> fresh = VarExplained.freshFotmob(g.id, m.vars);
        boolean pending = m.varPending;
        List<String[]> reds = new ArrayList<>(m.reds);
        if (!pending && fresh.isEmpty()) return;
        varHeld.add(g.id, st, () -> {
            if (!running || !g.id.equals(lastId) || mode != FOOTBALL_MODE) return;
            if (!varExplained(c)) {
                if (pending) bug.varCheck(8_000L);
                return;
            }
            if (!shows(c, FOOTBALL)) return;
            long now = System.currentTimeMillis();
            if (pending) varCheckOut(g, VarExplained.noCheck());
            for (String[] e : fresh) {
                VarExplained.Call call = VarExplained.fromFotMob(e, reds);
                if (call == null || VarExplained.decidedLately(g.id, now) && !VarExplained.checking(g.id, now)) continue;
                varDecisionOut(g, call);
            }
        });
    }

    /** For the tests and the previews: an alert dropped now. */
    void show(Drop d) {
        drop(d);
    }

    // ---- test notifications (the player's options, PlayerTests): each moment on demand, nothing kept ----------------

    /** A test's made-up game, shown while nothing else is on the board; how long the board stays up for a test at least. */
    private Game testGame;
    private long testUntil, addedFrom, addedUntil;
    private static final long TEST_MS = 12_000L, ADDED_MS = 12_000L, OPEN_MS = 700L;

    /** Whether a test keeps the board up with its made-up game: a while, and until what it put out is back in. */
    private boolean testing(long now) {
        if (testGame == null || testUntil == 0) return false;
        if (now < testUntil || dropping || stats.isOut() || lineups.isOut() || bug.bandLeft(now) > 0) return true;
        testUntil = 0;
        return false;
    }

    /** The game a test is shown on: the board's own when it shows a football game, else a made-up one (TestAlerts). */
    private Game testGame(long now) {
        Game g = mode == FOOTBALL_MODE ? screen.game(now) : null;
        if (g != null) return g;
        if (testGame == null) testGame = com.gathin.tvhomephone.sports.TestAlerts.sample(c, null);
        return testGame;
    }

    /** A test on the board: at once when it is up, else once the made-up game has come up on it. False: not now. */
    private boolean test(Runnable r) {
        if (!running || small || hidden) return false;
        long now = System.currentTimeMillis();
        testGame(now);
        testUntil = Math.max(testUntil, now + TEST_MS);
        if (boardUp()) {
            r.run();
            return true;
        }
        draw(now);
        h.postDelayed(() -> {
            if (running && !small) r.run();
        }, OPEN_MS);
        return true;
    }

    /** Tests: a goal for side (1 home, 2 away) on the board with the goal sound chosen, then its scorer beside it. */
    boolean testGoal(int side) {
        if (bug.bandLeft(System.currentTimeMillis()) > 0) return false;
        return test(() -> {
            long now = System.currentTimeMillis();
            Game g = testGame(now);
            bug.goal(side);
            com.gathin.broadcast.Sfx.goal(c, PlayerIntro.goalSound(c));
            if (changed != null) changed.run();
            h.postDelayed(() -> testScorer(g, side), bug.bandLeft(now) + 2_000L);
        });
    }

    /** Tests: a goal's scorer beside the board, standing up out of it when he has a photo. */
    boolean testScorer(int side) {
        return test(() -> testScorer(testGame(System.currentTimeMillis()), side));
    }

    /** The side's latest scorer in g (else a made-up one) beside the board, as the board captions a goal. */
    private void testScorer(Game g, int side) {
        if (!running || small) return;
        String[] goal = lastGoal(g, side);
        if (goal == null) goal = new String[]{"TEST|scorer", side == 1 ? "A. Player" : "B. Player", "67'", side == 1 ? "home" : "away", ""};
        Drop d = goalDrop(g, goal);
        d.homeCrest = bug.homeCrest;
        d.awayCrest = bug.awayCrest;
        enrich(d, g, goal[1], "home".equals(goal[3]) ? g.homeName : g.awayName);
    }

    /** Tests: the game's stats out beside the board, one after another. False while something else is out. */
    boolean testStats() {
        if (dropping || stats.isOut() || lineups.isOut()) return false;
        return test(() -> {
            if (dropping || stats.isOut() || lineups.isOut()) return;
            showStats(testStats(mode == FOOTBALL_MODE && lastId.equals(statsFor) ? statsNow() : new HashMap<>()));
        });
    }

    /**
     * Pure: the stats a test shows: the game's own at half time's moment when they have been read, else made-up ones,
     * one of each kind a stat can be (a bar, the form, two places, one line).
     */
    static List<String[]> testStats(Map<String, String[]> by) {
        List<String[]> pick = statsFor("ht", by);
        if (!pick.isEmpty()) return pick;
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"Possession", "58%", "42%"});
        out.add(new String[]{"Shots", "12", "7"});
        out.add(new String[]{"Form", "WWDLW", "LDWWD", "form"});
        out.add(new String[]{"League table", "4th · 31 pts", "9th · 22 pts", "text"});
        out.add(new String[]{"Player of the match", "C. Player · 2 goals", "", "one"});
        return out;
    }

    /** Tests: a line-ups alert's card beside the board (after what is out now, as a real one). */
    boolean testCard(Events.Alert a) {
        if (a == null || a.summary == null || a.game == null) return false;
        return test(() -> lineupsCard(a));
    }

    /** Tests: a substitution beside the board (OFF, then ON), then three at once (made up). */
    boolean testSub() {
        return test(() -> {
            Game g = testGame(System.currentTimeMillis());
            Drop d = subDrop(g, new String[]{"Substitution", "71", "1", "Alex Player", "", "Ben Example", ""});
            Drop three = subDrop(g, java.util.Arrays.asList(
                    new String[]{"Substitution", "78", "2", "Carl Sample", "", "Dan van Test", ""},
                    new String[]{"Substitution", "78", "2", "Eli Demo", "", "Finn Trial", ""},
                    new String[]{"Substitution", "78", "2", "Gus Mock", "", "Hal Pilot", ""}));
            for (Drop x : new Drop[]{d, three}) {
                x.own = mode != F1_MODE;
                x.homeCrest = bug.homeCrest;
                x.awayCrest = bug.awayCrest;
                drop(x);
            }
        });
    }

    /**
     * Tests: VAR checking, its line scanning across the board; with VAR explained on, its check out of the board ("Checking a
     * penalty", possible handball) and 8 s later its decision turning over in its place with the WHY folding down (made up).
     */
    boolean testVar() {
        return test(() -> {
            if (!varExplained(c) || mode == F1_MODE) {
                bug.varCheck(8_000L);
                if (changed != null) changed.run();
                return;
            }
            long now = System.currentTimeMillis();
            Game g = testGame(now);
            g.id = "test-var";
            varCheckOut(g, new String[]{"penalty", VarExplained.HANDBALL, "Sam Okafor"});
            // As if the check began 85 s before its decision (the test shows it for 8 s).
            VarExplained.checkBegan(g.id, now - 77_000L);
            if (changed != null) changed.run();
            h.postDelayed(() -> {
                VarExplained.Call call = new VarExplained.Call();
                call.kind = "no_penalty";
                call.reason = VarExplained.HANDBALL;
                call.player = "Sam Okafor";
                call.onField = 1;
                varDecisionOut(g, call);
            }, 8_000L);
        });
    }

    /** Tests: the added time put up beside the clock for a few seconds (the board red, steady), and its alert beside it. */
    boolean testAdded() {
        if (System.currentTimeMillis() < addedUntil) return false;
        return test(() -> {
            long now = System.currentTimeMillis();
            addedFrom = now;
            addedUntil = now + ADDED_MS;
            testUntil = Math.max(testUntil, addedUntil);
            if (changed != null) changed.run();
            draw(now);
            Drop d = addedDrop(testGame(now), "+4");
            if (d != null && mode != F1_MODE) {
                d.own = true;
                d.homeCrest = bug.homeCrest;
                d.awayCrest = bug.awayCrest;
                drop(d);
            }
        });
    }

    /** Tests: a made-up penalty shootout under the board, a kick every few seconds, into sudden death. */
    boolean testPens() {
        if (System.currentTimeMillis() < pensTestUntil) return false;
        return test(() -> {
            long now = System.currentTimeMillis();
            pensShownFor = "";
            pensTestFrom = now;
            pensTestUntil = now + (Shootout.TEST_HOME.length + Shootout.TEST_AWAY.length + 2) * Shootout.TEST_EVERY_MS;
            testUntil = Math.max(testUntil, pensTestUntil);
            if (changed != null) changed.run();
            draw(now);
        });
    }

    /** Tests: a goal's milestone ("3rd of the season"), then a yellow card's ("One more and he's banned"), beside the board. */
    boolean testMilestones() {
        return test(() -> {
            Game g = testGame(System.currentTimeMillis());
            Drop goal = goalDrop(g, new String[]{"TEST|milestone", "A. Player", "67'", "home", ""});
            goal.tag = Milestones.goal(1, 3, false);
            goal.tagKind = BoardDrop.TAG_GOAL;
            Drop card = yellowDrop(g, new String[]{"71'", "Yellow Card", "B. Player", "away", "TEST|yellow", ""}, Milestones.ONE_MORE);
            for (Drop x : new Drop[]{goal, card}) {
                x.own = mode != F1_MODE;
                x.homeCrest = bug.homeCrest;
                x.awayCrest = bug.awayCrest;
                drop(x);
            }
        });
    }

    private void testAdded(OverlayBug.Look l, long now) {
        addedTest(l, (now - addedFrom) / 1000);
        // As in a game (AddedTime): no +N tab of the board's own while its panel is out with Added time stays on.
        l.added = boardAdded(l.added, addedStays(c) && strip.isOut() && strip.drop() != null && strip.drop().addedMins > 0);
    }

    /**
     * Pure: a look in the added time's test, secs into it: "+4" beside the clock, the clock going on from its half's end
     * (the first half's before the 45th minute, else the second's), the score alive as in added time.
     */
    static void addedTest(OverlayBug.Look l, long secs) {
        int m = minuteOfClock(l.clock);
        int end = m >= 0 && m < 45 ? 45 : 90;
        long s = Math.max(0, secs);
        l.clock = String.format(Locale.UK, "%d:%02d", end + s / 60, s % 60);
        l.added = "+4";
        l.live = true;
        l.stoppage = true;
    }

    List<Drop> waiting() {
        return new ArrayList<>(drops);
    }
}
