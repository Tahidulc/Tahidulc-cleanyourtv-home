package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Your viewing (launcher v2), from the chart button in Home's top bar: what has played on this TV, from ViewingLog.
 * This week: the hours against the week before and day by day, which apps, when (evenings, by day and hour), what
 * (the films and series, with their posters), and a few habits. Left and Right change tab, Back closes.
 */
final class ViewingPanel {
    private static final int NAVY = 0xFF10157E, YELLOW = 0xFFFFD04D, CARD = 0x1AFFFFFF, EDGE = 0x2EFFFFFF;
    static final String[] PROVIDERS = {"8", "9", "119", "337", "350", "38", "41", "54", "103", "333", "39", "29", "73", "531", "1899"};

    private final HomeV2 h;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Typeface heavy, semi, regular;
    private boolean open;
    private FrameLayout overlay, body;
    private final List<TextView> tabViews = new ArrayList<>();
    private final String[] tabs;
    private int tab, gen;

    ViewingPanel(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
        tabs = new String[]{"This week", monthName(LocalDate.now().minusMonths(1)), "Your apps"};
    }

    boolean isOpen() {
        return open;
    }

    void show() {
        show(0);
    }

    /** Opened on a tab (1: last month, from the card on the 1st of the month). */
    void show(int first) {
        if (open) { load(first); return; }
        open = true;
        overlay = new FrameLayout(c);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF10157E, 0xFF1B279F, 0xFF2838C0}));
        overlay.setClickable(true);
        TextView crumb = h.text(16, semi, YELLOW);
        crumb.setText("YOUR VIEWING");
        crumb.setLetterSpacing(0.25f);
        place(overlay, crumb, 96, 72, 300, 30);
        LinearLayout bar = new LinearLayout(c);
        tabViews.clear();
        for (String t : tabs) {
            TextView v = h.text(22, regular, Color.WHITE);
            v.setText(t);
            v.setGravity(Gravity.CENTER);
            v.setPadding(h.px(24), 0, h.px(24), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(50));
            lp.rightMargin = h.px(8);
            bar.addView(v, lp);
            tabViews.add(v);
        }
        place(overlay, bar, 400, 58, 1400, 50);
        body = new FrameLayout(c);
        place(overlay, body, 0, 0, 1920, 1080);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        load(Math.max(0, Math.min(first, tabs.length - 1)));
    }

    void hide() {
        if (!open) return;
        open = false;
        gen++;
        View o = overlay;
        overlay = null;
        o.animate().alpha(0f).setDuration(180).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && tab > 0) load(tab - 1);
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && tab < tabs.length - 1) load(tab + 1);
        return true;
    }

    private void load(int t) {
        tab = t;
        int my = ++gen;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView v = tabViews.get(i);
            boolean on = i == t;
            GradientDrawable g = h.fill(25, on ? 0x29FFFFFF : 0);
            if (on) g.setStroke(h.px(2), Color.WHITE);
            v.setBackground(g);
            v.setTypeface(on ? semi : regular);
        }
        body.removeAllViews();
        // The posters fetched off the main thread; the views made on it.
        io.execute(() -> {
            Map<Integer, Bitmap> pics = new HashMap<>();
            Map<Integer, List<String>> kinds = new HashMap<>();
            Map<String, String> notes = t == 2 ? appNotes() : new HashMap<>();
            try {
                List<JSONObject> want = prefetch(t);
                for (int i = 0; i < want.size(); i++) {
                    JSONObject x = want.get(i);
                    Object[] d = details(x.optInt("id"), x.optBoolean("tv"), i < 5);
                    if (d[0] != null) pics.put(x.optInt("id"), (Bitmap) d[0]);
                    kinds.put(x.optInt("id"), (List<String>) d[1]);
                }
            } catch (Exception ignored) {
                // no posters this time
            }
            main.post(() -> {
                if (my != gen || !open) return;
                posters = pics;
                genres = kinds;
                tips = notes;
                View v;
                try {
                    v = build(t);
                } catch (Exception ex) {
                    return;
                }
                body.addView(v, new FrameLayout.LayoutParams(-1, -1));
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(200).start();
            });
        });
    }

    /** The posters each tab shows, fetched before it is drawn. */
    private Map<Integer, Bitmap> posters = new HashMap<>();

    /** Each title's kinds (TMDB's genres), for last month's personality. */
    private Map<Integer, List<String>> genres = new HashMap<>();

    /** The titles whose posters (the first five) and kinds a tab shows. Off the main thread. */
    private List<JSONObject> prefetch(int t) {
        if (t == 2) return new ArrayList<>();
        List<JSONObject> all = t == 1 ? titles(month(LocalDate.now().minusMonths(1))) : titles(days(LocalDate.now(), 7));
        return all.subList(0, Math.min(t == 1 ? 10 : 5, all.size()));
    }

    private View build(int t) {
        return t == 2 ? appsView() : t == 1 ? monthView() : week();
    }

    // ---- your apps: what they cost an hour ----------------------------------------------------------------------

    /** The streaming apps with a monthly price: package, name, typical UK price (the theme's app_prices replace it). */
    private static final String[][] PAID = {
            {"com.netflix.ninja", "Netflix", "12.99"}, {"com.amazon.amazonvideo.livingroom", "Prime Video", "8.99"},
            {"com.disney.disneyplus", "Disney+", "9.99"}, {"com.apple.atve.androidtv.appletv", "Apple TV", "9.99"},
            {"com.nowtv.androidtv", "NOW", "9.99"}, {"com.cbs.ca", "Paramount+", "6.99"}, {"com.wbd.stream", "HBO Max", "9.99"}};

    /** What each app's subscription costs a month: the theme's "app_prices" ("Netflix|12.99"), else the usual UK price. */
    private Map<String, Double> prices() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String[] p : PAID) out.put(p[0], Double.parseDouble(p[2]));
        for (String line : Config.themeStrings(c, "app_prices", new ArrayList<>())) {
            String[] kv = line.split("\\|");
            if (kv.length < 2) continue;
            String who = kv[0].trim();
            for (String[] p : PAID) if (p[1].equalsIgnoreCase(who)) who = p[0];
            try {
                out.put(who, Double.parseDouble(kv[1].trim().replace("£", "")));
            } catch (NumberFormatException ignored) {
                // left as it was
            }
        }
        return out;
    }

    /** Tips for each app, from the series watched here (Your shows): what is coming on it, and when it ends. */
    private Map<String, String> tips = new HashMap<>();

    private Map<String, String> appNotes() {
        Map<String, String> out = new HashMap<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        LocalDate today = LocalDate.now();
        for (WatchHistory.Entry e : WatchHistory.load(c)) {
            if (!e.tv || e.tmdbId <= 0 || !seen.add(e.tmdbId) || seen.size() > 15) continue;
            JSONObject d = YourShows.details(h.tmdbKey, e.tmdbId);
            if (d == null) continue;
            JSONObject gb = YourShows.gb(d), next = d.optJSONObject("next_episode_to_air");
            JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
            String app = null;
            for (int i = 0; flat != null && i < flat.length() && app == null; i++) {
                String a = HomeV2.serviceApp(String.valueOf(flat.optJSONObject(i).optInt("provider_id")));
                if (a != null && ReleaseAlerts.installed(c, a)) app = a;
            }
            if (app == null || next == null || out.containsKey(app)) continue;
            try {
                LocalDate nd = LocalDate.parse(next.optString("air_date"));
                int season = next.optInt("season_number"), ep = next.optInt("episode_number");
                int count = 0;
                JSONArray ss = d.optJSONArray("seasons");
                for (int i = 0; ss != null && i < ss.length(); i++) if (ss.getJSONObject(i).optInt("season_number") == season) count = ss.getJSONObject(i).optInt("episode_count");
                // Weekly from the next one: the season's last episode.
                LocalDate end = count > ep ? nd.plusWeeks(count - ep) : nd;
                out.put(app, d.optString("name") + (ep == 1 ? " is back " : " has new episodes until ")
                        + (ep == 1 ? nd : end).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)) + (ep == 1 ? "" : " (weekly)"));
            } catch (Exception ignored) {
                // no note for it
            }
        }
        return out;
    }

    private View appsView() {
        LocalDate any = LocalDate.now().minusMonths(1);
        String name = monthName(any);
        List<JSONObject> days = month(any);
        Map<String, Integer> secs = new HashMap<>();
        Map<String, Map<String, Integer>> what = new HashMap<>();
        for (JSONObject d : days) {
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                secs.put(p, secs.getOrDefault(p, 0) + a.optInt(p));
            }
            JSONObject t = d.optJSONObject("t");
            for (Iterator<String> it = t == null ? null : t.keys(); it != null && it.hasNext(); ) {
                JSONObject one = t.optJSONObject(it.next());
                String p = one.optString("p", "");
                if (p.isEmpty() || one.optString("n").isEmpty()) continue;
                Map<String, Integer> m = what.computeIfAbsent(p, k -> new HashMap<>());
                m.put(one.optString("n"), m.getOrDefault(one.optString("n"), 0) + one.optInt("s"));
            }
        }
        Map<String, Double> price = prices();
        List<String> apps = new ArrayList<>();
        for (String p : price.keySet()) if (ReleaseAlerts.installed(c, p)) apps.add(p);
        apps.sort((x, y) -> {
            double a = per(price.get(x), secs.getOrDefault(x, 0)), b = per(price.get(y), secs.getOrDefault(y, 0));
            return Double.compare(a, b);
        });
        FrameLayout v = new FrameLayout(c);
        double total = 0;
        int paidSecs = 0;
        for (String p : apps) { total += price.get(p); paidSecs += secs.getOrDefault(p, 0); }
        TextView head = h.text(40, heavy, Color.WHITE);
        head.setText("Are your apps worth it?");
        place(v, head, 96, 128, 900, 56);
        TextView sum = h.text(19, regular, 0xD9FFFFFF);
        sum.setText(name + " · £" + money(total) + " a month for " + hm(paidSecs)
                + (paidSecs >= 3600 ? " · £" + money(total / (paidSecs / 3600.0)) + " an hour" : ""));
        place(v, sum, 96, 190, 1500, 30);
        if (apps.isEmpty()) {
            TextView none = h.text(24, regular, 0xD9FFFFFF);
            none.setText("No paid streaming apps on this TV.");
            place(v, none, 96, 260, 1200, 40);
            return v;
        }
        for (int i = 0; i < Math.min(6, apps.size()); i++) {
            String p = apps.get(i);
            int s = secs.getOrDefault(p, 0);
            double cost = price.get(p), hour = per(cost, s);
            String word;
            int colour;
            if (s < 600) { word = "NOT USED"; colour = 0xFFFF5A5A; }
            else if (hour < 1) { word = "BEST VALUE"; colour = 0xFF3DDC84; }
            else if (hour < 2) { word = "GOOD VALUE"; colour = 0xFF3DDC84; }
            else if (hour < 4) { word = "OK"; colour = YELLOW; }
            else { word = "HARDLY USED"; colour = 0xFFFF5A5A; }
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(h.px(26), h.px(20), h.px(26), h.px(18));
            GradientDrawable g = h.fill(24, CARD);
            g.setStroke(h.px(colour == 0xFFFF5A5A ? 2 : 1), colour == 0xFFFF5A5A ? colour : EDGE);
            card.setBackground(g);
            LinearLayout top = new LinearLayout(c);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(appMark(p), new LinearLayout.LayoutParams(0, h.px(34), 1f));
            TextView chip = h.text(14, semi, colour == YELLOW ? NAVY : colour == 0xFF3DDC84 ? 0xFF06331B : Color.WHITE);
            chip.setText(word);
            chip.setLetterSpacing(0.08f);
            chip.setPadding(h.px(10), h.px(4), h.px(10), h.px(4));
            chip.setBackground(h.fill(6, colour));
            top.addView(chip);
            card.addView(top);
            LinearLayout line = new LinearLayout(c);
            line.setGravity(Gravity.BOTTOM);
            TextView big = h.text(46, heavy, Color.WHITE);
            big.setText(s < 600 ? "—" : hour < 1 ? Math.round(hour * 100) + "p" : "£" + money(hour));
            line.addView(big);
            TextView sm = h.text(17, regular, 0xCCFFFFFF);
            sm.setText((s < 600 ? "" : "an hour · ") + hm(s) + " for £" + money(cost));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.leftMargin = h.px(12);
            sl.bottomMargin = h.px(10);
            line.addView(sm, sl);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = h.px(8);
            card.addView(line, ll);
            FrameLayout track = new FrameLayout(c);
            track.setBackground(h.fill(5, 0x24FFFFFF));
            View fill = new View(c);
            fill.setBackground(h.fill(5, colour));
            float share = s < 600 ? 0.04f : (float) Math.max(0.06, Math.min(1, 1 - hour / 6));
            track.addView(fill, new FrameLayout.LayoutParams(Math.round(h.px(500) * share), -1));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(h.px(500), h.px(10));
            tl.topMargin = h.px(8);
            card.addView(track, tl);
            Map<String, Integer> on = what.get(p);
            if (on != null && !on.isEmpty()) {
                List<Map.Entry<String, Integer>> list = new ArrayList<>(on.entrySet());
                list.sort((x, y) -> y.getValue() - x.getValue());
                StringBuilder b = new StringBuilder();
                for (int k = 0; k < Math.min(2, list.size()); k++) b.append(k == 0 ? "" : ", ").append(list.get(k).getKey());
                if (list.size() > 2) b.append(" and ").append(list.size() - 2).append(" more");
                TextView w = h.text(16, regular, 0xCCFFFFFF);
                w.setText(b + ".");
                w.setSingleLine(true);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
                wl.topMargin = h.px(10);
                card.addView(w, wl);
            }
            String tip = tips.get(p);
            String advice = tip != null ? (hour >= 4 || s < 600 ? "Tip · " + tip + ": keep it till then, pause after" : "Coming · " + tip)
                    : hour >= 4 || s < 600 ? "Tip · Nothing you watch there is coming soon: pausing it saves £" + money(cost) + " a month" : null;
            if (advice != null) {
                TextView t = h.text(15, semi, Color.WHITE);
                t.setText(advice);
                t.setMaxLines(2);
                t.setPadding(h.px(12), h.px(8), h.px(12), h.px(8));
                t.setBackground(h.fill(12, colour == 0xFFFF5A5A ? 0x2EFF5A5A : 0x14FFFFFF));
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
                al.topMargin = h.px(10);
                card.addView(t, al);
            }
            place(v, card, 96 + (i % 3) * 584, 250 + (i / 3) * 340, 560, 318);
        }
        TextView foot = h.text(15, regular, 0x99FFFFFF);
        foot.setText("Prices are the theme's app_prices, or the usual UK price where it gives none. Nothing is cancelled for you: it only tells you.");
        place(v, foot, 96, 1000, 1600, 30);
        return v;
    }

    private static double per(double price, int secs) {
        return secs < 600 ? 999 : price / (secs / 3600.0);
    }

    private static String money(double v) {
        return String.format(Locale.UK, "%.2f", v);
    }

    /** A film's or series' poster (when asked) and genres, from TMDB: {Bitmap or null, List<String>}. Off the main thread. */
    private Object[] details(int id, boolean tv, boolean picture) {
        List<String> kinds = new ArrayList<>();
        if (id <= 0 || h.tmdbKey.isEmpty()) return new Object[]{null, kinds};
        try {
            JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id + "?api_key=" + h.tmdbKey));
            JSONArray g = d.optJSONArray("genres");
            for (int i = 0; g != null && i < g.length(); i++) kinds.add(g.getJSONObject(i).optString("name"));
            String p = d.optString("poster_path", "");
            Bitmap b = !picture || p.isEmpty() || "null".equals(p) ? null : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w342" + p, h.px(260));
            return new Object[]{b, kinds};
        } catch (Exception e) {
            return new Object[]{picture ? poster(id, tv) : null, kinds};
        }
    }

    static String monthName(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.FULL, Locale.UK);
    }

    /** A calendar month's records, its first day first. */
    private List<JSONObject> month(LocalDate any) {
        LocalDate first = any.withDayOfMonth(1);
        List<JSONObject> out = new ArrayList<>();
        for (LocalDate d = first; d.getMonthValue() == first.getMonthValue(); d = d.plusDays(1)) out.add(ViewingLog.day(c, d));
        return out;
    }

    // ---- last month ---------------------------------------------------------------------------------------------

    private View monthView() {
        LocalDate any = LocalDate.now().minusMonths(1), first = any.withDayOfMonth(1);
        String name = monthName(any);
        List<JSONObject> days = month(any), before = month(any.minusMonths(1));
        int sum = 0, was = 0, live = 0, late = 0, weekend = 0, withTv = 0, bestDay = 0, bestSecs = 0;
        for (int i = 0; i < days.size(); i++) {
            JSONObject d = days.get(i);
            int s = total(d);
            sum += s;
            if (s >= 15 * 60) withTv++;
            if (s > bestSecs) { bestSecs = s; bestDay = i; }
            DayOfWeek dow = first.plusDays(i).getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) weekend += s;
            JSONArray hh = d.optJSONArray("h");
            for (int k = 21; k < 24; k++) late += hh == null ? 0 : hh.optInt(k);
            for (int k = 0; k < 3; k++) late += hh == null ? 0 : hh.optInt(k);
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                if (ViewingLog.live(p)) live += a.optInt(p);
            }
        }
        for (JSONObject d : before) was += total(d);
        FrameLayout v = new FrameLayout(c);
        if (sum < 30 * 60) {
            TextView none = h.text(26, regular, 0xD9FFFFFF);
            none.setText("Not enough watched on this TV in " + name + " for a round-up. Next month's is on the 1st.");
            place(v, none, 96, 200, 1300, 120);
            return v;
        }
        // The month's hours, big.
        TextView kicker = h.text(18, semi, YELLOW);
        kicker.setText("YOUR " + name.toUpperCase(Locale.UK) + " ON TV");
        kicker.setLetterSpacing(0.28f);
        place(v, kicker, 120, 140, 900, 30);
        int hours = Math.round(sum / 3600f);
        TextView big = h.text(150, heavy, Color.WHITE);
        big.setText(hours + (hours == 1 ? " hour" : " hours"));
        big.setLetterSpacing(-0.03f);
        place(v, big, 108, 168, 1200, 200);
        TextView sub = h.text(26, regular, 0xEBFFFFFF);
        int days24 = sum / 86400, rest = (sum % 86400) / 3600;
        String compare = was <= 0 ? "" : ", " + Math.abs(hours - Math.round(was / 3600f)) + (hours >= Math.round(was / 3600f) ? " more" : " fewer")
                + " than " + monthName(any.minusMonths(1));
        sub.setText((days24 > 0 ? "That's " + days24 + (days24 == 1 ? " day and " : " days and ") + rest + " hours" : "That's " + hm(sum)) + compare + ".");
        place(v, sub, 120, 372, 1200, 40);

        // The top show, then the next four.
        List<JSONObject> titles = titles(days);
        JSONObject top = null;
        for (JSONObject t : titles) if (t.optBoolean("tv")) { top = t; break; }
        if (top == null && !titles.isEmpty()) top = titles.get(0);
        if (top != null) {
            FrameLayout pic = new FrameLayout(c);
            pic.setBackground(h.fill(20, 0xFF0B0F55));
            pic.setClipToOutline(true);
            pic.setOutlineProvider(h.round(20));
            ImageView pi = new ImageView(c);
            pi.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap b = posters.get(top.optInt("id"));
            if (b != null) pi.setImageBitmap(b);
            pic.addView(pi, new FrameLayout.LayoutParams(-1, -1));
            TextView one = h.text(36, heavy, YELLOW);
            one.setText("#1");
            one.setShadowLayer(h.px(10), 0, h.px(2), 0xB3000000);
            FrameLayout.LayoutParams ol = new FrameLayout.LayoutParams(-2, -2);
            ol.leftMargin = h.px(12);
            ol.topMargin = h.px(6);
            pic.addView(one, ol);
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(h.px(20));
            ring.setStroke(h.px(5), YELLOW);
            pic.setForeground(ring);
            place(v, pic, 120, 440, 230, 345);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView head = h.text(16, semi, 0xD9FFFFFF);
            head.setText(top.optBoolean("tv") ? "YOUR TOP SHOW" : "YOUR TOP FILM");
            head.setLetterSpacing(0.2f);
            col.addView(head);
            TextView n = h.text(48, heavy, Color.WHITE);
            n.setText(top.optString("n"));
            n.setMaxLines(2);
            col.addView(n);
            int eps = top.optJSONArray("eps") == null ? 0 : top.optJSONArray("eps").length();
            int span = 0;
            for (JSONObject d : days) if (d.optJSONObject("t") != null && d.optJSONObject("t").has(keyOf(top))) span++;
            TextView line = h.text(22, regular, 0xEBFFFFFF);
            line.setText((eps > 1 ? eps + " episodes in " + span + (span == 1 ? " day" : " days") + " · " : "") + hm(top.optInt("s")));
            col.addView(line);
            String app = top.optString("p", "");
            if (!app.isEmpty()) {
                LinearLayout on = new LinearLayout(c);
                on.setGravity(Gravity.CENTER_VERTICAL);
                TextView w = h.text(18, regular, 0xD9FFFFFF);
                w.setText("on  ");
                on.addView(w);
                on.addView(appMark(app), new LinearLayout.LayoutParams(-2, h.px(26)));
                LinearLayout.LayoutParams onl = new LinearLayout.LayoutParams(-2, -2);
                onl.topMargin = h.px(8);
                col.addView(on, onl);
            }
            place(v, col, 380, 600, 420, 200);
        }
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        int n = 2;
        for (JSONObject t : titles) {
            if (t == top) continue;
            if (n > 5) break;
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = h.text(34, heavy, 0xD9FFFFFF);
            num.setText(String.valueOf(n++));
            row.addView(num, new LinearLayout.LayoutParams(h.px(50), -2));
            ImageView p = new ImageView(c);
            p.setScaleType(ImageView.ScaleType.CENTER_CROP);
            p.setBackground(h.fill(8, 0xFF0B0F55));
            p.setClipToOutline(true);
            p.setOutlineProvider(h.round(8));
            Bitmap b = posters.get(t.optInt("id"));
            if (b != null) p.setImageBitmap(b);
            row.addView(p, new LinearLayout.LayoutParams(h.px(60), h.px(90)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView tn = h.text(21, semi, Color.WHITE);
            tn.setText(t.optString("n"));
            tn.setSingleLine(true);
            words.addView(tn);
            TextView tt = h.text(17, regular, 0xCCFFFFFF);
            tt.setText(hm(t.optInt("s")));
            words.addView(tt);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(h.px(380), -2);
            wl.leftMargin = h.px(16);
            row.addView(words, wl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
            rl.bottomMargin = h.px(14);
            list.addView(row, rl);
        }
        place(v, list, 860, 440, 520, 440);

        // Your TV personality.
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(h.px(30), h.px(26), h.px(30), h.px(26));
        card.setBackground(h.fill(26, 0xF2FFFFFF));
        TextView ph = h.text(15, semi, 0xFFFF2D7A);
        ph.setText("YOUR TV PERSONALITY");
        ph.setLetterSpacing(0.2f);
        card.addView(ph);
        String[] who = personality(titles, live, late, weekend, sum);
        TextView pn = h.text(42, heavy, NAVY);
        pn.setText(who[0]);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = h.px(8);
        card.addView(pn, pl);
        TextView pt = h.text(19, regular, 0xD910157E);
        pt.setText(who[1]);
        pt.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = h.px(10);
        card.addView(pt, tl);
        place(v, card, 1380, 120, 440, 330);

        // The month in four facts.
        LinearLayout facts = new LinearLayout(c);
        int films = 0;
        JSONObject longest = null;
        for (JSONObject t : titles) {
            if (t.optBoolean("tv") || !keyOf(t).startsWith("mv:")) continue;
            films++;
            if (longest == null || t.optInt("s") > longest.optInt("s")) longest = t;
        }
        LocalDate big1 = first.plusDays(bestDay);
        String[][] four = {
                {"BIGGEST DAY", big1.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + " " + big1.getDayOfMonth() + " · " + hm(bestSecs)},
                {"LIVE TV & SPORT", hm(live)},
                {"FILMS", films == 0 ? "None" : films + (longest != null ? " · most, " + longest.optString("n") : "")},
                {"DAYS WITH TV", withTv + " of " + days.size()}};
        for (String[] f : four) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView a = h.text(14, semi, 0xB3FFFFFF);
            a.setText(f[0]);
            a.setLetterSpacing(0.18f);
            col.addView(a);
            TextView b = h.text(21, semi, Color.WHITE);
            b.setText(f[1]);
            b.setSingleLine(true);
            col.addView(b);
            facts.addView(col, new LinearLayout.LayoutParams(h.px(420), -2));
        }
        place(v, facts, 120, 900, 1700, 80);
        return v;
    }

    /** The key a title was logged under. */
    private static String keyOf(JSONObject t) {
        int id = t.optInt("id");
        return id > 0 ? (t.optBoolean("tv") ? "tv:" : "mv:") + id : "x:" + t.optString("n");
    }

    /** {name, line}: the month's TV personality, from the kinds watched most (by time) and when. */
    private String[] personality(List<JSONObject> titles, int live, int late, int weekend, int sum) {
        Map<String, Integer> kinds = new HashMap<>();
        for (JSONObject t : titles) {
            List<String> g = genres.get(t.optInt("id"));
            if (g == null) continue;
            for (String k : g) {
                String group = group(k);
                if (group != null) kinds.put(group, kinds.getOrDefault(group, 0) + t.optInt("s"));
            }
        }
        String best = null;
        int bestS = 0;
        for (Map.Entry<String, Integer> e : kinds.entrySet()) if (e.getValue() > bestS) { best = e.getKey(); bestS = e.getValue(); }
        int latePct = Math.round(late * 100f / Math.max(1, sum));
        String when = latePct >= 50 ? "Late-Night" : weekend * 100f / Math.max(1, sum) >= 50 ? "Weekend" : "Evening";
        if (live * 100f / Math.max(1, sum) >= 40 || best == null) {
            return new String[]{"The " + when + " Matchday Regular", "Live TV and sport were " + Math.round(live * 100f / Math.max(1, sum))
                    + "% of your month, and " + latePct + "% of your hours were after 9pm."};
        }
        String noun;
        switch (best) {
            case "Crime and thrillers": noun = "Detective"; break;
            case "Comedy": noun = "Comedy Fan"; break;
            case "Sci-fi and fantasy": noun = "Explorer"; break;
            case "Drama": noun = "Drama Lover"; break;
            case "Animation and family": noun = "Family Screener"; break;
            case "Documentaries": noun = "Fact Finder"; break;
            case "Reality": noun = "Reality Watcher"; break;
            case "Action": noun = "Action Hero"; break;
            case "Horror": noun = "Fright Fan"; break;
            default: noun = "Romantic";
        }
        return new String[]{"The " + when + " " + noun, best + " made up " + Math.round(bestS * 100f / Math.max(1, sum))
                + "% of your month, and " + latePct + "% of your hours were after 9pm."};
    }

    private static String group(String genre) {
        switch (genre) {
            case "Crime": case "Thriller": case "Mystery": return "Crime and thrillers";
            case "Comedy": return "Comedy";
            case "Science Fiction": case "Sci-Fi & Fantasy": case "Fantasy": return "Sci-fi and fantasy";
            case "Drama": return "Drama";
            case "Animation": case "Family": case "Kids": return "Animation and family";
            case "Documentary": return "Documentaries";
            case "Reality": return "Reality";
            case "Action": case "Action & Adventure": case "Adventure": case "War": case "War & Politics": return "Action";
            case "Horror": return "Horror";
            case "Romance": return "Romance";
            default: return null;
        }
    }

    // ---- This week -------------------------------------------------------------------------------------------------

    /** The last seven days' records (today last), and the seven before them. */
    private List<JSONObject> days(LocalDate last, int n) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) out.add(ViewingLog.day(c, last.minusDays(i)));
        return out;
    }

    static int total(JSONObject d) {
        JSONObject a = d.optJSONObject("a");
        int s = 0;
        for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) s += a.optInt(it.next());
        return s;
    }

    private View week() {
        LocalDate today = LocalDate.now();
        List<JSONObject> week = days(today, 7), before = days(today.minusDays(7), 7);
        FrameLayout v = new FrameLayout(c);
        int sum = 0, was = 0;
        int[] perDay = new int[7];
        for (int i = 0; i < 7; i++) { perDay[i] = total(week.get(i)); sum += perDay[i]; was += total(before.get(i)); }
        if (sum < 60) {
            TextView none = h.text(26, regular, 0xD9FFFFFF);
            none.setText("Nothing watched on this TV in the last week yet. What plays in any app (and on live TV) shows here, "
                    + "counted on the TV and never sent anywhere.");
            place(v, none, 96, 200, 1300, 120);
            return v;
        }

        // The hours, against the week before, and day by day.
        LinearLayout first = card(v, 96, 150, 560, 300);
        first.addView(label(today.minusDays(6).format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK)
                + " – " + today.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK)));
        LinearLayout big = new LinearLayout(c);
        big.setGravity(Gravity.BOTTOM);
        TextView hours = h.text(64, heavy, Color.WHITE);
        hours.setText(hm(sum));
        big.addView(hours);
        if (was > 0) {
            TextView delta = h.text(18, semi, YELLOW);
            int diff = sum - was;
            delta.setText((diff >= 0 ? "▲ " : "▼ ") + hm(Math.abs(diff)));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
            dl.leftMargin = h.px(14);
            dl.bottomMargin = h.px(12);
            big.addView(delta, dl);
        }
        first.addView(big);
        TextView avg = h.text(16, regular, 0xBFFFFFFF);
        avg.setText("on the TV in the last 7 days · " + hm(sum / 7) + " a day");
        first.addView(avg);
        LinearLayout bars = new LinearLayout(c);
        bars.setGravity(Gravity.BOTTOM);
        int most = 1;
        for (int s : perDay) most = Math.max(most, s);
        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            TextView n = h.text(12, semi, 0xCCFFFFFF);
            n.setText(perDay[i] >= 60 ? hmShort(perDay[i]) : "");
            col.addView(n);
            View b = new View(c);
            b.setBackground(h.fill(8, perDay[i] == most ? YELLOW : 0x8CFFFFFF));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(h.px(34), Math.max(h.px(4), Math.round(h.px(96) * perDay[i] / (float) most)));
            bl.topMargin = h.px(4);
            col.addView(b, bl);
            TextView d = h.text(14, semi, 0xCCFFFFFF);
            d.setText(today.minusDays(6 - i).getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.UK));
            col.addView(d);
            bars.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bp.topMargin = h.px(10);
        first.addView(bars, bp);

        // Where: the apps.
        Map<String, Integer> apps = new HashMap<>();
        for (JSONObject d : week) {
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                String k = ViewingLog.live(p) ? ViewingLog.LIVE : p;
                apps.put(k, apps.getOrDefault(k, 0) + a.optInt(p));
            }
        }
        List<Map.Entry<String, Integer>> appList = new ArrayList<>(apps.entrySet());
        appList.sort((x, y) -> y.getValue() - x.getValue());
        LinearLayout where = card(v, 676, 150, 560, 300);
        where.addView(label("WHERE"));
        int top = appList.isEmpty() ? 1 : appList.get(0).getValue();
        for (int i = 0; i < Math.min(6, appList.size()); i++) where.addView(appRow(appList.get(i).getKey(), appList.get(i).getValue(), top, i == 0));

        // When: evenings, 17:00 to 01:00, by day of the week.
        int[][] grid = new int[7][9];
        for (int i = 0; i < 7; i++) {
            JSONObject d = week.get(i);
            int row = today.minusDays(6 - i).getDayOfWeek().getValue() - 1;
            JSONArray hh = d.optJSONArray("h");
            for (int k = 0; k < 7; k++) grid[row][k] += hh == null ? 0 : hh.optInt(17 + k);
            JSONArray next = i < 6 ? week.get(i + 1).optJSONArray("h") : null;
            for (int k = 0; k < 2; k++) grid[row][7 + k] += next == null ? 0 : next.optInt(k);
        }
        LinearLayout when = card(v, 1256, 150, 568, 300);
        int[] busy = busiest(grid);
        LinearLayout head = new LinearLayout(c);
        head.addView(label("WHEN"), new LinearLayout.LayoutParams(0, -2, 1f));
        if (busy != null) {
            TextView b = h.text(14, semi, YELLOW);
            b.setText("Busiest: " + DayOfWeek.of(busy[0] + 1).getDisplayName(TextStyle.FULL, Locale.UK) + " "
                    + String.format(Locale.UK, "%02d:00–%02d:00", (17 + busy[1]) % 24, (19 + busy[1]) % 24));
            head.addView(b);
        }
        when.addView(head);
        when.addView(heatmap(grid), new LinearLayout.LayoutParams(-1, -1));

        // What: the films and series.
        List<JSONObject> titles = titles(week);
        FrameLayout whatBox = new FrameLayout(c);
        whatBox.setBackground(cardGround());
        place(v, whatBox, 96, 470, 1728, 236);
        TextView wl = label("WHAT");
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(-2, -2);
        wlp.leftMargin = h.px(28);
        wlp.topMargin = h.px(20);
        whatBox.addView(wl, wlp);
        LinearLayout what = new LinearLayout(c);
        FrameLayout.LayoutParams wp = new FrameLayout.LayoutParams(-1, -2);
        wp.leftMargin = h.px(28);
        wp.topMargin = h.px(58);
        whatBox.addView(what, wp);
        for (int i = 0; i < Math.min(5, titles.size()); i++) what.addView(titleTile(titles.get(i)), new LinearLayout.LayoutParams(h.px(330), -2));
        if (titles.isEmpty()) {
            TextView n = h.text(18, regular, 0xBFFFFFFF);
            n.setText("Live TV and clips only this week.");
            what.addView(n);
        }

        // Habits.
        LinearLayout chips = new LinearLayout(c);
        place(v, chips, 96, 730, 1728, 56);
        for (String s : habits(today, week, perDay, sum)) chips.addView(chip(s), chipParams());
        TextView foot = h.text(15, regular, 0x99FFFFFF);
        foot.setText("Counted on this TV from what plays in your apps and on live TV. It never leaves the TV.");
        place(v, foot, 96, 1000, 1500, 30);
        return v;
    }

    /** The seven days' films and series, most watched first: {name, s, id, tv, eps}. */
    private List<JSONObject> titles(List<JSONObject> days) {
        Map<String, JSONObject> all = new LinkedHashMap<>();
        for (JSONObject d : days) {
            JSONObject t = d.optJSONObject("t");
            for (Iterator<String> it = t == null ? null : t.keys(); it != null && it.hasNext(); ) {
                String k = it.next();
                if (k.startsWith(ViewingLog.LIVE) || k.startsWith("app:")) continue;
                JSONObject one = t.optJSONObject(k);
                try {
                    JSONObject sum = all.get(k);
                    if (sum == null) all.put(k, sum = new JSONObject(one.toString()).put("eps", new JSONArray()).put("s", 0));
                    sum.put("s", sum.optInt("s") + one.optInt("s"));
                    JSONArray e = one.optJSONArray("e"), into = sum.getJSONArray("eps");
                    for (int i = 0; e != null && i < e.length(); i++) {
                        boolean has = false;
                        for (int j = 0; j < into.length(); j++) if (into.optString(j).equals(e.optString(i))) { has = true; break; }
                        if (!has) into.put(e.optString(i));
                    }
                } catch (Exception ignored) {
                    // that one left out
                }
            }
        }
        List<JSONObject> out = new ArrayList<>(all.values());
        out.sort((x, y) -> y.optInt("s") - x.optInt("s"));
        return out;
    }

    private View titleTile(JSONObject t) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView poster = new ImageView(c);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(h.fill(10, 0xFF0B0F55));
        poster.setClipToOutline(true);
        poster.setOutlineProvider(h.round(10));
        Bitmap p = posters.get(t.optInt("id"));
        if (p != null) poster.setImageBitmap(p);
        row.addView(poster, new LinearLayout.LayoutParams(h.px(100), h.px(150)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = h.text(17, semi, Color.WHITE);
        name.setText(t.optString("n"));
        name.setMaxLines(2);
        col.addView(name);
        TextView time = h.text(24, heavy, YELLOW);
        time.setText(hm(t.optInt("s")));
        col.addView(time);
        int eps = t.optJSONArray("eps") == null ? 0 : t.optJSONArray("eps").length();
        if (eps > 0) {
            TextView e = h.text(13, regular, 0xBFFFFFFF);
            e.setText(eps + (eps == 1 ? " episode" : " episodes"));
            col.addView(e);
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(h.px(200), -2);
        cl.leftMargin = h.px(14);
        row.addView(col, cl);
        return row;
    }

    /** A film's or series' poster (TMDB), at poster size; null when unknown. Off the main thread. */
    Bitmap poster(int id, boolean tv) {
        if (id <= 0 || h.tmdbKey.isEmpty()) return null;
        for (WatchHistory.Entry e : WatchHistory.load(c)) {
            if (e.tmdbId == id && e.poster != null) return ScoresTicker.cachedLogo(c, e.poster.replace("/w342/", "/w185/"), h.px(200));
        }
        try {
            String p = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id + "?api_key=" + h.tmdbKey))
                    .optString("poster_path", "");
            return p.isEmpty() || "null".equals(p) ? null : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w185" + p, h.px(200));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> habits(LocalDate today, List<JSONObject> week, int[] perDay, int sum) {
        List<String> out = new ArrayList<>();
        // Nights in a row with a quarter of an hour or more, up to today (or yesterday, early in the day).
        int streak = 0;
        for (int i = 0; i < 60; i++) {
            int s = total(ViewingLog.day(c, today.minusDays(i)));
            if (s >= 15 * 60) streak++;
            else if (i > 0) break;
        }
        if (streak >= 2) out.add("🔥  " + streak + " days in a row");
        int late = 0;
        for (int i = 0; i < 7; i++) {
            JSONArray hh = week.get(i).optJSONArray("h");
            for (int k = 21; k < 24; k++) late += hh == null ? 0 : hh.optInt(k);
            for (int k = 0; k < 3; k++) late += hh == null ? 0 : hh.optInt(k);
        }
        if (sum > 0) out.add("☾  " + Math.round(late * 100f / sum) + "% after 21:00");
        int best = 0;
        for (int i = 1; i < 7; i++) if (perDay[i] > perDay[best]) best = i;
        out.add("★  Biggest day: " + today.minusDays(6 - best).getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + ", " + hm(perDay[best]));
        int live = 0;
        for (JSONObject d : week) {
            JSONObject a = d.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                if (ViewingLog.live(p)) live += a.optInt(p);
            }
        }
        if (live > 0) out.add("📺  Live TV and sport: " + hm(live));
        return out;
    }

    // ---- pieces ------------------------------------------------------------------------------------------------

    private LinearLayout card(FrameLayout into, int x, int y, int w, int hgt) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(h.px(28), h.px(20), h.px(28), h.px(18));
        v.setBackground(cardGround());
        place(into, v, x, y, w, hgt);
        return v;
    }

    private GradientDrawable cardGround() {
        GradientDrawable g = h.fill(24, CARD);
        g.setStroke(Math.max(1, h.px(1)), EDGE);
        return g;
    }

    private TextView label(String s) {
        TextView t = h.text(15, semi, 0xCCFFFFFF);
        t.setText(s);
        t.setLetterSpacing(0.16f);
        return t;
    }

    private View appRow(String pkg, int secs, int top, boolean first) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, h.px(5), 0, h.px(5));
        FrameLayout mark = new FrameLayout(c);
        View logo = appMark(pkg);
        mark.addView(logo, new FrameLayout.LayoutParams(-2, h.px(26), Gravity.CENTER_VERTICAL));
        row.addView(mark, new LinearLayout.LayoutParams(h.px(150), h.px(30)));
        FrameLayout track = new FrameLayout(c);
        track.setBackground(h.fill(7, 0x24FFFFFF));
        View fill = new View(c);
        fill.setBackground(h.fill(7, first ? YELLOW : 0xBFFFFFFF));
        track.addView(fill, new FrameLayout.LayoutParams(Math.max(h.px(6), Math.round(h.px(230) * secs / (float) top)), -1));
        row.addView(track, new LinearLayout.LayoutParams(h.px(230), h.px(14)));
        TextView t = h.text(17, semi, Color.WHITE);
        t.setText(hm(secs));
        t.setGravity(Gravity.END);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return row;
    }

    /** An app's mark: the service's own logo (Netflix, Prime Video …), LIVE TV, else its icon and name. */
    View appMark(String pkg) {
        if (ViewingLog.LIVE.equals(pkg)) {
            TextView t = h.text(13, semi, Color.WHITE);
            t.setText("LIVE TV");
            t.setLetterSpacing(0.08f);
            t.setPadding(h.px(8), h.px(3), h.px(8), h.px(3));
            t.setBackground(h.fill(5, 0xFFE8112D));
            return t;
        }
        for (String pid : PROVIDERS) {
            if (pkg.equals(HomeV2.serviceApp(pid)) && HomeV2.serviceLogo(pid) != 0) {
                ImageView i = new ImageView(c);
                i.setImageResource(HomeV2.serviceLogo(pid));
                i.setAdjustViewBounds(true);
                i.setScaleType(ImageView.ScaleType.FIT_START);
                return i;
            }
        }
        LinearLayout v = new LinearLayout(c);
        v.setGravity(Gravity.CENTER_VERTICAL);
        PackageManager pm = c.getPackageManager();
        try {
            Drawable icon = pm.getApplicationIcon(pkg);
            ImageView i = new ImageView(c);
            i.setImageDrawable(icon);
            v.addView(i, new LinearLayout.LayoutParams(h.px(24), h.px(24)));
        } catch (Exception ignored) {
            // no icon
        }
        TextView t = h.text(15, semi, Color.WHITE);
        try {
            t.setText(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            t.setText(pkg);
        }
        t.setSingleLine(true);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = h.px(8);
        v.addView(t, tl);
        return v;
    }

    private View heatmap(int[][] grid) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        int most = 1;
        for (int[] r : grid) for (int x : r) most = Math.max(most, x);
        for (int r = 0; r < 7; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView d = h.text(12, semi, 0xBFFFFFFF);
            d.setText(DayOfWeek.of(r + 1).getDisplayName(TextStyle.SHORT, Locale.UK));
            row.addView(d, new LinearLayout.LayoutParams(h.px(40), -2));
            for (int k = 0; k < 9; k++) {
                float f = grid[r][k] / (float) most;
                View cell = new View(c);
                int colour = f <= 0.01f ? 0x12FFFFFF : f < 0.25f ? 0x33FFFFFF : f < 0.5f ? 0x73FFD04D : f < 0.75f ? 0xBFFFD04D : YELLOW;
                cell.setBackground(h.fill(5, colour));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, h.px(22), 1f);
                cl.leftMargin = h.px(5);
                row.addView(cell, cl);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = h.px(r == 0 ? 12 : 5);
            v.addView(row, rl);
        }
        LinearLayout hours = new LinearLayout(c);
        View pad = new View(c);
        hours.addView(pad, new LinearLayout.LayoutParams(h.px(40), 1));
        for (int k = 0; k < 9; k++) {
            TextView t = h.text(11, regular, 0x99FFFFFF);
            t.setText(String.format(Locale.UK, "%02d", (17 + k) % 24));
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            tl.leftMargin = h.px(5);
            hours.addView(t, tl);
        }
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = h.px(6);
        v.addView(hours, hl);
        return v;
    }

    /** {day row, first hour column} of the busiest two hours; null when nothing. */
    private static int[] busiest(int[][] grid) {
        int best = 0;
        int[] at = null;
        for (int r = 0; r < 7; r++) for (int k = 0; k < 8; k++) {
            int s = grid[r][k] + grid[r][k + 1];
            if (s > best) { best = s; at = new int[]{r, k}; }
        }
        return at;
    }

    TextView chip(String s) {
        TextView t = h.text(18, semi, Color.WHITE);
        t.setText(s);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(h.px(20), 0, h.px(20), 0);
        GradientDrawable g = h.fill(25, 0x1FFFFFFF);
        g.setStroke(Math.max(1, h.px(1)), 0x38FFFFFF);
        t.setBackground(g);
        return t;
    }

    LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(50));
        lp.rightMargin = h.px(12);
        return lp;
    }

    void place(FrameLayout parent, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        parent.addView(v, p);
    }

    /** "14h 20m", "45m". */
    static String hm(int secs) {
        int m = Math.round(secs / 60f);
        return m >= 60 ? m / 60 + "h " + String.format(Locale.UK, "%02d", m % 60) + "m" : m + "m";
    }

    /** "3:05", "45m" for the bars. */
    private static String hmShort(int secs) {
        int m = Math.round(secs / 60f);
        return m >= 60 ? m / 60 + ":" + String.format(Locale.UK, "%02d", m % 60) : m + "m";
    }
}
