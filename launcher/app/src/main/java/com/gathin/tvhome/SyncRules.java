package com.gathin.tvhome;

import java.util.HashSet;

import java.util.Collections;

import java.util.Arrays;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sync's rules without Android (SyncHub uses them; unit tested): last writer wins, finding what changed in the
 * TV's own stores since the last look, what goes out after a time, old tombstones, and the stores' records as
 * items' data and back (phone/tvhome-phone/SYNC.md).
 */
final class SyncRules {
    private SyncRules() { }

    static final long TOMBSTONE_MS = 60L * 24 * 3600_000L;

    /**
     * The kinds the phones, tablets and Homefield TV share through the TVs, which the TV only keeps to pass on (it has
     * no store of its own for them): the IPTV playlists, favourite channels, categories, recent channels, where each film
     * or episode is up to, and the settings every device has (SYNC.md).
     */
    static final Set<String> RELAYED = new HashSet<>(Arrays.asList("playlist", "livefav", "livecat", "liverecent", "resume", "setting"));
    /** Kinds that carry a login: only to and from a caller with a token, never between TVs on their own. */
    static final Set<String> SECRET = Collections.singleton("playlist");
    /** A relayed item's data at most this long (as JSON). */
    static final int RELAY_BYTES = 64 * 1024;

    /** At most this many of each relayed kind kept here (one more of a kind already full is turned away). */
    static int relayMax(String type) {
        switch (type) {
            case "playlist": return 30;
            case "livefav": return 3000;
            case "livecat": return 50;
            case "liverecent": return 10;
            case "resume": return 400;
            case "setting": return 300;
            default: return 0;
        }
    }

    /** Whether a relayed item may be kept: its kind, a key and data within bounds, and room when it is a new one. */
    static boolean relayFits(String type, String key, int bytes, boolean known, int countOfType) {
        if (!RELAYED.contains(type) || key == null || key.isEmpty() || key.length() > 200) return false;
        if (bytes <= 0 || bytes > RELAY_BYTES) return false;
        return known || countOfType < relayMax(type);
    }

    /** Whether an item of this kind goes to, or is taken from, a caller: a login only with a token. */
    static boolean shared(String type, boolean trusted) {
        return trusted || !SECRET.contains(type);
    }

    /** The token given against the one kept, in time that doesn't tell how much of it matched. */
    static boolean sameToken(String kept, String given) {
        if (kept == null || given == null || kept.isEmpty()) return false;
        byte[] a = kept.getBytes(java.nio.charset.StandardCharsets.UTF_8), b = given.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    /** What the TV remembers of one item: its data's fingerprint, when and where it last changed, when it got here. */
    static final class Meta {
        String hash = "";
        long updated, seen;
        boolean deleted;
        String from = "";

        Meta copy() {
            Meta m = new Meta();
            m.hash = hash;
            m.updated = updated;
            m.seen = seen;
            m.deleted = deleted;
            m.from = from;
            return m;
        }
    }

    /** Last writer wins: the later updated, then the greater from on a tie; anything beats nothing. */
    static boolean wins(long updatedIn, String fromIn, Meta had) {
        if (had == null) return true;
        if (updatedIn != had.updated) return updatedIn > had.updated;
        return (fromIn == null ? "" : fromIn).compareTo(had.from == null ? "" : had.from) > 0;
    }

    /** A short fingerprint of an item's data as the TV writes it. */
    static String hash(String canonical) {
        return canonical == null ? "" : canonical.length() + ":" + Integer.toHexString(canonical.hashCode());
    }

    /**
     * The TV's own stores looked at again: an item new or changed here is stamped now, from this TV; one gone from its
     * store becomes a tombstone, stamped now. Items in zeroIds (defaults the TV did not choose: its own starred clubs)
     * are stamped 0 when first seen, so any choice made anywhere beats them. The ids that changed, metas updated.
     */
    static List<String> scan(Map<String, String> current, Map<String, Meta> metas, long now, String me, Set<String> zeroIds) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> e : current.entrySet()) {
            String h = hash(e.getValue());
            Meta m = metas.get(e.getKey());
            if (m != null && !m.deleted && h.equals(m.hash)) continue;
            Meta n = new Meta();
            n.hash = h;
            boolean zero = zeroIds != null && zeroIds.contains(e.getKey()) && (m == null || m.updated == 0);
            n.updated = zero ? 0 : now;
            n.from = me;
            n.seen = now;
            metas.put(e.getKey(), n);
            changed.add(e.getKey());
        }
        for (Map.Entry<String, Meta> e : metas.entrySet()) {
            Meta m = e.getValue();
            if (m.deleted || current.containsKey(e.getKey())) continue;
            m.deleted = true;
            m.hash = "";
            m.updated = now;
            m.from = me;
            m.seen = now;
            changed.add(e.getKey());
        }
        return changed;
    }

    /** The ids whose change reached this TV after since (its own clock): what goes to a phone or the other TV. */
    static List<String> since(Map<String, Meta> metas, long since) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Meta> e : metas.entrySet()) if (e.getValue().seen > since) out.add(e.getKey());
        return out;
    }

    /** Tombstones older than 60 days dropped; true when any went. */
    static boolean prune(Map<String, Meta> metas, long now) {
        boolean any = false;
        for (Iterator<Map.Entry<String, Meta>> it = metas.entrySet().iterator(); it.hasNext(); ) {
            Meta m = it.next().getValue();
            if (m.deleted && now - m.updated > TOMBSTONE_MS) {
                it.remove();
                any = true;
            }
        }
        return any;
    }

    // ---- the stores' records as data, and back -----------------------------------------------------------------------

    static String id(String type, String key) {
        return type + "|" + key;
    }

    static String titleKey(boolean tv, int id) {
        return (tv ? "tv:" : "mv:") + id;
    }

    /** "tv:1399" or "mv:550" (a season rating "tv:1399:s2" too): {tv, id}, or null. */
    static Object[] parseTitleKey(String key) {
        if (key == null || key.length() < 4 || key.charAt(2) != ':') return null;
        boolean tv;
        if (key.startsWith("tv:")) tv = true;
        else if (key.startsWith("mv:")) tv = false;
        else return null;
        String rest = key.substring(3);
        int colon = rest.indexOf(':');
        if (colon >= 0) rest = rest.substring(0, colon);
        try {
            int id = Integer.parseInt(rest);
            return id > 0 ? new Object[]{tv, id} : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** My list's entry as an item's data (its "apps" mark stays on the TV). */
    static Map<String, Object> myListData(int id, boolean tv, String title, String poster, String backdrop, long added) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("tv", tv);
        d.put("title", title == null ? "" : title);
        d.put("poster", clean(poster));
        d.put("backdrop", clean(backdrop));
        d.put("added", added);
        return d;
    }

    /** A rating as kept by Ratings ("value|when|genre|name") as data: {value, title, at, genre}; null when unreadable. */
    static Map<String, Object> ratingData(String encoded) {
        Object[] r = WatchRules.decode(encoded);
        if (r == null) return null;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("value", r[0]);
        d.put("title", r[3]);
        d.put("at", r[1]);
        d.put("genre", r[2]);
        return d;
    }

    /** A rating's data as Ratings keeps it; null when its value is not one of the three. */
    static String ratingRecord(int value, long at, String genre, String title) {
        if (value != WatchRules.NOT_FOR_ME && value != WatchRules.LIKED && value != WatchRules.LOVED) return null;
        return WatchRules.encode(value, at, genre, title);
    }

    /**
     * A reminder's item key: the one it came with (a phone's "game:401", kept in the record), else one made from it
     * ("show:Sky Sports Main Event:1762300800000:Man Utd v Roma").
     */
    static String reminderKey(String kept, String channel, long start, String title) {
        if (kept != null && !kept.isEmpty()) return kept;
        return "show:" + (channel == null ? "" : channel) + ":" + start + ":" + (title == null ? "" : title);
    }

    static Map<String, Object> reminderData(String title, long start, String channel, String logo, String kind) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("title", title == null ? "" : title);
        d.put("at", start);
        d.put("kind", kind == null || kind.isEmpty() ? "show" : kind);
        d.put("channel", channel == null ? "" : channel);
        d.put("logo", logo == null ? "" : logo);
        return d;
    }

    /** A Film night plan's fields as data (what the phone shows and sets). */
    static Map<String, Object> planData(int id, String title, long start, int runtime, boolean lights, boolean remind,
                                        boolean quiet, String poster, String backdrop) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", id);
        d.put("title", title == null ? "" : title);
        d.put("start", start);
        d.put("runtime", runtime);
        d.put("lights", lights);
        d.put("remind", remind);
        d.put("quiet", quiet);
        d.put("poster", clean(poster));
        d.put("backdrop", clean(backdrop));
        return d;
    }

    /** A plain map written the same way every time (the fingerprint's text): JSON-like, in the map's own order. */
    static String canonical(Map<String, Object> d) {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : d.entrySet()) {
            if (!first) b.append(',');
            first = false;
            b.append('"').append(e.getKey()).append("\":");
            value(b, e.getValue());
        }
        return b.append('}').toString();
    }

    private static void value(StringBuilder b, Object v) {
        if (v == null) b.append("null");
        else if (v instanceof Number || v instanceof Boolean) b.append(v);
        else if (v instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object x : (List<?>) v) {
                if (!first) b.append(',');
                first = false;
                value(b, x);
            }
            b.append(']');
        } else {
            b.append('"').append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
    }

    private static String clean(String s) {
        return s == null || "null".equals(s) ? "" : s;
    }
}
