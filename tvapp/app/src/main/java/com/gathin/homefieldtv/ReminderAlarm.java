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
 * in the guide), exact where Android allows it. It never wakes the TV: while its screen is off the card waits, looked at
 * again every half minute, and shows once the screen is on if the programme began less than three minutes before (too
 * late after that, it goes). Made again when a reminder is set or cancelled, when the app starts, when the TV starts
 * (BOOT_COMPLETED) and after an update (MY_PACKAGE_REPLACED), since Android forgets alarms then.
 */
public final class ReminderAlarm extends BroadcastReceiver {
    private static final String FIRE = "com.gathin.homefieldtv.REMINDER";
    /** A card due while the screen is off: looked at again this often (never waking the TV for it). */
    private static final long RECHECK_MS = 30_000L;

    private static PendingIntent pending(Context app) {
        Intent i = new Intent(app, ReminderAlarm.class).setAction(FIRE);
        return PendingIntent.getBroadcast(app, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The screen on (else the TV is off or asleep). */
    private static boolean on(Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm == null || pm.isInteractive();
    }

    /**
     * The alarm for the next card due: at once for one due already (in half a minute while the screen is off, to look
     * again), or none when there is none.
     */
    static void schedule(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = app.getSystemService(AlarmManager.class);
        if (am == null) return;
        long now = System.currentTimeMillis();
        long at = Reminders.due(app, now).isEmpty() ? Reminders.nextCard(app, now) : now + (on(app) ? 1000L : RECHECK_MS);
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
            // Only on a TV whose screen is on: while it is off the card is kept (it isn't woken for it), and shows once
            // the screen is on again while the programme has only just begun.
            if (on(c)) {
                for (Reminders.Reminder r : due) {
                    ReminderCard.show(c, r);
                    Reminders.remove(c, r.key, r.start);
                }
            }
        }
        schedule(c);
    }
}
