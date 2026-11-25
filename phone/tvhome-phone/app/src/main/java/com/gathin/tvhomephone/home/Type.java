package com.gathin.tvhomephone.home;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

import androidx.core.content.res.ResourcesCompat;

import com.gathin.tvhomephone.R;

/**
 * Home's type, as the TV launcher's: Outfit for headings and titles (the variable font in assets, at the weight asked,
 * built once each), Inter for words (res/font, SIL Open Font License 1.1). Android's own sans when one can't be read.
 */
public final class Type {
    private static final SparseArray<Typeface> OUTFIT = new SparseArray<>(), JAKARTA = new SparseArray<>();
    private static Typeface inter, interSemi, interLight;

    private Type() {}

    public static synchronized Typeface outfit(Context c, int weight) {
        Typeface t = OUTFIT.get(weight);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(c.getAssets(), "fonts/outfit.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = Typeface.create("sans-serif-medium", weight >= 700 ? Typeface.BOLD : Typeface.NORMAL);
        OUTFIT.put(weight, t);
        return t;
    }

    /**
     * Live TV's type (the guide, its panel and the channels' rows): Plus Jakarta Sans, the variable font in assets at
     * the weight asked (200 to 800), built once each; Inter where it can't be read.
     */
    public static synchronized Typeface jakarta(Context c, int weight) {
        Typeface t = JAKARTA.get(weight);
        if (t != null) return t;
        try {
            t = new Typeface.Builder(c.getAssets(), "fonts/plusjakartasans.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (RuntimeException e) {
            t = null;
        }
        if (t == null) t = weight >= 600 ? interSemi(c) : inter(c);
        JAKARTA.put(weight, t);
        return t;
    }

    public static synchronized Typeface inter(Context c) {
        if (inter == null) inter = font(c, R.font.inter_regular, Typeface.create("sans-serif", Typeface.NORMAL));
        return inter;
    }

    public static synchronized Typeface interSemi(Context c) {
        if (interSemi == null) interSemi = font(c, R.font.inter_semibold, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return interSemi;
    }

    public static synchronized Typeface interLight(Context c) {
        if (interLight == null) interLight = font(c, R.font.inter_display_light, Typeface.create("sans-serif-light", Typeface.NORMAL));
        return interLight;
    }

    private static Typeface font(Context c, int id, Typeface fallback) {
        try {
            Typeface t = ResourcesCompat.getFont(c, id);
            return t == null ? fallback : t;
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
