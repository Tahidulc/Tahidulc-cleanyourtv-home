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
 * Which game: Manchester United's first; then England's; then Barcelona, Real Madrid, Bayern Munich, PSG, AC Milan,
 * Inter and Juventus; then the Premier League, Champions League, EFL Cup and FA Cup games live on British TV (Sky's
 * guide lists them). Each shows from the morning of the day until three hours after its kick-off; with several, the
 * one live now, else the next to start.
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
    /** A game shows until this long after its kick-off. */
    private static final long AFTER_MS = 3 * 3600_000L;

    /** What Match Day shows. */
    static final class Pick {
        ScoresTicker.Game g;
        /** 1 Manchester United, 2 England, 3 a followed club, 4 a major game on British TV. */
        int rank;
        List<String> watch;
        String channel, channelLogo, picture, listedName;
        long coverFrom;
        Bitmap league, home, away, logo;
        String homeForm = "", awayForm = "";
    }

    private final HomeV2 h;
    private final Context c;
    final FrameLayout panel;
    final TextView chip;
    private final ImageView leagueView, homeCrest, awayCrest, channelView;
    private final TextView state, clock, subtitle, homeName, awayName, middle, line, channelText;
    private final LinearLayout countdown, form, events, buttons;
    private final TextView[] digits = new TextView[3];
    private final List<TextView> buttonViews = new ArrayList<>();
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
        place(teams, 0, 60, 1320, 100);

        LinearLayout third = row();
        countdown = row();
        TextView k = h.text(15, semi, 0xD9FFFFFF);
        k.setText("KICK-OFF IN");
        k.setLetterSpacing(0.14f);
        countdown.addView(k);
        String[] units = {"HRS", "MINS", "SECS"};
        for (int i = 0; i < 3; i++) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            GradientDrawable g = h.fill(12, 0x4D000000);
            g.setStroke(Math.max(1, h.px(1)), 0x40FFFFFF);
            box.setBackground(g);
            digits[i] = h.text(34, heavy, Color.WHITE);
            digits[i].setFontFeatureSettings("tnum");
            box.addView(digits[i]);
            TextView u = h.text(11, semi, 0xCCFFFFFF);
            u.setText(units[i]);
            u.setLetterSpacing(0.16f);
            box.addView(u);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(h.px(74), h.px(58));
            bp.leftMargin = h.px(10);
            countdown.addView(box, bp);
        }
        third.addView(countdown);
        form = new LinearLayout(c);
        form.setOrientation(LinearLayout.VERTICAL);
        third.addView(form, gap(30));
        events = row();
        third.addView(events);
        place(third, 0, 172, 1320, 60);

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
        place(fourth, 0, 240, 1320, 36);

        buttons = row();
        place(buttons, 0, 286, 1320, 54);

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
            int rank = rank(g);
            Object[] show = null;
            if (rank == 0 && g.slug != null && MAJOR_SLUGS.contains(g.slug)) {
                show = TvGuide.show(channels, name(g, true), name(g, false), g.kickoff);
                if (show != null) rank = 4;
            }
            if (rank == 0) continue;
            if (best == null || better(g, rank, best)) {
                best = new Pick();
                best.g = g;
                best.rank = rank;
                if (show != null) apply(best, show);
            }
        }
        if (best == null) return null;
        if (best.channel == null) {
            Object[] show = TvGuide.show(channels, name(best.g, true), name(best.g, false), best.g.kickoff);
            if (show != null) apply(best, show);
        }
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
        int crest = h.px(96) * 2;
        if (best.g.homeLogo != null) best.home = ScoresTicker.cachedLogo(c, best.g.homeLogo, crest);
        if (best.g.awayLogo != null) best.away = ScoresTicker.cachedLogo(c, best.g.awayLogo, crest);
        if (best.g.leagueLogo != null) best.league = ScoresTicker.cachedLogo(c, best.g.leagueLogo.replace("/500/", "/500-dark/"), h.px(48) * 2);
        if (best.logo == null && best.channelLogo != null) best.logo = HomeV2.trim(ScoresTicker.cachedLogo(c, best.channelLogo, h.px(360)));
        form(best);
        return best;
    }

    /** Manchester United first, then England, then the followed clubs; the rest only when on British TV. */
    private static int rank(ScoresTicker.Game g) {
        if (MAN_UNITED.equals(g.homeId) || MAN_UNITED.equals(g.awayId)) return 1;
        if (ENGLAND.equals(g.homeId) || ENGLAND.equals(g.awayId)) return 2;
        if (CLUBS.contains(g.homeId) || CLUBS.contains(g.awayId)) return 3;
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
        if (rank != best.rank) return rank < best.rank;
        return g.state == 2 ? g.kickoff > best.g.kickoff : g.kickoff < best.g.kickoff;
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
        channelView.setImageBitmap(p.logo);
        channelView.setVisibility(p.logo != null ? View.VISIBLE : View.GONE);
        channelText.setText(p.logo == null && p.channel != null ? p.channel : "");
        channelText.setVisibility(p.logo == null && p.channel != null ? View.VISIBLE : View.GONE);
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("HH:mm");
        String ko = Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()).format(hm);
        String text;
        if (live) text = "On now";
        else if (done) text = "Kick-off was " + ko;
        else if (p.coverFrom > 0 && p.coverFrom < g.kickoff) text = "Live from " + Instant.ofEpochMilli(p.coverFrom).atZone(ZoneId.systemDefault()).format(hm) + " · kick-off " + ko;
        else text = "Kick-off " + ko;
        if (p.channel == null && p.watch == null) text += " · Not on British TV";
        line.setText(text);
        buttons(p);
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
            boolean early = g.state == 1 && System.currentTimeMillis() < (p.coverFrom > 0 ? p.coverFrom : g.kickoff);
            addButton(g.state == 0 ? "Watch live" : g.state == 2 ? "Watch on " + p.watch.get(0) : early ? "Watch " + p.watch.get(0) : "Watch the build-up",
                    () -> h.tune(p.watch, p.channelLogo));
        }
        addButton("Live Centre", () -> LiveCentre.open(c, g.id));
        if (g.state == 1) {
            long at = p.coverFrom > 0 && p.coverFrom < g.kickoff ? p.coverFrom : g.kickoff;
            String when = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(hm);
            String title = g.home + " v " + g.away;
            addButton("Remind me at " + when, () -> {
                boolean set = Reminders.toggle(c, title, at, p.channel, p.channelLogo);
                TextView b = buttonViews.get(buttonViews.size() - 1);
                b.setText(set ? "Reminder set for " + when : "Remind me at " + when);
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

    int buttonCount() {
        return buttonViews.size();
    }

    void press(int i) {
        if (i >= 0 && i < actions.size()) actions.get(i).run();
    }

    /** Shown (and lit on button i) while Home's focus is on Match Day; otherwise the chip by For You's heading. */
    void paint(boolean focused, int i, boolean chipAllowed) {
        boolean on = focused && active();
        panel.setVisibility(on ? View.VISIBLE : View.GONE);
        chip.setVisibility(!focused && chipAllowed && active() ? View.VISIBLE : View.GONE);
        for (int k = 0; k < buttonViews.size(); k++) {
            TextView b = buttonViews.get(k);
            boolean lit = on && k == i;
            GradientDrawable bg = h.fill(26, lit ? 0xFFFFFFFF : 0x1FFFFFFF);
            bg.setStroke(h.px(lit ? 0 : 2), 0x73FFFFFF);
            b.setBackground(bg);
            b.setTextColor(lit ? 0xFF12178A : Color.WHITE);
            b.animate().scaleX(lit ? 1.05f : 1f).scaleY(lit ? 1.05f : 1f).setDuration(150).start();
        }
    }
}
