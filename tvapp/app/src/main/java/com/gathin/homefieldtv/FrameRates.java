package com.gathin.homefieldtv;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Versions;
import com.gathin.homefieldtv.prefs.QualityPicks;

import java.util.HashMap;
import java.util.Map;

/**
 * Each channel's frames a second as last seen playing it live (the stream's own rate, or as FpsMeter measured it),
 * kept on this TV so the guide's details can show the chip ("50 FPS") for a channel watched before, and the player's
 * card at once for one tuned again. Only ever a rate seen on that channel; a channel never seen has none (no chip).
 * Main thread. Kept by the stream's own key (a channel's FHD and SD versions have their own rates); one row per channel,
 * a channel's rate is its version picked by hand's, else its best's, else any of its versions' seen (of(channel)).
 */
final class FrameRates {
    private FrameRates() {}

    private static final String PREFS = "frame_rates";
    /** The most channels kept (the oldest let go past it). */
    static final int MAX = 600;
    private static Map<String, Integer> known;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Map<String, Integer> known(Context c) {
        if (known == null) {
            known = new HashMap<>();
            for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
                if (e.getValue() instanceof Integer) known.put(e.getKey(), (Integer) e.getValue());
            }
        }
        return known;
    }

    /** A channel's frames a second as last seen (whole: 24, 25, 30, 50, 60), or 0 when not known. */
    static int of(Context c, String key) {
        if (key == null) return 0;
        Integer f = known(c).get(key);
        return f == null ? 0 : f;
    }

    /**
     * A channel's frames a second as last seen: one row per channel, its version picked by hand first, then its versions
     * best first, then its own key's; 0 when not known. Makes nothing (the guide's details ask as they draw).
     */
    static int of(Context c, Channel ch) {
        if (ch == null) return 0;
        Versions v = Versions.of(ch);
        if (v != null) {
            int f = of(c, QualityPicks.pickedKey(c, ch));
            for (int i = 0; f <= 0 && i < v.all.length; i++) f = of(c, v.all[i].key);
            if (f > 0) return f;
        }
        return of(c, ch.key);
    }

    /** A channel's rate seen playing it (whole frames a second); written only when it changes. */
    static void put(Context c, String key, int fps) {
        if (key == null || fps <= 0) return;
        Map<String, Integer> k = known(c);
        Integer had = k.get(key);
        if (had != null && had == fps) return;
        k.put(key, fps);
        SharedPreferences.Editor e = prefs(c).edit().putInt(key, fps);
        if (k.size() > MAX) {
            // Kept small: the channels past the most are let go (any of them; they are measured again when watched).
            int drop = k.size() - MAX;
            for (java.util.Iterator<String> it = k.keySet().iterator(); it.hasNext() && drop > 0; ) {
                String other = it.next();
                if (other.equals(key)) continue;
                it.remove();
                e.remove(other);
                drop--;
            }
        }
        e.apply();
    }

    /** "50 FPS": the chip's words for a rate (whole frames a second), "" for none. */
    static String chip(int fps) {
        return fps <= 0 ? "" : CHIPS.computeIfAbsent(fps, f -> f + " FPS");
    }

    private static final Map<Integer, String> CHIPS = new HashMap<>();

    /** Tests and Settings → Remove everything: forgotten. */
    static void forget(Context c) {
        known = null;
        prefs(c).edit().clear().apply();
    }
}
