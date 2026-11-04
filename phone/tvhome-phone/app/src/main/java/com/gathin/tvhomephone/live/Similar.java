package com.gathin.tvhomephone.live;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's "Similar channels": the same family first (every Sky Sports channel for Sky Sports Main Event, the other
 * BBC channels for BBC One), then the rest of its group, in the playlist's order. The channel itself and its copies in
 * other qualities are left out, and of several copies of another channel only the best picture is offered.
 */
final class Similar {
    private Similar() {}

    /** Indexes into channels (infos lines up with it) of up to limit channels like the one at self. */
    static List<Integer> pick(List<Channel> channels, Names.Info[] infos, int self, int limit) {
        List<Integer> out = new ArrayList<>();
        if (self < 0 || self >= channels.size() || self >= infos.length || limit <= 0) return out;
        Names.Info me = infos[self];
        String group = channels.get(self).group;
        Map<String, Integer> family = new LinkedHashMap<>(), same = new LinkedHashMap<>();
        int n = Math.min(channels.size(), infos.length);
        for (int i = 0; i < n; i++) {
            if (i == self) continue;
            Names.Info in = infos[i];
            if (!me.key.isEmpty() && me.key.equals(in.key)) continue;
            String k = in.key.isEmpty() ? "#" + i : in.key;
            if (!me.family.isEmpty() && me.family.equals(in.family)) keep(family, k, i, infos);
            else if (!group.isEmpty() && group.equals(channels.get(i).group)) keep(same, k, i, infos);
        }
        for (int i : family.values()) {
            if (out.size() >= limit) return out;
            out.add(i);
        }
        for (Map.Entry<String, Integer> e : same.entrySet()) {
            if (out.size() >= limit) break;
            if (!family.containsKey(e.getKey())) out.add(e.getValue());
        }
        return out;
    }

    /** One per channel name: the better picture, then not a backup, then the first in the playlist. */
    private static void keep(Map<String, Integer> m, String key, int i, Names.Info[] infos) {
        Integer had = m.get(key);
        if (had == null) {
            m.put(key, i);
            return;
        }
        Names.Info a = infos[i], b = infos[had];
        if (a.quality > b.quality || (a.quality == b.quality && b.backup && !a.backup)) m.put(key, i);
    }
}
