package com.gathin.tvhomephone;

import android.content.Context;

/** What each part starts as the app starts: the sync with the TVs, the alerts, and the playlists' daily care. */
final class Startup {
    private Startup() {}

    static void run(Context c) {
        try {
            com.gathin.tvhomephone.sync.SyncStartup.start(c);
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome", "sync start", e);
        }
        try {
            com.gathin.tvhomephone.sports.SportsStartup.start(c);
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome", "alerts start", e);
        }
        try {
            // The scoreboard over other apps, when it was left on.
            com.gathin.tvhomephone.live.FloatService.start(c);
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome", "scoreboard start", e);
        }
        try {
            com.gathin.tvhomephone.live.PlaylistCare.start(c);
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome", "playlists start", e);
        }
    }
}
