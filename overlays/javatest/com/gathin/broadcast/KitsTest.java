package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** The 3D kits' frames (KitSheets), a goal's shirt turning (KitTurn), the shirt numbers (Shirts) and the first names. */
public class KitsTest {
    @Test public void boardFramesTurnThenSway() {
        float[] a = KitSheets.boardAngles(0.35f);
        assertEquals(KitSheets.TURN_N + KitSheets.SWAY_N, a.length);
        assertEquals(0.35f, a[0], 1e-6);
        assertEquals(0.35f + Math.PI / 12, a[1], 1e-5);
        // the sway's middle frame is the side's rest, four either side of it 0.07 apart
        assertEquals(0.35f, a[KitSheets.SWAY_AT + 4], 1e-6);
        assertEquals(0.35f - 0.28f, a[KitSheets.SWAY_AT], 1e-5);
        assertEquals(0.35f + 0.28f, a[KitSheets.SWAY_AT + 8], 1e-5);
    }

    @Test public void nearestFrameAmongTheOnesAsked() {
        KitSheets.Sheet s = new KitSheets.Sheet("k", null, 6, 6, 96, KitSheets.boardAngles(0.35f));
        assertEquals(KitSheets.SWAY_AT + 5, s.nearest(0.35f + 0.07f, KitSheets.SWAY_AT, KitSheets.SWAY_AT + KitSheets.SWAY_N));
        // half a turn round from its rest: frame 12 of the turn
        assertEquals(12, s.nearest((float) (0.35 + Math.PI), 0, KitSheets.TURN_N));
        // a whole turn and a bit is the same as a bit
        assertEquals(1, s.nearest((float) (0.35 + Math.PI * 2 + Math.PI / 12), 0, KitSheets.TURN_N));
    }

    @Test public void goalFramesFromTheFrontRoundToTheBack() {
        float[] a = KitSheets.turnAngles();
        assertEquals(28, a.length);
        assertEquals(-0.45f, a[0], 1e-6);
        assertEquals(Math.PI + 0.3, a[a.length - 1], 1e-4);
        for (int i = 1; i < a.length; i++) assertTrue(a[i] > a[i - 1]);
    }

    @Test public void theShirtComesOutTurnsAndGoesHome() {
        assertEquals(0f, KitTurn.up(0f), 1e-6);
        assertEquals(1f, KitTurn.up(1.0f), 1e-6);
        assertEquals(0f, KitTurn.up(4.2f), 1e-6);
        // at rest in its cell to begin with, round to its back (swaying a little) once out
        assertEquals(0.35f, KitTurn.angle(0f, 0.35f), 1e-6);
        assertEquals(Math.PI + 0.25 * Math.sin(2.4), KitTurn.angle(2.0f, 0.35f), 1e-4);
        assertEquals(0.35f, KitTurn.angle(4.2f, 0.35f), 1e-6);
    }

    @Test public void shirtNumbersFromTheSquads() throws Exception {
        Shirts.clear();
        JSONArray rosters = new JSONArray()
                .put(new JSONObject().put("roster", new JSONArray()
                        .put(new JSONObject().put("jersey", "41").put("athlete", new JSONObject().put("id", "123").put("displayName", "Jay Hollis").put("lastName", "Hollis")))
                        .put(new JSONObject().put("jersey", "37").put("athlete", new JSONObject().put("id", "9").put("displayName", "Daan van der Molen").put("lastName", "van der Molen")))
                        .put(new JSONObject().put("athlete", new JSONObject().put("id", "77").put("displayName", "No Number")))));
        Shirts.note("g1", rosters);
        assertArrayEquals(new String[]{"41", "Jay Hollis", "Hollis"}, Shirts.of("g1", "123"));
        assertNull(Shirts.of("g1", "77"));
        assertNull(Shirts.of("g2", "123"));
        assertEquals("HOLLIS", Shirts.back("Hollis", "Jay Hollis"));
        assertEquals("VAN DER MOLEN", Shirts.back("van der Molen", "Daan van der Molen"));
        assertEquals("ZEZE", Shirts.back("", "Zeze"));
        assertEquals("HOLLIS", Shirts.back(null, "Jay Hollis"));
    }

    @Test public void firstNames() {
        assertEquals("Tomáš", BroadcastData.firstName("Tomáš Vlček", "Tomáš", "Vlček"));
        assertEquals("Jay", BroadcastData.firstName("Jay Hollis", "", "Hollis"));
        assertEquals("", BroadcastData.firstName("Zeze", "", "Zeze"));
        assertEquals("", BroadcastData.firstName("Zeze", "Zeze", "Zeze"));
        assertEquals("Mads", BroadcastData.firstName("Mads Østby", null, "Østby"));
    }

    @Test public void shirtsNotMadeAreAskedForAgainAMinuteLaterThreeTriesAtMost() {
        KitSheets.Tries t = new KitSheets.Tries();
        long wait = KitSheets.Tries.WAIT_MS;
        assertFalse(t.due("a", 0));
        t.failed("a", 1000);
        t.failed("a", 1100);   // its other side, in the same go: one try
        assertFalse(t.due("a", 1100 + wait - 1));
        assertTrue(t.due("a", 1100 + wait));
        assertFalse(t.due("a", 1100 + wait));   // once
        assertFalse(t.given("a"));
        t.failed("a", 100_000);
        assertTrue(t.due("a", 100_000 + wait));
        t.failed("a", 200_000);
        assertTrue(t.given("a"));
        assertFalse(t.due("a", Long.MAX_VALUE - 1));
        // another game's shirts start afresh
        t.failed("b", 300_000);
        assertFalse(t.given("b"));
        assertTrue(t.due("b", 300_000 + wait));
    }

    @Test public void aSideForItsSheet() throws Exception {
        JSONObject t = KitSheets.team("359", "Arsenal", "home", 0xFFE20520, 0x003399, "https://example.invalid/a.png");
        assertEquals("359", t.getString("espnId"));
        assertEquals("#e20520", t.getString("color"));
        assertEquals("#003399", t.getString("color2"));
        assertEquals("home", t.getString("kitType"));
        JSONObject none = KitSheets.team(null, null, null, 0, 0, null);
        assertEquals("", none.getString("espnId"));
        assertTrue(!none.has("color"));
    }
}
