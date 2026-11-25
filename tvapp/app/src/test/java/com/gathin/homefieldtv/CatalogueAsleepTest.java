package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import org.junit.After;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The films and series aren't kept in memory while only live TV is on screen: half a minute into a channel full screen
 * they are let go (Catalog.sleep: the TCL's 200,000 titles are some 70 MB of its 192), Home keeping what it shows, and
 * read back as soon as Home is shown again. Never while a screen that shows them is open under the live one. Invented
 * channels and titles.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class CatalogueAsleepTest {
    private ActivityController<TvActivity> tv;
    private Context made;

    @After public void after() {
        Exec.inline = true;
        if (tv != null) tv.pause().stop().destroy();
        if (made != null) {
            MadeUpGuide.unload(made);
            Catalog.get(made).clear();
        }
        tv = null;
        Exec.inline = false;
    }

    private static void idle(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Until it holds (the catalogue is read and let go off the main thread), the main thread kept going meanwhile. */
    private static void until(BooleanSupplier done) throws InterruptedException {
        long end = System.currentTimeMillis() + 20_000;
        while (!done.getAsBoolean()) {
            assertTrue("in time", System.currentTimeMillis() < end);
            Thread.sleep(20);
            shadowOf(Looper.getMainLooper()).idle();
        }
        idle(50);
    }

    private static void follow(TvActivity a) throws Exception {
        java.lang.reflect.Method m = TvActivity.class.getDeclaredMethod("catalogueFollows");
        m.setAccessible(true);
        m.invoke(a);
    }

    @Test public void letGoWhileAChannelPlaysAndReadBackForHome() throws Exception {
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        made = app;
        Sources.clear(app);
        Sources.put(app, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        Map<String, Guide.Schedule> guide = new HashMap<>();
        List<Channel> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Channel c = new Channel("k:" + (700 + i), "Kestrel " + (i + 1), "Entertainment", "", "http://provider.invalid/live/" + i + ".ts", "",
                    null, null, 101 + i);
            out.add(c);
            long[] st = new long[6], en = new long[6];
            for (int k = 0; k < st.length; k++) {
                st[k] = now - now % half - half + k * half;
                en[k] = st[k] + half;
            }
            guide.put(c.key, MadeUpGuide.schedule(st, en));
        }
        Library lib = Library.get(app);
        lib.useForPreview(out, guide);
        Catalog cat = Catalog.get(app);
        cat.useForPreview(catalogue(now), new HashMap<>());
        int titles = cat.size();
        assertTrue(titles > 30_000);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        TvActivity a = tv.get();
        idle(400);
        a.watch(lib.byKey(out.get(0).key));
        idle(400);
        assertTrue(a.top() instanceof LiveScreen);
        assertFalse(cat.asleep());

        // As on the TV (the work off the main thread, the half minute on the clock).
        Exec.inline = false;
        follow(a);
        idle(TvActivity.CATALOGUE_SLEEP_MS - 1_000);
        assertFalse("not before its half minute", cat.asleep());
        idle(2_000);
        until(cat::asleep);
        assertTrue(cat.films().isEmpty());
        assertEquals("its size still known", titles, cat.size());

        // Home again: read back, and Home told.
        a.home();
        until(() -> !cat.asleep() && cat.ready());
        assertEquals(titles, cat.films().size() + cat.series().size());
        assertTrue(a.top() instanceof HomeScreen);
        // Home doesn't let them go.
        idle(TvActivity.CATALOGUE_SLEEP_MS + 1_000);
        assertFalse(cat.asleep());
    }

    /** 30,000 films and 10,000 series in 400 categories, as an Xtream login's catalogue file has them. */
    private static List<String> catalogue(long now) {
        List<String> rows = new ArrayList<>();
        for (int c = 0; c < 300; c++) rows.add("c\tm\t" + c + "\tEN| Films " + (c + 1));
        for (int c = 0; c < 100; c++) rows.add("c\ts\t" + c + "\tEN| Series " + (c + 1));
        for (int i = 0; i < 30_000; i++) {
            int year = 1980 + i % 47;
            rows.add("m\t" + (200_000 + i) + "\tEN| Harbour Lights " + (i / 100 + 1) + " (" + year + ")\t" + (i % 300)
                    + "\thttp://images.provider.invalid/p/m" + i + ".jpg\t" + (5 + i % 5) + ".1\t" + (now - (long) i * 3_600_000L) + "\tmkv\t"
                    + year + "\t\t\t");
        }
        for (int i = 0; i < 10_000; i++) {
            int year = 1990 + i % 37;
            rows.add("s\t" + (300_000 + i) + "\tEN| The Long Tide " + (i / 300 + 1) + "\t" + (i % 100) + "\thttp://images.provider.invalid/p/s" + i
                    + ".jpg\t7.2\t" + (now - (long) i * 7_200_000L) + "\t" + year + "\tA family keeps the harbour going.\t");
        }
        return rows;
    }
}
