package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.util.LruCache;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;

/**
 * Today's football on the greeting's line: one glass box per game, from ESPN's public scoreboards for the leagues
 * in config.xml score_leagues ("eng.1|PL"). As many boxes as fit make a page; with more than one page, each stays
 * PAGE_MS and then flips up to the next. Scores refresh every minute while a game is on, every 10 minutes
 * otherwise, and nothing runs while Home is not in front. Hidden on a day without games.
 */
final class ScoresTicker {
    private static final long LIVE_MS = 60_000L;
    private static final long IDLE_MS = 10 * 60_000L;
    private static final long PAGE_MS = 8_000L;
    private static final long FLIP_MS = 450L;
    private static final String FEED = "https://site.api.espn.com/apis/site/v2/sports/soccer/%s/scoreboard?dates=%s";

    /** One game, ready to draw. */
    static final class Game {
        String league, home, away, homeScore, awayScore, detail;
        /** Logo URLs: the competition (its dark-background version) and both crests; null when ESPN has none. */
        String leagueLogo, homeLogo, awayLogo;
        int state;   // 0 live, 1 upcoming, 2 finished: the order they are shown in
        long kickoff;
    }

    private final Context context;
    private final float density;
    private final FrameLayout box;
    private final String[] leagues;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Game> games = new ArrayList<>();
    private final List<List<Game>> pages = new ArrayList<>();
    private int page;
    private LinearLayout shownRow;
    private boolean running;
    private final Runnable refresh = this::fetch;
    private final Runnable flip = this::nextPage;
    /** Logos at LOGO px, in memory and on disk (they change once a season, not once a minute). */
    private final LruCache<String, Bitmap> logos = new LruCache<>(80);
    private final ExecutorService logoIo = Executors.newFixedThreadPool(2);
    private final Map<String, List<ImageView>> waiting = new HashMap<>();
    private final File logoDir;
    private final int logo;

    ScoresTicker(Context context, FrameLayout root, float density, String[] leagues) {
        this.context = context;
        this.density = density;
        this.leagues = leagues;
        logo = dp(20);
        logoDir = new File(context.getCacheDir(), "logos");
        box = new FrameLayout(context);
        box.setClipChildren(true);
        box.setVisibility(View.GONE);
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(box, new FrameLayout.LayoutParams(0, dp(30), Gravity.TOP | Gravity.LEFT));
        measure.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f,
                context.getResources().getDisplayMetrics()));
        measure.setTypeface(Typeface.DEFAULT_BOLD);   // measure as if all bold: the widest case
    }

    /** Puts the boxes at (x, centreY) in root coordinates, up to right; called once the greeting is laid out. */
    void place(int x, int centreY, int right) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        int w = Math.max(0, right - x);
        if (lp.width == w && lp.leftMargin == x && lp.topMargin == centreY - lp.height / 2) return;
        lp.width = w;
        lp.leftMargin = x;
        lp.topMargin = centreY - lp.height / 2;
        box.setLayoutParams(lp);
        paginate();
    }

    void start() {
        if (leagues.length == 0) return;
        running = true;
        main.removeCallbacks(refresh);
        fetch();
        schedulePage();
    }

    void stop() {
        running = false;
        main.removeCallbacks(refresh);
        main.removeCallbacks(flip);
    }

    void close() {
        stop();
        io.shutdownNow();
        logoIo.shutdownNow();
    }

    // ---- data ------------------------------------------------------------------------------------

    private void fetch() {
        main.removeCallbacks(refresh);
        io.execute(() -> {
            ZoneId zone = ZoneId.systemDefault();
            String date = LocalDate.now(zone).format(DateTimeFormatter.BASIC_ISO_DATE);
            List<Game> found = new ArrayList<>();
            for (String entry : leagues) {
                String[] parts = entry.split("\\|", 2);
                String slug = parts[0].trim();
                String tag = parts.length > 1 ? parts[1].trim() : slug;
                try {
                    JSONObject board = new JSONObject(get(String.format(FEED, slug, date)));
                    String leagueLogo = leagueLogo(board);
                    JSONArray events = board.optJSONArray("events");
                    for (int i = 0; events != null && i < events.length(); i++) {
                        Game g = parse(events.getJSONObject(i), tag, zone);
                        if (g == null) continue;
                        g.leagueLogo = leagueLogo;
                        found.add(g);
                    }
                } catch (Exception ignored) {
                    // one league failing leaves the others
                }
            }
            found.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state)
                    : a.state == 2 ? Long.compare(b.kickoff, a.kickoff) : Long.compare(a.kickoff, b.kickoff));
            boolean live = false;
            for (Game g : found) live |= g.state == 0;
            final boolean anyLive = live;
            main.post(() -> {
                games = found;
                paginate();
                if (running) main.postDelayed(refresh, anyLive ? LIVE_MS : IDLE_MS);
            });
        });
    }

    private static Game parse(JSONObject e, String tag, ZoneId zone) throws Exception {
        JSONObject c = e.getJSONArray("competitions").getJSONObject(0);
        JSONObject type = c.getJSONObject("status").getJSONObject("type");
        JSONObject home = null, away = null;
        JSONArray teams = c.getJSONArray("competitors");
        for (int t = 0; t < teams.length(); t++) {
            JSONObject team = teams.getJSONObject(t);
            if ("home".equals(team.optString("homeAway"))) home = team; else away = team;
        }
        if (home == null || away == null) return null;
        Game g = new Game();
        g.league = tag;
        g.home = home.getJSONObject("team").optString("abbreviation");
        g.away = away.getJSONObject("team").optString("abbreviation");
        g.homeLogo = home.getJSONObject("team").optString("logo", null);
        g.awayLogo = away.getJSONObject("team").optString("logo", null);
        g.homeScore = home.optString("score", "0");
        g.awayScore = away.optString("score", "0");
        // ESPN writes "2026-10-25T14:00Z", which Instant.parse refuses (no seconds); OffsetDateTime accepts it.
        ZonedDateTime when = OffsetDateTime.parse(e.getString("date")).toInstant().atZone(zone);
        g.kickoff = when.toInstant().toEpochMilli();
        String state = type.optString("state");
        if ("in".equals(state)) {
            g.state = 0;
            g.detail = type.optString("shortDetail", "LIVE");
        } else if ("pre".equals(state)) {
            g.state = 1;
            g.detail = when.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            g.state = 2;
            g.detail = type.optString("shortDetail", "FT");
        }
        return g;
    }

    /** The competition's logo for dark backgrounds, else its default one. */
    private static String leagueLogo(JSONObject board) {
        JSONArray leagues = board.optJSONArray("leagues");
        JSONArray list = leagues == null || leagues.length() == 0 ? null : leagues.optJSONObject(0).optJSONArray("logos");
        String fallback = null;
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject l = list.optJSONObject(i);
            String rel = String.valueOf(l.optJSONArray("rel"));
            if (rel.contains("dark")) return l.optString("href", null);
            if (fallback == null) fallback = l.optString("href", null);
        }
        return fallback;
    }

    // ---- pages -----------------------------------------------------------------------------------

    /** Splits the games into pages of as many boxes as fit the width, and shows the current one. */
    private void paginate() {
        pages.clear();
        int width = box.getLayoutParams().width;
        if (games.isEmpty() || width <= 0) {
            box.setVisibility(View.GONE);
            box.removeAllViews();
            shownRow = null;
            return;
        }
        int gap = dp(10);
        List<Game> current = new ArrayList<>();
        int used = 0;
        for (Game g : games) {
            int w = boxWidth(g);
            if (!current.isEmpty() && used + gap + w > width) {
                pages.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            used += (current.isEmpty() ? 0 : gap) + w;
            current.add(g);
        }
        if (!current.isEmpty()) pages.add(current);
        if (page >= pages.size()) page = 0;
        box.setVisibility(View.VISIBLE);
        box.removeAllViews();
        shownRow = row(pages.get(page));
        box.addView(shownRow);
    }

    private void schedulePage() {
        main.removeCallbacks(flip);
        if (running) main.postDelayed(flip, PAGE_MS);
    }

    /** The current page slides up and out while the next one rises in from below. */
    private void nextPage() {
        if (running && pages.size() > 1 && shownRow != null) {
            page = (page + 1) % pages.size();
            LinearLayout out = shownRow;
            LinearLayout in = row(pages.get(page));
            int h = box.getHeight() > 0 ? box.getHeight() : dp(30);
            in.setTranslationY(h);
            in.setAlpha(0f);
            box.addView(in);
            out.animate().translationY(-h).alpha(0f).setDuration(FLIP_MS)
                    .withEndAction(() -> box.removeView(out)).start();
            in.animate().translationY(0f).alpha(1f).setDuration(FLIP_MS).start();
            shownRow = in;
        }
        schedulePage();
    }

    private LinearLayout row(List<Game> list) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < list.size(); i++) {
            Game g = list.get(i);
            LinearLayout b = new LinearLayout(context);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setBackgroundResource(R.drawable.glass_box);
            b.setPadding(dp(12), 0, dp(12), 0);
            if (g.leagueLogo != null) b.addView(logoView(g.leagueLogo, 0));
            else b.addView(label(league(g)));
            b.addView(logoView(g.homeLogo, dp(12)));
            b.addView(label(teams(g)), margin(dp(6)));
            b.addView(logoView(g.awayLogo, dp(6)));
            b.addView(label(detail(g)), margin(dp(8)));
            // Each box grows in proportion to what it holds, so a page always fills the whole line.
            b.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(30), boxWidth(g));
            if (i > 0) lp.leftMargin = dp(10);
            row.addView(b, lp);
        }
        row.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
        return row;
    }

    private TextView label(CharSequence text) {
        TextView t = new TextView(context);
        t.setSingleLine(true);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        t.setIncludeFontPadding(false);
        t.setText(text);
        return t;
    }

    private static LinearLayout.LayoutParams margin(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = left;
        return lp;
    }

    private ImageView logoView(String url, int left) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(logo, logo);
        lp.leftMargin = left;
        v.setLayoutParams(lp);
        if (url != null) loadLogo(url, v);
        return v;
    }

    /** Width of one game's box: its texts, three logos and their gaps, and the padding. */
    private int boxWidth(Game g) {
        float text = measure.measureText(teams(g).toString()) + measure.measureText(detail(g).toString());
        int lead = g.leagueLogo != null ? logo : Math.round(measure.measureText(league(g).toString()));
        return Math.round(text) + lead + 2 * logo + dp(12 + 6 + 6 + 8) + dp(24) + dp(4);
    }

    private static CharSequence league(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder(g.league);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(0xFFC77DFF), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static CharSequence teams(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder();
        if (g.state == 1) {
            s.append(g.home).append("  v  ").append(g.away);
        } else {
            s.append(g.home).append("  ");
            int at = s.length();
            s.append(g.homeScore).append("–").append(g.awayScore);
            s.setSpan(new StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.append("  ").append(g.away);
        }
        return s;
    }

    private static CharSequence detail(Game g) {
        SpannableStringBuilder s = new SpannableStringBuilder(g.detail);
        s.setSpan(new ForegroundColorSpan(g.state == 0 ? 0xFF4ADE80 : 0x99FFFFFF), 0, s.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    // ---- logos -----------------------------------------------------------------------------------

    private void loadLogo(String url, ImageView into) {
        Bitmap hit = logos.get(url);
        if (hit != null) { into.setImageBitmap(hit); return; }
        List<ImageView> list = waiting.get(url);
        if (list != null) { list.add(into); return; }
        list = new ArrayList<>();
        list.add(into);
        waiting.put(url, list);
        logoIo.execute(() -> {
            Bitmap bmp = fetchLogo(url);
            main.post(() -> {
                List<ImageView> views = waiting.remove(url);
                if (bmp == null || views == null) return;
                logos.put(url, bmp);
                for (ImageView v : views) v.setImageBitmap(bmp);
            });
        });
    }

    /** From the disk cache, or from ESPN's resizer at twice the logo size, then saved for next time. */
    private Bitmap fetchLogo(String url) {
        File file = new File(logoDir, Integer.toHexString(url.hashCode()) + ".png");
        if (file.isFile()) {
            Bitmap b = BitmapFactory.decodeFile(file.getPath());
            if (b != null) return b;
        }
        String small = url;
        int at = url.indexOf("/i/");
        if (url.startsWith("https://a.espncdn.com/") && at > 0) {
            small = "https://a.espncdn.com/combiner/i?img=" + url.substring(at) + "&w=" + logo * 2 + "&h=" + logo * 2;
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(small).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            byte[] data;
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024) out.write(buf, 0, n);
                data = out.toByteArray();
            } finally {
                conn.disconnect();
            }
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (b != null && (logoDir.isDirectory() || logoDir.mkdirs())) {
                try (FileOutputStream fo = new FileOutputStream(file)) { fo.write(data); }
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
