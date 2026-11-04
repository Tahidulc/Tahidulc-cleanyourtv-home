package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Live TV's search through the guide: every programme still to finish across its whole window, by title and (for
 * three letters or more, ranked lower) by description. Built once after the guide loads, as flat arrays with each title
 * lower-cased once, so a search over a large guide stays quick off the main thread. Ranked: the title exactly, then a
 * title that starts with it (or has a word that does), then elsewhere in a title, then a description; within each, what
 * is on now first, then the soonest.
 */
final class ProgrammeSearch {
    static final int EXACT = 0, WORD = 1, INSIDE = 2, DESCRIPTION = 3;

    private ProgrammeSearch() {}

    /** One programme found. */
    static final class Hit {
        final Channel channel;
        final Guide.Schedule schedule;
        final int programme;
        final int rank;
        final boolean onNow;
        final long start;
        final int order;

        Hit(Channel channel, Guide.Schedule schedule, int programme, int rank, boolean onNow, int order) {
            this.channel = channel;
            this.schedule = schedule;
            this.programme = programme;
            this.rank = rank;
            this.onNow = onNow;
            this.start = schedule.start[programme];
            this.order = order;
        }

        String title() { return schedule.title[programme]; }
    }

    static final Comparator<Hit> ORDER = (a, b) -> {
        if (a.rank != b.rank) return Integer.compare(a.rank, b.rank);
        if (a.onNow != b.onNow) return a.onNow ? -1 : 1;
        if (a.start != b.start) return Long.compare(a.start, b.start);
        return Integer.compare(a.order, b.order);
    };

    /** The guide's programmes flattened: for each, its channel (by position in the list) and its place in that schedule. */
    static final class Index {
        final List<Channel> channels;
        final Guide.Schedule[] schedules;
        final int[] chan, prog;
        final String[] low;
        /** The earliest start and the latest end in the guide (0 when it is empty). */
        final long from, to;

        Index(List<Channel> channels, Guide.Schedule[] schedules) {
            this.channels = channels;
            this.schedules = schedules;
            int n = 0;
            for (Guide.Schedule s : schedules) if (s != null) n += s.start.length;
            chan = new int[n];
            prog = new int[n];
            low = new String[n];
            Map<String, String> folded = new HashMap<>();
            long a = Long.MAX_VALUE, b = 0;
            int at = 0;
            for (int c = 0; c < schedules.length; c++) {
                Guide.Schedule s = schedules[c];
                if (s == null) continue;
                for (int i = 0; i < s.start.length; i++) {
                    chan[at] = c;
                    prog[at] = i;
                    String t = s.title[i];
                    String f = folded.get(t);
                    if (f == null) folded.put(t, f = fold(t));
                    low[at] = f;
                    at++;
                    a = Math.min(a, s.start[i]);
                    b = Math.max(b, s.stop[i]);
                }
            }
            from = a == Long.MAX_VALUE ? 0 : a;
            to = b;
        }

        static final Index EMPTY = new Index(Collections.emptyList(), new Guide.Schedule[0]);

        int size() { return chan.length; }
    }

    /** Lower case, accents and small capitals made plain, spaces single: how titles and searches are compared. */
    static String fold(String s) {
        if (s == null) return "";
        boolean ascii = true;
        for (int i = 0; i < s.length() && ascii; i++) ascii = s.charAt(i) < 128;
        String t = (ascii ? s : Names.plain(s)).toLowerCase(Locale.ROOT).trim();
        return t.indexOf("  ") >= 0 ? t.replaceAll(" +", " ") : t;
    }

    /** How a folded title matches a folded search: EXACT, WORD (the title or one of its words starts with it), INSIDE or -1. */
    static int titleRank(String low, String q) {
        if (q.isEmpty()) return -1;
        if (low.equals(q)) return EXACT;
        int i = low.indexOf(q);
        if (i < 0) return -1;
        for (; i >= 0; i = low.indexOf(q, i + 1)) {
            if (i == 0 || !Character.isLetterOrDigit(low.charAt(i - 1))) return WORD;
        }
        return INSIDE;
    }

    /** Whether a description has the folded search in it (any case; for three letters or more). */
    static boolean inDescription(String desc, String q) {
        if (desc == null || q.length() < 3 || desc.length() < q.length()) return false;
        char c0 = q.charAt(0);
        for (int i = 0, end = desc.length() - q.length(); i <= end; i++) {
            if (Character.toLowerCase(desc.charAt(i)) == c0 && desc.regionMatches(true, i, q, 0, q.length())) return true;
        }
        return false;
    }

    /** The best limit programmes for a search (two letters or more), not yet ended at now, in ORDER. */
    static List<Hit> search(Index ix, String query, long now, int limit) {
        return search(ix, query, now, limit, null);
    }

    /** As search, leaving out the channels skip marks (by their place in the index's channels: the hidden ones). */
    static List<Hit> search(Index ix, String query, long now, int limit, boolean[] skip) {
        String q = fold(query);
        List<Hit> out = new ArrayList<>();
        if (q.length() < 2 || limit <= 0) return out;
        PriorityQueue<Hit> best = new PriorityQueue<>(limit + 1, Collections.reverseOrder(ORDER));
        for (int k = 0; k < ix.size(); k++) {
            int ci = ix.chan[k];
            if (skip != null && ci < skip.length && skip[ci]) continue;
            Guide.Schedule s = ix.schedules[ci];
            int i = ix.prog[k];
            if (s.stop[i] <= now) continue;
            int rank = titleRank(ix.low[k], q);
            if (rank < 0 && inDescription(s.desc == null ? null : s.desc[i], q)) rank = DESCRIPTION;
            if (rank < 0) continue;
            Hit h = new Hit(ix.channels.get(ix.chan[k]), s, i, rank, s.start[i] <= now, k);
            best.add(h);
            if (best.size() > limit) best.poll();
        }
        out.addAll(best);
        out.sort(ORDER);
        return out;
    }
}
