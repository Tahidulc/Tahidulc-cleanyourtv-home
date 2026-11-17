package com.gathin.homefieldtv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import com.gathin.homefieldtv.engine.Reminders;

import java.util.List;

/**
 * Reminders' alarm: one at a time, for the next card due (a minute before a programme the family asked to be reminded of
 * in the guide), exact where Android allows it. It never wakes the TV: a TV that is off stays off, and its card, too
 * late by the time the TV is on again, isn't shown. Made again when a reminder is set or cancelled, when the app starts,
 * when the TV starts (BOOT_COMPLETED) and after an update (MY_PACKAGE_REPLACED), since Android forgets alarms then.
 */
public final class ReminderAlarm extends BroadcastReceiver {
    private static final String FIRE = "com.gathin.homefieldtv.REMINDER";

    private static PendingIntent pending(Context app) {
        Intent i = new Intent(app, ReminderAlarm.class).setAction(FIRE);
        return PendingIntent.getBroadcast(app, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The alarm for the next card due (at once for one due already), or none when there is none. */
    static void schedule(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = app.getSystemService(AlarmManager.class);
        if (am == null) return;
        long now = System.currentTimeMillis();
        long at = Reminders.due(app, now).isEmpty() ? Reminders.nextCard(app, now) : now + 1000L;
        PendingIntent pi = pending(app);
        am.cancel(pi);
        if (at == Long.MAX_VALUE) return;
        // Not a waking alarm: when the TV is off it waits, and is let go once the TV is on again (too late by then).
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setWindow(AlarmManager.RTC, at, 30_000L, pi);
        else am.setExact(AlarmManager.RTC, at, pi);
    }

    @Override public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (FIRE.equals(a)) {
            long now = System.currentTimeMillis();
            List<Reminders.Reminder> due = Reminders.due(c, now);
            PowerManager pm = c.getSystemService(PowerManager.class);
            // Only on a TV that is on: one that is off isn't woken for it.
            boolean on = pm == null || pm.isInteractive();
            for (Reminders.Reminder r : due) {
                if (on) ReminderCard.show(c, r);
                Reminders.remove(c, r.key, r.start);
            }
        }
        schedule(c);
    }
}
