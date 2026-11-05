package com.gathin.tvhomephone.sports;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.SyncStore;

import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * "Kick-off in 2 min": two minutes before a followed club's game, or a game you set a reminder for, a heads-up with the
 * playlist channel it will be on (AlsoShowing's best) and Watch now, which opens it in the player (a tap: Android lets
 * a tap start the player, never the app on its own). The live watcher's polls find a followed game's moment; an alarm
 * (KickOffReceiver) covers a reminded game the watcher isn't watching. Once per game.
 */
public final class KickOff {
    private KickOff() {}

    static final long SOON_MS = 2 * 60_000L;
    /** The heads-up stays this long at most (the game has started by then). */
    private static final long SHOWN_MS = 4 * 60_000L;

    /** Whether a kick-off at k is in its window at now: from two and a half to half a minute before. Pure. */
    static boolean due(long k, long now) {
        return k > 0 && now >= k - SOON_MS - 30_000L && now < k - 30_000L;
    }

    /** Whole minutes to go, at least one ("Kick-off in 2 min"). Pure. */
    static int minutes(long k, long now) {
        return (int) Math.max(1, Math.round((k - now) / 60_000.0));
    }

    /**
     * When to wake for the next of these kick-offs: two minutes before the first whose window has not passed yet (now
     * when one is in it already), -1 when there is none. Pure.
     */
    static long alarmAt(List<Long> kickoffs, long now) {
        long best = -1;
        for (Long k : kickoffs) {
            if (k == null || k <= 0 || now >= k - 30_000L) continue;
            long at = Math.max(now, k - SOON_MS);
            if (best < 0 || at < best) best = at;
        }
        return best;
    }

    /** The key a game's heads-up is remembered by (AlertLog). */
    static String key(String id) {
        return "KO2|" + id;
    }

    /** From each poll: the followed games and the reminded ones in their window, told now. Off the main thread. */
    static void check(Context c, List<Game> followed, long now) {
        HashSet<String> told = new HashSet<>();
        if (Kinds.on(c, "kickoff")) {
            for (Game g : followed) {
                if (g.state != Game.PRE || !due(g.kickoff, now)) continue;
                post(c, g, null, now);
                told.add(g.id);
            }
        }
        if (!Kinds.on(c, "reminder")) return;
        for (JSONObject r : SyncStore.list(c, "reminder")) {
            long at = r.optLong("at");
            if (!"game".equals(r.optString("kind")) || !due(at, now) || told.contains(r.optString("id"))) continue;
            Game g = null;
            for (Game f : followed) if (f.id != null && f.id.equals(r.optString("id"))) g = f;
            if (g == null && !r.optString("slug").isEmpty()) {
                LocalDate day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate();
                for (Game s : Espn.scoreboard(r.optString("slug"), day, 60_000L)) if (s.id != null && s.id.equals(r.optString("id"))) g = s;
            }
            if (g != null && g.state != Game.PRE) continue;
            if (g != null) post(c, g, r.optString("channel"), now);
            else postPlain(c, r, now);
        }
    }

    private static void post(Context c, Game g, String reminderChannel, long now) {
        if (g.id == null || AlertLog.sent(c, key(g.id)) || MuteReceiver.muted(c, g.id)) return;
        AlertLog.markSent(c, key(g.id));
        com.gathin.tvhomephone.live.Channel ch;
        try {
            ch = com.gathin.tvhomephone.live.AlsoShowing.best(c, g);
        } catch (RuntimeException e) {
            ch = null;
        }
        String fixture = g.homeName + " v " + g.awayName;
        String on = ch != null ? ch.name : reminderChannel;
        String text = on == null || on.isEmpty() ? fixture : c.getString(R.string.f2_kickoff_on, fixture, on);
        NotificationCompat.Builder b = builder(c, c.getString(R.string.f2_kickoff_title, minutes(g.kickoff, now)), text, g.id);
        Bitmap comp = NotifCard.logo(c, Competitions.logo(g), Ui.dp(c, 48), Ui.dp(c, 48));
        if (comp != null) b.setLargeIcon(comp);
        if (ch != null) {
            PendingIntent watch = PendingIntent.getActivity(c, ("ko2:watch:" + g.id).hashCode(),
                    com.gathin.tvhomephone.live.PlayerActivity.intent(c, ch.key), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.setContentIntent(watch);
            b.addAction(R.drawable.live_ic_play, c.getString(R.string.f2_kickoff_watch), watch);
        } else {
            b.setContentIntent(Notifier.open(c, g));
        }
        b.addAction(R.drawable.live_ic_close, c.getString(R.string.f2_kickoff_later), KickOffReceiver.dismiss(c, g.id));
        Notifier.notify(c, id(g.id), b);
    }

    /** A reminded game ESPN no longer lists: its name and channel only. */
    private static void postPlain(Context c, JSONObject r, long now) {
        String id = r.optString("id", r.optString("title") + r.optLong("at"));
        if (AlertLog.sent(c, key(id))) return;
        AlertLog.markSent(c, key(id));
        String on = r.optString("channel");
        String title = r.optString("title");
        NotificationCompat.Builder b = builder(c, c.getString(R.string.f2_kickoff_title, minutes(r.optLong("at"), now)),
                on.isEmpty() ? title : c.getString(R.string.f2_kickoff_on, title, on), id);
        b.setContentIntent(Notifier.home(c));
        b.addAction(R.drawable.live_ic_close, c.getString(R.string.f2_kickoff_later), KickOffReceiver.dismiss(c, id));
        Notifier.notify(c, id(id), b);
    }

    private static NotificationCompat.Builder builder(Context c, String title, String text, String id) {
        return new NotificationCompat.Builder(c, Notifier.REMINDERS)
                .setSmallIcon(R.drawable.sports_ic_ball)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(c.getColor(R.color.bug_blue_top))
                .setAutoCancel(true)
                .setTimeoutAfter(SHOWN_MS)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
    }

    static int id(String gameId) {
        return ("ko2:" + gameId).hashCode();
    }

    /** The followed kick-offs the watcher found (today and tomorrow) and the reminded games' times. */
    static List<Long> times(Context c) {
        List<Long> out = new ArrayList<>();
        for (String k : c.getApplicationContext().getSharedPreferences("sports_watch", Context.MODE_PRIVATE)
                .getStringSet("fixtures", new HashSet<>())) {
            try {
                out.add(Long.parseLong(k));
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        for (JSONObject r : SyncStore.list(c, "reminder")) if ("game".equals(r.optString("kind")) && r.optLong("at") > 0) out.add(r.optLong("at"));
        return out;
    }

    /** The alarm for the next heads-up (exact: the app may use exact alarms), replacing the one before. */
    public static void plan(Context c) {
        long at = alarmAt(times(c), System.currentTimeMillis());
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 43, new Intent(c, KickOffReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (at < 0) {
            am.cancel(pi);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
