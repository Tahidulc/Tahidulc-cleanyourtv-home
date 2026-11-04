package com.gathin.tvhomephone.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Every 15 minutes (WorkManager): a sync with every paired TV, the phone asleep or not. */
public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    @NonNull @Override public Result doWork() {
        Syncer.syncAll(getApplicationContext(), false);
        return Result.success();
    }
}
