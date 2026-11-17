package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.engine.Resume;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Home, as the family's hero-card video has it (HomeCard): one big card at a time for whatever the remote is on, from
 * the sections along the top: what's on now, Continue watching, the family's own TorBox titles, the newest films and TV
 * shows from the playlists, sport on now and the favourite channels. A channel's card is its programme on now with its
 * next ones along its foot; a series' has its episodes, a film's like ones. OK on a card: a channel full screen, a
 * title's page, or carrying on from where it was left; on a tile of its strip: that episode (every source first), that
 * like title, the programme on now (or a reminder for a later one). Homefield's mark at the top left opens the menu
 * (Rail, its drawer coming in over the page); CH+ and CH- go straight to the next channel full screen.
 */
final class HomeScreen extends Screen implements HomeCard.Host {
    static final String ON_NOW = "on", CONTINUE = "continue", TORBOX = "torbox", FILMS = "films", SHOWS = "shows",
            SPORT = "sport", FAVOURITES = "favourites";
    private static final int MAX = 30;

    private Rail rail;
    private HomeCard home;
    private boolean inMenu;
    /** A key pressed since Home was made: until then the remote goes to the cards as soon as there are some. */
    private boolean touched;
    private final Handler h = new Handler(Looper.getMainLooper());
    private HomeCard.Card pending;
    private final Runnable fetch = this::fetchDetails;

    HomeScreen(TvActivity tv) {
        super(tv);
    }

    @Override View build() {
        Cine.init(tv);
        FrameLayout f = new FrameLayout(tv);
        f.setBackgroundColor(Cine.GROUND);
        home = new HomeCard(tv, this);
        rail = new Rail(tv, Rail.HOME);
        rail.keep = true;
        rail.hideClosed = true;
        f.addView(home, new FrameLayout.LayoutParams(-1, -1));
        f.addView(rail, new FrameLayout.LayoutParams(-1, -1));
        fill();
        return f;
    }

    @Override void shown() {
        // Home has no live window: the stream let go (the phone's "stopped" flag taken, as there is nothing to hold off).
        tv.takeMiniTvHeld();
        tv.live.stop();
        tv.live.place(null);
        fill();
    }

    @Override void hidden() {
        h.removeCallbacks(fetch);
    }

    @Override void tick() {
        rail.invalidate();
        fill();
    }

    @Override void channelsChanged() {
        fill();
    }

    @Override void catalogChanged() {
        fill();
    }

    // ---- the sections -------------------------------------------------------------------------------------------

    private void fill() {
        if (home == null) return;
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        long now = System.currentTimeMillis();
        List<HomeCard.Section> list = new ArrayList<>();

        List<HomeCard.Card> onNow = new ArrayList<>(), sport = new ArrayList<>(), favs = new ArrayList<>();
        // What's on now and the sport in it: found off the main thread (Library.onNow), made again when it is.
        Library.OnNow on = lib.onNow(now, this::fill);
        for (Channel c : on.channels) if (onNow.size() < MAX) onNow.add(channelCard(lib, c, now));
        for (Channel c : on.sport) if (sport.size() < MAX) sport.add(channelCard(lib, c, now));
        for (String k : lib.favourites()) {
            Channel c = lib.byKey(k);
            if (c != null && favs.size() < MAX) favs.add(channelCard(lib, c, now));
        }
        if (!onNow.isEmpty()) list.add(new HomeCard.Section(lib.hasGuide() ? "On now" : "Your channels", ON_NOW, onNow));

        List<HomeCard.Card> going = new ArrayList<>();
        for (Resume.Point p : Resume.all(tv)) {
            Catalog.Title t = cat.title(p.title);
            if (t != null && going.size() < 20) going.add(resumeCard(p, t));
        }
        if (!going.isEmpty()) list.add(new HomeCard.Section("Continue watching", CONTINUE, going));

        // The family's own TorBox films and shows that no playlist has, newest first.
        List<Catalog.Title> own = new ArrayList<>(cat.own(false));
        own.addAll(cat.own(true));
        own.sort((a, b) -> Long.compare(b.added, a.added));
        List<HomeCard.Card> mine = titleCards(own);
        if (!mine.isEmpty()) list.add(new HomeCard.Section(Catalog.TORBOX, TORBOX, mine));
        List<HomeCard.Card> films = titleCards(cat.recent(false, MAX));
        if (!films.isEmpty()) list.add(new HomeCard.Section("New films", FILMS, films));
        List<HomeCard.Card> shows = titleCards(cat.recent(true, MAX));
        if (!shows.isEmpty()) list.add(new HomeCard.Section("New TV shows", SHOWS, shows));
        if (!sport.isEmpty()) list.add(new HomeCard.Section("Sport on now", SPORT, sport));
        if (!favs.isEmpty()) list.add(new HomeCard.Section("Favourites", FAVOURITES, favs));

        if (list.isEmpty()) {
            if (!lib.hasSource()) home.empty("Welcome to Homefield", "Add your family's playlist in Settings: live TV, films and shows all come from it.");
            else home.empty("Homefield", lib.busy() || lib.status().isEmpty() ? "Loading your channels…" : lib.status());
        }
        home.set(list);
        if (list.isEmpty() && !inMenu) {
            inMenu = true;
            rail.setActive(true);
        } else if (!list.isEmpty() && inMenu && !touched) {
            inMenu = false;
            rail.setActive(false);
        }
        HomeCard.Card c = home.card();
        if (c != null) focused(c);
    }

    private List<HomeCard.Card> titleCards(List<Catalog.Title> titles) {
        List<HomeCard.Card> out = new ArrayList<>();
        for (int i = 0; i < titles.size() && out.size() < MAX; i++) out.add(titleCard(titles.get(i)));
        return out;
    }

    /** A channel's card: its programme on now (else its name), when it runs, the channel, the time left; its logo. */
    static HomeCard.Card channelCard(Library lib, Channel c, long now) {
        Guide.Schedule s = lib.schedule(c.key);
        int i = s == null ? -1 : s.at(now);
        String number = lib.number(c) > 0 ? lib.number(c) + "  " + c.name : c.name;
        if (i >= 0) {
            float done = (now - s.start[i]) / (float) Math.max(1, s.stop[i] - s.start[i]);
            return new HomeCard.Card(c, "c:" + c.key, s.title[i], new String[]{Ui.span(s.start[i], s.stop[i]), number,
                    Ui.left(s.stop[i] - now)}, 0, "", c.logo, true, done);
        }
        return new HomeCard.Card(c, "c:" + c.key, c.name, new String[]{number, c.group}, 0, "", c.logo, false, -1f);
    }

    private HomeCard.Card titleCard(Catalog.Title t) {
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        HomeCard.Card c = new HomeCard.Card(t, "t:" + t.key, t.name, facts(t, d, null), stars(t, d), picture(t, d), "", false, -1f);
        return c;
    }

    private HomeCard.Card resumeCard(Resume.Point p, Catalog.Title t) {
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        List<String> f = new ArrayList<>();
        String line = BrowseScreen.resumeLine(t.series ? p.subtitle : "", p.position, p.duration);
        if (!line.isEmpty()) for (String s : line.split(" · ")) f.add(s);
        if (!t.year.isEmpty()) f.add(t.year);
        String pic = picture(t, d);
        if (pic.isEmpty()) pic = p.picture;
        return new HomeCard.Card(p, "r:" + t.key, t.name, f.toArray(new String[0]), stars(t, d), pic, "", false,
                p.position > 0 ? p.progress() : -1f);
    }

    private static String picture(Catalog.Title t, Catalog.Details d) {
        return d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
    }

    /**
     * Pure: a title card's facts, as the video's line has them: a series' season (the one carried on in, else its
     * newest), a film's length; its year, first genre and age ("15+").
     */
    static String[] facts(Catalog.Title t, Catalog.Details d, Resume.Point p) {
        List<String> out = new ArrayList<>();
        if (t.series && d != null && !d.seasons.isEmpty()) {
            int season = d.seasons.get(d.seasons.size() - 1).number;
            Catalog.Episode e = p == null ? null : d.episode(p.episode);
            if (e != null) season = e.season;
            out.add("Season " + season);
        } else if (!t.series && d != null && d.minutes > 0) {
            out.add(Ui.duration(d.minutes));
        }
        if (!t.year.isEmpty()) out.add(t.year);
        String g = d == null ? "" : PosterArt.firstGenre(d.genre);
        if (g.isEmpty()) g = PosterArt.genreOf(t.category);
        if (!g.isEmpty()) out.add(g);
        String age = d == null ? "" : d.age.trim();
        if (!age.isEmpty()) out.add(age.matches("\\d+") ? age + "+" : age);
        return out.toArray(new String[0]);
    }

    /** Pure: out of five, the rating's (out of ten) nearest. */
    static int stars(Catalog.Title t, Catalog.Details d) {
        float r = Billboard.rating(d != null && !d.rating.isEmpty() ? d.rating : t.rating);
        return r <= 0 ? 0 : Math.max(1, Math.min(5, Math.round(r / 2f)));
    }

    // ---- the strip ----------------------------------------------------------------------------------------------

    @Override public void focused(HomeCard.Card card) {
        h.removeCallbacks(fetch);
        Object o = card.item;
        if (o instanceof Channel) {
            channelStrip(card, (Channel) o);
            return;
        }
        Catalog.Title t = titleOf(o);
        if (t == null) return;
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        if (d != null) {
            titleStrip(card, t, d);
        } else {
            pending = card;
            h.postDelayed(fetch, 450);
        }
    }

    private Catalog.Title titleOf(Object o) {
        if (o instanceof Catalog.Title) return (Catalog.Title) o;
        if (o instanceof Resume.Point) return Catalog.get(tv).title(((Resume.Point) o).title);
        return null;
    }

    private void fetchDetails() {
        HomeCard.Card card = pending;
        Catalog.Title t = card == null ? null : titleOf(card.item);
        if (t == null) return;
        Catalog.get(tv).details(t, d -> {
            if (d == null || home == null) return;
            card.facts = card.item instanceof Resume.Point ? card.facts : facts(t, d, null);
            card.stars = stars(t, d);
            if (home.card() == card) titleStrip(card, t, d);
            else home.invalidate();
        });
    }

    /** A channel's next programmes: on now first, with how far in, then the later ones with their times. */
    private void channelStrip(HomeCard.Card card, Channel c) {
        Guide.Schedule s = Library.get(tv).schedule(c.key);
        long now = System.currentTimeMillis();
        List<HomeCard.Tile> tiles = new ArrayList<>();
        if (s != null) {
            int i = s.at(now);
            if (i < 0) i = s.after(now);
            for (int k = Math.max(0, i); k >= 0 && k < s.start.length && tiles.size() < 8; k++) {
                boolean onNow = s.start[k] <= now && s.stop[k] > now;
                float done = onNow ? (now - s.start[k]) / (float) Math.max(1, s.stop[k] - s.start[k]) : -1f;
                String when = onNow ? "Now" : LiveText.hm(s.start[k]);
                tiles.add(new HomeCard.Tile(new long[]{s.start[k], s.stop[k], k}, "", when, s.title[k], done));
            }
        }
        home.strip(card, tiles, 0);
    }

    /** A series' episodes (every season's, each season's first named "Season 2"…), or a film's like ones. */
    private void titleStrip(HomeCard.Card card, Catalog.Title t, Catalog.Details d) {
        List<HomeCard.Tile> tiles = new ArrayList<>();
        int at = 0;
        if (t.series) {
            Resume.Point p = card.item instanceof Resume.Point ? (Resume.Point) card.item : Resume.latest(tv, t.key);
            int first = d.seasons.isEmpty() ? 0 : d.seasons.get(0).number;
            for (Catalog.Season s : d.seasons) {
                for (int k = 0; k < s.episodes.size(); k++) {
                    Catalog.Episode e = s.episodes.get(k);
                    if (p != null && e.id.equals(p.episode)) at = tiles.size();
                    Resume.Point ep = Resume.get(tv, t.key, e.id);
                    String label = k == 0 && s.number != first ? "Season " + s.number : String.format(Locale.UK, "%02d", e.number);
                    tiles.add(new HomeCard.Tile(e, e.still.isEmpty() ? picture(t, d) : e.still, label,
                            e.title.isEmpty() ? "Episode " + e.number : e.title, ep != null && ep.position > 0 ? ep.progress() : -1f));
                }
            }
        } else {
            for (Catalog.Category c : Catalog.get(tv).categories(false)) {
                if (!c.name.equals(t.category)) continue;
                for (Catalog.Title o : c.titles) {
                    if (tiles.size() >= 10) break;
                    if (o == t) continue;
                    Catalog.Details od = Catalog.get(tv).cachedDetails(o);
                    tiles.add(new HomeCard.Tile(o, picture(o, od), o.name, "", -1f));
                }
            }
        }
        home.strip(card, tiles, at);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        touched = true;
        if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            zap(code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1);
            return true;
        }
        if (inMenu) {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                closeMenu();
                return true;
            }
            return rail.key(code) || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP
                    || code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        return home.key(code);
    }

    private void closeMenu() {
        if (home.card() == null) return;
        inMenu = false;
        rail.setActive(false);
    }

    /** The next or the last channel, full screen (Settings → Categories and Parental control: past hidden and locked ones). */
    private void zap(int dir) {
        Library lib = Library.get(tv);
        Channel from = tv.live.channel() != null ? tv.live.channel() : lib.lastChannel();
        Channel c = lib.step(from, dir);
        if (c != null && !com.gathin.homefieldtv.prefs.Parental.blocks(tv, c)) tv.watch(c);
    }

    @Override void ok() {
        touched = true;
        if (inMenu) {
            if (rail.focus == Rail.HOME) closeMenu();
            else Rail.go(tv, rail.focus, Rail.HOME);
            return;
        }
        home.ok();
    }

    @Override void okLong() {
        HomeCard.Card c = home.card();
        if (!inMenu && home.area() == HomeCard.CARD && c != null && c.item instanceof Channel) {
            Library.get(tv).toggleFavourite((Channel) c.item);
            return;
        }
        ok();
    }

    @Override boolean back() {
        touched = true;
        if (inMenu) {
            // As the guide does: Back from the menu is back to the TV, full screen.
            Channel c = tv.live.channel() != null ? tv.live.channel() : tv.channelToPlay();
            if (c != null) {
                tv.watch(c);
                return true;
            }
            return false;
        }
        if (home.back()) return true;
        menu();
        return true;
    }

    @Override public void section(int index) {
        // The new section's card is shown at once (focused() follows).
    }

    @Override public void menu() {
        inMenu = true;
        rail.setActive(true);
    }

    @Override public void chosen(HomeCard.Card card) {
        Object o = card.item;
        if (o instanceof Channel) {
            tv.watch((Channel) o);
        } else if (o instanceof Catalog.Title) {
            tv.openTitle((Catalog.Title) o);
        } else if (o instanceof Resume.Point) {
            carryOn((Resume.Point) o);
        }
    }

    /** Continue watching: on at once from where it was (an episode needs its series' page read; else its page). */
    private void carryOn(Resume.Point p) {
        Catalog.Title t = Catalog.get(tv).title(p.title);
        if (t == null) return;
        if (!t.series && p.episode.isEmpty()) {
            tv.play(t, null, p.position, false);
            return;
        }
        Catalog.get(tv).details(t, d -> {
            Catalog.Episode e = d == null ? null : d.episode(p.episode);
            if (e != null) tv.play(t, e, p.position, false);
            else tv.openTitle(t);
        });
    }

    @Override public void chosen(HomeCard.Card card, HomeCard.Tile tile) {
        Object o = tile.item;
        if (o instanceof Catalog.Episode) {
            Catalog.Title t = titleOf(card.item);
            if (t == null) return;
            Catalog.Episode e = (Catalog.Episode) o;
            Resume.Point p = Resume.get(tv, t.key, e.id);
            tv.play(t, e, p == null ? 0 : p.position, true);
        } else if (o instanceof Catalog.Title) {
            tv.openTitle((Catalog.Title) o);
        } else if (o instanceof long[] && card.item instanceof Channel) {
            Channel c = (Channel) card.item;
            long[] when = (long[]) o;
            long now = System.currentTimeMillis();
            if (when[0] <= now && when[1] > now) {
                tv.watch(c);
                return;
            }
            if (Reminders.has(tv, c.key, when[0])) GuideActions.unremind(tv, c, tile.name, when[0]);
            else GuideActions.remind(tv, c, tile.name, when[0], when[1]);
        }
    }

    /** Previews and tests: the remote on a section, a card, and where (HomeCard.CARD, STRIP) along the strip. */
    void previewFocus(int section, int card, int where, int strip) {
        inMenu = false;
        rail.setActive(false);
        home.focus(section, card, where, strip);
    }

    /** Previews: the slide from one card to the next caught part way. */
    void previewBetween(float at) {
        home.between(at);
    }

    /** Previews and tests: a section's place by its tag (ON_NOW, FILMS…), -1 when it isn't shown. */
    int previewSection(String tag) {
        return home.sectionOf(tag);
    }
}
