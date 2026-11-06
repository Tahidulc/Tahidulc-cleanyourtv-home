package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
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

}
