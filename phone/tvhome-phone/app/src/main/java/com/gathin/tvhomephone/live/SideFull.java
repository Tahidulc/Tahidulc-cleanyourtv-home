package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.view.OrientationEventListener;

/**
 * The phone turned on its side while a picture plays small (the guide's, or one carried on to another tab): full screen
 * on its side, wherever in the app, with the phone's rotation locked or not. Locked, the turn is read from the phone's
 * tilt (Turn); with rotation on, the page turns round and is made again, and that is read as the turn. Upright again
 * goes back (PlayerActivity's own). Not on a tablet, not while paused, not when the picture is full screen already.
 */
public final class SideFull {
    private SideFull() { }

    private static OrientationEventListener turner;
    private static final Turn turn = new Turn();

    /** The app's page in front (on) or not: the phone's tilt watched while it is. */
    public static void watch(Activity a, boolean on) {
        if (!on || tablet(a)) {
            if (turner != null) turner.disable();
            return;
        }
        if (turner == null) {
            Activity app = a;
            turner = new OrientationEventListener(a.getApplicationContext(), android.hardware.SensorManager.SENSOR_DELAY_UI) {
                @Override public void onOrientationChanged(int angle) {
                    if (turn.feed(angle, SystemClock.elapsedRealtime()) == Turn.SIDEWAYS) open(app);
                }
            };
        }
        if (turner.canDetectOrientation()) turner.enable();
    }

    /** The page made again: turned round on to its side from upright (rotation on) is a turn too. */
    public static void remade(Activity a, boolean wasUpright) {
        if (wasUpright && a.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // After the tabs have taken the picture back (or left it waiting for them).
            a.getWindow().getDecorView().postDelayed(() -> open(a), 300L);
        }
    }

    /** Whether the page stands upright now (kept across its being made again). */
    public static boolean upright(Activity a) {
        return a.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
    }

    private static void open(Activity a) {
        if (a.isFinishing() || a.isDestroyed() || tablet(a)) return;
        Playback pb = Playback.get(a);
        Channel ch = pb.channel();
        if (ch == null || !pb.live() || pb.paused() || pb.fullScreen()) return;
        PlayerActivity.start(a, ch.key, Library.ALL, true, true);
    }

    private static boolean tablet(Activity a) {
        return a.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }
}
