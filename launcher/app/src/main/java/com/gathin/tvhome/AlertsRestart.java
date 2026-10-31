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
        if (!Config.themeFlag(c, "alerts_everywhere", false)) return;
        GoalWatchService.startEverywhere(c);
    }
}
