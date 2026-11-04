package com.gathin.tvhomephone.sports;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.notification.StatusBarNotification;

/**
 * An alert's "Mute game": no more alerts from that game (for six hours, past its end), and the ones showing go. The
 * live score in "Following live" stays.
 */
public class MuteReceiver extends BroadcastReceiver {
    static final String GAME = "game";
    private static final long FOR_MS = 6 * 3600_000L;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("sports_muted", Context.MODE_PRIVATE);
    }

    /** Whether that game's alerts are muted now. */
    public static boolean muted(Context c, String gameId) {
        return gameId != null && prefs(c).getLong(gameId, 0) > System.currentTimeMillis();
    }

    static void mute(Context c, String gameId) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        long now = System.currentTimeMillis();
        // Old games' entries go as a new one comes.
        for (java.util.Map.Entry<String, ?> x : p.getAll().entrySet()) {
            if (x.getValue() instanceof Long && (Long) x.getValue() < now) e.remove(x.getKey());
        }
        e.putLong(gameId, now + FOR_MS).apply();
    }

    static PendingIntent pending(Context c, String gameId) {
        Intent i = new Intent(c, MuteReceiver.class).putExtra(GAME, gameId);
        return PendingIntent.getBroadcast(c, ("mute:" + gameId).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra(GAME);
        if (id == null || id.isEmpty()) return;
        mute(c, id);
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if (m == null) return;
        String group = "game:" + id;
        try {
            for (StatusBarNotification n : m.getActiveNotifications()) {
                if (group.equals(n.getNotification().getGroup())) m.cancel(n.getTag(), n.getId());
            }
        } catch (RuntimeException ignored) {
            // nothing showing
        }
    }
}
