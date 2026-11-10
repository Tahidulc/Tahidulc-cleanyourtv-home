package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The league a try of the pop-up or line-ups takes its game from. */
public class ClassicTryTest {
    @Test
    public void leagueNames() {
        assertEquals("eng.1|PL", ClassicIntro.league(""));
        assertEquals("eng.1|PL", ClassicIntro.league("PL"));
        assertEquals("esp.1|LALIGA", ClassicIntro.league("laliga"));
        assertEquals("esp.1|LALIGA", ClassicIntro.league("esp.1"));
        assertEquals("ita.1|ITA.1", ClassicIntro.league("ita.1"));
        assertEquals("ger.1|BUND", ClassicIntro.league("ger.1|BUND"));
    }
}
