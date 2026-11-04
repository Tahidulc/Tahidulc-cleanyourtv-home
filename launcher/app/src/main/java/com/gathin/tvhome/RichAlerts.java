package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Three alerts after a goal, in the score cards' own look (ScoreCard's navy striped band, pale streaked stage, navy
 * words, crests and score, the remote's Back key), on Home and over the apps:
 * <ul>
 * <li>Live table: a goal in a league game moves a club up or down the table (ESPN's standings with every game under
 * way added in): on Home the bar ("Man United up to 4th") grows down into five rows of the table around the club, its
 * row lit and moving to its new place; over an app the same as a card in the top right.</li>
 * <li>Goal Rush: three or more followed games under way at once and a goal in one of them: a strip of every live
 * game's score, the one that has just scored flashing GOAL, "N goals in the last 10 mins" at its start; over an app
 * above the foot of the screen, on Home just above the dock. In for twelve seconds, then away.</li>
 * <li>Player milestone: a hat-trick (three goals by one player in a game): on Home the bar with his photo, over an
 * app a card in the top right with his photo, the club's crest, his goals' minutes and his league goals this season
 * (ESPN's athlete statistics).</li>
 * </ul>
 * Each waits for the goal's own card to have gone (and any other pop-up), so they come one after another. Theme
 * "rich_alerts" (on unless turned off). The adb test hooks (test_table, test_goalrush, test_milestone, "auto") use
 * today's real games, standings, crests and players.
 */
final class RichAlerts {
    private RichAlerts() { }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final int NAVY = 0xFF151A63, INK = 0xFF0A0D33, INK_SOFT = 0xFF2B3170, LIT = 0xFF1B2591;
    private static final int UP = 0xFF1DB954, DOWN = 0xFFE5484D;
    private static final PathInterpolator SLIDE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** How long each stays up: the table (it has more to read), the strip, the milestone. */
    private static final long TABLE_MS = 13_000L, RUSH_MS = 12_000L, MILESTONE_MS = 11_000L;
    /** The goal's own card comes a minute after the goal (GoalWatchService); these after that one. */
    private static final long AFTER_CARD_MS = 60_000L + 11_000L;
    private static float k = 1f;

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    private static void scale(Context c) {
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
    }

    private static boolean on(Context c) {
        return Config.themeFlag(c, "rich_alerts", true);
    }

    /** Whether Home is in front (the bar forms), else an app (the card and the strip over it). */
    private static boolean onHome() {
        return GoalWatchService.laneHome && GoalWatchService.homeInFront;
    }

    // ---- the window over everything ---------------------------------------------------------------------------------

    /** The card or strip showing (one at a time), when it came up, and the key it lets through to the app. */
    private static FrameLayout host;
    private static long shownAt;
    private static final int[] passing = {0};
    private static Runnable closeLater;
    /** The table under Home's bar (a second window, folding up with the bar). */
    private static FrameLayout tablePanel;

    static boolean isShowing() {
        return host != null;
    }

    /** Back while a card or the strip shows: it shrinks away towards its Back key. True when it took the key. */
    static boolean key(KeyEvent e) {
        if (host == null || e.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        if (PopupMotion.passOn(e, shownAt, passing)) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) main.post(() -> PopupMotion.press(host));
        else if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> closeHost(true));
        return true;
    }

    /**
     * Whether this kind may show now for a game in the competition with this slug (the Control Centre's Notifications:
     * its kind's switch and its competition's), logging it when not.
     */
    private static boolean allowed(Context c, String kind, String slug, String what) {
        boolean home = NotifyPrefs.homeNow();
        if (NotifyPrefs.allowed(c, kind, home, slug)) return true;
        NotifyPrefs.held(c, kind, home, null, what, slug);
        return false;
    }

    /** Runs show once nothing else is up (the goal's card, a line-up, an older alert), at most a minute and a half on. */
    private static void whenClear(Context c, Runnable show, long since) {
        boolean busy = host != null || ScoreCard.busy() || LineupCard.isShowing() || GoalWatchService.alertShowing() || WatchCards.holds();
        if (busy && android.os.SystemClock.uptimeMillis() - since < 90_000L) {
            main.postDelayed(() -> whenClear(c, show, since), 1_500L);
            return;
        }
        show.run();
    }

    private static WindowManager.LayoutParams overlay(int w, int h, int gravity, int x, int y, String title) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        lp.x = x;
        lp.y = y;
        lp.setTitle(title);
        return lp;
    }

    private static boolean add(Context c, View v, WindowManager.LayoutParams lp) {
        try {
            c.getSystemService(WindowManager.class).addView(v, lp);
            return true;
        } catch (RuntimeException e) {
            AlertLog.note(c, "rich alert could not be shown: " + e);
            return false;
        }
    }

    private static void remove(View v) {
        try {
            v.getContext().getSystemService(WindowManager.class).removeView(v);
        } catch (RuntimeException ignored) {
            // already gone
        }
    }

    /** The card or strip shown as host for ms, coming in its own way (strip: up from below; card: in from the right). */
    private static void showHost(Context c, FrameLayout win, View content, WindowManager.LayoutParams lp, boolean strip, long ms) {
        showHost(c, win, content, lp, strip, ms, 0);
    }

    /**
     * As above; over Settings or a permission screen, where Android hides every app's pop-ups, it waits two seconds at a
     * time for it to close, two minutes at most (a follow-up to a goal), then is left out.
     */
    private static void showHost(Context c, FrameLayout win, View content, WindowManager.LayoutParams lp, boolean strip, long ms, int waits) {
        String hiding = onHome() ? null : OverlayGuard.hiding(c);
        if (hiding != null) {
            if (waits * 2_000L >= OverlayGuard.FOLLOW_UP_MS) {
                AlertLog.note(c, "not shown: " + OverlayGuard.name(hiding) + " stayed open (Android hides pop-ups over it)");
                return;
            }
            if (waits == 0) AlertLog.note(c, "waiting: " + OverlayGuard.name(hiding) + " is open (Android hides pop-ups over it)");
            main.postDelayed(() -> showHost(c, win, content, lp, strip, ms, waits + 1), 2_000L);
            return;
        }
        closeHost(false);
        if (!add(c, win, lp)) return;
        host = win;
        win.setTag(strip ? "strip" : "card");
        shownAt = android.os.SystemClock.uptimeMillis();
        if (strip) {
            content.setTranslationY(px(110));
            content.setAlpha(0f);
            content.animate().translationY(0f).alpha(1f).setDuration(340).setInterpolator(SLIDE).start();
        } else {
            content.setTranslationX(px(40));
            content.setAlpha(0f);
            content.animate().translationX(0f).alpha(1f).setDuration(320).setInterpolator(SLIDE).start();
        }
        closeLater = () -> closeHost(false);
        main.postDelayed(closeLater, ms);
    }

    private static void closeHost(boolean byBack) {
        FrameLayout v = host;
        host = null;
        if (closeLater != null) main.removeCallbacks(closeLater);
        closeLater = null;
        if (v == null) return;
        final boolean[] went = {false};
        Runnable gone = () -> {
            if (went[0]) return;
            went[0] = true;
            remove(v);
        };
        main.postDelayed(gone, 900L);
        if (byBack) {
            PopupMotion.backOut(v, v.getWidth(), 0f, gone);
            return;
        }
        View content = v.getChildAt(0);
        if (content == null) { gone.run(); return; }
        if ("strip".equals(v.getTag())) content.animate().translationY(px(110)).alpha(0f).setDuration(260).withEndAction(gone).start();
        else content.animate().translationX(px(40)).alpha(0f).setDuration(240).withEndAction(gone).start();
    }

    // ---- the parts, in the score cards' look ------------------------------------------------------------------------

    private static TextView text(Context c, CharSequence s, float size, int colour, int weight) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * k);
        v.setTextColor(colour);
        v.setTypeface(ScoreCard.outfit(c, weight));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static ImageView image(Context c, Bitmap b) {
        ImageView v = new ImageView(c);
        if (b != null) {
            b.setHasMipMap(true);
            v.setImageBitmap(b);
        }
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    /** The navy band with its fine diagonal stripes (the score cards' and the line-up card's). */
    private static android.graphics.drawable.Drawable band() {
        return SkyStyle.band(0xFF1B2591, 0xFF0D1360, px(14), px(3));
    }

    /** The pale stage with its soft streaks. */
    private static android.graphics.drawable.Drawable stage() {
        return SkyStyle.stage(px(40), px(26));
    }

    private static View backKey(Context c, int size) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), size));
        v.setTag(PopupMotion.BACK_KEY);
        return v;
    }

    /** A box clipped to the score cards' square corners. */
    private static void square(View v) {
        float r = SkyStyle.corner(v.getContext());
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** The slanted label (LIVE TABLE, HAT-TRICK) on its colour, as the score cards' GOAL and RED. */
    private static View label(Context c, String words, String small, int colour, int h) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(px(14), 0, px(22), 0);
        v.setBackground(new ScoreCard.Slant(colour, px(14), false));
        v.addView(text(c, words, 23, 0xFFFFFFFF, 900));
        if (small != null) {
            TextView s = text(c, small, 10, 0xD9FFFFFF, 700);
            s.setLetterSpacing(0.16f);
            v.addView(s);
        }
        v.setMinimumHeight(h);
        return v;
    }

    /** The score block: both crests and the score (and the minute or FT under it), on navy. */
    private static View scoreBlock(Context c, Bitmap home, Bitmap away, String score, String minute, int h, int crest) {
        LinearLayout v = new LinearLayout(c);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(px(12), 0, px(12), 0);
        v.setBackgroundColor(NAVY);
        v.addView(image(c, home), new LinearLayout.LayoutParams(px(crest), px(crest)));
        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        TextView s = text(c, score, 24, 0xFFFFFFFF, 900);
        s.setFontFeatureSettings("tnum");
        mid.addView(s);
        if (minute != null && !minute.isEmpty()) mid.addView(text(c, minute, 11, 0xCCFFFFFF, 700));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.leftMargin = px(10);
        ml.rightMargin = px(10);
        v.addView(mid, ml);
        v.addView(image(c, away), new LinearLayout.LayoutParams(px(crest), px(crest)));
        v.setMinimumHeight(h);
        return v;
    }

    // ---- ESPN's standings, and the table with the live games in it --------------------------------------------------

    static final class Club {
        String id, name, shortName, logo;
        int p, gf, ga, pts, rank;
        Bitmap crest;

        Club copy() {
            Club x = new Club();
            x.id = id;
            x.name = name;
            x.shortName = shortName;
            x.logo = logo;
            x.p = p;
            x.gf = gf;
            x.ga = ga;
            x.pts = pts;
            x.rank = rank;
            x.crest = crest;
            return x;
        }
    }

    /** The competitions with a table (a cup has none). */
    private static final Set<String> LEAGUES = new HashSet<>(Arrays.asList("eng.1", "eng.2", "sco.1", "esp.1", "ita.1", "ger.1",
            "fra.1", "uefa.champions", "uefa.europa", "uefa.europa.conf"));

    private static final Map<String, Object[]> tables = new HashMap<>();

    /** A competition's table from ESPN (kept five minutes), each club by its ESPN id; null when it could not be read. */
    private static Map<String, Club> standings(String slug) {
        synchronized (tables) {
            Object[] had = tables.get(slug);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < 5 * 60_000L) {
                @SuppressWarnings("unchecked") Map<String, Club> t = (Map<String, Club>) had[1];
                return copy(t);
            }
        }
        try {
            JSONObject root = new JSONObject(HomeV2.get("https://site.api.espn.com/apis/v2/sports/soccer/" + slug + "/standings"));
            JSONArray children = root.optJSONArray("children");
            JSONArray entries = children != null && children.length() > 0
                    ? children.getJSONObject(0).getJSONObject("standings").getJSONArray("entries")
                    : root.getJSONObject("standings").getJSONArray("entries");
            Map<String, Club> t = new HashMap<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                JSONObject team = e.getJSONObject("team");
                Club x = new Club();
                x.id = team.optString("id");
                x.name = team.optString("displayName");
                x.shortName = team.optString("shortDisplayName", x.name);
                JSONArray logos = team.optJSONArray("logos");
                x.logo = logos != null && logos.length() > 0 ? logos.getJSONObject(0).optString("href", null) : null;
                Map<String, Integer> st = new HashMap<>();
                JSONArray stats = e.optJSONArray("stats");
                for (int s = 0; stats != null && s < stats.length(); s++) {
                    JSONObject o = stats.getJSONObject(s);
                    st.put(o.optString("name"), (int) Math.round(o.optDouble("value", 0)));
                }
                x.p = st.getOrDefault("gamesPlayed", 0);
                x.gf = st.getOrDefault("pointsFor", 0);
                x.ga = st.getOrDefault("pointsAgainst", 0);
                x.pts = st.getOrDefault("points", 0);
                x.rank = st.getOrDefault("rank", i + 1);
                t.put(x.id, x);
            }
            synchronized (tables) { tables.put(slug, new Object[]{System.currentTimeMillis(), t}); }
            return copy(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Club> copy(Map<String, Club> t) {
        Map<String, Club> out = new HashMap<>();
        for (Map.Entry<String, Club> e : t.entrySet()) out.put(e.getKey(), e.getValue().copy());
        return out;
    }

    /** A result added to the table (sign 1) or taken out of it (-1). */
    private static void apply(Map<String, Club> t, String home, String away, int hs, int as, int sign) {
        Club h = t.get(home), a = t.get(away);
        if (h == null || a == null || hs < 0 || as < 0) return;
        h.p += sign;
        a.p += sign;
        h.gf += sign * hs;
        h.ga += sign * as;
        a.gf += sign * as;
        a.ga += sign * hs;
        h.pts += sign * (hs > as ? 3 : hs == as ? 1 : 0);
        a.pts += sign * (as > hs ? 3 : hs == as ? 1 : 0);
    }

    /** The table in order: points, goal difference, goals scored; each club's place set. */
    private static List<Club> sorted(Collection<Club> clubs) {
        List<Club> out = new ArrayList<>(clubs);
        out.sort((x, y) -> x.pts != y.pts ? y.pts - x.pts : (x.gf - x.ga) != (y.gf - y.ga) ? (y.gf - y.ga) - (x.gf - x.ga)
                : x.gf != y.gf ? y.gf - x.gf : String.valueOf(x.name).compareTo(String.valueOf(y.name)));
        for (int i = 0; i < out.size(); i++) out.get(i).rank = i + 1;
        return out;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The side the goal counts for (an own goal: the other side). */
    private static boolean forHome(String[] goal) {
        boolean home = !"away".equals(goal[3]);
        return home != "og".equals(goal[4]);
    }

    /** What the table alert shows. */
    static final class Table {
        ScoresTicker.Game g;
        String[] goal;
        Club club;
        int before, after;
        List<Club> rows = new ArrayList<>();
        Bitmap league, homeCrest, awayCrest;
        String headline, line;
    }

    /**
     * The table before and after this goal in g: ESPN's standings (a finished game already in them; a live one, and
     * every other live game of the competition, added at its score), the goal taken back out for "before". The club
     * that moved (a followed one first, else the scorers, else the side that dropped); null when no club moved, or
     * when the table could not be read.
     */
    private static Table tableFor(Context c, ScoresTicker.Game g, String[] goal, List<ScoresTicker.Game> games, boolean evenIfStill) {
        if (g.slug == null || !LEAGUES.contains(g.slug) || g.homeId == null || g.awayId == null) return null;
        Map<String, Club> base = standings(g.slug);
        if (base == null || !base.containsKey(g.homeId) || !base.containsKey(g.awayId)) return null;
        int hs = parse(g.homeScore), as = parse(g.awayScore);
        if (hs < 0 || as < 0) return null;
        boolean home = forHome(goal);
        int bh = home ? Math.max(0, hs - 1) : hs, ba = home ? as : Math.max(0, as - 1);
        Map<String, Club> after = copy(base), before = copy(base);
        for (ScoresTicker.Game x : games) {
            if (x.state != 0 || x == g || !g.slug.equals(x.slug) || (x.id != null && x.id.equals(g.id))) continue;
            apply(after, x.homeId, x.awayId, parse(x.homeScore), parse(x.awayScore), 1);
            apply(before, x.homeId, x.awayId, parse(x.homeScore), parse(x.awayScore), 1);
        }
        if (g.state == 0) {
            apply(after, g.homeId, g.awayId, hs, as, 1);
            apply(before, g.homeId, g.awayId, bh, ba, 1);
        } else {
            // Finished: its result is in ESPN's table already; "before" has it at the score before the goal instead.
            apply(before, g.homeId, g.awayId, hs, as, -1);
            apply(before, g.homeId, g.awayId, bh, ba, 1);
        }
        List<Club> a = sorted(after.values());
        sorted(before.values());
        Set<String> followed = new HashSet<>(Arrays.asList(SyncHub.starredClubs(c)));
        String scorer = home ? g.homeId : g.awayId, other = home ? g.awayId : g.homeId;
        String pick = null;
        for (String id : new String[]{scorer, other}) {
            if (followed.contains(id) && before.get(id).rank != after.get(id).rank) { pick = id; break; }
        }
        if (pick == null && before.get(scorer).rank != after.get(scorer).rank) pick = scorer;
        if (pick == null && before.get(other).rank != after.get(other).rank) pick = other;
        if (pick == null) {
            if (!evenIfStill) return null;
            pick = scorer;
        }
        Table t = new Table();
        t.g = g;
        t.goal = goal;
        t.club = after.get(pick);
        t.before = before.get(pick).rank;
        t.after = t.club.rank;
        int from = Math.max(0, Math.min(t.after - 3, a.size() - 5));
        for (int i = from; i < Math.min(a.size(), from + 5); i++) t.rows.add(a.get(i));
        String who = t.club.shortName != null && !t.club.shortName.isEmpty() ? t.club.shortName : t.club.name;
        t.headline = who + (t.after < t.before ? " up to " : t.after > t.before ? " down to " : " stay ") + ordinal(t.after);
        t.line = surname(goal[1]) + " " + goal[2] + " · live table";
        int crestPx = px(96);
        for (Club x : t.rows) if (x.logo != null) x.crest = ScoresTicker.cachedLogo(c, x.logo, crestPx);
        t.league = leagueLogo(c, g);
        t.homeCrest = g.homeLogo == null ? null : ScoreCard.onNavy(ScoresTicker.cachedLogo(c, g.homeLogo, crestPx));
        t.awayCrest = g.awayLogo == null ? null : ScoreCard.onNavy(ScoresTicker.cachedLogo(c, g.awayLogo, crestPx));
        return t;
    }

    private static Bitmap leagueLogo(Context c, ScoresTicker.Game g) {
        String url = g.leagueLogo != null ? g.leagueLogo : GoalWatchService.leagueLogoFor(g);
        return url == null ? null : ScoresTicker.cachedLogo(c, url, px(96));
    }

    private static String ordinal(int n) {
        int t = n % 100;
        String suf = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suf;
    }

    private static String surname(String full) {
        if (full == null) return "";
        String f = full.trim();
        int sp = f.lastIndexOf(' ');
        return sp > 0 ? f.substring(sp + 1) : f;
    }

    // ---- the table: on Home under the bar, over an app as a card ------------------------------------------------

    private static void showTable(GoalWatchService s, Table t) {
        scale(s);
        whenClear(s, () -> {
            if (!allowed(s, "table", t.g.slug, "live table " + t.headline)) return;
            AlertLog.note(s, "live table shown " + (onHome() ? "on Home" : "over the app") + ": " + t.headline);
            if (onHome()) tableBar(s, t); else tableCard(s, t);
        }, android.os.SystemClock.uptimeMillis());
    }

    /** On Home: the score cards' bar (LIVE TABLE, the headline, the score), five rows of the table opening under it. */
    private static void tableBar(GoalWatchService s, Table t) {
        // The crests, the competition's logo and where to watch are read off the main thread.
        io.execute(() -> {
            ScoreCard.Data d = s.footballData(t.g);
            main.post(() -> tableBar(s, t, d));
        });
    }

    private static void tableBar(GoalWatchService s, Table t, ScoreCard.Data d) {
        d.kind = "table";
        d.label = "LIVE";
        d.labelSmall = "TABLE";
        d.labelColour = NAVY;
        d.headline = t.headline;
        d.line = t.line;
        d.minute = t.g.state == 2 ? "FT" : t.goal[2];
        d.crest = null;
        d.showMs = TABLE_MS;
        d.onShown = () -> main.postDelayed(() -> { if (onHome() && !PopupFrame.isShowing()) tablePanel(s, t); }, 380L);
        d.onClosed = RichAlerts::closeTablePanel;
        ScoreCard.show(s, d);
    }

    /** The rows: place, crest, club, played, goal difference, points; the club's lit, moving into its new place. */
    private static LinearLayout rows(Context c, Table t, int w, boolean onNavyHead) {
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(16), 0, px(16), 0);
        head.addView(text(c, "", 12, INK_SOFT, 700), new LinearLayout.LayoutParams(px(36), -2));
        head.addView(text(c, "CLUB", 12, INK_SOFT, 700), new LinearLayout.LayoutParams(0, -2, 1f));
        for (String col : new String[]{"P", "GD", "PTS"}) {
            TextView v = text(c, col, 12, INK_SOFT, 700);
            v.setGravity(Gravity.END);
            head.addView(v, new LinearLayout.LayoutParams(px(col.equals("PTS") ? 56 : 46), -2));
        }
        head.addView(new View(c), new LinearLayout.LayoutParams(px(52), 1));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, px(28));
        list.addView(head, hl);
        int rowH = px(40);
        for (Club x : t.rows) {
            boolean mine = x.id.equals(t.club.id);
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px(16), 0, px(16), 0);
            if (mine) r.setBackgroundColor(LIT);
            int ink = mine ? 0xFFFFFFFF : INK;
            TextView pos = text(c, String.valueOf(x.rank), 19, ink, 800);
            pos.setFontFeatureSettings("tnum");
            r.addView(pos, new LinearLayout.LayoutParams(px(36), -2));
            r.addView(image(c, mine ? ScoreCard.onNavy(x.crest) : x.crest), new LinearLayout.LayoutParams(px(28), px(28)));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
            nl.leftMargin = px(12);
            r.addView(text(c, x.name, 18, ink, mine ? 800 : 600), nl);
            int gd = x.gf - x.ga;
            for (String v : new String[]{String.valueOf(x.p), (gd > 0 ? "+" : "") + gd, String.valueOf(x.pts)}) {
                TextView tv = text(c, v, 18, ink, v.equals(String.valueOf(x.pts)) ? 900 : 600);
                tv.setGravity(Gravity.END);
                tv.setFontFeatureSettings("tnum");
                r.addView(tv, new LinearLayout.LayoutParams(px(v.equals(String.valueOf(x.pts)) ? 56 : 46), -2));
            }
            // Its move: an arrow and by how many places (up green, down red), on the club's row only.
            TextView mv = text(c, "", 15, mine ? (t.after < t.before ? 0xFF7CF0A6 : 0xFFFF9EA2) : INK, 900);
            mv.setGravity(Gravity.END);
            if (mine && t.after != t.before) mv.setText((t.after < t.before ? "▲ +" : "▼ −") + Math.abs(t.before - t.after));
            r.addView(mv, new LinearLayout.LayoutParams(px(52), -2));
            list.addView(r, new LinearLayout.LayoutParams(-1, rowH));
            if (mine && t.after != t.before) {
                // In from its old place (where it sat before the goal, clamped to the five rows), to its new one.
                int shift = Math.max(-2, Math.min(2, t.before - t.after));
                r.setTranslationY(shift * rowH);
                r.setElevation(px(2));
                r.animate().translationY(0f).setStartDelay(650).setDuration(520).setInterpolator(SLIDE).start();
            }
        }
        list.setPadding(0, px(4), 0, px(10));
        return list;
    }

    private static void tablePanel(Context c, Table t) {
        closeTablePanel();
        int w = px(780);
        FrameLayout win = new FrameLayout(c);
        win.setClipChildren(true);
        FrameLayout box = new FrameLayout(c);
        box.setBackground(stage());
        square(box);
        box.addView(rows(c, t, w, false), new FrameLayout.LayoutParams(-1, -2));
        win.addView(box, new FrameLayout.LayoutParams(w, -2));
        int barH = px(52);
        WindowManager.LayoutParams lp = overlay(w, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START,
                ScoresTicker.corner() ? ScoresTicker.laneLeft : (c.getResources().getDisplayMetrics().widthPixels - w) / 2,
                (ScoresTicker.corner() ? Math.max(0, ScoresTicker.laneCentre - barH / 2) : px(14)) + barH + px(3), "RichAlertsTable");
        if (!add(c, win, lp)) return;
        tablePanel = win;
        // Grows down out of the bar: drawn from under its foot.
        box.setTranslationY(-px(260));
        box.animate().translationY(0f).setDuration(380).setInterpolator(SLIDE).start();
    }

    private static void closeTablePanel() {
        FrameLayout v = tablePanel;
        tablePanel = null;
        if (v == null) return;
        View box = v.getChildAt(0);
        if (box == null) { remove(v); return; }
        box.animate().translationY(-box.getHeight() - px(10)).setDuration(240).setInterpolator(SLIDE).withEndAction(() -> remove(v)).start();
        main.postDelayed(() -> remove(v), 700L);
    }

    /** Over an app: a card in the top right, the band with LIVE TABLE and the score on top, the five rows under it. */
    private static void tableCard(Context c, Table t) {
        int w = px(660), headH = px(64), margin = px(10);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(stage());
        square(card);
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackground(band());
        if (t.league != null) {
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(40), px(40));
            ll.leftMargin = px(14);
            ll.rightMargin = px(6);
            head.addView(image(c, t.league), ll);
        }
        head.addView(label(c, "LIVE", "TABLE", NAVY, headH), new LinearLayout.LayoutParams(-2, headH));
        head.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(scoreBlock(c, t.homeCrest, t.awayCrest, (t.g.homeScore == null ? "0" : t.g.homeScore) + "–" + (t.g.awayScore == null ? "0" : t.g.awayScore),
                t.g.state == 2 ? "FT" : t.goal[2], headH, 36), new LinearLayout.LayoutParams(-2, headH));
        card.addView(head, new LinearLayout.LayoutParams(-1, headH));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(px(18), px(12), px(18), px(6));
        words.addView(text(c, t.headline, 23, INK, 800));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.topMargin = px(4);
        words.addView(text(c, t.line, 14, INK_SOFT, 600), sl);
        card.addView(words, new LinearLayout.LayoutParams(-1, -2));
        card.addView(rows(c, t, w, true), new LinearLayout.LayoutParams(-1, -2));
        cornerCard(c, card, w, margin, TABLE_MS);
    }

    /**
     * A card in the bottom left corner, where every pop-up over the apps sits (the goal's, the red card's, the
     * line-ups'; Goal Rush alone keeps the middle of the foot), its Back key on its corner, for ms. Over Ambient mode,
     * above its bar along the foot.
     */
    private static void cornerCard(Context c, View card, int w, int margin, long ms) {
        FrameLayout win = new FrameLayout(c);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, -2);
        cl.setMargins(margin, margin, margin, margin);
        win.addView(card, cl);
        win.addView(backKey(c, 2 * margin), new FrameLayout.LayoutParams(2 * margin, 2 * margin, Gravity.TOP | Gravity.END));
        WindowManager.LayoutParams lp = overlay(w + 2 * margin, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START,
                px(24), px(AmbientScene.showing() ? AmbientScene.ALERT_FOOT : 24), "RichAlertsCard");
        showHost(c, win, card, lp, false, ms);
    }

    // ---- Goal Rush: the strip of every live game --------------------------------------------------------------------

    /** The goals seen lately, by when (for "N goals in the last 10 mins"), and when the strip last came up. */
    private static final List<Long> recent = new ArrayList<>();
    private static long lastRush;

    static final class Chip {
        Bitmap home, away;
        String score, minute;
        boolean scored;
    }

    /** The strip, for a goal in the competition with this slug (its switch in Notifications holds it). */
    private static void showRush(GoalWatchService s, List<Chip> chips, int goals, String slug) {
        scale(s);
        whenClear(s, () -> {
            if (!allowed(s, "goalrush", slug, "goal rush, " + goals + " goals")) return;
            AlertLog.note(s, "goal rush shown " + (onHome() ? "on Home" : "over the app") + ": " + chips.size() + " games, " + goals + " goals");
            rushStrip(s, chips, goals);
        }, android.os.SystemClock.uptimeMillis());
    }

    private static void rushStrip(Context c, List<Chip> chips, int goals) {
        int w = px(1400), h = px(64);
        LinearLayout strip = new LinearLayout(c);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setBackground(stage());
        square(strip);
        // The band: GOAL RUSH and how many goals in the last ten minutes.
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(20), 0, px(26), 0);
        head.setBackground(band());
        head.addView(text(c, "GOAL RUSH", 22, 0xFFFFFFFF, 900));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
        gl.topMargin = px(3);
        head.addView(text(c, goals + (goals == 1 ? " goal" : " goals") + " in the last 10 mins", 12.5f, 0xD9FFFFFF, 600), gl);
        strip.addView(head, new LinearLayout.LayoutParams(-2, h));
        // Every live game: both crests and the score, the minute under it; the one that has just scored lit.
        LinearLayout games = new LinearLayout(c);
        games.setGravity(Gravity.CENTER_VERTICAL);
        for (Chip x : chips) {
            FrameLayout cell = new FrameLayout(c);
            LinearLayout chip = new LinearLayout(c);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(px(12), 0, px(12), 0);
            int ink = x.scored ? 0xFFFFFFFF : INK;
            if (x.scored) chip.setBackgroundColor(NAVY);
            chip.addView(image(c, x.scored ? ScoreCard.onNavy(x.home) : x.home), new LinearLayout.LayoutParams(px(28), px(28)));
            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setGravity(Gravity.CENTER);
            TextView sc = text(c, x.score, 19, ink, 900);
            sc.setFontFeatureSettings("tnum");
            mid.addView(sc);
            mid.addView(text(c, x.minute, 10.5f, x.scored ? 0xCCFFFFFF : INK_SOFT, 700));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
            ml.leftMargin = px(8);
            ml.rightMargin = px(8);
            chip.addView(mid, ml);
            chip.addView(image(c, x.scored ? ScoreCard.onNavy(x.away) : x.away), new LinearLayout.LayoutParams(px(28), px(28)));
            cell.addView(chip, new FrameLayout.LayoutParams(-2, h));
            if (x.scored) {
                // GOAL, flashing three times over the game that has just scored, then staying lit.
                TextView tag = text(c, "GOAL", 10, 0xFFFFFFFF, 900);
                tag.setLetterSpacing(0.12f);
                tag.setPadding(px(6), px(2), px(6), px(2));
                GradientDrawable tg = new GradientDrawable();
                tg.setColor(0xFFE3061B);
                tg.setCornerRadius(px(3));
                tag.setBackground(tg);
                FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                tl.topMargin = px(1);
                cell.addView(tag, tl);
                android.animation.ObjectAnimator blink = android.animation.ObjectAnimator.ofFloat(tag, View.ALPHA, 1f, 0.15f, 1f);
                blink.setDuration(520);
                blink.setRepeatCount(3);
                blink.setStartDelay(500);
                blink.start();
            }
            View rule = new View(c);
            rule.setBackgroundColor(0x330A0D33);
            games.addView(cell, new LinearLayout.LayoutParams(-2, h));
            games.addView(rule, new LinearLayout.LayoutParams(Math.max(1, px(1.5f)), px(34)));
        }
        LinearLayout.LayoutParams gs = new LinearLayout.LayoutParams(0, h, 1f);
        gs.leftMargin = px(6);
        strip.addView(games, gs);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(24), px(24));
        kl.leftMargin = px(10);
        kl.rightMargin = px(14);
        strip.addView(backKey(c, px(24)), kl);
        FrameLayout win = new FrameLayout(c);
        win.addView(strip, new FrameLayout.LayoutParams(w, h));
        // Over an app above the foot (clear of the score cards in the bottom left); on Home just above the dock.
        WindowManager.LayoutParams lp = overlay(w, h, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, px(onHome() ? 150 : 140), "RichAlertsRush");
        showHost(c, win, strip, lp, true, RUSH_MS);
    }

    /** The chips of these games, the one with the goal lit. */
    private static List<Chip> chips(Context c, List<ScoresTicker.Game> games, ScoresTicker.Game scoredIn) {
        List<Chip> out = new ArrayList<>();
        int crestPx = px(80);
        for (ScoresTicker.Game x : games) {
            Chip ch = new Chip();
            ch.home = x.homeLogo == null ? null : ScoresTicker.cachedLogo(c, x.homeLogo, crestPx);
            ch.away = x.awayLogo == null ? null : ScoresTicker.cachedLogo(c, x.awayLogo, crestPx);
            ch.score = (x.homeScore == null ? "0" : x.homeScore) + "–" + (x.awayScore == null ? "0" : x.awayScore);
            String clock = x.state == 0 ? ScoresTicker.liveClock(x) : null;
            ch.minute = x.state == 2 ? "FT" : clock == null ? "" : clock.replaceAll(":\\d\\d$", "'");
            ch.scored = x == scoredIn || (scoredIn != null && x.id != null && x.id.equals(scoredIn.id));
            out.add(ch);
        }
        return out;
    }

    // ---- the milestone ----------------------------------------------------------------------------------------------

    static final class Milestone {
        ScoresTicker.Game g;
        String who, label, small, team;
        List<String> minutes = new ArrayList<>();
        boolean home;
        Bitmap photo, crest, league, homeCrest, awayCrest;
        String season;
    }

    /** The player's goals in g (not own goals): their minutes. */
    private static List<String> goalsBy(ScoresTicker.Game g, String who) {
        List<String> out = new ArrayList<>();
        for (String[] x : g.goals) if (who != null && who.equals(x[1]) && !"og".equals(x[4])) out.add(x[2]);
        return out;
    }

    private static Milestone milestone(GoalWatchService s, ScoresTicker.Game g, String who, boolean home, String label, String small) {
        Milestone m = new Milestone();
        m.g = g;
        m.who = who;
        m.home = home;
        m.label = label;
        m.small = small;
        m.minutes = goalsBy(g, who);
        m.team = home ? (g.homeName != null ? g.homeName : g.home) : (g.awayName != null ? g.awayName : g.away);
        try {
            m.photo = s.playerPhoto(who, m.team, home ? g.homeColor : g.awayColor);
        } catch (RuntimeException e) {
            m.photo = null;
        }
        int crestPx = px(96);
        String crestUrl = home ? g.homeLogo : g.awayLogo;
        m.crest = crestUrl == null ? null : ScoresTicker.cachedLogo(s, crestUrl, crestPx);
        m.homeCrest = g.homeLogo == null ? null : ScoreCard.onNavy(ScoresTicker.cachedLogo(s, g.homeLogo, crestPx));
        m.awayCrest = g.awayLogo == null ? null : ScoreCard.onNavy(ScoresTicker.cachedLogo(s, g.awayLogo, crestPx));
        m.league = leagueLogo(s, g);
        m.season = seasonLine(g, who, home);
        return m;
    }

    /**
     * His league goals this season, from ESPN's athlete statistics ("9 Premier League goals this season"); the
     * athlete found by name in the game's line-ups. Null when either could not be read.
     */
    private static String seasonLine(ScoresTicker.Game g, String who, boolean home) {
        if (g.id == null || g.slug == null || who == null) return null;
        try {
            JSONObject sum = new JSONObject(HomeV2.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug + "/summary?event=" + g.id));
            String athlete = null;
            JSONArray rosters = sum.optJSONArray("rosters");
            for (int i = 0; rosters != null && i < rosters.length() && athlete == null; i++) {
                JSONArray r = rosters.getJSONObject(i).optJSONArray("roster");
                for (int j = 0; r != null && j < r.length(); j++) {
                    JSONObject a = r.getJSONObject(j).optJSONObject("athlete");
                    if (a != null && PlayerPhotos.sameName(a.optString("displayName"), who)) { athlete = a.optString("id"); break; }
                }
            }
            if (athlete == null || athlete.isEmpty()) return null;
            JSONObject st = new JSONObject(HomeV2.get("https://site.web.api.espn.com/apis/common/v3/sports/soccer/athletes/" + athlete + "/stats?region=gb"));
            JSONArray cats = st.optJSONArray("categories");
            if (cats == null || cats.length() == 0) return null;
            JSONObject cat = cats.getJSONObject(0);
            JSONArray labels = cat.optJSONArray("labels");
            JSONArray seasons = cat.optJSONArray("statistics");
            if (labels == null || seasons == null || seasons.length() == 0) return null;
            int gi = -1, ai = -1;
            for (int i = 0; i < labels.length(); i++) {
                if ("G".equals(labels.optString(i))) gi = i;
                if ("A".equals(labels.optString(i))) ai = i;
            }
            if (gi < 0) return null;
            JSONObject latest = seasons.getJSONObject(0);
            for (int i = 1; i < seasons.length(); i++) {
                JSONObject x = seasons.getJSONObject(i);
                if (x.optJSONObject("season") != null && latest.optJSONObject("season") != null
                        && x.getJSONObject("season").optInt("year") > latest.getJSONObject("season").optInt("year")) latest = x;
            }
            JSONArray vals = latest.optJSONArray("stats");
            if (vals == null) return null;
            int goals = parse(vals.optString(gi)), assists = ai >= 0 ? parse(vals.optString(ai)) : -1;
            if (goals < 0) return null;
            String league = "league";
            JSONObject leagues = st.optJSONObject("leagues");
            if (leagues != null && leagues.keys().hasNext()) {
                JSONObject l = leagues.optJSONObject(leagues.keys().next());
                if (l != null) league = l.optString("abbreviation", l.optString("displayName", "league"));
            }
            return goals + " " + league + (goals == 1 ? " goal" : " goals") + (assists > 0 ? " · " + assists + (assists == 1 ? " assist" : " assists") : "") + " this season";
        } catch (Exception e) {
            return null;
        }
    }

    private static void showMilestone(GoalWatchService s, Milestone m) {
        scale(s);
        whenClear(s, () -> {
            if (!allowed(s, "milestone", m.g.slug, "milestone " + m.label + " " + m.who)) return;
            AlertLog.note(s, "milestone shown " + (onHome() ? "on Home" : "over the app") + ": " + m.label + " " + m.who);
            if (onHome()) milestoneBar(s, m); else milestoneCard(s, m);
        }, android.os.SystemClock.uptimeMillis());
    }

    private static int kitLabel(int kit) {
        if (kit == 0) return NAVY;
        double lum = (0.299 * ((kit >> 16) & 0xFF) + 0.587 * ((kit >> 8) & 0xFF) + 0.114 * (kit & 0xFF)) / 255.0;
        return lum > 0.6 ? NAVY : kit | 0xFF000000;
    }

    private static String minutesLine(Milestone m) {
        return TextUtils.join(" · ", m.minutes);
    }

    /** On Home: the score cards' bar, the label in his side's colour, his photo and name, his goals and season. */
    private static void milestoneBar(GoalWatchService s, Milestone m) {
        io.execute(() -> {
            ScoreCard.Data d = s.footballData(m.g);
            main.post(() -> milestoneBar(s, m, d));
        });
    }

    private static void milestoneBar(GoalWatchService s, Milestone m, ScoreCard.Data d) {
        d.kind = "milestone";
        d.label = m.label;
        d.labelSmall = m.small;
        d.labelColour = kitLabel(m.home ? m.g.homeColor : m.g.awayColor);
        String[] name = splitName(m.who);
        d.first = name[0];
        d.last = name[1];
        d.line = minutesLine(m) + (m.season != null ? " · " + m.season : "");
        d.photo = m.photo;
        d.crest = m.home ? d.homeCrest : d.awayCrest;
        d.minute = m.g.state == 2 ? "FT" : null;
        d.showMs = MILESTONE_MS;
        ScoreCard.show(s, d);
    }

    private static String[] splitName(String full) {
        String f = full == null ? "" : full.trim();
        int sp = f.lastIndexOf(' ');
        return sp > 0 ? new String[]{f.substring(0, sp), f.substring(sp + 1)} : new String[]{"", f};
    }

    /** Over an app: a card in the top right, the band with the competition and his club, his photo, the label, his name. */
    private static void milestoneCard(Context c, Milestone m) {
        int w = px(640), h = px(196), bandW = px(84), margin = px(10);
        FrameLayout card = new FrameLayout(c);
        card.setBackground(stage());
        square(card);
        LinearLayout band = new LinearLayout(c);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setGravity(Gravity.CENTER);
        band.setBackground(band());
        if (m.league != null) band.addView(image(c, m.league), new LinearLayout.LayoutParams(px(40), px(40)));
        if (m.crest != null) {
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(50), px(50));
            cl.topMargin = px(14);
            band.addView(image(c, ScoreCard.onNavy(m.crest)), cl);
        }
        card.addView(band, new FrameLayout.LayoutParams(bandW, h));
        if (m.photo != null) {
            // Head and shoulders, standing on the card's foot.
            ImageView ph = image(c, m.photo);
            ph.setScaleType(ImageView.ScaleType.FIT_END);
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(px(170), h - px(8), Gravity.BOTTOM);
            pl.leftMargin = bandW + px(4);
            card.addView(ph, pl);
        }
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        View lab = label(c, m.label, m.small, kitLabel(m.home ? m.g.homeColor : m.g.awayColor), px(46));
        words.addView(lab, new LinearLayout.LayoutParams(-2, px(46)));
        String[] name = splitName(m.who);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
        fl.topMargin = px(10);
        words.addView(text(c, name[0], 16, INK_SOFT, 500), fl);
        words.addView(text(c, name[1].toUpperCase(Locale.UK), 30, INK, 800));
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.topMargin = px(6);
        String score = (m.g.homeScore == null ? "0" : m.g.homeScore) + "–" + (m.g.awayScore == null ? "0" : m.g.awayScore);
        String vs = m.home ? (m.g.awayName != null ? m.g.awayName : m.g.away) : (m.g.homeName != null ? m.g.homeName : m.g.home);
        words.addView(text(c, minutesLine(m) + "  ·  " + score + " v " + vs + (m.g.state == 2 ? " (FT)" : ""), 14.5f, INK_SOFT, 600), ll);
        if (m.season != null) {
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.topMargin = px(3);
            words.addView(text(c, m.season, 14.5f, INK, 700), sl);
        }
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(-1, h);
        wl.leftMargin = bandW + px(m.photo != null ? 180 : 20);
        wl.rightMargin = px(16);
        card.addView(words, wl);
        card.setMinimumHeight(h);
        cornerCard(c, card, w, margin, MILESTONE_MS);
    }

    // ---- the real triggers: a goal seen (GoalWatchService's poll, on the main thread) -------------------------------

    /**
     * A goal has just been seen in g (its own card comes in a minute): the table it changes, the Goal Rush strip when
     * three or more followed games are under way, and a hat-trick, each after the goal's card.
     */
    static void goalSeen(GoalWatchService s, ScoresTicker.Game g, String[] goal, List<ScoresTicker.Game> games) {
        if (!on(s) || g == null || goal == null) return;
        scale(s);
        long now = System.currentTimeMillis();
        int lately;
        synchronized (recent) {
            recent.add(now);
            recent.removeIf(t -> now - t > 10 * 60_000L);
            lately = recent.size();
        }
        boolean anywhere = GoalWatchService.followsAnywhere(s, g);
        List<ScoresTicker.Game> live = new ArrayList<>();
        // The live games of the competitions switched on in Notifications (the rest are not shown, so not counted).
        for (ScoresTicker.Game x : games) {
            if (x.state == 0 && GoalWatchService.followsAnywhere(s, x) && NotifyPrefs.competitionOn(s, x.slug)) live.add(x);
        }
        List<ScoresTicker.Game> all = new ArrayList<>(games);
        main.postDelayed(() -> io.execute(() -> {
            // Over an app only for the games the goal alerts follow there; Home's own games only on Home.
            if (!anywhere && !onHome()) return;
            Table t = tableFor(s, g, goal, all, false);
            if (t != null) main.post(() -> showTable(s, t));
        }), AFTER_CARD_MS);
        if (live.size() >= 3 && now - lastRush > 2 * 60_000L && anywhere) {
            lastRush = now;
            int goals = lately;
            main.postDelayed(() -> io.execute(() -> {
                List<Chip> chips = chips(s, live, g);
                main.post(() -> showRush(s, chips, goals, g.slug));
            }), AFTER_CARD_MS + 1_000L);
        }
        if (!"og".equals(goal[4]) && goalsBy(g, goal[1]).size() == 3) {
            boolean home = !"away".equals(goal[3]);
            main.postDelayed(() -> io.execute(() -> {
                if (!anywhere && !onHome()) return;
                Milestone m = milestone(s, g, goal[1], home, "HAT-TRICK", null);
                main.post(() -> showMilestone(s, m));
            }), AFTER_CARD_MS + 2_000L);
        }
    }

    // ---- adb test hooks, with today's real games --------------------------------------------------------------------

    /** test_table, test_goalrush, test_milestone ("auto"): today's real games, tables, crests and players. */
    static void test(GoalWatchService s, Intent intent) {
        boolean table = intent.hasExtra("test_table"), rush = intent.hasExtra("test_goalrush"), mile = intent.hasExtra("test_milestone");
        if (!table && !rush && !mile) return;
        scale(s);
        io.execute(() -> {
            List<ScoresTicker.Game> games = todays(s);
            AlertLog.note(s, "rich alert test: " + games.size() + " of today's games");
            if (table) testTable(s, games);
            if (rush) testRush(s, games);
            if (mile) testMilestone(s, games);
        });
    }

    /** Today's games of the followed competitions (the Champions League's first), any that have started. */
    private static List<ScoresTicker.Game> todays(Context c) {
        List<ScoresTicker.Game> out = new ArrayList<>();
        List<ScoresTicker.Game> loaded = ScoresTicker.load(c.getResources().getStringArray(R.array.goal_alert_leagues));
        for (ScoresTicker.Game g : loaded) if (g.state == 0 || g.state == 2) out.add(g);
        out.sort((a, b) -> ("uefa.champions".equals(a.slug) ? 0 : 1) - ("uefa.champions".equals(b.slug) ? 0 : 1));
        return out;
    }

    /** The game and goal that moved a club furthest in its table (a followed club's first). */
    private static void testTable(GoalWatchService s, List<ScoresTicker.Game> games) {
        Table best = null;
        int bestMove = -1;
        for (ScoresTicker.Game g : games) {
            if (g.goals.isEmpty() || g.slug == null || !LEAGUES.contains(g.slug)) continue;
            String[] last = g.goals.get(g.goals.size() - 1);
            Table t = tableFor(s, g, last, games, true);
            if (t == null) continue;
            int move = Math.abs(t.before - t.after);
            if (move > bestMove) {
                best = t;
                bestMove = move;
            }
        }
        if (best == null) { AlertLog.note(s, "rich alert test: no table to show"); return; }
        Table t = best;
        main.post(() -> showTable(s, t));
    }

    private static void testRush(GoalWatchService s, List<ScoresTicker.Game> games) {
        if (games.isEmpty()) return;
        // The game with the latest goal is the one that has just scored; the goals in the ten minutes before it.
        ScoresTicker.Game scored = null;
        int latest = -1;
        for (ScoresTicker.Game g : games) {
            for (String[] x : g.goals) {
                int m = minuteOf(x[2]);
                if (m > latest) { latest = m; scored = g; }
            }
        }
        int goals = 0;
        for (ScoresTicker.Game g : games) for (String[] x : g.goals) if (minuteOf(x[2]) >= latest - 10) goals++;
        List<ScoresTicker.Game> shown = new ArrayList<>(games.subList(0, Math.min(8, games.size())));
        if (scored != null && !shown.contains(scored)) shown.set(shown.size() - 1, scored);
        List<Chip> chips = chips(s, shown, scored);
        int n = Math.max(1, goals);
        String slug = scored == null ? null : scored.slug;
        main.post(() -> showRush(s, chips, n, slug));
    }

    private static int minuteOf(String minute) {
        if (minute == null) return -1;
        String[] parts = minute.replace("'", "").split("\\+");
        int m = parse(parts[0]);
        if (parts.length > 1) m += Math.max(0, parse(parts[1]));
        return m;
    }

    /** Tonight's best: a hat-trick if there was one, else the most goals by one player (a brace, test only). */
    private static void testMilestone(GoalWatchService s, List<ScoresTicker.Game> games) {
        ScoresTicker.Game bestG = null;
        String bestWho = null;
        boolean bestHome = true;
        int bestN = 0;
        for (ScoresTicker.Game g : games) {
            Map<String, Integer> count = new HashMap<>();
            Map<String, Boolean> side = new HashMap<>();
            for (String[] x : g.goals) {
                if ("og".equals(x[4]) || x[1] == null || x[1].isEmpty()) continue;
                count.merge(x[1], 1, Integer::sum);
                side.put(x[1], !"away".equals(x[3]));
            }
            for (Map.Entry<String, Integer> e : count.entrySet()) {
                if (e.getValue() > bestN) {
                    bestN = e.getValue();
                    bestWho = e.getKey();
                    bestG = g;
                    bestHome = Boolean.TRUE.equals(side.get(e.getKey()));
                }
            }
        }
        if (bestG == null) { AlertLog.note(s, "rich alert test: no scorer today"); return; }
        Milestone m = milestone(s, bestG, bestWho, bestHome, bestN >= 3 ? "HAT-TRICK" : bestN == 2 ? "BRACE" : "GOAL", null);
        main.post(() -> showMilestone(s, m));
    }
}
