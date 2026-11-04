package com.gathin.tvhomephone.sports;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After the phone restarts: the alerts' schedule again (built by phone-sports). */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        SportsStartup.start(c);
    }
}
