package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The alerts sent on this phone, newest first (the last 40), for Home's Latest list, and the keys already sent so an
 * alert never comes twice (kept a few days).
 */
public final class AlertLog {
    private AlertLog() {}

    private static final int KEEP = 40;
    private static final long SENT_MS = 4L * 24 * 3600_000L;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("sports_alerts", Context.MODE_PRIVATE);
    }

    public static synchronized void add(Context c, Events.Alert a) {
        JSONArray had = list(c);
        JSONArray out = new JSONArray();
        try {
            JSONObject o = new JSONObject().put("kind", a.kind).put("title", a.title).put("text", a.text)
                    .put("at", System.currentTimeMillis());
            if (a.channel != null && !a.channel.isEmpty()) o.put("channel", a.channel);
            if (a.game != null) o.put("slug", a.game.slug).put("id", a.game.id).put("home", a.game.homeLogo).put("away", a.game.awayLogo);
            else if (a.slug != null && !a.slug.isEmpty()) o.put("slug", a.slug);
            out.put(o);
        } catch (JSONException ignored) {
            // plain fields
        }
        for (int i = 0; i < had.length() && out.length() < KEEP; i++) out.put(had.opt(i));
        prefs(c).edit().putString("log", out.toString()).apply();
    }

    public static synchronized JSONArray list(Context c) {
        try {
            return new JSONArray(prefs(c).getString("log", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static synchronized boolean sent(Context c, String key) {
        return prefs(c).contains("k:" + key);
    }

    public static synchronized void markSent(Context c, String key) {
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putLong("k:" + key, now);
        List<String> old = new ArrayList<>();
        for (java.util.Map.Entry<String, ?> en : p.getAll().entrySet()) {
            if (en.getKey().startsWith("k:") && en.getValue() instanceof Long && now - (Long) en.getValue() > SENT_MS) old.add(en.getKey());
        }
        for (String k : old) e.remove(k);
        e.apply();
    }

    /** Every key sent, for a game's incidents. */
    public static synchronized java.util.Set<String> sentKeys(Context c) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String k : prefs(c).getAll().keySet()) if (k.startsWith("k:")) out.add(k.substring(2));
        return out;
    }
}
