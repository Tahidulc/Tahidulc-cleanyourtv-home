package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.broadcast.Sfx;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.TestAlerts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Test notifications in the player's options: everything the app can show, grouped, each now on this channel as it
 * really comes, whatever the styles chosen. The alerts (TestAlerts: each kind, all of them in turn, the line-ups'); the
 * kick-off pop-up in both styles at each of its moments, and the line-ups in both (PlayerIntro); the scoreboard's own
 * moments and what slides out of it (ScoreBoard); each goal sound; each info tile; the other notifications and
 * on-screen moments. No setting changes, nothing goes in the alerts' log or replaces a real one, and one already on the
 * screen is waited for (a note says so), never stacked. Settings offers the ones that need no channel playing.
 */
final class PlayerTests {
    private PlayerTests() {}

    static final String ALERTS = "alerts", POPUP = "popup", STRAP = "strap", LINEUPS = "lineups", BOARD = "board",
            SOUNDS = "sounds", TILES = "tiles", OTHER = "other";
    /** The groups, in the options' order. */
    static final String[] GROUPS = {ALERTS, POPUP, STRAP, LINEUPS, BOARD, SOUNDS, TILES, OTHER};
    /** The alerts' row besides the kinds: all of them in turn, and the line-ups' alert. */
    static final String ALL = "all", LINEUPS_ALERT = "lineups_alert";
    /** The kick-off pop-up's moments (both styles), and the line-ups' two styles. */
    static final String[] MOMENTS = {"kickoff", "restart", "second"};
    static final String[] LINEUP_STYLES = {"lineups", "stadium", "studio", "intro"};
    /** The other intros: F1's, UFC's, boxing's and plain football's (no competition). */
    static final String[] SPORT_INTROS = {"intro:f1", "intro:ufc", "intro:boxing", "intro:football"};
    /** The scoreboard's own: a goal each way (with the goal sound), its scorer, the stats, the line-up card, a
     * substitution, VAR checking, the added time's "+N". */
    static final String[] BOARD_TESTS = {"goal_home", "goal_away", "scorer", "stats", "card", "sub", "var", "added", "logo_away", "pens",
            "milestones", "goal_shirt"};
    /** On the full screen only: the banner for a goal in another game, an alert waiting for the stream, the break. */
    static final String BANNER = "banner", HELD = "held", BREAK = "break";
    /** How long the test alert waits for the stream. */
    static final long HELD_MS = 10_000L;

    // ---- the list (pure) -------------------------------------------------------------------------------------------

    /** Pure: a group's tests by id, in order. */
    static List<String> tests(String group) {
        List<String> out = new ArrayList<>();
        switch (group == null ? "" : group) {
            case ALERTS:
                out.add(ALL);
                for (String[] k : TestAlerts.kinds()) out.add(k[0]);
                out.add(LINEUPS_ALERT);
                break;
            case POPUP:
            case STRAP:
                out.addAll(Arrays.asList(MOMENTS));
                break;
            case LINEUPS:
                out.addAll(Arrays.asList(LINEUP_STYLES));
                out.addAll(Arrays.asList(SPORT_INTROS));
                break;
            case BOARD:
                out.addAll(Arrays.asList(BOARD_TESTS));
                break;
            case SOUNDS:
                for (String k : Sfx.GOALS) if (!"off".equals(k)) out.add(k);
                break;
            case TILES:
                for (TileKind.Style s : TileKind.Style.values()) if (s != TileKind.Style.AUTO) out.add(s.name());
                break;
            case OTHER:
                out.add(BANNER);
                out.add(HELD);
                out.add(BREAK);
                // The scoreboard over other apps keeps out of the player's way: tried from Settings.
                for (String id : TestAlerts.OTHERS) if (!TestAlerts.FLOAT.equals(id)) out.add(id);
                break;
            default:
                break;
        }
        return out;
    }

    /** Pure: whether a test shows over the picture (a channel playing), or is a notification or pop-up Settings has too. */
    static boolean needsPlayer(String group, String id) {
        if (ALERTS.equals(group)) return false;
        if (OTHER.equals(group)) return BANNER.equals(id) || HELD.equals(id) || BREAK.equals(id);
        return true;
    }

    /** Pure: the groups that need the scoreboard over the picture (the overlay): left out without one. */
    static boolean needsOverlay(String group) {
        return POPUP.equals(group) || STRAP.equals(group) || LINEUPS.equals(group) || BOARD.equals(group);
    }

    // ---- the options' rows -----------------------------------------------------------------------------------------

    /** The tests' rows in the player's options, a chip each; overlay null leaves out what needs the scoreboard. */
    static void add(Activity a, LinearLayout box, PlayerExtras.Host host, ScoreOverlay overlay, PlayerExtras extras) {
        for (String group : GROUPS) {
            if (overlay == null && needsOverlay(group)) continue;
            LinearLayout row = PlayerExtras.chipRow(a, box, groupLabel(group));
            for (String id : tests(group)) {
                if (overlay == null && BREAK.equals(id)) continue;
                TextView t = Ui.guideChip(a, label(a, group, id), ALL.equals(id));
                t.setOnClickListener(v -> run(a, host, overlay, extras, group, id));
                PlayerExtras.addChip(row, t);
            }
        }
        PlayerExtras.note(a, box, a.getString(R.string.live4_tests_note));
        PlayerExtras.note(a, box, a.getString(R.string.live7_note));
    }

    private static void run(Activity a, PlayerExtras.Host host, ScoreOverlay overlay, PlayerExtras extras, String group, String id) {
        IntConsumer note = res -> {
            if (!a.isFinishing()) Toast.makeText(a, res, Toast.LENGTH_SHORT).show();
        };
        // The goal sounds keep the options open, to hear one after another.
        if (SOUNDS.equals(group)) {
            sound(a, id);
            return;
        }
        host.closeSheet();
        Game watching = host.game();
        switch (group) {
            case ALERTS:
                if (ALL.equals(id)) {
                    TestAlerts.play(a, watching, null);
                } else if (LINEUPS_ALERT.equals(id)) {
                    note.accept(R.string.live7_looking);
                    TestAlerts.lineups(a, watching, note);
                } else {
                    TestAlerts.play(a, watching, id);
                }
                break;
            case POPUP:
            case STRAP:
                overlay.testPopup(POPUP.equals(group) ? "classic" : "strap", id, note);
                break;
            case LINEUPS:
                if ("intro".equals(id)) overlay.testIntro(note);
                else if (id.startsWith("intro:")) overlay.testSport(id.substring(6), note);
                else overlay.testLineups(id, note);
                break;
            case BOARD:
                overlay.testBoard(id, note);
                break;
            case TILES:
                host.testTile(TileKind.Style.valueOf(id));
                break;
            case OTHER:
                if (BANNER.equals(id)) {
                    extras.banner(TestAlerts.alert("goal", TestAlerts.sample(a, null)));
                } else if (HELD.equals(id)) {
                    // the line saying an alert waits for the stream, then the goal as it comes
                    LiveAlerts.testHold(TestAlerts.alert("goal", TestAlerts.sample(a, watching)), HELD_MS,
                            () -> TestAlerts.play(a, watching, "goal"));
                } else if (BREAK.equals(id)) {
                    overlay.testBreak(note);
                } else {
                    TestAlerts.other(a, watching, id, note);
                }
                break;
            default:
                break;
        }
    }

    /** A goal sound heard now (loaded first the first time: tried again a few times while it loads). */
    static void sound(Context c, String key) {
        String name = "goal-" + key;
        Sfx.warm(c, name);
        Handler h = new Handler(Looper.getMainLooper());
        int[] tries = {0};
        Runnable[] again = new Runnable[1];
        again[0] = () -> {
            if (!Sfx.play(c, name, 0f, 0.9f) && ++tries[0] < 8) h.postDelayed(again[0], 150);
        };
        h.post(again[0]);
    }

    /** A sport's intro, as its chip reads. */
    static int sportLabel(String key) {
        switch (key) {
            case "f1": return R.string.live48_intro_f1;
            case "ufc": return R.string.live48_intro_ufc;
            case "boxing": return R.string.live48_intro_boxing;
            default: return R.string.live48_intro_football;
        }
    }

    static int groupLabel(String group) {
        switch (group) {
            case ALERTS: return R.string.live7_alerts;
            case POPUP: return R.string.live7_popup;
            case STRAP: return R.string.live7_strap;
            case LINEUPS: return R.string.live7_lineups;
            case BOARD: return R.string.live7_board;
            case SOUNDS: return R.string.live7_sounds;
            case TILES: return R.string.live7_tiles;
            default: return R.string.live7_other;
        }
    }

    static String label(Context c, String group, String id) {
        switch (group) {
            case ALERTS:
                if (ALL.equals(id)) return c.getString(R.string.live4_tests_all);
                if (LINEUPS_ALERT.equals(id)) return c.getString(R.string.live7_lineups_alert);
                for (String[] k : TestAlerts.kinds()) if (k[0].equals(id)) return k[1];
                return id;
            case POPUP:
            case STRAP:
                return c.getString("kickoff".equals(id) ? R.string.live7_kickoff : "restart".equals(id) ? R.string.live7_restart
                        : R.string.live7_second);
            case LINEUPS:
                if (id.startsWith("intro:")) return c.getString(sportLabel(id.substring(6)));
                return c.getString("stadium".equals(id) ? R.string.live35_style_stadium : "studio".equals(id) ? R.string.live_style_studio
                        : "intro".equals(id) ? R.string.live45_intro_test : R.string.live35_style_beam);
            case BOARD:
                return c.getString(boardLabel(id));
            case SOUNDS:
                return Sfx.goalName(id);
            case TILES:
                return c.getString(PlayerActivity.styleLabel(TileKind.Style.valueOf(id)));
            default:
                if (BANNER.equals(id)) return c.getString(R.string.live7_banner);
                if (HELD.equals(id)) return c.getString(R.string.live7_held);
                if (BREAK.equals(id)) return c.getString(R.string.live7_break);
                return c.getString(TestAlerts.label(id));
        }
    }

    private static int boardLabel(String id) {
        switch (id) {
            case "goal_home": return R.string.live7_goal_home;
            case "goal_away": return R.string.live7_goal_away;
            case "scorer": return R.string.live7_scorer;
            case "stats": return R.string.live7_stats;
            case "card": return R.string.live7_card;
            case "sub": return R.string.live7_sub;
            case "var": return R.string.live7_var;
            case "logo_away": return R.string.live68_test_logo_away;
            case "pens": return R.string.live69_test_pens;
            case "milestones": return R.string.live69_test_milestones;
            case "goal_shirt": return R.string.live_shirt_test;
            default: return R.string.live7_added;
        }
    }

    // ---- the info tiles' test --------------------------------------------------------------------------------------

    /**
     * Pure: what a test tile in style s shows: what's on (a copy), with made-up parts where it is not of that kind: a game
     * (madeUp, with its numbers so far) for Match night, a film for Cinematic, a series' episode for Card; the Glass
     * ribbon as it is. The channel, its times and what's next stay the guide's.
     */
    static TileData.Info tileInfo(TileData.Info from, TileKind.Style s, Game madeUp) {
        TileData.Info t = from.copy();
        switch (s) {
            case MATCH:
                if (t.game == null && madeUp != null) {
                    t.game = madeUp;
                    t.title = madeUp.homeName + " v " + madeUp.awayName;
                    t.desc = "";
                    t.facts = new ArrayList<>();
                    t.stats = new ArrayList<>();
                    t.stats.add(new String[]{"Possession", "58%", "42%"});
                    t.stats.add(new String[]{"Shots", "12", "7"});
                    t.stats.add(new String[]{"On target", "5", "2"});
                }
                t.kind = TileKind.Kind.MATCH;
                break;
            case CINEMA:
                if (t.kind != TileKind.Kind.FILM) {
                    t.kind = TileKind.Kind.FILM;
                    t.title = "A Test Film";
                    t.episode = null;
                    t.desc = "A made-up film, to see how a film's tile looks over the picture.";
                    t.overview = "";
                    t.year = "2024";
                    t.genres = "Drama · Thriller";
                    t.rating = "7.4";
                    t.certificate = "12A";
                    t.still = null;
                    t.poster = null;
                }
                break;
            case CARD:
                if (t.kind != TileKind.Kind.SERIES) {
                    t.kind = TileKind.Kind.SERIES;
                    t.title = "A Test Series";
                    t.episode = "S2 · E4";
                    t.desc = "A made-up episode, to see how a series' tile looks over the picture.";
                    t.overview = "";
                    t.year = "";
                    t.genres = "Drama";
                    t.rating = "";
                    t.certificate = "";
                    t.still = null;
                }
                break;
            default:
                break;
        }
        return t;
    }
}
