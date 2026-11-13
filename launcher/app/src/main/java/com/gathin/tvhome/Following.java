package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the TV follows, as the phone's Settings has it (the Control Centre's Teams & competitions, FollowPanel): the
 * starred clubs, one list with the phone's and the other TV's (SyncHub), and a switch for each competition.
 *
 * <p>A starred club's games are read wherever the TV reads scores, and in the club's own league and the Conference
 * League too (a national side's: the World Cup and friendlies as well). A competition switched on has every one of its
 * games read. Either way, a game of theirs on the channel watched is one the scoreboard can come up for: by itself
 * when Scoreboard over matches is on Automatic (OverlayPrompt), else from the button or by hand.
 *
 * <p>The competitions the TV reads every game of already (config.xml's goal_alert_leagues and score_leagues) keep
 * Notifications' switch as theirs: off, nothing comes from them, the scoreboard included. The rest are off until
 * switched on here; on, their games' alerts come on Home only (as the Championship's do) unless a starred club plays.
 */
final class Following {
    private Following() { }

    private static final String FILE = "following";

    /** Every competition with a switch, in the order the card lists them: {slug, tag, name}. */
    static final String[][] COMPETITIONS = {
            {"eng.1", "PL", "Premier League"}, {"eng.2", "CHAMP", "Championship"}, {"eng.3", "LEAGUE ONE", "League One"},
            {"eng.4", "LEAGUE TWO", "League Two"}, {"eng.fa", "FA CUP", "FA Cup"}, {"eng.league_cup", "EFL CUP", "EFL Cup"},
            {"uefa.champions", "UCL", "Champions League"}, {"uefa.europa", "UEL", "Europa League"},
            {"uefa.europa.conf", "UECL", "Conference League"}, {"sco.1", "SPFL", "Scottish Premiership"},
            {"esp.1", "LALIGA", "LaLiga"}, {"esp.copa_del_rey", "COPA", "Copa del Rey"}, {"esp.super_cup", "SUPERCOPA", "Supercopa"},
            {"ita.1", "SERIE A", "Serie A"}, {"ita.coppa_italia", "COPPA", "Coppa Italia"}, {"ger.1", "BUNDESLIGA", "Bundesliga"},
            {"ger.dfb_pokal", "POKAL", "DFB-Pokal"}, {"fra.1", "LIGUE 1", "Ligue 1"}, {"fra.coupe_de_france", "CDF", "Coupe de France"},
            {"ned.1", "EREDIVISIE", "Eredivisie"}, {"por.1", "LIGA PORTUGAL", "Liga Portugal"}, {"ksa.1", "SAUDI PRO", "Saudi Pro League"},
            {"usa.1", "MLS", "MLS"}, {"mex.1", "LIGA MX", "Liga MX"}, {"fifa.world", "WORLD CUP", "World Cup"},
            {"uefa.euro", "EURO", "Euros"}, {"uefa.nations", "UNL", "Nations League"},
            {"fifa.worldq.uefa", "WCQ", "World Cup qualifiers"}, {"uefa.euroq", "EUROQ", "Euro qualifiers"},
            {"fifa.friendly", "FRIENDLY", "Friendlies"}};

    /** The leagues Add a club looks through, and the national sides (the World Cup's): {slug, name}. */
    static final String[][] LEAGUES = {
            {"eng.1", "Premier League"}, {"eng.2", "Championship"}, {"eng.3", "League One"}, {"eng.4", "League Two"},
            {"sco.1", "Scottish Premiership"}, {"esp.1", "LaLiga"}, {"ita.1", "Serie A"}, {"ger.1", "Bundesliga"},
            {"fra.1", "Ligue 1"}, {"ned.1", "Eredivisie"}, {"por.1", "Liga Portugal"}, {"ksa.1", "Saudi Pro League"},
            {"usa.1", "MLS"}, {"mex.1", "Liga MX"}, {"fifa.world", "National teams"}};

    /** A starred club's games are looked for in the Conference League too. */
    static final String CONFERENCE = "uefa.europa.conf";

    /** ESPN's logo ids for the competitions GoalWatchService has none for (read from ESPN's scoreboards). */
    private static final Map<String, String> LOGO_IDS = new HashMap<>();
    static {
        String[][] ids = {{"ksa.1", "2488"}, {"fifa.world", "4"}, {"uefa.euro", "74"}, {"uefa.nations", "2395"},
                {"fifa.worldq.uefa", "67"}, {"uefa.euroq", "56"}, {"fifa.friendly", "53"}, {"esp.copa_del_rey", "80"}};
        for (String[] i : ids) LOGO_IDS.put(i[0], i[1]);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- the competitions ------------------------------------------------------------------------------------------

    /** Pure: a competition's tag as the scores carry it ("LIGA PORTUGAL"); the slug in capitals for one not listed. */
    static String tag(String slug) {
        if (slug == null) return "";
        for (String[] x : COMPETITIONS) if (x[0].equals(slug)) return x[1];
        return slug.toUpperCase(Locale.ROOT);
    }

    /** Pure: its name ("Liga Portugal"); the slug for one not listed. */
    static String name(String slug) {
        if (slug == null) return "";
        for (String[] x : COMPETITIONS) if (x[0].equals(slug)) return x[2];
        for (String[] x : LEAGUES) if (x[0].equals(slug)) return x[1];
        return slug;
    }

    /** Pure: one of the national sides' competitions. */
    static boolean international(String slug) {
        return slug != null && (slug.startsWith("fifa.") || slug.equals("uefa.euro") || slug.equals("uefa.euroq") || slug.equals("uefa.nations"));
    }

    private static Set<String> always;

    /** The competitions every game of which the TV reads anyway: goal_alert_leagues and score_leagues. */
    static synchronized Set<String> alwaysRead(Context c) {
        if (always != null) return always;
        Set<String> out = new HashSet<>();
        for (String l : c.getResources().getStringArray(R.array.goal_alert_leagues)) out.add(l.split("\\|", 2)[0].trim());
        for (String l : c.getResources().getStringArray(R.array.score_leagues)) out.add(l.split("\\|", 2)[0].trim());
        always = Collections.unmodifiableSet(out);
        return always;
    }

    /** Whether a competition is followed: every game of it read, the scoreboard able to come up for any. */
    static boolean on(Context c, String slug) {
        if (alwaysRead(c).contains(slug)) return NotifyPrefs.competitionOn(c, slug);
        return prefs(c).getBoolean("every." + slug, false);
    }

    /**
     * A competition switched. One read anyway: Notifications' switch for it. Any other: every game of it read from the
     * next poll, and (on) Notifications' switch for it on too, so nothing set there keeps its scoreboard away.
     */
    static void set(Context c, String slug, boolean on) {
        if (alwaysRead(c).contains(slug)) {
            NotifyPrefs.setCompetition(c, slug, on);
        } else {
            prefs(c).edit().putBoolean("every." + slug, on).apply();
            if (on) NotifyPrefs.setCompetition(c, slug, true);
        }
        AlertLog.note(c, "Teams & competitions: " + name(slug) + (on ? " followed, every game" : " off"));
    }

    /** The competitions switched on that the TV would not read otherwise, by slug. */
    static Set<String> extraSlugs(Context c) {
        Set<String> out = new LinkedHashSet<>();
        SharedPreferences p = prefs(c);
        Set<String> always = alwaysRead(c);
        for (String[] x : COMPETITIONS) if (!always.contains(x[0]) && p.getBoolean("every." + x[0], false)) out.add(x[0]);
        return out;
    }

    /** The same, as the poll's league entries ("por.1|LIGA PORTUGAL"). */
    static List<String> extraLeagues(Context c) {
        return entries(extraSlugs(c));
    }

    /** Pure: these competitions as the poll's league entries, each once, in order. */
    static List<String> entries(Collection<String> slugs) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : slugs) {
            if (s == null || s.trim().isEmpty() || !seen.add(s.trim())) continue;
            out.add(s.trim() + "|" + tag(s.trim()));
        }
        return out;
    }

    /** What a competition's line says now. */
    static String words(Context c, String slug) {
        if (on(c, slug)) return alwaysRead(c).contains(slug) ? "Every game: the scoreboard and its alerts"
                : "Every game: the scoreboard, its alerts on Home";
        return offWords(slug, alwaysRead(c).contains(slug));
    }

    /** Pure: what a competition switched off still has followed in it. */
    static String offWords(String slug, boolean readAnyway) {
        if (readAnyway) return "Off: nothing from it, nor the scoreboard";
        return international(slug) ? "Off: England's games and your teams' only" : "Off: your clubs' games only";
    }

    /** Its logo for a dark ground, in about px pixels (off the main thread); null when ESPN has none known. */
    static Bitmap logo(Context c, String slug, int px) {
        Bitmap b = GoalWatchService.competitionLogo(c, slug, px);
        if (b != null) return b;
        String id = LOGO_IDS.get(slug);
        return id == null ? null : ScoresTicker.cachedLogo(c, "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/" + id + ".png", px);
    }

    // ---- the starred clubs -----------------------------------------------------------------------------------------

    /** The starred clubs (ESPN ids): the phone's and the other TV's list. */
    static String[] clubs(Context c) {
        return SyncHub.starredClubs(c);
    }

    static boolean starred(Context c, String id) {
        return id != null && Arrays.asList(clubs(c)).contains(id);
    }

    /** A club starred or not: the list kept, and the phone and the other TV hear of it. */
    static void star(Context c, String id, boolean on) {
        if (id == null || id.isEmpty()) return;
        List<String> now = new ArrayList<>(Arrays.asList(clubs(c)));
        boolean had = now.contains(id);
        if (on == had) return;
        if (on) now.add(id);
        else now.remove(id);
        SyncHub.setStarredClubs(c, now);
        String[] k = known(c, id);
        AlertLog.note(c, "Teams & competitions: " + (k != null && !k[0].isEmpty() ? k[0] : "club " + id) + (on ? " starred" : " unstarred"));
    }

    /** A club's name, crest and league, as a league's clubs or ESPN's own page for it gave them. */
    static void know(Context c, String id, String name, String crest, String league) {
        if (id == null || id.isEmpty()) return;
        String now = clean(name) + "|" + clean(crest) + "|" + clean(league);
        if (!now.equals(prefs(c).getString("club." + id, null))) prefs(c).edit().putString("club." + id, now).apply();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('|', ' ').trim();
    }

    /** {name, crest, league} as known (each may be ""); null when nothing is known of it yet. */
    static String[] known(Context c, String id) {
        String k = prefs(c).getString("club." + id, null);
        if (k == null) return null;
        String[] p = k.split("\\|", -1);
        return p.length == 3 ? p : null;
    }

    /** ESPN's crest for a club by its id, when nothing better is known. */
    static String crestUrl(String id) {
        return "https://a.espncdn.com/i/teamlogos/soccer/500/" + id + ".png";
    }

    /** A starred club's line: its league ("Liga Portugal · every game"), or that it is shared. */
    static String clubWords(Context c, String id) {
        String[] k = known(c, id);
        String league = k == null ? "" : k[2];
        return league.isEmpty() ? "Every game · on your phone too" : name(league) + " · every game, on your phone too";
    }

    /** The starred clubs' own leagues (and the Conference League), as the poll's league entries. */
    static List<String> clubLeagues(Context c) {
        List<String> leagues = new ArrayList<>();
        String[] ids = clubs(c);
        for (String id : ids) {
            String[] k = known(c, id);
            if (k != null) leagues.add(k[2]);
        }
        return clubEntries(leagues, ids.length > 0);
    }

    /**
     * Pure: the league entries for the starred clubs' leagues: each league, a national side's World Cup and
     * friendlies, and with any club starred the Conference League.
     */
    static List<String> clubEntries(Collection<String> leagues, boolean anyClub) {
        List<String> slugs = new ArrayList<>();
        for (String l : leagues) {
            if (l == null || l.trim().isEmpty()) continue;
            slugs.add(l.trim());
            if (international(l.trim())) {
                slugs.add("fifa.world");
                slugs.add("fifa.friendly");
            }
        }
        if (anyClub) slugs.add(CONFERENCE);
        return entries(slugs);
    }

    /** How long before a club whose league couldn't be read is asked about again. */
    private static final long RETRY_MS = 6 * 3_600_000L;

    /**
     * The starred clubs nothing is known of yet (starred on the phone): their names, crests and leagues read from
     * ESPN, two at a time, so the next poll reads their leagues. Off the main thread.
     */
    static void resolve(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        int asked = 0;
        for (String id : clubs(c)) {
            String[] k = known(c, id);
            if (k != null && !k[2].isEmpty()) continue;
            if (now - p.getLong("tried." + id, 0) < RETRY_MS) continue;
            if (asked++ >= 2) break;
            p.edit().putLong("tried." + id, now).apply();
            SportsData.Team t = SportsData.team(c, id);
            if (t != null && !t.name.isEmpty()) know(c, id, t.name, t.logo, t.league);
        }
    }

    /** The tile's line: "3 clubs · 12 competitions". */
    static String describe(Context c) {
        int clubs = clubs(c).length, comps = 0;
        for (String[] x : COMPETITIONS) if (on(c, x[0])) comps++;
        return (clubs == 1 ? "1 club" : clubs + " clubs") + " · " + (comps == 1 ? "1 competition" : comps + " competitions");
    }
}
