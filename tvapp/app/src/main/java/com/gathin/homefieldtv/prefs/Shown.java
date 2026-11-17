package com.gathin.homefieldtv.prefs;

import android.content.Context;

import com.gathin.homefieldtv.engine.Channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Which channels the screens show, by Settings → Categories (hidden groups) and Parental control (locked channels
 * hidden, until the PIN is entered): Library's lists, Live TV's rows, channel up and down and search all go by it. A
 * favourite or recent channel in a hidden category stays in Favourites and Recent; one locked away doesn't. A list is
 * read through a Filter, which reads what is hidden and locked once for the whole list, never once a channel.
 */
public final class Shown {
    private Shown() {}

    /** Goes up with every change to what is hidden or locked, for the lists made from it to be made again. */
    private static volatile int version;
    /** Told of each change, on the thread that made it (Library makes its lists again off the main thread). */
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private static volatile List<Channel> from, shown;
    private static volatile int shownVersion = -1;
    private static volatile boolean shownOpen;

    public static int version() {
        return version;
    }

    static void changed() {
        version++;
        for (Runnable r : listeners) r.run();
    }

    /** r told of every change to what is hidden or locked (it must be quick: it runs on the thread that changed it). */
    public static void listen(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    /**
     * What is hidden and locked as it stands now, read once: whether a channel is listed, kept or needs the PIN, for a
     * whole list of channels at a time. Its version is read first, so a change while it is read makes it stale, never
     * wrong.
     */
    public static final class Filter {
        public final int version;
        public final boolean open;
        private final Set<String> hidden, lockedGroups, lockedChannels;
        private final boolean hides, pin;

        Filter(int version, Set<String> hidden, Set<String> lockedGroups, Set<String> lockedChannels, boolean hides, boolean pin,
               boolean open) {
            this.version = version;
            this.hidden = hidden;
            this.lockedGroups = lockedGroups;
            this.lockedChannels = lockedChannels;
            this.hides = hides;
            this.pin = pin;
            this.open = open;
        }

        /** Whether a channel is in the lists (Shown.listed). */
        public boolean listed(Channel ch) {
            return ch != null && !hidden.contains(ch.group) && kept(ch);
        }

        /** Whether a channel stays in Favourites and Recent (Shown.kept). */
        public boolean kept(Channel ch) {
            return ch != null && !(hides && blocks(ch));
        }

        /** Whether a channel needs the PIN before it plays (Parental.blocks). */
        public boolean blocks(Channel ch) {
            return ch != null && pin && !open && (lockedChannels.contains(ch.key) || lockedGroups.contains(ch.group));
        }

        /** Whether nothing is hidden or locked away from the lists. */
        boolean everything() {
            return hidden.isEmpty() && !(hides && pin && !open);
        }

        /** Whether it still says what is hidden and locked now. */
        public boolean current() {
            return version == Shown.version && open == Parental.open();
        }
    }

    /** What is hidden and locked now, for a list (see Filter). */
    public static Filter filter(Context c) {
        int v = version;
        boolean open = Parental.open();
        return new Filter(v, Categories.hidden(c), Parental.lockedGroups(c), Parental.lockedChannels(c), Parental.hides(c),
                Parental.hasPin(c), open);
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
     * change (any thread; Library makes its own off the main thread).
     */
    public static List<Channel> of(Context c, List<Channel> all) {
        int v = version;
        boolean open = Parental.open();
        List<Channel> s = shown;
        if (s != null && from == all && shownVersion == v && shownOpen == open) return s;
        Filter f = filter(c);
        if (f.everything()) {
            s = all;
        } else {
            List<Channel> out = new ArrayList<>(all.size());
            for (Channel ch : all) if (f.listed(ch)) out.add(ch);
            s = Collections.unmodifiableList(out);
        }
        shown = s;
        from = all;
        shownVersion = f.version;
        shownOpen = f.open;
        return s;
    }

    /**
     * Channel up or down from a channel through the lists: past channels that need the PIN (they play only when chosen
     * from a list); from itself when there is no other. Library.step does the same for its own lists at once.
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
     * in the lists that needs no PIN; null when there is none. Library.playable does the same for its own lists at once.
     */
    public static Channel playable(Context c, Channel want, List<Channel> list) {
        if (want != null && listed(c, want) && !Parental.blocks(c, want)) return want;
        for (Channel ch : list) if (!Parental.blocks(c, ch)) return ch;
        return null;
    }
}
