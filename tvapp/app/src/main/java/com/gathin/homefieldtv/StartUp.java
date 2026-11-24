package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.General;

/**
 * Settings → General → Start on: the screen Homefield TV opens on. Last channel (as it comes): the channel watched last,
 * full screen with its card, when the app starts afresh (once the channels are read, a minute at most, and only while
 * nothing else was opened) and when the family comes back to it (opened again from the TV's Home, or after a while
 * away): never a channel behind the PIN lock, never a programme from catch-up or a film carried on (the live channel
 * instead). The guide, films or TV shows: when it starts afresh.
 */
final class StartUp {
    private StartUp() {}

    private static final long WAIT_MS = 60_000L;

    /** Over Home, just made: the screen Settings say. */
    static void open(TvActivity tv) {
        switch (General.start(tv)) {
            case General.START_LIVE:
                live(tv);
                break;
            case General.START_GUIDE:
                tv.openGuide(null);
                break;
            case General.START_FILMS:
                tv.openBrowse(false);
                break;
            case General.START_SHOWS:
                tv.openBrowse(true);
                break;
            default:
                break;
        }
    }

    /** How long away counts as coming back when the app wasn't opened again from the TV's Home (via Recents, the TV waking). */
    static final long AWAY_MS = 2 * 60_000L;

    /**
     * Coming back to Homefield TV (TvActivity.onResume): whether the last channel should play now. opened: the app was
     * opened again from the TV's Home (its launcher's intent); awayMs: how long it was out of sight; own: it was away in one
     * of its own screens (a film, Android's installer or file chooser, voice search), which it goes back to.
     */
    static boolean playOnReturn(int start, boolean opened, long awayMs, boolean own) {
        if (start != General.START_LIVE) return false;
        return opened || !own && awayMs >= AWAY_MS;
    }

    /** Come back: the last channel full screen with its card (none, locked or hidden: the screen it was on). True when it plays. */
    static boolean cameBack(TvActivity tv) {
        Library lib = Library.get(tv);
        Channel c = lib.ready() ? tv.channelToPlay() : null;
        if (c == null) return false;
        tv.watch(c);
        return true;
    }

    /** The last channel full screen, now or once the channels are in, while Home is still all there is. */
    private static void live(TvActivity tv) {
        Library lib = Library.get(tv);
        if (lib.ready() && tv.channelToPlay() != null) {
            tv.watch(tv.channelToPlay());
            return;
        }
        long until = System.currentTimeMillis() + WAIT_MS;
        Runnable[] once = new Runnable[1];
        once[0] = () -> {
            Channel c = lib.ready() ? tv.channelToPlay() : null;
            if (c == null && System.currentTimeMillis() < until) return;
            lib.removeListener(once[0]);
            if (c != null && !tv.isFinishing() && tv.top() instanceof HomeScreen) tv.watch(c);
        };
        lib.addListener(once[0]);
    }
}
