package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Small helpers for app banners: a dark banner redrawn on white, and the colour at a banner's edge. */
final class BannerArt {
    private BannerArt() { }

    /**
     * The banner redrawn for a white background: greys are inverted (a black field turns white, white and grey
     * lettering turns near-black) while coloured pixels keep their colour, so a brand mark stays as it is.
     */
    static Drawable onWhite(Context ctx, Drawable banner) {
        Bitmap bmp = render(banner, 0, 0);
        if (bmp == null) return banner;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        float[] hsv = new float[3];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int a = Color.alpha(c);
            if (a == 0) { px[i] = Color.WHITE; continue; }
            int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
            Color.RGBToHSV(r, g, b, hsv);
            int grey = 255 - Math.round(0.299f * r + 0.587f * g + 0.114f * b);
            grey = Math.max(20, grey);   // white lettering becomes near-black, not pure black
            int greyed = Color.rgb(grey, grey, grey);
            // Saturation decides how much of the pixel is "colour": greys invert, the brand mark stays.
            float keep = Math.min(1f, hsv[1] * hsv[2] * 2.2f);
            int out = blend(greyed, Color.rgb(r, g, b), keep);
            px[i] = blend(Color.WHITE, out, a / 255f);
        }
        Bitmap result = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        return new BitmapDrawable(ctx.getResources(), result);
    }

    /**
     * A white logo on a brand-coloured field (Prime Video's blue, Disney+'s navy-to-teal) redrawn as the brand
     * colour on white: the brighter and greyer a pixel, the more of the brand colour it gets; the field turns white.
     */
    static Drawable brandOnWhite(Context ctx, Drawable banner) {
        Bitmap bmp = render(banner, 0, 0);
        if (bmp == null) return banner;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int tint = darkest(px, w, h);
        for (int y = 0; y < h; y++) {
            // The field behind this row, from its left edge: Disney+ fades navy to teal top to bottom, so one
            // colour for the whole banner would leave a halo. Each pixel is then "un-mixed": how much of it is the
            // white logo over that field. Anti-aliased edges keep their in-between values, so they stay smooth.
            int left = px[y * w + Math.min(2, w - 1)], right = px[y * w + Math.max(0, w - 3)];
            // The brighter of the two edges: a field that brightens towards one side then leaves no grey smudge.
            int bg = Color.luminance(left) >= Color.luminance(right) ? left : right;
            int br = Color.red(bg), bgG = Color.green(bg), bb = Color.blue(bg);
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                float a = Math.max(unmix(Color.red(c), br), Math.max(unmix(Color.green(c), bgG), unmix(Color.blue(c), bb)));
                a *= Color.alpha(c) / 255f;
                px[y * w + x] = blend(Color.WHITE, tint, a);
            }
        }
        return new BitmapDrawable(ctx.getResources(), Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888));
    }

    /** How far a channel has moved from the field towards white, 0..1. */
    private static float unmix(int value, int field) {
        if (field >= 250) return 0f;
        return Math.max(0f, Math.min(1f, (value - field) / (float) (255 - field)));
    }

    /** The darkest of the banner's four corners: the deep end of a gradient field, readable on white. */
    private static int darkest(int[] px, int w, int h) {
        int[] corners = {px[w + 1], px[2 * w - 2], px[(h - 2) * w + 1], px[(h - 1) * w - 2]};
        int best = corners[0];
        for (int c : corners) {
            if (Color.red(c) + Color.green(c) + Color.blue(c) < Color.red(best) + Color.green(best) + Color.blue(best)) best = c;
        }
        // Prime's field is bright blue already; deepen any very light corner so the logo keeps contrast.
        float[] hsv = new float[3];
        Color.colorToHSV(best, hsv);
        hsv[2] = Math.min(hsv[2], 0.62f);
        hsv[1] = Math.min(1f, Math.max(hsv[1], 0.55f) * 1.15f);
        return Color.HSVToColor(hsv);
    }

    /**
     * An app icon as a plain white glyph for a frosted pill: an adaptive icon's foreground layer (its safe zone
     * cropped so the glyph fills the button), every pixel white at its own alpha. Tint it like any pill icon.
     */
    static Drawable monoIcon(Context ctx, Drawable icon) {
        Drawable src = icon;
        boolean adaptive = false;
        if (icon instanceof android.graphics.drawable.AdaptiveIconDrawable
                && ((android.graphics.drawable.AdaptiveIconDrawable) icon).getForeground() != null) {
            src = ((android.graphics.drawable.AdaptiveIconDrawable) icon).getForeground();
            adaptive = true;
        }
        int size = 216;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        src.setBounds(0, 0, size, size);
        src.draw(new Canvas(bmp));
        if (adaptive) {
            int m = size * 22 / 108;   // the 108dp layer's outer margin around its 64dp-ish glyph area
            bmp = Bitmap.createBitmap(bmp, m, m, size - 2 * m, size - 2 * m);
        }
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int a = Color.alpha(c);
            if (!adaptive) {
                // A flat icon: keep only its light parts (a white glyph on a coloured square).
                int lum = Math.round(0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c));
                a = a * Math.max(0, Math.min(255, (lum - 140) * 3)) / 255;
            }
            px[i] = Color.argb(a, 255, 255, 255);
        }
        return new BitmapDrawable(ctx.getResources(), Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888));
    }

    /**
     * A banner's logo without its box, for laying over a picture: the field colour (sampled at the edge) turns
     * transparent, dark lettering turns white so it reads on a dark scene, brand colours stay.
     */
    static Drawable knockout(Context ctx, Drawable banner) {
        Bitmap bmp = render(banner, 0, 0);
        if (bmp == null) return banner;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int field = px[w + 1];
        float[] hsv = new float[3];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int dr = Color.red(c) - Color.red(field), dg = Color.green(c) - Color.green(field), db = Color.blue(c) - Color.blue(field);
            float dist = (float) Math.sqrt(dr * dr + dg * dg + db * db) / 441f;
            int a = Math.round(Math.max(0f, Math.min(1f, (dist - 0.08f) / 0.25f)) * Color.alpha(c));
            Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv);
            int rgb = c;
            if (hsv[1] < 0.35f || hsv[2] < 0.35f) {
                // Greys and near-blacks: lettering that was dark on a light field becomes white.
                rgb = hsv[2] < 0.6f ? Color.WHITE : Color.rgb(Color.red(c), Color.green(c), Color.blue(c));
                if (Color.luminance(field) < 0.5f) rgb = c;   // a dark field already had light lettering
            }
            px[i] = Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
        }
        return new BitmapDrawable(ctx.getResources(), Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888));
    }

    /** The colour just inside the banner's top-left corner, for filling the space around it. */
    static int edgeColor(Drawable d) {
        Bitmap bmp = render(d, 64, 36);
        if (bmp == null) return 0xFF1C1C22;
        int c = bmp.getPixel(2, 2);
        return Color.alpha(c) < 128 ? 0xFF1C1C22 : (c | 0xFF000000);
    }

    private static Bitmap render(Drawable d, int width, int height) {
        int w = width > 0 ? width : d.getIntrinsicWidth();
        int h = height > 0 ? height : d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return null;
        if (width <= 0 && d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return ((BitmapDrawable) d).getBitmap().copy(Bitmap.Config.ARGB_8888, false);
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Drawable copy = d.getConstantState() != null ? d.getConstantState().newDrawable().mutate() : d;
        copy.setBounds(0, 0, w, h);
        copy.draw(new Canvas(bmp));
        return bmp;
    }

    private static int blend(int base, int over, float t) {
        return Color.rgb(Math.round(Color.red(base) + (Color.red(over) - Color.red(base)) * t),
                Math.round(Color.green(base) + (Color.green(over) - Color.green(base)) * t),
                Math.round(Color.blue(base) + (Color.blue(over) - Color.blue(base)) * t));
    }
}
