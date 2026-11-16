package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/**
 * What's new, once, on the first start after an update (Updates.whatsNew): a card over Home with the version's name
 * and its notes, and OK. OK or Back closes it.
 */
final class WhatsNewScreen extends Screen {
    private final String name, notes;

    WhatsNewScreen(TvActivity tv, String name, String notes) {
        super(tv);
        this.name = name == null ? "" : name;
        this.notes = notes == null ? "" : notes;
    }

    @Override View build() {
        return new Card(tv);
    }

    @Override void ok() {
        tv.pop();
    }

    private final class Card extends View {
        private static final float W = 960f, PAD = 64f;
        private final Paint shade = Dark.fill(Dark.alpha(Dark.GROUND, 0xC8)), card = Dark.fill(Dark.CARD), edge = Dark.line(Dark.LINE, 1.5f);
        private final Paint button = Dark.fill(Dark.ACCENT), ring = Dark.line(Dark.alpha(Dark.RING, 0xE6), 2.5f);
        private final TextPaint kicker = Dark.text(24, 600, Dark.ACCENT), title = Dark.text(44, 600, Dark.TEXT);
        private final TextPaint body = Dark.text(28, 400, Dark.SOFT), ok = Dark.text(28, 600, Dark.ACCENT_INK);
        private final RectF r = new RectF();
        private StaticLayout lines;

        Card(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), shade);
            if (lines == null) lines = Ui.layout(notes, body, Math.round(Ui.f(W - 2 * PAD)), 9);
            float h = 64 + 40 + 64 + lines.getHeight() / Ui.s + 56 + 64 + PAD;
            float x = (1920f - W) / 2f, y = Math.max(80f, (1080f - h) / 2f);
            r.set(Ui.f(x), Ui.f(y), Ui.f(x + W), Ui.f(y + h));
            c.drawRoundRect(r, Ui.f(28), Ui.f(28), card);
            c.drawRoundRect(r, Ui.f(28), Ui.f(28), edge);
            float tx = Ui.f(x + PAD);
            c.drawText("WHAT'S NEW", tx, Ui.f(y + 88), kicker);
            c.drawText(Ui.fit("Homefield TV " + name, title, Ui.f(W - 2 * PAD)), tx, Ui.f(y + 146), title);
            Ui.draw(c, lines, tx, Ui.f(y + 190));
            float by = y + 190 + lines.getHeight() / Ui.s + 48;
            String label = "OK";
            float bw = Math.max(Ui.f(160), ok.measureText(label) + Ui.f(80));
            r.set(tx, Ui.f(by), tx + bw, Ui.f(by + 64));
            c.drawRoundRect(r, Ui.f(32), Ui.f(32), button);
            Dark.ring(c, r, Ui.f(32), ring);
            c.drawText(label, r.centerX() - ok.measureText(label) / 2f, r.centerY() + Ui.f(10), ok);
        }
    }
}
