package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

/**
 * Chips, as the screens draw them (positions in screen pixels, sizes in design pixels):
 *
 * <pre>
 *   media(c, x, cy, text)            → width: a media chip on the banner and the info cards (FHD, 50 FPS, STEREO): solid
 *                                      #999, 30 high, radius 5, its letters bold (Type.CHIP_BOLD) in the panel's colour;
 *                                      put them GAP (8) apart
 *   rating(c, x, cy, text)           → width: a film's rating on a poster's foot ("7.4"): the accent, its ink (white)
 *   grey(c, x, cy, text)             → width: a rating among a title's facts ("5.8"): white at 16 % with TEXT1 writing
 *   filter(c, r, text, focused, on)  a filter (search's playlists): fully rounded, the focus pill where the remote is,
 *                                      SELECTED when chosen, TEXT1 writing (ROW size)
 *   history(c, r, text, focused)     a past search: fully rounded (54 high), the panel's colour at 60 % over the ground,
 *                                      TEXT2 writing (30 px); the focus pill where the remote is
 * </pre>
 */
final class Chips {
    private Chips() {}

    static final float HEIGHT = 30f, GAP = 8f, PAD = 9f, RADIUS = 5f, FILTER_H = 54f;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static TextPaint bold, small, row, past;
    private static float madeFor;

    /** The paints, made again when the screen's scale or the text size changed. */
    private static void paints() {
        float k = Ui.s * com.gathin.homefieldtv.prefs.Look.text;
        if (bold != null && k == madeFor) return;
        madeFor = k;
        bold = Type.paint(Type.CHIP_BOLD, Type.BOLD, 0);
        small = Type.paint(Type.CHIP, Type.MEDIUM, 0);
        row = Type.paint(Type.ROW, Type.REGULAR, 0);
        past = Type.paint(30f, Type.REGULAR, 0);
    }

    private static float chip(Canvas c, float x, float cy, String text, int back, TextPaint p, int ink) {
        float h = Ui.f(HEIGHT), w = p.measureText(text) + Ui.f(PAD * 2);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(back);
        c.drawRoundRect(R, Ui.f(RADIUS), Ui.f(RADIUS), FILL);
        p.setColor(ink);
        c.drawText(text, x + Ui.f(PAD), Type.centred(p, cy), p);
        return w;
    }

    /** FHD, 50 FPS, STEREO: solid grey, the letters in the panel's colour. */
    static float media(Canvas c, float x, float cy, String text) {
        paints();
        return chip(c, x, cy, text, Palette.CHIP, bold, Palette.PANEL);
    }

    /** A poster's rating: the accent with its ink. */
    static float rating(Canvas c, float x, float cy, String text) {
        paints();
        return chip(c, x, cy, text, Palette.ACCENT, small, Palette.ACCENT_INK);
    }

    /** A rating among a title's facts: white at 16 %. */
    static float grey(Canvas c, float x, float cy, String text) {
        paints();
        return chip(c, x, cy, text, 0x29FFFFFF, small, Palette.TEXT1);
    }

    /** The width a chip of this text takes (media chips), to lay a row of them out. */
    static float width(String text) {
        paints();
        return bold.measureText(text) + Ui.f(PAD * 2);
    }

    /** A filter: the focus pill where the remote is, SELECTED when chosen, else just its words on a quiet fill. */
    static void filter(Canvas c, RectF r, String text, boolean focused, boolean chosen) {
        paints();
        float rad = r.height() / 2f;
        FILL.setColor(focused ? Palette.FOCUS : chosen ? 0x33FFFFFF : Palette.SELECTED);
        c.drawRoundRect(r, rad, rad, FILL);
        row.setColor(focused || chosen ? Palette.TEXT1 : Palette.TEXT2);
        float tw = row.measureText(text);
        c.drawText(Ui.fit(text, row, r.width() - Ui.f(32)), r.centerX() - Math.min(tw, r.width() - Ui.f(32)) / 2f,
                Type.centred(row, r.centerY()), row);
    }

    /** A past search: the panel's colour at 60 % (the focus pill where the remote is), grey writing. */
    static void history(Canvas c, RectF r, String text, boolean focused) {
        paints();
        float rad = r.height() / 2f;
        FILL.setColor(focused ? Palette.FOCUS : Palette.alpha(Palette.PANEL, 0x99));
        c.drawRoundRect(r, rad, rad, FILL);
        past.setColor(focused ? Palette.TEXT1 : Palette.TEXT2);
        c.drawText(Ui.fit(text, past, r.width() - Ui.f(48)), r.left + Ui.f(24), Type.centred(past, r.centerY()), past);
    }
}
