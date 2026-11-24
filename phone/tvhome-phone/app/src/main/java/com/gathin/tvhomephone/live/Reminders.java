package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.tvhomephone.SyncStore;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The guide's Remind me: a synced reminder (SYNC.md's "reminder" items, kind "show") for a programme on a channel, by
 * the channel's name in the playlist (a name of the family's own, or the row's without its quality, would make it
 * another reminder on the other devices).
 */
final class Reminders {
    private Reminders() {}

    static final String TYPE = "reminder";

    static String key(String channelName, long start) {
        return "show:" + channelName + ":" + start;
    }

    static boolean has(Context c, Channel ch, long start) {
        return SyncStore.get(c, TYPE, key(ch.playlistName, start)) != null;
    }

    /** Sets or clears the reminder; true when one is set now. */
    static boolean toggle(Context c, Channel ch, String title, long start) {
        String k = key(ch.playlistName, start);
        if (SyncStore.get(c, TYPE, k) != null) {
            SyncStore.delete(c, TYPE, k);
            return false;
        }
        try {
            SyncStore.put(c, TYPE, k, new JSONObject().put("title", title).put("at", start).put("kind", "show")
                    .put("channel", ch.playlistName));
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    /** One reminder set, as Settings lists it. */
    static final class Kept {
        final String key, title, channel;
        final long at;

        Kept(String key, String title, String channel, long at) {
            this.key = key;
            this.title = title;
            this.channel = channel;
            this.at = at;
        }
    }

    /**
     * Pure: the reminders still to come (or begun in the last hour) among the kept items (SyncStore.items: key, data,
     * deleted), soonest first; a programme's and a game's alike.
     */
    static java.util.List<Kept> upcoming(java.util.List<JSONObject> items, long now) {
        java.util.List<Kept> out = new java.util.ArrayList<>();
        for (JSONObject it : items) {
            if (it.optBoolean("deleted")) continue;
            JSONObject d = it.optJSONObject("data");
            if (d == null) continue;
            long at = d.optLong("at");
            if (at <= 0 || at < now - 3_600_000L) continue;
            String title = d.optString("title", "");
            out.add(new Kept(it.optString("key"), title.isEmpty() ? d.optString("channel", "") : title, d.optString("channel", ""), at));
        }
        out.sort((a, b) -> Long.compare(a.at, b.at));
        return out;
    }

    /** The reminders set on this phone and the family's other devices, soonest first. */
    static java.util.List<Kept> upcoming(Context c) {
        return upcoming(SyncStore.items(c, TYPE), System.currentTimeMillis());
    }

    /** One cancelled, everywhere. */
    static void cancel(Context c, String key) {
        SyncStore.delete(c, TYPE, key);
    }
}
