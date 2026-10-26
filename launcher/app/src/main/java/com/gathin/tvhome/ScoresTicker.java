package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.LruCache;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;

/**
 * Today's football on the greeting's line: one glass box per game, from ESPN's public scoreboards for the leagues
 * in config.xml score_leagues ("eng.1|PL"). As many boxes as fit make a page; with more than one page, each stays
 * PAGE_MS and then flips up to the next. Scores refresh every minute while a game is on, every 10 minutes
 * otherwise, and nothing runs while Home is not in front. Hidden on a day without games.
 */
final class ScoresTicker {
    /** What OK and Down on a game box do: open the configured app, go back to the top bar. */
    interface Host {
        void onScoresOpen();
        void onScoresDown();
    }

    private static final long LIVE_MS = 60_000L;
    private static final long IDLE_MS = 10 * 60_000L;
    private static final long PAGE_MS = 8_000L;
    private static final long FLIP_MS = 450L;
    private static final String FEED = "https://site.api.espn.com/apis/site/v2/sports/soccer/%s/scoreboard?dates=%s";

    /** One game, ready to draw. */
    static final class Game {
        String id, league, home, away, homeScore, awayScore, detail;
        /** ESPN's team ids, and the league slug the game came from (for its match summary). */
        String homeId, awayId, slug;
        /** The teams' colours (0 when ESPN has none), and while live the match clock in seconds as read at clockAt. */
        int homeColor, awayColor;
        /** The clubs' full names ("Tottenham Hotspur"), for finding the game in a TV guide. */
        String homeName, awayName;
        /** adb tests only: the moment to look up in the TV guide instead of the kick-off. */
        long tvAt;
        double clockSec;
        long clockAt;
        boolean halftime;
        /** Logo URLs: the competition (its dark-background version) and both crests; null when ESPN has none. */
        String leagueLogo, homeLogo, awayLogo;
        int state;   // 0 live, 1 upcoming, 2 finished: the order they are shown in
        long kickoff;
        /** Every goal so far: {unique key, scorer, minute, "home"/"away", "pen"/"og"/""}. */
        final List<String[]> goals = new ArrayList<>();
        /** Every red card: {unique key, player, minute, "home"/"away", ""}. */
        final List<String[]> reds = new ArrayList<>();
        /** An F1 session instead of a game: the leading drivers as {code, team logo url}, else a line of text. */
        List<String[]> drivers;
        String text;
    }

    /** An F1 box beside the football (a theme's "f1_scores_bar"): the live session, else the last one, else the next. */
    private boolean f1;

    void showF1(boolean on) {
        f1 = on;
    }

    /** The goal alert that drops over the line; off when GoalWatchService shows its pop-ups on Home too. */
    private boolean alertsOn = true;

    void showAlerts(boolean on) {
        alertsOn = on;
    }

    private static final long ALERT_MS = 10_000L;
    /** Goals already announced (or already scored when Home first read the scores). */
    private final java.util.Set<String> seenGoals = new java.util.HashSet<>();
    private boolean primed;
    private final java.util.ArrayDeque<Object[]> alerts = new java.util.ArrayDeque<>();
    private View alertView;

    private final Context context;
    private final float density;
    private final FrameLayout box;
    private final String[] leagues;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Game> games = new ArrayList<>();
    private final List<List<Game>> pages = new ArrayList<>();
    private int page;
    private LinearLayout shownRow;
    private boolean running;
    private final Runnable refresh = this::fetch;
    private final Runnable flip = this::nextPage;
    /** Logos at LOGO px, in memory and on disk (they change once a season, not once a minute). */
    private final LruCache<String, Bitmap> logos = new LruCache<>(80);
    private final ExecutorService logoIo = Executors.newFixedThreadPool(2);
    private final Map<String, List<ImageView>> waiting = new HashMap<>();
    private final File logoDir;
    private final int logo;

    private final Host host;

    ScoresTicker(Context context, FrameLayout root, float density, String[] leagues, Host host) {
        this.host = host;
        this.context = context;
        this.density = density;
        this.leagues = leagues;
        logo = dp(20);
        logoDir = new File(context.getCacheDir(), "logos");
        box = new FrameLayout(context);
        box.setClipChildren(true);
        box.setVisibility(View.GONE);
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(box, new FrameLayout.LayoutParams(0, dp(30), Gravity.TOP | Gravity.LEFT));
        measure.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
                context.getResources().getDisplayMetrics()));
        measure.setTypeface(Typeface.DEFAULT_BOLD);   // measure as if all bold: the widest case
    }

    /** Puts the boxes at (x, centreY) in root coordinates, up to right; called once the greeting is laid out. */
    void place(int x, int centreY, int right) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        int w = Math.max(0, right - x);
        if (lp.width == w && lp.leftMargin == x && lp.topMargin == centreY - lp.height / 2) return;
        lp.width = w;
        lp.leftMargin = x;
        lp.topMargin = centreY - lp.height / 2;
        box.setLayoutParams(lp);
        paginate();
    }

    void start() {
        if (leagues.length == 0) return;
        running = true;
        main.removeCallbacks(refresh);
        fetch();
        schedulePage();
    }

    void stop() {
        running = false;
        main.removeCallbacks(refresh);
        main.removeCallbacks(flip);
    }

    void close() {
        stop();
        io.shutdownNow();
        logoIo.shutdownNow();
    }

    // ---- data ------------------------------------------------------------------------------------

    private void fetch() {
        main.removeCallbacks(refresh);
        io.execute(() -> {
            List<Game> found = load(leagues);
            if (f1) {
                Game race = f1Game(F1Results.weekend(null));
                if (race != null) found.add(race);
            }
            found.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state)
                    : a.state == 2 ? Long.compare(b.kickoff, a.kickoff) : Long.compare(a.kickoff, b.kickoff));
            boolean live = false;
            for (Game g : found) live |= g.state == 0;
            final boolean anyLive = live;
            main.post(() -> {
                games = found;
                // New goals since the last read become alerts; the first read only learns what is already in.
                for (Game g : found) {
                    for (String[] goal : g.goals) {
                        if (seenGoals.add(goal[0]) && primed && alertsOn) alerts.add(new Object[]{g, goal});
                    }
                }
                if (!found.isEmpty()) primed = true;
                paginate();
                showNextAlert();
                if (running) main.postDelayed(refresh, anyLive ? LIVE_MS : IDLE_MS);
            });
        });
    }

    /** Today's games for these leagues ("eng.1|PL"), unsorted; a league that fails to load is left out. */
    static List<Game> load(String[] leagues) {
        ZoneId zone = ZoneId.systemDefault();
        String date = LocalDate.now(zone).format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Game> found = new ArrayList<>();
        for (String entry : leagues) {
            String[] parts = entry.split("\\|", 2);
            String slug = parts[0].trim();
            String tag = parts.length > 1 ? parts[1].trim() : slug;
            try {
                JSONObject board = new JSONObject(get(String.format(FEED, slug, date)));
                String leagueLogo = leagueLogo(board);
                JSONArray events = board.optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    Game g = parse(events.getJSONObject(i), tag, zone);
                    if (g == null) continue;
                    g.leagueLogo = leagueLogo;
                    g.slug = slug;
                    found.add(g);
                }
            } catch (Exception ignored) {
                // one league failing leaves the others
            }
        }
        return found;
    }

    private static Game parse(JSONObject e, String tag, ZoneId zone) throws Exception {
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject type = c.getJSONObject("status").getJSONObject("type");
        JSONObject home = null, away = null;
        JSONArray teams = c.getJSONArray("competitors");
        for (int t = 0; t < teams.length(); t++) {
            JSONObject team = teams.getJSONObject(t);
            if ("home".equals(team.optString("homeAway"))) home = team; else away = team;
        }
        if (home == null || away == null) return null;
        Game g = new Game();
        String homeId = home.getJSONObject("team").optString("id"), id = e.optString("id");
        g.id = id;
        JSONArray details = c.optJSONArray("details");
        for (int d = 0; details != null && d < details.length(); d++) {
            JSONObject x = details.getJSONObject(d);
            boolean red = x.optBoolean("redCard") || (x.optJSONObject("type") != null
                    && x.getJSONObject("type").optString("text").toLowerCase(java.util.Locale.ROOT).contains("red card"));
            if (!x.optBoolean("scoringPlay") && !red) continue;
            JSONArray who = x.optJSONArray("athletesInvolved");
            String scorer = who != null && who.length() > 0 ? who.getJSONObject(0).optString("displayName") : "";
            String minute = x.optJSONObject("clock") != null ? x.getJSONObject("clock").optString("displayValue") : "";
            String side = homeId.equals(x.optJSONObject("team") != null ? x.getJSONObject("team").optString("id") : "") ? "home" : "away";
            String kind = x.optBoolean("ownGoal") ? "og" : x.optBoolean("penaltyKick") ? "pen" : "";
            if (red) g.reds.add(new String[]{"R|" + id + "|" + minute + "|" + scorer, scorer, minute, side, ""});
            else g.goals.add(new String[]{id + "|" + minute + "|" + scorer, scorer, minute, side, kind});
        }
        g.league = tag;
        g.homeId = homeId;
        g.awayId = away.getJSONObject("team").optString("id");
        g.home = home.getJSONObject("team").optString("abbreviation");
        g.away = away.getJSONObject("team").optString("abbreviation");
        g.homeLogo = home.getJSONObject("team").optString("logo", null);
        g.homeName = home.getJSONObject("team").optString("displayName", null);
        g.awayName = away.getJSONObject("team").optString("displayName", null);
        g.homeColor = color(home.getJSONObject("team").optString("color", ""));
        g.awayColor = color(away.getJSONObject("team").optString("color", ""));
        if (g.awayColor == g.homeColor) g.awayColor = color(away.getJSONObject("team").optString("alternateColor", ""));
        g.awayLogo = away.getJSONObject("team").optString("logo", null);
        g.homeScore = home.optString("score", "0");
        g.awayScore = away.optString("score", "0");
        // ESPN can list a goal a refresh before it raises the score; never show fewer goals than it lists.
        int homeGoals = 0, awayGoals = 0;
        for (String[] goal : g.goals) {
            if ("og".equals(goal[4])) continue;   // which side ESPN credits an own goal to is not certain: skip them
            if ("home".equals(goal[3])) homeGoals++; else awayGoals++;
        }
        g.homeScore = String.valueOf(Math.max(homeGoals, parseInt(g.homeScore)));
        g.awayScore = String.valueOf(Math.max(awayGoals, parseInt(g.awayScore)));
        // ESPN writes "2026-10-25T14:00Z", which Instant.parse refuses (no seconds); OffsetDateTime accepts it.
        ZonedDateTime when = OffsetDateTime.parse(e.getString("date")).toInstant().atZone(zone);
        g.kickoff = when.toInstant().toEpochMilli();
        String state = type.optString("state");
        if ("in".equals(state)) {
            g.state = 0;
            g.detail = type.optString("shortDetail", "LIVE");
            g.clockSec = c.getJSONObject("status").optDouble("clock", 0);
            g.clockAt = System.currentTimeMillis();
            g.halftime = type.optString("name").contains("HALFTIME");
        } else if ("pre".equals(state)) {
            g.state = 1;
            g.detail = when.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            g.state = 2;
            g.detail = type.optString("shortDetail", "FT");
        }
        return g;
    }

    /**
     * The game's major incidents from its ESPN match summary: penalties missed or saved, VAR checks and goals ruled
     * out (goals and red cards come from the scoreboard). {unique key, what, player, minute}.
     */
    static List<String[]> incidents(Game g) {
        List<String[]> out = new ArrayList<>();
        if (g.slug == null || g.id == null) return out;
        try {
            JSONObject summary = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug
                    + "/summary?event=" + g.id));
            JSONArray events = summary.optJSONArray("keyEvents");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject k = events.getJSONObject(i);
                String type = k.optJSONObject("type") == null ? "" : k.getJSONObject("type").optString("text");
                String what = incident(type);
                if (what == null) continue;
                JSONArray who = k.optJSONArray("participants");
                JSONObject athlete = who != null && who.length() > 0 ? who.getJSONObject(0).optJSONObject("athlete") : null;
                String minute = k.optJSONObject("clock") == null ? "" : k.getJSONObject("clock").optString("displayValue");
                out.add(new String[]{"I|" + g.id + "|" + k.optString("id", type + minute), what,
                        athlete == null ? "" : athlete.optString("displayName"), minute});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** The most recent finished game of the league in the last few days, or null. */
    static Game lastFinished(String slug, String tag, int days) {
        ZoneId zone = ZoneId.systemDefault();
        for (int back = 1; back <= days; back++) {
            String date = LocalDate.now(zone).minusDays(back).format(DateTimeFormatter.BASIC_ISO_DATE);
            try {
                JSONArray events = new JSONObject(get(String.format(FEED, slug, date))).optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    Game g = parse(events.getJSONObject(i), tag, zone);
                    if (g != null && g.state == 2) { g.slug = slug; return g; }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** One side's starting eleven: {surname, headshot url, shirt number} per player, in ESPN's order. */
    static final class Side {
        String abbr, crest, formation, name;
        final List<String[]> players = new ArrayList<>();
    }

    /** Both starting line-ups from the match summary, home first; null until they are announced (about an hour before). */
    static Side[] lineups(Game g) {
        if (g.slug == null || g.id == null) return null;
        try {
            JSONObject summary = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug
                    + "/summary?event=" + g.id));
            JSONArray rosters = summary.optJSONArray("rosters");
            Side home = null, away = null;
            for (int i = 0; rosters != null && i < rosters.length(); i++) {
                JSONObject r = rosters.getJSONObject(i);
                JSONObject team = r.optJSONObject("team");
                Side side = new Side();
                side.formation = r.optString("formation", "");
                JSONArray list = r.optJSONArray("roster");
                for (int k = 0; list != null && k < list.length(); k++) {
                    JSONObject p = list.getJSONObject(k);
                    if (!p.optBoolean("starter")) continue;
                    JSONObject a = p.getJSONObject("athlete");
                    JSONObject shot = a.optJSONObject("headshot");
                    String photo = shot != null ? shot.optString("href", null)
                            : "https://a.espncdn.com/i/headshots/soccer/players/full/" + a.optString("id") + ".png";
                    String pos = p.optJSONObject("position") == null ? "" : p.getJSONObject("position").optString("abbreviation", "");
                    side.players.add(new String[]{GoalWatchService.surname(a.optString("displayName")), photo, p.optString("jersey", ""), pos,
                            a.optString("displayName")});
                }
                if (team != null && team.optString("id").equals(g.homeId)) { side.abbr = g.home; side.crest = g.homeLogo; home = side; }
                else { side.abbr = g.away; side.crest = g.awayLogo; away = side; }
                side.name = team == null ? null : team.optString("displayName", null);
            }
            if (home == null || away == null || home.players.size() < 11 || away.players.size() < 11) return null;
            return new Side[]{home, away};
        } catch (Exception e) {
            return null;
        }
    }

    /** The incidents worth a pop-up, with their icon; null for everything else (substitutions, yellow cards). */
    private static String incident(String type) {
        String t = type.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("penalty") && (t.contains("miss") || t.contains("wide") || t.contains("post") || t.contains("bar"))) return "❌  Pen missed";
        if (t.contains("penalty") && t.contains("saved")) return "🧤  Pen saved";
        if (t.contains("disallowed") || t.contains("cancelled") || t.contains("overturned")) return "🚫  Goal ruled out";
        if (t.contains("var")) return "📺  VAR";
        return null;
    }

    /** The competition's logo for dark backgrounds, else its default one. */
    private static String leagueLogo(JSONObject board) {
        JSONArray leagues = board.optJSONArray("leagues");
        JSONArray list = leagues == null || leagues.length() == 0 ? null : leagues.optJSONObject(0).optJSONArray("logos");
        String fallback = null;
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject l = list.optJSONObject(i);
            String rel = String.valueOf(l.optJSONArray("rel"));
            if (rel.contains("dark")) return l.optString("href", null);
            if (fallback == null) fallback = l.optString("href", null);
        }
        return fallback;
    }

    /**
     * The weekend's box: a session under way first (its top three and the lap), else one that finished in the last
     * twelve hours (its top three), else the next qualifying, sprint or race in the coming four days (day and time).
     */
    static Game f1Game(F1Results.Weekend w) {
        if (w == null) return null;
        long now = System.currentTimeMillis();
        F1Results.Session live = null, done = null, next = null;
        for (F1Results.Session s : w.sessions) {
            if ("in".equals(s.state)) live = s;
            else if ("post".equals(s.state) && s.counts() && now - s.start < 12 * 60 * 60_000L + 3 * 60 * 60_000L) done = s;
            else if ("pre".equals(s.state) && s.counts() && next == null && s.start - now < 4 * 24 * 60 * 60_000L) next = s;
        }
        F1Results.Session s = live != null ? live : done != null ? done : next;
        if (s == null) return null;
        Game g = new Game();
        g.id = "F1|" + s.id;
        g.league = "F1";
        g.leagueLogo = F1Results.F1_LOGO;
        g.home = s.label;
        g.kickoff = s.start;
        g.state = s == live ? 0 : s == done ? 2 : 1;
        if (s == next) {
            ZonedDateTime when = java.time.Instant.ofEpochMilli(s.start).atZone(ZoneId.systemDefault());
            boolean today = when.toLocalDate().equals(LocalDate.now());
            g.detail = when.format(DateTimeFormatter.ofPattern(today ? "HH:mm" : "EEE HH:mm", java.util.Locale.getDefault()));
            g.text = w.name.replace("Grand Prix", "GP");
        } else {
            g.drivers = new ArrayList<>();
            for (int i = 0; i < Math.min(3, s.top.size()); i++) {
                g.drivers.add(new String[]{F1Results.code(s.top.get(i)), s.logos.get(i)});
            }
            g.detail = s == live ? (s.isRace() && s.lap > 0 ? "LAP " + s.lap : "LIVE") : "FINAL";
        }
        return g;
    }

    /** "004170" to an opaque colour; 0 when it is not one. */
    static int color(String hex) {
        try { return hex.length() == 6 ? 0xFF000000 | Integer.parseInt(hex, 16) : 0; } catch (Exception e) { return 0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ---- goal alerts ------------------------------------------------------------------------------

    /** Drops the next goal alert over the scores line for ALERT_MS, then lifts it away. */
    private void showNextAlert() {
        if (alertView != null || alerts.isEmpty() || !running || box.getVisibility() != View.VISIBLE) return;
        Object[] next = alerts.poll();
        Game g = (Game) next[0];
        String[] goal = (String[]) next[1];
        boolean homeGoal = "home".equals(goal[3]);
        LinearLayout a = new LinearLayout(context);
        a.setOrientation(LinearLayout.HORIZONTAL);
        a.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColors(new int[]{0xE6143D2A, 0xE60E2A1D});
        bg.setOrientation(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        bg.setStroke(dp(1.5f), 0xFF4ADE80);
        a.setBackground(bg);
        a.setPadding(dp(14), 0, dp(14), 0);
        TextView tag = label(goalTag(this.games, g));
        a.addView(tag);
        a.addView(logoView(homeGoal ? g.homeLogo : g.awayLogo, dp(12)));
        SpannableStringBuilder line = new SpannableStringBuilder();
        line.append(goal[1]);
        line.setSpan(new StyleSpan(Typeface.BOLD), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.append("  ").append(goal[2]);
        if (!goal[4].isEmpty()) line.append(goal[4].equals("pen") ? " (pen)" : " (og)");
        int at = line.length();
        line.append("     ").append(g.home).append("  ").append(g.homeScore).append("–").append(g.awayScore)
                .append("  ").append(g.away);
        line.setSpan(new ForegroundColorSpan(0xCCFFFFFF), at, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        a.addView(label(line), margin(dp(8)));
        a.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        int h = dp(30);
        a.setTranslationY(-h);
        box.addView(a);
        alertView = a;
        a.animate().translationY(0f).setDuration(FLIP_MS).start();
        main.postDelayed(() -> a.animate().translationY(-h).alpha(0f).setDuration(FLIP_MS).withEndAction(() -> {
            box.removeView(a);
            alertView = null;
            showNextAlert();
        }).start(), ALERT_MS);
    }

    private static CharSequence goalTag(List<Game> latest, Game g) {
        // The score from the newest refresh of this game, as the alert goes up.
        for (Game n : latest) if (n.id != null && n.id.equals(g.id)) { g.homeScore = n.homeScore; g.awayScore = n.awayScore; }
        SpannableStringBuilder s = new SpannableStringBuilder("⚽");
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(0xFF4ADE80), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    // ---- pages -----------------------------------------------------------------------------------

    /** Splits the games into pages of as many boxes as fit the width, and shows the current one. */
    private void paginate() {
        pages.clear();
        int width = box.getLayoutParams().width;
        if (games.isEmpty() || width <= 0) {
            box.setVisibility(View.GONE);
            box.removeAllViews();
            shownRow = null;
            alertView = null;
            return;
        }
        int gap = dp(10);
        List<Game> current = new ArrayList<>();
        int used = 0;
        for (Game g : games) {
            int w = boxWidth(g);
            if (!current.isEmpty() && used + gap + w > width) {
                pages.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            used += (current.isEmpty() ? 0 : gap) + w;
            current.add(g);
        }
        if (!current.isEmpty()) pages.add(current);
        if (page >= pages.size()) page = 0;
        int focused = focusedIndex();
        box.setVisibility(View.VISIBLE);
        if (shownRow != null) box.removeView(shownRow);
        shownRow = row(pages.get(page));
        box.addView(shownRow, 0);   // under a goal alert, if one is showing
        if (focused >= 0) focusBox(Math.min(focused, shownRow.getChildCount() - 1));
    }

    /** Focus the first game box; false when there are no scores on screen. */
    boolean focus() {
        if (shownRow == null || box.getVisibility() != View.VISIBLE || shownRow.getChildCount() == 0) return false;
        return shownRow.getChildAt(0).requestFocus();
    }

    private int focusedIndex() {
        if (shownRow == null) return -1;
        for (int i = 0; i < shownRow.getChildCount(); i++) if (shownRow.getChildAt(i).hasFocus()) return i;
        return -1;
    }

    private void focusBox(int index) {
        if (shownRow != null && index >= 0 && index < shownRow.getChildCount()) shownRow.getChildAt(index).requestFocus();
    }

    /** Left/Right past a page's end: the previous or next page, with the focus on its nearest box. */
    private void turnPage(boolean forward) {
        if (pages.size() < 2) return;
        page = (page + (forward ? 1 : pages.size() - 1)) % pages.size();
        if (shownRow != null) box.removeView(shownRow);
        shownRow = row(pages.get(page));
        box.addView(shownRow, 0);
        focusBox(forward ? 0 : shownRow.getChildCount() - 1);
    }

    private void schedulePage() {
        main.removeCallbacks(flip);
        if (running) main.postDelayed(flip, PAGE_MS);
    }

    /** The current page slides up and out while the next one rises in from below. */
    private void nextPage() {
        // Never flip the page out from under a box the viewer is on.
        if (running && pages.size() > 1 && shownRow != null && !box.hasFocus() && alertView == null) {
            page = (page + 1) % pages.size();
            LinearLayout out = shownRow;
            LinearLayout in = row(pages.get(page));
            int h = box.getHeight() > 0 ? box.getHeight() : dp(30);
            in.setTranslationY(h);
            in.setAlpha(0f);
            box.addView(in, 0);
            out.animate().translationY(-h).alpha(0f).setDuration(FLIP_MS)
                    .withEndAction(() -> box.removeView(out)).start();
            in.animate().translationY(0f).alpha(1f).setDuration(FLIP_MS).start();
            shownRow = in;
        }
        schedulePage();
    }

    private LinearLayout row(List<Game> list) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < list.size(); i++) {
            Game g = list.get(i);
            LinearLayout b = new LinearLayout(context);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setBackgroundResource(R.drawable.glass_box);
            b.setPadding(dp(12), 0, dp(12), 0);
            b.setFocusable(true);
            final int index = i, count = list.size();
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setCornerRadius(dp(16));
            ring.setStroke(dp(2), 0xFFFFFFFF);
            ring.setColor(0x26FFFFFF);
            b.setOnFocusChangeListener((v, has) -> v.setForeground(has ? ring : null));
            b.setOnClickListener(v -> host.onScoresOpen());
            b.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case android.view.KeyEvent.KEYCODE_DPAD_DOWN: host.onScoresDown(); return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_UP: return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                        if (index == 0) turnPage(false); else focusBox(index - 1);
                        return true;
                    case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (index == count - 1) turnPage(true); else focusBox(index + 1);
                        return true;
                    default: return false;
                }
            });
            if (g.leagueLogo != null) b.addView(logoView(g.leagueLogo, 0));
            else b.addView(label(league(g)));
            if (g.drivers != null || g.text != null) {
                // F1: the session, then its top three with their team logos (or the Grand Prix, before it starts).
                b.addView(label(bold(g.home)), margin(dp(8)));
                if (g.drivers != null) {
                    for (String[] d : g.drivers) {
                        b.addView(logoView(d[1], dp(8)));
                        b.addView(label(d[0]), margin(dp(3)));
                    }
                } else {
                    b.addView(label(g.text), margin(dp(8)));
                }
            } else {
                b.addView(logoView(g.homeLogo, dp(12)));
                b.addView(label(teams(g)), margin(dp(6)));
                b.addView(logoView(g.awayLogo, dp(6)));
            }
            b.addView(label(detail(g)), margin(dp(8)));
            // Each box grows in proportion to what it holds, so a page always fills the whole line.
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), boxWidth(g));
            if (i > 0) lp.leftMargin = dp(10);
            row.addView(b, lp);
        }
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        return row;
    }

    private TextView label(CharSequence text) {
        TextView t = new TextView(context);
        t.setSingleLine(true);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        t.setIncludeFontPadding(false);
        t.setText(text);
        return t;
    }

    private static LinearLayout.LayoutParams margin(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        return lp;
    }

    private ImageView logoView(String url, int left) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(logo, logo);
        lp.leftMargin = left;
        v.setLayoutParams(lp);
        if (url != null) loadLogo(url, v);
        return v;
    }

    /** Width of one game's box: its texts, three logos and their gaps, and the padding. */
    private int boxWidth(Game g) {
        if (g.drivers != null || g.text != null) {
            float t = measure.measureText(g.home) + measure.measureText(detail(g).toString());
            int extra = dp(8 + 8);
            if (g.drivers != null) for (String[] d : g.drivers) { t += measure.measureText(d[0]); extra += logo + dp(8 + 3); }
            else { t += measure.measureText(g.text); extra += dp(8); }
            return Math.round(t) + logo + extra + dp(24) + dp(4);
        }
        float text = measure.measureText(teams(g).toString()) + measure.measureText(detail(g).toString());
        int lead = g.leagueLogo != null ? logo : Math.round(measure.measureText(league(g).toString()));
        return Math.round(text) + lead + 2 * logo + dp(12 + 6 + 6 + 8) + dp(24) + dp(4);
    }

    private static CharSequence league(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder(g.league);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(0xFFC77DFF), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static CharSequence bold(String text) {
        SpannableStringBuilder s = new SpannableStringBuilder(text);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static CharSequence teams(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder();
        if (g.state == 1) {
            s.append(g.home).append("  v  ").append(g.away);
        } else {
            s.append(g.home).append("  ");
            int at = s.length();
            s.append(g.homeScore).append("–").append(g.awayScore);
            s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append("  ").append(g.away);
        }
        return s;
    }

    private static CharSequence detail(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder(g.detail);
        s.setSpan(new ForegroundColorSpan(g.state == 0 ? 0xFF4ADE80 : 0x99FFFFFF), 0, s.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    // ---- logos -----------------------------------------------------------------------------------

    private void loadLogo(String url, ImageView into) {
        Bitmap hit = logos.get(url);
        if (hit != null) { into.setImageBitmap(hit); return; }
        List<ImageView> list = waiting.get(url);
        if (list != null) { list.add(into); return; }
        list = new ArrayList<>();
        list.add(into);
        waiting.put(url, list);
        logoIo.execute(() -> {
            Bitmap bmp = fetchLogo(url);
            main.post(() -> {
                List<ImageView> views = waiting.remove(url);
                if (bmp == null || views == null) return;
                logos.put(url, bmp);
                for (ImageView v : views) v.setImageBitmap(bmp);
            });
        });
    }

    /** From the disk cache, or from ESPN's resizer at twice the logo size, then saved for next time. */
    private Bitmap fetchLogo(String url) {
        return forDark(cachedLogo(context, url, logo));   // the scores line is dark glass
    }

    /**
     * The logo as it reads on a dark background: one that is almost black (Mercedes' F1 star) comes back white, the
     * rest unchanged.
     */
    static Bitmap forDark(Bitmap b) {
        if (b == null) return null;
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        long sum = 0;
        int n = 0;
        for (int c : px) {
            if ((c >>> 24) < 128) continue;
            sum += (((c >> 16) & 0xFF) * 30 + ((c >> 8) & 0xFF) * 59 + (c & 0xFF) * 11) / 100;
            n++;
        }
        if (n == 0 || sum / n >= 70) return b;
        for (int i = 0; i < px.length; i++) px[i] = (px[i] & 0xFF000000) | 0x00F2F4F7;
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** A crest or league logo at about size px: the shared disk cache first, then ESPN's resizer. Off the main thread. */
    static Bitmap cachedLogo(Context context, String url, int logo) {
        File logoDir = new File(context.getCacheDir(), "logos");
        // The size is part of the name: a bigger use of the same logo must not get the small copy.
        File file = new File(logoDir, Integer.toHexString((url + "@" + logo).hashCode()) + ".png");
        if (file.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(file.getPath());
            if (b != null) return b;
        }
        String small = url;
        int at = url.indexOf("/i/");
        if (url.startsWith("https://a.espncdn.com/") && at > 0) {
            small = "https://a.espncdn.com/combiner/i?img=" + url.substring(at) + "&w=" + logo * 2 + "&h=" + logo * 2;
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(small).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            byte[] data;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024) out.write(buf, 0, n);
                data = out.toByteArray();
            } finally {
                conn.disconnect();
            }
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (b != null && (logoDir.isDirectory() || logoDir.mkdirs())) {
                try (FileOutputStream fo = new FileOutputStream(file)) { fo.write(data); }
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
