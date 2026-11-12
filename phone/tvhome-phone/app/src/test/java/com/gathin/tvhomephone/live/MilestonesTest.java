package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Game;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** The milestones on the scoreboard's pop-outs: a goal's place in the game and the season, a yellow card's ban. */
public class MilestonesTest {
    @Test public void ordinals() {
        assertEquals("1st", Milestones.ordinal(1));
        assertEquals("2nd", Milestones.ordinal(2));
        assertEquals("3rd", Milestones.ordinal(3));
        assertEquals("4th", Milestones.ordinal(4));
        assertEquals("11th", Milestones.ordinal(11));
        assertEquals("12th", Milestones.ordinal(12));
        assertEquals("13th", Milestones.ordinal(13));
        assertEquals("21st", Milestones.ordinal(21));
        assertEquals("22nd", Milestones.ordinal(22));
        assertEquals("111th", Milestones.ordinal(111));
    }

    @Test public void aGoalsTag() {
        assertEquals("3rd of the season", Milestones.goal(1, 3, false));
        assertEquals("1st of the season", Milestones.goal(1, 1, false));
        assertEquals("2nd of the game", Milestones.goal(2, 9, false));
        assertEquals("Hat-trick!", Milestones.goal(3, 12, false));
        assertEquals("4th of the game", Milestones.goal(4, 13, false));
        // Not known, or an own goal: none.
        assertEquals("", Milestones.goal(1, -1, false));
        assertEquals("", Milestones.goal(1, 3, true));
    }

    @Test public void tonightsGoalsCountedOnce() {
        assertEquals(3, Milestones.seasonWithTonight(2, false, 1));
        assertEquals(3, Milestones.seasonWithTonight(3, true, 1));
        assertEquals(-1, Milestones.seasonWithTonight(-1, false, 1));
    }

    @Test public void premierLeagueBansByMatchweek() {
        assertEquals(Milestones.ONE_MORE, Milestones.yellow("eng.1", 4, 12));
        assertEquals(Milestones.BANNED, Milestones.yellow("eng.1", 5, 19));
        // Past the 19th, five is no ban; ten by the 32nd.
        assertEquals(Milestones.NONE, Milestones.yellow("eng.1", 5, 20));
        assertEquals(Milestones.ONE_MORE, Milestones.yellow("eng.1", 9, 25));
        assertEquals(Milestones.BANNED, Milestones.yellow("eng.1", 10, 32));
        assertEquals(Milestones.NONE, Milestones.yellow("eng.1", 10, 33));
        assertEquals(Milestones.ONE_MORE, Milestones.yellow("eng.1", 14, 36));
        assertEquals(Milestones.BANNED, Milestones.yellow("eng.1", 15, 36));
        assertEquals(Milestones.NONE, Milestones.yellow("eng.1", 3, 10));
        assertEquals(Milestones.NONE, Milestones.yellow("eng.1", 0, 10));
    }

    @Test public void uefaBansAtTheThirdAndEverySecondAfter() {
        assertEquals(Milestones.NONE, Milestones.yellow("uefa.champions", 1, 0));
        assertEquals(Milestones.ONE_MORE, Milestones.yellow("uefa.champions", 2, 0));
        assertEquals(Milestones.BANNED, Milestones.yellow("uefa.champions", 3, 0));
        assertEquals(Milestones.ONE_MORE, Milestones.yellow("uefa.europa", 4, 0));
        assertEquals(Milestones.BANNED, Milestones.yellow("uefa.europa.conf", 5, 0));
        assertEquals(Milestones.BANNED, Milestones.yellow("uefa.champions", 7, 0));
    }

    @Test public void otherCompetitionsSayNothing() {
        assertFalse(Milestones.counts("eng.fa"));
        assertFalse(Milestones.counts("esp.1"));
        assertTrue(Milestones.counts("eng.1"));
        assertTrue(Milestones.counts("uefa.champions"));
        assertEquals(Milestones.NONE, Milestones.yellow("eng.fa", 5, 10));
        assertEquals(Milestones.NONE, Milestones.yellow("esp.1", 5, 10));
    }

    @Test public void theYellowCardsWords() {
        assertEquals("One more and he's banned", Milestones.yellowWords(Milestones.ONE_MORE));
        assertEquals("Banned for the next game", Milestones.yellowWords(Milestones.BANNED));
        assertEquals("", Milestones.yellowWords(Milestones.NONE));
    }

    @Test public void theMatchweek() {
        Calendar nov = Calendar.getInstance();
        nov.set(2026, Calendar.NOVEMBER, 12);
        Calendar may = Calendar.getInstance();
        may.set(2027, Calendar.MAY, 10);
        Calendar feb = Calendar.getInstance();
        feb.set(2027, Calendar.FEBRUARY, 10);
        assertEquals(12, Milestones.matchweek(11, nov));
        assertTrue(Milestones.matchweek(0, nov) <= 19);
        assertTrue(Milestones.matchweek(0, feb) > 19 && Milestones.matchweek(0, feb) <= 32);
        assertTrue(Milestones.matchweek(0, may) > 32);
    }

    private static final String OVERVIEW = "{\"statistics\":{\"labels\":[\"STRT\",\"FC\",\"FA\",\"YC\",\"RC\",\"G\",\"A\",\"SHOT\",\"SOG\",\"OFF\"],"
            + "\"splits\":[{\"displayName\":\"2026-27 English Carabao Cup\",\"stats\":[\"1\",\"0\",\"0\",\"0\",\"0\",\"5\",\"0\",\"3\",\"2\",\"0\"]},"
            + "{\"displayName\":\"2026-27 English Premier League\",\"stats\":[\"10\",\"8\",\"12\",\"4\",\"0\",\"2\",\"1\",\"20\",\"9\",\"3\"]}]},"
            + "\"gameLog\":{\"events\":{\"401100\":{\"id\":\"401100\"}}}}";

    @Test public void theSeasonInThisCompetition() throws Exception {
        JSONObject o = new JSONObject(OVERVIEW);
        int[] pl = Milestones.season(o, "999", "Premier League");
        assertArrayEquals(new int[]{2, 4, 0}, pl);
        // Tonight already in the game log: counted.
        assertArrayEquals(new int[]{2, 4, 1}, Milestones.season(o, "401100", "Premier League"));
        // The second name when the first has none; none at all for a competition he hasn't played.
        assertArrayEquals(new int[]{2, 4, 0}, Milestones.season(o, "999", "Nowhere Cup", "English Premier League"));
        assertNull(Milestones.season(o, "999", "Champions League"));
        assertNull(Milestones.season(new JSONObject("{}"), "999", "Premier League"));
        assertNull(Milestones.season(null, "999", "Premier League"));
    }

    @Test public void goalsInTheGameUpToThisOne() {
        List<String[]> goals = new ArrayList<>();
        goals.add(new String[]{"g1", "A. Player", "10'", "home", ""});
        goals.add(new String[]{"g2", "B. Other", "20'", "away", ""});
        goals.add(new String[]{"g3", "A. Player", "30'", "home", "og"});
        goals.add(new String[]{"g4", "A. Player", "40'", "home", "pen"});
        goals.add(new String[]{"g5", "A. Player", "50'", "home", ""});
        assertEquals(1, Milestones.inGame(goals, "g1", "A. Player"));
        assertEquals(2, Milestones.inGame(goals, "g4", "A. Player"));
        assertEquals(3, Milestones.inGame(goals, "g5", "A. Player"));
        assertEquals(1, Milestones.inGame(goals, "g2", "B. Other"));
    }

    @Test public void theScorersIdFromTheLineUps() {
        List<String[]> lineup = new ArrayList<>();
        lineup.add(new String[]{"9", "Beto", "F", "1", "11", "Beto", "", "", "1", "", "", "335"});
        lineup.add(new String[]{"10", "Iliman Ndiaye", "F", "1", "10", "Ndiaye", "", "", "0", "", "", "500"});
        lineup.add(new String[]{"5", "Jake O'Brien", "D", "1", "5", "O'Brien", "", "", "0", "", "", "600"});
        lineup.add(new String[]{"6", "James Tarkowski", "D", "1", "6", "Tarkowski", "", "", "0", "", "", "700"});
        lineup.add(new String[]{"16", "James Garner", "M", "1", "8", "Garner", "", "", "0", "", "", "800"});
        assertEquals("500", Milestones.athleteId(lineup, "Iliman Ndiaye", 1, 5, 11));
        assertEquals("335", Milestones.athleteId(lineup, "Beto", 1, 5, 11));
        assertEquals("700", Milestones.athleteId(lineup, "J. Tarkowski", 1, 5, 11));
        assertNull(Milestones.athleteId(lineup, "Somebody Else", 1, 5, 11));
        assertNull(Milestones.athleteId(null, "Beto", 1, 5, 11));
    }

    @Test public void aCardsMinuteAsTheClockPassesIt() {
        assertEquals(34, ScoreBoard.cardMinute("34'"));
        assertEquals(47, ScoreBoard.cardMinute("45'+2'"));
        assertEquals(0, ScoreBoard.cardMinute(""));
        assertEquals(0, ScoreBoard.cardMinute(null));
    }

    @Test public void theYellowCardsPopOut() {
        Game g = new Game();
        g.homeName = "Everton";
        g.awayName = "Arsenal";
        ScoreBoard.Drop d = ScoreBoard.yellowDrop(g, new String[]{"71'", "Yellow Card", "B. Player", "away", "E|1|9", ""}, Milestones.ONE_MORE);
        assertEquals("YELLOW CARD", d.chip);
        assertEquals("B. Player 71'", d.line);
        assertEquals("One more and he's banned", d.tag);
        assertEquals(BoardDrop.TAG_WARN, d.tagKind);
        assertEquals(2, d.side);
        assertTrue(d.own);
        ScoreBoard.Drop ban = ScoreBoard.yellowDrop(g, new String[]{"12'", "Yellow Card", "C. Player", "home", "E|1|3", ""}, Milestones.BANNED);
        assertEquals("Banned for the next game", ban.tag);
        assertEquals(BoardDrop.TAG_BAN, ban.tagKind);
        assertEquals(1, ban.side);
    }
}
