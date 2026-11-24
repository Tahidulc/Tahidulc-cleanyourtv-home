package com.gathin.tvhome;

import org.junit.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Homefield TV's cards on the dock's row: read from its provider's rows (a channel once, its order kept, no provider: the
 * plain app's row), what each card says, and what OK sends Homefield TV. Invented channels.
 */
public class HomefieldCardsTest {
    private static final long NOW = 1_800_000_000_000L, MIN = 60_000L;
    private static final ZoneId UTC = ZoneId.of("UTC");

    /** The provider's rows as a list of column maps. */
    private static final class Fake implements HomefieldCards.Rows {
        final List<Map<String, Object>> rows = new ArrayList<>();
        int at = -1;

        Fake row(String section, String key, String name, int number, String now, long start, long end, String picture, String next) {
            Map<String, Object> r = new HashMap<>();
            r.put("section", section);
            r.put("channel_key", key);
            r.put("channel_name", name);
            r.put("channel_number", (long) number);
            r.put("logo_url", "https://logos.example/" + key + ".png");
            r.put("now_title", now);
            r.put("now_start", start);
            r.put("now_end", end);
            r.put("now_picture", picture);
            r.put("now_description", now == null ? null : "About " + now);
            r.put("next_title", next);
            rows.add(r);
            return this;
        }

        Fake colours(int top, int foot) {
            rows.get(rows.size() - 1).put("logo_colour", (long) top);
            rows.get(rows.size() - 1).put("logo_colour_foot", (long) foot);
            return this;
        }

        @Override public boolean next() {
            return ++at < rows.size();
        }

        @Override public String str(String column) {
            Object o = rows.get(at).get(column);
            return o == null ? null : o.toString();
        }

        @Override public long num(String column) {
            Object o = rows.get(at).get(column);
            return o instanceof Long ? (Long) o : 0L;
        }
    }

    private static List<String> keys(List<HomefieldCards.Card> cards) {
        List<String> out = new ArrayList<>();
        for (HomefieldCards.Card c : cards) out.add(c.key);
        return out;
    }

    @Test public void theCardsInTheirOrderEachChannelOnce() {
        Fake f = new Fake()
                .row("recent", "p0|2", "Kestrel Two", 102, "Saltwater Hymn", NOW - 10 * MIN, NOW + 20 * MIN, "https://pictures.example/hymn.jpg", "Midnight Orchard")
                .colours(0xFF203040, 0xFF102030)
                .row("recent", "p0|1", "Kestrel One", 101, null, 0, 0, null, null)
                .row("favourite", "p0|5", "Harbour TV", 105, "Northbound", NOW - 5 * MIN, NOW + 25 * MIN, "", null)
                .row("favourite", "p0|2", "Kestrel Two", 102, "Saltwater Hymn", NOW - 10 * MIN, NOW + 20 * MIN, "", null)
                .row("sport", "s|1", "North Sports 1", 401, "Live: Riverside v Fogbell", NOW - 20 * MIN, NOW + 90 * MIN, "", "Full time")
                .row("sport", "", "No key", 0, "Nothing", NOW, NOW + MIN, "", null);
        List<HomefieldCards.Card> cards = HomefieldCards.parse(f);
        // Kestrel Two once (its first place, recent); a row without a key left out.
        assertEquals(Arrays.asList("p0|2", "p0|1", "p0|5", "s|1"), keys(cards));
        HomefieldCards.Card two = cards.get(0);
        assertEquals("recent", two.section);
        assertEquals("Kestrel Two", two.name);
        assertEquals(102, two.number);
        assertArrayEquals(new int[]{0xFF203040, 0xFF102030}, two.colours);
        assertTrue(two.hasPicture());
        // No colours said: worked out on the TV from the logo.
        assertNull(cards.get(2).colours);
        assertFalse(cards.get(2).hasPicture());
    }

    @Test public void noProviderMeansThePlainRow() {
        // Homefield TV too old to have the cards (or not allowed): null, and Home shows the app's own row as before.
        assertNull(HomefieldCards.parse(null));
        // A Homefield TV with nothing to show yet: no cards, but the cards are Homefield TV's.
        assertTrue(HomefieldCards.parse(new Fake()).isEmpty());
        // At most MAX cards.
        Fake many = new Fake();
        for (int i = 0; i < 50; i++) many.row("sport", "s|" + i, "North Sports " + i, 400 + i, "Game " + i, NOW, NOW + MIN, "", null);
        assertEquals(HomefieldCards.MAX, HomefieldCards.parse(many).size());
    }

    @Test public void whatACardSays() {
        List<HomefieldCards.Card> cards = HomefieldCards.parse(new Fake()
                .row("recent", "p0|2", "Kestrel Two", 102, "Saltwater Hymn", NOW - 10 * MIN, NOW + 20 * MIN, "https://pictures.example/hymn/16-9/640", "Midnight Orchard")
                .row("favourite", "p0|1", "Kestrel One", 101, null, 0, 0, null, null));
        HomefieldCards.Card on = cards.get(0), off = cards.get(1);
        // As TiviMate's cards: the programme, "On now" with its times, a bar a third of the way.
        assertEquals("Saltwater Hymn", HomefieldCards.title(on));
        String sub = HomefieldCards.sub(on, UTC);
        assertTrue(sub, sub.startsWith("On now · ") && sub.contains(" – "));
        assertEquals(1f / 3f, HomefieldCards.progress(on, NOW), 0.001f);
        assertEquals("Recently watched · Kestrel Two · 20 min left · Next: Midnight Orchard", HomefieldCards.heroMeta(on, NOW));
        // Nothing on (its guide says nothing): the channel's name and its section, no bar.
        assertEquals("Kestrel One", HomefieldCards.title(off));
        assertEquals("Favourite", HomefieldCards.sub(off, UTC));
        assertEquals(-1f, HomefieldCards.progress(off, NOW), 0f);
        assertEquals("Favourite · Kestrel One", HomefieldCards.heroMeta(off, NOW));
        assertEquals("Sport on now", HomefieldCards.sectionWords("sport"));
    }

    @Test public void picturesAtTheirSizeElseTheLogoOnItsColour() {
        List<HomefieldCards.Card> cards = HomefieldCards.parse(new Fake()
                .row("recent", "p0|2", "Kestrel Two", 102, "Saltwater Hymn", NOW - 10 * MIN, NOW + 20 * MIN, "https://pictures.example/hymn/16-9/640", null)
                .row("recent", "p0|1", "Kestrel One", 101, "The Long Tide", NOW - 10 * MIN, NOW + 20 * MIN, "", null)
                .colours(0xFF203040, 0xFF102030));
        // The card's picture decoded at the card's width; the hero's the larger copy.
        assertEquals("https://pictures.example/hymn/16-9/640" + HomefieldCards.FIT, HomefieldCards.picture(cards.get(0)));
        assertEquals("https://pictures.example/hymn/16-9/1280", HomefieldCards.heroPicture(cards.get(0)));
        // No picture: the logo on its colours, drawn on the TV (art), the colours and logo carried in its address.
        String art = HomefieldCards.picture(cards.get(1));
        assertTrue(art, art.startsWith(HomefieldCards.ART));
        assertEquals(art, HomefieldCards.heroPicture(cards.get(1)));
        assertArrayEquals(new int[]{0xFF203040, 0xFF102030}, HomefieldCards.colours(art.substring(HomefieldCards.ART.length()).split("\\|")[0]));
        assertTrue(art.contains("|https://logos.example/p0|1.png".replace("p0|1", "p0%7C1")));
        assertNull(HomefieldCards.colours("0,0"));
        assertNull(HomefieldCards.colours("rubbish"));
    }

    @Test public void okSendsTheKeyItsNameAndNumberAndWhen() {
        HomefieldCards.Card c = HomefieldCards.parse(new Fake()
                .row("sport", "s|1", "North Sports 1", 401, "Live: Riverside v Fogbell", NOW, NOW + MIN, "", null)).get(0);
        List<Object[]> e = HomefieldCards.extras(c, NOW);
        assertEquals(4, e.size());
        assertArrayEquals(new Object[]{"com.gathin.homefieldtv.extra.CHANNEL_KEY", "s|1"}, e.get(0));
        assertArrayEquals(new Object[]{"com.gathin.homefieldtv.extra.CHANNEL_NAME", "North Sports 1"}, e.get(1));
        assertArrayEquals(new Object[]{"com.gathin.homefieldtv.extra.CHANNEL_NUMBER", 401}, e.get(2));
        assertArrayEquals(new Object[]{"com.gathin.homefieldtv.extra.AT", NOW}, e.get(3));
        assertEquals("com.gathin.homefieldtv.action.PLAY_CHANNEL", HomefieldCards.ACTION);
        // No name or number: the key alone (and when).
        HomefieldCards.Card bare = new HomefieldCards.Card();
        bare.key = "p0|9";
        assertEquals(2, HomefieldCards.extras(bare, NOW).size());
    }

    @Test public void theSameCardsOnlyMoveTheirBars() {
        HomeV2.Tile a = tile("p0|2", "Saltwater Hymn"), b = tile("p0|2", "Saltwater Hymn");
        b.progress = 0.8f;
        assertTrue(HomefieldCards.same(Arrays.asList(a), Arrays.asList(b)));
        assertFalse(HomefieldCards.same(Arrays.asList(a), Arrays.asList(tile("p0|2", "Midnight Orchard"))));
        assertFalse(HomefieldCards.same(Arrays.asList(a), Arrays.asList(tile("p0|3", "Saltwater Hymn"))));
        assertFalse(HomefieldCards.same(Arrays.asList(a), new ArrayList<>()));
        assertFalse(HomefieldCards.same(null, Arrays.asList(a)));
    }

    private static HomeV2.Tile tile(String key, String title) {
        HomeV2.Tile t = new HomeV2.Tile();
        t.key = key;
        t.title = title;
        t.image = "https://pictures.example/" + title.hashCode() + HomefieldCards.FIT;
        t.sub = "On now · 20:00 – 20:30";
        return t;
    }
}
