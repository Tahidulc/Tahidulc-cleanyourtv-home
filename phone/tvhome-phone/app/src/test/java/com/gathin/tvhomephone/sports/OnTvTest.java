package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Live sport in Sky's guide: which sport a listing is, its title tidied, one event on several channels kept once. */
public class OnTvTest {
    @Test public void listingsSayTheirSport() {
        assertEquals(OnTv.TENNIS, OnTv.sportOf("ATP Finals: Alcaraz v Tiafoe"));
        assertEquals(OnTv.CRICKET, OnTv.sportOf("1st Test Day 2: SA v BAN"));
        assertEquals(OnTv.CRICKET, OnTv.sportOf("WBBL Sydney.T v Melbourne.S"));
        assertEquals(OnTv.DARTS, OnTv.sportOf("Grand Slam Of Darts"));
        assertEquals(OnTv.NFL, OnTv.sportOf("NFL 49ers @ Cowboys"));
        assertEquals(OnTv.NBA, OnTv.sportOf("NBA: Magic @ Celtics"));
        assertEquals(OnTv.NHL, OnTv.sportOf("NHL: Calgary at Anaheim"));
        assertEquals(OnTv.F1, OnTv.sportOf("Las Vegas F1 GP: Qualifying"));
        assertEquals(OnTv.UFC, OnTv.sportOf("UFC Fight Night: Prochazka vs. Stirling"));
        assertEquals(OnTv.BOXING, OnTv.sportOf("Boxing: Smith v Jones"));
        assertEquals(OnTv.GOLF, OnTv.sportOf("PGA Tour Champions Golf"));
        assertEquals(OnTv.FOOTBALL, OnTv.sportOf("SNF: Newcastle v Arsenal"));
        // Shows about sport, and what isn't sport, are no events.
        assertNull(OnTv.sportOf("NFL Fantasy"));
        assertNull(OnTv.sportOf("NFL The Insiders"));
        assertNull(OnTv.sportOf("Sky Sports FC"));
        assertNull(OnTv.sportOf("at the Apollo"));
    }

    @Test public void titlesTidied() {
        assertEquals("NFL on 5: San Francisco 49ers @ Dallas Cowboys", OnTv.clean("Live: NFL on 5: San Francisco 49ers @ Dallas Cowboys"));
        assertEquals("ATP Finals: Medvedev v Cobolli", OnTv.clean("Live ATP Finals:Medvedev v Cobolli"));
        assertEquals("Grand Slam Of Darts", OnTv.clean("Live Grand Slam Of Darts"));
    }

    private static JSONObject event(String title, long st, long d) throws Exception {
        return new JSONObject().put("t", title).put("st", st).put("d", d);
    }

    @Test public void oneEventOnSeveralChannelsKeptOnceItsOwnChannelFirst() throws Exception {
        long st = 1_794_830_400L;
        JSONObject root = new JSONObject().put("schedule", new JSONArray()
                .put(new JSONObject().put("sid", "4002").put("events", new JSONArray()
                        .put(event("Live ATP Finals: Alcaraz v Tiafoe", st, 10800))
                        .put(event("Sky Sports News at Ten", st + 20000, 3600))))
                .put(new JSONObject().put("sid", "1284").put("events", new JSONArray()
                        .put(event("Live ATP Finals: Alcaraz v Tiafoe", st, 10800))
                        .put(event("ATP Finals Highlights", st + 20000, 1800))))
                .put(new JSONObject().put("sid", "9999").put("events", new JSONArray()
                        .put(event("Live Unknown Channel Darts", st, 600)))));
        List<OnTv.Listing> all = OnTv.merge(OnTv.parse(root));
        assertEquals(1, all.size());
        OnTv.Listing l = all.get(0);
        assertEquals("ATP Finals: Alcaraz v Tiafoe", l.title);
        assertEquals(OnTv.TENNIS, l.sport);
        assertEquals(Arrays.asList("Sky Sports Tennis", "Sky Sports Main Event"), l.channels);
        assertEquals(st * 1000L, l.start);
        assertEquals((st + 10800) * 1000L, l.end);
    }

    @Test public void anEventFindsItsListing() {
        long t = 1_794_830_400_000L;
        OnTv.Listing nfl = new OnTv.Listing("NFL Chargers @ Ravens", OnTv.NFL, t - 10 * 60_000L, t + 3 * 3_600_000L);
        nfl.channels.add("Sky Sports NFL");
        OnTv.Listing darts = new OnTv.Listing("Grand Slam Of Darts", OnTv.DARTS, t, t + 3_600_000L);
        darts.channels.add("Sky Sports Main Event");
        List<OnTv.Listing> all = Arrays.asList(nfl, darts);
        assertEquals(nfl, OnTv.find(all, OnTv.NFL, t, 3 * 3_600_000L, "Chargers", "Ravens"));
        assertNull(OnTv.find(all, OnTv.NFL, t, 3 * 3_600_000L, "Chiefs", "Ravens"));
        // Days later: not this listing.
        assertNull(OnTv.find(all, OnTv.NFL, t + 3 * 86_400_000L, 3 * 3_600_000L, "Chargers"));
        // No time asked: any listing of the sport with the words.
        assertEquals(darts, OnTv.find(all, OnTv.DARTS, 0, 0, "darts"));
    }

    @Test public void logosForLeaguesThatHaveThem() {
        assertEquals(OnTv.LEAGUES + "nfl.png", OnTv.logo(OnTv.NFL, ""));
        assertEquals(MoreSports.DP_WORLD_LOGO, OnTv.logo(OnTv.GOLF, "DP World Tour Championship"));
        assertNull(OnTv.logo(OnTv.GOLF, "LPGA Tour Golf"));
        assertNull(OnTv.logo(OnTv.DARTS, "Grand Slam Of Darts"));
    }
}
