package com.gathin.tvhomephone.sports;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import com.gathin.tvhomephone.App;

/**
 * The live games' watcher: a foreground service (with a quiet "Following live" notification showing the scores) that
 * polls every 30 seconds while a followed game is on or about to start, then stops and leaves an alarm for the next.
 * Started from the app, from the 15-minute check, or from an exact alarm (which Android lets start it from the
 * background); where Android refuses, the 15-minute check sends the alerts instead. Between polls, while the screen is
 * on, the notification's scoreboard clocks go on second by second (LiveCard).
 */
public class AlertService extends Service {
    private static final long TICK_MS = 30_000L, NEAR_MS = 20 * 60_000L;
    private boolean running, listening;
    /**
     * The live games as the last poll drew them, the words under them, the clocks' words last sent, and when the card
     * first went up (its time in the list of notifications). Main thread only.
     */
    private LiveCard card;
    private String line = "", sent = "";
    private long since;

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
                String words = got.live ? got.ongoing : got.nextKickoff > 0 ? "Kick-off at " + java.time.Instant.ofEpochMilli(got.nextKickoff)
                        .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";
                // The live games drawn here, off the main thread (their crests); each second's notification reuses them.
                LiveCard drawn = LiveCard.draw(AlertService.this, got.followed);
                App.main.post(() -> {
                    if (!running) return;
                    long now = System.currentTimeMillis();
                    boolean near = got.nextKickoff > 0 && got.nextKickoff - now < NEAR_MS;
                    // Not before the alerts held back (the TV's minute) have gone: a full time seen as it ends.
                    if (!got.live && !near && !Watcher.holding()) {
                        finish(got);
                        return;
                    }
                    card = drawn;
                    line = words;
                    show(now);
                    App.main.postDelayed(tick, TICK_MS);
                    second.run();
                });
            });
        }
    };

    /**
     * The scoreboard's clocks going on: on each whole second (as the app's clocks turn), while the screen is on and a
     * clock is going, the notification sent again when a clock's words have moved (LiveCard says what that costs).
     * Stops itself when no clock is going (half time, or no game on: the next poll starts it again), and goes when the
     * screen does, the service stops, or the games end.
     */
    private final Runnable second = new Runnable() {
        @Override public void run() {
            App.main.removeCallbacks(this);
            if (!running || card == null || !awake()) return;
            long now = System.currentTimeMillis();
            if (!card.words(now).equals(sent)) show(now);
            if (card.ticks(now)) App.main.postDelayed(this, LiveCard.delay(now));
        }
    };

    /** The screen going off stops the seconds; coming on, the clocks are brought up to date and go on. */
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) App.main.removeCallbacks(second);
            else second.run();
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

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            ContextCompat.registerReceiver(this, screen, f, ContextCompat.RECEIVER_NOT_EXPORTED);
            listening = true;
        } catch (RuntimeException e) {
            // the seconds then go by the checks alone (awake())
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            long now = System.currentTimeMillis();
            android.app.Notification n;
            if (card != null) {
                sent = card.words(now);
                n = Notifier.following(this, card, line, now, since).build();
            } else {
                n = Notifier.ongoing(this, "Checking the games…");
            }
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

    /** The card as it stands at now, sent. */
    private void show(long now) {
        if (card != null && since == 0) since = now;
        sent = card == null ? "" : card.words(now);
        Notifier.notify(this, Notifier.ONGOING_ID, Notifier.following(this, card, line, now, since));
    }

    /** Whether the screen is on: the only time the notification's seconds can be seen. */
    private boolean awake() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm == null || pm.isInteractive();
    }

    private void finish(Watcher.State s) {
        halt();
        App.io.execute(() -> Watcher.plan(getApplicationContext(), null));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** The polls and the seconds stopped, the card let go. */
    private void halt() {
        running = false;
        card = null;
        App.main.removeCallbacks(tick);
        App.main.removeCallbacks(second);
    }

    @Override public void onDestroy() {
        halt();
        if (listening) {
            listening = false;
            try {
                unregisterReceiver(screen);
            } catch (RuntimeException ignored) {
                // not registered after all
            }
        }
        super.onDestroy();
    }
}
