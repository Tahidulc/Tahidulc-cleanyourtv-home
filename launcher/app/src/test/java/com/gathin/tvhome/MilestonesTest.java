package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.json.JSONObject;
import org.junit.Test;

public class MilestonesTest {
    @Test
    public void ordinals() throws Exception {
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

    @Test
    public void aGoalsTag() throws Exception {
        assertEquals("3rd of the season", Milestones.goal(1, 3));
        assertEquals("1st of the season", Milestones.goal(1, 1));
        assertEquals("2nd of the game", Milestones.goal(2, 9));
        assertEquals("2nd of the game", Milestones.goal(2, 0));
        assertEquals("Hat-trick!", Milestones.goal(3, 12));
        assertEquals("4th of the game", Milestones.goal(4, 0));
        assertNull(Milestones.goal(1, 0));
    }

    @Test
    public void thePremierLeaguesBans() throws Exception {
        LocalDate nov = LocalDate.of(2026, 11, 12), feb = LocalDate.of(2027, 2, 1), may = LocalDate.of(2027, 5, 1);
        assertEquals("One more and he's banned", Milestones.yellow("eng.1", 4, nov));
        assertEquals("Banned for the next game", Milestones.yellow("eng.1", 5, nov));
        assertNull(Milestones.yellow("eng.1", 3, nov));
        // After the 19th round: ten.
        assertNull(Milestones.yellow("eng.1", 5, feb));
        assertEquals("One more and he's banned", Milestones.yellow("eng.1", 9, feb));
        assertEquals("Banned for the next game", Milestones.yellow("eng.1", 10, feb));
        // After the 32nd: fifteen.
        assertEquals("One more and he's banned", Milestones.yellow("eng.1", 14, may));
        assertEquals("Banned for the next game", Milestones.yellow("eng.1", 15, may));
        // Six in November: on to ten.
        assertEquals(10, Milestones.banAt("eng.1", 6, nov));
        // The phone's dates: ten still counts to 19 April, fifteen alone from the 20th.
        assertEquals("One more and he's banned", Milestones.yellow("eng.1", 9, LocalDate.of(2027, 4, 19)));
        assertNull(Milestones.yellow("eng.1", 9, LocalDate.of(2027, 4, 20)));
    }

    @Test
    public void uefasBans() throws Exception {
        LocalDate nov = LocalDate.of(2026, 11, 12);
        assertEquals("One more and he's banned", Milestones.yellow("uefa.champions", 2, nov));
        assertEquals("Banned for the next game", Milestones.yellow("uefa.champions", 3, nov));
        assertEquals("One more and he's banned", Milestones.yellow("uefa.europa", 4, nov));
        assertEquals("Banned for the next game", Milestones.yellow("uefa.europa.conf", 5, nov));
        assertEquals("Banned for the next game", Milestones.yellow("uefa.champions", 7, nov));
        // As the phone says it: the 3rd, then every second one (the 5th, the 7th), a warning on the 2nd, 4th and 6th.
        assertEquals("Banned for the next game", Milestones.yellow("uefa.champions", 3, LocalDate.of(2027, 5, 2)));
        assertEquals("One more and he's banned", Milestones.yellow("uefa.champions", 6, nov));
        assertNull(Milestones.yellow("uefa.champions", 1, nov));
        // A competition whose rule is not known: nothing.
        assertNull(Milestones.yellow("esp.1", 4, nov));
        assertNull(Milestones.yellow(null, 4, nov));
        assertNull(Milestones.yellow("eng.1", 0, nov));
    }

    @Test
    public void hisSeasonLessTonight() throws Exception {
        JSONObject o = new JSONObject("{\"statistics\":{\"labels\":[\"STRT\",\"FC\",\"FA\",\"YC\",\"RC\",\"G\",\"A\"],"
                + "\"splits\":[{\"displayName\":\"2026-27 Premier League\",\"stats\":[\"11\",\"9\",\"14\",\"4\",\"0\",\"7\",\"2\"]},"
                + "{\"displayName\":\"2026-27 Champions League\",\"stats\":[\"4\",\"2\",\"3\",\"1\",\"0\",\"3\",\"1\"]}]},"
                + "\"gameLog\":{\"statistics\":[{\"labels\":[\"APP\",\"G\",\"A\",\"SHOT\",\"SOG\",\"FC\",\"FA\",\"OFF\",\"YC\",\"RC\"],"
                + "\"events\":[{\"eventId\":\"740001\",\"stats\":[\"1\",\"2\",\"0\",\"4\",\"3\",\"1\",\"0\",\"0\",\"1\",\"0\"]}]}]}}");
        // Tonight's two goals and a yellow already in: taken out (they are counted from the game itself).
        int[] pl = Milestones.season(o, Milestones.names("eng.1", "English Premier League"), "740001");
        assertEquals(5, pl[0]);
        assertEquals(3, pl[1]);
        int[] other = Milestones.season(o, Milestones.names("eng.1", ""), "999");
        assertEquals(7, other[0]);
        assertEquals(4, other[1]);
        int[] ucl = Milestones.season(o, Milestones.names("uefa.champions", "UEFA Champions League"), "999");
        assertEquals(3, ucl[0]);
        int[] none = Milestones.season(o, Milestones.names("ger.1", "Bundesliga"), "999");
        assertEquals(-1, none[0]);
        assertEquals(-1, Milestones.season(new JSONObject("{}"), new String[]{"x"}, "1")[0]);
    }

    @Test
    public void hisGoalsInTheGameSoFar() throws Exception {
        String[] a = {"1|10|A", "A", "10'", "home", "", "11"}, og = {"1|20|B", "B", "20'", "home", "og", "22"},
                b = {"1|30|A", "A", "30'", "home", "pen", "11"}, c = {"1|40|C", "C", "40'", "away", "", "33"},
                d = {"1|50|A", "A", "50'", "home", "", "11"};
        List<String[]> goals = Arrays.asList(a, og, b, c, d);
        assertEquals(1, Milestones.inGame(goals, a));
        assertEquals(2, Milestones.inGame(goals, b));
        assertEquals(3, Milestones.inGame(goals, d));
        assertEquals(1, Milestones.inGame(goals, c));
        assertEquals(0, Milestones.inGame(goals, og));
        assertEquals(0, Milestones.inGame(goals, new String[]{"x", "Z", "", "home", "", ""}));
    }

    @Test
    public void aYellowCardsAlert() throws Exception {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "9";
        g.homeName = "Everton";
        g.awayName = "Spurs";
        BoardDrop.Drop d = BoardCover.yellowDrop(g, new String[]{"Y|9", "James Garner", "41'", "home", "", "5"}, "One more and he's banned");
        assertEquals("YELLOW CARD", d.chip);
        assertEquals("James Garner 41'", d.line);
        assertEquals("One more and he's banned", d.tag);
        assertEquals(BoardDrop.CARD, d.tagMark);
        assertEquals(1, d.side);
        assertEquals(Milestones.YELLOW, d.colour);
    }
}
