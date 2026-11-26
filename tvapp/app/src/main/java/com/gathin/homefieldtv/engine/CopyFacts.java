package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What each copy of a film or an episode really is, for its row on the cover ("can you tell me the quality of the
 * sources and how big the file is"): its picture, HDR, sound and size. A TorBox file has its size from TorBox and the
 * rest from its release's name. A playlist's copy has them from its provider's own page of it (StreamInfo: a film's
 * get_vod_info, read here where it hasn't been; an episode's info in its series' page, read already), the name's tags
 * only where there is no page; with no size there, one HEAD of its address (its Content-Length), only while nothing
 * plays and through StreamGate's one connection (never beside a stream, and given way to one that starts); with none
 * from that either, its bitrate × length ("≈ 4.1 GB"). Each copy's size is kept for the app's life by Copies.id (never
 * its address); the address and the login in it are never logged or kept.
 *
 * <p>The copies are done one after another, off the main thread, each written on the main thread as it is known, so
 * the cover shows what is known at once and the rest as it comes; then the playlists' copies are put in their order
 * again (rank: the best first by the real values, TorBox's left where they are).
 */
public final class CopyFacts {
    private CopyFacts() {}

    /** One HEAD of an address: its Content-Length (0: none said). The tests' stand-in; the real one asks the provider. */
    public interface Head {
        long length(String url, String userAgent, String referrer) throws IOException;
    }

    /** How long a HEAD may take to connect and to answer. */
    static final int TIMEOUT_MS = 4000;
    /** Tests and previews: a stand-in for the HEAD (null: the real one, which is never asked while Exec.inline). */
    public static volatile Head head;
    /**
     * Each copy's size once looked for (by Copies.id): the file's own (bytes), worked out from its bitrate (its bytes
     * negated), or none found (0).
     */
    private static final Map<String, Long> SIZES = new HashMap<>();
    /** HEADs asked (tests). */
    static int asked;

    /** A fill under way: stopped as the cover goes or a copy starts playing (no HEAD after that). */
    public static final class Look {
        volatile boolean stopped;

        public void stop() {
            stopped = true;
        }

        public boolean stopped() {
            return stopped;
        }
    }

    /** Forget every size kept (tests). */
    public static synchronized void forget() {
        SIZES.clear();
        asked = 0;
    }

    /**
     * The copies filled in (on the main thread, one after another): changed runs on the main thread each time one of
     * them learnt something. The list is read, never changed here; its copies are.
     */
    public static Look fill(Context c, List<Catalog.Play> plays, Runnable changed) {
        Look look = new Look();
        step(c.getApplicationContext(), new ArrayList<>(plays), 0, changed, look);
        return look;
    }

    private static void step(Context app, List<Catalog.Play> plays, int i, Runnable changed, Look look) {
        if (look.stopped || i >= plays.size()) return;
        Catalog.Play p = plays.get(i);
        Runnable next = () -> step(app, plays, i + 1, changed, look);
        if (p.torbox || TorBox.ours(p.url)) {
            next.run();
            return;
        }
        Runnable size = () -> sized(app, p, changed, look, next);
        if (!p.info && !p.key.isEmpty()) {
            // Its own title's page (a film's: get_vod_info) says what its stream is.
            Catalog cat = Catalog.get(app);
            Catalog.Title t = cat.title(p.key);
            if (t != null && !t.series) {
                cat.details(t, d -> {
                    if (look.stopped) return;
                    if (d != null && d.stream != null && !p.info) {
                        d.stream.apply(p);
                        changed.run();
                    }
                    size.run();
                });
                return;
            }
        }
        size.run();
    }

    /** Its size: the file's own as known, kept from before, asked once (HEAD), else worked out. */
    private static void sized(Context app, Catalog.Play p, Runnable changed, Look look, Runnable next) {
        if (p.size > 0 && !p.sizeGuessed) {
            next.run();
            return;
        }
        String id = Copies.id(p);
        Long kept;
        synchronized (CopyFacts.class) {
            kept = SIZES.get(id);
        }
        long estimate = p.stream == null ? 0 : p.stream.estimate();
        if (kept != null) {
            if (put(p, kept != 0 ? kept : -estimate)) changed.run();
            next.run();
            return;
        }
        String url = p.url, ua = p.userAgent, ref = p.referrer;
        Exec.io(() -> {
            long got = look.stopped ? -1 : ask(url, ua, ref);
            Exec.main(() -> {
                // -1: not asked now (something plays): the estimate for now, asked another time.
                long v = got > 0 ? got : estimate > 0 ? -estimate : 0;
                if (got >= 0) {
                    synchronized (CopyFacts.class) {
                        SIZES.put(id, v);
                    }
                }
                if (look.stopped) return;
                if (put(p, v)) changed.run();
                next.run();
            });
        });
    }

    /** A size on a copy (bytes; negated: worked out); whether it changed. */
    private static boolean put(Catalog.Play p, long v) {
        if (v > 0) {
            if (p.size == v && !p.sizeGuessed) return false;
            p.size = v;
            p.sizeGuessed = false;
            return true;
        }
        if (v < 0 && (p.size <= 0 || p.sizeGuessed) && p.size != -v) {
            p.size = -v;
            p.sizeGuessed = true;
            return true;
        }
        return false;
    }

    /**
     * One HEAD: its length (0: none said, or it failed), or -1 when it wasn't asked because a stream is open. Through
     * StreamGate as a stream of its own, so it never overlaps one, and a stream that starts closes it at once.
     */
    static long ask(String url, String userAgent, String referrer) {
        Head h = head;
        if (h == null && Exec.inline) return 0;
        if (StreamGate.open() > 0) return -1;
        int session = StreamGate.newSession();
        StreamGate.Entry entry = null;
        Connection conn = new Connection();
        try {
            entry = StreamGate.enter(session, conn);
            synchronized (CopyFacts.class) {
                asked++;
            }
            return h != null ? Math.max(0, h.length(url, userAgent, referrer)) : conn.head(url, userAgent, referrer);
        } catch (InterruptedException e) {
            if (!(e instanceof StreamGate.Superseded)) Thread.currentThread().interrupt();
            return -1;
        } catch (IOException | RuntimeException e) {
            // Only its kind: the address (with the login in it) is never written out.
            android.util.Log.w("HomefieldTv", "copy size not asked: " + e.getClass().getSimpleName());
            return 0;
        } finally {
            conn.kill();
            StreamGate.leave(entry);
        }
    }

    /** The HEAD's connection, which a stream that starts closes at once. */
    private static final class Connection implements StreamGate.Closable {
        private volatile HttpURLConnection http;
        private volatile boolean killed;

        long head(String url, String userAgent, String referrer) throws IOException {
            if (killed) return 0;
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            http = c;
            c.setRequestMethod("HEAD");
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            if (userAgent != null && !userAgent.isEmpty()) c.setRequestProperty("User-Agent", userAgent);
            if (referrer != null && !referrer.isEmpty()) c.setRequestProperty("Referer", referrer);
            int code = c.getResponseCode();
            long length = c.getContentLengthLong();
            return code >= 200 && code < 300 && length > 0 ? length : 0;
        }

        @Override public void kill() {
            killed = true;
            HttpURLConnection c = http;
            if (c != null) c.disconnect();
        }

        @Override public long quietMs(long now) {
            return 0;
        }

        @Override public void quiet() {
            // a HEAD is answered or it times out
        }
    }

    /**
     * Pure: the playlists' copies put in their order again by what is known of them now (Formats.compare: the picture,
     * HDR before SDR, surround before stereo, the bigger file), each in a place a playlist's copy had; TorBox's left
     * where they are. Whether the order changed.
     */
    public static boolean rank(List<Catalog.Play> plays) {
        List<Integer> at = new ArrayList<>();
        List<Catalog.Play> theirs = new ArrayList<>();
        for (int i = 0; i < plays.size(); i++) {
            if (plays.get(i).torbox) continue;
            at.add(i);
            theirs.add(plays.get(i));
        }
        List<Catalog.Play> sorted = new ArrayList<>(theirs);
        Formats.sort(sorted);
        if (sorted.equals(theirs)) return false;
        for (int k = 0; k < at.size(); k++) plays.set(at.get(k), sorted.get(k));
        return true;
    }
}
