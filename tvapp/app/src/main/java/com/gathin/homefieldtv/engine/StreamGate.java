package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One stream at a time, for the whole app. A provider's account usually allows one connection, and a second one makes it
 * cut the first (on the TV that is a stall) or refuse the new one. So every stream connection the players open (live
 * MPEG-TS, an HLS stream's playlists and segments, the archive, a film) comes through here first: a stream (a session,
 * one per media source) about to connect has every older stream's connection closed before it opens, waited for at most
 * WAIT_MS, and one that can be closed from another thread (LiveHttp's) is closed at once; an older stream still
 * connecting once a newer one has the connection gives way. One stream's own connections (an HLS playlist and its
 * segment) don't wait for each other.
 *
 * <p>It also keeps the counts the play log writes (how many connections are open now, the most at once), and watches the
 * live connections that are reading: one that has had no data for NO_DATA_MS is told (LiveHttp connects it again). The
 * stream's address is never kept here: only whether two connections are the same stream's.
 */
public final class StreamGate {
    private StreamGate() {}

    /** The longest a new stream waits for the one before to close (it is told to close at once where it can be). */
    public static final long WAIT_MS = 1_500L;
    /** A live connection reading with no data this long is taken as dead (LiveHttp connects it again). */
    public static final long NO_DATA_MS = 5_000L;
    /** NO_DATA_MS as used (the tests make it short). */
    static volatile long noData = NO_DATA_MS;
    /** How often the reading connections are looked at. */
    static final long CHECK_MS = 250L;

    /** A connection that can be closed from another thread, and told it has gone quiet. */
    public interface Closable {
        /** Closed for good (another stream wants the connection): its reads end at once. Any thread; never calls back. */
        void kill();

        /**
         * How long it has been waiting in a read with nothing come (ms; 0 when not reading, or before its first data,
         * which may take a while as a provider starts a channel).
         */
        long quietMs(long now);

        /** No data for NO_DATA_MS while reading: its connection dropped (it connects again). Any thread; never calls back. */
        void quiet();
    }

    /** An open connection: its stream, and what can close it (null for one that can only be waited for). */
    public static final class Entry {
        final int session;
        final Closable closable;

        Entry(int session, Closable closable) {
            this.session = session;
            this.closable = closable;
        }
    }

    private static final Object LOCK = new Object();
    private static final List<Entry> OPEN = new ArrayList<>();
    private static final AtomicInteger SESSIONS = new AtomicInteger();
    /** Since the play log last asked: the most open at once, connections opened while another stream's was still open,
     *  the longest a stream waited for the one before (ms). */
    private static int most, overlaps, waitedMs;
    private static Thread watch;

    /** The streams' clock (ms, monotonic). */
    public static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /** A new stream (one media source): its connections share this number. */
    public static int newSession() {
        return SESSIONS.incrementAndGet();
    }

    /** A newer stream has the connection: this one (an older stream's, let go already) never opens. */
    public static final class Superseded extends InterruptedException {
        Superseded() {
            super("a newer stream has the connection");
        }
    }

    /**
     * A connection of stream session about to open: the older streams' connections closed first (those that can be,
     * at once; the others waited for), at most WAIT_MS; then it is counted as open. Its loading thread waits here. A
     * newer stream's open already (the remote moved on again): this one gives way (Superseded).
     */
    public static Entry enter(int session, Closable closable) throws InterruptedException {
        Entry e = new Entry(session, closable);
        List<Closable> kill = new ArrayList<>();
        synchronized (LOCK) {
            long start = now(), until = start + WAIT_MS;
            boolean waited = false;
            while (true) {
                kill.clear();
                boolean others = false;
                for (Entry o : OPEN) {
                    if (o.session == session) continue;
                    if (o.session > session) throw new Superseded();
                    others = true;
                    if (o.closable != null) kill.add(o.closable);
                }
                for (Closable c : kill) c.kill();
                long left = until - now();
                if (!others || left <= 0) {
                    if (others) overlaps++;
                    break;
                }
                waited = true;
                LOCK.wait(Math.min(left, 50L));
            }
            if (waited) waitedMs = (int) Math.max(waitedMs, now() - start);
            OPEN.add(e);
            most = Math.max(most, OPEN.size());
            if (closable != null && watch == null && !Exec.inline) startWatch();
        }
        return e;
    }

    /** A connection closed. */
    public static void leave(Entry e) {
        if (e == null) return;
        synchronized (LOCK) {
            OPEN.remove(e);
            LOCK.notifyAll();
        }
    }

    /** The newest stream so far (its session); streams made after it have higher numbers. */
    public static int lastSession() {
        return SESSIONS.get();
    }

    /**
     * The connections of the streams up to session that can be closed, closed now (the player let go of them: nothing
     * lingers on the provider); a stream made since is left alone.
     */
    public static void killUpTo(int session) {
        List<Closable> kill = new ArrayList<>();
        synchronized (LOCK) {
            for (Entry o : OPEN) if (o.closable != null && o.session <= session) kill.add(o.closable);
        }
        for (Closable c : kill) c.kill();
    }

    /** Stream connections open now. */
    public static int open() {
        synchronized (LOCK) {
            return OPEN.size();
        }
    }

    /**
     * For the play log, once a minute: {open now, the most open at once since the last ask, connections that opened
     * while another stream's was still open, the longest a stream waited for the one before (ms)}; the counts start
     * again.
     */
    public static int[] take() {
        synchronized (LOCK) {
            int[] out = {OPEN.size(), Math.max(most, OPEN.size()), overlaps, waitedMs};
            most = OPEN.size();
            overlaps = 0;
            waitedMs = 0;
            return out;
        }
    }

    /** The longest a reading live connection has had nothing come, now (ms). */
    public static long quietMs() {
        long now = now(), q = 0;
        synchronized (LOCK) {
            for (Entry o : OPEN) if (o.closable != null) q = Math.max(q, o.closable.quietMs(now));
        }
        return q;
    }

    /** Looks at the reading live connections now: one quiet for NO_DATA_MS is told (tests call it themselves). */
    static void check() {
        long now = now();
        List<Closable> told = new ArrayList<>();
        synchronized (LOCK) {
            for (Entry o : OPEN) if (o.closable != null && o.closable.quietMs(now) >= noData) told.add(o.closable);
        }
        for (Closable c : told) c.quiet();
    }

    private static void startWatch() {
        watch = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(CHECK_MS);
                } catch (InterruptedException e) {
                    return;
                }
                synchronized (LOCK) {
                    boolean any = false;
                    for (Entry o : OPEN) any |= o.closable != null;
                    if (!any) {
                        watch = null;
                        return;
                    }
                }
                check();
            }
        }, "homefield-stream-watch");
        watch.setDaemon(true);
        watch.start();
    }

    /** Tests: nothing open, the counts from nothing. */
    static void reset() {
        synchronized (LOCK) {
            OPEN.clear();
            most = overlaps = waitedMs = 0;
            LOCK.notifyAll();
        }
        noData = NO_DATA_MS;
    }
}
