package com.gathin.homefieldtv.engine;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The app's threads: a few for reading playlists, guides, catalogues and pictures, and the main thread's handler. */
public final class Exec {
    private Exec() {}

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    public static final Handler main = new Handler(Looper.getMainLooper());
    /** Previews and tests: work that would go off the main thread runs at once on the caller's. */
    public static volatile boolean inline;

    /** Off the main thread (at once, in a preview). */
    public static void io(Runnable r) {
        if (inline) r.run();
        else IO.execute(r);
    }

    /** On the main thread, next. */
    public static void main(Runnable r) {
        main.post(r);
    }
}
