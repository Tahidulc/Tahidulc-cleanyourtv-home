package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CategorySet;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.prefs.HomeStyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Movies, or TV Shows, as 07 pages under the tab bar. Settings › Appearance › Home style: Editorial (as it comes) hands
 * the page to EditorialPage (the carousel and rows of CategorySet.MOVIES or SHOWS); Classic is this page's own (homefield-07-real 09 and 11; the page itself is CineRows): the
 * playlists' lists as pills (All movies, Recently added, Your TorBox, then each category) with the sort at the right
 * (as in the playlist, by name, newest first, best rated), the title the remote is on in a big card (its logo, facts,
 * story, cast and director), then the chosen list as a shelf of posters (a series' as wide cards), then Continue
 * watching with how far in, and My List. A long list ends in a card that opens all of it (ListScreen). OK on a poster
 * opens its page; on a Continue watching card it carries on at once. Up from the first shelf goes to the pills, and from
 * them to the tab bar; Back goes to the pills, then the tab bar.
 *
 * <p>Nothing here goes through every title on the main thread: the shelves are the catalogue's own lists, cut to their
 * first few, and a long list is sorted off the main thread.
 */
final class BrowseScreen extends Screen implements CineRows.Host {
    static final String MINE = "mine", CONTINUE = "continue", RECENT = "recent", ALL = "all";
    /** How many of a list its shelf shows before its "All" card. */
    static final int ROW_MAX = 40;
    static final String[] SORTS = {"As in the playlist", "By name", "Newest first", "Best rated"};
    /** A list this long is sorted off the main thread. */
    static final int SORT_INLINE = 400;
    /** My list's page (MyListScreen) still slides its content by these (design pixels). */
    static final float WITH_COLUMN = 672f, WITHOUT = 56f;

    private final boolean series;
    private CineRows rows;
    /** Home style Editorial: the page (null for Classic). */
    private EditorialPage editorial;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title pending, extrasFor;
    private final Runnable fetch = this::fetchDetails, extras = this::fetchExtras;
    /** The pills' lists (their tags: ALL, RECENT, "cat:Drama"), the one shown, the sort. */
    private final List<String> pillNames = new ArrayList<>();
    private final List<Object> pillTags = new ArrayList<>();
    private Object chosen = RECENT;
    private int sort = 2, sortAsk;
    /** The chosen list as sorted (null while it is being sorted). */
    private List<Catalog.Title> sorted;
    private Object sortedFor;
    private int sortedHow = -1;
    /** TMDB's extras of the titles the remote has rested on (read once, kept here). */
    private final Map<String, Tmdb.Extras> extrasKnown = new HashMap<>();

    BrowseScreen(TvActivity tv, boolean series) {
        super(tv);
        this.series = series;
    }

    /** Its page draws an opaque ground over the whole screen (the window's own isn't drawn under it). */
    @Override boolean opaque() {
        return true;
    }

    @Override View build() {
        Cine.init(tv);
        PosterArt.init(tv);
        if (HomeStyle.editorial(tv)) {
            editorial = new EditorialPage(tv, series ? CategorySet.SHOWS : CategorySet.MOVIES);
            return editorial.build();
        }
        FrameLayout f = new FrameLayout(tv);
        // No background of its own: its page draws its opaque ground first (a full screen of fill saved each frame).
        rows = new CineRows(tv);
        rows.setHost(this);
        f.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        fill();
        return f;
    }

    @Override void shown() {
        // No live picture here: the stream let go (a film may want the provider's one connection).
        tv.live.stop();
        tv.live.place(null);
        if (editorial != null) {
            editorial.shown();
            return;
        }
        if (rows != null) rows.setActive(tv.tabs() == null || !tv.tabs().active());
        fill();
    }

    @Override void hidden() {
        h.removeCallbacks(fetch);
        h.removeCallbacks(extras);
        if (editorial != null) editorial.hidden();
    }

    /** Home style Editorial's page (null for Classic). */
    EditorialPage editorial() {
        return editorial;
    }

    @Override void catalogChanged() {
        if (editorial != null) {
            editorial.catalogChanged();
            return;
        }
        sorted = null;
        sortedFor = null;
        fill();
    }

    @Override boolean shell() {
        return true;
    }

    @Override int tab() {
        return series ? TabBar.SHOWS : TabBar.MOVIES;
    }

    /** Down from the tab bar: the pills. */
    @Override void fromTabs() {
        if (editorial != null) {
            editorial.fromTabs();
            return;
        }
        if (rows != null) {
            rows.setActive(true);
            rows.fromAbove();
        }
    }

    // ---- what it shows ------------------------------------------------------------------------------------------

    private String kind() {
        return series ? "TV shows" : "movies";
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

    /** Pure: a Continue watching card's line: "S2 E4 · 32m left", "32m left", "S2 E4", or "" (subtitle "S2 E4 · Pilot"). */
    static String resumeLine(String subtitle, long position, long duration) {
        String code = "";
        if (subtitle != null && !subtitle.isEmpty()) {
            int dot = subtitle.indexOf(" · ");
            code = (dot > 0 ? subtitle.substring(0, dot) : subtitle).trim();
        }
        String left = "";
        if (duration > 0 && position > 0 && position < duration) {
            long mins = Math.max(1, Math.round((duration - position) / 60_000.0));
            left = mins >= 60 ? (mins / 60) + "h " + (mins % 60) + "m left" : mins + "m left";
        }
        if (code.isEmpty()) return left;
        return left.isEmpty() ? code : code + " · " + left;
    }

    /** Wide cards for a series' shelf (always, as the mock-ups have them) or with Settings → Posters: Landscape. */
    private int shelfKind() {
        return series || PosterArt.landscape ? CineRows.WIDE : CineRows.POSTERS;
    }

    /** A title's card on a shelf: its poster (its wide picture on a wide shelf), NEW, its picture's quality. */
    private CineRows.Tile card(Catalog.Title t, int kind, long now) {
        String pic = t.poster;
        Catalog.Details d = null;
        if (kind == CineRows.WIDE) {
            d = Catalog.get(tv).cachedDetails(t);
            pic = d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
        }
        CineRows.Tile tile = new CineRows.Tile(t, pic, kind == CineRows.WIDE ? seasonsLine(t, d) : "", -1f, t);
        boolean fresh = !PosterArt.badge(t.added, now).isEmpty();
        tile.badge = fresh ? t.series ? "NEW EPISODES" : "NEW" : "";
        tile.quality = PosterArt.chip(t.bestPicture());
        return tile;
    }

    /** A series' card's line: its seasons where its page has been read, else its year and genre. */
    private static String seasonsLine(Catalog.Title t, Catalog.Details d) {
        if (t.series && d != null && !d.seasons.isEmpty()) {
            Catalog.Season s = d.seasons.get(d.seasons.size() - 1);
            String eps = s.episodes.isEmpty() ? "" : " · " + s.episodes.size() + (s.episodes.size() == 1 ? " episode" : " episodes");
            return d.seasons.size() == 1 ? "Limited series" + eps : "Season " + s.number + eps;
        }
        String g = PosterArt.genreOf(t.category);
        return t.year.isEmpty() ? g : g.isEmpty() ? t.year : t.year + " · " + g;
    }

    /** A Continue watching card: the wide picture (the episode's, where it was left), where it was and how far in. */
    private CineRows.Tile resumeTile(Catalog.Title t) {
        Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        String pic = d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
        if (p != null && t.series && !p.picture.isEmpty()) pic = p.picture;
        String line = p == null ? "" : resumeLine(t.series ? p.subtitle : "", p.position, p.duration);
        CineRows.Tile tile = new CineRows.Tile(t, pic, line, p != null && p.position > 0 ? p.progress() : -1f, p);
        tile.quality = PosterArt.chip(t.bestPicture());
        return tile;
    }

    private List<CineRows.Tile> cards(List<Catalog.Title> titles, int kind) {
        long now = System.currentTimeMillis();
        int n = Math.min(ROW_MAX, titles.size());
        List<CineRows.Tile> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(card(titles.get(i), kind, now));
        return out;
    }

    /** The pills: All, Recently added, Your TorBox, then each category (the shown one kept when it is still there). */
    private void pills() {
        Catalog cat = Catalog.get(tv);
        pillNames.clear();
        pillTags.clear();
        pillNames.add(series ? "All TV shows" : "All movies");
        pillTags.add(ALL);
        pillNames.add(series ? "New episodes" : "Recently added");
        pillTags.add(RECENT);
        List<Catalog.Category> cats = cat.categories(series);
        for (Catalog.Category c : cats) {
            if (c.name.equals(Catalog.TORBOX) && !c.titles.isEmpty()) {
                pillNames.add(c.name);
                pillTags.add("cat:" + c.name);
            }
        }
        for (Catalog.Category c : cats) {
            if (c.name.equals(Catalog.TORBOX) || c.titles.isEmpty()) continue;
            pillNames.add(c.name);
            pillTags.add("cat:" + c.name);
        }
        if (!pillTags.contains(chosen)) chosen = RECENT;
    }

    /** The page: the pills, the chosen list's shelf (sorted), Continue watching, My List. */
    private void fill() {
        if (rows == null) return;
        pills();
        List<Catalog.Title> list = listOf(chosen);
        List<Catalog.Title> shown = order(list);
        List<CineRows.Row> out = new ArrayList<>();
        int kind = shelfKind();
        if (!shown.isEmpty()) {
            CineRows.Row r = new CineRows.Row(pillNames.get(pillTags.indexOf(chosen)), kind, cards(shown, kind), shown.size(), chosen);
            r.quiet = quiet(chosen, shown);
            out.add(r);
        }
        List<Catalog.Title> going = continuing();
        if (!going.isEmpty()) {
            List<CineRows.Tile> tiles = new ArrayList<>();
            for (int i = 0; i < Math.min(ROW_MAX, going.size()); i++) tiles.add(resumeTile(going.get(i)));
            CineRows.Row r = new CineRows.Row("Continue watching", CineRows.WIDE, tiles, going.size(), CONTINUE);
            r.quiet = going.size() + " in progress";
            out.add(r);
        }
        List<Catalog.Title> mine = mine();
        if (!mine.isEmpty()) {
            CineRows.Row r = new CineRows.Row("My List", kind, cards(mine, kind), mine.size(), MINE);
            out.add(r);
        }
        rows.set(out);
        rows.pills(pillNames, pillTags.indexOf(chosen), SORTS[sort]);
        showHero();
    }

    /** A shelf's quieter words: where Recently added comes from ("In Home and Your TorBox"), how many a long list has. */
    private String quiet(Object tag, List<Catalog.Title> titles) {
        if (RECENT.equals(tag) || ALL.equals(tag)) {
            List<String> from = new ArrayList<>();
            for (Sources.Entry e : Sources.inUse(tv)) if (!e.name.isEmpty() && from.size() < 3) from.add(e.name);
            boolean own = false;
            for (int i = 0; i < Math.min(titles.size(), 60) && !own; i++) own = titles.get(i).torbox();
            if (own) from.add(Catalog.TORBOX);
            if (from.isEmpty()) return "";
            StringBuilder b = new StringBuilder("In ");
            for (int i = 0; i < from.size(); i++) {
                if (i > 0) b.append(i == from.size() - 1 ? " and " : ", ");
                b.append(from.get(i));
            }
            return b.toString();
        }
        return String.format(Locale.UK, "%,d", titles.size()) + (titles.size() == 1 ? " title" : " titles");
    }

    /**
     * The chosen list in the sort asked for: at once for a short one or the playlist's order, else sorted off the main
     * thread (the playlist's order shown meanwhile), the page made again when it is.
     */
    private List<Catalog.Title> order(List<Catalog.Title> list) {
        if (sort == 0 || RECENT.equals(chosen) && sort == 2) return list;
        if (sortedFor == chosen && sortedHow == sort && sorted != null) return sorted;
        if (list.size() <= SORT_INLINE) {
            List<Catalog.Title> copy = new ArrayList<>(list);
            copy.sort(order(sort));
            remember(copy);
            return copy;
        }
        int ask = ++sortAsk;
        Object tag = chosen;
        int how = sort;
        Exec.work(() -> {
            List<Catalog.Title> copy = new ArrayList<>(list);
            copy.sort(order(how));
            Runnable done = () -> {
                if (ask != sortAsk || tag != chosen || how != sort) return;
                remember(copy);
                fill();
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
        return list;
    }

    private void remember(List<Catalog.Title> copy) {
        sorted = copy;
        sortedFor = chosen;
        sortedHow = sort;
    }

    /** A list's titles by its tag ("all", "mine", "recent", "cat:Drama"). */
    private List<Catalog.Title> listOf(Object tag) {
        Catalog cat = Catalog.get(tv);
        if (ALL.equals(tag)) return series ? cat.series() : cat.films();
        if (MINE.equals(tag)) return mine();
        if (CONTINUE.equals(tag)) return continuing();
        if (RECENT.equals(tag)) return cat.recent(series, 120);
        String name = tag instanceof String && ((String) tag).startsWith("cat:") ? ((String) tag).substring(4) : "";
        for (Catalog.Category c : cat.categories(series)) if (c.name.equals(name)) return c.titles;
        return new ArrayList<>();
    }

    // ---- the big card --------------------------------------------------------------------------------------------

    private void showHero() {
        h.removeCallbacks(fetch);
        h.removeCallbacks(extras);
        CineRows.Tile t = rows.tile();
        CineRows.Row rw = rows.current();
        if (t == null) {
            if (rw != null && rw.focus >= rw.tiles.size()) rows.heroNote(rw.title, rw.allWords + "  ·  OK shows them all");
            else rows.heroNote("", emptyWhy());
            return;
        }
        Catalog.Details d = Catalog.get(tv).cachedDetails(t.title);
        Tmdb.Extras x = extrasKnown.get(t.title.key);
        boolean resume = rw != null && CONTINUE.equals(rw.tag);
        rows.hero(t.title, d, x, resume ? t.line : "", MyList.has(tv, t.title.key), play(t.title, d));
        pending = d == null ? t.title : null;
        if (pending != null) h.postDelayed(fetch, t.title.series ? 900 : 500);
        // TMDB's logo when the remote rests on it (kept ones at once).
        if (x == null) {
            Tmdb.Extras kept = Tmdb.keptExtras(tv, t.title);
            if (kept != null) {
                extrasKnown.put(t.title.key, kept);
                rows.hero(t.title, d, kept, resume ? t.line : "", MyList.has(tv, t.title.key), play(t.title, d));
            } else {
                extrasFor = t.title;
                h.postDelayed(extras, 700);
            }
        }
        readAhead();
    }

    /** The card's Play: "Resume S2 E3", "Play S1 E1", "Resume", "Play". */
    private String play(Catalog.Title t, Catalog.Details d) {
        Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        if (!t.series) return p != null && p.position > 0 ? "Resume" : "Play";
        Catalog.Episode e = d == null || p == null ? null : d.episode(p.episode);
        if (e != null) return (p.position > 0 ? "Resume S" : "Play S") + e.season + " E" + e.number;
        if (d != null && !d.episodes().isEmpty()) return "Play S" + d.episodes().get(0).season + " E" + d.episodes().get(0).number;
        return "Play";
    }

    /**
     * The big pictures a key away, along the shelf and in the shelves above and below, read after what is on screen
     * (Pics.prefetch), so a held key finds each there.
     */
    private void readAhead() {
        Catalog cat = Catalog.get(tv);
        ahead(cat, rows.near(0, 1));
        ahead(cat, rows.near(0, 2));
        ahead(cat, rows.near(0, -1));
        ahead(cat, rows.near(1, 0));
        ahead(cat, rows.near(-1, 0));
    }

    private void ahead(Catalog cat, CineRows.Tile t) {
        if (t == null || rows == null) return;
        String pic = heroPicture(t.title, cat.cachedDetails(t.title));
        if (pic.isEmpty()) return;
        Pics.prefetch(pic, 1240, 400, Pics.FACE);
        if (rows.getWidth() > 0) Pics.prefetch(pic, rows.getWidth(), rows.getHeight(), Pics.BLUR);
    }

    /** Pure: the big picture for a title (its details' backdrop, else its own, else its poster). */
    static String heroPicture(Catalog.Title t, Catalog.Details d) {
        return d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
    }

    private String emptyWhy() {
        Catalog cat = Catalog.get(tv);
        List<Catalog.Title> all = series ? cat.series() : cat.films();
        if (Sources.list(tv).isEmpty()) return "Add your playlist in Settings: its " + kind() + " show here.";
        if (all.isEmpty() && (cat.busy() || Library.get(tv).busy())) return "Loading your playlist's " + kind() + "…";
        if (all.isEmpty() && !cat.status().isEmpty()) return cat.status();
        return all.isEmpty() ? "No " + kind() + " in your playlists" : "";
    }

    private void fetchDetails() {
        Catalog.Title want = pending;
        if (want == null) return;
        Catalog.get(tv).details(want, d -> {
            CineRows.Tile t = rows.tile();
            if (t != null && t.title == want) showHero();
        });
    }

    private void fetchExtras() {
        Catalog.Title want = extrasFor;
        if (want == null) return;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.extras(tv, want);
            if (x != null) Exec.main(() -> {
                if (extrasKnown.size() > 200) extrasKnown.clear();
                extrasKnown.put(want.key, x);
                CineRows.Tile t = rows == null ? null : rows.tile();
                if (t != null && t.title == want) showHero();
            });
        });
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (editorial != null) return editorial.key(code);
        return rows.key(code);
    }

    @Override void ok() {
        if (editorial != null) editorial.ok();
        else rows.ok();
    }

    @Override void okLong() {
        if (editorial != null) editorial.okLong();
        else rows.ok();
    }

    @Override boolean back() {
        if (editorial != null) return editorial.back();
        if (rows.back()) return true;
        toBar();
        return true;
    }

    /** The remote up to the tab bar (nothing on the page lifted meanwhile). */
    private void toBar() {
        if (tv.tabs() == null) return;
        tv.tabs().enter();
        rows.setActive(false);
    }

    // ---- the shelves --------------------------------------------------------------------------------------------------

    @Override public void focused(CineRows.Row row, int index) {
        showHero();
    }

    @Override public void chosen(CineRows.Row row, int index) {
        if (index >= row.tiles.size()) {
            List<Catalog.Title> titles = row.tag == chosen ? order(listOf(row.tag)) : listOf(row.tag);
            if (!titles.isEmpty()) tv.push(new ListScreen(tv, series, row.title, titles));
            return;
        }
        Catalog.Title t = row.tiles.get(index).title;
        if (CONTINUE.equals(row.tag) && carryOn(t)) return;
        tv.openTitle(t);
    }

    /** A Continue watching card: on at once from where it was (an episode needs its series' page read; else its page). */
    private boolean carryOn(Catalog.Title t) {
        Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        if (p == null) return false;
        if (!t.series) {
            tv.play(t, null, p.position, false);
            return true;
        }
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        Catalog.Episode ep = d == null ? null : d.episode(p.episode);
        if (ep == null) return false;
        tv.play(t, ep, p.position, p.position <= 0);
        return true;
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.UP && tv.tabs() != null) {
            toBar();
            return true;
        }
        return false;
    }

    @Override public void pill(int index) {
        if (index < 0 || index >= pillTags.size() || pillTags.get(index).equals(chosen)) return;
        chosen = pillTags.get(index);
        fill();
    }

    @Override public void sort() {
        sort = (sort + 1) % SORTS.length;
        fill();
    }

    /** Previews and tests: the remote on a shelf and a card (shelf -1: on the pills, at pill index). */
    void focusRow(int row, int index) {
        rows.focus(row, index);
    }

    /** Previews and tests: a list shown by its pill's name. */
    void previewList(String pillName) {
        int i = pillNames.indexOf(pillName);
        if (i >= 0) pill(i);
    }

    /** Previews: TMDB's extras for a title as if it had answered. */
    void previewExtras(Catalog.Title t, Tmdb.Extras x) {
        extrasKnown.put(t.key, x);
        showHero();
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
}
