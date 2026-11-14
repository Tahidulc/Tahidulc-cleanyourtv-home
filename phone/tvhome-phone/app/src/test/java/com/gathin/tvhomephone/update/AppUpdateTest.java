package com.gathin.tvhomephone.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** Homefield's own updates from the TVs: what a TV offers, read safely; what is fetched for this version; when to ask. */
public class AppUpdateTest {
    private static final String SHA = "eea09be0a7b4f0beb15bd86909d5a0ab87406df5a1aa1ba2d1af58de381bba6c";

    private static JSONObject offer() throws Exception {
        return new JSONObject().put("code", 123).put("name", "4.92").put("notes", " Updates from the TVs ")
                .put("file", "Homefield-4.92.apk").put("size", 139040928L).put("sha256", SHA)
                .put("patches", new JSONArray()
                        .put(new JSONObject().put("from", 122).put("file", "Homefield-122-123.patch").put("bytes", 2488616))
                        .put(new JSONObject().put("from", 120).put("file", "../Homefield-120-123.patch").put("bytes", 4793376))
                        .put(new JSONObject().put("from", 0).put("file", "x.patch")));
    }

    @Test public void aTvsOfferIsReadWhole() throws Exception {
        AppUpdate.Offer o = AppUpdate.Offer.parse(offer());
        assertNotNull(o);
        assertEquals(123, o.code);
        assertEquals("4.92", o.name);
        assertEquals("Updates from the TVs", o.notes);
        assertEquals("Homefield-4.92.apk", o.file);
        assertEquals(139040928L, o.size);
        // Only patches with a plain name and a real version are kept.
        assertEquals(1, o.patches.size());
        assertEquals("Homefield-122-123.patch", o.patchFrom(122));
    }

    @Test public void aBrokenOfferIsNoOffer() throws Exception {
        assertNull(AppUpdate.Offer.parse(null));
        assertNull(AppUpdate.Offer.parse(new JSONObject()));
        assertNull(AppUpdate.Offer.parse(offer().put("code", 0)));
        assertNull(AppUpdate.Offer.parse(offer().put("name", "")));
        assertNull(AppUpdate.Offer.parse(offer().put("file", "../../base.apk")));
        assertNull(AppUpdate.Offer.parse(offer().put("file", "")));
        assertNull(AppUpdate.Offer.parse(offer().put("size", 0)));
        assertNull(AppUpdate.Offer.parse(offer().put("sha256", "abc")));
        assertNull(AppUpdate.Offer.parse(offer().put("sha256", SHA.replace('e', 'z'))));
    }

    @Test public void onlyWhatChangedIsFetchedWhenTheTvHasItForThisVersion() throws Exception {
        AppUpdate.Offer o = AppUpdate.Offer.parse(offer());
        assertEquals("Homefield-122-123.patch", o.patchFrom(122));
        assertEquals(2488616L, o.bytesFor(122));
        // Any other version: the whole APK.
        assertNull(o.patchFrom(121));
        assertNull(o.patchFrom(0));
        assertNull(o.patchFrom(Long.MAX_VALUE));
        assertEquals(139040928L, o.bytesFor(121));
    }

    @Test public void whereItIsKeptWithIt() throws Exception {
        AppUpdate.Offer o = AppUpdate.Offer.parse(offer());
        o.tv = "Bedroom TV";
        o.host = "192.168.1.50";
        o.port = 47820;
        AppUpdate.Offer back = AppUpdate.Offer.kept(new JSONObject(o.json().toString()));
        assertNotNull(back);
        assertEquals("Bedroom TV", back.tv);
        assertEquals("192.168.1.50", back.host);
        assertEquals(47820, back.port);
        assertEquals(SHA, back.sha256);
        assertEquals("Homefield-122-123.patch", back.patchFrom(122));
        assertEquals(2488616L, back.bytesFor(122));
        // Without where it is, it cannot be fetched.
        assertNull(AppUpdate.Offer.kept(offer()));
    }

    @Test public void onlyPlainNamesAreAskedFor() {
        assertTrue(AppUpdate.plainName("Homefield-4.92.apk"));
        assertTrue(AppUpdate.plainName("update.json"));
        assertFalse(AppUpdate.plainName(null));
        assertFalse(AppUpdate.plainName(".."));
        assertFalse(AppUpdate.plainName("a/b"));
        assertFalse(AppUpdate.plainName("a?b"));
        assertFalse(AppUpdate.plainName(".hidden"));
    }

    @Test public void theTvsAreLookedAtNowAndThenAndAskedAboutOnceADay() {
        long hour = 3_600_000L, now = 100 * hour;
        assertTrue(AppUpdate.due(now, 0, AppUpdate.OPEN_EVERY_MS));
        assertFalse(AppUpdate.due(now, now - 10 * 60_000L, AppUpdate.OPEN_EVERY_MS));
        assertTrue(AppUpdate.due(now, now - 31 * 60_000L, AppUpdate.OPEN_EVERY_MS));
        assertFalse(AppUpdate.due(now, now - 2 * hour, AppUpdate.BACKGROUND_EVERY_MS));
        assertTrue(AppUpdate.due(now, now - 3 * hour, AppUpdate.BACKGROUND_EVERY_MS));
        // A clock set back: looked at again.
        assertTrue(AppUpdate.due(now, now + hour, AppUpdate.OPEN_EVERY_MS));

        assertTrue(AppUpdate.askNow(123, 0, 0, now));
        assertFalse(AppUpdate.askNow(123, 123, now - hour, now));
        assertTrue(AppUpdate.askNow(123, 123, now - 25 * hour, now));
        assertTrue(AppUpdate.askNow(124, 123, now - hour, now));
    }

    @Test public void sizesForPeople() {
        assertEquals("2.4 MB", AppUpdate.megabytes(2488616));
        assertEquals("0.1 MB", AppUpdate.megabytes(1000));
        assertEquals("133 MB", AppUpdate.megabytes(139040928L));
        assertTrue(AppUpdate.hex64(SHA));
        assertFalse(AppUpdate.hex64(SHA.substring(1)));
    }
}
