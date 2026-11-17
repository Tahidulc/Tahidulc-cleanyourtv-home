package com.gathin.homefieldtv;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.TorBox;

import java.util.ArrayList;
import java.util.List;

/**
 * A film's or a series' page, dark: its backdrop filling the screen, its title large at the top left, and at the foot
 * Play (or Resume where it was left) with round buttons beside it (My list's heart, from the start, the trailer), the
 * story and facts in the middle and who is in it on the right. Down from there: for a series its seasons as pills over
 * one row of every episode, season after season, the season's pill moving along as the row goes (and a season chosen
 * sliding the row to its first episode); then the trailer, the cast, and more from its category.
 */
final class TitleScreen extends Screen implements DetailPage.Host {
    private Catalog.Title title;
    private Catalog.Details details;
    private boolean asked, asking;
    private DetailPage page;

    TitleScreen(TvActivity tv, Catalog.Title t) {
        super(tv);
        this.title = t;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Dark.GROUND);
        page = new DetailPage(tv, this);
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        details = Catalog.get(tv).cachedDetails(title);
        fill();
        if (details == null) ask();
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
        List<DetailPage.Button> buttons = new ArrayList<>();
        if (!title.series) {
            if (p != null && p.position > 0) {
                buttons.add(new DetailPage.Button("Resume  ·  " + Ui.left(p.duration - p.position), -1, () -> tv.play(title, null, p.position, false)));
                buttons.add(new DetailPage.Button("From the start", Dark.RESTART, () -> tv.play(title, null, 0, true)));
            } else {
                buttons.add(new DetailPage.Button("Play", -1, () -> tv.play(title, null, 0, true)));
            }
        } else if (details == null) {
            buttons.add(new DetailPage.Button(asked && !asking ? "Couldn't get the episodes: try again" : "Finding the episodes…", -1, this::ask));
        } else {
            List<Catalog.Episode> all = details.episodes();
            Catalog.Episode resume = p == null ? null : details.episode(p.episode);
            if (resume != null) {
                String label = (p.position > 0 ? "Resume" : "Play") + " S" + resume.season + " E" + resume.number
                        + (p.position > 0 ? "  ·  " + Ui.left(p.duration - p.position) : "");
                // Resume: straight away from the copy it was started on; an episode from its start (the next one): every source first.
                buttons.add(new DetailPage.Button(label, -1, () -> tv.play(title, resume, p.position, p.position <= 0)));
            } else if (!all.isEmpty()) {
                Catalog.Episode first = all.get(0);
                buttons.add(new DetailPage.Button("Play S" + first.season + " E" + first.number, -1, () -> tv.play(title, first, 0, true)));
            } else {
                buttons.add(new DetailPage.Button(title.torbox() ? "No episodes in your TorBox" : "No episodes in your playlist", -1, () -> { }));
            }
            if (resume != null && !all.isEmpty()) {
                Catalog.Episode first = all.get(0);
                buttons.add(new DetailPage.Button("From the first episode", Dark.RESTART, () -> tv.play(title, first, 0, true)));
            }
        }
        boolean on = MyList.has(tv, title.key);
        buttons.add(1, new DetailPage.Button(on ? "On My list" : "Add to My list", on ? Dark.HEART_FULL : Dark.HEART, this::toggleList));
        String trailer = trailerId(details == null ? "" : details.trailer);
        if (!trailer.isEmpty()) buttons.add(new DetailPage.Button("Trailer", Dark.PLAY, () -> trailer(trailer)));

        List<PosterArt.Item> more = new ArrayList<>();
        for (Catalog.Category c : Catalog.get(tv).categories(title.series)) {
            if (!c.name.equals(title.category)) continue;
            for (Catalog.Title t : c.titles) {
                if (t == title || more.size() >= 30) continue;
                PosterArt.Item it = new PosterArt.Item(t);
                it.genre = PosterArt.genreOf(t.category);
                Catalog.Details read = Catalog.get(tv).cachedDetails(t);
                if (read != null) it.backdrop = read.backdrop;
                more.add(it);
            }
        }
        page.set(title, details, buttons, trailer, more, resumeEpisode(p));
    }

    private String resumeEpisode(Resume.Point p) {
        return p == null || details == null || details.episode(p.episode) == null ? "" : p.episode;
    }

    private void toggleList() {
        boolean on = MyList.toggle(tv, title.key);
        Toast.makeText(tv, on ? title.name + " added to My list" : title.name + " taken off My list", Toast.LENGTH_SHORT).show();
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
        return page.key(code);
    }

    @Override void ok() {
        page.ok();
    }

    @Override boolean back() {
        return page.back();
    }

    @Override public void play(Catalog.Episode ep) {
        Resume.Point p = Resume.get(tv, title.key, ep.id);
        // An episode chosen: every source shown first (as the phone's), then on from where it was left, if it was.
        tv.play(title, ep, p == null ? 0 : p.position, true);
    }

    @Override public void open(Catalog.Title t) {
        tv.openTitle(t);
    }

    @Override public void trailerChosen(String id) {
        trailer(id);
    }

    @Override public void left() {
        tv.pop();
    }

    @Override public float progress(Catalog.Episode ep) {
        Resume.Point p = Resume.get(tv, title.key, ep.id);
        return p == null || p.position <= 0 ? -1f : p.progress();
    }
}
