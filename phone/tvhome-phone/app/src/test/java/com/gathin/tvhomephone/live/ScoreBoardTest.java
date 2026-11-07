package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
        assertEquals(OverlayBug.Style.CARD, all[all.length - 1]);
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
}
