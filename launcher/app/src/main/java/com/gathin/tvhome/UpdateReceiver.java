package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * An update of the launcher put ready on the TV (Updater) installed now, asked for over ADB; only a sender holding
 * WRITE_SECURE_SETTINGS (ADB's shell, never another app) can. As Check for updates in the Control Centre does, the card
 * showing how far it has got.
 */
public class UpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (Updater.ACTION_UPDATE.equals(intent.getAction())) Updater.check(c, false);
    }
}
