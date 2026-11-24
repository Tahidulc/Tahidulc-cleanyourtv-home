package com.gathin.homefieldtv.sports;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The game as the channel being watched shows it, as the TV launcher and the phone keep it (LiveAlerts): a playlist's
 * stream runs behind the real game (often 30 s to a minute), so the hub's score, clock, events and stats, and the
 * scoreboard over the picture, are held back by that channel's delay. The delay is kept for each channel: set by "It's
 * just happened" as a goal, a card or the kick-off is seen (the delay is then the time since its news came), or nudged
 * by 5 s; a channel never set takes the last one set on any (one provider's channels run behind by much the same), and
 * with none set at all a minute, as the launcher's board over TiviMate waits.
 * Kept on this TV.
 */
public final class InStep {
    private InStep() {}

    private static final String PREFS = "in_step";
    public static final int MAX_DELAY_S = 180, STEP_S = 5;
    /** An event's news is the reference for "It's just happened" for this long. */
    static final long CALIBRATE_WITHIN = 5 * 60_000L;
    /** What came in is kept this long (the longest delay, and some), so what the picture shows can always be found. */
    static final long KEEP_MS = (MAX_DELAY_S + 60) * 1000L;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * What a channel never lined up waits, as the TV launcher's pop-ups and board over TiviMate do when nothing is lined
     * up (its InStep.DEFAULT_MS): a playlist's stream runs behind by about this much.
     */
    public static final int DEFAULT_S = 60;

    /** A channel's delay behind the real game, in seconds: its own, else the last set on any channel, else DEFAULT_S. */
    public static int delay(Context c, String channelKey) {
        if (channelKey == null || channelKey.isEmpty()) return 0;
        SharedPreferences p = prefs(c);
        return p.getInt("d." + channelKey, p.getInt("d.last", DEFAULT_S));
    }

    /** Whether a channel has a delay of its own (lined up at a kick-off, or nudged on it). */
    public static boolean own(Context c, String channelKey) {
        return channelKey != null && !channelKey.isEmpty() && prefs(c).contains("d." + channelKey);
    }

    /** Whether the scoreboard asks for OK at each kick-off to set its clock with the channel's (on unless switched off). */
    public static boolean asking(Context c) {
        return prefs(c).getBoolean("ask", true);
    }

    public static void setAsking(Context c, boolean on) {
        prefs(c).edit().putBoolean("ask", on).apply();
    }

    public static void setDelay(Context c, String channelKey, int seconds) {
        if (channelKey == null || channelKey.isEmpty()) return;
        int s = clamp(seconds);
        prefs(c).edit().putInt("d." + channelKey, s).putInt("d.last", s).apply();
    }

    /** How far behind a channel not lined up yet is taken to be (the last set on any channel), seconds. */
    public static int usual(Context c) {
        return prefs(c).getInt("d.last", DEFAULT_S);
    }

    /** Settings › More scoreboard settings: that for every channel not lined up yet. */
    public static void setUsual(Context c, int seconds) {
        prefs(c).edit().putInt("d.last", clamp(seconds)).apply();
    }

    /** The delay moved by seconds (Sooner −5, Later +5); the new delay. */
    public static int nudge(Context c, String channelKey, int seconds) {
        int s = clamp(delay(c, channelKey) + seconds);
        setDelay(c, channelKey, s);
        return s;
    }

    /** Pure: a delay kept within 0 to MAX_DELAY_S seconds. */
    static int clamp(int s) {
        return Math.max(0, Math.min(MAX_DELAY_S, s));
    }

    /**
     * Pure: the delay "It's just happened" sets, pressed at now for an event whose news came at eventAt (0: none yet);
     * -1 when there is no event recent enough to go by.
     */
    static int calibrated(long eventAt, long now) {
        if (eventAt <= 0 || now < eventAt || now - eventAt > CALIBRATE_WITHIN) return -1;
        return clamp((int) Math.round((now - eventAt) / 1000.0));
    }

    /** "It's just happened" on a channel: its delay set from the game's latest news (Line.lastChange); -1 when none. */
    public static int justHappened(Context c, String channelKey, long eventAt) {
        int d = calibrated(eventAt, System.currentTimeMillis());
        if (d >= 0) setDelay(c, channelKey, d);
        return d;
    }

    // ---- the channel being watched -------------------------------------------------------------------------------

    /** The channel on the full screen now (or last), and when it was last seen playing there (0: none). */
    private static volatile String watchingKey = "";
    private static volatile long watchingAt;
    /** The hub holds a game back to a channel watched this recently (just before the hub was opened). */
    static final long WATCHED_WITHIN = 20 * 60_000L;

    /** The live screen says which channel it plays, now and again while it plays. */
    public static void watching(String channelKey, long now) {
        watchingKey = channelKey == null ? "" : channelKey;
        watchingAt = now;
    }

    /** The channel watched now or just before (within WATCHED_WITHIN of now), "" for none. */
    public static String watched(long now) {
        String k = watchingKey;
        return k.isEmpty() || now - watchingAt > WATCHED_WITHIN ? "" : k;
    }

    /** Pure: "In step · 45 s behind live", or "Live, no delay". */
    public static String words(int delayS) {
        return delayS <= 0 ? "Live, no delay" : String.format(Locale.UK, "In step · %d s behind live", delayS);
    }

    // ---- what came in, held back --------------------------------------------------------------------------------

    /**
     * A game's states as they came in (each with when it came), so what is shown is the latest the channel's picture has
     * reached: the one that came no later than now less the delay. Thread-safe.
     */
    public static final class Line<T> {
        private final List<Long> at = new ArrayList<>();
        private final List<T> states = new ArrayList<>();
        private final List<String> keys = new ArrayList<>();
        /** When the latest state that differed from the one before it came (a goal, a card, the kick-off), 0 none. */
        private long lastChange;

        /** A state that came at time t, with a key saying what in it matters (the score, the events) for lastChange. */
        public synchronized void add(long t, T state, String key) {
            if (state == null) return;
            String k = key == null ? "" : key;
            if (!keys.isEmpty() && !keys.get(keys.size() - 1).equals(k)) lastChange = t;
            at.add(t);
            states.add(state);
            keys.add(k);
            while (at.size() > 1 && t - at.get(0) > KEEP_MS && t - at.get(1) > KEEP_MS) {
                at.remove(0);
                states.remove(0);
                keys.remove(0);
            }
        }

        /** The state the picture shows at now with delayS seconds of delay: the latest no later than now − delay; the
         * oldest kept when none is that old yet (the channel hasn't reached even that); null when none came. */
        public synchronized T at(long now, int delayS) {
            int i = index(now, delayS);
            return i < 0 ? null : states.get(i);
        }

        /** When the state shown at now came (see at()); 0 when none. */
        public synchronized long cameAt(long now, int delayS) {
            int i = index(now, delayS);
            return i < 0 ? 0 : at.get(i);
        }

        private int index(long now, int delayS) {
            if (states.isEmpty()) return -1;
            long until = now - Math.max(0, delayS) * 1000L;
            int found = 0;
            for (int i = 0; i < at.size(); i++) if (at.get(i) <= until) found = i;
            return found;
        }

        public synchronized long lastChange() {
            return lastChange;
        }

        public synchronized T latest() {
            return states.isEmpty() ? null : states.get(states.size() - 1);
        }

        public synchronized int size() {
            return states.size();
        }
    }
}
