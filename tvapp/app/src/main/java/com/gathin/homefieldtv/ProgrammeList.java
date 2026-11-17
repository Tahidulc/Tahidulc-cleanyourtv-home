package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A list of programmes as the family's other app draws its History (and so Recordings and My list's TV programmes and
 * reminders here): each day under its name ("Today", 28 px grey) with a rule between days; each programme a 111 px row,
 * its title (30 px; on now in the light accent), under it its day and times ("Mon, 16 Nov, 21:00 — 22:35") with
 * catch-up's circle arrow or a reminder's bell, a word at the right of the text (Scheduled, Ready…), then the channel's
 * logo and name. Where the remote is the row is the accent pill with a round delete button at its end (Right goes on to
 * it, OK deletes); right of the list a card with the programme's title, times and its description. Only the rows in
 * sight are drawn; the list slides to keep the remote's row in sight.
 */
final class ProgrammeList extends View {
    /** Sizes and places (design pixels): the list's width, a row, a day's name, the card. */
    static final float W = 1008f, ROW = 111f, GAP = 8f, HEAD = 88f, TOP = 40f, CARD_X = 1192f, CARD_Y = 160f, CARD_W = 680f;
    private static final float LOGO_X = 600f, LOGO_W = 99f, LOGO_H = 66f, NAME_X = 723f, TRASH = 72f;
    private static final long SCROLL_MS = 200;

    /** One programme. */
    static final class Entry {
        final Object tag;
        /** The day it is listed under ("Today"). */
        final String day;
        String title = "", when = "", channel = "", logo = "", state = "", about = "", cardLine = "";
        /** Catch-up's circle arrow, or a reminder's bell, after the times. */
        boolean archive, bell;
        /** On now: its title in the light accent; its state word in it too (Recording). */
        boolean now, stateLit;
        boolean deletable = true;
        private StaticLayout nameL, cardTitleL, cardAboutL;

        Entry(Object tag, String day) {
            this.tag = tag;
            this.day = day == null ? "" : day;
        }
    }

    interface Host {
        void chosen(ProgrammeList list, Entry e);

        void delete(ProgrammeList list, Entry e);

        void focused(ProgrammeList list, Entry e);

        /** Past the list's edge (Ui.LEFT, UP, RIGHT, DOWN): true when the screen took it. */
        boolean edge(ProgrammeList list, int dir);
    }

    /** The list's left edge (design pixels), and whether the card shows at the right. */
    float left = Rail.W + 24f;
    boolean card = true;
    private final List<Entry> entries = new ArrayList<>();
    /** Each entry's top, from the list's top (design pixels), with the days' names between. */
    private final List<Float> tops = new ArrayList<>();
    private final List<float[]> heads = new ArrayList<>();
    private final List<String> headNames = new ArrayList<>();
    private float total;
    private int focus = -1;
    private boolean active, onTrash;
    private Host host;
    private float scroll, scrollFrom, scrollTo;
    private long scrollAt;

    private final TextPaint titleP = Type.paint(30f, Type.REGULAR, Palette.TEXT1), whenP = Type.paint(26f, Type.REGULAR, Palette.TEXT2);
    private final TextPaint headP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2), nameP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
    private final TextPaint cardTitleP = Type.paint(32f, Type.MEDIUM, Palette.TEXT1), cardP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), icon = new Paint(Paint.ANTI_ALIAS_FLAG), iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;

    ProgrammeList(Context c) {
        super(c);
        icon.setStyle(Paint.Style.STROKE);
        icon.setStrokeCap(Paint.Cap.ROUND);
    }

    void setHost(Host h) {
        host = h;
    }

    /** New entries, grouped by their days in the order given; the remote kept on the same one (by tag) when it can. */
    void set(List<Entry> list) {
        Object was = focused() == null ? null : focused().tag;
        entries.clear();
        entries.addAll(list);
        tops.clear();
        heads.clear();
        headNames.clear();
        float y = TOP;
        String day = null;
        for (Entry e : entries) {
            if (!e.day.equals(day)) {
                if (day != null) y += GAP * 2;
                heads.add(new float[]{y, day == null ? 0f : 1f});
                headNames.add(e.day);
                y += HEAD;
                day = e.day;
            }
            tops.add(y);
            y += ROW + GAP;
        }
        total = y + TOP;
        focus = entries.isEmpty() ? -1 : 0;
        for (int i = 0; was != null && i < entries.size(); i++) if (was.equals(entries.get(i).tag)) focus = i;
        onTrash = false;
        settle(true);
        invalidate();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    Entry focused() {
        return focus >= 0 && focus < entries.size() ? entries.get(focus) : null;
    }

    void setActive(boolean on) {
        active = on;
        if (!on) onTrash = false;
        invalidate();
    }

    boolean active() {
        return active;
    }

    private void settle(boolean now) {
        float target = 0f;
        if (focus >= 0) {
            float y0 = tops.get(focus), y1 = y0 + ROW;
            // A day's name just over the remote's row stays in sight with it.
            if (focus == 0 || !entries.get(focus - 1).day.equals(entries.get(focus).day)) y0 -= HEAD;
            target = RowList.scrollFor(scrollTo, y0, y1 + GAP, 1080f - TOP, total);
        }
        if (now || Exec.inline) {
            scroll = scrollFrom = scrollTo = target;
            scrollAt = 0;
        } else if (target != scrollTo) {
            scrollFrom = scroll;
            scrollTo = target;
            scrollAt = SystemClock.uptimeMillis();
        }
    }

    boolean key(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int n = focus + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (n < 0 || n >= entries.size()) return host != null && host.edge(this, code == KeyEvent.KEYCODE_DPAD_UP ? Ui.UP : Ui.DOWN);
                focus = n;
                onTrash = false;
                settle(false);
                invalidate();
                if (host != null) host.focused(this, entries.get(n));
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                Entry e = focused();
                if (e != null && e.deletable && !onTrash) {
                    onTrash = true;
                    invalidate();
                    return true;
                }
                return host != null && host.edge(this, Ui.RIGHT);
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (onTrash) {
                    onTrash = false;
                    invalidate();
                    return true;
                }
                return host != null && host.edge(this, Ui.LEFT);
            default:
                return false;
        }
    }

    void ok() {
        Entry e = focused();
        if (e == null || host == null) return;
        if (onTrash) host.delete(this, e);
        else host.chosen(this, e);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        long t = SystemClock.uptimeMillis();
        boolean moving = false;
        if (scrollAt != 0) {
            float k = Math.min(1f, (t - scrollAt) / (float) SCROLL_MS);
            scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
            if (k >= 1f) scrollAt = 0;
            else moving = true;
        }
        float x0 = Ui.f(left), x1 = Ui.f(left + W);
        c.save();
        c.clipRect(x0 - Ui.f(24), 0, x1 + Ui.f(24), getHeight());
        for (int i = 0; i < heads.size(); i++) {
            float y = heads.get(i)[0] - scroll;
            if (y > 1080f || y + HEAD < 0f) continue;
            if (heads.get(i)[1] > 0f) {
                fill.setColor(Palette.DIVIDER);
                c.drawRect(x0 + Ui.f(24), Ui.f(y - GAP) - Ui.f(1), x1 - Ui.f(24), Ui.f(y - GAP) + Ui.f(1), fill);
            }
            headP.setColor(Palette.TEXT2);
            c.drawText(headNames.get(i), x0 + Ui.f(24), Ui.f(y + 58), headP);
        }
        for (int i = 0; i < entries.size(); i++) {
            float y = tops.get(i) - scroll;
            if (y > 1080f || y + ROW < 0f) continue;
            drawEntry(c, entries.get(i), x0, Ui.f(y), i == focus);
        }
        c.restore();
        Entry f = focused();
        if (card && f != null) drawCard(c, f);
        if (moving) postInvalidateOnAnimation();
    }

    private void drawEntry(Canvas c, Entry e, float x, float y, boolean on) {
        float w = Ui.f(W), h = Ui.f(ROW);
        r.set(x, y, x + w, y + h);
        if (on && active && !onTrash) Focus.pill(c, r);
        else if (on) Focus.selected(c, r);
        titleP.setColor(e.now ? Palette.ACCENT_LIGHT : Palette.TEXT1);
        float textRoom = Ui.f(LOGO_X - 48);
        c.drawText(Ui.fit(e.title, titleP, textRoom), x + Ui.f(24), y + Ui.f(48), titleP);
        whenP.setColor(Palette.TEXT2);
        float stateW = e.state.isEmpty() ? 0f : whenP.measureText(e.state) + Ui.f(24);
        String when = Ui.fit(e.when, whenP, textRoom - stateW - Ui.f(48));
        float wx = x + Ui.f(24);
        c.drawText(when, wx, y + Ui.f(90), whenP);
        if (e.archive || e.bell) {
            float ix = wx + whenP.measureText(when) + Ui.f(30), iy = y + Ui.f(81);
            icon.setColor(Palette.TEXT2);
            icon.setStrokeWidth(Math.max(1f, Ui.f(2.5f)));
            iconFill.setColor(Palette.TEXT2);
            if (e.archive) Dark.replay(c, ix, iy, Ui.f(10), icon, iconFill);
            else Glyphs.draw(c, Glyphs.REMINDER, ix, iy, Ui.f(26), Palette.TEXT2);
        }
        if (!e.state.isEmpty()) {
            whenP.setColor(e.stateLit ? Palette.ACCENT_LIGHT : Palette.TEXT2);
            c.drawText(e.state, x + Ui.f(LOGO_X - 24) - whenP.measureText(e.state), y + Ui.f(90), whenP);
        }
        // The channel: its logo and name.
        Bitmap b = e.logo.isEmpty() ? null : Pics.get(e.logo, Ui.px(LOGO_W), Ui.px(LOGO_H), Pics.logoMode(), redraw);
        float lx = x + Ui.f(LOGO_X), ly = y + (h - Ui.f(LOGO_H)) / 2f;
        if (b != null) {
            c.drawBitmap(b, lx + (Ui.f(LOGO_W) - b.getWidth()) / 2f, ly + (Ui.f(LOGO_H) - b.getHeight()) / 2f, bmp);
        }
        float nameRoom = Ui.f(W - NAME_X - (e.deletable ? TRASH + 32 : 24));
        if (e.nameL == null || e.nameL.getWidth() != Math.round(nameRoom)) e.nameL = Ui.layout(e.channel, nameP, Math.round(nameRoom), 2);
        nameP.setColor(Palette.TEXT1);
        Ui.draw(c, e.nameL, x + Ui.f(NAME_X), y + (h - e.nameL.getHeight()) / 2f);
        if (on && e.deletable) {
            float cx = x + w - Ui.f(16 + TRASH / 2f), cy = y + h / 2f;
            if (active && onTrash) {
                Focus.circle(c, cx, cy, Ui.f(TRASH));
            } else {
                fill.setColor(Palette.SELECTED);
                c.drawCircle(cx, cy, Ui.f(TRASH) / 2f, fill);
            }
            Glyphs.draw(c, Glyphs.TRASH, cx, cy, Ui.f(34), Palette.TEXT1);
        }
    }

    /** The card at the right: the programme's title, its times and what the screen says of it, its description. */
    private void drawCard(Canvas c, Entry e) {
        float pad = 32f, room = CARD_W - pad * 2;
        if (e.cardTitleL == null) e.cardTitleL = Ui.layout(e.title, cardTitleP, Ui.px(room), 2);
        if (e.cardAboutL == null) e.cardAboutL = e.about.isEmpty() ? null : Ui.layout(e.about, cardP, Ui.px(room), 5);
        float hTitle = e.cardTitleL.getHeight() / Ui.s;
        float h = pad + hTitle + 54f + (e.cardLine.isEmpty() ? 0f : 40f) + (e.cardAboutL == null ? 0f : 12f + e.cardAboutL.getHeight() / Ui.s) + pad;
        r.set(Ui.f(CARD_X), Ui.f(CARD_Y), Ui.f(CARD_X + CARD_W), Ui.f(CARD_Y + Math.max(200f, h)));
        fill.setColor(Palette.PANEL);
        c.drawRoundRect(r, Ui.f(16), Ui.f(16), fill);
        float x = Ui.f(CARD_X + pad), y = CARD_Y + pad;
        cardTitleP.setColor(Palette.TEXT1);
        Ui.draw(c, e.cardTitleL, x, Ui.f(y));
        y += hTitle + 44f;
        cardP.setColor(Palette.TEXT2);
        c.drawText(e.when, x, Ui.f(y), cardP);
        if (e.archive || e.bell) {
            float ix = x + cardP.measureText(e.when) + Ui.f(30), iy = Ui.f(y - 9);
            icon.setColor(Palette.TEXT2);
            icon.setStrokeWidth(Math.max(1f, Ui.f(2.5f)));
            iconFill.setColor(Palette.TEXT2);
            if (e.archive) Dark.replay(c, ix, iy, Ui.f(11), icon, iconFill);
            else Glyphs.draw(c, Glyphs.REMINDER, ix, iy, Ui.f(28), Palette.TEXT2);
        }
        if (!e.cardLine.isEmpty()) {
            y += 40f;
            cardP.setColor(e.stateLit ? Palette.ACCENT_LIGHT : Palette.TEXT1);
            c.drawText(Ui.fit(e.cardLine, cardP, Ui.f(room)), x, Ui.f(y), cardP);
        }
        if (e.cardAboutL != null) {
            cardP.setColor(Palette.TEXT2);
            Ui.draw(c, e.cardAboutL, x, Ui.f(y + 16f));
        }
    }

    // ---- words ----------------------------------------------------------------------------------------------------

    /** Pure: a day as the other app writes it: "Mon, 16 Nov". */
    static String date(long t, ZoneId z) {
        java.time.ZonedDateTime d = Instant.ofEpochMilli(t).atZone(z);
        return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + ", " + d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK);
    }

    /** "Mon, 16 Nov, 21:00 — 22:35". */
    static String when(long start, long end) {
        return date(start, ZoneId.systemDefault()) + ", " + LiveText.span(start, end);
    }
}
