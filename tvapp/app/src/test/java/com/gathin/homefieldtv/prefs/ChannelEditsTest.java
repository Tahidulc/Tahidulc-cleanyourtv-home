package com.gathin.homefieldtv.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.homefieldtv.engine.Channel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** The family's edits to their channels, kept as compact records by channel key (ChannelEdits), and a group's own sorting. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ChannelEditsTest {
    private Context c;

    @Before public void fresh() {
        c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences(ChannelEdits.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences(Categories.PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        ChannelEdits.forget();
        Categories.forget();
    }

    @After public void after() {
        fresh();
    }

    private static Channel ch(String key, String name, String group) {
        return new Channel(key, name, group, "", "http://provider.example/" + key, "", null, null, 0);
    }

    @Test public void recordsAreCompactLinesByKey() {
        Map<String, ChannelEdits.Rec> m = new LinkedHashMap<>();
        m.put("x:101", new ChannelEdits.Rec(true, -1, "", ""));
        m.put("p1:x:7", new ChannelEdits.Rec(false, 3, "My\tOwn\nName", "countries/united-kingdom/bbc-one-uk.png"));
        m.put("x:9", new ChannelEdits.Rec(false, -1, "", ChannelEdits.LOGO_PLAYLIST));
        m.put("x:nothing", new ChannelEdits.Rec(false, -1, "", ""));
        String text = ChannelEdits.write(m);
        assertEquals("x:101\t1\t\t\t\np1:x:7\t\t3\tMy Own Name\tcountries/united-kingdom/bbc-one-uk.png\nx:9\t\t\t\t-\n", text);
        Map<String, ChannelEdits.Rec> back = ChannelEdits.parse(text + "broken line\n\n");
        assertEquals(3, back.size());
        assertTrue(back.get("x:101").hidden);
        assertEquals(3, back.get("p1:x:7").place);
        assertEquals("My Own Name", back.get("p1:x:7").name);
        assertEquals(ChannelEdits.LOGO_PLAYLIST, back.get("x:9").logo);
        assertTrue(ChannelEdits.parse("").isEmpty());
        assertTrue(ChannelEdits.parse(null).isEmpty());
    }

    @Test public void aNameIsTidied() {
        assertEquals("Film Night", ChannelEdits.tidy("  Film \t Night \n"));
        assertEquals("", ChannelEdits.tidy(null));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 80; i++) b.append('a');
        assertEquals(ChannelEdits.NAME_MAX, ChannelEdits.tidy(b.toString()).length());
    }

    @Test public void editsAreKeptAndReadBack() {
        Channel kestrel = ch("x:1", "UK: KESTREL ONE FHD", "Entertainment"), north = ch("x:2", "North Sports 1", "Sports");
        assertEquals("UK: KESTREL ONE FHD", ChannelEdits.name(kestrel));
        int v = Shown.version();
        ChannelEdits.rename(c, kestrel, "Kestrel One");
        assertTrue(Shown.version() > v);
        assertEquals("Kestrel One", ChannelEdits.name(kestrel));
        assertTrue(ChannelEdits.renamed(kestrel));
        // Its playlist's own name (or nothing) is no name of the family's.
        ChannelEdits.rename(c, kestrel, "UK: KESTREL ONE FHD");
        assertFalse(ChannelEdits.renamed(kestrel));
        ChannelEdits.rename(c, kestrel, "Kestrel One");
        ChannelEdits.setHidden(c, north.key, true);
        ChannelEdits.setLogo(c, north.key, ChannelEdits.LOGO_PLAYLIST);
        ChannelEdits.order(c, Arrays.asList("x:3", "x:2", "x:1"));
        // Read again from the TV's settings (a backup put back): the same.
        ChannelEdits.forget();
        ChannelEdits.load(c);
        assertEquals("Kestrel One", ChannelEdits.name(kestrel));
        assertTrue(ChannelEdits.hidden(north.key));
        assertEquals(ChannelEdits.LOGO_PLAYLIST, ChannelEdits.logo(north.key));
        assertEquals(Arrays.asList(north.key), ChannelEdits.hiddenKeys(c));
        ChannelEdits.Snapshot s = ChannelEdits.snapshot(c);
        assertEquals(1, s.place(north.key));
        assertEquals(0, s.place("x:3"));
        assertEquals(-1, s.place("x:9"));
        assertTrue(s.hidden(north.key));
        assertEquals("Kestrel One", s.name(kestrel.key));
        assertNull(s.name(north.key));
        // The same moment until something the lists show changes.
        assertSame(s, ChannelEdits.snapshot(c));
        ChannelEdits.showAll(c);
        assertNotSame(s, ChannelEdits.snapshot(c));
        assertFalse(ChannelEdits.hidden(north.key));
        assertTrue(ChannelEdits.hiddenKeys(c).isEmpty());
        // A record with nothing left in it goes.
        ChannelEdits.setLogo(c, north.key, "");
        ChannelEdits.rename(c, kestrel, "");
        ChannelEdits.order(c, Arrays.asList("x:3"));
        assertTrue(ChannelEdits.write(new LinkedHashMap<>(ChannelEdits.records())).contains("x:2\t\t1\t\t\n"));
    }

    @Test public void sortingForAllAndForAGroup() {
        assertEquals(ChannelEdits.PLAYLIST, ChannelEdits.sort(c));
        ChannelEdits.setSort(c, ChannelEdits.NAME);
        assertEquals(ChannelEdits.NAME, ChannelEdits.sort(c));
        assertEquals(-1, Categories.sortOf(c, "Sports"));
        Categories.setSort(c, "Sports", ChannelEdits.MANUAL);
        assertEquals(ChannelEdits.MANUAL, Categories.sortOf(c, "Sports"));
        ChannelEdits.Snapshot s = ChannelEdits.snapshot(c);
        assertEquals(ChannelEdits.MANUAL, s.sortOf("Sports"));
        assertEquals(ChannelEdits.NAME, s.sortOf("News"));
        assertTrue(s.sorts());
        // Read again: the same.
        Categories.forget();
        ChannelEdits.forget();
        assertEquals(ChannelEdits.MANUAL, Categories.sortOf(c, "Sports"));
        assertEquals(ChannelEdits.NAME, ChannelEdits.sort(c));
        Categories.setSort(c, "Sports", -1);
        assertEquals(-1, Categories.sortOf(c, "Sports"));
        // Channel names editor: on as it comes.
        assertTrue(ChannelEdits.editor(c));
        ChannelEdits.setEditor(c, false);
        assertFalse(ChannelEdits.editor(c));
    }

    @Test public void theGroupsShownPutInANewOrderHiddenOnesKeepTheirPlaces() {
        assertEquals(Arrays.asList("C", "h", "A", "B"), Categories.placed(Arrays.asList("A", "h", "B", "C"), Arrays.asList("C", "A", "B")));
        assertEquals(Arrays.asList("B", "A"), Categories.placed(Arrays.asList("A", "B"), Arrays.asList("B", "A")));
        Categories.setHidden(c, "h", true);
        Categories.placeShown(c, Arrays.asList("A", "h", "B", "C"), Arrays.asList("C", "A", "B"));
        assertEquals(Arrays.asList("C", "A", "B"), Categories.arranged(c, Arrays.asList("A", "h", "B", "C")));
        assertEquals(Arrays.asList("C", "h", "A", "B"), Categories.every(c, Arrays.asList("A", "h", "B", "C")));
        Categories.hideAll(c, Arrays.asList("A", "B", "C"));
        assertTrue(Categories.arranged(c, Arrays.asList("A", "h", "B", "C")).isEmpty());
        Categories.showAll(c);
        assertEquals(4, Categories.arranged(c, Arrays.asList("A", "h", "B", "C")).size());
    }
}
