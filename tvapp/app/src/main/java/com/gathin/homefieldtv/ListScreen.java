package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One of the lists of Movies or TV shows, all of it: under the top bar the list's name large and how many it has, a
 * pill on the right to sort it (as in the playlist, by name, newest first, best rated: a small menu drops from it), and
 * every poster in rows (six across, or four wide pictures as Settings → Posters say), the page taking the colour of
 * the poster the remote is on, which grows. The remote's row glides up under the heading. OK opens a title's page.
 * Sorting a list of thousands happens off the main thread; only the posters in sight are drawn.
 */
final class ListScreen extends Screen {
    private final boolean series;
    private final String name;
    private final List<Catalog.Title> titles;
    private List<Catalog.Title> shown;
    private int sort, sortAsk;
    private TopBar bar;
    private Wall wall;
    private boolean onBar;

    ListScreen(TvActivity tv, boolean series, String name, List<Catalog.Title> titles) {
        super(tv);
        this.series = series;
        this.name = name;
        this.titles = titles;
        this.shown = titles;
    }

    @Override View build() {
        Cine.init(tv);
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Cine.GROUND);
        wall = new Wall(tv);
        bar = new TopBar(tv, series ? TopBar.SHOWS : TopBar.MOVIES);
        f.addView(wall, new FrameLayout.LayoutParams(-1, -1));
        f.addView(bar, new FrameLayout.LayoutParams(-1, Ui.px(TopBar.H + 90f)));
        wall.reset();
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        if (wall != null) wall.invalidate();
    }

    @Override void tick() {
        bar.invalidate();
    }

    /** The list sorted as asked, off the main thread for a long one (an answer to an older ask let go). */
    private void resort(int how) {
        sort = how;
        int ask = ++sortAsk;
        if (how == 0) {
            shown = titles;
            wall.reset();
            return;
        }
        Exec.work(() -> {
            List<Catalog.Title> copy = new ArrayList<>(titles);
            copy.sort(BrowseScreen.order(how));
            Runnable done = () -> {
                if (ask == sortAsk) {
                    shown = copy;
                    wall.reset();
                }
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (onBar) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                onBar = false;
                bar.enter(false);
                wall.onPill = true;
                wall.invalidate();
                return true;
            }
            return bar.key(code) || code == KeyEvent.KEYCODE_DPAD_UP;
        }
        return wall.key(code);
    }

    @Override void ok() {
        if (onBar) {
            TopBar.go(tv, bar.focus, bar.current, series);
            return;
        }
        wall.ok();
    }

    @Override boolean back() {
        if (wall.menuOpen) {
            wall.menuOpen = false;
            wall.invalidate();
            return true;
        }
        if (onBar) {
            onBar = false;
            bar.enter(false);
            wall.invalidate();
            return true;
        }
        return false;
    }

    private void toBar() {
        onBar = true;
        wall.onPill = false;
        bar.enter(true);
        wall.invalidate();
    }

    /** Previews and tests: the remote on a poster (its place in the list), or on the sort pill with its menu open. */
    void focus(int index) {
        wall.focus = Math.max(0, Math.min(index, shown.size() - 1));
        wall.onPill = false;
        wall.place(true);
    }

    void openSort() {
        wall.onPill = true;
        wall.menuOpen = true;
        wall.menuAt = sort;
        wall.invalidate();
    }

    // ---- the posters ------------------------------------------------------------------------------------------------

    /** The heading, the sort pill and its menu, and the posters in rows. */
    private final class Wall extends View {
        static final float TOP = 330f, GAP_X = 30f, GAP_Y = 40f;
        int focus;
        boolean onPill, menuOpen;
        int menuAt;
        private final Smooth y = new Smooth(120f, 0.4f);
        private final Lift lift = new Lift();
        private final TileArt art = new TileArt();
        private final Cine.Wash wash = new Cine.Wash();
        private final TextPaint headP = Cine.text(58f, Cine.outfit(800), Cine.TEXT), countP = Cine.text(26f, Cine.inter(), Cine.MUTED);
        private final TextPaint pillP = Cine.text(25f, Cine.interSemi(), Cine.TEXT), pillInk = Cine.text(25f, Cine.interSemi(), Cine.INK),
                rowP = Cine.text(27f, Cine.inter(), Cine.SOFT);
        private final Paint ground = new Paint(), grey = Ui.fill(Cine.BUTTON), white = Ui.fill(Cine.WHITE),
                menuFill = Ui.fill(0xF2202128), lit = Ui.fill(0x2EFFFFFF), dim = Ui.fill(0x99000000), tick = Ui.stroke(Cine.TEXT, 4f);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate, recolour = this::recolour;
        private String heading = "", count = "", sortWords = "";

        Wall(Context c) {
            super(c);
        }

        int columns() {
            return PosterArt.landscape ? 4 : 6;
        }

        float tileW() {
            return PosterArt.landscape ? CineRows.WIDE_W * 1.08f : CineRows.POSTER_W;
        }

        float tileH() {
            return PosterArt.landscape ? CineRows.WIDE_H * 1.08f : CineRows.POSTER_H;
        }

        float cellH() {
            return tileH() + GAP_Y;
        }

        /** The space between the posters, so a row fills the page between its margins. */
        float pitch() {
            int cols = columns();
            return (1920f - 2 * TopBar.LEFT - tileW()) / (cols - 1);
        }

        void reset() {
            focus = Math.min(focus, Math.max(0, shown.size() - 1));
            heading = Ui.fit(name, headP, Ui.f(1200f));
            count = String.format(Locale.UK, "%,d", shown.size()) + (shown.size() == 1 ? (series ? " show" : " movie") : series ? " shows" : " movies");
            sortWords = "Sort  ·  " + BrowseScreen.SORTS[sort];
            place(true);
        }

        void place(boolean now) {
            float want = (focus / columns()) * cellH();
            if (now) y.jump(want);
            else y.to(want);
            recolour();
        }

        private void recolour() {
            Catalog.Title t = focus < shown.size() ? shown.get(focus) : null;
            int art = t == null ? 0 : Tint.of(picture(t), recolour);
            wash.set(Cine.shade(art, 0.6f, 0.16f));
            invalidate();
        }

        private String picture(Catalog.Title t) {
            if (!PosterArt.landscape) return t.poster;
            Catalog.Details d = Catalog.get(tv).cachedDetails(t);
            return d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
        }

        boolean key(int code) {
            if (menuOpen) {
                if (code == KeyEvent.KEYCODE_DPAD_UP) menuAt = Math.max(0, menuAt - 1);
                else if (code == KeyEvent.KEYCODE_DPAD_DOWN) menuAt = Math.min(BrowseScreen.SORTS.length - 1, menuAt + 1);
                invalidate();
                return true;
            }
            if (onPill) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && !shown.isEmpty()) {
                    onPill = false;
                    place(false);
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    toBar();
                }
                return true;
            }
            int cols = columns(), n = shown.size();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (focus % cols == 0) return true;
                    focus--;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (focus % cols < cols - 1 && focus < n - 1) focus++;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (focus < cols) {
                        onPill = true;
                        invalidate();
                        return true;
                    }
                    focus -= cols;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (focus + cols < n) focus += cols;
                    else if (focus / cols < (n - 1) / cols) focus = n - 1;
                    break;
                default:
                    return false;
            }
            place(false);
            return true;
        }

        void ok() {
            if (menuOpen) {
                menuOpen = false;
                if (menuAt != sort) resort(menuAt);
                invalidate();
                return;
            }
            if (onPill) {
                menuOpen = true;
                menuAt = sort;
                invalidate();
                return;
            }
            if (focus < shown.size()) tv.openTitle(shown.get(focus));
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = y.step();
            moving |= wash.step();
            int col = wash.colour();
            ground.setColor(col);
            c.drawRect(0, 0, getWidth(), getHeight(), ground);
            Catalog.Title on = !onBar && !onPill && focus < shown.size() ? shown.get(focus) : null;
            moving |= lift.step(on);
            float left = Ui.f(TopBar.LEFT);
            int cols = columns();
            float tw = Ui.f(tileW()), th = Ui.f(tileH()), pitch = Ui.f(pitch()), ch = Ui.f(cellH());
            float top = Ui.f(TOP) - Ui.f(y.value);
            int firstRow = Math.max(0, (int) Math.floor((Ui.f(TOP) - top) / ch) - 1);
            int lastRow = (int) Math.ceil((getHeight() - top) / ch);
            int lifted = -1;
            for (int rr = firstRow; rr <= lastRow; rr++) {
                float ry = top + rr * ch;
                // Rows passing up under the heading fade away.
                float a = ry < Ui.f(TOP - 30f) ? Math.max(0f, 1f - (Ui.f(TOP - 30f) - ry) / Ui.f(140f)) : 1f;
                if (a <= 0f) continue;
                for (int k = 0; k < cols; k++) {
                    int i = rr * cols + k;
                    if (i >= shown.size()) break;
                    Catalog.Title t = shown.get(i);
                    if (lift.of(t) > 0.01f) {
                        if (t == on) lifted = i;
                        else poster(c, t, left + k * pitch, ry, tw, th, lift.of(t), Math.round(255 * a));
                        continue;
                    }
                    poster(c, t, left + k * pitch, ry, tw, th, 0f, Math.round(255 * a));
                }
            }
            if (lifted >= 0) {
                Catalog.Title t = shown.get(lifted);
                float ry = top + (lifted / cols) * ch;
                poster(c, t, left + (lifted % cols) * pitch, ry, tw, th, lift.of(t), 255);
            }
            // The heading on the page's colour, the rows passing under it.
            ground.setColor(col);
            c.drawRect(0, 0, getWidth(), Ui.f(TOP - 70f), ground);
            heading(c);
            if (moving) postInvalidateOnAnimation();
        }

        private void poster(Canvas c, Catalog.Title t, float x, float y, float w, float h, float up, int alpha) {
            art.poster(c, t, picture(t), x, y, w, h, up, alpha, redraw);
        }

        private void heading(Canvas c) {
            float x = Ui.f(TopBar.LEFT);
            c.drawText(heading, x, Ui.f(214f), headP);
            c.drawText(count, x, Ui.f(262f), countP);
            // The sort pill: grey, white where the remote is.
            float pw = pillP.measureText(sortWords) + Ui.f(64f);
            r.set(Ui.f(1920f - TopBar.LEFT) - pw, Ui.f(176f), Ui.f(1920f - TopBar.LEFT), Ui.f(240f));
            boolean here = onPill && !onBar;
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, here ? white : grey);
            c.drawText(sortWords, r.left + Ui.f(32f), Cine.mid(pillP, r.centerY()), here ? pillInk : pillP);
            if (menuOpen) menu(c, r.right);
        }

        /** The sort's menu, dropped from the pill: a row a way, the one in use ticked, the remote's lit. */
        private void menu(Canvas c, float right) {
            float w = Ui.f(440f), rowH = Ui.f(74f), top = Ui.f(256f);
            float h = rowH * BrowseScreen.SORTS.length + Ui.f(24f);
            c.drawRect(0, Ui.f(TOP - 60f), getWidth(), getHeight(), dim);
            r.set(right - w, top, right, top + h);
            c.drawRoundRect(r, Ui.f(18f), Ui.f(18f), menuFill);
            for (int i = 0; i < BrowseScreen.SORTS.length; i++) {
                float ry = top + Ui.f(12f) + i * rowH;
                if (i == menuAt) {
                    r.set(right - w + Ui.f(12f), ry, right - Ui.f(12f), ry + rowH);
                    c.drawRoundRect(r, Ui.f(12f), Ui.f(12f), lit);
                }
                rowP.setColor(i == menuAt ? Cine.TEXT : Cine.SOFT);
                c.drawText(BrowseScreen.SORTS[i], right - w + Ui.f(68f), Cine.mid(rowP, ry + rowH / 2f), rowP);
                if (i == sort) {
                    tick.setStrokeWidth(Ui.f(4f));
                    Cine.tick(c, right - w + Ui.f(36f), ry + rowH / 2f, Ui.f(22f), tick);
                }
            }
        }
    }
}
