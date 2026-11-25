package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Homefield TV's row on the dock is TiviMate's: the same label and glyph, the same cards from Sky's guide (pictures,
 * logos, times, bars, hero, marks, previews), read alike; only OK differs, Homefield TV's opening Homefield TV on the
 * channel. Its own cards only when there are no channels on now. Invented channels and programmes.
 */
public class LiveRowTest {
    private static final long NOW = 1_800_000_000_000L, MIN = 60_000L;
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String TIVIMATE = "ar.tvplayer.tv", HOMEFIELD = "com.gathin.homefieldtv";

    private static UkTv.Now on(String channel, String title, long startAgo, long length, String next, String raw) throws Exception {
        UkTv.Now n = new UkTv.Now();
        n.channel = channel;
        n.title = title;
        n.synopsis = "About " + title;
        n.start = NOW - startAgo * MIN;
        n.end = n.start + length * MIN;
        n.next = next;
        n.image = "https://pictures.example/" + title.replace(' ', '-') + "/16-9/640";
        n.logo = "https://logos.example/" + channel.replace(' ', '-') + ".png";
        n.raw = raw == null ? null : new JSONObject(raw);
        return n;
    }

    private static List<UkTv.Now> guide() throws Exception {
        return Arrays.asList(on("Kestrel One", "The Long Tide", 22, 60, "Harbour Lights", "{\"d\":3600,\"hd\":true,\"s\":true,\"r\":\"12\"}"),
                on("North Sports 1 UHD", "Live: Riverside v Fogbell", 30, 120, "Full Time", "{\"d\":7200,\"at\":\"DD\"}"),
                on("Fogbell News", "Evening Headlines", 12, 30, null, null));
    }

    /** What OK did, in order: "tune:channel|logo" or "homefield:channel". */
    private final List<String> did = new ArrayList<>();

    private List<HomeV2.Tile> row(String pkg) throws Exception {
        return LiveRow.tiles(guide(), false, NOW, UTC, ch -> "stream-" + ch,
                LiveRow.ok(pkg, (ch, logo) -> did.add("tune:" + ch + "|" + logo), ch -> did.add("homefield:" + ch)));
    }

    @Test public void homefieldsRowIsTiviMatesButForOk() throws Exception {
        List<HomeV2.Tile> tivi = row(TIVIMATE), home = row(HOMEFIELD);
        assertEquals(3, tivi.size());
        assertEquals(tivi.size(), home.size());
        // Every field of every card the same (its look, its hero, its preview), OK aside.
        for (int i = 0; i < tivi.size(); i++) {
            for (Field f : HomeV2.Tile.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getName().equals("ok")) continue;
                f.setAccessible(true);
                assertEquals("card " + i + ": " + f.getName(), f.get(tivi.get(i)), f.get(home.get(i)));
            }
            assertNotNull(tivi.get(i).ok);
            assertNotNull(home.get(i).ok);
        }
        // As TiviMate's cards are made: the picture, the logo (a 4K channel's marked), the times, the bar, the hero.
        HomeV2.Tile one = home.get(0);
        assertEquals("The Long Tide", one.title);
        assertEquals("On now · 07:38 – 08:38", one.sub);
        assertEquals("https://pictures.example/The-Long-Tide/16-9/1280", one.heroImage);
        assertEquals("https://logos.example/Kestrel-One.png", one.badge);
        assertEquals(one.badge, one.heroTop);
        assertEquals("Kestrel One · 38 min left · Next: Harbour Lights", one.heroMeta);
        assertEquals(22f / 60f, one.progress, 0.001f);
        assertEquals("stream-Kestrel One", one.stream);
        assertEquals(Arrays.asList("HD", "S", "age:12"), one.specs);
        assertEquals("60 mins", one.duration);
        assertNull(one.key);
        HomeV2.Tile sport = home.get(1);
        assertEquals("Riverside v Fogbell", sport.title);
        assertEquals("https://logos.example/North-Sports-1-UHD.png#uhd", sport.badge);
        assertEquals(Arrays.asList("UHD", "DOLBY"), sport.specs);
        assertEquals("2h ", sport.duration);
    }

    @Test public void okOnHomefieldsOpensHomefieldAndOnTiviMatesPutsTheChannelOn() throws Exception {
        List<HomeV2.Tile> tivi = row(TIVIMATE), home = row(HOMEFIELD);
        home.get(1).ok.run();
        tivi.get(1).ok.run();
        home.get(0).ok.run();
        assertEquals(Arrays.asList("homefield:North Sports 1 UHD",
                "tune:North Sports 1 UHD|https://logos.example/North-Sports-1-UHD.png#uhd", "homefield:Kestrel One"), did);
        // Any other app's live row (the TV guide box's hover is TiviMate's) puts the channel on as every link does.
        did.clear();
        LiveRow.tiles(guide(), false, NOW, UTC, ch -> null, LiveRow.ok(null, (ch, logo) -> did.add("tune:" + ch), ch -> did.add("homefield:" + ch)))
                .get(2).ok.run();
        assertEquals(Collections.singletonList("tune:Fogbell News"), did);
    }

    @Test public void sportAloneForForYou() throws Exception {
        List<HomeV2.Tile> live = LiveRow.tiles(guide(), true, NOW, UTC, ch -> null, LiveRow.ok(TIVIMATE, (a, b) -> { }, a -> { }));
        assertEquals(1, live.size());
        assertEquals("Riverside v Fogbell", live.get(0).title);
    }

    @Test public void theSameLabelAndGlyphAndRow() {
        assertTrue(LiveRow.app(TIVIMATE));
        assertTrue(LiveRow.app(HOMEFIELD));
        assertFalse(LiveRow.app("com.debridstream.tv"));
        assertFalse(LiveRow.app(null));
        assertEquals("LIVE TV · ON NOW", LiveRow.LABEL);
    }

    @Test public void itsOwnCardsOnlyWhenThereAreNone() {
        HomeV2.Tile live = new HomeV2.Tile(), card = new HomeV2.Tile();
        card.key = "p0|101";
        // Live TV's cards: the row stays them (Homefield TV's provider not read for it).
        assertFalse(LiveRow.cardsRow(Collections.singletonList(live)));
        // Its own cards (live TV had none), or nothing yet: read again and kept fresh.
        assertTrue(LiveRow.cardsRow(Collections.singletonList(card)));
        assertTrue(LiveRow.cardsRow(new ArrayList<>()));
        // Not read yet: nothing of its own read ahead (the live row comes first).
        assertFalse(LiveRow.cardsRow(null));
    }
}
