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
 * opens it (as the panels do), Spin again picks another. Up and Down between the moods, who and the buttons; Left and
 * Right along them; OK chooses; Back closes.
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
    private static final int NAVY = 0xFF10157E, YELLOW = 0xFFFFD04D;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Surprise shown;

    private HomeV2 h;
    private Context c;
    private FrameLayout overlay;
    private final List<TextView> moodViews = new ArrayList<>(), whoViews = new ArrayList<>(), buttons = new ArrayList<>();
    private TextView timeView, name, line, why;
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
        View o = overlay;
        o.animate().alpha(0f).setDuration(180).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    private void build() {
        Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold), semi = c.getResources().getFont(R.font.inter_semibold),
                regular = c.getResources().getFont(R.font.inter_regular);
        overlay = new FrameLayout(c);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0A0E4A, 0xFF080A32, 0xFF04061C}));
        overlay.setClickable(true);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        TextView crumb = h.text(16, semi, 0xFFFF4FA3);
        crumb.setText("MOVIES");
        crumb.setLetterSpacing(0.3f);
        crumb.setGravity(Gravity.CENTER);
        place(crumb, 0, 60, 1920, 26);
        TextView head = h.text(54, heavy, Color.WHITE);
        head.setText("Surprise me");
        head.setGravity(Gravity.CENTER);
        place(head, 0, 88, 1920, 74);
        LinearLayout moods = new LinearLayout(c);
        moods.setGravity(Gravity.CENTER);
        for (String[] m : MOODS) moodViews.add(chip(moods, m[0], 20, 50, semi));
        place(moods, 0, 186, 1920, 54);
        LinearLayout whoRow = new LinearLayout(c);
        whoRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        TextView wl = h.text(14, semi, 0xB3FFFFFF);
        wl.setText("WHO'S WATCHING   ");
        wl.setLetterSpacing(0.16f);
        whoRow.addView(wl);
        for (String w : WHO) whoViews.add(chip(whoRow, w, 17, 44, semi));
        TextView tl = h.text(14, semi, 0xB3FFFFFF);
        tl.setText("      TIME   ");
        tl.setLetterSpacing(0.16f);
        whoRow.addView(tl);
        timeView = chip(whoRow, "", 17, 44, semi);
        place(whoRow, 0, 256, 1920, 50);
        int[][] at = {{256, 412, 200, 300}, {550, 407, 210, 315}, {810, 336, 300, 450}, {1160, 407, 210, 315}, {1464, 412, 200, 300}};
        for (int i = 0; i < 5; i++) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setBackground(h.fill(i == 2 ? 20 : 14, 0xFF141A5C));
            v.setClipToOutline(true);
            v.setOutlineProvider(h.round(i == 2 ? 20 : 14));
            v.setAlpha(i == 2 ? 1f : i == 1 || i == 3 ? 0.6f : 0.3f);
            if (i != 2) v.setRotationY(i < 2 ? 16 : -16);
            reel[i] = v;
            place(v, at[i][0], at[i][1], at[i][2], at[i][3]);
        }
        View ring = new View(c);
        GradientDrawable rg = new GradientDrawable();
        rg.setCornerRadius(h.px(20));
        rg.setStroke(h.px(6), Color.WHITE);
        ring.setBackground(rg);
        place(ring, 810, 336, 300, 450);
        name = h.text(30, semi, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        place(name, 0, 800, 1920, 44);
        line = h.text(20, regular, 0xD9FFFFFF);
        line.setGravity(Gravity.CENTER);
        place(line, 0, 846, 1920, 30);
        why = h.text(19, semi, YELLOW);
        why.setGravity(Gravity.CENTER);
        place(why, 0, 880, 1920, 30);
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER);
        buttons.add(chip(bar, "▶  Play", 21, 62, semi));
        buttons.add(chip(bar, "🎲  Spin again", 21, 62, semi));
        place(bar, 0, 926, 1920, 70);
        paint();
    }

    private TextView chip(LinearLayout into, String words, int sp, int tall, Typeface face) {
        TextView t = h.text(sp, face, Color.WHITE);
        t.setText(words);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(h.px(22), 0, h.px(22), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(tall));
        lp.leftMargin = lp.rightMargin = h.px(6);
        into.addView(t, lp);
        return t;
    }

    /** A new pick: the films for the mood read (a page at random of the first three), then the reel spins onto one. */
    private void spin() {
        int my = ++gen;
        spinning = true;
        name.setText("");
        line.setText("");
        why.setText("Spinning…");
        int m = mood, w = who;
        boolean bed = byBed;
        IO.execute(() -> {
            List<PagesV2.Title> got = new ArrayList<>();
            Map<String, java.time.LocalDate> seen = ViewingLog.watched(c);
            for (int page : new int[]{1 + random.nextInt(3), 1}) {
                for (PagesV2.Title t : PagesV2.tmdb(h.tmdbKey, query(c, m, w, bed, page), false)) {
                    if (!seen.containsKey("mv:" + t.id) && !contains(got, t.id)) got.add(t);
                }
                if (got.size() >= 6) break;
            }
            Collections.shuffle(got, random);
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
            int rt = runtime;
            MAIN.post(() -> {
                if (my != gen || shown != this) return;
                pool = new ArrayList<>(five);
                pics.clear();
                pics.addAll(bitmaps);
                roll(my, 0, rt);
            });
        });
    }

    private static boolean contains(List<PagesV2.Title> list, int id) {
        for (PagesV2.Title t : list) if (t.id == id) return true;
        return false;
    }

    /** The reel's turns: the posters stepping along, slowing, stopping with the pick (the first) in the middle. */
    private void roll(int my, int step, int runtime) {
        if (my != gen || shown != this) return;
        if (pool.isEmpty()) {
            spinning = false;
            why.setText("Nothing on your apps for this right now. Try another mood, or turn off Done by bed.");
            for (ImageView v : reel) v.setImageDrawable(null);
            pick = null;
            return;
        }
        int steps = 12, n = pics.size();
        int centre = step >= steps ? 0 : (steps - step) % n;
        for (int i = 0; i < 5; i++) {
            Bitmap b = pics.get(((centre + i - 2) % n + n) % n);
            if (b != null) reel[i].setImageBitmap(b); else reel[i].setImageDrawable(null);
        }
        if (step < steps) {
            MAIN.postDelayed(() -> roll(my, step + 1, runtime), 60 + step * 18L);
            return;
        }
        spinning = false;
        pick = pool.get(0);
        reel[2].setScaleX(0.94f);
        reel[2].setScaleY(0.94f);
        reel[2].animate().scaleX(1f).scaleY(1f).setDuration(260).start();
        name.setText(pick.title);
        String vote = pick.vote > 0 ? String.format(Locale.UK, " · ★ %.1f", pick.vote) : "";
        line.setText(String.join(" · ", nonEmpty(pick.year, pick.what, runtime > 0 ? runtime / 60 + "h " + String.format(Locale.UK, "%02d", runtime % 60) + "m" : "")) + vote);
        why.setText((runtime > 0 ? FitsTonight.heroLine(c, runtime) + " · " : "") + MOODS[mood][0] + " · not watched on this TV yet");
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
        else spin();
        paint();
    }

    private void paint() {
        for (int i = 0; i < moodViews.size(); i++) style(moodViews.get(i), i == mood, row == 0 && i == col);
        for (int i = 0; i < whoViews.size(); i++) style(whoViews.get(i), i == who, row == 1 && i == col);
        timeView.setText(byBed ? "Done by " + String.format(Locale.UK, "%02d:%02d", FitsTonight.bedMinutes(c) / 60 % 24, FitsTonight.bedMinutes(c) % 60) : "Any length");
        style(timeView, byBed, row == 1 && col == WHO.length);
        for (int i = 0; i < buttons.size(); i++) style(buttons.get(i), false, row == 2 && i == col);
    }

    /** chosen: filled white; focused: a white ring (filled bright when also chosen). */
    private void style(TextView t, boolean chosen, boolean focused) {
        GradientDrawable g = h.fill(28, focused ? Color.WHITE : chosen ? 0xE6FFFFFF : 0x14FFFFFF);
        if (!focused && !chosen) g.setStroke(Math.max(1, h.px(1.5f)), 0x73FFFFFF);
        if (focused && !chosen && row != 2) g.setStroke(h.px(3), YELLOW);
        t.setBackground(g);
        t.setTextColor(focused || chosen ? NAVY : Color.WHITE);
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        overlay.addView(v, p);
    }
}
