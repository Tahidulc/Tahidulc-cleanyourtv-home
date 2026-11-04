package com.gathin.tvhomephone;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The app: one main-thread handler and one pool for network reads, shared by every screen. */
public class App extends Application {
    public static final Handler main = new Handler(Looper.getMainLooper());
    public static final ExecutorService io = Executors.newFixedThreadPool(4);
    public static App app;

    @Override public void onCreate() {
        super.onCreate();
        app = this;
        // Each part's start-up (the sync client, the alerts), set up in their own packages.
        Startup.run(this);
    }
}
