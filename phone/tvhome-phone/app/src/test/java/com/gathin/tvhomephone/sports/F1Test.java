package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class F1Test {
    private static final String LOGOS = "https://media.formula1.com/image/upload/c_lfill,w_96/q_auto/v1740000001/common/f1/";

    @Test public void teamLogosAsFormula1ComFilesThem() {
        assertEquals(LOGOS + "2026/redbullracing/2026redbullracinglogo.webp", F1.logo("red_bull", 2026));
        assertEquals(LOGOS + "2026/racingbulls/2026racingbullslogo.webp", F1.logo("rb", 2026));
        assertEquals(LOGOS + "2026/astonmartin/2026astonmartinlogo.webp", F1.logo("aston_martin", 2026));
        assertEquals(LOGOS + "2026/mercedes/2026mercedeslogo.webp", F1.logo("mercedes", 2026));
        assertEquals(LOGOS + "2026/kicksauber/2026kicksauberlogo.webp", F1.logo("kick_sauber", 2026));
        assertNull(F1.logo("", 2026));
        assertNull(F1.logo(null, 2026));
    }

    @Test public void lastSeasonsLogoAfterThisSeasons() {
        assertArrayEquals(new String[]{LOGOS + "2026/audi/2026audilogo.webp", LOGOS + "2025/audi/2025audilogo.webp"}, F1.logos("audi", 2026));
        assertEquals(0, F1.logos("", 2026).length);
    }

    @Test public void teamColours() {
        assertEquals(0xFFFF8000, F1.colour("mclaren"));
        assertEquals(0xFF3671C6, F1.colour("red_bull"));
        assertEquals(0xFF27F4D2, F1.colour("mercedes"));
        assertEquals(0xFFC9A227, F1.colour("cadillac"));
        assertEquals(0xFFE10600, F1.colour("minardi"));
        assertEquals(0xFFE10600, F1.colour(null));
    }

    @Test public void lastNamesMatchWithoutAccents() {
        assertEquals("hulkenberg", F1.key("Nico Hülkenberg"));
        assertEquals(F1.key("Hülkenberg"), F1.key("Nico Hulkenberg"));
        assertEquals("perez", F1.key("Sergio Pérez"));
        assertEquals("antonelli", F1.key("Kimi Antonelli"));
        assertEquals("antonelli", F1.key("Antonelli"));
    }

    @Test public void initials() {
        assertEquals("LN", F1.initials("Lando Norris"));
        assertEquals("AK", F1.initials("Andrea Kimi Antonelli"));
        assertEquals("M", F1.initials("McLaren"));
        assertEquals("", F1.initials(null));
    }

    @Test public void espnIdsFromTheScoreboard() throws Exception {
        JSONObject board = new JSONObject("{\"events\":[{\"competitions\":["
                + "{\"competitors\":[{\"id\":\"4665\",\"athlete\":{\"displayName\":\"Max Verstappen\"}},"
                + "{\"id\":\"4396\",\"athlete\":{\"displayName\":\"Nico Hülkenberg\"}},{\"athlete\":{\"displayName\":\"No Id\"}}]},"
                + "{\"competitors\":[{\"id\":\"5829\",\"athlete\":{\"displayName\":\"Kimi Antonelli\"}}]}]}]}");
        Map<String, String> ids = new HashMap<>();
        F1.learn(board, ids);
        assertEquals("4665", ids.get(F1.key("Verstappen")));
        assertEquals("4396", ids.get(F1.key("Hulkenberg")));
        assertEquals("5829", ids.get(F1.key("Antonelli")));
        assertEquals(3, ids.size());
        assertEquals("https://a.espncdn.com/i/headshots/rpm/players/full/4665.png", F1.headshot("4665"));
        assertNull(F1.headshot(null));
    }
}
