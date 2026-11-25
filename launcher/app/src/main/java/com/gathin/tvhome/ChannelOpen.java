package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every channel link on the launcher opens (the TV guide, Home's cards and the dock's row, the score pills and game
 * boxes, the alerts and their "watch" buttons, the half-time hop, the planner, the phone's channel): one routing point.
 *
 * <p>Homefield TV when this TV's config.xml names it (channel_app_package) and the Homefield TV on the TV answers its
 * PLAY_CHANNEL: started straight on the channel with every name the caller had for it, the one wanted first (its
 * CHANNEL_NAMES; CHANNEL_NAME the first, for an older Homefield TV), which Homefield TV reads tolerantly and plays full
 * screen at once. No helper, no search typed, no logo fetched for a cover. Otherwise (another app chosen, no Homefield TV,
 * or one too old to answer) the helper's way into TiviMate, exactly as before (AppSearchService.tune).
 */
final class ChannelOpen {
    private ChannelOpen() { }

    static final String TIVIMATE = "ar.tvplayer.tv", HOMEFIELD = HomefieldCards.PKG;
    static final String EXTRA_NAMES = "com.gathin.homefieldtv.extra.CHANNEL_NAMES";
    /** The most names sent. */
    static final int MOST_NAMES = 8;

    /** Which way a channel goes. */
    static final int HELPER = 0, TO_HOMEFIELD = 1;

    // ---- pure ----------------------------------------------------------------------------------------------------------

    /**
     * Which way: Homefield TV when it is the channel app chosen (its package, case and spaces aside), it answers
     * PLAY_CHANNEL on this TV, and there is a name to send; else the helper's way into TiviMate.
     */
    static int way(String chosen, boolean homefieldAnswers, List<String> names) {
        boolean wanted = chosen != null && HOMEFIELD.equalsIgnoreCase(chosen.trim());
        return wanted && homefieldAnswers && !names(names).isEmpty() ? TO_HOMEFIELD : HELPER;
    }

    /** The names sent, in order: blanks and repeats out, MOST_NAMES at most; the first the one wanted. */
    static List<String> names(List<String> names) {
        List<String> out = new ArrayList<>();
        if (names == null) return out;
        for (String n : names) {
            String t = n == null ? "" : n.trim();
            if (!t.isEmpty() && !out.contains(t) && out.size() < MOST_NAMES) out.add(t);
        }
        return out;
    }

    /**
     * What PLAY_CHANNEL carries, {extra, value}: the one wanted as CHANNEL_NAME (an older Homefield TV reads only that),
     * every name as CHANNEL_NAMES (a String[]), and when it was asked (a Long). No key: the launcher doesn't know
     * Homefield TV's.
     */
    static List<Object[]> extras(List<String> names, long now) {
        List<String> all = names(names);
        List<Object[]> out = new ArrayList<>();
        if (all.isEmpty()) return out;
        out.add(new Object[]{HomefieldCards.EXTRA_NAME, all.get(0)});
        out.add(new Object[]{EXTRA_NAMES, all.toArray(new String[0])});
        out.add(new Object[]{HomefieldCards.EXTRA_AT, now});
        return out;
    }

    // ---- on the TV -------------------------------------------------------------------------------------------------------

    /** The channel app this TV's config.xml chooses ("" for TiviMate's helper way). */
    static String chosen(Context c) {
        try {
            return c.getString(R.string.channel_app_package);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Whether the Homefield TV on this TV answers PLAY_CHANNEL (installed, and new enough). */
    static boolean homefieldAnswers(Context c) {
        try {
            return !c.getPackageManager().queryIntentActivities(new Intent(HomefieldCards.ACTION).setPackage(HOMEFIELD), 0).isEmpty();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether a channel opened now goes to Homefield TV (the callers then fetch no logo for the helper's cover). */
    static boolean toHomefield(Context c) {
        return HOMEFIELD.equalsIgnoreCase(chosen(c).trim()) && homefieldAnswers(c);
    }

    /** The app a channel link opens when there is no channel to put on (a game with none known): Homefield TV or TiviMate. */
    static String app(Context c, String helperPkg) {
        return toHomefield(c) ? HOMEFIELD : helperPkg;
    }

    /** An app opened as its icon opens it (no channel to put it on); false when it has no way in. */
    static boolean openApp(Context c, String pkg) {
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        // Homefield TV may start on the channel watched last: the launcher's own live channel let go first.
        if (HOMEFIELD.equals(pkg)) LivePreviews.stopAll();
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
        GoalWatchService.onAppOpened(c, pkg);
        return true;
    }

    /** As below, with no picture for the helper's cover. */
    static boolean open(Context c, String helperPkg, List<String> names, Bitmap logo) {
        return open(c, helperPkg, names, logo, null);
    }

    /**
     * A channel opened: names the ones to try, the first the one wanted; helperPkg the app the helper's way goes into (the
     * caller's, as before: TiviMate); logo and art for the helper's cover only. False when nothing could be opened.
     */
    static boolean open(Context c, String helperPkg, List<String> names, Bitmap logo, Bitmap art) {
        // Whichever app plays it: the launcher's own live channel (a tile, the guide's corner) let go first.
        LivePreviews.stopAll();
        if (way(chosen(c), homefieldAnswers(c), names) == TO_HOMEFIELD && inHomefield(c, names)) return true;
        return AppSearchService.tune(c, helperPkg, names, logo, art);
    }

    /**
     * Homefield TV straight on the channel (its PLAY_CHANNEL with the names); false when it couldn't be asked (none on the
     * TV, too old, nothing to send). The channel noted as the one watched (InStep: its game's alerts wait for it).
     */
    static boolean inHomefield(Context c, List<String> names) {
        List<Object[]> extras = extras(names, System.currentTimeMillis());
        if (extras.isEmpty()) return false;
        // The family's account allows one connection: a channel the launcher is playing live itself (a dock tile, the
        // guide's corner) is let go first, so Homefield TV's own connection isn't refused or the preview's cut.
        LivePreviews.stopAll();
        Intent i = new Intent(HomefieldCards.ACTION).setPackage(HOMEFIELD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        for (Object[] e : extras) {
            if (e[1] instanceof String[]) i.putExtra((String) e[0], (String[]) e[1]);
            else if (e[1] instanceof Long) i.putExtra((String) e[0], (long) (Long) e[1]);
            else i.putExtra((String) e[0], String.valueOf(e[1]));
        }
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
        InStep.tuned(c, names(names));
        GoalWatchService.onAppOpened(c, HOMEFIELD);
        return true;
    }
}
