package com.gathin.homefieldtv.engine;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The channels the TV launcher shows as cards when its dock is on Homefield TV (ChannelCardsProvider): the channels
 * watched last (newest first), the favourites, and the sport channels, each with a few hours of its programmes (title,
 * times, a few lines about it, the picture Homefield TV found for it) and its logo and the logo's colours. Made by
 * ChannelCards off the main thread whenever those change, kept in a small file in the app's own storage, so the launcher
 * is answered at once, from memory or that file, never by reading the playlists or the guide.
 *
 * <p>What is on now and next is worked out when the launcher asks (rows), from the programmes kept here, so a card is
 * right for hours after the file was written. Sport on now: the sport channels with something on at that moment, the
 * ones whose programme says it is live first, at most SPORT_SHOWN. Each list holds a channel once (one row per channel:
 * a channel's versions are its row, as Library gives them); the lists may share a channel (the launcher shows it once).
 * Pure: no Android here, so it is tested on its own.
 */
public final class CardsSnapshot {
    public static final int VERSION = 1;
    /** The rows' sections, as the provider names them. */
    public static final String RECENT = "recent", FAVOURITE = "favourite", SPORT = "sport";
    /** The most of each list kept, and of sport shown on now (of SPORT_KEPT kept with their programmes). */
    public static final int RECENT_MAX = RecentChannels.MAX, FAVOURITES_MAX = 24, SPORT_SHOWN = 12, SPORT_KEPT = 40;
    /** Each channel's programmes kept: from the one on now, this many at most, starting within AHEAD_MS. */
    public static final int PROGRAMMES_MAX = 10;
    public static final long AHEAD_MS = 10 * 3600_000L;
    /** Descriptions are kept for a channel's first few programmes only (the file stays small). */
    public static final int DESCRIBED = 3;

    /** One channel: its key (its row's), the name and number Homefield TV shows, its logo and the logo's colours. */
    public static final class Card {
        public final String key, name, logo;
        public final int number;
        /** The logo's tile colours {top, foot} as Homefield TV worked them out, or null when not known yet. */
        public final int[] colours;
        public final List<Prog> progs = new ArrayList<>();

        Card(String key, String name, int number, String logo, int[] colours) {
            this.key = key;
            this.name = name == null ? "" : name;
            this.number = number;
            this.logo = logo == null ? "" : logo;
            this.colours = colours != null && colours.length >= 2 ? new int[]{colours[0], colours[1]} : null;
        }

        /** The programme on at t, or null. */
        public Prog at(long t) {
            for (Prog p : progs) if (p.start <= t && t < p.end) return p;
            return null;
        }

        /** The programme after the one on at t (the first starting at t or later when none is on), or null. */
        public Prog after(long t) {
            Prog on = at(t);
            long from = on != null ? on.end : t;
            for (Prog p : progs) if (p.start >= from) return p;
            return null;
        }
    }

    /** One programme: its times (ms), title, a few lines about it ("" when none) and its picture's address ("" for none). */
    public static final class Prog {
        public final long start, end;
        public final String title, desc, picture;

        Prog(long start, long end, String title, String desc, String picture) {
            this.start = start;
            this.end = end;
            this.title = title == null ? "" : title;
            this.desc = desc == null ? "" : desc;
            this.picture = picture == null ? "" : picture;
        }
    }

    /** One card as the launcher is given it: its section, its place in it, the channel, what is on now and next. */
    public static final class Row {
        public final String section;
        public final int position;
        public final Card card;
        public final Prog now, next;

        Row(String section, int position, Card card, Prog now, Prog next) {
            this.section = section;
            this.position = position;
            this.card = card;
            this.now = now;
            this.next = next;
        }
    }

    /** When it was made (ms). */
    public final long at;
    public final Map<String, Card> channels;
    public final List<String> recent, favourites, sport;

    private CardsSnapshot(long at, Map<String, Card> channels, List<String> recent, List<String> favourites, List<String> sport) {
        this.at = at;
        this.channels = Collections.unmodifiableMap(channels);
        this.recent = Collections.unmodifiableList(recent);
        this.favourites = Collections.unmodifiableList(favourites);
        this.sport = Collections.unmodifiableList(sport);
    }

    public static final CardsSnapshot EMPTY = new CardsSnapshot(0, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());

    // ---- the rows the launcher is given --------------------------------------------------------------------------------

    /**
     * The cards at time now: the recent channels (newest first), then the favourites, then the sport on now (the live ones
     * first, at most SPORT_SHOWN); each with what is on and what is next. A recent channel or a favourite shows with
     * nothing on (its guide says nothing then); a sport channel only with something on.
     */
    public List<Row> rows(long now) {
        List<Row> out = new ArrayList<>();
        add(out, RECENT, recent, now, false, RECENT_MAX);
        add(out, FAVOURITE, favourites, now, false, FAVOURITES_MAX);
        List<Row> sports = new ArrayList<>();
        add(sports, SPORT, sport, now, true, SPORT_KEPT);
        // Live first, the rest in the guide's order (a stable sort keeps it).
        List<Row> live = new ArrayList<>(), rest = new ArrayList<>();
        for (Row r : sports) (live(r.now.title) ? live : rest).add(r);
        live.addAll(rest);
        for (int i = 0; i < live.size() && i < SPORT_SHOWN; i++) {
            Row r = live.get(i);
            out.add(new Row(SPORT, i, r.card, r.now, r.next));
        }
        return out;
    }

    private void add(List<Row> out, String section, List<String> keys, long now, boolean onNow, int max) {
        Set<String> seen = new HashSet<>();
        for (String k : keys) {
            if (seen.size() >= max) break;
            Card c = channels.get(k);
            if (c == null || !seen.add(k)) continue;
            Prog on = c.at(now);
            if (onNow && on == null) {
                seen.remove(k);
                continue;
            }
            out.add(new Row(section, seen.size() - 1, c, on, c.after(now)));
        }
    }

    /**
     * Pure: whether a programme's title says it is live ("Live: …", "LIVE Premier League", "… (Live)", "… - Live"), as the
     * guides put it; "Live and Kicking" (a show's own name, no mark after it) and "Deliverance" are not.
     */
    public static boolean live(String title) {
        if (title == null) return false;
        String t = title.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("live:") || t.startsWith("live -") || t.startsWith("live ") && t.length() > 5 && !t.startsWith("live and ")) {
            return true;
        }
        return t.endsWith("(live)") || t.endsWith("[live]") || t.endsWith("- live") || t.contains(" live: ") || t.startsWith("live!");
    }

    // ---- making one ---------------------------------------------------------------------------------------------------

    /** Makes a snapshot: channels with their programmes, and the three lists (each channel once in each). */
    public static final class Builder {
        private final long at;
        private final Map<String, Card> channels = new LinkedHashMap<>();
        private final List<String> recent = new ArrayList<>(), favourites = new ArrayList<>(), sport = new ArrayList<>();

        public Builder(long at) {
            this.at = at;
        }

        /** A channel, made once (the first call's name, number, logo and colours kept); null for one without a key. */
        public Card card(String key, String name, int number, String logo, int[] colours) {
            if (key == null || key.isEmpty()) return null;
            Card c = channels.get(key);
            if (c == null) channels.put(key, c = new Card(key, name, number, logo, colours));
            return c;
        }

        /**
         * A programme of a channel's, in time order; one that has ended by the snapshot's time, starts past AHEAD_MS or
         * would be past PROGRAMMES_MAX is left out. Its description is kept for the first DESCRIBED only.
         */
        public void prog(Card c, long start, long end, String title, String desc, String picture) {
            if (c == null || end <= at || end <= start || start >= at + AHEAD_MS || c.progs.size() >= PROGRAMMES_MAX) return;
            if (!c.progs.isEmpty() && start < c.progs.get(c.progs.size() - 1).start) return;
            c.progs.add(new Prog(start, end, title, c.progs.size() < DESCRIBED ? desc : "", picture));
        }

        /** The channel (made with card first) put in a list: RECENT, FAVOURITE or SPORT; once, and up to each list's most. */
        public void add(String section, String key) {
            List<String> l = RECENT.equals(section) ? recent : FAVOURITE.equals(section) ? favourites : SPORT.equals(section) ? sport : null;
            int max = RECENT.equals(section) ? RECENT_MAX : FAVOURITE.equals(section) ? FAVOURITES_MAX : SPORT_KEPT;
            if (l == null || key == null || !channels.containsKey(key) || l.contains(key) || l.size() >= max) return;
            l.add(key);
        }

        public CardsSnapshot build() {
            // Only the channels a list holds are kept.
            Set<String> used = new HashSet<>(recent);
            used.addAll(favourites);
            used.addAll(sport);
            Map<String, Card> kept = new LinkedHashMap<>();
            for (Map.Entry<String, Card> e : channels.entrySet()) if (used.contains(e.getKey())) kept.put(e.getKey(), e.getValue());
            return new CardsSnapshot(at, kept, recent, favourites, sport);
        }
    }

    /** A programme's picture as this snapshot has it (by its channel and start): its address, "" for none known. */
    public String picture(String key, long start) {
        Card c = key == null ? null : channels.get(key);
        if (c == null) return "";
        for (Prog p : c.progs) if (p.start == start) return p.picture;
        return "";
    }

    /** The same snapshot with programme pictures added (by channel key, then start); the others as they were. */
    public CardsSnapshot withPictures(Map<String, Map<Long, String>> found) {
        if (found == null || found.isEmpty()) return this;
        Builder b = new Builder(at);
        for (Card c : channels.values()) {
            Card n = b.card(c.key, c.name, c.number, c.logo, c.colours);
            Map<Long, String> mine = found.get(c.key);
            for (Prog p : c.progs) {
                String pic = mine != null && mine.containsKey(p.start) && !mine.get(p.start).isEmpty() ? mine.get(p.start) : p.picture;
                n.progs.add(new Prog(p.start, p.end, p.title, p.desc, pic));
            }
        }
        for (String k : recent) b.add(RECENT, k);
        for (String k : favourites) b.add(FAVOURITE, k);
        for (String k : sport) b.add(SPORT, k);
        return b.build();
    }

    // ---- kept as a small file -------------------------------------------------------------------------------------------

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", VERSION);
            o.put("at", at);
            JSONObject ch = new JSONObject();
            for (Card c : channels.values()) {
                JSONObject j = new JSONObject();
                j.put("n", c.name);
                j.put("no", c.number);
                j.put("l", c.logo);
                if (c.colours != null) j.put("c", new JSONArray().put(c.colours[0]).put(c.colours[1]));
                JSONArray ps = new JSONArray();
                for (Prog p : c.progs) ps.put(new JSONArray().put(p.start).put(p.end).put(p.title).put(p.desc).put(p.picture));
                j.put("p", ps);
                ch.put(c.key, j);
            }
            o.put("ch", ch);
            o.put("recent", new JSONArray(recent));
            o.put("fav", new JSONArray(favourites));
            o.put("sport", new JSONArray(sport));
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    /** A snapshot read back from its file; EMPTY when it can't be read (or is another version's). */
    public static CardsSnapshot fromJson(String json) {
        if (json == null || json.isEmpty()) return EMPTY;
        try {
            JSONObject o = new JSONObject(json);
            if (o.optInt("v") != VERSION) return EMPTY;
            long at = o.optLong("at");
            Map<String, Card> channels = new LinkedHashMap<>();
            JSONObject ch = o.optJSONObject("ch");
            if (ch != null) {
                for (java.util.Iterator<String> it = ch.keys(); it.hasNext(); ) {
                    String key = it.next();
                    JSONObject j = ch.optJSONObject(key);
                    if (j == null || key.isEmpty()) continue;
                    JSONArray col = j.optJSONArray("c");
                    int[] colours = col != null && col.length() >= 2 ? new int[]{col.optInt(0), col.optInt(1)} : null;
                    Card c = new Card(key, j.optString("n"), j.optInt("no"), j.optString("l"), colours);
                    JSONArray ps = j.optJSONArray("p");
                    for (int i = 0; ps != null && i < ps.length(); i++) {
                        JSONArray p = ps.optJSONArray(i);
                        if (p == null || p.length() < 3) continue;
                        c.progs.add(new Prog(p.optLong(0), p.optLong(1), p.optString(2), p.optString(3), p.optString(4)));
                    }
                    channels.put(key, c);
                }
            }
            return new CardsSnapshot(at, channels, keys(o.optJSONArray("recent"), channels), keys(o.optJSONArray("fav"), channels),
                    keys(o.optJSONArray("sport"), channels));
        } catch (JSONException e) {
            return EMPTY;
        }
    }

    private static List<String> keys(JSONArray a, Map<String, Card> channels) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String k = a.optString(i);
            if (channels.containsKey(k) && !out.contains(k)) out.add(k);
        }
        return out;
    }
}
