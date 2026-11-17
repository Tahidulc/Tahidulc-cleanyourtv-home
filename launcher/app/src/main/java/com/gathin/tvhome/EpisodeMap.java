package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Episode map (theme "episode_map", on unless set false), from a series' panel (TitlePanel's Episode map button): every
 * episode on one map, a row of squares for each season (ten across, more rows for a longer season), each square coloured
 * by its TMDB rating on a five-step ramp from deep indigo to gold against the series' own range (dark writing on the two
 * lightest), its number in the corner and its rating in the middle; ticked where this TV has seen it (Your viewing's
 * episodes, and everything before the furthest one watched), the next one to watch ringed with NEXT, each season's best
 * with ★ BEST, a season's last episode FINALE; episodes not out yet dim, with their date. Beside it a binge plan: the
 * next episode's still, how many are left in its season and how long they take, one, two or three a night with the day
 * that would finish it, and tonight's episodes with when they end against bedtime (the theme's "bedtime").
 * The arrows move over the squares (Right from a row's end into the plan, Left back), OK plays the series in its
 * licensed app (the one on this TV that streams it in the UK; never Debrid Stream or an IPTV app), Up from the first row
 * goes back to the panel's buttons. As the Movies and TV Ideas canvas draws it (B · Episode map).
 */
final class EpisodeMap {
    static boolean on(Context c) {
        return Config.themeFlag(c, "episode_map", true);
    }

    /** The ramp, lowest to highest; an episode with no votes yet; not out yet; the dark writing on the two lightest. */
    static final int[] RAMP = {0xFF34306B, 0xFF5B4A6A, 0xFF8A6862, 0xFFC08C4F, 0xFFF2B33D};
    private static final int UNRATED = 0xFF262A58, UNAIRED = 0xFF1C1F4C, INK = 0xFF1A1440, BLUE_LABEL = 0xFFA9C4FF;
    /** The most seasons drawn (the latest ones): a long-running series stays quick to read and to draw. */
    private static final int MOST_SEASONS = 8;
    private static final int ACROSS = 10;

    static final class Ep {
        int season, number, runtime;
        String name, still, overview;
        double vote;
        LocalDate aired;
        boolean watched;

        boolean out(LocalDate today) {
            return aired != null && !aired.isAfter(today);
        }
    }

    interface Host {
        /** OK on a square that is out: the series in its app. */
        void play(Ep e);
    }

    // ---- reading ------------------------------------------------------------------------------------------------

    /** TMDB's seasons (tv/{id}/season/{n}), kept six hours: the map and Coming up read the same ones. */
    private static final Map<String, Object[]> SEASONS = new HashMap<>();
    private static final long KEEP_MS = 6 * 3600_000L;

    static JSONObject season(String key, int id, int number) {
        if (key == null || key.isEmpty() || id <= 0) return null;
        String k = id + ":" + number;
        synchronized (SEASONS) {
            Object[] had = SEASONS.get(k);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < KEEP_MS) return (JSONObject) had[1];
        }
        try {
            JSONObject s = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id + "/season/" + number
                    + "?language=en-GB&api_key=" + key));
            synchronized (SEASONS) { SEASONS.put(k, new Object[]{System.currentTimeMillis(), s}); }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** The series' seasons (the latest MOST_SEASONS, specials left out), each episode marked watched or not. Off the main thread. */
    static List<List<Ep>> read(HomeV2 h, int id, JSONObject details) {
        List<List<Ep>> out = new ArrayList<>();
        JSONObject d = details != null && details.has("seasons") ? details : YourShows.details(h.tmdbKey, id);
        JSONArray list = d == null ? null : d.optJSONArray("seasons");
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject s = list.optJSONObject(i);
            if (s != null && s.optInt("season_number") > 0 && s.optInt("episode_count") > 0) numbers.add(s.optInt("season_number"));
        }
        while (numbers.size() > MOST_SEASONS) numbers.remove(0);
        int fallback = 45;
        JSONArray runs = d == null ? null : d.optJSONArray("episode_run_time");
        if (runs != null && runs.length() > 0 && runs.optInt(0) > 0) fallback = runs.optInt(0);
        Set<String> seen = ViewingLog.episodes(h.a, id);
        int[] at = furthest(h.a, id);
        for (int n : numbers) {
            JSONObject s = season(h.tmdbKey, id, n);
            JSONArray eps = s == null ? null : s.optJSONArray("episodes");
            if (eps == null) continue;
            List<Ep> one = new ArrayList<>();
            for (int i = 0; i < eps.length(); i++) {
                JSONObject o = eps.optJSONObject(i);
                if (o == null) continue;
                Ep e = new Ep();
                e.season = n;
                e.number = o.optInt("episode_number");
                e.name = o.optString("name", "");
                e.overview = o.optString("overview", "");
                e.vote = o.optInt("vote_count") > 0 ? o.optDouble("vote_average", 0) : 0;
                e.runtime = o.optInt("runtime") > 0 ? o.optInt("runtime") : fallback;
                String still = o.optString("still_path", "");
                e.still = still.isEmpty() || "null".equals(still) ? null : "https://image.tmdb.org/t/p/w780" + still;
                try {
                    e.aired = LocalDate.parse(o.optString("air_date"));
                } catch (Exception x) {
                    e.aired = null;
                }
                e.watched = seen.contains("S" + n + "E" + e.number) || watchedBefore(at, n, e.number);
                one.add(e);
            }
            if (!one.isEmpty()) out.add(one);
        }
        return out;
    }

    /**
     * The furthest episode watched in any app (WatchHistory, "S2 E3" in what played): {season, episode, finished}, or null.
     * Everything before it counts as seen; it too once played to its end.
     */
    private static int[] furthest(Context c, int id) {
        int[] best = null;
        for (WatchHistory.Entry e : WatchHistory.load(c)) {
            if (!e.tv || e.tmdbId != id) continue;
            int[] p = YourShows.position(e);
            if (p == null) continue;
            int done = e.progress() >= 0.9f ? 1 : 0;
            if (best == null || p[0] > best[0] || p[0] == best[0] && (p[1] > best[1] || p[1] == best[1] && done > best[2])) {
                best = new int[]{p[0], p[1], done};
            }
        }
        return best;
    }

    /** Before the furthest episode watched (or that one, played to its end). */
    static boolean watchedBefore(int[] at, int season, int episode) {
        if (at == null) return false;
        if (season != at[0]) return season < at[0];
        return episode < at[1] || episode == at[1] && at[2] == 1;
    }

    /** A made-up series for adb's test when TMDB has nothing: two seasons of ten, the first seen and three of the second. */
    static List<List<Ep>> sample(LocalDate today) {
        double[][] votes = {{8.1, 7.8, 8.0, 8.4, 7.6, 8.2, 8.6, 8.3, 8.9, 9.2}, {8.0, 7.9, 8.3, 8.5, 7.7, 8.1, 8.8, 8.4, 9.0, 9.4}};
        List<List<Ep>> out = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            List<Ep> one = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Ep e = new Ep();
                e.season = s + 1;
                e.number = i + 1;
                e.vote = votes[s][i];
                e.runtime = 56;
                e.name = "";
                e.overview = "";
                e.aired = today.minusDays(400L - s * 200L - i * 7L);
                e.watched = s == 0 || i < 3;
                one.add(e);
            }
            out.add(one);
        }
        return out;
    }

    // ---- the sums (pure, unit tested) ------------------------------------------------------------------------------

    /** Where a rating sits on the ramp, 0 to 4, across the series' own range (taken as at least a point wide); -1 unrated. */
    static int step(double vote, double lo, double hi) {
        if (vote <= 0) return -1;
        double span = Math.max(1.0, hi - lo);
        int s = (int) Math.floor((vote - lo) / span * RAMP.length);
        return Math.max(0, Math.min(RAMP.length - 1, s));
    }

    /** {lowest, highest} rating of the episodes out and rated, or {0, 0}. */
    static double[] range(List<List<Ep>> seasons, LocalDate today) {
        double lo = Double.MAX_VALUE, hi = 0;
        for (List<Ep> s : seasons) for (Ep e : s) {
            if (e.vote <= 0 || !e.out(today)) continue;
            lo = Math.min(lo, e.vote);
            hi = Math.max(hi, e.vote);
        }
        return hi == 0 ? new double[]{0, 0} : new double[]{lo, hi};
    }

    /** The first episode out and not seen, in order; null when all caught up. */
    static Ep next(List<List<Ep>> seasons, LocalDate today) {
        for (List<Ep> s : seasons) for (Ep e : s) if (!e.watched && e.out(today)) return e;
        return null;
    }

    /** The episodes out and not seen in next's season, from next on. */
    static List<Ep> left(List<List<Ep>> seasons, Ep next, LocalDate today) {
        List<Ep> out = new ArrayList<>();
        if (next == null) return out;
        for (List<Ep> s : seasons) for (Ep e : s) {
            if (e.season == next.season && e.number >= next.number && e.out(today) && !e.watched) out.add(e);
        }
        return out;
    }

    /** The day the last of left episodes is seen at perNight a night, from tonight (or tomorrow when tonight has gone). */
    static LocalDate doneOn(int left, int perNight, LocalDate today, boolean tonight) {
        LocalDate first = tonight ? today : today.plusDays(1);
        if (left <= 0) return first;
        int nights = (left + perNight - 1) / perNight;
        return first.plusDays(nights - 1);
    }

    /** "done tonight", "done tomorrow", "done Sat 7 Nov". */
    static String doneWords(LocalDate done, LocalDate today) {
        long n = ChronoUnit.DAYS.between(today, done);
        if (n <= 0) return "done tonight";
        if (n == 1) return "done tomorrow";
        return "done " + done.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK));
    }

    /** "6h 30m", "2h", "45m". */
    static String hm(int minutes) {
        if (minutes < 60) return minutes + "m";
        return minutes / 60 + "h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + "m");
    }

    /** A time of day from minutes since midnight (past midnight wraps): "21:38", "00:15". */
    static String clock(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.UK, "%02d:%02d", m / 60, m % 60);
    }

    /** "E4", "E4 and E5", "E4, E5 and E6". */
    static String names(List<Ep> eps) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < eps.size(); i++) {
            if (i > 0) b.append(i == eps.size() - 1 ? " and " : ", ");
            b.append('E').append(eps.get(i).number);
        }
        return b.toString();
    }

    // ---- the view -------------------------------------------------------------------------------------------------

    private final HomeV2 h;
    private final Context c;
    private final Host host;
    private final int width, height;
    private final Typeface heavy, bold, semi, regular;
    final FrameLayout view;
    private final FrameLayout window;
    private final LinearLayout content;
    private final TextView loading;
    private LinearLayout plan;
    private final List<Ep[]> lines = new ArrayList<>();
    private final List<View[]> cells = new ArrayList<>();
    private final List<Integer> lineTops = new ArrayList<>();
    private final List<LinearLayout> choiceRows = new ArrayList<>();
    private final List<TextView> choiceDone = new ArrayList<>();
    private TextView tonightLine;
    private int line, cell, choice, chosen = 1, sq, windowH, shift;
    private boolean inPlan, filled;
    private List<Ep> leftEps = new ArrayList<>();
    private Ep next;

    /** width and height: the room under the panel's buttons, in the design's pixels. */
    EpisodeMap(HomeV2 home, PagesV2.Title t, JSONObject details, int width, int height, boolean test, Host host) {
        h = home;
        c = home.a;
        this.host = host;
        this.width = width;
        this.height = height;
        heavy = Fonts.get(c, R.font.outfit_extrabold);
        semi = home.semi;
        bold = Typeface.create(home.semi, Typeface.BOLD);
        regular = home.regular;
        view = new FrameLayout(c);
        view.setClipChildren(false);

        // The heading and its key: the ramp, watched, next.
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = h.text(26, semi, Color.WHITE);
        name.setText("Episode map");
        head.addView(name);
        head.addView(gap(28));
        TextView rating = h.text(15, regular, 0xFFD4D7EE);
        rating.setText("Rating");
        head.addView(rating);
        head.addView(gap(8));
        for (int colour : RAMP) {
            View sw = new View(c);
            sw.setBackground(h.fill(3, colour));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(h.px(26), h.px(14));
            sl.rightMargin = h.px(8);
            head.addView(sw, sl);
        }
        TextView higher = h.text(15, regular, 0xFFD4D7EE);
        higher.setText("higher");
        head.addView(higher);
        head.addView(gap(28));
        TextView tick = h.text(15, regular, 0xFFD4D7EE);
        tick.setText("✓  watched");
        head.addView(tick);
        head.addView(gap(28));
        View ring = new View(c);
        GradientDrawable rg = new GradientDrawable();
        rg.setCornerRadius(h.px(3));
        rg.setStroke(Math.max(1, h.px(2.5f)), Color.WHITE);
        ring.setBackground(rg);
        head.addView(ring, new LinearLayout.LayoutParams(h.px(14), h.px(14)));
        head.addView(gap(8));
        TextView nx = h.text(15, regular, 0xFFD4D7EE);
        nx.setText("next");
        head.addView(nx);
        place(view, head, 0, 0, planX() - 34, 34);

        // The grid's window: the seasons scroll up inside it as the focus goes down.
        windowH = height - 52;
        window = new FrameLayout(c);
        window.setClipChildren(true);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        window.addView(content, new FrameLayout.LayoutParams(-1, -2));
        place(view, window, 0, 52, planX() - 24, windowH);
        loading = h.text(18, regular, 0xBFFFFFFF);
        loading.setText("Reading the episodes…");
        place(view, loading, 0, 64, 600, 30);

        int id = t.id;
        h.io.execute(() -> {
            List<List<Ep>> got = read(h, id, details);
            if (got.isEmpty() && test) got = sample(LocalDate.now());
            List<List<Ep>> seasons = got;
            h.main.post(() -> { if (view.isAttachedToWindow()) fill(seasons); });
        });
    }

    /** The plan's left edge: the card ends at 1864 on the screen, whatever the panel's own left edge. */
    private int planX() {
        return width - 504;
    }

    private View gap(int w) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(h.px(w), 1));
        return v;
    }

    private void fill(List<List<Ep>> seasons) {
        loading.setVisibility(View.GONE);
        if (seasons.isEmpty()) {
            loading.setText("No episodes listed for this series yet");
            loading.setVisibility(View.VISIBLE);
            return;
        }
        LocalDate today = LocalDate.now();
        double[] r = range(seasons, today);
        next = next(seasons, today);
        int across = Math.max(1, Math.min(ACROSS, maxEpisodes(seasons)));
        sq = Math.max(64, Math.min(104, (planX() - 34 - 130 - (ACROSS - 1) * 10) / ACROSS));
        int tall = Math.round(sq * 0.96f), top = 0;
        for (List<Ep> s : seasons) {
            Ep best = null;
            int rated = 0;
            for (Ep e : s) if (e.vote > 0 && e.out(today)) { rated++; if (best == null || e.vote > best.vote) best = e; }
            if (rated < 3) best = null;
            boolean complete = s.get(s.size() - 1).out(today);
            for (int from = 0; from < s.size(); from += across) {
                LinearLayout one = new LinearLayout(c);
                one.setGravity(Gravity.CENTER_VERTICAL);
                one.setClipChildren(false);
                TextView label = h.text(18, semi, 0xFFE4E6F5);
                label.setText(from == 0 ? "Season " + s.get(0).season : "");
                one.addView(label, new LinearLayout.LayoutParams(h.px(130), -2));
                int to = Math.min(s.size(), from + across);
                Ep[] eps = new Ep[to - from];
                View[] views = new View[to - from];
                for (int i = from; i < to; i++) {
                    Ep e = s.get(i);
                    View sqv = square(e, r[0], r[1], e == best, complete && i == s.size() - 1 && e != best, today);
                    LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(h.px(sq), h.px(tall));
                    sl.rightMargin = h.px(10);
                    one.addView(sqv, sl);
                    eps[i - from] = e;
                    views[i - from] = sqv;
                }
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, -2);
                ol.bottomMargin = h.px(12);
                content.addView(one, ol);
                lines.add(eps);
                cells.add(views);
                lineTops.add(top);
                top += tall + 12;
            }
        }
        buildPlan(seasons, today);
        // Onto the next one to watch (else the first), the map scrolled to show it.
        line = 0;
        cell = 0;
        for (int l = 0; l < lines.size(); l++) for (int i = 0; i < lines.get(l).length; i++) {
            if (lines.get(l)[i] == next) { line = l; cell = i; }
        }
        filled = true;
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(220).start();
        paint();
    }

    private static int maxEpisodes(List<List<Ep>> seasons) {
        int most = 0;
        for (List<Ep> s : seasons) most = Math.max(most, s.size());
        return most;
    }

    private View square(Ep e, double lo, double hi, boolean best, boolean finale, LocalDate today) {
        FrameLayout f = new FrameLayout(c);
        boolean out = e.out(today);
        int st = step(e.vote, lo, hi);
        int bg = !out ? UNAIRED : st < 0 ? UNRATED : RAMP[st];
        int ink = out && st >= 3 ? INK : Color.WHITE;
        GradientDrawable g = h.fill(10, bg);
        if (e == next) g.setStroke(h.px(3), Color.WHITE);
        f.setBackground(g);
        TextView num = h.text(13, bold, ink);
        num.setText("E" + e.number);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        nl.leftMargin = h.px(10);
        nl.topMargin = h.px(8);
        f.addView(num, nl);
        TextView mid = h.text(out ? (sq < 90 ? 22 : 26) : 15, out ? heavy : bold, ink);
        mid.setText(!out ? (e.aired == null ? "TBC" : e.aired.format(DateTimeFormatter.ofPattern("d MMM", Locale.UK)).toUpperCase(Locale.UK))
                : e.vote > 0 ? String.format(Locale.UK, "%.1f", e.vote) : "–");
        f.addView(mid, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        if (e.watched) {
            TextView tick = h.text(15, bold, ink);
            tick.setText("✓");
            FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            tl.rightMargin = h.px(9);
            tl.topMargin = h.px(6);
            f.addView(tick, tl);
        }
        String tag = e == next ? "NEXT" : best ? "★ BEST" : finale ? "FINALE" : null;
        if (tag != null) {
            TextView tg = h.text(11, bold, ink);
            tg.setText(tag);
            tg.setLetterSpacing(0.09f);
            FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            gl.bottomMargin = h.px(8);
            f.addView(tg, gl);
            // The rating up a little, so the tag under it has room.
            ((FrameLayout.LayoutParams) mid.getLayoutParams()).bottomMargin = h.px(8);
        }
        if (!out) f.setAlpha(0.6f);
        return f;
    }

    // ---- the binge plan -------------------------------------------------------------------------------------------

    private void buildPlan(List<List<Ep>> seasons, LocalDate today) {
        plan = new LinearLayout(c);
        plan.setOrientation(LinearLayout.VERTICAL);
        plan.setPadding(h.px(22), h.px(20), h.px(22), h.px(22));
        GradientDrawable bg = h.fill(18, 0x8C28246E);
        bg.setStroke(Math.max(1, h.px(1.5f)), 0x38FFFFFF);
        plan.setBackground(bg);
        TextView label = h.text(14, bold, BLUE_LABEL);
        label.setText("BINGE PLAN");
        label.setLetterSpacing(0.18f);
        plan.addView(label);

        leftEps = left(seasons, next, today);
        Ep shown = next;
        if (shown == null) {
            // All caught up: the latest one out, for its picture.
            for (List<Ep> s : seasons) for (Ep e : s) if (e.out(today)) shown = e;
        }
        FrameLayout still = new FrameLayout(c);
        still.setBackground(h.fill(12, 0x24FFFFFF));
        still.setClipToOutline(true);
        still.setOutlineProvider(h.round(12));
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (shown != null && shown.still != null) h.image(pic, shown.still, h.px(460));
        still.addView(pic, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xCC000000}));
        still.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        TextView on = h.text(18, bold, Color.WHITE);
        on.setText(next != null ? "Next: S" + next.season + " E" + next.number : shown != null ? "Last out: S" + shown.season + " E" + shown.number : "");
        FrameLayout.LayoutParams onl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        onl.leftMargin = h.px(14);
        onl.bottomMargin = h.px(12);
        still.addView(on, onl);
        LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(-1, h.px(190));
        stl.topMargin = h.px(14);
        plan.addView(still, stl);

        TextView sum = h.text(20, semi, Color.WHITE);
        LinearLayout.LayoutParams suml = new LinearLayout.LayoutParams(-1, -2);
        suml.topMargin = h.px(14);
        plan.addView(sum, suml);
        if (next == null) {
            sum.setText("All caught up");
            Ep coming = null;
            for (List<Ep> s : seasons) for (Ep e : s) if (!e.out(today) && e.aired != null && coming == null) coming = e;
            TextView when = h.text(17, regular, 0xFFE4E6F5);
            when.setText(coming != null ? "S" + coming.season + " E" + coming.number + " is out "
                    + coming.aired.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)) : "No more episodes dated yet");
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.topMargin = h.px(10);
            plan.addView(when, wl);
        } else {
            int minutes = 0;
            for (Ep e : leftEps) minutes += e.runtime;
            sum.setText(leftEps.size() + " left in season " + next.season + " · about " + hm(minutes));
            chosen = Math.min(leftEps.size(), 2) - 1;
            for (int n = 1; n <= 3; n++) {
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(h.px(16), 0, h.px(16), 0);
                TextView a = h.text(18, bold, Color.WHITE);
                a.setText(n + " a night");
                row.addView(a, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView b = h.text(18, regular, 0xFFD4D7EE);
                row.addView(b);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, h.px(50));
                rl.topMargin = h.px(n == 1 ? 12 : 8);
                plan.addView(row, rl);
                choiceRows.add(row);
                choiceDone.add(b);
            }
            tonightLine = h.text(17, regular, 0xFFE4E6F5);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
            tl.topMargin = h.px(14);
            plan.addView(tonightLine, tl);
            words(today);
        }
        place(view, plan, planX(), 0, 504, -2);
    }

    /** Each choice's finishing day, and tonight's episodes (as many as the chosen one a night) and when they end. */
    private void words(LocalDate today) {
        int bed = FitsTonight.bedMinutes(c), now = LocalTime.now().toSecondOfDay() / 60;
        if (now < 5 * 60) now += 24 * 60;
        boolean tonight = now < bed - 20;
        for (int i = 0; i < choiceDone.size(); i++) {
            choiceDone.get(i).setText(doneWords(doneOn(leftEps.size(), i + 1, today, tonight), today));
        }
        int per = Math.max(1, Math.min(leftEps.size(), chosen + 1));
        List<Ep> first = new ArrayList<>(leftEps.subList(0, per));
        int minutes = 0;
        for (Ep e : first) minutes += e.runtime;
        if (tonight) {
            int end = now + minutes, spare = bed - end;
            tonightLine.setText("Tonight: " + names(first) + ", ends " + clock(end) + " · "
                    + (spare >= 0 ? spare + " min before bed" : -spare + " min after bed"));
        } else {
            tonightLine.setText("Tomorrow: " + names(first) + " · " + hm(minutes));
        }
    }

    // ---- the remote ----------------------------------------------------------------------------------------------

    /** A key while the map has the focus; false: Up from its first row (back to the panel's buttons). */
    boolean key(int code) {
        if (!filled) return code != KeyEvent.KEYCODE_DPAD_UP;
        if (inPlan) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) inPlan = false;
            else if (code == KeyEvent.KEYCODE_DPAD_UP) { if (choice > 0) choice--; else return false; }
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN && choice < choiceRows.size() - 1) choice++;
            paint();
            return true;
        }
        int n = lines.get(line).length;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (cell > 0) cell--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (cell < n - 1) cell++;
                else if (!choiceRows.isEmpty()) { inPlan = true; choice = chosen; }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (line == 0) return false;
                line--;
                cell = Math.min(cell, lines.get(line).length - 1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (line < lines.size() - 1) { line++; cell = Math.min(cell, lines.get(line).length - 1); }
                break;
            default:
                return true;
        }
        paint();
        return true;
    }

    /** OK: a square plays the series in its app (one not out yet says when it is); a choice becomes the plan. */
    void press() {
        if (!filled) return;
        if (inPlan) {
            chosen = choice;
            words(LocalDate.now());
            paint();
            return;
        }
        Ep e = lines.get(line)[cell];
        if (!e.out(LocalDate.now())) {
            android.widget.Toast.makeText(c, "S" + e.season + " E" + e.number + (e.aired == null ? " has no date yet"
                    : " is out " + e.aired.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK))), android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        host.play(e);
    }

    /** The focus drawn: on a square or a choice while the map has it, none while the panel's buttons have it. */
    private void paint() {
        if (!filled) return;
        boolean on = mapHasFocus;
        for (int l = 0; l < cells.size(); l++) {
            View[] row = cells.get(l);
            for (int i = 0; i < row.length; i++) {
                boolean here = on && !inPlan && l == line && i == cell;
                GradientDrawable fg = null;
                if (here) {
                    fg = new GradientDrawable();
                    fg.setCornerRadius(h.px(10));
                    fg.setStroke(h.px(4), Color.WHITE);
                }
                ((FrameLayout) row[i]).setForeground(fg);
                float s = here ? 1.08f : 1f;
                if (row[i].getScaleX() != s) row[i].animate().scaleX(s).scaleY(s).setDuration(120).start();
            }
        }
        for (int i = 0; i < choiceRows.size(); i++) {
            boolean picked = i == chosen, here = on && inPlan && i == choice;
            GradientDrawable g = h.fill(12, picked ? 0x33FFFFFF : 0x14FFFFFF);
            if (here) g.setStroke(h.px(2.5f), Color.WHITE);
            else if (picked) g.setStroke(Math.max(1, h.px(1.5f)), 0x80FFFFFF);
            choiceRows.get(i).setBackground(g);
        }
        // The focused row kept in the window: the seasons slide up as the focus goes down past it.
        if (!lineTops.isEmpty()) {
            int tall = Math.round(sq * 0.96f) + 12;
            int top = lineTops.get(line);
            if (top - shift + tall > windowH) shift = top + tall - windowH;
            if (top < shift) shift = top;
            content.animate().translationY(-h.px(shift)).setDuration(180).setInterpolator(Fluid.EASE).start();
        }
    }

    /** Whether the map holds the focus now (the panel tells it as the focus moves between it and the buttons). */
    private boolean mapHasFocus;

    void focus(boolean on) {
        mapHasFocus = on;
        if (!on) inPlan = false;
        paint();
    }

    private void place(FrameLayout parent, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w < 0 ? w : h.px(w), hgt < 0 ? hgt : h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        parent.addView(v, p);
    }
}
