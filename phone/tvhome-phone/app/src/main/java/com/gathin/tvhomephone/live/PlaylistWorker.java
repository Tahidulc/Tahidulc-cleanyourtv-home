package com.gathin.tvhomephone.live;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Once a day: the playlists' expiry warnings that are due, then the weekly health check when it is time (PlaylistHealth). */
public class PlaylistWorker extends Worker {
    public PlaylistWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    @NonNull @Override public Result doWork() {
        Context c = getApplicationContext();
        try {
            Expiry.check(c);
        } catch (RuntimeException ignored) {
            // tomorrow
        }
        try {
            PlaylistHealth.weekly(c);
        } catch (RuntimeException ignored) {
            // next time
        }
        return Result.success();
    }
}
