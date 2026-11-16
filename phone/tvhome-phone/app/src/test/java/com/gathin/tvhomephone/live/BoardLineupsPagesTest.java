package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The TV's line-up card on a phone: fewer players a page, line by line, the keeper with his defence, the pitch last. */
public class BoardLineupsPagesTest {
    /** A side in its lines, numbered from 1 (the keeper) as given: {1}, {2, 3, 4, 5}... */
    private static BoardLineups.Team team(int... lines) {
        BoardLineups.Team t = new BoardLineups.Team();
        int n = 1;
        for (int size : lines) {
            List<BoardLineups.Player> line = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                BoardLineups.Player p = new BoardLineups.Player();
                p.number = String.valueOf(n++);
                p.last = "Player" + p.number;
                line.add(p);
            }
            t.rows.add(line);
        }
        return t;
    }

    private static BoardLineups.Data data(BoardLineups.Team home, BoardLineups.Team away) {
        BoardLineups.Data d = new BoardLineups.Data();
        d.home = home;
        d.away = away;
        return d;
    }

    @Test public void fiveAPageKeepsTheKeeperWithHisDefence() {
        BoardLineups.Data d = data(team(1, 4, 3, 3), team(1, 4, 4, 2));
        assertEquals(Arrays.asList(
                "1 2 3 4 5 | KEEPER · DEFENCE", "6 7 8 | MIDFIELD", "9 10 11 | ATTACK",
                "1 2 3 4 5 | KEEPER · DEFENCE", "6 7 8 9 | MIDFIELD", "10 11 | ATTACK",
                "PITCH"), BoardLineups.describe(d, 5));
    }

    @Test public void sixAPageJoinsTheMidfieldAndAttack() {
        BoardLineups.Data d = data(team(1, 4, 3, 3), team(1, 4, 2, 3, 1));
        assertEquals(Arrays.asList(
                "1 2 3 4 5 | KEEPER · DEFENCE", "6 7 8 9 10 11 | MIDFIELD · ATTACK",
                // Both lines of the midfield and the lone striker on one page.
                "1 2 3 4 5 | KEEPER · DEFENCE", "6 7 8 9 10 11 | MIDFIELD · ATTACK",
                "PITCH"), BoardLineups.describe(d, 6));
    }

    @Test public void aLineTooLongForAPageIsSplit() {
        BoardLineups.Data d = data(team(1, 3, 5, 2), team(1, 3, 5, 2));
        List<String> pages = BoardLineups.describe(d, 3);
        assertEquals("1 2 3 4 | KEEPER · DEFENCE", pages.get(0));
        assertEquals("5 6 7 | MIDFIELD", pages.get(1));
        assertEquals("8 9 | MIDFIELD", pages.get(2));
        assertEquals("10 11 | ATTACK", pages.get(3));
        assertEquals("PITCH", pages.get(pages.size() - 1));
    }

    @Test public void theTvsOwnPagesAreLeftAsTheyWere() {
        BoardLineups.Data d = data(team(1, 4, 3, 3), team(1, 4, 4, 2));
        assertEquals(Arrays.asList("1 2 3 4 5 | KEEPER · DEFENCE", "6 7 8 9 10 11 | MIDFIELD · ATTACK",
                "1 2 3 4 5 | KEEPER · DEFENCE", "6 7 8 9 10 11 | MIDFIELD · ATTACK", "PITCH"), BoardLineups.describe(d, 0));
    }

    @Test public void howManyFitAPage() {
        // A phone's pop-up (about 340 by 140 dp) has room for five; the TV's own shape for six.
        assertEquals(5, BoardLineups.perPageFor(1020, 425));
        assertEquals(6, BoardLineups.perPageFor(1180, 400));
        assertTrue(BoardLineups.perPageFor(300, 300) >= 3);
        assertTrue(BoardLineups.showFor(data(team(1, 4, 3, 3), team(1, 4, 4, 2)), 5, 2_800L) > 7 * 2_800L);
    }
}
