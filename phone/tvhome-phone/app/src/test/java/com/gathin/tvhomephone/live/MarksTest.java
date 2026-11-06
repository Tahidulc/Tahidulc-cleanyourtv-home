package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.List;

/** The picture's and sound's marks: worded boxes, and the logos of the formats that have them. */
public class MarksTest {
    private static String words(List<Marks.Mark> marks) {
        StringBuilder b = new StringBuilder();
        for (Marks.Mark m : marks) b.append(b.length() == 0 ? "" : " ").append(m);
        return b.toString();
    }

    @Test public void thePictureIsWordedAsTheTvsDo() {
        assertEquals("4K UHD", Marks.picture("4K", true));
        assertEquals("1080p HD", Marks.picture("1080p", true));
        assertEquals("720p HD", Marks.picture("720p", false));
        assertEquals("SD", Marks.picture("SD", false));
        // Not read yet: HD when the player says so, else nothing.
        assertEquals("HD", Marks.picture("", true));
        assertEquals("", Marks.picture(null, false));
    }

    @Test public void hdrFormatsWithLogosShowThem() {
        assertEquals("[Dolby Vision]", Marks.hdr("Dolby Vision").toString());
        assertEquals("[HDR10]", Marks.hdr("HDR10").toString());
        assertEquals("[HDR10 plus]", Marks.hdr("HDR10+").toString());
        assertEquals("HLG", Marks.hdr("HLG").toString());
        assertNull(Marks.hdr(""));
        assertNull(Marks.hdr(null));
    }

    @Test public void dolbySoundIsItsLogoThenItsChannels() {
        assertEquals("[Dolby Atmos]", words(Marks.sound("Dolby Atmos")));
        assertEquals("[Dolby Digital 5.1] 5.1", words(Marks.sound("Dolby Digital 5.1")));
        assertEquals("[Dolby Digital+ 7.1] 7.1", words(Marks.sound("Dolby Digital+ 7.1")));
        assertEquals("[Dolby Digital Stereo] Stereo", words(Marks.sound("Dolby Digital Stereo")));
        assertEquals("[Dolby TrueHD]", words(Marks.sound("Dolby TrueHD")));
        // Everything else in words.
        assertEquals("AAC Stereo", words(Marks.sound("AAC Stereo")));
        assertEquals("DTS 5.1", words(Marks.sound("DTS 5.1")));
        assertEquals("", words(Marks.sound("")));
    }

    @Test public void theChannelsAreReadOffTheEnd() {
        assertEquals("5.1", Marks.channels("Dolby Digital+ 5.1"));
        assertEquals("Mono", Marks.channels("Dolby Digital Mono"));
        assertEquals("10ch", Marks.channels("Dolby AC-4 10ch"));
        assertEquals("", Marks.channels("Dolby Atmos"));
        assertEquals("", Marks.channels(null));
    }

    @Test public void aStreamsMarksComeInTheTvsOrder() {
        List<Marks.Mark> m = Marks.of("4K", true, "Dolby Vision", "Dolby Atmos", true);
        assertEquals("4K UHD [Dolby Vision] [Dolby Atmos] S", words(m));
        assertEquals("4K Ultra HD, Dolby Vision, Dolby Atmos, Subtitles", Marks.spoken(m));
        assertEquals("1080p HD [Dolby Digital 5.1] 5.1", words(Marks.of("1080p", true, "", "Dolby Digital 5.1", false)));
        assertEquals("HD", words(Marks.of("", true, null, null, false)));
        assertEquals("", words(Marks.of("", false, "", "", false)));
    }
}
