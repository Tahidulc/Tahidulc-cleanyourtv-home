package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.AsSeen;
import com.gathin.broadcast.VarCheck;
import com.gathin.homefieldtv.sports.Game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Checking VAR" beside Homefield TV's board ends as the launcher's and the phone's do (VarCheck, BoardVar): replayed
 * through a made-up game's commentary (Northfield v Riverside) read every 45 seconds, held until the channel's picture (a
 * minute behind, lined up) reaches each read; a quick check with no decision comes off as the picture reaches the read
 * that has play going on, not three minutes later.
 */
public class BoardVarTest {
    private static final long T = 1_800_000_000_000L;
    private static final String ID = "770002";

    private static Game game() {
        Game g = new Game();
        g.id = ID;
        g.state = Game.LIVE;
        g.period = 2;
        g.detail = "63'";
        return g;
    }

    private static String[] line(String text, String minute, int seq) {
        return new String[]{text, minute, String.valueOf(seq)};
    }

    /** The picture at now: in play in the second half, the channel's delay set (a minute). */
    private static AsSeen.Picture picture(long now) {
        AsSeen.Picture p = new AsSeen.Picture();
        p.now = now;
        p.state = AsSeen.PLAY;
        p.period = 2;
        p.secs = 3780 + (int) ((now - T) / 1000);
        p.known = true;
        p.lagMs = 60_000L;
        return p;
    }

    @Test public void aQuickCheckWithNoDecisionComesOffWhenPlayGoesOn() {
        Game g = game();
        AsSeen.Held held = new AsSeen.Held();
        BoardVar check = new BoardVar();
        long[] outAt = {0}, offAt = {0};
        // Read 1 (T): the check under way, the latest line.
        List<String[]> first = new ArrayList<>(Arrays.asList(
                line("Foul by Ayo Bennett (Riverside).", "62'", 101),
                line("VAR checking for a possible penalty for Northfield.", "63'", 102)));
        List<VarExplained.Seen> seen = VarExplained.scan(first);
        assertEquals(1, seen.size());
        assertNotNull(seen.get(0).about);
        assertFalse(VarCheck.movedOn(first));
        held.add(ID, BoardLayer.stamp(g, T, -1), () -> check.out(ID, outAt[0] = clock[0]));
        // Read 2 (T + 45 s): play has gone on (a corner), no decision anywhere.
        List<String[]> second = new ArrayList<>(first);
        second.add(line("Corner, Northfield. Conceded by Sam Okafor.", "64'", 103));
        assertTrue(VarCheck.movedOn(second));
        assertEquals(1, VarExplained.scan(second).size());
        held.add(ID, BoardLayer.stamp(g, T + 45_000L, -1), () -> check.movedOn(ID));
        for (long now = T; now <= T + 300_000L; now += 1000L) {
            clock[0] = now;
            held.release(ID, picture(now));
            if (check.over(now, false)) {
                offAt[0] = now;
                check.off();
                break;
            }
        }
        // Out as the picture reaches the check (its delay and the margin: 70 s), off as it reaches the corner's read.
        assertEquals(T + 70_000L, outAt[0]);
        assertEquals(T + 115_000L, offAt[0]);
        assertTrue(offAt[0] - outAt[0] < VarExplained.CHECK_MAX_MS);
        assertTrue(VarExplained.CHECK_MAX_MS <= 100_000L);
    }

    private final long[] clock = {0};

    @Test public void itsDecisionTurnsItAtOnceAndItIsReadFirst() {
        BoardVar check = new BoardVar();
        check.out(ID, T);
        assertTrue(check.isOut(ID));
        assertTrue(check.isOut(null));
        assertFalse(check.isOut("another"));
        // The decision: at once.
        assertTrue(check.over(T + 1000L, true));
        // Gone on: never before its least (8 s), so it is read.
        assertTrue(check.movedOn(ID));
        assertFalse(check.over(T + 3000L, false));
        assertTrue(check.over(T + VarCheck.LEAST_MS, false));
        // Another game's events say nothing of this check.
        check.out(ID, T);
        assertFalse(check.movedOn("another"));
        assertFalse(check.over(T + 30_000L, false));
        // Nothing at all: off at its longest, 100 s (it was three minutes).
        assertFalse(check.over(T + VarCheck.CAP_MS - 1, false));
        assertTrue(check.over(T + VarCheck.CAP_MS, false));
        check.off();
        assertFalse(check.isOut(null));
        assertFalse(check.over(T + 1_000_000L, true));
    }

    @Test public void fotMobsFlagOnThenOffIsPlayGoneOn() {
        BoardVar check = new BoardVar();
        check.out(ID, T);
        check.flag(ID, false);
        assertFalse(check.over(T + 30_000L, false));
        check.flag(ID, true);
        assertFalse(check.over(T + 30_000L, false));
        check.flag(ID, false);
        assertTrue(check.over(T + 30_000L, false));
    }

    @Test public void aCheckThatEndedWithoutItsDecisionIsNoLongerUnderWay() {
        VarExplained.checkBegan(ID, T);
        assertTrue(VarExplained.checking(ID, T + 10_000L));
        VarExplained.checkEnded(ID, T + 20_000L);
        assertFalse(VarExplained.checking(ID, T + 21_000L));
        assertTrue(VarExplained.endedLately(ID, T + 21_000L));
        assertFalse(VarExplained.endedLately(ID, T + 20_000L + VarCheck.AGAIN_MS));
        // A new check after it is under way again.
        VarExplained.checkStarted(ID, T + 200_000L);
        assertTrue(VarExplained.checking(ID, T + 201_000L));
    }
}
