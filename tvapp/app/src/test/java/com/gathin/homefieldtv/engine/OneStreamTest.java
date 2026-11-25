package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One stream at a time, and a live MPEG-TS stream that rides out its drops, against a made-up provider (each connection
 * scripted: its status, its bytes, then the end, a reset, or nothing more until it is closed): a stream that drops at
 * its end, on a reset or with a 503 is connected again in well under half a second and its bytes carry on with no
 * error for the player; one quiet for too long is connected again; the channel before is closed before the next one
 * connects, and never two connections open at once; a newer stream wins over an older one still connecting; a refusal
 * as it first connects (the account's connection still held a moment) is asked again; a login refused gives up at once;
 * a redirect is followed and the channel's own address asked again each time; a playlist given as MPEG-TS just ends;
 * and no error carries the address. Every address .invalid.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@OptIn(markerClass = UnstableApi.class)
public class OneStreamTest {
    static final String URL = "http://provider.invalid:8080/live/family/secret-pass/1234.ts";
    static final int END = 0, RESET = 1, HANG = 2;

    /** One connection's script. */
    static final class Script {
        final String name;
        final int status;
        final String location;
        final byte[] body;
        final int then;

        Script(String name, int status, String location, byte[] body, int then) {
            this.name = name;
            this.status = status;
            this.location = location;
            this.body = body;
            this.then = then;
        }

        static Script ok(String name, int packets, int then) {
            return new Script(name, 200, null, ts(packets), then);
        }

        static Script status(String name, int code) {
            return new Script(name, code, null, new byte[0], END);
        }
    }

    /** MPEG-TS-shaped bytes: n packets of 188, each starting with the sync byte. */
    static byte[] ts(int packets) {
        byte[] b = new byte[packets * 188];
        for (int i = 0; i < packets; i++) {
            b[i * 188] = 0x47;
            for (int k = 1; k < 188; k++) b[i * 188 + k] = (byte) (i + k);
        }
        return b;
    }

    /** The made-up provider: its connections in order (the last one again when they run out). */
    static final class Provider implements LiveHttp.Opener {
        final List<Script> scripts = new ArrayList<>();
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<String> urls = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger open = new AtomicInteger(), most = new AtomicInteger(), connects = new AtomicInteger();
        private int next;

        Provider(Script... s) {
            Collections.addAll(scripts, s);
        }

        synchronized Script take() {
            Script s = scripts.get(Math.min(next, scripts.size() - 1));
            next++;
            return s;
        }

        @Override public LiveHttp.Conn open(String url, Map<String, String> headers, int connectMs, int readMs) {
            Script s = take();
            urls.add(url);
            connects.incrementAndGet();
            most.accumulateAndGet(open.incrementAndGet(), Math::max);
            events.add("open " + s.name);
            return new Fake(this, s);
        }
    }

    static final class Fake implements LiveHttp.Conn {
        private final Provider p;
        private final Script s;
        private final Object lock = new Object();
        private boolean closed;

        Fake(Provider p, Script s) {
            this.p = p;
            this.s = s;
        }

        @Override public int status() {
            return s.status;
        }

        @Override public String location() {
            return s.location;
        }

        @Override public InputStream body() {
            return new InputStream() {
                int at;

                @Override public int read() throws IOException {
                    byte[] one = new byte[1];
                    int n = read(one, 0, 1);
                    return n < 0 ? -1 : one[0] & 0xFF;
                }

                @Override public int read(byte[] b, int off, int len) throws IOException {
                    synchronized (lock) {
                        if (closed) throw new SocketException("Socket closed");
                    }
                    if (at < s.body.length) {
                        int n = Math.min(len, Math.min(4096, s.body.length - at));
                        System.arraycopy(s.body, at, b, off, n);
                        at += n;
                        return n;
                    }
                    if (s.then == END) return -1;
                    if (s.then == RESET) throw new SocketException("Connection reset by " + URL);
                    synchronized (lock) {
                        while (!closed) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new java.io.InterruptedIOException();
                            }
                        }
                    }
                    throw new SocketException("Socket closed");
                }
            };
        }

        @Override public Map<String, List<String>> headers() {
            return new HashMap<>();
        }

        @Override public void disconnect() {
            synchronized (lock) {
                if (closed) return;
                closed = true;
                lock.notifyAll();
            }
            p.open.decrementAndGet();
            p.events.add("close " + s.name);
        }
    }

    private static LiveHttp source(Provider p, int session) {
        return new LiveHttp.Factory("Homefield/1.0", null, session).opener(p).createDataSource();
    }

    private static DataSpec spec() {
        return new DataSpec(Uri.parse(URL));
    }

    /** Reads until want bytes have come (failing on an error), timing out after a few seconds. */
    private static long readBytes(LiveHttp s, long want) throws IOException {
        byte[] b = new byte[8192];
        long got = 0, until = System.currentTimeMillis() + 5_000;
        while (got < want) {
            if (System.currentTimeMillis() > until) fail("only " + got + " of " + want);
            int n = s.read(b, 0, b.length);
            if (n == C.RESULT_END_OF_INPUT) fail("ended at " + got);
            got += n;
        }
        return got;
    }

    @Before public void before() {
        Exec.inline = false;
        StreamGate.reset();
        StreamStats.reset();
    }

    @After public void after() {
        //noinspection ResultOfMethodCallIgnored
        Thread.interrupted();
        StreamGate.reset();
        StreamStats.reset();
        LiveHttp.againFor = LiveHttp.AGAIN_FOR_MS;
        LiveHttp.firstFor = LiveHttp.FIRST_FOR_MS;
    }

    @Test public void aDropAtItsEndOnAResetOrA503IsConnectedAgainAtOnceWithNoError() throws Exception {
        Provider p = new Provider(Script.ok("a", 40, END), Script.ok("b", 40, RESET), Script.status("c", 503),
                Script.ok("d", 40, HANG));
        LiveHttp s = source(p, StreamGate.newSession());
        assertEquals(C.LENGTH_UNSET, s.open(spec()));
        // Three connections' bytes in a row, read as one stream: never an error the player would see.
        assertEquals(120 * 188, readBytes(s, 120 * 188));
        long[] r = StreamStats.take();
        assertEquals("the end of the stream", 1, r[StreamStats.EOF]);
        assertEquals("a reset", 1, r[StreamStats.RESET]);
        long most = r[StreamStats.NAMES.length + 1];
        System.out.println("RECONNECT the longest " + most + " ms, in all " + r[StreamStats.NAMES.length] + " ms");
        assertTrue("connected again within half a second, the 503 tried again 150 ms later: " + most, most < 500);
        // Never two connections at once, each closed before the next.
        assertEquals(1, p.most.get());
        assertEquals("[open a, close a, open b, close b, open c, close c, open d]", p.events.toString());
        // Each time the channel's own address.
        for (String u : p.urls) assertEquals(URL, u);
        s.close();
        assertEquals(0, p.open.get());
        assertEquals(0, StreamGate.open());
    }

    @Test public void aStreamWithNoDataForTooLongIsConnectedAgain() throws Exception {
        StreamGate.noData = 200;
        Provider p = new Provider(Script.ok("a", 20, HANG), Script.ok("b", 20, HANG));
        LiveHttp s = source(p, StreamGate.newSession());
        s.open(spec());
        long t0 = System.currentTimeMillis();
        assertEquals(40 * 188, readBytes(s, 40 * 188));
        long took = System.currentTimeMillis() - t0;
        assertEquals(1, StreamStats.take()[StreamStats.NO_DATA]);
        assertTrue("quiet 200 ms, then connected again: " + took, took < 2_000);
        assertEquals(1, p.most.get());
        s.close();
    }

    @Test public void theChannelBeforeIsClosedBeforeTheNextConnects() throws Exception {
        Provider p = new Provider(Script.ok("one", 10, HANG), Script.ok("two", 10, HANG));
        LiveHttp first = source(p, StreamGate.newSession());
        first.open(spec());
        // The first channel's loading thread waiting on a read (the provider sending nothing more).
        CountDownLatch reading = new CountDownLatch(1), done = new CountDownLatch(1);
        AtomicReference<Throwable> ended = new AtomicReference<>();
        Thread loader = new Thread(() -> {
            try {
                byte[] b = new byte[8192];
                long got = 0;
                while (got < 10 * 188) got += first.read(b, 0, b.length);
                reading.countDown();
                first.read(b, 0, b.length);
            } catch (Throwable t) {
                ended.set(t);
            } finally {
                // As Media3's loader does once a load ends.
                first.close();
                done.countDown();
            }
        });
        loader.start();
        assertTrue(reading.await(2, TimeUnit.SECONDS));
        Thread.sleep(50);
        // The next channel: the one before closed first, at once (its read ended), then this one connects.
        LiveHttp second = source(p, StreamGate.newSession());
        long t0 = System.currentTimeMillis();
        second.open(spec());
        long waited = System.currentTimeMillis() - t0;
        assertTrue(done.await(2, TimeUnit.SECONDS));
        assertTrue("the first one's read ended with an error for its (cancelled) load", ended.get() instanceof HttpDataSource.HttpDataSourceException);
        assertEquals("[open one, close one, open two]", p.events.toString());
        assertEquals("never two at once", 1, p.most.get());
        System.out.println("ONE STREAM the next channel waited " + waited + " ms for the one before to close");
        assertTrue("closed at once, not after a read timeout: " + waited, waited < 500);
        int[] c = StreamGate.take();
        assertEquals(1, c[0]);
        assertEquals(0, c[2]);
        second.close();
    }

    @Test public void aNewerStreamWinsOverAnOlderOneStillConnecting() throws Exception {
        Provider p = new Provider(Script.ok("new", 10, HANG));
        int older = StreamGate.newSession(), newer = StreamGate.newSession();
        LiveHttp now = source(p, newer);
        now.open(spec());
        try {
            source(p, older).open(spec());
            fail("the older stream gives way");
        } catch (HttpDataSource.HttpDataSourceException expected) {
            // its media source was let go already
        }
        assertEquals(1, p.connects.get());
        now.close();
    }

    @Test public void anHlsStreamWaitsForTheOneBeforeButNotForItsOwn() throws Exception {
        // An older stream's connection that can't be closed from here (Media3's own HTTP): waited for.
        StreamGate.Entry old = StreamGate.enter(StreamGate.newSession(), null);
        int session = StreamGate.newSession();
        StreamGate.Entry playlist = null;
        Thread leaves = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                // on
            }
            StreamGate.leave(old);
        });
        leaves.start();
        long t0 = System.currentTimeMillis();
        playlist = StreamGate.enter(session, null);
        long waited = System.currentTimeMillis() - t0;
        assertTrue("waited for the older one: " + waited, waited >= 150 && waited < StreamGate.WAIT_MS);
        // Its own segment alongside its playlist: no wait.
        t0 = System.currentTimeMillis();
        StreamGate.Entry segment = StreamGate.enter(session, null);
        assertTrue(System.currentTimeMillis() - t0 < 100);
        assertEquals(2, StreamGate.open());
        StreamGate.leave(segment);
        StreamGate.leave(playlist);
        int[] c = StreamGate.take();
        assertEquals(0, c[0]);
        assertEquals(0, c[2]);
        assertTrue(c[3] >= 150);
    }

    @Test public void aRefusalAsItFirstConnectsIsAskedAgain() throws Exception {
        // The launcher's tile (or the stream before) still holding the account's one connection for a moment.
        Provider p = new Provider(Script.status("held", 403), Script.ok("ok", 10, HANG));
        LiveHttp s = source(p, StreamGate.newSession());
        long t0 = System.currentTimeMillis();
        s.open(spec());
        long took = System.currentTimeMillis() - t0;
        assertEquals(10 * 188, readBytes(s, 10 * 188));
        assertEquals(1, StreamStats.take()[StreamStats.NAMES.length + 2]);
        assertTrue("asked again 300 ms later: " + took, took >= 250 && took < 1_000);
        s.close();

        // Refused for longer than FIRST_FOR_MS: the player is told (LiveTv's tries).
        LiveHttp.firstFor = 600;
        Provider no = new Provider(Script.status("held", 403));
        LiveHttp t = source(no, StreamGate.newSession());
        try {
            t.open(spec());
            fail("refused");
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            assertEquals(403, e.responseCode);
        }
        assertTrue(no.connects.get() >= 2);
        assertEquals(0, StreamGate.open());
    }

    @Test public void aLoginRefusedGivesUpAtOnceAndADeadStreamAfterItsTime() throws Exception {
        Provider p = new Provider(Script.ok("a", 10, END), Script.status("login", 401));
        LiveHttp s = source(p, StreamGate.newSession());
        s.open(spec());
        long t0 = System.currentTimeMillis();
        try {
            readBytes(s, 20 * 188);
            fail("refused");
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            assertEquals(401, e.responseCode);
        }
        assertTrue(System.currentTimeMillis() - t0 < 300);
        s.close();

        LiveHttp.againFor = 500;
        Provider dead = new Provider(Script.ok("a", 10, END), Script.status("busy", 503));
        LiveHttp d = source(dead, StreamGate.newSession());
        d.open(spec());
        t0 = System.currentTimeMillis();
        try {
            readBytes(d, 20 * 188);
            fail("gave up");
        } catch (HttpDataSource.InvalidResponseCodeException e) {
            assertEquals(503, e.responseCode);
        }
        long took = System.currentTimeMillis() - t0;
        assertTrue("tried again for its time: " + took, took >= 450 && took < 3_000);
        long[] r = StreamStats.take();
        assertEquals(2, r[StreamStats.NAMES.length + 3]);
        d.close();
    }

    @Test public void aRedirectIsFollowedAndTheChannelsOwnAddressAskedAgain() throws Exception {
        String lb = "https://edge.provider.invalid/hls/token-abc/1234.ts";
        Provider p = new Provider(new Script("to", 302, lb, new byte[0], END), Script.ok("edge", 10, END),
                new Script("to", 302, lb, new byte[0], END), Script.ok("edge", 10, HANG));
        LiveHttp s = source(p, StreamGate.newSession());
        s.open(spec());
        assertEquals(20 * 188, readBytes(s, 20 * 188));
        assertEquals(java.util.Arrays.asList(URL, lb, URL, lb), new ArrayList<>(p.urls));
        assertEquals(1, p.most.get());
        s.close();
    }

    @Test public void aPlaylistGivenAsMpegTsJustEnds() throws Exception {
        byte[] m3u = "#EXTM3U\n#EXTINF:-1,Kestrel One\nhttp://provider.invalid/1.m3u8\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Provider p = new Provider(new Script("text", 200, null, m3u, END));
        LiveHttp s = source(p, StreamGate.newSession());
        s.open(spec());
        byte[] b = new byte[8192];
        assertEquals(m3u.length, s.read(b, 0, b.length));
        assertEquals(C.RESULT_END_OF_INPUT, s.read(b, 0, b.length));
        assertEquals("not connected again", 1, p.connects.get());
        s.close();
    }

    @Test public void noErrorCarriesTheAddress() throws Exception {
        List<Throwable> seen = new ArrayList<>();
        Provider p = new Provider(Script.ok("a", 2, RESET), Script.status("login", 401));
        LiveHttp s = source(p, StreamGate.newSession());
        s.open(spec());
        try {
            readBytes(s, 10 * 188);
        } catch (IOException e) {
            seen.add(e);
        }
        s.close();
        LiveHttp bad = new LiveHttp.Factory("Homefield/1.0", null, StreamGate.newSession()).createDataSource();
        try {
            bad.open(new DataSpec(Uri.parse("notaurl://family:secret-pass@")));
        } catch (IOException e) {
            seen.add(e);
        }
        bad.close();
        assertFalse(seen.isEmpty());
        for (Throwable e : seen) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                String m = String.valueOf(t.getMessage());
                assertFalse(m, m.contains("secret") || m.contains("family") || m.contains("provider.invalid"));
            }
        }
    }

    @Test public void theCausesAndRatesAsTheLogWritesThem() {
        assertEquals(StreamStats.TIMEOUT, StreamStats.cause(new java.net.SocketTimeoutException()));
        assertEquals(StreamStats.RESET, StreamStats.cause(new SocketException("x")));
        assertEquals(StreamStats.EOF, StreamStats.cause(new java.io.EOFException()));
        assertEquals(StreamStats.EOF, StreamStats.cause(new java.net.ProtocolException("unexpected end of stream")));
        assertEquals(StreamStats.CONNECT, StreamStats.cause(new java.net.ConnectException()));
        HttpDataSource.InvalidResponseCodeException e503 = new HttpDataSource.InvalidResponseCodeException(503, null, null,
                new HashMap<>(), spec(), new byte[0]);
        assertEquals(StreamStats.HTTP, StreamStats.cause(e503));
        assertEquals(503, StreamStats.http(new IOException(e503)));
        assertEquals("http503", StreamStats.name(StreamStats.HTTP, 503));
        assertEquals("eof", StreamStats.name(StreamStats.EOF, 0));
        assertEquals("6.2Mbps", StreamStats.rate(6_200_000));
        assertEquals("850kbps", StreamStats.rate(850_000));
        assertTrue(LiveHttp.refusal(e503));
        assertTrue(LiveHttp.refusal(new HttpDataSource.InvalidResponseCodeException(403, null, null, new HashMap<>(), spec(), new byte[0])));
        assertFalse(LiveHttp.refusal(new HttpDataSource.InvalidResponseCodeException(404, null, null, new HashMap<>(), spec(), new byte[0])));
        assertFalse(LiveHttp.refusal(new java.net.UnknownHostException()));
        assertFalse(LiveHttp.worthAgain(new HttpDataSource.InvalidResponseCodeException(401, null, null, new HashMap<>(), spec(), new byte[0])));
        assertTrue(LiveHttp.worthAgain(e503));
        assertNotNull(spec());
    }

    @Test public void liveLoadsAreTriedAgainQuickly() {
        IOException drop = new SocketException("x");
        // MPEG-TS: LiveHttp tried for AGAIN_FOR_MS already, so the player is told at once (LiveTv's own tries).
        assertEquals(C.TIME_UNSET, Streams.LiveRetries.delay(true, drop, 1, 0));
        // HLS: 100, 300, 700 ms, then every 1.5 s (Media3's own: 0, 1, 2, 3, 4, 5 s).
        assertEquals(100, Streams.LiveRetries.delay(false, drop, 1, 0));
        assertEquals(300, Streams.LiveRetries.delay(false, drop, 2, 1000));
        assertEquals(700, Streams.LiveRetries.delay(false, drop, 3, 2000));
        assertEquals(1_500, Streams.LiveRetries.delay(false, drop, 7, 5000));
        // What Media3 would not try again (a playlist that isn't one) stays so.
        assertEquals(C.TIME_UNSET, Streams.LiveRetries.delay(false, new IOException(), 1, C.TIME_UNSET));
    }
}
