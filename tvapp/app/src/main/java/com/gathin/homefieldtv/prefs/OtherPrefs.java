package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings → Other, as the family's other app has it, for what Homefield TV does with them:
 *
 * <pre>
 *   Search      Show search history (on): the last searches as chips under the field; Show favorite channels first (on)
 *   Recording   Start recording before program start, min (0); Stop recording after program end, min (0): how much of
 *               the provider's archive either side of a programme a recording plays
 *   VOD         Autoplay next episode (on): a series goes on to its next episode at the end of one
 * </pre>
 *
 * The searches are kept on this TV only, the newest first, at most HISTORY_MAX; nothing of them leaves it.
 */
public final class OtherPrefs {
    private OtherPrefs() {}

    public static final int HISTORY_MAX = 16;
    /** The minutes a recording may start early or run on. */
    public static final int[] MARGINS = {0, 1, 2, 3, 5, 10, 15, 30};

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("other", Context.MODE_PRIVATE);
    }

    // ---- Search ---------------------------------------------------------------------------------------------------

    public static boolean showHistory(Context c) {
        return prefs(c).getBoolean("search.history", true);
    }

    public static void setShowHistory(Context c, boolean on) {
        prefs(c).edit().putBoolean("search.history", on).apply();
    }

    public static boolean favouritesFirst(Context c) {
        return prefs(c).getBoolean("search.favourites", true);
    }

    public static void setFavouritesFirst(Context c, boolean on) {
        prefs(c).edit().putBoolean("search.favourites", on).apply();
    }

    /** The searches made, the newest first. */
    public static List<String> history(Context c) {
        return parse(prefs(c).getString("search.list", ""));
    }

    /** A search made: to the front (once), the oldest let go past HISTORY_MAX. */
    public static void remember(Context c, String query) {
        prefs(c).edit().putString("search.list", join(add(history(c), query))).apply();
    }

    public static void forget(Context c) {
        prefs(c).edit().remove("search.list").apply();
    }

    /** Pure: a search added to the front of the list (the same words, any case, once), at most HISTORY_MAX. */
    public static List<String> add(List<String> list, String query) {
        List<String> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().replace('\n', ' ');
        if (!q.isEmpty()) out.add(q);
        for (String s : list) {
            if (out.size() >= HISTORY_MAX) break;
            if (!s.toLowerCase(Locale.ROOT).equals(q.toLowerCase(Locale.ROOT))) out.add(s);
        }
        return out;
    }

    static List<String> parse(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String line : s.split("\n")) if (!line.trim().isEmpty() && out.size() < HISTORY_MAX) out.add(line.trim());
        return out;
    }

    static String join(List<String> list) {
        return String.join("\n", list);
    }

    // ---- Recording ------------------------------------------------------------------------------------------------

    public static int before(Context c) {
        return margin(prefs(c).getInt("rec.before", 0));
    }

    public static void setBefore(Context c, int minutes) {
        prefs(c).edit().putInt("rec.before", margin(minutes)).apply();
    }

    public static int after(Context c) {
        return margin(prefs(c).getInt("rec.after", 0));
    }

    public static void setAfter(Context c, int minutes) {
        prefs(c).edit().putInt("rec.after", margin(minutes)).apply();
    }

    /** Pure: minutes as one of MARGINS (0 for anything else). */
    public static int margin(int minutes) {
        for (int m : MARGINS) if (m == minutes) return m;
        return 0;
    }

    // ---- VOD ------------------------------------------------------------------------------------------------------

    public static boolean autoplayNext(Context c) {
        return prefs(c).getBoolean("vod.next", true);
    }

    public static void setAutoplayNext(Context c, boolean on) {
        prefs(c).edit().putBoolean("vod.next", on).apply();
    }
}
