package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** "Play this channel" from the launcher: its action and extras read, and the channel found by key, else name, else number. */
public class PlayRequestTest {
    private static final long NOW = 1_800_000_000_000L;

    private static Channel ch(String key, String name, int number) {
        return new Channel(key, name, "Entertainment", "", "http://provider.example/" + key + ".ts", "", null, null, number);
    }

    /** A made-up library's lookups, counting how often a name was looked for (that one reads every name). */
    private static final class Lookups implements PlayRequest.Finder {
        final Map<String, Channel> keys = new HashMap<>();
        final Map<String, List<Channel>> names = new HashMap<>();
        final Map<Integer, Channel> numbers = new HashMap<>();
        int namesAsked;

        Lookups with(Channel row, String... otherKeys) {
            keys.put(row.key, row);
            for (String k : otherKeys) keys.put(k, row);
            names.computeIfAbsent(row.name, n -> new ArrayList<>()).add(row);
            numbers.put(row.number, row);
            return this;
        }

        @Override public Channel byKey(String key) {
            return keys.get(key);
        }

        @Override public List<Channel> named(String name) {
            namesAsked++;
            List<Channel> l = names.get(name);
            return l == null ? Collections.emptyList() : l;
        }

        @Override public Channel numbered(int number) {
            return numbers.get(number);
        }
    }

    @Test public void readFromTheIntent() {
        PlayRequest r = PlayRequest.of(PlayRequest.ACTION, " p0|42 ", " Kestrel One ", 101, NOW - 5_000L, NOW);
        assertNotNull(r);
        assertEquals("p0|42", r.key);
        assertEquals("Kestrel One", r.name);
        assertEquals(101, r.number);
        // Any one of the three is enough; a number below one is none.
        assertEquals("Kestrel One", PlayRequest.of(PlayRequest.ACTION, null, "Kestrel One", 0, 0, NOW).name);
        assertEquals(7, PlayRequest.of(PlayRequest.ACTION, "", "", 7, 0, NOW).number);
        assertEquals(0, PlayRequest.of(PlayRequest.ACTION, "k", null, -3, 0, NOW).number);
        // Another action, nothing to go by, or asked for long ago (an intent given again when the app is restored): none.
        assertNull(PlayRequest.of("android.intent.action.MAIN", "p0|42", "Kestrel One", 101, 0, NOW));
        assertNull(PlayRequest.of(null, "p0|42", "", 0, 0, NOW));
        assertNull(PlayRequest.of(PlayRequest.ACTION, " ", null, 0, 0, NOW));
        assertNull(PlayRequest.of(PlayRequest.ACTION, "p0|42", "", 0, NOW - PlayRequest.FRESH_MS - 1, NOW));
        assertNotNull(PlayRequest.of(PlayRequest.ACTION, "p0|42", "", 0, NOW - PlayRequest.FRESH_MS + 1_000L, NOW));
        // The names the launcher sends, as the manifest has them.
        assertEquals("com.gathin.homefieldtv.action.PLAY_CHANNEL", PlayRequest.ACTION);
        assertEquals("com.gathin.homefieldtv.extra.CHANNEL_KEY", PlayRequest.EXTRA_KEY);
        assertEquals("com.gathin.homefieldtv.extra.CHANNEL_NAME", PlayRequest.EXTRA_NAME);
        assertEquals("com.gathin.homefieldtv.extra.CHANNEL_NUMBER", PlayRequest.EXTRA_NUMBER);
    }

    @Test public void foundByKeyFirstThenNameThenNumber() {
        Channel one = ch("p0|1", "Kestrel One", 101), two = ch("p0|2", "Kestrel Two", 102);
        Lookups f = new Lookups().with(one, "p0|1sd").with(two);
        // The key, a version's key finding its row: no name read.
        assertSame(one, PlayRequest.of(PlayRequest.ACTION, "p0|1sd", "Kestrel Two", 102, 0, NOW).find(f));
        assertEquals(0, f.namesAsked);
        // A key gone (the playlist read again): by name, before a number that may now be another channel's.
        assertSame(two, PlayRequest.of(PlayRequest.ACTION, "p0|gone", "Kestrel Two", 101, 0, NOW).find(f));
        assertEquals(1, f.namesAsked);
        // By number when the name isn't there either.
        assertSame(one, PlayRequest.of(PlayRequest.ACTION, "p0|gone", "Old name", 101, 0, NOW).find(f));
        assertNull(PlayRequest.of(PlayRequest.ACTION, "p0|gone", "Old name", 999, 0, NOW).find(f));
        // byKey alone never reads the names (at once, on the main thread).
        int asked = f.namesAsked;
        assertNull(PlayRequest.of(PlayRequest.ACTION, "", "Kestrel One", 0, 0, NOW).byKey(f));
        assertEquals(asked, f.namesAsked);
    }

    @Test public void whatANoteCallsIt() {
        assertEquals("Kestrel One", PlayRequest.of(PlayRequest.ACTION, "k", "Kestrel One", 101, 0, NOW).said());
        assertEquals("channel 101", PlayRequest.of(PlayRequest.ACTION, "k", "", 101, 0, NOW).said());
        assertEquals("the channel", PlayRequest.of(PlayRequest.ACTION, "k", "", 0, 0, NOW).said());
    }
}
