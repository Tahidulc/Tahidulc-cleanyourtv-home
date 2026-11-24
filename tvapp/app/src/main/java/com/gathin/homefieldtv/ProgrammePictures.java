package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.SkyGuide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Programme pictures from Sky's public guide (SkyGuide) for the TV guide and the live screens, remembered while the app
 * runs, by channel and programme start. A screen asks only for what the remote has rested on (peek while it moves), so
 * holding Down through the channels asks for nothing; until a picture comes the screens draw the channel's logo on its
 * tile. Main thread only.
 */
final class ProgrammePictures {
    private ProgrammePictures() {}

    /** How long the remote rests before pictures are asked for. */
    static final long REST_MS = 300;

    private static final Map<Key, String> known = new LinkedHashMap<Key, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, String> e) {
            return size() > 240;
        }
    };
    private static final Map<Key, List<Runnable>> waiting = new HashMap<>();

    /** A programme by its channel's key and its start: looked up through one kept probe (a frame's cells make nothing). */
    private static final class Key {
        String ch;
        long start;
        int hash;

        Key set(String c, long s) {
            ch = c;
            start = s;
            hash = c.hashCode() * 31 + Long.hashCode(s);
            return this;
        }

        @Override public int hashCode() {
            return hash;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return hash == k.hash && start == k.start && ch.equals(k.ch);
        }
    }

    private static final Key PROBE = new Key();

    /** The probe set to a programme (main thread), for looking up. */
    private static Key probe(Channel ch, long start) {
        return PROBE.set(ch.key, start);
    }

    /** A key of its own, to keep. */
    private static Key key(Channel ch, long start) {
        return new Key().set(ch.key, start);
    }

    /** Previews: programme i's picture given straight in (a drawn picture's file: address), as if Sky had answered. */
    static void preview(Channel ch, Guide.Schedule s, int i, String url) {
        if (ch != null && s != null && i >= 0 && i < s.start.length) known.put(key(ch, s.start[i]), url == null ? "" : url);
    }

    /** What is known of programme i's picture without asking: its address, "" when Sky has none, null when not known. */
    static String peek(Channel ch, Guide.Schedule s, int i) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        return known.get(probe(ch, s.start[i]));
    }

    /**
     * Programme i's picture: its address, "" when Sky has none, or null while it is asked for (whenKnown then runs on
     * the main thread when the answer comes).
     */
    static String get(Context c, Channel ch, Guide.Schedule s, int i, Runnable whenKnown) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        Key k = probe(ch, s.start[i]);
        String had = known.get(k);
        if (had != null) return had;
        List<Runnable> w = waiting.get(k);
        if (w != null) {
            if (whenKnown != null && !w.contains(whenKnown)) w.add(whenKnown);
            return null;
        }
        k = key(ch, s.start[i]);
        w = new ArrayList<>(2);
        if (whenKnown != null) w.add(whenKnown);
        waiting.put(k, w);
        long now = System.currentTimeMillis();
        // Sky's programme on at the time: now for the one on now, else a little into it (the two guides' times can differ).
        long at = s.start[i] <= now && now < s.stop[i] ? now : s.start[i] + Math.min(10 * 60_000L, (s.stop[i] - s.start[i]) / 2);
        Key kept = k;
        SkyGuide.picture(c, ch.name, at, s.title[i], url -> {
            known.put(kept, url);
            List<Runnable> done = waiting.remove(kept);
            if (done != null) for (Runnable r : done) r.run();
        });
        return null;
    }
}
