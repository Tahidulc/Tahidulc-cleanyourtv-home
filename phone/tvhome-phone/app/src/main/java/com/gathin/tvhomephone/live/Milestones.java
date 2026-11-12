package com.gathin.tvhomephone.live;

import java.util.Locale;

/**
 * What a goal or a yellow card means for the player, as a tag on its pop-out beside the scoreboard (BoardDrop): a goal's
 * place in the game ("2nd of the game", "Hat-trick!") or in the season in this competition ("3rd of the season"); a
 * yellow card's ban, where the competition counts them: the Premier League (5 by matchweek 19, 10 by matchweek 32, 15
 * at any time) and UEFA's club competitions (the third, then every second after: the fifth, the seventh). Pure: the
 * tests check it.
 */
final class Milestones {
    private Milestones() { }

    /** Pure: 1st, 2nd, 3rd, 4th ... 11th, 12th, 13th, 21st. */
    static String ordinal(int n) {
        int t = n % 100;
        String suf = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suf;
    }

    /**
     * Pure: a goal's tag: in the game first (his second, a hat-trick, more), else his season's count in this competition
     * with tonight's goal in it (-1 when not known: no tag). Own goals get none.
     */
    static String goal(int inGame, int inSeason, boolean own) {
        if (own) return "";
        if (inGame == 3) return "Hat-trick!";
        if (inGame >= 2) return ordinal(inGame) + " of the game";
        if (inSeason >= 1) return ordinal(inSeason) + " of the season";
        return "";
    }

    /**
     * Pure: his goals this season in this competition with tonight's: the season's figure as ESPN has it, plus tonight's
     * so far when tonight's game is not in it yet.
     */
    static int seasonWithTonight(int season, boolean tonightCounted, int tonightSoFar) {
        if (season < 0) return -1;
        return tonightCounted ? Math.max(season, tonightSoFar) : season + tonightSoFar;
    }

    /** Whether a competition (ESPN's slug) has its yellow cards counted towards bans here. */
    static boolean counts(String slug) {
        return "eng.1".equals(slug) || isUefa(slug);
    }

    private static boolean isUefa(String slug) {
        return "uefa.champions".equals(slug) || "uefa.europa".equals(slug) || "uefa.europa.conf".equals(slug);
    }

    /** The yellow card line's kinds: none, one more and he's banned, banned for the next game. */
    static final int NONE = 0, ONE_MORE = 1, BANNED = 2;

    /**
     * Pure: what his yellow cards in this competition (this one counted) mean: banned at a threshold, one short of one
     * a warning. The Premier League's thresholds by its matchweek; UEFA's at the third and every second one after.
     */
    static int yellow(String slug, int yellows, int matchweek) {
        if (yellows <= 0) return NONE;
        if ("eng.1".equals(slug)) {
            int[] at = matchweek <= 19 ? new int[]{5, 10, 15} : matchweek <= 32 ? new int[]{10, 15} : new int[]{15};
            for (int t : at) if (yellows == t) return BANNED;
            for (int t : at) if (yellows == t - 1) return ONE_MORE;
            return NONE;
        }
        if (isUefa(slug)) {
            if (yellows >= 3 && yellows % 2 == 1) return BANNED;
            if (yellows >= 2 && yellows % 2 == 0) return ONE_MORE;
            return NONE;
        }
        return NONE;
    }

    /** Pure: the yellow card's words for its kind ("" for none). */
    static String yellowWords(int kind) {
        return kind == BANNED ? "Banned for the next game" : kind == ONE_MORE ? "One more and he's banned" : "";
    }

    /** Pure: a matchweek guessed from the date when the table cannot tell (August to December the first half). */
    static int matchweekByDate(java.util.Calendar cal) {
        int m = cal.get(java.util.Calendar.MONTH) + 1, d = cal.get(java.util.Calendar.DAY_OF_MONTH);
        if (m >= 8) return 10;
        if (m < 4 || m == 4 && d < 20) return 25;
        return 35;
    }

    /** Pure: the index of a stat's label in ESPN's overview ("G", "YC"), -1 when it has none. */
    static int labelAt(org.json.JSONArray labels, String want) {
        for (int i = 0; labels != null && i < labels.length(); i++) {
            if (want.equalsIgnoreCase(labels.optString(i))) return i;
        }
        return -1;
    }

    /** Pure: whether a split's name ("2026-27 English Premier League") is the competition's. */
    static boolean splitFor(String splitName, String competition) {
        if (splitName == null || competition == null || competition.isEmpty()) return false;
        return splitName.toLowerCase(Locale.ROOT).contains(competition.toLowerCase(Locale.ROOT));
    }

    /**
     * Pure: his season in a competition from ESPN's athlete overview: {goals, yellow cards, 1 when tonight's game is in it
     * already (its game log has it), else 0}; null when the overview has no figures for the competition. names: the
     * competition's names to look for in its splits ("Premier League", "English Premier League").
     */
    static int[] season(org.json.JSONObject overview, String eventId, String... names) {
        org.json.JSONObject st = overview == null ? null : overview.optJSONObject("statistics");
        if (st == null) return null;
        org.json.JSONArray labels = st.optJSONArray("labels"), splits = st.optJSONArray("splits");
        int g = labelAt(labels, "G"), y = labelAt(labels, "YC");
        if (splits == null || g < 0 && y < 0) return null;
        for (String name : names) {
            for (int i = 0; i < splits.length(); i++) {
                org.json.JSONObject sp = splits.optJSONObject(i);
                if (sp == null || !splitFor(sp.optString("displayName"), name)) continue;
                org.json.JSONArray v = sp.optJSONArray("stats");
                if (v == null) continue;
                org.json.JSONObject log = overview.optJSONObject("gameLog");
                boolean counted = log != null && eventId != null && !eventId.isEmpty() && log.toString().contains("\"" + eventId + "\"");
                return new int[]{number(v, g), number(v, y), counted ? 1 : 0};
            }
        }
        return null;
    }

    private static int number(org.json.JSONArray v, int i) {
        if (i < 0 || i >= v.length()) return 0;
        String s = v.optString(i, "0").replace(",", "").trim();
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Pure: how many goals a player has in the game up to and including this one (its key), own goals not counted. */
    static int inGame(java.util.List<String[]> goals, String key, String player) {
        int n = 0;
        if (goals == null || player == null) return 0;
        for (String[] x : goals) {
            if (x.length > 1 && player.equals(x[1]) && !(x.length > 4 && "og".equals(x[4]))) n++;
            if (key != null && x.length > 0 && key.equals(x[0])) break;
        }
        return n;
    }

    /**
     * Pure: ESPN's id for a player in the line-ups by his name as the scores give it ({..., name, ..., short name, ...,
     * id} rows, Espn.P_*): his full name, else his surname when only one has it; null when none or more than one.
     */
    static String athleteId(java.util.List<String[]> lineup, String name, int nameAt, int shortAt, int idAt) {
        if (lineup == null || name == null || name.isEmpty()) return null;
        String want = name.trim();
        for (String[] p : lineup) if (p.length > idAt && want.equalsIgnoreCase(p[nameAt])) return p[idAt].isEmpty() ? null : p[idAt];
        String last = want.contains(" ") ? want.substring(want.lastIndexOf(' ') + 1) : want;
        String found = null;
        for (String[] p : lineup) {
            if (p.length <= idAt) continue;
            boolean same = last.equalsIgnoreCase(p[shortAt]) || p[nameAt].toLowerCase(Locale.ROOT).endsWith(" " + last.toLowerCase(Locale.ROOT));
            if (!same) continue;
            if (found != null) return null;
            found = p[idAt];
        }
        return found == null || found.isEmpty() ? null : found;
    }

    /** Pure: the matchweek a club is playing, from its games played in the table as it stood (0 when not known: the date's guess). */
    static int matchweek(int played, java.util.Calendar today) {
        return played > 0 ? played + 1 : matchweekByDate(today);
    }
}
