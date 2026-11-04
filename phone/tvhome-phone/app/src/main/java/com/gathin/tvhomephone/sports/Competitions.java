package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The competitions the alerts can come from, each with its own switch (kept on this phone, not synced: every one on but
 * Friendlies), and each one's logo from ESPN as the TV shows it: the scoreboard's own once it has been read (its
 * version for dark grounds first), else ESPN's league logo by the id its scoreboard gives. F1 has ESPN's F1 logo.
 */
public final class Competitions {
    private Competitions() {}

    /** F1's switch, beside the football competitions' slugs. */
    public static final String F1 = "f1";
    public static final String F1_LOGO = "https://a.espncdn.com/i/teamlogos/leagues/500/f1.png";
    private static final String LEAGUE_LOGOS = "https://a.espncdn.com/i/leaguelogos/soccer/";

    /** Off until switched on: friendlies (the TV follows them, but few want an alert for one). */
    static final String[] OFF_BY_DEFAULT = {"fifa.friendly"};

    /**
     * Each competition's league logo id, as its scoreboard's leagues[0].logos name the file (read from ESPN's
     * scoreboards, November 2026): .../leaguelogos/soccer/500-dark/&lt;id&gt;.png for dark grounds, 500/ for light.
     */
    static final Map<String, String> IDS = new HashMap<>();
    static {
        String[][] ids = {{"eng.1", "23"}, {"uefa.champions", "2"}, {"eng.fa", "40"}, {"eng.league_cup", "41"}, {"eng.2", "24"},
                {"uefa.europa", "2310"}, {"uefa.europa.conf", "20296"}, {"esp.1", "15"}, {"ita.1", "12"}, {"ger.1", "10"},
                {"fra.1", "9"}, {"sco.1", "45"}, {"esp.copa_del_rey", "80"}, {"esp.super_cup", "431"}, {"uefa.nations", "2395"},
                {"fifa.worldq.uefa", "67"}, {"uefa.euroq", "56"}, {"fifa.friendly", "53"}, {"fifa.world", "4"}, {"uefa.euro", "74"}};
        for (String[] i : ids) IDS.put(i[0], i[1]);
    }

    /** The Supercopa has no file under 500-dark: its scoreboard names this one for dark grounds. */
    private static final Map<String, String> DARK = new HashMap<>();
    static {
        DARK.put("esp.super_cup", "https://a.espncdn.com/guid/f3ea21b4-d55a-317d-9e8e-6dfd6cd7963a/logos/default-dark.png");
    }

    /** slug → {dark, light} as the scoreboards have given them this run. */
    private static final Map<String, String[]> LEARNT = new ConcurrentHashMap<>();

    // ---- the switches ---------------------------------------------------------------------------------------------

    /** Every competition with a switch, {slug, name}: the football ones as Espn.LEAGUES lists them, then F1. */
    public static List<String[]> all() {
        List<String[]> out = new ArrayList<>();
        for (String[] l : Espn.LEAGUES) out.add(new String[]{l[0], l[2]});
        out.add(new String[]{F1, "Formula 1"});
        return out;
    }

    static boolean onByDefault(String slug) {
        for (String s : OFF_BY_DEFAULT) if (s.equals(slug)) return false;
        return true;
    }

    /**
     * Whether alerts may come from a competition, by the switches saved (slug → Boolean; a map of the phone's saved
     * switches, or any other): what was saved, else on (but Friendlies). A game with no competition is never held back.
     */
    public static boolean allowed(Map<String, ?> prefs, String slug) {
        if (slug == null || slug.isEmpty()) return true;
        Object v = prefs == null ? null : prefs.get(slug);
        return v instanceof Boolean ? (Boolean) v : onByDefault(slug);
    }

    /**
     * Whether an alert may be sent by its competition's switch: a game's by its competition, F1's by F1. Reminders the
     * family set themselves, the weather and anything else with no competition always may.
     */
    public static boolean allowed(Map<String, ?> prefs, Events.Alert a) {
        if (a == null) return false;
        if ("reminder".equals(a.kind)) return true;
        if (a.kind != null && a.kind.startsWith("f1")) return allowed(prefs, F1);
        return a.game == null || allowed(prefs, a.game.slug);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("sports_competitions", Context.MODE_PRIVATE);
    }

    /** The switches saved on this phone, slug → on. */
    public static Map<String, ?> saved(Context c) {
        return prefs(c).getAll();
    }

    public static boolean on(Context c, String slug) {
        return allowed(saved(c), slug);
    }

    public static void set(Context c, String slug, boolean on) {
        prefs(c).edit().putBoolean(slug, on).apply();
    }

    // ---- the logos ------------------------------------------------------------------------------------------------

    /** The competition's logo for a dark ground (the app's navy), or null when ESPN has none known. */
    public static String logo(String slug) {
        if (slug == null) return null;
        if (F1.equals(slug)) return F1_LOGO;
        String[] had = LEARNT.get(slug);
        if (had != null && had[0] != null) return had[0];
        if (DARK.containsKey(slug)) return DARK.get(slug);
        String id = IDS.get(slug);
        return id == null ? (had != null ? had[1] : null) : LEAGUE_LOGOS + "500-dark/" + id + ".png";
    }

    /** The competition's logo for a light ground (the alerts' silver band), or null. */
    public static String lightLogo(String slug) {
        if (slug == null) return null;
        if (F1.equals(slug)) return F1_LOGO;
        String[] had = LEARNT.get(slug);
        if (had != null && had[1] != null) return had[1];
        String id = IDS.get(slug);
        return id == null ? null : LEAGUE_LOGOS + "500/" + id + ".png";
    }

    /** A logo for a game: the one its scoreboard gave, else its competition's. */
    public static String logo(Game g) {
        if (g == null) return null;
        return g.leagueLogo != null && !g.leagueLogo.isEmpty() ? g.leagueLogo : logo(g.slug);
    }

    /**
     * Of a scoreboard's leagues[0].logos: {the one for dark grounds (rel "dark"), the default one}, either null when not
     * listed; the default stands in for the dark one when there is none. Pure, for the tests.
     */
    static String[] pick(JSONArray logos) {
        String dark = null, light = null;
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject l = logos.optJSONObject(i);
            if (l == null) continue;
            String href = l.optString("href", "");
            if (href.isEmpty()) continue;
            String rel = String.valueOf(l.optJSONArray("rel"));
            if (rel.contains("dark")) { if (dark == null) dark = href; }
            else if (light == null) light = href;
        }
        if (dark == null && light == null) return null;
        return new String[]{dark != null ? dark : light, light != null ? light : dark};
    }

    /** Keeps what a scoreboard gave for its competition's logo; returns the one for dark grounds, or null. */
    static String learn(String slug, JSONArray logos) {
        String[] p = pick(logos);
        if (p == null) return null;
        if (slug != null) LEARNT.put(slug, p);
        return p[0];
    }
}
