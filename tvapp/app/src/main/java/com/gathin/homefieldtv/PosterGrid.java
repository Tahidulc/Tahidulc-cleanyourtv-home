package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;

/**
 * Every title of a list in one grid, drawn by one view: the list's name and how many at the top, the posters in rows
 * under it, the grid sliding up and down to keep the remote's row in sight. Thousands of titles cost no more than the
 * rows in sight.
 */
final class PosterGrid extends View {
    interface Host {
        void focused(PosterArt.Item item);

        void chosen(PosterArt.Item item);

        /** The remote went past the grid's left or top edge; true when the screen took it. */
        boolean edge(int dir);
    }

    static final float GAP_X = 30f, GAP_Y = 30f, HEAD = 120f;

    private final List<PosterArt.Item> items = new ArrayList<>();
    private int focus;
    private boolean active;
    private Host host;
    /** The grid's left and right edges (design pixels). */
    float left = 600f, right = 1880f;
    private String title = "", empty = "";
    private float scroll, scrollTo;
    private long last;
    private final Lift lift = new Lift();
    private PosterArt art;
    private float artFor = -1f;
    private final TextPaint headP = Dark.text(40, 600, Dark.TEXT), countP = Dark.text(24, 500, Dark.MUTED);
    private final TextPaint emptyP = Dark.text(28, 400, Dark.SOFT);
    private final Runnable redraw = this::invalidate;

    PosterGrid(Context c) {
        super(c);
    }

    void setHost(Host h) {
        host = h;
    }

    /** A list's titles; the remote back at its first (or kept where it was when keep). */
    void set(String name, List<PosterArt.Item> list, String whenEmpty, boolean keep) {
        title = name == null ? "" : name;
        empty = whenEmpty == null ? "" : whenEmpty;
        items.clear();
        items.addAll(list);
        if (!keep) {
            focus = 0;
            scroll = scrollTo = 0;
        }
        focus = Math.max(0, Math.min(focus, items.size() - 1));
        settle();
        invalidate();
    }

    int count() {
        return items.size();
    }

    PosterArt.Item item() {
        return focus < items.size() ? items.get(focus) : null;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    private PosterArt art() {
        // As many posters across as fit at about the rows' size.
        int cols = columns();
        float w = (Ui.f(right - left) - (cols - 1) * Ui.f(GAP_X)) / cols;
        if (art == null || w != artFor) {
            art = new PosterArt(w);
            artFor = w;
        }
        return art;
    }

    int columns() {
        return Math.max(3, (int) ((right - left + GAP_X) / (Shelves.POSTER_W + GAP_X)));
    }

    private float rowPitch() {
        return art().tileH() + Ui.f(GAP_Y);
    }

    private void settle() {
        if (getHeight() <= 0) return;
        int cols = columns();
        int r = focus / cols;
        float y = r * rowPitch(), view = getHeight() - Ui.f(HEAD) - Ui.f(40);
        if (y - scrollTo > view - rowPitch()) scrollTo = y - (view - rowPitch());
        if (y < scrollTo) scrollTo = y;
        scrollTo = Math.max(0, scrollTo);
        if (Exec.inline) scroll = scrollTo;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        settle();
    }

    boolean key(int code) {
        if (items.isEmpty()) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) {
                return host != null && host.edge(code == KeyEvent.KEYCODE_DPAD_LEFT ? Ui.LEFT : Ui.UP);
            }
            return false;
        }
        int cols = columns(), was = focus;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus % cols == 0) return host != null && host.edge(Ui.LEFT);
                focus--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus % cols < cols - 1 && focus < items.size() - 1) focus++;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus < cols) return host != null && host.edge(Ui.UP);
                focus -= cols;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus + cols < items.size()) focus += cols;
                else if (focus / cols < (items.size() - 1) / cols) focus = items.size() - 1;
                break;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                focus = Math.min(items.size() - 1, focus + cols * 3);
                break;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                focus = Math.max(0, focus - cols * 3);
                break;
            default:
                return false;
        }
        if (focus != was) {
            settle();
            invalidate();
            if (host != null) host.focused(item());
        }
        return true;
    }

    void ok() {
        PosterArt.Item it = item();
        if (it != null && host != null) host.chosen(it);
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, now - last);
        last = now;
        boolean moving = false;
        if (Math.abs(scrollTo - scroll) > 0.5f) {
            scroll += (scrollTo - scroll) * (Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 75.0));
            moving = true;
        } else {
            scroll = scrollTo;
            last = 0;
        }
        float x0 = Ui.f(left);
        c.drawText(Ui.fit(title, headP, Ui.f(right - left) - Ui.f(160)), x0, Ui.f(100), headP);
        if (!items.isEmpty()) {
            String n = items.size() == 1 ? "1 title" : items.size() + " titles";
            c.drawText(n, Ui.f(right) - countP.measureText(n), Ui.f(98), countP);
        } else if (!empty.isEmpty()) {
            StaticLayout l = Ui.layout(empty, emptyP, Math.round(Ui.f(right - left) * 0.8f), 4);
            Ui.draw(c, l, x0, Ui.f(150));
        }
        PosterArt a = art();
        int cols = columns();
        float pitchX = a.w + Ui.f(GAP_X), pitchY = rowPitch(), top = Ui.f(HEAD) + Ui.f(24);
        c.save();
        c.clipRect(x0 - Ui.f(30), Ui.f(HEAD) - Ui.f(4), getWidth(), getHeight());
        int firstRow = Math.max(0, (int) Math.floor(scroll / pitchY) - 1);
        int lastRow = (int) Math.ceil((scroll + getHeight()) / pitchY) + 1;
        // The poster the remote is on grows into its lift and ring as the one it left settles back; both drawn last.
        moving |= lift.step(active ? item() : null);
        PosterArt.Item lifted = null, settling = null;
        float lx = 0, ly = 0, sx = 0, sy = 0;
        for (int r = firstRow; r <= lastRow; r++) {
            for (int k = 0; k < cols; k++) {
                int i = r * cols + k;
                if (i >= items.size()) break;
                float x = x0 + k * pitchX, y = top + r * pitchY - scroll;
                PosterArt.Item it = items.get(i);
                if (lift.of(it) > 0f) {
                    if (it == lift.leaving()) {
                        settling = it;
                        sx = x;
                        sy = y;
                    } else {
                        lifted = it;
                        lx = x;
                        ly = y;
                    }
                    continue;
                }
                a.draw(c, it, x, y, 0f, 255, redraw);
            }
        }
        if (settling != null) a.draw(c, settling, sx, sy, lift.of(settling), 255, redraw);
        if (lifted != null) a.draw(c, lifted, lx, ly, lift.of(lifted), 255, redraw);
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }
}
