package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Several playlists: the same channel in more than one made one with backups, and each account's expiry date. */
public class PlaylistsTest {
    private static Channel ch(String key, String name) {
        return new Channel(key, name, "", "", "http://example.invalid/" + key, "", null, null, 0);
    }

    @Test public void theSameChannelInTwoPlaylistsIsOneWithABackup() {
        List<Channel> out = Library.merged(Arrays.asList(
                ch("1", "UK: Sky Sports Main Event HD"), ch("2", "BBC One"), ch("p1:9", "Sky Sports Main Event HD"),
                ch("p1:10", "ITV1")));
        assertEquals(3, out.size());
        assertEquals("1", out.get(0).key);
        assertEquals(1, out.get(0).alternates.size());
        assertEquals("p1:9", out.get(0).alternates.get(0).key);
        assertEquals("p1:10", out.get(2).key);
    }

    @Test public void oneplaylistsOwnTwinsStayApart() {
        List<Channel> out = Library.merged(Arrays.asList(ch("1", "BBC One"), ch("2", "BBC One")));
        assertEquals(2, out.size());
        assertTrue(out.get(0).alternates.isEmpty());
    }

    @Test public void theBetterPictureIsNotABackupOfTheOther() {
        List<Channel> out = Library.merged(Arrays.asList(ch("1", "Sky Sports Premier League HD"),
                ch("p1:1", "Sky Sports Premier League UHD")));
        assertEquals(2, out.size());
    }

    @Test public void aKeysPlaylistIsItsPrefix() {
        assertEquals("p0", Library.playlistOf("x:123"));
        assertEquals("p0", Library.playlistOf("http://a/b"));
        assertEquals("p0", Library.playlistOf("plain"));
        assertEquals("p2", Library.playlistOf("p2:x:123"));
        assertEquals("p11", Library.playlistOf("p11:abc"));
        assertEquals("p0", Library.playlistOf("pa:abc"));
    }

    @Test public void expiryInWords() {
        long now = java.time.LocalDate.of(2026, 11, 5).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        long in128 = now + 128L * 86_400_000L;
        assertEquals("Runs out 13 Mar 2027 · 128 days left", Sources.expiry(new Sources.Info(in128, "Active", 1, 0, now), now));
        assertEquals("Runs out 6 Nov 2026 · 1 day left", Sources.expiry(new Sources.Info(now + 86_400_000L, "", 0, 0, now), now));
        assertEquals("Doesn't run out", Sources.expiry(new Sources.Info(-1, "", 0, 0, now), now));
        assertEquals("Ran out on 2 Nov 2026", Sources.expiry(new Sources.Info(now - 3L * 86_400_000L, "", 0, 0, now), now));
        assertEquals("", Sources.expiry(new Sources.Info(0, "", 0, 0, now), now));
        assertEquals("", Sources.expiry(null, now));
    }

    @Test public void expiryColours() {
        long now = 1_000_000_000_000L;
        assertEquals(Ui.GREEN, LiveTvSettings.expiryColour(new Sources.Info(-1, "", 0, 0, now), now));
        assertEquals(Ui.GREEN, LiveTvSettings.expiryColour(new Sources.Info(now + 40L * 86_400_000L, "", 0, 0, now), now));
        assertEquals(0xFFFFC93C, LiveTvSettings.expiryColour(new Sources.Info(now + 5L * 86_400_000L, "", 0, 0, now), now));
        assertEquals(Ui.PINK, LiveTvSettings.expiryColour(new Sources.Info(now - 2L * 86_400_000L, "", 0, 0, now), now));
    }

    @Test public void anXtreamFilmIsMarkedWithItsPlaylist() {
        assertEquals("x:movie:1.mkv", Vod.stored("p0", "x:movie:1.mkv"));
        assertEquals("p2|x:movie:1.mkv", Vod.stored("p2", "x:movie:1.mkv"));
        assertEquals("http://a/b.mp4", Vod.stored("p2", "http://a/b.mp4"));
    }

    @Test public void eventChannelsNamedForTheGameAreFound() {
        String[][] game = {AlsoShowing.clubNames("Man United", "Manchester United"), AlsoShowing.clubNames("Arsenal", "Arsenal")};
        assertTrue(AlsoShowing.names(AlsoShowing.words("EPL 03: Manchester United vs Arsenal 17:30"), game));
        assertTrue(AlsoShowing.names(AlsoShowing.words("|UK| Man Utd v Arsenal"), game));
        assertTrue(AlsoShowing.names(AlsoShowing.words("PPV 2 - Arsenal @ Man United"), game));
        // Another club of the same city isn't it.
        assertFalse(AlsoShowing.names(AlsoShowing.words("EPL 04: Manchester City vs Arsenal"), game));
        // A channel that names only one of them isn't it.
        assertFalse(AlsoShowing.names(AlsoShowing.words("Arsenal TV"), game));
        // Nor words two clubs share.
        String[][] city = {AlsoShowing.clubNames("Man City", "Manchester City"), AlsoShowing.clubNames("Leicester", "Leicester City")};
        assertFalse(AlsoShowing.names(AlsoShowing.words("Hull City v Leeds United"), city));
        assertTrue(AlsoShowing.names(AlsoShowing.words("Man City v Leicester"), city));
    }

    @Test public void aClubsOwnWordFindsIt() {
        String[][] game = {AlsoShowing.clubNames("Tottenham", "Tottenham Hotspur"), AlsoShowing.clubNames("Brighton", "Brighton & Hove Albion")};
        assertTrue(AlsoShowing.names(AlsoShowing.words("EVENT 7 | Tottenham Hotspur - Brighton and Hove Albion"), game));
        assertTrue(AlsoShowing.names(AlsoShowing.words("Spurs? no: Tottenham v Brighton"), game));
        String[][] wolves = {AlsoShowing.clubNames("Wolves", "Wolverhampton Wanderers"), AlsoShowing.clubNames("Everton", "Everton FC")};
        assertTrue(AlsoShowing.names(AlsoShowing.words("Wolverhampton v Everton"), wolves));
        assertTrue(AlsoShowing.names(AlsoShowing.words("Wolves vs Everton FC"), wolves));
    }
}
