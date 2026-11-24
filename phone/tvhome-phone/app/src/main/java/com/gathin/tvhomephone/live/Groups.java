package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The playlists' categories as the family wants them: hidden ones left out of Live TV (their channels out of the guide,
 * the lists and search; a favourite channel among them stays in Favourites), and favourite ones first among the chips,
 * in the order they were starred. By the category's name, so they hold across playlists and reloads. Hidden and
 * favourite ones go to the family's other phones and tablets (sync's livecat); the rest is kept on this phone.
 *
 * <p>As Homefield TV's Manage categories has them besides: a name of the family's own (Rename category; label()), their
 * own order (Categories sorting: By order in playlist, or Your own order, the categories moved in Manage categories)
 * and a category's own Sort channels (ChannelEdits' sortings; -1 as Channels sorting says).
 */
public final class Groups {
    private Groups() {}

    private static final String PREFS = "live_groups", HIDDEN = "hidden", FAVS = "favs", NAMES = "names", ORDER = "order",
            ORDERED = "ordered", SORTS = "sorts";
    /** Goes up with every change, for the chips to know to be drawn again. */
    static volatile int version;

    /** The names, the own order and the sortings, read once (any thread may read labels while the screens draw). */
    private static volatile Map<String, String> names = Collections.emptyMap();
    private static volatile List<String> own = Collections.emptyList();
    private static volatile boolean ordered;
    private static volatile Map<String, Integer> sorts = Collections.emptyMap();
    private static volatile boolean loaded;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The names, order and sortings read (once: Library does it as the channels come in). */
    static void load(Context c) {
        if (loaded) return;
        SharedPreferences p = prefs(c);
        names = ChannelEdits.strings(p.getString(NAMES, ""));
        own = Collections.unmodifiableList(listOf(p.getString(ORDER, "[]")));
        ordered = p.getBoolean(ORDERED, false);
        Map<String, Integer> s = new HashMap<>();
        for (Map.Entry<String, String> e : ChannelEdits.strings(p.getString(SORTS, "")).entrySet()) {
            try {
                s.put(e.getKey(), ChannelEdits.clampSort(Integer.parseInt(e.getValue())));
            } catch (NumberFormatException ignored) {
                // not a sorting
            }
        }
        sorts = s;
        loaded = true;
    }

    private static List<String> listOf(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) if (!out.contains(a.getString(i))) out.add(a.getString(i));
        } catch (JSONException ignored) {
            // none kept
        }
        return out;
    }

    // ---- names --------------------------------------------------------------------------------------------------

    /** The category's name as the family reads it: theirs, else the playlist's. */
    public static String label(String group) {
        String n = group == null ? null : names.get(group);
        return n != null && !n.isEmpty() ? n : group == null ? "" : group;
    }

    static boolean renamed(String group) {
        return group != null && names.containsKey(group);
    }

    /** A name of the family's own for a category; nothing typed (or its own) is the playlist's name again. */
    static void rename(Context c, String group, String typed) {
        load(c);
        String n = ChannelEdits.clean(typed);
        Map<String, String> m = new HashMap<>(names);
        if (n.isEmpty() || n.equals(group)) m.remove(group);
        else m.put(group, n);
        names = m;
        prefs(c).edit().putString(NAMES, ChannelEdits.json(m).toString()).apply();
        changed(c);
    }

    // ---- their order and each one's sorting ----------------------------------------------------------------------

    /** Categories sorting: their own order (true) or the playlist's. */
    static boolean ordered(Context c) {
        load(c);
        return ordered;
    }

    static void setOrdered(Context c, boolean on) {
        load(c);
        ordered = on;
        prefs(c).edit().putBoolean(ORDERED, on).apply();
        changed(c);
    }

    /** The categories in the family's order (Move in Manage categories), and that order kept in use. */
    static void setOwnOrder(Context c, List<String> groups) {
        load(c);
        own = Collections.unmodifiableList(new ArrayList<>(groups));
        ordered = true;
        prefs(c).edit().putString(ORDER, new JSONArray(own).toString()).putBoolean(ORDERED, true).apply();
        changed(c);
    }

    /** The playlists' categories as the family has them arranged: their own order when it is in use, else as given. */
    static List<String> arranged(List<String> groups) {
        return ordered ? arrange(groups, own) : groups;
    }

    /** Pure: the categories in an order of the family's (those it has, first), the rest after in the playlists' order. */
    static List<String> arrange(List<String> groups, List<String> order) {
        if (order == null || order.isEmpty()) return groups;
        List<String> out = new ArrayList<>(groups.size());
        java.util.Set<String> have = new HashSet<>(groups);
        for (String g : order) if (have.contains(g) && !out.contains(g)) out.add(g);
        for (String g : groups) if (!out.contains(g)) out.add(g);
        return out;
    }

    /** A category's own Sort channels, or -1 to go by Channels sorting. */
    static int sortOf(String group) {
        Integer s = group == null ? null : sorts.get(group);
        return s == null ? -1 : s;
    }

    static void setSort(Context c, String group, int sort) {
        load(c);
        Map<String, Integer> m = new HashMap<>(sorts);
        if (sort < 0) m.remove(group);
        else m.put(group, ChannelEdits.clampSort(sort));
        sorts = m;
        Map<String, String> kept = new HashMap<>();
        for (Map.Entry<String, Integer> e : m.entrySet()) kept.put(e.getKey(), String.valueOf(e.getValue()));
        prefs(c).edit().putString(SORTS, ChannelEdits.json(kept).toString()).apply();
        version++;
        Library.get(c).orderChanged();
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

    /** The hidden and favourite categories as another of the family's devices has them (sync). */
    static void replace(Context c, Set<String> hidden, List<String> favourites) {
        prefs(c).edit().putStringSet(HIDDEN, new HashSet<>(hidden)).putString(FAVS, new JSONArray(favourites).toString()).apply();
        changed(c);
    }

    /** Everything shown again and no favourites (Live TV's Remove everything). */
    static void clear(Context c) {
        prefs(c).edit().clear().apply();
        names = Collections.emptyMap();
        own = Collections.emptyList();
        ordered = false;
        sorts = Collections.emptyMap();
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
