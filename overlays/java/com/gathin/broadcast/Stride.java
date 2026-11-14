package com.gathin.broadcast;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Stride, the family's own made-up kit sponsor (no real company), as a slim strap under the scoreboard's pop-outs on the
 * TV and the phone: "SPONSORED BY" in small spaced capitals, then Stride's mark (two strokes leaning forward, a short
 * one behind and a long one ahead, as the legs of a running stride, the back one trailing off) and its name in heavy
 * slanted capitals; a light sweeps across it as it comes out. Drawn, not an image.
 */
public final class Stride {
    private Stride() { }

    /** The strap's height for a pop-out h high, and the gap above it. */
    public static int strapHeight(int h) {
        return Math.max(10, Math.round(h * 0.46f));
    }

    public static int gap(int h) {
        return Math.max(2, Math.round(h * 0.08f));
    }

    private static final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            small = new Paint(Paint.ANTI_ALIAS_FLAG), name = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG),
            shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path legs = new Path();
    private static final RectF box = new RectF();

    static {
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setColor(0xB3E8EAF2);
        name.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
        name.setColor(0xFFFFFFFF);
        name.setTextSkewX(-0.22f);
        line.setStyle(Paint.Style.STROKE);
        mark.setStyle(Paint.Style.FILL);
    }

    /** Pure: how wide the strap's words and mark are at height h (its ends' room included). */
    public static float width(float h) {
        small.setTextSize(h * 0.31f);
        small.setLetterSpacing(0.22f);
        name.setTextSize(h * 0.62f);
        name.setLetterSpacing(0.04f);
        return h * 0.55f + small.measureText("SPONSORED BY") + h * 0.45f + markW(h) + h * 0.16f + name.measureText("STRIDE") + h * 0.6f;
    }

    private static float markW(float h) {
        return h * 0.95f;
    }

    /**
     * The strap with its top left at (x, y), w wide at most and h high: its ground (the board's), a fine light edge, the
     * words and the mark; sweep 0..1 the light passing across (outside that: none). Its end on the right (alignRight) or
     * the left, as the pop-out's far end is.
     */
    public static void draw(Canvas cv, float x, float y, float w, float h, int groundColour, boolean alignRight, float sweep) {
        float need = Math.min(w, width(h));
        float left = alignRight ? x + w - need : x;
        box.set(left, y, left + need, y + h);
        float r = h / 2f;
        ground.setShader(null);
        ground.setColor(groundColour);
        cv.drawRoundRect(box, r, r, ground);
        line.setStrokeWidth(Math.max(1f, h * 0.05f));
        line.setColor(0x33FFFFFF);
        cv.drawRoundRect(box, r, r, line);
        cv.save();
        cv.clipRect(box);
        float cy = y + h / 2f, px = left + h * 0.55f;
        // SPONSORED BY
        small.setTextSize(h * 0.31f);
        small.setLetterSpacing(0.22f);
        Paint.FontMetrics sm = small.getFontMetrics();
        cv.drawText("SPONSORED BY", px, cy - (sm.ascent + sm.descent) / 2f, small);
        px += small.measureText("SPONSORED BY") + h * 0.45f;
        // The mark: two strokes leaning forward, the back one short and trailing off, the front one long.
        float mw = markW(h), top = y + h * 0.16f, bottom = y + h * 0.84f, t = h * 0.17f;
        legs.reset();
        // back leg: from low behind to half height
        float bx = px, by = bottom, lean = mw * 0.42f;
        legs.moveTo(bx, by);
        legs.lineTo(bx + t, by);
        legs.lineTo(bx + t + lean * 0.62f, by - (bottom - top) * 0.58f);
        legs.lineTo(bx + lean * 0.62f + t * 0.25f, by - (bottom - top) * 0.58f);
        legs.close();
        mark.setShader(new LinearGradient(bx, by, bx + lean, by - (bottom - top) * 0.58f, 0x55FFFFFF, 0xFFFFFFFF, Shader.TileMode.CLAMP));
        cv.drawPath(legs, mark);
        legs.reset();
        // front leg: from the ground ahead up to the top, longer and steeper forward
        float fx = px + mw * 0.34f;
        legs.moveTo(fx, bottom);
        legs.lineTo(fx + t, bottom);
        legs.lineTo(fx + t + mw * 0.50f, top);
        legs.lineTo(fx + mw * 0.50f + t * 0.2f, top);
        legs.close();
        mark.setShader(null);
        mark.setColor(0xFFFFFFFF);
        cv.drawPath(legs, mark);
        px += mw + h * 0.16f;
        // STRIDE
        name.setTextSize(h * 0.62f);
        name.setLetterSpacing(0.04f);
        Paint.FontMetrics nm = name.getFontMetrics();
        cv.drawText("STRIDE", px, cy - (nm.ascent + nm.descent) / 2f, name);
        // The light passing across as it comes out.
        if (sweep > 0f && sweep < 1f) {
            float sx = box.left - h * 2 + (box.width() + h * 4) * sweep;
            shine.setShader(new LinearGradient(sx - h * 1.2f, 0, sx + h * 1.2f, 0, new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            cv.drawRect(box, shine);
        }
        cv.restore();
    }
}
