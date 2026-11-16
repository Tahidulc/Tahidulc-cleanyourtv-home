package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The channels last watched on this TV, newest first, for the row of recent channels in the channel banner and the
 * guide's Recently watched. One more than the row shows is kept, so the row still has its ten when the channel playing
 * is left out of it. Pure: Library keeps the line in the TV's own settings, and nothing of it leaves the TV.
 */
public final class RecentChannels {
    private RecentChannels() {}

    /** The most the row shows. */
    public static final int MAX = 10;

    /** The keys after a channel was watched: it first, once; past MAX + 1 the oldest go. */
    public static List<String> add(List<String> keys, String key) {
        List<String> out = new ArrayList<>(MAX + 1);
        if (key != null && !key.isEmpty()) out.add(key);
        if (keys != null) {
            for (String k : keys) {
                if (out.size() > MAX) break;
                if (k == null || k.isEmpty() || out.contains(k)) continue;
                out.add(k);
            }
        }
        return out;
    }

    /** The row while a channel plays: newest first, without the one playing (null for none), MAX at most. */
    public static List<String> shown(List<String> keys, String playing) {
        List<String> out = new ArrayList<>();
        if (keys == null) return out;
        for (String k : keys) {
            if (out.size() >= MAX) break;
            if (k == null || k.isEmpty() || k.equals(playing) || out.contains(k)) continue;
            out.add(k);
        }
        return out;
    }

    /** The line as it is kept: a key a line. */
    public static List<String> parse(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        for (String k : line.split("\n")) {
            String t = k.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    public static String join(List<String> keys) {
        StringBuilder b = new StringBuilder();
        if (keys == null) return "";
        for (String k : keys) {
            if (k == null || k.trim().isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(k.trim());
        }
        return b.toString();
    }
}
