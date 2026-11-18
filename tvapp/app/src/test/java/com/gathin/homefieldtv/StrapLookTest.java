package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.broadcast.CompBug;
import com.gathin.homefieldtv.sports.Game;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pop-up in my scoreboard's look over Homefield TV's board: every look the board has its strap on the page
 * (overlays/src/strap/strap.js), By competition's the look the board draws for the game; the one switch to it, made once
 * on the TV's own settings; the style's steps; the strap carrying the board as it is drawn, with invented clubs, and its
 * clock starting from the board's.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StrapLookTest {
    private static final Pattern LOOK = Pattern.compile("^\\s*S\\.([a-z0-9_]+)\\s*=", Pattern.MULTILINE);

    /** The looks the strap's page draws: its S.<name> keys, in capitals (the overlays' StrapLooksTest reads them so too). */
    private static Set<String> pageLooks() throws IOException {
        File d = new File("").getAbsoluteFile(), js = null;
        for (int i = 0; i < 6 && d != null && js == null; i++, d = d.getParentFile()) {
            File f = new File(d, "overlays/src/strap/strap.js");
            if (f.exists()) js = f;
        }
        assertTrue("no overlays/src/strap/strap.js above " + new File("").getAbsolutePath(), js != null);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = LOOK.matcher(new String(Files.readAllBytes(js.toPath()), StandardCharsets.UTF_8));
        while (m.find()) out.add(m.group(1).toUpperCase(Locale.ROOT));
        return out;
    }

    /** The look the board draws for a style and the game's competition look, by its style's name (Classic for none). */
    private static String drawn(BoardBug.Style style, CompBug.Kind gameKind) {
        CompBug.Kind k = BoardBug.kindOf(style, gameKind);
        if (k == null) return style == BoardBug.Style.COMPETITION ? BoardBug.Style.CLASSIC.name() : style.name();
        for (BoardBug.Style s : BoardBug.Style.values()) if (s.kind == k) return s.name();
        throw new AssertionError("no look of the board draws " + k);
    }

    @Test public void everyLookOfTheBoardHasItsStrap() throws IOException {
        Set<String> page = pageLooks();
        assertEquals(35, page.size());
        for (BoardBug.Style s : BoardBug.Style.values()) {
            String look = BoardLayer.strapLook(s, "eng.1", "English Premier League");
            assertTrue(s + ": strap.js has no S." + look.toLowerCase(Locale.ROOT), page.contains(look));
            if (s != BoardBug.Style.COMPETITION) assertEquals(s.name(), look);
        }
        // the looks offered here (all but the 3D kits, kept as By competition)
        for (BoardBug.Style s : BoardPrefs.looks()) assertTrue(s.name(), page.contains(BoardLayer.strapLook(s, null, null)));
    }

    @Test public void byCompetitionIsTheLookTheBoardDrawsForTheGame() {
        String[][] games = {{"eng.1", "English Premier League"}, {"uefa.champions", "UEFA Champions League"}, {"esp.1", "LaLiga"},
                {"ita.1", "Italian Serie A"}, {"ger.1", "German Bundesliga"}, {"usa.1", "MLS"}, {"mex.1", "Mexican Liga BBVA MX"},
                {"eng.fa", "FA Cup"}, {"sco.1", "Scottish Premiership"}, {"fifa.friendly", "International Friendly"}, {null, null}};
        for (String[] g : games) {
            CompBug.Kind k = CompBug.forGame(g[0], g[1]);
            assertEquals(g[1], drawn(BoardBug.Style.COMPETITION, k), BoardLayer.strapLook(BoardBug.Style.COMPETITION, g[0], g[1]));
        }
        assertEquals("UCL", BoardLayer.strapLook(BoardBug.Style.COMPETITION, "uefa.champions", "UEFA Champions League"));
        assertEquals("CLASSIC", BoardLayer.strapLook(BoardBug.Style.COMPETITION, "fifa.friendly", "International Friendly"));
        // a look picked by hand is its own, whatever the game
        assertEquals("RETRO_2008", BoardLayer.strapLook(BoardBug.Style.RETRO_2008, "esp.1", "LaLiga"));
        assertEquals("LED", BoardLayer.strapLook(BoardBug.Style.LED, "uefa.champions", "UEFA Champions League"));
        // the competition's name as the board is given it: its name, else its tag; the strap takes the same
        Game g = new Game();
        g.slug = "esp.1";
        g.league = "LALIGA";
        g.leagueName = "LaLiga";
        assertEquals("LaLiga", BoardLayer.boardComp(g));
        g.leagueName = "";
        assertEquals("LALIGA", BoardLayer.boardComp(g));
        g.league = null;
        g.leagueName = null;
        assertEquals("", BoardLayer.boardComp(g));
    }

    @Test public void thePopUpFollowsTheBoardOnceAndStepsThroughTheThree() {
        // the one switch: a pop-up that was on (the classic, the only one here before) to the board's look; off stays off
        assertEquals("strap", BoardPrefs.followBoard(null, true));
        assertEquals("off", BoardPrefs.followBoard(null, false));
        assertEquals("strap", BoardPrefs.followBoard("classic", true));
        assertEquals("off", BoardPrefs.followBoard("off", true));
        assertEquals("strap", BoardPrefs.followBoard("strap", false));
        // OK and Right: in my scoreboard's look, classic, off, round again; Left the other way
        assertEquals("classic", BoardPrefs.nextPopup("strap", 1));
        assertEquals("off", BoardPrefs.nextPopup("classic", 1));
        assertEquals("strap", BoardPrefs.nextPopup("off", 1));
        assertEquals("off", BoardPrefs.nextPopup("strap", -1));
        assertEquals("strap", BoardPrefs.nextPopup("classic", -1));
        assertEquals("In my scoreboard's look", BoardPrefs.popupWords("strap"));
        assertEquals("Classic", BoardPrefs.popupWords("classic"));
        assertEquals("Off", BoardPrefs.popupWords("off"));
    }

    @Test public void theSwitchIsMadeOnceOnTheTvsOwnSettings() {
        Context c = RuntimeEnvironment.getApplication();
        SharedPreferences p = c.getSharedPreferences("scoreboard", Context.MODE_PRIVATE);
        try {
            // the old switch on: the board's look, once; a style chosen after is kept
            p.edit().clear().putBoolean("popup", true).commit();
            assertEquals("strap", BoardPrefs.popupStyle(c));
            assertTrue(p.getBoolean("popup_follows_board", false));
            BoardPrefs.setPopupStyle(c, "classic");
            assertEquals("classic", BoardPrefs.popupStyle(c));
            assertTrue(BoardPrefs.popupOn(c));
            // the old switch off: off stays off, and off is off
            p.edit().clear().putBoolean("popup", false).commit();
            assertEquals("off", BoardPrefs.popupStyle(c));
            assertFalse(BoardPrefs.popupOn(c));
            // a new install: in my scoreboard's look
            p.edit().clear().commit();
            assertEquals("strap", BoardPrefs.popupStyle(c));
            assertTrue(BoardPrefs.popupOn(c));
        } finally {
            p.edit().clear().commit();
        }
    }

    @Test public void theStrapCarriesTheBoardAsItIsDrawn() throws Exception {
        Game g = new Game();
        g.id = "900001";
        g.home = "HAR";
        g.away = "MIL";
        g.homeName = "Harbourside";
        g.awayName = "Millbrook";
        g.homeFull = "Harbourside Athletic";
        g.awayFull = "Millbrook United";
        g.homeColor = "0e4c92";
        g.homeAlt = "f2c200";
        g.awayColor = "050505";   // too dark for the board's navy: its second colour there
        g.awayAlt = "c8102e";
        g.slug = "uefa.champions";
        g.leagueName = "UEFA Champions League";
        g.venue = "Harbour Park";
        JSONObject o = BoardLayer.strapCfg(g, BoardLayer.match(g), "kickoff", null, null, null, BoardBug.Style.COMPETITION, null,
                null, new float[]{86.4f, 48.6f, 452.2f, 60.8f});
        assertEquals("UCL", o.getString("look"));
        JSONObject b = o.getJSONObject("board");
        assertEquals(86, b.getInt("x"));
        assertEquals(49, b.getInt("y"));
        assertEquals(452, b.getInt("w"));
        assertEquals(61, b.getInt("h"));
        // each side's colour as the board has it, and the name the board gives the club
        assertEquals(hex(BoardBug.colour(0x0e4c92, 0xf2c200)), o.getJSONObject("home").getString("board"));
        assertEquals(hex(BoardBug.colour(0x050505, 0xc8102e)), o.getJSONObject("away").getString("board"));
        assertEquals("#c8102e", o.getJSONObject("away").getString("board"));
        assertEquals("HARBOURSIDE", o.getJSONObject("home").getString("retro"));
        assertEquals("CHAMPIONS LEAGUE", o.getString("compTitle"));
        // the classic looks draw their own board on the page: no more than the look and the place
        JSONObject c = BoardLayer.strapCfg(g, BoardLayer.match(g), "second", new int[]{1, 1}, null, null, BoardBug.Style.GLASS, null, null,
                null);
        assertEquals("GLASS", c.getString("look"));
        assertFalse(c.has("board"));
        assertFalse(c.has("compTitle"));
    }

    @Test public void theStrapsClockStartsFromTheBoardsWhileItRuns() {
        assertEquals(1421, BoardLayer.clockSecs("23:41"));
        assertEquals(5405, BoardLayer.clockSecs("90:05"));
        assertEquals(7200, BoardLayer.clockSecs("120:00"));
        assertEquals(3, BoardLayer.clockSecs(" 00:03 "));
        // not a running clock: the strap keeps its own (from the moment)
        assertEquals(-1, BoardLayer.clockSecs("HT"));
        assertEquals(-1, BoardLayer.clockSecs("FT"));
        assertEquals(-1, BoardLayer.clockSecs("67'"));
        assertEquals(-1, BoardLayer.clockSecs(""));
        assertEquals(-1, BoardLayer.clockSecs(null));
    }

    private static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
    }
}
