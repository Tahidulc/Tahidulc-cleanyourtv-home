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
 * A film's or a series' page, as the family's other app has it: its picture on the right fading into the ground, its
 * words at the top left (VodHero: title and year, rating, facts, cast, director, story), and the buttons and rows
 * under them (DetailPage). A film: Play, or Resume and Play from beginning where it was left part-way. A series:
 * Resume S2 E8 (or Play S1 E1) and Play from beginning, then its seasons' rows of episodes. Round buttons after them:
 * the trailer (in the TV's YouTube app), My list's bookmark (with a plus, or filled once on it), and where something
 * was started, taking it off Continue watching. Play shows every source first, the best first (VodActivity's Choose a
 * source); Resume carries on straight away from the copy it was started on.
 */
final class TitleScreen extends Screen implements DetailPage.Host {
    private Catalog.Title title;
    private Catalog.Details details;
    private boolean asked, asking;
    private VodHero hero;
    private Backdrop picture;
    private DetailPage page;

    TitleScreen(TvActivity tv, Catalog.Title t) {
        super(tv);
        this.title = t;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Palette.GROUND);
        picture = new Backdrop(tv, 560f, 0f, 1360f, 765f, Pics.DARK);
        hero = new VodHero(tv);
        hero.w = 860f;
        page = new DetailPage(tv, this, hero);
        f.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        f.addView(hero, new FrameLayout.LayoutParams(-1, -1));
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
                buttons.add(new DetailPage.Button("Resume", Dark.PLAY, true, () -> tv.play(title, null, p.position, false)));
                buttons.add(new DetailPage.Button("Play from beginning", Dark.RESTART, true, () -> tv.play(title, null, 0, true)));
            } else {
                buttons.add(new DetailPage.Button("Play", Dark.PLAY, true, () -> tv.play(title, null, 0, true)));
            }
        } else if (details == null) {
            buttons.add(new DetailPage.Button(asked && !asking ? "Couldn't get the episodes: try again" : "Finding the episodes…",
                    Dark.PLAY, true, this::ask));
        } else {
            List<Catalog.Episode> all = details.episodes();
            Catalog.Episode resume = p == null ? null : details.episode(p.episode);
            if (resume != null) {
                String label = (p.position > 0 ? "Resume" : "Play") + " S" + resume.season + " E" + resume.number;
                // Resume: straight away from the copy it was started on; an episode from its start (the next one): every source first.
                buttons.add(new DetailPage.Button(label, Dark.PLAY, true, () -> tv.play(title, resume, p.position, p.position <= 0)));
                if (!all.isEmpty()) {
                    Catalog.Episode first = all.get(0);
                    buttons.add(new DetailPage.Button("Play from beginning", Dark.RESTART, true, () -> tv.play(title, first, 0, true)));
                }
            } else if (!all.isEmpty()) {
                Catalog.Episode first = all.get(0);
                buttons.add(new DetailPage.Button("Play S" + first.season + " E" + first.number, Dark.PLAY, true,
                        () -> tv.play(title, first, 0, true)));
            } else {
                buttons.add(new DetailPage.Button(title.torbox() ? "No episodes in your TorBox" : "No episodes in your playlist",
                        Dark.PLAY, true, () -> { }));
            }
        }
        String trailer = trailerId(details == null ? "" : details.trailer);
        if (!trailer.isEmpty()) {
            buttons.add(new DetailPage.Button("Trailer", DetailPage.GLYPH + Glyphs.TRAILER, false, () -> trailer(trailer)));
        }
        boolean on = MyList.has(tv, title.key);
        buttons.add(new DetailPage.Button(on ? "On My list" : "Add to My list", DetailPage.GLYPH + (on ? Glyphs.BOOKMARK_ON
                : Glyphs.BOOKMARK_ADD), false, this::toggleList));
        if (p != null) {
            buttons.add(new DetailPage.Button("Remove from Continue watching", DetailPage.GLYPH + Glyphs.TRASH, false, this::forget));
        }

        List<PosterArt.Item> more = new ArrayList<>();
        for (Catalog.Category c : Catalog.get(tv).categories(title.series)) {
            if (!c.name.equals(title.category)) continue;
            for (Catalog.Title t : c.titles) {
                // The first 30: never on through a category of thousands.
                if (more.size() >= 30) break;
                if (t == title) continue;
                PosterArt.Item it = new PosterArt.Item(t);
                it.genre = PosterArt.genreOf(t.category);
                Catalog.Details read = Catalog.get(tv).cachedDetails(t);
                if (read != null) it.backdrop = read.backdrop;
                more.add(it);
            }
        }
        hero.show(title, details, title.torbox() ? Catalog.TORBOX : "");
        picture.set(details != null && !details.backdrop.isEmpty() ? details.backdrop : title.backdrop);
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

    @Override public void left() {
        tv.pop();
    }

    @Override public float progress(Catalog.Episode ep) {
        Resume.Point p = Resume.get(tv, title.key, ep.id);
        return p == null || p.position <= 0 ? -1f : p.progress();
    }

    /** Previews: the remote on a row (-1: the buttons) and along it. */
    void previewFocus(int row, int along) {
        page.focus(row, along);
    }
}
