package com.gathin.tvhomephone.live;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.Game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The game's alerts as the stream being watched shows it. A playlist's stream runs behind the real game (often 30 s to a
 * minute), so an alert for the game on screen waits for the stream's delay, kept for each channel: set by tapping "It's
 * just happened" as a goal, a card or the kick-off is seen (the delay is the time since its alert came), or nudged by
 * 5 s. A goal elsewhere, by a club followed, comes to the player as a banner (Switch to that game). A goal or card
 * alert for the game being watched offers "See it again" when the rewind (Timeshift) holds it. Kept on this phone.
 */
public final class LiveAlerts {
    private LiveAlerts() {}

    private static final String PREFS = "live_alerts", HOLD = "hold";
    /** An event's alert is the reference for "It's just happened" for this long. */
    private static final long CALIBRATE_WITHIN = 5 * 60_000L;
    private static final int MAX_DELAY_S = 180;

    /** The game on the full screen, and the channel it plays on ("" when nothing). Main thread writes. */
    private static volatile String watchingGame = "", watchingStream = "";
    /** When each game's latest alert came, for "It's just happened". */
    private static final Map<String, Long> lastEvent = new ConcurrentHashMap<>();
    /** The alerts waiting, as {game id, words, when they show}. */
    private static final List<Held> held = new CopyOnWriteArrayList<>();
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    /** An alert waiting for the stream. */
    public static final class Held {
        public final String gameId, title;
        public final long showAt;

        Held(String gameId, String title, long showAt) {
            this.gameId = gameId;
            this.title = title;
            this.showAt = showAt;
        }
    }

    /** The full screen: a goal elsewhere (its alert, for the banner); the alerts held changed. */
    public interface Listener {
        void onGoalElsewhere(Events.Alert a);

        default void onHeldChanged() {}

        /** An alert shown now (as its notification goes up, after any wait for the stream): the scoreboard's drop. */
        default void onShown(Events.Alert a) {}
    }

    /** An alert shown now: told to the scoreboards up (ScoreBoard), on the main thread. */
    public static void shown(Events.Alert a) {
        if (a == null) return;
        for (Listener l : listeners) main.post(() -> l.onShown(a));
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void addListener(Listener l) { listeners.add(l); }

    public static void removeListener(Listener l) { listeners.remove(l); }

    /** What the full screen shows now (null game: none). */
    static void watching(String gameId, String streamKey) {
        watchingGame = gameId == null ? "" : gameId;
        watchingStream = streamKey == null ? "" : streamKey;
    }

    static String watchingGame() { return watchingGame; }

    /** Whether alerts wait for the stream (on unless turned off). */
    static boolean holding(Context c) {
        return prefs(c).getBoolean(HOLD, true);
    }

    static void setHolding(Context c, boolean on) {
        prefs(c).edit().putBoolean(HOLD, on).apply();
    }

    /**
     * A channel's delay behind the real game, in seconds: its own (set by hand, or measured off its scoreboard), else
     * the last set or measured on any channel (one provider's channels run behind by much the same), else 0.
     */
    static int delay(Context c, String streamKey) {
        if (streamKey == null || streamKey.isEmpty()) return 0;
        android.content.SharedPreferences p = prefs(c);
        return p.getInt("d." + streamKey, p.getInt("d.last", 0));
    }

    static void setDelay(Context c, String streamKey, int seconds) {
        if (streamKey == null || streamKey.isEmpty()) return;
        int s = clamp(seconds);
        prefs(c).edit().putInt("d." + streamKey, s).putInt("d.last", s).apply();
    }

    static int clamp(int s) {
        return Math.max(0, Math.min(MAX_DELAY_S, s));
    }

    /**
     * Pure: the delay "It's just happened" sets, tapped at now for an event whose alert came at eventAt (0: none yet);
     * -1 when there is no event recent enough to go by.
     */
    static int calibrated(long eventAt, long now) {
        if (eventAt <= 0 || now < eventAt || now - eventAt > CALIBRATE_WITHIN) return -1;
        return clamp((int) Math.round((now - eventAt) / 1000.0));
    }

    /** "It's just happened" on the game being watched: the stream's delay set from its latest alert; -1 when none. */
    static int justHappened(Context c) {
        String g = watchingGame, s = watchingStream;
        if (g.isEmpty() || s.isEmpty()) return -1;
        Long at = lastEvent.get(g);
        int d = calibrated(at == null ? 0 : at, System.currentTimeMillis());
        if (d >= 0) setDelay(c, s, d);
        return d;
    }

    /** The alerts waiting now, soonest first. */
    public static List<Held> held() {
        List<Held> out = new ArrayList<>(held);
        out.sort((a, b) -> Long.compare(a.showAt, b.showAt));
        return out;
    }

    /**
     * An alert about to be shown: kept back (true; show runs when the stream gets there) when it is for the game being
     * watched and that stream has a delay; a goal for a followed club in another game goes to the full screen too.
     */
    public static boolean hold(Context c, Events.Alert a, Runnable show) {
        if (a == null || a.game == null || a.game.id == null) return false;
        long now = System.currentTimeMillis();
        lastEvent.put(a.game.id, now);
        boolean mine = a.game.id.equals(watchingGame);
        if (!mine && "goal".equals(a.kind) && !watchingGame.isEmpty()) {
            for (Listener l : listeners) main.post(() -> l.onGoalElsewhere(a));
        }
        if (!mine || !holding(c)) return false;
        int d = delay(c, watchingStream);
        if (d <= 0) return false;
        Held h = new Held(a.game.id, a.title, now + d * 1000L);
        held.add(h);
        for (Listener l : listeners) main.post(l::onHeldChanged);
        main.postDelayed(() -> {
            held.remove(h);
            for (Listener l : listeners) l.onHeldChanged();
            show.run();
        }, d * 1000L);
        return true;
    }

    /**
     * A test (PlayerTests): an alert waiting ms for the stream, as hold() keeps one back (the full screen's "1 alert
     * waiting" line), then show. Nothing else is touched: no delay learnt, no game's last moment kept.
     */
    static void testHold(Events.Alert a, long ms, Runnable show) {
        Held h = new Held(a.game == null ? "" : a.game.id, a.title, System.currentTimeMillis() + ms);
        held.add(h);
        for (Listener l : listeners) main.post(l::onHeldChanged);
        main.postDelayed(() -> {
            held.remove(h);
            for (Listener l : listeners) l.onHeldChanged();
            show.run();
        }, ms);
    }

    /** When to go back to for a moment's alert seen now: 25 s before it reached the screen. */
    static long replayFrom(long shownAt) {
        return shownAt - 25_000L;
    }

    /**
     * "See it again" on a goal, red card or VAR alert for the game being watched, when the rewind holds that moment:
     * the full screen goes back to just before it.
     */
    public static void seeAgain(Context c, Events.Alert a, NotificationCompat.Builder b) {
        if (a == null || a.game == null || a.game.id == null || !a.game.id.equals(watchingGame)) return;
        if (!"goal".equals(a.kind) && !"red".equals(a.kind) && !"var".equals(a.kind)) return;
        long from = replayFrom(System.currentTimeMillis());
        if (!Timeshift.get(c).covers(watchingStream, from)) return;
        Intent i = PlayerActivity.replayIntent(c, from);
        b.addAction(R.drawable.live_ic_replay, c.getString(R.string.live3_see_again),
                PendingIntent.getActivity(c, ("again:" + a.key).hashCode(), i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    /** A game's id, for a channel's game found by the player (null when none). */
    static String id(Game g) {
        return g == null ? null : g.id;
    }
}
