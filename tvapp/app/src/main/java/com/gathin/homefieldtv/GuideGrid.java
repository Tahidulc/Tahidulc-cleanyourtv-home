package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Reminders;

import java.util.ArrayList;
import java.util.List;

/**
 * The TV guide's grid, dark, drawn by one view beside the categories, under the programme's band (GuideBand): the times
 * along a ruler, then a row a channel, its number and logo and then its programmes as blocks across two hours, each in
 * its own deep colour (by its title, as the phone's guide has them) with its times over its title and the part already
 * shown shaded. The programme the remote is on is amber, the part of it already shown a lighter amber, and under its
 * channel a strip opens: its picture (Sky's, else the channel's logo on its tile), its title, times, what it is (Live,
 * Starts in 12 min, HD or 4K) and two lines of what it is about. The rows below move down to make room as the strip
 * closes under the row left; its writing fades and rises into place. Up and Down go through the channels, Left and
 * Right through the programmes, the hours sliding along as they go. On a channel whose provider keeps an archive
 * (catch-up), Left goes on back into the programmes already shown, each marked with a circle arrow, as far as the
 * archive goes; elsewhere Left from the programme on now goes to the categories.
 */
final class GuideGrid extends View {
    interface Host {
        /** The remote moved to another programme or channel. */
        void moved();

        /** Left from the programme on now: back to the categories. */
        void leftEdge();
    }

    static final long HALF = 30 * 60_000L, WINDOW = 4 * HALF;
    /** The programmes' own deep colours, picked by title. */
    // The programmes' colours (Dark.DEEP) and the rest come from the theme.
    static final int AIRED = 0x59000000;
    // Where things go (design pixels).
    static final float X = SidePanel.X + SidePanel.W + 36f, RIGHT = 1888f, RULER_Y = 302f, ROWS_Y = 328f;
    private static final float CHAN_W = 236f, PROG_X = X + CHAN_W, GAP = 6f, ROW_H = 80f, BOTTOM = 1064f;
    private static final float STRIP_H = 224f, PIC_W = 324f, PIC_H = 182f;

    private final Library lib;
    private Host host;
    private List<Channel> channels = new ArrayList<>();
    private int focus;
    private long cursor, winStart;
    private float topRow, topRowTo;
    private boolean active = true, settled;
    private long last;
    /** The strip under the channel the remote is on (0 shut, 1 open), and the one closing under the row it left. */
    private float open = 1f, closing;
    private int closingRow = -1;
    /** Shown when there are no channels. */
    String empty = "";

    private final Paint cell = Dark.fill(Dark.CARD), cellLit = Dark.fill(Dark.CARD_HI), block = Dark.fill(Dark.CARD);
    private final Paint aired = Dark.fill(AIRED), lit = Dark.fill(Dark.ACCENT), litAired = Dark.fill(Dark.ACCENT_SOFT);
    private final Paint ring = Dark.line(Dark.alpha(Dark.RING, 0xE6), 2.5f), nowLine = Dark.fill(0x8CFFFFFF), nowMark = Dark.fill(0xFFFFFFFF);
    private final Paint tick = Dark.fill(0x33FFFFFF), stripBack = Dark.fill(Dark.CARD), tileBack = Dark.fill(0xFF000000);
    private final Paint tagFill = Dark.fill(Dark.ACCENT), tagLine = Dark.line(0x80FFFFFF, 1.5f);
    private final Paint chevron = Dark.line(0xB3FFFFFF, 2f), chevronInk = Dark.line(0xB31E1606, 2f);
    private final Paint markLine = Dark.line(0xCCFFFFFF, 2f), markFill = Dark.fill(0xCCFFFFFF);
    private final Paint markInk = Dark.line(Dark.ACCENT_INK, 2f), markInkFill = Dark.fill(Dark.ACCENT_INK);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG), picPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint numP = Dark.text(36, 300, Dark.SOFT), numLit = Dark.text(36, 400, Dark.TEXT), nameP = Dark.text(22, 600, Dark.SOFT);
    private final TextPaint spanP = Dark.text(19, 500, 0xB3FFFFFF), titleP = Dark.text(25, 600, Dark.TEXT);
    private final TextPaint spanInk = Dark.text(19, 500, 0xB31E1606), titleInk = Dark.text(25, 700, Dark.ACCENT_INK);
    private final TextPaint dayP = Dark.text(26, 600, Dark.TEXT), rulerP = Dark.text(22, 500, Dark.MUTED);
    private final TextPaint stripTitle = Dark.text(34, 600, Dark.TEXT), stripSpan = Dark.text(26, 500, Dark.SOFT);
    private final TextPaint stripDesc = Dark.text(25, 400, Dark.SOFT), tagP = Dark.text(20, 600, Dark.SOFT);
    private final TextPaint tagInk = Dark.text(20, 700, Dark.ACCENT_INK), emptyP = Dark.text(32, 400, Dark.SOFT);
    private final TextPaint tileName = Dark.text(24, 700, Dark.TEXT);
    private final RectF r = new RectF();
    private final Path tri = new Path();
    private final Matrix picMatrix = new Matrix();
    private Bitmap shaderFor;
    private final Fade strip = new Fade();
    private final Runnable redraw = this::invalidate;
    private final Runnable rest = () -> {
        settled = true;
        invalidate();
    };
    // What the strip says, made again only when the programme changes.
    private String descKey = "";
    private StaticLayout descL;
    private StaticLayout emptyL;
    private String emptyFor = "";

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
        closingRow = -1;
        closing = 0f;
        open = Exec.inline ? 1f : 0f;
        settle(true);
        descKey = "";
        moved();
    }

    /** The same channels again (the guide was read): the remote stays where it is. */
    void refresh() {
        descKey = "";
        invalidate();
    }

    Channel channel() {
        return focus < channels.size() ? channels.get(focus) : null;
    }

    /** The programme the remote is on in its channel's schedule (-1 for none), and when the remote is. */
    int programme() {
        Channel ch = channel();
        Guide.Schedule s = ch == null ? null : lib.schedule(ch.key);
        return s == null ? -1 : s.at(cursor);
    }

    int count() {
        return channels.size();
    }

    /** The programme the remote is on as {start, end}, or null when there is none. */
    long[] times() {
        Channel ch = channel();
        Guide.Schedule s = ch == null ? null : lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(cursor);
        return i < 0 ? null : new long[]{s.start[i], s.stop[i]};
    }

    /** The remote went back before the programmes on now (catch-up). */
    boolean inPast() {
        long now = System.currentTimeMillis();
        return cursor < now - now % HALF;
    }

    /** Back to the time now (Back from the past). */
    void toNow() {
        long now = System.currentTimeMillis();
        cursor = now;
        winStart = now - now % HALF;
        moved();
    }

    /** To the channel with this number, if the category has it. */
    boolean jumpTo(int number) {
        for (int i = 0; i < channels.size(); i++) {
            if (lib.number(channels.get(i)) == number) {
                if (i != focus) toRow(i);
                else moved();
                return true;
            }
        }
        return false;
    }

    /** Pure: a programme's own deep colour, the same for the same title. */
    static int deep(String title) {
        return Dark.DEEP[Math.floorMod(title == null ? 0 : title.hashCode(), Dark.DEEP.length)];
    }

    private void moved() {
        invalidate();
        settled = Exec.inline;
        removeCallbacks(rest);
        if (!Exec.inline) postDelayed(rest, ProgrammePictures.REST_MS);
        if (host != null) host.moved();
    }

    /** To another channel: the strip closes under the row left and opens under this one. */
    private void toRow(int to) {
        if (to == focus || to < 0 || to >= channels.size()) return;
        if (!Exec.inline) {
            closingRow = focus;
            closing = open;
            open = 0f;
        }
        focus = to;
        settle(false);
        moved();
    }

    /** How many rows fit with the strip open. */
    private int fit() {
        return Math.max(1, (int) ((BOTTOM - ROWS_Y - STRIP_H) / ROW_H));
    }

    private void settle(boolean now) {
        int rows = fit();
        if (focus < topRowTo) topRowTo = focus;
        if (focus > topRowTo + rows - 1) topRowTo = focus - rows + 1;
        topRowTo = Math.max(0, Math.min(topRowTo, Math.max(0, channels.size() - rows)));
        if (now || Exec.inline) topRow = topRowTo;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(rest);
        super.onDetachedFromWindow();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        if (channels.isEmpty()) return false;
        long now = System.currentTimeMillis();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                toRow(focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                toRow(focus + 1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                topRowTo = Math.max(0, topRowTo - fit());
                toRow(Math.max(0, focus - fit()));
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                topRowTo = topRowTo + fit();
                toRow(Math.min(channels.size() - 1, focus + fit()));
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
                Channel ch = channel();
                Guide.Schedule s = lib.schedule(ch.key);
                int i = s == null ? -1 : s.at(cursor);
                if (Catchup.source(ch) != null) {
                    // Catch-up: back through the programmes the provider's archive keeps; past the oldest, the categories.
                    int n = s == null ? -1 : s.after(cursor);
                    int p = s == null ? -1 : i >= 0 ? i - 1 : (n < 0 ? s.start.length : n) - 1;
                    if (p < 0 || !Catchup.has(ch, s.start[p], now)) {
                        if (host != null) host.leftEdge();
                        return true;
                    }
                    cursor = s.start[p];
                } else {
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
                }
                while (cursor < winStart) winStart -= HALF;
                long floor = now - now % HALF;
                if (winStart < floor && cursor >= floor) winStart = floor;
                moved();
                return true;
            }
            default:
                return false;
        }
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    /** One frame's step of the scrolling and the strips; true while anything still moves. */
    private boolean step() {
        long t = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, t - last);
        last = t;
        boolean moving = false;
        float k = Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 70.0);
        if (Math.abs(topRowTo - topRow) > 0.004f) {
            topRow += (topRowTo - topRow) * k;
            moving = true;
        } else {
            topRow = topRowTo;
        }
        float ks = Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 55.0);
        if (open < 0.996f) {
            open += (1f - open) * ks;
            moving = true;
        } else {
            open = 1f;
        }
        if (closing > 0.004f) {
            closing -= closing * ks;
            moving = true;
        } else {
            closing = 0f;
            closingRow = -1;
        }
        if (!moving) last = 0;
        return moving;
    }

    private float rowY(int i) {
        float y = ROWS_Y + (i - topRow) * ROW_H;
        if (closingRow >= 0 && i > closingRow) y += closing * STRIP_H;
        if (i > focus) y += open * STRIP_H;
        return y;
    }

    @Override protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        boolean moving = step();
        if (channels.isEmpty()) {
            drawEmpty(c);
            return;
        }
        drawRuler(c, now);
        c.save();
        c.clipRect(Ui.f(X - 4), Ui.f(ROWS_Y - 4), Ui.f(RIGHT + 8), Ui.f(BOTTOM));
        int first = Math.max(0, (int) Math.floor(topRow) - 1);
        float stripY = -1f, closingY = -1f;
        for (int i = first; i < channels.size(); i++) {
            float y = rowY(i);
            if (y > BOTTOM) break;
            if (i == focus) stripY = y + ROW_H;
            if (i == closingRow) closingY = y + ROW_H;
            if (y + ROW_H < ROWS_Y) continue;
            drawRow(c, i, y, now);
        }
        // The time now, down through the rows (the strips go over it).
        boolean nowShown = now >= winStart && now < winStart + WINDOW;
        if (nowShown) {
            float x = xAt(now);
            c.drawRect(x - Ui.f(1), Ui.f(ROWS_Y - 4), x + Ui.f(1), Ui.f(BOTTOM), nowLine);
        }
        if (closingY >= 0f && closing > 0.01f) drawStripBack(c, closingY, closing * STRIP_H);
        if (stripY >= 0f && open > 0.01f) drawStrip(c, stripY, open * STRIP_H, now);
        c.restore();
        if (nowShown) {
            float x = xAt(now);
            tri.reset();
            tri.moveTo(x - Ui.f(9), Ui.f(ROWS_Y - 17));
            tri.lineTo(x + Ui.f(9), Ui.f(ROWS_Y - 17));
            tri.lineTo(x, Ui.f(ROWS_Y - 6));
            tri.close();
            c.drawPath(tri, nowMark);
        }
        if (moving || strip.busy()) postInvalidateOnAnimation();
    }

    private void drawEmpty(Canvas c) {
        if (empty.isEmpty()) return;
        if (emptyL == null || !emptyFor.equals(empty)) {
            emptyL = Ui.layout(empty, emptyP, Ui.px(RIGHT - X - 100), 4);
            emptyFor = empty;
        }
        Ui.draw(c, emptyL, Ui.f(X), Ui.f(160));
    }

    private float pxPerMs() {
        return Ui.f(RIGHT - PROG_X) / WINDOW;
    }

    private float xAt(long time) {
        return Ui.f(PROG_X) + (time - winStart) * pxPerMs();
    }

    private void drawRuler(Canvas c, long now) {
        // The day the hours start on (Yesterday, back in the archive).
        c.drawText(Ui.day(winStart < now - now % HALF ? winStart : Math.max(winStart, now), now), Ui.f(X), Ui.f(RULER_Y), dayP);
        for (long t = winStart; t < winStart + WINDOW; t += HALF) {
            float x = xAt(t);
            c.drawRect(x, Ui.f(RULER_Y - 20), x + Math.max(1f, Ui.f(1.5f)), Ui.f(RULER_Y + 4), tick);
            c.drawText(LiveText.hm(t), x + Ui.f(10), Ui.f(RULER_Y), rulerP);
        }
    }

    private void drawRow(Canvas c, int index, float y, long now) {
        Channel ch = channels.get(index);
        boolean here = index == focus;
        float top = Ui.f(y), bottom = Ui.f(y + ROW_H - GAP), mid = (top + bottom) / 2f;
        // The channel: its number and logo, on a cell lighter for the row the remote is on.
        r.set(Ui.f(X), top, Ui.f(PROG_X - GAP), bottom);
        c.drawRoundRect(r, Ui.f(10), Ui.f(10), here ? cellLit : cell);
        String num = String.valueOf(lib.number(ch));
        TextPaint np = here ? numLit : numP;
        c.drawText(num, Ui.f(X + 82) - np.measureText(num), mid + Ui.f(13), np);
        float lx = Ui.f(X + 96), lw = Ui.f(128);
        Bitmap logo = Pics.get(ch.logo, Ui.px(128), Ui.px(66), Pics.logoMode(), redraw);
        if (logo != null) c.drawBitmap(logo, lx + (lw - logo.getWidth()) / 2f, mid - logo.getHeight() / 2f, bmp);
        else c.drawText(Ui.fit(ch.name, nameP, Ui.f(CHAN_W - 110)), lx, mid + Ui.f(8), nameP);

        // Its programmes.
        Guide.Schedule s = lib.schedule(ch.key);
        long winEnd = winStart + WINDOW;
        float gap = Ui.f(GAP);
        if (s == null) {
            // No guide for this channel: one block across.
            drawBlock(c, Ui.f(PROG_X), top, Ui.f(RIGHT) - gap, bottom, "No programme information", ch.name, Dark.CARD, -1f, here, false,
                    false, false);
            return;
        }
        // Catch-up: how far back the provider's archive of this channel goes (MAX_VALUE: it keeps none).
        long kept = Catchup.earliest(ch, now);
        boolean reminders = Reminders.any(getContext());
        boolean drewFocus = false;
        for (int i = 0; i < s.start.length; i++) {
            if (s.stop[i] <= winStart) continue;
            if (s.start[i] >= winEnd) break;
            float x0 = xAt(Math.max(s.start[i], winStart)), x1 = xAt(Math.min(s.stop[i], winEnd)) - gap;
            if (x1 - x0 < Ui.f(6)) continue;
            boolean focused = here && s.start[i] <= cursor && cursor < s.stop[i];
            float airedTo = s.start[i] < now ? xAt(Math.min(now, s.stop[i])) : -1f;
            boolean archived = s.stop[i] <= now && s.start[i] >= kept;
            boolean bell = reminders && s.start[i] > now && Reminders.has(getContext(), ch.key, s.start[i]);
            drawBlock(c, x0, top, x1, bottom, LiveText.span(s.start[i], s.stop[i]), s.title[i], deep(s.title[i]), airedTo,
                    focused, s.start[i] < winStart, archived, bell);
            if (focused) drewFocus = true;
        }
        if (here && !drewFocus) {
            // Nothing in the guide at that time: the half hour the remote is on, lit.
            long a = cursor - cursor % HALF, b = a + HALF;
            drawBlock(c, xAt(Math.max(a, winStart)), top, xAt(Math.min(b, winEnd)) - gap, bottom, "", "", Dark.CARD, -1f, true, false,
                    false, false);
        }
    }

    /**
     * One programme's block: its colour (amber where the remote is), the part already shown, its times over its title,
     * and at its top right catch-up's mark for a programme that has been shown and is in the provider's archive, or a
     * bell for one to come with a reminder set.
     */
    private void drawBlock(Canvas c, float x0, float top, float x1, float bottom, String span, String title, int colour,
                           float airedTo, boolean focused, boolean cut, boolean archived, boolean bell) {
        r.set(x0, top, x1, bottom);
        float rad = Ui.f(8);
        boolean amber = focused && active;
        if (amber) {
            c.drawRoundRect(r, rad, rad, lit);
            if (airedTo > x0) {
                c.save();
                c.clipRect(x0, top, Math.min(airedTo, x1), bottom);
                c.drawRoundRect(r, rad, rad, litAired);
                c.restore();
            }
        } else {
            block.setColor(colour);
            c.drawRoundRect(r, rad, rad, block);
            if (airedTo > x0) {
                c.save();
                c.clipRect(x0, top, Math.min(airedTo, x1), bottom);
                c.drawRoundRect(r, rad, rad, aired);
                c.restore();
            }
            if (focused) Dark.ring(c, r, rad, ring);
        }
        float tx = x0 + Ui.f(14);
        if (cut) {
            Ui.chevron(c, x0 + Ui.f(11), (top + bottom) / 2f, Ui.f(8), Ui.LEFT, amber ? chevronInk : chevron);
            tx += Ui.f(12);
        }
        float room = x1 - tx - Ui.f(10);
        if (room < Ui.f(28)) return;
        boolean mark = (archived || bell) && x1 - x0 > Ui.f(64);
        if (mark && archived) Dark.replay(c, x1 - Ui.f(20), top + Ui.f(21), Ui.f(8), amber ? markInk : markLine, amber ? markInkFill : markFill);
        else if (mark) Dark.bell(c, x1 - Ui.f(20), top + Ui.f(21), Ui.f(10), amber ? markInkFill : markFill);
        c.save();
        c.clipRect(x0, top, x1, bottom);
        TextPaint sp = amber ? spanInk : spanP, tp = amber ? titleInk : titleP;
        if (!span.isEmpty()) c.drawText(Ui.fit(span, sp, mark ? room - Ui.f(30) : room), tx, top + Ui.f(29), sp);
        if (!title.isEmpty()) c.drawText(Ui.fit(title, tp, room), tx, top + Ui.f(span.isEmpty() ? 46 : 59), tp);
        c.restore();
    }

    /** The strip closing under the row the remote left: its ground only. */
    private void drawStripBack(Canvas c, float y, float h) {
        float top = Ui.f(y), bottom = Ui.f(y + h - GAP);
        if (bottom - top < 1f) return;
        r.set(Ui.f(PROG_X), top, Ui.f(RIGHT), bottom);
        c.drawRoundRect(r, Ui.f(12), Ui.f(12), stripBack);
    }

    /** The strip under the channel the remote is on, h of it open so far. */
    private void drawStrip(Canvas c, float y, float h, long now) {
        Channel ch = channel();
        if (ch == null) return;
        float top = Ui.f(y), shown = Ui.f(y + h - GAP);
        if (shown - top < 1f) return;
        c.save();
        c.clipRect(Ui.f(PROG_X), top, Ui.f(RIGHT), shown);
        r.set(Ui.f(PROG_X), top, Ui.f(RIGHT), Ui.f(y + STRIP_H - GAP));
        c.drawRoundRect(r, Ui.f(12), Ui.f(12), stripBack);
        Guide.Schedule s = lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(cursor);
        String key = i >= 0 ? ch.key + "|" + s.start[i] : ch.key + "|-";
        strip.focus(key);

        // The picture: Sky's, else the channel's logo on its tile.
        float px = Ui.f(PROG_X + 18), py = top + Ui.f(18), pw = Ui.f(PIC_W), ph = Ui.f(PIC_H);
        String url = i < 0 ? "" : settled ? ProgrammePictures.get(getContext(), ch, s, i, redraw) : ProgrammePictures.peek(ch, s, i);
        Bitmap pic = url == null || url.isEmpty() ? null : Pics.get(url, Ui.px(PIC_W), Ui.px(PIC_H), Pics.CROP, redraw);
        if (pic != null) drawPicture(c, pic, px, py, pw, ph);
        else drawLogoTile(c, ch, px, py, pw, ph);

        // Its words, fading and rising into place.
        int a = Math.round(255 * strip.text());
        float rise = strip.rise();
        stripTitle.setAlpha(a);
        stripSpan.setAlpha(a);
        stripDesc.setAlpha(a);
        tagP.setAlpha(a);
        tagInk.setAlpha(a);
        tagFill.setAlpha(a);
        tagLine.setAlpha(a / 2);
        float tx = px + pw + Ui.f(28), room = Ui.f(RIGHT - 24) - tx;
        String title = i >= 0 ? s.title[i] : ch.name;
        c.drawText(Ui.fit(title, stripTitle, room), tx, top + Ui.f(62) + rise, stripTitle);
        float ly = top + Ui.f(108) + rise, lx = tx;
        if (i >= 0) {
            String day = Ui.day(s.start[i], now);
            String sp = LiveText.span(s.start[i], s.stop[i]);
            if (!"Today".equals(day)) sp = day + ", " + sp;
            c.drawText(sp, lx, ly, stripSpan);
            lx += stripSpan.measureText(sp) + Ui.f(20);
            boolean archived = Catchup.has(ch, s.start[i], now);
            if (s.start[i] <= now && now < s.stop[i]) {
                lx = tag(c, "Live", lx, ly, true);
                // Its start is in the archive: held OK watches it from there.
                if (archived && now - s.start[i] >= LiveTv.LIVE_EDGE_MS) lx = tag(c, "From the start", lx, ly, false);
            } else if (s.stop[i] <= now) {
                // Shown already: in the archive, OK plays it.
                if (archived) lx = tag(c, "Catch-up", lx, ly, true);
            } else {
                lx = tag(c, LiveText.startsIn(now, s.start[i]), lx, ly, false);
                if (Reminders.has(getContext(), ch.key, s.start[i])) lx = tag(c, "Reminder set", lx, ly, true);
            }
        } else {
            String none = s == null ? "No programme information" : "Nothing in the guide at this time";
            c.drawText(none, lx, ly, stripSpan);
            lx += stripSpan.measureText(none) + Ui.f(20);
        }
        tag(c, LiveText.quality(ch.name), lx, ly, false);
        if (i >= 0 && !s.desc[i].isEmpty()) {
            if (!key.equals(descKey) || descL == null) {
                descL = Ui.layout(s.desc[i], stripDesc, Math.round(room), 2);
                descKey = key;
            }
            Ui.draw(c, descL, tx, top + Ui.f(128) + rise);
        }
        stripTitle.setAlpha(255);
        stripSpan.setAlpha(255);
        stripDesc.setAlpha(255);
        tagP.setAlpha(255);
        tagInk.setAlpha(255);
        tagFill.setAlpha(255);
        tagLine.setAlpha(0x80);
        c.restore();
    }

    /** A tag after the times (Live in amber; Starts in 12 min, HD, 4K outlined); "" draws nothing. */
    private float tag(Canvas c, String t, float x, float base, boolean live) {
        if (t == null || t.isEmpty()) return x;
        float w = (live ? tagInk : tagP).measureText(t) + Ui.f(24), hgt = Ui.f(34);
        r.set(x, base - Ui.f(25), x + w, base - Ui.f(25) + hgt);
        if (live) {
            c.drawRoundRect(r, hgt / 2f, hgt / 2f, tagFill);
            c.drawText(t, x + Ui.f(12), base - Ui.f(1), tagInk);
        } else {
            c.drawRoundRect(r, hgt / 2f, hgt / 2f, tagLine);
            c.drawText(t, x + Ui.f(12), base - Ui.f(1), tagP);
        }
        return x + w + Ui.f(12);
    }

    /** A programme's picture with rounded corners (a shader made once a picture). */
    private void drawPicture(Canvas c, Bitmap pic, float x, float y, float w, float h) {
        if (pic != shaderFor) {
            picPaint.setShader(new BitmapShader(pic, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            shaderFor = pic;
        }
        picMatrix.setTranslate(x, y);
        picPaint.getShader().setLocalMatrix(picMatrix);
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, Ui.f(10), Ui.f(10), picPaint);
    }

    /** No programme picture: the channel's logo on its black tile (or its name). */
    private void drawLogoTile(Canvas c, Channel ch, float x, float y, float w, float h) {
        Bitmap t = Pics.tiles ? Pics.get(ch.logo, Ui.px(PIC_W), Ui.px(PIC_H), Pics.TILE, redraw) : null;
        if (t != null) {
            c.drawBitmap(t, x, y, bmp);
            return;
        }
        r.set(x, y, x + w, y + h);
        c.drawRoundRect(r, Ui.f(10), Ui.f(10), tileBack);
        Bitmap f = Pics.tiles ? null : Pics.get(ch.logo, Math.round(w * 0.7f), Math.round(h * 0.6f), Pics.FIT, redraw);
        if (f != null) {
            c.drawBitmap(f, x + (w - f.getWidth()) / 2f, y + (h - f.getHeight()) / 2f, bmp);
        } else {
            String n = Ui.fit(ch.name, tileName, w - Ui.f(24));
            c.drawText(n, x + (w - tileName.measureText(n)) / 2f, y + h / 2f + Ui.f(8), tileName);
        }
    }
}
