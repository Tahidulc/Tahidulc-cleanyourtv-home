package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.List;

/**
 * The Control Centre's Notifications switches (NotifyPanel), and the one check every pop-up goes through as it comes
 * up: the main switch for where it would show (On Home: Home, Ambient mode and pop-up mode; Over apps: any other app)
 * and the switch of its own kind (NotifyKinds); for football's kinds, the switch of the game's competition too
 * (NotifyKinds.COMPETITIONS). What sets a notification off (the scores' poll, the news, a camera) is not changed: it
 * is only not shown, and Today's alerts says why. Do not disturb (the Control Centre's tile, DoNotDisturb) keeps
 * everything back for a while but the doorbell and the TV's own questions and codes.
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

    // ---- Do not disturb (DoNotDisturb): the Control Centre's tile ------------------------------------------------

    /** Do not disturb's choice now (DoNotDisturb.OFF once its time has passed). */
    static int dndMode(Context c) {
        SharedPreferences p = prefs(c);
        return DoNotDisturb.mode(p.getInt("dnd.mode", DoNotDisturb.OFF), p.getLong("dnd.until", 0), System.currentTimeMillis());
    }

    /** Whether Do not disturb is on now. */
    static boolean dndOn(Context c) {
        return dndMode(c) != DoNotDisturb.OFF;
    }

    /** The tile's line: "Off", "On until 21:45", "On until you turn it off". */
    static String dndWords(Context c) {
        SharedPreferences p = prefs(c);
        return DoNotDisturb.words(p.getInt("dnd.mode", DoNotDisturb.OFF), p.getLong("dnd.until", 0), System.currentTimeMillis(),
                java.util.TimeZone.getDefault());
    }

    /** OK on the tile: the next choice (Off, an hour, until the morning, until turned off), from now. */
    static void dndNext(Context c) {
        int mode = DoNotDisturb.next(dndMode(c));
        long until = DoNotDisturb.until(mode, System.currentTimeMillis(), java.util.TimeZone.getDefault());
        // commit: the choice is kept at once, through a restart of the TV.
        prefs(c).edit().putInt("dnd.mode", mode).putLong("dnd.until", until).commit();
        AlertLog.note(c, "Do not disturb: " + dndWords(c));
    }

    /**
     * OK held on a pop-up (QuietHold): Do not disturb for an hour, unless it is already on for longer. {until, 1 when
     * this turned it on or made it longer, 0 when it was already on for longer}.
     */
    static long[] dndHeld(Context c) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        int saved = p.getInt("dnd.mode", DoNotDisturb.OFF);
        long until = p.getLong("dnd.until", 0);
        long[] next = DoNotDisturb.held(saved, until, now);
        boolean changed = next[0] != DoNotDisturb.mode(saved, until, now) || next[1] != until;
        if (changed) p.edit().putInt("dnd.mode", (int) next[0]).putLong("dnd.until", next[1]).commit();
        AlertLog.note(c, "Do not disturb (OK held on a pop-up): " + dndWords(c));
        return new long[]{next[1], changed ? 1 : 0};
    }

    /**
     * Whether Do not disturb keeps this kind back now: everything but the doorbell (someone at the door is never kept
     * waiting) and the TV's own questions and codes (ALWAYS).
     */
    private static boolean dndFor(Context c, String id) {
        return !NotifyKinds.ALWAYS.equals(id) && !"doorbell".equals(id) && dndOn(c);
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

    /** A competition's switch (by its slug, "eng.1"): its default (NotifyKinds.competitionOn) until switched. */
    static boolean competitionOn(Context c, String slug) {
        if (slug == null) return true;
        SharedPreferences p = prefs(c);
        String key = "comp." + slug;
        return NotifyKinds.competitionOn(slug, p.contains(key) ? p.getBoolean(key, true) : null);
    }

    static void setCompetition(Context c, String slug, boolean on) {
        prefs(c).edit().putBoolean("comp." + slug, on).apply();
    }

    /**
     * The competition's part of the check: whether a notification of kind id, for a game in the competition with this
     * slug, may show. True for a kind no competition holds (F1, the other sports, a reminder the user set) and where
     * the competition is not known (null).
     */
    static boolean allowedCompetition(Context c, String id, String slug) {
        return slug == null || !NotifyKinds.byCompetition(id) || competitionOn(c, slug);
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
        // Do not disturb: nothing but the doorbell.
        if (dndFor(c, id)) return false;
        // A film night with quiet pop-ups (FilmNight): everything waits but its own reminder (and the doorbell).
        if (quietFor(c, id)) return false;
        return plain(c, id, onHome);
    }

    /** As allowed, for a game in the competition with this slug (null: none known): its switch is passed too. */
    static boolean allowed(Context c, String id, boolean onHome, String slug) {
        return allowed(c, id, onHome) && allowedCompetition(c, id, slug);
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
        // Do not disturb: not even a starred club's goal comes (Today's alerts keeps it).
        if (dndFor(c, id)) return false;
        // Its game's competition switched off: not even a starred club's goal comes.
        if (!allowedCompetition(c, id, d.slug)) return false;
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
        held(c, id, onHome, key, what, null);
    }

    /** As held, for a game in the competition with this slug (null: none known), which may be why. */
    static void held(Context c, String id, boolean onHome, String key, String what, String slug) {
        NotifyKinds.Kind k = NotifyKinds.get(id);
        boolean quiet = quietFor(c, id);
        String why = dndFor(c, id) ? "Do not disturb is on"
                : !master(c, onHome) ? (onHome ? "On Home" : "Over apps") + " is off in Notifications"
                : !allowedCompetition(c, id, slug) ? NotifyKinds.competitionName(slug) + " is off in Notifications"
                : quiet && (k == null || kindOn(c, k)) ? FilmNight.quietWords(c)
                : (k == null ? id : k.name) + " is off in Notifications";
        AlertLog.note(c, "not shown (" + why + "): " + what);
        if (key != null) AlertToday.why(c, key, why);
    }

    /** The Control Centre tile's line: where notifications show now. */
    static String summary(Context c) {
        if (dndOn(c)) return "Do not disturb is on";
        boolean home = master(c, true), apps = master(c, false);
        return home && apps ? "On Home and over apps" : home ? "On Home only" : apps ? "Over apps only" : "All off";
    }
}
