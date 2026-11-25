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
            names.computeIfAbsent(Names.key(row.name), n -> new ArrayList<>()).add(row);
            numbers.put(row.number, row);
            return this;
        }

        @Override public Channel byKey(String key) {
            return keys.get(key);
        }

        @Override public List<Channel> named(String name) {
            return withKey(Names.key(name));
        }

        @Override public List<Channel> withKey(String key) {
            namesAsked++;
            List<Channel> l = names.get(key);
            return l == null ? Collections.emptyList() : l;
        }

        @Override public java.util.Collection<String> keys() {
            return names.keySet();
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

    @Test public void theNamesInOrderTheOneWantedFirst() {
        // The launcher's CHANNEL_NAMES with its CHANNEL_NAME (the one wanted) first; blanks and repeats out.
        PlayRequest r = PlayRequest.of(PlayRequest.ACTION, null, " Kestrel One UHD ",
                new String[]{"Kestrel One UHD", " ", null, "Kestrel One", "Kestrel One HD"}, 0, NOW, NOW);
        assertNotNull(r);
        assertEquals("", r.key);
        assertEquals("Kestrel One UHD", r.name);
        assertEquals(java.util.Arrays.asList("Kestrel One UHD", "Kestrel One", "Kestrel One HD"), r.names);
        // The names alone are enough (an older launcher's name alone too); at most MOST_NAMES.
        assertEquals("Harbour TV", PlayRequest.of(PlayRequest.ACTION, null, null, new String[]{"Harbour TV"}, 0, 0, NOW).name);
        String[] many = new String[12];
        for (int i = 0; i < many.length; i++) many[i] = "North Sports " + (i + 1);
        assertEquals(PlayRequest.MOST_NAMES, PlayRequest.of(PlayRequest.ACTION, null, null, many, 0, 0, NOW).names.size());
        assertNull(PlayRequest.of(PlayRequest.ACTION, null, null, new String[]{" ", null}, 0, 0, NOW));
        assertEquals("com.gathin.homefieldtv.extra.CHANNEL_NAMES", PlayRequest.EXTRA_NAMES);
    }

    @Test public void foundByTheFirstNameReadTheSameElseALooserReading() {
        Channel one = ch("p0|1", "UK: KESTREL ONE FHD", 101), two = ch("p0|2", "Kestrel Two London", 102),
                harbour = ch("p0|5", "Harbour TV +1", 105);
        Lookups f = new Lookups().with(one).with(two).with(harbour);
        // Tolerantly, at once: case, quality, a UK tag.
        assertSame(one, PlayRequest.of(PlayRequest.ACTION, null, "kestrel one", null, 0, 0, NOW).exact(f));
        // The one wanted missing: the next name that reads the same, before any looser reading of the first.
        assertSame(one, PlayRequest.of(PlayRequest.ACTION, null, "Kestrel Two West", new String[]{"Kestrel One HD"}, 0, 0, NOW).find(f));
        // Nothing reads the same: its region's channel (Kestrel Two London for Kestrel Two), never a +1 for the channel.
        assertSame(two, PlayRequest.of(PlayRequest.ACTION, null, "Kestrel Two", null, 0, 0, NOW).find(f));
        assertNull(PlayRequest.of(PlayRequest.ACTION, null, "Kestrel Two", null, 0, 0, NOW).exact(f));
        assertNull(PlayRequest.of(PlayRequest.ACTION, null, "Harbour TV", null, 0, 0, NOW).find(f));
        // No name found: the number, as before.
        assertSame(harbour, PlayRequest.of(PlayRequest.ACTION, null, "Harbour TV", null, 105, 0, NOW).find(f));
        // A game's own channel by its teams.
        Channel game = ch("p0|9", "Peacock 03: Riverside vs Fogbell", 900);
        f.with(game);
        assertSame(game, PlayRequest.of(PlayRequest.ACTION, null, "match:Riverside|Fogbell", null, 0, 0, NOW).find(f));
        assertEquals("Riverside v Fogbell", PlayRequest.of(PlayRequest.ACTION, null, "match:Riverside|Fogbell", null, 0, 0, NOW).said());
    }

    @Test public void whatANoteCallsIt() {
        assertEquals("Kestrel One", PlayRequest.of(PlayRequest.ACTION, "k", "Kestrel One", 101, 0, NOW).said());
        assertEquals("channel 101", PlayRequest.of(PlayRequest.ACTION, "k", "", 101, 0, NOW).said());
        assertEquals("the channel", PlayRequest.of(PlayRequest.ACTION, "k", "", 0, 0, NOW).said());
    }
}
