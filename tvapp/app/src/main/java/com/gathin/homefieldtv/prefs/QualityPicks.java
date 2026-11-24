package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Versions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quality version the family picked by hand for a channel (the Quality choice in the guide's hold-OK card and the
 * player's Video tracks: "ITV 1 HD" rather than its best), kept on this TV until Best is chosen again. Kept by the
 * channel's versions' keys (any of them finds it), so it holds across reloads, whichever version the channel's row is.
 * Main thread (read at once from memory).
 */
public final class QualityPicks {
    private QualityPicks() {}

    static final String PREFS = "quality_picks";
    private static Map<String, String> picks;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static synchronized Map<String, String> picks(Context c) {
        if (picks == null) {
            picks = new HashMap<>();
            for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
                if (e.getValue() instanceof String) picks.put(e.getKey(), (String) e.getValue());
            }
        }
        return picks;
    }

    /** Read again on next use (a backup put back). */
    public static synchronized void forget() {
        picks = null;
    }

    /** The version picked by hand for a channel, among these (its versions to play), or null: its best (automatic). */
    public static Channel of(Context c, Channel ch, List<Channel> versions) {
        if (ch == null || versions == null || versions.size() < 2) return null;
        Map<String, String> m = picks(c);
        if (m.isEmpty()) return null;
        String want = m.get(ch.key);
        Versions v = Versions.of(ch);
        if (want == null && v != null) {
            for (Channel o : v.all) {
                want = m.get(o.key);
                if (want != null) break;
            }
        }
        if (want == null) return null;
        for (Channel o : versions) if (o.key.equals(want)) return o;
        return null;
    }

    /** The key of the version picked by hand for a channel (one of its versions), or null; makes nothing (drawing asks). */
    public static String pickedKey(Context c, Channel ch) {
        if (ch == null) return null;
        Map<String, String> m = picks(c);
        if (m.isEmpty()) return null;
        String want = m.get(ch.key);
        Versions v = Versions.of(ch);
        if (v == null) return null;
        for (int i = 0; want == null && i < v.all.length; i++) want = m.get(v.all[i].key);
        return want != null && v.has(want) ? want : null;
    }

    /** A version picked by hand for a channel; null: Best again (its best version, automatically). */
    public static synchronized void set(Context c, Channel ch, Channel version) {
        if (ch == null) return;
        Map<String, String> m = picks(c);
        SharedPreferences.Editor e = prefs(c).edit();
        Versions v = Versions.of(ch);
        m.remove(ch.key);
        e.remove(ch.key);
        if (v != null) {
            for (Channel o : v.all) {
                m.remove(o.key);
                e.remove(o.key);
            }
        }
        if (version != null) {
            m.put(ch.key, version.key);
            e.putString(ch.key, version.key);
        }
        e.apply();
    }
}
