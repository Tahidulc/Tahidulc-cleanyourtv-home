package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Progress lines (positions and sizes in screen pixels):
 *
 * <pre>
 *   thin(c, x, cy, w, h, done)          how far a programme is: FILL on TRACK, round ends. 80 x 6 in the guide's header
 *                                       and the info cards, 424 x 4 under a list row, about 40 x 4 on the banner
 *   seek(c, x0, x1, cy, at, now, live)  the player's seek line, 6 px: the accent up to the play position (at), then
 *                                       SEEK_AIRED on to now (what catch-up can go back to), SEEK_LIVE after; a white
 *                                       knob (18 px) at the play position. at and now are shares of the line (0 to 1);
 *                                       live: watching live, so the line is the programme's and nothing is aired-only
 * </pre>
 *
 * Use Ui.f for sizes: thin(c, x, cy, Ui.f(80), Ui.f(6), 0.4f).
 */
final class Bars {
    private Bars() {}

    static final float SEEK = 6f, KNOB = 18f;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);

    static void thin(Canvas c, float x, float cy, float w, float h, float done) {
        float k = Math.max(0f, Math.min(1f, done)), r = h / 2f;
        P.setColor(Palette.TRACK);
        c.drawRoundRect(x, cy - r, x + w, cy + r, r, r, P);
        if (k <= 0f) return;
        P.setColor(Palette.FILL);
        c.drawRoundRect(x, cy - r, x + Math.max(h, w * k), cy + r, r, r, P);
    }

    static void seek(Canvas c, float x0, float x1, float cy, float at, float now, boolean live) {
        float h = Math.max(2f, Ui.f(SEEK)), r = h / 2f, w = x1 - x0;
        float a = x0 + w * Math.max(0f, Math.min(1f, at)), n = x0 + w * Math.max(0f, Math.min(1f, now));
        P.setColor(Palette.SEEK_LIVE);
        c.drawRect(x0, cy - r, x1, cy + r, P);
        if (!live && n > a) {
            P.setColor(Palette.SEEK_AIRED);
            c.drawRect(a, cy - r, n, cy + r, P);
        }
        P.setColor(Palette.ACCENT);
        c.drawRect(x0, cy - r, a, cy + r, P);
        P.setColor(Palette.KNOB);
        c.drawCircle(a, cy, Ui.f(KNOB) / 2f, P);
    }
}
