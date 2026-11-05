package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The playlists' expiry warnings and health verdicts (pure parts). */
public class PlaylistCareTest {
    private static final long DAY = 86_400_000L, NOW = 1_800_000_000_000L;

    @Test public void aWeekBeforeThenADayBefore() {
        assertEquals(0, Expiry.due(NOW + 30 * DAY, NOW));
        assertEquals(0, Expiry.due(NOW + 7 * DAY + 1, NOW));
        assertEquals(Expiry.WEEK, Expiry.due(NOW + 7 * DAY, NOW));
        assertEquals(Expiry.WEEK, Expiry.due(NOW + 2 * DAY, NOW));
        assertEquals(Expiry.WEEK, Expiry.due(NOW + DAY + 1, NOW));
        assertEquals(Expiry.LAST_DAY, Expiry.due(NOW + DAY, NOW));
        assertEquals(Expiry.LAST_DAY, Expiry.due(NOW + 60_000L, NOW));
    }

    @Test public void noWarningForUnknownNeverOrPast() {
        assertEquals(0, Expiry.due(0, NOW));
        assertEquals(0, Expiry.due(-1, NOW));
        assertEquals(0, Expiry.due(NOW - DAY, NOW));
        assertEquals(0, Expiry.due(NOW, NOW));
    }

    @Test public void howLongIsLeftInWords() {
        assertEquals("in 6 days", Expiry.left(NOW + 6 * DAY, NOW));
        assertEquals("in 3 days", Expiry.left(NOW + 2 * DAY + 3_600_000L, NOW));
        assertEquals("within a day", Expiry.left(NOW + 5 * 3_600_000L, NOW));
    }

    @Test public void eachWarningIsKeptPerPlaylistAndDate() {
        assertNotEquals(Expiry.key("p0", NOW, 7), Expiry.key("p0", NOW, 1));
        assertNotEquals(Expiry.key("p0", NOW, 7), Expiry.key("p1", NOW, 7));
        // Renewed: a new date is warned about afresh.
        assertNotEquals(Expiry.key("p0", NOW, 7), Expiry.key("p0", NOW + 30 * DAY, 7));
    }

    @Test public void verdicts() {
        assertEquals(PlaylistHealth.HEALTHY, PlaylistHealth.verdict(400, 1200, 5, 0));
        assertEquals(PlaylistHealth.SLOW, PlaylistHealth.verdict(400, 1200, 5, 1));
        assertEquals(PlaylistHealth.SLOW, PlaylistHealth.verdict(400, 4800, 5, 0));
        assertEquals(PlaylistHealth.SLOW, PlaylistHealth.verdict(7000, 1200, 5, 0));
        assertEquals(PlaylistHealth.STRUGGLING, PlaylistHealth.verdict(400, 1200, 4, 2));
        assertEquals(PlaylistHealth.STRUGGLING, PlaylistHealth.verdict(400, 9000, 5, 0));
        assertEquals(PlaylistHealth.STRUGGLING, PlaylistHealth.verdict(-1, -1, 0, 0));
        // Not tried (its connections all in use): told by its answer alone.
        assertEquals(PlaylistHealth.HEALTHY, PlaylistHealth.verdict(500, -1, 0, 0));
    }

    @Test public void theHealthierPlaylistScoresLower() {
        PlaylistHealth.Result good = new PlaylistHealth.Result(400, 1200, 5, 0, NOW);
        PlaylistHealth.Result slow = new PlaylistHealth.Result(400, 4800, 5, 0, NOW);
        PlaylistHealth.Result failing = new PlaylistHealth.Result(400, 1200, 5, 3, NOW);
        assertTrue(PlaylistHealth.score(good) < PlaylistHealth.score(slow));
        assertTrue(PlaylistHealth.score(slow) < PlaylistHealth.score(failing));
        assertEquals(-1, PlaylistHealth.score((PlaylistHealth.Result) null));
    }

    @Test public void hlsIsFollowedToItsVariantThenItsNewestSegment() {
        String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=5000000\nhd/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nsd/index.m3u8\n";
        assertEquals("http://example.invalid/live/hd/index.m3u8", PlaylistHealth.segment("http://example.invalid/live/main.m3u8", master));
        String media = "#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg1.ts\n#EXTINF:6,\nseg2.ts\n";
        assertEquals("http://example.invalid/live/hd/seg2.ts", PlaylistHealth.segment("http://example.invalid/live/hd/index.m3u8", media));
        assertNull(PlaylistHealth.segment("http://example.invalid/a.m3u8", "#EXTM3U\n"));
    }

    @Test public void wordsForTheCard() {
        assertEquals("1.2 s", PlaylistHealth.seconds(1200));
        assertEquals("—", PlaylistHealth.seconds(-1));
        assertEquals("today", PlaylistHealth.ago(NOW - 3_600_000L, NOW));
        assertEquals("yesterday", PlaylistHealth.ago(NOW - DAY - 1, NOW));
        assertEquals("3 days ago", PlaylistHealth.ago(NOW - 3 * DAY - 1, NOW));
    }
}
