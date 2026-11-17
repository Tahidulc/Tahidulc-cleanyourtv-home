package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

/**
 * Home's mini TV in the dark look: the channel last watched playing in a window at the top right (the live picture
 * goes over the window), on a dark card with a thin edge, and under the window LIVE in amber, the channel's number and
 * name, and what's on with what's left of it. Where nothing can play, a line in the window says why (the playlist to
 * add, the channels loading); with no channel and nothing to say, there is no card at all.
 */
final class MiniTv extends View {
    /** The window the live picture fills, and the card's margin round it and its room under it (design pixels). */
    static final float TV_X = 1412f, TV_Y = 64f, TV_W = 436f, TV_H = 245f, PAD = 12f, UNDER = 82f;

    /** Shown in the window when no channel plays. */
    String note = "";
    private final TvActivity tv;
    private final Paint card = Dark.fill(Dark.alpha(Dark.RAIL, 0xF2)), edge = Dark.line(Dark.LINE, 1.5f);
    private final Paint window = Dark.fill(0xFF000000), live = Dark.fill(Dark.ACCENT);
    private final TextPaint liveP = Dark.text(16, 700, Dark.ACCENT_INK), nameP = Dark.text(23, 600, Dark.TEXT);
    private final TextPaint onP = Dark.text(20, 400, Dark.SOFT), noteP = Dark.text(24, 500, Dark.SOFT);
    private final RectF r = new RectF();
    private String noteFor;
    private StaticLayout noteL;

    MiniTv(TvActivity tv) {
        super(tv);
        this.tv = tv;
        liveP.setLetterSpacing(0.08f);
    }

    /** The window, in screen pixels. */
    Rect tvRect() {
        return new Rect(Ui.px(TV_X), Ui.px(TV_Y), Ui.px(TV_X + TV_W), Ui.px(TV_Y + TV_H));
    }

    @Override protected void onDraw(Canvas c) {
        Channel ch = tv.live.channel();
        if (ch == null && note.isEmpty()) return;
        r.set(Ui.f(TV_X - PAD), Ui.f(TV_Y - PAD), Ui.f(TV_X + TV_W + PAD), Ui.f(TV_Y + TV_H + (ch == null ? PAD : UNDER)));
        c.drawRoundRect(r, Ui.f(22), Ui.f(22), card);
        c.drawRoundRect(r, Ui.f(22), Ui.f(22), edge);
        // The window: black until the picture comes (it goes over this), or the line saying why there is none.
        r.set(Ui.f(TV_X), Ui.f(TV_Y), Ui.f(TV_X + TV_W), Ui.f(TV_Y + TV_H));
        c.drawRoundRect(r, Ui.f(4), Ui.f(4), window);
        if (ch == null) {
            if (!note.equals(noteFor) || noteL == null) {
                noteL = Ui.layout(note, noteP, Ui.px(TV_W - 64), 3);
                noteFor = note;
            }
            Ui.draw(c, noteL, r.left + Ui.f(32), r.centerY() - noteL.getHeight() / 2f);
            return;
        }
        // Under it: LIVE, the channel, and what's on with what's left.
        float x = Ui.f(TV_X), y = Ui.f(TV_Y + TV_H + 40);
        float lw = liveP.measureText("LIVE") + Ui.f(18);
        r.set(x, y - Ui.f(21), x + lw, y + Ui.f(5));
        c.drawRoundRect(r, Ui.f(6), Ui.f(6), live);
        c.drawText("LIVE", x + Ui.f(9), y - Ui.f(2), liveP);
        Library lib = Library.get(getContext());
        String name = (com.gathin.homefieldtv.prefs.Look.numbers ? lib.number(ch) + "  " : "") + ch.name;
        c.drawText(Ui.fit(name, nameP, Ui.f(TV_W) - lw - Ui.f(14)), x + lw + Ui.f(14), y, nameP);
        long now = System.currentTimeMillis();
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(now);
        if (i >= 0) {
            String on = s.title[i] + "  ·  " + LiveText.left(now, s.stop[i]);
            c.drawText(Ui.fit(on, onP, Ui.f(TV_W)), x, y + Ui.f(30), onP);
        }
    }
}
