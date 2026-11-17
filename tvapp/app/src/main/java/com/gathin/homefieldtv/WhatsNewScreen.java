package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/**
 * What's new, once, on the first start after an update (Updates.whatsNew), as the panels' questions are drawn
 * (DialogCard): the screen dimmed by the scrim, a card of the panel's colour in the middle with "What's new" in the
 * accent, "Homefield TV 1.13" (PICKER size, medium), the notes in grey under it, and OK in the focus pill. OK or Back
 * closes it. The full notes stay in Settings → About → Updates.
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
        private static final float W = 960f, PAD = 48f;
        private final Paint scrim = new Paint(), card = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint kicker = Type.paint(Type.SMALL, Type.MEDIUM, Palette.ACCENT), title = Type.paint(Type.PICKER, Type.MEDIUM, Palette.TEXT1);
        private final TextPaint body = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2), ok = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
        private final RectF r = new RectF();
        private StaticLayout lines;

        Card(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            scrim.setColor(Palette.SCRIM);
            c.drawRect(0, 0, getWidth(), getHeight(), scrim);
            if (lines == null) lines = Ui.layout(notes, body, Ui.px(W - 2 * PAD), 12);
            float textH = lines.getHeight() / Ui.s;
            float h = PAD + 30f + 52f + 20f + textH + 32f + RowList.ROW_1 + Focus.INSET_PANEL;
            float x = (1920f - W) / 2f, y = Math.max(60f, (1080f - h) / 2f);
            r.set(Ui.f(x), Ui.f(y), Ui.f(x + W), Ui.f(y + h));
            card.setColor(Palette.PANEL);
            c.drawRoundRect(r, Ui.f(16), Ui.f(16), card);
            float tx = Ui.f(x + PAD);
            kicker.setColor(Palette.ACCENT);
            c.drawText("What's new", tx, Ui.f(y + PAD + 18f), kicker);
            title.setColor(Palette.TEXT1);
            c.drawText(Ui.fit("Homefield TV " + name, title, Ui.f(W - 2 * PAD)), tx, Ui.f(y + PAD + 70f), title);
            body.setColor(Palette.TEXT2);
            Ui.draw(c, lines, tx, Ui.f(y + PAD + 30f + 52f + 20f));
            // OK, where the remote is: the focus pill, 16 px in from the card's sides.
            float by = y + PAD + 30f + 52f + 20f + textH + 32f;
            r.set(Ui.f(x + Focus.INSET_PANEL), Ui.f(by), Ui.f(x + W - Focus.INSET_PANEL), Ui.f(by + RowList.ROW_1));
            Focus.pill(c, r);
            ok.setColor(Palette.TEXT1);
            c.drawText("OK", Ui.f(x + RowList.TEXT_X), Type.centred(ok, r.centerY()), ok);
        }
    }
}
