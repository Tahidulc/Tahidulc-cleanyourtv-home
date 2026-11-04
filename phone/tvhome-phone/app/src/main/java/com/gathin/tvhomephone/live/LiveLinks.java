package com.gathin.tvhomephone.live;

import android.content.Context;

/**
 * "Live on <channel>" made tappable: the channel of that name in the family's own playlist, when one is set up and has
 * it. Sports and Home call these; they show the line as a link only when has() is true. Names are compared as Names
 * does (country and quality tags, punctuation and old names set aside); a streaming service ("Amazon Prime Video") is
 * never a channel. Of several matches: a favourite, then one tagged British, then the best picture.
 */
public final class LiveLinks {
    private LiveLinks() {}

    /** Whether the playlist has a channel for this broadcaster's name ("Sky Sports Main Event", "TNT Sports 1"). */
    public static boolean has(Context c, String channelName) {
        if (c == null || channelName == null || channelName.trim().isEmpty()) return false;
        Library l = Library.get(c);
        // Not read yet this run: read in the background (from the phone's copy), false until then.
        l.ensure();
        return l.match(channelName) != null;
    }

    /** The playlist's own logo (tvg-logo) for this broadcaster's channel; null when it has no such channel or no logo. */
    public static String logo(Context c, String channelName) {
        if (c == null || channelName == null || channelName.trim().isEmpty()) return null;
        Library l = Library.get(c);
        l.ensure();
        Channel ch = l.match(channelName);
        return ch == null || ch.logo.isEmpty() ? null : ch.logo;
    }

    /** Plays that channel in Live TV's player; false when there is none. */
    public static boolean open(Context c, String channelName) {
        if (c == null || channelName == null) return false;
        Library l = Library.get(c);
        l.ensure();
        Channel ch = l.match(channelName);
        if (ch == null) return false;
        PlayerActivity.start(c, ch.key, Library.ALL);
        return true;
    }
}
