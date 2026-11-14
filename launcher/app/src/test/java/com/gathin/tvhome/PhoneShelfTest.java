package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Homefield's shelf on the TV: only plain names given out, and a broken download picked up where it stopped. */
public class PhoneShelfTest {
    @Test public void onlyPlainNamesAreGivenOut() {
        assertTrue(PhoneShelf.plainName("update.json"));
        assertTrue(PhoneShelf.plainName("Homefield-4.92.apk"));
        assertTrue(PhoneShelf.plainName("Homefield-122-123.patch"));
        assertFalse(PhoneShelf.plainName(null));
        assertFalse(PhoneShelf.plainName(""));
        assertFalse(PhoneShelf.plainName(".."));
        assertFalse(PhoneShelf.plainName(".hidden"));
        assertFalse(PhoneShelf.plainName("../updates/update.json"));
        assertFalse(PhoneShelf.plainName("a/b.apk"));
        assertFalse(PhoneShelf.plainName("a%2Fb.apk"));
        assertFalse(PhoneShelf.plainName("a b.apk"));
        assertFalse(PhoneShelf.plainName("x".repeat(97)));
    }

    @Test public void aBrokenDownloadPicksUpWhereItStopped() {
        assertEquals(0, PhoneShelf.rangeStart(null, 100));
        assertEquals(0, PhoneShelf.rangeStart("", 100));
        assertEquals(0, PhoneShelf.rangeStart("bytes=0-", 100));
        assertEquals(42, PhoneShelf.rangeStart("bytes=42-", 100));
        assertEquals(99, PhoneShelf.rangeStart("Bytes=99-", 100));
        assertEquals(-1, PhoneShelf.rangeStart("bytes=100-", 100));
        assertEquals(-1, PhoneShelf.rangeStart("bytes=0-10", 100));
        assertEquals(-1, PhoneShelf.rangeStart("bytes=-10", 100));
        assertEquals(-1, PhoneShelf.rangeStart("bytes=1-,5-", 100));
        assertEquals(-1, PhoneShelf.rangeStart("items=1-", 100));
        assertEquals(-1, PhoneShelf.rangeStart("bytes=x-", 100));
    }
}
