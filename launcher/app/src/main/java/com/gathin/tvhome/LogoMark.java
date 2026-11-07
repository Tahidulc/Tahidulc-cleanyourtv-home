package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * The cover BoardCover puts over the channel's logo, as the phone's player has it (Homefield's LogoCover): dark smoked
 * glass, all but solid so none of the logo shows through, in one of three looks: Glass (the Homefield house and play
 * mark with the name beside it when there is room), Mark (the mark alone) or Plain (the glass alone). Kind to the panel:
 * only dark grounds and a soft white mark, never the phone's bright violet-to-coral tile, and an edge of its ground
 * (inset) round everything, so the pixel or two it moves against burn-in never uncovers the logo.
 */
final class LogoMark extends View {
    /** Its looks, as the Scoreboard card names them. */
    enum Look {
        GLASS("Glass, with the mark"), MARK("Mark only"), PLAIN("Plain");

        final String label;

        Look(String label) {
            this.label = label;
        }

        static Look of(String name) {
            for (Look l : values()) if (l.name().equals(name)) return l;
            return GLASS;
        }
    }

    private static final int GROUND = 0xFA0B0F24, PLAIN_GROUND = 0xFC07080F, MARK_INK = 0xFFD5DAEA;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG),
            words = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path house = new Path(), play = new Path(), drawn = new Path();
    /** The play button's colour: the cover's own ground, painted (never a hole, so the logo under it never shows). */
    private final Paint button = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private Look look = Look.GLASS;
    private int inset;
    private boolean outlined;
    /** The ring's dashes, made once for a width of line. */
    private float dashFor = -1;

    LogoMark(Context c) {
        super(c);
        // The phone's mark (its 108-unit icon): a house with a play button in it, drawn with round corners.
        house.moveTo(31, 75);
        house.lineTo(31, 50);
        house.lineTo(54, 31);
        house.lineTo(77, 50);
        house.lineTo(77, 75);
        house.close();
        play.moveTo(48, 51);
        play.lineTo(48, 71);
        play.lineTo(65, 61);
        play.close();
        ink.setColor(MARK_INK);
        ink.setStyle(Paint.Style.FILL_AND_STROKE);
        ink.setStrokeJoin(Paint.Join.ROUND);
        button.setStyle(Paint.Style.FILL_AND_STROKE);
        button.setStrokeJoin(Paint.Join.ROUND);
        words.setTypeface(ScoreCard.outfit(c, 700));
        words.setColor(MARK_INK);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(BoardBug.INK);
        setContentDescription("Homefield");
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void look(Look l) {
        if (l == look) return;
        look = l;
        invalidate();
    }

    void inset(int px) {
        inset = px;
        invalidate();
    }

    /** Ringed while Move and resize has it. */
    void outline(boolean on) {
        if (on == outlined) return;
        outlined = on;
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float rad = Math.min(w, h) * 0.18f;
        r.set(0, 0, w, h);
        fill.setColor(look == Look.PLAIN ? PLAIN_GROUND : GROUND);
        cv.drawRoundRect(r, rad, rad, fill);
        float cw = w - 2 * inset, ch = h - 2 * inset;
        if (look != Look.PLAIN && cw > 0 && ch > 0) {
            float s = ch * 0.78f;
            boolean name = look == Look.GLASS && cw / ch >= 2.2f;
            float x = name ? inset + ch * 0.14f : inset + (cw - s) / 2;
            mark(cv, x, inset + (ch - s) / 2, s);
            if (name) {
                float left = x + s * 1.14f, room = inset + cw - left - ch * 0.2f;
                float size = fit("Homefield", room, ch * 0.44f);
                words.setTextSize(size);
                Paint.FontMetrics fm = words.getFontMetrics();
                cv.drawText("Homefield", left, inset + ch / 2 - (fm.ascent + fm.descent) / 2, words);
            }
        }
        if (outlined) {
            float sw = Math.max(2f, Math.min(w, h) * 0.05f);
            if (sw != dashFor) {
                dashFor = sw;
                edge.setStrokeWidth(sw);
                edge.setPathEffect(new DashPathEffect(new float[]{sw * 3, sw * 2}, 0));
            }
            r.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
            cv.drawRoundRect(r, rad, rad, edge);
        }
    }

    /** The mark filling a square of side s at (x, y): its house is about 51 of the icon's units across. */
    private void mark(Canvas cv, float x, float y, float s) {
        float k = s / 52f;
        m.reset();
        m.setScale(k, k);
        m.postTranslate(x - 28 * k, y - 28 * k);
        house.transform(m, drawn);
        ink.setStrokeWidth(5 * k);
        cv.drawPath(drawn, ink);
        play.transform(m, drawn);
        button.setColor(GROUND | 0xFF000000);
        button.setStrokeWidth(2 * k);
        cv.drawPath(drawn, button);
    }

    /** The biggest text size up to most at which the words fit in room. */
    private float fit(String s, float room, float most) {
        words.setTextSize(most);
        float wd = words.measureText(s);
        return wd <= room ? most : Math.max(6, most * room / Math.max(1, wd));
    }
}
