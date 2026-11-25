package com.gathin.homefieldtv.engine;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A live channel's MPEG-TS stream over HTTP that rides out a dropped connection itself. When the provider closes it (the
 * end of the stream), the connection is reset, a read times out, no data comes for StreamGate.NO_DATA_MS, or the server
 * answers 5xx for a moment, it is connected again within a few hundred milliseconds (at once, then 150, 300, 600 ms...)
 * and the bytes carry on where they left off for the player, which plays on from what it has kept meanwhile: no error,
 * no "Reconnecting…", no spinner. The MPEG-TS reader finds its place again in the new bytes. Only when it can't be
 * connected again for AGAIN_FOR_MS, or drops again and again, is the player told (and LiveTv's own tries take over).
 *
 * <p>One stream at a time (StreamGate): the channel before is closed before this one connects, and a refusal as it first
 * connects (the account's one connection still held a moment by the stream before, the launcher's tile, another app) is
 * tried again for FIRST_FOR_MS. Redirects are followed across http and https; each time it connects again it asks the
 * channel's own address (a load balancer's redirect may carry a token that runs out). Its loading thread is given a
 * little more of the CPU than the screens. The address is never logged or put in an error: it holds the login.
 */
@OptIn(markerClass = UnstableApi.class)
public final class LiveHttp extends BaseDataSource implements StreamGate.Closable {
    /** Connecting: this long at most; a first read may wait longer (a provider starting a channel on demand). */
    public static final int CONNECT_MS = 6_000, FIRST_READ_MS = 15_000;
    /** The waits before each try again after a drop: at once, then a little longer each time, 2 s at most. */
    static final long[] AGAIN_MS = {0, 150, 300, 600, 1_000, 1_500, 2_000};
    /** A drop is tried again for this long before the player is told (it plays on from its buffer meanwhile). */
    public static final long AGAIN_FOR_MS = 12_000L;
    /** Dropping this many times within DROPS_MS is a stream that won't stay up: the player is told (another copy). */
    static final int DROPS = 8;
    static final long DROPS_MS = 60_000L;
    /** A refusal as it first connects is tried again this long, these waits apart. */
    public static final long FIRST_FOR_MS = 4_000L;
    static final long[] FIRST_AGAIN_MS = {300, 500, 700, 1_000};
    /** AGAIN_FOR_MS and FIRST_FOR_MS as used (the tests make them short). */
    static volatile long againFor = AGAIN_FOR_MS, firstFor = FIRST_FOR_MS;
    /** The end of a stream is only a drop for a stream: MPEG-TS (its first byte the sync byte) or one past this size. */
    static final long STREAM_BYTES = 64 * 1024;
    static final int REDIRECTS = 10;

    /** One HTTP exchange (HttpURLConnection on the TV; the tests' own fakes). */
    public interface Conn {
        int status() throws IOException;

        @Nullable String location();

        InputStream body() throws IOException;

        Map<String, List<String>> headers();

        /** Closed, from any thread: a read waiting on it ends. */
        void disconnect();
    }

    /** How a connection is made. */
    public interface Opener {
        Conn open(String url, Map<String, String> headers, int connectMs, int readMs) throws IOException;
    }

    /** The TV's own: HttpURLConnection, redirects left to LiveHttp (it follows them across http and https). */
    static final Opener HTTP = (url, headers, connectMs, readMs) -> {
        HttpURLConnection h;
        try {
            h = (HttpURLConnection) new URL(url).openConnection();
        } catch (IllegalArgumentException | java.net.MalformedURLException e) {
            // Never the address: it holds the login.
            throw new IOException("not an address");
        }
        h.setConnectTimeout(connectMs);
        h.setReadTimeout(readMs);
        h.setInstanceFollowRedirects(false);
        h.setUseCaches(false);
        h.setRequestProperty("Accept-Encoding", "identity");
        for (Map.Entry<String, String> e : headers.entrySet()) h.setRequestProperty(e.getKey(), e.getValue());
        return new Conn() {
            @Override public int status() throws IOException {
                return h.getResponseCode();
            }

            @Override public String location() {
                return h.getHeaderField("Location");
            }

            @Override public InputStream body() throws IOException {
                return h.getInputStream();
            }

            @Override public Map<String, List<String>> headers() {
                // As Media3's own HTTP gives them: by name whatever its case, without the status line's empty name.
                Map<String, List<String>> m = h.getHeaderFields(), out = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                if (m != null) for (Map.Entry<String, List<String>> e : m.entrySet()) if (e.getKey() != null) out.put(e.getKey(), e.getValue());
                return out;
            }

            @Override public void disconnect() {
                try {
                    h.disconnect();
                } catch (RuntimeException ignored) {
                    // gone already
                }
            }
        };
    };

    /** Makes one for each stream (all of a stream's share its session in StreamGate). */
    public static final class Factory implements DataSource.Factory {
        final Map<String, String> headers;
        final int session;
        Opener opener = HTTP;

        public Factory(String userAgent, @Nullable String referrer, int session) {
            Map<String, String> h = new HashMap<>();
            h.put("User-Agent", userAgent);
            if (referrer != null && !referrer.isEmpty()) h.put("Referer", referrer);
            this.headers = Collections.unmodifiableMap(h);
            this.session = session;
        }

        /** Tests: their own connections. */
        Factory opener(Opener o) {
            opener = o;
            return this;
        }

        @NonNull @Override public LiveHttp createDataSource() {
            return new LiveHttp(this);
        }
    }

    private final Map<String, String> headers;
    private final int session;
    private final Opener opener;
    private final Object nap = new Object();
    private DataSpec spec;
    private String url;
    private StreamGate.Entry entry;
    private volatile Conn conn;
    private InputStream in;
    private boolean opened;
    /** Closed for good (another stream, the player let go); told it went quiet (no data for NO_DATA_MS). */
    private volatile boolean killed, quietTold;
    /** When the read waiting now began (ms; 0: not reading), and whether this connection has had its first data. */
    private volatile long readingSince;
    private volatile boolean flowing;
    /** This connection's bytes so far, and whether its first byte was MPEG-TS's sync byte. */
    private long got;
    private boolean ts;
    /** The drops lately (ms, oldest first), for DROPS within DROPS_MS. */
    private final long[] drops = new long[DROPS];
    private int dropN;

    LiveHttp(Factory f) {
        super(true);
        headers = f.headers;
        session = f.session;
        opener = f.opener;
    }

    static long now() {
        return StreamGate.now();
    }

    @Override public long open(@NonNull DataSpec dataSpec) throws HttpDataSource.HttpDataSourceException {
        spec = dataSpec;
        url = dataSpec.uri.toString();
        killed = false;
        quietTold = false;
        transferInitializing(dataSpec);
        favour();
        try {
            entry = StreamGate.enter(session, this);
        } catch (InterruptedException e) {
            // Its load cancelled, or a newer stream has the connection (its media source let go already).
            if (!(e instanceof StreamGate.Superseded)) Thread.currentThread().interrupt();
            throw failure(new InterruptedIOException(), HttpDataSource.HttpDataSourceException.TYPE_OPEN);
        }
        long start = now();
        int tries = 0;
        while (true) {
            try {
                connect();
                break;
            } catch (IOException e) {
                if (killed || !refusal(e) || now() - start >= firstFor) {
                    dropQuietly();
                    StreamGate.leave(entry);
                    entry = null;
                    throw failure(e, HttpDataSource.HttpDataSourceException.TYPE_OPEN);
                }
                StreamStats.refused(StreamStats.http(e));
                if (!pause(FIRST_AGAIN_MS[Math.min(tries++, FIRST_AGAIN_MS.length - 1)])) {
                    StreamGate.leave(entry);
                    entry = null;
                    throw failure(new InterruptedIOException(), HttpDataSource.HttpDataSourceException.TYPE_OPEN);
                }
            }
        }
        opened = true;
        transferStarted(dataSpec);
        // Live: no length, and never a range asked for (the stream is wherever the provider is now).
        return C.LENGTH_UNSET;
    }

    /**
     * Pure: whether a first connection's failure is a refusal worth asking again a moment later: the account's
     * connection still held (403, 406, 423, 429, 458, 509), the server busy (5xx), the connection reset or refused.
     */
    static boolean refusal(IOException e) {
        int code = StreamStats.http(e);
        if (code > 0) return code == 403 || code == 406 || code == 423 || code == 429 || code == 458 || code >= 500;
        // Not a refusal: no such server (the network or the address), or one that didn't answer in time.
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.UnknownHostException || t instanceof java.net.SocketTimeoutException) return false;
            if (t == t.getCause()) break;
        }
        return !(e instanceof InterruptedIOException) && !"not an address".equals(e.getMessage());
    }

    /** Pure: whether a drop is worth connecting again (not a login refused or a channel gone for good: 400, 401, 410). */
    static boolean worthAgain(IOException e) {
        int code = StreamStats.http(e);
        return code != 400 && code != 401 && code != 410;
    }

    /** Connected to the channel's own address, its redirects followed: the body ready to read. */
    private void connect() throws IOException {
        String u = url;
        for (int hop = 0; ; hop++) {
            Conn c = opener.open(u, headers, CONNECT_MS, FIRST_READ_MS);
            conn = c;
            if (killed) {
                c.disconnect();
                throw new InterruptedIOException();
            }
            int code = c.status();
            if (code >= 300 && code < 400 && code != 304) {
                String to = c.location();
                c.disconnect();
                if (to == null || to.isEmpty() || hop >= REDIRECTS) throw new java.net.ProtocolException("redirect");
                u = resolve(u, to);
                continue;
            }
            if (code < 200 || code > 299) {
                Map<String, List<String>> h = c.headers();
                c.disconnect();
                throw new HttpDataSource.InvalidResponseCodeException(code, null, null, h, spec, new byte[0]);
            }
            in = c.body();
            // This connection's bytes from nothing (whether the stream is MPEG-TS is kept from its first connection).
            got = 0;
            flowing = false;
            return;
        }
    }

    /** Pure: a redirect's address against the one it came from. */
    static String resolve(String from, String to) throws IOException {
        try {
            return new URL(new URL(from), to).toString();
        } catch (java.net.MalformedURLException e) {
            throw new java.net.ProtocolException("redirect");
        }
    }

    @Override public int read(@NonNull byte[] buffer, int offset, int length) throws HttpDataSource.HttpDataSourceException {
        if (length == 0) return 0;
        while (true) {
            if (killed) throw failure(new InterruptedIOException(), HttpDataSource.HttpDataSourceException.TYPE_READ);
            InputStream s = in;
            IOException failed = null;
            int n;
            readingSince = now();
            try {
                n = s == null ? -1 : s.read(buffer, offset, length);
            } catch (IOException e) {
                n = -1;
                failed = e;
            } finally {
                readingSince = 0;
            }
            if (n > 0) {
                if (got == 0 && buffer[offset] == 0x47) ts = true;
                got += n;
                flowing = true;
                StreamStats.bytes(n);
                bytesTransferred(n);
                return n;
            }
            if (n == 0) continue;
            if (killed) throw failure(new InterruptedIOException(), HttpDataSource.HttpDataSourceException.TYPE_READ);
            // The end of something that has never been a stream (a playlist given as MPEG-TS, a short reply): its end.
            // A connection closed before its first byte is the provider refusing it (connected again, as a drop).
            if (failed == null && !quietTold && !ts && got > 0 && got < STREAM_BYTES) return C.RESULT_END_OF_INPUT;
            int cause = quietTold ? StreamStats.NO_DATA : failed == null ? StreamStats.EOF : StreamStats.cause(failed);
            again(cause, StreamStats.http(failed));
        }
    }

    /** Dropped: connected again at once, then a little later each time; told to the player only when it can't be. */
    private void again(int cause, int http) throws HttpDataSource.HttpDataSourceException {
        dropQuietly();
        StreamStats.dropped(cause, http);
        long start = now();
        System.arraycopy(drops, 1, drops, 0, drops.length - 1);
        drops[drops.length - 1] = start;
        dropN = Math.min(drops.length, dropN + 1);
        if (dropN == drops.length && start - drops[0] < DROPS_MS) {
            StreamStats.gaveUp(cause, http);
            throw failure(new java.net.SocketException("dropped again and again"), HttpDataSource.HttpDataSourceException.TYPE_READ);
        }
        int tries = 0;
        while (true) {
            long wait = AGAIN_MS[Math.min(tries, AGAIN_MS.length - 1)];
            if (wait > 0 && !pause(wait) || killed) {
                throw failure(new InterruptedIOException(), HttpDataSource.HttpDataSourceException.TYPE_READ);
            }
            tries++;
            try {
                quietTold = false;
                connect();
                StreamStats.reconnected(cause, http, now() - start);
                return;
            } catch (IOException e) {
                dropQuietly();
                if (killed) throw failure(new InterruptedIOException(), HttpDataSource.HttpDataSourceException.TYPE_READ);
                if (!worthAgain(e) || now() - start >= againFor) {
                    StreamStats.gaveUp(StreamStats.cause(e), StreamStats.http(e));
                    throw failure(e, HttpDataSource.HttpDataSourceException.TYPE_READ);
                }
            }
        }
    }

    /** Waits ms (woken early when killed); false when the thread was interrupted or it was killed. */
    private boolean pause(long ms) {
        long until = now() + ms;
        synchronized (nap) {
            while (!killed) {
                long left = until - now();
                if (left <= 0) return true;
                try {
                    nap.wait(left);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /** As the player wants it: an HTTP status as it is (LiveTv reads it), anything else as a network failure. */
    private HttpDataSource.HttpDataSourceException failure(IOException e, int type) {
        if (e instanceof HttpDataSource.HttpDataSourceException) return (HttpDataSource.HttpDataSourceException) e;
        return HttpDataSource.HttpDataSourceException.createForIOException(e, spec, type);
    }

    private void dropQuietly() {
        Conn c = conn;
        conn = null;
        InputStream s = in;
        in = null;
        if (c != null) c.disconnect();
        if (s != null) {
            try {
                s.close();
            } catch (IOException | RuntimeException ignored) {
                // closed already
            }
        }
    }

    @Override public void close() {
        boolean was = opened;
        opened = false;
        dropQuietly();
        StreamGate.leave(entry);
        entry = null;
        if (was) transferEnded();
    }

    @Nullable @Override public Uri getUri() {
        return spec == null ? null : spec.uri;
    }

    @NonNull @Override public Map<String, List<String>> getResponseHeaders() {
        Conn c = conn;
        return c == null ? Collections.emptyMap() : c.headers();
    }

    // ---- StreamGate ---------------------------------------------------------------------------------------------

    @Override public void kill() {
        killed = true;
        Conn c = conn;
        if (c != null) c.disconnect();
        synchronized (nap) {
            nap.notifyAll();
        }
    }

    @Override public long quietMs(long now) {
        long r = readingSince;
        return r == 0 || !flowing ? 0 : Math.max(0, now - r);
    }

    @Override public void quiet() {
        if (quietTold || killed) return;
        quietTold = true;
        Conn c = conn;
        if (c != null) c.disconnect();
    }

    // ---- the loading thread ---------------------------------------------------------------------------------------

    /**
     * The thread reading the stream given a little more of the CPU than the screens' own (Android's "display" priority,
     * under the player's sound): on a slow TV a screen drawing took its time from it.
     */
    static void favour() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DISPLAY);
        } catch (Throwable ignored) {
            // as it is
        }
    }
}
