package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/** Homefield TV's own updates: update.json read as the phone shelf writes it, versions compared, what's new wrapped. */
public class UpdatesTest {
    private static final String SHA = "3f1a6c0e9b2d4f8a7c6e5d4c3b2a1908f7e6d5c4b3a29180706f5e4d3c2b1a09";

    private static String json(int code, String name, String file, long size, String sha) {
        return "{\"code\":" + code + ",\"name\":\"" + name + "\",\"notes\":\"Catch-up and Watch from the start\",\"file\":\"" + file
                + "\",\"size\":" + size + ",\"sha256\":\"" + sha + "\"}";
    }

    @Test public void readsTheShelfsUpdateJson() {
        Updates.Offer o = Updates.Offer.parse(json(7, "1.6", "HomefieldTV-1.6.apk", 4_344_844, SHA));
        assertNotNull(o);
        assertEquals(7, o.code);
        assertEquals("1.6", o.name);
        assertEquals("Catch-up and Watch from the start", o.notes);
        assertEquals("HomefieldTV-1.6.apk", o.file);
        assertEquals(4_344_844, o.size);
        assertEquals(SHA, o.sha256);
        // A fingerprint in capitals is the same fingerprint.
        assertEquals(SHA, Updates.Offer.parse(json(7, "1.6", "HomefieldTV-1.6.apk", 1, SHA.toUpperCase())).sha256);
        // Extra fields (the phone shelf's patches) are passed over.
        assertNotNull(Updates.Offer.parse("{\"code\":7,\"name\":\"1.6\",\"notes\":\"\",\"file\":\"a.apk\",\"size\":9,\"sha256\":\"" + SHA
                + "\",\"patches\":[]}"));
    }

    @Test public void halfAnUpdateIsNone() {
        assertNull(Updates.Offer.parse(null));
        assertNull(Updates.Offer.parse(""));
        assertNull(Updates.Offer.parse("not json"));
        assertNull(Updates.Offer.parse(json(0, "1.6", "HomefieldTV-1.6.apk", 10, SHA)));
        assertNull(Updates.Offer.parse(json(7, "", "HomefieldTV-1.6.apk", 10, SHA)));
        assertNull(Updates.Offer.parse(json(7, "1.6", "HomefieldTV-1.6.apk", 0, SHA)));
        assertNull(Updates.Offer.parse(json(7, "1.6", "HomefieldTV-1.6.apk", 10, "abc")));
        assertNull(Updates.Offer.parse(json(7, "1.6", "HomefieldTV-1.6.apk", 10, SHA.replace('f', 'g'))));
        // Only a plain APK name beside update.json: no folders, nothing hidden, nothing else.
        assertNull(Updates.Offer.parse(json(7, "1.6", "../HomefieldTV-1.6.apk", 10, SHA)));
        assertNull(Updates.Offer.parse(json(7, "1.6", "sub/HomefieldTV-1.6.apk", 10, SHA)));
        assertNull(Updates.Offer.parse(json(7, "1.6", ".HomefieldTV-1.6.apk", 10, SHA)));
        assertNull(Updates.Offer.parse(json(7, "1.6", "HomefieldTV-1.6.zip", 10, SHA)));
    }

    @Test public void offeredOnlyWhenNewerByVersionCode() {
        Updates.Offer o = Updates.Offer.parse(json(7, "1.6", "HomefieldTV-1.6.apk", 10, SHA));
        assertTrue(Updates.newer(o, 6));
        assertFalse(Updates.newer(o, 7));
        assertFalse(Updates.newer(o, 8));
        assertFalse(Updates.newer(null, 1));
    }

    @Test public void versionNamesComparePartByPart() {
        assertTrue(Updates.compareNames("1.10", "1.9") > 0);
        assertTrue(Updates.compareNames("1.5", "1.6") < 0);
        assertEquals(0, Updates.compareNames("1.5", "1.5.0"));
        assertTrue(Updates.compareNames("2.0", "1.99") > 0);
        assertTrue(Updates.compareNames("1.6", "1.6-beta") < 0 || Updates.compareNames("1.6", "1.6-beta") > 0);
        assertEquals(0, Updates.compareNames("", "0"));
    }

    @Test public void sameKeyMeansTheSameCertificates() {
        byte[] mine = {1, 2, 3}, other = {9, 9, 9};
        assertTrue(Updates.sameSigner(new byte[][]{mine.clone()}, new byte[][]{mine}));
        assertFalse(Updates.sameSigner(new byte[][]{other}, new byte[][]{mine}));
        assertFalse(Updates.sameSigner(new byte[][]{mine, other}, new byte[][]{mine}));
        assertTrue(Updates.sameSigner(new byte[][]{other, mine}, new byte[][]{mine, other}));
        assertFalse(Updates.sameSigner(null, new byte[][]{mine}));
        assertFalse(Updates.sameSigner(new byte[0][], new byte[0][]));
    }

    @Test public void whatsNewWrapsIntoLines() {
        List<String> l = Updates.lines("Catch-up on channels that keep an archive, and Watch from the start.", 30);
        assertEquals(Arrays.asList("Catch-up on channels that keep", "an archive, and Watch from the", "start."), l);
        assertEquals(Arrays.asList("One", "Two"), Updates.lines("One\nTwo", 40));
        assertTrue(Updates.lines("   ", 40).isEmpty());
        assertTrue(Updates.lines(null, 40).isEmpty());
    }

    @Test public void whenItLastLooked() {
        ZoneId london = ZoneId.of("Europe/London");
        long now = ZonedDateTime.of(2026, 11, 16, 22, 0, 0, 0, london).toInstant().toEpochMilli();
        assertEquals("Not checked yet", Updates.checked(0, now, london));
        assertEquals("Last checked today at 21:40", Updates.checked(now - 20 * 60_000L, now, london));
        assertEquals("Last checked yesterday at 08:05",
                Updates.checked(ZonedDateTime.of(2026, 11, 15, 8, 5, 0, 0, london).toInstant().toEpochMilli(), now, london));
        assertEquals("Last checked on Thu 12 Nov at 19:30",
                Updates.checked(ZonedDateTime.of(2026, 11, 12, 19, 30, 0, 0, london).toInstant().toEpochMilli(), now, london));
        assertEquals("4.1 MB", Updates.megabytes(4_344_844));
    }
}
