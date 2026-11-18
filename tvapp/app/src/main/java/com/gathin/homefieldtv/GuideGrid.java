package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The TV guide's grid in the 07 look (the approved stills 05c and 06c, the family's own guide restyled), under the
 * programme's details (GuideBand): the date in Homefield's amber at the left of the times, the times every half hour
 * with a small tick under each (300 px a half hour from x 512, about 2 h 15 min across), and the now line, a short amber
 * stroke under its glowing dot; then a row a channel, 92 px apart:
 *
 * <pre>
 *   the channel's cell    426 x 84 from x 74, rounded 20, filled with its logo's colour (ChannelLook: the shared rule,
 *                         a touch richer than the logo's own tile): its number (22 px), its logo on its tile (108 x 64),
 *                         its name (22 px, white) and under it its marks: ▶ in red on the channel playing in the
 *                         preview, its HD chip, the catch-up mark on a channel whose provider keeps an archive, the
 *                         amber star on a favourite. The remote's lifts (1.035) with a white ring and a soft glow
 *   its programmes        cells 84 high with 8 px between, rounded 16, deep blue-grey: the title (21 px, semibold, one
 *                         line) and under it the times (16 px) with the minutes left for the one on now, a bell on one
 *                         with a reminder, the catch-up mark on one the archive keeps; the programme's own picture
 *                         fitted round its faces (PicTile: at its 16:9 shape on the right of a cell 330 px wide and up,
 *                         a small thumbnail on one 200 to 330, none on a narrower one), never under a scrim; the one on
 *                         now with a white line along its foot to the time now; one already shown dimmer
 *   the remote's row      a soft light band behind it; its cell lifts (1.04) with a white ring, a deep shadow and a glow
 * </pre>
 *
 * <p>The remote: Up and Down move a row, the rows gliding (ROW_MS) so the remote's row stays second from the top; Left
 * and Right go to the programme before or after, the times gliding (PAGE_MS) so that the remote's programme starts at
 * the window's left; a programme that runs past the window's right edge pages an hour on, the remote staying on it. Both
 * glides (Glide) are stepped on the display's frame clock and turn smoothly when a held key sends them on again, never
 * starting over and never snapping. Left from the programme on now goes to the groups (Host.leftEdge), Up from the first
 * row to the tab bar (Host.topEdge). The channel keys page the rows; the rewind and fast-forward keys page the times two
 * hours, back into a channel's archive too (Homefield's catch-up), and Back comes back to now (awayFromNow, toNow).
 *
 * <p>Nothing here goes through every channel: a row asks its channel's schedule only for the programmes in the window
 * (Guide.Schedule.from, by halves), and only the rows on screen are drawn. A frame makes nothing new: titles and names
 * are measured once and cut from their kept widths (TextFit), times and numbers are kept words (Words), the cells'
 * second lines are made once each (Lines), the programmes' pictures' addresses are looked up once a cell (Art) and the
 * pictures themselves without a new key (Pics). Pictures are only asked for once the remote rests (askPictures).
 */
final class GuideGrid extends View {
    interface Host {
        /** The remote moved to another programme or channel. */
        void moved();

        /** Left from the first programme there is to go to: the groups. */
        void leftEdge();

        /** Up from the first row: the tab bar. */
        void topEdge();

        /** The channel playing in the preview (its ▶), or null. */
        Channel playing();
    }

    static final long HALF = 30 * 60_000L, HOUR = 60 * 60_000L, PAGE = 2 * HOUR;
    /** The rows' glide and the times' page, as measured on the family's TV (§5: 200–230 ms and 300–330 ms, easing out). */
    static final long ROW_MS = 215, PAGE_MS = 315;
    // Where things go (design pixels): the 07 stills, the side panels closed.
    static final float COLUMN = 512f, RIGHT = 1860f, DATE_X = 90f, LABELS_Y = 507f, TICK_Y = 518f, NOW_Y = 514f, ROWS_Y = 532f;
    /** 300 px a half hour. */
    static final float PX_PER_MIN = 10f;
    /** How long the window shows: (1860 - 512) / 10 px a minute. */
    static final long SHOWN_MS = (long) ((RIGHT - COLUMN) / PX_PER_MIN * 60_000L);
    /** The remote's row sits this many rows from the top where the list allows. */
    static final int ABOVE = 1;
    /** A row's gap under its cells; the cells' corners; the words' inset; the band behind the remote's row. */
    static final float GAP = 8f, CELL_R = 16f, TEXT_IN = 18f, BAND_X = 74f, BAND_R = 24f;
    /** The channel's cell and what is in it (the stills' .ep-ch). */
    static final float CH_X = 74f, CH_W = 426f, NUM_X = 90f, TILE_X = 158f, TILE_W = 108f, TILE_H = 64f, NAME_X = 282f, NAME_R = 488f;
    /** The programmes' cells: their colour (under the picture), the lift of the remote's (1.04). */
    static final int CELL = 0xFF171C2B;
    static final float CELL_LIFT = 1.04f;
    /** The remote's cell's shadow (0 22 44 -10 black 80 %) and glow (0 0 40 white 18 %). */
    static final float[] SH_CELL = {22f, 44f, -10f, 204f}, GLOW_CELL = {0f, 40f, 0f, 46f};
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

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG), band = new Paint(Paint.ANTI_ALIAS_FLAG),
            nowLine = new Paint(Paint.ANTI_ALIAS_FLAG), nowGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint dateP = Type.paint(22f, Type.SEMIBOLD, Look07.AMBER), labelP = Type.clock(22f, Type.MEDIUM, Look07.L2);
    private final TextPaint nameP = Type.paint(22f, Type.SEMIBOLD, Look07.L1), numP = Type.clock(22f, Type.SEMIBOLD, 0xE0FFFFFF);
    private final TextPaint cellP = Type.paint(21f, Type.SEMIBOLD, Look07.L1), subP = Type.clock(16f, Type.MEDIUM, Look07.L2);
    private final TextPaint chipP = Type.paint(11f, Type.BOLD, Look07.L1);
    /** Titles, names, second lines and numbers measured once, cut from their kept widths each frame. */
    private final TextFit cells, names, numbers, subs;
    /** The programmes' pictures, a PicTile for each row on screen (so their shaders stay kept: Shaders' 32 each). */
    private final PicTile[] tiles = new PicTile[8];
    private final Art art = new Art();
    private final Lines lines = new Lines();
    /** The channels' HD chips, worked out once a channel (the most recent 512). */
    private final Map<Channel, String> quality = new LinkedHashMap<Channel, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Channel, String> e) {
            return size() > 512;
        }
    };
    /** Whether each channel is a favourite (its star), asked once a channel and kept until they change. */
    private final Map<Channel, Boolean> favourites = new LinkedHashMap<Channel, Boolean>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Channel, Boolean> e) {
            return size() > 512;
        }
    };
    /** The lifts: the remote's channel cell and its programme's cell (07's springy ease). */
    private final Lift07 channelLift = new Lift07();
    private final CellLift cellLift = new CellLift();
    private final RectF r = new RectF(), area = new RectF(), o = new RectF();
    private final Runnable redraw = this::invalidate;
    /** A picture's address became known (Sky answered): the cells look again. */
    private final Runnable artKnown = () -> {
        art.generation++;
        invalidate();
    };
    /** The day the window is on when it isn't today, and its words ("Wed, 18 Nov"). */
    private long dayKey = Long.MIN_VALUE;
    private String dayText = "";

    GuideGrid(Context c, Library lib) {
        super(c);
        this.lib = lib;
        cells = new TextFit(cellP, 640);
        names = new TextFit(nameP, 256);
        numbers = new TextFit(numP, 128);
        subs = new TextFit(subP, 512);
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new PicTile(this);
            tiles[i].colour(CELL);
        }
        ring.setStyle(Paint.Style.STROKE);
        Shadows.prepare(SH_CELL, CELL_R);
        Shadows.prepare(GLOW_CELL, CELL_R);
        Shadows.prepare(Look07.SH_CHANNEL, Look07.R_CHANNEL);
        Shadows.prepare(Look07.SH_CHANNEL_UP, Look07.R_CHANNEL);
        Shadows.prepare(Look07.GLOW_CHANNEL, Look07.R_CHANNEL);
        // The now line: amber fading down its 14 px; its dot's glow (0 0 12 amber 80 %). Placed by translating.
        nowLine.setShader(new LinearGradient(0, 0, 0, Ui.f(14f), 0xF2FFB547, 0x0DFFB547, Shader.TileMode.CLAMP));
        nowGlow.setShader(new RadialGradient(0, 0, Ui.f(19f), new int[]{0xCCFFB547, 0x40FFB547, 0x00FFB547}, new float[]{0.3f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
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

    /** A row's pitch for Settings → Appearance → Rows in the guide: 92 as the stills, 76 or 108. */
    static float pitch() {
        switch (Look.rows) {
            case Look.ROWS_MORE: return 76f;
            case Look.ROWS_FEWER: return 108f;
            default: return 92f;
        }
    }

    /** A row's cells' height (its pitch less the gap under them). */
    static float cellH() {
        return pitch() - GAP;
    }

    /** How many rows are on screen. */
    static int visible() {
        return (int) Math.ceil((1080f - ROWS_Y) / pitch());
    }

    /** Pure: the first row on screen for the remote's row: one row over it where it can, the last row at the foot. */
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
        art.clear();
        favourites.clear();
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
        art.clear();
        favourites.clear();
        invalidate();
    }

    /** A channel was made a favourite or taken off them: the stars looked at again. */
    void favouritesChanged() {
        favourites.clear();
        invalidate();
    }

    private boolean favourite(Channel ch) {
        Boolean b = favourites.get(ch);
        if (b == null) {
            b = lib.isFavourite(ch);
            favourites.put(ch, b);
        }
        return b;
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
        float x0 = Math.max(Ui.f(COLUMN + GAP / 2f), xAt(sp[0], ws) + Ui.f(GAP / 2f)), x1 = Math.min(Ui.f(RIGHT - GAP / 2f), xAt(sp[1], ws) - Ui.f(GAP / 2f));
        out.set(x0, Ui.f(y), Math.max(x0 + Ui.f(40), x1), Ui.f(y + cellH()));
        return out;
    }

    /** The remote's whole row (its channel's cell to the window's end, with its lift), for the menus to leave undimmed. */
    RectF focusedRow(RectF out) {
        float y = rowTop(focus, rows.target());
        out.set(Ui.f(CH_X - 10f), Ui.f(y - 5f), Ui.f(RIGHT), Ui.f(y + cellH() + 5f));
        return out;
    }

    // ---- the pictures --------------------------------------------------------------------------------------------------

    /**
     * The remote has rested: the pictures of the programmes in the window on the rows on screen asked for (Sky's guide,
     * through ProgrammePictures; each channel's day is one answer there), so the cells fill in. Nothing while moving.
     */
    void askPictures() {
        if (channels.isEmpty() || Exec.inline) return;
        long from = winStart, to = winStart + SHOWN_MS;
        int first = Math.max(0, (int) Math.floor(rows.target() / pitch()));
        for (int k = first; k < channels.size() && k < first + visible() + 1; k++) {
            Channel ch = channels.get(k);
            Guide.Schedule s = lib.schedule(ch.key);
            if (s == null || s.start.length == 0) continue;
            int n = s.start.length, i = s.from(from);
            while (i < n && s.stop[i] <= from) i++;
            for (; i < n && s.start[i] < to; i++) {
                // Only cells wide enough to show a picture.
                long a = Math.max(s.start[i], from), b = Math.min(s.stop[i], to);
                if ((b - a) / 60_000f * PX_PER_MIN < PicTile.THUMB_W) continue;
                ProgrammePictures.get(getContext(), ch, s, i, artKnown);
            }
        }
        invalidate();
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

    /** The rows glide so the remote's row is second from the top (at once: a new list, or in previews). */
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
                } else if (host != null) {
                    host.topEdge();
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
            area.set(0, Ui.f(ROWS_Y), Ui.f(1920f), getHeight());
            if (!emptyTitle.isEmpty()) EmptyState.draw(c, area, emptyIcon, 120f, emptyTitle, emptyLine);
            if (moving) postInvalidateOnAnimation();
            return;
        }
        // The lifts follow the remote (none while it is in the groups or a row is being moved).
        Channel here = channel();
        if (here != null) spanInto(here, cursor);
        boolean lifting = active && lifted == null && here != null;
        moving |= channelLift.step(lifting ? here : null);
        moving |= cellLift.step(focus, lifting ? focusSpan[0] : Long.MIN_VALUE);
        boolean bells = Reminders.any(getContext());
        Channel playing = host == null ? null : host.playing();
        c.save();
        c.clipRect(0, Ui.f(ROWS_Y - 6f), getWidth(), getHeight());
        float h = pitch(), scroll = rows.value();
        int first = Math.max(0, (int) Math.floor(scroll / h));
        int last = first;
        for (int i = first; i < channels.size(); i++) {
            float y = rowTop(i, scroll);
            if (y > 1080f) break;
            last = i;
            // The remote's row last, over its neighbours (its cells lift past them).
            if (i == focus || y + h < ROWS_Y) continue;
            drawRow(c, i, y, now, ws, bells, playing, false);
        }
        if (focus >= first && focus <= last) drawRow(c, focus, rowTop(focus, scroll), now, ws, bells, playing, true);
        // A row moved by hand (Move channel): raised, its edge in the accent, ▲▼ at its right (MoveMode).
        if (lifted != null && focus < channels.size() && channels.get(focus).key.equals(lifted)) {
            float y = rowTop(focus, scroll);
            r.set(Ui.f(CH_X), Ui.f(y), Ui.f(RIGHT), Ui.f(y + cellH()));
            MoveMode.drawLifted(c, r, Ui.f(RIGHT - 44f), Ui.f(y + cellH() / 2f), Look07.CARD_FRAME);
        }
        c.restore();
        if (moving) postInvalidateOnAnimation();
    }

    /** The remote's programme (or gap) as {start, end}, worked out into a kept array (nothing made a frame). */
    private final long[] focusSpan = new long[2];

    private void spanInto(Channel ch, long t) {
        Guide.Schedule s = lib.schedule(ch.key);
        if (s == null || s.start.length == 0) {
            focusSpan[0] = NONE_FROM;
            focusSpan[1] = NONE_TO;
            return;
        }
        int i = s.at(t);
        if (i >= 0) {
            focusSpan[0] = s.start[i];
            focusSpan[1] = s.stop[i];
            return;
        }
        int n = s.after(t);
        int p = (n < 0 ? s.start.length : n) - 1;
        focusSpan[0] = p >= 0 ? Math.min(t, s.stop[p]) : NONE_FROM;
        focusSpan[1] = n >= 0 ? s.start[n] : NONE_TO;
    }

    /** The date in amber, a time every half hour with its tick (fading out under the channels), and the now line. */
    private void drawTimes(Canvas c, long now, double ws) {
        // The window on another day than today: its day (Homefield's), else the date and the clock as on the TV.
        long day = (long) ws + 60_000L;
        c.drawText(Words.sameDay(day, now) ? Words.dateClock(now) : dayOf(day), Ui.f(DATE_X), Ui.f(LABELS_Y), dateP);
        int a = 0xBD;
        fill.setColor(0x40FFFFFF);
        for (long t = half((long) ws); ; t += HALF) {
            float x = xAt(t, ws);
            if (x > Ui.f(RIGHT)) break;
            float fade = (x - Ui.f(COLUMN - 60f)) / Ui.f(60f);
            if (fade <= 0f) continue;
            float k = Math.min(1f, fade);
            labelP.setColor(Palette.alpha(0xFFEBEBF5, Math.round(a * k)));
            c.drawText(Words.hm(t), x + Ui.f(10f), Ui.f(LABELS_Y), labelP);
            fill.setAlpha(Math.round(0x40 * k));
            c.drawRoundRect(x, Ui.f(TICK_Y), x + Ui.f(2f), Ui.f(TICK_Y + 8f), Ui.f(1f), Ui.f(1f), fill);
        }
        float nx = xAt(now, ws);
        if (nx >= Ui.f(COLUMN) && nx <= Ui.f(RIGHT)) {
            c.save();
            c.translate(nx, Ui.f(NOW_Y));
            c.drawRect(-Ui.f(1f), 0, Ui.f(1f), Ui.f(14f), nowLine);
            c.translate(0, -Ui.f(1f));
            c.drawCircle(0, 0, Ui.f(19f), nowGlow);
            fill.setColor(Look07.AMBER);
            c.drawCircle(0, 0, Ui.f(7f), fill);
            c.restore();
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

    private void drawRow(Canvas c, int index, float y, long now, double ws, boolean bells, Channel playing, boolean here) {
        Channel ch = channels.get(index);
        float ch0 = cellH();
        float top = Ui.f(y), bottom = Ui.f(y + ch0);
        if (here && active) {
            // The soft band behind the remote's row.
            r.set(Ui.f(BAND_X), top - Ui.f(3f), Ui.f(RIGHT), bottom + Ui.f(3f));
            band.setColor(0x12FFFFFF);
            c.drawRoundRect(r, Ui.f(BAND_R), Ui.f(BAND_R), band);
            ring.setStrokeWidth(Math.max(1f, Ui.f(1f)));
            ring.setColor(0x14FFFFFF);
            c.drawRoundRect(r, Ui.f(BAND_R), Ui.f(BAND_R), ring);
        }
        drawChannel(c, ch, top, bottom, channelLift.of(ch), playing != null && playing.key.equals(ch.key));

        // Its programmes, from the first still on at the window's start (found by halves), clipped to the grid.
        c.save();
        c.clipRect(Ui.f(COLUMN - 4f), top - Ui.f(14f), Ui.f(1920f), bottom + Ui.f(14f));
        Guide.Schedule s = lib.schedule(ch.key);
        long we = (long) ws + SHOWN_MS + HALF;
        long kept = Catchup.earliest(ch, now);
        PicTile tile = tiles[index & 7];
        int liftAt = -2;
        if (s == null || s.start.length == 0) {
            drawCell(c, tile, ch, null, -1, NONE_FROM, NONE_TO, ws, top, bottom, "No information", index, now, 0);
        } else {
            long from = (long) ws;
            int n = s.start.length, i = s.from(from);
            while (i < n && s.stop[i] <= from) i++;
            // Where the programme before ended (a gap from there to the next one is a cell of its own).
            long ended = i > 0 ? s.stop[i - 1] : NONE_FROM;
            for (; i < n; i++) {
                long a = s.start[i], b = s.stop[i];
                if (a >= we) break;
                if (a > ended) drawGap(c, tile, ch, ended, a, ws, top, bottom, index, now);
                // The remote's cell after the others (lifted over them).
                if (here && a <= cursor && cursor < b) {
                    liftAt = i;
                } else {
                    drawCell(c, tile, ch, s, i, a, b, ws, top, bottom, s.title[i], index, now, mark(ch, s, i, now, kept, bells));
                }
                ended = Math.max(ended, b);
            }
            if (ended < we) drawGap(c, tile, ch, ended, NONE_TO, ws, top, bottom, index, now);
            if (liftAt >= 0) drawCell(c, tile, ch, s, liftAt, s.start[liftAt], s.stop[liftAt], ws, top, bottom, s.title[liftAt], index, now,
                    mark(ch, s, liftAt, now, kept, bells));
        }
        c.restore();
    }

    private int mark(Channel ch, Guide.Schedule s, int i, long now, long kept, boolean bells) {
        long a = s.start[i], b = s.stop[i];
        if (b <= now && a >= kept && a < now) return MARK_ARCHIVE;
        if (a <= now && now < b && kept != Long.MAX_VALUE) return MARK_ARCHIVE;
        if (bells && a > now && Reminders.has(getContext(), ch.key, a)) return MARK_BELL;
        return 0;
    }

    /** A gap's open ends: before a schedule's first programme, after its last (span's). */
    private static final long NONE_FROM = Long.MIN_VALUE / 4, NONE_TO = Long.MAX_VALUE / 4;

    private void drawGap(Canvas c, PicTile tile, Channel ch, long a, long b, double ws, float top, float bottom, int index, long now) {
        float w = xAt(b, ws) - xAt(a, ws);
        drawCell(c, tile, ch, null, -1, a, b, ws, top, bottom, w > Ui.f(240f) ? "No information" : "", index, now, 0);
    }

    static final int MARK_BELL = 1, MARK_ARCHIVE = 2;

    /**
     * One programme's cell (or a gap's): its picture fitted round its faces where it is wide enough (PicTile), its title
     * and under it its times, the minutes left and its mark; the one on now with its line to the time now; the remote's
     * lifted with its ring.
     */
    private void drawCell(Canvas c, PicTile tile, Channel ch, Guide.Schedule s, int i, long a, long b, double ws, float top, float bottom,
                          String title, int index, long now, int mark) {
        float xa = xAt(a, ws), xb = xAt(b, ws);
        float left = Math.max(xa + Ui.f(GAP / 2f), Ui.f(COLUMN + GAP / 2f)), right = Math.min(xb - Ui.f(GAP / 2f), Ui.f(RIGHT - GAP / 2f));
        if (right - left < Ui.f(2f)) return;
        boolean here = index == focus && a <= cursor && cursor < b;
        float up = here ? cellLift.of(index, a) : 0f;
        boolean done = b <= now;
        float rad = Ui.f(CELL_R), h = bottom - top;
        r.set(left, top, right, bottom);
        boolean scaled = up > 0.001f;
        if (scaled) {
            float k = 1f + (CELL_LIFT - 1f) * up;
            c.save();
            c.scale(k, k, r.centerX(), r.centerY());
            float u = Math.min(1f, up);
            Shadows.draw(c, r, rad, SH_CELL, u);
            Shadows.glow(c, r, rad, GLOW_CELL, u);
        }
        // The picture (where the cell takes one), else the cell plain.
        String url = s == null ? null : art.url(ch, s, i);
        float end = url == null || url.isEmpty() ? -1f : tile.draw(c, r, rad, url, PicTile.WORDS_LEFT, here, done);
        if (end < 0f) {
            fill.setColor(CELL);
            c.drawRoundRect(r, rad, rad, fill);
            float sw = Math.max(1f, Ui.f(1f));
            ring.setStrokeWidth(sw);
            ring.setColor(0x12FFFFFF);
            c.drawRoundRect(left + sw / 2f, top + sw / 2f, right - sw / 2f, bottom - sw / 2f, rad, rad, ring);
            end = right;
        }
        // How far through the one on now is: a white line along its foot, to the time now (no shading over it).
        if (Look.progress && a <= now && now < b) {
            float to = Math.min(right - Ui.f(6f), xAt(now, ws));
            if (to > left + Ui.f(8f)) {
                fill.setColor(Look07.L1);
                c.drawRoundRect(left + Ui.f(6f), bottom - Ui.f(4f), to, bottom, Ui.f(2f), Ui.f(2f), fill);
            }
        }
        // Its words: the title, then its times (the minutes left on now) and its mark.
        float tx = left + Ui.f(TEXT_IN), room = Math.min(end, right - Ui.f(14f)) - tx;
        if (!title.isEmpty() && room > Ui.f(18f)) {
            boolean two = h >= Ui.f(76f) && s != null;
            cellP.setColor(done && !here ? Look07.L2 : Look07.L1);
            float base = two ? top + h * 0.393f : Type.centred(cellP, top + h / 2f);
            cells.line(c, title, tx, base, room);
            if (two && room > Ui.f(40f)) {
                String line = lines.of(a, b, a <= now && now < b ? (int) Math.max(1L, (b - now + 59_999L) / 60_000L) : -1);
                float sb = top + h * 0.738f, cy = sb - Ui.f(6f);
                float iconRoom = mark != 0 ? Ui.f(24f) : 0f;
                float w = subs.line(c, line, tx, sb, Math.max(0f, room - iconRoom));
                if (mark != 0 && w + Ui.f(24f) <= room) {
                    float mx = tx + w + Ui.f(8f + 8f);
                    if (mark == MARK_BELL) Icons07.draw(c, Icons07.BELL, mx, cy, Ui.f(16f), Look07.AMBER);
                    else Icons07.draw(c, Icons07.CATCHUP, mx, cy, Ui.f(16f), Look07.L2);
                }
            }
        }
        if (scaled) {
            // 07's focus: a 3 px white ring just outside the cell.
            float rw = Ui.f(3f), out = rw / 2f;
            ring.setStrokeWidth(rw);
            ring.setColor(Palette.alpha(Look07.L1, Math.round(255 * Math.min(1f, up))));
            c.drawRoundRect(left - out, top - out, right + out, bottom + out, rad + out, rad + out, ring);
            c.restore();
        }
    }

    /**
     * The channel's cell in its logo's colour (ChannelLook), lifted where the remote is: its number, its logo on its tile,
     * its name and under it its marks (▶ playing, its HD chip, catch-up, the favourite's star).
     */
    private void drawChannel(Canvas c, Channel ch, float top, float bottom, float up, boolean playing) {
        float h = bottom - top;
        r.set(Ui.f(CH_X), top, Ui.f(CH_X + CH_W), bottom);
        int words = ChannelLook.fillCell(c, r, Ui.f(Look07.R_CHANNEL), ch, up);
        boolean ink = words == Look07.INK;
        float k = 1f + (Look07.CHANNEL_LIFT - 1f) * Math.max(0f, Math.min(1f, up));
        boolean scaled = k > 1.0005f && ChannelLook.colour(ch) != 0;
        float cy = (top + bottom) / 2f;
        if (scaled) {
            c.save();
            c.scale(k, k, r.centerX(), r.centerY());
        }
        if (Look.numbers) {
            String n = Words.number(lib.number(ch));
            numP.setColor(ink ? Look07.INK2 : 0xE0FFFFFF);
            c.drawText(n, Ui.f(NUM_X), Type.centred(numP, cy), numP);
        }
        // The logo on its tile (Settings › Appearance › Logos › Behind logos), or the empty tile with a TV while there is none.
        float th = Math.min(TILE_H, h / Ui.s - 20f), tw = th * TILE_W / TILE_H;
        float tileX = Look.numbers ? TILE_X : NUM_X;
        String logoUrl = LogoPack.url(ch);
        o.set(Ui.f(tileX), cy - Ui.f(th) / 2f, Ui.f(tileX + tw), cy + Ui.f(th) / 2f);
        if (!Logos.draw(c, logoUrl, o, redraw) && (logoUrl.isEmpty() || Pics.failed(logoUrl))) {
            Icons07.draw(c, Icons07.TV, o.centerX(), o.centerY(), Ui.f(th * 0.5f), ink ? Look07.INK3 : Look07.L3);
        }
        // The name (two lines when it needs them), its marks under it.
        float nameX = tileX + tw + 16f, room = Ui.f(NAME_R - nameX);
        nameP.setColor(ink ? Look07.INK : Look07.L1);
        String name = ChannelEdits.name(ch);
        boolean fav = favourite(ch), archive = Catchup.source(ch) != null;
        String q = qualityOf(ch);
        boolean marks = playing || fav || archive || !q.isEmpty();
        float marksY;
        if (h >= Ui.f(76f) && marks) {
            boolean one = names.fitsOne(name, room);
            float lineH = Ui.f(26f);
            if (one) {
                names.line(c, name, Ui.f(nameX), top + h * 0.44f, room);
                marksY = top + h * 0.73f;
            } else {
                names.lines(c, name, Ui.f(nameX), top + h * 0.30f, top + h * 0.30f, top + h * 0.30f + lineH, room, 2);
                marksY = top + h * 0.84f;
            }
            drawMarks(c, Ui.f(nameX), marksY, playing, q, archive, fav, ink);
        } else {
            names.lines(c, name, Ui.f(nameX), Type.centred(nameP, cy), Type.centred(nameP, cy - Ui.f(13f)), Type.centred(nameP, cy + Ui.f(13f)),
                    room, h >= Ui.f(76f) ? 2 : 1);
        }
        if (scaled) c.restore();
    }

    /** A channel's marks along cy from x: ▶ in red (playing), its HD chip, the catch-up mark, the amber star. */
    private void drawMarks(Canvas c, float x, float cy, boolean playing, String q, boolean archive, boolean fav, boolean ink) {
        float gap = Ui.f(8f);
        if (playing) {
            Icons07.draw(c, Icons07.PLAY, x + Ui.f(6f), cy, Ui.f(12f), Look07.RED);
            x += Ui.f(12f) + gap;
        }
        if (!q.isEmpty()) {
            float w = chipP.measureText(q) + Ui.f(10f), hh = Ui.f(20f), sw = Math.max(1f, Ui.f(1.5f));
            ring.setStrokeWidth(sw);
            ring.setColor(ink ? 0x66000000 : 0x99FFFFFF);
            c.drawRoundRect(x + sw / 2f, cy - hh / 2f + sw / 2f, x + w - sw / 2f, cy + hh / 2f - sw / 2f, Ui.f(5f), Ui.f(5f), ring);
            chipP.setColor(ink ? Look07.INK2 : Look07.L1);
            c.drawText(q, x + Ui.f(5f), Type.centred(chipP, cy), chipP);
            x += w + gap;
        }
        if (archive) {
            Icons07.draw(c, Icons07.CATCHUP, x + Ui.f(7.5f), cy, Ui.f(15f), ink ? Look07.INK2 : 0xD1FFFFFF);
            x += Ui.f(15f) + gap;
        }
        if (fav) Icons07.draw(c, Icons07.STAR, x + Ui.f(7.5f), cy, Ui.f(15f), Look07.AMBER);
    }

    /** "HD", "4K" or "" as the channel's name says (worked out once a channel). */
    private String qualityOf(Channel ch) {
        String q = quality.get(ch);
        if (q == null) {
            q = LiveText.quality(ch.name);
            quality.put(ch, q);
        }
        return q;
    }

    // ---- kept words and pictures --------------------------------------------------------------------------------------

    /** The cells' second lines ("21:00 – 22:00 · 40 min left"), made once each and kept (nothing made a frame). */
    static final class Lines {
        private static final int N = 512;
        private final long[] ka = new long[N], kb = new long[N];
        private final int[] km = new int[N];
        private final String[] text = new String[N];
        private int madeFor = Integer.MIN_VALUE;

        /** left: the minutes left for one on now, -1 for any other. */
        String of(long a, long b, int left) {
            int settings = Look.times24 ? 1 : 0;
            if (settings != madeFor) {
                Arrays.fill(text, null);
                madeFor = settings;
            }
            long x = a * 0x9E3779B97F4A7C15L ^ b * 31L ^ left;
            int h = (int) (x ^ (x >>> 29)) & (N - 1), slot = h;
            for (int k = 0; k < 8; k++) {
                int j = (h + k) & (N - 1);
                if (text[j] == null) {
                    slot = j;
                    break;
                }
                if (ka[j] == a && kb[j] == b && km[j] == left) return text[j];
            }
            String s = a <= NONE_FROM || b >= NONE_TO ? "" : LiveText.hm(a) + " – " + LiveText.hm(b);
            if (left >= 0) s += " · " + leftWords(left);
            ka[slot] = a;
            kb[slot] = b;
            km[slot] = left;
            text[slot] = s;
            return s;
        }
    }

    /** Pure: "40 min left", "1h 10m left" (as the stills write what is left of a programme). */
    static String leftWords(int minutes) {
        int m = Math.max(1, minutes);
        if (m < 60) return m + " min left";
        int mm = m % 60;
        return m / 60 + "h " + (mm < 10 ? "0" : "") + mm + "m left";
    }

    /**
     * The programmes' pictures' addresses for the cells on screen, by schedule and programme, looked up once a cell
     * (ProgrammePictures.peek) and kept; one not known yet is looked at again only when a picture became known.
     */
    static final class Art {
        private static final int N = 1024;
        private final Guide.Schedule[] sched = new Guide.Schedule[N];
        private final int[] index = new int[N], gen = new int[N];
        private final String[] url = new String[N];
        int generation;

        String url(Channel ch, Guide.Schedule s, int i) {
            int h = (System.identityHashCode(s) * 31 + i) & (N - 1), slot = h;
            for (int k = 0; k < 8; k++) {
                int j = (h + k) & (N - 1);
                if (sched[j] == null) {
                    slot = j;
                    break;
                }
                if (sched[j] == s && index[j] == i) {
                    if (url[j] == null && gen[j] != generation) {
                        url[j] = ProgrammePictures.peek(ch, s, i);
                        gen[j] = generation;
                    }
                    return url[j];
                }
            }
            sched[slot] = s;
            index[slot] = i;
            url[slot] = ProgrammePictures.peek(ch, s, i);
            gen[slot] = generation;
            return url[slot];
        }

        void clear() {
            Arrays.fill(sched, null);
            Arrays.fill(url, null);
        }
    }

    /** Lift07 for a cell known by its row and its start, as Lift07 does it (nothing made a frame). */
    private static final class CellLift {
        private int onRow = -1, wasRow = -1;
        private long onAt = Long.MIN_VALUE, wasAt = Long.MIN_VALUE;
        private Glide up = spring(), down = spring();

        private static Glide spring() {
            Glide g = new Glide(Look07.LIFT_MS, 100f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK);
            g.jump(0f);
            return g;
        }

        /** The frame's step with the remote's cell (at: Long.MIN_VALUE for none); true while either still moves. */
        boolean step(int row, long at) {
            if (row != onRow || at != onAt) {
                Glide t = down;
                down = up;
                up = t;
                if (!(at != Long.MIN_VALUE && row == wasRow && at == wasAt)) up.jump(0f);
                wasRow = onRow;
                wasAt = onAt;
                onRow = row;
                onAt = at;
                up.to(at == Long.MIN_VALUE ? 0f : 1f);
                down.to(0f);
            }
            return up.step() | down.step();
        }

        float of(int row, long at) {
            if (row == onRow && at == onAt && onAt != Long.MIN_VALUE) return up.value();
            if (row == wasRow && at == wasAt) return down.value();
            return 0f;
        }
    }

    // ---- what the tests look at --------------------------------------------------------------------------------------

    /** Titles, names and numbers measured so far, and found already measured (the frame's text cache). */
    int textMeasured() {
        return cells.misses + names.misses + numbers.misses + subs.misses;
    }

    int textKept() {
        return cells.hits + names.hits + numbers.hits + subs.hits;
    }

    /** Where the rows and the times are drawn now (design pixels of scroll, ms of the window's start). */
    float rowsShown() {
        return rows.value();
    }

    double windowShown() {
        return winStart + pan.value() / PX_PER_MIN * 60_000.0;
    }
}
