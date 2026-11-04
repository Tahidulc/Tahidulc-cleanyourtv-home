package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What How was it? was told (WatchCards): each film or series rated Not for me, Liked it or Loved it, by its TMDB id
 * ("mv:123", "tv:456"), with when, its main genre and its name; and which titles it has asked about already, so it asks
 * once. Kept in the app's own preferences, never sent anywhere. Loved titles lead Because you watched (Because); Not for
 * me titles are left out of it and of Surprise me, and Surprise me puts others of their main genre last.
 */
final class Ratings {
    private Ratings() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ratings", Context.MODE_PRIVATE);
    }

    static String key(boolean tv, int id) {
        return (tv ? "tv:" : "mv:") + id;
    }

    /** A rating saved (value: WatchRules.NOT_FOR_ME, LIKED or LOVED). */
    static void set(Context c, boolean tv, int id, int value, String genre, String name) {
        if (id <= 0) return;
        prefs(c).edit().putString("r." + key(tv, id), WatchRules.encode(value, System.currentTimeMillis(), genre, name)).apply();
        AlertLog.note(c, "rated " + name + ": " + WatchRules.word(value));
        SyncHub.changed(c);
    }

    /** Every rating as kept: its key ("mv:123", "tv:456:s2") to "value|when|genre|name" (SyncHub). */
    static Map<String, String> records(Context c) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (e.getKey().startsWith("r.") && e.getValue() instanceof String) out.put(e.getKey().substring(2), (String) e.getValue());
        }
        return out;
    }

    /** The main genre kept with a rating, or "" (a rating from a phone keeps the one this TV knew). */
    static String genre(Context c, String key) {
        Object[] r = WatchRules.decode(prefs(c).getString("r." + key, null));
        return r == null ? "" : (String) r[2];
    }

    /** A rating made on a phone or the other TV (SyncHub), as Ratings keeps it. */
    static void putSynced(Context c, String key, String record) {
        prefs(c).edit().putString("r." + key, record).apply();
        SyncHub.changed(c);
    }

    /** A rating taken back elsewhere (SyncHub); false when there was none here. */
    static boolean removeSynced(Context c, String key) {
        if (!prefs(c).contains("r." + key)) return false;
        prefs(c).edit().remove("r." + key).apply();
        SyncHub.changed(c);
        return true;
    }

    /** The rating of a title, or 0 when it has none. */
    static int get(Context c, boolean tv, int id) {
        Object[] r = WatchRules.decode(prefs(c).getString("r." + key(tv, id), null));
        return r == null ? 0 : (Integer) r[0];
    }

    /** Every rating: {key ("mv:123"), value, when, genre, name}, newest first. */
    static List<Object[]> all(Context c) {
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet()) {
            if (!e.getKey().startsWith("r.") || !(e.getValue() instanceof String)) continue;
            Object[] r = WatchRules.decode((String) e.getValue());
            if (r != null) out.add(new Object[]{e.getKey().substring(2), r[0], r[1], r[2], r[3]});
        }
        out.sort((a, b) -> Long.compare((Long) b[2], (Long) a[2]));
        return out;
    }

    /** The titles rated Not for me ("mv:123"). */
    static Set<String> notForMe(Context c) {
        Set<String> out = new HashSet<>();
        for (Object[] r : all(c)) if ((Integer) r[1] == WatchRules.NOT_FOR_ME) out.add((String) r[0]);
        return out;
    }

    /** The main genres of the titles rated Not for me (Surprise me puts others of them last). */
    static Set<String> notForMeGenres(Context c) {
        Set<String> out = new HashSet<>();
        for (Object[] r : all(c)) if ((Integer) r[1] == WatchRules.NOT_FOR_ME && !((String) r[3]).isEmpty()) out.add((String) r[3]);
        return out;
    }

    /** The titles rated Loved it, newest first: {key, name}. */
    static List<String[]> loved(Context c) {
        List<String[]> out = new ArrayList<>();
        for (Object[] r : all(c)) if ((Integer) r[1] == WatchRules.LOVED) out.add(new String[]{(String) r[0], (String) r[4]});
        return out;
    }

    /** Whether How was it? already asked about this (a film's key, or a series' season: "tv:456:s2"). */
    static boolean asked(Context c, String what) {
        return prefs(c).getBoolean("a." + what, false);
    }

    static void markAsked(Context c, String what) {
        prefs(c).edit().putBoolean("a." + what, true).apply();
    }
}
