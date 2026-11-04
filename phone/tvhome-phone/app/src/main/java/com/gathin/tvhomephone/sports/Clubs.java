package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.SyncStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The starred clubs whose games the alerts follow: the synced "clubs"/"starred" item (SYNC.md), the same list as the
 * TVs' goal_alert_teams; Manchester United (ESPN 360) until a TV or this phone sets one. Their names and crests are
 * kept here for Settings.
 */
public final class Clubs {
    private Clubs() {}

    public static final String MAN_UNITED = "360";

    public static Set<String> starred(Context c) {
        Set<String> out = new LinkedHashSet<>();
        JSONObject d = SyncStore.get(c, "clubs", "starred");
        JSONArray t = d == null ? null : d.optJSONArray("teams");
        for (int i = 0; t != null && i < t.length(); i++) {
            String id = t.optString(i);
            if (!id.isEmpty()) out.add(id);
        }
        if (d == null) out.add(MAN_UNITED);
        return out;
    }

    public static void set(Context c, Set<String> ids) {
        JSONObject d = new JSONObject();
        try {
            d.put("teams", new JSONArray(new ArrayList<>(ids)));
        } catch (JSONException ignored) {
            // a plain list
        }
        SyncStore.put(c, "clubs", "starred", d);
    }

    private static SharedPreferences names(Context c) {
        return c.getApplicationContext().getSharedPreferences("sports_club_names", Context.MODE_PRIVATE);
    }

    /** Remembers a club's name and crest as a game or a search shows it. */
    public static void know(Context c, String id, String name, String logo) {
        if (id == null || id.isEmpty() || name == null) return;
        String had = names(c).getString(id, null);
        String now = name + "|" + (logo == null ? "" : logo);
        if (!now.equals(had)) names(c).edit().putString(id, now).apply();
    }

    /** {id, name, crest} for each starred club; the name is the id when not yet seen. */
    public static List<String[]> describe(Context c) {
        List<String[]> out = new ArrayList<>();
        for (String id : starred(c)) {
            String k = names(c).getString(id, null);
            String name = MAN_UNITED.equals(id) ? "Manchester United" : id;
            String logo = Espn.crest(id);
            if (k != null) {
                int bar = k.indexOf('|');
                name = k.substring(0, bar);
                if (bar + 1 < k.length()) logo = k.substring(bar + 1);
            }
            out.add(new String[]{id, name, logo});
        }
        return out;
    }
}
