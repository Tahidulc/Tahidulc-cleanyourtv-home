package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The thermostat from Tuya's cloud: found among the project's devices, read from its own specification, its values
 * scaled, kept in range and on their steps, shown in its unit, and a change sent once the remote is still.
 */
public class ThermostatTest {
    private static final int NONE = TuyaCloud.NONE;
    /** A radiator valve's target: 5-35 °C in half degrees (raw 50-350, scale 1, step 5). */
    private static final TuyaCloud.Range HALF = new TuyaCloud.Range(50, 350, 1, 5, "℃", false);
    /** A wall thermostat's: 5-35 °C in whole degrees. */
    private static final TuyaCloud.Range WHOLE = new TuyaCloud.Range(5, 35, 0, 1, "℃", false);

    // ---- scaling, range and steps ---------------------------------------------------------------------------------

    @Test public void rangesAreReadFromTheSpecification() {
        TuyaCloud.Range r = TuyaCloud.rangeOf("{\"unit\":\"℃\",\"min\":50,\"max\":350,\"scale\":1,\"step\":5}");
        assertNotNull(r);
        assertEquals(50, r.min);
        assertEquals(350, r.max);
        assertEquals(1, r.scale);
        assertEquals(5, r.step);
        assertEquals("℃", r.unit);
        assertFalse(r.guessed);
        // Not a number's range, or not JSON at all.
        assertNull(TuyaCloud.rangeOf("{\"range\":[\"auto\",\"manual\"]}"));
        assertNull(TuyaCloud.rangeOf("{}"));
        assertNull(TuyaCloud.rangeOf("not json"));
        // A step of 0 or none at all counts as 1; no scale is 0.
        TuyaCloud.Range plain = TuyaCloud.rangeOf("{\"min\":5,\"max\":35,\"step\":0}");
        assertEquals(1, plain.step);
        assertEquals(0, plain.scale);
        assertEquals(Arrays.asList("auto", "manual", "holiday"), TuyaCloud.enumOf("{\"range\":[\"auto\",\"manual\",\"holiday\"]}"));
        assertTrue(TuyaCloud.enumOf("{}").isEmpty());
    }

    @Test public void valuesAreScaled() {
        assertEquals(21.5, TuyaCloud.real(215, 1), 1e-9);
        assertEquals(21.0, TuyaCloud.real(21, 0), 1e-9);
        assertEquals(20.25, TuyaCloud.real(2025, 2), 1e-9);
        assertEquals(215, TuyaCloud.toRaw(21.5, HALF));
        assertEquals(21, TuyaCloud.toRaw(21.4, WHOLE));
        // Onto the nearest half degree.
        assertEquals(215, TuyaCloud.toRaw(21.3, HALF));
        assertEquals(210, TuyaCloud.toRaw(21.2, HALF));
    }

    @Test public void targetsAreKeptInRangeAndOnTheSteps() {
        assertEquals(350, TuyaCloud.snap(400, HALF));
        assertEquals(50, TuyaCloud.snap(20, HALF));
        assertEquals(50, TuyaCloud.snap(-30, HALF));
        assertEquals(215, TuyaCloud.snap(214, HALF));
        // Steps counted from the least: 45, 55, 65...
        TuyaCloud.Range odd = new TuyaCloud.Range(45, 352, 1, 10, "℃", false);
        assertEquals(55, TuyaCloud.snap(52, odd));
        // The top not on a step: the step under it.
        assertEquals(345, TuyaCloud.snap(352, odd));
        assertEquals(345, TuyaCloud.snap(500, odd));
    }

    @Test public void leftAndRightMoveByTheDevicesStep() {
        assertEquals(220, TuyaCloud.nudge(215, 1, HALF));
        assertEquals(210, TuyaCloud.nudge(215, -1, HALF));
        assertEquals(22, TuyaCloud.nudge(21, 1, WHOLE));
        // At the ends it stays.
        assertEquals(350, TuyaCloud.nudge(350, 1, HALF));
        assertEquals(50, TuyaCloud.nudge(50, -1, HALF));
        // A reading off the steps goes onto them first.
        assertEquals(220, TuyaCloud.nudge(213, 1, HALF));
        // Not known: nothing to move.
        assertEquals(NONE, TuyaCloud.nudge(NONE, 1, HALF));
    }

    @Test public void degreesShowInTheDevicesUnit() {
        assertEquals("21.5°C", TuyaCloud.degrees(215, HALF, false, true));
        assertEquals("21°C", TuyaCloud.degrees(210, HALF, false, true));
        assertEquals("21°C", TuyaCloud.degrees(21, WHOLE, false, true));
        assertEquals("21.5°", TuyaCloud.degrees(215, HALF, false, false));
        assertEquals("70°F", TuyaCloud.degrees(70, new TuyaCloud.Range(41, 95, 0, 1, "℉", false), true, true));
        assertEquals("-0.5°C", TuyaCloud.degrees(-5, new TuyaCloud.Range(-300, 1000, 1, 1, "℃", false), false, true));
        assertEquals("0°C", TuyaCloud.degrees(0, HALF, false, true));
        assertEquals("–", TuyaCloud.degrees(NONE, HALF, false, true));
        assertEquals("–", TuyaCloud.degrees(215, null, false, true));
    }

    @Test public void fahrenheitFromTheCodeOrTheUnit() {
        assertTrue(TuyaCloud.fahrenheit("temp_set_f", "", false));
        assertTrue(TuyaCloud.fahrenheit("temp_set", "℉", false));
        assertTrue(TuyaCloud.fahrenheit("temp_set", "°F", false));
        assertFalse(TuyaCloud.fahrenheit("temp_set", "℃", true));
        assertFalse(TuyaCloud.fahrenheit("temp_set", "°C", false));
        // The unit says neither: the device's own setting.
        assertTrue(TuyaCloud.fahrenheit("temp_set", "", true));
        assertFalse(TuyaCloud.fahrenheit("temp_set", null, false));
    }

    // ---- finding it -------------------------------------------------------------------------------------------------

    @Test public void heatingDevicesAreFound() throws Exception {
        assertNotNull(TuyaCloud.heatingOf(device("Hall valve", "wkf", true, null)));
        assertNotNull(TuyaCloud.heatingOf(device("Wall", "wk", true, null)));
        assertNotNull(TuyaCloud.heatingOf(device("Floor", "wkcz", true, null)));
        assertNotNull(TuyaCloud.heatingOf(device("Heater", "rs", true, null)));
        // Any other device with a target in its state.
        assertNotNull(TuyaCloud.heatingOf(device("Air con", "kt", true, status("temp_set", 22))));
        assertNotNull(TuyaCloud.heatingOf(device("Odd one", "qt", true, status("TempSet", 22))));
        // A light, or a plug with a temperature it only reads, is not.
        assertNull(TuyaCloud.heatingOf(device("Lamp", "dj", true, status("switch_led", true))));
        assertNull(TuyaCloud.heatingOf(device("Plug", "cz", true, status("temp_current", 210))));
        assertNull(TuyaCloud.heatingOf(null));
        // The IoT Core list says isOnline.
        JSONObject d = new JSONObject().put("id", "x1").put("name", "Valve").put("category", "wkf").put("isOnline", false);
        assertFalse(TuyaCloud.heatingOf(d).online);
        // No name: "Thermostat".
        assertEquals("Thermostat", TuyaCloud.heatingOf(new JSONObject().put("id", "x2").put("category", "wk")).name);
    }

    @Test public void theOneNamedElseTheFirstOnline() throws Exception {
        TuyaCloud.Thermostat aircon = TuyaCloud.heatingOf(device("Air con", "kt", true, status("temp_set", 22)));
        TuyaCloud.Thermostat hall = TuyaCloud.heatingOf(device("Hall valve", "wkf", false, null));
        TuyaCloud.Thermostat lounge = TuyaCloud.heatingOf(device("Lounge", "wk", true, null));
        TuyaCloud.Thermostat bedroom = TuyaCloud.heatingOf(device("Bedroom", "wkf", true, null));
        List<TuyaCloud.Thermostat> all = new ArrayList<>(Arrays.asList(aircon, hall, lounge, bedroom));
        // By name (any case) or id, offline or not.
        assertSame(hall, TuyaCloud.choose(all, "hall VALVE"));
        assertSame(bedroom, TuyaCloud.choose(all, "id-Bedroom"));
        // Else the first online thermostat, valve or heater, before an online air conditioner.
        assertSame(lounge, TuyaCloud.choose(all, ""));
        assertSame(lounge, TuyaCloud.choose(all, null));
        // A name matching none: the same as no name.
        assertSame(lounge, TuyaCloud.choose(all, "Kitchen"));
        // Only offline thermostats and an online air conditioner: the one that answers.
        assertSame(aircon, TuyaCloud.choose(Arrays.asList(hall, aircon), ""));
        assertSame(hall, TuyaCloud.choose(Arrays.asList(hall), ""));
        assertNull(TuyaCloud.choose(new ArrayList<>(), "Hall valve"));
    }

    // ---- reading it ---------------------------------------------------------------------------------------------------

    /** A radiator valve (wkf) as Tuya's specification gives it: no switch, three modes, half degrees. */
    private static JSONObject valveSpec() throws Exception {
        return new JSONObject()
                .put("category", "wkf")
                .put("functions", new JSONArray()
                        .put(fn("mode", "Enum", "{\"range\":[\"auto\",\"manual\",\"holiday\"]}"))
                        .put(fn("temp_set", "Integer", "{\"unit\":\"℃\",\"min\":50,\"max\":350,\"scale\":1,\"step\":5}"))
                        .put(fn("child_lock", "Boolean", "{}")))
                .put("status", new JSONArray()
                        .put(fn("mode", "Enum", "{\"range\":[\"auto\",\"manual\",\"holiday\"]}"))
                        .put(fn("temp_set", "Integer", "{\"unit\":\"℃\",\"min\":50,\"max\":350,\"scale\":1,\"step\":5}"))
                        .put(fn("temp_current", "Integer", "{\"unit\":\"℃\",\"min\":-300,\"max\":1000,\"scale\":1,\"step\":1}"))
                        .put(fn("work_state", "Enum", "{\"range\":[\"heating\",\"idle\"]}")));
    }

    @Test public void theSpecificationsCodesSettableFirst() throws Exception {
        Map<String, String[]> spec = TuyaCloud.specCodes(valveSpec());
        assertEquals(Arrays.asList("mode", "temp_set", "child_lock", "temp_current", "work_state"), new ArrayList<>(spec.keySet()));
        assertEquals("set", spec.get("temp_set")[2]);
        assertEquals("read", spec.get("temp_current")[2]);
        assertEquals("Integer", spec.get("temp_current")[0]);
        assertTrue(TuyaCloud.specCodes(null).isEmpty());
    }

    @Test public void aRadiatorValveIsReadFromItsSpecification() throws Exception {
        TuyaCloud.Thermostat t = new TuyaCloud.Thermostat();
        t.spec = TuyaCloud.specCodes(valveSpec());
        TuyaCloud.read(t, new JSONArray()
                .put(code("mode", "manual")).put(code("temp_set", 215)).put(code("temp_current", 203))
                .put(code("work_state", "heating")).put(code("child_lock", false)));
        assertEquals("temp_set", t.setCode);
        assertEquals("temp_current", t.roomCode);
        assertNull(t.switchCode);
        assertEquals("mode", t.modeCode);
        assertEquals("work_state", t.stateCode);
        assertEquals(215, t.target);
        assertEquals(203, t.temp);
        assertEquals(5, t.set.step);
        assertEquals(1, t.room.scale);
        assertFalse(t.set.guessed);
        assertFalse(t.fahrenheit);
        // No switch: on.
        assertTrue(t.on);
        assertEquals("manual", t.mode);
        assertEquals(Arrays.asList("auto", "manual", "holiday"), t.modes);
        assertEquals("heating", TuyaCloud.stateKind(t.state));
        assertEquals("21.5°C", TuyaCloud.degrees(t.target, t.set, t.fahrenheit, true));
        assertEquals("20.3°C", TuyaCloud.degrees(t.temp, t.room, t.fahrenheit, true));
    }

    @Test public void bothUnitsOfferedTheOneTheDeviceShows() throws Exception {
        JSONObject spec = new JSONObject().put("functions", new JSONArray()
                .put(fn("switch", "Boolean", "{}"))
                .put(fn("temp_set", "Integer", "{\"unit\":\"℃\",\"min\":5,\"max\":35,\"scale\":0,\"step\":1}"))
                .put(fn("temp_set_f", "Integer", "{\"unit\":\"℉\",\"min\":41,\"max\":95,\"scale\":0,\"step\":1}"))
                .put(fn("temp_unit_convert", "Enum", "{\"range\":[\"c\",\"f\"]}")))
                .put("status", new JSONArray()
                        .put(fn("temp_current", "Integer", "{\"unit\":\"℃\",\"min\":0,\"max\":50,\"scale\":0,\"step\":1}"))
                        .put(fn("temp_current_f", "Integer", "{\"unit\":\"℉\",\"min\":32,\"max\":122,\"scale\":0,\"step\":1}")));
        TuyaCloud.Thermostat t = new TuyaCloud.Thermostat();
        t.spec = TuyaCloud.specCodes(spec);
        JSONArray st = new JSONArray().put(code("switch", false)).put(code("temp_set", 21)).put(code("temp_set_f", 70))
                .put(code("temp_current", 19)).put(code("temp_current_f", 66)).put(code("temp_unit_convert", "f"));
        TuyaCloud.read(t, st);
        assertEquals("temp_set_f", t.setCode);
        assertEquals("temp_current_f", t.roomCode);
        assertTrue(t.fahrenheit);
        assertEquals("70°F", TuyaCloud.degrees(t.target, t.set, t.fahrenheit, true));
        assertEquals("66°F", TuyaCloud.degrees(t.temp, t.room, t.fahrenheit, true));
        assertEquals("switch", t.switchCode);
        assertFalse(t.on);
        // No mode code: no modes, none offered.
        assertNull(t.modeCode);
        assertTrue(t.modes.isEmpty());
        // In Celsius on the device: the Celsius codes.
        TuyaCloud.read(t, new JSONArray().put(code("temp_set", 21)).put(code("temp_set_f", 70)).put(code("temp_current", 19))
                .put(code("temp_unit_convert", "c")));
        assertEquals("temp_set", t.setCode);
        assertFalse(t.fahrenheit);
        assertEquals("21°C", TuyaCloud.degrees(t.target, t.set, t.fahrenheit, true));
    }

    @Test public void withoutItsSpecificationTheRangeIsGuessed() throws Exception {
        TuyaCloud.Thermostat t = new TuyaCloud.Thermostat();
        TuyaCloud.read(t, new JSONArray().put(code("temp_set", 215)).put(code("temp_current", 198)).put(code("switch", true))
                .put(code("mode", "auto")));
        assertTrue(t.set.guessed);
        assertEquals(1, t.set.scale);
        assertEquals(5, t.set.step);
        assertEquals("19.8°C", TuyaCloud.degrees(t.temp, t.room, t.fahrenheit, true));
        assertEquals("switch", t.switchCode);
        assertEquals("mode", t.modeCode);
        // Its mode known, but not the others: no row to choose from.
        assertTrue(t.modes.isEmpty());
        // Whole degrees when the value is small.
        TuyaCloud.Thermostat w = new TuyaCloud.Thermostat();
        TuyaCloud.read(w, new JSONArray().put(code("temp_set", 21)));
        assertEquals(0, w.set.scale);
        assertEquals("21°C", TuyaCloud.degrees(w.target, w.set, w.fahrenheit, true));
        // No codes at all: nothing known, nothing to send.
        TuyaCloud.Thermostat none = new TuyaCloud.Thermostat();
        TuyaCloud.read(none, new JSONArray());
        assertNull(none.setCode);
        assertEquals(NONE, none.target);
        assertEquals(0, TuyaCloud.commands(none, 215, false, "auto").length());
    }

    @Test public void otherNamesForTheTargetAndTheRoom() {
        assertTrue(TuyaCloud.targetLike("temp_set"));
        assertTrue(TuyaCloud.targetLike("temp_set_f"));
        assertTrue(TuyaCloud.targetLike("set_temp"));
        assertTrue(TuyaCloud.targetLike("TempSet"));
        assertFalse(TuyaCloud.targetLike("temp_current"));
        assertFalse(TuyaCloud.targetLike("upper_temp"));
        assertFalse(TuyaCloud.targetLike(null));
        assertTrue(TuyaCloud.roomLike("temp_current_f"));
        assertTrue(TuyaCloud.roomLike("temp_indoor"));
        assertFalse(TuyaCloud.roomLike("temp_set"));
    }

    // ---- changing it ------------------------------------------------------------------------------------------------

    private static TuyaCloud.Thermostat wall() {
        TuyaCloud.Thermostat t = new TuyaCloud.Thermostat();
        t.id = "dev1";
        t.setCode = "temp_set";
        t.switchCode = "switch";
        t.modeCode = "mode";
        t.set = HALF;
        t.target = 210;
        t.on = true;
        t.mode = "auto";
        return t;
    }

    @Test public void onlyWhatChangedIsSent() throws Exception {
        TuyaCloud.Thermostat t = wall();
        // Pressed back to where it was (up then down, on then off): nothing to send.
        assertEquals(0, TuyaCloud.commands(t, 210, true, "auto").length());
        JSONArray one = TuyaCloud.commands(t, 225, true, "auto");
        assertEquals(1, one.length());
        assertEquals("temp_set", one.getJSONObject(0).getString("code"));
        assertEquals(225, one.getJSONObject(0).getInt("value"));
        // Out of range or between steps: sent in range and on a step.
        assertEquals(350, TuyaCloud.commands(t, 999, true, "auto").getJSONObject(0).getInt("value"));
        assertEquals(215, TuyaCloud.commands(t, 213, true, "auto").getJSONObject(0).getInt("value"));
        // All three: on or off first, then the mode, then the target.
        JSONArray all = TuyaCloud.commands(t, 230, false, "manual");
        assertEquals(3, all.length());
        assertEquals("switch", all.getJSONObject(0).getString("code"));
        assertFalse(all.getJSONObject(0).getBoolean("value"));
        assertEquals("mode", all.getJSONObject(1).getString("code"));
        assertEquals("manual", all.getJSONObject(1).getString("value"));
        assertEquals("temp_set", all.getJSONObject(2).getString("code"));
        // A device without a switch is never sent one.
        t.switchCode = null;
        assertEquals(0, TuyaCloud.commands(t, 210, false, "auto").length());
    }

    @Test public void sentOnceTheRemoteIsStill() {
        long settle = ThermostatPanel.SETTLE_MS;
        // Nothing changed: nothing to wait for.
        assertEquals(-1, ThermostatPanel.settleWait(false, 1_000, 5_000, settle));
        // Pressed 300 ms ago: 500 ms more; then it goes.
        assertEquals(500, ThermostatPanel.settleWait(true, 10_000, 10_300, settle));
        assertEquals(0, ThermostatPanel.settleWait(true, 10_000, 10_800, settle));
        assertEquals(0, ThermostatPanel.settleWait(true, 10_000, 12_000, settle));
        // A press just now starts the wait again: one command for a run of presses.
        assertEquals(settle, ThermostatPanel.settleWait(true, 12_000, 12_000, settle));
    }

    @Test public void aFreshReadingWaitsForTheDevice() {
        long keep = ThermostatPanel.KEEP_MS;
        assertTrue(ThermostatPanel.keepShown(true, false, 0, 100_000, keep));
        assertTrue(ThermostatPanel.keepShown(false, true, 0, 100_000, keep));
        // Taken a minute ago: what was sent stands (a valve reports late).
        assertTrue(ThermostatPanel.keepShown(false, false, 40_000, 100_000, keep));
        // Long since, or never sent: the reading is shown.
        assertFalse(ThermostatPanel.keepShown(false, false, 40_000, 40_000 + keep, keep));
        assertFalse(ThermostatPanel.keepShown(false, false, 0, 100_000, keep));
    }

    @Test public void tuyasRefusalInAFewWords() {
        assertEquals("device is offline", TuyaCloud.why("tuya: 2001 device is offline"));
        assertEquals("param is illegal ,please check it", TuyaCloud.why("tuya: 1109 param is illegal ,please check it"));
        assertEquals("sign invalid", TuyaCloud.why("tuya token refused: sign invalid"));
        assertEquals("", TuyaCloud.why(null));
        assertTrue(TuyaCloud.why("tuya: 1 " + new String(new char[100]).replace('\0', 'x')).length() <= 60);
    }

    @Test public void modesAndStatesInWords() {
        assertEquals("Auto", TuyaCloud.modeName("auto"));
        assertEquals("Temp auto", TuyaCloud.modeName("temp_auto"));
        assertEquals("Eco", TuyaCloud.modeName("ECO"));
        assertEquals("", TuyaCloud.modeName(null));
        assertEquals("heating", TuyaCloud.stateKind("Warming"));
        assertEquals("idle", TuyaCloud.stateKind("standby"));
        assertEquals("open", TuyaCloud.stateKind("opened"));
        assertEquals("closed", TuyaCloud.stateKind("close"));
        assertNull(TuyaCloud.stateKind("window_open"));
        assertNull(TuyaCloud.stateKind(null));
    }

    @Test public void theReportsLinesForTheSpecification() {
        assertEquals("temp_set: Integer, set, 5°C to 35°C step 0.5°C (raw 50-350 scale 1 step 5 ℃)",
                TuyaCloud.specLine("temp_set", new String[]{"Integer", "{\"unit\":\"℃\",\"min\":50,\"max\":350,\"scale\":1,\"step\":5}", "set"}));
        assertEquals("mode: Enum, set, auto / manual",
                TuyaCloud.specLine("mode", new String[]{"Enum", "{\"range\":[\"auto\",\"manual\"]}", "set"}));
        assertEquals("switch: Boolean, set", TuyaCloud.specLine("switch", new String[]{"Boolean", "{}", "set"}));
        assertEquals("valve_open_degree: Integer, read, 0 to 100 step 1 (raw 0-100 scale 0 step 1 %)",
                TuyaCloud.specLine("valve_open_degree", new String[]{"Integer", "{\"unit\":\"%\",\"min\":0,\"max\":100,\"scale\":0,\"step\":1}", "read"}));
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private static JSONObject device(String name, String category, boolean online, JSONArray status) throws Exception {
        JSONObject d = new JSONObject().put("id", "id-" + name).put("name", name).put("category", category).put("online", online);
        if (status != null) d.put("status", status);
        return d;
    }

    private static JSONArray status(String code, Object value) throws Exception {
        return new JSONArray().put(code(code, value));
    }

    private static JSONObject code(String code, Object value) throws Exception {
        return new JSONObject().put("code", code).put("value", value);
    }

    private static JSONObject fn(String code, String type, String values) throws Exception {
        return new JSONObject().put("code", code).put("type", type).put("values", values);
    }
}
