package com.gathin.homefieldtv.engine;

import android.content.Context;

import com.gathin.broadcast.PicFinder;
import com.gathin.broadcast.SkyListing;

import org.json.JSONArray;

import java.io.File;
import java.util.Map;

/**
 * Programme pictures for the TV guide from the UK guide catalogues, looked for as the phone app looks for them (PicFinder,
 * shared): the programme found by its channel and time in Sky's public guide (episode, then season and series pictures,
 * Sky's "Image unavailable" card passed over), Freeview's and Virgin Media's public guides, a game's own picture
 * (TheSportsDB), then by its title (TVMaze, and TMDB through WiderPictures: the episode's still, then the series'
 * picture). Every answer kept on the TV (PicFetch: the app's cache folder, listings for hours, titles for days) and
 * small in memory; requests kept a little apart, a plain user agent, no login, nothing about the family. Off the main
 * thread only.
 */
public final class SkyGuide {
    private SkyGuide() {}

    static final String SERVICES = SkyListing.SERVICES;
    static final String SCHEDULE = SkyListing.SCHEDULE;
    /** The pictures' width: enough for the cells, the info card and the details' panel. */
    static final int WIDTH = 640;

    private static volatile PicFinder finder;

    /** The shared finder, its pages in the app's cache (made the first time it is wanted). */
    public static PicFinder finder(Context c) {
        PicFinder f = finder;
        if (f != null) return f;
        synchronized (SkyGuide.class) {
            if (finder == null) {
                Context app = c.getApplicationContext();
                finder = new PicFinder(new File(app.getCacheDir(), "guide-pictures"), (p, season, episode) -> {
                    // TMDB by the programme's title: null while the searches are spaced out (asked again later).
                    String series = WiderPictures.find(app, p.title, p.desc, p.channel, p.group);
                    if (series == null) return null;
                    String still = WiderPictures.still(app, p.title, p.desc, p.channel, p.group, season, episode);
                    return new String[]{still, series};
                }, WIDTH, WIDTH);
            }
            return finder;
        }
    }

    /** Android is short of memory: the listings read so far let go (read again from the cache when wanted). */
    public static void trim() {
        PicFinder f = finder;
        if (f != null) f.trim();
    }

    // ---- pure: names, the list, the programme (shared with the phone: SkyListing) ------------------------------------

    /** Sky's short names spelt out ("SkySpMainEvHD" → "Sky Sports Main Event HD", "U&Dave" → "Dave"). Pure. */
    static String skyName(String t) {
        return SkyListing.skyName(t);
    }

    /** Pure: a channel's name as both sides are compared ("UK: Sky Sports Main Event FHD" → "skysportmainevent"). */
    static String key(String name) {
        return SkyListing.keyOf(Names.key(name));
    }

    /** Pure: Sky's list of channels as name → its number: the North West's BBC One first, then an HD copy. */
    static Map<String, String> services(String json) {
        return SkyListing.services(json, Names::key);
    }

    /**
     * Pure: the programme on at a time (start seconds "st", length "d"), its title preferred among several; one starting
     * within five minutes after the time when none is on at it. Its "programmeuuid", or "".
     */
    static String uuidAt(JSONArray events, long at, String title) {
        return SkyListing.uuidAt(events, at, title);
    }
}
