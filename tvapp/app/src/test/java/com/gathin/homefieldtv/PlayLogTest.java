package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.gathin.homefieldtv.engine.StreamGate;
import com.gathin.homefieldtv.engine.StreamStats;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The play log (PlayDiag) and what the live player does with an error (LiveTv.afterError): a stall's line as it ends
 * (its cause: the live connection dropped and connected again, the player's error by its code's name), the minute's
 * line (stalls, reconnects, errors, the stream's kind, its connections), neither carrying an address, a login or a
 * token whatever the errors' own messages hold; nothing written for a minute nothing played; and behind an HLS stream's
 * live window, straight back to its live position (a few times running), its MPEG-TS for a provider's HLS form that
 * never played, HLS for an address that wasn't MPEG-TS, else the channel's tries.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34)
public class PlayLogTest {
    private static final Pattern SECRET = Pattern.compile("(?i)https?:|provider|family|secret|token|\\.ts\\b|\\.m3u8");

    @Before public void before() {
        // The counts from nothing.
        StreamStats.take();
        StreamGate.take();
    }

    @Test public void theLinesSayWhatHappenedAndNeverTheAddress() {
        List<String> lines = new ArrayList<>();
        PlayDiag d = new PlayDiag(lines::add);
        long[] buffered = {12_300};
        d.attach(new PlayDiag.Now() {
            @Override public boolean wants() {
                return true;
            }

            @Override public long bufferedMs() {
                return buffered[0];
            }
        });
        // A channel starting is not a stall.
        d.channel();
        d.starting("TS");
        d.state(Player.STATE_BUFFERING);
        d.state(Player.STATE_READY);
        assertTrue(lines.isEmpty());

        // A stall: the provider closed the connection and LiveHttp connected it again.
        buffered[0] = 200;
        d.state(Player.STATE_BUFFERING);
        StreamStats.dropped(StreamStats.EOF, 0);
        StreamStats.reconnected(StreamStats.EOF, 0, 120);
        // A failed load whose message has the address in it (Media3's do).
        d.loadError(new IOException("http://provider.invalid:8080/live/family/secret-pass/1234.ts?token=abc"));
        d.state(Player.STATE_READY);
        assertEquals(1, lines.size());
        String stall = lines.get(0);
        System.out.println("STALL LINE " + stall);
        assertTrue(stall, stall.startsWith("stall "));
        assertTrue(stall, stall.contains("cause=reconnect:eof"));
        assertTrue(stall, stall.contains("type=TS"));
        assertTrue(stall, stall.contains("buffer=0.2s/"));
        assertTrue(stall, stall.contains(" gc=") && stall.contains(" main=") && stall.contains(" quiet=") && stall.contains(" conns="));

        // The player's error while it played: a stall until the picture is back, its code's name the cause.
        d.playerError(PlaybackException.getErrorCodeName(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS));
        d.state(Player.STATE_IDLE);
        d.retry();
        d.starting("TS");
        d.state(Player.STATE_BUFFERING);
        d.state(Player.STATE_READY);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1), lines.get(1).contains("cause=error:io-bad-http-status"));

        String minute = d.minute("playing", 12_300);
        System.out.println("MINUTE LINE " + minute);
        assertTrue(minute, minute.startsWith("play type=TS state=playing stalls=2/"));
        assertTrue(minute, minute.contains(" channel=2/"));
        assertTrue(minute, minute.contains(" reconnects=1/0.1s(eof:1)"));
        assertTrue(minute, minute.contains(" errors=1(io-bad-http-status)"));
        assertTrue(minute, minute.contains(" retries=1") && minute.contains(" conns=") && minute.contains(" buffer=12.3s/"));
        for (String l : lines) assertFalse(l, SECRET.matcher(l).find());
        assertFalse(minute, SECRET.matcher(minute).find());

        // The next minute starts from nothing; let go, a minute with nothing played writes nothing.
        d.stopped();
        String next = d.minute("idle", 0);
        if (next != null) assertTrue(next, next.contains("stalls=0/0.0s"));
        assertNull(d.minute("idle", 0));
    }

    @Test public void aStallsCauseInOrder() {
        long t = 100_000;
        long[] none = {-1, -1, 0, -1};
        assertEquals("error:behind-live-window", PlayDiag.cause(null, "behind-live-window", none, t, -1, -1, 0, false, 0));
        assertEquals("gaveup:http503", PlayDiag.cause(null, null, new long[]{t - 100, StreamStats.HTTP, 503, t + 9_000}, t, -1, -1, 0, false, 0));
        assertEquals("reconnect:reset", PlayDiag.cause(null, null, new long[]{t - 2_000, StreamStats.RESET, 0, -1}, t, -1, -1, 0, false, 0));
        // A drop long before isn't this stall's.
        assertEquals("slow", PlayDiag.cause(null, null, new long[]{t - 60_000, StreamStats.RESET, 0, -1}, t, -1, -1, 0, false, 0));
        assertEquals("load:timeout", PlayDiag.cause(null, null, none, t, t + 50, StreamStats.TIMEOUT, 0, false, 0));
        assertEquals("decoder", PlayDiag.cause(null, null, none, t, -1, -1, 0, true, 0));
        assertEquals("nodata", PlayDiag.cause(null, null, none, t, -1, -1, 0, false, 2_300));
        assertEquals("left", PlayDiag.cause("left", null, none, t, -1, -1, 0, false, 0));
    }

    @Test public void behindTheLiveWindowGoesStraightBackToLive() {
        int behind = PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW;
        // An HLS stream that fell behind its window: back to its live position, the same stream, three times running.
        assertEquals(LiveTv.BACK_TO_LIVE, LiveTv.afterError(behind, false, 0, false, true, true));
        assertEquals(LiveTv.BACK_TO_LIVE, LiveTv.afterError(behind, false, 2, true, true, true));
        // Falling behind again and again: the channel's tries (STEADY_MS of playing starts the count again).
        assertEquals(LiveTv.DROPPED, LiveTv.afterError(behind, false, LiveTv.BEHIND_MOST, false, true, true));
        // The archive has its own tries.
        assertEquals(LiveTv.ARCHIVE, LiveTv.afterError(behind, true, 0, false, true, true));
        // The provider's HLS form of a TS channel that never played: its MPEG-TS.
        assertEquals(LiveTv.AS_TS, LiveTv.afterError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, false, 0, true, false, true));
        // Read as MPEG-TS but it wasn't: as HLS.
        assertEquals(LiveTv.AS_HLS, LiveTv.afterError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, false, 0, false, false, false));
        // A connection that failed for good (LiveHttp tried for 12 s): the channel's tries.
        assertEquals(LiveTv.DROPPED, LiveTv.afterError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, false, 0, false, true, false));
    }
}
