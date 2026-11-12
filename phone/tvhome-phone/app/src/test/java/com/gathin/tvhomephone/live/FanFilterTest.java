package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** What may show from a fan's post on a family's TV picture. */
public class FanFilterTest {
    @Test public void swearingAndSlursKeepAPostOff() {
        assertTrue(FanFilter.rude("What a f*cking... no, what a FUCKING goal"));
        assertTrue(FanFilter.rude("absolute shite performance"));
        assertTrue(FanFilter.rude("you bellend"));
        assertTrue(FanFilter.rude("kill   yourself"));
        assertFalse(FanFilter.rude("Scunthorpe away was a classic"));
        assertFalse(FanFilter.rude("Passing through midfield looked sharp"));
        assertFalse(FanFilter.rude("Same eleven as Saturday please"));
    }

    @Test public void onlyEnglishWords() {
        assertTrue(FanFilter.english("Same eleven as Saturday please, the midfield finally clicked"));
        assertFalse(FanFilter.english("Вперёд, красные дьяволы, мы с вами всегда"));
        assertFalse(FanFilter.english("Vamos equipo hoy ganamos seguro hermanos"));
    }

    @Test public void tidiedAndCut() {
        assertEquals("Line-ups are out & Kerr starts", FanFilter.clean("<p>Line-ups are out &amp; Kerr   starts https://x.example/abc</p>"));
        String long1 = "word ".repeat(60).trim();
        String cut = FanFilter.cut(long1);
        assertTrue(cut.length() <= FanFilter.MAX + 1);
        assertTrue(cut.endsWith("…"));
    }

    @Test public void aLinkOrPictureAloneIsNotAPost() {
        assertNull(FanFilter.show("https://example.com/watch"));
        assertNull(FanFilter.show("🔥🔥🔥"));
        assertEquals("Away end already in full voice. Nothing to lose today.", FanFilter.show("Away end already in full voice. Nothing to lose today."));
        assertNull(FanFilter.show("Away end in full voice, what a shit atmosphere at home though"));
    }

    @Test public void badLabels() {
        assertTrue(FanFilter.badLabels(Arrays.asList("porn")));
        assertTrue(FanFilter.badLabels(Arrays.asList("graphic-media")));
        assertTrue(FanFilter.badLabels(Arrays.asList("!hide")));
        assertFalse(FanFilter.badLabels(Arrays.asList("")));
        assertFalse(FanFilter.badLabels(null));
    }

    @Test public void names() {
        assertTrue(FanFilter.nameOk("Goodison Lou"));
        assertFalse(FanFilter.nameOk("shite_fc_fan"));
    }
}
