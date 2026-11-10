package com.gathin.tvhomephone.live;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.gathin.tvhomephone.MainActivity;
import com.gathin.tvhomephone.R;

import java.util.Locale;

/**
 * A playlist about to run out, told in time to renew it: a week before and a day before (as its provider says, Sources'
 * Info), once each for that playlist and that date, so a renewed account (a new date) is told about afresh. On the
 * "Your playlists" channel; tapping one opens Settings at Live TV. Checked once a day (PlaylistWorker) and after the
 * playlists load (PlaylistCare).
 */
public final class Expiry {
    private Expiry() {}

    public static final String CHANNEL = "live_playlists";
    static final long DAY = 86_400_000L;
    /** The two warnings: a week before, a day before. */
    static final int WEEK = 7, LAST_DAY = 1;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("live_expiry", Context.MODE_PRIVATE);
    }

    /**
     * Which warning is due for an account running out at expires (ms), now: WEEK from seven days before to a day
     * before, LAST_DAY in its last day, 0 otherwise (unknown, never runs out, or already run out). Pure.
     */
    static int due(long expires, long now) {
        if (expires <= 0) return 0;
        long left = expires - now;
        if (left <= 0) return 0;
        if (left <= LAST_DAY * DAY) return LAST_DAY;
        if (left <= WEEK * DAY) return WEEK;
        return 0;
    }

    /** "in 6 days", "tomorrow", "today": how long is left, in words. Pure. */
    static String left(long expires, long now) {
        long days = (long) Math.ceil((expires - now) / (double) DAY);
        if (days <= 0) return "today";
        if (days == 1) return "within a day";
        return "in " + days + " days";
    }

    /** The key a warning is remembered by: the playlist, its date, which warning. */
    static String key(String id, long expires, int warning) {
        return id + "|" + expires + "|" + warning;
    }

    /** Every playlist's warnings that are due and not yet given, given now. */
    public static void check(Context c) {
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        for (Sources.Entry e : Sources.list(c)) {
            Sources.Info i = Sources.info(c, e.id);
            if (i == null) continue;
            int w = due(i.expires, now);
            if (w == 0 || p.getBoolean(key(e.id, i.expires, w), false)) continue;
            // The day-before warning stands for the week-before one too (a first check in the last day gives one).
            p.edit().putBoolean(key(e.id, i.expires, w), true).putBoolean(key(e.id, i.expires, WEEK), true).apply();
            notify(c, e, i, now);
        }
    }

    static void channel(Context c) {
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if (m == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.f2_channel_playlists), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(c.getString(R.string.f2_channel_playlists_about));
        m.createNotificationChannel(ch);
    }

    private static void notify(Context c, Sources.Entry e, Sources.Info i, long now) {
        show(c, e.id, e.name, i.expires, Library.get(c).countIn(e.id), now);
    }

    /**
     * A test of the warning (TestAlerts): a made-up playlist running out in six days, under its own id; nothing is
     * remembered, so a real warning still comes.
     */
    public static void test(Context c) {
        long now = System.currentTimeMillis();
        show(c, "TEST|expiry", c.getString(R.string.live7_test_playlist), now + 6 * DAY - 60_000L, 1200, now);
    }

    private static void show(Context c, String id, String name, long expires, int channels, long now) {
        channel(c);
        String day = java.time.Instant.ofEpochMilli(expires).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMM", Locale.UK));
        String title = c.getString(R.string.f2_expiry_title, name, left(expires, now));
        String text = channels > 0 ? c.getString(R.string.f2_expiry_text_count, day, channels) : c.getString(R.string.f2_expiry_text, day);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.live_ic_guide)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setColor(0xFFFFC93C)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(settings(c));
        try {
            NotificationManagerCompat.from(c).notify(("expiry:" + id).hashCode(), b.build());
        } catch (SecurityException ignored) {
            // notifications not allowed on this phone
        }
    }

    /** Opens Settings at Live TV's playlists. */
    static PendingIntent settings(Context c) {
        Intent i = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_PLAYLISTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 7301, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
