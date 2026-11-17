package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings → Appearance: the clock (12-hour, 24-hour or off; 24-hour as it comes, as the family's other app shows
 * "Tue, 17 Nov, 10:31"), programme times (24-hour as they come, spans with an em dash: "10:00 — 11:00"), the size of
 * the words, the live screen's channel tiles (programme pictures or channel logos), the guide's rows, progress bars and
 * channel numbers. The screens read these as they draw (statics, loaded when the app starts and changed by Settings),
 * so a change shows at once; the words' size takes the screens made again. A choice the family saved stays as it is.
 */
public final class Look {
    private Look() {}

    public static final int CLOCK_12 = 0, CLOCK_24 = 1, CLOCK_OFF = 2;
    public static final String[] CLOCK_NAMES = {"12-hour", "24-hour", "Off"};
    public static final String[] CLOCK_ABOUT = {"3.05pm", "15:05", "No clock in the corner"};
    public static final String[] TIMES_NAMES = {"24-hour", "12-hour"};
    public static final String[] TIMES_ABOUT = {"17:40 — 18:45", "5.40 — 6.45pm"};
    public static final float[] TEXT_SCALES = {0.9f, 1f, 1.1f, 1.2f};
    public static final String[] TEXT_NAMES = {"Smaller", "Normal", "Larger", "Largest"};
    public static final int ROWS_MORE = 0, ROWS_NORMAL = 1, ROWS_FEWER = 2;
    public static final String[] ROWS_NAMES = {"More, smaller", "Normal", "Fewer, bigger"};
    public static final String[] ROWS_ABOUT = {"Titles only", "Times over titles", "Times over titles, more room"};
    public static final String[] TILES_NAMES = {"Programme pictures", "Channel logos"};
    public static final String[] TILES_ABOUT = {"What's on, with the channel's logo on it", "The channel's logo, what's on under it"};

    /** As the screens draw them now. */
    public static volatile int clock = CLOCK_24;
    public static volatile boolean times24 = true, numbers = true, progress = true, logos;
    public static volatile int textSize = 1, rows = ROWS_NORMAL;
    public static volatile float text = 1f;

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("look", Context.MODE_PRIVATE);
    }

    /** Settings' choices read for the screens (the app starting). */
    public static void load(Context c) {
        SharedPreferences p = prefs(c);
        clock = PlayerPrefs.clamp(p.getInt("clock", CLOCK_24), 0, 2);
        times24 = p.getBoolean("times24", true);
        numbers = p.getBoolean("numbers", true);
        progress = p.getBoolean("progress", true);
        logos = p.getBoolean("logos", false);
        textSize = PlayerPrefs.clamp(p.getInt("text", 1), 0, TEXT_SCALES.length - 1);
        text = TEXT_SCALES[textSize];
        rows = PlayerPrefs.clamp(p.getInt("rows", ROWS_NORMAL), 0, 2);
    }

    public static void setClock(Context c, int v) {
        clock = PlayerPrefs.clamp(v, 0, 2);
        prefs(c).edit().putInt("clock", clock).apply();
    }

    public static void setTimes24(Context c, boolean on) {
        times24 = on;
        prefs(c).edit().putBoolean("times24", on).apply();
    }

    public static void setNumbers(Context c, boolean on) {
        numbers = on;
        prefs(c).edit().putBoolean("numbers", on).apply();
    }

    public static void setProgress(Context c, boolean on) {
        progress = on;
        prefs(c).edit().putBoolean("progress", on).apply();
    }

    public static void setLogos(Context c, boolean on) {
        logos = on;
        prefs(c).edit().putBoolean("logos", on).apply();
    }

    /** The words' size: kept now, shown once the screens are made again (Settings starts them again). */
    public static void setTextSize(Context c, int i) {
        textSize = PlayerPrefs.clamp(i, 0, TEXT_SCALES.length - 1);
        text = TEXT_SCALES[textSize];
        prefs(c).edit().putInt("text", textSize).apply();
    }

    public static void setRows(Context c, int v) {
        rows = PlayerPrefs.clamp(v, 0, 2);
        prefs(c).edit().putInt("rows", rows).apply();
    }

    // ---- pure ---------------------------------------------------------------------------------------------------

    /** The time of day as the corner clock and the app's other times show it: "3.05pm" or "15:05". */
    public static String clock(int hour, int minute, boolean h24) {
        if (h24) return two(hour) + ":" + two(minute);
        int h = hour % 12;
        return (h == 0 ? 12 : h) + "." + two(minute) + (hour < 12 ? "am" : "pm");
    }

    /** A programme's time as the guide shows it: "17:40", or "5.40pm" (spans drop the first am/pm, as said). */
    public static String hm(int hour, int minute, boolean h24) {
        return clock(hour, minute, h24);
    }

    /** "17:40 — 18:45", or "5.40 — 6.45pm" (the first am/pm dropped when both halves share it): an em dash, as TiviMate writes. */
    public static String span(String a, String b) {
        if (a.length() > 2 && b.length() > 2) {
            String ea = a.substring(a.length() - 2), eb = b.substring(b.length() - 2);
            if ((ea.equals("am") || ea.equals("pm")) && ea.equals(eb)) a = a.substring(0, a.length() - 2);
        }
        return a + " — " + b;
    }

    /** A guide row's height (design pixels) for its number of rows: more and smaller, as it came, fewer and bigger. */
    public static float rowHeight(int rows) {
        switch (rows) {
            case ROWS_MORE: return 64f;
            case ROWS_FEWER: return 100f;
            default: return 80f;
        }
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
