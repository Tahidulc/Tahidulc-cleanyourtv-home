package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Settings → General: the screen Homefield TV opens on (Home, Live TV on the last channel, the guide, films or TV
 * shows) and whether it starts when the TV does (BootStart). Android 10 and later let an app open itself at start-up
 * only with "Display over other apps" allowed for it, which Settings says and opens where the TV has the setting.
 */
public final class General {
    private General() {}

    public static final int START_HOME = 0, START_LIVE = 1, START_GUIDE = 2, START_FILMS = 3, START_SHOWS = 4;
    public static final String[] START_NAMES = {"Home", "Last channel", "The guide", "Films", "TV shows"};
    public static final String[] START_ABOUT = {"Home, where you left it", "Full screen, on the last channel watched, with its card",
            "", "", ""};
    /** As it comes: the last channel watched, full screen (the family asked: "make it play from the last opened channel"). */
    public static final int START_DEFAULT = START_LIVE;

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("general", Context.MODE_PRIVATE);
    }

    public static int start(Context c) {
        return PlayerPrefs.clamp(prefs(c).getInt("start", START_DEFAULT), 0, START_NAMES.length - 1);
    }

    public static void setStart(Context c, int v) {
        prefs(c).edit().putInt("start", v).apply();
    }

    public static boolean onBoot(Context c) {
        return prefs(c).getBoolean("boot", false);
    }

    public static void setOnBoot(Context c, boolean on) {
        prefs(c).edit().putBoolean("boot", on).apply();
    }

    /** Whether Android lets Homefield TV open itself at start-up (10 and later: "Display over other apps" allowed). */
    public static boolean mayOpenAtBoot(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        try {
            return android.provider.Settings.canDrawOverlays(c);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
