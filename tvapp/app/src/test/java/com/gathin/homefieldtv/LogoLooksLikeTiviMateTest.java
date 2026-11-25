package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.gathin.broadcast.BrandStyles;
import com.gathin.broadcast.CoverLooks;
import com.gathin.broadcast.LogoExtras;
import com.gathin.broadcast.SportsMark;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpChannels;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;

import org.junit.After;
import org.junit.Before;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The logo's choices in Homefield TV as the TV launcher has them over TiviMate (the family: "I also want the option to pick
 * what logo I want to use like tivimate ... Make sure it has all the logos that are already in the launcher"): every look
 * the launcher's Scoreboard card offers (CoverLooks.Look, its 24, drawn by the same code), the channel's own logo first as
 * Homefield TV's came, this channel's own, the 54 styles, the Sports wordmark's words and a general channel, the picture
 * tag, the background, the competition's logo, LIVE, and Event's four extras; each kept, picked from the hold-OK menu,
 * and drawn over the picture.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class LogoLooksLikeTiviMateTest {
    private ActivityController<TvActivity> tv;
    private Context app;
    private List<Channel> ch;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        AlertSwitches.reset();
        AlertSwitches.launcher = (c, q) -> null;
        LogoHere.launcher = (c, q) -> null;
        ch = MadeUpChannels.load(app);
        List<String> recent = new ArrayList<>();
        for (Channel c : ch) recent.add(c.key);
        Library.get(app).setPicks(new ArrayList<>(), recent);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        Library.get(app).setPicks(new ArrayList<>(), new ArrayList<>());
        MadeUpChannels.unload(app);
        clear();
        tv = null;
        Exec.inline = false;
    }

    private void clear() {
        for (String p : new String[]{"scoreboard", "remote", "in_step", "sync_store", "logo_extras"}) {
            app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private static void hold(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 100, KeyEvent.ACTION_DOWN, code, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 120, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private BoardLayer overAMatch(int i) {
        TvActivity a = tv.get();
        a.watch(ch.get(i));
        idle();
        LiveScreen live = (LiveScreen) a.top();
        press(a, KeyEvent.KEYCODE_BACK);
        View v = ((ViewGroup) live.view()).getChildAt(0);
        BoardLayer b = (BoardLayer) v;
        InStep.setDelay(app, ch.get(i).key, 0);
        long now = System.currentTimeMillis();
        b.found(BoardLikeTiviMateTest.match("logo-1"),
                Collections.singletonList(new BoardClock.Reading(now - 3_000L, 1, 0, Game.LIVE, false, 67 * 60, "68'")));
        idle();
        assertTrue(b.coverUp());
        return b;
    }

    private static void downTo(TvActivity a, BoardLayer b, int line) {
        for (int i = 0; i < BoardMenu.LINES && b.menu.open && b.menu.at < line; i++) press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertEquals(line, b.menu.at);
    }

    @Test public void everyLauncherLookIsAChoiceInItsOrderAndKept() {
        // The channel's own logo first (as Homefield TV's came, so what the family had stays), then the launcher's 24.
        String[] all = BoardPrefs.logoLooks();
        assertEquals(1 + CoverLooks.Look.values().length, all.length);
        assertEquals(25, all.length);
        assertEquals(BoardPrefs.CHANNEL_LOGO, all[0]);
        for (int i = 0; i < CoverLooks.Look.values().length; i++) assertEquals(CoverLooks.Look.values()[i].name(), all[i + 1]);
        assertEquals("The channel's own logo", BoardPrefs.logoLookWords(BoardPrefs.CHANNEL_LOGO));
        assertEquals("Broadcast, scoreboard colours", BoardPrefs.logoLookWords("BROADCAST_BOARD"));
        assertEquals("Whitebrook Road styles", BoardPrefs.logoLookWords("STYLES"));
        assertEquals("Stadium LED", BoardPrefs.logoLookWords("LED"));
        // Round the list both ways.
        assertEquals("SIGNATURE", BoardPrefs.nextLogoLook(BoardPrefs.CHANNEL_LOGO, 1));
        assertEquals("LED", BoardPrefs.nextLogoLook(BoardPrefs.CHANNEL_LOGO, -1));
        assertEquals(BoardPrefs.CHANNEL_LOGO, BoardPrefs.nextLogoLook("LED", 1));
        assertEquals(BoardPrefs.CHANNEL_LOGO, BoardPrefs.logoLookOf("NOT_A_LOOK"));
        // Kept, as chosen.
        assertEquals(BoardPrefs.CHANNEL_LOGO, BoardPrefs.logoLook(app));
        for (String look : all) {
            BoardPrefs.setLogoLook(app, look);
            assertEquals(look, BoardPrefs.logoLook(app));
        }
        // The picture tags: Auto, none, HD, UHD, 4K, UHD HDR, 4K HDR (the launcher's seven).
        assertEquals(7, CoverLooks.TAGS.length);
        assertNull(BoardPrefs.logoTag(app));
        for (String t : CoverLooks.TAGS) {
            BoardPrefs.setLogoTag(app, t);
            assertEquals(t, BoardPrefs.logoTag(app));
        }
        assertEquals("Auto, from the channel (UHD HDR)", CoverLooks.tagWords(null, "Sky Sports Main Event UHD HDR"));
        assertEquals("4K HDR", CoverLooks.tagShown("4K HDR", "Any HD"));
        assertEquals("HD", CoverLooks.tagShown(null, "Northfield TV HD"));
        // The 54 styles, round.
        assertEquals(0, BoardPrefs.logoStyle(app));
        BoardPrefs.setLogoStyle(app, 53);
        assertEquals(53, BoardPrefs.logoStyle(app));
        assertEquals(0, BoardPrefs.nextStyle(53, 1));
        assertEquals(53, BoardPrefs.nextStyle(0, -1));
        // The wordmark's words: Auto first, then the launcher's.
        assertEquals("", BoardPrefs.logoWhat(app));
        assertEquals(SportsMark.WHATS[1], BoardPrefs.nextWhat("", 1));
        BoardPrefs.setLogoWhat(app, "f1");
        assertEquals("f1", BoardPrefs.logoWhat(app));
        assertEquals("f1", BoardLayer.whatOn("f1", null));
        assertEquals(SportsMark.auto("eng.1", false), BoardLayer.whatOn("", gameIn("eng.1")));
        // A general channel or a sports one; the background's five; the competition's logo and LIVE.
        assertFalse(BoardPrefs.logoNameOnly(app));
        BoardPrefs.setLogoNameOnly(app, true);
        assertTrue(BoardPrefs.logoNameOnly(app));
        assertEquals(CoverLooks.PLATE, BoardPrefs.logoGround(app));
        int g = CoverLooks.PLATE;
        Set<Integer> grounds = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            grounds.add(g);
            g = BoardPrefs.nextGround(g, 1);
        }
        assertEquals(5, grounds.size());
        assertEquals(CoverLooks.PLATE, g);
        BoardPrefs.setLogoGround(app, CoverLooks.TEMPERED);
        assertEquals(CoverLooks.TEMPERED, BoardPrefs.logoGround(app));
        assertEquals("Tempered glass", CoverLooks.groundWords(CoverLooks.TEMPERED));
        assertTrue(BoardPrefs.logoComp(app));
        assertTrue(BoardPrefs.logoLive(app));
        // This channel's own: as the rest, leave the channel's own, then every look; kept a channel at a time.
        assertNull(BoardPrefs.channelLogo(app, "ch-a"));
        assertEquals(BoardPrefs.LEAVE, BoardPrefs.nextChannelLogo(null, 1));
        assertEquals(BoardPrefs.CHANNEL_LOGO, BoardPrefs.nextChannelLogo(BoardPrefs.LEAVE, 1));
        assertNull(BoardPrefs.nextChannelLogo("LED", 1));
        BoardPrefs.setChannelLogo(app, "ch-a", "EVENT_NAVY");
        assertEquals("EVENT_NAVY", BoardPrefs.channelLogo(app, "ch-a"));
        assertEquals("EVENT_NAVY", BoardPrefs.logoLookHere(app, "ch-a"));
        assertEquals(BoardPrefs.logoLook(app), BoardPrefs.logoLookHere(app, "ch-b"));
        assertEquals("As the rest (Plain)", BoardPrefs.channelLogoWords(null, "PLAIN"));
        assertEquals("Leave the channel's own", BoardPrefs.channelLogoWords(BoardPrefs.LEAVE, "PLAIN"));
        // Event's four extras, on unless off (LogoExtras' own switches, the launcher's).
        for (String which : LogoExtras.ALL) {
            assertTrue(LogoExtras.on(app, which));
            LogoExtras.set(app, which, false);
            assertFalse(LogoExtras.on(app, which));
        }
    }

    private static Game gameIn(String slug) {
        Game g = BoardLikeTiviMateTest.match("x");
        g.slug = slug;
        return g;
    }

    @Test public void theHoldOkMenuPicksEachOfThemOverTheMatch() {
        TvActivity a = tv.get();
        BoardLayer b = overAMatch(3);
        String key = ch.get(3).key;
        hold(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(b.menuOpen());
        // Logo's look: Right through every one of the launcher's, round to the channel's own again; each kept.
        downTo(a, b, BoardMenu.M_LOGO_LOOK);
        assertTrue(BoardMenu.menuLines(b.menuState())[BoardMenu.M_LOGO_LOOK].contains("The channel's own logo"));
        for (CoverLooks.Look l : CoverLooks.Look.values()) {
            press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
            assertEquals(l.name(), BoardPrefs.logoLook(app));
            assertTrue(BoardMenu.menuLines(b.menuState())[BoardMenu.M_LOGO_LOOK].contains(l.label));
            assertTrue("the cover up in it over the match", b.coverUp());
        }
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(BoardPrefs.CHANNEL_LOGO, BoardPrefs.logoLook(app));
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals("LED", BoardPrefs.logoLook(app));
        // This channel's logo: Leave the channel's own: no cover on this channel (the menu still up).
        downTo(a, b, BoardMenu.M_LOGO_HERE);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(BoardPrefs.LEAVE, BoardPrefs.channelLogo(app, key));
        idle();
        assertFalse(b.coverUp());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(BoardPrefs.CHANNEL_LOGO, BoardPrefs.channelLogo(app, key));
        idle();
        assertTrue(b.coverUp());
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertNull(BoardPrefs.channelLogo(app, key));
        // The style, the wordmark's words, a general channel, the tag, the background, the competition's logo, LIVE.
        downTo(a, b, BoardMenu.M_LOGO_STYLE);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, BoardPrefs.logoStyle(app));
        assertTrue(BoardMenu.menuLines(b.menuState())[BoardMenu.M_LOGO_STYLE].contains("2 of 54  ·  " + BrandStyles.name(1)));
        downTo(a, b, BoardMenu.M_LOGO_WHAT);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(SportsMark.WHATS[1], BoardPrefs.logoWhat(app));
        downTo(a, b, BoardMenu.M_LOGO_CHANNEL);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertTrue(BoardPrefs.logoNameOnly(app));
        downTo(a, b, BoardMenu.M_LOGO_TAG);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals("", BoardPrefs.logoTag(app));
        downTo(a, b, BoardMenu.M_LOGO_GROUND);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(CoverLooks.FROST, BoardPrefs.logoGround(app));
        downTo(a, b, BoardMenu.M_LOGO_COMP);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.logoComp(app));
        downTo(a, b, BoardMenu.M_LOGO_LIVE);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertFalse(BoardPrefs.logoLive(app));
        // Event's extras, each switched.
        for (int line = BoardMenu.M_LOGO_NEXT; line <= BoardMenu.M_LOGO_MARKS; line++) {
            downTo(a, b, line);
            press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
            assertFalse(LogoExtras.on(app, BoardMenu.extraOf(line)));
        }
        // Kept after the menu closes and the channel comes on again.
        press(a, KeyEvent.KEYCODE_BACK);
        assertFalse(b.menuOpen());
        assertEquals("LED", BoardPrefs.logoLook(app));
        assertEquals(1, BoardPrefs.logoStyle(app));
    }

    /** A cover of look l over a stand-in for the picture, w x h: the pixels it changed. */
    private static int drawn(CoverLooks art, CoverLooks.Look l, CoverLooks.Parts p, int w, int h, Bitmap out) {
        out.eraseColor(0xFF2F7A35);
        art.draw(new Canvas(out), l, w, h, p);
        int changed = 0;
        for (int y = 0; y < h; y += 2) for (int x = 0; x < w; x += 2) if (out.getPixel(x, y) != 0xFF2F7A35) changed++;
        return changed;
    }

    @Test public void everyLookIsDrawnAndEachUnlikeTheOthers() {
        CoverLooks art = new CoverLooks(app, BoardPrefs.outfit(app, 700), BoardPrefs.outfit(app, 800));
        CoverLooks.Parts p = new CoverLooks.Parts();
        p.tag = "UHD";
        p.live = true;
        p.sub = "main event";
        p.inset = 2;
        p.board = new CoverLooks.Board(0xF70B1033, 0xFF2443B4, 0xFFE6E9F2, false);
        int w = 320, h = 128;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Set<Integer> looks = new HashSet<>();
        for (CoverLooks.Look l : CoverLooks.Look.values()) {
            int[] grounds = l.own() ? CoverLooks.GROUNDS : new int[]{CoverLooks.PLATE};
            for (int g : grounds) {
                p.ground = g;
                int n = drawn(art, l, p, w, h, out);
                assertTrue(l + " on ground " + g + " drew over the picture", n > (w / 2) * (h / 2) / 10);
            }
            p.ground = CoverLooks.PLATE;
            drawn(art, l, p, w, h, out);
            looks.add(java.util.Arrays.hashCode(pixels(out)));
        }
        assertEquals("every one of the 24 drawn its own way", CoverLooks.Look.values().length, looks.size());
        // Each of the 54 styles, each its own.
        Set<Integer> styles = new HashSet<>();
        for (int s = 0; s < BrandStyles.COUNT; s++) {
            p.style = s;
            drawn(art, CoverLooks.Look.STYLES, p, w, h, out);
            styles.add(java.util.Arrays.hashCode(pixels(out)));
        }
        assertEquals(BrandStyles.COUNT, styles.size());
        // The picture tag shows on Broadcast: drawn with one and with none, unlike.
        p.tag = "4K HDR";
        drawn(art, CoverLooks.Look.BROADCAST, p, w, h, out);
        int with = java.util.Arrays.hashCode(pixels(out));
        p.tag = "";
        drawn(art, CoverLooks.Look.BROADCAST, p, w, h, out);
        assertNotEquals(with, java.util.Arrays.hashCode(pixels(out)));
    }

    private static int[] pixels(Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        return px;
    }
}
