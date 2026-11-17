package com.gathin.homefieldtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.gathin.homefieldtv.prefs.General;

/**
 * Settings → General → Start when the TV starts: Homefield TV opened as the TV finishes starting. Android 10 and later
 * let it only with "Display over other apps" allowed for it (Settings says so); otherwise nothing happens.
 */
public final class BootStart extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null || !Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        if (!General.onBoot(c) || !General.mayOpenAtBoot(c)) return;
        try {
            c.startActivity(new Intent(c, TvActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // The TV wouldn't let it start: Home as ever.
        }
    }
}
