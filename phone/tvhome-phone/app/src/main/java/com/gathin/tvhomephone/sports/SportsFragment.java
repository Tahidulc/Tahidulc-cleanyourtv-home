package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;
import com.google.android.material.tabs.TabLayout;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sports, as the TV's Sports page: Match Centre (today's games under each competition's logo, your clubs and live ones
 * first, each opening its own page), the tables (# · club · P · W · D · L · GD · Pts, for the Premier League and the
 * competitions the TV shows), F1 (the next Grand Prix's sessions and both standings, with the drivers' photos, the teams'
 * logos and colours) and This Weekend (the fixtures by competition with where they are on as the channel's logo, a
 * link into Live TV when the playlist has the channel, and Remind me). Pull down to read again.
 */
public class SportsFragment extends Fragment {
    private LinearLayout page;
    private SwipeRefreshLayout swipe;
    private int tab;
    private String table = "eng.1";
    private boolean f1Teams;
    private int gen;

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(c.getColor(R.color.ground));
        TextView title = Ui.heading(c, "Sports", c.getColor(R.color.sports));
        title.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), 0);
        root.addView(title);
        TabLayout tabs = new TabLayout(c);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabs.setBackgroundColor(c.getColor(R.color.ground));
        tabs.setSelectedTabIndicatorColor(c.getColor(R.color.sports));
        tabs.setTabTextColors(c.getColor(R.color.text_dim), c.getColor(R.color.text));
        for (int id : new int[]{R.string.sports_tab_centre, R.string.sports_tab_tables, R.string.sports_tab_f1, R.string.sports_tab_weekend}) {
            tabs.addTab(tabs.newTab().setText(id));
        }
        root.addView(tabs, Ui.matchWrap());
        swipe = new SwipeRefreshLayout(c);
        ScrollView scroll = new ScrollView(c);
        page = Ui.column(c);
        int pad = Ui.dp(c, 16);
        page.setPadding(pad, Ui.dp(c, 4), pad, pad);
        scroll.addView(page);
        swipe.addView(scroll);
        swipe.setOnRefreshListener(this::load);
        root.addView(swipe, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) { tab = t.getPosition(); load(); }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) { load(); }
        });
        load();
        return root;
    }

    private void load() {
        if (page == null) return;
        int my = ++gen;
        swipe.setRefreshing(true);
        int which = tab;
        Context app = requireContext().getApplicationContext();
        App.io.execute(() -> {
            Object data;
            switch (which) {
                case 1: data = Espn.table(table); break;
                case 2: data = new Object[]{F1.next(), f1Teams ? F1.teams() : F1.drivers()}; break;
                case 3: data = weekend(app); break;
                default: data = today(app);
            }
            App.main.post(() -> {
                if (my != gen || !isAdded()) return;
                swipe.setRefreshing(false);
                page.removeAllViews();
                switch (which) {
                    case 1: showTable(cast(data)); break;
                    case 2: showF1((Object[]) data); break;
                    case 3: showWeekend(cast(data)); break;
                    default: showToday(cast(data));
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) { return (T) o; }

    // ---- Match Centre -------------------------------------------------------------------------------------------

    /** The key of the starred clubs' games, ahead of the competitions' slugs in today(). */
    static final String MINE = "";

    /** Today's games by competition (slug), the starred clubs' first (under MINE) when they play. */
    static Map<String, List<Game>> today(Context c) {
        Map<String, List<Game>> out = new LinkedHashMap<>();
        Set<String> teams = Clubs.starred(c);
        List<Game> mine = new ArrayList<>();
        for (String[] l : Espn.LEAGUES) {
            List<Game> games = Espn.scoreboard(l[0], LocalDate.now(), 30_000L);
            if (games.isEmpty()) continue;
            games.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state) : Long.compare(a.kickoff, b.kickoff));
            for (Game g : games) if (g.involves(teams)) mine.add(g);
            out.put(l[0], games);
        }
        if (!mine.isEmpty()) {
            Map<String, List<Game>> first = new LinkedHashMap<>();
            first.put(MINE, mine);
            first.putAll(out);
            return first;
        }
        return out;
    }

    private void showToday(Map<String, List<Game>> days) {
        Context c = requireContext();
        Set<String> teams = Clubs.starred(c);
        if (days.isEmpty()) { Ui.empty(page, "No games today in the competitions the TV follows."); return; }
        for (Map.Entry<String, List<Game>> e : days.entrySet()) {
            boolean mine = MINE.equals(e.getKey());
            if (mine) page.addView(Ui.heading(c, "Your clubs", c.getColor(R.color.sports)));
            else page.addView(Ui.compHeading(c, Espn.name(e.getKey()), Competitions.logo(e.getValue().get(0)), c.getColor(R.color.sports)));
            List<View> cards = new ArrayList<>();
            // The starred clubs' games come from several competitions: each card says which.
            for (Game g : e.getValue()) cards.add(GameRow.build(c, g, g.involves(teams), mine));
            GameRow.addAll(page, cards);
        }
    }

    // ---- Tables -------------------------------------------------------------------------------------------------

    private void showTable(List<String[]> rows) {
        Context c = requireContext();
        HorizontalScrollView chipsScroll = new HorizontalScrollView(c);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(c);
        String name = "";
        for (String[] t : Espn.TABLES) {
            boolean on = t[0].equals(table);
            if (on) name = t[1];
            LinearLayout chip = Ui.row(c);
            chip.setBackground(on ? Ui.fill(c, 0x553DDC84, 18) : Ui.glass(c, 18));
            chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 6));
            String logo = Competitions.logo(t[0]);
            if (logo != null) {
                ImageView v = new ImageView(c);
                v.setScaleType(ImageView.ScaleType.FIT_CENTER);
                v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
                vp.rightMargin = Ui.dp(c, 7);
                chip.addView(v, vp);
                Ui.load(v, logo, null, () -> v.setVisibility(View.GONE));
            }
            chip.addView(Ui.text(c, t[1], 14, c.getColor(R.color.text), on));
            chip.setContentDescription(t[1] + (on ? ", shown" : ""));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            chip.setOnClickListener(v -> { table = t[0]; load(); });
            chips.addView(chip, lp);
        }
        chipsScroll.addView(chips);
        page.addView(chipsScroll, Ui.margins(c, 0, 10, 0, 2));
        page.addView(Ui.compHeading(c, name, Competitions.logo(table), c.getColor(R.color.sports)));
        if (rows.isEmpty()) { Ui.empty(page, "The table isn't available just now."); return; }
        Set<String> teams = Clubs.starred(c);
        LinearLayout head = tableRow(c, new String[]{"#", null, "Club", "P", "W", "D", "L", "GD", "Pts"}, false, true);
        head.setAlpha(0.7f);
        page.addView(head);
        String group = null;
        for (String[] r : rows) {
            if (!r[7].isEmpty() && !r[7].equals(group)) {
                group = r[7];
                page.addView(Ui.heading(c, group, c.getColor(R.color.text_soft)));
            }
            String w = r.length > 10 ? r[8] : "", d = r.length > 10 ? r[9] : "", l = r.length > 10 ? r[10] : "";
            page.addView(tableRow(c, new String[]{r[0], r[3], r[2], r[4], w, d, l, r[5], r[6]}, teams.contains(r[1]), false));
        }
    }

    /**
     * One line of a table: {rank, crest, club, P, W, D, L, GD, Pts}. Narrow number columns and the club's name cut short
     * to fit a phone upright; a little more room on a tablet.
     */
    private static LinearLayout tableRow(Context c, String[] cells, boolean mine, boolean head) {
        boolean wide = Ui.wide(c);
        int num = Ui.dp(c, wide ? 38 : 24), gdW = Ui.dp(c, wide ? 46 : 32), ptsW = Ui.dp(c, wide ? 48 : 34);
        float small = wide ? 14 : 13, big = wide ? 15 : 14;
        LinearLayout r = Ui.row(c);
        r.setPadding(Ui.dp(c, wide ? 8 : 4), Ui.dp(c, 7), Ui.dp(c, wide ? 8 : 4), Ui.dp(c, 7));
        if (mine) r.setBackground(Ui.fill(c, 0x2E4F8BFF, 8));
        r.addView(Ui.text(c, cells[0], small, c.getColor(R.color.text_soft), true), new LinearLayout.LayoutParams(Ui.dp(c, wide ? 32 : 24), -2));
        int crest = Ui.dp(c, wide ? 24 : 20);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(crest, crest);
        cp.rightMargin = Ui.dp(c, wide ? 10 : 6);
        if (cells[1] != null && !cells[1].isEmpty()) {
            ImageView v = Ui.image(c, cells[1], wide ? 24 : 20);
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            r.addView(v, cp);
        } else {
            r.addView(new View(c), cp);
        }
        TextView n = Ui.text(c, cells[2], big, c.getColor(R.color.text), mine);
        n.setMaxLines(1);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(n, Ui.weight(1));
        for (int i = 3; i <= 7; i++) {
            TextView t = Ui.text(c, cells[i], small, c.getColor(R.color.text_soft), head);
            t.setGravity(Gravity.END);
            t.setMaxLines(1);
            r.addView(t, new LinearLayout.LayoutParams(i == 7 ? gdW : num, -2));
        }
        TextView t = Ui.text(c, cells[8], big, c.getColor(R.color.text), true);
        t.setGravity(Gravity.END);
        t.setMaxLines(1);
        r.addView(t, new LinearLayout.LayoutParams(ptsW, -2));
        if (!head) {
            r.setContentDescription(cells[0] + ", " + cells[2] + ": played " + cells[3] + ", won " + cells[4] + ", drawn " + cells[5]
                    + ", lost " + cells[6] + ", goal difference " + cells[7] + ", " + cells[8] + " points");
        }
        return r;
    }

    // ---- F1 -----------------------------------------------------------------------------------------------------

    private void showF1(Object[] data) {
        Context c = requireContext();
        F1.Weekend w = (F1.Weekend) data[0];
        if (w != null) {
            LinearLayout card = Ui.card(c, null);
            card.setBackground(Ui.vertical(0xFFB3121B, 0xFF5A0A10));
            LinearLayout top = Ui.row(c);
            // F1's red logo on a small white tile, as it would vanish on the card's red.
            FrameLayout tile = new FrameLayout(c);
            tile.setBackground(Ui.fill(c, 0xFFFFFFFF, 5));
            tile.setPadding(Ui.dp(c, 5), Ui.dp(c, 3), Ui.dp(c, 5), Ui.dp(c, 3));
            ImageView f1 = new ImageView(c);
            f1.setScaleType(ImageView.ScaleType.FIT_CENTER);
            f1.setContentDescription("Formula 1");
            tile.addView(f1, new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 16)));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
            tp.rightMargin = Ui.dp(c, 10);
            top.addView(tile, tp);
            Ui.load(f1, Competitions.F1_LOGO, null, () -> tile.setVisibility(View.GONE));
            top.addView(Ui.text(c, "NEXT GRAND PRIX", 12, 0xCCFFFFFF, true));
            card.addView(top);
            TextView gp = Ui.text(c, w.name, 22, c.getColor(R.color.text), true);
            gp.setPadding(0, Ui.dp(c, 6), 0, 0);
            card.addView(gp);
            if (!w.circuit.isEmpty()) card.addView(Ui.text(c, w.circuit, 14, 0xCCFFFFFF, false));
            DateTimeFormatter f = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm");
            for (Object[] s : w.sessions) {
                long at = (Long) s[1];
                if (at == 0) continue;
                LinearLayout r = Ui.row(c);
                r.setPadding(0, Ui.dp(c, 6), 0, 0);
                r.addView(Ui.text(c, (String) s[0], 15, c.getColor(R.color.text), "Race".equals(s[0])), Ui.weight(1));
                r.addView(Ui.text(c, Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(f), 14, 0xE6FFFFFF, false));
                card.addView(r);
            }
            page.addView(card, Ui.margins(c, 0, 12, 0, 6));
            page.addView(Ui.liveOn(c, "Sky Sports F1"));
        }
        LinearLayout chips = Ui.row(c);
        for (int i = 0; i < 2; i++) {
            boolean teams = i == 1;
            TextView chip = Ui.text(c, teams ? "Teams" : "Drivers", 14, c.getColor(R.color.text), teams == f1Teams);
            chip.setBackground(teams == f1Teams ? Ui.fill(c, 0x66E10600, 18) : Ui.glass(c, 18));
            chip.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            chip.setOnClickListener(v -> { f1Teams = teams; load(); });
            chips.addView(chip, lp);
        }
        page.addView(chips, Ui.margins(c, 0, 14, 0, 8));
        List<F1.Standing> rows = cast(data[1]);
        if (rows.isEmpty()) { Ui.empty(page, "The standings aren't available just now."); return; }
        for (F1.Standing r : rows) page.addView(f1Row(c, r, f1Teams));
    }

    /**
     * One line of F1's standings: the place, the team's colour bar, the driver's photo (his initials on the team's
     * colour when ESPN has none) or the team's logo, the name with the team under it, and the points.
     */
    private static View f1Row(Context c, F1.Standing r, boolean teams) {
        int colour = F1.colour(r.teamId);
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6));
        row.addView(Ui.text(c, r.position, 14, c.getColor(R.color.text_soft), true), new LinearLayout.LayoutParams(Ui.dp(c, 28), -2));
        View bar = new View(c);
        bar.setBackground(Ui.fill(c, colour, 2));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(c, 4), Ui.dp(c, 40));
        bp.rightMargin = Ui.dp(c, 10);
        row.addView(bar, bp);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
        pp.rightMargin = Ui.dp(c, 12);
        row.addView(teams ? teamTile(c, r, 40) : photo(c, r, colour, 40), pp);
        LinearLayout who = Ui.column(c);
        TextView name = Ui.text(c, r.name, 15, c.getColor(R.color.text), true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        who.addView(name);
        if (teams) {
            who.addView(Ui.text(c, ("1".equals(r.wins) ? "1 win" : r.wins + " wins"), 12, c.getColor(R.color.text_dim), false));
        } else if (!r.team.isEmpty()) {
            LinearLayout team = Ui.row(c);
            team.setPadding(0, Ui.dp(c, 2), 0, 0);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18));
            sp.rightMargin = Ui.dp(c, 6);
            team.addView(teamTile(c, r, 18), sp);
            TextView t = Ui.text(c, r.team, 12, c.getColor(R.color.text_dim), false);
            t.setMaxLines(1);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            team.addView(t, Ui.weight(1));
            who.addView(team);
        }
        row.addView(who, Ui.weight(1));
        row.addView(Ui.text(c, r.points + " pts", 15, c.getColor(R.color.text), true));
        row.setContentDescription(r.position + ", " + r.name + (teams ? ", " + r.wins + " wins" : r.team.isEmpty() ? "" : ", " + r.team)
                + ", " + r.points + " points");
        return row;
    }

    /** The driver's photo from ESPN in a circle of his team's colour; his initials on it until (or unless) it loads. */
    private static View photo(Context c, F1.Standing r, int colour, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable round = new GradientDrawable();
        round.setShape(GradientDrawable.OVAL);
        round.setColors(new int[]{colour, darker(colour)});
        f.setBackground(round);
        f.setClipToOutline(true);
        TextView initials = Ui.text(c, F1.initials(r.name), sizeDp * 0.36f, readableOn(colour), true);
        initials.setGravity(Gravity.CENTER);
        f.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        if (r.headshot != null) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            f.addView(v, new FrameLayout.LayoutParams(-1, -1));
            Ui.load(v, r.headshot, () -> initials.setVisibility(View.GONE), () -> {
                v.setVisibility(View.GONE);
                initials.setVisibility(View.VISIBLE);
            });
        }
        f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return f;
    }

    /**
     * The team's logo from formula1.com on a small white tile (several are black: they would vanish on the navy); this
     * season's, else last season's; the team's initials on its colour when neither loads.
     */
    private static View teamTile(Context c, F1.Standing r, int sizeDp) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.fill(c, 0xFFFFFFFF, sizeDp / 5f));
        int pad = Ui.dp(c, sizeDp * 0.1f);
        f.setPadding(pad, pad, pad, pad);
        TextView initials = Ui.text(c, F1.initials(r.team.isEmpty() ? r.name : r.team), sizeDp * 0.32f, readableOn(F1.colour(r.teamId)), true);
        initials.setGravity(Gravity.CENTER);
        initials.setVisibility(View.GONE);
        f.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        f.addView(v, new FrameLayout.LayoutParams(-1, -1));
        Runnable none = () -> {
            v.setVisibility(View.GONE);
            f.setBackground(Ui.fill(c, F1.colour(r.teamId), sizeDp / 5f));
            if (sizeDp >= 30) initials.setVisibility(View.VISIBLE);
        };
        String[] logos = F1.logos(r.teamId, r.season);
        if (logos.length == 0) none.run(); else Ui.load(v, logos, null, none);
        f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return f;
    }

    private static int darker(int colour) {
        return 0xFF000000 | (Math.round(((colour >> 16) & 0xFF) * 0.6f) << 16) | (Math.round(((colour >> 8) & 0xFF) * 0.6f) << 8)
                | Math.round((colour & 0xFF) * 0.6f);
    }

    /** White on a dark colour, navy on a pale one (Haas's grey, Mercedes's teal). */
    private static int readableOn(int colour) {
        double lum = (0.299 * ((colour >> 16) & 0xFF) + 0.587 * ((colour >> 8) & 0xFF) + 0.114 * (colour & 0xFF)) / 255.0;
        return lum > 0.62 ? 0xFF0A0D33 : 0xFFFFFFFF;
    }

    // ---- This Weekend -------------------------------------------------------------------------------------------

    /** The days of the coming weekend (Friday to Monday; from today when it is already Friday, Saturday or Sunday). */
    static List<LocalDate> weekendDays(LocalDate today) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate d = today;
        DayOfWeek w = d.getDayOfWeek();
        if (w != DayOfWeek.FRIDAY && w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY) {
            while (d.getDayOfWeek() != DayOfWeek.FRIDAY) d = d.plusDays(1);
        }
        while (out.size() < 4 && (out.isEmpty() || d.getDayOfWeek() != DayOfWeek.TUESDAY)) {
            out.add(d);
            d = d.plusDays(1);
        }
        return out;
    }

    /** day → its games: the starred clubs' anywhere, and every Premier League game. Each with where it is on. */
    static Map<LocalDate, List<Object[]>> weekend(Context c) {
        Map<LocalDate, List<Object[]>> out = new LinkedHashMap<>();
        Set<String> teams = Clubs.starred(c);
        for (LocalDate day : weekendDays(LocalDate.now())) {
            List<Object[]> games = new ArrayList<>();
            for (String[] l : Espn.LEAGUES) {
                for (Game g : Espn.scoreboard(l[0], day, 30 * 60_000L)) {
                    if (!g.involves(teams) && !"eng.1".equals(g.slug)) continue;
                    if (g.state == Game.POST) continue;
                    games.add(new Object[]{g, Channels.best(c, g)});
                }
            }
            games.sort((a, b) -> Long.compare(((Game) a[0]).kickoff, ((Game) b[0]).kickoff));
            if (!games.isEmpty()) out.put(day, games);
        }
        return out;
    }

    private void showWeekend(Map<LocalDate, List<Object[]>> days) {
        Context c = requireContext();
        Set<String> teams = Clubs.starred(c);
        if (days.isEmpty()) { Ui.empty(page, "No fixtures found for the weekend yet."); return; }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEEE d MMMM");
        for (Map.Entry<LocalDate, List<Object[]>> e : days.entrySet()) {
            page.addView(Ui.heading(c, e.getKey().format(f), c.getColor(R.color.sports)));
            // The day's games under each competition's logo and name, in the order their first games start.
            Map<String, List<Object[]>> byCompetition = new LinkedHashMap<>();
            for (Object[] x : e.getValue()) {
                Game g = (Game) x[0];
                byCompetition.computeIfAbsent(g.slug == null ? "" : g.slug, k -> new ArrayList<>()).add(x);
            }
            for (List<Object[]> games : byCompetition.values()) {
                Game first = (Game) games.get(0)[0];
                LinearLayout h = Ui.compHeading(c, first.leagueName == null || first.leagueName.isEmpty() ? first.league : first.leagueName,
                        Competitions.logo(first), c.getColor(R.color.text_soft));
                h.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
                page.addView(h);
                weekendCards(c, games, teams);
            }
        }
    }

    private void weekendCards(Context c, List<Object[]> games, Set<String> teams) {
        List<View> cards = new ArrayList<>();
        for (Object[] x : games) {
            Game g = (Game) x[0];
            String channel = (String) x[1];
            LinearLayout card = GameRow.build(c, g, g.involves(teams));
            LinearLayout foot = Ui.row(c);
            foot.setPadding(0, Ui.dp(c, 6), 0, 0);
            View where = channel == null ? new View(c) : Ui.liveOn(c, channel);
            foot.addView(where, Ui.weight(1));
            Button remind = new Button(c);
            String key = "game:" + g.id;
            remind.setText(SyncStore.get(c, "reminder", key) != null ? R.string.sports_reminded : R.string.sports_remind_me);
            remind.setOnClickListener(v -> {
                if (SyncStore.get(c, "reminder", key) != null) {
                    SyncStore.delete(c, "reminder", key);
                    remind.setText(R.string.sports_remind_me);
                } else {
                    SyncStore.put(c, "reminder", key, GameActivity.reminder(g, channel));
                    remind.setText(R.string.sports_reminded);
                }
            });
            foot.addView(remind, new LinearLayout.LayoutParams(-2, -2));
            card.addView(foot);
            cards.add(card);
        }
        GameRow.addAll(page, cards);
    }
}
