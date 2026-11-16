package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;

/**
 * The tall blue panel down the left of Home and the TV guide: the Homefield wordmark and the time, the mini TV
 * (the live picture shows through where it is), and a menu under it with the highlight on one line.
 */
final class Panel extends View {
    static final float W = 505f, TV_X = 60f, TV_Y = 168f, TV_W = 445f, TV_H = 250f;
    private static final float ITEM = 67f;

    final List<String> items = new ArrayList<>();
    int focus;
    boolean active = true;
    /** Where the first item's middle is, and how far down the menu may go (design pixels). */
    float first = 640f, bottom = 1062f;
    /** Shown in the mini TV's place when no channel plays. */
    String note = "";

    private final Paint strip = new Paint(), edge = new Paint(), shade = new Paint(), tvBack = new Paint(), tvLine = new Paint();
    private final TextPaint word = Ui.paint(56, true, Ui.WHITE), time = Ui.paint(34, false, Ui.WHITE);
    private final TextPaint item = Ui.paint(34, false, Ui.DIM), itemOn = Ui.paint(34, true, Ui.WHITE);
    private final TextPaint noteP = Ui.paint(26, false, Ui.DIM);
    private final Paint arrow = Ui.stroke(Ui.FAINT, 2.5f), dot = Ui.fill(Dark.ACCENT);
    private final Glow glow = new Glow();
    private final RectF r = new RectF();
    private float scroll, scrollTo;
    private long last;

    Panel(Context c) {
        super(c);
        strip.setColor(Ui.STRIP);
        edge.setColor(Ui.tint(0x804F92DA));
        tvBack.setColor(Ui.tint(0xFF07306E));
        tvLine.setStyle(Paint.Style.STROKE);
        tvLine.setStrokeWidth(Math.max(1f, Ui.f(1.5f)));
        tvLine.setColor(0x55FFFFFF);
        word.setShader(new LinearGradient(0, Ui.f(62), 0, Ui.f(116), 0xFFFFFFFF, Ui.tint(0xFF9CD4FF), Shader.TileMode.CLAMP));
        word.setLetterSpacing(-0.02f);
    }

    /** The mini TV, in screen pixels. */
    Rect tvRect() {
        return new Rect(Ui.px(TV_X), Ui.px(TV_Y), Ui.px(TV_X + TV_W), Ui.px(TV_Y + TV_H));
    }

    void setItems(List<String> list, int focusOn) {
        items.clear();
        items.addAll(list);
        focus = Math.max(0, Math.min(focusOn, items.size() - 1));
        settleScroll(true);
        invalidate();
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    /** Up and Down along the menu; false at its ends. */
    boolean key(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && focus > 0) {
            focus--;
        } else if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus < items.size() - 1) {
            focus++;
        } else {
            return false;
        }
        settleScroll(false);
        invalidate();
        return true;
    }

    private void settleScroll(boolean now) {
        float y = first + focus * ITEM;
        float max = bottom - ITEM / 2;
        float want = scrollTo;
        if (y - want > max) want = y - max;
        if (y - want < first) want = y - first;
        if (want < 0) want = 0;
        scrollTo = want;
        if (now || Exec.inline) scroll = want;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        shade.setShader(new LinearGradient(Ui.f(W), 0, Ui.f(58), h, Ui.PANEL_TOP, Ui.PANEL_BOTTOM, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        int h = getHeight();
        long now = System.currentTimeMillis();
        c.drawRect(0, 0, Ui.f(58), h, strip);
        c.drawRect(Ui.f(58), 0, Ui.f(W), h, shade);
        c.drawRect(Ui.f(W) - Math.max(1f, Ui.f(2)), 0, Ui.f(W), h, edge);
        c.drawText("Homefield", Ui.f(92), Ui.f(110), word);
        String t = Ui.clock(now);
        c.drawText(t, Ui.f(W - 22) - time.measureText(t), Ui.f(106), time);

        // The mini TV: the live picture covers this when a channel plays.
        r.set(Ui.f(TV_X), Ui.f(TV_Y), Ui.f(TV_X + TV_W), Ui.f(TV_Y + TV_H));
        c.drawRect(r, tvBack);
        if (!note.isEmpty()) {
            android.text.StaticLayout l = Ui.layout(note, noteP, Ui.px(TV_W - 60), 3);
            Ui.draw(c, l, r.left + Ui.f(30), r.centerY() - l.getHeight() / 2f);
        }
        c.drawRect(r, tvLine);

        // The menu.
        long dt = last == 0 ? 16 : Math.min(50, now - last);
        last = now;
        if (Math.abs(scrollTo - scroll) > 0.5f) {
            scroll += (scrollTo - scroll) * (1f - (float) Math.exp(-dt / 70.0));
            postInvalidateOnAnimation();
        } else {
            scroll = scrollTo;
            last = 0;
        }
        c.save();
        c.clipRect(0, Ui.f(first - ITEM / 2 - 18), Ui.f(W), Ui.f(bottom + 10));
        for (int i = 0; i < items.size(); i++) {
            float cy = first + i * ITEM - scroll;
            if (cy < first - ITEM || cy > bottom + ITEM) continue;
            boolean on = i == focus;
            if (on) {
                r.set(Ui.f(95), Ui.f(cy - 39), Ui.f(468), Ui.f(cy + 39));
                if (active) glow.draw(c, r);
                else glow.box(c, r);
            }
            TextPaint p = on ? itemOn : item;
            String name = Ui.fit(items.get(i), p, Ui.f(330));
            c.drawText(name, Ui.f(112), Ui.f(cy + 12), p);
            // A newer Homefield TV waiting (Settings, Updates): a dot after Settings.
            if ("Settings".equals(items.get(i)) && Updates.waiting(getContext())) {
                c.drawCircle(Ui.f(112) + p.measureText(name) + Ui.f(18), Ui.f(cy - 2), Ui.f(7), dot);
            }
        }
        c.restore();
        if (scroll > 1f) Ui.chevron(c, Ui.f(281), Ui.f(first - ITEM / 2 - 22), Ui.f(12), Ui.UP, arrow);
        if (first + (items.size() - 1) * ITEM - scroll > bottom) Ui.chevron(c, Ui.f(281), Ui.f(bottom + 22), Ui.f(12), Ui.DOWN, arrow);
    }
}
