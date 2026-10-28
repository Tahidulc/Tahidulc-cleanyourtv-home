package com.gathin.deliverytotv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.util.ArrayDeque;

/**
 * A drive in the car (its Bluetooth connected): the phone's own position, and once it is heading home, the time and
 * miles left to the TVs that show them ("home_eta"): when the trip home starts, at about 10, 5 and 2 minutes, and
 * when it is almost there. It runs only while the phone is connected to the car.
 */
public class HomeTrip extends Service implements LocationListener {
    private static final String CHANNEL = "trip";
    /** Roads are longer than the straight line to home: about this much on a drive in town. */
    private static final double ROAD = 1.3;

    /** Where the phone has been this trip: {millis, miles from home}. */
    private final ArrayDeque<double[]> track = new ArrayDeque<>();
    private double startMiles = -1;
    private boolean heading, arrived;
    private int lastStep = Integer.MAX_VALUE;

    static void start(Context c) {
        if (!Relay.homeSet(c)) return;
        try {
            Intent i = new Intent(c, HomeTrip.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) {
            // not allowed from here: no arrival times this drive
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, HomeTrip.class));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Time to home", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Time to home").setContentText("Sending your time to home to the TV while you drive")
                .setOngoing(true).build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(7, n);
            LocationManager lm = getSystemService(LocationManager.class);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15_000L, 40f, this, Looper.getMainLooper());
        } catch (Exception e) {
            stopSelf();   // no location permission (or not allowed now): nothing to do
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        try { getSystemService(LocationManager.class).removeUpdates(this); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public void onLocationChanged(Location l) {
        double miles = Relay.milesFromHome(this, l.getLatitude(), l.getLongitude());
        if (miles < 0) return;
        long now = System.currentTimeMillis();
        track.addLast(new double[]{now, miles});
        while (track.size() > 2 && now - track.peekFirst()[0] > 6 * 60_000L) track.pollFirst();
        if (arrived) return;
        if (miles < 0.25) {
            // Home: once, if it was a trip home.
            if (heading) send(miles, 0, true);
            arrived = true;
            return;
        }
        // Heading home: closer by a quarter of a mile or more over the last few minutes.
        double[] first = track.peekFirst();
        double gained = first[1] - miles, minutes = (now - first[0]) / 60_000.0;
        if (minutes < 1.5 || gained < 0.25) return;
        double speed = Math.max(0.2, Math.min(1.1, gained / minutes));   // miles of straight line closer per minute
        int eta = (int) Math.max(1, Math.round(miles / speed));
        if (!heading) {
            heading = true;
            startMiles = miles;
            send(miles * ROAD, eta, false);
            lastStep = step(eta);
            return;
        }
        int step = step(eta);
        if (step < lastStep) {
            lastStep = step;
            send(miles * ROAD, eta, false);
        }
    }

    /** The stage of the trip by minutes left: 10, 5, 2 (each sent once). */
    private static int step(int eta) {
        return eta <= 2 ? 2 : eta <= 5 ? 5 : eta <= 10 ? 10 : 99;
    }

    private void send(double roadMiles, int minutes, boolean arriving) {
        try {
            double progress = startMiles > 0 ? 1 - roadMiles / (startMiles * ROAD) : 0;
            Relay.sendHome(this, new JSONObject().put("kind", "home_eta").put("minutes", minutes)
                    .put("miles", Math.round(roadMiles * 10) / 10.0).put("progress", Math.max(0, Math.min(1, progress)))
                    .put("arriving", arriving));
        } catch (Exception ignored) {
        }
    }
}
