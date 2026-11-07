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
        // Stacked is drawn taller in its own corner (its clubs one above the other); the rest as they were.
        assertTrue(OverlayBug.Style.STACKED.tall > 1.5f);
        for (OverlayBug.Style st : OverlayBug.Style.values()) if (st != OverlayBug.Style.STACKED) assertEquals(1f, st.tall, 0f);
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

    @Test public void eachSidesShareOfAStatFillsItsBar() {
        assertEquals(0.58f, BoardStats.share("58%", "42%"), 0.001f);
        assertEquals(0.7f, BoardStats.share("7", "3"), 0.001f);
        assertEquals(0.5f, BoardStats.share("0", "0"), 0.001f);
        assertEquals(0.5f, BoardStats.share("", null), 0.001f);
        assertEquals(1f, BoardStats.share("2", "0"), 0.001f);
    }
}
