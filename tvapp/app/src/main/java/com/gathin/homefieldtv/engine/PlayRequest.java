package com.gathin.homefieldtv.engine;

import java.util.List;

/**
 * "Play this channel" from the TV launcher (or adb): Homefield TV opened straight on a channel, full screen. The channel
 * is named by its key (the one Homefield TV keeps for it, as the launcher's cards give it), with its name and number as
 * fallbacks for a playlist read again since (a key gone, the channel still there by its name or number). Pure: the
 * intent's extras read here (PlayChannel hands them in), and the channel found through a Finder (Library's lookups).
 *
 * <pre>
 *   am start -a com.gathin.homefieldtv.action.PLAY_CHANNEL --es com.gathin.homefieldtv.extra.CHANNEL_KEY "p0|1234"
 *            --es com.gathin.homefieldtv.extra.CHANNEL_NAME "Kestrel One" --ei com.gathin.homefieldtv.extra.CHANNEL_NUMBER 101
 * </pre>
 */
public final class PlayRequest {
    public static final String ACTION = "com.gathin.homefieldtv.action.PLAY_CHANNEL";
    public static final String EXTRA_KEY = "com.gathin.homefieldtv.extra.CHANNEL_KEY";
    public static final String EXTRA_NAME = "com.gathin.homefieldtv.extra.CHANNEL_NAME";
    public static final String EXTRA_NUMBER = "com.gathin.homefieldtv.extra.CHANNEL_NUMBER";
    /** When it was asked for (System.currentTimeMillis()); optional. One older than FRESH_MS is not acted on. */
    public static final String EXTRA_AT = "com.gathin.homefieldtv.extra.AT";
    /** A request kept this long at most: an intent given again much later (the app restored) opens as usual. */
    public static final long FRESH_MS = 2 * 60_000L;

    public final String key, name;
    public final int number;

    private PlayRequest(String key, String name, int number) {
        this.key = key;
        this.name = name;
        this.number = number;
    }

    /**
     * The request in an intent's action and extras, or null: another action, nothing to find the channel by, or asked
     * for more than FRESH_MS ago (at 0: not said, always fresh).
     */
    public static PlayRequest of(String action, String key, String name, int number, long at, long now) {
        if (!ACTION.equals(action)) return null;
        if (at > 0 && Math.abs(now - at) > FRESH_MS) return null;
        String k = key == null ? "" : key.trim(), n = name == null ? "" : name.trim();
        int no = Math.max(0, number);
        if (k.isEmpty() && n.isEmpty() && no == 0) return null;
        return new PlayRequest(k, n, no);
    }

    /** How the channel is found: by key (at once), by name (every channel's name read: off the main thread), by number. */
    public interface Finder {
        /** The row shown for the channel kept under this key (one row per channel), or null. */
        Channel byKey(String key);

        /** The listed channels' rows whose name reads as this one, in the lists' order. */
        List<Channel> named(String name);

        /** The first listed channel with this number, or null. */
        Channel numbered(int number);
    }

    /** The channel by its key alone (at once on the main thread), or null. */
    public Channel byKey(Finder f) {
        return key.isEmpty() ? null : f.byKey(key);
    }

    /** The channel: by its key, else its name (the first listed with it), else its number; null when none of them finds it. */
    public Channel find(Finder f) {
        Channel c = byKey(f);
        if (c != null) return c;
        if (!name.isEmpty()) {
            List<Channel> l = f.named(name);
            if (l != null && !l.isEmpty()) return l.get(0);
        }
        return number > 0 ? f.numbered(number) : null;
    }

    /** What to call it in a note when it can't be found ("Kestrel One", "channel 101", "the channel"). */
    public String said() {
        return !name.isEmpty() ? name : number > 0 ? "channel " + number : "the channel";
    }
}
