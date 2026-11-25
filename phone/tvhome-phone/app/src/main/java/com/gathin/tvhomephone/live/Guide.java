package com.gathin.tvhomephone.live;

import com.gathin.broadcast.PicLinks;
import com.gathin.broadcast.PicSources;

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
 * from an hour ago to twelve hours ahead, so a large guide stays small in memory: repeated titles and descriptions are
 * kept once, a description is cut to a few lines, and past a budget the rest of the guide keeps its titles only. Each
 * programme's picture ({@code <icon src>}, or an {@code <image>} of it, a landscape one first) is kept too, small
 * (PicLinks, shared with Homefield TV: an int a programme, the addresses packed once for the whole guide), and what its
 * categories say it is as a byte (PicSources.categoryCode), for its kind's picture where none is found (ProgArt).
 */
public final class Guide {
    private Guide() {}

    static final long BEHIND_MS = 3600_000L, AHEAD_MS = 12 * 3600_000L;
    /** A description's most characters, and all descriptions' together (about 12 MB) before the rest are left out. */
    static final int DESC_MAX = 360, DESC_BUDGET = 6_000_000;

    /** One channel's programmes in time order. */
    public static final class Schedule {
        public final long[] start, stop;
        public final String[] title;
        /** Each programme's description ("" when the guide gives none). */
        public final String[] desc;
        /** Each programme's picture in links (-1: none); null when the guide gives this channel none. */
        final int[] pic;
        final PicLinks links;
        /** What each programme's categories say it is (PicSources.categoryCode; 0: none); null when the guide gives none. */
        final byte[] kind;
        /** Each programme's picture as found to draw (ProgArt), made as it is first drawn; null until then. */
        volatile Object[] art;

        Schedule(long[] start, long[] stop, String[] title, String[] desc) {
            this(start, stop, title, desc, null, PicLinks.EMPTY, null);
        }

        Schedule(long[] start, long[] stop, String[] title, String[] desc, int[] pic, PicLinks links) {
            this(start, stop, title, desc, pic, links, null);
        }

        Schedule(long[] start, long[] stop, String[] title, String[] desc, int[] pic, PicLinks links, byte[] kind) {
            this.start = start;
            this.stop = stop;
            this.title = title;
            this.desc = desc;
            this.pic = pic;
            this.links = links == null ? PicLinks.EMPTY : links;
            this.kind = kind;
        }

        /** What programme i's categories say it is (a PicSources kind, "" for none). */
        public String category(int i) {
            return kind == null || i < 0 || i >= kind.length ? "" : PicSources.categoryWord(kind[i]);
        }

        /** Programme i's kind's code (0: none), as kept. */
        byte kindCode(int i) {
            return kind == null || i < 0 || i >= kind.length ? 0 : kind[i];
        }

        /** Programme i's own picture as the guide gives it ("" for none). */
        public String pic(int i) {
            return pic == null || i < 0 || i >= pic.length || pic[i] < 0 ? "" : links.get(pic[i]);
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
        final List<String> titles = new ArrayList<>(), descs = new ArrayList<>();
        /** Each programme's picture (into the guide's PicLinks), or null while none has one; its kind, the same. */
        private int[] pics;
        private byte[] kinds;

        void add(long a, long b, String t) {
            add(a, b, t, "");
        }

        void add(long a, long b, String t, String d) {
            add(a, b, t, d, -1);
        }

        void add(long a, long b, String t, String d, int pic) {
            add(a, b, t, d, pic, (byte) 0);
        }

        void add(long a, long b, String t, String d, int pic, byte kind) {
            int at = times.size();
            if (kind != 0 && kinds == null) kinds = new byte[Math.max(8, at + 1)];
            if (kinds != null) {
                if (at >= kinds.length) kinds = java.util.Arrays.copyOf(kinds, Math.max(kinds.length * 2, at + 1));
                kinds[at] = kind;
            }
            times.add(new long[]{a, b});
            titles.add(t == null ? "" : t);
            descs.add(d == null ? "" : d);
            if (pic >= 0 && pics == null) {
                pics = new int[Math.max(8, at + 1)];
                java.util.Arrays.fill(pics, -1);
            }
            if (pics != null) {
                if (at >= pics.length) {
                    int old = pics.length;
                    pics = java.util.Arrays.copyOf(pics, Math.max(old * 2, at + 1));
                    java.util.Arrays.fill(pics, old, pics.length, -1);
                }
                pics[at] = pic;
            }
        }

        Schedule build() {
            return build(PicLinks.EMPTY);
        }

        /** The schedule, its pictures in links (the guide's, shared by every channel's). */
        Schedule build(PicLinks links) {
            Integer[] order = new Integer[times.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Arrays.sort(order, (x, y) -> Long.compare(times.get(x)[0], times.get(y)[0]));
            long[] s = new long[order.length], e = new long[order.length];
            String[] t = new String[order.length], d = new String[order.length];
            int[] p = pics == null ? null : new int[order.length];
            byte[] k = kinds == null ? null : new byte[order.length];
            for (int i = 0; i < order.length; i++) {
                s[i] = times.get(order[i])[0];
                e[i] = times.get(order[i])[1];
                t[i] = titles.get(order[i]);
                d[i] = descs.get(order[i]);
                if (p != null) p[i] = order[i] < pics.length ? pics[order[i]] : -1;
                if (k != null) k[i] = order[i] < kinds.length ? kinds[order[i]] : 0;
            }
            return new Schedule(s, e, t, d, p, links, k);
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
        Map<String, Builder> out = new HashMap<>();
        Map<String, List<String>> idKeys = new HashMap<>(byId);
        Map<String, String> pool = new HashMap<>();
        PicLinks.Builder links = new PicLinks.Builder();
        Map<String, Byte> kinds = new HashMap<>();
        int[] budget = {DESC_BUDGET};
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
                    String title = "", desc = "", icon = "", image = "", cats = "";
                    int imageRank = 0;
                    int depth = p.getDepth();
                    while ((ev = p.next()) != XmlPullParser.END_DOCUMENT && !(ev == XmlPullParser.END_TAG && p.getDepth() == depth)) {
                        if (ev != XmlPullParser.START_TAG) continue;
                        String inner = p.getName();
                        if ("title".equals(inner) && title.isEmpty()) title = p.nextText().trim();
                        else if ("desc".equals(inner) && desc.isEmpty() && budget[0] > 0) desc = shortDesc(p.nextText());
                        else if ("icon".equals(inner) && icon.isEmpty()) icon = PicLinks.clean(p.getAttributeValue(null, "src"));
                        else if ("category".equals(inner) && cats.length() < 80) cats = cats + " | " + p.nextText().trim();
                        else if ("image".equals(inner)) {
                            // XMLTV 1.1's pictures: a landscape one (backdrop, still) before a poster.
                            int rank = imageRank(p.getAttributeValue(null, "type"), p.getAttributeValue(null, "orient"));
                            String u = PicLinks.clean(p.nextText());
                            if (!u.isEmpty() && rank > imageRank) {
                                image = u;
                                imageRank = rank;
                            }
                        }
                    }
                    int pic = links.add(pick(icon, image, imageRank));
                    byte kind = 0;
                    if (!cats.isEmpty()) {
                        Byte had = kinds.get(cats);
                        if (had == null) kinds.put(cats, had = PicSources.categoryCode(cats));
                        kind = had;
                    }
                    title = once(pool, title);
                    if (!desc.isEmpty()) {
                        String had = pool.get(desc);
                        if (had == null) budget[0] -= desc.length();
                        desc = once(pool, desc);
                    }
                    for (String k : keys) {
                        Builder bl = out.get(k);
                        if (bl == null) out.put(k, bl = new Builder());
                        bl.add(a, b, title, desc, pic, kind);
                    }
                }
            }
        } catch (XmlPullParserException e) {
            // A guide cut short: what was read so far is kept.
        }
        PicLinks all = links.build();
        Map<String, Schedule> done = new HashMap<>();
        for (Map.Entry<String, Builder> e : out.entrySet()) done.put(e.getKey(), e.getValue().build(all));
        return done;
    }

    /** Pure: how well an XMLTV {@code <image>} fills a landscape cell: a backdrop or still 3, landscape 2, a poster 1. */
    static int imageRank(String type, String orient) {
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT), o = orient == null ? "" : orient.trim().toUpperCase(Locale.ROOT);
        if (t.equals("backdrop") || t.equals("still") || t.equals("fanart")) return 3;
        if (t.equals("poster") || o.equals("P")) return 1;
        return 2;
    }

    /** Pure: the programme's picture: a landscape {@code <image>} before its icon, the icon before a poster. */
    static String pick(String icon, String image, int imageRank) {
        String i = icon == null ? "" : icon, m = image == null ? "" : image;
        if (!m.isEmpty() && (imageRank >= 2 || i.isEmpty())) return m;
        return i;
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
