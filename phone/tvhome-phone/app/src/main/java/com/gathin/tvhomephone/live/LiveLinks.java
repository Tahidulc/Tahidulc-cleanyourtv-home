package com.gathin.tvhomephone.live;

import android.content.Context;

/**
 * "Live on <channel>" made tappable (built by phone-live): the channel of that name in the family's own playlist, if
 * one is set up and has it. Sports and Home call these; they show the line as a link only when has() is true.
 */
public final class LiveLinks {
    private LiveLinks() {}

    /** Whether the playlist has a channel for this broadcaster's name ("Sky Sports Main Event", "TNT Sports 1"). */
    public static boolean has(Context c, String channelName) {
        return false;
    }

    /** Plays that channel in Live TV's player; false when there is none. */
    public static boolean open(Context c, String channelName) {
        return false;
    }
}
