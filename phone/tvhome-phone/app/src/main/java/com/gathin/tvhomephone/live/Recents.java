package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Recently watched channels, as Live TV's strip, the player's Recent and "Back to" show them: the app's own watch
 * history (Picks.recent, newest first, kept by Playback once a channel has played RECORD_AFTER_MS, so swiping past one
 * doesn't add it), each channel once: by its merged channel, as Library.merged makes the same channel in several
 * playlists one (its name's key, its picture's quality, backup or not), so a key from another playlist or another of
 * the family's devices and the channel it was merged into are one entry. Channels no longer in the playlists, hidden
 * ones and the one playing now are left out. Pure, for the tests.
 */
final class Recents {
    private Recents() {}

    /** How many the strip and the player's Recent show at most. */
    static final int SHOWN = 10;
    /** A channel counts as watched once it has played this long. */
    static final long RECORD_AFTER_MS = 10_000L;

    /** The merged channel a channel is: its name's key with its quality and backup tag; its own key when its name has none. */
    static String identity(Channel c) {
        if (c == null) return "";
        Names.Info i = Names.info(c.name, c.group);
        if (i.key.isEmpty()) return "k:" + c.key;
        return i.key + "|" + i.quality + (i.backup ? "|b" : "");
    }

    /**
     * The recent channels to show, newest first: keys (the history, newest first) as the playlists have them now (byKey;
     * null when gone), each merged channel once, hidden ones and the one playing (currentKey, or null) left out, at most
     * limit.
     */
    static List<Channel> list(List<String> keys, Function<String, Channel> byKey, Predicate<Channel> hidden, String currentKey,
                              int limit) {
        List<Channel> out = new ArrayList<>();
        if (keys == null || byKey == null || limit <= 0) return out;
        Set<String> seen = new HashSet<>();
        Channel playing = currentKey == null ? null : byKey.apply(currentKey);
        if (playing != null) seen.add(identity(playing));
        if (currentKey != null) seen.add("key:" + currentKey);
        for (String k : keys) {
            if (k == null || k.isEmpty() || !seen.add("key:" + k)) continue;
            Channel c = byKey.apply(k);
            if (c == null || (hidden != null && hidden.test(c))) continue;
            // The merged channel once (the channel it resolved to among its keys, whichever copy's key it was).
            if (!seen.add(identity(c))) continue;
            if (!k.equals(c.key) && !seen.add("key:" + c.key)) continue;
            out.add(c);
            if (out.size() >= limit) break;
        }
        return out;
    }

    /** The channel to go back to: the newest recent one that isn't the one playing; null when there is none. */
    static Channel last(List<String> keys, Function<String, Channel> byKey, Predicate<Channel> hidden, String currentKey) {
        List<Channel> one = list(keys, byKey, hidden, currentKey, 1);
        return one.isEmpty() ? null : one.get(0);
    }
}
