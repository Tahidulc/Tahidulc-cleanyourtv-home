package com.gathin.tvhomephone.sports;

import android.content.Context;

import com.gathin.tvhomephone.App;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Test notifications, from the player's options: every kind of alert, one after another (or one kind), on the game
 * being watched (or one made up from the starred clubs), each through the real way an alert goes: its notification,
 * its pop-up over the apps, its drop from the scoreboard. They are not kept in the alerts' log and never stop a real
 * one coming (their own keys).
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
    static Game sample(Context c, Game watching) {
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
    static Events.Alert alert(String kind, Game g) {
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

    /** "Manchester United" to "MAN". */
    static String code(String name) {
        String n = name == null ? "" : name.replaceAll("[^\\p{L}]", "");
        return (n.length() > 3 ? n.substring(0, 3) : n).toUpperCase(Locale.ROOT);
    }
}
