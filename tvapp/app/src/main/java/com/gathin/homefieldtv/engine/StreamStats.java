package com.gathin.homefieldtv.engine;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.HttpDataSource;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * What the stream connections did, for the play log (PlayDiag): the bytes that came (a second at a time, for the rate
 * just now and over the minute), each time a dropped live stream was connected again and why (the provider closed it,
 * the connection was reset, a read timed out, no data came, an HTTP status), refusals as a stream first connected, and
 * the drops that were given up on. Numbers and kinds only: never an address, a name or a message (they can hold the
 * login). Any thread.
 */
@OptIn(markerClass = UnstableApi.class)
public final class StreamStats {
    private StreamStats() {}

    /** Why a connection ended or failed. */
    public static final int EOF = 0, RESET = 1, TIMEOUT = 2, NO_DATA = 3, HTTP = 4, CONNECT = 5, OTHER = 6;
    public static final String[] NAMES = {"eof", "reset", "timeout", "nodata", "http", "connect", "other"};

    private static final Object LOCK = new Object();
    /** Since the play log last took them: drops connected again (by cause), the time that took, the longest. */
    private static final int[] again = new int[NAMES.length];
    private static long againMs, againMost;
    private static int refused, gaveUp, lastHttp;
    /** The last drop: when (elapsed ms), why, and its HTTP status (0: none); and when the last one was given up. */
    private static long lastDropAt = -1, lastGaveUpAt = -1;
    private static int lastCause = -1;
    /** Bytes a second, the last SECONDS of them (ring by second), and all of them. */
    static final int SECONDS = 16;
    private static final long[] perSecond = new long[SECONDS];
    private static long second = -1, total;

    static long now() {
        return StreamGate.now();
    }

    // ---- what the connections tell ----------------------------------------------------------------------------

    /** n bytes came. */
    public static void bytes(int n) {
        long s = now() / 1000L;
        synchronized (LOCK) {
            roll(s);
            perSecond[(int) (s % SECONDS)] += n;
            total += n;
        }
    }

    private static void roll(long s) {
        if (second < 0 || s - second >= SECONDS) {
            java.util.Arrays.fill(perSecond, 0);
        } else {
            for (long k = second + 1; k <= s; k++) perSecond[(int) (k % SECONDS)] = 0;
        }
        if (s > second) second = s;
    }

    /** A dropped live stream connected again, after ms. */
    public static void reconnected(int cause, int http, long ms) {
        synchronized (LOCK) {
            again[clamp(cause)]++;
            againMs += ms;
            againMost = Math.max(againMost, ms);
            note(cause, http);
        }
    }

    /** A live stream dropped (noted at once, before it is connected again). */
    public static void dropped(int cause, int http) {
        synchronized (LOCK) {
            note(cause, http);
        }
    }

    private static void note(int cause, int http) {
        lastDropAt = now();
        lastCause = clamp(cause);
        if (http > 0) lastHttp = http;
    }

    /** A stream refused as it first connected (tried again a moment later). */
    public static void refused(int http) {
        synchronized (LOCK) {
            refused++;
            if (http > 0) lastHttp = http;
        }
    }

    /** A dropped live stream that couldn't be connected again: the player is told. */
    public static void gaveUp(int cause, int http) {
        synchronized (LOCK) {
            gaveUp++;
            lastGaveUpAt = now();
            note(cause, http);
        }
    }

    private static int clamp(int cause) {
        return cause < 0 || cause >= NAMES.length ? OTHER : cause;
    }

    // ---- what the play log reads -----------------------------------------------------------------------------

    /** All the bytes that have come. */
    public static long total() {
        synchronized (LOCK) {
            return total;
        }
    }

    /** The rate over the last seconds whole seconds (bits a second; the second going on left out). */
    public static long bitsPerSecond(int seconds) {
        long s = now() / 1000L;
        int n = Math.max(1, Math.min(SECONDS - 1, seconds));
        synchronized (LOCK) {
            roll(s);
            long sum = 0;
            for (int k = 1; k <= n; k++) sum += perSecond[(int) ((s - k) % SECONDS)];
            return sum * 8 / n;
        }
    }

    /** When the last drop was (elapsed ms, -1: none), its cause and HTTP status, and when one was last given up on. */
    public static long[] lastDrop() {
        synchronized (LOCK) {
            return new long[]{lastDropAt, lastCause, lastHttp, lastGaveUpAt};
        }
    }

    /**
     * Since the last take: {drops connected again by cause (NAMES' order)…, the time that took in all (ms), the longest
     * (ms), refusals as a stream connected, drops given up on, the last HTTP status}; the counts start again.
     */
    public static long[] take() {
        synchronized (LOCK) {
            long[] out = new long[NAMES.length + 5];
            for (int i = 0; i < NAMES.length; i++) out[i] = again[i];
            out[NAMES.length] = againMs;
            out[NAMES.length + 1] = againMost;
            out[NAMES.length + 2] = refused;
            out[NAMES.length + 3] = gaveUp;
            out[NAMES.length + 4] = lastHttp;
            java.util.Arrays.fill(again, 0);
            againMs = againMost = 0;
            refused = gaveUp = lastHttp = 0;
            return out;
        }
    }

    /** Tests: from nothing. */
    static void reset() {
        synchronized (LOCK) {
            java.util.Arrays.fill(again, 0);
            java.util.Arrays.fill(perSecond, 0);
            againMs = againMost = 0;
            refused = gaveUp = lastHttp = 0;
            lastDropAt = lastGaveUpAt = -1;
            lastCause = -1;
            second = -1;
            total = 0;
        }
    }

    // ---- pure -------------------------------------------------------------------------------------------------

    /** Pure: why a connection failed, by the kind of the error alone (never its message). */
    public static int cause(IOException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) return HTTP;
            if (t instanceof SocketTimeoutException) return TIMEOUT;
            if (t instanceof EOFException) return EOF;
            if (t instanceof ConnectException || t instanceof UnknownHostException) return CONNECT;
            if (t instanceof SocketException) return RESET;
            if (t instanceof InterruptedIOException) return OTHER;
            // OkHttp's "unexpected end of stream" (a body cut short) is a ProtocolException.
            if (t instanceof java.net.ProtocolException) return EOF;
            if (t == t.getCause()) break;
        }
        return OTHER;
    }

    /** Pure: the HTTP status in an error (0: none). */
    public static int http(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpDataSource.InvalidResponseCodeException) return ((HttpDataSource.InvalidResponseCodeException) t).responseCode;
            if (t == t.getCause()) break;
        }
        return 0;
    }

    /** Pure: a cause's name with its status ("http503", "eof"). */
    public static String name(int cause, int http) {
        String n = NAMES[clamp(cause)];
        return cause == HTTP && http > 0 ? n + http : n;
    }

    /** Pure: bits a second as the log writes them ("6.2Mbps", "850kbps"). */
    public static String rate(long bps) {
        if (bps >= 1_000_000L) return String.format(Locale.ROOT, "%.1fMbps", bps / 1e6);
        return (bps / 1000L) + "kbps";
    }
}
