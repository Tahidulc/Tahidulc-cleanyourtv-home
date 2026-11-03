package com.gathin.deliverytotv;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;

/**
 * Every quarter of an hour (and after a restart): the notification listener asked to be connected again, in case the
 * phone stopped it to save battery (Honor's does), so order updates keep reaching the TVs without opening the app.
 */
public class Rebind extends JobService {
    private static final int ID = 4710;

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null || js.getPendingJob(ID) != null) return;
        js.schedule(new JobInfo.Builder(ID, new ComponentName(c, Rebind.class))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build());
    }

    @Override public boolean onStartJob(JobParameters params) {
        try {
            NotificationListenerService.requestRebind(new ComponentName(this, DeliveryListener.class));
        } catch (Exception ignored) {
            // notification access not given: nothing to connect
        }
        return false;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }
}
