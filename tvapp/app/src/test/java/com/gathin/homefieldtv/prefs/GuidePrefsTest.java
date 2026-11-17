package com.gathin.homefieldtv.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;

/** Settings → Guide: more guides, the time shift, and how often the guide and playlists are read again. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GuidePrefsTest {
    @Test public void asItCame() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals(6, GuidePrefs.guideHours(c));
        assertEquals(24, GuidePrefs.listsHours(c));
        assertFalse(GuidePrefs.atStart(c));
        assertEquals(0, GuidePrefs.shiftMin(c));
        assertTrue(GuidePrefs.extra(c).isEmpty());
        assertEquals(GuidePrefs.GUIDE_NAMES.length, GuidePrefs.GUIDE_HOURS.length);
        assertEquals(GuidePrefs.LISTS_NAMES.length, GuidePrefs.LISTS_HOURS.length);
        // Something stored that isn't a choice: as it came.
        GuidePrefs.setGuideHours(c, 5);
        assertEquals(6, GuidePrefs.guideHours(c));
        GuidePrefs.setShiftMin(c, 45);
        assertEquals(0, GuidePrefs.shiftMin(c));
        GuidePrefs.setShiftMin(c, -90);
        assertEquals(0, GuidePrefs.shiftMin(c));
        GuidePrefs.setShiftMin(c, 120);
        assertEquals(7_200_000L, GuidePrefs.shiftMs(c));
    }

    @Test public void dueByTheHours() {
        long h = 3600_000L;
        assertFalse(GuidePrefs.due(6, 0, 6 * h));
        assertTrue(GuidePrefs.due(6, 0, 6 * h + 1));
        // Only with Refresh now: never by itself.
        assertFalse(GuidePrefs.due(0, 0, 1000 * h));
        Context c = RuntimeEnvironment.getApplication();
        GuidePrefs.setListsHours(c, 0);
        assertFalse(GuidePrefs.listsDue(c, 0, 1000 * h));
        GuidePrefs.setGuideHours(c, 1);
        assertTrue(GuidePrefs.guideDue(c, 0, 2 * h));
    }

    @Test public void moreGuides() {
        Context c = RuntimeEnvironment.getApplication();
        assertFalse(GuidePrefs.addExtra(c, "epg.example.invalid/guide.xml"));
        assertFalse(GuidePrefs.addExtra(c, "http://a b.invalid/x.xml"));
        assertTrue(GuidePrefs.addExtra(c, "https://epg.example.invalid/uk.xml.gz"));
        assertFalse(GuidePrefs.addExtra(c, "https://epg.example.invalid/uk.xml.gz"));
        assertTrue(GuidePrefs.addExtra(c, "http://other.example.invalid/x.xml"));
        assertEquals(2, GuidePrefs.extra(c).size());
        // The playlists' own first, then the family's, each once.
        assertEquals("http://p.invalid/g.xml http://other.example.invalid/x.xml https://epg.example.invalid/uk.xml.gz",
                GuidePrefs.guides(c, "http://p.invalid/g.xml http://other.example.invalid/x.xml"));
        assertEquals("https://epg.example.invalid/uk.xml.gz http://other.example.invalid/x.xml", GuidePrefs.guides(c, ""));
        GuidePrefs.removeExtra(c, 0);
        assertEquals(Collections.singletonList("http://other.example.invalid/x.xml"), GuidePrefs.extra(c));
        for (int i = 0; i < 20; i++) GuidePrefs.addExtra(c, "http://g" + i + ".example.invalid/x.xml");
        assertEquals(GuidePrefs.MAX_EXTRA, GuidePrefs.extra(c).size());
        assertEquals("a.invalid", GuidePrefs.join("a.invalid", Arrays.asList("", "a.invalid")));
    }

    @Test public void shownByItsServerOnly() {
        assertEquals("epg.example.invalid", GuidePrefs.host("http://user:secret@epg.example.invalid:8080/xmltv.php?username=u&password=p"));
        assertEquals("epg.example.invalid", GuidePrefs.host("https://epg.example.invalid/guide.xml.gz"));
        assertEquals("A guide", GuidePrefs.host("http:///x.xml"));
    }

    @Test public void theShiftInWords() {
        assertEquals("None", GuidePrefs.shiftWords(0));
        assertEquals("+1 hour", GuidePrefs.shiftWords(60));
        assertEquals("−2 hours", GuidePrefs.shiftWords(-120));
        assertEquals("+30 minutes", GuidePrefs.shiftWords(30));
        // 12 hours either way, an hour apart, and half an hour either way.
        assertEquals(27, GuidePrefs.SHIFTS.length);
        assertEquals(-720, GuidePrefs.SHIFTS[0]);
        assertEquals(720, GuidePrefs.SHIFTS[GuidePrefs.SHIFTS.length - 1]);
        assertEquals(0, GuidePrefs.SHIFTS[13]);
    }
}
