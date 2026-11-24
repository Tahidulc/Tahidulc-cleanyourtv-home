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
 * runs, by channel and programme start. Where Sky has none, searched wider (WiderPictures: TMDB by the programme's title,
 * the year or series its description gives and the channel's kind, off the main thread and spaced out); where nothing is
 * found, the channel's own picture (ONE_COLOUR: its logo on its own colour, drawn by Pics), so a programme is never
 * blank: in the guide's cells, the info card, the mini guide, the reminder's panel and every screen that asks here. A
 * screen asks only for what the remote has rested on (peek while it moves), so holding Down through the channels asks
 * for nothing; until a picture comes the screens draw the channel's logo on its tile. Main thread only.
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
    /** The channel's own picture given while the wider search was spaced out: asked again after RETRY_MS. */
    private static final Map<Key, Long> retry = new HashMap<>();
    static final long RETRY_MS = 90_000L;

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
        if (had != null) {
            Long due = retry.isEmpty() ? null : retry.get(k);
            if (due == null || System.currentTimeMillis() < due) return had;
            // The wider search was spaced out last time: asked again (the picture there meanwhile stays drawn).
            retry.remove(k);
            known.remove(k);
        }
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
        Context app = c.getApplicationContext();
        String title = s.title[i], desc = s.desc[i], name = ch.name, group = ch.group, own = ownPicture(ch);
        SkyGuide.picture(c, ch.name, at, s.title[i], url -> {
            if (url != null && !url.isEmpty()) {
                done(kept, url);
                return;
            }
            // Sky has none: searched wider off the main thread; else the channel's own picture (never blank).
            com.gathin.homefieldtv.engine.Exec.io(() -> {
                String found = com.gathin.homefieldtv.engine.WiderPictures.find(app, title, desc, name, group);
                String use = found != null && !found.isEmpty() ? found : own;
                com.gathin.homefieldtv.engine.Exec.main(() -> {
                    if (found == null && !own.isEmpty()) retry.put(kept, System.currentTimeMillis() + RETRY_MS);
                    done(kept, use);
                });
            });
        });
        return null;
    }

    private static void done(Key k, String url) {
        known.put(k, url);
        if (retry.size() > 500) retry.clear();
        List<Runnable> w = waiting.remove(k);
        if (w != null) for (Runnable r : w) r.run();
    }

    /** The channel's own picture, for a programme nothing else was found for: its logo on its colour ("" with no logo). */
    static String ownPicture(Channel ch) {
        String logo = ch == null ? "" : com.gathin.homefieldtv.engine.LogoPack.url(ch);
        return logo == null || logo.isEmpty() ? "" : Pics.ON_COLOUR + logo;
    }
}
