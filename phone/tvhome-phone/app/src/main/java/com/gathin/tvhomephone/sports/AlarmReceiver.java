package com.gathin.tvhomephone.sports;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * The alarm fifteen minutes before a followed kick-off, a race or a reminder: the live watcher starts (an exact alarm
 * lets it start from the background); where it cannot, one check runs now instead.
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!AlertService.start(c)) {
            WorkManager.getInstance(c).enqueue(new OneTimeWorkRequest.Builder(AlertWorker.class).build());
        }
    }
}
