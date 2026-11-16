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
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Sources;

import java.util.ArrayList;
import java.util.List;

/**
 * Films, or TV shows, from the family's playlists, dark: the rail down the left; first every category at a glance, a row
 * of posters each under the big picture of whatever the remote is on (its backdrop, title, facts, story and who is in
 * it); Left from a row's first poster opens the side panel of lists (All categories, all of them, Continue watching,
 * My list, then each category with how many it has), and any list but All categories shows as one grid of posters
 * beside the panel. OK on a poster opens its page.
 */
final class BrowseScreen extends Screen implements Shelves.Host, PosterGrid.Host {
    private static final int ROW_MAX = 400;
    private static final int RAIL = 0, PANEL = 1, CONTENT = 2;
    private static final String WALLS = "walls", ALL = "all", CONTINUE = "continue", MINE = "mine";

    private final boolean series;
    private Rail rail;
    private SidePanel panel;
    private Billboard billboard;
    private Shelves shelves;
    private PosterGrid grid;
    private Message message;
    private View shade;
    private int area = CONTENT;
    /** What the content shows: WALLS, ALL, CONTINUE, MINE, or a category's name. */
    private String showing = WALLS;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title pending;
    private final Runnable fetch = this::fetchDetails;
    private final Runnable switchList = this::showChosen;
    /** The catalogue the shelves were made from (made again only when it changes). */
    private List<Catalog.Title> madeFrom;
    private final List<Shelves.Shelf> categoryShelves = new ArrayList<>();

    BrowseScreen(TvActivity tv, boolean series) {
        super(tv);
        this.series = series;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Dark.GROUND);
        billboard = new Billboard(tv);
        shelves = new Shelves(tv);
        shelves.setHost(this);
        grid = new PosterGrid(tv);
        grid.setHost(this);
        grid.left = SidePanel.X + SidePanel.W + 50f;
        grid.right = 1880f;
        message = new Message(tv);
        shade = new View(tv);
        shade.setBackgroundColor(0x99000000);
        panel = new SidePanel(tv);
        rail = new Rail(tv, series ? Rail.SHOWS : Rail.FILMS);
        f.addView(billboard, new FrameLayout.LayoutParams(-1, -1));
        f.addView(shelves, new FrameLayout.LayoutParams(-1, -1));
        f.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        f.addView(message, new FrameLayout.LayoutParams(-1, -1));
        f.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        f.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        fill();
        place();
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
        h.removeCallbacks(switchList);
    }

    @Override void tick() {
        rail.invalidate();
    }

    @Override void catalogChanged() {
        fill();
    }

    // ---- what it shows ------------------------------------------------------------------------------------------

    private List<Catalog.Title> all() {
        Catalog cat = Catalog.get(tv);
        return series ? cat.series() : cat.films();
    }

    private List<Catalog.Title> continuing() {
        Catalog cat = Catalog.get(tv);
        List<Catalog.Title> out = new ArrayList<>();
        for (Resume.Point p : Resume.all(tv)) {
            Catalog.Title t = cat.title(p.title);
            if (t != null && t.series == series && !out.contains(t)) out.add(t);
        }
        return out;
    }

    private List<Catalog.Title> mine() {
        Catalog cat = Catalog.get(tv);
        List<Catalog.Title> out = new ArrayList<>();
        for (String k : MyList.keys(tv)) {
            Catalog.Title t = cat.title(k);
            if (t != null && t.series == series && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** A title as a poster: its genre (its page's when read, else its category's) and how far through it is. */
    private PosterArt.Item item(Catalog.Title t) {
        PosterArt.Item it = new PosterArt.Item(t);
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        it.genre = d != null && !PosterArt.firstGenre(d.genre).isEmpty() ? PosterArt.firstGenre(d.genre) : PosterArt.genreOf(t.category);
        Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        if (p != null && p.position > 0) it.progress = p.progress();
        return it;
    }

    private List<PosterArt.Item> items(List<Catalog.Title> titles, int max) {
        List<PosterArt.Item> out = new ArrayList<>(Math.min(max, titles.size()));
        for (Catalog.Title t : titles) {
            if (out.size() >= max) break;
            out.add(item(t));
        }
        return out;
    }

    private void fill() {
        Catalog cat = Catalog.get(tv);
        List<Shelves.Shelf> list = new ArrayList<>();
        List<Catalog.Title> cont = continuing();
        if (!cont.isEmpty()) list.add(new Shelves.Shelf("Continue watching", items(cont, 40)));
        List<Catalog.Title> mine = mine();
        if (!mine.isEmpty()) list.add(new Shelves.Shelf("My list", items(mine, 60)));
        List<Catalog.Title> all = all();
        if (all != madeFrom) {
            madeFrom = all;
            categoryShelves.clear();
            List<Catalog.Title> recent = cat.recent(series, 60);
            if (!recent.isEmpty()) categoryShelves.add(new Shelves.Shelf("Recently added", items(recent, 60)));
            for (Catalog.Category c : cat.categories(series)) categoryShelves.add(new Shelves.Shelf(c.name, items(c.titles, ROW_MAX)));
        }
        list.addAll(categoryShelves);
        shelves.set(list);

        // The panel's lists.
        List<SidePanel.Entry> entries = new ArrayList<>();
        List<Catalog.Category> cats = cat.categories(series);
        entries.add(new SidePanel.Entry("All categories", Dark.GRID, cats.size(), WALLS));
        entries.add(new SidePanel.Entry(series ? "All TV shows" : "All films", Dark.LIST, all.size(), ALL));
        entries.add(new SidePanel.Entry("Continue watching", Dark.RESUME, cont.size(), CONTINUE));
        entries.add(SidePanel.Entry.section("Your lists"));
        entries.add(new SidePanel.Entry("My list", Dark.HEART, mine.size(), MINE));
        if (!cats.isEmpty()) entries.add(SidePanel.Entry.section("Categories"));
        for (Catalog.Category c : cats) entries.add(new SidePanel.Entry(c.name, -1, c.titles.size(), "cat:" + c.name));
        panel.set(series ? "TV shows" : "Films", entries, 0);
        panel.choose(showing.equals(WALLS) || showing.equals(ALL) || showing.equals(CONTINUE) || showing.equals(MINE) ? showing : "cat:" + showing);
        if (!showing.equals(WALLS)) showList(showing, true);

        String msg = "";
        if (list.isEmpty()) {
            if (Sources.list(tv).isEmpty()) msg = "Add your playlist in Settings: its " + (series ? "TV shows" : "films") + " show here.";
            else if (cat.busy() || Library.get(tv).busy()) msg = "Loading your playlist's " + (series ? "TV shows" : "films") + "…";
            else if (!cat.status().isEmpty()) msg = cat.status();
            else msg = "Your playlist has no " + (series ? "TV shows" : "films") + ".";
        }
        message.set(series ? "TV shows" : "Films", msg);
        showHero();
        place();
    }

    /** A list in the grid, by what the panel calls it. */
    private void showList(String what, boolean keep) {
        String name;
        List<Catalog.Title> titles;
        String empty = "";
        switch (what) {
            case ALL:
                name = series ? "All TV shows" : "All films";
                titles = all();
                break;
            case CONTINUE:
                name = "Continue watching";
                titles = continuing();
                empty = "Nothing part-watched yet: what you start shows here, to carry on from where you left it.";
                break;
            case MINE:
                name = "My list";
                titles = mine();
                empty = "Nothing on your list yet. Open a title and choose the heart to keep it here.";
                break;
            default: {
                name = what;
                titles = new ArrayList<>();
                for (Catalog.Category c : Catalog.get(tv).categories(series)) if (c.name.equals(what)) titles = c.titles;
                break;
            }
        }
        grid.set(name, items(titles, Integer.MAX_VALUE), empty, keep);
    }

    /** The panel's choice shown (a moment after the remote stops on it). */
    private void showChosen() {
        SidePanel.Entry e = panel.chosen();
        if (e == null || e.tag == null) return;
        String tag = (String) e.tag;
        String what = tag.startsWith("cat:") ? tag.substring(4) : tag;
        if (what.equals(showing)) return;
        showing = what;
        if (!what.equals(WALLS)) showList(what, false);
        place();
        showHero();
    }

    /** Which views show for where the remote is and what is shown. */
    private void place() {
        boolean walls = showing.equals(WALLS);
        billboard.setVisibility(walls ? View.VISIBLE : View.GONE);
        shelves.setVisibility(walls ? View.VISIBLE : View.GONE);
        grid.setVisibility(walls ? View.GONE : View.VISIBLE);
        boolean panelOut = !walls || area == PANEL || area == RAIL && !walls;
        panel.setVisibility(panelOut || area == PANEL ? View.VISIBLE : View.GONE);
        // Over the rows, the panel lies on a shade; beside the grid it is part of the page.
        shade.setVisibility(walls && area == PANEL ? View.VISIBLE : View.GONE);
        message.setVisibility(walls ? View.VISIBLE : View.GONE);
        rail.setActive(area == RAIL);
        panel.setActive(area == PANEL);
        shelves.setActive(area == CONTENT && walls);
        grid.setActive(area == CONTENT && !walls);
    }

    private void showHero() {
        if (!showing.equals(WALLS)) {
            h.removeCallbacks(fetch);
            return;
        }
        PosterArt.Item it = shelves.item();
        h.removeCallbacks(fetch);
        if (it == null) {
            billboard.clear();
            return;
        }
        Catalog.Title t = it.title;
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        billboard.show(t, d, note(t));
        pending = d == null ? t : null;
        if (pending != null) h.postDelayed(fetch, t.series ? 900 : 500);
    }

    /** "S2 E3 · 21m left", "52m left" for what was left part-way; "" otherwise. */
    private String note(Catalog.Title t) {
        Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        if (p == null || p.position <= 0) return "";
        String left = Ui.left(p.duration - p.position);
        if (!t.series) return left;
        String s = p.subtitle.isEmpty() ? "" : p.subtitle.split("  ·  ")[0];
        return s.isEmpty() ? left : s + " · " + left;
    }

    private void fetchDetails() {
        Catalog.Title want = pending;
        if (want == null) return;
        Catalog.get(tv).details(want, d -> {
            PosterArt.Item it = shelves.item();
            if (it != null && it.title == want && showing.equals(WALLS)) billboard.show(want, d, note(want));
        });
    }

    // ---- the remote -------------------------------------------------------------------------------------------

    private void to(int a) {
        area = a;
        place();
    }

    @Override boolean key(int code, KeyEvent e) {
        switch (area) {
            case RAIL:
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    to(showing.equals(WALLS) && !shelves.isEmpty() ? CONTENT : PANEL);
                    return true;
                }
                return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
            case PANEL:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    to(RAIL);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    toContent();
                    return true;
                }
                if (panel.key(code)) {
                    h.removeCallbacks(switchList);
                    h.postDelayed(switchList, 220);
                    return true;
                }
                return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
            default:
                if (showing.equals(WALLS)) {
                    if (shelves.isEmpty()) {
                        if (code == KeyEvent.KEYCODE_DPAD_LEFT) to(RAIL);
                        return code == KeyEvent.KEYCODE_DPAD_LEFT;
                    }
                    return shelves.key(code);
                }
                return grid.key(code);
        }
    }

    private void toContent() {
        h.removeCallbacks(switchList);
        showChosen();
        if (showing.equals(WALLS) && shelves.isEmpty()) return;
        to(CONTENT);
        showHero();
    }

    @Override void ok() {
        switch (area) {
            case RAIL:
                Rail.go(tv, rail.focus, rail.page);
                break;
            case PANEL:
                toContent();
                break;
            default:
                if (showing.equals(WALLS)) shelves.ok();
                else grid.ok();
                break;
        }
    }

    @Override boolean back() {
        if (area == RAIL) {
            to(showing.equals(WALLS) ? CONTENT : PANEL);
            return true;
        }
        if (area == CONTENT && !showing.equals(WALLS)) {
            to(PANEL);
            return true;
        }
        if (area == PANEL && showing.equals(WALLS) && !shelves.isEmpty()) {
            to(CONTENT);
            return true;
        }
        return false;
    }

    @Override public void focused(Shelves.Shelf row, PosterArt.Item item) {
        showHero();
    }

    @Override public void chosen(Shelves.Shelf row, PosterArt.Item item) {
        tv.openTitle(item.title);
    }

    @Override public void focused(PosterArt.Item item) {}

    @Override public void chosen(PosterArt.Item item) {
        tv.openTitle(item.title);
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.LEFT) {
            panel.choose(showing.equals(WALLS) || showing.equals(ALL) || showing.equals(CONTINUE) || showing.equals(MINE) ? showing : "cat:" + showing);
            to(PANEL);
            return true;
        }
        return false;
    }

    /** The page's name and a line when there is nothing to show. */
    static final class Message extends View {
        private String title = "", text = "";
        private final TextPaint titleP = Dark.text(52, 600, Dark.TEXT), textP = Dark.text(30, 400, Dark.SOFT);

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
            c.drawText(title, Ui.f(170), Ui.f(150), titleP);
            StaticLayout l = Ui.layout(text, textP, Ui.px(1100), 4);
            Ui.draw(c, l, Ui.f(170), Ui.f(200));
        }
    }
}
