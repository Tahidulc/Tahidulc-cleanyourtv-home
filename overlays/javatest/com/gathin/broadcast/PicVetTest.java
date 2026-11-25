package com.gathin.broadcast;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A catalogue's picture that is no programme's is passed over (PicVet): placeholders, plain cards, tiny ones, logos. */
public class PicVetTest {
    @Test public void knownPlaceholdersByTheirAddress() {
        assertTrue(PicVet.placeholderUrl("https://img.invalid/image-service/ImagesEPG/EventImages/nologo.png"));
        assertTrue(PicVet.placeholderUrl("https://img.invalid/assets/placeholder-16x9.jpg"));
        assertTrue(PicVet.placeholderUrl("https://img.invalid/default_image.jpg"));
        assertTrue(PicVet.placeholderUrl(""));
        assertFalse(PicVet.placeholderUrl("https://img.invalid/pd-image/9bdee893/16-9/640"));
    }

    @Test public void tinyPicturesAreNoProgrammes() {
        assertTrue(PicVet.tiny(1, 1));
        assertTrue(PicVet.tiny(160, 60));
        assertFalse(PicVet.tiny(240, 135));
    }

    @Test public void theChannelsOwnLogoIsNoProgrammesPicture() {
        assertTrue(PicVet.logo("https://logos.invalid/uk/harbour-one.png?v=2", "http://logos.invalid/uk/harbour-one.png"));
        assertFalse(PicVet.logo("https://img.invalid/epg/lantern.jpg", "https://logos.invalid/uk/harbour-one.png"));
        assertFalse(PicVet.logo("https://img.invalid/epg/lantern.jpg", ""));
    }

    @Test public void aPlainCardIsFlatAPictureIsNot() {
        // Sky's "Image unavailable": one light blue, a line of dark words across the middle.
        int[] card = new int[24 * 14];
        for (int i = 0; i < card.length; i++) card[i] = 0xFF5B9BD5;
        for (int x = 4; x < 20; x++) card[7 * 24 + x] = 0xFF1C3D6E;
        assertTrue(PicVet.flat(card));
        // A scene: a sky fading into hills, people.
        int[] scene = new int[24 * 14];
        for (int y = 0; y < 14; y++) {
            for (int x = 0; x < 24; x++) {
                int r = 40 + y * 12, g = 70 + x * 5, b = 160 - y * 8;
                scene[y * 24 + x] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        assertFalse(PicVet.flat(scene));
        assertTrue(PicVet.flat(new int[0]));
    }
}
