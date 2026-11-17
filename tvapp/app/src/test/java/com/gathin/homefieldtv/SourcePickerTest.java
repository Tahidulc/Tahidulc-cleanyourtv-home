package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.gathin.homefieldtv.engine.Catalog;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/** The source list's marks beside each copy: the picture's, HDR's and sound's, then the file's kind, as the phone's. */
public class SourcePickerTest {
    private static Catalog.Play play(int picture, String hdr, String sound, String kind) {
        Catalog.Play p = Catalog.previewPlay("torbox:torrent:1:1", "");
        p.picture = picture;
        p.hdr = hdr;
        p.sound = sound;
        p.kind = kind;
        return p;
    }

    @Test public void marksAsThePhoneShowsThem() {
        assertEquals(Arrays.asList("4K", "Dolby Vision", "Dolby Atmos", "MKV"),
                SourcePicker.marks(play(Catalog.PICTURE_4K, "Dolby Vision", "Dolby Atmos", "MKV")));
        // Dolby's other sound: its logo, then its channels in words.
        assertEquals(Arrays.asList("1080p", "HDR10", "Dolby", "5.1", "MP4"),
                SourcePicker.marks(play(Catalog.PICTURE_1080, "HDR10", "Dolby Digital+ 5.1", "MP4")));
        // The rest in words.
        assertEquals(Arrays.asList("720p", "DTS-HD 7.1"), SourcePicker.marks(play(Catalog.PICTURE_720, "", "DTS-HD 7.1", "")));
        assertEquals(Collections.emptyList(), SourcePicker.marks(play(0, "", "", "")));
    }

    @Test public void logosForThoseThatHaveOne() {
        for (String m : Arrays.asList("4K", "1080p", "720p", "SD", "Dolby Vision", "HDR10+", "HDR10", "HLG", "Dolby Atmos", "Dolby")) {
            assertNotEquals(m, 0, SourcePicker.logo(m));
        }
        assertEquals(0, SourcePicker.logo("HDR"));
        assertEquals(0, SourcePicker.logo("5.1"));
        assertEquals(0, SourcePicker.logo("MKV"));
    }
}
