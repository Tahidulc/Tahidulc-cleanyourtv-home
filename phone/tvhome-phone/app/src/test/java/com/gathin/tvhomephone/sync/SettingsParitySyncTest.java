package com.gathin.tvhomephone.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.tvhomephone.live.ChannelEdits;
import com.gathin.tvhomephone.live.StartOn;
import com.gathin.tvhomephone.live.Times;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The new settings kept on the phone and passed to the family's other phones and tablets: their defaults (24-hour times,
 * one row per channel, playlist order, Live TV to open on), the old Open on Live TV switch carried over, and the
 * sync's items for them, written on another device and read back in memory there.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SettingsParitySyncTest {
    private Context c;

    @Before public void fresh() {
        c = RuntimeEnvironment.getApplication();
        for (String p : new String[]{"look", "live_edits", "start"}) c.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        ChannelEdits.synced(c);
    }

    private static JSONObject item(String key, Object v, String t) throws Exception {
        return new JSONObject().put("type", "setting").put("key", key).put("data", SyncSettingsList.data(v, t));
    }

    @Test public void theDefaultsAreHomefieldTvs() {
        assertTrue(Times.h24());
        assertTrue(ChannelEdits.merging(c));
        assertEquals(0, ChannelEdits.sort(c));
        assertEquals(StartOn.LIVE, StartOn.get(c));
    }

    @Test public void theOldSwitchOffOpensOnHomeAndAChoiceIsKept() {
        c.getSharedPreferences("start", Context.MODE_PRIVATE).edit().putBoolean("open_live", false).commit();
        assertEquals(StartOn.HOME, StartOn.get(c));
        c.getSharedPreferences("start", Context.MODE_PRIVATE).edit().putInt("start_on", StartOn.WATCH).commit();
        assertEquals(StartOn.WATCH, StartOn.get(c));
    }

    @Test public void theNewSettingsHaveNamesEveryDeviceKnows() {
        Set<String> names = new HashSet<>();
        for (String[] s : SyncSettingsList.PHONE) assertTrue("each once: " + s[0], names.add(s[0]));
        for (String n : Arrays.asList("times.24h", "look.progress", "channels.merge", "channels.sort")) {
            assertTrue(n, names.contains(n));
            assertTrue(n, SyncSettingsList.HELD.contains(n));
        }
    }

    @Test public void settingsFromAnotherDeviceAreTakenInAndShowAtOnce() throws Exception {
        SyncSettingsList.write(c, Arrays.asList(item("times.24h", false, "b"), item("channels.merge", false, "b"),
                item("channels.sort", 2, "i"), item("look.progress", false, "b")));
        assertFalse(Times.h24());
        assertFalse(ChannelEdits.merging(c));
        assertEquals(2, ChannelEdits.sort(c));
        assertFalse(c.getSharedPreferences("look", Context.MODE_PRIVATE).getBoolean("progress", true));
        // And read back as this device's to pass on, with their types.
        Map<String, JSONObject> mine = SyncSettingsList.read(c);
        assertEquals(false, mine.get("times.24h").get("v"));
        assertEquals("b", mine.get("times.24h").getString("t"));
        assertEquals(2, mine.get("channels.sort").getInt("v"));
        assertEquals("i", mine.get("channels.sort").getString("t"));
        assertEquals(false, mine.get("channels.merge").get("v"));
    }

    @Test public void aSettingOfTheWrongTypeIsLeftAlone() throws Exception {
        SyncSettingsList.write(c, Arrays.asList(item("channels.sort", "by name", "s")));
        assertEquals(0, ChannelEdits.sort(c));
        assertFalse(c.getSharedPreferences("live_edits", Context.MODE_PRIVATE).contains("sort"));
    }
}
