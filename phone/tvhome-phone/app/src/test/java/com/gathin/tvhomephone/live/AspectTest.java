package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test public void rememberedPerChannelWithFitNotStored() {
        MapStore s = new MapStore();
        assertEquals(Aspect.FIT, Aspect.get(s, "a"));
        Aspect.set(s, "a", Aspect.FILL);
        Aspect.set(s, "b", Aspect.FOUR_THREE);
        assertEquals(Aspect.FILL, Aspect.get(s, "a"));
        assertEquals(Aspect.FOUR_THREE, Aspect.get(s, "b"));
        assertEquals(Aspect.FIT, Aspect.get(s, "c"));
        Aspect.set(s, "a", Aspect.FIT);
        assertEquals(Aspect.FIT, Aspect.get(s, "a"));
        assertFalse(s.m.containsKey("aspect:a"));
        assertTrue(s.m.containsKey("aspect:b"));
    }

    @Test public void oddValuesReadAsFit() {
        MapStore s = new MapStore();
        s.put("aspect:x", "SOMETHING_ELSE");
        assertEquals(Aspect.FIT, Aspect.get(s, "x"));
        assertEquals(Aspect.FIT, Aspect.get(s, null));
        Aspect.set(s, null, Aspect.FILL);
        assertTrue(s.m.keySet().stream().allMatch(k -> k.equals("aspect:x")));
    }

    @Test public void doubleTapAndPinch() {
        assertEquals(Aspect.FILL, Aspect.FIT.toggled());
        assertEquals(Aspect.FIT, Aspect.FILL.toggled());
        assertEquals(Aspect.FIT, Aspect.WIDE.toggled());
        assertEquals(Aspect.FILL, Aspect.FIT.pinched(1.3f));
        assertEquals(Aspect.FIT, Aspect.FILL.pinched(0.7f));
        assertEquals(Aspect.FILL, Aspect.FILL.pinched(1.02f));
        assertEquals(16f / 9f, Aspect.WIDE.ratio, 0.0001f);
        assertEquals(0f, Aspect.FILL.ratio, 0f);
    }
}
