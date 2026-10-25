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
