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

    /** The player on that channel, for a notification's Watch; null when the playlist has no such channel. */
    public static android.content.Intent watch(Context c, String channelName) {
        if (c == null || channelName == null) return null;
        Library l = Library.get(c);
        l.ensureNow();
        Channel ch = l.match(channelName);
        return ch == null ? null : PlayerActivity.intent(c, ch.key);
    }

    /**
     * The app is being left (Home, or a swipe away) while the Live TV guide plays a channel: the full-screen player
     * takes the same stream and goes straight into picture in picture. Nothing when nothing plays, it is paused, the
     * full-screen player has it already, or the small window is turned off in the player's options.
     */
    public static void leaving(android.app.Activity a) {
        if (a == null || android.os.Build.VERSION.SDK_INT < 26
                || !a.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return;
        Playback pb = Playback.get(a);
        Channel ch = pb.channel();
        if (ch == null || !pb.live() || pb.paused() || pb.fullScreen() || !PlayerActivity.autoPip(a)) return;
        try {
            a.startActivity(PlayerActivity.intent(a, ch.key).putExtra(PlayerActivity.EXTRA_PIP, true)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (RuntimeException ignored) {
            // not now
        }
    }
}
