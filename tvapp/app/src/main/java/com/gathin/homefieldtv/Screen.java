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

    /**
     * Whether this key, now, means one thing pressed and another held (the player's Left, Up, Right, Down and Back, as
     * TiviMate's remote has them): then a press comes to key() or back() when the key is let go, and holding it comes to
     * keyLong() instead, once.
     */
    boolean holds(int code) {
        return false;
    }

    /** A key held (holds said so): true when used; false gives it as a press after all. */
    boolean keyLong(int code) {
        return false;
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

    // ---- 07's shell (TabBar) ------------------------------------------------------------------------------------------

    /**
     * Whether the shell is drawn over this screen: Homefield's mark at the top left, the clock at the top right, and the
     * tab bar (a tab's page: tab()) or the "‹ back" line (a screen reached from another: backLine()) between them.
     * Leave the top 136 px of the page for it. Off as it comes (the screens drawn before 07 keep their own tops).
     */
    boolean shell() {
        return false;
    }

    /** The tab this screen is the page of (TabBar.HOME, GUIDE, MOVIES, SHOWS, SPORTS, MY_LIST, SEARCH), or TabBar.NONE. */
    int tab() {
        return TabBar.NONE;
    }

    /**
     * A screen reached from another (a title, a match, catch-up, a club, a driver): the words of its "‹ back" line, where
     * Back goes ("Movies", "Sports · Today", "Guide"); null for a tab's page.
     */
    String backLine() {
        return null;
    }

    /** The remote came back down from the tab bar to this page (Down, or OK on its own tab): take it where it was. */
    void fromTabs() {}
}
