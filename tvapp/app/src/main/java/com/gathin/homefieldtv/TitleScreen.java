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
 * A film's or a series' page (TitlePage), under the top bar (TopBar). A film: Play, or Resume and From the start where
 * it was left part-way. A series: Resume S2 E8 (or Play S1 E1) and From the start. Then My list (a tick once on it),
 * the trailer (in the TV's YouTube app; a series has it as the first tile of its episodes instead) and, where something
 * was started, Remove from Continue watching. Play shows every source first, the best first (VodActivity's Choose a
 * source); Resume carries on straight away from the copy it was started on. TMDB's logo, cast and seasons' colours come
 * when it has answered (kept on the TV after, so the page has them at once next time).
 */
final class TitleScreen extends Screen implements TitlePage.Host {
    private Catalog.Title title;
    private Catalog.Details details;
    private boolean asked, asking, onBar;
    private TitlePage page;
    private TopBar bar;

    TitleScreen(TvActivity tv, Catalog.Title t) {
        super(tv);
        this.title = t;
    }

    @Override View build() {
        Cine.init(tv);
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Cine.GROUND);
        page = new TitlePage(tv, this);
        bar = new TopBar(tv, title.series ? TopBar.SHOWS : TopBar.MOVIES);
        page.bar(bar);
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        f.addView(bar, new FrameLayout.LayoutParams(-1, Ui.px(TopBar.H + 90f)));
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
        // Back from a film: its resume point has moved.
        if (built()) fill();
    }

    @Override void tick() {
        if (bar != null) bar.invalidate();
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
                buttons.add(new TitlePage.Button("Resume", TitlePage.PLAY, true, () -> tv.play(title, null, p.position, false)));
                buttons.add(new TitlePage.Button("From the start", TitlePage.RESTART, false, () -> tv.play(title, null, 0, true)));
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
                buttons.add(new TitlePage.Button(label, TitlePage.PLAY, true, () -> tv.play(title, resume, p.position, p.position <= 0)));
                if (!all.isEmpty()) {
                    Catalog.Episode first = all.get(0);
                    buttons.add(new TitlePage.Button("From the start", TitlePage.RESTART, false, () -> tv.play(title, first, 0, true)));
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
        if (!trailer.isEmpty() && !title.series) {
            buttons.add(new TitlePage.Button("Trailer", TitlePage.TRAILER, false, () -> trailer(trailer)));
        }
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
        page.set(title, details, buttons, more, resumeEpisode(p));
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
        try {
            tv.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(tv, "There's no app on this TV to play the trailer", Toast.LENGTH_SHORT).show();
        }
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (onBar) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                leaveBar();
                return true;
            }
            return bar.key(code) || code == KeyEvent.KEYCODE_DPAD_UP;
        }
        return page.key(code);
    }

    private void leaveBar() {
        onBar = false;
        bar.enter(false);
        page.enter();
    }

    @Override void ok() {
        if (onBar) {
            TopBar.go(tv, bar.focus, bar.current, title.series);
            return;
        }
        page.ok();
    }

    @Override boolean back() {
        if (onBar) {
            leaveBar();
            return true;
        }
        return page.back();
    }

    @Override public void up() {
        onBar = true;
        bar.enter(true);
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
