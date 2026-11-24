package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;
import com.gathin.homefieldtv.engine.Videos;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A series' page as the family's recording of a series page has it (made-up series, three seasons): Down from the
 * buttons the page rises to its section; Left and Right along the one row of every season's episodes, the season tab
 * following by itself into the next season; Left and Right on the tabs jumping the row to that season's first
 * episode; the remote's episode lifted, the one it left settled; its words on the panel cut cleanly (the name to one
 * line, the story to two, each inside the panel); the Videos row from TMDB's videos, OK on an episode still playing it.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class SeriesPageTest {
    private static final String LONG_PLOT = "As the harbour wall gives way in the worst storm for a century, the new ferry captain has "
            + "one night to bring the lifeboat crew home, the whole town watching from the cliff, and nobody quite telling her the truth "
            + "about the last captain, the lighthouse, or the letter in the drawer.";
    private ActivityController<TvActivity> tv;
    private Context app;

    @After public void after() {
        EditorialPageTest.drain();
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        EditorialData.clear();
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
            Resume.clear(app);
        }
        Exec.inline = false;
        EditorialData.freshCatalog();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(TabBar.REST_MS + 100));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void press(int code) {
        long t = SystemClock.uptimeMillis();
        tv.get().dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        tv.get().dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    /** A made-up series of three seasons (6, 5 and 4 episodes; one with a very long name and story), its page open. */
    private TitlePage open() {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\ts\t1\tDrama and crime");
        long added = System.currentTimeMillis() / 1000;
        rows.add("s\t1\tHarbour Lights\t1\t\t7.4\t" + added * 1000L + "\t2021\tA harbour town keeps its secrets.\t");
        Map<String, Catalog.Details> details = new HashMap<>();
        List<String[]> eps = new ArrayList<>();
        int[] counts = {6, 5, 4};
        for (int s = 0; s < counts.length; s++) {
            for (int k = 1; k <= counts[s]; k++) {
                boolean long_ = s == 1 && k == 2;
                eps.add(new String[]{"e" + (s + 1) + "-" + k, String.valueOf(s + 1), String.valueOf(k),
                        long_ ? "The Night the Harbour Wall Gave Way and Nobody Came Home Until the Morning" : "Chapter " + k,
                        long_ ? LONG_PLOT : "The night the lights went out.", "", String.valueOf(50 + k), (2021 + s) + "-06-1" + k});
            }
        }
        details.put("p0|s|1", Catalog.previewDetails("A harbour town keeps its secrets.", "Drama", "Rowan Vale", "Ada Holt", "2021", "",
                "7.9", "15", 52, eps.toArray(new String[0][])));
        Catalog.get(app).useForPreview(rows, details);
        Resume.clear(app);
        EditorialData.none();
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        tv.get().openTitle(Catalog.get(app).title("p0|s|1"));
        idle();
        if (tv.get().tabs() != null && tv.get().tabs().active()) tv.get().tabs().leave();
        idle();
        return ((TitleScreen) tv.get().top()).page();
    }

    /** The page drawn a few times (its glides and lifts stepped, as on screen). */
    private static void draw(TitlePage page) {
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < 3; i++) page.draw(new Canvas(b));
        b.recycle();
    }

    @Test public void theTabFollowsTheRowIntoTheNextSeason() {
        TitlePage page = open();
        int second = page.previewFirstOf(1), third = page.previewFirstOf(2);
        assertEquals(6, second);
        assertEquals(11, third);
        // The last of season 1, then Right: the first of season 2, and its tab in view by itself.
        page.focus(TitlePage.EPISODES, second - 1);
        assertEquals(0, page.previewSeason());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(second, page.previewEpisode());
        assertEquals(1, page.previewSeason());
        press(KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, page.previewSeason());
        // Along to the end of the row, through season 2 into season 3.
        for (int i = 0; i < 10; i++) press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(14, page.previewEpisode());
        assertEquals(2, page.previewSeason());
    }

    @Test public void aTabJumpsTheRowToItsFirstEpisode() {
        TitlePage page = open();
        page.focus(TitlePage.EPISODES, 2);
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals("up to the tabs", TitlePage.SEASONS, page.part());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, page.previewSeason());
        assertEquals(page.previewFirstOf(1), page.previewEpisode());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(2, page.previewSeason());
        assertEquals(page.previewFirstOf(2), page.previewEpisode());
        // Down from the tabs: on that first episode.
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.EPISODES, page.part());
        assertEquals(page.previewFirstOf(2), page.previewEpisode());
    }

    @Test public void downFromTheButtonsRisesToTheSeriesSection() {
        TitlePage page = open();
        assertEquals(TitlePage.BUTTONS, page.part());
        page.toPart(TitlePage.EPISODES);
        draw(page);
        // With the menu down the left (Editorial, as it comes) what is under it starts MENU_SHIFT lower.
        float shift = page.previewVertical() ? TitlePage.MENU_SHIFT : 0f;
        assertEquals(TitlePage.SECTION + shift, page.risen(), 0.5f);
        press(KeyEvent.KEYCODE_BACK);
        draw(page);
        assertEquals(TitlePage.BUTTONS, page.part());
        assertEquals(0f, page.risen(), 0.5f);
    }

    @Test public void theRemotesEpisodeLiftsAndTheOneItLeftSettles() {
        TitlePage page = open();
        page.focus(TitlePage.EPISODES, 3);
        draw(page);
        assertTrue("lifted: " + page.previewLift(3), page.previewLift(3) > 0.9f);
        assertEquals(0f, page.previewLift(2), 0.001f);
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        draw(page);
        assertTrue(page.previewLift(4) > 0.9f);
        assertEquals("settled back", 0f, page.previewLift(3), 0.01f);
        // Up on the tabs, nothing in the row is lifted.
        page.focus(TitlePage.SEASONS, 0);
        draw(page);
        assertEquals(0f, page.previewLift(0), 0.01f);
    }

    @Test public void thePanelsWordsAreCutCleanly() {
        TitlePage page = open();
        int i = page.previewFirstOf(1) + 1;
        page.focus(TitlePage.EPISODES, i);
        float room = page.previewRoom();
        String name = page.previewName(i);
        assertTrue(name, name.length() < "The Night the Harbour Wall Gave Way and Nobody Came Home Until the Morning".length());
        assertTrue("the name fits its line", page.previewNamePaint().measureText(name) <= room + 0.5f);
        StaticLayout story = page.previewStory(i);
        assertEquals("two lines of story", 2, story.getLineCount());
        for (int k = 0; k < story.getLineCount(); k++) assertTrue("line " + k + " inside the panel", story.getLineWidth(k) <= room + 0.5f);
        assertTrue("the second line ends cut", story.getEllipsisCount(1) > 0);
        // A short one is left whole.
        assertEquals("Chapter 1", page.previewName(0));
        assertEquals(1, page.previewStory(0).getLineCount());
        assertEquals(0, page.previewStory(0).getEllipsisCount(0));
    }

    @Test public void theVideosRowComesFromTmdbsVideos() {
        TitlePage page = open();
        assertTrue("no videos without TMDB's", page.previewVideos().isEmpty());
        List<Videos.Video> vs = new ArrayList<>();
        vs.add(new Videos.Video("aaaaaaaaaa1", "Behind the harbour", "Behind the Scenes", "en", true, "2023-01-01"));
        vs.add(new Videos.Video("aaaaaaaaaa2", "Season 2 teaser", "Teaser", "en", true, "2022-01-01"));
        vs.add(new Videos.Video("aaaaaaaaaa3", "Official trailer", "Trailer", "en", true, "2021-01-01"));
        ((TitleScreen) tv.get().top()).previewExtras(new Tmdb.Extras("", null, null, "", "15", vs, false));
        idle();
        List<String> keys = page.previewVideos();
        assertEquals("trailers, then teasers, then the rest", List.of("aaaaaaaaaa3", "aaaaaaaaaa2", "aaaaaaaaaa1"), keys);
        page.focus(TitlePage.EPISODES, 0);
        press(KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(TitlePage.VIDEOS, page.part());
        press(KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, page.previewVideo());
        press(KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(TitlePage.EPISODES, page.part());
        assertEquals("https://i.ytimg.com/vi/aaaaaaaaaa3/mqdefault.jpg", Videos.thumb("aaaaaaaaaa3"));
        assertEquals("", Videos.thumb("not a key"));
    }

    @Test public void anEpisodesLastLine() {
        assertEquals("58m  16 Jun 2024", TitlePage.episodeLine(58, "2024-06-16"));
        assertEquals("1h 9m  23 Jun 2024", TitlePage.episodeLine(69, "2024-06-23"));
        assertEquals("16 Jun 2024", TitlePage.episodeLine(0, "2024-06-16"));
        assertEquals("58m", TitlePage.episodeLine(58, ""));
        assertEquals("", TitlePage.episodeLine(0, "soon"));
    }
}
