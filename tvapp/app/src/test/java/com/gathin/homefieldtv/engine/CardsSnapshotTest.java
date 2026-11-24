package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The launcher's cards for Homefield TV: recent channels newest first, then the favourites, then the sport on now (live
 * first); each list a channel once; what is on now and next worked out when asked; kept as a small file. Invented channels.
 */
public class CardsSnapshotTest {
    private static final long NOW = 1_800_000_000_000L, MIN = 60_000L;

    private static List<String> keys(List<CardsSnapshot.Row> rows, String section) {
        List<String> out = new ArrayList<>();
        for (CardsSnapshot.Row r : rows) if (section == null || section.equals(r.section)) out.add(r.card.key);
        return out;
    }

    /** A channel with half-hour programmes from 20 minutes ago, titled as given. */
    private static CardsSnapshot.Card channel(CardsSnapshot.Builder b, String key, String name, String... titles) {
        CardsSnapshot.Card c = b.card(key, name, 100 + key.length(), "https://logos.example/" + key + ".png", null);
        long at = NOW - 20 * MIN;
        for (String t : titles) {
            b.prog(c, at, at + 30 * MIN, t, "About " + t, "");
            at += 30 * MIN;
        }
        return c;
    }

    @Test public void recentThenFavouritesThenSportOnNowLiveFirst() {
        CardsSnapshot.Builder b = new CardsSnapshot.Builder(NOW);
        channel(b, "k1", "Kestrel One", "The Long Tide", "Harbour Lights");
        channel(b, "k2", "Kestrel Two", "Rocket Garden");
        channel(b, "h1", "Harbour TV", "Saltwater Hymn");
        channel(b, "s1", "North Sports 1", "Darts Tonight", "Snooker Replay");
        channel(b, "s2", "North Sports 2", "Live: Riverside v Fogbell");
        CardsSnapshot.Card s3 = b.card("s3", "North Sports 3", 403, "", null);
        // Nothing on now on s3: its game is later.
        b.prog(s3, NOW + 60 * MIN, NOW + 150 * MIN, "LIVE Kestrel Cup Final", "", "");
        b.add(CardsSnapshot.RECENT, "k2");
        b.add(CardsSnapshot.RECENT, "k1");
        b.add(CardsSnapshot.FAVOURITE, "h1");
        b.add(CardsSnapshot.FAVOURITE, "k1");
        b.add(CardsSnapshot.SPORT, "s1");
        b.add(CardsSnapshot.SPORT, "s2");
        b.add(CardsSnapshot.SPORT, "s3");
        CardsSnapshot s = b.build();
        List<CardsSnapshot.Row> rows = s.rows(NOW);
        assertEquals(java.util.Arrays.asList("k2", "k1", "h1", "k1", "s2", "s1"), keys(rows, null));
        assertEquals(CardsSnapshot.RECENT, rows.get(0).section);
        assertEquals(0, rows.get(0).position);
        assertEquals(1, rows.get(1).position);
        assertEquals(CardsSnapshot.FAVOURITE, rows.get(2).section);
        assertEquals(CardsSnapshot.SPORT, rows.get(4).section);
        assertEquals(0, rows.get(4).position);
        // What is on now and what comes next.
        CardsSnapshot.Row k1 = rows.get(1);
        assertEquals("The Long Tide", k1.now.title);
        assertEquals("Harbour Lights", k1.next.title);
        assertEquals("About The Long Tide", k1.now.desc);
        // An hour on, s3's game is on and live: it leads the sport.
        List<CardsSnapshot.Row> later = s.rows(NOW + 70 * MIN);
        assertEquals("s3", keys(later, CardsSnapshot.SPORT).get(0));
        // A recent channel with nothing on still shows (the guide says nothing then); its now is null.
        assertNull(later.get(0).now);
        assertEquals("k2", later.get(0).card.key);
    }

    @Test public void eachListHoldsAChannelOnceAndAtMostItsMost() {
        CardsSnapshot.Builder b = new CardsSnapshot.Builder(NOW);
        for (int i = 0; i < 30; i++) channel(b, "c" + i, "Channel " + i, "Show " + i);
        b.add(CardsSnapshot.RECENT, "c1");
        b.add(CardsSnapshot.RECENT, "c1");
        b.add(CardsSnapshot.RECENT, "c2");
        for (int i = 0; i < 30; i++) b.add(CardsSnapshot.FAVOURITE, "c" + i);
        // A key with no card is never listed.
        b.add(CardsSnapshot.FAVOURITE, "nowhere");
        CardsSnapshot s = b.build();
        assertEquals(java.util.Arrays.asList("c1", "c2"), s.recent);
        assertEquals(CardsSnapshot.FAVOURITES_MAX, s.favourites.size());
        assertFalse(s.favourites.contains("nowhere"));
        // A channel made twice keeps the first name.
        assertEquals("Channel 1", b.card("c1", "Another name", 1, "", null).name);
        // Only channels a list holds are kept.
        CardsSnapshot.Builder few = new CardsSnapshot.Builder(NOW);
        channel(few, "a", "Kestrel One", "The Long Tide");
        channel(few, "b", "Kestrel Two", "Rocket Garden");
        few.add(CardsSnapshot.RECENT, "a");
        assertEquals(Collections.singleton("a"), few.build().channels.keySet());
    }

    @Test public void sportShowsOnlyWhatIsOnAndAtMostTwelve() {
        CardsSnapshot.Builder b = new CardsSnapshot.Builder(NOW);
        for (int i = 0; i < 20; i++) {
            channel(b, "s" + i, "North Sports " + i, i % 5 == 0 ? "Live: Game " + i : "Highlights " + i);
            b.add(CardsSnapshot.SPORT, "s" + i);
        }
        List<CardsSnapshot.Row> rows = b.build().rows(NOW);
        assertEquals(CardsSnapshot.SPORT_SHOWN, rows.size());
        // The four live ones first, in the guide's order, then the rest in theirs.
        assertEquals(java.util.Arrays.asList("s0", "s5", "s10", "s15", "s1", "s2"), keys(rows, null).subList(0, 6));
        for (int i = 0; i < rows.size(); i++) assertEquals(i, rows.get(i).position);
        // Long after the programmes kept, no sport is on.
        assertTrue(b.build().rows(NOW + 24 * 3600_000L).isEmpty());
    }

    @Test public void onlyTheComingHoursAreKeptTheirFirstDescribed() {
        CardsSnapshot.Builder b = new CardsSnapshot.Builder(NOW);
        CardsSnapshot.Card c = b.card("k", "Kestrel One", 101, "", null);
        // Over before the snapshot: left out.
        b.prog(c, NOW - 90 * MIN, NOW - 30 * MIN, "Earlier", "gone", "");
        for (int i = 0; i < 20; i++) b.prog(c, NOW + i * 60 * MIN, NOW + (i + 1) * 60 * MIN, "Hour " + i, "About hour " + i, "");
        assertEquals(CardsSnapshot.PROGRAMMES_MAX, c.progs.size());
        assertEquals("Hour 0", c.progs.get(0).title);
        assertEquals("About hour 2", c.progs.get(2).desc);
        assertEquals("", c.progs.get(3).desc);
        // Out of order, or empty: left out.
        CardsSnapshot.Card d = b.card("d", "Kestrel Two", 102, "", null);
        b.prog(d, NOW, NOW + 30 * MIN, "First", "", "");
        b.prog(d, NOW - 10 * MIN, NOW + 5 * MIN, "Back in time", "", "");
        b.prog(d, NOW + 30 * MIN, NOW + 30 * MIN, "No length", "", "");
        assertEquals(1, d.progs.size());
    }

    @Test public void keptAsAFileAndReadBack() {
        CardsSnapshot.Builder b = new CardsSnapshot.Builder(NOW);
        CardsSnapshot.Card c = b.card("p0|42", "Kestrel One", 101, "https://logos.example/k1.png", new int[]{0xFF203040, 0xFF102030});
        b.prog(c, NOW - 10 * MIN, NOW + 20 * MIN, "The Long Tide", "A ferry, a storm, a promise.", "https://pictures.example/tide.jpg");
        b.prog(c, NOW + 20 * MIN, NOW + 50 * MIN, "Harbour Lights", "", "");
        b.add(CardsSnapshot.RECENT, "p0|42");
        b.add(CardsSnapshot.FAVOURITE, "p0|42");
        CardsSnapshot s = b.build();
        CardsSnapshot back = CardsSnapshot.fromJson(s.toJson());
        assertEquals(NOW, back.at);
        assertEquals(s.recent, back.recent);
        assertEquals(s.favourites, back.favourites);
        CardsSnapshot.Card k = back.channels.get("p0|42");
        assertEquals("Kestrel One", k.name);
        assertEquals(101, k.number);
        assertArrayEquals(new int[]{0xFF203040, 0xFF102030}, k.colours);
        assertEquals("https://pictures.example/tide.jpg", k.at(NOW).picture);
        assertEquals("A ferry, a storm, a promise.", k.at(NOW).desc);
        assertEquals("Harbour Lights", k.after(NOW).title);
        // Nothing, rubbish or another version's file: no cards.
        assertSame(CardsSnapshot.EMPTY, CardsSnapshot.fromJson(null));
        assertSame(CardsSnapshot.EMPTY, CardsSnapshot.fromJson("not json"));
        assertSame(CardsSnapshot.EMPTY, CardsSnapshot.fromJson("{\"v\":99}"));
        assertTrue(CardsSnapshot.EMPTY.rows(NOW).isEmpty());
    }

    @Test public void picturesFoundLaterGoIn() {
        CardsSnapshot.Builder b = new CardsSnapshot.Builder(NOW);
        CardsSnapshot.Card c = b.card("k", "Kestrel One", 101, "", null);
        b.prog(c, NOW - 10 * MIN, NOW + 20 * MIN, "The Long Tide", "", "");
        b.prog(c, NOW + 20 * MIN, NOW + 50 * MIN, "Harbour Lights", "", "https://pictures.example/had.jpg");
        b.add(CardsSnapshot.RECENT, "k");
        CardsSnapshot s = b.build();
        Map<String, Map<Long, String>> found = new HashMap<>();
        Map<Long, String> mine = new HashMap<>();
        mine.put(NOW - 10 * MIN, "https://pictures.example/tide.jpg");
        mine.put(NOW + 20 * MIN, "");
        found.put("k", mine);
        CardsSnapshot with = s.withPictures(found);
        assertEquals("https://pictures.example/tide.jpg", with.picture("k", NOW - 10 * MIN));
        // An empty answer never takes away a picture already known.
        assertEquals("https://pictures.example/had.jpg", with.picture("k", NOW + 20 * MIN));
        assertEquals(s.recent, with.recent);
        assertEquals("", with.picture("nowhere", NOW));
        assertSame(s, s.withPictures(new HashMap<>()));
    }

    @Test public void whatSaysLive() {
        assertTrue(CardsSnapshot.live("Live: Riverside v Fogbell"));
        assertTrue(CardsSnapshot.live("LIVE Kestrel Cup Final"));
        assertTrue(CardsSnapshot.live("Kestrel Cup Final (Live)"));
        assertTrue(CardsSnapshot.live("Snooker - Live"));
        assertFalse(CardsSnapshot.live("Liverpool Street Stories"));
        assertFalse(CardsSnapshot.live("Live and Kicking"));
        assertFalse(CardsSnapshot.live("Deliverance"));
        assertFalse(CardsSnapshot.live(null));
    }
}
