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

/**
 * The TV guide's menus, as the family's TiviMate draws them (DEVICE.md §2.8), and its programme description:
 *
 * <pre>
 *   openCard(items, focus, row)   OK held: a floating card [1392,32][1888,1048] of the panel's colour (radius 20, a soft
 *                                 shadow) over the screen dimmed by the scrim, the row it is for left undimmed with a
 *                                 2 px edge in the focus colour; rows 80 high: an icon row (40 px icon 40 px in, its name
 *                                 104 px in), a line (40 px in), and a section (16 px, a 2 px rule, then its name in the
 *                                 accent, 24 px, on an 80 px row); the focus pill 16 px in. It scrolls when longer
 *   openPopup(items, cell)        OK on a programme to come: a card 376 wide right under the remote's cell (over it when
 *                                 there is no room below), no scrim, rows 80 high, the pill 8 px in, the words 32 px in
 *   openAbout(...)                Programme description: a card [1152,48] 720 wide, as the player's: the title (two
 *                                 lines), the times with the bar and the minutes left, and all it says, over the scrim
 * </pre>
 *
 * Up and Down move (no wrap-around, the focus jumping), OK tells the host (Host.chosen with the line's tag), Back is
 * the screen's (close). It fades and slides in from 22 px to the right (130 ms) and out (100 ms), as the panels do.
 */
final class GuideMenu extends View {
    static final int ICON = 0, ITEM = 1, SECTION = 2;

    /** A line of the menu. */
    static final class Item {
        final int kind, icon;
        final String tag, title;

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

        /** A section's name in the accent over its lines (the programme's, the channel's), after a rule. */
        static Item section(String title) {
            return new Item(SECTION, null, -1, title);
        }

        boolean focusable() {
            return kind != SECTION;
        }

        float height() {
            return kind == SECTION ? SECTION_H : ROW;
        }
    }

    interface Host {
        void chosen(GuideMenu menu, String tag);
    }

    private static final int NONE = 0, CARD = 1, POPUP = 2, ABOUT = 3;
    // Design pixels.
    static final float CARD_X = 1392f, CARD_Y = 32f, CARD_W = 496f, CARD_H = 1016f, RADIUS = 20f, ROW = 80f, SECTION_H = 98f,
            POPUP_W = 376f, ABOUT_X = 1152f, ABOUT_Y = 48f, ABOUT_W = 720f, SLIDE = 22f;
    static final long OPEN_MS = 130, CLOSE_MS = 100, SCROLL_MS = 150;

    private Host host;
    private int mode = NONE;
    private boolean closing;
    private final List<Item> items = new ArrayList<>();
    private int focus;
    /** The card's box, how far in its pill and words are (design pixels). */
    private float x, y, w, h, inset, textX;
    /** The row it is for (screen pixels), left undimmed; empty for none. */
    private final RectF hole = new RectF();
    private float shown, scroll, scrollFrom, scrollTo;
    private long scrollAt;
    private ValueAnimator anim;
    // The programme description.
    private String aboutTimes = "", aboutLeft = "";
    private float aboutDone = -1f;
    private StaticLayout aboutTitle, aboutDesc;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint lineP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), sectionP = Type.paint(Type.SMALL, Type.MEDIUM, Palette.ACCENT);
    private final TextPaint aboutTitleP = Type.paint(32f, Type.MEDIUM, Palette.TEXT1), aboutP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint aboutDescP = Type.paint(26f, Type.REGULAR, Palette.TEXT2);
    private final RectF r = new RectF();

    GuideMenu(Context c) {
        super(c);
        edge.setStyle(Paint.Style.STROKE);
        setVisibility(GONE);
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
        h = CARD_H;
        inset = 16f;
        textX = 40f;
        start(CARD, focusAt, row);
    }

    /** OK on a programme to come: the small menu under its cell (screen px), the grid's left edge at gridLeft (design px). */
    void openPopup(List<Item> list, RectF cell, float gridLeft) {
        items.clear();
        items.addAll(list);
        w = POPUP_W;
        h = 16f;
        for (Item it : items) h += it.height();
        x = Math.max(gridLeft, Math.min(cell.left / Ui.s - 3f, 1920f - 16f - w));
        float below = cell.bottom / Ui.s + 3f, above = cell.top / Ui.s - 3f - h;
        y = below + h <= 1080f - 8f || above < 8f ? Math.min(below, 1080f - 8f - h) : above;
        inset = 8f;
        textX = 32f;
        start(POPUP, 0, null);
    }

    /** Programme description: the title, its times (with how far through and what's left, on now), all it says. */
    void openAbout(String title, String times, float done, String left, String desc, RectF row) {
        items.clear();
        aboutTimes = times == null ? "" : times;
        aboutLeft = left == null ? "" : left;
        aboutDone = done;
        int tw = Ui.px(ABOUT_W - 64f);
        aboutTitle = layout(title, aboutTitleP, tw, 2, 1.05f);
        String d = desc == null || desc.isEmpty() ? "No description" : desc;
        aboutDesc = layout(d, aboutDescP, tw, 18, 1.12f);
        x = ABOUT_X;
        y = ABOUT_Y;
        w = ABOUT_W;
        h = 32f + aboutTitle.getHeight() / Ui.s + 16f + 40f + 18f + aboutDesc.getHeight() / Ui.s + 36f;
        start(ABOUT, -1, row);
    }

    private static StaticLayout layout(String text, TextPaint p, int width, int lines, float spacing) {
        String t = text == null ? "" : text;
        return StaticLayout.Builder.obtain(t, 0, t.length(), p, Math.max(1, width)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacing).setIncludePad(false).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
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

    /** The remote while it is open: Up and Down move; every other key stays here. */
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

    /** The lines scroll so the remote's line (and its section's name over it) stays whole in sight. */
    private void settle(boolean now) {
        if (focus < 0) return;
        float y0 = 0f;
        for (int i = 0; i < focus; i++) y0 += items.get(i).height();
        float y1 = y0 + items.get(focus).height(), total = 0f;
        for (Item it : items) total += it.height();
        if (focus > 0 && items.get(focus - 1).kind == SECTION) y0 -= ROW;
        float room = h - inset * 2f;
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
        if (mode != POPUP) drawScrim(c);
        float off = mode == POPUP ? 0f : Ui.f(SLIDE) * (1f - shown);
        int alpha = Math.round(255 * shown);
        r.set(Ui.f(x) + off, Ui.f(y), Ui.f(x + w) + off, Ui.f(y + h));
        drawShadow(c, r, alpha);
        fill.setColor(Palette.alpha(Palette.PANEL, alpha));
        c.drawRoundRect(r, Ui.f(RADIUS), Ui.f(RADIUS), fill);
        c.save();
        c.clipRect(r);
        if (alpha < 255) c.saveLayerAlpha(r, alpha);
        if (mode == ABOUT) drawAbout(c, off);
        else drawLines(c, off);
        if (alpha < 255) c.restore();
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    /** The screen dimmed (black at 60 %), the row the menu is for left as it is, with its edge in the focus colour. */
    private void drawScrim(Canvas c) {
        fill.setColor(Palette.alpha(Palette.SCRIM, Math.round(((Palette.SCRIM >>> 24) & 0xFF) * shown)));
        if (hole.isEmpty()) {
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            return;
        }
        c.save();
        c.clipOutRect(hole);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        c.restore();
        float sw = Math.max(1f, Ui.f(2f));
        edge.setStrokeWidth(sw);
        edge.setColor(Palette.alpha(Palette.FOCUS, Math.round(((Palette.FOCUS >>> 24) & 0xFF) * shown)));
        c.drawRoundRect(hole.left + sw / 2f, hole.top + sw / 2f, hole.right - sw / 2f, hole.bottom - sw / 2f, Ui.f(6f), Ui.f(6f), edge);
    }

    /** A soft shadow round the card. */
    private void drawShadow(Canvas c, RectF box, int alpha) {
        for (int i = 4; i >= 1; i--) {
            float g = Ui.f(i * 3f);
            fill.setColor(Palette.alpha(0xFF000000, Math.round(alpha * 0.07f)));
            c.drawRoundRect(box.left - g, box.top - g + Ui.f(2f), box.right + g, box.bottom + g + Ui.f(2f), Ui.f(RADIUS) + g, Ui.f(RADIUS) + g, fill);
        }
    }

    private void drawLines(Canvas c, float off) {
        float left = Ui.f(x) + off, right = Ui.f(x + w) + off;
        float rowY = y + inset - scroll;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float ih = it.height();
            if (rowY > y + h) break;
            if (rowY + ih >= y) {
                if (it.kind == SECTION) {
                    fill.setColor(Palette.DIVIDER);
                    float ry = Ui.f(rowY + 16f);
                    c.drawRect(left + Ui.f(textX), ry, right - Ui.f(textX), ry + Ui.f(2f), fill);
                    sectionP.setColor(Palette.ACCENT);
                    float cy = Ui.f(rowY + 18f + ROW / 2f);
                    c.drawText(Ui.fit(it.title, sectionP, right - left - Ui.f(textX * 2f)), left + Ui.f(textX), Type.centred(sectionP, cy), sectionP);
                } else {
                    float top0 = Ui.f(rowY), cy = Ui.f(rowY + ROW / 2f);
                    if (i == focus) {
                        r.set(left + Ui.f(inset), top0, right - Ui.f(inset), top0 + Ui.f(ROW));
                        Focus.pill(c, r);
                    }
                    lineP.setColor(Palette.TEXT1);
                    float tx = left + Ui.f(textX);
                    if (it.kind == ICON) {
                        RoundButton.glyph(c, it.icon, left + Ui.f(textX + 20f), cy, Ui.f(36f), Palette.TEXT1);
                        tx = left + Ui.f(104f);
                    }
                    c.drawText(Ui.fit(it.title, lineP, right - tx - Ui.f(textX)), tx, Type.centred(lineP, cy), lineP);
                }
            }
            rowY += ih;
        }
    }

    private void drawAbout(Canvas c, float off) {
        float left = Ui.f(x + 32f) + off, ty = Ui.f(y + 32f);
        aboutTitleP.setColor(Palette.TEXT1);
        Ui.draw(c, aboutTitle, left, ty);
        float base = ty + aboutTitle.getHeight() + Ui.f(16f + 30f);
        aboutP.setColor(Palette.TEXT2);
        float lx = left;
        if (!aboutTimes.isEmpty()) {
            c.drawText(aboutTimes, lx, base, aboutP);
            lx += aboutP.measureText(aboutTimes) + Ui.f(24f);
        }
        if (aboutDone >= 0f) {
            Bars.thin(c, lx, base - Ui.f(9f), Ui.f(80f), Ui.f(6f), aboutDone);
            lx += Ui.f(80f + 24f);
        }
        if (!aboutLeft.isEmpty()) c.drawText(aboutLeft, lx, base, aboutP);
        aboutDescP.setColor(Palette.TEXT2);
        Ui.draw(c, aboutDesc, left, base + Ui.f(28f));
    }
}
