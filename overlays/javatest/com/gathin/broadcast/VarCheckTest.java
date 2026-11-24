package com.gathin.broadcast;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** When "Checking VAR" comes off the board: the same rule on the TV and the phone. */
public class VarCheckTest {
    @Test public void itGoesWithItsDecisionOrAtItsLongest() {
        assertTrue(VarCheck.over(2_000, true, false, 0, false));
        assertFalse(VarCheck.over(VarCheck.CAP_MS - 1, false, false, 0, false));
        assertTrue(VarCheck.over(VarCheck.CAP_MS, false, false, 0, false));
        // well short of the three minutes it used to stand
        assertTrue(VarCheck.CAP_MS <= 100_000L);
    }

    @Test public void itGoesWhenTheGameGoesOnPastIt() {
        // read first, whatever comes
        assertFalse(VarCheck.over(VarCheck.LEAST_MS - 1, false, true, 60_000, true));
        // the commentary's next line, FotMob's flag gone, another event of the game
        assertTrue(VarCheck.over(VarCheck.LEAST_MS, false, true, 0, false));
        // the channel's scoreboard back after the check's replays
        assertTrue(VarCheck.over(15_000, false, false, 0, true));
    }

    @Test public void itGoesWhenThePictureIsBackInLivePlay() {
        // the main camera's wide shot held: play again, once the check has been on a little
        assertFalse(VarCheck.over(12_000, false, false, 30_000, false));
        assertFalse(VarCheck.over(VarCheck.PLAY_AFTER_MS + 5_000, false, false, VarCheck.PLAY_MS - 1, false));
        assertTrue(VarCheck.over(VarCheck.PLAY_AFTER_MS + 5_000, false, false, VarCheck.PLAY_MS, false));
        // no pictures: the cap is the backstop
        assertFalse(VarCheck.over(60_000, false, false, 0, false));
    }

    private static String[] line(String text) {
        return new String[]{text, "63'", "1"};
    }

    @Test public void theCommentaryGoesOnPastTheCheck() {
        List<String[]> waiting = Arrays.asList(line("Goal! Rovers 1, Albion 0. Sam Okafor (Rovers)."), line("VAR checking: possible offside."));
        assertFalse(VarCheck.movedOn(waiting));
        List<String[]> decided = Arrays.asList(line("VAR checking: possible offside."), line("VAR Decision: Goal Rovers 1-0 Albion."));
        assertFalse(VarCheck.movedOn(decided));
        List<String[]> on = Arrays.asList(line("VAR checking: possible penalty."), line("Corner, Albion. Conceded by Leo Marsh."));
        assertTrue(VarCheck.movedOn(on));
        assertFalse(VarCheck.movedOn(Arrays.asList(line("Foul by Leo Marsh (Albion)."), line("Free kick Rovers."))));
        assertFalse(VarCheck.movedOn(null));
    }

    @Test public void livePlayTellsHowLongTheWideShotHasHeldAndTheScoreboardsReturn() {
        LivePlay p = new LivePlay();
        int[] wide = new int[LivePlay.W * LivePlay.H];
        Arrays.fill(wide, 0xFF2E8B3A);
        long t = 1_000_000;
        assertTrue(p.wideMs(t) == 0);
        for (int i = 0; i <= 20; i++) p.frame(wide, LivePlay.W, LivePlay.H, t + i * 500L);
        assertTrue(p.wideMs(t + 10_000) >= 9_000);
        // the channel's scoreboard away for a replay, then back
        p.bug(true, t + 11_000);
        assertTrue(p.wideMs(t + 11_000) == 0);
        assertFalse(p.backSince(t + 5_000, t + 11_500));
        p.bug(false, t + 14_000);
        assertFalse(p.backSince(t + 5_000, t + 14_500));
        assertTrue(p.backSince(t + 5_000, t + 16_000));
        assertFalse(p.backSince(t + 12_000, t + 16_000));
    }
}
