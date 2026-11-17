package com.gathin.homefieldtv.prefs;

import android.content.Context;

import com.gathin.homefieldtv.engine.Channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which channels the screens show, by Settings → Categories (hidden groups) and Parental control (locked channels
 * hidden, until the PIN is entered): Library's lists, Live TV's rows, channel up and down and search all go by it. A
 * favourite or recent channel in a hidden category stays in Favourites and Recent; one locked away doesn't.
 */
public final class Shown {
    private Shown() {}

    /** Goes up with every change to what is hidden or locked, for the lists made from it to be made again. */
    private static volatile int version;

    private static volatile List<Channel> from, shown;
    private static volatile int shownVersion = -1;
    private static volatile boolean shownOpen;

    public static int version() {
        return version;
    }

    static void changed() {
        version++;
    }

    /** Whether a channel is in the lists: not in a hidden category, and not locked away. */
    public static boolean listed(Context c, Channel ch) {
        return ch != null && !Categories.isHidden(c, ch.group) && kept(c, ch);
    }

    /** Whether a channel stays in Favourites and Recent: not locked away (Parental control: Hide them). */
    public static boolean kept(Context c, Channel ch) {
        return ch != null && !(Parental.hides(c) && Parental.blocks(c, ch));
    }

    /**
     * The channels the lists show, out of all of them: all itself when nothing is hidden, else a list made once for each
     * change (any thread).
     */
    public static List<Channel> of(Context c, List<Channel> all) {
        int v = version;
        boolean open = Parental.open();
        List<Channel> s = shown;
        if (s != null && from == all && shownVersion == v && shownOpen == open) return s;
        Set<String> hiddenGroups = Categories.hidden(c);
        boolean lockedAway = Parental.hides(c) && Parental.hasPin(c) && !open;
        if (hiddenGroups.isEmpty() && !lockedAway) {
            s = all;
        } else {
            List<Channel> out = new ArrayList<>(all.size());
            for (Channel ch : all) if (listed(c, ch)) out.add(ch);
            s = Collections.unmodifiableList(out);
        }
        shown = s;
        from = all;
        shownVersion = v;
        shownOpen = open;
        return s;
    }

    /**
     * Channel up or down from a channel through the lists: past channels that need the PIN (they play only when chosen
     * from a list); from itself when there is no other.
     */
    public static Channel step(Context c, List<Channel> list, Channel from, int dir) {
        return step(list, from, dir, ch -> Parental.blocks(c, ch));
    }

    /**
     * Pure: the channel dir places from one in a list, round at the ends, past those skip says; from (or the list's
     * first, when from isn't in it) when every other is skipped; null for an empty list.
     */
    public static Channel step(List<Channel> list, Channel from, int dir, Predicate<Channel> skip) {
        int n = list.size();
        if (n == 0) return null;
        int at = -1;
        if (from != null) {
            for (int i = 0; i < n; i++) {
                if (list.get(i).key.equals(from.key)) {
                    at = i;
                    break;
                }
            }
        }
        int d = dir < 0 ? -1 : 1;
        int i = at < 0 ? (d > 0 ? -1 : 0) : at;
        for (int k = 0; k < n; k++) {
            i = ((i + d) % n + n) % n;
            Channel ch = list.get(i);
            if (i != at && !skip.test(ch)) return ch;
        }
        return at >= 0 ? from : list.get(0);
    }

    /**
     * The channel the mini TV on Home may start by itself: the one asked for when nothing keeps it back, else the first
     * in the lists that needs no PIN; null when there is none.
     */
    public static Channel playable(Context c, Channel want, List<Channel> list) {
        if (want != null && listed(c, want) && !Parental.blocks(c, want)) return want;
        for (Channel ch : list) if (!Parental.blocks(c, ch)) return ch;
        return null;
    }
}
