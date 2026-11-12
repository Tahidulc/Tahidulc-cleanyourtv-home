package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The alerts started again without waiting for Home: after the launcher is updated (an install stops its service,
 * and a person still in TiviMate would otherwise get no goals until they next went Home) and after the TV starts.
 * Only with alerts_everywhere, which keeps them running whatever is on.
 */
public class AlertsRestart extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        // Updated by its own updater: says so, and lets the update's files go.
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) Updater.started(c.getApplicationContext());
        // The notification listener (camera alarms, Continue Watching): a TCL leaves it unbound after an update until
        // it is switched off and on again, so it asks to be bound again itself.
        try {
            android.service.notification.NotificationListenerService.requestRebind(
                    new android.content.ComponentName(c, WatchHistory.class));
        } catch (RuntimeException ignored) {
            // not allowed as a listener on this TV: nothing to bind
        }
        if (!Config.themeFlag(c, "alerts_everywhere", false)) return;
        GoalWatchService.startEverywhere(c);
    }
}
