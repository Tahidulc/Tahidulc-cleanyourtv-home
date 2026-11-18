package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Shown;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Every list goes by the family's edits (ChannelEdits): a hidden channel is out of all of them (the guide's lists, a
 * group's, channel up and down, the numbers, search, Favourites and Recent), a renamed one is found by both names, and
 * the lists are sorted as Channels sorting and a group's own Sort channels say (by name, by number, manual). Invented
 * channels.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EditedListsTest {
    private Context c;
    private Library lib;
    private List<Channel> chans;

    /** key, name, group, number. */
    private static final String[][] CHANNELS = {
            {"e:1", "Zephyr One", "Alpha", "5"},
            {"e:2", "Amber Two", "Alpha", "3"},
            {"e:3", "Meadow Three", "Alpha", "9"},
            {"e:4", "Beacon Four", "Beta", "1"},
            {"e:5", "Bramble Five", "Beta", "2"},
            {"e:6", "Cobalt Six", "Gamma", "7"},
    };

    @Before public void load() {
        Exec.inline = true;
        c = RuntimeEnvironment.getApplication();
        clear();
        Sources.clear(c);
        Sources.put(c, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "test"));
        chans = new ArrayList<>();
        for (String[] r : CHANNELS) {
            chans.add(new Channel(r[0], r[1], r[2], "", "http://provider.example:8080/live/family/test/" + r[0] + ".ts", "", null, null,
                    Integer.parseInt(r[3])));
        }
        lib = Library.get(c);
        lib.useForPreview(chans, new HashMap<>());
    }

    private void clear() {
        c.getSharedPreferences("channel_edits", Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("live_groups", Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("live_tv", Context.MODE_PRIVATE).edit().clear().commit();
        ChannelEdits.forget();
        Categories.forget();
    }

    @After public void unload() {
        clear();
        Library.get(c).clear();
        Sources.clear(c);
        Exec.inline = false;
    }

    private static List<String> keys(List<Channel> l) {
        List<String> out = new ArrayList<>();
        for (Channel ch : l) out.add(ch.key);
        return out;
    }

    private Channel ch(int i) {
        return chans.get(i - 1);
    }

    @Test public void aHiddenChannelIsOutOfEveryList() {
        lib.toggleFavourite(ch(2));
        lib.watched(ch(2));
        assertTrue(keys(lib.all()).contains("e:2"));
        // Home's On now: the favourites first.
        assertEquals("e:2", lib.onNowAt(System.currentTimeMillis()).channels.get(0).key);
        ChannelEdits.setHidden(c, "e:2", true);
        assertFalse(keys(lib.all()).contains("e:2"));
        assertEquals(Arrays.asList("e:1", "e:3"), keys(lib.group("Alpha").channels));
        assertEquals(-1, lib.lineup().indexOf(ch(2)));
        // The numbers, channel up and down, search, Favourites and Recent.
        assertNull(lib.numbered(3));
        assertEquals("e:3", lib.step(ch(1), 1).key);
        assertEquals("e:1", lib.step(ch(3), -1).key);
        assertTrue(lib.search("amber", 10).isEmpty());
        assertTrue(lib.list(Library.FAVOURITES, "").isEmpty());
        assertTrue(lib.recent(null).isEmpty());
        assertFalse(lib.listed(ch(2)));
        assertFalse(Shown.listed(c, ch(2)));
        // Nor on Home, a favourite though it is.
        assertFalse(keys(lib.onNowAt(System.currentTimeMillis()).channels).contains("e:2"));
        // Never the channel started by itself (Library.playable).
        assertEquals("e:1", lib.playable(ch(2)).key);
        // Shown again: back where it was.
        ChannelEdits.setHidden(c, "e:2", false);
        assertEquals(Arrays.asList("e:1", "e:2", "e:3"), keys(lib.group("Alpha").channels));
        assertEquals("e:2", lib.numbered(3).key);
        assertEquals(1, lib.list(Library.FAVOURITES, "").size());
    }

    @Test public void aRenamedChannelIsFoundByBothNames() {
        lib.toggleFavourite(ch(4));
        ChannelEdits.rename(c, ch(4), "Lighthouse Sport");
        assertEquals("Lighthouse Sport", ChannelEdits.name(ch(4)));
        assertEquals(Arrays.asList("e:4"), keys(lib.search("lighthouse", 10)));
        assertEquals(Arrays.asList("e:4"), keys(lib.search("beacon", 10)));
        assertEquals(1, lib.list(Library.FAVOURITES, "lighthouse").size());
        assertEquals(1, lib.list(Library.FAVOURITES, "beacon").size());
        // Its playlist's name again: found by that alone.
        ChannelEdits.rename(c, ch(4), "");
        assertTrue(lib.search("lighthouse", 10).isEmpty());
        assertEquals(Arrays.asList("e:4"), keys(lib.search("beacon", 10)));
    }

    @Test public void listsAreSortedAsTheFamilySays() {
        assertEquals(Arrays.asList("e:1", "e:2", "e:3", "e:4", "e:5", "e:6"), keys(lib.all()));
        // By name: the family's own name where given.
        ChannelEdits.rename(c, ch(6), "Apple Six");
        ChannelEdits.setSort(c, ChannelEdits.NAME);
        assertEquals(Arrays.asList("e:2", "e:6", "e:4", "e:5", "e:3", "e:1"), keys(lib.all()));
        assertEquals(Arrays.asList("e:2", "e:3", "e:1"), keys(lib.group("Alpha").channels));
        // Channel up and down go in that order; the numbers still find each one.
        assertEquals("e:6", lib.step(ch(2), 1).key);
        assertEquals("e:1", lib.numbered(5).key);
        assertEquals(1, lib.lineup().indexOf(ch(6)));
        assertEquals(1, lib.lineup().indexOfNumber(7));
        // By number.
        ChannelEdits.setSort(c, ChannelEdits.NUMBER);
        assertEquals(Arrays.asList("e:4", "e:5", "e:2", "e:1", "e:6", "e:3"), keys(lib.all()));
        // A group's own sorting: Alpha by name while the rest go by number.
        Categories.setSort(c, "Alpha", ChannelEdits.NAME);
        assertEquals(Arrays.asList("e:2", "e:3", "e:1"), keys(lib.group("Alpha").channels));
        assertEquals(Arrays.asList("e:4", "e:5"), keys(lib.group("Beta").channels));
        assertEquals(2, lib.group("Alpha").indexOf(ch(1)));
        // The guide's own kinds go in the order all the channels do.
        List<String> kind = keys(lib.genre(Genres.ORDER.get(Math.max(0, lib.index().genre[0]))).channels), inAll = new ArrayList<>();
        for (Channel x : lib.all()) if (kind.contains(x.key)) inAll.add(x.key);
        assertFalse(kind.isEmpty());
        assertEquals(inAll, kind);
    }

    @Test public void manualIsTheOrderMoveChannelLeft() {
        // Move channel in Alpha: each one's place kept, Alpha then sorted Manual.
        ChannelEdits.order(c, Arrays.asList("e:3", "e:1", "e:2"));
        Categories.setSort(c, "Alpha", ChannelEdits.MANUAL);
        assertEquals(Arrays.asList("e:3", "e:1", "e:2"), keys(lib.group("Alpha").channels));
        assertEquals(0, lib.group("Alpha").indexOf(ch(3)));
        // All the channels still in the playlist's order (Channels sorting as it comes).
        assertEquals(Arrays.asList("e:1", "e:2", "e:3", "e:4", "e:5", "e:6"), keys(lib.all()));
        // Manual for all of them: the groups in the family's order, each in its own order.
        Categories.placeShown(c, lib.groups(), Arrays.asList("Gamma", "Alpha", "Beta"));
        ChannelEdits.setSort(c, ChannelEdits.MANUAL);
        assertEquals(Arrays.asList("e:6", "e:3", "e:1", "e:2", "e:4", "e:5"), keys(lib.all()));
        // A channel the family never placed comes after the placed ones, in the playlist's order.
        ChannelEdits.order(c, Arrays.asList("e:5"));
        assertEquals(Arrays.asList("e:5", "e:4"), keys(lib.group("Beta").channels));
    }

    @Test public void favouritesInTheirNewOrder() {
        lib.toggleFavourite(ch(1));
        lib.toggleFavourite(ch(4));
        lib.toggleFavourite(ch(6));
        lib.orderFavourites(Arrays.asList("e:6", "e:1", "e:4"));
        assertEquals(Arrays.asList("e:6", "e:1", "e:4"), lib.favourites());
        assertEquals(Arrays.asList("e:6", "e:1", "e:4"), keys(lib.list(Library.FAVOURITES, "")));
    }
}
