package com.gathin.homefieldtv.engine;

import java.util.Arrays;
import java.util.List;

/**
 * Every film's and series' name in the family's playlists, made findable, so TMDB's lists (trending, popular, a
 * network's shows) can be matched to what the playlists hold: "The Long Harbour (2019)" and TMDB's "The Long Harbour"
 * alike, a year apart at most for a film. Made once for each catalogue, off the main thread, and small: one long per
 * name (its squashed name's hash and the title's place, sorted), about 1.6 MB for 200,000 titles, with no string kept.
 * A name found by its hash is checked letter by letter against the title's own, so two names never get mixed up.
 */
public final class MatchIndex {
    /** The low bits of each entry: the title's place in its list (up to 2 million titles a kind). */
    private static final int PLACE_BITS = 21;
    private static final long PLACE = (1L << PLACE_BITS) - 1, HASH = ~PLACE;

    private final List<Catalog.Title> films, series;
    private final long[] filmKeys, seriesKeys;

    private MatchIndex(List<Catalog.Title> films, List<Catalog.Title> series) {
        this.films = films;
        this.series = series;
        this.filmKeys = keys(films);
        this.seriesKeys = keys(series);
    }

    private static MatchIndex last;

    /**
     * The index of the catalogue as it is now: the one made before while its lists are the same, else made now. Goes
     * through every title the first time: never on the main thread.
     */
    public static synchronized MatchIndex of(Catalog cat) {
        List<Catalog.Title> f = cat.films(), s = cat.series();
        if (last == null || last.films != f || last.series != s) last = new MatchIndex(f, s);
        return last;
    }

    /** The index made last let go (the catalogue it holds was let go: Catalog.sleep); made again when next asked. */
    static synchronized void forget() {
        last = null;
    }

    /** Pure, for the tests: an index of these lists (made now, whatever was made before). */
    public static MatchIndex of(List<Catalog.Title> films, List<Catalog.Title> series) {
        return new MatchIndex(films, series);
    }

    public int size() {
        return filmKeys.length + seriesKeys.length;
    }

    private static long[] keys(List<Catalog.Title> list) {
        int n = Math.min(list.size(), (int) PLACE);
        long[] out = new long[n * 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            String name = list.get(i).name;
            long h = hash(name, false);
            if (h == 0) continue;
            out[k++] = (h & HASH) | i;
            // "The Office (US)", "Doctor Who (2005)": also found by the name before its brackets.
            long b = hash(name, true);
            if (b != 0 && b != h) out[k++] = (b & HASH) | i;
        }
        long[] keys = Arrays.copyOf(out, k);
        Arrays.sort(keys);
        return keys;
    }

    /**
     * The playlists' title TMDB's answer is (series or a film; its name, its original name, its year, 0 for none): one
     * whose name squashes the same and whose year is no more than a year apart (a film; any year for a series, or where
     * either says none), the closest year first; null when the playlists don't have it.
     */
    public Catalog.Title find(boolean tv, String name, String original, int year) {
        Catalog.Title t = find(tv, name, year);
        if (t == null && original != null && !original.isEmpty() && !original.equals(name)) t = find(tv, original, year);
        return t;
    }

    private Catalog.Title find(boolean tv, String name, int year) {
        long h = hash(name, false);
        if (h == 0) return null;
        long[] keys = tv ? seriesKeys : filmKeys;
        List<Catalog.Title> list = tv ? series : films;
        long want = h & HASH;
        int i = lower(keys, want);
        Catalog.Title best = null;
        int bestGap = Integer.MAX_VALUE;
        for (; i < keys.length && (keys[i] & HASH) == want; i++) {
            Catalog.Title t = list.get((int) (keys[i] & PLACE));
            if (!same(t.name, name)) continue;
            int y = year(t.year);
            int gap = year == 0 || y == 0 ? 1 : Math.abs(y - year);
            if (!tv && gap > 1) continue;
            if (gap < bestGap) {
                best = t;
                bestGap = gap;
            }
        }
        return best;
    }

    /** The first place in keys at or after want (keys sorted). */
    private static int lower(long[] keys, long want) {
        int lo = 0, hi = keys.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid] < want) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Pure: whether two names squash the same (either with its brackets, or without them). */
    static boolean same(String a, String b) {
        String sa = squash(a, false), sb = squash(b, false);
        if (sa.isEmpty() || sb.isEmpty()) return false;
        if (sa.equals(sb)) return true;
        String ba = squash(a, true), bb = squash(b, true);
        return !ba.isEmpty() && (ba.equals(sb) || bb.equals(sa) || ba.equals(bb));
    }

    private static int year(String y) {
        if (y == null || y.length() < 4) return 0;
        int v = 0;
        for (int i = 0; i < 4; i++) {
            char c = y.charAt(i);
            if (c < '0' || c > '9') return 0;
            v = v * 10 + (c - '0');
        }
        return v;
    }

    // ---- a name squashed ----------------------------------------------------------------------------------------

    /**
     * Pure: a name as compared: letters and digits only, lower case, accents dropped (the common ones), "&" as "and",
     * apostrophes gone, a leading "The" dropped; cut: what is before its first "(" or "[" only ("The Office (US)" as
     * "office"). Written by hand: made for every title, it allocates nothing but its answer.
     */
    static String squash(String name, boolean cut) {
        StringBuilder b = new StringBuilder(name == null ? 0 : name.length());
        walk(name, cut, b, null);
        return b.toString();
    }

    /** Pure: the squashed name's 64-bit hash (FNV-1a), 0 for a name with no letters or digits. */
    static long hash(String name, boolean cut) {
        long[] h = {0xcbf29ce484222325L, 0};
        walk(name, cut, null, h);
        return h[1] == 0 ? 0 : h[0] == 0 ? 1 : h[0];
    }

    /** Through the name once: each kept character to out (when given) and into the hash (when given). */
    private static void walk(String name, boolean cut, StringBuilder out, long[] hash) {
        if (name == null) return;
        int n = name.length();
        // A leading "the " (any case) is left out.
        int start = 0;
        while (start < n && !word(name.charAt(start))) start++;
        if (n - start > 4 && lower(name.charAt(start)) == 't' && lower(name.charAt(start + 1)) == 'h'
                && lower(name.charAt(start + 2)) == 'e' && !word(name.charAt(start + 3)) && name.charAt(start + 3) != '\'') {
            start += 3;
        }
        boolean any = false;
        for (int i = start; i < n; i++) {
            char c = name.charAt(i);
            if (cut && (c == '(' || c == '[') && any) break;
            if (c == '&') {
                put('a', out, hash);
                put('n', out, hash);
                put('d', out, hash);
                any = true;
                continue;
            }
            char k = plain(c);
            if (k == 0) continue;
            put(k, out, hash);
            any = true;
        }
    }

    private static void put(char c, StringBuilder out, long[] hash) {
        if (out != null) out.append(c);
        if (hash != null) {
            hash[0] = (hash[0] ^ c) * 0x100000001b3L;
            hash[1]++;
        }
    }

    private static boolean word(char c) {
        return plain(c) != 0;
    }

    private static char lower(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
    }

    /** A character as kept: a to z and 0 to 9 (accented Latin letters as their plain one), else 0. */
    private static char plain(char c) {
        if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') return c;
        if (c >= 'A' && c <= 'Z') return (char) (c + 32);
        if (c < 0xC0 || c > 0x17F) return 0;
        return ACCENTS.charAt(c - 0xC0) == '.' ? 0 : ACCENTS.charAt(c - 0xC0);
    }

    /** U+00C0 to U+017F as plain letters ('.' for none). */
    private static final String ACCENTS =
            "aaaaaaaceeeeiiii" + "dnooooo.ouuuuyts" + "aaaaaaaceeeeiiii" + "dnooooo.ouuuuyty"
                    + "aaaaaaccccccccdd" + "ddeeeeeeeeeegggg" + "gggghhhhiiiiiiii" + "iijjjjkkklllllll"
                    + "lllnnnnnnnnnoooo" + "oooorrrrrrssssss" + "ssttttttuuuuuuuu" + "uuuuwwyyyzzzzzzs";
}
