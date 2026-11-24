package com.gathin.tvhomephone.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings the family's devices share (SYNC.md, "setting" items): each by a name every app knows ("posters.landscape")
 * and, here, the preferences file and key the phone keeps it under. Only those that make sense on every phone and tablet
 * (and, for the first few, on Homefield TV too); what belongs to one device (its screen, its player's buffer, TorBox's
 * key) stays where it is. An item's data is {"v": value, "t": "b" | "i" | "l" | "s"}.
 */
final class SyncSettingsList {
    private SyncSettingsList() {}

    /** {name, preferences file, key, type}. */
    static final String[][] PHONE = {
            // On every device (Homefield TV keeps these under its own names).
            {"posters.landscape", "posters", "landscape", "b"},
            {"board.on", "score_overlay", "on", "b"},
            {"board.lineups", "score_overlay", "lineups", "b"},
            // On the phones and tablets.
            {"subs.on", "subtitles", "on", "b"},
            {"subs.langs", "subtitles", "langs", "s"},
            {"subs.size", "subtitles", "size", "i"},
            {"subs.look", "subtitles", "look", "i"},
            {"subs.hi", "subtitles", "hi", "b"},
            {"subs.auto", "subtitles", "auto", "b"},
            {"captions.live", "subtitles", "auto_live", "b"},
            {"board.cover", "score_overlay", "cover", "b"},
            {"board.style", "score_overlay", "style", "s"},
            {"board.custom", "score_overlay", "custom", "s"},
            {"board.logo", "score_overlay", "logo", "s"},
            {"board.subs", "score_overlay", "subs", "b"},
            {"board.fotmob", "score_overlay", "fotmob", "b"},
            {"board.seethrough", "score_overlay", "see_through", "b"},
            // Homefield TV's Appearance and Channels & categories, under its own preference names where it has them.
            {"times.24h", "look", "times24", "b"},
            {"look.progress", "look", "progress", "b"},
            {"channels.merge", "live_edits", "merge", "b"},
            {"channels.sort", "live_edits", "sort", "i"},
    };
    /** Those kept in memory once read (live.Times, Theme, ChannelEdits): read again when one comes from another device. */
    static final java.util.Set<String> HELD = new java.util.HashSet<>(java.util.Arrays.asList("times.24h", "look.progress",
            "channels.merge", "channels.sort"));
    /** Every alert kind's switch (Sports' alerts), each its own setting: "sports.kind." and its key. */
    private static final String KINDS = "sports_kinds", KIND = "sports.kind.";

    private static SharedPreferences prefs(Context c, String file) {
        return c.getApplicationContext().getSharedPreferences(file, Context.MODE_PRIVATE);
    }

    /** Pure: a setting's value as an item's data. */
    static JSONObject data(Object v, String type) {
        JSONObject d = new JSONObject();
        try {
            d.put("v", v).put("t", type);
        } catch (JSONException ignored) {
            // plain values
        }
        return d;
    }

    /** The shared settings this device has set, as items' data by name. */
    static Map<String, JSONObject> read(Context c) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (String[] s : PHONE) {
            SharedPreferences p = prefs(c, s[1]);
            if (!p.contains(s[2])) continue;
            Object v = p.getAll().get(s[2]);
            if (v != null && typeOf(v).equals(s[3])) out.put(s[0], data(v, s[3]));
        }
        for (Map.Entry<String, ?> e : prefs(c, KINDS).getAll().entrySet()) {
            if (e.getValue() instanceof Boolean) out.put(KIND + e.getKey(), data(e.getValue(), "b"));
        }
        return out;
    }

    /** Pure: the type letter of a preference's value. */
    static String typeOf(Object v) {
        if (v instanceof Boolean) return "b";
        if (v instanceof Integer) return "i";
        if (v instanceof Long) return "l";
        return "s";
    }

    /** Settings from another device written here (those this app has; any it doesn't know left alone). */
    static void write(Context c, List<JSONObject> items) {
        boolean held = false;
        for (JSONObject it : items) {
            if (!it.optBoolean("deleted") && HELD.contains(it.optString("key"))) held = true;
            if (it.optBoolean("deleted")) continue;
            String name = it.optString("key");
            JSONObject d = it.optJSONObject("data");
            if (d == null || !d.has("v")) continue;
            String file = null, key = null, type = d.optString("t");
            for (String[] s : PHONE) {
                if (s[0].equals(name)) {
                    file = s[1];
                    key = s[2];
                    if (!s[3].equals(type)) file = null;
                }
            }
            if (file == null && name.startsWith(KIND) && "b".equals(type) && name.length() > KIND.length() && name.length() < 80) {
                file = KINDS;
                key = name.substring(KIND.length());
            }
            if (file == null) continue;
            SharedPreferences p = prefs(c, file);
            Object had = p.getAll().get(key);
            SharedPreferences.Editor e = p.edit();
            switch (type) {
                case "b": if (!Boolean.valueOf(d.optBoolean("v")).equals(had)) e.putBoolean(key, d.optBoolean("v")).apply(); break;
                case "i": if (!Integer.valueOf(d.optInt("v")).equals(had)) e.putInt(key, d.optInt("v")).apply(); break;
                case "l": if (!Long.valueOf(d.optLong("v")).equals(had)) e.putLong(key, d.optLong("v")).apply(); break;
                default: if (!d.optString("v").equals(had)) e.putString(key, d.optString("v")).apply(); break;
            }
        }
        if (held) com.gathin.tvhomephone.live.ChannelEdits.synced(c);
    }
}
