package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.AddedTime;
import com.gathin.broadcast.CompBug;

import org.junit.Test;

/**
 * Added time the same on every look of the TV's board (BoardBug's own, the Lab's five, By competition and each
 * competition's own, the four retro boards), through a made-up game's second half's end: one rule for them all
 * (AddedTime). Before the minutes are put up, nothing; once they are, the ADDED TIME pop-out ("+4 minutes"); while they
 * run, with "Added time stays out" on the board keeps no +N tab (the panel says them), with it off the +N tab stands at
 * its end, beside a competition look's main row; however narrow a look is kept the minutes are never lost.
 */
public class AddedTimeLooksTest {
    private static final int H = 60;

    @Test public void everyLookTakesTheSameAddedTime() {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "added-every-look";
        g.homeColor = 0x1B458F;
        g.awayColor = 0xC8102E;
        int[] put = {0, 4, 0, 0};
        for (BoardBug.Style st : BoardBug.Style.values()) {
            for (String slug : st == BoardBug.Style.COMPETITION ? new String[]{"eng.1", "fifa.friendly"} : new String[]{"eng.1"}) {
                String name = st.name() + " " + slug;
                CompBug.Kind k = BoardBug.kindOf(st, CompBug.forGame(slug, "eng.1".equals(slug) ? "Premier League" : "Friendly"));
                float rows = k != null ? k.tall : st.tall;
                // 88:40: nothing on the board yet; 89:10: the minutes put up, from the half's last minute on.
                assertEquals(name, "", BoardCover.addedWords("88:40", "89'", put));
                String added = BoardCover.addedWords("89:10", "90'", put);
                assertEquals(name, "+4", added);
                // Out of the board once as its pop-out, the same words for every look.
                BoardDrop.Drop d = BoardCover.addedDrop(g, added);
                assertNotNull(name, d);
                assertEquals("ADDED TIME", d.chip);
                assertEquals("+4 minutes", d.title);
                assertEquals(4, d.addedMins);
                // Added time stays out: the panel stands, the board keeps no +N tab of its own.
                assertEquals(name, "", BoardCover.boardAdded(added, true));
                // Off: the +N tab at the board's end; at a room for itself beside it, every look has it.
                assertEquals(name, "+4", BoardCover.boardAdded(added, false));
                int room = Math.round(H * 0.06f + H * 0.8f * 1.15f);
                float least = st == BoardBug.Style.CARD ? AddedTime.LEAST_CARD : AddedTime.LEAST;
                assertTrue(name, AddedTime.outside(AddedTime.drawsInside(k), Math.round(H * 4f), room, H, least));
                // Kept narrow: a look that cannot write them by its clock keeps the tab; the rest write them by its clock.
                boolean outside = AddedTime.outside(AddedTime.drawsInside(k), Math.round(H * 1.5f), room, H, least);
                assertTrue(name, outside || AddedTime.drawsInside(k));
                assertEquals(name, k != null, outside);
                // No minutes, no tab.
                assertFalse(name, AddedTime.outside(AddedTime.drawsInside(k), Math.round(H * 4f), 0, H, least));
                // The tab's band: inside the board, beside a competition look's main row, on a tall look's foot.
                float bh = H * rows;
                float[] band = AddedTime.band(k, AddedTime.tall(rows), bh);
                assertTrue(name, band[0] >= -0.01f && band[0] + band[1] <= bh + 0.01f && band[1] > 0);
                if (k != null) {
                    float[] row = CompBug.bar(k, bh);
                    assertTrue(name, band[0] >= row[0] - 0.01f && band[0] + band[1] <= row[1] + 0.01f);
                } else if (AddedTime.tall(rows)) {
                    assertEquals(name, bh, band[0] + band[1], 0.01f);
                }
                // Half time, full time, the next half: the panel goes in.
                assertTrue(BoardCover.addedStands(0, false, true, 2, 2));
                assertFalse(BoardCover.addedStands(0, true, false, 2, 2));
                assertFalse(BoardCover.addedStands(2, false, false, 2, 2));
                assertFalse(BoardCover.addedStands(0, false, true, 3, 2));
            }
        }
    }
}
