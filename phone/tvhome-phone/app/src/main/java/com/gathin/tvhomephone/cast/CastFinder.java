package com.gathin.tvhomephone.cast;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.gathin.tvhomephone.sync.Tvs;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Everything on the Wi-Fi a channel can be cast to, looked for at once: the paired TVs (straight away), Homefield TV on
 * the TVs, Chromecasts and Google TVs, and DLNA sets. Each device reported once on the main thread; done when the slower
 * searches have had their time (Cast goes on listening until stop).
 */
public class CastFinder {
    private static final long LOOK_MS = 6_000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> seen = new HashSet<>();
    private Context app;
    private boolean stopped;

    public void start(Context c, Consumer<CastDevice> found, Runnable done) {
        app = c.getApplicationContext();
        Consumer<CastDevice> once = d -> main.post(() -> {
            if (!stopped && seen.add(d.id)) found.accept(d);
        });
        for (String name : Tvs.names(app)) once.accept(CastDevice.tvHome(name));
        AtomicInteger left = new AtomicInteger(2);
        Runnable one = () -> main.post(() -> {
            if (left.decrementAndGet() == 0 && !stopped && done != null) done.run();
        });
        HomefieldLink.find(app, Casting.pairedHosts(app), LOOK_MS, once, one);
        new Thread(() -> {
            Dlna.find(app, LOOK_MS - 1_000, once);
            one.run();
        }, "cast-dlna").start();
        if (Chromecast.possible(app)) Chromecast.scan(app, once);
    }

    public void stop() {
        stopped = true;
        if (app != null) Chromecast.stopScan(app);
    }
}
