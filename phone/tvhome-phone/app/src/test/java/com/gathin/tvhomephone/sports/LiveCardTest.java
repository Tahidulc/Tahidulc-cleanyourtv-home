package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiveCardTest {
    private static Game live(String id, double clock, int period, String display) {
        Game g = new Game();
        g.id = id;
        g.state = Game.LIVE;
        g.clock = clock;
        g.period = period;
        g.displayClock = display;
        g.detail = display;
        return g;
    }

    @Test public void theClockCountsInMinutesAndSecondsPastTheHour() {
        Game g = live("lc-hour", 67 * 60, 2, "67'");
        long t = 5_000_000L;
        assertArrayEquals(new String[]{"67:00", ""}, LiveCard.clock(g, t));
        assertArrayEquals(new String[]{"67:23", ""}, LiveCard.clock(g, t + 23_000));
        assertEquals("\u2022 67:23", LiveCard.plain(LiveCard.clock(g, t + 23_000)));
        // Extra time too, never as hours.
        Game et = live("lc-et", 104 * 60, 3, "104'");
        assertEquals("\u2022 104:00", LiveCard.plain(LiveCard.clock(et, t)));
        assertEquals("\u2022 104:59", LiveCard.plain(LiveCard.clock(et, t + 59_000)));
        // Held at :59 until ESPN's next minute, as the app's clock is.
        assertEquals("\u2022 104:59", LiveCard.plain(LiveCard.clock(et, t + 300_000)));
    }

    @Test public void itIsTheAppsClock() {
        // The same game read the same moment: the words the app shows (LiveClock), the dot before them.
        Game g = live("lc-same", 23 * 60, 1, "23'");
        long t = 9_000_000L;
        LiveCard.clock(g, t);
        assertEquals("\u2022 " + LiveClock.words(g, t + 41_000), LiveCard.plain(LiveCard.clock(g, t + 41_000)));
    }

    @Test public void addedTimeHalfTimeAndNoClock() {
        Game added = live("lc-added", 5520, 2, "90'+4'");
        String[] c = LiveCard.clock(added, 0);
        assertArrayEquals(new String[]{"90:00", "+4"}, c);
        assertEquals("\u2022 90:00 \u2009+4\u2009", LiveCard.plain(c));
        Game ht = live("lc-ht", 2700, 1, "HT");
        ht.halftime = true;
        assertArrayEquals(new String[]{"HT", ""}, LiveCard.clock(ht, 0));
        assertEquals("\u2022 HT", LiveCard.plain(LiveCard.clock(ht, 0)));
    }

    @Test public void onlyTheLiveGamesFourAtMost() {
        List<Game> followed = new ArrayList<>();
        Game pre = live("lc-pre", 0, 0, "");
        pre.state = Game.PRE;
        followed.add(pre);
        for (int i = 0; i < 6; i++) followed.add(live("lc-l" + i, 600, 1, "10'"));
        List<Game> shown = LiveCard.live(followed);
        assertEquals(LiveCard.MAX, shown.size());
        assertEquals("lc-l0", shown.get(0).id);
        assertTrue(LiveCard.live(null).isEmpty());
    }

    @Test public void itGoesAgainOnlyWhenTheWordsMove() {
        Game g = live("lc-words", 30 * 60, 1, "30'");
        List<Game> games = Arrays.asList(g);
        long t = 20_000_000L;
        String first = LiveCard.words(games, t);
        assertEquals(first, LiveCard.words(games, t + 400));
        assertNotEquals(first, LiveCard.words(games, t + 1_000));
        // Held at :59 until ESPN's next minute: nothing new to send.
        assertEquals(LiveCard.words(games, t + 59_000), LiveCard.words(games, t + 75_000));
        // In added time the words stand still between reads.
        List<Game> added = Arrays.asList(live("lc-words-added", 2820, 1, "45'+2'"));
        assertEquals(LiveCard.words(added, t), LiveCard.words(added, t + 30_000));
    }

    @Test public void itTicksWhileAClockIsGoing() {
        Game ht = live("lc-tick-ht", 2700, 1, "HT");
        ht.halftime = true;
        assertFalse(LiveCard.ticks(Arrays.asList(ht), 0));
        assertTrue(LiveCard.ticks(Arrays.asList(ht, live("lc-tick", 600, 1, "10'")), 0));
        assertFalse(LiveCard.ticks(new ArrayList<>(), 0));
    }

    @Test public void onTheWholeSecond() {
        assertEquals(1000, LiveCard.delay(5_000));
        assertEquals(1, LiveCard.delay(5_999));
        assertEquals(750, LiveCard.delay(1_234_250));
    }

    @Test public void thePartsMeetOnWholePixels() {
        int[] x = NotifCard.edges(935);
        assertEquals(0, x[0]);
        assertEquals(935, x[4]);
        assertEquals(Math.round(935 * 162 / 414f), x[2]);
        assertEquals(Math.round(935 * 304 / 414f), x[3]);
        assertTrue(NotifCard.SCORE_BASE < NotifCard.CLOCK_TOP);
    }

    /** The layout lays the words over the line the drawing leaves empty: its weights are the drawing's. */
    @Test public void theWordsSitOverTheClocksLine() throws Exception {
        Path xml = Paths.get("src/main/res/layout/sports_notif_live_row.xml");
        if (!Files.exists(xml)) xml = Paths.get("app").resolve(xml);
        String s = new String(Files.readAllBytes(xml), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("layout_weight=\"(\\d+)\"").matcher(s);
        List<Integer> w = new ArrayList<>();
        while (m.find()) w.add(Integer.parseInt(m.group(1)));
        // Across: the competition and the home crest, the middle, the away crest. Down, in the middle: above, the line, under.
        assertEquals(Arrays.asList(NotifCard.PARTS[0] + NotifCard.PARTS[1], 62, 24, 14, NotifCard.PARTS[3], NotifCard.PARTS[2]),
                Arrays.asList(w.get(0), w.get(2), w.get(3), w.get(4), w.get(5), w.get(1)));
        assertEquals(NotifCard.CLOCK_TOP, w.get(2) / 100f, 1e-6);
        assertEquals(NotifCard.CLOCK_BOTTOM - NotifCard.CLOCK_TOP, w.get(3) / 100f, 1e-6);
        assertEquals(1 - NotifCard.CLOCK_BOTTOM, w.get(4) / 100f, 1e-6);
    }
}
