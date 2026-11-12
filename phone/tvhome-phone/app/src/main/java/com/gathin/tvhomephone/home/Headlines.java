package com.gathin.tvhomephone.home;

import android.content.Context;

import com.gathin.tvhomephone.Net;
import com.gathin.tvhomephone.live.BreakingNews;
import com.gathin.tvhomephone.live.BreakingNews.Story;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Home's headlines: BBC News's top stories and BBC Sport's, from their public RSS feeds (the same ones the alerts read),
 * each with its picture, how long ago it went up, and its link (opened in the browser). Read at most every ten
 * minutes; the last read is kept on this phone so Home shows it at once.
 */
final class Headlines {
    static final int MAX = 10;
    /** Stories older than this are left out. */
    static final long MAX_AGE_MS = 48 * 3600_000L;
    static final long FRESH_MS = 10 * 60_000L;
    private static final String FILE = "home_news.json";

    private Headlines() {}

    /** BBC News's (0) and BBC Sport's (1) stories, and when they were read. */
    static final class Read {
        final List<List<Story>> feeds = new ArrayList<>();
        long at;
    }

    private static Read memory;

    /**
     * The stories worth a card, in the feed's own order (its editors'): news, not a video, a quiz or a podcast; not
     * older than MAX_AGE_MS when the feed dates it; each story once; at most max. Pure.
     */
    static List<Story> pick(List<Story> all, long now, int max) {
        List<Story> out = new ArrayList<>();
        Set<String> once = new HashSet<>();
        for (Story s : all) {
            if (out.size() >= max) break;
            if (s == null || s.title == null || s.title.trim().isEmpty()) continue;
            if (feature(s.title)) continue;
            if (s.at > 0 && now - s.at > MAX_AGE_MS) continue;
            String key = s.link.isEmpty() ? s.title.toLowerCase(Locale.ROOT) : s.link.replaceAll("[?#].*$", "");
            if (!once.add(key)) continue;
            out.add(s);
        }
        return out;
    }

    /** A video, a quiz, a podcast or a live page's teaser, not a story to read. Pure. */
    static boolean feature(String title) {
        String t = title.toLowerCase(Locale.ROOT).trim();
        for (String f : new String[]{"watch:", "listen:", "quiz", "podcast", "in pictures:", "the papers:", "newspaper headlines"}) {
            if (t.startsWith(f) || t.contains(" " + f)) return true;
        }
        return false;
    }

    /** "Just now", "12 min ago", "3 hr ago", "Yesterday", "3 days ago"; "" when the feed gave no time. Pure. */
    static String ago(long at, long now) {
        if (at <= 0) return "";
        long m = Math.max(0, (now - at) / 60_000L);
        if (m < 2) return "Just now";
        if (m < 60) return m + " min ago";
        long h = m / 60;
        if (h < 24) return h + " hr ago";
        long d = h / 24;
        return d == 1 ? "Yesterday" : d + " days ago";
    }

    /** What was read last (this run's, else the copy on this phone), or null. */
    static Read kept(Context c) {
        synchronized (Headlines.class) {
            if (memory != null) return memory;
        }
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            JSONObject o = new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
            Read r = new Read();
            r.at = o.optLong("at");
            JSONArray feeds = o.optJSONArray("feeds");
            for (int i = 0; feeds != null && i < feeds.length(); i++) {
                List<Story> list = new ArrayList<>();
                JSONArray a = feeds.optJSONArray(i);
                for (int j = 0; a != null && j < a.length(); j++) {
                    JSONObject s = a.optJSONObject(j);
                    if (s != null) list.add(new Story(s.optString("t"), "", s.optString("l"), s.optLong("at"), s.optString("p")));
                }
                r.feeds.add(list);
            }
            synchronized (Headlines.class) {
                if (memory == null) memory = r;
            }
            return r;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Both feeds read now (unless read in the last FRESH_MS and not forced), kept. Off the main thread. */
    static Read load(Context c, boolean force) {
        Read had = kept(c);
        long now = System.currentTimeMillis();
        if (!force && had != null && now - had.at < FRESH_MS && had.feeds.size() == BreakingNews.FEEDS.length) return had;
        Read r = new Read();
        r.at = now;
        boolean any = false;
        for (int i = 0; i < BreakingNews.FEEDS.length; i++) {
            List<Story> list;
            try {
                list = pick(BreakingNews.parse(Net.get(BreakingNews.FEEDS[i][0])), now, MAX);
                any |= !list.isEmpty();
            } catch (IOException | RuntimeException e) {
                // that feed as it was last read
                list = had != null && had.feeds.size() > i ? had.feeds.get(i) : new ArrayList<>();
            }
            r.feeds.add(list);
        }
        if (!any && had != null) return had;
        synchronized (Headlines.class) {
            memory = r;
        }
        save(c, r);
        return r;
    }

    private static void save(Context c, Read r) {
        try {
            JSONArray feeds = new JSONArray();
            for (List<Story> list : r.feeds) {
                JSONArray a = new JSONArray();
                for (Story s : list) a.put(new JSONObject().put("t", s.title).put("l", s.link).put("at", s.at).put("p", s.picture));
                feeds.put(a);
            }
            byte[] b = new JSONObject().put("at", r.at).put("feeds", feeds).toString().getBytes(StandardCharsets.UTF_8);
            File tmp = new File(c.getFilesDir(), FILE + ".part");
            try (FileOutputStream o = new FileOutputStream(tmp)) {
                o.write(b);
            }
            if (!tmp.renameTo(new File(c.getFilesDir(), FILE))) tmp.delete();
        } catch (IOException | JSONException ignored) {
            // kept in memory only
        }
    }
}
