package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.AsSeen;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * No spoilers on Homefield TV's board: the minutes added and what the summary brings (a card, a substitution, VAR) wait
 * for the picture: the channel's delay since the scores had them, and the board's clock past their minute; with no delay
 * set for the channel and its clock not set at kick-off, the longest a channel runs behind. A made-up game.
 */
public class BoardHoldsTest {
    private static Game live(int period, String detail) {
        Game g = new Game();
        g.id = "holds-1";
        g.state = Game.LIVE;
        g.period = period;
        g.detail = detail;
        return g;
    }

    /** The picture at now: the board's clock at secs in the second half, its delay known (set) or not. */
    private static AsSeen.Picture at(long now, int secs, boolean known, long delayMs) {
        BoardClock.Reading r = new BoardClock.Reading(0, 0, 0, Game.LIVE, false, secs, (secs / 60 + 1) + "'");
        String clock = String.format(java.util.Locale.UK, "%02d:%02d", secs / 60, secs % 60);
        return BoardLayer.picture(r, clock, now, known, delayMs);
    }

    @Test
    public void theMinutesAddedWaitForThePicture() {
        AsSeen.Reads<int[]> added = new AsSeen.Reads<>();
        long read = 1_000_000L;
        Game g = live(2, "90'");
        added.add(new AsSeen.Stamp(read, BoardLayer.phaseOf(g), BoardLayer.periodOf(g), -1, -1, -1), new int[]{0, 4, 0, 0});
        // The channel's delay set (45 s): not until the picture has reached the read, then +4 beside the clock.
        assertNull(added.pick(at(read + 40_000L, 89 * 60 + 10, true, 45_000L)));
        assertArrayEquals(new int[]{0, 4, 0, 0}, added.pick(at(read + 60_000L, 89 * 60 + 30, true, 45_000L)));
        assertEquals("+4", BoardClock.addedWords("89:30", "90'", added.pick(at(read + 60_000L, 89 * 60 + 30, true, 45_000L))));
        // No delay set, the clock not set at kick-off: the longest a channel runs behind, never at once.
        assertNull(added.pick(at(read + 1_000L, 89 * 60, false, 60_000L)));
        assertNull(added.pick(at(read + 150_000L, 91 * 60, false, 60_000L)));
        assertArrayEquals(new int[]{0, 4, 0, 0}, added.pick(at(read + AsSeen.SAFE_MS, 92 * 60, false, 60_000L)));
    }

    @Test
    public void aCardWaitsForTheDelayAndItsMinute() {
        AsSeen.Held held = new AsSeen.Held();
        List<String> out = new ArrayList<>();
        long read = 2_000_000L;
        // A yellow card at 71' (the clock's 70:00 to 70:59), read 20 s after it.
        held.add("holds-1", BoardLayer.stamp(live(2, "71'"), read, AsSeen.minuteAt("71'")), () -> out.add("YELLOW CARD"));
        // The delay passed but the board's clock still in its minute: held.
        held.release("holds-1", at(read + 60_000L, 70 * 60 + 50, true, 45_000L));
        assertTrue(out.isEmpty());
        // A minute past its minute on the board's clock (and the delay long past): out.
        held.release("holds-1", at(read + 100_000L, 72 * 60, true, 45_000L));
        assertEquals(1, out.size());
        // No delay set: the longest lag, whatever the board's clock says.
        held.add("holds-1", BoardLayer.stamp(live(2, "73'"), read + 200_000L, AsSeen.minuteAt("73'")), () -> out.add("SUB"));
        held.release("holds-1", at(read + 300_000L, 80 * 60, false, 60_000L));
        assertEquals(1, out.size());
        held.release("holds-1", at(read + 200_000L + AsSeen.SAFE_MS, 81 * 60, false, 60_000L));
        assertEquals(2, out.size());
        // A VAR check with no minute: the delay alone.
        held.add("holds-1", BoardLayer.stamp(live(2, "82'"), read + 400_000L, -1), () -> out.add("VAR"));
        held.release("holds-1", at(read + 430_000L, 83 * 60, true, 45_000L));
        assertEquals(2, out.size());
        held.release("holds-1", at(read + 460_000L, 83 * 60, true, 45_000L));
        assertEquals(3, out.size());
    }

    @Test
    public void aFirstHalfAlertNotYetOutWhenTheSecondHalfIsOnIsLetGo() {
        AsSeen.Held held = new AsSeen.Held();
        List<String> out = new ArrayList<>();
        held.add("holds-1", BoardLayer.stamp(live(1, "44'"), 0, AsSeen.minuteAt("44'")), () -> out.add("late"));
        held.release("holds-1", at(30_000L, 46 * 60, false, 0));
        assertTrue(out.isEmpty());
        assertEquals(0, held.size());
    }

    @Test
    public void whereThePictureIs() {
        AsSeen.Picture p = at(0, 70 * 60 + 5, true, 45_000L);
        assertEquals(AsSeen.PLAY, p.state);
        assertEquals(2, p.period);
        assertEquals(70 * 60 + 5, p.secs);
        BoardClock.Reading ht = new BoardClock.Reading(0, 1, 0, Game.LIVE, true, 2700, "HT");
        assertEquals(AsSeen.BREAK, BoardLayer.picture(ht, "HT", 0, false, 0).state);
        assertEquals(-1, BoardLayer.picture(ht, "HT", 0, false, 0).secs);
        BoardClock.Reading ft = new BoardClock.Reading(0, 2, 1, Game.POST, false, 5400, "FT");
        assertEquals(AsSeen.OVER, BoardLayer.picture(ft, "FT", 0, false, 0).state);
        assertEquals(AsSeen.BEFORE, BoardLayer.picture(null, "", 0, false, 0).state);
        Game g = live(0, "45'+2'");
        assertEquals(1, BoardLayer.periodOf(g));
        g.halftime = true;
        assertEquals(AsSeen.BREAK, BoardLayer.phaseOf(g));
        assertFalse(BoardLayer.phaseOf(live(2, "60'")) == AsSeen.BREAK);
    }
}
