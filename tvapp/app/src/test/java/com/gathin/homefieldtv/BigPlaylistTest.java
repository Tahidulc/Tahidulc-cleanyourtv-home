package com.gathin.homefieldtv;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.BigPlaylist;
import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Genres;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;
import com.gathin.homefieldtv.prefs.RemoteKeys;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The family's playlist at its largest (BigPlaylist: 50,000 channels in 400 groups, a guide of 2,000 channels over 24
 * hours, 40,000 films and series), and how long the main thread takes over each screen and key that froze 1.10: the TV
 * guide opening, its categories, Home's rows, channel up and down, a number typed, Live TV's rows (and since the player
 * took TiviMate's ways, its overlay, channel lists and History), films and TV shows, search. Each is timed on the JVM (the first time, then the median of the rest) and printed; each must stay well under
 * the limit below, however many channels there are. What is worked out off the main thread (the lists, Home's rows, a
 * search) is timed too, for the record.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class BigPlaylistTest {
    /** The most any one key or screen may keep the main thread on the JVM (a generous limit: these take a few ms). */
    private static final double LIMIT_MS = 250;

    private final Map<String, double[]> times = new LinkedHashMap<>();
    private final Map<String, Boolean> onMain = new LinkedHashMap<>();
    private Context app;

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Until the work off the main thread is done and what it handed back has run (at most ten seconds). */
    private static void settle() throws InterruptedException {
        for (int i = 0; i < 10_000; i++) {
            idle();
            if (Exec.busy() == 0) {
                idle();
                if (Exec.busy() == 0) return;
            }
            Thread.sleep(1);
        }
    }

    /** Back to Home, the pages' fades run to their end. */
    private static void home(TvActivity a) {
        a.home();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
    }

    /** A key held: down, a repeat once the player's hold has gone by, up. */
    private static void hold(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 100, KeyEvent.ACTION_DOWN, code, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 120, KeyEvent.ACTION_UP, code, 0));
    }

    /** The wall clock in ns (java.time's: Robolectric's own clock stands still under a paused looper). */
    private static long realNanos() {
        java.time.Instant i = java.time.Clock.systemUTC().instant();
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }

    private interface Step {
        void run() throws Exception;
    }

    /** Runs r n times (before and after each, untimed); keeps the first (cold) and the median of the rest, in ms. */
    private void time(String what, boolean main, int n, Step before, Step r, Step after) throws Exception {
        double[] ms = new double[n];
        for (int i = 0; i < n; i++) {
            if (before != null) before.run();
            long t0 = realNanos();
            r.run();
            ms[i] = (realNanos() - t0) / 1e6;
            if (after != null) after.run();
        }
        double[] rest = Arrays.copyOfRange(ms, Math.min(1, n - 1), n);
        Arrays.sort(rest);
        times.put(what, new double[]{ms[0], rest[rest.length / 2]});
        onMain.put(what, main);
    }

    private void time(String what, int n, Step r) throws Exception {
        time(what, true, n, null, r, null);
    }

    private void time(String what, int n, Step r, Step after) throws Exception {
        time(what, true, n, null, r, after);
    }

    @After public void report() {
        StringBuilder b = new StringBuilder("\nTIMINGS (ms, JVM)                                                  first    median\n");
        for (Map.Entry<String, double[]> e : times.entrySet()) {
            b.append(String.format(Locale.ROOT, "  %-62s %9.2f %9.2f%s%n", e.getKey(), e.getValue()[0], e.getValue()[1],
                    Boolean.TRUE.equals(onMain.get(e.getKey())) ? "" : "   (off the main thread)"));
        }
        System.out.println(b);
        Exec.inline = false;
        if (app != null) BigPlaylist.unload(app);
    }

    @Test public void fiftyThousandChannelsKeepTheMainThreadFree() throws Exception {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        long t0 = realNanos();
        BigPlaylist.load(app);
        times.put("load: the made-up playlist, guide and catalogue", new double[]{(realNanos() - t0) / 1e6, 0});
        onMain.put("load: the made-up playlist, guide and catalogue", false);
        Library lib = Library.get(app);
        Catalog cat = Catalog.get(app);
        List<Channel> every = lib.every();
        assertTrue(every.size() > 49_000);

        // ---- worked out once a load, off the main thread ----------------------------------------------------------
        time("Genres.of, every channel (as the channels are read)", false, 3, null, () -> {
            for (Channel c : every) Genres.of(c.group, c.name);
        }, null);
        time("Library: the index made (each load)", false, 3, null, () -> BigPlaylist.index(every), null);
        time("Library: the lists made (each load, each hidden or locked)", false, 3, null, () -> BigPlaylist.lists(app, every), null);
        // The family's edits (ChannelEdits): 500 channels hidden, 500 renamed, every list sorted by name, then by number.
        StringBuilder edits = new StringBuilder();
        for (int k = 0; k < 500; k++) {
            edits.append(BigPlaylist.keyOf(k * 97)).append("\t1\t\t\t\n");
            edits.append(BigPlaylist.keyOf(k * 97 + 13)).append("\t\t\tRenamed ").append(k).append("\t\n");
        }
        app.getSharedPreferences("channel_edits", Context.MODE_PRIVATE).edit().putString("records", edits.toString())
                .putInt("sort", ChannelEdits.NAME).commit();
        ChannelEdits.forget();
        time("Library: the lists made, by name, 500 hidden, 500 renamed", false, 3, null, () -> BigPlaylist.lists(app, every), null);
        ChannelEdits.setSort(app, ChannelEdits.NUMBER);
        time("Library: the lists made, by number, 500 hidden, 500 renamed", false, 3, null, () -> BigPlaylist.lists(app, every), null);
        time("Library: search a name the family gave, 8 channels", false, 5, null, () -> lib.search("renamed 4", 8), null);
        // The new logos: a screenful of channels' matches worked out as they are first drawn (on the main thread).
        LogoPack.useForPreview(new String(java.nio.file.Files.readAllBytes(new java.io.File("src/main/assets/logos/fallback.txt").toPath()),
                java.nio.charset.StandardCharsets.UTF_8));
        int[] screen = {0};
        time("LogoPack.auto: 12 channels' new logos matched (first drawn)", true, 5, null, () -> {
            for (int k = 0; k < 12; k++) LogoPack.auto(every.get(screen[0]++ * 7 % every.size()));
        }, null);
        app.getSharedPreferences("channel_edits", Context.MODE_PRIVATE).edit().clear().commit();
        ChannelEdits.forget();
        time("Library: Home's rows found (each minute)", false, 3, null, () -> BigPlaylist.onNow(app), null);
        time("Library: search \"lantern\", 8 channels", false, 5, null, () -> lib.search("lantern", 8), null);
        time("Catalog.search(\"lantern\") films + series", false, 5, null, () -> {
            cat.search("lantern", false, 12);
            cat.search("lantern", true, 12);
        }, null);

        // ---- the engine's answers to the screens, on the main thread -------------------------------------------------
        Exec.inline = false;
        String flip = BigPlaylist.groupOf(30_000);
        time("Library.all(), once a category is hidden or shown", true, 3,
                () -> Categories.setHidden(app, flip, !Categories.isHidden(app, flip)), lib::all, BigPlaylistTest::settle);
        String one = BigPlaylist.keyOf(30_001);
        time("Library.all(), once a channel is hidden or shown (Hide channel)", true, 3,
                () -> ChannelEdits.setHidden(app, one, !ChannelEdits.hidden(one)), lib::all, BigPlaylistTest::settle);
        time("Library.all(), once the channels are sorted another way", true, 3,
                () -> ChannelEdits.setSort(app, ChannelEdits.sort(app) == ChannelEdits.NAME ? ChannelEdits.PLAYLIST : ChannelEdits.NAME), lib::all,
                BigPlaylistTest::settle);
        ChannelEdits.setHidden(app, one, false);
        ChannelEdits.setSort(app, ChannelEdits.PLAYLIST);
        settle();
        time("Library.list(a group)", 5, () -> lib.list(BigPlaylist.groupOf(25_000), ""));
        time("Library.list(FAVOURITES) + recent(null) + favourites()", 5, () -> {
            lib.list(Library.FAVOURITES, "");
            lib.recent(null);
            lib.favourites();
        });
        Channel last = lib.all().get(lib.all().size() - 1);
        time("Library.step, channel up from the last", 5, () -> lib.step(last, 1));
        time("Library.playable", 5, () -> lib.playable(last));
        time("Library.numbered(9999)", 5, () -> assertNotNull(lib.numbered(9999)));
        time("Catalog.recent(films, 30) + (series, 30)", 5, () -> {
            cat.recent(false, 30);
            cat.recent(true, 30);
        });
        time("Resume.get, every film (each poster drawn asks)", 3, () -> {
            for (Catalog.Title t : cat.films()) Resume.get(app, t.key, "");
        });
        long now = System.currentTimeMillis();
        time("Schedule.at + after, every guide channel", 5, () -> {
            for (Channel c : every) {
                Guide.Schedule s = lib.schedule(c.key);
                if (s != null) {
                    s.at(now);
                    s.after(now + 6 * 3600_000L);
                }
            }
        });

        // ---- the screens, as the remote opens them ------------------------------------------------------------------
        Exec.inline = true;
        TvActivity a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idle();
        Exec.inline = false;
        settle();
        time("Home: its rows made again (each minute, each return)", 5, () -> a.homeScreen().tick(), BigPlaylistTest::settle);
        time("TV guide: opened (all channels)", 5, () -> a.openGuide(null), () -> home(a));
        time("TV guide: opened on Sport (Home's Sports)", 5, () -> a.openGuide(Genres.SPORT), () -> home(a));
        a.openGuide(null);
        idle();
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        // The panel shows a category a moment after the remote rests on it: at once here (in the previews' way).
        Exec.inline = true;
        time("TV guide: Down the categories (one shown at once)", 9, () -> press(a, KeyEvent.KEYCODE_DPAD_DOWN));
        Exec.inline = false;
        time("TV guide: told the channels changed (a sync)", 5, () -> a.top().channelsChanged());
        home(a);
        time("channelToPlay (the mini TV's channel)", 5, a::channelToPlay);

        // Live TV in the previews' way (no player made); none of its keys has work off the main thread.
        Exec.inline = true;
        a.watch(lib.all().get(10));
        idle();
        time("Live: CH- (the card of the channel before), from the last channel", true, 9, () -> a.live.play(last),
                () -> press(a, KeyEvent.KEYCODE_CHANNEL_DOWN), null);
        time("Live: 9 9 9 9 typed (the fourth tunes)", true, 5, () -> {
            press(a, KeyEvent.KEYCODE_9);
            press(a, KeyEvent.KEYCODE_9);
            press(a, KeyEvent.KEYCODE_9);
        }, () -> press(a, KeyEvent.KEYCODE_9), null);
        time("Live: Up (the next channel)", 5, () -> press(a, KeyEvent.KEYCODE_DPAD_UP), () -> press(a, KeyEvent.KEYCODE_BACK));
        time("Live: OK (the overlay: the card and the tiles)", 5, () -> press(a, KeyEvent.KEYCODE_DPAD_CENTER),
                () -> press(a, KeyEvent.KEYCODE_BACK));
        time("Live: Left (the channel list over the picture)", 5, () -> press(a, KeyEvent.KEYCODE_DPAD_LEFT),
                () -> press(a, KeyEvent.KEYCODE_BACK));
        time("Live: Left held (the channel list, the picture in its window)", 5, () -> hold(a, KeyEvent.KEYCODE_DPAD_LEFT),
                () -> press(a, KeyEvent.KEYCODE_BACK));
        time("Live: Up held (History)", 5, () -> hold(a, KeyEvent.KEYCODE_DPAD_UP), () -> press(a, KeyEvent.KEYCODE_BACK));
        // Settings → Remote → Up and Down: the category rows (as Homefield TV came).
        RemoteKeys.setUpDown(app, 0);
        time("Live: Up (the category rows)", 5, () -> press(a, KeyEvent.KEYCODE_DPAD_UP), () -> press(a, KeyEvent.KEYCODE_BACK));
        RemoteKeys.setUpDown(app, 1);
        Exec.inline = false;
        home(a);

        time("Films: opened", 3, () -> a.openBrowse(false), () -> home(a));
        time("TV shows: opened", 3, () -> a.openBrowse(true), () -> home(a));
        a.openBrowse(false);
        idle();
        // The categories' column opens on All movies: up to the playlists' heading, then down onto All movies again,
        // one grid of every film shown at once as the remote lands on it.
        press(a, KeyEvent.KEYCODE_DPAD_UP);
        time("Films: All films chosen (30,000 posters)", 1, () -> {
            press(a, KeyEvent.KEYCODE_DPAD_DOWN);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        });
        // Into the posters, and along them.
        time("Films: into the posters and along them", 5, () -> press(a, KeyEvent.KEYCODE_DPAD_RIGHT));
        home(a);
        a.openSearch();
        idle();
        SearchScreen s = (SearchScreen) a.top();
        time("Search: \"lantern\" typed (main thread)", 5, () -> s.type("lantern"), BigPlaylistTest::settle);
        time("Search: \"lantern\" typed, to its results shown", false, 5, null, () -> {
            s.type("lantern");
            settle();
        }, null);
        home(a);

        // A guide with nothing sport on now: Home's Sport row then has every channel looked at. (Read in the previews'
        // way, so that Home's mini TV, told of the new channels, makes no player.)
        Exec.inline = true;
        lib.useForPreview(lib.every(), BigPlaylist.guideWithoutSport(lib.every(), System.currentTimeMillis()));
        idle();
        Exec.inline = false;
        settle();
        time("Home: its rows, nothing sport on now (every channel looked at)", 5, () -> a.homeScreen().tick(), BigPlaylistTest::settle);

        for (Map.Entry<String, double[]> e : times.entrySet()) {
            if (!Boolean.TRUE.equals(onMain.get(e.getKey()))) continue;
            assertTrue(e.getKey() + " kept the main thread " + e.getValue()[1] + " ms", e.getValue()[1] < LIMIT_MS);
        }
    }
}
