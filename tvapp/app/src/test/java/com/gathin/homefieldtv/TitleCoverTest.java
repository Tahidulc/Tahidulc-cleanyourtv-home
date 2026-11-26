package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Copies;
import com.gathin.homefieldtv.engine.DayRows;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MyList;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.HomeStyle;
import com.gathin.homefieldtv.prefs.OtherPrefs;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A title's page as the family's cover B (made-up playlist, invented names): a film's and a series' laid out as B in
 * both Home styles; the remote's order down it (Resume first where it was left part-way, else the best source; the
 * buttons Up from the first row; More like this under it all, the button jumping there, Up coming back); OK on each row
 * (Resume and From the start from the copy it was started on, a source from where it was left, a series' episode on its
 * own cover); the buttons; Back; Continue watching, the carousel and the rows opening the page with Resume on; the next
 * episode still straight away; and the trailer in the top card (after its rest, never while the remote moves; its stops
 * and the poster coming back).
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class TitleCoverTest {
    private static final String LANTERN = "p0|m|1", GLASS = "p0|m|2", EMBER = "p0|m|3", COAST = "p0|s|1";
    private ActivityController<TvActivity> tv;
    private Context app;
    private TrailerTest.Stub stub;

    @After public void after() {
        EditorialPageTest.drain();
        Trailer.standIn = null;
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        EditorialData.clear();
        DayRows.now = null;
        if (app != null) {
            if (MyList.has(app, LANTERN)) MyList.toggle(app, LANTERN);
            HomeStyle.set(app, HomeStyle.EDITORIAL);
            Library.get(app).clear();
            Sources.clear(app);
            Resume.clear(app);
            Copies.clear(app);
        }
        Exec.inline = false;
        EditorialData.freshCatalog();
    }

    private static void idle() {
        rest(TabBar.REST_MS + 100);
    }

    private static void rest(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void press(int code) {
        long t = SystemClock.uptimeMillis();
        tv.get().dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        tv.get().dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** The made-up family playlist: three films (one with a trailer) and a series of two seasons; then the app. */
    private TvActivity start(boolean tmdb) {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        DayRows.now = System.currentTimeMillis();
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tDrama and mystery");
        rows.add("c\ts\t1\tDrama and crime");
        long added = System.currentTimeMillis() / 1000;
        String[] films = {"The Lantern House (2025) 4K", "Glass Meridian (2021)", "Ember Road (2018)"};
        for (int i = 0; i < films.length; i++) {
            // A poster's address that is never there (the carousel takes titles with a picture).
            rows.add("m\t" + (i + 1) + "\t" + films[i] + "\t1\tfile:/nowhere/f" + i + ".jpg\t7.1\t" + (added - i * 3600L) * 1000L
                    + "\tmkv\t2020\t\t\t");
        }
        rows.add("s\t1\tThe Quiet Coast\t1\t\t7.4\t" + added * 1000L + "\t2022\tA harbour town keeps its secrets.\t");
        rows.add("s\t2\tHarbour Lights\t1\t\t7.0\t" + (added - 60) * 1000L + "\t2021\tA made-up lighthouse.\t");
        Map<String, Catalog.Details> details = new HashMap<>();
        details.put(LANTERN, Catalog.previewDetails("A widow returns to the lakeside house her family left.", "Drama", "Mara Ashby, Tom Reyes",
                "Ines Hale", "2025", "", "7.9", "12A", 134, null, "MadeUpTrail"));
        details.put(GLASS, Catalog.previewDetails("A made-up story of a harbour.", "Drama", "", "", "2021", "", "7.0", "15", 101, null));
        details.put(EMBER, Catalog.previewDetails("A made-up road.", "Drama", "", "", "2018", "", "6.8", "12", 100, null));
        String[][] eps = {{"e1", "1", "1", "Arrival", "The ferry comes in late.", "", "45"}, {"e2", "1", "2", "The Lamp", "", "", "44"},
                {"e3", "2", "1", "Fog", "", "", "46"}};
        details.put(COAST, Catalog.previewDetails("A harbour town keeps its secrets.", "Drama", "", "", "2022", "", "8.1", "12", 0, eps));
        Catalog.get(app).useForPreview(rows, details);
        Resume.clear(app);
        Copies.clear(app);
        if (MyList.has(app, LANTERN)) MyList.toggle(app, LANTERN);
        if (tmdb) EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        else EditorialData.none();
        stub = new TrailerTest.Stub();
        Trailer.standIn = stub;
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        return tv.get();
    }

    private void leaveTabs() {
        if (tv.get().tabs() != null && tv.get().tabs().active()) tv.get().tabs().leave();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private TitleScreen open(String key) {
        tv.get().openTitle(Catalog.get(app).title(key));
        idle();
        leaveTabs();
        return (TitleScreen) tv.get().top();
    }

    private static Resume.Point left(String key, String episode, long at, long length) {
        return new Resume.Point(key, episode, "A made-up title", episode.isEmpty() ? "" : "S1 E2  ·  The Lamp", "", at, length,
                System.currentTimeMillis());
    }

    private static List<Catalog.Play> copies() {
        Catalog.Play torbox = Catalog.previewPlay("torbox:1:2", "");
        torbox.picture = Catalog.PICTURE_4K;
        torbox.hdr = "Dolby Vision";
        torbox.sound = "Dolby Atmos";
        torbox.size = 58L << 30;
        Catalog.Play home = Catalog.previewPlay("http://a.example/1.mkv", "Home");
        home.picture = Catalog.PICTURE_1080;
        home.hdr = "HDR10";
        home.sound = "Dolby Digital+ 5.1";
        Catalog.Play spare = Catalog.previewPlay("http://b.example/1.mp4", "Spare");
        spare.picture = Catalog.PICTURE_720;
        spare.sound = "Stereo";
        return Arrays.asList(torbox, home, spare);
    }

    /** Every activity started so far forgotten. */
    private void drainStarted() {
        while (shadowOf(tv.get()).getNextStartedActivity() != null) {
            // taken off the list
        }
    }

    /** The page drawn once (its glides stepped, the cover under it told how far it has risen). */
    private static void draw(TitleScreen s) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888);
        s.page().draw(new android.graphics.Canvas(b));
        b.recycle();
    }

    /** The player started (and taken off the list of those started), or null. */
    private Intent started() {
        Intent i = shadowOf(tv.get()).getNextStartedActivity();
        return i != null && i.getComponent() != null && i.getComponent().getClassName().equals(VodActivity.class.getName()) ? i : null;
    }

    // ---- laid out as B -----------------------------------------------------------------------------------------------

    @Test public void aFilmsPageIsCoverB() {
        start(false);
        TitleScreen s = open(GLASS);
        CoverPage c = s.cover();
        assertTrue(c.isOpen());
        assertEquals("Glass Meridian", c.titleWords());
        assertTrue(c.plotWords().startsWith("A made-up story"));
        // Not started: no Resume; its sources straight away, the best first with the remote on it.
        assertEquals(0, c.actionCount());
        assertEquals(1, c.rowCount());
        s.previewCopies(copies());
        assertEquals(0, c.focus());
        assertTrue(c.bestLit());
        // Its buttons beside the facts: My List and More like this (no trailer, nothing to remove).
        assertEquals(Arrays.asList("My List", "More like this"), Arrays.asList(c.pillWords(0), c.pillWords(1)));
        assertEquals(Icons07.PLUS, c.pillIcon(0));
        assertTrue(c.buttonsBeside());
        assertEquals(TitlePage.COVER, s.page().part());
        assertEquals(0f, s.page().risen(), 0.5f);
        // With a trailer: Trailer first.
        TitleScreen lantern = open(LANTERN);
        assertEquals("Trailer", lantern.cover().pillWords(0));
    }

    @Test public void aSeriesPageIsCoverBWithItsSectionAndMoreLikeThisUnderIt() {
        start(false);
        Resume.save(app, left(COAST, "e2", 20 * 60_000L, 44 * 60_000L));
        TitleScreen s = open(COAST);
        CoverPage c = s.cover();
        TitlePage page = s.page();
        assertEquals("The Quiet Coast", c.titleWords());
        // Resume S1 E2 · 24 min left, From the start (S1 E1); no sources of its own.
        assertEquals(2, c.actionCount());
        assertEquals("Resume", c.actionName(0));
        assertEquals("S1 E2  ·  24 min left", c.actionWords(0));
        assertEquals("From the start", c.actionName(1));
        assertEquals("S1 E1", c.actionWords(1));
        assertEquals(0, c.rowCount());
        assertEquals(0, c.focus());
        // Down past From the start: the seasons, then the episodes, a screen down; then More like this at the foot.
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.SEASONS, page.part());
        assertEquals(TitlePage.SECTION, page.risen(), 0.5f);
        assertFalse("nothing lit on the cover", c.active());
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.EPISODES, page.part());
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.MORE, page.part());
        assertEquals("a screen under the section", TitlePage.SECTION + TitlePage.LOWER, page.risen(), 0.5f);
        // Up comes back the same way; Back from down here straight up to the cover, the remote where it was.
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(TitlePage.EPISODES, page.part());
        press(KeyEvent.KEYCODE_BACK);
        assertEquals(TitlePage.COVER, page.part());
        assertEquals(0f, page.risen(), 0.5f);
        assertEquals(1, c.focus());
        assertTrue(c.active());
        // More like this's button: straight down to it.
        int more = -1;
        for (int i = 0; i < c.pillCount(); i++) if (c.pillWords(i).equals("More like this")) more = i;
        c.focusRow(-1, more);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(TitlePage.MORE, page.part());
    }

    // ---- the remote's order and OK --------------------------------------------------------------------------------------

    @Test public void theRemotesOrderDownAFilmLeftPartWay() {
        start(false);
        Resume.save(app, left(LANTERN, "", 52 * 60_000L, 134 * 60_000L));
        TitleScreen s = open(LANTERN);
        CoverPage c = s.cover();
        TitlePage page = s.page();
        s.previewCopies(copies());
        // 1. Resume, the remote on it; 2. From the start; 3. the sources, BEST first.
        assertEquals("Resume", c.actionName(0));
        assertEquals("1h 22m left", c.actionWords(0));
        assertEquals(0, c.focus());
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(1, c.focus());
        assertEquals("From the start", c.actionName(1));
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertTrue(c.bestLit());
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(4, c.focus());
        // 4. Down from the last source: More like this, the page risen to it.
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.MORE, page.part());
        assertEquals("a screen under the cover", TitlePage.COVER_H, page.risen(), 0.5f);
        draw(s);
        assertEquals("the cover goes up with it", page.previewMoreRise(), c.scrolled(), 0.5f);
        assertFalse(c.active());
        // Along it, then Up: back on the cover, on the last source.
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, page.previewMore());
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(TitlePage.COVER, page.part());
        assertEquals(4, c.focus());
        draw(s);
        assertEquals(0f, c.scrolled(), 0.5f);
        // Up from the first row: the buttons (Left and Right along them); Up again: the tab bar.
        for (int i = 0; i < 4; i++) press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(0, c.focus());
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(c.onButtons());
        assertEquals("Trailer", c.pillWords(c.pillAt()));
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("My List", c.pillWords(c.pillAt()));
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(0, c.focus());
        press(KeyEvent.KEYCODE_DPAD_UP);
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(tv.get().tabs().active());
        assertFalse(c.active());
    }

    @Test public void okOnEachRow() {
        start(false);
        Resume.save(app, left(LANTERN, "", 52 * 60_000L, 134 * 60_000L));
        TitleScreen s = open(LANTERN);
        CoverPage c = s.cover();
        List<Catalog.Play> all = copies();
        s.previewCopies(all);
        Copies.started(app, LANTERN, "", all.get(2));
        drainStarted();
        // Resume: straight away (no second cover), from where it was left, from the copy it was started on.
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent resume = started();
        assertNotNull(resume);
        assertEquals(LANTERN, resume.getStringExtra(VodActivity.TITLE));
        assertEquals(52 * 60_000L, resume.getLongExtra(VodActivity.START, -1));
        assertFalse(resume.getBooleanExtra(VodActivity.CHOOSE, true));
        assertEquals(2, Copies.find(app, LANTERN, "", all));
        // From the start: from 0.
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent zero = started();
        assertEquals(0L, zero.getLongExtra(VodActivity.START, -1));
        assertFalse(zero.getBooleanExtra(VodActivity.CHOOSE, true));
        // The playlist's 1080p: from where it was left, kept as the copy it was started on (the player puts it first).
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent source = started();
        assertEquals(52 * 60_000L, source.getLongExtra(VodActivity.START, -1));
        assertFalse(source.getBooleanExtra(VodActivity.CHOOSE, true));
        assertEquals(1, Copies.find(app, LANTERN, "", all));
        // A series: Resume straight away; Play and From the start, and an episode, on that episode's own cover.
        Resume.save(app, left(COAST, "e2", 20 * 60_000L, 44 * 60_000L));
        TitleScreen show = open(COAST);
        drainStarted();
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent ep = started();
        assertEquals("e2", ep.getStringExtra(VodActivity.EPISODE));
        assertEquals(20 * 60_000L, ep.getLongExtra(VodActivity.START, -1));
        assertFalse(ep.getBooleanExtra(VodActivity.CHOOSE, true));
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent first = started();
        assertEquals("e1", first.getStringExtra(VodActivity.EPISODE));
        assertEquals(0L, first.getLongExtra(VodActivity.START, -1));
        assertTrue(first.getBooleanExtra(VodActivity.CHOOSE, false));
        show.page().focus(TitlePage.EPISODES, 2);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        Intent chosen = started();
        assertEquals("e3", chosen.getStringExtra(VodActivity.EPISODE));
        assertTrue(chosen.getBooleanExtra(VodActivity.CHOOSE, false));
    }

    @Test public void theButtons() {
        start(false);
        Resume.save(app, left(LANTERN, "", 52 * 60_000L, 134 * 60_000L));
        TitleScreen s = open(LANTERN);
        CoverPage c = s.cover();
        assertEquals(Arrays.asList("Trailer", "My List", "More like this", "Remove"),
                Arrays.asList(c.pillWords(0), c.pillWords(1), c.pillWords(2), c.pillWords(3)));
        // Trailer: the trailer itself, full screen here (Trailer.fullScreen: not the TV's YouTube app); Back to the page.
        drainStarted();
        c.focusRow(-1, 0);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        idle();
        assertTrue(tv.get().top() instanceof Trailer.Full);
        assertEquals("MadeUpTrail+sound", stub.played.get(stub.played.size() - 1));
        assertNull(shadowOf(tv.get()).getNextStartedActivity());
        press(KeyEvent.KEYCODE_BACK);
        idle();
        assertEquals(s, tv.get().top());
        // My List: on it, ticked; the remote stays on it.
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(MyList.has(app, LANTERN));
        assertEquals(Icons07.CHECK, c.pillIcon(1));
        assertTrue(c.onButtons());
        assertEquals(1, c.pillAt());
        // More like this: straight down to its row; Up back to the cover.
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(TitlePage.MORE, s.page().part());
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(TitlePage.COVER, s.page().part());
        // Remove: off Continue watching, Resume and Remove gone.
        c.focusRow(-1, 3);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertNull(Resume.get(app, LANTERN, ""));
        assertEquals(0, c.actionCount());
        assertEquals(3, c.pillCount());
    }

    @Test public void backGoesBackToWhereItWasOpenedFrom() {
        start(false);
        Screen home = tv.get().top();
        open(LANTERN);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.MORE, ((TitleScreen) tv.get().top()).page().part());
        // From More like this: up to the cover first.
        press(KeyEvent.KEYCODE_BACK);
        assertTrue(tv.get().top() instanceof TitleScreen);
        assertEquals(TitlePage.COVER, ((TitleScreen) tv.get().top()).page().part());
        // On the cover: back where it was opened from.
        press(KeyEvent.KEYCODE_BACK);
        idle();
        assertEquals(home, tv.get().top());
    }

    // ---- where it opens from ------------------------------------------------------------------------------------------

    @Test public void continueWatchingOpensThePageWithResumeOn() {
        start(true);
        Resume.save(app, left(EMBER, "", 20 * 60_000L, 100 * 60_000L));
        // Editorial Home's Continue Watching row (Home made again with it).
        tv.pause().stop().destroy();
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        TvActivity a = tv.get();
        EditorialPage home = a.homeScreen().editorial();
        Editorial.Row row = null;
        for (Editorial.Row r : home.view().rows()) if (r.id.equals("continue")) row = r;
        assertNotNull(row);
        drainStarted();
        home.chosen(row, row.cards.get(0));
        idle();
        assertNull("not played: its page", started());
        assertResumeOn(EMBER, 0);
        // My List's Continue watching.
        a.home();
        idle();
        MyListScreen list = new MyListScreen(a);
        a.fromHome(list);
        idle();
        list.previewList(MyListScreen.CONTINUE, true);
        list.ok();
        idle();
        assertNull(started());
        assertResumeOn(EMBER, 0);
        // Classic Home's big card: OK on Resume; MENU (From the Start) on From the start.
        tv.pause().stop().destroy();
        HomeStyle.set(app, HomeStyle.CLASSIC);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        Resume.Point p = Resume.get(app, EMBER, "");
        HomeCard.Card card = new HomeCard.Card(p, EMBER, "Ember Road", "", "", false, 0.2f);
        tv.get().homeScreen().chosen(card);
        idle();
        assertResumeOn(EMBER, 0);
        tv.get().home();
        idle();
        tv.get().homeScreen().second(card);
        idle();
        assertResumeOn(EMBER, 1);
        // A row's card (Movies' Continue watching): its page.
        tv.get().home();
        idle();
        assertTrue(tv.get().carryOn(p, false));
        idle();
        assertResumeOn(EMBER, 0);
    }

    @Test public void theCarouselOpensThePageWithResumeOn() {
        TvActivity a = start(true);
        for (String k : new String[]{LANTERN, GLASS, EMBER}) Resume.save(app, left(k, "", 20 * 60_000L, 100 * 60_000L));
        a.fromHome(new BrowseScreen(a, false));
        idle();
        leaveTabs();
        EditorialPage movies = ((BrowseScreen) a.top()).editorial();
        // The remote on the carousel's first card.
        movies.previewFocus(-1, 0);
        idle();
        assertEquals(Editorial.HERO, movies.view().area());
        drainStarted();
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        idle();
        assertTrue(a.top() instanceof TitleScreen);
        assertNull(started());
        CoverPage c = ((TitleScreen) a.top()).cover();
        assertEquals("Resume", c.actionName(0));
        assertEquals(0, c.focus());
    }

    private void assertResumeOn(String key, int row) {
        Screen top = tv.get().top();
        assertTrue(String.valueOf(top), top instanceof TitleScreen);
        CoverPage c = ((TitleScreen) top).cover();
        assertEquals(Catalog.get(app).title(key).name, c.titleWords());
        assertEquals("Resume", c.actionName(0));
        assertEquals(row, c.focus());
    }

    // ---- the next episode --------------------------------------------------------------------------------------------

    @Test public void theNextEpisodeStillPlaysStraightAway() {
        start(false);
        OtherPrefs.setAutoplayNext(app, true);
        VodActivity.previewDur = 45 * 60_000L;
        VodActivity.previewPos = 45 * 60_000L - 30_000L;
        VodActivity.previewPlaying = true;
        // An episode chosen with one source: no choice to make, it plays; at its end the next plays straight away.
        Intent i = new Intent(app, VodActivity.class).putExtra(VodActivity.TITLE, COAST).putExtra(VodActivity.EPISODE, "e1")
                .putExtra(VodActivity.START, 0L).putExtra(VodActivity.CHOOSE, true);
        VodActivity v = Robolectric.buildActivity(VodActivity.class, i).setup().get();
        idle();
        assertFalse(v.cover().isOpen());
        v.previewUpNext();
        assertTrue(v.upNextShowing());
        long t = SystemClock.uptimeMillis();
        v.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        v.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        idle();
        assertEquals("e2", v.episode().id);
        assertFalse("no cover for the next episode", v.cover().isOpen());
        assertEquals(0L, v.startAtForTests());
        v.finish();
    }

    // ---- the trailer in the top card ------------------------------------------------------------------------------------

    @Test public void theTrailerPlaysInTheTopCardAfterItsRestNeverWhileMoving() {
        start(false);
        TitleScreen s = open(LANTERN);
        CoverPage c = s.cover();
        assertTrue(stub.played.isEmpty());
        // Each move before the rest is over starts the rest again.
        for (int i = 0; i < 4; i++) {
            press(i % 2 == 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
            rest(Trailer.REST_MS - 200);
        }
        assertTrue("not while the remote moves", stub.played.isEmpty());
        rest(Trailer.REST_MS);
        assertEquals("with its sound to come once it is shown (Sound on as it comes)", Arrays.asList("MadeUpTrail+sound"), stub.played);
        assertEquals(0f, c.widened(), 0.01f);
        // Playing clean (hidden until YouTube's own things have gone): the poster widens into the 16:9 card, the window over it.
        TrailerTest.plays(stub, 150f);
        assertEquals(1f, c.widened(), 0.01f);
        android.graphics.RectF spot = new android.graphics.RectF();
        assertTrue(c.trailerSpot(spot));
        assertEquals(16f / 9f, spot.width() / spot.height(), 0.01f);
        // It plays on as the remote goes along the buttons and the rows.
        press(KeyEvent.KEYCODE_DPAD_UP);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.COVER, s.page().part());
        assertEquals(Trailer.SHOWN, s.trailerCard().state());
        // It ends: the poster comes back.
        stub.l.ended();
        assertEquals(0f, c.widened(), 0.01f);
        rest(Trailer.REST_MS + 100);
        assertEquals("not again until the page is shown again", 1, stub.played.size());
    }

    @Test public void theTrailerStopsAndThePosterComesBack() {
        start(false);
        TitleScreen s = open(LANTERN);
        CoverPage c = s.cover();
        // Going down to More like this.
        rest(Trailer.REST_MS + 100);
        TrailerTest.plays(stub, 150f);
        assertEquals(1f, c.widened(), 0.01f);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.MORE, s.page().part());
        assertEquals(0f, c.widened(), 0.01f);
        assertFalse(s.trailerCard().busy());
        // On OK to play a source (shown again first: as from the player).
        press(KeyEvent.KEYCODE_BACK);
        s.shown();
        rest(Trailer.REST_MS + 100);
        TrailerTest.plays(stub, 150f);
        assertEquals(1f, c.widened(), 0.01f);
        press(KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(s.trailerCard().busy());
        assertEquals(0f, c.widened(), 0.01f);
        // When anything opens over the page.
        s.shown();
        rest(Trailer.REST_MS + 100);
        TrailerTest.plays(stub, 150f);
        tv.get().openSearch();
        idle();
        assertFalse(s.trailerCard().busy());
        assertEquals(0f, c.widened(), 0.01f);
        // On leaving the page.
        tv.get().pop();
        idle();
        leaveTabs();
        rest(Trailer.REST_MS + 100);
        TrailerTest.plays(stub, 150f);
        assertEquals(1f, c.widened(), 0.01f);
        press(KeyEvent.KEYCODE_BACK);
        idle();
        assertFalse(s.trailerCard().busy());
        assertEquals(0f, c.widened(), 0.01f);
    }
}
