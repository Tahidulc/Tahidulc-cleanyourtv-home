package com.gathin.homefieldtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Sources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Movies, or TV shows, as the family's other app has them: the rail down the left, then the categories' column
 * (GroupsPanel: My list and Continue watching in bold, then the playlists' heading with All movies, Your TorBox (the
 * family's own titles no playlist has), Recently added and every category, the remote's row held at one height),
 * and the list the remote is on as a grid of posters (PosterGrid) under the big words of the poster the remote is on
 * (VodHero: its title, rating, year, length and genres, cast, director and story) with its picture on the right
 * fading into the ground. Moving down the column shows each list at once. Right (or OK) goes into the posters: the
 * column goes and the posters slide to the left edge, and the search and sort buttons show at the top right; Left
 * from a row's first poster brings the column back. OK on a poster opens its page. Back goes from the posters to the
 * column, then to the menu, then back. An empty list says so with the clapperboard where the other app has it.
 *
 * <p>Sort (the top right's second button): as the playlist has them, by name, newest first or best rated, sorted off
 * the main thread. Nothing here goes through every title on the main thread: the lists are the catalogue's own, and
 * each poster is made only as it is drawn (PosterArt.Items).
 */
final class BrowseScreen extends Screen implements GroupsPanel.Host, PosterGrid.Host, RightPanel.Host {
    private static final String MINE = "mine", CONTINUE = "continue", ALL = "all", RECENT = "recent", HEAD = "head";
    private static final int RAIL = 0, COLUMN = 1, GRID = 2, BUTTONS = 3;
    /** Content's left edge with the column out and with it gone (design pixels). */
    static final float WITH_COLUMN = 672f, WITHOUT = 56f;
    private static final long SLIDE_MS = 150;
    static final String[] SORTS = {"As in the playlist", "By name", "Newest first", "Best rated"};
    /** The list each kind was last on, opened on it again (by tag). */
    private static String lastFilms, lastShows;

    private final boolean series;
    private Rail rail;
    private GroupsPanel column;
    private VodHero hero;
    private Backdrop picture;
    private PosterGrid grid;
    private Overlay overlay;
    private RightPanel sortPanel;
    private int area = COLUMN, button;
    /** The list shown (a tag), its sort, and the sorting asked last (an older answer is let go). */
    private String showing = "";
    private int sort, sortAsk;
    private float contentX = WITH_COLUMN;
    private ValueAnimator slide;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title pending;
    private final Runnable fetch = this::fetchDetails;
    private List<Catalog.Title> madeFrom;

    BrowseScreen(TvActivity tv, boolean series) {
        super(tv);
        this.series = series;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Palette.GROUND);
        picture = new Backdrop(tv, 800f, 0f, 1120f, 640f, Pics.DARK);
        hero = new VodHero(tv);
        grid = new PosterGrid(tv);
        grid.setHost(this);
        overlay = new Overlay(tv);
        column = new GroupsPanel(tv);
        column.x = Rail.W;
        column.setHost(this);
        rail = new Rail(tv, series ? Rail.SHOWS : Rail.FILMS);
        sortPanel = new RightPanel(tv);
        sortPanel.setHost(this);
        sortPanel.setVisibility(View.GONE);
        f.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        f.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        f.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        f.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        f.addView(column, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        f.addView(sortPanel, new FrameLayout.LayoutParams(-1, -1));
        fill();
        place(true);
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
        rail.invalidate();
    }

    @Override void catalogChanged() {
        fill();
    }

    // ---- what it shows ------------------------------------------------------------------------------------------

    private String kind() {
        return series ? "TV shows" : "movies";
    }

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
        if (d != null) it.backdrop = d.backdrop;
        Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        if (p != null && p.position > 0) it.progress = p.progress();
        return it;
    }

    /** A list's titles as posters, each made as it is first drawn or reached (PosterArt.Items), never all at once. */
    private List<PosterArt.Item> items(List<Catalog.Title> titles) {
        return new PosterArt.Items(titles, this::item);
    }

    /** The column's rows: the lists of the family's own, then the playlists' heading and their lists. */
    private void fill() {
        if (column == null) return;
        Catalog cat = Catalog.get(tv);
        List<GroupsPanel.Entry> entries = new ArrayList<>();
        entries.add(GroupsPanel.Entry.bold("My list", MINE));
        entries.add(GroupsPanel.Entry.bold("Continue watching", CONTINUE));
        entries.add(GroupsPanel.Entry.heading(playlistsName(), HEAD));
        entries.add(GroupsPanel.Entry.group(series ? "All TV shows" : "All movies", ALL));
        List<Catalog.Category> cats = cat.categories(series);
        Catalog.Category own = null;
        for (Catalog.Category c : cats) if (c.name.equals(Catalog.TORBOX)) own = c;
        if (own != null) entries.add(GroupsPanel.Entry.group(own.name, "cat:" + own.name));
        entries.add(GroupsPanel.Entry.group("Recently added", RECENT));
        for (Catalog.Category c : cats) if (c != own) entries.add(GroupsPanel.Entry.group(c.name, "cat:" + c.name));
        if (showing.isEmpty()) {
            String last = series ? lastShows : lastFilms;
            showing = last != null ? last : !continuing().isEmpty() ? CONTINUE : ALL;
        }
        int at = 0;
        for (int i = 0; i < entries.size(); i++) if (showing.equals(entries.get(i).tag)) at = i;
        column.set(entries, at);
        column.choose(showing);
        List<Catalog.Title> now = all();
        boolean changed = now != madeFrom;
        madeFrom = now;
        show(showing, !changed);
    }

    /** The playlists' heading: the one in use's name, or All playlists. */
    private String playlistsName() {
        List<Sources.Entry> in = Sources.inUse(tv);
        return in.size() == 1 ? in.get(0).name : "All playlists";
    }

    /** A list shown in the grid, by its tag (sorted as asked, off the main thread when it must be). */
    private void show(String what, boolean keep) {
        String name;
        List<Catalog.Title> titles;
        switch (what) {
            case ALL:
                name = series ? "All TV shows" : "All movies";
                titles = all();
                break;
            case CONTINUE:
                name = "Continue watching";
                titles = continuing();
                break;
            case MINE:
                name = "My list";
                titles = mine();
                break;
            case RECENT:
                name = "Recently added";
                titles = Catalog.get(tv).recent(series, 60);
                break;
            default: {
                name = what.startsWith("cat:") ? what.substring(4) : what;
                titles = Collections.emptyList();
                for (Catalog.Category c : Catalog.get(tv).categories(series)) if (c.name.equals(name)) titles = c.titles;
                break;
            }
        }
        if (sort == 0) {
            grid.set(name, items(titles), keep);
            showHero();
        } else {
            sorted(name, titles, keep);
        }
        overlay.invalidate();
    }

    /** The list sorted as asked off the main thread, then shown (an answer to an older ask let go). */
    private void sorted(String name, List<Catalog.Title> titles, boolean keep) {
        int ask = ++sortAsk, how = sort;
        Exec.work(() -> {
            List<Catalog.Title> copy = new ArrayList<>(titles);
            copy.sort(order(how));
            Runnable done = () -> {
                if (ask != sortAsk) return;
                grid.set(name, items(copy), keep);
                showHero();
                overlay.invalidate();
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    /** Pure: a sort's order: by name, the newest added first, or the best rated first (the playlist's order kept on ties). */
    static Comparator<Catalog.Title> order(int how) {
        switch (how) {
            case 1: return (a, b) -> a.name.compareToIgnoreCase(b.name);
            case 2: return (a, b) -> Long.compare(b.added, a.added);
            case 3: return (a, b) -> Float.compare(Billboard.rating(b.rating), Billboard.rating(a.rating));
            default: return (a, b) -> 0;
        }
    }

    // ---- where things are --------------------------------------------------------------------------------------

    /** The views for where the remote is: the column out or gone, the posters and words beside it. */
    private void place(boolean now) {
        boolean columnOut = area != GRID && area != BUTTONS;
        rail.setActive(area == RAIL);
        column.setActive(area == COLUMN);
        column.setVisibility(columnOut ? View.VISIBLE : View.GONE);
        // In the posters the menu's rail goes too, as the other app's does: the posters from the screen's edge.
        rail.setVisibility(columnOut ? View.VISIBLE : View.GONE);
        grid.setActive(area == GRID);
        slideTo(columnOut ? WITH_COLUMN : WITHOUT, now);
        overlay.invalidate();
    }

    private void slideTo(float x, boolean now) {
        if (slide != null) slide.cancel();
        if (now || Exec.inline || contentX == x) {
            contentX = x;
            moved();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(contentX, x);
        a.setDuration(SLIDE_MS);
        a.setInterpolator(Fade.EASE);
        a.addUpdateListener(v -> {
            contentX = (Float) v.getAnimatedValue();
            moved();
        });
        slide = a;
        a.start();
    }

    private void moved() {
        hero.moveTo(contentX);
        hero.w = Math.min(860f, 1420f - contentX);
        grid.moveTo(contentX - 4f);
        overlay.invalidate();
    }

    private void to(int a) {
        area = a;
        place(false);
        if (a == COLUMN) column.slideIn();
    }

    // ---- the words over the posters -----------------------------------------------------------------------------

    private void showHero() {
        PosterArt.Item it = grid.item();
        h.removeCallbacks(fetch);
        if (it == null) {
            hero.clear();
            picture.set("");
            return;
        }
        Catalog.Title t = it.title;
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        hero.show(t, d, t.torbox() ? Catalog.TORBOX : "");
        picture.set(d != null && !d.backdrop.isEmpty() ? d.backdrop : t.backdrop);
        pending = d == null ? t : null;
        if (pending != null) h.postDelayed(fetch, t.series ? 900 : 500);
    }

    private void fetchDetails() {
        Catalog.Title want = pending;
        if (want == null) return;
        Catalog.get(tv).details(want, d -> {
            PosterArt.Item it = grid.item();
            if (it != null && it.title == want) {
                hero.show(want, d, want.torbox() ? Catalog.TORBOX : "");
                if (d != null && !d.backdrop.isEmpty()) picture.set(d.backdrop);
            }
        });
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (sortPanel.getVisibility() == View.VISIBLE) return sortPanel.key(code);
        switch (area) {
            case RAIL:
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    to(COLUMN);
                    return true;
                }
                return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT;
            case COLUMN:
                return column.key(code);
            case BUTTONS:
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    button = code == KeyEvent.KEYCODE_DPAD_LEFT ? 0 : 1;
                    overlay.invalidate();
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    to(GRID);
                }
                return true;
            default:
                return grid.key(code);
        }
    }

    @Override void ok() {
        if (sortPanel.getVisibility() == View.VISIBLE) {
            sortPanel.ok();
            return;
        }
        switch (area) {
            case RAIL:
                if (rail.focus == rail.page) to(COLUMN);
                else Rail.go(tv, rail.focus, rail.page);
                break;
            case COLUMN:
                column.ok();
                break;
            case BUTTONS:
                if (button == 0) tv.openSearch();
                else openSort();
                break;
            default:
                grid.ok();
                break;
        }
    }

    @Override boolean back() {
        if (sortPanel.getVisibility() == View.VISIBLE) {
            sortPanel.close(() -> sortPanel.setVisibility(View.GONE));
            return true;
        }
        switch (area) {
            case GRID:
            case BUTTONS:
                to(COLUMN);
                return true;
            case COLUMN:
                to(RAIL);
                return true;
            default:
                return false;
        }
    }

    // ---- the column ---------------------------------------------------------------------------------------------

    @Override public void focused(GroupsPanel panel, GroupsPanel.Entry entry) {
        if (entry.kind == GroupsPanel.HEADING || !(entry.tag instanceof String)) return;
        // The list the remote is on, at once.
        showing = (String) entry.tag;
        if (series) lastShows = showing;
        else lastFilms = showing;
        column.choose(showing);
        show(showing, false);
    }

    @Override public void chosen(GroupsPanel panel, GroupsPanel.Entry entry) {
        if (!grid.isEmpty()) to(GRID);
    }

    @Override public boolean edge(GroupsPanel panel, int dir) {
        if (dir == Ui.LEFT) to(RAIL);
        else if (dir == Ui.RIGHT && !grid.isEmpty()) to(GRID);
        return true;
    }

    // ---- the posters ----------------------------------------------------------------------------------------------

    @Override public void focused(PosterArt.Item item) {
        showHero();
    }

    @Override public void chosen(PosterArt.Item item) {
        tv.openTitle(item.title);
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.LEFT) {
            to(COLUMN);
            return true;
        }
        if (dir == Ui.UP) {
            area = BUTTONS;
            button = 0;
            place(false);
            return true;
        }
        return false;
    }

    // ---- sort -----------------------------------------------------------------------------------------------------

    private void openSort() {
        List<RowList.Row> rows = new ArrayList<>();
        for (int i = 0; i < SORTS.length; i++) rows.add(RowList.Row.radio(i, SORTS[i], i == sort));
        sortPanel.setVisibility(View.VISIBLE);
        sortPanel.open("Sort", null, rows, sort, true);
    }

    @Override public void chosen(RightPanel p, RowList.Row row) {
        int how = row.tag instanceof Integer ? (Integer) row.tag : 0;
        sortPanel.close(() -> sortPanel.setVisibility(View.GONE));
        if (how == sort) return;
        sort = how;
        show(showing, false);
    }

    @Override public void focused(RightPanel p, RowList.Row row) {}

    @Override public boolean edge(RightPanel p, int dir) {
        return true;
    }

    // ---- what is drawn over the posters ---------------------------------------------------------------------------

    /** The search and sort buttons at the top right (with the posters), and an empty list's clapperboard. */
    private final class Overlay extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Overlay(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            if (grid.isEmpty()) {
                drawEmpty(c);
                return;
            }
            if (area != GRID && area != BUTTONS) return;
            // A pill with search and sort, the other app's [1728,40][1872,112].
            r.set(Ui.f(1728), Ui.f(40), Ui.f(1872), Ui.f(112));
            fill.setColor(Palette.SELECTED);
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
            float[] cx = {1764f, 1836f};
            for (int i = 0; i < 2; i++) {
                if (area == BUTTONS && button == i) Focus.circle(c, Ui.f(cx[i]), Ui.f(76), Ui.f(64));
                int colour = Palette.TEXT1;
                if (i == 0) RoundButton.glyph(c, Dark.SEARCH, Ui.f(cx[i]), Ui.f(76), Ui.f(34), colour);
                else Glyphs.draw(c, Glyphs.SORT, Ui.f(cx[i]), Ui.f(76), Ui.f(34), colour);
            }
        }

        private void drawEmpty(Canvas c) {
            String why = "";
            Catalog cat = Catalog.get(tv);
            if (Sources.list(tv).isEmpty()) why = "Add your playlist in Settings: its " + kind() + " show here.";
            else if (all().isEmpty() && (cat.busy() || Library.get(tv).busy())) why = "Loading your playlist's " + kind() + "…";
            else if (all().isEmpty() && !cat.status().isEmpty()) why = cat.status();
            else if (showing.equals(MINE)) why = "Open a title and choose My list to keep it here.";
            else if (showing.equals(CONTINUE)) why = "What you start watching shows here, to carry on from where you left it.";
            r.set(Ui.f(1232), 0, Ui.f(1920), getHeight());
            EmptyState.draw(c, r, Dark.CLAPPER, 120f, series ? "No TV shows" : "No movies", why);
        }
    }

    /** Previews and tests: the list shown, by its column's tag ("all", "cat:Drama"), the remote in its posters. */
    void showList(String tag) {
        showing = tag;
        fill();
        if (!grid.isEmpty()) {
            area = GRID;
            place(true);
        }
    }

    /** Previews: where the remote is in the posters (its place in the list). */
    void focusPoster(int index) {
        for (int i = 0; i < index; i++) grid.key(KeyEvent.KEYCODE_DPAD_RIGHT);
    }
}
