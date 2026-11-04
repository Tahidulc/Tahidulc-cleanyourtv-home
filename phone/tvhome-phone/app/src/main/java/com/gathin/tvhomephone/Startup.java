package com.gathin.tvhomephone;

import android.content.Context;

/** What each part starts as the app starts: the sync with the TVs, and the alerts. */
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
    }
}
