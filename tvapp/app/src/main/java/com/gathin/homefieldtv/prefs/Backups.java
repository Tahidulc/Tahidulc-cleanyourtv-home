package com.gathin.homefieldtv.prefs;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Names;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A channel's backup copies beyond its copies in the family's other playlists (Channel.alternates): the same channel
 * in another group of the playlists ("UK Sports" and "UK Sports Backup"), at the same picture first, then any other.
 * Tried in that order when its stream drops (Settings → Stream problems → Backup copies). Pure.
 */
public final class Backups {
    private Backups() {}

    /** At most this many more copies are tried. */
    static final int MORE = 4;

    public static List<Channel> more(List<Channel> all, Channel c) {
        List<Channel> same = new ArrayList<>(), other = new ArrayList<>();
        if (c == null || all == null) return same;
        Names.Info want = Names.info(c.name, c.group);
        if (want.key.isEmpty()) return same;
        Set<String> skip = new HashSet<>();
        skip.add(c.key);
        for (Channel a : c.alternates) skip.add(a.key);
        for (Channel x : all) {
            if (skip.contains(x.key) || x.url.equals(c.url)) continue;
            Names.Info i = Names.info(x.name, x.group);
            if (!want.key.equals(i.key)) continue;
            (i.quality == want.quality ? same : other).add(x);
        }
        same.addAll(other);
        return same.size() > MORE ? new ArrayList<>(same.subList(0, MORE)) : same;
    }
}
