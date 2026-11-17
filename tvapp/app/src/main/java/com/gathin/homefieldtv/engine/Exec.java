package com.gathin.homefieldtv.engine;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The app's threads: a few for reading playlists, guides, catalogues and pictures; one of its own for the quick work
 * worked out from them (the lists the screens show, search, Home's rows), so that never waits behind a download; and the
 * main thread's handler. Something going wrong off the main thread is noted (its kind only) rather than closing the app.
 */
public final class Exec {
    private Exec() {}

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "homefield-work");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final AtomicInteger busy = new AtomicInteger();
    public static final Handler main = new Handler(Looper.getMainLooper());
    /** Previews and tests: work that would go off the main thread runs at once on the caller's. */
    public static volatile boolean inline;

    /** Off the main thread (at once, in a preview). */
    public static void io(Runnable r) {
        if (inline) r.run();
        else IO.execute(guarded(r));
    }

    /** Quick work off the main thread, on its own thread, in the order asked (at once, in a preview). */
    public static void work(Runnable r) {
        if (inline) r.run();
        else WORK.execute(guarded(r));
    }

    /** On the main thread, next. */
    public static void main(Runnable r) {
        main.post(r);
    }

    /** Tests: how much is waiting for, or running on, the threads off the main one. */
    public static int busy() {
        return busy.get();
    }

    private static Runnable guarded(Runnable r) {
        busy.incrementAndGet();
        return () -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                // Never the message: it can hold a playlist's address. The app carries on.
                android.util.Log.w("HomefieldTv", "background work failed: " + e.getClass().getSimpleName());
            } finally {
                busy.decrementAndGet();
            }
        };
    }
}
