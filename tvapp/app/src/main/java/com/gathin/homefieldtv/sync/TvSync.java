package com.gathin.homefieldtv.sync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;

import com.gathin.homefieldtv.engine.Exec;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Homefield TV syncs with the launcher on the same TV, which passes everything on to the family's phones, tablets and
 * other TVs (phone/tvhome-phone/SYNC.md). The launcher hands this app its token through its provider
 * (content://com.gathin.tvhome.sync, call "token"), which answers only an app signed with the launcher's own key; the
 * token then goes with POST /sync to the launcher on this TV only (127.0.0.1), as the launcher takes it from nowhere else.
 * A sync at start, ten seconds after a change here, and every ten minutes while the app runs. The token and the items
 * are never logged or shown.
 */
public final class TvSync {
    private TvSync() {}

    static final String PROVIDER = "content://com.gathin.tvhome.sync";
    static final long AFTER_CHANGE_MS = 10_000L, EVERY_MS = 10 * 60_000L;
    private static final Object LOCK = new Object();
    /** The application's own context (never an activity's), kept for the timed syncs. */
    @SuppressLint("StaticFieldLeak")
    private static Context app;
    private static volatile boolean busy;

    private static final Runnable SOON = () -> {
        Context c = app;
        if (c != null) Exec.io(() -> sync(c));
    };
    private static final Runnable TICK = new Runnable() {
        @Override public void run() {
            SOON.run();
            Exec.main.postDelayed(this, EVERY_MS);
        }
    };

    /** Homefield TV opened: a sync in a moment, then every ten minutes while it runs. */
    public static synchronized void start(Context c) {
        if (app != null) return;
        app = c.getApplicationContext();
        Exec.main.postDelayed(TICK, 3_000L);
    }

    /** Something shared changed here: a sync ten seconds later (several changes together go in one). */
    public static void changed() {
        if (app == null) return;
        Exec.main.removeCallbacks(SOON);
        Exec.main.postDelayed(SOON, AFTER_CHANGE_MS);
    }

    /** Settings, Sync now: done runs on the main thread after it. */
    public static void syncNow(Context c, Runnable done) {
        Context a = c.getApplicationContext();
        busy = true;
        Exec.io(() -> {
            sync(a);
            if (done != null) Exec.main(done);
        });
    }

    private static SharedPreferences state(Context c) {
        return c.getApplicationContext().getSharedPreferences("sync_state", Context.MODE_PRIVATE);
    }

    /** What Settings shows: syncing, when it last synced, or why it couldn't. */
    public static String status(Context c) {
        if (busy) return "Syncing…";
        SharedPreferences st = state(c);
        long synced = st.getLong("synced", 0);
        String error = st.getString("error", "");
        String when = synced > 0 ? "Synced " + DateUtils.getRelativeTimeSpanString(synced, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) : "";
        if (!error.isEmpty()) return error + (when.isEmpty() ? "" : "  ·  " + when);
        return when.isEmpty() ? "Not synced yet" : when;
    }

    /** One sync with the launcher on this TV. Off the main thread. */
    static void sync(Context c) {
        synchronized (LOCK) {
            busy = true;
            try {
                try {
                    TvLiveSync.export(c);
                } catch (RuntimeException ignored) {
                    // what is already in SyncItems still goes
                }
                Bundle b = token(c);
                String token = b == null ? null : b.getString("token");
                if (token == null || token.isEmpty()) {
                    state(c).edit().putString("error", "The launcher on this TV isn't there to sync with").apply();
                    return;
                }
                int port = b.getInt("port", 47820);
                // The first time a kind is taken in, this TV's own go out straight after.
                if (round(c, port, token) && TvLiveSync.apply(c, true)) {
                    TvLiveSync.export(c);
                    round(c, port, token);
                }
            } finally {
                busy = false;
            }
        }
    }

    /**
     * The token from the launcher's provider, or null when there is no launcher to give one (not on this TV, or not
     * signed with this app's key: it refuses).
     */
    static Bundle token(Context c) {
        try {
            return c.getContentResolver().call(Uri.parse(PROVIDER), "token", null, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** This TV's changes since the last time out, the launcher's since the last answer in: true when it answered. */
    private static boolean round(Context c, int port, String token) {
        SharedPreferences st = state(c);
        long started = System.currentTimeMillis();
        try {
            JSONObject body = new JSONObject().put("since", st.getLong("since", 0)).put("device", SyncItems.device())
                    .put("items", SyncItems.changedSince(c, st.getLong("pushed", 0)));
            JSONObject r = post(port, token, body);
            SyncItems.merge(c, r.optJSONArray("items"));
            st.edit().putLong("pushed", started).putLong("since", r.optLong("now")).putLong("synced", System.currentTimeMillis())
                    .putString("error", "").apply();
            return true;
        } catch (Refused e) {
            // A launcher from before Homefield TV synced (or one that no longer knows the token).
            st.edit().putString("error", "The launcher refused (" + e.status + "): update it").apply();
        } catch (IOException | JSONException e) {
            st.edit().putString("error", "Couldn't reach the launcher").apply();
        }
        return false;
    }

    /** An answer other than 200. */
    static final class Refused extends IOException {
        final int status;

        Refused(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }

    /** POST /sync to the launcher on this TV, with the token. */
    static JSONObject post(int port, String token, JSONObject body) throws IOException, JSONException {
        HttpURLConnection h = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/sync").openConnection();
        try {
            h.setConnectTimeout(4_000);
            h.setReadTimeout(20_000);
            h.setDoOutput(true);
            h.setRequestMethod("POST");
            h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            h.setRequestProperty("X-Token", token);
            byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
            h.setFixedLengthStreamingMode(out.length);
            try (OutputStream o = h.getOutputStream()) {
                o.write(out);
            }
            int code = h.getResponseCode();
            if (code != 200) throw new Refused(code);
            try (InputStream in = h.getInputStream()) {
                return new JSONObject(read(in, 8 << 20));
            }
        } finally {
            h.disconnect();
        }
    }

    private static String read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16_384];
        for (int n; (n = in.read(buf)) > 0; ) {
            b.write(buf, 0, n);
            if (b.size() > max) throw new IOException("too long");
        }
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }
}
