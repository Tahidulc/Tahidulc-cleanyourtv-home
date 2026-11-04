package com.gathin.tvhomephone.sports;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Short facts about a game for the player's info tile, from ESPN's summary: before kick-off the table, each side's
 * run, earlier meetings and the competition's top scorers; once it is on, the numbers of the game too (the ball, the
 * shots, the saves, the passing, the corners, the cards) and who stands out. Each is {tag, words}: tags are STAT,
 * FORM, TABLE, HEAD TO HEAD, PLAYER and FACT. Stats come first while it is live.
 */
public final class Facts {
    private Facts() {}

    public static List<String[]> of(Game g, Espn.Summary s) {
        List<String[]> live = new ArrayList<>(), before = new ArrayList<>();
        if (g == null || s == null) return live;
        String home = g.homeName, away = g.awayName;
        boolean on = g.state != Game.PRE;
        if (on) stats(g, s, live);
        // The table as it stood.
        String[] ht = row(s.table, s.homeId), at = row(s.table, s.awayId);
        String comp = g.leagueName == null || g.leagueName.isEmpty() ? "the table" : "the " + g.leagueName;
        if (ht != null && at != null) {
            before.add(new String[]{"TABLE", home + " " + ordinal(ht[2]) + " (" + points(ht[3]) + "), " + away + " " + ordinal(at[2])
                    + " (" + points(at[3]) + ") in " + comp + (on ? ", before this game" : "")});
        } else if (ht != null || at != null) {
            String[] r = ht != null ? ht : at;
            before.add(new String[]{"TABLE", (ht != null ? home : away) + " are " + ordinal(r[2]) + " in " + comp + " on " + points(r[3])});
        }
        // Each side's run.
        String hr = run(home, s.homeLast), ar = run(away, s.awayLast);
        if (!hr.isEmpty()) before.add(new String[]{"FORM", hr});
        if (!ar.isEmpty()) before.add(new String[]{"FORM", ar});
        String hl = lastGame(home, s.homeLast), al = lastGame(away, s.awayLast);
        if (!on && !hl.isEmpty()) before.add(new String[]{"FORM", hl});
        if (!on && !al.isEmpty()) before.add(new String[]{"FORM", al});
        // Earlier meetings.
        if (!s.meetings.isEmpty()) {
            String[] m = s.meetings.get(0);
            before.add(new String[]{"HEAD TO HEAD", "Last met in " + m[0] + ": " + m[1] + " " + m[2] + "–" + m[4] + " " + m[3]
                    + (s.meetings.size() > 1 ? " (" + record(g, s) + ")" : "")});
        } else if (!on) {
            before.add(new String[]{"HEAD TO HEAD", "The first time these two have met in ESPN's records"});
        }
        // The competition's leaders (before kick-off ESPN gives them for the season so far).
        for (String[] l : s.leaders) {
            String club = "home".equals(l[0]) ? home : away;
            if ("goalsLeaders".equals(l[1]) && l[3].contains("Goals:")) {
                int goals = after(l[3], "Goals:"), games = after(l[3], "Matches:");
                if (goals > 0) before.add(new String[]{"PLAYER", l[2] + " has " + goals + (goals == 1 ? " goal" : " goals")
                        + (games > 0 ? " in " + games + (games == 1 ? " game" : " games") : "") + " for " + club + " in " + comp});
            } else if ("assistsLeaders".equals(l[1]) && l[3].contains("Assists:")) {
                int n = after(l[3], "Assists:");
                if (n > 1) before.add(new String[]{"PLAYER", l[2] + " leads " + club + " with " + n + " assists in " + comp});
            }
        }
        if (s.attendance > 0) before.add(new String[]{"FACT", String.format(Locale.UK, "Crowd: %,d", s.attendance)
                + (g.venue == null || g.venue.isEmpty() ? "" : " at " + g.venue)});
        if (!s.referee.isEmpty()) before.add(new String[]{"FACT", "Referee: " + s.referee});
        List<String[]> out = new ArrayList<>(live);
        out.addAll(before);
        return out;
    }

    /** The game's own numbers as facts. */
    private static void stats(Game g, Espn.Summary s, List<String[]> out) {
        Map<String, String> h = s.homeNumbers, a = s.awayNumbers;
        String home = g.homeName, away = g.awayName;
        double hp = num(h.get("possessionPct")), ap = num(a.get("possessionPct"));
        if (hp + ap > 0) {
            boolean homeMore = hp >= ap;
            out.add(new String[]{"STAT", (homeMore ? home : away) + " have had " + Math.round(homeMore ? hp : ap) + "% of the ball"});
        }
        int hs = (int) num(h.get("totalShots")), as = (int) num(a.get("totalShots"));
        int hot = (int) num(h.get("shotsOnTarget")), aot = (int) num(a.get("shotsOnTarget"));
        if (hs + as > 0) out.add(new String[]{"STAT", "Shots " + hs + "–" + as + " · on target " + hot + "–" + aot});
        int hsv = (int) num(h.get("saves")), asv = (int) num(a.get("saves"));
        String hKeeper = leader(s, "home", "saves"), aKeeper = leader(s, "away", "saves");
        if (Math.max(hsv, asv) >= 2) {
            boolean homeKeeper = hsv >= asv;
            String keeper = homeKeeper ? hKeeper : aKeeper;
            int n = homeKeeper ? hsv : asv;
            out.add(new String[]{"STAT", (keeper.isEmpty() ? (homeKeeper ? home : away) + "'s keeper" : keeper) + " has made " + n + " saves"});
        }
        String shooter = topShooter(s);
        if (!shooter.isEmpty()) out.add(new String[]{"PLAYER", shooter});
        double hpass = num(h.get("passPct")), apass = num(a.get("passPct"));
        int hpasses = (int) num(h.get("accuratePasses")), apasses = (int) num(a.get("accuratePasses"));
        if (hpasses + apasses > 50) {
            boolean homeMore = hpasses >= apasses;
            double pct = homeMore ? hpass : apass;
            out.add(new String[]{"STAT", (homeMore ? home : away) + " have completed " + (homeMore ? hpasses : apasses) + " passes"
                    + (pct > 0 ? " (" + Math.round(pct <= 1 ? pct * 100 : pct) + "%)" : "")});
        }
        int hc = (int) num(h.get("wonCorners")), ac = (int) num(a.get("wonCorners"));
        if (hc + ac > 0) out.add(new String[]{"STAT", "Corners " + hc + "–" + ac});
        int hy = (int) num(h.get("yellowCards")), ay = (int) num(a.get("yellowCards"));
        int hr = (int) num(h.get("redCards")), ar = (int) num(a.get("redCards"));
        if (hy + ay + hr + ar > 0) {
            out.add(new String[]{"STAT", "Cards: " + home + " " + cards(hy, hr) + ", " + away + " " + cards(ay, ar)});
        }
        int hoff = (int) num(h.get("offsides")), aoff = (int) num(a.get("offsides"));
        if (hoff + aoff >= 3) out.add(new String[]{"STAT", "Offsides " + hoff + "–" + aoff});
        // A side behind by two that is level or ahead now.
        int behindHome = 0, behindAway = 0, hg = 0, ag = 0;
        for (String[] goal : g.goals) {
            if ("home".equals(goal[3])) hg++; else ag++;
            behindHome = Math.max(behindHome, ag - hg);
            behindAway = Math.max(behindAway, hg - ag);
        }
        if (behindHome >= 2 && g.homeScore >= g.awayScore) out.add(new String[]{"FACT", home + " were " + behindHome + " goals down"});
        if (behindAway >= 2 && g.awayScore >= g.homeScore) out.add(new String[]{"FACT", away + " were " + behindAway + " goals down"});
    }

    private static String topShooter(Espn.Summary s) {
        String best = "";
        int most = 0;
        for (String[] l : s.leaders) {
            if (!"totalShots".equals(l[1]) || l[3].contains(":")) continue;
            int n = (int) num(l[3]);
            if (n > most) {
                most = n;
                best = l[2];
            }
        }
        return most >= 3 ? best + " has had " + most + " shots, the most on the pitch" : "";
    }

    private static String leader(Espn.Summary s, String side, String what) {
        for (String[] l : s.leaders) if (side.equals(l[0]) && what.equals(l[1]) && !l[3].contains(":")) return l[2];
        return "";
    }

    private static String cards(int yellow, int red) {
        List<String> p = new ArrayList<>();
        if (yellow > 0) p.add(yellow + " yellow");
        if (red > 0) p.add(red + " red");
        return p.isEmpty() ? "none" : String.join(" and ", p);
    }

    /** "Real Madrid have won all of their last 5", "Dortmund are unbeaten in 5 (3 wins)", "AEK have lost 3 of their last 5". */
    static String run(String club, List<String[]> last) {
        if (last.size() < 3) return "";
        int n = last.size(), w = 0, d = 0, l = 0, scored = 0, clean = 0;
        for (String[] r : last) {
            if ("W".equals(r[0])) w++; else if ("D".equals(r[0])) d++; else l++;
            if (Espn.parseInt(r[1]) > 0) scored++;
            if (Espn.parseInt(r[2]) == 0) clean++;
        }
        int streak = 0;
        String kind = last.get(n - 1)[0];
        for (int i = n - 1; i >= 0 && last.get(i)[0].equals(kind); i--) streak++;
        if (w == n) return club + " have won all of their last " + n;
        if (l == 0) return club + " are unbeaten in " + n + " (" + w + (w == 1 ? " win" : " wins") + ")";
        if ("W".equals(kind) && streak >= 3) return club + " have won their last " + streak;
        if ("L".equals(kind) && streak >= 2) return club + " have lost their last " + streak;
        if (scored == n) return club + " have scored in each of their last " + n;
        if (clean >= 3) return club + " have kept " + clean + " clean sheets in their last " + n;
        List<String> parts = new ArrayList<>();
        if (w > 0) parts.add(w + (w == 1 ? " win" : " wins"));
        if (d > 0) parts.add(d + (d == 1 ? " draw" : " draws"));
        parts.add(l + (l == 1 ? " defeat" : " defeats"));
        String said = parts.size() == 1 ? parts.get(0) : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
        return club + ": " + said + " in their last " + n;
    }

    /** "Last time out: Dortmund drew 2–2 at Bayern Munich". */
    static String lastGame(String club, List<String[]> last) {
        if (last.isEmpty()) return "";
        String[] r = last.get(last.size() - 1);
        String where = "@".equals(r[4]) ? " away" : " at home";
        if ("W".equals(r[0])) return "Last time out " + club + " beat " + r[3] + " " + r[1] + "–" + r[2] + where;
        if ("L".equals(r[0])) return "Last time out " + club + " lost " + r[2] + "–" + r[1] + where + " to " + r[3];
        return "Last time out " + club + " drew " + r[1] + "–" + r[2] + where + " to " + r[3];
    }

    /** "2 draws", "1 win each", "Real Madrid 2 wins, AEK 1, 1 draw" over the meetings ESPN lists. */
    private static String record(Game g, Espn.Summary s) {
        int hw = 0, aw = 0, d = 0;
        for (String[] m : s.meetings) {
            int a = Espn.parseInt(m[2]), b = Espn.parseInt(m[4]);
            if (a == b) { d++; continue; }
            String winner = a > b ? m[1] : m[3];
            if (same(winner, g.homeName) || same(winner, g.homeFull)) hw++; else aw++;
        }
        int n = s.meetings.size();
        if (d == n) return "all " + n + " drawn";
        return g.homeName + " " + hw + ", " + g.awayName + " " + aw + (d > 0 ? ", " + d + (d == 1 ? " draw" : " draws") : "")
                + " in " + n + " meetings";
    }

    private static boolean same(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        String x = a.toLowerCase(Locale.ROOT), y = b.toLowerCase(Locale.ROOT);
        return x.equals(y) || x.contains(y) || y.contains(x);
    }

    private static String[] row(List<String[]> table, String id) {
        if (id == null || id.isEmpty()) return null;
        for (String[] r : table) if (id.equals(r[0])) return r;
        return null;
    }

    static String ordinal(String rank) {
        int n = Espn.parseInt(rank);
        if (n <= 0) return rank;
        int t = n % 100;
        String suffix = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suffix;
    }

    private static String points(String p) {
        return p + (p.equals("1") ? " pt" : " pts");
    }

    private static int after(String words, String label) {
        int i = words.indexOf(label);
        if (i < 0) return 0;
        String rest = words.substring(i + label.length()).trim();
        int end = 0;
        while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;
        return end == 0 ? 0 : Espn.parseInt(rest.substring(0, end));
    }

    private static double num(String v) {
        if (v == null) return 0;
        try {
            return Double.parseDouble(v.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
