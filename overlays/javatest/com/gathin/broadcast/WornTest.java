package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;

/** The kits each side wears, as ESPN's line-ups name them, and the colours the boards take from them. */
public class WornTest {
    @After
    public void clear() {
        Worn.forget();
    }

    /** Rosters as ESPN gives them in a match summary (only what Worn reads). */
    private static JSONArray rosters(String homeType, String homeColour, String homeAlt, String awayType, String awayColour, String awayAlt)
            throws Exception {
        JSONArray a = new JSONArray();
        a.put(new JSONObject().put("homeAway", "home").put("team", new JSONObject().put("id", "464"))
                .put("uniform", uniform(homeType, homeColour, homeAlt)));
        a.put(new JSONObject().put("homeAway", "away").put("team", new JSONObject().put("id", "578"))
                .put("uniform", uniform(awayType, awayColour, awayAlt)));
        return a;
    }

    private static JSONObject uniform(String type, String colour, String alt) throws Exception {
        if (type == null) return null;
        JSONObject u = new JSONObject().put("type", type).put("color", colour);
        if (alt != null) u.put("alternateColor", alt);
        return u;
    }

    @Test
    public void walesInTheirGoldThirdKitInOslo() throws Exception {
        // Norway v Wales, 14 November 2026: ESPN's team colours are both red; Wales wore their third kit.
        Worn.Kit[] k = Worn.of(rosters("home", "f50000", "1d2c60", "third", "e9af3a", "112117"), "464");
        assertEquals("home", k[0].type);
        assertEquals(0xFFF50000, k[0].body);
        assertEquals("third", k[1].type);
        assertEquals(0xFFE9AF3A, k[1].body);
        assertEquals(0xFF112117, k[1].trim);
        assertEquals("Third kit", k[1].label());
        int[] c = Worn.pick(k, 0xFFC8102E, 0, 0xFFE70000, 0);
        assertEquals(0xFFF50000, c[0]);
        assertEquals(0xFFE9AF3A, c[2]);
        assertEquals(0xFF112117, c[3]);
    }

    @Test
    public void noKitNamedUntilTheLineUpsAreOut() throws Exception {
        Worn.Kit[] k = Worn.of(rosters(null, null, null, null, null, null), "464");
        assertNull(k[0]);
        assertNull(k[1]);
        assertFalse(Worn.note("401861175", k));
        assertNull(Worn.get("401861175"));
    }

    @Test
    public void withNoKitNamedTheAwaySideChangesOnAClash() {
        // Both red: the away side takes its second colour.
        int[] c = Worn.pick(null, 0xFFC8102E, 0xFFFFFFFF, 0xFFE70000, 0xFF0B1E4B);
        assertEquals(0xFFC8102E, c[0]);
        assertEquals(0xFF0B1E4B, c[2]);
        // Its second clashes too (or it has none): white against a dark home side, navy against a light one.
        assertEquals(0xFFF4F5FA, Worn.pick(null, 0xFFC8102E, 0, 0xFFE70000, 0xFFD00000)[2]);
        assertEquals(0xFF1A2466, Worn.pick(null, 0xFFFFFFFF, 0, 0xFFF8F8FF, 0)[2]);
        // No clash: the clubs' own colours.
        int[] apart = Worn.pick(null, 0xFFC8102E, 0, 0xFF144992, 0xFFFFFFFF);
        assertEquals(0xFF144992, apart[2]);
        assertEquals(0xFFFFFFFF, apart[3]);
    }

    @Test
    public void picksAgainAsTheSame() {
        int[] once = Worn.pick(null, 0xFFC8102E, 0, 0xFFE70000, 0xFF0B1E4B);
        int[] twice = Worn.pick(null, once[0], once[1], once[2], once[3]);
        assertEquals(once[2], twice[2]);
        int[] white = Worn.pick(null, 0xFFC8102E, 0, 0xFFE70000, 0);
        assertEquals(white[2], Worn.pick(null, white[0], white[1], white[2], white[3])[2]);
    }

    @Test
    public void aKitWithoutASecondColourKeepsTheClubs() throws Exception {
        // Lazio in black at Napoli, no second colour named: its club's second (white) goes with it.
        Worn.Kit[] k = Worn.of(rosters("home", "129bc4", "fafafa", "third", "000000", null), "");
        int[] c = Worn.pick(k, 0xFF0677D2, 0, 0xFF74BDE7, 0xFFFFFFFF);
        assertEquals(0xFF000000, c[2]);
        assertEquals(0xFFFFFFFF, c[3]);
    }

    @Test
    public void sidesByTeamWhenNotMarked() throws Exception {
        JSONArray a = new JSONArray();
        a.put(new JSONObject().put("team", new JSONObject().put("id", "578")).put("uniform", uniform("away", "ffffff", null)));
        a.put(new JSONObject().put("team", new JSONObject().put("id", "464")).put("uniform", uniform("home", "f50000", null)));
        Worn.Kit[] k = Worn.of(a, "464");
        assertEquals(0xFFF50000, k[0].body);
        assertEquals(0xFFFFFFFF, k[1].body);
    }

    @Test
    public void keptThroughARestart() throws Exception {
        assertTrue(Worn.note("401861165", Worn.of(rosters("home", "f50000", "1d2c60", "third", "e9af3a", "112117"), "")));
        assertFalse(Worn.note("401861165", Worn.of(rosters("home", "f50000", "1d2c60", "third", "e9af3a", "112117"), "")));
        assertTrue(Worn.unsaved());
        String saved = Worn.save();
        assertFalse(Worn.unsaved());
        Worn.forget();
        Worn.restore(saved);
        Worn.Kit[] k = Worn.get("401861165");
        assertNotNull(k);
        assertEquals("third", k[1].type);
        assertEquals(0xFFE9AF3A, k[1].body);
        assertEquals(0xFF112117, k[1].trim);
        assertEquals(0xFF1D2C60, k[0].trim);
    }

    @Test
    public void aSideNoLongerNamedKeepsItsKit() throws Exception {
        Worn.note("1", Worn.of(rosters("home", "f50000", null, "third", "e9af3a", null), ""));
        Worn.Kit[] later = Worn.of(rosters("home", "f50000", null, null, null, null), "");
        assertFalse(Worn.note("1", later));
        assertEquals(0xFFE9AF3A, Worn.get("1")[1].body);
    }

    @Test
    public void onlyTheLastFortyGames() throws Exception {
        for (int i = 0; i < 45; i++) Worn.note("g" + i, Worn.of(rosters("home", "f50000", null, "away", "ffffff", null), ""));
        assertNull(Worn.get("g0"));
        assertNotNull(Worn.get("g44"));
    }

    @Test
    public void hexBothWays() {
        assertEquals(0xFFE9AF3A, Worn.hex("e9af3a"));
        assertEquals(0xFFE9AF3A, Worn.hex("#E9AF3A"));
        assertEquals(0, Worn.hex(""));
        assertEquals(0, Worn.hex("zzzzzz"));
        assertEquals("e9af3a", Worn.hexOf(0xFFE9AF3A));
        assertEquals("", Worn.hexOf(0));
    }
}
