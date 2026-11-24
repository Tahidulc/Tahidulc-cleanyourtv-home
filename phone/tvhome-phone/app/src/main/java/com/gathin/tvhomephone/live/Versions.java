package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One row per channel (Settings › Channels &amp; categories, on as it comes), as Homefield TV has it: a playlist's "ITV 1
 * FHD", "ITV 1 HD" and "ITV 1 SD" in one category are one channel, one row in Live TV under "ITV 1", its best version
 * (4K, then FHD, HD, a name that says none, then SD; ties in the playlist's order) the one shown and played unless the
 * family picks another (Quality, in the channel's menu and the player's options).
 *
 * <p>Pure. Which channels are versions of one: in the same category, with the same name once Names has made it
 * comparable (its quality and country tags gone; a "+1", another number or a region is another channel), both a backup
 * feed or neither, and never two different guide ids (those with none join the first).
 */
final class Versions {
    private Versions() {}

    /**
     * Each channel's versions, by place, best first (one array shared by all of them), or null for a channel in one
     * version. infos lines up with channels (Names.info of each one's playlist name).
     */
    static int[][] group(List<Channel> channels, Names.Info[] infos) {
        int n = channels.size();
        int[][] out = new int[n][];
        Map<String, List<Integer>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Names.Info in = i < infos.length ? infos[i] : null;
            if (in == null || in.key.isEmpty()) continue;
            String b = channels.get(i).group + '\u0001' + in.key + (in.backup ? "\u0001b" : "");
            List<Integer> l = buckets.get(b);
            if (l == null) buckets.put(b, l = new ArrayList<>(2));
            l.add(i);
        }
        for (List<Integer> l : buckets.values()) {
            if (l.size() < 2) continue;
            // By guide id: each id its own channel; those with none join the first (and give it theirs).
            List<String> ids = new ArrayList<>(2);
            List<List<Integer>> sets = new ArrayList<>(2);
            for (int i : l) {
                String id = channels.get(i).tvgId.trim().toLowerCase(Locale.ROOT);
                int at;
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
                Arrays.sort(o, (a, b) -> {
                    int la = infos[a].quality, lb = infos[b].quality;
                    return la != lb ? Integer.compare(lb, la) : Integer.compare(a, b);
                });
                int[] places = new int[o.length];
                for (int q = 0; q < o.length; q++) places[q] = o[q];
                for (int p : places) out[p] = places;
            }
        }
        return out;
    }

    /** A level as a phone's Quality chip says it ("4K", "FHD", "HD", "SD"; "" for a name that says none). */
    static String badge(int level) {
        switch (level) {
            case 4: return "4K";
            case 3: return "FHD";
            case 2: return "HD";
            case 0: return "SD";
            default: return "";
        }
    }

    /**
     * The words on a channel's Quality chips, one per version: its quality alone ("FHD", "HD", "SD") where that tells
     * them apart, else each version's own name, as the playlist gives it.
     */
    static String[] labels(List<Channel> versions) {
        String[] out = new String[versions.size()];
        java.util.Set<String> seen = new HashSet<>();
        boolean apart = true;
        for (int i = 0; i < out.length; i++) {
            out[i] = badge(Names.info(versions.get(i).playlistName, versions.get(i).group).quality);
            if (out[i].isEmpty() || !seen.add(out[i])) apart = false;
        }
        if (!apart) for (int i = 0; i < out.length; i++) out[i] = versions.get(i).playlistName;
        return out;
    }

    /** Quality tags, wherever they are in a name: left out of the name the row shows. */
    private static final Set<String> QUALITY = new HashSet<>(Arrays.asList("hd", "fhd", "uhd", "4k", "8k", "sd", "raw", "hevc",
            "h264", "h265", "x264", "x265", "avc", "1080", "1080p", "1080i", "720", "720p", "576", "576p", "576i", "2160", "2160p",
            "50fps", "60fps", "25fps", "30fps", "fps", "hdr", "sdr", "hq", "lq", "uhd4k", "4khdr", "fullhd", "hd1080", "hd720", "ᴴᴰ"));

    /**
     * A name without its quality tags, as the family reads it: "UK: ITV 1 FHD" → "UK: ITV 1", "BBC TWO (HD)" → "BBC TWO",
     * "Sky Sports F1 ᴴᴰ" → "Sky Sports F1", "ITV 1 HD+" → "ITV 1"; the name itself when nothing else would be left.
     */
    static String title(String name) {
        if (name == null) return "";
        int n = name.length();
        boolean[] cut = new boolean[n];
        List<int[]> w = new ArrayList<>();
        for (int i = 0; i < n; ) {
            if (!word(name.charAt(i))) {
                i++;
                continue;
            }
            int s = i;
            while (i < n && word(name.charAt(i))) i++;
            w.add(new int[]{s, i});
        }
        boolean any = false;
        for (int k = 0; k < w.size(); k++) {
            String t = lower(name, w.get(k)), next = k + 1 < w.size() ? lower(name, w.get(k + 1)) : "";
            int span = 0;
            if (QUALITY.contains(t)) span = 1;
            if ((t.equals("h") || t.equals("x")) && (next.equals("264") || next.equals("265"))
                    || digits(t) && next.equals("fps") || (t.equals("full") || t.equals("ultra")) && next.equals("hd")) span = 2;
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

    /** A letter, a digit or the small ᴴᴰ mark, as one word's. */
    private static boolean word(char c) {
        return Character.isLetterOrDigit(c) || c == 'ᴴ' || c == 'ᴰ';
    }

    private static String lower(String s, int[] span) {
        return s.substring(span[0], span[1]).toLowerCase(Locale.ROOT);
    }

    private static boolean digits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    /** What is left once the tags are cut: empty brackets gone, single spaces, no mark hanging at either end. */
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

    private static int emptyPair(String s, char open, char close) {
        for (int i = s.indexOf(open); i >= 0; i = s.indexOf(open, i + 1)) {
            int j = i + 1;
            while (j < s.length() && Character.isWhitespace(s.charAt(j))) j++;
            if (j < s.length() && s.charAt(j) == close) return i;
        }
        return -1;
    }
}
