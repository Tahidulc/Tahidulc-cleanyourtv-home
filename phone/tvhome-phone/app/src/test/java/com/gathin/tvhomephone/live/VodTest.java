package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class VodTest {
    @Test public void providersTagsAndYearsAreSetAside() {
        assertEquals("matrix", Vod.key("The Matrix"));
        assertEquals("matrix", Vod.key("EN - The Matrix (1999) 4K"));
        assertEquals("matrix", Vod.key("|UK| The Matrix 1999 FHD"));
        assertEquals("matrix", Vod.key("[4K] The Matrix"));
        assertEquals("oppenheimer", Vod.key("NF: Oppenheimer (2023)"));
        assertEquals("fast and furious", Vod.key("Fast & Furious"));
        assertEquals("amelie", Vod.key("Amélie"));
        assertEquals("matrix", Vod.key("The Matrix HD"));
    }

    @Test public void aTitlesOwnFirstWordStays() {
        assertEquals("x men", Vod.key("X-Men"));
        assertEquals("spider man no way home", Vod.key("EN - Spider-Man: No Way Home"));
        assertNotEquals(Vod.key("Men"), Vod.key("X-Men"));
        assertEquals("it chapter two", Vod.key("EN - It - Chapter Two (2019)"));
        assertEquals("mad max fury road", Vod.key("EN - Mad Max: Fury Road (2015) 4K"));
        assertEquals("us", Vod.key("Us (2019)"));
        assertEquals("us", Vod.key("EN - Us 2019 HDR"));
        assertEquals(Vod.key("It Chapter Two"), Vod.key("EN - It - Chapter Two (2019)"));
    }

    @Test public void theYearIsRead() {
        assertEquals("1999", Vod.year("The Matrix (1999)"));
        assertEquals("2023", Vod.year("EN - Oppenheimer 2023 4K"));
        assertEquals("", Vod.year("The Matrix"));
        // A number that is a title's own, not a year.
        assertEquals("", Vod.year("Apollo 13"));
    }
}
