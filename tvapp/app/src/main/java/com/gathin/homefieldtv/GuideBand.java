package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

/**
 * The TV guide's band at the top, as in the family's recording: the programme the remote is on, its title large, then
 * the channel (its number and name, a heart for a favourite, HD or 4K as its name says), its times and what's left of
 * it, and three lines of what it is about. Its picture lies at the top right behind it (a Backdrop), and the band comes
 * in again, faded and risen, each time the remote moves to another programme. The channel number being typed shows at
 * its right.
 */
final class GuideBand extends View {
    private static final float X = GuideGrid.X, TEXT_W = 860f;

    private final Library lib;
    private Channel ch;
    private int programme = -1;
    private boolean favourite;
    private String typed = "", shownKey = "";
    private StaticLayout descL;

    private final Paint track = Dark.fill(0x40FFFFFF), accent = Dark.fill(Dark.ACCENT);
    private final Paint chip = Dark.line(0x99FFFFFF, 1.5f), heart = Dark.fill(Dark.ACCENT), heartLine = Dark.line(Dark.ACCENT, 2.2f);
    private final Paint typedBack = Dark.fill(0xF0141417);
    private final TextPaint titleP = Dark.text(48, 600, Dark.TEXT), chanP = Dark.text(24, 500, Dark.MUTED);
    private final TextPaint timeP = Dark.text(24, 600, Dark.SOFT), descP = Dark.text(26, 400, Dark.SOFT);
    private final TextPaint chipP = Dark.text(18, 700, Dark.SOFT), typedP = Dark.text(44, 600, Dark.TEXT);
    private final RectF r = new RectF();

    GuideBand(Context c, Library lib) {
        super(c);
        this.lib = lib;
    }

    /** The programme the remote is on (index in its channel's schedule, -1 for none); its words come in again when it changes. */
    void show(Channel c, int index, boolean isFavourite) {
        Guide.Schedule s = c == null ? null : lib.schedule(c.key);
        String key = c == null ? "" : c.key + "|" + (s != null && index >= 0 && index < s.start.length ? s.start[index] : -1);
        boolean other = !key.equals(shownKey);
        ch = c;
        programme = index;
        favourite = isFavourite;
        if (other) {
            shownKey = key;
            descL = null;
            if (c != null) Fade.in(this);
        }
        invalidate();
    }

    void typed(String digits) {
        typed = digits == null ? "" : digits;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        if (!typed.isEmpty()) drawTyped(c);
        if (ch == null) return;
        long now = System.currentTimeMillis();
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s != null && programme >= 0 && programme < s.start.length ? programme : -1;
        float x = Ui.f(X), w = Ui.f(TEXT_W);

        // Its title.
        c.drawText(Ui.fit(i >= 0 ? s.title[i] : ch.name, titleP, w), x, Ui.f(98), titleP);

        // The channel, the times and what's left.
        float lx = x;
        String chan = lib.number(ch) + "  " + ch.name;
        String chanFit = Ui.fit(chan, chanP, Ui.f(420));
        c.drawText(chanFit, lx, Ui.f(142), chanP);
        lx += chanP.measureText(chanFit) + Ui.f(10);
        if (favourite) {
            Dark.icon(c, Dark.HEART_FULL, lx + Ui.f(11), Ui.f(134), Ui.f(22), heartLine, heart);
            lx += Ui.f(32);
        }
        String q = LiveText.quality(ch.name);
        if (!q.isEmpty()) {
            float cw = chipP.measureText(q) + Ui.f(16);
            r.set(lx, Ui.f(120), lx + cw, Ui.f(147));
            c.drawRoundRect(r, Ui.f(6), Ui.f(6), chip);
            c.drawText(q, lx + Ui.f(8), Ui.f(140), chipP);
            lx += cw + Ui.f(16);
        } else {
            lx += Ui.f(6);
        }
        if (i >= 0) {
            String day = Ui.day(s.start[i], now);
            String span = LiveText.span(s.start[i], s.stop[i]);
            if (!"Today".equals(day)) span = day + ", " + span;
            c.drawText(span, lx, Ui.f(142), timeP);
            lx += timeP.measureText(span) + Ui.f(16);
            if (s.start[i] <= now && now < s.stop[i]) {
                float by = Ui.f(134), bw = Ui.f(90);
                r.set(lx, by, lx + bw, by + Ui.f(5));
                c.drawRoundRect(r, Ui.f(3), Ui.f(3), track);
                r.set(lx, by, lx + bw * LiveText.progress(now, s.start[i], s.stop[i]), by + Ui.f(5));
                c.drawRoundRect(r, Ui.f(3), Ui.f(3), accent);
                c.drawText(LiveText.left(now, s.stop[i]), lx + bw + Ui.f(12), Ui.f(142), chanP);
            } else {
                c.drawText(LiveText.startsIn(now, s.start[i]), lx, Ui.f(142), chanP);
            }
        } else {
            c.drawText(s == null ? "No programme information" : "Nothing in the guide at this time", lx, Ui.f(142), chanP);
        }

        // What it is about.
        if (i >= 0 && !s.desc[i].isEmpty()) {
            if (descL == null) descL = Ui.layout(s.desc[i], descP, Math.round(w), 3);
            Ui.draw(c, descL, x, Ui.f(162));
        }
    }

    /** The channel number being typed, "10-" until it is done. */
    private void drawTyped(Canvas c) {
        r.set(Ui.f(GuideGrid.RIGHT - 230), Ui.f(46), Ui.f(GuideGrid.RIGHT), Ui.f(136));
        c.drawRoundRect(r, Ui.f(16), Ui.f(16), typedBack);
        String d = typed.length() >= 3 ? typed : (typed + "---").substring(0, 3);
        c.drawText(d, r.centerX() - typedP.measureText(d) / 2f, r.centerY() + Ui.f(16), typedP);
    }
}
