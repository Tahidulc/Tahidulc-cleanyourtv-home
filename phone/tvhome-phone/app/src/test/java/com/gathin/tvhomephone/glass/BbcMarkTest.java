package com.gathin.tvhomephone.glass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Which of the BBC's marks a story wears: by its source's name, or by its link. */
public class BbcMarkTest {
    @Test public void sourcesSayTheirSection() {
        assertEquals("SPORT", BbcMark.section("BBC Sport"));
        assertEquals("NEWS", BbcMark.section("BBC News"));
        assertEquals("SPORT", BbcMark.section(" bbc sport · football "));
        assertNull(BbcMark.section("Sky Sports News"));
        assertNull(BbcMark.section("The Harbour Times"));
        // The BBC with no section named: no mark rather than the wrong one.
        assertNull(BbcMark.section("BBC"));
        assertNull(BbcMark.section(null));
    }

    @Test public void linksSayTheirSection() {
        assertEquals("SPORT", BbcMark.sectionOfLink("https://www.bbc.co.uk/sport/football/articles/c1234"));
        assertEquals("NEWS", BbcMark.sectionOfLink("https://www.bbc.co.uk/news/articles/c5678"));
        assertEquals("NEWS", BbcMark.sectionOfLink("https://www.bbc.com/news/world-12345"));
        assertNull(BbcMark.sectionOfLink("https://www.example.com/sport/football/123"));
        assertNull(BbcMark.sectionOfLink(""));
        assertNull(BbcMark.sectionOfLink(null));
    }
}
