package com.gathin.tvhomephone.glass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

/** The glass pages' pure parts: the greeting and date, a bar's tint without a blur, the lens's colours, the blur. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GlassTest {
    @Test
    public void theGreetingFollowsTheDay() {
        assertEquals("Good morning", Glass.greeting(LocalTime.of(7, 30)));
        assertEquals("Good morning", Glass.greeting(LocalTime.of(11, 59)));
        assertEquals("Good afternoon", Glass.greeting(LocalTime.of(12, 0)));
        assertEquals("Good afternoon", Glass.greeting(LocalTime.of(17, 59)));
        assertEquals("Good evening", Glass.greeting(LocalTime.of(18, 0)));
        assertEquals("Good evening", Glass.greeting(LocalTime.of(2, 0)));
    }

    @Test
    public void theDateReadsAsTheHeaderShowsIt() {
        assertEquals("Monday 16 November", Glass.date(LocalDate.of(2026, 11, 16)));
    }

    @Test
    public void aBarWithNothingBlurredUnderItIsTintedDeeper() {
        int tint = 0x9E121318;
        int deeper = BlurBehind.deeper(tint);
        assertTrue((deeper >>> 24) > (tint >>> 24));
        assertTrue((deeper >>> 24) <= 0xE6);
        assertEquals(tint & 0xFFFFFF, deeper & 0xFFFFFF);
        assertEquals(0xE6, BlurBehind.deeper(0xFF000000) >>> 24);
    }

    @Test
    public void theLensBlendsFromOneColourToTheOther() {
        assertEquals(Glass.MUTED, LensBar.blend(Glass.MUTED, Glass.TEXT, 0f));
        assertEquals(Glass.TEXT, LensBar.blend(Glass.MUTED, Glass.TEXT, 1f));
        assertEquals(Glass.TEXT, LensBar.blend(Glass.MUTED, Glass.TEXT, 3f));
        int half = LensBar.blend(0xFF000000, 0xFFFFFFFF, 0.5f);
        assertEquals(0xFF, half >>> 24);
        assertTrue(((half >> 16) & 255) >= 127 && ((half >> 16) & 255) <= 128);
    }

    @Test
    public void theBlurKeepsAFlatPictureFlatAndSpreadsAPoint() {
        int w = 21, h = 11;
        int[] px = new int[w * h], tmp = new int[w * h];
        Arrays.fill(px, 0xFF808080);
        Soften.blur(px, tmp, w, h, 4);
        for (int p : px) assertEquals(0xFF808080, p);
        Arrays.fill(px, 0xFF000000);
        px[5 * w + 10] = 0xFFFFFFFF;
        Soften.blur(px, tmp, w, h, 4);
        int mid = px[5 * w + 10] & 255, near = px[5 * w + 12] & 255, far = px[0] & 255;
        assertTrue(mid > 0 && mid < 255);
        assertTrue(near > 0 && near <= mid);
        assertEquals(0, far);
    }
}
