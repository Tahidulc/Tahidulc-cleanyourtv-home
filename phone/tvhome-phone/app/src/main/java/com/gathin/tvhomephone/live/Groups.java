package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The playlists' categories as the family wants them: hidden ones left out of Live TV (their channels out of the guide,
 * the lists and search; a favourite channel among them stays in Favourites), and favourite ones first among the chips,
 * in the order they were starred. By the category's name, so they hold across playlists and reloads. Kept on this
 * phone, never synced.
 */
public final class Groups {
    private Groups() {}

    private static final String PREFS = "live_groups", HIDDEN = "hidden", FAVS = "favs";
    /** Goes up with every change, for the chips to know to be drawn again. */
    static volatile int version;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> hidden(Context c) {
        return new HashSet<>(prefs(c).getStringSet(HIDDEN, Collections.emptySet()));
    }

    public static void setHidden(Context c, String group, boolean hide) {
        Set<String> s = hidden(c);
        if (hide) s.add(group);
        else s.remove(group);
        prefs(c).edit().putStringSet(HIDDEN, s).apply();
        // Hidden is not a favourite any more (and a favourite shown again stays one).
        if (hide) setFavourite(c, group, false);
        changed(c);
    }

    /** Every category shown again. */
    public static void showAll(Context c) {
        prefs(c).edit().remove(HIDDEN).apply();
        changed(c);
    }

    private static void changed(Context c) {
        version++;
        Library.get(c).groupsChanged();
    }

    public static List<String> favourites(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(FAVS, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {
            // none kept
        }
        return out;
    }

    public static boolean isFavourite(Context c, String group) {
        return favourites(c).contains(group);
    }

    public static void setFavourite(Context c, String group, boolean fav) {
        List<String> l = favourites(c);
        l.remove(group);
        if (fav) l.add(group);
        prefs(c).edit().putString(FAVS, new JSONArray(l).toString()).apply();
        changed(c);
    }

    /** Everything shown again and no favourites (Live TV's Remove everything). */
    static void clear(Context c) {
        prefs(c).edit().clear().apply();
        version++;
    }

    /**
     * Pure: the categories as the chips show them: the favourites first (in the order starred, those the playlists
     * still have), then the rest in the playlists' order, the hidden ones left out.
     */
    static List<String> order(List<String> groups, Set<String> hidden, List<String> favourites) {
        List<String> out = new ArrayList<>();
        for (String f : favourites) if (groups.contains(f) && !hidden.contains(f) && !out.contains(f)) out.add(f);
        for (String g : groups) if (!hidden.contains(g) && !out.contains(g)) out.add(g);
        return out;
    }
}
