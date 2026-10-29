package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Launcher v2's pages behind the boxes along the bottom of Home: Discover (tabs over one row of posters), Movies and
 * TV shows (two rows of posters under a large picture) and Sport (the match of the day, the Premier League table and
 * the F1 standings, then this week's live sport). Each page sits over Home and keeps its top bar; Back returns.
 */
final class PagesV2 {
    private PagesV2() { }

    abstract static class Page {
        final HomeV2 h;
        final FrameLayout layer;

        Page(HomeV2 h) {
            this.h = h;
            layer = new FrameLayout(h.a);
            layer.setClipChildren(false);
        }

        void open() {
            h.root.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            layer.setAlpha(0f);
            layer.animate().alpha(1f).setDuration(250).setInterpolator(HomeV2.SKY).start();
        }

        void close() {
            layer.animate().alpha(0f).setDuration(180).withEndAction(() -> h.root.removeView(layer)).start();
        }

        /** True when the page used the key; Back is handled by Home. */
        abstract boolean key(KeyEvent e);

        int px(float v) { return h.px(v); }

        void add(View v, int x, int y, int w, int hgt) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : px(w), hgt < 0 ? hgt : px(hgt));
            lp.leftMargin = px(x);
            lp.topMargin = px(y);
            layer.addView(v, lp);
        }

        static boolean ok(int code) {
            return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        }
    }

    // ---- films and series from TMDB --------------------------------------------------------------------------------

    static final class Title {
        String title, poster, backdrop, year, overview, what;
        double vote;
        int id;
        boolean tv;
    }

    private static final Map<Integer, String> GENRES = new HashMap<>();
    static {
        Object[][] g = {{28, "Action"}, {12, "Adventure"}, {16, "Animation"}, {35, "Comedy"}, {80, "Crime"}, {99, "Documentary"},
                {18, "Drama"}, {10751, "Family"}, {14, "Fantasy"}, {36, "History"}, {27, "Horror"}, {10402, "Music"}, {9648, "Mystery"},
                {10749, "Romance"}, {878, "Science Fiction"}, {53, "Thriller"}, {10752, "War"}, {37, "Western"}, {10759, "Action & Adventure"},
                {10765, "Sci-Fi & Fantasy"}, {10768, "War & Politics"}, {10762, "Kids"}, {10764, "Reality"}};
        for (Object[] x : g) GENRES.put((Integer) x[0], (String) x[1]);
    }

    /** A TMDB list ("results") as titles with a poster; tv says what the results are when they do not say it themselves. */
    static List<Title> tmdb(String key, String path, boolean tv) {
        List<Title> out = new ArrayList<>();
        if (key.isEmpty()) return out;
        try {
            String url = "https://api.themoviedb.org/3/" + path + (path.contains("?") ? "&" : "?") + "language=en-GB&api_key=" + key;
            JSONArray r = new JSONObject(HomeV2.get(url)).optJSONArray("results");
            for (int i = 0; r != null && i < r.length() && out.size() < 20; i++) {
                JSONObject o = r.getJSONObject(i);
                String type = o.optString("media_type", tv ? "tv" : "movie");
                if (!type.equals("tv") && !type.equals("movie")) continue;
                String poster = o.optString("poster_path", ""), back = o.optString("backdrop_path", "");
                if (poster.isEmpty() || "null".equals(poster)) continue;
                Title t = new Title();
                t.tv = type.equals("tv");
                t.id = o.optInt("id");
                t.title = o.optString(t.tv ? "name" : "title");
                t.overview = o.optString("overview", "");
                String date = o.optString(t.tv ? "first_air_date" : "release_date", "");
                t.year = date.length() >= 4 ? date.substring(0, 4) : "";
                t.vote = o.optDouble("vote_average", 0);
                JSONArray ids = o.optJSONArray("genre_ids");
                List<String> gs = new ArrayList<>();
                for (int k = 0; ids != null && k < ids.length() && gs.size() < 2; k++) if (GENRES.containsKey(ids.optInt(k))) gs.add(GENRES.get(ids.optInt(k)));
                t.what = TextUtils.join(" · ", gs);
                t.poster = "https://image.tmdb.org/t/p/w342" + poster;
                t.backdrop = back.isEmpty() || "null".equals(back) ? null : "https://image.tmdb.org/t/p/w1280" + back;
                out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Films and series taken in turn from two lists. */
    static List<Title> mix(List<Title> a, List<Title> b) {
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(a.size(), b.size()) && out.size() < 20; i++) {
            if (i < a.size()) out.add(a.get(i));
            if (i < b.size()) out.add(b.get(i));
        }
        return out;
    }

    // ---- poster pages: Discover, Movies, TV shows ------------------------------------------------------------------

    /** One row of posters: the focused one opens out to its wide picture. */
    static final class Row {
        String name, note;
        boolean numbered;
        List<Title> items = new ArrayList<>();
        TextView label;
        FrameLayout view;
        LinearLayout strip;
    }

    static final class PosterPage extends Page {
        private static final int NARROW = 150, WIDE = 400, TALL = 225, GAP = 20;
        private final String crumb;
        private final int crumbColour;
        private final String[] tabs;
        private final java.util.function.Function<Integer, List<Row>> source;
        private final int[] rowY;
        private final List<Row> rows = new ArrayList<>();
        private LinearLayout tabBar;
        private View lens;
        private int tab, row, idx;
        private boolean onTabs;
        private int generation;
        private final Runnable heroLater = this::heroNow;

        /**
         * tabs null for a page without tabs; source gives a tab's rows (off the main thread); rowY the top of each row's
         * name, in design pixels.
         */
        PosterPage(HomeV2 h, String crumb, int crumbColour, String[] tabs, int[] rowY, java.util.function.Function<Integer, List<Row>> source) {
            super(h);
            this.crumb = crumb;
            this.crumbColour = crumbColour;
            this.tabs = tabs;
            this.rowY = rowY;
            this.source = source;
        }

        @Override void open() {
            super.open();
            if (tabs != null) {
                FrameLayout track = new FrameLayout(h.a);
                lens = new View(h.a);
                GradientDrawable g = h.fill(25, 0x29FFFFFF);
                g.setStroke(px(1.5f), 0x73FFFFFF);
                lens.setBackground(g);
                track.addView(lens, new FrameLayout.LayoutParams(0, px(50)));
                tabBar = new LinearLayout(h.a);
                tabBar.setOrientation(LinearLayout.HORIZONTAL);
                for (String t : tabs) {
                    TextView v = h.text(22, h.regular, 0xBFFFFFFF);
                    v.setText(t);
                    v.setGravity(Gravity.CENTER);
                    v.setPadding(px(24), 0, px(24), 0);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(50));
                    lp.rightMargin = px(6);
                    tabBar.addView(v, lp);
                }
                track.addView(tabBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(50)));
                add(track, 216, 516, -2, 50);
                tabBar.post(this::moveLens);
            }
            for (int i = 0; i < rowY.length; i++) {
                Row r = new Row();
                r.label = h.text(24, h.semi, Color.WHITE);
                add(r.label, 216, rowY[i], 1500, 34);
                r.view = new FrameLayout(h.a);
                r.view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                r.view.setClipToOutline(true);
                r.strip = new LinearLayout(h.a);
                r.strip.setOrientation(LinearLayout.HORIZONTAL);
                r.strip.setGravity(Gravity.BOTTOM);
                FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(TALL + 12));
                sl.leftMargin = px(12);
                sl.topMargin = px(6);
                r.view.addView(r.strip, sl);
                add(r.view, 204, rowY[i] + (tabs != null ? 62 : 40) - 6, 1632, TALL + 24);
                rows.add(r);
            }
            onTabs = false;
            load(0);
        }

        private void load(int t) {
            tab = t;
            int gen = ++generation;
            for (Row r : rows) r.strip.animate().alpha(0.25f).setDuration(150).start();
            h.io.execute(() -> {
                List<Row> got = source.apply(t);
                h.main.post(() -> {
                    if (gen != generation) return;
                    for (int i = 0; i < rows.size(); i++) {
                        Row r = rows.get(i);
                        Row g = i < got.size() ? got.get(i) : new Row();
                        r.items = g.items;
                        r.numbered = g.numbered;
                        r.label.setText(g.name == null ? "" : g.name);
                        if (g.note != null) {
                            android.text.SpannableString sp = new android.text.SpannableString(g.name + "  · " + g.note);
                            sp.setSpan(new android.text.style.ForegroundColorSpan(0x8CFFFFFF), g.name.length(), sp.length(), 0);
                            sp.setSpan(new android.text.style.AbsoluteSizeSpan(px(18)), g.name.length(), sp.length(), 0);
                            r.label.setText(sp);
                        }
                        fill(r);
                        r.strip.animate().alpha(1f).setDuration(200).start();
                    }
                    idx = 0;
                    if (!onTabs) row = 0;
                    paint();
                    heroNow();
                });
            });
        }

        private void fill(Row r) {
            r.strip.removeAllViews();
            r.strip.setTranslationX(0);
            for (int i = 0; i < r.items.size(); i++) {
                Title t = r.items.get(i);
                FrameLayout f = new FrameLayout(h.a);
                f.setClipToOutline(true);
                f.setOutlineProvider(h.round(14));
                f.setBackground(h.fill(14, 0xFF222433));
                ImageView poster = new ImageView(h.a);
                poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                f.addView(poster, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                h.image(poster, t.poster, px(NARROW * 2));
                ImageView wide = new ImageView(h.a);
                wide.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wide.setAlpha(0f);
                f.addView(wide, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                View shade = new View(h.a);
                shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0x8C000000}));
                f.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                if (t.vote > 0) {
                    TextView b = h.text(15, h.semi, Color.WHITE);
                    b.setText(String.format(Locale.UK, "★ %.1f", t.vote));
                    b.setPadding(px(8), px(3), px(8), px(2));
                    b.setBackground(h.fill(8, 0x8C000000));
                    FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
                    bl.topMargin = px(10);
                    bl.rightMargin = px(10);
                    f.addView(b, bl);
                }
                TextView name = h.text(18, h.semi, Color.WHITE);
                name.setText(t.title);
                name.setSingleLine(true);
                name.setEllipsize(TextUtils.TruncateAt.END);
                name.setShadowLayer(px(6), 0, px(1), 0xCC000000);
                name.setAlpha(0f);
                FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
                nl.leftMargin = px(14);
                nl.rightMargin = px(14);
                nl.bottomMargin = px(12);
                f.addView(name, nl);
                if (r.numbered && i < 10) {
                    TextView n = new TextView(h.a);
                    n.setText(String.valueOf(i + 1));
                    n.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(110));
                    n.setTypeface(h.semi);
                    n.setTextColor(0x00FFFFFF);
                    n.getPaint().setStyle(android.graphics.Paint.Style.STROKE);
                    n.getPaint().setStrokeWidth(px(3));
                    n.setTextColor(0xD9FFFFFF);
                    n.setIncludeFontPadding(false);
                    FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
                    ll.leftMargin = -px(4);
                    ll.bottomMargin = -px(18);
                    f.addView(n, ll);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(NARROW), px(TALL));
                lp.rightMargin = px(GAP);
                r.strip.addView(f, lp);
            }
        }

        private void moveLens() {
            if (tabBar == null || tab >= tabBar.getChildCount()) return;
            View t = tabBar.getChildAt(tab);
            ViewGroup.LayoutParams lp = lens.getLayoutParams();
            int from = lp.width;
            float fromX = lens.getTranslationX();
            ValueAnimator va = ValueAnimator.ofFloat(0, 1);
            va.setDuration(250);
            va.setInterpolator(HomeV2.SKY);
            va.addUpdateListener(an -> {
                float f = (float) an.getAnimatedValue();
                lp.width = Math.round(from + (t.getWidth() - from) * f);
                lens.setLayoutParams(lp);
                lens.setTranslationX(fromX + (t.getLeft() - fromX) * f);
            });
            va.start();
            for (int i = 0; i < tabBar.getChildCount(); i++) {
                TextView v = (TextView) tabBar.getChildAt(i);
                v.setTextColor(i == tab ? Color.WHITE : 0xBFFFFFFF);
                v.setTypeface(i == tab ? h.semi : h.regular);
            }
            GradientDrawable g = h.fill(25, onTabs ? 0xEBFFFFFF : 0x29FFFFFF);
            g.setStroke(px(1.5f), onTabs ? Color.WHITE : 0x73FFFFFF);
            lens.setBackground(g);
            if (onTabs) ((TextView) tabBar.getChildAt(tab)).setTextColor(0xFF1A1D24);
        }

        private void paint() {
            for (int ri = 0; ri < rows.size(); ri++) {
                Row r = rows.get(ri);
                for (int i = 0; i < r.strip.getChildCount(); i++) {
                    FrameLayout f = (FrameLayout) r.strip.getChildAt(i);
                    boolean on = !onTabs && ri == row && i == idx;
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) f.getLayoutParams();
                    int target = px(on ? WIDE : NARROW);
                    if (lp.width != target) {
                        int from = lp.width;
                        ValueAnimator va = ValueAnimator.ofInt(from, target);
                        va.setDuration(300);
                        va.setInterpolator(HomeV2.SKY);
                        va.addUpdateListener(an -> { lp.width = (int) an.getAnimatedValue(); f.setLayoutParams(lp); });
                        va.start();
                    }
                    ImageView wide = (ImageView) f.getChildAt(1);
                    Title t = r.items.get(i);
                    if (on && t.backdrop != null) {
                        h.image(wide, t.backdrop.replace("/w1280/", "/w780/"), px(WIDE * 2));
                        wide.animate().alpha(1f).setDuration(350).start();
                        wide.setScaleX(1.02f);
                        wide.setScaleY(1.02f);
                        wide.animate().scaleX(1.12f).scaleY(1.12f).setDuration(6000).setInterpolator(new android.view.animation.LinearInterpolator()).start();
                    } else {
                        wide.animate().alpha(0f).setDuration(200).start();
                    }
                    f.getChildAt(f.getChildCount() - (r.numbered && i < 10 ? 2 : 1)).animate().alpha(on ? 1f : 0f).setDuration(200).start();
                    f.setForeground(on ? h.ring(14) : null);
                }
                int first = !onTabs && ri == row ? Math.max(0, idx - 4) : 0;
                r.strip.animate().translationX(-first * px(NARROW + GAP)).setDuration(300).setInterpolator(HomeV2.SKY).start();
            }
            if (tabBar != null) moveLens();
        }

        private void heroNow() {
            if (row >= rows.size() || rows.get(row).items.isEmpty()) return;
            Title t = rows.get(row).items.get(Math.min(idx, rows.get(row).items.size() - 1));
            HomeV2.Tile tile = new HomeV2.Tile();
            tile.title = t.title;
            tile.heroTitle = t.title;
            tile.heroImage = t.backdrop != null ? t.backdrop : t.poster;
            tile.heroTop = crumb;
            String vote = t.vote > 0 ? String.format(Locale.UK, " · ★ %.1f", t.vote) : "";
            tile.heroMeta = TextUtils.join(" · ", nonEmpty(t.year, t.what, t.tv ? "Series" : "Film")) + vote;
            tile.heroText = t.overview;
            h.heroTopColour(crumbColour);
            h.hero(tile);
            h.io.execute(() -> {
                String logo = h.tmdbLogo(t.id, t.tv);
                if (logo == null) return;
                tile.heroLogo = logo;
                h.main.post(() -> {
                    Title now = rows.get(row).items.isEmpty() ? null : rows.get(row).items.get(Math.min(idx, rows.get(row).items.size() - 1));
                    if (now == t) h.hero(tile);
                });
            });
        }

        private static String[] nonEmpty(String... s) {
            List<String> out = new ArrayList<>();
            for (String x : s) if (x != null && !x.isEmpty()) out.add(x);
            return out.toArray(new String[0]);
        }

        @Override boolean key(KeyEvent e) {
            int code = e.getKeyCode();
            if (ok(code)) {
                if (e.getAction() == KeyEvent.ACTION_UP && !onTabs && row < rows.size() && idx < rows.get(row).items.size()) {
                    Title t = rows.get(row).items.get(idx);
                    String pkg = h.debridPackage();
                    if (pkg != null) {
                        AppSearchService.search(h.a, pkg, t.title, t.tv);
                        GoalWatchService.onAppOpened(h.a, pkg);
                    }
                }
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return code != KeyEvent.KEYCODE_BACK;
            int n = row < rows.size() ? rows.get(row).items.size() : 0;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (onTabs) { if (tab > 0) load(tab - 1); }
                    else if (idx > 0) idx--;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (onTabs) { if (tab < tabs.length - 1) load(tab + 1); }
                    else if (idx < n - 1) idx++;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (onTabs) return true;
                    if (row > 0) { row--; idx = Math.min(idx, Math.max(0, rows.get(row).items.size() - 1)); }
                    else if (tabs != null) onTabs = true;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (onTabs) onTabs = false;
                    else if (row < rows.size() - 1) { row++; idx = Math.min(idx, Math.max(0, rows.get(row).items.size() - 1)); }
                    break;
                default:
                    return false;
            }
            paint();
            h.main.removeCallbacks(heroLater);
            h.main.postDelayed(heroLater, 300);
            return true;
        }
    }

    static Row row(String name, String note, boolean numbered, List<Title> items) {
        Row r = new Row();
        r.name = name;
        r.note = note;
        r.numbered = numbered;
        r.items = items;
        return r;
    }

    static PosterPage discover(HomeV2 h) {
        String k = h.tmdbKey;
        String[] tabs = {"New releases", "Trending", "Netflix", "Disney+", "Prime Video", "Apple TV+"};
        int[] providers = {0, 0, 8, 337, 9, 350};
        return new PosterPage(h, "DISCOVER", 0xFFC9A7FF, tabs, new int[]{584 - 62}, t -> {
            List<Title> items;
            LocalDate today = LocalDate.now();
            if (t == 0) {
                items = mix(tmdb(k, "discover/movie?region=GB&with_release_type=4|5&release_date.gte=" + today.minusWeeks(6)
                                + "&release_date.lte=" + today + "&sort_by=popularity.desc", false),
                        tmdb(k, "discover/tv?first_air_date.gte=" + today.minusMonths(2) + "&sort_by=popularity.desc&with_original_language=en", true));
            } else if (t == 1) {
                items = tmdb(k, "trending/all/day", false);
            } else {
                String w = "&watch_region=GB&with_watch_providers=" + providers[t] + "&sort_by=popularity.desc";
                items = mix(tmdb(k, "discover/tv?without_genres=10763,10764,10767" + w, true), tmdb(k, "discover/movie?" + w.substring(1), false));
            }
            return Arrays.asList(row(null, null, false, items));
        });
    }

    static PosterPage movies(HomeV2 h) {
        String k = h.tmdbKey;
        return new PosterPage(h, "MOVIES", 0xFFFF4FA3, null, new int[]{504, 790}, t -> Arrays.asList(
                row("Popular in the UK", "popular right now", false, tmdb(k, "movie/popular?region=GB", false)),
                row("Top 10 films this week", null, true, tmdb(k, "trending/movie/week", false))));
    }

    static PosterPage shows(HomeV2 h) {
        String k = h.tmdbKey;
        return new PosterPage(h, "TV SHOWS", 0xFF4F8BFF, null, new int[]{504, 790}, t -> Arrays.asList(
                row("Top 10 series this week", null, true, tmdb(k, "trending/tv/week", true)),
                row("On TV now", "new episodes", false, tmdb(k, "tv/on_the_air?without_genres=10763,10764,10767", true))));
    }

    // ---- Sport -----------------------------------------------------------------------------------------------------

    static final class SportPage extends Page {
        private FrameLayout cards, fixtures;
        private final List<LinearLayout> strips = new ArrayList<>();
        private final List<List<HomeV2.Tile>> lists = new ArrayList<>();
        private int zone = -1, idx;   // -1: the match card's watch bar; 0 and 1: the fixture rows
        private TextView watch;
        private Runnable watchOk;

        SportPage(HomeV2 h) {
            super(h);
        }

        @Override void open() {
            super.open();
            cards = new FrameLayout(h.a);
            cards.setClipChildren(false);
            add(cards, 0, 0, 1920, 1080);
            fixtures = new FrameLayout(h.a);
            fixtures.setClipChildren(false);
            add(fixtures, 0, 0, 1920, 1080);
            fixtures.setTranslationY(px(660));
            String[] names = {"LIVE FOOTBALL · THIS WEEK", "MORE LIVE SPORT"};
            for (int i = 0; i < 2; i++) {
                TextView l = h.text(18, h.semi, 0xBFFFFFFF);
                l.setLetterSpacing(0.11f);
                l.setText(names[i]);
                addTo(fixtures, l, 216, 236 + i * 300, 1500, 30);
                FrameLayout view = new FrameLayout(h.a);
                view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                view.setClipToOutline(true);
                LinearLayout strip = new LinearLayout(h.a);
                strip.setOrientation(LinearLayout.HORIZONTAL);
                FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(216));
                sl.leftMargin = px(24);
                sl.topMargin = px(24);
                view.addView(strip, sl);
                addTo(fixtures, view, 192, 262 + i * 300, 1656, 268);
                strips.add(strip);
                lists.add(new ArrayList<>());
            }
            buildMatch();
            buildTable();
            buildF1();
            h.io.execute(() -> {
                List<HomeV2.Tile> all;
                try {
                    all = h.sport();
                } catch (Exception e) {
                    all = new ArrayList<>();
                }
                List<HomeV2.Tile> foot = new ArrayList<>(), other = new ArrayList<>();
                for (HomeV2.Tile t : all) ("football".equals(t.group) ? foot : other).add(t);
                h.main.post(() -> {
                    lists.set(0, foot);
                    lists.set(1, other);
                    for (int i = 0; i < 2; i++) {
                        strips.get(i).removeAllViews();
                        for (HomeV2.Tile t : lists.get(i)) strips.get(i).addView(fixture(t));
                    }
                    paint();
                });
            });
            paint();
        }

        private void addTo(FrameLayout parent, View v, int x, int y, int w, int hgt) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(hgt));
            lp.leftMargin = px(x);
            lp.topMargin = px(y);
            parent.addView(v, lp);
        }

        private LinearLayout card(int x, int w) {
            LinearLayout c = new LinearLayout(h.a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(px(28), px(24), px(28), px(24));
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x21FFFFFF, 0x0DFFFFFF});
            g.setCornerRadius(px(26));
            g.setStroke(px(1.5f), 0x3DFFFFFF);
            c.setBackground(g);
            addTo(cards, c, x, 206, w, 640);
            return c;
        }

        private TextView kicker(LinearLayout c, String s) {
            TextView k = h.text(15, h.semi, 0xA6FFFFFF);
            k.setLetterSpacing(0.2f);
            k.setText(s);
            c.addView(k);
            return k;
        }

        private ImageView headLogo;

        /** The competition's name in its own lettering, after its logo (logo may be null, or set later through headLogo). */
        private TextView heading(LinearLayout c, String s, int font, String logo) {
            LinearLayout line = new LinearLayout(h.a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            headLogo = new ImageView(h.a);
            headLogo.setAdjustViewBounds(true);
            headLogo.setMaxHeight(px(40));
            headLogo.setMaxWidth(px(90));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(40));
            il.rightMargin = px(12);
            line.addView(headLogo, il);
            if (logo != null) h.image(headLogo, logo, px(90));
            TextView t = h.text(26, h.a.getResources().getFont(font), Color.WHITE);
            t.setText(s);
            line.addView(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(8);
            lp.bottomMargin = px(10);
            c.addView(line, lp);
            return t;
        }

        // The match of the day: live first, else the latest result, else the next kick-off.
        private void buildMatch() {
            LinearLayout c = card(216, 592);
            TextView k = kicker(c, "MATCH OF THE DAY");
            TextView comp = heading(c, "Loading…", R.font.brand_plain, null);
            ImageView compLogo = headLogo;
            LinearLayout score = new LinearLayout(h.a);
            score.setOrientation(LinearLayout.HORIZONTAL);
            score.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = px(14);
            c.addView(score, sl);
            ImageView hc = crest(), ac = crest();
            TextView hn = h.text(24, h.regular, 0xCCFFFFFF), an = h.text(24, h.regular, 0xCCFFFFFF);
            TextView n = h.text(84, h.semi, Color.WHITE);
            n.setLetterSpacing(-0.02f);
            score.addView(team(hc, hn));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nl.leftMargin = px(28);
            nl.rightMargin = px(28);
            score.addView(n, nl);
            score.addView(team(ac, an));
            TextView res = h.text(21, h.semi, 0xFF3DDC84);
            res.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = px(8);
            c.addView(res, rl);
            LinearLayout poss = new LinearLayout(h.a);
            poss.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pl.topMargin = px(20);
            c.addView(poss, pl);
            LinearLayout events = new LinearLayout(h.a);
            events.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            el.topMargin = px(16);
            c.addView(events, el);
            watch = h.text(20, h.regular, Color.WHITE);
            watch.setGravity(Gravity.CENTER);
            watch.setBackground(h.fill(30, 0x1FFFFFFF));
            c.addView(watch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(60)));
            watch.setText("Finding the channel…");

            String[] leagues = h.a.getResources().getStringArray(R.array.score_leagues);
            String[] footballTv = h.a.getResources().getStringArray(R.array.football_tv_channels);
            h.io.execute(() -> {
                List<ScoresTicker.Game> games = ScoresTicker.load(leagues);
                ScoresTicker.Game best = null;
                long now = System.currentTimeMillis();
                for (ScoresTicker.Game g : games) {
                    if (g.drivers != null || g.text != null || g.home == null) continue;
                    if (best == null) { best = g; continue; }
                    int rank = g.state == 0 ? 0 : g.state == 2 ? 1 : 2, br = best.state == 0 ? 0 : best.state == 2 ? 1 : 2;
                    if (rank < br || rank == br && (rank == 1 ? g.kickoff > best.kickoff : g.kickoff < best.kickoff)) best = g;
                }
                if (best == null) {
                    h.main.post(() -> { comp.setText("No games today"); watch.setText(""); });
                    return;
                }
                ScoresTicker.Game g = best;
                String[] summary = summary(g);   // {home possession, away possession, shootout home, shootout away}
                TvGuide.Channel ch = TvGuide.find(footballTv, g.homeName, g.awayName, g.kickoff);
                h.main.post(() -> {
                    String when = g.state == 0 ? "LIVE" : g.state == 2 ? "FULL TIME" : "KICK-OFF " +
                            DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(g.kickoff)).toUpperCase(Locale.UK);
                    k.setText(when);
                    if (g.state == 0) k.setTextColor(0xFFFF3B4A);
                    comp.setText(g.league == null ? "" : g.league);
                    if (g.leagueLogo != null) h.image(compLogo, g.leagueLogo, px(90));
                    h.image(hc, g.homeLogo, px(172));
                    h.image(ac, g.awayLogo, px(172));
                    hn.setText(g.homeName != null ? short_(g.homeName) : g.home);
                    an.setText(g.awayName != null ? short_(g.awayName) : g.away);
                    n.setText(g.state == 1 ? "v" : g.homeScore + " – " + g.awayScore);
                    String r = g.detail == null ? "" : g.detail;
                    if (summary[2] != null) {
                        int sh = Integer.parseInt(summary[2]), sa = Integer.parseInt(summary[3]);
                        r = "Full time · " + (sh > sa ? short_(g.homeName) : short_(g.awayName)) + " win " + Math.max(sh, sa) + "–" + Math.min(sh, sa) + " on penalties";
                    }
                    res.setText(r);
                    if (summary[0] != null) possession(poss, summary[0], summary[1], g.homeColor, g.awayColor);
                    for (String[] goal : g.goals) {
                        if (events.getChildCount() >= 4) break;
                        TextView ev = h.text(19, h.regular, Color.WHITE);
                        String tag = "pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (og)" : "";
                        ev.setText(goal[2] + "   ⚽ " + goal[1] + tag);
                        ev.setGravity("home".equals(goal[3]) ? Gravity.START : Gravity.END);
                        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        gl.bottomMargin = px(8);
                        events.addView(ev, gl);
                    }
                    if (ch != null) {
                        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
                        b.append(g.state == 0 ? "LIVE on  " : g.state == 1 ? "On  " : "Was on  ");
                        int at = b.length();
                        b.append("  ");
                        b.append("   ·   Press OK");
                        watch.setText(b);
                        loadSpan(watch, b, at, ch.logo);
                        watchOk = () -> h.tune(ch.name, ch.logo);
                    } else {
                        watch.setText(g.state == 2 ? "Full time" : "Not on UK TV");
                    }
                });
            });
        }

        /** Puts the channel's logo into the watch bar's text where the two spaces are. */
        private void loadSpan(TextView v, android.text.SpannableStringBuilder b, int at, String url) {
            ImageView probe = new ImageView(h.a);
            h.image(probe, url, px(200), () -> {
                android.graphics.drawable.Drawable d = probe.getDrawable();
                if (d == null) return;
                int hgt = px(26), w = Math.round(hgt * d.getIntrinsicWidth() / (float) Math.max(1, d.getIntrinsicHeight()));
                d.setBounds(0, 0, w, hgt);
                b.setSpan(new android.text.style.ImageSpan(d, android.text.style.ImageSpan.ALIGN_CENTER), at, at + 2, 0);
                v.setText(b);
            });
        }

        private static String short_(String club) {
            if (club == null) return "";
            return club.replace("Wolverhampton Wanderers", "Wolves").replace("Brighton & Hove Albion", "Brighton").replace("Tottenham Hotspur", "Spurs")
                    .replace("Manchester United", "Man United").replace("Manchester City", "Man City").replace("Nottingham Forest", "Nott'm Forest")
                    .replace(" AFC", "").replace(" FC", "");
        }

        private ImageView crest() {
            ImageView v = new ImageView(h.a);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return v;
        }

        private LinearLayout team(ImageView crest, TextView name) {
            LinearLayout t = new LinearLayout(h.a);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.addView(crest, new LinearLayout.LayoutParams(px(86), px(86)));
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(150), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(6);
            t.addView(name, lp);
            return t;
        }

        private void possession(LinearLayout into, String home, String away, int hc, int ac) {
            LinearLayout line = new LinearLayout(h.a);
            line.setOrientation(LinearLayout.HORIZONTAL);
            TextView a = h.text(17, h.regular, 0xBFFFFFFF), m = h.text(17, h.regular, 0xBFFFFFFF), b = h.text(17, h.regular, 0xBFFFFFFF);
            a.setText(home + "%");
            m.setText("Possession");
            m.setGravity(Gravity.CENTER);
            b.setText(away + "%");
            b.setGravity(Gravity.END);
            line.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(m, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            into.addView(line);
            LinearLayout bar = new LinearLayout(h.a);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setClipToOutline(true);
            bar.setOutlineProvider(h.round(4));
            View x = new View(h.a), y = new View(h.a);
            x.setBackgroundColor(hc != 0 ? 0xFF000000 | hc : 0xFFC8102E);
            int hue = hc != 0 ? 0xFF000000 | hc : 0xFFC8102E;
            int other = ac != 0 ? 0xFF000000 | ac : 0xFF1D4ED8;
            // Two clubs in much the same colour: the away side in white, so the bar still splits.
            if (Math.abs(Color.red(hue) - Color.red(other)) + Math.abs(Color.green(hue) - Color.green(other)) + Math.abs(Color.blue(hue) - Color.blue(other)) < 160) other = 0xE6FFFFFF;
            y.setBackgroundColor(other);
            float hp = parse(home), ap = parse(away);
            bar.addView(x, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, hp)));
            bar.addView(y, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, ap)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(8));
            bl.topMargin = px(6);
            into.addView(bar, bl);
        }

        private static float parse(String s) {
            try { return Float.parseFloat(s.replace("%", "")); } catch (Exception e) { return 50; }
        }

        /** {home possession, away possession, home shoot-out goals, away shoot-out goals}; any may be null. */
        private static String[] summary(ScoresTicker.Game g) {
            String[] out = new String[4];
            if (g.slug == null || g.id == null || g.state == 1) return out;
            try {
                JSONObject s = new JSONObject(HomeV2.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + g.slug + "/summary?event=" + g.id));
                JSONArray teams = s.optJSONObject("boxscore") == null ? null : s.getJSONObject("boxscore").optJSONArray("teams");
                for (int i = 0; teams != null && i < teams.length(); i++) {
                    JSONObject t = teams.getJSONObject(i);
                    String id = t.optJSONObject("team") == null ? "" : t.getJSONObject("team").optString("id");
                    JSONArray st = t.optJSONArray("statistics");
                    for (int k = 0; st != null && k < st.length(); k++) {
                        if (!"possessionPct".equals(st.getJSONObject(k).optString("name"))) continue;
                        out[id.equals(g.homeId) ? 0 : 1] = st.getJSONObject(k).optString("displayValue");
                    }
                }
                JSONObject comp = s.optJSONObject("header") == null ? null : s.getJSONObject("header").optJSONArray("competitions").optJSONObject(0);
                JSONArray cs = comp == null ? null : comp.optJSONArray("competitors");
                for (int i = 0; cs != null && i < cs.length(); i++) {
                    JSONObject c = cs.getJSONObject(i);
                    if (!c.has("shootoutScore")) continue;
                    out["home".equals(c.optString("homeAway")) ? 2 : 3] = String.valueOf(c.optInt("shootoutScore"));
                }
                if (out[2] == null || out[3] == null) { out[2] = null; out[3] = null; }
            } catch (Exception ignored) {
            }
            return out;
        }

        private void buildTable() {
            LinearLayout c = card(832, 518);
            kicker(c, "LIVE TABLE");
            heading(c, "Premier League", R.font.brand_premier, "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/23.png");
            LinearLayout list = new LinearLayout(h.a);
            list.setOrientation(LinearLayout.VERTICAL);
            c.addView(list);
            list.addView(tableRow(null, "", "CLUB", "P", "GD", "PTS", true));
            h.io.execute(() -> {
                List<String[]> rows = new ArrayList<>();
                try {
                    JSONObject root = new JSONObject(HomeV2.get("https://site.api.espn.com/apis/v2/sports/soccer/eng.1/standings"));
                    JSONArray entries = root.getJSONArray("children").getJSONObject(0).getJSONObject("standings").getJSONArray("entries");
                    for (int i = 0; i < entries.length(); i++) {
                        JSONObject e = entries.getJSONObject(i);
                        JSONObject team = e.getJSONObject("team");
                        Map<String, String> st = new HashMap<>();
                        JSONArray stats = e.optJSONArray("stats");
                        for (int k = 0; stats != null && k < stats.length(); k++)
                            st.put(stats.getJSONObject(k).optString("name"), stats.getJSONObject(k).optString("displayValue"));
                        JSONArray tl = team.optJSONArray("logos");
                        rows.add(new String[]{st.getOrDefault("rank", ""), team.optString("displayName"), st.getOrDefault("gamesPlayed", ""),
                                st.getOrDefault("pointDifferential", ""), st.getOrDefault("points", ""),
                                tl != null && tl.length() > 0 ? tl.getJSONObject(0).optString("href") : null});
                    }
                    rows.sort((x, y) -> Integer.compare(num(x[0]), num(y[0])));
                } catch (Exception ignored) {
                }
                h.main.post(() -> {
                    for (int i = 0; i < Math.min(10, rows.size()); i++) {
                        String[] r = rows.get(i);
                        list.addView(tableRow(r[5], r[0], short_(r[1]), r[2], r[3], r[4], false));
                    }
                });
            });
        }

        private static int num(String s) {
            try { return Integer.parseInt(s.replace("+", "").trim()); } catch (Exception e) { return 99; }
        }

        private View tableRow(String logo, String pos, String club, String p, String gd, String pts, boolean head) {
            LinearLayout r = new LinearLayout(h.a);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            int size = head ? 14 : 20;
            int col = head ? 0x8CFFFFFF : 0xBFFFFFFF;
            TextView a = h.text(size, h.regular, 0x99FFFFFF);
            a.setText(pos);
            r.addView(a, new LinearLayout.LayoutParams(px(34), ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!head) {
                ImageView l = new ImageView(h.a);
                l.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(26), px(26));
                ll.rightMargin = px(10);
                r.addView(l, ll);
                if (logo != null) h.image(l, logo, px(52));
            }
            TextView name = h.text(size, h.regular, head ? col : Color.WHITE);
            name.setText(club);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            if (head) name.setLetterSpacing(0.14f);
            r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            for (String v : new String[]{p, gd, pts}) {
                TextView t = h.text(size, v == pts && !head ? h.semi : h.regular, v == pts && !head ? Color.WHITE : col);
                t.setText(!head && v == gd && !gd.startsWith("-") && !gd.equals("0") ? "+" + gd.replace("+", "") : v);
                t.setGravity(Gravity.END);
                r.addView(t, new LinearLayout.LayoutParams(px(52), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            r.setPadding(px(6), px(head ? 4 : 7), px(6), px(head ? 2 : 7));
            if (!head) {
                GradientDrawable g = new GradientDrawable();
                g.setColor("1".equals(pos) ? 0x1AFFFFFF : 0x00000000);
                g.setCornerRadius(px(10));
                r.setBackground(g);
            }
            return r;
        }

        private void buildF1() {
            LinearLayout c = card(1374, 450);
            kicker(c, "DRIVERS' STANDINGS");
            TextView head = heading(c, "Formula 1", R.font.brand_f1, "https://a.espncdn.com/i/teamlogos/leagues/500-dark/f1.png");
            LinearLayout list = new LinearLayout(h.a);
            list.setOrientation(LinearLayout.VERTICAL);
            c.addView(list);
            h.io.execute(() -> {
                List<String[]> rows = new ArrayList<>();
                String round = "";
                try {
                    JSONObject root = new JSONObject(HomeV2.get("https://api.jolpi.ca/ergast/f1/current/driverStandings.json"));
                    JSONObject l = root.getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0);
                    round = l.optString("round");
                    JSONArray st = l.getJSONArray("DriverStandings");
                    for (int i = 0; i < Math.min(10, st.length()); i++) {
                        JSONObject x = st.getJSONObject(i);
                        JSONObject dr = x.getJSONObject("Driver");
                        JSONArray cons = x.getJSONArray("Constructors");
                        String team = cons.getJSONObject(cons.length() - 1).optString("constructorId");
                        rows.add(new String[]{x.optString("position"), dr.optString("givenName").substring(0, 1) + ". " + dr.optString("familyName"),
                                x.optString("points"), team});
                    }
                } catch (Exception ignored) {
                }
                String r = round;
                h.main.post(() -> {
                    if (!r.isEmpty()) head.setText("after round " + r);
                    for (String[] x : rows) list.addView(driver(x));
                });
            });
        }

        private static int teamColour(String team) {
            switch (team) {
                case "mercedes": return 0xFF27F4D2;
                case "ferrari": return 0xFFE80020;
                case "red_bull": return 0xFF3671C6;
                case "mclaren": return 0xFFFF8000;
                case "aston_martin": return 0xFF229971;
                case "alpine": return 0xFF0093CC;
                case "williams": return 0xFF64C4FF;
                case "rb": return 0xFF6692FF;
                case "sauber": case "audi": return 0xFF52E252;
                case "haas": return 0xFFB6BABD;
                case "cadillac": return 0xFFC0C0C0;
                default: return 0xFFFFFFFF;
            }
        }

        private View driver(String[] x) {
            LinearLayout r = new LinearLayout(h.a);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px(4), px(7), px(4), px(7));
            TextView p = h.text(20, h.regular, 0x99FFFFFF);
            p.setText(x[0]);
            r.addView(p, new LinearLayout.LayoutParams(px(34), ViewGroup.LayoutParams.WRAP_CONTENT));
            View bar = new View(h.a);
            bar.setBackground(h.fill(3, teamColour(x[3])));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(5), px(22));
            bl.rightMargin = px(12);
            r.addView(bar, bl);
            ImageView team = new ImageView(h.a);
            team.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(28), px(28));
            tl.rightMargin = px(12);
            r.addView(team, tl);
            String logo = F1Results.logo(x[3]);
            if (logo != null) h.image(team, logo, px(56));
            TextView n = h.text(20, h.regular, Color.WHITE);
            n.setText(x[1]);
            n.setSingleLine(true);
            r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView pts = h.text(20, h.semi, Color.WHITE);
            pts.setText(x[2]);
            r.addView(pts);
            return r;
        }

        private View fixture(HomeV2.Tile t) {
            FrameLayout f = new FrameLayout(h.a);
            f.setClipToOutline(true);
            f.setOutlineProvider(h.round(16));
            f.setBackground(h.fill(16, 0xFF15182E));
            ImageView pic = new ImageView(h.a);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(pic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            h.image(pic, t.image, px(384));
            View shade = new View(h.a);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x40000000, 0x00000000, 0x00000000, 0xD9000000}));
            f.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TextView when = h.text(16, h.semi, Color.WHITE);
            when.setText(t.chip);
            when.setLetterSpacing(0.06f);
            when.setPadding(px(10), px(3), px(10), px(2));
            when.setBackground(h.fill(8, "LIVE NOW".equals(t.chip) ? 0xFFE0263A : 0xB3000000));
            FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
            wl.leftMargin = px(12);
            wl.topMargin = px(12);
            f.addView(when, wl);
            if (t.compLogo != null) {
                FrameLayout chip = new FrameLayout(h.a);
                chip.setBackground(h.fill(10, Color.WHITE));
                ImageView l = new ImageView(h.a);
                l.setScaleType(ImageView.ScaleType.FIT_CENTER);
                chip.addView(l, new FrameLayout.LayoutParams(px(32), px(32), Gravity.CENTER));
                h.image(l, t.compLogo, px(64));
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(44), px(44), Gravity.TOP | Gravity.END);
                cl.rightMargin = px(12);
                cl.topMargin = px(10);
                f.addView(chip, cl);
            }
            LinearLayout cap = new LinearLayout(h.a);
            cap.setOrientation(LinearLayout.VERTICAL);
            TextView n = h.text(22, h.semi, Color.WHITE);
            n.setText(t.title);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            cap.addView(n);
            LinearLayout on = new LinearLayout(h.a);
            on.setOrientation(LinearLayout.HORIZONTAL);
            on.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(h.a);
            dot.setBackground(h.fill(5, 0xFFFF3B4A));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(9), px(9));
            dl.rightMargin = px(8);
            on.addView(dot, dl);
            TextView lo = h.text(16, h.regular, 0xD9FFFFFF);
            lo.setText("Live on ");
            on.addView(lo);
            ImageView ch = new ImageView(h.a);
            ch.setAdjustViewBounds(true);
            ch.setMaxHeight(px(18));
            ch.setMaxWidth(px(180));
            on.addView(ch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(18)));
            h.image(ch, t.badge, px(180));
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ol.topMargin = px(4);
            cap.addView(on, ol);
            FrameLayout.LayoutParams capl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            capl.leftMargin = px(16);
            capl.rightMargin = px(14);
            capl.bottomMargin = px(12);
            f.addView(cap, capl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(384), px(216));
            lp.rightMargin = px(24);
            f.setLayoutParams(lp);
            return f;
        }

        private void paint() {
            boolean down = zone >= 0;
            cards.animate().alpha(down ? 0f : 1f).translationY(down ? -px(80) : 0).setDuration(350).setInterpolator(HomeV2.SKY).start();
            fixtures.animate().translationY(down ? 0 : px(660)).setDuration(400).setInterpolator(HomeV2.SKY).start();
            GradientDrawable wb = h.fill(30, zone == -1 ? 0xEBFFFFFF : 0x1FFFFFFF);
            wb.setStroke(px(1.5f), zone == -1 ? Color.WHITE : 0x4DFFFFFF);
            watch.setBackground(wb);
            watch.setTextColor(zone == -1 ? 0xFF1A1D24 : Color.WHITE);
            for (int r = 0; r < strips.size(); r++) {
                LinearLayout s = strips.get(r);
                for (int i = 0; i < s.getChildCount(); i++) {
                    FrameLayout f = (FrameLayout) s.getChildAt(i);
                    boolean on = zone == r && i == idx;
                    f.animate().scaleX(on ? 1.07f : 1f).scaleY(on ? 1.07f : 1f).setDuration(150).setInterpolator(HomeV2.SKY).start();
                    f.setForeground(on ? h.ring(16) : null);
                    f.setElevation(on ? px(8) : 0);
                }
                int first = zone == r ? Math.max(0, idx - 3) : 0;
                s.animate().translationX(-first * px(408)).setDuration(300).setInterpolator(HomeV2.SKY).start();
            }
        }

        @Override boolean key(KeyEvent e) {
            int code = e.getKeyCode();
            if (ok(code)) {
                if (e.getAction() == KeyEvent.ACTION_UP) {
                    if (zone == -1) { if (watchOk != null) watchOk.run(); }
                    else if (idx < lists.get(zone).size() && lists.get(zone).get(idx).ok != null) lists.get(zone).get(idx).ok.run();
                }
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return code != KeyEvent.KEYCODE_BACK;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (zone < 1) { zone++; idx = 0; }
                    if (zone >= 0 && lists.get(zone).isEmpty() && zone < 1) zone++;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (zone >= 0) { zone--; idx = 0; }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (zone >= 0 && idx > 0) idx--;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (zone >= 0 && idx < lists.get(zone).size() - 1) idx++;
                    break;
                default:
                    return false;
            }
            paint();
            return true;
        }
    }
}
