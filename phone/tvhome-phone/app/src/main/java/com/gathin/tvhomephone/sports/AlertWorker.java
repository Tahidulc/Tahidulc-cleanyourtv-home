package com.gathin.tvhomephone.sports;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * The 15-minute check (WorkManager): one poll of the followed games (so alerts still come, if later, when Android will
 * not start the live watcher), the rain alert, then the next wake planned (the watcher started when a game is on).
 */
public class AlertWorker extends Worker {
    public AlertWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        try {
            Watcher.State s = Watcher.poll(c);
            Watcher.weather(c);
            Watcher.plan(c, s);
        } catch (RuntimeException e) {
            return Result.retry();
        }
        return Result.success();
    }
}
