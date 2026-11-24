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
    public void theHeroKeepsOnlyWhatItsViewShowsAtTheViewsSize() {
        // A 3840 by 2160 backdrop decoded at 1920 by 1080 for a 1400 by 720 view on a 1080p screen: the 16:9 picture is
        // cut to the view's shape (its top and foot), and kept 1400 wide, not 1920 (4 MB instead of two 8 MB copies).
        float[] p = BakedHero.shownPart(1920, 1080, 1400);
        assertEquals(0f, p[0], 1e-3);
        assertEquals(1920f, p[2], 1e-3);
        assertEquals(1920f * 720f / 1400f, p[3], 1e-2);
        assertEquals((1080f - p[3]) / 2f, p[1], 1e-2);
        assertEquals(1400f, p[4], 0f);
        assertEquals(720f, p[5], 0f);
    }

    @Test
    public void aSmallHeroIsNeverEnlargedAndAWideOneLosesItsSides() {
        // TMDB's w780 still: kept as it is shown, at its own size.
        float[] small = BakedHero.shownPart(780, 439, 1400);
        assertEquals(780f, small[4], 0f);
        assertEquals(Math.round(780f * 720f / 1400f), small[5], 0f);
        // A very wide picture: its sides cut evenly, the height whole.
        float[] wide = BakedHero.shownPart(3000, 720, 1400);
        assertEquals(720f, wide[3], 1e-3);
        assertEquals(1400f, wide[2], 1e-3);
        assertEquals(800f, wide[0], 1e-3);
        assertEquals(1400f, wide[4], 0f);
        // A 4K screen: the view is 2800 wide, still no larger than the picture.
        assertEquals(1920f, BakedHero.shownPart(1920, 1080, 2800)[4], 0f);
        // Nothing to show: one pixel, never zero.
        assertEquals(1f, BakedHero.shownPart(0, 0, 1400)[4], 0f);
    }

    @Test
    public void nothingToCropIsLeftAlone() {
        float[] m = BakedHero.centreCrop(0, 0, 100, 100);
        assertEquals(1f, m[0], 0f);
        assertEquals(0f, m[1], 0f);
        assertEquals(0f, m[2], 0f);
    }
}
