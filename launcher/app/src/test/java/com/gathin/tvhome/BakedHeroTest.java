package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The centre crop shared by the baked background and the rounded posters: as ImageView's CENTER_CROP places it. */
public class BakedHeroTest {
    @Test
    public void widePictureFillsTheHeightCentred() {
        // A 1400 by 600 picture in a 1400 by 720 box: scaled up to the height, its sides cut off evenly.
        float[] m = BakedHero.centreCrop(1400, 600, 1400, 720);
        assertEquals(1.2f, m[0], 1e-4);
        assertEquals(Math.round((1400 - 1400 * 1.2f) / 2f), m[1], 1e-4);
        assertEquals(0f, m[2], 1e-4);
    }

    @Test
    public void tallPictureFillsTheWidthCentred() {
        // A 185 by 278 poster in a 207 by 310 box: the width decides, the top and foot cut off evenly.
        float[] m = BakedHero.centreCrop(185, 278, 207, 310);
        float s = 207f / 185f;
        assertEquals(s, m[0], 1e-4);
        assertEquals(0f, m[1], 1e-4);
        assertEquals(Math.round((310 - 278 * s) / 2f), m[2], 1e-4);
    }

    @Test
    public void nothingToCropIsLeftAlone() {
        float[] m = BakedHero.centreCrop(0, 0, 100, 100);
        assertEquals(1f, m[0], 0f);
        assertEquals(0f, m[1], 0f);
        assertEquals(0f, m[2], 0f);
    }
}
