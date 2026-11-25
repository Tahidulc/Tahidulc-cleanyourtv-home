package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CategorySet;
import com.gathin.homefieldtv.engine.DayRows;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Shelves;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.TmdbLists;
import com.gathin.homefieldtv.engine.Videos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Editorial page of Home, Movies or TV Shows (Settings › Appearance › Home style: Editorial): the day's rows as
 * DayRows picks them from CategorySet's (which show and their order change with the day, the same all day; the row for
 * the part of the day as the page was made), each made by Shelves and drawn by Editorial. HomeScreen and BrowseScreen hand
 * it their screen's work when Editorial is chosen.
 *
 * <p>Living: as the page is shown again (the app back in front, a film over) on a new day or part of the day, its rows
 * are picked again; a row's titles past their expiry (a few hours: DayRows.expiry) are asked for again off the main
 * thread, and while the page is open the minute's tick does the same for the rows the remote isn't on, so the rows
 * change under the family without anything jumping under the remote. A Coming Soon card that no playlist has yet says
 * when it comes; OK plays its trailer in the TV's YouTube app (it has nothing else to play).
 *
 * <p>Built lazily and in pages, so the main thread never goes through the catalogue: the rows from the family's own
 * lists (Continue Watching, the newest) are made at once from the lists the catalogue keeps; a TMDB row is made off the
 * main thread and comes in when it is ready (a row with nothing in it never does), the first few as the page opens and
 * the next ones as the remote goes down to them; a TMDB row reads its next page as the remote nears its end. What was
 * made is kept (Shelves), so the page comes back at once. The card the remote rests on has its page's details read (its
 * length, genre and age) and TMDB's logo; the backdrops either side of it are read ahead at the size they are drawn. OK on
 * a card opens its page (Continue Watching carries on); OK on the carousel is View Details; OK held puts it on My List or
 * takes it off; Back, or Up past the top, goes up to the tab bar.
 *
 * <p>When the remote rests on a row's card its trailer plays in it (Trailer): its trailers are found as its details and
 * TMDB's extras are read (Videos.trailers: TMDB's, the family's language first, then the playlist's own); it stops as the
 * remote moves, goes up to the tab bar, or the page goes.
 *
 * <p>The carousel at the top of Home, Movies and TV Shows ("a carousel of titles that I can move myself and will move
 * automatically") is its featured titles from the living rows (what is trending today with what the playlists added this
 * week, refreshed with the rows): it moves on by itself every AUTO_MS while it is what the page shows (the remote on it,
 * or up on the tab bar over it), never while the remote is in the rows; a move by hand (Left or Right, as far as its
 * ends) holds it PAUSE_MS; and on a title the remote rests on its trailer plays behind its words after 3 s, as on a
 * poster, holding it while it plays and moving on AFTER_MS once it is over.
 */
final class EditorialPage implements Editorial.Host, Trailer.Host {
    /** The TMDB rows asked for as the page opens, and how far past the remote's row they are asked for. */
    static final int FIRST = 8, AHEAD = 6;

    private final TvActivity tv;
    private final String page;
    private final List<CategorySet.Entry> entries = new ArrayList<>();
    private CategorySet.Entry heroEntry;
    /** The day (and part of it) the rows were picked for (DayRows.key). */
    private String dayKey = "";
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
    /** The titles whose extras have been looked for (found or not): their trailers are known as far as they will be. */
    private final Set<String> looked = new HashSet<>();
    /** Coming Soon cards' trailers, by TMDB's id. */
    private final Map<String, List<String>> soonKnown = new HashMap<>();
    private boolean publishing, again;
    /** The trailer in the card the remote rests on. */
    private Trailer trailer;

    EditorialPage(TvActivity tv, String page) {
        this.tv = tv;
        this.page = page;
        pick();
    }

    /** The day's rows picked (DayRows), the rows kept for other days let go of. */
    private void pick() {
        java.time.LocalDateTime now = DayRows.local(DayRows.now());
        dayKey = DayRows.key(now);
        entries.clear();
        heroEntry = null;
        Set<String> ids = new HashSet<>();
        for (CategorySet.Entry e : DayRows.of(page, now)) {
            ids.add(e.id);
            if (e.kind == CategorySet.HERO) heroEntry = e;
            else entries.add(e);
        }
        Shelves.keepOnly(page, ids);
    }

    /** Whether the day (or its part) has moved on since the rows were picked. */
    private boolean newDay() {
        return !DayRows.key(DayRows.local(DayRows.now())).equals(dayKey);
    }

    View build() {
        Cine.init(tv);
        PosterArt.init(tv);
        FrameLayout f = new FrameLayout(tv);
        view = new Editorial(tv);
        view.setHost(this);
        f.addView(view, new FrameLayout.LayoutParams(-1, -1));
        // The carousel's words over its trailer: the trailer's window goes under them.
        View over = view.over(tv);
        f.addView(over, new FrameLayout.LayoutParams(-1, -1));
        trailer = new Trailer(tv, f, this);
        trailer.under(over);
        fill();
        return f;
    }

    // ---- the carousel's own steps -----------------------------------------------------------------------------------

    /** How often the carousel moves on by itself, and how long a move by hand holds it. */
    static final long AUTO_MS = 7000, PAUSE_MS = 15_000;
    private final Runnable auto = this::autoStep;

    /** Its next own step in ms (a full AUTO_MS from now, or PAUSE_MS after a move by hand). */
    private void autoIn(long ms) {
        h.removeCallbacks(auto);
        if (heroEntry != null) h.postDelayed(auto, ms);
    }

    /** On to its next title, while it is what the page shows and no trailer plays in it; then the next step. */
    private void autoStep() {
        if (view == null) return;
        if (mayShow() && !trailer.busy()) view.heroNext();
        autoIn(AUTO_MS);
    }

    @Override public void heroMoved() {
        autoIn(PAUSE_MS);
    }

    /** How soon the carousel moves on once the trailer that held it is over. */
    static final long AFTER_MS = 2000;

    /** A carousel card's trailer over (it ended, or none of its trailers would play): on to the next shortly after. */
    @Override public void finished(boolean played) {
        if (played && view != null && view.heroInSight()) autoIn(AFTER_MS);
    }

    /** The carousel's next own step a full AUTO_MS after a title comes to its front (by hand: heroMoved, after this). */
    @Override public void heroOn() {
        autoIn(AUTO_MS);
    }

    /** Tests: the trailer of the card the remote rests on. */
    Trailer trailer() {
        return trailer;
    }

    Editorial view() {
        return view;
    }

    void shown() {
        if (view == null) return;
        view.setActive(tv.tabs() == null || !tv.tabs().active());
        if (newDay()) {
            // Another day (or part of it): the day's rows picked again, the remote's place kept where its row still is.
            pick();
            reload();
            return;
        }
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
        autoIn(AUTO_MS);
    }

    /**
     * The minute's tick while the page is open: the rows (but the remote's) whose titles are past their expiry asked for
     * again off the main thread, coming in as they are made.
     */
    void tick() {
        if (view == null || !mayShow()) return;
        Editorial.Row on = view.current();
        long now = DayRows.now();
        // The carousel's titles too, with the rows (the remote kept on its title).
        if (heroEntry != null && asked.contains(heroEntry.id) && Shelves.expired(shelves.get(heroEntry.id), now)) {
            asked.remove(heroEntry.id);
            request(heroEntry);
        }
        for (CategorySet.Entry e : entries) {
            if (!e.tmdb() || !asked.contains(e.id) || on != null && on.id.equals(e.id)) continue;
            Shelves.Shelf s = shelves.get(e.id);
            if (s != null && Shelves.expired(s, now)) {
                asked.remove(e.id);
                request(e);
            }
        }
    }

    /** Whether this page's screen is the one on top, with the app in front. */
    private boolean mayShow() {
        Screen top = tv.top();
        boolean mine = top instanceof HomeScreen ? ((HomeScreen) top).editorial() == this
                : top instanceof BrowseScreen && ((BrowseScreen) top).editorial() == this;
        return mine && tv.showing(top);
    }

    void hidden() {
        h.removeCallbacks(rested);
        h.removeCallbacks(auto);
        if (trailer != null) trailer.hidden();
    }

    void catalogChanged() {
        Shelves.forget();
        reload();
    }

    /** Every row made again (from what Shelves keeps, where it is still for this catalogue). */
    private void reload() {
        if (trailer != null) trailer.moved();
        round++;
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
        for (Shelves.Pick p : s.picks) {
            // Only titles the family can play; a Coming Soon row's titles to come, too (its title says so).
            if (p.title != null) cards.add(card(p));
            else if (e.soon() && p.item != null) cards.add(soonCard(p));
        }
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

    /** A Coming Soon card no playlist has yet: TMDB's poster and backdrop, its score, when it comes, its genre, its story. */
    private Editorial.Card soonCard(Shelves.Pick p) {
        TmdbLists.Item it = p.item;
        Editorial.Card c = new Editorial.Card(p, it.poster, it.backdrop.isEmpty() ? it.poster : it.backdrop);
        c.name = it.name;
        c.year = comes(it.date);
        c.genre = it.genres.length > 0 ? TmdbLists.genre(it.genres[0]) : "";
        c.story = it.plot;
        return c;
    }

    /** Pure: when a title to come comes, as its card says it: "Coming 18 Dec", "Coming soon". */
    static String comes(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return "Coming soon";
        try {
            java.time.LocalDate d = java.time.LocalDate.parse(date);
            return "Coming " + d.getDayOfMonth() + " " + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK);
        } catch (RuntimeException e) {
            return "Coming soon";
        }
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
        if (card == null) {
            trailer.moved();
            return;
        }
        // A key held: the page's picture and the read-ahead wait until it is let go (landed), so nothing is read for the
        // titles the remote only passes.
        boolean passing = view.holding();
        if (!passing) view.ground(card.wide);
        restingOn = card;
        // Its trailer, after a rest, the remote in the page (the carousel's behind its words).
        if (!view.active()) trailer.moved();
        else {
            trailer.rest(trailerKey(card));
            trailers(card);
        }
        if (Exec.inline) rested();
        else h.postDelayed(rested, 450);
        if (!passing) readAhead(row == null);
        if (row != null) {
            // The rows past the remote's, asked for now.
            int at = -1;
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(row.id)) at = i;
            ahead(at);
        }
    }

    /** A held key let go on a card: the page's picture and the pictures either side of it, as a press would have them. */
    @Override public void landed(Editorial.Row row, Editorial.Card card) {
        if (view == null || card == null) return;
        view.ground(card.wide);
        readAhead(row == null);
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
        if (carousel) return;
        // The posters just past the right of the screen (the next to slide into sight), read before they are needed.
        for (int along = 5; along <= 6; along++) {
            Editorial.Card c = view.near(along);
            if (c != null && !c.poster.isEmpty()) Pics.prefetch(c.poster, Ui.px(Editorial.POSTER_W), Ui.px(Editorial.CARD_H), Pics.CROP);
        }
    }

    /**
     * The remote rested on a card: its page's details (length, genre, age, the playlist's trailer) and TMDB's extras (its
     * logo, its videos), read off the main thread; extras kept from before videos were are asked for again. A Coming Soon
     * card no playlist has: its videos, by TMDB's id.
     */
    private void rested() {
        Editorial.Card card = restingOn;
        Catalog.Title t = card == null ? null : card.title();
        if (t == null) {
            if (card != null && card.pick.item != null) soonVideos(card);
            return;
        }
        if (Catalog.get(tv).cachedDetails(t) == null) {
            Catalog.get(tv).details(t, d -> {
                if (d != null) known(t);
            });
        }
        Tmdb.Extras had = extrasKnown.get(t.key);
        if (looked.contains(t.key) && (had == null || !had.old)) return;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.keptExtras(tv, t);
            if (x == null || x.old) {
                Tmdb.Extras asked = Tmdb.extras(tv, t);
                if (asked != null) x = asked;
            }
            Tmdb.Extras got = x;
            Runnable done = () -> {
                if (looked.size() > 400) looked.clear();
                looked.add(t.key);
                if (got != null) {
                    if (extrasKnown.size() > 200) extrasKnown.clear();
                    extrasKnown.put(t.key, got);
                }
                known(t);
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    // ---- its trailer ----------------------------------------------------------------------------------------------

    /** What a card's trailer is kept by: its title's key, or a Coming Soon card's TMDB id (null for none). */
    private static Object trailerKey(Editorial.Card c) {
        Catalog.Title t = c.title();
        if (t != null) return t.key;
        String k = c.pick.key();
        return k.isEmpty() ? null : k;
    }

    /** A Coming Soon card's videos (by TMDB's id), read off the main thread once; its trailers handed on. */
    private void soonVideos(Editorial.Card c) {
        TmdbLists.Item it = c.pick.item;
        String k = c.pick.key();
        if (soonKnown.containsKey(k)) {
            trailer.keys(k, soonKnown.get(k));
            return;
        }
        Exec.io(() -> {
            List<String> keys = Videos.trailers(Videos.of(tv, it.tv, it.id), Tmdb.language(tv), "");
            Runnable done = () -> {
                if (soonKnown.size() > 100) soonKnown.clear();
                soonKnown.put(k, keys);
                trailer.keys(k, keys);
            };
            if (Exec.inline) done.run();
            else Exec.main(done);
        });
    }

    /** A card's trailers as far as they are known now (once its extras have been looked for), handed to the trailer. */
    private void trailers(Editorial.Card c) {
        Catalog.Title t = c.title();
        if (t == null || !looked.contains(t.key)) return;
        Tmdb.Extras x = extrasKnown.get(t.key);
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        trailer.keys(t.key, Videos.trailers(x == null ? null : x.videos, Tmdb.language(tv), d == null ? "" : d.trailer));
    }

    /** The remote's card's place for its trailer, once it has grown and the page has settled. */
    @Override public boolean spot(android.graphics.RectF out) {
        return view != null && view.trailerSpot(out);
    }

    /** Whether this page is on screen in front (its screen on top, the app in front) with the remote in its rows. */
    @Override public boolean mayPlay() {
        Screen top = tv.top();
        boolean mine = top instanceof HomeScreen ? ((HomeScreen) top).editorial() == this
                : top instanceof BrowseScreen && ((BrowseScreen) top).editorial() == this;
        return mine && tv.showing(top) && view != null && view.active();
    }

    /** A trailer shows in the card now (or no longer): over the carousel's, its words are drawn again. */
    @Override public void showing(boolean on) {
        if (view != null) view.heroTrailer(on && view.area() == Editorial.HERO);
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
        Editorial.Card on = restingOn;
        if (on != null && on.title() == t) trailers(on);
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
        if (p.title != null) {
            tv.openTitle(p.title);
            return;
        }
        if (p.item == null) return;
        // A title still to come: its trailer in the TV's YouTube app, else when it comes.
        List<String> keys = soonKnown.get(p.key());
        if (keys != null && !keys.isEmpty()) {
            trailer.moved();
            TitleScreen.trailer(tv, keys.get(0));
        } else {
            Toast.makeText(tv, p.item.name + ": " + comes(p.item.date).replace("Coming", "coming"), Toast.LENGTH_SHORT).show();
        }
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

    /** The remote up to the tab bar (nothing on the page lifted meanwhile, no trailer). */
    void toBar() {
        if (tv.tabs() == null) return;
        trailer.moved();
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

    /** A key with its repeat count (held: its repeats), as the remote sends it. */
    boolean key(int code, android.view.KeyEvent e) {
        return view.key(code, e == null ? 0 : e.getRepeatCount());
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
        looked.add(t.key);
        known(t);
    }

    /** Tests: the day's rows as picked (their ids), and the day they were picked for. */
    List<String> previewDay() {
        List<String> out = new ArrayList<>();
        if (heroEntry != null) out.add(heroEntry.id);
        for (CategorySet.Entry e : entries) out.add(e.id);
        return out;
    }

    String previewDayKey() {
        return dayKey;
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
