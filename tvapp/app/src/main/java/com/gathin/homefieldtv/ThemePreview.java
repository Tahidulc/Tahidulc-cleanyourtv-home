package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

/**
 * Settings, Theme: a small picture of the app in the theme the remote is on, under the list, changing as the remote
 * moves (each colour blending into the next over a quarter of a second): the guide's categories, its band with a
 * programme's times and what's left, three rows of programmes with the one the remote is on lit inside its ring and
 * the line at now, a poster with its New badge and picture chip, the buttons, and the tint the blue screens take.
 */
final class ThemePreview extends View {
    /** Where it lies (design pixels): under the theme list. */
    static final float X = 820f, Y = 678f, W = 1040f, H = 328f;
    private static final long BLEND_MS = 260;

    // A palette's colours, in this order.
    private static final int GROUND = 0, RAIL = 1, PANEL = 2, CARD = 3, CARD_HI = 4, ACCENT = 5, INK = 6, SOFT = 7, BADGE = 8,
            RING = 9, DEEP0 = 10, DEEP1 = 11, DEEP2 = 12, BLUE_TOP = 13, BLUE_FOOT = 14, COUNT = 15;

    private static final String[] CATEGORIES = {"All channels", "Favourites", "Sport", "Films", "News"};
    private static final String[][] ROWS = {{"Morning Mix", "The Evening Show", "Late Film"}, {"Kick-off Live", "Match Night", "Highlights"},
            {"World Today", "Tonight's News", "Weather"}};
    /** Where each row's middle programme starts, as a share of the row. */
    private static final float[] CUT = {0.34f, 0.46f, 0.30f};
    private static final String[] NUMBERS = {"101", "102", "103"};
    private final RectF posterR = new RectF(), blueR = new RectF();
    private String caption = "";

    private int[] from = palette(Theme.ALL[0]), to = from;
    /** The colours drawn this frame (from blended into to). */
    private final int[] now = new int[COUNT];
    private int shown = -1;
    private long start;
    private LinearGradient posterShade, blueShade;
    private int posterFor = 1, blueFor = 1;

    private final Paint fill = Dark.fill(0), edge = Dark.line(0x26FFFFFF, 1.5f), ring = Dark.line(0xFFFFFFFF, 2.5f);
    private final Paint aired = Dark.fill(0x59000000), track = Dark.fill(0x40FFFFFF), shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint railP = Dark.text(17, 500, Dark.SOFT), railOn = Dark.text(17, 600, 0xFFFFFFFF);
    private final TextPaint titleP = Dark.text(28, 600, 0xFFFFFFFF), lineP = Dark.text(16, 500, Dark.MUTED);
    private final TextPaint cellP = Dark.text(16, 500, Dark.SOFT), cellOn = Dark.text(16, 600, 0xFFFFFFFF);
    private final TextPaint numP = Dark.text(16, 600, Dark.MUTED), badgeP = Dark.text(14, 700, 0), chipP = Dark.text(13, 800, 0xFF111114);
    private final TextPaint buttonP = Dark.text(16, 600, 0), blueP = Ui.paint(22, true, Ui.WHITE), blueSub = Ui.paint(17, false, Ui.DIM);
    private final TextPaint captionP = Ui.paint(24, false, Ui.DIM);
    private final RectF r = new RectF();

    ThemePreview(Context c) {
        super(c);
    }

    /** The theme the remote is on: blended into from what is showing now. */
    void show(int index) {
        int i = Math.max(0, Math.min(Theme.ALL.length - 1, index));
        if (i == shown) return;
        boolean first = shown < 0;
        shown = i;
        caption = Theme.ALL[i].name + ", as it will look";
        blend();
        from = first ? palette(Theme.ALL[i]) : now.clone();
        to = palette(Theme.ALL[i]);
        start = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Pure: a theme's colours as the picture draws them (the guide's colours mixed as Theme.apply mixes them). */
    static int[] palette(Theme t) {
        int[] p = new int[COUNT];
        p[GROUND] = t.ground;
        p[RAIL] = t.rail;
        p[PANEL] = t.panel;
        p[CARD] = t.card;
        p[CARD_HI] = t.cardHi;
        p[ACCENT] = t.accent;
        p[INK] = t.accentInk;
        p[SOFT] = t.accentSoft;
        p[BADGE] = t.badge;
        p[RING] = t.ring;
        for (int k = 0; k < 3; k++) {
            int d = Theme.DEEP[k * 2];
            p[DEEP0 + k] = t.tintShare > 0f ? Theme.mix(d, t.tint, t.tintShare) : d;
        }
        p[BLUE_TOP] = Theme.shift(0xFF0C4FAE, t.hue, t.sat, t.val);
        p[BLUE_FOOT] = Theme.shift(0xFF06347C, t.hue, t.sat, t.val);
        return p;
    }

    /** now: from blended into to as far as the time says. */
    private void blend() {
        float k = Exec.inline ? 1f : Math.min(1f, (SystemClock.uptimeMillis() - start) / (float) BLEND_MS);
        k = Fade.EASE.getInterpolation(k);
        for (int i = 0; i < COUNT; i++) now[i] = Theme.mix(from[i], to[i], k);
    }

    /** The poster's and the blue screens' shading for these colours (made again only when they change). */
    private void shaders(RectF poster, RectF blue) {
        int pk = now[DEEP1] * 31 * 31 + now[DEEP2] * 31 + now[GROUND];
        if (posterShade == null || pk != posterFor) {
            posterShade = new LinearGradient(0, poster.top, 0, poster.bottom, new int[]{now[DEEP1], now[DEEP2], now[GROUND]},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            posterFor = pk;
        }
        int bk = now[BLUE_TOP] * 31 + now[BLUE_FOOT];
        if (blueShade == null || bk != blueFor) {
            blueShade = new LinearGradient(blue.left, blue.top, blue.right, blue.bottom, now[BLUE_TOP], now[BLUE_FOOT], Shader.TileMode.CLAMP);
            blueFor = bk;
        }
    }

    private RectF box(float x, float y, float w, float h) {
        r.set(Ui.f(x), Ui.f(y), Ui.f(x + w), Ui.f(y + h));
        return r;
    }

    private void paint(int colour) {
        fill.setShader(null);
        fill.setColor(colour);
    }

    @Override protected void onDraw(Canvas c) {
        if (shown < 0) return;
        blend();
        int[] p = now;
        boolean moving = !Exec.inline && SystemClock.uptimeMillis() - start < BLEND_MS;

        c.drawText(caption, Ui.f(X), Ui.f(Y - 14), captionP);

        // The app's ground, its rail of categories.
        paint(p[GROUND]);
        c.drawRoundRect(box(X, Y, W, H), Ui.f(18), Ui.f(18), fill);
        paint(p[RAIL]);
        c.drawRoundRect(box(X, Y, 176, H), Ui.f(18), Ui.f(18), fill);
        c.drawRect(box(X + 150, Y, 26, H), fill);
        for (int i = 0; i < CATEGORIES.length; i++) {
            float ty = Y + 34 + i * 44;
            boolean on = i == 2;
            if (on) {
                paint(p[CARD_HI]);
                c.drawRoundRect(box(X + 10, ty - 26, 156, 38), Ui.f(10), Ui.f(10), fill);
                paint(p[ACCENT]);
                c.drawRoundRect(box(X + 10, ty - 20, 5, 26), Ui.f(3), Ui.f(3), fill);
            }
            c.drawText(CATEGORIES[i], Ui.f(X + 26), Ui.f(ty), on ? railOn : railP);
        }

        // The band: a programme, its channel and times, what's left of it.
        float bx = X + 196;
        c.drawText("The Evening Show", Ui.f(bx), Ui.f(Y + 44), titleP);
        String chan = "101  Channel One   18:00 – 19:00";
        c.drawText(chan, Ui.f(bx), Ui.f(Y + 74), lineP);
        float px = bx + lineP.measureText(chan) / Ui.s + 14;
        c.drawRoundRect(box(px, Y + 66, 80, 5), Ui.f(3), Ui.f(3), track);
        paint(p[ACCENT]);
        c.drawRoundRect(box(px, Y + 66, 48, 5), Ui.f(3), Ui.f(3), fill);
        c.drawText("25 min left", Ui.f(px + 92), Ui.f(Y + 74), lineP);

        // Three rows of programmes, the remote on one, the line at now.
        float gx = bx, gy = Y + 98, rowH = 60, gap = 10, chanW = 74, gw = 560;
        for (int row = 0; row < 3; row++) {
            float y = gy + row * (rowH + gap);
            paint(p[PANEL]);
            c.drawRoundRect(box(gx, y, chanW, rowH), Ui.f(10), Ui.f(10), fill);
            c.drawText(NUMBERS[row], Ui.f(gx + 18), Ui.f(y + rowH / 2 + 6), numP);
            float cx = gx + chanW + 6, room = gw - chanW - 6;
            for (int k = 0; k < 3; k++) {
                float a = k == 0 ? 0f : k == 1 ? CUT[row] : CUT[row] + 0.36f, b = k == 0 ? CUT[row] : k == 1 ? CUT[row] + 0.36f : 1f;
                float x0 = cx + room * a, x1 = cx + room * b - 6;
                boolean on = row == 0 && k == 1;
                int base = on ? p[CARD_HI] : k == 1 ? p[DEEP0 + row] : p[CARD];
                paint(base);
                RectF cell = box(x0, y, x1 - x0, rowH);
                c.drawRoundRect(cell, Ui.f(10), Ui.f(10), fill);
                if (k == 0) {
                    c.drawRoundRect(cell, Ui.f(10), Ui.f(10), aired);
                } else if (k == 1 && on) {
                    // The part already shown, in the accent's lighter shade.
                    paint(Dark.alpha(p[SOFT], 0x33));
                    c.drawRoundRect(box(x0, y, (x1 - x0) * 0.45f, rowH), Ui.f(10), Ui.f(10), fill);
                }
                TextPaint tp = on ? cellOn : cellP;
                c.drawText(Ui.fit(ROWS[row][k], tp, Ui.f(x1 - x0 - 24)), Ui.f(x0 + 12), Ui.f(y + rowH / 2 + 6), tp);
                if (on) {
                    ring.setColor(Dark.alpha(p[RING], 0xE6));
                    Dark.ring(c, box(x0, y, x1 - x0, rowH), Ui.f(10), ring);
                }
            }
        }
        float nowX = gx + chanW + 6 + (gw - chanW - 6) * 0.52f;
        paint(p[ACCENT]);
        c.drawRect(box(nowX - 1.5f, gy - 8, 3, 3 * rowH + 2 * gap + 12), fill);
        c.drawCircle(Ui.f(nowX), Ui.f(gy - 8), Ui.f(5), fill);

        // A poster with its New badge and picture chip.
        float qx = X + 776, qy = Y + 22, qw = 112, qh = 168;
        posterR.set(box(qx, qy, qw, qh));
        blueR.set(box(qx, Y + 204, W - (qx - X) - 16, 100));
        shaders(posterR, blueR);
        fill.setColor(0xFFFFFFFF);
        fill.setShader(posterShade);
        c.drawRoundRect(posterR, Ui.f(9), Ui.f(9), fill);
        fill.setShader(null);
        paint(p[BADGE]);
        c.drawRoundRect(box(qx + 7, qy + 7, 40, 20), Ui.f(5), Ui.f(5), fill);
        badgeP.setColor(p[INK]);
        c.drawText("New", Ui.f(qx + 13), Ui.f(qy + 22), badgeP);
        paint(0xFFFFFFFF);
        c.drawRoundRect(box(qx + qw - 33, qy + 7, 26, 18), Ui.f(4), Ui.f(4), fill);
        c.drawText("4K", Ui.f(qx + qw - 29), Ui.f(qy + 21), chipP);

        // The buttons: the one the remote is on in the accent.
        float ux = qx + qw + 12, uw = W - (ux - X) - 16;
        paint(p[ACCENT]);
        c.drawRoundRect(box(ux, qy, uw, 40), Ui.f(20), Ui.f(20), fill);
        buttonP.setColor(p[INK]);
        drawCentred(c, "Watch", ux, uw, qy + 26, buttonP);
        paint(p[CARD_HI]);
        c.drawRoundRect(box(ux, qy + 50, uw, 40), Ui.f(20), Ui.f(20), fill);
        buttonP.setColor(0xFFFFFFFF);
        drawCentred(c, "My list", ux, uw, qy + 76, buttonP);

        // The blue screens' tint (Home, Search, Settings).
        shade.setShader(blueShade);
        c.drawRoundRect(blueR, Ui.f(12), Ui.f(12), shade);
        c.drawText("Home", blueR.left + Ui.f(16), blueR.top + Ui.f(40), blueP);
        c.drawText("Search, Settings", blueR.left + Ui.f(16), blueR.top + Ui.f(70), blueSub);

        c.drawRoundRect(box(X, Y, W, H), Ui.f(18), Ui.f(18), edge);
        if (moving) postInvalidateOnAnimation();
    }

    private void drawCentred(Canvas c, String s, float x, float w, float base, TextPaint p) {
        c.drawText(s, Ui.f(x) + (Ui.f(w) - p.measureText(s)) / 2f, Ui.f(base), p);
    }
}
