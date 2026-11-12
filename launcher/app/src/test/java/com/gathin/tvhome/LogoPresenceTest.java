package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The ad-break screen's word on the channel's logo: its solid strokes their own colour (up) or not (an advert). */
public class LogoPresenceTest {
    private static LogoShape.Model whiteLetters(int w, int h) {
        int n = w * h;
        boolean[] logo = new boolean[n];
        float[] t = new float[n];
        float[][] add = new float[3][n];
        for (int i = 0; i < n; i++) {
            t[i] = 1f;
            int x = i % w, y = i / w;
            if (x >= 10 && x < 50 && y >= 5 && y < 15) {
                logo[i] = true;
                t[i] = 0.05f;
                add[0][i] = add[1][i] = add[2][i] = 245f;
            }
        }
        return new LogoShape.Model(w, h, logo, t, add, LogoShape.Model.SEEN);
    }

    @Test public void theLogoUpReadsNearOneAndAnAdvertNearZero() {
        int w = 60, h = 20;
        LogoShape.Model m = whiteLetters(w, h);
        int[] up = new int[w * h], advert = new int[w * h];
        for (int i = 0; i < up.length; i++) {
            boolean stroke = m.logo[i];
            up[i] = stroke ? 0xFFF8F8F8 : 0xFF203040 + (i % 50);
            advert[i] = 0xFF000000 | ((i * 37) % 200) << 16 | ((i * 11) % 180) << 8 | ((i * 5) % 160);
        }
        assertTrue(LogoShape.presence(up, m) > 0.9f);
        assertTrue(LogoShape.presence(advert, m) < AdBreakLogic.ABSENT);
    }

    @Test public void tooLittleSolidToTell() {
        int w = 10, h = 4;
        LogoShape.Model m = whiteLetters(w, h);
        assertEquals(-1f, LogoShape.presence(new int[w * h], m), 0f);
    }
}
