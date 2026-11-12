package com.gathin.tvhomephone.live;

import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;

import java.util.ArrayList;
import java.util.List;

/**
 * The ad-break screen's arithmetic (AdBreakView draws it): its session (when it may come up on the channel playing:
 * the listings' programme naming a game, else a game found another way, else a match's scoreboard seen), the phase
 * the line over the cards names, each club's form in this competition, the table around the two clubs and where they
 * would be if it finished now, and how the cards take turns. Pure: the tests feed it.
 */
final class AdBreak {
    private AdBreak() {}

    /** A game found without listings: its session starts this long before kick-off. */
    static final long BEFORE_MS = 60 * 60_000L;
    /** Each widget's turn on its card, the cards' start one after another, and the wipe between widgets. */
    static final long TURN_MS = 12_000L, CLIP_MAX_MS = 30_000L, WIPE_MS = 900L;

    // ---- when: the session -----------------------------------------------------------------------------------------

    /** The listings' next programme counts from this long before it starts (the end of the one before it). */
    static final long NEXT_WITHIN_MS = 15 * 60_000L;
    /** After the programme's listed end, this long more (programmes overrun). */
    static final long AFTER_PROGRAMME_MS = 5 * 60_000L;
    /** A game found without listings: from the hour before kick-off to half an hour after full time. */
    static final long FULL_TIME_AFTER_MS = 115 * 60_000L, AFTER_FULL_TIME_MS = 30 * 60_000L;
    /** No game found, but the broadcaster's scoreboard seen on the channel: a match is on for this long after. */
    static final long BOARD_HOLDS_MS = 20 * 60_000L;

    /** Where a session came from: the listings, a game found another way, or no game (the channel's scoreboard seen). */
    static final int LISTINGS = 1, FOUND = 2, GENERAL = 3;

    /**
     * What the channel playing is showing, for the ad-break screen: the game (null for a general session), the
     * programme's title where the listings give one, and the time the screen may come in at each break.
     */
    static final class Session {
        final Game game;
        final String title;
        final long from, to;
        final int source;

        Session(Game game, String title, long from, long to, int source) {
            this.game = game;
            this.title = title == null ? "" : title;
            this.from = from;
            this.to = to;
            this.source = source;
        }

        boolean in(long now) {
            return now >= from && now <= to;
        }

        /** The same session as another (the same game, or both general, the same time): no new screen for it. */
        boolean same(Session o) {
            if (o == null) return false;
            if (game == null || o.game == null) return game == o.game && source == o.source;
            return game.id != null && game.id.equals(o.game.id);
        }
    }

    /**
     * Pure: from the listings (the channel's own guide, as TiviMate shows it): the programme on now, or the next one
     * starting within NEXT_WITHIN_MS, that names one of today's games (both clubs, or one in a title that reads as a
     * fixture: OnAir.matchEither). Its time is the session's: from the programme's start (the next one's: a quarter of an
     * hour before) to its end and five minutes more, so every break in its build-up, at half time and after counts.
     */
    static Session fromListings(List<OverMatches.Programme> guide, List<Game> today, long now) {
        if (guide == null || today == null || today.isEmpty()) return null;
        for (OverMatches.Programme p : guide) {
            if (p.start <= 0 || p.stop <= p.start || p.stop + AFTER_PROGRAMME_MS < now || p.start - now > NEXT_WITHIN_MS) continue;
            Game g = com.gathin.tvhomephone.sports.OnAir.matchEither(p.title, p.desc, p.start, p.stop, today);
            if (g == null) continue;
            long from = p.start > now ? p.start - NEXT_WITHIN_MS : p.start;
            return new Session(g, p.title, from, p.stop + AFTER_PROGRAMME_MS, LISTINGS);
        }
        return null;
    }

    /**
     * Pure: a game found without listings (the channel's name, the tile, the scoreboard's game, the games' channels): from
     * the hour before kick-off to half an hour after full time (about 1h55 after kick-off; longer while ESPN still has it
     * on, extra time and all). Null without a game or a kick-off.
     */
    static Session fromGame(Game g, String title, long now) {
        if (g == null || g.kickoff <= 0) return null;
        long to = g.kickoff + FULL_TIME_AFTER_MS + AFTER_FULL_TIME_MS;
        if (g.state == Game.LIVE) to = Math.max(to, now + AFTER_FULL_TIME_MS);
        return new Session(g, title, g.kickoff - BEFORE_MS, to, FOUND);
    }

    /** Pure: no game found, but the broadcaster's scoreboard seen on this channel at seenAt: a match is on for a while. */
    static Session general(String title, long seenAt) {
        if (seenAt <= 0) return null;
        return new Session(null, title, seenAt - BOARD_HOLDS_MS, seenAt + BOARD_HOLDS_MS, GENERAL);
    }

    /** Pure: today's games whose session (fromGame) is on now: the ones the channel may be showing for the screen. */
    static List<Game> candidates(List<Game> today, long now) {
        List<Game> out = new ArrayList<>();
        if (today == null) return out;
        for (Game g : today) {
            if (g == null || g.id == null) continue;
            Session s = fromGame(g, null, now);
            if (s != null && s.in(now)) out.add(g);
        }
        return out;
    }

    /**
     * Pure: whether a game is in play (the ball moving: no adverts in football then, so the logo going is something
     * else, a replay or a graphic). Not at half time, before kick-off or after it; and not once the clock is past 40
     * minutes in the first half or 85 in the second (ESPN's half time and full time come a little late).
     */
    static boolean inPlay(Game g) {
        if (g == null || g.state != Game.LIVE || g.halftime) return false;
        int minute = (int) (g.clock / 60);
        if (g.period == 1 && minute >= 40) return false;
        if (g.period == 2 && minute >= 85) return false;
        return g.period != 3 || minute < 25;   // extra time's own half time
    }

    /**
     * Whether the ad-break screen is wanted: switched on, the channel on its adverts, inside the session's time, and the
     * game (where there is one) not in play.
     */
    static boolean wanted(boolean on, boolean adverts, Session s, long now) {
        return on && adverts && eligible(s, now);
    }

    /** Whether a break now would be the ad-break screen's: inside the session's time, the game not in play. */
    static boolean eligible(Session s, long now) {
        return s != null && s.in(now) && !inPlay(s.game);
    }

    /** Whether a game is before its kick-off, within the hour (the screen shown by hand from the options). */
    static boolean soon(Game g, long now) {
        return g != null && g.state == Game.PRE && g.kickoff > now && g.kickoff - now <= BEFORE_MS;
    }

    /** Pure: up to n games, the followed clubs' first, each lot in its own order. */
    static List<Game> mineFirst(List<Game> games, java.util.Collection<String> mine, int n) {
        List<Game> first = new ArrayList<>(), rest = new ArrayList<>();
        if (games != null) for (Game g : games) {
            if (g == null) continue;
            if (mine != null && g.involves(mine)) first.add(g);
            else rest.add(g);
        }
        first.addAll(rest);
        return new ArrayList<>(first.subList(0, Math.min(Math.max(0, n), first.size())));
    }

    // ---- the phase: what the line over the cards says ----------------------------------------------------------------

    static final int BEFORE = 0, HALF_TIME = 1, LIVE_BREAK = 2, FULL_TIME = 3, NO_GAME = 4;

    /** Pure: before kick-off, half time, a break while ESPN still has it on, full time; or no game (a general session). */
    static int phase(Game g) {
        if (g == null) return NO_GAME;
        if (g.state == Game.POST) return FULL_TIME;
        if (g.state == Game.LIVE) return g.halftime ? HALF_TIME : LIVE_BREAK;
        return BEFORE;
    }

    /** The tag on the left of the line. */
    static String tag(int phase) {
        switch (phase) {
            case BEFORE: return "BEFORE KICK-OFF";
            case HALF_TIME: return "HALF-TIME";
            case FULL_TIME: return "FULL TIME";
            default: return "AD BREAK";
        }
    }

    /** Whether the line-up card goes round: before kick-off and at half time. */
    static boolean lineupsNow(int phase) {
        return phase == BEFORE || phase == HALF_TIME;
    }

    /** How often the game on the screen is read again (its score, its clock). */
    static final long SCORE_EVERY_MS = 30_000L;

    /** Pure: the right of the line once kicked off: "HT", "FT", else the minute as ESPN gives it ("67'"). */
    static String clockLabel(Game g) {
        int phase = phase(g);
        if (phase == HALF_TIME) return "HT";
        if (phase == FULL_TIME) return "FT";
        if (phase != LIVE_BREAK) return "";
        if (g.displayClock != null && !g.displayClock.isEmpty()) return g.displayClock;
        if (g.clock > 0) return (int) (g.clock / 60) + "'";
        return g.detail == null ? "" : g.detail;
    }

    /** "12:40" to kick-off (minutes and seconds), "1:02:05" over an hour; "" once it is time. */
    static String countdown(long kickoff, long now) {
        long s = (kickoff - now) / 1000;
        if (s <= 0) return "";
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.UK, "%d:%02d:%02d", h, m, sec) : String.format(java.util.Locale.UK, "%d:%02d", m, sec);
    }

    // ---- the cards' turns -------------------------------------------------------------------------------------------

    /** When card i of n first changes: the cards one after another, a third (or half) of a turn apart. */
    static long firstTurn(int i, int n) {
        return TURN_MS + (n <= 1 ? 0 : TURN_MS * i / n);
    }

    /** The widget after (step 1) or before (step -1) widget at, going round n. */
    static int next(int at, int n, int step) {
        if (n <= 0) return 0;
        return ((at + step) % n + n) % n;
    }

    // ---- form, from the club's games in this competition ------------------------------------------------------------

    /**
     * A club's last n results in this competition, oldest first ("WWDLW"), from its schedule rows (Espn.schedule,
     * oldest first): only the games played.
     */
    static String form(List<String[]> schedule, String teamId, int n) {
        StringBuilder all = new StringBuilder();
        if (schedule == null || teamId == null) return "";
        for (String[] r : schedule) {
            if (!"post".equals(r[Espn.S_STATE])) continue;
            boolean home = teamId.equals(r[Espn.S_HOME]);
            if (!home && !teamId.equals(r[Espn.S_AWAY])) continue;
            int us = number(home ? r[Espn.S_HOME_GOALS] : r[Espn.S_AWAY_GOALS]), them = number(home ? r[Espn.S_AWAY_GOALS] : r[Espn.S_HOME_GOALS]);
            if (us < 0 || them < 0) continue;
            all.append(us > them ? 'W' : us < them ? 'L' : 'D');
        }
        return all.length() > n ? all.substring(all.length() - n) : all.toString();
    }

    /** A club's next n games in this competition: {opponent id, opponent name, "H"/"A", date}. */
    static List<String[]> fixtures(List<String[]> schedule, String teamId, long now, int n) {
        List<String[]> out = new ArrayList<>();
        if (schedule == null || teamId == null) return out;
        for (String[] r : schedule) {
            if ("post".equals(r[Espn.S_STATE]) || "in".equals(r[Espn.S_STATE])) continue;
            boolean home = teamId.equals(r[Espn.S_HOME]);
            if (!home && !teamId.equals(r[Espn.S_AWAY])) continue;
            long at = when(r[Espn.S_DATE]);
            if (at > 0 && at < now) continue;
            out.add(new String[]{home ? r[Espn.S_AWAY] : r[Espn.S_HOME], home ? r[Espn.S_AWAY_NAME] : r[Espn.S_HOME_NAME], home ? "H" : "A", r[Espn.S_DATE]});
            if (out.size() >= n) break;
        }
        return out;
    }

    // ---- the table ----------------------------------------------------------------------------------------------------

    /** Indexes into Espn.table's rows: {rank, team id, name, logo, played, goal difference, points, group, won, drawn, lost}. */
    static final int T_RANK = 0, T_ID = 1, T_NAME = 2, T_LOGO = 3, T_PLAYED = 4, T_GD = 5, T_POINTS = 6, T_GROUP = 7;

    /** The rows of the club's own group (the whole table in a league). */
    static List<String[]> group(List<String[]> table, String teamId) {
        String g = null;
        for (String[] r : table) if (r[T_ID].equals(teamId)) g = r[T_GROUP];
        List<String[]> out = new ArrayList<>();
        if (g == null) return out;
        for (String[] r : table) if (r[T_GROUP].equals(g)) out.add(r);
        return out;
    }

    /** size rows of a group around a club (the club among them, the clubs just above it first, as far as the table allows). */
    static List<String[]> around(List<String[]> group, String teamId, int size) {
        int at = -1;
        for (int i = 0; i < group.size(); i++) if (group.get(i)[T_ID].equals(teamId)) at = i;
        List<String[]> out = new ArrayList<>();
        if (at < 0) return out;
        int from = Math.max(0, Math.min(at - size / 2, group.size() - size));   // more above: who they are chasing
        for (int i = from; i < Math.min(group.size(), from + size); i++) out.add(group.get(i));
        return out;
    }

    /**
     * Where each club would be if the game finished now: {home club's place on a home win, a draw, an away win} and the
     * away club's the same, over a group's rows. Points first, then goal difference, as the tables go.
     */
    static int[][] ifFinished(List<String[]> group, String homeId, String awayId) {
        int[][] out = new int[2][3];
        int[][] change = {{3, 1, 1}, {1, 0, 0}, {0, 3, -1}};   // per result: {home points, away points, home goal difference}
        for (int r = 0; r < 3; r++) {
            int hp = change[r][0], ap = change[r][1], hgd = change[r][2];
            out[0][r] = placeAfter(group, homeId, homeId, awayId, hp, ap, hgd);
            out[1][r] = placeAfter(group, awayId, homeId, awayId, hp, ap, hgd);
        }
        return out;
    }

    private static int placeAfter(List<String[]> group, String who, String homeId, String awayId, int hp, int ap, int hgd) {
        int[] me = null;
        List<int[]> all = new ArrayList<>();
        for (String[] r : group) {
            int pts = number(r[T_POINTS]), gd = number(r[T_GD].replace("+", ""));
            if (r[T_ID].equals(homeId)) { pts += hp; gd += hgd; }
            if (r[T_ID].equals(awayId)) { pts += ap; gd -= hgd; }
            int[] row = {pts, gd, r[T_ID].equals(who) ? 1 : 0};
            all.add(row);
            if (row[2] == 1) me = row;
        }
        if (me == null) return 0;
        int place = 1;
        for (int[] o : all) if (o != me && (o[0] > me[0] || o[0] == me[0] && o[1] > me[1])) place++;
        return place;
    }

    /** "1st", "2nd", "3rd", "4th", "11th", "22nd". */
    static String ordinal(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    /** A number in ESPN's words ("12", "+9", "-3"); -1 when there is none. */
    static int number(String s) {
        if (s == null) return -1;
        String t = s.trim().replace("+", "");
        if (t.isEmpty()) return -1;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** ESPN's "2026-11-08T16:30Z" as milliseconds; 0 when it can't be read. */
    static long when(String iso) {
        if (iso == null || iso.isEmpty()) return 0;
        try {
            return java.time.OffsetDateTime.parse(iso.length() == 17 ? iso.replace("Z", ":00Z") : iso).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
