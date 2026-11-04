package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.tvhomephone.SyncStore;

import org.json.JSONException;
import org.json.JSONObject;

/** The guide's Remind me: a synced reminder (SYNC.md's "reminder" items, kind "show") for a programme on a channel. */
final class Reminders {
    private Reminders() {}

    static final String TYPE = "reminder";

    static String key(String channelName, long start) {
        return "show:" + channelName + ":" + start;
    }

    static boolean has(Context c, Channel ch, long start) {
        return SyncStore.get(c, TYPE, key(ch.name, start)) != null;
    }

    /** Sets or clears the reminder; true when one is set now. */
    static boolean toggle(Context c, Channel ch, String title, long start) {
        String k = key(ch.name, start);
        if (SyncStore.get(c, TYPE, k) != null) {
            SyncStore.delete(c, TYPE, k);
            return false;
        }
        try {
            SyncStore.put(c, TYPE, k, new JSONObject().put("title", title).put("at", start).put("kind", "show")
                    .put("channel", ch.name));
            return true;
        } catch (JSONException e) {
            return false;
        }
    }
}
