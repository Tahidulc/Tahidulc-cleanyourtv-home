package com.gathin.tvhome;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 3.94's sports: players followed, a club's season, the weekend planner, See it again and the half-time hop. */
public class SportsExtrasTest {

    private static Players.Followed followed(String id, String name, String team) {
        Players.Followed f = new Players.Followed();
        f.id = id;
        f.name = name;
        f.teamId = team;
        return f;
    }

    @Test
    public void aScorerIsFoundByNameOrBySurnameAtTheSameClub() {
        List<Players.Followed> l = Arrays.asList(followed("1", "Mohamed Salah", "364"), followed("2", "Martin Ødegaard", "359"));
        assertEquals("1", Players.match(l, "Mohamed Salah", "364").id);
        assertEquals("2", Players.match(l, "Martin Odegaard", "359").id);
        assertEquals("1", Players.match(l, "M. Salah", "364").id);
        // The same surname at another club is someone else.
        assertNull(Players.match(l, "Somebody Salah", "999"));
        assertNull(Players.match(l, "", "364"));
    }

    @Test
    public void namesCompareWithoutAccents() {
        assertEquals("martinodegaard", Players.key("Martin Ødegaard"));
        assertEquals("ilimanndiaye", Players.key("Iliman Ndiaye"));
        assertEquals("salah", Players.surname("Mohamed  Salah "));
        assertEquals("", Players.key(null));
    }

    private static SportsData.Fixture game(int us, int them, boolean home) {
        SportsData.Fixture f = new SportsData.Fixture();
        f.us = us;
        f.them = them;
        f.home = home;
        f.over = true;
        return f;
    }

    @Test
    public void theSeasonsPointsAndRecord() {
        List<SportsData.Fixture> played = Arrays.asList(game(2, 0, true), game(1, 1, false), game(0, 3, false), game(3, 2, true));
        assertArrayEquals(new int[]{3, 4, 4, 7}, Season.running(played));
        assertArrayEquals(new int[]{2, 1, 1}, Season.record(played, null));
        assertArrayEquals(new int[]{2, 0, 0}, Season.record(played, true));
        assertArrayEquals(new int[]{0, 1, 1}, Season.record(played, false));
        assertEquals("W2 D1 L1", Season.words(Season.record(played, null)));
    }

    @Test
    public void howHardAGameLooks() {
        assertEquals(2, Season.difficulty(1, 20));
        assertEquals(2, Season.difficulty(6, 20));
        assertEquals(1, Season.difficulty(10, 20));
        assertEquals(0, Season.difficulty(18, 20));
        assertEquals(1, Season.difficulty(null, 20));
    }

    @Test
    public void topScorersMostGoalsFirst() {
        Map<String, Integer> s = new HashMap<>();
        s.put("Beto", 3);
        s.put("Iliman Ndiaye", 5);
        s.put("Garner", 3);
        List<Map.Entry<String, Integer>> top = Season.top(s, 2);
        assertEquals(2, top.size());
        assertEquals("Iliman Ndiaye", top.get(0).getKey());
        assertEquals("Beto", top.get(1).getKey());
    }

    @Test
    public void theWeekendsTwoDays() {
        // Thursday 5 November 2026: Saturday 7 and Sunday 8.
        assertEquals(Arrays.asList(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)), WeekendPlanner.weekend(LocalDate.of(2026, 11, 5)));
        // On a Sunday: that day and the next Saturday.
        assertEquals(Arrays.asList(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)), WeekendPlanner.weekend(LocalDate.of(2026, 11, 8)));
    }

    private static WeekendPlanner.Plan plan(String tv, long at) {
        WeekendPlanner.Plan p = new WeekendPlanner.Plan();
        p.tv = tv;
        p.at = at;
        return p;
    }

    @Test
    public void twoGamesOnOneTvAtOnceClash() {
        long three = 15 * 3_600_000L;
        assertTrue(WeekendPlanner.clash(plan("Living room", three), plan("Living room", three + 3_600_000L)));
        assertFalse(WeekendPlanner.clash(plan("Living room", three), plan("Bedroom", three)));
        assertFalse(WeekendPlanner.clash(plan("Living room", three), plan("Living room", three + 2 * 3_600_000L)));
        assertFalse(WeekendPlanner.clash(plan("", three), plan("", three)));
    }

    @Test
    public void okStepsAGameThroughTheTvsAndNone() {
        List<String> tvs = Arrays.asList("Living room", "Bedroom");
        assertEquals("Living room", WeekendPlanner.next(tvs, ""));
        assertEquals("Bedroom", WeekendPlanner.next(tvs, "Living room"));
        assertEquals("", WeekendPlanner.next(tvs, "Bedroom"));
        assertEquals("", WeekendPlanner.next(Collections.emptyList(), ""));
    }

    @Test
    public void aPlannedGameGoesOnJustBeforeKickOff() {
        long ko = 1_000_000_000L;
        assertTrue(WeekendPlanner.dueNow(ko, ko - 60_000L));
        assertTrue(WeekendPlanner.dueNow(ko, ko + 10 * 60_000L));
        assertFalse(WeekendPlanner.dueNow(ko, ko - 5 * 60_000L));
        assertFalse(WeekendPlanner.dueNow(ko, ko + 40 * 60_000L));
    }

    @Test
    public void seeItAgainGoesBackToJustBeforeTheGoal() {
        assertEquals(2, Replay.steps(0));
        assertEquals(3, Replay.steps(5_000L));
        assertEquals(18, Replay.steps(10 * 60_000L));
        assertEquals("input keyevent 89 89", Replay.command(89, 2));
    }

    private static ScoresTicker.Game live(String id, int state, boolean half) {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = id;
        g.state = state;
        g.halftime = half;
        return g;
    }

    @Test
    public void theHalfTimeHopOffersGamesStillPlaying() {
        ScoresTicker.Game mine = live("m", 0, true);
        List<ScoresTicker.Game> all = new ArrayList<>(Arrays.asList(mine, live("a", 0, false), live("b", 0, true), live("c", 1, false),
                live("d", 0, false), live("e", 0, false), live("f", 0, false)));
        List<ScoresTicker.Game> out = HalfTimeHop.choices(mine, all);
        assertEquals(3, out.size());
        assertEquals("a", out.get(0).id);
        assertEquals("d", out.get(1).id);
    }

    @Test
    public void squadsGoKeepersFirst() {
        assertTrue(PlayersPanel.order("G") < PlayersPanel.order("D"));
        assertTrue(PlayersPanel.order("M") < PlayersPanel.order("F"));
        assertEquals("Forward", PlayersPanel.position("F"));
        assertEquals("", PlayersPanel.position(""));
    }

    @Test
    public void espnScoresAndTimesAreRead() {
        assertEquals(2, SportsData.score(2));
        assertEquals(3, SportsData.score("3"));
        assertEquals(-1, SportsData.score(null));
        assertEquals(1_794_150_000_000L, SportsData.time("2026-11-08T15:00Z"));
        assertEquals(0, SportsData.time(""));
        assertEquals(0xFF0606FA, SportsData.colour("0606fa"));
        assertEquals(0, SportsData.colour("nope"));
    }
}
