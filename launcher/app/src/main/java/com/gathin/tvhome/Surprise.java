package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Surprise me: Movies' tab of moods (Cosy, Edge of my seat, Mind-bending …), each opening a spin that picks one film
 * on the TV's own streaming apps for that mood, not yet watched here, for who is watching (just me, two of us, the
 * family: rated PG at most) and, when asked, done before bed. The reel of posters spins and stops on the pick; Play
 * opens it (as the panels do), Roll Again picks another, Trailer looks it up on YouTube. Up and Down between the moods,
 * who and the buttons; Left and Right along them; OK chooses; Back closes.
 */
final class Surprise {
    private Surprise() { }

    /** {name, line under it, with_genres, without_genres}. */
    static final String[][] MOODS = {
            {"Cosy", "Feel-good comedies, family films and romance", "35|10751|10749", "27,53,80"},
            {"Edge of my seat", "Thrillers, crime and mysteries that keep you guessing", "53|80|9648", "16,10751"},
            {"Mind-bending", "Science fiction that makes you think", "878", "16,10751"},
            {"Big laugh", "Comedies, nothing heavy", "35", "18,27,53"},
            {"Good cry", "Dramas and love stories", "18|10749", "27,28,53"},
            {"Epic", "Big adventures, history and war", "12|14|36|10752", "16"},
            {"Scary", "Horror", "27", "16,10751"}};
    private static final String[] WHO = {"Just me", "Two of us", "Family"};
    private static final int NAVY = MockKit.NAVY, YELLOW = 0xFFFFD04D, GROUND = 0xFF080A32;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Surprise shown;

    private HomeV2 h;
    private Context c;
    private FrameLayout overlay;
    private final List<TextView> moodViews = new ArrayList<>(), whoViews = new ArrayList<>();
    private final List<LinearLayout> buttons = new ArrayList<>();
    private TextView timeView, name, line, why, about;
    /** The pick's own picture, blurred, behind everything. */
    private ImageView back;
    private Typeface semi, regular;
    /** After "on" in the line of details: Debrid Stream's mark, then the services that carry the film (DiceRoll.marks). */
    private LinearLayout marks;
    /** The reel thrown like dice. */
    private DiceRoll dice;
    private final ImageView[] reel = new ImageView[5];
    private int mood, who = 1, row = 2, col;
    private boolean byBed = true, spinning;
    private List<PagesV2.Title> pool = new ArrayList<>();
    private final List<Bitmap> pics = new ArrayList<>();
    private PagesV2.Title pick;
    private int gen;
    private final Random random = new Random();

    // ---- Movies' tab: the moods -----------------------------------------------------------------------------------

    /**
     * The moods as cards of their own (MoodArt: each mood's colours, its sign and its name), not a film's poster, which
     * looked like a spin for that one film. OK on one spins for it.
     */
    static PagesV2.Row row(HomeV2 h) {
        List<PagesV2.Title> items = new ArrayList<>();
        for (int i = 0; i < MOODS.length; i++) {
            PagesV2.Title t = new PagesV2.Title();
            t.title = MOODS[i][0];
            t.overview = MOODS[i][1] + ", from the films on your streaming apps. OK spins for one you haven't seen.";
            t.year = "";
            t.what = "Surprise me";
            t.tv = false;
            t.poster = "mood:mv:" + i + ":p";
            t.backdrop = "mood:mv:" + i + ":w";
            int m = i;
            t.ok = () -> open(h, m);
            items.add(t);
        }
        return PagesV2.row(null, null, false, items);
    }

    /** TMDB's discover for a mood on the TV's apps; who 2 is the family (PG at most); within the time to bed if asked. */
    static String query(Context c, int mood, int who, boolean byBed, int page) {
        String providers = ReleaseAlerts.providers(c);
        StringBuilder q = new StringBuilder("discover/movie?watch_region=GB&with_watch_monetization_types=flatrate&with_watch_providers=")
                .append(providers.isEmpty() ? "8" : providers).append("&with_genres=").append(MOODS[mood][2])
                .append("&without_genres=").append(MOODS[mood][3]).append("&vote_average.gte=6.3&vote_count.gte=150&with_runtime.gte=70")
                .append("&sort_by=popularity.desc&page=").append(page);
        if (who == 2) q.append("&certification_country=GB&certification.lte=PG");
        if (byBed) q.append("&with_runtime.lte=").append(FitsTonight.window(c));
        return q.toString();
    }

    // ---- the spin ---------------------------------------------------------------------------------------------------

    static boolean isOpen() {
        return shown != null;
    }

    static boolean key(KeyEvent e) {
        return shown != null && shown.onKey(e);
    }

    static void close() {
        if (shown != null) shown.hide();
    }

    static void open(HomeV2 h, int mood) {
        close();
        Surprise s = new Surprise();
        s.h = h;
        s.c = h.a;
        s.mood = mood;
        shown = s;
        s.build();
        s.spin();
    }

    private void hide() {
        if (shown == this) shown = null;
        gen++;
        dice.stop();
        View o = overlay;
        o.animate().alpha(0f).setDuration(180).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    /**
     * As the mock-up: the pick's own picture, blurred and dark, behind a deep-blue glow; MOVIES and Surprise me over the
     * moods; who's watching and the time; the reel of posters fanned in perspective (outer ones turned further and
     * fainter, fading into the page at the sides), the pick large in a white ring; then the film and its details on
     * one line, why it suits in yellow, what it's about, and the pills: OK Play, Roll Again, Trailer.
     */
    private void build() {
        Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold), semi = c.getResources().getFont(R.font.inter_semibold),
                regular = c.getResources().getFont(R.font.inter_regular);
        this.semi = semi;
        this.regular = regular;
        overlay = new FrameLayout(c);
        overlay.setBackgroundColor(GROUND);
        overlay.setClickable(true);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        back = new ImageView(c);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        back.setColorFilter(new android.graphics.LightingColorFilter(0xFF737373, 0));   // brightness 45%
        back.setScaleX(1.06f);
        back.setScaleY(1.06f);
        back.setAlpha(0f);
        overlay.addView(back, new FrameLayout.LayoutParams(-1, -1));
        View glow = new View(c);
        GradientDrawable gg = new GradientDrawable();
        gg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gg.setGradientCenter(0.5f, 0.55f);
        gg.setGradientRadius(h.px(1000));
        gg.setColors(new int[]{0x33141C92, 0xE6080A32});
        glow.setBackground(gg);
        overlay.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        TextView crumb = h.text(16, heavy, 0xFFFF4FA3);
        crumb.setText("MOVIES");
        crumb.setLetterSpacing(0.25f);
        crumb.setGravity(Gravity.CENTER);
        place(crumb, 0, 70, 1920, 22);
        TextView head = h.text(58, heavy, Color.WHITE);
        head.setText("Surprise me");
        head.setGravity(Gravity.CENTER);
        place(head, 0, 100, 1920, 76);
        LinearLayout moods = new LinearLayout(c);
        moods.setGravity(Gravity.CENTER);
        for (String[] m : MOODS) moodViews.add(chip(moods, m[0], 20, 50, regular));
        place(moods, 0, 196, 1920, 50);
        LinearLayout whoRow = new LinearLayout(c);
        whoRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        whoRow.addView(caption("WHO'S WATCHING", heavy, 0));
        for (String w : WHO) whoViews.add(chip(whoRow, w, 17, 44, regular));
        whoRow.addView(caption("TIME", heavy, 30));
        timeView = chip(whoRow, "", 17, 44, regular);
        place(whoRow, 0, 262, 1920, 44);
        // The fan: as the mock-up's perspective, the outer posters turned 36 degrees, the next 18.
        int[][] at = {{256, 412, 200, 300}, {550, 412, 200, 300}, {810, 336, 300, 450}, {1170, 412, 200, 300}, {1464, 412, 200, 300}};
        float[] turn = {36, 18, 0, -18, -36}, alpha = {0.35f, 0.6f, 1f, 0.6f, 0.35f};
        for (int i = 0; i < 5; i++) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setBackground(h.fill(i == 2 ? 20 : 14, 0xFF141A5C));
            v.setClipToOutline(true);
            v.setOutlineProvider(h.round(i == 2 ? 20 : 14));
            v.setAlpha(alpha[i]);
            v.setRotationY(turn[i]);
            if (i == 2) v.setElevation(h.px(30));
            reel[i] = v;
            place(v, at[i][0], at[i][1], at[i][2], at[i][3]);
        }
        View ring = new View(c);
        GradientDrawable rg = new GradientDrawable();
        rg.setCornerRadius(h.px(20));
        rg.setStroke(h.px(6), Color.WHITE);
        ring.setBackground(rg);
        ring.setElevation(h.px(31));
        place(ring, 810, 336, 300, 450);
        dice = new DiceRoll(h, overlay, reel, ring);
        // The row fades into the page at both ends, as the mock-up's.
        place(MockKit.sideFade(c, GROUND, 0.26f), 0, 330, 1920, 470);
        // Under the reel: the film with its details (and where Play opens it) on one line, why it suits in yellow, what
        // it is about, then the pills.
        LinearLayout meta = new LinearLayout(c);
        meta.setGravity(Gravity.CENTER);
        name = h.text(30, heavy, Color.WHITE);
        name.setSingleLine(true);
        meta.addView(name);
        line = h.text(20, regular, 0xD9FFFFFF);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.leftMargin = h.px(14);
        meta.addView(line, ll);
        marks = new LinearLayout(c);
        marks.setGravity(Gravity.CENTER_VERTICAL);
        marks.setVisibility(View.GONE);
        meta.addView(marks, new LinearLayout.LayoutParams(-2, h.px(42)));
        place(meta, 0, 806, 1920, 44);
        why = h.text(19, semi, YELLOW);
        why.setGravity(Gravity.CENTER);
        place(why, 0, 856, 1920, 30);
        about = h.text(17, regular, 0xB3FFFFFF);
        about.setGravity(Gravity.CENTER);
        about.setMaxLines(2);
        about.setEllipsize(android.text.TextUtils.TruncateAt.END);
        place(about, 360, 892, 1200, 46);
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER);
        buttons.add(pill(bar, "Play", true));
        buttons.add(pill(bar, "🎲  Roll Again", false));
        buttons.add(pill(bar, "▲  Trailer", false));
        place(bar, 0, 950, 1920, 64);
        paint();
    }

    private TextView caption(String words, Typeface face, int before) {
        TextView t = h.text(15, face, 0xB3FFFFFF);
        t.setText(words);
        t.setLetterSpacing(0.13f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = h.px(before);
        lp.rightMargin = h.px(10);
        t.setLayoutParams(lp);
        return t;
    }

    private LinearLayout pill(LinearLayout into, String words, boolean main) {
        LinearLayout p = MockKit.pill(h, words, 21, semi, 64, main, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(64));
        lp.leftMargin = lp.rightMargin = h.px(7);
        into.addView(p, lp);
        return p;
    }

    private TextView chip(LinearLayout into, String words, int sp, int tall, Typeface face) {
        TextView t = h.text(sp, face, Color.WHITE);
        t.setText(words);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(h.px(sp >= 20 ? 22 : 18), 0, h.px(sp >= 20 ? 22 : 18), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(tall));
        lp.leftMargin = lp.rightMargin = h.px(sp >= 20 ? 6 : 5);
        into.addView(t, lp);
        return t;
    }

    /** A new pick: the films for the mood read (a page at random of the first three), then the reel spins onto one. */
    private void spin() {
        int my = ++gen;
        spinning = true;
        name.setText("");
        line.setText("");
        about.setText("");
        marks.setVisibility(View.GONE);
        why.setText("Rolling the dice…");
        back.animate().alpha(0.35f).setDuration(300).start();
        dice.start(new ArrayList<>(pics));
        int m = mood, w = who;
        boolean bed = byBed;
        IO.execute(() -> {
            List<PagesV2.Title> got = new ArrayList<>();
            Map<String, java.time.LocalDate> seen = ViewingLog.watched(c);
            // How was it?'s Not for me: that film never, others of its main genre after the rest.
            java.util.Set<String> nfm = Ratings.notForMe(c), nfmGenres = Ratings.notForMeGenres(c);
            for (int page : new int[]{1 + random.nextInt(3), 1}) {
                for (PagesV2.Title t : PagesV2.tmdb(h.tmdbKey, query(c, m, w, bed, page), false)) {
                    if (!seen.containsKey("mv:" + t.id) && !nfm.contains("mv:" + t.id) && !contains(got, t.id)) got.add(t);
                }
                if (got.size() >= 6) break;
            }
            Collections.shuffle(got, random);
            if (!nfmGenres.isEmpty()) {
                List<PagesV2.Title> later = new ArrayList<>();
                for (PagesV2.Title t : got) {
                    for (String g : t.what == null ? new String[0] : t.what.split(" · ")) if (nfmGenres.contains(g)) { later.add(t); break; }
                }
                got.removeAll(later);
                got.addAll(later);
            }
            // The pick: the first whose real length fits (TMDB's own filter lets some longer ones through).
            int runtime = 0, window = FitsTonight.window(c);
            for (int i = 0; i < Math.min(6, got.size()); i++) {
                int rt;
                try {
                    rt = new org.json.JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + got.get(i).id + "?api_key=" + h.tmdbKey)).optInt("runtime");
                } catch (Exception ex) {
                    rt = 0;
                }
                if (!bed || rt > 0 && rt <= window) {
                    runtime = rt;
                    got.add(0, got.remove(i));
                    break;
                }
            }
            List<PagesV2.Title> five = got.subList(0, Math.min(9, got.size()));
            List<Bitmap> bitmaps = new ArrayList<>();
            for (PagesV2.Title t : five) bitmaps.add(ScoresTicker.cachedLogo(c, t.poster, h.px(300)));
            // Where the pick is streaming in the UK, for the marks after "on".
            org.json.JSONObject where = null;
            if (!five.isEmpty()) {
                try {
                    org.json.JSONObject r = new org.json.JSONObject(HomeV2.get("https://api.themoviedb.org/3/movie/" + five.get(0).id
                            + "/watch/providers?api_key=" + h.tmdbKey)).optJSONObject("results");
                    where = r == null ? null : r.optJSONObject("GB");
                } catch (Exception ignored) {
                    // Debrid Stream's mark alone
                }
            }
            // The pick's picture, blurred, for behind it all.
            String art = five.isEmpty() ? null : five.get(0).backdrop;
            Bitmap soft = art == null || !art.startsWith("http") ? null
                    : MockKit.soft(ScoresTicker.cachedLogo(c, art.replace("/w1280/", "/w300/"), h.px(300)), 48);
            int rt = runtime;
            org.json.JSONObject gb = where;
            MAIN.post(() -> {
                if (my != gen || shown != this) return;
                pool = new ArrayList<>(five);
                pics.clear();
                pics.addAll(bitmaps);
                land(my, rt, gb, soft);
            });
        });
    }

    private static boolean contains(List<PagesV2.Title> list, int id) {
        for (PagesV2.Title t : list) if (t.id == id) return true;
        return false;
    }

    /** The dice land: the pick (the first) on the middle card, the others round it; then its details. */
    private void land(int my, int runtime, org.json.JSONObject gb, Bitmap soft) {
        if (my != gen || shown != this) return;
        Bitmap[] last = new Bitmap[5];
        int n = pics.size();
        for (int i = 0; i < 5; i++) last[i] = n == 0 ? DiceRoll.die(1 + random.nextInt(6)) : pics.get(((i - 2) % n + n) % n);
        dice.land(last, () -> {
            if (my != gen || shown != this) return;
            spinning = false;
            if (pool.isEmpty()) {
                why.setText("Nothing on your apps for this right now. Try another mood, or turn off Done by bed.");
                pick = null;
                return;
            }
            pick = pool.get(0);
            name.setText(pick.title);
            String vote = pick.vote > 0 ? String.format(Locale.UK, "★ %.1f", pick.vote) : "";
            // Where Play opens it: Debrid Stream's mark first, then the services it is on.
            boolean on = DiceRoll.marks(h, marks, gb);
            line.setText(String.join("  ·  ", nonEmpty(pick.year, pick.what, runtime > 0 ? runtime / 60 + "h " + String.format(Locale.UK, "%02d", runtime % 60) + "m" : "", vote,
                    on ? "on" : "")));
            why.setText((runtime > 0 ? FitsTonight.heroLine(c, runtime) + " · " : "") + MOODS[mood][0] + " · not watched on this TV yet");
            about.setText(pick.overview == null ? "" : pick.overview);
            if (soft != null) {
                back.animate().cancel();
                back.setImageBitmap(soft);
                back.setAlpha(0f);
                back.animate().alpha(1f).setDuration(600).start();
            }
            for (View v : new View[]{name, why, line, marks, about}) {
                v.setAlpha(0f);
                v.setTranslationY(h.px(10));
                v.animate().alpha(1f).translationY(0).setDuration(320).start();
            }
        });
    }

    private static String[] nonEmpty(String... s) {
        List<String> out = new ArrayList<>();
        for (String x : s) if (x != null && !x.isEmpty()) out.add(x);
        return out.toArray(new String[0]);
    }

    private boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) press();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int size = row == 0 ? moodViews.size() : row == 1 ? whoViews.size() + 1 : buttons.size();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) col--;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < size - 1) col++;
        else if (code == KeyEvent.KEYCODE_DPAD_UP && row > 0) { row--; col = row == 0 ? mood : Math.min(who, 3); }
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && row < 2) { row++; col = row == 1 ? who : 0; }
        paint();
        return true;
    }

    private void press() {
        if (row == 0) { mood = col; spin(); }
        else if (row == 1 && col < WHO.length) { who = col; spin(); }
        else if (row == 1) { byBed = !byBed; spin(); }
        else if (col == 0) { if (pick != null && !spinning) { PagesV2.Title t = pick; hide(); h.openTitle(t); } }
        else if (col == 2) {
            // The trailer: YouTube's search for it.
            if (pick != null && !spinning) {
                String q = pick.title + (pick.year == null || pick.year.isEmpty() ? "" : " " + pick.year) + " official trailer";
                hide();
                h.searchTitle("com.google.android.youtube.tv", q, false, 0, null);
            }
        }
        else spin();
        if (shown == this) paint();
    }

    private void paint() {
        for (int i = 0; i < moodViews.size(); i++) style(moodViews.get(i), i == mood, row == 0 && i == col, 25);
        for (int i = 0; i < whoViews.size(); i++) style(whoViews.get(i), i == who, row == 1 && i == col, 22);
        timeView.setText(byBed ? "Done by " + String.format(Locale.UK, "%02d:%02d", FitsTonight.bedMinutes(c) / 60 % 24, FitsTonight.bedMinutes(c) % 60) : "Any length");
        style(timeView, byBed, row == 1 && col == WHO.length, 22);
        for (int i = 0; i < buttons.size(); i++) MockKit.paint(h, buttons.get(i), row == 2 && i == col, 64);
    }

    /** chosen: white, in bold navy; not chosen: an outline; focused: a yellow ring round it as well. */
    private void style(TextView t, boolean chosen, boolean focused, float radius) {
        GradientDrawable g = h.fill(radius, chosen ? 0xFFFFFFFF : focused ? 0x33FFFFFF : 0x00FFFFFF);
        if (focused) g.setStroke(h.px(3), YELLOW);
        else if (!chosen) g.setStroke(Math.max(1, h.px(1.5f)), 0x73FFFFFF);
        t.setBackground(g);
        t.setTextColor(chosen ? NAVY : Color.WHITE);
        t.setTypeface(chosen ? semi : regular);
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        overlay.addView(v, p);
    }
}
