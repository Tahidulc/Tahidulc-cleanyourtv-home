package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.gathin.broadcast.SportBoard;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.live.LiveLinks;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Sports, in Home's glass (SportsScreen lays it out, the family's mock-up A): the featured game (live, your club's first,
 * else your club's next) with Watch on its channel; your clubs' next games; today's football under each competition's
 * logo; live sport on British TV (Sky's guide: tennis, cricket, darts, the NFL…) by channel logo; the tables; then
 * Formula 1, tennis, UFC, the NFL, the NBA and golf from ESPN, each with the channel the guide shows it on; and the
 * football coming up. Every part is read at the same time, off the main thread, shown as it comes, and kept to show at
 * once when the tab is opened again; read again every minute while it shows, and on a pull.
 */
public class SportsFragment extends Fragment implements SportsScreen.Host {
    private SportsScreen screen;
    private boolean visible;
    private int gen, pending;
    private String table = "eng.1";
    /** What each part showed last (by its key), shown again at once when the page is made again. */
    private static final Map<String, Object> KEPT = new HashMap<>();
    /** The featured game from today's football, and your club's next (the featured one when today has none). */
    private Object[] featuredToday, featuredClub, featuredLast;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!visible || screen == null) return;
            screen.stamp();
            App.main.postDelayed(this, 15_000L);
        }
    };
    private final Runnable reread = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            load(false);
            App.main.postDelayed(this, 60_000L);
        }
    };

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = requireContext();
        screen = new SportsScreen(c, this);
        screen.swipe.setOnRefreshListener(() -> load(true));
        for (Map.Entry<String, Object> e : new ArrayList<>(KEPT.entrySet())) apply(e.getKey(), e.getValue());
        return screen.view();
    }

    @Override public void onResume() {
        super.onResume();
        visible = true;
        App.main.post(tick);
        App.main.post(reread);
    }

    @Override public void onPause() {
        visible = false;
        App.main.removeCallbacks(tick);
        App.main.removeCallbacks(reread);
        super.onPause();
    }

    // ---- reading ----------------------------------------------------------------------------------------------------

    /** Every part read again at the same time (the spinner up while a pull's reads are out). */
    private void load(boolean pulled) {
        if (!isAdded()) return;
        Context app = requireContext().getApplicationContext();
        int my = ++gen;
        pending = 0;
        if (pulled) screen.swipe.setRefreshing(true);
        read(my, "weather", Weather::now);
        read(my, "football", () -> football(app));
        read(my, "clubs", () -> clubs(app));
        read(my, "ontv", () -> onTv(guide(0, 1)));
        read(my, "table", () -> new Object[]{table, Espn.table(table), Clubs.starred(app)});
        read(my, "f1", () -> f1());
        read(my, "tennis", () -> tennis());
        read(my, "ufc", () -> ufc());
        read(my, "nfl", () -> teamSport(MoreSports.nfl(), OnTv.NFL));
        read(my, "nba", () -> teamSport(MoreSports.nba(), OnTv.NBA));
        read(my, "golf", () -> golf());
        read(my, "coming", () -> new Object[]{britishOnly(app, weekend(app)), Clubs.starred(app)});
    }

    private void read(int my, String key, Callable<Object> job) {
        pending++;
        App.io.execute(() -> {
            Object r;
            try {
                r = job.call();
            } catch (Exception e) {
                r = null;
            }
            Object got = r;
            App.main.post(() -> {
                if (my != gen || !isAdded() || screen == null) return;
                if (--pending <= 0) screen.swipe.setRefreshing(false);
                if (got == null) return;
                KEPT.put(key, got);
                apply(key, got);
            });
        });
    }

    /** A part's reading on the page. */
    @SuppressWarnings("unchecked")
    private void apply(String key, Object r) {
        if (screen == null || r == null) return;
        Object[] x = r instanceof Object[] ? (Object[]) r : null;
        switch (key) {
            case "weather":
                if (x != null && x.length > 0 && x[0] != null) screen.weather(x[0] + "\u00B0 \u00B7 Manchester");
                break;
            case "football":
                screen.matchday((Boolean) x[3]);
                screen.today((Map<String, List<Game>>) x[0], (Map<String, String>) x[1], (Set<String>) x[2]);
                featuredToday = (Object[]) x[4];
                featuredLast = x.length > 5 ? (Object[]) x[5] : null;
                featured();
                break;
            case "clubs":
                screen.clubs((List<Object[]>) x[0]);
                featuredClub = (Object[]) x[1];
                featured();
                break;
            case "ontv":
                screen.onTv((List<OnTv.Listing>) r);
                break;
            case "table":
                screen.table((String) x[0], (List<String[]>) x[1], (Set<String>) x[2]);
                break;
            case "f1":
                screen.f1((F1.Weekend) x[0], (List<F1.Standing>) x[1], (String) x[2]);
                break;
            case "tennis":
                screen.tennis((List<Object[]>) r);
                break;
            case "ufc":
                screen.fights((List<SportBoard>) x[0], (String) x[1]);
                break;
            case "nfl":
                screen.nfl((List<MoreSports.Match>) r);
                break;
            case "nba":
                screen.nba((List<MoreSports.Match>) r);
                break;
            case "golf":
                screen.golf((List<MoreSports.Leaderboard>) r);
                break;
            case "coming":
                screen.comingUp((Map<LocalDate, List<Object[]>>) x[0], (Set<String>) x[1]);
                break;
            default:
                break;
        }
    }

    /** The game at the top: today's (live first, your clubs' first), else your club's next. */
    private void featured() {
        Object[] f = featuredToday != null ? featuredToday : featuredClub != null ? featuredClub : featuredLast;
        if (f == null) screen.featured(null, null, null);
        else screen.featured((Game) f[0], (String) f[1], (String) f[2]);
    }

    /**
     * Off the main thread: {today's games by competition (your clubs' first), each game's channel by its id, your
     * clubs, whether there is football today, the featured game {game, channel, label} or null}.
     */
    static Object[] football(Context c) {
        Map<String, List<Game>> days = today(c);
        Set<String> mine = Clubs.starred(c);
        Map<String, String> channels = new HashMap<>();
        Game live = null, livePl = null, liveMine = null, nextMine = null, next = null;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, List<Game>> e : days.entrySet()) {
            for (Game g : e.getValue()) {
                if (g.state != Game.POST && !channels.containsKey(g.id)) {
                    String ch = british(c, g);
                    if (ch != null) channels.put(g.id, ch);
                }
                boolean yours = g.involves(mine);
                if (g.state == Game.LIVE) {
                    if (yours && liveMine == null) liveMine = g;
                    if (livePl == null && "eng.1".equals(g.slug)) livePl = g;
                    if (live == null && big(g)) live = g;
                } else if (g.state == Game.PRE && g.kickoff > now - 60_000L) {
                    if (yours && (nextMine == null || g.kickoff < nextMine.kickoff)) nextMine = g;
                    if ("eng.1".equals(g.slug) && (next == null || g.kickoff < next.kickoff)) next = g;
                }
            }
        }
        // Your club live, then a Premier League game live, then your club later today, then a big game live (a British
        // side's, a European night's); else your club's next (from the clubs' part), and only then the next PL game.
        Game pick = liveMine != null ? liveMine : livePl != null ? livePl : nextMine != null ? nextMine : live;
        Object[] featured = pick == null ? null : new Object[]{pick, channels.get(pick.id), pick.state == Game.PRE ? "Today" : null};
        Object[] fallback = next == null ? null : new Object[]{next, channels.get(next.id), "Today"};
        return new Object[]{days, channels, mine, !days.isEmpty(), featured, fallback};
    }

    /** The British channel a game is on (one the family's TV knows, or the playlist has), never ESPN's American ones. */
    static String british(Context c, Game g) {
        String ch = Channels.best(c, g);
        return ch != null && (ChannelLogos.sky(ch) != null || LiveLinks.has(c, ch)) ? ch : null;
    }

    private static final Set<String> BIG = new java.util.HashSet<>(java.util.Arrays.asList("uefa.champions", "uefa.europa",
            "uefa.europa.conf", "eng.fa", "eng.league_cup", "fifa.world", "uefa.euro"));
    private static final java.util.regex.Pattern HOME_NATIONS = java.util.regex.Pattern.compile(
            "(?i)^(england|scotland|wales|northern ireland|republic of ireland|ireland)$");

    /** Pure-ish: a game worth the top of the page when nobody's club plays: a European night, a cup, a home nation's. */
    static boolean big(Game g) {
        if (g.slug != null && BIG.contains(g.slug)) return true;
        for (String n : new String[]{g.homeFull, g.awayFull, g.homeName, g.awayName}) if (n != null && HOME_NATIONS.matcher(n.trim()).matches()) return true;
        return false;
    }

    /** Off the main thread: {each starred club {club, its next game, its channel}, your club's next as featured}. */
    static Object[] clubs(Context c) {
        List<Object[]> cards = new ArrayList<>();
        Object[] featured = null;
        for (String[] club : Clubs.describe(c)) {
            List<Game> next = MoreSports.nextGames(club[0], 1);
            Game g = next.isEmpty() ? null : next.get(0);
            String channel = g == null ? null : british(c, g);
            cards.add(new Object[]{club, g, channel});
            if (featured == null && g != null) featured = new Object[]{g, channel, "Your next game"};
        }
        return new Object[]{cards, featured};
    }

    /** The coming days' games with ESPN's American channels taken off (the British ones and the playlist's kept). */
    static Map<LocalDate, List<Object[]>> britishOnly(Context c, Map<LocalDate, List<Object[]>> days) {
        for (List<Object[]> games : days.values()) {
            for (Object[] x : games) {
                String ch = (String) x[1];
                if (ch != null && ChannelLogos.sky(ch) == null && !LiveLinks.has(c, ch)) x[1] = null;
            }
        }
        return days;
    }

    /** Off the main thread: the live sport in Sky's guide from today to that many days on. */
    static List<OnTv.Listing> guide(int from, int to) {
        LocalDate today = LocalDate.now();
        List<LocalDate> days = new ArrayList<>();
        for (int d = from; d <= to; d++) days.add(today.plusDays(d));
        return OnTv.days(days.toArray(new LocalDate[0]));
    }

    /** Pure-ish: the guide's live sport still to come or on (football has its own parts), the next thirty hours. */
    static List<OnTv.Listing> onTv(List<OnTv.Listing> all) {
        List<OnTv.Listing> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (OnTv.Listing l : all) {
            if (OnTv.FOOTBALL.equals(l.sport) || l.end <= now || l.start > now + 30 * 3_600_000L) continue;
            out.add(l);
        }
        return out;
    }

    /** Off the main thread: {the next Grand Prix, the drivers' standings, its channel (the guide's, else Sky Sports F1)}. */
    static Object[] f1() {
        F1.Weekend w = F1.next();
        List<F1.Standing> drivers = F1.drivers();
        String channel = "Sky Sports F1";
        if (w != null && w.race > 0) {
            OnTv.Listing l = OnTv.find(guide(0, 1), OnTv.F1, 0, 0, "F1");
            if (l != null && l.channel() != null) channel = l.channel();
        }
        return new Object[]{w, drivers, channel};
    }

    /** Off the main thread: each tour {name, its matches, the channel the guide shows it on}. */
    static List<Object[]> tennis() {
        List<OnTv.Listing> tv = guide(0, 1);
        List<Object[]> out = new ArrayList<>();
        for (String tour : new String[]{"ATP", "WTA"}) {
            List<SportBoard> m = MoreSports.tennis(tour);
            if (m.isEmpty()) continue;
            out.add(new Object[]{tour, m, tennisChannel(tv, m)});
        }
        return out;
    }

    /** Pure: the channel the guide shows a tour's matches on: a listing naming a player of a match on now or soon. */
    static String tennisChannel(List<OnTv.Listing> tv, List<SportBoard> matches) {
        for (SportBoard s : matches) {
            if (s.over()) continue;
            for (SportBoard.Side p : new SportBoard.Side[]{s.a, s.b}) {
                String surname = p.shortName == null || p.shortName.isEmpty() ? p.name : p.shortName;
                if (surname == null || surname.length() < 3) continue;
                OnTv.Listing l = OnTv.find(tv, OnTv.TENNIS, s.start, 4 * 3_600_000L, surname);
                if (l != null) return l.channel();
            }
        }
        return null;
    }

    /** Off the main thread: {UFC's next card, the channel the guide shows it on that day}. */
    static Object[] ufc() {
        List<SportBoard> card = MoreSports.ufc();
        String channel = null;
        if (!card.isEmpty() && card.get(0).start > 0) {
            LocalDate day = Instant.ofEpochMilli(card.get(0).start).atZone(ZoneId.systemDefault()).toLocalDate();
            OnTv.Listing l = OnTv.find(OnTv.days(day, day.plusDays(1)), OnTv.UFC, 0, 0, "UFC");
            if (l != null) channel = l.channel();
        }
        return new Object[]{card, channel};
    }

    /** Off the main thread: a team sport's games, each to come in the next day and a half with its guide channel. */
    static List<MoreSports.Match> teamSport(List<MoreSports.Match> games, String sport) {
        List<OnTv.Listing> tv = guide(0, 1);
        for (MoreSports.Match m : games) {
            if (m.over()) continue;
            OnTv.Listing l = OnTv.find(tv, sport, m.start, 3 * 3_600_000L, m.awayShort, m.homeShort);
            if (l != null) m.channel = l.channel();
        }
        return games;
    }

    /** Off the main thread: the PGA Tour's and the DP World Tour's leaderboards, each with its guide channel. */
    static List<MoreSports.Leaderboard> golf() {
        List<OnTv.Listing> tv = guide(0, 1);
        List<MoreSports.Leaderboard> out = new ArrayList<>();
        MoreSports.Leaderboard pga = MoreSports.pga(5), dpw = MoreSports.dpWorld(5);
        if (dpw != null) {
            OnTv.Listing l = OnTv.find(tv, OnTv.GOLF, 0, 0, "DP World");
            if (l != null) dpw.channel = l.channel();
            out.add(dpw);
        }
        if (pga != null) {
            for (OnTv.Listing l : tv) {
                String t = l.title.toLowerCase(java.util.Locale.ROOT);
                if (OnTv.GOLF.equals(l.sport) && t.contains("pga tour") && !t.contains("champions") && !t.contains("lpga")) {
                    pga.channel = l.channel();
                    break;
                }
            }
            out.add(pga);
        }
        return out;
    }

    // ---- the page's taps --------------------------------------------------------------------------------------------

    @Override public void game(Game g) {
        if (g == null || !isAdded()) return;
        startActivity(GameActivity.intent(requireContext(), g));
    }

    @Override public void watch(String channel) {
        if (channel == null || !isAdded()) return;
        Context c = requireContext();
        if (!LiveLinks.open(c, channel)) Toast.makeText(c, channel + " isn't in your playlist", Toast.LENGTH_SHORT).show();
    }

    @Override public void table(String slug) {
        table = slug;
        if (!isAdded()) return;
        Context app = requireContext().getApplicationContext();
        read(gen, "table", () -> new Object[]{slug, Espn.table(slug), Clubs.starred(app)});
    }

    @Override public void settings() {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(R.id.tab_settings);
    }

    @Override public void event(SportEvent e) {
        if (e == null || !isAdded()) return;
        startActivity(EventActivity.intent(requireContext(), e));
    }

    @Override public void team(String slug, String[] row) {
        if (row == null || row.length < 4 || row[1] == null || row[1].isEmpty() || !isAdded()) return;
        startActivity(TeamActivity.intent(requireContext(), slug, row[1], row[2], row[3]));
    }

    // ---- shared with the rest of the app ----------------------------------------------------------------------------

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


    /**
     * One line of a table: {rank, crest, club, P, W, D, L, GD, Pts}. Narrow number columns and the club's name cut short
     * to fit a phone upright; a little more room on a tablet.
     */
    static LinearLayout tableRow(Context c, String[] cells, boolean mine, boolean head) {
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

}
