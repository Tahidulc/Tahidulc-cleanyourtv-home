package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HiddenTest {
    private static Channel ch(String key, String name, String tvgId, String url) {
        return new Channel(key, name, "UK", "", url, tvgId, "", "", 0);
    }

    @Test public void keysLastAndTellCopiesApart() {
        Channel hd = ch("bbc1.uk|BBC One HD", "BBC One HD", "bbc1.uk", "http://x/u/p/1.ts");
        Channel sd = ch("bbc1.uk|BBC One SD", "BBC One SD", "bbc1.uk", "http://x/u/p/2.ts");
        // The guide id with the name: the SD copy isn't the HD copy, though they share the id.
        assertEquals("id:bbc1.uk|bbc one hd", Hidden.key(hd));
        assertNotEquals(Hidden.key(hd), Hidden.key(sd));
        // The same channel after a refresh (a new address, the same id and name): the same key.
        assertEquals(Hidden.key(hd), Hidden.key(ch("other", "BBC  One HD ", "BBC1.UK", "http://y/new/9.m3u8")));
        // No tvg-id: the plain name and a hash of the address (never the address itself, which can hold the login).
        Channel a = ch("|Film4", "ᶠⁱˡᵐ4", "", "http://host/user/secret/77.ts");
        String k = Hidden.key(a);
        assertTrue(k.startsWith("nm:"));
        assertFalse(k.contains("secret"));
        assertFalse(k.contains("host"));
        assertEquals(k, Hidden.key(ch("|Film4", "ᶠⁱˡᵐ4", "", "http://host/user/secret/77.ts")));
        assertNotEquals(k, Hidden.key(ch("|Film4", "ᶠⁱˡᵐ4", "", "http://host/user/secret/78.ts")));
        // The hash is the same on every run (FNV-1a, not the JVM's own).
        assertEquals(0xcbf29ce484222325L, Hidden.fnv(""));
        assertEquals(Hidden.fnv("abc"), Hidden.fnv("abc"));
        assertNotEquals(Hidden.fnv("abc"), Hidden.fnv("abd"));
    }

    @Test public void hiddenChannelsAreLeftOut() {
        List<Channel> all = new ArrayList<>();
        for (int i = 0; i < 6; i++) all.add(ch("k" + i, "Channel " + i, i % 2 == 0 ? "c" + i + ".uk" : "", "http://x/" + i));
        Set<String> hidden = new HashSet<>(Arrays.asList(Hidden.key(all.get(1)), Hidden.key(all.get(4)), "id:gone|left the playlist"));
        List<Channel> shown = Hidden.visible(all, hidden);
        assertEquals(Arrays.asList(all.get(0), all.get(2), all.get(3), all.get(5)), shown);
        assertEquals(all, Hidden.visible(all, new HashSet<>()));
        assertEquals(all, Hidden.visible(all, null));
        String[] keys = new String[all.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = Hidden.key(all.get(i));
        assertArrayEquals(new boolean[]{false, true, false, false, true, false}, Hidden.mask(keys, hidden));
        assertArrayEquals(new boolean[6], Hidden.mask(keys, new HashSet<>()));
    }

    @Test public void swipingPassesOverHiddenChannels() {
        boolean[] hid = {false, true, false, true, false};
        // Next from 0 skips 1; next from 2 skips 3; next from 4 goes round to 0.
        assertEquals(2, Hidden.step(5, 0, 1, i -> hid[i]));
        assertEquals(4, Hidden.step(5, 2, 1, i -> hid[i]));
        assertEquals(0, Hidden.step(5, 4, 1, i -> hid[i]));
        // Back from 0 goes round to 4; back from 2 skips 1.
        assertEquals(4, Hidden.step(5, 0, -1, i -> hid[i]));
        assertEquals(0, Hidden.step(5, 2, -1, i -> hid[i]));
        // Two at a time.
        assertEquals(4, Hidden.step(5, 0, 2, i -> hid[i]));
        // From a hidden channel (hidden while it played): on to the next shown one.
        assertEquals(2, Hidden.step(5, 1, 1, i -> hid[i]));
        // From outside the list: the first (or last) shown one.
        assertEquals(0, Hidden.step(5, -1, 1, i -> hid[i]));
        assertEquals(4, Hidden.step(5, -1, -1, i -> hid[i]));
        // Nothing hidden: plain steps.
        assertEquals(1, Hidden.step(5, 0, 1, null));
        assertEquals(4, Hidden.step(5, 0, -1, i -> false));
    }

    @Test public void swipingWithOneOrNoneLeft() {
        boolean[] one = {true, false, true};
        assertEquals(1, Hidden.step(3, 1, 1, i -> one[i]));
        assertEquals(1, Hidden.step(3, 1, -1, i -> one[i]));
        assertEquals(1, Hidden.step(3, 0, 1, i -> one[i]));
        assertEquals(-1, Hidden.step(3, 0, 1, i -> true));
        assertEquals(-1, Hidden.step(0, 0, 1, i -> false));
        assertEquals(2, Hidden.step(3, 2, 0, i -> false));
    }
}
