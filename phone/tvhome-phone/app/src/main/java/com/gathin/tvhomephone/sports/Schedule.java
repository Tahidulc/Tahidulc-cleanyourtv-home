package com.gathin.tvhomephone.sports;

import java.util.List;

/**
 * When the alerts must be awake (pure, for the tests): from fifteen minutes before a followed game's kick-off (its
 * line-ups) until it ends, and fifteen minutes before a reminder's or a race's time.
 */
public final class Schedule {
    private Schedule() {}

    public static final long LEAD_MS = 15 * 60_000L;
    /** How long after kick-off a game may still be going (extra time, delays). */
    public static final long GAME_MS = 150 * 60_000L;

    /** The time to wake for an event at t. */
    public static long alarmFor(long t) {
        return t - LEAD_MS;
    }

    /**
     * The next time to wake for these event times: now itself when one is within its window already (from its alarm
     * to GAME_MS after it), else the earliest alarm still to come; -1 when there is none.
     */
    public static long next(List<Long> events, long now) {
        long best = -1;
        for (Long t : events) {
            if (t == null || t <= 0) continue;
            long alarm = alarmFor(t);
            if (alarm <= now && now < t + GAME_MS) return now;
            if (alarm > now && (best < 0 || alarm < best)) best = alarm;
        }
        return best;
    }

    /** Whether the live watcher should run now: a followed game live, or one kicking off within its lead. */
    public static boolean watchNow(List<Game> followed, long now) {
        for (Game g : followed) {
            if (g.state == Game.LIVE) return true;
            if (g.state == Game.PRE && g.kickoff - LEAD_MS - 5 * 60_000L <= now && now < g.kickoff + GAME_MS) return true;
        }
        return false;
    }
}
