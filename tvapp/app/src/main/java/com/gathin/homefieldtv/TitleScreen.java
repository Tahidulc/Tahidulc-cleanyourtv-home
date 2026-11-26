package com.gathin.homefieldtv;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.RectF;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CopyFacts;
import com.gathin.homefieldtv.engine.Copies;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.TorBox;
import com.gathin.homefieldtv.engine.Videos;

import java.util.ArrayList;
import java.util.List;

/**
 * A film's or a series' page, in 07's shell with its "‹ Movies" (or "‹ TV Shows") line, in both Home styles: its first
 * screen is the family's cover B (CoverPage, under the rest of the page, TitlePage), what every film and series opens
 * on, from a row, the carousel, Search, My List or Continue watching:
 *
 * <pre>
 *   a film     Resume ("1h 12m left", the copy it was started on under it) with the remote on it and From the start,
 *              where it was left part-way; then PLAY FROM, every source the best first (the remote on BEST where it
 *              wasn't started), each one's picture, HDR, sound and size filled in as they come (CopyFacts). OK plays at
 *              once, no second cover: Resume and From the start from the copy it was started on, a source from where
 *              it was left (that copy kept as the one started on)
 *   a series   Resume S2 E8 ("23 min left") or Play S1 E1 (the episode to carry on with, from its start), and From the
 *              start (S1 E1); Resume plays at once from the copy it was started on, Play and From the start (and an
 *              episode chosen below) open that episode's own cover with its sources (VodActivity's), unless it has but
 *              one; under the cover its seasons, episodes and Videos row as before
 *   buttons    beside the facts, Up from the first row: Trailer (the trailer in the TV's YouTube app), My List (a tick
 *              once on it), More like this (down to its row) and, where something was started, Remove from Continue
 *              watching
 * </pre>
 *
 * Down from the cover's last row the page rises to what is under it (a series' section, then More like this at the
 * foot); Up comes back; Back from further down comes back up to the cover, and on the cover goes back to wherever the
 * page was opened from.
 *
 * <p>Its trailer plays in the cover's top card ("trailers need to play on the top card when I'm on a movie and tv
 * show"): once the remote has rested on the cover for Trailer's rest (never while it moves: each move starts the rest
 * again), the poster widens into a 16:9 card on its own middle and the trailer plays in it (Trailer, its one window over
 * the card, with the sound as Settings say), playing on while the remote goes along the rows and buttons. It stops and
 * the poster comes back when it ends, as anything is played, as the remote goes down the page, as the page goes and as
 * anything comes over it; it starts again only when the page is shown again.
 *
 * <p>TMDB's logo, cast and seasons' colours come when it has answered (kept on the TV after, so the page has them at
 * once next time).
 */
final class TitleScreen extends Screen implements TitlePage.Host, Trailer.Host {
    private Catalog.Title title;
    private Catalog.Details details;
    private Tmdb.Extras extras;
    private boolean asked, asking;
    private TitlePage page;
    private CoverPage cover;
    private Trailer trailer;
    /** Opened at From the start (Home's MENU on Continue watching): the remote there as the rows first come. */
    private final boolean fromStart;
    private boolean placed;
    /** A film's copies (found as the page opens), their facts being filled in. */
    private final List<Catalog.Play> copies = new ArrayList<>();
    private boolean copiesAsked;
    private CopyFacts.Look facts;
    /** What OK on each of the cover's rows (above the sources) and buttons does. */
    private final List<Runnable> rows = new ArrayList<>(), pills = new ArrayList<>();
    /** The trailer has played in the card since the page was shown: it isn't started again until it is shown again. */
    private boolean trailerDone;

    TitleScreen(TvActivity tv, Catalog.Title t) {
        this(tv, t, false);
    }

    TitleScreen(TvActivity tv, Catalog.Title t, boolean fromStart) {
        super(tv);
        this.title = t;
        this.fromStart = fromStart;
    }

    /** Its cover draws an opaque ground over the whole screen (the window's own isn't drawn under it). */
    @Override boolean opaque() {
        return true;
    }

    @Override View build() {
        Cine.init(tv);
        FrameLayout f = new FrameLayout(tv);
        cover = new CoverPage(tv, new CoverPage.Host() {
            @Override public void play(int index) {
                playCopy(index);
            }

            @Override public void action(int index) {
                if (index >= 0 && index < rows.size()) rows.get(index).run();
            }

            @Override public void button(int index) {
                if (index >= 0 && index < pills.size()) pills.get(index).run();
            }

            @Override public void up() {
                TitleScreen.this.up();
            }

            @Override public void down() {
                page.below();
            }
        });
        cover.withSources(!title.series);
        f.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        // Over the cover: what is under it (a series' section, More like this), drawn as the page rises to it.
        page = new TitlePage(tv, this, cover);
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        trailer = new Trailer(tv, f, this);
        details = Catalog.get(tv).cachedDetails(title);
        fill();
        if (details == null) ask();
        // TMDB's logo, cast and seasons: kept ones at once, else asked for (when the launcher gives its key).
        Tmdb.Extras kept = Tmdb.keptExtras(tv, title);
        if (kept != null) extras(kept);
        Catalog.Title want = title;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.extras(tv, want);
            if (x != null && x != kept) Exec.main(() -> {
                if (page != null && title == want) extras(x);
            });
        });
        // The family's TorBox read meanwhile (when it is in use), so that an episode finds its copies at once.
        TorBox.warm(tv);
        findCopies();
        trailer.rest(title.key);
        trailerKeys();
        return f;
    }

    /** TMDB's extras: the cover's logo and cast (with their photos), the page's videos and seasons' colours. */
    private void extras(Tmdb.Extras x) {
        extras = x;
        page.extras(x);
        cover.logo(x.logo);
        List<String[]> faces = new ArrayList<>();
        for (String[] p : x.cast) {
            if (p.length == 0 || p[0].isEmpty() || p[0].endsWith("(director)")) continue;
            faces.add(new String[]{p[0], p.length > 2 ? p[2] : ""});
        }
        if (!faces.isEmpty()) cover.cast(faces);
        trailerKeys();
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
        if (page != null) page.setActive(tv.tabs() == null || !tv.tabs().active());
        // Back from a film: its resume point has moved; its trailer may play again, its sources' facts go on.
        if (built()) {
            fill();
            trailerDone = false;
            trailer.rest(title.key);
            trailerKeys();
            if (!copies.isEmpty() && (facts == null || facts.stopped())) facts = CopyFacts.fill(tv, copies, this::factsCame);
        }
    }

    @Override void hidden() {
        if (trailer != null) trailer.hidden();
        if (facts != null) facts.stop();
    }

    @Override boolean shell() {
        return true;
    }

    /** Reached from Movies or TV Shows: "‹ Movies", "‹ TV Shows" where the tabs would be. */
    @Override String backLine() {
        return title.series ? "TV Shows" : "Movies";
    }

    /** Down from the tab bar: the cover. */
    @Override void fromTabs() {
        if (page == null) return;
        page.setActive(true);
        page.enter();
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

    /** The cover's words, rows and buttons, and the page under it, for what is known now. */
    private void fill() {
        Resume.Point p = point();
        List<Catalog.Title> more = new ArrayList<>();
        for (Catalog.Category c : Catalog.get(tv).categories(title.series)) {
            if (!c.name.equals(title.category)) continue;
            for (Catalog.Title t : c.titles) {
                // The first 30: never on through a category of thousands.
                if (more.size() >= 30) break;
                if (t != title) more.add(t);
            }
        }
        cover.show(title, null, details);
        cover.actions(actions(p));
        cover.buttons(buttons(p, more));
        if (!placed && cover.actionCount() > 1) {
            placed = true;
            if (fromStart) cover.focusRow(1, 0);
        }
        resumeCopy(p);
        page.set(title, details, more, resumeEpisode(p));
    }

    /**
     * The cover's rows above the sources, and what OK on each does: a film left part-way: Resume and From the start (a
     * film not started: none, its best source first); a series: Resume S2 E8 (or Play S1 E1) and From the start.
     */
    private List<CoverPage.Action> actions(Resume.Point p) {
        List<CoverPage.Action> out = new ArrayList<>();
        rows.clear();
        if (!title.series) {
            if (p != null && p.position > 0) {
                long length = p.duration > 0 ? p.duration : details == null ? 0 : details.minutes * 60_000L;
                long at = p.position;
                out.add(new CoverPage.Action(CoverPage.RESUME_NAME, CoverPage.resumeWords("", at, length), p.progress(), false));
                rows.add(() -> playing(() -> tv.play(title, null, at, false)));
                out.add(new CoverPage.Action(CoverPage.FROM_START_NAME, CoverPage.startWords("", length), 0f, true));
                rows.add(() -> playing(() -> tv.play(title, null, 0, false)));
            }
            return out;
        }
        if (details == null) {
            out.add(new CoverPage.Action(CoverPage.PLAY_NAME, asked && !asking ? "Couldn't get the episodes: OK to try again"
                    : "Finding the episodes…", 0f, false));
            rows.add(this::ask);
            return out;
        }
        List<Catalog.Episode> all = details.episodes();
        Catalog.Episode resume = p == null ? null : details.episode(p.episode);
        if (resume != null) {
            String code = "S" + resume.season + " E" + resume.number;
            if (p.position > 0) {
                long length = p.duration > 0 ? p.duration : resume.minutes * 60_000L, at = p.position;
                out.add(new CoverPage.Action(CoverPage.RESUME_NAME, CoverPage.resumeWords(code, at, length), p.progress(), false));
                // Straight away, from the copy it was started on.
                rows.add(() -> playing(() -> tv.play(title, resume, at, false)));
            } else {
                // The next one, from its start: its own cover with its sources.
                out.add(new CoverPage.Action(CoverPage.PLAY_NAME, code, 0f, false));
                rows.add(() -> playing(() -> tv.play(title, resume, 0, true)));
            }
        } else if (!all.isEmpty()) {
            Catalog.Episode first = all.get(0);
            out.add(new CoverPage.Action(CoverPage.PLAY_NAME, "S" + first.season + " E" + first.number, 0f, false));
            rows.add(() -> playing(() -> tv.play(title, first, 0, true)));
            return out;
        } else {
            out.add(new CoverPage.Action(CoverPage.PLAY_NAME, title.torbox() ? "No episodes in your TorBox" : "No episodes in your playlist",
                    0f, false));
            rows.add(() -> { });
            return out;
        }
        if (!all.isEmpty()) {
            Catalog.Episode first = all.get(0);
            out.add(new CoverPage.Action(CoverPage.FROM_START_NAME, "S" + first.season + " E" + first.number, 0f, true));
            rows.add(() -> playing(() -> tv.play(title, first, 0, true)));
        }
        return out;
    }

    /** The buttons beside the facts, and what OK on each does. */
    private List<CoverPage.Pill> buttons(Resume.Point p, List<Catalog.Title> more) {
        List<CoverPage.Pill> out = new ArrayList<>();
        pills.clear();
        String id = trailerId(details == null ? "" : details.trailer);
        if (!id.isEmpty()) {
            out.add(new CoverPage.Pill(Icons07.FILM, "Trailer"));
            pills.add(() -> {
                trailer.moved();
                trailerDone = true;
                trailer(id);
            });
        }
        boolean on = MyList.has(tv, title.key);
        out.add(new CoverPage.Pill(on ? Icons07.CHECK : Icons07.PLUS, "My List"));
        pills.add(this::toggleList);
        if (!more.isEmpty()) {
            out.add(new CoverPage.Pill(Icons07.GRID, "More like this"));
            pills.add(() -> page.toPart(TitlePage.MORE));
        }
        if (p != null) {
            out.add(new CoverPage.Pill(Icons07.BIN, "Remove"));
            pills.add(this::forget);
        }
        return out;
    }

    /** Something is about to play: the card's trailer and the copies' look stopped first. */
    private void playing(Runnable start) {
        trailer.moved();
        trailerDone = true;
        if (facts != null) facts.stop();
        start.run();
    }

    // ---- a film's copies -------------------------------------------------------------------------------------------

    /** A film's every copy on its cover, the best first, their facts filled in as they come. */
    private void findCopies() {
        if (title.series || copiesAsked) return;
        copiesAsked = true;
        if (TorBox.on(tv) && !TorBox.fresh(tv)) cover.status("Looking in your TorBox…");
        Catalog.Title want = title;
        VodActivity.copies(tv, title, null, found -> {
            if (page == null || title != want) return;
            copies.clear();
            copies.addAll(found);
            if (copies.isEmpty()) {
                cover.status(title.torbox() ? "It isn't in your TorBox any more, or TorBox is turned off." : "Its playlist has gone from this TV.");
                return;
            }
            cover.sources(copies);
            resumeCopy(point());
            if (facts != null) facts.stop();
            facts = CopyFacts.fill(tv, copies, this::factsCame);
        });
    }

    /** More is known of the copies: their rows again, the best first by what they really are. */
    private void factsCame() {
        CopyFacts.rank(copies);
        cover.sources(copies);
        resumeCopy(point());
    }

    /** The copy Resume carries on from, said on its row (a film left part-way). */
    private void resumeCopy(Resume.Point p) {
        if (title.series || copies.isEmpty() || p == null || p.position <= 0) return;
        cover.resumeFrom(Math.max(0, Copies.find(tv, title.key, "", copies)));
    }

    /** OK on a source: it plays from where it was left, kept as the copy it was started on (the rest its backups). */
    private void playCopy(int i) {
        if (i < 0 || i >= copies.size()) return;
        Resume.Point p = point();
        Copies.started(tv, title.key, "", copies.get(i));
        playing(() -> tv.play(title, null, p != null && p.position > 0 ? p.position : 0, false));
    }

    private String resumeEpisode(Resume.Point p) {
        return p == null || details == null || details.episode(p.episode) == null ? "" : p.episode;
    }

    private void toggleList() {
        boolean on = MyList.toggle(tv, title.key);
        Toast.makeText(tv, on ? title.name + " added to My list" : title.name + " taken off My list", Toast.LENGTH_SHORT).show();
        fill();
    }

    /** Taken off Continue watching (every episode's place, for a series). */
    private void forget() {
        Resume.remove(tv, title.key);
        Toast.makeText(tv, title.name + " taken off Continue watching", Toast.LENGTH_SHORT).show();
        fill();
    }

    // ---- the trailer in the top card -----------------------------------------------------------------------------

    /** The title's trailers (TMDB's, the family's language first, then the playlist's own), as they become known. */
    private void trailerKeys() {
        if (trailer == null) return;
        trailer.keys(title.key, Videos.trailers(extras == null ? null : extras.videos, Tmdb.language(tv), details == null ? "" : details.trailer));
    }

    /** The cover's card for the trailer, once the poster has popped in and the page is at its top with the remote on it. */
    @Override public boolean spot(RectF out) {
        return page != null && page.part() == TitlePage.COVER && cover.trailerSpot(out);
    }

    /** Whether the trailer may play: this page in front (nothing over it, the app in front), the remote on its cover. */
    @Override public boolean mayPlay() {
        return page != null && tv.showing(this) && page.part() == TitlePage.COVER;
    }

    @Override public void showing(boolean on) {
        if (on) trailerDone = true;
        cover.trailerShown(on);
    }

    @Override public void finished(boolean played) {
        if (played) trailerDone = true;
    }

    /** The remote moved: down the page, the trailer stops; on the cover, one waiting waits from now, one playing plays on. */
    @Override public void moved() {
        if (trailer == null || page == null) return;
        if (page.part() != TitlePage.COVER) {
            trailer.moved();
            return;
        }
        if (trailerDone || trailer.busy()) return;
        trailer.moved();
        trailer.rest(title.key);
        trailerKeys();
    }

    /** Pure: a trailer's YouTube id from what a provider gives ("dQw4w9WgXcQ", a watch or youtu.be address), else "". */
    static String trailerId(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.matches("[A-Za-z0-9_-]{11}")) return t;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})").matcher(t);
        return m.find() ? m.group(1) : "";
    }

    /** The trailer full screen here (Trailer.fullScreen; the TV's YouTube app only when none of its trailers will play). */
    private void trailer(String id) {
        Trailer.fullScreen(tv, title, id);
    }

    /** A trailer (its YouTube id) in the TV's YouTube app, from any screen (Home's MENU too). */
    static void trailer(TvActivity tv, String id) {
        try {
            tv.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(tv, "There's no app on this TV to play the trailer", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        return page.key(code);
    }

    @Override void ok() {
        page.ok();
    }

    @Override boolean back() {
        return page.back();
    }

    /** Up from the top of the page: the tab bar comes back over the page, the remote on it. */
    @Override public void up() {
        if (tv.tabs() == null) return;
        tv.tabs().enter();
        page.setActive(false);
    }

    /** An episode chosen: its own cover with its sources first (straight away with one), on from where it was left. */
    @Override public void play(Catalog.Episode ep) {
        Resume.Point p = Resume.get(tv, title.key, ep.id);
        playing(() -> tv.play(title, ep, p == null ? 0 : p.position, true));
    }

    @Override public void video(String key) {
        String id = trailerId(key);
        if (!id.isEmpty()) {
            trailer.moved();
            trailer(id);
        }
    }

    @Override public void open(Catalog.Title t) {
        tv.openTitle(t);
    }

    @Override public float progress(Catalog.Episode ep) {
        Resume.Point p = Resume.get(tv, title.key, ep.id);
        return p == null || p.position <= 0 ? -1f : p.progress();
    }

    /** Previews: the remote on a part (TitlePage.COVER…) and along it. */
    void previewFocus(int part, int along) {
        page.focus(part, along);
    }

    /** Tests: the page itself, its cover, its copies and its trailer. */
    TitlePage page() {
        return page;
    }

    CoverPage cover() {
        return cover;
    }

    List<Catalog.Play> copies() {
        return copies;
    }

    Trailer trailerCard() {
        return trailer;
    }

    /** Previews: TMDB's extras as if it had answered. */
    void previewExtras(Tmdb.Extras x) {
        extras(x);
    }

    /** Previews and tests: these copies as if found (the best first), on the cover as the page shows them. */
    void previewCopies(List<Catalog.Play> list) {
        if (facts != null) facts.stop();
        copies.clear();
        copies.addAll(list);
        cover.sources(copies);
        resumeCopy(point());
    }
}
