package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One row of posters a category under the big picture, drawn by one view: the row the remote is on sits just under the
 * picture with the remote's poster lifted at the left end of what is in sight, the row sliding along under it; rows
 * above fade away as they go up under the picture, the next one peeks in below. Only what is in sight is drawn.
 */
final class Shelves extends View {
    interface Host {
        void focused(Shelf row, PosterArt.Item item);

        void chosen(Shelf row, PosterArt.Item item);

        /** The remote went past an edge (Ui.LEFT, UP); true when the screen took it. */
        boolean edge(int dir);
    }

    static final class Shelf {
        final String title;
        final List<PosterArt.Item> items;
        int focus;
        float x;

        Shelf(String title, List<PosterArt.Item> items) {
            this.title = title == null ? "" : title;
            this.items = items;
        }
    }

    /** A poster's width and the gap between posters (design pixels). */
    static final float POSTER_W = 214f, GAP = 26f, HEAD = 58f, AFTER = 34f;

    private final List<Shelf> shelves = new ArrayList<>();
    private int row;
    private boolean active = true;
    private Host host;
    /** Where the rows start on the left, and where the row the remote is on starts (its heading; design pixels). */
    float left = 160f, top = 600f;
    private float scrollY;
    private long last;
    private final PosterArt art;
    private final TextPaint headP = Dark.text(30, 600, Dark.TEXT);
    private final Runnable redraw = this::invalidate;

    Shelves(Context c) {
        super(c);
        art = new PosterArt(Ui.f(POSTER_W));
    }

    void setHost(Host h) {
        host = h;
    }

    float rowH() {
        return HEAD + (art.tileH() / Ui.s) + AFTER;
    }

    /** New rows; the remote stays on the same row (by its title) and the same place along it. */
    void set(List<Shelf> list) {
        Map<String, Shelf> old = new HashMap<>();
        for (Shelf s : shelves) old.put(s.title, s);
        String was = row < shelves.size() ? shelves.get(row).title : null;
        boolean first = shelves.isEmpty();
        shelves.clear();
        int at = 0;
        for (int i = 0; i < list.size(); i++) {
            Shelf n = list.get(i);
            Shelf o = old.get(n.title);
            if (o != null) {
                n.focus = Math.max(0, Math.min(o.focus, n.items.size() - 1));
                n.x = Math.max(0, Math.min(o.x, n.items.size() - 1));
            }
            if (n.title.equals(was)) at = i;
            shelves.add(n);
        }
        row = shelves.isEmpty() ? 0 : Math.min(at, shelves.size() - 1);
        if (first) scrollY = targetY();
        invalidate();
    }

    boolean isEmpty() {
        return shelves.isEmpty();
    }

    Shelf shelf() {
        return row < shelves.size() ? shelves.get(row) : null;
    }

    PosterArt.Item item() {
        Shelf s = shelf();
        return s == null || s.items.isEmpty() ? null : s.items.get(Math.min(s.focus, s.items.size() - 1));
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    private float targetY() {
        return row * Ui.f(rowH());
    }

    private void changed() {
        invalidate();
        if (host != null) host.focused(shelf(), item());
    }

    boolean key(int code) {
        Shelf cur = shelf();
        if (cur == null) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (cur.focus > 0) {
                    cur.focus--;
                    changed();
                    return true;
                }
                return host != null && host.edge(Ui.LEFT);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (cur.focus < cur.items.size() - 1) {
                    cur.focus++;
                    changed();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) {
                    row--;
                    changed();
                    return true;
                }
                return host != null && host.edge(Ui.UP);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < shelves.size() - 1) {
                    row++;
                    changed();
                }
                return true;
            default:
                return false;
        }
    }

    void ok() {
        PosterArt.Item it = item();
        if (it != null && host != null) host.chosen(shelf(), it);
    }

    private boolean step() {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, now - last);
        last = now;
        float k = Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 75.0);
        boolean moving = false;
        float target = targetY();
        if (Math.abs(target - scrollY) > 0.5f) {
            scrollY += (target - scrollY) * k;
            moving = true;
        } else {
            scrollY = target;
        }
        for (Shelf s : shelves) {
            if (Math.abs(s.focus - s.x) > 0.002f) {
                s.x += (s.focus - s.x) * k;
                moving = true;
            } else {
                s.x = s.focus;
            }
        }
        if (!moving) last = 0;
        return moving;
    }

    @Override protected void onDraw(Canvas c) {
        boolean moving = step();
        float topPx = Ui.f(top), h = Ui.f(rowH());
        float y = topPx - scrollY;
        for (int i = 0; i < shelves.size(); i++) {
            if (y > getHeight()) break;
            if (y + h > 0) {
                float alpha = 1f;
                if (y < topPx - 1) alpha = Math.max(0f, 1f - (topPx - y) / (h * 0.5f));
                if (alpha > 0.01f) drawShelf(c, shelves.get(i), y, (int) (alpha * 255), i == row);
            }
            y += h;
        }
        if (moving) postInvalidateOnAnimation();
    }

    private void drawShelf(Canvas c, Shelf s, float y, int a, boolean focusedRow) {
        float x0 = Ui.f(left);
        headP.setAlpha(a);
        c.drawText(Ui.fit(s.title, headP, getWidth() - x0 - Ui.f(60)), x0, y + Ui.f(36), headP);
        float ty = y + Ui.f(HEAD), pitch = art.w + Ui.f(GAP);
        int n = s.items.size();
        int from = Math.max(0, (int) Math.floor(s.x) - 1);
        int to = Math.min(n - 1, from + (int) Math.ceil((getWidth() - x0) / pitch) + 2);
        boolean here = focusedRow && active;
        for (int i = from; i <= to; i++) {
            if (here && i == s.focus) continue;
            float tx = x0 + (i - s.x) * pitch;
            if (tx + art.w < 0) continue;
            art.draw(c, s.items.get(i), tx, ty, false, a, redraw);
        }
        if (here && s.focus < n) art.draw(c, s.items.get(s.focus), x0 + (s.focus - s.x) * pitch, ty, true, a, redraw);
        // The next posters along asked for early, so the row slides on to pictures.
        if (focusedRow) for (int i = to + 1; i <= Math.min(n - 1, to + 4); i++) art.prefetch(s.items.get(i), null);
    }
}
