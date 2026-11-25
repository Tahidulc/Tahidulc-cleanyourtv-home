package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * Debrid Stream's cards (its row on the dock, Discover's, Movies' and TV shows' posters, the trending row and its
 * Continue Watching tab): always Debrid Stream when it is on the TV, whichever app a title was watched in, and at once.
 * The helper kept switched on ahead of time (Home up, the dock near Debrid Stream, its cards in view), so OK never waits
 * for it; a title Debrid Stream itself publishes to Android TV's rows (Watch Next, its preview rows) opened straight from
 * that row's own link, no search at all.
 */
final class DebridOpen {
    private DebridOpen() { }

    static final String PKG = ReleaseAlerts.DEBRID;
    /** The helper asked to come back on at most this often while Debrid Stream's cards are in view. */
    static final long WARM_EVERY_MS = 5_000L;
    private static long warmedAt;

    // ---- pure ----------------------------------------------------------------------------------------------------------

    /** The app a Debrid Stream card opens: Debrid Stream whenever it is on the TV; else the one it was watched in (or none). */
    static String target(String debrid, String watchedIn) {
        return debrid != null ? debrid : watchedIn;
    }

    /** Whether the dock's focus at place i is on Debrid Stream or next to it (dock: its apps' packages, null for All apps). */
    static boolean near(List<String> dock, int i) {
        for (int k = i - 1; k <= i + 1; k++) {
            if (k < 0 || dock == null || k >= dock.size()) continue;
            String p = dock.get(k);
            if (p != null && p.contains("debrid")) return true;
        }
        return false;
    }

    /** Whether a row of Home (by its name) is Debrid Stream's cards: its own, or For you's, Discover's, Movies', TV shows'. */
    static boolean ofDebrid(String row) {
        return row != null && (row.contains("debrid") || row.equals("foryou") || row.equals("discover") || row.equals("movies")
                || row.equals("shows"));
    }

    /**
     * The link of the row Debrid Stream publishes for this title (rows: {title, picture, intent, line, progress}, as
     * HomeV2.published reads Android TV's Watch Next and preview rows), or null: the same title, letters and digits
     * alike, with a link of its own.
     */
    static String published(List<String[]> rows, String title) {
        if (rows == null || title == null) return null;
        String want = DebridSteps.letters(title);
        if (want.isEmpty()) return null;
        for (String[] r : rows) {
            if (r == null || r.length < 3 || r[0] == null || r[2] == null || r[2].trim().isEmpty()) continue;
            if (DebridSteps.letters(r[0]).equals(want)) return r[2];
        }
        return null;
    }

    /** Pure: whether the helper should be asked to come back on now (not running, and not asked a moment ago). */
    static boolean warmNow(boolean running, long now, long lastAsked) {
        return !running && (lastAsked == 0 || now - lastAsked >= WARM_EVERY_MS || now < lastAsked);
    }

    // ---- on the TV -------------------------------------------------------------------------------------------------------

    /** Debrid Stream's cards in view (or the dock near it): the helper switched back on now if it is off, ready for OK. */
    static void warm(Context c) {
        long now = System.currentTimeMillis();
        if (!warmNow(AppSearchService.running(), now, warmedAt)) return;
        warmedAt = now;
        AppSearchService.ensure(c);
    }

    /** A published row's own link opened in Debrid Stream; false when it can't be (no such row, or it doesn't open). */
    static boolean openPublished(Context c, String uri) {
        if (uri == null) return false;
        try {
            Intent i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            if (!PKG.equals(i.getPackage()) && (i.getComponent() == null || !PKG.equals(i.getComponent().getPackageName()))) {
                i.setPackage(PKG);
            }
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
