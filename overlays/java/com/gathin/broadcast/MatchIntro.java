package com.gathin.broadcast;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The match intro (page "match", overlays/src/match): an open in the style of a big network's football show, made our
 * own, for the game about to be watched. Its settings, made here off the main thread: the two clubs (names, colours,
 * crests, ESPN's ids for the page's own club colours), each with its four best-known players (FotMob's squad by transfer
 * value, the injured left out; in the line-ups' hour only the starting eleven), in the Premier League's cut-out (La
 * Liga's in a La Liga game, else FotMob's face); the competition's other clubs playing that day (ESPN's scoreboard); and
 * in a Premier League game two of the round's other games' stars face to face (its fantasy game's list). Never null:
 * whatever could not be had is left out, and the page makes do.
 */
public final class MatchIntro {
    private MatchIntro() { }

    /** Its music (assets-tv/broadcast/intros), played by the app. */
    public static final String SOUND = "intros/sound-match.m4a";
    /** The Champions League style's own music (a choir's anthem of our own). */
    public static final String SOUND_UCL = "intros/sound-match-ucl.m4a";
    static final String FOTMOB_TEAM = "https://www.fotmob.com/api/data/teams?id=";
    static final String SCOREBOARD = "https://site.api.espn.com/apis/site/v2/sports/soccer/%s/scoreboard?dates=%s-%s";
    /** How long a club's squad is kept on disk before it is fetched again. */
    static final long KEEP_MS = 12 * 3600_000L;
    /** Each club's players in the intro. */
    static final int STARS = 4;

    /**
     * The page's settings for the game m (its FotMob match details when known, for the clubs' ids, the ground and the
     * line-ups), the fixture as the other intros write it ("NEWCASTLE v ARSENAL").
     */
    public static JSONObject build(AssetManager am, BroadcastData.Match m, JSONObject details, File cacheDir, String fixture) throws JSONException {
        JSONObject o = new JSONObject();
        String trophy = BroadcastData.introTrophy(m.compId, m.compName);
        o.put("comp", trophy);
        o.put("compName", m.compName == null ? "" : m.compName);
        String mk = BroadcastData.mark(m.compId, m.compName);
        if (!mk.isEmpty()) o.put("mark", mk);
        o.put("fixture", fixture == null || fixture.isEmpty() ? BroadcastData.fixture(m.homeName, m.awayName) : fixture);
        long ko = m.kickoff > 0 ? m.kickoff : System.currentTimeMillis();
        ZonedDateTime local = Instant.ofEpochMilli(ko).atZone(ZoneId.systemDefault());
        o.put("ko", String.format(Locale.ROOT, "%02d:%02d", local.getHour(), local.getMinute()));
        String ground = groundOf(details, m.venue);
        if (!ground.isEmpty()) o.put("ground", ground.toUpperCase(Locale.ROOT));
        o.put("sound", SOUND);
        o.put("soundUcl", SOUND_UCL);
        JSONObject home = club(m.homeName, m.homeShort, m.homeColor, m.homeAlt, m.homeCrestUrl);
        JSONObject away = club(m.awayName, m.awayShort, m.awayColor, m.awayAlt, m.awayCrestUrl);
        o.put("home", home);
        o.put("away", away);
        boolean laLiga = LaLigaPhotos.isLaLiga(m.compId, m.compName, details);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            Future<String> hc = pool.submit(() -> BroadcastData.image(m.homeCrest, m.homeCrestUrl, false));
            Future<String> ac = pool.submit(() -> BroadcastData.image(m.awayCrest, m.awayCrestUrl, false));
            JSONObject boot = laLiga ? null : PlPhotos.boot(cacheDir);
            long[] ids = teamIds(details);
            Set<Long> hs = starters(details, "homeTeam"), as = starters(details, "awayTeam");
            LaLigaPhotos.Budget budget = laLiga ? new LaLigaPhotos.Budget() : null;
            String hName = m.homeName, aName = m.awayName;
            Future<JSONArray> hp = pool.submit(() -> players(ids[0], hs, hName, boot, laLiga ? new String[]{hName} : null, budget, cacheDir));
            Future<JSONArray> ap = pool.submit(() -> players(ids[1], as, aName, boot, laLiga ? new String[]{aName} : null, budget, cacheDir));
            String slug = slug(m.compId, m.compName);
            Future<JSONArray> fx = pool.submit(() -> slug.isEmpty() ? new JSONArray() : fixtures(slug, ko, m.homeName, m.awayName));
            String hcs = hc.get(30, TimeUnit.SECONDS), acs = ac.get(30, TimeUnit.SECONDS);
            if (hcs != null) home.put("crest", hcs);
            if (acs != null) away.put("crest", acs);
            // the other clubs that day: seven crests for the tunnels and the parade
            JSONArray games = fx.get(30, TimeUnit.SECONDS);
            JSONArray others = new JSONArray();
            List<Future<String>> crests = new ArrayList<>();
            for (int i = 0; i < games.length() && others.length() < 8; i++) {
                JSONArray g = games.getJSONArray(i);
                for (int k = 0; k < 2 && others.length() < 8; k++) {
                    JSONObject t = g.getJSONObject(k);
                    String url = t.optString("logo");
                    crests.add(pool.submit(() -> BroadcastData.image(null, url, false)));
                    others.put(t);
                }
            }
            // the round's other stars, face to face (the Premier League's own list knows them)
            JSONArray duels = boot == null || !"pl".equals(trophy) ? new JSONArray() : duels(boot, games, pool);
            o.put("home", home.put("players", hp.get(60, TimeUnit.SECONDS)));
            o.put("away", away.put("players", ap.get(60, TimeUnit.SECONDS)));
            for (int i = 0; i < others.length(); i++) {
                String c = crests.get(i).get(20, TimeUnit.SECONDS);
                JSONObject t = others.getJSONObject(i);
                t.remove("logo");
                if (c != null) t.put("crest", c);
            }
            o.put("others", others);
            o.put("duels", duels);
        } catch (Exception e) {
            // whatever came in time is used
        } finally {
            pool.shutdownNow();
        }
        return BroadcastData.withMark(am, o);
    }

    // ---- the clubs ---------------------------------------------------------------------------------------------------

    static JSONObject club(String name, String abbr, int color, int alt, String crestUrl) throws JSONException {
        JSONObject t = new JSONObject();
        String full = name == null ? "" : name.trim();
        t.put("full", full);
        t.put("name", clubWord(full));
        t.put("abbr", abbrOf(abbr, full));
        if (BroadcastData.hex(color) != null) t.put("color", BroadcastData.hex(color));
        if (BroadcastData.hex(alt) != null) t.put("color2", BroadcastData.hex(alt));
        String id = espnId(crestUrl);
        if (!id.isEmpty()) t.put("espn", id);
        return t;
    }

    private static final String[][] WORDS = {
            {"manchester city", "MAN CITY"}, {"manchester united", "MAN UTD"}, {"tottenham hotspur", "SPURS"}, {"nottingham forest", "FOREST"},
            {"wolverhampton wanderers", "WOLVES"}, {"brighton & hove albion", "BRIGHTON"}, {"brighton and hove albion", "BRIGHTON"},
            {"afc bournemouth", "BOURNEMOUTH"}, {"crystal palace", "PALACE"}, {"aston villa", "VILLA"}, {"west ham united", "WEST HAM"},
            {"paris saint-germain", "PSG"}, {"paris saint germain", "PSG"}, {"internazionale", "INTER"}, {"inter milan", "INTER"},
            {"ac milan", "MILAN"}, {"borussia dortmund", "DORTMUND"}, {"bayern munich", "BAYERN"}, {"bayern münchen", "BAYERN"},
            {"bayer leverkusen", "LEVERKUSEN"}, {"rb leipzig", "LEIPZIG"}, {"borussia mönchengladbach", "GLADBACH"},
            {"eintracht frankfurt", "FRANKFURT"}, {"atlético madrid", "ATLÉTICO"}, {"atletico madrid", "ATLÉTICO"},
            {"athletic club", "ATHLETIC"}, {"real sociedad", "REAL SOCIEDAD"}, {"olympique marseille", "MARSEILLE"},
            {"olympique de marseille", "MARSEILLE"}, {"olympique lyonnais", "LYON"}, {"as roma", "ROMA"}, {"ss lazio", "LAZIO"},
            {"ssc napoli", "NAPOLI"}, {"sheffield united", "SHEFFIELD UTD"}};

    /** Pure: a club's name as the intro writes it on its own: "Newcastle United" NEWCASTLE, "Manchester City" MAN CITY. */
    static String clubWord(String name) {
        String n = name == null ? "" : name.trim();
        String l = n.toLowerCase(Locale.ROOT);
        for (String[] w : WORDS) if (l.equals(w[0])) return w[1];
        String s = n.replaceAll("(?i)^(fc|afc|cf|ac|sc|ssc|as|ss) ", "").replaceAll("(?i) (fc|afc|cf)$", "");
        String t = s.replaceAll("(?i) (united|city|town|rovers|wanderers|athletic|albion|hotspur|county)$", "");
        if (t.replace(" ", "").length() >= 4) s = t;
        return s.toUpperCase(Locale.ROOT);
    }

    /** Pure: the three letters for a club: the feed's own (ESPN's "MNC" and "MAN" as the clubs write them), else its name's. */
    static String abbrOf(String abbr, String name) {
        String a = abbr == null ? "" : abbr.trim().toUpperCase(Locale.ROOT);
        if ("MNC".equals(a)) return "MCI";
        if ("MAN".equals(a) && name != null && name.toLowerCase(Locale.ROOT).contains("united")) return "MUN";
        if (a.length() >= 2 && a.length() <= 4 && a.matches("[A-Z0-9]+")) return a;
        return BroadcastData.shortOf(abbr, name);
    }

    private static final Pattern ESPN_LOGO = Pattern.compile("/teamlogos/soccer/\\d+/(\\d+)\\.png");

    /** Pure: ESPN's club id in its crest's address ("https://a.espncdn.com/i/teamlogos/soccer/500/361.png": "361"), else "". */
    static String espnId(String url) {
        if (url == null) return "";
        Matcher mm = ESPN_LOGO.matcher(url);
        return mm.find() ? mm.group(1) : "";
    }

    /** Pure: ESPN's slug for the competition ("eng.1"), from the feed's own or the competition's key; "" when not known. */
    static String slug(String compId, String compName) {
        String s = compId == null ? "" : compId.trim().toLowerCase(Locale.ROOT);
        if (s.contains(".")) return s;
        switch (BroadcastData.introTrophy(compId, compName)) {
            case "pl": return "eng.1";
            case "laliga": return "esp.1";
            case "seriea": return "ita.1";
            case "bundesliga": return "ger.1";
            case "ligue1": return "fra.1";
            case "ucl": return "uefa.champions";
            case "uel": return "uefa.europa";
            case "uecl": return "uefa.europa.conf";
            case "facup": return "eng.fa";
            case "leaguecup": return "eng.league_cup";
            default: return "";
        }
    }

    /** Pure: the ground from FotMob's match details (its info box), else the feed's venue. */
    static String groundOf(JSONObject details, String venue) {
        JSONObject c = details == null ? null : details.optJSONObject("content");
        JSONObject f = c == null ? null : c.optJSONObject("matchFacts");
        JSONObject ib = f == null ? null : f.optJSONObject("infoBox");
        JSONObject st = ib == null ? null : ib.optJSONObject("Stadium");
        String g = st == null ? "" : st.optString("name", "");
        return !g.isEmpty() ? g : venue == null ? "" : venue.trim();
    }

    /** Pure: FotMob's ids of the two clubs in its match details ({home, away}; 0 when not known). */
    static long[] teamIds(JSONObject details) {
        JSONObject g = details == null ? null : details.optJSONObject("general");
        JSONObject h = g == null ? null : g.optJSONObject("homeTeam"), a = g == null ? null : g.optJSONObject("awayTeam");
        return new long[]{h == null ? 0 : h.optLong("id"), a == null ? 0 : a.optLong("id")};
    }

    /** Pure: the starting eleven's FotMob ids for one side ("homeTeam"), empty before the line-ups are out. */
    static Set<Long> starters(JSONObject details, String side) {
        Set<Long> out = new HashSet<>();
        JSONObject c = details == null ? null : details.optJSONObject("content");
        JSONObject lu = c == null ? null : c.optJSONObject("lineup");
        JSONObject t = lu == null ? null : lu.optJSONObject(side);
        JSONArray st = t == null ? null : t.optJSONArray("starters");
        for (int i = 0; st != null && i < st.length(); i++) {
            JSONObject p = st.optJSONObject(i);
            if (p != null && p.optLong("id") > 0) out.add(p.optLong("id"));
        }
        return out.size() >= 11 ? out : new HashSet<>();
    }

    // ---- the players ------------------------------------------------------------------------------------------------

    /** One of FotMob's squad: what the intro needs of him. */
    static final class Member {
        final long id;
        final String name, pos;
        final int num;
        final double value;
        final boolean keeper;

        Member(long id, String name, int num, String pos, double value, boolean keeper) {
            this.id = id;
            this.name = name;
            this.num = num;
            this.pos = pos;
            this.value = value;
            this.keeper = keeper;
        }
    }

    /**
     * Pure: a club's best-known players from FotMob's team page (its "squad"): by transfer value, the injured and the
     * coach left out, a keeper counted at a third of his value (an intro is for the scorers); when the line-ups are out
     * (starters), only those in them. The first n... more are given, for those without a picture.
     */
    static List<Member> ranked(JSONObject team, Set<Long> starters) {
        List<Member> out = new ArrayList<>();
        JSONObject sq = team == null ? null : team.optJSONObject("squad");
        JSONArray groups = sq == null ? null : sq.optJSONArray("squad");
        for (int i = 0; groups != null && i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g == null || "coach".equals(g.optString("title"))) continue;
            JSONArray ms = g.optJSONArray("members");
            for (int k = 0; ms != null && k < ms.length(); k++) {
                JSONObject p = ms.optJSONObject(k);
                if (p == null || p.optLong("id") <= 0 || (p.has("injury") && !p.isNull("injury"))) continue;
                if (!starters.isEmpty() && !starters.contains(p.optLong("id"))) continue;
                JSONObject role = p.optJSONObject("role");
                boolean keeper = role != null && "keeper_long".equals(role.optString("key")) || "GK".equals(p.optString("positionIdsDesc"));
                String pos = p.optString("positionIdsDesc", "");
                int comma = pos.indexOf(',');
                double v = p.optDouble("transferValue", 0) + p.optDouble("rating", 0) * 1000;
                out.add(new Member(p.optLong("id"), p.optString("name"), p.optInt("shirtNumber", 0), comma > 0 ? pos.substring(0, comma) : pos,
                        keeper ? v / 3 : v, keeper));
            }
        }
        out.sort((a, b) -> Double.compare(b.value, a.value));
        return out;
    }

    /**
     * Pure: a player's two lines {first, last} as the name card writes them: the name he is known by big (the Premier
     * League's own when one word: "Gabriel", "Gonzalo"), the rest of his name small above, nothing above when he goes by
     * his first name.
     */
    static String[] nameLines(String name, String known) {
        String n = name == null ? "" : name.trim();
        String[] parts = n.isEmpty() ? new String[0] : n.split("\\s+");
        String k = known == null ? "" : known.trim();
        String last = !k.isEmpty() && !k.contains(" ") && !k.contains(".") && k.length() > 2 ? k : parts.length == 0 ? k : parts[parts.length - 1];
        StringBuilder first = new StringBuilder();
        for (String p : parts) {
            if (PlPhotos.fold(p).equals(PlPhotos.fold(last))) continue;
            if (first.length() > 0) first.append(' ');
            first.append(p);
        }
        String f = first.toString();
        if (parts.length > 1 && PlPhotos.fold(parts[0]).equals(PlPhotos.fold(last))) f = "";
        if (PlPhotos.fold(f).equals(PlPhotos.fold(last))) f = "";
        return new String[]{f.toUpperCase(Locale.ROOT), last.toUpperCase(Locale.ROOT)};
    }

    /** A club's players with their pictures (the team's FotMob id; its line-up's ids; its name for the lists). */
    static JSONArray players(long teamId, Set<Long> starters, String club, JSONObject boot, String[] laLiga, LaLigaPhotos.Budget budget,
                             File cacheDir) throws JSONException {
        JSONArray out = new JSONArray();
        JSONObject team = teamId > 0 ? squad(teamId, cacheDir) : null;
        List<Member> ms = ranked(team, starters);
        List<PlPhotos.Element> els = boot == null ? new ArrayList<>() : PlPhotos.club(boot, club);
        if (ms.isEmpty() && !els.isEmpty() && boot != null) return fplPlayers(boot, club, STARS);
        int tried = 0;
        for (Member mm : ms) {
            if (out.length() >= STARS || ++tried > STARS + 4) break;
            String last = mm.name.trim().replaceAll(".*\\s", "");
            PlPhotos.Element e = laLiga == null ? PlPhotos.elementFor(els, mm.name, last) : null;
            String photo = null;
            boolean face = false;
            if (laLiga != null) photo = LaLigaPhotos.photo(mm.name, last, laLiga, cacheDir, budget);
            else if (e != null) {
                Bitmap bm = PlPhotos.picture(e.code);
                if (bm != null) photo = BroadcastData.dataUrl(bm, true);
            }
            if (photo == null) {
                photo = BroadcastData.image(null, "https://images.fotmob.com/image_resources/playerimages/" + mm.id + ".png", true);
                face = photo != null;
            }
            if (photo == null) continue;
            String[] lines = nameLines(mm.name, e == null ? "" : e.web);
            JSONObject p = new JSONObject().put("first", lines[0]).put("last", lines[1]).put("pos", mm.pos).put("photo", photo);
            if (mm.num > 0) p.put("num", mm.num);
            if (face) p.put("face", true);
            out.put(p);
        }
        return out;
    }

    /** A club's players from the Premier League's own list alone (no FotMob page): its dearest, without their numbers. */
    static JSONArray fplPlayers(JSONObject boot, String club, int n) throws JSONException {
        JSONArray out = new JSONArray();
        for (JSONObject e : PlPhotos.dearest(boot, club, n + 3)) {
            if (out.length() >= n) break;
            Bitmap bm = PlPhotos.picture(e.optInt("code"));
            if (bm == null) continue;
            String[] lines = nameLines(e.optString("first_name") + " " + e.optString("second_name"), e.optString("web_name"));
            out.put(new JSONObject().put("first", lines[0]).put("last", lines[1]).put("pos", PlPhotos.place(e.optInt("element_type")))
                    .put("photo", BroadcastData.dataUrl(bm, true)));
        }
        return out;
    }

    /** FotMob's team page for a club, kept on disk a while (cacheDir/fm-team-ID.json); null when it cannot be had. */
    static JSONObject squad(long teamId, File cacheDir) {
        File f = cacheDir == null ? null : new File(cacheDir, "fm-team-" + teamId + ".json");
        long now = System.currentTimeMillis();
        try {
            if (f != null && f.isFile() && now - f.lastModified() < KEEP_MS) {
                return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
        try {
            String s = Net.text(FOTMOB_TEAM + teamId);
            JSONObject t = new JSONObject(s);
            if (f != null) Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
            return t;
        } catch (Exception e) {
            try {
                return f != null && f.isFile() ? new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)) : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    // ---- the competition's other games that day ------------------------------------------------------------------

    /** The competition's other games around the kick-off (ESPN's scoreboard), each its two clubs {name, full, abbr, ...}. */
    static JSONArray fixtures(String slug, long kickoff, String home, String away) {
        try {
            LocalDate d = Instant.ofEpochMilli(kickoff).atZone(ZoneOffset.UTC).toLocalDate();
            DateTimeFormatter f = DateTimeFormatter.BASIC_ISO_DATE;
            String url = String.format(Locale.ROOT, SCOREBOARD, slug, d.minusDays(1).format(f), d.plusDays(1).format(f));
            return others(new JSONObject(Net.text(url)), kickoff, home, away);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Pure: from ESPN's scoreboard, the other games within a day and a half of the kick-off, nearest first. */
    static JSONArray others(JSONObject board, long kickoff, String home, String away) throws JSONException {
        List<Object[]> games = new ArrayList<>();
        JSONArray ev = board == null ? null : board.optJSONArray("events");
        for (int i = 0; ev != null && i < ev.length(); i++) {
            JSONObject e = ev.optJSONObject(i);
            JSONArray comps = e == null ? null : e.optJSONArray("competitions");
            JSONObject c = comps == null ? null : comps.optJSONObject(0);
            JSONArray cs = c == null ? null : c.optJSONArray("competitors");
            if (cs == null || cs.length() != 2) continue;
            long at = parseTime(e.optString("date"));
            if (kickoff > 0 && at > 0 && Math.abs(at - kickoff) > 36 * 3600_000L) continue;
            JSONObject[] two = new JSONObject[2];
            boolean ours = false;
            for (int k = 0; k < 2; k++) {
                JSONObject q = cs.optJSONObject(k);
                JSONObject t = q == null ? null : q.optJSONObject("team");
                if (t == null) break;
                String name = t.optString("displayName");
                if (same(name, home) || same(name, away)) ours = true;
                JSONObject o = new JSONObject().put("full", name).put("name", clubWord(name))
                        .put("abbr", abbrOf(t.optString("abbreviation"), name)).put("espn", t.optString("id"));
                if (!t.optString("color").isEmpty()) o.put("color", "#" + t.optString("color"));
                if (!t.optString("alternateColor").isEmpty()) o.put("color2", "#" + t.optString("alternateColor"));
                o.put("logo", t.optString("logo"));
                two["home".equals(q.optString("homeAway")) ? 0 : 1] = o;
            }
            if (ours || two[0] == null || two[1] == null) continue;
            games.add(new Object[]{Math.abs(at - kickoff), new JSONArray().put(two[0]).put(two[1])});
        }
        games.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
        JSONArray out = new JSONArray();
        for (Object[] g : games) out.put(g[1]);
        return out;
    }

    private static boolean same(String a, String b) {
        String x = PlPhotos.fold(a), y = PlPhotos.fold(b);
        return !x.isEmpty() && !y.isEmpty() && (x.equals(y) || x.startsWith(y) || y.startsWith(x));
    }

    private static long parseTime(String iso) {
        try {
            return Instant.parse(iso.length() == 17 ? iso.replace("Z", ":00Z") : iso).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Two of the round's other games' best-known players face to face (the Premier League's list): [[home's, away's], ...]. */
    static JSONArray duels(JSONObject boot, JSONArray games, ExecutorService pool) {
        JSONArray out = new JSONArray();
        try {
            List<Object[]> scored = new ArrayList<>();
            for (int i = 0; i < games.length(); i++) {
                JSONArray g = games.getJSONArray(i);
                scored.add(new Object[]{PlPhotos.strength(boot, g.getJSONObject(0).optString("full")) + PlPhotos.strength(boot, g.getJSONObject(1).optString("full")), g});
            }
            scored.sort((a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
            List<Future<JSONObject>> picks = new ArrayList<>();
            List<JSONObject> teams = new ArrayList<>();
            for (int i = 0; i < Math.min(2, scored.size()); i++) {
                JSONArray g = (JSONArray) scored.get(i)[1];
                for (int k = 0; k < 2; k++) {
                    JSONObject t = new JSONObject(g.getJSONObject(k).toString());
                    teams.add(t);
                    picks.add(pool.submit(() -> {
                        JSONArray one = fplPlayers(boot, t.optString("full"), 1);
                        return one.length() == 0 ? null : one.getJSONObject(0);
                    }));
                }
            }
            List<Future<String>> crests = new ArrayList<>();
            for (JSONObject t : teams) {
                String url = t.optString("logo");
                crests.add(pool.submit(() -> BroadcastData.image(null, url, false)));
            }
            for (int i = 0; i + 1 < picks.size(); i += 2) {
                JSONObject a = picks.get(i).get(40, TimeUnit.SECONDS), b = picks.get(i + 1).get(40, TimeUnit.SECONDS);
                if (a == null || b == null) continue;
                for (int k = 0; k < 2; k++) {
                    JSONObject t = teams.get(i + k);
                    String c = crests.get(i + k).get(20, TimeUnit.SECONDS);
                    t.remove("logo");
                    if (c != null) t.put("crest", c);
                }
                out.put(new JSONArray().put(a.put("team", teams.get(i))).put(b.put("team", teams.get(i + 1))));
            }
        } catch (Exception ignored) {
            // the page has the match's own players face to face then
        }
        return out;
    }
}
