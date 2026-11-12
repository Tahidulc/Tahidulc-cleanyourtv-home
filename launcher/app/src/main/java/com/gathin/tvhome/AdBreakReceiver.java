package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The ad-break screen brought up (or put away) over whatever is on, asked for over ADB only: only a sender holding
 * WRITE_SECURE_SETTINGS (ADB's shell, never another app) can. "--es do show" or "--es do hide"; up for ten minutes at
 * most, or until Back.
 */
public class AdBreakReceiver extends BroadcastReceiver {
    static final String ACTION = "com.gathin.tvhome.action.ADBREAK";

    @Override public void onReceive(Context c, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        String what = intent.getStringExtra("do");
        AdBreak.test(c, !"hide".equals(what));
    }
}
