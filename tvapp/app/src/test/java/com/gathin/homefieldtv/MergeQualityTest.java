package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.engine.Reminders;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Ladder;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.prefs.QualityPicks;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One row per channel (Settings › Channels &amp; categories, on as it comes): the family's "ITV 1 FHD", "ITV 1 HD" and
 * "ITV 1 SD" are one row everywhere, its best played and its others in its Quality choice; what was kept for any of them
 * (a favourite, a lock, a name, a logo, a reminder, the guide, the archive) is the channel's; hiding, renaming and
 * restoring a merged channel go on every version; a number typed for any version lands on it; and with the setting off
 * every version is its own row again. Invented channels.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class MergeQualityTest {
    private static final String ENT = "UK Entertainment", SPORT = "UK Sport";
    private Context app;
    private Library lib;
    private ActivityController<TvActivity> tv;

    /** key, name, category, number, guide id. */
    private static final String[][] CHANNELS = {
            {"e:1", "ITV 1 SD", ENT, "103", ""},
            {"e:2", "ITV 1 HD", ENT, "102", "itv1.uk"},
            {"e:3", "ITV 1 FHD", ENT, "101", "itv1.uk"},
            {"e:4", "CHANNEL 4 FHD", ENT, "104", ""},
            {"e:5", "CHANNEL 4 HD", ENT, "105", ""},
            {"e:6", "BBC TWO SD", ENT, "106", ""},
            {"e:7", "ITV 1 +1", ENT, "107", ""},
            {"s:1", "Kestrel Sports 1 HD", SPORT, "401", ""},
            {"s:2", "Kestrel Sports 2 HD", SPORT, "402", ""},
            {"s:3", "Kestrel Sports 1 SD", SPORT, "403", ""},
    };

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        lib = Library.get(app);
        Sources.clear(app);
        Sources.put(app, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "family", "test"));
        load();
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        lib.clear();
        clear();
        Exec.inline = false;
    }

    private void clear() {
        for (String p : new String[]{"channel_edits", "live_groups", "parental", "quality_picks", "live_tv", "reminders"}) {
            app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
        ChannelEdits.forget();
        Categories.forget();
        Parental.forget();
        QualityPicks.forget();
        Reminders.forget();
    }

    /** The playlist read (again): new channels with the same keys, as a refresh brings; ITV 1 HD keeps three days' archive. */
    private void load() {
        List<Channel> chans = new ArrayList<>();
        for (String[] r : CHANNELS) {
            boolean kept = r[0].equals("e:2");
            chans.add(new Channel(r[0], r[1], r[2], "", "http://provider.example:8080/live/" + r[0] + ".ts", r[4], null, null,
                    Integer.parseInt(r[3]), kept ? Catchup.XC : "", "", kept ? 3 : 0, ""));
        }
        // The guide for ITV 1's SD alone.
        Map<String, Guide.Schedule> guide = new HashMap<>();
        long now = System.currentTimeMillis(), half = 30 * 60_000L, from = now - now % half;
        long[] starts = new long[6], stops = new long[6];
        for (int k = 0; k < 6; k++) {
            starts[k] = from + k * half;
            stops[k] = from + (k + 1) * half;
        }
        guide.put("e:1", MadeUpGuide.schedule(starts, stops));
        lib.useForPreview(chans, guide);
    }

    private static List<String> keys(List<Channel> l) {
        List<String> out = new ArrayList<>();
        for (Channel c : l) out.add(c.key);
        return out;
    }

    private static List<String> names(List<Channel> l) {
        List<String> out = new ArrayList<>();
        for (Channel c : l) out.add(c.name);
        return out;
    }

    private static List<String> titles(List<GuideMenu.Item> items) {
        List<String> out = new ArrayList<>();
        for (GuideMenu.Item it : items) out.add(it.title);
        return out;
    }

    private Channel itv() {
        return lib.byKey("e:3");
    }

    @Test public void oneRowPerChannelItsBestFirst() {
        // ITV 1's three versions, Channel 4's two and Kestrel Sports 1's two are a row each, where each first comes.
        assertEquals(Arrays.asList("e:3", "e:4", "e:6", "e:7", "s:1", "s:2"), keys(lib.all()));
        assertEquals(Arrays.asList("e:3", "e:4", "e:6", "e:7"), keys(lib.group(ENT).channels));
        // Its name without the quality; its versions best first; the best is its row whichever version is asked for.
        Channel row = itv();
        assertEquals("ITV 1", ChannelEdits.name(row));
        assertEquals("CHANNEL 4", ChannelEdits.name(lib.byKey("e:5")));
        assertEquals("BBC TWO SD", ChannelEdits.name(lib.byKey("e:6")));
        assertEquals(Arrays.asList("ITV 1 FHD", "ITV 1 HD", "ITV 1 SD"), names(lib.versions(row)));
        assertSame(row, lib.byKey("e:1"));
        assertSame(row, lib.row(lib.exact("e:2")));
        assertEquals("e:1", lib.exact("e:1").key);
        // The row shows a version's programmes when its best has no guide, and its archive is the HD's.
        assertNotNull(lib.schedule("e:3"));
        assertEquals("e:2", Catchup.source(row).key);
        // Search finds it once, by any version's name.
        assertEquals(Arrays.asList("e:3", "e:7"), keys(lib.search("itv 1", 10)));
        assertEquals(Collections.singletonList("e:3"), keys(lib.search("itv 1 sd", 10)));
    }

    @Test public void anyVersionsNumberLandsOnItsRow() {
        for (int n : new int[]{101, 102, 103}) {
            assertEquals("e:3", lib.numbered(n).key);
            assertEquals(0, lib.lineup().indexOfNumber(n));
            assertEquals(0, lib.group(ENT).indexOfNumber(n));
        }
        assertEquals("e:4", lib.numbered(105).key);
        assertEquals(1, lib.lineup().indexOfNumber(105));
        assertEquals(4, lib.lineup().indexOfNumber(403));
        // Every version is where its row is; channel up and down from any of them go on from the row.
        assertEquals(0, lib.lineup().indexOf(lib.exact("e:1")));
        assertEquals(0, lib.group(ENT).indexOf(lib.exact("e:2")));
        assertEquals("e:4", lib.step(lib.exact("e:1"), 1).key);
        assertEquals("s:2", lib.step(lib.exact("s:3"), 1).key);
        assertEquals("e:7", lib.step(lib.exact("s:3"), -1).key);
        assertEquals("e:4", lib.step(itv(), 1).key);
        // A short list (the favourites) finds its row by any version and by any version's number.
        assertEquals(0, lib.lineup(Collections.singletonList(itv())).indexOf(lib.exact("e:1")));
        assertEquals(0, lib.lineup(Collections.singletonList(itv())).indexOfNumber(103));
    }

    @Test public void favouritesLocksAndRemindersOnAnyVersionAreTheChannels() {
        // A favourite kept on ITV 1 SD before: the channel's.
        lib.setPicks(Collections.singletonList("e:1"), Collections.singletonList("e:2"));
        Channel row = itv();
        assertTrue(lib.isFavourite(row));
        assertEquals(Collections.singletonList("e:3"), keys(lib.list(Library.FAVOURITES, "")));
        assertEquals(Collections.singletonList("e:3"), keys(lib.recent(null)));
        assertTrue(lib.recent(row).isEmpty());
        // Taken off: every version off; added again: the row's.
        assertFalse(lib.toggleFavourite(row));
        assertTrue(lib.favourites().isEmpty());
        assertTrue(lib.toggleFavourite(row));
        assertEquals(Collections.singletonList("e:3"), lib.favourites());
        // A lock on one version locks the channel; Block channel locks and unblocks every version.
        Parental.setPin(app, "2468");
        Parental.relock();
        Parental.lockChannel(app, "e:2", true);
        assertTrue(Parental.blocks(app, row));
        assertTrue(Parental.lockedHere(app, row));
        Parental.lockChannel(app, row, false);
        assertFalse(Parental.blocks(app, row));
        Parental.lockChannel(app, row, true);
        assertTrue(Parental.lockedChannels(app).containsAll(Arrays.asList("e:1", "e:2", "e:3")));
        Parental.lockChannel(app, row, false);
        Parental.clearPin(app);
        // A reminder set on a version before is the channel's, and cancelled from it.
        long start = System.currentTimeMillis() + 3_600_000L;
        Reminders.add(app, new Reminders.Reminder("e:1", "ITV 1 SD", "", "Coastal Kitchen", start, start + 1_800_000L));
        assertTrue(Reminders.has(app, "e:3", start));
        Reminders.remove(app, "e:3", start);
        assertFalse(Reminders.has(app, "e:1", start));
    }

    @Test public void editsOnAnyVersionAreTheChannelsAndNewOnesGoOnEvery() {
        // Kept on single versions before (as 2.0 kept them, each version a channel of its own): a name on the HD, a logo
        // on the SD, the FHD hidden by itself.
        ChannelEdits.setMerging(app, false);
        ChannelEdits.rename(app, lib.exact("e:2"), "My ITV");
        ChannelEdits.setLogo(app, "e:1", "countries/united-kingdom/itv-1-uk.png");
        ChannelEdits.setHidden(app, "e:3", true);
        ChannelEdits.setMerging(app, true);
        assertEquals(Collections.singletonList("e:2"), ChannelEdits.renamedKeys(app));
        Channel row = lib.byKey("e:1");
        // The FHD hidden by itself: the HD is the row, the FHD out of its Quality choice; the channel stays listed.
        assertEquals("e:2", row.key);
        assertEquals(Arrays.asList("ITV 1 HD", "ITV 1 SD"), names(lib.versions(row)));
        assertEquals("My ITV", ChannelEdits.name(row));
        assertTrue(ChannelEdits.renamed(row));
        assertEquals("countries/united-kingdom/itv-1-uk.png", ChannelEdits.logo(row));
        assertEquals(Arrays.asList("e:2", "e:4", "e:6", "e:7", "s:1", "s:2"), keys(lib.all()));
        // Restore channel name: off every version; renamed again: on every version, kept through a refresh.
        ChannelEdits.rename(app, row, "");
        assertEquals("ITV 1", ChannelEdits.name(row));
        assertFalse(ChannelEdits.renamed(row));
        ChannelEdits.rename(app, row, "Independent One");
        for (String k : new String[]{"e:1", "e:2", "e:3"}) assertEquals("Independent One", ChannelEdits.name(lib.exact(k)));
        load();
        assertEquals("Independent One", ChannelEdits.name(lib.byKey("e:3")));
        assertEquals(1, lib.search("independent", 5).size());
        ChannelEdits.rename(app, lib.byKey("e:3"), "ITV 1");
        assertFalse(ChannelEdits.renamed(lib.byKey("e:3")));
    }

    @Test public void hidingAMergedChannelHidesEveryVersionAndShowsThemAllAgain() {
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        TvActivity a = tv.get();
        ChannelMenu.hide(a, itv());
        for (String k : new String[]{"e:1", "e:2", "e:3"}) assertTrue(ChannelEdits.hidden(k));
        assertFalse(keys(lib.all()).contains("e:3"));
        assertEquals(-1, lib.lineup().indexOfNumber(103));
        // Settings › Channels & categories › Hidden channels: one line for the channel; OK brings every version back.
        a.push(new SettingsScreen(a, "channels.hidden"));
        idle();
        SettingsScreen set = (SettingsScreen) a.top();
        RowList.Row line = null;
        int lines = 0;
        for (RowList.Row r : set.rows("channels.hidden")) {
            if (!(r.tag instanceof String)) continue;
            String t = (String) r.tag;
            if (t.startsWith("hch:") || t.startsWith("hgrp:")) lines++;
            if (t.startsWith("hgrp:")) line = r;
        }
        assertEquals(1, lines);
        assertNotNull(line);
        assertEquals("ITV 1", line.title);
        set.chosen(null, line);
        for (String k : new String[]{"e:1", "e:2", "e:3"}) assertFalse(ChannelEdits.hidden(k));
        assertEquals("e:3", lib.all().get(0).key);
    }

    @Test public void hiddenCategoriesStayHiddenThroughARefresh() {
        Categories.setHidden(app, SPORT, true);
        assertTrue(lib.group(SPORT).channels.isEmpty());
        load();
        assertTrue(Categories.isHidden(app, SPORT));
        assertTrue(lib.group(SPORT).channels.isEmpty());
        assertFalse(keys(lib.all()).contains("s:1"));
        Categories.setHidden(app, SPORT, false);
        assertEquals(Arrays.asList("s:1", "s:2"), keys(lib.group(SPORT).channels));
    }

    @Test public void theSettingOffBringsBackEveryVersion() {
        lib.setPicks(Collections.singletonList("e:1"), null);
        ChannelEdits.setMerging(app, false);
        assertEquals(10, lib.all().size());
        assertEquals("ITV 1 FHD", ChannelEdits.name(lib.byKey("e:3")));
        assertEquals("e:1", lib.numbered(103).key);
        assertEquals("e:3", lib.numbered(101).key);
        assertFalse(lib.isFavourite(lib.byKey("e:3")));
        assertEquals(Collections.singletonList(lib.byKey("e:3")), lib.versions(lib.byKey("e:3")));
        assertEquals(4, lib.search("itv 1", 10).size());
        ChannelEdits.setMerging(app, true);
        assertEquals(6, lib.all().size());
    }

    @Test public void theHoldOkCardStartsWithItsQuality() {
        Channel row = itv();
        List<GuideMenu.Item> card = GuideActions.card(app, row, lib.schedule(row.key), -1, true, true);
        List<String> t = titles(card);
        // Search and Settings, then Quality: Best, then each version by its own name, the best ticked (nothing plays).
        assertEquals(Arrays.asList("Search", "Settings", "Quality", "Best", "ITV 1 FHD", "ITV 1 HD", "ITV 1 SD"), t.subList(0, 7));
        assertEquals("In use", card.get(3).value);
        assertTrue(card.get(4).checked);
        assertFalse(card.get(5).checked);
        // The channel's own lines follow, under its name, Rename always there.
        int at = t.indexOf("ITV 1");
        assertTrue(at > 6);
        assertTrue(t.subList(at, t.size()).containsAll(Arrays.asList("Add to Favourites", "Block channel", "Hide channel",
                "Rename channel", "Change logo", "Move channel", "Catch-up")));
        // Picked by hand: the HD ticked, Best no longer in use; the archive's card has no Quality.
        QualityPicks.set(app, row, lib.exact("e:2"));
        card = GuideActions.card(app, row, null, -1, true, true);
        assertEquals("", card.get(3).value);
        assertTrue(card.get(5).checked);
        assertFalse(card.get(4).checked);
        assertFalse(titles(GuideActions.card(app, row, null, -1, false)).contains("Quality"));
        // A channel in one version has none.
        assertFalse(titles(GuideActions.card(app, lib.byKey("e:6"), null, -1, true, true)).contains("Quality"));
    }

    @Test public void okPlaysTheBestAndTheQualityChoicePlaysAVersion() {
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        TvActivity a = tv.get();
        Channel row = itv();
        a.live.play(row);
        assertEquals("e:3", a.live.channel().key);
        assertEquals("e:3", a.live.version().key);
        // The Quality choice: the HD kept for the channel and played on at once; Best again: the FHD.
        assertTrue(GuideActions.chooseQuality(a, row, GuideActions.Q_VERSION + "e:2"));
        assertEquals("e:2", a.live.version().key);
        assertEquals("e:3", a.live.channel().key);
        assertTrue(a.live.pickedByHand());
        // Remembered for the channel: played again, the HD.
        a.live.stop();
        a.live.forget();
        a.live.play(lib.byKey("e:3"));
        assertEquals("e:2", a.live.version().key);
        assertTrue(GuideActions.chooseQuality(a, row, GuideActions.Q_BEST));
        assertEquals("e:3", a.live.version().key);
        assertFalse(a.live.pickedByHand());
        // A version played by its own key (a reminder's, the phone's) plays as its channel's row, on that version.
        a.live.stop();
        a.live.forget();
        a.live.play(lib.exact("e:1"));
        assertEquals("e:3", a.live.channel().key);
        assertEquals("e:1", a.live.version().key);
        // Hold OK in the guide: the card's Quality, chosen there, plays that version in the preview.
        a.live.stop();
        a.live.forget();
        a.openGuide(null);
        idle();
        GuideScreen g = (GuideScreen) a.top();
        assertEquals("e:3", g.focusedChannel().key);
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(g.menuOpen());
        g.chosen(null, GuideActions.Q_VERSION + "e:1");
        idle();
        assertFalse(g.menuOpen());
        assertEquals("e:3", a.live.channel().key);
        assertEquals("e:1", a.live.version().key);
        assertEquals("e:1", QualityPicks.of(app, row, lib.versions(row)).key);
    }

    @Test public void bestFirstAndTheFallBack() {
        Channel row = itv();
        List<Channel> vs = lib.versions(row);
        Ladder l = Ladder.of(vs, vs.get(0), PlayerPrefs.QUALITY_ADAPTIVE);
        assertEquals("e:3", l.stream().key);
        // The FHD doesn't answer: the HD, with its note.
        Channel was = l.base();
        l.down();
        assertEquals("e:2", l.stream().key);
        assertEquals("Playing ITV 1 HD: FHD wasn't answering", Ladder.fellBack(was, l.base()));
        // Adaptive never goes above a version picked by hand.
        Ladder hand = Ladder.picked(vs, vs.get(1));
        assertEquals("e:2", hand.stream().key);
        assertFalse(hand.canUp());
        assertTrue(hand.canDown());
        // A fixed quality falls back only from a version that never started, once tried again quickly.
        assertTrue(LiveTv.fallsBack(true, false, 2));
        assertFalse(LiveTv.fallsBack(true, true, 2));
        assertFalse(LiveTv.fallsBack(true, false, 1));
        assertFalse(LiveTv.fallsBack(false, false, 5));
        // 720p as Picture quality: its best at or under it, the HD.
        assertEquals("e:2", Ladder.of(vs, vs.get(0), 2).stream().key);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void hold(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 100, KeyEvent.ACTION_DOWN, code, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 120, KeyEvent.ACTION_UP, code, 0));
        idle();
    }
}
