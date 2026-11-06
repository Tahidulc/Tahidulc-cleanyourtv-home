package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Sources;

import java.util.ArrayList;
import java.util.List;

/**
 * Movies, or TV shows: the family's playlist's own catalogue, a row a category in the provider's order (the newest
 * first), the big picture and synopsis of whatever the remote is on at the top. OK opens the title's page.
 */
final class BrowseScreen extends Screen implements Rows.Host {
    private static final int ROW_MAX = 400;

    private final boolean series;
    private Strip strip;
    private Hero hero;
    private Rows rows;
    private Message message;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title pending;
    private final Runnable fetch = this::fetchDetails;
    /** The catalogue the category shelves were made from, and those shelves (made again only when it changes). */
    private List<Catalog.Title> madeFrom;
    private final List<Rows.Row> shelves = new ArrayList<>();

    BrowseScreen(TvActivity tv, boolean series) {
        super(tv);
        this.series = series;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        strip = new Strip(tv);
        hero = new Hero(tv);
        hero.textX = 262f;
        hero.textY = 84f;
        hero.textW = 800f;
        hero.pic.set(860f, 0f, 1920f, 560f);
        rows = new Rows(tv);
        rows.left = 262f;
        rows.top = 432f;
        rows.setHost(this);
        message = new Message(tv);
        f.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        f.addView(message, new FrameLayout.LayoutParams(-1, -1));
        f.addView(strip, new FrameLayout.LayoutParams(-1, -1));
        fill();
        return f;
    }

    @Override void shown() {
        // No mini TV here: the stream let go (a film may want the provider's one connection).
        tv.live.stop();
        tv.live.place(null);
        fill();
    }

    @Override void hidden() {
        h.removeCallbacks(fetch);
    }

    @Override void tick() {
        strip.invalidate();
    }

    @Override void catalogChanged() {
        fill();
    }

    private void fill() {
        Catalog cat = Catalog.get(tv);
        List<Rows.Row> list = new ArrayList<>();
        List<Rows.Tile> cont = new ArrayList<>();
        for (Resume.Point p : Resume.all(tv)) {
            Catalog.Title t = cat.title(p.title);
            if (t == null || t.series != series) continue;
            Rows.Tile tile = new Rows.Tile(t);
            tile.picture = t.poster;
            tile.title = t.name;
            tile.progress = p.position == 0 ? -1f : p.progress();
            cont.add(tile);
        }
        if (!cont.isEmpty()) list.add(new Rows.Row("Continue watching", Rows.POSTER, cont));
        List<Catalog.Title> all = series ? cat.series() : cat.films();
        if (all != madeFrom) {
            madeFrom = all;
            shelves.clear();
            List<Rows.Tile> recent = HomeScreen.posters(cat.recent(series, 60));
            if (!recent.isEmpty()) shelves.add(new Rows.Row("Recently added", Rows.POSTER, recent));
            for (Catalog.Category c : cat.categories(series)) {
                List<Catalog.Title> ts = c.titles.size() > ROW_MAX ? c.titles.subList(0, ROW_MAX) : c.titles;
                shelves.add(new Rows.Row(c.name, Rows.POSTER, HomeScreen.posters(ts)));
            }
        }
        list.addAll(shelves);
        rows.setRows(list);
        String msg = "";
        if (list.isEmpty()) {
            if (Sources.list(tv).isEmpty()) msg = "Add your playlist in Settings: its " + (series ? "TV shows" : "films") + " show here.";
            else if (cat.busy() || Library.get(tv).busy()) msg = "Loading your playlist's " + (series ? "TV shows" : "films") + "…";
            else if (!cat.status().isEmpty()) msg = cat.status();
            else msg = "Your playlist has no " + (series ? "TV shows" : "films") + ".";
        }
        message.set(series ? "TV shows" : "Movies", msg);
        showHero();
    }

    private void showHero() {
        Rows.Tile t = rows.tile();
        strip.chevronY = rows.isEmpty() ? -1f : rows.focusTop() + Rows.tileH(Rows.POSTER) / 2f;
        strip.invalidate();
        h.removeCallbacks(fetch);
        if (t == null) {
            hero.clear();
            return;
        }
        HomeScreen.heroFor(tv, hero, t, System.currentTimeMillis());
        pending = t.item instanceof Catalog.Title ? (Catalog.Title) t.item : null;
        if (pending != null && Catalog.get(tv).cachedDetails(pending) == null && !pending.series) h.postDelayed(fetch, 450);
    }

    private void fetchDetails() {
        Catalog.Title want = pending;
        if (want == null) return;
        Catalog.get(tv).details(want, d -> {
            Rows.Tile t = rows.tile();
            if (t != null && t.item == want) HomeScreen.heroFor(tv, hero, t, System.currentTimeMillis());
        });
    }

    @Override boolean key(int code, KeyEvent e) {
        if (rows.isEmpty() && code == KeyEvent.KEYCODE_DPAD_LEFT) {
            tv.pop();
            return true;
        }
        return rows.key(code);
    }

    @Override void ok() {
        rows.ok(false);
    }

    @Override public void focused(Rows.Row row, Rows.Tile tile) {
        showHero();
    }

    @Override public void chosen(Rows.Row row, Rows.Tile tile, boolean held) {
        if (tile.item instanceof Catalog.Title) tv.openTitle((Catalog.Title) tile.item);
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.LEFT) {
            tv.pop();
            return true;
        }
        return false;
    }

    /** The page's name and a line when there is nothing to show. */
    static final class Message extends View {
        private String title = "", text = "";
        private final TextPaint titleP = Ui.paint(56, false, Ui.WHITE), textP = Ui.paint(32, false, Ui.DIM);

        Message(Context c) {
            super(c);
        }

        void set(String t, String m) {
            title = t;
            text = m;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (text.isEmpty()) return;
            c.drawText(title, Ui.f(262), Ui.f(150), titleP);
            StaticLayout l = Ui.layout(text, textP, Ui.px(1100), 4);
            Ui.draw(c, l, Ui.f(262), Ui.f(200));
        }
    }
}
