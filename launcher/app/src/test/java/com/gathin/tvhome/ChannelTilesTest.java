package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Behind logos: each logo on its own colour by default; every TV switched to it once from 4.98's black tiles (the old
 * switch cannot say when a tile was chosen); whatever is chosen since (Logo's colour, None or Black tile) is kept.
 */
public class ChannelTilesTest {
    /** The preferences as the switch reads them: what a TV kept, in memory. */
    private static final class Prefs implements ChannelTiles.Kept {
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
    public void aNewTvHasEachLogoOnItsOwnColour() {
        Prefs p = new Prefs();
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(p));
        assertEquals(Integer.valueOf(ChannelTiles.COLOUR), p.m.get(ChannelTiles.KEY));
    }

    @Test
    public void theOldDefaultIsSwitchedOnce() {
        // 4.98 to 5.36, never touched: black tiles by default; now the logo's colour, written once and not again.
        Prefs p = new Prefs();
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(p));
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(p));
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(p));
        assertEquals(1, p.writes);
    }

    @Test
    public void anOldTileSettingCannotSayWhenSoItIsSwitchedOnceToo() {
        // The old switch turned off and on again (tiles), or off ("as they come"): either way the new default now.
        Prefs on = new Prefs();
        on.m.put(ChannelTiles.OLD, 1);
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(on));
        Prefs off = new Prefs();
        off.m.put(ChannelTiles.OLD, 0);
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(off));
    }

    @Test
    public void aChoiceMadeAfterTheSwitchIsKept() {
        Prefs p = new Prefs();
        p.m.put(ChannelTiles.OLD, 1);
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(p));
        // the family picks Black tile in the style picker: kept, however often it is read
        p.put(ChannelTiles.KEY, ChannelTiles.TILE);
        for (int i = 0; i < 3; i++) assertEquals(ChannelTiles.TILE, ChannelTiles.behind(p));
        // then None
        p.put(ChannelTiles.KEY, ChannelTiles.NONE);
        for (int i = 0; i < 3; i++) assertEquals(ChannelTiles.NONE, ChannelTiles.behind(p));
        // and back to the logo's colour
        p.put(ChannelTiles.KEY, ChannelTiles.COLOUR);
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(p));
    }

    @Test
    public void theRuleItself() {
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(null, null));
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(null, Boolean.TRUE));
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(null, Boolean.FALSE));
        assertEquals(ChannelTiles.TILE, ChannelTiles.behind(ChannelTiles.TILE, Boolean.FALSE));
        assertEquals(ChannelTiles.NONE, ChannelTiles.behind(ChannelTiles.NONE, Boolean.TRUE));
        // something it does not know is the default
        assertEquals(ChannelTiles.COLOUR, ChannelTiles.behind(7, null));
    }

    @Test
    public void theLoadersAreAskedForTheRightLogo() {
        String u = "https://example.invalid/logos/kestrel-one.png";
        assertEquals(u + ChannelTiles.HALO, ChannelTiles.haloUrl(u));
        assertEquals(u + ChannelTiles.MARK, ChannelTiles.url(u));
        assertEquals(u + ChannelTiles.COLOUR_MARK, ChannelTiles.colourUrl(u));
        assertTrue(ChannelTiles.haloed(ChannelTiles.haloUrl(u)));
        assertFalse(ChannelTiles.haloed(ChannelTiles.url(u)));
        assertTrue(ChannelTiles.colourMarked(ChannelTiles.colourUrl(u)));
        assertFalse(ChannelTiles.colourMarked(ChannelTiles.haloUrl(u)));
        for (String m : new String[]{ChannelTiles.haloUrl(u), ChannelTiles.url(u), ChannelTiles.colourUrl(u)}) {
            assertEquals(u, ChannelTiles.unmarked(m));
        }
        // a UHD logo keeps its own mark under ours
        assertEquals(u + "#uhd", ChannelTiles.unmarked(ChannelTiles.colourUrl(u + "#uhd")));
        // marked once only; nothing stays nothing
        assertEquals(ChannelTiles.haloUrl(u), ChannelTiles.haloUrl(ChannelTiles.haloUrl(u)));
        assertNull(ChannelTiles.colourUrl(null));
        assertEquals("", ChannelTiles.haloUrl(""));
    }
}
