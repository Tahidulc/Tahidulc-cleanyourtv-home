package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * Android's installer's word on the launcher's own update (Updater): asked to put up its question first (before Android
 * 12), that question opened; not gone in, why. When it does go in, Android restarts the launcher as the new version and
 * this is seldom heard.
 */
public class UpdateStatus extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @SuppressWarnings("deprecation") Intent ask = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (ask != null) {
                ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(ask);
                    Updater.asked(c);
                    return;
                } catch (RuntimeException e) {
                    Updater.failed(c, "Android's question could not be shown");
                    return;
                }
            }
            Updater.failed(c, "Android wanted to ask first");
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Updater.installed(c);
        } else {
            String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Updater.failed(c, why == null || why.isEmpty() ? "Android said no (" + status + ")" : why);
        }
    }
}
