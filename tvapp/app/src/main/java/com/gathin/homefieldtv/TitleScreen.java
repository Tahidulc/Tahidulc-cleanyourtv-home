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
import com.gathin.homefieldtv.engine.Resume;

import java.util.ArrayList;
import java.util.List;

/**
 * A film's or a series' page: its picture, title, stars, running time and synopsis, who is in it, Play (or Resume
 * where it was left), and for a series its seasons and episodes; for a film, more from its category.
 */
final class TitleScreen extends Screen implements Rows.Host {
    private static final int BUTTONS = 0, SEASONS = 1, ROWS = 2;

    private Catalog.Title title;
    private Catalog.Details details;
    private boolean asked, asking;
    private FrameLayout page;
    private Strip strip;
    private Hero hero;
    private Extras extras;
    private Rows rows;
    private int area = BUTTONS, button, season;
    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    TitleScreen(TvActivity tv, Catalog.Title t) {
        super(tv);
        this.title = t;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new FrameLayout(tv);
        strip = new Strip(tv);
        hero = new Hero(tv);
        hero.textX = 262f;
        hero.textY = 78f;
        hero.textW = 900f;
        hero.pic.set(700f, 0f, 1920f, 720f);
        hero.titleLines = 2;
        hero.plotLines = title.series ? 5 : 4;
        hero.setTitleSize(60f);
        extras = new Extras(tv);
        rows = new Rows(tv);
        rows.left = 262f;
        rows.top = title.series ? 762f : 680f;
        rows.hideAbove = false;
        rows.setHost(this);
        rows.setActive(false);
        page.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        page.addView(extras, new FrameLayout.LayoutParams(-1, -1));
        page.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        f.addView(strip, new FrameLayout.LayoutParams(-1, -1));
        details = Catalog.get(tv).cachedDetails(title);
        fill();
        if (details == null) ask();
        return f;
    }

    /** The title's details asked of its provider (again, after a failure). */
    private void ask() {
        if (asking) return;
        asking = true;
        fill();
        Catalog.get(tv).details(title, d -> {
            asking = false;
            asked = true;
            details = d;
            fill();
        });
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        // Back from a film: its resume point has moved.
        if (built()) fill();
    }

    @Override void tick() {
        strip.invalidate();
    }

    @Override void catalogChanged() {
        Catalog.Title now = Catalog.get(tv).title(title.key);
        if (now != null && now != title) {
            title = now;
            fill();
        }
    }

    private Resume.Point point() {
        return title.series ? Resume.latest(tv, title.key) : Resume.get(tv, title.key, "");
    }

    private void fill() {
        HomeScreen.heroFor(tv, hero, tileOf(title), System.currentTimeMillis());
        labels.clear();
        actions.clear();
        Resume.Point p = point();
        if (!title.series) {
            if (p != null && p.position > 0) {
                labels.add("Resume  ·  " + Ui.left(p.duration - p.position));
                actions.add(() -> tv.play(title, null, p.position));
                labels.add("Play from the start");
                actions.add(() -> tv.play(title, null, 0));
            } else {
                labels.add("Play");
                actions.add(() -> tv.play(title, null, 0));
            }
        } else if (details == null) {
            labels.add(asked && !asking ? "Couldn't get the episodes: try again" : "Finding the episodes…");
            actions.add(this::ask);
        } else {
            List<Catalog.Episode> all = details.episodes();
            Catalog.Episode resume = p == null ? null : details.episode(p.episode);
            if (resume != null) {
                labels.add("Resume S" + resume.season + " E" + resume.number + (p.position > 0 ? "  ·  " + Ui.left(p.duration - p.position) : ""));
                actions.add(() -> tv.play(title, resume, p.position));
            }
            if (!all.isEmpty()) {
                Catalog.Episode first = all.get(0);
                labels.add((resume == null ? "Play" : "Start") + " S" + first.season + " E" + first.number);
                actions.add(() -> tv.play(title, first, 0));
            }
            if (all.isEmpty()) {
                labels.add("No episodes in your playlist");
                actions.add(() -> { });
            }
            if (resume != null && season == 0) {
                for (int i = 0; i < details.seasons.size(); i++) if (details.seasons.get(i).number == resume.season) season = i;
            }
        }
        button = Math.min(button, labels.size() - 1);
        fillRows();
        extras.invalidate();
    }

    private static Rows.Tile tileOf(Catalog.Title t) {
        Rows.Tile tile = new Rows.Tile(t);
        tile.picture = t.poster;
        tile.title = t.name;
        return tile;
    }

    private float rowsTop() {
        return title.series ? 762f : 680f;
    }

    private void fillRows() {
        List<Rows.Row> list = new ArrayList<>();
        if (area != ROWS) rows.moveTop(rowsTop());
        if (title.series) {
            if (details != null && !details.seasons.isEmpty()) {
                season = Math.max(0, Math.min(season, details.seasons.size() - 1));
                Catalog.Season s = details.seasons.get(season);
                Resume.Point p = point();
                List<Rows.Tile> eps = new ArrayList<>();
                for (Catalog.Episode e : s.episodes) {
                    Rows.Tile t = new Rows.Tile(e);
                    t.picture = e.still;
                    t.fallback = !details.backdrop.isEmpty() ? details.backdrop : title.backdrop.isEmpty() ? title.poster : title.backdrop;
                    t.title = e.number + ". " + (e.title.isEmpty() ? "Episode " + e.number : e.title);
                    t.subtitle = Ui.duration(e.minutes);
                    if (p != null && p.episode.equals(e.id) && p.position > 0) {
                        t.progress = p.progress();
                        t.subtitle = (t.subtitle.isEmpty() ? "" : t.subtitle + "  ·  ") + Ui.left(p.duration - p.position);
                    }
                    eps.add(t);
                }
                list.add(new Rows.Row("", Rows.EPISODE, eps));
            }
        } else {
            List<Rows.Tile> more = new ArrayList<>();
            for (Catalog.Category c : Catalog.get(tv).categories(false)) {
                if (!c.name.equals(title.category)) continue;
                for (Catalog.Title t : c.titles) {
                    if (t != title && more.size() < 30) more.add(tileOf(t));
                }
            }
            if (!more.isEmpty()) list.add(new Rows.Row("More like this", Rows.POSTER, more));
        }
        rows.setRows(list);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    private void setArea(int a) {
        area = a;
        rows.setActive(a == ROWS);
        float shift = a == ROWS ? (title.series ? -140f : -280f) : 0f;
        if (Exec.inline) {
            hero.setTranslationY(Ui.f(shift));
            extras.setTranslationY(Ui.f(shift));
        } else {
            hero.animate().translationY(Ui.f(shift)).setDuration(220).start();
            extras.animate().translationY(Ui.f(shift)).setDuration(220).start();
        }
        rows.moveTop(rowsTop() + shift);
        extras.invalidate();
    }

    private boolean hasSeasons() {
        return title.series && details != null && details.seasons.size() > 0;
    }

    @Override boolean key(int code, KeyEvent e) {
        switch (area) {
            case BUTTONS:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (button > 0) button--;
                    else tv.pop();
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (button < labels.size() - 1) button++;
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (hasSeasons()) setArea(SEASONS);
                    else if (!rows.isEmpty()) setArea(ROWS);
                } else {
                    return code == KeyEvent.KEYCODE_DPAD_UP;
                }
                extras.invalidate();
                return true;
            case SEASONS:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (season > 0) {
                        season--;
                        fillRows();
                    } else {
                        tv.pop();
                    }
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (season < details.seasons.size() - 1) {
                        season++;
                        fillRows();
                    }
                } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    setArea(BUTTONS);
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (!rows.isEmpty()) setArea(ROWS);
                } else {
                    return false;
                }
                extras.invalidate();
                return true;
            default:
                return rows.key(code);
        }
    }

    @Override void ok() {
        if (area == BUTTONS) {
            if (button >= 0 && button < actions.size()) actions.get(button).run();
        } else if (area == SEASONS) {
            if (!rows.isEmpty()) setArea(ROWS);
        } else {
            rows.ok(false);
        }
    }

    @Override boolean back() {
        if (area != BUTTONS) {
            setArea(BUTTONS);
            return true;
        }
        return false;
    }

    @Override public void focused(Rows.Row row, Rows.Tile tile) {}

    @Override public void chosen(Rows.Row row, Rows.Tile tile, boolean held) {
        if (tile.item instanceof Catalog.Episode) {
            Catalog.Episode e = (Catalog.Episode) tile.item;
            Resume.Point p = Resume.get(tv, title.key, e.id);
            tv.play(title, e, p == null ? 0 : p.position);
        } else if (tile.item instanceof Catalog.Title) {
            tv.openTitle((Catalog.Title) tile.item);
        }
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.UP) {
            setArea(hasSeasons() ? SEASONS : BUTTONS);
            return true;
        }
        if (dir == Ui.LEFT) {
            tv.pop();
            return true;
        }
        return false;
    }

    // ---- under the synopsis: who is in it, the buttons, the seasons --------------------------------------------

    private final class Extras extends View {
        private final TextPaint people = Ui.paint(26, false, Ui.FAINT), peopleKey = Ui.paint(26, true, Ui.DIM);
        private final TextPaint btnP = Ui.paint(30, false, Ui.WHITE), btnOn = Ui.paint(30, true, Ui.WHITE);
        private final TextPaint tabP = Ui.paint(30, false, Ui.DIM), tabOn = Ui.paint(30, true, Ui.WHITE);
        private final Paint btnBack = Ui.fill(0x26FFFFFF), under = Ui.fill(Ui.CYAN);
        private final Glow glow = new Glow();
        private final RectF r = new RectF();

        Extras(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            float x = Ui.f(262), w = Ui.f(1000);
            float y = title.series ? 528 : 500;
            if (details != null) {
                if (!details.cast.isEmpty()) {
                    c.drawText("Starring", x, Ui.f(y), peopleKey);
                    float k = peopleKey.measureText("Starring") + Ui.f(12);
                    c.drawText(Ui.fit(details.cast, people, w - k), x + k, Ui.f(y), people);
                    y += 38;
                }
                if (!details.director.isEmpty()) {
                    c.drawText("Director", x, Ui.f(y), peopleKey);
                    float k = peopleKey.measureText("Director") + Ui.f(12);
                    c.drawText(Ui.fit(details.director, people, w - k), x + k, Ui.f(y), people);
                }
            }
            // The buttons.
            float bx = x, by = Ui.f(title.series ? 586 : 566), bh = Ui.f(66);
            for (int i = 0; i < labels.size(); i++) {
                boolean on = area == BUTTONS && i == button;
                TextPaint p = on ? btnOn : btnP;
                float bw = p.measureText(labels.get(i)) + Ui.f(64);
                r.set(bx, by, bx + bw, by + bh);
                if (on) glow.draw(c, r);
                else c.drawRect(r, btnBack);
                c.drawText(labels.get(i), bx + Ui.f(32), by + bh / 2f + Ui.f(10), p);
                bx += bw + Ui.f(22);
            }
            // The seasons.
            if (hasSeasons()) {
                float tx = x, ty = Ui.f(708);
                for (int i = 0; i < details.seasons.size(); i++) {
                    String name = details.seasons.get(i).name;
                    boolean sel = i == season, on = sel && area == SEASONS;
                    TextPaint p = sel ? tabOn : tabP;
                    float tw = p.measureText(name);
                    if (on) {
                        r.set(tx - Ui.f(18), ty - Ui.f(40), tx + tw + Ui.f(18), ty + Ui.f(18));
                        glow.draw(c, r);
                    }
                    c.drawText(name, tx, ty, p);
                    if (sel) c.drawRect(tx, ty + Ui.f(10), tx + tw, ty + Ui.f(14), under);
                    tx += tw + Ui.f(52);
                    if (tx > getWidth() - Ui.f(120)) break;
                }
            }
        }
    }
}
