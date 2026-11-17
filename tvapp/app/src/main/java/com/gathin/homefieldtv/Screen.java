package com.gathin.homefieldtv;

import android.view.KeyEvent;
import android.view.View;

/**
 * One full screen of the app (Home, the TV guide, Movies, a title's page…). The activity keeps them in a stack: Back
 * closes the top one. Screens draw their own highlight and take the remote's keys themselves; nothing here uses
 * Android's own focus, so the D-pad behaves the same on every TV.
 */
abstract class Screen {
    final TvActivity tv;
    private View view;

    Screen(TvActivity tv) {
        this.tv = tv;
    }

    final View view() {
        if (view == null) view = build();
        return view;
    }

    final boolean built() {
        return view != null;
    }

    abstract View build();

    /** Its view goes over the live picture (the channel banner) rather than under it. */
    boolean overVideo() {
        return false;
    }

    /**
     * Drawn over the screen under it, which stays on screen dimmed (Settings' panel, a side menu: RightPanel): it is put
     * on top without the fade between screens, and takes itself in and out (opening, closing).
     */
    boolean overlay() {
        return false;
    }

    /** An overlay put on top: its own way in (the panel sliding in). */
    void opening() {}

    /** An overlay closed: its own way out, then done (true); false for the usual fade. */
    boolean closing(Runnable done) {
        return false;
    }

    /** On top: the first time, or again after the screen over it closed (or a film ended). */
    void shown() {}

    /** No longer on top. */
    void hidden() {}

    /** A key pressed (and held: its repeats); true when used. OK comes to ok() instead. */
    boolean key(int code, KeyEvent e) {
        return false;
    }

    void ok() {}

    /** OK held down. */
    void okLong() {
        ok();
    }

    /** Back: true when the screen used it itself, false to close it. */
    boolean back() {
        return false;
    }

    /** Each minute, on the minute (the clock, the guide's now line). */
    void tick() {}

    /** The channels or the guide changed. */
    void channelsChanged() {}

    /** The films and series changed. */
    void catalogChanged() {}

    /** Homefield TV's own update: found, gone, or under way (Updates). */
    void updatesChanged() {}
}
