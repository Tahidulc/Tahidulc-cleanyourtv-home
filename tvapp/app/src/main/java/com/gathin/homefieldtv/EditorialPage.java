package com.gathin.homefieldtv;

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
import com.gathin.homefieldtv.engine.TmdbLists;

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
 * lists (Continue Watching, the newest) are made at once from the lists the catalogue keeps; a TMDB row is made off the
 * main thread and comes in when it is ready (a row with nothing in it never does), the first few as the page opens and
 * the next ones as the remote goes down to them; a TMDB row reads its next page as the remote nears its end. What was
 * made is kept (Shelves), so the page comes back at once. The card the remote rests on has its page's details read (its
 * length, genre and age) and TMDB's logo; the backdrops either side of it are read ahead at the size they are drawn. OK on
 * a card opens its page (Continue Watching carries on); OK on the carousel is View Details; OK held puts it on My List or
 * takes it off; Back, or Up past the top, goes up to the tab bar.
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
    private Editorial.Card restingOn;
    private final Runnable rested = this::rested;
    /** TMDB's extras (the logo, the age) of the titles rested on, read once and kept here. */
    private final Map<String, Tmdb.Extras> extrasKnown = new HashMap<>();
    private boolean publishing, again;

    EditorialPage(TvActivity tv, String page) {
        this.tv = tv;
        this.page = page;
        List<CategorySet.Entry> rows = new ArrayList<>();
        for (CategorySet.Entry e : CategorySet.of(page)) {
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
        // Continue Watching may have changed while a film played: made again (quick); old TMDB rows asked again.
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
        h.removeCallbacks(rested);
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

    /** Every row as far as it can be made now: kept ones, the local ones, a row's local choice; TMDB's asked for. */
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
            // The family's own lists at once; a row's own local choice too (the newest) while TMDB's is read.
            Shelves.Shelf local = Shelves.local(tv, e);
            if (!e.tmdb() || !local.isEmpty()) shelves.put(e.id, local);
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
        for (CategorySet.Entry e : entries) {
            Shelves.Shelf s = shelves.get(e.id);
            if (s == null || s.isEmpty() || s.title.contains("{")) continue;
            Editorial.Row r = made.get(e.id);
            if (r == null || r.from != s) {
                r = row(e, s);
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

    private Editorial.Row row(CategorySet.Entry e, Shelves.Shelf s) {
        List<Editorial.Card> cards = new ArrayList<>(s.picks.size());
        for (Shelves.Pick p : s.picks) if (p.title != null) cards.add(card(p));
        Editorial.Row r = new Editorial.Row(e.id, s.title, cards, s.total());
        r.more = s.more();
        r.from = s;
        return r;
    }

    /** A row's card: its poster and backdrop, and its words under the row. */
    private Editorial.Card card(Shelves.Pick p) {
        Catalog.Title t = p.title;
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        String wide = wide(p, d);
        if (p.point != null && t.series && !p.point.picture.isEmpty()) wide = p.point.picture;
        Editorial.Card c = new Editorial.Card(p, p.poster(), wide);
        c.name = t.name;
        if (p.point != null) {
            c.progress = p.point.position > 0 ? p.point.progress() : -1f;
            String code = HomeScreen.episodeCode(p.point.subtitle);
            if (!code.isEmpty()) c.episode = code.replace(" ", ":");
        }
        words(c, d, extrasKnown.get(t.key));
        return c;
    }

    /** A card's words: its score, year, genre, length, story and TMDB's logo, from all that is known of it so far. */
    private void words(Editorial.Card c, Catalog.Details d, Tmdb.Extras x) {
        Catalog.Title t = c.title();
        TmdbLists.Item it = c.pick.item;
        c.details = d;
        c.extras = x;
        float r = Billboard.rating(d != null && !d.rating.isEmpty() ? d.rating : !t.rating.isEmpty() ? t.rating : it != null ? it.rating : "");
        c.score = r > 0f ? Math.round(r * 10f) + "%" : "";
        c.year = !t.year.isEmpty() ? t.year : it != null ? it.year : "";
        String g = d == null ? "" : Hero07.Facts.genres(d.genre, 1);
        if (g.isEmpty() && it != null && it.genres.length > 0) g = TmdbLists.genre(it.genres[0]);
        if (g.isEmpty()) g = PosterArt.genreOf(t.category);
        c.genre = g;
        if (!c.episode.isEmpty()) c.length = c.episode;
        else if (d != null && d.minutes > 0) c.length = Ui.duration(d.minutes);
        else if (t.series && d != null && !d.seasons.isEmpty()) c.length = d.seasons.size() == 1 ? "1 Season" : d.seasons.size() + " Seasons";
        else c.length = "";
        c.story = d != null && !d.plot.isEmpty() ? d.plot : !t.plot.isEmpty() ? t.plot : it != null ? it.plot : "";
        c.logo = x == null ? "" : x.logo;
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
            Editorial.Card c = new Editorial.Card(p, p.poster(), pic);
            c.name = p.title.name;
            heroWords(c);
            out.add(c);
        }
        return out;
    }

    /** A carousel card's words: what it is, its facts (with its age), My List, its story, TMDB's logo where known. */
    private void heroWords(Editorial.Card c) {
        Catalog.Title t = c.title();
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        Tmdb.Extras x = extrasKnown.get(t.key);
        words(c, d, x);
        c.age = x != null && !x.age.isEmpty() ? x.age : d == null ? "" : d.age.trim();
        c.kicker = t.torbox() ? (t.series ? "TV SHOW · " : "FILM · ") + Catalog.TORBOX.toUpperCase(Locale.UK) : t.series ? "TV SHOW" : "FILM";
        c.onList = MyList.has(tv, t.key);
    }

    // ---- the remote moving ------------------------------------------------------------------------------------------

    @Override public void focused(Editorial.Row row, Editorial.Card card) {
        h.removeCallbacks(rested);
        if (card == null) return;
        view.ground(card.wide);
        restingOn = card;
        if (Exec.inline) rested();
        else h.postDelayed(rested, 450);
        readAhead(row == null);
        if (row != null) {
            // The rows past the remote's, asked for now.
            int at = -1;
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(row.id)) at = i;
            ahead(at);
        }
    }

    /** The backdrops a key either side of the remote's card, read after what is on screen, at the size they are drawn. */
    private void readAhead(boolean carousel) {
        for (int along : new int[]{1, -1, 2}) {
            Editorial.Card c = carousel ? view.heroNear(along) : view.near(along);
            if (c == null || c.wide.isEmpty()) continue;
            if (carousel) Pics.prefetch(c.wide, Ui.px(Editorial.HERO_W), Ui.px(Editorial.HERO_H), Pics.FACE);
            else Pics.prefetch(c.wide, Ui.px(Editorial.WIDE_W), Ui.px(Editorial.CARD_H), Pics.CROP);
            if (view.getWidth() > 0 && along == 1) Pics.prefetch(c.wide, view.getWidth(), view.getHeight(), Pics.BLUR);
        }
    }

    /** The remote rested on a card: its page's details (length, genre, age) and TMDB's logo, read off the main thread. */
    private void rested() {
        Editorial.Card card = restingOn;
        Catalog.Title t = card == null ? null : card.title();
        if (t == null) return;
        if (Catalog.get(tv).cachedDetails(t) == null) {
            Catalog.get(tv).details(t, d -> {
                if (d != null) known(t);
            });
        }
        if (extrasKnown.containsKey(t.key)) return;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.keptExtras(tv, t);
            if (x == null) x = Tmdb.extras(tv, t);
            Tmdb.Extras got = x;
            if (got == null) return;
            Runnable done = () -> {
                if (extrasKnown.size() > 200) extrasKnown.clear();
                extrasKnown.put(t.key, got);
                known(t);
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    /** More is known of a title (its details, its extras): its cards' words made again where they are shown. */
    private void known(Catalog.Title t) {
        if (view == null) return;
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        for (Editorial.Card c : heroCards) {
            if (c.title() == t) {
                heroWords(c);
                view.refresh(c);
            }
        }
        for (Editorial.Row r : view.rows()) {
            for (Editorial.Card c : r.cards) {
                if (c.title() != t) continue;
                words(c, d, extrasKnown.get(t.key));
                view.refresh(c);
            }
        }
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
        Shelves.Pick p = card.pick;
        if (p.point != null && carryOn(p.point)) return;
        if (p.title != null) tv.openTitle(p.title);
    }

    /** Continue Watching: on at once from where it was (an episode needs its series' page read; else its page). */
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
        if (t == null) return;
        boolean on = MyList.toggle(tv, t.key);
        Toast.makeText(tv, on ? t.name + " added to My List" : t.name + " taken off My List", Toast.LENGTH_SHORT).show();
        for (Editorial.Card c : heroCards) {
            if (c.title() == t) {
                heroWords(c);
                view.refresh(c);
            }
        }
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

    /** Down from the tab bar: the carousel, or the row it was on. */
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

    /** Back: up to the tab bar, the page where it was (as the other app has it). */
    boolean back() {
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
        extrasKnown.put(t.key, x);
        known(t);
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

    /** Previews: the remote's row caught part way through its last move along it. */
    void previewBetween(float k) {
        view.previewBetween(k);
    }

    /** Tests: how many TMDB rows have been asked for so far. */
    int previewAsked() {
        return asked.size();
    }
}
