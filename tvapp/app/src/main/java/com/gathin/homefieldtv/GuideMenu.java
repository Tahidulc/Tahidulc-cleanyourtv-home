package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The TV guide's menus and its programme description, in the 07 look (still 08 of the approved mock-ups):
 *
 * <pre>
 *   openCard(items, focus, row)   OK held: a frosted card 520 wide at (1330, 128), rounded 40 (Frost, mat-dark), over
 *                                 the screen dimmed by the scrim, the row it is for left undimmed with a white edge;
 *                                 Search and Settings as two buttons along its top, then the lines: a section's name in
 *                                 capitals (14 px, letter-spaced, the third label) after a hairline rule, and each line
 *                                 56 high with its icon (22 px) and words (21 px), its value at the right (17 px) and,
 *                                 for a lock, the warning red; the remote's line white with dark words, lifted 3 %.
 *                                 It scrolls when longer than the screen
 *   openPopup(items, cell)        OK on a programme to come: a frosted card 376 wide right under the remote's cell (over
 *                                 it when there is no room below), no scrim, lines 56 high
 *   openAbout(...)                Programme description: a frosted card at (1152, 48), 720 wide: its title (36 px,
 *                                 bold, two lines), its times with the bar and the minutes left, and all it says, over
 *                                 the scrim
 * </pre>
 *
 * Up and Down move through every line in order (Search, then Settings, then the lines; no wrap-around), Left and Right
 * between the two buttons; OK tells the host (Host.chosen with the line's tag); Back is the screen's (close). It fades
 * and slides in from 22 px to the right (130 ms) and out (100 ms), as the panels do. Each line's icon is the mock-ups'
 * for what it does (its tag); a line can carry a value at its right (Item.value: "From catch-up", "By number").
 */
final class GuideMenu extends View {
    static final int ICON = 0, ITEM = 1, SECTION = 2;

    /** A line of the menu. */
    static final class Item {
        final int kind, icon;
        final String tag, title;
        /** A choice's line with a tick at its right, the one in use (Sort channels). */
        boolean checked;
        /** Words at its right, quieter ("From catch-up", "By number"); "" for none. */
        String value = "";
        /** A line that locks or takes away (Block channel): the warning red. */
        boolean warn;
        /** The title in capitals, for a section (made once). */
        private String caps;

        private Item(int kind, String tag, int icon, String title) {
            this.kind = kind;
            this.tag = tag;
            this.icon = icon;
            this.title = title == null ? "" : title;
        }

        static Item icon(String tag, int icon, String title) {
            return new Item(ICON, tag, icon, title);
        }

        static Item item(String tag, String title) {
            return new Item(ITEM, tag, -1, title);
        }

        /** One of a few choices, ticked when it is the one in use. */
        static Item choice(String tag, String title, boolean checked) {
            Item it = new Item(ITEM, tag, -1, title);
            it.checked = checked;
            return it;
        }

        /** A section's name over its lines (the programme's, the channel's), after a rule. */
        static Item section(String title) {
            return new Item(SECTION, null, -1, title);
        }

        /** Words at its right (this line, for chaining). */
        Item value(String v) {
            value = v == null ? "" : v;
            return this;
        }

        /** The warning red (this line). */
        Item warn() {
            warn = true;
            return this;
        }

        boolean focusable() {
            return kind != SECTION;
        }

        /** Its height as a line (the two buttons along the top take one row between them: BUTTON_H). */
        float height() {
            return kind == SECTION ? SECTION_H : ROW;
        }

        String caps() {
            if (caps == null) caps = title.toUpperCase(Locale.UK);
            return caps;
        }
    }

    interface Host {
        void chosen(GuideMenu menu, String tag);
    }

    private static final int NONE = 0, CARD = 1, POPUP = 2, ABOUT = 3;
    // Design pixels (the mock-ups' .ep-menu).
    static final float CARD_X = 1330f, CARD_Y = 128f, CARD_W = 520f, CARD_H = 916f, RADIUS = 40f, ROW = 56f, SECTION_H = 44f,
            BUTTON_H = 56f, PAD = 16f, POPUP_W = 376f, ABOUT_X = 1152f, ABOUT_Y = 48f, ABOUT_W = 720f, SLIDE = 22f;
    static final long OPEN_MS = 130, CLOSE_MS = 100, SCROLL_MS = 150;

    private Host host;
    private int mode = NONE;
    private boolean closing;
    private final List<Item> items = new ArrayList<>();
    private int focus;
    /** How many of the first lines are the buttons along the top (Search, Settings): drawn side by side. */
    private int buttons;
    /** The card's box (design pixels). */
    private float x, y, w, h;
    /** The row it is for (screen pixels), left undimmed; empty for none. */
    private final RectF hole = new RectF();
    private float shown, scroll, scrollFrom, scrollTo;
    private long scrollAt;
    private ValueAnimator anim;
    // The programme description.
    private String aboutTimes = "", aboutLeft = "";
    private float aboutDone = -1f;
    private StaticLayout aboutTitle, aboutDesc;

    private final Frost frost = new Frost(Look07.MAT_DARK);
    private final Lift07 lift = new Lift07();
    /** The row the menu is for has a white edge round it (as it comes); the guide's own rows carry their focus instead. */
    boolean edgedHole = true;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint lineP = Type.paint(Type.MENU, Type.MEDIUM, Look07.L1), valueP = Type.paint(Type.MENU_VALUE, Type.MEDIUM, Look07.L3),
            sectionP = Type.label(14f, Look07.L3), buttonP = Type.paint(19f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint aboutTitleP = Type.paint(36f, Type.BOLD, Look07.L1), aboutP = Type.clock(Type.CAP, Type.MEDIUM, Look07.L2);
    private final TextPaint aboutDescP = Type.paint(Type.BODY, Type.REGULAR, Look07.L2);
    private final TextFit lines = new TextFit(lineP, 128), values = new TextFit(valueP, 64), sections = new TextFit(sectionP, 32);
    private final RectF r = new RectF();

    GuideMenu(Context c) {
        super(c);
        edge.setStyle(Paint.Style.STROKE);
        setVisibility(GONE);
        Shadows.prepare(Look07.SH_ROW, Look07.R_MENU_ITEM);
    }

    void setHost(Host h) {
        host = h;
    }

    boolean isOpen() {
        return mode != NONE && !closing;
    }

    /** The card's box on the screen (screen pixels), for the screen to keep the live picture off it. */
    RectF box(RectF out) {
        out.set(Ui.f(x), Ui.f(y), Ui.f(x + w), Ui.f(y + h));
        return out;
    }

    /** OK held: the whole menu, the remote on line focusAt (a line's index), the row it is for (screen px) undimmed. */
    void openCard(List<Item> list, int focusAt, RectF row) {
        items.clear();
        items.addAll(list);
        x = CARD_X;
        y = CARD_Y;
        w = CARD_W;
        h = Math.min(CARD_H, PAD * 2 + contentHeight());
        start(CARD, focusAt, row);
    }

    /** OK on a programme to come: the small menu under its cell (screen px), the grid's left edge at gridLeft (design px). */
    void openPopup(List<Item> list, RectF cell, float gridLeft) {
        items.clear();
        items.addAll(list);
        w = POPUP_W;
        h = PAD * 2 + contentHeight();
        x = Math.max(gridLeft, Math.min(cell.left / Ui.s - 3f, 1920f - 16f - w));
        float below = cell.bottom / Ui.s + 8f, above = cell.top / Ui.s - 8f - h;
        y = below + h <= 1080f - 8f || above < 8f ? Math.min(below, 1080f - 8f - h) : above;
        start(POPUP, 0, null);
    }

    /** Programme description: the title, its times (with how far through and what's left, on now), all it says. */
    void openAbout(String title, String times, float done, String left, String desc, RectF row) {
        items.clear();
        aboutTimes = times == null ? "" : times;
        aboutLeft = left == null ? "" : left;
        aboutDone = done;
        int tw = Ui.px(ABOUT_W - 68f);
        aboutTitle = layout(title, aboutTitleP, tw, 2, 1.05f);
        String d = desc == null || desc.isEmpty() ? "No description" : desc;
        aboutDesc = layout(d, aboutDescP, tw, 18, Type.BODY_LINES);
        x = ABOUT_X;
        y = ABOUT_Y;
        w = ABOUT_W;
        h = 34f + aboutTitle.getHeight() / Ui.s + 16f + 40f + 18f + aboutDesc.getHeight() / Ui.s + 40f;
        start(ABOUT, -1, row);
    }

    private static StaticLayout layout(String text, TextPaint p, int width, int lines, float spacing) {
        String t = text == null ? "" : text;
        return StaticLayout.Builder.obtain(t, 0, t.length(), p, Math.max(1, width)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacing).setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
    }

    /** The lines' height (design px), the buttons along the top taking one row between them. */
    private float contentHeight() {
        buttons = 0;
        while (buttons < items.size() && buttons < 2 && items.get(buttons).kind == ICON) buttons++;
        float total = buttons > 0 ? BUTTON_H + 10f : 0f;
        for (int i = buttons; i < items.size(); i++) total += items.get(i).height();
        return total;
    }

    private void start(int m, int focusAt, RectF row) {
        mode = m;
        closing = false;
        if (row == null) hole.setEmpty();
        else hole.set(row);
        focus = -1;
        if (focusAt >= 0 && focusAt < items.size() && items.get(focusAt).focusable()) focus = focusAt;
        else focus = next(-1, 1);
        scroll = scrollFrom = scrollTo = 0f;
        scrollAt = 0;
        settle(true);
        setVisibility(VISIBLE);
        animate(1f, OPEN_MS, null);
    }

    /** Fades out, then gone. */
    void close() {
        if (mode == NONE || closing) return;
        closing = true;
        animate(0f, CLOSE_MS, () -> {
            mode = NONE;
            closing = false;
            setVisibility(GONE);
        });
    }

    private void animate(float to, long ms, Runnable after) {
        if (anim != null) anim.cancel();
        if (Exec.inline) {
            shown = to;
            invalidate();
            if (after != null) after.run();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(shown, to);
        a.setDuration(ms);
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            shown = (Float) v.getAnimatedValue();
            invalidate();
        });
        if (after != null) {
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                }

                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (!cancelled) after.run();
                }
            });
        }
        anim = a;
        a.start();
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    private int next(int from, int dir) {
        for (int k = from + dir; k >= 0 && k < items.size(); k += dir) if (items.get(k).focusable()) return k;
        return -1;
    }

    /** The remote while it is open: Up and Down move (Left and Right between the two buttons); every other key stays here. */
    boolean key(int code) {
        if (mode == ABOUT || closing) return true;
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            int n = next(focus, code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            if (n >= 0) {
                focus = n;
                settle(false);
                invalidate();
            } else if (code == KeyEvent.KEYCODE_DPAD_UP && scrollTo > 0f) {
                // At the top: the lines above come into sight.
                scrollFrom = scroll;
                scrollTo = 0f;
                scrollAt = Exec.inline ? 0 : SystemClock.uptimeMillis();
                if (Exec.inline) scroll = 0f;
                invalidate();
            }
        } else if ((code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) && buttons == 2 && focus < 2) {
            focus = code == KeyEvent.KEYCODE_DPAD_LEFT ? 0 : 1;
            invalidate();
        }
        return true;
    }

    void ok() {
        if (closing) return;
        if (mode == ABOUT) {
            close();
            return;
        }
        if (focus < 0 || focus >= items.size()) return;
        String tag = items.get(focus).tag;
        if (host != null && tag != null) host.chosen(this, tag);
    }

    /** Where line i starts (design px from the first line). */
    private float yOf(int i) {
        float y0 = buttons > 0 ? BUTTON_H + 10f : 0f;
        if (i < buttons) return 0f;
        for (int k = buttons; k < i; k++) y0 += items.get(k).height();
        return y0;
    }

    /** The lines scroll so the remote's line (and its section's name over it) stays whole in sight. */
    private void settle(boolean now) {
        if (focus < 0) return;
        float y0 = yOf(focus), y1 = y0 + (focus < buttons ? BUTTON_H : items.get(focus).height());
        float total = PAD * 2 + contentHeight() - PAD * 2;
        if (focus > 0 && items.get(focus - 1).kind == SECTION) y0 -= SECTION_H;
        float room = h - PAD * 2;
        float target = RowList.scrollFor(scrollTo, y0, y1, room, total);
        if (now || Exec.inline) {
            scroll = scrollFrom = scrollTo = target;
            scrollAt = 0;
        } else if (target != scrollTo) {
            scrollFrom = scroll;
            scrollTo = target;
            scrollAt = SystemClock.uptimeMillis();
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (mode == NONE) return;
        long t = SystemClock.uptimeMillis();
        boolean moving = false;
        if (scrollAt != 0) {
            float k = Math.min(1f, (t - scrollAt) / (float) SCROLL_MS);
            scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
            if (k >= 1f) scrollAt = 0;
            else moving = true;
        }
        Item lit = focus >= 0 && focus < items.size() && mode != ABOUT ? items.get(focus) : null;
        moving |= lift.step(lit);
        if (mode != POPUP) drawScrim(c);
        float off = mode == POPUP ? 0f : Ui.f(SLIDE) * (1f - shown);
        int alpha = Math.round(255 * shown);
        r.set(Ui.f(x) + off, Ui.f(y), Ui.f(x + w) + off, Ui.f(y + h));
        frost.draw(c, r, Ui.f(mode == POPUP ? 28f : RADIUS), shown);
        c.save();
        c.clipRect(r.left, r.top + Ui.f(2f), r.right, r.bottom - Ui.f(2f));
        if (alpha < 255) c.saveLayerAlpha(r, alpha);
        if (mode == ABOUT) drawAbout(c, off);
        else drawLines(c, off);
        if (alpha < 255) c.restore();
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    /** The screen dimmed, the row the menu is for left as it is, with a white edge. */
    private void drawScrim(Canvas c) {
        fill.setColor(Palette.alpha(Look07.SCRIM, Math.round(((Look07.SCRIM >>> 24) & 0xFF) * shown)));
        if (hole.isEmpty()) {
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            return;
        }
        c.save();
        c.clipOutRect(hole);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        c.restore();
        if (!edgedHole) return;
        float sw = Math.max(1f, Ui.f(2f));
        edge.setStrokeWidth(sw);
        edge.setColor(Palette.alpha(Look07.L1, Math.round(0xB3 * shown)));
        c.drawRoundRect(hole.left + sw / 2f, hole.top + sw / 2f, hole.right - sw / 2f, hole.bottom - sw / 2f, Ui.f(Look07.R_ROW),
                Ui.f(Look07.R_ROW), edge);
    }

    private void drawLines(Canvas c, float off) {
        float left = Ui.f(x + PAD) + off, right = Ui.f(x + w - PAD) + off;
        float top = y + PAD - scroll;
        // Search and Settings: two buttons side by side along the top.
        if (buttons > 0) {
            float gap = Ui.f(10f), bw = (right - left - gap * (buttons - 1)) / buttons;
            for (int i = 0; i < buttons; i++) {
                float bx = left + i * (bw + gap);
                r.set(bx, Ui.f(top), bx + bw, Ui.f(top + BUTTON_H));
                boolean on = i == focus;
                float k = on ? Math.max(0f, lift.of(items.get(i))) : 0f;
                c.save();
                if (k > 0f) c.scale(1f + 0.03f * k, 1f + 0.03f * k, r.centerX(), r.centerY());
                if (on) Shadows.draw(c, r, Ui.f(20f), Look07.SH_ROW, Math.min(1f, k));
                fill.setColor(on ? Look07.FOCUS_FILL : 0x1FFFFFFF);
                c.drawRoundRect(r, Ui.f(20f), Ui.f(20f), fill);
                Item it = items.get(i);
                int ink = on ? Look07.INK : Look07.L1;
                buttonP.setColor(ink);
                float tw = buttonP.measureText(it.title), is = Ui.f(22f), all = is + Ui.f(10f) + tw;
                float sx = r.centerX() - all / 2f;
                Icons07.draw(c, iconOf(it), sx + is / 2f, r.centerY(), is, ink);
                c.drawText(it.title, sx + is + Ui.f(10f), Type.centred(buttonP, r.centerY()), buttonP);
                c.restore();
            }
        }
        float rowY = top + (buttons > 0 ? BUTTON_H + 10f : 0f);
        for (int i = buttons; i < items.size(); i++) {
            Item it = items.get(i);
            float ih = it.height();
            if (rowY > y + h) break;
            if (rowY + ih >= y) {
                if (it.kind == SECTION) {
                    if (i > buttons) {
                        fill.setColor(Look07.RULE);
                        c.drawRect(left + Ui.f(10f), Ui.f(rowY + 6f), right - Ui.f(10f), Ui.f(rowY + 6f) + Math.max(1f, Ui.f(1f)), fill);
                    }
                    sections.paint().setColor(Look07.L3);
                    sections.line(c, it.caps(), left + Ui.f(16f), Ui.f(rowY + 34f), right - left - Ui.f(32f));
                } else {
                    drawLine(c, it, i == focus, left, right, rowY);
                }
            }
            rowY += ih;
        }
    }

    /** A line: its icon and words, its value at the right; the remote's white with dark words, lifted a little. */
    private void drawLine(Canvas c, Item it, boolean on, float left, float right, float rowY) {
        float top0 = Ui.f(rowY), cy = Ui.f(rowY + ROW / 2f);
        r.set(left, top0, right, top0 + Ui.f(ROW));
        float k = on ? Math.max(0f, lift.of(it)) : 0f;
        c.save();
        if (on) {
            c.scale(1f + 0.03f * k, 1f + 0.03f * k, r.centerX(), r.centerY());
            Shadows.draw(c, r, Ui.f(Look07.R_MENU_ITEM), Look07.SH_ROW, Math.min(1f, k));
            fill.setColor(Look07.FOCUS_FILL);
            c.drawRoundRect(r, Ui.f(Look07.R_MENU_ITEM), Ui.f(Look07.R_MENU_ITEM), fill);
        }
        int ink = on ? Look07.INK : it.warn ? Look07.WARN : Look07.L1;
        int iconInk = on ? Look07.INK : it.warn ? Look07.WARN : Look07.L2;
        float tx = left + Ui.f(18f);
        int icon = iconOf(it);
        if (icon >= 0) {
            Icons07.draw(c, icon, tx + Ui.f(11f), cy, Ui.f(22f), iconInk);
            tx += Ui.f(22f + 16f);
        }
        float room = right - tx - Ui.f(18f);
        if (it.checked) {
            Icons07.draw(c, Icons07.CHECK, right - Ui.f(18f + 13f), cy, Ui.f(26f), on ? Look07.INK : Look07.GREEN);
            room -= Ui.f(40f);
        } else if (!it.value.isEmpty()) {
            valueP.setColor(on ? Look07.INK2 : Look07.L3);
            float vw = Math.min(values.width(it.value), room * 0.5f);
            values.line(c, it.value, right - Ui.f(18f) - vw, Type.centred(valueP, cy), vw);
            room -= vw + Ui.f(16f);
        }
        lineP.setColor(ink);
        lines.line(c, it.title, tx, Type.centred(lineP, cy), room);
        c.restore();
    }

    /** Each line's icon, by what it does (its tag): the mock-ups' own. -1: none. */
    static int iconOf(Item it) {
        String t = it.tag;
        if (t == null) return -1;
        switch (t) {
            case GuideActions.SEARCH: return Icons07.SEARCH;
            case GuideActions.SETTINGS: return Icons07.COG;
            case GuideActions.WATCH: return Icons07.PLAY;
            case GuideActions.START: return Icons07.RESTART;
            case GuideActions.REMIND:
            case GuideActions.UNREMIND: return Icons07.BELL;
            case GuideActions.RECORD: return Icons07.REC;
            case GuideActions.MY_LIST: return it.title.startsWith("Remove") ? Icons07.CHECK : Icons07.PLUS;
            case GuideActions.ABOUT: return Icons07.INFO;
            case GuideActions.FAVOURITE: return Icons07.STAR;
            case GuideActions.ARCHIVE: return Icons07.CATCHUP;
            case GuideActions.BLOCK: return Icons07.LOCK;
            case ChannelMenu.HIDE: return Icons07.EYE;
            case ChannelMenu.RENAME:
            case ChannelMenu.UNNAME: return Icons07.PENCIL;
            case ChannelMenu.LOGO: return Icons07.IMAGE;
            case ChannelMenu.MOVE: return Icons07.MOVE;
            case GroupMenu.RENAME:
            case GroupMenu.UNNAME: return Icons07.PENCIL;
            case GroupMenu.HIDE:
            case GroupMenu.HIDE_ALL: return Icons07.EYE;
            case GroupMenu.MOVE: return Icons07.MOVE;
            case GroupMenu.SORT: return Icons07.SORT;
            case GroupMenu.SHOW_ALL: return Icons07.GRID;
            case GroupMenu.MANAGE: return Icons07.LIST;
            default:
                return -1;
        }
    }

    private void drawAbout(Canvas c, float off) {
        float left = Ui.f(x + 34f) + off, ty = Ui.f(y + 34f);
        Ui.draw(c, aboutTitle, left, ty);
        float base = ty + aboutTitle.getHeight() + Ui.f(16f + 30f);
        float lx = left;
        if (!aboutTimes.isEmpty()) {
            c.drawText(aboutTimes, lx, base, aboutP);
            lx += aboutP.measureText(aboutTimes) + Ui.f(20f);
        }
        if (aboutDone >= 0f) {
            Pills.progress(c, lx, base - Ui.f(7f), Ui.f(150f), aboutDone);
            lx += Ui.f(150f + 20f);
        }
        if (!aboutLeft.isEmpty()) c.drawText(aboutLeft, lx, base, aboutP);
        Ui.draw(c, aboutDesc, left, base + Ui.f(24f));
    }
}
