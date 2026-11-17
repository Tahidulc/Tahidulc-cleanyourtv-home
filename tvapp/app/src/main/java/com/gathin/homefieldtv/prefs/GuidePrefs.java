package com.gathin.homefieldtv.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings → Guide: guides of the family's own (XMLTV addresses) after the playlists' own, the guide's time shift for
 * one whose times are out by a fixed amount, how often the guide and the playlists are read again, and whether both are
 * read again each time Homefield TV starts afresh. The guide is kept twelve hours ahead (Guide), so it is read again at
 * least that often. An address can hold a login: it is shown by its server's name only and never logged.
 */
public final class GuidePrefs {
    private GuidePrefs() {}

    static final String PREFS = "guide", EXTRA = "extra", SHIFT = "shift_min", GUIDE_H = "guide_hours", LISTS_H = "lists_hours",
            AT_START = "at_start";
    public static final int[] GUIDE_HOURS = {1, 3, 6, 12};
    public static final String[] GUIDE_NAMES = {"Every hour", "Every 3 hours", "Every 6 hours", "Every 12 hours"};
    /** 0: only with Settings → Refresh now. */
    public static final int[] LISTS_HOURS = {6, 12, 24, 48, 168, 0};
    public static final String[] LISTS_NAMES = {"Every 6 hours", "Every 12 hours", "Every day", "Every 2 days", "Every week",
            "Only with Refresh now"};
    /** The time shifts offered, in minutes: 12 hours either way, an hour at a time, and half an hour either way. */
    public static final int[] SHIFTS;
    public static final int MAX_EXTRA = 8;

    static {
        List<Integer> s = new ArrayList<>();
        for (int h = -12; h <= 12; h++) {
            if (h == 0) {
                s.add(-30);
                s.add(0);
                s.add(30);
            } else {
                s.add(h * 60);
            }
        }
        SHIFTS = new int[s.size()];
        for (int i = 0; i < SHIFTS.length; i++) SHIFTS[i] = s.get(i);
    }

    /** Whether this start of the app has been looked at yet (Update when Homefield TV starts). */
    private static volatile boolean started;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- guides of the family's own --------------------------------------------------------------------------------

    public static List<String> extra(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(EXTRA, "[]"));
            for (int i = 0; i < a.length(); i++) {
                String u = a.optString(i).trim();
                if (usable(u) && !out.contains(u)) out.add(u);
            }
        } catch (JSONException ignored) {
            // none
        }
        return out;
    }

    /** A guide's address added after the others; false when it isn't an address or is there already (or there are 8). */
    public static boolean addExtra(Context c, String url) {
        String u = url == null ? "" : url.trim();
        List<String> l = extra(c);
        if (!usable(u) || l.contains(u) || l.size() >= MAX_EXTRA) return false;
        l.add(u);
        prefs(c).edit().putString(EXTRA, new JSONArray(l).toString()).apply();
        return true;
    }

    public static void removeExtra(Context c, int i) {
        List<String> l = extra(c);
        if (i < 0 || i >= l.size()) return;
        l.remove(i);
        prefs(c).edit().putString(EXTRA, new JSONArray(l).toString()).apply();
    }

    /** The guides read, space apart: the playlists' own first (as Library keeps them), then the family's. */
    public static String guides(Context c, String playlists) {
        return join(playlists, extra(c));
    }

    // ---- time shift -----------------------------------------------------------------------------------------------

    public static int shiftMin(Context c) {
        int m = prefs(c).getInt(SHIFT, 0);
        return contains(SHIFTS, m) ? m : 0;
    }

    public static void setShiftMin(Context c, int m) {
        prefs(c).edit().putInt(SHIFT, contains(SHIFTS, m) ? m : 0).apply();
    }

    public static long shiftMs(Context c) {
        return shiftMin(c) * 60_000L;
    }

    // ---- how often ------------------------------------------------------------------------------------------------

    public static int guideHours(Context c) {
        int h = prefs(c).getInt(GUIDE_H, 6);
        return contains(GUIDE_HOURS, h) ? h : 6;
    }

    public static void setGuideHours(Context c, int h) {
        prefs(c).edit().putInt(GUIDE_H, contains(GUIDE_HOURS, h) ? h : 6).apply();
    }

    public static int listsHours(Context c) {
        int h = prefs(c).getInt(LISTS_H, 24);
        return contains(LISTS_HOURS, h) ? h : 24;
    }

    public static void setListsHours(Context c, int h) {
        prefs(c).edit().putInt(LISTS_H, contains(LISTS_HOURS, h) ? h : 24).apply();
    }

    public static boolean atStart(Context c) {
        return prefs(c).getBoolean(AT_START, false);
    }

    public static void setAtStart(Context c, boolean on) {
        prefs(c).edit().putBoolean(AT_START, on).apply();
    }

    /** True once, the first time it is asked after the app starts, when Update when Homefield TV starts is on. */
    public static boolean firstStart(Context c) {
        if (started) return false;
        started = true;
        return atStart(c);
    }

    /** Whether the playlists are due to be read again. */
    public static boolean listsDue(Context c, long loadedAt, long now) {
        return due(listsHours(c), loadedAt, now);
    }

    /** Whether the guide is due to be read again. */
    public static boolean guideDue(Context c, long guideAt, long now) {
        return due(guideHours(c), guideAt, now);
    }

    // ---- pure -----------------------------------------------------------------------------------------------------

    /** Pure: due after every hours (0: never by itself). */
    static boolean due(int hours, long at, long now) {
        return hours > 0 && now - at > hours * 3600_000L;
    }

    /** Pure: a guide's address as typed is one to read (http or https, no spaces). */
    static boolean usable(String u) {
        if (u == null || u.isEmpty() || u.length() > 2000) return false;
        for (int i = 0; i < u.length(); i++) if (Character.isWhitespace(u.charAt(i))) return false;
        String l = u.toLowerCase(Locale.ROOT);
        return (l.startsWith("http://") || l.startsWith("https://")) && l.length() > 10;
    }

    /** Pure: the addresses space apart, each once, the first list's first. */
    static String join(String first, List<String> more) {
        List<String> out = new ArrayList<>();
        if (first != null) for (String u : first.split(" ")) if (!u.isEmpty() && !out.contains(u)) out.add(u);
        for (String u : more) if (!u.isEmpty() && !out.contains(u)) out.add(u);
        return String.join(" ", out);
    }

    /** Pure: an address shown by its server's name only (it can hold a login). */
    public static String host(String url) {
        String u = url == null ? "" : url.trim();
        int s = u.indexOf("://");
        if (s >= 0) u = u.substring(s + 3);
        String auth = u.substring(0, firstOf(u, "/?#"));
        int at = auth.lastIndexOf('@');
        if (at >= 0) auth = auth.substring(at + 1);
        int colon = auth.indexOf(':');
        if (colon > 0) auth = auth.substring(0, colon);
        return auth.isEmpty() ? "A guide" : auth;
    }

    /** Pure: the time shift in words ("None", "+1 hour", "−30 minutes", "+2 hours"). */
    public static String shiftWords(int min) {
        if (min == 0) return "None";
        String sign = min > 0 ? "+" : "−";
        int a = Math.abs(min);
        if (a % 60 != 0) return sign + a + " minutes";
        int h = a / 60;
        return sign + h + (h == 1 ? " hour" : " hours");
    }

    private static int firstOf(String s, String chars) {
        int best = s.length();
        for (int i = 0; i < chars.length(); i++) {
            int k = s.indexOf(chars.charAt(i));
            if (k >= 0 && k < best) best = k;
        }
        return best;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }
}
