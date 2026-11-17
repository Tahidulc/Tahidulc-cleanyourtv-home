package com.gathin.tvhome;

import android.app.Application;
import android.content.ComponentCallbacks2;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The launcher's process: its memory watched from the start (MemoryWatch: the self-check's log once a minute, and what is
 * let go on a memory warning), whichever part of it started first (Home, the alerts' service, a helper). Not in the
 * small ":keep" process (KeepService), which only keeps accessibility on.
 */
public final class TvHomeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (mainProcess()) MemoryWatch.start(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        MemoryWatch.trimmed(this, level);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        MemoryWatch.trimmed(this, ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
    }

    /** Whether this is the launcher's own process, not one of its others (":keep"). */
    private boolean mainProcess() {
        String name = null;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            name = Application.getProcessName();
        } else {
            try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
                byte[] b = new byte[256];
                int n = in.read(b);
                int end = 0;
                while (end < Math.max(0, n) && b[end] != 0) end++;
                name = new String(b, 0, end, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // taken as the main one
            }
        }
        return name == null || name.isEmpty() || name.equals(getPackageName());
    }
}
