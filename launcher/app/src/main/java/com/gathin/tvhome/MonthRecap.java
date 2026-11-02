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
 * Your month on TV, as a short animated story (the card on the 1st of the month, or Your viewing): a page every five
 * seconds, each with its own colour, numbers counting up and pictures sliding in: the hours watched, the apps, the
 * top films and series with their posters, when you watch (the hour and the day of the week), the biggest day, then
 * OK for Your viewing's full month. Right or OK moves on, Left goes back, Back closes. From the viewing log (ViewingLog).
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
        String month;
        int total, days, bestHour = -1, bestDaySecs;
        LocalDate bestDay;
        DayOfWeek bestWeekday;
        final int[] hours = new int[24];
        final List<Object[]> apps = new ArrayList<>();     // {pkg, secs, name, icon}
        final List<Object[]> titles = new ArrayList<>();   // {name, secs, poster bitmap or null, episodes}
    }

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
                    if (sum == null) titles.put(k, sum = new JSONObject().put("n", one.optString("n")).put("id", one.optInt("id")).put("tv", one.optBoolean("tv")));
                    sum.put("s", sum.optInt("s") + one.optInt("s"));
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
            if (f.apps.size() == 3) break;
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
        List<JSONObject> tl = new ArrayList<>(titles.values());
        tl.sort((x, y) -> y.optInt("s") - x.optInt("s"));
        for (JSONObject t : tl) {
            if (f.titles.size() == 3) break;
            Bitmap poster = null;
            int id = t.optInt("id");
            if (id > 0 && !h.tmdbKey.isEmpty()) {
                try {
                    JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (t.optBoolean("tv") ? "tv/" : "movie/") + id
                            + "?api_key=" + h.tmdbKey));
                    String p = d.optString("poster_path");
                    if (!p.isEmpty() && !"null".equals(p)) poster = ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w342" + p, h.px(300));
                } catch (Exception ignored) {
                    // the name without its poster
                }
            }
            f.titles.add(new Object[]{t.optString("n"), t.optInt("s"), poster, t.optInt("eps")});
        }
        return f;
    }

    // ---- the pages -------------------------------------------------------------------------------------------------

    private void build(Facts f) {
        if (f.total <= 0) {
            pages.add(() -> {
                FrameLayout p = page(0);
                big(p, "Your " + f.month, 220, 96);
                line(p, "Nothing watched here in " + f.month + " yet. See you next month!", 380, 36, 0xE6FFFFFF);
            });
        } else {
            pages.add(() -> {
                FrameLayout p = page(0);
                line(p, "YOUR MONTH ON TV", 300, 26, 0xFFFFD04D).setLetterSpacing(0.3f);
                TextView t = big(p, "Your " + f.month, 360, 130);
                pop(t, 200);
                line(p, "A look back at what you watched", 540, 36, 0xCCFFFFFF);
            });
            pages.add(() -> {
                FrameLayout p = page(1);
                line(p, "You watched", 260, 40, 0xE6FFFFFF);
                TextView n = big(p, "0h", 330, 170);
                countUp(n, f.total);
                line(p, "across " + f.days + (f.days == 1 ? " day" : " days") + " in " + f.month, 560, 38, 0xE6FFFFFF);
            });
            if (!f.apps.isEmpty()) pages.add(() -> {
                FrameLayout p = page(2);
                line(p, "Your top apps", 170, 56, Color.WHITE).setTypeface(heavy);
                int max = (Integer) f.apps.get(0)[1];
                for (int i = 0; i < f.apps.size(); i++) appRow(p, f.apps.get(i), max, 330 + i * 190, i);
            });
            if (!f.titles.isEmpty()) pages.add(() -> {
                FrameLayout p = page(3);
                line(p, "What you watched most", 120, 56, Color.WHITE).setTypeface(heavy);
                int n = f.titles.size();
                for (int i = 0; i < n; i++) poster(p, f.titles.get(i), i, n);
            });
            if (f.bestHour >= 0) pages.add(() -> {
                FrameLayout p = page(4);
                line(p, "Your TV time", 170, 40, 0xE6FFFFFF);
                big(p, "Most of it at " + hour(f.bestHour), 230, 104);
                line(p, f.bestWeekday == null ? "" : f.bestWeekday.getDisplayName(TextStyle.FULL, Locale.UK) + "s were your biggest nights",
                        390, 38, 0xE6FFFFFF);
                hourBars(p, f.hours, f.bestHour);
            });
            if (f.bestDay != null) pages.add(() -> {
                FrameLayout p = page(5);
                line(p, "Your biggest day", 280, 40, 0xE6FFFFFF);
                TextView d = big(p, f.bestDay.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.UK) + " " + f.bestDay.getDayOfMonth() + " "
                        + f.month, 350, 110);
                pop(d, 150);
                line(p, ViewingPanel.hm(f.bestDaySecs) + " in front of the TV", 530, 44, 0xFFFFE27A);
            });
            pages.add(() -> {
                FrameLayout p = page(6);
                TextView t = big(p, "That was your " + f.month, 330, 100);
                pop(t, 100);
                line(p, "OK for the whole month in Your viewing", 500, 36, 0xE6FFFFFF);
            });
        }
        int n = pages.size();
        LinearLayout row = new LinearLayout(h.a);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < n; i++) {
            View d = new View(h.a);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(h.px(4));
            g.setColor(0x66FFFFFF);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(h.px(1600f / Math.max(n, 1) - 12), h.px(6));
            lp.leftMargin = h.px(6);
            lp.rightMargin = h.px(6);
            row.addView(d, lp);
            dots.add(d);
        }
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-1, h.px(6), Gravity.TOP);
        rlp.topMargin = h.px(48);
        overlay.addView(row, rlp);
        go(1);
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
            g.setCornerRadius(h.px(4));
            g.setColor(i <= at ? 0xFFFFFFFF : 0x40FFFFFF);
            dots.get(i).setBackground(g);
        }
        if (at < pages.size() - 1) MAIN.postDelayed(auto, PAGE_MS);
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
        t.setText(s);
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

    /** "48h 20m", counting up from nothing over a second and a half. */
    private void countUp(TextView t, int secs) {
        ValueAnimator a = ValueAnimator.ofInt(0, secs);
        a.setDuration(1600);
        a.setStartDelay(300);
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(v -> t.setText(ViewingPanel.hm((Integer) v.getAnimatedValue())));
        a.start();
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
