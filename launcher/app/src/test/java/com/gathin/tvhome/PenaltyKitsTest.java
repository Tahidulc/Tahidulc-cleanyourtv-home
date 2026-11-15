package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.Penalty;
import com.gathin.broadcast.Worn;

import org.junit.Test;

/** The penalty card's pages on the TV's board, and the kit the line-up card draws for the kit ESPN names. */
public class PenaltyKitsTest {
    private static ScoresTicker.Game game() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "1";
        g.home = "WHI";
        g.away = "RIV";
        g.homeName = "Whitebrook";
        g.awayName = "Riverside";
        g.homeColor = 0xFF1B458F;
        g.awayColor = 0xFFC8102E;
        return g;
    }

    private static Penalty.Given given() {
        Penalty.Given p = new Penalty.Given();
        p.team = "Riverside";
        p.wonBy = "Sam Okafor";
        p.concededBy = "Theo Marsh";
        p.minute = "63'";
        return p;
    }

    @Test public void cardWithItsRecords() {
        Penalty.Records r = new Penalty.Records();
        r.taker = "Leo Brandt";
        r.takerLine = Penalty.takerLine(new int[]{4, 5, 0, 0}, null);
        r.keeper = "Jonas Vale";
        r.keeperLine = Penalty.keeperLine(new int[]{0, 0, 6, 1}, null);
        BoardDrop.Drop d = BoardCover.penaltyDrop(game(), given(), 2, r);
        assertEquals("PENALTY", d.chip);
        assertTrue(d.penalty);
        assertEquals(2, d.side);
        assertEquals(3, d.pages.length);
        assertEquals("Riverside", d.pages[0][0]);
        assertEquals("Won by Okafor · given away by Marsh", d.pages[0][1]);
        assertEquals("Brandt", d.pages[1][0]);
        assertEquals("Likely taker · Scored 4 of 5 this season", d.pages[1][1]);
        assertEquals("Vale", d.pages[2][0]);
        assertEquals("In goal · Saved 1 of 6 this season", d.pages[2][1]);
        assertTrue(d.what.startsWith("PENALTY Riverside"));
    }

    @Test public void cardWithoutRecordsIsOnePage() {
        BoardDrop.Drop d = BoardCover.penaltyDrop(game(), given(), 2, new Penalty.Records());
        assertNull(d.pages);
        assertEquals("Riverside", d.title);
        assertEquals("Won by Okafor · given away by Marsh", d.line);
        assertTrue(BoardDrop.pageCount(d) == 1);
    }

    private static Kits.Kit kit(int body, int sleeves) {
        Kits.Kit k = new Kits.Kit();
        k.body = body;
        k.sleeves = sleeves;
        return k;
    }

    @Test public void lineUpsDrawTheKitNamed() {
        // Wales this season: red home, white away, gold third (as an article would give them).
        Kits.Kit[] wales = {kit(0xFFD30A11, 0xFFD30A11), kit(0xFFFFFFFF, 0xFF00A4A6), kit(0xFFE2B13C, 0xFF14302A)};
        Kits.Kit[] norway = {kit(0xFFC8102E, 0xFFC8102E)};
        // ESPN names the third kit: the article's third, with its sleeves.
        assertSame(wales[2], Kits.worn(wales, norway, true, new Worn.Kit("third", 0xFFE9AF3A, 0xFF112117)));
        // A kit ESPN names that no article kit is near: ESPN's colour alone.
        Kits.Kit odd = Kits.worn(wales, norway, true, new Worn.Kit("fourth", 0xFF6A0DAD, 0));
        assertEquals(0xFF6A0DAD, odd.body);
        assertEquals(0xFF6A0DAD, odd.sleeves);
        // None named: the clash rule (red against red: the away kit).
        assertSame(wales[1], Kits.worn(wales, norway, true, null));
    }
}
