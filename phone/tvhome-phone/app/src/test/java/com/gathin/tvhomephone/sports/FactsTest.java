package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FactsTest {
    private static Game game(int state, String home, String away, String homeId, String awayId) {
        Game g = new Game();
        g.id = "1";
        g.state = state;
        g.homeName = home;
        g.awayName = away;
        g.homeId = homeId;
        g.awayId = awayId;
        g.leagueName = "Champions League";
        g.venue = "OPAP Arena";
        return g;
    }

    private static String all(List<String[]> facts) {
        StringBuilder b = new StringBuilder();
        for (String[] f : facts) b.append(f[0]).append(": ").append(f[1]).append('\n');
        return b.toString();
    }

    @Test public void finishedGameLeadsWithItsNumbers() throws Exception {
        Espn.Summary s = SummaryFixtures.aek();
        Game g = game(Game.POST, "AEK Athens", "Real Madrid", "887", "86");
        g.homeScore = 2;
        g.awayScore = 3;
        List<String[]> f = Facts.of(g, s);
        String text = all(f);
        assertEquals("STAT", f.get(0)[0]);
        assertTrue(text, text.contains("Real Madrid have had 70% of the ball"));
        assertTrue(text, text.contains("Shots 9–24 · on target 5–8"));
        assertTrue(text, text.contains("Brignoli has made 5 saves"));
        assertTrue(text, text.contains("Mbappé has had 7 shots"));
        assertTrue(text, text.contains("Corners 0–7"));
        assertTrue(text, text.contains("Real Madrid have won all of their last 5"));
        assertTrue(text, text.contains("Last met in 2002: Real Madrid 2–2 AEK Athens (all 2 drawn)"));
    }

    @Test public void beforeKickOffTheTableRunsAndScorers() throws Exception {
        Espn.Summary s = SummaryFixtures.dortmund();
        Game g = game(Game.PRE, "Dortmund", "Betis", "124", "244");
        List<String[]> f = Facts.of(g, s);
        String text = all(f);
        assertTrue(text, text.contains("TABLE: Dortmund "));
        assertTrue(text, text.contains("Dortmund are unbeaten in 5 (3 wins)"));
        assertTrue(text, text.contains("Last time out Dortmund drew 2–2 away to Bayern Munich"));
        assertTrue(text, text.contains("Serhou Guirassy has 4 goals in 3 games for Dortmund in the Champions League"));
        assertTrue(text, !text.contains("STAT:"));
    }

    @Test public void runs() {
        List<String[]> last = new ArrayList<>();
        for (String r : new String[]{"W", "L", "W", "W", "W"}) last.add(new String[]{r, "W".equals(r) ? "2" : "0", "1", "X", "vs", "L"});
        assertEquals("Spurs have won their last 3", Facts.run("Spurs", last));
        last.set(4, new String[]{"L", "0", "2", "X", "vs", "L"});
        last.set(3, new String[]{"L", "0", "2", "X", "vs", "L"});
        assertEquals("Spurs have lost their last 2", Facts.run("Spurs", last));
        assertEquals("21st", Facts.ordinal("21"));
        assertEquals("12th", Facts.ordinal("12"));
        assertEquals("3rd", Facts.ordinal("3"));
    }
}
