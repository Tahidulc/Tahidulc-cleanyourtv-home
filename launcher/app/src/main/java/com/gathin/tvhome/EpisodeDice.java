package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Surprise me: TV shows' tab of moods (as Movies' Surprise me) (Sitcom classics, Animated and silly, British laughs, Crime of the week, Sci-fi
 * one-offs, Cartoon comfort), each opening a spin that picks one episode of one show for that mood, from the shows on
 * the TV's own streaming apps: any episode, a top-rated one (from the best-rated third of a season), or the first
 * episode of a show not yet watched here; any length, or under 25 minutes. The reel of posters spins and stops on the
 * show, with the episode (season, number, title, its rating and length, and what happens). Play opens the show as the
 * page's posters do; Roll Again picks another. Up and Down between the moods, the options and the buttons; Left and
 * Right along them; OK chooses; Back closes.
 */
final class EpisodeDice {
    private EpisodeDice() { }

    /** {name, line under it, the shows ("Name|first year" each, ";" between)}. */
    static final String[][] MOODS = {
            {"Sitcom classics", "The comfort sitcoms you can drop into anywhere",
                    "Friends|1994;The Office|2005;Brooklyn Nine-Nine|2013;Parks and Recreation|2009;Seinfeld|1989;Frasier|1993;"
                            + "How I Met Your Mother|2005;The Big Bang Theory|2007;Modern Family|2009;Scrubs|2001;New Girl|2011;"
                            + "Community|2009;Malcolm in the Middle|2000;Everybody Loves Raymond|1996;Two and a Half Men|2003;"
                            + "The Fresh Prince of Bel-Air|1990;It's Always Sunny in Philadelphia|2005;Superstore|2015;"
                            + "Young Sheldon|2017;Schitt's Creek|2015;Abbott Elementary|2021;What We Do in the Shadows|2019"},
            {"Animated & silly", "The Simpsons, Family Guy, South Park and friends",
                    "The Simpsons|1989;Family Guy|1999;South Park|1997;Futurama|1999;American Dad!|2005;Bob's Burgers|2011;"
                            + "Rick and Morty|2013;King of the Hill|1997;Archer|2009;BoJack Horseman|2014;The Cleveland Show|2009;"
                            + "Solar Opposites|2020;Big Mouth|2017;Disenchantment|2018;Inside Job|2021;Harley Quinn|2019"},
            {"British laughs", "Only Fools, Peep Show, Gavin & Stacey and more",
                    "Only Fools and Horses|1981;The Inbetweeners|2008;Peep Show|2003;Gavin & Stacey|2007;Fawlty Towers|1975;"
                            + "The IT Crowd|2006;Friday Night Dinner|2011;Ghosts|2019;Red Dwarf|1988;The Office|2001;"
                            + "Not Going Out|2006;Derry Girls|2018;Detectorists|2014;Still Game|2002;Stath Lets Flats|2018;"
                            + "Man Like Mobeen|2017;Count Arthur Strong|2013;Black Books|2000"},
            {"Crime of the week", "One case, one episode, solved by the end",
                    "Columbo|1971;Law & Order: Special Victims Unit|1999;NCIS|2003;House|2004;Death in Paradise|2011;"
                            + "Midsomer Murders|1997;Criminal Minds|2005;Monk|2002;Psych|2006;Castle|2009;"
                            + "Agatha Christie's Poirot|1989;Elementary|2012;Murder, She Wrote|1984;Lucifer|2016;"
                            + "CSI: Crime Scene Investigation|2000;Father Brown|2013"},
            {"Sci-fi one-offs", "Stories that start and end in one go",
                    "Black Mirror|2011;The Twilight Zone|1959;Love, Death & Robots|2019;Doctor Who|2005;"
                            + "Star Trek: The Next Generation|1987;The X-Files|1993;Inside No. 9|2014;"
                            + "Philip K. Dick's Electric Dreams|2017;Tales from the Loop|2020;The Outer Limits|1995"},
            {"Cartoon comfort", "Feel-good animation for any age",
                    "Bluey|2018;SpongeBob SquarePants|1999;Avatar: The Last Airbender|2005;Gravity Falls|2012;"
                            + "Adventure Time|2010;Phineas and Ferb|2007;The Amazing World of Gumball|2011;Hey Duggee|2014;"
                            + "Steven Universe|2013;Regular Show|2010;Kipo and the Age of Wonderbeasts|2020"}};
    private static final String[] PICKS = {"Any episode", "A top-rated one", "A first episode"};
    private static final int NAVY = 0xFF10157E, YELLOW = 0xFFFFD04D;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static EpisodeDice shown;

    private HomeV2 h;
    private Context c;
    private FrameLayout overlay;
    private final List<TextView> moodViews = new ArrayList<>(), pickViews = new ArrayList<>(), buttons = new ArrayList<>();
    private TextView lengthView, name, episode, line, about;
    /** After "on" in the line of details: Debrid Stream's mark, then the services that carry the show (DiceRoll.marks). */
    private LinearLayout marks;
    /** The reel thrown like dice. */
    private DiceRoll dice;
    private final ImageView[] reel = new ImageView[5];
    private int mood, how, row = 2, col;
    private boolean quick, rolling;
    private final List<Bitmap> pics = new ArrayList<>();
    private PagesV2.Title pick;
    /** The picked episode, for Play to find in Debrid Stream: its season, number and name. */
    private int pickSeason, pickEpisode;
    private String pickEpisodeName;
    private android.graphics.Bitmap pickStill;
    private int gen;
    private final Random random = new Random();

    // ---- TV shows' tab: the moods ---------------------------------------------------------------------------------

    /**
     * The moods as cards of their own (MoodArt: each mood's colours, its sign and its name), not a show's poster, which
     * looked like a spin for that one show. OK on one spins for it.
     */
    static PagesV2.Row row(HomeV2 h) {
        List<PagesV2.Title> items = new ArrayList<>();
        for (int i = 0; i < MOODS.length; i++) {
            PagesV2.Title t = new PagesV2.Title();
            t.title = MOODS[i][0];
            t.overview = MOODS[i][1] + ". OK spins for an episode, from the shows on your streaming apps.";
            t.year = "";
            t.what = "Surprise me";
            t.tv = true;
            t.poster = "mood:tv:" + i + ":p";
            t.backdrop = "mood:tv:" + i + ":w";
            int m = i;
            t.ok = () -> open(h, m);
            items.add(t);
        }
        return PagesV2.row(null, null, false, items);
    }

    /** A show's TMDB id from its name and first year ("Friends|1994"), looked up once and kept. 0 when not found. */
    private static int id(HomeV2 h, String show) {
        SharedPreferences kept = h.a.getSharedPreferences("dice_ids", Context.MODE_PRIVATE);
        if (kept.contains(show)) return kept.getInt(show, 0);
        String[] p = show.split("\\|");
        int found = 0;
        boolean failed = false;
        try {
            JSONArray res = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/search/tv?query=" + URLEncoder.encode(p[0], "UTF-8")
                    + (p.length > 1 ? "&first_air_date_year=" + p[1] : "") + "&api_key=" + h.tmdbKey)).optJSONArray("results");
            if (res != null && res.length() > 0) found = res.getJSONObject(0).optInt("id");
        } catch (Exception e) {
            failed = true;
        }
        if (!failed) kept.edit().putInt(show, found).apply();
        return found;
    }

    // ---- the roll ---------------------------------------------------------------------------------------------------

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
        EpisodeDice s = new EpisodeDice();
        s.h = h;
        s.c = h.a;
        s.mood = mood;
        shown = s;
        s.build();
        s.roll();
    }

    private void hide() {
        if (shown == this) shown = null;
        gen++;
        dice.stop();
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
        TextView crumb = h.text(16, semi, 0xFF4F8BFF);
        crumb.setText("TV SHOWS");
        crumb.setLetterSpacing(0.3f);
        crumb.setGravity(Gravity.CENTER);
        place(crumb, 0, 52, 1920, 26);
        TextView head = h.text(54, heavy, Color.WHITE);
        head.setText("Surprise me");
        head.setGravity(Gravity.CENTER);
        place(head, 0, 80, 1920, 74);
        LinearLayout moods = new LinearLayout(c);
        moods.setGravity(Gravity.CENTER);
        for (String[] m : MOODS) moodViews.add(chip(moods, m[0], 19, 50, semi));
        place(moods, 0, 174, 1920, 54);
        LinearLayout opts = new LinearLayout(c);
        opts.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        TextView pl = h.text(14, semi, 0xB3FFFFFF);
        pl.setText("PICK   ");
        pl.setLetterSpacing(0.16f);
        opts.addView(pl);
        for (String p : PICKS) pickViews.add(chip(opts, p, 17, 44, semi));
        TextView ll = h.text(14, semi, 0xB3FFFFFF);
        ll.setText("      LENGTH   ");
        ll.setLetterSpacing(0.16f);
        opts.addView(ll);
        lengthView = chip(opts, "", 17, 44, semi);
        place(opts, 0, 242, 1920, 50);
        int[][] at = {{256, 392, 200, 300}, {550, 387, 210, 315}, {810, 316, 300, 450}, {1160, 387, 210, 315}, {1464, 392, 200, 300}};
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
        place(ring, 810, 316, 300, 450);
        dice = new DiceRoll(h, overlay, reel, ring);
        name = h.text(30, semi, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        place(name, 0, 778, 1920, 44);
        episode = h.text(24, semi, YELLOW);
        episode.setGravity(Gravity.CENTER);
        place(episode, 0, 822, 1920, 36);
        LinearLayout meta = new LinearLayout(c);
        meta.setGravity(Gravity.CENTER);
        line = h.text(18, regular, 0xD9FFFFFF);
        meta.addView(line);
        marks = new LinearLayout(c);
        marks.setGravity(Gravity.CENTER_VERTICAL);
        marks.setVisibility(View.GONE);
        meta.addView(marks, new LinearLayout.LayoutParams(-2, h.px(42)));
        place(meta, 0, 856, 1920, 42);
        about = h.text(17, regular, 0xB3FFFFFF);
        about.setGravity(Gravity.CENTER);
        about.setMaxLines(2);
        about.setEllipsize(android.text.TextUtils.TruncateAt.END);
        place(about, 360, 900, 1200, 50);
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER);
        buttons.add(chip(bar, "▶  Play", 21, 60, semi));
        buttons.add(chip(bar, "🎲  Roll Again", 21, 60, semi));
        place(bar, 0, 956, 1920, 66);
        paint();
    }

    private TextView chip(LinearLayout into, String words, int sp, int tall, Typeface face) {
        TextView t = h.text(sp, face, Color.WHITE);
        t.setText(words);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(h.px(20), 0, h.px(20), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(tall));
        lp.leftMargin = lp.rightMargin = h.px(6);
        into.addView(t, lp);
        return t;
    }

    /**
     * A new pick, off the main thread: the mood's shows on the TV's apps, shuffled (a first episode only from shows not
     * watched here), then for the first that has one, an episode as asked: any (seasons weighed by their length), a
     * top-rated one, or the first; under 25 minutes when asked. The reel then spins onto that show.
     */
    private void roll() {
        int my = ++gen;
        rolling = true;
        name.setText("");
        episode.setText("");
        line.setText("");
        marks.setVisibility(View.GONE);
        about.setText("Rolling the dice…");
        dice.start(new ArrayList<>(pics));
        int m = mood, w = how;
        boolean q = quick;
        IO.execute(() -> {
            List<String> shows = new ArrayList<>();
            Collections.addAll(shows, MOODS[m][2].split(";"));
            Collections.shuffle(shows, random);
            java.util.Map<String, LocalDate> seen = ViewingLog.watched(c);
            List<JSONObject> onApps = new ArrayList<>();
            Object[] got = null;
            for (String show : shows) {
                int id = id(h, show);
                JSONObject d = id > 0 ? YourShows.details(h.tmdbKey, id) : null;
                if (d == null || HomeV2.onYourApps(h, YourShows.gb(d)) == null) continue;
                onApps.add(d);
                if (got == null && !(w == 2 && seen.containsKey("tv:" + id))) got = episode(d, w, q);
                if (got != null && onApps.size() >= 5) break;
            }
            // The reel: the picked show in the middle, the others round it.
            List<JSONObject> faces = new ArrayList<>(onApps);
            if (got != null) { faces.remove((JSONObject) got[0]); faces.add(0, (JSONObject) got[0]); }
            List<Bitmap> bitmaps = new ArrayList<>();
            for (JSONObject d : faces.subList(0, Math.min(6, faces.size()))) {
                String p = d.optString("poster_path", "");
                bitmaps.add(p.isEmpty() || "null".equals(p) ? null : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w342" + p, h.px(300)));
            }
            Object[] result = got;
            MAIN.post(() -> {
                if (my != gen || shown != this) return;
                pics.clear();
                pics.addAll(bitmaps);
                land(my, result);
            });
        });
    }

    /** {the show, its title for Play, "S5 E12 “Homer at the Bat”", the line, what happens} for one show; null if none fits. */
    private Object[] episode(JSONObject d, int how, boolean quick) {
        int id = d.optInt("id");
        LocalDate today = LocalDate.now();
        JSONObject last = d.optJSONObject("last_episode_to_air");
        int lastS = last == null ? 0 : last.optInt("season_number"), lastE = last == null ? 0 : last.optInt("episode_number");
        // The seasons out so far, each with its episodes out so far.
        List<int[]> seasons = new ArrayList<>();
        JSONArray ss = d.optJSONArray("seasons");
        for (int i = 0; ss != null && i < ss.length(); i++) {
            JSONObject s = ss.optJSONObject(i);
            int n = s == null ? 0 : s.optInt("season_number"), count = s == null ? 0 : s.optInt("episode_count");
            if (n < 1 || count < 1) continue;
            if (lastS > 0 && n > lastS) continue;
            if (n == lastS && lastE > 0) count = Math.min(count, lastE);
            String air = s.optString("air_date", "");
            if (!air.isEmpty() && !"null".equals(air) && LocalDate.parse(air).isAfter(today)) continue;
            seasons.add(new int[]{n, count});
        }
        if (seasons.isEmpty()) return null;
        int typical = 0;
        JSONArray rt = d.optJSONArray("episode_run_time");
        if (rt != null && rt.length() > 0) typical = rt.optInt(0);
        if (quick && typical > 30) return null;
        for (int tries = 0; tries < 3; tries++) {
            int season, number;
            if (how == 2) {
                season = seasons.get(0)[0];
                number = 1;
            } else {
                int total = 0;
                for (int[] s : seasons) total += s[1];
                int r = random.nextInt(total);
                int[] chosen = seasons.get(0);
                for (int[] s : seasons) { if (r < s[1]) { chosen = s; break; } r -= s[1]; }
                season = chosen[0];
                number = 1 + random.nextInt(chosen[1]);
            }
            JSONObject ep = null;
            try {
                if (how == 1) {
                    // The best-rated third of the season (TMDB's ratings, with enough votes), one at random.
                    JSONArray eps = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id + "/season/" + season
                            + "?language=en-GB&api_key=" + h.tmdbKey)).optJSONArray("episodes");
                    List<JSONObject> rated = new ArrayList<>();
                    for (int i = 0; eps != null && i < eps.length(); i++) {
                        JSONObject e = eps.getJSONObject(i);
                        String air = e.optString("air_date", "");
                        if (air.isEmpty() || "null".equals(air) || LocalDate.parse(air).isAfter(today)) continue;
                        if (quick && e.optInt("runtime") > 30) continue;
                        rated.add(e);
                    }
                    rated.sort((x, y) -> Double.compare(y.optDouble("vote_average"), x.optDouble("vote_average")));
                    List<JSONObject> best = rated.subList(0, Math.max(Math.min(1, rated.size()), rated.size() / 3));
                    if (!best.isEmpty()) ep = best.get(random.nextInt(best.size()));
                } else {
                    ep = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id + "/season/" + season + "/episode/" + number
                            + "?language=en-GB&api_key=" + h.tmdbKey));
                }
            } catch (Exception e) {
                ep = null;
            }
            if (ep == null) continue;
            int runtime = ep.optInt("runtime") > 0 ? ep.optInt("runtime") : typical;
            if (quick && runtime > 30) { if (how == 2) return null; continue; }
            PagesV2.Title t = new PagesV2.Title();
            t.id = id;
            t.tv = true;
            t.title = d.optString("name", "");
            String poster = d.optString("poster_path", ""), back = d.optString("backdrop_path", "");
            t.poster = poster.isEmpty() || "null".equals(poster) ? null : "https://image.tmdb.org/t/p/w342" + poster;
            t.backdrop = back.isEmpty() || "null".equals(back) ? null : "https://image.tmdb.org/t/p/w1280" + back;
            t.vote = d.optDouble("vote_average", 0);
            t.year = "";
            t.overview = d.optString("overview", "");
            int s = ep.optInt("season_number", season), n = ep.optInt("episode_number", number);
            String title = ep.optString("name", "");
            String head = "S" + s + " E" + n + (title.isEmpty() || title.matches("(?i)episode \\d+") ? "" : "  “" + title + "”");
            List<String> bits = new ArrayList<>();
            double vote = ep.optDouble("vote_average", 0);
            if (vote > 0) bits.add(String.format(Locale.UK, "★ %.1f", vote));
            if (runtime > 0) bits.add(runtime + " min");
            String air = ep.optString("air_date", "");
            if (air.length() >= 4) bits.add("first shown " + air.substring(0, 4));
            String app = null;
            JSONObject gb = YourShows.gb(d);
            JSONArray flat = gb == null ? null : gb.optJSONArray("flatrate");
            for (int i = 0; flat != null && i < flat.length() && app == null; i++) {
                JSONObject pr = flat.optJSONObject(i);
                String a = pr == null ? null : HomeV2.serviceApp(String.valueOf(pr.optInt("provider_id")));
                if (a != null && ReleaseAlerts.installed(c, a)) app = pr.optString("provider_name", null);
            }
            t.what = head;
            // The episode's own picture, for the cover while Debrid Stream finds it.
            String still = ep.optString("still_path", "");
            android.graphics.Bitmap stillPic = still.isEmpty() || "null".equals(still) ? null
                    : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w780" + still, 960);
            return new Object[]{d, t, head, String.join("  ·  ", bits), ep.optString("overview", ""), app, s, n,
                    ep.optString("name", ""), stillPic};
        }
        return null;
    }

    /** The dice land: the picked show on the middle card, the mood's others round it; then the episode. */
    private void land(int my, Object[] result) {
        if (my != gen || shown != this) return;
        Bitmap[] last = new Bitmap[5];
        int n = pics.size();
        for (int i = 0; i < 5; i++) last[i] = n == 0 ? DiceRoll.die(1 + random.nextInt(6)) : pics.get(((i - 2) % n + n) % n);
        dice.land(last, () -> {
            if (my != gen || shown != this) return;
            rolling = false;
            if (pics.isEmpty() || result == null) {
                about.setText(pics.isEmpty() ? "None of these shows is on your streaming apps right now. Try another mood."
                        : "No episode fits that right now. Try another option, or roll again.");
                pick = null;
                return;
            }
            pick = (PagesV2.Title) result[1];
            name.setText(pick.title);
            episode.setText((String) result[2]);
            // Where Play opens it: Debrid Stream's mark first, then the services the show is on; without any mark,
            // the service's name.
            boolean on = DiceRoll.marks(h, marks, YourShows.gb((JSONObject) result[0]));
            String details = (String) result[3], app = (String) result[5];
            line.setText(on ? (details.isEmpty() ? "on" : details + "  ·  on")
                    : app != null ? (details.isEmpty() ? "on " + app : details + "  ·  on " + app) : details);
            pickSeason = (Integer) result[6];
            pickEpisode = (Integer) result[7];
            pickEpisodeName = (String) result[8];
            pickStill = (android.graphics.Bitmap) result[9];
            String what = (String) result[4];
            about.setText(what.isEmpty() ? MOODS[mood][0] + " · " + PICKS[how] : what);
            for (View v : new View[]{name, episode, line, marks, about}) {
                v.setAlpha(0f);
                v.setTranslationY(h.px(10));
                v.animate().alpha(1f).translationY(0).setDuration(320).start();
            }
        });
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
        int size = row == 0 ? moodViews.size() : row == 1 ? pickViews.size() + 1 : buttons.size();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) col--;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < size - 1) col++;
        else if (code == KeyEvent.KEYCODE_DPAD_UP && row > 0) { row--; col = row == 0 ? mood : how; }
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && row < 2) { row++; col = row == 1 ? how : 0; }
        paint();
        return true;
    }

    private void press() {
        if (row == 0) { mood = col; roll(); }
        else if (row == 1 && col < PICKS.length) { how = col; roll(); }
        else if (row == 1) { quick = !quick; roll(); }
        else if (col == 0) {
            if (pick != null && !rolling) {
                PagesV2.Title t = pick;
                hide();
                // In Debrid Stream the helper goes on from the series' page to the episode itself (its season, then
                // the episode, left highlighted for OK); anywhere else, the episode to look for is said.
                if (ReleaseAlerts.installed(c, ReleaseAlerts.DEBRID) && AppSearchService.running()) {
                    AppSearchService.wantEpisode(pickSeason, pickEpisode, pickEpisodeName, pickStill);
                } else {
                    android.widget.Toast.makeText(c.getApplicationContext(), t.title + ": " + t.what, android.widget.Toast.LENGTH_LONG).show();
                }
                h.openTitle(t);
            }
        } else roll();
        paint();
    }

    private void paint() {
        for (int i = 0; i < moodViews.size(); i++) style(moodViews.get(i), i == mood, row == 0 && i == col);
        for (int i = 0; i < pickViews.size(); i++) style(pickViews.get(i), i == how, row == 1 && i == col);
        lengthView.setText(quick ? "Under 25 min" : "Any length");
        style(lengthView, quick, row == 1 && col == PICKS.length);
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
