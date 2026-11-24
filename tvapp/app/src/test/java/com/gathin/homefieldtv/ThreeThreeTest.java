package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.prefs.Merges;
import com.gathin.homefieldtv.prefs.OtherPrefs;
import com.gathin.homefieldtv.sports.Game;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 3.3's features as 3.4 rebuilds them: channels merged by hand (out of the lists, their stream, guide and catch-up standing
 * in, alongside One row per channel), Automatic updates' address and its words, the logo box's own colours, the
 * scoreboard's menu on every channel, Sports' days and Skip steps. Invented channels and addresses.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ThreeThreeTest {
    private Context app;
    private Library lib;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        lib = Library.get(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "made-up"));
        Merges.separateAll(app);
    }

    @After public void tearDown() {
        Merges.separateAll(app);
        Merges.forget();
        lib.clear();
        Sources.clear(app);
        Exec.inline = false;
    }

    private static Channel ch(String key, String name, String group, String tvg, boolean archive) {
        Channel c = new Channel(key, name, group, "", "http://provider.invalid/live/" + key.replace(':', '-') + ".ts", tvg, null, null, 0);
        return archive ? c.archived(Catchup.XC, "", 3, "") : c;
    }

    @Test public void aChannelMergedByHandLeavesTheListsAndStandsIn() {
        long now = System.currentTimeMillis();
        List<Channel> list = new ArrayList<>();
        list.add(ch("e:1", "Harbourlight One FHD", "UK Entertainment", "", false));
        list.add(ch("e:2", "Harbourlight One HD", "UK Entertainment", "", false));
        list.add(ch("e:3", "Harbour Light 1 Backup", "UK Extra", "hl1.uk", true));
        list.add(ch("e:4", "Fernhill News", "UK News", "", false));
        Map<String, Guide.Schedule> guide = new HashMap<>();
        guide.put("e:3", com.gathin.homefieldtv.engine.MadeUpGuide.schedule(new long[]{now - 600_000L}, new long[]{now + 1_200_000L}));
        lib.useForPreview(list, guide);
        Channel main = lib.byKey("e:1"), backup = lib.byKey("e:3");
        assertEquals(3, lib.all().size());
        assertNull(lib.schedule(main.key));
        assertNull(Catchup.source(main));

        // Merge with: the backup joins Harbourlight One.
        assertNull(Merges.merge(app, backup, main));
        assertEquals(2, lib.all().size());
        assertFalse(lib.all().contains(backup));
        // Its stream after the channel's own two qualities; its guide and its catch-up stand in.
        List<Channel> vs = lib.versions(main);
        assertEquals("e:3", vs.get(vs.size() - 1).key);
        assertEquals(3, vs.size());
        assertNotNull(lib.schedule(main.key));
        assertEquals("e:3", Catchup.source(main).key);
        assertEquals(1, Merges.keysInto(main).size());

        // Never into itself, one of its own versions, or a channel merged away.
        assertEquals(Merges.CANT, Merges.merge(app, main, lib.exact("e:2")));
        assertEquals(Merges.CANT, Merges.merge(app, lib.byKey("e:4"), backup));

        // Separated: back in the lists.
        assertEquals(1, Merges.separateFrom(app, main));
        assertEquals(3, lib.all().size());
        assertNull(lib.schedule(main.key));
    }

    @Test public void theMergeCandidatesAreTheLikeliestFirst() {
        List<Channel> list = new ArrayList<>();
        list.add(ch("a", "Kestrel Sport 1", "UK Sport", "", false));
        list.add(ch("b", "Lantern Movies", "UK Sport", "", false));
        list.add(ch("c", "KESTREL SPORT 1 HD", "Backups", "", false));
        list.add(ch("d", "Kestrel Arena", "Elsewhere", "", false));
        list.add(ch("e", "Moorhen Kids", "Elsewhere", "", false));
        List<Channel> c = MergeScreen.candidates(list.get(0), list, 10);
        assertEquals("c", c.get(0).key);
        assertEquals("b", c.get(1).key);
        assertEquals("d", c.get(2).key);
        assertEquals(3, c.size());
    }

    @Test public void automaticUpdatesReadTheirAddressAndSayWhatWentWrong() throws Exception {
        assertNull(AutoUpdate.web("not an address"));
        assertNull(AutoUpdate.web("ftp://updates.invalid/update.json"));
        URI u = AutoUpdate.web("https://updates.invalid/homefield/update.json");
        assertNotNull(u);
        String[] apk = AutoUpdate.apkOf(u, new JSONObject("{\"code\":40,\"name\":\"3.5\",\"apk\":\"homefieldtv-3.5.apk\"}"));
        assertEquals("https://updates.invalid/homefield/homefieldtv-3.5.apk", apk[0]);
        assertEquals("homefieldtv-3.5.apk", apk[1]);
        apk = AutoUpdate.apkOf(u, new JSONObject("{\"code\":40,\"url\":\"https://files.invalid/dl/app-release.apk?x=1\"}"));
        assertEquals("app-release.apk", apk[1]);
        assertNull(AutoUpdate.apkOf(u, new JSONObject("{\"code\":40}")));
        assertEquals("Nothing at the update address yet (or the repository is private)", AutoUpdate.httpProblem(404));
        assertEquals("The update address answered HTTP 500", AutoUpdate.httpProblem(500));
        assertEquals(AutoUpdate.OFF, AutoUpdate.mode(app));
        AutoUpdate.setMode(app, AutoUpdate.ON_OPEN);
        assertEquals(AutoUpdate.ON_OPEN, AutoUpdate.mode(app));
        AutoUpdate.setMode(app, AutoUpdate.OFF);
    }

    @Test public void theLogoBoxCanBeOneColourForEvery() {
        assertEquals("Forest green", Logos.NAMES[3]);
        assertEquals("Midnight blue", Logos.NAMES[4]);
        assertEquals("Slate grey", Logos.NAMES[5]);
        try {
            Logos.setBehind(app, 3);
            assertTrue(Logos.fixed());
            assertNotNull(Logos.fixedTint());
            assertSame(Logos.fixedTint(), ChannelLook.tint(null));
            assertEquals(Pics.CELL_NONE, Logos.cellMode());
        } finally {
            Logos.setBehind(app, Logos.COLOUR);
        }
        assertNull(Logos.fixedTint());
    }

    @Test public void theScoreboardsMenuSaysWhatIsOnTheChannel() {
        assertEquals("No match on this channel now", BoardLayer.menuLine(null, false, false));
        assertEquals("Reading the match", BoardLayer.menuLine(null, true, false));
        Game g = new Game();
        g.home = "ASH";
        g.homeName = "Ashby Rovers";
        g.away = "CAL";
        g.awayName = "Calderby Town";
        assertEquals("Reading the scores", BoardLayer.menuLine(g, false, false));
        assertEquals("Ashby Rovers v Calderby Town", BoardLayer.menuLine(g, false, true));
    }

    @Test public void sportsHasItsDays() {
        assertEquals("F1 & more", SportsScreen.SECTION_NAMES[SportsScreen.FORMULA]);
        assertEquals("No football tomorrow in the competitions followed.", SportsScreen.listWords(SportsScreen.TOMORROW, false, true, ""));
        assertEquals("No football yesterday in the competitions followed.", SportsScreen.listWords(SportsScreen.YESTERDAY, false, true, ""));
        assertEquals("No football today in the competitions followed.", SportsScreen.listWords(SportsScreen.TODAY, false, true, ""));
        assertEquals("No football is live right now. Today's games are under Today.",
                SportsScreen.listWords(SportsScreen.LIVE, false, true, ""));
        assertEquals("Today's matches come up here once they are read", SportsScreen.listWords(SportsScreen.TODAY, true, true, ""));
    }

    @Test public void skipStepsAsChosen() {
        assertEquals(10_000L, OtherPrefs.skipMs(app));
        OtherPrefs.setSkip(app, 5);
        assertEquals(60_000L, OtherPrefs.skipMs(app));
        assertEquals("1 minute", OtherPrefs.SKIP_NAMES[OtherPrefs.skipIndex(app)]);
        OtherPrefs.setSkip(app, 1);
    }
}
