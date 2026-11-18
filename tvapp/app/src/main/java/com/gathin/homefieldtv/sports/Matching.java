package com.gathin.homefieldtv.sports;

import android.content.Context;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Names;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A game and the family's own playlist channels, both ways, as the phone's "Also Showing On" does it (AlsoShowing): every
 * channel that shows a game (an event channel named for it, a channel with it in its guide now, the broadcasters it is
 * on as the playlist has them, its 4K one and its HD one), English channels first; and the game a channel being watched
 * shows (its name, or its guide's programme now). Off the main thread.
 */
public final class Matching {
    private Matching() {}

    /** A channel showing the game, as the hub's "Watch on" lists it. */
    public static final class Row {
        public Channel ch;
        public String name = "", logo = "", under = "";
        public boolean now, uhd, best;
        /** How sure it is to show the game: by its own name, by the guide, by a broadcaster, a favourite, the better picture. */
        public int score;
        /** In English: 2 sure, 1 not known, 0 another language (english()). */
        public int english = 1;
    }

    /** Every playlist channel that shows the game at when, best first (see rank()); the one playing now marked. */
    public static List<Row> rows(Context c, Game g, Channel playing, long when) {
        Library lib = Library.get(c);
        List<Channel> all = lib.all();
        Map<String, Row> byKey = new LinkedHashMap<>();
        if (g == null || all.isEmpty()) return new ArrayList<>();
        Set<String> fav = new HashSet<>(lib.favourites());
        String[][] clubs = {clubNames(g.homeName, g.homeFull), clubNames(g.awayName, g.awayFull)};
        // The playlists' event channels named for the game ("EPL 03: Arsenal v Chelsea 15:00"): the surest of all.
        for (Channel ch : all) {
            if (!names(channelWords(ch.name), clubs)) continue;
            Row r = row(ch, playing, fav);
            r.under = "Named for this game";
            r.score += 5;
            byKey.put(ch.key, r);
            if (byKey.size() >= 12) break;
        }
        // The channels with the game in their guide.
        List<Game> one = new ArrayList<>();
        one.add(g);
        for (Channel ch : all) {
            Guide.Schedule s = lib.schedule(ch.key);
            int at = s == null ? -1 : s.at(when);
            if (at < 0) continue;
            String title = s.title[at] == null ? "" : s.title[at], desc = s.desc == null || s.desc[at] == null ? "" : s.desc[at];
            if (!OnAir.looksLikeFixture(title) && !OnAir.looksLikeFixture(desc) && !names(words(title + " " + desc), clubs)) continue;
            if (OnAir.match(title, desc, s.start[at], s.stop[at], one) == null && !names(words(title + " " + desc), clubs)) continue;
            Row r = byKey.get(ch.key);
            if (r == null) {
                r = row(ch, playing, fav);
                r.under = title;
                byKey.put(ch.key, r);
            }
            r.score += 4;
            if (byKey.size() >= 24) break;
        }
        // The broadcasters it is on, as the playlist has them: its 4K channel and its HD one. Their channels found by
        // the name's key (the library's index of names), not by reading every channel's name again for each.
        for (String name : Channels.names(g)) {
            List<Channel> same = new ArrayList<>();
            for (Channel ch : lib.named(name)) if (lib.listed(ch)) same.add(ch);
            Channel[] tier = tiers(same, name);
            for (int t = 1; t >= 0; t--) {
                // One row per channel: a broadcaster's 4K and HD versions of one channel are its one row.
                Channel ch = lib.row(tier[t]);
                if (ch == null) continue;
                Row r = byKey.get(ch.key);
                if (r == null) {
                    r = row(ch, playing, fav);
                    r.under = t == 1 ? "In 4K" : "Listed for this game";
                    byKey.put(ch.key, r);
                }
                r.uhd |= t == 1;
                r.score += 3;
            }
        }
        List<Row> out = new ArrayList<>(byKey.values());
        out.sort((a, b) -> Integer.compare(rank(b), rank(a)));
        for (Row r : out) {
            if (!r.now) {
                r.best = true;
                break;
            }
        }
        return out;
    }

    private static Row row(Channel ch, Channel playing, Set<String> fav) {
        Row r = new Row();
        r.ch = ch;
        // As the family named it, with its logo as chosen (the new logos: LogoPack); found by its playlist's name.
        r.name = tidy(com.gathin.homefieldtv.prefs.ChannelEdits.name(ch));
        r.logo = com.gathin.homefieldtv.engine.LogoPack.url(ch);
        r.uhd = Names.uhd(ch.name);
        r.now = playing != null && playing.key.equals(ch.key);
        r.english = english(ch.name + " " + (ch.group == null ? "" : ch.group));
        if (fav.contains(ch.key)) r.score += 2;
        if (r.uhd) r.score += 1;
        return r;
    }

    /** Pure: what plays now first, then English before not known before another language, then how sure. */
    static int rank(Row r) {
        return (r.now ? 1000 : 0) + 100 + r.english * 30 + r.score;
    }

    /** A broadcaster's channels in the playlist ("TNT Sports 1"): {its best HD one, its best 4K one}, either null. */
    static Channel[] tiers(List<Channel> all, String broadcaster) {
        Channel[] out = new Channel[2];
        if (broadcaster == null || broadcaster.isEmpty() || Names.isService(broadcaster)) return out;
        String want = Names.key(broadcaster);
        if (want.isEmpty()) return out;
        int[] bestScore = {-1, -1};
        for (Channel ch : all) {
            if (!want.equals(Names.key(ch.name))) continue;
            int t = Names.uhd(ch.name) ? 1 : 0;
            int s = english(ch.name + " " + (ch.group == null ? "" : ch.group));
            if (s > bestScore[t]) {
                bestScore[t] = s;
                out[t] = ch;
            }
        }
        return out;
    }

    /**
     * The game a channel shows at when, among games: its name named for one ("EPL 03: Arsenal v Chelsea"), else its guide's
     * programme then; null for none. Off the main thread.
     */
    public static Game gameOn(Library lib, Channel ch, long when, List<Game> games) {
        if (ch == null || games == null || games.isEmpty()) return null;
        String cw = channelWords(ch.name);
        for (Game g : games) {
            if (names(cw, new String[][]{clubNames(g.homeName, g.homeFull), clubNames(g.awayName, g.awayFull)})) return g;
        }
        Guide.Schedule s = lib == null ? null : lib.schedule(ch.key);
        int at = s == null ? -1 : s.at(when);
        if (at < 0) return null;
        String title = s.title[at] == null ? "" : s.title[at], desc = s.desc == null || s.desc[at] == null ? "" : s.desc[at];
        Game m = OnAir.matchEither(title, desc, s.start[at], s.stop[at], games);
        if (m != null) return m;
        String tw = words(title + " " + desc);
        for (Game g : games) {
            if (names(tw, new String[][]{clubNames(g.homeName, g.homeFull), clubNames(g.awayName, g.awayFull)})) return g;
        }
        return null;
    }

    // ---- English first ----------------------------------------------------------------------------------------------

    private static final Set<String> ENGLISH_TAGS = new HashSet<>(Arrays.asList(
            "uk", "gb", "en", "eng", "english", "england", "ie", "irl", "ireland", "us", "usa", "ca", "can", "au", "aus", "nz",
            "sco", "scotland", "wales", "nir"));
    private static final Set<String> OTHER_TAGS = new HashSet<>(Arrays.asList(
            "de", "ger", "at", "ch", "fr", "fra", "es", "esp", "spa", "nl", "pt", "por", "be", "it", "ita", "pl", "pol", "tr",
            "tur", "ar", "arab", "arabic", "br", "bra", "mx", "lat", "latam", "latino", "gr", "ro", "hu", "cz", "sk", "se",
            "swe", "no", "nor", "dk", "den", "fi", "fin", "ru", "rus", "ua", "il", "ir", "kr", "jp", "cn", "al", "alb", "exyu",
            "yu", "hr", "rs", "srb", "ba", "si", "bg", "mk", "ma", "dz", "tn", "eg", "sa", "ae", "qa", "kw", "af", "pk", "bd",
            "ku", "vf", "vost"));
    /** Words that say a channel speaks another language, wherever they are. */
    private static final String[] OTHER_WORDS = {" arabic ", " french ", " german ", " spanish ", " italian ", " portuguese ",
            " turkish ", " polish ", " dutch ", " greek ", " russian ", " hindi ", " urdu ", " bangla ", " deutsch ",
            " espanol ", " francais ", " deportes ", " latino ", " latam "};
    /** British, Irish, American and Australian networks, in English whatever their tag says. */
    private static final String[] ENGLISH_NETWORKS = {" sky sports ", " tnt sport ", " tnt sports ", " bt sport ", " bbc ",
            " itv ", " itv1 ", " itv4 ", " channel 4 ", " channel 5 ", " premier sports ", " viaplay sports ", " eurosport 1 ",
            " espn ", " fox sports ", " nbc ", " cbs ", " tsn ", " sportsnet ", " optus ", " stan sport ", " peacock ",
            " sky sport news ", " racing tv ", " free sports ", " s4c "};

    /**
     * Pure: how sure a channel (its name and group) is to be in English: 2 for an English tag ("UK:", "|US|", "IE -",
     * "[EN]") or a British, Irish, American or Australian network; 0 for another country's or language's tag or a word
     * that says so ("AR:", "|DE|", "Arabic", "Deportes"); 1 when it doesn't say.
     */
    static int english(String nameAndGroup) {
        String n = words(nameAndGroup);
        if (n.contains(" english ")) return 2;
        String[] toks = n.trim().split(" ");
        if (toks.length > 0 && !toks[0].isEmpty()) {
            if (ENGLISH_TAGS.contains(toks[0])) return 2;
            if (OTHER_TAGS.contains(toks[0])) return 0;
        }
        for (String w : OTHER_WORDS) if (n.contains(w)) return 0;
        if (n.contains(" eng ")) return 2;
        for (String net : ENGLISH_NETWORKS) if (n.contains(net)) return 2;
        if (toks.length > 1) {
            String last = toks[toks.length - 1];
            if (ENGLISH_TAGS.contains(last)) return 2;
            if (OTHER_TAGS.contains(last)) return 0;
        }
        return 1;
    }

    private static final Pattern LEAD = Pattern.compile("^\\s*[\\[(|]?\\s*([A-Za-z]{2,7})\\s*[\\])|:\\-]+\\s*");
    private static final Pattern TAIL = Pattern.compile(
            "(?i)(\\s+(?:hd|fhd|uhd|4k|sd|hevc|raw|h265|1080p|720p|50fps|60fps|backup|bk|alt|vip))+\\s*$");
    /** Names kept in capitals when a channel's name is all capitals (the rest become words: "BBC One", "TNT Sports"). */
    private static final Set<String> ACRONYMS = new HashSet<>(Arrays.asList(
            "BBC", "ITV", "TNT", "ESPN", "NBC", "CBS", "TSN", "BT", "UK", "US", "USA", "MTV", "CNN", "RTE", "LFC", "MUTV", "NFL",
            "NBA", "NHL", "MLB", "UFC", "WWE", "PGA", "ATP", "WTA", "DAZN", "HD", "UHD", "EPL", "SPL", "SRF", "ORF", "ZDF", "ARD",
            "RAI", "TV", "EFL", "PDC", "NOW"));

    /** Pure: a playlist channel's name made plain: its country tag and picture tags gone, capitals made words ("TNT Sports 1"). */
    static String tidy(String name) {
        String n = name == null ? "" : name.trim();
        Matcher m = LEAD.matcher(n);
        if (m.lookingAt() && m.end() < n.length()) {
            String tag = m.group(1).toLowerCase(Locale.ROOT);
            if (ENGLISH_TAGS.contains(tag) || OTHER_TAGS.contains(tag)) n = n.substring(m.end());
        }
        n = TAIL.matcher(n).replaceAll("").trim();
        n = n.replaceAll("\\s+", " ");
        if (!n.isEmpty() && n.equals(n.toUpperCase(Locale.ROOT))) {
            StringBuilder b = new StringBuilder();
            for (String w : n.split(" ")) {
                if (b.length() > 0) b.append(' ');
                boolean letters = !w.isEmpty() && w.chars().allMatch(Character::isLetter);
                if (!letters || ACRONYMS.contains(w)) b.append(w);
                else b.append(w.charAt(0)).append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            n = b.toString();
        }
        return n.isEmpty() ? (name == null ? "" : name.trim()) : n;
    }

    // ---- clubs named ------------------------------------------------------------------------------------------------

    /** Channels' names as words() makes them, kept: the hub asks for every game of the day in turn. */
    private static final Map<String, String> CHANNEL_WORDS = new ConcurrentHashMap<>();

    private static String channelWords(String name) {
        String n = name == null ? "" : name;
        String w = CHANNEL_WORDS.get(n);
        if (w == null) {
            if (CHANNEL_WORDS.size() > 200_000) CHANNEL_WORDS.clear();
            w = words(n);
            CHANNEL_WORDS.put(n, w);
        }
        return w;
    }

    private static final Pattern MARKS = Pattern.compile("\\p{M}"), NOT_WORDS = Pattern.compile("[^a-z0-9]+");

    /** Pure: words made comparable: lower case, accents and punctuation gone, a space either side. */
    static String words(String s) {
        String n = MARKS.matcher(Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKD)).replaceAll("")
                .toLowerCase(Locale.ROOT).replace("&", " and ");
        return " " + NOT_WORDS.matcher(n).replaceAll(" ").trim() + " ";
    }

    /** Pure: whether words (as words() makes them) name both clubs, each by one of its names. */
    static boolean names(String words, String[][] clubs) {
        for (String[] club : clubs) {
            boolean found = false;
            for (String n : club) {
                if (words.contains(" " + n + " ")) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return clubs.length > 0;
    }

    /**
     * Pure: the words a club goes by in a channel's name or a guide: ESPN's short name and its full one ("Man United",
     * "Manchester United"), United as Utd, without FC, and its own word when it has one ("Tottenham", "Wolverhampton";
     * never a word two clubs share, as City, United or Manchester).
     */
    static String[] clubNames(String shortName, String full) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String name : new String[]{shortName, full}) {
            String w = words(name).trim();
            if (w.isEmpty()) continue;
            w = (" " + w + " ").replace(" fc ", " ").replace(" afc ", " ").replace(" cf ", " ").trim();
            if (w.isEmpty()) continue;
            out.add(w);
            if ((" " + w + " ").contains(" united ")) out.add((" " + w + " ").replace(" united ", " utd ").trim());
            for (String part : w.split(" ")) {
                if (part.length() >= 5 && !SHARED.contains(part)) {
                    out.add(part);
                    break;
                }
            }
        }
        return out.toArray(new String[0]);
    }

    /** Words in more than one club's name, never enough on their own. */
    private static final Set<String> SHARED = new HashSet<>(Arrays.asList(
            "united", "city", "town", "county", "rovers", "athletic", "athletico", "atletico", "albion", "wanderers", "hotspur",
            "manchester", "sheffield", "bristol", "nottingham", "birmingham", "borussia", "sporting", "olympique", "olympic",
            "racing", "deportivo", "union", "dynamo", "dinamo", "inter", "internacional", "real", "royal", "young", "saint",
            "queens", "rangers", "celtic", "academy", "women", "ladies", "reserves", "under", "north", "south", "east", "west",
            "club", "football", "sport", "sports", "stade", "stadium"));
}
