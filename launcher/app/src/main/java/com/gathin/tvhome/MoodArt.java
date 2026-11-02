package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Random;

/**
 * Surprise me's moods drawn by the launcher, not a film's or a show's poster (which looked like a spin for that one
 * title): each mood its own colours and glow, a faint wallpaper of its sign, a touch of its genre (police tape for
 * crime, stars for science fiction, bubbles for cartoons, rain for a good cry), the sign large, its name, and "Spin".
 * Asked for as a picture address, "mood:tv:2:p" (TV shows' third mood, a poster) or "mood:mv:0:w" (Movies' first,
 * wide, for the widened tile and the picture at the top); HomeV2's loader draws it at the width it needs.
 */
final class MoodArt {
    private MoodArt() { }

    /** {sign, wallpaper sign, top colour, bottom colour, glow, touch}. Touch: 0 none, 1 tape, 2 stars, 3 bubbles, 4 rain. */
    private static final Object[][] TV = {
            {"🛋️", "😂", 0xFFFF7A45, 0xFFB0154F, 0xFFFFD166, 0},   // Sitcom classics
            {"🍩", "🍩", 0xFFFFC93C, 0xFFFF4F9A, 0xFFFFFFFF, 3},   // Animated & silly
            {"☕", "☕", 0xFF1E9A86, 0xFF0B2A57, 0xFFB8F2E6, 0},   // British laughs
            {"🔍", "🔎", 0xFF2B2D42, 0xFF7A0010, 0xFFFF5964, 1},   // Crime of the week
            {"🛸", "✨", 0xFF4B1CB8, 0xFF07071F, 0xFF9D7BFF, 2},   // Sci-fi one-offs
            {"🧸", "🎈", 0xFF4CC9F0, 0xFF2E9E7A, 0xFFFFFFFF, 3}};  // Cartoon comfort
    private static final Object[][] MOVIES = {
            {"🕯️", "🍂", 0xFFF4A261, 0xFF7A2E12, 0xFFFFE8B0, 0},   // Cosy
            {"😱", "⚡", 0xFFD00000, 0xFF140404, 0xFFFF6B6B, 0},   // Edge of my seat
            {"🌀", "🌀", 0xFF7B2FF7, 0xFF00A6D6, 0xFFB5F2FF, 2},   // Mind-bending
            {"😂", "😂", 0xFFFFC300, 0xFFFF5733, 0xFFFFF3B0, 0},   // Big laugh
            {"😢", "💧", 0xFF5E9BFF, 0xFF1A237E, 0xFFCFE3FF, 4},   // Good cry
            {"⚔️", "🏰", 0xFFC9A227, 0xFF4A2C06, 0xFFFFE08A, 0},   // Epic
            {"👻", "🦇", 0xFF1B4332, 0xFF020604, 0xFF74C69D, 0}};  // Scary

    /** The picture for "mood:tv:2:p" at this width (a poster is half again as tall as wide; wide is 16:9). */
    static Bitmap draw(Context c, String url, int width) {
        try {
            String[] p = url.split(":");
            boolean tv = "tv".equals(p[1]);
            int i = Integer.parseInt(p[2]);
            // "w" wide, "b" wide and bare (no name or "Spin": the picture at the top of the page), else a poster.
            boolean bare = p.length > 3 && "b".equals(p[3]);
            boolean wide = bare || p.length > 3 && "w".equals(p[3]);
            Object[][] set = tv ? TV : MOVIES;
            if (i < 0 || i >= set.length) return null;
            String name = tv ? EpisodeDice.MOODS[i][0] : Surprise.MOODS[i][0];
            int w = Math.max(64, Math.min(width, 1600)), h = wide ? Math.round(w * 9 / 16f) : Math.round(w * 1.5f);
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            paint(c, new Canvas(b), w, h, set[i], bare ? null : name, tv, wide, i);
            return b;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static void paint(Context c, Canvas cv, int w, int h, Object[] m, String name, boolean tv, boolean wide, int seed) {
        String sign = (String) m[0], wall = (String) m[1];
        int top = (Integer) m[2], bottom = (Integer) m[3], glow = (Integer) m[4], touch = (Integer) m[5];
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        // The ground: the mood's two colours on a slant, and a glow where the sign stands.
        p.setShader(new LinearGradient(0, 0, w * 0.4f, h, top, bottom, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        float gx = wide ? w * 0.7f : w * 0.5f, gy = wide ? h * 0.45f : h * 0.36f, gr = (wide ? h : w) * 0.75f;
        p.setShader(new RadialGradient(gx, gy, gr, (glow & 0x00FFFFFF) | 0x66000000, glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawCircle(gx, gy, gr, p);
        p.setShader(null);

        // The wallpaper: the mood's small sign, row on row on a tilt, faint.
        Random r = new Random(seed * 31L + (tv ? 7 : 3));
        float unit = (wide ? h : w) * 0.16f;
        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        wp.setTextSize(unit * 0.62f);
        wp.setTextAlign(Paint.Align.CENTER);
        int layer = cv.saveLayerAlpha(0, 0, w, h, 46);
        cv.rotate(-14, w / 2f, h / 2f);
        for (float y = -h * 0.3f, row = 0; y < h * 1.3f; y += unit * 1.15f, row++) {
            for (float x = -w * 0.3f + (row % 2) * unit * 0.6f; x < w * 1.3f; x += unit * 1.2f) cv.drawText(wall, x, y, wp);
        }
        cv.restoreToCount(layer);

        // The genre's touch.
        if (touch == 1) tape(cv, w, h, wide);
        else if (touch == 2) stars(cv, w, h, r);
        else if (touch == 3) bubbles(cv, w, h, r);
        else if (touch == 4) rain(cv, w, h, r);

        // The sign, large, with a soft shadow.
        Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        sp.setTextAlign(Paint.Align.CENTER);
        float size = wide ? h * 0.5f : w * 0.5f;
        sp.setTextSize(size);
        sp.setShadowLayer(size * 0.08f, 0, size * 0.04f, 0x66000000);
        cv.drawText(sign, gx, gy + size * 0.36f, sp);

        if (name == null) return;
        // A shade along the foot for the words, the name, and "Spin".
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, h * (wide ? 0.45f : 0.55f), 0, h, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
        cv.drawRect(0, h * (wide ? 0.45f : 0.55f), w, h, shade);
        Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold);
        Typeface semi = c.getResources().getFont(R.font.inter_semibold);
        Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
        np.setTypeface(heavy);
        np.setColor(Color.WHITE);
        np.setShadowLayer(w * 0.01f, 0, w * 0.005f, 0x80000000);
        float margin = wide ? w * 0.065f : w * 0.08f;
        float nameSize = wide ? h * 0.12f : w * 0.135f;
        np.setTextSize(nameSize);
        // The name on one line where it fits, else on two (at its spaces, the halves as even as they come).
        String[] lines = fit(np, name, w - margin * 2);
        float spinSize = nameSize * (wide ? 0.42f : 0.46f);
        float base = h - margin - spinSize * 1.6f;
        for (int k = lines.length - 1; k >= 0; k--) {
            cv.drawText(lines[k], margin, base, np);
            base -= nameSize * 1.02f;
        }
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTypeface(semi);
        tp.setColor(0xE6FFFFFF);
        tp.setTextSize(spinSize);
        tp.setLetterSpacing(0.04f);
        cv.drawText("🎲  Spin for " + (tv ? "an episode" : "a film"), margin, h - margin, tp);
    }

    private static String[] fit(Paint p, String name, float room) {
        if (p.measureText(name) <= room || !name.contains(" ")) return new String[]{name};
        int best = -1;
        float bestDiff = Float.MAX_VALUE;
        for (int i = name.indexOf(' '); i >= 0; i = name.indexOf(' ', i + 1)) {
            float d = Math.abs(p.measureText(name.substring(0, i)) - p.measureText(name.substring(i + 1)));
            if (d < bestDiff) { bestDiff = d; best = i; }
        }
        return new String[]{name.substring(0, best), name.substring(best + 1)};
    }

    /** Crime: a band of yellow police tape across the corner. */
    private static void tape(Canvas cv, int w, int h, boolean wide) {
        int save = cv.save();
        float band = (wide ? h : w) * 0.09f;
        cv.rotate(-24, w / 2f, h / 2f);
        float y = wide ? h * 0.22f : h * 0.18f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFD60A);
        cv.drawRect(-w, y, w * 2, y + band, p);
        p.setColor(0xFF111111);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        p.setTextSize(band * 0.55f);
        p.setLetterSpacing(0.12f);
        String words = "CASE OF THE WEEK  •  DO NOT CROSS  •  ";
        float step = p.measureText(words);
        for (float x = -w; x < w * 2; x += step) cv.drawText(words, x, y + band * 0.72f, p);
        cv.restoreToCount(save);
    }

    /** Science fiction: a scatter of small stars, a few brighter. */
    private static void stars(Canvas cv, int w, int h, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 70; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.8f, s = (r.nextFloat() * 1.6f + 0.6f) * w / 400f;
            p.setColor(Color.argb(90 + r.nextInt(150), 255, 255, 255));
            cv.drawCircle(x, y, s, p);
        }
    }

    /** Cartoons: soft bubbles rising. */
    private static void bubbles(Canvas cv, int w, int h, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 14; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h * 0.85f, s = (r.nextFloat() * 0.06f + 0.02f) * Math.max(w, h);
            fill.setColor(0x1FFFFFFF);
            cv.drawCircle(x, y, s, fill);
            p.setStrokeWidth(Math.max(1, s * 0.08f));
            p.setColor(0x59FFFFFF);
            cv.drawCircle(x, y, s, p);
            fill.setColor(0x66FFFFFF);
            cv.drawCircle(x - s * 0.35f, y - s * 0.35f, s * 0.18f, fill);
        }
    }

    /** A good cry: thin streaks of rain. */
    private static void rain(Canvas cv, int w, int h, Random r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 46; i++) {
            float x = r.nextFloat() * w * 1.1f, y = r.nextFloat() * h * 0.85f, len = (r.nextFloat() * 0.05f + 0.03f) * h;
            p.setStrokeWidth(Math.max(1, w / 320f));
            p.setColor(Color.argb(50 + r.nextInt(70), 220, 235, 255));
            cv.drawLine(x, y, x - len * 0.25f, y + len, p);
        }
    }
}
