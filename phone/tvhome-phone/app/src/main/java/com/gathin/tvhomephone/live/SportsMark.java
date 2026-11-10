package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Whitebrook Road Sports' own channel mark, the family's name set as a sports broadcaster sets its channels': "whitebrook
 * road" in heavy lower case, "sports" in white on a red tab, then what is on ("premier league", "football", "f1", "main
 * event") and the picture's tag after a fine bar ("| HD"). In one line, or stacked (the name small over what is on, big).
 * What is on follows the game (the Premier League's games "premier league", any other football "football", Formula 1
 * "f1"), unless one is chosen. Drawn on its own plate (stacked: in the colour of what is on), so the channel's logo under
 * it never shows, or plain over the picture (see-through) with a soft shadow, where LogoPatch has hidden the channel's own
 * logo under it. The family's own words; no broadcaster's lettering or emblem.
 */
final class SportsMark {
    /** What can be on it, as the Scoreboard card offers them: "" (Auto, from the game) first. */
    static final String[] WHATS = {"", "premier league", "football", "main event", "f1", "cricket", "golf", "nfl", "arena", "tennis",
            "racing", "darts", "action"};
    static final int RED = 0xFFE10A1E, RED_DEEP = 0xFFB0061A, WHITE = 0xFFFFFFFF, SOFT = 0xD9FFFFFF, NIGHT = 0xFF0A0E1C;

    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            picture = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Typeface heavy, bold, semi;
    private final RectF r = new RectF();
    private final Rect bounds = new Rect();
    private final java.util.Map<Typeface, Float> xs = new java.util.HashMap<>();

    SportsMark(Context c) {
        heavy = ChannelMark.outfit(c, 800);
        bold = ChannelMark.outfit(c, 700);
        semi = ChannelMark.outfit(c, 600);
    }

    /** Pure: what is on for a game of this league (ESPN's slug, "eng.1"), or for Formula 1 (f1 true). */
    static String auto(String slug, boolean f1) {
        if (f1) return "f1";
        String s = slug == null ? "" : slug.toLowerCase(Locale.ROOT);
        if (s.equals("eng.1")) return "premier league";
        if (s.startsWith("nfl")) return "nfl";
        return "football";
    }

    /** Pure: the colour of what is on, for its plate (stacked) and its line under the one-line mark. */
    static int colourOf(String what) {
        switch (what == null ? "" : what) {
            case "premier league": return 0xFF4B1D8C;
            case "main event": return 0xFF00A3D2;
            case "f1": return 0xFFD5121E;
            case "cricket": return 0xFF2E86DE;
            case "golf": return 0xFF0B7A55;
            case "nfl": return 0xFF1C3F94;
            case "arena": return 0xFFF07800;
            case "tennis": return 0xFF8DB600;
            case "racing": return 0xFF7A1FA2;
            case "darts": return 0xFFC2185B;
            case "action": return 0xFF00897B;
            default: return 0xFF1E9A4E;
        }
    }

    /** Pure: the label the Scoreboard card shows for a choice ("Auto, from the game" for ""). */
    static String label(String what) {
        return what == null || what.isEmpty() ? "Auto, from the game" : what;
    }

    /**
     * The mark in the box (x, y, w, h), fitted and set at its left (centred when stacked on a plate): stacked or in one
     * line; with "sports" on its tab or the name alone; what is on; the tag ("" none); on its plate or plain over the
     * picture; the competition's logo (null: none) before the tag.
     */
    void draw(Canvas cv, float x, float y, float w, float h, boolean stacked, boolean sports, String what, String tag, boolean plate,
              Bitmap comp) {
        if (w <= 2 || h <= 2) return;
        String on = what == null || what.isEmpty() ? "football" : what;
        String t = tag == null ? "" : tag.trim();
        int colour = colourOf(on);
        if (plate) {
            float rad = Math.min(w, h) * (stacked ? 0.14f : 0.18f);
            r.set(x, y, x + w, y + h);
            if (stacked) {
                fill.setShader(new LinearGradient(x, y, x + w, y + h, lighten(colour, 0.12f), darken(colour, 0.2f), Shader.TileMode.CLAMP));
            } else {
                fill.setShader(new LinearGradient(x, y, x, y + h, 0xFF141A2E, NIGHT, Shader.TileMode.CLAMP));
            }
            cv.drawRoundRect(r, rad, rad, fill);
            fill.setShader(null);
            if (!stacked) {
                // A line in the colour of what is on along its foot, inside the round corners.
                float lh = Math.max(2f, h * 0.07f);
                fill.setColor(colour);
                cv.drawRect(x + rad * 0.6f, y + h - lh, x + w - rad * 0.6f, y + h, fill);
            }
        }
        float pad = plate ? Math.min(h * (stacked ? 0.16f : 0.2f), w * 0.05f) : Math.min(h * 0.06f, w * 0.02f);
        float bx = x + pad, by = y + pad, bw = w - 2 * pad, bh = h - 2 * pad;
        if (plate) text.clearShadowLayer();
        if (stacked) stacked(cv, bx, by, bw, bh, sports, on, t, plate, comp);
        else line(cv, bx, by, bw, bh, sports, on, t, plate, comp);
        text.clearShadowLayer();
        fill.clearShadowLayer();
    }

    // ---- one line ----------------------------------------------------------------------------------------------------

    private void line(Canvas cv, float x, float y, float w, float h, boolean sports, String on, String tag, boolean plate, Bitmap comp) {
        // Laid out at a size of 1 (x-height 1), then fitted.
        float xh = xOf(heavy), name = width("whitebrook road", heavy, -0.01f) / xh;
        float gap = 0.42f, tabW = sports ? tabW() : 0f, what = width(on, bold, -0.005f) / xOf(bold);
        float compW = comp != null ? 1.5f * comp.getWidth() / Math.max(1f, comp.getHeight()) : 0f;
        float tagW = tag.isEmpty() ? 0f : 0.5f + width(tag, semi, 0.04f) / xOf(semi);
        float total = name + gap + (sports ? tabW + gap : 0f) + what + (compW > 0 ? gap + compW : 0f) + (tagW > 0 ? gap + tagW : 0f);
        // Ascenders and descenders: about 2.2 x-heights from top to bottom.
        float s = Math.min(w / total, h / 2.25f);
        float base = y + h / 2f + s * 0.5f, left = x;
        shadow(plate, s);
        left = words(cv, "whitebrook road", heavy, -0.01f, s, WHITE, left, base) + gap * s;
        if (sports) left = tab(cv, left, base, s, plate) + gap * s;
        left = words(cv, on, bold, -0.005f, s, WHITE, left, base);
        if (compW > 0) {
            left += gap * s;
            float ch = 1.5f * s, cw = compW * s;
            r.set(left, base - s * 0.5f - ch / 2f, left + cw, base - s * 0.5f + ch / 2f);
            cv.drawBitmap(comp, null, r, picture);
            left += cw;
        }
        if (tagW > 0) tagAfter(cv, left + gap * s, base, s, tag);
    }

    // ---- stacked -----------------------------------------------------------------------------------------------------

    private void stacked(Canvas cv, float x, float y, float w, float h, boolean sports, String on, String tag, boolean plate, Bitmap comp) {
        // The name small on top (with "sports" on its tab), what is on big under it, the tag beside what is on.
        float top = 0.58f, gap = 0.42f;
        float nameW = width("whitebrook road", heavy, -0.01f) / xOf(heavy) * top + (sports ? (gap + tabW()) * top : 0f);
        float whatW = width(on, heavy, -0.015f) / xOf(heavy);
        float compW = comp != null ? 1.4f * comp.getWidth() / Math.max(1f, comp.getHeight()) : 0f;
        float tagW = tag.isEmpty() ? 0f : 0.5f + width(tag, semi, 0.04f) / xOf(semi) * 0.8f;
        float line2 = whatW + (compW > 0 ? gap + compW : 0f) + (tagW > 0 ? gap + tagW : 0f);
        // Two lines: the small one (2.2 x-heights at 0.58) over the big one, a little apart.
        float tall = 2.2f * top + 0.35f + 2.1f;
        float s = Math.min(w / Math.max(nameW, line2), h / tall);
        float blockW = Math.max(nameW, line2) * s, blockH = tall * s;
        float left = plate ? x + (w - blockW) / 2f : x, y0 = y + (h - blockH) / 2f;
        shadow(plate, s);
        float base1 = y0 + 1.55f * top * s, ss = s * top;
        float after = words(cv, "whitebrook road", heavy, -0.01f, ss, WHITE, left, base1);
        if (sports) tab(cv, after + gap * ss, base1, ss, plate);
        float base2 = y0 + blockH - 0.62f * s;
        float l2 = words(cv, on, heavy, -0.015f, s, WHITE, left, base2);
        if (compW > 0) {
            l2 += gap * s;
            float ch = 1.4f * s, cw = compW * s;
            r.set(l2, base2 - s * 0.5f - ch / 2f, l2 + cw, base2 - s * 0.5f + ch / 2f);
            cv.drawBitmap(comp, null, r, picture);
            l2 += cw;
        }
        if (tagW > 0) tagAfter(cv, l2 + gap * s, base2, s * 0.8f, tag);
    }

    // ---- its parts ---------------------------------------------------------------------------------------------------

    /** The red tab's width at x-height 1. */
    private float tabW() {
        return width("sports", bold, 0f) / xOf(bold) * 0.92f + 0.7f;
    }

    /** "sports" in white on its red tab from left, on the line base, at x-height s: the tab's right end. */
    private float tab(Canvas cv, float left, float base, float s, boolean plate) {
        float tw = tabW() * s, top = base - s * 1.62f, bottom = base + s * 0.52f;
        r.set(left, top, left + tw, bottom);
        float rad = s * 0.36f;
        fill.setShader(new LinearGradient(left, top, left, bottom, RED, RED_DEEP, Shader.TileMode.CLAMP));
        if (!plate) fill.setShadowLayer(s * 0.5f, 0, s * 0.08f, 0x66000000);
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        fill.clearShadowLayer();
        // The words on the tab, a touch smaller, on its own middle: no shadow on the red.
        float ws = s * 0.92f;
        text.clearShadowLayer();
        words(cv, "sports", bold, 0f, ws, WHITE, left + (tw - width("sports", bold, 0f) / xOf(bold) * ws) / 2f,
                (top + bottom) / 2f + ws * 0.5f);
        shadow(plate, s);
        return left + tw;
    }

    /** A fine bar, then the tag lighter, from left on the line base. */
    private void tagAfter(Canvas cv, float left, float base, float s, String tag) {
        float bar = Math.max(1f, s * 0.12f);
        fill.setColor(0xB3FFFFFF);
        cv.drawRect(left, base - s * 1.45f, left + bar, base + s * 0.35f, fill);
        words(cv, tag, semi, 0.04f, s, SOFT, left + bar + s * 0.38f, base);
    }

    private void shadow(boolean plate, float s) {
        if (plate) text.clearShadowLayer();
        else text.setShadowLayer(Math.max(1f, s * 0.45f), 0, s * 0.06f, 0x8C000000);
    }

    /** s in lower case at x-height size from left on base: where it ends. */
    private float words(Canvas cv, String s, Typeface t, float spacing, float size, int colour, float left, float base) {
        float px = size / xOf(t);
        text.setTypeface(t);
        text.setTextSize(px);
        text.setLetterSpacing(spacing);
        text.setColor(colour);
        text.setTextAlign(Paint.Align.LEFT);
        cv.drawText(s, left, base, text);
        float wd = text.measureText(s);
        text.setLetterSpacing(0f);
        return left + wd;
    }

    /** s's width at a text size of 1. */
    private float width(String s, Typeface t, float spacing) {
        text.setTypeface(t);
        text.setTextSize(100f);
        text.setLetterSpacing(spacing);
        float wd = text.measureText(s) / 100f;
        text.setLetterSpacing(0f);
        return wd;
    }

    /** The x-height (an "x"'s height) at a text size of 1. */
    private float xOf(Typeface t) {
        Float k = xs.get(t);
        if (k == null) {
            text.setTypeface(t);
            text.setTextSize(100f);
            text.getTextBounds("x", 0, 1, bounds);
            k = Math.max(0.2f, bounds.height() / 100f);
            xs.put(t, k);
        }
        return k;
    }

    static int lighten(int c, float k) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r += Math.round((255 - r) * k);
        g += Math.round((255 - g) * k);
        b += Math.round((255 - b) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int darken(int c, float k) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r = Math.round(r * (1 - k));
        g = Math.round(g * (1 - k));
        b = Math.round(b * (1 - k));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
