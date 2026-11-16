package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** The table's number columns: played, won, drawn, lost, goal difference and points, in that order (a made-up club). */
public class TableColumnsTest {
    @Test public void aRowsNumbersInTheirColumns() {
        String[] row = {"4", "901", "Herons", "https://example.invalid/901.png", "10", "+9", "20", "", "6", "2", "2"};
        assertArrayEquals(new String[]{"10", "6", "2", "2", "+9", "20"}, SportsScreen.numbersOf(row));
        assertArrayEquals(new String[]{"Pld", "W", "D", "L", "GD", "Pts"}, SportsScreen.NUMBER_HEADS);
    }

    @Test public void theClubsColumnTakesWhatTheNumbersLeave() {
        int[] cols = {28, 23, 23, 23, 33, 34};
        // Room for all six: the club's column takes the rest, nothing slid.
        assertArrayEquals(new int[]{163, 0}, SportsScreen.fit(333, 154, cols, 6));
        // Too narrow: played slides under the club's column whole, the club's column widening to the rest.
        assertArrayEquals(new int[]{158, 28}, SportsScreen.fit(300, 154, cols, 6));
        // Narrower still: played, won and drawn slide under it.
        assertArrayEquals(new int[]{174, 74}, SportsScreen.fit(270, 154, cols, 6));
    }

    @Test public void aRowWithoutWonDrawnLostLeavesThemBlank() {
        String[] row = {"4", "901", "Herons", "", "10", "+9", "20", ""};
        assertArrayEquals(new String[]{"10", "", "", "", "+9", "20"}, SportsScreen.numbersOf(row));
    }
}
