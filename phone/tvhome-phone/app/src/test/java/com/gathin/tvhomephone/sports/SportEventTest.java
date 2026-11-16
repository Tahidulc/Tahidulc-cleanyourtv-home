package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.broadcast.SportBoard;

import org.junit.Test;

import java.util.Arrays;

/** The other sports' events, made from the Sports page's pieces and kept in an Intent. */
public class SportEventTest {
    @Test public void aTeamGameFindsBothTeamsByEitherName() {
        MoreSports.Match m = new MoreSports.Match();
        m.id = "77";
        m.home = "Harbour City Herons";
        m.homeShort = "Herons";
        m.away = "Westfield Wolves";
        m.awayShort = "Wolves";
        m.start = 1_000_000L;
        m.channel = "tv1 Sports NFL";
        SportEvent e = SportEvent.game(m, OnTv.NFL);
        assertEquals("Westfield Wolves at Harbour City Herons", e.title);
        assertEquals("football/nfl", e.espn);
        assertEquals("77", e.espnId);
        assertEquals(Arrays.asList("tv1 Sports NFL"), e.channels);
        assertArrayEquals(new String[]{"Harbour City Herons", "Herons"}, e.finds.get(0)[0]);
        assertArrayEquals(new String[]{"Westfield Wolves", "Wolves"}, e.finds.get(0)[1]);
        assertEquals("basketball/nba", SportEvent.game(m, OnTv.NBA).espn);
    }

    @Test public void aTennisMatchByItsPlayersAndTheirScores() {
        SportBoard s = new SportBoard();
        s.sport = SportBoard.TENNIS;
        s.event = "Lakeside Open";
        s.stage = "Final";
        s.state = "post";
        s.a.name = "Pim Vosloo";
        s.b.name = "Arno Kettering";
        s.a.games = new int[]{6, 7};
        s.b.games = new int[]{4, 5};
        SportEvent e = SportEvent.tennis(s, "ATP", null);
        assertEquals("Pim Vosloo v Arno Kettering", e.title);
        assertEquals("Lakeside Open · Final", e.subtitle);
        assertEquals("tennis/atp", e.espn);
        assertEquals("6  7", e.homeScore);
        assertEquals("4  5", e.awayScore);
        assertArrayEquals(new String[]{"Pim Vosloo", "Vosloo"}, e.finds.get(0)[0]);
    }

    @Test public void aNumberedCardIsFoundByItsNumberToo() {
        SportBoard main = new SportBoard();
        main.event = "UFC 412: Delacour vs. Brann";
        main.a.name = "Rafe Delacour";
        main.b.name = "Tomas Brann";
        main.start = 5_000_000L;
        SportBoard other = new SportBoard();
        other.a.name = "Ivo Marsh";
        other.b.name = "Lee Okafor";
        other.stage = "Lightweight";
        other.start = 4_000_000L;
        SportEvent e = SportEvent.fightNight(Arrays.asList(main, other), "tv1 Sports");
        assertEquals(4_000_000L, e.start);
        assertEquals(2, e.finds.size());
        assertArrayEquals(new String[]{"UFC 412"}, e.finds.get(1)[0]);
        assertEquals("Ivo Marsh v Lee Okafor", e.lines.get(0)[0]);
        assertEquals("The card", e.linesHead);
    }

    @Test public void wordsMadePlain() {
        assertEquals("Port Albany", SportEvent.gpPlace("Port Albany Grand Prix"));
        assertEquals("Port Albany", SportEvent.gpPlace("Formula 1 Port Albany GP"));
        assertEquals("Austin Championship", SportEvent.tournament("Austin Championship pres. by Lakeside Solutions"));
        assertEquals("Pinewood Classic", SportEvent.tournament("The Pinewood Classic"));
        assertEquals("Premiership Rugby: Kingsmoor v Ashcombe", SportEvent.plainTitle("Live Premiership Rugby: Kingsmoor v Ashcombe"));
        assertArrayEquals(new String[]{"Kingsmoor", "Ashcombe"}, SportEvent.sides("Premiership Rugby: Kingsmoor v Ashcombe"));
        assertArrayEquals(new String[]{"Harlow", "Upton Vale"}, SportEvent.sides("Harlow vs Upton Vale (Round 3)"));
        assertNull(SportEvent.sides("Grand Slam of Darts"));
        assertEquals("Vosloo", SportEvent.surname("P. Vosloo", "Pim Vosloo"));
        assertEquals("Kettering", SportEvent.surname("", "Arno Kettering"));
    }

    @Test public void aListingFindsItsSidesOrItsTitle() {
        OnTv.Listing l = new OnTv.Listing("Live Premiership Rugby: Kingsmoor v Ashcombe", OnTv.RUGBY, 1_000L, 2_000L);
        l.channels.add("tv1 Sports Action");
        SportEvent e = SportEvent.listing(l);
        assertEquals("Premiership Rugby: Kingsmoor v Ashcombe", e.title);
        assertArrayEquals(new String[]{"Kingsmoor"}, e.finds.get(0)[0]);
        assertArrayEquals(new String[]{"Ashcombe"}, e.finds.get(0)[1]);
        SportEvent darts = SportEvent.listing(new OnTv.Listing("Live Grand Slam of Darts", OnTv.DARTS, 1_000L, 2_000L));
        assertArrayEquals(new String[]{"Grand Slam of Darts"}, darts.finds.get(0)[0]);
    }

    @Test public void aGolfLeaderboardReadsItsOwnTour() {
        assertEquals("golf/eur", SportEvent.golfLeague(MoreSports.DP_WORLD_LOGO, ""));
        assertEquals("golf/eur", SportEvent.golfLeague("", "DP World Tour"));
        assertEquals("golf/pga", SportEvent.golfLeague(MoreSports.PGA_LOGO, "PGA TOUR"));
        assertEquals("golf/pga", SportEvent.golfLeague(null, null));
    }

    @Test public void keptInAnIntentWhole() {
        SportEvent e = new SportEvent();
        e.sport = OnTv.F1;
        e.title = "Port Albany Grand Prix";
        e.subtitle = "Round 21";
        e.state = "in";
        e.start = 7L;
        e.end = 9L;
        e.espn = "racing/f1";
        e.channels.add("tv1 Sports F1");
        e.finds.add(new String[][]{{"Port Albany"}, {"F1", "Formula 1"}});
        e.lines.add(new String[]{"Race", "Sun 15:00"});
        e.linesHead = "The weekend";
        SportEvent back = SportEvent.from(e.json());
        assertEquals(e.title, back.title);
        assertEquals(e.subtitle, back.subtitle);
        assertTrue(back.live());
        assertEquals(7L, back.start);
        assertEquals(9L, back.end);
        assertEquals(e.channels, back.channels);
        assertArrayEquals(e.finds.get(0)[1], back.finds.get(0)[1]);
        assertArrayEquals(e.lines.get(0), back.lines.get(0));
        assertEquals("The weekend", back.linesHead);
    }
}
