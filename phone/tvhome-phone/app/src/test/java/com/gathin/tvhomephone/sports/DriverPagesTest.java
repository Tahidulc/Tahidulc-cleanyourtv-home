package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The F1 driver's page: Jolpica's standings with each driver's number, nationality and birthday, his season (Grands
 * Prix and podiums) from one read of his results, the next Grand Prix's dates and countdown, formula1.com's pictures by
 * the family of addresses the team logos use, and the figures only as the sources give them. Invented replies only.
 */
public class DriverPagesTest {
    private static JSONObject standing(String pos, String id, String number, String given, String family, String nat, String born,
                                       String points, String... teams) throws Exception {
        JSONArray cons = new JSONArray();
        for (String t : teams) {
            String[] p = t.split("=");
            cons.put(new JSONObject().put("constructorId", p[0]).put("name", p[1]));
        }
        JSONObject driver = new JSONObject().put("driverId", id).put("givenName", given).put("familyName", family)
                .put("nationality", nat).put("dateOfBirth", born);
        if (number != null) driver.put("permanentNumber", number);
        return new JSONObject().put("position", pos).put("points", points).put("Driver", driver).put("Constructors", cons);
    }

    @Test public void theStandingsWithEachDriversNumberCountryAndBirthday() throws Exception {
        JSONArray rows = new JSONArray()
                .put(standing("1", "aldo_ferran", "33", "Aldo", "Ferran", "Dutch", "1997-09-30", "412", "red_bull=Northline Racing"))
                .put(standing("2", "jules_marchetti", "4", "Jules", "Marchetti", "British", "1999-11-13", "398.5",
                        "williams=Old Team", "mclaren=Orange Arrow"))
                .put(standing("3", "new_boy", null, "Kai", "Nuevo", "", "unknown", "0"));
        JSONObject root = new JSONObject().put("MRData", new JSONObject().put("StandingsTable", new JSONObject().put("season", "2026")
                .put("StandingsLists", new JSONArray().put(new JSONObject().put("season", "2026").put("DriverStandings", rows)))));
        List<F1.Standing> l = F1.drivers(root);
        assertEquals(3, l.size());
        F1.Standing a = l.get(0), b = l.get(1), c = l.get(2);
        assertEquals("aldo_ferran", a.driverId);
        assertEquals("33", a.number);
        assertEquals("Aldo", a.given);
        assertEquals("Aldo Ferran", a.name);
        assertEquals("Dutch", a.nationality);
        assertEquals("1997-09-30", a.born);
        assertEquals("red_bull", a.teamId);
        assertEquals(2026, a.season);
        // his latest team this season
        assertEquals("mclaren", b.teamId);
        assertEquals("Orange Arrow", b.team);
        // what is not given stays empty
        assertEquals("", c.number);
        assertEquals("", c.born);
        assertEquals("", c.nationality);
        JSONObject none = new JSONObject().put("MRData", new JSONObject().put("StandingsTable", new JSONObject().put("StandingsLists", new JSONArray())));
        assertTrue(F1.drivers(none).isEmpty());
    }

    private static JSONObject race(String round, String position) throws Exception {
        JSONObject r = new JSONObject().put("season", "2026").put("round", round);
        if (position != null) r.put("Results", new JSONArray().put(new JSONObject().put("position", position)));
        else r.put("Results", new JSONArray());
        return r;
    }

    @Test public void hisSeasonFromOneReadOfHisResults() throws Exception {
        JSONArray races = new JSONArray().put(race("1", "1")).put(race("2", "3")).put(race("3", "4")).put(race("4", "19"))
                .put(race("5", null)).put(race("6", "2"));
        F1.Season s = F1.season(new JSONObject().put("MRData", new JSONObject().put("RaceTable",
                new JSONObject().put("season", "2026").put("Races", races))));
        assertEquals(2026, s.year);
        assertEquals(5, s.races);
        assertEquals(3, s.podiums);
        assertEquals(1, s.wins);
        F1.Season none = F1.season(new JSONObject().put("MRData", new JSONObject().put("RaceTable", new JSONObject().put("Races", new JSONArray()))));
        assertEquals(0, none.races);
        assertEquals(0, none.podiums);
        // an id that is not one is never read
        assertNull(F1.season("../../x"));
        assertNull(F1.keptSeason("nobody_read"));
    }

    @Test public void theNextGrandPrixWithItsWeekendsFirstSession() throws Exception {
        JSONObject r = new JSONObject().put("raceName", "Harbour City Grand Prix").put("round", "22")
                .put("Circuit", new JSONObject().put("circuitName", "Harbour Street Circuit"))
                .put("date", "2026-11-22").put("time", "14:00:00Z")
                .put("FirstPractice", new JSONObject().put("date", "2026-11-20").put("time", "11:30:00Z"))
                .put("Qualifying", new JSONObject().put("date", "2026-11-21").put("time", "15:00:00Z"));
        F1.Weekend w = F1.weekend(new JSONObject().put("MRData", new JSONObject().put("RaceTable",
                new JSONObject().put("Races", new JSONArray().put(r)))));
        assertEquals("Harbour City Grand Prix", w.name);
        assertEquals("Harbour Street Circuit", w.circuit);
        assertEquals(ZonedDateTime.of(2026, 11, 22, 14, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(), w.race);
        assertEquals(ZonedDateTime.of(2026, 11, 20, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli(), w.start);
        assertEquals("Race", w.sessions.get(w.sessions.size() - 1)[0]);
        assertNull(F1.weekend(new JSONObject().put("MRData", new JSONObject().put("RaceTable", new JSONObject().put("Races", new JSONArray())))));
        assertEquals("20 – 22 Nov", DriverFacts.dates(w.start, w.race, ZoneId.of("Europe/London")));
        assertEquals("22 Nov", DriverFacts.dates(0, w.race, ZoneId.of("Europe/London")));
        long oct30 = ZonedDateTime.of(2026, 10, 30, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli();
        long nov1 = ZonedDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli();
        assertEquals("30 Oct – 1 Nov", DriverFacts.dates(oct30, nov1, ZoneId.of("Europe/London")));
        assertEquals("", DriverFacts.dates(0, 0, ZoneId.of("Europe/London")));
    }

    @Test public void theCountdownInDaysHoursAndMinutes() {
        long now = 1_000_000_000_000L;
        long start = now + ((4 * 24 + 20) * 60 + 35) * 60_000L + 30_000L;
        assertArrayEquals(new String[]{"04", "20", "35"}, DriverFacts.countdown(start, now));
        assertArrayEquals(new String[]{"00", "00", "00"}, DriverFacts.countdown(now - 5_000, now));
        assertArrayEquals(new String[]{"12", "00", "01"}, DriverFacts.countdown(now + (12 * 24 * 60 + 1) * 60_000L, now));
        // the next change is when the minutes do, and never sooner than a second
        assertEquals(30_050L, DriverFacts.untilTick(start, now));
        assertEquals(60_000L, DriverFacts.untilTick(now + 120_000L, now));
        assertEquals(60_000L, DriverFacts.untilTick(now - 1, now));
        assertEquals(1000L, DriverFacts.untilTick(now + 60_000L * 3 + 100, now));
    }

    @Test public void theLineUnderHisNameAndHisFigures() {
        LocalDate today = LocalDate.of(2026, 11, 18);
        assertEquals(29, DriverFacts.age("1997-09-30", today));
        assertEquals(26, DriverFacts.age("1999-11-19", today));
        assertEquals(-1, DriverFacts.age("", today));
        assertEquals("Dutch  ·  Age 29", DriverFacts.facts("Dutch", 29));
        assertEquals("Dutch", DriverFacts.facts("Dutch", -1));
        assertEquals("Age 29", DriverFacts.facts("", 29));
        assertEquals("", DriverFacts.facts(null, -1));
        assertEquals("1st", DriverFacts.place("1"));
        assertEquals("22nd", DriverFacts.place("22"));
        assertEquals("", DriverFacts.place("-"));

        F1.Standing st = new F1.Standing();
        st.points = "412";
        // before his season is read: his points only, nothing filled in
        assertEquals(Arrays.asList("Points=412"), flat(DriverFacts.figures(st, null)));
        F1.Season s = new F1.Season();
        s.races = 21;
        s.podiums = 14;
        assertEquals(Arrays.asList("Grands Prix=21", "Points=412", "Podiums=14"), flat(DriverFacts.figures(st, s)));
        st.points = "98.5";
        assertEquals("Points=98.5", flat(DriverFacts.figures(st, null)).get(0));
        st.points = "";
        F1.Season unread = new F1.Season();
        assertTrue(DriverFacts.figures(st, unread).isEmpty());
    }

    private static List<String> flat(List<String[]> f) {
        List<String> out = new ArrayList<>();
        for (String[] x : f) out.add(x[0] + "=" + x[1]);
        return out;
    }

    @Test public void theDriverOpenedIsFoundById() {
        List<F1.Standing> l = new ArrayList<>();
        for (String id : new String[]{"a_one", "b_two", "c_three"}) {
            F1.Standing st = new F1.Standing();
            st.driverId = id;
            st.name = id.toUpperCase();
            l.add(st);
        }
        assertEquals(1, DriverFacts.indexOf(l, "b_two", "x"));
        assertEquals(2, DriverFacts.indexOf(l, "", "C_THREE"));
        assertEquals(0, DriverFacts.indexOf(l, "nobody", "nobody"));
    }

    @Test public void formula1ComsPicturesByTheLogosFamilyOfAddresses() {
        F1.Standing st = new F1.Standing();
        st.given = "Nico";
        st.family = "Hülkenberg";
        st.teamId = "red_bull";
        st.season = 2026;
        String base = "https://media.formula1.com/image/upload/c_lfill,w_";
        assertEquals(base + "400/q_auto/v1740000001/common/f1/2026/redbullracing/nichul01/2026redbullracingnichul01right.webp", F1.photo(st, 400));
        assertEquals(base + "800/q_auto/v1740000001/common/f1/2026/astonmartin/2026astonmartincarright.webp", F1.car("aston_martin", 2026, 800));
        assertEquals(base + "96/q_auto/v1740000001/common/f1/2026/racingbulls/2026racingbullslogo.webp", F1.logo("rb", 2026));
        assertEquals("maxver01", F1.driverSlug("Max Emilian", "Verstappen"));
        assertNull(F1.driverSlug("Al", "Jo"));
        assertNull(F1.car("../x", 2026, 800));
        assertNull(F1.photo(null, 400));
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/ned.png", F1.flag("Dutch"));
        assertEquals("https://a.espncdn.com/i/teamlogos/countries/500/mon.png", F1.flag(" Monegasque "));
        assertNull(F1.flag("Martian"));
    }
}
