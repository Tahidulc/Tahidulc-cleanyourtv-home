package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sports.ChannelLogos;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnAir;
import com.gathin.tvhomephone.watch.Tmdb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the player's info tile shows for the programme on now: the guide's own (title, times, description, what's
 * next), the channel's number and logo, and its kind (TileKind); then, read off the main thread, the game it shows
 * with both crests, the competition's logo, the score and each club's form (ESPN), or the film's or series' year,
 * genres, rating, certificate, still and the next programme's poster (TMDB, with the key a paired TV gave).
 */
final class TileData {
    private TileData() {}

    static final class Info {
        Channel ch;
        int number;
        /** The channel's logo: the playlist's, else Sky's guide logo by its name; null for none. */
        String channelLogo;
        String title = "", desc = "", episode;
        long start, stop;
        boolean onNow;
        String nextTitle = "";
        long nextStart, nextStop;
        TileKind.Kind kind = TileKind.Kind.OTHER;
        /** From TMDB ("" until read, or when there is none). */
        String year = "", genres = "", rating = "", certificate = "", overview = "", still, poster, nextPoster,
                nextGenres = "";
        /** The game shown, and its numbers so far once it has started ({label, home, away}). */
        Game game;
        List<String[]> stats = new ArrayList<>();
        /** Facts about the game, {tag, words} (Facts): the table, the runs, the meetings, its numbers once it is on. */
        List<String[]> facts = new ArrayList<>();
        /** The stream's own marks: HD (720 lines or more) and subtitles; the player sets them. */
        boolean hd, subtitles;
        /** And what the stream says of its picture and sound (MediaTags): "1080p", "HDR10", "Dolby Digital 5.1". */
        String video = "", hdr = "", audio = "";
        /** Whether the background reads have finished (the tile is drawn again then). */
        boolean enriched;

        /** A copy for the background reads to fill in (the one on screen is only read on the main thread). */
        Info copy() {
            Info o = new Info();
            o.ch = ch; o.number = number; o.channelLogo = channelLogo; o.title = title; o.desc = desc; o.episode = episode;
            o.start = start; o.stop = stop; o.onNow = onNow; o.nextTitle = nextTitle; o.nextStart = nextStart;
            o.nextStop = nextStop; o.kind = kind; o.year = year; o.genres = genres; o.rating = rating;
            o.certificate = certificate; o.overview = overview; o.still = still; o.poster = poster; o.nextPoster = nextPoster;
            o.nextGenres = nextGenres; o.hd = hd; o.subtitles = subtitles; o.video = video; o.hdr = hdr; o.audio = audio; o.game = game; o.stats = new ArrayList<>(stats); o.facts = new ArrayList<>(facts);
            o.enriched = enriched;
            return o;
        }

        /** Which programme this is (the tile is drawn again only for the same one). */
        String id() {
            return (ch == null ? "" : ch.key) + "|" + start;
        }
    }

    /** The guide's part, at once (main thread). */
    static Info now(Context c, Library lib, Channel ch) {
        Info i = new Info();
        i.ch = ch;
        i.number = lib.number(ch);
        i.channelLogo = !ch.logo.isEmpty() ? ch.logo : ChannelLogos.sky(ch.name);
        Guide.Schedule s = lib.schedule(ch.key);
        long now = System.currentTimeMillis();
        int at = s == null ? -1 : s.at(now), next = s == null ? -1 : s.after(now);
        if (at >= 0) {
            i.onNow = true;
            i.title = s.title[at];
            i.desc = s.desc[at];
            i.start = s.start[at];
            i.stop = s.stop[at];
        }
        if (i.title.isEmpty()) i.title = ch.name;
        if (next >= 0) {
            i.nextTitle = s.title[next];
            i.nextStart = s.start[next];
            i.nextStop = s.stop[next];
        }
        i.episode = TileKind.episode(i.title, i.desc);
        i.kind = TileKind.of(ch.name, ch.group, i.title, i.desc, false);
        i.year = TileKind.year(i.title, i.desc);
        return i;
    }

    interface Done {
        void done(Info i);
    }

    /**
     * The ESPN and TMDB parts, read on the app's threads into a copy of shown; done gets the copy on the main thread
     * (not when nothing was found).
     */
    static void enrich(Context c, Info shown, Done done) {
        Context app = c.getApplicationContext();
        Info i = shown.copy();
        App.io.execute(() -> {
            boolean changed = false;
            try {
                changed |= game(i);
                if (i.kind == TileKind.Kind.FILM) changed |= film(app, i);
                else if (i.kind == TileKind.Kind.SERIES || i.kind == TileKind.Kind.OTHER) changed |= series(app, i);
                if (!i.nextTitle.isEmpty() && i.kind != TileKind.Kind.MATCH) changed |= next(app, i);
            } catch (RuntimeException ignored) {
                // a read that failed leaves the guide's own words
            }
            i.enriched = true;
            if (changed) App.main.post(() -> done.done(i));
        });
    }

    /** The game it shows: for a sports channel, or a title that reads as a fixture on any channel. */
    private static boolean game(Info i) {
        boolean sports = TileKind.sportsChannel(i.ch.name, i.ch.group);
        if (!sports && !OnAir.looksLikeFixture(i.title) && !OnAir.looksLikeFixture(i.desc)) return false;
        List<Game> today = OnAir.today(60_000L);
        Game g = OnAir.match(i.title, i.desc, i.start, i.stop, today);
        if (g == null) return false;
        i.game = g;
        i.kind = TileKind.Kind.MATCH;
        if (g.slug != null && g.id != null) {
            Espn.Summary s = Espn.summary(g.slug, g.id, 30_000L);
            if (g.state != Game.PRE) i.stats = s.stats;
            i.facts = com.gathin.tvhomephone.sports.Facts.of(g, s);
        }
        return true;
    }

    // ---- TMDB ----------------------------------------------------------------------------------------------------

    private static final Map<String, JSONObject> FOUND = new HashMap<>();

    private static JSONObject search(Context c, String kind, String title, String year) {
        String key = kind + "|" + title.toLowerCase(Locale.ROOT) + "|" + year;
        synchronized (FOUND) {
            if (FOUND.containsKey(key)) return FOUND.get(key);
        }
        JSONObject first = null;
        try {
            String q = URLEncoder.encode(title, StandardCharsets.UTF_8.name());
            String path = "search/" + kind + "?query=" + q + "&include_adult=false&language=en-GB"
                    + (!year.isEmpty() && "movie".equals(kind) ? "&year=" + year : "");
            JSONArray r = Tmdb.get(c, path).optJSONArray("results");
            for (int k = 0; r != null && k < r.length() && first == null; k++) {
                JSONObject o = r.optJSONObject(k);
                if (o == null) continue;
                if ("multi".equals(kind) && !"movie".equals(o.optString("media_type")) && !"tv".equals(o.optString("media_type"))) continue;
                String name = o.optString("title", o.optString("name"));
                // The same title only (a guide's "Verity" is not TMDB's "Verity's Secret").
                if (same(name, title) || same(o.optString("original_title", o.optString("original_name")), title)) first = o;
            }
        } catch (Exception ignored) {
            // TMDB not reachable or no key: the guide's words only
        }
        synchronized (FOUND) {
            if (FOUND.size() > 200) FOUND.clear();
            FOUND.put(key, first);
        }
        return first;
    }

    static boolean same(String a, String b) {
        return norm(a).equals(norm(b)) && !norm(a).isEmpty();
    }

    private static String norm(String s) {
        return s == null ? "" : java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace("&", "and").replaceAll("^the ", "").replaceAll("[^a-z0-9]", "");
    }

    private static String genres(JSONObject o, int most) {
        JSONArray ids = o.optJSONArray("genre_ids");
        List<String> out = new ArrayList<>();
        if (ids != null) for (int k = 0; k < ids.length() && out.size() < most; k++) {
            String g = Tmdb.genre(ids.optInt(k));
            if (g != null && !g.isEmpty()) out.add(g);
        }
        JSONArray full = o.optJSONArray("genres");
        if (out.isEmpty() && full != null) for (int k = 0; k < full.length() && out.size() < most; k++) {
            JSONObject g = full.optJSONObject(k);
            if (g != null) out.add(g.optString("name"));
        }
        return android.text.TextUtils.join(" · ", out);
    }

    private static String rating(JSONObject o) {
        double v = o.optDouble("vote_average", 0);
        return v > 0 && o.optInt("vote_count", 0) >= 20 ? String.format(Locale.UK, "★ %.1f", v) : "";
    }

    private static boolean film(Context c, Info i) {
        if (!Tmdb.ready(c)) return false;
        String t = TileKind.lookupTitle(i.title);
        if (t.isEmpty()) return false;
        JSONObject m = search(c, "movie", t, i.year);
        if (m == null && !i.year.isEmpty()) m = search(c, "movie", t, "");
        if (m == null) return false;
        String date = m.optString("release_date", "");
        if (date.length() >= 4) i.year = date.substring(0, 4);
        i.genres = genres(m, 3);
        i.rating = rating(m);
        i.overview = m.optString("overview", "");
        i.still = Tmdb.image(m.optString("backdrop_path", null), "w780");
        i.poster = Tmdb.image(m.optString("poster_path", null), "w342");
        try {
            JSONObject d = Tmdb.get(c, "movie/" + m.optInt("id") + "?append_to_response=release_dates");
            i.certificate = certificate(d);
        } catch (Exception ignored) {
            // no certificate then
        }
        return true;
    }

    /** The UK certificate (PG, 12A, 15) from a film's release dates, else "". */
    static String certificate(JSONObject d) {
        JSONObject rd = d == null ? null : d.optJSONObject("release_dates");
        JSONArray r = rd == null ? null : rd.optJSONArray("results");
        for (int k = 0; r != null && k < r.length(); k++) {
            JSONObject x = r.optJSONObject(k);
            if (x == null || !"GB".equals(x.optString("iso_3166_1"))) continue;
            JSONArray dates = x.optJSONArray("release_dates");
            for (int j = 0; dates != null && j < dates.length(); j++) {
                String cert = dates.optJSONObject(j) == null ? "" : dates.optJSONObject(j).optString("certification", "");
                if (!cert.isEmpty()) return cert;
            }
        }
        return "";
    }

    /** A series (or anything else TMDB knows as one): its genres, rating, picture; the episode's still where it can. */
    private static boolean series(Context c, Info i) {
        if (!Tmdb.ready(c) || TileKind.newsChannel(i.ch.name, i.ch.group)) return false;
        String t = TileKind.lookupTitle(i.title);
        if (t.isEmpty() || t.equalsIgnoreCase(i.ch.name)) return false;
        JSONObject s = search(c, "tv", t, "");
        if (s == null) return false;
        i.genres = genres(s, 2);
        i.rating = rating(s);
        i.still = Tmdb.image(s.optString("backdrop_path", null), "w780");
        i.poster = Tmdb.image(s.optString("poster_path", null), "w342");
        if (i.overview.isEmpty() && i.desc.isEmpty()) i.overview = s.optString("overview", "");
        int[] se = seasonEpisode(i.episode);
        if (se != null) {
            try {
                JSONObject e = Tmdb.get(c, "tv/" + s.optInt("id") + "/season/" + se[0] + "/episode/" + se[1]);
                String still = Tmdb.image(e.optString("still_path", null), "w780");
                if (still != null) i.still = still;
            } catch (Exception ignored) {
                // the series' picture then
            }
        }
        return true;
    }

    /** {season, episode} out of "S2 · E4", or null. */
    static int[] seasonEpisode(String ep) {
        if (ep == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^S(\\d+) · E(\\d+)$").matcher(ep);
        return m.find() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    private static boolean next(Context c, Info i) {
        if (!Tmdb.ready(c)) return false;
        String t = TileKind.lookupTitle(i.nextTitle);
        if (t.isEmpty()) return false;
        JSONObject o = search(c, "multi", t, "");
        if (o == null) return false;
        i.nextPoster = Tmdb.image(o.optString("poster_path", null), "w185");
        i.nextGenres = genres(o, 2);
        return i.nextPoster != null || !i.nextGenres.isEmpty();
    }
}
