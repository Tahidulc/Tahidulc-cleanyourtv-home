package com.gathin.homefieldtv;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Problems;
import com.gathin.homefieldtv.engine.SkyGuide;

/**
 * What keeps Homefield TV open when something goes wrong, and notes it (Problems) for Settings' If something goes
 * wrong:
 * <ul>
 *   <li>an error on any thread but the main one is noted and that thread ends; the app carries on (Android would close
 *   the whole app for it). Out of memory there lets go of what can be read again (the pictures not on screen, the guide
 *   beyond the next hours, Sky's programme lists);</li>
 *   <li>an error on the main thread is noted, then the app closes as Android closes it (nothing else is safe then);</li>
 *   <li>the main thread is watched: when it hasn't answered for FROZEN_MS (a key the remote sent is waiting) the freeze is
 *   noted with where it was, and its length once it ends.</li>
 * </ul>
 * Not in the JVM tests (Robolectric's main thread stands still between steps).
 */
final class Guard {
    private Guard() {}

    /** The main thread is frozen once it hasn't answered for this long (Android's own "not responding" is at 5 s). */
    static final long FROZEN_MS = 4_000L;
    static final long BEAT_MS = 1_000L;

    private static volatile boolean installed;
    private static Context app;

    static void install(Context c) {
        if (installed) return;
        installed = true;
        app = c.getApplicationContext();
        Problems.init(app.getFilesDir());
        Exec.lowOnMemory = Guard::relieve;
        if (underTest()) return;
        Thread main = Looper.getMainLooper().getThread();
        Thread.UncaughtExceptionHandler was = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (t == main) {
                try {
                    Problems.crash(t, e);
                } catch (Throwable ignored) {
                    // closing anyway
                }
                if (was != null) was.uncaughtException(t, e);
                return;
            }
            // Off the main thread: noted, and only that thread ends.
            try {
                android.util.Log.w("HomefieldTv", "background thread failed: " + e.getClass().getSimpleName());
                Problems.background(t, e);
                if (Problems.isOom(e)) Exec.main(Guard::relieve);
            } catch (Throwable ignored) {
                // nothing more to do
            }
        });
        Exec.guardMain = true;
        Thread w = new Thread(Guard::watch, "homefield-watchdog");
        w.setDaemon(true);
        w.setPriority(Thread.NORM_PRIORITY + 1);
        w.start();
    }

    static boolean underTest() {
        return "robolectric".equals(Build.FINGERPRINT);
    }

    /** Out of memory somewhere: what can be read again let go, at once (main thread). */
    static void relieve() {
        try {
            Pics.trim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
            if (app != null) Library.get(app).trim(System.currentTimeMillis());
            SkyGuide.trim();
        } catch (RuntimeException ignored) {
            // as much as could be
        }
    }

    // ---- while a channel plays full screen ---------------------------------------------------------------------------

    /** How long a channel plays full screen before the pictures the screens under it hold are let go. */
    static final long WATCHING_MS = 30_000L;
    private static final Runnable LET_GO = () -> {
        // Half the pictures (those drawn longest ago: Home's, the guide's), read again from disk when wanted; the screen's
        // own come back in a moment. The title pages' details too.
        Pics.trim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        if (app != null) com.gathin.homefieldtv.engine.Catalog.get(app).forgetPages();
    };

    /** A channel full screen (on), or not any more: after WATCHING_MS of it, what playback doesn't need let go once. */
    static void watching(boolean on) {
        Exec.main.removeCallbacks(LET_GO);
        if (on && !Exec.inline) Exec.main.postDelayed(LET_GO, WATCHING_MS);
    }

    // ---- the main thread watched ---------------------------------------------------------------------------------------

    private static volatile long beat;
    private static final Runnable BEAT = () -> beat = SystemClock.uptimeMillis();

    private static void watch() {
        Thread main = Looper.getMainLooper().getThread();
        beat = SystemClock.uptimeMillis();
        long frozenAt = 0, frozenWall = 0;
        StackTraceElement[] where = null;
        while (true) {
            Exec.main.post(BEAT);
            try {
                Thread.sleep(BEAT_MS);
            } catch (InterruptedException e) {
                return;
            }
            long now = SystemClock.uptimeMillis(), late = now - beat;
            if (frozenAt == 0 && late >= FROZEN_MS) {
                // Frozen: where the main thread is now, and the note written at once (Android may close the app next).
                frozenAt = beat;
                frozenWall = System.currentTimeMillis() - late;
                where = main.getStackTrace();
                Problems.froze(frozenWall, (int) (late / 1000), where);
            } else if (frozenAt != 0 && late < 2 * BEAT_MS) {
                // Going again: the freeze's whole length.
                int secs = (int) Math.max(1, (beat - frozenAt) / 1000);
                Problems.froze(frozenWall, secs, where);
                frozenAt = 0;
                where = null;
            }
        }
    }
}
