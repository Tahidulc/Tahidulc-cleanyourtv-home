package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Genres;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Home, in the dark look of Films, TV shows and the guide: the rail down the left (Homefield's mark and the time, then
 * Home, the TV guide, Films, TV shows, Sports, Search and Settings, where the remote starts), the big picture of whatever
 * the remote is on, the mini TV at the top right playing the channel last watched, and rows under them: what's on now,
 * Continue watching, the family's own TorBox titles, the newest films and TV shows from the playlists, sport on now and
 * the favourite channels.
 */
final class HomeScreen extends Screen implements Rows.Host, LiveTv.Listener {
    private Rail rail;
    private MiniTv mini;
    private Hero hero;
    private Rows rows;
    private boolean inMenu = true;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title pending;
    private Rows.Tile heroTile;
    private final Runnable fetch = this::fetchDetails;

    HomeScreen(TvActivity tv) {
        super(tv);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Dark.GROUND);
        rail = new Rail(tv, Rail.HOME);
        // The remote starts on the rail, as on the old menu, and comes back to the icon it was on.
        rail.keep = true;
        rail.setActive(true);
        mini = new MiniTv(tv);
        hero = new Hero(tv);
        hero.titleLines = 2;
        hero.plotLines = 3;
        hero.setTitleSize(72f);
        rows = new Rows(tv);
        rows.setHost(this);
        rows.setActive(false);
        f.addView(hero.picture, new FrameLayout.LayoutParams(-1, -1));
        f.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        f.addView(mini, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        fill();
        return f;
    }

    @Override void shown() {
        tv.live.addListener(this);
        tv.live.place(mini.tvRect());
        Channel c = tv.channelToPlay();
        // Back here because the phone stopped what it sent: the mini TV stays off until the remote plays something.
        if (tv.takeMiniTvHeld()) tv.live.stop();
        else if (c != null) tv.live.play(c);
        else tv.live.forget();
        fill();
    }

    @Override void hidden() {
        tv.live.removeListener(this);
        h.removeCallbacks(fetch);
    }

    @Override public void liveChanged() {
        updateNote();
        mini.invalidate();
    }

    private void updateNote() {
        Library lib = Library.get(tv);
        String note;
        if (!lib.hasSource()) note = "Add your playlist in Settings to watch TV here.";
        else if (!lib.ready()) note = lib.busy() ? "Loading your channels…" : lib.status().isEmpty() ? "Loading your channels…" : lib.status();
        else note = "";
        if (!note.equals(mini.note)) {
            mini.note = note;
            mini.invalidate();
        }
    }

    @Override void tick() {
        rail.invalidate();
        mini.invalidate();
        fill();
    }

    @Override void channelsChanged() {
        if (tv.isTop(this)) {
            Channel c = tv.channelToPlay();
            if (c != null) tv.live.play(c);
            else tv.live.forget();
        }
        fill();
    }

    @Override void catalogChanged() {
        fill();
    }

    // ---- the rows -----------------------------------------------------------------------------------------------

    private void fill() {
        if (rows == null) return;
        updateNote();
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        long now = System.currentTimeMillis();
        List<Rows.Row> list = new ArrayList<>();

        List<Rows.Tile> onNow = new ArrayList<>(), sport = new ArrayList<>(), favs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Channel> order = new ArrayList<>();
        for (String k : lib.favourites()) {
            Channel c = lib.byKey(k);
            if (c != null) order.add(c);
        }
        order.addAll(lib.all());
        boolean guide = lib.hasGuide();
        for (Channel c : order) {
            if (!seen.add(c.key)) continue;
            if (onNow.size() >= 24 && sport.size() >= 16) break;
            Guide.Schedule s = lib.schedule(c.key);
            int i = s == null ? -1 : s.at(now);
            if (guide && i < 0) continue;
            Rows.Tile t = channelTile(lib, c, s, i, now);
            if (onNow.size() < 24) onNow.add(t);
            if (sport.size() < 16 && Genres.SPORT.equals(Genres.of(c.group, c.name))) sport.add(channelTile(lib, c, s, i, now));
        }
        for (String k : lib.favourites()) {
            Channel c = lib.byKey(k);
            if (c == null || favs.size() >= 24) continue;
            Guide.Schedule s = lib.schedule(c.key);
            favs.add(channelTile(lib, c, s, s == null ? -1 : s.at(now), now));
        }
        if (!onNow.isEmpty()) list.add(new Rows.Row(guide ? "On now" : "Your channels", Rows.CHANNEL, onNow));

        List<Rows.Tile> cont = new ArrayList<>();
        for (Resume.Point p : Resume.all(tv)) {
            Catalog.Title t = cat.title(p.title);
            if (t == null) continue;
            Rows.Tile tile = new Rows.Tile(p);
            tile.picture = p.picture;
            tile.fallback = t.backdrop.isEmpty() ? t.poster : t.backdrop;
            tile.title = t.name;
            tile.subtitle = p.subtitle.isEmpty() ? Ui.left(p.duration - p.position) : p.subtitle;
            tile.progress = p.position == 0 ? -1f : p.progress();
            cont.add(tile);
            if (cont.size() >= 20) break;
        }
        if (!cont.isEmpty()) list.add(new Rows.Row("Continue watching", Rows.WIDE, cont));

        // The family's own TorBox films and shows that no playlist has, newest first, with TorBox's cloud.
        List<Catalog.Title> own = new ArrayList<>(cat.own(false));
        own.addAll(cat.own(true));
        own.sort((a, b) -> Long.compare(b.added, a.added));
        List<Rows.Tile> mine = posters(own.subList(0, Math.min(30, own.size())));
        for (Rows.Tile t : mine) t.torbox = true;
        if (!mine.isEmpty()) list.add(new Rows.Row(Catalog.TORBOX, Rows.POSTER, mine));

        List<Rows.Tile> films = posters(cat.recent(false, 30));
        if (!films.isEmpty()) list.add(new Rows.Row("New films", Rows.POSTER, films));
        List<Rows.Tile> shows = posters(cat.recent(true, 30));
        if (!shows.isEmpty()) list.add(new Rows.Row("New TV shows", Rows.POSTER, shows));
        if (!sport.isEmpty()) list.add(new Rows.Row("Sport on now", Rows.CHANNEL, sport));
        if (!favs.isEmpty()) list.add(new Rows.Row("Your favourite channels", Rows.CHANNEL, favs));
        rows.setRows(list);
        if (list.isEmpty() && !inMenu) {
            inMenu = true;
            rail.setActive(true);
            rows.setActive(false);
        }
        showHero();
    }

    static Rows.Tile channelTile(Library lib, Channel c, Guide.Schedule s, int i, long now) {
        Rows.Tile t = new Rows.Tile(c);
        t.logo = c.logo;
        if (i >= 0) {
            t.title = s.title[i];
            t.subtitle = lib.number(c) + "  " + c.name + "  ·  " + Ui.left(s.stop[i] - now);
            t.progress = (now - s.start[i]) / (float) Math.max(1, s.stop[i] - s.start[i]);
        } else {
            t.title = c.name;
            t.subtitle = String.valueOf(lib.number(c));
        }
        return t;
    }

    static List<Rows.Tile> posters(List<Catalog.Title> titles) {
        List<Rows.Tile> out = new ArrayList<>(titles.size());
        for (Catalog.Title t : titles) {
            Rows.Tile tile = new Rows.Tile(t);
            tile.picture = t.poster;
            tile.title = t.name;
            out.add(tile);
        }
        return out;
    }

    // ---- the big picture ----------------------------------------------------------------------------------------

    private void showHero() {
        // On the menu: the newest film, as the guide puts its own pick of the day there; else the tile the rows were on.
        Rows.Tile t = rows.tile();
        if (inMenu) {
            Rows.Row films = rows.find("New films");
            if (films != null && !films.tiles.isEmpty()) t = films.tiles.get(0);
        }
        heroTile = t;
        if (t == null) {
            Library lib = Library.get(tv);
            if (!lib.hasSource()) {
                hero.show("", "", "Welcome to Homefield", "", 0,
                        "Add your family's playlist in Settings: an M3U address or an Xtream Codes login. Live TV, the guide, "
                                + "films and TV shows all come from it.", "", false);
            } else {
                hero.show("", "", lib.busy() ? "Loading your channels…" : "Homefield", "", 0, lib.status(), "", false);
            }
            return;
        }
        heroFor(tv, hero, t, now());
        h.removeCallbacks(fetch);
        pending = t.item instanceof Catalog.Title ? (Catalog.Title) t.item
                : t.item instanceof Resume.Point ? Catalog.get(tv).title(((Resume.Point) t.item).title) : null;
        if (pending != null && Catalog.get(tv).cachedDetails(pending) == null && !pending.series) h.postDelayed(fetch, 450);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private void fetchDetails() {
        Catalog.Title want = pending;
        if (want == null) return;
        Catalog.get(tv).details(want, d -> {
            Rows.Tile t = heroTile;
            if (t != null && (t.item == want || t.item instanceof Resume.Point && ((Resume.Point) t.item).title.equals(want.key))) {
                heroFor(tv, hero, t, now());
            }
        });
    }

    /** The big picture for a tile: a channel's programme now, a film or a series. */
    static void heroFor(TvActivity tv, Hero hero, Rows.Tile t, long now) {
        Library lib = Library.get(tv);
        hero.badge = "";
        if (t.item instanceof Channel) {
            Channel c = (Channel) t.item;
            Guide.Schedule s = lib.schedule(c.key);
            int i = s == null ? -1 : s.at(now);
            if (i >= 0) {
                hero.show("", c.logo, s.title[i], "Now  ·  " + Ui.span(s.start[i], s.stop[i]) + "  ·  " + lib.number(c) + " " + c.name, 0,
                        s.desc[i], "", false);
            } else {
                hero.show("", c.logo, c.name, lib.number(c) + "  ·  " + c.group, 0, "", "", false);
            }
            return;
        }
        Catalog.Title title = t.item instanceof Catalog.Title ? (Catalog.Title) t.item
                : t.item instanceof Resume.Point ? Catalog.get(tv).title(((Resume.Point) t.item).title) : null;
        if (title == null) return;
        Catalog.Details d = Catalog.get(tv).cachedDetails(title);
        String meta = meta(title, d);
        String plot = d != null && !d.plot.isEmpty() ? d.plot : title.plot;
        String backdrop = d != null && !d.backdrop.isEmpty() ? d.backdrop : title.backdrop;
        if (t.item instanceof Resume.Point) {
            Resume.Point p = (Resume.Point) t.item;
            meta = (p.subtitle.isEmpty() ? "" : p.subtitle + "  ·  ") + Ui.left(p.duration - p.position);
        }
        float stars = rating(title, d);
        hero.badge = d == null ? "" : d.age;
        if (!backdrop.isEmpty()) hero.show(title.category, "", title.name, meta, stars, plot, backdrop, false);
        else hero.show(title.category, "", title.name, meta, stars, plot, title.poster, true);
    }

    static float rating(Catalog.Title t, Catalog.Details d) {
        String r = d != null && !d.rating.isEmpty() ? d.rating : t.rating;
        try {
            return r.isEmpty() ? 0f : Float.parseFloat(r);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    /** "1h 56m  ·  2019  ·  Action, Thriller". */
    static String meta(Catalog.Title t, Catalog.Details d) {
        List<String> parts = new ArrayList<>();
        if (d != null && d.minutes > 0 && !t.series) parts.add(Ui.duration(d.minutes));
        if (d != null && !d.seasons.isEmpty()) parts.add(d.seasons.size() == 1 ? "1 series" : d.seasons.size() + " series");
        if (!t.year.isEmpty()) parts.add(t.year);
        if (d != null && !d.genre.isEmpty()) parts.add(d.genre.replace(" /", ",").replace(" ,", ","));
        return String.join("  ·  ", parts);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            zap(code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1);
            return true;
        }
        if (inMenu) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && !rows.isEmpty()) {
                enterRows();
                return true;
            }
            return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP
                    || code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        return rows.key(code);
    }

    private void enterRows() {
        inMenu = false;
        rail.setActive(false);
        rows.setActive(true);
        showHero();
    }

    private void zap(int dir) {
        // Settings → Categories and Parental control: past hidden categories and locked channels.
        Channel c = com.gathin.homefieldtv.prefs.Shown.step(tv, Library.get(tv).all(), tv.live.channel(), dir);
        if (c != null && !com.gathin.homefieldtv.prefs.Parental.blocks(tv, c)) tv.live.play(c);
    }

    @Override void ok() {
        if (!inMenu) {
            rows.ok(false);
            return;
        }
        // Home into the rows; the rest as the rail does on every dark screen (Sports: the sports hub).
        if (rail.focus == Rail.HOME) {
            if (!rows.isEmpty()) enterRows();
        } else {
            Rail.go(tv, rail.focus, Rail.HOME);
        }
    }

    @Override void okLong() {
        if (!inMenu) rows.ok(true);
        else ok();
    }

    @Override boolean back() {
        if (!inMenu) {
            inMenu = true;
            rail.setActive(true);
            rows.setActive(false);
            showHero();
            return true;
        }
        // As the guide does: Back from the menu is back to the TV, full screen.
        Channel c = tv.live.channel();
        if (c != null) {
            tv.watch(c);
            return true;
        }
        return false;
    }

    // ---- the rows' remote -------------------------------------------------------------------------------------

    @Override public void focused(Rows.Row row, Rows.Tile tile) {
        showHero();
    }

    @Override public void chosen(Rows.Row row, Rows.Tile tile, boolean held) {
        open(tv, tile, held);
    }

    /** What OK on a tile does, wherever it is: a channel full screen, a title's page, a film carried on. */
    static void open(TvActivity tv, Rows.Tile tile, boolean held) {
        Object o = tile.item;
        if (o instanceof Channel) {
            if (held) {
                Library.get(tv).toggleFavourite((Channel) o);
                return;
            }
            tv.watch((Channel) o);
        } else if (o instanceof Catalog.Title) {
            tv.openTitle((Catalog.Title) o);
        } else if (o instanceof Resume.Point) {
            Resume.Point p = (Resume.Point) o;
            Catalog.Title t = Catalog.get(tv).title(p.title);
            if (t == null) return;
            if (held || !t.series && p.episode.isEmpty()) {
                if (held) tv.openTitle(t);
                else tv.play(t, null, p.position, false);
                return;
            }
            // An episode: its details first (where it plays comes from them).
            Catalog.get(tv).details(t, d -> {
                Catalog.Episode e = d == null ? null : d.episode(p.episode);
                if (e != null) tv.play(t, e, p.position, false);
                else tv.openTitle(t);
            });
        }
    }

    @Override public boolean edge(int dir) {
        if (dir == Ui.LEFT) {
            back();
            return true;
        }
        return false;
    }
}
