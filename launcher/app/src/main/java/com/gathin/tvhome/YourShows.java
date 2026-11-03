package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TV shows' "Your shows" tab: every series being watched on this TV (from WatchHistory, in any app), each with where
 * it is up to and what is next, from TMDB: a new episode out ("NEW · S4 E1"), episodes still to watch ("3 LEFT", or
 * "DONE BY 23:15" when they all fit before bed), the next one's day ("E7 TOMORROW", "BACK 10 DEC"), or a new season
 * not yet dated. Series finished and ended are left out.
 * OK on a series that is on one of the TV's streaming apps does what the page's other tabs do; on a series on none of
 * them, its store where it can be bought (Prime Video, Apple TV, YouTube), else a line saying it is not on the apps.
 * As the mock-up draws it: each poster's badge, then along its foot how far through the season ("6 of 8 · Disney+")
 * over a yellow bar; over the hero's picture, this week's episodes of these series, day by day (Row.side).
 */
final class YourShows {
    private YourShows() { }

    private static final int RED = 0xFFE8112D, YELLOW = 0xFFFFD04D, NAVY = 0xFF141C92, WHITE = 0xFFFFFFFF, DIM = 0x99000000,
            GREEN = 0xFF3DDC84, GREEN_INK = 0xFF06331B;
    private static final Pattern SE = Pattern.compile("(?i)\\bs(\\d{1,2})\\s*:?\\s*e(\\d{1,3})\\b");
    private static final Pattern SEASON_EPISODE = Pattern.compile("(?i)season\\s*(\\d{1,2}).{0,12}?episode\\s*(\\d{1,3})");
    /** TMDB's series details (with the UK's providers), kept six hours. */
    private static final Map<Integer, Object[]> DETAILS = new HashMap<>();
    private static final long KEEP_MS = 6 * 3600_000L;

    /** Any series watched on this TV yet (TV shows opens on Your shows when Continue Watching is empty). */
    static boolean any(Context c) {
        for (WatchHistory.Entry e : WatchHistory.load(c)) if (e.tv && e.tmdbId > 0) return true;
        return false;
    }

    static PagesV2.Row row(HomeV2 h) {
        List<PagesV2.Title> items = new ArrayList<>();
        List<Object[]> ranked = new ArrayList<>(), week = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        long since = System.currentTimeMillis() - 180L * 24 * 3600_000L;
        for (WatchHistory.Entry e : WatchHistory.load(h.a)) {
            if (!e.tv || e.tmdbId <= 0 || e.at < since || !seen.add(e.tmdbId)) continue;
            if (seen.size() > 15) break;
            try {
                Object[] r = series(h, e, week);
                if (r != null) ranked.add(r);
            } catch (Exception ignored) {
                // that series left out this time
            }
        }
        // New episodes first, then those with episodes to go (as last watched), then the next to come, soonest first.
        ranked.sort((x, y) -> {
            int a = (Integer) x[1], b = (Integer) y[1];
            if (a != b) return Integer.compare(a, b);
            return Long.compare((Long) x[2], (Long) y[2]);
        });
        for (Object[] r : ranked) items.add((PagesV2.Title) r[0]);
        PagesV2.Row row = PagesV2.row(null, null, false, items);
        row.empty = "Series you watch on this TV show here, with how far you are and when the next episode is out.";
        if (!week.isEmpty()) row.side = thisWeek(week);
        return row;
    }

    /**
     * "THIS WEEK IN YOUR SHOWS": Monday to Sunday, today ringed in white, each day with the episode of one of these
     * series out that day (its poster, the episode, the service), as the mock-up has it over the hero's picture.
     * Entries: {day, poster URL, "E7", TMDB provider id, the provider's name}.
     */
    private static java.util.function.BiFunction<HomeV2, PagesV2.Title, View> thisWeek(List<Object[]> week) {
        week.sort((x, y) -> ((LocalDate) x[0]).compareTo((LocalDate) y[0]));
        View[] made = new View[1];
        return (h, t) -> {
            if (made[0] != null) return made[0];
            Typeface bold = Typeface.create(h.semi, Typeface.BOLD), heavy = h.a.getResources().getFont(R.font.outfit_extrabold);
            LocalDate today = LocalDate.now(), monday = today.with(DayOfWeek.MONDAY);
            FrameLayout layer = new FrameLayout(h.a);
            LinearLayout card = new LinearLayout(h.a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(h.px(20), h.px(18), h.px(20), h.px(16));
            GradientDrawable g = h.fill(22, 0x9E0A0E50);
            g.setStroke(Math.max(1, h.px(1)), 0x38FFFFFF);
            card.setBackground(g);
            LinearLayout head = new LinearLayout(h.a);
            head.setGravity(Gravity.BOTTOM);
            TextView title = h.text(15, bold, Color.WHITE);
            title.setText("THIS WEEK IN YOUR SHOWS");
            title.setLetterSpacing(0.16f);
            head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView count = h.text(14, h.regular, 0xB3FFFFFF);
            count.setText(week.size() + (week.size() == 1 ? " new episode" : " new episodes"));
            head.addView(count);
            card.addView(head);
            LinearLayout days = new LinearLayout(h.a);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
            dl.topMargin = h.px(12);
            card.addView(days, dl);
            for (int i = 0; i < 7; i++) {
                LocalDate d = monday.plusDays(i);
                boolean now = d.equals(today);
                LinearLayout col = new LinearLayout(h.a);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                TextView name = h.text(13, bold, now ? Color.WHITE : 0xBFFFFFFF);
                name.setText(d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.UK).toUpperCase(Locale.UK));
                name.setLetterSpacing(0.08f);
                col.addView(name);
                TextView num = h.text(15, bold, now ? NAVY : Color.WHITE);
                num.setText(String.valueOf(d.getDayOfMonth()));
                num.setGravity(Gravity.CENTER);
                if (now) num.setBackground(h.fill(15, WHITE));
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(h.px(30), h.px(30));
                nl.topMargin = h.px(6);
                col.addView(num, nl);
                List<Object[]> on = new ArrayList<>();
                for (Object[] w : week) if (d.equals(w[0])) on.add(w);
                if (on.isEmpty()) {
                    TextView none = h.text(13, h.regular, 0x73FFFFFF);
                    none.setText("–");
                    LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(-2, -2);
                    xl.topMargin = h.px(36);
                    col.addView(none, xl);
                } else {
                    Object[] w = on.get(0);
                    ImageView poster = new ImageView(h.a);
                    poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    poster.setBackground(h.fill(7, 0xFF0B0F55));
                    poster.setClipToOutline(true);
                    poster.setOutlineProvider(h.round(7));
                    h.image(poster, (String) w[1], h.px(124));
                    LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(h.px(62), h.px(92));
                    pl.topMargin = h.px(6);
                    col.addView(poster, pl);
                    TextView ep = h.text(13, bold, Color.WHITE);
                    ep.setText(w[2] + (on.size() > 1 ? " +" + (on.size() - 1) : ""));
                    LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-2, -2);
                    el.topMargin = h.px(6);
                    col.addView(ep, el);
                    int logo = HomeV2.serviceLogo((Integer) w[3] > 0 ? String.valueOf(w[3]) : null);
                    LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, h.px(14));
                    ll.topMargin = h.px(6);
                    if (logo != 0) {
                        ImageView mark = new ImageView(h.a);
                        mark.setImageResource(logo);
                        mark.setAdjustViewBounds(true);
                        mark.setMaxWidth(h.px(64));
                        col.addView(mark, ll);
                    } else if (w[4] != null) {
                        // No logo shipped for it (NOW and the rest): its name, set as a small wordmark.
                        TextView mark = h.text(12, heavy, Color.WHITE);
                        mark.setText(((String) w[4]).toUpperCase(Locale.UK));
                        mark.setSingleLine(true);
                        mark.setIncludeFontPadding(false);
                        col.addView(mark, ll);
                    }
                }
                days.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(h.px(600), -2, Gravity.TOP | Gravity.END);
            cl.topMargin = h.px(214);
            cl.rightMargin = h.px(h.dockMode ? 48 : 96);
            layer.addView(card, cl);
            made[0] = layer;
            return layer;
        };
    }

    /** How far back a new episode counts as new. */
    private static final int NEW_DAYS = 7;

    /**
     * TV shows' "New episodes" tab: every episode out in the last week, newest first, for the series you watch here
     * first (a red NEW badge, with how far you are), then the most popular series on the TV's own streaming apps (the
     * episode in white, and which app). OK as Your shows does: a series on one of the TV's apps as the page's other
     * tabs, one on none of them its store, else a line.
     */
    static PagesV2.Row fresh(HomeV2 h) {
        LocalDate today = LocalDate.now(), from = today.minusDays(NEW_DAYS);
        List<Object[]> mine = new ArrayList<>(), others = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        long since = System.currentTimeMillis() - 180L * 24 * 3600_000L;
        // Yours.
        for (WatchHistory.Entry e : WatchHistory.load(h.a)) {
            if (!e.tv || e.tmdbId <= 0 || e.at < since || !seen.add(e.tmdbId)) continue;
            if (seen.size() > 15) break;
            try {
                JSONObject d = details(h.tmdbKey, e.tmdbId);
                JSONObject last = d == null ? null : d.optJSONObject("last_episode_to_air");
                LocalDate out = date(last);
                if (out == null || out.isBefore(from) || out.isAfter(today)) continue;
                PagesV2.Title t = episode(h, d, last, out, today, true);
                if (t == null) continue;
                int[] at = position(e);
                if (at != null) t.foot = "You're on S" + at[0] + " E" + at[1];
                mine.add(new Object[]{t, out.toEpochDay()});
            } catch (Exception ignored) {
                // that series left out this time
            }
        }
        // The rest: out this week on the TV's apps, most watched first (TMDB's popularity), news, soaps and talk left out.
        String providers = ReleaseAlerts.providers(h.a);
        if (!providers.isEmpty() && h.tmdbKey != null && !h.tmdbKey.isEmpty()) {
            try {
                JSONArray res = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/discover/tv?language=en-GB&watch_region=GB"
                        + "&with_watch_providers=" + providers + "&with_watch_monetization_types=flatrate"
                        + "&air_date.gte=" + from + "&air_date.lte=" + today
                        + "&without_genres=10763,10766,10767&vote_count.gte=20&sort_by=popularity.desc&api_key=" + h.tmdbKey))
                        .optJSONArray("results");
                for (int i = 0; res != null && i < res.length() && others.size() < 16; i++) {
                    int id = res.getJSONObject(i).optInt("id");
                    if (id <= 0 || !seen.add(id)) continue;
                    JSONObject d = details(h.tmdbKey, id);
                    JSONObject last = d == null ? null : d.optJSONObject("last_episode_to_air");
                    LocalDate out = date(last);
                    if (out == null || out.isBefore(from) || out.isAfter(today)) continue;
                    PagesV2.Title t = episode(h, d, last, out, today, false);
                    if (t != null) others.add(new Object[]{t, out.toEpochDay()});
                }
            } catch (Exception ignored) {
                // yours only this time
            }
        }
        mine.sort((x, y) -> Long.compare((Long) y[1], (Long) x[1]));
        List<PagesV2.Title> items = new ArrayList<>();
        for (Object[] r : mine) items.add((PagesV2.Title) r[0]);
        for (Object[] r : others) items.add((PagesV2.Title) r[0]);
        PagesV2.Row row = PagesV2.row(null, null, false, items);
        row.empty = "New episodes from the last week show here: the series you watch first, then the most popular on your apps.";
        return row;
    }

    /** A new episode as a poster: the series, its badge (red for yours), the day it came out, and what OK does. */
    private static PagesV2.Title episode(HomeV2 h, JSONObject d, JSONObject last, LocalDate out, LocalDate today, boolean yours) {
        PagesV2.Title t = new PagesV2.Title();
        t.id = d.optInt("id");
        t.tv = true;
        t.title = d.optString("name", "");
        String poster = d.optString("poster_path", ""), back = d.optString("backdrop_path", "");
        if (poster.isEmpty() || "null".equals(poster)) return null;
        t.poster = "https://image.tmdb.org/t/p/w342" + poster;
        t.backdrop = !back.isEmpty() && !"null".equals(back) ? "https://image.tmdb.org/t/p/w1280" + back : null;
        t.vote = d.optDouble("vote_average", 0);
        t.year = "";
        int se = last.optInt("season_number"), ep = last.optInt("episode_number");
        boolean premiere = ep == 1;
        t.badge = (yours ? "NEW · " : "") + "S" + se + " E" + ep;
        t.badgeBg = yours ? RED : WHITE;
        t.badgeFg = yours ? WHITE : NAVY;
        String app = null;
        JSONObject gb = gb(d);
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length() && app == null; i++) {
            JSONObject pr = flat.optJSONObject(i);
            String a = pr == null ? null : HomeV2.serviceApp(String.valueOf(pr.optInt("provider_id")));
            if (a != null && ReleaseAlerts.installed(h.a, a)) app = pr.optString("provider_name", null);
        }
        t.foot = "Out " + (ChronoUnit.DAYS.between(out, today) == 0 ? "Today" : ChronoUnit.DAYS.between(out, today) == 1 ? "Yesterday"
                : out.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK)));
        t.what = (premiere ? "Season " + se + " starts: " : "New episode: ") + "S" + se + " E" + ep + named(last) + ", out " + when(out, today)
                + (app != null ? " · on " + app : "");
        t.overview = !last.optString("overview").isEmpty() ? last.optString("overview") : d.optString("overview", "");
        if (HomeV2.onYourApps(h, gb) == null) {
            String store = HomeV2.storeFor(h, gb);
            String name = t.title, art = t.backdrop != null ? t.backdrop : t.poster;
            int id = t.id;
            t.ok = store != null ? () -> h.searchTitle(store, name, true, id, art)
                    : () -> Toast.makeText(h.a, name + " isn't on your streaming apps yet", Toast.LENGTH_SHORT).show();
        }
        return t;
    }

    /** {title, rank (0 new, 1 to go, 2 coming, 3 undated), order within the rank} for one series; null to leave it out. */
    private static Object[] series(HomeV2 h, WatchHistory.Entry e, List<Object[]> week) throws Exception {
        JSONObject d = details(h.tmdbKey, e.tmdbId);
        if (d == null) return null;
        int[] at = position(e);
        boolean finished = e.progress() >= 0.95f;
        Map<Integer, Integer> counts = new HashMap<>();
        JSONArray seasons = d.optJSONArray("seasons");
        for (int i = 0; seasons != null && i < seasons.length(); i++) {
            JSONObject s = seasons.getJSONObject(i);
            if (s.optInt("season_number") > 0) counts.put(s.optInt("season_number"), s.optInt("episode_count"));
        }
        JSONObject last = d.optJSONObject("last_episode_to_air"), next = d.optJSONObject("next_episode_to_air");
        int sa = last == null ? 0 : last.optInt("season_number"), ea = last == null ? 0 : last.optInt("episode_number");
        LocalDate lastDate = date(last);
        int runtime = last != null && last.optInt("runtime") > 0 ? last.optInt("runtime") : first(d.optJSONArray("episode_run_time"), 45);
        // The service on this TV it streams on, for the foot ("6 of 8 · Disney+") and this week's panel.
        String service = null;
        int provider = 0;
        JSONObject gbAll = gb(d);
        JSONArray flat = gbAll == null ? null : gbAll.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length() && service == null; i++) {
            JSONObject pr = flat.optJSONObject(i);
            String a = pr == null ? null : HomeV2.serviceApp(String.valueOf(pr.optInt("provider_id")));
            if (a != null && h.installed(a)) { service = pr.optString("provider_name", null); provider = pr.optInt("provider_id"); }
        }
        // This week's episodes (Monday to Sunday): the last one out and the next, when they fall in it.
        String small = d.optString("poster_path", "");
        if (!small.isEmpty() && !"null".equals(small)) {
            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY), sunday = monday.plusDays(6);
            for (JSONObject ep : new JSONObject[]{last, d.optJSONObject("next_episode_to_air")}) {
                LocalDate on = date(ep);
                if (on == null || on.isBefore(monday) || on.isAfter(sunday)) continue;
                week.add(new Object[]{on, "https://image.tmdb.org/t/p/w185" + small, "E" + ep.optInt("episode_number"), provider, service});
            }
        }

        // The next episode to watch: the one under way, or the one after the one finished.
        int sn = 0, en = 0;
        if (at != null) {
            sn = at[0];
            en = at[1] + (finished ? 1 : 0);
            Integer inSeason = counts.get(sn);
            if (finished && inSeason != null && en > inSeason && counts.containsKey(sn + 1)) { sn++; en = 1; }
        }
        boolean aired = at != null && last != null && (sn < sa || sn == sa && en <= ea);
        int left = 0;
        if (aired) {
            if (sn == sa) left = ea - en + 1;
            else {
                left = Math.max(0, counts.getOrDefault(sn, en) - en + 1);
                for (int s = sn + 1; s < sa; s++) left += counts.getOrDefault(s, 0);
                left += ea;
            }
        }

        PagesV2.Title t = new PagesV2.Title();
        t.id = e.tmdbId;
        t.tv = true;
        t.title = d.optString("name", e.show != null ? e.show : e.title);
        String poster = d.optString("poster_path", ""), back = d.optString("backdrop_path", "");
        t.poster = !poster.isEmpty() && !"null".equals(poster) ? "https://image.tmdb.org/t/p/w342" + poster : e.poster;
        t.backdrop = !back.isEmpty() && !"null".equals(back) ? "https://image.tmdb.org/t/p/w1280" + back : e.backdrop;
        if (t.poster == null) return null;
        t.vote = d.optDouble("vote_average", 0);
        t.year = "";
        t.overview = d.optString("overview", "");
        t.app = e.pkg;
        int inSeason = counts.getOrDefault(sn, 0);
        // How far through the season: "S4 · 0 of 8" on the foot, over a yellow bar (each state below words it its way).
        boolean place = at != null && inSeason > 0;
        String through = place ? (en - 1) + " of " + inSeason : null;
        if (place) {
            t.foot = "S" + sn + " · " + through;
            t.season = Math.max(0f, Math.min(1f, (en - 1) / (float) inSeason));
        }
        boolean longGap = at != null && (System.currentTimeMillis() - e.at) / 86_400_000L >= Recap.GAP_DAYS;

        LocalDate today = LocalDate.now();
        int rank;
        long order;
        if (left > 0) {
            boolean fresh = sn == sa && en == ea && lastDate != null && !lastDate.isBefore(today.minusDays(8));
            if (fresh) {
                rank = 0;
                order = -lastDate.toEpochDay();
                t.badge = "NEW · S" + sa + " E" + ea;
                t.badgeBg = RED;
                t.badgeFg = WHITE;
                t.what = "S" + sa + " E" + ea + named(last) + " · " + runtime + " mins · out " + since(lastDate, today)
                        + (next != null && date(next) != null ? " · E" + next.optInt("episode_number") + " " + when(date(next), today) : "");
                if (!last.optString("overview").isEmpty()) t.overview = last.optString("overview");
            } else if (place && en == 1 && at[0] < sn) {
                // The season after the one finished is out: green, with Previously on ready after a long break.
                rank = 1;
                order = -e.at;
                t.badge = "SEASON " + sn + " IS OUT";
                t.badgeBg = GREEN;
                t.badgeFg = GREEN_INK;
                if (longGap) t.foot = "Recap ready";
                t.what = "Season " + sn + " is out · " + left + (left == 1 ? " episode" : " episodes") + " so far · about " + hours(left * runtime);
            } else {
                rank = 1;
                order = -e.at;
                String done = doneBy(h.a, left * runtime);
                t.badge = done != null ? "DONE BY " + done : left + " LEFT · " + hours(left * runtime).toUpperCase(Locale.UK);
                t.badgeBg = WHITE;
                t.badgeFg = NAVY;
                if (place) t.foot = done != null ? left + " left · " + through : through + (service != null ? " · " + service : "");
                t.what = left + (left == 1 ? " episode" : " episodes") + " to go, about " + hours(left * runtime) + " · next S" + sn + " E" + en;
            }
        } else if (next != null && date(next) != null) {
            LocalDate nd = date(next);
            rank = 2;
            order = nd.toEpochDay();
            boolean newSeason = next.optInt("episode_number") == 1;
            t.badge = newSeason ? "BACK " + nd.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK)
                    : "E" + next.optInt("episode_number") + " " + day(nd, today);
            t.badgeBg = YELLOW;
            t.badgeFg = NAVY;
            // A new season: just the date on the poster. The next episode: how far, and where.
            if (newSeason) { t.foot = null; t.season = -1; }
            else if (place) t.foot = through + (service != null ? " · " + service : "");
            t.what = (newSeason ? "Season " + next.optInt("season_number") + " starts " : "S" + next.optInt("season_number") + " E" + next.optInt("episode_number")
                    + named(next) + " is out ") + when(nd, today);
            if (!newSeason && !next.optString("overview").isEmpty()) t.overview = next.optString("overview");
        } else if ("Returning Series".equals(d.optString("status"))) {
            rank = 3;
            order = -e.at;
            t.badge = "S" + (sa + 1) + " · DATE TBC";
            t.badgeBg = DIM;
            t.badgeFg = WHITE;
            t.foot = null;
            t.season = -1;
            t.what = "All caught up · season " + (sa + 1) + " not dated yet";
        } else {
            return null;   // finished and ended: nothing more to come
        }

        // OK: a series on one of the TV's streaming apps does what the page's other tabs do; one on none of them, its
        // store if one of the TV's stores sells it, else says so.
        JSONObject gb = gb(d);
        if (HomeV2.onYourApps(h, gb) == null) {
            String store = HomeV2.storeFor(h, gb);
            String name = t.title, art = t.backdrop != null ? t.backdrop : t.poster;
            int id = t.id;
            t.ok = store != null ? () -> h.searchTitle(store, name, true, id, art)
                    : () -> Toast.makeText(h.a, name + " isn't on your streaming apps yet", Toast.LENGTH_SHORT).show();
        }
        // Back after a break (three weeks or more) with episodes to go: Previously on first.
        if (left > 0 && longGap) {
            Runnable go = t.ok != null ? t.ok : () -> h.playLikePages(t);
            t.ok = () -> Recap.open(h, t, go);
        }
        return new Object[]{t, rank, order};
    }

    /** "S4:E2", "S4 E2", "Season 4 Episode 2" in the title or subtitle: {season, episode}, or null. */
    static int[] position(WatchHistory.Entry e) {
        String all = e.title + " " + (e.sub == null ? "" : e.sub);
        Matcher m = SE.matcher(all);
        if (m.find()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        m = SEASON_EPISODE.matcher(all);
        if (m.find()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        return null;
    }

    static JSONObject details(String key, int id) {
        if (key == null || key.isEmpty()) return null;
        synchronized (DETAILS) {
            Object[] had = DETAILS.get(id);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < KEEP_MS) return (JSONObject) had[1];
        }
        try {
            JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id
                    + "?language=en-GB&append_to_response=watch/providers&api_key=" + key));
            synchronized (DETAILS) { DETAILS.put(id, new Object[]{System.currentTimeMillis(), d}); }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** The UK's providers in a details answer (append_to_response=watch/providers), or null. */
    static JSONObject gb(JSONObject d) {
        JSONObject wp = d.optJSONObject("watch/providers");
        JSONObject res = wp == null ? null : wp.optJSONObject("results");
        return res == null ? null : res.optJSONObject("GB");
    }

    private static LocalDate date(JSONObject ep) {
        if (ep == null) return null;
        try {
            return LocalDate.parse(ep.optString("air_date"));
        } catch (Exception e) {
            return null;
        }
    }

    private static int first(JSONArray a, int fallback) {
        return a != null && a.length() > 0 && a.optInt(0) > 0 ? a.optInt(0) : fallback;
    }

    private static String named(JSONObject ep) {
        String n = ep == null ? "" : ep.optString("name", "");
        return n.isEmpty() || n.matches("(?i)episode \\d+") ? "" : " “" + n + "”";
    }

    /** "E7 TOMORROW", "E3 THURSDAY", "E3 12 NOV". */
    private static String day(LocalDate d, LocalDate today) {
        long n = ChronoUnit.DAYS.between(today, d);
        if (n <= 0) return "TODAY";
        if (n == 1) return "TOMORROW";
        if (n < 7) return d.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK)).toUpperCase(Locale.UK);
        return d.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK);
    }

    /** "today", "yesterday", "on Sunday", "on Sunday 8 November". */
    private static String when(LocalDate d, LocalDate today) {
        long n = ChronoUnit.DAYS.between(today, d);
        if (n == 0) return "today";
        if (n == 1) return "tomorrow";
        if (n == -1) return "yesterday";
        if (Math.abs(n) < 7) return "on " + d.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK));
        return "on " + d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK));
    }

    /** "since Sunday", "today", "yesterday", "since 26 October". */
    private static String since(LocalDate d, LocalDate today) {
        long n = ChronoUnit.DAYS.between(d, today);
        if (n <= 0) return "today";
        if (n == 1) return "yesterday";
        if (n < 7) return "since " + d.format(DateTimeFormatter.ofPattern("EEEE", Locale.UK));
        return "since " + d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.UK));
    }

    private static String hours(int minutes) {
        return minutes < 60 ? minutes + " min" : minutes / 60 + "h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + "m");
    }

    /** The time it would all be over if started now, when that is before bed this evening; else null. */
    private static String doneBy(Context c, int minutes) {
        int bed = FitsTonight.bedMinutes(c), now = LocalTime.now().toSecondOfDay() / 60;
        if (now < 17 * 60 || now >= bed) return null;
        int end = now + minutes;
        return end <= bed ? String.format(Locale.UK, "%02d:%02d", end / 60, end % 60) : null;
    }
}
