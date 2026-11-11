package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Whitebrook Road Sports on the phone: what is on it, as the game and the family's choice have it. */
public class SeeThroughTest {
    @Test public void theWordmarkSaysWhatIsOn() {
        assertEquals("premier league", ScoreBoard.whatFor("", "Premier League", false));
        assertEquals("football", ScoreBoard.whatFor("", "Scottish Premiership", false));
        assertEquals("football", ScoreBoard.whatFor("", "Champions League", false));
        assertEquals("f1", ScoreBoard.whatFor("", "", true));
        // One chosen is kept, whatever is on.
        assertEquals("main event", ScoreBoard.whatFor("main event", "Premier League", false));
        assertEquals("", SportsMark.WHATS[0]);
    }

    @Test public void aGeneralChannelCarriesNoSport() {
        // Sports off: the name alone, nothing on it; on, the choice or football.
        assertEquals("", SportsMark.onFor(false, "premier league"));
        assertEquals("", SportsMark.onFor(false, ""));
        assertEquals("f1", SportsMark.onFor(true, "f1"));
        assertEquals("football", SportsMark.onFor(true, ""));
    }
}
