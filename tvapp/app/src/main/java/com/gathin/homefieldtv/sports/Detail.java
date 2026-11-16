package com.gathin.homefieldtv.sports;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One game's page as it stood when read: the scoreboard's state (score, clock, half), ESPN's events, stats and line-ups,
 * FotMob's figures beside them (expected goals, big chances…) and its momentum, and the minutes added in each half.
 * The page keeps these as they come (InStep.Line) and shows the one the channel's picture has reached. Read off the main
 * thread; anything a source doesn't answer is left out.
 */
public final class Detail {
    public Game game;
    /** {minute, what ("Goal", "Yellow Card", "Red Card", "Substitution", "VAR"…), player, "home"/"away"/"", key, the second player}. */
    public final List<String[]> events = new ArrayList<>();
    /** {label, home, away}: possession first, then shots, on target, expected goals… */
    public final List<String[]> stats = new ArrayList<>();
    /** Each side's line-up as Espn.P_* indexes it, the starters first. */
    public final List<String[]> homeLineup = new ArrayList<>(), awayLineup = new ArrayList<>();
    public String homeFormation = "", awayFormation = "", referee = "";
    /** FotMob's momentum, {minute, value} (above 0: the home side on top); empty without. */
    public final List<int[]> momentum = new ArrayList<>();
    /** The minutes added in each half (and extra time's), -1 until put up. */
    public final int[] added = {-1, -1, -1, -1};
    /** When it was read. */
    public long at;

    /** The game's page read now. Off the main thread. */
    public static Detail read(Game g) {
        Detail d = new Detail();
        d.at = System.currentTimeMillis();
        d.game = fresh(g);
        if (d.game == null) d.game = g;
        Espn.Summary s = g == null || g.slug == null || g.id == null ? null : Espn.summary(g.slug, g.id, 10_000L);
        if (s != null) {
            d.events.addAll(s.events);
            d.stats.addAll(s.stats);
            d.homeLineup.addAll(s.homeLineup);
            d.awayLineup.addAll(s.awayLineup);
            d.homeFormation = s.homeFormation;
            d.awayFormation = s.awayFormation;
            d.referee = s.referee;
            System.arraycopy(s.added, 0, d.added, 0, Math.min(4, s.added.length));
        }
        if (g != null && g.state != Game.PRE && g.id != null) {
            try {
                FotMob.Match m = FotMob.forGame(g.id, g.homeName, g.awayName, g.kickoff);
                if (m != null) {
                    FotMob.addTo(d.stats, g.id, g.homeName, g.awayName, g.kickoff);
                    d.momentum.addAll(m.momentum);
                    for (int i = 0; i < 4; i++) if (d.added[i] < 0 && m.added[i] > 0) d.added[i] = m.added[i];
                }
            } catch (RuntimeException ignored) {
                // ESPN's alone
            }
        }
        return d;
    }

    /** The game as the scoreboard has it now (its own competition's, today's or the day it kicked off). */
    private static Game fresh(Game g) {
        if (g == null || g.slug == null || g.id == null) return null;
        LocalDate day = g.kickoff > 0 ? Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();
        for (Game x : Espn.scoreboard(g.slug, day, 10_000L)) if (g.id.equals(x.id)) return x;
        return null;
    }

    /** Pure: what in it a goal, a card or the kick-off changes (for "It's just happened": InStep.Line's key). */
    public static String key(Detail d) {
        if (d == null || d.game == null) return "";
        return d.game.homeScore + "-" + d.game.awayScore + "|" + d.game.state + "|" + d.events.size();
    }

    /** Pure: the events, newest first, as the page lists them. */
    public List<String[]> newestFirst() {
        List<String[]> out = new ArrayList<>(events);
        Collections.reverse(out);
        return out;
    }

    /** Pure: a stat's two sides as shares of their sum (0.5 each when neither has any): for its bar. */
    public static float[] shares(String home, String away) {
        double h = number(home), a = number(away);
        if (h + a <= 0) return new float[]{0.5f, 0.5f};
        return new float[]{(float) (h / (h + a)), (float) (a / (h + a))};
    }

    /** Pure: the number at the start of a stat ("54%" 54, "1.23" 1.23, "7 (3)" 7); 0 for none. */
    static double number(String s) {
        if (s == null) return 0;
        StringBuilder b = new StringBuilder();
        for (char ch : s.trim().toCharArray()) {
            if (Character.isDigit(ch) || ch == '.') b.append(ch);
            else if (b.length() > 0) break;
        }
        try {
            return b.length() == 0 ? 0 : Double.parseDouble(b.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
