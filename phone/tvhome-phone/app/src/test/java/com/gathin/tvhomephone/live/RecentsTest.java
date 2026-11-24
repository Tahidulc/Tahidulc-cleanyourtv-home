package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Recently watched (Recents), with invented channels: newest first, each merged channel once (another playlist's copy,
 * a key from another device, the same channel twice), the one playing, hidden ones and ones no longer in the playlists
 * left out, and never more than asked for.
 */
public class RecentsTest {
    private final Map<String, Channel> byKey = new HashMap<>();

    private Channel ch(String key, String name, String group) {
        Channel c = new Channel(key, name, group, "", "http://example.invalid/" + key, "", null, null, 0);
        byKey.put(key, c);
        return c;
    }

    private Function<String, Channel> lookup() {
        return byKey::get;
    }

    private static List<String> names(List<Channel> list) {
        List<String> out = new ArrayList<>();
        for (Channel c : list) out.add(c.name);
        return out;
    }

    @Test public void newestFirstAsTheHistoryHasThem() {
        ch("a", "Northgate One HD", "UK: Entertainment");
        ch("b", "Riverside Two HD", "UK: Entertainment");
        ch("c", "Fieldside Sports Arena", "UK: Sports");
        List<Channel> r = Recents.list(Arrays.asList("c", "a", "b"), lookup(), null, null, 10);
        assertEquals(Arrays.asList("Fieldside Sports Arena", "Northgate One HD", "Riverside Two HD"), names(r));
    }

    @Test public void theChannelPlayingIsLeftOut() {
        ch("a", "Northgate One HD", "UK: Entertainment");
        ch("b", "Riverside Two HD", "UK: Entertainment");
        ch("c", "Harbour News", "UK: News");
        List<Channel> r = Recents.list(Arrays.asList("b", "a", "c"), lookup(), null, "b", 10);
        assertEquals(Arrays.asList("Northgate One HD", "Harbour News"), names(r));
        // Back to: the newest that isn't the one playing.
        assertEquals("Northgate One HD", Recents.last(Arrays.asList("b", "a", "c"), lookup(), null, "b").name);
    }

    @Test public void eachMergedChannelOnce() {
        // The same channel from a second playlist (its own key, the same name and picture), and as written with a
        // country tag: one merged channel, shown once, where it was newest.
        ch("a", "Northgate One HD", "UK: Entertainment");
        ch("p1:77", "UK: Northgate One HD", "Entertainment");
        ch("b", "Riverside Two HD", "UK: Entertainment");
        List<Channel> r = Recents.list(Arrays.asList("p1:77", "b", "a"), lookup(), null, null, 10);
        assertEquals(Arrays.asList("UK: Northgate One HD", "Riverside Two HD"), names(r));
        assertEquals(Recents.identity(byKey.get("a")), Recents.identity(byKey.get("p1:77")));
    }

    @Test public void aKeyThatResolvesToItsMergedChannelIsOneWithIt() {
        // A key from another of the family's devices that the playlists here merged into "a" (Library.resolve).
        ch("a", "Northgate One HD", "UK: Entertainment");
        ch("b", "Riverside Two HD", "UK: Entertainment");
        Function<String, Channel> resolve = k -> "p2:9".equals(k) ? byKey.get("a") : byKey.get(k);
        List<Channel> r = Recents.list(Arrays.asList("p2:9", "b", "a"), resolve, null, null, 10);
        assertEquals(Arrays.asList("Northgate One HD", "Riverside Two HD"), names(r));
        // And it counts as the one playing when "a" is on.
        assertEquals(Arrays.asList("Riverside Two HD"), names(Recents.list(Arrays.asList("p2:9", "b"), resolve, null, "a", 10)));
    }

    @Test public void aChannelInAnotherPictureIsItsOwn() {
        // The 4K version is a channel of its own (the merge keeps the qualities apart): both stay.
        ch("a", "Fieldside Sports Main Event HD", "UK: Sports");
        ch("u", "Fieldside Sports Main Event UHD", "4K UHD");
        List<Channel> r = Recents.list(Arrays.asList("u", "a"), lookup(), null, null, 10);
        assertEquals(2, r.size());
    }

    @Test public void theSameKeyTwiceIsOnce() {
        ch("a", "Northgate One HD", "UK: Entertainment");
        ch("b", "Riverside Two HD", "UK: Entertainment");
        assertEquals(Arrays.asList("Northgate One HD", "Riverside Two HD"),
                names(Recents.list(Arrays.asList("a", "a", "b", "a"), lookup(), null, null, 10)));
    }

    @Test public void goneHiddenAndEmptyKeysAreLeftOut() {
        ch("a", "Northgate One HD", "UK: Entertainment");
        Channel hidden = ch("b", "Riverside Two HD", "UK: Entertainment");
        ch("c", "Harbour News", "UK: News");
        List<Channel> r = Recents.list(Arrays.asList("gone", "", null, "b", "c", "a"), lookup(), c -> c == hidden, null, 10);
        assertEquals(Arrays.asList("Harbour News", "Northgate One HD"), names(r));
    }

    @Test public void neverMoreThanAskedFor() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ch("k" + i, "Channel Number " + (char) ('A' + i % 26) + i, "Group");
            keys.add("k" + i);
        }
        List<Channel> r = Recents.list(keys, lookup(), null, null, Recents.SHOWN);
        assertEquals(Recents.SHOWN, r.size());
        assertEquals("k0", r.get(0).key);
        assertEquals("k9", r.get(Recents.SHOWN - 1).key);
        assertTrue(Recents.list(keys, lookup(), null, null, 0).isEmpty());
    }

    @Test public void nothingToGoBackTo() {
        ch("a", "Northgate One HD", "UK: Entertainment");
        assertNull(Recents.last(Arrays.asList("a"), lookup(), null, "a"));
        assertNull(Recents.last(null, lookup(), null, null));
        assertTrue(Recents.list(Arrays.asList("a"), null, null, null, 5).isEmpty());
    }

    @Test public void aNameWithNoWordsIsItsOwnChannel() {
        // Nothing left of a name once its tags go: kept apart by its key, never merged with another such.
        ch("x", "HD", "Misc");
        ch("y", "4K", "Misc");
        assertEquals(2, Recents.list(Arrays.asList("x", "y"), lookup(), null, null, 10).size());
    }
}
