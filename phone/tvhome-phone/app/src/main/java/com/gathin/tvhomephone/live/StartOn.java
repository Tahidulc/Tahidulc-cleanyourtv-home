package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import com.gathin.tvhomephone.R;

/**
 * Where the app opens, as Homefield TV's General › Start on: Home, Live TV (the channel last watched; as it comes), Films
 * and TV shows (Watch) or Sports. Used when it opens afresh from its icon, and when it comes back after a minute away;
 * Home leaves it where it was. It was a switch before, Open on Live TV: on is Live TV, off is Home.
 */
public final class StartOn {
    private StartOn() {}

    public static final int HOME = 0, LIVE = 1, WATCH = 2, SPORTS = 3;
    static final int[] NAMES = {R.string.ps_start_home, R.string.ps_start_live, R.string.ps_start_watch, R.string.ps_start_sports};
    static final String PREFS = "start", KEY = "start_on", OLD = "open_live";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The choice: kept, else from the old switch (on as it came: Live TV). */
    public static int get(Context c) {
        SharedPreferences p = prefs(c);
        return choice(p.contains(KEY) ? p.getInt(KEY, LIVE) : null, p.contains(OLD) ? p.getBoolean(OLD, true) : null);
    }

    static void set(Context c, int choice) {
        // The old switch kept in step, for an older Homefield put back on this phone.
        prefs(c).edit().putInt(KEY, clamp(choice)).putBoolean(OLD, clamp(choice) == LIVE).apply();
    }

    /** Pure: the choice from what is kept (either may be missing: null). */
    static int choice(Integer kept, Boolean openLive) {
        if (kept != null) return clamp(kept);
        if (openLive != null) return openLive ? LIVE : HOME;
        return LIVE;
    }

    static int clamp(int c) {
        return c >= HOME && c <= SPORTS ? c : LIVE;
    }

    /** The tab to open on (its id), or 0 for Home: where it was is kept. */
    public static int tab(Context c) {
        return tabOf(get(c));
    }

    /** Pure: a choice's tab, 0 for Home. */
    static int tabOf(int choice) {
        switch (choice) {
            case LIVE: return R.id.tab_live;
            case WATCH: return R.id.tab_watch;
            case SPORTS: return R.id.tab_sports;
            default: return 0;
        }
    }
}
