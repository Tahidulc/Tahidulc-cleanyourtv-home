package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gathin.broadcast.SportBoard;
import com.gathin.tvhomephone.glass.AmbientView;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.glass.LensBar;
import com.gathin.tvhomephone.live.LogoTiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sports, in the glass of Home (the family's mock-up A): the page glowing with the featured club's colours, "Matchday"
 * and Sports with the date, the time and the weather; the pill (Live, Football, Tables, More) and a strip of every sport
 * on the page by its logo; then the featured game (live, or your club's next) with Watch on its channel; your clubs'
 * next games; today's football under each competition's logo; live sport on British TV by channel logo; the table; then
 * Formula 1, tennis, UFC, the NFL, the NBA and golf, each with its league's logo and the channel it is on; and the
 * football coming up with every channel's logo. It only lays out what it is given: SportsFragment reads and gives.
 */
final class SportsScreen {
    /** What the page's taps do. */
    interface Host {
        /** A football game's own page. */
        void game(Game g);

        /** A channel played in Live TV (when the playlist has it). */
        void watch(String channel);

        /** Another competition's table. */
        void table(String slug);

        /** Settings, where the clubs and the alerts are chosen. */
        void settings();

        /** Another sport's event on its own page (every channel showing it, its highlights). */
        default void event(SportEvent e) {
        }

        /** A club's own page, from a table's row ({rank, team id, name, logo, …} as Espn.table gives it). */
        default void team(String slug, String[] row) {
        }
    }

    /** The pill's places. */
    static final int LIVE = 1, FOOTBALL = 2, TABLES = 3, MORE = 4;

    final Context c;
    final Host host;
    final FrameLayout root;
    final AmbientView ambient;
    final SwipeRefreshLayout swipe;
    final ScrollView scroll;
    final LinearLayout col;
    final LensBar pill;
    private final TextView kicker, date, line;
    private final LinearLayout strip;
    private final HorizontalScrollView stripScroll;
    /** A tablet with the room: the two columns side by side and each column (null on a phone: one column). */
    private LinearLayout columns, leftColumn, rightColumn;
    /** The sections down the left column (football); the rest go down the right. */
    private static final java.util.Set<String> LEFT = new java.util.HashSet<>(java.util.Arrays.asList("live", "clubs", "today", "coming"));
    /** Each section by its key, in the page's order. */
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private String under = "Manchester";
    private int spinnerFor = -1;
    private boolean tableFull;
    private List<String[]> tableRows;
    private String tableSlug = "eng.1";
    private Set<String> tableMine;

    /** A section: its heading and its box (hidden while it has nothing). */
    static final class Section {
        final String key, name;
        final LinearLayout box, body;
        final TextView head;
        /** Its chip on the strip: the logo (or the sport's emoji) and the word (its heading's, unless given). */
        String chipLogo, chipSport, chipName;

        Section(String key, String name, LinearLayout box, TextView head, LinearLayout body) {
            this.key = key;
            this.name = name;
            this.box = box;
            this.head = head;
            this.body = body;
        }
    }

    SportsScreen(Context c, Host host) {
        this.c = c;
        this.host = host;
        root = new FrameLayout(c);
        root.setBackgroundColor(Glass.GROUND);
        ambient = new AmbientView(c);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        swipe = new SwipeRefreshLayout(c);
        swipe.setColorSchemeColors(Glass.TEXT);
        swipe.setProgressBackgroundColorSchemeColor(0xFF26272E);
        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        col = SportCards.column(c);
        scroll.addView(col, new FrameLayout.LayoutParams(-1, -2));
        swipe.addView(scroll);
        root.addView(swipe, new FrameLayout.LayoutParams(-1, -1));
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> ambient.setShift(y));
        Glass.fit(root, (top, bottom) -> {
            scroll.setPadding(0, top + Glass.dp(c, 12), 0, bottom);
            if (top == spinnerFor) return;
            spinnerFor = top;
            swipe.setProgressViewOffset(false, top - Glass.dp(c, 40), top + Glass.dp(c, 56));
        });

        // "Matchday" and Sports; the date, the time and the weather on the right.
        LinearLayout head = SportCards.row(c);
        int side = Glass.dp(c, 20);
        head.setPadding(side, 0, side, 0);
        head.setGravity(Gravity.BOTTOM);
        LinearLayout left = SportCards.column(c);
        kicker = Glass.text(c, "Matchday", 14, Glass.MUTED, Glass.body(c));
        left.addView(kicker);
        TextView title = Glass.text(c, "Sports", 34, Glass.TEXT, Glass.heading(c, 600));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Glass.dp(c, 2);
        left.addView(title, tp);
        head.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout right = SportCards.column(c);
        right.setGravity(Gravity.END);
        date = Glass.text(c, "", 16, Glass.TEXT, Glass.heading(c, 600));
        date.setGravity(Gravity.END);
        line = Glass.text(c, "", 12, Glass.MUTED, Glass.body(c));
        line.setGravity(Gravity.END);
        right.addView(date);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Glass.dp(c, 4);
        right.addView(line, lp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.bottomMargin = Glass.dp(c, 4);
        head.addView(right, rp);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // The pill: the settings, then the places on the page.
        pill = new LensBar(c, LensBar.PILL);
        pill.action(Icons.SETTINGS, "Sports settings", host::settings)
                .segment(LIVE, "Live").segment(FOOTBALL, "Football").segment(TABLES, "Tables").segment(MORE, "More");
        pill.select(LIVE, false);
        pill.setSources(ambient);
        pill.setOnPick(id -> {
            String key = id == LIVE ? "live" : id == FOOTBALL ? "today" : id == TABLES ? "table" : "ontv";
            if (id == LIVE && !visible("live")) key = "ontv";
            if (id == FOOTBALL && !visible("today")) key = visible("live") ? "live" : "coming";
            if (id == MORE && !visible("ontv")) key = firstOther();
            scrollTo(key);
        });
        boolean wide = c.getResources().getConfiguration().screenWidthDp >= 600;
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(wide ? Glass.dp(c, 520) : -1, -2);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        pp.setMargins(Glass.dp(c, 16), Glass.dp(c, 18), Glass.dp(c, 16), 0);
        col.addView(pill, pp);

        // Every sport on the page by its logo: a tap slides the page to it.
        stripScroll = new HorizontalScrollView(c);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip = SportCards.row(c);
        strip.setPadding(Glass.dp(c, 16), 0, Glass.dp(c, 10), 0);
        stripScroll.addView(strip, new FrameLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = Glass.dp(c, 14);
        col.addView(stripScroll, sp);
        stripScroll.setVisibility(View.GONE);

        // A tablet with the room: two columns, football down the left and the rest of the sport down the right.
        if (com.gathin.tvhomephone.glass.Wide.twoColumns(c)) {
            columns = SportCards.row(c);
            columns.setGravity(Gravity.TOP);
            leftColumn = SportCards.column(c);
            rightColumn = SportCards.column(c);
            columns.addView(leftColumn, new LinearLayout.LayoutParams(0, -2, 1f));
            columns.addView(rightColumn, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(columns, new LinearLayout.LayoutParams(-1, -2));
        }

        section("live", null, null, null);
        section("clubs", "Your clubs", null, null);
        section("today", "Today", Competitions.logo("eng.1"), OnTv.FOOTBALL).chipName = "Football";
        section("ontv", "Live sport on TV", null, null);
        section("table", "Tables", null, null);
        section("f1", "Formula 1", Competitions.F1_LOGO, OnTv.F1);
        section("tennis", "Tennis", null, OnTv.TENNIS);
        section("ufc", "UFC", SportCards.SportFeeds_UFC_LOGO, OnTv.UFC);
        section("nfl", "NFL", OnTv.LEAGUES + "nfl.png", OnTv.NFL);
        section("nba", "NBA", OnTv.LEAGUES + "nba.png", OnTv.NBA);
        section("golf", "Golf", MoreSports.PGA_LOGO, OnTv.GOLF);
        section("coming", "Coming up", null, null);
        stamp();
    }

    View view() {
        return root;
    }

    private Section section(String key, String name, String chipLogo, String chipSport) {
        LinearLayout box = SportCards.column(c);
        TextView head = null;
        if (name != null) {
            head = Glass.section(c, name, null);
            box.addView(head);
        } else {
            box.setPadding(0, Glass.dp(c, 16), 0, 0);
        }
        LinearLayout body = SportCards.column(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.leftMargin = lp.rightMargin = Glass.dp(c, 16);
        box.addView(body, lp);
        box.setVisibility(View.GONE);
        (columns == null ? col : LEFT.contains(key) ? leftColumn : rightColumn).addView(box, new LinearLayout.LayoutParams(-1, -2));
        Section s = new Section(key, name, box, head, body);
        s.chipLogo = chipLogo;
        s.chipSport = chipSport;
        sections.put(key, s);
        return s;
    }

    private boolean visible(String key) {
        Section s = sections.get(key);
        return s != null && s.box.getVisibility() == View.VISIBLE;
    }

    private String firstOther() {
        for (String k : new String[]{"f1", "tennis", "ufc", "nfl", "nba", "golf"}) if (visible(k)) return k;
        return "table";
    }

    /** Slides the page so a section's heading sits under the status bar. */
    void scrollTo(String key) {
        Section s = sections.get(key);
        if (s == null || s.box.getVisibility() != View.VISIBLE) return;
        int top = s.box.getTop() + (columns != null ? columns.getTop() : 0);
        scroll.smoothScrollTo(0, Math.max(0, top - Glass.dp(c, 4)));
    }

    /** The page's width a section has (the screen's, or a column's on a tablet with two). */
    private int pageWidth() {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        return columns == null ? w : w / 2;
    }

    /** Fills a section (nothing: hidden) and the strip of chips again. */
    private void fill(String key, List<View> views) {
        Section s = sections.get(key);
        if (s == null) return;
        s.body.removeAllViews();
        for (View v : views) {
            LinearLayout.LayoutParams lp = v.getLayoutParams() instanceof LinearLayout.LayoutParams
                    ? (LinearLayout.LayoutParams) v.getLayoutParams() : new LinearLayout.LayoutParams(-1, -2);
            if (s.body.getChildCount() > 0 && lp.topMargin == 0) lp.topMargin = Glass.dp(c, 10);
            s.body.addView(v, lp);
        }
        s.box.setVisibility(views.isEmpty() ? View.GONE : View.VISIBLE);
        chips();
    }

    /** The strip: a chip for each sport on the page, by its logo (or its emoji where it has none). */
    private void chips() {
        strip.removeAllViews();
        for (Section s : sections.values()) {
            if (s.chipSport == null || s.box.getVisibility() != View.VISIBLE) continue;
            strip.addView(chip(s.chipLogo, s.chipSport, s.chipName != null ? s.chipName : s.name, () -> scrollTo(s.key)));
        }
        // The sports only the TV guide has today (darts, cricket, snooker…): a chip each, to the TV list.
        for (String sport : guideOnly) strip.addView(chip(OnTv.logo(sport, ""), sport, OnTv.sportName(sport), () -> scrollTo("ontv")));
        stripScroll.setVisibility(strip.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private final List<String> guideOnly = new ArrayList<>();

    private View chip(String logo, String sport, String name, Runnable tap) {
        LinearLayout ch = SportCards.row(c);
        ch.setBackground(Glass.card(c, 18));
        ch.setPadding(Glass.dp(c, 10), Glass.dp(c, 7), Glass.dp(c, 14), Glass.dp(c, 7));
        View mark = logo != null ? SportCards.image(c, logo, 22, 22) : SportCards.sportMark(c, sport, "", 22);
        ch.addView(mark);
        TextView t = SportCards.text(c, name, 13, Glass.TEXT, Glass.bodySemi(c));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.leftMargin = Glass.dp(c, 8);
        ch.addView(t, tp);
        ch.setOnClickListener(v -> tap.run());
        ch.setContentDescription(name);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Glass.dp(c, 40));
        lp.rightMargin = Glass.dp(c, 8);
        ch.setLayoutParams(lp);
        return ch;
    }

    // ---- the header -------------------------------------------------------------------------------------------------

    /** The line under the date: the time and the weather ("17:41 · 6° Manchester"). */
    void weather(String w) {
        under = w == null || w.isEmpty() ? "Manchester" : w;
        stamp();
    }

    /** The date and the time as they are now (the clock's tick). */
    void stamp() {
        date.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d", Locale.UK)));
        line.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + " · " + under);
    }

    /** "Matchday" while there is football today, "This week" otherwise. */
    void matchday(boolean on) {
        kicker.setText(on ? "Matchday" : "This week");
    }

    // ---- the featured game ------------------------------------------------------------------------------------------

    /** The game at the top: live (your club's first) or your club's next, with Watch on its channel. Null: none. */
    void featured(Game g, String channel, String label) {
        List<View> v = new ArrayList<>();
        if (g != null) {
            v.add(featuredCard(g, channel, label));
            String glow = g.homeLogo != null && !g.homeLogo.isEmpty() ? g.homeLogo : g.awayLogo;
            if (glow != null && !glow.isEmpty()) ambient.setImage(glow);
        }
        fill("live", v);
    }

    private View featuredCard(Game g, String channel, String label) {
        LinearLayout box = SportCards.card(c);
        int pad = Glass.dp(c, 16);
        box.setPadding(pad, Glass.dp(c, 14), pad, pad);
        LinearLayout top = SportCards.row(c);
        top.addView(SportCards.image(c, g.leagueLogo, 18, 18));
        String where = (g.leagueName == null ? "" : g.leagueName) + (g.venue == null || g.venue.isEmpty() ? "" : " · " + g.venue);
        TextView w = SportCards.text(c, where, 12, Glass.MUTED, Glass.body(c));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = Glass.dp(c, 8);
        top.addView(w, wp);
        String state = g.state == Game.LIVE ? "● LIVE " + (g.halftime ? "HT" : g.detail) : g.state == Game.POST ? "FT" : label;
        top.addView(SportCards.pill(c, state, g.state == Game.LIVE ? SportCards.LIVE_RED : 0x33FFFFFF, Glass.TEXT));
        box.addView(top);

        LinearLayout mid = SportCards.row(c);
        mid.setPadding(0, Glass.dp(c, 14), 0, 0);
        mid.addView(club(g.homeLogo, g.homeName, goals(g, "home")), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout score = SportCards.column(c);
        score.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean before = g.state == Game.PRE;
        TextView big = Glass.text(c, before ? SportCards.when(g.kickoff).replaceAll("^\\S+ ", "") : g.homeScore + " – " + g.awayScore,
                before ? 30 : 42, Glass.TEXT, Glass.heading(c, 700));
        big.setGravity(Gravity.CENTER);
        score.addView(big);
        if (before) {
            TextView day = SportCards.text(c, SportCards.dayAndTime(g.kickoff).replaceAll(" · .*$", ""), 12, Glass.MUTED, Glass.body(c));
            day.setGravity(Gravity.CENTER);
            score.addView(day);
        }
        mid.addView(score, new LinearLayout.LayoutParams(Glass.dp(c, 120), -2));
        mid.addView(club(g.awayLogo, g.awayName, goals(g, "away")), new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(mid);

        LinearLayout buttons = SportCards.row(c);
        buttons.setPadding(0, Glass.dp(c, 16), 0, 0);
        if (channel != null) {
            LinearLayout watch = SportCards.row(c);
            watch.setGravity(Gravity.CENTER);
            watch.setBackground(roundFill(0xFFFFFFFF, 22));
            watch.setPadding(Glass.dp(c, 16), 0, Glass.dp(c, 16), 0);
            TextView play = SportCards.text(c, "▶  Watch ·", 14, 0xFF101114, Glass.bodySemi(c));
            watch.addView(play);
            View logo = darkLogo(channel);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) logo.getLayoutParams();
            lp.leftMargin = Glass.dp(c, 8);
            watch.addView(logo, lp);
            watch.setOnClickListener(v -> host.watch(channel));
            watch.setContentDescription("Watch on " + channel);
            buttons.addView(watch, new LinearLayout.LayoutParams(0, Glass.dp(c, 44), 1f));
        }
        TextView match = SportCards.text(c, "Match", 14, Glass.TEXT, Glass.bodySemi(c));
        match.setGravity(Gravity.CENTER);
        match.setBackground(roundFill(0x33FFFFFF, 22));
        match.setOnClickListener(v -> host.game(g));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(channel == null ? 0 : Glass.dp(c, 96), Glass.dp(c, 44), channel == null ? 1f : 0f);
        if (channel != null) mp.leftMargin = Glass.dp(c, 10);
        buttons.addView(match, mp);
        box.addView(buttons);
        box.setOnClickListener(v -> host.game(g));
        return box;
    }

    /**
     * A channel's logo on the white Watch button, as Behind logos asks (LogoTiles): on a small tile in its own colour (the
     * default), on black, or as it is on the white (a soft dark halo behind a white logo, so it shows). Its name on a dark
     * tile when it has no logo.
     */
    private View darkLogo(String channel) {
        FrameLayout tile = new FrameLayout(c);
        tile.setPadding(Glass.dp(c, 6), Glass.dp(c, 4), Glass.dp(c, 6), Glass.dp(c, 4));
        tile.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        String url = channel == null ? null : ChannelLogos.known(c, channel);
        if (url == null) {
            tile.setBackground(roundFill(0xFF101114, 6));
            tile.addView(SportCards.channel(c, channel, 14), new FrameLayout.LayoutParams(-2, Glass.dp(c, 14)));
            return tile;
        }
        ImageView logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setMaxWidth(Glass.dp(c, 118));
        logo.setContentDescription(channel);
        tile.addView(logo, new FrameLayout.LayoutParams(-2, Glass.dp(c, 14)));
        LogoTiles.show(tile, 6, false, logo, url, LogoTiles.ON_WHITE, () -> {
            tile.removeAllViews();
            tile.setBackground(roundFill(0xFF101114, 6));
            tile.addView(SportCards.text(c, channel, 11, Glass.MUTED, Glass.bodySemi(c)), new FrameLayout.LayoutParams(-2, -2));
        });
        return tile;
    }

    private android.graphics.drawable.GradientDrawable roundFill(int colour, float radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(Glass.dp(c, radiusDp));
        return d;
    }

    private View club(String logo, String name, String scorers) {
        LinearLayout col = SportCards.column(c);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView crest = SportCards.image(c, logo, 56, 56);
        col.addView(crest);
        TextView n = Glass.text(c, name == null ? "" : name, 15, Glass.TEXT, Glass.bodySemi(c));
        n.setGravity(Gravity.CENTER);
        n.setMaxLines(2);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = Glass.dp(c, 8);
        col.addView(n, np);
        if (!scorers.isEmpty()) {
            TextView s = Glass.text(c, scorers, 12, Glass.MUTED, Glass.body(c));
            s.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.topMargin = Glass.dp(c, 4);
            col.addView(s, sp);
        }
        return col;
    }

    /** "Salah 23'\nMbeumo 58'": a side's scorers, one a line. */
    static String goals(Game g, String side) {
        StringBuilder b = new StringBuilder();
        for (String[] goal : g.goals) {
            if (!side.equals(goal[3])) continue;
            String who = goal[1] == null ? "" : goal[1];
            int sp = who.lastIndexOf(' ');
            String surname = sp > 0 ? who.substring(sp + 1) : who;
            if (b.length() > 0) b.append('\n');
            b.append(surname).append(' ').append(goal[2]).append("og".equals(goal[4]) ? " (og)" : "pen".equals(goal[4]) ? " (pen)" : "");
        }
        return b.toString();
    }

    // ---- your clubs -------------------------------------------------------------------------------------------------

    /** A card for each starred club: {club {id, name, crest}, its next game or null, that game's channel or null}. */
    void clubs(List<Object[]> cards) {
        List<View> v = new ArrayList<>();
        if (!cards.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            hs.setClipToPadding(false);
            LinearLayout row = SportCards.row(c);
            for (Object[] x : cards) row.addView(clubCard((String[]) x[0], (Game) x[1], (String) x[2]));
            hs.addView(row);
            v.add(hs);
        }
        fill("clubs", v);
    }

    private View clubCard(String[] club, Game next, String channel) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Glass.card(c, 20));
        Glass.round(f, Glass.dp(c, 20));
        // The club's crest, big and cut by the card's edge.
        ImageView crest = new ImageView(c);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        crest.setAlpha(0.95f);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Glass.dp(c, 84), Glass.dp(c, 84), Gravity.END | Gravity.CENTER_VERTICAL);
        cp.rightMargin = -Glass.dp(c, 18);
        f.addView(crest, cp);
        Ui.load(crest, club[2], null, () -> crest.setVisibility(View.GONE));
        LinearLayout words = SportCards.column(c);
        int p = Glass.dp(c, 14);
        words.setPadding(p, Glass.dp(c, 12), Glass.dp(c, 70), Glass.dp(c, 12));
        words.addView(SportCards.text(c, club[1], 17, Glass.TEXT, Glass.heading(c, 600)));
        if (next == null) {
            words.addView(SportCards.text(c, "No games found", 13, Glass.MUTED, Glass.body(c)));
        } else {
            boolean home = club[0].equals(next.homeId);
            String other = home ? next.awayName : next.homeName;
            String state = next.state == Game.LIVE ? "LIVE " + next.detail + " · " + next.homeScore + "–" + next.awayScore
                    : SportCards.when(next.kickoff);
            TextView vs = SportCards.text(c, (home ? "v " : "at ") + other, 14, Glass.SOFT, Glass.body(c));
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2);
            vp.topMargin = Glass.dp(c, 6);
            words.addView(vs, vp);
            words.addView(SportCards.text(c, state, 13, next.state == Game.LIVE ? SportCards.LIVE_RED : Glass.MUTED, Glass.bodySemi(c)));
            LinearLayout marks = SportCards.row(c);
            marks.addView(SportCards.image(c, next.leagueLogo, 18, 18));
            if (channel != null) {
                View ch = SportCards.channel(c, channel, 12);
                LinearLayout.LayoutParams chp = (LinearLayout.LayoutParams) ch.getLayoutParams();
                chp.leftMargin = Glass.dp(c, 8);
                marks.addView(ch, chp);
            } else {
                TextView comp = SportCards.text(c, next.leagueName == null ? "" : next.leagueName, 12, Glass.MUTED, Glass.body(c));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.leftMargin = Glass.dp(c, 6);
                marks.addView(comp, lp);
            }
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-2, -2);
            mp.topMargin = Glass.dp(c, 8);
            words.addView(marks, mp);
            f.setOnClickListener(v -> host.game(next));
            f.setContentDescription(club[1] + ": " + (home ? "home to " : "away at ") + other + ", " + state
                    + (channel == null ? "" : ", on " + channel));
        }
        f.addView(words, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Glass.dp(c, 236), -2);
        lp.rightMargin = Glass.dp(c, 10);
        f.setLayoutParams(lp);
        return f;
    }

    // ---- today's football -------------------------------------------------------------------------------------------

    /** Today's games under each competition's logo, your clubs' first; each with its channel's logo. */
    void today(Map<String, List<Game>> days, Map<String, String> channels, Set<String> mine) {
        List<View> v = new ArrayList<>();
        for (Map.Entry<String, List<Game>> e : days.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            boolean yours = SportsFragment.MINE.equals(e.getKey());
            Game first = e.getValue().get(0);
            LinearLayout card = SportCards.card(c);
            card.addView(SportCards.compHead(c, yours ? null : first.leagueLogo, yours ? "Your clubs" : first.leagueName,
                    e.getValue().size() + (e.getValue().size() == 1 ? " game" : " games")));
            boolean firstRow = true;
            for (Game g : e.getValue()) {
                if (!firstRow) card.addView(SportCards.divider(c));
                firstRow = false;
                card.addView(SportCards.gameRow(c, g, channels.get(g.id), g.involves(mine), x -> host.game(g)));
            }
            v.add(card);
        }
        fill("today", v);
    }

    // ---- live sport on TV -------------------------------------------------------------------------------------------

    private boolean tvAll;
    private List<OnTv.Listing> tv = new ArrayList<>();

    /** Live sport on British TV (football aside): on now first, then what comes, each with every channel's logo. */
    void onTv(List<OnTv.Listing> listings) {
        tv = listings == null ? new ArrayList<>() : listings;
        guideOnly.clear();
        for (OnTv.Listing l : tv) {
            if (!guideOnly.contains(l.sport) && !hasOwnSection(l.sport)) guideOnly.add(l.sport);
        }
        drawTv();
    }

    private static boolean hasOwnSection(String sport) {
        switch (sport) {
            case OnTv.F1: case OnTv.TENNIS: case OnTv.UFC: case OnTv.NFL: case OnTv.NBA: case OnTv.GOLF: case OnTv.FOOTBALL:
                return true;
            default:
                return false;
        }
    }

    private void drawTv() {
        List<View> v = new ArrayList<>();
        if (!tv.isEmpty()) {
            LinearLayout card = SportCards.card(c);
            long now = System.currentTimeMillis();
            int shown = 0, max = tvAll ? tv.size() : 8;
            for (OnTv.Listing l : tv) {
                if (shown >= max) break;
                if (shown > 0) card.addView(SportCards.divider(c));
                card.addView(SportCards.listingRow(c, l, now, x -> host.event(SportEvent.listing(l))));
                shown++;
            }
            if (tv.size() > 8) card.addView(more(tvAll ? "Show less" : "Show all " + tv.size(), () -> {
                tvAll = !tvAll;
                drawTv();
            }));
            v.add(card);
        }
        fill("ontv", v);
    }

    private TextView more(String words, Runnable tap) {
        TextView t = SportCards.text(c, words + "  ›", 13, SportCards.MINE, Glass.bodySemi(c));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Glass.dp(c, 12), 0, Glass.dp(c, 2));
        t.setOnClickListener(x -> tap.run());
        return t;
    }

    // ---- the table --------------------------------------------------------------------------------------------------

    /** A competition's table: chips for the others by their logos; its top four and your clubs' places, or all of it. */
    void table(String slug, List<String[]> rows, Set<String> mine) {
        tableSlug = slug;
        tableRows = rows;
        tableMine = mine;
        drawTable();
    }

    private void drawTable() {
        List<View> v = new ArrayList<>();
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = SportCards.row(c);
        String name = "";
        for (String[] t : Espn.TABLES) {
            boolean on = t[0].equals(tableSlug);
            if (on) name = t[1];
            LinearLayout ch = SportCards.row(c);
            ch.setBackground(on ? roundFill(0x3D3DDC84, 18) : Glass.card(c, 18));
            ch.setPadding(Glass.dp(c, 10), Glass.dp(c, 6), Glass.dp(c, 14), Glass.dp(c, 6));
            ch.addView(SportCards.image(c, Competitions.logo(t[0]), 20, 20));
            TextView tt = SportCards.text(c, t[1], 13, Glass.TEXT, on ? Glass.bodySemi(c) : Glass.body(c));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.leftMargin = Glass.dp(c, 7);
            ch.addView(tt, tp);
            ch.setOnClickListener(x -> {
                tableFull = false;
                host.table(t[0]);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Glass.dp(c, 8);
            chips.addView(ch, lp);
        }
        hs.addView(chips);
        v.add(hs);
        if (tableRows != null && !tableRows.isEmpty()) {
            LinearLayout card = SportCards.card(c);
            card.addView(SportCards.compHead(c, Competitions.logo(tableSlug), name, ""));
            List<Integer> shown = tableFull ? all(tableRows.size()) : condensed(tableRows, tableMine);
            card.addView(tableGrid(shown), new LinearLayout.LayoutParams(-1, -2));
            card.addView(more(tableFull ? "Top and your clubs" : "Full table", () -> {
                tableFull = !tableFull;
                drawTable();
            }));
            v.add(card);
        }
        fill("table", v);
    }

    private static List<Integer> all(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(i);
        return out;
    }

    /** Pure: the rows to show folded: the top four, and each of your clubs with the one either side, in order. */
    static List<Integer> condensed(List<String[]> rows, Set<String> mine) {
        java.util.TreeSet<Integer> keep = new java.util.TreeSet<>();
        for (int i = 0; i < Math.min(4, rows.size()); i++) keep.add(i);
        for (int i = 0; i < rows.size(); i++) {
            if (mine != null && mine.contains(rows.get(i)[1])) {
                for (int k = Math.max(0, i - 1); k <= Math.min(rows.size() - 1, i + 1); k++) keep.add(k);
            }
        }
        if (keep.size() <= 4 && rows.size() > 4) keep.add(4);
        return new ArrayList<>(keep);
    }

    /** The table's number columns, in order. */
    static final String[] NUMBER_HEADS = {"Pld", "W", "D", "L", "GD", "Pts"};
    /** Each number column's width, in sp so that it grows with the text. */
    private static final int[] NUMBER_SP = {28, 23, 23, 23, 33, 34};

    /** Pure: a table row's numbers ({rank, id, name, logo, played, GD, points, group, won, drawn, lost}) as the columns go. */
    static String[] numbersOf(String[] r) {
        return new String[]{r[4], at(r, 8), at(r, 9), at(r, 10), r[5], r[6]};
    }

    private static String at(String[] r, int i) {
        return r.length > i && r[i] != null ? r[i] : "";
    }

    /**
     * The table's rows: the place, crest and club fixed at the left, the numbers (Pld, W, D, L, GD, Pts) beside them,
     * sliding sideways under the club's column when the phone is too narrow for all of them (the first few start slid
     * under it whole, so the points always show). A row opens the club's page.
     */
    private View tableGrid(List<Integer> shown) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int[] cols = new int[NUMBER_SP.length];
        for (int i = 0; i < cols.length; i++) cols[i] = sp(dm, NUMBER_SP[i]);
        // The section's margins and the card's padding either side (of its column, on a tablet with two).
        int inner = pageWidth() - 2 * Glass.dp(c, 16) - 2 * Glass.dp(c, 14);
        int[] fit = fit(inner, Glass.dp(c, 154), cols, Glass.dp(c, 6));
        int left = fit[0];
        int rowH = sp(dm, 34), groupH = sp(dm, 30), gapH = sp(dm, 18);
        LinearLayout fixed = SportCards.column(c), sliding = SportCards.column(c);
        fixed.addView(tableLeft(null, true, false), new LinearLayout.LayoutParams(left, rowH));
        sliding.addView(tableNumbers(NUMBER_HEADS, cols, true, false), new LinearLayout.LayoutParams(-1, rowH));
        int last = -1;
        String group = null;
        for (int i : shown) {
            String[] r = tableRows.get(i);
            if (!r[7].isEmpty() && !r[7].equals(group)) {
                group = r[7];
                TextView g = SportCards.text(c, group, 12, Glass.MUTED, Glass.bodySemi(c));
                g.setGravity(Gravity.BOTTOM);
                g.setPadding(Glass.dp(c, 6), 0, 0, Glass.dp(c, 4));
                fixed.addView(g, new LinearLayout.LayoutParams(left, groupH));
                sliding.addView(new View(c), new LinearLayout.LayoutParams(1, groupH));
            }
            if (last >= 0 && i != last + 1) {
                TextView gap = SportCards.text(c, "· · ·", 12, Glass.FAINT, Glass.body(c));
                gap.setGravity(Gravity.CENTER);
                fixed.addView(gap, new LinearLayout.LayoutParams(left, gapH));
                sliding.addView(new View(c), new LinearLayout.LayoutParams(1, gapH));
            }
            boolean mine = tableMine != null && tableMine.contains(r[1]);
            String[] n = numbersOf(r);
            View club = tableLeft(r, false, mine), figures = tableNumbers(n, cols, false, mine);
            View.OnClickListener open = x -> host.team(tableSlug, r);
            club.setOnClickListener(open);
            figures.setOnClickListener(open);
            figures.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            club.setContentDescription(r[0] + ", " + r[2] + ": played " + n[0] + (n[1].isEmpty() ? "" : ", won " + n[1] + ", drawn " + n[2]
                    + ", lost " + n[3]) + ", goal difference " + n[4] + ", " + n[5] + " points. Opens the club's page.");
            fixed.addView(club, new LinearLayout.LayoutParams(left, rowH));
            sliding.addView(figures, new LinearLayout.LayoutParams(-1, rowH));
            last = i;
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(true);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.addView(sliding, new FrameLayout.LayoutParams(-2, -2));
        LinearLayout grid = SportCards.row(c);
        grid.setGravity(Gravity.TOP);
        grid.addView(fixed, new LinearLayout.LayoutParams(left, -2));
        grid.addView(hs, new LinearLayout.LayoutParams(0, -2, 1f));
        if (fit[1] > 0) {
            hs.setHorizontalFadingEdgeEnabled(true);
            hs.setFadingEdgeLength(Glass.dp(c, 14));
            hs.post(() -> hs.scrollTo(fit[1], 0));
        }
        return grid;
    }

    /**
     * Pure: the club's column's width and how far the numbers start slid, for a card inner pixels wide, the numbers'
     * columns cols wide with pad after them. The club's column takes what the numbers leave (at least min); when they
     * don't all fit, the first few slide under it whole (the points always showing) and it widens to fill what the rest
     * leave.
     */
    static int[] fit(int inner, int min, int[] cols, int pad) {
        int numbers = pad;
        for (int c : cols) numbers += c;
        if (inner - min >= numbers) return new int[]{inner - numbers, 0};
        int hidden = 0;
        for (int k = 0; k < cols.length - 1 && numbers - hidden > inner - min; k++) hidden += cols[k];
        return new int[]{Math.max(min, inner - (numbers - hidden)), hidden};
    }

    private static int sp(android.util.DisplayMetrics dm, float v) {
        return Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, v, dm));
    }

    /** A row's fixed part: its place, crest and club (the heading's is empty). */
    private LinearLayout tableLeft(String[] r, boolean head, boolean mine) {
        LinearLayout row = SportCards.row(c);
        row.setPadding(Glass.dp(c, 6), 0, Glass.dp(c, 4), 0);
        if (mine) row.setBackground(half(true));
        row.addView(SportCards.text(c, head ? "" : r[0], 13, head ? Glass.FAINT : Glass.MUTED, Glass.bodySemi(c)),
                new LinearLayout.LayoutParams(Glass.dp(c, 24), -2));
        if (head) row.addView(new View(c), new LinearLayout.LayoutParams(Glass.dp(c, 20), 1));
        else row.addView(SportCards.image(c, r[3], 20, 20));
        TextView n = SportCards.text(c, head ? "" : r[2], 14, Glass.TEXT, mine ? Glass.bodySemi(c) : Glass.body(c));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
        np.leftMargin = Glass.dp(c, 8);
        row.addView(n, np);
        if (!head) row.setForeground(SportCards.touch(c));
        return row;
    }

    /** A row's numbers, each in its column, to the right. */
    private LinearLayout tableNumbers(String[] cells, int[] cols, boolean head, boolean mine) {
        LinearLayout row = SportCards.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.setPadding(0, 0, Glass.dp(c, 6), 0);
        if (mine) row.setBackground(half(false));
        for (int i = 0; i < cells.length; i++) {
            boolean pts = i == cells.length - 1;
            TextView t = SportCards.text(c, cells[i], pts && !head ? 14 : 13, head ? Glass.FAINT : pts ? Glass.TEXT : Glass.MUTED,
                    pts ? Glass.bodySemi(c) : Glass.body(c));
            t.setGravity(Gravity.END);
            row.addView(t, new LinearLayout.LayoutParams(cols[i], -2));
        }
        if (!head) row.setForeground(SportCards.touch(c));
        return row;
    }

    /** Your club's mark, in two halves (the fixed column's and the numbers'), rounded on its own side. */
    private android.graphics.drawable.GradientDrawable half(boolean left) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(0x223DDC84);
        float r = Glass.dp(c, 10);
        d.setCornerRadii(left ? new float[]{r, r, 0, 0, 0, 0, r, r} : new float[]{0, 0, r, r, r, r, 0, 0});
        return d;
    }

    // ---- Formula 1 --------------------------------------------------------------------------------------------------

    /** The next Grand Prix (its sessions, the channel) and the drivers' standings at the top, with photos and teams. */
    void f1(F1.Weekend w, List<F1.Standing> drivers, String channel) {
        List<View> v = new ArrayList<>();
        if (w != null || (drivers != null && !drivers.isEmpty())) {
            LinearLayout card = SportCards.card(c);
            LinearLayout head = SportCards.row(c);
            head.addView(SportCards.image(c, Competitions.F1_LOGO, 40, 20));
            LinearLayout words = SportCards.column(c);
            words.addView(SportCards.text(c, w == null ? "Formula 1" : w.name, 16, Glass.TEXT, Glass.heading(c, 600)));
            if (w != null && w.circuit != null && !w.circuit.isEmpty()) {
                words.addView(SportCards.text(c, (w.round == null || w.round.isEmpty() ? "" : "Round " + w.round + " · ") + w.circuit,
                        12, Glass.MUTED, Glass.body(c)));
            }
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
            wp.leftMargin = Glass.dp(c, 10);
            head.addView(words, wp);
            if (channel != null) head.addView(SportCards.channel(c, channel, 14));
            card.addView(head);
            if (w != null) {
                long now = System.currentTimeMillis();
                boolean nextMarked = false;
                LinearLayout sessions = SportCards.column(c);
                sessions.setPadding(0, Glass.dp(c, 10), 0, 0);
                for (Object[] s : w.sessions) {
                    long at = (Long) s[1];
                    if (at == 0) continue;
                    boolean next = !nextMarked && at + 2 * 3_600_000L > now;
                    if (next) nextMarked = true;
                    LinearLayout r = SportCards.row(c);
                    r.setPadding(0, Glass.dp(c, 3), 0, Glass.dp(c, 3));
                    View dot = new View(c);
                    dot.setBackground(roundFill(next ? 0xFFE10600 : 0x33FFFFFF, 4));
                    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Glass.dp(c, 8), Glass.dp(c, 8));
                    dp.rightMargin = Glass.dp(c, 10);
                    r.addView(dot, dp);
                    r.addView(SportCards.text(c, (String) s[0], 14, next ? Glass.TEXT : Glass.SOFT, next ? Glass.bodySemi(c) : Glass.body(c)),
                            new LinearLayout.LayoutParams(0, -2, 1f));
                    r.addView(SportCards.text(c, SportCards.dayAndTime(at), 13, next ? Glass.TEXT : Glass.MUTED, Glass.body(c)));
                    sessions.addView(r);
                }
                card.addView(sessions);
            }
            if (drivers != null && !drivers.isEmpty()) {
                card.addView(SportCards.divider(c));
                for (int i = 0; i < drivers.size() && i < 5; i++) card.addView(driver(drivers.get(i)));
            }
            if (w != null) card.setOnClickListener(x -> host.event(SportEvent.grandPrix(w, channel, System.currentTimeMillis())));
            else if (channel != null) card.setOnClickListener(x -> host.watch(channel));
            v.add(card);
        }
        fill("f1", v);
    }

    private View driver(F1.Standing d) {
        int colour = F1.colour(d.teamId);
        LinearLayout r = SportCards.row(c);
        r.setPadding(0, Glass.dp(c, 5), 0, Glass.dp(c, 5));
        r.addView(SportCards.text(c, d.position, 13, Glass.MUTED, Glass.bodySemi(c)), new LinearLayout.LayoutParams(Glass.dp(c, 22), -2));
        View bar = new View(c);
        bar.setBackground(roundFill(colour, 2));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Glass.dp(c, 4), Glass.dp(c, 30));
        bp.rightMargin = Glass.dp(c, 10);
        r.addView(bar, bp);
        View photo = SportCards.photo(c, d.headshot, d.name, colour, 34);
        r.addView(photo);
        LinearLayout words = SportCards.column(c);
        words.addView(SportCards.text(c, d.name, 14, Glass.TEXT, Glass.bodySemi(c)));
        LinearLayout team = SportCards.row(c);
        String[] logos = F1.logos(d.teamId, d.season);
        if (logos.length > 0) {
            ImageView tl = SportCards.image(c, null, 16, 16);
            tl.setVisibility(View.VISIBLE);
            FrameLayout tile = new FrameLayout(c);
            tile.setBackground(roundFill(0xFFFFFFFF, 3));
            tile.setPadding(Glass.dp(c, 1), Glass.dp(c, 1), Glass.dp(c, 1), Glass.dp(c, 1));
            tile.addView(tl, new FrameLayout.LayoutParams(Glass.dp(c, 14), Glass.dp(c, 14)));
            Ui.load(tl, logos, null, () -> tile.setVisibility(View.GONE));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.rightMargin = Glass.dp(c, 6);
            team.addView(tile, tlp);
        }
        team.addView(SportCards.text(c, d.team, 12, Glass.MUTED, Glass.body(c)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = Glass.dp(c, 2);
        words.addView(team, tp);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = Glass.dp(c, 10);
        r.addView(words, wp);
        r.addView(SportCards.text(c, d.points, 15, Glass.TEXT, Glass.bodySemi(c)));
        r.setContentDescription(d.position + ", " + d.name + ", " + d.team + ", " + d.points + " points. Opens his page");
        // His page (the championship to swipe through), as the family's picture of a driver's has it.
        r.setBackground(SportCards.touch(c));
        r.setOnClickListener(x -> c.startActivity(DriverActivity.intent(c, d.driverId, d.name)));
        return r;
    }

    // ---- tennis, UFC, the NFL, the NBA, golf -------------------------------------------------------------------------

    /** Each tour's week: live matches first, then what comes today, then the latest results, with flags and sets. */
    void tennis(List<Object[]> tours) {
        List<View> v = new ArrayList<>();
        for (Object[] t : tours) {
            @SuppressWarnings("unchecked") List<SportBoard> matches = (List<SportBoard>) t[1];
            if (matches.isEmpty()) continue;
            String tour = (String) t[0], channel = (String) t[2];
            LinearLayout card = SportCards.card(c);
            LinearLayout head = SportCards.row(c);
            head.addView(SportCards.sportMark(c, OnTv.TENNIS, "", 26));
            LinearLayout words = SportCards.column(c);
            words.addView(SportCards.text(c, matches.get(0).event, 15, Glass.TEXT, Glass.bodySemi(c)));
            words.addView(SportCards.text(c, tour + (matches.get(0).stage.isEmpty() ? "" : " · " + matches.get(0).stage), 12, Glass.MUTED, Glass.body(c)));
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
            wp.leftMargin = Glass.dp(c, 10);
            head.addView(words, wp);
            if (channel != null) head.addView(SportCards.channel(c, channel, 14));
            card.addView(head);
            LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2);
            gap.topMargin = Glass.dp(c, 10);
            boolean first = true;
            for (SportBoard s : pickMatches(matches, 6)) {
                if (!first) card.addView(SportCards.divider(c));
                View row = SportCards.tennisRow(c, s);
                row.setOnClickListener(x -> host.event(SportEvent.tennis(s, tour, channel)));
                row.setBackground(SportCards.touch(c));
                card.addView(row, first ? gap : new LinearLayout.LayoutParams(-1, -2));
                first = false;
            }
            if (channel != null) card.setOnClickListener(x -> host.watch(channel));
            v.add(card);
        }
        fill("tennis", v);
    }

    /** Pure: the matches worth showing: live ones, then the next to come, then the latest finished, at most max. */
    static List<SportBoard> pickMatches(List<SportBoard> all, int max) {
        List<SportBoard> out = new ArrayList<>();
        for (SportBoard s : all) if (s.live() && out.size() < max) out.add(s);
        List<SportBoard> next = new ArrayList<>(), done = new ArrayList<>();
        for (SportBoard s : all) {
            if (s.live()) continue;
            if (s.over()) done.add(s); else next.add(s);
        }
        next.sort((a, b) -> Long.compare(a.start, b.start));
        done.sort((a, b) -> Long.compare(b.start, a.start));
        for (SportBoard s : next) if (out.size() < Math.max(1, max - 2) || done.isEmpty() && out.size() < max) out.add(s);
        for (SportBoard s : done) if (out.size() < max) out.add(s);
        return out;
    }

    /** UFC's next card: the main event's fighters face to face, then the other bouts. */
    void fights(List<SportBoard> card, String channel) {
        List<View> v = new ArrayList<>();
        if (card != null && !card.isEmpty()) v.add(SportCards.fightCard(c, card, channel, x -> host.event(SportEvent.fightNight(card, channel))));
        fill("ufc", v);
    }

    void nfl(List<MoreSports.Match> games) {
        matches("nfl", "NFL", OnTv.LEAGUES + "nfl.png", games);
    }

    void nba(List<MoreSports.Match> games) {
        matches("nba", "NBA", OnTv.LEAGUES + "nba.png", games);
    }

    private final Map<String, Boolean> allShown = new LinkedHashMap<>();

    private void matches(String key, String league, String logo, List<MoreSports.Match> games) {
        List<View> v = new ArrayList<>();
        if (games != null && !games.isEmpty()) {
            boolean all = Boolean.TRUE.equals(allShown.get(key));
            LinearLayout card = SportCards.card(c);
            int live = 0;
            for (MoreSports.Match m : games) if (m.live()) live++;
            card.addView(SportCards.compHead(c, logo, league, live > 0 ? live + " live" : games.size() + " games"));
            int max = all ? games.size() : 6;
            for (int i = 0; i < games.size() && i < max; i++) {
                MoreSports.Match m = games.get(i);
                if (i > 0) card.addView(SportCards.divider(c));
                card.addView(SportCards.matchRow(c, m, x -> host.event(SportEvent.game(m, key))));
            }
            if (games.size() > 6) card.addView(more(all ? "Show less" : "Show all " + games.size(), () -> {
                allShown.put(key, !all);
                matches(key, league, logo, games);
            }));
            v.add(card);
        }
        fill(key, v);
    }

    void golf(List<MoreSports.Leaderboard> boards) {
        List<View> v = new ArrayList<>();
        for (MoreSports.Leaderboard b : boards) {
            if (b == null || b.rows.isEmpty()) continue;
            View card = SportCards.golfCard(c, b);
            card.setOnClickListener(x -> host.event(SportEvent.golf(b)));
            v.add(card);
        }
        fill("golf", v);
    }

    // ---- football coming up ----------------------------------------------------------------------------------------

    /** The coming days' games (your clubs' and the Premier League's), a card a day, each with its channel's logo. */
    void comingUp(Map<LocalDate, List<Object[]>> days, Set<String> mine) {
        List<View> v = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK);
        for (Map.Entry<LocalDate, List<Object[]>> e : days.entrySet()) {
            if (e.getKey().equals(LocalDate.now())) continue;
            LinearLayout card = SportCards.card(c);
            card.addView(SportCards.compHead(c, null, e.getKey().format(f), e.getValue().size() + (e.getValue().size() == 1 ? " game" : " games")));
            boolean first = true;
            for (Object[] x : e.getValue()) {
                Game g = (Game) x[0];
                if (!first) card.addView(SportCards.divider(c));
                first = false;
                LinearLayout r = SportCards.row(c);
                r.addView(SportCards.image(c, g.leagueLogo, 18, 18));
                View row = SportCards.gameRow(c, g, (String) x[1], g.involves(mine), z -> host.game(g));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f);
                rp.leftMargin = Glass.dp(c, 8);
                r.addView(row, rp);
                card.addView(r);
            }
            v.add(card);
        }
        fill("coming", v);
    }
}
