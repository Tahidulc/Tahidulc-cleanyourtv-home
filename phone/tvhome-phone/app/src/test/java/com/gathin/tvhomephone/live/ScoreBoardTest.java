package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;

import java.util.List;

/** The scoreboard's words and its alerts' drops; breaking news read from a feed. */
public class ScoreBoardTest {
    @Test public void aGoalDropsWithItsChipAndWithoutItsHead() {
        Events.Alert a = new Events.Alert("goal", "k1", "GOAL · Everton 1–0 Brentford", "Beto 23' for Everton\nAssist: Doucouré", null);
        ScoreBoard.Drop d = ScoreBoard.drop(a);
        assertEquals("GOAL", d.chip);
        assertEquals("Everton 1–0 Brentford", d.title);
        assertEquals("Beto 23' for Everton", d.line);
    }

    @Test public void alertsGoUnderTheirSwitches() {
        assertEquals(ScoreBoard.FOOTBALL, ScoreBoard.group("goal"));
        assertEquals(ScoreBoard.SPORT, ScoreBoard.group("f1live"));
        assertEquals(ScoreBoard.NEWS, ScoreBoard.group("news"));
        assertEquals(ScoreBoard.NEWS, ScoreBoard.group("weather"));
    }

    @Test public void clubsAreThreeLetters() {
        assertEquals("EVE", ScoreBoard.code("EVE", "Everton"));
        assertEquals("BRE", ScoreBoard.code("", "Brentford"));
        assertEquals("MAN", ScoreBoard.code(null, "Man United"));
    }

    @Test public void theLookFollowsTheGame() {
        Game g = new Game();
        g.home = "EVE";
        g.away = "BRE";
        g.state = Game.LIVE;
        g.homeScore = 1;
        OverlayBug.Look l = ScoreBoard.look(g, new String[]{"45:00", "+2"});
        assertEquals("1–0", l.score);
        assertEquals("45:00", l.clock);
        assertEquals("+2", l.added);
        assertTrue(l.live);
        g.halftime = true;
        assertEquals("HT", ScoreBoard.look(g, null).clock);
        g.state = Game.POST;
        assertEquals("FT", ScoreBoard.look(g, null).clock);
    }

    @Test public void f1ChannelsAreKnownByName() {
        assertTrue(ScoreBoard.f1Channel(new Channel("k", "UK: Sky Sports F1 HD", "", "", "", "", "", "", 0)));
        assertFalse(ScoreBoard.f1Channel(new Channel("k", "Sky Sports Main Event", "", "", "", "", "", "", 0)));
    }

    @Test public void clubColoursStayVisibleOnNavy() {
        assertEquals(0xFFFFFF00, OverlayBug.colour("ffff00", ""));
        // Black with a yellow second colour: the yellow.
        assertEquals(0xFFFFCC00, OverlayBug.colour("000000", "ffcc00"));
        assertEquals(0xFFFFFFFF, OverlayBug.colour("", ""));
    }

    @Test public void theNewsFeedIsRead() {
        String rss = "<rss><channel><title>BBC News</title>"
                + "<item><title><![CDATA[Big story breaks]]></title><description><![CDATA[What happened &amp; why]]></description>"
                + "<link>https://example.org/1</link><pubDate>Thu, 05 Nov 2026 18:00:00 GMT</pubDate></item>"
                + "<item><title>Quiz of the week</title><link>https://example.org/2</link></item></channel></rss>";
        List<BreakingNews.Story> s = BreakingNews.parse(rss);
        assertEquals(2, s.size());
        assertEquals("Big story breaks", s.get(0).title);
        assertEquals("What happened & why", s.get(0).line);
        long at = s.get(0).at;
        assertTrue(BreakingNews.breaking(s.get(0), at + 10 * 60_000L));
        assertFalse(BreakingNews.breaking(s.get(0), at + 60 * 60_000L));
        assertFalse(BreakingNews.breaking(new BreakingNews.Story("Quiz of the week", "", "", at), at));
        // Its picture, asked for large; none without one, and only over https.
        assertEquals("", s.get(0).picture);
        assertEquals("https://ichef.example.org/ace/standard/976/a/b.jpg", BreakingNews.picture(
                "<title>x</title><media:thumbnail width=\"240\" height=\"135\" url=\"https://ichef.example.org/ace/standard/240/a/b.jpg\"/>"));
        assertEquals("", BreakingNews.picture("<media:thumbnail url=\"http://plain.example.org/a.jpg\"/>"));
        assertEquals("", BreakingNews.picture(null));
    }

    @Test
    public void theLooksIncludeCrestsAndTimeAndStacked() {
        assertEquals(OverlayBug.Style.CRESTS, OverlayBug.Style.of("CRESTS"));
        assertEquals(OverlayBug.Style.STACKED, OverlayBug.Style.of("STACKED"));
        assertEquals(OverlayBug.Style.CLASSIC, OverlayBug.Style.of("nonsense"));
        assertEquals("Crests and time", OverlayBug.Style.CRESTS.label);
        // Stacked is drawn taller in its own corner (its clubs one above the other), Card too (its big crests); the rest as they were.
        assertTrue(OverlayBug.Style.STACKED.tall > 1.5f);
        assertTrue(OverlayBug.Style.CARD.tall > 1.5f);
        for (OverlayBug.Style st : OverlayBug.Style.values())
            if (st != OverlayBug.Style.STACKED && st != OverlayBug.Style.CARD) assertEquals(1f, st.tall, 0f);
    }

    @Test
    public void theCardLookIsTheLastAndLightsOnItsWhite() {
        OverlayBug.Style[] all = OverlayBug.Style.values();
        assertEquals(OverlayBug.Style.CARD, all[all.length - 2]);
        // 4.26: Frosted glass after it.
        assertEquals(OverlayBug.Style.FROSTED, all[all.length - 1]);
        assertEquals(OverlayBug.Style.CARD, OverlayBug.Style.of("CARD"));
        assertEquals("Card", OverlayBug.Style.CARD.label);
        // Its white over its blue: a light blend stays light, the club's colour laid fully over is the club's.
        assertEquals(0xFF000000 | OverlayBug.CARD_TOP, OverlayBug.blend(OverlayBug.CARD_TOP, 0xFFE0213A, 0f));
        assertEquals(0xFFE0213A, OverlayBug.blend(OverlayBug.CARD_TOP, 0xFFE0213A, 1f));
    }

    @Test public void itComesInAsABroadcastersDoes() {
        float w = 300, h = 30, cap = 30;
        // Nothing at first; its first block opening out of a line across its middle.
        assertEquals(0f, OverlayBug.shownPart(0f, w, h, cap)[2], 0.01f);
        float[] opening = OverlayBug.shownPart(0.1f, w, h, cap);
        assertEquals(cap, opening[2], 0.01f);
        assertTrue(opening[1] > 0 && opening[3] < h && opening[1] < h / 2 && opening[3] > h / 2);
        // The block open, then the rest wiping out of it to the right, all there at the end.
        float[] block = OverlayBug.shownPart(0.3f, w, h, cap);
        assertEquals(0f, block[1], 0.01f);
        assertEquals(h, block[3], 0.01f);
        assertEquals(cap, block[2], 0.01f);
        float half = OverlayBug.shownPart(0.65f, w, h, cap)[2];
        assertTrue(half > cap && half < w);
        assertEquals(w, OverlayBug.shownPart(1f, w, h, cap)[2], 0.01f);
        // Always more as it goes on.
        float last = -1;
        for (int i = 0; i <= 20; i++) {
            float[] p = OverlayBug.shownPart(i / 20f, w, h, cap);
            float area = (p[2] - p[0]) * (p[3] - p[1]);
            assertTrue(area >= last);
            last = area;
        }
    }

    @Test public void aGoalSweepsAcrossFromTheScorersSideAndOnOut() {
        // Nothing before the band; in from the left for the home side, held across it all, then on out to the right.
        float[] before = OverlayBug.goalBand(OverlayBug.BAND_IN - 1, true);
        assertEquals(0f, before[1] - before[0], 0f);
        float[] coming = OverlayBug.goalBand(OverlayBug.BAND_IN + OverlayBug.BAND_MS / 3, true);
        assertEquals(0f, coming[0], 0.001f);
        assertTrue(coming[1] > 0f && coming[1] < 1f);
        float[] held = OverlayBug.goalBand(2000, true);
        assertEquals(0f, held[0], 0.001f);
        assertEquals(1f, held[1], 0.001f);
        float[] going = OverlayBug.goalBand(OverlayBug.BAND_OUT + OverlayBug.BAND_MS / 3, true);
        assertTrue(going[0] > 0f && going[0] < 1f);
        assertEquals(1f, going[1], 0.001f);
        float[] gone = OverlayBug.goalBand(OverlayBug.BAND_OUT + OverlayBug.BAND_MS, true);
        assertEquals(0f, gone[1] - gone[0], 0f);
        // The away side's from the right, out to the left.
        float[] away = OverlayBug.goalBand(OverlayBug.BAND_IN + OverlayBug.BAND_MS / 3, false);
        assertEquals(1f, away[1], 0.001f);
        assertTrue(away[0] > 0f && away[0] < 1f);
        float[] awayGoing = OverlayBug.goalBand(OverlayBug.BAND_OUT + OverlayBug.BAND_MS / 3, false);
        assertEquals(0f, awayGoing[0], 0.001f);
        assertTrue(awayGoing[1] > 0f && awayGoing[1] < 1f);
        // Always more of it coming in, less going out.
        float last = -1;
        for (long t = OverlayBug.BAND_IN; t <= OverlayBug.BAND_IN + OverlayBug.BAND_MS; t += 25) {
            float[] b = OverlayBug.goalBand(t, true);
            assertTrue(b[1] - b[0] >= last);
            last = b[1] - b[0];
        }
    }

    @Test public void theNewScoreGlowsOnceTheBandHasGone() {
        long from = OverlayBug.BAND_OUT + OverlayBug.BAND_MS;
        assertEquals(0f, OverlayBug.glow(from - 1), 0f);
        assertEquals(1f, OverlayBug.glow(from), 0.001f);
        assertTrue(OverlayBug.glow(from + OverlayBug.GLOW_MS / 2) < 1f);
        assertEquals(0f, OverlayBug.glow(OverlayBug.GOAL_MS), 0f);
        assertEquals(OverlayBug.GOAL_MS, from + OverlayBug.GLOW_MS);
    }

    private static Game leedsSpurs() {
        Game g = new Game();
        g.homeName = "Leeds";
        g.awayName = "Tottenham";
        g.homeColor = "1d428a";
        g.awayColor = "132257";
        g.awayAlt = "ffffff";
        return g;
    }

    @Test public void theLineUpsDropWithBothElevens() {
        Events.Alert a = new Events.Alert("kickoff", "L|1", "LINE-UPS · Leeds v Tottenham · 15:00",
                "Leeds (4-3-3): Meslier, Bogle, Rodon\nTottenham (4-2-3-1): Vicario, Porro, Romero", leedsSpurs());
        ScoreBoard.Drop d = ScoreBoard.drop(a);
        assertTrue(d.lineups);
        assertEquals("LINE-UPS", d.chip);
        assertEquals("Leeds v Tottenham · 15:00", d.title);
        assertEquals("Leeds (4-3-3): Meslier, Bogle, Rodon", d.line);
        assertEquals("Tottenham (4-2-3-1): Vicario, Porro, Romero", d.line2);
        assertEquals(0, d.side);
        assertEquals(0xFF1D428A, d.homeColour);
        // Kick-off itself is no line-up.
        ScoreBoard.Drop k = ScoreBoard.drop(new Events.Alert("kickoff", "K|1", "KICK-OFF · Leeds v Tottenham", "Premier League", leedsSpurs()));
        assertFalse(k.lineups);
        assertEquals("KICK-OFF", k.chip);
        assertEquals("", k.line2);
    }

    @Test public void aDropKnowsWhichSideItIsAbout() {
        Game g = leedsSpurs();
        assertEquals(1, ScoreBoard.drop(new Events.Alert("goal", "g1", "GOAL · Leeds 1–0 Tottenham", "Calvert-Lewin 23' for Leeds", g)).side);
        assertEquals(2, ScoreBoard.drop(new Events.Alert("goal", "g2", "GOAL · Leeds 1–1 Tottenham", "Son 51' (pen) for Tottenham\nAssist: x", g)).side);
        assertEquals(2, ScoreBoard.drop(new Events.Alert("red", "r1", "RED CARD · Tottenham", "Romero 70' · Leeds 1–1 Tottenham", g)).side);
        assertEquals(0, ScoreBoard.drop(new Events.Alert("ht", "H|1", "HALF TIME · Leeds 1–1 Tottenham", "", g)).side);
        // No game: no clubs' colours down its edge, the kind's instead.
        ScoreBoard.Drop news = ScoreBoard.drop(new Events.Alert("news", "n1", "BREAKING NEWS · Something", "", null));
        assertEquals(0, news.homeColour);
        assertEquals(0, news.awayColour);
        // The kinds with no colour of their own take the board's.
        assertEquals(ScoreBoard.PLAIN, ScoreBoard.colour("ht"));
        assertTrue(ScoreBoard.colour("goal") != ScoreBoard.PLAIN);
    }

    @Test public void clubColoursStayReadableOnTheStrip() {
        // Spurs' navy on a dark strip: lightened; white on Light's: darkened; a bright red either way: as it is.
        int navy = BoardDrop.legible(0xFF132257, false);
        assertTrue(lum(navy) > lum(0xFF132257) + 60);
        int white = BoardDrop.legible(0xFFFFFFFF, true);
        assertTrue(lum(white) < 150);
        assertEquals(0xFFE5333B, BoardDrop.legible(0xFFE5333B, false));
        assertEquals(0xFFE5333B, BoardDrop.legible(0xFFE5333B, true));
    }

    private static int lum(int c) {
        return (((c >> 16) & 0xFF) * 299 + ((c >> 8) & 0xFF) * 587 + (c & 0xFF) * 114) / 1000;
    }

    @Test public void eachSidesShareOfAStatFillsItsBar() {
        assertEquals(0.58f, BoardStats.share("58%", "42%"), 0.001f);
        assertEquals(0.7f, BoardStats.share("7", "3"), 0.001f);
        assertEquals(0.5f, BoardStats.share("0", "0"), 0.001f);
        assertEquals(0.5f, BoardStats.share("", null), 0.001f);
        assertEquals(1f, BoardStats.share("2", "0"), 0.001f);
    }

    // ---- 4.18: out sideways, a few stats moments, the clock to full time, the scorer, Whitebrook Road ----------------

    @Test public void theStatsComeAFewTimesAGame() {
        assertEquals("pre", ScoreBoard.statsSlot(true, 8 * 60_000L, Game.PRE, false, -1));
        assertEquals(null, ScoreBoard.statsSlot(true, 15 * 60_000L, Game.PRE, false, -1));
        assertEquals(null, ScoreBoard.statsSlot(false, 0, Game.LIVE, false, 10));
        assertEquals("h1", ScoreBoard.statsSlot(false, 0, Game.LIVE, false, 30));
        assertEquals("ht", ScoreBoard.statsSlot(false, 0, Game.LIVE, true, 45));
        assertEquals("h2", ScoreBoard.statsSlot(false, 0, Game.LIVE, false, 70));
        assertEquals(null, ScoreBoard.statsSlot(false, 0, Game.LIVE, false, 88));
        assertEquals("ft", ScoreBoard.statsSlot(false, 0, Game.POST, false, -1));
        java.util.Map<String, String[]> by = new java.util.HashMap<>();
        by.put("Possession", new String[]{"Possession", "58%", "42%"});
        by.put("Shots", new String[]{"Shots", "9", "4"});
        by.put("Corners", new String[]{"Corners", "5", "2"});
        java.util.List<String[]> h1 = ScoreBoard.statsFor("h1", by);
        assertEquals(3, h1.size());
        assertEquals("Corners", h1.get(2)[0]);
        by.put("Player of the match", new String[]{"Player of the match", "Saka (ARS) · 2 goals", "", "one"});
        assertEquals("Player of the match", ScoreBoard.statsFor("h2", by).get(1)[0]);
        assertTrue(ScoreBoard.statsFor("pre", new java.util.HashMap<>()).isEmpty());
        assertEquals(67, ScoreBoard.minuteOfClock("67:23"));
        assertEquals(-1, ScoreBoard.minuteOfClock("HT"));
    }

    @Test public void theSummaryIsReadOftenNearTheEndsOfHalves() {
        assertEquals(ScoreBoard.ENDS_READ_MS, ScoreBoard.readEvery(44));
        assertEquals(ScoreBoard.ENDS_READ_MS, ScoreBoard.readEvery(93));
        assertEquals(ScoreBoard.STATS_READ_MS, ScoreBoard.readEvery(60));
        assertEquals(ScoreBoard.STATS_READ_MS, ScoreBoard.readEvery(-1));
    }

    @Test public void theAddedTimeShowsFromTheHalfsLastMinuteOnceKnown() {
        int[] put = {2, 5, -1, -1};
        assertEquals("", ScoreBoard.addedBox(1, 43, put, ""));
        assertEquals("+2", ScoreBoard.addedBox(1, 44, put, ""));
        assertEquals("+5", ScoreBoard.addedBox(2, 91, put, ""));
        // Not put up yet: no box; the channel's own shown as it has it.
        assertEquals("", ScoreBoard.addedBox(2, 91, new int[]{-1, -1, -1, -1}, ""));
        assertEquals("+4", ScoreBoard.addedBox(2, 90, put, "+4"));
    }

    @Test public void fullTimeShowsALittleUnderAMinute() {
        assertTrue(!ScoreBoard.ftGone(40_000L, false));
        assertTrue(ScoreBoard.ftGone(51_000L, false));
        // An alert still out: it waits, but never past a minute.
        assertTrue(!ScoreBoard.ftGone(51_000L, true));
        assertTrue(ScoreBoard.ftGone(61_000L, true));
    }

    @Test public void eachGoalIsCaptionedOnceWithItsScorer() {
        Game g = leedsSpurs();
        g.homeScore = 1;
        g.goals.add(new String[]{"G|1|23'|Dan James", "Dan James", "23'", "home", ""});
        g.goals.add(new String[]{"G|1|67'|Joel Piroe", "Joel Piroe", "67'", "home", "pen"});
        String[] last = ScoreBoard.lastGoal(g, 1);
        assertEquals("Joel Piroe", last[1]);
        assertEquals(null, ScoreBoard.lastGoal(g, 2));
        ScoreBoard.Drop d = ScoreBoard.goalDrop(g, last);
        assertEquals("GOAL", d.chip);
        assertEquals("Joel Piroe 67' (pen)", d.line);
        assertEquals("G|1|67'|Joel Piroe", d.key);
        assertTrue(d.own);
        assertEquals(1, d.side);
        // The goal's alert is about the same goal: its key the goal's own.
        Events.Alert a = new Events.Alert("goal", "G|1|67'|Joel Piroe", "GOAL · Leeds 1–0 Tottenham", "Joel Piroe 67' (pen) for Leeds", g);
        assertEquals("Joel Piroe", ScoreBoard.player(a)[1]);
        assertEquals(" (og)", ScoreBoard.kindWords("og"));
    }

    @Test public void whatSlidesOutGoesWhereThereIsRoom() {
        assertTrue(!ScoreBoard.outLeft(500, 900, 30));
        assertTrue(ScoreBoard.outLeft(900, 100, 30));
        assertTrue(!ScoreBoard.outLeft(80, 100, 30));
        assertEquals(2 * BoardSide.SLIDE_MS + 3 * BoardStats.EACH_MS + 2 * BoardStats.TURN_MS, BoardStats.showFor(3));
    }

    @Test public void theStatsAndFactsComeFromTheSummary() {
        com.gathin.tvhomephone.sports.Espn.Summary s = new com.gathin.tvhomephone.sports.Espn.Summary();
        s.homeId = "359";
        s.awayId = "368";
        s.stats.add(new String[]{"Possession", "58.3%", "41.7%"});
        s.stats.add(new String[]{"Shots", "9", "4"});
        s.homeNumbers.put("accuratePasses", "420");
        s.homeNumbers.put("totalPasses", "500");
        s.awayNumbers.put("accuratePasses", "300");
        s.awayNumbers.put("totalPasses", "400");
        s.homeLast.add(new String[]{"W"});
        s.homeLast.add(new String[]{"D"});
        s.awayLast.add(new String[]{"L"});
        s.table.add(new String[]{"359", "Arsenal", "2", "61", "29"});
        s.table.add(new String[]{"368", "Everton", "15", "30", "29"});
        s.winHome = "64%";
        s.winAway = "13%";
        java.util.Map<String, String[]> by = ScoreBoard.statsOf(s, Game.LIVE, "ARS", "EVE");
        assertEquals("58%", by.get("Possession")[1]);
        assertEquals("84%", by.get("Pass accuracy")[1]);
        assertEquals("WD", by.get("Form")[1]);
        assertEquals("2nd · 61 pts", by.get("League table")[1]);
        assertEquals("15th · 30 pts", by.get("League table")[2]);
        assertEquals("64%", by.get("Win chance")[1]);
        // Before kick-off: only the facts.
        java.util.Map<String, String[]> pre = ScoreBoard.statsOf(s, Game.PRE, "ARS", "EVE");
        assertEquals(null, pre.get("Possession"));
        assertTrue(pre.containsKey("Form"));
        assertEquals("21st", ScoreBoard.ordinal(21));
        assertEquals("12th", ScoreBoard.ordinal(12));
    }

    @Test public void theLogoCoverSaysWhitebrookRoad() {
        assertEquals("Whitebrook Road", LogoCover.NAME);
        assertTrue(LogoCover.Look.BROADCAST.broadcast());
        assertTrue(LogoCover.Look.BROADCAST_BOARD.broadcast());
        assertTrue(!LogoCover.Look.GLASS.broadcast());
        assertEquals("UHD", LogoCover.tagOf("UK: Sky Sports Premier League UHD"));
        assertEquals("HD", LogoCover.tagOf("Sky Sports Main Event HD"));
        assertEquals("", LogoCover.tagOf("Shdtv Sports"));
    }

    @Test public void theLineUpsCardHasEachSidesLinesThenThePitch() {
        java.util.List<String[]> lineup = new java.util.ArrayList<>();
        String[][] who = {{"1", "David Raya", "G", "Raya"}, {"4", "Ben White", "RB", "White"}, {"2", "William Saliba", "CD-R", "Saliba"},
                {"6", "Gabriel Magalhães", "CD-L", "Gabriel"}, {"33", "Riccardo Calafiori", "LB", "Calafiori"},
                {"8", "Martin Ødegaard", "AM", "Ødegaard"}, {"41", "Declan Rice", "CM-R", "Rice"}, {"23", "Mikel Merino", "CM-L", "Merino"},
                {"7", "Bukayo Saka", "RW", "Saka"}, {"29", "Kai Havertz", "CF", "Havertz"}, {"11", "Gabriel Martinelli", "LW", "Martinelli"}};
        for (int i = 0; i < who.length; i++) {
            String[] row = new String[13];
            java.util.Arrays.fill(row, "");
            row[com.gathin.tvhomephone.sports.Espn.P_NUMBER] = who[i][0];
            row[com.gathin.tvhomephone.sports.Espn.P_NAME] = who[i][1];
            row[com.gathin.tvhomephone.sports.Espn.P_POSITION] = who[i][2];
            row[com.gathin.tvhomephone.sports.Espn.P_STARTER] = "1";
            row[com.gathin.tvhomephone.sports.Espn.P_PLACE] = String.valueOf(i + 1);
            row[com.gathin.tvhomephone.sports.Espn.P_SHORT] = who[i][3];
            lineup.add(row);
        }
        java.util.List<java.util.List<BoardLineups.Player>> rows = BoardLineups.rows("4-3-3", lineup);
        assertTrue(rows.size() >= 3);
        assertEquals("Raya", rows.get(0).get(0).last);
        assertEquals("David", rows.get(0).get(0).first);
        int all = 0;
        for (java.util.List<BoardLineups.Player> r : rows) all += r.size();
        assertEquals(11, all);
        BoardLineups.Data d = new BoardLineups.Data();
        d.home.rows.addAll(rows);
        d.away.rows.addAll(rows);
        // Each side's keeper and defence, its midfield and attack, then the pitch: a little under half a minute.
        long ms = BoardLineups.showFor(d);
        assertTrue(ms > 15_000L && ms < 40_000L);
    }

    // ---- 4.26: frosted glass, the new moments, FotMob ----------------------------------------------------------------

    @Test
    public void theClockBeatsRedOnlyInAddedTime() {
        OverlayBug.Look l = new OverlayBug.Look();
        l.live = true;
        l.clock = "88:10";
        // A close game's last minutes: no beat any more.
        assertFalse(OverlayBug.pulses(l));
        // Into the half's added time: it beats, on every look.
        l.clock = "90:40";
        l.stoppage = true;
        assertTrue(OverlayBug.pulses(l));
        // Never on F1's board, nor once the game is over.
        l.f1 = true;
        assertFalse(OverlayBug.pulses(l));
        l.f1 = false;
        l.live = false;
        assertFalse(OverlayBug.pulses(l));
        float most = 0f;
        for (long t = 0; t < 1000; t += 10) most = Math.max(most, OverlayBug.heartbeat(t));
        assertTrue(most > 0.8f);
        assertEquals(OverlayBug.Style.FROSTED, OverlayBug.Style.of("FROSTED"));
    }

    @Test
    public void fotMobsFiguresComeAsTheBoardsStats() {
        FotMob.Match m = new FotMob.Match();
        m.homeTeamId = 9825;
        m.stats.put("expected_goals", new String[]{"1.26", "0.36"});
        m.stats.put("physical_metrics_distance_covered", new String[]{"118995", "114637"});
        m.homeTop = new String[]{"Martin Ødegaard", "8.5", "1"};
        m.awayTop = new String[]{"Lewie Coyle", "7.1", "2"};
        m.insights.add(new String[]{"Have scored 8 goals in their last 5 matches", "9825", ""});
        java.util.Map<String, String[]> by = FotMob.statsOf(m, "ARS", "HUL");
        assertEquals("1.26", by.get("Expected goals")[1]);
        assertEquals("119.0 km", by.get("Distance run")[1]);
        assertEquals("Ødegaard 8.5", by.get("Top rated")[1]);
        assertEquals("ARS have scored 8 goals in their last 5 matches", by.get("Insight")[1]);
        assertNull(by.get("Momentum"));
        assertEquals("Expected goals", ScoreBoard.statsFor("ft", by).get(0)[0]);
        assertTrue(FotMob.sameClub("Man Utd", "Manchester United"));
        assertFalse(FotMob.sameClub("Manchester City", "Manchester United"));
    }

    @Test
    public void aSubstitutionSaysWhoCameOnAndWhoWentOff() {
        Game g = new Game();
        g.id = "1";
        g.homeName = "Arsenal";
        g.awayName = "Hull City";
        ScoreBoard.Drop d = ScoreBoard.subDrop(g, new String[]{"Substitution", "69", "1", "Eberechi Eze", "9", "Christos Tzolis", "8"});
        assertEquals("SUBSTITUTION", d.chip);
        assertEquals("Arsenal", d.title);
        assertEquals("On Eberechi Eze · off Christos Tzolis · 69'", d.line);
        assertEquals(1, d.side);
    }

    @Test public void theStatsTestShowsTheGamesOwnElseOneOfEachKind() {
        // Nothing read yet: made-up ones, a bar, the form, two places and one line.
        List<String[]> made = ScoreBoard.testStats(new java.util.HashMap<>());
        assertEquals(5, made.size());
        java.util.Set<String> kinds = new java.util.HashSet<>();
        for (String[] s : made) kinds.add(s.length > 3 ? s[3] : "bar");
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("bar", "form", "text", "one")), kinds);
        // The game's own once read: half time's moment of them.
        java.util.Map<String, String[]> by = new java.util.HashMap<>();
        by.put("Possession", new String[]{"Possession", "61%", "39%"});
        by.put("Shots", new String[]{"Shots", "9", "4"});
        List<String[]> own = ScoreBoard.testStats(by);
        assertEquals(2, own.size());
        assertEquals("61%", own.get(0)[1]);
    }

    @Test public void theAddedTimeTestPutsUpFourInItsHalf() {
        OverlayBug.Look l = new OverlayBug.Look();
        l.clock = "67:23";
        ScoreBoard.addedTest(l, 0);
        assertEquals("90:00", l.clock);
        assertEquals("+4", l.added);
        assertTrue(l.stoppage);
        assertTrue(l.live);
        assertTrue(OverlayBug.pulses(l));
        OverlayBug.Look first = new OverlayBug.Look();
        first.clock = "31:05";
        ScoreBoard.addedTest(first, 75);
        assertEquals("46:15", first.clock);
        OverlayBug.Look ht = new OverlayBug.Look();
        ht.clock = "HT";
        ScoreBoard.addedTest(ht, 5);
        assertEquals("90:05", ht.clock);
    }

    @Test public void theBoardGoesAtHalfTimeAfterItsAlertAndComesBackForTheSecondHalf() {
        assertFalse(ScoreBoard.htGone(10_000L, false));
        assertTrue(ScoreBoard.htGone(ScoreBoard.HT_SHOWN_MS + 1_000L, false));
        // Something still out of it (the alert, the half's stats): it waits, a minute at most.
        assertFalse(ScoreBoard.htGone(ScoreBoard.HT_SHOWN_MS + 1_000L, true));
        assertTrue(ScoreBoard.htGone(ScoreBoard.HT_MOST_MS + 1_000L, true));
    }
}
