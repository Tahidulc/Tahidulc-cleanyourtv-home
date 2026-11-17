package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Movies, or TV shows, as the family's "TV show card" has them, made for the remote: the top bar (Home, TV shows,
 * Movies, Recently added; Homefield's mark; Search, My list), and under it the picture of whatever the remote is on
 * across the page, fading into the page's colour (the picture's own, made deep, changing as the remote moves), with its
 * logo or big lettering, its facts and what it's about. Under that the rows (CineRows): Continue watching with how far
 * in, My list, Recently added, Your TorBox, then every category, each ending in a tile that opens all of it (ListScreen).
 * OK on a poster opens its page; on a Continue watching tile it carries on at once. Up from the first row goes to the
 * top bar; Back goes there too, then back Home.
 *
 * <p>Nothing here goes through every title on the main thread: the rows are the catalogue's own lists, cut to their
 * first few.
 */
final class BrowseScreen extends Screen implements CineRows.Host {
    static final String MINE = "mine", CONTINUE = "continue", RECENT = "recent";
    /** How many of a list a row shows before its "All" tile. */
    static final int ROW_MAX = 24;
    static final String[] SORTS = {"As in the playlist", "By name", "Newest first", "Best rated"};
    /** My list's page (MyListScreen) still slides its content by these (design pixels). */
    static final float WITH_COLUMN = 672f, WITHOUT = 56f;

    private final boolean series;
    private TopBar bar;
    private Hero hero;
    private CineRows rows;
    private boolean onBar;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title pending, extrasFor;
    private final Runnable fetch = this::fetchDetails, extras = this::fetchExtras;

    BrowseScreen(TvActivity tv, boolean series) {
        super(tv);
        this.series = series;
    }

    @Override View build() {
        Cine.init(tv);
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Cine.GROUND);
        hero = new Hero(tv);
        rows = new CineRows(tv);
        rows.setHost(this);
        bar = new TopBar(tv, series ? TopBar.SHOWS : TopBar.MOVIES);
        f.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        f.addView(bar, new FrameLayout.LayoutParams(-1, Ui.px(TopBar.H + 90f)));
        fill();
        if (rows.isEmpty()) toBar(true);
        return f;
    }

    @Override void shown() {
        // No live picture here: the stream let go (a film may want the provider's one connection).
        tv.live.stop();
        tv.live.place(null);
        fill();
    }

    @Override void hidden() {
        h.removeCallbacks(fetch);
        h.removeCallbacks(extras);
    }

    @Override void tick() {
        bar.invalidate();
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

    /** Pure: a Continue watching tile's line: "S2 E4 · 32m left", "32m left", "S2 E4", or "" (subtitle "S2 E4 · Pilot"). */
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

    /** A title's poster tile (its wide picture where Settings → Posters say Landscape). */
    private CineRows.Tile poster(Catalog.Title t) {
        String pic = t.poster;
        if (PosterArt.landscape) {
            Catalog.Details d = Catalog.get(tv).cachedDetails(t);
            pic = d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
        }
        return new CineRows.Tile(t, pic, "", -1f, t);
    }

    /** A Continue watching tile: the wide picture (the episode's, where it was left), where it was and how far in. */
    private CineRows.Tile resumeTile(Catalog.Title t) {
        Resume.Point p = t.series ? Resume.latest(tv, t.key) : Resume.get(tv, t.key, "");
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        String pic = d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
        if (p != null && t.series && !p.picture.isEmpty()) pic = p.picture;
        String line = p == null ? "" : resumeLine(t.series ? p.subtitle : "", p.position, p.duration);
        return new CineRows.Tile(t, pic, line, p != null && p.position > 0 ? p.progress() : -1f, p);
    }

    private List<CineRows.Tile> posters(List<Catalog.Title> titles) {
        int n = Math.min(ROW_MAX, titles.size());
        List<CineRows.Tile> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(poster(titles.get(i)));
        return out;
    }

    /** The page's rows: Continue watching, My list, Recently added, Your TorBox, then each category. */
    private void fill() {
        if (rows == null) return;
        Catalog cat = Catalog.get(tv);
        List<CineRows.Row> out = new ArrayList<>();
        List<Catalog.Title> going = continuing();
        if (!going.isEmpty()) {
            List<CineRows.Tile> tiles = new ArrayList<>();
            for (int i = 0; i < Math.min(ROW_MAX, going.size()); i++) tiles.add(resumeTile(going.get(i)));
            out.add(new CineRows.Row("Continue watching", CineRows.WIDE, tiles, going.size(), CONTINUE));
        }
        List<Catalog.Title> mine = mine();
        if (!mine.isEmpty()) out.add(new CineRows.Row("My List", CineRows.POSTERS, posters(mine), mine.size(), MINE));
        List<Catalog.Title> recent = cat.recent(series, 60);
        if (!recent.isEmpty()) out.add(new CineRows.Row("Recently added", CineRows.POSTERS, posters(recent), recent.size(), RECENT));
        List<Catalog.Category> cats = cat.categories(series);
        for (Catalog.Category c : cats) {
            if (c.name.equals(Catalog.TORBOX) && !c.titles.isEmpty()) {
                out.add(new CineRows.Row(c.name, CineRows.POSTERS, posters(c.titles), c.titles.size(), "cat:" + c.name));
            }
        }
        for (Catalog.Category c : cats) {
            if (c.name.equals(Catalog.TORBOX) || c.titles.isEmpty()) continue;
            out.add(new CineRows.Row(c.name, CineRows.POSTERS, posters(c.titles), c.titles.size(), "cat:" + c.name));
        }
        rows.set(out);
        showHero();
    }

    /** A list's titles by its tag ("mine", "recent", "cat:Drama"). */
    private List<Catalog.Title> listOf(Object tag) {
        if (MINE.equals(tag)) return mine();
        if (CONTINUE.equals(tag)) return continuing();
        if (RECENT.equals(tag)) return Catalog.get(tv).recent(series, 120);
        String name = tag instanceof String && ((String) tag).startsWith("cat:") ? ((String) tag).substring(4) : "";
        for (Catalog.Category c : Catalog.get(tv).categories(series)) if (c.name.equals(name)) return c.titles;
        return new ArrayList<>();
    }

    // ---- the big picture -----------------------------------------------------------------------------------------

    private void showHero() {
        h.removeCallbacks(fetch);
        h.removeCallbacks(extras);
        CineRows.Tile t = rows.tile();
        CineRows.Row rw = rows.current();
        if (t == null) {
            if (rw != null) hero.list(rw.title, rw.allWords);
            else hero.empty(emptyWhy());
            return;
        }
        Catalog.Details d = Catalog.get(tv).cachedDetails(t.title);
        hero.show(t.title, d, rw != null && rw.kind == CineRows.WIDE ? t.line : "");
        pending = d == null ? t.title : null;
        if (pending != null) h.postDelayed(fetch, t.title.series ? 900 : 500);
        // TMDB's logo when the remote rests on it (kept ones at once).
        extrasFor = t.title;
        Tmdb.Extras kept = Tmdb.keptExtras(tv, t.title);
        if (kept != null) hero.extras(t.title, kept);
        else h.postDelayed(extras, 700);
    }

    private String emptyWhy() {
        Catalog cat = Catalog.get(tv);
        if (Sources.list(tv).isEmpty()) return "Add your playlist in Settings: its " + kind() + " show here.";
        if (all().isEmpty() && (cat.busy() || Library.get(tv).busy())) return "Loading your playlist's " + kind() + "…";
        if (all().isEmpty() && !cat.status().isEmpty()) return cat.status();
        return "";
    }

    private void fetchDetails() {
        Catalog.Title want = pending;
        if (want == null) return;
        Catalog.get(tv).details(want, d -> {
            CineRows.Tile t = rows.tile();
            if (t != null && t.title == want) {
                CineRows.Row rw = rows.current();
                hero.show(want, d, rw != null && rw.kind == CineRows.WIDE ? t.line : "");
            }
        });
    }

    private void fetchExtras() {
        Catalog.Title want = extrasFor;
        if (want == null) return;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.extras(tv, want);
            if (x != null) Exec.main(() -> {
                if (hero != null) hero.extras(want, x);
            });
        });
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    private void toBar(boolean on) {
        onBar = on;
        bar.enter(on);
        rows.setActive(!on);
    }

    @Override boolean key(int code, KeyEvent e) {
        if (onBar) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && !rows.isEmpty()) {
                toBar(false);
                return true;
            }
            return bar.key(code) || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        return rows.key(code);
    }

    @Override void ok() {
        if (onBar) {
            if (bar.focus == bar.current && !rows.isEmpty()) toBar(false);
            else TopBar.go(tv, bar.focus, bar.current, series);
            return;
        }
        rows.ok();
    }

    @Override boolean back() {
        if (!onBar) {
            toBar(true);
            return true;
        }
        return false;
    }

    // ---- the rows ---------------------------------------------------------------------------------------------------

    @Override public void focused(CineRows.Row row, int index) {
        showHero();
    }

    @Override public void chosen(CineRows.Row row, int index) {
        if (index >= row.tiles.size()) {
            List<Catalog.Title> titles = listOf(row.tag);
            if (!titles.isEmpty()) tv.push(new ListScreen(tv, series, row.title, titles));
            return;
        }
        Catalog.Title t = row.tiles.get(index).title;
        if (CONTINUE.equals(row.tag) && carryOn(t)) return;
        tv.openTitle(t);
    }

    /** A Continue watching tile: on at once from where it was (an episode needs its series' page read; else its page). */
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
        if (dir == Ui.UP) {
            toBar(true);
            return true;
        }
        return dir == Ui.LEFT;
    }

    /** Previews and tests: the remote on a row and a tile. */
    void focusRow(int row, int index) {
        rows.focus(row, index);
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

    // ---- the big picture's view ---------------------------------------------------------------------------------------

    /**
     * The page under the bar: its colour, the picture of what the remote is on across it (the last one fading out as the
     * new one comes), fading at the left and foot into the colour, and over it the logo or lettering, the facts, and
     * what it's about; the words come in a little after the picture, rising into place.
     */
    private final class Hero extends View {
        static final float TEXT_X = TopBar.LEFT, LOGO_TOP = 168f, LOGO_W = 780f, LOGO_H = 230f, FACTS_Y = 450f, PLOT_Y = 478f;
        private Catalog.Title title, extrasTitle;
        private Tmdb.Extras shownExtras;
        private String picture = "", was = "", line = "", listName = "", listNote = "", why = "";
        private final Smooth swap = new Smooth(170f, 0.004f), words = new Smooth(150f, 0.004f);
        private final Cine.Wash wash = new Cine.Wash();
        private final Lettering lettering = new Lettering();
        private final FactsLine facts = new FactsLine(26f);
        private final Paint ground = new Paint(), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final TextPaint plotP = Cine.text(26f, Cine.inter(), Cine.SOFT), lineP = Cine.text(28f, Cine.interSemi(), Cine.TEXT),
                bigP = Cine.text(84f, Cine.outfit(800), Cine.TEXT), noteP = Cine.text(28f, Cine.inter(), Cine.SOFT);
        private StaticLayout plot;
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate, recolour = this::recolour;

        Hero(Context c) {
            super(c);
        }

        void show(Catalog.Title t, Catalog.Details d, String resume) {
            boolean fresh = t != title;
            title = t;
            listName = "";
            why = "";
            line = resume == null ? "" : resume;
            String pic = d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
            if (!pic.equals(picture)) {
                was = picture;
                picture = pic;
                swap.jump(0f);
                swap.to(1f);
            }
            if (fresh) {
                words.jump(0f);
                words.to(1f);
                lettering.logo("");
            }
            facts.set(t, d, extrasTitle == t ? shownExtras : null);
            lettering.set(t.name, Ui.f(LOGO_W), Ui.f(LOGO_H), Ui.f(132f));
            String p = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
            plot = p.isEmpty() ? null : Ui.layout(p, plotP, Ui.px(900f), line.isEmpty() ? 3 : 2);
            recolour();
            invalidate();
        }

        /** TMDB's extras for the title on show: its logo once read (never a blank between), its years and age. */
        void extras(Catalog.Title t, Tmdb.Extras x) {
            if (t != title || x == null) return;
            extrasTitle = t;
            shownExtras = x;
            facts.set(t, Catalog.get(tv).cachedDetails(t), x);
            if (!x.logo.isEmpty()) {
                Runnable shown = () -> {
                    if (title == t) {
                        lettering.logo(x.logo);
                        invalidate();
                    }
                };
                if (Pics.get(x.logo, Ui.px(LOGO_W), Ui.px(LOGO_H), Pics.FIT, shown) != null) shown.run();
            }
            invalidate();
        }

        /** A row's "All" tile: its name and how many, no picture. */
        void list(String name, String count) {
            title = null;
            listName = Ui.fit(name, bigP, Ui.f(1500f));
            listNote = count + "  ·  OK shows them all";
            was = picture;
            picture = "";
            swap.jump(0f);
            swap.to(1f);
            invalidate();
        }

        void empty(String note) {
            title = null;
            listName = "";
            why = note;
            picture = "";
            was = "";
            invalidate();
        }

        private void recolour() {
            int art = Tint.of(picture, recolour);
            wash.set(Cine.shade(art, 0.6f, 0.15f));
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int ht, int ow, int oh) {
            wash.clear();
            float H = Ui.f(1080f);
            wash.add(0, 0, Ui.f(1300f), H, 0, 0, Ui.f(1300f), 0, 0.92f, 0.55f, 0.42f, 0f);
            wash.add(0, Ui.f(300f), w, Ui.f(760f), 0, Ui.f(300f), 0, Ui.f(760f), 0f, 0.62f, 0.55f, 1f);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = swap.step();
            moving |= words.step();
            moving |= wash.step();
            int w = getWidth(), ht = getHeight();
            ground.setColor(wash.colour());
            c.drawRect(0, 0, w, ht, ground);
            r.set(0, 0, w, Ui.f(1080f));
            float k = swap.value;
            if (k < 1f && !was.isEmpty()) {
                Bitmap b = Pics.cached(was, 1280, 720, Pics.CROP);
                if (b != null) {
                    bmp.setAlpha(Math.round(255 * (1f - k)));
                    c.drawBitmap(b, null, r, bmp);
                }
            }
            if (!picture.isEmpty()) {
                Bitmap b = Pics.get(picture, 1280, 720, Pics.CROP, redraw);
                if (b != null) {
                    bmp.setAlpha(Math.round(255 * k));
                    c.drawBitmap(b, null, r, bmp);
                }
            }
            wash.draw(c);
            // Below the picture, the page's own colour (under the rows).
            c.drawRect(0, Ui.f(760f), w, ht, ground);

            int a = Math.round(255 * words.value);
            float rise = Ui.f(16f) * (1f - words.value), x = Ui.f(TEXT_X);
            if (title != null) {
                lettering.draw(c, x, Ui.f(LOGO_TOP) + rise, a, redraw);
                facts.draw(c, x, Ui.f(FACTS_Y) + rise, a);
                float y = Ui.f(PLOT_Y) + rise;
                if (!line.isEmpty()) {
                    lineP.setAlpha(a);
                    c.drawText(line, x, y - lineP.ascent(), lineP);
                    y += Ui.f(44f);
                }
                if (plot != null) {
                    plotP.setAlpha(Math.round(a * 0.9f));
                    Ui.draw(c, plot, x, y);
                }
            } else if (!listName.isEmpty()) {
                bigP.setAlpha(255);
                c.drawText(listName, x, Ui.f(LOGO_TOP + 170f), bigP);
                c.drawText(listNote, x, Ui.f(FACTS_Y), noteP);
            } else {
                c.drawText(series ? "No TV shows" : "No movies", x, Ui.f(LOGO_TOP + 170f), bigP);
                if (!why.isEmpty()) c.drawText(why, x, Ui.f(FACTS_Y), noteP);
            }
            if (moving) postInvalidateOnAnimation();
        }
    }
}
