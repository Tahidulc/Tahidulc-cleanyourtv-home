package com.gathin.tvhomephone.sports;

import com.gathin.broadcast.SportBoard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A sports event that isn't a football game, for its own page (EventActivity): an NFL or NBA game, a tennis match, a
 * UFC card, a Grand Prix weekend, a golf tournament or a listing from the British guide. What it is, when, how it
 * stands, the British channels the guide lists it on, the names it goes by in a channel's name or a guide's title
 * (EventChannels), and where ESPN keeps its videos (Highlights). Made from what the Sports page already has.
 */
public final class SportEvent {
    /** The sport, as OnTv names it (OnTv.F1, OnTv.TENNIS...). */
    public String sport = "";
    public String title = "", subtitle = "", logo = "";
    /** Two sides when it has them (a game, a match, the main event): names, logos and scores. */
    public String home = "", away = "", homeLogo = "", awayLogo = "", homeScore = "", awayScore = "";
    /** pre, in or post, as ESPN has them; its short detail ("Q3 4:12", "Round 2", "Final"). */
    public String state = "pre", detail = "";
    public long start, end;
    /** ESPN's league ("football/nfl") and the event's id there, for its videos; "" when not known. */
    public String espn = "", espnId = "";
    /** The British channels the guide lists it on (Sky's names). */
    public final List<String> channels = new ArrayList<>();
    /**
     * What finds it in a channel's name or a programme's title: any one of these sets, each of a set's groups by one of
     * its names ({{"Kansas City Chiefs", "Chiefs"}, {"Buffalo Bills", "Bills"}}).
     */
    public final List<String[][]> finds = new ArrayList<>();
    /** Lines under the head, {left, right}: a weekend's sessions, a leaderboard's leaders, a card's other bouts. */
    public final List<String[]> lines = new ArrayList<>();
    public String linesHead = "";

    public boolean live() {
        return "in".equals(state);
    }

    public boolean over() {
        return "post".equals(state);
    }

    /** When it is on: from its start until its end (three hours when the end is not known). */
    public long endOr() {
        return end > start ? end : start + 3 * 3_600_000L;
    }

    // ---- made from the Sports page's pieces (pure) --------------------------------------------------------------

    /** An NFL or NBA game. */
    static SportEvent game(MoreSports.Match m, String sport) {
        SportEvent e = new SportEvent();
        e.sport = sport;
        e.title = m.away + " at " + m.home;
        e.subtitle = m.league;
        e.logo = m.leagueLogo;
        e.home = m.home;
        e.away = m.away;
        e.homeLogo = m.homeLogo;
        e.awayLogo = m.awayLogo;
        e.homeScore = m.homeScore;
        e.awayScore = m.awayScore;
        e.state = m.state;
        e.detail = m.detail;
        e.start = m.start;
        e.end = m.start + (OnTv.NBA.equals(sport) ? 3 : 4) * 3_600_000L;
        e.espn = OnTv.NBA.equals(sport) ? "basketball/nba" : "football/nfl";
        e.espnId = m.id;
        if (m.channel != null) e.channels.add(m.channel);
        e.finds.add(new String[][]{{m.home, m.homeShort}, {m.away, m.awayShort}});
        if (!m.homeRecord.isEmpty() || !m.awayRecord.isEmpty()) {
            e.lines.add(new String[]{m.away, m.awayRecord});
            e.lines.add(new String[]{m.home, m.homeRecord});
            e.linesHead = "Records";
        }
        return e;
    }

    /** A tennis match on a tour (ATP or WTA). */
    static SportEvent tennis(SportBoard s, String tour, String channel) {
        SportEvent e = new SportEvent();
        e.sport = OnTv.TENNIS;
        e.title = s.a.name + " v " + s.b.name;
        e.subtitle = s.event + (s.stage == null || s.stage.isEmpty() ? "" : " · " + s.stage);
        e.home = s.a.name;
        e.away = s.b.name;
        e.homeLogo = s.a.flagUrl;
        e.awayLogo = s.b.flagUrl;
        e.state = s.state;
        e.detail = s.over() ? (s.method == null || s.method.isEmpty() ? "Final" : s.method) : s.live() ? "Live" : "";
        e.start = s.start;
        e.end = s.start + 3 * 3_600_000L;
        e.espn = "tennis/" + (tour == null ? "atp" : tour.toLowerCase(Locale.ROOT));
        if (channel != null) e.channels.add(channel);
        e.finds.add(new String[][]{{s.a.name, surname(s.a.shortName, s.a.name)}, {s.b.name, surname(s.b.shortName, s.b.name)}});
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i = 0; i < Math.max(s.a.games.length, s.b.games.length); i++) {
            if (a.length() > 0) {
                a.append("  ");
                b.append("  ");
            }
            a.append(i < s.a.games.length ? s.a.games[i] : 0);
            b.append(i < s.b.games.length ? s.b.games[i] : 0);
        }
        if (a.length() > 0) {
            e.homeScore = a.toString();
            e.awayScore = b.toString();
        }
        return e;
    }

    /** UFC's card: its main event face to face, the other bouts under it. */
    static SportEvent fightNight(List<SportBoard> card, String channel) {
        SportBoard main = card.get(0);
        SportEvent e = new SportEvent();
        e.sport = OnTv.UFC;
        e.title = main.event;
        e.subtitle = main.a.name + " v " + main.b.name + (main.stage == null || main.stage.isEmpty() ? "" : " · " + main.stage);
        e.logo = SportCards.SportFeeds_UFC_LOGO;
        e.home = main.a.name;
        e.away = main.b.name;
        e.homeLogo = main.a.photoUrl;
        e.awayLogo = main.b.photoUrl;
        e.state = main.state;
        e.detail = main.over() ? (main.method == null || main.method.isEmpty() ? "Result" : main.method) : "";
        long first = main.start;
        for (SportBoard b : card) if (b.start > 0 && (first <= 0 || b.start < first)) first = b.start;
        e.start = first;
        e.end = Math.max(main.start, first) + 2 * 3_600_000L;
        e.espn = "mma/ufc";
        if (channel != null) e.channels.add(channel);
        e.finds.add(new String[][]{{main.a.name, surname(main.a.shortName, main.a.name)}, {main.b.name, surname(main.b.shortName, main.b.name)}});
        Matcher n = Pattern.compile("(?i)\\bufc\\s*(\\d{2,4})\\b").matcher(main.event == null ? "" : main.event);
        if (n.find()) e.finds.add(new String[][]{{"UFC " + n.group(1)}});
        for (int i = 1; i < card.size(); i++) {
            SportBoard b = card.get(i);
            e.lines.add(new String[]{b.a.name + " v " + b.b.name, b.over() ? (b.method == null ? "" : b.method) : b.stage});
        }
        if (!e.lines.isEmpty()) e.linesHead = "The card";
        return e;
    }

    /** A Grand Prix weekend: its sessions, its next one as when it is on. */
    static SportEvent grandPrix(F1.Weekend w, String channel, long now) {
        SportEvent e = new SportEvent();
        e.sport = OnTv.F1;
        e.title = w.name == null ? "Formula 1" : w.name;
        e.subtitle = (w.round == null || w.round.isEmpty() ? "" : "Round " + w.round + " · ") + (w.circuit == null ? "" : w.circuit);
        e.logo = Competitions.F1_LOGO;
        long next = 0, nextEnd = 0;
        for (Object[] s : w.sessions) {
            long at = (Long) s[1];
            if (at <= 0) continue;
            String name = (String) s[0];
            e.lines.add(new String[]{name, SportCards.dayAndTime(at)});
            long len = name != null && name.toLowerCase(Locale.ROOT).contains("race") ? 2 * 3_600_000L : 3_600_000L + 15 * 60_000L;
            if (next == 0 && at + len > now) {
                next = at;
                nextEnd = at + len;
            }
        }
        if (!e.lines.isEmpty()) e.linesHead = "The weekend";
        e.start = next > 0 ? next : w.race;
        e.end = next > 0 ? nextEnd : w.race + 2 * 3_600_000L;
        e.state = next > 0 && next <= now ? "in" : next == 0 && w.race > 0 && w.race + 2 * 3_600_000L < now ? "post" : "pre";
        e.espn = "racing/f1";
        if (channel != null) e.channels.add(channel);
        String place = gpPlace(e.title);
        if (!place.isEmpty()) e.finds.add(new String[][]{{place}, {"F1", "Formula 1", "Formula One", "Grand Prix"}});
        return e;
    }

    /** A golf tournament and its leaders. */
    static SportEvent golf(MoreSports.Leaderboard b) {
        SportEvent e = new SportEvent();
        e.sport = OnTv.GOLF;
        e.title = b.event;
        e.subtitle = b.tour;
        e.logo = b.logo;
        e.state = b.state;
        e.detail = b.detail;
        long now = System.currentTimeMillis();
        e.start = now;
        e.end = now + 4 * 3_600_000L;
        e.espn = golfLeague(b.logo, b.tour);
        if (b.channel != null) e.channels.add(b.channel);
        String core = tournament(b.event);
        if (!core.isEmpty()) e.finds.add(new String[][]{{core}});
        for (String[] r : b.rows) e.lines.add(new String[]{r[0] + "  " + r[1], r[3]});
        if (!e.lines.isEmpty()) e.linesHead = "Leaderboard";
        return e;
    }

    /** A listing from the British guide (darts, snooker, rugby, cricket and the rest). */
    static SportEvent listing(OnTv.Listing l) {
        SportEvent e = new SportEvent();
        e.sport = l.sport;
        e.title = plainTitle(l.title);
        e.subtitle = "";
        e.logo = OnTv.logo(l.sport, l.title);
        e.start = l.start;
        e.end = l.end;
        long now = System.currentTimeMillis();
        e.state = l.on(now) ? "in" : l.end <= now ? "post" : "pre";
        e.channels.addAll(l.channels);
        e.espn = espnFor(l.sport);
        String[] sides = sides(e.title);
        if (sides != null) e.finds.add(new String[][]{{sides[0]}, {sides[1]}});
        else if (!e.title.isEmpty()) e.finds.add(new String[][]{{e.title}});
        return e;
    }

    // ---- words (pure) -----------------------------------------------------------------------------------------------

    /** A player's or fighter's surname: the short name ESPN gives, else the name's last word. */
    static String surname(String shortName, String name) {
        String s = shortName == null ? "" : shortName.trim();
        if (s.contains(".")) s = s.substring(s.lastIndexOf('.') + 1).trim();
        if (s.length() >= 3) return s;
        String n = name == null ? "" : name.trim();
        int sp = n.lastIndexOf(' ');
        return sp > 0 ? n.substring(sp + 1) : n;
    }

    /** A Grand Prix's place: "Las Vegas" for "Las Vegas Grand Prix" (or "the Las Vegas GP"). */
    static String gpPlace(String name) {
        String n = name == null ? "" : name.trim();
        n = n.replaceAll("(?i)\\b(formula\\s*(1|one)|f1)\\b", " ").replaceAll("(?i)\\bgrand\\s+prix\\b|\\bgp\\b", " ");
        n = n.replaceAll("(?i)^\\s*the\\s+", "").replaceAll("[:\\-–]", " ").replaceAll("\\s+", " ").trim();
        return n;
    }

    /**
     * A tournament's own name, its sponsors' words and a leading "The" gone: "Austin Championship" for "... pres. by
     * Compliance Solutions", "Pinewood Classic" for "The Pinewood Classic".
     */
    static String tournament(String name) {
        String n = name == null ? "" : name.trim();
        n = n.replaceAll("(?i)\\s+(pres\\.?|presented)\\s+by\\s+.*$", "").replaceAll("(?i)\\s+(hosted|sponsored)\\s+by\\s+.*$", "");
        n = n.replaceAll("(?i)^the\\s+", "");
        return n.replaceAll("\\s+", " ").trim();
    }

    /** A guide's title without its "Live" ("Live Premiership Rugby: Bath v Saracens" is "Premiership Rugby: Bath v Saracens"). */
    static String plainTitle(String title) {
        String t = title == null ? "" : title.trim();
        t = t.replaceAll("(?i)^live\\s*[:\\-–]?\\s*", "");
        return t.trim();
    }

    private static final Pattern VERSUS = Pattern.compile("(?i)^(?:.*?:\\s*)?(.+?)\\s+(?:v|vs\\.?)\\s+(.+?)\\s*$");

    /** The two sides a title names ("Bath" and "Saracens" for "Premiership Rugby: Bath v Saracens"); null without. */
    static String[] sides(String title) {
        Matcher m = VERSUS.matcher(title == null ? "" : title.trim());
        if (!m.matches()) return null;
        String a = m.group(1).trim(), b = m.group(2).replaceAll("\\s*[-–(].*$", "").trim();
        if (a.length() < 3 || b.length() < 3) return null;
        return new String[]{a, b};
    }

    /** Pure: ESPN's league for a golf leaderboard: the DP World Tour's (by its logo or its name), else the PGA Tour's. */
    static String golfLeague(String logo, String tour) {
        String t = tour == null ? "" : tour.toLowerCase(Locale.ROOT);
        boolean europe = MoreSports.DP_WORLD_LOGO.equals(logo) || t.contains("dp world") || t.contains("european");
        return europe ? "golf/eur" : "golf/pga";
    }

    /** ESPN's league for a sport's videos ("" for none read). */
    static String espnFor(String sport) {
        switch (sport == null ? "" : sport) {
            case OnTv.F1: return "racing/f1";
            case OnTv.NFL: return "football/nfl";
            case OnTv.NBA: return "basketball/nba";
            case OnTv.UFC: return "mma/ufc";
            case OnTv.TENNIS: return "tennis/atp";
            case OnTv.GOLF: return "golf/pga";
            default: return "";
        }
    }

    // ---- kept in an Intent --------------------------------------------------------------------------------------

    public JSONObject json() {
        JSONObject o = new JSONObject();
        try {
            o.put("sport", sport).put("title", title).put("subtitle", subtitle).put("logo", logo).put("home", home)
                    .put("away", away).put("homeLogo", homeLogo).put("awayLogo", awayLogo).put("homeScore", homeScore)
                    .put("awayScore", awayScore).put("state", state).put("detail", detail).put("start", start).put("end", end)
                    .put("espn", espn).put("espnId", espnId).put("channels", new JSONArray(channels)).put("linesHead", linesHead);
            JSONArray f = new JSONArray();
            for (String[][] set : finds) {
                JSONArray s = new JSONArray();
                for (String[] group : set) {
                    JSONArray g = new JSONArray();
                    for (String n : group) g.put(n == null ? "" : n);
                    s.put(g);
                }
                f.put(s);
            }
            o.put("finds", f);
            JSONArray ls = new JSONArray();
            for (String[] l : lines) ls.put(new JSONArray().put(l[0]).put(l.length > 1 ? l[1] : ""));
            o.put("lines", ls);
        } catch (JSONException ignored) {
            // plain fields
        }
        return o;
    }

    public static SportEvent from(JSONObject o) {
        SportEvent e = new SportEvent();
        e.sport = o.optString("sport");
        e.title = o.optString("title");
        e.subtitle = o.optString("subtitle");
        e.logo = o.optString("logo");
        e.home = o.optString("home");
        e.away = o.optString("away");
        e.homeLogo = o.optString("homeLogo");
        e.awayLogo = o.optString("awayLogo");
        e.homeScore = o.optString("homeScore");
        e.awayScore = o.optString("awayScore");
        e.state = o.optString("state", "pre");
        e.detail = o.optString("detail");
        e.start = o.optLong("start");
        e.end = o.optLong("end");
        e.espn = o.optString("espn");
        e.espnId = o.optString("espnId");
        e.linesHead = o.optString("linesHead");
        JSONArray ch = o.optJSONArray("channels");
        for (int i = 0; ch != null && i < ch.length(); i++) if (!ch.optString(i).isEmpty()) e.channels.add(ch.optString(i));
        JSONArray f = o.optJSONArray("finds");
        for (int i = 0; f != null && i < f.length(); i++) {
            JSONArray s = f.optJSONArray(i);
            if (s == null) continue;
            String[][] set = new String[s.length()][];
            for (int k = 0; k < s.length(); k++) {
                JSONArray g = s.optJSONArray(k);
                set[k] = new String[g == null ? 0 : g.length()];
                for (int j = 0; g != null && j < g.length(); j++) set[k][j] = g.optString(j);
            }
            e.finds.add(set);
        }
        JSONArray ls = o.optJSONArray("lines");
        for (int i = 0; ls != null && i < ls.length(); i++) {
            JSONArray l = ls.optJSONArray(i);
            if (l != null) e.lines.add(new String[]{l.optString(0), l.optString(1)});
        }
        return e;
    }
}
