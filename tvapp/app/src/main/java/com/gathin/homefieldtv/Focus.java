package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Where the remote is, and what is chosen while it is elsewhere, drawn the same way on every screen:
 *
 * <pre>
 *   pill(c, r)            the accent at 37.5 % (Palette.FOCUS) in a rounded box, radius RADIUS (12): list rows, the
 *                         drawer, groups, menus, pickers; white writing on it. Inset from its column's sides by
 *                         INSET_DRAWER (32: the drawer, groups) or INSET_PANEL (16: Settings, menus, pickers)
 *   selected(c, r)        white at 8.5 % (Palette.SELECTED) in the same shape: the section, group or category on
 *                         screen while the remote is on another
 *   circle(c, cx, cy, d)  the accent at 37.5 % in a circle of diameter d: round buttons (68 in the player's action
 *                         bar, 88 for play/pause)
 *   border(c, r, radius)  a 2 px edge in the light accent: a tile in the row over the video
 * </pre>
 *
 * Focus jumps: the next frame draws it where the remote now is, never sliding from the last item. Sizes are design
 * pixels; the rectangles passed in are screen pixels.
 */
final class Focus {
    private Focus() {}

    static final float RADIUS = 12f, INSET_DRAWER = 32f, INSET_PANEL = 16f, BORDER = 2f;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG), EDGE = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        EDGE.setStyle(Paint.Style.STROKE);
    }

    /** Where the remote is: the accent at 37.5 %. */
    static void pill(Canvas c, RectF r) {
        FILL.setColor(Palette.FOCUS);
        c.drawRoundRect(r, Ui.f(RADIUS), Ui.f(RADIUS), FILL);
    }

    /** Chosen, the remote elsewhere: white at 8.5 %. */
    static void selected(Canvas c, RectF r) {
        FILL.setColor(Palette.SELECTED);
        c.drawRoundRect(r, Ui.f(RADIUS), Ui.f(RADIUS), FILL);
    }

    /** A round button the remote is on (d: its diameter, screen pixels). */
    static void circle(Canvas c, float cx, float cy, float d) {
        FILL.setColor(Palette.FOCUS);
        c.drawCircle(cx, cy, d / 2f, FILL);
    }

    /** A tile the remote is on, over the video: its 2 px light-accent edge, just inside r. */
    static void border(Canvas c, RectF r, float radius) {
        float w = Math.max(1f, Ui.f(BORDER));
        EDGE.setColor(Palette.ACCENT_LIGHT);
        EDGE.setStrokeWidth(w);
        c.drawRoundRect(r.left + w / 2f, r.top + w / 2f, r.right - w / 2f, r.bottom - w / 2f, radius, radius, EDGE);
    }
}
