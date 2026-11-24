package com.gathin.broadcast;

import java.util.Locale;

/**
 * Pure colour sums for the family's own scoreboards (BoardSpec, BoardDesigner) and logos (LogoSpec): colours as
 * 0xAARRGGBB ints, "#RRGGBB" in and out, WCAG's relative luminance and contrast ratio, a colour laid over another, mixes,
 * and hue, saturation and value. No Android in here, so the JVM tests use it as the apps do.
 */
public final class BoardColours {
    private BoardColours() {
    }

    public static final int WHITE = 0xFFFFFFFF, INK = 0xFFE6E9F2, BLACK = 0xFF000000, NIGHT = 0xFF0B0E16;

    /** "#RRGGBB" (or "#AARRGGBB", "RRGGBB", "#RGB") as a colour, or fallback for anything else. */
    public static int parse(String s, int fallback) {
        if (s == null) return fallback;
        String h = s.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) {
            StringBuilder b = new StringBuilder(6);
            for (int i = 0; i < 3; i++) b.append(h.charAt(i)).append(h.charAt(i));
            h = b.toString();
        }
        if (h.length() != 6 && h.length() != 8) return fallback;
        for (int i = 0; i < h.length(); i++) if (Character.digit(h.charAt(i), 16) < 0) return fallback;
        long v = Long.parseLong(h, 16);
        return h.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
    }

    /** Whether s is a colour parse() reads. */
    public static boolean valid(String s) {
        return parse(s, 1) != 1 || parse(s, 2) != 2;
    }

    /** "#RRGGBB" for an opaque colour, "#AARRGGBB" for one see-through. */
    public static String hex(int c) {
        if ((c >>> 24) == 0xFF) return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
        return String.format(Locale.ROOT, "#%08X", c);
    }

    public static int alpha(int c) {
        return c >>> 24;
    }

    public static int opaque(int c) {
        return c | 0xFF000000;
    }

    public static int withAlpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }

    /** c laid over an opaque ground (its alpha mixing it in), opaque. */
    public static int over(int c, int ground) {
        float a = alpha(c) / 255f;
        int g = opaque(ground);
        return mix(g, opaque(c), a);
    }

    /** a to b by t (0 a, 1 b), each channel, alpha too. */
    public static int mix(int a, int b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        int ar = (a >>> 24), rr = (a >> 16) & 0xFF, gg = (a >> 8) & 0xFF, bb = a & 0xFF;
        int br = (b >>> 24), r2 = (b >> 16) & 0xFF, g2 = (b >> 8) & 0xFF, b2 = b & 0xFF;
        int oa = Math.round(ar + (br - ar) * u), or = Math.round(rr + (r2 - rr) * u), og = Math.round(gg + (g2 - gg) * u),
                ob = Math.round(bb + (b2 - bb) * u);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** WCAG's relative luminance of an opaque colour, 0 (black) to 1 (white). */
    public static double luminance(int c) {
        return 0.2126 * channel((c >> 16) & 0xFF) + 0.7152 * channel((c >> 8) & 0xFF) + 0.0722 * channel(c & 0xFF);
    }

    /** WCAG's contrast ratio of two colours (1 to 21), each taken opaque. */
    public static double contrast(int a, int b) {
        double la = luminance(opaque(a)), lb = luminance(opaque(b));
        double hi = Math.max(la, lb), lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }

    /** Whichever of the light ink and the near-black reads better on a ground. */
    public static int inkFor(int ground) {
        return contrast(INK, ground) >= contrast(NIGHT, ground) ? INK : NIGHT;
    }

    /**
     * ink kept if it reads on ground at least at min, else moved toward white or black (whichever reads better) until it
     * does, keeping its hue where it can: a light gold on navy stays gold, a navy on navy turns light.
     */
    public static int legible(int ink, int ground, double min) {
        int g = opaque(ground), i = opaque(ink);
        if (contrast(i, g) >= min) return i;
        int toward = contrast(WHITE, g) >= contrast(BLACK, g) ? WHITE : BLACK;
        for (int k = 1; k <= 20; k++) {
            int c = mix(i, toward, k / 20f);
            if (contrast(c, g) >= min) return c;
        }
        return toward;
    }

    /** {hue 0-360, saturation 0-1, value 0-1}. */
    public static float[] hsv(int c) {
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 1e-6f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h *= 60f;
            if (h < 0) h += 360f;
        }
        return new float[]{h, max <= 0 ? 0 : d / max, max};
    }

    /** An opaque colour from hue (any degrees), saturation and value (0-1). */
    public static int fromHsv(float h, float s, float v) {
        float hh = ((h % 360f) + 360f) % 360f;
        float ss = Math.max(0f, Math.min(1f, s)), vv = Math.max(0f, Math.min(1f, v));
        float c = vv * ss, x = c * (1 - Math.abs((hh / 60f) % 2 - 1)), m = vv - c;
        float r, g, b;
        if (hh < 60) { r = c; g = x; b = 0; } else if (hh < 120) { r = x; g = c; b = 0; } else if (hh < 180) { r = 0; g = c; b = x; }
        else if (hh < 240) { r = 0; g = x; b = c; } else if (hh < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    /** The colour with its HSV value times k (deeper below 1, lighter above), its hue and saturation kept. */
    public static int value(int c, float k) {
        float[] v = hsv(c);
        return withAlpha(fromHsv(v[0], v[1], v[2] * k), alpha(c));
    }

    /** The colour with its HSV value set. */
    public static int atValue(int c, float v) {
        float[] h = hsv(c);
        return withAlpha(fromHsv(h[0], h[1], v), alpha(c));
    }

    /** How far apart two hues are, 0-180 degrees. */
    public static float hueGap(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180 ? 360 - d : d;
    }

    /** Whether a colour has a real hue (not a white, grey or black). */
    public static boolean coloured(int c) {
        float[] v = hsv(c);
        return v[1] >= 0.22f && v[2] >= 0.16f;
    }
}
