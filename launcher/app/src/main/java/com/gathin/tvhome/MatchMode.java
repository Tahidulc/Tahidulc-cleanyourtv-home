package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Match mode (the Control Centre's tile, MatchPanel): for one game, chosen from today's, until a few minutes after
 * its full time (or four hours at most). While it is on: nothing pops up but that game's own alerts, the doorbell and
 * the TV's questions (quiet); the lights that are on go down at kick-off and the second half and back up at half time
 * and full time (dim); a goal for the club cheered (the starred club playing, else either side) flashes them in its
 * colours, the other side's dims them a moment, a red card is one red pulse (goal lights). Every one of these comes
 * as the moment shows on screen: the game is the one watched, so its alerts and so its lights wait for the channel
 * (InStep). Kept in "match_mode"; the sums are pure, for the tests.
 */
final class MatchMode {
    private MatchMode() { }

    private static final String FILE = "match_mode";
    /** How long it runs at most, and how long after full time it stays on. */
    static final long MAX_MS = 4 * 3_600_000L, AFTER_FT_MS = 3 * 60_000L;
    /** The lights' level from kick-off. */
    static final int DIM_PERCENT = 30;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- on and off ------------------------------------------------------------------------------------------------

    static boolean active(Context c) {
        SharedPreferences p = prefs(c);
        return p.getBoolean("on", false) && System.currentTimeMillis() < p.getLong("until", 0);
    }

    /** The game it is on for (null when off). */
    static String game(Context c) {
        return active(c) ? prefs(c).getString("game", null) : null;
    }

    static boolean isGame(Context c, String gameId) {
        return gameId != null && gameId.equals(game(c));
    }

    static String label(Context c) {
        return prefs(c).getString("label", "");
    }

    /** On for this game: the club cheered and both sides' colours kept; lights down at once when it is already on. */
    static void start(Context c, ScoresTicker.Game g) {
        if (g == null || g.id == null) return;
        Set<String> starred = new HashSet<>(Arrays.asList(SyncHub.starredClubs(c)));
        String side = side(starred, g.homeId, g.awayId);
        long now = System.currentTimeMillis();
        long from = Math.max(now, g.kickoff);
        prefs(c).edit().putBoolean("on", true).putString("game", g.id).putString("label", label(g))
                .putString("side", side).putInt("c.home", g.homeColor).putInt("c.away", g.awayColor)
                .putInt("a.home", g.homeAlt).putInt("a.away", g.awayAlt)
                .putLong("until", Math.min(from + MAX_MS, now + MAX_MS + 3_600_000L)).apply();
        AlertLog.note(c, "Match mode on: " + AlertLog.game(g) + (side.isEmpty() ? "" : " (cheering " + side + ")"));
        if (g.state == 0 && !g.halftime && dims(c) && MatchLights.available(c)) MatchLights.dim(c, DIM_PERCENT);
    }

    static void stop(Context c) {
        boolean was = active(c);
        prefs(c).edit().putBoolean("on", false).apply();
        if (was) {
            AlertLog.note(c, "Match mode off");
            if (MatchLights.available(c)) MatchLights.restore(c);
        }
    }

    /** The tile's line: "On · Everton v Spurs", or "Off". */
    static String words(Context c) {
        return active(c) ? "On · " + label(c) : "Off";
    }

    static String label(ScoresTicker.Game g) {
        String h = g.homeName != null ? g.homeName : g.home, a = g.awayName != null ? g.awayName : g.away;
        return (h == null ? "?" : h) + " v " + (a == null ? "?" : a);
    }

    // ---- its switches ----------------------------------------------------------------------------------------------

    static boolean quiet(Context c) { return prefs(c).getBoolean("quiet", true); }

    static void setQuiet(Context c, boolean on) { prefs(c).edit().putBoolean("quiet", on).apply(); }

    static boolean dims(Context c) { return prefs(c).getBoolean("dim", true); }

    static void setDims(Context c, boolean on) { prefs(c).edit().putBoolean("dim", on).apply(); }

    static boolean goalLights(Context c) { return prefs(c).getBoolean("goals", true); }

    static void setGoalLights(Context c, boolean on) { prefs(c).edit().putBoolean("goals", on).apply(); }

    // ---- quiet -----------------------------------------------------------------------------------------------------

    /** Whether Match mode's quiet keeps a pop-up of this kind back (gameId: the game it is about, null for none). */
    static boolean keepsBack(Context c, String kind, String gameId) {
        SharedPreferences p = prefs(c);
        boolean on = p.getBoolean("on", false) && System.currentTimeMillis() < p.getLong("until", 0);
        return keepsBack(on, quiet(c), kind, gameId, p.getString("game", null));
    }

    /** Pure: the rule: on and quiet, not the doorbell or the TV's own questions, and not about the game itself. */
    static boolean keepsBack(boolean on, boolean quiet, String kind, String gameId, String matchGame) {
        if (!on || !quiet) return false;
        if (NotifyKinds.ALWAYS.equals(kind) || "doorbell".equals(kind)) return false;
        return gameId == null || !gameId.equals(matchGame);
    }

    // ---- what happens in the game ------------------------------------------------------------------------------------

    /**
     * Something in the game, as it shows on screen (main thread): "ko" kick-off, "ht" half time, "2h" the second half,
     * "ft" full time, "goal" and "red" (side: "home" or "away").
     */
    static void event(Context c, ScoresTicker.Game g, String what, String side) {
        if (g == null || !isGame(c, g.id) || !MatchLights.available(c)) {
            if (g != null && "ft".equals(what) && isGame(c, g.id)) endSoon(c);
            return;
        }
        SharedPreferences p = prefs(c);
        switch (what) {
            case "ko":
            case "2h":
                if (dims(c)) MatchLights.dim(c, DIM_PERCENT);
                break;
            case "ht":
                if (dims(c)) MatchLights.restore(c);
                break;
            case "ft":
                MatchLights.restore(c);
                endSoon(c);
                break;
            case "goal": {
                if (!goalLights(c)) break;
                String mine = p.getString("side", "");
                if (cheers(mine, side)) {
                    boolean home = !"away".equals(side);
                    MatchLights.celebrate(c, colours(p.getInt(home ? "c.home" : "c.away", 0), p.getInt(home ? "a.home" : "a.away", 0)));
                } else {
                    MatchLights.pulse(c, false);
                }
                break;
            }
            case "red":
                if (goalLights(c)) MatchLights.pulse(c, true);
                break;
            default:
                break;
        }
    }

    /** Full time: off in a few minutes (the full-time card and the lights back first). */
    private static void endSoon(Context c) {
        prefs(c).edit().putLong("until", Math.min(prefs(c).getLong("until", 0), System.currentTimeMillis() + AFTER_FT_MS)).apply();
    }

    /** Test the goal lights (Match mode's button): the cheered side's colours, else a white and gold flash. */
    static void test(Context c, ScoresTicker.Game g) {
        if (!MatchLights.available(c)) return;
        if (g == null) {
            MatchLights.celebrate(c, colours(0, 0));
            return;
        }
        Set<String> starred = new HashSet<>(Arrays.asList(SyncHub.starredClubs(c)));
        boolean home = !"away".equals(side(starred, g.homeId, g.awayId));
        MatchLights.celebrate(c, colours(home ? g.homeColor : g.awayColor, home ? g.homeAlt : g.awayAlt));
    }

    // ---- the pure parts --------------------------------------------------------------------------------------------

    /** Pure: the side cheered: the starred club's ("home" or "away"), "" when neither or both are starred. */
    static String side(Set<String> starred, String homeId, String awayId) {
        boolean h = homeId != null && starred.contains(homeId), a = awayId != null && starred.contains(awayId);
        return h == a ? "" : h ? "home" : "away";
    }

    /** Pure: whether a goal by side is one to celebrate: the cheered side's, or either when none is cheered. */
    static boolean cheers(String mine, String scored) {
        return mine == null || mine.isEmpty() || mine.equals(scored);
    }

    /**
     * Pure: the goal lights' colours from a club's kit: the kit as bright as a bulb makes it, then white (or the
     * second colour, or gold, when the kit is white itself); white and gold when the club's colour is not known.
     */
    static int[] colours(int kit, int alt) {
        int white = 0xFFFFFFFF, gold = 0xFFFFC21A;
        if ((kit & 0xFFFFFF) == 0) return new int[]{white, gold};
        int main = bright(kit);
        // A white kit (or a black or grey one, white at a bulb's brightest): white, then its second colour or gold.
        if (nearWhite(kit) || main == white) {
            int second = (alt & 0xFFFFFF) != 0 && !nearWhite(alt) && bright(alt) != white ? bright(alt) : gold;
            return new int[]{white, second};
        }
        return new int[]{main, white};
    }

    /** Pure: the colour at its brightest (its strongest channel full), its hue kept; grey and black become white. */
    static int bright(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max == 0 || max - min < 24) return 0xFFFFFFFF;
        float k = 255f / max;
        return 0xFF000000 | Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
    }

    static boolean nearWhite(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return r > 0xD8 && g > 0xD8 && b > 0xD8;
    }
}
