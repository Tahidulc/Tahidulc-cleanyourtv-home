package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class SfxTest {
    @Test
    public void theGoalSoundsComeRoundInTurnAndAreNamed() {
        assertEquals("swoosh", Sfx.nextGoal("off"));
        assertEquals("roar", Sfx.nextGoal("swoosh"));
        assertEquals("Your swoosh", Sfx.goalName(Sfx.GOAL_DEFAULT));
        assertEquals("hit", Sfx.nextGoal("roar"));
        assertEquals("boom", Sfx.nextGoal("stab"));
        // the six made for the new goal animations after them, then round to off
        assertEquals("ting", Sfx.nextGoal("sting"));
        assertEquals("brass", Sfx.nextGoal("ting"));
        assertEquals("off", Sfx.nextGoal("thunder"));
        assertEquals("thunder", Sfx.previousGoal("off"));
        assertEquals("sting", Sfx.previousGoal("ting"));
        assertEquals("off", Sfx.previousGoal("swoosh"));
        assertEquals("off", Sfx.previousGoal("nothing like it"));
        assertEquals("Whoosh and ting", Sfx.goalName("ting"));
        assertEquals("Thunder and sparkle", Sfx.goalName("thunder"));
        for (String k : Sfx.REVEAL_GOALS) assertFalse(k, "Off".equals(Sfx.goalName(k)));
        // the default stays the family's swoosh
        assertEquals("swoosh", Sfx.GOAL_DEFAULT);
        assertEquals("off", Sfx.nextGoal("nothing like it"));
        assertEquals("Broadcast hit", Sfx.goalName("hit"));
        assertEquals("Off", Sfx.goalName(null));
        assertEquals(Sfx.GOALS.length, Sfx.GOAL_NAMES.length);
    }
}
