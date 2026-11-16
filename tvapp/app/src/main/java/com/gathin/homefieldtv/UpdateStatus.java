package com.gathin.homefieldtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

/**
 * Android's installer's word on an update Homefield TV handed it (Updates): its question to put up ("Update?"), done,
 * or why not. Only Android's installer sends it (not exported; the PendingIntent Updates gave it).
 */
public final class UpdateStatus extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null || !Updates.ACTION_STATUS.equals(i.getAction())) return;
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent ask = confirm(i);
                if (ask == null) {
                    Updates.failed("Android didn't ask about the update: try Update now again");
                    return;
                }
                ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(ask);
                    Updates.asking();
                } catch (RuntimeException e) {
                    Updates.failed("Android's question about the update couldn't open");
                }
                return;
            }
            case PackageInstaller.STATUS_SUCCESS:
                Updates.installed();
                return;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                Updates.failed("Not updated: the update was cancelled");
                return;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                Updates.failed("Not updated: the TV needs more free space");
                return;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                Updates.failed("Not updated: Android says this version doesn't fit this TV");
                return;
            default: {
                String why = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Updates.failed("Not updated" + (why == null || why.isEmpty() ? "" : ": " + why));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static Intent confirm(Intent i) {
        return Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class) : i.getParcelableExtra(Intent.EXTRA_INTENT);
    }
}
