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
        // Muted from an earlier alert's "Mute game": kept in the log, not shown.
        if (a.game != null && MuteReceiver.muted(c, a.game.id)) return;
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
        } else {
            b.setContentIntent(home(c));
        }
        // Watch match first: the game on the family's own playlist (AlsoShowing's best channel for it).
        if (a.game != null && a.game.state != Game.POST && !a.kind.startsWith("f1")) watchMatch(c, a.game, b);
        if (!(NotifCard.drawn(a) && carded(c, a, b)) && !drawnOther(c, a, b)) {
            if (a.game != null) {
                Bitmap crest = crest(c, a);
                if (crest != null) b.setLargeIcon(crest);
            }
            withLogos(c, a, b);
        }
        notify(c, (a.key).hashCode(), b);
        if (a.game != null) summary(c, a.game, channel);
    }

    /**
     * The other drawn cards: the line-ups on a pitch, the F1 podium. Folded, the competition's (or F1's) logo with the
     * words; opened out, the card. False when there is none to draw.
     */
    private static boolean drawnOther(Context c, Events.Alert a, NotificationCompat.Builder b) {
        Bitmap card = null;
        String title = a.title, line = a.text == null ? "" : a.text.replace('\n', ' ');
        try {
            if (a.key.startsWith("L|") && a.game != null && a.summary != null) {
                card = NotifCard.lineups(c, a.game, a.summary);
                title = "Line-ups · " + a.game.homeName + " v " + a.game.awayName;
                line = (a.summary.homeFormation.isEmpty() ? "" : a.game.homeName + " " + a.summary.homeFormation + " · ")
                        + (a.summary.awayFormation.isEmpty() ? "" : a.game.awayName + " " + a.summary.awayFormation);
            } else if ("f1result".equals(a.kind)) {
                card = NotifCard.podium(c, a);
                title = a.title == null ? "" : a.title.replace("F1 RESULT · ", "Podium · ");
            }
        } catch (RuntimeException e) {
            card = null;
        }
        if (card == null) return false;
        String slug = a.kind.startsWith("f1") ? Competitions.F1 : a.game != null ? a.game.slug : a.slug;
        String compUrl = a.game != null && !a.kind.startsWith("f1") ? Competitions.logo(a.game) : Competitions.logo(slug);
        Bitmap comp = NotifCard.logo(c, compUrl, Ui.dp(c, 24), Ui.dp(c, 24));
        RemoteViews small = new RemoteViews(c.getPackageName(), R.layout.sports_notif_match);
        small.setTextViewText(R.id.sports_notif_title, title);
        small.setTextViewText(R.id.sports_notif_text, line);
        if (comp != null) small.setImageViewBitmap(R.id.sports_notif_logo, comp);
        else small.setViewVisibility(R.id.sports_notif_logo, View.GONE);
        small.setViewVisibility(R.id.sports_notif_crest, View.GONE);
        RemoteViews big = new RemoteViews(c.getPackageName(), R.layout.sports_notif_card);
        big.setImageViewBitmap(R.id.sports_notif_card, card);
        big.setContentDescription(R.id.sports_notif_card, title + ". " + (a.text == null ? "" : a.text));
        b.setContentTitle(title).setContentText(line)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomHeadsUpContentView(small)
                .setCustomBigContentView(big);
        if (a.game != null) {
            b.addAction(R.drawable.sports_ic_ball, c.getString(R.string.sports_notif_match), open(c, a.game));
            b.addAction(R.drawable.live_ic_bell, c.getString(R.string.sports_notif_mute), MuteReceiver.pending(c, a.game.id));
        }
        return true;
    }

    /**
     * A game's moment as its card: folded, the competition's logo, what happened and who, and the crest of the side it
     * was for; opened out, NotifCard's drawing. Watch (on the channel, when the playlist has it), Match and Mute game
     * under it. False when the card could not be drawn.
     */
    private static boolean carded(Context c, Events.Alert a, NotificationCompat.Builder b) {
        Game g = a.game;
        String channel = a.channel;
        if ((channel == null || channel.isEmpty()) && g.state != Game.POST) channel = Channels.best(c, g);
        Bitmap chLogo = null;
        if ("kickoff".equals(a.kind) && channel != null && !channel.isEmpty()) {
            chLogo = NotifCard.logo(c, ChannelLogos.find(c, channel), Ui.dp(c, 110), Ui.dp(c, 18));
        }
        Bitmap card;
        try {
            card = NotifCard.match(c, a, chLogo);
        } catch (RuntimeException e) {
            card = null;
        }
        if (card == null) return false;
        String title = NotifCard.title(a), line = NotifCard.line(a);
        String side = NotifCard.side(a);
        Bitmap comp = NotifCard.logo(c, Competitions.logo(g), Ui.dp(c, 24), Ui.dp(c, 24));
        Bitmap crest = NotifCard.logo(c, "away".equals(side) ? g.awayLogo : g.homeLogo, Ui.dp(c, 30), Ui.dp(c, 30));
        RemoteViews small = new RemoteViews(c.getPackageName(), R.layout.sports_notif_match);
        small.setTextViewText(R.id.sports_notif_title, title);
        small.setTextViewText(R.id.sports_notif_text, line);
        if (comp != null) {
            small.setImageViewBitmap(R.id.sports_notif_logo, comp);
            small.setContentDescription(R.id.sports_notif_logo, g.leagueName);
        } else {
            small.setViewVisibility(R.id.sports_notif_logo, View.GONE);
        }
        if (crest != null) {
            small.setImageViewBitmap(R.id.sports_notif_crest, crest);
            small.setContentDescription(R.id.sports_notif_crest, "away".equals(side) ? g.awayName : g.homeName);
        } else {
            small.setViewVisibility(R.id.sports_notif_crest, View.GONE);
        }
        RemoteViews big = new RemoteViews(c.getPackageName(), R.layout.sports_notif_card);
        big.setImageViewBitmap(R.id.sports_notif_card, card);
        big.setContentDescription(R.id.sports_notif_card, title + ". " + line);
        b.setContentTitle(title)
                .setContentText(line)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomHeadsUpContentView(small)
                .setCustomBigContentView(big);
        b.addAction(R.drawable.sports_ic_ball, c.getString(R.string.sports_notif_match), open(c, g));
        b.addAction(R.drawable.live_ic_bell, c.getString(R.string.sports_notif_mute), MuteReceiver.pending(c, g.id));
        return true;
    }

    /**
     * An alert that happened while the phone was off or asleep for long: kept in the app's alerts, marked as missed,
     * never posted late (so a phone coming back on does not bring a run of old goals).
     */
    public static void missed(Context c, Events.Alert a) {
        if (!Kinds.on(c, a.kind) || !Competitions.allowed(Competitions.saved(c), a) || AlertLog.sent(c, a.key)) return;
        AlertLog.markSent(c, a.key);
        AlertLog.add(c, a);
        android.content.SharedPreferences p = c.getApplicationContext().getSharedPreferences("sports_missed", Context.MODE_PRIVATE);
        p.edit().putString(a.key, a.kind + "\t" + a.title + "\t" + (a.text == null ? "" : a.text) + "\t" + System.currentTimeMillis()).apply();
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
        return following(c, text).build();
    }

    private static NotificationCompat.Builder following(Context c, String text) {
        return new NotificationCompat.Builder(c, LIVE)
                .setSmallIcon(R.drawable.sports_ic_ball)
                .setContentTitle(c.getString(R.string.sports_following))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setColor(c.getColor(R.color.sports))
                .setContentIntent(home(c));
    }

    /**
     * "Following live" with the live games drawn as the scores box's rows: the first folded, all of them (four at
     * most) opened out; tapping opens the game when there is one. Off the main thread (the crests).
     */
    public static NotificationCompat.Builder following(Context c, java.util.List<Game> followed, String text) {
        NotificationCompat.Builder b = following(c, text);
        java.util.List<Game> live = new java.util.ArrayList<>();
        for (Game g : followed) if (g.state == Game.LIVE) live.add(g);
        if (live.isEmpty()) return b;
        try {
            Bitmap one = NotifCard.live(c, live, 1), all = live.size() > 1 ? NotifCard.live(c, live, 4) : one;
            if (one == null) return b;
            RemoteViews small = new RemoteViews(c.getPackageName(), R.layout.sports_notif_card);
            small.setImageViewBitmap(R.id.sports_notif_card, one);
            small.setContentDescription(R.id.sports_notif_card, text);
            RemoteViews big = new RemoteViews(c.getPackageName(), R.layout.sports_notif_card);
            big.setImageViewBitmap(R.id.sports_notif_card, all);
            big.setContentDescription(R.id.sports_notif_card, text);
            b.setStyle(new NotificationCompat.DecoratedCustomViewStyle()).setCustomContentView(small).setCustomBigContentView(big);
            if (live.size() == 1) b.setContentIntent(open(c, live.get(0)));
            // The first game's clock going on in the notification's corner, minutes and seconds, as a scoreboard's.
            long now = System.currentTimeMillis(), secs = GameClock.seconds(live.get(0), now);
            if (secs >= 0) b.setUsesChronometer(true).setShowWhen(true).setWhen(now - secs * 1000L);
            else b.setShowWhen(false);
        } catch (RuntimeException ignored) {
            // the words alone
        }
        return b;
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

    /**
     * "Watch match": the game in the Live TV player, on the playlist channel most likely to have it (a guide that shows
     * the game, or the broadcaster's channel, the better picture first); nothing when the playlist has none.
     */
    static void watchMatch(Context c, Game g, NotificationCompat.Builder b) {
        com.gathin.tvhomephone.live.Channel ch;
        try {
            ch = com.gathin.tvhomephone.live.AlsoShowing.best(c, g);
        } catch (RuntimeException e) {
            ch = null;
        }
        if (ch == null) return;
        Intent watch = com.gathin.tvhomephone.live.PlayerActivity.intent(c, ch.key);
        b.addAction(R.drawable.live_ic_play, c.getString(R.string.sports_notif_watch_match),
                PendingIntent.getActivity(c, ("watch:" + g.id).hashCode(), watch,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
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
