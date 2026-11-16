package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
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
 * A list down part of the screen (search results, Settings): headings, lines with a small picture, a title and a line
 * under it, and the highlight on one of them. It scrolls to keep the highlight in view.
 */
final class ListPanel extends View {
    static final int TEXT = 0, POSTER = 1, LOGO = 2, HEADING = 3, NOTE = 4;

    static final class Item {
        final Object tag;
        final int kind;
        String title, subtitle, picture = "";

        Item(Object tag, int kind, String title, String subtitle) {
            this.tag = tag;
            this.kind = kind;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
        }

        boolean focusable() {
            return kind != HEADING && kind != NOTE;
        }
    }

    interface Host {
        void chosen(ListPanel list, Item item);

        void focused(ListPanel list, Item item);

        /** Past the list's edge (Ui.LEFT, UP, RIGHT, DOWN); true when the screen took it. */
        boolean edge(ListPanel list, int dir);
    }

    /** Where it goes (design pixels). */
    float x = 820f, y = 80f, w = 1060f, bottom = 1060f;
    private final List<Item> items = new ArrayList<>();
    private int focus = -1;
    private boolean active;
    private Host host;
    private float scroll, scrollTo;
    private long last;

    private final Glow glow = new Glow();
    private final TextPaint titleP = Ui.paint(30, false, Ui.WHITE), titleOn = Ui.paint(30, true, Ui.WHITE);
    private final TextPaint subP = Ui.paint(24, false, Ui.FAINT), headP = Ui.paint(26, true, Ui.DIM), noteP = Ui.paint(26, false, Ui.DIM);
    private final Paint line = Ui.fill(0x1FFFFFFF), logoBack = Ui.fill(Ui.tint(0x33062A66)), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint tileBack = Ui.fill(com.gathin.broadcast.LogoTile.GROUND);
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;

    ListPanel(Context c) {
        super(c);
    }

    void setHost(Host h) {
        host = h;
    }

    static float height(Item it) {
        switch (it.kind) {
            case HEADING: return 64f;
            case NOTE: return 52f;
            case POSTER: return 112f;
            case LOGO: return 96f;
            default: return it.subtitle.isEmpty() ? 76f : 92f;
        }
    }

    /** New items; the highlight stays on the same line number when it can. */
    void setItems(List<Item> list) {
        items.clear();
        items.addAll(list);
        if (focus >= items.size() || focus < 0 || !items.get(focus).focusable()) focus = firstFocusable(0, 1);
        settle(true);
        invalidate();
    }

    void setFocus(int i) {
        if (i >= 0 && i < items.size() && items.get(i).focusable()) {
            focus = i;
            settle(false);
            invalidate();
        }
    }

    List<Item> items() {
        return items;
    }

    Item item() {
        return focus >= 0 && focus < items.size() ? items.get(focus) : null;
    }

    int focusIndex() {
        return focus;
    }

    boolean canFocus() {
        return firstFocusable(0, 1) >= 0;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    private int firstFocusable(int from, int dir) {
        for (int i = from; i >= 0 && i < items.size(); i += dir) if (items.get(i).focusable()) return i;
        return -1;
    }

    boolean key(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: {
                int n = firstFocusable(focus - 1, -1);
                if (n >= 0) {
                    focus = n;
                    moved();
                    return true;
                }
                if (focus > 0) {
                    scrollTo = 0;
                    invalidate();
                }
                return host != null && host.edge(this, Ui.UP);
            }
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                int n = firstFocusable(focus + 1, 1);
                if (n >= 0) {
                    focus = n;
                    moved();
                    return true;
                }
                return host != null && host.edge(this, Ui.DOWN);
            }
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return host != null && host.edge(this, Ui.LEFT);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return host != null && host.edge(this, Ui.RIGHT);
            default:
                return false;
        }
    }

    void ok() {
        Item it = item();
        if (it != null && host != null) host.chosen(this, it);
    }

    private void moved() {
        settle(false);
        invalidate();
        if (host != null) host.focused(this, item());
    }

    private void settle(boolean now) {
        if (focus < 0) {
            scrollTo = 0;
        } else {
            float top = 0;
            for (int i = 0; i < focus; i++) top += height(items.get(i));
            float h = height(items.get(focus)), room = bottom - y;
            // A heading just above the highlight stays in view with it.
            float headroom = focus > 0 && items.get(focus - 1).kind == HEADING ? height(items.get(focus - 1)) : 0;
            if (top - headroom < scrollTo) scrollTo = Math.max(0, top - headroom);
            if (top + h > scrollTo + room) scrollTo = top + h - room;
        }
        if (now || Exec.inline) scroll = scrollTo;
    }

    @Override protected void onDraw(Canvas c) {
        long t = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, t - last);
        last = t;
        boolean moving = false;
        if (Math.abs(scrollTo - scroll) > 0.5f) {
            scroll += (scrollTo - scroll) * (Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 70.0));
            moving = true;
        } else {
            scroll = scrollTo;
            last = 0;
        }
        c.save();
        c.clipRect(Ui.f(x - 24), Ui.f(y - 16), Ui.f(x + w + 24), Ui.f(bottom + 16));
        float top = y - scroll;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float h = height(it);
            if (top > bottom) break;
            if (top + h >= y - 20) drawItem(c, it, i == focus, top, h);
            top += h;
        }
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    private void drawItem(Canvas c, Item it, boolean on, float top, float h) {
        float left = Ui.f(x), right = Ui.f(x + w);
        if (it.kind == HEADING) {
            c.drawText(Ui.fit(it.title, headP, right - left), left, Ui.f(top + 44), headP);
            return;
        }
        if (it.kind == NOTE) {
            c.drawText(Ui.fit(it.title, noteP, right - left), left, Ui.f(top + 34), noteP);
            return;
        }
        r.set(left - Ui.f(12), Ui.f(top + 4), right + Ui.f(12), Ui.f(top + h - 4));
        if (on && active) glow.draw(c, r);
        else if (on) glow.box(c, r);
        else c.drawRect(left, Ui.f(top + h - 1), right, Ui.f(top + h), line);
        float tx = left + Ui.f(10);
        if (it.kind == POSTER) {
            float pw = Ui.f(64), ph = Ui.f(96);
            Bitmap b = Pics.get(it.picture, Math.round(pw), Math.round(ph), Pics.CROP, redraw);
            r.set(tx, Ui.f(top + 8), tx + pw, Ui.f(top + 8) + ph);
            if (b != null) c.drawBitmap(b, null, r, bmp);
            else c.drawRect(r, logoBack);
            tx += pw + Ui.f(24);
        } else if (it.kind == LOGO) {
            float lw = Ui.f(128), lh = Ui.f(72);
            r.set(tx, Ui.f(top) + (Ui.f(h) - lh) / 2f, tx + lw, Ui.f(top) + (Ui.f(h) + lh) / 2f);
            // A channel's logo on its black tile (Pics.tiles), else on the guide's blue.
            boolean tile = Pics.tiles;
            if (tile) c.drawRoundRect(r, Math.min(lw, lh) * 0.12f, Math.min(lw, lh) * 0.12f, tileBack);
            else c.drawRect(r, logoBack);
            Bitmap b = tile ? Pics.get(it.picture, Math.round(lw), Math.round(lh), Pics.TILE, redraw)
                    : Pics.get(it.picture, Math.round(lw - Ui.f(12)), Math.round(lh - Ui.f(12)), Pics.FIT, redraw);
            if (b != null) c.drawBitmap(b, r.centerX() - b.getWidth() / 2f, r.centerY() - b.getHeight() / 2f, bmp);
            tx += lw + Ui.f(24);
        }
        float room = right - tx - Ui.f(16);
        TextPaint tp = on ? titleOn : titleP;
        float mid = Ui.f(top + h / 2);
        if (it.subtitle.isEmpty()) {
            c.drawText(Ui.fit(it.title, tp, room), tx, mid + Ui.f(10), tp);
        } else {
            c.drawText(Ui.fit(it.title, tp, room), tx, mid - Ui.f(4), tp);
            c.drawText(Ui.fit(it.subtitle, subP, room), tx, mid + Ui.f(28), subP);
        }
    }
}
