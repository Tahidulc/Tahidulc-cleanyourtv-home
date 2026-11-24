package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;

import com.gathin.homefieldtv.engine.CardsSnapshot;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpCards;
import com.gathin.homefieldtv.engine.MadeUpChannels;
import com.gathin.homefieldtv.engine.PlayRequest;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.Parental;

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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The TV launcher's way into Homefield TV: the cards it reads (the recent channels newest first and one row per channel,
 * the favourites, the sport on now; locked channels never on them; answered from the file, not the playlists) and its
 * "play this channel" (straight on to the channel full screen, started afresh or running, by key, else name; never past
 * the PIN). Invented channels.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ChannelCardsTest {
    private Context app;
    private Library lib;
    private List<Channel> ch;
    private ActivityController<TvActivity> tv;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        lib = Library.get(app);
        ch = MadeUpCards.load(app);
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        MadeUpChannels.unload(app);
        clear();
        Exec.inline = false;
    }

    private void clear() {
        for (String p : new String[]{"channel_edits", "live_groups", "parental", "live_tv"}) {
            app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
        ChannelEdits.forget();
        Parental.forget();
        ChannelCardsProvider.file(app).delete();
        ChannelCardsProvider.put(CardsSnapshot.EMPTY, -1);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private Channel by(String key) {
        for (Channel c : ch) if (c.key.equals(key)) return c;
        throw new AssertionError(key);
    }

    private static List<String> keys(List<String> l) {
        return l;
    }

    private static List<String> rowKeys(List<CardsSnapshot.Row> rows, String section) {
        List<String> out = new ArrayList<>();
        for (CardsSnapshot.Row r : rows) if (r.section.equals(section)) out.add(r.card.key);
        return out;
    }

    // ---- the cards -----------------------------------------------------------------------------------------------------

    @Test public void recentOneRowPerChannelNewestFirstFavouritesAndSport() {
        // Kestrel One watched in SD, then Kestrel Two, then Kestrel One in HD: one row, Kestrel One's (its FHD), first.
        lib.watched(by("c:1"));
        lib.watched(by("c:4"));
        lib.watched(by("c:2"));
        lib.toggleFavourite(by("c:5"));
        lib.toggleFavourite(by("c:1"));
        long now = System.currentTimeMillis();
        CardsSnapshot s = ChannelCards.snapshot(app, lib, null, now);
        assertEquals(java.util.Arrays.asList("c:3", "c:4"), keys(s.recent));
        assertEquals(java.util.Arrays.asList("c:5", "c:3"), keys(s.favourites));
        CardsSnapshot.Card one = s.channels.get("c:3");
        // Its name without the quality, its number, its logo as Homefield TV shows it, a few hours of programmes.
        assertEquals("Kestrel One", one.name);
        assertEquals(101, one.number);
        assertEquals("https://logos.example/c-3.png", one.logo);
        assertEquals("The Long Tide", one.at(now).title);
        assertEquals("Harbour Lights", one.after(now).title);
        // Sport: on now first (live first when asked), then the one whose game comes later.
        assertEquals(java.util.Arrays.asList("s:1", "s:3", "s:2"), keys(s.sport));
        List<CardsSnapshot.Row> rows = s.rows(now);
        assertEquals(java.util.Arrays.asList("s:1", "s:3"), rowKeys(rows, CardsSnapshot.SPORT));
        // 45 minutes on: the final is live and leads; s:3 has nothing on.
        assertEquals(java.util.Arrays.asList("s:2", "s:1"), rowKeys(s.rows(now + 45 * 60_000L), CardsSnapshot.SPORT));
    }

    @Test public void lockedChannelsNeverComeUpOnTheLauncher() {
        lib.watched(by("c:4"));
        lib.watched(by("c:5"));
        lib.toggleFavourite(by("c:4"));
        Parental.setPin(app, "2468");
        Parental.lockChannel(app, by("c:4"), true);
        Parental.lockChannel(app, by("s:1"), true);
        CardsSnapshot s = ChannelCards.snapshot(app, lib, null, System.currentTimeMillis());
        assertEquals(java.util.Collections.singletonList("c:5"), s.recent);
        assertTrue(s.favourites.isEmpty());
        assertFalse(s.sport.contains("s:1"));
        assertFalse(s.channels.containsKey("c:4"));
    }

    @Test public void theProviderAnswersFromTheFileNotThePlaylists() throws Exception {
        lib.watched(by("c:4"));
        lib.toggleFavourite(by("c:5"));
        long now = System.currentTimeMillis();
        CardsSnapshot s = ChannelCards.snapshot(app, lib, null, now);
        File f = ChannelCardsProvider.file(app);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.toJson().getBytes(StandardCharsets.UTF_8));
        }
        // The playlists gone from memory: the cards are still there, from the file alone.
        MadeUpChannels.unload(app);
        Robolectric.setupContentProvider(ChannelCardsProvider.class, ChannelCardsProvider.AUTHORITY);
        Uri all = Uri.parse("content://" + ChannelCardsProvider.AUTHORITY + "/cards");
        List<String> sections = new ArrayList<>(), keys = new ArrayList<>();
        try (Cursor c = app.getContentResolver().query(all, null, null, null, null)) {
            assertNotNull(c);
            for (String col : ChannelCardsProvider.COLUMNS) assertTrue(col, c.getColumnIndex(col) >= 0);
            while (c.moveToNext()) {
                sections.add(c.getString(c.getColumnIndexOrThrow("section")));
                keys.add(c.getString(c.getColumnIndexOrThrow("channel_key")));
                if ("c:4".equals(keys.get(keys.size() - 1))) {
                    assertEquals("Kestrel Two", c.getString(c.getColumnIndexOrThrow("channel_name")));
                    assertEquals(104, c.getInt(c.getColumnIndexOrThrow("channel_number")));
                    assertEquals("Saltwater Hymn", c.getString(c.getColumnIndexOrThrow("now_title")));
                    assertEquals("Midnight Orchard", c.getString(c.getColumnIndexOrThrow("next_title")));
                    long start = c.getLong(c.getColumnIndexOrThrow("now_start")), end = c.getLong(c.getColumnIndexOrThrow("now_end"));
                    assertTrue(start <= now && now < end);
                    assertEquals(now, c.getLong(c.getColumnIndexOrThrow("written_at")));
                }
            }
        }
        assertEquals(java.util.Arrays.asList("recent", "favourite", "sport", "sport"), sections);
        assertEquals(java.util.Arrays.asList("c:4", "c:5", "s:1", "s:3"), keys);
        // One section on its own.
        try (Cursor c = app.getContentResolver().query(Uri.withAppendedPath(all, "sport"), null, null, null, null)) {
            assertNotNull(c);
            assertEquals(2, c.getCount());
        }
        // Anything else: nothing.
        assertEquals(null, app.getContentResolver().query(Uri.parse("content://" + ChannelCardsProvider.AUTHORITY + "/other"),
                null, null, null, null));
    }

    // ---- play this channel ---------------------------------------------------------------------------------------------

    private static Intent play(String key, String name, int number) {
        return new Intent(PlayRequest.ACTION).putExtra(PlayRequest.EXTRA_KEY, key).putExtra(PlayRequest.EXTRA_NAME, name)
                .putExtra(PlayRequest.EXTRA_NUMBER, number).putExtra(PlayRequest.EXTRA_AT, System.currentTimeMillis());
    }

    private static String playing(TvActivity a) {
        return a.live.channel() == null ? "" : a.live.channel().key;
    }

    @Test public void startedAfreshStraightOnToTheChannelItsBestVersion() {
        // Kestrel One's SD key: its row plays, from its best version (FHD), full screen; Home never comes up.
        tv = Robolectric.buildActivity(TvActivity.class, play("c:1", "Kestrel One", 103)).setup();
        TvActivity a = tv.get();
        idle();
        assertTrue(a.top() instanceof LiveScreen);
        assertEquals("c:3", playing(a));
        assertEquals(View.GONE, a.homeScreen().view().getVisibility());
        assertEquals(View.VISIBLE, a.screens.getVisibility());
        // Taken once: the intent no longer asks (the activity made again opens as usual).
        assertFalse(PlayRequest.ACTION.equals(a.getIntent().getAction()));
    }

    @Test public void runningTheChannelChangesAtOnceAndByNameWhenItsKeyIsGone() {
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle();
        assertTrue(a.top() instanceof HomeScreen);
        tv.newIntent(play("c:4", "Kestrel Two", 104));
        idle();
        assertTrue(a.top() instanceof LiveScreen);
        assertEquals("c:4", playing(a));
        // A key from before the playlist was read again: found by its name.
        tv.newIntent(play("p9|gone", "Harbour TV", 999));
        idle();
        assertEquals("c:5", playing(a));
        // Nothing finds it: the app as it was, a note said.
        tv.newIntent(play("p9|gone", "No Such Channel", 999));
        idle();
        assertEquals("c:5", playing(a));
        assertEquals(View.VISIBLE, a.screens.getVisibility());
    }

    @Test public void neverPastThePin() {
        Parental.setPin(app, "2468");
        Parental.lockChannel(app, by("c:4"), true);
        // Homefield TV left since the PIN was typed: locked again.
        Parental.relock();
        tv = Robolectric.buildActivity(TvActivity.class, play("c:4", "Kestrel Two", 104)).setup();
        TvActivity a = tv.get();
        idle();
        // The PIN's pad over Home (shown), nothing playing.
        assertTrue(a.top() instanceof PinScreen);
        assertFalse("c:4".equals(playing(a)));
        assertEquals(View.VISIBLE, a.screens.getVisibility());
        assertEquals(View.VISIBLE, a.homeScreen().view().getVisibility());
    }

    @Test public void anOldOrEmptyRequestOpensAsUsual() {
        Intent old = play("c:4", "Kestrel Two", 104).putExtra(PlayRequest.EXTRA_AT, System.currentTimeMillis() - 10 * 60_000L);
        tv = Robolectric.buildActivity(TvActivity.class, old).setup();
        TvActivity a = tv.get();
        idle();
        assertTrue(a.top() instanceof HomeScreen);
        assertEquals(View.VISIBLE, a.screens.getVisibility());
        tv.newIntent(new Intent(PlayRequest.ACTION));
        idle();
        assertTrue(a.top() instanceof HomeScreen);
    }

    @Test public void theNumberAsANumberOrAsWords() {
        Bundle b = new Bundle();
        b.putInt(PlayRequest.EXTRA_NUMBER, 104);
        assertEquals(104, PlayChannel.number(b));
        b.putString(PlayRequest.EXTRA_NUMBER, " 105 ");
        assertEquals(105, PlayChannel.number(b));
        b.putString(PlayRequest.EXTRA_NUMBER, "one");
        assertEquals(0, PlayChannel.number(b));
        assertEquals(0, PlayChannel.number(null));
        // The library's lookups: a version's key finds its row; a name finds the listed row; a number its channel.
        PlayRequest.Finder f = PlayChannel.finder(lib);
        assertEquals("c:3", f.byKey("c:2").key);
        assertEquals("c:3", f.named("Kestrel One HD").get(0).key);
        assertEquals("c:4", f.numbered(104).key);
    }
}
