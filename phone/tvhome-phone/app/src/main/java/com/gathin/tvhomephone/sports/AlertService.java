package com.gathin.tvhomephone.sports;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.content.ContextCompat;

import com.gathin.tvhomephone.App;

/**
 * The live games' watcher: a foreground service (with a quiet "Following live" notification showing the scores) that
 * polls every 30 seconds while a followed game is on or about to start, then stops and leaves an alarm for the next.
 * Started from the app, from the 15-minute check, or from an exact alarm (which Android lets start it from the
 * background); where Android refuses, the 15-minute check sends the alerts instead.
 */
public class AlertService extends Service {
    private static final long TICK_MS = 30_000L, NEAR_MS = 20 * 60_000L;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            App.io.execute(() -> {
                Watcher.State s;
                try {
                    s = Watcher.poll(AlertService.this, true);
                } catch (RuntimeException e) {
                    s = new Watcher.State();
                }
                Watcher.State got = s;
                App.main.post(() -> {
                    if (!running) return;
                    long now = System.currentTimeMillis();
                    boolean near = got.nextKickoff > 0 && got.nextKickoff - now < NEAR_MS;
                    // Not before the alerts held back (the TV's minute) have gone: a full time seen as it ends.
                    if (!got.live && !near && !Watcher.holding()) {
                        finish(got);
                        return;
                    }
                    String line = got.live ? got.ongoing : "Kick-off at " + java.time.Instant.ofEpochMilli(got.nextKickoff)
                            .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    Notifier.notify(AlertService.this, Notifier.ONGOING_ID,
                            new androidx.core.app.NotificationCompat.Builder(AlertService.this, Notifier.LIVE)
                                    .setSmallIcon(com.gathin.tvhomephone.R.drawable.sports_ic_ball)
                                    .setContentTitle(getString(com.gathin.tvhomephone.R.string.sports_following))
                                    .setContentText(line).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
                                    .setContentIntent(Notifier.home(AlertService.this)));
                    App.main.postDelayed(tick, TICK_MS);
                });
            });
        }
    };

    /** Starts the watcher; false when Android will not let it start now (the 15-minute check covers it). */
    public static boolean start(Context c) {
        try {
            ContextCompat.startForegroundService(c, new Intent(c, AlertService.class));
            return true;
        } catch (RuntimeException e) {
            // Android 12 and later: not from the background without an exact alarm.
            return false;
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            android.app.Notification n = Notifier.ongoing(this, "Checking the games…");
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notifier.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(Notifier.ONGOING_ID, n);
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            App.main.post(tick);
        }
        return START_NOT_STICKY;
    }

    private void finish(Watcher.State s) {
        running = false;
        App.main.removeCallbacks(tick);
        App.io.execute(() -> Watcher.plan(getApplicationContext(), null));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        running = false;
        App.main.removeCallbacks(tick);
        super.onDestroy();
    }
}
