package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.R;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Homefield TV's customisation on the phone: programme times, the colour theme, Open on (and the switch it was), one
 * row per channel (which channels are versions of one, the name the row shows, the version shown), the family's own
 * names and sortings, and the categories' own order. Invented channels throughout.
 */
public class SettingsParityTest {
    private static Channel ch(String key, String name, String group, String tvgId) {
        return new Channel(key, name, group, "", "http://example.invalid/" + key, tvgId, null, null, 0);
    }

    private static Names.Info[] infos(List<Channel> l) {
        Names.Info[] in = new Names.Info[l.size()];
        for (int i = 0; i < in.length; i++) in[i] = Names.info(l.get(i).playlistName, l.get(i).group);
        return in;
    }

    // ---- programme times ----------------------------------------------------------------------------------------

    @Test public void programmeTimesIn24HourAnd12Hour() {
        assertEquals("19:40", Times.hm(19, 40, true));
        assertEquals("07:05", Times.hm(7, 5, true));
        assertEquals("00:00", Times.hm(0, 0, true));
        assertEquals("7.40pm", Times.hm(19, 40, false));
        assertEquals("12.00am", Times.hm(0, 0, false));
        assertEquals("12.30pm", Times.hm(12, 30, false));
        assertEquals("11.59pm", Times.hm(23, 59, false));
        assertEquals("9.05am", Times.hm(9, 5, false));
    }

    @Test public void theGuideWritesTimesIntoItsOwnBuffer() {
        char[] buf = new char[24];
        int end = Times.write(buf, 3, 10, 30, false);
        assertEquals("10.30am", new String(buf, 3, end - 3));
        end = Times.write(buf, 0, 10, 30, true);
        assertEquals("10:30", new String(buf, 0, end));
    }

    // ---- the colour theme ---------------------------------------------------------------------------------------

    @Test public void homefieldAmberIsThePhonesOwnLook() {
        assertEquals("Homefield • Amber", Theme.name(Theme.HOMEFIELD, Theme.AMBER));
        assertArrayEquals(new int[]{Theme.GLASS_GROUND, Theme.TV_GROUND, Theme.PAGE_GROUND}, Theme.grounds(Theme.HOMEFIELD));
        assertArrayEquals(new int[]{0xBD24242A, 0xD6121216, 0xEB24242A, 0xF2121216}, Theme.panels(Theme.HOMEFIELD));
        assertEquals(0xFFFFB547, Theme.ACCENTS[Theme.AMBER].colour);
    }

    @Test public void aTintedPrimaryColoursEveryGroundAndThePanels() {
        int indigo = 0;
        assertEquals("Indigo • Blue", Theme.name(indigo, 4));
        int g = Theme.PRIMARIES[indigo].ground;
        assertArrayEquals(new int[]{g, g, g}, Theme.grounds(indigo));
        int[] p = Theme.panels(indigo);
        assertEquals(0xBD000000 | (Theme.PRIMARIES[indigo].header & 0xFFFFFF), p[0]);
        assertEquals(0xF2000000 | (Theme.PRIMARIES[indigo].playback & 0xFFFFFF), p[3]);
    }

    @Test public void aDiscPicksItsHalfOfThePairAndOutOfRangeGoesHome() {
        assertArrayEquals(new int[]{3, Theme.AMBER}, Theme.pick(true, 3, Theme.HOMEFIELD, Theme.AMBER));
        assertArrayEquals(new int[]{Theme.HOMEFIELD, 7}, Theme.pick(false, 7, Theme.HOMEFIELD, Theme.AMBER));
        assertEquals(Theme.HOMEFIELD, Theme.clampP(99));
        assertEquals(Theme.AMBER, Theme.clampA(-1));
        assertEquals(12, Theme.PRIMARIES.length);
        assertEquals(13, Theme.ACCENTS.length);
    }

    // ---- Open on ------------------------------------------------------------------------------------------------

    @Test public void openOnLiveTvAsItCameAndTheOldSwitchCarriedOver() {
        assertEquals(StartOn.LIVE, StartOn.choice(null, null));
        assertEquals(StartOn.LIVE, StartOn.choice(null, true));
        assertEquals(StartOn.HOME, StartOn.choice(null, false));
        assertEquals(StartOn.WATCH, StartOn.choice(StartOn.WATCH, false));
        assertEquals(StartOn.SPORTS, StartOn.choice(StartOn.SPORTS, true));
        assertEquals(StartOn.LIVE, StartOn.choice(42, false));
        assertEquals(0, StartOn.tabOf(StartOn.HOME));
        assertEquals(R.id.tab_live, StartOn.tabOf(StartOn.LIVE));
        assertEquals(R.id.tab_watch, StartOn.tabOf(StartOn.WATCH));
        assertEquals(R.id.tab_sports, StartOn.tabOf(StartOn.SPORTS));
    }

    // ---- one row per channel ----------------------------------------------------------------------------------------

    @Test public void aChannelsQualitiesInOneCategoryAreOneBestFirst() {
        List<Channel> l = Arrays.asList(
                ch("a", "UK: Harbour One SD", "UK | General", "harbour1.uk"),
                ch("b", "UK: Harbour One FHD", "UK | General", "harbour1.uk"),
                ch("c", "UK: Harbour One HD", "UK | General", ""),
                ch("d", "UK: Harbour One +1", "UK | General", ""),
                ch("e", "UK: Harbour One HD", "UK | Backup", ""),
                ch("f", "Millbrook Sport 1 HD", "UK | Sport", ""),
                ch("g", "Millbrook Sport 2 HD", "UK | Sport", ""));
        int[][] v = Versions.group(l, infos(l));
        assertArrayEquals(new int[]{1, 2, 0}, v[0]);
        assertTrue(v[0] == v[1] && v[1] == v[2]);
        assertNull("a +1 is another channel", v[3]);
        assertNull("another category is another channel", v[4]);
        assertNull("another number is another channel", v[5]);
        assertNull(v[6]);
    }

    @Test public void twoGuideIdsAreNeverOneChannel() {
        List<Channel> l = Arrays.asList(
                ch("a", "Seaview TV HD", "Local", "seaview.north"),
                ch("b", "Seaview TV SD", "Local", "seaview.south"),
                ch("c", "Seaview TV", "Local", ""));
        int[][] v = Versions.group(l, infos(l));
        assertArrayEquals(new int[]{0, 2}, v[0]);
        assertNull(v[1]);
    }

    @Test public void theRowIsNamedWithoutItsQuality() {
        assertEquals("UK: Harbour One", Versions.title("UK: Harbour One FHD"));
        assertEquals("Harbour Two", Versions.title("Harbour Two (HD)"));
        assertEquals("Harbour Sport", Versions.title("Harbour Sport ᴴᴰ"));
        assertEquals("Harbour One", Versions.title("Harbour One HD+"));
        assertEquals("Harbour One +1", Versions.title("Harbour One +1 HD"));
        assertEquals("Harbour Films", Versions.title("Harbour Films 1080p 50 FPS"));
        assertEquals("HD", Versions.title("HD"));
    }

    @Test public void theVersionShownIsThePickedOneElseTheBestNotHidden() {
        List<Channel> l = Arrays.asList(ch("sd", "Bay TV SD", "G", ""), ch("fhd", "Bay TV FHD", "G", ""),
                ch("hd", "Bay TV HD", "G", ""), ch("x", "Other", "G", ""));
        int[][] v = Versions.group(l, infos(l));
        boolean[] none = new boolean[4];
        assertArrayEquals(new boolean[]{false, true, false, true}, Library.rows(l, v, none, Collections.emptyMap()));
        Map<String, String> picks = new HashMap<>();
        for (String k : new String[]{"sd", "fhd", "hd"}) picks.put(k, "sd");
        assertArrayEquals(new boolean[]{true, false, false, true}, Library.rows(l, v, none, picks));
        // The picked one hidden by itself: the best that isn't.
        assertArrayEquals(new boolean[]{false, true, false, true}, Library.rows(l, v, new boolean[]{true, false, false, false}, picks));
        // Every version hidden: none is a row.
        assertArrayEquals(new boolean[]{false, false, false, true}, Library.rows(l, v, new boolean[]{true, true, true, false}, picks));
        // The setting off: every version a row of its own.
        assertArrayEquals(new boolean[]{true, true, true, true}, Library.rows(l, new int[4][], none, picks));
    }

    // ---- the family's own names and sortings ----------------------------------------------------------------------

    @Test public void aTypedNameIsTidiedAndShownOverThePlaylists() {
        assertEquals("Mum's soaps", ChannelEdits.clean("  Mum's   soaps \n"));
        assertEquals("", ChannelEdits.clean(null));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 80; i++) longName.append('x');
        assertEquals(ChannelEdits.NAME_MAX, ChannelEdits.clean(longName.toString()).length());
        assertEquals("Mum's soaps", ChannelEdits.shown("Harbour One FHD", "Mum's soaps", "Harbour One"));
        assertEquals("Harbour One", ChannelEdits.shown("Harbour One FHD", null, "Harbour One"));
        assertEquals("Harbour One FHD", ChannelEdits.shown("Harbour One FHD", "", null));
    }

    @Test public void channelsSortedByNameNumberOrTheFamilysOrder() {
        Integer[] places = {0, 1, 2, 3};
        String[] names = {"delta", "alpha", "charlie", "bravo"};
        int[] numbers = {40, 0, 3, 12};
        int[] manual = {2, 99_999, 0, 1};
        assertArrayEquals(places, ChannelEdits.sorted(places, ChannelEdits.PLAYLIST, names, numbers, manual));
        assertArrayEquals(new Integer[]{1, 3, 2, 0}, ChannelEdits.sorted(places, ChannelEdits.NAME, names, numbers, manual));
        assertArrayEquals("no number last", new Integer[]{2, 3, 0, 1}, ChannelEdits.sorted(places, ChannelEdits.NUMBER, names, numbers, manual));
        assertArrayEquals("not moved last", new Integer[]{2, 3, 0, 1}, ChannelEdits.sorted(places, ChannelEdits.MANUAL, names, numbers, manual));
        // Ties keep the order given.
        assertArrayEquals(new Integer[]{3, 1}, ChannelEdits.sorted(new Integer[]{3, 1}, ChannelEdits.NAME, new String[]{"a", "a", "a", "a"},
                numbers, manual));
        assertEquals(ChannelEdits.PLAYLIST, ChannelEdits.clampSort(9));
    }

    @Test public void editsKeptAsJsonComeBackTheSame() {
        Map<String, String> names = new HashMap<>();
        names.put("x:101", "Mum's soaps");
        names.put("tvg.one|harbour one fhd", "Harbour");
        assertEquals(names, ChannelEdits.strings(ChannelEdits.json(names).toString()));
        Map<String, List<String>> orders = new LinkedHashMap<>();
        orders.put("UK | General", Arrays.asList("x:3", "x:1", "x:2"));
        orders.put("", Collections.singletonList("x:9"));
        assertEquals(orders, ChannelEdits.lists(ChannelEdits.jsonLists(orders).toString()));
        assertTrue(ChannelEdits.strings("not json").isEmpty());
        assertTrue(ChannelEdits.lists("").isEmpty());
        assertEquals("Mum's soaps", ChannelEdits.State.first(names, Arrays.asList("x:7", "x:101")));
        assertNull(ChannelEdits.State.first(names, Collections.singletonList("x:7")));
    }

    // ---- reminders ------------------------------------------------------------------------------------------------

    @Test public void remindersToComeSoonestFirstGoneAndCancelledLeftOut() throws Exception {
        long now = 1_800_000_000_000L;
        List<org.json.JSONObject> items = Arrays.asList(
                new org.json.JSONObject().put("key", "show:Harbour One:2").put("data", new org.json.JSONObject().put("title", "Quiz night")
                        .put("at", now + 7_200_000L).put("channel", "Harbour One")),
                new org.json.JSONObject().put("key", "game:77").put("data", new org.json.JSONObject().put("title", "Harbourside v Millbrook")
                        .put("at", now + 600_000L)),
                new org.json.JSONObject().put("key", "show:Old:1").put("data", new org.json.JSONObject().put("title", "Yesterday's")
                        .put("at", now - 86_400_000L)),
                new org.json.JSONObject().put("key", "show:Gone:3").put("deleted", true).put("data", new org.json.JSONObject()));
        List<Reminders.Kept> l = Reminders.upcoming(items, now);
        assertEquals(2, l.size());
        assertEquals("game:77", l.get(0).key);
        assertEquals("Quiz night", l.get(1).title);
        assertEquals("Harbour One", l.get(1).channel);
        assertEquals("show:Harbour One:5", Reminders.key("Harbour One", 5));
    }

    // ---- the categories' own order --------------------------------------------------------------------------------

    @Test public void categoriesInTheFamilysOrderThenTheRest() {
        List<String> playlist = Arrays.asList("News", "Sport", "Kids", "Films");
        assertEquals(Arrays.asList("Kids", "News", "Sport", "Films"), Groups.arrange(playlist, Arrays.asList("Kids", "Gone", "News")));
        assertEquals(playlist, Groups.arrange(playlist, Collections.emptyList()));
        assertEquals(playlist, Groups.arrange(playlist, null));
        List<String> unchanged = new ArrayList<>(playlist);
        assertEquals(unchanged, Groups.arranged(playlist));
        assertFalse(Groups.renamed("News"));
        assertEquals("News", Groups.label("News"));
        assertEquals(-1, Groups.sortOf("News"));
    }
}
