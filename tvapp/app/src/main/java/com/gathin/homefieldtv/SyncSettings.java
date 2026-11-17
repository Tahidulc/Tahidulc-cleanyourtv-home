package com.gathin.homefieldtv;

import com.gathin.homefieldtv.sync.TvLiveSync;
import com.gathin.homefieldtv.sync.TvSync;

import java.util.List;

/**
 * Settings → General → Sync: Homefield TV keeps the family's playlists, favourite and recent channels, Continue
 * watching and some settings the same as the phones and tablets, through the launcher on this TV
 * (phone/tvhome-phone/SYNC.md). When it last synced (OK: Sync now), and a switch for each kind, all on to begin with.
 */
final class SyncSettings {
    private SyncSettings() {}

    /** {switch, name, what it covers}. */
    private static final String[][] KINDS = {
            {TvLiveSync.PLAYLISTS, "Playlists", "Your playlists, logins included, only between your own devices"},
            {TvLiveSync.PICKS, "Favourites & recents", "Favourite and recent channels"},
            {TvLiveSync.WATCHING, "Continue watching", "Where each film and episode is up to"},
            {TvLiveSync.SETTINGS, "Settings", "Posters and the scoreboard"}};

    /** The page's rows: Sync now and when it last synced, then a switch for each kind. */
    static void rows(TvActivity tv, List<RowList.Row> out) {
        out.add(RowList.Row.item("now", "Sync now", TvSync.status(tv)));
        out.add(RowList.Row.header("What syncs"));
        for (String[] k : KINDS) {
            String what = TvLiveSync.PLAYLISTS.equals(k[0]) && TvLiveSync.offByRemoveEverything(tv)
                    ? "Since Remove everything on this TV: on takes in the family's playlists again" : k[2];
            out.add(RowList.Row.toggle("kind:" + k[0], k[1], what, TvLiveSync.on(tv, k[0])));
        }
        out.add(RowList.Row.note("Through the launcher on this TV, which passes them on to your phones, tablets and other TVs. "
                + "TorBox's key stays on each device."));
    }

    /** Under Remove everything, once done: Playlists sync is off on this TV. */
    static void removedNote(TvActivity tv, List<RowList.Row> out) {
        if (!TvLiveSync.offByRemoveEverything(tv)) return;
        out.add(RowList.Row.note("Removed from this TV only. Playlists sync is off here: Sync turns it on again."));
    }

    /** Remove everything chosen: clear (what it removes) for this TV only, with Playlists sync switched off (TvLiveSync). */
    static void removeEverything(TvActivity tv, Runnable clear) {
        TvLiveSync.removeEverything(tv, clear);
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
