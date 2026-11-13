package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

import java.util.ArrayList;
import java.util.List;

/**
 * The TV guide's grid, drawn by one view: the programme the remote is on written large across the top, the times
 * along a ruler, and a row a channel (its number and logo, then its programmes as blocks across two hours), with the
 * programme the remote is on lit and a line at the time now. Up and Down go through the channels, Left and Right
 * through the programmes, the hours sliding along as they go.
 */
final class GuideGrid extends View {
    interface Host {
        /** The remote moved to another programme or channel. */
        void moved();

        /** Left from the programme on now: back to the categories. */
        void leftEdge();
    }

    static final long HALF = 30 * 60_000L, WINDOW = 4 * HALF;
    // Where things go (design pixels).
    private static final float X = 545f, RIGHT = 1880f, CHAN_W = 220f, RULER_Y = 352f, ROWS_Y = 412f, ROW_H = 92f, BOTTOM = 1066f;
    private static final float PROG_X = X + CHAN_W;

    private final Library lib;
    private Host host;
    private List<Channel> channels = new ArrayList<>();
    private int focus;
    private long cursor, winStart;
    private float topRow, topRowTo;
    private boolean active = true;
    private long last;
    /** Shown when there are no channels. */
    String empty = "";

    private final Glow glow = new Glow();
    private final Paint cell = Ui.fill(Ui.CELL), cellNow = Ui.fill(Ui.CELL_NOW), nowLine = Ui.fill(0xE6FFFFFF);
    private final Paint rowLit = Ui.fill(0x1AFFFFFF), bar = Ui.fill(Ui.CYAN), track = Ui.fill(0x40FFFFFF);
    private final Paint arrow = Ui.stroke(Ui.FAINT, 2f), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final TextPaint titleP = Ui.paint(46, false, Ui.WHITE), lineP = Ui.paint(28, false, Ui.DIM);
    private final TextPaint descP = Ui.paint(28, false, Ui.DIM), rulerDay = Ui.paint(28, true, Ui.WHITE);
    private final TextPaint rulerP = Ui.paint(26, false, Ui.DIM), numP = Ui.paint(26, false, Ui.DIM), numOn = Ui.paint(26, true, Ui.WHITE);
    private final TextPaint progP = Ui.paint(27, false, Ui.WHITE), progOn = Ui.paint(27, true, Ui.WHITE);
    private final TextPaint nameP = Ui.paint(24, false, Ui.DIM), noneP = Ui.paint(26, false, Ui.FAINT), emptyP = Ui.paint(32, false, Ui.DIM);
    private final RectF r = new RectF();
    private final Path tri = new Path();
    private final Runnable redraw = this::invalidate;

    // The details at the top, made again only when the programme changes.
    private String shownKey = "";
    private StaticLayout descL;

    GuideGrid(Context c, Library lib) {
        super(c);
        this.lib = lib;
        long now = System.currentTimeMillis();
        cursor = now;
        winStart = now - now % HALF;
    }

    void setHost(Host h) {
        host = h;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    /** Another category's channels; the remote goes to the one playing if it is there, else the first. */
    void setChannels(List<Channel> list, Channel playing) {
        channels = list;
        focus = 0;
        if (playing != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).key.equals(playing.key)) {
                    focus = i;
                    break;
                }
            }
        }
        long now = System.currentTimeMillis();
        cursor = Math.max(cursor, now);
        if (cursor > winStart + WINDOW - 15 * 60_000L || cursor < winStart) winStart = now - now % HALF;
        settle(true);
        shownKey = "";
        invalidate();
    }

    /** The same channels again (the guide was read): the remote stays where it is. */
    void refresh() {
        shownKey = "";
        invalidate();
    }

    Channel channel() {
        return focus < channels.size() ? channels.get(focus) : null;
    }

    int count() {
        return channels.size();
    }

    /** To the channel with this number, if the category has it. */
    boolean jumpTo(int number) {
        for (int i = 0; i < channels.size(); i++) {
            if (lib.number(channels.get(i)) == number) {
                focus = i;
                settle(false);
                moved();
                return true;
            }
        }
        return false;
    }

    private void moved() {
        invalidate();
        if (host != null) host.moved();
    }

    private void settle(boolean now) {
        int rows = visibleRows();
        if (focus < topRowTo) topRowTo = focus;
        if (focus > topRowTo + rows - 1) topRowTo = focus - rows + 1;
        topRowTo = Math.max(0, Math.min(topRowTo, Math.max(0, channels.size() - rows)));
        if (now || Exec.inline) topRow = topRowTo;
    }

    private int visibleRows() {
        return (int) ((BOTTOM - ROWS_Y) / ROW_H);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        if (channels.isEmpty()) return false;
        long now = System.currentTimeMillis();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus > 0) {
                    focus--;
                    settle(false);
                    moved();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < channels.size() - 1) {
                    focus++;
                    settle(false);
                    moved();
                }
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                focus = Math.max(0, focus - visibleRows());
                topRowTo = Math.max(0, topRowTo - visibleRows());
                settle(false);
                moved();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                focus = Math.min(channels.size() - 1, focus + visibleRows());
                topRowTo = topRowTo + visibleRows();
                settle(false);
                moved();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                Guide.Schedule s = lib.schedule(channel().key);
                long next = -1;
                if (s != null) {
                    int i = s.at(cursor);
                    int n = i >= 0 ? i + 1 : s.after(cursor);
                    if (n >= 0 && n < s.start.length) next = s.start[n];
                }
                if (next < 0) next = cursor - cursor % HALF + HALF;
                if (next > now + 12 * 3600_000L) return true;
                cursor = next;
                while (cursor >= winStart + WINDOW - 20 * 60_000L) winStart += HALF;
                moved();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_LEFT: {
                Guide.Schedule s = lib.schedule(channel().key);
                int i = s == null ? -1 : s.at(cursor);
                boolean onNow = cursor <= now || (i >= 0 && s.start[i] <= now);
                if (onNow) {
                    if (host != null) host.leftEdge();
                    return true;
                }
                long prev;
                if (s != null && i > 0) prev = s.start[i - 1];
                else if (s != null && i < 0) {
                    int n = s.after(cursor);
                    int p = (n < 0 ? s.start.length : n) - 1;
                    prev = p >= 0 ? s.start[p] : cursor - HALF;
                } else prev = cursor - cursor % HALF - (cursor % HALF == 0 ? HALF : 0);
                cursor = Math.max(prev, now);
                while (cursor < winStart) winStart -= HALF;
                long floor = now - now % HALF;
                if (winStart < floor) winStart = floor;
                moved();
                return true;
            }
            default:
                return false;
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        long t = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, t - last);
        last = t;
        boolean moving = false;
        if (Math.abs(topRowTo - topRow) > 0.004f) {
            topRow += (topRowTo - topRow) * (Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 70.0));
            moving = true;
        } else {
            topRow = topRowTo;
            last = 0;
        }
        if (channels.isEmpty()) {
            if (!empty.isEmpty()) {
                StaticLayout l = Ui.layout(empty, emptyP, Ui.px(RIGHT - X - 100), 4);
                Ui.draw(c, l, Ui.f(X), Ui.f(160));
            }
            return;
        }
        drawDetails(c, now);
        drawRuler(c, now);
        c.save();
        c.clipRect(Ui.f(X - 20), Ui.f(ROWS_Y - 14), Ui.f(RIGHT + 20), Ui.f(BOTTOM + 14));
        int first = Math.max(0, (int) Math.floor(topRow));
        for (int i = first; i < channels.size(); i++) {
            float y = ROWS_Y + (i - topRow) * ROW_H;
            if (y > BOTTOM) break;
            drawRow(c, i, y, now);
        }
        c.restore();
        // The time now, down through the rows.
        if (now >= winStart && now < winStart + WINDOW) {
            float x = xAt(now);
            c.drawRect(x - Ui.f(1), Ui.f(ROWS_Y - 6), x + Ui.f(1), Ui.f(BOTTOM), nowLine);
            tri.reset();
            tri.moveTo(x - Ui.f(9), Ui.f(RULER_Y + 30));
            tri.lineTo(x + Ui.f(9), Ui.f(RULER_Y + 30));
            tri.lineTo(x, Ui.f(RULER_Y + 42));
            tri.close();
            c.drawPath(tri, nowLine);
        }
        if (moving) postInvalidateOnAnimation();
    }

    private float pxPerMs() {
        return Ui.f(RIGHT - PROG_X) / WINDOW;
    }

    private float xAt(long time) {
        return Ui.f(PROG_X) + (time - winStart) * pxPerMs();
    }

    private void drawDetails(Canvas c, long now) {
        Channel ch = channel();
        if (ch == null) return;
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(cursor);
        float x = Ui.f(X), w = Ui.f(RIGHT - X - 300);
        String title = i >= 0 ? s.title[i] : ch.name;
        c.drawText(Ui.fit(title, titleP, w), x, Ui.f(100), titleP);
        String line;
        if (i >= 0) line = Ui.span(s.start[i], s.stop[i]) + "  ·  " + Ui.day(s.start[i], now) + "  ·  " + lib.number(ch) + " " + ch.name;
        else line = lib.number(ch) + " " + ch.name + (s == null ? "  ·  No programme information" : "");
        c.drawText(Ui.fit(line, lineP, w), x, Ui.f(150), lineP);
        float dy = 178;
        if (i >= 0 && s.start[i] <= now && now < s.stop[i]) {
            float bw = Ui.f(300), p = (now - s.start[i]) / (float) Math.max(1, s.stop[i] - s.start[i]);
            c.drawRect(x, Ui.f(dy), x + bw, Ui.f(dy + 5), track);
            c.drawRect(x, Ui.f(dy), x + bw * p, Ui.f(dy + 5), bar);
            c.drawText(Ui.left(s.stop[i] - now), x + bw + Ui.f(16), Ui.f(dy + 11), lineP);
            dy += 30;
        }
        String key = ch.key + "|" + (i >= 0 ? s.start[i] : -1);
        if (!key.equals(shownKey)) {
            shownKey = key;
            String d = i >= 0 ? s.desc[i] : "";
            descL = d.isEmpty() ? null : Ui.layout(d, descP, Math.round(w), 3);
        }
        if (descL != null) Ui.draw(c, descL, x, Ui.f(dy + 8));
        // The channel's logo, large, at the right (on its black tile: Pics.tiles).
        Bitmap logo = Pics.tiles ? Pics.get(ch.logo, Ui.px(214), Ui.px(120), Pics.TILE, redraw)
                : Pics.get(ch.logo, Ui.px(250), Ui.px(120), Pics.FIT, redraw);
        if (logo != null) c.drawBitmap(logo, Ui.f(RIGHT) - logo.getWidth(), Ui.f(60) + (Ui.f(120) - logo.getHeight()) / 2f, bmp);
    }

    private void drawRuler(Canvas c, long now) {
        c.drawText(Ui.day(Math.max(winStart, now), now), Ui.f(X), Ui.f(RULER_Y + 30), rulerDay);
        for (long t = winStart; t < winStart + WINDOW; t += HALF) {
            float x = xAt(t);
            c.drawRect(x, Ui.f(RULER_Y + 8), x + Math.max(1f, Ui.f(1.5f)), Ui.f(RULER_Y + 38), track);
            c.drawText(Ui.clock(t), x + Ui.f(10), Ui.f(RULER_Y + 30), rulerP);
        }
    }

    private void drawRow(Canvas c, int index, float y, long now) {
        Channel ch = channels.get(index);
        boolean lit = index == focus;
        float top = Ui.f(y), bottom = Ui.f(y + ROW_H - 6);
        if (lit) c.drawRect(Ui.f(X), top, Ui.f(PROG_X - 8), bottom, rowLit);
        String num = String.valueOf(lib.number(ch));
        TextPaint np = lit ? numOn : numP;
        c.drawText(num, Ui.f(X + 64) - np.measureText(num), (top + bottom) / 2f + Ui.f(9), np);
        // On its black tile (Pics.tiles), a little wider and taller than the logo's own room was.
        Bitmap logo = Pics.tiles ? Pics.get(ch.logo, Ui.px(132), Ui.px(70), Pics.TILE, redraw)
                : Pics.get(ch.logo, Ui.px(128), Ui.px(58), Pics.FIT, redraw);
        if (logo != null) {
            c.drawBitmap(logo, Ui.f(X + 80) + (Ui.f(128) - logo.getWidth()) / 2f, (top + bottom - logo.getHeight()) / 2f, bmp);
        } else {
            c.drawText(Ui.fit(ch.name, nameP, Ui.f(CHAN_W - 90)), Ui.f(X + 80), (top + bottom) / 2f + Ui.f(8), nameP);
        }
        Guide.Schedule s = lib.schedule(ch.key);
        long winEnd = winStart + WINDOW;
        float gap = Ui.f(3);
        boolean drewFocus = false;
        if (s != null) {
            for (int i = 0; i < s.start.length; i++) {
                if (s.stop[i] <= winStart) continue;
                if (s.start[i] >= winEnd) break;
                float x0 = xAt(Math.max(s.start[i], winStart)) + gap, x1 = xAt(Math.min(s.stop[i], winEnd)) - gap;
                if (x1 - x0 < Ui.f(4)) continue;
                boolean on = s.start[i] <= now && now < s.stop[i];
                boolean focused = lit && s.start[i] <= cursor && cursor < s.stop[i];
                r.set(x0, top, x1, bottom);
                if (focused && active) {
                    glow.draw(c, r);
                    drewFocus = true;
                } else if (focused) {
                    glow.box(c, r);
                    drewFocus = true;
                } else {
                    c.drawRect(r, on ? cellNow : cell);
                }
                float tx = x0 + Ui.f(14);
                if (s.start[i] < winStart) {
                    Ui.chevron(c, x0 + Ui.f(12), (top + bottom) / 2f, Ui.f(9), Ui.LEFT, arrow);
                    tx += Ui.f(12);
                }
                TextPaint p = focused ? progOn : progP;
                float room = x1 - tx - Ui.f(10);
                if (room > Ui.f(24)) {
                    c.save();
                    c.clipRect(x0, top, x1, bottom);
                    c.drawText(Ui.fit(s.title[i], p, room), tx, (top + bottom) / 2f + Ui.f(9), p);
                    c.restore();
                }
            }
        }
        if (!drewFocus) {
            // No programme known at that time: one block across, still lit when the remote is on it.
            float x0 = Ui.f(PROG_X) + gap, x1 = Ui.f(RIGHT) - gap;
            if (s == null) {
                r.set(x0, top, x1, bottom);
                if (lit && active) glow.draw(c, r);
                else if (lit) glow.box(c, r);
                else c.drawRect(r, cell);
                c.drawText(Ui.fit(ch.name, noneP, x1 - x0 - Ui.f(28)), x0 + Ui.f(14), (top + bottom) / 2f + Ui.f(9), noneP);
            } else if (lit) {
                long a = cursor - cursor % HALF, b = a + HALF;
                r.set(xAt(Math.max(a, winStart)) + gap, top, xAt(Math.min(b, winEnd)) - gap, bottom);
                if (active) glow.draw(c, r);
                else glow.box(c, r);
            }
        }
    }
}
