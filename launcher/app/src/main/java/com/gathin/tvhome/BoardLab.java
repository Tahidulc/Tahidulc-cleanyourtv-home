package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Scoreboard Lab on the TV's board, its pure parts (BoardCover does the rest): a switch for each of the Lab's stats
 * and which stat each covers; the Lab stats' own moments in a game (an early one, one after the break, a late one) and
 * what each shows; the momentum wave's stat. Nothing here is a price or a paid figure.
 */
final class BoardLab {
    private BoardLab() {
    }

    /** Each of the Lab's stats the settings can switch off, {key, name, what it is}: on unless switched off. */
    static final String[][] STATS = {
            {"xg", "Expected goals", "How many goals each side's chances are worth"},
            {"xgot", "xG on target", "How good the shots on target were"},
            {"chances", "Big chances", "The chances a player should score"},
            {"touches", "Touches in the box", "Who keeps getting into dangerous areas"},
            {"momentum", "Momentum", "Who is on top, and its wave once or twice a half"},
            {"ratings", "Player ratings", "The top rated, and the player of the match"},
            {"basics", "Possession, shots and corners", "And passing, fouls and the rest of the basics"},
            {"form", "Form", "Each side's last five, before kick-off"},
            {"streaks", "Streaks", "Unbeaten runs, winning runs, clean sheets"},
            {"h2h", "Head to head", "The record between the two, and the last away win"},
            {"drought", "Goal droughts", "A side without a goal in their last few games"},
            {"comeback", "Comebacks", "A side back level or ahead from behind"},
            {"early", "Early goals", "A goal inside the first five minutes"},
            {"referee", "Referee's cards", "The referee's bookings so far, with the first"},
            {"table", "Table as it stands", "Where each side would be if it ended now"},
            {"shots", "Big saves and great finishes", "By FotMob's own chances, as they happen"},
    };

    /** Pure: the switch a stat's name comes under ("Possession": basics); "" for one no switch covers (always shown). */
    static String statKey(String name) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        switch (n) {
            case "expected goals": return "xg";
            case "xg on target": return "xgot";
            case "big chances": return "chances";
            case "touches in box": return "touches";
            case "momentum": return "momentum";
            case "top rated": case "player of the match": return "ratings";
            case "possession": case "shots": case "on target": case "corners": case "pass accuracy": case "fouls": case "distance run":
            case "yellow cards": case "saves": case "offsides":
                return "basics";
            case "form": return "form";
            case "head to head": return "h2h";
            case "goal drought": return "drought";
            case "comeback": return "comeback";
            case "early goal": return "early";
            case "referee": return "referee";
            case "league table": case "as it stands": return "table";
            case "big save": case "great finish": return "shots";
            default: break;
        }
        return n.startsWith("streak") ? "streaks" : "";
    }

    /**
     * Pure: the Lab stats' own moment due ("e1": 12 to 20 minutes in, "s2": 51 to 58, "late": 83 to 87), in play only;
     * null at any other minute. Each comes once a game, besides the board's usual stats moments.
     */
    static String slot(int state, boolean halftime, int minute) {
        if (state != 0 || halftime) return null;
        if (minute >= 12 && minute <= 20) return "e1";
        if (minute >= 51 && minute <= 58) return "s2";
        if (minute >= 83 && minute <= 87) return "late";
        return null;
    }

    /**
     * Pure: what a Lab moment shows from the Lab's stats worked out for the game (by name: "Head to head", "Streak home",
     * "Streak away", "Goal drought home", "Goal drought away", "As it stands"): the head to head and the sides' runs early
     * on (a drought only once a side is still without a goal at the half hour, MatchStats.drought), the table as it
     * stands and a drought after the break, the table late on. Empty when there is none.
     */
    static List<String[]> pick(String slot, Map<String, String[]> lab) {
        String[] want;
        int most;
        switch (slot == null ? "" : slot) {
            case "e1":
                want = new String[]{"Head to head", "Streak home", "Streak away", "Goal drought home", "Goal drought away"};
                most = 3;
                break;
            case "s2":
                want = new String[]{"As it stands", "Goal drought home", "Goal drought away"};
                most = 2;
                break;
            case "late":
                want = new String[]{"As it stands"};
                most = 1;
                break;
            default:
                return new ArrayList<>();
        }
        List<String[]> out = new ArrayList<>();
        for (String w : want) {
            String[] s = lab == null ? null : lab.get(w);
            if (s != null && out.size() < most) out.add(s);
        }
        return out;
    }

    /**
     * Pure: the momentum wave's stat from FotMob's momentum ({minute, -100 to 100}, home above), the last fifteen minutes up
     * to now: {"Momentum", its values (BoardStats.waveCode), which side is on top ("WHI on top", "Level"), "wave"}; null
     * with fewer than four values to draw.
     */
    static String[] wave(List<int[]> momentum, int minute, String homeCode, String awayCode) {
        List<int[]> last = com.gathin.broadcast.BoardFx.lastMinutes(momentum, minute, 15);
        if (last.size() < 4) return null;
        int top = com.gathin.broadcast.BoardFx.onTop(last);
        String who = top == 1 ? homeCode + " on top" : top == 2 ? awayCode + " on top" : "Level";
        return new String[]{"Momentum", BoardStats.waveCode(last), who, "wave"};
    }

    /**
     * Pure: whether a Lab sound is heard: only while what it is for is up on the screen (and not faded away), and only
     * with the swish switched on (the Lab's sounds go with it).
     */
    static boolean heard(boolean up, float alpha, boolean swishOn) {
        return up && alpha >= 0.05f && swishOn;
    }

    /** Pure: the cards FotMob lists so far ({type, minute, home, name, id, card, ...}): {yellows, reds}. */
    static int[] cards(List<String[]> events) {
        int y = 0, r = 0;
        for (String[] e : events == null ? new ArrayList<String[]>() : events) {
            if (e == null || e.length < 6 || !"Card".equals(e[0])) continue;
            String card = e[5] == null ? "" : e[5].toLowerCase(Locale.ROOT);
            if (card.contains("red")) r++;
            else if (card.contains("yellow")) y++;
        }
        return new int[]{y, r};
    }
}
