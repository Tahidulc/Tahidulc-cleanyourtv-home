package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.engine.History;
import com.gathin.homefieldtv.prefs.Picture;
import com.gathin.homefieldtv.prefs.PlayerPrefs;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The player's pure parts in TiviMate's way: the bar's widths and scroll, the lists' scroll, words, shapes, History. */
public class PlaybackTest {
    @Test public void theBarsButtonsAreAtLeast160AndCentreTheRemotes() {
        assertEquals(160f, PlayerBar.slot(100f), 0f);
        // "Closed captions 1" (about 190 px): its words and 32.
        assertEquals(222f, PlayerBar.slot(190f), 0f);
        // At the start nothing moves; in the middle the remote's button is centred; never past the row's end.
        assertEquals(0f, PlayerBar.scrollFor(80f, 3000f), 0f);
        assertEquals(48f + 1500f - 960f, PlayerBar.scrollFor(1500f, 3000f), 0f);
        assertEquals(48f + 3000f - 1872f, PlayerBar.scrollFor(2950f, 3000f), 0f);
        // A row that fits never moves.
        assertEquals(0f, PlayerBar.scrollFor(1700f, 1800f), 0f);
    }

    @Test public void soundTracksAreWrittenAsTheBarWritesThem() {
        assertArrayEquals(new String[]{"English", "Stereo"}, PlayerBar.audioWords("en", null, 2));
        assertArrayEquals(new String[]{"Sound", "5.1"}, PlayerBar.audioWords("und", null, 6));
        assertArrayEquals(new String[]{"Director's commentary", "Mono"}, PlayerBar.audioWords("en", "Director's commentary", 1));
        assertNull(PlayerBar.audioWords("fr", null, 0)[1]);
        assertEquals("French", PlayerBar.audioWords("fr", null, 0)[0]);
    }

    @Test public void theListKeepsTheRemotesChannelTheSecondRow() {
        assertEquals(0f, ChannelList.scrollFor(0, 10, 1080f), 0f);
        assertEquals(0f, ChannelList.scrollFor(1, 10, 1080f), 0f);
        // Ten channels: no further than the last row's foot at the screen's.
        assertEquals(89f + 10 * 125f - 1080f, ChannelList.scrollFor(5, 10, 1080f), 0f);
        assertEquals(49 * 125f, ChannelList.scrollFor(50, 100, 1080f), 0f);
    }

    @Test public void wordsAsTheCardsWriteThem() {
        assertEquals("Riverside • Sports", InfoCard.where("Riverside", "Sports"));
        assertEquals("Sports", InfoCard.where("", "Sports"));
        assertEquals("Riverside", InfoCard.where("Riverside", " "));
        assertEquals("29 min", InfoCard.minutes(29 * 60_000L));
        assertEquals("1 min", InfoCard.minutes(1L));
        assertEquals("408 min", InfoCard.minutes(408 * 60_000L - 30_000L));
        assertEquals("00:05", CatchupScreen.clock(5_000L));
        assertEquals("55:00", CatchupScreen.clock(55 * 60_000L));
        assertEquals("1:02:03", CatchupScreen.clock(3_723_000L));
        assertEquals("0 ms", PlayerPrefs.offsetWords(0));
        assertEquals("+150 ms", PlayerPrefs.offsetWords(150));
        assertEquals("-100 ms", PlayerPrefs.offsetWords(-100));
    }

    @Test public void theDisplayModesNewShapes() {
        // Reduced: Normal at nine tenths, in the middle.
        assertArrayEquals(new int[]{96, 54, 1728, 972}, Picture.place(PlayerPrefs.REDUCED, 1920, 1080, 0, 0, 1920, 1080));
        // 2.35:1: bars over and under.
        assertArrayEquals(new int[]{0, 131, 1920, 817}, Picture.place(PlayerPrefs.CINEMA, 1920, 1080, 0, 0, 1920, 1080));
        // Zoom - 2.35:1: a 16:9 picture grown until its 2.35:1 band is the screen's height, its sides cut.
        int[] z = Picture.place(PlayerPrefs.ZOOM_235, 1920, 1080, 0, 0, 1920, 1080);
        assertEquals(1080f, z[2] / 2.35f, 2f);
        assertEquals((1920 - z[2]) / 2, z[0]);
        assertTrue(z[3] > 1080);
        // The old shapes are where they were; Display mode lists all eight once, Normal first.
        assertEquals(PlayerPrefs.ASPECT_NAMES.length, PlayerPrefs.DISPLAY_ORDER.length);
        assertEquals(PlayerPrefs.FIT, PlayerPrefs.DISPLAY_ORDER[0]);
        assertEquals("Normal", PlayerPrefs.ASPECT_NAMES[PlayerPrefs.FIT]);
        assertEquals("16:9", PlayerPrefs.ASPECT_NAMES[PlayerPrefs.WIDE]);
        assertEquals(8, Arrays.stream(PlayerPrefs.DISPLAY_ORDER).distinct().count());
    }

    @Test public void historyKeepsOneEntryAProgrammeNewestLastForSevenDays() {
        long now = 1_800_000_000_000L, day = 24 * 3600_000L;
        History.Entry a = new History.Entry("k1", "The Long Tide", 100L, 200L, now - 3 * 3600_000L, false);
        History.Entry b = new History.Entry("k2", "Harbour Lights", 300L, 400L, now - 3600_000L, true);
        List<History.Entry> l = History.with(new ArrayList<>(), a);
        l = History.with(l, b);
        // The same programme watched again: one entry, at the end, with its new time.
        History.Entry again = new History.Entry("k1", "The Long Tide", 100L, 200L, now, false);
        l = History.with(l, again);
        assertEquals(2, l.size());
        assertEquals("k2", l.get(0).key);
        assertEquals(now, l.get(1).at);
        // Older than seven days: gone; the rest oldest first.
        History.Entry old = new History.Entry("k3", "Rocket Garden", 0L, 1L, now - 8 * day, false);
        List<History.Entry> mixed = new ArrayList<>(Arrays.asList(again, old, b));
        List<History.Entry> kept = History.prune(mixed, now);
        assertEquals(2, kept.size());
        assertEquals("k2", kept.get(0).key);
        // Kept and read back as they were.
        List<History.Entry> back = History.parse(History.join(kept));
        assertEquals(2, back.size());
        assertEquals("Harbour Lights", back.get(0).title);
        assertTrue(back.get(0).archived);
        assertEquals(300L, back.get(0).start);
        assertEquals(0, History.parse("not a list").size());
        // Never more than MAX.
        List<History.Entry> many = new ArrayList<>();
        for (int i = 0; i < History.MAX + 5; i++) many = History.with(many, new History.Entry("k" + i, "", i, i + 1, now + i, false));
        assertEquals(History.MAX, many.size());
        assertEquals("k5", many.get(0).key);
    }
}
