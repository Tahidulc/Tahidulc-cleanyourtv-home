package com.gathin.tvhome;

import com.gathin.broadcast.BoardFx;
import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.LabMarks;
import com.gathin.broadcast.Sfx;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Scoreboard Lab on the TV's board (5.27): its five looks and logos, the goal's three sets by game, the net after every
 * goal, the quiet sounds, the red card, VAR and the sub swap, the weather touch and the momentum wave, and the Lab's stats
 * with their switches. Made-up clubs only (Whitebrook WHI, Riverside RIV) and made-up game ids.
 */
public class ScoreboardLabTest {

    private static final String[] GAMES = new String[40];

    static {
        for (int i = 0; i < GAMES.length; i++) GAMES[i] = "lab-game-" + (7000 + i * 37);
    }

    // ---- the looks and the logos ----------------------------------------------------------------------------------

    @Test
    public void theLabsFiveLooksComeAfterTheKitsDrawnByCompBug() {
        BoardBug.Style[] lab = {BoardBug.Style.FLOODLIGHT, BoardBug.Style.SPLITKIT, BoardBug.Style.TICKER, BoardBug.Style.DUO,
                BoardBug.Style.LED};
        for (int i = 0; i < lab.length; i++) {
            assertEquals(BoardBug.Style.KITS.ordinal() + 1 + i, lab[i].ordinal());
            assertNotNull(lab[i].kind);
            assertEquals(lab[i], BoardBug.Style.of(lab[i].name()));
            assertTrue(lab[i].kind.lab());
        }
        assertEquals(CompBug.Kind.FLOODLIGHT, BoardBug.Style.FLOODLIGHT.kind);
        assertEquals(CompBug.Kind.LED, BoardBug.Style.LED.kind);
        // The odometer's two: Floodlight and Stadium LED always roll the score.
        assertTrue(BoardBug.Style.FLOODLIGHT.kind.rolls());
        assertTrue(BoardBug.Style.LED.kind.rolls());
        assertFalse(BoardBug.Style.SPLITKIT.kind.rolls());
        assertFalse(BoardBug.Style.TICKER.kind.rolls());
        assertFalse(BoardBug.Style.DUO.kind.rolls());
        assertEquals("Floodlight", BoardBug.Style.FLOODLIGHT.label);
    }

    @Test
    public void theLabsFiveLogosAreDrawnWholeAfterTheStyles() {
        LogoMark.Look[] lab = {LogoMark.Look.FLOODLIGHT, LogoMark.Look.SPLITKIT, LogoMark.Look.TICKER, LogoMark.Look.DUO,
                LogoMark.Look.LED};
        LabMarks.Look[] marks = LabMarks.Look.values();
        assertEquals(marks.length, lab.length);
        for (int i = 0; i < lab.length; i++) {
            assertEquals(LogoMark.Look.STYLES.ordinal() + 1 + i, lab[i].ordinal());
            assertEquals(marks[i], lab[i].lab());
            assertEquals(marks[i].label, lab[i].label);
            // Drawn whole (see-through capable), not a channel look nor Broadcast, kept by name.
            assertTrue(lab[i].own());
            assertFalse(lab[i].channel());
            assertFalse(lab[i].broadcast());
            assertFalse(lab[i].sports());
            assertFalse(lab[i].event());
            assertEquals(lab[i], LogoMark.Look.of(lab[i].name()));
        }
        assertNull(LogoMark.Look.STYLES.lab());
        assertNull(LogoMark.Look.GLASS.lab());
        assertNull(LogoMark.Look.SIGNATURE.lab());
        assertEquals(24, LogoMark.Look.values().length);
        // The last look is the Lab's LED: the per-channel picker steps on to "as the rest" after it.
        assertNull(ChannelOverlays.nextLook(LogoMark.Look.LED.name(), 1));
        assertEquals(LogoMark.Look.LED.name(), ChannelOverlays.nextLook(null, -1));
    }

    @Test
    public void aLabLogoIsAsTallAsItsBoxUnlessItsWordsWantMoreRoom() {
        assertEquals(40f, LogoMark.labHeight(120f, 160f, 40f), 0f);
        assertEquals(40f, LogoMark.labHeight(160f, 160f, 40f), 0f);
        // Too wide at the box's height: less tall, in proportion, so it fits the box's width exactly.
        assertEquals(32f, LogoMark.labHeight(200f, 160f, 40f), 1e-4f);
        assertEquals(40f, LogoMark.labHeight(0f, 160f, 40f), 0f);
    }

    // ---- a goal: three sets, by game -------------------------------------------------------------------------------

    @Test
    public void eachGameHasOneOfTheThreeGoalSetsAndKeepsIt() {
        Set<Integer> seen = new HashSet<>();
        for (String g : GAMES) {
            int set = BoardFx.goalSet(g);
            assertTrue(set >= BoardFx.GOAL_BAND && set <= BoardFx.GOAL_BURST);
            assertEquals(set, BoardFx.goalSet(g));
            seen.add(set);
            // Every look draws the game's set; the 3D kits keep their own gold GOAL (today's band).
            assertEquals(set, BoardBug.setFor(BoardBug.Style.CLASSIC, set));
            assertEquals(set, BoardBug.setFor(BoardBug.Style.FLOODLIGHT, set));
            assertEquals(set, BoardBug.setFor(BoardBug.Style.PL_WORLD, set));
            assertEquals(BoardFx.GOAL_BAND, BoardBug.setFor(BoardBug.Style.KITS, set));
        }
        assertEquals(3, seen.size());
    }

    @Test
    public void eachSetRunsItsOwnLengthTheNetAfterIt() {
        // Today's band: in, across, out; the net as the band goes; the glow after.
        assertEquals(BoardBug.BAND_OUT + BoardBug.BAND_MS, BoardBug.setMs(BoardFx.GOAL_BAND));
        assertEquals(BoardBug.RIPPLE_AT, BoardBug.meshAt(BoardFx.GOAL_BAND));
        assertEquals(BoardBug.GOAL_MS, BoardBug.goalMs(BoardFx.GOAL_BAND));
        // The flip: wash, shine and the digit, the net from 1.2 s.
        assertEquals(BoardFx.FLIP_MS, BoardBug.setMs(BoardFx.GOAL_FLIP));
        assertEquals(BoardFx.MESH_AFTER_FLIP, BoardBug.meshAt(BoardFx.GOAL_FLIP));
        assertEquals(Math.max(BoardFx.FLIP_MS, BoardFx.MESH_AFTER_FLIP + BoardFx.MESH_MS), BoardBug.goalMs(BoardFx.GOAL_FLIP));
        // The burst: rings, the roll and the glow, the net from 1.3 s.
        assertEquals(BoardFx.BURST_MS, BoardBug.setMs(BoardFx.GOAL_BURST));
        assertEquals(BoardFx.MESH_AFTER_BURST, BoardBug.meshAt(BoardFx.GOAL_BURST));
        assertEquals(Math.max(BoardFx.BURST_MS, BoardFx.MESH_AFTER_BURST + BoardFx.MESH_MS), BoardBug.goalMs(BoardFx.GOAL_BURST));
        for (int set = BoardFx.GOAL_BAND; set <= BoardFx.GOAL_BURST; set++) {
            // The net always shivers inside the goal's moment, after every goal.
            assertTrue(BoardBug.meshAt(set) > 0);
            assertTrue(BoardBug.meshAt(set) + BoardFx.MESH_MS <= BoardBug.goalMs(set));
        }
    }

    @Test
    public void alertsWaitForTheGoalsOwnMomentInItsSet() {
        long at = 1_000_000L;
        for (int set = BoardFx.GOAL_BAND; set <= BoardFx.GOAL_BURST; set++) {
            long len = BoardBug.setMs(set);
            assertEquals(len, BoardBug.bandLeft(at, set, at));
            assertEquals(len - 1000, BoardBug.bandLeft(at, set, at + 1000));
            assertEquals(0, BoardBug.bandLeft(at, set, at + len));
            assertEquals(0, BoardBug.bandLeft(at, set, at + len + 5000));
            assertEquals(0, BoardBug.bandLeft(0, set, at));
        }
        // The Lab's two are over sooner than today's band.
        assertTrue(BoardBug.bandLeft(at, BoardFx.GOAL_FLIP, at) < BoardBug.bandLeft(at, BoardFx.GOAL_BAND, at));
        assertTrue(BoardBug.bandLeft(at, BoardFx.GOAL_BURST, at) < BoardBug.bandLeft(at, BoardFx.GOAL_BAND, at));
    }

    @Test
    public void theScoreFlipsOrRollsAsItsSetAndLookSay() {
        // Today's band on an ordinary look: no move (the score pops as before).
        assertArrayEquals(new float[]{0f, 1f}, BoardBug.moveAt(BoardFx.GOAL_BAND, false, 2000), 0f);
        // On an odometer look it rolls as the band goes.
        float[] early = BoardBug.moveAt(BoardFx.GOAL_BAND, true, BoardBug.BAND_ROLL_FROM - 1);
        float[] late = BoardBug.moveAt(BoardFx.GOAL_BAND, true, BoardBug.BAND_ROLL_TO + 1);
        assertEquals(CompBug.MOVE_ROLL, (int) early[0]);
        assertEquals(0f, early[1], 0f);
        assertEquals(1f, late[1], 0f);
        // The flip turns its figure in; on an odometer look it rolls instead.
        float[] flip = BoardBug.moveAt(BoardFx.GOAL_FLIP, false, 500);
        assertEquals(CompBug.MOVE_FLIP, (int) flip[0]);
        assertEquals(BoardFx.flipMove(500), flip[1], 0f);
        assertEquals(CompBug.MOVE_ROLL, (int) BoardBug.moveAt(BoardFx.GOAL_FLIP, true, 500)[0]);
        // The burst always rolls it up.
        float[] burst = BoardBug.moveAt(BoardFx.GOAL_BURST, false, 900);
        assertEquals(CompBug.MOVE_ROLL, (int) burst[0]);
        assertEquals(BoardFx.rollMove(900), burst[1], 0f);
        // Before and after the goal: still.
        for (int set = BoardFx.GOAL_BAND; set <= BoardFx.GOAL_BURST; set++) {
            assertArrayEquals(new float[]{0f, 1f}, BoardBug.moveAt(set, true, -1), 0f);
            assertArrayEquals(new float[]{0f, 1f}, BoardBug.moveAt(set, true, BoardBug.goalMs(set)), 0f);
        }
    }

    @Test
    public void theScoreBeforeTheGoalIsReadInEitherDash() {
        assertArrayEquals(new String[]{"2", "1"}, BoardBug.goalsOf("2–1"));
        assertArrayEquals(new String[]{"0", "3"}, BoardBug.goalsOf("0 - 3"));
        assertArrayEquals(new String[]{"", ""}, BoardBug.goalsOf(""));
        assertArrayEquals(new String[]{"", ""}, BoardBug.goalsOf(null));
        assertArrayEquals(new String[]{"", ""}, BoardBug.goalsOf("19:45"));
        assertEquals("2 - 1", BoardBug.cardScoreOf("2–1"));
        assertEquals("19:45", BoardBug.cardScoreOf("19:45"));
        assertEquals("", BoardBug.cardScoreOf(null));
    }

    // ---- the quiet sounds ------------------------------------------------------------------------------------------

    @Test
    public void eachSetHasItsOwnQuietSoundsTheNetLast() {
        LinkedHashMap<String, Long> flip = BoardBug.goalSounds(BoardFx.GOAL_FLIP, false);
        assertEquals(Arrays.asList(Sfx.FLIP, Sfx.NET), new ArrayList<>(flip.keySet()));
        assertEquals(BoardFx.FLIP_FROM, (long) flip.get(Sfx.FLIP));
        assertEquals(BoardFx.MESH_AFTER_FLIP, (long) flip.get(Sfx.NET));
        // On an odometer look the flip's figure rolls: the roll's sound in its place.
        assertEquals(Arrays.asList(Sfx.ROLL, Sfx.NET), new ArrayList<>(BoardBug.goalSounds(BoardFx.GOAL_FLIP, true).keySet()));
        LinkedHashMap<String, Long> burst = BoardBug.goalSounds(BoardFx.GOAL_BURST, false);
        assertEquals(Arrays.asList(Sfx.BURST, Sfx.ROLL, Sfx.NET), new ArrayList<>(burst.keySet()));
        assertEquals(0L, (long) burst.get(Sfx.BURST));
        assertEquals(BoardFx.ROLL_FROM, (long) burst.get(Sfx.ROLL));
        assertEquals(BoardFx.MESH_AFTER_BURST, (long) burst.get(Sfx.NET));
        // Today's band: the family's swish on the new score as before (on every look, odometers too), the net's after.
        for (boolean rolls : new boolean[]{false, true}) {
            LinkedHashMap<String, Long> band = BoardBug.goalSounds(BoardFx.GOAL_BAND, rolls);
            assertEquals(Arrays.asList(Sfx.NET), new ArrayList<>(band.keySet()));
            assertEquals(BoardBug.RIPPLE_AT, (long) band.get(Sfx.NET));
        }
        // Every one is one of the Lab's own, each inside its goal's moment.
        Set<String> lab = new HashSet<>(Arrays.asList(Sfx.LAB));
        for (int set = BoardFx.GOAL_BAND; set <= BoardFx.GOAL_BURST; set++) {
            for (boolean rolls : new boolean[]{false, true}) {
                for (Map.Entry<String, Long> e : BoardBug.goalSounds(set, rolls).entrySet()) {
                    assertTrue(e.getKey(), lab.contains(e.getKey()));
                    assertTrue(e.getValue() >= 0 && e.getValue() < BoardBug.goalMs(set));
                }
            }
        }
    }

    @Test
    public void theLabsSoundsAreHeardOnlyWithTheBoardUpAndTheSwishOn() {
        assertTrue(BoardLab.heard(true, 1f, true));
        assertFalse(BoardLab.heard(false, 1f, true));
        assertFalse(BoardLab.heard(true, 0f, true));
        assertFalse(BoardLab.heard(true, 0.01f, true));
        assertFalse(BoardLab.heard(true, 1f, false));
        // Quiet: under the swish the pop-outs already play.
        assertTrue(Sfx.QUIET < 0.7f);
    }

    // ---- the drops: red card, VAR, the sub swap ---------------------------------------------------------------------

    @Test
    public void aRedCardSwingsUpByItsChipAndStays() {
        BoardDrop.Drop red = new BoardDrop.Drop();
        red.chip = "RED CARD";
        BoardDrop.Drop yellow = new BoardDrop.Drop();
        yellow.chip = "YELLOW CARD";
        assertTrue(BoardDrop.red(red));
        assertFalse(BoardDrop.red(yellow));
        assertFalse(BoardDrop.red(null));
        // From half way through the alert's slide; held once it lands, never tucked away.
        assertEquals(1, BoardDrop.cardT(0));
        assertEquals(1, BoardDrop.cardT(BoardSide.SLIDE_MS / 2));
        assertEquals(500, BoardDrop.cardT(BoardSide.SLIDE_MS / 2 + 500));
        assertEquals(BoardFx.RED_AWAY - 1, BoardDrop.cardT(60_000));
        assertTrue(BoardDrop.cardT(60_000) > BoardFx.RED_UP);
    }

    @Test
    public void theSubsPlayersSwapInOnceAndHold() {
        float[] start = BoardDrop.swapNow(0), held = BoardDrop.swapNow(60_000);
        float[] lab0 = BoardFx.swapAt(0);
        assertEquals(lab0[BoardFx.SW_IN_ALPHA], start[0], 0f);
        assertEquals(lab0[BoardFx.SW_IN_SHIFT], start[1], 0f);
        assertEquals(lab0[BoardFx.SW_IN_POP], start[2], 0f);
        // Held where it lands (the Lab's loop would dim them again): the same however long the page stands.
        assertArrayEquals(BoardDrop.swapNow((long) (BoardFx.SWAP_MS * 0.6f)), held, 0f);
        assertArrayEquals(held, BoardDrop.swapNow(9_000), 0f);
        assertTrue(held[0] >= 0.99f);
        assertEquals(0f, held[1], 0.01f);
        for (long t = 0; t <= BoardFx.SWAP_MS; t += 100) {
            float[] s = BoardDrop.swapNow(t);
            assertTrue(s[0] >= 0f && s[0] <= 1f);
            assertTrue(s[2] >= 1f && s[2] <= 1.3f);
        }
    }

    // ---- the momentum wave ------------------------------------------------------------------------------------------

    @Test
    public void theWaveCarriesItsValuesAndShowsLongerThanAStat() {
        List<int[]> v = Arrays.asList(new int[]{21, 35}, new int[]{22, -12}, new int[]{23, 0});
        assertEquals("21:35,22:-12,23:0", BoardStats.waveCode(v));
        List<int[]> back = BoardStats.waveValues("21:35,22:-12,23:0");
        assertEquals(3, back.size());
        assertArrayEquals(new int[]{22, -12}, back.get(1));
        // What it cannot read is left out.
        assertEquals(1, BoardStats.waveValues("21:35,x,:4,9:").size());
        assertTrue(BoardStats.waveValues("").isEmpty());
        assertTrue(BoardStats.waveValues(null).isEmpty());
        assertEquals("", BoardStats.waveCode(null));
        assertEquals(BoardFx.WAVE_MS, BoardStats.eachMs(new String[]{"Momentum", "21:35", "WHI on top", "wave"}));
        assertEquals(BoardStats.EACH_MS, BoardStats.eachMs(new String[]{"Possession", "55%", "45%"}));
        assertEquals(BoardStats.EACH_MS, BoardStats.eachMs(null));
    }

    @Test
    public void theWaveIsTheLastFifteenMinutesAndWhoIsOnTop() {
        List<int[]> mom = new ArrayList<>();
        for (int m = 1; m <= 40; m++) mom.add(new int[]{m, m >= 20 ? 60 : -20});
        String[] w = BoardLab.wave(mom, 30, "WHI", "RIV");
        assertNotNull(w);
        assertEquals("Momentum", w[0]);
        assertEquals("wave", w[3]);
        assertEquals("WHI on top", w[2]);
        List<int[]> drawn = BoardStats.waveValues(w[1]);
        assertEquals(15, drawn.get(0)[0]);
        assertEquals(30, drawn.get(drawn.size() - 1)[0]);
        // Riverside on top, and too little to draw.
        List<int[]> away = new ArrayList<>();
        for (int m = 1; m <= 30; m++) away.add(new int[]{m, -70});
        assertEquals("RIV on top", BoardLab.wave(away, 30, "WHI", "RIV")[2]);
        assertNull(BoardLab.wave(Arrays.asList(new int[]{28, 10}, new int[]{29, 12}, new int[]{30, 8}), 30, "WHI", "RIV"));
        assertNull(BoardLab.wave(null, 30, "WHI", "RIV"));
    }

    @Test
    public void theWaveComesOnceOrTwiceAHalfAndIsLogged() {
        for (String g : GAMES) {
            Set<String> shown = new HashSet<>();
            int first = 0, second = 0;
            for (int m = 0; m <= 95; m++) {
                if (BoardFx.waveDue(g, m, shown) == null) continue;
                if (m <= 45) first++;
                else second++;
            }
            assertTrue(g, first >= 1 && first <= 2);
            assertTrue(g, second >= 1 && second <= 2);
            // Each once: a board looking again at the same minute shows none.
            for (int m = 0; m <= 95; m++) assertNull(BoardFx.waveDue(g, m, shown));
        }
        assertEquals("momentum wave at 61' (WHI on top)", BoardCover.waveNote(61, "WHI on top"));
        assertEquals("momentum wave at 24' (Level)", BoardCover.waveNote(24, "Level"));
    }

    // ---- the weather touch ------------------------------------------------------------------------------------------

    @Test
    public void theWeatherTouchComesTwoOrThreeTimesAGameAndIsLogged() {
        for (String g : GAMES) {
            Set<String> shown = new HashSet<>();
            int n = 0;
            for (int m = 0; m <= 95; m++) if (BoardFx.weatherDue(g, m, shown)) n++;
            assertTrue(g + ": " + n, n >= 2 && n <= 3);
        }
        assertEquals("weather touch: rain at 23'", BoardCover.weatherNote(BoardFx.Sky.RAIN, 23));
        assertEquals("weather touch: clear night at 71'", BoardCover.weatherNote(BoardFx.Sky.CLEAR_NIGHT, 71));
        assertEquals("rain", BoardCover.skyWords(BoardFx.Sky.RAIN));
        assertEquals("clear day", BoardCover.skyWords(BoardFx.Sky.CLEAR_DAY));
        assertEquals("none", BoardCover.skyWords(null));
    }

    @Test
    public void fotmobsWeatherHeadToHeadAndFormAreRead() throws Exception {
        JSONObject wx = new JSONObject().put("iconCode", 11).put("precipitation", 1.4).put("snow", 0).put("windSpeed", 14)
                .put("cloudCover", 90).put("lastUpdated", "2026-11-15T20:00:00Z");
        JSONArray meetings = new JSONArray().put(new JSONObject()
                .put("time", new JSONObject().put("utcTime", "2025-03-01T15:00:00.000Z"))
                .put("home", new JSONObject().put("id", "1001")).put("away", new JSONObject().put("id", "2002"))
                .put("status", new JSONObject().put("finished", true).put("cancelled", false).put("scoreStr", "2 - 1")));
        JSONArray homeForm = new JSONArray(), awayForm = new JSONArray();
        for (int i = 0; i < 4; i++) {
            homeForm.put(new JSONObject().put("score", "2 - 0").put("home", new JSONObject().put("id", "1001").put("isOurTeam", true))
                    .put("away", new JSONObject().put("id", "9").put("isOurTeam", false)));
            awayForm.put(new JSONObject().put("score", "1 - 0").put("home", new JSONObject().put("id", "9").put("isOurTeam", false))
                    .put("away", new JSONObject().put("id", "2002").put("isOurTeam", true)));
        }
        JSONObject d = new JSONObject()
                .put("general", new JSONObject().put("homeTeam", new JSONObject().put("id", 1001)).put("awayTeam", new JSONObject().put("id", 2002)))
                .put("content", new JSONObject().put("weather", wx)
                        .put("h2h", new JSONObject().put("matches", meetings))
                        .put("matchFacts", new JSONObject().put("teamForm", new JSONArray().put(homeForm).put(awayForm)))
                        .put("shotmap", new JSONObject().put("shots", new JSONArray())));
        FotMob.Match m = FotMob.parse(d);
        assertEquals(11, m.weatherIcon);
        assertEquals(BoardFx.Sky.RAIN, FotMob.skyOf(m));
        assertNotNull(m.h2h);
        assertEquals(4, m.homeForm.length());
        assertEquals(4, m.awayForm.length());
        assertNotNull(m.shotList);
        // Before kick-off: the head to head and each side's run.
        Map<String, String[]> before = BoardCover.labBefore(m, "WHI", "RIV");
        assertEquals("Last 1: WHI 1W 0D 0L", before.get("Head to head")[1]);
        assertEquals("Streak", before.get("Streak home")[0]);
        assertTrue(before.get("Streak home")[1].startsWith("WHI"));
        assertTrue(before.get("Streak away")[1].startsWith("RIV"));
        // No weather from FotMob: no sky (Open-Meteo's is the TV's fallback).
        FotMob.Match none = FotMob.parse(new JSONObject().put("content", new JSONObject()));
        assertNull(FotMob.skyOf(none));
        assertNull(FotMob.skyOf(null));
        assertTrue(BoardCover.labBefore(null, "WHI", "RIV").isEmpty());
        assertTrue(BoardCover.labBefore(none, "WHI", "RIV").isEmpty());
    }

    // ---- the Lab's stats, each with its switch ----------------------------------------------------------------------

    @Test
    public void eachLabStatHasItsOwnSwitchAndNoPaidOneOrBettingWord() {
        Set<String> keys = new HashSet<>();
        for (String[] s : BoardLab.STATS) {
            assertEquals(3, s.length);
            assertTrue(keys.add(s[0]));
            assertFalse(s[1].isEmpty() || s[2].isEmpty());
            String all = (s[1] + " " + s[2]).toLowerCase(java.util.Locale.ROOT);
            // The win chance before kick-off is percentages only: never odds, a bookmaker or a bet. Nothing paid.
            assertFalse(all, all.contains("odds") || all.contains("bookmaker") || all.matches(".*\\bbet(s|ting)?\\b.*")
                    || all.contains("field tilt") || all.contains("ppda"));
        }
        assertTrue(keys.containsAll(Arrays.asList("xg", "xgot", "chances", "touches", "momentum", "ratings", "basics", "form",
                "streaks", "h2h", "drought", "comeback", "early", "referee", "table", "shots", "winchance")));
        // What each stat on the board comes under.
        String[][] by = {{"Expected goals", "xg"}, {"xG on target", "xgot"}, {"Big chances", "chances"}, {"Touches in box", "touches"},
                {"Momentum", "momentum"}, {"Top rated", "ratings"}, {"Player of the match", "ratings"}, {"Possession", "basics"},
                {"Shots", "basics"}, {"On target", "basics"}, {"Corners", "basics"}, {"Pass accuracy", "basics"}, {"Fouls", "basics"},
                {"Yellow cards", "basics"}, {"Form", "form"}, {"Streak", "streaks"}, {"Head to head", "h2h"}, {"Goal drought", "drought"},
                {"Comeback", "comeback"}, {"Early goal", "early"}, {"Referee", "referee"}, {"League table", "table"},
                {"As it stands", "table"}, {"Big save", "shots"}, {"Great finish", "shots"}, {"Win chance", "winchance"}};
        for (String[] b : by) {
            assertEquals(b[0], b[1], BoardLab.statKey(b[0]));
            assertTrue(b[1], keys.contains(BoardLab.statKey(b[0])));
        }
        // No switch of its own: always shown (with the stats on).
        assertEquals("", BoardLab.statKey("Did you know"));
        assertEquals("", BoardLab.statKey(null));
        assertEquals("", BoardLab.statKey(""));
    }

    @Test
    public void theLabsOwnStatsMomentsComeEarlyAfterTheBreakAndLate() {
        assertNull(BoardLab.slot(0, false, 11));
        for (int m = 12; m <= 20; m++) assertEquals("e1", BoardLab.slot(0, false, m));
        assertNull(BoardLab.slot(0, false, 21));
        assertNull(BoardLab.slot(0, false, 50));
        for (int m = 51; m <= 58; m++) assertEquals("s2", BoardLab.slot(0, false, m));
        assertNull(BoardLab.slot(0, false, 59));
        for (int m = 83; m <= 87; m++) assertEquals("late", BoardLab.slot(0, false, m));
        assertNull(BoardLab.slot(0, false, 88));
        // In play only: not at half time, before kick-off or after full time.
        assertNull(BoardLab.slot(0, true, 15));
        assertNull(BoardLab.slot(1, false, 15));
        assertNull(BoardLab.slot(2, false, 85));
        // Away from the board's usual stats moments (25 to 41, 64 to 82).
        for (int m = 0; m <= 95; m++) {
            if (BoardLab.slot(0, false, m) != null) assertNull(String.valueOf(m), BoardCover.statsSlot(false, 0, 0, false, m));
        }
    }

    @Test
    public void eachLabMomentShowsItsOwnStats() {
        Map<String, String[]> lab = new HashMap<>();
        lab.put("Head to head", new String[]{"Head to head", "Last 6: WHI 3W 2D 1L", "", "one"});
        lab.put("Streak home", new String[]{"Streak", "WHI have won 4 in a row", "", "one"});
        lab.put("Streak away", new String[]{"Streak", "RIV unbeaten in 5", "", "one"});
        lab.put("Goal drought away", new String[]{"Goal drought", "RIV without a goal in their last 3 games", "", "one"});
        lab.put("As it stands", new String[]{"As it stands", "WHI 3rd · 31 pts", "RIV 9th · 22 pts", "text"});
        List<String[]> e1 = BoardLab.pick("e1", lab);
        assertEquals(3, e1.size());
        assertEquals("Head to head", e1.get(0)[0]);
        assertEquals("WHI have won 4 in a row", e1.get(1)[1]);
        assertEquals("RIV unbeaten in 5", e1.get(2)[1]);
        // Without the runs, a drought there is (said only past the half hour, so never this early in a real game).
        Map<String, String[]> fewer = new HashMap<>(lab);
        fewer.remove("Streak home");
        fewer.remove("Streak away");
        assertEquals("Goal drought", BoardLab.pick("e1", fewer).get(1)[0]);
        List<String[]> s2 = BoardLab.pick("s2", lab);
        assertEquals(2, s2.size());
        assertEquals("As it stands", s2.get(0)[0]);
        assertEquals("Goal drought", s2.get(1)[0]);
        List<String[]> late = BoardLab.pick("late", lab);
        assertEquals(1, late.size());
        assertEquals("As it stands", late.get(0)[0]);
        assertTrue(BoardLab.pick("e1", new HashMap<>()).isEmpty());
        assertTrue(BoardLab.pick("h1", lab).isEmpty());
        assertTrue(BoardLab.pick(null, lab).isEmpty());
        assertTrue(BoardLab.pick("late", null).isEmpty());
        // Before kick-off the head to head and the runs join the usual ones.
        Map<String, String[]> pre = new HashMap<>(lab);
        pre.put("Form", new String[]{"Form", "WWDLW", "LDWWW"});
        List<String[]> before = BoardCover.statsFor("pre", pre);
        List<String> names = new ArrayList<>();
        for (String[] s : before) names.add(s[0]);
        assertTrue(names.contains("Head to head"));
        assertTrue(names.contains("Streak"));
        assertTrue(before.size() <= 5);
    }

    @Test
    public void theRefereesCardsAreCountedFromFotmobsEvents() {
        List<String[]> ev = new ArrayList<>();
        ev.add(new String[]{"Card", "12", "1", "Ash", "11", "Yellow", ""});
        ev.add(new String[]{"Card", "40", "0", "Birch", "12", "Yellow", ""});
        ev.add(new String[]{"Substitution", "46", "1", "Cedar", "13", "Ash", "11"});
        ev.add(new String[]{"Card", "70", "0", "Birch", "12", "YellowRed", ""});
        ev.add(new String[]{"Card", "88", "1", "Dale", "14", "Red", ""});
        ev.add(null);
        assertArrayEquals(new int[]{2, 2}, BoardLab.cards(ev));
        assertArrayEquals(new int[]{0, 0}, BoardLab.cards(new ArrayList<>()));
        assertArrayEquals(new int[]{0, 0}, BoardLab.cards(null));
    }

    @Test
    public void theLabsStatsAreLoggedByName() {
        List<String[]> two = Arrays.asList(new String[]{"Head to head", "Last 6: WHI 3W 2D 1L", "", "one"},
                new String[]{"Streak", "WHI have won 4 in a row", "", "one"});
        assertEquals("Lab stats at 15': Head to head, Streak", BoardCover.labNote("stats at 15'", two));
        List<String[]> one = java.util.Collections.singletonList(new String[]{"Big save", "0.62 xGOT from Ash's shot", "", "one"});
        assertEquals("Lab Big save (0.62 xGOT from Ash's shot)", BoardCover.labNote("", one));
        assertEquals("Lab Early goal", BoardCover.labNote(null, java.util.Collections.singletonList(new String[]{"Early goal", ""})));
        assertEquals("Lab stats at 85':", BoardCover.labNote("stats at 85'", new ArrayList<>()));
    }
}
