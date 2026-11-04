package com.gathin.tvhome;

import android.content.Context;

/**
 * The screens over which Android hides every app's pop-up windows: Settings, and the permission and install screens
 * (their windows carry HIDE_NON_SYSTEM_OVERLAY_WINDOWS, so a card added over them is there but never on screen; the
 * Streamer's own window list showed the half-time card held hidden this way). A card due while one of them is open
 * waits until it is closed, rather than being "shown" where nobody can see it.
 */
final class OverlayGuard {
    private OverlayGuard() {}

    private static final String[] HIDING = {"com.android.tv.settings", "com.android.settings",
            "com.google.android.tv.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller"};

    /** How long a card waits for such a screen to close: then it shows all the same (or, a follow-up, is dropped). */
    static final long MAX_MS = 10 * 60_000L, FOLLOW_UP_MS = 2 * 60_000L;

    private static long checkedAt;
    private static String last;

    /** The app in front when it is one that hides pop-ups, else null (read at most once a second). Main thread. */
    static String hiding(Context c) {
        long now = android.os.SystemClock.uptimeMillis();
        if (checkedAt != 0 && now - checkedAt < 1_000L) return last;
        checkedAt = now;
        String front = AlertLog.front(c);
        last = null;
        for (String p : HIDING) if (p.equals(front)) last = p;
        return last;
    }

    /** "Settings" or "a permission screen", for the log. */
    static String name(String pkg) {
        return pkg != null && pkg.contains("settings") ? "Settings" : "a permission or install screen";
    }
}
