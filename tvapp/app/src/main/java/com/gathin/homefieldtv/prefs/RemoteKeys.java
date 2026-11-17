package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

/**
 * Settings → Remote, for live TV full screen: what Up and Down do (open the channel list, as it came, or change channel)
 * and which way channels go, what OK and OK held do, how long the number keys wait before changing channel, the key for
 * the channel before, and the four colour keys. The live screen asks action() for a key and does what it says.
 */
public final class RemoteKeys {
    private RemoteKeys() {}

    /** What a key can do on live TV full screen. */
    public static final int NOTHING = 0, GUIDE = 1, LIST = 2, PREVIOUS = 3, FAVOURITE = 4, PLAYER_MENU = 5, SUBTITLES = 6,
            START_OVER = 7, SEARCH = 8, BANNER = 9, SETTINGS = 10, CHANNEL_MENU = 11;
    public static final String[] ACTION_NAMES = {"Nothing", "The guide", "The channel list", "The channel before",
            "Favourite on or off", "The player's menu", "Subtitles", "Watch from the start", "Search", "The banner",
            "Settings", "The channel's menu"};

    /** The colour keys' choices, and what each does unless changed. */
    public static final int[] COLOUR_ACTIONS = {NOTHING, GUIDE, LIST, PREVIOUS, FAVOURITE, PLAYER_MENU, SUBTITLES, START_OVER, SEARCH, SETTINGS};
    public static final int[] COLOUR_KEYS = {KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_PROG_BLUE};
    public static final String[] COLOUR_NAMES = {"Red", "Green", "Yellow", "Blue"};
    static final int[] COLOUR_DEFAULTS = {GUIDE, LIST, PLAYER_MENU, PREVIOUS};

    /** OK and OK held, full screen: their choices (the first as it came). */
    public static final int[] OK_ACTIONS = {BANNER, LIST, GUIDE, PLAYER_MENU};
    public static final int[] OK_LONG_ACTIONS = {CHANNEL_MENU, PLAYER_MENU, GUIDE, FAVOURITE};

    public static final String[] UPDOWN_NAMES = {"Open the channel list", "Change channel"};
    public static final String[] DIRECTION_NAMES = {"Up: the channel above in the list", "Up: the next number"};
    public static final int[] WAITS_MS = {1000, 1600, 2500, 4000};
    public static final String[] WAIT_NAMES = {"1 second", "1.6 seconds", "2.5 seconds", "4 seconds"};
    public static final int PREV_KEY = 0, PREV_ZERO = 1, PREV_LEFT = 2;
    public static final String[] PREVIOUS_NAMES = {"The remote's Last or Recall key", "That, or 0 on its own", "That, or Left"};

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("remote", Context.MODE_PRIVATE);
    }

    /**
     * Up and Down on live TV full screen: 1 change channel (as TiviMate's remote has them, and Homefield TV since), 0 open
     * the live screen's rows of channels (as Homefield TV came).
     */
    public static int upDown(Context c) {
        return PlayerPrefs.clamp(prefs(c).getInt("updown", 1), 0, 1);
    }

    public static void setUpDown(Context c, int v) {
        prefs(c).edit().putInt("updown", v).apply();
    }

    /**
     * Channel up and down turned round: Up goes to the next number, the channel below in the list (as TiviMate's remote,
     * and Homefield TV since); off, Up goes to the channel above (as Homefield TV came).
     */
    public static boolean reversed(Context c) {
        return prefs(c).getBoolean("reversed", true);
    }

    public static void setReversed(Context c, boolean on) {
        prefs(c).edit().putBoolean("reversed", on).apply();
    }

    public static int ok(Context c) {
        return pick(prefs(c).getInt("ok", BANNER), OK_ACTIONS);
    }

    public static void setOk(Context c, int action) {
        prefs(c).edit().putInt("ok", action).apply();
    }

    public static int okLong(Context c) {
        return pick(prefs(c).getInt("oklong", CHANNEL_MENU), OK_LONG_ACTIONS);
    }

    public static void setOkLong(Context c, int action) {
        prefs(c).edit().putInt("oklong", action).apply();
    }

    public static int waitMs(Context c) {
        int w = prefs(c).getInt("wait", 1600);
        for (int v : WAITS_MS) if (v == w) return w;
        return 1600;
    }

    public static void setWaitMs(Context c, int ms) {
        prefs(c).edit().putInt("wait", ms).apply();
    }

    public static int previous(Context c) {
        return PlayerPrefs.clamp(prefs(c).getInt("previous", PREV_KEY), 0, 2);
    }

    public static void setPrevious(Context c, int v) {
        prefs(c).edit().putInt("previous", v).apply();
    }

    /** What colour key i (red, green, yellow, blue) does. */
    public static int colour(Context c, int i) {
        return pick(prefs(c).getInt("colour." + i, COLOUR_DEFAULTS[i]), COLOUR_ACTIONS);
    }

    public static void setColour(Context c, int i, int action) {
        prefs(c).edit().putInt("colour." + i, action).apply();
    }

    /** What a key does on live TV full screen by Settings (the colour keys, the Last key); NOTHING for any other. */
    public static int action(Context c, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_LAST_CHANNEL) return PREVIOUS;
        int i = colourIndex(keyCode);
        return i < 0 ? NOTHING : colour(c, i);
    }

    // ---- pure ---------------------------------------------------------------------------------------------------

    /** Which colour key a key is (0 red … 3 blue), -1 for any other. */
    public static int colourIndex(int keyCode) {
        for (int i = 0; i < COLOUR_KEYS.length; i++) if (COLOUR_KEYS[i] == keyCode) return i;
        return -1;
    }

    /** The way a channel key goes (-1 the channel above in the list, +1 the one below), turned round when Settings say. */
    public static int zapDir(int dir, boolean reversed) {
        return reversed ? -dir : dir;
    }

    /** A stored action if it is one of the choices, else the first (as it came). */
    static int pick(int action, int[] choices) {
        for (int a : choices) if (a == action) return a;
        return choices[0];
    }
}
