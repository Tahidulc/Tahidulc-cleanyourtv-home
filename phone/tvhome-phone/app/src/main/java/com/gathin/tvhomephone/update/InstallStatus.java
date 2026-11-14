package com.gathin.tvhomephone.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/**
 * Android's installer's word on Homefield's own update (AppUpdate): asked to put up its question first (before Android
 * 12, or the first time), that question opened; not gone in, why. When it does go in, Android closes the app as the new
 * version goes in and this is seldom heard (Updated speaks for it).
 */
public class InstallStatus extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent ask = Build.VERSION.SDK_INT >= 33 ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class) : legacy(intent);
            if (ask != null) {
                ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(ask);
                    AppUpdate.asked(c);
                } catch (RuntimeException e) {
                    AppUpdate.failed(c, "Android's question could not be shown. Open Homefield and tap Update again");
                }
                return;
            }
            AppUpdate.failed(c, "Android wanted to ask first. Tap Update again");
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            AppUpdate.installed(c);
        } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            AppUpdate.failed(c, "it was stopped before it went in. Tap Update to try again");
        } else {
            String why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            AppUpdate.failed(c, why == null || why.isEmpty() ? "Android said no (" + status + ")" : why);
        }
    }

    @SuppressWarnings("deprecation")
    private static Intent legacy(Intent intent) {
        return intent.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}
