package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which copy each film or episode was started on, so that Resume and Continue watching carry on from that copy, as the
 * phone's do. Kept on this TV only (not synced, not in a backup), and never an address: TorBox's copy by its file's
 * ids ("torbox:torrent:12:3", no key in it), a playlist's by a digest of its playlist and where it plays.
 */
public final class Copies {
    private Copies() {}

    static final int MAX = 300;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("copies", Context.MODE_PRIVATE);
    }

    /** Pure: how a copy is known again without its address. */
    static String id(Catalog.Play p) {
        if (p == null) return "";
        if (p.torbox || TorBox.ours(p.url)) return p.url;
        return "iptv:" + digest(p.playlist + "\n" + p.url);
    }

    private static String digest(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(String.format(Locale.ROOT, "%02x", h[i]));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String key(String title, String episode) {
        return (title == null ? "" : title) + "|" + (episode == null ? "" : episode);
    }

    /** The copy a film or episode was started on, kept (the oldest forgotten past MAX). */
    public static synchronized void started(Context c, String title, String episode, Catalog.Play p) {
        if (title == null || title.isEmpty() || p == null) return;
        SharedPreferences sp = prefs(c);
        SharedPreferences.Editor e = sp.edit().putString(key(title, episode), System.currentTimeMillis() + "|" + id(p));
        Map<String, ?> all = sp.getAll();
        if (all.size() >= MAX) {
            List<Map.Entry<String, ?>> old = new ArrayList<>(all.entrySet());
            old.sort((a, b) -> Long.compare(at(a.getValue()), at(b.getValue())));
            for (int i = 0; i <= all.size() - MAX; i++) e.remove(old.get(i).getKey());
        }
        e.apply();
    }

    private static long at(Object v) {
        String s = v instanceof String ? (String) v : "";
        int bar = s.indexOf('|');
        try {
            return bar > 0 ? Long.parseLong(s.substring(0, bar)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Where among the copies the one it was started on is, or -1 (none kept, or it has gone). */
    public static int find(Context c, String title, String episode, List<Catalog.Play> plays) {
        String v = prefs(c).getString(key(title, episode), null);
        if (v == null) return -1;
        int bar = v.indexOf('|');
        return indexOf(bar < 0 ? v : v.substring(bar + 1), plays);
    }

    /** Pure: the place of the copy known by this id among the copies, or -1. */
    static int indexOf(String id, List<Catalog.Play> plays) {
        if (id == null || id.isEmpty()) return -1;
        for (int i = 0; i < plays.size(); i++) if (id.equals(id(plays.get(i)))) return i;
        return -1;
    }

    /** Pure: the copies with the one chosen first, the rest in their order (as the phone's chosenFirst). */
    public static <T> List<T> chosenFirst(List<T> plays, int chosen) {
        List<T> out = new ArrayList<>(plays);
        if (chosen > 0 && chosen < out.size()) out.add(0, out.remove(chosen));
        return out;
    }

    /** Remove everything: forgotten with the rest. */
    public static synchronized void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
