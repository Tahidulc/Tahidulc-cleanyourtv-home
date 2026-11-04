package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
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
 * Sports, as the TV's Sports page: Match Centre (today's games, your clubs and live ones first, each opening its own
 * page), the tables (the Premier League and the competitions the TV shows), F1 (the next Grand Prix's sessions and both
 * standings) and This weekend (the fixtures with where they are on, a link into Live TV when the playlist has the
 * channel, and Remind me). Pull down to read again.
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

    static Map<String, List<Game>> today(Context c) {
        Map<String, List<Game>> out = new LinkedHashMap<>();
        Set<String> teams = Clubs.starred(c);
        List<Game> mine = new ArrayList<>();
        for (String[] l : Espn.LEAGUES) {
            List<Game> games = Espn.scoreboard(l[0], LocalDate.now(), 30_000L);
            if (games.isEmpty()) continue;
            games.sort((a, b) -> a.state != b.state ? Integer.compare(a.state, b.state) : Long.compare(a.kickoff, b.kickoff));
            for (Game g : games) if (g.involves(teams)) mine.add(g);
            out.put(l[2], games);
        }
        if (!mine.isEmpty()) {
            Map<String, List<Game>> first = new LinkedHashMap<>();
            first.put("Your clubs", mine);
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
            page.addView(Ui.heading(c, e.getKey(), c.getColor(R.color.sports)));
            List<View> cards = new ArrayList<>();
            for (Game g : e.getValue()) cards.add(GameRow.build(c, g, g.involves(teams)));
            GameRow.addAll(page, cards);
        }
    }

    // ---- Tables -------------------------------------------------------------------------------------------------

    private void showTable(List<String[]> rows) {
        Context c = requireContext();
        HorizontalScrollView chipsScroll = new HorizontalScrollView(c);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(c);
        for (String[] t : Espn.TABLES) {
            TextView chip = Ui.text(c, t[1], 14, c.getColor(R.color.text), t[0].equals(table));
            chip.setBackground(t[0].equals(table) ? Ui.fill(c, 0x553DDC84, 18) : Ui.glass(c, 18));
            chip.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            chip.setOnClickListener(v -> { table = t[0]; load(); });
            chips.addView(chip, lp);
        }
        chipsScroll.addView(chips);
        page.addView(chipsScroll, Ui.margins(c, 0, 10, 0, 8));
        if (rows.isEmpty()) { Ui.empty(page, "The table isn't available just now."); return; }
        Set<String> teams = Clubs.starred(c);
        LinearLayout head = tableRow(c, "#", null, "Club", "P", "GD", "Pts", false);
        head.setAlpha(0.7f);
        page.addView(head);
        String group = null;
        for (String[] r : rows) {
            if (!r[7].isEmpty() && !r[7].equals(group)) {
                group = r[7];
                page.addView(Ui.heading(c, group, c.getColor(R.color.text_soft)));
            }
            page.addView(tableRow(c, r[0], r[3], r[2], r[4], r[5], r[6], teams.contains(r[1])));
        }
    }

    private static LinearLayout tableRow(Context c, String rank, String logo, String name, String p, String gd, String pts, boolean mine) {
        LinearLayout r = Ui.row(c);
        r.setPadding(Ui.dp(c, 8), Ui.dp(c, 7), Ui.dp(c, 8), Ui.dp(c, 7));
        if (mine) r.setBackground(Ui.fill(c, 0x2E4F8BFF, 8));
        r.addView(Ui.text(c, rank, 14, c.getColor(R.color.text_soft), true), new LinearLayout.LayoutParams(Ui.dp(c, 30), -2));
        if (logo != null) {
            android.widget.ImageView v = Ui.image(c, logo, 22);
            ((LinearLayout.LayoutParams) v.getLayoutParams()).rightMargin = Ui.dp(c, 8);
            r.addView(v);
        }
        TextView n = Ui.text(c, name, 15, c.getColor(R.color.text), mine);
        n.setMaxLines(1);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(n, Ui.weight(1));
        for (String s : new String[]{p, gd}) {
            TextView t = Ui.text(c, s, 14, c.getColor(R.color.text_soft), false);
            t.setGravity(Gravity.END);
            r.addView(t, new LinearLayout.LayoutParams(Ui.dp(c, 40), -2));
        }
        TextView t = Ui.text(c, pts, 15, c.getColor(R.color.text), true);
        t.setGravity(Gravity.END);
        r.addView(t, new LinearLayout.LayoutParams(Ui.dp(c, 44), -2));
        return r;
    }

    // ---- F1 -----------------------------------------------------------------------------------------------------

    private void showF1(Object[] data) {
        Context c = requireContext();
        F1.Weekend w = (F1.Weekend) data[0];
        if (w != null) {
            LinearLayout card = Ui.card(c, null);
            card.setBackground(Ui.vertical(0xFFB3121B, 0xFF5A0A10));
            card.addView(Ui.text(c, "NEXT GRAND PRIX", 12, 0xCCFFFFFF, true));
            card.addView(Ui.text(c, w.name, 22, c.getColor(R.color.text), true));
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
        List<String[]> rows = cast(data[1]);
        if (rows.isEmpty()) { Ui.empty(page, "The standings aren't available just now."); return; }
        for (String[] r : rows) {
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 8), Ui.dp(c, 7), Ui.dp(c, 8), Ui.dp(c, 7));
            row.addView(Ui.text(c, r[0], 14, c.getColor(R.color.text_soft), true), new LinearLayout.LayoutParams(Ui.dp(c, 30), -2));
            LinearLayout who = Ui.column(c);
            who.addView(Ui.text(c, r[1], 15, c.getColor(R.color.text), true));
            if (!f1Teams && !r[2].isEmpty()) who.addView(Ui.text(c, r[2], 12, c.getColor(R.color.text_dim), false));
            if (f1Teams) who.addView(Ui.text(c, r[2] + " wins", 12, c.getColor(R.color.text_dim), false));
            row.addView(who, Ui.weight(1));
            row.addView(Ui.text(c, r[3] + " pts", 15, c.getColor(R.color.text), true));
            page.addView(row);
        }
    }

    // ---- This weekend -------------------------------------------------------------------------------------------

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
            List<View> cards = new ArrayList<>();
            for (Object[] x : e.getValue()) {
                Game g = (Game) x[0];
                String channel = (String) x[1];
                LinearLayout card = GameRow.build(c, g, g.involves(teams));
                LinearLayout foot = Ui.row(c);
                foot.setPadding(0, Ui.dp(c, 6), 0, 0);
                TextView where = channel == null ? Ui.text(c, g.leagueName, 13, c.getColor(R.color.text_dim), false) : Ui.liveOn(c, channel);
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
}
