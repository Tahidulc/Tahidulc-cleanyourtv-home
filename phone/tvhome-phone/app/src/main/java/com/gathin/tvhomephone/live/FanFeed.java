package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.sports.Clubs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What fans of the starred clubs are saying now, for Home's feed: Bluesky's newest public posts naming each club and the
 * club's own Reddit community's (its pre-match thread's comments on a match day), through FanFilter as the ad-break
 * screen's are, no bots, nothing older than two days, newest first. No profile pictures are read: a post shows its
 * author's initials. Nothing is logged and nothing is kept on the phone: the last read is held while the app runs.
 */
public final class FanFeed {
    private FanFeed() {}

    /** At most this many clubs are read (each is a Bluesky search and a Reddit feed or two). */
    static final int CLUBS = 3;
    /** Posts older than this are left out. */
    static final long MAX_AGE_MS = 48L * 60 * 60 * 1000;
    /** Read again no sooner than this (Home asks every two minutes while it is on the screen). */
    static final long GAP_MS = 90_000L, FORCED_GAP_MS = 20_000L;

    /** One post as Home shows it. */
    public static final class Item {
        /** "Bluesky" or "Reddit". */
        public final String source;
        /** The author's name, their handle ("@fan.bsky.social", "u/fan"), the words, the post's page ("" for none). */
        public final String name, handle, text, link;
        /** The community it is in ("r/reddevils"; "" for Bluesky's). */
        public final String where;
        public final long at;

        public Item(String source, String name, String handle, String text, String link, String where, long at) {
            this.source = source == null ? "" : source;
            this.name = name == null ? "" : name;
            this.handle = handle == null ? "" : handle;
            this.text = text == null ? "" : text;
            this.link = safeLink(link);
            this.where = where == null ? "" : where;
            this.at = at;
        }

        Item(FanPosts.Post p) {
            this(p.source, p.name, p.handle, p.text, p.link, p.where, p.at);
        }

        /** The same post read again has the same key. */
        public String key() {
            return source + "|" + handle + "|" + at + "|" + text.hashCode();
        }

        public boolean bluesky() {
            return FanPosts.BLUESKY.equals(source);
        }
    }

    private static final Object LOCK = new Object();
    private static volatile List<Item> kept = Collections.emptyList();
    private static volatile String keptFor = "";
    private static volatile List<String> keptClubs = Collections.emptyList();
    private static volatile long readAt;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("fan_feed", Context.MODE_PRIVATE);
    }

    /** Fans' posts on Home (on until it is switched off in Settings, Sports). */
    public static boolean on(Context c) {
        return prefs(c).getBoolean("home", true);
    }

    public static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("home", on).apply();
        if (!on) {
            kept = Collections.emptyList();
            readAt = 0;
        }
    }

    /** The last posts read, at once (none before the first read, or when it is off). */
    public static List<Item> kept() {
        return kept;
    }

    /** The clubs the last posts read are about. */
    public static List<String> keptClubs() {
        return keptClubs;
    }

    /** The clubs the feed is about, by name ("Manchester United", "Everton"), as Settings has them starred. */
    public static List<String> clubs(Context c) {
        return names(Clubs.describe(c));
    }

    /**
     * Off the main thread: the newest posts about the starred clubs, at most max; what it read last when asked again
     * within a minute and a half (twenty seconds when forced, as a pull to refresh is). Off, or no clubs: none.
     */
    public static List<Item> load(Context c, int max, boolean force) {
        if (!on(c)) return Collections.emptyList();
        List<String> names = clubs(c);
        if (names.isEmpty()) {
            kept = Collections.emptyList();
            return kept;
        }
        String forWhom = String.join("|", names);
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            boolean same = forWhom.equals(keptFor);
            if (same && now - readAt < (force ? FORCED_GAP_MS : GAP_MS)) return kept;
            List<Item> out = new ArrayList<>();
            for (FanPosts.Post p : FanPosts.clubs(names, max, MAX_AGE_MS)) out.add(new Item(p));
            readAt = System.currentTimeMillis();
            // Nothing came (no network, a source down): what it had stays up rather than the feed emptying.
            if (out.isEmpty() && same) return kept;
            kept = Collections.unmodifiableList(out);
            keptFor = forWhom;
            keptClubs = Collections.unmodifiableList(new ArrayList<>(names));
            return kept;
        }
    }

    /** Pure: a post's page when it is on Bluesky's or Reddit's own site (https), else "" (nothing opens). */
    static String safeLink(String link) {
        if (link == null) return "";
        String l = link.trim();
        String low = l.toLowerCase(Locale.ROOT);
        for (String site : new String[]{"https://bsky.app/", "https://www.reddit.com/", "https://reddit.com/", "https://old.reddit.com/"}) {
            if (low.startsWith(site) && !l.contains(" ")) return l;
        }
        return "";
    }

    /** "now", "3m", "2h", "1d": how long ago. */
    public static String ago(long at, long now) {
        return FanPosts.ago(at, now);
    }

    /**
     * Pure: the starred clubs' names fit to search for, from Clubs.describe's {id, name, crest}: a club not yet seen by
     * name (its name is its id, a number) is left out; each name once; at most CLUBS.
     */
    static List<String> names(List<String[]> described) {
        Set<String> out = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String[] d : described) {
            if (d == null || d.length < 2 || d[1] == null) continue;
            String name = d[1].trim();
            if (name.isEmpty() || name.matches("[0-9]+") || name.equals(d[0])) continue;
            if (!seen.add(name.toLowerCase(Locale.ROOT))) continue;
            out.add(name);
            if (out.size() >= CLUBS) break;
        }
        return new ArrayList<>(out);
    }
}
