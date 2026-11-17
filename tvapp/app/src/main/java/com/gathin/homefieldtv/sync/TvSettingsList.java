package com.gathin.homefieldtv.sync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings Homefield TV shares with the phones and tablets (phone/tvhome-phone/SYNC.md, "setting" items), by the
 * names every app knows them by, with the preferences file and key this app keeps each under. Only those every device
 * has; what belongs to this TV (its theme, buffer, decoder, screen) stays here. A new poster shape shows the next time
 * Homefield TV opens. An item's data is {"v": value, "t": "b"}.
 */
final class TvSettingsList {
    private TvSettingsList() {}

    /** {name, preferences file, key, type}. */
    static final String[][] TV = {
            {"posters.landscape", "look", "posters_wide", "b"},
            {"board.on", "scoreboard", "on", "b"},
            {"board.lineups", "scoreboard", "lineups", "b"},
    };

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

    /** The shared settings set on this TV, as items' data by name. */
    static Map<String, JSONObject> read(Context c) {
        Map<String, JSONObject> out = new LinkedHashMap<>();
        for (String[] s : TV) {
            Object v = prefs(c, s[1]).getAll().get(s[2]);
            if (v instanceof Boolean) out.put(s[0], data(v, s[3]));
        }
        return out;
    }

    /** Settings from another device written here (those this app has; any other left alone). */
    static void write(Context c, List<JSONObject> items) {
        for (JSONObject it : items) {
            if (it.optBoolean("deleted")) continue;
            JSONObject d = it.optJSONObject("data");
            if (d == null || !d.has("v") || !"b".equals(d.optString("t"))) continue;
            for (String[] s : TV) {
                if (!s[0].equals(it.optString("key"))) continue;
                SharedPreferences p = prefs(c, s[1]);
                boolean v = d.optBoolean("v");
                if (!Boolean.valueOf(v).equals(p.getAll().get(s[2]))) p.edit().putBoolean(s[2], v).apply();
            }
        }
    }
}
