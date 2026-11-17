package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * An app's full logo (its name as it writes it, with its mark), taken from the banner the app shows on a TV's home
 * screen: on a plain background, that background is taken out (the edges kept soft) and dark grey lettering made
 * white, so the logo sits on the launcher's navy as Netflix's does; on a picture, the banner itself, as a small card.
 * Null when the app has no banner (its icon and name are shown instead). Kept for the launcher's life.
 */
final class AppLogos {
    private AppLogos() { }

    /** {Bitmap, Boolean card}: the logo, and whether it is the banner as a card rather than a cut-out logo. */
    private static final ByteLru<String, Object[]> MADE = new ByteLru<>("applogos", 12L * 1024 * 1024,
            o -> o != null && o.length > 0 && o[0] instanceof Bitmap && !((Bitmap) o[0]).isRecycled()
                    ? ((Bitmap) o[0]).getAllocationByteCount() : 0);

    static synchronized Object[] logo(Context c, String pkg) {
        if (MADE.containsKey(pkg)) return MADE.get(pkg);
        Object[] out = null;
        try {
            out = make(c, pkg);
        } catch (Exception | OutOfMemoryError ignored) {
            // the icon and name instead
        }
        MADE.put(pkg, out);
        return out;
    }

    private static Drawable banner(PackageManager pm, String pkg) {
        Drawable d = null;
        try {
            Intent tv = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (tv != null) d = pm.getActivityBanner(tv);
        } catch (Exception ignored) {
            // the app's own
        }
        if (d == null) {
            try {
                d = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) {
                // none
            }
        }
        return d;
    }

    private static Object[] make(Context c, String pkg) {
        Drawable d = banner(c.getPackageManager(), pkg);
        if (d == null) return null;
        int w = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 320, h = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 180;
        // Large banners drawn smaller: a logo a few dozen pixels tall needs no more.
        float scale = Math.min(1f, 480f / w);
        w = Math.max(1, Math.round(w * scale));
        h = Math.max(1, Math.round(h * scale));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, w, h);
        d.draw(new Canvas(b));
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);

        // The background: the commonest colour round the edge, if nearly all the edge is that colour.
        int bg = edgeColour(px, w, h);
        if (bg == 0) return new Object[]{b, true};
        int br = Color.red(bg), bgG = Color.green(bg), bb = Color.blue(bg);
        boolean lightGround = lum(br, bgG, bb) > 140;
        final float soft = 90f;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Color.red(p), g = Color.green(p), bl = Color.blue(p);
            int dist = Math.max(Math.abs(r - br), Math.max(Math.abs(g - bgG), Math.abs(bl - bb)));
            if (dist < 14) { px[i] = 0; continue; }
            float a = Math.min(1f, dist / soft);
            // The colour as it was before it was blended into the background at the logo's soft edges.
            int cr = clamp(Math.round(br + (r - br) / a)), cg = clamp(Math.round(bgG + (g - bgG) / a)), cb = clamp(Math.round(bb + (bl - bb) / a));
            // Lettering made for a light banner, dark grey or a deep colour (TiviMate's dark teal "mate"), would vanish
            // on navy: white instead. Brighter colours are kept.
            int l = lum(cr, cg, cb);
            if (lightGround && (l < 90 || l < 130 && sat(cr, cg, cb) < 0.25f) || l < 60) { cr = 255; cg = 255; cb = 255; }
            px[i] = Color.argb(Math.round(a * 255), cr, cg, cb);
        }
        Bitmap cut = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        Bitmap trimmed = HomeV2.trim(cut);
        // Next to nothing left, or the whole banner: not a logo on a plain ground after all; the banner as a card.
        float share = trimmed.getWidth() * trimmed.getHeight() / (float) (w * h);
        if (trimmed.getWidth() < 8 || trimmed.getHeight() < 8 || share > 0.92f) return new Object[]{b, true};
        return new Object[]{trimmed, false};
    }

    /** The edge's commonest colour (to within a little), or 0 when the edge is not one colour (a picture, a gradient). */
    private static int edgeColour(int[] px, int w, int h) {
        Map<Integer, Integer> count = new HashMap<>();
        int total = 0;
        for (int x = 0; x < w; x += 2) { total += tally(count, px[x]); total += tally(count, px[(h - 1) * w + x]); }
        for (int y = 0; y < h; y += 2) { total += tally(count, px[y * w]); total += tally(count, px[y * w + w - 1]); }
        int best = 0, bestN = 0;
        for (Map.Entry<Integer, Integer> e : count.entrySet()) if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        if (total == 0 || bestN < total * 0.85f) return 0;
        // Back from the bucket to a colour (the bucket's middle), opaque.
        return Color.rgb(((best >> 16) & 0xFF) * 16 + 8, ((best >> 8) & 0xFF) * 16 + 8, (best & 0xFF) * 16 + 8);
    }

    private static int tally(Map<Integer, Integer> count, int p) {
        if (Color.alpha(p) < 250) return 0;
        int key = ((Color.red(p) / 16) << 16) | ((Color.green(p) / 16) << 8) | (Color.blue(p) / 16);
        count.merge(key, 1, Integer::sum);
        return 1;
    }

    private static int lum(int r, int g, int b) {
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static float sat(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min) / (float) max;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
