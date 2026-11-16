package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.tvhomephone.sports.ChannelLogos;
import com.gathin.tvhomephone.sports.OnTv;
import com.gathin.tvhomephone.sports.SportEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AlsoShowing's sibling for the other sports: every channel in the family's playlists that shows an event that isn't a
 * football game (an NFL or NBA game, a tennis match, a UFC card, a Grand Prix, a golf tournament, a listing from the
 * British guide). The surest first: an event channel named for it ("UK EVENTS 03: Chiefs v Bills"), then those whose
 * guide has it on at the time, then the playlists' copies of the British channels the guide lists it on (or, without
 * any, the sport's own channel, Sky Sports F1 for a Grand Prix), English ones before the rest, as AlsoShowing ranks.
 */
public final class EventChannels {
    private EventChannels() {}

    /** Every channel to watch it on, only those that play. Off the main thread. */
    public static List<AlsoShowing.Listed> listed(Context c, SportEvent e) {
        if (e == null) return new ArrayList<>();
        long now = System.currentTimeMillis();
        return AlsoShowing.listed(c, rows(c, e, when(e, now)), e.start, e.live(), now);
    }

    /** When to look in the guides: now once it is on, its start (a minute in) before. */
    static long when(SportEvent e, long now) {
        return e != null && e.start > now ? e.start + 60_000L : now;
    }

    static List<AlsoShowing.Row> rows(Context c, SportEvent e, long when) {
        Library lib = Library.get(c);
        lib.ensureNow();
        List<String[][]> finds = normalized(e.finds);
        Map<String, AlsoShowing.Row> byKey = new LinkedHashMap<>();
        Set<String> fav = lib.favourites();
        List<Channel> all = lib.list(Library.ALL, "");
        if (!finds.isEmpty()) {
            // Event channels named for it: the surest.
            for (Channel ch : all) {
                if (!matches(AlsoShowing.channelWords(ch.name), finds)) continue;
                AlsoShowing.Row r = AlsoShowing.row(ch, null, fav);
                r.under = "Event channel for it";
                r.score += 5;
                byKey.put(ch.key, r);
                if (byKey.size() >= 12) break;
            }
            // Those whose guide has it on at the time.
            for (Channel ch : all) {
                Guide.Schedule s = lib.schedule(ch.key);
                int at = s == null ? -1 : s.at(when);
                if (at < 0) continue;
                String title = s.title[at], desc = s.desc[at];
                if (!matches(AlsoShowing.words(title + " " + desc), finds)) continue;
                AlsoShowing.Row r = byKey.get(ch.key);
                if (r == null) {
                    r = AlsoShowing.row(ch, null, fav);
                    r.under = title;
                    byKey.put(ch.key, r);
                }
                if (r.start == 0) {
                    r.start = s.start[at];
                    r.end = s.stop[at];
                }
                r.score += 4;
                if (byKey.size() >= 24) break;
            }
        }
        // The British channels the guide lists it on, as the playlists have them; else the sport's own.
        List<String> named = new ArrayList<>(e.channels);
        boolean usual = named.isEmpty();
        if (usual) for (String u : usual(e.sport)) named.add(u);
        for (String name : named) {
            Channel[] tier = lib.tiers(name);
            for (int t = 1; t >= 0; t--) {
                Channel ch = tier[t];
                if (ch == null) continue;
                AlsoShowing.Row r = byKey.get(ch.key);
                if (r == null) {
                    r = AlsoShowing.row(ch, null, fav);
                    r.under = usual ? "Its usual channel" : t == 1 ? "Shows it in 4K UHD" : "Listed for it";
                    byKey.put(ch.key, r);
                }
                if (r.logo == null || r.logo.isEmpty()) {
                    String logo = ChannelLogos.find(c, name);
                    r.logo = logo != null ? logo : ChannelLogos.sky(name);
                }
                r.uhd |= t == 1;
                r.score += usual ? 2 : 3;
            }
        }
        List<AlsoShowing.Row> out = new ArrayList<>(byKey.values());
        out.sort((a, b) -> Integer.compare(AlsoShowing.rank(b), AlsoShowing.rank(a)));
        for (AlsoShowing.Row r : out) {
            if (r.ch != null) {
                r.best = true;
                break;
            }
        }
        return out;
    }

    /** The sport's own British channel, for an event the guide lists on none (pure). */
    static String[] usual(String sport) {
        switch (sport == null ? "" : sport) {
            case OnTv.F1: return new String[]{"Sky Sports F1"};
            case OnTv.NFL: return new String[]{"Sky Sports NFL"};
            case OnTv.GOLF: return new String[]{"Sky Sports Golf"};
            case OnTv.TENNIS: return new String[]{"Sky Sports Tennis"};
            case OnTv.CRICKET: return new String[]{"Sky Sports Cricket"};
            default: return new String[0];
        }
    }

    /**
     * Pure: the event's names as words() makes them, each group's names that are worth anything: two letters at least
     * ("F1"), three for a lone word that isn't a number; a group with none left drops its whole set.
     */
    static List<String[][]> normalized(List<String[][]> finds) {
        List<String[][]> out = new ArrayList<>();
        if (finds == null) return out;
        for (String[][] set : finds) {
            if (set == null || set.length == 0) continue;
            String[][] n = new String[set.length][];
            boolean ok = true;
            for (int g = 0; g < set.length && ok; g++) {
                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                for (String name : set[g] == null ? new String[0] : set[g]) {
                    String w = AlsoShowing.words(name).trim();
                    if (w.length() < 2) continue;
                    if (w.length() == 2 && !w.matches(".*\\d.*")) continue;
                    names.add(w);
                }
                n[g] = names.toArray(new String[0]);
                ok = n[g].length > 0;
            }
            if (ok) out.add(n);
        }
        return out;
    }

    /** Pure: whether plain words (a clip's headline, its tags) name the event, as matches() finds it in a channel. */
    public static boolean mentions(String text, List<String[][]> finds) {
        return matches(AlsoShowing.words(text), normalized(finds));
    }

    /** Pure: whether words (as words() makes them) name the event: every group of one of its sets, each by one name. */
    static boolean matches(String words, List<String[][]> finds) {
        if (words == null || finds == null) return false;
        for (String[][] set : finds) if (AlsoShowing.names(words, set)) return true;
        return false;
    }
}
