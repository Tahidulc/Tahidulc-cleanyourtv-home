package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which channel a name from the TV launcher means ("play this channel" with the names it has for it: Sky's guide's, the
 * playlist's as TiviMate shows it, a game's teams), in this TV's playlists. Each name is read as Names reads it (case,
 * spacing and punctuation, HD/FHD/UHD/4K/SD, a UK tag at either end, "&" as "and", One as 1, Sports as Sport: all the
 * same; a +1 stays apart), and the channels are taken closest first:
 *
 * <ol>
 *   <li>the same name (EXACT): the first name with one wins, so the one wanted beats its stand-ins;</li>
 *   <li>the same name but for a region ("BBC One" for "BBC One London HD", either way round: REGION);</li>
 *   <li>a game's own channel ("match:Home|Away": a channel named for both teams, "Peacock 03: Elversberg vs Mainz": GAME).</li>
 * </ol>
 *
 * A closer reading of a later name beats a looser one of an earlier name, so a wrong channel never plays when a closer
 * one is there; nothing close enough is none (null). Among the channels with the same reading: the picture the name asks
 * for (its UHD when it says UHD), else the best one; British before another country's; a backup feed last; then the
 * lists' order. Pure.
 */
public final class ChannelMatch {
    private ChannelMatch() {}

    public static final int NONE = 0, GAME = 1, REGION = 2, EXACT = 3;
    /** A game by its teams, as the launcher names one ("match:Home|Away"). */
    public static final String GAME_PREFIX = "match:";

    /** What may follow a channel's name for its region (Names' keys: lower case, no spaces). */
    static final Set<String> REGIONS = new HashSet<>(Arrays.asList("london", "north", "northeast", "northwest", "south",
            "southeast", "southwest", "east", "west", "eastmidlands", "westmidlands", "midlands", "yorkshire", "eastyorkshire",
            "wales", "cymru", "scotland", "ni", "northernireland", "cambridge", "oxford", "tynetees", "tyne", "border", "granada",
            "meridian", "anglia", "central", "westcountry", "ulster", "channelislands", "lincolnshire"));

    /** The channels a name's reading finds (the lists' rows, in the lists' order). */
    public interface Channels<T> {
        /** The rows whose name's key (Names.key) is this one. */
        List<T> withKey(String key);

        /** The rows whose name reads as this one; as withKey by default (a library may find them more cheaply). */
        default List<T> sameAs(String name) {
            return withKey(key(name));
        }

        /** Every name's key the rows have, for the looser readings (none: only the same name is looked for). */
        Collection<String> keys();

        /** The row's name, for the picture it says and its country. */
        String name(T row);
    }

    /** The name's reading as Names gives it ("" for none: a game, a streaming service, nothing left). */
    static String key(String name) {
        if (name == null || name.trim().isEmpty() || isGame(name) || Names.isService(name)) return "";
        return Names.key(name);
    }

    static boolean isGame(String name) {
        return name != null && name.trim().startsWith(GAME_PREFIX);
    }

    /** A game's two teams' keys, or null when it names fewer (or the same twice, or too short to tell). */
    static String[] teams(String name) {
        if (!isGame(name)) return null;
        String[] t = name.trim().substring(GAME_PREFIX.length()).split("\\|");
        if (t.length < 2) return null;
        String a = Names.key(t[0]), b = Names.key(t[1]);
        return a.length() >= 3 && b.length() >= 3 && !a.equals(b) ? new String[]{a, b} : null;
    }

    /** How close a channel's name key is to the name wanted (NONE, GAME, REGION or EXACT). */
    public static int closeness(String wanted, String channelKey) {
        if (channelKey == null || channelKey.isEmpty()) return NONE;
        if (isGame(wanted)) return game(teams(wanted), channelKey) ? GAME : NONE;
        return reading(key(wanted), channelKey);
    }

    /** EXACT, REGION or NONE for a wanted key against a channel's. */
    private static int reading(String w, String channelKey) {
        if (w.isEmpty()) return NONE;
        if (w.equals(channelKey)) return EXACT;
        return network(w).equals(network(channelKey)) ? REGION : NONE;
    }

    /**
     * A channel's key without the region at its end, when it has one ("bbc1" for "bbc1london" and "bbc1south"); else the
     * key itself. What is left must still say something (four letters or more).
     */
    static String network(String key) {
        for (int cut = Math.max(4, key.length() - 16); cut < key.length() - 1; cut++) {
            if (REGIONS.contains(key.substring(cut))) return key.substring(0, cut);
        }
        return key;
    }

    /** Both teams' names in the channel's ("peacock03elversbergvsmainz" for Elversberg and Mainz). */
    private static boolean game(String[] teams, String channelKey) {
        return teams != null && channelKey.contains(teams[0]) && channelKey.contains(teams[1]);
    }

    /** The names to try, in order: blanks and repeats left out. */
    public static List<String> clean(List<String> names) {
        List<String> out = new ArrayList<>();
        if (names == null) return out;
        for (String n : names) {
            String t = n == null ? "" : n.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** The best row for the names by their same name only (one lookup a name: Channels.sameAs), or null. */
    public static <T> T exact(List<String> names, Channels<T> f) {
        return exact(clean(names), f, false);
    }

    private static <T> T exact(List<String> names, Channels<T> f, boolean byKey) {
        for (String n : names) {
            String k = key(n);
            if (k.isEmpty()) continue;
            T best = pick(n, byKey ? f.withKey(k) : f.sameAs(n), f);
            if (best != null) return best;
        }
        return null;
    }

    /**
     * The best row for the names, closest reading first, or null: the same name (sameAs, then every name's key, which
     * finds one whose name has none of its words, "BT Sport 1" for "TNT Sports 1"), then a region, then a game.
     */
    public static <T> T best(List<String> names, Channels<T> f) {
        List<String> all = clean(names);
        T same = exact(all, f, false);
        if (same != null) return same;
        Collection<String> have = f.keys();
        if (have == null || have.isEmpty()) return null;
        same = exact(all, f, true);
        if (same != null) return same;
        // The keys in a fixed order, so the same names always find the same channel.
        List<String> keys = new ArrayList<>(have);
        Collections.sort(keys);
        for (int tier : new int[]{REGION, GAME}) {
            for (String n : all) {
                String w = tier == REGION ? key(n) : null;
                String[] t = tier == GAME ? teams(n) : null;
                if (tier == REGION && w.isEmpty() || tier == GAME && t == null) continue;
                List<T> found = new ArrayList<>();
                for (String k : keys) {
                    boolean hit = tier == REGION ? reading(w, k) == REGION : game(t, k);
                    if (hit) for (T row : f.withKey(k)) if (!found.contains(row)) found.add(row);
                }
                T best = pick(n, found, f);
                if (best != null) return best;
            }
        }
        return null;
    }

    /**
     * Of the rows with the same reading: the picture the name asks for where it says one (UHD for "… UHD"), else the
     * best; a British one before another country's; a backup feed last; else the first.
     */
    static <T> T pick(String wanted, List<T> rows, Channels<T> f) {
        if (rows == null || rows.isEmpty()) return null;
        if (rows.size() == 1) return rows.get(0);
        Names.Info w = Names.info(isGame(wanted) ? "" : wanted, null);
        boolean saysQuality = w.quality != 1;
        T best = null;
        int bestScore = Integer.MIN_VALUE;
        for (T r : rows) {
            Names.Info i = Names.info(f.name(r), null);
            int score = (i.uk ? 100 : 0) - (i.backup ? 50 : 0) + (saysQuality ? -Math.abs(i.quality - w.quality) * 10 : i.quality * 10);
            if (score > bestScore) {
                best = r;
                bestScore = score;
            }
        }
        return best;
    }
}
