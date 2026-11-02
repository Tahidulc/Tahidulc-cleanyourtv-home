package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/**
 * Keeps accessibility on. It does nothing: no events, no keys, no screen reading. Android counts accessibility as on
 * only while at least one helper is connected, and on these TVs the only helpers are the launcher's own, which live in
 * Home's process. Whenever they all dropped at once (Home restarting, the channel helper switched off and on again to
 * wake it), accessibility went off for every app for a moment, and on Android 14 an app closing a window at that very
 * moment crashed in Android's own code (ViewRootImpl.getAccessibilityFocusedRect: Debrid Stream, TiviMate, Tuya on the
 * Streamer). In a process of its own (":keep", see the manifest), this one stays connected through all of that, so
 * accessibility never goes off; only installing the launcher itself still turns it off for a moment.
 */
public class KeepService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Nothing: it asks for no events.
    }

    @Override public void onInterrupt() {
        // Nothing to stop.
    }
}
