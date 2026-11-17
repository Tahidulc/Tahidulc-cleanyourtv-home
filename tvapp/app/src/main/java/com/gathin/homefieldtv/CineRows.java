package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Movies' and TV shows' rows under the big picture: each under its heading, its posters (or wide pictures, as Settings →
 * Posters say) along it, Continue watching's wide tiles with their red bars, and at a long row's end a tile to see all
 * of it ("All 1,234"). The row the remote is on sits just under the picture with the remote's tile first, grown; the
 * rows above slide up out of sight, the next one shows below. Up and Down go from row to row (each keeps its place), Left
 * and Right along it; rows and tiles glide there (Glide, carrying on from where they are when a key is held down).
 * Only what is in sight is drawn.
 */
final class CineRows extends View {
    interface Host {
        /** The remote moved to a row's tile (index: the "All" tile is the row's size). */
        void focused(Row row, int index);

        /** OK on a row's tile, or on its "All" tile (index = the row's size). */
        void chosen(Row row, int index);

        /** The remote ran off the top (Ui.UP) or the left (Ui.LEFT); true when the host took it. */
        boolean edge(int dir);
    }

    static final int POSTERS = 0, WIDE = 1;

    /** A tile: its title, its picture, and for a wide one its line ("S2 E4 · 32m left") and how far it was watched. */
    static final class Tile {
        final Catalog.Title title;
        final String picture, line;
        final float progress;
        final Object item;

        Tile(Catalog.Title title, String picture, String line, float progress, Object item) {
            this.title = title;
            this.picture = picture == null ? "" : picture;
            this.line = line == null ? "" : line;
            this.progress = progress;
            this.item = item;
        }
    }

    /** One row: its heading, kind and tiles, how many its whole list has (an "All" tile when it has more), a tag to keep it by. */
    static final class Row {
        final String title;
        final int kind;
        final List<Tile> tiles;
        final int total;
        final Object tag;
        final String allWords;
        int focus;
        final Glide x = new Glide(ROW_MS);

        Row(String title, int kind, List<Tile> tiles, int total, Object tag) {
            this.title = title;
            this.kind = kind;
            this.tiles = tiles == null ? Collections.emptyList() : tiles;
            this.total = total;
            this.tag = tag;
            this.allWords = "All " + String.format(Locale.UK, "%,d", total);
        }

        boolean hasAll() {
            return total > tiles.size();
        }

        int stops() {
            return tiles.size() + (hasAll() ? 1 : 0);
        }
    }

    /** Places (design pixels): the left edge, where the remote's row's heading sits, a heading's height, the gaps. */
    static final float LEFT = TopBar.LEFT, ROW_Y = 630f, HEAD = 80f, GAP = 26f, AFTER = 40f;
    static final float POSTER_W = 226f, POSTER_H = 339f, WIDE_W = 384f, WIDE_H = 216f;

    private final List<Row> rows = new ArrayList<>();
    private int row;
    private final Glide y = new Glide(DOWN_MS);
    /** How long a row takes to glide along, and the rows up or down. */
    static final long ROW_MS = 260, DOWN_MS = 320;
    private Host host;
    private boolean active = true;
    private final Lift lift = new Lift();
    private final TileArt art = new TileArt();
    private final TextPaint headP;
    private final Runnable redraw = this::invalidate;

    CineRows(Context c) {
        super(c);
        headP = Cine.text(31f, Cine.outfit(600), Cine.TEXT);
    }

    void setHost(Host h) {
        host = h;
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    /** The rows (the remote kept on the row with the same tag, else the first; each row keeps its place). */
    void set(List<Row> list) {
        Object was = row < rows.size() ? rows.get(row).tag : null;
        int[] focusOf = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            for (Row old : rows) {
                if (old.tag != null && old.tag.equals(list.get(i).tag)) {
                    focusOf[i] = Math.min(old.focus, Math.max(0, list.get(i).stops() - 1));
                    break;
                }
            }
        }
        rows.clear();
        rows.addAll(list);
        row = 0;
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).focus = focusOf[i];
            rows.get(i).x.jump(target(rows.get(i)));
            if (was != null && was.equals(rows.get(i).tag)) row = i;
        }
        y.jump(top(row));
        invalidate();
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    Row current() {
        return row < rows.size() ? rows.get(row) : null;
    }

    /** The tile the remote is on (null on an "All" tile). */
    Tile tile() {
        Row rw = current();
        return rw == null || rw.focus >= rw.tiles.size() ? null : rw.tiles.get(rw.focus);
    }

    /**
     * The tile a key or two from the remote's (down rows away, then along places from where that row's remote rests), or
     * null: for reading its pictures ahead.
     */
    Tile near(int down, int along) {
        int ri = row + down;
        if (ri < 0 || ri >= rows.size()) return null;
        Row rw = rows.get(ri);
        int i = rw.focus + along;
        return i < 0 || i >= rw.tiles.size() ? null : rw.tiles.get(i);
    }

    private float tileW(Row rw) {
        return rw.kind == WIDE || PosterArt.landscape ? WIDE_W : POSTER_W;
    }

    private float tileH(Row rw) {
        return rw.kind == WIDE || PosterArt.landscape ? WIDE_H : POSTER_H;
    }

    private float height(Row rw) {
        return HEAD + tileH(rw) + AFTER;
    }

    /** Where a row's top is, the first row's at 0 (design pixels). */
    private float top(int i) {
        float t = 0;
        for (int k = 0; k < i && k < rows.size(); k++) t += height(rows.get(k));
        return t;
    }

    /** Where a row slides so its remote's tile is first. */
    private float target(Row rw) {
        return -rw.focus * (tileW(rw) + GAP);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    boolean key(int code) {
        Row rw = current();
        if (rw == null) return host != null && host.edge(code == KeyEvent.KEYCODE_DPAD_LEFT ? Ui.LEFT : Ui.UP);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (rw.focus == 0) return host != null && host.edge(Ui.LEFT);
                move(rw, rw.focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (rw.focus < rw.stops() - 1) move(rw, rw.focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row == 0) return host != null && host.edge(Ui.UP);
                toRow(row - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (row < rows.size() - 1) toRow(row + 1);
                return true;
            default:
                return false;
        }
    }

    private void move(Row rw, int to) {
        rw.focus = to;
        rw.x.to(target(rw));
        told();
    }

    private void toRow(int i) {
        row = i;
        y.to(top(row));
        told();
    }

    /** Previews and tests: the remote on a row and a tile at once. */
    void focus(int rowIndex, int index) {
        if (rowIndex < 0 || rowIndex >= rows.size()) return;
        row = rowIndex;
        Row rw = rows.get(row);
        rw.focus = Math.max(0, Math.min(index, rw.stops() - 1));
        rw.x.jump(target(rw));
        y.jump(top(row));
        told();
    }

    private void told() {
        Row rw = current();
        if (host != null && rw != null) host.focused(rw, rw.focus);
        invalidate();
    }

    void ok() {
        Row rw = current();
        if (host != null && rw != null) host.chosen(rw, rw.focus);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        boolean moving = y.step();
        Row on = current();
        Object focusKey = on == null || !active ? null : on.focus < on.tiles.size() ? on.tiles.get(on.focus) : on;
        moving |= lift.step(focusKey);
        float top = Ui.f(ROW_Y) - Ui.f(y.value());
        for (int i = 0; i < rows.size(); i++) {
            Row rw = rows.get(i);
            float h = Ui.f(height(rw));
            if (top > getHeight()) break;
            if (top + h > 0) {
                // Rows above the remote's slide up out of sight under the picture's words.
                float fade = i >= row ? 1f : Math.max(0f, Math.min(1f, (top + h - Ui.f(ROW_Y - 40f)) / Ui.f(180f)));
                int alpha = Math.round(255 * fade);
                if (alpha > 0) moving |= drawRow(c, rw, top, alpha);
            }
            top += h;
        }
        if (moving) postInvalidateOnAnimation();
    }

    /** One row at top (screen pixels); true while its tiles are still sliding. */
    private boolean drawRow(Canvas c, Row rw, float top, int alpha) {
        boolean moving = rw.x.step();
        float left = Ui.f(LEFT);
        headP.setAlpha(alpha);
        c.drawText(rw.title, left, top + Ui.f(HEAD - 18f), headP);
        float tw = Ui.f(tileW(rw)), th = Ui.f(tileH(rw)), step = tw + Ui.f(GAP);
        float x0 = left + Ui.f(rw.x.value());
        int first = Math.max(0, (int) Math.floor((left - x0) / step) - 1);
        int last = Math.min(rw.stops() - 1, (int) Math.ceil((getWidth() - x0) / step));
        float tileTop = top + Ui.f(HEAD);
        Object leaving = lift.leaving();
        int lifted = -1, away = -1;
        for (int k = first; k <= last; k++) {
            Object key = k < rw.tiles.size() ? rw.tiles.get(k) : rw;
            if (lift.of(key) > 0.01f && rw == current()) {
                if (key == leaving) away = k;
                else lifted = k;
                continue;
            }
            drawTile(c, rw, k, x0 + k * step, tileTop, tw, th, 0f, alpha);
        }
        if (away >= 0) drawTile(c, rw, away, x0 + away * step, tileTop, tw, th, lift.of(leaving), alpha);
        if (lifted >= 0) {
            Object key = lifted < rw.tiles.size() ? rw.tiles.get(lifted) : rw;
            drawTile(c, rw, lifted, x0 + lifted * step, tileTop, tw, th, lift.of(key), alpha);
        }
        return moving;
    }

    private void drawTile(Canvas c, Row rw, int k, float x, float y, float w, float h, float up, int alpha) {
        // Off the page's left edge tiles fade away.
        float left = Ui.f(LEFT);
        float a = x < left - w * 0.3f ? Math.max(0f, 1f - (left - w * 0.3f - x) / (w * 0.7f)) : 1f;
        int al = Math.round(alpha * a);
        if (al <= 0) return;
        if (k >= rw.tiles.size()) {
            art.all(c, rw.allWords, x, y, w, h, up, al);
            return;
        }
        Tile t = rw.tiles.get(k);
        if (rw.kind == WIDE) art.wide(c, t.title, t.picture, t.line, t.progress, x, y, w, h, up, al, redraw);
        else art.poster(c, t.title, t.picture, x, y, w, h, up, al, redraw);
    }
}
