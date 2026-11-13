package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rows of posters and pictures, one under another, drawn by one view: the row the remote is on sits at the same height
 * with its highlight at the left, the row sliding along under it as it moves; the rows above fade away under the big
 * picture. Only what is on screen is drawn, so a catalogue of thousands costs no more than a dozen.
 */
final class Rows extends View {
    static final int POSTER = 0, WIDE = 1, CHANNEL = 2, EPISODE = 3;

    static final class Tile {
        final Object item;
        String picture = "", fallback = "", logo = "", title = "", subtitle = "";
        float progress = -1f;
        private String fitT, fitS;
        private float fitW = -1f;
        private StaticLayout card, under;

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

        Row(String title, int kind, List<Tile> tiles) {
            this.title = title == null ? "" : title;
            this.kind = kind;
            this.tiles = tiles;
        }
    }

    interface Host {
        void focused(Row row, Tile tile);

        void chosen(Row row, Tile tile, boolean held);

        /** The remote went past the rows' edge (Ui.LEFT, UP, RIGHT, DOWN); true when the screen took it. */
        boolean edge(int dir);
    }

    private final List<Row> rows = new ArrayList<>();
    private int row;
    private boolean active = true;
    private Host host;
    /** The rows' left edge and where the row the remote is on starts (design pixels). */
    float left = 262f, top = 440f;
    /** The rows above the one the remote is on fade away (under a big picture). */
    boolean hideAbove = true;
    private float scrollY, topTo = -1f;
    private long last;

    private final Glow glow = new Glow();
    private final TextPaint headP = Ui.paint(36, false, Ui.WHITE), capP = Ui.paint(27, false, Ui.WHITE);
    private final TextPaint subP = Ui.paint(22, false, Ui.DIM), cardP = Ui.paint(30, true, Ui.WHITE);
    private final TextPaint underP = Ui.paint(26, false, Ui.WHITE), underDim = Ui.paint(22, false, Ui.FAINT);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), track = Ui.fill(0x55FFFFFF);
    private final Paint bar = Ui.fill(Ui.CYAN), cardBack = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint();
    private final Paint tileBack = Ui.fill(com.gathin.broadcast.LogoTile.GROUND);
    private final RectF r = new RectF(), f = new RectF();
    private float shadeFor = -1f, cardFor = -1f;
    private final Runnable redraw = this::invalidate;

    Rows(Context c) {
        super(c);
    }

    void setHost(Host h) {
        host = h;
    }

    // ---- sizes (design pixels) ----------------------------------------------------------------------------------

    static float tileW(int kind) {
        return kind == POSTER ? 230f : kind == EPISODE ? 384f : 420f;
    }

    static float tileH(int kind) {
        return kind == POSTER ? 345f : kind == EPISODE ? 216f : 236f;
    }

    private static float gap(int kind) {
        return kind == POSTER ? 24f : 26f;
    }

    /** The row's title above it, with room for the highlight's glow under the writing. */
    private static float head(Row r) {
        return r.title.isEmpty() ? 0f : r.kind == POSTER ? 80f : 72f;
    }

    private static float under(int kind) {
        return kind == EPISODE ? 78f : 0f;
    }

    static float height(Row r) {
        return head(r) + tileH(r.kind) + under(r.kind) + (r.kind == POSTER ? 34f : 40f);
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

    int rowIndex() {
        return row;
    }

    Tile tile() {
        Row r = row();
        return r == null || r.tiles.isEmpty() ? null : r.tiles.get(Math.min(r.focus, r.tiles.size() - 1));
    }

    void focusRow(int i) {
        if (i < 0 || i >= rows.size()) return;
        row = i;
        changed();
    }

    void setActive(boolean on) {
        active = on;
        invalidate();
    }

    /** The rows' top moved to a new place, gliding there (at once in previews). */
    void moveTop(float to) {
        if (topTo < 0) topTo = top;
        topTo = to;
        if (Exec.inline) top = to;
        invalidate();
    }

    boolean active() {
        return active;
    }

    /** Where the row the remote is on starts on screen, for the strip's chevron (design pixels). */
    float focusTop() {
        Row r = row();
        return top + (r == null ? 0 : head(r));
    }

    private float targetY() {
        float y = 0;
        for (int i = 0; i < row && i < rows.size(); i++) y += Ui.f(height(rows.get(i)));
        return y;
    }

    private void changed() {
        invalidate();
        if (host != null) host.focused(row(), tile());
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
        if (topTo >= 0 && Math.abs(topTo - top) > 0.5f) {
            top += (topTo - top) * k;
            moving = true;
        } else if (topTo >= 0) {
            top = topTo;
        }
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
        float topPx = Ui.f(top);
        float y = topPx - scrollY;
        for (int i = 0; i < rows.size(); i++) {
            Row x = rows.get(i);
            float h = Ui.f(height(x));
            if (y > getHeight()) break;
            if (y + h > 0) {
                float alpha = 1f;
                if (hideAbove && y < topPx - 1) alpha = Math.max(0f, 1f - (topPx - y) / (h * 0.6f));
                if (alpha > 0.01f) drawRow(c, x, y, (int) (alpha * 255), i == row);
            }
            y += h;
        }
        if (moving) postInvalidateOnAnimation();
    }

    private void drawRow(Canvas c, Row x, float y, int a, boolean focusedRow) {
        float x0 = Ui.f(left);
        if (!x.title.isEmpty()) {
            headP.setAlpha(a);
            c.drawText(Ui.fit(x.title, headP, getWidth() - x0 - Ui.f(40)), x0, y + Ui.f(40), headP);
        }
        int k = x.kind;
        float ty = y + Ui.f(head(x)), tw = Ui.px(tileW(k)), th = Ui.px(tileH(k)), pitch = tw + Ui.f(gap(k));
        c.save();
        c.clipRect(x0 - Ui.f(26), ty - Ui.f(34), getWidth(), ty + th + Ui.f(under(k)) + Ui.f(34));
        int n = x.tiles.size();
        int from = Math.max(0, (int) Math.floor(x.x) - 1);
        int to = Math.min(n - 1, from + (int) Math.ceil((getWidth() - x0) / pitch) + 2);
        boolean focusHere = focusedRow && active;
        for (int i = from; i <= to; i++) {
            if (focusHere && i == x.focus) continue;
            float tx = x0 + (i - x.x) * pitch;
            if (tx + tw < x0 - Ui.f(4)) continue;
            drawTile(c, x.tiles.get(i), k, tx, ty, tw, th, a);
        }
        if (focusHere && x.focus < n) {
            float tx = x0 + (x.focus - x.x) * pitch;
            float sc = k == POSTER ? 1.08f : 1.06f;
            float cx = tx + tw / 2f, cy = ty + th / 2f;
            f.set(cx - tw * sc / 2f - Ui.f(11), cy - th * sc / 2f - Ui.f(11), cx + tw * sc / 2f + Ui.f(11),
                    cy + th * sc / 2f + Ui.f(11) + (k == EPISODE ? Ui.f(under(k)) : 0f));
            glow.draw(c, f);
            c.save();
            c.scale(sc, sc, cx, cy);
            drawTile(c, x.tiles.get(x.focus), k, tx, ty, tw, th, a);
            c.restore();
        }
        c.restore();
    }

    private void drawTile(Canvas c, Tile t, int k, float x, float y, float w, float h, int a) {
        int iw = Math.round(w), ih = Math.round(h);
        bmp.setAlpha(a);
        r.set(x, y, x + w, y + h);
        Bitmap b = null;
        if (k != CHANNEL) {
            b = Pics.get(t.picture, iw, ih, Pics.CROP, redraw);
            if (b == null && !t.fallback.isEmpty()) b = Pics.get(t.fallback, iw, ih, Pics.CROP, redraw);
        }
        if (b != null) {
            c.drawBitmap(b, null, r, bmp);
        } else {
            card(c, t, k, x, y, w, h, a);
        }
        if (k == WIDE || k == CHANNEL) caption(c, t, k, x, y, w, h, a);
        if (t.progress >= 0f) {
            float bh = Ui.f(5);
            track.setAlpha(a * 0x55 / 255);
            bar.setAlpha(a);
            c.drawRect(x, y + h - bh, x + w, y + h, track);
            c.drawRect(x, y + h - bh, x + w * Math.max(0.02f, Math.min(1f, t.progress)), y + h, bar);
        }
        if (k == EPISODE) {
            if (t.under == null) t.under = Ui.layout(t.title, underP, Math.round(w), 2);
            underP.setAlpha(a);
            Ui.draw(c, t.under, x, y + h + Ui.f(10));
            if (!t.subtitle.isEmpty()) {
                underDim.setAlpha(a * 0x9E / 255);
                c.drawText(Ui.fit(t.subtitle, underDim, w), x, y + h + Ui.f(10) + t.under.getHeight() + Ui.f(24), underDim);
            }
        }
    }

    /** No picture: the guide's blue, the title or the channel's logo on it. */
    private void card(Canvas c, Tile t, int k, float x, float y, float w, float h, int a) {
        if (cardFor != h) {
            cardBack.setShader(new LinearGradient(0, 0, 0, h, Ui.CARD_LIGHT, Ui.CARD, Shader.TileMode.CLAMP));
            cardFor = h;
        }
        cardBack.setAlpha(a);
        c.save();
        c.translate(x, y);
        // A channel's card on its black tile (Pics.tiles), the logo made ready for black.
        boolean tile = k == CHANNEL && Pics.tiles;
        if (tile) {
            tileBack.setAlpha(a);
            c.drawRect(0, 0, w, h, tileBack);
        } else {
            c.drawRect(0, 0, w, h, cardBack);
        }
        if (k == CHANNEL) {
            Bitmap logo = Pics.get(t.logo, Math.round(w * 0.6f), Math.round(h * 0.4f), tile ? Pics.READY : Pics.FIT, redraw);
            if (logo != null) {
                bmp.setAlpha(a);
                c.drawBitmap(logo, (w - logo.getWidth()) / 2f, h * 0.36f - logo.getHeight() / 2f, bmp);
            } else {
                String name = t.subtitle.isEmpty() ? t.title : t.subtitle;
                cardP.setAlpha(a);
                String s = Ui.fit(name, cardP, w - Ui.f(40));
                c.drawText(s, (w - cardP.measureText(s)) / 2f, h * 0.36f + Ui.f(10), cardP);
            }
        } else if (k != WIDE) {
            if (t.card == null) t.card = Ui.layout(t.title, cardP, Math.round(w - Ui.f(36)), 4);
            cardP.setAlpha(a);
            Ui.draw(c, t.card, Ui.f(18), (h - t.card.getHeight()) / 2f);
        }
        c.restore();
    }

    /** The writing along the foot of a wide picture, on a dark fade. */
    private void caption(Canvas c, Tile t, int k, float x, float y, float w, float h, int a) {
        if (shadeFor != h) {
            shade.setShader(new LinearGradient(0, h * 0.42f, 0, h, 0x00000000, 0xE0000000, Shader.TileMode.CLAMP));
            shadeFor = h;
        }
        shade.setAlpha(a);
        c.save();
        c.translate(x, y);
        c.drawRect(0, h * 0.42f, w, h, shade);
        float tw = w - Ui.f(32);
        if (t.fitW != tw) {
            t.fitT = Ui.fit(t.title, capP, tw);
            t.fitS = Ui.fit(t.subtitle, subP, tw);
            t.fitW = tw;
        }
        boolean two = !t.fitS.isEmpty();
        float base = h - Ui.f(t.progress >= 0 ? 20 : 16);
        capP.setAlpha(a);
        subP.setAlpha(a * 0xD9 / 255);
        if (two) {
            c.drawText(t.fitS, Ui.f(16), base, subP);
            c.drawText(t.fitT, Ui.f(16), base - Ui.f(30), capP);
        } else {
            c.drawText(t.fitT, Ui.f(16), base, capP);
        }
        if (k == WIDE && !t.logo.isEmpty()) {
            // The channel's logo over the picture: on its own small black tile (Pics.tiles).
            Bitmap logo = Pics.tiles ? Pics.get(t.logo, Ui.px(100), Ui.px(56), Pics.TILE, redraw)
                    : Pics.get(t.logo, Ui.px(170), Ui.px(44), Pics.FIT, redraw);
            if (logo != null) {
                bmp.setAlpha(a);
                c.drawBitmap(logo, Ui.f(16), base - Ui.f(two ? 64 : 34) - logo.getHeight(), bmp);
            }
        }
        c.restore();
    }
}
