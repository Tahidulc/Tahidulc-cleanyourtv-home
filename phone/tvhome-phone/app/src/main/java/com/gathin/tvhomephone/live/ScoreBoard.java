package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.F1;
import com.gathin.tvhomephone.sports.F1Live;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.Kinds;
import com.gathin.tvhomephone.sports.LiveClock;
import com.gathin.tvhomephone.sports.LiveGames;
import com.gathin.tvhomephone.sports.OnScreen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The scoreboard while something is watched, and the alerts that drop from it: in the player over the picture
 * (ScoreOverlay) and over other apps (FloatService). It shows the channel's own game when it has one, read every 15
 * seconds and shown as the stream shows it (OnScreen: the score changes as the goal goes in on the screen, the clock is
 * the screen's); on an F1 channel, the session (F1Live), in step the same way; otherwise the first followed club's game
 * on now. Goals and the other football alerts, F1 and the other sport, and breaking news drop from under it, each as
 * switched on here.
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

    static void setLogoCover(Context c, LogoCover.Look l) { prefs(c).edit().putString("logo", l.name()).apply(); }

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
    /** The scoreboard and, under it, the alert's drop. */
    final LinearLayout view;
    final OverlayBug bug;
    private final LinearLayout card;
    private final TextView chip, title, line;
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

    /** One alert's drop. */
    static final class Drop {
        String chip = "", title = "", line = "";
        int colour;
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

    ScoreBoard(Context ctx, Source src) {
        this.c = ctx;
        this.src = src;
        float d = ctx.getResources().getDisplayMetrics().density;
        view = new LinearLayout(ctx);
        view.setOrientation(LinearLayout.VERTICAL);
        bug = new OverlayBug(ctx);
        bug.style(style(ctx));
        bug.setVisibility(View.GONE);
        bug.setOnClickListener(v -> src.tapped());
        bug.setOnLongClickListener(v -> {
            src.held();
            return true;
        });
        view.addView(bug, new LinearLayout.LayoutParams(-2, Math.round(30 * d)));

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Math.round(12 * d), Math.round(9 * d), Math.round(12 * d), Math.round(10 * d));
        card.setBackground(Ui.round(ctx, 0xF20B1033, 12));
        card.setElevation(6 * d);
        chip = Ui.text(ctx, "", 10, 0xFF0A0D33, true);
        chip.setLetterSpacing(0.08f);
        chip.setPadding(Math.round(6 * d), Math.round(1 * d), Math.round(6 * d), Math.round(1 * d));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        card.addView(chip, cp);
        title = Ui.text(ctx, "", 14, 0xFFFFFFFF, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Math.round(5 * d), 0, 0);
        card.addView(title);
        line = Ui.text(ctx, "", 12, 0xFFC9CFEA, false);
        line.setMaxLines(2);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setPadding(0, Math.round(2 * d), 0, 0);
        card.addView(line);
        card.setVisibility(View.GONE);
        card.setOnClickListener(v -> {
            Drop now = (Drop) card.getTag();
            hideDrop();
            if (now != null && now.alert != null) src.alertTapped(now.alert);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(280 * d), -2);
        lp.topMargin = Math.round(6 * d);
        view.addView(card, lp);
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
        card.animate().cancel();
        card.setVisibility(View.GONE);
        card.setTag(null);
        dropping = false;
    }

    /** The small window: the board alone, no drops (they'd cover the picture). */
    void small(boolean on) {
        small = on;
        if (on) {
            drops.clear();
            card.animate().cancel();
            card.setVisibility(View.GONE);
            dropping = false;
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
                    }
                } else if (session != null) {
                    channelGame = false;
                    f1Reads.addLast(new Object[]{now, session});
                    while (f1Reads.size() > 40) f1Reads.removeFirst();
                    mode = F1_MODE;
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
        if (mode == FOOTBALL_MODE) {
            Game g = screen.game(now);
            if (g != null) {
                l = look(g, screen.clock(now));
                // A goal as the screen shows it: that side lit.
                if (g.id != null && g.id.equals(lastShownId)) {
                    if (g.homeScore > lastHome && lastHome >= 0) {
                        bug.goal(1);
                        if (changed != null) changed.run();
                    } else if (g.awayScore > lastAway && lastAway >= 0) {
                        bug.goal(2);
                        if (changed != null) changed.run();
                    }
                }
                lastShownId = g.id;
                lastHome = g.homeScore;
                lastAway = g.awayScore;
            }
        } else if (mode == F1_MODE && !f1Reads.isEmpty()) {
            long at = now - src.behindMs(false);
            Object[] pick = f1Reads.peekFirst();
            for (Object[] r : f1Reads) if ((Long) r[0] <= at) pick = r;
            l = f1Look((F1Live.Session) pick[1], at);
        }
        if (l == null) {
            bug.setVisibility(View.GONE);
            return;
        }
        bug.set(l);
        bug.setVisibility(View.VISIBLE);
    }

    private String lastShownId;

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
        drop(drop(a));
    }

    /** Pure: an alert as its drop: the kind's chip, the title without its "GOAL · " head, the first line. */
    static Drop drop(Events.Alert a) {
        Drop d = new Drop();
        d.alert = a;
        d.chip = chip(a.kind);
        d.colour = colour(a.kind);
        String t = a.title == null ? "" : a.title;
        int dot = t.indexOf(" · ");
        d.title = dot > 0 && dot < 24 && t.substring(0, dot).equals(t.substring(0, dot).toUpperCase(Locale.ROOT)) ? t.substring(dot + 3) : t;
        d.line = a.text == null ? "" : a.text.split("\n")[0];
        return d;
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
            default: return kind != null && kind.startsWith("f1") ? 0xFFE10600 : 0xFFDDE3FF;
        }
    }

    private void drop(Drop d) {
        if (small) return;
        drops.addLast(d);
        while (drops.size() > 4) drops.removeFirst();
        if (!dropping) next();
    }

    private void next() {
        Drop d = drops.pollFirst();
        if (d == null) {
            dropping = false;
            return;
        }
        dropping = true;
        if (changed != null) changed.run();
        card.setTag(d);
        chip.setText(d.chip);
        chip.setTextColor(OverlayBug.ink(d.colour));
        chip.setBackground(Ui.round(c, d.colour, 4));
        title.setText(d.title);
        line.setText(d.line);
        line.setVisibility(d.line.isEmpty() ? View.GONE : View.VISIBLE);
        card.setGravity(Gravity.START);
        // As wide as the board, and never narrower than a short headline needs.
        float dp = c.getResources().getDisplayMetrics().density;
        int w = Math.max(bug.getVisibility() == View.VISIBLE ? bug.getWidth() : 0, Math.round(260 * dp));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        lp.width = Math.min(w, Math.round(380 * dp));
        card.setLayoutParams(lp);
        card.setAlpha(0f);
        card.setTranslationY(-10 * dp);
        card.setVisibility(View.VISIBLE);
        card.animate().alpha(1f).translationY(0).setDuration(220).start();
        h.postDelayed(hide, DROP_MS);
    }

    private void hideDrop() {
        h.removeCallbacks(hide);
        if (card.getVisibility() != View.VISIBLE) {
            next();
            return;
        }
        float dp = c.getResources().getDisplayMetrics().density;
        card.animate().alpha(0f).translationY(-10 * dp).setDuration(180).withEndAction(() -> {
            card.setVisibility(View.GONE);
            card.setTag(null);
            next();
        }).start();
    }

    /** For the tests and the previews: an alert dropped now. */
    void show(Drop d) {
        drop(d);
    }

    List<Drop> waiting() {
        return new ArrayList<>(drops);
    }
}
