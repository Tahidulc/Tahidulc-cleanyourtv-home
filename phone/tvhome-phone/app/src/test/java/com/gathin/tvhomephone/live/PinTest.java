package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The picture kept in sight over the guide (Pin, with Fold): when it is kept, how small it goes, and what goes with it. */
public class PinTest {
    @Test public void keptOnlyWhileAChannelPlaysInIt() {
        assertTrue(Pin.kept(true, true));
        // Nothing playing (waiting with its play button, stopped, or the full screen has the stream): folds as before.
        assertFalse(Pin.kept(false, true));
        // No picture there (closed with the cross, or a search without it).
        assertFalse(Pin.kept(true, false));
        assertFalse(Pin.kept(false, false));
    }

    @Test public void aboutFortyFivePercentOfAPhonesWidth() {
        // A phone 1233 px across (411 dp at 3x), its picture 1161 px (12 dp either side).
        assertEquals(555, Pin.compactWidth(1233, 1161, false));
        // A tablet: under a third, the band's picture being half the width already.
        assertEquals(480, Pin.compactWidth(1600, 776, true));
        // Never wider than the picture itself, nor anything without a width.
        assertEquals(300, Pin.compactWidth(1233, 300, false));
        assertEquals(0, Pin.compactWidth(0, 0, false));
    }

    @Test public void scalesFromWholeToSmall() {
        float kMin = 555 / 1161f;
        assertEquals(1f, Pin.scale(0f, kMin), 0f);
        assertEquals(kMin, Pin.scale(1f, kMin), 1e-6f);
        assertEquals((1f + kMin) / 2f, Pin.scale(0.5f, kMin), 1e-6f);
        // Settling past either end: no further.
        assertEquals(1f, Pin.scale(-0.2f, kMin), 0f);
        assertEquals(kMin, Pin.scale(1.3f, kMin), 1e-6f);
    }

    @Test public void theSmallPictureFillsJustWhatShows() {
        // A phone: 6 dp over the picture, 4 under it (18 and 12 px at 3x), the picture 1161 by 653, small 555 wide.
        int padT = 18, padB = 12, pw = 1161, ph = pw * 9 / 16, cw = Pin.compactWidth(1233, pw, false);
        float kMin = cw / (float) pw;
        int ch = Math.round(ph * kMin);
        int pinH = padT + ph + padB, keep = padT + ch + padB, head = 900;
        for (int s = 0; s <= Fold.range(pinH, head, keep); s += 7) {
            int shown = Fold.pinShown(s, pinH, head, keep);
            float k = Pin.scale(Fold.shrunk(shown, pinH, keep), kMin);
            // The picture as drawn ends where the guide begins (less the gap under it), to the pixel.
            assertEquals(shown - padB, padT + ph * k, 1f);
        }
    }

    @Test public void aTapIsFullScreenOnceItIsSmall() {
        assertFalse(Pin.compact(0f));
        assertFalse(Pin.compact(0.49f));
        assertTrue(Pin.compact(Pin.COMPACT_AT));
        assertTrue(Pin.compact(1f));
    }

    @Test public void theChannelBesideItComesInOnTheWayToSmall() {
        float from = Pin.INFO_FROM, to = Pin.INFO_FULL_AT;
        assertEquals(0f, Pin.infoAlpha(0f, from, to), 0f);
        assertEquals(0f, Pin.infoAlpha(from, from, to), 0f);
        assertEquals(0.5f, Pin.infoAlpha((from + to) / 2f, from, to), 1e-6f);
        assertEquals(1f, Pin.infoAlpha(to, from, to), 1e-6f);
        assertEquals(1f, Pin.infoAlpha(1f, from, to), 0f);
        // Grown back (scrolled up): gone again.
        assertEquals(0f, Pin.infoAlpha(0.2f, from, to), 0f);
        // Coming in and all there at once.
        assertEquals(0f, Pin.infoAlpha(0.89f, 0.9f, 0.9f), 0f);
        assertEquals(1f, Pin.infoAlpha(0.9f, 0.9f, 0.9f), 0f);
    }

    @Test public void onATabletOnlyOnceThePictureIsClearOfIt() {
        // A tablet 1600 px across (800 dp at 2x): its picture 776, small 480, the channel 32 beyond it.
        int pw = 776, cw = Pin.compactWidth(1600, pw, true);
        float kMin = cw / (float) pw, clear = Pin.clearAt(pw, cw + 32, kMin);
        // The picture, shrunk that far, is no wider than the room.
        assertEquals(cw + 32, pw * Pin.scale(clear, kMin), 0.5f);
        assertTrue(clear > 0.8f && clear < 1f);
        // Never: all the way; always: at once; no picture: all the way.
        assertEquals(1f, Pin.clearAt(776, 100, 0.5f), 0f);
        assertEquals(0f, Pin.clearAt(776, 900, 0.5f), 0f);
        assertEquals(1f, Pin.clearAt(0, 100, 0.5f), 0f);
    }

    @Test public void aTabletsDetailsGiveWayFirst() {
        assertEquals(1f, Pin.detailsAlpha(0f), 0f);
        assertEquals(0f, Pin.detailsAlpha(Pin.DETAILS_GONE_AT), 1e-6f);
        assertEquals(0f, Pin.detailsAlpha(1f), 0f);
        // Gone before the channel beside the small picture comes in: never both at once.
        int pw = 776, cw = Pin.compactWidth(1600, pw, true);
        float from = Pin.clearAt(pw, cw + 32, cw / (float) pw);
        for (float b = 0f; b <= 1f; b += 0.05f) assertTrue(Pin.detailsAlpha(b) == 0f || Pin.infoAlpha(b, from, 1f) == 0f);
    }

    @Test public void itsCornersLookTheSameAsItShrinks() {
        // Whole: as it was (18 dp at 3x); small at half its size: 24 px of its own look 12.
        assertEquals(54f, Pin.corner(0f, 54f, 36f, 1f), 1e-4f);
        assertEquals(72f, Pin.corner(1f, 54f, 36f, 0.5f), 1e-4f);
        assertEquals(36f, Pin.corner(1f, 54f, 36f, 0.5f) * 0.5f, 1e-4f);
    }
}
