package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of rows, as every panel draws one: Settings and its pages, side menus, pickers and dialogs' buttons.
 *
 * <pre>
 *   Row.item(tag, title, value)        a title (ROW, TEXT1) with its value or description under it in grey (SMALL,
 *                                      TEXT2): 90 high with one line, 118 with two (longer words take a second line)
 *   Row.toggle(tag, title, value, on)  the same with a switch at the right (80 x 46, 48 px in from the right edge):
 *                                      on, the accent track and a white thumb; off, a grey track and thumb
 *   Row.header(title)                  a section's name in the accent (SMALL, medium), 72 high: "Update options"
 *   Row.note(text)                     a grey note (SMALL), as many lines as it needs
 *   Row.radio(tag, title, checked)     a picker's choice: its circle (36, 70 px in), the accent ring and dot when chosen,
 *                                      its name 128 px in; Row.swatch(...) has a colour's disc in place of the circle,
 *                                      a tick in it when chosen
 *   Row.icon(tag, icon, title)         a menu line, 80 high: an icon (40, Dark's kinds) 40 px in, its name 104 px in
 *   Row.divider()                      a 2 px rule between groups of lines (32 high)
 *   Row.stepper(tag, value)            a value with a button each side (the player's audio offset): ‹ and › in boxes
 *                                      128 x 96, 48 px in from either side, the value between them (PICKER); Left and
 *                                      Right move between the two buttons (side 0 and 1), OK presses the one lit
 * </pre>
 *
 * Rows can be greyed (enabled = false: TEXT3, OK does nothing) and carry the accent dot after their title (dot).
 * The remote's row is in the focus pill, 16 px in from the list's sides and as tall as the row (Focus.pill); when the
 * list is not where the remote is (setActive(false)) that row keeps the selected pill. Up and Down jump to the next row
 * that can be chosen (headers, notes and dividers are passed over; no wrap-around), the list scrolling in SCROLL_MS to
 * keep it whole in sight (with the section name just over it); Page keys move six rows. OK lights the row for
 * FLASH_MS and tells the host. Words start 48 px in (TEXT_X).
 *
 * The list's place is x, top, w, bottom in design pixels (the panel sets them); heading, when set, is a title drawn
 * at headingY (the panel's band) that slides with the list. Hosts change a row (its value, its switch) and call
 * refresh(), or give new rows with setRows() (the remote stays on the same line number when it can).
 */
final class RowList extends View {
    static final int ITEM = 0, HEADER = 1, NOTE = 2, RADIO = 3, ICON = 4, DIVIDER = 5, STEPPER = 6;
    /** Heights and places (design pixels). */
    static final float ROW_1 = 90f, ROW_2 = 118f, TITLE_LINE = 34f, VALUE_LINE = 30f, HEADER_H = 72f, ICON_H = 80f,
            DIVIDER_H = 32f, INSET = Focus.INSET_PANEL, TEXT_X = 48f, ICON_TEXT_X = 104f;
    static final float SWITCH_W = 80f, SWITCH_H = 46f, SWITCH_RIGHT = 48f, RADIO_X = 70f, RADIO_D = 36f, RADIO_TEXT_X = 128f;
    static final long SCROLL_MS = 150, FLASH_MS = 180;
    /** A stepper's row (its buttons at its top, the gap under them) and its buttons. */
    static final float STEPPER_H = 145f, STEP_W = 128f, STEP_H = 96f;

    /** One line of the list. */
    static final class Row {
        final int kind;
        final Object tag;
        String title, value;
        /** A switch at the right: on or off (null: none). */
        Boolean toggle;
        /** A radio row's choice is this one. */
        boolean checked;
        /** False: greyed, and OK does nothing. */
        boolean enabled = true;
        /** The accent dot after the title: something waits there (an update). */
        boolean dot;
        /** An icon before the title (ICON rows; Dark's kinds), else -1. */
        int icon = -1;
        /** A radio row's colour disc in place of its circle (0: none). */
        int swatch;
        /** A stepper's button the remote is on: 0 the left one (‹), 1 the right (›). */
        int side;
        // Laid out for the list's width (setRows / refresh).
        StaticLayout titleL, valueL;
        float height;

        Row(int kind, Object tag, String title, String value) {
            this.kind = kind;
            this.tag = tag;
            this.title = title == null ? "" : title;
            this.value = value == null ? "" : value;
        }

        static Row item(Object tag, String title, String value) {
            return new Row(ITEM, tag, title, value);
        }

        static Row toggle(Object tag, String title, String value, boolean on) {
            Row r = new Row(ITEM, tag, title, value);
            r.toggle = on;
            return r;
        }

        static Row header(String title) {
            return new Row(HEADER, null, title, "");
        }

        static Row note(String text) {
            return new Row(NOTE, null, text, "");
        }

        static Row radio(Object tag, String title, boolean checked) {
            Row r = new Row(RADIO, tag, title, "");
            r.checked = checked;
            return r;
        }

        static Row swatch(Object tag, String title, int colour, boolean checked) {
            Row r = radio(tag, title, checked);
            r.swatch = colour;
            return r;
        }

        static Row icon(Object tag, int icon, String title) {
            Row r = new Row(ICON, tag, title, "");
            r.icon = icon;
            return r;
        }

        static Row divider() {
            return new Row(DIVIDER, null, "", "");
        }

        static Row stepper(Object tag, String value) {
            return new Row(STEPPER, tag, "", value);
        }

        boolean focusable() {
            return kind == ITEM || kind == RADIO || kind == ICON || kind == STEPPER;
        }
    }

    interface Host {
        /** OK on a row that is not greyed. */
        void chosen(RowList list, Row row);

        /** The remote moved to another row. */
        void focused(RowList list, Row row);

        /** Past the list's edge (Ui.LEFT, UP, RIGHT, DOWN): true when the screen took it. */
        boolean edge(RowList list, int dir);
    }

    /** Where it goes (design pixels). */
    float x = 1200f, top = 160f, w = 720f, bottom = 1080f;
    /** A title over the rows, in the panel's band (null: none), its baseline at headingY. */
    String heading;
    float headingY = 87f;

    private final List<Row> rows = new ArrayList<>();
    private int focus = -1;
    private boolean active = true;
    private Host host;
    private float scroll, scrollFrom, scrollTo;
    private long scrollAt, flashAt;
    private int flash = -1;

    private final TextPaint titleP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1);
    private final TextPaint valueP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
    private final TextPaint headerP = Type.paint(Type.SMALL, Type.MEDIUM, Palette.ACCENT);
    private final TextPaint noteP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
    private final TextPaint headingP = Type.paint(Type.TITLE, Type.MEDIUM, Palette.TEXT1);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconLine = new Paint(Paint.ANTI_ALIAS_FLAG), iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    RowList(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        iconLine.setStyle(Paint.Style.STROKE);
        iconLine.setStrokeCap(Paint.Cap.ROUND);
        iconLine.setStrokeJoin(Paint.Join.ROUND);
    }

    void setHost(Host h) {
        host = h;
    }

    /** New rows; the remote stays on the same line number when that line can be chosen. */
    void setRows(List<Row> list) {
        rows.clear();
        rows.addAll(list);
        layOut();
        if (focus < 0 || focus >= rows.size() || !rows.get(focus).focusable()) focus = next(rows, -1, 1);
        settle(true);
        invalidate();
    }

    List<Row> rows() {
        return rows;
    }

    /** A row's words or switch changed: laid out and drawn again. */
    void refresh() {
        layOut();
        settle(true);
        invalidate();
    }

    void setFocus(int i) {
        if (i >= 0 && i < rows.size() && rows.get(i).focusable()) {
            focus = i;
            settle(true);
            invalidate();
        }
    }

    int focusIndex() {
        return focus;
    }

    Row focused() {
        return focus >= 0 && focus < rows.size() ? rows.get(focus) : null;
    }

    boolean canFocus() {
        return next(rows, -1, 1) >= 0;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    boolean active() {
        return active;
    }

    // ---- pure -------------------------------------------------------------------------------------------------------

    /** Pure: the next row that can be chosen after i going dir (1 down, -1 up), or -1 (no wrap-around). */
    static int next(List<Row> rows, int i, int dir) {
        for (int k = i + dir; k >= 0 && k < rows.size(); k += dir) if (rows.get(k).focusable()) return k;
        return -1;
    }

    /**
     * Pure: the scroll (design pixels) that keeps y0..y1 (from the list's first row) whole in a view room tall,
     * moving as little as it can from now and never past either end of a list total tall.
     */
    static float scrollFor(float now, float y0, float y1, float room, float total) {
        float s = now;
        if (y1 - s > room) s = y1 - room;
        if (y0 - s < 0) s = y0;
        return Math.max(0f, Math.min(Math.max(0f, total - room), s));
    }

    // ---- laying out -----------------------------------------------------------------------------------------------

    private void layOut() {
        for (Row row : rows) {
            switch (row.kind) {
                case HEADER:
                    row.height = HEADER_H;
                    break;
                case DIVIDER:
                    row.height = DIVIDER_H;
                    break;
                case ICON:
                    row.height = ICON_H;
                    break;
                case STEPPER:
                    row.height = STEPPER_H;
                    break;
                case NOTE: {
                    row.titleL = Ui.layout(row.title, noteP, Ui.px(w - TEXT_X * 2), 8);
                    row.height = row.titleL.getHeight() / Ui.s + 24f;
                    break;
                }
                case RADIO: {
                    row.titleL = Ui.layout(row.title, titleP, Ui.px(w - RADIO_TEXT_X - TEXT_X), 2);
                    row.height = ROW_1 + (row.titleL.getLineCount() - 1) * TITLE_LINE;
                    break;
                }
                default: {
                    float room = w - TEXT_X * 2 - (row.toggle != null ? SWITCH_W + 24f : 0f) - (row.dot ? 30f : 0f);
                    row.titleL = Ui.layout(row.title, titleP, Ui.px(room), 2);
                    row.valueL = row.value.isEmpty() ? null : Ui.layout(row.value, valueP, Ui.px(room), 2);
                    int tl = row.titleL.getLineCount(), vl = row.valueL == null ? 0 : row.valueL.getLineCount();
                    row.height = vl == 0 ? ROW_1 + (tl - 1) * TITLE_LINE : ROW_2 + (tl - 1) * TITLE_LINE + (vl - 1) * VALUE_LINE;
                    break;
                }
            }
        }
    }

    private float yOf(int index) {
        float y = 0f;
        for (int i = 0; i < index && i < rows.size(); i++) y += rows.get(i).height;
        return y;
    }

    private float total() {
        return yOf(rows.size());
    }

    private void settle(boolean now) {
        float target;
        if (focus < 0) {
            target = 0f;
        } else {
            float y0 = yOf(focus), y1 = y0 + rows.get(focus).height;
            // A section's name just over the remote's row stays in sight with it.
            if (focus > 0 && rows.get(focus - 1).kind == HEADER) y0 -= rows.get(focus - 1).height;
            target = scrollFor(scrollTo, y0, y1, bottom - top, total());
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

    // ---- the remote -----------------------------------------------------------------------------------------------

    boolean key(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: {
                int n = next(rows, focus, -1);
                if (n >= 0) {
                    move(n);
                    return true;
                }
                // At the top: the lines above (a section's name, a note) come into sight.
                if (scrollTo > 0f) {
                    scrollFrom = scroll;
                    scrollTo = 0f;
                    scrollAt = Exec.inline ? 0 : SystemClock.uptimeMillis();
                    if (Exec.inline) scroll = 0f;
                    invalidate();
                }
                return host != null && host.edge(this, Ui.UP);
            }
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int n = next(rows, focus, 1);
                if (n >= 0) {
                    move(n);
                    return true;
                }
                return host != null && host.edge(this, Ui.DOWN);
            }
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN: {
                int dir = code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1, at = focus;
                for (int i = 0; i < 6; i++) {
                    int n = next(rows, at, dir);
                    if (n < 0) break;
                    at = n;
                }
                if (at != focus && at >= 0) move(at);
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int side = code == KeyEvent.KEYCODE_DPAD_LEFT ? 0 : 1;
                Row row = focused();
                // A stepper: Left and Right go between its two buttons.
                if (row != null && row.kind == STEPPER && row.side != side) {
                    row.side = side;
                    invalidate();
                    return true;
                }
                return host != null && host.edge(this, side == 0 ? Ui.LEFT : Ui.RIGHT);
            }
            default:
                return false;
        }
    }

    private void move(int to) {
        focus = to;
        settle(false);
        invalidate();
        if (host != null) host.focused(this, rows.get(to));
    }

    void ok() {
        Row row = focused();
        if (row == null || !row.enabled) return;
        flash = focus;
        flashAt = SystemClock.uptimeMillis();
        invalidate();
        if (host != null) host.chosen(this, row);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    /** The list fades as a whole when a panel slides it: no layer needed for that. */
    @Override public boolean hasOverlappingRendering() {
        return false;
    }

    @Override protected void onDraw(Canvas c) {
        long t = SystemClock.uptimeMillis();
        boolean moving = false;
        if (scrollAt != 0) {
            float k = Math.min(1f, (t - scrollAt) / (float) SCROLL_MS);
            scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
            if (k >= 1f) scrollAt = 0;
            else moving = true;
        }
        if (heading != null && !heading.isEmpty()) {
            headingP.setColor(Palette.TEXT1);
            c.drawText(Ui.fit(heading, headingP, Ui.f(w - TEXT_X * 2)), Ui.f(x + TEXT_X), Ui.f(headingY), headingP);
        }
        c.save();
        c.clipRect(Ui.f(x), Ui.f(top), Ui.f(x + w), Ui.f(bottom));
        float y = top - scroll;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (y > bottom) break;
            if (y + row.height >= top) drawRow(c, row, i, y, t);
            y += row.height;
        }
        c.restore();
        if (flash >= 0) {
            if (t - flashAt < FLASH_MS && !Exec.inline) moving = true;
            else flash = -1;
        }
        if (moving) postInvalidateOnAnimation();
    }

    private void drawRow(Canvas c, Row row, int i, float y, long t) {
        float left = Ui.f(x), right = Ui.f(x + w), top0 = Ui.f(y), h = Ui.f(row.height);
        switch (row.kind) {
            case HEADER:
                headerP.setColor(Palette.ACCENT);
                c.drawText(Ui.fit(row.title, headerP, right - left - Ui.f(TEXT_X * 2)), left + Ui.f(TEXT_X), top0 + Ui.f(48), headerP);
                return;
            case NOTE:
                noteP.setColor(Palette.TEXT2);
                if (row.titleL != null) Ui.draw(c, row.titleL, left + Ui.f(TEXT_X), top0 + Ui.f(12));
                return;
            case DIVIDER:
                fill.setColor(Palette.DIVIDER);
                c.drawRect(left + Ui.f(40), top0 + h / 2f - Ui.f(1), right - Ui.f(40), top0 + h / 2f + Ui.f(1), fill);
                return;
            default:
                break;
        }
        boolean on = i == focus;
        if (row.kind == STEPPER) {
            stepper(c, row, left, right, top0, on && active);
            return;
        }
        r.set(left + Ui.f(INSET), top0, right - Ui.f(INSET), top0 + h);
        if (on && active) Focus.pill(c, r);
        else if (on) Focus.selected(c, r);
        if (i == flash && t - flashAt < FLASH_MS) {
            // OK's moment: the row lit a little more, fading.
            float k = 1f - (t - flashAt) / (float) FLASH_MS;
            fill.setColor(Palette.alpha(0xFFFFFFFF, Math.round(0x1F * k)));
            c.drawRoundRect(r, Ui.f(Focus.RADIUS), Ui.f(Focus.RADIUS), fill);
        }
        int ink = row.enabled ? Palette.TEXT1 : Palette.TEXT3;
        float cy = top0 + h / 2f;
        if (row.kind == ICON) {
            iconLine.setColor(ink);
            iconLine.setStrokeWidth(Math.max(1f, Ui.f(3f)));
            iconFill.setColor(ink);
            Dark.icon(c, row.icon, left + Ui.f(60), cy, Ui.f(36), iconLine, iconFill);
            titleP.setColor(ink);
            c.drawText(Ui.fit(row.title, titleP, right - left - Ui.f(ICON_TEXT_X + TEXT_X)), left + Ui.f(ICON_TEXT_X),
                    Type.centred(titleP, cy), titleP);
            return;
        }
        if (row.kind == RADIO) {
            radio(c, row, left + Ui.f(RADIO_X), cy, ink);
            titleP.setColor(ink);
            if (row.titleL != null) Ui.draw(c, row.titleL, left + Ui.f(RADIO_TEXT_X), cy - row.titleL.getHeight() / 2f);
            return;
        }
        // An item: its title, its value under it, its switch at the right.
        titleP.setColor(ink);
        valueP.setColor(row.enabled ? Palette.TEXT2 : Palette.TEXT3);
        float block = row.titleL.getHeight() + (row.valueL == null ? 0 : Ui.f(6) + row.valueL.getHeight());
        float ty = cy - block / 2f, tx = left + Ui.f(TEXT_X);
        Ui.draw(c, row.titleL, tx, ty);
        if (row.dot) {
            float dx = tx + row.titleL.getLineWidth(0) + Ui.f(18);
            fill.setColor(Palette.ACCENT);
            c.drawCircle(dx, ty + row.titleL.getLineBaseline(0) - Ui.f(10), Ui.f(7), fill);
        }
        if (row.valueL != null) Ui.draw(c, row.valueL, tx, ty + row.titleL.getHeight() + Ui.f(6));
        if (row.toggle != null) toggle(c, right - Ui.f(SWITCH_RIGHT + SWITCH_W), cy, row.toggle, on && active, row.enabled);
    }

    /** A stepper: its two buttons at the row's top (the lit one in the focus pill), the value between them. */
    private void stepper(Canvas c, Row row, float left, float right, float top0, boolean lit) {
        float bw = Ui.f(STEP_W), bh = Ui.f(STEP_H), in = Ui.f(TEXT_X), cy = top0 + bh / 2f;
        iconLine.setColor(Palette.TEXT1);
        iconLine.setStrokeWidth(Math.max(1f, Ui.f(3f)));
        for (int s = 0; s < 2; s++) {
            float x0 = s == 0 ? left + in : right - in - bw;
            r.set(x0, top0, x0 + bw, top0 + bh);
            if (lit && row.side == s) Focus.pill(c, r);
            Ui.chevron(c, r.centerX(), cy, Ui.f(10), s == 0 ? Ui.LEFT : Ui.RIGHT, iconLine);
        }
        if (stepP == null) stepP = Type.paint(Type.PICKER, Type.REGULAR, Palette.TEXT1);
        stepP.setColor(Palette.TEXT1);
        String v = Ui.fit(row.value, stepP, right - left - in * 2 - bw * 2 - Ui.f(32));
        c.drawText(v, (left + right) / 2f - stepP.measureText(v) / 2f, Type.centred(stepP, cy), stepP);
    }

    private TextPaint stepP;

    /** A radio's circle (or a colour's disc) centred on (cx, cy): the accent ring and dot when chosen. */
    private void radio(Canvas c, Row row, float cx, float cy, int ink) {
        float rad = Ui.f(RADIO_D) / 2f;
        if (row.swatch != 0) {
            fill.setColor(row.swatch);
            c.drawCircle(cx, cy, rad, fill);
            ring.setColor(0x33FFFFFF);
            ring.setStrokeWidth(Math.max(1f, Ui.f(1.5f)));
            c.drawCircle(cx, cy, rad, ring);
            if (row.checked) {
                // A tick in the disc, dark on a light colour.
                int tick = luminance(row.swatch) > 0.5f ? 0xFF1A1A1A : 0xFFFFFFFF;
                iconLine.setColor(tick);
                iconLine.setStrokeWidth(Math.max(1f, Ui.f(3.5f)));
                Dark.icon(c, Dark.CHECK, cx, cy, rad * 1.25f, iconLine, iconLine);
            }
            return;
        }
        ring.setStrokeWidth(Math.max(1f, Ui.f(3f)));
        ring.setColor(row.checked ? Palette.ACCENT : row.enabled ? Palette.TEXT2 : Palette.TEXT3);
        c.drawCircle(cx, cy, rad - Ui.f(1.5f), ring);
        if (row.checked) {
            fill.setColor(Palette.ACCENT);
            c.drawCircle(cx, cy, rad * 0.5f, fill);
        }
    }

    /** A switch in its 80 x 46 box from x, centred on cy: on, the accent track and a white thumb at the right. */
    private void toggle(Canvas c, float x0, float cy, boolean on, boolean focused, boolean enabled) {
        float bw = Ui.f(SWITCH_W), th = Ui.f(28), tr = th / 2f;
        r.set(x0 + Ui.f(4), cy - tr, x0 + bw - Ui.f(4), cy + tr);
        int track = on ? Palette.ACCENT : focused ? Palette.SWITCH_OFF_FOCUS : Palette.SWITCH_OFF;
        fill.setColor(enabled ? track : Palette.alpha(track, 0x40));
        c.drawRoundRect(r, tr, tr, fill);
        float d = Ui.f(on ? 32f : 22f);
        float tx = on ? r.right - Ui.f(16) : r.left + Ui.f(16);
        fill.setColor(on || focused ? 0xFFFFFFFF : Palette.THUMB_OFF);
        if (!enabled) fill.setColor(Palette.alpha(fill.getColor(), 0x80));
        c.drawCircle(tx, cy, d / 2f, fill);
    }

    /** Pure: a colour's relative brightness (0 to 1). */
    static float luminance(int c) {
        return (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF) + 0.0722f * (c & 0xFF)) / 255f;
    }
}
