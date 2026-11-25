package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * One kind of alert switched on or off from ADB, as its switch in Settings › Notifications does (NotifyPrefs.set), so a
 * TV can be set without the remote. Only the shell can send it (WRITE_SECURE_SETTINGS, as GuardConfig).
 * {@code adb shell am broadcast -a com.gathin.tvhome.NOTIFY -n com.gathin.tvhome/.NotifyConfig --es kind table --ez on false}
 * The result data says what the kind's switch is now ("table=false"), or that no kind has that id.
 */
public class NotifyConfig extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        setResultData(apply(context, intent.getStringExtra("kind"), intent.hasExtra("on") ? intent.getBooleanExtra("on", true) : null));
    }

    /** Sets the kind's switch when on is given; what it is now, or why nothing was done. */
    static String apply(Context c, String id, Boolean on) {
        NotifyKinds.Kind k = NotifyKinds.get(id);
        if (k == null) return "no kind " + id;
        if (on != null) NotifyPrefs.set(c, k, on);
        return k.id + "=" + NotifyPrefs.on(c, k.id);
    }
}
