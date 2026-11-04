package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class AspectTest {
    private static final class MapStore implements Aspect.Store {
        final Map<String, String> m = new HashMap<>();

        @Override public String get(String key) { return m.get(key); }

        @Override public void put(String key, String value) { m.put(key, value); }

        @Override public void remove(String key) { m.remove(key); }
    }

    @Test public void onItsSideFillsUprightFits() {
        assertEquals(Aspect.STRETCH, Aspect.defaultFor(true));
        assertEquals(Aspect.FIT, Aspect.defaultFor(false));
        MapStore s = new MapStore();
        assertEquals(Aspect.STRETCH, Aspect.get(s, "a", true));
        assertEquals(Aspect.FIT, Aspect.get(s, "a", false));
        assertTrue(s.m.isEmpty());
    }

    @Test public void aPickBeatsTheDefaultEachWayRound() {
        MapStore s = new MapStore();
        // Fit picked on its side is kept (it isn't the default there); Stretch there is the default, so forgotten.
        Aspect.set(s, "a", true, Aspect.FIT);
        assertEquals(Aspect.FIT, Aspect.get(s, "a", true));
        assertEquals(Aspect.FIT, Aspect.get(s, "a", false));
        Aspect.set(s, "a", true, Aspect.STRETCH);
        assertEquals(Aspect.STRETCH, Aspect.get(s, "a", true));
        assertFalse(s.m.containsKey("aspect:a"));
        // Upright and on its side are remembered apart.
        Aspect.set(s, "b", false, Aspect.FILL);
        Aspect.set(s, "b", true, Aspect.FOUR_THREE);
        assertEquals(Aspect.FILL, Aspect.get(s, "b", false));
        assertEquals(Aspect.FOUR_THREE, Aspect.get(s, "b", true));
        assertEquals(Aspect.FIT, Aspect.get(s, "c", false));
        assertEquals(Aspect.STRETCH, Aspect.get(s, "c", true));
        Aspect.set(s, "b", false, Aspect.FIT);
        assertEquals(Aspect.FIT, Aspect.get(s, "b", false));
        assertFalse(s.m.containsKey("aspect-portrait:b"));
    }

    @Test public void anOlderPickOnItsSideStillCounts() {
        // Before this version the player was on its side only, and kept "aspect:<channel>".
        MapStore s = new MapStore();
        s.put("aspect:old", "STRETCH");
        assertEquals(Aspect.STRETCH, Aspect.get(s, "old", true));
        assertEquals(Aspect.FIT, Aspect.get(s, "old", false));
    }

    @Test public void oddValuesReadAsTheDefault() {
        MapStore s = new MapStore();
        s.put("aspect:x", "SOMETHING_ELSE");
        assertEquals(Aspect.STRETCH, Aspect.get(s, "x", true));
        assertEquals(Aspect.FIT, Aspect.get(s, null, false));
        assertNull(Aspect.parse(null));
        Aspect.set(s, null, true, Aspect.FIT);
        assertTrue(s.m.keySet().stream().allMatch(k -> k.equals("aspect:x")));
    }

    @Test public void doubleTapAndPinch() {
        assertEquals(Aspect.FILL, Aspect.FIT.toggled());
        assertEquals(Aspect.FIT, Aspect.FILL.toggled());
        assertEquals(Aspect.FIT, Aspect.WIDE.toggled());
        // On its side a double tap goes Stretch, Fit, Fill and round.
        assertEquals(Aspect.FIT, Aspect.STRETCH.toggled(true));
        assertEquals(Aspect.FILL, Aspect.FIT.toggled(true));
        assertEquals(Aspect.STRETCH, Aspect.FILL.toggled(true));
        assertEquals(Aspect.FILL, Aspect.FIT.toggled(false));
        assertEquals(Aspect.FILL, Aspect.FIT.pinched(1.3f));
        assertEquals(Aspect.FIT, Aspect.FILL.pinched(0.7f));
        assertEquals(Aspect.FILL, Aspect.FILL.pinched(1.02f));
        assertEquals(16f / 9f, Aspect.WIDE.ratio, 0.0001f);
        assertEquals(0f, Aspect.FILL.ratio, 0f);
    }
}
