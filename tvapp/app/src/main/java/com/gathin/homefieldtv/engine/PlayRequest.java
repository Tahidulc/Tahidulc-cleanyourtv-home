package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * "Play this channel" from the TV launcher (or adb): Homefield TV opened straight on a channel, full screen. The channel
 * is named by its key (the one Homefield TV keeps for it, as the launcher's cards give it), or by its names: the one
 * wanted first, then its stand-ins (CHANNEL_NAMES; CHANNEL_NAME alone from an older launcher), read as ChannelMatch reads
 * them; its number last. Pure: the intent's extras read here (PlayChannel hands them in), and the channel found through
 * a Finder (Library's lookups).
 *
 * <pre>
 *   am start -a com.gathin.homefieldtv.action.PLAY_CHANNEL --es com.gathin.homefieldtv.extra.CHANNEL_KEY "p0|1234"
 *            --es com.gathin.homefieldtv.extra.CHANNEL_NAME "Kestrel One" --ei com.gathin.homefieldtv.extra.CHANNEL_NUMBER 101
 *   am start -a com.gathin.homefieldtv.action.PLAY_CHANNEL
 *            --esa com.gathin.homefieldtv.extra.CHANNEL_NAMES "Kestrel One UHD,Kestrel One"
 * </pre>
 */
public final class PlayRequest {
    public static final String ACTION = "com.gathin.homefieldtv.action.PLAY_CHANNEL";
    public static final String EXTRA_KEY = "com.gathin.homefieldtv.extra.CHANNEL_KEY";
    public static final String EXTRA_NAME = "com.gathin.homefieldtv.extra.CHANNEL_NAME";
    /** The names to try in turn (a String array), the first the one wanted. */
    public static final String EXTRA_NAMES = "com.gathin.homefieldtv.extra.CHANNEL_NAMES";
    public static final String EXTRA_NUMBER = "com.gathin.homefieldtv.extra.CHANNEL_NUMBER";
    /** When it was asked for (System.currentTimeMillis()); optional. One older than FRESH_MS is not acted on. */
    public static final String EXTRA_AT = "com.gathin.homefieldtv.extra.AT";
    /** A request kept this long at most: an intent given again much later (the app restored) opens as usual. */
    public static final long FRESH_MS = 2 * 60_000L;
    /** The most names tried. */
    public static final int MOST_NAMES = 8;

    public final String key, name;
    /** Every name to try, the one wanted (name) first; empty with none. */
    public final List<String> names;
    public final int number;

    private PlayRequest(String key, List<String> names, int number) {
        this.key = key;
        this.names = Collections.unmodifiableList(names);
        this.name = names.isEmpty() ? "" : names.get(0);
        this.number = number;
    }

    /** As below, with one name (an older launcher's, adb). */
    public static PlayRequest of(String action, String key, String name, int number, long at, long now) {
        return of(action, key, name, null, number, at, now);
    }

    /**
     * The request in an intent's action and extras, or null: another action, nothing to find the channel by, or asked
     * for more than FRESH_MS ago (at 0: not said, always fresh). The names: CHANNEL_NAMES in their order, CHANNEL_NAME first
     * when it is not among them; blanks and repeats left out, MOST_NAMES at most.
     */
    public static PlayRequest of(String action, String key, String name, String[] names, int number, long at, long now) {
        if (!ACTION.equals(action)) return null;
        if (at > 0 && Math.abs(now - at) > FRESH_MS) return null;
        String k = key == null ? "" : key.trim();
        List<String> all = new ArrayList<>();
        if (name != null) all.add(name);
        if (names != null) Collections.addAll(all, names);
        all = ChannelMatch.clean(all);
        // CHANNEL_NAME is the one wanted, CHANNEL_NAMES the order: the given name stays first.
        if (all.size() > MOST_NAMES) all = new ArrayList<>(all.subList(0, MOST_NAMES));
        int no = Math.max(0, number);
        if (k.isEmpty() && all.isEmpty() && no == 0) return null;
        return new PlayRequest(k, all, no);
    }

    /** How the channel is found: by key (at once), by name (every channel's name read: off the main thread), by number. */
    public interface Finder {
        /** The row shown for the channel kept under this key (one row per channel), or null. */
        Channel byKey(String key);

        /** The listed channels' rows whose name reads as this one, in the lists' order (the same name: ChannelMatch.EXACT). */
        List<Channel> named(String name);

        /** The first listed channel with this number, or null. */
        Channel numbered(int number);

        /** The listed rows whose name's key (Names.key) is this one; as named() by default. */
        default List<Channel> withKey(String key) {
            return named(key);
        }

        /** Every name's key the channels have, for the looser readings (ChannelMatch); none by default. */
        default Collection<String> keys() {
            return Collections.emptyList();
        }
    }

    private static ChannelMatch.Channels<Channel> channels(Finder f) {
        return new ChannelMatch.Channels<Channel>() {
            @Override public List<Channel> withKey(String key) {
                List<Channel> l = f.withKey(key);
                return l == null ? Collections.<Channel>emptyList() : l;
            }

            @Override public List<Channel> sameAs(String name) {
                List<Channel> l = f.named(name);
                return l == null ? Collections.<Channel>emptyList() : l;
            }

            @Override public Collection<String> keys() {
                return f.keys();
            }

            @Override public String name(Channel row) {
                return row.name;
            }
        };
    }

    /** The channel by its key alone (at once on the main thread), or null. */
    public Channel byKey(Finder f) {
        return key.isEmpty() ? null : f.byKey(key);
    }

    /** The channel by its key, else by one of its names read the same (a lookup each: at once once names are read), or null. */
    public Channel exact(Finder f) {
        Channel c = byKey(f);
        return c != null ? c : ChannelMatch.exact(names, channels(f));
    }

    /**
     * The channel: by its key, else by its names (closest reading first: ChannelMatch), else its number; null when none of
     * them finds it.
     */
    public Channel find(Finder f) {
        Channel c = byKey(f);
        if (c != null) return c;
        c = ChannelMatch.best(names, channels(f));
        if (c != null) return c;
        return number > 0 ? f.numbered(number) : null;
    }

    /** What to call it in a note when it can't be found ("Kestrel One", "channel 101", "the channel"). */
    public String said() {
        String n = name.startsWith(ChannelMatch.GAME_PREFIX) ? name.substring(ChannelMatch.GAME_PREFIX.length()).replace("|", " v ") : name;
        return !n.isEmpty() ? n : number > 0 ? "channel " + number : "the channel";
    }
}
