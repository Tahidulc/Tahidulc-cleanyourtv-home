package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.Collections;
import java.util.List;

/**
 * A list's posters as the family's other app lays out Movies and TV shows under the title's words: the list's name
 * (30 px, medium) with "n / total" at its right (where the remote is in the list), then the posters seven to a row
 * (244 wide, 18 apart; four landscape cards), the remote's row always at the same height (ROW_Y, 593) with the next row
 * peeking in under it; the rows slide up and down past it (200 ms). One view draws it all, and only the rows in sight,
 * so a category of thousands costs no more than a dozen. Its left edge (left) moves with the categories' column, the
 * posters going on past the screen's right edge as the other app's do.
 */
final class PosterGrid extends View {
    interface Host {
        void focused(PosterArt.Item item);

        void chosen(PosterArt.Item item);

        /** The remote went past the grid's left or top edge (Ui.LEFT, UP); true when the screen took it. */
        boolean edge(int dir);
    }

    /** A portrait poster's width, the gap between posters, the header's baseline and the remote's row's top (design). */
    static final float POSTER_W = 244f, GAP = 18f, HEAD_Y = 568f, ROW_Y = 593f, COUNT_RIGHT = 1864f, WIDTH = 7 * POSTER_W + 6 * GAP;
    static final long SCROLL_MS = 200;

    /** The list as given (its posters may be made only as they are reached: PosterArt.Items), never copied. */
    private List<PosterArt.Item> items = Collections.emptyList();
    private int focus;
    private boolean active;
    private Host host;
    /** The grid's left edge (design pixels). */
    float left = 52f;
    private String title = "";
    private float scroll, scrollFrom, scrollTo;
    private long scrollAt;
    private PosterArt art;
    private boolean artWide;
    private final TextPaint headP = Type.paint(30f, Type.MEDIUM, Palette.TEXT1), countP = Type.paint(30f, Type.REGULAR, Palette.TEXT2);
    private final Runnable redraw = this::invalidate;

    PosterGrid(Context c) {
        super(c);
    }

    void setHost(Host h) {
        host = h;
    }

    /** A list's titles; the remote back at its first (or kept where it was when keep). */
    void set(String name, List<PosterArt.Item> list, boolean keep) {
        title = name == null ? "" : name;
        items = list == null ? Collections.<PosterArt.Item>emptyList() : list;
        if (!keep) focus = 0;
        focus = Math.max(0, Math.min(focus, items.size() - 1));
        settle(!keep);
        invalidate();
    }

    int count() {
        return items.size();
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    PosterArt.Item item() {
        return focus >= 0 && focus < items.size() ? items.get(focus) : null;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    void moveTo(float x) {
        if (left == x) return;
        left = x;
        invalidate();
    }

    private PosterArt art() {
        if (art == null || artWide != PosterArt.landscape) {
            art = new PosterArt(Ui.f(cardW()));
            artWide = PosterArt.landscape;
        }
        return art;
    }

    /** Pure: posters a row (seven portrait, four landscape). */
    static int columns(boolean wide) {
        return wide ? 4 : 7;
    }

    int columns() {
        return columns(PosterArt.landscape);
    }

    /** A card's width (design pixels): seven portrait posters, or four landscape ones, across the same width. */
    private float cardW() {
        int cols = columns();
        return (WIDTH - (cols - 1) * GAP) / cols;
    }

    private float rowPitch() {
        return art().tileH() / Ui.s + GAP;
    }

    private void settle(boolean now) {
        float target = (focus / Math.max(1, columns())) * rowPitch();
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
            settle(false);
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
        if (items.isEmpty()) return;
        long t = SystemClock.uptimeMillis();
        boolean moving = false;
        if (scrollAt != 0) {
            float k = Math.min(1f, (t - scrollAt) / (float) SCROLL_MS);
            scroll = scrollFrom + (scrollTo - scrollFrom) * Fade.EASE.getInterpolation(k);
            if (k >= 1f) scrollAt = 0;
            else moving = true;
        }
        float x0 = Ui.f(left);
        headP.setColor(Palette.TEXT1);
        c.drawText(Ui.fit(title, headP, Ui.f(WIDTH - 200)), x0 + Ui.f(4), Ui.f(HEAD_Y), headP);
        // Where the remote is in the list: "1 / 25", the number white, the rest grey.
        String n = (focus + 1) + "", of = " / " + items.size();
        float right = Ui.f(left + COUNT_RIGHT - 52f);
        countP.setColor(Palette.TEXT2);
        float ow = countP.measureText(of);
        c.drawText(of, right - ow, Ui.f(HEAD_Y), countP);
        countP.setColor(Palette.TEXT1);
        c.drawText(n, right - ow - countP.measureText(n), Ui.f(HEAD_Y), countP);
        countP.setColor(Palette.TEXT2);

        PosterArt a = art();
        int cols = columns();
        float pitchX = Ui.f(cardW() + GAP), pitchY = rowPitch();
        c.save();
        // Under the title's words: the rows above the remote's slide away under the header.
        c.clipRect(0, Ui.f(ROW_Y - 8f), getWidth(), getHeight());
        int firstRow = Math.max(0, (int) Math.floor(scroll / pitchY) - 1);
        int lastRow = (int) Math.ceil((scroll + 1080f - ROW_Y) / pitchY) + 1;
        PosterArt.Item on = active ? item() : null;
        for (int r = firstRow; r <= lastRow; r++) {
            for (int k = 0; k < cols; k++) {
                int i = r * cols + k;
                if (i >= items.size()) break;
                float x = x0 + k * pitchX, y = Ui.f(ROW_Y + r * pitchY - scroll);
                if (x > getWidth()) break;
                PosterArt.Item it = items.get(i);
                a.draw(c, it, x, y, it == on, 255, redraw);
            }
        }
        c.restore();
        // The next posters along asked for early, so the grid moves on to pictures.
        int next = (focus / cols + 1) * cols;
        for (int i = next; i < Math.min(items.size(), next + cols * 2); i++) a.prefetch(items.get(i), null);
        if (moving) postInvalidateOnAnimation();
    }
}
