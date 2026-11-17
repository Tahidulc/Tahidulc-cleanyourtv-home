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

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.Look;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Home's rows in the dark look, one under another, drawn by one view as Films' and TV shows' rows are (Shelves): the
 * row the remote is on sits under the big picture with the remote's tile lifted inside a white ring, the row sliding
 * along under it; the rows above fade away under the picture and the next one peeks in below. Three kinds of tile: a
 * channel's, as the live screen draws them (a picture of what's on with the channel's logo, else the logo on its
 * black tile; the programme's title, a line about it and how far through it is in amber; the channel's number and
 * name and what's left under it), a wide one to carry on from (its picture, its title and where it was over the foot,
 * how far through), and a poster (PosterArt, as everywhere). Only what is in sight is drawn, so a catalogue of
 * thousands costs no more than a dozen.
 */
final class Rows extends View {
    static final int POSTER = 0, WIDE = 1, CHANNEL = 2;

    static final class Tile {
        final Object item;
        String picture = "", fallback = "", logo = "", title = "", subtitle = "";
        float progress = -1f;
        /** One of the family's own TorBox titles (no playlist has it): its poster carries TorBox's cloud (PosterArt). */
        boolean torbox;
        private PosterArt.Item art;
        // A channel's words fitted to its tile, for the programme starting at fitAt and the minute fitMinute; a wide
        // tile's for the width fitW.
        private String fitT, fitS, fitName, fitLeft;
        private long fitAt = Long.MIN_VALUE, fitMinute = -1;
        private float fitW = -1f;

        Tile(Object item) {
            this.item = item;
        }
    }

    static final class Row {
        final String title;
        final int kind;
        final List<Tile> tiles;
        int focus;
        float x;
        /** What each tile's lift is kept by: the same across new rows of the same title, so nothing grows again. */
        private Object[] keys;

        Row(String title, int kind, List<Tile> tiles) {
            this.title = title == null ? "" : title;
            this.kind = kind;
            this.tiles = tiles;
        }

        Object key(int i) {
            if (keys == null || keys.length <= i) keys = keys == null ? new Object[Math.max(8, i + 1)] : Arrays.copyOf(keys, Math.max(i + 1, keys.length * 2));
            if (keys[i] == null) keys[i] = new Object();
            return keys[i];
        }
    }

    interface Host {
        void focused(Row row, Tile tile);

        void chosen(Row row, Tile tile, boolean held);

        /** The remote went past the rows' edge (Ui.LEFT, UP, RIGHT, DOWN); true when the screen took it. */
        boolean edge(int dir);
    }

    /** A portrait poster's width, a channel's and a wide tile's size, the gap between them, the heading's room (design px). */
    static final float POSTER_W = Shelves.POSTER_W, TILE_W = 384f, TILE_H = 216f, GAP = 26f, HEAD = 58f, AFTER = 34f;
    /** The room under a channel's tile for its number and name. */
    private static final float NAME = 46f, RADIUS = 16f, LIFT = PosterArt.LIFT;

    private final List<Row> rows = new ArrayList<>();
    private int row;
    private boolean active = true;
    private Host host;
    /** The rows' left edge and where the row the remote is on starts, its heading (design pixels). */
    float left = Billboard.TEXT_X, top = 600f;
    private float scrollY;
    private long last;
    /** The remote has rested on the row: its channels' programme pictures are asked for (ProgrammePictures). */
    private boolean settled = Exec.inline;
    private final Runnable rest = () -> {
        settled = true;
        invalidate();
    };

    private final Lift lift = new Lift();
    private final PosterArt art;
    private final TextPaint headP = Type.paint(30f, Type.MEDIUM, Palette.TEXT1), countP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
    private final TextPaint tTitle = Dark.text(25, 600, Dark.TEXT), tLine = Dark.text(19, 400, Dark.SOFT);
    private final TextPaint nameP = Dark.text(21, 500, Dark.MUTED), nameOn = Dark.text(21, 600, Dark.SOFT);
    private final TextPaint leftP = Dark.text(19, 500, Dark.MUTED), logoName = Dark.text(28, 700, Dark.TEXT);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), black = Dark.fill(0xFF000000);
    private final Paint card = Dark.fill(Dark.CARD), track = Dark.fill(0x40FFFFFF), bar = Dark.fill(Dark.ACCENT);
    private final Paint ring = Dark.line(Palette.ACCENT_LIGHT, Focus.BORDER);
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;

    Rows(Context c) {
        super(c);
        art = new PosterArt(PosterArt.cardWidth(Ui.f(POSTER_W)));
    }

    void setHost(Host h) {
        host = h;
    }

    // ---- sizes (design pixels) ----------------------------------------------------------------------------------

    private float tileW(int kind) {
        return kind == POSTER ? art.w / Ui.s : TILE_W;
    }

    /** A tile and what is under it. */
    private float tileH(int kind) {
        return kind == POSTER ? art.tileH() / Ui.s : kind == CHANNEL ? TILE_H + NAME : TILE_H;
    }

    private float height(Row r) {
        return HEAD + tileH(r.kind) + AFTER;
    }

    // ---- what it shows ------------------------------------------------------------------------------------------

    /** New rows; the remote stays on the same row (by its title) and the same place along it. */
    void setRows(List<Row> list) {
        Map<String, Row> old = new HashMap<>();
        for (Row o : rows) old.put(o.title + "|" + o.kind, o);
        String was = row < rows.size() ? rows.get(row).title : null;
        boolean first = rows.isEmpty();
        rows.clear();
        int at = 0;
        for (int i = 0; i < list.size(); i++) {
            Row n = list.get(i);
            Row o = old.get(n.title + "|" + n.kind);
            if (o != null) {
                n.focus = Math.max(0, Math.min(o.focus, n.tiles.size() - 1));
                n.x = Math.max(0, Math.min(o.x, n.tiles.size() - 1));
                n.keys = o.keys;
            }
            if (n.title.equals(was)) at = i;
            rows.add(n);
        }
        row = rows.isEmpty() ? 0 : Math.min(at, rows.size() - 1);
        if (first) scrollY = targetY();
        invalidate();
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    /** A row by its title, or null. */
    Row find(String title) {
        for (Row r : rows) if (r.title.equals(title)) return r;
        return null;
    }

    Row row() {
        return row < rows.size() ? rows.get(row) : null;
    }

    Tile tile() {
        Row r = row();
        return r == null || r.tiles.isEmpty() ? null : r.tiles.get(Math.min(r.focus, r.tiles.size() - 1));
    }

    void setActive(boolean on) {
        active = on;
        moved();
    }

    private float targetY() {
        float y = 0;
        for (int i = 0; i < row && i < rows.size(); i++) y += Ui.f(height(rows.get(i)));
        return y;
    }

    /** The remote moved: the pictures wait for it to rest again. */
    private void moved() {
        removeCallbacks(rest);
        settled = Exec.inline;
        if (!Exec.inline) postDelayed(rest, ProgrammePictures.REST_MS);
        invalidate();
    }

    private void changed() {
        moved();
        if (host != null) host.focused(row(), tile());
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(rest);
        super.onDetachedFromWindow();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        Row cur = row();
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
                if (cur.focus < cur.tiles.size() - 1) {
                    cur.focus++;
                    changed();
                    return true;
                }
                if (host != null) host.edge(Ui.RIGHT);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) {
                    row--;
                    changed();
                    return true;
                }
                return host != null && host.edge(Ui.UP);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < rows.size() - 1) {
                    row++;
                    changed();
                    return true;
                }
                if (host != null) host.edge(Ui.DOWN);
                return true;
            default:
                return false;
        }
    }

    void ok(boolean held) {
        Tile t = tile();
        if (t != null && host != null) host.chosen(row(), t, held);
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

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
        for (Row x : rows) {
            if (Math.abs(x.focus - x.x) > 0.002f) {
                x.x += (x.focus - x.x) * k;
                moving = true;
            } else {
                x.x = x.focus;
            }
        }
        if (!moving) last = 0;
        return moving;
    }

    @Override protected void onDraw(Canvas c) {
        boolean moving = step();
        Row on = row();
        moving |= lift.step(active && on != null && !on.tiles.isEmpty() ? on.key(Math.min(on.focus, on.tiles.size() - 1)) : null);
        float topPx = Ui.f(top);
        float y = topPx - scrollY;
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.size(); i++) {
            Row x = rows.get(i);
            float h = Ui.f(height(x));
            if (y > getHeight()) break;
            if (y + h > 0) {
                float alpha = 1f;
                if (y < topPx - 1) alpha = Math.max(0f, 1f - (topPx - y) / (h * 0.5f));
                if (alpha > 0.01f) drawRow(c, x, y, (int) (alpha * 255), i == row, now);
            }
            y += h;
        }
        if (moving) postInvalidateOnAnimation();
    }

    private void drawRow(Canvas c, Row x, float y, int a, boolean focusedRow, long now) {
        float x0 = Ui.f(left);
        if (!x.title.isEmpty()) {
            // The row's name, and how far along it the remote is at the right ("3 / 25").
            String count = x.tiles.isEmpty() ? "" : (Math.min(x.focus, x.tiles.size() - 1) + 1) + " / " + x.tiles.size();
            float cw = countP.measureText(count), right = getWidth() - Ui.f(72);
            headP.setColor(Palette.TEXT1);
            headP.setAlpha(Math.round(a * ((Palette.TEXT1 >>> 24) & 0xFF) / 255f));
            c.drawText(Ui.fit(x.title, headP, right - cw - Ui.f(40) - x0), x0, y + Ui.f(36), headP);
            countP.setColor(Palette.TEXT2);
            countP.setAlpha(Math.round(a * ((Palette.TEXT2 >>> 24) & 0xFF) / 255f));
            c.drawText(count, right - cw, y + Ui.f(36), countP);
        }
        int k = x.kind;
        float ty = y + Ui.f(HEAD), tw = Ui.f(tileW(k)), pitch = tw + Ui.f(GAP);
        int n = x.tiles.size();
        int from = Math.max(0, (int) Math.floor(x.x) - 1);
        int to = Math.min(n - 1, from + (int) Math.ceil((getWidth() - x0) / pitch) + 2);
        // The tile the remote is on grows into its lift and ring as the one it left settles back (both drawn last, over).
        int lifted = -1, settling = -1;
        Object leaving = lift.leaving();
        for (int i = from; i <= to; i++) {
            float tx = x0 + (i - x.x) * pitch;
            if (tx + tw < 0) continue;
            float l = x.keys == null ? 0f : lift.of(x.key(i));
            if (l > 0f) {
                if (x.key(i) == leaving) settling = i;
                else lifted = i;
                continue;
            }
            drawTile(c, x, x.tiles.get(i), tx, ty, 0f, a, focusedRow, now);
        }
        if (settling >= 0) drawTile(c, x, x.tiles.get(settling), x0 + (settling - x.x) * pitch, ty, lift.of(x.key(settling)), a, focusedRow, now);
        if (lifted >= 0) drawTile(c, x, x.tiles.get(lifted), x0 + (lifted - x.x) * pitch, ty, lift.of(x.key(lifted)), a, focusedRow, now);
        // The next posters along asked for early, so the row slides on to pictures.
        if (focusedRow && k == POSTER) for (int i = to + 1; i <= Math.min(n - 1, to + 4); i++) art.prefetch(art(x.tiles.get(i)), null);
    }

    private void drawTile(Canvas c, Row row, Tile t, float x, float y, float l, int a, boolean focusedRow, long now) {
        if (row.kind == POSTER) {
            PosterArt.Item it = art(t);
            if (it != null) art.draw(c, it, x, y, l, a, redraw);
            return;
        }
        float w = Ui.f(TILE_W), h = Ui.f(TILE_H), rad = Ui.f(RADIUS);
        c.save();
        if (l > 0f) {
            float s = 1f + (LIFT - 1f) * l;
            c.scale(s, s, x + w / 2f, y + h / 2f);
        }
        r.set(x, y, x + w, y + h);
        if (row.kind == CHANNEL) channel(c, t, x, y, w, h, rad, a, focusedRow && active && settled, now);
        else wide(c, t, x, y, w, h, rad, a);
        if (l > 0f) {
            ring.setAlpha(Math.round(a * 0.9f * l));
            Dark.ring(c, r, rad, ring);
        }
        c.restore();
        if (row.kind == CHANNEL) under(c, t, x, y + h + (LIFT - 1f) * h / 2f * l, w, l > 0.5f, a);
    }

    /** A poster's look: its genre (its page's when read, else its category's) and its wide picture where known. */
    private PosterArt.Item art(Tile t) {
        if (t.art == null && t.item instanceof Catalog.Title) {
            Catalog.Title title = (Catalog.Title) t.item;
            PosterArt.Item it = new PosterArt.Item(title);
            Catalog.Details d = Catalog.get(getContext()).cachedDetails(title);
            String g = d != null ? PosterArt.firstGenre(d.genre) : "";
            it.genre = g.isEmpty() ? PosterArt.genreOf(title.category) : g;
            if (d != null) it.backdrop = d.backdrop;
            t.art = it;
        }
        return t.art;
    }

    /** A channel's tile: what's on, as the live screen's tiles show it. */
    private void channel(Canvas c, Tile t, float x, float y, float w, float h, float rad, int a, boolean ask, long now) {
        Channel ch = t.item instanceof Channel ? (Channel) t.item : null;
        Guide.Schedule s = ch == null ? null : Library.get(getContext()).schedule(ch.key);
        int i = s == null ? -1 : s.at(now);
        // Settings → Appearance → Channel tiles: Channel logos leaves the programme's picture out.
        String url = i < 0 || Look.logos ? "" : ask ? ProgrammePictures.get(getContext(), ch, s, i, redraw) : ProgrammePictures.peek(ch, s, i);
        Bitmap pic = url == null || url.isEmpty() ? null : Pics.get(url, Math.round(w), Math.round(h), Pics.CARD, redraw);
        int logoMode = Pics.tiles ? Pics.READY : Pics.FIT;
        bmp.setAlpha(a);
        if (pic != null) {
            c.drawBitmap(pic, null, r, bmp);
            Bitmap logo = Pics.get(t.logo, Ui.px(130), Ui.px(48), logoMode, redraw);
            if (logo != null) c.drawBitmap(logo, x + Ui.f(16), y + Ui.f(14), bmp);
        } else {
            black.setAlpha(a);
            c.drawRoundRect(r, rad, rad, black);
            Bitmap logo = Pics.get(t.logo, Ui.px(200), Ui.px(70), logoMode, redraw);
            if (logo != null) {
                c.drawBitmap(logo, x + (w - logo.getWidth()) / 2f, y + Ui.f(72) - logo.getHeight() / 2f, bmp);
            } else if (ch != null) {
                logoName.setAlpha(a);
                String nm = Ui.fit(ch.name, logoName, w - Ui.f(32));
                c.drawText(nm, x + (w - logoName.measureText(nm)) / 2f, y + Ui.f(82), logoName);
            }
        }
        // What's on, a line about it (else its times), how far through; under the tile the channel and what's left.
        long at = i >= 0 ? s.start[i] : Long.MIN_VALUE + 1, minute = now / 60_000L;
        float room = w - Ui.f(32);
        if (t.fitAt != at || t.fitW != room) {
            String title = i >= 0 ? s.title[i] : t.title;
            String about = i < 0 ? (s == null ? "No programme information" : "Nothing in the guide now")
                    : s.desc[i].isEmpty() ? LiveText.span(s.start[i], s.stop[i]) : s.desc[i];
            t.fitT = Ui.fit(title, tTitle, room);
            t.fitS = Ui.fit(about, tLine, room);
            t.fitAt = at;
            t.fitW = room;
            t.fitMinute = -1;
        }
        if (t.fitMinute != minute && ch != null) {
            t.fitLeft = i >= 0 ? LiveText.left(now, s.stop[i]) : "";
            float lw = t.fitLeft.isEmpty() ? 0f : leftP.measureText(t.fitLeft) + Ui.f(16);
            t.fitName = Ui.fit((Look.numbers ? Library.get(getContext()).number(ch) + "  " : "") + ch.name, nameOn, w - lw);
            t.fitMinute = minute;
        }
        tTitle.setAlpha(a);
        tLine.setAlpha(a);
        c.drawText(t.fitT, x + Ui.f(16), y + h - Ui.f(46), tTitle);
        c.drawText(t.fitS, x + Ui.f(16), y + h - Ui.f(21), tLine);
        if (i >= 0 && Look.progress) {
            float bx = x + Ui.f(16), by = y + h - Ui.f(10);
            track.setAlpha(a * 0x40 / 255);
            bar.setAlpha(a);
            c.drawRect(bx, by, bx + room, by + Ui.f(3), track);
            c.drawRect(bx, by, bx + room * LiveText.progress(now, s.start[i], s.stop[i]), by + Ui.f(3), bar);
        }
    }

    /** Under a channel's tile: its number and name, and what's left of what's on (fitted as the tile was drawn). */
    private void under(Canvas c, Tile t, float x, float y, float w, boolean on, int a) {
        if (t.fitName == null) return;
        TextPaint np = on ? nameOn : nameP;
        np.setAlpha(a);
        float base = y + Ui.f(32);
        c.drawText(t.fitName, x, base, np);
        if (!t.fitLeft.isEmpty()) {
            leftP.setAlpha(a);
            c.drawText(t.fitLeft, x + w - leftP.measureText(t.fitLeft), base, leftP);
        }
    }

    /** A wide tile to carry on from: its picture, its title and where it was over the foot, how far through. */
    private void wide(Canvas c, Tile t, float x, float y, float w, float h, float rad, int a) {
        Bitmap b = Pics.get(t.picture, Math.round(w), Math.round(h), Pics.CARD, redraw);
        if (b == null && !t.fallback.isEmpty()) b = Pics.get(t.fallback, Math.round(w), Math.round(h), Pics.CARD, redraw);
        bmp.setAlpha(a);
        if (b != null) {
            c.drawBitmap(b, null, r, bmp);
        } else {
            card.setAlpha(a);
            c.drawRoundRect(r, rad, rad, card);
        }
        float room = w - Ui.f(32);
        if (t.fitW != room || t.fitT == null) {
            t.fitT = Ui.fit(t.title, tTitle, room);
            t.fitS = Ui.fit(t.subtitle, tLine, room);
            t.fitW = room;
        }
        tTitle.setAlpha(a);
        tLine.setAlpha(a);
        boolean two = !t.fitS.isEmpty();
        float base = y + h - Ui.f(t.progress >= 0f ? 21 : 16);
        if (two) {
            c.drawText(t.fitT, x + Ui.f(16), base - Ui.f(27), tTitle);
            c.drawText(t.fitS, x + Ui.f(16), base, tLine);
        } else {
            c.drawText(t.fitT, x + Ui.f(16), base, tTitle);
        }
        if (t.progress >= 0f) {
            float bx = x + Ui.f(16), by = y + h - Ui.f(10);
            track.setAlpha(a * 0x40 / 255);
            bar.setAlpha(a);
            c.drawRect(bx, by, bx + room, by + Ui.f(3), track);
            c.drawRect(bx, by, bx + room * Math.max(0.02f, Math.min(1f, t.progress)), by + Ui.f(3), bar);
        }
    }
}
