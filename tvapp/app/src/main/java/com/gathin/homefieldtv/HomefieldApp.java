package com.gathin.homefieldtv;

import android.app.Application;

/**
 * Homefield TV's process as it starts (the app, a reminder's alarm or the TV starting): Guard first, so that whatever
 * goes wrong from here is noted for Settings' If something goes wrong, and an error off the main thread never closes the
 * app.
 */
public final class HomefieldApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Guard.install(this);
    }
}
