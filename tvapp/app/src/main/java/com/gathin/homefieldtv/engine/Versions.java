package com.gathin.homefieldtv.engine;

import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One channel's quality versions made one (Settings › Channels &amp; categories › One row per channel, on as it comes):
 * "ITV 1 FHD", "ITV 1 HD" and "ITV 1 SD" in one category are one channel, shown in one row, its best version played
 * (4K/UHD, then FHD, then HD, then a name that says nothing of its picture, then SD; ties in the playlist's order) and
 * the others under its Quality choice (the guide's hold-OK card, the player's Video tracks).
 *
 * <p>Which channels are versions of one (group, pure; worked out once a load, off the main thread, in Lists.Index):
 * <ul>
 *   <li>in the same category (the playlist's group: versions never leave it);</li>
 *   <li>by the guide id where they share one; two with different guide ids are never one (a channel and its HD-only twin);
 *   <li>else by the name made comparable as LogoMatch makes it: the quality tags anywhere (FHD, HD, SD, UHD, 4K, HEVC,
 *       H.265, 50 FPS, RAW…) and a "+" after one ("HD+") gone, the country tags at either end ("UK:", "|UK|", "[UK]") and
 *       a list number before it gone, One as 1, Sports as Sport, a few aliases (ITV as ITV1, C4 as Channel 4); its other
 *       words kept: a "+1" (or "+24") is another channel, as is another number (Sky Sports 1 and 2) or a region (BBC One
 *       and BBC One Wales).</li>
 * </ul>
 *
 * <p>Each version of a channel in more than one keeps the same Versions (Channel.versions: its versions best first and
 * the channel's name without its quality tags, "ITV 1"); a channel in one version keeps none. Whether the lists show
 * them as one is the setting's (of() reads it); with it off every version is a channel of its own, as before.
 */
public final class Versions {
    /** The levels a name says (as Ladder has them): SD, a name that says none, HD, FHD, UHD. */
    public static final int SD = 0, PLAIN = 1, HD = 2, FHD = 3, UHD = 4;

    /** The versions, best first. */
    public final Channel[] all;
    /** The channel's name without its quality tags, from its best version's ("UK: ITV 1 FHD" → "UK: ITV 1"). */
    public final String title;

    Versions(Channel[] all, String title) {
        this.all = all;
        this.title = title;
    }

    /** How many versions. */
    public int size() {
        return all.length;
    }

    /** Whether a key is one of these versions'. */
    public boolean has(String key) {
        if (key == null) return false;
        for (Channel c : all) if (c.key.equals(key)) return true;
        return false;
    }

    /** A channel's versions while the lists make them one (the setting on); null: it comes in one version, or the setting is off. */
    public static Versions of(Channel c) {
        if (c == null) return null;
        Versions v = c.versions;
        return v != null && ChannelEdits.merging() ? v : null;
    }

    // ---- what a name says --------------------------------------------------------------------------------------------

    /** Quality tags (and a backup feed's), wherever they are in a name: left out of its key and its title. */
    private static final Set<String> QUALITY = new HashSet<>(Arrays.asList("hd", "fhd", "uhd", "4k", "8k", "sd", "raw", "hevc",
            "h264", "h265", "x264", "x265", "avc", "1080", "1080p", "1080i", "720", "720p", "576", "576p", "576i", "2160", "2160p",
            "50fps", "60fps", "25fps", "30fps", "fps", "hdr", "sdr", "hq", "lq", "vip", "multiaudio", "backup", "bk", "uhd4k", "4khdr",
            "fullhd", "geo", "geoblocked", "orig", "original", "hd1080", "hd720"));
    private static final Set<String> LEVEL_UHD = new HashSet<>(Arrays.asList("uhd", "4k", "8k", "uhd4k", "4khdr", "2160", "2160p"));
    private static final Set<String> LEVEL_FHD = new HashSet<>(Arrays.asList("fhd", "fullhd", "1080", "1080p", "1080i", "hd1080", "raw"));
    private static final Set<String> LEVEL_HD = new HashSet<>(Arrays.asList("hd", "720", "720p", "hd720", "hq"));
    private static final Set<String> LEVEL_SD = new HashSet<>(Arrays.asList("sd", "576", "576p", "576i", "lq", "low"));

    /** Pure: the level a name says (UHD, FHD, HD, SD; PLAIN when it says none). */
    public static int level(String name) {
        List<String> t = words(name == null ? "" : Names.plain(name).toLowerCase(Locale.ROOT));
        boolean uhd = false, fhd = false, hd = false, sd = false;
        for (int i = 0; i < t.size(); i++) {
            String w = t.get(i), next = i + 1 < t.size() ? t.get(i + 1) : "";
            if (w.equals("ultra") && next.equals("hd")) uhd = true;
            else if (w.equals("full") && next.equals("hd")) fhd = true;
            else if (LEVEL_UHD.contains(w)) uhd = true;
            else if (LEVEL_FHD.contains(w)) fhd = true;
            else if (LEVEL_HD.contains(w)) hd = true;
            else if (LEVEL_SD.contains(w)) sd = true;
        }
        return uhd ? UHD : fhd ? FHD : hd ? HD : sd ? SD : PLAIN;
    }

    /** Pure: a level as the Quality choice and the note write it ("4K", "FHD", "HD", "SD"; "" for a name that says none). */
    public static String badge(int level) {
        switch (level) {
            case UHD: return "4K";
            case FHD: return "FHD";
            case HD: return "HD";
            case SD: return "SD";
            default: return "";
        }
    }

    /**
     * Pure: a name made comparable for its versions, as LogoMatch makes a name for its logo: "UK: ITV 1 FHD", "|UK| ITV1
     * HD+" and "ITV 1 SD" all "itv1"; "ITV 1 +1" is "itv1plus", "BBC One Wales" "bbc1wales". "" when nothing is left.
     */
    public static String key(String name) {
        if (name == null || name.isEmpty()) return "";
        return plainKey(Names.plain(name).toLowerCase(Locale.ROOT));
    }

    /** Pure: the same for a name already plain and lower case (as the lists keep every channel's: Lists.Index.lower). */
    static String plainKey(String lower) {
        if (lower == null || lower.isEmpty()) return "";
        String k = LogoMatch.plainName(unplus(lower), null).key;
        String a = LogoMatch.alias(k);
        return a != null ? a : k;
    }

    /** Pure: a "+" straight after a quality tag ("HD+", "FHD+") taken out: a better picture's mark, not a timeshift ("HD+1" keeps it). */
    static String unplus(String s) {
        int plus = s.indexOf('+');
        if (plus < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '+' && !(i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                int start = i;
                while (start > 0 && Character.isLetterOrDigit(s.charAt(start - 1))) start--;
                if (start < i && QUALITY.contains(s.substring(start, i).toLowerCase(Locale.ROOT))) continue;
            }
            b.append(c);
        }
        return b.toString();
    }

    /**
     * Pure: a name without its quality tags, as the family reads it: "UK: ITV 1 FHD" → "UK: ITV 1", "BBC TWO (HD)" → "BBC
     * TWO", "Sky Sports F1 ᴴᴰ" → "Sky Sports F1", "ITV 1 HD+" → "ITV 1"; the name itself when nothing else is left.
     */
    public static String title(String name) {
        if (name == null) return "";
        int n = name.length();
        boolean[] cut = new boolean[n];
        List<int[]> w = new ArrayList<>();
        for (int i = 0; i < n; ) {
            if (!Character.isLetterOrDigit(name.charAt(i))) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && Character.isLetterOrDigit(name.charAt(i))) i++;
            w.add(new int[]{s, i});
        }
        boolean any = false;
        for (int k = 0; k < w.size(); k++) {
            String t = lower(name, w.get(k)), next = k + 1 < w.size() ? lower(name, w.get(k + 1)) : "";
            int span = 0;
            if (QUALITY.contains(t)) span = 1;
            if ((t.equals("h") || t.equals("x")) && (next.equals("264") || next.equals("265"))
                    || isDigits(t) && next.equals("fps") || (t.equals("full") || t.equals("ultra")) && next.equals("hd")) span = 2;
            if (span == 0) continue;
            int from = w.get(k)[0], to = w.get(k + span - 1)[1];
            // A "+" straight after it ("HD+"), not a timeshift's ("+1").
            while (to < n && name.charAt(to) == '+' && !(to + 1 < n && Character.isDigit(name.charAt(to + 1)))) to++;
            for (int i = from; i < to; i++) cut[i] = true;
            any = true;
            k += span - 1;
        }
        if (!any) return name.trim();
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) if (!cut[i]) b.append(name.charAt(i));
        String out = tidy(b.toString());
        for (int i = 0; i < out.length(); i++) if (Character.isLetterOrDigit(out.charAt(i))) return out;
        return name.trim();
    }

    private static String lower(String s, int[] span) {
        return Names.plain(s.substring(span[0], span[1])).toLowerCase(Locale.ROOT);
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /** What is left after the tags are cut: empty brackets gone, spaces one, no mark left hanging at the ends. */
    private static String tidy(String s) {
        String t = s;
        for (String pair : new String[]{"()", "[]", "{}"}) {
            int at;
            while ((at = emptyPair(t, pair.charAt(0), pair.charAt(1))) >= 0) {
                int close = t.indexOf(pair.charAt(1), at);
                t = t.substring(0, at) + t.substring(close + 1);
            }
        }
        StringBuilder b = new StringBuilder(t.length());
        boolean space = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) {
                space = b.length() > 0;
                continue;
            }
            if (space) b.append(' ');
            space = false;
            b.append(c);
        }
        String o = b.toString();
        int end = o.length();
        while (end > 0 && " -|/:·•_,.".indexOf(o.charAt(end - 1)) >= 0) end--;
        int start = 0;
        while (start < end && " -/·•_,.".indexOf(o.charAt(start)) >= 0) start++;
        return o.substring(start, end).trim();
    }

    /** Where an opening mark with only spaces before its closing one is, or -1. */
    private static int emptyPair(String s, char open, char close) {
        for (int i = s.indexOf(open); i >= 0; i = s.indexOf(open, i + 1)) {
            int j = i + 1;
            while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
            if (j < s.length() && s.charAt(j) == close) return i;
        }
        return -1;
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                cur.append(c);
            } else if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ---- which channels are one ------------------------------------------------------------------------------------------

    /**
     * Pure: the channels' versions, by their places: for each channel, its channel's places best first (one array shared
     * by all its versions), or null for a channel in one version. Each channel in more than one is given its Versions
     * (Channel.versions); the others' are taken away. Never across categories, never two guide ids.
     */
    public static int[][] group(List<Channel> channels) {
        return group(channels, null);
    }

    /** The same, with each channel's name already plain and lower case (Lists.Index.lower; null: made here). */
    static int[][] group(List<Channel> channels, String[] lower) {
        int n = channels.size();
        int[][] out = new int[n][];
        // The channels by category and key, in the playlist's order.
        Map<String, int[]> buckets = new LinkedHashMap<>();
        int[] next = new int[n];
        Arrays.fill(next, -1);
        int[] last = new int[n];
        String[] keys = new String[n];
        for (int i = 0; i < n; i++) {
            Channel c = channels.get(i);
            c.versions = null;
            String k = lower != null && i < lower.length ? plainKey(lower[i]) : key(c.name);
            keys[i] = k;
            if (k.isEmpty()) continue;
            String b = c.group + '\u0001' + k;
            int[] head = buckets.get(b);
            if (head == null) {
                buckets.put(b, new int[]{i, i});
            } else {
                next[head[1]] = i;
                head[1] = i;
            }
        }
        for (int[] head : buckets.values()) {
            if (head[0] == head[1]) continue;
            // By guide id: each id its own channel; those with none join the first (and give it theirs).
            List<String> ids = new ArrayList<>(2);
            List<List<Integer>> sets = new ArrayList<>(2);
            for (int i = head[0]; i >= 0; i = next[i]) {
                String id = channels.get(i).tvgId.trim().toLowerCase(Locale.ROOT);
                int at = -1;
                if (id.isEmpty()) {
                    at = sets.isEmpty() ? -1 : 0;
                } else {
                    at = ids.indexOf(id);
                    if (at < 0) at = ids.indexOf("");
                    if (at >= 0) ids.set(at, id);
                }
                if (at < 0) {
                    ids.add(id);
                    sets.add(new ArrayList<>(3));
                    at = sets.size() - 1;
                }
                sets.get(at).add(i);
            }
            for (List<Integer> s : sets) {
                if (s.size() < 2) continue;
                Integer[] o = s.toArray(new Integer[0]);
                Map<Integer, Integer> levelOf = new HashMap<>();
                for (Integer p : o) levelOf.put(p, level(channels.get(p).name));
                // Best first; ties in the playlist's order.
                Arrays.sort(o, (a, b) -> {
                    int la = levelOf.get(a), lb = levelOf.get(b);
                    return la != lb ? Integer.compare(lb, la) : Integer.compare(a, b);
                });
                int[] places = new int[o.length];
                Channel[] all = new Channel[o.length];
                for (int q = 0; q < o.length; q++) {
                    places[q] = o[q];
                    all[q] = channels.get(o[q]);
                }
                Versions v = new Versions(all, title(all[0].name));
                for (int p : places) {
                    out[p] = places;
                    channels.get(p).versions = v;
                }
            }
        }
        return out;
    }
}
