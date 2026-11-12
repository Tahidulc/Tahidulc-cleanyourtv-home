package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

public class FightNightTest {
    private static final long MIN = 60_000L;

    /** A card of four: two prelims at 23:00, two on the main card at 01:00 (the last the main event, five rounds). */
    private static JSONObject board(String[] states, int onRound, String clock) throws Exception {
        StringBuilder b = new StringBuilder("{\"events\":[{\"id\":\"600\",\"name\":\"UFC Fight Night: Archer vs. Ruiz\",\"competitions\":[");
        String[] dates = {"2026-11-14T23:00Z", "2026-11-14T23:00Z", "2026-11-15T01:00Z", "2026-11-15T01:00Z"};
        for (int i = 0; i < 4; i++) {
            if (i > 0) b.append(',');
            boolean main = i == 3;
            b.append("{\"id\":\"f").append(i).append("\",\"date\":\"").append(dates[i]).append("\",")
                    .append("\"type\":{\"abbreviation\":\"").append(main ? "Heavyweight" : "Lightweight").append("\"},")
                    .append("\"format\":{\"regulation\":{\"periods\":").append(main ? 5 : 3).append("}},")
                    .append("\"status\":{\"period\":").append("in".equals(states[i]) ? onRound : 0)
                    .append(",\"displayClock\":\"").append("in".equals(states[i]) ? clock : "0:00").append("\",")
                    .append("\"type\":{\"state\":\"").append(states[i]).append("\",\"shortDetail\":\"Final\"}},")
                    .append("\"competitors\":[")
                    .append("{\"id\":\"").append(100 + i).append("\",\"order\":2,\"winner\":false,\"records\":[{\"summary\":\"19-0-0\"}],\"athlete\":{\"displayName\":\"")
                    .append(main ? "Mateo Ruiz" : "Blue " + i).append("\"}},")
                    .append("{\"id\":\"").append(200 + i).append("\",\"order\":1,\"winner\":").append("post".equals(states[i]) && i == 2)
                    .append(",\"records\":[{\"summary\":\"24-1-0\"}],\"athlete\":{\"displayName\":\"")
                    .append(main ? "Kane Archer" : "Red " + i).append("\"}}]}");
        }
        return new JSONObject(b.append("]}]}").toString());
    }

    @Test
    public void theCardIsReadInOrderTheMainEventLast() throws Exception {
        List<FightNight.Event> all = FightNight.events(board(new String[]{"pre", "pre", "pre", "pre"}, 0, ""));
        assertEquals(1, all.size());
        FightNight.Event e = all.get(0);
        assertEquals(4, e.fights.size());
        FightNight.Fight m = e.main();
        assertEquals("Heavyweight", m.weight);
        assertEquals(5, m.rounds);
        // The red corner is the first in ESPN's order.
        assertEquals("Kane Archer", m.red.name);
        assertEquals("24-1-0", m.red.record);
        assertEquals("203", m.red.id);
        assertEquals("Mateo Ruiz", m.blue.name);
        assertEquals(FightNight.time("2026-11-15T01:00Z"), e.mainCardStart());
        assertEquals(java.time.Instant.parse("2026-11-15T01:00:00Z").toEpochMilli(), FightNight.time("2026-11-15T01:00Z"));
        assertEquals(0, FightNight.time("soon"));
    }

    @Test
    public void theRingWalkIsTheLaterOfTheCardsStartAndWhatIsLeft() throws Exception {
        FightNight.Event e = FightNight.events(board(new String[]{"pre", "pre", "pre", "pre"}, 0, "")).get(0);
        long start = e.mainCardStart();
        // Hours before: the main card's start and its one fight before the main event.
        assertEquals(start + 22 * MIN, FightNight.ringWalk(e, start - 5 * 60 * MIN));
        // At the main card's start with three fights still to come: now and theirs.
        assertEquals(start + 3 * 25 * MIN, FightNight.ringWalk(e, start));
        // The co-main in its second of three rounds: one round more and the break, the prelims done.
        FightNight.Event on = FightNight.events(board(new String[]{"post", "post", "in", "pre"}, 2, "3:10")).get(0);
        long now = start + 10 * MIN;
        assertEquals(Math.max(start + 22 * MIN, now + 6 * MIN + 5 * MIN), FightNight.ringWalk(on, now));
        // The main event on: no walk to come.
        FightNight.Event mainOn = FightNight.events(board(new String[]{"post", "post", "post", "in"}, 1, "4:00")).get(0);
        assertEquals(0, FightNight.ringWalk(mainOn, now));
    }

    @Test
    public void theFightOnNowOnTheMainCard() throws Exception {
        FightNight.Event on = FightNight.events(board(new String[]{"post", "post", "in", "pre"}, 2, "3:10")).get(0);
        FightNight.Fight f = FightNight.onNow(on);
        assertNotNull(f);
        assertSame(on.fights.get(2), f);
        assertEquals(2, f.round);
        assertNull(FightNight.onNow(FightNight.events(board(new String[]{"in", "pre", "pre", "pre"}, 1, "1:00")).get(0)));
    }

    @Test
    public void wordsForTheCards() throws Exception {
        assertEquals("in 1 h 05", FightNight.until(65 * MIN));
        assertEquals("in 12 min", FightNight.until(12 * MIN + 30_000L));
        assertEquals("now", FightNight.until(20_000L));
        // ESPN's clock counts up through the round ("2:27" in): the time left is the rest of its five minutes.
        assertEquals(147, FightNight.elapsed("2:27"));
        assertEquals(-1, FightNight.elapsed("-"));
        assertEquals(0.36f, FightNight.roundLeft(FightNight.elapsed("3:12")), 0.001f);
        assertEquals(1f, FightNight.roundLeft(0), 0.001f);
        assertEquals(-1f, FightNight.roundLeft(-1), 0.001f);
        assertEquals("1:48 left", FightNight.leftWords(192));
        assertEquals("0:00 left", FightNight.leftWords(320));
        // Counted on between ESPN's reads, never past the round's end.
        assertEquals(162, FightNight.elapsedNow(147, 1_000L, 16_000L));
        assertEquals(300, FightNight.elapsedNow(290, 0L, 60_000L));
        assertEquals(-1, FightNight.elapsedNow(-1, 0L, 60_000L));
        FightNight.Fight f = new FightNight.Fight();
        f.red.name = "Kane Archer";
        f.blue.name = "Mateo Ruiz";
        f.red.winner = true;
        f.result = "KO/TKO";
        f.round = 4;
        f.clock = "1:12";
        assertEquals("Archer wins · KO/TKO R4", FightNight.resultLine(f));
        // ESPN's status says only "Final": the round and the time it ended instead.
        f.result = "";
        f.red.winner = false;
        f.blue.winner = true;
        f.round = 3;
        f.clock = "2:27";
        assertEquals("Ruiz wins · R3 2:27", FightNight.resultLine(f));
        f.blue.winner = false;
        f.round = 0;
        assertEquals("Fight over", FightNight.resultLine(f));
    }

    @Test
    public void fightNightIsTheEventsNightAndTheNextOneIsChosen() throws Exception {
        FightNight.Event e = FightNight.events(board(new String[]{"pre", "pre", "pre", "pre"}, 0, "")).get(0);
        long start = e.mainCardStart();
        assertTrue(FightNight.near(e, start - 2 * 60 * MIN));
        assertTrue(!FightNight.near(e, start - 5 * 60 * MIN));
        assertTrue(!FightNight.near(e, start + 7 * 60 * MIN));
        assertSame(e, FightNight.next(java.util.Collections.singletonList(e), start - 24 * 60 * MIN));
        assertNull(FightNight.next(java.util.Collections.singletonList(e), start + 9 * 60 * MIN));
    }
}
