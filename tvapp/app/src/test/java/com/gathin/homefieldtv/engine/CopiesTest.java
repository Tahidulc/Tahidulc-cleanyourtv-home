package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * The copy each film or episode was started on, kept for Resume without its address; the one chosen first, the rest in
 * their order. Made-up addresses only.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CopiesTest {
    private static Catalog.Play play(String url, String playlist, boolean torbox) {
        Catalog.Play p = new Catalog.Play(url, null, null);
        p.playlist = playlist;
        p.torbox = torbox;
        return p;
    }

    @Test public void knownAgainWithoutTheAddress() {
        Catalog.Play tb = play("torbox:torrent:12:3", "", true);
        Catalog.Play mine = play("http://provider.example:8080/movie/family/made-up-password/101.mkv", "Home", false);
        assertEquals("torbox:torrent:12:3", Copies.id(tb));
        String id = Copies.id(mine);
        assertTrue(id.startsWith("iptv:"));
        assertFalse(id.contains("provider.example"));
        assertFalse(id.contains("made-up-password"));
        assertFalse(id.contains("101"));
        // Another playlist with the same address is another copy.
        assertNotEquals(id, Copies.id(play(mine.url, "Spare", false)));
        assertEquals(1, Copies.indexOf(id, Arrays.asList(tb, mine)));
        assertEquals(-1, Copies.indexOf("iptv:nothing", Arrays.asList(tb, mine)));
    }

    @Test public void theOneChosenFirstTheRestInTheirOrder() {
        List<String> plays = Arrays.asList("a", "b", "c", "d");
        assertEquals(Arrays.asList("c", "a", "b", "d"), Copies.chosenFirst(plays, 2));
        assertEquals(plays, Copies.chosenFirst(plays, 0));
        assertEquals(plays, Copies.chosenFirst(plays, 9));
    }

    @Test public void resumeFindsTheCopyItWasStartedOn() {
        Context c = RuntimeEnvironment.getApplication();
        Copies.clear(c);
        Catalog.Play tb = play("torbox:torrent:12:3", "", true);
        Catalog.Play mine = play("http://provider.example/movie/1.mkv", "Home", false);
        Catalog.Play spare = play("http://provider.example/movie/2.mkv", "Spare", false);
        Copies.started(c, "p0|s|901", "901-1-3", spare);
        assertEquals(2, Copies.find(c, "p0|s|901", "901-1-3", Arrays.asList(tb, mine, spare)));
        // Another episode, or the copy gone: none.
        assertEquals(-1, Copies.find(c, "p0|s|901", "901-1-4", Arrays.asList(tb, mine, spare)));
        assertEquals(-1, Copies.find(c, "p0|s|901", "901-1-3", Arrays.asList(tb, mine)));
        // Remove everything forgets them.
        Copies.clear(c);
        assertEquals(-1, Copies.find(c, "p0|s|901", "901-1-3", Arrays.asList(tb, mine, spare)));
    }
}
