package com.gathin.tvhomephone.sync;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.SyncStore;

import java.util.concurrent.TimeUnit;

/**
 * The sync with the TVs, started with the app: a sync now, one a few seconds after any change made here (several
 * changes in a row go together), and every 15 minutes through WorkManager.
 */
public final class SyncStartup {
    private static final long AFTER_CHANGE_MS = 4_000;
    private static boolean started;
    private static Context app;

    private SyncStartup() {}

    private static final Runnable SOON = () -> App.io.execute(() -> Syncer.syncAll(app, false));

    public static synchronized void start(Context c) {
        if (started) return;
        started = true;
        app = c.getApplicationContext();
        SyncStore.addListener(() -> {
            App.main.removeCallbacks(SOON);
            App.main.postDelayed(SOON, AFTER_CHANGE_MS);
        });
        try {
            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build();
            WorkManager.getInstance(app).enqueueUniquePeriodicWork("tv-sync", ExistingPeriodicWorkPolicy.KEEP, work);
        } catch (RuntimeException ignored) {
            // the sync on open and after changes still runs
        }
        App.main.postDelayed(SOON, 1_500);
    }
}
