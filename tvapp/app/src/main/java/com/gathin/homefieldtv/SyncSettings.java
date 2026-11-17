package com.gathin.homefieldtv;

import com.gathin.homefieldtv.sync.TvLiveSync;
import com.gathin.homefieldtv.sync.TvSync;

import java.util.List;

/**
 * Settings' Sync section: Homefield TV keeps the family's playlists, favourite and recent channels, Continue watching
 * and some settings the same as the phones and tablets, through the launcher on this TV (phone/tvhome-phone/SYNC.md).
 * When it last synced (OK: Sync now), and a switch for each kind, all on to begin with.
 */
final class SyncSettings {
    private SyncSettings() {}

    /** {switch, name, what it covers}. */
    private static final String[][] KINDS = {
            {TvLiveSync.PLAYLISTS, "Playlists", "Your playlists, logins included, only between your own devices"},
            {TvLiveSync.PICKS, "Favourites & recents", "Favourite and recent channels"},
            {TvLiveSync.WATCHING, "Continue watching", "Where each film and episode is up to"},
            {TvLiveSync.SETTINGS, "Settings", "Posters and the scoreboard"}};

    /** The section's lines, as the settings list shows them. */
    static void fill(TvActivity tv, List<ListPanel.Item> out) {
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "Sync with your phones and tablets", ""));
        out.add(new ListPanel.Item("now", ListPanel.TEXT, "Sync now", TvSync.status(tv)));
        out.add(new ListPanel.Item(null, ListPanel.HEADING, "What syncs", ""));
        for (String[] k : KINDS) {
            out.add(new ListPanel.Item("kind:" + k[0], ListPanel.TEXT, k[1], (TvLiveSync.on(tv, k[0]) ? "On" : "Off") + "  ·  " + k[2]));
        }
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "Through the launcher on this TV, which passes them on to your phones, tablets and other TVs.", ""));
        out.add(new ListPanel.Item(null, ListPanel.NOTE, "TorBox's key stays on each device.", ""));
    }

    /** A line chosen: changed (true, the list is drawn again), or not one of the section's (false). done: after a sync. */
    static boolean chosen(TvActivity tv, String tag, Runnable done) {
        if (tag.equals("now")) {
            TvSync.syncNow(tv, done);
            return true;
        }
        if (tag.startsWith("kind:")) {
            String kind = tag.substring(5);
            boolean on = !TvLiveSync.on(tv, kind);
            TvLiveSync.setOn(tv, kind, on);
            if (on) TvSync.changed();
            return true;
        }
        return false;
    }
}
