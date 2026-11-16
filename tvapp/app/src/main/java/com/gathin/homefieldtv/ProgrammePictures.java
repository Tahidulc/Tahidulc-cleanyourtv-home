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

    private static final Map<String, String> known = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > 240;
        }
    };
    private static final Map<String, List<Runnable>> waiting = new HashMap<>();

    private static String key(Channel ch, long start) {
        return ch.key + "|" + start;
    }

    /** What is known of programme i's picture without asking: its address, "" when Sky has none, null when not known. */
    static String peek(Channel ch, Guide.Schedule s, int i) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        return known.get(key(ch, s.start[i]));
    }

    /**
     * Programme i's picture: its address, "" when Sky has none, or null while it is asked for (whenKnown then runs on
     * the main thread when the answer comes).
     */
    static String get(Context c, Channel ch, Guide.Schedule s, int i, Runnable whenKnown) {
        if (ch == null || s == null || i < 0 || i >= s.start.length) return "";
        String k = key(ch, s.start[i]);
        String had = known.get(k);
        if (had != null) return had;
        List<Runnable> w = waiting.get(k);
        if (w != null) {
            if (whenKnown != null && !w.contains(whenKnown)) w.add(whenKnown);
            return null;
        }
        w = new ArrayList<>(2);
        if (whenKnown != null) w.add(whenKnown);
        waiting.put(k, w);
        long now = System.currentTimeMillis();
        // Sky's programme on at the time: now for the one on now, else a little into it (the two guides' times can differ).
        long at = s.start[i] <= now && now < s.stop[i] ? now : s.start[i] + Math.min(10 * 60_000L, (s.stop[i] - s.start[i]) / 2);
        SkyGuide.picture(c, ch.name, at, s.title[i], url -> {
            known.put(k, url);
            List<Runnable> done = waiting.remove(k);
            if (done != null) for (Runnable r : done) r.run();
        });
        return null;
    }
}
