package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A title's name as the top of its page shows it: its logo where TMDB has one (read at the size it is drawn, its own
 * shape kept), else big lettering made for it: the name in tall capitals, silver, on one line or two balanced ones, with
 * its small joining words ("of", "the", "and") small and red beside the big ones, as a title's own logo often has them.
 * Both sit on the foot of their box. Laid out once for a name and a box (set), drawn many times.
 */
final class Lettering {
    /** The joining words drawn small. */
    static final Set<String> SMALL = new HashSet<>(Arrays.asList("OF", "THE", "AND", "&", "A", "AN", "IN", "ON", "TO", "FOR", "AT", "WITH"));
    private static final float CAP = 0.70f, PITCH = 0.84f, LITTLE = 0.42f;

    private String name = "", logo = "";
    private float boxW, boxH;
    private String[] words = new String[0];
    private float[] wx = new float[0], wy = new float[0];
    private boolean[] small = new boolean[0];
    private int[] line = new int[0];
    private TextPaint[] lines = new TextPaint[0];
    private TextPaint little;
    private float height;
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    /** Laid out for a name and a box (screen pixels), the biggest capitals no more than max. */
    void set(String title, float w, float h, float max) {
        String t = title == null ? "" : title.trim();
        if (t.equals(name) && w == boxW && h == boxH && lines.length > 0) return;
        name = t;
        boxW = w;
        boxH = h;
        String[] tokens = t.toUpperCase(Locale.UK).split("\\s+");
        List<String> ws = new ArrayList<>();
        for (String s : tokens) if (!s.isEmpty()) ws.add(s);
        if (ws.isEmpty()) ws.add("");
        words = ws.toArray(new String[0]);
        small = new boolean[words.length];
        for (int i = 0; i < words.length; i++) small[i] = words.length > 1 && SMALL.contains(words[i]) && i < words.length - 1;
        TextPaint big = paint(max);
        little = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        little.setTypeface(Cine.condensed());
        little.setColor(Cine.RED);
        little.setShadowLayer(Ui.f(10f), 0, Ui.f(3f), 0x80000000);
        float size = max;
        int[] split = null;
        for (int tries = 0; tries < 30; tries++) {
            big.setTextSize(size);
            little.setTextSize(size * LITTLE);
            split = split(big, w);
            if (split != null && split.length * size * PITCH <= h) break;
            size *= 0.93f;
            split = null;
        }
        if (split == null) {
            // Very long: the smallest size, as many lines as it needs.
            big.setTextSize(size);
            little.setTextSize(size * LITTLE);
            split = greedy(big, w);
        }
        int n = split.length;
        line = new int[words.length];
        wx = new float[words.length];
        wy = new float[words.length];
        lines = new TextPaint[n];
        float pitch = size * PITCH;
        height = (n - 1) * pitch + size * CAP;
        float baseline = h - (n - 1) * pitch;
        for (int l = 0; l < n; l++) {
            int from = l == 0 ? 0 : split[l - 1], to = split[l];
            float bl = baseline + l * pitch;
            TextPaint p = paint(size);
            p.setShader(new LinearGradient(0, bl - size * CAP, 0, bl, new int[]{0xFFFFFFFF, 0xFFF1F3F7, 0xFFFFFFFF, 0xFFDCE1E8},
                    new float[]{0f, 0.46f, 0.54f, 1f}, Shader.TileMode.CLAMP));
            lines[l] = p;
            float at = 0;
            for (int i = from; i < to; i++) {
                line[i] = l;
                wx[i] = at;
                wy[i] = small[i] ? bl - size * CAP + size * LITTLE * CAP : bl;
                at += width(i, p) + gap(i, to, size);
            }
        }
    }

    /** TMDB's logo for the title ("" for none: the lettering). */
    void logo(String url) {
        logo = url == null ? "" : url;
    }

    /** How tall the lettering is (its logo's height can be less). */
    float height() {
        return height;
    }

    /** Drawn in its box, its top left at (x, y), at an alpha (0-255); ready: when the logo arrives. */
    void draw(Canvas c, float x, float y, int alpha, Runnable ready) {
        if (!logo.isEmpty()) {
            Bitmap b = Pics.get(logo, Math.round(boxW), Math.round(boxH), Pics.FIT, ready);
            if (b != null) {
                bmp.setAlpha(alpha);
                c.drawBitmap(b, x, y + boxH - b.getHeight(), bmp);
                return;
            }
            if (!Pics.failed(logo)) return;
        }
        for (TextPaint p : lines) p.setAlpha(alpha);
        little.setAlpha(alpha);
        for (int i = 0; i < words.length; i++) {
            c.drawText(words[i], x + wx[i], y + wy[i], small[i] ? little : lines[line[i]]);
        }
    }

    private TextPaint paint(float size) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(Cine.condensed());
        p.setTextSize(size);
        p.setLetterSpacing(0.015f);
        p.setColor(Cine.TEXT);
        p.setShadowLayer(Ui.f(12f), 0, Ui.f(3f), 0xB3000000);
        return p;
    }

    private float width(int i, TextPaint big) {
        return (small[i] ? little : big).measureText(words[i]);
    }

    private float gap(int i, int end, float size) {
        if (i >= end - 1) return 0;
        return small[i] || small[i + 1] ? size * 0.10f : size * 0.20f;
    }

    private float run(int from, int to, TextPaint big) {
        float w = 0;
        for (int i = from; i < to; i++) w += width(i, big) + gap(i, to, big.getTextSize());
        return w;
    }

    /** The lines' ends (word indexes): one line, or the two most even ones; null when they don't fit in w. */
    private int[] split(TextPaint big, float w) {
        int n = words.length;
        if (run(0, n, big) <= w) return new int[]{n};
        int best = -1;
        float bestMax = Float.MAX_VALUE;
        for (int k = 1; k < n; k++) {
            float a = run(0, k, big), b = run(k, n, big);
            if (a > w || b > w) continue;
            float m = Math.max(a, b);
            if (m < bestMax) {
                bestMax = m;
                best = k;
            }
        }
        return best < 0 ? null : new int[]{best, n};
    }

    /** Pure line-filling, for a name too long for two lines. */
    private int[] greedy(TextPaint big, float w) {
        List<Integer> ends = new ArrayList<>();
        int from = 0;
        for (int i = 1; i <= words.length; i++) {
            if (i == words.length || run(from, i + 1, big) > w) {
                ends.add(i);
                from = i;
            }
        }
        int[] out = new int[ends.size()];
        for (int i = 0; i < out.length; i++) out[i] = ends.get(i);
        return out;
    }
}
