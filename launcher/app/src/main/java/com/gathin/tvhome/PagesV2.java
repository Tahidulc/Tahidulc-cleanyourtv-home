package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
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

    /**
     * A poster in the slider: laid out once at its widest (16:9) and drawn as wide as it is now (vis) through its
     * rounded outline, so opening out or folding back changes only the outline and its focus ring, never the layout
     * (a width changed every frame made the whole screen lay itself out again on every frame, and the slider stuttered).
     * Its shade is kept to hand, not looked up each frame.
     */
    static final class Tile extends FrameLayout {
        /** How wide it is drawn, in pixels; -1: all of it. */
        float vis = -1;
        View dim;
        /** Its picture's fetch, held back until the slider brings it near the screen (null: fetched). */
        Runnable load;
        /** Whether the slider drew it on the last frame: one off the screen then and now is left alone. */
        boolean shown = true;
        private final float radius;

        Tile(android.content.Context c, float radius) {
            super(c);
            this.radius = radius;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    int w = vis < 0 ? v.getWidth() : Math.min(v.getWidth(), Math.round(vis));
                    o.setRoundRect(0, 0, Math.max(1, w), v.getHeight(), Tile.this.radius);
                }
            });
            setClipToOutline(true);
        }

        void vis(float w) {
            if (Math.abs(w - vis) < 0.5f) return;
            vis = w;
            invalidateOutline();
            if (getForeground() != null) invalidate();
        }

        /** The focus ring drawn round what shows (its drawn width), not the whole laid-out box. */
        @Override public void setForeground(android.graphics.drawable.Drawable d) {
            super.setForeground(d == null || d instanceof VisRing ? d : new VisRing(d, this));
        }

        @Override public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /** A drawable drawn over a Tile only as wide as the tile shows. */
    static final class VisRing extends android.graphics.drawable.Drawable {
        private final android.graphics.drawable.Drawable inner;
        private final Tile tile;

        VisRing(android.graphics.drawable.Drawable inner, Tile tile) {
            this.inner = inner;
            this.tile = tile;
        }

        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            int w = tile.vis < 0 ? b.width() : Math.min(b.width(), Math.round(tile.vis));
            inner.setBounds(b.left, b.top, b.left + w, b.bottom);
            inner.draw(c);
        }

        @Override public void setAlpha(int a) { inner.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { inner.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    abstract static class Page {
        final HomeV2 h;
        final FrameLayout layer;

        Page(HomeV2 h) {
            this.h = h;
            // lite_home: its fade in and out drawn straight onto the screen, each piece with the alpha, instead of the
            // whole page first being painted into a screen-sized copy on every frame of the fade.
            layer = new FrameLayout(h.a) {
                @Override public boolean hasOverlappingRendering() {
                    return !h.lite;
                }
            };
            layer.setClipChildren(false);
        }

        void open() {
            h.root.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // Option B's top bar leaves room at the top: the whole page sits that much higher.
            float base = -px(h.pageLift());
            // In as an iPhone's screen: rising a little into place on a spring as it fades up (and, but on the lighter
            // TVs, growing the last few per cent), drawn into a layer while it moves.
            layer.setTranslationY(base + px(36));
            layer.setAlpha(0f);
            Fluid.layered(layer, 480);
            layer.animate().alpha(1f).setDuration(240).setInterpolator(Fluid.EASE).start();
            Fluid.spring(layer, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, base);
            if (!h.lite) {
                layer.setPivotX(h.root.getWidth() / 2f);
                layer.setPivotY(h.root.getHeight() * 0.7f);
                layer.setScaleX(0.985f);
                layer.setScaleY(0.985f);
                Fluid.spring(layer, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, 1f);
                Fluid.spring(layer, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1f);
            }
        }

        void close() {
            // Out the same way backwards, quicker.
            Fluid.layered(layer, 260);
            layer.animate().alpha(0f).translationY(layer.getTranslationY() + px(20)).setDuration(200).setInterpolator(Fluid.EASE_IN)
                    .withEndAction(() -> h.root.removeView(layer)).start();
        }

        /** True when the page used the key; Back is handled by Home. */
        abstract boolean key(KeyEvent e);

        /** The focus came into the page from the boxes under it (it was showing above them). */
        void entered() {
        }

        /** Whether the page grows into the dock's room when the dock tucks away (the pages of posters). */
        boolean growsDown() {
            return false;
        }

        /** The dock has tucked away (true) or come back: the page grows into the room, or goes back. */
        void room(boolean big) {
        }

        /** A title's details open under the hero: the page's own rows make way (true), or come back. */
        void makeWay(boolean away) {
        }

        /** Back pressed in the page: true when the page used it (else Home closes the page). */
        boolean back() {
            return false;
        }

        int px(float v) { return h.px(v); }

        void add(View v, int x, int y, int w, int hgt) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : px(wide(x, w)), hgt < 0 ? hgt : px(hgt));
            lp.leftMargin = px(across(x));
            lp.topMargin = px(y);
            layer.addView(v, lp);
        }

        /** As add, at exactly the place given: for a page laid out for the dock layout itself. */
        void addAt(View v, int x, int y, int w, int hgt) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : px(w), hgt < 0 ? hgt : px(hgt));
            lp.leftMargin = px(x);
            lp.topMargin = px(y);
            layer.addView(v, lp);
        }

        /**
         * The pages were laid out beside the rail of apps, from 216 to 1824. In the dock layout the rail has gone and
         * that span is 48 to 1872 (Home's own edges): a place in it is stretched across, so rows, tabs and cards use the
         * whole width (what sits at the screen's own edge, such as a full-screen frame at 0, stays where it is).
         */
        int across(int x) {
            return h.dockMode && x >= 180 ? Math.round(48 + (x - 216) * SPAN) : x;
        }

        int wide(int x, int w) {
            return h.dockMode && x >= 180 && w > 0 ? Math.round(w * SPAN) : w;
        }

        static final float SPAN = 1824f / 1608f;

        static boolean ok(int code) {
            return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        }
    }

    // ---- films and series from TMDB --------------------------------------------------------------------------------

    static final class Title {
        String title, poster, backdrop, year, overview, what;
        /** Its hero line without "Series"/"Film" (a new episode's line already says what it is). */
        boolean noKind;
        double vote;
        int id;
        boolean tv;
        /** Its official trailer on YouTube, once looked up ("" when TMDB has none). */
        String youtube;
        /** Continue watching: the app it was being watched in, which OK opens again. */
        String app;
        /** A label on the poster's top corner (Your shows: "NEW · S4 E1", "E7 TOMORROW"), its colours. */
        String badge;
        int badgeBg, badgeFg;
        /** A line along the poster's foot (Your shows: "S3 · 5 of 8"). */
        String foot;
        /** Fits tonight: its running time in minutes; the poster says when it would end if started now. 0: not shown. */
        int runtime;
        /** Your shows: how far through the season (0 to 1), a yellow bar along the foot; -1: the watched bar as usual. */
        float season = -1;
        /** The streaming service's logo on the poster's top corner (Fits tonight); 0: none. */
        int mark;
        /** What OK does instead of the page's own (Your shows, a series on none of the TV's apps). */
        Runnable ok;
    }

    /**
     * The title's trailers on YouTube from TMDB, comma-separated, best first: official trailers, then any trailer, then
     * teasers, then clips (the player moves on when YouTube will not play one in a tile). Any language when TMDB has
     * none in English. "" when there are none. Off the main thread.
     */
    static String trailer(String key, Title t) {
        String known = TRAILER_IDS.get((t.tv ? "t" : "m") + t.id);
        if (known != null) return known;
        try {
            JSONArray list = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (t.tv ? "tv/" : "movie/") + t.id
                    + "/videos?include_video_language=en,null&api_key=" + key)).optJSONArray("results");
            List<String>[] ranks = new List[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject v = list.getJSONObject(i);
                if (!"YouTube".equals(v.optString("site")) || v.optString("key").isEmpty()) continue;
                String type = v.optString("type");
                int r = "Trailer".equals(type) ? (v.optBoolean("official") ? 0 : 1) : "Teaser".equals(type) ? 2 : "Clip".equals(type) ? 3 : -1;
                if (r >= 0) ranks[r].add(v.optString("key"));
            }
            StringBuilder out = new StringBuilder();
            int n = 0;
            for (List<String> rank : ranks) for (String k : rank) {
                if (n++ >= 6) break;
                if (out.length() > 0) out.append(',');
                out.append(k);
            }
            TRAILER_IDS.put((t.tv ? "t" : "m") + t.id, out.toString());
            return out.toString();
        } catch (Exception e) {
            return "";   // not remembered: asked again next time
        }
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
    /** TMDB's lists, kept half an hour: a page opened again (or read ahead from Home) shows at once. */
    private static final Map<String, Object[]> LISTS = new HashMap<>();
    private static final long LIST_MS = 30 * 60_000L;

    static List<Title> tmdb(String key, String path, boolean tv) {
        String k = path + "|" + tv;
        synchronized (LISTS) {
            Object[] had = LISTS.get(k);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < LIST_MS) {
                @SuppressWarnings("unchecked") List<Title> list = (List<Title>) had[1];
                return new ArrayList<>(list);
            }
        }
        List<Title> got = readList(key, path, tv);
        if (!got.isEmpty()) synchronized (LISTS) { LISTS.put(k, new Object[]{System.currentTimeMillis(), got}); }
        return new ArrayList<>(got);
    }

    /** The most posters a row of the poster pages takes: two of TMDB's pages, so the row runs on well past the screen. */
    static final int LIST_MAX = 40;

    /**
     * A row's TMDB list: its first two pages (each kept as tmdb() keeps it), the second asked for at the same moment as
     * the first so the row waits no longer for twice the posters, anything on both once, up to LIST_MAX.
     */
    static List<Title> tmdbRow(String key, String path, boolean tv) {
        String next = path + (path.contains("?") ? "&" : "?") + "page=2";
        java.util.concurrent.CompletableFuture<List<Title>> second =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> tmdb(key, next, tv));
        List<Title> first = tmdb(key, path, tv), more;
        try {
            more = first.isEmpty() ? new ArrayList<>() : second.get(12, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            more = new ArrayList<>();
        }
        List<Title> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (List<Title> l : Arrays.asList(first, more)) {
            for (Title t : l) if (out.size() < LIST_MAX && seen.add((t.tv ? "tv" : "mv") + t.id)) out.add(t);
        }
        return out;
    }

    private static List<Title> readList(String key, String path, boolean tv) {
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

    /** Films and series taken in turn from two lists (up to LIST_MAX). */
    static List<Title> mix(List<Title> a, List<Title> b) {
        List<Title> out = new ArrayList<>();
        for (int i = 0; i < Math.max(a.size(), b.size()) && out.size() < LIST_MAX; i++) {
            if (i < a.size()) out.add(a.get(i));
            if (i < b.size()) out.add(b.get(i));
        }
        return out;
    }

    // ---- poster pages: Discover, Movies, TV shows ------------------------------------------------------------------

    /** One row of posters: the focused one opens out to its wide picture. */
    static final class Row {
        String name, note;
        /** What to say when the row has nothing in it (Continue Watching before anything is started); null: say nothing. */
        String empty;
        boolean numbered;
        /**
         * A panel of the tab's own over the hero's picture (Your shows' week, Fits tonight's evening), made on the main
         * thread for the title in focus (null when the tab has none): a screen-sized layer, the panel placed inside it.
         * The same view back for another title is left as it is.
         */
        java.util.function.BiFunction<HomeV2, Title, View> side;
        List<Title> items = new ArrayList<>();
        TextView label;
        FrameLayout view;
        LinearLayout strip;
    }

    static final class PosterPage extends Page {
        /**
         * Posters 2:3, widened 16:9, as tall as fits between the tabs and the boxes (beside the rail), or the dock (in
         * the dock layout, where the old boxes' room is free: 207 x 310, widened to 551).
         */
        final int NARROW, WIDE, TALL;
        static final int GAP = 20;
        /** The widest a poster is drawn, for reading its picture at the size it shows. */
        static final int NARROW_MOST = 207;
        private final String crumb;
        private final int crumbColour;
        private final String[] tabs;
        final java.util.function.Function<Integer, List<Row>> source;
        private final int[] rowY;
        private final List<Row> rows = new ArrayList<>();
        private LinearLayout tabBar;
        private android.widget.HorizontalScrollView tabScroll;
        private View lens;
        private int tab, row, idx;
        private boolean onTabs;
        private int generation;
        private final Runnable heroLater = this::heroNow;
        /** The tab the page opens on; the words shown where a row's posters would be when it has none. */
        int startTab;
        /**
         * The Kids page's shows at the top while the focus is on the tabs, and the family's children, when the TV has
         * their pictures, about above the tabs (KidsPlay); null on other pages.
         */
        KidsPlay play;
        /** The show at the top now (-1: a title's picture is there). */
        private int actShown = -1;
        private TextView emptyView;

        /** The dock away: the row a fifth as big again, down into the dock's room, ending above the dock's arrow. */
        private boolean big;
        static final float BIG = 1.2f;

        @Override boolean growsDown() {
            return h.dockMode && rowY.length == 1;
        }

        @Override void makeWay(boolean away) {
            List<View> all = new ArrayList<>();
            if (tabScroll != null) all.add(tabScroll);
            for (Row r : rows) { all.add(r.view); all.add(r.label); }
            if (emptyView != null) all.add(emptyView);
            for (View v : all) v.animate().alpha(away ? 0f : 1f).translationY(away ? px(30) : 0).setDuration(away ? 180 : 280).start();
        }

        /** The title the hero shows (the one focused in the row, or last focused before going up to the tabs). */
        private Title heroTitle() {
            if (row >= rows.size() || rows.get(row).items.isEmpty()) return null;
            return rows.get(row).items.get(Math.min(idx, rows.get(row).items.size() - 1));
        }

        @Override void room(boolean grow) {
            if (big == grow || rows.isEmpty()) return;
            big = grow;
            for (Row r : rows) {
                r.view.setPivotX(0f);
                r.view.setPivotY(bigPivot());
                r.view.animate().scaleX(grow ? big() : 1f).scaleY(grow ? big() : 1f).setDuration(grow ? 300 : 230)
                        .setInterpolator(HomeV2.SKY)
                        // The slider's glow follows the row as it grows or shrinks.
                        .setUpdateListener(slider() ? an -> slide(r, focusF, wideIdx, wideAmt) : null).start();
            }
            if (emptyView != null) emptyView.animate().translationY(grow ? px(40) : 0).setDuration(300).start();
            paint();
        }

        /**
         * How much the rows grow when the dock tucks away: the slider's one row more under option B's top bar, into the
         * room the page moved up by (its foot stays clear of the dock's chip).
         */
        private float big() {
            return slider() && h.corners ? BIG + (float) HomeV2.PAGE_LIFT / (TALL + 24) + SLIDER_MORE : BIG;
        }

        /**
         * The slider under option B's top bar grows a little more again (bigger posters), from a line 24 down its box
         * rather than its top: it grows up into the room under the tabs as well as down, its posters' foot still above
         * the dock's chip (at 990).
         */
        static final float SLIDER_MORE = 0.08f;

        private float bigPivot() {
            return slider() && h.corners ? px(24) : 0f;
        }

        PosterPage startOn(int t) {
            startTab = tabs == null ? 0 : Math.max(0, Math.min(t, tabs.length - 1));
            return this;
        }
        /** The rows, together, when there are more than fit: moved up as the focus goes down (null for two rows). */
        private FrameLayout column;

        private void addRow(View v, int x, int y, int w, int hgt) {
            if (column == null) { add(v, x, y, w, hgt); return; }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(wide(x, w)), px(hgt));
            lp.leftMargin = px(across(x));
            lp.topMargin = px(y);
            column.addView(v, lp);
        }

        // ---- trailers, as on v1: once the poster has widened, its trailer plays across it ----------------------

        /**
         * A moment after landing on a poster: soon enough to feel immediate, long enough that sliding along does not
         * start YouTube's player at every poster passed (its loading stalled the slide).
         */
        private static final long TRAILER_DELAY_MS = 650L;
        private final TilePlayer player;
        private FrameLayout trailerTile;
        private Runnable trailerSoon;

        private void trailerFor(FrameLayout tile, Title t, List<Title> row, int at) {
            if (tile == trailerTile) return;
            if (trailerSoon != null) h.main.removeCallbacks(trailerSoon);
            player.stop();
            trailerTile = tile;
            if (tile == null || t == null || t.id <= 0 || h.tmdbKey.isEmpty()) return;
            // The next few along are looked up now, so their trailers are ready when they are reached.
            for (int n = at + 1; n < Math.min(row.size(), at + 4); n++) {
                Title next = row.get(n);
                // On the pages' own pool: Home's single reader stays free for the hero's details and logos.
                if (next.youtube == null) TRAILERS.execute(() -> { if (next.youtube == null) next.youtube = trailer(h.tmdbKey, next); });
            }
            trailerSoon = () -> {
                if (trailerTile != tile) return;
                if (t.youtube != null) { if (!t.youtube.isEmpty()) player.trailer(tile, 0, t.youtube, tile.findViewWithTag("poster"), tile.findViewWithTag("wide")); return; }
                // On the trailers' own threads: behind Home's reader (the hero's pictures, logos and details) a slow
                // TV often never reached it before the next poster was picked.
                TRAILERS.execute(() -> {
                    String id = trailer(h.tmdbKey, t);
                    h.main.post(() -> {
                        t.youtube = id;
                        if (trailerTile == tile && !id.isEmpty()) player.trailer(tile, 0, id, tile.findViewWithTag("poster"), tile.findViewWithTag("wide"));
                    });
                });
            };
            h.main.postDelayed(trailerSoon, slider() ? SLIDER_TRAILER_MS : TRAILER_DELAY_MS);
        }

        @Override void close() {
            if (play != null) play.stop();
            trailerTile = null;
            if (trailerSoon != null) h.main.removeCallbacks(trailerSoon);
            cancelLand();
            player.stop();   // Home's own player: kept, with its page loaded, for Home and the next page
            super.close();
        }

        /**
         * tabs null for a page without tabs; source gives a tab's rows (off the main thread); rowY the top of each row's
         * name, in design pixels.
         */
        PosterPage(HomeV2 h, String crumb, int crumbColour, String[] tabs, int[] rowY, java.util.function.Function<Integer, List<Row>> source) {
            super(h);
            NARROW = h.dockMode ? NARROW_MOST : 180;
            WIDE = h.dockMode ? 551 : 480;
            TALL = h.dockMode ? 310 : 270;
            // Home's player, shared: a second YouTube page beside it never finished loading on the TCL, and the
            // trailers waited for it.
            player = h.players;
            this.crumb = crumb;
            this.crumbColour = crumbColour;
            this.tabs = tabs;
            this.rowY = rowY;
            this.source = source;
        }

        @Override void open() {
            super.open();
            player.warm();   // YouTube's player page loads now, so the first trailer starts at once
            if (tabs != null) {
                FrameLayout track = new FrameLayout(h.a);
                lens = new View(h.a);
                GradientDrawable g = h.fill(25, 0x29FFFFFF);
                g.setStroke(px(1.5f), 0x73FFFFFF);
                lens.setBackground(g);
                track.addView(lens, new FrameLayout.LayoutParams(0, px(50)));
                tabBar = new LinearLayout(h.a);
                // lite_home: the row of tab names as one finished picture; the lens slides over it.
                if (h.lite) tabBar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                tabBar.setOrientation(LinearLayout.HORIZONTAL);
                for (String t : tabs) {
                    TextView v = h.text(22, h.regular, 0xBFFFFFFF);
                    v.setText(title(t));
                    v.setGravity(Gravity.CENTER);
                    v.setPadding(px(24), 0, px(24), 0);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(50));
                    lp.rightMargin = px(6);
                    tabBar.addView(v, lp);
                }
                track.addView(tabBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(50)));
                // More tabs than the width (the genres): the bar slides along under the focus, cut off at the edges.
                tabScroll = new android.widget.HorizontalScrollView(h.a);
                tabScroll.setHorizontalScrollBarEnabled(false);
                tabScroll.setFocusable(false);
                tabScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
                // Cut off at its own edges, the tabs going out of sight fade away.
                tabScroll.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                tabScroll.setClipToOutline(true);
                // lite_home (the TCL): no faded edges (each is drawn through an extra layer of the bar, every frame).
                tabScroll.setHorizontalFadingEdgeEnabled(!h.lite);
                tabScroll.setFadingEdgeLength(px(70));
                tabScroll.addView(track, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(50)));
                add(tabScroll, 216, 516, 1608, 50);
                tabBar.post(this::moveLens);
            }
            if (rowY.length > 2) {
                column = new FrameLayout(h.a);
                column.setClipChildren(false);
                layer.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            for (int i = 0; i < rowY.length; i++) {
                Row r = new Row();
                r.label = h.text(24, h.semi, Color.WHITE);
                addRow(r.label, 216, rowY[i], 1500, 34);
                // Faded as a row of separate posters (they never overlap), with no offscreen copy per frame.
                r.view = new FrameLayout(h.a) {
                    @Override public boolean hasOverlappingRendering() {
                        return false;
                    }
                };
                r.view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                r.view.setClipToOutline(true);
                // Only the row's box clips: its strip runs on past the screen, and posters further along must still
                // draw when it slides left (clipped to the strip's own width they vanished while the focus went on).
                r.view.setClipChildren(false);
                r.strip = new LinearLayout(h.a) {
                    @Override public boolean hasOverlappingRendering() {
                        return false;
                    }
                };
                r.strip.setOrientation(LinearLayout.HORIZONTAL);
                r.strip.setGravity(Gravity.BOTTOM);
                FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(TALL + 12));
                sl.leftMargin = px(12);
                sl.topMargin = px(6);
                r.view.addView(r.strip, sl);
                // A row's name sits just over its posters (Discover's one row leaves room under its tabs).
                addRow(r.view, 204, rowY[i] + (tabs != null && rowY.length == 1 ? 62 : 40) - 6, 1632, TALL + 24);
                rows.add(r);
                // lite_home (the TCL): no glow (two lights over a thousand pixels across, blended on every frame of a
                // slide, were more than its graphics chip could keep up with).
                if (slider() && glow == null && !h.lite) {
                    // The slider's glow: a soft light in the focused poster's own colour, behind the row.
                    // Two lights, one over the other: a new colour fades in on the one not showing (each drawn once
                    // in its colour), not a gradient made again on every frame of the change.
                    FrameLayout g = new FrameLayout(h.a) {
                        @Override public boolean hasOverlappingRendering() {
                            return false;
                        }
                    };
                    for (int gi = 0; gi < 2; gi++) {
                        View one = new View(h.a);
                        GradientDrawable gd = new GradientDrawable();
                        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
                        gd.setGradientRadius(px(WIDE) * 0.72f);
                        gd.setColors(new int[]{0x00000000, 0x00000000});
                        one.setBackground(gd);
                        one.setAlpha(0f);
                        g.addView(one, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                    glow = g;
                    glowPaint = (GradientDrawable) g.getChildAt(0).getBackground();
                    // Square, twice the gradient's radius, so it fades out evenly before any edge.
                    layer.addView(glow, layer.indexOfChild(r.view), new FrameLayout.LayoutParams(px(WIDE * 1.44f), px(WIDE * 1.44f)));
                }
            }
            // Where a row has nothing (Continue Watching before anything is started): a line saying so.
            emptyView = h.text(24, h.regular, 0xB3FFFFFF);
            emptyView.setMaxLines(2);
            emptyView.setVisibility(View.GONE);
            add(emptyView, 216, rowY[0] + (tabs != null && rowY.length == 1 ? 62 : 40) + 70, 1200, 80);
            // A page with tabs opens on its first tab (or the one after an empty Continue Watching); Down goes into the
            // posters.
            onTabs = tabs != null;
            // Opened on its tabs: the row lies flat until Down goes onto it.
            flat = flatTo = onTabs ? 1f : 0f;
            load(startTab);
            if (play != null) {
                // Over the picture at the top, down to the line just above the tabs (feet on it); the description on the
                // left is left clear.
                addAt(play, 520, 0, 1400, 516);
                play.start();
            }
        }

        /** Tabs already read, kept while the page is open: going back to one is instant. */
        private final Map<Integer, List<Row>> loaded = new HashMap<>();
        private final Runnable loadNow = this::loadNow;

        /**
         * Tab t: the lens moves to it at once; its posters come from what was read before, else are read once the
         * focus stops on it (sliding along the tabs reads nothing in between, which made it stutter).
         */
        private void load(int t) {
            tab = t;
            int gen = ++generation;
            if (tabBar != null) moveLens();
            List<Row> have = loaded.get(t);
            h.main.removeCallbacks(loadNow);
            if (have != null) { show(have, gen); return; }
            for (Row r : rows) r.strip.animate().alpha(0.25f).setDuration(150).start();
            if (gen == 1) loadNow(); else h.main.postDelayed(loadNow, 200);
        }

        private void loadNow() {
            int t = tab, gen = generation;
            PAGES.execute(() -> {
                List<Row> got = source.apply(t);
                h.main.post(() -> {
                    loaded.put(t, got);
                    if (gen == generation) show(got, gen);
                    // The tabs either side read ahead, so the next step along shows at once.
                    for (int n : new int[]{t + 1, t - 1}) {
                        if (n < 0 || tabs == null || n >= tabs.length || loaded.containsKey(n)) continue;
                        PAGES.execute(() -> {
                            List<Row> ahead = source.apply(n);
                            // Their first posters fetched too, so the tab along shows its pictures at once.
                            int k = 0;
                            for (Row r : ahead) for (Title x : r.items) if (k++ < 10) h.prefetch(posterUrl(h, x), posterWidth(h));
                            h.main.post(() -> { if (!loaded.containsKey(n)) loaded.put(n, ahead); });
                        });
                    }
                });
            });
        }

        private void show(List<Row> got, int gen) {
                    if (gen != generation) return;
                    for (int i = 0; i < rows.size(); i++) {
                        Row r = rows.get(i);
                        Row g = i < got.size() ? got.get(i) : new Row();
                        r.items = g.items;
                        r.numbered = g.numbered;
                        r.side = g.side;
                        if (i == 0 && g.items.isEmpty()) side(r, null);
                        String name = title(g.name);
                        r.label.setText(name == null ? "" : name);
                        if (g.note != null && name != null) {
                            android.text.SpannableString sp = new android.text.SpannableString(name + "  · " + g.note);
                            sp.setSpan(new android.text.style.ForegroundColorSpan(0x8CFFFFFF), name.length(), sp.length(), 0);
                            sp.setSpan(new android.text.style.AbsoluteSizeSpan(px(18)), name.length(), sp.length(), 0);
                            r.label.setText(sp);
                        }
                        fill(r);
                        r.strip.animate().alpha(1f).setDuration(200).start();
                        if (i == 0) {
                            boolean none = g.items.isEmpty() && g.empty != null;
                            // "Hold OK…": the remote's key itself, not the word.
                            Keys.set(emptyView, none ? g.empty : "");
                            emptyView.setVisibility(none ? View.VISIBLE : View.GONE);
                        }
                    }
                    idx = 0;
                    if (!onTabs) row = 0;
                    // Come in from the boxes before the posters had arrived: onto the first poster now they have.
                    if (intoPosters && onTabs && !rows.isEmpty() && !rows.get(0).items.isEmpty()) { onTabs = false; row = 0; }
                    intoPosters = false;
                    paint();
                    heroNow();
        }

        /** Up (or OK) from the boxes: straight onto the first poster, not the tabs above them. */
        private boolean intoPosters;

        /** The tab's own panel over the hero's picture now (Row.side), or null. */
        private View sideView;

        /** The row's panel for the title in focus, in place of the one before (under the rows and tabs). */
        private void side(Row r, Title t) {
            View v = r == null || r.side == null ? null : r.side.apply(h, t);
            if (v == sideView) return;
            if (sideView != null && sideView.getParent() instanceof ViewGroup) ((ViewGroup) sideView.getParent()).removeView(sideView);
            sideView = v;
            if (v == null) return;
            if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
            layer.addView(v, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        @Override void entered() {
            if (onTabs && !rows.isEmpty() && !rows.get(0).items.isEmpty()) {
                onTabs = false;
                row = 0;
                idx = 0;
                paint();
            } else if (onTabs) {
                intoPosters = true;   // the tab's posters still coming: onto them when they arrive
            }
        }

        /** Films and series started in any app: TMDB id to how far in. */
        private Map<Integer, Float> watched = new HashMap<>();

        private void fill(Row r) {
            watched = WatchHistory.progress(h.a);
            r.strip.removeAllViews();
            r.strip.setTranslationX(0);
            boolean asSlider = slider();
            // The slider's posters laid out at their widest once, drawn narrower (Tile): the parts that belong to the
            // poster itself are as wide as the poster.
            int part = asSlider ? px(NARROW) : ViewGroup.LayoutParams.MATCH_PARENT;
            for (int i = 0; i < r.items.size(); i++) {
                Title t = r.items.get(i);
                Tile f = new Tile(h.a, px(14));
                if (asSlider) f.vis = px(NARROW);
                // Not kept as a finished picture on lite_home: a poster widens and narrows, and a picture that changes
                // size every frame is made again every frame.
                f.setBackground(h.fill(14, 0xFF222433));
                ImageView poster = new ImageView(h.a);
                poster.setTag("poster");
                poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                f.addView(poster, new FrameLayout.LayoutParams(part, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));
                // As wide as it is drawn on lite_home (twice that elsewhere). The slider's first dozen at once, the
                // rest (a row now runs to forty) as the row comes to them.
                if (asSlider && i >= 12) f.load = () -> h.image(poster, posterUrl(h, t), posterWidth(h));
                else h.image(poster, posterUrl(h, t), posterWidth(h));
                ImageView wide = new ImageView(h.a);
                wide.setTag("wide");   // found by name: the trailer slides in under it, so it moves along
                wide.setScaleType(ImageView.ScaleType.CENTER_CROP);
                wide.setAlpha(0f);
                f.addView(wide, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                // Started and not finished (in any app): how far in, as a bar along the poster's foot.
                // Your shows: how far through the season instead, in yellow on a light track.
                Float done = t.season >= 0 ? Float.valueOf(t.season) : watched.get(t.id);
                if (done != null) {
                    FrameLayout bar = new FrameLayout(h.a);
                    bar.setTag("progress");
                    bar.setBackgroundColor(t.season >= 0 ? 0x4DFFFFFF : 0x80000000);
                    View fill = new View(h.a);
                    fill.setBackgroundColor(t.season >= 0 ? 0xFFFFD04D : 0xFFE50914);
                    bar.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
                    // Inset from the poster's edges, its ends rounded, so the focus outline never covers it.
                    bar.setClipToOutline(true);
                    bar.setOutlineProvider(h.round(3));
                    FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(asSlider ? px(NARROW - 20) : ViewGroup.LayoutParams.MATCH_PARENT, px(6),
                            Gravity.BOTTOM | Gravity.START);
                    bl.leftMargin = px(10);
                    bl.rightMargin = px(10);
                    bl.bottomMargin = px(10);
                    f.addView(bar, bl);
                    // As a share of the poster's width, which changes as it widens.
                    float share = done;
                    bar.addOnLayoutChangeListener((v, l, tp, rt, b, ol, ot, or, ob) -> {
                        ViewGroup.LayoutParams fl = fill.getLayoutParams();
                        int w = Math.round((rt - l) * share);
                        if (fl.width != w) { fl.width = w; fill.setLayoutParams(fl); }
                    });
                }
                // Fits tonight and Your shows: a line along the foot (when it would end; where the series is up to),
                // on a shade, faded out while the poster is widened (its logo goes there).
                CharSequence foot = t.runtime > 0 ? FitsTonight.foot(h, t.runtime) : t.foot;
                if (foot != null) {
                    TextView line = h.text(t.runtime > 0 ? 18 : 14, h.semi, Color.WHITE);
                    line.setTag("foot");
                    line.setText(foot);
                    // Fits tonight's two lines: when it ends, then how long and the time to spare.
                    if (t.runtime > 0) { line.setMaxLines(2); line.setLineSpacing(px(2), 1f); } else line.setSingleLine(true);
                    line.setGravity(Gravity.BOTTOM);
                    line.setShadowLayer(px(4), 0, px(1), 0xCC000000);
                    line.setPadding(px(10), px(t.runtime > 0 ? 44 : 34), px(10), px(done != null ? 24 : t.runtime > 0 ? 11 : 12));
                    line.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xE6000000}));
                    f.addView(line, new FrameLayout.LayoutParams(part, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
                    // The bar stays on top of the shade.
                    View bar = f.findViewWithTag("progress");
                    if (bar != null) bar.bringToFront();
                }
                // The service's logo in the top corner (Fits tonight), on a faint shade so a light poster leaves it readable.
                if (t.mark != 0) {
                    View shade = new View(h.a);
                    shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x73000000, 0x00000000}));
                    f.addView(shade, new FrameLayout.LayoutParams(part, px(54), Gravity.TOP | Gravity.START));
                    ImageView m = new ImageView(h.a);
                    m.setImageResource(t.mark);
                    m.setAdjustViewBounds(true);
                    m.setScaleType(ImageView.ScaleType.FIT_START);
                    m.setMaxWidth(px(80));
                    FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(16), Gravity.TOP | Gravity.START);
                    ml.leftMargin = px(10);
                    ml.topMargin = px(10);
                    f.addView(m, ml);
                }
                if (t.badge != null) {
                    // As heavy as the labels on the mock-ups: Inter's semibold made bold.
                    TextView b = h.text(13, android.graphics.Typeface.create(h.semi, android.graphics.Typeface.BOLD), t.badgeFg);
                    b.setText(t.badge);
                    b.setLetterSpacing(0.05f);
                    b.setSingleLine(true);
                    b.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    b.setPadding(px(8), px(5), px(8), px(5));
                    b.setBackground(h.fill(6, t.badgeBg));
                    b.setMaxWidth(px(NARROW - 16));
                    FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP | Gravity.START);
                    bl.leftMargin = px(8);
                    bl.topMargin = px(8);
                    f.addView(b, bl);
                }
                // The slider: a shade over the posters away from the focus, deeper the further they are.
                View dim = new View(h.a);
                dim.setTag("dim");
                dim.setBackgroundColor(0xFF0B0E33);
                dim.setAlpha(0f);
                f.dim = dim;
                f.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                // Otherwise just the poster (and its trailer): no words over it.
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(asSlider ? WIDE : NARROW), px(TALL));
                lp.rightMargin = px(GAP);
                r.strip.addView(f, lp);
            }
            // The first posters' logos fetched now: the one widened shows its logo at once.
            for (int i = 0; i < Math.min(8, r.items.size()); i++) h.prefetchLogo(r.items.get(i).id, r.items.get(i).tv, 220);
            // The strip as wide as all its posters (and the focused one widened): left to wrap its content, it was
            // held to the screen's width and the posters past it were never drawn.
            ViewGroup.LayoutParams sl = r.strip.getLayoutParams();
            sl.width = slider() ? px(r.items.size() * (WIDE + GAP) + 40) : px(r.items.size() * (NARROW + GAP) + (WIDE - NARROW) + 40);
            r.strip.setLayoutParams(sl);
            if (slider()) {
                // A new tab: the slider starts again at its first poster, nothing widened.
                if (slideAnim != null) slideAnim.cancel();
                if (wideAnim != null) wideAnim.cancel();
                cancelLand();
                focusF = 0;
                wideIdx = -1;
                wideAmt = 0;
                landed = -1;
                r.strip.post(() -> slide(r, focusF, wideIdx, wideAmt));
            }
        }

        /** What the lens and the tab names last showed: the tab, and whether in glass (focus on the tabs). */
        private int lensTab = -1;
        private int lensGlass = -1;
        private ValueAnimator lensAnim;
        private androidx.dynamicanimation.animation.SpringAnimation lensSpring;

        private void moveLens() {
            if (tabBar == null || tab >= tabBar.getChildCount()) return;
            View t = tabBar.getChildAt(tab);
            if (tabScroll != null) {
                // The focused tab kept inside the bar, with the next one peeking in.
                int view = tabScroll.getWidth(), at = tabScroll.getScrollX(), margin = px(120);
                if (t.getRight() + margin > at + view) tabScroll.smoothScrollTo(t.getRight() + margin - view, 0);
                else if (t.getLeft() - margin < at) tabScroll.smoothScrollTo(Math.max(0, t.getLeft() - margin), 0);
            }
            ViewGroup.LayoutParams lp = lens.getLayoutParams();
            // Where it shows now (its edges, mid-spring or laid out), not where its layout last put it.
            int from = lens.getWidth() > 0 ? lens.getWidth() : lp.width;
            float fromX = lens.getLeft() + lens.getTranslationX();
            // Each key press calls this: in the posters the lens stays where it is, so nothing is laid out or redrawn.
            boolean moves = from != t.getWidth() || fromX != t.getLeft();
            int glassy = onTabs ? 1 : 0;
            if (lensTab == tab && lensGlass == glassy && !moves) return;
            if (lensAnim != null) lensAnim.cancel();
            if (lensSpring != null) lensSpring.cancel();
            if (moves) {
                // On a spring, like liquid glass: its edges moved straight (setLeft/setRight), nothing laid out on the
                // way (its width through the layout made the whole page lay out again on every frame); laid out once
                // in its new place at the end.
                int toW = t.getWidth(), toX = t.getLeft();
                float fromL = fromX, fromR = fromX + from;
                androidx.dynamicanimation.animation.FloatValueHolder p = new androidx.dynamicanimation.animation.FloatValueHolder(0f);
                androidx.dynamicanimation.animation.SpringAnimation sp = new androidx.dynamicanimation.animation.SpringAnimation(p);
                sp.setSpring(new androidx.dynamicanimation.animation.SpringForce(1f).setStiffness(520f).setDampingRatio(0.78f));
                sp.setMinimumVisibleChange(0.002f);
                sp.addUpdateListener((an, v, vel) -> {
                    float l = fromL + (toX - fromL) * v, r = fromR + (toX + toW - fromR) * v;
                    lens.setTranslationX(0);
                    lens.setLeft(Math.round(l));
                    lens.setRight(Math.round(Math.max(l + 1, r)));
                });
                sp.addEndListener((an, cancelled, v, vel) -> {
                    if (cancelled) return;
                    lp.width = toW;
                    lens.setLayoutParams(lp);
                    lens.setTranslationX(toX);
                });
                lensSpring = sp;
                sp.start();
            }
            if (lensTab != tab) {
                for (int i = 0; i < tabBar.getChildCount(); i++) {
                    TextView v = (TextView) tabBar.getChildAt(i);
                    v.setTextColor(i == tab ? Color.WHITE : 0xBFFFFFFF);
                    v.setTypeface(i == tab ? h.semi : h.regular);
                }
                lensTab = tab;
            }
            if (lensGlass == glassy) return;
            lensGlass = glassy;
            if (onTabs) {
                // The focus on the tabs: the score pills' liquid glass, a brighter rim, the words staying white.
                // Lighter than the pills' glass: the picture behind shows through it.
                android.graphics.drawable.Drawable glass = h.a.getDrawable(ScoresTicker.glassRes()).mutate();
                glass.setAlpha(150);
                GradientDrawable rim = h.fill(25, 0x08FFFFFF);
                rim.setStroke(px(1.5f), 0xA6FFFFFF);
                lens.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{glass, rim}));
            } else {
                GradientDrawable g = h.fill(25, 0x29FFFFFF);
                g.setStroke(px(1.5f), 0x73FFFFFF);
                lens.setBackground(g);
            }
        }

        private void paint() {
            for (int ri = 0; ri < rows.size(); ri++) {
                Row r = rows.get(ri);
                if (slider()) { sliderPaint(r); continue; }
                for (int i = 0; i < r.strip.getChildCount(); i++) {
                    FrameLayout f = (FrameLayout) r.strip.getChildAt(i);
                    boolean on = !onTabs && ri == row && i == idx;
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) f.getLayoutParams();
                    int target = px(on ? WIDE : NARROW);
                    if (lp.width != target) {
                        int from = lp.width;
                        ValueAnimator va = ValueAnimator.ofInt(from, target);
                        va.setDuration(220);
                        va.setInterpolator(HomeV2.SKY);
                        va.addUpdateListener(an -> { lp.width = (int) an.getAnimatedValue(); f.setLayoutParams(lp); });
                        va.start();
                    }
                    style(f, r.items.get(i), on);
                }
                // Bigger (the dock away), one poster fewer before the focused one, so it stays on the screen.
                int first = !onTabs && ri == row ? Math.max(0, idx - (big ? 3 : 4)) : 0;
                r.strip.animate().translationX(-first * px(NARROW + GAP)).setDuration(300).setInterpolator(HomeV2.SKY).start();
            }
            paintRest();
        }

        /** A poster's focused look (widened: its picture, logo and ring) or its resting one; only when it changes. */
        private void style(FrameLayout f, Title t, boolean on) {
                    if (!HomeV2.restyle(f, on ? 1 : 0)) return;
                    ImageView wide = (ImageView) f.findViewWithTag("wide");
                    // Widened: the title's own logo at its foot (fetched the first time), over the picture and trailer.
                    View footLine = f.findViewWithTag("foot");
                    if (footLine != null) footLine.animate().alpha(on ? 0f : 1f).setDuration(on ? 120 : 250).start();
                    View logo = f.findViewWithTag("titlelogo");
                    if (on && logo == null) logo = h.titleLogo(f, t.id, t.tv, 220, 62);
                    if (logo != null && !on) logo.animate().alpha(0f).setDuration(150).start();
                    if (on && logo != null && ((FrameLayout) logo).getChildAt(1) instanceof ImageView
                            && ((ImageView) ((FrameLayout) logo).getChildAt(1)).getDrawable() != null) logo.animate().alpha(1f).setDuration(250).start();
                    if (on && t.backdrop != null) {
                        // Until its own wide picture comes (or if it never does), the poster's picture fills the wide
                        // space, so an opened poster is never its narrow picture beside an empty space.
                        if (wide.getDrawable() == null) {
                            View pv = f.findViewWithTag("poster");
                            if (pv instanceof ImageView && ((ImageView) pv).getDrawable() != null) wide.setImageDrawable(((ImageView) pv).getDrawable());
                        }
                        // lite_home: its width on screen at the end of its slow zoom (1.12), no more.
                        h.image(wide, t.backdrop.replace("/w1280/", "/w780/"), px(h.lite ? Math.round(WIDE * 1.12f) : WIDE * 2));
                        wide.animate().alpha(1f).setDuration(350).start();
                        if (t.backdrop.startsWith("mood:")) {
                            // Surprise me's mood cards hold still: the slow zoom took their name and "Spin" past the card's edge.
                            wide.setScaleX(1f);
                            wide.setScaleY(1f);
                        } else if (h.lite) {
                            // lite_home (the TCL): the picture still, a touch in (six seconds of a big picture redrawn
                            // at a new size every frame, under the trailer loading, made the next slide stutter).
                            wide.animate().cancel();
                            wide.setScaleX(1.06f);
                            wide.setScaleY(1.06f);
                        } else {
                            wide.setScaleX(1.02f);
                            wide.setScaleY(1.02f);
                            wide.animate().scaleX(1.12f).scaleY(1.12f).setDuration(6000).setInterpolator(new android.view.animation.LinearInterpolator()).start();
                        }
                    } else {
                        wide.animate().alpha(0f).setDuration(200).start();
                    }
                    focusLook(f, on);
        }

        /**
         * The focused poster lifted off the page: nudged up on a spring (no shadow under it), a light sheen along its
         * top under the white ring; the others lie flat.
         */
        private void focusLook(FrameLayout f, boolean on) {
            f.setForeground(on ? liftedRing() : null);
            Fluid.lift(f, on, px(28), px(slider() ? 8 : 4));
        }

        private android.graphics.drawable.Drawable liftedRing() {
            GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x33FFFFFF, 0x00FFFFFF});
            float r = px(14);
            sheen.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            android.graphics.drawable.LayerDrawable ld = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{sheen, h.ring(14)});
            ld.setLayerInset(0, px(2), px(2), px(2), px(TALL * 0.55f));
            return ld;
        }

        private void paintRest() {
            Row focused = onTabs || row >= rows.size() ? null : rows.get(row);
            boolean has = focused != null && idx < focused.strip.getChildCount() && idx < focused.items.size();
            // The next few along: their logos and wide pictures fetched now, so each shows at once when reached.
            if (has) {
                for (int n = idx; n < Math.min(focused.items.size(), idx + 4); n++) {
                    Title nx = focused.items.get(n);
                    h.prefetchLogo(nx.id, nx.tv, 220);
                    if (nx.backdrop != null) h.prefetch(nx.backdrop.replace("/w1280/", "/w780/"), px(h.lite ? Math.round(WIDE * 1.12f) : WIDE * 2));
                }
            }
            trailerFor(has ? (FrameLayout) focused.strip.getChildAt(idx) : null, has ? focused.items.get(idx) : null,
                    has ? focused.items : null, idx);
            // More rows than the screen holds: they move up together, the focused one second from the top.
            if (column != null && rowY.length > 2) {
                // The focused row takes the first row's place, under the tabs; the next one peeks in below it.
                int top = onTabs ? 0 : Math.max(0, Math.min(row, rowY.length - 1));
                column.animate().translationY(-px(rowY[top] - rowY[0])).setDuration(320).setInterpolator(HomeV2.SKY).start();
                for (int ri = 0; ri < rows.size(); ri++) {
                    float a = ri < top ? 0f : 1f;
                    rows.get(ri).view.animate().alpha(a).setDuration(200).start();
                    rows.get(ri).label.animate().alpha(a).setDuration(200).start();
                }
            }
            // The tabs stay on screen, in the posters too, so the sections are always in sight.
            if (tabBar != null) moveLens();
            // The description's lines: as many as fit above the tabs.
            h.heroLines(tabs == null ? 2 : rowY.length > 2 ? 2 : 1);
        }

        // ---- the poster slider -------------------------------------------------------------------------------------

        /**
         * The slider (one row of posters, the dock layout; theme "poster_slider", on): the focused poster stays in one
         * place, second from the left, at full size (the first poster at the row's own left, so a tab never opens with
         * a gap before its first poster); the others shrink and darken the further they are from it, and
         * the whole row slides under it as the focus moves (520 ms, slow in, fast, slow out), the poster left behind
         * folding back from 16:9 as it goes. Landed, the poster takes the ring, and after a short pause (LAND_PAUSE_MS)
         * opens sideways to 16:9 (420 ms), its picture fading in
         * over the poster with its logo at the foot and the ring round it, the posters to its right moving along to
         * make room, and its trailer starts across it a moment later. Behind it, a soft glow in its own colour.
         */
        private boolean slider() {
            return h.dockMode && rowY.length == 1 && Config.themeFlag(h.a, "poster_slider", true);
        }

        static final long SLIDER_MS = 280, WIDEN_MS = 240, LAND_PAUSE_MS = 2500, SLIDER_TRAILER_MS = SLIDER_MS + LAND_PAUSE_MS + WIDEN_MS + 150;
        /**
         * How big each poster is, and how deep its shade, by how far it is from the focus: the queue stays large and
         * clear (it went down to 0.6 and 90% shade, small dim posters with wide gaps round them).
         */
        private static final float[] SCALE = {1f, 0.9f, 0.84f, 0.8f, 0.77f, 0.75f, 0.74f, 0.73f};
        private static final float[] SHADE = {0f, 0.3f, 0.42f, 0.5f, 0.56f, 0.6f, 0.64f, 0.68f};
        /** The room between the slider's posters, in design pixels: close together, one row (it was 20). */
        static final int SLIDE_GAP = 12;
        /** Where the row is (the focus, as a fraction while sliding), which poster is widened and how far, which landed. */
        private float focusF, wideAmt;
        /**
         * How flat the row lies, 0 to 1: with the focus up on the tabs (the dock showing) the posters lie flat, an even
         * row all one size with no shade, glow or lift; Down onto them, they stand up into the slider (the focused one
         * big, the queue behind it, the 3D look). flatTo is where it is going.
         */
        private float flat = 1f, flatTo = 1f;
        private ValueAnimator flatAnim;
        private int wideIdx = -1, landed = -1;
        private ValueAnimator slideAnim, wideAnim, glowAnim;
        private View glow;
        private GradientDrawable glowPaint;
        private int glowColour;

        private static float table(float[] t, float d) {
            float a = Math.abs(d);
            int i = (int) Math.floor(a);
            if (i >= t.length - 1) return t[t.length - 1];
            float f = a - i;
            return t[i] + (t[i + 1] - t[i]) * f;
        }

        /** The focused poster's place: one poster (at its shrunk size) and a gap in from the row's left. */
        private float focusX() {
            return px(NARROW) * SCALE[1] + px(SLIDE_GAP) + px(6);
        }

        /**
         * Where the focused poster sits with the focus at f: the first poster at the row's own left (nothing before it,
         * so no gap), from the second on in its place second from the left; in between, on the way.
         */
        private float anchorAt(float f) {
            float t = Math.max(0f, Math.min(1f, f));
            return px(6) + (focusX() - px(6)) * t;
        }

        /**
         * Each poster's left on screen with the focus on poster k (whole), given the posters' widths. Near the end of
         * the list the row stops with its last poster at right (the screen's edge, as far in as the first poster is
         * from the left), the focus moving on along it, so no gap opens after the last; never so far that one opens
         * before the first (a list that fits on the screen stays at the left).
         */
        private float[] lefts(int k, float[] w, int n, float right) {
            float[] x = new float[n];
            if (n == 0) return x;
            int kk = Math.max(0, Math.min(n - 1, k));
            x[kk] = anchorAt(kk);
            float gap = px(SLIDE_GAP);
            for (int i = kk + 1; i < n; i++) x[i] = x[i - 1] + w[i - 1] * table(SCALE, i - 1 - kk) + gap;
            for (int i = kk - 1; i >= 0; i--) x[i] = x[i + 1] - gap - w[i] * table(SCALE, i - kk);
            float end = x[n - 1] + w[n - 1] * table(SCALE, n - 1 - kk);
            float shift = Math.min(right - end, px(6) - x[0]);
            if (shift > 0) for (int i = 0; i < n; i++) x[i] += shift;
            return x;
        }

        /**
         * The row's edges in the strip's own pixels: {where the last poster stops (in from the screen's right edge as
         * far as the first poster is from its left), where the screen ends (posters past it are not drawn)}.
         */
        private float[] sliderEdges(Row r) {
            float sx = Math.max(0.01f, r.view.getScaleX());
            float left = 0;
            for (View v = r.view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) left += v.getLeft();
            float screen = h.a.getResources().getDisplayMetrics().widthPixels;
            float stripLeft = r.strip.getLeft();
            float margin = left + (stripLeft + px(6)) * sx;
            // Not laid out yet: nothing held back (the next frame places it properly).
            float clip = r.view.getWidth() > 0 ? r.view.getWidth() - stripLeft : Float.MAX_VALUE / 4;
            float shown = Math.min(clip, (screen - left) / sx - stripLeft);
            return new float[]{Math.min(shown, (screen - margin - left) / sx - stripLeft), shown};
        }

        /** Every poster placed for the focus at f (a fraction while sliding); the glow behind the focused one. */
        private void slide(Row r, float f, int wIdx, float wAmt) {
            int n = r.strip.getChildCount();
            if (n == 0) return;
            r.strip.setTranslationX(0);
            float[] w = new float[n], natural = new float[n];
            float gap = px(GAP), narrow = px(NARROW), wide = px(WIDE);
            // Each laid out at its widest (Tile), one after another: only how wide each is drawn changes.
            for (int i = 0; i < n; i++) {
                View c = r.strip.getChildAt(i);
                float want = i == wIdx ? narrow + (wide - narrow) * wAmt : narrow;
                if (c instanceof Tile) ((Tile) c).vis(want);
                w[i] = want;
                natural[i] = i * (wide + gap);
            }
            int k = (int) Math.floor(f);
            float frac = f - k;
            float[] edges = sliderEdges(r);
            float[] a = lefts(k, w, n, edges[0]), b = lefts(k + 1, w, n, edges[0]);
            float[] fa = flat > 0f ? flatLefts(k, n, edges[0]) : null, fb = flat > 0f ? flatLefts(k + 1, n, edges[0]) : null;
            float shadeFocus = onTabs ? 0.3f : 0f;
            int lastShown = -1;
            float[] xs = new float[n], ss = new float[n];
            for (int i = 0; i < n; i++) {
                View c = r.strip.getChildAt(i);
                float d = i - f;
                float s = table(SCALE, i - k) + (table(SCALE, i - k - 1) - table(SCALE, i - k)) * frac;
                float x = a[i] + (b[i] - a[i]) * frac;
                if (fa != null) {
                    // Flat: every poster its full size, one after another (blended in as the row lies down or stands up).
                    float xf = fa[i] + (fb[i] - fa[i]) * frac;
                    x += (xf - x) * flat;
                    s += (1f - s) * flat;
                }
                xs[i] = x;
                ss[i] = s;
                // Off the screen on the last frame and on this one: nothing to set (a row runs to forty posters, and
                // setting every one of them each frame of a slide cost the TCL frames).
                boolean on = x + w[i] * s > -r.strip.getLeft() && x < edges[1];
                if (!on && c instanceof Tile && !((Tile) c).shown) continue;
                if (c instanceof Tile) ((Tile) c).shown = on;
                c.setPivotX(w[i] / 2f);
                c.setPivotY(px(TALL) / 2f);
                c.setScaleX(s);
                c.setScaleY(s);
                c.setTranslationX(x - natural[i] - (1 - s) * w[i] / 2f);
                // Drawn while any of it is on the screen: the row runs on to the screen's edge, never stopping short.
                c.setAlpha(on ? 1f : 0f);
                if (on) lastShown = i;
                View shade = c instanceof Tile ? ((Tile) c).dim : c.findViewWithTag("dim");
                if (shade != null) shade.setAlpha((1f - flat) * Math.max(table(SHADE, d), Math.abs(d) < 0.5f ? shadeFocus * (1 - Math.abs(d) * 2) : 0f));
            }
            // The pictures of what is on the screen, and the next few, fetched (the rest wait for the row to reach them).
            for (int i = 0; i < n && i <= lastShown + 4; i++) {
                View c = r.strip.getChildAt(i);
                if (c instanceof Tile && ((Tile) c).load != null) {
                    Runnable go = ((Tile) c).load;
                    ((Tile) c).load = null;
                    go.run();
                }
            }
            if (glow != null) {
                // Only with the row stood up (none while it lies flat under the tabs).
                glow.setAlpha((onTabs ? 0.45f : 1f) * (1f - flat));
                // Centred on the focused poster, wherever the row is (it grows with the row when the dock is away).
                int fi = Math.max(0, Math.min(n - 1, Math.round(f)));
                float fw = w[fi] * ss[fi];
                float fx = xs[fi];
                float cx = r.strip.getLeft() + fx + fw / 2f, cy = r.strip.getTop() + px(TALL) / 2f;
                float sx = r.view.getScaleX(), py = r.view.getPivotY();
                glow.setScaleX(sx);
                glow.setScaleY(sx);
                glow.setX(r.view.getX() + cx * sx - glow.getWidth() / 2f);
                glow.setY(r.view.getY() + py + (cy - py) * sx - glow.getHeight() / 2f);
            }
        }

        /**
         * The flat row from the focus on poster k: each poster its full narrow size, the first at the row's left (the
         * focused one second from the left once past the first), the last never short of the right edge.
         */
        private float[] flatLefts(int k, int n, float right) {
            float[] x = new float[n];
            if (n == 0) return x;
            int kk = Math.max(0, Math.min(n - 1, k));
            float step = px(NARROW) + px(SLIDE_GAP), anchor = px(6) + Math.min(kk, 1) * step;
            for (int i = 0; i < n; i++) x[i] = anchor + (i - kk) * step;
            float end = x[n - 1] + px(NARROW);
            float shift = Math.min(right - end, px(6) - x[0]);
            if (shift > 0) for (int i = 0; i < n; i++) x[i] += shift;
            return x;
        }

        /** The row lies down (up to the tabs) or stands up (down onto the posters), in step with the row's growing. */
        private void flatten(Row r, boolean down) {
            float to = down ? 1f : 0f;
            if (to == flatTo) return;
            flatTo = to;
            if (flatAnim != null) flatAnim.cancel();
            ValueAnimator va = ValueAnimator.ofFloat(flat, to);
            flatAnim = va;
            va.setDuration(down ? 220 : 280);
            va.setInterpolator(HomeV2.SKY);
            va.addUpdateListener(an -> {
                flat = (float) an.getAnimatedValue();
                // The slide (or the row's growing) places the posters itself on its frames; otherwise this does.
                if (slideAnim == null || !slideAnim.isRunning()) slide(r, focusF, wideIdx, wideAmt);
            });
            va.start();
        }

        /** The focus changed (or the tabs took it): the row slides to it, and the poster opens once it has landed. */
        private void sliderPaint(Row r) {
            int n = r.strip.getChildCount();
            if (n == 0 || r.items.isEmpty()) return;
            flatten(r, onTabs);
            int target = Math.max(0, Math.min(idx, n - 1));
            // Off the poster with the trailer (along, or up to the tabs): the trailer stopped first, so the pictures it
            // covered come back and then fade out as the poster closes (stopped after, they came back over it).
            if (trailerTile != null && (onTabs || target >= n || r.strip.getChildAt(target) != trailerTile)) player.stop();
            // Every poster not landed-on back to its resting look (the one left behind folds back as it goes).
            for (int i = 0; i < n && i < r.items.size(); i++) {
                if (i == landed && i == target && !onTabs) continue;
                if (i == arrived && i == target && !onTabs) continue;
                FrameLayout f = (FrameLayout) r.strip.getChildAt(i);
                style(f, r.items.get(i), false);
                // The ring (and the lift) a poster took while it waited to open.
                if (i == arrived) focusLook(f, false);
            }
            // Up to the tabs before it opened: it does not open, and opens afresh on the way back down.
            if (onTabs && arrived >= 0) cancelLand();
            if (Math.abs(focusF - target) < 0.001f && (onTabs ? wideAmt == 0f : landed == target || arrived == target)) {
                slide(r, focusF, wideIdx, wideAmt);
                return;
            }
            if (slideAnim != null) slideAnim.cancel();
            if (wideAnim != null) wideAnim.cancel();
            cancelLand();
            float from = focusF, wideFrom = wideAmt;
            int oldWide = wideIdx;
            landed = -1;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            slideAnim = va;
            // Already there (back down from the tabs): straight to opening; a widened one narrowing first (up to the tabs).
            va.setDuration(Math.abs(from - target) < 0.001f ? (wideFrom > 0 ? 220 : 1) : SLIDER_MS);
            va.setInterpolator(new android.view.animation.PathInterpolator(0.65f, 0f, 0.35f, 1f));
            final boolean[] cancelled = {false};
            va.addUpdateListener(an -> {
                float t = (float) an.getAnimatedValue();
                focusF = from + (target - from) * t;
                wideAmt = wideFrom * (1 - t);
                slide(r, focusF, oldWide, wideAmt);
            });
            va.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled[0] = true;
                }
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (cancelled[0]) return;
                    focusF = target;
                    wideAmt = 0;
                    wideIdx = -1;
                    slide(r, focusF, -1, 0);
                    if (!onTabs && idx == target) arrive(r, target);
                }
            });
            va.start();
        }

        /** The poster that took the ring and is waiting to open (-1: none), and its opening, posted. */
        private int arrived = -1;
        private Runnable landSoon;

        private void cancelLand() {
            if (landSoon != null) h.main.removeCallbacks(landSoon);
            landSoon = null;
            arrived = -1;
        }

        /**
         * Arrived on poster i: it takes the ring and its colour goes to the glow at once; it opens out only after a short
         * pause, so a poster passed on the way along stays a poster.
         */
        private void arrive(Row r, int i) {
            if (i >= r.strip.getChildCount() || i >= r.items.size()) return;
            cancelLand();
            arrived = i;
            FrameLayout f = (FrameLayout) r.strip.getChildAt(i);
            focusLook(f, true);
            glowTo(colourOf(f));
            landSoon = () -> {
                landSoon = null;
                if (onTabs || idx != i || arrived != i) return;
                arrived = -1;
                land(r, i);
            };
            h.main.postDelayed(landSoon, LAND_PAUSE_MS);
        }

        /** Landed on poster i: its focused look, then it opens sideways to 16:9, the glow taking its colour. */
        private void land(Row r, int i) {
            if (i >= r.strip.getChildCount() || i >= r.items.size()) return;
            landed = i;
            // Nothing wide to show (no wide picture for it): it stays its poster, focused, not opened onto an empty space.
            if (r.items.get(i).backdrop == null) return;
            wideIdx = i;
            FrameLayout f = (FrameLayout) r.strip.getChildAt(i);
            style(f, r.items.get(i), true);
            glowTo(colourOf(f));
            ValueAnimator va = ValueAnimator.ofFloat(wideAmt, 1f);
            wideAnim = va;
            va.setDuration(WIDEN_MS);
            va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
            va.addUpdateListener(an -> {
                wideAmt = (float) an.getAnimatedValue();
                slide(r, focusF, wideIdx, wideAmt);
            });
            va.start();
        }

        /** The poster's own colour, for the glow: its vivid colours averaged, at a steady brightness. */
        private int colourOf(FrameLayout f) {
            ImageView p = f.findViewWithTag("poster");
            android.graphics.drawable.Drawable d = p == null ? null : p.getDrawable();
            if (!(d instanceof android.graphics.drawable.BitmapDrawable)) return 0xFF3A4CC0;
            android.graphics.Bitmap b = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
            if (b == null || b.isRecycled()) return 0xFF3A4CC0;
            try {
                android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(b, 10, 15, true);
                float[] hsv = new float[3];
                double r = 0, g = 0, bl = 0, wsum = 0;
                for (int y = 0; y < 15; y++) for (int x = 0; x < 10; x++) {
                    int c = small.getPixel(x, y);
                    Color.colorToHSV(c, hsv);
                    double wgt = hsv[1] * hsv[1] * (hsv[2] > 0.2f && hsv[2] < 0.95f ? 1 : 0.1) + 0.001;
                    r += Color.red(c) * wgt; g += Color.green(c) * wgt; bl += Color.blue(c) * wgt; wsum += wgt;
                }
                if (small != b) small.recycle();
                Color.colorToHSV(Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum)), hsv);
                hsv[1] = Math.max(0.55f, Math.min(0.85f, hsv[1]));
                hsv[2] = 0.68f;
                return Color.HSVToColor(hsv);
            } catch (RuntimeException e) {
                return 0xFF3A4CC0;
            }
        }

        /** Which of the two lights shows now. */
        private int glowFront;

        private void glowTo(int colour) {
            if (!(glow instanceof ViewGroup) || ((ViewGroup) glow).getChildCount() < 2 || colour == glowColour) return;
            glowColour = colour;
            ViewGroup g = (ViewGroup) glow;
            View out = g.getChildAt(glowFront), in = g.getChildAt(1 - glowFront);
            glowFront = 1 - glowFront;
            // The light coming in takes the new colour once, then the two cross-fade (their alpha only).
            ((GradientDrawable) in.getBackground()).setColors(new int[]{(0xA6 << 24) | (colour & 0xFFFFFF), (0x33 << 24) | (colour & 0xFFFFFF), colour & 0xFFFFFF});
            in.animate().cancel();
            out.animate().cancel();
            in.animate().alpha(1f).setDuration(320).setInterpolator(HomeV2.SKY).start();
            out.animate().alpha(0f).setDuration(320).setInterpolator(HomeV2.SKY).start();
        }

        /** Each title's marks from the store, once looked up (empty: the store had none). */
        private static final Map<Integer, List<String>> MARKS = new HashMap<>();

        private void heroNow() {
            // The Kids page on its tabs: its show at the top (once, not again at every tab), not the first title's.
            if (play != null) {
                boolean shows = onTabs && play.hasShows();
                play.shows(shows);
                if (shows) {
                    if (actShown != play.act()) {
                        actShown = play.act();
                        h.heroTopColour(crumbColour);
                        h.hero(play.tile(crumb));
                    }
                    return;
                }
                actShown = -1;
            }
            if (row >= rows.size() || rows.get(row).items.isEmpty()) return;
            Title t = rows.get(row).items.get(Math.min(idx, rows.get(row).items.size() - 1));
            side(rows.get(row), t);
            HomeV2.Tile tile = new HomeV2.Tile();
            tile.title = t.title;
            tile.heroTitle = t.title;
            tile.heroImage = t.backdrop != null ? t.backdrop : t.poster;
            // A mood's picture at the top goes without its words: the page already names it, and the name showed through the tabs.
            if (tile.heroImage != null && (tile.heroImage.startsWith("mood:") || tile.heroImage.startsWith("ytkids:"))) tile.heroImage = tile.heroImage.replace(":w", ":b");
            tile.heroTop = crumb;
            String vote = t.vote > 0 ? String.format(Locale.UK, " · ★ %.1f", t.vote) : "";
            // Fits tonight: when it would end, first.
            String fit = t.runtime > 0 ? FitsTonight.heroLine(h.a, t.runtime) : null;
            // An app's card (YouTube Kids) is neither a series nor a film.
            tile.heroMeta = TextUtils.join(" · ", nonEmpty(fit, t.year, t.what,
                    t.noKind || t.poster != null && t.poster.startsWith("ytkids:") ? null : t.tv ? "Series" : "Film")) + vote;
            tile.heroText = t.overview;
            // The running time and the streaming service (TMDB, looked up by Home's hero), and the store's marks.
            tile.tmdbId = t.id;
            tile.tv = t.tv;
            List<String> known = MARKS.get(t.id);
            if (known != null) tile.specs.addAll(known);
            h.heroTopColour(crumbColour);
            h.hero(tile);
            // A tile that is not a film or series (Surprise me's moods): nothing more to look up.
            if (t.id <= 0) return;
            if (known == null) h.io.execute(() -> {
                Trending.Title st = Trending.storeMarks(h.a, t.title, t.year, t.tv);
                List<String> m = new ArrayList<>();
                if (st != null) {
                    if (st.quality != null) m.add(st.quality.equals("4K") ? "4K UHD" : st.quality);
                    if (st.dolbyVision) m.add("DOLBY VISION");
                    else if (st.hdr10plus) m.add("HDR10+");
                    else if (st.hdr) m.add("HDR10");
                    if (st.atmos) m.add("DOLBY ATMOS");
                    if (st.age != null) m.add("age:" + st.age);
                }
                h.main.post(() -> {
                    MARKS.put(t.id, m);
                    if (m.isEmpty()) return;
                    Title now = rows.get(row).items.isEmpty() ? null : rows.get(row).items.get(Math.min(idx, rows.get(row).items.size() - 1));
                    if (now != t) return;
                    tile.specs.clear();
                    tile.specs.addAll(m);
                    h.hero(tile);
                });
            });
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

        /** OK held on a poster: its panel opened. */
        private boolean held;

        /** OK on a poster: its own action, else the app it was being watched in, else Debrid Stream's page for it. */
        private void play(Title t) {
            // From the Kids page after bedtime: the gentle timer (KidsNight; KidsArt's own cards start it themselves).
            if ("KIDS".equals(crumb) && t.ok == null) KidsNight.opened(h.a);
            if (t.ok != null) { t.ok.run(); return; }
            h.playLikePages(t);
        }

        @Override boolean key(KeyEvent e) {
            if (play != null) play.hush();
            return pageKey(e);
        }

        private boolean pageKey(KeyEvent e) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) intoPosters = false;   // a key of their own first: left where it takes them
            int code = e.getKeyCode();
            if (ok(code)) {
                boolean has = !onTabs && row < rows.size() && idx < rows.get(row).items.size();
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) held = false;
                // Held: the title's panel (the cast, the films so far, My list); its release is then not a press.
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() > 0) {
                    if (has && !held) {
                        held = true;
                        Title t = rows.get(row).items.get(idx);
                        h.showTitle(t, () -> play(t));
                    }
                    return true;
                }
                if (e.getAction() == KeyEvent.ACTION_UP) {
                    if (held) { held = false; return true; }
                    if (has) play(rows.get(row).items.get(idx));
                }
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return code != KeyEvent.KEYCODE_BACK;
            int n = row < rows.size() ? rows.get(row).items.size() : 0;
            switch (code) {
                // At an edge the key is Home's (false): on to the rail, the top buttons or the boxes, no Back needed.
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (onTabs) { if (tab > 0) load(tab - 1); else return false; }
                    else if (idx > 0) idx--;
                    else return false;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (onTabs) { if (tab < tabs.length - 1) load(tab + 1); }
                    else if (idx < n - 1) idx++;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (onTabs) {
                        // Up on the hero: the title's details open under it, on this page (the saga so far, the cast);
                        // Up again goes on to the top bar. A page with its own scene up there (Kids) goes straight on.
                        Title ht = heroTitle();
                        if (ht == null || ht.id <= 0 || play != null || h.tmdbKey.isEmpty()) return false;
                        h.showTitle(ht, () -> play(ht), false);
                        return true;
                    }
                    if (row > 0) { row--; idx = Math.min(idx, Math.max(0, rows.get(row).items.size() - 1)); }
                    else if (tabs != null) onTabs = true;
                    else return false;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // From the tabs into the posters; a tab with none (Continue Watching, nothing started) on to the boxes.
                    if (onTabs) { if (n == 0) return false; onTabs = false; }
                    else if (row < rows.size() - 1 && !rows.get(row + 1).items.isEmpty()) { row++; idx = Math.min(idx, Math.max(0, rows.get(row).items.size() - 1)); }
                    else return false;
                    break;
                default:
                    return false;
            }
            paint();
            h.main.removeCallbacks(heroLater);
            h.main.postDelayed(heroLater, h.lite ? 450 : 300);
            return true;
        }
    }

    /** Small words left lower case inside a title. */
    private static final java.util.Set<String> SMALL = new java.util.HashSet<>(Arrays.asList(
            "a", "an", "and", "of", "on", "in", "the", "to", "for", "at", "by", "&", "v", "vs"));

    /** "Popular romance films" to "Popular Romance Films", "sci-fi" to "Sci-Fi". */
    static String title(String s) {
        if (s == null) return null;
        StringBuilder b = new StringBuilder(s.length());
        String[] words = s.split(" ", -1);
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (i > 0) b.append(' ');
            // Small words stay small; a word with capitals of its own (iPlayer, BBC, NOW) stays as it is.
            if (w.isEmpty() || i > 0 && SMALL.contains(w) || !w.equals(w.toLowerCase(Locale.ROOT))) { b.append(w); continue; }
            boolean up = true;
            for (char c : w.toCharArray()) {
                b.append(up ? Character.toUpperCase(c) : c);
                up = c == '-';
            }
        }
        return b.toString();
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
        String[] tabs = {"Continue Watching", "My list", "New releases", "Trending", "Netflix", "Disney+", "Prime Video", "Apple TV+"};
        int[] providers = {0, 0, 0, 0, 8, 337, 9, 350};
        return new PosterPage(h, "DISCOVER", 0xFFC9A7FF, tabs, new int[]{584 - 62}, t -> {
            List<Title> items;
            LocalDate today = LocalDate.now();
            if (t == 0) return Arrays.asList(resumeRow(h, null));
            if (t == 1) return Arrays.asList(myListRow(h));
            if (t == 2) {
                items = mix(tmdbRow(k, "discover/movie?region=GB&with_release_type=4|5&release_date.gte=" + today.minusWeeks(6)
                                + "&release_date.lte=" + today + "&sort_by=popularity.desc", false),
                        tmdbRow(k, "discover/tv?first_air_date.gte=" + today.minusMonths(2) + "&sort_by=popularity.desc&with_original_language=en", true));
            } else if (t == 3) {
                items = tmdbRow(k, "trending/all/day", false);
            } else {
                String w = "&watch_region=GB&with_watch_providers=" + providers[t] + "&sort_by=popularity.desc";
                items = mix(tmdbRow(k, "discover/tv?without_genres=10763,10764,10767" + w, true), tmdbRow(k, "discover/movie?" + w.substring(1), false));
            }
            return Arrays.asList(row(null, null, false, items));
        }).startOn(resumeEmpty(h, null) ? (MyList.all(h.a).isEmpty() ? 2 : 1) : 0);
    }

    /** Each title's UK providers (TMDB), kept an hour: My list's posters say which are not on the TV's apps yet. */
    private static final Map<String, Object[]> PROVIDERS = new HashMap<>();

    static JSONObject providersGb(String key, int id, boolean tv) {
        String k = (tv ? "tv:" : "mv:") + id;
        synchronized (PROVIDERS) {
            Object[] had = PROVIDERS.get(k);
            if (had != null && System.currentTimeMillis() - (Long) had[0] < 3600_000L) return (JSONObject) had[1];
        }
        JSONObject gb = null;
        try {
            JSONObject r = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id + "/watch/providers?api_key=" + key))
                    .optJSONObject("results");
            gb = r == null ? null : r.optJSONObject("GB");
        } catch (Exception ignored) {
            // asked again next time
        }
        synchronized (PROVIDERS) { PROVIDERS.put(k, new Object[]{System.currentTimeMillis(), gb}); }
        return gb;
    }

    /**
     * My list: what was kept from a title's panel, newest first. A title on one of the TV's streaming apps opens as the
     * page's others do; one on none of them says so on its poster, and OK opens the store that sells it, else a note
     * (a pop-up says when it arrives on one of the apps).
     */
    static Row myListRow(HomeV2 h) {
        List<Title> items = MyList.titles(h.a);
        for (Title t : items) {
            JSONObject gb = providersGb(h.tmdbKey, t.id, t.tv);
            if (HomeV2.onYourApps(h, gb) != null) continue;
            t.badge = "NOT ON YOUR APPS";
            t.badgeBg = 0xB3000000;
            t.badgeFg = Color.WHITE;
            String store = HomeV2.storeFor(h, gb);
            t.ok = store != null ? () -> h.searchTitle(store, t.title, t.tv, t.id, t.backdrop != null ? t.backdrop : t.poster)
                    : () -> android.widget.Toast.makeText(h.a, t.title + " isn't on your apps yet: a pop-up will say when it is",
                    android.widget.Toast.LENGTH_LONG).show();
        }
        Row r = row(null, null, false, items);
        r.empty = "Nothing on My list yet. Hold OK on any poster and choose My list to keep it here.";
        return r;
    }

    /**
     * Read ahead from Home, a moment after it is up: the tab each of Discover, Movies and TV shows opens on, with its
     * first posters and their logos in the cache, so a page opens with everything already there.
     */
    /**
     * A poster's picture: on lite_home TMDB's 185-wide one, the size it is drawn (180), a quarter of the 342's bytes to
     * fetch and decode and nothing to shrink after; elsewhere the 342 (drawn at twice the size for sharpness).
     */
    static String posterUrl(HomeV2 h, Title t) {
        return h.lite && t.poster != null ? t.poster.replace("/w342/", "/w185/") : t.poster;
    }

    static int posterWidth(HomeV2 h) {
        return h.px(h.lite ? PosterPage.NARROW_MOST : PosterPage.NARROW_MOST * 2);
    }

    static void warm(HomeV2 h) {
        if (h.tmdbKey.isEmpty()) return;
        for (PosterPage p : new PosterPage[]{discover(h), movies(h), shows(h)}) {
            int t = p.startTab;
            PAGES.execute(() -> {
                int n = 0;
                for (Row r : p.source.apply(t)) {
                    for (Title x : r.items) {
                        if (n++ >= 20) break;
                        h.prefetch(posterUrl(h, x), posterWidth(h));
                        h.prefetchLogo(x.id, x.tv, 220);
                    }
                }
            });
        }
    }

    /** A streaming service's titles in the UK (TMDB's watch providers: Netflix 8, Prime Video 9, Disney+ 337, Apple TV+ 350). */
    private static String on(boolean tv, int provider) {
        return "discover/" + (tv ? "tv" : "movie") + "?watch_region=GB&with_watch_providers=" + provider + "&sort_by=popularity.desc";
    }

    private static String genre(boolean tv, int id) {
        return "discover/" + (tv ? "tv" : "movie") + "?with_genres=" + id + "&sort_by=popularity.desc&vote_count.gte=150";
    }

    private static String since(int days) {
        return java.time.LocalDate.now().minusDays(days).toString();
    }

    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    /** The pages' tabs read on a pool of their own: the one in view, and its neighbours ahead. */
    private static final java.util.concurrent.ExecutorService PAGES = java.util.concurrent.Executors.newFixedThreadPool(3);
    /** The trailer lookups' own threads, and each title's trailers once found ("m123" / "t456"), for every page and tab. */
    private static final java.util.concurrent.ExecutorService TRAILERS = java.util.concurrent.Executors.newFixedThreadPool(2);
    private static final Map<String, String> TRAILER_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    static PosterPage movies(HomeV2 h) {
        String m = "discover/movie?sort_by=popularity.desc&watch_region=GB";
        return sections(h, "MOVIES", 0xFFFF4FA3, false, new String[][]{
                {"Fits tonight", "@fits", ""},
                {"Surprise me", "@surprise", ""},
                {"Popular", "movie/popular?region=GB", ""},
                {"Top 10", "trending/movie/week", "n"},
                {"New to stream", m + "&region=GB&with_release_type=4&release_date.gte=" + since(45) + "&release_date.lte=" + today(), ""},
                {"In cinemas", "movie/now_playing?region=GB", ""},
                {"Trending", "trending/movie/day", ""},
                {"Netflix", on(false, 8), ""},
                {"Prime Video", on(false, 9), ""},
                {"Disney+", on(false, 337), ""},
                {"Apple TV+", on(false, 350), ""},
                {"NOW", on(false, 39), ""},
                {"Action", genre(false, 28), ""},
                {"Thriller", genre(false, 53), ""},
                {"Comedy", "discover/movie?with_genres=35&without_genres=27,53&sort_by=popularity.desc&vote_count.gte=300", ""},
                {"Drama", genre(false, 18), ""},
                {"Sci-fi", genre(false, 878), ""},
                {"Horror", genre(false, 27), ""},
                {"Crime", genre(false, 80), ""},
                {"Romance", genre(false, 10749), ""},
                {"Family", genre(false, 10751), ""},
                {"Animation", genre(false, 16), ""},
                {"Documentary", genre(false, 99), ""},
                {"Hidden gems", "discover/movie?vote_average.gte=7.4&vote_count.gte=150&vote_count.lte=1500&sort_by=vote_average.desc", ""},
                {"Critically acclaimed", "movie/top_rated?region=GB", ""},
                {"True stories", "discover/movie?with_keywords=9672&sort_by=popularity.desc&vote_count.gte=200", ""},
                {"Best of British", "discover/movie?with_origin_country=GB&sort_by=popularity.desc&vote_count.gte=200", ""},
                {"90s classics", "discover/movie?primary_release_date.gte=1990-01-01&primary_release_date.lte=1999-12-31&sort_by=popularity.desc&vote_count.gte=1000", ""},
                {"Korean cinema", "discover/movie?with_original_language=ko&sort_by=popularity.desc&vote_count.gte=150", ""},
                {"Coming soon", "movie/upcoming?region=GB", ""}});
    }

    static PosterPage shows(HomeV2 h) {
        return sections(h, "TV SHOWS", 0xFF4F8BFF, true, new String[][]{
                {"Your shows", "@yourshows", ""},
                {"New episodes", "@newepisodes", ""},
                {"New series", "discover/tv?first_air_date.gte=" + since(60) + "&first_air_date.lte=" + today() + "&sort_by=popularity.desc&with_original_language=en", ""},
                {"Surprise me", "@dice", ""},
                {"Top 10", "trending/tv/week", "n"},
                {"On TV now", "tv/on_the_air?without_genres=10763,10764,10767", ""},
                {"Trending", "trending/tv/day", ""},
                {"Netflix", on(true, 8), ""},
                {"Prime Video", on(true, 9), ""},
                {"Disney+", on(true, 337), ""},
                {"Apple TV+", on(true, 350), ""},
                {"NOW", on(true, 39), ""},
                {"BBC iPlayer", on(true, 38), ""},
                {"Drama", genre(true, 18), ""},
                {"Crime", genre(true, 80), ""},
                {"Comedy", genre(true, 35), ""},
                {"Sci-fi & fantasy", genre(true, 10765), ""},
                {"Action", genre(true, 10759), ""},
                {"Mystery", genre(true, 9648), ""},
                {"Reality", "discover/tv?with_genres=10764&sort_by=popularity.desc&watch_region=GB", ""},
                {"Documentary", genre(true, 99), ""},
                {"Animation", "discover/tv?with_genres=16&sort_by=popularity.desc&vote_count.gte=100", ""},
                {"Kids", "discover/tv?with_genres=10762&sort_by=popularity.desc", ""},
                {"Binge in a weekend", "discover/tv?with_type=2&sort_by=popularity.desc&vote_count.gte=100", ""},
                {"Best of British", "discover/tv?with_origin_country=GB&sort_by=popularity.desc&vote_count.gte=100", ""},
                {"Critically acclaimed", "tv/top_rated", ""},
                {"Hidden gems", "discover/tv?vote_average.gte=8&vote_count.gte=100&vote_count.lte=1200&sort_by=vote_average.desc", ""},
                {"Anime", "discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc&vote_count.gte=100", ""},
                {"Korean dramas", "discover/tv?with_original_language=ko&with_genres=18&sort_by=popularity.desc&vote_count.gte=50", ""}});
    }

    // ---- Kids ---------------------------------------------------------------------------------------------------------

    /**
     * Kids (the dock's seventh section): children's TV and films as Movies and TV shows are laid out, a tab each, OK on
     * a poster doing what it does there. Top picks leads with YouTube for kids (YouTube on a search made for children;
     * the YouTube tab has more: cartoons, nursery rhymes, learning, Bluey...), then the children's series and family films
     * on the TV's own apps. Films are the BBFC's U and PG.
     */
    static PosterPage kids(HomeV2 h) {
        String k = h.tmdbKey;
        String mine = ReleaseAlerts.providers(h.a);
        String apps = mine.isEmpty() ? "8|9|337" : mine;
        String[][] spec = {
                {"Top picks", "@top"},
                {"Continue Watching", "@continue"},
                {"YouTube", "@youtube"},
                {"Bangla", "@bangla"},
                {"Kids TV", "discover/tv?with_genres=10762&with_original_language=en&watch_region=GB&sort_by=popularity.desc"},
                {"Favourites", "discover/tv?with_genres=10762&sort_by=vote_count.desc"},
                {"Family films", FAMILY + "&vote_count.gte=200"},
                {"Animated films", "discover/movie?with_genres=16&certification_country=GB&certification.lte=PG&sort_by=popularity.desc&vote_count.gte=200"},
                {"New films", FAMILY + "&primary_release_date.gte=" + since(400) + "&primary_release_date.lte=" + today()},
                {"Disney+", "@337"},
                {"Netflix", "@8"},
                {"Prime Video", "@9"},
                {"CBeebies & CBBC", "discover/tv?with_genres=10762&watch_region=GB&with_watch_providers=38&sort_by=popularity.desc"}};
        String[] tabs = new String[spec.length];
        for (int i = 0; i < spec.length; i++) tabs[i] = spec[i][0];
        PosterPage p = new PosterPage(h, "KIDS", HomeV2.CAT_COLOURS[HomeV2.KIDS], tabs, new int[]{584 - 62}, t -> {
            String what = spec[t][1];
            List<Title> items = new ArrayList<>();
            if ("@continue".equals(what)) {
                Row r = row(null, null, false, kidsResume(h));
                r.empty = "Nothing to pick up yet. Children's series and films started in Netflix, Disney+, Prime Video, "
                        + "Debrid Stream and the rest show here, with how far they got.";
                return Arrays.asList(r);
            }
            if ("@youtube".equals(what)) {
                for (int n = 1; n < KidsArt.BANGLA; n++) items.add(youTubeCard(h, n));
            } else if ("@bangla".equals(what)) {
                for (int n = KidsArt.BANGLA + 1; n < KidsArt.TOPICS.length; n++) items.add(youTubeCard(h, n));
            } else if ("@top".equals(what)) {
                items.add(youTubeCard(h, 0));
                items.add(youTubeCard(h, KidsArt.BANGLA));
                // What the children were part way through, first after YouTube (the whole list on Continue Watching).
                List<Title> going = kidsResume(h);
                items.addAll(going.subList(0, Math.min(4, going.size())));
                items.addAll(mix(tmdb(k, kidsTv(apps), true), tmdb(k, FAMILY + onApps(apps), false)));
            } else if (what.startsWith("@")) {
                String app = what.substring(1);
                items.addAll(mix(tmdb(k, kidsTv(app), true), tmdb(k, FAMILY + onApps(app), false)));
            } else {
                items.addAll(tmdb(k, what, what.startsWith("discover/tv")));
            }
            return Arrays.asList(row(null, null, false, items));
        });
        // The shows at the top and the family's own children as 3D cartoons, when the TV has their pictures and scenes
        // (KidsPlay.cast, KidsPlay.scenes): never otherwise.
        List<java.io.File> cast = h.dockMode ? KidsPlay.cast(h.a) : new ArrayList<>();
        List<java.io.File> scenes = h.dockMode ? KidsPlay.scenes(h.a) : new ArrayList<>();
        if (!cast.isEmpty() || !scenes.isEmpty()) p.play = new KidsPlay(h, cast, scenes, p::heroNow);
        return p.startOn(0);
    }

    /**
     * Continue Watching for the Kids page: the films and series started and not finished (WatchHistory), only the
     * children's: a series TMDB files under Kids, or Animation and Family together; a film under Family or Animation.
     * Each title's answer is kept, so TMDB is asked once for it. Off the main thread.
     */
    static List<Title> kidsResume(HomeV2 h) {
        List<Title> out = new ArrayList<>();
        android.content.SharedPreferences p = h.a.getSharedPreferences("kids_titles", android.content.Context.MODE_PRIVATE);
        for (Title t : WatchHistory.titles(h.a, null)) {
            String key = (t.tv ? "tv:" : "mv:") + t.id;
            Boolean kid = p.contains(key) ? Boolean.valueOf(p.getBoolean(key, false)) : null;
            if (kid == null) {
                kid = childrens(h.tmdbKey, t.id, t.tv);
                if (kid != null) p.edit().putBoolean(key, kid).apply();
            }
            if (Boolean.TRUE.equals(kid)) out.add(t);
        }
        return out;
    }

    /** Whether TMDB files the title as children's (null: it could not be asked). */
    private static Boolean childrens(String key, int id, boolean tv) {
        if (key == null || key.isEmpty() || id <= 0) return null;
        try {
            JSONObject d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (tv ? "tv/" : "movie/") + id + "?api_key=" + key));
            JSONArray g = d.optJSONArray("genres");
            java.util.Set<Integer> ids = new java.util.HashSet<>();
            for (int i = 0; g != null && i < g.length(); i++) ids.add(g.getJSONObject(i).optInt("id"));
            return tv ? ids.contains(10762) || ids.contains(16) && ids.contains(10751) : ids.contains(10751) || ids.contains(16);
        } catch (Exception e) {
            return null;
        }
    }

    /** The BBFC's U and PG family films, the most popular first. */
    private static final String FAMILY = "discover/movie?with_genres=10751&certification_country=GB&certification.lte=PG&sort_by=popularity.desc";

    private static String kidsTv(String providers) {
        return "discover/tv?with_genres=10762&watch_region=GB&with_watch_monetization_types=flatrate&with_watch_providers=" + providers + "&sort_by=popularity.desc";
    }

    private static String onApps(String providers) {
        return "&watch_region=GB&with_watch_monetization_types=flatrate&with_watch_providers=" + providers + "&vote_count.gte=50";
    }

    /**
     * A YouTube card for children: YouTube Kids is not offered on every TV (not on this TCL), so OK opens YouTube itself
     * on a search made for children (KidsArt.TOPICS: cartoons, nursery rhymes, Bluey...).
     */
    static Title youTubeCard(HomeV2 h, int n) {
        Title t = new Title();
        t.title = (String) KidsArt.TOPICS[n][0];
        t.year = "";
        t.what = "YouTube";
        t.overview = n >= KidsArt.BANGLA
                ? "Opens YouTube on " + KidsArt.TOPICS[n][0] + ": rhymes, letters and stories in Bangla, made for children."
                : "Opens YouTube on " + KidsArt.TOPICS[n][1] + ": videos made for children, ready to pick from.";
        t.poster = "ytkids:p:" + n;
        t.backdrop = "ytkids:w:" + n;
        t.ok = () -> KidsArt.open(h, n);
        return t;
    }

    /**
     * Movies or TV shows as Discover is laid out: every section a tab along the top ({name, TMDB list, "n" for a
     * numbered Top 10}), sliding along with Left and Right, and its one row of posters under them.
     */
    private static PosterPage sections(HomeV2 h, String crumb, int colour, boolean tv, String[][] spec) {
        String k = h.tmdbKey;
        String[] tabs = new String[spec.length + 1];
        tabs[0] = "Continue Watching";
        for (int i = 0; i < spec.length; i++) tabs[i + 1] = spec[i][0];
        PosterPage p = new PosterPage(h, crumb, colour, tabs, new int[]{584 - 62}, t -> t == 0
                ? Arrays.asList(resumeRow(h, tv))
                // The tabs of their own: Fits tonight (Movies), Your shows (TV shows).
                : "@fits".equals(spec[t - 1][1]) ? Arrays.asList(FitsTonight.row(h))
                : "@yourshows".equals(spec[t - 1][1]) ? Arrays.asList(YourShows.row(h))
                : "@newepisodes".equals(spec[t - 1][1]) ? Arrays.asList(YourShows.fresh(h))
                : "@surprise".equals(spec[t - 1][1]) ? Arrays.asList(Surprise.row(h))
                : "@dice".equals(spec[t - 1][1]) ? Arrays.asList(EpisodeDice.row(h))
                // A Top 10 stays one page (it is numbered); every other tab runs to two.
                : Arrays.asList(row(null, null, "n".equals(spec[t - 1][2]),
                        "n".equals(spec[t - 1][2]) ? tmdb(k, spec[t - 1][1], tv) : tmdbRow(k, spec[t - 1][1], tv))));
        // Nothing to continue: the tab after it, past Your shows too when no series has been watched yet.
        if (!resumeEmpty(h, tv)) return p.startOn(0);
        return p.startOn("@yourshows".equals(spec[0][1]) && !YourShows.any(h.a) ? 2 : 1);
    }

    /**
     * Continue Watching, the first tab of Discover (films and series), Movies and TV shows: what is part-watched in any
     * app, the newest first. Always there; with nothing started yet the page opens on the tab after it.
     */
    static Row resumeRow(HomeV2 h, Boolean tv) {
        Row r = row(null, null, false, WatchHistory.titles(h.a, tv));
        r.empty = "Nothing to pick up yet. " + (tv == null ? "Films and series" : tv ? "Series" : "Films")
                + " you start in Netflix, Prime Video, Disney+, Debrid Stream and the rest show here, with how far you got.";
        return r;
    }

    private static boolean resumeEmpty(HomeV2 h, Boolean tv) {
        return WatchHistory.titles(h.a, tv).isEmpty();
    }

    // ---- Apps -------------------------------------------------------------------------------------------------------

    /**
     * Every app as a grid of wide tiles, five across: the app's own TV banner where it has one (as Android TV's home
     * shows them), else its icon and name on glass. The grid moves up a row at a time as the focus goes down.
     */
    static final class AppsPage extends Page {
        /** Five across, three down beside the rail; six across, four down in the dock layout, edge to edge (48 to 1872). */
        private final int COLS, TW, TH, TOP, SHOWN;
        private static final int GAP = 24;
        private FrameLayout grid;
        private final List<FrameLayout> tiles = new ArrayList<>();
        private final List<String> packages = new ArrayList<>();
        private int idx;

        AppsPage(HomeV2 h) {
            super(h);
            COLS = h.dockMode ? 6 : 5;
            TW = h.dockMode ? 284 : 300;
            TH = h.dockMode ? 160 : 169;
            TOP = h.dockMode ? 228 : 236;
            SHOWN = h.dockMode ? 4 : 3;
        }

        @Override void open() {
            super.open();
            TextView crumb = h.text(18, h.semi, 0xFF2AD1C9);
            crumb.setLetterSpacing(0.19f);
            crumb.setText("APPS");
            if (h.dockMode) addAt(crumb, 48, 190, 600, 30); else add(crumb, 216, 196, 600, 30);
            grid = new FrameLayout(h.a);
            grid.setClipChildren(false);
            FrameLayout view = new FrameLayout(h.a);
            view.setClipChildren(false);
            view.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
            view.setClipToOutline(true);
            view.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (h.dockMode) addAt(view, 40, TOP - 16, COLS * (TW + GAP) + 16, SHOWN * (TH + GAP) + 8);
            else add(view, 196, TOP - 16, COLS * (TW + GAP) + 16, SHOWN * (TH + GAP) + 24);
            h.io.execute(() -> {
                android.content.pm.PackageManager pm = h.a.getPackageManager();
                android.content.Intent q = new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);
                List<android.content.pm.ResolveInfo> found = pm.queryIntentActivities(q, 0);
                found.sort((x, y) -> x.loadLabel(pm).toString().compareToIgnoreCase(y.loadLabel(pm).toString()));
                List<String> hidden = Config.load(h.a).hiddenApps;
                List<Object[]> apps = new ArrayList<>();
                for (android.content.pm.ResolveInfo r : found) {
                    String p = r.activityInfo.packageName;
                    if (p.equals(h.a.getPackageName()) || hidden.contains(p)) continue;
                    android.graphics.drawable.Drawable banner = r.activityInfo.loadBanner(pm);
                    if (banner == null) try { banner = pm.getApplicationBanner(p); } catch (Exception ignored) { }
                    apps.add(new Object[]{p, r.loadLabel(pm).toString(), banner, r.loadIcon(pm)});
                }
                h.main.post(() -> build(apps));
            });
        }

        private void build(List<Object[]> apps) {
            for (int i = 0; i < apps.size(); i++) {
                Object[] a = apps.get(i);
                FrameLayout f = new FrameLayout(h.a);
                if (h.lite) f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                f.setClipToOutline(true);
                f.setOutlineProvider(h.round(14));
                f.setBackground(h.fill(14, 0x26FFFFFF));
                if (a[2] != null) {
                    ImageView b = new ImageView(h.a);
                    b.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    b.setImageDrawable((android.graphics.drawable.Drawable) a[2]);
                    f.addView(b, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                } else {
                    LinearLayout in = new LinearLayout(h.a);
                    in.setOrientation(LinearLayout.VERTICAL);
                    in.setGravity(Gravity.CENTER);
                    ImageView ic = new ImageView(h.a);
                    ic.setImageDrawable((android.graphics.drawable.Drawable) a[3]);
                    ic.setClipToOutline(true);
                    ic.setOutlineProvider(h.round(16));
                    in.addView(ic, new LinearLayout.LayoutParams(px(76), px(76)));
                    TextView n = h.text(19, h.regular, Color.WHITE);
                    n.setText((String) a[1]);
                    n.setSingleLine(true);
                    n.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    nl.topMargin = px(10);
                    in.addView(n, nl);
                    f.addView(in, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(TW), px(TH));
                lp.leftMargin = px(8 + (i % COLS) * (TW + GAP));
                lp.topMargin = px(8 + (i / COLS) * (TH + GAP));
                grid.addView(f, lp);
                tiles.add(f);
                packages.add((String) a[0]);
            }
            paint();
        }

        @Override void entered() {
            paint();
        }

        private void paint() {
            for (int i = 0; i < tiles.size(); i++) {
                boolean on = !h.preview && i == idx;
                FrameLayout f = tiles.get(i);
                Fluid.spring(f, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_X, on ? 1.07f : 1f, Fluid.STIFF_SMALL, Fluid.DAMP_SMALL);
                Fluid.spring(f, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, on ? 1.07f : 1f, Fluid.STIFF_SMALL, Fluid.DAMP_SMALL);
                f.setForeground(on ? h.ring(14) : null);
                // Lifted off the page when focused (a spring; no shadow).
                Fluid.lift(f, on, px(22), 0);
            }
            int firstRow = Math.max(0, idx / COLS - (SHOWN - 1));
            grid.animate().translationY(-px(firstRow * (TH + GAP))).setDuration(260).setInterpolator(HomeV2.SKY).start();
        }

        @Override boolean key(KeyEvent e) {
            int code = e.getKeyCode();
            if (ok(code)) {
                if (e.getAction() == KeyEvent.ACTION_UP && idx < packages.size()) h.openApp(packages.get(idx));
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return code != KeyEvent.KEYCODE_BACK;
            int n = tiles.size();
            // At an edge the key is Home's (false): the rail, the top buttons or the boxes.
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (idx % COLS == 0) return false;
                    idx--;
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (idx % COLS < COLS - 1 && idx + 1 < n) idx++;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (idx < COLS) return false;
                    idx -= COLS;
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (idx + COLS >= n) { if (idx / COLS < (n - 1) / COLS) idx = n - 1; else return false; }
                    else idx += COLS;
                    break;
                default:
                    return false;
            }
            paint();
            return true;
        }
    }

    // ---- Sport -----------------------------------------------------------------------------------------------------

    /**
     * Sports, as the Match Centre (option 1): a big stage for the match of the day (its crests, the kick-off or the
     * score, and its buttons: Watch on its channel, Live Centre, Remind me; the Live Centre itself while games are on,
     * F1 timing on a race weekend), beside it one tables panel (the 8 leagues, then F1's drivers and teams, as tabs),
     * under them Tonight: this week's live football as slim cards; then, down, the rows (Continue Watching, more live
     * sport, the highlights). The dock tucks away while the page has the focus.
     */
    static final class SportPage extends Page {
        private FrameLayout cards, fixtures;
        /** Where the stage and the tables panel start, in the page's own place (under the top bar on screen). */
        private int top;
        /** The live football of the week, as slim cards under the stage (row 1, not one of the rows below). */
        private static final int TONIGHT = 1;
        /** Each row's place among the rows below Tonight (-1 for Tonight itself), and how many there are. */
        private final List<Integer> slotOf = new ArrayList<>();
        private int slots;
        private TextView tonightCounts, tonightNone;
        private final List<LinearLayout> strips = new ArrayList<>();
        private final List<List<HomeV2.Tile>> lists = new ArrayList<>();
        private int zone = -1, idx;   // -1: the cards; 0: Continue Watching; 1 and 2: the fixture rows
        /** The row of sport started in any app (row 0), and the line shown in it when there is none. */
        private final List<WatchHistory.Entry> resume = new ArrayList<>();
        private TextView resumeNone;
        private final List<View> rowViews = new ArrayList<>();
        private TextView watch;
        private Runnable watchOk;
        /** While the featured game is live: v1's pitch with the ball moving through the plays, the clock, the score. */
        private boolean alive = true;
        private LivePitch pitch;
        private TextView playLabel, kickerView, scoreView;
        private LinearLayout eventsView;
        private ScoresTicker.Game liveGame;
        private final java.util.Set<String> seenPlays = new java.util.HashSet<>();
        private final java.util.concurrent.ExecutorService playsIo = java.util.concurrent.Executors.newSingleThreadExecutor();
        private final Runnable playsTick = this::readPlays;
        private final Runnable clockTick = this::tickClock;
        private final Runnable scoreTick = this::readScore;

        SportPage(HomeV2 h) {
            super(h);
        }

        /** The dock tucks away while the page has the focus (the chip at the foot brings it back), as on the posters' pages. */
        @Override boolean growsDown() {
            return h.dockMode;
        }

        /**
         * At (x, y) w by hgt in the dock layout's own places (48 to 1872 across); beside the rail, the same span drawn
         * into 216 to 1824.
         */
        private void addExact(FrameLayout parent, View v, int x, int y, int w, int hgt) {
            int lx = h.dockMode ? x : Math.round(216 + (x - 48) / SPAN), lw = h.dockMode || w < 0 ? w : Math.round(w / SPAN);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(lw < 0 ? lw : px(lw), hgt < 0 ? hgt : px(hgt));
            lp.leftMargin = px(lx);
            lp.topMargin = px(y);
            parent.addView(v, lp);
        }

        /** The order Down goes through: Tonight, then the rows below as they are on the page. */
        private List<Integer> order() {
            List<Integer> o = new ArrayList<>();
            o.add(TONIGHT);
            for (int r = 0; r < lists.size(); r++) if (r != TONIGHT) o.add(r);
            return o;
        }

        /** The next row down with anything in it after zone z (-1: the stage and the tables); -2 when none. */
        private int nextZone(int z) {
            List<Integer> o = order();
            int at = z < 0 ? -1 : o.indexOf(z);
            for (int k = at + 1; k < o.size(); k++) if (o.get(k) < lists.size() && !lists.get(o.get(k)).isEmpty()) return o.get(k);
            return -2;
        }

        /** The row above z with anything in it; -1 (the stage and the tables) at the top. */
        private int prevZone(int z) {
            List<Integer> o = order();
            for (int k = o.indexOf(z) - 1; k >= 0; k--) if (!lists.get(o.get(k)).isEmpty()) return o.get(k);
            return -1;
        }

        /** The rows before the highlights: Continue Watching and the two of live sport (holding OK sets a reminder there). */
        private static final int LIVE_ROWS = 3;
        private final java.util.concurrent.ExecutorService highlightsIo = java.util.concurrent.Executors.newSingleThreadExecutor();

        /**
         * A row of 16:9 tiles under its name, the next one down the page (300 apart); its index. Row 1 (this week's live
         * football) is Tonight instead: slim cards under the stage, its name and counts over them (buildTonight).
         */
        private int addRow(String name) {
            int i = strips.size();
            if (i == TONIGHT) {
                slotOf.add(-1);
                FrameLayout view = new FrameLayout(h.a);
                view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
                view.setClipToOutline(true);
                view.setClipChildren(false);
                LinearLayout strip = new LinearLayout(h.a);
                strip.setOrientation(LinearLayout.HORIZONTAL);
                strip.setClipChildren(false);
                strip.setClipToPadding(false);
                // Room round the cards for the focused one to grow and show its whole ring.
                FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(SLIM_H));
                sl.leftMargin = px(12);
                sl.topMargin = px(12);
                view.addView(strip, sl);
                addExact(cards, view, 36, top + 562, 1848, SLIM_H + 24);
                strips.add(strip);
                lists.add(new ArrayList<>());
                return i;
            }
            int slot = slots++;
            slotOf.add(slot);
            TextView l = h.text(18, h.semi, 0xBFFFFFFF);
            l.setLetterSpacing(0.11f);
            l.setText(name);
            addTo(fixtures, l, 216, 236 + slot * 300, 1500, 30);
            rowViews.add(l);
            FrameLayout view = new FrameLayout(h.a);
            view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
            view.setClipToOutline(true);
            view.setClipChildren(false);
            LinearLayout strip = new LinearLayout(h.a);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            // The strip is no wider than the row, but its tiles run on past it and slide in as the row scrolls:
            // not cut off at its own edge (the row's frame does the cutting), or those further along stay blank.
            strip.setClipChildren(false);
            strip.setClipToPadding(false);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(216));
            sl.leftMargin = px(24);
            sl.topMargin = px(24);
            view.addView(strip, sl);
            addTo(fixtures, view, 192, 262 + slot * 300, 1656, 268);
            rowViews.add(view);
            strips.add(strip);
            lists.add(new ArrayList<>());
            return i;
        }

        /**
         * Highlights, a row for each sport under the live sport (SportsHighlights): read off the main thread, a sport at
         * a time, each row added (in the sports' order) once its videos are in; a sport with none gets no row.
         */
        private void loadHighlights() {
            highlightsIo.execute(() -> {
                for (SportsHighlights.Sport sp : SportsHighlights.SPORTS) {
                    if (!alive) return;
                    List<SportsHighlights.Clip> got = SportsHighlights.load(sp);
                    if (got.isEmpty()) continue;
                    h.main.post(() -> { if (alive) addHighlights(sp, got); });
                }
            });
        }

        private void addHighlights(SportsHighlights.Sport sp, List<SportsHighlights.Clip> clips) {
            int r = addRow("HIGHLIGHTS  ·  " + sp.name);
            // Under the cards, the focused one's title, channel and age (nothing written over the pictures).
            View rowFrame = rowViews.isEmpty() ? null : rowViews.get(rowViews.size() - 1);
            if (rowFrame instanceof FrameLayout) {
                TextView cap = h.text(15, h.regular, Color.WHITE);
                cap.setSingleLine(true);
                cap.setEllipsize(TextUtils.TruncateAt.END);
                cap.setGravity(Gravity.CENTER_VERTICAL);
                FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(1560), px(20), Gravity.TOP | Gravity.START);
                cl.leftMargin = px(26);
                cl.topMargin = px(24 + 216 + 8);
                ((FrameLayout) rowFrame).addView(cap, cl);
                captions.put(r, cap);
                rowClips.put(r, clips);
            }
            for (int i = 0; i < clips.size(); i++) {
                SportsHighlights.Clip c = clips.get(i);
                HomeV2.Tile t = new HomeV2.Tile();
                t.title = c.title;
                int at = i;
                // OK: the video big on Home, the event's details beside it (HighlightPlayer).
                t.ok = () -> watch(r, clips, at);
                lists.get(r).add(t);
                strips.get(r).addView(highlight(c));
            }
            paint();
        }

        /** The highlight playing large over the page, or null. */
        private HighlightPlayer player;

        private void watch(int row, List<SportsHighlights.Clip> clips, int at) {
            if (player != null && player.isShowing()) return;
            View card = row < strips.size() ? strips.get(row).getChildAt(at) : null;
            player = new HighlightPlayer(h, new HighlightPlayer.Host() {
                @Override public View card(int i) {
                    return row < strips.size() ? strips.get(row).getChildAt(i) : null;
                }
                @Override public void focus(int i) {
                    if (row >= lists.size() || i >= lists.get(row).size()) return;
                    zone = row;
                    idx = i;
                    paint();
                }
                @Override public void openInYouTube(String id) {
                    playYouTube(id);
                }
            }, clips, at);
            player.open(card);
        }

        @Override boolean back() {
            if (player != null && player.isShowing()) { player.close(); return true; }
            return false;
        }

        /** A video in YouTube on the TV (its own app; any app that plays YouTube links when it has none). */
        private void playYouTube(String id) {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/watch?v=" + id)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                h.a.startActivity(new android.content.Intent(i).setPackage("com.google.android.youtube.tv"));
            } catch (RuntimeException e) {
                try {
                    h.a.startActivity(i);
                } catch (RuntimeException ignored) {
                    // nothing here plays YouTube
                }
            }
        }

        /**
         * A highlight's tile, clean (SportsHighlights.card): the video's own widescreen picture with only what it is
         * (HIGHLIGHTS, FULL FIGHT…) and the competition's own logo over it; its title goes under the row (captions).
         */
        private View highlight(SportsHighlights.Clip c) {
            FrameLayout f = SportsHighlights.card(h, c, 384, 216, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(384), px(216));
            lp.rightMargin = px(24);
            f.setLayoutParams(lp);
            f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            return f;
        }

        /** Each highlights row's caption under its cards, the clips it shows, and the one it last had the focus on. */
        private final Map<Integer, TextView> captions = new HashMap<>();
        private final Map<Integer, List<SportsHighlights.Clip>> rowClips = new HashMap<>();
        private final Map<Integer, Integer> captionAt = new HashMap<>();

        /** The focused highlight's title, channel and age under its row; another row's under it stays on the last one. */
        private void captions() {
            for (Map.Entry<Integer, TextView> e : captions.entrySet()) {
                int r = e.getKey();
                List<SportsHighlights.Clip> cl = rowClips.get(r);
                if (cl == null || cl.isEmpty()) continue;
                if (zone == r) captionAt.put(r, Math.max(0, Math.min(idx, cl.size() - 1)));
                Integer at = captionAt.get(r);
                SportsHighlights.Clip c = cl.get(at == null ? 0 : Math.min(at, cl.size() - 1));
                TextView t = e.getValue();
                if (t.getTag() != c) {
                    t.setTag(c);
                    t.setText(SportsHighlights.caption(c));
                }
                if (paintedCaptionZone != zone) t.animate().alpha(zone == r ? 1f : 0.55f).setDuration(160).start();
            }
            paintedCaptionZone = zone;
        }

        @Override void open() {
            super.open();
            // Under option B's one-line top bar the page sits PAGE_LIFT higher: the stage then starts 140 down the screen.
            top = h.corners ? 140 + h.pageLift() : 206;
            cards = new FrameLayout(h.a);
            cards.setClipChildren(false);
            add(cards, 0, 0, 1920, 1080);
            fixtures = new FrameLayout(h.a);
            fixtures.setClipChildren(false);
            add(fixtures, 0, 0, 1920, 1080);
            fixtures.setTranslationY(px(restY()));
            String[] names = {"CONTINUE WATCHING", "LIVE FOOTBALL · THIS WEEK", "MORE LIVE SPORT"};
            for (String name : names) addRow(name);
            buildTonight();
            // Continue Watching: matches, highlights and races part-watched in any app (Sky Sports, TNT Sports, DAZN,
            // F1 TV, Debrid Stream…), each with how far in; OK opens the app it was in.
            resumeNone = h.text(22, h.regular, 0x99FFFFFF);
            resumeNone.setText("Nothing to pick up yet. Matches, highlights and races you start in Sky Sports, TNT Sports, DAZN, F1 TV and the rest show here.");
            addTo(fixtures, resumeNone, 216, 290, 1500, 40);
            for (WatchHistory.Entry e : WatchHistory.sports(h.a)) {
                resume.add(e);
                HomeV2.Tile t = new HomeV2.Tile();
                t.title = e.title;
                String pkg = e.pkg;
                t.ok = () -> h.openApp(pkg);
                lists.get(0).add(t);
                strips.get(0).addView(resumeTile(e));
            }
            resumeNone.setVisibility(resume.isEmpty() ? View.VISIBLE : View.GONE);
            loadHighlights();
            buildMatch();
            buildTable();
            buildF1();
            // The fixtures as last read, at once (read ahead from Home); then read again and put in place of them.
            List<HomeV2.Tile> have = h.sportTiles;
            if (have != null) showFixtures(have);
            // While the first read goes on, Sky's tiles as soon as they are known.
            if (have == null) h.sportEarly = early -> h.main.post(() -> { if (alive && lists.get(1).isEmpty() && lists.get(2).isEmpty()) showFixtures(early); });
            if (have == null || !h.sportFresh()) {
                java.util.concurrent.Future<List<HomeV2.Tile>> reading = h.sportFetch();
                new Thread(() -> {
                    List<HomeV2.Tile> all;
                    try {
                        all = reading.get(120, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        all = new ArrayList<>();
                    }
                    List<HomeV2.Tile> got = all;
                    h.main.post(() -> { if (alive && (!got.isEmpty() || have == null)) showFixtures(got); });
                }, "sport-wait").start();
            }
            paint();
        }

        /** Tonight's cards: 288 by 132, 18 apart. */
        private static final int SLIM_W = 288, SLIM_H = 132, SLIM_GAP = 18;

        /** Where the rows below sit while the stage has the focus: their first name just under Tonight. */
        private int restY() {
            return top + 506;
        }

        /** Tonight's name over its cards, and on the right the counts: how many are on now and this week. */
        private void buildTonight() {
            TextView name = h.text(18, h.semi, 0xCCFFFFFF);
            name.setLetterSpacing(0.11f);
            name.setText("TONIGHT  ·  LIVE FOOTBALL THIS WEEK");
            name.setSingleLine(true);
            addExact(cards, name, 48, top + 528, 1000, 30);
            tonightCounts = h.text(16, h.regular, 0xBFFFFFFF);
            tonightCounts.setGravity(Gravity.END);
            tonightCounts.setSingleLine(true);
            addExact(cards, tonightCounts, 1000, top + 530, 872, 28);
            tonightNone = h.text(20, h.regular, 0x99FFFFFF);
            tonightNone.setText("Finding this week's live football…");
            addExact(cards, tonightNone, 48, top + 620, 1500, 34);
        }

        /** The competition's short name from its ESPN logo (the leagues the tables know); null for another. */
        private static String compName(String logo) {
            if (logo == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/(\\d+)\\.png").matcher(logo);
            if (!m.find()) return null;
            for (Object[] l : LEAGUES) if (m.group(1).equals(l[3])) return ((String) l[1]).toUpperCase(Locale.UK);
            return null;
        }

        /**
         * One of Tonight's games as a slim card: the competition and the channel's logo along the top, then each club
         * with its crest beside it, and after the whole fixture the kick-off (LIVE while it is on).
         */
        private View slim(HomeV2.Tile t) {
            FrameLayout f = new FrameLayout(h.a);
            f.setClipToOutline(true);
            f.setOutlineProvider(h.round(20));
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x26FFFFFF, 0x0FFFFFFF});
            g.setCornerRadius(px(20));
            g.setStroke(px(1.5f), 0x38FFFFFF);
            f.setBackground(g);
            // Focused, a solid ground of its own: the see-through glass let the lifted card's shadow show through it as
            // a dark patch with a bright edge (the odd "shine" inside the box). Solid, the shadow stays outside, round
            // the card, and the white ring follows its corners.
            GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF323E92, 0xFF1F2766});
            on.setCornerRadius(px(20));
            slimGrounds.put(f, new android.graphics.drawable.Drawable[]{g, on});
            LinearLayout col = new LinearLayout(h.a);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(px(18), px(14), px(16), px(12));
            f.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout l1 = new LinearLayout(h.a);
            l1.setGravity(Gravity.CENTER_VERTICAL);
            String comp = compName(t.compLogo);
            if (t.compLogo != null) {
                // The competition by its logo alone (its name for the screen reader), large enough to read.
                ImageView cl = new ImageView(h.a);
                cl.setScaleType(ImageView.ScaleType.FIT_START);
                cl.setContentDescription(comp != null ? comp : "Football");
                l1.addView(cl, new LinearLayout.LayoutParams(0, px(28), 1f));
                h.image(cl, t.compLogo, px(56));
            } else {
                TextView cn = h.text(13, h.semi, 0xB3FFFFFF);
                cn.setText(comp != null ? comp : "FOOTBALL");
                cn.setLetterSpacing(0.1f);
                cn.setSingleLine(true);
                cn.setEllipsize(TextUtils.TruncateAt.END);
                l1.addView(cn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            if (t.badge != null) {
                ImageView ch = new ImageView(h.a);
                ch.setAdjustViewBounds(true);
                ch.setMaxHeight(px(18));
                ch.setMaxWidth(px(104));
                LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(18));
                chl.leftMargin = px(8);
                l1.addView(ch, chl);
                h.image(ch, t.badge, px(104));
            } else if (t.channel != null) {
                TextView ch = h.text(13, h.regular, 0xB3FFFFFF);
                ch.setText(t.channel);
                ch.setSingleLine(true);
                ch.setMaxWidth(px(120));
                ch.setEllipsize(TextUtils.TruncateAt.END);
                l1.addView(ch);
            }
            col.addView(l1);
            String[] clubs = t.title == null ? new String[]{"", ""} : t.title.split(" v ", 2);
            ImageView c1 = crest(), c2 = crest();
            for (int k = 0; k < 2; k++) {
                LinearLayout line = new LinearLayout(h.a);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.addView(k == 0 ? c1 : c2, new LinearLayout.LayoutParams(px(24), px(24)));
                TextView n = h.text(19, h.semi, Color.WHITE);
                n.setText(short_(k < clubs.length ? clubs[k].trim() : ""));
                n.setSingleLine(true);
                n.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nl.leftMargin = px(10);
                line.addView(n, nl);
                if (k == 1) {
                    // After the whole fixture: the kick-off (today's time; another day's with its day), or LIVE.
                    boolean live = "LIVE NOW".equals(t.chip);
                    TextView when = h.text(15, h.semi, live ? 0xFFFF3B4A : 0xFFFFD04D);
                    String at = t.start <= 0 ? t.chip : DateTimeFormatter.ofPattern(
                            Instant.ofEpochMilli(t.start).atZone(ZoneId.systemDefault()).toLocalDate().equals(java.time.LocalDate.now()) ? "HH:mm" : "EEE HH:mm",
                            Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(t.start));
                    when.setText(live ? "●  LIVE" : at);
                    LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    wl.leftMargin = px(8);
                    line.addView(when, wl);
                }
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ll.topMargin = px(k == 0 ? 12 : 8);
                col.addView(line, ll);
            }
            // The crests: from the game's own picture ("crest:home|away"), else looked up by the clubs' names.
            if (t.image != null && t.image.startsWith("crest:")) {
                String[] urls = t.image.substring(6).split("\\|", 2);
                if (urls.length == 2) { h.image(c1, urls[0], px(48)); h.image(c2, urls[1], px(48)); }
            } else if (clubs.length == 2) {
                h.io.execute(() -> {
                    String u1 = PlaylistSport.crest(clubs[0].trim()), u2 = PlaylistSport.crest(clubs[1].trim());
                    h.main.post(() -> {
                        if (!alive) return;
                        if (u1 != null) h.image(c1, u1, px(48));
                        if (u2 != null) h.image(c2, u2, px(48));
                    });
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(SLIM_W), px(SLIM_H));
            lp.rightMargin = px(SLIM_GAP);
            f.setLayoutParams(lp);
            return f;
        }

        private void showFixtures(List<HomeV2.Tile> all) {
            List<HomeV2.Tile> foot = new ArrayList<>(), other = new ArrayList<>();
            for (HomeV2.Tile t : all) ("football".equals(t.group) ? foot : other).add(t);
            // Where the focus was in a row, kept as near as the new list allows.
            lists.set(1, foot);
            lists.set(2, other);
            for (int i = 1; i < 3; i++) {
                strips.get(i).removeAllViews();
                for (HomeV2.Tile t : lists.get(i)) strips.get(i).addView(i == TONIGHT ? slim(t) : fixture(t));
            }
            if (tonightNone != null) {
                tonightNone.setText("No live football on in the next few days.");
                tonightNone.setVisibility(foot.isEmpty() ? View.VISIBLE : View.GONE);
            }
            if (zone >= 1 && zone < lists.size()) idx = Math.min(idx, Math.max(0, lists.get(zone).size() - 1));
            paint();
        }

        @Override void close() {
            alive = false;
            if (player != null) player.removeNow();
            h.sportEarly = null;
            h.main.removeCallbacks(playsTick);
            h.main.removeCallbacks(clockTick);
            h.main.removeCallbacks(scoreTick);
            h.main.removeCallbacks(liveTick);
            playsIo.shutdownNow();
            highlightsIo.shutdownNow();
            super.close();
        }

        /** The latest plays every 10 seconds, each new one played out on the pitch. */
        private void readPlays() {
            h.main.removeCallbacks(playsTick);
            ScoresTicker.Game g = liveGame;
            if (!alive || g == null || g.state != 0 || g.slug == null) return;
            boolean first = seenPlays.isEmpty();
            playsIo.execute(() -> {
                List<LivePitch.Play> plays = LivePitch.latest(g.slug, g.id, g.homeId);
                h.main.post(() -> {
                    if (!alive) return;
                    List<LivePitch.Play> fresh = new ArrayList<>();
                    for (LivePitch.Play p : plays) if (seenPlays.add(p.id)) fresh.add(p);
                    if (!fresh.isEmpty()) pitch.add(fresh, first);
                    h.main.postDelayed(playsTick, 10_000L);
                });
            });
        }

        /** The match clock, counting on by the second from ESPN's last reading; half-time at the break. */
        private void tickClock() {
            h.main.removeCallbacks(clockTick);
            ScoresTicker.Game g = liveGame;
            if (!alive || g == null || g.state != 0) return;
            if (g.halftime) {
                kickerView.setText("●  HALF-TIME");
            } else {
                long secs = (long) g.clockSec + (System.currentTimeMillis() - g.clockAt) / 1000L;
                kickerView.setText(String.format(Locale.ROOT, "●  LIVE  %d:%02d", secs / 60, secs % 60));
            }
            h.main.postDelayed(clockTick, 1000L);
        }

        /** The score, the clock's reading and the scorers again every 30 seconds. */
        private void readScore() {
            h.main.removeCallbacks(scoreTick);
            ScoresTicker.Game g = liveGame;
            if (!alive || g == null) return;
            String[] leagues = h.a.getResources().getStringArray(R.array.score_leagues);
            playsIo.execute(() -> {
                ScoresTicker.Game now = null;
                for (ScoresTicker.Game x : ScoresTicker.load(leagues)) if (g.id != null && g.id.equals(x.id)) now = x;
                ScoresTicker.Game fresh = now;
                h.main.post(() -> {
                    if (!alive || fresh == null) return;
                    liveGame = fresh;
                    scoreView.setText(fresh.homeScore + " – " + fresh.awayScore);
                    goals(eventsView, fresh, 1);
                    if (fresh.state != 0) kickerView.setText("FULL TIME");
                    else h.main.postDelayed(scoreTick, 30_000L);
                });
            });
        }

        private void goals(LinearLayout events, ScoresTicker.Game g, int max) {
            events.removeAllViews();
            List<String[]> list = new ArrayList<>(g.goals);
            int from = Math.max(0, list.size() - max);
            for (int i = from; i < list.size(); i++) {
                String[] goal = list.get(i);
                TextView ev = h.text(19, h.regular, Color.WHITE);
                String tag = "pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (og)" : "";
                ev.setText(goal[2] + "   ⚽ " + goal[1] + tag);
                ev.setGravity("home".equals(goal[3]) ? Gravity.START : Gravity.END);
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gl.bottomMargin = px(6);
                events.addView(ev, gl);
            }
        }

        private void addTo(FrameLayout parent, View v, int x, int y, int w, int hgt) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(wide(x, w)), px(hgt));
            lp.leftMargin = px(across(x));
            lp.topMargin = px(y);
            parent.addView(v, lp);
        }

        /** The stage: its ground (the clubs' colours either side), its buttons along the foot and which has the focus. */
        private LinearLayout stage, stageButtons;
        private GradientDrawable stageBg;
        private TextView liveBtn, remindBtn, timingBtn, stageHint;
        private final List<TextView> stageBtns = new ArrayList<>();
        private int stageIdx;
        /** The match of the day, for Remind me: its name, kick-off, state (1 to come), channel and its logo. */
        private String matchTitle, matchChannel, matchLogo;
        private long matchStart;
        private int matchState = -1;

        /** The stage's ground: the home side's colour on the left, the away side's on the right, the night between. */
        private void stageGround(int home, int away) {
            int hc = home != 0 ? (home & 0xFFFFFF) | 0x8C000000 : 0x40243A9A;
            int ac = away != 0 ? (away & 0xFFFFFF) | 0x73000000 : 0x40243A9A;
            stageBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{hc, 0x4D171A5C, 0x4D171A5C, ac});
            stageBg.setCornerRadius(px(30));
            stageBg.setStroke(px(1.5f), 0x3DFFFFFF);
            stage.setBackground(stageBg);
        }

        /** before, the Live Centre's stamp (heightPx design pixels high, in line with the words), then after. */
        private CharSequence withStamp(String before, String after, int heightPx) {
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(before);
            int at = b.length();
            b.append("  ");
            android.graphics.Bitmap bm = LiveCentre.stampPicture(h.a, px(heightPx));
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(h.a.getResources(), bm);
            d.setBounds(0, 0, bm.getWidth(), bm.getHeight());
            b.setSpan(new MidSpan(d), at, at + 2, 0);
            b.append(after);
            return b;
        }

        /** One of the stage's round buttons along its foot. */
        private TextView stageButton(String label) {
            TextView b = h.text(19, h.regular, Color.WHITE);
            b.setText(label);
            b.setGravity(Gravity.CENTER);
            b.setSingleLine(true);
            b.setPadding(px(26), 0, px(26), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (!stageBtns.isEmpty()) lp.leftMargin = px(14);
            stageButtons.addView(b, lp);
            stageBtns.add(b);
            return b;
        }

        /** The stage's buttons that show, in order (Left and Right go through these). */
        private List<TextView> shownButtons() {
            List<TextView> out = new ArrayList<>();
            for (TextView b : stageBtns) if (b.getVisibility() == View.VISIBLE) out.add(b);
            return out;
        }

        /** A club on the stage: its crest large, its name under it. */
        private LinearLayout bigTeam(ImageView crest, TextView name, int size) {
            LinearLayout t = new LinearLayout(h.a);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.addView(crest, new LinearLayout.LayoutParams(px(size), px(size)));
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(320), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(14);
            t.addView(name, lp);
            return t;
        }

        /** "Kick-off in 25 min" (within three hours), else "Kick-off Wed 20:00". */
        private static String kickoffIn(long at) {
            long mins = (at - System.currentTimeMillis()) / 60_000L;
            if (mins <= 0) return "Kick-off now";
            if (mins < 60) return "Kick-off in " + mins + " min";
            if (mins < 180) return "Kick-off in " + (mins / 60) + "h " + (mins % 60) + "m";
            return "Kick-off " + DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at));
        }

        /** Each side's scorers on its own side under the stage's clubs ("Barry 49'"): the home side's left, the away's right. */
        private void scorers(LinearLayout events, ScoresTicker.Game g) {
            events.removeAllViews();
            StringBuilder home = new StringBuilder(), away = new StringBuilder();
            for (String[] goal : g.goals) {
                StringBuilder to = "home".equals(goal[3]) ? home : away;
                if (to.length() > 0) to.append("   ");
                to.append(goal[1]).append(' ').append(goal[2]).append("pen".equals(goal[4]) ? " (pen)" : "og".equals(goal[4]) ? " (og)" : "");
            }
            if (home.length() == 0 && away.length() == 0) return;
            LinearLayout line = new LinearLayout(h.a);
            TextView a = h.text(18, h.regular, 0xD9FFFFFF), b = h.text(18, h.regular, 0xD9FFFFFF);
            a.setText(home.length() > 0 ? "⚽  " + home : "");
            b.setText(away.length() > 0 ? away + "  ⚽" : "");
            b.setGravity(Gravity.END);
            for (TextView t : new TextView[]{a, b}) { t.setMaxLines(2); t.setEllipsize(TextUtils.TruncateAt.END); }
            line.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            events.addView(line);
        }

        /** Remind me on the stage: a reminder for the match of the day before it starts (pressed again, taken off). */
        private void remindMatch() {
            if (matchTitle == null) return;
            String msg;
            // (A toast shows words only: the key is not named in it.)
            if (matchState != 1 || matchStart <= System.currentTimeMillis()) msg = matchState == 2 ? "Full time · " + matchTitle : "Already on: watch it now";
            else {
                boolean on = Reminders.toggle(h.a, matchTitle, matchStart, matchChannel, matchLogo);
                remindBtn.setText(on ? "✓  Reminder set" : "Remind me");
                String when = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(matchStart));
                msg = on ? "🔔  Reminder set · " + matchTitle + " · " + when : "Reminder off · " + matchTitle;
            }
            android.widget.Toast.makeText(h.a, msg, android.widget.Toast.LENGTH_SHORT).show();
        }

        /** OK on one of the stage's buttons. */
        private void pressStage() {
            List<TextView> shown = shownButtons();
            if (shown.isEmpty()) return;
            TextView b = shown.get(Math.max(0, Math.min(stageIdx, shown.size() - 1)));
            if (b == watch) { if (watchOk != null) watchOk.run(); }
            else if (b == liveBtn) LiveCentre.open(h.a, null);
            else if (b == remindBtn) remindMatch();
            else if (b == timingBtn) F1Tower.open(h.a, true);
        }

        /** The last match of the day shown, with its crests: shown at once when Sport opens again. */
        private static volatile Object[] lastMatch;

        /**
         * The stage, the match of the day: live first, else the latest result, else the next kick-off. Along its top the
         * competition and how long until kick-off (the minute while live); the two clubs large, each with its crest, the
         * kick-off (or the score) between them; along its foot Watch on (the channel's logo, OK), Live Centre, Remind me.
         */
        private void buildMatch() {
            LinearLayout c = new LinearLayout(h.a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(px(40), px(28), px(40), px(26));
            stage = c;
            stageGround(0, 0);
            addExact(cards, c, 48, top, 1200, 500);
            LinearLayout head = new LinearLayout(h.a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView compLogo = new ImageView(h.a);
            compLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams cll = new LinearLayout.LayoutParams(px(32), px(32));
            cll.rightMargin = px(12);
            head.addView(compLogo, cll);
            TextView comp = h.text(16, h.semi, 0xD9FFFFFF);
            comp.setLetterSpacing(0.16f);
            comp.setSingleLine(true);
            comp.setEllipsize(TextUtils.TruncateAt.END);
            comp.setText("MATCH OF THE DAY");
            head.addView(comp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView k = h.text(16, h.semi, Color.WHITE);
            k.setPadding(px(14), px(6), px(14), px(6));
            k.setBackground(h.fill(14, 0x59000000));
            k.setSingleLine(true);
            k.setVisibility(View.INVISIBLE);
            head.addView(k);
            c.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout score = new LinearLayout(h.a);
            score.setOrientation(LinearLayout.HORIZONTAL);
            score.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sl.topMargin = px(22);
            c.addView(score, sl);
            ImageView hc = crest(), ac = crest();
            android.graphics.Typeface outfit = h.a.getResources().getFont(R.font.outfit_bold);
            TextView hn = h.text(36, outfit, Color.WHITE), an = h.text(36, outfit, Color.WHITE);
            TextView n = h.text(88, h.a.getResources().getFont(R.font.outfit_extrabold), Color.WHITE);
            n.setLetterSpacing(-0.02f);
            n.setIncludeFontPadding(false);
            n.setText("v");
            LinearLayout home = bigTeam(hc, hn, 160), away = bigTeam(ac, an, 160);
            score.addView(home);
            LinearLayout mid = new LinearLayout(h.a);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setGravity(Gravity.CENTER_HORIZONTAL);
            mid.addView(n);
            // Under the kick-off (or the score): the day and the ground, or how it ended.
            TextView res = h.text(18, h.regular, 0xBFFFFFFF);
            res.setGravity(Gravity.CENTER);
            res.setSingleLine(true);
            res.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(px(360), ViewGroup.LayoutParams.WRAP_CONTENT);
            rl.topMargin = px(8);
            mid.addView(res, rl);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ml.leftMargin = px(20);
            ml.rightMargin = px(20);
            score.addView(mid, ml);
            score.addView(away);
            // Under the clubs: possession and the scorers (the pitch and the plays while it is live).
            LinearLayout info = new LinearLayout(h.a);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            il.topMargin = px(10);
            c.addView(info, il);
            LinearLayout poss = new LinearLayout(h.a);
            poss.setOrientation(LinearLayout.VERTICAL);
            info.addView(poss, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout events = new LinearLayout(h.a);
            events.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            el.topMargin = px(8);
            info.addView(events, el);
            stageButtons = new LinearLayout(h.a);
            stageButtons.setOrientation(LinearLayout.HORIZONTAL);
            stageButtons.setGravity(Gravity.CENTER_VERTICAL);
            c.addView(stageButtons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(60)));
            watch = stageButton("Finding the channel…");
            watch.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20));
            // The Live Centre named by its own stamp (LIVE on the green, CENTRE on white), never in words.
            liveBtn = stageButton("");
            liveBtn.setText(withStamp("", "", 30));
            liveBtn.setContentDescription("Live Centre");
            remindBtn = stageButton("Remind me");
            timingBtn = stageButton("F1 timing");
            timingBtn.setVisibility(View.GONE);
            stageHint = h.text(16, h.regular, 0xBFFFFFFF);
            stageHint.setText(withStamp("While games are on, this turns into ", ": the pitch, the plays, every score", 24));
            stageHint.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            stageHint.setMaxLines(2);
            stageHint.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            hl.leftMargin = px(20);
            stageButtons.addView(stageHint, hl);
            // Just the three buttons: the words beside them ("While games are on, this turns into the Live Centre…")
            // kept out of sight (the view stays, so what sets it still can).
            stageHint.setVisibility(View.GONE);

            String[] leagues = h.a.getResources().getStringArray(R.array.score_leagues);
            String[] footballTv = h.a.getResources().getStringArray(R.array.football_tv_channels);
            // The match, its crests and competition's logo as soon as they are known (last time's at once on opening),
            // ahead of the possession, the channel and the rest, which take longer to read.
            java.util.function.Consumer<Object[]> early = m -> {
                ScoresTicker.Game g = (ScoresTicker.Game) m[0];
                comp.setText((ScoresTicker.competition(g) + "  ·  MATCH OF THE DAY").toUpperCase(Locale.UK));
                if (m[1] != null) hc.setImageBitmap((android.graphics.Bitmap) m[1]);
                if (m[2] != null) ac.setImageBitmap((android.graphics.Bitmap) m[2]);
                if (m[3] != null) compLogo.setImageBitmap((android.graphics.Bitmap) m[3]);
                hn.setText(g.homeName != null ? short_(g.homeName) : g.home);
                an.setText(g.awayName != null ? short_(g.awayName) : g.away);
                n.setText(g.state == 1 ? DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(g.kickoff))
                        : g.homeScore + " – " + g.awayScore);
                stageGround(g.homeColor, g.awayColor);
                paint();
            };
            if (lastMatch != null) early.accept(lastMatch);
            h.io.execute(() -> {
                // Games under way in the Premier League, the Championship or Europe's top leagues: the Live Centre
                // on the stage in place of the match of the day.
                List<ScoresTicker.Game> live = liveGames();
                if (!live.isEmpty()) {
                    h.main.post(() -> buildLive(c, live));
                    return;
                }
                List<ScoresTicker.Game> games = ScoresTicker.load(leagues);
                ScoresTicker.Game best = null;
                for (ScoresTicker.Game g : games) {
                    if (g.drivers != null || g.text != null || g.home == null) continue;
                    if (best == null) { best = g; continue; }
                    int rank = g.state == 0 ? 0 : g.state == 2 ? 1 : 2, br = best.state == 0 ? 0 : best.state == 2 ? 1 : 2;
                    if (rank < br || rank == br && (rank == 1 ? g.kickoff > best.kickoff : g.kickoff < best.kickoff)) best = g;
                }
                if (best == null) {
                    h.main.post(() -> {
                        comp.setText("MATCH OF THE DAY");
                        n.setText("–");
                        res.setText("No games today");
                        watch.setText("Nothing on today");
                        remindBtn.setVisibility(View.GONE);
                        paint();
                    });
                    return;
                }
                ScoresTicker.Game g = best;
                // The crests from the logo cache on disk, straight away (not queued behind the fixtures' pictures).
                Object[] m = {g, g.homeLogo == null ? null : ScoresTicker.cachedLogo(h.a, g.homeLogo, px(320)),
                        g.awayLogo == null ? null : ScoresTicker.cachedLogo(h.a, g.awayLogo, px(320)),
                        g.leagueLogo == null ? null : ScoresTicker.cachedLogo(h.a, g.leagueLogo, px(64))};
                lastMatch = m;
                h.main.post(() -> early.accept(m));
                String[] summary = summary(g);   // {home possession, away possession, shootout home, shootout away}
                TvGuide.Channel ch = TvGuide.find(footballTv, g.homeName, g.awayName, g.kickoff);
                // Not on British TV: the channel the Sports tiles name for it (the league's own, or the playlist's).
                String art = g.homeLogo != null && g.awayLogo != null ? "crest:" + g.homeLogo + "|" + g.awayLogo : null;
                PlaylistSport.Where other = ch == null && g.state != 2 ? PlaylistSport.where(h, g.homeName, g.awayName, g.slug, art) : null;
                h.main.post(() -> {
                    if (!alive) return;
                    k.setVisibility(View.VISIBLE);
                    k.setText(g.state == 0 ? "●  LIVE" : g.state == 2 ? "FULL TIME" : kickoffIn(g.kickoff));
                    k.setBackground(h.fill(14, g.state == 0 ? 0xE6E0263A : 0x59000000));
                    comp.setText((ScoresTicker.competition(g) + "  ·  MATCH OF THE DAY").toUpperCase(Locale.UK));
                    if (g.leagueLogo != null && m[3] == null) h.image(compLogo, g.leagueLogo, px(64));
                    if (m[1] == null) h.image(hc, g.homeLogo, px(320));
                    if (m[2] == null) h.image(ac, g.awayLogo, px(320));
                    hn.setText(g.homeName != null ? short_(g.homeName) : g.home);
                    an.setText(g.awayName != null ? short_(g.awayName) : g.away);
                    String day = DateTimeFormatter.ofPattern("EEEE", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(g.kickoff));
                    if (g.state == 1) {
                        n.setText(DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(g.kickoff)));
                        res.setText(day + (g.venue == null || g.venue.isEmpty() ? "" : "  ·  " + g.venue));
                    } else {
                        n.setText(g.homeScore + " – " + g.awayScore);
                        String r = g.detail == null ? "" : g.detail;
                        if (summary[2] != null) {
                            int sh = Integer.parseInt(summary[2]), sa = Integer.parseInt(summary[3]);
                            r = (sh > sa ? short_(g.homeName) : short_(g.awayName)) + " win " + Math.max(sh, sa) + "–" + Math.min(sh, sa) + " on penalties";
                        }
                        res.setText(r);
                    }
                    if (summary[0] != null) possession(poss, summary[0], summary[1], g.homeColor, g.awayColor);
                    stageGround(g.homeColor, g.awayColor);
                    kickerView = k;
                    scoreView = n;
                    eventsView = events;
                    matchTitle = (g.homeName != null ? short_(g.homeName) : g.home) + " v " + (g.awayName != null ? short_(g.awayName) : g.away);
                    matchStart = g.kickoff;
                    matchState = g.state;
                    if (g.state != 1) remindBtn.setVisibility(View.GONE);
                    if (g.state == 0) {
                        // Live: v1's pitch, the ball moving through ESPN's play-by-play, the latest touch beside it.
                        liveGame = g;
                        pitch = new LivePitch(h.a, h.a.getResources().getDisplayMetrics().density);
                        pitch.setColors(g.homeColor != 0 ? 0xFF000000 | g.homeColor : Color.WHITE, g.awayColor != 0 ? 0xFF000000 | g.awayColor : 0xFFFFD54F);
                        playLabel = h.text(18, h.regular, 0xD9FFFFFF);
                        playLabel.setMaxLines(2);
                        playLabel.setEllipsize(TextUtils.TruncateAt.END);
                        pitch.setListener(play -> playLabel.setText(play.label));
                        // Room for the pitch: the crests a little smaller, the score a little less tall.
                        for (ImageView cr : new ImageView[]{hc, ac}) {
                            ViewGroup.LayoutParams cl = cr.getLayoutParams();
                            cl.width = px(110);
                            cl.height = px(110);
                            cr.setLayoutParams(cl);
                        }
                        n.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(76));
                        LinearLayout pitchRow = new LinearLayout(h.a);
                        pitchRow.setGravity(Gravity.CENTER_VERTICAL);
                        pitchRow.addView(pitch, new LinearLayout.LayoutParams(px(440), px(140)));
                        LinearLayout side = new LinearLayout(h.a);
                        side.setOrientation(LinearLayout.VERTICAL);
                        side.addView(playLabel);
                        info.removeView(events);
                        LinearLayout.LayoutParams evl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        evl.topMargin = px(10);
                        side.addView(events, evl);
                        LinearLayout.LayoutParams sdl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        sdl.leftMargin = px(24);
                        pitchRow.addView(side, sdl);
                        info.addView(pitchRow);
                        goals(events, g, 1);
                        readPlays();
                        tickClock();
                        h.main.postDelayed(scoreTick, 30_000L);
                    } else {
                        scorers(events, g);
                    }
                    if (ch != null) {
                        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
                        b.append(g.state == 0 ? "Watch live on  " : g.state == 1 ? "Watch on  " : "Was on  ");
                        int at = b.length();
                        b.append("  ");
                        pressOk(b);
                        watch.setText(b);
                        loadSpan(watch, b, at, ch.logo);
                        watchOk = () -> h.tune(ch.name, ch.logo);
                        matchChannel = ch.name;
                        matchLogo = ch.logo;
                    } else if (other != null) {
                        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
                        b.append(g.state == 0 ? "Watch live on  " : "Watch on  ");
                        int at = b.length();
                        b.append(other.logo != null ? "  " : other.name);
                        pressOk(b);
                        watch.setText(b);
                        if (other.logo != null) loadSpan(watch, b, at, other.logo);
                        watchOk = other.ok;
                        matchChannel = other.name;
                        matchLogo = other.logo;
                    } else {
                        watch.setText(g.state == 2 ? "Full time" : "Not on UK TV");
                    }
                    paint();
                });
            });
        }

        /** The six leagues' games under way, the Premier League's first (all today's, for the test hook). */
        private List<ScoresTicker.Game> liveGames() {
            List<ScoresTicker.Game> live = new ArrayList<>();
            for (ScoresTicker.Game g : ScoresTicker.load(LiveCentre.LEAGUES)) if (g.state == 0 || LiveCentre.testCard) live.add(g);
            live.sort((a, b) -> Integer.compare(leagueRank(a), leagueRank(b)));
            return live;
        }

        private static int leagueRank(ScoresTicker.Game g) {
            for (int i = 0; i < LiveCentre.LEAGUES.length; i++) if (LiveCentre.LEAGUES[i].startsWith(g.slug + "|")) return i;
            return LiveCentre.LEAGUES.length;
        }

        private LinearLayout liveRows;
        private TextView liveKicker;
        private final Runnable liveTick = this::readLive;

        /**
         * The Live Centre on the stage: LIVE NOW and how many games, every one under way (in two columns) with its
         * competition's logo, the score and the minute (read again every 30 seconds), and along the foot Press OK for
         * Live Centre (and F1 timing on a race weekend).
         */
        private void buildLive(LinearLayout c, List<ScoresTicker.Game> live) {
            if (!alive) return;
            c.removeAllViews();
            stageGround(0xE0263A, 0x243A9A);
            LinearLayout head = new LinearLayout(h.a);
            head.setGravity(Gravity.CENTER_VERTICAL);
            liveKicker = h.text(16, h.semi, 0xFFFF6B76);
            liveKicker.setLetterSpacing(0.16f);
            head.addView(liveKicker, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            c.addView(head);
            // Its name as its own stamp, large: LIVE on the green, CENTRE on white.
            ImageView title = new ImageView(h.a);
            title.setImageBitmap(LiveCentre.stampPicture(h.a, px(52)));
            title.setScaleType(ImageView.ScaleType.FIT_START);
            title.setAdjustViewBounds(true);
            title.setContentDescription("Live Centre");
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(52));
            tl.topMargin = px(8);
            c.addView(title, tl);
            liveRows = new LinearLayout(h.a);
            liveRows.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                LinearLayout col = new LinearLayout(h.a);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (k == 1) cl.leftMargin = px(48);
                liveRows.addView(col, cl);
            }
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            rl.topMargin = px(8);
            c.addView(liveRows, rl);
            // "Press [OK] for [LIVE|CENTRE]": this remote's OK button and the Live Centre's own stamp, no words for them.
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Press  ");
            int at = b.length();
            b.append("  ");
            int okSize = px(36);
            android.graphics.drawable.BitmapDrawable ok = new android.graphics.drawable.BitmapDrawable(h.a.getResources(),
                    okKey(Config.themeString(h.a, "ok_button", "tcl"), okSize));
            ok.setBounds(0, 0, okSize, okSize);
            b.setSpan(new MidSpan(ok), at, at + 2, 0);
            b.append("   for   ");
            int st = b.length();
            b.append("  ");
            android.graphics.Bitmap stampBm = LiveCentre.stampPicture(h.a, px(34));
            android.graphics.drawable.BitmapDrawable stamp = new android.graphics.drawable.BitmapDrawable(h.a.getResources(), stampBm);
            stamp.setBounds(0, 0, stampBm.getWidth(), stampBm.getHeight());
            b.setSpan(new MidSpan(stamp), st, st + 2, 0);
            watch.setText(b);
            // Its buttons: the Live Centre itself (the watch button now opens it), F1 timing when it shows.
            liveBtn.setVisibility(View.GONE);
            remindBtn.setVisibility(View.GONE);
            stageHint.setText("Every game under way, the score and the minute");
            c.addView(stageButtons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(60)));
            stageIdx = 0;
            watchOk = () -> LiveCentre.open(h.a, null);
            fillLive(live);
            h.main.postDelayed(liveTick, 30_000L);
            paint();
        }

        private void fillLive(List<ScoresTicker.Game> live) {
            int n = 0;
            for (ScoresTicker.Game g : live) if (g.state == 0) n++;
            liveKicker.setText("●  LIVE NOW · " + (LiveCentre.testCard && n == 0 ? live.size() : n) + (n == 1 ? " GAME" : " GAMES"));
            LinearLayout[] cols = {(LinearLayout) liveRows.getChildAt(0), (LinearLayout) liveRows.getChildAt(1)};
            for (LinearLayout col : cols) col.removeAllViews();
            int shown = 0;
            String slug = null;
            for (ScoresTicker.Game g : live) {
                if (shown >= 8) break;
                LinearLayout col = cols[shown < 4 ? 0 : 1];
                if (shown == 4) slug = null;
                LinearLayout r = new LinearLayout(h.a);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(0, px(10), 0, px(10));
                if (col.getChildCount() > 0 && !java.util.Objects.equals(g.slug, slug)) r.setPadding(0, px(16), 0, px(10));
                slug = g.slug;
                ImageView logo = new ImageView(h.a);
                logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (g.leagueLogo != null) h.image(logo, g.leagueLogo, px(56));
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(28), px(28));
                ll.rightMargin = px(14);
                r.addView(logo, ll);
                TextView names = h.text(21, h.regular, Color.WHITE);
                names.setText(short_(g.homeName != null ? g.homeName : g.home) + "  v  " + short_(g.awayName != null ? g.awayName : g.away));
                names.setSingleLine(true);
                names.setEllipsize(TextUtils.TruncateAt.END);
                r.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView score = h.text(23, h.semi, Color.WHITE);
                score.setText(g.state == 1 ? "v" : g.homeScore + "–" + g.awayScore);
                score.setGravity(Gravity.CENTER);
                r.addView(score, new LinearLayout.LayoutParams(px(76), ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView min = h.text(17, h.semi, g.state == 0 ? SPORT_GREEN : 0x99FFFFFF);
                min.setText(g.state == 2 ? "FT" : g.state == 1 ? DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(g.kickoff)) : g.halftime ? "HT" : g.detail == null ? "" : g.detail);
                min.setGravity(Gravity.END);
                r.addView(min, new LinearLayout.LayoutParams(px(70), ViewGroup.LayoutParams.WRAP_CONTENT));
                col.addView(r);
                View rule = new View(h.a);
                rule.setBackgroundColor(0x14FFFFFF);
                col.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1))));
                shown++;
            }
        }

        private void readLive() {
            h.main.removeCallbacks(liveTick);
            if (!alive || liveRows == null) return;
            playsIo.execute(() -> {
                List<ScoresTicker.Game> live = liveGames();
                h.main.post(() -> {
                    if (!alive) return;
                    if (!live.isEmpty()) fillLive(live);
                    h.main.postDelayed(liveTick, 30_000L);
                });
            });
        }

        /**
         * "Press [OK]" after the channel: the word, then this TV's own remote's OK button (the theme's ok_button),
         * drawn at the size it shows so it is sharp; it reads on the bar both focused (white) and not (dark glass).
         */
        private void pressOk(android.text.SpannableStringBuilder b) {
            b.append("    Press ");
            int at = b.length();
            b.append("  ");
            int size = px(36);
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(h.a.getResources(),
                    okKey(Config.themeString(h.a, "ok_button", "tcl"), size));
            d.setBounds(0, 0, size, size);
            b.setSpan(new MidSpan(d), at, at + 2, 0);
        }

        /**
         * A picture in a line of words, its middle level with the middle of the capitals ("Was on", "Press"), so the
         * words sit level with the channel's logo and the OK key; an ImageSpan centres on the font's whole line box,
         * which leaves the words looking low.
         */
        static final class MidSpan extends android.text.style.ReplacementSpan {
            private final android.graphics.drawable.Drawable d;
            private final android.graphics.Rect cap = new android.graphics.Rect();

            MidSpan(android.graphics.drawable.Drawable d) { this.d = d; }

            private float middle(android.graphics.Paint p) {
                p.getTextBounds("H", 0, 1, cap);
                return cap.top / 2f;   // above the baseline, so negative
            }

            @Override public int getSize(android.graphics.Paint p, CharSequence t, int start, int end, android.graphics.Paint.FontMetricsInt fm) {
                android.graphics.Rect r = d.getBounds();
                if (fm != null) {
                    android.graphics.Paint.FontMetricsInt pf = p.getFontMetricsInt();
                    int top = Math.round(middle(p) - r.height() / 2f), bottom = top + r.height();
                    fm.ascent = Math.min(pf.ascent, top);
                    fm.top = Math.min(pf.top, top);
                    fm.descent = Math.max(pf.descent, bottom);
                    fm.bottom = Math.max(pf.bottom, bottom);
                    fm.leading = pf.leading;
                }
                return r.width();
            }

            @Override public void draw(android.graphics.Canvas c, CharSequence t, int start, int end, float x, int top, int y, int bottom, android.graphics.Paint p) {
                c.save();
                c.translate(x, y + middle(p) - d.getBounds().height() / 2f);
                d.draw(c);
                c.restore();
            }
        }

        /**
         * The OK button as it looks on the remote, big enough to read in a line of text: the TCL's black button with
         * OK in white inside the thin grey ring of its direction keys; the Streamer's plain pale button.
         */
        static android.graphics.Bitmap okKey(String remote, int s) {
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bm);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            float r = s / 2f;
            boolean streamer = "streamer".equalsIgnoreCase(remote);
            if ("tcl".equalsIgnoreCase(remote)) {
                // The TCL remote's: the arrows' dark ring with its four marks, the round OK button in the middle.
                p.setColor(0xFF2A2C31);
                c.drawCircle(r, r, r, p);
                p.setColor(0xFF8A8E96);
                float m = r * 0.86f, tri = r * 0.10f;
                for (int i = 0; i < 4; i++) {
                    android.graphics.Path a = new android.graphics.Path();
                    float cx = r + (i == 1 ? m : i == 3 ? -m : 0), cy = r + (i == 2 ? m : i == 0 ? -m : 0);
                    float dx = i == 1 ? 1 : i == 3 ? -1 : 0, dy = i == 2 ? 1 : i == 0 ? -1 : 0;
                    a.moveTo(cx + dx * tri, cy + dy * tri);
                    a.lineTo(cx - dx * tri + dy * tri, cy - dy * tri + dx * tri);
                    a.lineTo(cx - dx * tri - dy * tri, cy - dy * tri - dx * tri);
                    a.close();
                    c.drawPath(a, p);
                }
                p.setColor(0xFF0B0C0E);
                c.drawCircle(r, r, r * 0.62f, p);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, r * 0.05f));
                p.setColor(0xFF4A4E56);
                c.drawCircle(r, r, r * 0.62f, p);
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(Color.WHITE);
                p.setTextAlign(android.graphics.Paint.Align.CENTER);
                p.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
                p.setTextSize(r * 0.50f);
                android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText("OK", r, r - (fm.ascent + fm.descent) / 2f, p);
                return bm;
            }
            p.setColor(streamer ? 0xFFBDB9B0 : 0xFF6B6E75);
            c.drawCircle(r, r, r, p);
            p.setColor(streamer ? 0xFFEDEAE4 : 0xFF0E0F11);
            c.drawCircle(r, r, r * 0.84f, p);
            if (!streamer) {
                p.setColor(Color.WHITE);
                p.setTextAlign(android.graphics.Paint.Align.CENTER);
                p.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
                p.setTextSize(r * 0.78f);
                android.graphics.Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText("OK", r, r - (fm.ascent + fm.descent) / 2f, p);
            }
            return bm;
        }

        /** Puts the channel's logo into the watch bar's text where the two spaces are. */
        private void loadSpan(TextView v, android.text.SpannableStringBuilder b, int at, String url) {
            ImageView probe = new ImageView(h.a);
            h.image(probe, url, px(200), () -> {
                android.graphics.drawable.Drawable d = probe.getDrawable();
                if (d == null) return;
                // On a dark chip: the bar turns white, and logos with white lettering ("main event") vanished on it.
                int hgt = px(22), w = Math.round(hgt * d.getIntrinsicWidth() / (float) Math.max(1, d.getIntrinsicHeight()));
                int padX = px(9), padY = px(5);
                GradientDrawable chip = new GradientDrawable();
                chip.setColor(0xFF15182E);
                chip.setCornerRadius(px(8));
                android.graphics.drawable.LayerDrawable on = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{chip, d});
                on.setLayerInset(1, padX, padY, padX, padY);
                on.setBounds(0, 0, w + 2 * padX, hgt + 2 * padY);
                b.setSpan(new MidSpan(on), at, at + 2, 0);
                v.setText(b);
            });
        }

        private static String short_(String club) {
            if (club == null) return "";
            return club.replace("Wolverhampton Wanderers", "Wolves").replace("Brighton & Hove Albion", "Brighton").replace("Tottenham Hotspur", "Spurs")
                    .replace("Manchester United", "Man United").replace("Manchester City", "Man City").replace("Nottingham Forest", "Nott'm Forest")
                    .replace("Newcastle United", "Newcastle").replace("West Ham United", "West Ham").replace("Sheffield United", "Sheffield Utd")
                    .replace("Wolverhampton", "Wolves").replace(" AFC", "").replace(" FC", "");
        }

        private ImageView crest() {
            ImageView v = new ImageView(h.a);
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return v;
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

        /** The tables Left and Right page through on the table card: {ESPN slug, name, lettering, ESPN logo id}. */
        private static final Object[][] LEAGUES = {
                {"eng.1", "Premier League", R.font.brand_premier, "23"}, {"eng.2", "Championship", R.font.brand_efl, "24"},
                {"esp.1", "LaLiga", R.font.brand_laliga, "15"}, {"ita.1", "Serie A", R.font.brand_seriea, "12"},
                {"ger.1", "Bundesliga", R.font.brand_bundesliga, "10"}, {"fra.1", "Ligue 1", R.font.brand_ligue1, "9"},
                {"sco.1", "Scottish Premiership", R.font.brand_plain, "45"}, {"uefa.champions", "Champions League", R.font.brand_uefa, "2"}};
        private final Map<String, List<String[]>> tables = new HashMap<>();
        /**
         * The tables panel beside the stage: its two tabs (the league, ‹ 1/8 › through the 8 of them; F1, ‹ Drivers ›
         * and ‹ Teams › the same way), which shows, its key line.
         */
        private LinearLayout panel, panelTabs, tableList;
        private GradientDrawable panelBg;
        private final TextView[] tabs = new TextView[2];
        private int panelTab;
        private TextView tableKey;
        private CharSequence tableKeyText = "";
        /** F1's standings after which round ("After Round 19 of 23"), for the head of its tabs. */
        private String f1Round = "";
        private int league, cardIdx;
        private LinearLayout tableHead;
        private android.widget.ScrollView tableView, driverView;
        private LinearLayout driverList;
        /** How far the table (standings) has been scrolled, in pixels: where the last press sent it. */
        private int tableTop, driverTop;

        /** The competitions' logos for the panel's tabs, by address, cut to their shape. */
        private final Map<String, android.graphics.Bitmap> compLogos = new HashMap<>();

        /**
         * A tab as a competition's logo (and after it the words given, "‹ 1/8 ›", "Drivers"): its name only as what the
         * screen reader says, and in words just until the logo has come (read off the main thread, kept on disk).
         */
        private void logoTab(TextView t, String url, String name, String after) {
            t.setTag(url);
            t.setContentDescription(after.isEmpty() ? name : name + ", " + after.replace("‹", "").replace("›", "").trim());
            android.graphics.Bitmap bm = compLogos.get(url);
            if (bm == null) {
                t.setText(after.isEmpty() ? name : name + "  " + after);
                h.io.execute(() -> {
                    android.graphics.Bitmap b = ScoresTicker.cachedLogo(h.a, url, px(96));
                    if (b == null) return;
                    android.graphics.Bitmap cut = HomeV2.trim(b);
                    h.main.post(() -> {
                        compLogos.put(url, cut);
                        if (url.equals(t.getTag())) logoTab(t, url, name, after);
                    });
                });
                return;
            }
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder("  ");
            android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(h.a.getResources(), bm);
            int hgt = px(26), w = Math.min(px(120), Math.round(hgt * bm.getWidth() / (float) Math.max(1, bm.getHeight())));
            if (w == px(120)) hgt = Math.round(w * bm.getHeight() / (float) Math.max(1, bm.getWidth()));
            d.setBounds(0, 0, w, hgt);
            sb.setSpan(new MidSpan(d), 0, 2, 0);
            if (!after.isEmpty()) sb.append("  ").append(after);
            t.setText(sb);
        }

        /** A list's window inside its card: scrolled from the keys only, no bar. */
        private android.widget.ScrollView scroller() {
            android.widget.ScrollView v = new android.widget.ScrollView(h.a);
            v.setVerticalScrollBarEnabled(false);
            v.setFocusable(false);
            v.setOverScrollMode(View.OVER_SCROLL_NEVER);
            return v;
        }

        private boolean scrollTable(int dir) {
            int to = scrollList(tableView, tableList, tableTop, dir);
            if (to < 0) return false;
            tableTop = to;
            return true;
        }

        private boolean scrollDrivers(int dir) {
            if (driverView == null) return false;
            int to = scrollList(driverView, driverList, driverTop, dir);
            if (to < 0) return false;
            driverTop = to;
            return true;
        }

        /**
         * The list a few rows further on (dir 1) or back (dir -1) from row 'top': the new first row, or -1 at its end,
         * so the key moves the focus on instead.
         */
        private int scrollList(android.widget.ScrollView view, LinearLayout list, int top, int dir) {
            // In pixels, a page at a time (all but a row of what shows, so the eye keeps its place): at the end, -1 at
            // once, so the next Down goes on to the rows below. Judged from where the list really is (row heights
            // differ, and a press that moved nothing once left the focus stuck on the card).
            if (list.getChildCount() == 0 || view.getHeight() == 0) return -1;
            int max = Math.max(0, list.getHeight() - view.getHeight());
            int row = Math.max(1, list.getChildAt(0).getHeight());
            int page = Math.max(row, view.getHeight() - row);
            int from = Math.max(0, Math.min(max, top));
            int to;
            if (dir > 0) {
                if (from >= max - 2) return -1;
                to = Math.min(max, from + page);
            } else {
                if (from <= 2) return -1;
                to = Math.max(0, from - page);
            }
            view.smoothScrollTo(0, to);
            return to;
        }

        /**
         * The tables panel beside the stage: tabs along its top (the league, with Left and Right through the 8 of them;
         * then F1's drivers and teams), under them the table (Down scrolls all 20), and the key to its colours.
         */
        private void buildTable() {
            LinearLayout c = new LinearLayout(h.a);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(px(22), px(20), px(22), px(16));
            panelBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x21FFFFFF, 0x0DFFFFFF});
            panelBg.setCornerRadius(px(30));
            panelBg.setStroke(px(1.5f), 0x3DFFFFFF);
            c.setBackground(panelBg);
            panel = c;
            addExact(cards, c, 1280, top, 592, 500);
            panelTabs = new LinearLayout(h.a);
            panelTabs.setOrientation(LinearLayout.HORIZONTAL);
            panelTabs.setGravity(Gravity.CENTER_VERTICAL);
            panelTabs.setPadding(px(4), px(4), px(4), px(4));
            panelTabs.setBackground(h.fill(23, 0x40000000));
            for (int i = 0; i < 2; i++) {
                TextView t = h.text(16, h.regular, 0xBFFFFFFF);
                t.setGravity(Gravity.CENTER);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(px(10), 0, px(10), 0);
                // The competitions by their own logos: F1's on its tab (Drivers, then Teams, with the arrows as the
                // leagues have), the league's on the first (showTable).
                if (i > 0) logoTab(t, F1Results.F1_LOGO, "F1", f1Arrows(1));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, px(38), 1f);
                if (i > 0) tl.leftMargin = px(4);
                panelTabs.addView(t, tl);
                tabs[i] = t;
            }
            c.addView(panelTabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(46)));
            tableHead = new LinearLayout(h.a);
            tableHead.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hl.topMargin = px(10);
            c.addView(tableHead, hl);
            // The whole table, every club, and F1's standings in the same place: each scrolls (Down and Up) in the panel.
            FrameLayout lists = new FrameLayout(h.a);
            c.addView(lists, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            tableView = scroller();
            tableList = new LinearLayout(h.a);
            tableList.setOrientation(LinearLayout.VERTICAL);
            tableView.addView(tableList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            lists.addView(tableView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            driverView = scroller();
            driverList = new LinearLayout(h.a);
            driverList.setOrientation(LinearLayout.VERTICAL);
            driverView.addView(driverList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            driverView.setVisibility(View.GONE);
            lists.addView(driverView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            tableKey = h.text(13, h.regular, 0xBFFFFFFF);
            tableKey.setSingleLine(true);
            tableKey.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kl.topMargin = px(8);
            c.addView(tableKey, kl);
            showTable(0);
        }

        /** The F1 tab's words after its logo: which of its two tables, between the arrows (as the leagues' "‹ 1/8 ›"). */
        private static String f1Arrows(int tab) {
            return "‹  " + (tab == 2 ? "Teams" : "Drivers") + "  ›";
        }

        /** The panel's tab: 0 the league's table, 1 F1's drivers, 2 F1's teams (both on the one F1 tab). */
        private void showPanel(int tab) {
            panelTab = tab;
            boolean f1 = tab > 0;
            if (f1) logoTab(tabs[1], F1Results.F1_LOGO, "F1", f1Arrows(tab));
            tableView.setVisibility(f1 ? View.GONE : View.VISIBLE);
            driverView.setVisibility(f1 ? View.VISIBLE : View.GONE);
            tableHead.removeAllViews();
            if (f1) {
                LinearLayout r = new LinearLayout(h.a);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(px(6), px(4), px(6), px(2));
                TextView a = h.text(13, h.semi, 0x8CFFFFFF);
                a.setLetterSpacing(0.14f);
                a.setText((tab == 1 ? "DRIVERS" : "TEAMS") + (f1Round.isEmpty() ? "" : "  ·  " + f1Round.toUpperCase(Locale.UK)));
                r.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                TextView b = h.text(13, h.semi, 0x8CFFFFFF);
                b.setLetterSpacing(0.14f);
                b.setText("PTS");
                r.addView(b);
                tableHead.addView(r);
                showF1(tab - 1);
                tableKey.setText("Down scrolls  ·  Left and Right: the 8 leagues, then F1");
            } else {
                tableHead.addView(tableRow(null, "", "CLUB", "P", "GD", "PTS", true, null));
                tableKey.setText(tableKeyText);
            }
            paint();
        }

        private void showTable(int i) {
            league = i;
            Object[] l = LEAGUES[i];
            // The league by its logo (its name only for the screen reader, or until the logo has come), then which of the 8.
            logoTab(tabs[0], "https://a.espncdn.com/i/leaguelogos/soccer/500-dark/" + l[3] + ".png", (String) l[1],
                    "‹  " + (i + 1) + "/" + LEAGUES.length + "  ›");
            if (panelTab != 0) showPanel(0);
            List<String[]> have = tables.get((String) l[0]);
            if (have != null) { fillTable(have); return; }
            tableList.animate().alpha(0.3f).setDuration(120).start();
            String slug = (String) l[0];
            h.io.execute(() -> {
                List<String[]> rows = new ArrayList<>();
                try {
                    JSONObject root = new JSONObject(HomeV2.get("https://site.api.espn.com/apis/v2/sports/soccer/" + slug + "/standings"));
                    JSONArray entries = root.getJSONArray("children").getJSONObject(0).getJSONObject("standings").getJSONArray("entries");
                    for (int n = 0; n < entries.length(); n++) {
                        JSONObject e = entries.getJSONObject(n);
                        JSONObject team = e.getJSONObject("team");
                        Map<String, String> st = new HashMap<>();
                        JSONArray stats = e.optJSONArray("stats");
                        for (int k = 0; stats != null && k < stats.length(); k++)
                            st.put(stats.getJSONObject(k).optString("name"), stats.getJSONObject(k).optString("displayValue"));
                        JSONArray tl = team.optJSONArray("logos");
                        // ESPN's note on the place: "Champions League", "Europa League", "Promotion", "Relegation"…
                        JSONObject note = e.optJSONObject("note");
                        rows.add(new String[]{st.getOrDefault("rank", ""), team.optString("displayName"), st.getOrDefault("gamesPlayed", ""),
                                st.getOrDefault("pointDifferential", ""), st.getOrDefault("points", ""),
                                tl != null && tl.length() > 0 ? tl.getJSONObject(0).optString("href") : null,
                                note == null ? null : note.optString("description", null)});
                    }
                    rows.sort((x, y) -> Integer.compare(num(x[0]), num(y[0])));
                } catch (Exception ignored) {
                }
                h.main.post(() -> {
                    if (!rows.isEmpty()) tables.put(slug, rows);
                    if (league == i) fillTable(rows);
                });
            });
        }

        private void fillTable(List<String[]> rows) {
            if (panelTab == 0) {
                tableHead.removeAllViews();
                tableHead.addView(tableRow(null, "", "CLUB", "P", "GD", "PTS", true, null));
            }
            tableList.removeAllViews();
            tableTop = 0;
            tableView.scrollTo(0, 0);
            for (int n = 0; n < rows.size(); n++) {
                String[] r = rows.get(n);
                tableList.addView(tableRow(r[5], r[0], short_(r[1]), r[2], r[3], r[4], false, r.length > 6 ? r[6] : null));
            }
            // The key to the colours, one entry for each kind of place the table has, in table order.
            android.text.SpannableStringBuilder key = new android.text.SpannableStringBuilder();
            java.util.Set<Integer> keyed = new java.util.HashSet<>();
            for (String[] r : rows) {
                String zone = r.length > 6 ? r[6] : null;
                int colour = zoneColour(zone);
                if (colour == 0 || !keyed.add(colour)) continue;
                if (key.length() > 0) key.append("     ");
                int at = key.length();
                key.append("●");
                key.setSpan(new android.text.style.ForegroundColorSpan(colour), at, key.length(), 0);
                key.append(" ").append(zoneName(zone));
            }
            tableKeyText = key;
            if (panelTab == 0) tableKey.setText(key);
            tableList.animate().alpha(1f).setDuration(160).start();
        }

        private static int num(String s) {
            try { return Integer.parseInt(s.replace("+", "").trim()); } catch (Exception e) { return 99; }
        }

        /**
         * A place's colour from ESPN's note on it: the Champions League blue, its qualifying lighter, the Europa
         * League orange, the Conference League green, promotion green (its play-offs teal), relegation red (its
         * play-off orange); the Champions League's own table: through to the last 16 green, the play-offs blue, out
         * red. 0 for a place with nothing riding on it.
         */
        static int zoneColour(String note) {
            if (note == null || note.isEmpty()) return 0;
            String n = note.toLowerCase(Locale.ROOT);
            boolean playoff = n.contains("playoff") || n.contains("play-off");
            if (n.contains("relegat")) return playoff ? 0xFFF97316 : 0xFFEF4444;
            if (n.contains("eliminated")) return 0xFFEF4444;
            if (n.contains("promotion")) return playoff ? 0xFF2DD4BF : 0xFF22C55E;
            if (n.contains("round of 16")) return 0xFF22C55E;
            if (n.contains("knockout")) return 0xFF60A5FA;
            if (n.contains("conference")) return 0xFF34D399;
            if (n.contains("europa")) return 0xFFF59E0B;
            if (n.contains("champions league")) return n.contains("qualif") ? 0xFF93C5FD : 0xFF3B82F6;
            return 0xFF9CA3AF;
        }

        /** The key's words for a place: ESPN's, without the seeding ("Knockout phase playoffs - seeded"). */
        static String zoneName(String note) {
            int dash = note.indexOf(" - ");
            String n = dash > 0 ? note.substring(0, dash) : note;
            if (n.equalsIgnoreCase("Qualifies for round of 16")) return "Round of 16";
            return title(n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1).toLowerCase(Locale.ROOT));
        }

        private View tableRow(String logo, String pos, String club, String p, String gd, String pts, boolean head, String zone) {
            LinearLayout r = new LinearLayout(h.a);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            // The place's colour: a bar at the row's start (an empty one keeps the columns lined up).
            int colour = head ? 0 : zoneColour(zone);
            View bar = new View(h.a);
            if (colour != 0) bar.setBackground(h.fill(2, colour));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(4), px(head ? 12 : 20));
            bl.rightMargin = px(8);
            r.addView(bar, bl);
            int size = head ? 13 : 17;
            int col = head ? 0x8CFFFFFF : 0xBFFFFFFF;
            TextView a = h.text(size, h.regular, 0x99FFFFFF);
            a.setText(pos);
            r.addView(a, new LinearLayout.LayoutParams(px(30), ViewGroup.LayoutParams.WRAP_CONTENT));
            if (!head) {
                ImageView l = new ImageView(h.a);
                l.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(22), px(22));
                ll.rightMargin = px(10);
                r.addView(l, ll);
                if (logo != null) h.image(l, logo, px(44));
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
                r.addView(t, new LinearLayout.LayoutParams(px(48), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            r.setPadding(px(6), px(head ? 4 : 5), px(6), px(head ? 2 : 5));
            if (!head) {
                GradientDrawable g = new GradientDrawable();
                // A wash of the place's colour across the row, so the zones read at a glance.
                g.setColor(colour != 0 ? (colour & 0x00FFFFFF) | 0x1F000000 : 0x00000000);
                g.setCornerRadius(px(10));
                r.setBackground(g);
            }
            return r;
        }

        /** F1's drivers' and teams' standings, for the panel's F1 tabs (and the timing button on a race weekend). */
        private void buildF1() {
            h.io.execute(() -> {
                List<String[]> rows = new ArrayList<>(), teams = new ArrayList<>();
                String round = "", rounds = "";
                try {
                    JSONObject root = new JSONObject(HomeV2.get("https://api.jolpi.ca/ergast/f1/current/driverStandings.json?limit=100"));
                    JSONObject l = root.getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0);
                    round = l.optString("round");
                    JSONArray st = l.getJSONArray("DriverStandings");
                    for (int i = 0; i < st.length(); i++) {
                        JSONObject x = st.getJSONObject(i);
                        JSONObject dr = x.getJSONObject("Driver");
                        JSONArray cons = x.getJSONArray("Constructors");
                        String team = cons.getJSONObject(cons.length() - 1).optString("constructorId");
                        rows.add(new String[]{x.optString("position"), dr.optString("givenName").substring(0, 1) + ". " + dr.optString("familyName"),
                                x.optString("points"), team});
                    }
                } catch (Exception ignored) {
                }
                // Then the constructors' championship, under the drivers.
                try {
                    JSONObject root = new JSONObject(HomeV2.get("https://api.jolpi.ca/ergast/f1/current/constructorStandings.json?limit=100"));
                    JSONArray st = root.getJSONObject("MRData").getJSONObject("StandingsTable").getJSONArray("StandingsLists").getJSONObject(0)
                            .getJSONArray("ConstructorStandings");
                    for (int i = 0; i < st.length(); i++) {
                        JSONObject x = st.getJSONObject(i), team = x.getJSONObject("Constructor");
                        teams.add(new String[]{x.optString("position"), team.optString("name"), x.optString("points"), team.optString("constructorId")});
                    }
                } catch (Exception ignored) {
                }
                // How many rounds the season has: the calendar's length.
                try {
                    rounds = new JSONObject(HomeV2.get("https://api.jolpi.ca/ergast/f1/current.json?limit=100")).getJSONObject("MRData").optString("total");
                } catch (Exception ignored) {
                }
                String r = round, of = rounds;
                String[] timing = f1Timing();
                h.main.post(() -> {
                    if (!alive) return;
                    if (timing != null) timingBar(timing);
                    if (!r.isEmpty()) f1Round = "After Round " + r + (of.isEmpty() || "0".equals(of) ? "" : " of " + of);
                    f1Drivers = rows;
                    f1Teams = teams;
                    if (panelTab > 0) showPanel(panelTab);
                });
            });
        }

        /** On a race weekend, from first practice to two hours after the race: OK on the F1 card opens the timing. */
        private boolean f1TimingOn;

        /** {what's on, the line under it} while a race weekend's timing is worth opening; null otherwise. */
        private static String[] f1Timing() {
            F1Results.Weekend w = F1Results.weekend(null);
            if (w == null || w.sessions.isEmpty()) return null;
            long now = System.currentTimeMillis();
            F1Results.Session first = w.sessions.get(0), last = w.sessions.get(w.sessions.size() - 1);
            if (now < first.start - 3 * 3_600_000L || now > last.start + 5 * 3_600_000L) return null;
            for (F1Results.Session s : w.sessions) {
                if (!"in".equals(s.state)) continue;
                String what = F1Results.label(s.abbr);
                return new String[]{what + " LIVE", s.isRace() && s.lap > 0 ? "Lap " + s.lap + " · live timing over any app" : "Live timing over any app"};
            }
            for (F1Results.Session s : w.sessions) {
                if (!"pre".equals(s.state)) continue;
                String at = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(s.start));
                return new String[]{s.isRace() ? "Starting grid & timing" : "F1 timing", F1Results.label(s.abbr).charAt(0)
                        + F1Results.label(s.abbr).substring(1).toLowerCase(Locale.UK) + " " + at + " · over any app"};
            }
            return new String[]{"F1 timing", "Final classification · over any app"};
        }

        /** A race weekend: the stage's F1 timing button shows (OK opens the timing tower over Sky Sports F1). */
        private void timingBar(String[] t) {
            f1TimingOn = true;
            boolean live = t[0].endsWith("LIVE");
            String label = t[0].charAt(0) + t[0].substring(1).toLowerCase(Locale.UK).replace("f1", "F1");
            timingBtn.setText((live ? "●  " : "") + label);
            timingBtn.setVisibility(View.VISIBLE);
            paint();
        }

        private static final int SPORT_GREEN = 0xFF3DDC84;

        private List<String[]> f1Drivers = new ArrayList<>(), f1Teams = new ArrayList<>();
        /** 0 the drivers' standings, 1 the constructors'. */
        private int f1Page;

        private void showF1(int p) {
            f1Page = p;
            driverList.removeAllViews();
            for (String[] x : p == 0 ? f1Drivers : f1Teams) driverList.addView(driver(x));
            driverView.scrollTo(0, 0);
            driverTop = 0;
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
            r.setPadding(px(6), px(5), px(6), px(5));
            TextView p = h.text(17, h.regular, 0x99FFFFFF);
            p.setText(x[0]);
            r.addView(p, new LinearLayout.LayoutParams(px(30), ViewGroup.LayoutParams.WRAP_CONTENT));
            View bar = new View(h.a);
            bar.setBackground(h.fill(3, teamColour(x[3])));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(5), px(20));
            bl.rightMargin = px(12);
            r.addView(bar, bl);
            ImageView team = new ImageView(h.a);
            team.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(24), px(24));
            tl.rightMargin = px(12);
            r.addView(team, tl);
            String logo = F1Results.logo(x[3]);
            if (logo != null) h.image(team, logo, px(56));
            TextView n = h.text(17, h.regular, Color.WHITE);
            n.setText(x[1]);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView pts = h.text(17, h.semi, Color.WHITE);
            pts.setText(x[2]);
            r.addView(pts);
            return r;
        }

        /** A part-watched match or race: the app's picture for it (else the app's banner), its name, how far in. */
        private View resumeTile(WatchHistory.Entry e) {
            FrameLayout f = new FrameLayout(h.a);
            f.setClipToOutline(true);
            f.setOutlineProvider(h.round(16));
            f.setBackground(h.fill(16, 0xFF15182E));
            ImageView pic = new ImageView(h.a);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(pic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (e.art != null) h.image(pic, e.art, px(384));
            else {
                try {
                    android.content.pm.PackageManager pm = h.a.getPackageManager();
                    android.graphics.drawable.Drawable b = pm.getApplicationBanner(e.pkg);
                    pic.setImageDrawable(b != null ? b : pm.getApplicationIcon(e.pkg));
                    if (b == null) pic.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                } catch (Exception ignored) {
                }
            }
            View shade = new View(h.a);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xE6000000}));
            f.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            LinearLayout cap = new LinearLayout(h.a);
            cap.setOrientation(LinearLayout.VERTICAL);
            TextView n = h.text(22, h.semi, Color.WHITE);
            n.setText(e.title);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            cap.addView(n);
            TextView left = h.text(16, h.regular, 0xD9FFFFFF);
            long mins = Math.max(1, (e.dur - e.pos) / 60_000L);
            left.setText(mins + " min left");
            cap.addView(left);
            FrameLayout.LayoutParams capl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            capl.leftMargin = px(16);
            capl.rightMargin = px(14);
            capl.bottomMargin = px(24);
            f.addView(cap, capl);
            // How far in: the same red bar as on the posters.
            FrameLayout bar = new FrameLayout(h.a);
            bar.setBackgroundColor(0x80000000);
            bar.setClipToOutline(true);
            bar.setOutlineProvider(h.round(3));
            View done = new View(h.a);
            done.setBackgroundColor(0xFFE50914);
            bar.addView(done, new FrameLayout.LayoutParams(Math.round((px(384) - px(32)) * e.progress()), ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(6), Gravity.BOTTOM);
            bl.leftMargin = bl.rightMargin = px(16);
            bl.bottomMargin = px(10);
            f.addView(bar, bl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(384), px(216));
            lp.rightMargin = px(24);
            f.setLayoutParams(lp);
            if (h.lite) f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            return f;
        }

        private View fixture(HomeV2.Tile t) {
            FrameLayout f = new FrameLayout(h.a);
            if (h.lite) f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
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
            // With the channel's logo where it has one; its name where not (a playlist's own event channel).
            lo.setText(t.badge == null && t.channel != null ? "Live on " + t.channel : "Live on ");
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
            f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            return f;
        }

        private boolean held;

        private void remind(HomeV2.Tile t) {
            String msg;
            if (t.start <= System.currentTimeMillis()) msg = "Already on: watch it now";
            else {
                boolean on = Reminders.toggle(h.a, t.title, t.start, t.channel, t.badge);
                String when = java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", java.util.Locale.UK)
                        .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(t.start));
                msg = on ? "🔔  Reminder set · " + t.title + " · " + when : "Reminder off · " + t.title;
            }
            android.widget.Toast.makeText(h.a, msg, android.widget.Toast.LENGTH_SHORT).show();
        }

        /**
         * What the last paint left on screen, so a key press changes only what it moves: before, every press restarted
         * an animation on every tile of every row (well over a hundred), the counts' text and a screen-sized layer for
         * the stage, and the scroll stuttered.
         */
        /** Tonight's cards' grounds: {resting glass, focused solid}. */
        private final Map<View, android.graphics.drawable.Drawable[]> slimGrounds = new java.util.WeakHashMap<>();
        private Boolean paintedRows;
        private float paintedFy = Float.NaN;
        private int paintedAbove = -1, paintedRowCount = -1, paintedCaptionZone = Integer.MIN_VALUE, paintedTabs = -1;
        private View paintedTile;
        private final Map<LinearLayout, int[]> paintedStrip = new HashMap<>();
        private String paintedCounts;

        private void paint() {
            if (stage == null || panel == null || cards == null) return;
            // Down in the rows below Tonight: the stage, the tables and Tonight go up and out, the rows come up.
            boolean rows = zone >= 0 && zone != TONIGHT;
            if (paintedRows == null || paintedRows != rows) {
                paintedRows = rows;
                cards.animate().alpha(rows ? 0f : 1f).translationY(rows ? -px(80) : 0).setDuration(260).setInterpolator(HomeV2.SKY).withLayer().start();
            }
            int slot = rows && zone < slotOf.size() ? Math.max(0, slotOf.get(zone)) : 0;
            // Two rows in sight at once: the focused one second from the top (the first at the top), those above gone.
            int above = rows ? Math.max(0, slot - 1) : 0;
            float fy = rows ? px(top - 236) - px(300) * above : px(restY());
            if (fy != paintedFy) {
                paintedFy = fy;
                // Only the rows' frame moves (a translation: nothing is laid out again), drawn as it is.
                fixtures.animate().translationY(fy).setDuration(300).setInterpolator(HomeV2.SKY).start();
            }
            if (above != paintedAbove || rowViews.size() != paintedRowCount) {
                paintedAbove = above;
                paintedRowCount = rowViews.size();
                for (int i = 0; i < rowViews.size(); i++) {
                    float a = rows && i / 2 < above ? 0f : 1f;
                    if (rowViews.get(i).getAlpha() != a) rowViews.get(i).animate().alpha(a).setDuration(200).start();
                }
                float ra = rows && above > 0 ? 0f : 1f;
                if (resumeNone.getAlpha() != ra) resumeNone.animate().alpha(ra).setDuration(200).start();
            }
            // The dock's chip at the foot keeps out of the thumbnails' way while the focus is in the rows; it comes back
            // on the last row (where Down brings the dock) and with the stage, the tables or Tonight.
            h.dockHandleHint(!rows || nextZone(zone) < 0);
            // The stage: a white rim with the focus on it, the focused button white with dark words.
            boolean onStage = zone == -1 && cardIdx == 0, onPanel = zone == -1 && cardIdx == 1;
            if (stageBg != null) {
                stageBg.setStroke(px(onStage ? 3 : 1.5f), onStage ? Color.WHITE : 0x3DFFFFFF);
                stage.invalidate();
            }
            List<TextView> shown = shownButtons();
            stageIdx = Math.max(0, Math.min(stageIdx, shown.size() - 1));
            for (TextView b : stageBtns) {
                boolean on = onStage && !shown.isEmpty() && shown.get(stageIdx) == b;
                boolean main = b == watch;
                GradientDrawable g = h.fill(30, on ? 0xEBFFFFFF : main ? 0x33FFFFFF : 0x40000000);
                g.setStroke(px(1.5f), on ? Color.WHITE : b == timingBtn ? 0xCCE10600 : main ? 0x80FFFFFF : 0x73FFFFFF);
                b.setBackground(g);
                b.setTextColor(on ? 0xFF1A1D24 : Color.WHITE);
                b.animate().scaleX(on ? 1.04f : 1f).scaleY(on ? 1.04f : 1f).setDuration(150).setInterpolator(HomeV2.SKY).start();
            }
            // The tables panel: its rim white with the focus, the tab showing lit (a white rim with the focus on the panel:
            // the competitions' logos are for dark grounds, so the tab stays dark under them).
            int tabsState = panelTab * 2 + (onPanel ? 1 : 0);
            if (tabsState != paintedTabs) {
                paintedTabs = tabsState;
                panelBg.setStroke(px(onPanel ? 3 : 1.5f), onPanel ? Color.WHITE : 0x3DFFFFFF);
                panel.invalidate();
                for (int i = 0; i < tabs.length; i++) {
                    boolean shownTab = i == Math.min(1, panelTab);
                    GradientDrawable tg = shownTab ? h.fill(19, onPanel ? 0x47FFFFFF : 0x2EFFFFFF) : null;
                    if (tg != null && onPanel) tg.setStroke(px(2), Color.WHITE);
                    tabs[i].setBackground(tg);
                    tabs[i].setTextColor(shownTab ? Color.WHITE : 0xBFFFFFFF);
                    tabs[i].setTypeface(shownTab ? h.semi : h.regular);
                    tabs[i].setAlpha(shownTab ? 1f : 0.7f);
                }
            }
            // Tonight's counts: how many games and events are on now and this week (the two rows of live sport).
            int liveNow = 0, coming = 0;
            for (int r = 1; r < Math.min(LIVE_ROWS, lists.size()); r++) {
                for (HomeV2.Tile t : lists.get(r)) if ("LIVE NOW".equals(t.chip)) liveNow++;
                coming += lists.get(r).size();
            }
            if (tonightCounts != null) {
                android.text.SpannableStringBuilder cb = new android.text.SpannableStringBuilder();
                if (liveNow > 0) {
                    cb.append("●");
                    cb.setSpan(new android.text.style.ForegroundColorSpan(0xFFFF3B4A), 0, 1, 0);
                    cb.append("  ").append(String.valueOf(liveNow)).append(" live now  ·  ");
                }
                cb.append(coming > 0 ? coming + " this week  ·  hold OK for a reminder" : "Live football and more");
                // Set only when the counts change: a new text is a new layout of the page. OK as the remote's own key.
                if (!cb.toString().equals(paintedCounts)) {
                    paintedCounts = cb.toString();
                    tonightCounts.setText(Keys.icons(h.a, cb, tonightCounts.getTextSize(), true));
                }
            }
            if (tonightNone != null && !lists.get(TONIGHT).isEmpty()) tonightNone.setVisibility(View.GONE);
            captions();
            // The focus: only the tile that has it now and the one that had it change their look.
            View now = zone >= 0 && zone < strips.size() && idx < strips.get(zone).getChildCount() ? strips.get(zone).getChildAt(idx) : null;
            if (now != paintedTile) {
                if (paintedTile != null) {
                    paintedTile.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(HomeV2.SKY).start();
                    paintedTile.setForeground(null);
                    paintedTile.setElevation(0);
                    android.graphics.drawable.Drawable[] gr = slimGrounds.get(paintedTile);
                    if (gr != null) paintedTile.setBackground(gr[0]);
                }
                if (now != null) {
                    boolean slim = zone == TONIGHT;
                    float grow = slim ? 1.05f : 1.07f;
                    now.animate().scaleX(grow).scaleY(grow).setDuration(150).setInterpolator(HomeV2.SKY).start();
                    // Tonight's card: its solid focused ground under the ring.
                    android.graphics.drawable.Drawable[] gr = slimGrounds.get(now);
                    if (gr != null) now.setBackground(gr[1]);
                    // No raised shadow under it (it showed through the glass cards and was cut at the strip's edge
                    // as the row slid along): the ring and the grow are the focus.
                    now.setForeground(h.ring(slim ? 20 : 16));
                }
                paintedTile = now;
            }
            for (int r = 0; r < strips.size(); r++) {
                LinearLayout s = strips.get(r);
                boolean slim = r == TONIGHT;
                // Tonight shows six at once, a row of tiles four: the strip slides along under the focus.
                // A row of highlights keeps the focused card whole with one either side where there is one, the last
                // ones inside at the row's end (never cut at its edge); the other rows as before.
                int first = zone != r ? 0 : captions.containsKey(r) ? Math.max(0, Math.min(idx - 2, s.getChildCount() - 4))
                        : Math.max(0, idx - (slim ? 4 : 3));
                // A strip slides (and shows its tiles near the window) only when its place changes or it was filled again.
                int[] was = paintedStrip.get(s);
                if (was == null || was[0] != first || was[1] != s.getChildCount()) {
                    paintedStrip.put(s, new int[]{first, s.getChildCount()});
                    HomeV2.window(s, first);
                    s.animate().translationX(-first * px(slim ? SLIM_W + SLIM_GAP : 408)).setDuration(300).setInterpolator(HomeV2.SKY).start();
                }
            }
        }

        /** Down or Up on the tables panel scrolls the table (or F1's standings) shown; false at its end. */
        private boolean scrollPanel(int dir) {
            return panelTab == 0 ? scrollTable(dir) : scrollDrivers(dir);
        }

        @Override boolean key(KeyEvent e) {
            // A highlight playing large takes every key.
            if (player != null && player.isShowing()) return player.key(e);
            int code = e.getKeyCode();
            if (ok(code)) {
                // Held on one of this week's games: a reminder for it (held again: taken off). A plain press watches it.
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 1 && zone >= 1 && zone < LIVE_ROWS && idx < lists.get(zone).size()) {
                    held = true;
                    remind(lists.get(zone).get(idx));
                    return true;
                }
                if (e.getAction() == KeyEvent.ACTION_UP) {
                    if (held) { held = false; return true; }
                    if (zone == -1) {
                        if (cardIdx == 0) pressStage();
                        else if (panelTab > 0 && f1TimingOn) F1Tower.open(h.a, true);
                        else { int r = nextZone(-1); if (r >= 0) { zone = r; idx = 0; paint(); } }
                    }
                    else if (idx < lists.get(zone).size() && lists.get(zone).get(idx).ok != null) lists.get(zone).get(idx).ok.run();
                }
                return true;
            }
            if (e.getAction() != KeyEvent.ACTION_DOWN) return code != KeyEvent.KEYCODE_BACK;
            switch (code) {
                // At an edge the key is Home's (false): on to the top bar or the dock, no Back needed.
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    // On the tables panel Down scrolls the table, a page at a time; at its end, on down.
                    if (zone == -1 && cardIdx == 1 && scrollPanel(1)) return true;
                    // Down: Tonight, then the rows, past any with nothing in them; from the last, on to the dock.
                    int next = nextZone(zone);
                    if (next < 0) return false;
                    zone = next;
                    idx = 0;
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (zone == -1 && cardIdx == 1 && scrollPanel(-1)) return true;
                    if (zone < 0) return false;
                    zone = prevZone(zone);
                    idx = 0;
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (zone >= 0) { if (idx > 0) idx--; else return false; }
                    else if (cardIdx == 0) { if (stageIdx > 0) stageIdx--; else return false; }
                    // On the panel: back through F1's teams and drivers to the leagues, back through them, then to the stage.
                    else if (panelTab == 2) showPanel(1);
                    else if (panelTab == 1) showPanel(0);
                    else if (league > 0) showTable(league - 1);
                    else { cardIdx = 0; stageIdx = Math.max(0, shownButtons().size() - 1); }
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (zone >= 0) { if (idx < lists.get(zone).size() - 1) idx++; }
                    // Along the stage's buttons, then into the tables panel.
                    else if (cardIdx == 0) { if (stageIdx < shownButtons().size() - 1) stageIdx++; else cardIdx = 1; }
                    // On the panel: on through the 8 leagues, then F1's drivers and teams.
                    else if (panelTab == 0) { if (league < LEAGUES.length - 1) showTable(league + 1); else showPanel(1); }
                    else if (panelTab == 1 && !f1Teams.isEmpty()) showPanel(2);
                    break;
                default:
                    return false;
            }
            paint();
            return true;
        }
    }
}
