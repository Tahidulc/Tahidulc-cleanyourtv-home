package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Settings → Categories: the playlists' groups as the family wants them. Some hidden (their channels out of the guide,
 * Live TV's rows, channel up and down and search; a favourite among them stays in Favourites and Recent, as on the
 * phone), the rest in an order of the family's own (groups it hasn't placed after, in the playlist's order), and any
 * given a name of its own. Kept by the group's name, so it holds across playlists and reloads, in the phone's file and
 * key for the hidden ones ("live_groups", "hidden"), so the two can be matched one day.
 */
public final class Categories {
    private Categories() {}

    static final String PREFS = "live_groups", HIDDEN = "hidden", ORDER = "order", NAMES = "names", SORTS = "sorts";
    /** What a group with no name of its own shows as (the playlist gave none). */
    public static final String UNNAMED = "Channels";

    private static volatile Set<String> hidden;
    private static volatile List<String> order;
    private static volatile Map<String, String> names;
    /** Each group's own Sort channels (ChannelEdits' kinds), where it has one. */
    private static volatile Map<String, Integer> sorts;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** What is kept, read again on next use (after a backup is restored). */
    public static synchronized void forget() {
        hidden = null;
        order = null;
        names = null;
        sorts = null;
        Shown.changed();
    }

    private static synchronized void load(Context c) {
        if (hidden != null && order != null && names != null && sorts != null) return;
        SharedPreferences p = prefs(c);
        hidden = Collections.unmodifiableSet(new HashSet<>(p.getStringSet(HIDDEN, Collections.emptySet())));
        List<String> o = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(ORDER, "[]"));
            for (int i = 0; i < a.length(); i++) o.add(a.getString(i));
        } catch (JSONException ignored) {
            // the playlist's order
        }
        order = Collections.unmodifiableList(o);
        Map<String, String> n = new HashMap<>();
        try {
            JSONObject j = new JSONObject(p.getString(NAMES, "{}"));
            for (Iterator<String> it = j.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = j.optString(k).trim();
                if (!v.isEmpty()) n.put(k, v);
            }
        } catch (JSONException ignored) {
            // their own names
        }
        names = Collections.unmodifiableMap(n);
        Map<String, Integer> s = new HashMap<>();
        try {
            JSONObject j = new JSONObject(p.getString(SORTS, "{}"));
            for (Iterator<String> it = j.keys(); it.hasNext(); ) {
                String k = it.next();
                int v = j.optInt(k, -1);
                if (v >= ChannelEdits.PLAYLIST && v <= ChannelEdits.MANUAL) s.put(k, v);
            }
        } catch (JSONException ignored) {
            // as all the channels are sorted
        }
        sorts = Collections.unmodifiableMap(s);
    }

    // ---- a group's own channels sorting (Sort channels in its menu) -------------------------------------------------

    /** Each group's own Sort channels, where it has one (read-only). */
    public static Map<String, Integer> sorts(Context c) {
        Map<String, Integer> s = sorts;
        if (s == null) {
            load(c);
            s = sorts;
        }
        return s;
    }

    /** A group's own Sort channels, or -1: as Appearance › TV guide › Channels sorting says. */
    public static int sortOf(Context c, String group) {
        Integer s = group == null ? null : sorts(c).get(group);
        return s == null ? -1 : s;
    }

    /** A group's channels sorted its own way (ChannelEdits' kinds), or as all the channels are (-1). */
    public static synchronized void setSort(Context c, String group, int sort) {
        if (group == null) return;
        Map<String, Integer> s = new HashMap<>(sorts(c));
        if (sort < ChannelEdits.PLAYLIST || sort > ChannelEdits.MANUAL) s.remove(group);
        else s.put(group, sort);
        prefs(c).edit().putString(SORTS, new JSONObject(s).toString()).apply();
        sorts = Collections.unmodifiableMap(s);
        Shown.changed();
    }

    // ---- hidden ---------------------------------------------------------------------------------------------------

    /** The hidden groups (read-only). Fast: kept in memory once read. */
    public static Set<String> hidden(Context c) {
        Set<String> h = hidden;
        if (h == null) {
            load(c);
            h = hidden;
        }
        return h;
    }

    public static boolean isHidden(Context c, String group) {
        return group != null && hidden(c).contains(group);
    }

    public static synchronized void setHidden(Context c, String group, boolean hide) {
        Set<String> s = new HashSet<>(hidden(c));
        if (hide) s.add(group);
        else s.remove(group);
        prefs(c).edit().putStringSet(HIDDEN, s).apply();
        hidden = Collections.unmodifiableSet(s);
        Shown.changed();
    }

    /** Every group shown again. */
    public static synchronized void showAll(Context c) {
        prefs(c).edit().remove(HIDDEN).apply();
        hidden = Collections.emptySet();
        Shown.changed();
    }

    /** Every one of these groups hidden (TiviMate's Hide all, in Manage groups). */
    public static synchronized void hideAll(Context c, List<String> groups) {
        Set<String> s = new HashSet<>(hidden(c));
        s.addAll(groups);
        prefs(c).edit().putStringSet(HIDDEN, s).apply();
        hidden = Collections.unmodifiableSet(s);
        Shown.changed();
    }

    // ---- names ----------------------------------------------------------------------------------------------------

    /** The name a group shows as: its own when given one, else the playlist's ("Channels" for none). */
    public static String label(Context c, String group) {
        Map<String, String> n = names;
        if (n == null) {
            load(c);
            n = names;
        }
        String own = group == null ? null : n.get(group);
        if (own != null) return own;
        return group == null || group.isEmpty() ? UNNAMED : group;
    }

    public static boolean renamed(Context c, String group) {
        label(c, group);
        return group != null && names.containsKey(group);
    }

    /** A group given a name of its own; empty: back to the playlist's. */
    public static synchronized void rename(Context c, String group, String name) {
        label(c, group);
        Map<String, String> n = new HashMap<>(names);
        String v = name == null ? "" : name.trim();
        if (v.length() > 60) v = v.substring(0, 60).trim();
        if (v.isEmpty() || v.equals(group)) n.remove(group);
        else n.put(group, v);
        prefs(c).edit().putString(NAMES, new JSONObject(n).toString()).apply();
        names = Collections.unmodifiableMap(n);
        Shown.changed();
    }

    // ---- order ----------------------------------------------------------------------------------------------------

    public static List<String> order(Context c) {
        List<String> o = order;
        if (o == null) {
            load(c);
            o = order;
        }
        return o;
    }

    /** Whether the family has put the groups in an order of their own. */
    public static boolean ordered(Context c) {
        return !order(c).isEmpty();
    }

    /** The playlist's order again. */
    public static synchronized void resetOrder(Context c) {
        prefs(c).edit().remove(ORDER).apply();
        order = Collections.emptyList();
        Shown.changed();
    }

    /**
     * A group moved among the groups as Settings lists them (every one, hidden too, in the family's order): dir -1 up a
     * place, +1 down, 0 to the top. That whole order is kept.
     */
    public static synchronized void move(Context c, List<String> groups, String group, int dir) {
        List<String> now = moved(arrange(groups, order(c), Collections.emptySet()), group, dir);
        prefs(c).edit().putString(ORDER, new JSONArray(now).toString()).apply();
        order = Collections.unmodifiableList(now);
        Shown.changed();
    }

    /**
     * The groups shown put in a new order (Move group in the TV guide): the hidden ones keep their places among them,
     * and that whole order is kept.
     */
    public static synchronized void placeShown(Context c, List<String> groups, List<String> shownNow) {
        List<String> now = placed(arrange(groups, order(c), Collections.emptySet()), shownNow);
        prefs(c).edit().putString(ORDER, new JSONArray(now).toString()).apply();
        order = Collections.unmodifiableList(now);
        Shown.changed();
    }

    /** The groups the lists show, in the family's order, hidden ones left out. */
    public static List<String> arranged(Context c, List<String> groups) {
        return arrange(groups, order(c), hidden(c));
    }

    /** Every group, hidden ones too, in the family's order (for Settings). */
    public static List<String> every(Context c, List<String> groups) {
        return arrange(groups, order(c), Collections.emptySet());
    }

    // ---- pure -----------------------------------------------------------------------------------------------------

    /**
     * Pure: the groups in an order: those the order names first, as it names them (only ones that are still there),
     * then the rest in the playlist's order; the hidden ones left out.
     */
    public static List<String> arrange(List<String> groups, List<String> order, Set<String> hidden) {
        Set<String> there = new LinkedHashSet<>(groups);
        List<String> out = new ArrayList<>(there.size());
        Set<String> placed = new HashSet<>();
        for (String g : order) {
            if (there.contains(g) && placed.add(g) && !hidden.contains(g)) out.add(g);
        }
        for (String g : there) {
            if (!placed.contains(g) && !hidden.contains(g)) out.add(g);
        }
        return out;
    }

    /**
     * Pure: every group in order (hidden ones too) with the ones in shownNow put in shownNow's order, each in one of the
     * places those took, so the others (hidden) keep theirs: [A, h, B, C] with C, A, B shown → [C, h, A, B].
     */
    static List<String> placed(List<String> every, List<String> shownNow) {
        Set<String> moving = new HashSet<>(shownNow);
        List<String> next = new ArrayList<>();
        for (String g : shownNow) if (every.contains(g)) next.add(g);
        List<String> out = new ArrayList<>(every.size());
        int k = 0;
        for (String g : every) {
            if (moving.contains(g) && k < next.size()) out.add(next.get(k++));
            else if (!moving.contains(g)) out.add(g);
        }
        while (k < next.size()) out.add(next.get(k++));
        return out;
    }

    /** Pure: a list with one of its names moved (dir -1 up a place, +1 down, 0 to the top); the same list when it isn't in it. */
    static List<String> moved(List<String> list, String name, int dir) {
        List<String> out = new ArrayList<>(list);
        int i = out.indexOf(name);
        if (i < 0) return out;
        out.remove(i);
        int to = dir == 0 ? 0 : Math.max(0, Math.min(out.size(), i + dir));
        out.add(to, name);
        return out;
    }
}
