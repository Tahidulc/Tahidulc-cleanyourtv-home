package com.gathin.tvhomephone.sports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What changed in a game between two polls, as the alerts it makes (the TV's GoalWatchService, MoreAlerts and
 * RichAlerts rules): kick-off, goals, red cards, half time, full time, a late equaliser or winner, a side back from
 * two down, a brace or a hat-trick, and the summary's VAR checks and penalties missed. Pure, for the tests: each alert
 * has a key, so one already sent is never sent again.
 */
public final class Events {
    private Events() {}

    public static final class Alert {
        public final String kind, key, title, text;
        public final Game game;
        /** A reminder's competition and channel (its game is not read again): for the logos; null when none. */
        public String slug, channel;
        /** A news story's own picture (its address; null when none): shown with its alert and pop-up. */
        public String picture;
        /** The line-ups alert's summary, for its pitch (not kept); null for the others. */
        public transient Espn.Summary summary;

        public Alert(String kind, String key, String title, String text, Game game) {
            this.kind = kind;
            this.key = key;
            this.title = title;
            this.text = text;
            this.game = game;
        }

        @Override public String toString() { return kind + ":" + title; }
    }

    /** The alerts between prev (null: first seen, nothing to tell yet) and now. */
    public static List<Alert> diff(Game prev, Game now) {
        List<Alert> out = new ArrayList<>();
        if (prev == null || now == null) return out;
        String fixture = now.homeName + " v " + now.awayName;
        String scoreLine = now.homeName + " " + now.score() + " " + now.awayName;
        if (prev.state == Game.PRE && now.state == Game.LIVE) {
            out.add(new Alert("kickoff", "K|" + now.id, "KICK-OFF · " + fixture,
                    now.leagueName + (now.venue != null ? " · " + now.venue : ""), now));
        }
        Set<String> had = new HashSet<>();
        for (String[] g : prev.goals) had.add(g[0]);
        for (int i = 0; i < now.goals.size(); i++) {
            String[] g = now.goals.get(i);
            if (had.contains(g[0])) continue;
            String club = "home".equals(g[3]) ? now.homeName : now.awayName;
            String kind = "pen".equals(g[4]) ? " (pen)" : "og".equals(g[4]) ? " (own goal)" : "";
            out.add(new Alert("goal", g[0], "GOAL · " + scoreLine, g[1] + " " + g[2] + kind + " for " + club, now));
            Alert d = drama(now, i);
            if (d != null) out.add(d);
            Alert m = milestone(now, i);
            if (m != null) out.add(m);
        }
        Alert back = comeback(prev, now);
        if (back != null) out.add(back);
        Set<String> reds = new HashSet<>();
        for (String[] r : prev.reds) reds.add(r[0]);
        for (String[] r : now.reds) {
            if (reds.contains(r[0])) continue;
            String club = "home".equals(r[3]) ? now.homeName : now.awayName;
            out.add(new Alert("red", r[0], "RED CARD · " + club, r[1] + " " + r[2] + " · " + scoreLine, now));
        }
        if (!prev.halftime && now.halftime) {
            out.add(new Alert("ht", "H|" + now.id, "HALF TIME · " + scoreLine, scorers(now), now));
        }
        if (prev.state == Game.LIVE && now.state == Game.POST) {
            out.add(new Alert("ft", "F|" + now.id, "FULL TIME · " + scoreLine, scorers(now), now));
        }
        return out;
    }

    /** The score just before goal i: {home, away}, counting the goals in the order ESPN lists them. */
    static int[] before(Game g, int i) {
        int h = 0, a = 0;
        for (int k = 0; k < i && k < g.goals.size(); k++) {
            if ("home".equals(g.goals.get(k)[3])) h++; else a++;
        }
        return new int[]{h, a};
    }

    /** From the 85th minute: an equaliser, or a goal that puts a level game ahead. */
    static Alert drama(Game g, int i) {
        String[] goal = g.goals.get(i);
        int minute = Game.minute(goal[2]);
        if (minute < 85) return null;
        int[] b = before(g, i);
        boolean home = "home".equals(goal[3]);
        int mine = home ? b[0] : b[1], theirs = home ? b[1] : b[0];
        String what;
        if (mine + 1 == theirs) what = "equaliser";
        else if (mine == theirs) what = "winner";
        else return null;
        String club = home ? g.homeName : g.awayName;
        int hs = b[0] + (home ? 1 : 0), as = b[1] + (home ? 0 : 1);
        return new Alert("drama", "D|" + goal[0], "LATE DRAMA · " + goal[2] + " " + what,
                goal[1] + " for " + club + " · " + g.homeName + " " + hs + "–" + as + " " + g.awayName, g);
    }

    /** A side two or more goals down now level or ahead: once a game for each side. */
    static Alert comeback(Game prev, Game now) {
        for (String side : new String[]{"home", "away"}) {
            int worst = 0, h = 0, a = 0;
            for (String[] goal : now.goals) {
                if ("home".equals(goal[3])) h++; else a++;
                int behind = "home".equals(side) ? a - h : h - a;
                worst = Math.max(worst, behind);
            }
            int nowDiff = "home".equals(side) ? now.homeScore - now.awayScore : now.awayScore - now.homeScore;
            int prevDiff = "home".equals(side) ? prev.homeScore - prev.awayScore : prev.awayScore - prev.homeScore;
            if (worst >= 2 && nowDiff >= 0 && prevDiff < 0) {
                String club = "home".equals(side) ? now.homeName : now.awayName;
                return new Alert("drama", "C|" + now.id + "|" + side, "COMEBACK · " + club + " from " + worst + " down",
                        now.homeName + " " + now.score() + " " + now.awayName, now);
            }
        }
        return null;
    }

    /** A player's second or third goal in the game (own goals left out). */
    static Alert milestone(Game g, int i) {
        String[] goal = g.goals.get(i);
        if ("og".equals(goal[4]) || goal[1].isEmpty()) return null;
        int n = 0;
        for (int k = 0; k <= i; k++) {
            String[] x = g.goals.get(k);
            if (x[1].equals(goal[1]) && !"og".equals(x[4])) n++;
        }
        if (n < 2 || n > 3) return null;
        String what = n == 2 ? "BRACE" : "HAT-TRICK";
        return new Alert("milestone", "M|" + g.id + "|" + goal[1] + "|" + n, what + " · " + goal[1],
                (n == 2 ? "Two goals" : "Three goals") + " for " + ("home".equals(goal[3]) ? g.homeName : g.awayName)
                        + " · " + g.homeName + " " + g.score() + " " + g.awayName, g);
    }

    /** The goals so far, as one line per club: "Man Utd: Fernandes 23', Højlund 67'". */
    public static String scorers(Game g) {
        Map<String, List<String>> by = new HashMap<>();
        for (String[] goal : g.goals) {
            String club = "home".equals(goal[3]) ? g.homeName : g.awayName;
            String name = goal[1].contains(" ") ? goal[1].substring(goal[1].lastIndexOf(' ') + 1) : goal[1];
            by.computeIfAbsent(club, k -> new ArrayList<>()).add(name + " " + goal[2] + ("pen".equals(goal[4]) ? " pen" : "og".equals(goal[4]) ? " og" : ""));
        }
        StringBuilder s = new StringBuilder();
        for (String club : new String[]{g.homeName, g.awayName}) {
            List<String> l = by.get(club);
            if (l == null) continue;
            if (s.length() > 0) s.append('\n');
            s.append(club).append(": ").append(String.join(", ", l));
        }
        return s.length() == 0 ? "No goals yet" : s.toString();
    }

    /** VAR checks, decisions and penalties missed or saved, from the game's summary events. */
    public static List<Alert> incidents(Game g, Espn.Summary s, Set<String> sent) {
        List<Alert> out = new ArrayList<>();
        for (String[] e : s.events) {
            String what = e[1].toLowerCase(Locale.ROOT);
            boolean var = what.contains("var");
            boolean pen = what.contains("penalty") && (what.contains("miss") || what.contains("saved"));
            if (!var && !pen || sent.contains(e[4])) continue;
            String club = "home".equals(e[3]) ? g.homeName : "away".equals(e[3]) ? g.awayName : "";
            out.add(new Alert("var", e[4], (var ? "VAR · " : "PENALTY MISSED · ") + g.homeName + " " + g.score() + " " + g.awayName,
                    e[1] + (e[2].isEmpty() ? "" : " · " + e[2]) + (club.isEmpty() ? "" : " (" + club + ")") + " " + e[0], g));
        }
        return out;
    }

    /** Both starting elevens before kick-off, when the summary has them; null until it does. */
    public static Alert lineups(Game g, Espn.Summary s) {
        String h = starters(s.homeLineup), a = starters(s.awayLineup);
        if (h.isEmpty() || a.isEmpty()) return null;
        String when = java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Alert out = new Alert("kickoff", "L|" + g.id, "LINE-UPS · " + g.homeName + " v " + g.awayName + " · " + when,
                g.homeName + (s.homeFormation.isEmpty() ? "" : " (" + s.homeFormation + ")") + ": " + h + "\n"
                        + g.awayName + (s.awayFormation.isEmpty() ? "" : " (" + s.awayFormation + ")") + ": " + a, g);
        out.summary = s;
        return out;
    }

    private static String starters(List<String[]> side) {
        List<String> names = new ArrayList<>();
        for (String[] p : side) {
            if (!"1".equals(p[3])) continue;
            names.add(p[1].contains(" ") ? p[1].substring(p[1].lastIndexOf(' ') + 1) : p[1]);
        }
        return String.join(", ", names);
    }
}
