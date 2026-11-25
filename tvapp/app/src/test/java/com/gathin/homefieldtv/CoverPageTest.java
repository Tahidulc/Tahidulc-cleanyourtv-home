package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cover a film or an episode opens on when Play is pressed (the family's cover B), on a made-up playlist with
 * invented names: it opens on Play with the best source lit, OK plays the one chosen from where it was left, Back goes
 * back to the title's page, its ground follows Settings › Colour theme and white words stay readable on every one of
 * them (and on a lighter one, darkened), an episode's version shows "S2 E1 · Fog" and its own story; the pop's timing.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class CoverPageTest {
    private static final String FILM = "p0|m|101", SERIES = "p0|s|201";
    private Context app;
    private Object catalogueWas;

    private static java.lang.reflect.Field catalogue() throws Exception {
        java.lang.reflect.Field f = Catalog.class.getDeclaredField("instance");
        f.setAccessible(true);
        return f;
    }

    @Before public void setUp() throws Exception {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        catalogueWas = catalogue().get(null);
        catalogue().set(null, null);
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.example:8080", "made-up", "made-up"));
        long now = System.currentTimeMillis();
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tFilms");
        rows.add("c\ts\t1\tDrama");
        rows.add("m\t101\tThe Lantern House (2025) 4K\t1\t\t7.9\t" + now + "\tmkv\t2025\t\t\t");
        rows.add("s\t201\tThe Quiet Coast\t1\t\t8.1\t" + now / 1000 + "\t2022\tA made-up series by the sea.\t");
        Map<String, Catalog.Details> pages = new HashMap<>();
        pages.put(FILM, Catalog.previewDetails("A widow returns to the lakeside house her family left.", "Drama, Mystery",
                "Mara Ashby, Tom Reyes, Elin Lund", "Vera Kosta", "2025-01-10", "", "7.9", "12A", 134, null));
        String[][] eps = {{"e1", "1", "1", "Arrival", "The ferry comes in late.", "", "45"},
                {"e2", "2", "1", "Fog", "The lamp goes dark in the fog.", "", "46"}};
        pages.put(SERIES, Catalog.previewDetails("A made-up series by the sea.", "Drama", "", "", "2022", "", "8.1", "12", 0, eps));
        Catalog.get(app).useForPreview(rows, pages);
        Resume.clear(app);
        CoverPage.previewElapsed = -1;
    }

    @After public void tearDown() throws Exception {
        Theme.save(app, Theme.HOMEFIELD, Theme.AMBER);
        Theme.apply(Theme.HOMEFIELD, Theme.AMBER);
        catalogue().set(null, catalogueWas);
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(VodActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    /** Play pressed on a title's page: the player asked to show every source first, from where it was left. */
    private VodActivity play(String key, String episode, long from) {
        Intent i = new Intent(app, VodActivity.class).putExtra(VodActivity.TITLE, key).putExtra(VodActivity.EPISODE, episode)
                .putExtra(VodActivity.START, from).putExtra(VodActivity.CHOOSE, true);
        VodActivity v = Robolectric.buildActivity(VodActivity.class, i).setup().get();
        idle();
        return v;
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
        home.sound = "5.1";
        Catalog.Play spare = Catalog.previewPlay("http://b.example/1.mp4", "Spare");
        spare.picture = Catalog.PICTURE_720;
        spare.sound = "Stereo";
        return Arrays.asList(torbox, home, spare);
    }

    @Test public void opensOnPlayTheBestLitOkPlaysItFromWhereItWasLeft() {
        VodActivity v = play(FILM, "", 600_000L);
        CoverPage c = v.cover();
        assertTrue(c.isOpen());
        assertFalse(v.overlayOn());
        assertEquals("The Lantern House", c.titleWords());
        assertTrue(c.plotWords().startsWith("A widow returns"));
        // Every source, the best first with the remote on it and marked BEST.
        List<Catalog.Play> all = copies();
        v.previewChoose(all);
        assertEquals(0, c.focus());
        assertTrue(c.bestLit());
        assertEquals(Arrays.asList("T", "4K", "TorBox  ·  Dolby Vision  ·  Atmos", "58.0 GB", ""),
                Arrays.asList(CoverPage.rowWords(all.get(0), "The Lantern House")));
        assertEquals(Arrays.asList("P", "720p", "Spare  ·  Stereo", "", ""), Arrays.asList(CoverPage.rowWords(all.get(2), "The Lantern House")));
        // Down and OK: the playlist's 1080p plays, from the place kept, the others its backups.
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(1, c.focus());
        assertFalse(c.bestLit());
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(c.isOpen());
        assertEquals(all.get(1), v.source());
        assertEquals(600_000L, v.startAtForTests());
        assertEquals("1 of 3 sources", v.copiesWords());
        assertTrue(v.overlayOn());
    }

    @Test public void eachSourceLabelledFromItsName() {
        // A TorBox file: its own name and its download's say what it is; the file's name under it (a pack's episode).
        Catalog.Play pack = Catalog.previewPlay("torbox:3:4", "");
        pack.picture = Catalog.PICTURE_4K;
        pack.name = "The Quiet Coast - S02E04.mkv";
        pack.item = "The.Quiet.Coast.S02.2160p.WEB-DL.DDP5.1.DV.HEVC-MADEUP";
        pack.size = 9L << 30;
        assertEquals(Arrays.asList("T", "4K", "TorBox  ·  Dolby Vision  ·  DD+ 5.1  ·  HEVC  ·  WEB-DL", "9.0 GB",
                "The Quiet Coast - S02E04.mkv"), Arrays.asList(CoverPage.rowWords(pack, "The Quiet Coast")));
        // A playlist's copy: its name's tags read already; its name not repeated when it is the title's.
        Catalog.Play iptv = Catalog.previewPlay("http://a.example/2.mkv", "Home");
        iptv.picture = Catalog.PICTURE_1080;
        iptv.name = "The Lantern House";
        iptv.hdr = "HDR10";
        iptv.sound = "Dolby Digital+ 5.1";
        assertEquals(Arrays.asList("P", "1080p", "Home  ·  HDR10  ·  DD+ 5.1", "", ""),
                Arrays.asList(CoverPage.rowWords(iptv, "The Lantern House")));
    }

    @Test public void aLongListScrollsTheBestFirst() {
        VodActivity v = play(FILM, "", 0);
        List<Catalog.Play> many = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            Catalog.Play p = Catalog.previewPlay("torbox:" + i + ":1", "");
            p.picture = i < 5 ? Catalog.PICTURE_4K : Catalog.PICTURE_1080;
            p.name = "The.Lantern.House.2025.Copy" + i + ".mkv";
            many.add(p);
        }
        v.previewChoose(many);
        CoverPage c = v.cover();
        v.getWindow().getDecorView().measure(android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY));
        v.getWindow().getDecorView().layout(0, 0, 1920, 1080);
        c.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888)));
        assertEquals(14, c.rowCount());
        assertTrue(c.rowsShown() < 14);
        for (int i = 0; i < 13; i++) press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(13, c.focus());
        assertTrue(c.firstShown() > 0);
        assertTrue(c.focus() < c.firstShown() + c.rowsShown());
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(many.get(13), v.source());
    }

    @Test public void theTitlesLogoOrItsNameInWords() throws Exception {
        VodActivity v = play(FILM, "", 0);
        CoverPage c = v.cover();
        // No logo: the title in words.
        assertFalse(c.showsLogo());
        // TMDB's logo come (a made-up one): drawn in the title's place.
        java.io.File f = new java.io.File(app.getCacheDir(), "made-up-logo.png");
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(600, 200, android.graphics.Bitmap.Config.ARGB_8888);
        b.eraseColor(0x80FFB45A);
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
            b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
        c.logo("file:" + f.getPath());
        assertTrue(c.showsLogo());
        // One that can't be read: the words again.
        c.logo("file:" + new java.io.File(app.getCacheDir(), "none.png").getPath());
        assertFalse(c.showsLogo());
        assertEquals("The Lantern House", c.titleWords());
    }

    @Test public void backGoesBackToTheTitlesPage() {
        VodActivity v = play(FILM, "", 0);
        v.previewChoose(copies());
        press(v, KeyEvent.KEYCODE_BACK);
        assertTrue(v.isFinishing());
    }

    @Test public void withoutChooseItPlaysStraightAway() {
        Intent i = new Intent(app, VodActivity.class).putExtra(VodActivity.TITLE, FILM).putExtra(VodActivity.EPISODE, "")
                .putExtra(VodActivity.START, 0L);
        VodActivity v = Robolectric.buildActivity(VodActivity.class, i).setup().get();
        idle();
        assertTrue(v.cover() == null || !v.cover().isOpen());
        assertTrue(v.overlayOn());
    }

    @Test public void theGroundFollowsTheColourTheme() {
        int red = -1;
        for (int i = 0; i < Theme.PRIMARIES.length; i++) if (Theme.PRIMARIES[i].name.equals("Red")) red = i;
        Theme.save(app, red, Theme.AMBER);
        VodActivity v = play(FILM, "", 0);
        assertEquals(CoverPage.base(Theme.PRIMARIES[red].ground), v.cover().baseColour());
        assertEquals(Theme.PRIMARIES[red].ground | 0xFF000000, v.cover().baseColour());
        v.finish();
        Theme.save(app, Theme.HOMEFIELD, Theme.AMBER);
        VodActivity w = play(FILM, "", 0);
        assertEquals(Theme.PRIMARIES[Theme.HOMEFIELD].ground | 0xFF000000, w.cover().baseColour());
    }

    @Test public void whiteWordsReadOnEveryGroundAndGlow() {
        int[] posters = {0xFFFFFFFF, 0xFFFFE14D, 0xFFE8262C, 0xFF2F6FD8, 0xFF000000, 0xFFFFB45A, 0xFF7FE3FF};
        List<Integer> grounds = new ArrayList<>();
        for (Theme.Primary p : Theme.PRIMARIES) grounds.add(p.ground);
        // A lighter ground than any the setting has now: darkened until the words read.
        grounds.add(0xFFE8E8E8);
        grounds.add(0xFF8090A0);
        for (int g : grounds) {
            int base = CoverPage.base(g);
            assertTrue(Integer.toHexString(g), CoverPage.contrast(0xFFFFFFFF, base) >= 7.0);
            for (int p : posters) {
                int under = CoverPage.underWords(base, CoverPage.toned(p));
                assertTrue(Integer.toHexString(g) + " " + Integer.toHexString(p), CoverPage.contrast(0xFFFFFFFF, under) >= 4.5);
            }
        }
        // The setting's own dark grounds are kept as they are.
        assertEquals(Theme.PRIMARIES[0].ground | 0xFF000000, CoverPage.base(Theme.PRIMARIES[0].ground));
        // A poster's most vivid colour, not its grey.
        int[] px = new int[100];
        Arrays.fill(px, 0xFF404040);
        for (int i = 0; i < 12; i++) px[i] = 0xFFE07020;
        int vivid = CoverPage.vivid(px);
        assertTrue(Integer.toHexString(vivid), ((vivid >> 16) & 0xFF) > ((vivid & 0xFF) + 60));
    }

    @Test public void anEpisodesCover() {
        VodActivity v = play(SERIES, "e2", 0);
        CoverPage c = v.cover();
        assertTrue(c.isOpen());
        assertEquals("S2 E1  ·  Fog", c.titleWords());
        assertEquals("The lamp goes dark in the fog.", c.plotWords());
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(c.isOpen());
        assertEquals("e2", v.episode().id);
    }

    @Test public void thePop() {
        assertEquals(0.85f, CoverPage.popScale(0, false), 0.001f);
        assertTrue(CoverPage.popScale(300, false) > 1.02f);
        assertEquals(1f, CoverPage.popScale(450, false), 0.001f);
        assertEquals(1f, CoverPage.popScale(2000, false), 0.001f);
        assertEquals(0.95f, CoverPage.popScale(0, true), 0.001f);
        assertEquals(1f, CoverPage.popScale(CoverPage.LITE_MS, true), 0.001f);
        // The words one after another, 40 ms apart; on Lite all at once.
        assertEquals(0f, CoverPage.wordsIn(CoverPage.WORDS_FROM + 2 * CoverPage.STAGGER, 2, false), 0.001f);
        assertTrue(CoverPage.wordsIn(CoverPage.WORDS_FROM + 2 * CoverPage.STAGGER, 1, false) > 0f);
        assertEquals(1f, CoverPage.wordsIn(2000, 6, false), 0.001f);
        assertEquals(CoverPage.wordsIn(100, 0, true), CoverPage.wordsIn(100, 6, true), 0.0001f);
        assertFalse(CoverPage.settled(400, 8, false));
        assertTrue(CoverPage.settled(2000, 8, false));
        assertTrue(CoverPage.settled(CoverPage.LITE_MS, 8, true));
    }
}
