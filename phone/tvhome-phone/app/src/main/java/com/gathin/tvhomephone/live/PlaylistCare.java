package com.gathin.tvhomephone.live;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.gathin.tvhomephone.App;

import java.util.concurrent.TimeUnit;

/**
 * Looking after the playlists, started with the app: the "Your playlists" channel, the daily check (PlaylistWorker:
 * expiry warnings, the weekly health check), and the expiry warnings looked at again each time the playlists load (a
 * new date from the provider is known then).
 */
public final class PlaylistCare {
    private PlaylistCare() {}

    private static long checkedAt;

    public static void start(Context c) {
        Context app = c.getApplicationContext();
        Expiry.channel(app);
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("playlist-care", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(PlaylistWorker.class, 1, TimeUnit.DAYS).build());
        Library lib = Library.get(app);
        lib.addListener(() -> {
            // After a load (not while it is still going), at most once a minute.
            long now = System.currentTimeMillis();
            if (lib.busy() || !lib.ready() || now - checkedAt < 60_000L) return;
            checkedAt = now;
            App.io.execute(() -> {
                try {
                    Expiry.check(app);
                } catch (RuntimeException ignored) {
                    // the daily check covers it
                }
            });
        });
    }
}
