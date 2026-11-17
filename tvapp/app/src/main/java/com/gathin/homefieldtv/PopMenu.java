package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * A small menu that pops out over a dark screen when OK is held (the guide, live TV): a card with what it is for on top
 * and a few lines under it, the one the remote is on in amber; a line can be marked as the one chosen (a tick), and the
 * remote starts on it. Up and Down move, OK picks, Back shuts it. The screen it is on passes it the remote while it is
 * open. It also asks a question (ask): a wider card in the middle with a few lines of words over the answers.
 */
final class PopMenu extends View {
    interface Pick {
        void picked(String key);
    }

    /** A line's third part when it is the one chosen: {key, label, CHOSEN}. */
    static final String CHOSEN = "chosen";
    private static final float W = 560f, ASK_W = 860f, HEAD = 74f, ROW = 66f, PAD = 14f;

    private final List<String[]> items = new ArrayList<>();
    private String title = "", sub = "";
    private int focus;
    private Pick pick;
    private float x, y, w = W;
    /** A question's words (null: a menu). */
    private StaticLayout body;
    private final Paint dim = Dark.fill(0x8C000000), card = Dark.fill(Dark.RAIL), edge = Dark.line(0x26FFFFFF, 1.5f), lit = Dark.fill(Dark.ACCENT);
    private final Paint tick = Dark.line(Dark.TEXT, 3.2f), tickLit = Dark.line(Dark.ACCENT_INK, 3.2f);
    private final TextPaint titleP = Dark.text(26, 700, Dark.TEXT), subP = Dark.text(20, 500, Dark.MUTED), bodyP = Dark.text(25, 500, Dark.SOFT);
    private final TextPaint itemP = Dark.text(28, 500, Dark.SOFT), itemLit = Dark.text(28, 700, Dark.ACCENT_INK);
    private final RectF r = new RectF();
    private final Path check = new Path();

    PopMenu(Context c) {
        super(c);
        setVisibility(GONE);
    }

    /** Open at (x, y) (design pixels, kept on the screen): its title, a line under it, and {key, label} lines. */
    void open(String heading, String line, List<String[]> lines, float atX, float atY, Pick p) {
        body = null;
        w = W;
        fill(heading, line, lines, p);
        float h = height();
        x = Math.max(40f, Math.min(atX, 1880f - w));
        y = Math.max(40f, Math.min(atY, 1040f - h));
        Fade.show(this, true, 140);
        invalidate();
    }

    /** A question in the middle of the screen: its title, its words, and the answers as {key, label} lines. */
    void ask(String heading, String words, List<String[]> answers, Pick p) {
        w = ASK_W;
        fill(heading, "", answers, p);
        String t = words == null ? "" : words;
        int bw = Math.round(Ui.f(w - 56f));
        body = StaticLayout.Builder.obtain(t, 0, t.length(), bodyP, Math.max(1, bw)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(0f, 1.12f).build();
        float h = height();
        x = (1920f - w) / 2f;
        y = Math.max(40f, (1080f - h) / 2f);
        Fade.show(this, true, 140);
        invalidate();
    }

    private void fill(String heading, String line, List<String[]> lines, Pick p) {
        title = heading == null ? "" : heading;
        sub = line == null ? "" : line;
        items.clear();
        items.addAll(lines);
        focus = 0;
        for (int i = 0; i < items.size(); i++) if (chosen(items.get(i))) focus = i;
        pick = p;
    }

    private static boolean chosen(String[] item) {
        return item.length > 2 && CHOSEN.equals(item[2]);
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

    /** The question's words' height, design pixels (0 for a menu). */
    private float words() {
        return body == null ? 0f : body.getHeight() / Ui.f(1f) + 22f;
    }

    private float height() {
        return PAD + head() + words() + items.size() * ROW + PAD;
    }

    @Override protected void onDraw(Canvas c) {
        if (items.isEmpty()) return;
        // What is under it dimmed, so the menu stands out.
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        float h = height();
        r.set(Ui.f(x), Ui.f(y), Ui.f(x + w), Ui.f(y + h));
        c.drawRoundRect(r, Ui.f(20), Ui.f(20), card);
        c.drawRoundRect(r, Ui.f(20), Ui.f(20), edge);
        float tx = x + 28f, room = Ui.f(w - 56f);
        c.drawText(Ui.fit(title, titleP, room), Ui.f(tx), Ui.f(y + PAD + 36f), titleP);
        if (!sub.isEmpty()) c.drawText(Ui.fit(sub, subP, room), Ui.f(tx), Ui.f(y + PAD + 64f), subP);
        float ry = y + PAD + head();
        if (body != null) {
            c.save();
            c.translate(Ui.f(tx), Ui.f(ry + 4f));
            body.draw(c);
            c.restore();
            ry += words();
        }
        for (int i = 0; i < items.size(); i++) {
            boolean on = i == focus;
            if (on) {
                r.set(Ui.f(x + 12f), Ui.f(ry + 4f), Ui.f(x + w - 12f), Ui.f(ry + ROW - 4f));
                c.drawRoundRect(r, Ui.f(12), Ui.f(12), lit);
            }
            TextPaint p = on ? itemLit : itemP;
            boolean mark = chosen(items.get(i));
            float labelRoom = mark ? room - Ui.f(48f) : room;
            c.drawText(Ui.fit(items.get(i)[1], p, labelRoom), Ui.f(tx), Ui.f(ry + ROW / 2f + 10f), p);
            if (mark) {
                // The one chosen: a tick at the line's end.
                float cx = Ui.f(x + w - 46f), cy = Ui.f(ry + ROW / 2f);
                check.reset();
                check.moveTo(cx - Ui.f(12f), cy + Ui.f(1f));
                check.lineTo(cx - Ui.f(3f), cy + Ui.f(10f));
                check.lineTo(cx + Ui.f(14f), cy - Ui.f(10f));
                c.drawPath(check, on ? tickLit : tick);
            }
            ry += ROW;
        }
    }
}
