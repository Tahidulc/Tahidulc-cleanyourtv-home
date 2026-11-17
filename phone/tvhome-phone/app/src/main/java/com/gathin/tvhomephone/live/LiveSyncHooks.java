package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the sync (sync.LiveSync, SYNC.md) reads and writes of Live TV and Watch, whose stores keep to this package:
 * the favourite and recent channels, the categories and channels hidden, and where each film or episode is up to.
 * Nothing here logs or shows a playlist's address or login.
 */
public final class LiveSyncHooks {
    private LiveSyncHooks() {}

    // ---- favourite and recent channels (channel keys as Live TV keeps them: "p1:" and so on before a later playlist's) --

    /** The favourite channels in the family's order, any without a place yet after them. */
    public static List<String> favourites(Context c) {
        List<String> out = new ArrayList<>(Picks.order(c));
        for (String k : Picks.favourites(c)) if (!out.contains(k)) out.add(k);
        return out;
    }

    public static List<String> recent(Context c) {
        return Picks.recent(c);
    }

    /** The favourites and recent channels as another device has them (null leaves either as it is). */
    public static void setPicks(Context c, List<String> favourites, List<String> recent) {
        Picks.replace(c, favourites, recent);
    }

    // ---- categories and channels hidden ------------------------------------------------------------------------------

    public static Set<String> hiddenGroups(Context c) {
        return Groups.hidden(c);
    }

    public static List<String> favouriteGroups(Context c) {
        return Groups.favourites(c);
    }

    public static void setGroups(Context c, Set<String> hidden, List<String> favourites) {
        Groups.replace(c, hidden, favourites);
    }

    public static LinkedHashMap<String, String> hiddenChannels(Context c) {
        return Hidden.all(c);
    }

    public static void setHiddenChannels(Context c, Map<String, String> hidden) {
        Hidden.replace(c, hidden);
    }

    // ---- where each film or episode is up to ---------------------------------------------------------------------------

    public static List<VodHistory.Entry> resume(Context c) {
        return VodHistory.list(c);
    }

    /** Pure: a title's name as the index keeps it ("EN - The Matrix (1999) 4K" and "The Matrix" alike). */
    public static String titleKey(String name) {
        return Vod.key(name == null ? "" : name);
    }

    private static SharedPreferences resumePrefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("vod_resume", Context.MODE_PRIVATE);
    }

    /**
     * A point from another device onto an entry this one has: its place, and for a series its episode when the series'
     * episodes say where that one plays. False when the episode can't be found here (the entry is left as it was).
     */
    public static boolean moveTo(Context c, VodHistory.Entry e, int season, int episode, long position, long duration, long at) {
        if (e.tv && (season != e.season || episode != e.episode)) {
            Vod.Episode found = null;
            for (Vod.Episode ep : Vod.episodesFrom(e.episodes)) if (ep.season == season && ep.number == episode) found = ep;
            if (found == null) return false;
            e.plays.clear();
            e.plays.addAll(found.plays());
            resumePrefs(c).edit().remove(e.key).apply();
            e.key = keyFor(e.plays);
            e.season = season;
            e.episode = episode;
            e.episodeTitle = found.title == null ? "" : found.title;
        }
        e.position = position;
        e.duration = duration;
        e.at = at;
        VodHistory.record(c, e);
        resumePrefs(c).edit().putLong(e.key, position).apply();
        return true;
    }

    /** The player's resume key for what plays first (VodPlayerActivity: its stored address's hash). */
    static String keyFor(List<String[]> plays) {
        return Integer.toHexString(plays.get(0)[0].hashCode());
    }

    /** One taken off this device's Continue watching (another device finished it, or took it off). */
    public static void forget(Context c, String key) {
        VodHistory.remove(c, key);
        resumePrefs(c).edit().remove(key).apply();
    }

    /**
     * A point from another device for a title this one hasn't started: looked for in this device's own playlists and,
     * when found, kept on Continue watching as if played here. Off the main thread (it reads the playlists' indexes).
     */
    public static boolean adopt(Context c, String name, String year, String alt, int tmdb, boolean tv, int season, int episode,
                                long position, long duration, long at, String poster, String title) {
        if (name == null || name.isEmpty()) return false;
        Vod.Item it = Vod.find(c, name, alt, year, tmdb, tv);
        if (it == null) return false;
        VodHistory.Entry e = new VodHistory.Entry();
        e.name = name;
        e.title = title == null || title.isEmpty() ? name : title;
        e.poster = poster == null ? "" : poster;
        e.tmdb = tmdb;
        e.tv = tv;
        e.year = year == null ? "" : year;
        e.alt = alt == null ? "" : alt;
        e.position = position;
        e.duration = duration;
        e.at = at;
        if (tv) {
            List<Vod.Episode> eps = Vod.episodes(c, it);
            Vod.Episode found = null;
            for (Vod.Episode ep : eps) if (ep.season == season && ep.number == episode) found = ep;
            if (found == null) return false;
            e.season = season;
            e.episode = episode;
            e.episodeTitle = found.title == null ? "" : found.title;
            e.plays.addAll(found.plays());
            e.episodes = Vod.episodesJson(eps);
        } else {
            e.plays.addAll(it.plays());
        }
        if (e.plays.isEmpty()) return false;
        e.key = keyFor(e.plays);
        VodHistory.record(c, e);
        resumePrefs(c).edit().putLong(e.key, position).apply();
        return true;
    }
}
