package com.gathin.tvhomephone.sync;

import android.content.Context;

import com.gathin.tvhomephone.Net;
import com.gathin.tvhomephone.SyncStore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * The sync itself (SYNC.md): with each paired TV in turn, this phone's changes since its last push go to the TV in one
 * POST /sync, and the TV's changes since the "now" it gave last time come back and are merged into SyncStore. A TV not
 * at its last address is looked for on the home network by name (at most every ten minutes). Off the main thread.
 */
final class Syncer {
    private static final Object LOCK = new Object();

    private Syncer() {}

    /** force: also TVs that failed a moment ago (Sync now, a new pairing). */
    static void syncAll(Context c, boolean force) {
        synchronized (LOCK) {
            for (JSONObject tv : Tvs.list(c)) {
                if (tv.optString("token").isEmpty()) continue;
                if (!force && tv.optLong("failedAt") > 0 && System.currentTimeMillis() - tv.optLong("failedAt") < 60_000L) continue;
                syncOne(c, tv);
            }
        }
    }

    private static void syncOne(Context c, JSONObject tv) {
        SyncState st = new SyncState(tv.optLong("since"), tv.optLong("pushed"));
        long started = st.begin(System.currentTimeMillis());
        try {
            JSONArray mine = SyncStore.changedSince(c, st.pushed);
            JSONObject body = new JSONObject().put("since", st.since).put("device", SyncStore.device()).put("items", mine);
            JSONObject r = call(c, tv, "/sync", body, 15_000);
            SyncStore.merge(c, r.optJSONArray("items"));
            st.done(started, r.optLong("now"));
            tv.put("since", st.since).put("pushed", st.pushed).put("synced", System.currentTimeMillis())
                    .put("error", "").put("failedAt", 0);
            if (Tvs.tmdbKey(c) == null) {
                try {
                    Tvs.setTmdbKey(c, call(c, tv, "/tmdb", new JSONObject(), 6_000).optString("key"));
                } catch (IOException ignored) {
                    // another time
                }
            }
        } catch (IOException e) {
            int s = Link.status(e);
            try {
                if (s == 401) {
                    // The TV no longer knows this phone: it must pair again; everything goes again then.
                    st.reset();
                    tv.put("token", "").put("since", 0).put("pushed", 0).put("error", "Pair again");
                } else {
                    tv.put("error", s == 0 ? "Not reachable" : "Refused (" + s + ")").put("failedAt", System.currentTimeMillis());
                }
            } catch (JSONException ignored) {
                // plain values
            }
        } catch (JSONException e) {
            try {
                tv.put("error", "Couldn't read the TV's answer");
            } catch (JSONException ignored) {
                // plain values
            }
        }
        Tvs.update(c, tv);
    }

    /**
     * One call to a paired TV with its token; when its last address doesn't answer, the TV is looked for by name on
     * the home network and the call made there (the address kept).
     */
    static JSONObject call(Context c, JSONObject tv, String path, JSONObject body, int timeoutMs) throws IOException, JSONException {
        String host = tv.optString("host");
        int port = tv.optInt("port", Link.PORT);
        try {
            return Link.post(host, port, path, body, tv.optString("token"), timeoutMs);
        } catch (Net.HttpError e) {
            throw e;
        } catch (IOException e) {
            String moved = Discovery.relocate(c, tv.optString("name"), host, port);
            if (moved == null) throw e;
            tv.put("host", moved);
            Tvs.update(c, tv);
            return Link.post(moved, port, path, body, tv.optString("token"), timeoutMs);
        }
    }
}
