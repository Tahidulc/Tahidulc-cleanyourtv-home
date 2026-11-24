package com.gathin.homefieldtv;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CategorySet;
import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.HomeStyle;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;
import com.gathin.homefieldtv.sports.Matching;
import com.gathin.homefieldtv.sports.SportsData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home, the first page of 07's shell. Settings › Appearance › Home style: Editorial (as it comes) hands the page to
 * EditorialPage (the carousel and rows of CategorySet.HOME); Classic is this page's own (HomeCard): one big card at a time for whatever the remote is on, from the sections
 * under the tab bar: what's on now, Continue watching, the family's own TorBox titles, the newest films and TV shows from
 * the playlists, sport on now and the favourite channels. A channel's card is its programme on now (the score where it
 * is a match, held back to the channel's delay) with its later programmes and what's on now on the next channels along
 * its foot; a series' has its episodes, a film's like ones. OK on a card is its first button: a channel full screen,
 * carrying on from where it was left, or a title's page; MENU is its second: Start Over (a channel's catch-up), From the
 * Start, the trailer, or My List. OK on a tile of its strip: that episode (every source first), that like title, the
 * programme on now (or a reminder for a later one). OK held on a channel makes it a favourite; CH+ and CH- go straight to
 * the next channel full screen. Up past the sections, or Back there, goes up to the tab bar.
 */
final class HomeScreen extends Screen implements HomeCard.Host {
    static final String ON_NOW = "on", CONTINUE = "continue", TORBOX = "torbox", FILMS = "films", SHOWS = "shows",
            SPORT = "sport", FAVOURITES = "favourites";
    private static final int MAX = 30;
    /** How many later programmes a channel's strip has, then how many channels on now after it. */
    private static final int LATER = 3, ALSO = 5;

    private HomeCard home;
    /** Home style Editorial: the page (null for Classic). */
    private EditorialPage editorial;
    private final Handler h = new Handler(Looper.getMainLooper());
    private HomeCard.Card pending, extrasFor, scoreFor;
    private final Runnable fetch = this::fetchDetails, extras = this::fetchExtras, score = this::fetchScore;
    /** The sport channels on now (their cards look for a match's score). */
    private final List<String> sportKeys = new ArrayList<>();
    /** Each channel's match as last found: {time asked, the game held back to its delay or null}. */
    private final Map<String, Object[]> scores = new HashMap<>();
    /**
     * TMDB's extras of the titles the remote has rested on, by key: read once (Tmdb's kept file, or TMDB) and kept here,
     * so making Home's cards each minute reads nothing from the disk.
     */
    private final Map<String, Tmdb.Extras> extrasKnown = new HashMap<>();
    /** The playlists' names by id, read once each time the cards are made. */
    private final Map<String, String> playlistNames = new HashMap<>();

    HomeScreen(TvActivity tv) {
        super(tv);
    }

    /** Its page draws an opaque ground over the whole screen (the window's own isn't drawn under it). */
    @Override boolean opaque() {
        return true;
    }

    @Override View build() {
        Cine.init(tv);
        if (HomeStyle.editorial(tv)) {
            editorial = new EditorialPage(tv, CategorySet.HOME);
            return editorial.build();
        }
        FrameLayout f = new FrameLayout(tv);
        // No background of its own: its page draws its opaque ground first (a full screen of fill saved each frame).
        home = new HomeCard(tv, this);
        f.addView(home, new FrameLayout.LayoutParams(-1, -1));
        fill();
        return f;
    }

    @Override void shown() {
        // Home has no live window: the stream let go (the phone's "stopped" flag taken, as there is nothing to hold off).
        tv.takeMiniTvHeld();
        tv.live.stop();
        tv.live.place(null);
        if (editorial != null) {
            editorial.shown();
            return;
        }
        if (home != null) home.setActive(tv.tabs() == null || !tv.tabs().active());
        fill();
    }

    @Override void hidden() {
        h.removeCallbacks(fetch);
        h.removeCallbacks(extras);
        h.removeCallbacks(score);
        if (editorial != null) editorial.hidden();
    }

    @Override void tick() {
        fill();
    }

    @Override void channelsChanged() {
        fill();
    }

    @Override void catalogChanged() {
        if (editorial != null) editorial.catalogChanged();
        else fill();
    }

    /** Home style Editorial's page (null for Classic). */
    EditorialPage editorial() {
        return editorial;
    }

    // ---- the sections -------------------------------------------------------------------------------------------

    private void fill() {
        if (home == null) return;
        Library lib = Library.get(tv);
        Catalog cat = Catalog.get(tv);
        long now = System.currentTimeMillis();
        List<HomeCard.Section> list = new ArrayList<>();
        playlistNames.clear();
        for (Sources.Entry e : Sources.list(tv)) playlistNames.put(e.id, e.name);

        // What's on now and the sport in it: found off the main thread (Library.onNow), made again when it is.
        Library.OnNow on = lib.onNow(now, this::fill);
        sportKeys.clear();
        for (Channel c : on.sport) sportKeys.add(c.key);
        String onName = lib.hasGuide() ? "On now" : "Your channels";
        List<HomeCard.Card> onNow = new ArrayList<>(), sport = new ArrayList<>(), favs = new ArrayList<>();
        for (Channel c : on.channels) if (onNow.size() < MAX) onNow.add(channelCard(lib, c, now, onName));
        for (Channel c : on.sport) if (sport.size() < MAX) sport.add(channelCard(lib, c, now, "Sport on now"));
        java.util.Set<String> favSeen = new java.util.HashSet<>();
        for (String k : lib.favourites()) {
            // A favourite version is its channel's row, once (one row per channel).
            Channel c = lib.byKey(k);
            // Not one the family hid (Hide channel): off Home as off every list.
            if (c != null && !ChannelEdits.hidden(c.key) && favSeen.add(c.key) && favs.size() < MAX) {
                favs.add(channelCard(lib, c, now, "Favourites"));
            }
        }
        if (!onNow.isEmpty()) list.add(new HomeCard.Section(onName, ON_NOW, onNow));

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
        List<HomeCard.Card> mine = titleCards(own, Catalog.TORBOX);
        if (!mine.isEmpty()) list.add(new HomeCard.Section(Catalog.TORBOX, TORBOX, mine));
        List<HomeCard.Card> films = titleCards(cat.recent(false, MAX), "New films");
        if (!films.isEmpty()) list.add(new HomeCard.Section("New films", FILMS, films));
        List<HomeCard.Card> shows = titleCards(cat.recent(true, MAX), "New TV shows");
        if (!shows.isEmpty()) list.add(new HomeCard.Section("New TV shows", SHOWS, shows));
        if (!sport.isEmpty()) list.add(new HomeCard.Section("Sport on now", SPORT, sport));
        if (!favs.isEmpty()) list.add(new HomeCard.Section("Favourites", FAVOURITES, favs));

        if (list.isEmpty()) {
            if (!lib.hasSource()) home.empty("Welcome to Homefield", "Add your family's playlist in Settings: live TV, films and shows all come from it.");
            else home.empty("Homefield", lib.busy() || lib.status().isEmpty() ? "Loading your channels…" : lib.status());
        }
        home.set(list);
        // Nothing to show yet: the remote waits on the tab bar (Settings' cog for a first playlist), but only while Home
        // is the page on top, so a list refreshed under the guide never takes the remote from it.
        if (list.isEmpty() && tv.tabs() != null && tv.isTop(this)) tv.tabs().enter();
        HomeCard.Card c = home.card();
        if (c != null) focused(c);
    }

    private List<HomeCard.Card> titleCards(List<Catalog.Title> titles, String section) {
        List<HomeCard.Card> out = new ArrayList<>();
        for (int i = 0; i < titles.size() && out.size() < MAX; i++) out.add(titleCard(titles.get(i), section));
        return out;
    }

    /**
     * A channel's card: its programme on now (else its name) with LIVE, its times and how long is left, what it's about,
     * Watch Live and (with catch-up) Start Over; its logo in the kicker. section: the kicker's first words.
     */
    HomeCard.Card channelCard(Library lib, Channel c, long now, String section) {
        Guide.Schedule s = lib.schedule(c.key);
        int i = s == null ? -1 : s.at(now);
        // The family's name for it (Rename channel) and its logo as the guide shows it (a new one, or the playlist's).
        String name = ChannelEdits.name(c), logo = LogoPack.url(c);
        int n = lib.number(c);
        String number = n > 0 ? n + " " + name : name;
        HomeCard.Card card;
        if (i >= 0) {
            float done = (now - s.start[i]) / (float) Math.max(1, s.stop[i] - s.start[i]);
            card = new HomeCard.Card(c, "c:" + c.key, s.title[i], "", logo, true, done);
            card.facts.words("", LiveText.span(s.start[i], s.stop[i]) + " · " + LiveText.left(now, s.stop[i]));
            card.plot = s.desc == null || s.desc[i] == null ? "" : s.desc[i];
            card.liveBadge = true;
            card.ch = c;
            card.schedule = s;
            card.at = i;
            if (Catchup.source(c) != null) {
                card.second = "Start Over";
                card.secondIcon = Icons07.RESTART;
            }
        } else {
            card = new HomeCard.Card(c, "c:" + c.key, name, "", logo, false, -1f);
            card.facts.words("", c.group);
        }
        card.channel = true;
        card.kick = section + " · " + number;
        card.quality = LiveText.quality(c.name);
        card.button = "Watch Live";
        card.buttonIcon = Icons07.PLAY;
        card.stripLabel = ("Later on " + name + ", and on now").toUpperCase(Locale.UK);
        return card;
    }

    private HomeCard.Card titleCard(Catalog.Title t, String section) {
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        HomeCard.Card c = new HomeCard.Card(t, "t:" + t.key, t.name, picture(t, d), "", false, -1f);
        c.kick = section + " · in " + where(t);
        words(c, t, d, extrasKnown.get(t.key));
        return c;
    }

    /** Where a title is: Your TorBox, or the name of the playlist it comes from. */
    private String where(Catalog.Title t) {
        if (t.torbox()) return Catalog.TORBOX;
        String name = playlistNames.get(t.playlist());
        return name == null || name.isEmpty() ? "your playlist" : name;
    }

    private HomeCard.Card resumeCard(Resume.Point p, Catalog.Title t) {
        Catalog.Details d = Catalog.get(tv).cachedDetails(t);
        // A series' card shows the episode's own picture (where it was left), a film's its backdrop.
        String pic = t.series && !p.picture.isEmpty() ? p.picture : picture(t, d);
        if (pic.isEmpty()) pic = p.picture;
        HomeCard.Card c = new HomeCard.Card(p, "r:" + t.key, t.name, pic, "", false, p.position > 0 ? p.progress() : -1f);
        words(c, t, d, extrasKnown.get(t.key));
        return c;
    }

    /** A title's card's words, from its details and TMDB's extras as far as they are known. */
    private void words(HomeCard.Card c, Catalog.Title t, Catalog.Details d, Tmdb.Extras x) {
        c.mark = x == null ? "" : x.logo;
        c.facts.set(t, d, x, 2);
        c.plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        c.lead = "";
        boolean on = MyList.has(tv, t.key);
        if (c.item instanceof Resume.Point) {
            Resume.Point p = (Resume.Point) c.item;
            Catalog.Episode e = d == null || p.episode.isEmpty() ? null : d.episode(p.episode);
            String code = e != null ? "S" + e.season + " E" + e.number : episodeCode(p.subtitle);
            c.kick = e != null ? "Continue watching · Season " + e.season + ", Episode " + e.number : "Continue watching";
            if (e != null) {
                c.lead = "E" + e.number + (e.title.isEmpty() ? "." : " · " + e.title + ".");
                if (!e.plot.isEmpty()) c.plot = e.plot;
            }
            c.button = p.position > 0 ? (code.isEmpty() ? "Resume" : "Resume " + code) : code.isEmpty() ? "Play" : "Play " + code;
            c.after = p.position > 0 && p.duration > p.position ? LiveText.mins(p.duration - p.position) + " left" : "";
            c.second = "From the Start";
            c.secondIcon = Icons07.RESTART;
            c.plus = false;
        } else if (t.series) {
            Resume.Point p = Resume.latest(tv, t.key);
            Catalog.Episode e = d == null || p == null ? null : d.episode(p.episode);
            if (e != null) c.button = (p.position > 0 ? "Resume S" : "Play S") + e.season + " E" + e.number;
            else if (d != null && !d.episodes().isEmpty()) c.button = "Play S" + d.episodes().get(0).season + " E" + d.episodes().get(0).number;
            else c.button = "Play";
            c.second = "";
            c.plus = true;
            c.plusOn = on;
        } else {
            c.button = "Play";
            c.second = d != null && !TitleScreen.trailerId(d.trailer).isEmpty() ? "Trailer" : "";
            c.secondIcon = Icons07.FILM;
            c.plus = true;
            c.plusOn = on;
        }
        c.buttonIcon = Icons07.PLAY;
        c.stripLabel = t.series ? "" : "MORE LIKE THIS";
    }

    /** Pure: "S2 E4" from a resume point's line ("S2 E4 · Salt"), else "". */
    static String episodeCode(String subtitle) {
        if (subtitle == null) return "";
        int dot = subtitle.indexOf('·');
        String code = (dot > 0 ? subtitle.substring(0, dot) : subtitle).trim();
        return code.matches("S\\d+ E\\d+") ? code : "";
    }

    private static String picture(Catalog.Title t, Catalog.Details d) {
        return d != null && !d.backdrop.isEmpty() ? d.backdrop : !t.backdrop.isEmpty() ? t.backdrop : t.poster;
    }

    // ---- the strip ----------------------------------------------------------------------------------------------

    @Override public void focused(HomeCard.Card card) {
        h.removeCallbacks(fetch);
        h.removeCallbacks(extras);
        h.removeCallbacks(score);
        Object o = card.item;
        if (o instanceof Channel) {
            channelStrip(card, (Channel) o);
            if (sportKeys.contains(((Channel) o).key)) {
                scoreFor = card;
                if (!showScore(card)) h.postDelayed(score, 500);
            }
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
        // TMDB's logo (and its years and certificate): kept ones at once, else asked for when the remote rests on it.
        if (!extrasKnown.containsKey(t.key)) {
            Tmdb.Extras kept = Tmdb.keptExtras(tv, t);
            if (kept != null) {
                extras(card, t, kept);
            } else {
                extrasFor = card;
                h.postDelayed(extras, 700);
            }
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
            words(card, t, d, extrasKnown.get(t.key));
            home.refresh(card);
            if (home.card() == card) titleStrip(card, t, d);
        });
    }

    private void fetchExtras() {
        HomeCard.Card card = extrasFor;
        Catalog.Title t = card == null ? null : titleOf(card.item);
        if (t == null) return;
        Exec.io(() -> {
            Tmdb.Extras x = Tmdb.extras(tv, t);
            if (x != null) Exec.main(() -> extras(card, t, x));
        });
    }

    /** A title's extras known: kept, and its card's words made again with them. */
    private void extras(HomeCard.Card card, Catalog.Title t, Tmdb.Extras x) {
        if (extrasKnown.size() > 200) extrasKnown.clear();
        extrasKnown.put(t.key, x);
        if (home == null) return;
        words(card, t, Catalog.get(tv).cachedDetails(t), x);
        home.refresh(card);
    }

    /** Previews: TMDB's extras for a title as if it had answered. */
    void previewExtras(Catalog.Title t, Tmdb.Extras x) {
        extrasKnown.put(t.key, x);
        fill();
    }

    /** The match on a sport channel's card: known (asked in the last minute) shown at once (true); else asked for. */
    private boolean showScore(HomeCard.Card card) {
        Channel c = (Channel) card.item;
        Object[] had = scores.get(c.key);
        if (had == null || System.currentTimeMillis() - (Long) had[0] > 60_000L) return false;
        score(card, (Game) had[1]);
        return true;
    }

    /** Off the main thread: today's football, the game this channel shows, its state held back to the channel's delay. */
    private void fetchScore() {
        HomeCard.Card card = scoreFor;
        if (card == null || !(card.item instanceof Channel)) return;
        Channel c = (Channel) card.item;
        Library lib = Library.get(tv);
        Exec.io(() -> {
            long now = System.currentTimeMillis();
            Game held = null;
            try {
                Game g = Matching.gameOn(lib, c, now, BoardLayer.relevant(SportsData.football(5 * 60_000L), now));
                if (g != null) held = SportsData.line(SportsData.FOOTBALL + ":" + g.id).at(now, InStep.delay(tv, c.key));
            } catch (RuntimeException ignored) {
                // no score this time
            }
            Game got = held;
            Exec.main(() -> {
                scores.put(c.key, new Object[]{System.currentTimeMillis(), got});
                score(card, got);
            });
        });
    }

    /** A match's score on its card (a live one only: never before the picture shows it, InStep's delay). */
    private void score(HomeCard.Card card, Game g) {
        if (g == null || g.state != Game.LIVE) {
            card.score = "";
        } else {
            card.home = g.home == null ? "" : g.home;
            card.away = g.away == null ? "" : g.away;
            card.score = g.homeScore + " – " + g.awayScore;
            card.minute = g.detail == null ? "" : g.detail;
            card.homeCrest = g.homeLogo == null ? "" : g.homeLogo;
            card.awayCrest = g.awayLogo == null ? "" : g.awayLogo;
            card.red = !g.reds.isEmpty();
            // The score says it: no story under it.
            card.plot = "";
        }
        if (home != null) home.refresh(card);
    }

    /** Previews: a match's score on a channel's card as if it had been found. */
    void previewScore(String channelKey, Game g) {
        scores.put(channelKey, new Object[]{System.currentTimeMillis(), g});
        HomeCard.Card c = home.card();
        if (c != null && c.item instanceof Channel && ((Channel) c.item).key.equals(channelKey)) score(c, g);
    }

    /**
     * A channel's strip: its later programmes with their times, then what's on now on the channels after it in the
     * section, each with how long is left and how far through it is.
     */
    private void channelStrip(HomeCard.Card card, Channel c) {
        Library lib = Library.get(tv);
        Guide.Schedule s = lib.schedule(c.key);
        long now = System.currentTimeMillis();
        String name = ChannelEdits.name(c);
        List<HomeCard.Tile> tiles = new ArrayList<>();
        if (s != null) {
            int i = s.at(now);
            int from = i >= 0 ? i + 1 : Math.max(0, s.after(now));
            for (int k = from; k < s.start.length && tiles.size() < LATER; k++) {
                if (s.stop[k] <= now) continue;
                HomeCard.Tile t = new HomeCard.Tile(new long[]{s.start[k], s.stop[k], k}, "", s.title[k], -1f);
                t.chip = LiveText.hm(s.start[k]);
                t.sub = name;
                t.ch = c;
                t.schedule = s;
                t.at = k;
                tiles.add(t);
            }
        }
        // What's on now on the channels after it in this section.
        HomeCard.Section sec = home.current();
        if (sec != null) {
            int at = sec.cards.indexOf(card);
            for (int k = at + 1; k < sec.cards.size() && tiles.size() < LATER + ALSO; k++) {
                Object o = sec.cards.get(k).item;
                if (!(o instanceof Channel)) continue;
                Channel other = (Channel) o;
                Guide.Schedule os = lib.schedule(other.key);
                int i = os == null ? -1 : os.at(now);
                if (i < 0) continue;
                HomeCard.Tile t = new HomeCard.Tile(other, "", os.title[i],
                        (now - os.start[i]) / (float) Math.max(1, os.stop[i] - os.start[i]));
                int n = lib.number(other);
                t.chip = n > 0 ? "NOW · " + n : "NOW";
                t.sub = ChannelEdits.name(other) + " · " + LiveText.left(now, os.stop[i]);
                t.ch = other;
                t.schedule = os;
                t.at = i;
                tiles.add(t);
            }
        }
        home.strip(card, tiles, 0);
    }

    /** A series' episodes (every season's, the label following the season in view), or a film's like ones. */
    private void titleStrip(HomeCard.Card card, Catalog.Title t, Catalog.Details d) {
        List<HomeCard.Tile> tiles = new ArrayList<>();
        int at = 0;
        if (t.series) {
            Resume.Point p = card.item instanceof Resume.Point ? (Resume.Point) card.item : Resume.latest(tv, t.key);
            for (Catalog.Season s : d.seasons) {
                String heading = "SEASON " + s.number;
                for (Catalog.Episode e : s.episodes) {
                    if (p != null && e.id.equals(p.episode)) at = tiles.size();
                    Resume.Point ep = Resume.get(tv, t.key, e.id);
                    HomeCard.Tile tile = new HomeCard.Tile(e, e.still.isEmpty() ? picture(t, d) : e.still,
                            "E" + e.number + " · " + (e.title.isEmpty() ? "Episode " + e.number : e.title),
                            ep != null && ep.position > 0 ? ep.progress() : -1f);
                    tile.sub = e.minutes > 0 ? e.minutes + " min" : "";
                    tile.heading = heading;
                    tiles.add(tile);
                }
            }
        } else {
            for (Catalog.Category c : Catalog.get(tv).categories(false)) {
                if (!c.name.equals(t.category)) continue;
                for (Catalog.Title o : c.titles) {
                    if (tiles.size() >= 10) break;
                    if (o == t) continue;
                    HomeCard.Tile tile = new HomeCard.Tile(o, o.poster.isEmpty() ? picture(o, Catalog.get(tv).cachedDetails(o)) : o.poster,
                            o.name, -1f);
                    float r = Billboard.rating(o.rating);
                    tile.sub = o.year + (r > 0f ? (o.year.isEmpty() ? "" : " · ") + String.format(Locale.UK, "★ %.1f", r) : "");
                    tile.poster = true;
                    tiles.add(tile);
                }
            }
        }
        home.strip(card, tiles, at);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            zap(code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1);
            return true;
        }
        if (editorial != null) return editorial.key(code);
        return home.key(code);
    }

    /** The next or the last channel, full screen (Settings → Categories and Parental control: past hidden and locked ones). */
    private void zap(int dir) {
        Library lib = Library.get(tv);
        Channel from = tv.live.channel() != null ? tv.live.channel() : lib.lastChannel();
        Channel c = lib.step(from, dir);
        if (c != null && !com.gathin.homefieldtv.prefs.Parental.blocks(tv, c)) tv.watch(c);
    }

    @Override void ok() {
        if (editorial != null) editorial.ok();
        else home.ok();
    }

    @Override void okLong() {
        if (editorial != null) {
            editorial.okLong();
            return;
        }
        HomeCard.Card c = home.card();
        if (home.area() == HomeCard.CARD && c != null && c.item instanceof Channel) {
            Library.get(tv).toggleFavourite((Channel) c.item);
            return;
        }
        ok();
    }

    @Override boolean back() {
        if (editorial != null) return editorial.back();
        if (home.back()) return true;
        menu();
        return true;
    }

    @Override public void section(int index) {
        // The new section's card is shown at once (focused() follows).
    }

    /** Up from the sections, or Back there: the remote up to 07's tab bar. */
    @Override public void menu() {
        if (editorial != null) {
            editorial.toBar();
            return;
        }
        if (tv.tabs() == null) return;
        tv.tabs().enter();
        home.setActive(false);
    }

    @Override boolean shell() {
        return true;
    }

    @Override int tab() {
        return TabBar.HOME;
    }

    /** Down from the tab bar: the sections along the top of the page. */
    @Override void fromTabs() {
        if (editorial != null) {
            editorial.fromTabs();
            return;
        }
        home.setActive(true);
        home.fromAbove();
    }

    @Override public void chosen(HomeCard.Card card) {
        Object o = card.item;
        if (o instanceof Channel) {
            tv.watch((Channel) o);
        } else if (o instanceof Catalog.Title) {
            tv.openTitle((Catalog.Title) o);
        } else if (o instanceof Resume.Point) {
            carryOn((Resume.Point) o, false);
        }
    }

    /** MENU on a card: Start Over (from the programme's start, in the channel's catch-up), From the Start, the trailer, My List. */
    @Override public void second(HomeCard.Card card) {
        Object o = card.item;
        if (o instanceof Channel) {
            Channel c = (Channel) o;
            if (card.at >= 0 && card.schedule != null && Catchup.source(c) != null) {
                tv.watchFrom(c, card.schedule.start[card.at], card.schedule.stop[card.at]);
            }
        } else if (o instanceof Resume.Point) {
            carryOn((Resume.Point) o, true);
        } else if (o instanceof Catalog.Title) {
            Catalog.Title t = (Catalog.Title) o;
            Catalog.Details d = Catalog.get(tv).cachedDetails(t);
            String id = d == null ? "" : TitleScreen.trailerId(d.trailer);
            if (!t.series && !id.isEmpty()) {
                TitleScreen.trailer(tv, id);
            } else {
                boolean on = MyList.toggle(tv, t.key);
                Toast.makeText(tv, on ? t.name + " added to My list" : t.name + " taken off My list", Toast.LENGTH_SHORT).show();
                words(card, t, d, extrasKnown.get(t.key));
                home.refresh(card);
            }
        }
    }

    /**
     * Continue watching: on at once from where it was (fromStart: from its start, every source first); an episode needs
     * its series' page read, else its page.
     */
    private void carryOn(Resume.Point p, boolean fromStart) {
        Catalog.Title t = Catalog.get(tv).title(p.title);
        if (t == null) return;
        if (!t.series && p.episode.isEmpty()) {
            tv.play(t, null, fromStart ? 0 : p.position, fromStart);
            return;
        }
        Catalog.get(tv).details(t, d -> {
            Catalog.Episode e = d == null ? null : d.episode(p.episode);
            if (e != null) tv.play(t, e, fromStart ? 0 : p.position, fromStart);
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
        } else if (o instanceof Channel) {
            tv.watch((Channel) o);
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
        home.focus(section, card, where, strip);
    }

    /** Previews: the cross-fade from one card to the next caught part way. */
    void previewBetween(float at) {
        home.between(at);
    }

    /** Previews and tests: a section's place by its tag (ON_NOW, FILMS…), -1 when it isn't shown. */
    int previewSection(String tag) {
        return home.sectionOf(tag);
    }
}
