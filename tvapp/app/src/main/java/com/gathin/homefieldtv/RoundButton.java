package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

/**
 * Round buttons, as the player's action bar, its transport row and a title's page draw them (positions in screen
 * pixels, sizes in design pixels; icons are Dark.icon's kinds, or SPORTS for SportsIcon's ball):
 *
 * <pre>
 *   bar(c, cx, cy, icon, label, focused)    an action-bar item: its icon (40) in the focus circle (68) where the remote is,
 *                                            its label under it (Type.CHIP, 48 below the icon's middle): TEXT1 where the
 *                                            remote is, TEXT2 otherwise. Items about BAR_PITCH (160) apart; a label can
 *                                            be the setting's value ("1920 × 1080", "Off")
 *   transport(c, cx, cy, icon, focused)      play/pause and the skips: a 72 px circle of white at 8 %, or the 88 px focus
 *                                            circle where the remote is
 *   icon(c, cx, cy, icon, focused)           a title page's round icon buttons (72): white at 8.5 %, or the focus circle
 *   play(c, r, label, focused)               the "▶ Play" pill on a title's page: the accent with its ink, a play shape and
 *                                            the label (ROW, medium); lighter where the remote is
 * </pre>
 */
final class RoundButton {
    private RoundButton() {}

    static final float BAR_FOCUS = 68f, BAR_ICON = 40f, BAR_LABEL = 48f, BAR_PITCH = 160f;
    static final float TRANSPORT = 72f, TRANSPORT_FOCUS = 88f, ICON = 72f;
    /** SportsIcon's ball, beside Dark's icons. */
    static final int SPORTS = -2;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG), STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static TextPaint label, play;
    private static float madeFor;

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
    }

    private static void paints() {
        float k = Ui.s * com.gathin.homefieldtv.prefs.Look.text;
        if (label != null && k == madeFor) return;
        madeFor = k;
        label = Type.paint(Type.CHIP, Type.REGULAR, 0);
        play = Type.paint(Type.ROW, Type.MEDIUM, 0);
    }

    /** An icon in a colour, size s (screen pixels). */
    static void glyph(Canvas c, int icon, float cx, float cy, float s, int colour) {
        STROKE.setColor(colour);
        STROKE.setStrokeWidth(Math.max(1f, s * 0.075f));
        FILL.setColor(colour);
        if (icon == SPORTS) SportsIcon.draw(c, cx, cy, s, STROKE, FILL);
        else Dark.icon(c, icon, cx, cy, s, STROKE, FILL);
    }

    static void bar(Canvas c, float cx, float cy, int icon, String text, boolean focused) {
        paints();
        if (focused) Focus.circle(c, cx, cy, Ui.f(BAR_FOCUS));
        glyph(c, icon, cx, cy, Ui.f(BAR_ICON), focused ? Palette.TEXT1 : Palette.TEXT2);
        if (text == null || text.isEmpty()) return;
        label.setColor(focused ? Palette.TEXT1 : Palette.TEXT2);
        String t = Ui.fit(text, label, Ui.f(BAR_PITCH + 40));
        c.drawText(t, cx - label.measureText(t) / 2f, cy + Ui.f(BAR_LABEL + 10), label);
    }

    static void transport(Canvas c, float cx, float cy, int icon, boolean focused) {
        if (focused) {
            Focus.circle(c, cx, cy, Ui.f(TRANSPORT_FOCUS));
        } else {
            FILL.setColor(0x14FFFFFF);
            c.drawCircle(cx, cy, Ui.f(TRANSPORT) / 2f, FILL);
        }
        glyph(c, icon, cx, cy, Ui.f(36), focused ? Palette.TEXT1 : 0xFFB3B0AD);
    }

    static void icon(Canvas c, float cx, float cy, int icon, boolean focused) {
        if (focused) {
            Focus.circle(c, cx, cy, Ui.f(ICON));
        } else {
            FILL.setColor(Palette.SELECTED);
            c.drawCircle(cx, cy, Ui.f(ICON) / 2f, FILL);
        }
        glyph(c, icon, cx, cy, Ui.f(32), Palette.TEXT1);
    }

    static void play(Canvas c, RectF r, String text, boolean focused) {
        paints();
        float rad = r.height() / 2f;
        FILL.setColor(Palette.ACCENT);
        c.drawRoundRect(r, rad, rad, FILL);
        if (focused) {
            // Where the remote is: the pill a shade lighter.
            FILL.setColor(0x26FFFFFF);
            c.drawRoundRect(r, rad, rad, FILL);
        }
        play.setColor(Palette.ACCENT_INK);
        float tw = play.measureText(text), s = Ui.f(24), gap = Ui.f(12);
        float x = r.centerX() - (s + gap + tw) / 2f;
        glyph(c, Dark.PLAY, x + s / 2f, r.centerY(), s, Palette.ACCENT_INK);
        c.drawText(text, x + s + gap, Type.centred(play, r.centerY()), play);
    }

    /** A rectangle for play(): x, y and size in design pixels. */
    static RectF box(float x, float y, float w, float h) {
        R.set(Ui.f(x), Ui.f(y), Ui.f(x + w), Ui.f(y + h));
        return R;
    }
}
