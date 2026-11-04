package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pop-ups when something new comes out (theme "release_alerts", on unless set false): a new episode of a series
 * watched on this TV in the last two months (Your shows), and a new film arriving on the TV's own streaming apps
 * (TMDB's UK digital and TV releases on those services, the popular ones, two a day at most).
 * For a title on one of the TV's streaming apps the card says "Released on Debrid Stream" and OK opens Debrid
 * Stream's page for it, as Movies and TV shows do. A new episode of a series on none of the TV's apps says only that
 * it is out; OK opens the store on the TV that sells it, when there is one. Black card, white writing; each title
 * once. Checked every two hours while the alerts service runs, between 08:00 and 23:30.
 */
final class ReleaseAlerts {
    private ReleaseAlerts() { }

    static final String DEBRID = "com.debridstream.tv";
    private static final int RED = 0xFFE8112D;

    static boolean on(Context c) {
        return Config.themeFlag(c, "release_alerts", true);
    }

    /** Off the main thread. force (adb's test): any hour, and the newest even if shown before. */
    static void check(Context c, boolean force) {
        String key = Config.themeString(c, "tmdb_key", "").trim();
        if (key.isEmpty() || !force && !on(c)) return;
        int now = LocalTime.now().toSecondOfDay() / 60;
        if (!force && (now < 8 * 60 || now > 23 * 60 + 30)) return;
        SharedPreferences p = c.getSharedPreferences("releases", Context.MODE_PRIVATE);
        Set<String> told = new HashSet<>(p.getStringSet("told", new HashSet<>()));
        List<ScoreCard.Data> cards = new ArrayList<>();
        try {
            episodes(c, key, told, force, cards);
        } catch (Exception ignored) {
            // the episodes left for next time
        }
        try {
            films(c, key, told, force, cards);
        } catch (Exception ignored) {
            // the films left for next time
        }
        try {
            fromList(c, key, cards);
        } catch (Exception ignored) {
            // My list left for next time
        }
        if (told.size() > 300) told.clear();
        p.edit().putStringSet("told", told).apply();
        ScoreCard.Data month = monthCard(c, p, force);
        if (month != null) cards.add(month);
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        for (ScoreCard.Data d : cards) main.post(() -> ScoreCard.show(c, d));
    }

    /**
     * On the first three days of a month, once, from 10:00: "Your October on TV" (its hours and top show), OK opening
     * Your viewing on last month's tab.
     */
    private static ScoreCard.Data monthCard(Context c, SharedPreferences p, boolean force) {
        LocalDate today = LocalDate.now();
        int hour = LocalTime.now().getHour();
        String month = today.minusMonths(1).withDayOfMonth(1).toString().substring(0, 7);
        if (!force && (today.getDayOfMonth() > 3 || hour < 10 || hour > 22 || month.equals(p.getString("month_told", "")))) return null;
        int secs = 0, best = 0;
        String top = null;
        java.util.Map<String, Integer> shows = new java.util.HashMap<>();
        for (LocalDate d = today.minusMonths(1).withDayOfMonth(1); d.isBefore(today.withDayOfMonth(1)); d = d.plusDays(1)) {
            JSONObject day = ViewingLog.day(c, d);
            secs += ViewingPanel.total(day);
            JSONObject t = day.optJSONObject("t");
            for (java.util.Iterator<String> it = t == null ? null : t.keys(); it != null && it.hasNext(); ) {
                String k = it.next();
                if (!k.startsWith("tv:") && !k.startsWith("mv:")) continue;
                JSONObject one = t.optJSONObject(k);
                int s = shows.getOrDefault(k, 0) + one.optInt("s");
                shows.put(k, s);
                if (s > best) { best = s; top = one.optString("n"); }
            }
        }
        if (secs < 3600 && !force) return null;
        p.edit().putString("month_told", month).apply();
        String name = ViewingPanel.monthName(today.minusMonths(1));
        ScoreCard.Data d = new ScoreCard.Data();
        d.dark = true;
        d.kind = "release";
        d.notify = "recap";
        d.band = 0xFF141519;
        d.bandDeep = 0xFF000000;
        d.labelColour = 0xFFFFD04D;
        d.label = "YOUR " + name.toUpperCase(java.util.Locale.UK);
        d.headline = Math.round(secs / 3600f) + " hours on the TV" + (top != null ? " · top: " + top : "");
        d.line = "Your month on TV is ready";
        d.okWords = "to see it";
        d.channel = "Your viewing";
        d.watch = () -> c.startActivity(new android.content.Intent(c, MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("viewing_tab", 1));
        return d;
    }

    /** New episodes, out today or yesterday, of the series watched here, that have not been watched yet. */
    private static void episodes(Context c, String key, Set<String> told, boolean force, List<ScoreCard.Data> out) {
        long since = System.currentTimeMillis() - 60L * 24 * 3600_000L;
        Set<Integer> seen = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (WatchHistory.Entry e : WatchHistory.load(c)) {
            if (!e.tv || e.tmdbId <= 0 || e.at < since || !seen.add(e.tmdbId)) continue;
            // Its pop-up turned off on TV shows' Coming up (OK held on it).
            if (ComingUp.muted(c, e.tmdbId, true)) continue;
            JSONObject d = YourShows.details(key, e.tmdbId);
            JSONObject last = d == null ? null : d.optJSONObject("last_episode_to_air");
            if (last == null) continue;
            LocalDate aired;
            try {
                aired = LocalDate.parse(last.optString("air_date"));
            } catch (Exception x) {
                continue;
            }
            if (aired.isAfter(today) || aired.isBefore(today.minusDays(force ? 30 : 1))) continue;
            int s = last.optInt("season_number"), ep = last.optInt("episode_number");
            // Already watched (that one, or one after it)?
            int[] at = YourShows.position(e);
            if (at != null && (at[0] > s || at[0] == s && at[1] >= ep && (at[1] > ep || e.progress() > 0.5f))) continue;
            String id = "tv:" + e.tmdbId + ":" + s + "x" + ep;
            if (!told.add(id) && !force) continue;
            String name = d.optString("name", e.show != null ? e.show : e.title);
            String epName = last.optString("name", "");
            ScoreCard.Data card = base(c, key, e.tmdbId, true);
            card.label = "NEW EPISODE";
            card.headline = name + " · S" + s + " E" + ep + (epName.isEmpty() || epName.matches("(?i)episode \\d+") ? "" : " “" + epName + "”");
            String still = last.optString("still_path", "");
            card.picture = picture(c, !still.isEmpty() && !"null".equals(still) ? "https://image.tmdb.org/t/p/w300" + still
                    : backdrop(d, "w300"));
            String art = backdrop(d, "w1280");
            JSONObject gb = YourShows.gb(d);
            if (onApps(c, gb)) {
                debrid(c, card, name, true, art);
            } else {
                card.line = aired.equals(today) ? "Out today" : "Out yesterday";
                String store = store(c, gb);
                if (store != null) {
                    card.channel = storeName(store);
                    card.watch = () -> AppSearchService.search(c, store, name, true, null, null);
                }
            }
            out.add(card);
            if (out.size() >= 3) return;
        }
    }

    /** My list: a title that was on none of the TV's apps when kept, now on one of them (two a check at most). */
    private static void fromList(Context c, String key, List<ScoreCard.Data> out) {
        int added = 0;
        for (JSONObject o : MyList.all(c)) {
            if (o.optBoolean("apps") || added >= 2) continue;
            int id = o.optInt("id");
            boolean tv = o.optBoolean("tv");
            if (ComingUp.muted(c, id, tv)) continue;
            JSONObject gb = PagesV2.providersGb(key, id, tv);
            if (!onApps(c, gb)) continue;
            MyList.arrived(c, id, tv);
            String name = o.optString("title");
            ScoreCard.Data card = base(c, key, id, tv);
            card.label = "FROM YOUR LIST";
            // As the mock-up: "Superman is now on Netflix", and when it was kept.
            String service = serviceOn(c, gb);
            card.headline = name + " is now on " + (service != null ? service : "your apps");
            String back = o.optString("backdrop", null);
            card.picture = picture(c, back == null || "null".equals(back) ? null : back.replace("/w1280/", "/w300/"));
            debrid(c, card, name, tv, back);
            long kept = o.optLong("added");
            if (kept > 0) {
                java.time.LocalDate when = java.time.Instant.ofEpochMilli(kept).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                card.line = "Added to your list in " + when.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.UK)
                        + (when.getYear() != LocalDate.now().getYear() ? " " + when.getYear() : "");
            }
            out.add(card);
            added++;
        }
    }

    /** New films on the TV's streaming apps, out today or yesterday: the two most popular. */
    private static void films(Context c, String key, Set<String> told, boolean force, List<ScoreCard.Data> out) throws Exception {
        String providers = providers(c);
        if (providers.isEmpty()) return;
        LocalDate today = LocalDate.now();
        JSONArray r = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/discover/movie?watch_region=GB&region=GB"
                + "&with_watch_monetization_types=flatrate&with_release_type=4|6&with_watch_providers=" + providers
                + "&release_date.gte=" + today.minusDays(force ? 10 : 1) + "&release_date.lte=" + today
                + "&sort_by=popularity.desc&language=en-GB&api_key=" + key)).optJSONArray("results");
        int added = 0;
        for (int i = 0; r != null && i < r.length() && added < 2; i++) {
            JSONObject f = r.getJSONObject(i);
            if (f.optDouble("popularity") < 30 && !force) continue;
            int id = f.optInt("id");
            if (ComingUp.muted(c, id, false)) continue;
            if (!told.add("film:" + id) && !force) continue;
            String name = f.optString("title");
            ScoreCard.Data card = base(c, key, id, false);
            card.label = "NEW FILM";
            String year = f.optString("release_date", "");
            card.headline = name + (year.length() >= 4 ? " (" + year.substring(0, 4) + ")" : "");
            String back = f.optString("backdrop_path", "");
            String art = back.isEmpty() || "null".equals(back) ? null : "https://image.tmdb.org/t/p/w1280" + back;
            card.picture = picture(c, art == null ? null : art.replace("/w1280/", "/w300/"));
            debrid(c, card, name, false, art);
            out.add(card);
            added++;
        }
    }

    /** The card's black ground, the title's own logo in the band, the red label. */
    private static ScoreCard.Data base(Context c, String key, int id, boolean tv) {
        ScoreCard.Data d = new ScoreCard.Data();
        d.dark = true;
        d.kind = "release";
        d.band = 0xFF141519;
        d.bandDeep = 0xFF000000;
        d.labelColour = RED;
        d.wideMark = true;
        try {
            JSONArray logos = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id
                    + "/images?include_image_language=en,null&api_key=" + key)).optJSONArray("logos");
            for (int i = 0; logos != null && i < logos.length(); i++) {
                JSONObject l = logos.getJSONObject(i);
                if (!"en".equals(l.optString("iso_639_1")) || !l.optString("file_path").endsWith(".png")) continue;
                Bitmap b = ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w300" + l.optString("file_path"), 200);
                if (b != null) d.leagueLogo = HomeV2.trim(b);
                break;
            }
        } catch (Exception ignored) {
            // no logo: the band is plain
        }
        return d;
    }

    /** "Released on Debrid Stream": OK opens Debrid Stream's page for the title (its search, through the helper). */
    private static void debrid(Context c, ScoreCard.Data card, String name, boolean tv, String art) {
        card.line = "Released on Debrid Stream";
        if (!installed(c, DEBRID)) return;
        card.channel = "Debrid Stream";
        // Debrid Stream's own mark (the red play button and its name), large enough to read at a glance.
        card.channelLogo = debridLogo(c);
        card.channelLogoScale = 2.4f;
        Bitmap pic = art == null ? null : ScoresTicker.cachedLogo(c, art, 640);
        card.watch = () -> {
            AppSearchService.search(c, DEBRID, name, tv, pic, null);
            GoalWatchService.onAppOpened(c, DEBRID);
        };
    }

    private static Bitmap debridLogo;

    /**
     * Debrid Stream's mark from its own TV banner (white and red on black): the black made see-through, so it sits on
     * the card's dark ground with no box round it, and trimmed to the mark. Null when the app has no banner.
     */
    static synchronized Bitmap debridLogo(Context c) {
        if (debridLogo != null) return debridLogo;
        try {
            android.graphics.drawable.Drawable dr = c.getPackageManager().getApplicationBanner(DEBRID);
            if (dr == null) return null;
            int w = dr.getIntrinsicWidth() > 0 ? dr.getIntrinsicWidth() : 432;
            int hh = dr.getIntrinsicHeight() > 0 ? dr.getIntrinsicHeight() : 243;
            Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            dr.setBounds(0, 0, w, hh);
            dr.draw(cv);
            int[] px = new int[w * hh];
            b.getPixels(px, 0, w, 0, 0, w, hh);
            for (int i = 0; i < px.length; i++) {
                int r = (px[i] >> 16) & 0xFF, g = (px[i] >> 8) & 0xFF, bl = px[i] & 0xFF;
                int a = Math.max(r, Math.max(g, bl));
                // Black to see-through, keeping each colour as it showed over black (the grey of "STREAM" included).
                px[i] = a < 12 ? 0 : (a << 24) | ((r * 255 / a) << 16) | ((g * 255 / a) << 8) | (bl * 255 / a);
            }
            b.setPixels(px, 0, w, 0, 0, w, hh);
            debridLogo = HomeV2.trim(b);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
        return debridLogo;
    }

    private static Bitmap picture(Context c, String url) {
        return url == null ? null : ScoresTicker.cachedLogo(c, url, 300);
    }

    private static String backdrop(JSONObject d, String size) {
        String b = d.optString("backdrop_path", "");
        return b.isEmpty() || "null".equals(b) ? null : "https://image.tmdb.org/t/p/" + size + b;
    }

    /** The TV's streaming services as discover's with_watch_providers takes them ("8|9|337"). */
    static String providers(Context c) {
        StringBuilder b = new StringBuilder();
        for (String id : new String[]{"8", "9", "119", "337", "350", "38", "41", "54", "103", "333", "39", "29", "73", "531", "1899"}) {
            String app = HomeV2.serviceApp(id);
            if (app != null && installed(c, app)) b.append(b.length() > 0 ? "|" : "").append(id);
        }
        return b.toString();
    }

    private static boolean onApps(Context c, JSONObject gb) {
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            JSONObject p = flat.optJSONObject(i);
            String app = p == null ? null : HomeV2.serviceApp(String.valueOf(p.optInt("provider_id")));
            if (app != null && installed(c, app)) return true;
        }
        return false;
    }

    /** The name of the first of the TV's own streaming apps that carries it (TMDB's provider name), or null. */
    private static String serviceOn(Context c, JSONObject gb) {
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            JSONObject p = flat.optJSONObject(i);
            String app = p == null ? null : HomeV2.serviceApp(String.valueOf(p.optInt("provider_id")));
            if (app != null && installed(c, app) && !p.optString("provider_name").isEmpty()) return p.optString("provider_name");
        }
        return null;
    }

    private static String store(Context c, JSONObject gb) {
        String[][] stores = {{"10", "com.amazon.amazonvideo.livingroom"}, {"2", "com.apple.atve.androidtv.appletv"},
                {"192", "com.google.android.youtube.tv"}, {"3", "com.google.android.videos"}};
        for (String kind : new String[]{"rent", "buy"}) {
            JSONArray list = gb == null ? null : gb.optJSONArray(kind);
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject p = list.optJSONObject(i);
                String id = p == null ? "" : String.valueOf(p.optInt("provider_id"));
                for (String[] s : stores) if (s[0].equals(id) && installed(c, s[1])) return s[1];
            }
        }
        return null;
    }

    private static String storeName(String pkg) {
        return pkg.contains("amazon") ? "Prime Video" : pkg.contains("apple") ? "Apple TV" : pkg.contains("youtube") ? "YouTube" : "Google TV";
    }

    static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
