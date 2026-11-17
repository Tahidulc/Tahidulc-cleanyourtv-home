package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.homefieldtv.engine.Channel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The family's own edits to their channels, as TiviMate lets them make from a channel's menu in the TV guide: hidden
 * (Hide channel), a name of their own (Rename channel, with Appearance › TV guide › Channel names editor on), the logo
 * chosen (Change logo: one of the new logos, or the playlist's) and a place in its group (Move channel, TiviMate's
 * Manage positions); and how the lists are sorted (Appearance › TV guide › Channels sorting, each group's own in
 * Categories).
 *
 * <p>Kept as compact records keyed by the channel's stable key, one line a channel that has an edit, in the "channel_edits"
 * settings (so a backup carries them): {@code key<TAB>hidden<TAB>place<TAB>name<TAB>logo}, hidden "1" or "", place a
 * number or "", the logo "" (as Settings › Appearance › Logos says), "-" (the playlist's) or a new logo's path. They hold
 * across reloads and playlists' changes; a channel gone keeps its line until it comes back.
 *
 * <p>Read at once from memory (the screens draw names with name()); a change that the lists show (hidden, a name, a
 * place, the sorting) makes them again off the main thread (Shown.changed), and the lists read a Snapshot, all of it
 * at one moment.
 */
public final class ChannelEdits {
    private ChannelEdits() {}

    /** Channels sorting (Appearance › TV guide), and a group's own (Sort channels in its menu). */
    public static final int PLAYLIST = 0, NAME = 1, NUMBER = 2, MANUAL = 3;
    public static final String[] SORT_NAMES = {"By order in playlist", "By name", "By number", "Manual"};
    /** A channel's logo choice: the playlist's own. */
    public static final String LOGO_PLAYLIST = "-";
    static final String PREFS = "channel_edits", RECORDS = "records", SORT = "sort", EDITOR = "editor";
    /** A name of the family's own is at most this long. */
    public static final int NAME_MAX = 60;

    /** One channel's edits. */
    public static final class Rec {
        public final boolean hidden;
        /** Its place in its group (Move channel), or -1. */
        public final int place;
        /** Its own name ("" for the playlist's), and its logo ("" automatic, "-" the playlist's, or a new logo's path). */
        public final String name, logo;

        Rec(boolean hidden, int place, String name, String logo) {
            this.hidden = hidden;
            this.place = place;
            this.name = name == null ? "" : name;
            this.logo = logo == null ? "" : logo;
        }

        boolean empty() {
            return !hidden && place < 0 && name.isEmpty() && logo.isEmpty();
        }

        Rec hidden(boolean h) {
            return new Rec(h, place, name, logo);
        }

        Rec placed(int p) {
            return new Rec(hidden, p, name, logo);
        }

        Rec named(String n) {
            return new Rec(hidden, place, n, logo);
        }

        Rec logo(String l) {
            return new Rec(hidden, place, name, l);
        }
    }

    static final Rec NONE = new Rec(false, -1, "", "");

    /**
     * Everything at one moment, for the lists (Shown.Filter): the records, the sorting for all the channels and each
     * group's own, and the family's order of the groups. Never changes.
     */
    public static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(Collections.emptyMap(), PLAYLIST, Collections.emptyMap(), Collections.emptyList());

        public final Map<String, Rec> recs;
        public final int sort;
        public final Map<String, Integer> groupSorts;
        public final List<String> groupOrder;
        private final int hiddenCount, renamedCount, placedCount;

        /** Shown's version it was made at: made again once that has moved on. */
        int version = -1;

        Snapshot(Map<String, Rec> recs, int sort, Map<String, Integer> groupSorts, List<String> groupOrder) {
            this.recs = recs;
            this.sort = sort;
            this.groupSorts = groupSorts;
            this.groupOrder = groupOrder;
            int h = 0, n = 0, p = 0;
            for (Rec r : recs.values()) {
                if (r.hidden) h++;
                if (!r.name.isEmpty()) n++;
                if (r.place >= 0) p++;
            }
            hiddenCount = h;
            renamedCount = n;
            placedCount = p;
        }

        public boolean hidden(String key) {
            if (hiddenCount == 0 || key == null) return false;
            Rec r = recs.get(key);
            return r != null && r.hidden;
        }

        /** Its own name, or null. */
        public String name(String key) {
            if (renamedCount == 0 || key == null) return null;
            Rec r = recs.get(key);
            return r == null || r.name.isEmpty() ? null : r.name;
        }

        /** Its place in its group, or -1. */
        public int place(String key) {
            if (placedCount == 0 || key == null) return -1;
            Rec r = recs.get(key);
            return r == null ? -1 : r.place;
        }

        /** How a group's channels are sorted: its own, else as all the channels are. */
        public int sortOf(String group) {
            Integer s = group == null ? null : groupSorts.get(group);
            return s == null ? sort : s;
        }

        public boolean anyHidden() {
            return hiddenCount > 0;
        }

        public boolean anyRenamed() {
            return renamedCount > 0;
        }

        /** Whether anything changes the lists' order (a sorting other than the playlist's). */
        public boolean sorts() {
            if (sort != PLAYLIST) return true;
            for (Integer s : groupSorts.values()) if (s != null && s != PLAYLIST) return true;
            return false;
        }
    }

    private static volatile Map<String, Rec> recs;
    private static volatile int sort = -1;
    private static volatile Boolean editor;
    private static volatile Snapshot snap;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** What is kept read again on next use (a backup put back). */
    public static synchronized void forget() {
        recs = null;
        sort = -1;
        editor = null;
        Shown.changed();
    }

    /** Read from the TV's settings (once; any thread). */
    public static void load(Context c) {
        records(c);
    }

    private static Map<String, Rec> records(Context c) {
        Map<String, Rec> m = recs;
        if (m != null) return m;
        synchronized (ChannelEdits.class) {
            if (recs == null) {
                SharedPreferences p = prefs(c);
                recs = Collections.unmodifiableMap(parse(p.getString(RECORDS, "")));
                sort = PlayerPrefs.clamp(p.getInt(SORT, PLAYLIST), PLAYLIST, MANUAL);
            }
            return recs;
        }
    }

    // ---- what the screens read ------------------------------------------------------------------------------------

    /** The name a channel shows under: the family's own, else the playlist's. At once (no settings read). */
    public static String name(Channel ch) {
        if (ch == null) return "";
        Map<String, Rec> m = recs;
        Rec r = m == null ? null : m.get(ch.key);
        return r == null || r.name.isEmpty() ? ch.name : r.name;
    }

    public static boolean renamed(Channel ch) {
        Map<String, Rec> m = recs;
        Rec r = m == null || ch == null ? null : m.get(ch.key);
        return r != null && !r.name.isEmpty();
    }

    /** Whether the family hid a channel (Hide channel). At once. */
    public static boolean hidden(String key) {
        Map<String, Rec> m = recs;
        Rec r = m == null || key == null ? null : m.get(key);
        return r != null && r.hidden;
    }

    /** A channel's logo as chosen: "" (as Settings › Appearance › Logos says), "-" the playlist's, or a new logo's path. */
    public static String logo(String key) {
        Map<String, Rec> m = recs;
        Rec r = m == null || key == null ? null : m.get(key);
        return r == null ? "" : r.logo;
    }

    /** The channels hidden, by key. */
    public static List<String> hiddenKeys(Context c) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Rec> e : records(c).entrySet()) if (e.getValue().hidden) out.add(e.getKey());
        return out;
    }

    /**
     * Everything at one moment, for the lists; made once for each change to what the lists show (Shown's version, read
     * first: a change while it is made makes it stale, never wrong).
     */
    public static Snapshot snapshot(Context c) {
        int v = Shown.version();
        Snapshot s = snap;
        if (s != null && s.version == v) return s;
        Map<String, Rec> m = records(c);
        s = new Snapshot(m, sort(c), Categories.sorts(c), Categories.order(c));
        s.version = v;
        snap = s;
        return s;
    }

    // ---- the settings -------------------------------------------------------------------------------------------------

    /** Appearance › TV guide › Channels sorting: how every list is sorted (a group can have its own). */
    public static int sort(Context c) {
        records(c);
        return sort;
    }

    public static void setSort(Context c, int s) {
        int v = PlayerPrefs.clamp(s, PLAYLIST, MANUAL);
        records(c);
        prefs(c).edit().putInt(SORT, v).apply();
        sort = v;
        changed(true);
    }

    /** Appearance › TV guide › Channel names editor: Rename channel in a channel's menu (on as it comes). */
    public static boolean editor(Context c) {
        Boolean b = editor;
        if (b == null) editor = b = prefs(c).getBoolean(EDITOR, true);
        return b;
    }

    public static void setEditor(Context c, boolean on) {
        editor = on;
        prefs(c).edit().putBoolean(EDITOR, on).apply();
    }

    // ---- edits ----------------------------------------------------------------------------------------------------------

    public static void setHidden(Context c, String key, boolean hide) {
        edit(c, key, r -> r.hidden(hide), true);
    }

    /** Every channel hidden shown again. */
    public static void showAll(Context c) {
        synchronized (ChannelEdits.class) {
            Map<String, Rec> m = new LinkedHashMap<>(records(c));
            boolean any = false;
            for (Map.Entry<String, Rec> e : new ArrayList<>(m.entrySet())) {
                if (!e.getValue().hidden) continue;
                any = true;
                Rec r = e.getValue().hidden(false);
                if (r.empty()) m.remove(e.getKey());
                else m.put(e.getKey(), r);
            }
            if (!any) return;
            save(c, m);
        }
        changed(true);
    }

    /** A name of the family's own ("" or the playlist's own: back to the playlist's), at most NAME_MAX long. */
    public static void rename(Context c, Channel ch, String name) {
        if (ch == null) return;
        String v = tidy(name);
        if (v.equals(ch.name)) v = "";
        String n = v;
        // Its new logo looked for again by the new name too.
        com.gathin.homefieldtv.engine.LogoPack.forget(ch.key);
        edit(c, ch.key, r -> r.named(n), true);
    }

    /** The logo chosen for a channel: "" as Settings say, "-" the playlist's, or a new logo's path. */
    public static void setLogo(Context c, String key, String choice) {
        String v = choice == null ? "" : choice.replace('\t', ' ').replace('\n', ' ').trim();
        edit(c, key, r -> r.logo(v), false);
    }

    /** A group's channels in this order (Move channel): each one's place kept, the others' left as they were. */
    public static void order(Context c, List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        synchronized (ChannelEdits.class) {
            Map<String, Rec> m = new LinkedHashMap<>(records(c));
            for (int i = 0; i < keys.size(); i++) {
                String k = keys.get(i);
                Rec r = m.get(k);
                m.put(k, (r == null ? NONE : r).placed(i));
            }
            save(c, m);
        }
        changed(true);
    }

    private interface Change {
        Rec apply(Rec r);
    }

    private static void edit(Context c, String key, Change change, boolean lists) {
        if (key == null || key.isEmpty()) return;
        synchronized (ChannelEdits.class) {
            Map<String, Rec> m = new LinkedHashMap<>(records(c));
            Rec was = m.get(key);
            Rec now = change.apply(was == null ? NONE : was);
            if (now.empty()) m.remove(key);
            else m.put(key, now);
            save(c, m);
        }
        changed(lists);
    }

    private static void save(Context c, Map<String, Rec> m) {
        prefs(c).edit().putString(RECORDS, write(m)).apply();
        recs = Collections.unmodifiableMap(m);
    }

    private static void changed(boolean lists) {
        if (lists) Shown.changed();
        com.gathin.homefieldtv.engine.LogoPack.changed();
    }

    // ---- pure ---------------------------------------------------------------------------------------------------------

    /** Pure: a name as kept: on one line, spaces tidied, at most NAME_MAX long. */
    public static String tidy(String name) {
        if (name == null) return "";
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch)) {
                space = b.length() > 0;
                continue;
            }
            if (space) b.append(' ');
            space = false;
            b.append(ch);
        }
        String v = b.toString();
        return v.length() > NAME_MAX ? v.substring(0, NAME_MAX).trim() : v;
    }

    /** Pure: the records as kept, a line a channel. */
    static String write(Map<String, Rec> m) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Rec> e : m.entrySet()) {
            Rec r = e.getValue();
            if (r.empty()) continue;
            b.append(cell(e.getKey())).append('\t').append(r.hidden ? "1" : "").append('\t').append(r.place >= 0 ? String.valueOf(r.place) : "")
                    .append('\t').append(cell(r.name)).append('\t').append(cell(r.logo)).append('\n');
        }
        return b.toString();
    }

    /** Pure: the records read back (lines that can't be read left out). */
    static Map<String, Rec> parse(String text) {
        Map<String, Rec> out = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return out;
        for (String line : text.split("\n")) {
            if (line.isEmpty()) continue;
            String[] c = line.split("\t", -1);
            if (c.length < 5 || c[0].isEmpty()) continue;
            int place = -1;
            if (!c[2].isEmpty()) {
                try {
                    place = Integer.parseInt(c[2]);
                } catch (NumberFormatException e) {
                    place = -1;
                }
            }
            Rec r = new Rec("1".equals(c[1]), place, c[3], c[4]);
            if (!r.empty()) out.put(c[0], r);
        }
        return out;
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    /** Pure: the keys of a list of channels, in its order (for order()). */
    public static List<String> keys(Collection<Channel> list) {
        List<String> out = new ArrayList<>(list.size());
        for (Channel c : list) out.add(c.key);
        return out;
    }

    /** Pure: the records, for the tests. */
    static Map<String, Rec> records() {
        Map<String, Rec> m = recs;
        return m == null ? new HashMap<>() : m;
    }
}
