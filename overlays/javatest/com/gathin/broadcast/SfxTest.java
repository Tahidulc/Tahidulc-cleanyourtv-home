package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SfxTest {
    @Test
    public void theGoalSoundsComeRoundInTurnAndAreNamed() {
        assertEquals("swoosh", Sfx.nextGoal("off"));
        assertEquals("roar", Sfx.nextGoal("swoosh"));
        assertEquals("Your swoosh", Sfx.goalName(Sfx.GOAL_DEFAULT));
        assertEquals("hit", Sfx.nextGoal("roar"));
        assertEquals("boom", Sfx.nextGoal("stab"));
        assertEquals("off", Sfx.nextGoal("sting"));
        assertEquals("off", Sfx.nextGoal("nothing like it"));
        assertEquals("Broadcast hit", Sfx.goalName("hit"));
        assertEquals("Off", Sfx.goalName(null));
        assertEquals(Sfx.GOALS.length, Sfx.GOAL_NAMES.length);
    }
}
