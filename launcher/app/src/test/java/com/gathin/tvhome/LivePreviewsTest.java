package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The family's account allows one connection at a time: every channel the launcher plays live itself (a dock tile's
 * TilePlayer, the guide's corner) is let go before Homefield TV (or another app) is asked to play one, so Homefield TV's
 * connection is never refused for the preview's, nor the preview cut by it. Each player says when it plays and when it
 * lets go; ChannelOpen, every channel link's one routing point, lets them all go first.
 */
public class LivePreviewsTest {
    private static final class Player implements LivePreviews.Preview {
        final List<String> log;
        final String name;

        Player(List<String> log, String name) {
            this.log = log;
            this.name = name;
        }

        @Override public void stopLive() {
            log.add("stop " + name);
            LivePreviews.stopped(this);
        }
    }

    @Test public void everyLiveChannelIsLetGoBeforeAChannelOpens() {
        List<String> log = new ArrayList<>();
        Player tile = new Player(log, "tile"), corner = new Player(log, "corner"), gone = new Player(log, "gone");
        LivePreviews.playing(tile);
        LivePreviews.playing(corner);
        LivePreviews.playing(gone);
        LivePreviews.stopped(gone);
        assertEquals(2, LivePreviews.count());
        LivePreviews.stopNow();
        assertEquals(0, LivePreviews.count());
        assertEquals(2, log.size());
        assertTrue(log.contains("stop tile") && log.contains("stop corner"));
        // Nothing playing: nothing to do.
        LivePreviews.stopNow();
        assertEquals(2, log.size());
    }

    private static String read(String name) throws IOException {
        File d = new File("src/main/java/com/gathin/tvhome");
        if (!d.isDirectory()) d = new File("app/src/main/java/com/gathin/tvhome");
        return new String(Files.readAllBytes(new File(d, name).toPath()), StandardCharsets.UTF_8);
    }

    @Test public void theRoutingPointAndBothPlayersTakePart() throws IOException {
        String open = read("ChannelOpen.java");
        // Before Homefield TV is asked (PLAY_CHANNEL, or opened as its icon opens it), and before any channel link.
        int ask = open.indexOf("static boolean inHomefield");
        int stop = open.indexOf("LivePreviews.stopAll()", ask);
        int start = open.indexOf("c.startActivity(i)", ask);
        assertTrue("inHomefield lets the previews go before it starts Homefield TV", ask > 0 && stop > ask && stop < start);
        assertTrue(open.indexOf("LivePreviews.stopAll()", open.indexOf("static boolean open(Context c, String helperPkg, List<String> names, Bitmap logo, Bitmap art)")) > 0);
        assertTrue(open.contains("if (HOMEFIELD.equals(pkg)) LivePreviews.stopAll();"));
        // The tile's player and the guide's corner say when they play and let go.
        for (String player : new String[]{"TilePlayer.java", "GuideV2.java"}) {
            String s = read(player);
            assertTrue(player, s.contains("implements LivePreviews.Preview"));
            assertTrue(player, s.contains("LivePreviews.playing(this);"));
            assertTrue(player, s.contains("LivePreviews.stopped(this);"));
        }
    }
}
