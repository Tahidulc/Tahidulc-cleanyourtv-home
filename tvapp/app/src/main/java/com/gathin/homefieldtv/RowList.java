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
import java.util.Locale;

/**
 * A list of rows, as every panel draws one: Settings and its pages, side menus, pickers and dialogs' buttons; in the 07
 * look (the mock-ups' .lrow): where the remote is, the row turns white with dark words and lifts a little (2.5 %, 07's
 * springy lift) over a soft shadow; elsewhere, words on the panel.
 *
 * <pre>
 *   Row.item(tag, title, value)        a title (24 px, semibold) with its value or description under it (18 px, the
 *                                      third label): 84 high with one line, 96 with two (longer words take another line)
 *   Row.toggle(tag, title, value, on)  the same with 07's switch at the right (64 x 38, green when on)
 *   Row.header(title)                  a section's name in Homefield's amber, in capitals, letter-spaced (15 px): 58 high
 *   Row.note(text)                     a quiet note (19 px), as many lines as it needs
 *   Row.radio(tag, title, checked)     a choice: its name, and the one in use ticked in green at the right;
 *                                      Row.swatch(...) has a colour's disc before its name, a tick in it when chosen
 *   Row.icon(tag, icon, title)         a menu line, 72 high: an icon (Dark's kinds) and its name
 *   Row.divider()                      a hairline rule between groups of lines (21 high)
 *   Row.stepper(tag, value)            a value with a round button each side (the player's audio offset): ‹ and ›,
 *                                      Left and Right move between them (side 0 and 1), OK presses the one lit
 *   Row.channel(tag, ch, title, value) an item with the channel's logo (Logos, 96 x 56) before its words (Settings ›
 *                                      Channels &amp; categories › Rename channels)
 * </pre>
 *
 * Rows can be greyed (enabled = false: the fourth label, OK does nothing) and carry the amber dot after their title
 * (dot). When the list is not where the remote is (setActive(false)) its row keeps a quiet white at 16 %. Up and Down
 * jump to the next row that can be chosen (headers, notes and dividers are passed over; no wrap-around), the list
 * scrolling to keep it whole in sight (with the section name just over it), gliding on the display's frame clock
 * (Glide: a held key glides on smoothly); Page keys move six rows. OK lights the row for FLASH_MS and tells the host.
 * A frame makes nothing new: every row's words are laid out or cut to fit once.
 *
 * The list's place is x, top, w, bottom in design pixels (the panel sets them); heading, when set, is a title drawn
 * at headingY (34 px, bold) that slides with the list. Hosts change a row (its value, its switch) and call refresh(),
 * or give new rows with setRows() (the remote stays on the same line number when it can).
 */
final class RowList extends View {
    static final int ITEM = 0, HEADER = 1, NOTE = 2, RADIO = 3, ICON = 4, DIVIDER = 5, STEPPER = 6;
    /** Heights and places (design pixels). */
    static final float ROW_1 = 84f, ROW_2 = 96f, TITLE_LINE = 30f, VALUE_LINE = 24f, HEADER_H = 58f, ICON_H = 72f,
            DIVIDER_H = 21f, INSET = 12f, TEXT_X = 34f, ICON_TEXT_X = 84f;
    static final float SWITCH_W = 64f, SWITCH_H = 38f, SWITCH_RIGHT = 34f, RADIO_X = 56f, RADIO_D = 34f, RADIO_TEXT_X = 92f;
    /** A channel row's logo (Row.channel) and the gap after it. */
    static final float LOGO_W = 96f, LOGO_H = 56f, LOGO_GAP = 18f;
    static final long SCROLL_MS = 150, FLASH_MS = 180;
    /** A stepper's row (its buttons at its top, the gap under them) and its buttons. */
    static final float STEPPER_H = 110f, STEP_W = 70f, STEP_H = 70f;
    /** The row where the remote is grows this much (the mock-ups' scale(1.025)). */
    static final float LIFT = 1.025f;

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
        /** The amber dot after the title: something waits there (an update). */
        boolean dot;
        /** An icon before the title (ICON rows; Dark's kinds), else -1. */
        int icon = -1;
        /** A radio row's colour disc in place of its circle (0: none). */
        int swatch;
        /** A stepper's button the remote is on: 0 the left one (‹), 1 the right (›). */
        int side;
        /** A channel's logo before an item's words (Row.channel), else null. */
        com.gathin.homefieldtv.engine.Channel channel;
        // Laid out for the list's width (setRows / refresh).
        StaticLayout titleL, valueL;
        float height;
        /** The title cut to fit one line (headers, icon rows), for the width and words it was cut for. */
        private String fit, fitFor;
        private float fitW = -1f;

        /** The title cut to fit width with p (kept until the title or the width changes). */
        String fitted(TextPaint p, float width) {
            if (fit == null || width != fitW || !title.equals(fitFor)) {
                fit = Ui.fit(kind == HEADER ? title.toUpperCase(Locale.UK) : title, p, width);
                fitFor = title;
                fitW = width;
            }
            return fit;
        }

        Row(int kind, Object tag, String title, String value) {
            this.kind = kind;
            this.tag = tag;
            this.title = title == null ? "" : title;
            this.value = value == null ? "" : value;
        }

        static Row item(Object tag, String title, String value) {
            return new Row(ITEM, tag, title, value);
        }

        /** An item with a channel's logo before its words. */
        static Row channel(Object tag, com.gathin.homefieldtv.engine.Channel ch, String title, String value) {
            Row r = new Row(ITEM, tag, title, value);
            r.channel = ch;
            return r;
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
    /** A title over the rows (null: none), its baseline at headingY. */
    String heading;
    float headingY = 87f;

    private final List<Row> rows = new ArrayList<>();
    private int focus = -1;
    private boolean active = true;
    private Host host;
    /** The list's scroll (design pixels), gliding to where the remote's row is whole in sight. */
    private final Glide scroll = new Glide(SCROLL_MS);
    /** The remote's row lifting as it comes to it (07's springy lift). */
    private final Lift07 lift = new Lift07();
    private long flashAt;
    private int flash = -1;
    /** The heading cut to fit (kept while it and the width stay the same). */
    private String headingFit, headingFor;
    private float headingW = -1f;

    private final TextPaint titleP = Type.paint(24f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint valueP = Type.paint(18f, Type.MEDIUM, Look07.L3);
    private final TextPaint headerP = Type.label(Type.LABEL, Look07.AMBER);
    private final TextPaint noteP = Type.paint(19f, Type.REGULAR, Look07.L3);
    private final TextPaint headingP = Type.paint(Type.TITLE, Type.BOLD, Look07.L1);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconLine = new Paint(Paint.ANTI_ALIAS_FLAG), iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), logoBox = new RectF();
    /** A channel row's logo come in: drawn again. */
    private final Runnable redraw = this::invalidate;

    RowList(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        iconLine.setStyle(Paint.Style.STROKE);
        iconLine.setStrokeCap(Paint.Cap.ROUND);
        iconLine.setStrokeJoin(Paint.Join.ROUND);
        Shadows.prepare(Look07.SH_ROW, Look07.R_ROW);
        Pills.prepare();
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
                    row.height = row.titleL.getHeight() / Ui.s + 20f;
                    break;
                }
                case RADIO: {
                    float left = row.swatch != 0 ? RADIO_TEXT_X : TEXT_X;
                    row.titleL = Ui.layout(row.title, titleP, Ui.px(w - left - TEXT_X - 40f), 2);
                    row.height = ROW_1 + (row.titleL.getLineCount() - 1) * TITLE_LINE;
                    break;
                }
                default: {
                    float room = w - TEXT_X * 2 - (row.toggle != null ? SWITCH_W + 20f : 0f) - (row.dot ? 30f : 0f)
                            - (row.channel != null ? LOGO_W + LOGO_GAP : 0f);
                    row.titleL = Ui.layout(row.title, titleP, Ui.px(room), 2);
                    row.valueL = row.value.isEmpty() ? null : Ui.layout(row.value, valueP, Ui.px(room), 2);
                    int tl = row.titleL.getLineCount(), vl = row.valueL == null ? 0 : row.valueL.getLineCount();
                    row.height = vl == 0 ? ROW_1 + (tl - 1) * TITLE_LINE : ROW_2 + (tl - 1) * TITLE_LINE + (vl - 1) * VALUE_LINE;
                    break;
                }
            }
            // A little room between rows, as the mock-ups' 4 px.
            if (row.focusable()) row.height += 4f;
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
            target = scrollFor(scroll.target(), y0, y1, bottom - top, total());
        }
        if (now) scroll.jump(target);
        else scroll.to(target);
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
                if (scroll.target() > 0f) {
                    scroll.to(0f);
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
        // Its rows are placed in screen pixels; laid out inside a panel's page (RightPanel, SettingsPanel: so a frame of
        // the rows moving redraws only that page, not the screen under it), it draws from the screen's corner.
        int l = getLeft(), t0 = getTop();
        if (l != 0 || t0 != 0) {
            c.save();
            c.translate(-l, -t0);
            drawRows(c);
            c.restore();
        } else {
            drawRows(c);
        }
    }

    private void drawRows(Canvas c) {
        long t = SystemClock.uptimeMillis();
        boolean moving = scroll.step();
        Row lit = active ? focused() : null;
        moving |= lift.step(lit);
        if (heading != null && !heading.isEmpty()) {
            headingP.setColor(Look07.L1);
            float hw = Ui.f(w - TEXT_X * 2);
            if (headingFit == null || hw != headingW || !heading.equals(headingFor)) {
                headingFit = Ui.fit(heading, headingP, hw);
                headingFor = heading;
                headingW = hw;
            }
            c.drawText(headingFit, Ui.f(x + TEXT_X - 12f), Ui.f(headingY), headingP);
        }
        c.save();
        // Room for the remote's row to lift past the list's sides.
        c.clipRect(Ui.f(x - 8f), Ui.f(top), Ui.f(x + w + 8f), Ui.f(bottom));
        float y = top - scroll.value();
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
        float left = Ui.f(x), right = Ui.f(x + w), top0 = Ui.f(y), h = Ui.f(row.height - (row.focusable() ? 4f : 0f));
        switch (row.kind) {
            case HEADER:
                headerP.setColor(Look07.AMBER);
                c.drawText(row.fitted(headerP, right - left - Ui.f(TEXT_X * 2)), left + Ui.f(TEXT_X - 10f), top0 + Ui.f(40f), headerP);
                return;
            case NOTE:
                noteP.setColor(Look07.L3);
                if (row.titleL != null) Ui.draw(c, row.titleL, left + Ui.f(TEXT_X - 10f), top0 + Ui.f(8f));
                return;
            case DIVIDER:
                fill.setColor(Look07.RULE);
                c.drawRect(left + Ui.f(22f), top0 + h / 2f, right - Ui.f(22f), top0 + h / 2f + Math.max(1f, Ui.f(1f)), fill);
                return;
            default:
                break;
        }
        boolean on = i == focus, lit = on && active;
        if (row.kind == STEPPER) {
            stepper(c, row, left, right, top0, lit);
            return;
        }
        r.set(left + Ui.f(INSET), top0, right - Ui.f(INSET), top0 + h);
        float rad = Ui.f(Look07.R_ROW);
        float k = lit ? Math.max(0f, lift.of(row)) : 0f;
        c.save();
        if (k > 0f) {
            float s = 1f + (LIFT - 1f) * k;
            c.scale(s, s, r.centerX(), r.centerY());
            Shadows.draw(c, r, rad, Look07.SH_ROW, Math.min(1f, k));
            fill.setColor(Look07.FOCUS_FILL);
            c.drawRoundRect(r, rad, rad, fill);
        } else if (on) {
            fill.setColor(lit ? Look07.FOCUS_FILL : Look07.SELECTED);
            c.drawRoundRect(r, rad, rad, fill);
        }
        if (i == flash && t - flashAt < FLASH_MS) {
            // OK's moment: the row lit a little more, fading.
            float f = 1f - (t - flashAt) / (float) FLASH_MS;
            fill.setColor(Palette.alpha(lit ? 0xFF000000 : 0xFFFFFFFF, Math.round(0x1F * f)));
            c.drawRoundRect(r, rad, rad, fill);
        }
        int ink = lit ? Look07.INK : row.enabled ? Look07.L1 : Look07.L4;
        int quiet = lit ? Look07.INK2 : row.enabled ? Look07.L3 : Look07.L4;
        float cy = top0 + h / 2f;
        if (row.kind == ICON) {
            iconLine.setColor(lit ? Look07.INK : Look07.L2);
            iconLine.setStrokeWidth(Math.max(1f, Ui.f(2.6f)));
            iconFill.setColor(lit ? Look07.INK : Look07.L2);
            Dark.icon(c, row.icon, left + Ui.f(INSET + 30f), cy, Ui.f(30), iconLine, iconFill);
            titleP.setColor(ink);
            c.drawText(row.fitted(titleP, right - left - Ui.f(ICON_TEXT_X + TEXT_X)), left + Ui.f(ICON_TEXT_X),
                    Type.centred(titleP, cy), titleP);
            c.restore();
            return;
        }
        if (row.kind == RADIO) {
            float tx = left + Ui.f(row.swatch != 0 ? RADIO_TEXT_X : TEXT_X);
            if (row.swatch != 0) swatch(c, row, left + Ui.f(RADIO_X), cy);
            titleP.setColor(ink);
            if (row.titleL != null) Ui.draw(c, row.titleL, tx, cy - row.titleL.getHeight() / 2f);
            // The one in use: a tick at the right (dark on the white row, green elsewhere).
            if (row.checked && row.swatch == 0) Icons07.draw(c, Icons07.CHECK, right - Ui.f(INSET + 34f), cy, Ui.f(28f), lit ? Look07.INK : Look07.GREEN);
            c.restore();
            return;
        }
        // An item: its title, its value under it, its switch at the right.
        titleP.setColor(ink);
        valueP.setColor(quiet);
        float block = row.titleL.getHeight() + (row.valueL == null ? 0 : Ui.f(3) + row.valueL.getHeight());
        float ty = cy - block / 2f, tx = left + Ui.f(TEXT_X);
        if (row.channel != null) {
            // The channel's logo on its tile before its words (Logos: the empty tile while it is read).
            logoBox.set(tx, cy - Ui.f(LOGO_H) / 2f, tx + Ui.f(LOGO_W), cy + Ui.f(LOGO_H) / 2f);
            if (!Logos.draw(c, row.channel, logoBox, redraw)) Logos.empty(c, logoBox);
            tx += Ui.f(LOGO_W + LOGO_GAP);
        }
        Ui.draw(c, row.titleL, tx, ty);
        if (row.dot) {
            float dx = tx + row.titleL.getLineWidth(0) + Ui.f(16);
            fill.setColor(Look07.AMBER);
            c.drawCircle(dx, ty + row.titleL.getLineBaseline(0) - Ui.f(8), Ui.f(5), fill);
        }
        if (row.valueL != null) Ui.draw(c, row.valueL, tx, ty + row.titleL.getHeight() + Ui.f(3));
        if (row.toggle != null) {
            Pills.toggle(c, right - Ui.f(SWITCH_RIGHT + SWITCH_W), cy, row.toggle, lit);
            if (!row.enabled) {
                fill.setColor(0x66000000);
                c.drawRoundRect(right - Ui.f(SWITCH_RIGHT + SWITCH_W), cy - Ui.f(19f), right - Ui.f(SWITCH_RIGHT), cy + Ui.f(19f),
                        Ui.f(19f), Ui.f(19f), fill);
            }
        }
        c.restore();
    }

    /** A stepper: its two round buttons at the row's top (the lit one white), the value between them. */
    private void stepper(Canvas c, Row row, float left, float right, float top0, boolean lit) {
        float bw = Ui.f(STEP_W), in = Ui.f(TEXT_X), cy = top0 + Ui.f(STEP_H) / 2f;
        for (int s = 0; s < 2; s++) {
            float cx = s == 0 ? left + in + bw / 2f : right - in - bw / 2f;
            Pills.circle(c, cx, cy, s == 0 ? Icons07.LEFT : Icons07.RIGHT, lit && row.side == s, false);
        }
        if (stepP == null) stepP = Type.paint(Type.H3, Type.SEMIBOLD, Look07.L1);
        String v = Ui.fit(row.value, stepP, right - left - in * 2 - bw * 2 - Ui.f(32));
        c.drawText(v, (left + right) / 2f - stepP.measureText(v) / 2f, Type.centred(stepP, cy), stepP);
    }

    private TextPaint stepP;

    /** A colour's disc centred on (cx, cy): a tick in it when chosen, dark on a light colour. */
    private void swatch(Canvas c, Row row, float cx, float cy) {
        float rad = Ui.f(RADIO_D) / 2f;
        fill.setColor(row.swatch);
        c.drawCircle(cx, cy, rad, fill);
        ring.setColor(0x33FFFFFF);
        ring.setStrokeWidth(Math.max(1f, Ui.f(1.5f)));
        c.drawCircle(cx, cy, rad, ring);
        if (row.checked) Icons07.draw(c, Icons07.CHECK, cx, cy, rad * 1.3f, luminance(row.swatch) > 0.5f ? 0xFF1A1A1A : 0xFFFFFFFF);
    }

    /** Pure: a colour's relative brightness (0 to 1). */
    static float luminance(int c) {
        return (0.2126f * ((c >> 16) & 0xFF) + 0.7152f * ((c >> 8) & 0xFF) + 0.0722f * (c & 0xFF)) / 255f;
    }
}
