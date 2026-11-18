package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The kick-off strap's looks (strap.js): one for every look the boards have, By competition's resolved to the game's own,
 * each with a sound. The apps' own tests check their boards' looks against the page with pageLooks().
 */
public class StrapLooksTest {
    /** A file of overlays/ found from the tests' folder (each app's module folder: launcher/app, phone/tvhome-phone/app...). */
    public static File overlays(String path) {
        File d = new File("").getAbsoluteFile();
        for (int i = 0; i < 6 && d != null; i++, d = d.getParentFile()) {
            File f = new File(d, "overlays/" + path);
            if (f.exists()) return f;
        }
        throw new AssertionError("no overlays/" + path + " above " + new File("").getAbsolutePath());
    }

    private static final Pattern LOOK = Pattern.compile("^\\s*S\\.([a-z0-9_]+)\\s*=", Pattern.MULTILINE);

    /** The looks the strap's page draws: its S.<name> keys, as BroadcastData names them (in capitals). */
    public static Set<String> pageLooks() throws IOException {
        String js = new String(Files.readAllBytes(overlays("src/strap/strap.js").toPath()), StandardCharsets.UTF_8);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = LOOK.matcher(js);
        while (m.find()) out.add(m.group(1).toUpperCase(Locale.ROOT));
        return out;
    }

    @Test
    public void thePageDrawsEveryLook() throws IOException {
        Set<String> page = pageLooks();
        assertEquals(35, BroadcastData.STRAP_LOOKS.length);
        for (String l : BroadcastData.STRAP_LOOKS) assertTrue("strap.js has no S." + l.toLowerCase(Locale.ROOT), page.contains(l));
        assertEquals(new LinkedHashSet<>(Arrays.asList(BroadcastData.STRAP_LOOKS)), page);
    }

    @Test
    public void theAppsGetThePageAsItIsWritten() throws IOException {
        // build.sh copies the page into assets/broadcast: the copy the apps package is the source as it stands
        for (String[] f : new String[][]{{"src/strap/strap.js", "assets/broadcast/strap.js"}, {"src/host/strap.html", "assets/broadcast/strap.html"},
                {"src/host/strap-host.js", "assets/broadcast/strap-host.js"}}) {
            assertTrue(f[1] + " is not " + f[0], Arrays.equals(Files.readAllBytes(overlays(f[0]).toPath()), Files.readAllBytes(overlays(f[1]).toPath())));
        }
    }

    @Test
    public void everyCompetitionLookHasItsName() {
        for (CompBug.Kind k : CompBug.Kind.values()) {
            String name = BroadcastData.lookName(k);
            assertTrue(name, Arrays.asList(BroadcastData.STRAP_LOOKS).contains(name));
            assertEquals(k, BroadcastData.kindOf(name));
        }
        assertEquals("PL_WORLD", BroadcastData.lookName(CompBug.Kind.PL));
        assertEquals("RETRO_2008", BroadcastData.lookName(CompBug.Kind.R2008));
        assertNull(BroadcastData.kindOf("KITS"));
        assertNull(BroadcastData.kindOf("CLASSIC"));
    }

    @Test
    public void byCompetitionIsTheGamesOwnLookElseClassic() {
        assertEquals("PL_WORLD", BroadcastData.strapLook("COMPETITION", "eng.1", "English Premier League"));
        assertEquals("UCL", BroadcastData.strapLook("COMPETITION", "uefa.champions", ""));
        assertEquals("LALIGA", BroadcastData.strapLook("COMPETITION", "esp.1", "LaLiga"));
        assertEquals("LIGA_MX", BroadcastData.strapLook("COMPETITION", "mex.1", ""));
        assertEquals("EFL", BroadcastData.strapLook("COMPETITION", "eng.league_cup", "Carabao Cup"));
        assertEquals("SERIE_A", BroadcastData.strapLook("COMPETITION", "", "Italian Serie A"));
        // a game with no look of its own: Classic, as the board falls back to
        assertEquals("CLASSIC", BroadcastData.strapLook("COMPETITION", "fifa.friendly", "International Friendly"));
        assertEquals("CLASSIC", BroadcastData.strapLook("COMPETITION", null, null));
        // a look picked by hand is its own, whatever the game
        assertEquals("UCL", BroadcastData.strapLook("ucl", "eng.1", "English Premier League"));
        assertEquals("RETRO_2004", BroadcastData.strapLook("RETRO_2004", "esp.1", "LaLiga"));
        assertEquals("FROSTED", BroadcastData.strapLook("FROSTED", null, null));
        // one the page does not know: Classic
        assertEquals("CLASSIC", BroadcastData.strapLook("NEON", null, null));
        assertEquals("CLASSIC", BroadcastData.strapLook(null, null, null));
    }

    @Test
    public void everyLookHasASoundThere() {
        for (String l : BroadcastData.STRAP_LOOKS) {
            String s = BroadcastData.strapSound(l);
            assertTrue(l + ": " + s, s.matches("strap-[a-z]+"));
            assertTrue(l + ": no " + s, overlays("src/sfx/" + s + ".ogg").exists());
        }
        assertEquals("strap-glass", BroadcastData.strapSound("GLASS"));
        assertEquals("strap-classic", BroadcastData.strapSound("UCL"));
        assertEquals("strap-club", BroadcastData.strapSound("SPLITKIT"));
    }

    @Test
    public void aDrawnBoardsStrapCarriesWhatItsBoardShows() throws Exception {
        BroadcastData.Match m = new BroadcastData.Match();
        m.homeName = "Harbourside Athletic";
        m.awayName = "Millbrook United";
        m.homeShort = "HAR";
        m.awayShort = "MIL";
        m.compId = "eng.1";
        m.compName = "English Premier League";
        m.homeBoard = 0xFF0E4C92;
        m.awayBoardName = "Millbrook Utd";
        org.json.JSONObject o = BroadcastData.strap("kickoff", m, null, null, null, "COMPETITION", new float[]{86, 49, 380, 110}, "");
        assertEquals("PL_WORLD", o.getString("look"));
        assertEquals("strap-club", o.getString("sfx"));   // its halves meet in the middle, as Club colours' do
        assertEquals("PL", o.getString("compShort"));
        assertEquals("PREMIER LEAGUE", o.getString("compTitle"));
        assertEquals("#0e4c92", o.getJSONObject("home").getString("board"));
        assertTrue(!o.getJSONObject("away").has("board"));
        // the names the retro boards set: the board's own name for the club, else the pop-up's
        assertEquals("HARBOURSIDE", o.getJSONObject("home").getString("retro"));
        assertEquals("MILLBROOK UTD", o.getJSONObject("away").getString("retro"));
        assertEquals(380, o.getJSONObject("board").getInt("w"));
        // the first nine draw their own boards: nothing more for them
        org.json.JSONObject c = BroadcastData.strap("kickoff", m, null, null, null, "GLASS", null, "");
        assertEquals("GLASS", c.getString("look"));
        assertTrue(!c.has("compShort"));
        assertNotNull(c.optString("sfx"));
    }
}
