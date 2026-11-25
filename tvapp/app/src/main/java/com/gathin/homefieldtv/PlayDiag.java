package com.gathin.homefieldtv;

import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;

import com.gathin.homefieldtv.engine.StreamGate;
import com.gathin.homefieldtv.engine.StreamStats;
import com.gathin.homefieldtv.prefs.PlayerSetup;

import java.io.IOException;
import java.util.Locale;

/**
 * The play log: how the live picture is doing, written to the memory log (MemLog) so the family's TV can be read after
 * the evening without the remote. Once a minute while a channel plays, a line of the minute:
 *
 * <pre>
 *   2026-11-25 20:37 play type=TS state=playing stalls=1/3.4s channel=4/9.8s start=1.2s reconnects=2/0.4s(eof:1,reset:1)
 *   refused=0 gaveup=0 errors=0 rate=6.2Mbps est=- buffer=12.3s/9.8MB conns=1/1 overlap=0 waited=0ms steps=0 retries=0
 *   behind=0 dropped=12 underruns=0
 * </pre>
 *
 * and a line for each stall as it ends (the picture stopped while it should have played):
 *
 * <pre>
 *   2026-11-25 20:37 stall 3.4s cause=reconnect:eof http=- quiet=2300ms rate=4.1Mbps est=- buffer=0.2s/1.1MB
 *   gc=1/45ms blocking=0 main=120ms type=TS conns=1
 * </pre>
 *
 * type: the stream as played (TS, HLS, HLS-for-TS: the provider's HLS form of a TS channel; archive-TS, archive-HLS);
 * stalls this minute (how many, how long in all), and since the channel was put on; start: how long the last start took
 * to its picture; reconnects: drops of the live connection that LiveHttp connected again itself (how long that took in
 * all, by cause), refused: a stream refused as it first connected (tried again), gaveup: drops it couldn't, errors: the
 * player's; rate: what the network gave over the minute, est: Media3's own estimate (HLS); buffer: what the player kept
 * ahead at the minute, in seconds and bytes; conns: stream connections open now and the most at once in the minute,
 * overlap: connections that opened while another stream's was still open, waited: the longest a stream waited for the
 * one before to close; steps between the channel's versions, retries (LiveTv's), behind (put back to live from behind
 * an HLS stream's window), frames dropped, the sound running dry. A stall's cause: error:… (the player's), gaveup:… and
 * reconnect:… (the live connection dropped: eof, reset, timeout, nodata, http503…), load:… (a load failed and was tried
 * again), decoder, nodata (the network sent nothing for a second or more), else slow (data came, too slowly); quiet:
 * the longest the network sent nothing meanwhile; gc: collections during it and their time, blocking: those that held
 * every thread up; main: the longest the main thread was held up. Numbers, kinds and codes only: never an address, a
 * name, a login or a message. Main thread.
 */
@OptIn(markerClass = UnstableApi.class)
final class PlayDiag implements AnalyticsListener {
    /** Where the lines go (MemLog's file; the tests' own). */
    interface Sink {
        void line(String line);
    }

    /** What the player says now: whether it should be playing, and its buffer ahead (ms). */
    interface Now {
        boolean wants();

        long bufferedMs();
    }

    /** At most this many stall lines a minute (the rest counted in the minute's line). */
    static final int STALL_LINES = 12;
    /** A drop or a failed load this soon before a stall is taken as its cause. */
    static final long NEAR_MS = 3_000L;
    /** How often the main thread is looked at during a stall, and for how long at most. */
    static final long PROBE_MS = 100L, PROBE_FOR_MS = 60_000L;

    private final Sink sink;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Now now;
    private String type = "-";
    private boolean starting, ready, stalling;
    private long startedAt, startMs = -1;
    /** This minute. */
    private int stalls, stallLines, moreStalls, errors, steps, retries, behind, underruns;
    private long stallMs, dropped, lastTotal = -1, lastMinuteAt;
    /** Since the channel was put on. */
    private int chStalls;
    private long chStallMs;
    /** The stall going on: when, and what was so then. */
    private long stallAt, quietMax, lagMax, lagNext, bufMsAt, bufBytesAt;
    private long[] gcAt;
    private String errorIn;
    private boolean decoderIn;
    private int loadCause = -1, loadHttp;
    private long loadAt = -1, estimate = -1;

    PlayDiag(Sink sink) {
        this.sink = sink;
    }

    void attach(Now n) {
        now = n;
    }

    /** The streams' own clock (ms), so a stall's start and a connection's drop (StreamStats) are on the same one. */
    static long clock() {
        return StreamGate.now();
    }

    // ---- what LiveTv tells -------------------------------------------------------------------------------------

    /** A stream about to be prepared (a channel, a version, the archive, a try again): what it is. */
    void starting(String kind) {
        type = kind;
        // A try again during a stall is part of the stall: it ends when the picture is back.
        if (!stalling) {
            starting = true;
            startedAt = clock();
        }
    }

    /** Another channel put on: its counts from nothing (a stall going on cut short). */
    void channel() {
        if (stalling) endStall("left");
        chStalls = 0;
        chStallMs = 0;
    }

    void step() {
        steps++;
    }

    void retry() {
        retries++;
    }

    void behind() {
        behind++;
    }

    /** The stream let go of. */
    void stopped() {
        if (stalling) endStall("stopped");
        ready = false;
        starting = false;
        type = "-";
    }

    // ---- what the player tells (AnalyticsListener, main thread) ----------------------------------------------

    @Override public void onPlaybackStateChanged(@NonNull EventTime t, int state) {
        state(state);
    }

    void state(int state) {
        if (state == Player.STATE_READY) {
            pendingError = null;
            if (stalling) endStall(null);
            if (starting) {
                startMs = clock() - startedAt;
                starting = false;
            }
            ready = true;
        } else if (state == Player.STATE_BUFFERING) {
            if (ready && !starting && !stalling && (now == null || now.wants())) beginStall();
        } else if (ready && !stalling) {
            // The player stopped by an error (or the stream's end) while it played: a stall, which goes on through
            // LiveTv's tries until the picture is back (or the channel is changed, or it is let go).
            beginStall();
        }
    }

    @Override public void onLoadError(@NonNull EventTime t, @NonNull LoadEventInfo info, @NonNull MediaLoadData data,
                                      @NonNull IOException error, boolean wasCanceled) {
        if (wasCanceled) return;
        loadError(error);
    }

    void loadError(IOException error) {
        loadCause = StreamStats.cause(error);
        loadHttp = StreamStats.http(error);
        loadAt = clock();
    }

    @Override public void onPlayerError(@NonNull EventTime t, @NonNull PlaybackException e) {
        playerError(e.getErrorCodeName());
    }

    void playerError(String codeName) {
        errors++;
        // ERROR_CODE_BEHIND_LIVE_WINDOW → behind-live-window: the code's name, never the error's message.
        String n = codeName == null ? "unknown" : codeName.replace("ERROR_CODE_", "").toLowerCase(Locale.ROOT).replace('_', '-');
        lastError = n;
        // The first error of a stall is its cause (the player tells it just before it stops, starting the stall).
        if (!stalling) pendingError = n;
        else if (errorIn == null) errorIn = n;
    }

    private String lastError = "", pendingError;

    @Override public void onBandwidthEstimate(@NonNull EventTime t, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        estimate = bitrateEstimate;
    }

    @Override public void onDroppedVideoFrames(@NonNull EventTime t, int count, long elapsedMs) {
        dropped += count;
    }

    @Override public void onAudioUnderrun(@NonNull EventTime t, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
        underruns++;
    }

    @Override public void onVideoCodecError(@NonNull EventTime t, @NonNull Exception e) {
        decoder();
    }

    @Override public void onAudioCodecError(@NonNull EventTime t, @NonNull Exception e) {
        decoder();
    }

    @Override public void onAudioSinkError(@NonNull EventTime t, @NonNull Exception e) {
        decoder();
    }

    void decoder() {
        if (stalling) decoderIn = true;
    }

    // ---- a stall -----------------------------------------------------------------------------------------------

    private final Runnable probe = new Runnable() {
        @Override public void run() {
            if (!stalling) return;
            long t = SystemClock.uptimeMillis();
            if (lagNext > 0) lagMax = Math.max(lagMax, t - lagNext);
            quietMax = Math.max(quietMax, StreamGate.quietMs());
            lagNext = t + PROBE_MS;
            // Its first minute only (a channel that has failed waits for the family, and the main thread is left be).
            if (clock() - stallAt < PROBE_FOR_MS) h.postDelayed(this, PROBE_MS);
        }
    };

    private void beginStall() {
        stalling = true;
        stallAt = clock();
        quietMax = StreamGate.quietMs();
        lagMax = 0;
        lagNext = 0;
        bufMsAt = now == null ? 0 : now.bufferedMs();
        bufBytesAt = PlayerSetup.liveBufferBytes();
        gcAt = gc();
        errorIn = pendingError;
        pendingError = null;
        decoderIn = false;
        h.removeCallbacks(probe);
        h.post(probe);
    }

    /** The stall over (how: null as the picture came back; else what ended it). */
    private void endStall(String how) {
        stalling = false;
        h.removeCallbacks(probe);
        long ms = Math.max(0, clock() - stallAt);
        stalls++;
        stallMs += ms;
        chStalls++;
        chStallMs += ms;
        long[] g = gc(), d = StreamStats.lastDrop();
        String cause = cause(how, errorIn, d, stallAt, loadAt, loadCause, loadHttp, decoderIn, quietMax);
        int http = cause.startsWith("reconnect:") || cause.startsWith("gaveup:") ? (int) d[2] : loadAt >= stallAt - NEAR_MS ? loadHttp : 0;
        if (stallLines >= STALL_LINES) {
            moreStalls++;
            return;
        }
        stallLines++;
        sink.line(String.format(Locale.ROOT, "stall %.1fs cause=%s http=%s quiet=%dms rate=%s est=%s buffer=%.1fs/%.1fMB gc=%d/%dms blocking=%d main=%dms type=%s conns=%d",
                ms / 1000.0, cause, http > 0 ? String.valueOf(http) : "-", quietMax, StreamStats.rate(StreamStats.bitsPerSecond(5)),
                estimate > 0 ? StreamStats.rate(estimate) : "-", bufMsAt / 1000.0, bufBytesAt / 1048576.0, Math.max(0, g[0] - gcAt[0]),
                Math.max(0, g[1] - gcAt[1]), Math.max(0, g[2] - gcAt[2]), lagMax, type, StreamGate.open()));
    }

    /**
     * Pure: a stall's cause. how: what ended it (null: the picture came back); errorIn: the player's error during it;
     * drop: StreamStats.lastDrop(); then a failed load, the decoders, the network going quiet, else slow.
     */
    static String cause(String how, String errorIn, long[] drop, long stallAt, long loadAt, int loadCause, int loadHttp,
                        boolean decoderIn, long quietMax) {
        if (errorIn != null) return "error:" + errorIn;
        if (drop[3] >= 0 && drop[3] >= stallAt - NEAR_MS) return "gaveup:" + StreamStats.name((int) drop[1], (int) drop[2]);
        if (drop[0] >= 0 && drop[0] >= stallAt - NEAR_MS) return "reconnect:" + StreamStats.name((int) drop[1], (int) drop[2]);
        if (loadAt >= 0 && loadAt >= stallAt - NEAR_MS) return "load:" + StreamStats.name(loadCause, loadHttp);
        if (decoderIn) return "decoder";
        if (quietMax >= 1000) return "nodata";
        if (how != null) return how;
        return "slow";
    }

    /** ART's collections so far: {how many, their time (ms), those that held every thread up}; 0 where not known. */
    static long[] gc() {
        return new long[]{stat("art.gc.gc-count"), stat("art.gc.gc-time"), stat("art.gc.blocking-gc-count")};
    }

    private static long stat(String name) {
        try {
            String v = Debug.getRuntimeStat(name);
            return v == null ? 0 : Long.parseLong(v);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ---- the minute's line -------------------------------------------------------------------------------------

    /** The minute's line (null when nothing played and nothing is open); the minute's counts start again. */
    String minute(String state, long bufferedMs) {
        long t = clock(), total = StreamStats.total();
        long secs = lastTotal < 0 ? 60 : Math.max(1, (t - lastMinuteAt) / 1000);
        long rate = lastTotal < 0 ? StreamStats.bitsPerSecond(15) : (total - lastTotal) * 8 / secs;
        lastTotal = total;
        lastMinuteAt = t;
        long[] r = StreamStats.take();
        int[] c = StreamGate.take();
        int n = StreamStats.NAMES.length;
        long reconnects = 0;
        StringBuilder by = new StringBuilder();
        for (int i = 0; i < n; i++) {
            reconnects += r[i];
            if (r[i] == 0) continue;
            by.append(by.length() == 0 ? "(" : ",").append(StreamStats.NAMES[i]).append(':').append(r[i]);
        }
        if (by.length() > 0) by.append(')');
        boolean idle = "-".equals(type) && stalls == 0 && errors == 0 && reconnects == 0 && c[1] == 0;
        String line = idle ? null : String.format(Locale.ROOT,
                "play type=%s state=%s stalls=%d/%.1fs channel=%d/%.1fs start=%s reconnects=%d/%.1fs%s refused=%d gaveup=%d errors=%d%s "
                        + "rate=%s est=%s buffer=%.1fs/%.1fMB conns=%d/%d overlap=%d waited=%dms steps=%d retries=%d behind=%d dropped=%d underruns=%d%s",
                type, state, stalls, stallMs / 1000.0, chStalls, chStallMs / 1000.0, startMs < 0 ? "-" : String.format(Locale.ROOT, "%.1fs", startMs / 1000.0),
                reconnects, r[n] / 1000.0, by, r[n + 2], r[n + 3], errors, lastError.isEmpty() ? "" : "(" + lastError + ")",
                StreamStats.rate(rate), estimate > 0 ? StreamStats.rate(estimate) : "-", bufferedMs / 1000.0, PlayerSetup.liveBufferBytes() / 1048576.0,
                c[0], c[1], c[2], c[3], steps, retries, behind, dropped, underruns, moreStalls > 0 ? " more=" + moreStalls : "");
        stalls = stallLines = moreStalls = errors = steps = retries = behind = underruns = 0;
        stallMs = dropped = 0;
        lastError = "";
        return line;
    }
}
