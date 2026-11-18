package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which channels are one channel's quality versions (Versions, one row per channel): by the guide id they share, else by
 * the name without its quality and country tags, as LogoMatch reads it; never across categories, never a +1, another
 * number, a region or a channel with a guide id of its own. Best first: 4K, FHD, HD, a name saying none, SD; ties in
 * the playlist's order. Invented and the family's own examples; pure (no Android).
 */
public class VersionsTest {
    private static Channel ch(String key, String name, String group, String tvgId) {
        return new Channel(key, name, group, "", "http://provider.invalid/" + key, tvgId, null, null, 0);
    }

    private static Channel ch(String key, String name) {
        return ch(key, name, "UK Entertainment", "");
    }

    /** The names of each channel's versions, best first ("" for one in one version), for the places asked. */
    private static List<String> names(List<Channel> all, int[][] groups, int i) {
        List<String> out = new ArrayList<>();
        if (groups[i] == null) return out;
        for (int p : groups[i]) out.add(all.get(p).name);
        return out;
    }

    @Test public void theFamilysExamplesAreOneRowEach() {
        List<Channel> all = Arrays.asList(ch("a", "ITV 1 HD"), ch("b", "ITV 1 SD"), ch("c", "ITV 1 FHD"), ch("d", "CHANNEL 4 FHD"),
                ch("e", "CHANNEL 4 HD"), ch("f", "BBC TWO SD"));
        int[][] g = Versions.group(all);
        // ITV 1's three, best first; one array for all of them.
        assertEquals(Arrays.asList("ITV 1 FHD", "ITV 1 HD", "ITV 1 SD"), names(all, g, 0));
        assertSame(g[0], g[1]);
        assertSame(g[0], g[2]);
        assertEquals(Arrays.asList("CHANNEL 4 FHD", "CHANNEL 4 HD"), names(all, g, 3));
        // BBC Two comes in one: no versions.
        assertNull(g[5]);
        assertNull(all.get(5).versions);
        // Each version keeps them, with the channel's name without its quality.
        assertSame(all.get(0).versions, all.get(2).versions);
        assertEquals("ITV 1", all.get(0).versions.title);
        assertEquals("CHANNEL 4", all.get(4).versions.title);
        assertSame(all.get(2), all.get(1).versions.all[0]);
    }

    @Test public void countryTagsAndQualityMarksAreLeftOut() {
        List<Channel> all = Arrays.asList(ch("a", "UK: ITV 1 FHD"), ch("b", "|UK| ITV1 HD+"), ch("c", "[UK] ITV 1 SD"),
                ch("d", "ITV 1 UHD"), ch("e", "UK: SKY SPORTS F1 HD HEVC"), ch("f", "Sky Sports F1 FHD 50FPS"),
                ch("g", "SKY SPORTS F1 H.265"), ch("h", "Sky Sports F1 ᴴᴰ"));
        int[][] g = Versions.group(all);
        assertEquals(Arrays.asList("ITV 1 UHD", "UK: ITV 1 FHD", "|UK| ITV1 HD+", "[UK] ITV 1 SD"), names(all, g, 0));
        // HEVC, 50 FPS and H.265 say nothing of its picture: FHD, then the two HDs in the playlist's order, then the one
        // that says none.
        assertEquals(Arrays.asList("Sky Sports F1 FHD 50FPS", "UK: SKY SPORTS F1 HD HEVC", "Sky Sports F1 ᴴᴰ", "SKY SPORTS F1 H.265"),
                names(all, g, 4));
    }

    @Test public void aTimeshiftARegionAndAnotherNumberAreOtherChannels() {
        List<Channel> all = Arrays.asList(ch("a", "ITV 1 HD"), ch("b", "ITV 1 +1 HD"), ch("c", "ITV 1+1 SD"), ch("d", "ITV 1 SD"),
                ch("e", "BBC One HD"), ch("f", "BBC One Wales HD"), ch("g", "BBC One SD"), ch("h", "BBC One Wales SD"),
                ch("i", "Sky Sports 1 HD"), ch("j", "Sky Sports 2 HD"), ch("k", "Sky Sports 1 SD"), ch("l", "ITV 1 London HD"));
        int[][] g = Versions.group(all);
        assertEquals(Arrays.asList("ITV 1 HD", "ITV 1 SD"), names(all, g, 0));
        // The +1s are one channel of their own (in two qualities), never ITV 1's.
        assertEquals(Arrays.asList("ITV 1 +1 HD", "ITV 1+1 SD"), names(all, g, 1));
        assertNotEquals(Versions.key("ITV 1 HD"), Versions.key("ITV 1 +1 HD"));
        assertEquals(Arrays.asList("BBC One HD", "BBC One SD"), names(all, g, 4));
        assertEquals(Arrays.asList("BBC One Wales HD", "BBC One Wales SD"), names(all, g, 5));
        assertEquals(Arrays.asList("Sky Sports 1 HD", "Sky Sports 1 SD"), names(all, g, 8));
        assertNull(g[9]);
        assertNull(g[11]);
    }

    @Test public void byTheGuideIdTheyShareNeverTwoIds() {
        List<Channel> all = Arrays.asList(ch("a", "BBC Two HD", "UK", "BBCTwo.uk"), ch("b", "BBC Two", "UK", "bbctwo.uk"),
                ch("c", "BBC Four HD", "UK", "BBCFourHD.uk"), ch("d", "BBC Four", "UK", "BBCFour.uk"),
                ch("e", "ITV 2 HD", "UK", "ITV2.uk"), ch("f", "ITV 2 SD", "UK", ""), ch("g", "E4 SD", "UK", ""), ch("h", "E4 HD", "UK", "E4.uk"));
        int[][] g = Versions.group(all);
        // The same id (as the guide reads them, any case): one channel.
        assertEquals(Arrays.asList("BBC Two HD", "BBC Two"), names(all, g, 0));
        // An HD twin with an id of its own is a channel of its own.
        assertNull(g[2]);
        assertNull(g[3]);
        // One without an id joins the one with it, either way round.
        assertEquals(Arrays.asList("ITV 2 HD", "ITV 2 SD"), names(all, g, 4));
        assertEquals(Arrays.asList("E4 HD", "E4 SD"), names(all, g, 6));
    }

    @Test public void neverAcrossCategories() {
        List<Channel> all = Arrays.asList(ch("a", "ITV 1 HD", "UK Entertainment", ""), ch("b", "ITV 1 SD", "UK Regional", ""),
                ch("c", "ITV 1 FHD", "UK Entertainment", ""));
        int[][] g = Versions.group(all);
        assertEquals(Arrays.asList("ITV 1 FHD", "ITV 1 HD"), names(all, g, 0));
        assertNull(g[1]);
    }

    @Test public void bestFirstTiesInThePlaylistsOrder() {
        assertEquals(Versions.UHD, Versions.level("Sky Sports Main Event 4K"));
        assertEquals(Versions.UHD, Versions.level("Sky Sports Main Event Ultra HD"));
        assertEquals(Versions.FHD, Versions.level("ITV 1 FHD"));
        assertEquals(Versions.FHD, Versions.level("ITV 1 1080p"));
        assertEquals(Versions.HD, Versions.level("ITV 1 HD+"));
        assertEquals(Versions.HD, Versions.level("ITV 1 ᴴᴰ"));
        assertEquals(Versions.PLAIN, Versions.level("ITV 1"));
        assertEquals(Versions.PLAIN, Versions.level("ITV 1 HEVC"));
        assertEquals(Versions.SD, Versions.level("ITV 1 SD"));
        List<Channel> all = Arrays.asList(ch("a", "ITV 1 SD"), ch("b", "ITV 1 HD"), ch("c", "ITV 1"), ch("d", "ITV 1 HD Backup"),
                ch("e", "ITV 1 4K"));
        int[][] g = Versions.group(all);
        assertArrayEquals(new int[]{4, 1, 3, 2, 0}, g[0]);
        assertEquals("4K", Versions.badge(Versions.UHD));
        assertEquals("FHD", Versions.badge(Versions.FHD));
        assertEquals("", Versions.badge(Versions.PLAIN));
    }

    @Test public void theNameWithoutItsQuality() {
        assertEquals("UK: ITV 1", Versions.title("UK: ITV 1 FHD"));
        assertEquals("BBC TWO", Versions.title("BBC TWO (HD)"));
        assertEquals("BBC TWO", Versions.title("BBC TWO [SD]"));
        assertEquals("Sky Sports F1", Versions.title("Sky Sports F1 ᴴᴰ"));
        assertEquals("ITV 1", Versions.title("ITV 1 HD+"));
        assertEquals("ITV 1", Versions.title("ITV 1 | FHD"));
        assertEquals("Sky Sports F1", Versions.title("Sky Sports F1 H.265 50 FPS"));
        assertEquals("Channel 4 +1", Versions.title("Channel 4 +1 HD"));
        assertEquals("Kestrel Sports", Versions.title("Kestrel Sports"));
        // Nothing but a tag: the name itself.
        assertEquals("HD", Versions.title("HD"));
        // Aliases as LogoMatch has them: ITV is ITV1, C4 is Channel 4.
        assertEquals(Versions.key("ITV HD"), Versions.key("ITV1 SD"));
        assertEquals(Versions.key("C4 HD"), Versions.key("Channel 4"));
        // A "+" after a quality tag is a mark, not a timeshift.
        assertEquals("ITV 1 HD", Versions.unplus("ITV 1 HD+"));
        assertEquals("ITV 1 HD+1", Versions.unplus("ITV 1 HD+1"));
        assertEquals("Sky Sports+", Versions.unplus("Sky Sports+"));
    }
}
