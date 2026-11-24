package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The programme's picture behind the folded band over the guide (PinBackdrop, with Pin and Fold): when it shows, what it
 * falls back to, when it changes, and where its shade is strongest.
 */
public class PinBackdropTest {
    @Test public void noneWhileTheHeadIsOpenAllOfItOnceSmall() {
        // Open, and the head folding away under the picture (not shrunk yet): the page as it was.
        assertEquals(0f, PinBackdrop.alpha(0f), 0f);
        assertEquals(0f, PinBackdrop.alpha(-0.3f), 0f);
        assertEquals(0f, PinBackdrop.alpha(PinBackdrop.FROM), 0f);
        // All of it by the time the channel beside the small picture is all there (a phone's), and on to small.
        assertEquals(1f, PinBackdrop.alpha(PinBackdrop.FULL_AT), 1e-6f);
        assertEquals(1f, PinBackdrop.alpha(1f), 0f);
        assertEquals(1f, PinBackdrop.alpha(1.2f), 0f);
        assertTrue(PinBackdrop.FULL_AT <= Pin.INFO_FULL_AT);
        // Half way: half; and only ever more as it shrinks (less as it grows again).
        assertEquals(0.5f, PinBackdrop.alpha((PinBackdrop.FROM + PinBackdrop.FULL_AT) / 2f), 1e-6f);
        float was = 0f;
        for (float by = 0f; by <= 1f; by += 0.01f) {
            float a = PinBackdrop.alpha(by);
            assertTrue(a >= was && a >= 0f && a <= 1f);
            was = a;
        }
    }

    @Test public void aTabletsGlassGivesWayOnlyToSomething() {
        // Something to show: the glass goes as it comes in.
        assertEquals(1f, Pin.glassAlpha(0f, true), 0f);
        assertEquals(0.25f, Pin.glassAlpha(0.75f, true), 1e-6f);
        assertEquals(0f, Pin.glassAlpha(1f, true), 0f);
        // Still looked for (or nothing to colour): the glass stays, as it was.
        assertEquals(1f, Pin.glassAlpha(1f, false), 0f);
    }

    @Test public void thePicturesOwnColourElseTheChannelsElseNothing() {
        // The picture's own colour (faces that can't be whole there), made opaque.
        assertEquals(0xFF203040, PinBackdrop.plainColour(ProgGround.COLOUR, 0x00203040, 0xFFE4002B, 0xFFB00020));
        // No picture: the channel's logo colour, as the panel has it (its top).
        assertEquals(0xFFE4002B, PinBackdrop.plainColour(ProgGround.CHANNEL, 0, 0xFFE4002B, 0xFFB00020));
        // Behind logos None: nothing to colour (the page's ground, as before).
        assertEquals(0, PinBackdrop.plainColour(ProgGround.CHANNEL, 0, 0, 0));
        // Looked for, or a picture: no plain colour.
        assertEquals(0, PinBackdrop.plainColour(ProgGround.WAIT, 0x203040, 0xFFE4002B, 0xFFB00020));
        assertEquals(0, PinBackdrop.plainColour(ProgGround.PICTURE, 0x203040, 0xFFE4002B, 0xFFB00020));
    }

    @Test public void whichGroundForWhatIsKnown() {
        ProgPics.Made colourOnly = new ProgPics.Made(null, null, 0xFF203040, true, 0f, 100f);
        // Looked for (ProgArt), or being made (ProgPics): nothing yet.
        assertEquals(ProgGround.WAIT, ProgGround.of(null, null));
        assertEquals(ProgGround.WAIT, ProgGround.of("https://img.invalid/a.jpg", null));
        // No picture, or one that can't be read: the channel's own.
        assertEquals(ProgGround.CHANNEL, ProgGround.of("", null));
        assertEquals(ProgGround.CHANNEL, ProgGround.of("https://img.invalid/a.jpg", ProgPics.NONE));
        // Made as its colour only.
        assertEquals(ProgGround.COLOUR, ProgGround.of("https://img.invalid/a.jpg", colourOnly));
    }

    @Test public void changesAtTheEndOfWhatIsOn() {
        Guide.Builder b = new Guide.Builder();
        b.add(1_000, 2_000, "The Lantern House");
        b.add(2_000, 3_000, "Coastal Kitchens");
        b.add(4_000, 5_000, "Night Shift");
        Guide.Schedule s = b.build();
        // On now: as it ends (the next one begins then).
        assertEquals(2_000L, PinBackdrop.nextChange(s, 1_500));
        assertEquals(3_000L, PinBackdrop.nextChange(s, 2_000));
        // A gap: as the next one begins.
        assertEquals(4_000L, PinBackdrop.nextChange(s, 3_500));
        // Nothing before the first, and nothing after the last.
        assertEquals(1_000L, PinBackdrop.nextChange(s, 0));
        assertEquals(-1L, PinBackdrop.nextChange(s, 6_000));
        assertEquals(-1L, PinBackdrop.nextChange(null, 1_500));
    }

    @Test public void theShadeIsStrongestBehindTheChannelsWords() {
        float[] pos = new float[6];
        int[] alpha = new int[6];
        // A phone (1233 px): the words from 36 px to 606, the small picture on the right.
        int n = PinBackdrop.stops(1233, 36, 606, 288, pos, alpha);
        assertStops(pos, n);
        assertEquals(PinBackdrop.AT_WORDS, alpha[0]);
        assertEquals(PinBackdrop.AT_WORDS, at(pos, alpha, n, 36f / 1233));
        assertEquals(PinBackdrop.WORDS_END, at(pos, alpha, n, 606f / 1233));
        assertEquals(PinBackdrop.REST, alpha[n - 1]);
        // A tablet (800 px): the small picture at the left, the words beside it, lighter either side of them.
        n = PinBackdrop.stops(800, 282, 662, 96, pos, alpha);
        assertStops(pos, n);
        assertEquals(PinBackdrop.REST, alpha[0]);
        assertEquals(PinBackdrop.AT_WORDS, at(pos, alpha, n, 282f / 800));
        assertEquals(PinBackdrop.WORDS_END, at(pos, alpha, n, 662f / 800));
        assertEquals(PinBackdrop.REST, alpha[n - 1]);
        // Words running nearly to the edge: their end's shade to the edge.
        n = PinBackdrop.stops(800, 282, 760, 96, pos, alpha);
        assertStops(pos, n);
        assertEquals(PinBackdrop.WORDS_END, alpha[n - 1]);
        assertTrue(PinBackdrop.AT_WORDS > PinBackdrop.WORDS_END && PinBackdrop.WORDS_END > PinBackdrop.REST);
        // Words not laid out yet: an even shade.
        n = PinBackdrop.stops(800, -1, -1, 96, pos, alpha);
        assertEquals(2, n);
        assertEquals(PinBackdrop.REST, alpha[0]);
    }

    @Test public void itsFacesGoRightOfTheWords() {
        // As much of the band as the words take, the faces in the rest (under a phone's small picture, beside a
        // tablet's channel line); the panel's share before the words are laid out.
        assertEquals(0.49f, PinBackdrop.room(1233, 606), 0.005f);
        assertEquals(0.83f, PinBackdrop.room(800, 662), 0.005f);
        assertEquals(PicFill.PANEL_ROOM, PinBackdrop.room(800, -1), 0f);
        assertEquals(ProgPics.BAND + 49, ProgPics.band(0.49f));
        assertEquals(ProgPics.BAND + 1, ProgPics.band(0f));
        assertEquals(ProgPics.BAND + 99, ProgPics.band(1f));
    }

    private static void assertStops(float[] pos, int n) {
        assertTrue(n >= 2);
        assertEquals(0f, pos[0], 0f);
        assertEquals(1f, pos[n - 1], 0f);
        for (int i = 1; i < n; i++) assertTrue(pos[i] >= pos[i - 1]);
    }

    private static int at(float[] pos, int[] alpha, int n, float p) {
        for (int i = 0; i < n; i++) if (Math.abs(pos[i] - p) < 1e-5f) return alpha[i];
        throw new AssertionError("no stop at " + p);
    }
}
