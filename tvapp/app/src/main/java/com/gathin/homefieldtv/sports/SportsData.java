package com.gathin.homefieldtv.sports;

import com.gathin.broadcast.SportBoard;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the sports hub lists: today's football in every competition the apps follow, then the other sports they cover
 * (the NFL, the NBA, tennis, the fight cards and F1), each as an Item with its two sides, its score or start, how far on
 * and its competition, live first, then what starts soonest, then what has finished. Each football game's states are
 * kept as they come in (InStep.Line), so a page can show the game as the channel being watched has reached it. Read off
 * the main thread; ESPN's answers kept a short while (Espn, MoreSports).
 */
public final class SportsData {
    private SportsData() {}

    public static final String FOOTBALL = "football", NFL = "nfl", NBA = "nba", TENNIS = "tennis", FIGHT = "fight", F1 = "f1";

    /** One game, match, fight or session. */
    public static final class Item {
        public String kind = FOOTBALL, key = "";
        /** A football game (ESPN's), or another sport's put into a Game's shape for the channels (Matching). */
        public Game game;
        public MoreSports.Match match;
        public SportBoard board;
        public String home = "", away = "", homeLogo = "", awayLogo = "", comp = "", compLogo = "";
        /** The score ("2–1"), "" before the start; how far on ("67'", "HT", "Q3 4:12", "Set 2", "FT"); the start's time. */
        public String score = "", status = "";
        public long start;
        public boolean live, over;
    }

    /** Every game's states as they came in, by its key: what the hub and the scoreboard show, held back to the picture. */
    private static final Map<String, InStep.Line<Game>> LINES = new HashMap<>();

    public static InStep.Line<Game> line(String key) {
        synchronized (LINES) {
            InStep.Line<Game> l = LINES.get(key);
            if (l == null) {
                l = new InStep.Line<>();
                LINES.put(key, l);
            }
            return l;
        }
    }

    /** Today's football, every competition, as ESPN has it now (each game also noted on its Line). Off the main thread. */
    public static List<Game> football(long freshMs) {
        List<Game> out = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        long now = System.currentTimeMillis();
        for (String[] l : Espn.LEAGUES) {
            for (Game g : Espn.scoreboard(l[0], today, freshMs)) {
                out.add(g);
                note(g, now);
            }
        }
        return out;
    }

    /**
     * Football on another day (-1 yesterday, 1 tomorrow) in the competitions followed (Espn.LEAGUES), by kick-off. Off the
     * main thread.
     */
    public static List<Item> footballOn(int days, long freshMs) {
        List<Item> out = new ArrayList<>();
        LocalDate day = LocalDate.now(ZoneId.systemDefault()).plusDays(days);
        for (String[] l : Espn.LEAGUES) for (Game g : Espn.scoreboard(l[0], day, freshMs)) out.add(item(g));
        out.sort((a, b) -> Long.compare(a.start, b.start));
        return out;
    }

    /** Pure: the games live now, in order. */
    public static List<Item> liveOf(List<Item> all) {
        List<Item> out = new ArrayList<>();
        for (Item it : all) if (it.live) out.add(it);
        return out;
    }

    /** A football game's state as read at, kept on its Line (the hub and the scoreboard hold it back the same way). */
    public static void note(Game g, long at) {
        if (g == null || g.id == null) return;
        line(FOOTBALL + ":" + g.id).add(at, g, g.homeScore + "-" + g.awayScore + "|" + g.state + "|" + g.goals.size() + "|" + g.reds.size());
    }

    /** Everything for the hub now, in order (see order()). Off the main thread. */
    public static List<Item> load(long freshMs) {
        List<Item> out = new ArrayList<>();
        for (Game g : football(freshMs)) out.add(item(g));
        try {
            for (MoreSports.Match m : MoreSports.nfl()) out.add(item(NFL, "NFL", m));
            for (MoreSports.Match m : MoreSports.nba()) out.add(item(NBA, "NBA", m));
        } catch (RuntimeException ignored) {
            // the other sports when they answer
        }
        try {
            for (String tour : new String[]{"atp", "wta"}) {
                for (SportBoard b : MoreSports.tennis(tour)) out.add(item(TENNIS, tour.toUpperCase(Locale.UK), b));
            }
            for (SportBoard b : MoreSports.ufc()) out.add(item(FIGHT, b.event.isEmpty() ? "UFC" : b.event, b));
        } catch (RuntimeException ignored) {
            // the same
        }
        return order(out, System.currentTimeMillis());
    }

    static Item item(Game g) {
        Item it = new Item();
        it.kind = FOOTBALL;
        it.key = FOOTBALL + ":" + g.id;
        it.game = g;
        it.home = g.homeName == null ? g.home : g.homeName;
        it.away = g.awayName == null ? g.away : g.awayName;
        it.homeLogo = g.homeLogo == null ? "" : g.homeLogo;
        it.awayLogo = g.awayLogo == null ? "" : g.awayLogo;
        it.comp = g.leagueName == null || g.leagueName.isEmpty() ? Espn.name(g.slug) : g.leagueName;
        it.compLogo = g.leagueLogo == null ? "" : g.leagueLogo;
        it.start = g.kickoff;
        it.live = g.state == Game.LIVE;
        it.over = g.state == Game.POST;
        it.score = g.state == Game.PRE ? "" : g.homeScore + "–" + g.awayScore;
        it.status = g.state == Game.PRE ? "" : g.halftime ? "HT" : g.state == Game.POST ? "FT" : g.detail;
        return it;
    }

    private static Item item(String kind, String comp, MoreSports.Match m) {
        Item it = new Item();
        it.kind = kind;
        it.key = kind + ":" + m.id;
        it.match = m;
        it.home = m.homeShort.isEmpty() ? m.home : m.homeShort;
        it.away = m.awayShort.isEmpty() ? m.away : m.awayShort;
        it.homeLogo = m.homeLogo;
        it.awayLogo = m.awayLogo;
        it.comp = comp;
        it.compLogo = m.leagueLogo;
        it.start = m.start;
        it.live = m.live();
        it.over = m.over();
        it.score = "pre".equals(m.state) ? "" : m.homeScore + "–" + m.awayScore;
        it.status = m.detail;
        it.game = shape(it, m.home, m.away);
        return it;
    }

    private static Item item(String kind, String comp, SportBoard b) {
        Item it = new Item();
        it.kind = kind;
        it.key = kind + ":" + b.id;
        it.board = b;
        it.home = b.a.shortName.isEmpty() ? b.a.name : b.a.shortName;
        it.away = b.b.shortName.isEmpty() ? b.b.name : b.b.shortName;
        it.homeLogo = b.a.photoUrl.isEmpty() ? b.a.flagUrl : b.a.photoUrl;
        it.awayLogo = b.b.photoUrl.isEmpty() ? b.b.flagUrl : b.b.photoUrl;
        it.comp = comp;
        it.start = b.start;
        it.live = "in".equals(b.state);
        it.over = "post".equals(b.state);
        it.status = it.over ? (b.method.isEmpty() ? "Final" : b.method) : it.live ? (b.sport == SportBoard.TENNIS ? "Set " + Math.max(1, b.set) : "Round " + Math.max(1, b.round)) : "";
        it.game = shape(it, b.a.name, b.b.name);
        return it;
    }

    /** Another sport's game in a football game's shape, so the playlist's channels for it are found the same way. */
    private static Game shape(Item it, String homeFull, String awayFull) {
        Game g = new Game();
        g.id = it.key;
        g.slug = it.kind;
        g.homeName = it.home;
        g.awayName = it.away;
        g.homeFull = homeFull == null ? "" : homeFull;
        g.awayFull = awayFull == null ? "" : awayFull;
        g.kickoff = it.start;
        g.state = it.live ? Game.LIVE : it.over ? Game.POST : Game.PRE;
        return g;
    }

    /**
     * Pure: the hub's order: what is live first (football before the rest, then by its start), then what starts soonest,
     * then what has finished (the latest first); anything that finished more than six hours ago left out.
     */
    static List<Item> order(List<Item> items, long now) {
        List<Item> live = new ArrayList<>(), soon = new ArrayList<>(), done = new ArrayList<>();
        for (Item it : items) {
            if (it.live) live.add(it);
            else if (it.over) {
                if (it.start <= 0 || now - it.start < 6 * 3_600_000L) done.add(it);
            } else soon.add(it);
        }
        live.sort((a, b) -> {
            int fa = FOOTBALL.equals(a.kind) ? 0 : 1, fb = FOOTBALL.equals(b.kind) ? 0 : 1;
            return fa != fb ? Integer.compare(fa, fb) : Long.compare(a.start, b.start);
        });
        soon.sort((a, b) -> Long.compare(a.start, b.start));
        done.sort((a, b) -> Long.compare(b.start, a.start));
        List<Item> out = new ArrayList<>(live);
        out.addAll(soon);
        out.addAll(done);
        return Collections.unmodifiableList(out);
    }
}
