package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;

/**
 * The picture's quality: adaptive (the chosen channel's own picture, a step down to the same channel in a lower quality
 * when the connection can't keep up, and back up once it has been steady) or fixed (always the chosen resolution, the
 * nearest under it when the channel has no such version). Data saver holds it at 720p on mobile data, and 4K on mobile
 * data is asked for first. The levels are Names' (4 UHD, 3 FHD, 2 HD, 1 not marked, 0 SD). Kept on this phone.
 */
final class Quality {
    private Quality() {}

    /** The choice: adaptive, or one of the levels. */
    static final int ADAPTIVE = -1;
    static final int[] CHOICES = {ADAPTIVE, 4, 3, 2, 0};
    private static final String CHOICE = "quality", SAVER = "data_saver", ASK_4K = "ask_4k";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE);
    }

    static int choice(Context c) {
        return prefs(c).getInt(CHOICE, ADAPTIVE);
    }

    static void setChoice(Context c, int choice) {
        prefs(c).edit().putInt(CHOICE, choice).apply();
    }

    /** Data saver: 720p at most on mobile data (on unless turned off). */
    static boolean saver(Context c) {
        return prefs(c).getBoolean(SAVER, true);
    }

    static void setSaver(Context c, boolean on) {
        prefs(c).edit().putBoolean(SAVER, on).apply();
    }

    /** 4K on mobile data only when asked for (on unless turned off). */
    static boolean ask4k(Context c) {
        return prefs(c).getBoolean(ASK_4K, true);
    }

    static void setAsk4k(Context c, boolean on) {
        prefs(c).edit().putBoolean(ASK_4K, on).apply();
    }

    /** On a connection that costs by the byte (mobile data, a hotspot). */
    static boolean metered(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        return cm != null && cm.isActiveNetworkMetered();
    }

    /** The highest level allowed now: the fixed one, or 4K; 720p on mobile data with data saver; HD when 4K is asked first. */
    static int cap(Context c, boolean fourKAllowed) {
        return cap(choice(c), metered(c), saver(c), ask4k(c) && !fourKAllowed);
    }

    /** Pure: as cap(Context), from the settings and the network. */
    static int cap(int choice, boolean metered, boolean saver, boolean ask4k) {
        int cap = choice == ADAPTIVE ? 4 : choice;
        if (metered && saver) cap = Math.min(cap, 2);
        if (metered && ask4k) cap = Math.min(cap, 3);
        return cap;
    }

    /** The tallest picture an HLS stream may pick at a level (its own variants, for a stream that has them). */
    static int maxHeight(int level) {
        switch (level) {
            case 4: return Integer.MAX_VALUE;
            case 3: return 1080;
            case 2: case 1: return 720;
            default: return 576;
        }
    }

    /**
     * Pure: which of a channel's versions to start on, given their levels best first and which one was chosen. Adaptive:
     * the chosen one, unless it is above the cap; then (and when fixed) the best at or under the cap, else the lowest.
     */
    static int start(int[] levels, int chosen, int choice, int cap) {
        if (levels.length == 0) return -1;
        if (choice == ADAPTIVE && chosen >= 0 && chosen < levels.length && levels[chosen] <= cap) return chosen;
        for (int i = 0; i < levels.length; i++) if (levels[i] <= cap) return i;
        return levels.length - 1;
    }

    /** How long a version must play steadily before adaptive tries the next one up: 4 minutes, doubled each time it failed. */
    static long upAfter(int failedUps) {
        return Math.min(32, 1L << Math.min(5, failedUps)) * 4 * 60_000L;
    }

    /** Whether the stalls (their start times, newest last) say the connection can't keep up: 3 in a minute, or one of 5 s. */
    static boolean struggling(long[] stallStarts, int n, long now, long longestStallMs) {
        if (longestStallMs >= 5000) return true;
        int recent = 0;
        for (int i = 0; i < n; i++) if (now - stallStarts[i] <= 60_000L) recent++;
        return recent >= 3;
    }

    /**
     * Pure: which of a channel's copies (its own first, then the other playlists') to start on, by how each playlist has
     * done here (lower is better, below 0 not known yet): another only when clearly better than its own (by 30%).
     */
    static int pickSource(double[] scores) {
        if (scores.length == 0 || scores[0] < 0) return 0;
        int best = 0;
        for (int i = 1; i < scores.length; i++) if (scores[i] >= 0 && scores[i] < scores[best]) best = i;
        return best != 0 && scores[best] < scores[0] * 0.7 ? best : 0;
    }

    /** A level's name, as the options show it. */
    static String label(int level) {
        switch (level) {
            case ADAPTIVE: return "Adaptive";
            case 4: return "4K";
            case 3: return "1080p";
            case 2: return "720p";
            case 1: return "HD";
            default: return "SD";
        }
    }
}
