package com.gathin.tvhomephone.sports;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationManagerCompat;

import com.gathin.tvhomephone.App;

/**
 * KickOff's alarm two minutes before a game (a poll now: the followed games and the reminded ones in their window are
 * told, the next alarm planned), and its "Not now" (the heads-up taken away).
 */
public class KickOffReceiver extends BroadcastReceiver {
    private static final String DISMISS = "com.gathin.tvhomephone.KICKOFF_DISMISS", TV = "com.gathin.tvhomephone.KICKOFF_TV",
            GAME = "game", CHANNEL = "channel";

    static PendingIntent dismiss(Context c, String gameId) {
        Intent i = new Intent(c, KickOffReceiver.class).setAction(DISMISS).putExtra(GAME, gameId);
        return PendingIntent.getBroadcast(c, ("ko2:later:" + gameId).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent onTv(Context c, String gameId, String channel) {
        Intent i = new Intent(c, KickOffReceiver.class).setAction(TV).putExtra(GAME, gameId).putExtra(CHANNEL, channel);
        return PendingIntent.getBroadcast(c, ("ko2:tv:" + gameId).hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onReceive(Context c, Intent i) {
        if (DISMISS.equals(i.getAction())) {
            NotificationManagerCompat.from(c).cancel(KickOff.id(i.getStringExtra(GAME)));
            return;
        }
        if (TV.equals(i.getAction())) {
            // The game on the first paired TV (its player, by the channel's name); a note when it can't be.
            NotificationManagerCompat.from(c).cancel(KickOff.id(i.getStringExtra(GAME)));
            java.util.List<String> tvs = com.gathin.tvhomephone.sync.Tvs.names(c);
            String ch = i.getStringExtra(CHANNEL);
            if (tvs.isEmpty() || ch == null) return;
            PendingResult done = goAsync();
            Context app = c.getApplicationContext();
            com.gathin.tvhomephone.sync.Tvs.tuneOnTv(app, tvs.get(0), java.util.Collections.singletonList(ch), (ok, err) -> {
                if (!ok) android.widget.Toast.makeText(app, err, android.widget.Toast.LENGTH_LONG).show();
                done.finish();
            });
            return;
        }
        PendingResult done = goAsync();
        Context app = c.getApplicationContext();
        App.io.execute(() -> {
            try {
                Watcher.State s = Watcher.poll(app, false);
                Watcher.plan(app, s);
            } catch (RuntimeException ignored) {
                // the 15-minute check covers it
            } finally {
                try {
                    KickOff.plan(app);
                } finally {
                    done.finish();
                }
            }
        });
    }
}
