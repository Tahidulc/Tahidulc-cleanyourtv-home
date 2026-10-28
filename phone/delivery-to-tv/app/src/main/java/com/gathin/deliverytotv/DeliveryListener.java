package com.gathin.deliverytotv;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The delivery apps' notifications (only the apps ticked in Delivery to TV; the rest are never read): an order's
 * update goes to the TVs when its stage or arrival time changes. Offers and other news from those apps, which say
 * nothing about an order, are left alone. The car app's (Tesla) go as they are.
 */
public class DeliveryListener extends NotificationListenerService {
    /** The last update passed on for each app, so a notification redrawn with the same news is not sent again. */
    private final Map<String, String> last = new HashMap<>();

    /** The car's Bluetooth coming and going: a drive starts and ends (HomeTrip). */
    private final android.content.BroadcastReceiver carLink = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, android.content.Intent i) {
            String car = Relay.car(c);
            if (car.isEmpty()) return;
            android.bluetooth.BluetoothDevice dev = i.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE);
            String name;
            try { name = dev == null ? null : dev.getName(); } catch (SecurityException e) { return; }
            if (name == null || !name.equals(car)) return;
            if (android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) HomeTrip.start(c);
            else HomeTrip.stop(c);
        }
    };

    @Override public void onListenerConnected() {
        android.content.IntentFilter f = new android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
        try { registerReceiver(carLink, f); } catch (Exception ignored) { }
    }

    @Override public void onListenerDisconnected() {
        try { unregisterReceiver(carLink); } catch (Exception ignored) { }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!Relay.apps(this).contains(pkg)) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle x = n.extras;
        String title = text(x, Notification.EXTRA_TITLE), body = text(x, Notification.EXTRA_TEXT),
                big = text(x, Notification.EXTRA_BIG_TEXT), sub = text(x, Notification.EXTRA_SUB_TEXT);
        String all = title + " \n" + body + " \n" + big + " \n" + sub;
        if (pkg.equals(Relay.TESLA)) { car(pkg, title, big.length() > body.length() ? big : body); return; }
        int stage = Relay.stage(all);
        // An order's live notification may say its progress as a bar instead of in words.
        int max = x.getInt(Notification.EXTRA_PROGRESS_MAX, 0), at = x.getInt(Notification.EXTRA_PROGRESS, 0);
        if (stage < 0 && max > 0) stage = Math.min(3, Math.round(4f * at / max));
        if (stage < 0) return;   // not about an order
        String status = !body.isEmpty() ? body : title;
        if (status.equals(body) && !title.isEmpty() && Relay.stage(title) >= 0) status = title;
        String eta = Relay.eta(all);
        String key = stage + "|" + eta + "|" + status;
        if (key.equals(last.get(pkg))) return;
        last.put(pkg, key);
        try {
            String from = Relay.restaurant(title + "\n" + body + "\n" + big);
            if (from.isEmpty()) from = Relay.savedRestaurant(this, pkg);
            JSONObject update = new JSONObject()
                    .put("app", Relay.label(this, pkg))
                    .put("package", pkg)
                    .put("stage", stage)
                    .put("status", status)
                    .put("eta", eta)
                    .put("from", from)
                    .put("items", Relay.items(this, pkg))
                    .put("icon", Relay.icon(this, pkg));
            Relay.send(this, update);
            if (stage == 4) Relay.clearItems(this, pkg);   // delivered: the next order starts afresh
        } catch (Exception ignored) {
        }
    }

    /** The car's last pop-up: when, and at what battery level. */
    private long carShownAt;
    private int carShownLevel = -1;

    /**
     * A car app's notification, as it is: what it says, the battery, the charge limit and the miles when it gives
     * them. While it charges (a notification that keeps updating) it goes to the TVs each 5% or quarter of an hour.
     */
    private void car(String pkg, String title, String body) {
        if (title.isEmpty() && body.isEmpty()) return;
        String key = title + "|" + body;
        if (key.equals(last.get(pkg))) return;
        last.put(pkg, key);
        String all = title + " " + body, low = all.toLowerCase(java.util.Locale.ROOT);
        int battery = Relay.battery(all);
        boolean charging = low.contains("charging") && !low.contains("complete") && !low.contains("stopped")
                && !low.contains("interrupted") && !low.contains("finished");
        long now = System.currentTimeMillis();
        if (charging && carShownLevel >= 0 && battery >= 0 && battery - carShownLevel < 5 && now - carShownAt < 15 * 60_000L) return;
        int from = charging ? carShownLevel : -1;
        carShownAt = now;
        carShownLevel = charging ? battery : -1;
        try {
            JSONObject u = new JSONObject()
                    .put("kind", "car")
                    .put("app", Relay.label(this, pkg))
                    .put("package", pkg)
                    .put("title", title)
                    .put("status", body)
                    .put("battery", battery)
                    .put("from", from)
                    .put("target", Relay.target(all))
                    .put("icon", Relay.icon(this, pkg));
            int miles = Relay.miles(all);
            if (miles >= 0) u.put("miles", miles);
            Relay.send(this, u);
        } catch (Exception ignored) {
        }
    }

    private static String text(Bundle x, String key) {
        CharSequence c = x == null ? null : x.getCharSequence(key);
        return c == null ? "" : c.toString().trim();
    }
}
