package com.gathin.tvhomephone.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Android has put a new version of Homefield in (heard by that new version): when it came from the app's own update
 * (AppUpdate), a note that it is done, and the app opened again where it can.
 */
public class Updated extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) AppUpdate.replaced(c.getApplicationContext());
    }
}
