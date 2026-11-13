package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Tennis, boxing and UFC for the scoreboard: what a programme is, ESPN's feeds read, the one on the channel picked. */
public class SportFeedsTest {

    @Test
    public void aProgrammeIsToldByItsTitle() {
        assertEquals(SportBoard.TENNIS, SportFeeds.sportOf("Live ATP Finals Tennis"));
        assertEquals(SportBoard.TENNIS, SportFeeds.sportOf("Live Wimbledon"));
        assertEquals(SportBoard.TENNIS, SportFeeds.sportOf("Live US Open Tennis: Women's Final"));
        assertEquals(SportBoard.TENNIS, SportFeeds.sportOf("Live WTA Tennis"));
        assertEquals(SportBoard.UFC, SportFeeds.sportOf("Live UFC 334: Gane v Hokit"));
        assertEquals(SportBoard.BOXING, SportFeeds.sportOf("Live Boxing: Joshua v Dubois"));
        assertEquals(SportBoard.BOXING, SportFeeds.sportOf("Live Fight Night: Wardley v Clarke"));
        // Not live events: a golf major, highlights, a weigh-in; anything else.
        assertEquals(0, SportFeeds.sportOf("Live US Open Golf"));
        assertEquals(0, SportFeeds.sportOf("Boxing Highlights"));
        assertEquals(0, SportFeeds.sportOf("UFC 334 Weigh-In"));
        assertEquals(0, SportFeeds.sportOf("Live PL: Spurs v C. Palace"));
        assertEquals(0, SportFeeds.sportOf(null));
    }

    @Test
    public void theTwoNamesATitleSetsAgainstEachOther() {
        assertArrayEquals(new String[]{"Joshua", "Dubois"}, SportFeeds.versus("Live Boxing: Joshua v Dubois"));
        assertArrayEquals(new String[]{"Gane", "Hokit"}, SportFeeds.versus("UFC 334: Gane vs. Hokit"));
        assertArrayEquals(new String[]{"Wardley", "Clarke"}, SportFeeds.versus("Live Fight Night: Wardley v Clarke - Heavyweight"));
        assertArrayEquals(new String[]{"Moses Itauma", "Dillian Whyte"}, SportFeeds.versus("Boxing: Moses Itauma vs Dillian Whyte"));
        assertNull(SportFeeds.versus("Live ATP Finals Tennis"));
        assertEquals("Sabalenka", SportFeeds.surname("A. Sabalenka", "Aryna Sabalenka"));
        assertEquals("Lozano Avellaneda", SportFeeds.surname("S. Lozano Avellaneda", "Sara Alejandra Lozano Avellaneda"));
        assertEquals("Alatengheili", SportFeeds.surname("Alatengheili", "Alatengheili"));
        assertEquals("Dubois", SportFeeds.surname("", "Daniel Dubois"));
    }

    private static final String TENNIS = "{\"events\":[{\"name\":\"FAGE WTA Finals Indian Wells\",\"major\":false,\"groupings\":["
            + "{\"grouping\":{\"displayName\":\"Women's Singles\"},\"competitions\":["
            + "{\"id\":\"187379\",\"startDate\":\"2026-11-08T21:00Z\",\"round\":{\"displayName\":\"Group Stage\"},"
            + "\"format\":{\"regulation\":{\"periods\":3}},\"venue\":{\"fullName\":\"Indian Wells, USA\"},"
            + "\"status\":{\"period\":2,\"type\":{\"state\":\"in\",\"description\":\"In Progress\"}},\"competitors\":["
            + "{\"order\":2,\"curatedRank\":{\"current\":6},\"linescores\":[{\"value\":4.0},{\"value\":2.0}],"
            + "\"athlete\":{\"displayName\":\"Elina Svitolina\",\"shortName\":\"E. Svitolina\",\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/ukr.png\"}}},"
            + "{\"order\":1,\"curatedRank\":{\"current\":2},\"linescores\":[{\"value\":6.0},{\"value\":3.0}],"
            + "\"athlete\":{\"displayName\":\"Aryna Sabalenka\",\"shortName\":\"A. Sabalenka\",\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/blr.png\"}}}]},"
            + "{\"id\":\"187380\",\"round\":{\"displayName\":\"Group Stage\"},"
            + "\"status\":{\"period\":3,\"type\":{\"state\":\"post\",\"description\":\"Final\"}},\"competitors\":["
            + "{\"order\":1,\"winner\":true,\"curatedRank\":{\"current\":1},\"linescores\":[{\"value\":7.0,\"tiebreak\":7},{\"value\":4.0},{\"value\":6.0}],"
            + "\"athlete\":{\"displayName\":\"Iga Swiatek\",\"shortName\":\"I. Swiatek\",\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/pol.png\"}}},"
            + "{\"order\":2,\"winner\":false,\"curatedRank\":{\"current\":3},\"linescores\":[{\"value\":6.0,\"tiebreak\":5},{\"value\":6.0},{\"value\":2.0}],"
            + "\"athlete\":{\"displayName\":\"Coco Gauff\",\"shortName\":\"C. Gauff\",\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/usa.png\"}}}]}]},"
            + "{\"grouping\":{\"displayName\":\"Women's Doubles\"},\"competitions\":[{\"id\":\"9\",\"competitors\":[]}]}]}]}";

    @Test
    public void tennisIsReadSetBySet() {
        List<SportBoard> all = SportFeeds.tennis(TENNIS, "WTA");
        assertEquals(2, all.size());
        SportBoard m = all.get(0);
        assertEquals(SportBoard.TENNIS, m.sport);
        assertTrue(m.live());
        assertEquals("WTA Finals", m.event);
        assertEquals("Group Stage", m.stage);
        assertEquals("Women's Singles", m.division);
        // The first listed is ESPN's order 1.
        assertEquals("Aryna Sabalenka", m.a.name);
        assertEquals("Sabalenka", m.a.shortName);
        assertEquals("BLR", m.a.country);
        assertEquals("2", m.a.seed);
        assertArrayEquals(new int[]{6, 3}, m.a.games);
        assertArrayEquals(new int[]{4, 2}, m.b.games);
        assertArrayEquals(new int[]{1, 0}, m.sets());
        assertEquals(2, m.setColumns());
        assertEquals("3", SportBoard.gamesIn(m.a, 1));
        assertEquals("", SportBoard.gamesIn(m.a, 2));
        assertFalse(m.tiebreakNow());
        // ESPN has no points and no server.
        assertEquals("", m.a.points);
        assertFalse(m.a.serving);
        // The finished one: a lost tiebreak kept beside its games, the sets two to one.
        SportBoard f = all.get(1);
        assertTrue(f.over());
        assertTrue(f.a.winner);
        assertEquals(5, f.b.tiebreak[0]);
        assertEquals(-1, f.b.tiebreak[1]);
        assertArrayEquals(new int[]{2, 1}, f.sets());
    }

    private static final String UFC = "{\"events\":[{\"name\":\"UFC 334: Gane vs. Hokit\",\"shortName\":\"UFC 334\",\"competitions\":["
            + "{\"id\":\"1\",\"type\":{\"abbreviation\":\"Lightweight\"},\"format\":{\"regulation\":{\"periods\":3}},"
            + "\"status\":{\"period\":1,\"displayClock\":\"3:54\",\"type\":{\"state\":\"post\",\"detail\":\"Final\"},\"result\":{\"displayName\":\"KO/TKO\"}},"
            + "\"competitors\":[{\"order\":1,\"winner\":true,\"id\":\"11\",\"records\":[{\"name\":\"overall\",\"summary\":\"12-2-0\"}],"
            + "\"athlete\":{\"displayName\":\"Kai Holland\",\"shortName\":\"K. Holland\",\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/usa.png\"}}},"
            + "{\"order\":2,\"winner\":false,\"id\":\"12\",\"athlete\":{\"displayName\":\"Dan Dober\",\"shortName\":\"D. Dober\"}}]},"
            + "{\"id\":\"2\",\"type\":{\"abbreviation\":\"Heavyweight\"},\"format\":{\"regulation\":{\"periods\":5}},"
            + "\"status\":{\"period\":2,\"displayClock\":\"1:20\",\"type\":{\"state\":\"in\",\"detail\":\"Round 2\"}},"
            + "\"competitors\":[{\"order\":2,\"id\":\"22\",\"records\":[{\"name\":\"overall\",\"summary\":\"10-0-0\"}],"
            + "\"athlete\":{\"displayName\":\"Josh Hokit\",\"shortName\":\"J. Hokit\",\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/usa.png\"}}},"
            + "{\"order\":1,\"id\":\"21\",\"records\":[{\"name\":\"overall\",\"summary\":\"14-2-0\"}],"
            + "\"athlete\":{\"displayName\":\"Ciryl Gane\",\"shortName\":\"C. Gane\",\"flag\":{\"href\":\"https://a.espncdn.com/i/teamlogos/countries/500/fra.png\"}}}]}]}]}";

    @Test
    public void ufcIsReadFightByFight() {
        List<SportBoard> all = SportFeeds.ufc(UFC);
        assertEquals(2, all.size());
        SportBoard done = all.get(0), main = all.get(1);
        assertEquals("UFC 334", main.event);
        assertTrue(main.mainEvent);
        assertFalse(done.mainEvent);
        // Red corner first (ESPN's order 1), its colour the corner's.
        assertEquals("Gane", main.a.shortName);
        assertEquals(SportBoard.RED_CORNER, main.a.colour);
        assertEquals("Hokit", main.b.shortName);
        assertEquals("14-2-0", main.a.record);
        assertEquals("FRA", main.a.country);
        assertEquals("https://a.espncdn.com/i/headshots/mma/players/full/21.png", main.a.photoUrl);
        assertEquals(5, main.rounds);
        assertEquals(2, main.round);
        // ESPN's clock counts up: 1:20 into round two is 3:40 left.
        assertEquals(80, main.secsIn);
        assertEquals("3:40", main.clockLeft());
        assertEquals("R2", SportBug.midWords(main));
        assertEquals("3:40", SportBug.clockWords(main));
        // Counted on between reads, never past the round's end.
        assertEquals("3:30", main.at(1_000L, 11_000L).clockLeft());
        assertEquals("0:00", main.at(1_000L, 1_000_000L).clockLeft());
        // The finished one: how and when.
        assertTrue(done.over());
        assertTrue(done.a.winner);
        assertEquals("KO/TKO", SportBug.midWords(done));
        assertEquals("R1 3:54", SportBug.clockWords(done));
        assertEquals("UFC 334 · LIGHTWEIGHT", SportBug.tabWords(done));
        assertEquals("UFC 334 · HEAVYWEIGHT · MAIN EVENT · ROUND 2 OF 5", SportBug.tabWords(main));
    }

    @Test
    public void theOneOnTheChannelIsPicked() {
        List<SportBoard> fights = SportFeeds.ufc(UFC);
        // The fight on now.
        assertSame(fights.get(1), SportFeeds.pick(fights, "Live UFC 334", "", 0L));
        // None on: the next on the card after the last over.
        fights.get(1).state = "pre";
        assertSame(fights.get(1), SportFeeds.pick(fights, "Live UFC 334", "", 0L));
        // Named in the title: that one.
        assertSame(fights.get(0), SportFeeds.pick(fights, "Live UFC: Holland v Dober", "", 0L));
        // Tennis: the live match of the title's tour, the best-ranked players first; by names when the screen shows them.
        List<SportBoard> matches = SportFeeds.tennis(TENNIS, "WTA");
        assertSame(matches.get(0), SportFeeds.pick(matches, "Live WTA Finals Tennis", "", 0L));
        // A tour with nothing live: none (never the other tour's).
        assertNull(SportFeeds.pick(matches, "Live ATP Tennis", "", 0L));
        assertSame(matches.get(1), SportFeeds.pick(matches, "Live WTA Finals", "SWIATEK 7 4 6 GAUFF 6 6 2", 0L));
    }

    @Test
    public void aBoxingBoardComesFromItsTitle() {
        SportBoard b = SportFeeds.boxing("Live Boxing: Joshua v Dubois - Heavyweight", 1_000L);
        assertEquals(SportBoard.BOXING, b.sport);
        assertEquals("Joshua", b.a.shortName);
        assertEquals("Dubois", b.b.shortName);
        assertEquals("Heavyweight", b.stage);
        assertEquals(12, b.rounds);
        assertEquals(180, b.roundSecs);
        assertEquals(SportBoard.RED_CORNER, b.a.colour);
        assertEquals("BOXING · HEAVYWEIGHT · 12 ROUNDS", SportBug.tabWords(b));
    }

    @Test
    public void theBoxingClockRunsFromTheFirstBell() {
        long bell = 1_000_000L;
        assertEquals(0, SportBoard.bell(bell, bell - 5_000L, 180, 60, 12)[0]);
        assertArrayEquals(new int[]{1, 10, 0, 0}, SportBoard.bell(bell, bell + 10_000L, 180, 60, 12));
        // The minute between rounds.
        assertArrayEquals(new int[]{1, 5, 1, 0}, SportBoard.bell(bell, bell + 185_000L, 180, 60, 12));
        assertArrayEquals(new int[]{2, 5, 0, 0}, SportBoard.bell(bell, bell + 245_000L, 180, 60, 12));
        // The last round's bell: over.
        assertEquals(1, SportBoard.bell(bell, bell + 12 * 240_000L, 180, 60, 12)[3]);
        assertEquals(1, SportBoard.bell(bell, bell + 11 * 240_000L + 185_000L, 180, 60, 12)[3]);
    }

    @Test
    public void howAFightEndedInAFewWords() {
        assertEquals("KO/TKO", SportBoard.methodShort("KO/TKO"));
        assertEquals("SUB", SportBoard.methodShort("Submission"));
        assertEquals("DEC", SportBoard.methodShort("Decision - Unanimous"));
        assertEquals("SPLIT DEC", SportBoard.methodShort("Decision - Split"));
        assertEquals("", SportBoard.methodShort("Final"));
        assertEquals("FINAL", SportBug.midWords(fightOver("Final")));
        assertEquals(Arrays.asList("CRO", "GBR"), Arrays.asList(SportFeeds.countryOf("https://a.espncdn.com/i/teamlogos/countries/500/cro.png"),
                SportFeeds.countryOf("https://x/countries/500/gbr.png")));
    }

    @Test
    public void aMomentIsASetTheMatchARoundOrTheWinner() {
        SportBoard was = SportFeeds.tennis(TENNIS, "WTA").get(0);
        was.a.games = new int[]{5};
        was.b.games = new int[]{3};
        SportBoard now = was.copy();
        now.a.games = new int[]{6, 0};
        now.b.games = new int[]{3, 0};
        assertEquals("SET SABALENKA", SportBoard.moment(was, now).words);
        assertEquals(now.a.colour, SportBoard.moment(was, now).colour);
        assertNull(SportBoard.moment(now, now.copy()));
        SportBoard end = now.copy();
        end.state = "post";
        end.a.winner = true;
        assertEquals("GAME, SET AND MATCH · SABALENKA", SportBoard.moment(now, end).words);
        List<SportBoard> fights = SportFeeds.ufc(UFC);
        SportBoard r2 = fights.get(1), r3 = r2.copy();
        r3.round = 3;
        assertEquals("ROUND 3", SportBoard.moment(r2, r3).words);
        SportBoard over = r3.copy();
        over.state = "post";
        over.method = "KO/TKO";
        over.a.winner = true;
        assertEquals("GANE WINS · KO/TKO", SportBoard.moment(r3, over).words);
        // Another match: nothing.
        assertNull(SportBoard.moment(fights.get(0), r3));
    }

    private static SportBoard fightOver(String method) {
        SportBoard b = new SportBoard();
        b.sport = SportBoard.UFC;
        b.state = "post";
        b.method = method;
        return b;
    }
}
