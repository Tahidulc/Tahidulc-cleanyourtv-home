package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Movies' "Fits tonight" tab: films on the TV's own streaming apps (TMDB's UK providers for the apps installed) that
 * end before bed if started now, each poster saying when it would end. Bed is the theme's "bedtime" ("23:00" when not
 * set). In the day the films are up to three hours; late, with under 80 minutes to bed, the short ones (95 minutes).
 * As the mock-up has it: each poster with the service's logo in its corner, "Ends 22:38" large and "1h 46m · 22 min
 * spare" under it; over the hero's picture, the evening from now to bed with the film in focus along it.
 */
final class FitsTonight {
    private FitsTonight() { }

    /** TMDB's running times, by film, and the service on this TV it streams on ({minutes, TMDB provider id or 0}). */
    private static final Map<Integer, int[]> RUNTIMES = new ConcurrentHashMap<>();
    private static final int PINK = 0xFFFF4FA3, PINK_LIGHT = 0xFFFF7AB8, YELLOW = 0xFFFFD04D;
    private static final ExecutorService LOOKUPS = Executors.newFixedThreadPool(4);

    /** Bed, in minutes after midnight (the theme's "bedtime", 23:00 by default; 00:30 counts as 24:30). */
    static int bedMinutes(Context c) {
        String s = Config.themeString(c, "bedtime", "23:00").trim();
        try {
            String[] p = s.split(":");
            int m = Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0);
            return m < 5 * 60 ? m + 24 * 60 : m;
        } catch (RuntimeException e) {
            return 23 * 60;
        }
    }

    private static int nowMinutes() {
        int m = LocalTime.now().toSecondOfDay() / 60;
        return m < 5 * 60 ? m + 24 * 60 : m;
    }

    /** The longest film that fits now, in minutes. */
    static int window(Context c) {
        int left = bedMinutes(c) - nowMinutes();
        if (left < 80) return 95;
        return Math.min(left, 180);
    }

    /** "Ends 22:38", started now. */
    static String ends(int runtime) {
        int end = (LocalTime.now().toSecondOfDay() / 60 + runtime) % (24 * 60);
        return String.format(Locale.UK, "Ends %02d:%02d", end / 60, end % 60);
    }

    /** Minutes to spare before bed if started now; null when bed has passed or is more than six hours off. */
    private static Integer spare(Context c, int runtime) {
        int spare = bedMinutes(c) - (nowMinutes() + runtime);
        return spare < 0 || spare > 6 * 60 ? null : spare;
    }

    /** "1 min", "22 mins", "2h 05m" (an hour or more). */
    private static String spareWords(int minutes) {
        return minutes >= 60 ? length(minutes) : minutes + (minutes == 1 ? " min" : " mins");
    }

    /** "1h 46m", "52m". */
    static String length(int minutes) {
        return minutes >= 60 ? minutes / 60 + "h " + String.format(Locale.UK, "%02d", minutes % 60) + "m" : minutes + "m";
    }

    /** The poster's foot: "Ends 22:38" large and heavy, and under it "1h 46m · 22 min spare". */
    static CharSequence foot(HomeV2 h, int runtime) {
        SpannableStringBuilder b = new SpannableStringBuilder(ends(runtime));
        b.setSpan(new Face(h.a.getResources().getFont(R.font.outfit_extrabold)), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int from = b.length();
        Integer spare = spare(h.a, runtime);
        b.append("\n").append(length(runtime)).append(spare == null ? "" : spare == 0 ? " · right on bedtime" : " · " + spare + " min spare");
        b.setSpan(new AbsoluteSizeSpan(h.px(13)), from, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new ForegroundColorSpan(0xD9FFFFFF), from, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    /** A typeface for part of a line (TypefaceSpan takes one only from Android 9). */
    static final class Face extends MetricAffectingSpan {
        private final Typeface face;

        Face(Typeface face) {
            this.face = face;
        }

        @Override public void updateDrawState(TextPaint p) {
            p.setTypeface(face);
        }

        @Override public void updateMeasureState(TextPaint p) {
            p.setTypeface(face);
        }
    }

    /** The hero's line: "Ends 22:38, 22 min before bed", or just when it ends when bed has passed. */
    static String heroLine(Context c, int runtime) {
        int spare = bedMinutes(c) - (nowMinutes() + runtime);
        String end = ends(runtime);
        if (spare < 0 || spare > 6 * 60) return end;
        return end + (spare == 0 ? ", right on bedtime" : ", " + spare + " min before bed");
    }

    static PagesV2.Row row(HomeV2 h) {
        List<PagesV2.Title> items = new ArrayList<>();
        String providers = HomeV2.installedProviders(h);
        PagesV2.Row out = PagesV2.row(null, null, false, items);
        if (providers.isEmpty() || h.tmdbKey.isEmpty()) {
            out.empty = "Films that fit before bed come from the streaming apps on this TV: none is installed yet.";
            return out;
        }
        int w = window(h.a);
        out.side = evening();
        List<PagesV2.Title> found = PagesV2.tmdb(h.tmdbKey, "discover/movie?watch_region=GB&with_watch_monetization_types=flatrate"
                + "&with_watch_providers=" + providers + "&with_runtime.gte=70&with_runtime.lte=" + w
                + "&vote_count.gte=40&sort_by=popularity.desc", false);
        // Each film's running time (TMDB's lists leave it out) and its service here, a few at a time.
        List<Future<int[]>> times = new ArrayList<>();
        for (PagesV2.Title t : found) times.add(LOOKUPS.submit(() -> runtime(h, t.id)));
        for (int i = 0; i < found.size(); i++) {
            int[] rt;
            try {
                rt = times.get(i).get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                rt = null;
            }
            if (rt == null || rt[0] <= 0 || rt[0] > w) continue;
            PagesV2.Title t = found.get(i);
            t.runtime = rt[0];
            t.mark = HomeV2.serviceLogo(rt[1] > 0 ? String.valueOf(rt[1]) : null);
            items.add(t);
        }
        out.empty = "Nothing on your apps fits before bed tonight.";
        return out;
    }

    private static int[] runtime(HomeV2 h, int id) {
        int[] known = RUNTIMES.get(id);
        if (known != null) return known;
        try {
            JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + id
                    + "?append_to_response=watch/providers&api_key=" + h.tmdbKey));
            // The first of its UK subscription services whose app is on this TV.
            int provider = 0;
            JSONObject gb = YourShows.gb(d);
            JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
            for (int i = 0; flat != null && i < flat.length() && provider == 0; i++) {
                JSONObject p = flat.optJSONObject(i);
                String app = p == null ? null : HomeV2.serviceApp(String.valueOf(p.optInt("provider_id")));
                if (app != null && h.installed(app)) provider = p.optInt("provider_id");
            }
            int[] got = {d.optInt("runtime"), provider};
            if (got[0] > 0) RUNTIMES.put(id, got);
            return got;
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    /**
     * Over the hero's picture, the evening as the mock-up draws it: a bar from now to bed with the film in focus
     * along it in pink ("ANIMALS · ENDS 22:38"), bed a yellow mark at its end, now and the time to spare under it.
     * Every title in the tab has it: one that runs past bed fills the bar and says by how much, and in the day (bed
     * more than six hours off) the spare time is in hours (it was left out in both, so some posters had no box).
     */
    private static java.util.function.BiFunction<HomeV2, PagesV2.Title, View> evening() {
        View[] made = new View[1];
        TextView[] words = new TextView[3];
        View[] fill = new View[1];
        return (h, t) -> {
            if (t == null || t.runtime <= 0) return null;
            // Minutes left before bed when it ends (below 0: past bed).
            int spare = bedMinutes(h.a) - (nowMinutes() + t.runtime);
            if (made[0] == null) {
                Typeface heavy = h.a.getResources().getFont(R.font.outfit_extrabold), bold = Typeface.create(h.semi, Typeface.BOLD);
                FrameLayout layer = new FrameLayout(h.a);
                LinearLayout card = new LinearLayout(h.a);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(h.px(20), h.px(16), h.px(20), h.px(14));
                GradientDrawable g = h.fill(22, 0x9E0A0E50);
                g.setStroke(Math.max(1, h.px(1)), 0x38FFFFFF);
                card.setBackground(g);
                TextView head = h.text(15, bold, 0xCCFFFFFF);
                head.setText("TONIGHT");
                head.setLetterSpacing(0.16f);
                card.addView(head);
                FrameLayout track = new FrameLayout(h.a);
                track.setBackground(h.fill(17, 0x24FFFFFF));
                View bar = new View(h.a);
                bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{PINK, PINK_LIGHT}));
                bar.setClipToOutline(true);
                bar.setOutlineProvider(h.round(17));
                track.addView(bar, new FrameLayout.LayoutParams(0, -1));
                TextView name = h.text(16, heavy, Color.WHITE);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                name.setGravity(Gravity.CENTER_VERTICAL);
                name.setPadding(h.px(16), 0, h.px(16), 0);
                track.addView(name, new FrameLayout.LayoutParams(-1, -1));
                // Bed: a yellow mark standing out above and below the bar's end.
                View bed = new View(h.a);
                bed.setBackgroundColor(YELLOW);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(h.px(3), h.px(50), Gravity.END | Gravity.CENTER_VERTICAL);
                FrameLayout box = new FrameLayout(h.a);
                box.setClipChildren(false);
                box.addView(track, new FrameLayout.LayoutParams(-1, h.px(34), Gravity.CENTER_VERTICAL));
                box.addView(bed, bl);
                LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(-1, h.px(50));
                xl.topMargin = h.px(8);
                card.setClipChildren(false);
                card.addView(box, xl);
                LinearLayout under = new LinearLayout(h.a);
                TextView now = h.text(15, h.regular, 0xD9FFFFFF);
                under.addView(now, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView left = h.text(15, bold, YELLOW);
                under.addView(left);
                LinearLayout.LayoutParams ul = new LinearLayout.LayoutParams(-1, -2);
                ul.topMargin = h.px(2);
                card.addView(under, ul);
                int right = h.dockMode ? 48 : 96, w = 600;
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(h.px(w), -2, Gravity.TOP | Gravity.END);
                cl.topMargin = h.px(214);
                cl.rightMargin = h.px(right);
                layer.addView(card, cl);
                words[0] = name;
                words[1] = now;
                words[2] = left;
                fill[0] = bar;
                made[0] = layer;
            }
            int from = nowMinutes(), to = bedMinutes(h.a);
            float share = Math.max(0.08f, Math.min(1f, t.runtime / (float) Math.max(1, to - from)));
            words[0].setText((t.title == null ? "" : t.title.toUpperCase(Locale.UK)) + " · " + ends(t.runtime).toUpperCase(Locale.UK));
            int nm = LocalTime.now().toSecondOfDay() / 60;
            words[1].setText(String.format(Locale.UK, "Now %02d:%02d", nm / 60, nm % 60));
            int bed = to % (24 * 60);
            String left = spare == 0 ? "Right on bedtime"
                    : spare < 0 ? spareWords(-spare) + " past bedtime"
                    : spareWords(spare) + " to spare";
            words[2].setText(left + String.format(Locale.UK, " · bed %02d:%02d", bed / 60, bed % 60));
            words[2].setTextColor(spare < 0 ? PINK_LIGHT : YELLOW);
            View bar = fill[0];
            View track = (View) bar.getParent();
            Runnable size = () -> {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
                lp.width = Math.round(track.getWidth() * share);
                bar.setLayoutParams(lp);
            };
            if (track.getWidth() > 0) size.run(); else track.post(size);
            return made[0];
        };
    }
}
