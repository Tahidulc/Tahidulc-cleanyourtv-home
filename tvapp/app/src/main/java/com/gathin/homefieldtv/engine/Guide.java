package com.gathin.homefieldtv.engine;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The guide (XMLTV, from the playlist's own address or the Xtream account's xmltv.php): what is on each channel now
 * and next, with each programme's description. Read as a stream, keeping only the channels in the playlist and a window
 * from an hour ago to twelve hours ahead (further back for a channel with catch-up: its archive, at most three days),
 * so a large guide stays small in memory: repeated titles and descriptions are kept once, a description is cut to a
 * few lines, and past a budget the rest of the guide keeps its titles only (the older programmes have a smaller budget
 * of their own, so what's on now and next keeps its descriptions).
 */
public final class Guide {
    private Guide() {}

    static final long BEHIND_MS = 3600_000L, AHEAD_MS = 12 * 3600_000L;
    /** A description's most characters, and all descriptions' together (about 12 MB) before the rest are left out. */
    static final int DESC_MAX = 360, DESC_BUDGET = 6_000_000;
    /** Catch-up: the most days of past programmes kept, and their descriptions' own budget (about 3 MB). */
    static final int PAST_DAYS = 3, PAST_BUDGET = 1_500_000;

    /** One channel's programmes in time order. */
    public static final class Schedule {
        public final long[] start, stop;
        public final String[] title;
        /** Each programme's description ("" when the guide gives none). */
        public final String[] desc;
        /** The longest programme, so that the first one still on at a time is found by its start (from()). */
        private final long longest;

        Schedule(long[] start, long[] stop, String[] title, String[] desc) {
            this.start = start;
            this.stop = stop;
            this.title = title;
            this.desc = desc;
            long l = 0;
            for (int i = 0; i < start.length; i++) l = Math.max(l, stop[i] - start[i]);
            longest = l;
        }

        /** The programme on at t, or -1. */
        public int at(long t) {
            int lo = 0, hi = start.length - 1, found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (start[mid] <= t) { found = mid; lo = mid + 1; } else hi = mid - 1;
            }
            return found >= 0 && stop[found] > t ? found : -1;
        }

        /** The first programme starting after t, or -1 (by halves, not one by one). */
        public int after(long t) {
            int i = startingFrom(t + 1);
            return i < start.length ? i : -1;
        }

        /**
         * Where to begin looking for the programmes still on at t or later (the guide's window): no programme before it
         * ends after t. Found by halves, so a channel with days of catch-up costs no more than one with an evening.
         */
        public int from(long t) {
            return startingFrom(t - longest + 1);
        }

        /** The first programme starting at t or later (start.length for none). */
        private int startingFrom(long t) {
            int lo = 0, hi = start.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (start[mid] < t) lo = mid + 1;
                else hi = mid;
            }
            return lo;
        }
    }

    /**
     * Pure: a schedule cut to the programmes still on at from and starting before to, with descriptions only for those
     * starting before descTo (the memory given back when Android asks: Library.trim).
     */
    static Schedule window(Schedule s, long from, long to, long descTo) {
        int a = s.from(from), n = s.start.length;
        while (a < n && s.stop[a] <= from) a++;
        int b = a;
        while (b < n && s.start[b] < to) b++;
        int k = b - a;
        long[] st = new long[k], en = new long[k];
        String[] t = new String[k], d = new String[k];
        for (int i = 0; i < k; i++) {
            st[i] = s.start[a + i];
            en[i] = s.stop[a + i];
            t[i] = s.title[a + i];
            d[i] = st[i] < descTo ? s.desc[a + i] : "";
        }
        return new Schedule(st, en, t, d);
    }

    /** Collects programmes for one channel key, then makes its Schedule. */
    static final class Builder {
        final List<long[]> times = new ArrayList<>();
        final List<String> titles = new ArrayList<>(), descs = new ArrayList<>();

        void add(long a, long b, String t) {
            add(a, b, t, "");
        }

        void add(long a, long b, String t, String d) {
            times.add(new long[]{a, b});
            titles.add(t == null ? "" : t);
            descs.add(d == null ? "" : d);
        }

        Schedule build() {
            Integer[] order = new Integer[times.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Arrays.sort(order, (x, y) -> Long.compare(times.get(x)[0], times.get(y)[0]));
            long[] s = new long[order.length], e = new long[order.length];
            String[] t = new String[order.length], d = new String[order.length];
            for (int i = 0; i < order.length; i++) {
                s[i] = times.get(order[i])[0];
                e[i] = times.get(order[i])[1];
                t[i] = titles.get(order[i]);
                d[i] = descs.get(order[i]);
            }
            return new Schedule(s, e, t, d);
        }
    }

    /** A description cut to DESC_MAX characters at a word, with an ellipsis; spaces and line breaks made single. */
    static String shortDesc(String d) {
        if (d == null) return "";
        String t = d.replaceAll("\\s+", " ").trim();
        if (t.length() <= DESC_MAX) return t;
        int cut = t.lastIndexOf(' ', DESC_MAX - 1);
        if (cut < DESC_MAX / 2) cut = DESC_MAX - 1;
        return t.substring(0, cut).trim() + "…";
    }

    /**
     * XMLTV's time ("20261104193000 +0000", or without the offset: then UTC) in ms; 0 when it can't be read.
     */
    public static long time(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.length() < 12) return 0;
        try {
            int y = Integer.parseInt(t.substring(0, 4)), mo = Integer.parseInt(t.substring(4, 6)), d = Integer.parseInt(t.substring(6, 8));
            int h = Integer.parseInt(t.substring(8, 10)), mi = Integer.parseInt(t.substring(10, 12));
            int sec = t.length() >= 14 && Character.isDigit(t.charAt(12)) ? Integer.parseInt(t.substring(12, 14)) : 0;
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            c.clear();
            c.set(y, mo - 1, d, h, mi, sec);
            long ms = c.getTimeInMillis();
            int sp = t.indexOf(' ');
            if (sp > 0 && sp + 5 <= t.length()) {
                String off = t.substring(sp + 1).trim();
                if (off.length() >= 5 && (off.charAt(0) == '+' || off.charAt(0) == '-')) {
                    int oh = Integer.parseInt(off.substring(1, 3)), om = Integer.parseInt(off.substring(3, 5));
                    long o = (oh * 60L + om) * 60_000L;
                    ms += off.charAt(0) == '+' ? -o : o;
                }
            }
            return ms;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return 0;
        }
    }

    /**
     * Reads a guide for the playlist's channels: byId maps a lower-case guide id to the channel keys that carry it,
     * byName a channel name's key (Names.key) to those with no guide id. Channels come before programmes in XMLTV, so
     * the display names are matched as the first programme arrives.
     */
    static Map<String, Schedule> read(InputStream in, Map<String, List<String>> byId, Map<String, List<String>> byName, long now)
            throws IOException {
        return read(in, byId, byName, now, null);
    }

    /** As above, keeping the programmes back to since's time for the channels in it (their catch-up). */
    static Map<String, Schedule> read(InputStream in, Map<String, List<String>> byId, Map<String, List<String>> byName, long now,
                                      Map<String, Long> since) throws IOException {
        Map<String, Builder> out = new HashMap<>();
        Map<String, List<String>> idKeys = new HashMap<>(byId);
        Map<String, String> pool = new HashMap<>();
        int[] budget = {DESC_BUDGET}, pastBudget = {PAST_BUDGET};
        try {
            XmlPullParser p = android.util.Xml.newPullParser();
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            p.setInput(in, null);
            long from = now - BEHIND_MS, to = now + AHEAD_MS;
            for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next()) {
                if (ev != XmlPullParser.START_TAG) continue;
                String tag = p.getName();
                if ("channel".equals(tag)) {
                    String id = p.getAttributeValue(null, "id");
                    if (id == null) continue;
                    String lid = id.toLowerCase(Locale.ROOT);
                    int depth = p.getDepth();
                    while ((ev = p.next()) != XmlPullParser.END_DOCUMENT && !(ev == XmlPullParser.END_TAG && p.getDepth() == depth)) {
                        if (ev == XmlPullParser.START_TAG && "display-name".equals(p.getName())) {
                            List<String> keys = byName.get(Names.key(p.nextText()));
                            if (keys != null) {
                                List<String> have = idKeys.get(lid);
                                if (have == null) idKeys.put(lid, have = new ArrayList<>());
                                for (String k : keys) if (!have.contains(k)) have.add(k);
                            }
                        }
                    }
                } else if ("programme".equals(tag)) {
                    String ch = p.getAttributeValue(null, "channel");
                    List<String> keys = ch == null ? null : idKeys.get(ch.toLowerCase(Locale.ROOT));
                    long a = time(p.getAttributeValue(null, "start")), b = time(p.getAttributeValue(null, "stop"));
                    if (keys == null || a == 0 || b <= a || b < earliest(keys, from, since) || a > to) {
                        skip(p);
                        continue;
                    }
                    // Kept only for catch-up: its description from the older programmes' own budget.
                    int[] room = b < from ? pastBudget : budget;
                    String title = "", desc = "";
                    int depth = p.getDepth();
                    while ((ev = p.next()) != XmlPullParser.END_DOCUMENT && !(ev == XmlPullParser.END_TAG && p.getDepth() == depth)) {
                        if (ev != XmlPullParser.START_TAG) continue;
                        String inner = p.getName();
                        if ("title".equals(inner) && title.isEmpty()) title = p.nextText().trim();
                        else if ("desc".equals(inner) && desc.isEmpty() && room[0] > 0) desc = shortDesc(p.nextText());
                    }
                    title = once(pool, title);
                    if (!desc.isEmpty()) {
                        String had = pool.get(desc);
                        if (had == null) room[0] -= desc.length();
                        desc = once(pool, desc);
                    }
                    for (String k : keys) {
                        Builder bl = out.get(k);
                        if (bl == null) out.put(k, bl = new Builder());
                        bl.add(a, b, title, desc);
                    }
                }
            }
        } catch (XmlPullParserException e) {
            // A guide cut short: what was read so far is kept.
        }
        Map<String, Schedule> done = new HashMap<>();
        for (Map.Entry<String, Builder> e : out.entrySet()) done.put(e.getKey(), e.getValue().build());
        return done;
    }

    /** Pure: the earliest end a programme may have to be kept for these channels: from, or further back for catch-up. */
    static long earliest(List<String> keys, long from, Map<String, Long> since) {
        if (since == null || since.isEmpty()) return from;
        long f = from;
        for (String k : keys) {
            Long s = since.get(k);
            if (s != null && s < f) f = s;
        }
        return f;
    }

    /** The one copy of a string that is repeated through the guide (a series' title on every channel showing it). */
    private static String once(Map<String, String> pool, String s) {
        String had = pool.get(s);
        if (had != null) return had;
        pool.put(s, s);
        return s;
    }

    private static void skip(XmlPullParser p) throws XmlPullParserException, IOException {
        int depth = 1;
        while (depth > 0) {
            int ev = p.next();
            if (ev == XmlPullParser.END_DOCUMENT) return;
            if (ev == XmlPullParser.START_TAG) depth++;
            else if (ev == XmlPullParser.END_TAG) depth--;
        }
    }
}
