package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Your month on TV, as a short animated story (the card on the 1st of the month, or Your viewing). It opens on the
 * mock-up's summary (the hours in huge type against the month before, the top show ringed in gold with numbers 2 to 5,
 * the TV personality, the month's biggest day, live TV, films and top app), held for 14 seconds; then a page every
 * five seconds, each with its own colour, numbers counting up and pictures sliding in: the apps, the top films and
 * series with their posters, when you watch (the hour and the day of the week), the biggest day, then OK for Your
 * viewing's full month. Dashes at the foot show where you are and what comes next. Right or OK moves on, Left goes
 * back, Back closes. From the viewing log (ViewingLog).
 */
final class MonthRecap {
    private MonthRecap() { }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long PAGE_MS = 5_000L;
    private static final int[][] COLOURS = {{0xFF10157E, 0xFF5B3FC4}, {0xFF0F6B4F, 0xFF1E9A86}, {0xFFB0306A, 0xFFE0457B},
            {0xFF1F5FBF, 0xFF4CC9F0}, {0xFF7A4A1F, 0xFFFFB238}, {0xFF5B3FC4, 0xFFFF5E5B}, {0xFF10157E, 0xFF2838C0}};

    private static MonthRecap shown;
    private HomeV2 h;
    private FrameLayout overlay, stage;
    private final List<Runnable> pages = new ArrayList<>();
    private int at = -1;
    private Typeface heavy, semi, regular;
    private final Runnable auto = () -> go(1);
    private final List<View> dots = new ArrayList<>();
    /** Each page's name, for "▶ Your apps" beside the dashes. */
    private final List<String> names = new ArrayList<>();
    private TextView nextName;

    static boolean isOpen() {
        return shown != null;
    }

    static boolean key(KeyEvent e) {
        return shown != null && shown.onKey(e);
    }

    static void close() {
        if (shown != null) shown.hide();
    }

    /** Last month's story. */
    static void open(HomeV2 h) {
        open(h, LocalDate.now().minusMonths(1));
    }

    static void open(HomeV2 h, LocalDate month) {
        close();
        MonthRecap r = new MonthRecap();
        shown = r;
        r.h = h;
        Context c = h.a;
        r.heavy = c.getResources().getFont(R.font.outfit_extrabold);
        r.semi = c.getResources().getFont(R.font.inter_semibold);
        r.regular = c.getResources().getFont(R.font.inter_regular);
        r.overlay = new FrameLayout(c);
        r.overlay.setBackgroundColor(0xFF060928);
        r.overlay.setClickable(true);
        r.stage = new FrameLayout(c);
        r.overlay.addView(r.stage, new FrameLayout.LayoutParams(-1, -1));
        h.root.addView(r.overlay, new FrameLayout.LayoutParams(-1, -1));
        r.overlay.setAlpha(0f);
        r.overlay.animate().alpha(1f).setDuration(300).start();
        IO.execute(() -> {
            Facts f = read(h, month);
            MAIN.post(() -> { if (shown == r) r.build(f); });
        });
    }

    // ---- the month in numbers ------------------------------------------------------------------------------------

    private static final class Facts {
        String month, before;
        int total, days, bestHour = -1, bestDaySecs, prevTotal, liveSecs, films, longestFilmSecs, daysInMonth;
        String longestFilm, persona, personaWhy;
        LocalDate bestDay;
        DayOfWeek bestWeekday;
        final int[] hours = new int[24];
        final List<Object[]> apps = new ArrayList<>();     // {pkg, secs, name, icon}
        /** {name, secs, poster bitmap or null, episodes, days watched, app, series}, the most watched first (five). */
        final List<Object[]> titles = new ArrayList<>();
        /** The top one's picture, faded in from the left (the summary's right-hand side). */
        Bitmap topBack;
    }

    /** Genre groups for the TV personality: {TMDB genre ids, what they are called in the line, the personality's noun}. */
    private static final Object[][] GROUPS = {
            {new int[]{80, 9648, 53}, "Crime and thrillers", "Detective"},
            {new int[]{878, 10765, 14}, "Science fiction and fantasy", "Explorer"},
            {new int[]{28, 12, 10759, 10752, 37}, "Action and adventure", "Thrill-Seeker"},
            {new int[]{35}, "Comedies", "Laugh Collector"},
            {new int[]{18}, "Dramas", "Drama Devotee"},
            {new int[]{16, 10751, 10762}, "Family films and cartoons", "Family Captain"},
            {new int[]{27}, "Horror", "Horror Hound"},
            {new int[]{99, 36}, "Documentaries and history", "Fact-Finder"},
            {new int[]{10749}, "Love stories", "Romantic"},
            {new int[]{10764, 10767}, "Reality and chat shows", "People-Watcher"}};

    private static Facts read(HomeV2 h, LocalDate any) {
        Context c = h.a;
        Facts f = new Facts();
        LocalDate first = any.withDayOfMonth(1);
        f.month = ViewingPanel.monthName(first);
        Map<String, Integer> apps = new HashMap<>();
        Map<String, JSONObject> titles = new HashMap<>();
        int[] weekdays = new int[7];
        for (LocalDate d = first; d.getMonthValue() == first.getMonthValue(); d = d.plusDays(1)) {
            JSONObject day = ViewingLog.day(c, d);
            int secs = ViewingPanel.total(day);
            if (secs <= 0) continue;
            f.total += secs;
            f.days++;
            weekdays[d.getDayOfWeek().getValue() - 1] += secs;
            if (secs > f.bestDaySecs) { f.bestDaySecs = secs; f.bestDay = d; }
            JSONObject a = day.optJSONObject("a");
            for (Iterator<String> it = a == null ? null : a.keys(); it != null && it.hasNext(); ) {
                String p = it.next();
                apps.merge(p, a.optInt(p), Integer::sum);
            }
            JSONArray hrs = day.optJSONArray("h");
            for (int i = 0; hrs != null && i < 24; i++) f.hours[i] += hrs.optInt(i);
            JSONObject t = day.optJSONObject("t");
            for (Iterator<String> it = t == null ? null : t.keys(); it != null && it.hasNext(); ) {
                String k = it.next();
                JSONObject one = t.optJSONObject(k);
                if (one == null || k.startsWith("x:") && one.optInt("s") < 20 * 60) continue;
                JSONObject sum = titles.get(k);
                try {
                    if (sum == null) titles.put(k, sum = new JSONObject().put("n", one.optString("n")).put("id", one.optInt("id")).put("tv", one.optBoolean("tv"))
                            .put("p", one.optString("p")));
                    sum.put("s", sum.optInt("s") + one.optInt("s"));
                    sum.put("days", sum.optInt("days") + 1);
                    JSONArray e = one.optJSONArray("e");
                    sum.put("eps", sum.optInt("eps") + (e == null ? 0 : e.length()));
                } catch (Exception ignored) {
                    // one title less
                }
            }
        }
        int best = 0;
        for (int i = 0; i < 24; i++) if (f.hours[i] > best) { best = f.hours[i]; f.bestHour = i; }
        int bw = 0;
        for (int i = 0; i < 7; i++) if (weekdays[i] > bw) { bw = weekdays[i]; f.bestWeekday = DayOfWeek.of(i + 1); }
        List<Map.Entry<String, Integer>> appList = new ArrayList<>(apps.entrySet());
        appList.sort((x, y) -> y.getValue() - x.getValue());
        for (Map.Entry<String, Integer> e : appList) {
            if (ViewingLog.live(e.getKey())) f.liveSecs += e.getValue();
            if (f.apps.size() == 3) continue;
            String name;
            Drawable icon = null;
            try {
                name = c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(e.getKey(), 0)).toString();
                icon = c.getPackageManager().getApplicationIcon(e.getKey());
            } catch (Exception ex) {
                continue;   // gone from the TV
            }
            f.apps.add(new Object[]{e.getKey(), e.getValue(), name, icon});
        }
        // The month before, for "9 more than September".
        LocalDate prev = first.minusMonths(1);
        f.before = ViewingPanel.monthName(prev);
        for (LocalDate d = prev; d.getMonthValue() == prev.getMonthValue(); d = d.plusDays(1)) f.prevTotal += ViewingPanel.total(ViewingLog.day(c, d));
        f.daysInMonth = first.lengthOfMonth();
        List<JSONObject> tl = new ArrayList<>(titles.values());
        tl.sort((x, y) -> y.optInt("s") - x.optInt("s"));
        for (JSONObject t : tl) {
            if (!t.optBoolean("tv") && t.optInt("id") > 0 && t.optInt("s") >= 20 * 60) {
                f.films++;
                if (t.optInt("s") > f.longestFilmSecs) { f.longestFilmSecs = t.optInt("s"); f.longestFilm = t.optString("n"); }
            }
        }
        Map<Integer, Integer> bySecs = new HashMap<>();
        for (JSONObject t : tl) {
            if (f.titles.size() == 5) break;
            Bitmap poster = null;
            int id = t.optInt("id");
            if (id > 0 && !h.tmdbKey.isEmpty()) {
                try {
                    JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (t.optBoolean("tv") ? "tv/" : "movie/") + id
                            + "?api_key=" + h.tmdbKey));
                    String p = d.optString("poster_path");
                    if (!p.isEmpty() && !"null".equals(p)) poster = ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w342" + p, h.px(300));
                    JSONArray g = d.optJSONArray("genres");
                    for (int i = 0; g != null && i < g.length(); i++) bySecs.merge(g.optJSONObject(i).optInt("id"), t.optInt("s"), Integer::sum);
                    String b = d.optString("backdrop_path");
                    if (f.titles.isEmpty() && !b.isEmpty() && !"null".equals(b)) {
                        f.topBack = fadeIn(ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w780" + b, h.px(1100)));
                    }
                } catch (Exception ignored) {
                    // the name without its poster
                }
            }
            f.titles.add(new Object[]{t.optString("n"), t.optInt("s"), poster, t.optInt("eps"), t.optInt("days"), t.optString("p"), t.optBoolean("tv")});
        }
        persona(f, bySecs, weekdays);
        return f;
    }

    /**
     * The TV personality, from the month itself: the genre group with the most hours ("Detective" for crime and
     * thrillers), or live TV and sport when that was most of it, with when you watch in front ("Late-Night" when half
     * of it was after 9pm, "Weekend" when the weekends were, "Early-Bird" for mornings), and the line saying why.
     */
    private static void persona(Facts f, Map<Integer, Integer> bySecs, int[] weekdays) {
        if (f.total <= 0) return;
        int best = 0;
        Object[] group = null;
        for (Object[] g : GROUPS) {
            int sum = 0;
            for (int id : (int[]) g[0]) sum += bySecs.getOrDefault(id, 0);
            if (sum > best) { best = sum; group = g; }
        }
        int late = 0, morning = 0;
        for (int i = 0; i < 24; i++) {
            if (i >= 21 || i < 4) late += f.hours[i];
            if (i >= 6 && i < 12) morning += f.hours[i];
        }
        float lateShare = late / (float) f.total, weekend = (weekdays[5] + weekdays[6]) / (float) f.total;
        String when = lateShare >= 0.5f ? "Late-Night" : weekend >= 0.5f ? "Weekend" : morning / (float) f.total >= 0.3f ? "Early-Bird" : "Everyday";
        String what;
        String share;
        if (f.liveSecs > best) {
            what = "Live-Sport Fan";
            share = "Live TV and sport were " + part(f.liveSecs / (float) f.total) + " of your month";
        } else if (group != null) {
            what = (String) group[2];
            share = group[1] + " were " + part(Math.min(1f, best / (float) f.total)) + " of your month";
        } else {
            return;
        }
        f.persona = "The " + when + " " + what;
        f.personaWhy = share + (lateShare >= 0.2f ? ", and " + ratio(lateShare) + " hours were after 9pm." : ".");
    }

    /** A share in words: "more than half", "about a third" … */
    private static String part(float x) {
        if (x >= 0.75f) return "most";
        if (x >= 0.5f) return "more than half";
        if (x >= 0.3f) return "about a third";
        if (x >= 0.2f) return "about a quarter";
        return "the biggest part";
    }

    /** A share as the nearest simple "2 in 3". */
    private static String ratio(float x) {
        int[][] r = {{1, 5}, {1, 4}, {1, 3}, {2, 5}, {1, 2}, {3, 5}, {2, 3}, {3, 4}, {4, 5}, {9, 10}};
        int[] best = r[0];
        for (int[] q : r) if (Math.abs(q[0] / (float) q[1] - x) < Math.abs(best[0] / (float) best[1] - x)) best = q;
        return best[0] + " in " + best[1];
    }

    /** The picture made see-through on its left half (the mock-up's mask), so it melts into the page. */
    private static Bitmap fadeIn(Bitmap b) {
        if (b == null) return null;
        try {
            Bitmap out = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(out);
            cv.drawBitmap(b, 0, 0, null);
            android.graphics.Paint p = new android.graphics.Paint();
            p.setShader(new android.graphics.LinearGradient(0, 0, b.getWidth() / 2f, 0, 0x00000000, 0xFF000000, android.graphics.Shader.TileMode.CLAMP));
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            cv.drawRect(0, 0, b.getWidth(), b.getHeight(), p);
            return out;
        } catch (OutOfMemoryError | RuntimeException e) {
            return b;
        }
    }

    // ---- the pages -------------------------------------------------------------------------------------------------

    private void build(Facts f) {
        if (f.total <= 0) {
            add("", () -> {
                FrameLayout p = page(0);
                big(p, "Your " + f.month, 220, 96);
                line(p, "Nothing watched here in " + f.month + " yet. See you next month!", 380, 36, 0xE6FFFFFF);
            });
        } else {
            add("Your " + f.month, () -> summary(f));
            if (!f.apps.isEmpty()) add("Your apps", () -> {
                FrameLayout p = page(2);
                line(p, "Your top apps", 170, 56, Color.WHITE).setTypeface(heavy);
                int max = (Integer) f.apps.get(0)[1];
                for (int i = 0; i < f.apps.size(); i++) appRow(p, f.apps.get(i), max, 330 + i * 190, i);
            });
            if (!f.titles.isEmpty()) add("What you watched most", () -> {
                FrameLayout p = page(3);
                line(p, "What you watched most", 120, 56, Color.WHITE).setTypeface(heavy);
                int n = Math.min(3, f.titles.size());
                for (int i = 0; i < n; i++) poster(p, f.titles.get(i), i, n);
            });
            if (f.bestHour >= 0) add("Your TV time", () -> {
                FrameLayout p = page(4);
                line(p, "Your TV time", 170, 40, 0xE6FFFFFF);
                big(p, "Most of it at " + hour(f.bestHour), 230, 104);
                line(p, f.bestWeekday == null ? "" : f.bestWeekday.getDisplayName(TextStyle.FULL, Locale.UK) + "s were your biggest nights",
                        390, 38, 0xE6FFFFFF);
                hourBars(p, f.hours, f.bestHour);
            });
            if (f.bestDay != null) add("Your biggest day", () -> {
                FrameLayout p = page(5);
                line(p, "Your biggest day", 280, 40, 0xE6FFFFFF);
                TextView d = big(p, f.bestDay.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + " " + f.bestDay.getDayOfMonth() + " "
                        + f.month, 350, 110);
                pop(d, 150);
                line(p, ViewingPanel.hm(f.bestDaySecs) + " in front of the TV", 530, 44, 0xFFFFE27A);
            });
            add("Your viewing", () -> {
                FrameLayout p = page(6);
                TextView t = big(p, "That was your " + f.month, 330, 100);
                pop(t, 100);
                line(p, "OK for the whole month in Your viewing", 500, 36, 0xE6FFFFFF);
            });
        }
        // As the mock-up: a dash for each page at the foot on the right (the one showing long), and what comes next.
        int n = pages.size();
        LinearLayout row = new LinearLayout(h.a);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        for (int i = 0; i < n; i++) {
            View d = new View(h.a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.px(12), h.px(6));
            lp.leftMargin = h.px(10);
            row.addView(d, lp);
            dots.add(d);
        }
        nextName = h.text(17, semi, 0xBFFFFFFF);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.leftMargin = h.px(20);
        row.addView(nextName, nl);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, h.px(30), Gravity.BOTTOM | Gravity.END);
        rlp.rightMargin = h.px(110);
        rlp.bottomMargin = h.px(48);
        overlay.addView(row, rlp);
        go(1);
    }

    private void add(String name, Runnable page) {
        names.add(name);
        pages.add(page);
    }

    /**
     * The mock-up's page: YOUR OCTOBER ON TV over the hours in huge type and how that compares with the month before;
     * the top show's poster ringed in gold (#1) with its episodes, days, time and app, then numbers 2 to 5 with their
     * posters; the TV personality on a white card; the month's biggest day, live TV, films and top app along the foot;
     * the top show's picture melting in on the right and two glows, pink and blue.
     */
    private void summary(Facts f) {
        FrameLayout p = new FrameLayout(h.a);
        p.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A0F6E, 0xFF2A1AA0, 0xFF10156E}));
        stage.addView(p, new FrameLayout.LayoutParams(-1, -1));
        p.setAlpha(0f);
        p.animate().alpha(1f).setDuration(450).start();
        if (f.topBack != null) {
            ImageView back = new ImageView(h.a);
            back.setImageBitmap(f.topBack);
            back.setScaleType(ImageView.ScaleType.CENTER_CROP);
            back.setAlpha(0.55f);
            put(p, back, 820, 0, 1100, 1080);
            back.setScaleX(1.08f);
            back.setScaleY(1.08f);
            back.animate().scaleX(1f).scaleY(1f).setDuration(PAGE_MS * 2).start();
        }
        put(p, glow(0x8CFF4FA3), -240, -260, 900, 900);
        put(p, glow(0x804F8BFF), 420, 600, 1100, 900);
        TextView label = h.text(18, heavy, 0xFFFFD04D);
        label.setText("YOUR " + f.month.toUpperCase(Locale.UK) + " ON TV");
        label.setLetterSpacing(0.28f);
        put(p, label, 120, 90, 1100, 26);
        Typeface black = h.a.getResources().getFont(R.font.outfit_black);
        TextView hours = h.text(200, black, Color.WHITE);
        hours.setLetterSpacing(-0.04f);
        hours.setSingleLine(true);
        put(p, hours, 112, 112, 1200, 236);
        int total = f.total;
        ValueAnimator count = ValueAnimator.ofInt(0, total);
        count.setDuration(1600);
        count.setStartDelay(250);
        count.setInterpolator(new DecelerateInterpolator(2f));
        count.addUpdateListener(v -> hours.setText(bigHours((Integer) v.getAnimatedValue())));
        hours.setText(bigHours(0));
        count.start();
        TextView than = h.text(30, regular, 0xEBFFFFFF);
        than.setText(compare(f));
        put(p, than, 120, 340, 1180, 44);
        rise(than, 500);
        // The top one, ringed in gold.
        if (!f.titles.isEmpty()) {
            Object[] top = f.titles.get(0);
            FrameLayout pic = new FrameLayout(h.a);
            pic.setBackground(h.fill(20, 0x33FFFFFF));
            pic.setClipToOutline(true);
            pic.setOutlineProvider(h.round(20));
            pic.setElevation(h.px(20));
            ImageView img = new ImageView(h.a);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (top[2] != null) img.setImageBitmap((Bitmap) top[2]);
            pic.addView(img, new FrameLayout.LayoutParams(-1, -1));
            TextView one = h.text(38, black, 0xFFFFD04D);
            one.setText("#1");
            one.setShadowLayer(h.px(10), 0, h.px(2), 0xB3000000);
            FrameLayout.LayoutParams ol = new FrameLayout.LayoutParams(-2, -2);
            ol.leftMargin = h.px(12);
            ol.topMargin = h.px(10);
            pic.addView(one, ol);
            MockKit.ring(h, pic, 20, 5, 0xFFFFD04D);
            put(p, pic, 120, 440, 250, 375);
            pic.setTranslationY(h.px(60));
            pic.setAlpha(0f);
            pic.animate().translationY(0).alpha(1f).setStartDelay(600).setDuration(650).setInterpolator(new OvershootInterpolator(1.1f)).start();
            LinearLayout col = new LinearLayout(h.a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM);
            boolean tv = Boolean.TRUE.equals(top[6]);
            TextView kind = h.text(16, heavy, 0xD9FFFFFF);
            kind.setText(tv ? "YOUR TOP SHOW" : "YOUR TOP FILM");
            kind.setLetterSpacing(0.19f);
            col.addView(kind);
            TextView name = h.text(58, heavy, Color.WHITE);
            name.setText((String) top[0]);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setLineSpacing(0, 1.0f);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
            nl.topMargin = h.px(8);
            col.addView(name, nl);
            int eps = (Integer) top[3], days = (Integer) top[4];
            TextView how = h.text(22, regular, 0xE6FFFFFF);
            how.setText((eps > 1 ? eps + " episodes in " + days + (days == 1 ? " day" : " days") + " · " : "") + ViewingPanel.hm((Integer) top[1]));
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
            hl.topMargin = h.px(8);
            col.addView(how, hl);
            View where = onApp(h.a, (String) top[5]);
            if (where != null) {
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, h.px(30));
                wl.topMargin = h.px(10);
                col.addView(where, wl);
            }
            put(p, col, 398, 440, 440, 369);
            rise(col, 800);
            // Numbers 2 to 5.
            LinearLayout list = new LinearLayout(h.a);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 1; i < f.titles.size(); i++) {
                Object[] t = f.titles.get(i);
                LinearLayout r = new LinearLayout(h.a);
                r.setGravity(Gravity.CENTER_VERTICAL);
                TextView rank = h.text(34, black, 0xD9FFFFFF);
                rank.setText(String.valueOf(i + 1));
                r.addView(rank, new LinearLayout.LayoutParams(h.px(44), -2));
                ImageView small = new ImageView(h.a);
                small.setScaleType(ImageView.ScaleType.CENTER_CROP);
                small.setBackground(h.fill(8, 0x33FFFFFF));
                small.setClipToOutline(true);
                small.setOutlineProvider(h.round(8));
                if (t[2] != null) small.setImageBitmap((Bitmap) t[2]);
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(h.px(60), h.px(90));
                sl.leftMargin = h.px(16);
                r.addView(small, sl);
                LinearLayout words = new LinearLayout(h.a);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView tn = h.text(21, semi, Color.WHITE);
                tn.setText((String) t[0]);
                tn.setSingleLine(true);
                tn.setEllipsize(android.text.TextUtils.TruncateAt.END);
                words.addView(tn, new LinearLayout.LayoutParams(h.px(330), -2));
                TextView tt = h.text(17, regular, 0xCCFFFFFF);
                tt.setText(ViewingPanel.hm((Integer) t[1]));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
                tl.topMargin = h.px(4);
                words.addView(tt, tl);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
                wl.leftMargin = h.px(16);
                r.addView(words, wl);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
                if (i > 1) rl.topMargin = h.px(14);
                list.addView(r, rl);
                r.setAlpha(0f);
                r.setTranslationX(h.px(40));
                r.animate().alpha(1f).translationX(0).setStartDelay(900 + i * 150L).setDuration(450).setInterpolator(new DecelerateInterpolator()).start();
            }
            put(p, list, 860, 440, 520, 420);
        }
        // The TV personality, on white.
        if (f.persona != null) {
            LinearLayout card = new LinearLayout(h.a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(h.fill(26, 0xF0FFFFFF));
            card.setElevation(h.px(24));
            card.setPadding(h.px(30), h.px(26), h.px(30), h.px(28));
            TextView cl = h.text(15, heavy, 0xFFFF2D7A);
            cl.setText("YOUR TV PERSONALITY");
            cl.setLetterSpacing(0.2f);
            card.addView(cl);
            TextView who = h.text(44, black, MockKit.NAVY);
            who.setText(f.persona);
            who.setLineSpacing(0, 1.02f);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.topMargin = h.px(10);
            card.addView(who, wl);
            TextView why = h.text(19, regular, 0xD9141C92);
            why.setText(f.personaWhy);
            why.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams yl = new LinearLayout.LayoutParams(-1, -2);
            yl.topMargin = h.px(12);
            card.addView(why, yl);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(470), -2);
            lp.leftMargin = h.px(1920 - 110 - 470);
            lp.topMargin = h.px(110);
            p.addView(card, lp);
            card.setAlpha(0f);
            card.setTranslationY(h.px(-30));
            card.setRotation(-2f);
            card.animate().alpha(1f).translationY(0).rotation(0).setStartDelay(1300).setDuration(600).setInterpolator(new OvershootInterpolator(1.2f)).start();
        }
        // Along the foot.
        LinearLayout foot = new LinearLayout(h.a);
        if (f.bestDay != null) stat(foot, "BIGGEST DAY", f.bestDay.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + " " + f.bestDay.getDayOfMonth() + " "
                + f.bestDay.getMonth().getDisplayName(TextStyle.SHORT, Locale.UK) + " · " + ViewingPanel.hm(f.bestDaySecs));
        if (f.liveSecs >= 600) stat(foot, "LIVE TV & SPORT", ViewingPanel.hm(f.liveSecs));
        if (f.films > 0) stat(foot, "FILMS", f.films + (f.longestFilm != null && f.films > 1 ? " · longest " + f.longestFilm + ", " + ViewingPanel.hm(f.longestFilmSecs) : ""));
        if (!f.apps.isEmpty()) stat(foot, "TOP APP", f.apps.get(0)[2] + " · " + ViewingPanel.hm((Integer) f.apps.get(0)[1]));
        else stat(foot, "DAYS WATCHED", f.days + " of " + f.daysInMonth);
        put(p, foot, 120, 900, 1500, 80);
        rise(foot, 1500);
    }

    private void stat(LinearLayout foot, String label, String value) {
        if (foot.getChildCount() == 4) return;
        LinearLayout col = new LinearLayout(h.a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = h.text(14, heavy, 0xB3FFFFFF);
        l.setText(label);
        l.setLetterSpacing(0.18f);
        col.addView(l);
        TextView v = h.text(22, semi, Color.WHITE);
        v.setText(value);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1, -2);
        vl.topMargin = h.px(6);
        col.addView(v, vl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.px(340), -2);
        if (foot.getChildCount() > 0) lp.leftMargin = h.px(30);
        foot.addView(col, lp);
    }

    /** "61 hours" (or "45 minutes" for a quiet month). */
    private static String bigHours(int secs) {
        int hrs = Math.round(secs / 3600f);
        return secs < 3600 ? Math.round(secs / 60f) + " mins" : hrs + (hrs == 1 ? " hour" : " hours");
    }

    /** "That's 2 days and 13 hours, 9 more than September." */
    private static String compare(Facts f) {
        int hrs = Math.round(f.total / 3600f);
        String that = hrs >= 24 ? "That's " + hrs / 24 + (hrs / 24 == 1 ? " day" : " days") + (hrs % 24 > 0 ? " and " + hrs % 24 + (hrs % 24 == 1 ? " hour" : " hours") : "")
                : "That's " + ViewingPanel.hm(f.total);
        if (f.prevTotal <= 0) return that + ".";
        int diff = hrs - Math.round(f.prevTotal / 3600f);
        return that + (diff == 0 ? ", the same as " + f.before + "." : ", " + Math.abs(diff) + (diff > 0 ? " more than " : " fewer than ") + f.before + ".");
    }

    /** "on" and the app's own logo (a cut-out one), or its name; null when it is gone. */
    private View onApp(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView on = h.text(18, regular, 0xD9FFFFFF);
        on.setText("on");
        r.addView(on);
        Object[] mark = AppLogos.logo(c, pkg);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, h.px(26));
        ml.leftMargin = h.px(10);
        if (mark != null && mark[0] != null && !Boolean.TRUE.equals(mark[1])) {
            ImageView m = new ImageView(c);
            m.setImageBitmap((Bitmap) mark[0]);
            m.setAdjustViewBounds(true);
            m.setScaleType(ImageView.ScaleType.FIT_CENTER);
            m.setMaxWidth(h.px(170));
            r.addView(m, ml);
        } else {
            try {
                TextView n = h.text(18, semi, Color.WHITE);
                n.setText(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0)));
                r.addView(n, new LinearLayout.LayoutParams(-2, -2));
                ((LinearLayout.LayoutParams) n.getLayoutParams()).leftMargin = h.px(8);
            } catch (Exception e) {
                return null;
            }
        }
        return r;
    }

    private View glow(int colour) {
        View v = new View(h.a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(h.px(480));
        g.setColors(new int[]{colour, colour & 0x00FFFFFF});
        v.setBackground(g);
        return v;
    }

    private void put(FrameLayout p, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        lp.leftMargin = h.px(x);
        lp.topMargin = h.px(y);
        p.addView(v, lp);
    }

    private void rise(View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(h.px(24));
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void go(int step) {
        int next = at + step;
        MAIN.removeCallbacks(auto);
        if (next < 0) return;
        if (next >= pages.size()) {
            hide();
            h.showViewing(1);
            return;
        }
        at = next;
        // The page before slides away; the new one is built and comes in.
        for (int i = 0; i < stage.getChildCount(); i++) {
            View old = stage.getChildAt(i);
            old.animate().alpha(0f).translationX(h.px(step > 0 ? -160 : 160)).setDuration(350).withEndAction(() -> stage.removeView(old)).start();
        }
        pages.get(at).run();
        for (int i = 0; i < dots.size(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(h.px(3));
            g.setColor(i == at ? 0xFFFFFFFF : 0x66FFFFFF);
            View d = dots.get(i);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = h.px(i == at ? 34 : 12);
            d.setLayoutParams(lp);
        }
        if (nextName != null) nextName.setText(at + 1 < names.size() && !names.get(at + 1).isEmpty() ? "▶  " + names.get(at + 1) : "");
        // The summary is a page to read: it stays longer.
        if (at < pages.size() - 1) MAIN.postDelayed(auto, at == 0 && names.size() > 1 ? 14_000L : PAGE_MS);
    }

    private FrameLayout page(int colour) {
        FrameLayout p = new FrameLayout(h.a);
        int[] c = COLOURS[colour % COLOURS.length];
        p.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, c));
        // Two soft discs of light drifting across the page.
        for (int i = 0; i < 2; i++) {
            View blob = new View(h.a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setGradientRadius(h.px(420));
            g.setColors(new int[]{0x40FFFFFF, 0x00FFFFFF});
            blob.setBackground(g);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(840), h.px(840));
            lp.leftMargin = h.px(i == 0 ? -200 : 1250);
            lp.topMargin = h.px(i == 0 ? -260 : 520);
            p.addView(blob, lp);
            blob.animate().translationX(h.px(i == 0 ? 180 : -200)).translationY(h.px(i == 0 ? 120 : -90)).setDuration(PAGE_MS + 1000).start();
        }
        stage.addView(p, new FrameLayout.LayoutParams(-1, -1));
        p.setAlpha(0f);
        p.setTranslationX(h.px(160));
        p.animate().alpha(1f).translationX(0).setDuration(450).setInterpolator(new DecelerateInterpolator()).start();
        return p;
    }

    private TextView big(FrameLayout p, String s, int y, int size) {
        TextView t = h.text(size, heavy, Color.WHITE);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.topMargin = h.px(y);
        p.addView(t, lp);
        return t;
    }

    private TextView line(FrameLayout p, String s, int y, int size, int colour) {
        TextView t = h.text(size, semi, colour);
        Keys.set(t, s);
        t.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.topMargin = h.px(y);
        lp.leftMargin = h.px(120);
        lp.rightMargin = h.px(120);
        p.addView(t, lp);
        t.setAlpha(0f);
        t.setTranslationY(h.px(24));
        t.animate().alpha(1f).translationY(0).setStartDelay(250).setDuration(500).start();
        return t;
    }

    private void pop(View v, long delay) {
        v.setScaleX(0.6f);
        v.setScaleY(0.6f);
        v.setAlpha(0f);
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(delay).setDuration(650).setInterpolator(new OvershootInterpolator(1.4f)).start();
    }

    private void appRow(FrameLayout p, Object[] app, int max, int y, int i) {
        LinearLayout row = new LinearLayout(h.a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(h.a);
        icon.setImageDrawable((Drawable) app[3]);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(h.px(120), h.px(120)));
        LinearLayout col = new LinearLayout(h.a);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = h.text(40, heavy, Color.WHITE);
        name.setText((String) app[2] + "   " + ViewingPanel.hm((Integer) app[1]));
        col.addView(name);
        View bar = new View(h.a);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(h.px(12));
        g.setColor(0xE6FFFFFF);
        bar.setBackground(g);
        int w = Math.max(h.px(40), Math.round(h.px(1100) * (Integer) app[1] / (float) Math.max(1, max)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(w, h.px(24));
        blp.topMargin = h.px(14);
        col.addView(bar, blp);
        bar.setPivotX(0);
        bar.setScaleX(0f);
        bar.animate().scaleX(1f).setStartDelay(400 + i * 250L).setDuration(900).setInterpolator(new DecelerateInterpolator()).start();
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.leftMargin = h.px(36);
        row.addView(col, clp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(1400), h.px(150), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = h.px(y);
        p.addView(row, lp);
        row.setAlpha(0f);
        row.setTranslationX(h.px(-60));
        row.animate().alpha(1f).translationX(0).setStartDelay(250 + i * 250L).setDuration(500).start();
    }

    private void poster(FrameLayout p, Object[] t, int i, int n) {
        int w = 300, gap = 90, total = n * w + (n - 1) * gap, x = (1920 - total) / 2 + i * (w + gap);
        LinearLayout col = new LinearLayout(h.a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView pic = new ImageView(h.a);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(h.px(22));
        bg.setColor(0x33FFFFFF);
        pic.setBackground(bg);
        pic.setClipToOutline(true);
        if (t[2] != null) pic.setImageBitmap((Bitmap) t[2]);
        col.addView(pic, new LinearLayout.LayoutParams(h.px(w), h.px(450)));
        TextView name = h.text(30, heavy, Color.WHITE);
        name.setText((i + 1) + ". " + t[0]);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(h.px(w + 60), -2);
        nlp.topMargin = h.px(18);
        col.addView(name, nlp);
        TextView sub = h.text(24, regular, 0xE6FFFFFF);
        int eps = (Integer) t[3];
        sub.setText(ViewingPanel.hm((Integer) t[1]) + (eps > 1 ? "  ·  " + eps + " episodes" : ""));
        sub.setGravity(Gravity.CENTER);
        col.addView(sub);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(w + 60), -2);
        lp.leftMargin = h.px(x - 30);
        lp.topMargin = h.px(260);
        p.addView(col, lp);
        col.setAlpha(0f);
        col.setTranslationY(h.px(80));
        col.setRotation(i % 2 == 0 ? -4f : 4f);
        col.animate().alpha(1f).translationY(0).rotation(0).setStartDelay(300 + i * 300L).setDuration(650)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    /** The day's 24 hours as bars growing up, the busiest one gold. */
    private void hourBars(FrameLayout p, int[] hours, int best) {
        int max = 1;
        for (int v : hours) max = Math.max(max, v);
        LinearLayout row = new LinearLayout(h.a);
        row.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 24; i++) {
            View b = new View(h.a);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(h.px(8));
            g.setColor(i == best ? 0xFFFFD04D : 0x99FFFFFF);
            b.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.px(44), Math.max(h.px(6), Math.round(h.px(360) * hours[i] / (float) max)));
            lp.leftMargin = h.px(6);
            lp.rightMargin = h.px(6);
            row.addView(b, lp);
            b.setPivotY(lp.height);
            b.setScaleY(0f);
            b.animate().scaleY(1f).setStartDelay(300 + i * 35L).setDuration(600).setInterpolator(new DecelerateInterpolator()).start();
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, h.px(380), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = h.px(520);
        p.addView(row, lp);
        LinearLayout labels = new LinearLayout(h.a);
        for (int i = 0; i < 24; i++) {
            TextView l = h.text(16, regular, 0xB3FFFFFF);
            l.setText(i % 3 == 0 ? hour(i) : "");
            l.setGravity(Gravity.CENTER);
            labels.addView(l, new LinearLayout.LayoutParams(h.px(56), -2));
        }
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        llp.topMargin = h.px(910);
        p.addView(labels, llp);
    }

    private static String hour(int h) {
        return h == 0 ? "midnight" : h == 12 ? "midday" : h < 12 ? h + "am" : (h - 12) + "pm";
    }

    private void hide() {
        if (shown == this) shown = null;
        MAIN.removeCallbacks(auto);
        View o = overlay;
        o.animate().alpha(0f).setDuration(250).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    private boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_UP || e.isCanceled()) return true;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok || code == KeyEvent.KEYCODE_DPAD_RIGHT) go(1);
        else if (code == KeyEvent.KEYCODE_DPAD_LEFT && at > 0) go(-1);
        return true;
    }
}
