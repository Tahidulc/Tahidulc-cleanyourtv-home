package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The credits cover ("That's a wrap"): when a film watched here reaches its credits, or an episode ends, a full page
 * comes up over the app while the credits roll on behind it. For a film: its name and backdrop, a five-star rating
 * (kept as the launcher's own Not for me, Liked it or Loved it, so How was it? does not ask again), the next film of
 * its series (Play it, or Remind me when it is still to come out, which puts it on My List), and three tabs: Up next
 * (the series' next film, then TMDB's picks like it), Cast and crew, and Extras (its featurettes and teasers, played
 * in YouTube). For an episode: the series, the next episode to play (in Debrid Stream the helper opens that very
 * episode; elsewhere the app's own next), the rest of the season, the cast and shows like it; after a season's last
 * episode, "Season N complete", its rating, and a reminder for the next season. A scene after the credits (TMDB's
 * keywords) is said, and the cover lifts in time for it. The time left of the credits counts down in the corner; OK on
 * "Watch the credits" or Back goes back to them, and the cover goes by itself when they end, when the next episode
 * starts, or when Home is pressed. A kind in the Control Centre's Notifications ("creditscover", theme
 * "credits_cover", on unless set false); in its place, How was it? as before.
 */
final class CreditsCover {
    private CreditsCover() { }

    static final String KIND = "creditscover";
    /** A scene after the credits: the cover lifts this long before the end, so it is seen. */
    static final long STINGER_MS = 150_000L;
    private static final long MAX_MS = 15 * 60_000L;
    private static final String IMG = "https://image.tmdb.org/t/p/";
    private static final int BG = 0xFF070B14, WHITE = 0xFFFFFFFF, SOFT = 0xFFB7BCCB, DIM = 0xFF8F96A6, GOLD = 0xFFF2C14E, INK = 0xFF0B0D12;

    private static Handler main;
    private static ExecutorService io;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static synchronized ExecutorService io() {
        if (io == null) io = Executors.newSingleThreadExecutor();
        return io;
    }

    static boolean on(Context c) {
        return Config.themeFlag(c, "credits_cover", true);
    }

    // ---- the sums (pure, for the tests) -----------------------------------------------------------------------------

    /** Stars to the launcher's own ratings: one or two Not for me, three or four Liked it, five Loved it. */
    static int rating(int stars) {
        return stars <= 2 ? WatchRules.NOT_FOR_ME : stars >= 5 ? WatchRules.LOVED : WatchRules.LIKED;
    }

    static String starWord(int stars) {
        switch (stars) {
            case 1: return "Not for me";
            case 2: return "Not really";
            case 3: return "Good";
            case 4: return "Really good";
            case 5: return "Loved it";
            default: return "";
        }
    }

    /** "6:12", "0:45", "1:02:30". */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s >= 3600) return String.format(Locale.UK, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format(Locale.UK, "%d:%02d", s / 60, s % 60);
    }

    /** "2h 36m", "49m"; "" for none. */
    static String runtime(int minutes) {
        if (minutes <= 0) return "";
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    /**
     * Pure: of a series' films ({id, release date} each), the index of the one after id by date, undated (not yet set)
     * last; -1 when id is the last or not there.
     */
    static int nextPart(List<String[]> parts, int id) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) order.add(i);
        order.sort((a, b) -> {
            String x = parts.get(a)[1] == null ? "" : parts.get(a)[1], y = parts.get(b)[1] == null ? "" : parts.get(b)[1];
            if (x.isEmpty() != y.isEmpty()) return x.isEmpty() ? 1 : -1;
            return x.compareTo(y);
        });
        for (int i = 0; i < order.size(); i++) {
            if (String.valueOf(id).equals(parts.get(order.get(i))[0])) return i + 1 < order.size() ? order.get(i + 1) : -1;
        }
        return -1;
    }

    /** The film after this one in its series (TMDB's collection parts); null when it is the last. */
    static JSONObject nextInCollection(JSONArray parts, int id) {
        if (parts == null) return null;
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            l.add(p == null ? new String[]{"", ""} : new String[]{String.valueOf(p.optInt("id")), p.optString("release_date", "")});
        }
        int at = nextPart(l, id);
        return at < 0 ? null : parts.optJSONObject(at);
    }

    /** Pure: what one of TMDB's keywords says of the credits: 2 a scene after them, 1 one during them, 0 nothing. */
    static int stingerOf(int id, String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (id == 179430 || n.contains("aftercreditsstinger")) return 2;
        if (id == 179431 || n.contains("duringcreditsstinger")) return 1;
        return 0;
    }

    /** A scene in the credits by TMDB's keywords: 2 after them, 1 during them, 0 none. */
    static int stinger(JSONObject keywords) {
        JSONArray a = keywords == null ? null : keywords.optJSONArray("keywords");
        int s = 0;
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject k = a.optJSONObject(i);
            if (k != null) s = Math.max(s, stingerOf(k.optInt("id"), k.optString("name", "")));
        }
        return s;
    }

    /** Whether the cover goes now: the end reached, or a scene after the credits about to play. */
    static boolean lifts(long dur, long pos, int stinger) {
        if (dur <= 0 || pos < 0) return false;
        return pos >= dur - 1_000L || (stinger == 2 && pos >= dur - STINGER_MS);
    }

    /**
     * Pure: which of TMDB's videos ({site, type, key} each) to show, in order: YouTube only, featurettes and behind the
     * scenes first, then teasers, clips and trailers; at most n, as their indices.
     */
    static List<Integer> extraOrder(List<String[]> videos, int n) {
        String[] order = {"featurette", "behind the scenes", "teaser", "clip", "trailer"};
        List<Integer> out = new ArrayList<>();
        for (String type : order) {
            for (int i = 0; i < videos.size() && out.size() < n; i++) {
                String[] v = videos.get(i);
                if (!"youtube".equalsIgnoreCase(v[0]) || v[2] == null || v[2].isEmpty() || !type.equalsIgnoreCase(v[1])) continue;
                out.add(i);
            }
        }
        return out;
    }

    /** Pure: the minute of the day something started at nowMinute and lasting minutes ends; -1 when its length is unknown. */
    static int endsAt(int nowMinute, int minutes) {
        return minutes <= 0 ? -1 : ((nowMinute + minutes) % 1440 + 1440) % 1440;
    }

    /**
     * Pure: when the next episode would end if it played now, and how that sits with bed (minutes of the day, bed as
     * FitsTonight has it): "ends 23:47 · 17 min past bed". Bed is said only within two hours of it, or once past it.
     */
    static String endsLine(int nowMinute, int minutes, int bedMinute) {
        int end = endsAt(nowMinute, minutes);
        if (end < 0) return "";
        String s = "ends " + WatchRules.clock(end);
        int before = WatchRules.beforeBed(end, bedMinute);
        if (before == 0) return s + " · right at bedtime";
        return before < 120 ? s + " · " + WatchRules.bedLine(before) : s;
    }

    /** Pure: what is left of the season after episode: "3 left this season", "The season finale is next"; "" at its end. */
    static String leftLine(int episode, int total) {
        int left = total - episode;
        if (episode <= 0 || left <= 0) return "";
        return left == 1 ? "The season finale is next" : left + " left this season";
    }

    /** Pure: an air date ("2026-11-12") as "Thu 12 Nov"; "" when it isn't one. */
    static String dayOf(String date) {
        try {
            return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK));
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Pure: a date ("2027-03-12") as "12 Mar 2027"; the text as it came when it isn't one. */
    static String longDay(String date) {
        try {
            return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK));
        } catch (RuntimeException e) {
            return date == null ? "" : date;
        }
    }

    /** Pure: whether an episode airing on date (TMDB's; "" when it has none yet) is out by today (both "2026-11-05"). */
    static boolean isOut(String date, String today) {
        return date != null && !date.isEmpty() && date.compareTo(today) <= 0;
    }

    /** The extras to show from TMDB's videos (extraOrder). */
    static List<JSONObject> extras(JSONArray results, int n) {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject v = results.optJSONObject(i);
            l.add(v == null ? new String[]{"", "", ""} : new String[]{v.optString("site"), v.optString("type"), v.optString("key", "")});
        }
        List<JSONObject> out = new ArrayList<>();
        for (int i : extraOrder(l, n)) out.add(results.optJSONObject(i));
        return out;
    }

    // ---- what it shows -----------------------------------------------------------------------------------------------

    /** One thing to press: a button, a tab's tile. */
    static final class Item {
        String title = "", line = "";
        Bitmap pic;
        boolean round;
        Runnable action;
    }

    /** The cover's content, read off the main thread. */
    static final class Content {
        int id;
        boolean tv, test;
        String name = "", eyebrow = "", meta = "", genre = "", rateLabel = "", askedKey;
        Bitmap backdrop;
        int stinger;
        final List<Item> buttons = new ArrayList<>();
        final List<String> tabNames = new ArrayList<>();
        final List<List<Item>> tabItems = new ArrayList<>();
        // The session it is over: its app, what it plays, how long.
        MediaController mc;
        String pkg, item;
        long dur;
        // The next-episode page (an episode ended mid-season): the one to play next, its story, the season so far.
        boolean episodePage, nextOut;
        String show = "", synopsis = "";
        int season, episode, total, nextNumber, nextRuntime;
        /** Play the next one: its line says when it would end, kept to the minute. */
        Item play;
    }

    /**
     * The credits (a film) or the end (an episode) reached in a session watched here (WatchCards, off the main thread):
     * the cover, once the title is matched on TMDB. False when it can't be made (How was it? is asked instead).
     */
    static boolean reached(Context c, MediaController mc, String pkg, String title, String sub, long dur, boolean film) {
        String key = Config.themeString(c, "tmdb_key", "").trim();
        if (key.isEmpty()) return false;
        WatchHistory.Entry e = entry(c, pkg, title, sub);
        if (e != null && e.tmdbId == 0) {
            WatchHistory.match(c, key);
            e = entry(c, pkg, title, sub);
        }
        if (e == null || e.tmdbId <= 0) {
            AlertLog.note(c, "credits cover: " + title + " not matched on TMDB");
            return false;
        }
        if (!e.tv && !film) return false;
        Content k;
        try {
            if (e.tv) {
                int[] at = YourShows.position(e);
                k = series(c, key, e.tmdbId, at == null ? 0 : at[0], at == null ? 0 : at[1], e.show != null ? e.show : e.title);
            } else {
                k = film(c, key, e.tmdbId, e.title);
            }
        } catch (Exception ex) {
            AlertLog.note(c, "credits cover could not be made: " + ex);
            return false;
        }
        if (k == null) return false;
        k.mc = mc;
        k.pkg = pkg;
        k.item = title + "|" + sub;
        k.dur = dur;
        Context a = c.getApplicationContext();
        main().post(() -> show(a, k));
        return true;
    }

    private static WatchHistory.Entry entry(Context c, String pkg, String title, String sub) {
        for (WatchHistory.Entry e : WatchHistory.load(c)) if (e.pkg.equals(pkg) && e.title.equals(title) && e.sub.equals(sub)) return e;
        return null;
    }

    private static Bitmap pic(Context c, String path, String size, int px) {
        if (path == null || path.isEmpty() || "null".equals(path)) return null;
        return ScoresTicker.cachedLogo(c, path.startsWith("http") ? path : IMG + size + path, px);
    }

    private static String now() {
        return WatchRules.clock(minuteNow());
    }

    private static int minuteNow() {
        LocalTime t = LocalTime.now();
        return t.getHour() * 60 + t.getMinute();
    }

    private static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(p);
        }
        return b.toString();
    }

    private static String firstGenre(JSONObject d) {
        JSONArray g = d.optJSONArray("genres");
        JSONObject first = g == null ? null : g.optJSONObject(0);
        return first == null ? "" : first.optString("name", "");
    }

    /** A film's cover: TMDB's details with its credits, videos, keywords and picks, and its series' next film. */
    static Content film(Context c, String key, int id, String fallback) throws Exception {
        JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + id
                + "?language=en-GB&append_to_response=credits,videos,keywords,recommendations&api_key=" + key));
        Content k = new Content();
        k.id = id;
        k.tv = false;
        k.name = d.optString("title", fallback);
        k.genre = firstGenre(d);
        String date = d.optString("release_date", "");
        k.eyebrow = "THAT'S A WRAP";
        k.meta = join(date.length() >= 4 ? date.substring(0, 4) : "", k.genre, runtime(d.optInt("runtime")), "finished " + now());
        k.rateLabel = "Your rating";
        k.askedKey = Ratings.key(false, id);
        k.backdrop = pic(c, d.optString("backdrop_path", ""), "w1280", 640);
        k.stinger = stinger(d.optJSONObject("keywords"));
        Context a = c.getApplicationContext();

        // The series' next film: Play it, or Remind me when it is still to come out.
        JSONObject next = null;
        JSONObject col = d.optJSONObject("belongs_to_collection");
        if (col != null && col.optInt("id") > 0) {
            try {
                JSONObject cj = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/collection/" + col.optInt("id")
                        + "?language=en-GB&api_key=" + key));
                next = nextInCollection(cj.optJSONArray("parts"), id);
            } catch (Exception ignored) {
                // no series this time
            }
        }
        List<Item> upNext = new ArrayList<>();
        if (next != null) {
            JSONObject n = next;
            String nd = n.optString("release_date", "");
            boolean out = !nd.isEmpty() && nd.compareTo(LocalDate.now().toString()) <= 0;
            Item b = new Item();
            if (out) {
                b.title = "Next: " + n.optString("title");
                b.line = join(nd.substring(0, 4), "the next film");
                b.action = () -> openTitle(a, n.optString("title"), false);
            } else {
                b.title = "Remind me: " + n.optString("title");
                b.line = nd.isEmpty() ? "The next film · no date yet" : "The next film · out " + longDay(nd);
                b.action = () -> remind(a, b, n.optInt("id"), false, n.optString("title"), n.optString("poster_path"), n.optString("backdrop_path"));
            }
            k.buttons.add(b);
            Item t = new Item();
            t.title = n.optString("title");
            t.line = out ? "The next film" : "The next film · " + (nd.isEmpty() ? "coming" : longDay(nd));
            t.pic = pic(c, n.optString("backdrop_path", ""), "w780", 300);
            t.action = b.action;
            upNext.add(t);
        }
        Item credits = new Item();
        credits.title = "Watch the credits";
        credits.action = CreditsCover::close;
        k.buttons.add(credits);

        JSONArray recs = d.optJSONObject("recommendations") == null ? null : d.optJSONObject("recommendations").optJSONArray("results");
        for (int i = 0; recs != null && i < recs.length() && upNext.size() < 5; i++) {
            JSONObject r = recs.optJSONObject(i);
            if (r == null || (next != null && r.optInt("id") == next.optInt("id"))) continue;
            Item t = new Item();
            t.title = r.optString("title");
            String rd = r.optString("release_date", "");
            t.line = join(rd.length() >= 4 ? rd.substring(0, 4) : "", "like this one");
            t.pic = pic(c, r.optString("backdrop_path", ""), "w780", 300);
            String name = t.title;
            t.action = () -> openTitle(a, name, false);
            upNext.add(t);
        }
        tab(k, "Up next", upNext);
        tab(k, "Cast & crew", cast(c, d.optJSONObject("credits"), true));
        tab(k, "Extras", videos(c, a, d.optJSONObject("videos")));
        return k;
    }

    /** An episode's cover: the series, the next episode, the rest of the season; a season's last one, its wrap. */
    static Content series(Context c, String key, int id, int season, int episode, String fallback) throws Exception {
        JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id
                + "?language=en-GB&append_to_response=credits,videos,recommendations&api_key=" + key));
        JSONArray eps = null;
        if (season > 0) {
            try {
                eps = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id + "/season/" + season
                        + "?language=en-GB&api_key=" + key)).optJSONArray("episodes");
            } catch (Exception ignored) {
                // the season's list not read: the series alone
            }
        }
        Content k = new Content();
        k.id = id;
        k.tv = true;
        k.name = d.optString("name", fallback);
        k.genre = firstGenre(d);
        k.backdrop = pic(c, d.optString("backdrop_path", ""), "w1280", 640);
        Context a = c.getApplicationContext();
        int total = eps == null ? 0 : eps.length();
        boolean finale = total > 0 && episode >= total;
        JSONObject ep = episode > 0 && eps != null && episode <= total ? eps.optJSONObject(episode - 1) : null;
        JSONObject nextEp = null;

        if (finale) {
            k.eyebrow = "SEASON " + season + " COMPLETE";
            k.meta = join(total + " episodes", k.genre, "finished " + now());
            k.rateLabel = "Rate season " + season;
            k.askedKey = Ratings.key(true, id) + ":s" + season;
            JSONObject nextAir = d.optJSONObject("next_episode_to_air");
            String status = d.optString("status", "");
            if (nextAir != null && nextAir.optInt("season_number") > season || "Returning Series".equals(status)) {
                Item b = new Item();
                b.title = "Remind me: season " + (season + 1);
                String air = nextAir != null && nextAir.optInt("season_number") > season ? nextAir.optString("air_date", "") : "";
                b.line = air.isEmpty() ? "We'll tell you when it's coming" : "Season " + (season + 1) + " · from " + longDay(air);
                String name = k.name;
                b.action = () -> remind(a, b, id, true, name, d.optString("poster_path"), d.optString("backdrop_path"));
                k.buttons.add(b);
            }
        } else {
            k.eyebrow = season > 0 && episode > 0 ? "S" + season + " · E" + episode + " DONE" : "THAT'S A WRAP";
            k.meta = join(ep == null ? "" : "E" + episode + " · " + ep.optString("name", ""), "finished " + now());
            k.rateLabel = "Rate the series";
            k.askedKey = Ratings.key(true, id);
            nextEp = eps != null && episode > 0 && episode < total ? eps.optJSONObject(episode) : null;
            // The next one known: the next-episode page.
            if (nextEp != null) nextEpisode(c, a, k, d, nextEp, season, episode, total);
        }
        Item credits = new Item();
        credits.title = "Watch the credits";
        credits.action = CreditsCover::close;
        k.buttons.add(credits);

        // The rest of the season: on the next-episode page, the ones after the next.
        List<Item> rest = new ArrayList<>();
        for (int i = k.episodePage ? episode + 1 : episode; !finale && eps != null && i < total && rest.size() < 5; i++) {
            JSONObject n = eps.optJSONObject(i);
            if (n == null) continue;
            Item t = new Item();
            int number = n.optInt("episode_number", i + 1);
            t.title = "E" + number + " · " + n.optString("name", "");
            t.line = join(runtime(n.optInt("runtime")), n.optString("air_date", "").isEmpty() ? "" : "aired " + n.optString("air_date"));
            t.pic = pic(c, n.optString("still_path", ""), "w300", 300);
            String name = k.name, epName = n.optString("name", "");
            Bitmap still = t.pic;
            boolean next = number == episode + 1;
            t.action = () -> playEpisode(a, name, season, number, epName, still, next);
            rest.add(t);
        }
        List<Item> like = new ArrayList<>();
        JSONArray recs = d.optJSONObject("recommendations") == null ? null : d.optJSONObject("recommendations").optJSONArray("results");
        for (int i = 0; recs != null && i < recs.length() && like.size() < 5; i++) {
            JSONObject r = recs.optJSONObject(i);
            if (r == null) continue;
            Item t = new Item();
            t.title = r.optString("name");
            String fd = r.optString("first_air_date", "");
            t.line = join(fd.length() >= 4 ? fd.substring(0, 4) : "", "like this one");
            t.pic = pic(c, r.optString("backdrop_path", ""), "w780", 300);
            String name = t.title;
            t.action = () -> openTitle(a, name, true);
            like.add(t);
        }
        if (finale) {
            tab(k, "If you liked " + k.name, like);
            tab(k, "Cast", cast(c, d.optJSONObject("credits"), false));
            tab(k, "Extras", videos(c, a, d.optJSONObject("videos")));
        } else if (k.episodePage) {
            tab(k, "Rest of season " + season, rest);
            tab(k, "In E" + k.nextNumber, castWith(c, nextEp.optJSONArray("guest_stars"), d.optJSONObject("credits")));
            tab(k, "Like this", like);
        } else {
            tab(k, "Season " + season, rest);
            tab(k, "Cast", cast(c, d.optJSONObject("credits"), false));
            tab(k, "Like this", like);
        }
        return k;
    }

    /**
     * The next-episode page: the next one's still across the top, its name, story and length, the season so far, and
     * Play it (its line saying when it would end, and how that sits with bed); Remind me when it is still to air.
     */
    private static void nextEpisode(Context c, Context a, Content k, JSONObject d, JSONObject n, int season, int episode, int total) {
        k.episodePage = true;
        k.show = k.name;
        k.season = season;
        k.episode = episode;
        k.total = total;
        int number = n.optInt("episode_number", episode + 1);
        k.nextNumber = number;
        String epName = n.optString("name", "");
        k.name = epName.isEmpty() ? "Episode " + number : epName;
        k.synopsis = n.optString("overview", "").trim();
        String air = n.optString("air_date", "");
        k.nextOut = isOut(air, LocalDate.now().toString());
        JSONArray usual = d.optJSONArray("episode_run_time");
        k.nextRuntime = n.optInt("runtime") > 0 ? n.optInt("runtime") : usual != null ? usual.optInt(0) : 0;
        double vote = n.optDouble("vote_average", 0);
        k.meta = join(k.show, runtime(k.nextRuntime), n.optInt("vote_count") >= 10 && vote > 0 ? String.format(Locale.UK, "★ %.1f", vote) : "");
        k.eyebrow = "NEXT EPISODE · S" + season + " E" + number + (k.nextOut || air.isEmpty() ? "" : " · " + dayOf(air).toUpperCase(Locale.UK));
        Bitmap big = pic(c, n.optString("still_path", ""), "original", 640);
        if (big != null) k.backdrop = big;
        Bitmap small = pic(c, n.optString("still_path", ""), "w300", 300);
        String show = k.show, poster = d.optString("poster_path"), backdrop = d.optString("backdrop_path");
        Item b = new Item();
        if (k.nextOut) {
            b.title = "Play E" + number;
            b.action = () -> playEpisode(a, show, season, number, epName, small, true);
            k.play = b;
        } else {
            b.title = "Remind me: E" + number;
            b.line = air.isEmpty() ? "No date yet" : "Out " + dayOf(air);
            b.action = () -> remind(a, b, k.id, true, show, poster, backdrop);
        }
        k.buttons.add(b);
    }

    private static void tab(Content k, String name, List<Item> items) {
        if (items.isEmpty()) return;
        k.tabNames.add(name);
        k.tabItems.add(items);
    }

    private static List<Item> cast(Context c, JSONObject credits, boolean director) {
        List<Item> out = new ArrayList<>();
        JSONArray cast = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; cast != null && i < cast.length() && out.size() < (director ? 5 : 6); i++) {
            JSONObject p = cast.optJSONObject(i);
            if (p != null) out.add(person(c, p));
        }
        JSONArray crew = credits == null ? null : credits.optJSONArray("crew");
        for (int i = 0; director && crew != null && i < crew.length(); i++) {
            JSONObject p = crew.optJSONObject(i);
            if (p == null || !"Director".equals(p.optString("job"))) continue;
            Item t = new Item();
            t.title = p.optString("name");
            t.line = "Director";
            t.pic = pic(c, p.optString("profile_path", ""), "w185", 160);
            t.round = true;
            out.add(t);
            break;
        }
        return out;
    }

    private static Item person(Context c, JSONObject p) {
        Item t = new Item();
        t.title = p.optString("name");
        t.line = p.optString("character", "");
        t.pic = pic(c, p.optString("profile_path", ""), "w185", 160);
        t.round = true;
        return t;
    }

    /** Who is in the next episode: its guests first, then the series' own cast (six in all). */
    private static List<Item> castWith(Context c, JSONArray guests, JSONObject credits) {
        List<Item> out = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = 0; guests != null && i < guests.length() && out.size() < 3; i++) {
            JSONObject p = guests.optJSONObject(i);
            if (p == null || p.optString("name").isEmpty()) continue;
            out.add(person(c, p));
            names.add(p.optString("name"));
        }
        JSONArray cast = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; cast != null && i < cast.length() && out.size() < 6; i++) {
            JSONObject p = cast.optJSONObject(i);
            if (p != null && !names.contains(p.optString("name"))) out.add(person(c, p));
        }
        return out;
    }

    private static List<Item> videos(Context c, Context a, JSONObject videos) {
        List<Item> out = new ArrayList<>();
        for (JSONObject v : extras(videos == null ? null : videos.optJSONArray("results"), 3)) {
            Item t = new Item();
            t.title = v.optString("name");
            t.line = v.optString("type") + (v.optBoolean("official") ? " · official" : "");
            String yt = v.optString("key");
            t.pic = pic(c, "https://img.youtube.com/vi/" + yt + "/hqdefault.jpg", "", 300);
            t.action = () -> youTube(a, yt);
            out.add(t);
        }
        return out;
    }

    // ---- what its buttons do -----------------------------------------------------------------------------------------

    /** A title from the shelf: opened in Debrid Stream (as Home's posters are), the cover away. */
    private static void openTitle(Context c, String title, boolean tv) {
        close();
        if (ReleaseAlerts.installed(c, ReleaseAlerts.DEBRID)) AppSearchService.search(c, ReleaseAlerts.DEBRID, title, tv);
    }

    /**
     * An episode: in Debrid Stream the helper opens that very one (as Surprise me does); in another app, the next one
     * is asked of the app (its own next), and the cover goes so the app's own up next plays.
     */
    private static void playEpisode(Context c, String show, int season, int episode, String name, Bitmap still, boolean next) {
        Content k = shown;
        close();
        if (k != null && ReleaseAlerts.DEBRID.equals(k.pkg)) {
            AppSearchService.wantEpisode(season, episode, name, still);
            AppSearchService.search(c, ReleaseAlerts.DEBRID, show, true);
        } else if (next && k != null && k.mc != null) {
            try {
                k.mc.getTransportControls().skipToNext();
            } catch (RuntimeException ignored) {
                // the app plays on by itself
            }
        }
    }

    /** On My List, which tells the TV when it arrives; the button says so. */
    private static void remind(Context c, Item b, int id, boolean tv, String title, String poster, String backdrop) {
        if (!MyList.has(c, id, tv)) {
            PagesV2.Title t = new PagesV2.Title();
            t.id = id;
            t.tv = tv;
            t.title = title;
            t.poster = poster == null || poster.isEmpty() || "null".equals(poster) ? "" : IMG + "w342" + poster;
            t.backdrop = backdrop == null || backdrop.isEmpty() || "null".equals(backdrop) ? "" : IMG + "w780" + backdrop;
            MyList.toggle(c, t, false);
        }
        b.title = "On your list";
        b.line = "We'll tell you when it arrives";
        refreshButtons();
    }

    /** A video in YouTube on the TV (its own app; any app that plays YouTube links when it has none). */
    private static void youTube(Context c, String id) {
        close();
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(new Intent(i).setPackage("com.google.android.youtube.tv"));
        } catch (RuntimeException e) {
            try {
                c.startActivity(i);
            } catch (RuntimeException ignored) {
                // nothing here plays YouTube
            }
        }
    }

    /** adb only, through GoalWatchService: --es test_credits film:438631 or tv:1399:2:7 (a TMDB id; season, episode). */
    static void test(Context c, String spec) {
        Context a = c.getApplicationContext();
        io().execute(() -> {
            try {
                String key = Config.themeString(a, "tmdb_key", "").trim();
                String[] f = spec.split(":");
                Content k = "tv".equals(f[0]) ? series(a, key, Integer.parseInt(f[1]), f.length > 2 ? Integer.parseInt(f[2]) : 1,
                        f.length > 3 ? Integer.parseInt(f[3]) : 1, "") : film(a, key, Integer.parseInt(f[1]), "");
                k.test = true;
                main().post(() -> show(a, k));
            } catch (Exception e) {
                AlertLog.note(a, "credits cover test: " + e);
            }
        });
    }

    // ---- the screen ---------------------------------------------------------------------------------------------------

    private static Context app;
    private static Content shown;
    private static FrameLayout window;
    private static TextView creditsLine, ratedWord;
    private static LinearLayout buttonRow, tabRow, shelf;
    private static final List<StarView> stars = new ArrayList<>();
    private static int starsGiven, tabAt, playMinute = -1;
    private static long upAt;
    private static float u = 2f, fontScale = 1f;

    /** One place the remote can be: its frame, its ring, what OK does there, and what being there does. */
    private static final class Cell {
        FrameLayout v;
        /** Drawn over the frame (its foreground) when the remote is here: a child sized to the frame would stretch it. */
        android.graphics.drawable.Drawable ring;
        Runnable ok, onFocus;
    }

    /** Rows: the stars, the buttons, the tabs, the shelf. */
    private static final List<List<Cell>> rows = new ArrayList<>();
    private static int row = 1, col;

    static boolean isShowing() {
        return window != null;
    }

    private static int px(float v) {
        return Math.round(v * u);
    }

    private static TextView text(Context c, String s, float size, int colour, int weight) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u * fontScale);
        v.setTextColor(colour);
        v.setTypeface(ScoreCard.outfit(c, weight));
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static GradientDrawable round(int fill, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius * u);
        return g;
    }

    private static GradientDrawable ringOf(int colour, float radius, boolean oval) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(oval ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
        g.setStroke(Math.max(2, px(2.5f)), colour);
        if (!oval) g.setCornerRadius(radius * u);
        return g;
    }

    /** A place for the remote: the content inside a frame with room round it for the ring. */
    private static Cell cell(Context c, View content, int w, int h, float radius, boolean oval, Runnable ok) {
        Cell cl = new Cell();
        FrameLayout f = new FrameLayout(c);
        int pad = px(5);
        f.setPadding(pad, pad, pad, pad);
        f.addView(content, new FrameLayout.LayoutParams(w, h));
        cl.v = f;
        cl.ring = ringOf(WHITE, radius + 4, oval);
        cl.ok = ok;
        return cl;
    }

    /** A star, drawn: filled gold when given, an outline when not. */
    private static final class StarView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        boolean lit;

        StarView(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = Math.min(w, h) * 0.42f, ir = r * 0.45f, cx = w / 2f, cy = h / 2f + r * 0.06f;
            path.reset();
            for (int i = 0; i < 10; i++) {
                double a = Math.PI / 2 + i * Math.PI / 5;
                float rr = i % 2 == 0 ? r : ir;
                float x = cx + (float) (rr * Math.cos(a)), y = cy - (float) (rr * Math.sin(a));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            p.setStyle(lit ? Paint.Style.FILL : Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, r * 0.12f));
            p.setColor(lit ? GOLD : DIM);
            cv.drawPath(path, p);
        }
    }

    /** The season as a row of small bars: the ones watched white, the next one gold, the rest faint. */
    private static final class SeasonBar extends View {
        /** Longer seasons are said in words instead. */
        static final int MOST = 26;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private final int total, done;

        SeasonBar(Context c, int total, int done) {
            super(c);
            this.total = Math.max(1, total);
            this.done = done;
        }

        /** 20 units a bar, narrower for a long season (the row at most 250). */
        int width() {
            float each = Math.min(20f, (250f - (total - 1) * 4f) / total);
            return px(total * each + (total - 1) * 4f);
        }

        @Override protected void onDraw(Canvas cv) {
            float gap = px(4), each = (getWidth() - gap * (total - 1)) / total, h = getHeight(), rad = h / 2f;
            for (int i = 1; i <= total; i++) {
                float x = (i - 1) * (each + gap);
                r.set(x, 0, x + each, h);
                p.setStyle(Paint.Style.FILL);
                p.setColor(i <= done ? WHITE : i == done + 1 ? 0x59F2C14E : 0x1FFFFFFF);
                cv.drawRoundRect(r, rad, rad, p);
                if (i == done + 1) {
                    float sw = Math.max(1f, px(1));
                    r.inset(sw / 2f, sw / 2f);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(sw);
                    p.setColor(GOLD);
                    cv.drawRoundRect(r, rad, rad, p);
                }
            }
        }
    }

    /**
     * A picture cut to a rounded card or a circle by its own shader, centre-cropped. The window is drawn by the CPU
     * (GoalWatchService.SoftwareWindow), which does not clip to outlines, so the corners are drawn, not clipped.
     */
    private static final class Pic extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Matrix m = new android.graphics.Matrix();
        private final android.graphics.RectF r = new android.graphics.RectF();
        private final Bitmap bmp;
        private final boolean circle;
        private final float radius;

        Pic(Context c, Bitmap bmp, boolean circle, float radius) {
            super(c);
            this.bmp = bmp;
            this.circle = circle;
            this.radius = radius;
            fill.setColor(0xFF1C2333);
            if (bmp != null) p.setShader(new android.graphics.BitmapShader(bmp, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            r.set(0, 0, w, h);
            shape(cv, fill);
            if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) return;
            float s = Math.max(w / bmp.getWidth(), h / bmp.getHeight());
            m.setScale(s, s);
            m.postTranslate((w - bmp.getWidth() * s) / 2f, (h - bmp.getHeight() * s) / 2f);
            p.getShader().setLocalMatrix(m);
            shape(cv, p);
        }

        private void shape(Canvas cv, Paint paint) {
            if (circle) cv.drawOval(r, paint); else cv.drawRoundRect(r, radius, radius, paint);
        }
    }

    /** A title as large as fits on one line in w (from max down to min; past that it is cut short). */
    private static void fit(TextView v, String s, int w, float max, float min) {
        Paint m = new Paint(v.getPaint());
        float size = max;
        while (size > min) {
            m.setTextSize(size * u * fontScale);
            if (m.measureText(s) <= w) break;
            size -= 2;
        }
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u * fontScale);
    }

    static void show(Context c, Content k) {
        if (window != null) close();
        if (!k.test) {
            if (GoalWatchService.homeInFront || HalfTimeBreak.isShowing() || QuickReply.isOpen()) return;
            boolean home = NotifyPrefs.homeNow();
            if (!NotifyPrefs.allowed(c, KIND, home)) {
                NotifyPrefs.held(c, KIND, home, null, "credits cover: " + k.name);
                return;
            }
        }
        app = c.getApplicationContext();
        shown = k;
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        u = HalfTimeBreak.unit(dm.widthPixels, dm.heightPixels);
        fontScale = Math.max(0.85f, Math.min(1.3f, app.getResources().getConfiguration().fontScale));
        Context sw = new GoalWatchService.SoftwareWindow(app);
        FrameLayout win = new FrameLayout(sw);
        win.setBackgroundColor(BG);

        // The backdrop (on the next-episode page, that episode's still, larger), top right, faded into the page at its
        // left and foot.
        if (k.backdrop != null) {
            boolean ep = k.episodePage;
            int bw = ep ? 640 : 600, bh = ep ? 360 : 338, fade = ep ? 300 : 240, foot = ep ? 150 : 110;
            ImageView bd = new ImageView(sw);
            bd.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bd.setImageBitmap(k.backdrop);
            win.addView(bd, new FrameLayout.LayoutParams(px(bw), px(bh), Gravity.TOP | Gravity.END));
            View left = new View(sw);
            left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{BG, 0x00070B14}));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(fade), px(bh), Gravity.TOP | Gravity.END);
            lp.rightMargin = px(bw - fade);
            win.addView(left, lp);
            View fv = new View(sw);
            fv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00070B14, BG}));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(px(bw), px(foot), Gravity.TOP | Gravity.END);
            fp.topMargin = px(bh - foot);
            win.addView(fv, fp);
        }

        // The credits still rolling behind, and a scene after them, in the top right.
        LinearLayout pills = new LinearLayout(sw);
        pills.setOrientation(LinearLayout.VERTICAL);
        pills.setGravity(Gravity.END);
        creditsLine = text(sw, "Credits rolling behind", 9, 0xFFD7DAE2, 600);
        creditsLine.setPadding(px(10), px(6), px(12), px(6));
        creditsLine.setBackground(round(0xD904070D, 999));
        pills.addView(creditsLine);
        if (k.stinger > 0) {
            TextView st = text(sw, k.stinger == 2 ? "★  Scene after the credits · the cover lifts for it" : "★  A scene plays during the credits · Back to watch",
                    9, 0xFFF6DFA0, 600);
            st.setPadding(px(10), px(6), px(12), px(6));
            GradientDrawable g = round(0x29F2C14E, 999);
            g.setStroke(Math.max(1, px(0.8f)), 0x99F2C14E);
            st.setBackground(g);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.topMargin = px(6);
            pills.addView(st, sp);
        }
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pp.topMargin = px(16);
        pp.rightMargin = px(16);
        win.addView(pills, pp);

        // The title, the rating, the buttons.
        LinearLayout colm = new LinearLayout(sw);
        colm.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text(sw, k.eyebrow, 9, GOLD, 800);
        eyebrow.setLetterSpacing(0.2f);
        colm.addView(eyebrow);
        int colW = k.episodePage ? 430 : 470;
        TextView title = text(sw, k.name, 40, WHITE, 800);
        title.setLetterSpacing(-0.02f);
        title.setPadding(0, px(8), 0, 0);
        if (k.episodePage) fit(title, k.name, px(colW), 40, 26);
        colm.addView(title);
        TextView meta = text(sw, k.meta, 11, SOFT, 400);
        meta.setPadding(0, px(8), 0, 0);
        colm.addView(meta);
        if (k.episodePage) {
            // Its story, then the season so far: watched, this one, the rest.
            if (!k.synopsis.isEmpty()) {
                TextView story = text(sw, k.synopsis, 10.5f, 0xFFD7DAE2, 400);
                story.setSingleLine(false);
                story.setMaxLines(3);
                story.setEllipsize(TextUtils.TruncateAt.END);
                story.setLineSpacing(px(3), 1f);
                story.setPadding(0, px(10), 0, 0);
                story.setShadowLayer(px(6), 0, 0, 0xCC000000);
                colm.addView(story, new LinearLayout.LayoutParams(px(410), -2));
            }
            LinearLayout so = new LinearLayout(sw);
            so.setGravity(Gravity.CENTER_VERTICAL);
            so.setPadding(0, px(12), 0, 0);
            String left = leftLine(k.episode, k.total);
            if (k.total <= SeasonBar.MOST) {
                SeasonBar bar = new SeasonBar(sw, k.total, k.episode);
                so.addView(bar, new LinearLayout.LayoutParams(bar.width(), px(7)));
            } else {
                left = join("E" + k.episode + " of " + k.total, left);
            }
            TextView lt = text(sw, left, 9, SOFT, 600);
            lt.setPadding(k.total <= SeasonBar.MOST ? px(10) : 0, 0, 0, 0);
            so.addView(lt);
            colm.addView(so);
        }

        rows.clear();
        stars.clear();
        starsGiven = Math.max(0, Ratings.get(app, k.tv, k.id) == WatchRules.LOVED ? 5 : Ratings.get(app, k.tv, k.id) == WatchRules.LIKED ? 4
                : Ratings.get(app, k.tv, k.id) == WatchRules.NOT_FOR_ME ? 1 : 0);
        LinearLayout rate = new LinearLayout(sw);
        rate.setGravity(Gravity.CENTER_VERTICAL);
        rate.setPadding(0, px(10), 0, 0);
        TextView rl = text(sw, k.rateLabel, 10, 0xFFD7DAE2, 600);
        rate.addView(rl, new LinearLayout.LayoutParams(px(96), -2));
        List<Cell> starRow = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            StarView sv = new StarView(sw);
            sv.lit = i <= starsGiven;
            stars.add(sv);
            int n = i;
            Cell cl = cell(sw, sv, px(26), px(26), 6, false, () -> rateStars(n));
            starRow.add(cl);
            rate.addView(cl.v);
        }
        ratedWord = text(sw, starsGiven > 0 ? starWord(starsGiven) : "Left and Right, then OK", 9.5f, 0xFF9EA4B4, 400);
        ratedWord.setPadding(px(8), 0, 0, 0);
        rate.addView(ratedWord);
        colm.addView(rate);
        rows.add(starRow);

        buttonRow = new LinearLayout(sw);
        buttonRow.setPadding(0, px(8), 0, 0);
        colm.addView(buttonRow);
        rows.add(new ArrayList<>());
        refreshButtons();

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(px(colW), -2, Gravity.TOP | Gravity.START);
        cp.leftMargin = px(36);
        cp.topMargin = px(34);
        win.addView(colm, cp);

        // The shelf: its tabs, then what the chosen one holds.
        LinearLayout lower = new LinearLayout(sw);
        lower.setOrientation(LinearLayout.VERTICAL);
        tabRow = new LinearLayout(sw);
        lower.addView(tabRow);
        shelf = new LinearLayout(sw);
        LinearLayout.LayoutParams shp = new LinearLayout.LayoutParams(-1, -2);
        shp.topMargin = px(6);
        lower.addView(shelf, shp);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.START);
        lp2.leftMargin = px(31);
        lp2.rightMargin = px(31);
        lp2.topMargin = px(k.episodePage ? 318 : 282);
        win.addView(lower, lp2);
        List<Cell> tabCells = new ArrayList<>();
        for (int i = 0; i < k.tabNames.size(); i++) {
            int t = i;
            LinearLayout tabBox = new LinearLayout(sw);
            tabBox.setOrientation(LinearLayout.VERTICAL);
            // Its own width (a vertical box's default is the full width, which, with the bar under it, would stretch
            // the tab across the row).
            tabBox.addView(text(sw, k.tabNames.get(i), 12, DIM, 700), new LinearLayout.LayoutParams(-2, -2));
            View bar = new View(sw);
            bar.setBackground(round(GOLD, 2));
            bar.setVisibility(View.INVISIBLE);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Math.max(2, px(2.5f)));
            blp.topMargin = px(4);
            tabBox.addView(bar, blp);
            Cell cl = cell(sw, tabBox, -2, -2, 6, false, null);
            cl.onFocus = () -> pickTab(t);
            tabCells.add(cl);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.rightMargin = px(14);
            tabRow.addView(cl.v, tp);
        }
        rows.add(tabCells);
        rows.add(new ArrayList<>());
        tabAt = -1;
        if (!k.tabNames.isEmpty()) pickTab(0);

        TextView hint = text(sw, k.episodePage ? "Arrows move · OK chooses · Back: the credits · it goes by itself when the next one starts"
                : "Arrows move · OK chooses · Back: the credits · it goes by itself when they end", 8.5f, DIM, 400);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        hp.leftMargin = px(36);
        hp.bottomMargin = px(14);
        win.addView(hint, hp);

        WindowManager.LayoutParams wl = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        wl.setTitle("CreditsCover");
        try {
            app.getSystemService(WindowManager.class).addView(win, wl);
        } catch (RuntimeException e) {
            AlertLog.note(app, "the credits cover could not open: " + e);
            shown = null;
            return;
        }
        window = win;
        win.setAlpha(0f);
        win.animate().alpha(1f).setDuration(450).start();
        if (!InputKeyService.running) AppSearchService.keysFor("credits", true);
        upAt = System.currentTimeMillis();
        row = 1;
        col = 0;
        focus();
        main().removeCallbacks(tick);
        main().post(tick);
        Ratings.markAsked(app, k.askedKey);
        AlertLog.note(app, "credits cover up: " + k.name + (k.test ? " (test)" : ""));
    }

    /** The buttons as they now read (Remind me becomes On your list). */
    private static void refreshButtons() {
        Content k = shown;
        if (k == null || buttonRow == null) return;
        Context c = buttonRow.getContext();
        if (k.play != null && app != null) {
            playMinute = minuteNow();
            k.play.line = join(runtime(k.nextRuntime), endsLine(playMinute, k.nextRuntime, FitsTonight.bedMinutes(app)));
        }
        buttonRow.removeAllViews();
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < k.buttons.size(); i++) {
            Item b = k.buttons.get(i);
            boolean primary = i == 0 && k.buttons.size() > 1;
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(px(16), 0, px(16), 0);
            GradientDrawable g = round(primary ? WHITE : 0x0FFFFFFF, 12);
            if (!primary) g.setStroke(Math.max(1, px(0.8f)), 0x29FFFFFF);
            box.setBackground(g);
            box.addView(text(c, b.title, 11.5f, primary ? INK : WHITE, 800));
            if (!b.line.isEmpty()) {
                TextView l = text(c, b.line, 9, primary ? 0xFF4A5060 : SOFT, 400);
                l.setPadding(0, px(3), 0, 0);
                box.addView(l);
            }
            Cell cl = cell(c, box, -2, px(44), 12, false, b.action);
            cells.add(cl);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.rightMargin = px(4);
            buttonRow.addView(cl.v, bp);
        }
        if (rows.size() > 1) rows.set(1, cells);
        if (window != null) focus();
    }

    /** A tab chosen: its name lit, its tiles on the shelf. */
    private static void pickTab(int t) {
        Content k = shown;
        if (k == null || t == tabAt || t < 0 || t >= k.tabItems.size()) return;
        tabAt = t;
        Context c = shelf.getContext();
        List<Cell> tabCells = rows.get(2);
        for (int i = 0; i < tabCells.size(); i++) {
            LinearLayout box = (LinearLayout) tabCells.get(i).v.getChildAt(0);
            ((TextView) box.getChildAt(0)).setTextColor(i == t ? WHITE : DIM);
            box.getChildAt(1).setVisibility(i == t ? View.VISIBLE : View.INVISIBLE);
        }
        shelf.removeAllViews();
        List<Cell> cells = new ArrayList<>();
        for (Item it : k.tabItems.get(t)) {
            LinearLayout one = new LinearLayout(c);
            one.setOrientation(LinearLayout.VERTICAL);
            one.setGravity(it.round ? Gravity.CENTER_HORIZONTAL : Gravity.START);
            Pic iv = new Pic(c, it.pic, it.round, 12 * u);
            int w = it.round ? px(84) : px(160), h = it.round ? px(84) : px(90);
            Cell cl = cell(c, iv, w, h, it.round ? 42 : 12, it.round, it.action);
            one.addView(cl.v, new LinearLayout.LayoutParams(-2, -2));
            TextView name = text(c, it.title, 10.5f, WHITE, 700);
            name.setPadding(px(5), px(4), px(5), 0);
            if (it.round) name.setGravity(Gravity.CENTER);
            one.addView(name, new LinearLayout.LayoutParams(it.round ? px(110) : px(170), -2));
            if (!it.line.isEmpty()) {
                TextView l = text(c, it.line, 9, SOFT, 400);
                l.setPadding(px(5), px(2), px(5), 0);
                if (it.round) l.setGravity(Gravity.CENTER);
                one.addView(l, new LinearLayout.LayoutParams(it.round ? px(110) : px(170), -2));
            }
            cells.add(cl);
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, -2);
            op.rightMargin = px(it.round ? 8 : 6);
            shelf.addView(one, op);
        }
        rows.set(3, cells);
    }

    /** Stars given: drawn, saved as the launcher's rating, said in words. */
    private static void rateStars(int n) {
        Content k = shown;
        if (k == null || app == null) return;
        starsGiven = n;
        for (int i = 0; i < stars.size(); i++) {
            stars.get(i).lit = i < n;
            stars.get(i).invalidate();
        }
        Ratings.set(app, k.tv, k.id, rating(n), k.genre, k.name);
        Ratings.markAsked(app, k.askedKey);
        if (ratedWord != null) ratedWord.setText(starWord(n) + " · saved");
    }

    /** The ring where the remote is. */
    private static void focus() {
        for (List<Cell> r : rows) for (Cell c : r) c.v.setForeground(null);
        if (row < 0 || row >= rows.size() || rows.get(row).isEmpty()) {
            for (int i = 0; i < rows.size(); i++) if (!rows.get(i).isEmpty()) { row = i; break; }
        }
        List<Cell> r = rows.get(row);
        if (r.isEmpty()) return;
        col = Math.max(0, Math.min(col, r.size() - 1));
        Cell c = r.get(col);
        c.v.setForeground(c.ring);
        if (c.onFocus != null) c.onFocus.run();
    }

    private static void move(int dr, int dc) {
        if (dr != 0) {
            int r = row + dr;
            while (r >= 0 && r < rows.size() && rows.get(r).isEmpty()) r += dr;
            if (r < 0 || r >= rows.size()) return;
            // The tabs row: onto the tab that is chosen.
            if (r == 2) col = Math.max(0, tabAt);
            else if (r == 1 || r == 3) col = 0;
            row = r;
        } else {
            col += dc;
        }
        focus();
    }

    /**
     * The remote while it is up: the arrows move, OK chooses, Back goes back to the credits; the rest (the volume)
     * go on to the TV.
     */
    static boolean key(KeyEvent e) {
        if (window == null) return false;
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN, up = e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (up) main().post(CreditsCover::close);
            return true;
        }
        if (QuietHold.isOk(code)) {
            if (up) main().post(() -> {
                if (window == null || row >= rows.size() || rows.get(row).isEmpty()) return;
                Runnable ok = rows.get(row).get(Math.min(col, rows.get(row).size() - 1)).ok;
                if (ok != null) ok.run();
            });
            return true;
        }
        int dr = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        int dc = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        if (dr != 0 || dc != 0) {
            if (down) main().post(() -> move(dr, dc));
            return true;
        }
        return false;
    }

    /** Each second: the credits' time left; away when they end, the next episode starts, or Home is pressed. */
    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            Content k = shown;
            if (window == null || k == null) return;
            if (!k.test && GoalWatchService.homeInFront) { close(); return; }
            if (System.currentTimeMillis() - upAt > (k.test ? 120_000L : MAX_MS)) { close(); return; }
            if (k.mc != null) {
                PlaybackState st;
                MediaMetadata md;
                try {
                    st = k.mc.getPlaybackState();
                    md = k.mc.getMetadata();
                } catch (RuntimeException ex) {
                    close();
                    return;
                }
                if (st == null || md == null) { close(); return; }
                int s = st.getState();
                if (s == PlaybackState.STATE_STOPPED || s == PlaybackState.STATE_NONE || s == PlaybackState.STATE_ERROR) { close(); return; }
                if (!(WatchCards.title(md) + "|" + WatchCards.sub(md)).equals(k.item)) { close(); return; }
                long pos = WatchCards.position(st);
                if (lifts(k.dur, pos, k.stinger)) { close(); return; }
                creditsLine.setText("●  Credits rolling behind · " + clock(k.dur - pos) + " left");
            } else {
                creditsLine.setText("●  Credits rolling behind");
            }
            // When the next episode would end, kept to the minute.
            if (k.play != null && minuteNow() != playMinute) refreshButtons();
            main().postDelayed(this, 1000L);
        }
    };

    /** The cover away (Back, Watch the credits, their end, Home); the credits as they were behind it. */
    static void close() {
        main().removeCallbacks(tick);
        FrameLayout v = window;
        window = null;
        shown = null;
        rows.clear();
        stars.clear();
        if (v == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("credits", false);
        if (app != null) AlertLog.note(app, "credits cover away");
        try {
            v.getContext().getSystemService(WindowManager.class).removeView(v);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }
}
