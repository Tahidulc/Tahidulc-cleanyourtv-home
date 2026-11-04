package com.gathin.tvhomephone.sports;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** The live games' watcher, a foreground service while one is on (built by phone-sports). */
public class AlertService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf();
        return START_NOT_STICKY;
    }
}
