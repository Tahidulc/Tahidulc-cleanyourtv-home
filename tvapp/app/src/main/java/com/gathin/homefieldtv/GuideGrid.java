package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Lineup;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Look;

import java.util.Collections;
import java.util.List;

/**
 * The TV guide's grid, as the family's TiviMate draws it (DEVICE.md §2.7), under the programme's details (GuideBand):
 * the date and the clock in the light accent at the left of the times, a label every half hour at 320 px the half hour
 * (10.67 px a minute, about 2 h 10 min across), a 2 px rule under them with a 10 px light-accent dot at the time now;
 * then a row a channel, 88 px apart: its number (Settings → Appearance), its logo on its rounded tile and its name (two
 * lines), a ▶ in the accent on the channel playing in the preview and a catch-up mark on a channel whose provider keeps
 * an archive; then its programmes in cells 82 high with 6 px between, neutral (white at 3.5 %), the part of a
 * programme already shown a little lighter, every cell of the remote's row lighter still (white at 20 %) and the cell
 * the remote is on in the accent at 37.5 %. Titles in Roboto Condensed, two lines with an ellipsis, 24 px in (a cell
 * that began before the window keeps its title at the window's left edge). A bell on a programme to come with a
 * reminder; the catch-up mark on a programme already shown that the archive keeps.
 *
 * <p>The remote: Up and Down move a row, the rows gliding (ROW_MS) so the remote's row stays third from the top; Left
 * and Right go to the programme before or after, the times gliding (PAGE_MS) so that the remote's programme starts at
 * the window's left; a programme that runs past the window's right edge pages an hour on, the remote staying on it. Both
 * glides (Glide) are stepped on the display's frame clock and turn smoothly when a held key sends them on again, never
 * starting over and never snapping. Left from the programme on now goes to the groups (Host.leftEdge). The channel keys
 * page the rows; the rewind and fast-forward keys page the times two hours, back into a channel's archive too
 * (Homefield's catch-up), and Back comes back to now (awayFromNow, toNow).
 *
 * <p>Nothing here goes through every channel: a row asks its channel's schedule only for the programmes in the window
 * (Guide.Schedule.from, by halves), and only the rows on screen are drawn. A frame makes nothing new: titles and names
 * are measured once and cut from their kept widths (TextFit), times and numbers are kept words (Words), pictures are
 * looked up without a new key (Pics).
 */
final class GuideGrid extends View {
    interface Host {
        /** The remote moved to another programme or channel. */
        void moved();

        /** Left from the first programme there is to go to: the groups. */
        void leftEdge();

        /** The channel playing in the preview (its ▶), or null. */
        Channel playing();
    }

    static final long HALF = 30 * 60_000L, HOUR = 60 * 60_000L, PAGE = 2 * HOUR;
    /** The rows' glide and the times' page, as measured on the family's TV (§5: 200–230 ms and 300–330 ms, easing out). */
    static final long ROW_MS = 215, PAGE_MS = 315;
    // Where things go (design pixels): DEVICE.md §2.7, the side panels closed.
    static final float COLUMN = 540f, RIGHT = 1920f, DATE_X = 32f, LABELS_Y = 447f, RULE_Y = 462f, ROWS_Y = 468f;
    /** 320 px a half hour. */
    static final float PX_PER_MIN = 320f / 30f;
    /** How long the window shows: (1920 - 540) / 10.67 px a minute. */
    static final long SHOWN_MS = (long) ((RIGHT - COLUMN) / PX_PER_MIN * 60_000L);
    /** The remote's row sits this many rows from the top where the list allows. */
    static final int ABOVE = 2;
    private static final float GAP = 6f, CELL_R = 6f, TEXT_IN = 24f, LINE = 33f, TILE_W = 90f, TILE_H = 60f;
    /** A cell's words: white at 75 % (#C7C9D1 on a cell); the remote's row and cell have TEXT1. */
    static final int CELL_TEXT = 0xBFFFFFFF;
    /** The part already shown of the cell the remote is on: white at 12.5 % over the focus (#366092 on the ground). */
    static final int FOCUS_AIRED = 0x20FFFFFF;
    /** How far ahead the guide goes (the guide is read 12 hours ahead). */
    private static final long AHEAD = 12 * HOUR;

    private final Library lib;
    private Host host;
    /** The list's channels, with where a channel or a number is in it (found at once, however many there are). */
    private Lineup lineup;
    private List<Channel> channels = Collections.emptyList();
    private int focus;
    /** The time the remote is on, and whether it follows the time now (the programmes on now, the window at its start). */
    private long cursor;
    private boolean live = true;
    private boolean active = true;
    /** The window's start: where it is going. Where it is drawn is that and the times' glide (pan, design pixels). */
    private long winStart;
    private final Glide pan = new Glide(PAGE_MS);
    /** The rows' scroll: the first row's top above ROWS_Y, in design pixels, gliding to where the remote's row sits. */
    private final Glide rows = new Glide(ROW_MS);
    /** Shown when there are no channels: an icon, a title and a line (EmptyState). */
    String emptyTitle = "", emptyLine = "";
    int emptyIcon = Dark.TV;
    /** The channel lifted by Move channel (its key), drawn raised (MoveMode); null: none. */
    String lifted;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dateP = Type.paint(Type.ROW, Type.MEDIUM, Palette.ACCENT_LIGHT), labelP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint nameP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT1), numP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint cellP = Type.paint(Type.ROW, Type.REGULAR, CELL_TEXT);
    /** Titles, names and numbers measured once, cut from their kept widths each frame. */
    private final TextFit cells, names, numbers;
    private final RectF r = new RectF(), area = new RectF();
    private final Runnable redraw = this::invalidate;
    /** The day the window is on when it isn't today, and its words ("Wed, 18 Nov"). */
    private long dayKey = Long.MIN_VALUE;
    private String dayText = "";

    GuideGrid(Context c, Library lib) {
        super(c);
        this.lib = lib;
        cellP.setTypeface(Type.condensed());
        cells = new TextFit(cellP, 640);
        names = new TextFit(nameP, 256);
        numbers = new TextFit(numP, 128);
        long now = System.currentTimeMillis();
        cursor = now;
        winStart = nowHalf(now);
    }

    void setHost(Host h) {
        host = h;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    // ---- what is on screen -----------------------------------------------------------------------------------------

    /** A row's height for Settings → Appearance → Rows in the guide: 88 as on the family's TV, 72 or 104. */
    static float pitch() {
        switch (Look.rows) {
            case Look.ROWS_MORE: return 72f;
            case Look.ROWS_FEWER: return 104f;
            default: return 88f;
        }
    }

    /** How many rows are on screen. */
    static int visible() {
        return (int) Math.ceil((1080f - ROWS_Y) / pitch());
    }

    /** Pure: the first row on screen for the remote's row: two rows over it where it can, the last row at the foot. */
    static int topFor(int focus, int count, int visible) {
        return Math.max(0, Math.min(focus - ABOVE, count - visible));
    }

    /** Pure: the start of the half hour t is in. */
    static long half(long t) {
        return t - Math.floorMod(t, HALF);
    }

    private static long nowHalf(long now) {
        return half(now);
    }

    /**
     * Another list's channels; the remote goes to focusOn if it is there, else to the first. The time stays where it
     * is (or follows the time now).
     */
    void setChannels(Lineup list, Channel focusOn) {
        lineup = list;
        channels = list == null ? Collections.<Channel>emptyList() : list.channels;
        focus = Math.max(0, list == null ? -1 : list.indexOf(focusOn));
        if (live) {
            long now = System.currentTimeMillis();
            cursor = now;
            setWindow(nowHalf(now), false);
        }
        settle(true);
        moved();
    }

    /** The same channels again (the guide was read): the remote stays where it is. */
    void refresh() {
        invalidate();
    }

    Channel channel() {
        return focus >= 0 && focus < channels.size() ? channels.get(focus) : null;
    }

    /** The channel n rows from the remote's (-1 above, 1 below), or null past either end. */
    Channel channelAt(int offset) {
        int i = focus + offset;
        return i >= 0 && i < channels.size() ? channels.get(i) : null;
    }

    int count() {
        return channels.size();
    }

    /** The programme the remote is on in its channel's schedule, or -1 (nothing in the guide then). */
    int programme() {
        Channel ch = channel();
        Guide.Schedule s = ch == null ? null : lib.schedule(ch.key);
        return s == null ? -1 : s.at(cursor);
    }

    /** The programme the remote is on as {start, end}, or null when there is none. */
    long[] times() {
        Channel ch = channel();
        Guide.Schedule s = ch == null ? null : lib.schedule(ch.key);
        int i = s == null ? -1 : s.at(cursor);
        return i < 0 ? null : new long[]{s.start[i], s.stop[i]};
    }

    /** The time the remote is on. */
    long cursor() {
        return cursor;
    }

    /** The remote has moved off the programmes on now (later, or back in the archive): Back comes back. */
    boolean awayFromNow() {
        return !live;
    }

    /** Back to the programmes on now. */
    void toNow() {
        long now = System.currentTimeMillis();
        cursor = now;
        live = true;
        setWindow(nowHalf(now), true);
        moved();
    }

    /** Each minute: following the time now, the window moves on with it at the half hour. */
    void tick() {
        if (live) {
            long now = System.currentTimeMillis();
            cursor = now;
            if (nowHalf(now) != winStart) setWindow(nowHalf(now), true);
            if (host != null) host.moved();
        }
        invalidate();
    }

    /** To the channel with this number, if the list has it (the first with it). */
    boolean jumpTo(int number) {
        int i = lineup == null ? -1 : lineup.indexOfNumber(number);
        if (i < 0) return false;
        toRow(i);
        moved();
        return true;
    }

    /**
     * Where the remote's cell is (screen pixels, before the guide is pushed right): its programme's cell in its row, or
     * the row's first cell's place when there is none.
     */
    RectF focusedCell(RectF out) {
        float y = rowTop(focus, rows.target());
        long[] sp = span(channel(), cursor);
        double ws = winStart;
        float x0 = Math.max(Ui.f(COLUMN), xAt(sp[0], ws) + Ui.f(GAP / 2f)), x1 = Math.min(Ui.f(RIGHT), xAt(sp[1], ws) - Ui.f(GAP / 2f));
        out.set(x0, Ui.f(y + GAP / 2f), Math.max(x0 + Ui.f(40), x1), Ui.f(y + pitch() - GAP / 2f));
        return out;
    }

    /** The remote's whole row (16 px in from the left, to the screen's edge), for the menus to leave undimmed. */
    RectF focusedRow(RectF out) {
        float y = rowTop(focus, rows.target());
        out.set(Ui.f(16f), Ui.f(y), Ui.f(RIGHT), Ui.f(y + pitch()));
        return out;
    }

    // ---- moving -----------------------------------------------------------------------------------------------------

    private void moved() {
        invalidate();
        if (host != null) host.moved();
    }

    private void toRow(int to) {
        if (to < 0 || to >= channels.size()) return;
        focus = to;
        if (live) cursor = System.currentTimeMillis();
        settle(false);
    }

    /** The rows glide so the remote's row is third from the top (at once: a new list, or in previews). */
    private void settle(boolean now) {
        float target = topFor(focus, channels.size(), visible()) * pitch();
        if (now) rows.jump(target);
        else rows.to(target);
        invalidate();
    }

    /** The window to start at ws, gliding there from where it is drawn now (or at once). */
    private void setWindow(long ws, boolean animate) {
        long was = winStart;
        winStart = ws;
        if (!animate || Exec.inline) {
            pan.jump(0f);
        } else if (ws != was) {
            // What is drawn stays where it is (the glide's place moves by the change), then glides to the new start.
            pan.shift((float) ((was - ws) / 60_000.0 * PX_PER_MIN));
            pan.to(0f);
        }
        invalidate();
    }

    /**
     * Pure: what the remote is on at t in a schedule: a programme's {start, end, its index}, or the gap there is between
     * two (index -1), as far as the schedule goes either way (a channel with no guide: one gap across every time).
     */
    static long[] span(Guide.Schedule s, long t) {
        long lo = NONE_FROM, hi = NONE_TO;
        if (s == null || s.start.length == 0) return new long[]{lo, hi, -1};
        int i = s.at(t);
        if (i >= 0) return new long[]{s.start[i], s.stop[i], i};
        int n = s.after(t);
        int p = (n < 0 ? s.start.length : n) - 1;
        long a = p >= 0 ? Math.min(t, s.stop[p]) : lo, b = n >= 0 ? s.start[n] : hi;
        return new long[]{a, b, -1};
    }

    private long[] span(Channel ch, long t) {
        return span(ch == null ? null : lib.schedule(ch.key), t);
    }

    /** The earliest the window may go back to on this channel: its archive's (catch-up), else the half hour now. */
    private long floorFor(Channel ch, long now) {
        long earliest = Catchup.earliest(ch, now);
        return earliest == Long.MAX_VALUE ? nowHalf(now) : Math.min(nowHalf(now), half(earliest));
    }

    boolean key(int code) {
        if (channels.isEmpty()) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (focus > 0) {
                    toRow(focus - 1);
                    moved();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus < channels.size() - 1) {
                    toRow(focus + 1);
                    moved();
                }
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                toRow(Math.max(0, focus - visible()));
                moved();
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                toRow(Math.min(channels.size() - 1, focus + visible()));
                moved();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                right();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                left();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                page(code == KeyEvent.KEYCODE_MEDIA_REWIND ? -1 : 1);
                return true;
            default:
                return false;
        }
    }

    /** The programme after this one, its start at the window's left; a long one pages the window an hour on instead. */
    private void right() {
        long now = System.currentTimeMillis();
        long[] sp = span(channel(), cursor);
        long next = sp[1];
        if (next >= winStart + SHOWN_MS) {
            // It runs on past the window: an hour on, the remote staying on it.
            long ws = winStart + HOUR;
            if (ws > now + AHEAD) return;
            live = false;
            cursor = Math.max(cursor, ws);
            setWindow(ws, true);
        } else {
            live = false;
            cursor = next;
            setWindow(Math.max(winFloor(now), half(next)), true);
        }
        moved();
    }

    /**
     * The programme before this one, its start at the window's left. One that began before the window pages it back an
     * hour; at the earliest the window goes (the half hour now, or a channel's archive once paged back into it), Left
     * goes on to the groups.
     */
    private void left() {
        long now = System.currentTimeMillis();
        Channel ch = channel();
        long[] sp = span(ch, cursor);
        long floor = winFloor(now);
        if (sp[0] < winStart) {
            if (winStart <= floor) {
                if (host != null) host.leftEdge();
                return;
            }
            long ws = Math.max(floor, winStart - HOUR);
            cursor = Math.max(ws, Math.min(cursor, ws + SHOWN_MS - 1));
            if (cursor > now && sp[0] <= now && sp[1] > now && ws == nowHalf(now)) {
                cursor = now;
                live = true;
            }
            setWindow(ws, true);
            moved();
            return;
        }
        long[] prev = span(ch, sp[0] - 1);
        boolean over = prev[1] <= now;
        // Only programmes on now or to come, unless the window has gone back into the archive (rewind) and keeps it.
        if (prev[0] == NONE_FROM || over && (winStart >= nowHalf(now) || prev[2] < 0 || !Catchup.has(ch, prev[0], now))) {
            if (host != null) host.leftEdge();
            return;
        }
        boolean onNow = prev[0] <= now && now < prev[1];
        cursor = onNow ? now : Math.max(prev[0], floor);
        live = onNow && Math.max(floor, half(prev[0])) <= nowHalf(now);
        setWindow(live ? nowHalf(now) : Math.max(floor, half(prev[0])), true);
        moved();
    }

    /** The earliest the window goes now: the half hour now, or back in the archive while it is already there. */
    private long winFloor(long now) {
        return winStart < nowHalf(now) ? floorFor(channel(), now) : nowHalf(now);
    }

    /** Rewind and fast-forward: the times two hours back (into the channel's archive) or on. */
    private void page(int dir) {
        long now = System.currentTimeMillis();
        long floor = floorFor(channel(), now);
        long ws = Math.max(floor, Math.min(half(now + AHEAD - HOUR), winStart + dir * PAGE));
        if (ws == winStart) return;
        long offset = Math.max(0, cursor - winStart);
        cursor = ws + Math.min(offset, SHOWN_MS - 1);
        live = ws == nowHalf(now);
        if (live) cursor = now;
        setWindow(ws, true);
        moved();
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    /** Whether the rows or the times are still gliding (the details wait for them to settle before reading ahead). */
    boolean gliding() {
        return rows.moving() || pan.moving();
    }

    /** A row's top (design pixels) with the rows scrolled by scroll. */
    private static float rowTop(int i, float scroll) {
        return ROWS_Y + i * pitch() - scroll;
    }

    private static float xAt(long time, double ws) {
        return Ui.f(COLUMN + (float) ((time - ws) / 60_000.0) * PX_PER_MIN);
    }

    @Override protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        // The frame's step of both glides (on the display's clock), then the window's start where it is drawn now.
        boolean moving = rows.step() | pan.step();
        double ws = winStart + pan.value() / PX_PER_MIN * 60_000.0;
        drawTimes(c, now, ws);
        if (channels.isEmpty()) {
            area.set(0, Ui.f(ROWS_Y), Ui.f(RIGHT), getHeight());
            if (!emptyTitle.isEmpty()) EmptyState.draw(c, area, emptyIcon, 120f, emptyTitle, emptyLine);
            if (moving) postInvalidateOnAnimation();
            return;
        }
        boolean bells = Reminders.any(getContext());
        Channel playing = host == null ? null : host.playing();
        c.save();
        c.clipRect(0, Ui.f(RULE_Y + 2), getWidth(), getHeight());
        float h = pitch(), scroll = rows.value();
        int first = Math.max(0, (int) Math.floor(scroll / h));
        for (int i = first; i < channels.size(); i++) {
            float y = rowTop(i, scroll);
            if (y > 1080f) break;
            if (y + h < ROWS_Y) continue;
            drawRow(c, i, y, h, now, ws, bells, playing);
        }
        // A row moved by hand (Move channel): raised, its edge in the accent, ▲▼ at its right (MoveMode).
        if (lifted != null && focus < channels.size() && channels.get(focus).key.equals(lifted)) {
            float y = rowTop(focus, scroll);
            r.set(Ui.f(16f), Ui.f(y + 1f), Ui.f(RIGHT - 4f), Ui.f(y + h - 1f));
            MoveMode.drawLifted(c, r, Ui.f(RIGHT - 44f), Ui.f(y + h / 2f), Palette.PANEL);
        }
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    /** The date and the clock, a label every half hour (fading out under the channels), the rule and the dot at now. */
    private void drawTimes(Canvas c, long now, double ws) {
        dateP.setColor(Palette.ACCENT_LIGHT);
        // The window on another day than today: its day (Homefield's), else the date and the clock as on the TV.
        long day = (long) ws + 60_000L;
        c.drawText(Words.sameDay(day, now) ? Words.dateClock(now) : dayOf(day), Ui.f(DATE_X), Ui.f(LABELS_Y), dateP);
        labelP.setColor(Palette.TEXT2);
        int a = labelP.getAlpha();
        for (long t = half((long) ws); ; t += HALF) {
            float x = xAt(t, ws);
            if (x > Ui.f(RIGHT)) break;
            float fade = (x - Ui.f(COLUMN - 70f)) / Ui.f(70f);
            if (fade <= 0f) continue;
            labelP.setAlpha(Math.round(a * Math.min(1f, fade)));
            c.drawText(Words.hm(t), x, Ui.f(LABELS_Y), labelP);
        }
        labelP.setAlpha(a);
        fill.setColor(Palette.GUIDE_DIVIDER);
        c.drawRect(Ui.f(DATE_X), Ui.f(RULE_Y), Ui.f(RIGHT), Ui.f(RULE_Y + 2f), fill);
        float nx = xAt(now, ws);
        if (nx >= Ui.f(COLUMN) && nx <= Ui.f(RIGHT)) {
            fill.setColor(Palette.ACCENT_LIGHT);
            c.drawCircle(nx, Ui.f(RULE_Y + 1f), Ui.f(5f), fill);
        }
    }

    /** "Wed, 18 Nov": a day as the date at the guide's left shows it (kept for the day the window is on). */
    private String dayOf(long t) {
        long k = Words.dayIndex(t);
        if (k != dayKey) {
            dayKey = k;
            String d = Ui.dateClock(t);
            int cut = d.indexOf(',', d.indexOf(',') + 1);
            dayText = cut > 0 ? d.substring(0, cut) : d;
        }
        return dayText;
    }

    private void drawRow(Canvas c, int index, float y, float h, long now, double ws, boolean bells, Channel playing) {
        Channel ch = channels.get(index);
        boolean here = index == focus;
        float top0 = Ui.f(y + GAP / 2f), bottom = Ui.f(y + h - GAP / 2f), cy = (top0 + bottom) / 2f;
        drawChannel(c, ch, here, cy, h, playing != null && playing.key.equals(ch.key));

        // Its programmes, from the first still on at the window's start (found by halves), clipped to the grid.
        c.save();
        c.clipRect(Ui.f(COLUMN), top0 - Ui.f(1), Ui.f(RIGHT), bottom + Ui.f(1));
        Guide.Schedule s = lib.schedule(ch.key);
        long we = (long) ws + SHOWN_MS + HALF;
        long kept = Catchup.earliest(ch, now);
        if (s == null || s.start.length == 0) {
            drawCell(c, NONE_FROM, NONE_TO, ws, top0, bottom, "No information", here, here && active, now, 0);
        } else {
            long from = (long) ws;
            int n = s.start.length, i = s.from(from);
            while (i < n && s.stop[i] <= from) i++;
            // Where the programme before ended (a gap from there to the next one is a cell of its own).
            long ended = i > 0 ? s.stop[i - 1] : NONE_FROM;
            for (; i < n; i++) {
                long a = s.start[i], b = s.stop[i];
                if (a >= we) break;
                if (a > ended) drawGap(c, ended, a, ws, top0, bottom, here, now);
                boolean focused = here && a <= cursor && cursor < b;
                int mark = 0;
                if (b <= now && a >= kept && a < now) mark = MARK_ARCHIVE;
                else if (bells && a > now && Reminders.has(getContext(), ch.key, a)) mark = MARK_BELL;
                drawCell(c, a, b, ws, top0, bottom, s.title[i], here, focused && active, now, mark);
                ended = Math.max(ended, b);
            }
            if (ended < we) drawGap(c, ended, NONE_TO, ws, top0, bottom, here, now);
        }
        c.restore();
    }

    /** A gap's open ends: before a schedule's first programme, after its last (span's). */
    private static final long NONE_FROM = Long.MIN_VALUE / 4, NONE_TO = Long.MAX_VALUE / 4;

    private void drawGap(Canvas c, long a, long b, double ws, float top0, float bottom, boolean here, long now) {
        boolean focused = here && a <= cursor && cursor < b;
        float w = xAt(b, ws) - xAt(a, ws);
        drawCell(c, a, b, ws, top0, bottom, w > Ui.f(240f) ? "No information" : "", here, focused && active, now, 0);
    }

    static final int MARK_BELL = 1, MARK_ARCHIVE = 2;

    /**
     * One programme's cell (or a gap's): neutral, the part already shown a little lighter, the remote's row lighter
     * still, the remote's cell in the focus; its title in up to two lines from 24 px in (from the window's left when it
     * began before it), and a bell or the catch-up mark at its right.
     */
    private void drawCell(Canvas c, long a, long b, double ws, float top0, float bottom, String title, boolean row, boolean focused,
                          long now, int mark) {
        float xa = xAt(a, ws), xb = xAt(b, ws);
        // Its left corners under the channels when it began before the window, so its edge there is straight; one
        // running on past the screen's edge is laid out whole and cut by the edge (its words too), as on the TV.
        float end = xb - Ui.f(GAP / 2f);
        float left = Math.max(xa + Ui.f(GAP / 2f), Ui.f(COLUMN - 40f)), right = Math.min(end, Ui.f(RIGHT + 40f));
        if (right - Math.max(left, Ui.f(COLUMN)) < Ui.f(2f)) return;
        xa = Math.max(xa, Ui.f(COLUMN - 40f));
        r.set(left, top0, right, bottom);
        float rad = Ui.f(CELL_R);
        fill.setColor(focused ? Palette.FOCUS : row ? Palette.CELL_ROW : Palette.CELL);
        c.drawRoundRect(r, rad, rad, fill);
        // The part already shown (Settings → Appearance → progress), from the cell's start to now.
        if (Look.progress && a < now) {
            float to = Math.min(right, xAt(now, ws));
            if (to > left) {
                c.save();
                c.clipRect(left, top0, to, bottom);
                fill.setColor(focused ? FOCUS_AIRED : Palette.CELL);
                c.drawRoundRect(r, rad, rad, fill);
                c.restore();
            }
        }
        float tx = Math.max(xa, Ui.f(COLUMN)) + Ui.f(TEXT_IN), room = Math.min(end, Ui.f(RIGHT * 2f)) - tx - Ui.f(12f);
        int ink = focused || row ? Palette.TEXT1 : CELL_TEXT;
        if (mark != 0 && room > Ui.f(70f)) {
            float mx = right - Ui.f(26f), my = (top0 + bottom) / 2f;
            int markInk = focused || row ? Palette.TEXT1 : Palette.TEXT2;
            if (mark == MARK_BELL) {
                fill.setColor(markInk);
                Dark.bell(c, mx, my, Ui.f(11f), fill);
            } else {
                RoundButton.glyph(c, Dark.HISTORY, mx, my, Ui.f(22f), markInk);
            }
            room -= Ui.f(34f);
        }
        if (title.isEmpty() || room < Ui.f(18f)) return;
        cellP.setColor(ink);
        float cy = (top0 + bottom) / 2f;
        cells.lines(c, title, tx, Type.centred(cellP, cy), Type.centred(cellP, cy - Ui.f(LINE / 2f)),
                Type.centred(cellP, cy + Ui.f(LINE / 2f)), room, pitch() >= 88f ? 2 : 1);
    }

    /** The channel: its number, its logo on its rounded tile, its name (two lines), and the ▶ or catch-up mark at its right. */
    private void drawChannel(Canvas c, Channel ch, boolean here, float cy, float h, boolean playing) {
        boolean numbered = Look.numbers;
        float tileH = Math.min(TILE_H, h - 28f), tileW = tileH * 1.5f;
        float tileX = numbered ? 112f : 32f, nameX = tileX + tileW + 20f;
        int lit = here && active ? Palette.ACCENT : Palette.TEXT1;
        if (numbered) {
            String n = Words.number(lib.number(ch));
            numP.setColor(here && active ? Palette.ACCENT : Palette.TEXT2);
            c.drawText(n, Ui.f(96f) - numbers.width(n), Type.centred(numP, cy), numP);
        }
        // The logo on its tile (Settings → Channel logos; the new logo or the channel's own choice: LogoPack), or the tile
        // with a TV on it while there is none.
        float lx = Ui.f(tileX), ly = cy - Ui.f(tileH) / 2f;
        String logoUrl = LogoPack.url(ch);
        Bitmap logo = logoUrl.isEmpty() ? null : Pics.get(logoUrl, Ui.px(tileW), Ui.px(tileH), Pics.logoMode(), redraw);
        if (logo != null) {
            c.drawBitmap(logo, lx + (Ui.f(tileW) - logo.getWidth()) / 2f, cy - logo.getHeight() / 2f, bmp);
        } else {
            r.set(lx, ly, lx + Ui.f(tileW), ly + Ui.f(tileH));
            fill.setColor(Pics.tiles ? 0xFF000000 : Palette.SELECTED);
            c.drawRoundRect(r, Ui.f(7f), Ui.f(7f), fill);
            if (logoUrl.isEmpty() || Pics.failed(logoUrl)) {
                RoundButton.glyph(c, Dark.TV, r.centerX(), r.centerY(), Ui.f(tileH * 0.5f), Palette.TEXT3);
            }
        }
        // ▶ on the channel playing, the catch-up mark on one whose provider keeps an archive (left of the ▶ when both).
        float markX = 508f, nameRight = 528f;
        if (playing) {
            RoundButton.glyph(c, Dark.PLAY, Ui.f(markX), cy, Ui.f(32f), Palette.ACCENT);
            nameRight = markX - 32f;
            markX -= 42f;
        }
        if (Catchup.source(ch) != null) {
            RoundButton.glyph(c, Dark.HISTORY, Ui.f(markX), cy, Ui.f(24f), Palette.TEXT2);
            nameRight = markX - 26f;
        }
        nameP.setColor(lit);
        names.lines(c, ChannelEdits.name(ch), Ui.f(nameX), Type.centred(nameP, cy), Type.centred(nameP, cy - Ui.f(LINE / 2f)),
                Type.centred(nameP, cy + Ui.f(LINE / 2f)), Ui.f(nameRight - nameX), h >= 88f ? 2 : 1);
    }

    // ---- what the tests look at --------------------------------------------------------------------------------------

    /** Titles, names and numbers measured so far, and found already measured (the frame's text cache). */
    int textMeasured() {
        return cells.misses + names.misses + numbers.misses;
    }

    int textKept() {
        return cells.hits + names.hits + numbers.hits;
    }

    /** Where the rows and the times are drawn now (design pixels of scroll, ms of the window's start). */
    float rowsShown() {
        return rows.value();
    }

    double windowShown() {
        return winStart + pan.value() / PX_PER_MIN * 60_000.0;
    }
}
