package com.gathin.tvhomephone.sports;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** The alerts started with the app and after a restart: their channels, the 15-minute check, and one check now. */
public final class SportsStartup {
    private SportsStartup() {}

    public static void start(Context c) {
        Notifier.channels(c);
        Constraints net = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager w = WorkManager.getInstance(c);
        w.enqueueUniquePeriodicWork("sports-check", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(AlertWorker.class, 15, TimeUnit.MINUTES).setConstraints(net).build());
        w.enqueueUniqueWork("sports-now", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(AlertWorker.class).setConstraints(net).build());
    }
}
