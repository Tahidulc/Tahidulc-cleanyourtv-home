package com.gathin.tvhomephone.live;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Scoreboard Lab on the phone's board, its pure parts (ScoreBoard does the rest), as the TV's: a switch for each of
 * the Lab's stats and which stat each covers; the Lab stats' own moments in a game (an early one, one after the break,
 * a late one) and what each shows; the momentum wave's stat; the head to head and each side's run before kick-off.
 * Nothing here is a price or a paid figure.
 */
final class BoardLab {
    private BoardLab() {
    }

    /** Each of the Lab's stats the settings can switch off, by its key (as the TV's): on unless switched off. */
    static final String[] KEYS = {"xg", "xgot", "chances", "touches", "momentum", "ratings", "basics", "form", "winchance", "streaks",
            "h2h", "drought", "comeback", "early", "referee", "table", "shots", "weather"};

    /** Each key's name in the settings, in the same order. */
    static final int[] NAMES = {R.string.lab_stat_xg, R.string.lab_stat_xgot, R.string.lab_stat_chances, R.string.lab_stat_touches,
            R.string.lab_stat_momentum, R.string.lab_stat_ratings, R.string.lab_stat_basics, R.string.lab_stat_form,
            R.string.lab_stat_winchance, R.string.lab_stat_streaks, R.string.lab_stat_h2h, R.string.lab_stat_drought, R.string.lab_stat_comeback,
            R.string.lab_stat_early, R.string.lab_stat_referee, R.string.lab_stat_table, R.string.lab_stat_shots,
            R.string.lab_stat_weather};

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
                return "basics";
            case "form": return "form";
            case "win chance": return "winchance";
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
     * null at any other minute. Each comes once a game, besides the board's usual stats moments (as the TV's).
     */
    static String slot(int state, boolean halftime, int minute) {
        if (state != Game.LIVE || halftime) return null;
        if (minute >= 12 && minute <= 20) return "e1";
        if (minute >= 51 && minute <= 58) return "s2";
        if (minute >= 83 && minute <= 87) return "late";
        return null;
    }

    /**
     * Pure: what a Lab moment shows from the Lab's stats worked out for the game (by name: "Head to head", "Streak home",
     * "Streak away", "Goal drought home", "Goal drought away", "As it stands"): the head to head and the sides' runs early
     * on, the table as it stands and a drought after the break, the table late on. Empty when there is none.
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

    /** Pure (off the main thread): the Lab's stats before kick-off from FotMob's match: the head to head, each side's run. */
    static Map<String, String[]> before(FotMob.Match m, String homeCode, String awayCode) {
        Map<String, String[]> out = new HashMap<>();
        if (m == null) return out;
        String[] h2h = com.gathin.broadcast.MatchStats.h2h(m.h2h, m.homeTeamId, m.awayTeamId, homeCode, awayCode);
        if (h2h != null) out.put("Head to head", h2h);
        String[] sh = com.gathin.broadcast.MatchStats.streak(m.homeForm, homeCode), sa = com.gathin.broadcast.MatchStats.streak(m.awayForm, awayCode);
        if (sh != null) out.put("Streak home", sh);
        if (sa != null) out.put("Streak away", sa);
        return out;
    }
}
