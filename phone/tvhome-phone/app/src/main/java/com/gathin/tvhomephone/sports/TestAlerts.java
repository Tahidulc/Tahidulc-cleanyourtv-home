package com.gathin.tvhomephone.sports;

import android.content.Context;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Test notifications, from the player's options and Settings: every kind of alert, one after another (or one kind), on
 * the game being watched (or one made up from the starred clubs), each through the real way an alert goes: its
 * notification, its pop-up over the apps, its drop from the scoreboard; the line-ups' alert on a game with its line-ups
 * out; and the other notifications (the kick-off heads-up, Following live, the playlists' warnings, a pop-up over
 * apps). They are not kept in the alerts' log, change no setting and never stop or replace a real one (their own keys
 * and ids).
 */
public final class TestAlerts {
    private TestAlerts() {}

    private static final long GAP_MS = 4_500L;

    /** {kind, name} in the order they play: every kind the alerts have. */
    public static List<String[]> kinds() {
        List<String[]> out = new ArrayList<>();
        for (String[] k : Kinds.ALL) out.add(new String[]{k[0], k[1]});
        return out;
    }

    /** Plays them: kind null for all, one after another; else that one. */
    public static void play(Context c, Game watching, String kind) {
        Context app = c.getApplicationContext();
        Game g = sample(app, watching);
        long at = 0;
        for (String[] k : kinds()) {
            if (kind != null && !kind.equals(k[0])) continue;
            Events.Alert a = alert(k[0], g);
            if (a == null) continue;
            App.main.postDelayed(() -> App.io.execute(() -> Notifier.test(app, a)), at);
            at += GAP_MS;
        }
    }

    /** The game to show them on: a copy of the one watched, else one made up from the starred clubs. */
    public static Game sample(Context c, Game watching) {
        Game g;
        if (watching != null) {
            g = Game.from(watching.json());
        } else {
            g = new Game();
            g.slug = "eng.1";
            g.league = "PL";
            g.leagueName = "Premier League";
            List<String[]> clubs;
            try {
                clubs = Clubs.describe(c);
            } catch (RuntimeException e) {
                clubs = new ArrayList<>();
            }
            String[] h = clubs.size() > 0 ? clubs.get(0) : new String[]{"", "Homefield", null};
            String[] a = clubs.size() > 1 ? clubs.get(1) : new String[]{"", "Visitors", null};
            g.homeId = h[0];
            g.homeName = h[1];
            g.homeLogo = h[2];
            g.awayId = a[0];
            g.awayName = a[1];
            g.awayLogo = a[2];
            g.home = code(h[1]);
            g.away = code(a[1]);
            g.homeColor = "e0213a";
            g.awayColor = "3d8bff";
            g.homeScore = 1;
            g.awayScore = 1;
            g.goals.add(new String[]{"TEST|g1", "A. Player", "23'", "home", ""});
            g.goals.add(new String[]{"TEST|g2", "B. Player", "51'", "away", ""});
        }
        g.id = "test-" + (g.id == null ? "" : g.id);
        g.state = Game.LIVE;
        g.halftime = false;
        g.period = 2;
        g.clock = 67 * 60;
        g.detail = "67'";
        g.displayClock = "67'";
        g.kickoff = System.currentTimeMillis() - 67 * 60_000L - 15 * 60_000L;
        // The test's own goal and red card (their own keys: never a real one's notification replaced).
        g.goals.add(new String[]{"TEST|goal", "C. Player", "67'", "home", ""});
        g.homeScore++;
        g.reds.add(new String[]{"TEST|red", "D. Player", "70'", "away"});
        return g;
    }

    /** The test alert of one kind on game g; null for none. */
    public static Events.Alert alert(String kind, Game g) {
        String line = g.homeName + " " + g.score() + " " + g.awayName;
        switch (kind) {
            case "goal":
                return new Events.Alert("goal", "TEST|goal", "GOAL · " + line, "C. Player 67' for " + g.homeName, g);
            case "red":
                return new Events.Alert("red", "TEST|red", "RED CARD · " + g.awayName, "D. Player 70' for " + g.awayName, g);
            case "kickoff": {
                Game k = Game.from(g.json());
                k.state = Game.LIVE;
                k.period = 1;
                k.clock = 0;
                k.homeScore = 0;
                k.awayScore = 0;
                k.goals.clear();
                k.reds.clear();
                k.detail = "1'";
                return new Events.Alert("kickoff", "TEST|K", "KICK-OFF · " + g.homeName + " v " + g.awayName, "Under way", k);
            }
            case "ht": {
                Game h = Game.from(g.json());
                h.halftime = true;
                h.period = 1;
                h.detail = "HT";
                return new Events.Alert("ht", "TEST|H", "HALF TIME · " + line, "", h);
            }
            case "ft": {
                Game f = Game.from(g.json());
                f.state = Game.POST;
                f.detail = "FT";
                return new Events.Alert("ft", "TEST|F", "FULL TIME · " + line, "", f);
            }
            case "var":
                return new Events.Alert("var", "TEST|V", "VAR · Penalty check", "A possible penalty for " + g.homeName + " is checked", g);
            case "drama":
                return new Events.Alert("drama", "D|TEST|goal", "LATE DRAMA · 67' winner", "C. Player for " + g.homeName + " · " + line, g);
            case "table":
                return new Events.Alert("table", "TEST|T", "LIVE TABLE · " + g.homeName + " up to 4th", "As it stands, if the score stays", g);
            case "milestone":
                return new Events.Alert("milestone", "TEST|M", "MILESTONE · C. Player", "Two goals today, " + line, g);
            case "f1live":
                return new Events.Alert("f1live", "TEST|F1L", "LIGHTS OUT · Test Grand Prix", "The race is under way", null);
            case "f1grid":
                return new Events.Alert("f1grid", "TEST|F1G", "F1 GRID · Test Grand Prix", "1 Norris · 2 Verstappen · 3 Leclerc", null);
            case "f1result":
                return new Events.Alert("f1result", "TEST|F1R", "F1 RESULT · Test Grand Prix", "1 Norris · 2 Verstappen · 3 Leclerc", null);
            case "weather":
                return new Events.Alert("weather", "TEST|W", "Rain on the way", "Showers from 17:00; dry again by 20:00", null);
            case "news": {
                Events.Alert n = new Events.Alert("news", "TEST|N", "A test of breaking news", "This is how a breaking story comes", null);
                n.channel = "BBC News";
                n.slug = "https://www.bbc.co.uk/news";
                return n;
            }
            case "reminder":
                return new Events.Alert("reminder", "TEST|R", "STARTS SOON · " + g.homeName + " v " + g.awayName, "At 20:00", null);
            default:
                return null;
        }
    }

    // ---- the line-ups' alert, on a game with its line-ups out ------------------------------------------------------

    /** The line-ups' alert (both elevens on a pitch) sent as a real one; note (main thread) when no game has them out. */
    public static void lineups(Context c, Game watching, IntConsumer note) {
        Context app = c.getApplicationContext();
        App.io.execute(() -> {
            Events.Alert a = lineupsAlert(watching);
            if (a == null) App.main.post(() -> note.accept(R.string.live7_no_lineups));
            else Notifier.test(app, a);
        });
    }

    /**
     * A test line-ups alert (its own key, "L|test-…") on the first of the games to try with both elevens out at ESPN, five
     * looked at at most; null when none has them. Off the main thread.
     */
    public static Events.Alert lineupsAlert(Game watching) {
        return tryOn(watching, 5, g -> {
            if (g.slug == null) return null;
            Espn.Summary s = Espn.summary(g.slug, g.id, 10 * 60_000L);
            Game t = Game.from(g.json());
            t.id = "test-" + g.id;
            return Events.lineups(t, s);
        });
    }

    /**
     * What make gives for the first of the games to try (candidates) that gives anything, most of them looked at; null
     * when none does. The one watched is tried before the others are read. Off the main thread.
     */
    public static <T> T tryOn(Game watching, int most, java.util.function.Function<Game, T> make) {
        int looked = 0;
        if (watching != null && watching.id != null) {
            looked++;
            T got = make.apply(watching);
            if (got != null) return got;
        }
        for (Game g : candidates(watching)) {
            if (g == watching || g.id == null) continue;
            if (++looked > most) break;
            T got = make.apply(g);
            if (got != null) return got;
        }
        return null;
    }

    /** The most games a test looks through. */
    static final int MOST = 8;

    /**
     * The games a test can be tried on, as the TV's tries find them (ClassicIntro.candidates): the one watched first,
     * then the Premier League's games under way or played in the last two weeks, the latest first, eight or so in all.
     * Off the main thread.
     */
    public static List<Game> candidates(Game watching) {
        List<Game> out = new ArrayList<>();
        if (watching != null && watching.id != null) out.add(watching);
        LocalDate day = LocalDate.now(ZoneId.systemDefault());
        for (int back = 0; back < 14 && out.size() < MOST; back++) {
            addPlayed(out, watching, Espn.scoreboard("eng.1", day.minusDays(back), 10 * 60_000L));
        }
        return out;
    }

    /** Pure: one day's games added to out: those under way or played (not the one watched), the latest first. */
    static void addPlayed(List<Game> out, Game watching, List<Game> day) {
        List<Game> played = new ArrayList<>();
        for (Game g : day) {
            if (g.state != Game.PRE && g.id != null && (watching == null || !g.id.equals(watching.id))) played.add(g);
        }
        played.sort(Comparator.comparingLong((Game g) -> g.kickoff).reversed());
        out.addAll(played);
    }

    // ---- the other notifications ------------------------------------------------------------------------------------

    public static final String KICKOFF_SOON = "kickoff2", FOLLOWING = "following", EXPIRY = "expiry", HEALTH = "health",
            POPUP = "popup", FLOAT = "float";
    /** The notifications and pop-ups over apps besides the alerts, in the order offered. */
    public static final String[] OTHERS = {KICKOFF_SOON, FOLLOWING, EXPIRY, HEALTH, POPUP, FLOAT};

    /** One of OTHERS's name (strings_live7). */
    public static int label(String id) {
        switch (id == null ? "" : id) {
            case KICKOFF_SOON: return R.string.live7_kickoff_soon;
            case FOLLOWING: return R.string.live7_following;
            case EXPIRY: return R.string.live7_expiry;
            case HEALTH: return R.string.live7_health;
            case POPUP: return R.string.live7_popup_try;
            case FLOAT: return R.string.live7_float;
            default: return 0;
        }
    }

    /**
     * One of OTHERS now, as it really comes, on the game watched (or one made up), under its own id: the kick-off
     * heads-up, "Following live" (gone in a minute, never ongoing), a playlist running out and one struggling (a made-up
     * playlist), a goal popping up over the apps whatever the pop-ups' switches, the scoreboard over other apps with a goal
     * on it (FloatService, its switch left as it is). note (main thread) says why one can't.
     */
    public static void other(Context c, Game watching, String id, IntConsumer note) {
        Context app = c.getApplicationContext();
        switch (id) {
            case KICKOFF_SOON: {
                Game g = soon(sample(app, watching), System.currentTimeMillis());
                App.io.execute(() -> KickOff.test(app, g));
                break;
            }
            case FOLLOWING: {
                Game g = sample(app, watching);
                App.io.execute(() -> Notifier.testFollowing(app, g));
                break;
            }
            case EXPIRY:
                App.io.execute(() -> com.gathin.tvhomephone.live.Expiry.test(app));
                break;
            case HEALTH:
                App.io.execute(() -> com.gathin.tvhomephone.live.PlaylistHealth.test(app));
                break;
            case POPUP: {
                if (!Popups.allowed(app)) {
                    note.accept(R.string.live7_popup_allow);
                    return;
                }
                Events.Alert a = alert("goal", sample(app, watching));
                App.io.execute(() -> Popups.show(app, a));
                break;
            }
            case FLOAT:
                if (!com.gathin.tvhomephone.live.FloatService.test(app)) note.accept(R.string.live7_popup_allow);
                break;
            default:
                break;
        }
    }

    /** Pure: a copy of g two minutes before its kick-off at now: before the game, no score yet. */
    static Game soon(Game g, long now) {
        Game k = Game.from(g.json());
        k.state = Game.PRE;
        k.kickoff = now + KickOff.SOON_MS;
        k.homeScore = 0;
        k.awayScore = 0;
        k.goals.clear();
        k.reds.clear();
        k.halftime = false;
        k.period = 0;
        k.clock = 0;
        k.detail = "";
        k.displayClock = "";
        return k;
    }

    /** "Manchester United" to "MAN". */
    static String code(String name) {
        String n = name == null ? "" : name.replaceAll("[^\\p{L}]", "");
        return (n.length() > 3 ? n.substring(0, 3) : n).toUpperCase(Locale.ROOT);
    }
}
