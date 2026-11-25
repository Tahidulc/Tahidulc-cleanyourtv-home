package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Outfit, the family's lettering on the logo covers, at a weight, the same in all three apps: the one variable font
 * (assets: broadcast/fonts/outfit.ttf, the file each app also has as res/font/outfit.ttf) with its weight set ('wght'),
 * as each app's font resources (outfit_medium.xml and the rest) make it; each weight made once. And the format marks'
 * pictures the covers draw (assets: broadcast/formats/: Dolby's, HDR10's), for the launcher's Home and both apps' covers.
 */
public final class Outfit {
    private Outfit() {}

    private static final Map<Integer, Typeface> FACES = new HashMap<>();
    private static final Map<String, Bitmap> FORMATS = new HashMap<>();

    /** Pure: the weight a face is made at: 900, 800, 700, 600, 500 (as the apps' outfit_black to outfit_medium), 400, 300, 200. */
    public static int weightOf(int weight) {
        if (weight >= 900) return 900;
        if (weight >= 800) return 800;
        if (weight >= 700) return 700;
        if (weight >= 600) return 600;
        if (weight >= 500) return 500;
        if (weight >= 400) return 400;
        return weight >= 300 ? 300 : 200;
    }

    /** Outfit at a weight (200 to 900). */
    public static synchronized Typeface at(Context c, int weight) {
        int w = weightOf(weight);
        Typeface t = FACES.get(w);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(c.getAssets(), "broadcast/fonts/outfit.ttf").setFontVariationSettings("'wght' " + w).build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = Typeface.create("sans-serif", w >= 700 ? Typeface.BOLD : Typeface.NORMAL);
        FACES.put(w, t);
        return t;
    }

    /** Pure: a format mark's picture in the assets ("HDR10+": broadcast/formats/fmt_hdr10plus.png); null for one in letters. */
    public static String formatAsset(String mark) {
        if (mark == null) return null;
        switch (mark) {
            case "HDR10+": return "broadcast/formats/fmt_hdr10plus.png";
            case "HDR10": return "broadcast/formats/fmt_hdr10.png";
            case "DOLBY VISION": return "broadcast/formats/fmt_dolby_vision.png";
            case "DOLBY ATMOS": return "broadcast/formats/fmt_dolby_atmos.png";
            case "DOLBY VISION ATMOS": return "broadcast/formats/fmt_dolby_vision_atmos.png";
            case "DOLBY": return "broadcast/formats/fmt_dolby.png";
            default: return null;
        }
    }

    /** A format mark's picture (the format's own logo, white), read once; null for one drawn in letters, or unreadable. */
    public static synchronized Bitmap format(Context c, String mark) {
        String path = formatAsset(mark);
        if (path == null) return null;
        if (FORMATS.containsKey(path)) return FORMATS.get(path);
        Bitmap b = null;
        try (InputStream in = c.getAssets().open(path)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (java.io.IOException | RuntimeException e) {
            b = null;
        }
        FORMATS.put(path, b);
        return b;
    }
}
