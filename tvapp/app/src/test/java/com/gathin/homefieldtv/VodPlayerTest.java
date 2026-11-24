package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.media3.common.MimeTypes;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.OtherPrefs;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.subs.OpenSubtitles;
import com.gathin.homefieldtv.subs.SubsPrefs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The film player with the remote, on a made-up playlist (a film and a series of four episodes, invented names) and no
 * stream (previews' positions): the overlay's keys, seeking on the plain picture with its scrubber, the sources panel
 * carrying on from the same moment, subtitles found on a fake OpenSubtitles and remembered for the title, Up next and its
 * countdown, and the episodes panel. Nothing reaches the real OpenSubtitles; the key is obviously made up.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class VodPlayerTest {
    private static final String FILM = "p0|m|101", SERIES = "p0|s|201";
    private Context app;
    private OpenSubtitles.Http was;
    private Object catalogueWas;
    private final List<String> asked = new ArrayList<>();

    /** The catalogue's one instance (an earlier test's, made with its own application, is put back afterwards). */
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
        rows.add("m\t101\tGlass Harbour (2024) 4K\t1\t\t7.8\t" + now + "\tmkv\t2024\t\t\t");
        rows.add("s\t201\tThe Quiet Coast\t1\t\t8.1\t" + now / 1000 + "\t2022\tA made-up series by the sea.\t");
        Map<String, Catalog.Details> pages = new HashMap<>();
        pages.put(FILM, Catalog.previewDetails("A made-up story of a harbour.", "Drama", "Ada Lune, Bo Marsh", "Cy Reed", "2024-03-01",
                "", "7.8", "15", 125, null));
        String[][] eps = {{"e1", "1", "1", "Arrival", "", "", "45"}, {"e2", "1", "2", "The Lamp", "", "", "44"},
                {"e3", "2", "1", "Fog", "", "", "46"}, {"e4", "2", "2", "Tides", "", "", "47"}};
        pages.put(SERIES, Catalog.previewDetails("A made-up series by the sea.", "Drama", "", "", "2022", "", "8.1", "12", 0, eps));
        Catalog.get(app).useForPreview(rows, pages);
        Resume.clear(app);
        app.getSharedPreferences("vod_subs", Context.MODE_PRIVATE).edit().clear().commit();
        SubsPrefs.forget(app);
        OtherPrefs.setAutoplayNext(app, true);
        OtherPrefs.setSkip(app, 1);
        VodActivity.previewDur = 125 * 60_000L;
        VodActivity.previewPos = 52 * 60_000L + 10_000L;
        VodActivity.previewPlaying = true;
        was = OpenSubtitles.http;
    }

    @After public void tearDown() throws Exception {
        catalogue().set(null, catalogueWas);
        OpenSubtitles.http = was;
        SubsPrefs.forget(app);
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void wait(int ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private static void press(VodActivity a, int code) {
        down(a, code);
        up(a, code);
        idle();
    }

    private static void down(VodActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
    }

    private static void up(VodActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
    }

    private VodActivity play(String key, String episode, long from) {
        Intent i = new Intent(app, VodActivity.class).putExtra(VodActivity.TITLE, key).putExtra(VodActivity.EPISODE, episode)
                .putExtra(VodActivity.START, from);
        VodActivity v = Robolectric.buildActivity(VodActivity.class, i).setup().get();
        idle();
        return v;
    }

    private static RowList.Row row(VodActivity v, String tag) {
        for (RowList.Row r : v.panels().card().list().rows()) if (tag.equals(r.tag)) return r;
        throw new AssertionError("no row " + tag);
    }

    private static int itemOf(int[] items, int want) {
        for (int k = 0; k < items.length; k++) if (items[k] == want) return k;
        return -1;
    }

    // ---- the overlay ----------------------------------------------------------------------------------------------------

    @Test public void theOverlaysKeys() {
        VodActivity v = play(FILM, "", 0);
        assertTrue(v.overlayOn());
        press(v, KeyEvent.KEYCODE_BACK);
        assertFalse(v.overlayOn());
        assertFalse(v.isFinishing());
        // OK brings it up with the remote on play; OK again pauses.
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(v.overlayOn());
        assertEquals(VodActivity.ROW_TRANSPORT, v.focusRow());
        assertEquals(VodActivity.T_PLAY, v.transport()[v.focusItem()]);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(VodActivity.previewPlaying);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(VodActivity.previewPlaying);
        // ◀◀ goes back a step at once.
        long pos = VodActivity.previewPos;
        press(v, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(VodActivity.T_BACK, v.transport()[v.focusItem()]);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(pos - 10_000, VodActivity.previewPos);
        // Down to the buttons (a film: no Episodes), along them.
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(VodActivity.ROW_BUTTONS, v.focusRow());
        assertEquals(-1, itemOf(v.buttons(), VodActivity.B_EPISODES));
        press(v, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(VodActivity.ROW_TRANSPORT, v.focusRow());
        // Up to the seek line: Right moves the spot, which waits for OK.
        press(v, KeyEvent.KEYCODE_DPAD_UP);
        assertEquals(VodActivity.ROW_SEEK, v.focusRow());
        pos = VodActivity.previewPos;
        press(v, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(v, KeyEvent.KEYCODE_DPAD_RIGHT);
        wait(2000);
        assertEquals(pos + 20_000, v.seekTarget());
        assertEquals(pos, VodActivity.previewPos);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(pos + 20_000, VodActivity.previewPos);
        assertEquals(-1, v.seekTarget());
        // Back leaves a spot not jumped to, then closes the overlay, then stops.
        press(v, KeyEvent.KEYCODE_DPAD_LEFT);
        press(v, KeyEvent.KEYCODE_BACK);
        assertEquals(-1, v.seekTarget());
        assertTrue(v.overlayOn());
        press(v, KeyEvent.KEYCODE_BACK);
        assertFalse(v.overlayOn());
        press(v, KeyEvent.KEYCODE_BACK);
        assertTrue(v.isFinishing());
    }

    @Test public void leftAndRightOnThePlainPictureSeekAtOnce() {
        VodActivity v = play(FILM, "", 0);
        press(v, KeyEvent.KEYCODE_BACK);
        long pos = VodActivity.previewPos;
        down(v, KeyEvent.KEYCODE_DPAD_LEFT);
        // The scrubber: the spot moved, the overlay not up.
        assertEquals(pos - 10_000, v.seekTarget());
        assertFalse(v.overlayOn());
        up(v, KeyEvent.KEYCODE_DPAD_LEFT);
        wait(600);
        assertEquals(pos - 10_000, VodActivity.previewPos);
        assertEquals(-1, v.seekTarget());
        // Settings › VOD › Skip steps.
        OtherPrefs.setSkip(app, 4);
        pos = VodActivity.previewPos;
        press(v, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(pos + 30_000, v.seekTarget());
        wait(600);
        // Fast forward and play/pause, the media keys.
        pos = VodActivity.previewPos;
        press(v, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        assertEquals(pos + 30_000, v.seekTarget());
        wait(600);
        assertEquals(pos + 30_000, VodActivity.previewPos);
        press(v, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        assertFalse(VodActivity.previewPlaying);
        assertTrue(v.overlayOn());
        // Held, further and further.
        assertEquals(10_000, VodActivity.step(10_000, 1));
        assertEquals(30_000, VodActivity.step(10_000, 6));
        assertEquals(60_000, VodActivity.step(10_000, 16));
        assertEquals(300_000, VodActivity.step(10_000, 30));
        assertEquals(60_000, VodActivity.step(60_000, 6));
    }

    // ---- sources --------------------------------------------------------------------------------------------------------

    @Test public void anotherSourceCarriesOnFromTheSameMoment() {
        VodActivity v = play(FILM, "", 0);
        Catalog.Play torbox = Catalog.previewPlay("torbox:1:2", "");
        torbox.picture = Catalog.PICTURE_4K;
        torbox.hdr = "Dolby Vision";
        torbox.sound = "Dolby Atmos";
        torbox.name = "Glass.Harbour.2024.2160p.DV.Atmos.mkv";
        torbox.size = 14L << 30;
        Catalog.Play home = Catalog.previewPlay("http://a.example/1.mkv", "Home");
        home.picture = Catalog.PICTURE_1080;
        home.sound = "5.1";
        Catalog.Play spare = Catalog.previewPlay("http://b.example/1.mp4", "Spare");
        spare.picture = Catalog.PICTURE_720;
        List<Catalog.Play> copies = Arrays.asList(torbox, home, spare);
        long pos = VodActivity.previewPos;
        v.previewSources(copies, 0, false);
        assertEquals("1 of 3 sources", v.copiesWords());
        assertEquals(Arrays.asList("4K", "DOLBY VISION", "DOLBY ATMOS"), v.chips());
        // From the overlay's Sources, the first of its buttons.
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        for (int i = 0; i < v.buttons().length; i++) press(v, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(VodActivity.B_SOURCES, v.buttons()[v.focusItem()]);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(v.overlayOn());
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(home, v.source());
        assertEquals(pos, v.startAtForTests());
        assertEquals("2 of 3 sources", v.copiesWords());
        // Up on the plain picture lists them too; Back goes back to the overlay.
        press(v, KeyEvent.KEYCODE_BACK);
        press(v, KeyEvent.KEYCODE_DPAD_UP);
        press(v, KeyEvent.KEYCODE_BACK);
        assertTrue(v.overlayOn());
        assertEquals(home, v.source());
        assertEquals("That copy stopped  ·  carrying on from Spare  ·  720p", VodActivity.sourceStopped(spare));
    }

    // ---- subtitles ------------------------------------------------------------------------------------------------------

    @Test public void subtitlesFromOpenSubtitlesAreRememberedForTheTitle() {
        SubsPrefs.setKey(app, "made-up-key-for-tests");
        String results = "{\"data\":[{\"attributes\":{\"language\":\"en\",\"release\":\"Glass.Harbour.2024.2160p\",\"download_count\":4200,"
                + "\"files\":[{\"file_id\":501,\"file_name\":\"gh.en.srt\"}]}},{\"attributes\":{\"language\":\"en\",\"release\":"
                + "\"Glass.Harbour.2024.SDH\",\"hearing_impaired\":true,\"download_count\":80,\"files\":[{\"file_id\":502}]}}]}";
        OpenSubtitles.http = (m, u, h, b) -> {
            asked.add(m + " " + u);
            if (u.contains("/subtitles?")) return new OpenSubtitles.Reply(200, results.getBytes(StandardCharsets.UTF_8));
            if (u.endsWith("/download")) return new OpenSubtitles.Reply(200, "{\"link\":\"https://files.example/501\"}".getBytes(StandardCharsets.UTF_8));
            return new OpenSubtitles.Reply(200, "1\n00:52:00,000 --> 00:52:30,000\n<i>The tide is turning.</i>\n".getBytes(StandardCharsets.UTF_8));
        };
        VodActivity v = play(FILM, "", 0);
        press(v, KeyEvent.KEYCODE_CAPTIONS);
        assertEquals(VodPanels.SUBS, v.panels().kind());
        assertTrue(row(v, "off").checked);
        v.panels().picked(v.panels().card(), row(v, "find"));
        idle();
        assertEquals(VodPanels.FOUND, v.panels().kind());
        assertEquals(VodSubs.ANSWERED, v.subs().searchState());
        assertEquals(2, v.subs().results().size());
        assertTrue(asked.get(0), asked.get(0).contains("query=glass+harbour") && asked.get(0).contains("year=2024"));
        assertNotNull(row(v, "r:0"));
        v.panels().picked(v.panels().card(), row(v, "r:0"));
        idle();
        assertEquals(SubsPrefs.Choice.FOUND, v.subs().kind());
        assertEquals("English", v.subs().words());
        assertTrue(((ChannelSubs) v.subs().view()).showing());
        // Half a second later, kept for the title.
        v.panels().show(VodPanels.SUBS);
        RowList.Row offset = row(v, "offset");
        offset.side = 1;
        v.panels().picked(v.panels().card(), offset);
        assertEquals(500, v.subs().offset());
        assertEquals(SubsPrefs.Choice.FOUND, SubsPrefs.choice(app, FILM).kind);
        assertEquals(501, SubsPrefs.choice(app, FILM).fileId);
        v.finish();
        // Next time the film plays: the same file, its timing, without asking OpenSubtitles again.
        asked.clear();
        VodActivity again = play(FILM, "", 0);
        idle();
        assertEquals(SubsPrefs.Choice.FOUND, again.subs().kind());
        assertEquals(500, again.subs().offset());
        assertTrue(((ChannelSubs) again.subs().view()).showing());
        assertTrue(asked.isEmpty());
        // Off is remembered too.
        press(again, KeyEvent.KEYCODE_CAPTIONS);
        again.panels().picked(again.panels().card(), row(again, "off"));
        assertEquals(SubsPrefs.Choice.OFF, SubsPrefs.choice(app, FILM).kind);
        assertFalse(again.panels().open());
    }

    @Test public void withoutAKeyTheSearchSaysWhereToAddIt() {
        VodActivity v = play(FILM, "", 0);
        press(v, KeyEvent.KEYCODE_CAPTIONS);
        RowList.Row find = row(v, "find");
        assertFalse(find.enabled);
        assertEquals(OpenSubtitles.NEEDS_KEY, find.value);
        // Back goes back to the overlay.
        press(v, KeyEvent.KEYCODE_BACK);
        assertFalse(v.panels().open());
        assertTrue(v.overlayOn());
    }

    @Test public void theStreamsOwnTracksInWords() {
        assertEquals("English  ·  SDH", VodSubs.words("eng", "", false, true));
        assertEquals("French  ·  Forced", VodSubs.words("fre", "", true, false));
        assertEquals("English  ·  Director's commentary", VodSubs.words("en", "Director's commentary", false, false));
        assertEquals("Subtitles", VodSubs.words("und", "", false, false));
        List<VodSubs.Own> own = Arrays.asList(new VodSubs.Own(null, 0, "eng", "Forced", true, false),
                new VodSubs.Own(null, 1, "eng", "", false, false), new VodSubs.Own(null, 2, "eng", "SDH", false, true),
                new VodSubs.Own(null, 3, "fre", "", false, false));
        assertEquals(2, VodSubs.match(own, "en", "SDH"));
        assertEquals(1, VodSubs.match(own, "en", "gone"));
        assertEquals(3, VodSubs.match(own, "fr", ""));
        assertEquals(-1, VodSubs.match(own, "de", ""));
        assertEquals(0, VodSubs.forced(own, Arrays.asList("en", "fr")));
        assertEquals(-1, VodSubs.forced(own, Arrays.asList("fr")));
    }

    // ---- audio, speed, picture, info ------------------------------------------------------------------------------------

    @Test public void audioSpeedPictureAndInfo() {
        VodActivity v = play(FILM, "", 0);
        idle();
        press(v, KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK);
        assertEquals(VodPanels.AUDIO, v.panels().kind());
        int before = PlayerPrefs.delayMs(app);
        RowList.Row delay = row(v, "delay");
        delay.side = 1;
        v.panels().picked(v.panels().card(), delay);
        assertEquals(before + PlayerPrefs.DELAY_STEP, PlayerPrefs.delayMs(app));
        PlayerPrefs.setDelayMs(app, 0);
        v.panels().close(true);
        v.panels().show(VodPanels.SPEED);
        v.panels().picked(v.panels().card(), row(v, "speed:3"));
        assertEquals(1.5f, v.speed(), 0.001f);
        assertFalse(v.panels().open());
        v.panels().show(VodPanels.PICTURE);
        v.panels().picked(v.panels().card(), row(v, "aspect:" + PlayerPrefs.ZOOM));
        assertEquals(PlayerPrefs.ZOOM, PlayerPrefs.vodAspect(app));
        PlayerPrefs.setVodAspect(app, PlayerPrefs.FIT);
        press(v, KeyEvent.KEYCODE_INFO);
        assertEquals(VodPanels.INFO, v.panels().kind());
        boolean story = false, cast = false;
        for (RowList.Row r : v.panels().card().list().rows()) {
            story |= r.title.contains("harbour");
            cast |= r.title.contains("Ada Lune");
        }
        assertTrue(story && cast);
        assertEquals("2024  ·  2h 5m  ·  ★ 7.8  ·  15  ·  Drama", VodPanels.facts(v.title(), v.details()));
        assertEquals("Dolby Digital+", VodPanels.codec(MimeTypes.AUDIO_E_AC3));
        assertEquals("Dolby Atmos", VodPanels.codec(MimeTypes.AUDIO_E_AC3_JOC));
        assertEquals("", VodPanels.codec("audio/x-unknown"));
        assertEquals("Fit", VodPanels.aspectName(PlayerPrefs.FIT));
        assertEquals("Fill", VodPanels.aspectName(PlayerPrefs.FILL));
        assertEquals("16:9", VodPanels.aspectName(PlayerPrefs.WIDE));
        assertEquals("English 5.1", VodActivity.shortAudio("English  ·  5.1  ·  Dolby Digital+"));
        assertEquals("1:02:05", VodOverlay.clock(3_725_000));
        assertEquals("1:05", VodOverlay.clock(65_000));
        assertEquals("−0:30", VodOverlay.by(-30_000));
        assertEquals("+2:30", VodOverlay.by(150_000));
    }

    // ---- series ---------------------------------------------------------------------------------------------------------

    @Test public void upNextNearTheEndOkPlaysItBackCancels() {
        VodActivity.previewDur = 45 * 60_000L;
        VodActivity.previewPos = 45 * 60_000L - 30_000L;
        VodActivity v = play(SERIES, "e1", 0);
        assertEquals("e1", v.episode().id);
        assertEquals(VodActivity.T_NEXT, v.transport()[v.transport().length - 1]);
        press(v, KeyEvent.KEYCODE_BACK);
        wait(600);
        assertEquals(VodActivity.NEXT_COUNTING, v.upNextState());
        assertTrue(v.upNextShowing());
        assertEquals("e2", v.upNextEpisode().id);
        int left = v.upNextSeconds();
        assertTrue(String.valueOf(left), left >= 1 && left <= 10);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("e2", v.episode().id);
        assertEquals(0, v.startAtForTests());
        // Continue watching carries on with the next one.
        assertEquals("e2", Resume.latest(app, SERIES).episode);
        // Back puts the card away; it plays on to the end.
        press(v, KeyEvent.KEYCODE_BACK);
        VodActivity.previewPos = 45 * 60_000L - 20_000L;
        wait(600);
        assertEquals(VodActivity.NEXT_COUNTING, v.upNextState());
        press(v, KeyEvent.KEYCODE_BACK);
        assertEquals(VodActivity.NEXT_CANCELLED, v.upNextState());
        assertFalse(v.upNextShowing());
        assertFalse(v.isFinishing());
        assertEquals("e2", v.episode().id);
    }

    @Test public void theCountdownRunsOutAndTheNextPlays() {
        VodActivity.previewDur = 45 * 60_000L;
        VodActivity.previewPos = 45 * 60_000L - 40_000L;
        VodActivity v = play(SERIES, "e2", 0);
        press(v, KeyEvent.KEYCODE_BACK);
        wait(600);
        assertEquals(VodActivity.NEXT_COUNTING, v.upNextState());
        wait(11_000);
        assertEquals("e3", v.episode().id);
        // Without Autoplay next episode: the card waits for OK, no countdown.
        OtherPrefs.setAutoplayNext(app, false);
        press(v, KeyEvent.KEYCODE_BACK);
        VodActivity.previewPos = 45 * 60_000L - 40_000L;
        wait(600);
        assertEquals(VodActivity.NEXT_WAITING, v.upNextState());
        assertEquals(-1, v.upNextSeconds());
        wait(11_000);
        assertEquals("e3", v.episode().id);
        assertEquals(25_000, VodActivity.credits(10 * 60_000L));
        assertEquals(60_000, VodActivity.credits(60 * 60_000L));
    }

    @Test public void theEpisodesPanel() {
        Resume.save(app, new Resume.Point(SERIES, "e2", "The Quiet Coast", "S1 E2", "", 10 * 60_000L, 44 * 60_000L,
                System.currentTimeMillis()));
        VodActivity.previewPos = 60_000L;
        VodActivity v = play(SERIES, "e1", 0);
        // MENU: the overlay's buttons; along to Episodes.
        press(v, KeyEvent.KEYCODE_MENU);
        assertEquals(VodActivity.ROW_BUTTONS, v.focusRow());
        int at = itemOf(v.buttons(), VodActivity.B_EPISODES);
        for (int i = 0; i < at; i++) press(v, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        EpisodesPanel p = v.episodesPanel();
        assertTrue(p.isOpen());
        assertEquals(0, p.season());
        assertEquals(0, p.focus());
        Catalog.Details d = v.details();
        // Those before the one watched last are watched; that one part-way.
        assertEquals(1f, v.progress(d.episode("e1")), 0.001f);
        assertEquals(10f / 44f, v.progress(d.episode("e2")), 0.01f);
        assertEquals(-1f, v.progress(d.episode("e3")), 0.001f);
        // Season 2, its second: Tides plays from its start.
        press(v, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, p.season());
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertFalse(p.isOpen());
        assertEquals("e4", v.episode().id);
        assertEquals(0, v.startAtForTests());
        // Back to season 1's second: from where it was left.
        v.previewEpisodes();
        assertEquals(1, p.season());
        press(v, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(0, p.season());
        press(v, KeyEvent.KEYCODE_DPAD_DOWN);
        press(v, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("e2", v.episode().id);
        assertEquals(10 * 60_000L, v.startAtForTests());
        // Up from the first row goes to the seasons; Back closes it, back to the overlay.
        v.previewEpisodes();
        press(v, KeyEvent.KEYCODE_DPAD_UP);
        press(v, KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(p.onPills());
        press(v, KeyEvent.KEYCODE_BACK);
        assertFalse(p.isOpen());
        assertTrue(v.overlayOn());
        assertEquals("1  Fog", EpisodesPanel.rowWords(d.episode("e3"))[0]);
        assertEquals("46m", EpisodesPanel.rowWords(d.episode("e3"))[1]);
    }
}
