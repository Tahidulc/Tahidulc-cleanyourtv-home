package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A small menu that pops out over a dark screen when OK is held (the guide, live TV): a card with what it is for on top
 * and a few lines under it, the one the remote is on in amber. Up and Down move, OK picks, Back shuts it. The screen it
 * is on passes it the remote while it is open.
 */
final class PopMenu extends View {
    interface Pick {
        void picked(String key);
    }

    private static final float W = 560f, HEAD = 74f, ROW = 66f, PAD = 14f;

    private final List<String[]> items = new ArrayList<>();
    private String title = "", sub = "";
    private int focus;
    private Pick pick;
    private float x, y;
    private final Paint dim = Dark.fill(0x8C000000), card = Dark.fill(Dark.RAIL), edge = Dark.line(0x26FFFFFF, 1.5f), lit = Dark.fill(Dark.ACCENT);
    private final TextPaint titleP = Dark.text(26, 700, Dark.TEXT), subP = Dark.text(20, 500, Dark.MUTED);
    private final TextPaint itemP = Dark.text(28, 500, Dark.SOFT), itemLit = Dark.text(28, 700, Dark.ACCENT_INK);
    private final RectF r = new RectF();

    PopMenu(Context c) {
        super(c);
        setVisibility(GONE);
    }

    /** Open at (x, y) (design pixels, kept on the screen): its title, a line under it, and {key, label} lines. */
    void open(String heading, String line, List<String[]> lines, float atX, float atY, Pick p) {
        title = heading == null ? "" : heading;
        sub = line == null ? "" : line;
        items.clear();
        items.addAll(lines);
        focus = 0;
        pick = p;
        float h = height();
        x = Math.max(40f, Math.min(atX, 1880f - W));
        y = Math.max(40f, Math.min(atY, 1040f - h));
        Fade.show(this, true, 140);
        invalidate();
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE && pick != null;
    }

    void close() {
        pick = null;
        Fade.show(this, false, 120);
    }

    /** A key while open: Up and Down move; the rest are kept from the screen under it. */
    boolean key(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && focus > 0) focus--;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus < items.size() - 1) focus++;
        invalidate();
        return true;
    }

    /** OK: the line the remote is on picked, and the menu shut. */
    void ok() {
        Pick p = pick;
        String key = focus < items.size() ? items.get(focus)[0] : null;
        close();
        if (p != null && key != null) p.picked(key);
    }

    private float head() {
        return sub.isEmpty() ? 52f : HEAD;
    }

    private float height() {
        return PAD + head() + items.size() * ROW + PAD;
    }

    @Override protected void onDraw(Canvas c) {
        if (items.isEmpty()) return;
        // What is under it dimmed, so the menu stands out.
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        float h = height();
        r.set(Ui.f(x), Ui.f(y), Ui.f(x + W), Ui.f(y + h));
        c.drawRoundRect(r, Ui.f(20), Ui.f(20), card);
        c.drawRoundRect(r, Ui.f(20), Ui.f(20), edge);
        float tx = x + 28f, room = Ui.f(W - 56f);
        c.drawText(Ui.fit(title, titleP, room), Ui.f(tx), Ui.f(y + PAD + 36f), titleP);
        if (!sub.isEmpty()) c.drawText(Ui.fit(sub, subP, room), Ui.f(tx), Ui.f(y + PAD + 64f), subP);
        float ry = y + PAD + head();
        for (int i = 0; i < items.size(); i++) {
            boolean on = i == focus;
            if (on) {
                r.set(Ui.f(x + 12f), Ui.f(ry + 4f), Ui.f(x + W - 12f), Ui.f(ry + ROW - 4f));
                c.drawRoundRect(r, Ui.f(12), Ui.f(12), lit);
            }
            TextPaint p = on ? itemLit : itemP;
            c.drawText(Ui.fit(items.get(i)[1], p, room), Ui.f(tx), Ui.f(ry + ROW / 2f + 10f), p);
            ry += ROW;
        }
    }
}
