package com.gathin.tvhomephone.sports;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.RemoteViews;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The home-screen widget, "Your club": the followed club's game live now (or the next one) drawn as the app's scores
 * box, the next game under it, and Watch while it is live (the player on the playlist channel AlsoShowing finds best).
 * Drawn again from each poll of the games (Watcher: every 30 s while a followed game is on, every 15 minutes besides);
 * the widget's own half-hourly update draws what was kept and asks for a fresh check when that is old.
 */
public class ClubWidget extends AppWidgetProvider {
    private static final String PREFS = "club_widget";
    /** The next fixture is looked for at most this often (it is a few days of scoreboards). */
    private static final long NEXT_MS = 6 * 3600_000L;
    private static final long STALE_MS = 20 * 60_000L;
    private static final String[] DEFAULT_LEAGUES = {"eng.1", "eng.2", "uefa.champions", "uefa.europa", "uefa.europa.conf", "eng.fa", "eng.league_cup"};

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int[] ids(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, ClubWidget.class));
    }

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Context app = c.getApplicationContext();
        PendingResult done = goAsync();
        App.io.execute(() -> {
            try {
                draw(app, ids);
                if (System.currentTimeMillis() - prefs(app).getLong("at", 0) > STALE_MS) {
                    WorkManager.getInstance(app).enqueue(new OneTimeWorkRequest.Builder(AlertWorker.class).build());
                }
            } finally {
                done.finish();
            }
        });
    }

    /**
     * Which game the widget shows (pure): a live one first, else the next to kick off today, else the last one today
     * that has finished; null when none.
     */
    static Game pick(List<Game> followed, long now) {
        Game live = null, next = null, last = null;
        for (Game g : followed) {
            if (g.state == Game.LIVE && live == null) live = g;
            else if (g.state == Game.PRE && g.kickoff > now - 3 * 3600_000L && (next == null || g.kickoff < next.kickoff)) next = g;
            else if (g.state == Game.POST && (last == null || g.kickoff > last.kickoff)) last = g;
        }
        return live != null ? live : next != null ? next : last;
    }

    /** "Everton v Fulham · Sat 15:00". Pure. */
    static String fixture(Game g, ZoneId zone) {
        String when = Instant.ofEpochMilli(g.kickoff).atZone(zone).format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK));
        return g.homeName + " v " + g.awayName + " · " + when;
    }

    /** From a poll (off the main thread): what to show worked out, kept, and drawn on every widget there is. */
    static void update(Context c, Watcher.State s) {
        int[] ids;
        try {
            ids = ids(c);
        } catch (RuntimeException e) {
            return;
        }
        if (ids == null || ids.length == 0) return;
        long now = System.currentTimeMillis();
        Set<String> teams = Clubs.starred(c);
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putLong("at", now);
        Set<String> leagues = new LinkedHashSet<>(p.getStringSet("leagues", new LinkedHashSet<>()));
        for (Game g : s.followed) if (g.slug != null) leagues.add(g.slug);
        e.putStringSet("leagues", leagues);
        Game shown = pick(s.followed, now);
        Game next = next(c, teams, leagues, shown, now);
        if (shown == null) {
            shown = next;
            next = shown == null ? null : next(c, teams, leagues, shown, now);
        }
        String watch = "";
        if (shown != null && shown.state == Game.LIVE) {
            try {
                com.gathin.tvhomephone.live.Channel ch = com.gathin.tvhomephone.live.AlsoShowing.best(c, shown);
                if (ch != null) watch = ch.key;
            } catch (RuntimeException ignored) {
                // no Watch
            }
        }
        e.putString("game", shown == null ? "" : shown.json().toString())
                .putString("next", next == null ? "" : fixture(next, ZoneId.systemDefault()))
                .putString("watch", watch);
        File bug = new File(c.getCacheDir(), "widget_bug.png");
        if (shown != null) {
            try {
                Bitmap logo = NotifCard.logo(c, Competitions.logo(shown), Ui.dp(c, 40), Ui.dp(c, 40));
                Bitmap b = ScoreBug.picture(c, shown, logo, Ui.dp(c, 300));
                try (FileOutputStream out = new FileOutputStream(bug)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
            } catch (IOException | RuntimeException ignored) {
                // drawn without it
            }
        }
        e.apply();
        draw(c, ids);
    }

    /**
     * The followed clubs' next game after shown (or after now): the coming week's scoreboards of the competitions they
     * have played in (and the English ones), looked at once every few hours; null when none is found.
     */
    private static Game next(Context c, Set<String> teams, Set<String> leagues, Game shown, long now) {
        if (teams.isEmpty()) return null;
        SharedPreferences p = prefs(c);
        long after = shown != null ? shown.kickoff : now;
        try {
            String had = p.getString("next_game", "");
            if (!had.isEmpty() && now - p.getLong("next_at", 0) < NEXT_MS) {
                Game g = Game.from(new JSONObject(had));
                if (g.kickoff > after && (shown == null || !g.id.equals(shown.id))) return g;
            }
        } catch (JSONException | RuntimeException ignored) {
            // look again
        }
        Set<String> look = new LinkedHashSet<>(leagues);
        for (String l : DEFAULT_LEAGUES) look.add(l);
        Game best = null;
        LocalDate today = LocalDate.now();
        for (int d = 0; d <= 8 && best == null; d++) {
            for (String slug : look) {
                for (Game g : Espn.scoreboard(slug, today.plusDays(d), NEXT_MS)) {
                    if (!g.involves(teams) || g.state != Game.PRE || g.kickoff <= after) continue;
                    if (shown != null && g.id != null && g.id.equals(shown.id)) continue;
                    if (best == null || g.kickoff < best.kickoff) best = g;
                }
            }
        }
        p.edit().putString("next_game", best == null ? "" : best.json().toString()).putLong("next_at", now).apply();
        return best;
    }

    /** Every widget drawn from what was kept. */
    static void draw(Context c, int[] ids) {
        SharedPreferences p = prefs(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_club);
        Game g = null;
        try {
            String raw = p.getString("game", "");
            if (!raw.isEmpty()) g = Game.from(new JSONObject(raw));
        } catch (JSONException ignored) {
            // none
        }
        String next = p.getString("next", ""), watch = p.getString("watch", "");
        v.setOnClickPendingIntent(R.id.widget_root, g != null ? Notifier.open(c, g) : Notifier.home(c));
        if (g == null) {
            v.setViewVisibility(R.id.widget_bug, View.GONE);
            v.setViewVisibility(R.id.widget_live, View.GONE);
            v.setViewVisibility(R.id.widget_watch, View.GONE);
            v.setTextViewText(R.id.widget_next, c.getString(Clubs.starred(c).isEmpty() ? R.string.f2_widget_pick : R.string.f2_widget_none));
        } else {
            Bitmap bug = BitmapFactory.decodeFile(new File(c.getCacheDir(), "widget_bug.png").getPath());
            if (bug != null) {
                v.setImageViewBitmap(R.id.widget_bug, bug);
                v.setViewVisibility(R.id.widget_bug, View.VISIBLE);
            } else {
                v.setViewVisibility(R.id.widget_bug, View.GONE);
            }
            v.setContentDescription(R.id.widget_bug, g.homeName + " " + (g.state == Game.PRE ? "v" : g.score()) + " " + g.awayName);
            v.setViewVisibility(R.id.widget_live, g.state == Game.LIVE ? View.VISIBLE : View.GONE);
            v.setTextViewText(R.id.widget_next, next.isEmpty()
                    ? (g.state == Game.PRE ? fixture(g, ZoneId.systemDefault()) : "")
                    : c.getString(R.string.f2_widget_next, next));
            if (g.state == Game.LIVE && !watch.isEmpty()) {
                v.setViewVisibility(R.id.widget_watch, View.VISIBLE);
                v.setOnClickPendingIntent(R.id.widget_watch, PendingIntent.getActivity(c, 7402,
                        com.gathin.tvhomephone.live.PlayerActivity.intent(c, watch), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            } else {
                v.setViewVisibility(R.id.widget_watch, View.GONE);
            }
        }
        try {
            AppWidgetManager.getInstance(c).updateAppWidget(ids, v);
        } catch (RuntimeException ignored) {
            // the launcher went away
        }
    }
}
