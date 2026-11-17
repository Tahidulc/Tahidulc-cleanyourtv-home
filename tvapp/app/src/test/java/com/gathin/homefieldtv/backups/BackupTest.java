package com.gathin.homefieldtv.backups;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.Parental;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

/**
 * Settings → Backup: everything set on the TV made into one file and put back as it was (each value with its own type),
 * on this TV or another, with what other parts keep in memory read afresh.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class BackupTest {
    private Context c;

    @Before public void fresh() {
        c = RuntimeEnvironment.getApplication();
        Parental.forget();
        Categories.forget();
        Reminders.forget();
    }

    private SharedPreferences p(String name) {
        return c.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    @Test public void roundTrip() {
        // The family's TV: playlists (made-up logins), favourites, recents, reminders, settings of each kind of value.
        p("live_sources").edit().putString("list", "[{\"id\":\"p0\",\"name\":\"Home\",\"type\":\"xtream\",\"server\":\"http://example.invalid\","
                + "\"user\":\"someone\",\"pass\":\"not-a-real-one\"}]").putString("active", "").commit();
        p("live_tv").edit().putString("favourites", "[\"x:1\",\"x:2\"]").putString("recent", "x:2,x:1").putString("last", "x:2").commit();
        p("reminders").edit().putString("list", "[{\"key\":\"x:1\",\"channel\":\"One\",\"logo\":\"\",\"title\":\"News\",\"start\":4102444800000,"
                + "\"end\":4102446600000}]").commit();
        p("player").edit().putInt("buffer_live", 3).putBoolean("afr", true).putFloat("speed", 1.25f).putLong("seen", 1234567890123L).commit();
        p("live_groups").edit().putStringSet("hidden", new HashSet<>(Arrays.asList("Adult", "Radio"))).putString("order", "[\"Kids\"]").commit();
        Parental.setPin(c, "2468");
        Parental.lockGroup(c, "Movies", true);

        String text = Backup.make(c, "1.8", 1_700_000_000_000L);
        // Nothing but settings files in it, none it doesn't know.
        Backup.Contents b = Backup.read(text);
        assertNotNull(b);
        assertEquals(1_700_000_000_000L, b.made);
        assertEquals("1.8", b.appVersion);
        assertArrayEquals(new int[]{1, 2, 1}, Backup.counts(b));

        // Another TV, or this one after a mistake: everything changed or gone.
        for (String f : Backup.FILES) p(f).edit().clear().commit();
        p("player").edit().putInt("buffer_live", 0).putBoolean("extra", true).commit();
        p("live_tv").edit().putString("favourites", "[\"x:9\"]").commit();
        Parental.forget();
        assertFalse(Parental.hasPin(c));

        BackupStore.restore(c, new BackupStore.Kept(BackupStore.LAST, Backup.read(text)));
        Map<String, ?> player = p("player").getAll();
        assertEquals(3, player.get("buffer_live"));
        assertEquals(true, player.get("afr"));
        assertEquals(1.25f, (Float) player.get("speed"), 0.0001f);
        assertEquals(1234567890123L, player.get("seen"));
        // A file the backup has is emptied first: what wasn't in it isn't left behind.
        assertFalse(player.containsKey("extra"));
        assertEquals("[\"x:1\",\"x:2\"]", p("live_tv").getString("favourites", ""));
        assertEquals("x:2", p("live_tv").getString("last", ""));
        assertEquals(new HashSet<>(Arrays.asList("Adult", "Radio")), p("live_groups").getStringSet("hidden", null));
        assertTrue(p("live_sources").getString("list", "").contains("someone"));
        // Read afresh after the restore: the PIN, the locks, the categories, the reminders.
        assertTrue(Parental.hasPin(c));
        assertTrue(Parental.check(c, "2468"));
        assertTrue(Parental.lockedGroups(c).contains("Movies"));
        assertTrue(Categories.isHidden(c, "Adult"));
        assertEquals(1, Reminders.list(c, 4102444700000L).size());
    }

    @Test public void onlyItsOwnFiles() {
        assertNull(Backup.read(null));
        assertNull(Backup.read("not json"));
        assertNull(Backup.read("{\"app\":\"someone-else\",\"kind\":\"backup\",\"v\":1,\"prefs\":{}}"));
        assertNull(Backup.read("{\"app\":\"homefieldtv\",\"kind\":\"backup\",\"v\":99,\"prefs\":{}}"));
        assertNull(Backup.read("{\"app\":\"homefieldtv\",\"kind\":\"backup\",\"v\":1}"));
        // A settings file it doesn't know is never written.
        Backup.Contents b = Backup.read("{\"app\":\"homefieldtv\",\"kind\":\"backup\",\"v\":1,\"made\":5,"
                + "\"prefs\":{\"app_update\":{\"x\":[\"s\",\"y\"]},\"look\":{\"theme\":[\"i\",2],\"bad\":[\"q\",1],\"short\":[\"i\"]}}}");
        assertNotNull(b);
        assertEquals(1, Backup.restore(c, b));
        assertFalse(p("app_update").contains("x"));
        assertEquals(2, p("look").getInt("theme", 0));
        assertFalse(p("look").contains("bad"));
        assertFalse(p("look").contains("short"));
    }

    @Test public void keptCopies() {
        assertNull(BackupStore.kept(c, BackupStore.PHONE));
        assertFalse(BackupStore.keepFromPhone(c, "{\"app\":\"nope\"}", "a phone"));
        String text = Backup.make(c, "1.8", 42L);
        assertTrue(BackupStore.keepFromPhone(c, text, "a phone"));
        BackupStore.Kept k = BackupStore.kept(c, BackupStore.PHONE);
        assertNotNull(k);
        assertEquals(42L, k.contents.made);
        assertEquals("a phone", k.contents.from);
    }
}
