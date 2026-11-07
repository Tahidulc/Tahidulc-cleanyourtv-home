package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A sports row under the apps (a theme's "sports_section"): the featured game as it happens (score, the main stats,
 * the latest incidents and ESPN's commentary), the live league tables (config.xml table_leagues, standings with the
 * games in progress counted in) and the F1 drivers' standings. OK on a table or the standings turns the page.
 * Reads only while Home is in front.
 */
final class SportsSection {
    interface Host {
        void onSportsUp();
    }

    private static final long LIVE_MS = 30_000L, IDLE_MS = 5 * 60_000L;
    private static final long TABLE_LIVE_MS = 2 * 60_000L, TABLE_IDLE_MS = 30 * 60_000L, F1_MS = 6 * 60 * 60_000L;
    /** Every card's height, and the most rows a table column holds before the table takes another column. */
    private static final int CARD_DP = 254, PER_COLUMN = 12, COLUMN_DP = 196;

    private final Context ctx;
    private final float d;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService logoIo = Executors.newFixedThreadPool(2);
    private final Map<String, Bitmap> logos = new HashMap<>();
    final HorizontalScrollView view;
    private final LinearLayout strip;
    private final FrameLayout featuredCard, f1Card;
    private final List<FrameLayout> tableCards = new ArrayList<>();
    private final String[] tableLeagues;
    private boolean running;
    private long tablesAt, f1At;
    private boolean anyLive;
    private final Runnable tick = this::refresh;
    /** The featured game's pitch and its feed of plays (every 10 s while it is live), and the ticking clock. */
    private final LivePitch pitch;
    private TextView playLabel, clockView;
    private String pitchGame;
    private final Set<String> seenPlays = new HashSet<>();
    private final ExecutorService playsIo = Executors.newSingleThreadExecutor();
    private final Runnable playsTick = this::readPlays;
    private final Runnable clockTick = this::updateClock;

    // The latest data, drawn again whenever any of it changes.
    private Featured featured;
    private final Map<String, Table> tables = new HashMap<>();
    private List<String[]> drivers = new ArrayList<>();   // {pos, code, surname, points, logo url}
    private String f1Round = "";

    /** The featured game, ready to draw. */
    static final class Featured {
        ScoresTicker.Game game;
        String formationHome, formationAway;
        final List<String[]> stats = new ArrayList<>();   // {label, home, away}
        final List<String> events = new ArrayList<>();    // "67'  ⚽  Salah", newest first
        String commentary;
        /** The British channel showing it live, if one lists it. */
        TvGuide.Channel tv;
        /** Before kick-off: each side's starting eleven (surnames), once announced. */
        final List<String> xiHome = new ArrayList<>(), xiAway = new ArrayList<>();
    }

    /** One league table: {rank, abbr, logo url, played, gd, points, live "1"/"0"} per row, best first. */
    static final class Table {
        String name, logo;
        boolean live;
        final List<String[]> rows = new ArrayList<>();
    }

    SportsSection(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        d = ctx.getResources().getDisplayMetrics().density;
        tableLeagues = ctx.getResources().getStringArray(R.array.table_leagues);
        view = new HorizontalScrollView(ctx);
        view.setHorizontalScrollBarEnabled(false);
        view.setClipToPadding(false);
        view.setClipChildren(false);
        view.setFocusable(false);
        strip = new LinearLayout(ctx);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        int pad = dp(18);
        strip.setPadding(dp(2), pad, dp(40), dp(28));
        view.addView(strip, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        featuredCard = card(dp(600));
        pitch = new LivePitch(ctx, d);
        pitch.setListener(p -> { if (playLabel != null) playLabel.setText(p.label); });
        strip.addView(featuredCard);
        for (String l : tableLeagues) {
            FrameLayout c = card(dp(230));
            tableCards.add(c);
            strip.addView(c);
        }
        f1Card = card(dp(230));
        strip.addView(f1Card);
        wireKeys();
        drawFeatured();
        for (int i = 0; i < tableCards.size(); i++) drawTable(tableCards.get(i), tableLeagues[i].split("\\|", 2)[0].trim());
        drawF1();
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    boolean focus() {
        return featuredCard.requestFocus();
    }

    void start() {
        if (running) return;
        running = true;
        main.removeCallbacks(tick);
        refresh();
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
        main.removeCallbacks(playsTick);
        main.removeCallbacks(clockTick);
    }

    void close() {
        stop();
        io.shutdownNow();
        logoIo.shutdownNow();
        playsIo.shutdownNow();
    }

    // ---- the live pitch and clock ----------------------------------------------------------------

    private void readPlays() {
        main.removeCallbacks(playsTick);
        Featured f = featured;
        if (!running || f == null || f.game.state != 0 || f.game.slug == null) return;
        ScoresTicker.Game g = f.game;
        boolean first = !g.id.equals(pitchGame);
        if (first) {
            pitchGame = g.id;
            seenPlays.clear();
            pitch.clear();
            pitch.setColors(g.homeColor != 0 ? g.homeColor : Color.WHITE, g.awayColor != 0 ? g.awayColor : 0xFFFFD54F);
        }
        playsIo.execute(() -> {
            List<LivePitch.Play> plays = LivePitch.latest(g.slug, g.id, g.homeId);
            main.post(() -> {
                List<LivePitch.Play> fresh = new ArrayList<>();
                for (LivePitch.Play p : plays) if (seenPlays.add(p.id)) fresh.add(p);
                if (!fresh.isEmpty()) pitch.add(fresh, first);
                if (running) main.postDelayed(playsTick, 10_000L);
            });
        });
    }

    /** The match clock, counting on by the second from ESPN's last reading; HT at half-time. */
    private void updateClock() {
        main.removeCallbacks(clockTick);
        Featured f = featured;
        if (clockView == null || f == null || f.game.state != 0) return;
        ScoresTicker.Game g = f.game;
        String text;
        if (g.halftime) {
            text = "●  HALF-TIME";
        } else {
            long secs = ScoresTicker.secsNow(g, System.currentTimeMillis());
            text = String.format(java.util.Locale.ROOT, "●  LIVE  %d:%02d", secs / 60, secs % 60);
        }
        clockView.setText(text);
        if (running) main.postDelayed(clockTick, 1000L);
    }

    // ---- data ------------------------------------------------------------------------------------

    private void refresh() {
        main.removeCallbacks(tick);
        long now = System.currentTimeMillis();
        boolean tablesNow = now - tablesAt >= (anyLive ? TABLE_LIVE_MS : TABLE_IDLE_MS);
        boolean f1Now = now - f1At >= F1_MS || drivers.isEmpty();
        if (tablesNow) tablesAt = now;
        if (f1Now) f1At = now;
        String[] leagues = ctx.getResources().getStringArray(R.array.score_leagues);
        Set<String> alertTags = new HashSet<>();
        for (String l : ctx.getResources().getStringArray(R.array.goal_alert_leagues)) {
            alertTags.add(l.contains("|") ? l.split("\\|", 2)[1].trim() : l.trim());
        }
        Set<String> clubs = new HashSet<>(Arrays.asList(SyncHub.starredClubs(ctx)));
        // Each league once: a table league already on the scores line keeps that line's short name.
        List<String> all = new ArrayList<>(Arrays.asList(leagues));
        Set<String> slugs = new HashSet<>();
        for (String l : leagues) slugs.add(l.split("\\|", 2)[0].trim());
        for (String l : tableLeagues) if (slugs.add(l.split("\\|", 2)[0].trim())) all.add(l);
        io.execute(() -> {
            List<ScoresTicker.Game> games = ScoresTicker.load(all.toArray(new String[0]));
            ScoresTicker.Game pick = pick(games, alertTags, clubs);
            Featured f = pick == null ? null : featured(pick);
            if (f != null) f.tv = TvGuide.orAbroad(TvGuide.find(ctx.getResources().getStringArray(R.array.football_tv_channels),
                    pick.homeName, pick.awayName, pick.kickoff), f.game);
            Map<String, Table> fresh = new HashMap<>();
            if (tablesNow) for (String l : tableLeagues) {
                String[] p = l.split("\\|", 2);
                Table t = table(p[0].trim(), p.length > 1 ? p[1].trim() : p[0].trim(), games);
                if (t != null) fresh.put(p[0].trim(), t);
            }
            List<String[]> standings = f1Now ? f1Standings() : null;
            boolean live = false;
            for (ScoresTicker.Game g : games) live |= g.state == 0;
            final boolean liveNow = live;
            main.post(() -> {
                anyLive = liveNow;
                featured = f;
                drawFeatured();
                tables.putAll(fresh);
                for (int i = 0; i < tableCards.size(); i++) {
                    String slug = tableLeagues[i].split("\\|", 2)[0].trim();
                    if (fresh.containsKey(slug)) drawTable(tableCards.get(i), slug);
                }
                if (standings != null && !standings.isEmpty()) { drivers = standings; drawF1(); }
                if (running) main.postDelayed(tick, f != null && f.game.state == 0 ? LIVE_MS : IDLE_MS);
                if (running && f != null && f.game.state == 0) { readPlays(); updateClock(); }
            });
        });
    }

    /**
     * The game to feature: a live one in the alert competitions first, then a live game of the followed clubs,
     * then any live game, then the next of today's alert games, then the last one to finish.
     */
    private static ScoresTicker.Game pick(List<ScoresTicker.Game> games, Set<String> tags, Set<String> clubs) {
        ScoresTicker.Game best = null;
        int bestRank = 99;
        for (ScoresTicker.Game g : games) {
            boolean alert = tags.contains(g.league), club = clubs.contains(g.homeId) || clubs.contains(g.awayId);
            int rank;
            if (g.state == 0) rank = alert ? 0 : club ? 1 : 2;
            else if (g.state == 1) rank = alert ? 3 : club ? 4 : 6;
            else rank = alert ? 5 : club ? 7 : 8;
            boolean better = rank < bestRank || (rank == bestRank && best != null
                    && (g.state == 2 ? g.kickoff > best.kickoff : g.kickoff < best.kickoff));
            if (better) { best = g; bestRank = rank; }
        }
        return best;
    }

    private static Featured featured(ScoresTicker.Game g) {
        Featured f = new Featured();
        f.game = g;
        if (g.slug == null || g.id == null) return f;
        try {
            JSONObject s = new JSONObject(get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug
                    + "/summary?event=" + g.id));
            // Stats, in the home/away order of the scoreboard.
            JSONArray teams = s.optJSONObject("boxscore") == null ? null : s.getJSONObject("boxscore").optJSONArray("teams");
            Map<String, String> home = new HashMap<>(), away = new HashMap<>();
            for (int i = 0; teams != null && i < teams.length(); i++) {
                JSONObject t = teams.getJSONObject(i);
                String id = t.optJSONObject("team") == null ? "" : t.getJSONObject("team").optString("id");
                Map<String, String> into = id.equals(g.homeId) ? home : away;
                JSONArray st = t.optJSONArray("statistics");
                for (int k = 0; st != null && k < st.length(); k++) {
                    into.put(st.getJSONObject(k).optString("name"), st.getJSONObject(k).optString("displayValue"));
                }
            }
            String[][] wanted = {{"possessionPct", "Possession"}, {"totalShots", "Shots"}, {"shotsOnTarget", "On target"},
                    {"wonCorners", "Corners"}, {"foulsCommitted", "Fouls"}};
            for (String[] w : wanted) {
                if (home.containsKey(w[0]) && away.containsKey(w[0])) f.stats.add(new String[]{w[1], home.get(w[0]), away.get(w[0])});
            }
            JSONArray rosters = s.optJSONArray("rosters");
            for (int i = 0; rosters != null && i < rosters.length(); i++) {
                JSONObject r = rosters.getJSONObject(i);
                String id = r.optJSONObject("team") == null ? "" : r.getJSONObject("team").optString("id");
                if (id.equals(g.homeId)) f.formationHome = r.optString("formation", null);
                else f.formationAway = r.optString("formation", null);
                JSONArray list = r.optJSONArray("roster");
                for (int k = 0; list != null && k < list.length(); k++) {
                    JSONObject p = list.getJSONObject(k);
                    if (!p.optBoolean("starter")) continue;
                    String name = GoalWatchService.surname(p.getJSONObject("athlete").optString("displayName"));
                    (id.equals(g.homeId) ? f.xiHome : f.xiAway).add(name);
                }
            }
            // The latest goals, cards and substitutions, newest first.
            JSONArray key = s.optJSONArray("keyEvents");
            for (int i = key == null ? -1 : key.length() - 1; i >= 0 && f.events.size() < 3; i--) {
                JSONObject k = key.getJSONObject(i);
                String type = k.optJSONObject("type") == null ? "" : k.getJSONObject("type").optString("text");
                String icon = icon(type);
                if (icon == null) continue;
                String minute = k.optJSONObject("clock") == null ? "" : k.getJSONObject("clock").optString("displayValue");
                JSONArray who = k.optJSONArray("participants");
                String name = "";
                for (int p = 0; who != null && p < who.length() && p < 2; p++) {
                    JSONObject a = who.getJSONObject(p).optJSONObject("athlete");
                    if (a == null) continue;
                    name += (name.isEmpty() ? "" : " ↔ ") + GoalWatchService.surname(a.optString("displayName"));
                    if (!type.toLowerCase(java.util.Locale.ROOT).contains("substitution")) break;
                }
                f.events.add(minute + "   " + icon + "   " + name);
            }
            JSONArray comm = s.optJSONArray("commentary");
            for (int i = comm == null ? -1 : comm.length() - 1; i >= 0; i--) {
                String text = comm.getJSONObject(i).optString("text", "").trim();
                if (!text.isEmpty()) {
                    JSONObject time = comm.getJSONObject(i).optJSONObject("time");
                    String at = time == null ? "" : time.optString("displayValue", "");
                    f.commentary = (at.isEmpty() ? "" : at + "  ") + text;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return f;
    }

    private static String icon(String type) {
        String t = type.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("own goal")) return "⚽ (og)";
        if (t.startsWith("goal") || t.contains("penalty - scored")) return "⚽";
        if (t.contains("red card")) return "🟥";
        if (t.contains("yellow card")) return "🟨";
        if (t.contains("substitution")) return "🔁";
        if (t.contains("penalty") && t.contains("saved")) return "🧤";
        if (t.contains("penalty")) return "❌";
        if (t.contains("var")) return "📺";
        return null;
    }

    /** The league's standings with every game in progress counted as it stands. */
    private static Table table(String slug, String name, List<ScoresTicker.Game> games) {
        try {
            JSONObject root = new JSONObject(get("https://site.api.espn.com/apis/v2/sports/soccer/" + slug + "/standings"));
            JSONObject group = root.getJSONArray("children").getJSONObject(0);
            JSONArray entries = group.getJSONObject("standings").getJSONArray("entries");
            Table t = new Table();
            t.name = name;
            JSONArray rootLogos = root.optJSONArray("logos");
            t.logo = rootLogos != null && rootLogos.length() > 0 ? rootLogos.getJSONObject(0).optString("href", null) : null;
            // id -> {played, gd, points, abbr, logo}
            Map<String, int[]> numbers = new HashMap<>();
            Map<String, String[]> names = new HashMap<>();
            List<String> order = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject e = entries.getJSONObject(i);
                JSONObject team = e.getJSONObject("team");
                String id = team.optString("id");
                Map<String, String> st = new HashMap<>();
                JSONArray stats = e.optJSONArray("stats");
                for (int k = 0; stats != null && k < stats.length(); k++) {
                    st.put(stats.getJSONObject(k).optString("name"), stats.getJSONObject(k).optString("displayValue"));
                }
                JSONArray tl = team.optJSONArray("logos");
                String logo = tl != null && tl.length() > 0 ? tl.getJSONObject(0).optString("href", null) : null;
                numbers.put(id, new int[]{num(st.get("gamesPlayed")), num(st.get("pointDifferential")), num(st.get("points")),
                        num(st.get("pointsFor")), 0});
                names.put(id, new String[]{team.optString("abbreviation"), logo});
                order.add(id);
            }
            for (ScoresTicker.Game g : games) {
                if (g.state != 0 || !slug.equals(g.slug)) continue;
                int hs = num(g.homeScore), as = num(g.awayScore);
                int[] h = numbers.get(g.homeId), a = numbers.get(g.awayId);
                if (h == null || a == null) continue;
                t.live = true;
                h[0]++; a[0]++;
                h[1] += hs - as; a[1] += as - hs;
                h[3] += hs; a[3] += as;
                h[2] += hs > as ? 3 : hs == as ? 1 : 0;
                a[2] += as > hs ? 3 : hs == as ? 1 : 0;
                h[4] = a[4] = 1;
            }
            order.sort((x, y) -> {
                int[] p = numbers.get(x), q = numbers.get(y);
                if (p[2] != q[2]) return q[2] - p[2];
                if (p[1] != q[1]) return q[1] - p[1];
                return q[3] - p[3];
            });
            for (int i = 0; i < order.size(); i++) {
                int[] n = numbers.get(order.get(i));
                String[] nm = names.get(order.get(i));
                t.rows.add(new String[]{String.valueOf(i + 1), nm[0], nm[1], String.valueOf(n[0]),
                        (n[1] > 0 ? "+" : "") + n[1], String.valueOf(n[2]), n[4] == 1 ? "1" : "0"});
            }
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private static int num(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim().replace("+", "")); } catch (Exception e) { return 0; }
    }

    /** {position, code, surname, points, team logo url} for every driver, from Jolpica. */
    private List<String[]> f1Standings() {
        List<String[]> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(get("https://api.jolpi.ca/ergast/f1/current/driverStandings.json"));
            JSONObject list = root.getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0);
            String round = list.optString("round");
            JSONArray st = list.getJSONArray("DriverStandings");
            for (int i = 0; i < st.length(); i++) {
                JSONObject x = st.getJSONObject(i);
                JSONObject dr = x.getJSONObject("Driver");
                JSONArray cons = x.getJSONArray("Constructors");
                String team = cons.getJSONObject(cons.length() - 1).optString("constructorId");
                String code = dr.optString("code", F1Results.code(dr.optString("familyName")));
                out.add(new String[]{x.optString("position", String.valueOf(i + 1)), code, dr.optString("familyName"),
                        x.optString("points"), F1Results.logo(team)});
            }
            main.post(() -> f1Round = round);
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- views -----------------------------------------------------------------------------------

    /**
     * Each competition's own colours for the strip along the top of its card: {ESPN logo id (its version for dark
     * backgrounds), start colour, end colour, accent}; the accent marks the top places in a table.
     */
    private static final Map<String, int[]> BRANDS = new HashMap<>();
    static {
        BRANDS.put("eng.1", new int[]{23, 0xFF37003C, 0xFF5E1069, 0xFF00FF85});
        BRANDS.put("eng.2", new int[]{24, 0xFF0B1A3F, 0xFF1F3471, 0xFFE8B730});
        BRANDS.put("sco.1", new int[]{45, 0xFF1B1446, 0xFF3D2A7D, 0xFFB79CFF});
        BRANDS.put("esp.1", new int[]{15, 0xFFD61F2C, 0xFFFF5A4E, 0xFFFFFFFF});
        BRANDS.put("ita.1", new int[]{12, 0xFF0A2463, 0xFF1F57C8, 0xFF2EE6FF});
        BRANDS.put("ger.1", new int[]{10, 0xFFB3001B, 0xFFE2001A, 0xFFFFFFFF});
        BRANDS.put("fra.1", new int[]{9, 0xFF0A1A38, 0xFF183A73, 0xFFDAE025});
        BRANDS.put("ned.1", new int[]{11, 0xFFC4171D, 0xFFF0592A, 0xFFFFFFFF});
        BRANDS.put("por.1", new int[]{14, 0xFF00163F, 0xFF00539F, 0xFF34C3FF});
        BRANDS.put("uefa.champions", new int[]{2, 0xFF07103A, 0xFF1A3A94, 0xFFC9D3F0});
        BRANDS.put("uefa.europa", new int[]{2310, 0xFFD85A00, 0xFFF39200, 0xFF111111});
        BRANDS.put("uefa.europa.conf", new int[]{20296, 0xFF00872F, 0xFF1BC45A, 0xFFFFFFFF});
        BRANDS.put("eng.league_cup", new int[]{41, 0xFF00573F, 0xFF00915A, 0xFFB5E61D});
        BRANDS.put("eng.fa", new int[]{40, 0xFF0B2A5B, 0xFF1D4E9E, 0xFFE4002B});
        BRANDS.put("f1", new int[]{0, 0xFF15151E, 0xFF34343F, 0xFFE10600});
    }
    private static final int[] PLAIN = {0, 0xFF2A2F3A, 0xFF4A505C, 0xFFFFFFFF};

    /**
     * Each competition's name in a free typeface close to its own (the official ones are licensed): rounded for the
     * Premier League, geometric for UEFA and LaLiga, condensed for the EFL, Serie A and the Bundesliga, F1's
     * Titillium. Only capitals and digits are bundled, so the names are set in capitals.
     */
    private static int brandFont(String slug) {
        if (slug == null) return R.font.brand_plain;
        switch (slug) {
            case "eng.1": return R.font.brand_premier;
            case "eng.2": case "sco.1": case "eng.league_cup": return R.font.brand_efl;
            case "esp.1": return R.font.brand_laliga;
            case "ita.1": return R.font.brand_seriea;
            case "ger.1": return R.font.brand_bundesliga;
            case "fra.1": return R.font.brand_ligue1;
            case "eng.fa": return R.font.brand_facup;
            case "f1": return R.font.brand_f1;
            default: return slug.startsWith("uefa.") ? R.font.brand_uefa : R.font.brand_plain;
        }
    }

    private final Map<Integer, Typeface> fonts = new HashMap<>();

    private Typeface font(int res) {
        Typeface t = fonts.get(res);
        if (t == null) {
            try { t = ctx.getResources().getFont(res); } catch (Exception e) { t = Typeface.DEFAULT_BOLD; }
            fonts.put(res, t);
        }
        return t;
    }

    private static int[] brand(String slug) {
        int[] b = slug == null ? null : BRANDS.get(slug);
        return b != null ? b : PLAIN;
    }

    private static String brandLogo(String slug) {
        int id = brand(slug)[0];
        return id == 0 ? null : "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/" + id + ".png";
    }

    private FrameLayout card(int width) {
        FrameLayout c = new FrameLayout(ctx);
        c.setFocusable(true);
        c.setClipToOutline(true);
        boolean light = LightUi.on();
        // Glass theme: smoky glass that lets the wallpaper's colour through, with a bright rim.
        GradientDrawable bg = light
                ? new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFAFFFFFF, 0xF2F7F8FA})
                : LightUi.glass() ? new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x8C1A1F2B, 0xA60D1017})
                : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xE6202430, 0xE6121419});
        bg.setCornerRadius(dp(14));
        bg.setStroke(Math.max(1, dp(1)), light ? 0x14000000 : LightUi.glass() ? 0x4DFFFFFF : 0x33FFFFFF);
        c.setBackground(bg);
        if (light) c.setElevation(dp(2));
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(14));
        ring.setStroke(dp(1.5f), LightUi.ink(Color.WHITE));
        c.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f).setDuration(160).start();
            v.setForeground(focused ? ring : null);
            v.setElevation(focused ? dp(8) : light ? dp(2) : 0);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, dp(CARD_DP));
        lp.rightMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private void setWidth(FrameLayout card, int width) {
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp.width != width) { lp.width = width; card.setLayoutParams(lp); }
    }

    /** Up goes back to the apps from any card; Left and Right stop at the row's ends. */
    private void wireKeys() {
        for (int i = 0; i < strip.getChildCount(); i++) {
            final int index = i, count = strip.getChildCount();
            strip.getChildAt(i).setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { host.onSportsUp(); return true; }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return index == 0;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return index == count - 1;
                return false;
            });
        }
    }

    /**
     * The card emptied and given its competition's strip along the top (logo, title, and a status on the right),
     * and under it the column its content goes in. The strip's status view is the card's last header view.
     */
    private LinearLayout column(FrameLayout card, String slug, String logoUrl, String title, String status) {
        card.removeAllViews();
        LinearLayout whole = new LinearLayout(ctx);
        whole.setOrientation(LinearLayout.VERTICAL);
        int[] b = brand(slug);
        LinearLayout band = row();
        band.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{b[1], b[2]}));
        band.setPadding(dp(14), 0, dp(14), 0);
        if (logoUrl != null) {
            boolean wide = F1Results.F1_LOGO.equals(logoUrl);
            ImageView mark = logo(logoUrl, wide ? 34 : 22, true);
            band.addView(mark, new LinearLayout.LayoutParams(dp(wide ? 34 : 22), dp(22)));
        }
        TextView t = text(title.toUpperCase(java.util.Locale.ROOT), 13, Color.WHITE, true);
        t.setTextColor(Color.WHITE);
        t.setTypeface(font(brandFont(slug)));
        t.setLetterSpacing(0.04f);
        band.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).leftMargin = logoUrl != null ? dp(9) : 0;
        statusView = null;
        if (status != null) {
            statusView = text(status, 10, Color.WHITE, true);
            statusView.setTextColor(status.startsWith("●") ? 0xFF7CFFAE : 0xD9FFFFFF);
            band.addView(statusView);
        }
        whole.addView(band, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        // A hairline of the competition's accent under the strip.
        View line = new View(ctx);
        line.setBackgroundColor(b[3]);
        whole.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(2))));
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(8), dp(14), dp(10));
        whole.addView(col, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        card.addView(whole, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return col;
    }

    private TextView statusView;

    /** Green for live: bright on the dark cards, deeper on the light ones. */
    private static int liveGreen() {
        return LightUi.on() ? 0xFF16A34A : 0xFF4ADE80;
    }

    private void drawFeatured() {
        Featured f = featured;
        if (f == null) {
            LinearLayout col = column(featuredCard, null, null, "FOOTBALL", null);
            clockView = null;
            col.addView(text("No games today", 14, 0xB3FFFFFF, false), top(dp(30)));
            return;
        }
        ScoresTicker.Game g = f.game;
        String status = g.state == 0 ? "●  LIVE  " + g.detail : g.state == 1 ? "KICK-OFF  " + g.detail : "FULL TIME";
        LinearLayout col = column(featuredCard, g.slug, brandLogo(g.slug) != null ? brandLogo(g.slug) : g.leagueLogo,
                ScoresTicker.competition(g), status);
        // While live, the status on the right is the ticking match clock.
        clockView = g.state == 0 ? statusView : null;
        // The score line: crest, name, score, name, crest.
        LinearLayout score = row();
        score.setGravity(Gravity.CENTER);
        score.addView(logo(g.homeLogo, 30, false), sized(dp(30), 0));
        score.addView(text(g.home, 15, Color.WHITE, true), gap(dp(10)));
        score.addView(text(g.state == 1 ? "v" : g.homeScore + "  –  " + g.awayScore, 22, Color.WHITE, true), gap(dp(16)));
        score.addView(text(g.away, 15, Color.WHITE, true), gap(dp(16)));
        score.addView(logo(g.awayLogo, 30, false), sized(dp(30), dp(10)));
        col.addView(score, fill(dp(2)));
        if (f.tv != null) {
            // "LIVE ON" and the channel's logo, centred under the score.
            LinearLayout on = row();
            on.setGravity(Gravity.CENTER);
            on.addView(text("LIVE ON", 9, 0xB3FFFFFF, true));
            if (f.tv.logo != null) {
                ImageView tvLogo = logo(f.tv.logo, 16, false);
                on.addView(tvLogo, new LinearLayout.LayoutParams(dp(120), dp(14)));
                ((LinearLayout.LayoutParams) tvLogo.getLayoutParams()).leftMargin = dp(8);
            } else {
                // Abroad, ESPN gives the channel's name only.
                on.addView(text(f.tv.name, 10, Color.WHITE, true), gap(dp(8)));
            }
            col.addView(on, fill(dp(3)));
        }
        if (f.stats.isEmpty()) {
            // Before kick-off: the two elevens side by side, or when to expect them.
            if (f.xiHome.size() >= 11 && f.xiAway.size() >= 11) {
                LinearLayout xi = row();
                xi.setGravity(Gravity.TOP);
                for (List<String> side : java.util.Arrays.asList(f.xiHome, f.xiAway)) {
                    LinearLayout c = new LinearLayout(ctx);
                    c.setOrientation(LinearLayout.VERTICAL);
                    // Two columns of the eleven a side, so all of it fits.
                    LinearLayout pair = row();
                    pair.setGravity(Gravity.TOP);
                    for (int half = 0; half < 2; half++) {
                        LinearLayout h = new LinearLayout(ctx);
                        h.setOrientation(LinearLayout.VERTICAL);
                        for (int i = half * 6; i < Math.min(half * 6 + 6, Math.min(11, side.size())); i++) {
                            h.addView(text(side.get(i), 10, 0xD9FFFFFF, false), top(dp(1)));
                        }
                        pair.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    }
                    c.addView(pair);
                    xi.addView(c, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
                col.addView(text("STARTING LINE-UPS", 9, 0x80FFFFFF, true), top(dp(8)));
                col.addView(xi, fill(dp(3)));
            } else {
                TextView wait = text("Line-ups are usually out an hour before kick-off", 11, 0x99FFFFFF, false);
                wait.setGravity(Gravity.CENTER);
                col.addView(wait, fill(dp(30)));
            }
            return;
        }
        // Stats as split bars, then the latest incidents and the newest line of commentary; while live, the pitch
        // with the ball moving through the plays comes first.
        LinearLayout body = row();
        body.setGravity(Gravity.TOP);
        if (g.state == 0) {
            LinearLayout left = new LinearLayout(ctx);
            left.setOrientation(LinearLayout.VERTICAL);
            if (pitch.getParent() instanceof ViewGroup) ((ViewGroup) pitch.getParent()).removeView(pitch);
            left.addView(pitch, new LinearLayout.LayoutParams(dp(206), dp(126)));
            playLabel = text("", 9.5f, 0xCCFFFFFF, false);
            left.addView(playLabel, top(dp(4)));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(206), ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.rightMargin = dp(14);
            body.addView(left, llp);
        }
        LinearLayout stats = new LinearLayout(ctx);
        stats.setOrientation(LinearLayout.VERTICAL);
        for (String[] st : f.stats) stats.addView(statRow(st), fill(dp(4)));
        body.addView(stats, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));
        LinearLayout events = new LinearLayout(ctx);
        events.setOrientation(LinearLayout.VERTICAL);
        events.setPadding(dp(14), 0, 0, 0);
        if (!f.events.isEmpty()) events.addView(text("LATEST", 9, 0x80FFFFFF, true));
        for (String e : f.events) events.addView(text(e, 10.5f, 0xE6FFFFFF, false), top(dp(4)));
        if (f.commentary != null) {
            TextView c = text(f.commentary, 10, 0x99FFFFFF, false);
            c.setSingleLine(false);
            c.setMaxLines(3);
            c.setEllipsize(TextUtils.TruncateAt.END);
            c.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
            events.addView(c, top(dp(6)));
        }
        body.addView(events, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(body, fill(dp(8)));
    }

    /** "12   Shots   9" over a bar split in proportion. */
    private View statRow(String[] s) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.VERTICAL);
        boolean pct = s[0].equals("Possession");
        LinearLayout line = row();
        TextView h = text(s[1] + (pct ? "%" : ""), 10.5f, Color.WHITE, true);
        TextView label = text(s[0], 9, 0x99FFFFFF, false);
        label.setGravity(Gravity.CENTER);
        TextView a = text(s[2] + (pct ? "%" : ""), 10.5f, Color.WHITE, true);
        a.setGravity(Gravity.END);
        line.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        line.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(line);
        float hv = parse(s[1]), av = parse(s[2]);
        float share = hv + av <= 0 ? 0.5f : hv / (hv + av);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        blp.topMargin = dp(2);
        r.addView(new SplitBar(ctx, share), blp);
        return r;
    }

    private static float parse(String s) {
        try { return Float.parseFloat(s.replace("%", "").trim()); } catch (Exception e) { return 0; }
    }

    /** Two rounded halves: the home share in white (ink on a light card), the away share dimmer. */
    private static final class SplitBar extends View {
        private final float share;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SplitBar(Context c, float share) {
            super(c);
            this.share = share;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), r = h / 2f, gap = h;
            float split = Math.max(r, Math.min(w - r, w * share));
            paint.setColor(LightUi.ink(0xF2FFFFFF));
            c.drawRoundRect(new RectF(0, 0, split - gap / 2f, h), r, r, paint);
            paint.setColor(LightUi.ink(0x5CFFFFFF));
            c.drawRoundRect(new RectF(split + gap / 2f, 0, w, h), r, r, paint);
        }
    }

    /** As many columns as a whole table needs side by side, at most PER_COLUMN rows in each. */
    private static int columns(int rows) {
        return Math.max(1, (rows + PER_COLUMN - 1) / PER_COLUMN);
    }

    private int tableWidth(int rows) {
        int cols = columns(rows);
        return dp(28) + cols * dp(COLUMN_DP) + (cols - 1) * dp(16);
    }

    private void drawTable(FrameLayout card, String slug) {
        Table t = tables.get(slug);
        String name = slug;
        for (String l : tableLeagues) if (l.startsWith(slug + "|")) name = l.split("\\|", 2)[1].trim();
        if (t == null) {
            LinearLayout col = column(card, slug, brandLogo(slug), name, null);
            col.addView(text("Loading the table…", 11, 0x80FFFFFF, false), top(dp(20)));
            return;
        }
        setWidth(card, tableWidth(t.rows.size()));
        LinearLayout col = column(card, slug, brandLogo(slug) != null ? brandLogo(slug) : t.logo, name, t.live ? "●  LIVE" : null);
        boolean uefa = slug.startsWith("uefa.");
        int accent = brand(slug)[3], n = t.rows.size();
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String[] r = t.rows.get(i);
            int rank = i + 1;
            // The places that matter: a UEFA league phase's top eight and play-off places; a league's top four and
            // its bottom three.
            int mark = uefa ? (rank <= 8 ? liveGreen() : rank <= 24 ? 0xFFF5A524 : 0)
                    : rank <= 4 ? (accent == 0xFFFFFFFF || accent == 0xFF111111 ? brand(slug)[2] : accent) : rank > n - 3 && n > 10 ? 0xFFE5484D : 0;
            rows.add(new String[]{r[0], r[2], r[1], r[3], r[4], r[5], r[6], String.valueOf(mark)});
        }
        columnsOf(col, rows, new String[]{"#", "TEAM", "P", "GD", "PTS"});
    }

    /** The rows in as many side-by-side columns as they need, each with its own heading. */
    private void columnsOf(LinearLayout col, List<String[]> rows, String[] head) {
        LinearLayout across = row();
        across.setGravity(Gravity.TOP);
        int cols = columns(rows.size()), per = (rows.size() + cols - 1) / cols;
        for (int c = 0; c < cols; c++) {
            LinearLayout one = new LinearLayout(ctx);
            one.setOrientation(LinearLayout.VERTICAL);
            one.addView(tableRow(head[0], null, head[1], head[2], head[3], head[4], false, true, 0), fill(0));
            for (int i = c * per; i < Math.min(rows.size(), c * per + per); i++) {
                String[] r = rows.get(i);
                one.addView(tableRow(r[0], r[1], r[2], r[3], r[4], r[5], "1".equals(r[6]), false, Integer.parseInt(r[7])), fill(dp(1.5f)));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(COLUMN_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (c > 0) lp.leftMargin = dp(16);
            across.addView(one, lp);
        }
        col.addView(across, fill(0));
    }

    private View tableRow(String rank, String logo, String team, String p, String gd, String pts, boolean live, boolean head, int mark) {
        LinearLayout r = row();
        int dim = head ? 0x80FFFFFF : 0xB3FFFFFF;
        float size = head ? 8.5f : 10f;
        // A short bar of colour before the places that matter.
        View bar = new View(ctx);
        if (mark != 0) {
            GradientDrawable m = new GradientDrawable();
            m.setColor(mark);
            m.setCornerRadius(dp(1));
            bar.setBackground(m);
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Math.max(2, dp(2.5f)), dp(head ? 1 : 11));
        blp.rightMargin = dp(5);
        r.addView(bar, blp);
        TextView rk = text(rank, size, dim, false);
        rk.setFontFeatureSettings("tnum");   // every digit the same width, so the columns line up
        r.addView(rk, new LinearLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.WRAP_CONTENT));
        if (logo != null) r.addView(logo(logo, 13, false), sized(dp(13), 0));
        else r.addView(new View(ctx), sized(dp(13), 0));
        TextView tm = text(team + (live ? "  ●" : ""), size, head ? dim : Color.WHITE, !head);
        r.addView(tm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ((LinearLayout.LayoutParams) tm.getLayoutParams()).leftMargin = dp(6);
        if (live) tm.setTextColor(liveGreen());
        for (String v : new String[]{p, gd}) {
            TextView c = text(v, size, dim, false);
            c.setGravity(Gravity.END);
            c.setFontFeatureSettings("tnum");
            r.addView(c, new LinearLayout.LayoutParams(dp(24), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView pt = text(pts, size, head ? dim : Color.WHITE, !head);
        pt.setGravity(Gravity.END);
        pt.setFontFeatureSettings("tnum");
        r.addView(pt, new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private void drawF1() {
        String title = "F1 DRIVERS" + (f1Round.isEmpty() ? "" : "  ·  ROUND " + f1Round);
        if (drivers.isEmpty()) {
            LinearLayout col = column(f1Card, "f1", F1Results.F1_LOGO, title, null);
            col.addView(text("Loading the standings…", 11, 0x80FFFFFF, false), top(dp(20)));
            return;
        }
        setWidth(f1Card, tableWidth(drivers.size()));
        LinearLayout col = column(f1Card, "f1", F1Results.F1_LOGO, title, null);
        List<String[]> rows = new ArrayList<>();
        for (String[] x : drivers) {
            int rank = num(x[0]);
            rows.add(new String[]{x[0], x[4], x[2], "", "", x[3], "0", String.valueOf(rank >= 1 && rank <= 3 ? 0xFFE10600 : 0)});
        }
        columnsOf(col, rows, new String[]{"#", "DRIVER", "", "", "PTS"});
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    /** Text in a card: white of the given strength, or ink of it on a light card. */
    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(LightUi.ink(color));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /**
     * A logo from the shared disk cache, filled in when it arrives: lightened for a dark card, as it is on a light
     * one or on a competition's strip (onBand, whose logos are made for dark backgrounds).
     */
    private ImageView logo(String url, int sizeDp, boolean onBand) {
        ImageView v = new ImageView(ctx);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (url == null) return v;
        boolean asIs = onBand || LightUi.on();
        String key = (asIs ? "" : "dark:") + url;
        Bitmap hit = logos.get(key);
        if (hit != null) { v.setImageBitmap(hit); return v; }
        int px = dp(sizeDp) * 2;
        logoIo.execute(() -> {
            Bitmap raw = ScoresTicker.cachedLogo(ctx, url, px);
            Bitmap b = asIs ? raw : ScoresTicker.forDark(raw);
            if (b != null) main.post(() -> { logos.put(key, b); v.setImageBitmap(b); });
        });
        return v;
    }

    private static LinearLayout.LayoutParams sized(int size, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = left;
        return lp;
    }

    private static LinearLayout.LayoutParams gap(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        return lp;
    }

    private static LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = margin;
        return lp;
    }

    private static LinearLayout.LayoutParams fill(int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = margin;
        return lp;
    }

    private int dp(float v) {
        return Math.round(v * d);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 3 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }
}
