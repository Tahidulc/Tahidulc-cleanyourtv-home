package com.gathin.homefieldtv.prefs;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One channel's pictures, for Settings → Player → Adapt quality to the connection, as the phone's Playback has them
 * (Library.variants there). Most channels come in one quality a stream, so the channel's levels are its versions: the
 * same channel in its other qualities (FHD, HD, SD), one for each, best first, the channel picked at its own; and each
 * version's copies in the family's other playlists (Channel.alternates) behind it. The screens go on showing the
 * channel picked (its name, number, logo, guide, favourites, recents, scoreboard); only the stream under it changes,
 * and the quality badge with it. Pure (tests).
 */
public final class Ladder {
    /** Levels as Names reads them from a name: SD … UHD; PLAIN is a name that says nothing of its picture. */
    public static final int SD = 0, PLAIN = 1, HD = 2, FHD = 3, UHD = 4;

    private final List<Channel> rungs;
    private final int[] levels;
    private final int picked;
    /** The version playing, the best it may go back up to, and which copy of it: 0 its own, then its alternates. */
    private int rung, top, alt;

    private Ladder(List<Channel> rungs, int picked, int start) {
        this.rungs = rungs;
        this.picked = picked;
        levels = new int[rungs.size()];
        for (int i = 0; i < levels.length; i++) levels[i] = level(rungs.get(i));
        rung = top = start;
    }

    /** The channel alone (its versions not found yet, or it has none): its copies in the other playlists still behind it. */
    public static Ladder single(Channel c) {
        List<Channel> one = new ArrayList<>(1);
        one.add(c);
        return new Ladder(one, 0, 0);
    }

    /** The channel's versions (variants()) and where to start as Settings → Picture quality says (start()). */
    public static Ladder of(List<Channel> versions, Channel pick, int quality) {
        if (versions == null || versions.isEmpty()) return single(pick);
        int chosen = Math.max(0, versions.indexOf(pick));
        int[] lv = new int[versions.size()];
        for (int i = 0; i < lv.length; i++) lv[i] = level(versions.get(i));
        return new Ladder(new ArrayList<>(versions), chosen, start(lv, chosen, quality));
    }

    /**
     * Which version to start on (and, adapting, the best to go back up to), as Settings → Picture quality says and the
     * phone's Quality.start does: Adaptive the one picked, unless it is over the cap (Data saver's 720p); then, and for a
     * fixed quality, the best at or under the cap, else the lowest.
     */
    static int start(int[] levels, int chosen, int quality) {
        if (levels.length == 0) return -1;
        int cap = PlayerPrefs.cap(quality);
        if (PlayerPrefs.adaptive(quality) && chosen >= 0 && chosen < levels.length && levels[chosen] <= cap) return chosen;
        for (int i = 0; i < levels.length; i++) if (levels[i] <= cap) return i;
        return levels.length - 1;
    }

    /**
     * The channel's versions, best picture first: for each other quality, the channel with its name (as Names compares
     * names) and the same kind of feed (a backup feed only with backup feeds) at that quality; a favourite first, then
     * one tagged British, then the playlists' order. The channel picked stands for its own quality.
     */
    public static List<Channel> variants(List<Channel> all, Channel ch, Set<String> favourites) {
        List<Channel> out = new ArrayList<>();
        if (ch == null) return out;
        Names.Info mine = Names.info(ch.name, ch.group);
        Map<Integer, Channel> byQuality = new TreeMap<>(Collections.reverseOrder());
        Map<Integer, Names.Info> infos = new TreeMap<>();
        if (!mine.key.isEmpty() && all != null) {
            for (Channel x : all) {
                if (x == ch || x.key.equals(ch.key)) continue;
                Names.Info n = Names.info(x.name, x.group);
                if (!n.key.equals(mine.key) || n.backup != mine.backup || n.quality == mine.quality) continue;
                Channel was = byQuality.get(n.quality);
                if (was == null || better(x, n, was, infos.get(n.quality), favourites)) {
                    byQuality.put(n.quality, x);
                    infos.put(n.quality, n);
                }
            }
        }
        boolean placed = false;
        for (Map.Entry<Integer, Channel> e : byQuality.entrySet()) {
            if (!placed && e.getKey() < mine.quality) {
                out.add(ch);
                placed = true;
            }
            out.add(e.getValue());
        }
        if (!placed) out.add(ch);
        return out;
    }

    /** Of two channels sharing a name and a quality: a favourite, then one tagged British; else the one found first. */
    private static boolean better(Channel a, Names.Info ia, Channel b, Names.Info ib, Set<String> favourites) {
        boolean fa = favourites != null && favourites.contains(a.key), fb = favourites != null && favourites.contains(b.key);
        if (fa != fb) return fa;
        return ia.uk && !ib.uk;
    }

    static int level(Channel c) {
        return c == null ? PLAIN : Names.info(c.name, c.group).quality;
    }

    public int size() {
        return rungs.size();
    }

    public int rung() {
        return rung;
    }

    public int top() {
        return top;
    }

    public int alt() {
        return alt;
    }

    /** The version picked (the channel itself). */
    public int picked() {
        return picked;
    }

    /** The playing version's level. */
    public int level() {
        return levels[rung];
    }

    /** The version at the quality playing. */
    public Channel base() {
        return rungs.get(rung);
    }

    /** The stream playing: that version's own, or one of its copies from another playlist. */
    public Channel stream() {
        Channel b = base();
        return alt > 0 && alt <= b.alternates.size() ? b.alternates.get(alt - 1) : b;
    }

    public boolean canDown() {
        return rung < rungs.size() - 1;
    }

    /** A step down: the next version, its own stream. */
    public void down() {
        if (!canDown()) return;
        rung++;
        alt = 0;
    }

    /** Below the best it may go back up to. */
    public boolean canUp() {
        return rung > top;
    }

    /** A step back up: the version above, its own stream. */
    public void up() {
        if (!canUp()) return;
        rung--;
        alt = 0;
    }

    /** The same version from the next playlist; false when there is none left. */
    public boolean nextAlt() {
        if (alt >= base().alternates.size()) return false;
        alt++;
        return true;
    }

    /** Playing version r's copy a (the versions found while the channel alone played: the stream it was on kept). */
    public void at(int r, int a) {
        if (r < 0 || r >= rungs.size()) return;
        rung = r;
        alt = Math.max(0, Math.min(a, rungs.get(r).alternates.size()));
    }

    /**
     * The badge for the picture playing: from its height when the player knows it (4K, FHD, HD, SD), else from its
     * version's name; "" when neither says.
     */
    public static String badge(int height, int level) {
        if (height >= 1500) return "4K";
        if (height >= 1000) return "FHD";
        if (height >= 700) return "HD";
        if (height > 0) return "SD";
        switch (level) {
            case UHD: return "4K";
            case FHD: return "FHD";
            case HD: return "HD";
            case SD: return "SD";
            default: return "";
        }
    }
}
