package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sports.ChannelLogos;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnAir;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home's Recently watched: what was watched on this phone, all kinds together and newest first. That covers films and
 * episodes from the family's own playlists (part-way or to the end), the games watched on Live TV (as ESPN has them,
 * with the channel they were on), and the other programmes watched on Live TV (as the guide had them). Live TV counts
 * a channel once it has played a minute, so zapping past doesn't count, then each programme on it while it plays on.
 * There is one card for each film, series (at its latest episode), game and programme. The list keeps MAX at most,
 * none older than MAX_AGE. It is kept on this phone only. Live TV keeps no stream addresses (the channel's key plays it
 * again); films and episodes keep theirs only as the playlists' index does (as VodHistory does).
 */
public final class Watched {
    private Watched() {}

    public static final int FILM = 0, EPISODE = 1, SPORT = 2, PROGRAMME = 3;
    static final int MAX = 30;
    static final long MAX_AGE = 30L * 24 * 3600_000L;
    /** The same programme is written again only after this (it is still being watched). */
    static final long REWRITE = 5 * 60_000L;
    /** A channel with no guide counts as one programme for this long. */
    static final long NO_GUIDE_SPAN = 3 * 3600_000L;
    /** A game kicked off longer ago than this is over, whatever was seen of it last (extra time and penalties too). */
    static final long LIVE_SPAN = 3 * 3600_000L + 30 * 60_000L;
    private static final String PREFS = "watched";

    /** One thing watched. */
    public static final class Entry {
        public int kind = PROGRAMME;
        /** When it was last watched (ms). */
        public long at;
        /** Films and episodes: what plays it again (the player's own record), and whether it was played to its end. */
        public VodHistory.Entry vod;
        public boolean finished;
        /** Live TV: the channel (its key plays it again), the programme as the guide had it, its kind in a word. */
        public String channelKey = "", channelName = "", channelLogo = "", title = "", tag = "";
        public long start, stop;
        /** A game: as ESPN had it, its score kept up to date while it is recent. */
        public Game game;

        public boolean isVod() {
            return kind == FILM || kind == EPISODE;
        }

        /** Its name on the card: the film's, the series', the game's ("Arsenal v Chelsea") or the programme's. */
        public String name() {
            if (isVod()) return vod == null ? "" : vod.name.isEmpty() ? vod.title : vod.name;
            if (kind == SPORT && game != null) return game.title();
            return title.isEmpty() ? channelName : title;
        }

        JSONObject json() throws JSONException {
            JSONObject o = new JSONObject().put("kind", kind).put("at", at);
            if (vod != null) o.put("vod", vod.json()).put("done", finished);
            if (!isVod()) {
                o.put("ch", channelKey).put("chName", channelName).put("chLogo", channelLogo).put("title", title)
                        .put("tag", tag).put("start", start).put("stop", stop);
                if (game != null) o.put("game", game.json());
            }
            return o;
        }

        static Entry from(JSONObject o) {
            Entry e = new Entry();
            e.kind = o.optInt("kind", PROGRAMME);
            e.at = o.optLong("at");
            JSONObject v = o.optJSONObject("vod");
            if (v != null) e.vod = VodHistory.Entry.from(v);
            e.finished = o.optBoolean("done");
            e.channelKey = o.optString("ch");
            e.channelName = o.optString("chName");
            e.channelLogo = o.optString("chLogo");
            e.title = o.optString("title");
            e.tag = o.optString("tag");
            e.start = o.optLong("start");
            e.stop = o.optLong("stop");
            JSONObject g = o.optJSONObject("game");
            if (g != null) e.game = Game.from(g);
            if (e.kind == SPORT && e.game == null) e.kind = PROGRAMME;
            return e;
        }

        /** Whether it can be shown: a film or episode with somewhere to play it, or a channel. */
        boolean usable() {
            if (isVod()) return vod != null && !vod.plays.isEmpty();
            return !channelKey.isEmpty() || (kind == SPORT && game != null);
        }

        /** The same film, series, game or programme. */
        boolean same(Entry o) {
            if (isVod() != o.isVod()) return false;
            if (isVod()) return vod != null && o.vod != null && vod.same(o.vod);
            if (game != null && o.game != null && game.id != null && game.id.equals(o.game.id)) return true;
            if (channelKey.isEmpty() || !channelKey.equals(o.channelKey)) return false;
            if (start > 0 || o.start > 0) return start == o.start;
            return Math.abs(at - o.at) < NO_GUIDE_SPAN;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- reading --------------------------------------------------------------------------------------------------

    /**
     * Everything watched, newest first: this list, plus anything part-way in Continue watching from before the list
     * was kept.
     */
    public static synchronized List<Entry> list(Context c) {
        return mix(stored(c), VodHistory.list(c), System.currentTimeMillis());
    }

    private static List<Entry> stored(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(Entry.from(o));
            }
        } catch (JSONException ignored) {
            // none readable
        }
        return out;
    }

    /**
     * The list with Continue watching's entries it doesn't have yet (as films and episodes left part-way), only what
     * can be shown and isn't older than MAX_AGE, newest first. Pure.
     */
    static List<Entry> mix(List<Entry> stored, List<VodHistory.Entry> resume, long now) {
        List<Entry> all = new ArrayList<>(stored);
        for (VodHistory.Entry v : resume) {
            Entry e = ofVod(v, false);
            boolean known = false;
            for (Entry o : all) known |= o.same(e);
            if (!known) all.add(e);
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) if (e.usable() && now - e.at <= MAX_AGE) out.add(e);
        out.sort((a, b) -> Long.compare(b.at, a.at));
        return out;
    }

    // ---- recording ------------------------------------------------------------------------------------------------

    /** A film or episode as the player keeps it. */
    static Entry ofVod(VodHistory.Entry v, boolean finished) {
        Entry e = new Entry();
        e.kind = v.tv ? EPISODE : FILM;
        e.at = v.at;
        e.vod = v;
        e.finished = finished;
        return e;
    }

    /** A film or episode left (VodPlayerActivity, as it stops): part-way or played to its end. */
    static synchronized void vod(Context c, VodHistory.Entry v, boolean finished) {
        save(c, put(stored(c), ofVod(v, finished), MAX));
    }

    /**
     * Live TV has played this channel a while (Playback, main thread): the programme on it now, kept; then, for sport,
     * the game it shows looked up off the main thread and put with it.
     */
    static void live(Context c, Channel ch) {
        if (c == null || ch == null) return;
        Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        Entry e = new Entry();
        e.at = now;
        e.channelKey = ch.key;
        e.channelName = ch.name;
        String logo = ch.logo.isEmpty() ? ChannelLogos.sky(ch.name) : ch.logo;
        e.channelLogo = logo == null ? "" : logo;
        String desc = "";
        Guide.Schedule s = Library.get(app).schedule(ch.key);
        int at = s == null ? -1 : s.at(now);
        if (at >= 0) {
            e.title = s.title[at] == null ? "" : s.title[at];
            desc = s.desc[at] == null ? "" : s.desc[at];
            e.start = s.start[at];
            e.stop = s.stop[at];
        }
        e.tag = tag(TileKind.of(ch.name, ch.group, e.title, desc, false), ch.name, ch.group);
        synchronized (Watched.class) {
            List<Entry> list = stored(app);
            if (!list.isEmpty() && list.get(0).same(e) && now - list.get(0).at < REWRITE) return;
            save(app, put(list, e, MAX));
        }
        boolean sport = TileKind.sportsChannel(ch.name, ch.group) || OnAir.looksLikeFixture(e.title) || OnAir.looksLikeFixture(desc);
        if (!sport || e.title.isEmpty()) return;
        String words = desc;
        App.io.execute(() -> {
            try {
                Game g = OnAir.match(e.title, words, e.start, e.stop, OnAir.today(60_000L));
                if (g != null) withGame(app, e, g);
            } catch (RuntimeException ignored) {
                // left as a programme
            }
        });
    }

    /** The game found for a programme just kept: the programme becomes that game's card. */
    private static synchronized void withGame(Context c, Entry programme, Game g) {
        List<Entry> list = stored(c);
        for (Entry o : list) {
            if (o.isVod() || !o.same(programme)) continue;
            o.kind = SPORT;
            o.game = g;
            save(c, put(list, o, MAX));
            return;
        }
    }

    /** The programme's kind in a word, on its card ("Sport", "Film", "News", "TV"). Pure. */
    static String tag(TileKind.Kind k, String channel, String group) {
        if (k == TileKind.Kind.MATCH || TileKind.sportsChannel(channel, group)) return "Sport";
        if (k == TileKind.Kind.FILM) return "Film";
        if (TileKind.newsChannel(channel, group)) return "News";
        return "TV";
    }

    /**
     * One taken off Recently watched (held on Home). A film or series goes from Continue watching too, any of its
     * episodes, or it would come back from there.
     */
    public static synchronized void remove(Context c, Entry e) {
        List<Entry> out = new ArrayList<>();
        for (Entry o : stored(c)) if (!o.same(e)) out.add(o);
        save(c, out);
        if (!e.isVod() || e.vod == null) return;
        for (VodHistory.Entry v : VodHistory.list(c)) if (v.same(e.vod)) VodHistory.remove(c, v.key);
    }

    /**
     * The list with e first, in place of the same film, series, game or programme (a programme's game, found before,
     * kept), at most max long. Pure.
     */
    static List<Entry> put(List<Entry> list, Entry e, int max) {
        List<Entry> out = new ArrayList<>();
        out.add(e);
        for (Entry o : list) {
            if (o == e) continue;
            if (o.same(e)) {
                if (e.game == null && o.game != null && !e.isVod()) {
                    e.game = o.game;
                    e.kind = SPORT;
                }
                continue;
            }
            if (out.size() < max) out.add(o);
        }
        return out;
    }

    private static void save(Context c, List<Entry> list) {
        JSONArray a = new JSONArray();
        long now = System.currentTimeMillis();
        for (Entry e : list) {
            if (now - e.at > MAX_AGE) continue;
            try {
                a.put(e.json());
            } catch (JSONException ignored) {
                // that one left out
            }
        }
        prefs(c).edit().putString("list", a.toString()).apply();
    }

    /** Everything forgotten (the playlists removed). */
    static synchronized void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    // ---- the games' scores ----------------------------------------------------------------------------------------

    /**
     * The games watched brought up to date (off the main thread): from today's scoreboards as Home has them, else,
     * for a game not over when it was last seen, from its own day's. True when any changed.
     */
    public static boolean refreshGames(Context c, List<Game> today) {
        List<Entry> list;
        synchronized (Watched.class) {
            list = stored(c);
        }
        Map<String, Game> fresh = new HashMap<>();
        if (today != null) for (Game g : today) if (g != null && g.id != null) fresh.put(g.id, g);
        Map<String, Game> found = new HashMap<>();
        int asked = 0;
        long now = System.currentTimeMillis();
        for (Entry e : list) {
            if (e.kind != SPORT || e.game == null || e.game.id == null) continue;
            Game g = fresh.get(e.game.id);
            if (g == null && e.game.state != Game.POST && e.game.slug != null && asked < 4 && now - e.game.kickoff > 2 * 3600_000L) {
                asked++;
                LocalDate day = Instant.ofEpochMilli(e.game.kickoff).atZone(ZoneId.systemDefault()).toLocalDate();
                for (Game d : Espn.scoreboard(e.game.slug, day, 10 * 60_000L)) {
                    if (d != null && e.game.id.equals(d.id)) g = d;
                }
            }
            if (g != null && changed(e.game, g)) found.put(g.id, g);
        }
        if (found.isEmpty()) return false;
        synchronized (Watched.class) {
            List<Entry> now2 = stored(c);
            for (Entry e : now2) {
                if (e.kind == SPORT && e.game != null && e.game.id != null && found.containsKey(e.game.id)) e.game = found.get(e.game.id);
            }
            save(c, now2);
        }
        return true;
    }

    /**
     * Whether the game is on now as far as is known: last seen live, and kicked off less than LIVE_SPAN ago (a game
     * last seen live long ago has ended since, its result not read yet). Pure.
     */
    public static boolean onNow(Game g, long now) {
        return g != null && g.state == Game.LIVE && (g.kickoff <= 0 || now - g.kickoff < LIVE_SPAN);
    }

    /** Whether the game moved on: its score, where it is, or its clock. Pure. */
    static boolean changed(Game was, Game now) {
        return was.homeScore != now.homeScore || was.awayScore != now.awayScore || was.state != now.state
                || was.halftime != now.halftime || !was.detail.equals(now.detail);
    }

    // ---- the words on the cards -----------------------------------------------------------------------------------

    /** When it was watched: "Today 20:15", "Yesterday 21:40", "Mon 19:30" in the last week, else "3 Nov". Pure. */
    static String when(long at, long now, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(at).atZone(zone);
        LocalDate day = t.toLocalDate(), today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String hm = t.format(DateTimeFormatter.ofPattern("HH:mm", Locale.UK));
        if (day.equals(today)) return "Today " + hm;
        if (day.equals(today.minusDays(1))) return "Yesterday " + hm;
        if (day.isAfter(today.minusDays(7)) && !day.isAfter(today)) return t.format(DateTimeFormatter.ofPattern("EEE", Locale.UK)) + " " + hm;
        return t.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK));
    }

    public static String when(long at) {
        return when(at, System.currentTimeMillis(), ZoneId.systemDefault());
    }

    /**
     * The line along a film's or episode's poster: "S2 E4 · 1h 05m left", "48m left", "Watched", or the next
     * episode's for a series watched to the end of one ("Next: S2 E5"). Pure.
     */
    public static String vodLine(Entry e) {
        VodHistory.Entry v = e.vod;
        if (v == null) return "";
        String ep = v.tv && v.episode > 0 ? (v.season > 0 ? "S" + v.season + " " : "") + "E" + v.episode : "";
        if (e.finished) {
            Vod.Episode next = next(e);
            if (next != null) return "Next: " + (next.season > 0 ? "S" + next.season + " " : "") + "E" + next.number;
            return ep.isEmpty() ? "Watched" : ep + " · Watched";
        }
        int left = v.minutesLeft();
        String rest = left > 0 ? span(left) + " left" : "";
        if (ep.isEmpty()) return rest;
        return rest.isEmpty() ? ep : ep + " · " + rest;
    }

    /** "48m", "1h 05m". Pure. */
    static String span(int minutes) {
        return minutes < 60 ? minutes + "m" : minutes / 60 + "h " + String.format(Locale.UK, "%02dm", minutes % 60);
    }

    /** The episode after the one watched to its end, when the series' episodes are known; null otherwise. Pure. */
    public static Vod.Episode next(Entry e) {
        if (e.vod == null || !e.vod.tv || !e.finished) return null;
        List<Vod.Episode> eps = Vod.episodesFrom(e.vod.episodes);
        int i = Vod.nextIndex(eps, e.vod.season, e.vod.episode);
        return i < 0 ? null : eps.get(i);
    }

    /**
     * What the card's tap plays: the film or episode from where it was left, the next episode after one watched to
     * its end, or the film again from the start. Null for a live programme (Home opens its channel).
     */
    public static android.content.Intent play(Context c, Entry e) {
        if (!e.isVod() || e.vod == null) return null;
        VodHistory.Entry v = e.vod;
        Vod.Episode next = next(e);
        if (next != null) {
            String name = v.name.isEmpty() ? v.title : v.name;
            return VodPlayerActivity.intent(c, next.plays(), name + (next.season > 0 ? " · S" + next.season : "") + " E" + next.number,
                    VodPlayerActivity.meta(name, v.tmdb, true, v.poster, next.season, next.number, next.title, v.year, v.alt), v.episodes);
        }
        return VodPlayerActivity.intent(c, v);
    }

    /** Whether the channel is still in the playlists (its card plays it again). Off the main thread: may read them. */
    public static boolean hasChannel(Context c, Entry e) {
        if (e.channelKey.isEmpty()) return false;
        Library lib = Library.get(c);
        lib.ensureNow();
        return lib.byKey(e.channelKey) != null;
    }
}
