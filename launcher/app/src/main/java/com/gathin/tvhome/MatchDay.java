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
import android.view.View;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Match Day on Home (launcher v2): on a day with a game that matters, the top of Home becomes that game: both crests,
 * the competition, a countdown to kick-off with each side's last five, the channel and when coverage starts, then the
 * live score and clock, then the result, with Watch, Live Centre and a reminder. Only the top changes: For You, the
 * six boxes, the rail and the top bar stay where they are; Down goes to For You (a small chip by its heading says Up
 * comes back), Back hides Match Day until tomorrow.
 *
 * Which game: Manchester United's first; then the Premier League, Champions League, EFL Cup and FA Cup games live
 * on British TV (Sky's guide lists them); then the 3pm Saturday Premier League games; then England's; then
 * Barcelona, Real Madrid, Bayern Munich, PSG, AC Milan, Inter and Juventus. Each shows from the morning of the day
 * until three hours after its kick-off; with several, the one live now, else the next to start.
 */
final class MatchDay {
    private static final String MAN_UNITED = "360", ENGLAND = "448";
    private static final Set<String> CLUBS = new HashSet<>(Arrays.asList("83", "86", "132", "160", "103", "110", "111"));
    private static final String[] MAJOR = {"eng.1|PL", "uefa.champions|UCL", "eng.league_cup|EFL CUP", "eng.fa|FA CUP"};
    private static final Set<String> MAJOR_SLUGS = new HashSet<>(Arrays.asList("eng.1", "uefa.champions", "eng.league_cup", "eng.fa"));
    private static final String[] CLUB_LEAGUES = {"esp.1|LALIGA", "ita.1|SERIE A", "ger.1|BUNDESLIGA", "fra.1|LIGUE 1",
            "uefa.europa|UEL", "esp.copa_del_rey|COPA", "ita.coppa_italia|COPPA", "ger.dfb_pokal|POKAL", "fra.coupe_de_france|CDF"};
    private static final String[] ENGLAND_LEAGUES = {"fifa.friendly|FRIENDLY", "uefa.nations|NATIONS LEAGUE",
            "fifa.worldq.uefa|WC QUALIFIER", "uefa.euroq|EURO QUALIFIER", "uefa.euro|EURO", "fifa.world|WORLD CUP"};
    /** The Premier League's bigger clubs, first among the 3pm games. */
    private static final Set<String> BIG = new HashSet<>(Arrays.asList("359", "363", "364", "382", "367", "361", "362"));
    /** A game shows until this long after its kick-off. */
    private static final long AFTER_MS = 3 * 3600_000L;

    /** What Match Day shows. */
    static final class Pick {
        ScoresTicker.Game g;
        /**
         * 1 Manchester United; 2 a Premier League, Champions League, EFL Cup or FA Cup game on British TV; 3 and 4 a 3pm
         * Saturday Premier League game (a bigger club's, then the rest); 5 England; 6 a followed club.
         */
        int rank;
        List<String> watch;
        String channel, channelLogo, picture, listedName;
        long coverFrom;
        Bitmap league, home, away, logo;
        String homeForm = "", awayForm = "";
        /** A 3pm Saturday Premier League game: not shown live on British TV; followed through Soccer Saturday. */
        boolean blackout;
        /** A Champions League game Prime Video shows in Britain (the evening's one game Sky's guide does not list). */
        boolean prime;
        /** Each side's top scorer in the competition (ESPN's leaders), and his cut-out in the club's kit (TheSportsDB). */
        String homeStar, awayStar;
        Bitmap homePlayer, awayPlayer;
    }

    private final HomeV2 h;
    private final Context c;
    final FrameLayout panel;
    final TextView chip;
    /** The two clubs' colours behind the crests, where Sky's guide has no picture for the game. */
    final Backdrop backdrop;
    private static final String PRIME = "com.amazon.amazonvideo.livingroom";
    private final ImageView leagueView, homeCrest, awayCrest, channelView;
    private final TextView state, clock, subtitle, homeName, awayName, middle, line, channelText;
    private final LinearLayout countdown, form, events, buttons;
    private final TextView[] digits = new TextView[3];
    private final List<View> buttonViews = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Typeface heavy, semi, regular;
    private Pick pick;
    private boolean running;
    /** Told whenever Match Day comes, changes or goes, so Home can move its focus. */
    Runnable changed;

    MatchDay(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
        panel = new FrameLayout(c);
        panel.setVisibility(View.GONE);
        backdrop = new Backdrop(c);
        backdrop.setVisibility(View.GONE);

        LinearLayout top = row();
        leagueView = new ImageView(c);
        leagueView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        top.addView(leagueView, new LinearLayout.LayoutParams(h.px(48), h.px(48)));
        state = h.text(26, heavy, Color.WHITE);
        state.setPadding(h.px(14), h.px(2), h.px(14), h.px(2));
        state.setBackground(h.fill(4, 0xFFE8112D));
        state.getPaint().setTextSkewX(-0.2f);
        state.setLetterSpacing(0.06f);
        top.addView(state, gap(14));
        clock = h.text(30, heavy, 0xFFFFD04D);
        top.addView(clock, gap(14));
        subtitle = h.text(19, semi, 0xE6FFFFFF);
        subtitle.setLetterSpacing(0.12f);
        subtitle.setAllCaps(true);
        top.addView(subtitle, gap(16));
        place(top, 0, 0, 1320, 50);

        LinearLayout teams = row();
        homeCrest = crest();
        teams.addView(homeCrest, new LinearLayout.LayoutParams(h.px(96), h.px(96)));
        homeName = name();
        teams.addView(homeName, gap(20));
        middle = h.text(36, heavy, Color.WHITE);
        middle.setGravity(Gravity.CENTER);
        teams.addView(middle, gap(20));
        awayName = name();
        teams.addView(awayName, gap(20));
        awayCrest = crest();
        LinearLayout.LayoutParams ac = new LinearLayout.LayoutParams(h.px(96), h.px(96));
        ac.leftMargin = h.px(20);
        teams.addView(awayCrest, ac);
        // The rows packed a little tighter than they were, so the buttons end higher and For You's heading under them
        // has room to breathe.
        place(teams, 0, 56, 1320, 100);

        LinearLayout third = row();
        countdown = row();
        TextView k = h.text(15, semi, 0xD9FFFFFF);
        k.setText("KICK-OFF IN");
        k.setLetterSpacing(0.14f);
        countdown.addView(k);
        String[] units = {"HRS", "MINS", "SECS"};
        for (int i = 0; i < 3; i++) {
            // Each box: the two digits and the unit under them, both centred across the box and the pair centred
            // down it (added at their own width: a vertical row's default would stretch them and left-align the text).
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            GradientDrawable g = h.fill(12, 0x4D000000);
            g.setStroke(Math.max(1, h.px(1)), 0x40FFFFFF);
            box.setBackground(g);
            digits[i] = h.text(32, heavy, Color.WHITE);
            digits[i].setFontFeatureSettings("tnum");
            digits[i].setLineSpacing(0, 1f);
            digits[i].setGravity(Gravity.CENTER);
            box.addView(digits[i], new LinearLayout.LayoutParams(-2, -2));
            TextView u = h.text(11, semi, 0xCCFFFFFF);
            u.setText(units[i]);
            u.setLetterSpacing(0.16f);
            u.setLineSpacing(0, 1f);
            u.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-2, -2);
            up.topMargin = h.px(3);
            box.addView(u, up);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(h.px(78), h.px(58));
            bp.leftMargin = h.px(10);
            countdown.addView(box, bp);
        }
        third.addView(countdown);
        form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        third.addView(form, gap(30));
        events = row();
        third.addView(events);
        place(third, 0, 162, 1320, 58);

        LinearLayout fourth = row();
        channelView = new ImageView(c);
        channelView.setAdjustViewBounds(true);
        channelView.setMaxHeight(h.px(32));
        channelView.setMaxWidth(h.px(360));
        fourth.addView(channelView, new LinearLayout.LayoutParams(-2, h.px(32)));
        channelText = h.text(19, regular, 0xE6FFFFFF);
        fourth.addView(channelText, gap(14));
        line = h.text(19, regular, 0xE6FFFFFF);
        fourth.addView(line, gap(14));
        place(fourth, 0, 226, 1320, 36);

        buttons = row();
        // The lit button grows a little: drawn past its row and the panel rather than clipped at the edge.
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        place(buttons, 0, 266, 1320, 56);

        chip = h.text(17, semi, Color.WHITE);
        chip.setLetterSpacing(0.06f);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(h.px(16), 0, h.px(18), 0);
        chip.setBackground(h.fill(18, 0x33FFFFFF));
        chip.setVisibility(View.GONE);
    }

    // ---- layout helpers -------------------------------------------------------------------------------------------

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout.LayoutParams gap(int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = h.px(left);
        return p;
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        panel.addView(v, p);
    }

    private ImageView crest() {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    private TextView name() {
        TextView t = h.text(64, heavy, Color.WHITE);
        t.getPaint().setTextSkewX(-0.12f);
        t.setSingleLine(true);
        t.setAllCaps(true);
        t.setShadowLayer(h.px(14), 0, h.px(3), 0x66000000);
        return t;
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    /** Whether Match Day has a game to show now (and it was not put away for today). */
    boolean active() {
        return pick != null && !hiddenToday(pick);
    }

    Pick pick() {
        return pick;
    }

    void start() {
        if (running) return;
        running = true;
        refresh.run();
        main.post(tick);
    }

    void stop() {
        running = false;
        main.removeCallbacks(refresh);
        main.removeCallbacks(tick);
    }

    /** Back on Match Day: put away until tomorrow, for this game. */
    void hideToday() {
        if (pick == null) return;
        prefs().edit().putString("hidden", LocalDate.now() + "|" + pick.g.id).apply();
        if (changed != null) changed.run();
    }

    private boolean hiddenToday(Pick p) {
        return (LocalDate.now() + "|" + p.g.id).equals(prefs().getString("hidden", ""));
    }

    private SharedPreferences prefs() {
        return c.getSharedPreferences("match_day", Context.MODE_PRIVATE);
    }

    /** Read again each minute while a game is near or live, else every ten minutes. */
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!running) return;
            io.execute(() -> {
                Pick p = choose();
                main.post(() -> {
                    boolean was = active();
                    String before = pick == null ? null : pick.g.id + pick.g.state;
                    pick = p;
                    if (p != null) bind(p);
                    String after = p == null ? null : p.g.id + p.g.state;
                    boolean differs = before == null ? after != null : !before.equals(after);
                    if (changed != null && (was != active() || differs)) changed.run();
                });
            });
            long soon = pick != null && (pick.g.state == 0 || pick.g.kickoff - System.currentTimeMillis() < 20 * 60_000L) ? 60_000L : 10 * 60_000L;
            main.postDelayed(this, soon);
        }
    };

    /** Each second: the countdown, and the live clock. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            Pick p = pick;
            if (p != null) {
                if (p.g.state == 1) {
                    long left = Math.max(0, p.g.kickoff - System.currentTimeMillis()) / 1000;
                    digits[0].setText(String.format(Locale.UK, "%02d", left / 3600));
                    digits[1].setText(String.format(Locale.UK, "%02d", left / 60 % 60));
                    digits[2].setText(String.format(Locale.UK, "%02d", left % 60));
                } else if (p.g.state == 0) {
                    clock.setText(ScoresTicker.liveClock(p.g));
                }
            }
            main.postDelayed(this, 1000);
        }
    };

    // ---- choosing the game -------------------------------------------------------------------------------------------

    private Pick choose() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long now = System.currentTimeMillis();
        Map<String, ScoresTicker.Game> all = new LinkedHashMap<>();
        for (String[] leagues : new String[][]{MAJOR, CLUB_LEAGUES, ENGLAND_LEAGUES}) {
            for (ScoresTicker.Game g : ScoresTicker.load(leagues, today)) if (g.id != null && !all.containsKey(g.id)) all.put(g.id, g);
        }
        String[] channels = c.getResources().getStringArray(R.array.football_tv_channels);
        Pick best = null;
        for (ScoresTicker.Game g : all.values()) {
            if (g.kickoff == 0 || now > g.kickoff + AFTER_MS) continue;
            // In order: Manchester United; the Premier League, Champions League, EFL Cup and FA Cup on British TV;
            // the 3pm Saturday Premier League games (the bigger clubs' first); England; the followed clubs.
            int rank = MAN_UNITED.equals(g.homeId) || MAN_UNITED.equals(g.awayId) ? 1 : 0;
            Object[] show = null;
            if (rank == 0 && g.slug != null && MAJOR_SLUGS.contains(g.slug)) {
                show = TvGuide.show(channels, name(g, true), name(g, false), g.kickoff);
                if (show != null) rank = 2;
            }
            if (rank == 0 && blackout(g)) rank = BIG.contains(g.homeId) || BIG.contains(g.awayId) ? 3 : 4;
            if (rank == 0) rank = rank(g);
            if (rank == 0) continue;
            if (best == null || better(g, rank, best)) {
                best = new Pick();
                best.g = g;
                best.rank = rank;
                if (show != null) apply(best, show);
            }
        }
        if (best == null) return null;
        best.blackout = blackout(best.g);
        if (best.blackout) {
            // Not live on British TV: no channel for the game itself; Soccer Saturday follows it on Sky Sports News.
            loadImages(best);
            form(best);
            stars(best);
            return best;
        }
        if (best.channel == null) {
            Object[] show = TvGuide.show(channels, name(best.g, true), name(best.g, false), best.g.kickoff);
            if (show != null) apply(best, show);
        }
        // A Champions League game the guide does not list on an evening it lists the others: Prime Video's.
        best.prime = best.channel == null && "uefa.champions".equals(best.g.slug) && TvGuide.listsLive(channels, best.g.kickoff, "live ucl");
        try {
            Object[] w = GoalWatchService.watchForGame(c, best.g);
            if (w != null && w[0] instanceof List) {
                best.watch = new ArrayList<>();
                for (Object o : (List<?>) w[0]) best.watch.add(String.valueOf(o));
                if (best.channel == null && !best.watch.isEmpty()) best.channel = best.watch.get(0);
                if (w.length > 1 && w[1] instanceof Bitmap) best.logo = (Bitmap) w[1];
            }
        } catch (RuntimeException ignored) {
            // no channel from the alerts' lookup: Sky's listing below, else Watch is left out
        }
        // Sky's listing as the way in when the alerts' lookup had none: its UHD channel first, then the listed one.
        if ((best.watch == null || best.watch.isEmpty()) && best.channel != null) {
            best.watch = new ArrayList<>();
            best.watch.add(best.channel);
            if (best.listedName != null && !best.listedName.equals(best.channel)) best.watch.add(best.listedName);
        }
        // The UHD channel's logo (Sky's, its HD box redrawn as UHD) wherever the UHD channel is the one tuned.
        if (best.watch != null && !best.watch.isEmpty() && HomeV2.uhdName(best.watch.get(0)) && best.channelLogo != null
                && !best.channelLogo.endsWith("#uhd")) best.channelLogo = best.channelLogo + "#uhd";
        loadImages(best);
        if (best.logo == null && best.channelLogo != null) best.logo = HomeV2.trim(ScoresTicker.cachedLogo(c, best.channelLogo, h.px(360)));
        form(best);
        stars(best);
        return best;
    }

    /**
     * Without Sky's picture for the game, the launcher makes its own as a broadcaster would: each side's top scorer
     * in the competition, cut out in the club's kit, standing in front of the club's colours.
     */
    private void stars(Pick p) {
        // TheSportsDB's artwork for the fixture: always for the Champions League, else where Sky has no picture.
        if (p.picture == null || "uefa.champions".equals(p.g.slug)) {
            String art = artwork(p.g);
            if (art != null) p.picture = art;
        }
        if (p.picture != null) return;
        int tall = h.px(652);
        if (p.homeStar != null) p.homePlayer = cutout(p.homeStar, name(p.g, true), tall);
        if (p.awayStar != null) p.awayPlayer = cutout(p.awayStar, name(p.g, false), tall);
    }

    /**
     * TheSportsDB's picture for a fixture (its "thumb", 1280 x 720: the two crests and the competition's mark, as
     * the competition's own graphics), found by the clubs' names as ESPN gives them, then tidied ("AS Roma" as
     * "Roma", "Internazionale" as "Inter"), then by their first words; only a game on the same day counts. Kept per
     * game, found or not, so each game is asked about once.
     */
    private String artwork(ScoresTicker.Game g) {
        if (g.id == null) return null;
        SharedPreferences kept = c.getSharedPreferences("match_art", Context.MODE_PRIVATE);
        if (kept.contains(g.id)) {
            String had = kept.getString(g.id, "");
            return had.isEmpty() ? null : had;
        }
        String day = Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();
        String home = name(g, true), away = name(g, false);
        String found = null;
        for (String[] pair : new String[][]{{home, away}, {tidy(home), tidy(away)}, {first(home), first(away)}}) {
            found = thumb(pair[0], pair[1], day);
            if (found != null) break;
        }
        // Only a definite answer is kept: a failed request is asked again next time.
        if (found != null || !lastFailed) kept.edit().putString(g.id, found == null ? "" : found).apply();
        return found;
    }

    private boolean lastFailed;

    private String thumb(String home, String away, String day) {
        try {
            String q = (home + " vs " + away).replace(' ', '_');
            JSONObject root = new JSONObject(ScoresTicker.get("https://www.thesportsdb.com/api/v1/json/123/searchevents.php?e="
                    + java.net.URLEncoder.encode(q, "UTF-8")));
            lastFailed = false;
            JSONArray ev = root.optJSONArray("event");
            for (int i = 0; ev != null && i < ev.length(); i++) {
                JSONObject e = ev.getJSONObject(i);
                String thumb = e.optString("strThumb", "");
                if (day.equals(e.optString("dateEvent")) && thumb.startsWith("http")) return thumb;
            }
        } catch (Exception e) {
            lastFailed = true;
        }
        return null;
    }

    /** A club's name as TheSportsDB tends to write it: without "AS", "FC" and the like, Internazionale as Inter. */
    private static String tidy(String n) {
        String t = n.replaceAll("(?i)^(AS|AC|FC|SS|SSC|SC|RC|RCD|CF|CD|UD|SV|VfB|VfL|OGC|1\\.\\s*FC)\\s+", "")
                .replaceAll("(?i)\\s+(FC|CF|SC|AFC|Rotterdam|Lisbon)$", "");
        return t.replaceAll("(?i)internazionale", "Inter");
    }

    private static String first(String n) {
        String t = tidy(n).trim();
        int sp = t.indexOf(' ');
        return sp > 0 ? t.substring(0, sp) : t;
    }

    private Bitmap cutout(String player, String club, int tall) {
        try {
            PlayerPhotos.Cutout cut = PlayerPhotos.cutout(c, player, club);
            if (cut == null || cut.url == null || cut.url.isEmpty()) return null;
            // The full-size original (the loader drops TheSportsDB's "/medium"), as tall as the picture.
            return ScoresTicker.cachedLogo(c, cut.url, tall / 2);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void loadImages(Pick best) {
        int crest = h.px(96) * 2;
        if (best.g.homeLogo != null) best.home = ScoresTicker.cachedLogo(c, best.g.homeLogo, crest);
        if (best.g.awayLogo != null) best.away = ScoresTicker.cachedLogo(c, best.g.awayLogo, crest);
        if (best.g.leagueLogo != null) best.league = ScoresTicker.cachedLogo(c, best.g.leagueLogo.replace("/500/", "/500-dark/"), h.px(48) * 2);
    }

    /** A Premier League game kicking off around 3pm on a Saturday, British time (the hour British TV may not show). */
    static boolean blackout(ScoresTicker.Game g) {
        if (g == null || !"eng.1".equals(g.slug) || g.kickoff == 0) return false;
        java.time.ZonedDateTime t = Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.of("Europe/London"));
        int minute = t.getHour() * 60 + t.getMinute();
        return t.getDayOfWeek() == java.time.DayOfWeek.SATURDAY && minute >= 14 * 60 + 45 && minute <= 15 * 60 + 15;
    }

    /** Sky Sports News from the TV guide's channels: {name, logo url}; null when the guide has none. */
    private String[] soccerSaturday() {
        for (String[] ch : HomeV2.guideChannels(c)) {
            if (ch[1].toLowerCase(Locale.ROOT).contains("sky sports news")) return new String[]{ch[1], ScoresTicker.skyLogo(ch[0], ch[1])};
        }
        return null;
    }

    /** Manchester United first, then England, then the followed clubs; the rest only when on British TV. */
    private static int rank(ScoresTicker.Game g) {
        if (MAN_UNITED.equals(g.homeId) || MAN_UNITED.equals(g.awayId)) return 1;
        if (ENGLAND.equals(g.homeId) || ENGLAND.equals(g.awayId)) return 5;
        if (CLUBS.contains(g.homeId) || CLUBS.contains(g.awayId)) return 6;
        return 0;
    }

    /**
     * Whether g beats the game chosen so far: Manchester United's always; then a game live now over one that is not;
     * then the next to start over one that has finished; between two alike, the better rank, then the earlier.
     */
    private static boolean better(ScoresTicker.Game g, int rank, Pick best) {
        if ((rank == 1) != (best.rank == 1)) return rank == 1;
        int a = order(g.state), b = order(best.g.state);
        if (a != b) return a < b;
        // Two still to come: the one starting first (then the better rank). Two live: the better rank, then the
        // earlier. Two finished: the more recent.
        if (g.state == 1 && g.kickoff != best.g.kickoff) return g.kickoff < best.g.kickoff;
        if (rank != best.rank) return rank < best.rank;
        // Level: a game with England or a followed club in it (Real Madrid's Champions League game over another).
        boolean mine = followed(g), theirs = followed(best.g);
        if (mine != theirs) return mine;
        return g.state == 2 ? g.kickoff > best.g.kickoff : g.kickoff < best.g.kickoff;
    }

    private static boolean followed(ScoresTicker.Game g) {
        return ENGLAND.equals(g.homeId) || ENGLAND.equals(g.awayId) || CLUBS.contains(g.homeId) || CLUBS.contains(g.awayId);
    }

    private static int order(int state) {
        return state == 0 ? 0 : state == 1 ? 1 : 2;
    }

    /**
     * The channel Sky's guide lists the game on, in its UHD form (name and UHD logo) where the playlist has the UHD
     * channel, so Match Day shows and tunes the 4K one first; else the HD one.
     */
    private void apply(Pick p, Object[] show) {
        TvGuide.Channel listed = new TvGuide.Channel((String) show[0], (String) show[1]);
        TvGuide.Channel up = TvGuide.uhd(c, listed);
        p.channel = up.name;
        p.channelLogo = up.logo;
        p.listedName = listed.name;
        p.coverFrom = (Long) show[2];
        String id = (String) show[4];
        if (id != null && !id.isEmpty()) p.picture = "https://images.metadata.sky.com/pd-image/" + id + "/16-9/1280";
    }

    private static String name(ScoresTicker.Game g, boolean home) {
        String full = home ? g.homeName : g.awayName;
        return full != null ? full : home ? g.home : g.away;
    }

    /** Each side's last five, oldest first ("LWWDW"), from ESPN's match summary. */
    private void form(Pick p) {
        if (p.g.slug == null || p.g.id == null) return;
        try {
            JSONObject s = new JSONObject(ScoresTicker.get("https://site.api.espn.com/apis/site/v2/sports/soccer/" + p.g.slug
                    + "/summary?event=" + p.g.id));
            JSONArray teams = s.optJSONArray("lastFiveGames");
            for (int i = 0; teams != null && i < teams.length(); i++) {
                JSONObject t = teams.getJSONObject(i);
                String id = t.optJSONObject("team") == null ? "" : t.getJSONObject("team").optString("id");
                JSONArray ev = t.optJSONArray("events");
                StringBuilder f = new StringBuilder();
                for (int k = ev == null ? -1 : ev.length() - 1; k >= 0; k--) {
                    String r = ev.getJSONObject(k).optString("gameResult");
                    if (!r.isEmpty()) f.append(r.charAt(0));
                }
                if (id.equals(p.g.homeId)) p.homeForm = f.toString();
                else if (id.equals(p.g.awayId)) p.awayForm = f.toString();
            }
            // Each side's top scorer in this competition (ESPN's "leaders", goals first).
            JSONArray leaders = s.optJSONArray("leaders");
            for (int i = 0; leaders != null && i < leaders.length(); i++) {
                JSONObject t = leaders.getJSONObject(i);
                String id = t.optJSONObject("team") == null ? "" : t.getJSONObject("team").optString("id");
                JSONArray cats = t.optJSONArray("leaders");
                String star = null;
                for (int k = 0; cats != null && k < cats.length() && star == null; k++) {
                    JSONObject cat = cats.getJSONObject(k);
                    if (!cat.optString("name").toLowerCase(Locale.UK).contains("goal") && k + 1 < cats.length()) continue;
                    JSONArray top = cat.optJSONArray("leaders");
                    if (top != null && top.length() > 0 && top.getJSONObject(0).optJSONObject("athlete") != null) {
                        star = top.getJSONObject(0).getJSONObject("athlete").optString("displayName", null);
                    }
                }
                if (id.equals(p.g.homeId)) p.homeStar = star;
                else if (id.equals(p.g.awayId)) p.awayStar = star;
            }
        } catch (Exception ignored) {
            // no form: the line stays empty
        }
    }

    // ---- showing it --------------------------------------------------------------------------------------------------

    private void bind(Pick p) {
        ScoresTicker.Game g = p.g;
        leagueView.setImageBitmap(p.league);
        boolean live = g.state == 0, done = g.state == 2;
        state.setText(live ? "LIVE" : done ? "FULL TIME" : "MATCH DAY");
        clock.setVisibility(live ? View.VISIBLE : View.GONE);
        subtitle.setText((g.venue != null ? g.venue : ScoresTicker.competition(g)));
        homeCrest.setImageBitmap(p.home);
        awayCrest.setImageBitmap(p.away);
        homeName.setText(name(g, true));
        awayName.setText(name(g, false));
        fitNames();
        if (live || done) {
            middle.setText(g.homeScore + "–" + g.awayScore);
            middle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(84));
            middle.setPadding(h.px(22), 0, h.px(22), 0);
            middle.setBackground(h.fill(16, 0x6B000000));
        } else {
            middle.setText("v");
            middle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(36));
            middle.setPadding(0, 0, 0, 0);
            middle.setBackground(null);
        }
        countdown.setVisibility(g.state == 1 ? View.VISIBLE : View.GONE);
        form.removeAllViews();
        form.setVisibility(g.state == 1 && (!p.homeForm.isEmpty() || !p.awayForm.isEmpty()) ? View.VISIBLE : View.GONE);
        if (form.getVisibility() == View.VISIBLE) {
            formLine(g.home, p.homeForm);
            formLine(g.away, p.awayForm);
        }
        events.removeAllViews();
        events.setVisibility(g.state == 1 ? View.GONE : View.VISIBLE);
        if (g.state != 1) {
            for (String[] goal : g.goals) eventChip(goal[2] + "  " + goal[1], "home".equals(goal[3]) ? g.homeColor : g.awayColor, false);
            for (String[] red : g.reds) eventChip(red[2] + "  " + red[1], 0xFFE8112D, true);
            if (g.goals.isEmpty() && g.reds.isEmpty()) eventChip(live ? "No goals yet" : "No goals", 0, false);
        }
        android.graphics.drawable.Drawable primeIcon = p.prime && p.logo == null ? appIcon(PRIME) : null;
        if (primeIcon != null) channelView.setImageDrawable(primeIcon); else channelView.setImageBitmap(p.logo);
        channelView.setVisibility(p.logo != null || primeIcon != null ? View.VISIBLE : View.GONE);
        String said = p.prime && p.logo == null ? "Live on Prime Video" : p.logo == null && p.channel != null ? p.channel : null;
        channelText.setText(said == null ? "" : said);
        channelText.setVisibility(said != null ? View.VISIBLE : View.GONE);
        backdrop.colours(g.homeColor, g.awayColor, g.awayAlt);
        backdrop.players(p.homePlayer, p.awayPlayer);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        String ko = Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()).format(hm);
        String text;
        if (live) text = "On now";
        else if (done) text = "Kick-off was " + ko;
        else if (p.coverFrom > 0 && p.coverFrom < g.kickoff) text = "Live from " + Instant.ofEpochMilli(p.coverFrom).atZone(ZoneId.systemDefault()).format(hm) + " · kick-off " + ko;
        else text = "Kick-off " + ko;
        if (p.blackout) text += live ? " · not live on UK TV · Soccer Saturday on Sky Sports News" : " · 3pm, not live on UK TV";
        else if (p.prime) text += "";
        else if (p.channel == null && p.watch == null) text += " · Not on British TV";
        line.setText(text);
        buttons(p);
        // The buttons were made again: their pills (and the lit one) back at once, not only when the focus next moves.
        if (painted) paint(lastFocused, lastIdx, lastChip);
        chip.setText("▲  MATCH DAY  ·  " + g.home + (live || done ? "  " + g.homeScore + "–" + g.awayScore + "  " : " v ") + g.away
                + (g.state == 1 ? "  ·  " + ko : live ? "  ·  " + ScoresTicker.liveClock(g) : ""));
    }

    /** The two names made smaller together until the line fits beside the crests and the score. */
    private void fitNames() {
        float size = 64;
        android.graphics.Paint pt = new android.graphics.Paint();
        pt.setTypeface(heavy);
        String hn = homeName.getText().toString().toUpperCase(Locale.UK), an = awayName.getText().toString().toUpperCase(Locale.UK);
        while (size > 34) {
            pt.setTextSize(h.px(size));
            float w = pt.measureText(hn) + pt.measureText(an);
            if (w <= h.px(1320 - 96 * 2 - 20 * 4 - 230)) break;
            size -= 2;
        }
        homeName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(size));
        awayName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h.px(size));
    }

    private void formLine(String club, String results) {
        LinearLayout r = row();
        TextView n = h.text(14, semi, 0xD9FFFFFF);
        n.setText(club == null ? "" : club.toUpperCase(Locale.UK));
        n.setLetterSpacing(0.08f);
        n.setSingleLine(true);
        r.addView(n, new LinearLayout.LayoutParams(h.px(150), -2));
        for (char ch : results.toCharArray()) {
            TextView b = h.text(13, heavy, ch == 'D' ? 0xFF12178A : Color.WHITE);
            b.setText(String.valueOf(ch));
            b.setGravity(Gravity.CENTER);
            b.setBackground(h.fill(6, ch == 'W' ? 0xFF19C37D : ch == 'D' ? 0xFFC9CEDE : 0xFFE8112D));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(h.px(24), h.px(24));
            bp.leftMargin = h.px(5);
            r.addView(b, bp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = h.px(4);
        form.addView(r, lp);
    }

    private void eventChip(String text, int colour, boolean card) {
        LinearLayout r = row();
        if (colour != 0) {
            View dot = new View(c);
            dot.setBackground(h.fill(card ? 3 : 8, colour | 0xFF000000));
            r.addView(dot, new LinearLayout.LayoutParams(h.px(card ? 14 : 16), h.px(card ? 20 : 16)));
        }
        TextView t = h.text(19, semi, Color.WHITE);
        t.setText(text);
        t.setSingleLine(true);
        r.addView(t, gap(colour != 0 ? 10 : 0));
        events.addView(r, gap(events.getChildCount() == 0 ? 0 : 26));
    }

    /** Watch (the channel in TiviMate, else the Live Centre), the Live Centre, and a reminder before kick-off. */
    private void buttons(Pick p) {
        buttons.removeAllViews();
        buttonViews.clear();
        actions.clear();
        ScoresTicker.Game g = p.g;
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        if (p.watch != null && !p.watch.isEmpty()) {
            // The channel by name, however it was found (Sky's guide, or the playlist as the Sports page does), without
            // a playlist's country prefix ("UK: ", "UK | ").
            String ch = p.watch.get(0).replaceFirst("^[A-Za-z]{2,3}\\s*[:|]\\s*", "").trim();
            addButton((g.state == 0 ? "Watch live on " : "Watch on ") + ch, () -> h.tune(p.watch, p.channelLogo));
        }
        if (p.prime && g.state != 2 && appIcon(PRIME) != null) {
            addButton("Open Prime Video", () -> {
                android.content.Intent open = c.getPackageManager().getLeanbackLaunchIntentForPackage(PRIME);
                if (open == null) open = c.getPackageManager().getLaunchIntentForPackage(PRIME);
                if (open != null) c.startActivity(open.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            });
        }
        // The Live Centre's own stamp (LIVE on green, CENTRE on white), as the Live Centre shows it.
        addLogoButton(LiveCentre.stampPicture(c, h.px(30)), "Live Centre", () -> LiveCentre.open(c, g.id));
        String[] ssn = p.blackout ? soccerSaturday() : null;
        if (ssn != null && g.state != 2) {
            addButton("Soccer Saturday", () -> h.tune(java.util.Collections.singletonList(ssn[0]), ssn[1]));
        }
        if (g.state == 1) {
            long at = p.coverFrom > 0 && p.coverFrom < g.kickoff ? p.coverFrom : g.kickoff;
            String when = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(hm);
            String title = g.home + " v " + g.away;
            addButton("Remind me at " + when, () -> {
                boolean set = Reminders.toggle(c, title, at, p.channel, p.channelLogo);
                View last = buttonViews.get(buttonViews.size() - 1);
                if (last instanceof TextView) ((TextView) last).setText(set ? "Reminder set for " + when : "Remind me at " + when);
            });
        }
    }

    private void addButton(String text, Runnable action) {
        TextView b = h.text(19, semi, Color.WHITE);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setPadding(h.px(26), 0, h.px(26), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(52));
        lp.rightMargin = h.px(16);
        buttons.addView(b, lp);
        buttonViews.add(b);
        actions.add(action);
    }

    /** A button that is a picture (the Live Centre's stamp) on the same pill as the others. */
    private void addLogoButton(Bitmap logo, String name, Runnable action) {
        FrameLayout b = new FrameLayout(c);
        b.setPadding(h.px(18), 0, h.px(18), 0);
        b.setContentDescription(name);
        ImageView mark = new ImageView(c);
        mark.setImageBitmap(logo);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.addView(mark, new FrameLayout.LayoutParams(logo.getWidth(), logo.getHeight(), Gravity.CENTER));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(52));
        lp.rightMargin = h.px(16);
        buttons.addView(b, lp);
        buttonViews.add(b);
        actions.add(action);
    }

    int buttonCount() {
        return buttonViews.size();
    }

    void press(int i) {
        if (i >= 0 && i < actions.size()) actions.get(i).run();
    }

    /** An installed app's icon, or null when it is not on this TV. */
    private android.graphics.drawable.Drawable appIcon(String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * The clubs' colours split on a slant behind the crests, as a broadcaster's match graphic, faded into Home's blue on
     * the left and at the foot.
     */
    static final class Backdrop extends View {
        private int home = 0xFFDA020E, away = 0xFF034694;
        private Bitmap left, right;
        private android.graphics.Rect leftBounds, rightBounds;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint mask = new android.graphics.Paint();

        Backdrop(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }

        void colours(int h, int a, int aAlt) {
            int hh = h == 0 ? 0xFFDA020E : h | 0xFF000000;
            int aa = a == 0 ? 0xFF034694 : a | 0xFF000000;
            // Two near-identical colours (red against red): the away side in its second colour.
            if (aAlt != 0 && near(hh, aa)) aa = aAlt | 0xFF000000;
            if (hh == home && aa == away) return;
            home = hh;
            away = aa;
            invalidate();
        }

        void players(Bitmap l, Bitmap r) {
            if (l == left && r == right) return;
            left = l;
            right = r;
            leftBounds = l == null ? null : opaque(l);
            rightBounds = r == null ? null : opaque(r);
            invalidate();
        }

        private static boolean near(int x, int y) {
            int dr = Color.red(x) - Color.red(y), dg = Color.green(x) - Color.green(y), db = Color.blue(x) - Color.blue(y);
            return dr * dr + dg * dg + db * db < 90 * 90;
        }

        private static int darker(int col) {
            return Color.rgb(Color.red(col) * 55 / 100, Color.green(col) * 55 / 100, Color.blue(col) * 55 / 100);
        }

        private void player(android.graphics.Canvas cv, Bitmap b, android.graphics.Rect bounds, float cx, float h, int glow) {
            if (b == null || bounds == null) return;
            float tall = h * 0.92f, scale = tall / bounds.height(), wide = bounds.width() * scale;
            float x = cx - wide / 2, y = h - tall;
            paint.setShader(new android.graphics.RadialGradient(cx, h * 0.62f, h * 0.55f, glow, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
            cv.drawCircle(cx, h * 0.62f, h * 0.55f, paint);
            paint.setShader(null);
            android.graphics.Paint shade = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
            shade.setColorFilter(new android.graphics.PorterDuffColorFilter(0x66000000, android.graphics.PorterDuff.Mode.SRC_IN));
            android.graphics.RectF to = new android.graphics.RectF(x, y, x + wide, h);
            cv.drawBitmap(b, bounds, new android.graphics.RectF(to.left + h * 0.02f, to.top + h * 0.012f, to.right + h * 0.02f, to.bottom), shade);
            cv.drawBitmap(b, bounds, to, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG | android.graphics.Paint.ANTI_ALIAS_FLAG));
        }

        /** The part of a cut-out that is not see-through. */
        private static android.graphics.Rect opaque(Bitmap b) {
            int w = b.getWidth(), h = b.getHeight(), l = w, t = h, r = -1, btm = -1;
            int[] row = new int[w];
            for (int y = 0; y < h; y += 2) {
                b.getPixels(row, 0, w, 0, y, w, 1);
                for (int x = 0; x < w; x += 2) {
                    if ((row[x] >>> 24) < 40) continue;
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > btm) btm = y;
                }
            }
            return r < 0 ? null : new android.graphics.Rect(l, t, Math.min(w, r + 2), Math.min(h, btm + 2));
        }

        @Override protected void onDraw(android.graphics.Canvas cv) {
            float w = getWidth(), h = getHeight();
            android.graphics.Path left = new android.graphics.Path();
            left.moveTo(0, 0); left.lineTo(w * 0.64f, 0); left.lineTo(w * 0.46f, h); left.lineTo(0, h); left.close();
            paint.setShader(new android.graphics.LinearGradient(0, 0, w * 0.5f, h, home, darker(home), android.graphics.Shader.TileMode.CLAMP));
            cv.drawPath(left, paint);
            android.graphics.Path right = new android.graphics.Path();
            right.moveTo(w * 0.64f, 0); right.lineTo(w, 0); right.lineTo(w, h); right.lineTo(w * 0.46f, h); right.close();
            paint.setShader(new android.graphics.LinearGradient(w, 0, w * 0.5f, h, away, darker(away), android.graphics.Shader.TileMode.CLAMP));
            cv.drawPath(right, paint);
            paint.setShader(null);
            paint.setColor(0xD9FFFFFF);
            android.graphics.Path slash = new android.graphics.Path();
            slash.moveTo(w * 0.633f, 0); slash.lineTo(w * 0.644f, 0); slash.lineTo(w * 0.466f, h); slash.lineTo(w * 0.455f, h); slash.close();
            cv.drawPath(slash, paint);
            // Light streaks across the slant, soft-edged, as a broadcaster's match graphic has.
            float[][] streaks = {{0.30f, 0.05f, 0.28f}, {0.37f, 0.014f, 0.45f}, {0.64f, 0.07f, 0.2f}, {0.71f, 0.011f, 0.42f}};
            cv.save();
            cv.skew(-0.42f, 0);   // on the slant: x moves left 0.42 for each 1 down
            for (float[] st : streaks) {
                float x0 = w * st[0], sw = w * st[1];
                int a = Math.round(255 * st[2]);
                paint.setShader(new android.graphics.LinearGradient(x0, 0, x0 + sw, 0, new int[]{0x00FFFFFF, (a << 24) | 0xFFFFFF, 0x00FFFFFF},
                        null, android.graphics.Shader.TileMode.CLAMP));
                cv.drawRect(x0, 0, x0 + sw, h, paint);
            }
            cv.restore();
            paint.setShader(null);
            // Each side's player, standing on the foot of the picture, a soft glow behind and a shadow beside.
            player(cv, this.left, leftBounds, w * 0.31f, h, 0x33FFFFFF);
            player(cv, this.right, rightBounds, w * 0.73f, h, 0x2EFFE6AA);
            mask.setShader(new android.graphics.LinearGradient(0, 0, w * 0.36f, 0, 0x00000000, 0xFF000000, android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, mask);
            mask.setShader(new android.graphics.LinearGradient(0, h * 0.6f, 0, h, 0xFF000000, 0x00000000, android.graphics.Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, w, h, mask);
        }
    }

    /** Shown (and lit on button i) while Home's focus is on Match Day; otherwise the chip by For You's heading. */
    private boolean painted, lastFocused, lastChip;
    private int lastIdx;

    void paint(boolean focused, int i, boolean chipAllowed) {
        painted = true;
        lastFocused = focused;
        lastIdx = i;
        lastChip = chipAllowed;
        i = Math.max(0, Math.min(i, buttonViews.size() - 1));
        boolean on = focused && active();
        panel.setVisibility(on ? View.VISIBLE : View.GONE);
        backdrop.setVisibility(on && pick.picture == null ? View.VISIBLE : View.GONE);
        chip.setVisibility(!focused && chipAllowed && active() ? View.VISIBLE : View.GONE);
        for (int k = 0; k < buttonViews.size(); k++) {
            View b = buttonViews.get(k);
            boolean lit = on && k == i;
            GradientDrawable bg = h.fill(26, lit ? 0xFFFFFFFF : 0x1FFFFFFF);
            bg.setStroke(h.px(lit ? 0 : 2), 0x73FFFFFF);
            b.setBackground(bg);
            if (b instanceof TextView) ((TextView) b).setTextColor(lit ? 0xFF12178A : Color.WHITE);
            b.animate().scaleX(lit ? 1.05f : 1f).scaleY(lit ? 1.05f : 1f).setDuration(150).start();
        }
    }
}
