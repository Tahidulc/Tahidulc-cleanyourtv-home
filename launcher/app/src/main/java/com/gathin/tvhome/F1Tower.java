package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * F1 live timing, as the timing tower down the left of F1's own pictures, over Sky Sports F1 in TiviMate: F1's red
 * band with the white F1 mark and the session, the lap (or "Lights out 20:00", or "Final"), then every driver in
 * running order: his place, his team's colour, the three letters F1's graphics use, his team's logo on a white tile,
 * places gained or lost since the grid in a race, and his gap when it is known. The order comes from OpenF1 (its
 * results and gaps, free from half an hour after a session) and, while a session runs, from ESPN's live order. Read
 * every ten seconds off the main thread; Back closes it, every other key goes on to TiviMate.
 */
final class F1Tower {
    private static final int RED = 0xFFE10600, RED_DEEP = 0xFF9E0400, BLACK = 0xFF141519, BLACK_DEEP = 0xFF0B0C0F,
            GAIN = 0xFF3DDC84, LOSS = 0xFFFF5A5F, NONE = Integer.MIN_VALUE;
    private static final String OPENF1 = "https://api.openf1.org/v1/";
    private static final long READ_MS = 10_000L;

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService io = Executors.newSingleThreadExecutor();
    private static Context app;
    private static FrameLayout host;
    private static LinearLayout rows;
    private static TextView label, strip, place, liveTag;
    private static ImageView mark;
    private static float k;
    private static final BitmapCache logos = new BitmapCache("f1", 4L * 1024 * 1024);

    /** One driver's line. */
    static final class Line {
        String code = "", key = "", gap = "", logo;
        int pos, colour = RED, change = NONE;
    }

    /** What the tower shows: the session, the line under the band, and the drivers. */
    static final class Board {
        String label = "", strip = "", place = "";
        boolean live;
        final List<Line> lines = new ArrayList<>();
    }

    private F1Tower() { }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    static boolean isShowing() {
        return host != null;
    }

    /** Opens it, Sky Sports F1 put on in TiviMate first when tune and a guide lists it. */
    static void open(Context ctx, boolean tune) {
        app = ctx.getApplicationContext();
        close();
        io.execute(() -> {
            Object[] w = tune ? GoalWatchService.f1Watch(app) : null;
            Board b = load();
            main.post(() -> {
                boolean tuning = false;
                if (w != null) {
                    try {
                        @SuppressWarnings("unchecked") List<String> names = (List<String>) w[0];
                        String pkg = app.getString(R.string.scores_open_package);
                        GoalWatchService.onAppOpened(app, pkg);
                        tuning = AppSearchService.tune(app, pkg, names, (Bitmap) w[1]);
                    } catch (RuntimeException ignored) {
                    }
                }
                if (tuning) waitForChannel(b, 0); else show(b);
            });
        });
    }

    private static void waitForChannel(Board b, int tries) {
        if (host != null) return;
        if (AppSearchService.tuning() && tries < 50) {
            main.postDelayed(() -> waitForChannel(b, tries + 1), 400L);
            return;
        }
        show(b);
    }

    // ---- the data -------------------------------------------------------------------------------------------------

    /**
     * The session worth showing: the one running; else, with the race (or sprint) still to start and its grid set,
     * the starting grid; else the last one finished. Its order from OpenF1 when OpenF1 has it, else ESPN's.
     */
    static Board load() {
        Board b = new Board();
        F1Results.Weekend w = F1Results.weekend(null);
        if (w == null || w.sessions.isEmpty()) {
            b.label = "F1";
            b.strip = "NO TIMING";
            return b;
        }
        b.place = w.name == null ? "" : w.name.replace(" Grand Prix", "");
        F1Results.Session live = null, next = null, last = null;
        for (F1Results.Session s : w.sessions) {
            if ("in".equals(s.state)) live = s;
            else if ("pre".equals(s.state) && next == null) next = s;
            else if ("post".equals(s.state)) last = s;
        }
        F1Results.Session gridOf = null;
        if (live == null && next != null && next.isRace()) {
            String abbr = "SR".equals(next.abbr) ? "SS" : "Qual";
            for (F1Results.Session s : w.sessions) if (abbr.equals(s.abbr) && "post".equals(s.state) && !s.top.isEmpty()) gridOf = s;
        }
        F1Results.Session shown = live != null ? live : gridOf != null ? gridOf : last;
        if (shown == null) shown = next != null ? next : w.sessions.get(0);
        b.live = shown == live;
        if (b.live) {
            b.label = F1Results.label(shown.abbr);
            b.strip = shown.isRace() && shown.lap > 0 ? "LAP " + shown.lap : "LIVE";
        } else if (shown == gridOf) {
            b.label = "SR".equals(next.abbr) ? "SPRINT GRID" : "STARTING GRID";
            b.strip = "LIGHTS OUT " + new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(next.start));
        } else {
            b.label = F1Results.label(shown.abbr);
            b.strip = "post".equals(shown.state) ? "FINAL" : "STARTS " + new java.text.SimpleDateFormat("EEE HH:mm", Locale.UK)
                    .format(new java.util.Date(shown.start)).toUpperCase(Locale.UK);
        }

        Map<String, String> teams = F1Results.teamsCached();
        boolean fromOpenF1 = !b.live && openF1(b, shown, teams);
        if (!fromOpenF1) {
            for (int i = 0; i < shown.top.size(); i++) {
                Line l = new Line();
                l.pos = i + 1;
                l.key = skey(shown.top.get(i));
                l.code = F1Results.code(l.key);
                String team = teams.get(F1Results.key(shown.top.get(i)));
                if (team == null) team = teams.get(l.key);
                l.colour = F1Results.teamColour(team);
                l.logo = i < shown.logos.size() && shown.logos.get(i) != null ? shown.logos.get(i) : F1Results.logo(team);
                b.lines.add(l);
            }
            // While a race runs, its gaps where OpenF1 lets them be read live (a sponsor's key); without one, none.
            if (b.live) liveGaps(b, shown);
        }
        // A race or sprint: places gained or lost since the grid (its qualifying's order).
        if (shown.isRace()) {
            String abbr = "SR".equals(shown.abbr) ? "SS" : "Qual";
            List<String> grid = new ArrayList<>();
            for (F1Results.Session s : w.sessions) if (abbr.equals(s.abbr)) for (String n : s.top) grid.add(skey(n));
            for (Line l : b.lines) {
                int g = grid.indexOf(l.key);
                if (g >= 0) l.change = (g + 1) - l.pos;
            }
        }
        return b;
    }

    /** A finished session's classification from OpenF1, with the gaps; false when OpenF1 does not have it. */
    private static boolean openF1(Board b, F1Results.Session s, Map<String, String> teams) {
        try {
            String key = sessionKey(s);
            if (key == null) return false;
            JSONArray result = new JSONArray(ScoresTicker.get(OPENF1 + "session_result?session_key=" + key));
            if (result.length() == 0) return false;
            Map<Integer, JSONObject> drivers = drivers(key);
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < result.length(); i++) list.add(result.getJSONObject(i));
            list.sort((x, y) -> Integer.compare(x.optInt("position", 99) == 0 ? 99 : x.optInt("position", 99),
                    y.optInt("position", 99) == 0 ? 99 : y.optInt("position", 99)));
            boolean race = s.isRace();
            int laps = 0;
            for (JSONObject r : list) {
                JSONObject d = drivers.get(r.optInt("driver_number"));
                Line l = new Line();
                l.pos = b.lines.size() + 1;
                String last = d == null ? "" : d.optString("last_name");
                l.key = skey(last);
                l.code = d == null ? "#" + r.optInt("driver_number") : d.optString("name_acronym", F1Results.code(last));
                String team = teams.get(F1Results.key(last));
                l.colour = team != null ? F1Results.teamColour(team) : colour(d == null ? null : d.optString("team_colour"));
                l.logo = F1Results.logo(team);
                if (r.optBoolean("dsq")) l.gap = "DSQ";
                else if (r.optBoolean("dns")) l.gap = "DNS";
                else if (r.optBoolean("dnf")) l.gap = "DNF";
                else l.gap = race ? raceGap(r, l.pos) : qualiGap(r, l.pos);
                laps = Math.max(laps, r.optInt("number_of_laps"));
                b.lines.add(l);
            }
            if (race && "FINAL".equals(b.strip) && laps > 0) b.strip = "FINAL · " + laps + " LAPS";
            return !b.lines.isEmpty();
        } catch (Exception e) {
            b.lines.clear();
            return false;
        }
    }

    /** The live gaps to the leader, latest per car, laid on ESPN's order by surname; nothing when OpenF1 says no. */
    private static void liveGaps(Board b, F1Results.Session s) {
        try {
            String key = sessionKey(s);
            if (key == null) return;
            String since = java.time.Instant.ofEpochMilli(System.currentTimeMillis() - 60_000L).toString();
            JSONArray iv = new JSONArray(ScoresTicker.get(OPENF1 + "intervals?session_key=" + key + "&date%3E" + since));
            Map<Integer, JSONObject> drivers = drivers(key);
            Map<String, String> gaps = new HashMap<>();
            for (int i = 0; i < iv.length(); i++) {
                JSONObject x = iv.getJSONObject(i);
                JSONObject d = drivers.get(x.optInt("driver_number"));
                if (d == null) continue;
                Object g = x.opt("gap_to_leader");
                gaps.put(skey(d.optString("last_name")), g instanceof Number
                        ? (((Number) g).doubleValue() == 0 ? "" : String.format(Locale.UK, "+%.3f", ((Number) g).doubleValue()))
                        : g == null || g == JSONObject.NULL ? "" : String.valueOf(g));
            }
            for (Line l : b.lines) {
                String g = gaps.get(l.key);
                if (g != null) l.gap = l.pos == 1 ? "LEADER" : g;
            }
        } catch (Exception ignored) {
        }
    }

    private static String sessionKeyFor;
    private static String sessionKeyIs;

    /** OpenF1's key for ESPN's session: this meeting's session starting within 20 minutes of it. */
    private static String sessionKey(F1Results.Session s) throws Exception {
        if (s.id != null && s.id.equals(sessionKeyFor)) return sessionKeyIs;
        JSONArray all = new JSONArray(ScoresTicker.get(OPENF1 + "sessions?meeting_key=latest"));
        String found = null;
        for (int i = 0; i < all.length(); i++) {
            JSONObject x = all.getJSONObject(i);
            long at = java.time.OffsetDateTime.parse(x.optString("date_start")).toInstant().toEpochMilli();
            if (Math.abs(at - s.start) < 20 * 60_000L) found = String.valueOf(x.optInt("session_key"));
        }
        sessionKeyFor = s.id;
        sessionKeyIs = found;
        return found;
    }

    private static final Map<String, Map<Integer, JSONObject>> driverCache = new HashMap<>();

    private static Map<Integer, JSONObject> drivers(String key) throws Exception {
        Map<Integer, JSONObject> m = driverCache.get(key);
        if (m != null) return m;
        m = new HashMap<>();
        JSONArray arr = new JSONArray(ScoresTicker.get(OPENF1 + "drivers?session_key=" + key));
        for (int i = 0; i < arr.length(); i++) m.put(arr.getJSONObject(i).optInt("driver_number"), arr.getJSONObject(i));
        if (!m.isEmpty()) driverCache.put(key, m);
        return m;
    }

    /** A race's gap: "+12.345", "+1 LAP" as OpenF1 writes it; the winner "WINNER". */
    private static String raceGap(JSONObject r, int pos) {
        if (pos == 1) return "WINNER";
        Object g = r.opt("gap_to_leader");
        if (g instanceof Number) return String.format(Locale.UK, "+%.3f", ((Number) g).doubleValue());
        return g == null || g == JSONObject.NULL ? "" : String.valueOf(g).toUpperCase(Locale.UK);
    }

    /** Qualifying: pole's lap time, every other driver's gap in the last part he reached. */
    private static String qualiGap(JSONObject r, int pos) {
        JSONArray dur = r.optJSONArray("duration"), gap = r.optJSONArray("gap_to_leader");
        int part = -1;
        for (int i = 0; dur != null && i < dur.length(); i++) if (!dur.isNull(i)) part = i;
        if (part < 0) return "";
        if (pos == 1) {
            double t = dur.optDouble(part);
            int m = (int) (t / 60);
            return String.format(Locale.UK, "%d:%06.3f", m, t - m * 60);
        }
        if (gap == null || gap.isNull(part)) return "";
        Object g = gap.opt(part);
        return g instanceof Number ? String.format(Locale.UK, "+%.3f", ((Number) g).doubleValue()) : String.valueOf(g);
    }

    private static int colour(String hex) {
        try {
            return hex == null || hex.isEmpty() ? RED : 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return RED;
        }
    }

    /** "Sainz Jr" and "Sainz" alike: the last word of the surname, without accents. */
    private static String skey(String surname) {
        String s = F1Results.key(surname).replaceAll("\\s+jr\\.?$", "").trim();
        int sp = s.lastIndexOf(' ');
        return sp >= 0 ? s.substring(sp + 1) : s;
    }

    // ---- the tower ------------------------------------------------------------------------------------------------

    private static void show(Board b) {
        if (host != null || app == null) return;
        Context c = app;
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        FrameLayout win = new FrameLayout(c);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BLACK);
        bg.setCornerRadius(px(12));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setOutlineProvider(round(px(12)));
        card.setElevation(px(10));

        // F1's red band: the white F1 mark, and the session.
        LinearLayout band = new LinearLayout(c);
        band.setGravity(Gravity.CENTER_VERTICAL);
        band.setPadding(px(16), 0, px(16), 0);
        band.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{RED, RED_DEEP}));
        mark = new ImageView(c);
        mark.setScaleType(ImageView.ScaleType.FIT_START);
        band.addView(mark, new LinearLayout.LayoutParams(px(74), px(20)));
        label = text(c, "", 18, 0xFFFFFFFF, ScoreCard.outfit(c, 800));
        label.setLetterSpacing(0.08f);
        label.setGravity(Gravity.END);
        band.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(band, new LinearLayout.LayoutParams(-1, px(58)));

        // The lap, the lights or "Final", and where.
        LinearLayout st = new LinearLayout(c);
        st.setGravity(Gravity.CENTER_VERTICAL);
        st.setPadding(px(16), 0, px(14), 0);
        st.setBackgroundColor(BLACK_DEEP);
        strip = text(c, "", 17, 0xFFFFFFFF, ScoreCard.outfit(c, 800));
        strip.setLetterSpacing(0.06f);
        st.addView(strip, new LinearLayout.LayoutParams(-2, -2));
        place = text(c, "", 14, 0xB3FFFFFF, inter(c));
        place.setGravity(Gravity.END);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, -2, 1f);
        pl.leftMargin = px(10);
        st.addView(place, pl);
        liveTag = text(c, "LIVE", 12, 0xFFFFFFFF, ScoreCard.outfit(c, 900));
        liveTag.setLetterSpacing(0.12f);
        liveTag.setPadding(px(7), px(3), px(7), px(3));
        GradientDrawable tag = new GradientDrawable();
        tag.setColor(RED);
        tag.setCornerRadius(px(4));
        liveTag.setBackground(tag);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(10);
        st.addView(liveTag, tl);
        card.addView(st, new LinearLayout.LayoutParams(-1, px(36)));

        rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, px(4), 0, px(4));
        card.addView(rows, new LinearLayout.LayoutParams(-1, -2));

        // Back hides it.
        LinearLayout foot = new LinearLayout(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(px(16), 0, px(16), 0);
        foot.setBackgroundColor(BLACK_DEEP);
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), px(20)));
        back.setTag(PopupMotion.BACK_KEY);
        foot.addView(back, new LinearLayout.LayoutParams(px(20), px(20)));
        TextView hide = text(c, "Hide timing", 13, 0x99FFFFFF, inter(c));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.leftMargin = px(8);
        foot.addView(hide, hl);
        card.addView(foot, new LinearLayout.LayoutParams(-1, px(32)));

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(330), -2, Gravity.TOP | Gravity.START);
        cl.leftMargin = px(36);
        cl.topMargin = px(36);
        win.addView(card, cl);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("F1Tower");
        // The remote stays with TiviMate: keys through the key helper, or the channel helper on a TV without one.
        boolean takeKeys = !InputKeyService.running && !AppSearchService.running();
        if (takeKeys) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        host = win;
        logoCover(c, true);
        if (!InputKeyService.running && !takeKeys) AppSearchService.keysFor("f1", true);
        if (takeKeys) {
            // Without the key helper (the TCL) the tower takes the remote's keys itself while it shows.
            win.setFocusable(true);
            win.setFocusableInTouchMode(true);
            win.requestFocus();
            win.setOnKeyListener((v, code, e) -> handle(code, e));
        }
        io.execute(() -> {
            Bitmap m = ScoresTicker.cachedLogo(app, F1Results.F1_LOGO, 200);
            Bitmap w = m == null ? null : GoalWatchService.white(HomeV2.trim(m));
            if (w != null) main.post(() -> mark.setImageBitmap(w));
        });
        fill(b);
        card.setTranslationX(-px(380));
        card.animate().translationX(0).setDuration(420).setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).withLayer().start();
        main.postDelayed(reread, READ_MS);
    }

    private static void fill(Board b) {
        if (host == null) return;
        Context c = app;
        label.setText(b.label);
        strip.setText(b.strip);
        place.setText(b.place);
        liveTag.setVisibility(b.live ? View.VISIBLE : View.GONE);
        rows.removeAllViews();
        if (b.lines.isEmpty()) {
            TextView none = text(c, "No order yet", 16, 0x99FFFFFF, inter(c));
            none.setPadding(px(16), px(14), px(16), px(14));
            rows.addView(none);
            return;
        }
        boolean anyGap = false, anyChange = false;
        for (Line l : b.lines) { anyGap |= !l.gap.isEmpty(); anyChange |= l.change != NONE; }
        for (Line l : b.lines) rows.addView(row(c, l, anyGap, anyChange), new LinearLayout.LayoutParams(-1, px(b.lines.size() > 20 ? 39 : 40)));
    }

    private static View row(Context c, Line l, boolean gaps, boolean changes) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, 0, px(14), 0);
        if (l.pos % 2 == 0) r.setBackgroundColor(0x0DFFFFFF);

        TextView pos = text(c, String.valueOf(l.pos), 17, 0xFFFFFFFF, ScoreCard.outfit(c, 700));
        pos.setGravity(Gravity.CENTER);
        r.addView(pos, new LinearLayout.LayoutParams(px(42), -2));

        View bar = new View(c);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(l.colour);
        bb.setCornerRadius(px(2));
        bar.setBackground(bb);
        r.addView(bar, new LinearLayout.LayoutParams(px(5), px(21)));

        TextView code = text(c, l.code, 18, 0xFFFFFFFF, ScoreCard.outfit(c, 800));
        code.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams col = new LinearLayout.LayoutParams(px(56), -2);
        col.leftMargin = px(10);
        r.addView(code, col);

        r.addView(logoTile(c, l.logo, px(23)), new LinearLayout.LayoutParams(px(23), px(23)));
        r.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));

        if (changes) {
            String ch = l.change == NONE || l.change == 0 ? "" : (l.change > 0 ? "▲" : "▼") + Math.abs(l.change);
            TextView v = text(c, ch, 13, l.change > 0 ? GAIN : LOSS, ScoreCard.outfit(c, 700));
            v.setGravity(Gravity.END);
            r.addView(v, new LinearLayout.LayoutParams(px(36), -2));
        }
        if (gaps) {
            TextView g = text(c, l.gap, 15, l.pos == 1 ? 0xFFFFFFFF : 0xFFD6D7DE, ScoreCard.outfit(c, 600));
            g.setGravity(Gravity.END);
            r.addView(g, new LinearLayout.LayoutParams(px(92), -2));
        }
        return r;
    }

    /** The team's logo straight on the black, its dark parts lifted to white so it shows (ScoreCard.onBlack). */
    private static View logoTile(Context c, String url, int size) {
        FrameLayout tile = new FrameLayout(c);
        if (url == null) return tile;
        // Straight on the black, no tile: a dark logo's dark parts lifted to white so it shows (ScoreCard.onBlack).
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(v, new FrameLayout.LayoutParams(-1, -1));
        Bitmap have = logos.get(url);
        if (have != null) {
            v.setImageBitmap(have);
        } else {
            io.execute(() -> {
                Bitmap b = ScoresTicker.cachedLogo(app, url, 64);
                if (b == null) return;
                Bitmap t = ScoreCard.onBlack(HomeV2.trim(b));
                main.post(() -> { logos.put(url, t); v.setImageBitmap(t); });
            });
        }
        return tile;
    }

    private static final Runnable reread = new Runnable() {
        @Override public void run() {
            if (host == null) return;
            io.execute(() -> {
                Board b = load();
                main.post(() -> fill(b));
            });
            main.postDelayed(this, READ_MS);
        }
    };

    // ---- keys -----------------------------------------------------------------------------------------------------

    /** The remote while it shows (InputKeyService): Back hides it; every other key goes on to TiviMate. */
    static boolean key(KeyEvent e) {
        if (host == null) return false;
        return handle(e.getKeyCode(), e);
    }

    private static boolean handle(int code, KeyEvent e) {
        if (code != KeyEvent.KEYCODE_BACK) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(F1Tower::close);
        return true;
    }

    static void close() {
        main.removeCallbacks(reread);
        logoCover(app, false);
        if (host != null && !InputKeyService.running) AppSearchService.keysFor("f1", false);
        FrameLayout v = host;
        host = null;
        if (v == null) return;
        v.setOnKeyListener(null);
        View card = v.getChildCount() > 0 ? v.getChildAt(0) : v;
        card.animate().translationX(-px(380)).setDuration(260).withLayer().withEndAction(() -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
        }).start();
    }

    /** The cover over the channel's logo while the tower is up: where the scoreboard's goes for this channel, in its look. */
    private static FrameLayout logoWin;
    private static LogoMark logoMark;

    /** The picture tag chosen anew on the Scoreboard card or its menu: the cover over the race's logo follows. */
    static void retag(Context c) {
        LogoMark m = logoMark;
        if (m != null && c != null) m.tag(BoardCover.logoTag(c).on(InStep.channel(c)));
    }

    private static void logoCover(Context c, boolean on) {
        if (!on) {
            FrameLayout f = logoWin;
            logoWin = null;
            logoMark = null;
            if (f != null) {
                try {
                    f.getContext().getSystemService(WindowManager.class).removeView(f);
                } catch (RuntimeException ignored) {
                    // gone already
                }
            }
            return;
        }
        if (c == null || logoWin != null || !BoardCover.logoOn(c)) return;
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
        String channel = InStep.channel(c);
        BoardPlace p = BoardPlace.get(c, channel);
        int edge = Math.max(2, Math.round(2f * sw / 1920f));
        LogoMark m = new LogoMark(c);
        m.look(BoardCover.logoLook(c));
        m.inset(edge);
        m.tag(BoardCover.logoTag(c).on(channel));
        m.sport("f1");
        // The Competition look: F1's own logo, in white.
        m.competition(null, "F1");
        m.compOn(BoardCover.logoComp(c));
        m.liveBar(BoardCover.logoLive(c));
        Context app = c.getApplicationContext();
        new Thread(() -> {
            Bitmap f1 = ScoresTicker.cachedLogo(app, F1Results.F1_LOGO, 200);
            Bitmap white = f1 == null ? null : GoalWatchService.white(HomeV2.trim(f1));
            if (white != null) m.post(() -> m.competition(white, "F1"));
        }, "f1-cover-logo").start();
        m.live(true);
        BoardBug colours = new BoardBug(c);
        colours.style(BoardCover.style(c));
        m.boardLook(colours.stripLook());
        int w = Math.round(p.lw * sw) + 2 * edge, h = Math.round(p.lh * sh) + 2 * edge;
        FrameLayout f = new FrameLayout(c);
        f.addView(m, new FrameLayout.LayoutParams(w, h));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Math.round(p.lx * sw) - edge;
        lp.y = Math.round(p.ly * sh) - edge;
        lp.setTitle("F1Tower logo");
        try {
            c.getSystemService(WindowManager.class).addView(f, lp);
            logoWin = f;
            logoMark = m;
        } catch (RuntimeException ignored) {
            // the tower without it
        }
    }

    // ---- small helpers --------------------------------------------------------------------------------------------

    private static Typeface inter(Context c) {
        try {
            return Fonts.get(c, R.font.inter_semibold);
        } catch (RuntimeException e) {
            return Typeface.DEFAULT_BOLD;
        }
    }

    private static TextView text(Context c, String s, float sizePx, int colour, Typeface face) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * k);
        v.setTextColor(colour);
        v.setTypeface(face);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static ViewOutlineProvider round(int r) {
        return new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        };
    }
}
