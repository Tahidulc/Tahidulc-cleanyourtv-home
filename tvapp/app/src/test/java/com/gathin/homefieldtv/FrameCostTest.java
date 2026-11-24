package com.gathin.homefieldtv;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.FrameCostData;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Sources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.SportsData;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The frame budget of every 07 screen while the remote moves (the guide's own is GuideDrawTest): each screen opened with a
 * made-up family playlist and drawn pictures (FrameCostData), its motion played frame by frame (a key held at 20 repeats
 * a second along a row, a press and its lift), and each frame's cost measured on the JVM (FrameMeter): the drawing calls
 * the UI thread records again, the bytes it makes, the fill the graphics chip does within the frame's damage (in whole
 * screens), the offscreen layers and the calls drawn in perspective.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class FrameCostTest {
    private Context app;
    private TvActivity a;
    private FrameMeter meter;
    private final List<FrameMeter.Run> runs = new ArrayList<>();

    @After public void after() {
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
        }
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void start() throws Exception {
        app = RuntimeEnvironment.getApplication();
        Exec.inline = true;
        File dir = new File(app.getCacheDir(), "frame-cost");
        FrameCostData.load(app, dir);
        Library lib = Library.get(app);
        for (int ci = 0; ci < FrameCostData.MADE.size(); ci++) {
            Channel ch = FrameCostData.MADE.get(ci);
            Guide.Schedule s = lib.schedule(ch.key);
            if (s == null) continue;
            for (int i = 0; i < s.start.length; i++) ProgrammePictures.preview(ch, s, i, FrameCostData.programmeOf(dir, ci, i));
        }
        // Home style Classic: these runs are its pages' (Home's card and strip, Movies' pills); EditorialFrameTest has Editorial's.
        com.gathin.homefieldtv.prefs.HomeStyle.set(app, com.gathin.homefieldtv.prefs.HomeStyle.CLASSIC);
        a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idle();
        View root = a.getWindow().getDecorView();
        FrameMeter.layout(root);
        meter = new FrameMeter(root);
    }

    /** Warm: the motion once with everything in place at once (pictures read, words measured), then back. */
    private void warm(Runnable motion, Runnable back) {
        Exec.inline = true;
        for (int i = 0; i < 3; i++) meter.tick();
        motion.run();
        for (int i = 0; i < 3; i++) meter.tick();
        back.run();
        for (int i = 0; i < 3; i++) meter.tick();
        Exec.inline = false;
        for (int i = 0; i < 70; i++) meter.tick();
        // And once moving as it will be measured (the JVM's first calls of each drawing path are slow and make garbage).
        motion.run();
        for (int i = 0; i < 40; i++) meter.tick();
        back.run();
        for (int i = 0; i < 70; i++) meter.tick();
    }

    private void keys(int code, int n) {
        for (int i = 0; i < n; i++) {
            FrameMeter.down(a, code, 0);
            FrameMeter.up(a, code);
            for (int f = 0; f < 4; f++) meter.tick();
        }
    }

    private void measure(String name, int code, int back, int n) {
        warm(() -> keys(code, n), () -> keys(back, n));
        FrameMeter.Run run = new FrameMeter.Run(name);
        meter.hold(a, run, code, n);
        meter.hold(a, run, back, n);
        meter.press(a, run, code);
        runs.add(run);
    }

    /** Single presses (a key that means another thing held), each with its motion to the end; then back. */
    private void taps(String name, int code, int back, int n) {
        warm(() -> keys(code, n), () -> keys(back, n));
        FrameMeter.Run run = new FrameMeter.Run(name);
        for (int i = 0; i < n; i++) meter.press(a, run, code);
        for (int i = 0; i < n; i++) meter.press(a, run, back);
        runs.add(run);
    }

    private void leaveBar() {
        if (a.tabs().active()) a.tabs().leave();
        idle();
    }

    @Test public void everyScreenMovesWithinItsBudget() throws Exception {
        start();
        Motion.force(false);
        scenarios(" (Full)");
        a.home();
        idle();
        Motion.force(true);
        scenarios(" (Lite)");
        Motion.force(false);

        StringBuilder b = new StringBuilder("\nFRAME COST (JVM, a frame of motion; ops: calls drawn, rec: recorded on the TV's canvas; fill"
                + " and damage in whole 1920x1080 screens)\n");
        b.append(FrameMeter.Run.header()).append('\n');
        for (FrameMeter.Run r : runs) b.append(r.row()).append('\n');
        if (FrameMeter.TRACE) for (FrameMeter.Run r : runs) b.append(r.views()).append('\n');
        System.out.println(b);
        for (FrameMeter.Run r : runs) {
            assertTrue(r.name + " drew no frame", r.frames > 0);
            if (!r.presses) assertTrue(r.name + ": a frame made " + r.bytesMedian() + " bytes", r.bytesMedian() <= FRAME_BYTES);
            assertTrue(r.name + ": frames made " + r.bytesPerFrame() + " bytes on average", r.bytesPerFrame() <= MEAN_BYTES);
            assertEquals(r.name + ": shadows made while moving", 0, r.sprites);
            if (r.name.endsWith("(Lite)")) assertEquals(r.name + ": drawn in perspective (Lite has no tilt)", 0.0, r.perspectivePerFrame(), 0.0);
        }
    }

    /** A frame of motion may make at most this many bytes on the heap (the guide's own budget, GuideDrawTest). */
    static final long FRAME_BYTES = GuideDrawTest.FRAME_BYTES;
    /** On average, with the frames that record a kept part again (a picture or the minute changed; costly on the JVM). */
    static final long MEAN_BYTES = 16 * 1024;

    private void scenarios(String tier) {
        // Home: the cards along a section, then the strip under one.
        leaveBar();
        measure("Home: cards" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 6);
        keys(KeyEvent.KEYCODE_DPAD_DOWN, 1);
        measure("Home: a card's strip" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 6);
        keys(KeyEvent.KEYCODE_DPAD_UP, 1);

        // Movies: down to the shelves, along the posters, down the shelves.
        a.fromHome(new BrowseScreen(a, false));
        idle();
        leaveBar();
        keys(KeyEvent.KEYCODE_DPAD_DOWN, 1);
        measure("Movies: posters" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 8);
        measure("Movies: shelves down" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 2);

        // The tab bar: the pill sliding from Movies to TV Shows and back, each page coming up under it.
        a.tabs().enter();
        idle();
        measure("Tab bar: pill and page" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 1);
        leaveBar();

        // A series' page: down to its episodes, along them.
        a.home();
        idle();
        a.openTitle(Catalog.get(app).title("p0|s|1"));
        idle();
        keys(KeyEvent.KEYCODE_DPAD_DOWN, 1);
        measure("Title: episodes" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 8);

        // Sports: along today's games.
        a.home();
        idle();
        SportsScreen hub = new SportsScreen(a);
        a.fromHome(hub);
        idle();
        hub.preview(games(), new HashMap<>());
        idle();
        leaveBar();
        measure("Sports: today" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 5);

        // Search: a word typed, along its results.
        a.home();
        idle();
        a.openSearch();
        idle();
        SearchScreen s = (SearchScreen) a.top();
        Exec.inline = true;
        s.type("e");
        idle();
        s.previewResults(0, 0);
        idle();
        Exec.inline = false;
        measure("Search: results" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 6);

        // My List: along its cards.
        a.home();
        idle();
        a.fromHome(new MyListScreen(a));
        idle();
        MyListScreen m = (MyListScreen) a.top();
        m.previewList(MyListScreen.MOVIES, true);
        idle();
        measure("My List: cards" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 4);

        // The guide: down its channels, along its times.
        a.home();
        idle();
        a.fromHome(new GuideScreen(a, null));
        idle();
        leaveBar();
        measure("Guide: channels" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 8);
        measure("Guide: times" + tier, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT, 4);

        // A channel playing: CH+ through the channels (the info card's picture cross-fading), Down through the mini guide.
        a.home();
        idle();
        Exec.inline = true;
        a.watch(FrameCostData.MADE.get(2));
        idle();
        Exec.inline = false;
        taps("Live: info card (CH+)" + tier, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, 4);
        taps("Live: mini guide" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 4);

        // The channel list (Left held over the picture): down its rows.
        FrameMeter.down(a, KeyEvent.KEYCODE_DPAD_LEFT, 0);
        FrameMeter.down(a, KeyEvent.KEYCODE_DPAD_LEFT, 14);
        FrameMeter.up(a, KeyEvent.KEYCODE_DPAD_LEFT);
        idle();
        assertTrue("the channel list open", a.top() instanceof ChannelListScreen);
        measure("Channel list: rows" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 6);
        // Its focus moves with no glide: each frame drawn is a key's, laying out the programmes of the channel it brought.
        runs.get(runs.size() - 1).presses = true;

        // Settings: down its rows (over Home).
        a.home();
        idle();
        a.openSettings();
        idle();
        measure("Settings: rows" + tier, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, 6);
        a.home();
        idle();
    }

    /** Today's games: live ones and later ones, invented clubs, no crests (their initials drawn instead). */
    private static List<SportsData.Item> games() {
        String[][] clubs = {{"Northland", "Marisca"}, {"Eastmoor", "Valdora"}, {"Kestria", "Norvik"}, {"Ostrava", "Lowmark"},
                {"Sarnia", "Tirol"}, {"Pelland", "Svea"}, {"Arvenia", "Costara"}, {"Chileno", "Columbra"}};
        List<SportsData.Item> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < clubs.length; i++) {
            Game g = new Game();
            g.id = String.valueOf(900 + i);
            g.slug = "uefa.nations";
            g.homeId = "h" + i;
            g.awayId = "a" + i;
            g.home = g.homeName = clubs[i][0];
            g.away = g.awayName = clubs[i][1];
            g.homeColor = "c8102e";
            g.awayColor = "1b4fa0";
            g.homeScore = i % 3;
            g.awayScore = (i + 1) % 2;
            g.state = i < 5 ? Game.LIVE : Game.PRE;
            g.detail = i < 5 ? (60 + i) + "'" : "";
            g.kickoff = i < 5 ? now - 70 * 60_000L : now + (60 + i * 15) * 60_000L;
            g.venue = "Harbourside Arena";
            SportsData.Item it = new SportsData.Item();
            it.kind = SportsData.FOOTBALL;
            it.key = "football:" + g.id;
            it.game = g;
            it.home = g.homeName;
            it.away = g.awayName;
            it.homeLogo = "";
            it.awayLogo = "";
            it.comp = i < 5 ? "UEFA Nations League" : "International Friendly";
            it.start = g.kickoff;
            it.live = g.state == Game.LIVE;
            it.over = false;
            it.score = g.state == Game.PRE ? "" : g.homeScore + "–" + g.awayScore;
            it.status = g.state == Game.PRE ? "" : g.detail;
            items.add(it);
        }
        return items;
    }
}
