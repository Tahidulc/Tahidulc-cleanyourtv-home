package com.gathin.homefieldtv.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/**
 * An F1 driver's page's data, from invented answers in Jolpica's shapes: the championship with each driver's number,
 * nationality and birth date and his latest team; his season counted from his results (Grands Prix, podiums, wins);
 * the next weekend from its first session; formula1.com's pictures in the family of the team logos' addresses; and the
 * flag for a nationality.
 */
public class F1PagesTest {
    private static final String STANDINGS = "{\"MRData\":{\"StandingsTable\":{\"season\":\"2026\",\"StandingsLists\":[{\"season\":\"2026\","
            + "\"round\":\"20\",\"DriverStandings\":[{\"position\":\"1\",\"points\":\"312\",\"wins\":\"5\",\"Driver\":{\"driverId\":\"leo_marchetti\","
            + "\"permanentNumber\":\"16\",\"code\":\"MAR\",\"givenName\":\"Leo\",\"familyName\":\"Marchetti\",\"dateOfBirth\":\"1999-04-12\","
            + "\"nationality\":\"Italian\"},\"Constructors\":[{\"constructorId\":\"williams\",\"name\":\"Old Team\"},{\"constructorId\":\"rb\","
            + "\"name\":\"Aurora Racing\"}]},{\"position\":\"2\",\"points\":\"288\",\"Driver\":{\"driverId\":\"finn_halvorsen\","
            + "\"givenName\":\"Finn\",\"familyName\":\"Hålvorsen\",\"nationality\":\"Dutch\",\"dateOfBirth\":\"soon\"},\"Constructors\":[]}]}]}}}";

    @Test public void theChampionshipWithNumbersNationalityAndLatestTeam() throws Exception {
        List<F1.Standing> d = F1.drivers(new JSONObject(STANDINGS));
        assertEquals(2, d.size());
        F1.Standing a = d.get(0), b = d.get(1);
        assertEquals("1", a.position);
        assertEquals("Leo Marchetti", a.name);
        assertEquals("Leo", a.given);
        assertEquals("16", a.number);
        assertEquals("Italian", a.nationality);
        assertEquals("1999-04-12", a.born);
        assertEquals("leo_marchetti", a.driverId);
        assertEquals("rb", a.teamId);
        assertEquals("Aurora Racing", a.team);
        assertEquals("312", a.points);
        assertEquals(2026, a.season);
        // No number, no team and a birth date that isn't one: left empty, never made up.
        assertEquals("", b.number);
        assertEquals("", b.teamId);
        assertEquals("", b.born);
    }

    @Test public void aSeasonCountsHisGrandsPrixAndPodiums() throws Exception {
        String results = "{\"MRData\":{\"RaceTable\":{\"season\":\"2026\",\"Races\":["
                + "{\"round\":\"1\",\"Results\":[{\"position\":\"1\",\"status\":\"Finished\"}]},"
                + "{\"round\":\"2\",\"Results\":[{\"position\":\"16\",\"status\":\"Retired\"}]},"
                + "{\"round\":\"3\",\"Results\":[{\"position\":\"3\",\"status\":\"Finished\"}]},"
                + "{\"round\":\"4\",\"Results\":[{\"position\":\"4\",\"status\":\"Finished\"}]},"
                + "{\"round\":\"5\",\"Results\":[]}]}}}";
        F1.Season s = F1.season(new JSONObject(results));
        assertEquals(2026, s.year);
        assertEquals(4, s.races);
        assertEquals(2, s.podiums);
        assertEquals(1, s.wins);
        F1.Season none = F1.season(new JSONObject("{\"MRData\":{\"RaceTable\":{\"season\":\"2026\",\"Races\":[]}}}"));
        assertEquals(0, none.races);
        assertEquals(0, none.podiums);
    }

    @Test public void theNextWeekendStartsWithItsFirstSession() throws Exception {
        String next = "{\"MRData\":{\"RaceTable\":{\"Races\":[{\"round\":\"21\",\"raceName\":\"Coastal Grand Prix\",\"Circuit\":{\"circuitName\":"
                + "\"Seafront Circuit\"},\"date\":\"2026-11-22\",\"time\":\"14:00:00Z\",\"FirstPractice\":{\"date\":\"2026-11-20\",\"time\":\"11:30:00Z\"},"
                + "\"Qualifying\":{\"date\":\"2026-11-21\",\"time\":\"15:00:00Z\"}}]}}}";
        F1.Weekend w = F1.weekend(new JSONObject(next));
        assertEquals("Coastal Grand Prix", w.name);
        assertEquals("Seafront Circuit", w.circuit);
        assertEquals(java.time.Instant.parse("2026-11-22T14:00:00Z").toEpochMilli(), w.race);
        assertEquals(java.time.Instant.parse("2026-11-20T11:30:00Z").toEpochMilli(), w.start);
        assertEquals(3, w.sessions.size());
        assertNull(F1.weekend(new JSONObject("{\"MRData\":{\"RaceTable\":{\"Races\":[]}}}")));
    }

    @Test public void formula1sPicturesInTheTeamLogosFamily() {
        F1.Standing st = new F1.Standing();
        st.given = "Nico";
        st.family = "Hülkenberg";
        st.name = "Nico Hülkenberg";
        st.teamId = "red_bull";
        st.season = 2026;
        assertEquals("https://media.formula1.com/image/upload/c_lfill,w_560/q_auto/v1740000001/common/f1/2026/redbullracing/nichul01/"
                + "2026redbullracingnichul01right.webp", F1.photo(st, 560));
        assertEquals("https://media.formula1.com/image/upload/c_lfill,w_1000/q_auto/v1740000001/common/f1/2026/astonmartin/"
                + "2026astonmartincarright.webp", F1.car("aston_martin", 2026, 1000));
        assertEquals("https://media.formula1.com/image/upload/c_lfill,w_96/q_auto/v1740000001/common/f1/2026/racingbulls/"
                + "2026racingbullslogo.webp", F1.logo("rb", 2026));
        assertEquals("andant01", F1.driverSlug("Andrea Kimi", "Antonelli"));
        assertEquals("maxver01", F1.driverSlug("Max", "Verstappen"));
        assertNull(F1.driverSlug("Al", "Ng"));
        assertEquals("audi", F1.teamSlug("audi"));
        assertNull(F1.teamSlug("Red Bull!"));
        assertNull(F1.car("", 2026, 1000));
        st.teamId = "";
        assertNull(F1.photo(st, 560));
    }

    @Test public void aFlagForANationality() {
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/ned.png", F1.flag("Dutch"));
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/gbr.png", F1.flag("British"));
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/mon.png", F1.flag(" monegasque "));
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/nzl.png", F1.flag("New Zealander"));
        assertNull(F1.flag("Martian"));
        assertNull(F1.flag(null));
    }
}
