package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Behind logos on the phone: each logo on its own colour by default, every phone switched to it once (4.75's old black
 * tile switch, unused since 4.77, cannot bring the tiles back), and whatever is chosen since (Logo's colour, None or
 * Black tile) kept.
 */
public class LogoTilesTest {
    /** The preferences as the switch reads them: what a phone kept, in memory. */
    private static final class Prefs implements LogoTiles.Kept {
        final Map<String, Integer> m = new HashMap<>();
        int writes;

        @Override public boolean has(String key) {
            return m.containsKey(key);
        }

        @Override public int get(String key, int otherwise) {
            Integer v = m.get(key);
            return v == null ? otherwise : v;
        }

        @Override public void put(String key, int value) {
            m.put(key, value);
            writes++;
        }
    }

    @Test
    public void aNewPhoneHasEachLogoOnItsOwnColour() {
        Prefs p = new Prefs();
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(p));
        assertEquals(Integer.valueOf(LogoTiles.COLOUR), p.m.get(LogoTiles.KEY));
    }

    @Test
    public void switchedOnceAndOnlyOnce() {
        Prefs p = new Prefs();
        for (int i = 0; i < 4; i++) assertEquals(LogoTiles.COLOUR, LogoTiles.behind(p));
        assertEquals(1, p.writes);
    }

    @Test
    public void theOld475SwitchDoesNotBringBlackTilesBack() {
        Prefs on = new Prefs();
        on.m.put(LogoTiles.OLD, 1);
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(on));
        Prefs off = new Prefs();
        off.m.put(LogoTiles.OLD, 0);
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(off));
    }

    @Test
    public void aChoiceMadeSinceIsKept() {
        Prefs p = new Prefs();
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(p));
        p.put(LogoTiles.KEY, LogoTiles.NONE);
        for (int i = 0; i < 3; i++) assertEquals(LogoTiles.NONE, LogoTiles.behind(p));
        p.put(LogoTiles.KEY, LogoTiles.TILE);
        for (int i = 0; i < 3; i++) assertEquals(LogoTiles.TILE, LogoTiles.behind(p));
        p.put(LogoTiles.KEY, LogoTiles.COLOUR);
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(p));
    }

    @Test
    public void theRuleItself() {
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(null, null));
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(null, Boolean.TRUE));
        assertEquals(LogoTiles.TILE, LogoTiles.behind(LogoTiles.TILE, null));
        assertEquals(LogoTiles.NONE, LogoTiles.behind(LogoTiles.NONE, Boolean.TRUE));
        assertEquals(LogoTiles.COLOUR, LogoTiles.behind(9, null));
    }
}
