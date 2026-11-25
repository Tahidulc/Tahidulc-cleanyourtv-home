package com.gathin.tvhome;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The launcher's players with a channel playing live from the playlist now (a dock tile's, TilePlayer; the guide's
 * corner, GuideV2). The family's account allows one connection at a time: before Homefield TV is asked to play a
 * channel (ChannelOpen), every one of them is let go, so its connection is closed before Homefield TV opens its own
 * (Homefield TV also asks again for a few seconds if the provider still refuses it). Held weakly: a player gone is
 * forgotten. Main thread.
 */
final class LivePreviews {
    private LivePreviews() { }

    /** A player that can let its live channel go. */
    interface Preview {
        void stopLive();
    }

    private static final Set<Preview> PLAYING = Collections.newSetFromMap(new WeakHashMap<>());

    /** p has a channel playing live now. */
    static void playing(Preview p) {
        PLAYING.add(p);
    }

    /** p's channel let go. */
    static void stopped(Preview p) {
        PLAYING.remove(p);
    }

    /** How many have a channel playing live (tests). */
    static int count() {
        return PLAYING.size();
    }

    /**
     * Every live channel the launcher plays let go, now on the main thread (posted there from another, so it is let go
     * as soon as it can be).
     */
    static void stopAll() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(LivePreviews::stopAll);
            return;
        }
        stopNow();
    }

    /** Every live channel let go, on this thread (stopAll's, from the main thread; the tests'). */
    static void stopNow() {
        List<Preview> all = new ArrayList<>(PLAYING);
        PLAYING.clear();
        for (Preview p : all) {
            try {
                p.stopLive();
            } catch (RuntimeException ignored) {
                // gone already
            }
        }
    }
}
