package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Locale;

/**
 * What a goal or a card means for the player, as a small tag on its pop-out beside the scoreboard (BoardDrop): a goal's
 * place in his season in this competition ("3rd of the season") and in the game ("2nd of the game", "Hat-trick!"); a
 * yellow card that leaves him one short of a ban, or brings one, where the competition's rule is known (the Premier
 * League's 5, 10 and 15; UEFA's club competitions' 3rd, 5th, 7th...). His season so far is ESPN's player overview
 * (each competition's tallies, less what its game log already has of tonight). Pure: the tests check it.
 */
final class Milestones {
    private Milestones() { }

    /** A goal's tag's colour (gold, with a star) and a card's (the yellow card's). */
    static final int GOLD = 0xFFFFC21A, YELLOW = 0xFFFFD21F;

    /** Pure: 1st, 2nd, 3rd, 4th ... 11th, 12th, 13th, 21st, 22nd. */
    static String ordinal(int n) {
        int t = n % 100;
        if (t >= 11 && t <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    /**
     * Pure: a goal's tag from his goals in this game (this one counted) and in the season in this competition (this one
     * counted; 0 or less: not known), worded as the phone's are: the game first (a hat-trick, his 2nd or 4th of the
     * game), else the season's; null for none.
     */
    static String goal(int inGame, int season) {
        if (inGame == 3) return "Hat-trick!";
        if (inGame >= 2) return ordinal(inGame) + " of the game";
        return season > 0 ? ordinal(season) + " of the season" : null;
    }

    /**
     * Pure: how many of the game's entries ({key, player, minute, side, kind, ESPN id}: goals or yellow cards, in order) are
     * this one's player's, up to and including it (by ESPN's id, else by name; own goals aside); 0 when it is not there.
     */
    static int inGame(java.util.List<String[]> all, String[] one) {
        if (all == null || one == null || one.length < 2) return 0;
        String id = one.length > 5 ? one[5] : "", name = one[1];
        int n = 0;
        for (String[] x : all) {
            if (x == null || x.length < 2) continue;
            boolean og = x.length > 4 && "og".equals(x[4]);
            String xid = x.length > 5 ? x[5] : "";
            boolean same = id != null && !id.isEmpty() ? id.equals(xid) : name != null && name.equals(x[1]);
            if (same && !og) n++;
            if (x == one || x[0] != null && x[0].equals(one[0])) return n;
        }
        return 0;
    }

    /**
     * The Premier League's own deadlines for its 5 and 10 yellow cards (its 19th and 32nd matchweeks), by the date as
     * the phone guesses them: August to December the first, January to 19 April the second.
     */
    private static boolean beforeRound19(LocalDate d) {
        return d.getMonthValue() >= 8;
    }

    private static boolean beforeRound32(LocalDate d) {
        return d.getMonthValue() >= 8 || d.getMonthValue() < 4 || d.getMonthValue() == 4 && d.getDayOfMonth() < 20;
    }

    /**
     * Pure: the yellow cards that bring the next ban with cardsAfter of them now (this one counted), in this competition
     * at this date: the Premier League's 5 (to about its 19th round), 10 (to about its 32nd), 15; UEFA's club competitions'
     * 3rd and every second one after it (the 5th, the 7th), as the phone has them; -1 for a competition whose rule is not
     * known.
     */
    static int banAt(String slug, int cardsAfter, LocalDate date) {
        String s = slug == null ? "" : slug;
        if ("eng.1".equals(s)) {
            if (cardsAfter <= 5 && beforeRound19(date)) return 5;
            if (cardsAfter <= 10 && beforeRound32(date)) return 10;
            return cardsAfter <= 15 ? 15 : -1;
        }
        if (s.equals("uefa.champions") || s.equals("uefa.europa") || s.equals("uefa.europa.conf")) {
            int t = 3;
            while (t < cardsAfter) t += 2;
            return t;
        }
        return -1;
    }

    /** Pure: a yellow card's tag: one short of a ban, or a ban for the next game; null for none (or a rule not known). */
    static String yellow(String slug, int cardsAfter, LocalDate date) {
        if (cardsAfter <= 0) return null;
        int t = banAt(slug, cardsAfter, date);
        if (t < 0) return null;
        if (cardsAfter == t) return "Banned for the next game";
        if (cardsAfter == t - 1) return "One more and he's banned";
        return null;
    }

    /** Pure: the words ESPN names a competition by in a player's season ("Premier League" for eng.1 …), best first. */
    static String[] names(String slug, String league) {
        String l = league == null ? "" : league;
        switch (slug == null ? "" : slug) {
            case "eng.1": return new String[]{"Premier League", l};
            case "uefa.champions": return new String[]{"Champions League", l};
            case "uefa.europa": return new String[]{"Europa League", l};
            case "uefa.europa.conf": return new String[]{"Conference League", l};
            case "eng.fa": return new String[]{"FA Cup", l};
            case "eng.league_cup": return new String[]{"League Cup", "Carabao", l};
            case "esp.1": return new String[]{"LALIGA", "La Liga", l};
            case "ger.1": return new String[]{"Bundesliga", l};
            case "ita.1": return new String[]{"Serie A", l};
            case "fra.1": return new String[]{"Ligue 1", l};
            default: return new String[]{l};
        }
    }

    /**
     * Pure: his season in this competition before tonight {goals, yellow cards} from ESPN's player overview: the split
     * named for it, less what the game log already has of tonight's game; -1 for a figure not there.
     */
    static int[] season(JSONObject overview, String[] names, String eventId) {
        int[] out = {-1, -1};
        JSONObject st = overview == null ? null : overview.optJSONObject("statistics");
        JSONArray labels = st == null ? null : st.optJSONArray("labels");
        JSONArray splits = st == null ? null : st.optJSONArray("splits");
        if (labels == null || splits == null) return out;
        int gi = index(labels, "G"), yi = index(labels, "YC");
        JSONArray stats = null;
        for (String want : names) {
            if (want == null || want.trim().isEmpty()) continue;
            String w = want.toLowerCase(Locale.ROOT);
            for (int i = 0; i < splits.length() && stats == null; i++) {
                JSONObject sp = splits.optJSONObject(i);
                if (sp != null && sp.optString("displayName").toLowerCase(Locale.ROOT).contains(w)) stats = sp.optJSONArray("stats");
            }
            if (stats != null) break;
        }
        if (stats == null) return out;
        out[0] = gi < 0 ? -1 : number(stats.optString(gi));
        out[1] = yi < 0 ? -1 : number(stats.optString(yi));
        // Tonight already in the game log: its goals and cards taken out (they are counted from the game itself).
        JSONObject log = overview.optJSONObject("gameLog");
        JSONArray logStats = log == null ? null : log.optJSONArray("statistics");
        JSONObject ls = logStats == null ? null : logStats.optJSONObject(0);
        JSONArray ll = ls == null ? null : ls.optJSONArray("labels");
        JSONArray events = ls == null ? null : ls.optJSONArray("events");
        for (int i = 0; ll != null && events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || eventId == null || !eventId.equals(e.optString("eventId"))) continue;
            JSONArray es = e.optJSONArray("stats");
            int lg = index(ll, "G"), ly = index(ll, "YC");
            if (out[0] >= 0 && lg >= 0 && es != null) out[0] = Math.max(0, out[0] - Math.max(0, number(es.optString(lg))));
            if (out[1] >= 0 && ly >= 0 && es != null) out[1] = Math.max(0, out[1] - Math.max(0, number(es.optString(ly))));
        }
        return out;
    }

    private static int index(JSONArray labels, String want) {
        for (int i = 0; i < labels.length(); i++) if (want.equals(labels.optString(i))) return i;
        return -1;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
