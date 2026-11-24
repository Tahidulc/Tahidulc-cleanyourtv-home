package com.gathin.homefieldtv;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CategorySet;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Shelves;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Editorial page of Home, Movies or TV Shows (Settings › Appearance › Home style: Editorial): its rows as CategorySet
 * lists them for the page, each made by Shelves and drawn by Editorial. HomeScreen and BrowseScreen hand it their
 * screen's work when Editorial is chosen.
 *
 * <p>Built lazily and in pages, so the main thread never goes through the catalogue: the rows from the family's own
 * lists (Continue watching, the newest, My List, TorBox, the categories, the moods) are made at once from the lists the
 * catalogue keeps; a TMDB row is made off the main thread and comes in when it is ready (a row with nothing in it never
 * does), the first few as the page opens and the next ones as the remote goes down to them; a TMDB row reads its next
 * page as the remote nears its end. What was made is kept (Shelves), so the page comes back at once. OK on a card opens
 * its page (Continue watching carries on; a mood or a category opens its titles); OK held puts it on My List or takes it
 * off; Up past the carousel, or Back there, goes up to the tab bar.
 */
final class EditorialPage implements Editorial.Host {
    /** The TMDB rows asked for as the page opens, and how far past the remote's row they are asked for. */
    static final int FIRST = 8, AHEAD = 6;

    private final TvActivity tv;
    private final String page;
    private final List<CategorySet.Entry> entries;
    private CategorySet.Entry heroEntry;
    private Editorial view;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Map<String, Shelves.Shelf> shelves = new HashMap<>();
    private final Map<String, Editorial.Row> made = new HashMap<>();
    private final Set<String> asked = new HashSet<>(), paging = new HashSet<>();
    private List<Editorial.Card> heroCards = new ArrayList<>();
    private Shelves.Shelf heroFrom;
    /** Raised when the catalogue changes: an answer for an older one is let go. */
    private int round;
    private Editorial.Card extrasFor, detailsFor;
    private final Runnable extras = this::fetchExtras, details = this::fetchDetails;
    /** TMDB's extras (the logo) of the carousel's titles, read once and kept here. */
    private final Map<String, Tmdb.Extras> extrasKnown = new HashMap<>();

    EditorialPage(TvActivity tv, String page) {
        this.tv = tv;
        this.page = page;
        List<CategorySet.Entry> all = CategorySet.of(page), rows = new ArrayList<>();
        for (CategorySet.Entry e : all) {
            if (e.kind == CategorySet.HERO) heroEntry = e;
            else rows.add(e);
        }
        entries = rows;
    }

    View build() {
        Cine.init(tv);
        PosterArt.init(tv);
        FrameLayout f = new FrameLayout(tv);
        view = new Editorial(tv);
        view.setHost(this);
        f.addView(view, new FrameLayout.LayoutParams(-1, -1));
        fill();
        return f;
    }

    Editorial view() {
        return view;
    }

    void shown() {
        if (view == null) return;
        view.setActive(tv.tabs() == null || !tv.tabs().active());
        // Continue watching and My List may have changed while a film played: made again (quick); old TMDB rows asked again.
        for (CategorySet.Entry e : entries) {
            if (!e.tmdb()) shelves.put(e.id, Shelves.local(tv, e));
            else if (asked.contains(e.id) && Shelves.stale(shelves.get(e.id))) {
                asked.remove(e.id);
                request(e);
            }
        }
        if (heroEntry != null && Shelves.stale(shelves.get(heroEntry.id)) && asked.remove(heroEntry.id)) request(heroEntry);
        publish();
    }

    void hidden() {
        h.removeCallbacks(extras);
        h.removeCallbacks(details);
    }

    void catalogChanged() {
        round++;
        Shelves.forget();
        shelves.clear();
        made.clear();
        asked.clear();
        paging.clear();
        heroFrom = null;
        fill();
    }

    // ---- the rows ---------------------------------------------------------------------------------------------------

    /** Every row as far as it can be made now: kept ones, the local ones, the carousel's local choice; TMDB's asked for. */
    private void fill() {
        if (view == null) return;
        List<CategorySet.Entry> all = new ArrayList<>(entries);
        if (heroEntry != null) all.add(0, heroEntry);
        for (CategorySet.Entry e : all) {
            Shelves.Shelf kept = Shelves.kept(tv, e);
            if (kept != null) {
                shelves.put(e.id, kept);
                if (e.tmdb()) {
                    asked.add(e.id);
                    if (Shelves.stale(kept)) {
                        asked.remove(e.id);
                        request(e);
                    }
                }
                continue;
            }
            // The family's own lists at once; the carousel's own choice too (the newest) while TMDB's trending is read.
            if (!e.tmdb() || e.kind == CategorySet.HERO) shelves.put(e.id, Shelves.local(tv, e));
        }
        if (heroEntry != null) request(heroEntry);
        ahead(-1);
        publish();
    }

    /** The TMDB rows from the first to AHEAD past the table's place of the remote's row (at least the first FIRST). */
    private void ahead(int at) {
        int until = Math.max(FIRST, at + AHEAD), n = 0;
        for (int i = 0; i < entries.size(); i++) {
            CategorySet.Entry e = entries.get(i);
            if (!e.tmdb()) continue;
            if (i > until && n >= FIRST) break;
            n++;
            request(e);
        }
    }

    /** A TMDB row made off the main thread, once; the page made again with it. */
    private void request(CategorySet.Entry e) {
        if (!asked.add(e.id)) return;
        int was = round;
        Exec.io(() -> {
            Shelves.Shelf s = Shelves.make(tv, e);
            Runnable done = () -> {
                if (was != round || view == null) return;
                shelves.put(e.id, s);
                publish();
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    private boolean publishing, again;

    /** The rows made (each from its shelf, made again only when its shelf is new) and given to the view in order. */
    private void publish() {
        if (view == null) return;
        if (publishing) {
            // Asked again while the view was being told (a preview's rows made at once): once more after this one.
            again = true;
            return;
        }
        publishing = true;
        try {
            do {
                again = false;
                publishOnce();
            } while (again);
        } finally {
            publishing = false;
        }
    }

    private void publishOnce() {
        List<Editorial.Row> rows = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (CategorySet.Entry e : entries) {
            Shelves.Shelf s = shelves.get(e.id);
            if (s == null || s.isEmpty()) continue;
            Editorial.Row r = made.get(e.id);
            if (r == null || r.from != s) {
                r = row(e, s, now);
                made.put(e.id, r);
            }
            rows.add(r);
        }
        Shelves.Shelf hs = heroEntry == null ? null : shelves.get(heroEntry.id);
        if (hs != heroFrom) {
            heroFrom = hs;
            heroCards = heroCards(hs);
        }
        view.set(heroCards, rows);
        if (rows.isEmpty() && heroCards.isEmpty()) view.note(emptyHead(), emptyWhy());
        else view.note("", "");
    }

    private Editorial.Row row(CategorySet.Entry e, Shelves.Shelf s, long now) {
        int kind = e.kind;
        if (kind == CategorySet.POSTERS && PosterArt.landscape) kind = CategorySet.WIDE;
        List<Editorial.Card> cards = new ArrayList<>(s.picks.size());
        for (int i = 0; i < s.picks.size(); i++) cards.add(card(s.picks.get(i), kind, i, now));
        Editorial.Row r = new Editorial.Row(e.id, s.title, kind, cards, s.total());
        r.more = s.more();
        r.from = s;
        if (e.source.startsWith("continue")) r.quiet = cards.size() == 1 ? "1 in progress" : cards.size() + " in progress";
        return r;
    }

    /** A row's card: its picture for the row's look, its words and marks. */
    private Editorial.Card card(Shelves.Pick p, int kind, int i, long now) {
        if (p.mood != null) {
            Editorial.Card c = new Editorial.Card(p, "");
            c.name = p.mood.name;
            c.from = p.mood.from;
            c.to = p.mood.to;
            c.tile = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.DITHER_FLAG);
            c.tile.setShader(new LinearGradient(0, 0, Ui.f(Editorial.TILE_W), Ui.f(Editorial.TILE_H), p.mood.from, p.mood.to,
                    Shader.TileMode.CLAMP));
            return c;
        }
        Catalog.Title t = p.title;
        if (p.category != null) {
            Catalog.Details d = Catalog.get(tv).cachedDetails(t);
            String pic = wide(p, d);
            Editorial.Card c = new Editorial.Card(p, pic);
            c.name = p.category.name;
            return c;
        }
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        String pic;
        if (kind == CategorySet.WIDE) {
            pic = p.point != null && t.series && !p.point.picture.isEmpty() ? p.point.picture : wide(p, d);
            if (pic.isEmpty() && p.point != null) pic = p.point.picture;
        } else {
            pic = p.poster();
        }
        Editorial.Card c = new Editorial.Card(p, pic);
        c.name = t.name;
        if (p.point != null) {
            c.line = BrowseScreen.resumeLine(t.series ? p.point.subtitle : "", p.point.position, p.point.duration);
            c.progress = p.point.position > 0 ? p.point.progress() : -1f;
        } else {
            String year = !t.year.isEmpty() ? t.year : p.item != null ? p.item.year : "";
            float r = Billboard.rating(!t.rating.isEmpty() ? t.rating : p.item != null ? p.item.rating : "");
            c.line = year + (r > 0f ? (year.isEmpty() ? "" : " · ") + String.format(Locale.UK, "★ %.1f", r) : "");
            if (c.line.isEmpty()) c.line = t.series ? "TV show" : "Film";
            c.badge = PosterArt.badge(t.added, now).isEmpty() ? "" : "NEW";
        }
        c.quality = PosterArt.chip(t.bestPicture());
        c.torbox = t.torbox();
        if (kind == CategorySet.TOP10) c.rank = i + 1;
        return c;
    }

    /** A title's wide picture: TMDB's backdrop, its page's, the playlist's, else its poster. */
    private static String wide(Shelves.Pick p, Catalog.Details d) {
        String b = p.backdrop();
        if (b.isEmpty() && d != null) b = d.backdrop;
        if (b.isEmpty() && p.title != null) b = p.title.poster;
        return b == null ? "" : b;
    }

    /** The carousel: the first ten of its row with a wide picture, each with its words. */
    private List<Editorial.Card> heroCards(Shelves.Shelf s) {
        List<Editorial.Card> out = new ArrayList<>();
        if (s == null) return out;
        for (Shelves.Pick p : s.picks) {
            if (p.title == null || out.size() >= 10) continue;
            Catalog.Details d = Catalog.get(tv).cachedDetails(p.title);
            String pic = wide(p, d);
            if (pic.isEmpty()) continue;
            Editorial.Card c = new Editorial.Card(p, pic);
            c.name = p.title.name;
            heroWords(c);
            out.add(c);
        }
        return out;
    }

    /** A carousel card's words: what it is, its button, My List, its story, its page's details and TMDB's logo where known. */
    private void heroWords(Editorial.Card c) {
        Catalog.Title t = c.title();
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        Tmdb.Extras x = extrasKnown.get(t.key);
        c.details = d;
        c.extras = x;
        c.logo = x == null ? "" : x.logo;
        c.kicker = t.torbox() ? (t.series ? "TV SHOW · " : "FILM · ") + Catalog.TORBOX.toUpperCase(Locale.UK) : t.series ? "TV SHOW" : "FILM";
        String plot = d != null && !d.plot.isEmpty() ? d.plot : !t.plot.isEmpty() ? t.plot : c.pick.item != null ? c.pick.item.plot : "";
        c.story = plot;
        c.onList = MyList.has(tv, t.key);
        Resume.Point r = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        if (t.series) {
            String code = r == null ? "" : HomeScreen.episodeCode(r.subtitle);
            c.play = code.isEmpty() ? "Play" : (r.position > 0 ? "Resume " : "Play ") + code;
        } else {
            c.play = r != null && r.position > 0 ? "Resume" : "Play";
        }
    }

    // ---- the remote moving ------------------------------------------------------------------------------------------

    @Override public void focused(Editorial.Row row, Editorial.Card card) {
        h.removeCallbacks(extras);
        h.removeCallbacks(details);
        if (row == null) {
            if (card == null) return;
            view.ground(card.picture);
            Catalog.Title t = card.title();
            if (t != null) {
                if (!extrasKnown.containsKey(t.key)) {
                    Tmdb.Extras kept = Tmdb.keptExtras(tv, t);
                    if (kept != null) extras(t, kept);
                    else {
                        extrasFor = card;
                        h.postDelayed(extras, 700);
                    }
                }
                if (Catalog.get(tv).cachedDetails(t) == null) {
                    detailsFor = card;
                    h.postDelayed(details, 450);
                }
            }
            readAhead();
            return;
        }
        // The page's ground: the title's wide picture (a mood keeps the one before).
        if (card != null && card.pick.mood == null) {
            Shelves.Pick p = card.pick;
            String g = p.title == null ? "" : wide(p, Catalog.get(tv).cachedDetails(p.title));
            if (!g.isEmpty()) view.ground(g);
        }
        // The rows past the remote's, asked for now.
        int at = -1;
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(row.id)) at = i;
        ahead(at);
    }

    /** The carousel's pictures a key either side, read after what is on screen. */
    private void readAhead() {
        for (int along : new int[]{1, -1, 2}) {
            Editorial.Card c = view.heroNear(along);
            if (c == null || c.picture.isEmpty()) continue;
            Pics.prefetch(c.picture, Ui.px(Editorial.HERO_W), Ui.px(Editorial.HERO_H), Pics.FACE);
            if (view.getWidth() > 0) Pics.prefetch(c.picture, view.getWidth(), view.getHeight(), Pics.BLUR);
        }
    }

    private void fetchExtras() {
        Editorial.Card c = extrasFor;
        Catalog.Title t = c == null ? null : c.title();
        if (t == null) return;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.extras(tv, t);
            if (x != null) Exec.main(() -> extras(t, x));
        });
    }

    private void extras(Catalog.Title t, Tmdb.Extras x) {
        if (extrasKnown.size() > 200) extrasKnown.clear();
        extrasKnown.put(t.key, x);
        for (Editorial.Card c : heroCards) {
            if (c.title() == t) {
                heroWords(c);
                if (view != null) view.refresh(c);
            }
        }
    }

    private void fetchDetails() {
        Editorial.Card c = detailsFor;
        Catalog.Title t = c == null ? null : c.title();
        if (t == null) return;
        Catalog.get(tv).details(t, d -> {
            if (d == null || view == null) return;
            for (Editorial.Card k : heroCards) {
                if (k.title() == t) {
                    heroWords(k);
                    view.refresh(k);
                }
            }
        });
    }

    @Override public void more(Editorial.Row row) {
        Shelves.Shelf s = shelves.get(row.id);
        if (s == null || !s.more() || !paging.add(row.id)) return;
        int was = round;
        Exec.io(() -> {
            Shelves.Shelf next = Shelves.more(tv, s);
            Runnable done = () -> {
                paging.remove(row.id);
                if (was != round || view == null) return;
                shelves.put(row.id, next);
                publish();
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    // ---- OK -----------------------------------------------------------------------------------------------------------

    @Override public void chosen(Editorial.Row row, Editorial.Card card) {
        if (card == null) {
            // A row's "All" card: its whole list.
            Shelves.Shelf s = row == null ? null : shelves.get(row.id);
            if (s == null) return;
            List<Catalog.Title> titles = s.all != null ? s.all : titlesOf(s);
            if (!titles.isEmpty()) tv.push(new ListScreen(tv, seriesOf(titles), row.title, titles));
            return;
        }
        Shelves.Pick p = card.pick;
        if (p.mood != null) {
            openMood(p.mood);
            return;
        }
        if (p.category != null) {
            tv.push(new ListScreen(tv, page.equals(CategorySet.SHOWS), p.category.name, p.category.titles));
            return;
        }
        if (p.point != null && carryOn(p.point)) return;
        if (p.title != null) tv.openTitle(p.title);
    }

    private static List<Catalog.Title> titlesOf(Shelves.Shelf s) {
        List<Catalog.Title> out = new ArrayList<>();
        for (Shelves.Pick p : s.picks) if (p.title != null && p.category == null) out.add(p.title);
        return out;
    }

    private boolean seriesOf(List<Catalog.Title> titles) {
        if (page.equals(CategorySet.SHOWS)) return true;
        if (page.equals(CategorySet.MOVIES)) return false;
        return !titles.isEmpty() && titles.get(0).series;
    }

    /** A mood's titles found (off the main thread), then their page; a word where there are none. */
    private void openMood(CategorySet.Mood m) {
        String kind = page.equals(CategorySet.MOVIES) ? "movie" : page.equals(CategorySet.SHOWS) ? "tv" : "all";
        int was = round;
        Exec.io(() -> {
            List<Catalog.Title> titles = Shelves.mood(tv, m, kind);
            Runnable done = () -> {
                if (was != round || tv.isFinishing()) return;
                if (titles.isEmpty()) {
                    Toast.makeText(tv, "Nothing for " + m.name + " in your playlists yet", Toast.LENGTH_SHORT).show();
                    return;
                }
                tv.push(new ListScreen(tv, seriesOf(titles), m.name, titles));
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    /** Continue watching: on at once from where it was (an episode needs its series' page read; else its page). */
    private boolean carryOn(Resume.Point p) {
        Catalog.Title t = Catalog.get(tv).title(p.title);
        if (t == null) return false;
        if (!t.series && p.episode.isEmpty()) {
            tv.play(t, null, p.position, false);
            return true;
        }
        Catalog.get(tv).details(t, d -> {
            Catalog.Episode e = d == null ? null : d.episode(p.episode);
            if (e != null) tv.play(t, e, p.position, p.position <= 0);
            else tv.openTitle(t);
        });
        return true;
    }

    @Override public void held(Editorial.Row row, Editorial.Card card) {
        Catalog.Title t = card.pick.title;
        if (t == null || card.pick.category != null || card.pick.mood != null) {
            chosen(row, card);
            return;
        }
        boolean on = MyList.toggle(tv, t.key);
        Toast.makeText(tv, on ? t.name + " added to My list" : t.name + " taken off My list", Toast.LENGTH_SHORT).show();
        for (Editorial.Card c : heroCards) {
            if (c.title() == t) {
                heroWords(c);
                view.refresh(c);
            }
        }
        // My List's own row made again.
        for (CategorySet.Entry e : entries) if (e.source.startsWith("mylist")) shelves.put(e.id, Shelves.local(tv, e));
        publish();
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.UP && tv.tabs() != null) {
            toBar();
            return true;
        }
        return false;
    }

    /** The remote up to the tab bar (nothing on the page lifted meanwhile). */
    void toBar() {
        if (tv.tabs() == null) return;
        tv.tabs().enter();
        view.setActive(false);
    }

    /** Down from the tab bar: the carousel, or the first row. */
    void fromTabs() {
        if (view == null) return;
        view.setActive(true);
        view.fromAbove();
    }

    boolean key(int code) {
        return view.key(code);
    }

    void ok() {
        view.ok();
    }

    void okLong() {
        view.okLong();
    }

    /** Back: from the rows to the carousel; from there, the tab bar. */
    boolean back() {
        if (view.back()) return true;
        toBar();
        return true;
    }

    // ---- nothing to show ----------------------------------------------------------------------------------------------

    private String emptyHead() {
        if (Sources.list(tv).isEmpty()) return "Welcome to Homefield";
        return page.equals(CategorySet.SHOWS) ? "TV Shows" : page.equals(CategorySet.MOVIES) ? "Movies" : "Homefield";
    }

    private String emptyWhy() {
        Catalog cat = Catalog.get(tv);
        if (Sources.list(tv).isEmpty()) return "Add your family's playlist in Settings";
        if (cat.busy() || Library.get(tv).busy()) return "Loading your playlists…";
        if (!cat.status().isEmpty() && !cat.ready()) return cat.status();
        return page.equals(CategorySet.SHOWS) ? "Your playlists' TV shows come up here" : "Your playlists' films come up here";
    }

    // ---- previews and tests -----------------------------------------------------------------------------------------

    /** Previews and tests: TMDB's extras for a title as if it had answered. */
    void previewExtras(Catalog.Title t, Tmdb.Extras x) {
        extras(t, x);
    }

    /** Previews and tests: the remote on the carousel's card i (row -1) or a row's card. */
    void previewFocus(int row, int i) {
        view.focus(row, i);
    }

    /** Previews and tests: the place of a row by its id among those shown (-1: left out). */
    int previewRow(String id) {
        List<Editorial.Row> rows = view.rows();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id.equals(id)) return i;
        return -1;
    }
}
