package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;

/**
 * The side panel of lists beside the rail: its heading (Films, TV shows, Live TV), then entries with an icon and a
 * count (All categories, All films, Continue watching, My list…), small section names between them (Your lists,
 * Categories), and the category names. The entry chosen sits in a white pill while the remote is on the panel, and
 * keeps a quieter one when it has gone to the page. Up and Down move the choice; the list slides to keep it in sight.
 */
final class SidePanel extends View {
    static final float X = Rail.W, W = 440f;
    private static final float ROW = 64f, SECTION = 52f;

    /** One line: an entry (icon, words, count) or a section's name (section true). */
    static final class Entry {
        final String words;
        final int icon, count;
        final boolean section;
        final Object tag;

        Entry(String words, int icon, int count, Object tag) {
            this.words = words;
            this.icon = icon;
            this.count = count;
            this.section = false;
            this.tag = tag;
        }

        private Entry(String words) {
            this.words = words;
            this.icon = -1;
            this.count = -1;
            this.section = true;
            this.tag = null;
        }

        static Entry section(String words) {
            return new Entry(words);
        }
    }

    String heading = "";
    private final List<Entry> entries = new ArrayList<>();
    private int chosen;
    private boolean active;
    /** Where the first line starts and the list's foot (design pixels). */
    float top = 150f, bottom = 1050f;
    private float scroll, scrollTo;
    private long last;

    private final Paint back = Dark.fill(Dark.PANEL), edge = Dark.fill(Dark.LINE), pill = Dark.fill(0xFFFFFFFF);
    private final Paint quiet = Dark.fill(0x1FFFFFFF), stroke = Dark.line(Dark.ACCENT, 2.2f), solid = Dark.fill(Dark.ACCENT);
    private final Paint strokeDark = Dark.line(0xFF111114, 2.2f), solidDark = Dark.fill(0xFF111114);
    private final TextPaint head = Dark.text(40, 600, Dark.TEXT), word = Dark.text(27, 500, Dark.SOFT), wordOn = Dark.text(27, 600, 0xFF111114);
    private final TextPaint count = Dark.text(22, 500, Dark.MUTED), countOn = Dark.text(22, 600, 0xFF4A4A50);
    private final TextPaint sectionP = Dark.text(21, 500, Dark.MUTED);
    private final Paint chevron = Dark.line(Dark.FAINT, 2f), chevronOn = Dark.line(0xFF4A4A50, 2f);
    private final RectF r = new RectF();

    SidePanel(Context c) {
        super(c);
    }

    void set(String heading, List<Entry> list, int chosenIndex) {
        this.heading = heading;
        entries.clear();
        entries.addAll(list);
        chosen = Math.max(0, Math.min(chosenIndex, entries.size() - 1));
        if (chosen < entries.size() && entries.get(chosen).section) step(1);
        settle(true);
        invalidate();
    }

    void setActive(boolean a) {
        active = a;
        invalidate();
    }

    boolean active() {
        return active;
    }

    Entry chosen() {
        return chosen < entries.size() ? entries.get(chosen) : null;
    }

    int chosenIndex() {
        return chosen;
    }

    /** The entry with this tag, chosen (no remote move). */
    void choose(Object tag) {
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).section && tag != null && tag.equals(entries.get(i).tag)) {
                chosen = i;
                settle(false);
                invalidate();
                return;
            }
        }
    }

    /** Up and Down along the entries (sections passed over); true when the choice moved. */
    boolean key(int code) {
        int was = chosen;
        if (code == KeyEvent.KEYCODE_DPAD_UP) step(-1);
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) step(1);
        else if (code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_CHANNEL_UP) for (int i = 0; i < 8; i++) step(-1);
        else if (code == KeyEvent.KEYCODE_PAGE_DOWN || code == KeyEvent.KEYCODE_CHANNEL_DOWN) for (int i = 0; i < 8; i++) step(1);
        else return false;
        settle(false);
        invalidate();
        return chosen != was;
    }

    private void step(int dir) {
        int i = chosen + dir;
        while (i >= 0 && i < entries.size() && entries.get(i).section) i += dir;
        if (i >= 0 && i < entries.size()) chosen = i;
    }

    private float yOf(int index) {
        float y = top;
        for (int i = 0; i < index && i < entries.size(); i++) y += entries.get(i).section ? SECTION : ROW;
        return y;
    }

    private void settle(boolean now) {
        float y = yOf(chosen), room = bottom - top;
        if (y - scrollTo > top + room - ROW * 2.5f) scrollTo = y - (top + room - ROW * 2.5f);
        if (y - scrollTo < top + ROW) scrollTo = y - top - ROW;
        float max = Math.max(0f, yOf(entries.size()) - bottom + ROW);
        scrollTo = Math.max(0f, Math.min(scrollTo, max));
        if (now || Exec.inline) scroll = scrollTo;
    }

    @Override protected void onDraw(Canvas c) {
        float x0 = Ui.f(X), x1 = Ui.f(X + W);
        c.drawRect(x0, 0, x1, getHeight(), back);
        c.drawRect(x1 - Math.max(1f, Ui.f(1.5f)), 0, x1, getHeight(), edge);
        c.drawText(heading, x0 + Ui.f(40), Ui.f(100), head);
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, now - last);
        last = now;
        if (Math.abs(scrollTo - scroll) > 0.5f) {
            scroll += (scrollTo - scroll) * (Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 70.0));
            postInvalidateOnAnimation();
        } else {
            scroll = scrollTo;
            last = 0;
        }
        c.save();
        c.clipRect(x0, Ui.f(top - 8), x1, Ui.f(bottom + 8));
        float y = top - scroll;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            float hh = e.section ? SECTION : ROW;
            if (y + hh < top - ROW) {
                y += hh;
                continue;
            }
            if (y > bottom + ROW) break;
            if (e.section) {
                c.drawText(e.words, x0 + Ui.f(40), Ui.f(y + 36), sectionP);
            } else {
                boolean sel = i == chosen;
                r.set(x0 + Ui.f(22), Ui.f(y + 6), x1 - Ui.f(22), Ui.f(y + ROW - 6));
                if (sel && active) c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, pill);
                else if (sel) c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, quiet);
                boolean dark = sel && active;
                float tx = x0 + Ui.f(44);
                if (e.icon >= 0) {
                    Dark.icon(c, e.icon, tx + Ui.f(12), Ui.f(y + ROW / 2f), Ui.f(24), dark ? strokeDark : stroke, dark ? solidDark : solid);
                    tx += Ui.f(42);
                }
                TextPaint p = dark ? wordOn : word;
                String n = e.count >= 0 ? String.valueOf(e.count) : "";
                TextPaint cp = dark ? countOn : count;
                float cw = n.isEmpty() ? 0 : cp.measureText(n);
                float room = x1 - Ui.f(84) - cw - tx - Ui.f(16);
                c.drawText(Ui.fit(e.words, p, room), tx, Ui.f(y + ROW / 2f + 9), p);
                if (!n.isEmpty()) c.drawText(n, x1 - Ui.f(84) - cw, Ui.f(y + ROW / 2f + 8), cp);
                Ui.chevron(c, x1 - Ui.f(56), Ui.f(y + ROW / 2f), Ui.f(7), Ui.RIGHT, dark ? chevronOn : chevron);
            }
            y += hh;
        }
        c.restore();
    }
}
