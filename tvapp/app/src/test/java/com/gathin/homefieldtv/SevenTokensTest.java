package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.text.TextPaint;

import com.gathin.homefieldtv.engine.Exec;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The 07 look's tokens: Inter's cuts for each size and weight, the mock-ups' letter spacing, and 07's lift (a spring
 * that follows cubic-bezier(.25,.9,.3,1.12): a small overshoot, settled by 0.4 s).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SevenTokensTest {
    @Test public void wordsTakeInterInTheCutForTheirSize() {
        assertEquals(0, Type.step(400));
        assertEquals(1, Type.step(500));
        assertEquals(2, Type.step(600));
        assertEquals(3, Type.step(700));
        assertEquals(4, Type.step(800));
        // The display cut from 28 px at 600 and heavier, as the browser's optical sizing drew the mock-ups.
        assertTrue(Type.display(Type.H1, Type.BOLD));
        assertTrue(Type.display(Type.CLOCK, Type.SEMIBOLD));
        assertFalse(Type.display(Type.TAB, Type.MEDIUM));
        assertFalse(Type.display(Type.HINT, Type.REGULAR));
        assertEquals(-0.028f, Type.spacing(Type.H1), 1e-6f);
        assertEquals(-0.02f, Type.spacing(Type.H2), 1e-6f);
        assertEquals(-0.015f, Type.spacing(Type.H3), 1e-6f);
        assertEquals(-0.01f, Type.spacing(Type.SUB), 1e-6f);

        Type.init(RuntimeEnvironment.getApplication());
        Typeface text = Type.face(Type.MEDIUM), display = Type.face(Type.BOLD, Type.H2);
        assertNotNull(text);
        assertNotNull(display);
        assertTrue("the display cut is its own face", display != Type.face(Type.BOLD));
        TextPaint label = Type.label(Type.LABEL, Look07.L3);
        assertEquals(Type.LABEL_SPACING, label.getLetterSpacing(), 1e-6f);
        assertEquals("tnum", Type.clock(Type.CLOCK, Type.SEMIBOLD, Look07.L1).getFontFeatureSettings());
    }

    @Test public void theLiftOvershootsALittleAndSettlesInFourTenths() {
        boolean was = Exec.inline;
        Exec.inline = false;
        try {
            Glide g = new Glide(Look07.LIFT_MS, 100f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK);
            g.jump(0f);
            g.to(1f);
            float peak = 0f, at100 = 0f;
            long t = 1000;
            g.step(t);
            for (int i = 1; i <= 40; i++) {
                t += 16;
                g.step(t);
                peak = Math.max(peak, g.value());
                if (i == 6) at100 = g.value();
            }
            // 07's ease: three-quarters there by 0.1 s, a 2-3 % overshoot, at rest by the end.
            assertTrue("quick start: " + at100, at100 > 0.65f && at100 < 0.85f);
            assertTrue("a small overshoot: " + peak, peak > 1.015f && peak < 1.04f);
            assertEquals(1f, g.value(), 0.01f);
            // Unsprung, the same Glide never passes its place.
            Glide plain = new Glide(Look07.LIFT_MS, 100f);
            plain.jump(0f);
            plain.to(1f);
            t = 1000;
            plain.step(t);
            float most = 0f;
            for (int i = 0; i < 60; i++) {
                t += 16;
                plain.step(t);
                most = Math.max(most, plain.value());
            }
            assertTrue(most <= 1.0001f);
        } finally {
            Exec.inline = was;
        }
    }

    @Test public void theBlurSpreadsAndKeepsItsTotal() {
        int w = 21, h = 21;
        int[] px = new int[w * h];
        px[10 * w + 10] = 0xFFFFFFFF;
        StackBlur.blur(px, w, h, 3);
        assertTrue("spread to its neighbours", ((px[10 * w + 12] >> 16) & 0xFF) > 0);
        assertTrue("the middle lower", ((px[10 * w + 10] >> 16) & 0xFF) < 255);
        assertEquals("nothing far off", 0, px[0] & 0xFF);
        // Saturation pushed and brightness lowered on a grey changes nothing but its lightness.
        int[] grey = {0xFF808080};
        StackBlur.colour(grey, 1.75f, 0.86f);
        int g = grey[0];
        assertEquals((g >> 16) & 0xFF, g & 0xFF);
        assertEquals(Math.round(128 * 0.86f), (g >> 8) & 0xFF, 1);
    }
}
