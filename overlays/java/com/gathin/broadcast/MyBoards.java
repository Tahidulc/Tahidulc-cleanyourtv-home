package com.gathin.broadcast;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure: the family's saved scoreboards (My scoreboards), the same on every device: each one an id ("b" and twelve hex
 * digits, made where it was first saved and kept everywhere it goes), its name and its spec. The apps keep them where
 * they keep their synced things (the phone's SyncStore, the TV launcher's SyncHub) as items of type "myboard" keyed by
 * the id, with {name, spec, at} as their data; this names them, copies them and reads them back safely.
 */
public final class MyBoards {
    private MyBoards() {
    }

    /** The sync items' type. */
    public static final String TYPE = "myboard";
    /** At most this many kept (one more is turned away). */
    public static final int MAX = 60;

    /** One saved board. */
    public static final class Saved {
        public final String id;
        public final String name;
        public final BoardSpec spec;
        /** When it was saved or last changed (ms). */
        public final long at;

        public Saved(String id, String name, BoardSpec spec, long at) {
            this.id = id;
            this.spec = spec == null ? new BoardSpec() : spec;
            this.spec.name = clean(name == null ? this.spec.name : name);
            this.name = this.spec.name;
            this.at = at;
        }

        /** Its data as a sync item carries it. */
        public JSONObject data() {
            JSONObject o = new JSONObject();
            try {
                o.put("name", name).put("spec", spec.json()).put("at", at);
            } catch (JSONException e) {
                // plain values
            }
            return o;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A new board's id. */
    public static String newId() {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        StringBuilder s = new StringBuilder("b");
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }

    /** Whether an id is one this makes (anything else from another device is turned away). */
    public static boolean validId(String id) {
        return id != null && id.matches("b[0-9a-f]{12}");
    }

    /** A saved board from a sync item's data (its key the id), or null when it can't be one. */
    public static Saved read(String id, JSONObject data) {
        if (!validId(id) || data == null) return null;
        JSONObject spec = data.optJSONObject("spec");
        if (spec == null) {
            String s = data.optString("spec", "");
            if (s.isEmpty()) return null;
            BoardSpec parsed = BoardSpec.parse(s);
            return new Saved(id, data.optString("name", parsed.name), parsed, data.optLong("at"));
        }
        BoardSpec parsed = BoardSpec.from(spec);
        return new Saved(id, data.optString("name", parsed.name), parsed, data.optLong("at"));
    }

    /** A name made plain and short (the spec's rules). */
    public static String clean(String name) {
        BoardSpec s = new BoardSpec();
        s.name = name;
        return s.validated().name;
    }

    /** The name for a copy of a board: "Night match copy", then "Night match copy 2", never one already taken. */
    public static String copyName(String name, Collection<String> taken) {
        String base = clean(name);
        String first = clean(base + " copy");
        if (!contains(taken, first)) return first;
        for (int i = 2; i < 100; i++) {
            String n = clean(base + " copy " + i);
            if (!contains(taken, n)) return n;
        }
        return first;
    }

    /** A name not taken: the name itself, else with " 2", " 3" after it. */
    public static String freeName(String name, Collection<String> taken) {
        String base = clean(name);
        if (!contains(taken, base)) return base;
        for (int i = 2; i < 100; i++) {
            String n = clean(base + " " + i);
            if (!contains(taken, n)) return n;
        }
        return base;
    }

    private static boolean contains(Collection<String> taken, String n) {
        if (taken == null) return false;
        for (String t : taken) if (t != null && t.equalsIgnoreCase(n)) return true;
        return false;
    }

    /** The names of saved boards. */
    public static List<String> names(Collection<Saved> boards) {
        List<String> out = new ArrayList<>();
        for (Saved s : boards) out.add(s.name);
        return out;
    }

    /** Newest first, then by name. */
    public static void sort(List<Saved> boards) {
        boards.sort((a, b) -> a.at != b.at ? Long.compare(b.at, a.at) : a.name.compareToIgnoreCase(b.name));
    }
}
