package com.gathin.homefieldtv;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.TorBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A film's or a series' page (TitlePage), in 07's shell with its "‹ Movies" (or "‹ TV Shows") line. A film: Play, or
 * Resume (with the minutes left) and From the Start where it was left part-way. A series: Resume S2 E8 (or Play S1 E1)
 * and From the Start. Then round buttons: My List (a tick once on it), the trailer (in the TV's YouTube app) and, where
 * something was started, Remove from Continue watching. Play shows every source first, the best first (VodActivity's Choose a
 * source); Resume carries on straight away from the copy it was started on. TMDB's logo, cast and seasons' colours come
 * when it has answered (kept on the TV after, so the page has them at once next time). With Home style Editorial the
 * buttons are a menu down the left, as the family's other film app has them: Play (or Resume), Trailer, More episodes
 * (a series: down to its episodes), Add to My List, More like this (down to it).
 */
final class TitleScreen extends Screen implements TitlePage.Host {
    private Catalog.Title title;
    private Catalog.Details details;
    private boolean asked, asking;
    private TitlePage page;

    TitleScreen(TvActivity tv, Catalog.Title t) {
        super(tv);
        this.title = t;
    }

    /** Its page draws an opaque ground over the whole screen (the window's own isn't drawn under it). */
    @Override boolean opaque() {
        return true;
    }

    @Override View build() {
        Cine.init(tv);
        FrameLayout f = new FrameLayout(tv);
        // No background of its own: its page draws its opaque ground first (a full screen of fill saved each frame).
        page = new TitlePage(tv, this);
        page.vertical(com.gathin.homefieldtv.prefs.HomeStyle.editorial(tv));
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        details = Catalog.get(tv).cachedDetails(title);
        fill();
        if (details == null) ask();
        // TMDB's logo, cast and seasons: kept ones at once, else asked for (when the launcher gives its key).
        Tmdb.Extras kept = Tmdb.keptExtras(tv, title);
        if (kept != null) page.extras(kept);
        Catalog.Title want = title;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.extras(tv, want);
            if (x != null && x != kept) Exec.main(() -> {
                if (page != null && title == want) page.extras(x);
            });
        });
        // The family's TorBox read meanwhile (when it is in use), so that Play finds its copies at once.
        TorBox.warm(tv);
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
        if (page != null) page.setActive(tv.tabs() == null || !tv.tabs().active());
        // Back from a film: its resume point has moved.
        if (built()) fill();
    }

    @Override boolean shell() {
        return true;
    }

    /** Reached from Movies or TV Shows: "‹ Movies", "‹ TV Shows" where the tabs would be. */
    @Override String backLine() {
        return title.series ? "TV Shows" : "Movies";
    }

    /** Down from the tab bar: the buttons. */
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

    private void fill() {
        Resume.Point p = point();
        List<TitlePage.Button> buttons = new ArrayList<>();
        if (!title.series) {
            if (p != null && p.position > 0) {
                TitlePage.Button resume = new TitlePage.Button("Resume", TitlePage.PLAY, true, () -> tv.play(title, null, p.position, false));
                resume.after = left(p);
                buttons.add(resume);
                buttons.add(new TitlePage.Button("From the Start", TitlePage.RESTART, false, () -> tv.play(title, null, 0, true)));
            } else {
                buttons.add(new TitlePage.Button("Play", TitlePage.PLAY, true, () -> tv.play(title, null, 0, true)));
            }
        } else if (details == null) {
            buttons.add(new TitlePage.Button(asked && !asking ? "Couldn't get the episodes: try again" : "Finding the episodes…",
                    TitlePage.PLAY, true, this::ask));
        } else {
            List<Catalog.Episode> all = details.episodes();
            Catalog.Episode resume = p == null ? null : details.episode(p.episode);
            if (resume != null) {
                String label = (p.position > 0 ? "Resume" : "Play") + " S" + resume.season + " E" + resume.number;
                // Resume: straight away from the copy it was started on; an episode from its start (the next one): every source first.
                TitlePage.Button main = new TitlePage.Button(label, TitlePage.PLAY, true, () -> tv.play(title, resume, p.position, p.position <= 0));
                if (p.position > 0) main.after = left(p);
                buttons.add(main);
                if (!all.isEmpty()) {
                    Catalog.Episode first = all.get(0);
                    buttons.add(new TitlePage.Button("From the Start", TitlePage.RESTART, false, () -> tv.play(title, first, 0, true)));
                }
            } else if (!all.isEmpty()) {
                Catalog.Episode first = all.get(0);
                buttons.add(new TitlePage.Button("Play S" + first.season + " E" + first.number, TitlePage.PLAY, true,
                        () -> tv.play(title, first, 0, true)));
            } else {
                buttons.add(new TitlePage.Button(title.torbox() ? "No episodes in your TorBox" : "No episodes in your playlist",
                        TitlePage.PLAY, true, () -> { }));
            }
        }
        boolean on = MyList.has(tv, title.key);
        buttons.add(new TitlePage.Button("My List", on ? TitlePage.TICK : TitlePage.PLUS, false, on, this::toggleList));
        String trailer = trailerId(details == null ? "" : details.trailer);
        if (!trailer.isEmpty()) buttons.add(new TitlePage.Button("Trailer", TitlePage.TRAILER, false, () -> trailer(trailer)));
        if (p != null) buttons.add(new TitlePage.Button("Remove", TitlePage.BIN, false, this::forget));

        List<Catalog.Title> more = new ArrayList<>();
        for (Catalog.Category c : Catalog.get(tv).categories(title.series)) {
            if (!c.name.equals(title.category)) continue;
            for (Catalog.Title t : c.titles) {
                // The first 30: never on through a category of thousands.
                if (more.size() >= 30) break;
                if (t != title) more.add(t);
            }
        }
        if (com.gathin.homefieldtv.prefs.HomeStyle.editorial(tv)) buttons = menu(buttons.get(0), on, trailer, more);
        page.set(title, details, buttons, more, resumeEpisode(p));
    }

    /**
     * Home style Editorial's menu down the left, as the family's other film app has it: Play (or Resume, the main one
     * as made), Trailer, More episodes (a series with any), Add to My List (or Remove from My List), More like this.
     */
    private List<TitlePage.Button> menu(TitlePage.Button main, boolean on, String trailer, List<Catalog.Title> more) {
        List<TitlePage.Button> out = new ArrayList<>();
        out.add(main);
        if (!trailer.isEmpty()) out.add(new TitlePage.Button("Trailer", TitlePage.TRAILER, false, () -> trailer(trailer)));
        if (title.series && details != null && !details.episodes().isEmpty()) {
            out.add(new TitlePage.Button("More episodes", TitlePage.EPS, false, () -> page.toPart(TitlePage.EPISODES)));
        }
        out.add(new TitlePage.Button(on ? "Remove from My List" : "Add to My List", on ? TitlePage.TICK : TitlePage.PLUS, false, on,
                this::toggleList));
        if (!more.isEmpty()) out.add(new TitlePage.Button("More like this", TitlePage.LIKE, false, () -> page.toPart(TitlePage.MORE)));
        return out;
    }

    /** "18 min left" after Resume: how much of it is still to watch ("" when that isn't known). */
    private static String left(Resume.Point p) {
        return p != null && p.duration > p.position && p.position > 0 ? LiveText.mins(p.duration - p.position) + " left" : "";
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

    /** Pure: a trailer's YouTube id from what a provider gives ("dQw4w9WgXcQ", a watch or youtu.be address), else "". */
    static String trailerId(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.matches("[A-Za-z0-9_-]{11}")) return t;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:v=|youtu\\.be/|embed/|shorts/)([A-Za-z0-9_-]{11})").matcher(t);
        return m.find() ? m.group(1) : "";
    }

    /** The trailer in the TV's YouTube app (nothing is played here). */
    private void trailer(String id) {
        trailer(tv, id);
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

    /** Up from the buttons: the tab bar comes back over the page, the remote on it. */
    @Override public void up() {
        if (tv.tabs() == null) return;
        tv.tabs().enter();
        page.setActive(false);
    }

    @Override public void play(Catalog.Episode ep) {
        Resume.Point p = Resume.get(tv, title.key, ep.id);
        // An episode chosen: every source shown first, then on from where it was left, if it was.
        tv.play(title, ep, p == null ? 0 : p.position, true);
    }

    @Override public void trailer() {
        String id = trailerId(details == null ? "" : details.trailer);
        if (!id.isEmpty()) trailer(id);
    }

    @Override public void open(Catalog.Title t) {
        tv.openTitle(t);
    }

    @Override public void person(String name) {
        GuideActions.search(tv, name);
    }

    @Override public float progress(Catalog.Episode ep) {
        Resume.Point p = Resume.get(tv, title.key, ep.id);
        return p == null || p.position <= 0 ? -1f : p.progress();
    }

    /** Previews: the remote on a part (TitlePage.BUTTONS…) and along it. */
    void previewFocus(int part, int along) {
        page.focus(part, along);
    }

    /** Previews: TMDB's extras as if it had answered. */
    void previewExtras(Tmdb.Extras x) {
        page.extras(x);
    }
}
