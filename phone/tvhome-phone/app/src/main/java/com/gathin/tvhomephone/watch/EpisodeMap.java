package com.gathin.tvhomephone.watch;

import android.app.Activity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sync.Tvs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * A series' Episode map, as the TVs draw it: one row of squares per season, each coloured by its TMDB rating on the
 * 5-step ramp (measured against the series' own range), E number and rating, a tick where the TVs have watched it,
 * the next one ringed NEXT, each season's BEST and FINALE; unaired ones dim with their date. Then the binge plan:
 * how many a night to finish the season, and tonight's end against bedtime.
 */
final class EpisodeMap {
    final LinearLayout view;
    private final Activity a;
    private final Title t;
    private final JSONObject details;
    private final LinearLayout rows, plan;
    private int watchedSeason, watchedEpisode;

    EpisodeMap(Activity a, Title t, JSONObject details) {
        this.a = a;
        this.t = t;
        this.details = details;
        view = Ui.column(a);
        int pad = Ui.dp(a, 20);
        view.setPadding(pad, Ui.dp(a, 18), pad, 0);
        LinearLayout head = Ui.row(a);
        head.addView(Ui.text(a, "Episode map", 18, 0xFFFFFFFF, true), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout legend = Ui.row(a);
        legend.addView(Ui.text(a, "Rating ", 11, Ui.DIM, false));
        for (int c : Plan.RAMP) {
            View sw = new View(a);
            sw.setBackground(Ui.round(c, 2, a));
            legend.addView(sw, Ui.margins(new LinearLayout.LayoutParams(Ui.dp(a, 12), Ui.dp(a, 8)), a, 1, 0, 1, 0));
        }
        legend.addView(Ui.text(a, " higher", 11, Ui.DIM, false));
        head.addView(legend);
        view.addView(head, Ui.fill());
        rows = Ui.column(a);
        view.addView(rows, Ui.fill());
        plan = Ui.column(a);
        view.addView(plan, Ui.fill());
        TextView loading = Ui.text(a, "Loading the episodes…", 13, Ui.DIM, false);
        loading.setPadding(0, Ui.dp(a, 8), 0, 0);
        rows.addView(loading);
        watchedFrom(Tvs.watchingCached(a));
        Tvs.watching(a, (w, e) -> {
            if (w != null && watchedFrom(w) && !a.isFinishing()) load();
        });
        load();
    }

    /** The furthest episode the TVs have played of this series; true when it moved. */
    private boolean watchedFrom(JSONObject w) {
        JSONArray shows = w == null ? null : w.optJSONArray("shows");
        for (int i = 0; shows != null && i < shows.length(); i++) {
            JSONObject s = shows.optJSONObject(i);
            if (s == null || s.optInt("id") != t.id) continue;
            int se = s.optInt("season"), ep = s.optInt("episode");
            if (se > watchedSeason || se == watchedSeason && ep > watchedEpisode) {
                watchedSeason = se;
                watchedEpisode = ep;
                return true;
            }
        }
        return false;
    }

    private boolean watched(int season, int ep) {
        return season < watchedSeason || season == watchedSeason && ep <= watchedEpisode;
    }

    private void load() {
        JSONArray seasons = details.optJSONArray("seasons");
        List<Integer> nums = new ArrayList<>();
        for (int i = 0; seasons != null && i < seasons.length(); i++) {
            int n = seasons.optJSONObject(i) == null ? 0 : seasons.optJSONObject(i).optInt("season_number");
            if (n > 0) nums.add(n);
        }
        while (nums.size() > 8) nums.remove(0);
        App.io.execute(() -> {
            List<JSONObject> got = new ArrayList<>();
            for (int n : nums) {
                try {
                    got.add(Tmdb.get(a, "tv/" + t.id + "/season/" + n));
                } catch (IOException ignored) {
                    // that season left out
                }
            }
            App.main.post(() -> {
                if (!a.isFinishing()) draw(got);
            });
        });
    }

    private void draw(List<JSONObject> seasons) {
        rows.removeAllViews();
        plan.removeAllViews();
        if (seasons.isEmpty()) {
            rows.addView(Ui.text(a, "No episode list from TMDB yet.", 13, Ui.DIM, false));
            return;
        }
        LocalDate today = LocalDate.now();
        double lo = 10, hi = 0;
        for (JSONObject s : seasons) {
            JSONArray eps = s.optJSONArray("episodes");
            for (int i = 0; eps != null && i < eps.length(); i++) {
                double v = eps.optJSONObject(i).optDouble("vote_average", 0);
                if (v > 0) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            }
        }
        if (hi < lo) { lo = 7; hi = 8; }
        int screen = a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 40) - Ui.dp(a, 4);
        int cell = Ui.dp(a, 54), gap = Ui.dp(a, 5);
        int columns = Math.max(5, Math.min(10, screen / (cell + gap)));
        JSONObject nextEp = null;
        int nextSeason = 0;
        for (JSONObject s : seasons) {
            int sn = s.optInt("season_number");
            JSONArray eps = s.optJSONArray("episodes");
            if (eps == null || eps.length() == 0) continue;
            double best = 0;
            for (int i = 0; i < eps.length(); i++) best = Math.max(best, eps.optJSONObject(i).optDouble("vote_average", 0));
            TextView label = Ui.text(a, "Season " + sn, 14, Ui.SOFT, true);
            label.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 6));
            rows.addView(label);
            GridLayout grid = new GridLayout(a);
            grid.setColumnCount(columns);
            for (int i = 0; i < eps.length(); i++) {
                JSONObject e = eps.optJSONObject(i);
                int en = e.optInt("episode_number");
                LocalDate air = ComingUp.parse(e.optString("air_date"));
                boolean aired = air != null && !air.isAfter(today);
                double v = e.optDouble("vote_average", 0);
                boolean seen = watched(sn, en);
                boolean next = nextEp == null && aired && !seen;
                if (next) { nextEp = e; nextSeason = sn; }
                String tag = next ? "NEXT" : best > 0 && v == best && aired && v > 0 ? "★ BEST"
                        : i == eps.length() - 1 && aired ? "FINALE" : null;
                View sq = square(en, v, Plan.step(v, lo, hi), aired, air, seen, next, tag);
                GridLayout.LayoutParams p = new GridLayout.LayoutParams();
                p.width = cell;
                p.height = cell;
                p.setMargins(0, 0, gap, gap);
                JSONObject ep = e;
                int season = sn;
                sq.setOnClickListener(x -> episode(season, ep, aired, air));
                grid.addView(sq, p);
            }
            rows.addView(grid);
        }
        if (nextEp != null) plan(seasons, nextSeason, nextEp);
    }

    private View square(int en, double v, int step, boolean aired, LocalDate air, boolean seen, boolean next, String tag) {
        FrameLayout f = new FrameLayout(a);
        int fill = aired && v > 0 ? Plan.RAMP[step] : 0x26FFFFFF;
        GradientDrawable bg = Ui.round(fill, 8, a);
        if (next) bg.setStroke(Ui.dp(a, 2.5f), 0xFFFFFFFF);
        f.setBackground(bg);
        int ink = aired && v > 0 && step >= 3 ? 0xFF1A1440 : 0xFFFFFFFF;
        TextView e = Ui.text(a, "E" + en, 9, ink, true);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        ep.setMargins(Ui.dp(a, 4), Ui.dp(a, 2), 0, 0);
        f.addView(e, ep);
        if (seen) {
            TextView tick = Ui.text(a, "✓", 10, ink, true);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            tp.setMargins(0, Ui.dp(a, 1), Ui.dp(a, 4), 0);
            f.addView(tick, tp);
        }
        TextView mid = Ui.text(a, aired ? (v > 0 ? String.format(Locale.UK, "%.1f", v) : "–")
                : air == null ? "TBA" : air.getDayOfMonth() + "/" + air.getMonthValue(), aired ? 15 : 11, ink, true);
        f.addView(mid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        if (tag != null) {
            TextView tt = Ui.text(a, tag, 7, ink, true);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.setMargins(0, 0, 0, Ui.dp(a, 2));
            f.addView(tt, lp);
        }
        if (!aired) f.setAlpha(0.55f);
        f.setContentDescription("Episode " + en + (aired ? ", rated " + v : ", not out yet") + (seen ? ", watched" : "") + (next ? ", next" : ""));
        return f;
    }

    private void episode(int season, JSONObject e, boolean aired, LocalDate air) {
        String title = "S" + season + " E" + e.optInt("episode_number") + " · " + e.optString("name");
        String body = aired ? e.optString("overview") : "Out " + (air == null ? "on a date to be announced" : Plan.date(air)) + ".";
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(a).setTitle(title).setMessage(body.isEmpty() ? " " : body);
        if (aired) b.setPositiveButton("Play", (d, w) -> {
            Providers.Choice ch = Providers.choose(a, Tmdb.providersGb(details), t.title, t.id, true);
            Providers.play(a, ch, null);
        });
        b.setNegativeButton("Close", null).show();
    }

    /** The binge plan: from the next episode to the season's end, 1, 2 or 3 a night. */
    private void plan(List<JSONObject> seasons, int season, JSONObject next) {
        JSONArray eps = null;
        for (JSONObject s : seasons) if (s.optInt("season_number") == season) eps = s.optJSONArray("episodes");
        if (eps == null) return;
        LocalDate today = LocalDate.now();
        List<Integer> runtimes = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        boolean from = false;
        for (int i = 0; i < eps.length(); i++) {
            JSONObject e = eps.optJSONObject(i);
            if (e.optInt("episode_number") == next.optInt("episode_number")) from = true;
            LocalDate air = ComingUp.parse(e.optString("air_date"));
            if (!from || air == null || air.isAfter(today)) continue;
            runtimes.add(e.optInt("runtime", 0));
            numbers.add(e.optInt("episode_number"));
        }
        if (runtimes.isEmpty()) return;
        LinearLayout card = Ui.column(a);
        int p = Ui.dp(a, 16);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.glass(a));
        card.addView(Ui.label(a, "BINGE PLAN", 0xFFA9C4FF));
        String still = Tmdb.image(next.optString("still_path", null), "w780");
        if (still != null) {
            ImageView iv = new ImageView(a);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(a).load(still).transform(new CenterCrop(), new RoundedCorners(Ui.dp(a, 10))).into(iv);
            card.addView(iv, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(a, 150)), a, 0, 10, 0, 0));
        }
        int total = Plan.minutes(runtimes, runtimes.size());
        TextView sum = Ui.text(a, "Next: S" + season + " E" + next.optInt("episode_number") + " · " + runtimes.size() + " left in season "
                + season + " · about " + Plan.length(total), 15, 0xFFFFFFFF, true);
        sum.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 6));
        card.addView(sum);
        LinearLayout choices = Ui.column(a);
        TextView tonight = Ui.text(a, "", 14, Ui.SOFT, false);
        int[] per = {2};
        Runnable[] paint = new Runnable[1];
        paint[0] = () -> {
            choices.removeAllViews();
            for (int k = 1; k <= 3; k++) {
                int kk = k;
                LinearLayout row = Ui.row(a);
                row.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 12), 0);
                row.setMinimumHeight(Ui.dp(a, 48));
                GradientDrawable bg = Ui.round(k == per[0] ? 0x33FFFFFF : 0x14FFFFFF, 12, a);
                if (k == per[0]) bg.setStroke(Ui.dp(a, 2), 0xFFFFFFFF);
                row.setBackground(bg);
                row.addView(Ui.text(a, k + " a night", 15, 0xFFFFFFFF, k == per[0]), new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(Ui.text(a, "done " + Plan.date(Plan.doneOn(today, runtimes.size(), k)), 14, Ui.SOFT, false));
                row.setOnClickListener(v -> { per[0] = kk; paint[0].run(); });
                choices.addView(row, Ui.margins(Ui.fill(), a, 0, 0, 0, 6));
            }
            Calendar now = Calendar.getInstance();
            int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
            int k = Math.min(per[0], runtimes.size());
            int end = nowMin + Plan.minutes(runtimes, k);
            int spare = Plan.spare(end, WatchPrefs.bedtime(a));
            StringBuilder which = new StringBuilder();
            for (int i = 0; i < k; i++) which.append(i == 0 ? "E" : i == k - 1 ? " and E" : ", E").append(numbers.get(i));
            tonight.setText("Tonight: " + which + ", ends " + Plan.hm(end) + " · "
                    + (spare >= 0 ? spare + " min before bed" : -spare + " min past bed"));
        };
        paint[0].run();
        card.addView(choices, Ui.fill());
        card.addView(tonight);
        plan.addView(card, Ui.margins(Ui.fill(), a, 0, 16, 0, 0));
    }
}
