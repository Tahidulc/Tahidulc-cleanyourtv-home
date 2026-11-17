package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TV shows' "Coming up" tab (theme "coming_up", on unless set false): the fortnight ahead from tomorrow, day by day. The
 * new episodes of the series watched on this TV (in any app, the last six months: TMDB's next episode and the rest of
 * its season that falls in the fortnight) and the films on My list on the day of their UK digital release. Each is a
 * poster with its day and how long until it ("FRI 6 · IN 2 DAYS") and along its foot the episode and the service
 * ("Episode 6 · Apple TV"); over the hero's picture the week it is in, seven days across, each with what comes out
 * that day (empty days "Nothing new"), the one in focus ringed. As the Movies and TV Ideas canvas draws it (G · Coming up).
 * OK opens the title's panel (Play there opens it in its licensed app, never Debrid Stream or an IPTV app); holding OK
 * turns the title's release pop-up off (and on again): ReleaseAlerts skips the titles turned off here.
 */
final class ComingUp {
    private ComingUp() { }

    static boolean on(Context c) {
        return Config.themeFlag(c, "coming_up", true);
    }

    /** adb's test_coming_up: TV shows opens on this tab, and with nothing of yours coming, popular series stand in. */
    static volatile boolean test;

    static final int DAYS = 14;
    private static final int BLUE = 0xFF4F8BFF, PINK = 0xFFFF4FA3, OFF_BG = 0xCC1B1E3C;
    /** Counts each pop-up turned off or on, so the week over the hero is drawn again after one. */
    private static volatile int changes;

    // ---- the pop-ups turned off ------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("comingup", Context.MODE_PRIVATE);
    }

    private static String key(int id, boolean tv) {
        return (tv ? "tv:" : "mv:") + id;
    }

    /** Its release pop-up turned off here (ReleaseAlerts asks). */
    static boolean muted(Context c, int id, boolean tv) {
        return prefs(c).getStringSet("muted", new HashSet<>()).contains(key(id, tv));
    }

    /** Off now (true) or on again (false). */
    static synchronized boolean toggleMuted(Context c, int id, boolean tv) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet("muted", new HashSet<>()));
        boolean now = !s.remove(key(id, tv));
        if (now) s.add(key(id, tv));
        prefs(c).edit().putStringSet("muted", s).apply();
        changes++;
        SyncHub.changed(c);
        return now;
    }

    /** Every title whose release pop-up is off here ("tv:1399", "mv:550"), for SyncHub. */
    static Set<String> mutedKeys(Context c) {
        return new HashSet<>(prefs(c).getStringSet("muted", new HashSet<>()));
    }

    /** A title's pop-up turned off (or on again) on a phone or the other TV (SyncHub). */
    static synchronized void setMuted(Context c, String key, boolean off) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet("muted", new HashSet<>()));
        if (off ? !s.add(key) : !s.remove(key)) return;
        prefs(c).edit().putStringSet("muted", s).apply();
        changes++;
        SyncHub.changed(c);
    }

    // ---- the sums (pure, unit tested) --------------------------------------------------------------------------------

    /** "TOMORROW", "IN 3 DAYS", "IN A WEEK", "IN 2 WEEKS"; "TODAY" for today. */
    static String countdown(LocalDate day, LocalDate today) {
        long n = ChronoUnit.DAYS.between(today, day);
        if (n <= 0) return "TODAY";
        if (n == 1) return "TOMORROW";
        if (n == 7) return "IN A WEEK";
        if (n == 14) return "IN 2 WEEKS";
        return "IN " + n + " DAYS";
    }

    /** "THU 5". */
    static String dayShort(LocalDate day) {
        return day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK).toUpperCase(Locale.UK) + " " + day.getDayOfMonth();
    }

    /** Which week of the fortnight a day is in: 0 the seven days from tomorrow, 1 the seven after; -1 outside it. */
    static int week(LocalDate day, LocalDate today) {
        long n = ChronoUnit.DAYS.between(today, day);
        if (n < 1 || n > DAYS) return -1;
        return (int) ((n - 1) / 7);
    }

    /** Within the fortnight ahead (tomorrow to DAYS days on). */
    static boolean ahead(LocalDate day, LocalDate today) {
        return day != null && week(day, today) >= 0;
    }

    // ---- reading ------------------------------------------------------------------------------------------------

    /** One thing out on a day: an episode of a series, or a film from My list. */
    static final class Item {
        LocalDate day;
        PagesV2.Title t;
        String what, service, art;
        boolean film;
    }

    /** Off the main thread (the page's own pool). */
    static PagesV2.Row row(HomeV2 h) {
        boolean sample = test;
        test = false;
        LocalDate today = LocalDate.now();
        List<Item> items = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        try {
            series(h, today, items, seen);
        } catch (Exception ignored) {
            // the series left out this time
        }
        try {
            films(h, today, items);
        } catch (Exception ignored) {
            // My list left out this time
        }
        if (items.isEmpty() && sample) {
            try {
                popular(h, today, items, seen);
            } catch (Exception ignored) {
                // nothing to stand in
            }
        }
        items.sort((x, y) -> {
            int d = x.day.compareTo(y.day);
            return d != 0 ? d : x.t.title.compareToIgnoreCase(y.t.title);
        });
        List<PagesV2.Title> titles = new ArrayList<>();
        for (Item it : items) {
            title(h, it, today);
            titles.add(it.t);
        }
        PagesV2.Row row = PagesV2.row(null, null, false, titles);
        row.empty = "New episodes of the series you watch here, and films on My list, show here in the fortnight before they're out.";
        row.side = weekPanel(items, today);
        return row;
    }

    /** The series watched here: each one's episodes out in the fortnight (its next one, and the rest of that season). */
    private static void series(HomeV2 h, LocalDate today, List<Item> out, Set<Integer> seen) {
        long since = System.currentTimeMillis() - 180L * 24 * 3600_000L;
        for (WatchHistory.Entry e : WatchHistory.load(h.a)) {
            if (!e.tv || e.tmdbId <= 0 || e.at < since || !seen.add(e.tmdbId)) continue;
            if (seen.size() > 15) break;
            addSeries(h, e.tmdbId, today, out);
        }
    }

    /** adb's test with nothing of yours coming: the most popular series on the air, as yours would show. */
    private static void popular(HomeV2 h, LocalDate today, List<Item> out, Set<Integer> seen) throws Exception {
        if (h.tmdbKey == null || h.tmdbKey.isEmpty()) return;
        JSONArray res = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/on_the_air?language=en-GB&api_key=" + h.tmdbKey))
                .optJSONArray("results");
        for (int i = 0; res != null && i < res.length() && out.size() < 12; i++) {
            int id = res.getJSONObject(i).optInt("id");
            if (id > 0 && seen.add(id)) addSeries(h, id, today, out);
        }
    }

    private static void addSeries(HomeV2 h, int id, LocalDate today, List<Item> out) {
        JSONObject d = YourShows.details(h.tmdbKey, id);
        JSONObject next = d == null ? null : d.optJSONObject("next_episode_to_air");
        LocalDate first = date(next);
        if (first == null || first.isAfter(today.plusDays(DAYS))) return;
        String[] service = service(h, YourShows.gb(d));
        int season = next.optInt("season_number");
        JSONObject s = EpisodeMap.season(h.tmdbKey, id, season);
        JSONArray eps = s == null ? null : s.optJSONArray("episodes");
        boolean any = false;
        for (int i = 0; eps != null && i < eps.length(); i++) {
            JSONObject ep = eps.optJSONObject(i);
            LocalDate on = date(ep);
            if (!ahead(on, today)) continue;
            out.add(episode(d, ep, on, service));
            any = true;
        }
        if (!any && ahead(first, today)) out.add(episode(d, next, first, service));
    }

    private static Item episode(JSONObject d, JSONObject ep, LocalDate on, String[] service) {
        Item it = new Item();
        it.day = on;
        PagesV2.Title t = new PagesV2.Title();
        t.id = d.optInt("id");
        t.tv = true;
        t.title = d.optString("name", "");
        String poster = d.optString("poster_path", ""), back = d.optString("backdrop_path", "");
        t.poster = poster.isEmpty() || "null".equals(poster) ? null : "https://image.tmdb.org/t/p/w342" + poster;
        t.backdrop = back.isEmpty() || "null".equals(back) ? null : "https://image.tmdb.org/t/p/w1280" + back;
        t.vote = d.optDouble("vote_average", 0);
        t.year = "";
        String overview = ep.optString("overview", "");
        t.overview = !overview.isEmpty() ? overview : d.optString("overview", "");
        int e = ep.optInt("episode_number"), s = ep.optInt("season_number");
        it.what = (e == 1 ? "Season " + s + " starts" : (s > 1 ? "S" + s + " · " : "") + "Episode " + e);
        it.service = service == null ? null : service[0];
        String still = ep.optString("still_path", "");
        it.art = !still.isEmpty() && !"null".equals(still) ? "https://image.tmdb.org/t/p/w300" + still
                : t.backdrop == null ? null : t.backdrop.replace("/w1280/", "/w300/");
        it.t = t;
        return it;
    }

    /** TMDB's UK digital releases (release_dates, type 4) of the films, kept twelve hours. */
    private static final Map<Integer, Object[]> RELEASES = new HashMap<>();

    /** The films on My list (the first thirty) whose UK digital release falls in the fortnight. */
    private static void films(HomeV2 h, LocalDate today, List<Item> out) {
        if (h.tmdbKey == null || h.tmdbKey.isEmpty()) return;
        int asked = 0;
        for (JSONObject o : MyList.all(h.a)) {
            if (o.optBoolean("tv") || asked++ >= 30) continue;
            int id = o.optInt("id");
            LocalDate on = digital(h.tmdbKey, id);
            if (!ahead(on, today)) continue;
            Item it = new Item();
            it.day = on;
            it.film = true;
            PagesV2.Title t = new PagesV2.Title();
            t.id = id;
            t.tv = false;
            t.title = o.optString("title");
            t.poster = clean(o.optString("poster", null));
            t.backdrop = clean(o.optString("backdrop", null));
            t.year = "";
            t.overview = "";
            if (t.poster == null) continue;
            it.what = "Film · out on digital";
            it.service = "My list";
            it.art = t.backdrop == null ? null : t.backdrop.replace("/w1280/", "/w300/");
            it.t = t;
            out.add(it);
        }
    }

    private static LocalDate digital(String key, int id) {
        synchronized (RELEASES) {
            Object[] had = RELEASES.get(id);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < 12 * 3600_000L) return (LocalDate) had[1];
        }
        LocalDate found = null;
        try {
            JSONArray res = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + id + "/release_dates?api_key=" + key))
                    .optJSONArray("results");
            for (int i = 0; res != null && i < res.length(); i++) {
                JSONObject r = res.optJSONObject(i);
                if (r == null || !"GB".equals(r.optString("iso_3166_1"))) continue;
                JSONArray dates = r.optJSONArray("release_dates");
                for (int k = 0; dates != null && k < dates.length(); k++) {
                    JSONObject d = dates.optJSONObject(k);
                    if (d == null || d.optInt("type") != 4) continue;
                    String when = d.optString("release_date", "");
                    if (when.length() >= 10) found = LocalDate.parse(when.substring(0, 10));
                }
            }
        } catch (Exception e) {
            return null;   // asked again next time
        }
        synchronized (RELEASES) { RELEASES.put(id, new Object[]{System.currentTimeMillis(), found}); }
        return found;
    }

    /** The service on this TV that streams it in the UK: {name, TMDB provider id}, or null. */
    private static String[] service(HomeV2 h, JSONObject gb) {
        JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
        for (int i = 0; flat != null && i < flat.length(); i++) {
            JSONObject p = flat.optJSONObject(i);
            String app = p == null ? null : HomeV2.serviceApp(String.valueOf(p.optInt("provider_id")));
            if (app != null && h.installed(app)) return new String[]{p.optString("provider_name"), String.valueOf(p.optInt("provider_id"))};
        }
        return null;
    }

    /** The poster's words: its day and countdown on the badge (blue, or dark with the pop-up off), the episode on the foot. */
    private static void title(HomeV2 h, Item it, LocalDate today) {
        PagesV2.Title t = it.t;
        String when = countdown(it.day, today);
        badge(h.a, it, today);
        t.foot = it.what + (it.service != null ? " · " + it.service : "");
        t.what = it.what + " · " + (ChronoUnit.DAYS.between(today, it.day) == 1 ? "out tomorrow"
                : "out " + it.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)) + " · " + when.toLowerCase(Locale.UK));
        t.noKind = true;
        // OK: the title's panel (its Play opens it in its licensed app). Held: its release pop-up off, or on again.
        t.ok = () -> h.showTitle(t, () -> h.openLicensed(t), false);
        t.hold = () -> {
            boolean off = toggleMuted(h.a, t.id, t.tv);
            badge(h.a, it, LocalDate.now());
            android.widget.Toast.makeText(h.a, t.title + (off ? ": no pop-up when it's out" : ": a pop-up when it's out"),
                    android.widget.Toast.LENGTH_SHORT).show();
        };
    }

    private static void badge(Context c, Item it, LocalDate today) {
        boolean off = muted(c, it.t.id, it.t.tv);
        it.t.badge = dayShort(it.day) + " · " + (off ? "POP-UP OFF" : countdown(it.day, today));
        it.t.badgeBg = off ? OFF_BG : it.film ? PINK : BLUE;
        it.t.badgeFg = Color.WHITE;
    }

    // ---- the week over the hero ------------------------------------------------------------------------------------

    /**
     * The week the title in focus comes out in (the first when none is): seven days across, each day's name and date
     * (tomorrow marked), then what comes out that day (its picture, name and episode, "+1" for more), "Nothing new" on a
     * dashed card for an empty day, the title in focus ringed in white, a title whose pop-up is off dimmed.
     */
    private static java.util.function.BiFunction<HomeV2, PagesV2.Title, View> weekPanel(List<Item> items, LocalDate today) {
        FrameLayout[] made = new FrameLayout[1];
        Object[] shown = {null, -1, -1};
        return (h, t) -> {
            int wk = 0;
            for (Item it : items) if (it.t == t) { wk = Math.max(0, week(it.day, today)); break; }
            // The same title, week and pop-ups as last time: left as it is.
            if (made[0] != null && shown[0] == t && (Integer) shown[1] == wk && (Integer) shown[2] == changes) return made[0];
            FrameLayout layer = made[0] != null ? made[0] : new FrameLayout(h.a);
            made[0] = layer;
            layer.removeAllViews();
            shown[0] = t;
            shown[1] = wk;
            shown[2] = changes;
            LinearLayout card = new LinearLayout(h.a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(h.px(20), h.px(16), h.px(20), h.px(16));
            GradientDrawable g = h.fill(22, 0x9E0A0E50);
            g.setStroke(Math.max(1, h.px(1)), 0x38FFFFFF);
            card.setBackground(g);
            Typeface bold = Typeface.create(h.semi, Typeface.BOLD);
            LinearLayout head = new LinearLayout(h.a);
            head.setGravity(Gravity.BOTTOM);
            TextView name = h.text(15, bold, Color.WHITE);
            name.setText(wk == 0 ? "THE WEEK AHEAD" : "THE WEEK AFTER");
            name.setLetterSpacing(0.16f);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            int count = 0;
            for (Item it : items) if (week(it.day, today) == wk) count++;
            TextView n = h.text(14, h.regular, 0xB3FFFFFF);
            n.setText(count == 0 ? "Nothing coming" : count + " coming up");
            head.addView(n);
            card.addView(head);
            LinearLayout days = new LinearLayout(h.a);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, -2);
            dl.topMargin = h.px(12);
            card.addView(days, dl);
            LocalDate start = today.plusDays(1 + 7L * wk);
            for (int i = 0; i < 7; i++) {
                LocalDate d = start.plusDays(i);
                List<Item> on = new ArrayList<>();
                for (Item it : items) if (it.day.equals(d)) on.add(it);
                days.addView(day(h, d, today, on, t, bold), new LinearLayout.LayoutParams(0, -2, 1f));
            }
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(h.px(780), -2, Gravity.TOP | Gravity.END);
            cl.topMargin = h.px(200);
            cl.rightMargin = h.px(h.dockMode ? 48 : 96);
            layer.addView(card, cl);
            return layer;
        };
    }

    private static View day(HomeV2 h, LocalDate d, LocalDate today, List<Item> on, PagesV2.Title focus, Typeface bold) {
        LinearLayout col = new LinearLayout(h.a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(h.px(4), 0, h.px(4), 0);
        LinearLayout top = new LinearLayout(h.a);
        top.setGravity(Gravity.BOTTOM);
        TextView name = h.text(16, Fonts.get(h.a, R.font.outfit_extrabold), Color.WHITE);
        name.setText(dayShort(d));
        top.addView(name);
        if (ChronoUnit.DAYS.between(today, d) == 1) {
            TextView tm = h.text(11, bold, 0xFFA9C4FF);
            tm.setText("  TMRW");
            top.addView(tm);
        }
        col.addView(top);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, h.px(118));
        bl.topMargin = h.px(8);
        if (on.isEmpty()) {
            TextView none = h.text(13, h.regular, 0xFFB9BDD8);
            none.setText("Nothing new");
            none.setGravity(Gravity.CENTER);
            GradientDrawable dash = new GradientDrawable();
            dash.setCornerRadius(h.px(10));
            dash.setStroke(Math.max(1, h.px(1.5f)), 0x38FFFFFF, h.px(6), h.px(4));
            none.setBackground(dash);
            col.addView(none, bl);
            return col;
        }
        Item it = on.get(0);
        for (Item x : on) if (x.t == focus) it = x;
        boolean ringed = it.t == focus, off = muted(h.a, it.t.id, it.t.tv);
        LinearLayout box = new LinearLayout(h.a);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = h.fill(10, 0x14FFFFFF);
        if (ringed) bg.setStroke(h.px(3), Color.WHITE);
        box.setBackground(bg);
        box.setPadding(h.px(ringed ? 3 : 0), h.px(ringed ? 3 : 0), h.px(ringed ? 3 : 0), h.px(6));
        ImageView pic = new ImageView(h.a);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pic.setBackground(h.fill(8, 0xFF0B0F55));
        pic.setClipToOutline(true);
        pic.setOutlineProvider(h.round(8));
        h.image(pic, it.art != null ? it.art : it.t.poster, h.px(200));
        box.addView(pic, new LinearLayout.LayoutParams(-1, h.px(54)));
        TextView title = h.text(13, bold, Color.WHITE);
        title.setText(it.t.title);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(h.px(6), h.px(6), h.px(6), 0);
        box.addView(title);
        TextView ep = h.text(12, h.regular, 0xFFDFE2F2);
        String what = it.film ? "Film" : it.what.replace("Episode ", "E");
        ep.setText(what + (on.size() > 1 ? "  +" + (on.size() - 1) : "") + (off ? "  · off" : ""));
        ep.setSingleLine(true);
        ep.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ep.setPadding(h.px(6), h.px(2), h.px(6), 0);
        box.addView(ep);
        if (off) box.setAlpha(0.55f);
        col.addView(box, bl);
        return col;
    }

    private static LocalDate date(JSONObject ep) {
        if (ep == null) return null;
        try {
            return LocalDate.parse(ep.optString("air_date"));
        } catch (Exception e) {
            return null;
        }
    }

    private static String clean(String url) {
        return url == null || url.isEmpty() || "null".equals(url) ? null : url;
    }
}
