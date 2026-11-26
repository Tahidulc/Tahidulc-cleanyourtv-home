package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.CategorySet;
import com.gathin.homefieldtv.engine.DayRows;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.TmdbLists;
import com.gathin.homefieldtv.prefs.HomeStyle;

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

/**
 * The Editorial Home, Movies and TV Shows as the family's remote finds them: Editorial as it comes and Classic when
 * chosen (Settings › Appearance › Home style); the rows in the day's order (DayRows), a row with nothing in the playlists left
 * out; the first rows asked for as the page opens and the next as the remote goes down; the title the remote is on wide
 * and the one it left a poster again; its line under the row; Down past the last row back to the first; Back to the tab
 * bar; OK opening a title, Continue Watching carrying on, the carousel's View Details; the title page's menu down the left.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class EditorialPageTest {
    private static final String[] FILMS = {"The Long Harbour (2019)", "Glass Meridian (2021)", "Ember Road (2018)", "Paper Kites (2022)",
            "Midnight Orchard (2020)", "Copper Valley (2017)", "Salt & Starlight (2023)", "Northbound (2016)", "Quiet Meridian (2024)",
            "Lantern Street (2015)", "Fern Hollow (2021)", "Iron Bay (2019)"};
    private static final String[] SHOWS = {"Harbour Lights", "Signal Lost", "The Quiet County", "Kitchen Table", "Fern & Fable", "Low Tide"};
    private ActivityController<TvActivity> tv;
    private Context app;

    @After public void after() {
        drain();
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        EditorialData.clear();
        DayRows.now = null;
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
            Resume.clear(app);
        }
        Exec.inline = false;
        // The next test's own app makes its own catalogue.
        EditorialData.freshCatalog();
    }

    /** Work still under way off the main thread (another test's catalogue read, a picture) done before going on. */
    static void drain() {
        for (int i = 0; i < 20_000 && com.gathin.homefieldtv.engine.Exec.busy() > 0; i++) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TabBar.REST_MS + 100));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    /** The made-up family playlist (and TMDB as EditorialData makes it, unless tmdb is false), then the app. */
    private TvActivity start(boolean tmdb) {
        drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        // The day's rows for now, the same all through the test.
        DayRows.now = System.currentTimeMillis();
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tUK | Comedy Movies");
        rows.add("c\tm\t2\tDrama and mystery");
        rows.add("c\ts\t1\tDrama and crime");
        long added = System.currentTimeMillis() / 1000;
        for (int i = 0; i < FILMS.length; i++) {
            String year = FILMS[i].substring(FILMS[i].length() - 5, FILMS[i].length() - 1);
            // A poster's address that is never there: drawn as a card in a colour of its own.
            rows.add("m\t" + (i + 1) + "\t" + FILMS[i] + "\t" + (i % 2 + 1) + "\tfile:/nowhere/f" + i + ".jpg\t7.1\t" + (added - i * 3600L) * 1000L
                    + "\tmkv\t" + year + "\t\t\t");
        }
        for (int i = 0; i < SHOWS.length; i++) {
            rows.add("s\t" + (i + 1) + "\t" + SHOWS[i] + "\t1\t\t7.4\t" + (added - i * 7200L) * 1000L + "\t" + (2019 + i % 4)
                    + "\tA harbour town keeps its secrets.\t");
        }
        // The first series' page read already: its episodes.
        Map<String, Catalog.Details> details = new HashMap<>();
        List<String[]> eps = new ArrayList<>();
        for (int k = 1; k <= 4; k++) eps.add(new String[]{"e" + k, "1", String.valueOf(k), "Chapter " + k, "", "", "44", "2021-01-0" + k});
        details.put("p0|s|1", Catalog.previewDetails("A harbour town keeps its secrets.", "Drama", "Rowan Vale", "Ada Holt", "2019", "", "7.9", "15",
                44, eps.toArray(new String[0][])));
        Catalog.get(app).useForPreview(rows, details);
        Resume.clear(app);
        Resume.save(app, new Resume.Point("p0|m|3", "", "Ember Road", "", "", 20 * 60_000L, 100 * 60_000L, System.currentTimeMillis()));
        if (tmdb) EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        else EditorialData.none();
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        return tv.get();
    }

    private static List<String> ids(EditorialPage page) {
        List<String> out = new ArrayList<>();
        for (Editorial.Row r : page.view().rows()) out.add(r.id);
        return out;
    }

    @Test public void homeIsEditorialAsItComesAndClassicWhenChosen() {
        TvActivity a = start(true);
        assertNotNull(a.homeScreen().editorial());
        assertEquals(HomeStyle.EDITORIAL, HomeStyle.get(a));
        tv.pause().stop().destroy();
        HomeStyle.set(app, HomeStyle.CLASSIC);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        assertNull("Classic: Home's one big card at a time", tv.get().homeScreen().editorial());
        tv.get().fromHome(new BrowseScreen(tv.get(), false));
        idle();
        assertNull(((BrowseScreen) tv.get().top()).editorial());
        assertEquals("Home style", new MoreSettingsProbe(tv.get()).title());
    }

    /** Settings' Home style picker, as Settings › Appearance has it. */
    private static final class MoreSettingsProbe {
        private final TvActivity a;

        MoreSettingsProbe(TvActivity a) {
            this.a = a;
        }

        String title() {
            a.openSettings();
            idle();
            SettingsScreen s = (SettingsScreen) a.top();
            SettingsScreen.Picker p = new MoreSettings(a, s).picker("homestyle");
            assertEquals(2, p.names.length);
            assertEquals("Editorial", p.names[0]);
            assertEquals("Classic", p.names[1]);
            assertEquals(HomeStyle.CLASSIC, p.chosen);
            assertFalse(p.note.toLowerCase(java.util.Locale.ROOT).contains("apple"));
            return p.title;
        }
    }

    @Test public void theRowsAreInTheDaysOrderAndEmptyOnesAreLeftOut() {
        TvActivity a = start(true);
        EditorialPage home = a.homeScreen().editorial();
        List<String> shown = ids(home), table = new ArrayList<>();
        for (CategorySet.Entry e : DayRows.today(CategorySet.HOME)) if (e.kind == CategorySet.ROW) table.add(e.id);
        assertEquals(DayRows.key(DayRows.local(DayRows.now())), home.previewDayKey());
        assertFalse(shown.isEmpty());
        assertEquals("continue", shown.get(0));
        int last = -1;
        for (String id : shown) {
            int at = table.indexOf(id);
            assertTrue(id + " in the day's order", at > last);
            last = at;
        }
        // Rows with no title in them are never shown: here, TMDB knows only one list.
        tv.pause().stop().destroy();
        Map<String, TmdbLists.Page> pages = new HashMap<>();
        List<TmdbLists.Item> items = new ArrayList<>();
        for (Catalog.Title t : Catalog.get(app).films()) items.add(new TmdbLists.Item(t.name.length(), false, t.name, t.name, t.year, "", "", "", "7", null));
        pages.put("trending/all/day#1", new TmdbLists.Page(items, 1, 1));
        EditorialData.only(pages);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        List<String> now = ids(tv.get().homeScreen().editorial());
        assertTrue("Top 10 Today, every day", now.contains("top10"));
        assertTrue("New This Week: the playlists' own", now.contains("fresh"));
        assertFalse(now.contains("acclaimed"));
        assertFalse(now.contains("oscars"));
        assertFalse(now.contains("because.0"));
    }

    @Test public void theFirstRowsAreAskedForAsThePageOpensAndTheNextOnTheWayDown() {
        TvActivity a = start(true);
        EditorialPage home = a.homeScreen().editorial();
        int first = home.previewAsked();
        assertEquals("the first rows, and the carousel's", EditorialPage.FIRST + 1, first);
        int rows = home.view().rows().size();
        home.previewFocus(rows - 1, 0);
        idle();
        assertTrue("more asked on the way down", home.previewAsked() > first);
        assertTrue(home.view().rows().size() > rows);
    }

    @Test public void theTitleTheRemoteIsOnIsWideAndTheOneItLeftAPoster() {
        TvActivity a = start(true);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        EditorialPage home = a.homeScreen().editorial();
        int i = home.previewRow("top10");
        home.previewFocus(i, 0);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        float[] w = home.view().previewWidths();
        assertEquals(Editorial.WIDE_W, w[0], 0.01f);
        assertEquals(Editorial.POSTER_W, w[1], 0.01f);
        // Its line under the row: its score, year, genre.
        Editorial.Card on = home.view().card();
        String line = Editorial.line(on);
        assertTrue(line, line.matches("\\d+% \\d{4} \\S.*"));
        assertFalse(on.story.isEmpty());
    }

    @Test public void downPastTheLastRowGoesBackToTheFirstAndBackGoesToTheTabBar() {
        TvActivity a = start(false);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        EditorialPage home = a.homeScreen().editorial();
        int rows = home.view().rows().size();
        assertTrue(rows >= 2);
        home.previewFocus(rows - 1, 0);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(home.view().rows().get(0), home.view().current());
        assertFalse(a.tabs().active());
        press(a, KeyEvent.KEYCODE_BACK);
        assertTrue("Back: the tab bar", a.tabs().active());
        assertTrue(a.top() instanceof HomeScreen);
    }

    @Test public void okOpensTheTitleAndTheCarouselHasViewDetails() {
        TvActivity a = start(true);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        EditorialPage home = a.homeScreen().editorial();
        home.previewFocus(home.previewRow("top10"), 1);
        assertNotNull(home.view().card().title());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof TitleScreen);
        // Movies: its carousel, OK is View Details.
        a.home();
        idle();
        a.fromHome(new BrowseScreen(a, false));
        idle();
        if (a.tabs().active()) a.tabs().leave();
        idle();
        EditorialPage movies = ((BrowseScreen) a.top()).editorial();
        assertEquals(Editorial.HERO, movies.view().area());
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof TitleScreen);
    }

    @Test public void theTitlePageIsCoverBInBothHomeStyles() {
        TvActivity a = start(true);
        a.openTitle(Catalog.get(app).films().get(0));
        idle();
        CoverPage cover = ((TitleScreen) a.top()).cover();
        assertTrue(cover.isOpen());
        // Not started: no Resume, its sources straight away; its buttons beside the facts.
        assertEquals(0, cover.actionCount());
        assertEquals("My List", cover.pillWords(0));
        assertEquals("More like this", cover.pillWords(cover.pillCount() - 1));
        // Ember Road, left part-way: Resume first, then From the start; and Remove.
        Catalog.Title ember = Catalog.get(app).title("p0|m|3");
        a.openTitle(ember);
        idle();
        CoverPage left = ((TitleScreen) a.top()).cover();
        assertEquals("Resume", left.actionName(0));
        assertEquals("1h 20m left", left.actionWords(0));
        assertEquals("From the start", left.actionName(1));
        assertEquals(0, left.focus());
        assertEquals("Remove", left.pillWords(left.pillCount() - 1));
        a.home();
        idle();
        // A series: Play S1 E1 and From the start, no sources of its own.
        Catalog.Title show = Catalog.get(app).title("p0|s|1");
        a.openTitle(show);
        idle();
        CoverPage series = ((TitleScreen) a.top()).cover();
        assertEquals("Play", series.actionName(0));
        assertEquals("S1 E1", series.actionWords(0));
        assertEquals(0, series.rowCount());
        // Classic has the same cover.
        tv.pause().stop().destroy();
        HomeStyle.set(app, HomeStyle.CLASSIC);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        tv.get().openTitle(ember);
        idle();
        assertEquals("Resume", ((TitleScreen) tv.get().top()).cover().actionName(0));
    }
}
