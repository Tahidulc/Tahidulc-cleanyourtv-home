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
            JSONObject update = new JSONObject()
                    .put("app", Relay.label(this, pkg))
                    .put("package", pkg)
                    .put("stage", stage)
                    .put("status", status)
                    .put("eta", eta)
                    .put("icon", Relay.icon(this, pkg));
            Relay.send(this, update);
        } catch (Exception ignored) {
        }
    }

    /** When the car app's last pop-up went, by its title: a notification that keeps updating pops up once. */
    private final Map<String, Long> carShown = new HashMap<>();

    /**
     * A car app's notification, as it is: what it says and the battery when it gives it. The same news again within
     * ten minutes (a charge counting up) only updates Home's car pill, quietly.
     */
    private void car(String pkg, String title, String body) {
        if (title.isEmpty() && body.isEmpty()) return;
        String key = title + "|" + body;
        if (key.equals(last.get(pkg))) return;
        last.put(pkg, key);
        long now = System.currentTimeMillis();
        Long shown = carShown.get(title);
        boolean quiet = shown != null && now - shown < 10 * 60_000L;
        if (!quiet) carShown.put(title, now);
        try {
            Relay.send(this, new JSONObject()
                    .put("kind", "car")
                    .put("quiet", quiet)
                    .put("app", Relay.label(this, pkg))
                    .put("package", pkg)
                    .put("title", title)
                    .put("status", body)
                    .put("battery", Relay.battery(title + " " + body))
                    .put("icon", Relay.icon(this, pkg)));
        } catch (Exception ignored) {
        }
    }

    private static String text(Bundle x, String key) {
        CharSequence c = x == null ? null : x.getCharSequence(key);
        return c == null ? "" : c.toString().trim();
    }
}
