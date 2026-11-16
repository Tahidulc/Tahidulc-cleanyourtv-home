package com.gathin.tvhomephone.live;

import com.gathin.broadcast.BoardFx;
import com.gathin.tvhomephone.sports.Game;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
 * 5.02: the Scoreboard Lab wired into the phone's board, as the TV's: a goal's crest burst and then its net, every game
 * since 5.03 (the 3D kits keeping the band), the Lab's stats under their own switches and at their own minutes, the momentum wave's stat, the red card's and
 * the substitution's moments on the pop-outs, FotMob's weather for the weather touch, the five Lab logos.
 */
public class LabWiringTest {

    // ---- the goal sets -------------------------------------------------------------------------------------------------

    @Test
    public void everyGoalIsTheCrestBurstAndTheKitsKeepTheBand() {
        for (int i = 0; i < 300; i++) {
            String id = "40" + i;
            int set = OverlayBug.setFor(OverlayBug.Style.CLASSIC, id);
            assertEquals(BoardFx.goalSet(id), set);
            // the crest burst, then the net, every game, whatever look shows it (bar the 3D kits' own gold GOAL)
            assertEquals(BoardFx.GOAL_BURST, set);
            assertEquals(set, OverlayBug.setFor(OverlayBug.Style.FLOODLIGHT, id));
            assertEquals(set, OverlayBug.setFor(OverlayBug.Style.PL_WORLD, id));
            assertEquals(BoardFx.GOAL_BAND, OverlayBug.setFor(OverlayBug.Style.KITS, id));
        }
        // no game known: the burst all the same
        assertEquals(BoardFx.GOAL_BURST, OverlayBug.setFor(OverlayBug.Style.CLASSIC, null));
        assertEquals(BoardFx.GOAL_BURST, OverlayBug.setFor(OverlayBug.Style.CLASSIC, ""));
        assertEquals(BoardFx.GOAL_BAND, OverlayBug.setFor(OverlayBug.Style.KITS, null));
    }

    @Test
    public void theScoreMovesAlongItsOwnTime() {
        assertEquals(0f, OverlayBug.along(100, 200, 600), 0f);
        assertEquals(0.5f, OverlayBug.along(400, 200, 600), 1e-6f);
        assertEquals(1f, OverlayBug.along(900, 200, 600), 0f);
        // A rolling board's band: the new score rolls up as the band goes, done before the goal's moment is.
        assertTrue(OverlayBug.ROLL_BAND_FROM > OverlayBug.BAND_OUT && OverlayBug.ROLL_BAND_TO < OverlayBug.GOAL_MS);
        // The score's two halves, as the card and the stacked rows take them.
        assertArrayEquals(new String[]{"2", "1"}, OverlayBug.goals("2–1"));
        assertArrayEquals(new String[]{"", ""}, OverlayBug.goals("HT"));
        assertEquals("2 - 1", OverlayBug.cardScore("2–1"));
    }

    // ---- the stats' switches, moments and picks ------------------------------------------------------------------------

    @Test
    public void eachStatHasItsSwitchAndItsName() {
        assertEquals(18, BoardLab.KEYS.length);
        assertEquals(BoardLab.KEYS.length, BoardLab.NAMES.length);
        assertEquals(BoardLab.KEYS.length, new HashSet<>(Arrays.asList(BoardLab.KEYS)).size());
        // what the board's stats are called, to the switch each comes under
        String[][] by = {{"Expected goals", "xg"}, {"xG on target", "xgot"}, {"Big chances", "chances"}, {"Touches in box", "touches"},
                {"Momentum", "momentum"}, {"Top rated", "ratings"}, {"Player of the match", "ratings"}, {"Possession", "basics"},
                {"Shots", "basics"}, {"On target", "basics"}, {"Corners", "basics"}, {"Pass accuracy", "basics"}, {"Fouls", "basics"},
                {"Distance run", "basics"}, {"Form", "form"}, {"Streak", "streaks"}, {"Head to head", "h2h"}, {"Goal drought", "drought"},
                {"Comeback", "comeback"}, {"Early goal", "early"}, {"Referee", "referee"}, {"League table", "table"},
                {"As it stands", "table"}, {"Big save", "shots"}, {"Great finish", "shots"}, {"Win chance", "winchance"}};
        Set<String> keys = new HashSet<>(Arrays.asList(BoardLab.KEYS));
        for (String[] b : by) {
            assertEquals(b[0], b[1], BoardLab.statKey(b[0]));
            assertTrue(b[1], keys.contains(b[1]));
        }
        // and the weather touch has its own; one no switch covers is always shown
        assertTrue(keys.contains("weather"));
        assertEquals("", BoardLab.statKey("Did you know"));
        assertEquals("", BoardLab.statKey(null));
        // what the Lab's own stats are named, as MatchStats names them
        assertEquals("early", BoardLab.statKey(com.gathin.broadcast.MatchStats.early("0:48")[0]));
        assertEquals("referee", BoardLab.statKey(com.gathin.broadcast.MatchStats.refCards("Sam Example", 2, 0)[0]));
    }

    @Test
    public void theLabsMomentsComeAtTheirOwnMinutes() {
        assertNull(BoardLab.slot(Game.LIVE, false, 11));
        for (int m = 12; m <= 20; m++) assertEquals("e1", BoardLab.slot(Game.LIVE, false, m));
        assertNull(BoardLab.slot(Game.LIVE, false, 21));
        assertNull(BoardLab.slot(Game.LIVE, false, 50));
        for (int m = 51; m <= 58; m++) assertEquals("s2", BoardLab.slot(Game.LIVE, false, m));
        for (int m = 83; m <= 87; m++) assertEquals("late", BoardLab.slot(Game.LIVE, false, m));
        assertNull(BoardLab.slot(Game.LIVE, false, 88));
        // in play only: never at half time, before or after
        assertNull(BoardLab.slot(Game.LIVE, true, 15));
        assertNull(BoardLab.slot(Game.PRE, false, 15));
        assertNull(BoardLab.slot(Game.POST, false, 85));
        // none of them on the board's own stats moments (the first half's from 25, the second's from 64)
        for (int m = 0; m < 130; m++) {
            String own = ScoreBoard.statsSlot(false, 0, Game.LIVE, false, m), lab = BoardLab.slot(Game.LIVE, false, m);
            assertTrue(m + "'", own == null || lab == null);
        }
    }

    @Test
    public void eachMomentPicksItsStatsInOrder() {
        Map<String, String[]> lab = new HashMap<>();
        String[] h2h = {"Head to head", "Last 6: WHI 3W 2D 1L", "", "one"}, sh = {"Streak", "WHI have won 4 in a row", "", "one"};
        String[] sa = {"Streak", "RIV unbeaten in 5", "", "one"}, dh = {"Goal drought", "WHI without a goal in their last 3 games", "", "one"};
        String[] table = {"As it stands", "WHI 3rd (up 1) · RIV 9th", "", "one"};
        lab.put("Head to head", h2h);
        lab.put("Streak home", sh);
        lab.put("Streak away", sa);
        lab.put("Goal drought home", dh);
        lab.put("As it stands", table);
        assertEquals(Arrays.asList(h2h, sh, sa), BoardLab.pick("e1", lab));
        assertEquals(Arrays.asList(table, dh), BoardLab.pick("s2", lab));
        assertEquals(java.util.Collections.singletonList(table), BoardLab.pick("late", lab));
        assertTrue(BoardLab.pick("h1", lab).isEmpty());
        assertTrue(BoardLab.pick("e1", new HashMap<>()).isEmpty());
        assertTrue(BoardLab.pick(null, lab).isEmpty());
        // Before kick-off the board's own moment takes the head to head and the runs too, after the form and the table.
        Map<String, String[]> by = new HashMap<>(lab);
        by.put("Form", new String[]{"Form", "WWDLW", "LDWWD", "form"});
        by.put("League table", new String[]{"League table", "4th · 31 pts", "9th · 22 pts", "text"});
        List<String[]> pre = ScoreBoard.statsFor("pre", by);
        assertEquals(5, pre.size());
        assertEquals("Form", pre.get(0)[0]);
        assertEquals("League table", pre.get(1)[0]);
        assertEquals("Head to head", pre.get(2)[0]);
        assertEquals(sh, pre.get(3));
        assertEquals(sa, pre.get(4));
        // the head to head alone is enough for it
        Map<String, String[]> one = new HashMap<>();
        one.put("Head to head", h2h);
        assertEquals(1, ScoreBoard.statsFor("pre", one).size());
    }

    @Test
    public void theRefereesCardsAreCountedFromFotMob() {
        List<String[]> ev = new ArrayList<>();
        ev.add(new String[]{"Card", "12", "1", "A. Player", "1", "Yellow"});
        ev.add(new String[]{"Card", "40", "0", "B. Player", "2", "YellowRed"});
        ev.add(new String[]{"Card", "71", "0", "C. Player", "3", "Red"});
        ev.add(new String[]{"Substitution", "60", "1", "D. Player", "4", "E. Player", "5"});
        ev.add(new String[]{"Card", "80"});
        assertArrayEquals(new int[]{1, 2}, BoardLab.cards(ev));
        assertArrayEquals(new int[]{0, 0}, BoardLab.cards(null));
    }

    // ---- the momentum wave ---------------------------------------------------------------------------------------------

    @Test
    public void theWavesStatCarriesItsValues() {
        List<int[]> mom = new ArrayList<>();
        for (int m = 1; m <= 30; m++) mom.add(new int[]{m, m <= 20 ? -10 : 60});
        String[] w = BoardLab.wave(mom, 30, "WHI", "RIV");
        assertNotNull(w);
        assertEquals("Momentum", w[0]);
        assertEquals("wave", w[3]);
        assertEquals("WHI on top", w[2]);
        List<int[]> back = BoardStats.waveValues(w[1]);
        // the last fifteen minutes up to now
        assertEquals(16, back.size());
        assertArrayEquals(new int[]{15, -10}, back.get(0));
        assertArrayEquals(new int[]{30, 60}, back.get(back.size() - 1));
        // the away side on top, or neither
        List<int[]> away = new ArrayList<>(), level = new ArrayList<>();
        for (int m = 40; m <= 50; m++) {
            away.add(new int[]{m, -45});
            level.add(new int[]{m, m % 2 == 0 ? 5 : -5});
        }
        assertEquals("RIV on top", BoardLab.wave(away, 50, "WHI", "RIV")[2]);
        assertEquals("Level", BoardLab.wave(level, 50, "WHI", "RIV")[2]);
        // too few values to draw: none
        List<int[]> few = new ArrayList<>();
        few.add(new int[]{10, 20});
        few.add(new int[]{11, 30});
        assertNull(BoardLab.wave(few, 11, "WHI", "RIV"));
        assertNull(BoardLab.wave(null, 11, "WHI", "RIV"));
    }

    @Test
    public void theWavesValuesGoThroughItsWords() {
        List<int[]> v = new ArrayList<>();
        v.add(new int[]{21, 35});
        v.add(new int[]{22, -12});
        v.add(null);
        v.add(new int[]{23});
        assertEquals("21:35,22:-12", BoardStats.waveCode(v));
        List<int[]> back = BoardStats.waveValues("21:35,22:-12,x:1,:4,5,23 : 7");
        assertEquals(3, back.size());
        assertArrayEquals(new int[]{21, 35}, back.get(0));
        assertArrayEquals(new int[]{22, -12}, back.get(1));
        assertArrayEquals(new int[]{23, 7}, back.get(2));
        assertTrue(BoardStats.waveValues("").isEmpty());
        assertTrue(BoardStats.waveValues(null).isEmpty());
        assertEquals("", BoardStats.waveCode(null));
        // out long enough to draw in and be read; every other stat as long as before
        assertEquals(BoardFx.WAVE_MS, BoardStats.eachMs(new String[]{"Momentum", "1:2", "Level", "wave"}));
        assertEquals(BoardStats.EACH_MS, BoardStats.eachMs(new String[]{"Possession", "58%", "42%"}));
        assertTrue(BoardFx.WAVE_IN < BoardFx.WAVE_MS);
    }

    @Test
    public void theWaveOnceOrTwiceAHalf() {
        for (int i = 0; i < 50; i++) {
            String id = "77" + i;
            Set<String> shown = new HashSet<>();
            int first = 0, second = 0;
            for (int m = 0; m <= 95; m++) {
                if (BoardFx.waveDue(id, m, shown) != null) {
                    if (m <= 45) first++;
                    else second++;
                }
            }
            assertTrue(id, first >= 1 && first <= 2);
            assertTrue(id, second >= 1 && second <= 2);
        }
    }

    // ---- the pop-outs' moments -----------------------------------------------------------------------------------------

    @Test
    public void aRedCardsAlertHasItsCard() {
        ScoreBoard.Drop red = new ScoreBoard.Drop();
        red.chip = "RED CARD";
        assertTrue(BoardDrop.redCard(red));
        ScoreBoard.Drop yellow = new ScoreBoard.Drop();
        yellow.chip = "YELLOW CARD";
        assertFalse(BoardDrop.redCard(yellow));
        ScoreBoard.Drop lineups = new ScoreBoard.Drop();
        lineups.chip = "RED CARD";
        lineups.lineups = true;
        assertFalse(BoardDrop.redCard(lineups));
        assertFalse(BoardDrop.redCard(null));
        // the card swings up as the alert comes to rest, and is held there (never tucked away while the alert is out)
        assertTrue(BoardSide.SLIDE_MS / 2 + BoardFx.RED_UP < BoardFx.RED_AWAY);
    }

    @Test
    public void aSubstitutionsPlayersCrossInThenStay() {
        assertEquals(0L, BoardDrop.swapT(-300));
        assertEquals(500L, BoardDrop.swapT(500));
        assertEquals(BoardDrop.SWAP_IN_MS, BoardDrop.swapT(60_000));
        // dim and a little to the left as the page comes, fully in and in place by the end, its arrow back to its size
        float[] start = BoardFx.swapAt(BoardDrop.swapT(0)), in = BoardFx.swapAt(BoardDrop.swapT(10_000));
        assertTrue(start[BoardFx.SW_IN_ALPHA] < 0.5f);
        assertTrue(start[BoardFx.SW_IN_SHIFT] < 0f);
        assertEquals(1f, in[BoardFx.SW_IN_ALPHA], 1e-4f);
        assertEquals(0f, in[BoardFx.SW_IN_SHIFT], 1e-4f);
        assertEquals(1f, in[BoardFx.SW_IN_POP], 1e-4f);
        // and each page's players are in before it turns to the next
        assertTrue(BoardDrop.SWAP_IN_MS < 4_000L);
    }

    // ---- the weather touch: FotMob's weather only ----------------------------------------------------------------------

    @Test
    public void theSkyIsFotMobsOrNone() throws Exception {
        FotMob.Match none = FotMob.parse(new JSONObject("{\"content\":{}}"));
        assertNull(FotMob.sky(none));
        assertNull(FotMob.sky(null));
        FotMob.Match rain = FotMob.parse(new JSONObject("{\"content\":{\"weather\":{\"iconCode\":12,\"precipitation\":1.4,"
                + "\"snow\":0,\"windSpeed\":14,\"cloudCover\":90,\"lastUpdated\":\"2026-11-15T19:00:00Z\"}}}"));
        assertEquals(12, rain.weatherIcon);
        assertEquals("2026-11-15T19:00:00Z", rain.weatherAt);
        assertEquals(BoardFx.sky(12, 1.4, 0, 14, 90), FotMob.sky(rain));
        assertNotNull(FotMob.sky(rain));
        // the head to head, each side's form and the shots kept as FotMob gives them for MatchStats
        FotMob.Match more = FotMob.parse(new JSONObject("{\"content\":{\"h2h\":{\"matches\":[]},\"shotmap\":{\"shots\":[{\"id\":1}]},"
                + "\"matchFacts\":{\"teamForm\":[[{\"score\":\"1 - 0\"}],[]]}}}"));
        assertNotNull(more.h2h);
        assertEquals(1, more.shotsRaw.length());
        assertEquals(1, more.homeForm.length());
        assertEquals(0, more.awayForm.length());
    }

    @Test
    public void aComebackOnlyWithEveryGoalSeen() {
        // seen from 0-0: the first goal, then each after it in turn
        assertTrue(ScoreBoard.everyGoalSeen(true, 1, 0));
        assertTrue(ScoreBoard.everyGoalSeen(true, 4, 3));
        // one missed while the board was away (two goals before this one, one seen), or taken back since
        assertFalse(ScoreBoard.everyGoalSeen(true, 2, 2));
        assertFalse(ScoreBoard.everyGoalSeen(true, 3, 1));
        // the board came up after goals were scored: none of the game's comebacks
        assertFalse(ScoreBoard.everyGoalSeen(false, 1, 0));
        assertFalse(ScoreBoard.everyGoalSeen(true, 1, -1));
    }

    @Test
    public void aWeatherMinuteWaitsForItsSky() {
        // A minute not taken while FotMob has no sky yet stays due for its few minutes, then is gone.
        for (int i = 0; i < 20; i++) {
            String id = "93" + i;
            int m = BoardFx.weatherMinutes(id)[0];
            Set<String> shown = new HashSet<>();
            assertTrue(id, BoardFx.weatherDue(id, m + 2, new HashSet<>()));
            assertFalse(id, BoardFx.weatherDue(id, m + 4, shown));
            // taken once, never again
            assertTrue(id, BoardFx.weatherDue(id, m + 1, shown));
            assertFalse(id, BoardFx.weatherDue(id, m + 2, shown));
        }
    }

    @Test
    public void theWeatherTouchTwoOrThreeTimesAGame() {
        for (int i = 0; i < 40; i++) {
            String id = "91" + i;
            Set<String> shown = new HashSet<>();
            int n = 0;
            for (int m = 0; m <= 95; m++) if (BoardFx.weatherDue(id, m, shown)) n++;
            assertTrue(id + ": " + n, n >= 2 && n <= 3);
        }
    }

    // ---- the logos -----------------------------------------------------------------------------------------------------

    @Test
    public void theLabsFiveLogosAreLooksOfTheirOwn() {
        LogoCover.Look[] all = LogoCover.Look.values();
        assertEquals(26, all.length);
        com.gathin.broadcast.LabMarks.Look[] lab = com.gathin.broadcast.LabMarks.Look.values();
        for (int i = 0; i < lab.length; i++) {
            LogoCover.Look l = all[all.length - lab.length + i];
            assertEquals(lab[i], l.lab());
            assertEquals(lab[i].label, l.label);
            assertTrue(l.own());
            assertFalse(l.channel());
            assertFalse(l.broadcast());
            assertEquals(l, LogoCover.Look.of(l.name()));
        }
        for (LogoCover.Look l : all) if (l.ordinal() <= LogoCover.Look.STYLES.ordinal()) assertNull(l.lab());
        // as tall as its box where it fits, less tall to fit where its words want more room
        assertEquals(40f, LogoCover.labHeight(100f, 120f, 40f), 0f);
        assertEquals(20f, LogoCover.labHeight(240f, 120f, 40f), 1e-4f);
        assertEquals(40f, LogoCover.labHeight(0f, 120f, 40f), 0f);
    }
}
