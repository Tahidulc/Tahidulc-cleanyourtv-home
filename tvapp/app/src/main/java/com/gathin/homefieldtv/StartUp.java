package com.gathin.homefieldtv;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.prefs.General;

/**
 * Settings → General → Start on: the screen Homefield TV opens on when it starts afresh (not when it comes back from
 * the background). Live TV waits for the channels to be read (a minute at most) and only while nothing else was opened.
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
