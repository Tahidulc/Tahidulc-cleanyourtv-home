package com.gathin.tvhomephone.live;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The guide's programme pictures (XMLTV's {@code <icon src>}, from the playlist's guide or the Xtream account's
 * xmltv.php), kept small however large the guide (the family's playlists: some 90,000 programmes): each address split
 * at its last '/' into its folder, kept once however many programmes share it (a guide's pictures come from a few), and
 * its file name, packed as bytes in one array; the same address twice is kept once. A programme holds an int into it
 * (-1: none), so a guide that gives no pictures costs nothing. The address is made again only when it is drawn.
 */
final class PicLinks {
    static final PicLinks EMPTY = new Builder().build();

    private final String[] heads;
    /** Per link: its folder (into heads) and where its file name starts in tails (the next one's start ends it). */
    private final int[] head, from;
    private final byte[] tails;
    private final int n;

    private PicLinks(String[] heads, int[] head, int[] from, byte[] tails, int n) {
        this.heads = heads;
        this.head = head;
        this.from = from;
        this.tails = tails;
        this.n = n;
    }

    int size() {
        return n;
    }

    /** The bytes kept for the file names (for the memory check). */
    int bytes() {
        return tails.length + 8 * n;
    }

    /** Link i made again ("" for none). */
    String get(int i) {
        if (i < 0 || i >= n) return "";
        int a = from[i], b = i + 1 < n ? from[i + 1] : tails.length;
        return heads[head[i]] + new String(tails, a, b - a, StandardCharsets.UTF_8);
    }

    /**
     * Pure: a picture's address as a guide gives it, made one to fetch: trimmed, "//host/x.jpg" given https, http and
     * https only (anything else, or nothing: "").
     */
    static String clean(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("//")) t = "https:" + t;
        String low = t.length() > 8 ? t.substring(0, 8).toLowerCase(Locale.ROOT) : t.toLowerCase(Locale.ROOT);
        if (!(low.startsWith("http://") || low.startsWith("https://"))) return "";
        if (t.indexOf(' ') >= 0) t = t.replace(" ", "%20");
        return t.length() > 12 && t.indexOf('/', 8) > 0 ? t : "";
    }

    /** Collects the links as the guide is read (one reader's thread). */
    static final class Builder {
        private final Map<String, Integer> seen = new HashMap<>(), headIndex = new HashMap<>();
        private final List<String> heads = new ArrayList<>();
        private int[] head = new int[64], from = new int[64];
        private byte[] tails = new byte[1024];
        private int n, used;

        /** An address added (cleaned): its index, or -1 for none. */
        int add(String url) {
            String u = clean(url);
            if (u.isEmpty()) return -1;
            Integer had = seen.get(u);
            if (had != null) return had;
            int cut = u.lastIndexOf('/') + 1;
            String h = u.substring(0, cut);
            Integer hi = headIndex.get(h);
            if (hi == null) {
                hi = heads.size();
                heads.add(h);
                headIndex.put(h, hi);
            }
            byte[] t = u.substring(cut).getBytes(StandardCharsets.UTF_8);
            if (n == head.length) {
                head = Arrays.copyOf(head, n * 2);
                from = Arrays.copyOf(from, n * 2);
            }
            if (used + t.length > tails.length) tails = Arrays.copyOf(tails, Math.max(tails.length * 2, used + t.length));
            head[n] = hi;
            from[n] = used;
            System.arraycopy(t, 0, tails, used, t.length);
            used += t.length;
            seen.put(u, n);
            return n++;
        }

        PicLinks build() {
            return new PicLinks(heads.toArray(new String[0]), Arrays.copyOf(head, n), Arrays.copyOf(from, n), Arrays.copyOf(tails, used), n);
        }
    }
}
