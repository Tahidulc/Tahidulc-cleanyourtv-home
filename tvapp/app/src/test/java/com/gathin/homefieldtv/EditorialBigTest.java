package com.gathin.homefieldtv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.BigPlaylist;
import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CategorySet;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Shelves;
import com.gathin.homefieldtv.engine.MatchIndex;
import com.gathin.homefieldtv.prefs.HomeStyle;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Editorial Home, Movies and TV Shows with the family's playlist at its largest (BigPlaylist: 40,000 films and
 * series, 50,000 channels): how long the main thread takes as each page opens, as the catalogue changes under it, and
 * as the remote goes along a row and down the rows, each well under BigPlaylistTest's limit however many titles there are;
 * and, for the record, what is worked out off the main thread (the names' index, a TMDB row matched).
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class EditorialBigTest {
    /** As BigPlaylistTest: the most any one key or screen may keep the main thread on the JVM. */
    private static final double LIMIT_MS = 250;
    private final Map<String, double[]> times = new LinkedHashMap<>();
    private final Map<String, Boolean> onMain = new LinkedHashMap<>();
    private Context app;

    @After public void after() {
        StringBuilder b = new StringBuilder("\nEDITORIAL, THE BIG PLAYLIST (JVM ms: the first time, then the median)\n");
        for (Map.Entry<String, double[]> e : times.entrySet()) {
            b.append(String.format(Locale.UK, "%-70s %8.1f %8.1f %s%n", e.getKey(), e.getValue()[0], e.getValue()[1],
                    onMain.get(e.getKey()) ? "main" : "off the main thread"));
        }
        System.out.println(b);
        Exec.inline = false;
        EditorialPageTest.drain();
        EditorialData.clear();
        if (app != null) BigPlaylist.unload(app);
        Exec.inline = false;
        // The next test's own app makes its own catalogue.
        EditorialData.freshCatalog();
    }

    private interface Step {
        void run() throws Exception;
    }

    private static long realNanos() {
        java.time.Instant i = java.time.Clock.systemUTC().instant();
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

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

    private void time(String what, boolean main, int n, Step r, Step after) throws Exception {
        double[] ms = new double[n];
        for (int i = 0; i < n; i++) {
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

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
    }

    @Test public void fortyThousandTitlesKeepTheMainThreadFree() throws Exception {
        EditorialPageTest.drain();
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        BigPlaylist.load(app);
        Catalog cat = Catalog.get(app);
        assertTrue(cat.films().size() + cat.series().size() >= 39_000);
        // TMDB's lists made from a few hundred of them (the rest is the playlists' own).
        EditorialData.load(cat.films().subList(0, 300), cat.series().subList(0, 150));

        // ---- off the main thread ----------------------------------------------------------------------------------
        time("MatchIndex: every film's and series' name (each catalogue)", false, 3,
                () -> MatchIndex.of(cat.films().subList(0, cat.films().size() - 1), cat.series()), null);
        CategorySet.Entry trending = CategorySet.of(CategorySet.HOME).get(2);
        time("Shelves.make: Trending Movies matched to the playlists", false, 5, () -> Shelves.make(app, trending), null);

        // ---- the screens, as the remote opens them ----------------------------------------------------------------
        HomeStyle.set(app, HomeStyle.EDITORIAL);
        TvActivity a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idle();
        Exec.inline = false;
        settle();
        EditorialPage home = a.homeScreen().editorial();
        assertFalse(home.view().rows().isEmpty());
        time("Editorial Home: made again (the catalogue changed)", true, 5, () -> a.homeScreen().catalogChanged(), EditorialBigTest::settle);
        time("Editorial Home: shown again (back from a film)", true, 5, () -> a.homeScreen().shown(), EditorialBigTest::settle);
        if (a.tabs().active()) a.tabs().leave();
        idle();
        home.previewFocus(0, 0);
        time("Editorial Home: along a row", true, 9, () -> press(a, KeyEvent.KEYCODE_DPAD_RIGHT), null);
        time("Editorial Home: down the rows (the next asked for)", true, 9, () -> press(a, KeyEvent.KEYCODE_DPAD_DOWN), EditorialBigTest::settle);
        time("Editorial Movies: opened", true, 3, () -> a.openBrowse(false), () -> {
            a.home();
            settle();
        });
        time("Editorial TV Shows: opened", true, 3, () -> a.openBrowse(true), () -> {
            a.home();
            settle();
        });
        for (Map.Entry<String, double[]> e : times.entrySet()) {
            if (onMain.get(e.getKey())) assertTrue(e.getKey() + " kept the main thread " + e.getValue()[1] + " ms", e.getValue()[1] < LIMIT_MS);
        }
    }
}
