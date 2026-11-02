package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * The Kids page's YouTube Kids card, drawn by the launcher (it has no poster of its own): a sunny gradient with soft
 * confetti, the app's own icon when it is on the TV (else a white play badge), "YouTube Kids" and what OK does.
 * Asked for as a picture address, "ytkids:p" (a poster), "ytkids:w" (wide, for the widened card) or "ytkids:b" (the
 * picture at the top of the page, without its words); HomeV2's loader draws it at the width it needs.
 */
final class KidsArt {
    static final String YOUTUBE_KIDS = "com.google.android.youtube.tvkids";

    private KidsArt() { }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(YOUTUBE_KIDS, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Bitmap draw(Context c, String url, int width) {
        String shape = url.substring(url.indexOf(':') + 1);
        boolean poster = shape.startsWith("p"), bare = shape.startsWith("b");
        int w = Math.max(64, width), h = poster ? Math.round(w * 1.5f) : Math.round(w * 9f / 16f);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFFFF5E5B, 0xFFFF8E3C, 0xFFFFC93C}, null, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.75f, h * 0.2f, Math.max(w, h) * 0.6f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // Confetti: soft dots and rings, the same each time.
        Random r = new Random(7);
        int[] colours = {0x66FFFFFF, 0x553DDC84, 0x554F8BFF, 0x55FFFFFF, 0x669B5CFF};
        for (int i = 0; i < 26; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = (0.008f + r.nextFloat() * 0.022f) * Math.max(w, h);
            p.setColor(colours[i % colours.length]);
            p.setStyle(i % 3 == 0 ? Paint.Style.STROKE : Paint.Style.FILL);
            p.setStrokeWidth(rad * 0.35f);
            cv.drawCircle(x, y, rad, p);
        }
        p.setStyle(Paint.Style.FILL);
        float unit = Math.min(w, h);
        // The mark: the app's own icon on the TV, else a white badge with a red play sign.
        float badge = unit * (poster ? 0.42f : bare ? 0.38f : 0.34f);
        float bx = (w - badge) / 2f, by = poster ? h * 0.24f : bare ? (h - badge) / 2f : h * 0.16f;
        Drawable icon = null;
        if (installed(c)) {
            try {
                icon = HomeV2.fullSquare(c, c.getPackageManager().getApplicationIcon(YOUTUBE_KIDS), Math.round(badge));
            } catch (Exception ignored) {
                icon = null;
            }
        }
        RectF box = new RectF(bx, by, bx + badge, by + badge);
        p.setColor(0x33000000);
        cv.drawRoundRect(new RectF(box.left, box.top + unit * 0.02f, box.right, box.bottom + unit * 0.02f), badge * 0.24f, badge * 0.24f, p);
        if (icon != null) {
            int save = cv.save();
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(box, badge * 0.24f, badge * 0.24f, android.graphics.Path.Direction.CW);
            cv.clipPath(clip);
            icon.setBounds(Math.round(box.left), Math.round(box.top), Math.round(box.right), Math.round(box.bottom));
            icon.draw(cv);
            cv.restoreToCount(save);
        } else {
            p.setColor(0xFFFFFFFF);
            cv.drawRoundRect(box, badge * 0.24f, badge * 0.24f, p);
            float pw = badge * 0.52f, ph = badge * 0.36f, px = box.centerX() - pw / 2f, py = box.centerY() - ph / 2f;
            p.setColor(0xFFFF2D2D);
            cv.drawRoundRect(new RectF(px, py, px + pw, py + ph), ph * 0.3f, ph * 0.3f, p);
            android.graphics.Path tri = new android.graphics.Path();
            float tx = box.centerX() - ph * 0.18f, ts = ph * 0.42f;
            tri.moveTo(tx, box.centerY() - ts / 2f);
            tri.lineTo(tx + ts * 0.9f, box.centerY());
            tri.lineTo(tx, box.centerY() + ts / 2f);
            tri.close();
            p.setColor(0xFFFFFFFF);
            cv.drawPath(tri, p);
        }
        if (bare) return b;
        // Its name, and what OK does.
        Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold);
        p.setTypeface(heavy);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(unit * 0.02f, 0, unit * 0.008f, 0x40000000);
        p.setTextSize(unit * (poster ? 0.15f : 0.12f));
        float ty = box.bottom + unit * (poster ? 0.2f : 0.17f);
        if (poster) {
            cv.drawText("YouTube", w / 2f, ty, p);
            cv.drawText("Kids", w / 2f, ty + p.getTextSize() * 1.05f, p);
            ty += p.getTextSize() * 1.05f;
        } else {
            cv.drawText("YouTube Kids", w / 2f, ty, p);
        }
        p.clearShadowLayer();
        p.setTypeface(c.getResources().getFont(R.font.inter_semibold));
        p.setTextSize(unit * (poster ? 0.062f : 0.05f));
        p.setColor(0xF2FFFFFF);
        String note = installed(c) ? "OK to open" : "OK to get it on Google Play";
        cv.drawText(note, w / 2f, ty + unit * (poster ? 0.12f : 0.09f), p);
        return b;
    }
}
