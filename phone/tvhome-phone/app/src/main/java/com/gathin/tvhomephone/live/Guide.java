package com.gathin.tvhomephone.live;

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
 * and next. Read as a stream, keeping only the channels in the playlist and a window from an hour ago to twelve hours
 * ahead, so a large guide stays small in memory.
 */
public final class Guide {
    private Guide() {}

    static final long BEHIND_MS = 3600_000L, AHEAD_MS = 12 * 3600_000L;

    /** One channel's programmes in time order. */
    public static final class Schedule {
        public final long[] start, stop;
        public final String[] title;

        Schedule(long[] start, long[] stop, String[] title) {
            this.start = start;
            this.stop = stop;
            this.title = title;
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

        /** The first programme starting after t, or -1. */
        public int after(long t) {
            for (int i = 0; i < start.length; i++) if (start[i] > t) return i;
            return -1;
        }
    }

    /** Collects programmes for one channel key, then makes its Schedule. */
    static final class Builder {
        final List<long[]> times = new ArrayList<>();
        final List<String> titles = new ArrayList<>();

        void add(long a, long b, String t) {
            times.add(new long[]{a, b});
            titles.add(t);
        }

        Schedule build() {
            Integer[] order = new Integer[times.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Arrays.sort(order, (x, y) -> Long.compare(times.get(x)[0], times.get(y)[0]));
            long[] s = new long[order.length], e = new long[order.length];
            String[] t = new String[order.length];
            for (int i = 0; i < order.length; i++) {
                s[i] = times.get(order[i])[0];
                e[i] = times.get(order[i])[1];
                t[i] = titles.get(order[i]);
            }
            return new Schedule(s, e, t);
        }
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
        Map<String, Builder> out = new HashMap<>();
        Map<String, List<String>> idKeys = new HashMap<>(byId);
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
                    if (keys == null || a == 0 || b <= a || b < from || a > to) {
                        skip(p);
                        continue;
                    }
                    String title = "";
                    int depth = p.getDepth();
                    while ((ev = p.next()) != XmlPullParser.END_DOCUMENT && !(ev == XmlPullParser.END_TAG && p.getDepth() == depth)) {
                        if (ev == XmlPullParser.START_TAG && "title".equals(p.getName()) && title.isEmpty()) title = p.nextText().trim();
                    }
                    for (String k : keys) {
                        Builder bl = out.get(k);
                        if (bl == null) out.put(k, bl = new Builder());
                        bl.add(a, b, title);
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
