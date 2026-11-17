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
 * Home's mini TV: the channel last watched playing in a window at the top right (the live picture goes over the
 * window), on a card of the panel's colour, and under the window LIVE in the accent, the channel's number and name, and
 * what's on with a thin bar of how far it is and the minutes left ("25 min"). Where nothing can play, a line in the
 * window says why (the playlist to add, the channels loading); with no channel and nothing to say, there is no card.
 */
final class MiniTv extends View {
    /** The window the live picture fills, and the card's margin round it and its room under it (design pixels). */
    static final float TV_X = 1412f, TV_Y = 64f, TV_W = 436f, TV_H = 245f, PAD = 12f, UNDER = 86f;

    /** Shown in the window when no channel plays. */
    String note = "";
    private final TvActivity tv;
    private final Paint card = Dark.fill(Palette.PANEL);
    private final Paint window = Dark.fill(0xFF000000), live = Dark.fill(Palette.ACCENT);
    private final TextPaint liveP = Type.paint(15f, Type.BOLD, Palette.ACCENT_INK), nameP = Type.paint(25f, Type.MEDIUM, Palette.TEXT1);
    private final TextPaint onP = Type.paint(22f, Type.REGULAR, Palette.TEXT2), noteP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
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
        c.drawRoundRect(r, Ui.f(16), Ui.f(16), card);
        // The window: black until the picture comes (it goes over this), or the line saying why there is none.
        r.set(Ui.f(TV_X), Ui.f(TV_Y), Ui.f(TV_X + TV_W), Ui.f(TV_Y + TV_H));
        c.drawRoundRect(r, Ui.f(8), Ui.f(8), window);
        if (ch == null) {
            if (!note.equals(noteFor) || noteL == null) {
                noteL = Ui.layout(note, noteP, Ui.px(TV_W - 64), 3);
                noteFor = note;
            }
            Ui.draw(c, noteL, r.left + Ui.f(32), r.centerY() - noteL.getHeight() / 2f);
            return;
        }
        // Under it: LIVE, the channel, and what's on with how far it is and the minutes left.
        float x = Ui.f(TV_X), y = Ui.f(TV_Y + TV_H + 40);
        float lw = liveP.measureText("LIVE") + Ui.f(16);
        r.set(x, y - Ui.f(22), x + lw, y + Ui.f(4));
        c.drawRoundRect(r, Ui.f(5), Ui.f(5), live);
        c.drawText("LIVE", x + Ui.f(8), Type.centred(liveP, r.centerY()), liveP);
        Library lib = Library.get(getContext());
        String name = (com.gathin.homefieldtv.prefs.Look.numbers ? lib.number(ch) + "  " : "") + ch.name;
        c.drawText(Ui.fit(name, nameP, Ui.f(TV_W) - lw - Ui.f(14)), x + lw + Ui.f(14), y, nameP);
        long now = System.currentTimeMillis();
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(now);
        if (i >= 0) {
            String left = Math.max(1, (s.stop[i] - now + 59_999L) / 60_000L) + " min";
            float lwid = onP.measureText(left), bar = Ui.f(64), ly = y + Ui.f(34);
            float room = Ui.f(TV_W) - lwid - bar - Ui.f(32);
            c.drawText(Ui.fit(s.title[i], onP, room), x, ly, onP);
            float done = (now - s.start[i]) / (float) Math.max(1, s.stop[i] - s.start[i]);
            Bars.thin(c, x + Ui.f(TV_W) - lwid - Ui.f(14) - bar, ly - Ui.f(8), bar, Ui.f(6), done);
            c.drawText(left, x + Ui.f(TV_W) - lwid, ly, onP);
        }
    }
}
