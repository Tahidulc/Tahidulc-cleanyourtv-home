package com.gathin.tvhomephone.sports;

/**
 * The colours of the club and driver pages, worked out from a club's (or an F1 team's) own colour: the ground, deep
 * enough in that colour that white words read on it; the glow at its middle; the cards' panels a shade lighter; the
 * number's coin in the colour itself. Pure, for the tests (no Android colour calls).
 */
final class ClubLook {
    private ClubLook() {}

    /** Navy, for a club whose colour is not known. */
    static final int NAVY = 0xFF1B2470;

    /** Pure: the colour to build a page on: ESPN's colour, or its other one when the first is white, grey or near it. */
    static int pick(int colour, int alternate) {
        if (colour == 0) return alternate != 0 ? alternate : NAVY;
        double[] a = hsl(colour);
        boolean plain = a[1] < 0.12 || a[2] > 0.88 || a[2] < 0.06;
        if (plain && alternate != 0) {
            double[] b = hsl(alternate);
            if (b[1] >= 0.2 && b[2] > 0.08 && b[2] < 0.85) return alternate;
        }
        return colour;
    }

    /**
     * Pure: the page's ground: the colour's own hue, deep enough that white words on it pass 7:1, never black. A grey,
     * white or black colour gives a deep slate.
     */
    static int ground(int colour) {
        double[] c = hsl(colour);
        double h = c[0], s = c[1], l = c[2];
        if (s < 0.12) {
            s = 0.16;
            h = 228;
            l = 0.17;
        } else {
            s = Math.min(s, 0.86);
            l = Math.min(Math.max(l, 0.16), 0.33);
        }
        int out = rgb(h, s, l);
        while (contrast(0xFFFFFFFF, out) < 7.0 && l > 0.08) {
            l -= 0.01;
            out = rgb(h, s, l);
        }
        return out;
    }

    /** Pure: the light at the middle of the ground (the ground a little lighter, in its hue). */
    static int glow(int ground) {
        double[] c = hsl(ground);
        return rgb(c[0], Math.min(1, c[1] * 1.04), Math.min(0.62, c[2] + 0.075));
    }

    /** Pure: the panel's top colour (a shade lighter and a touch greyer than the ground) with its alpha. */
    static int panelTop(int ground) {
        double[] c = hsl(ground);
        return (rgb(c[0], c[1] * 0.72, Math.min(0.7, c[2] + 0.15)) & 0x00FFFFFF) | 0xE6000000;
    }

    /** Pure: the panel's foot: nearly the ground, a little lighter, half see-through. */
    static int panelBottom(int ground) {
        double[] c = hsl(ground);
        return (rgb(c[0], c[1], Math.min(0.7, c[2] + 0.045)) & 0x00FFFFFF) | 0x8C000000;
    }

    /** Pure: the number's coin: the club's own colour brought to a middle lightness, or white for a white club. */
    static int coin(int colour) {
        double[] c = hsl(colour);
        if (c[1] < 0.12) return c[2] > 0.5 ? 0xFFF2F3F7 : 0xFF3A3F52;
        double l = Math.min(Math.max(c[2], 0.40), 0.52);
        int out = rgb(c[0], Math.max(c[1], 0.62), l);
        while (contrast(0xFFFFFFFF, out) < 3.2 && l > 0.2) {
            l -= 0.02;
            out = rgb(c[0], Math.max(c[1], 0.62), l);
        }
        return out;
    }

    /** Pure: the digits on the coin: white, or the ground's deep ink on a white coin. */
    static int coinInk(int coin) {
        return contrast(0xFFFFFFFF, coin) >= 3.0 ? 0xFFFFFFFF : 0xFF141726;
    }

    /** Pure: a colour with its lightness moved by d (−1 to 1), kept in range. */
    static int lighter(int colour, double d) {
        double[] c = hsl(colour);
        return (colour & 0xFF000000) | (rgb(c[0], c[1], Math.max(0, Math.min(1, c[2] + d))) & 0x00FFFFFF);
    }

    /** Pure: a colour at an alpha (0 to 255). */
    static int alpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    // ---- colour arithmetic ------------------------------------------------------------------------------------------

    /** Pure: {hue 0-360, saturation 0-1, lightness 0-1}. */
    static double[] hsl(int colour) {
        double r = ((colour >> 16) & 0xFF) / 255.0, g = ((colour >> 8) & 0xFF) / 255.0, b = (colour & 0xFF) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2, h = 0, s = 0;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) h = (g - b) / d + (g < b ? 6 : 0);
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
        }
        return new double[]{h, s, l};
    }

    /** Pure: an opaque colour from hue, saturation and lightness. */
    static int rgb(double h, double s, double l) {
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        double hk = (((h % 360) + 360) % 360) / 360.0;
        int r = channel(p, q, hk + 1 / 3.0), g = channel(p, q, hk), b = channel(p, q, hk - 1 / 3.0);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int channel(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        double v;
        if (t < 1 / 6.0) v = p + (q - p) * 6 * t;
        else if (t < 1 / 2.0) v = q;
        else if (t < 2 / 3.0) v = p + (q - p) * (2 / 3.0 - t) * 6;
        else v = p;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** Pure: WCAG's relative luminance of an opaque colour. */
    static double luminance(int colour) {
        double r = lin(((colour >> 16) & 0xFF) / 255.0), g = lin(((colour >> 8) & 0xFF) / 255.0), b = lin((colour & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** Pure: WCAG's contrast ratio of two opaque colours (1 to 21). */
    static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }
}
