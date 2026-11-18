package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.live.FotMob;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A club's page, read off the main thread. ESPN's side of it: its standing, its season's record with the home and away
 * splits, the competition's results (its form, last game, clean sheets) and its next game, and its squad. FotMob's,
 * found by one of its games: the squad with every player's picture, age, country and season, the season's figures with
 * the club's place in the league for each (xG, possession, shots, cards…), its leading players, its last and next games
 * in every competition and its ground. Each reading is pure, for the tests.
 */
final class TeamData {
    /** FotMob's pictures of a club's crest and of a country's flag, by its id or its three-letter code. */
    static final String FOTMOB_CREST = "https://images.fotmob.com/image_resources/logo/teamlogo/";

    /** A season's record: every game, or those at home, or away. */
    static final class Record {
        int played, won, drawn, lost, scored, conceded;
        /** ESPN's points (after any deduction); -1 when not given, then counted from the results. */
        int points = -1;

        boolean has() {
            return played > 0;
        }

        int points() {
            return points >= 0 ? points : won * 3 + drawn;
        }

        int difference() {
            return scored - conceded;
        }
    }

    /** One of the club's games: the other club, home or away, when, the competition, and the score once played. */
    static final class Fixture {
        String other = "", otherLogo = "", comp = "";
        boolean home, played;
        long when;
        int scored = -1, conceded = -1;

        /** W, D or L; a space when not played. */
        char result() {
            if (!played || scored < 0 || conceded < 0) return ' ';
            return scored > conceded ? 'W' : scored < conceded ? 'L' : 'D';
        }
    }

    /** One of the season's figures (FotMob's): its key and name, the club's value, its place in the league (0: none). */
    static final class Figure {
        final String key, name, value;
        final int rank;

        Figure(String key, String name, String value, int rank) {
            this.key = key;
            this.name = name;
            this.value = value;
            this.rank = rank;
        }
    }

    /** A player at the top of one of the club's lists: name, value, picture. */
    static final class Leader {
        final String name, value, photo;

        Leader(String name, String value, String photo) {
            this.name = name;
            this.value = value;
            this.photo = photo;
        }
    }

    /** One of the club's lists ("Top scorer"): its key, its name and the club's best three. */
    static final class Board {
        final String key, name;
        final List<Leader> top = new ArrayList<>();

        Board(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    /** One of the squad. */
    static final class Player {
        String name = "", number = "", country = "", flag = "", photo = "", injury = "";
        int age, goals, assists, yellow, red;
        double rating;
        /** The name as the cards set it, first name over surname (ESPN's parts when they make up the name). */
        String first = "", last = "";
        /** His place in words ("Right-back", "Defender") and the others FotMob lists ("LB, LWB"); "" when not given. */
        String position = "", also = "";
        /** The line of the squad he is in (Goalkeepers, Defenders, Midfielders, Forwards, Others, Manager). */
        String line = "";
        /** ESPN's head-and-shoulders cut-out when ESPN has one, and ESPN's id for him. */
        String cutout = "", espnId = "";
        /**
         * His league's own cut-out (the Premier League's or La Liga's, head to waist in the kit: LeaguePhotos), looked up
         * when his card first asks for its picture: "" when the league has none of him, null until looked up.
         */
        volatile String league;
        /** His height in cm and his date of birth ("1999-03-18"), when a source gives them. */
        int height;
        String born = "";
        /** FotMob's id (0 when not from FotMob). */
        int fotmobId;
        /** ESPN's figures for his season in the competition, by ESPN's names ("appearances", "totalGoals"), as given. */
        final Map<String, Integer> espnStats = new LinkedHashMap<>();
        /** FotMob's season figures it gave a value for: "goals", "assists", "ycards", "rcards", "rating". */
        final Map<String, Double> fotmobStats = new LinkedHashMap<>();
    }

    /** A line of the squad (Goalkeepers, Defenders…) and its players. */
    static final class Group {
        final String title;
        final List<Player> players = new ArrayList<>();

        Group(String title) {
            this.title = title;
        }
    }

    String slug = "", id = "", name = "", shortName = "", logo = "", color = "", standing = "", venue = "";
    /** ESPN's other colour for the club ("" when not given), for a club whose first colour is white or grey. */
    String alternate = "";
    /** The season ESPN's figures for the squad are from ("2026-27 English Premier League"); "" when not given. */
    String season = "";
    /** ESPN's squad, every player once (its ids, cut-outs and season figures, for FotMob's squad to take on). */
    final List<Player> espnPlayers = new ArrayList<>();
    int rank;
    final Record all = new Record(), home = new Record(), away = new Record();
    /** The competition's games played, newest first (ESPN's). */
    final List<Fixture> results = new ArrayList<>();
    /** The next game and the last: in any competition once FotMob is read, else this competition's. */
    Fixture next, last;
    /** FotMob's form in every competition, oldest first ("LLWD"); "" when not read. */
    String formEverywhere = "";
    final List<Figure> figures = new ArrayList<>();
    final List<Board> boards = new ArrayList<>();
    final List<Group> squad = new ArrayList<>();
    /** Where the squad came from: "FotMob", "ESPN" or "". */
    String squadFrom = "";
    /** Whether FotMob's part is in. */
    boolean fotmob;
    /** Whether the page's reading is over (FotMob's part in, or not to come): kept as shown for the player's page. */
    boolean done;

    TeamData() {
    }

    private static final Map<String, Object[]> READ = new HashMap<>();

    /** The page read in the last ten minutes, FotMob's part and all; null when there is none. */
    static TeamData kept(String slug, String id) {
        synchronized (READ) {
            Object[] had = READ.get(slug + ":" + id);
            return had != null && System.currentTimeMillis() - (Long) had[0] < 10 * 60_000L ? (TeamData) had[1] : null;
        }
    }

    /** ESPN's part (off the main thread): the club, its record and standing, the competition's results, its squad. */
    static TeamData espn(String slug, String id, String name, String logo) {
        TeamData t = new TeamData();
        t.slug = slug;
        t.id = id;
        t.name = name == null ? "" : name;
        t.shortName = t.name;
        t.logo = logo == null ? "" : logo;
        espnTeam(t, Espn.site(slug + "/teams/" + id, 10 * 60_000L));
        JSONObject sched = Espn.site(slug + "/teams/" + id + "/schedule", 10 * 60_000L);
        if (sched != null) results(t, sched);
        JSONObject roster = Espn.site(slug + "/teams/" + id + "/roster", 6 * 3_600_000L);
        if (roster != null) {
            t.squad.addAll(espnSquad(roster));
            if (!t.squad.isEmpty()) t.squadFrom = "ESPN";
            for (Group g : t.squad) if (!g.title.equals("Manager")) t.espnPlayers.addAll(g.players);
            t.season = rosterSeason(roster);
        }
        return t;
    }

    private static final Map<String, Object[]> SHOWN = new HashMap<>();

    /** Keeps the page as last shown, for a player's page opened from it (the same squad, the same order). */
    static void shown(TeamData t) {
        if (t == null) return;
        synchronized (SHOWN) {
            if (SHOWN.size() > 8) SHOWN.clear();
            SHOWN.put(t.slug + ":" + t.id, new Object[]{System.currentTimeMillis(), t});
        }
    }

    /** The page as last shown (within the hour), or null. */
    static TeamData lastShown(String slug, String id) {
        synchronized (SHOWN) {
            Object[] had = SHOWN.get(slug + ":" + id);
            return had != null && System.currentTimeMillis() - (Long) had[0] < 3_600_000L ? (TeamData) had[1] : null;
        }
    }

    /** What a page's reading gives as it comes: ESPN's part first, then (done) with FotMob's when it has the club. */
    interface Step {
        void got(TeamData t, boolean done);
    }

    /**
     * Reads a club's page off the main thread, telling step on the main thread: ESPN's part as soon as it is in, then
     * the whole page (or ESPN's alone, or null, when FotMob does not have the club). One read for the page's opening;
     * the feeds' own caches keep a second opening from reading again.
     */
    static void read(Context c, String slug, String id, String name, String logo, Step step) {
        Context app = c.getApplicationContext();
        com.gathin.tvhomephone.App.io.execute(() -> {
            TeamData espn;
            try {
                espn = espn(slug, id, name, logo);
            } catch (RuntimeException e) {
                espn = null;
            }
            TeamData first = espn;
            if (first != null) com.gathin.tvhomephone.App.main.post(() -> step.got(first, false));
            TeamData full;
            try {
                full = first == null ? null : withFotMob(app, first);
            } catch (RuntimeException e) {
                full = null;
            }
            TeamData got = full != null ? full : first;
            com.gathin.tvhomephone.App.main.post(() -> step.got(got, true));
        });
    }

    /**
     * ESPN's part with FotMob's added (off the main thread), as a new page: null when FotMob does not have the club.
     * FotMob's id for it is found once, by one of its games, and kept.
     */
    static TeamData withFotMob(Context c, TeamData espn) {
        int fm = fotmobId(c, espn);
        JSONObject root = fm > 0 ? FotMob.team(fm) : null;
        if (root == null) return null;
        TeamData t = espn.copy();
        fotmob(t, root);
        if (!t.fotmob) return null;
        if ("FotMob".equals(t.squadFrom)) merge(t.squad, t.espnPlayers);
        synchronized (READ) {
            if (READ.size() > 12) READ.clear();
            READ.put(t.slug + ":" + t.id, new Object[]{System.currentTimeMillis(), t});
        }
        return t;
    }

    private static int fotmobId(Context c, TeamData t) {
        SharedPreferences kept = c.getApplicationContext().getSharedPreferences("fotmob_teams", Context.MODE_PRIVATE);
        int known = kept.getInt(t.id, 0);
        if (known > 0) return known;
        List<Fixture> games = new ArrayList<>();
        if (!t.results.isEmpty()) games.add(t.results.get(0));
        if (t.next != null) games.add(t.next);
        if (t.results.size() > 1) games.add(t.results.get(1));
        for (Fixture f : games) {
            if (f.when <= 0 || f.other.isEmpty()) continue;
            String home = f.home ? t.name : f.other, away = f.home ? f.other : t.name;
            int got = FotMob.teamId(home, away, f.when, f.home);
            if (got > 0) {
                kept.edit().putInt(t.id, got).apply();
                return got;
            }
        }
        return 0;
    }

    private TeamData copy() {
        TeamData t = new TeamData();
        t.slug = slug;
        t.id = id;
        t.name = name;
        t.shortName = shortName;
        t.logo = logo;
        t.color = color;
        t.alternate = alternate;
        t.season = season;
        t.espnPlayers.addAll(espnPlayers);
        t.standing = standing;
        t.venue = venue;
        t.rank = rank;
        copy(all, t.all);
        copy(home, t.home);
        copy(away, t.away);
        t.results.addAll(results);
        t.next = next;
        t.last = last;
        t.formEverywhere = formEverywhere;
        t.figures.addAll(figures);
        t.boards.addAll(boards);
        t.squad.addAll(squad);
        t.squadFrom = squadFrom;
        t.fotmob = fotmob;
        t.done = done;
        return t;
    }

    private static void copy(Record from, Record to) {
        to.played = from.played;
        to.won = from.won;
        to.drawn = from.drawn;
        to.lost = from.lost;
        to.scored = from.scored;
        to.conceded = from.conceded;
        to.points = from.points;
    }

    // ---- ESPN ---------------------------------------------------------------------------------------------------

    /** Pure: ESPN's club (…/teams/{id}): its names, crest, colour, standing, record (with home and away) and next game. */
    static void espnTeam(TeamData t, JSONObject root) {
        JSONObject team = root == null ? null : root.optJSONObject("team");
        if (team == null) return;
        t.name = str(team, "displayName", t.name);
        t.shortName = str(team, "shortDisplayName", t.name);
        if (t.logo.isEmpty()) t.logo = firstLogo(team);
        t.color = str(team, "color", "");
        t.alternate = str(team, "alternateColor", "");
        t.standing = str(team, "standingSummary", "");
        JSONObject rec = team.optJSONObject("record");
        JSONArray items = rec == null ? null : rec.optJSONArray("items");
        JSONObject total = null;
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject it = items.optJSONObject(i);
            if (it != null && (total == null || "total".equals(str(it, "type")))) total = it;
        }
        if (total != null) {
            Map<String, Double> v = new HashMap<>();
            JSONArray st = total.optJSONArray("stats");
            for (int i = 0; st != null && i < st.length(); i++) {
                JSONObject s = st.optJSONObject(i);
                if (s != null && !s.isNull("value")) v.put(str(s, "name"), s.optDouble("value", 0));
            }
            fill(t.all, v, "");
            fill(t.home, v, "home");
            fill(t.away, v, "away");
            if (v.containsKey("points")) t.all.points = (int) Math.round(v.get("points"));
            if (v.containsKey("rank")) t.rank = (int) Math.round(v.get("rank"));
        }
        JSONArray ne = team.optJSONArray("nextEvent");
        Fixture f = ne == null || ne.length() == 0 ? null : fixture(ne.optJSONObject(0), t.id);
        if (f != null && !f.played) t.next = f;
    }

    private static void fill(Record r, Map<String, Double> v, String prefix) {
        r.played = num(v, prefix, "gamesPlayed");
        r.won = num(v, prefix, "wins");
        r.drawn = num(v, prefix, "ties");
        r.lost = num(v, prefix, "losses");
        r.scored = num(v, prefix, "pointsFor");
        r.conceded = num(v, prefix, "pointsAgainst");
    }

    private static int num(Map<String, Double> v, String prefix, String name) {
        String key = prefix.isEmpty() ? name : prefix + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Double d = v.get(key);
        return d == null ? 0 : (int) Math.round(d);
    }

    /** Pure: the competition's games played (…/teams/{id}/schedule), newest first; the record from them when ESPN gave none. */
    static void results(TeamData t, JSONObject root) {
        JSONArray events = root.optJSONArray("events");
        List<Fixture> played = new ArrayList<>();
        for (int i = 0; events != null && i < events.length(); i++) {
            Fixture f = fixture(events.optJSONObject(i), t.id);
            if (f != null && f.played) played.add(f);
        }
        played.sort((a, b) -> Long.compare(b.when, a.when));
        t.results.clear();
        t.results.addAll(played);
        if (!played.isEmpty() && t.last == null) t.last = played.get(0);
        if (!t.all.has()) recordFrom(played, t.all, t.home, t.away);
    }

    /** Pure: one of ESPN's events as the club's game; null when the club is not in it. */
    static Fixture fixture(JSONObject e, String teamId) {
        if (e == null) return null;
        JSONArray comps = e.optJSONArray("competitions");
        JSONObject comp = comps == null ? null : comps.optJSONObject(0);
        JSONArray cs = comp == null ? null : comp.optJSONArray("competitors");
        if (cs == null || cs.length() < 2) return null;
        JSONObject us = null, them = null;
        for (int i = 0; i < cs.length(); i++) {
            JSONObject c = cs.optJSONObject(i);
            JSONObject team = c == null ? null : c.optJSONObject("team");
            if (team == null) continue;
            if (teamId.equals(str(team, "id"))) us = c;
            else them = c;
        }
        if (us == null || them == null) return null;
        JSONObject other = them.optJSONObject("team");
        Fixture f = new Fixture();
        f.other = str(other, "shortDisplayName", str(other, "displayName"));
        f.otherLogo = firstLogo(other);
        f.home = "home".equals(str(us, "homeAway"));
        f.when = time(str(e, "date", str(comp, "date")));
        JSONObject league = e.optJSONObject("league");
        f.comp = league == null ? "" : str(league, "abbreviation", str(league, "name"));
        JSONObject status = comp.optJSONObject("status");
        JSONObject type = status == null ? null : status.optJSONObject("type");
        f.played = type != null && (type.has("completed") ? type.optBoolean("completed") : "post".equals(str(type, "state")));
        if (f.played) {
            f.scored = score(us);
            f.conceded = score(them);
            if (f.scored < 0 || f.conceded < 0) f.played = false;
        }
        return f;
    }

    private static int score(JSONObject competitor) {
        Object s = competitor.opt("score");
        if (s instanceof JSONObject) {
            JSONObject o = (JSONObject) s;
            if (o.has("value") && !o.isNull("value")) return (int) Math.round(o.optDouble("value", -1));
            return whole(str(o, "displayValue"));
        }
        if (s instanceof Number) return ((Number) s).intValue();
        return s instanceof String ? whole((String) s) : -1;
    }

    private static int whole(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    /** Pure: the record counted from the games played, at home and away. */
    static void recordFrom(List<Fixture> games, Record all, Record home, Record away) {
        for (Fixture f : games) {
            char r = f.result();
            if (r == ' ') continue;
            for (Record x : new Record[]{all, f.home ? home : away}) {
                x.played++;
                if (r == 'W') x.won++;
                else if (r == 'D') x.drawn++;
                else x.lost++;
                x.scored += f.scored;
                x.conceded += f.conceded;
            }
        }
    }

    /** Pure: ESPN's squad (…/teams/{id}/roster) by position, with numbers, ages and flags, ESPN's pictures where it has them. */
    static List<Group> espnSquad(JSONObject root) {
        Map<String, Group> by = new LinkedHashMap<>();
        for (String g : new String[]{"Manager", "Goalkeepers", "Defenders", "Midfielders", "Forwards", "Others"}) by.put(g, new Group(g));
        // ESPN's coach is the manager only when it names one: a list of several is the club's past managers.
        JSONArray coach = root.optJSONArray("coach");
        JSONObject boss = coach == null || coach.length() != 1 ? null : coach.optJSONObject(0);
        if (boss != null) {
            Player p = new Player();
            p.first = str(boss, "firstName", "");
            p.last = str(boss, "lastName", "");
            p.name = (p.first + " " + p.last).trim();
            p.line = "Manager";
            p.position = "Manager";
            if (!p.name.isEmpty()) by.get("Manager").players.add(p);
        }
        JSONArray athletes = root.optJSONArray("athletes");
        List<JSONObject> all = new ArrayList<>();
        for (int i = 0; athletes != null && i < athletes.length(); i++) {
            JSONObject a = athletes.optJSONObject(i);
            if (a == null) continue;
            JSONArray items = a.optJSONArray("items");
            if (items == null) all.add(a);
            for (int k = 0; items != null && k < items.length(); k++) if (items.optJSONObject(k) != null) all.add(items.optJSONObject(k));
        }
        for (JSONObject a : all) {
            Player p = new Player();
            p.name = str(a, "displayName", str(a, "fullName", ""));
            if (p.name.isEmpty()) continue;
            p.number = str(a, "jersey", "");
            p.age = a.optInt("age");
            p.country = str(a, "citizenship", "");
            JSONObject flag = a.optJSONObject("flag");
            p.flag = flag == null ? "" : str(flag, "href", "");
            JSONObject head = a.optJSONObject("headshot");
            p.photo = head == null ? "" : str(head, "href", "");
            p.cutout = p.photo;
            p.espnId = str(a, "id", "");
            p.first = str(a, "firstName", "");
            p.last = str(a, "lastName", "");
            JSONObject pos = a.optJSONObject("position");
            String where = pos == null ? "" : str(pos, "displayName", str(pos, "name", ""));
            p.position = where;
            double inches = a.optDouble("height", 0);
            if (inches > 40 && inches < 100) p.height = (int) Math.round(inches * 2.54);
            p.born = day(str(a, "dateOfBirth", ""));
            espnStats(a, p.espnStats);
            p.line = lineOf(where);
            by.get(p.line).players.add(p);
        }
        List<Group> out = new ArrayList<>();
        for (Group g : by.values()) if (!g.players.isEmpty()) out.add(g);
        return out;
    }

    /** Pure: a player's figures in ESPN's roster (statistics' splits, every category), by ESPN's names, as given. */
    static void espnStats(JSONObject athlete, Map<String, Integer> into) {
        JSONObject st = athlete.optJSONObject("statistics");
        JSONObject splits = st == null ? null : st.optJSONObject("splits");
        JSONArray cats = splits == null ? null : splits.optJSONArray("categories");
        for (int i = 0; cats != null && i < cats.length(); i++) {
            JSONObject cat = cats.optJSONObject(i);
            JSONArray stats = cat == null ? null : cat.optJSONArray("stats");
            for (int k = 0; stats != null && k < stats.length(); k++) {
                JSONObject s = stats.optJSONObject(k);
                if (s == null || s.isNull("value") || !s.has("value")) continue;
                double v = s.optDouble("value", Double.NaN);
                String n = str(s, "name", "");
                if (n.isEmpty() || Double.isNaN(v) || Double.isInfinite(v) || v < 0) continue;
                into.put(n, (int) Math.round(v));
            }
        }
    }

    /** Pure: the season ESPN's roster is for, as it names it ("2026-27 English Premier League"); "" when not given. */
    static String rosterSeason(JSONObject root) {
        JSONObject s = root == null ? null : root.optJSONObject("season");
        return s == null ? "" : str(s, "displayName", str(s, "name", ""));
    }

    /** Pure: the day part of an ISO date ("1999-03-18T07:00Z" to "1999-03-18"); "" when it is not one. */
    static String day(String iso) {
        if (iso == null || iso.length() < 10) return "";
        String d = iso.substring(0, 10);
        return d.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") ? d : "";
    }

    /**
     * Pure: ESPN's extras for each of FotMob's players (matched by name, else by shirt number and surname): ESPN's id and
     * cut-out, the name's parts, height and birthday where FotMob has none, and ESPN's season figures.
     */
    static void merge(List<Group> squad, List<Player> espn) {
        if (espn == null || espn.isEmpty()) return;
        for (Group g : squad) {
            if (g.title.equals("Manager")) continue;
            for (Player p : g.players) {
                Player e = match(p, espn);
                if (e == null) continue;
                if (p.cutout.isEmpty()) p.cutout = e.cutout;
                if (p.espnId.isEmpty()) p.espnId = e.espnId;
                if (p.first.isEmpty() && p.last.isEmpty()) {
                    p.first = e.first;
                    p.last = e.last;
                }
                if (p.height <= 0) p.height = e.height;
                if (p.born.isEmpty()) p.born = e.born;
                if (p.espnStats.isEmpty()) p.espnStats.putAll(e.espnStats);
            }
        }
    }

    /** Pure: ESPN's player for one of FotMob's: the same name; else the same shirt number and the same surname. */
    static Player match(Player p, List<Player> espn) {
        Player nameOnly = null;
        for (Player e : espn) {
            if (PlayerPhotos.sameName(p.name, e.name)) {
                if (p.number.isEmpty() || e.number.isEmpty() || p.number.equals(e.number)) return e;
                if (nameOnly == null) nameOnly = e;
            }
        }
        if (nameOnly != null) return nameOnly;
        if (p.number.isEmpty()) return null;
        String surname = lastWord(PlayerPhotos.plain(p.name));
        for (Player e : espn) {
            if (p.number.equals(e.number) && !surname.isEmpty() && surname.equals(lastWord(PlayerPhotos.plain(e.name)))) return e;
        }
        return null;
    }

    private static String lastWord(String s) {
        int sp = s.lastIndexOf(' ');
        return sp >= 0 ? s.substring(sp + 1) : s;
    }

    private static String lineOf(String position) {
        String p = position.toLowerCase(Locale.ROOT);
        if (p.contains("goal") || p.equals("g") || p.equals("gk")) return "Goalkeepers";
        if (p.contains("defen") || p.contains("back")) return "Defenders";
        if (p.contains("midfield")) return "Midfielders";
        if (p.contains("forward") || p.contains("striker") || p.contains("wing") || p.contains("attack")) return "Forwards";
        return "Others";
    }

    // ---- FotMob -------------------------------------------------------------------------------------------------

    /** Pure: FotMob's club (teams?id=) added: its squad, figures, leading players, form, last and next games, ground. */
    static void fotmob(TeamData t, JSONObject root) {
        JSONObject details = root.optJSONObject("details");
        int fm = details == null ? 0 : details.optInt("id");
        List<Group> squad = fotmobSquad(root);
        if (!squad.isEmpty()) {
            t.squad.clear();
            t.squad.addAll(squad);
            t.squadFrom = "FotMob";
        }
        t.figures.clear();
        t.figures.addAll(figures(root));
        t.boards.clear();
        t.boards.addAll(boards(root));
        JSONObject ov = root.optJSONObject("overview");
        if (ov != null) {
            Fixture next = fotmobFixture(ov.optJSONObject("nextMatch"), fm);
            if (next != null && !next.played) t.next = next;
            Fixture last = fotmobFixture(ov.optJSONObject("lastMatch"), fm);
            if (last != null && last.played) t.last = last;
            t.formEverywhere = fotmobForm(ov.optJSONArray("teamForm"));
            t.venue = venue(ov.optJSONObject("venue"));
        }
        t.fotmob = !squad.isEmpty() || !t.figures.isEmpty() || !t.boards.isEmpty();
    }

    /** Pure: FotMob's squad by its lines, the manager first: every player's picture, number, country, age and season. */
    static List<Group> fotmobSquad(JSONObject root) {
        List<Group> out = new ArrayList<>();
        JSONObject sq = root.optJSONObject("squad");
        JSONArray groups = sq == null ? null : sq.optJSONArray("squad");
        for (int i = 0; groups != null && i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            JSONArray ms = g == null ? null : g.optJSONArray("members");
            if (ms == null) continue;
            Group line = new Group(lineTitle(str(g, "title")));
            for (int k = 0; k < ms.length(); k++) {
                JSONObject m = ms.optJSONObject(k);
                if (m == null || str(m, "name", "").isEmpty()) continue;
                Player p = new Player();
                p.name = str(m, "name");
                int pid = m.optInt("id");
                p.photo = pid > 0 ? FotMob.photoUrl(pid) : "";
                p.number = m.has("shirtNumber") && !m.isNull("shirtNumber") ? str(m, "shirtNumber") : "";
                p.country = str(m, "cname", "");
                p.flag = flag(str(m, "ccode", ""));
                p.age = m.optInt("age");
                p.goals = m.optInt("goals");
                p.assists = m.optInt("assists");
                p.yellow = m.optInt("ycards");
                p.red = m.optInt("rcards");
                p.rating = m.optDouble("rating", 0);
                if (Double.isNaN(p.rating)) p.rating = 0;
                JSONObject injury = m.optJSONObject("injury");
                if (injury != null) p.injury = str(injury, "expectedReturn", "");
                if (injury != null && p.injury.isEmpty()) p.injury = "Injured";
                p.fotmobId = Math.max(0, pid);
                p.line = line.title;
                boolean boss = line.title.equals("Manager");
                String[] where = boss ? new String[]{"Manager", ""} : PlayerFacts.positions(str(m, "positionIdsDesc", ""));
                p.position = where[0];
                p.also = where[1];
                if (p.position.isEmpty()) {
                    JSONObject role = m.optJSONObject("role");
                    p.position = role == null ? "" : str(role, "fallback", "");
                }
                int cm = m.optInt("height");
                if (cm > 120 && cm < 230) p.height = cm;
                p.born = day(str(m, "dateOfBirth", ""));
                if (!boss) {
                    for (String key : new String[]{"goals", "assists", "ycards", "rcards", "rating"}) {
                        if (!m.has(key) || m.isNull(key)) continue;
                        double v = m.optDouble(key, Double.NaN);
                        if (!Double.isNaN(v) && !Double.isInfinite(v) && v >= 0) p.fotmobStats.put(key, v);
                    }
                }
                line.players.add(p);
            }
            if (!line.players.isEmpty()) out.add(line);
        }
        return out;
    }

    /** Pure: FotMob's name for a line of the squad, as the page says it. */
    static String lineTitle(String key) {
        switch (key == null ? "" : key.toLowerCase(Locale.ROOT)) {
            case "coach": return "Manager";
            case "keepers": return "Goalkeepers";
            case "defenders": return "Defenders";
            case "midfielders": return "Midfielders";
            case "attackers": return "Forwards";
            default: return key == null || key.isEmpty() ? "Others" : Character.toUpperCase(key.charAt(0)) + key.substring(1);
        }
    }

    /** Pure: a country's flag at FotMob by its three-letter code ("" for none). */
    static String flag(String code) {
        return code == null || code.isEmpty() ? "" : FOTMOB_CREST + code.toLowerCase(Locale.ROOT) + ".png";
    }

    /** Pure: the club's season figures (FotMob's stats.teams): its value in each and its place in the league. */
    static List<Figure> figures(JSONObject root) {
        List<Figure> out = new ArrayList<>();
        JSONObject stats = root.optJSONObject("stats");
        JSONArray teams = stats == null ? null : stats.optJSONArray("teams");
        for (int i = 0; teams != null && i < teams.length(); i++) {
            JSONObject cat = teams.optJSONObject(i);
            JSONObject p = cat == null ? null : cat.optJSONObject("participant");
            if (p == null || p.isNull("value")) continue;
            JSONObject s = p.optJSONObject("stat");
            String v = value(p.optDouble("value"), s == null ? "" : str(s, "format"), s == null ? 0 : s.optInt("fractions"));
            if (v.isEmpty()) continue;
            out.add(new Figure(s == null ? "" : str(s, "name"), str(cat, "header"), v, p.optInt("rank")));
        }
        return out;
    }

    /** Pure: the club's lists of its best players (FotMob's stats.players), each with its top three. */
    static List<Board> boards(JSONObject root) {
        List<Board> out = new ArrayList<>();
        JSONObject stats = root.optJSONObject("stats");
        JSONArray players = stats == null ? null : stats.optJSONArray("players");
        for (int i = 0; players != null && i < players.length(); i++) {
            JSONObject cat = players.optJSONObject(i);
            JSONArray top = cat == null ? null : cat.optJSONArray("topThree");
            if (top == null || top.length() == 0) continue;
            JSONObject first = top.optJSONObject(0);
            JSONObject fs = first == null ? null : first.optJSONObject("stat");
            Board b = new Board(fs == null ? "" : str(fs, "name"), str(cat, "header"));
            for (int k = 0; k < top.length() && k < 3; k++) {
                JSONObject x = top.optJSONObject(k);
                if (x == null) continue;
                JSONObject s = x.optJSONObject("stat");
                String v = value(x.optDouble("value"), s == null ? "" : str(s, "format"), s == null ? 0 : s.optInt("fractions"));
                int pid = x.optInt("id");
                b.top.add(new Leader(str(x, "name"), v, pid > 0 ? FotMob.photoUrl(pid) : ""));
            }
            if (!b.top.isEmpty()) out.add(b);
        }
        return out;
    }

    /** Pure: one of FotMob's games (overview's nextMatch or lastMatch) as the club's (fm its FotMob id); null when none. */
    static Fixture fotmobFixture(JSONObject m, int fm) {
        if (m == null) return null;
        JSONObject h = m.optJSONObject("home"), a = m.optJSONObject("away");
        if (h == null || a == null) return null;
        boolean home = h.optInt("id") == fm;
        if (!home && a.optInt("id") != fm) return null;
        JSONObject us = home ? h : a, them = home ? a : h;
        Fixture f = new Fixture();
        f.home = home;
        f.other = str(them, "name", "");
        int other = them.optInt("id");
        f.otherLogo = other > 0 ? FOTMOB_CREST + other + ".png" : "";
        JSONObject tour = m.optJSONObject("tournament");
        f.comp = tour == null ? "" : str(tour, "name", "");
        JSONObject st = m.optJSONObject("status");
        f.when = st == null ? 0 : time(str(st, "utcTime"));
        f.played = st != null && st.optBoolean("finished") && !st.optBoolean("cancelled");
        if (f.played) {
            f.scored = us.optInt("score", -1);
            f.conceded = them.optInt("score", -1);
            if (f.scored < 0 || f.conceded < 0) f.played = false;
        }
        return f;
    }

    /** Pure: FotMob's form (overview's teamForm, oldest first) as letters, "WDL". */
    static String fotmobForm(JSONArray form) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; form != null && i < form.length(); i++) {
            JSONObject f = form.optJSONObject(i);
            String r = f == null ? "" : str(f, "resultString", "");
            if (r.equals("W") || r.equals("D") || r.equals("L")) b.append(r);
        }
        return b.toString();
    }

    /** Pure: the ground (overview's venue): "Quayside Park, Harbour City · 31,250". */
    static String venue(JSONObject v) {
        JSONObject w = v == null ? null : v.optJSONObject("widget");
        if (w == null || str(w, "name", "").isEmpty()) return "";
        String out = str(w, "name") + (str(w, "city", "").isEmpty() ? "" : ", " + str(w, "city"));
        JSONArray pairs = v.optJSONArray("statPairs");
        for (int i = 0; pairs != null && i < pairs.length(); i++) {
            JSONArray p = pairs.optJSONArray(i);
            if (p != null && "Capacity".equals(p.optString(0)) && p.optInt(1) > 0) {
                out += " · " + String.format(Locale.UK, "%,d", p.optInt(1));
            }
        }
        return out;
    }

    // ---- reckoning ------------------------------------------------------------------------------------------------

    /** Pure: the last n results' letters, oldest first (the results newest first). */
    static String form(List<Fixture> results, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = Math.min(n, results.size()) - 1; i >= 0; i--) {
            char r = results.get(i).result();
            if (r != ' ') b.append(r);
        }
        return b.toString();
    }

    /** Pure: how many of the games the club kept a clean sheet in. */
    static int cleanSheets(List<Fixture> results) {
        int n = 0;
        for (Fixture f : results) if (f.played && f.conceded == 0) n++;
        return n;
    }

    /** Pure: how many of the games the club did not score in. */
    static int blanks(List<Fixture> results) {
        int n = 0;
        for (Fixture f : results) if (f.played && f.scored == 0) n++;
        return n;
    }

    /** Pure: the biggest win (or, for wins false, the heaviest defeat); null when there was none. */
    static Fixture biggest(List<Fixture> results, boolean wins) {
        Fixture best = null;
        for (Fixture f : results) {
            if (f.result() != (wins ? 'W' : 'L')) continue;
            int by = Math.abs(f.scored - f.conceded), had = best == null ? -1 : Math.abs(best.scored - best.conceded);
            int goals = wins ? f.scored : f.conceded, hadGoals = best == null ? -1 : wins ? best.scored : best.conceded;
            if (by > had || by == had && goals > hadGoals) best = f;
        }
        return best;
    }

    /** The order of the season's figures, by FotMob's keys, in groups; one not listed goes under "More". */
    static final String[][] SECTIONS = {
            {"Attack", "goals_team_match", "expected_goals_team", "_xg_diff_team", "ontarget_scoring_att_team", "big_chance_team",
                    "big_chance_missed_team", "touches_in_opp_box_team", "corner_taken_team", "_set_piece_goals_team", "penalty_won_team"},
            {"Possession", "possession_percentage_team", "accurate_pass_team", "accurate_long_balls_team", "accurate_cross_team"},
            {"Defence", "goals_conceded_team_match", "expected_goals_conceded_team", "total_tackle_team", "interception_team",
                    "effective_clearance_team", "poss_won_att_3rd_team", "saves_team", "_set_piece_goals_conceded_team", "penalty_conceded_team"},
            {"Discipline", "fk_foul_lost_team", "total_yel_card_team", "total_red_card_team"},
            {"More", "rating_team", "phys_tdc_team", "home_attendance_team"}};

    /** Pure: the figures in their groups, in SECTIONS' order; any FotMob adds later goes under "More". */
    static Map<String, List<Figure>> sections(List<Figure> figures) {
        Map<String, List<Figure>> out = new LinkedHashMap<>();
        List<Figure> left = new ArrayList<>(figures);
        for (String[] s : SECTIONS) {
            List<Figure> in = new ArrayList<>();
            for (int i = 1; i < s.length; i++) {
                for (Figure f : figures) if (s[i].equals(f.key) && left.remove(f)) in.add(f);
            }
            if (s[0].equals("More")) in.addAll(left);
            if (!in.isEmpty()) out.put(s[0], in);
        }
        return out;
    }

    /** The club's lists shown first, by FotMob's keys; the rest follow when asked for. */
    static final String[] FIRST_BOARDS = {"goals", "goal_assist", "_goals_and_goal_assist", "rating", "expected_goals",
            "expected_assists", "total_att_assist", "big_chance_created", "won_contest", "total_tackle", "_save_percentage",
            "_goals_prevented", "yellow_card", "red_card", "mins_played", "phys_ts"};

    /** Pure: the lists to show: those of FIRST_BOARDS in its order; with all, the others after them in FotMob's order. */
    static List<Board> shown(List<Board> boards, boolean all) {
        List<Board> out = new ArrayList<>();
        for (String k : FIRST_BOARDS) for (Board b : boards) if (k.equals(b.key) && !out.contains(b)) out.add(b);
        if (all) for (Board b : boards) if (!out.contains(b)) out.add(b);
        return out;
    }

    /** Pure: one of FotMob's values as it shows it: 6.91, 54.9%, 112.5 km, 35.4 km/h, 74,146. */
    static String value(double v, String format, int fractions) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        switch (format == null ? "" : format) {
            case "percent": return fixed(v, Math.max(0, fractions)) + "%";
            case "meter": return fixed(v / 1000.0, 1) + " km";
            case "kph": return fixed(v, 1) + " km/h";
            case "fraction": return fixed(v, fractions);
            default: return fixed(v, v == Math.rint(v) ? 0 : Math.max(1, fractions));
        }
    }

    private static String fixed(double v, int places) {
        if (places <= 0) return String.format(Locale.UK, "%,d", Math.round(v));
        return String.format(Locale.UK, "%." + places + "f", v);
    }

    /** Pure: 1st, 2nd, 3rd, 4th … 11th, 12th, 13th, 21st. */
    static String ordinal(int n) {
        int t = n % 100;
        String suf = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suf;
    }

    /** Pure: an injury as the squad says it: "Injured · back late November", "Injured · back in a few days". */
    static String injuryLine(String back) {
        String b = back == null ? "" : back.trim();
        if (b.isEmpty() || b.equalsIgnoreCase("injured")) return "Injured";
        String lower = Character.toLowerCase(b.charAt(0)) + b.substring(1);
        if (lower.equals("unknown")) return "Injured · return not known";
        boolean span = lower.matches("(?:a |an |\\d+ |few |a few |a couple of )?.*\\b(?:days?|weeks?|months?)");
        return "Injured · back " + (span ? "in " : "") + lower;
    }

    /** Pure: a name's initials for its disc when there is no picture: "Ashby Varley" → "AV", "Kettering" → "K". */
    static String initials(String name) {
        if (name == null) return "";
        String[] w = name.trim().split("\\s+");
        if (w.length == 0 || w[0].isEmpty()) return "";
        String first = w[0].substring(0, 1);
        return (w.length == 1 ? first : first + w[w.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    /** Pure: a club's colour from ESPN's "1a7f4b" (or "#1a7f4b"); fallback when it is not one. */
    static int colour(String hex, int fallback) {
        if (hex == null) return fallback;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return fallback;
        try {
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A field as text; the fallback when it is missing, null or empty (Android's optString gives "null" for a null). */
    private static String str(JSONObject o, String key, String fallback) {
        if (o == null || o.isNull(key)) return fallback;
        String s = o.optString(key, fallback);
        return s.isEmpty() ? fallback : s;
    }

    private static String str(JSONObject o, String key) {
        return str(o, key, "");
    }

    private static String firstLogo(JSONObject team) {
        JSONArray logos = team == null ? null : team.optJSONArray("logos");
        JSONObject l = logos == null ? null : logos.optJSONObject(0);
        if (l != null) return str(l, "href", "");
        return team == null ? "" : str(team, "logo", "");
    }

    private static long time(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return OffsetDateTime.parse(iso).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
