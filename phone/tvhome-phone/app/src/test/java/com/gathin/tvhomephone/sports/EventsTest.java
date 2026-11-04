package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class EventsTest {
    private static Game game(int state, String... goals) {
        Game g = new Game();
        g.id = "1";
        g.slug = "eng.1";
        g.homeId = "360";
        g.awayId = "382";
        g.homeName = "Man Utd";
        g.awayName = "Brighton";
        g.home = "MUN";
        g.away = "BHA";
        g.leagueName = "Premier League";
        g.state = state;
        g.detail = state == Game.LIVE ? "60'" : "";
        for (String x : goals) {
            String[] p = x.split(",");   // side,minute,player[,kind]
            g.goals.add(new String[]{"G|1|" + p[1] + "|" + p[2], p[2], p[1], p[0], p.length > 3 ? p[3] : ""});
            if ("home".equals(p[0])) g.homeScore++; else g.awayScore++;
        }
        return g;
    }

    private static List<String> kinds(List<Events.Alert> a) {
        List<String> out = new ArrayList<>();
        for (Events.Alert x : a) out.add(x.kind);
        return out;
    }

    @Test public void firstSightSendsNothing() {
        assertTrue(Events.diff(null, game(Game.LIVE, "home,10',B. Fernandes")).isEmpty());
    }

    @Test public void kickOffAndANewGoal() {
        assertEquals(List.of("kickoff"), kinds(Events.diff(game(Game.PRE), game(Game.LIVE))));
        List<Events.Alert> a = Events.diff(game(Game.LIVE), game(Game.LIVE, "home,23',B. Fernandes,pen"));
        assertEquals(List.of("goal"), kinds(a));
        assertTrue(a.get(0).title.contains("Man Utd 1–0 Brighton"));
        assertTrue(a.get(0).text.contains("(pen)"));
    }

    @Test public void theSameGoalIsNotSentTwice() {
        Game g = game(Game.LIVE, "home,23',B. Fernandes");
        assertTrue(Events.diff(g, game(Game.LIVE, "home,23',B. Fernandes")).isEmpty());
    }

    @Test public void lateEqualiserIsDrama() {
        Game before = game(Game.LIVE, "away,10',D. Welbeck");
        Game after = game(Game.LIVE, "away,10',D. Welbeck", "home,89',R. Højlund");
        List<String> k = kinds(Events.diff(before, after));
        assertTrue(k.contains("goal"));
        assertTrue(k.contains("drama"));
    }

    @Test public void earlyEqualiserIsNot() {
        Game before = game(Game.LIVE, "away,10',D. Welbeck");
        Game after = game(Game.LIVE, "away,10',D. Welbeck", "home,40',R. Højlund");
        assertEquals(List.of("goal"), kinds(Events.diff(before, after)));
    }

    @Test public void backFromTwoDown() {
        Game before = game(Game.LIVE, "away,5',A", "away,9',A", "home,50',B");
        Game after = game(Game.LIVE, "away,5',A", "away,9',A", "home,50',B", "home,70',C");
        List<Events.Alert> a = Events.diff(before, after);
        boolean comeback = false;
        for (Events.Alert x : a) if (x.title.startsWith("COMEBACK")) comeback = true;
        assertTrue(comeback);
    }

    @Test public void braceAndHatTrick() {
        Game one = game(Game.LIVE, "home,10',R. Højlund");
        Game two = game(Game.LIVE, "home,10',R. Højlund", "home,30',R. Højlund");
        Game three = game(Game.LIVE, "home,10',R. Højlund", "home,30',R. Højlund", "home,80',R. Højlund");
        assertTrue(kinds(Events.diff(one, two)).contains("milestone"));
        boolean hat = false;
        for (Events.Alert x : Events.diff(two, three)) if (x.title.startsWith("HAT-TRICK")) hat = true;
        assertTrue(hat);
    }

    @Test public void halfTimeAndFullTime() {
        Game live = game(Game.LIVE, "home,10',B");
        Game ht = game(Game.LIVE, "home,10',B");
        ht.halftime = true;
        assertEquals(List.of("ht"), kinds(Events.diff(live, ht)));
        assertEquals(List.of("ft"), kinds(Events.diff(live, game(Game.POST, "home,10',B"))));
    }

    @Test public void redCard() {
        Game after = game(Game.LIVE);
        after.reds.add(new String[]{"R|1|55'|L. Dunk", "L. Dunk", "55'", "away"});
        assertEquals(List.of("red"), kinds(Events.diff(game(Game.LIVE), after)));
    }

    @Test public void minutes() {
        assertEquals(89, Game.minute("89'"));
        assertEquals(93, Game.minute("90'+3'"));
        assertEquals(0, Game.minute(""));
    }
}
