package com.gathin.broadcast;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * What the live overlays are given (overlays/assets/broadcast: kickoff.html, lineups.html): the game's clubs, crests,
 * colours, competition and ground, and for the line-ups both teams from FotMob with each player's picture. Everything is
 * fetched and put inline (data: URLs), so the page reads nothing from the web itself. Off the main thread.
 */
public final class BroadcastData {
    private BroadcastData() { }

    /** A game as the app knows it (its own scores' feed): filled by the app, read here. */
    public static final class Match {
        public String homeName = "", awayName = "", homeShort = "", awayShort = "";
        /** Each side's colours (ARGB; 0 when not known): its first, and its second for the shorts. */
        public int homeColor, awayColor, homeAlt, awayAlt;
        /** The crests and the competition's logo: a picture the app has, else its address. */
        public Bitmap homeCrest, awayCrest, compLogo;
        public String homeCrestUrl, awayCrestUrl, compLogoUrl;
        /** The competition: its feed's id ("eng.1", or FotMob's "47") and name ("Premier League"). */
        public String compId = "", compName = "";
        public String venue = "";
        public long kickoff;
    }

    // ---- the competition ---------------------------------------------------------------------------------------------

    /**
     * Pure: the overlays' key for a competition, from ESPN's slug or FotMob's league id, else its name: "pl", "facup",
     * "leaguecup", "ucl", "uel", "uecl", or "" for any other (a plain cup then, and its own name and logo).
     */
    public static String compKey(String id, String name) {
        String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "eng.1": case "47": return "pl";
            case "eng.fa": case "132": return "facup";
            case "eng.league_cup": case "133": return "leaguecup";
            case "uefa.champions": case "42": return "ucl";
            case "uefa.europa": case "73": return "uel";
            case "uefa.europa.conf": case "10216": return "uecl";
            default: break;
        }
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("conference league")) return "uecl";
        if (n.contains("europa league")) return "uel";
        if (n.contains("champions league") && !n.contains("women") && !n.contains("afc") && !n.contains("caf")) return "ucl";
        if (n.contains("carabao") || n.contains("league cup") || n.contains("efl cup")) return "leaguecup";
        if (n.equals("fa cup") || n.startsWith("fa cup") || n.contains("emirates fa cup")) return "facup";
        if ((n.equals("premier league") || n.startsWith("english premier league")) && !n.contains("2")) return "pl";
        return "";
    }

    /** Pure: the competition's name on the tab: the usual one for the six, else the feed's own. */
    public static String compName(String key, String fallback) {
        switch (key == null ? "" : key) {
            case "pl": return "Premier League";
            case "facup": return "FA Cup";
            case "leaguecup": return "Carabao Cup";
            case "ucl": return "Champions League";
            case "uel": return "Europa League";
            case "uecl": return "Conference League";
            default: return fallback == null ? "" : fallback;
        }
    }

    /**
     * Pure: the competition's own mark on the pop-up's tab (assets: broadcast/marks/KEY.png): its logo, then its name in
     * its own lettering, white. From ESPN's slug or FotMob's league id, else its name; "" for one without (the tab then
     * has the feed's logo and the name in the pop-up's own lettering). Sponsors' names that are betting firms are never
     * in a mark: the Scottish leagues' marks carry the league's name only.
     */
    public static String mark(String id, String name) {
        String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "eng.1": case "47": return "pl";
            case "eng.2": case "48": return "championship";
            case "eng.3": case "108": return "league1";
            case "eng.4": case "109": return "league2";
            case "eng.fa": case "132": return "facup";
            case "eng.league_cup": case "133": return "leaguecup";
            case "eng.trophy": case "142": return "efltrophy";
            case "eng.w.1": case "9227": return "wsl";
            case "uefa.champions": case "42": return "ucl";
            case "uefa.europa": case "73": return "uel";
            case "uefa.europa.conf": case "10216": return "uecl";
            case "uefa.nations": case "9806": return "nations";
            case "esp.1": case "87": return "laliga";
            case "esp.copa_del_rey": case "138": return "copadelrey";
            case "esp.super_cup": case "139": return "supercopa";
            case "ita.1": case "55": return "seriea";
            case "ita.coppa_italia": case "141": return "coppaitalia";
            case "ger.1": case "54": return "bundesliga";
            case "ger.dfb_pokal": case "209": return "dfbpokal";
            case "fra.1": case "53": return "ligue1";
            case "fra.coupe_de_france": case "134": return "coupedefrance";
            case "ned.1": case "57": return "eredivisie";
            case "por.1": case "61": return "ligaportugal";
            case "sco.1": case "64": return "spfl";
            case "sco.2": case "123": return "scotchamp";
            case "usa.1": case "130": return "mls";
            case "ksa.1": case "536": return "saudipro";
            default: break;
        }
        if (!s.isEmpty() && !s.matches("[0-9]+") && s.contains(".")) return "";   // another league ESPN knows: none
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replace('’', '\'');
        if (n.isEmpty()) return "";
        if (n.contains("women") || n.contains("wsl")) {
            return n.contains("super league") || n.equals("wsl") ? "wsl" : "";
        }
        if (n.matches(".*\\b(u-?1[0-9]|u-?2[0-3]|under[- ]?[12][0-9]|youth|reserve|premier league 2)\\b.*")) return "";
        String k = compKey("", name);
        if (!k.isEmpty()) return k;
        k = introTrophy("", name);
        if (!k.isEmpty()) return k;
        if (n.contains("nations league")) return "nations";
        if (n.contains("scottish premiership") || n.equals("premiership")) return "spfl";
        if (n.contains("scottish championship")) return "scotchamp";
        if (n.equals("championship") || n.contains("league championship") || n.equals("efl championship")
                || n.equals("english championship") || n.endsWith("bet championship")) return "championship";
        if (n.equals("league one") || n.contains("league one") || n.equals("english league 1")) return "league1";
        if (n.equals("league two") || n.contains("league two") || n.equals("english league 2")) return "league2";
        if (n.contains("efl trophy") || n.contains("vertu trophy")) return "efltrophy";
        if (n.contains("copa del rey")) return "copadelrey";
        if (n.contains("supercopa") || n.contains("spanish super cup")) return "supercopa";
        if (n.contains("coppa italia")) return "coppaitalia";
        if (n.contains("dfb") && n.contains("pokal")) return "dfbpokal";
        if (n.contains("coupe de france")) return "coupedefrance";
        if (n.contains("eredivisie")) return "eredivisie";
        if (n.contains("primeira liga") || n.contains("liga portugal")) return "ligaportugal";
        if (n.equals("mls") || n.contains("major league soccer")) return "mls";
        if (n.contains("saudi pro league") || n.contains("saudi professional league") || n.contains("roshn")) return "saudipro";
        return "";
    }

    /** Pure: "#rrggbb" for an ARGB colour; null for none (0). */
    public static String hex(int argb) {
        return argb == 0 ? null : String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
    }

    /** Pure: a club's three letters for its kit's tag ("Manchester United": "MAN"), from its short name when it has one. */
    static String shortOf(String shortName, String name) {
        String s = shortName != null && !shortName.trim().isEmpty() ? shortName.trim() : name == null ? "" : name.trim();
        s = s.replaceAll("[^A-Za-z]", "");
        return s.length() >= 3 ? s.substring(0, 3).toUpperCase(Locale.ROOT) : s.toUpperCase(Locale.ROOT);
    }

    // ---- the kick-off pop-up -----------------------------------------------------------------------------------------

    /**
     * The pop-up for one moment: kind "kickoff" (the game's kick-off), "restart" (play starting again after a goal, with the
     * score, scorer and minute) or "second" (the second half's kick-off, with the score). Crests and the logo inline.
     */
    public static JSONObject kickoff(String kind, Match m, int[] score, String scorer, String minute) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("kind", kind);
        o.put("home", side(m.homeName, m.homeShort, m.homeColor, m.homeAlt, image(m.homeCrest, m.homeCrestUrl, false)));
        o.put("away", side(m.awayName, m.awayShort, m.awayColor, m.awayAlt, image(m.awayCrest, m.awayCrestUrl, false)));
        String key = compKey(m.compId, m.compName);
        o.put("comp", key.isEmpty() ? "other" : key);
        o.put("compName", compName(key, m.compName));
        String logo = image(m.compLogo, m.compLogoUrl, false);
        if (logo != null) o.put("compLogo", logo);
        String mk = mark(m.compId, m.compName);
        if (!mk.isEmpty()) o.put("mark", mk);
        o.put("stadium", m.venue == null ? "" : m.venue);
        if (score != null && score.length == 2) o.put("score", new JSONArray().put(score[0]).put(score[1]));
        if (scorer != null) o.put("scorer", scorer);
        if (minute != null) o.put("goalMin", minute);
        o.put("kicksOff", "home");
        o.put("sound", true);
        return o;
    }

    private static JSONObject side(String name, String shortName, int colour, int alt, String crest) throws JSONException {
        JSONObject s = new JSONObject();
        s.put("name", name == null ? "" : name);
        s.put("short", shortOf(shortName, name));
        if (hex(colour) != null) s.put("color", hex(colour));
        if (hex(alt) != null) s.put("color2", hex(alt));
        if (crest != null) s.put("crest", crest);
        return s;
    }

    /** Pure: a goal's minute as the pop-up writes it ("23'", "45+2'"); "" for none. */
    public static String minute(String m) {
        if (m == null) return "";
        String s = m.trim().replace("’", "'");
        if (s.isEmpty()) return "";
        return s.endsWith("'") ? s : s + "'";
    }

    // ---- the matchday intro ------------------------------------------------------------------------------------------

    /**
     * Pure: the trophy at the intro's end: the six cups' and leagues' keys (compKey), and the four leagues it also has a
     * trophy for ("laliga", "seriea", "bundesliga", "ligue1"); "" for its plain star.
     */
    public static String introTrophy(String id, String name) {
        String k = compKey(id, name);
        if (!k.isEmpty()) return k;
        String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (n.contains("women") || n.contains(" 2") || n.contains("segunda")) return "";
        String nn = n.replace(" ", "");
        if (s.equals("esp.1") || s.equals("87") || nn.equals("laliga") || nn.equals("spanishlaliga") || nn.startsWith("laligaea")) return "laliga";
        if (s.equals("ita.1") || s.equals("55") || n.equals("serie a") || n.startsWith("italian serie a")) return "seriea";
        if (s.equals("ger.1") || s.equals("54") || n.equals("bundesliga") || n.startsWith("german bundesliga")) return "bundesliga";
        if (s.equals("fra.1") || s.equals("53") || n.equals("ligue 1") || n.startsWith("french ligue 1")) return "ligue1";
        return "";
    }

    /** The competitions, the sports and plain football that have an intro of their own (assets: broadcast/intros/KEY.mp4). */
    public static final String[] INTRO_KEYS = {"pl", "laliga", "ucl", "uel", "uecl", "facup", "leaguecup", "seriea", "bundesliga",
            "ligue1", "football", "f1", "ufc", "boxing"};

    /** Pure: a football game's intro: its competition's own, else plain football's (the Champions League ball). */
    public static String introKey(String compId, String compName) {
        String k = introTrophy(compId, compName);
        return k.isEmpty() ? "football" : k;
    }

    /**
     * The intro's settings (reveal.html): the film for key (the Premier League's: the family's own clip, then the
     * Whitebrook Road Sports ending), its sound, and the fixture written under the wordmark at its moment. own: a
     * family's own video (a file's path) played first instead, then the ending on its own; ownMs its length.
     */
    public static JSONObject intro(String key, String fixture, String own, long ownMs) throws JSONException {
        JSONObject o = new JSONObject();
        JSONArray clips = new JSONArray();
        String k = key == null || key.isEmpty() ? "football" : key;
        if (own != null && !own.isEmpty()) {
            double first = Math.min(40.0, Math.max(1.0, ownMs / 1000.0));
            clips.put(own).put("intros/tail.mp4");
            o.put("firstMax", 40.0);
            o.put("sound", "intros/sound-tail.m4a");
            o.put("soundAt", first);
            o.put("fixtureAt", first + 3.8);
        } else {
            clips.put("intros/" + k + ".mp4");
            o.put("sound", "pl".equals(k) ? "intros/sound-pl.m4a" : "intros/sound-full.m4a");
            o.put("fixtureAt", "pl".equals(k) ? 13.8 : 15.2);
        }
        o.put("key", k);
        o.put("clips", clips);
        // in capitals as the wordmark's lines are, the "v" between two sides kept small
        o.put("fixture", fixture == null ? "" : fixture.trim().toUpperCase(Locale.ROOT).replace(" V ", " v "));
        return o;
    }

    /** Pure: the fixture under the wordmark: "ARSENAL v EVERTON" (the v kept small), or "" without both teams. */
    public static String fixture(String home, String away) {
        String h = home == null ? "" : home.trim(), a = away == null ? "" : away.trim();
        if (h.isEmpty() || a.isEmpty()) return "";
        return h.toUpperCase(Locale.ROOT) + " v " + a.toUpperCase(Locale.ROOT);
    }

    // ---- the line-ups ------------------------------------------------------------------------------------------------

    /**
     * The classic line-ups from FotMob's match details (its "content.lineup"): both elevens with their places, numbers
     * and the captain, the subs, the formations, the ground's place for its light, and each player's picture (the
     * Premier League's cut-out, in a La Liga game La Liga's, else FotMob's face). Null when FotMob has no line-ups for
     * the game yet.
     */
    public static JSONObject lineups(Match m, JSONObject details, File cacheDir) throws JSONException {
        JSONObject content = details == null ? null : details.optJSONObject("content");
        JSONObject lu = content == null ? null : content.optJSONObject("lineup");
        JSONObject ht = lu == null ? null : lu.optJSONObject("homeTeam"), at = lu == null ? null : lu.optJSONObject("awayTeam");
        if (ht == null || at == null || size(ht.optJSONArray("starters")) < 11 || size(at.optJSONArray("starters")) < 11) return null;
        JSONObject home = team(ht), away = team(at);
        home.put("name", m.homeName == null || m.homeName.isEmpty() ? ht.optString("name") : m.homeName);
        away.put("name", m.awayName == null || m.awayName.isEmpty() ? at.optString("name") : m.awayName);
        home.put("short", shortOf(m.homeShort, home.optString("name")));
        away.put("short", shortOf(m.awayShort, away.optString("name")));
        if (hex(m.homeColor) != null) home.put("color", hex(m.homeColor));
        if (hex(m.homeAlt) != null) home.put("color2", hex(m.homeAlt));
        if (hex(m.awayColor) != null) away.put("color", hex(m.awayColor));
        if (hex(m.awayAlt) != null) away.put("color2", hex(m.awayAlt));
        // the crests, and every player's picture, fetched side by side
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            Future<String> hc = pool.submit(() -> image(m.homeCrest, m.homeCrestUrl != null ? m.homeCrestUrl : fotmobCrest(ht), false));
            Future<String> ac = pool.submit(() -> image(m.awayCrest, m.awayCrestUrl != null ? m.awayCrestUrl : fotmobCrest(at), false));
            // a La Liga game's players have La Liga's own cut-outs (a few pages a game); any other's the Premier League's
            boolean laLiga = LaLigaPhotos.isLaLiga(m.compId, m.compName, details);
            LaLigaPhotos.Budget budget = laLiga ? new LaLigaPhotos.Budget() : null;
            JSONObject boot = laLiga ? null : PlPhotos.boot(cacheDir);
            JSONObject[][] teams = {{home, ht}, {away, at}};
            List<List<PlPhotos.Element>> clubs = new ArrayList<>();
            for (JSONObject[] t : teams) {
                List<PlPhotos.Element> club = PlPhotos.club(boot, t[0].optString("name"));
                if (club.isEmpty()) club = PlPhotos.club(boot, t[1].optString("name"));
                clubs.add(club);
            }
            // both elevens before the subs, so the page budget goes to the starters first
            Map<JSONObject, Future<String[]>> pics = new LinkedHashMap<>();
            for (String list : new String[]{"xi", "subs"}) {
                for (int k = 0; k < teams.length; k++) {
                    final List<PlPhotos.Element> els = clubs.get(k);
                    final String[] names = laLiga ? new String[]{teams[k][0].optString("name"), teams[k][1].optString("name")} : null;
                    JSONArray ps = teams[k][0].optJSONArray(list);
                    for (int i = 0; ps != null && i < ps.length(); i++) {
                        JSONObject p = ps.getJSONObject(i);
                        pics.put(p, pool.submit(() -> pictures(els, names, budget, cacheDir, p)));
                    }
                }
            }
            String hcs = hc.get(30, TimeUnit.SECONDS), acs = ac.get(30, TimeUnit.SECONDS);
            if (hcs != null) home.put("crest", hcs);
            if (acs != null) away.put("crest", acs);
            for (Map.Entry<JSONObject, Future<String[]>> e : pics.entrySet()) {
                try {
                    String[] two = e.getValue().get(40, TimeUnit.SECONDS);
                    if (two[0] != null) e.getKey().put("photo", two[0]);
                    else if (two[1] != null) e.getKey().put("face", two[1]);
                } catch (Exception ignored) { }
            }
        } catch (Exception e) {
            // whatever came in time is used: the rest are drawn without their pictures
        } finally {
            pool.shutdownNow();
        }
        JSONObject o = new JSONObject();
        o.put("home", home);
        o.put("away", away);
        JSONObject info = content.optJSONObject("matchFacts");
        info = info == null ? null : info.optJSONObject("infoBox");
        JSONObject ground = info == null ? null : info.optJSONObject("Stadium");
        o.put("stadium", ground != null && !ground.optString("name").isEmpty() ? ground.optString("name") : m.venue == null ? "" : m.venue);
        o.put("city", ground == null ? "" : ground.optString("city"));
        o.put("lat", ground != null && ground.has("lat") ? ground.optDouble("lat", 51.5) : 51.5);
        o.put("lon", ground != null && ground.has("long") ? ground.optDouble("long", -0.1) : -0.1);
        String ref = referee(info);
        if (!ref.isEmpty()) o.put("referee", ref);
        // the ground itself behind the line-ups (a free photo, graded by the page for the time of the game)
        JSONObject photo = Grounds.ground(o.optString("stadium"), o.optString("city"), cacheDir);
        if (photo != null) o.put("ground", photo);
        long ko = m.kickoff > 0 ? m.kickoff : System.currentTimeMillis();
        ZonedDateTime local = Instant.ofEpochMilli(ko).atZone(ZoneId.systemDefault());
        o.put("ko", String.format(Locale.ROOT, "%02d:%02d", local.getHour(), local.getMinute()));
        o.put("kickoffAt", ko);
        o.put("cond", night(local) ? "night" : "day");
        o.put("compName", compName(compKey(m.compId, m.compName), m.compName));
        o.put("sound", true);
        return o;
    }

    /** Pure: the referee's name from FotMob's info box ({"Referee": {"text": ...}}), "" when it has none. */
    static String referee(JSONObject infoBox) {
        JSONObject r = infoBox == null ? null : infoBox.optJSONObject("Referee");
        return r == null ? "" : r.optString("text", "").trim();
    }

    // ---- the first design: the strap in the board's own look ------------------------------------------------------

    /**
     * The kick-off strap in the scoreboard's look (page "strap"): the pop-up's settings, with the board's look (its
     * BoardBug style's name), where the board sits on a 1920 x 1080 screen {x, y, w, h} (the strap folds into it) and
     * the ground's weather ("9° · light rain", or "").
     */
    public static JSONObject strap(String kind, Match m, int[] score, String scorer, String minute, String look, float[] board,
                                   String weather) throws JSONException {
        JSONObject o = kickoff(kind, m, score, scorer, minute);
        o.put("look", look == null || look.isEmpty() ? "CLASSIC" : look);
        if (board != null && board.length == 4 && board[2] > 0 && board[3] > 0) {
            o.put("board", new JSONObject().put("x", Math.round(board[0])).put("y", Math.round(board[1]))
                    .put("w", Math.round(board[2])).put("h", Math.round(board[3])));
        }
        if (weather != null && !weather.isEmpty()) o.put("weather", weather);
        return o;
    }

    /** Pure: whether a kick-off is under the floodlights: from five in winter's months, from seven in summer's. */
    static boolean night(ZonedDateTime t) {
        int month = t.getMonthValue(), hour = t.getHour();
        boolean winter = month >= 10 || month <= 3;
        return hour >= (winter ? 17 : 19) || hour < 6;
    }

    /**
     * Pure: one FotMob team as the line-ups read it: {name, formation, coach, xi: [{n, name, last, id, gk, cap, x, y,
     * pos}], subs: [{n, name, last, id, gk}]}. x is FotMob's vertical layout turned round (1 - x), so the right-back
     * (FotMob's x near 0) is near 1; y as given (the keeper near 0.1, the forwards near 0.9).
     */
    static JSONObject team(JSONObject t) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", t.optString("name"));
        o.put("formation", t.optString("formation"));
        JSONObject coach = t.optJSONObject("coach");
        o.put("coach", coach == null ? "" : coach.optString("name"));
        JSONArray xi = new JSONArray(), subs = new JSONArray();
        JSONArray st = t.optJSONArray("starters"), sb = t.optJSONArray("subs");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject p = st.optJSONObject(i);
            if (p == null) continue;
            JSONObject q = person(p);
            JSONObject v = p.optJSONObject("verticalLayout");
            double x = v == null ? 0.5 : v.optDouble("x", 0.5), y = v == null ? 0.5 : v.optDouble("y", 0.5);
            q.put("x", Math.round((1 - x) * 1000) / 1000.0);
            q.put("y", Math.round(y * 1000) / 1000.0);
            q.put("pos", p.optInt("positionId"));
            q.put("cap", p.optBoolean("isCaptain", false));
            xi.put(q);
        }
        for (int i = 0; sb != null && i < sb.length(); i++) {
            JSONObject p = sb.optJSONObject(i);
            if (p != null) subs.put(person(p));
        }
        o.put("xi", xi);
        o.put("subs", subs);
        return o;
    }

    private static JSONObject person(JSONObject p) throws JSONException {
        JSONObject q = new JSONObject();
        q.put("n", p.optString("shirtNumber", ""));
        String name = p.optString("name");
        String last = p.optString("lastName");
        if (last.isEmpty()) {
            String[] w = name.trim().split("\\s+");
            last = w.length == 0 ? name : w[w.length - 1];
        }
        q.put("name", name);
        q.put("last", last);
        q.put("id", p.optLong("id"));
        q.put("gk", p.optInt("positionId", -1) == 11 || p.optInt("usualPlayingPositionId", -1) == 0);
        return q;
    }

    private static int size(JSONArray a) {
        return a == null ? 0 : a.length();
    }

    private static String fotmobCrest(JSONObject t) {
        long id = t.optLong("id");
        return id > 0 ? "https://images.fotmob.com/image_resources/logo/teamlogo/" + id + ".png" : null;
    }

    /**
     * One player's two pictures, as data: URLs: {the Premier League's cut-out (in a La Liga game, La Liga's: laLiga
     * holds his club's names), FotMob's face (only without one)}.
     */
    private static String[] pictures(List<PlPhotos.Element> club, String[] laLiga, LaLigaPhotos.Budget budget, File cacheDir, JSONObject p) {
        String photo = null, face = null;
        if (laLiga != null) {
            photo = LaLigaPhotos.photo(p.optString("name"), p.optString("last"), laLiga, cacheDir, budget);
        } else {
            int code = PlPhotos.codeFor(club, p.optString("name"), p.optString("last"));
            Bitmap bm = PlPhotos.picture(code);
            if (bm != null) photo = dataUrl(bm, true);
        }
        if (photo == null && p.optLong("id") > 0) {
            face = image(null, "https://images.fotmob.com/image_resources/playerimages/" + p.optLong("id") + ".png", false);
        }
        return new String[]{photo, face};
    }

    // ---- pictures ------------------------------------------------------------------------------------------------------

    private static final Map<String, String> IMAGES = new LinkedHashMap<String, String>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > 24;
        }
    };

    /** A picture as a data: URL: the one given, else fetched from url (the last few kept); null when there is none. */
    static String image(Bitmap given, String url, boolean small) {
        if (given != null) return dataUrl(given, small);
        if (url == null || url.isEmpty()) return null;
        synchronized (IMAGES) {
            String s = IMAGES.get(url);
            if (s != null) return s;
        }
        try {
            byte[] b = Net.get(url, 15_000, 4 << 20);
            Bitmap bm = BitmapFactory.decodeByteArray(b, 0, b.length);
            if (bm == null) return null;
            String s = dataUrl(bm, small);
            synchronized (IMAGES) {
                IMAGES.put(url, s);
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The pop-up's settings with the competition's own mark put inline (markImg: a data: URL of broadcast/marks/KEY.png),
     * when it has one. The page draws it into its canvases, so it comes inline as the crests do, never from a file.
     */
    public static JSONObject withMark(AssetManager am, JSONObject o) {
        String k = o == null ? "" : o.optString("mark", "");
        if (k.isEmpty() || am == null || !k.matches("[a-z0-9]+")) return o;
        String key = "mark:" + k, s;
        synchronized (IMAGES) {
            s = IMAGES.get(key);
        }
        if (s == null) {
            try (InputStream in = am.open("broadcast/marks/" + k + ".png")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                s = "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return o;
            }
            synchronized (IMAGES) {
                IMAGES.put(key, s);
            }
        }
        try {
            o.put("markImg", s);
        } catch (JSONException ignored) {
            // left without it: the tab has the logo and the name
        }
        return o;
    }

    /** A bitmap as a data: URL: PNG (exact, for crests and logos) or WebP with its see-through kept (players' pictures). */
    static String dataUrl(Bitmap bm, boolean webp) {
        if (webp) return "data:image/webp;base64," + Base64.encodeToString(webp(bm), Base64.NO_WRAP);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.PNG, 100, out);
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** A player's picture as the line-ups take it: WebP, its see-through kept. */
    @SuppressWarnings("deprecation")
    static byte[] webp(Bitmap bm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bitmap.CompressFormat f = Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        bm.compress(f, 86, out);
        return out.toByteArray();
    }

    /** Pure: the goals so far as a score {home, away} from a board's "2–1" (any dash); null when it is not one. */
    public static int[] score(String s) {
        if (s == null) return null;
        String[] p = s.trim().split("\\s*[-–—]\\s*");
        if (p.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
