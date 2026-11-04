package com.gathin.tvhomephone.sports;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;

/**
 * The alerts as phone notifications: one per event, grouped per game, the scoring club's crest beside it and the
 * competition's logo by its title (the channel's for where it is on), in the channels Settings lists (Goals and
 * scores, F1, Weather, Reminders, and the quiet Live score while a game is on). Tapping one opens the game
 * (GameActivity).
 */
public final class Notifier {
    private Notifier() {}

    public static final String ALERTS = "sports_alerts", LIVE = "sports_live", F1 = "sports_f1", WEATHER = "sports_weather",
            REMINDERS = "sports_reminders";
    public static final int ONGOING_ID = 4100;

    public static void channels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager m = c.getSystemService(NotificationManager.class);
        if (m == null) return;
        m.createNotificationChannel(new NotificationChannel(ALERTS, c.getString(R.string.sports_channel_alerts), NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel live = new NotificationChannel(LIVE, c.getString(R.string.sports_channel_live), NotificationManager.IMPORTANCE_LOW);
        live.setShowBadge(false);
        m.createNotificationChannel(live);
        m.createNotificationChannel(new NotificationChannel(F1, c.getString(R.string.sports_channel_f1), NotificationManager.IMPORTANCE_HIGH));
        m.createNotificationChannel(new NotificationChannel(WEATHER, c.getString(R.string.sports_channel_weather), NotificationManager.IMPORTANCE_DEFAULT));
        m.createNotificationChannel(new NotificationChannel(REMINDERS, c.getString(R.string.sports_channel_reminders), NotificationManager.IMPORTANCE_HIGH));
    }

    /**
     * Sends one alert, if its kind and its competition (or F1) are switched on and it was not sent before. Off the main
     * thread (crests and logos).
     */
    public static void post(Context c, Events.Alert a) {
        if (!Kinds.on(c, a.kind) || !Competitions.allowed(Competitions.saved(c), a) || AlertLog.sent(c, a.key)) return;
        AlertLog.markSent(c, a.key);
        AlertLog.add(c, a);
        String channel = a.kind.startsWith("f1") ? F1 : "weather".equals(a.kind) ? WEATHER : "reminder".equals(a.kind) ? REMINDERS : ALERTS;
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.sports_ic_ball)
                .setContentTitle(a.title)
                .setContentText(a.text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(a.text))
                .setColor(c.getColor(R.color.bug_blue_top))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        if (a.game != null) {
            b.setGroup("game:" + a.game.id);
            b.setContentIntent(open(c, a.game));
            Bitmap crest = crest(c, a);
            if (crest != null) b.setLargeIcon(crest);
        } else {
            b.setContentIntent(home(c));
        }
        withLogos(c, a, b);
        notify(c, (a.key).hashCode(), b);
        if (a.game != null) summary(c, a.game, channel);
    }

    /** The group's summary, so a game's alerts sit together. */
    private static void summary(Context c, Game g, String channel) {
        NotificationCompat.Builder s = new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.sports_ic_ball)
                .setContentTitle(g.homeName + " " + (g.state == Game.PRE ? "v" : g.score()) + " " + g.awayName)
                .setGroup("game:" + g.id)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setColor(c.getColor(R.color.bug_blue_top))
                .setContentIntent(open(c, g));
        notify(c, ("summary:" + g.id).hashCode(), s);
    }

    /** The quiet ongoing notification while a followed game is live: its score and minute. */
    public static android.app.Notification ongoing(Context c, String text) {
        return new NotificationCompat.Builder(c, LIVE)
                .setSmallIcon(R.drawable.sports_ic_ball)
                .setContentTitle(c.getString(R.string.sports_following))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setColor(c.getColor(R.color.sports))
                .setContentIntent(home(c))
                .build();
    }

    static void notify(Context c, int id, NotificationCompat.Builder b) {
        try {
            NotificationManagerCompat.from(c).notify(id, b.build());
        } catch (SecurityException ignored) {
            // notifications not allowed on this phone
        }
    }

    private static Bitmap crest(Context c, Events.Alert a) {
        Game g = a.game;
        String url = g.homeLogo;
        if (a.text != null && g.awayName != null && a.text.contains("for " + g.awayName)) url = g.awayLogo;
        if (a.title.contains(" · " + g.awayName) && !a.title.contains(g.homeName)) url = g.awayLogo;
        if (url == null) return null;
        try {
            int s = Ui.dp(c, 64);
            return Glide.with(c.getApplicationContext()).asBitmap().load(url).submit(s, s).get(8, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- the competition's and the channel's logos --------------------------------------------------------------

    /**
     * The alert with its competition's logo beside the title and, for a reminder or a kick-off, the channel's logo for
     * where it is on (each on a navy chip: the logos are drawn for dark grounds). As it is when neither is known.
     */
    private static void withLogos(Context c, Events.Alert a, NotificationCompat.Builder b) {
        String slug = a.kind.startsWith("f1") ? Competitions.F1 : a.game != null ? a.game.slug : a.slug;
        String compUrl = a.game != null && !a.kind.startsWith("f1") ? Competitions.logo(a.game) : Competitions.logo(slug);
        String channel = a.channel;
        if ((channel == null || channel.isEmpty()) && a.game != null && "kickoff".equals(a.kind) && a.game.state != Game.POST) {
            channel = Channels.best(c, a.game);
        }
        String channelUrl = channel == null || channel.isEmpty() ? null : ChannelLogos.find(c, channel);
        Bitmap comp = bitmap(c, compUrl, Ui.dp(c, 24), Ui.dp(c, 24));
        Bitmap ch = bitmap(c, channelUrl, Ui.dp(c, 120), Ui.dp(c, 18));
        if (comp == null && ch == null) return;
        String text = a.text == null ? "" : a.text;
        // The channel's name goes from the words when its logo stands for it ("At 20:00 on Sky Sports F1").
        if (ch != null && a.channel != null && !a.channel.isEmpty()) text = text.replace(" on " + a.channel, "");
        String compName = Competitions.F1.equals(slug) ? "Formula 1" : a.game != null && a.game.leagueName != null ? a.game.leagueName
                : slug == null || slug.isEmpty() ? "" : Espn.name(slug);
        b.setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(views(c, R.layout.sports_notification, a.title, text, comp, compName, ch, channel))
                .setCustomBigContentView(views(c, R.layout.sports_notification_big, a.title, text, comp, compName, ch, channel));
    }

    private static RemoteViews views(Context c, int layout, String title, String text, Bitmap comp, String compName, Bitmap ch,
                                     String channel) {
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        v.setTextViewText(R.id.sports_notif_title, title);
        v.setTextViewText(R.id.sports_notif_text, text);
        if (comp != null) {
            v.setImageViewBitmap(R.id.sports_notif_logo, comp);
            v.setContentDescription(R.id.sports_notif_logo, compName);
        } else {
            v.setViewVisibility(R.id.sports_notif_logo, View.GONE);
        }
        if (ch != null) {
            v.setImageViewBitmap(R.id.sports_notif_channel, ch);
            v.setContentDescription(R.id.sports_notif_channel, "Live on " + channel);
        } else {
            v.setViewVisibility(R.id.sports_notif_channel, View.GONE);
        }
        return v;
    }

    /** A logo fitted in w by h pixels, or null when there is none or it does not come in a few seconds. */
    private static Bitmap bitmap(Context c, String url, int w, int h) {
        if (url == null || url.isEmpty()) return null;
        try {
            return Glide.with(c.getApplicationContext()).asBitmap().load(url).fitCenter().submit(w, h).get(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    static PendingIntent open(Context c, Game g) {
        Intent i = GameActivity.intent(c, g);
        return PendingIntent.getActivity(c, g.id == null ? 0 : g.id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent home(Context c) {
        Intent i = new Intent(c, com.gathin.tvhomephone.MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
