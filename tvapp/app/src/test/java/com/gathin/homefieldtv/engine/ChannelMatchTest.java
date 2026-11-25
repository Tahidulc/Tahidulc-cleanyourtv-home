package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The launcher's names for a channel read tolerantly in this TV's playlist (ChannelMatch): case, quality tags, a UK tag,
 * "&" and "and", spacing and punctuation all the same; a +1 never the channel itself; a region only when nothing reads the
 * same; a game's own channel by its teams; the names in order, but a closer reading always before a looser one, and a
 * wrong channel never. Invented playlists.
 */
public class ChannelMatchTest {
    /** A playlist's rows by their names, in its order. */
    private static final class Playlist implements ChannelMatch.Channels<String> {
        final Map<String, List<String>> byKey = new LinkedHashMap<>();
        int keysAsked;

        Playlist(String... names) {
            for (String n : names) byKey.computeIfAbsent(Names.key(n), k -> new ArrayList<>()).add(n);
        }

        @Override public List<String> withKey(String key) {
            List<String> l = byKey.get(key);
            return l == null ? new ArrayList<>() : l;
        }

        @Override public Collection<String> keys() {
            keysAsked++;
            return byKey.keySet();
        }

        @Override public String name(String row) {
            return row;
        }
    }

    private static final Playlist UK = new Playlist("UK: BBC One London HD", "UK: BBC Two HD", "UK: ITV 1 HD", "UK: ITV1 +1",
            "Channel 4 HD", "UK: CHANNEL 5 FHD", "UK: Sky Sports Main Event FHD", "UK: Sky Sports Main Event UHD",
            "UK: SKY SPORTS PREMIER LEAGUE HD", "TNT Sport 1 ᴴᴰ", "UK: Sky Arts & Culture", "Kestrel One", "Kestrel One +1",
            "US: Fogbell News HD", "UK: Fogbell News", "Peacock 03: Elversberg vs Mainz 19:30", "Sky Sports News SD",
            "Sky Sports News HD BACKUP");

    private static String best(String... names) {
        return ChannelMatch.best(Arrays.asList(names), UK);
    }

    @Test public void caseQualityCountryPunctuationAndAmpersandReadTheSame() {
        assertEquals("UK: BBC Two HD", best("bbc two"));
        assertEquals("UK: BBC Two HD", best("BBC 2"));
        assertEquals("UK: ITV 1 HD", best("ITV1"));
        assertEquals("UK: ITV 1 HD", best("ITV"));
        assertEquals("Channel 4 HD", best("Channel4"));
        assertEquals("UK: CHANNEL 5 FHD", best("5"));
        assertEquals("UK: SKY SPORTS PREMIER LEAGUE HD", best("Sky Sport Premier-League"));
        assertEquals("TNT Sport 1 ᴴᴰ", best("TNT Sports 1"));
        assertEquals("TNT Sport 1 ᴴᴰ", best("BT Sport 1 HD"));
        assertEquals("UK: Sky Arts & Culture", best("Sky Arts and Culture"));
        assertEquals("UK: Sky Arts & Culture", best("SKY ARTS AND CULTURE UK"));
    }

    @Test public void aPlusOneIsAnotherChannel() {
        assertEquals("Kestrel One", best("Kestrel One"));
        assertEquals("Kestrel One +1", best("Kestrel One +1"));
        assertEquals("UK: ITV1 +1", best("ITV1 +1"));
        // A +1 the playlist hasn't: none, never the channel itself an hour ahead.
        assertNull(best("Channel 4 +1"));
    }

    @Test public void thePictureTheNameAsksForElseTheBestBritishOneNoBackup() {
        assertEquals("UK: Sky Sports Main Event UHD", best("Sky Sports Main Event UHD"));
        assertEquals("UK: Sky Sports Main Event FHD", best("Sky Sports Main Event HD"));
        assertEquals("UK: Sky Sports Main Event UHD", best("Sky Sports Main Event"));
        assertEquals("UK: Fogbell News", best("Fogbell News"));
        assertEquals("Sky Sports News SD", best("Sky Sports News"));
    }

    @Test public void theNamesInOrderButCloserBeforeLooser() {
        // The first name that reads the same wins.
        assertEquals("UK: Sky Sports Main Event UHD", best("Sky Sports Main Event UHD", "Sky Sports Premier League"));
        assertEquals("UK: SKY SPORTS PREMIER LEAGUE HD", best("No Such Channel", "Sky Sports Premier League"));
        // A region only when nothing reads the same: "BBC One" is BBC One London; a later name reading the same comes first.
        assertEquals("UK: BBC One London HD", best("BBC One"));
        assertEquals("UK: BBC Two HD", best("BBC One South", "BBC Two"));
        assertEquals("UK: BBC One London HD", best("BBC One South"));
    }

    @Test public void aGameByItsTeams() {
        assertEquals("Peacock 03: Elversberg vs Mainz 19:30", best("match:Elversberg|Mainz"));
        assertEquals("Peacock 03: Elversberg vs Mainz 19:30", best("match:Mainz|Elversberg", "No Such Channel"));
        // A channel of its own for the game before the game's own event channel? The channel reading the same first.
        assertEquals("UK: Sky Sports Main Event UHD", best("match:Elversberg|Mainz", "Sky Sports Main Event"));
        assertNull(best("match:Elversberg|Hoffenheim"));
        assertNull(best("match:Elversberg"));
    }

    @Test public void neverAWrongChannel() {
        assertNull(best("Sky Sports Main Event 2"));
        assertNull(best("TNT Sports 2"));
        assertNull(best("Kestrel Two"));
        assertNull(best("BBC Three"));
        // A streaming service is not a channel.
        assertNull(best("Amazon Prime Video"));
        assertNull(best("", " ", null));
        assertNull(ChannelMatch.best(null, UK));
    }

    @Test public void theSameNameAtOnceWithoutLookingAtEveryName() {
        Playlist p = new Playlist("Kestrel One HD", "Harbour TV");
        assertEquals("Harbour TV", ChannelMatch.exact(Arrays.asList("Harbour TV HD", "Kestrel One"), p));
        assertEquals("Kestrel One HD", ChannelMatch.exact(Arrays.asList("No Such Channel", "kestrel one"), p));
        assertNull(ChannelMatch.exact(Arrays.asList("Harbour TV West"), p));
        assertEquals(0, p.keysAsked);
        // best reads every name's key only when nothing reads the same.
        assertEquals("Harbour TV", ChannelMatch.best(Arrays.asList("Harbour TV West"), p));
        assertEquals(1, p.keysAsked);
    }

    @Test public void howClose() {
        assertEquals(ChannelMatch.EXACT, ChannelMatch.closeness("BBC One HD", Names.key("UK: BBC 1")));
        assertEquals(ChannelMatch.REGION, ChannelMatch.closeness("BBC One", Names.key("BBC One Yorkshire")));
        assertEquals(ChannelMatch.REGION, ChannelMatch.closeness("BBC One Wales", Names.key("BBC One")));
        assertEquals(ChannelMatch.GAME, ChannelMatch.closeness("match:Riverside|Fogbell", Names.key("02: Riverside v Fogbell")));
        assertEquals(ChannelMatch.NONE, ChannelMatch.closeness("BBC One", Names.key("BBC One +1")));
        assertEquals(ChannelMatch.NONE, ChannelMatch.closeness("Sky Sports", Names.key("Sky Sports Main Event")));
    }
}
