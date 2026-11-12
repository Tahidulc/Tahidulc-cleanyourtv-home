package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sports.Channels;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnAir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings' "Scoreboard over matches", the TV's setting, in the player:
 *
 * <ul>
 *   <li>Ask, as the player always was and until chosen. Nothing is asked on the phone (the TV asks with its hold-OK
 *   button): the board on the picture shows the game the programme's info tile found (the guide's title naming both
 *   clubs, read as the tile comes up), else your clubs' live games, else F1 on an F1 channel.</li>
 *   <li>Automatic: the same, and the player looks for itself, as a channel starts and every two minutes, whether the
 *   channel is showing a football match, live or within 15 minutes of kick-off (recognise). One found comes on the
 *   board by itself where the tile has none: the guide panel chosen instead of the tile, a guide that names the
 *   programme but not the clubs, the build-up before it, a channel with no guide at all.</li>
 *   <li>Off: no scoreboard on the picture (the player's options turn it back on, as before).</li>
 * </ul>
 */
public final class OverMatches {
    private OverMatches() {}

    static final String AUTO = "auto", ASK = "ask", OFF = "off";
    /** What the player does with a match recognised (decide): the board as it was, the board on for it, or no board. */
    static final int KEEP = 0, SHOW = 1, NONE = 2;
    /** A match kicking off this soon counts as on; one this far past its kick-off with ESPN still to say so does too. */
    static final long SOON_MS = 15 * 60_000L, LATE_MS = 30 * 60_000L;
    /** How often the player looks again on the same channel, and how fresh ESPN's scores must be for a look. */
    static final long LOOK_MS = 2 * 60_000L, FRESH_MS = 2 * 60_000L;

    /** Automatic, Ask or Off. */
    static String mode(Context c) {
        return modeOf(ScoreBoard.overMatches(c), ScoreBoard.on(c));
    }

    /** Pure: Off while the scoreboard on the picture is off; else as chosen, Ask until then. */
    static String modeOf(String chosen, boolean boardOn) {
        if (!boardOn) return OFF;
        return AUTO.equals(chosen) ? AUTO : ASK;
    }

    /** Chosen in Settings: Off switches the scoreboard on the picture off; the others switch it on, as chosen. */
    static void setMode(Context c, String m) {
        if (OFF.equals(m)) {
            ScoreBoard.setOn(c, false);
            return;
        }
        ScoreBoard.setOverMatches(c, AUTO.equals(m) ? AUTO : ASK);
        ScoreBoard.setOn(c, true);
    }

    /**
     * Pure: what the player does with a match recognised on the channel playing: NONE when Off (no board at all); on
     * Automatic SHOW, the board on for it, unless the board has the channel's game already (the tile's, or this one);
     * else KEEP, the board as it was.
     */
    static int decide(String mode, boolean recognised, boolean shownAlready) {
        if (OFF.equals(mode)) return NONE;
        return AUTO.equals(mode) && recognised && !shownAlready ? SHOW : KEEP;
    }

    /** Pure: whether a game counts as on: live, kicking off within SOON_MS, or a late kick-off within LATE_MS. */
    static boolean onOrSoon(Game g, long now) {
        return onOrWithin(g, now, SOON_MS);
    }

    /** Pure: as onOrSoon, kicking off within this long (the ad-break screen looks an hour ahead). */
    static boolean onOrWithin(Game g, long now, long within) {
        if (g == null) return false;
        if (g.state == Game.LIVE) return true;
        return g.state == Game.PRE && g.kickoff > 0 && g.kickoff - now <= within && now - g.kickoff <= LATE_MS;
    }

    /** A programme in the channel's guide: its title, description and times. */
    static final class Programme {
        final String title, desc;
        final long start, stop;

        Programme(String title, String desc, long start, long stop) {
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
            this.start = start;
            this.stop = stop;
        }
    }

    /** The programme on now, and the next one when it starts within SOON_MS (the match after its build-up). */
    static List<Programme> guide(Guide.Schedule s, long now) {
        return guide(s, now, SOON_MS);
    }

    /** The programme on now, and the next one when it starts within this long. */
    static List<Programme> guide(Guide.Schedule s, long now, long within) {
        List<Programme> out = new ArrayList<>();
        if (s == null) return out;
        int at = s.at(now), next = s.after(now);
        if (at >= 0) out.add(new Programme(s.title[at], s.desc[at], s.start[at], s.stop[at]));
        if (next >= 0 && s.start[next] - now <= within) out.add(new Programme(s.title[next], s.desc[next], s.start[next], s.stop[next]));
        return out;
    }

    /**
     * Pure: whether a channel is worth a look for a match (each look reads ESPN's scores): a sports channel, one of
     * Britain's football channels by name (BBC One and ITV1 show games too), or a programme that reads as a fixture.
     */
    static boolean worthLooking(String name, String group, List<Programme> guide, List<String> british) {
        if (TileKind.sportsChannel(name, group)) return true;
        String key = Names.key(name);
        if (!key.isEmpty()) for (String b : british) if (key.equals(Names.key(b))) return true;
        for (Programme p : guide) if (OnAir.looksLikeFixture(p.title) || OnAir.looksLikeFixture(p.desc)) return true;
        return false;
    }

    /**
     * Pure: the football match the channel is showing, of the games on now or soon (onOrSoon), or null. In turn: the
     * guide's programme on now or the next one naming it (both clubs, its kick-off inside the programme: OnAir.match);
     * the channel's own name naming both clubs (an event channel, "EPL 03: Arsenal v Chelsea"); the game this channel
     * is one of the own channels of (own, by game id: Sky's guide's, ESPN's), the live one if more than one is; the one
     * game this channel is one of its competition's usual broadcasters of (usual) and whose own channels are not known
     * (one Sky's guide has on Sky Sports Main Event is not the one on Sky Sports Premier League). Nothing is guessed
     * between two.
     */
    static Game recognise(String channel, List<Programme> guide, List<Game> games, Map<String, List<String>> own,
                          Map<String, List<String>> usual, long now) {
        List<Game> on = new ArrayList<>();
        for (Game g : games) if (g != null && g.id != null && onOrSoon(g, now)) on.add(g);
        return recogniseAmong(channel, guide, on, own, usual);
    }

    /**
     * Pure: as recognise, among games already chosen (the ad-break screen's: every game whose build-up, half time or
     * aftermath is on now), none left out for its time.
     */
    static Game recogniseAmong(String channel, List<Programme> guide, List<Game> on, Map<String, List<String>> own,
                               Map<String, List<String>> usual) {
        if (on == null || on.isEmpty()) return null;
        for (Programme p : guide) {
            Game g = OnAir.match(p.title, p.desc, p.start, p.stop, on);
            if (g != null) return g;
        }
        String words = AlsoShowing.words(channel);
        List<Game> named = new ArrayList<>();
        for (Game g : on) {
            String[][] clubs = {AlsoShowing.clubNames(g.homeName, g.homeFull), AlsoShowing.clubNames(g.awayName, g.awayFull)};
            if (AlsoShowing.names(words, clubs)) named.add(g);
        }
        Game g = one(named);
        if (g != null) return g;
        String key = channel == null || Names.isService(channel) ? "" : Names.key(channel);
        if (key.isEmpty()) return null;
        g = one(showing(key, on, own));
        if (g != null) return g;
        List<Game> usually = showing(key, on, usual);
        if (usually.size() != 1) return null;
        List<String> placed = own == null ? null : own.get(usually.get(0).id);
        return placed == null || placed.isEmpty() ? usually.get(0) : null;
    }

    /** Pure: the games whose channels (in lists, by game id) include the channel with this key (Names.key). */
    private static List<Game> showing(String key, List<Game> games, Map<String, List<String>> lists) {
        List<Game> out = new ArrayList<>();
        if (lists == null) return out;
        for (Game g : games) {
            List<String> names = lists.get(g.id);
            if (names == null) continue;
            boolean hit = false;
            for (String listing : names) {
                for (String part : Names.parts(listing)) hit |= !Names.isService(part) && key.equals(Names.key(part));
            }
            if (hit) out.add(g);
        }
        return out;
    }

    /** Pure: the one game of these, else the one live among them; null for none, or no telling. */
    private static Game one(List<Game> games) {
        if (games.size() == 1) return games.get(0);
        Game live = null;
        for (Game g : games) {
            if (g.state != Game.LIVE) continue;
            if (live != null) return null;
            live = g;
        }
        return live;
    }

    /** The match found (null for none), on the main thread. */
    interface Found {
        void found(Game g);
    }

    /**
     * Off the main thread: recognise the channel's match from today's games (ESPN's), its guide and its name first, and
     * only then where each game is on (Sky's guide is read for that); done on the main thread.
     */
    static void find(Channel ch, List<Programme> guide, Found done) {
        find(ch, guide, SOON_MS, done);
    }

    /** As find, for a game kicking off within this long. */
    static void find(Channel ch, List<Programme> guide, long within, Found done) {
        App.io.execute(() -> {
            Game g = null;
            try {
                long now = System.currentTimeMillis();
                List<Game> on = new ArrayList<>();
                for (Game x : OnAir.today(FRESH_MS)) if (onOrWithin(x, now, within)) on.add(x);
                Map<String, List<String>> none = Collections.emptyMap();
                g = recognise(ch.name, guide, on, none, none, now);
                if (g == null && !on.isEmpty()) {
                    Map<String, List<String>> own = new HashMap<>(), usual = new HashMap<>();
                    for (Game x : on) {
                        if (x.id == null) continue;
                        List<String> mine = Channels.own(x), all = Channels.names(x);
                        all.removeAll(mine);
                        own.put(x.id, mine);
                        usual.put(x.id, all);
                    }
                    g = recognise(ch.name, guide, on, own, usual, now);
                }
            } catch (RuntimeException ignored) {
                // looked for again at the next look
            }
            Game got = g;
            App.main.post(() -> done.found(got));
        });
    }
}
