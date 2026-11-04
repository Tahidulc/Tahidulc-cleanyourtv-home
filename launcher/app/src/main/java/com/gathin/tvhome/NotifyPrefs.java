package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.List;

/**
 * The Control Centre's Notifications switches (NotifyPanel), and the one check every pop-up goes through as it comes
 * up: the main switch for where it would show (On Home: Home, Ambient mode and pop-up mode; Over apps: any other app)
 * and the switch of its own kind (NotifyKinds). What sets a notification off (the scores' poll, the news, a camera) is
 * not changed: it is only not shown, and Today's alerts says why.
 *
 * <p>A kind the theme leaves off is not listed but offered under Add; adding it turns its theme switch on
 * (Config.themeFlag), so what sets it off starts too.
 */
final class NotifyPrefs {
    private NotifyPrefs() { }

    private static final String FILE = "notify";
    private static SharedPreferences prefs;

    private static SharedPreferences prefs(Context c) {
        SharedPreferences p = prefs;
        if (p == null) prefs = p = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        return p;
    }

    /** A main switch: On Home (home true) or Over apps. On unless switched off. */
    static boolean master(Context c, boolean home) {
        return prefs(c).getBoolean(home ? "home" : "apps", true);
    }

    static void setMaster(Context c, boolean home, boolean on) {
        prefs(c).edit().putBoolean(home ? "home" : "apps", on).apply();
    }

    /** A kind's own switch: on unless switched off. */
    static boolean on(Context c, String id) {
        return prefs(c).getBoolean("on." + id, true);
    }

    /** Whether a kind the theme leaves off was added under Add. */
    static boolean added(Context c, String id) {
        return prefs(c).getBoolean("add." + id, false);
    }

    /** A kind's switch. One with a theme switch: the alerts' service reads its switches again (NotifyPrefs.adds). */
    static void set(Context c, NotifyKinds.Kind k, boolean on) {
        prefs(c).edit().putBoolean("on." + k.id, on).apply();
        if (k.flag != null) GoalWatchService.flagsChanged();
    }

    /** Added under Add (and on), or taken back off the list. */
    static void add(Context c, NotifyKinds.Kind k, boolean add) {
        prefs(c).edit().putBoolean("add." + k.id, add).putBoolean("on." + k.id, true).apply();
        if (k.flag != null) GoalWatchService.flagsChanged();
    }

    /**
     * Whether this theme switch is on because Notifications added one of its kinds (and has it on); Config.themeFlag
     * asks. False at once for a switch no kind has.
     */
    static boolean adds(Context c, String flag) {
        List<NotifyKinds.Kind> kinds = NotifyKinds.byFlag(flag);
        if (kinds == null) return false;
        for (NotifyKinds.Kind k : kinds) if (added(c, k.id) && on(c, k.id)) return true;
        return false;
    }

    /** Whether the theme brings this kind (its switch on, or none); theme is Config.themeJson's, read once by the caller. */
    static boolean themed(NotifyKinds.Kind k, JSONObject theme) {
        return k.flag == null || (theme == null ? k.flagDefault : theme.optBoolean(k.flag, k.flagDefault));
    }

    /** Whether the kind is in the page's list: the theme brings it, or it was added. */
    static boolean listed(Context c, NotifyKinds.Kind k, JSONObject theme) {
        return themed(k, theme) || added(c, k.id);
    }

    /** Where a notification would show now: on Home (Home in front, Ambient mode or pop-up mode), else over an app. */
    static boolean homeNow() {
        return GoalWatchService.homeInFront || PopupFrame.isShowing() || AmbientScene.showing();
    }

    /**
     * The one check, as a notification of kind id is about to show (onHome: where, homeNow()). ALWAYS passes; an id not
     * in NotifyKinds has only the main switch.
     */
    static boolean allowed(Context c, String id, boolean onHome) {
        if (NotifyKinds.ALWAYS.equals(id)) return true;
        // A film night with quiet pop-ups (FilmNight): everything waits but its own reminder (and the doorbell).
        if (quietFor(c, id)) return false;
        return plain(c, id, onHome);
    }

    /**
     * Whether a film night keeps this kind quiet now: all but its own reminder, the doorbell (someone at the door is
     * never kept waiting) and the questions and codes.
     */
    private static boolean quietFor(Context c, String id) {
        return !NotifyKinds.ALWAYS.equals(id) && !NotifyKinds.FILM_NIGHT.equals(id) && !"doorbell".equals(id) && FilmNight.quietNow(c);
    }

    /** allowed's switches alone, with no film night's quiet. */
    private static boolean plain(Context c, String id, boolean onHome) {
        if (NotifyKinds.ALWAYS.equals(id)) return true;
        if (!master(c, onHome)) return false;
        NotifyKinds.Kind k = NotifyKinds.get(id);
        return k == null || kindOn(c, k);
    }

    /**
     * As allowed, for a score card: a dramatic goal (LATE DRAMA, COMEBACK) is still a goal, so it shows while either
     * Goals or Late drama is on. A card shown again from Today's alerts was asked for: always.
     */
    static boolean allowed(Context c, ScoreCard.Data d, boolean onHome) {
        if (d.again) return true;
        String id = NotifyKinds.ofCard(d.notify, d.kind, !d.podium.isEmpty());
        // A film night's quiet: a goal for one of the starred clubs still comes (with its usual switches).
        if (quietFor(c, id) && !(d.starred && ("goal".equals(id) || "drama".equals(id)))) return false;
        if ("drama".equals(id)) {
            if (!master(c, onHome)) return false;
            return kindOn(c, NotifyKinds.get("drama")) || kindOn(c, NotifyKinds.get("goal"));
        }
        return plain(c, id, onHome);
    }

    private static boolean kindOn(Context c, NotifyKinds.Kind k) {
        if (!on(c, k.id)) return false;
        if (k.flag == null || added(c, k.id) || Config.themeOnly(c, k.flag, k.flagDefault)) return true;
        // Not listed: the theme leaves it off and it was not added. When a kind sharing its theme switch was added (the
        // live table, and so the rest of rich_alerts), it comes along with that one but is not shown. Otherwise only
        // an adb test brings it, and that shows.
        return !adds(c, k.flag);
    }

    /** Logs a notification held back (AlertLog), and says why in Today's alerts when it has a key there. */
    static void held(Context c, String id, boolean onHome, String key, String what) {
        NotifyKinds.Kind k = NotifyKinds.get(id);
        boolean quiet = quietFor(c, id);
        String why = !master(c, onHome) ? (onHome ? "On Home" : "Over apps") + " is off in Notifications"
                : quiet && (k == null || kindOn(c, k)) ? FilmNight.quietWords(c)
                : (k == null ? id : k.name) + " is off in Notifications";
        AlertLog.note(c, "not shown (" + why + "): " + what);
        if (key != null) AlertToday.why(c, key, why);
    }

    /** The Control Centre tile's line: where notifications show now. */
    static String summary(Context c) {
        boolean home = master(c, true), apps = master(c, false);
        return home && apps ? "On Home and over apps" : home ? "On Home only" : apps ? "Over apps only" : "All off";
    }
}
