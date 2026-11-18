package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The tv-logo project's channel logos (github.com/tv-logo/tv-logos: free logos for dark grounds, free for personal use)
 * matched to the playlist's channel names, each match with how sure it is. Pure: the JVM tests use it as it is.
 *
 * <p>Its index is compact text, a line a folder: {@code countries/united-kingdom<TAB>bbc-one-uk bbc-two-uk …}, the
 * logos' file names without ".png" (lines starting # are notes). Only the countries asked for are kept in memory.
 *
 * <p>A channel's name is made comparable as the logos' names are: small capitals, superscripts and accents plain, lower
 * case; a leading list number ("101 | ") and the quality tags anywhere (FHD, HD, SD, UHD, 4K, HEVC, H.265, 50 FPS, RAW,
 * VIP, backup…) gone; country tags and separators at either end ("UK:", "UK |", "|UK|", "[UK]", "(GB)") gone, the first
 * one kept as where the channel is from (else the group's: "UK | Sport"); One as 1, Sports as Sport, & as and; "+1" kept
 * apart as "plus" (a timeshift never takes the channel's own logo, nor the other way round); then a few aliases (ITV as
 * ITV1, CBBC as BBC CBBC, More4, Sky Main Event as Sky Sports Main Event…). The numbers in a name always count: TNT
 * Sports 1 is never TNT Sports 2.
 *
 * <p>How sure a logo is (sure, 0–1): the same name in the channel's own country's folder (the United Kingdom's when it
 * says none) is 1; in the international folder 0.9 (0.85 for a channel that says its country); Ireland's for a British
 * channel, or the other way, 0.85; another single country's 0.85 when the name says no country, else 0.7, or 0.6 when the
 * channel says another country. Found by dropping a generic word at its end ("Dave TV") or a region ("ITV1 London"): 0.9
 * of that. Found by most of its words only: at most 0.78, never shown by itself (SURE is 0.8), only offered in Change
 * logo. Among equally sure ones, the plain logo comes before its HD-badged, wide, white, alternative, icon, mono and
 * light-ground versions and before an old one; a UHD channel's own UHD logo first.
 */
public final class LogoMatch {
    /** A logo is shown by itself only this sure or more. */
    public static final float SURE = 0.8f;
    public static final String UK = "united-kingdom", IE = "ireland", INT = "international";

    static final int HD = 1, HZ = 2, ICON = 4, LIGHT = 8, WHITE = 16, MONO = 32, ALT = 64, UHD = 128, OLD = 256, OTHER = 512;

    /** One logo in the pack. */
    public static final class Logo {
        /** Its path in the project ("countries/united-kingdom/hd/bbc-one-hd-uk.png"). */
        public final String path;
        /** Its country's folder ("united-kingdom", "nordic"). */
        public final String country;
        final String key;
        final String[] tokens;
        final int variant;
        final boolean timeshift;

        Logo(String path, String country, String key, String[] tokens, int variant, boolean timeshift) {
            this.path = path;
            this.country = country;
            this.key = key;
            this.tokens = tokens;
            this.variant = variant;
            this.timeshift = timeshift;
        }

        /** Its name as the family reads it, from its file's name: "BBC One" from bbc-one-uk (its kind is about()'s). */
        public String label() {
            return labelOf(path);
        }

        /** Its country and kind for a menu: "United Kingdom  ·  HD  ·  Wide". */
        public String about() {
            return aboutOf(this);
        }
    }

    /** Pure: a logo's name as the family reads it, from its path: "BBC One" from .../bbc-one-uk.png. */
    public static String labelOf(String path) {
        String file = path.substring(path.lastIndexOf('/') + 1);
        if (file.endsWith(".png")) file = file.substring(0, file.length() - 4);
        String[] t = file.split("-");
        StringBuilder b = new StringBuilder();
        int end = t.length > 1 && t[t.length - 1].length() <= 3 ? t.length - 1 : t.length;
        for (int i = 0; i < end; i++) {
            String w = t[i];
            if (w.isEmpty() || VARIANT_WORDS.contains(w)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(ACRONYMS.contains(w) || w.length() == 1 ? w.toUpperCase(Locale.ROOT) : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return b.toString();
    }

    /** A logo's country and kind for a menu: "United Kingdom  ·  HD  ·  Wide". */
    static String aboutOf(Logo l) {
        StringBuilder b = new StringBuilder(countryName(l.country));
        if ((l.variant & UHD) != 0) b.append("  ·  UHD");
        if ((l.variant & HD) != 0) b.append("  ·  HD");
        if ((l.variant & HZ) != 0) b.append("  ·  Wide");
        if ((l.variant & WHITE) != 0) b.append("  ·  White");
        if ((l.variant & ALT) != 0) b.append("  ·  Another version");
        if ((l.variant & ICON) != 0) b.append("  ·  Icon");
        if ((l.variant & MONO) != 0) b.append("  ·  One colour");
        if ((l.variant & LIGHT) != 0) b.append("  ·  For light grounds");
        if ((l.variant & OLD) != 0) b.append("  ·  Old");
        return b.toString();
    }

    /** A logo found for a channel and how sure the match is (0–1). */
    public static final class Found {
        public final Logo logo;
        public final float sure;
        final int score;

        Found(Logo logo, float sure, int score) {
            this.logo = logo;
            this.sure = sure;
            this.score = score;
        }
    }

    /**
     * A channel's name made comparable: its key, the key with its country tags kept (full: "92 News UK"), without a
     * generic word at its end, without a region; its words; its country; UHD; a timeshift.
     */
    static final class Name {
        final String key, full, generic, region;
        final List<String> tokens;
        final String country;
        final boolean uhd, timeshift;

        Name(String key, String full, String generic, String region, List<String> tokens, String country, boolean uhd, boolean timeshift) {
            this.key = key;
            this.full = full;
            this.generic = generic;
            this.region = region;
            this.tokens = tokens;
            this.country = country;
            this.uhd = uhd;
            this.timeshift = timeshift;
        }
    }

    private final List<Logo> logos;
    private final Map<String, List<Logo>> byKey;
    private final Map<String, List<Logo>> byToken;
    /** The countries' folders it holds. */
    public final Set<String> countries;

    private LogoMatch(List<Logo> logos, Set<String> countries) {
        this.logos = logos;
        this.countries = Collections.unmodifiableSet(countries);
        byKey = new HashMap<>(logos.size() * 2);
        byToken = new HashMap<>();
        for (Logo l : logos) {
            if (l.key.isEmpty()) continue;
            List<Logo> k = byKey.get(l.key);
            if (k == null) byKey.put(l.key, k = new ArrayList<>(2));
            k.add(l);
            for (String t : l.tokens) {
                List<Logo> p = byToken.get(t);
                if (p == null) byToken.put(t, p = new ArrayList<>(4));
                if (p.isEmpty() || p.get(p.size() - 1) != l) p.add(l);
            }
        }
    }

    /** How many logos it holds. */
    public int size() {
        return logos.size();
    }

    /**
     * Pure: the white version of a logo (its path), for a logo too dark to see on a dark ground: the same channel's logo
     * in the same country marked white, the one most like it (HD, wide, icon as it is) first; null when there is none or
     * it is white already.
     */
    public String whiteOf(String path) {
        if (path == null) return null;
        Logo me = null;
        for (Logo l : logos) {
            if (l.path.equals(path)) {
                me = l;
                break;
            }
        }
        if (me == null || (me.variant & WHITE) != 0 || me.key.isEmpty()) return null;
        List<Logo> same = byKey.get(me.key);
        if (same == null) return null;
        Logo best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (Logo l : same) {
            if (l == me || (l.variant & WHITE) == 0 || (l.variant & LIGHT) != 0 || !l.country.equals(me.country)) continue;
            int diff = Integer.bitCount((l.variant & ~WHITE) ^ (me.variant & ~WHITE));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = l;
            }
        }
        return best == null ? null : best.path;
    }

    // ---- the index ------------------------------------------------------------------------------------------------

    /** Pure: an index's text read, only the countries' folders in wanted kept (null: all of them). */
    public static LogoMatch parse(String text, Set<String> wanted) {
        List<Logo> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> pool = new HashMap<>();
        if (text != null) {
            int at = 0, n = text.length();
            while (at < n) {
                int end = text.indexOf('\n', at);
                if (end < 0) end = n;
                String line = text.substring(at, end).trim();
                at = end + 1;
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                int tab = line.indexOf('\t');
                if (tab <= 0) continue;
                String folder = line.substring(0, tab).trim();
                String country = countryOf(folder);
                if (country.isEmpty() || wanted != null && !wanted.contains(country)) continue;
                int flags = folderFlags(folder);
                if (flags < 0) continue;
                seen.add(country);
                for (String file : line.substring(tab + 1).trim().split(" ")) {
                    Logo l = logo(folder, country, file, flags, pool);
                    if (l != null) out.add(l);
                }
            }
        }
        return new LogoMatch(out, seen);
    }

    /**
     * Pure: the compact index from the project's paths (its tree): the PNGs under countries/, a line a folder, in the
     * paths' order; screen bugs, mosaics and names that are not plain lower-case letters, digits and dashes left out.
     */
    public static String compact(Iterable<String> paths, String note) {
        Map<String, StringBuilder> folders = new LinkedHashMap<>();
        for (String p : paths) {
            if (p == null || !p.startsWith("countries/") || !p.endsWith(".png") || p.contains("screen-bug")) continue;
            int slash = p.lastIndexOf('/');
            String folder = p.substring(0, slash), file = p.substring(slash + 1, p.length() - 4);
            if (!plainPath(folder) || !plainPath(file) || file.isEmpty()) continue;
            StringBuilder b = folders.get(folder);
            if (b == null) folders.put(folder, b = new StringBuilder());
            if (b.length() > 0) b.append(' ');
            b.append(file);
        }
        StringBuilder out = new StringBuilder();
        if (note != null && !note.isEmpty()) out.append("# ").append(note.replace('\n', ' ')).append('\n');
        for (Map.Entry<String, StringBuilder> e : folders.entrySet()) out.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        return out.toString();
    }

    private static boolean plainPath(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-' || c == '_' || c == '/' || c == '.')) return false;
        }
        return true;
    }

    /** "countries/united-kingdom/hd" → "united-kingdom"; "countries/nordic/sweden" → "nordic"; "" for any other. */
    static String countryOf(String folder) {
        if (!folder.startsWith("countries/")) return "";
        String rest = folder.substring(10);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    /** A sub-folder's kind: hd/ (HD-badged), obsolete/ and old/ (old), other/; -1 for one never used (screen bugs). */
    private static int folderFlags(String folder) {
        int f = 0;
        for (String part : folder.split("/")) {
            switch (part) {
                case "hd": f |= HD; break;
                case "obsolete": case "old": f |= OLD; break;
                case "other": f |= OTHER; break;
                case "horizontal": f |= HZ; break;
                case "screen-bug": case "bug": return -1;
                default: break;
            }
        }
        return f;
    }

    private static final Set<String> VARIANT_WORDS = new HashSet<>(Arrays.asList("hd", "hz", "icon", "light", "white", "mono",
            "dark", "alt", "alt2", "alt3", "badge", "uhd", "4k", "hdr"));
    /** Words a logo's name writes in capitals. */
    private static final Set<String> ACRONYMS = new HashSet<>(Arrays.asList("bbc", "itv", "tnt", "bt", "mtv", "tv", "uk", "us", "cnn",
            "rte", "tg", "nbc", "cbs", "abc", "hbo", "amc", "tlc", "s4c", "stv", "qvc", "gb", "tcm", "pbs", "cbbc", "bet", "fx", "espn",
            "nfl", "nba", "f1", "dmax", "rtl", "zdf", "ard", "npo", "tf1", "dazn", "pga", "ufc", "wwe", "lfctv", "mutv", "gaa", "ndtv", "ary",
            "b4u", "ben", "pop", "vh1", "hgtv", "jml", "tjc", "tbn", "uhd", "int", "nhk", "cgtn", "rt"));

    /** One file's logo: its key from its words, its kind from the words that say a version. */
    private static Logo logo(String folder, String country, String file, int flags, Map<String, String> pool) {
        if (file.isEmpty() || file.startsWith("0_")) return null;
        String[] t = file.split("-");
        int end = t.length;
        // The country's code at its end ("-uk", "-int", "-ie").
        if (end > 1 && t[end - 1].length() >= 2 && t[end - 1].length() <= 3 && letters(t[end - 1])) end--;
        int variant = flags;
        List<String> words = new ArrayList<>(end);
        for (int i = 0; i < end; i++) {
            String w = t[i];
            if (w.isEmpty()) continue;
            switch (w) {
                case "hd": if (end > 1) { variant |= HD; continue; } break;
                case "hz": variant |= HZ; continue;
                case "white": variant |= WHITE; continue;
                case "light": variant |= LIGHT; continue;
                case "mono": variant |= MONO; continue;
                case "dark": continue;
                case "bug": return null;
                case "uhd": case "4k": case "hdr": variant |= UHD; continue;
                case "ultra":
                    if (i + 1 < end && t[i + 1].equals("hdr")) {
                        variant |= UHD;
                        i++;
                        continue;
                    }
                    break;
                case "icon": case "badge":
                    variant |= ICON;
                    if (i + 1 < end && digits(t[i + 1])) i++;
                    continue;
                default:
                    if (w.startsWith("alt") && (w.length() == 3 || digits(w.substring(3)))) {
                        variant |= ALT;
                        if (i + 1 < end && digits(t[i + 1])) i++;
                        continue;
                    }
                    break;
            }
            words.add(w);
        }
        if (words.isEmpty()) return null;
        List<String> norm = words(words);
        boolean shift = timeshift(norm);
        String key = join(norm);
        if (key.isEmpty()) return null;
        List<String> toks = new ArrayList<>();
        for (String w : norm) splitDigits(w, toks);
        String[] ta = new String[toks.size()];
        for (int i = 0; i < ta.length; i++) ta[i] = shared(pool, toks.get(i));
        return new Logo(folder + "/" + file + ".png", country, shared(pool, key), ta, variant, shift);
    }

    private static String shared(Map<String, String> pool, String s) {
        String had = pool.get(s);
        if (had != null) return had;
        pool.put(s, s);
        return s;
    }

    // ---- a channel's name -------------------------------------------------------------------------------------------

    /** Quality tags (and a backup feed's), wherever they are in a name. */
    private static final Set<String> QUALITY = new HashSet<>(Arrays.asList("hd", "fhd", "uhd", "4k", "8k", "sd", "raw", "hevc",
            "h264", "h265", "x264", "x265", "avc", "1080", "1080p", "1080i", "720", "720p", "576p", "576i", "50fps", "60fps", "25fps",
            "30fps", "fps", "hdr", "sdr", "hq", "lq", "vip", "multiaudio", "backup", "bk", "uhd4k", "4khdr", "fullhd", "geo",
            "geoblocked", "orig", "original", "hd1080", "hd720", "ᴴᴰ"));
    private static final Set<String> UHD_TAGS = new HashSet<>(Arrays.asList("uhd", "4k", "8k", "uhd4k", "4khdr"));
    /** Country tags and where each country's logos are. */
    private static final Map<String, String> COUNTRY = new HashMap<>();
    /** Words that end a name without saying which channel it is ("Dave TV"). */
    private static final Set<String> GENERIC = new HashSet<>(Arrays.asList("tv", "channel", "television", "network", "hd"));
    /** Regions of a national channel with no logo of their own ("BBC One North West", "ITV1 London"). */
    private static final Set<String> REGIONS = new HashSet<>(Arrays.asList("london", "north", "south", "east", "west", "northeast",
            "northwest", "southeast", "southwest", "yorkshire", "yorks", "midlands", "anglia", "central", "granada", "meridian",
            "tyne", "tees", "border", "westcountry", "cumbria", "oxford", "devon", "cornwall", "kent", "sussex", "lincolnshire",
            "humber", "ne", "nw", "se", "sw", "yorks", "eastmidlands", "westmidlands", "solent", "calendar", "tynetees",
            "channelislands", "hampshire"));
    /** The names the family's playlists may use for a logo's name (keys). */
    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Map<String, String> WORDS = new HashMap<>();

    static {
        String[][] countries = {
                {"united-kingdom", "uk", "gb", "gbr", "eng", "england", "british", "en", "scotland", "wales"},
                {"ireland", "ie", "irl", "ireland", "eire"},
                {"united-states", "us", "usa", "america"},
                {"canada", "ca", "can", "canada"},
                {"australia", "au", "aus", "australia"},
                {"new-zealand", "nz"},
                {"germany", "de", "ger", "germany", "deutschland"},
                {"austria", "at", "austria"},
                {"switzerland", "swiss", "switzerland"},
                {"france", "fr", "france"},
                {"spain", "es", "spain", "espana"},
                {"italy", "it", "ita", "italy", "italia"},
                {"netherlands", "nl", "netherlands", "holland"},
                {"belgium", "be", "belgium"},
                {"portugal", "pt", "portugal"},
                {"poland", "pl", "poland", "polska"},
                {"turkey", "tr", "turkey", "turkiye"},
                {"greece", "gr", "greece"},
                {"romania", "ro", "romania"},
                {"serbia", "rs", "serbia"},
                {"croatia", "hr", "croatia"},
                {"nordic", "se", "no", "dk", "fi", "sweden", "norway", "denmark", "finland", "nordic", "scandinavia"},
                {"south-africa", "za"},
                {"argentina", "ar", "argentina"},
                {"mexico", "mx", "mexico"},
                {"brazil", "br", "brazil", "brasil"},
                {"india", "india"},
                {"united-arab-emirates", "uae", "ae"},
                {"international", "int", "intl", "international"},
        };
        for (String[] c : countries) for (int i = 1; i < c.length; i++) COUNTRY.put(c[i], c[0]);
        WORDS.put("one", "1");
        WORDS.put("two", "2");
        WORDS.put("three", "3");
        WORDS.put("four", "4");
        WORDS.put("sports", "sport");
        WORDS.put("junior", "jr");
        WORDS.put("&", "and");
        String[][] aliases = {
                {"itv", "itv1"}, {"itvhd", "itv1"}, {"ch4", "channel4"}, {"c4", "channel4"}, {"ch5", "channel5"}, {"c5", "channel5"},
                {"five", "channel5"}, {"5", "channel5"}, {"more4", "4more"}, {"more4plus", "4moreplus"}, {"cbbc", "bbccbbc"},
                {"cbeebies", "bbccbeebies"}, {"natgeo", "nationalgeographic"}, {"natgeowild", "nationalgeographicwild"},
                {"discovery", "discoverychannel"}, {"discoveryplus", "discoverychannelplus"}, {"history", "skyhistory"},
                {"w", "wnetwork"}, {"skymainevent", "skysportmainevent"}, {"skypremierleague", "skysportpremierleague"},
                {"skysportpl", "skysportpremierleague"}, {"skysportprem", "skysportpremierleague"}, {"skyf1", "skysportf1"},
                {"skyfootball", "skysportfootball"}, {"skycricket", "skysportcricket"}, {"skygolf", "skysportgolf"},
                {"skyarena", "skysportarena"}, {"skyaction", "skysportaction"}, {"skyracing", "skysportracing"},
                {"skytennis", "skysporttennis"}, {"skymix", "skysportmix"}, {"skydarts", "skysportdarts"}, {"skynfl", "skysportnfl"},
                {"crimeplusinvestigation", "crimeandinvestigation"}, {"candi", "crimeandinvestigation"},
                {"crimeinvestigation", "crimeandinvestigation"}, {"nickjunior", "nickjr"}, {"bbcworld", "bbcworldnews"},
                {"tntsportsultimate", "tntsportultimate"}, {"premiersport", "premiersport1"}, {"uktv", "u"},
                {"ideal", "idealworld"}, {"qvcuk", "qvc"}, {"rteone", "rte1"}, {"virginmedia", "virginmedia1"},
        };
        for (String[] a : aliases) ALIASES.put(a[0], a[1]);
    }

    /** Pure: a channel's name (and its group) made comparable. */
    static Name name(String channel, String group) {
        String s = Names.plain(channel == null ? "" : channel).toLowerCase(Locale.ROOT);
        List<String> raw = new ArrayList<>();
        List<Boolean> hard = new ArrayList<>();
        tokens(s, raw, hard);
        // A list number before the name ("101 | BBC One", "101. BBC One", "1001 BBC One"), not a name's own ("5 USA").
        if (raw.size() > 1 && digits(raw.get(0)) && (raw.get(0).length() >= 3 || hard.get(0))) {
            raw.remove(0);
            hard.remove(0);
        }
        boolean uhd = false;
        // The words kept, each with whether a mark (not a space) came after it (a mark after a word left out goes to the one before).
        List<String> t = new ArrayList<>(raw.size());
        List<Boolean> th = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            String w = raw.get(i);
            String next = i + 1 < raw.size() ? raw.get(i + 1) : "";
            int skip = -1;
            if (QUALITY.contains(w)) {
                uhd |= UHD_TAGS.contains(w);
                skip = 0;
            } else if ((w.equals("h") || w.equals("x")) && (next.equals("264") || next.equals("265")) || digits(w) && next.equals("fps")) {
                skip = 1;
            } else if ((w.equals("full") || w.equals("ultra")) && next.equals("hd")) {
                uhd |= w.equals("ultra");
                skip = 1;
            }
            if (skip < 0) {
                t.add(w);
                th.add(hard.get(i));
                continue;
            }
            i += skip;
            if (hard.get(i) && !th.isEmpty()) th.set(th.size() - 1, true);
        }
        List<String> tagged = new ArrayList<>(t);
        String country = null;
        // Country tags at the start: "UK: …", "|UK| …", "[UK] …", "UK …"; "United Kingdom" and the like as two words.
        while (t.size() > 1) {
            String two = t.size() > 2 ? t.get(0) + "-" + t.get(1) : "";
            String c2 = two.equals("united-kingdom") ? UK : two.equals("united-states") ? "united-states"
                    : two.equals("south-africa") ? "south-africa" : two.equals("new-zealand") ? "new-zealand" : null;
            if (c2 != null && hasLetters(t.subList(2, t.size()))) {
                if (country == null) country = c2;
                t.remove(0);
                t.remove(0);
                th.remove(0);
                th.remove(0);
                continue;
            }
            String c = COUNTRY.get(t.get(0));
            if (c == null || !hasLetters(t.subList(1, t.size()))) break;
            if (country == null) country = c;
            t.remove(0);
            th.remove(0);
        }
        // At the end only a sure one ("UK", "USA", "IE") or one set apart ("… (PL)", "… | DE"), never a name's own
        // last word ("Sky Sports PL", "5 USA" keeps its USA: nothing but a number would be left).
        while (t.size() > 1) {
            int n = t.size();
            String last = t.get(n - 1), c = COUNTRY.get(last);
            boolean sure = STRONG.contains(last) || th.get(n - 2);
            if (c == null || !sure || last.equals("en") || last.equals("scotland") || last.equals("wales")
                    || !hasLetters(t.subList(0, n - 1))) break;
            if (country == null) country = c;
            t.remove(n - 1);
            th.remove(n - 1);
        }
        if (country == null && group != null) country = countryOfGroup(group);
        List<String> norm = words(t);
        boolean shift = timeshift(norm);
        String key = join(norm);
        String full = tagged.size() == t.size() ? null : join(words(tagged));
        // Without a generic word at its end ("Dave TV"), without a region ("ITV1 London", "BBC One North West").
        String generic = null, region = null;
        if (norm.size() > 1 && GENERIC.contains(norm.get(norm.size() - 1))) generic = join(norm.subList(0, norm.size() - 1));
        int keep = norm.size();
        while (keep > 1 && REGIONS.contains(norm.get(keep - 1))) keep--;
        if (keep < norm.size()) region = join(norm.subList(0, keep));
        List<String> toks = new ArrayList<>();
        for (String w : norm) splitDigits(w, toks);
        return new Name(key, full, generic, region, toks, country, uhd, shift);
    }

    /** Country tags at a name's end that are surely tags, whatever comes before them. */
    private static final Set<String> STRONG = new HashSet<>(Arrays.asList("uk", "gb", "usa", "us", "ie", "irl", "int"));

    /** Whether some word in these has a letter in it (a name is left, not only a number). */
    private static boolean hasLetters(List<String> words) {
        for (String w : words) for (int i = 0; i < w.length(); i++) if (Character.isLetter(w.charAt(i))) return true;
        return false;
    }

    /** Pure: the country a group's name says ("UK | Sport", "USA ENTERTAINMENT", "Ireland"), or null. */
    static String countryOfGroup(String group) {
        String s = Names.plain(group).toLowerCase(Locale.ROOT);
        List<String> t = new ArrayList<>();
        tokens(s, t, new ArrayList<>());
        for (int i = 0; i < t.size(); i++) {
            if (i + 1 < t.size()) {
                String two = t.get(i) + "-" + t.get(i + 1);
                if (two.equals("united-kingdom")) return UK;
                if (two.equals("united-states")) return "united-states";
                if (two.equals("south-africa") || two.equals("new-zealand")) return two;
            }
            String w = t.get(i);
            // Two-letter words that are also English ("in", "it", "no", "be", "at") only as the group's first word.
            if (w.length() == 2 && i > 0 && !w.equals("uk") && !w.equals("us") && !w.equals("ie")) continue;
            String c = COUNTRY.get(w);
            if (c != null) return c;
        }
        return null;
    }

    /**
     * Pure: the countries' folders a playlist's logos come from: the United Kingdom's, Ireland's and the international
     * one always, then those its groups and its channels' tags name.
     */
    public static Set<String> countries(Iterable<String> groups, Iterable<String> names, int maxNames) {
        Set<String> out = new LinkedHashSet<>(Arrays.asList(UK, IE, INT));
        if (groups != null) for (String g : groups) {
            String c = countryOfGroup(g);
            if (c != null) out.add(c);
        }
        int n = 0;
        if (names != null) for (String name : names) {
            if (n++ >= maxNames) break;
            String c = leadingTag(name);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** Pure, quick: the country a name's first word tags it with ("UK: …", "|DE| …", "[US] …"), or null; no other work. */
    static String leadingTag(String name) {
        if (name == null) return null;
        int n = name.length(), i = 0;
        while (i < n && !Character.isLetter(name.charAt(i))) i++;
        int start = i;
        while (i < n && i - start < 12 && Character.isLetter(name.charAt(i))) i++;
        if (i == start || i >= n || Character.isLetterOrDigit(name.charAt(i)) || i - start > 3) return null;
        return COUNTRY.get(name.substring(start, i).toLowerCase(Locale.ROOT));
    }

    /** Lower-case words: "+1" as plus, "+2" as plus 2, "Sports+" as sport plus, & as and; hard: a mark (not a space) after it. */
    private static void tokens(String s, List<String> out, List<Boolean> hard) {
        StringBuilder cur = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '+') {
                flush(cur, out, hard, true);
                out.add("plus");
                hard.add(false);
                // "+1": the plus alone (the project's own way); "+24": plus, then the number.
                if (i + 1 < n && s.charAt(i + 1) == '1' && (i + 2 >= n || !Character.isLetterOrDigit(s.charAt(i + 2)))) i++;
            } else if (c == '&') {
                flush(cur, out, hard, false);
                out.add("and");
                hard.add(false);
            } else if (Character.isLetterOrDigit(c)) {
                cur.append(c);
            } else {
                flush(cur, out, hard, c != ' ' && c != '\t');
            }
        }
        flush(cur, out, hard, false);
    }

    private static void flush(StringBuilder cur, List<String> out, List<Boolean> hard, boolean mark) {
        if (cur.length() == 0) {
            if (mark && !hard.isEmpty()) hard.set(hard.size() - 1, true);
            return;
        }
        out.add(cur.toString());
        hard.add(mark);
        cur.setLength(0);
    }

    /** Words as both sides write them: One as 1, Sports as Sport, "Sports+" as sport plus. */
    private static List<String> words(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String w : in) {
            String m = WORDS.get(w);
            if (m == null && w.endsWith("plus") && w.length() > 4) {
                String base = w.substring(0, w.length() - 4), bm = WORDS.get(base);
                if (bm != null) {
                    out.add(bm);
                    out.add("plus");
                    continue;
                }
            }
            out.add(m != null ? m : w);
        }
        return out;
    }

    /** A timeshift: "plus" last, or "plus" and a number last ("+1", "+2"). */
    private static boolean timeshift(List<String> w) {
        int n = w.size();
        if (n >= 2 && w.get(n - 1).equals("plus")) return true;
        return n >= 3 && w.get(n - 2).equals("plus") && digits(w.get(n - 1));
    }

    private static String join(List<String> w) {
        StringBuilder b = new StringBuilder();
        for (String s : w) b.append(s);
        return b.toString();
    }

    /** "itv1" as itv and 1, for comparing words. */
    private static void splitDigits(String w, List<String> out) {
        int start = 0;
        for (int i = 1; i <= w.length(); i++) {
            boolean end = i == w.length();
            if (end || Character.isDigit(w.charAt(i)) != Character.isDigit(w.charAt(i - 1))) {
                out.add(w.substring(start, i));
                start = i;
            }
        }
    }

    private static boolean digits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private static boolean letters(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) < 'a' || s.charAt(i) > 'z') return false;
        return true;
    }

    // ---- matching ------------------------------------------------------------------------------------------------------

    /** The logo to show for a channel by itself: the best, when it is sure enough (SURE); else null. */
    public Found best(String channel, String group) {
        List<Found> c = find(name(channel, group), 1, false);
        return c.isEmpty() || c.get(0).sure < SURE ? null : c.get(0);
    }

    /** Every logo that may be the channel's, the best first, at most max (Change logo). */
    public List<Found> candidates(String channel, String group, int max) {
        return find(name(channel, group), max, true);
    }

    private List<Found> find(Name n, int max, boolean loose) {
        Map<String, Found> out = new LinkedHashMap<>();
        if (n.key.isEmpty()) return new ArrayList<>();
        named(n, n.key, 1f, out);
        // With its country's word kept: a name that ends in one ("92 News UK").
        if (n.full != null) named(n, n.full, 1f, out);
        if (n.generic != null) named(n, n.generic, 0.9f, out);
        if (n.region != null) named(n, n.region, 0.9f, out);
        // By most of its words: only offered (never sure enough to show by itself).
        if (loose) loose(n, out);
        List<Found> all = new ArrayList<>(out.values());
        all.sort((a, b) -> a.sure != b.sure ? Float.compare(b.sure, a.sure) : Integer.compare(b.score, a.score));
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    /** A key's alias first (it says which of two logos the family's channel is: "ITV" is ITV1), then the key itself. */
    private void named(Name n, String key, float factor, Map<String, Found> out) {
        String alias = ALIASES.get(key);
        if (alias != null) exact(n, alias, factor, out);
        exact(n, key, factor, out);
    }

    /** Every logo with this key, as sure as its country makes it (times factor). */
    private void exact(Name n, String key, float factor, Map<String, Found> out) {
        List<Logo> l = byKey.get(key);
        if (l == null) return;
        Set<String> folders = new HashSet<>();
        for (Logo g : l) folders.add(g.country);
        for (Logo g : l) {
            if (g.timeshift != n.timeshift || out.containsKey(g.path)) continue;
            out.put(g.path, new Found(g, sure(n.country, g.country, folders.size() == 1) * factor, score(g, n)));
        }
    }

    /** By most of its words (the numbers all the same, a timeshift only for a timeshift): never sure enough by itself. */
    private void loose(Name n, Map<String, Found> out) {
        Set<String> mine = new HashSet<>(n.tokens);
        Set<String> myNumbers = numbers(n.tokens);
        Map<Logo, Integer> hits = new HashMap<>();
        for (String t : mine) {
            if (t.equals("tv") || t.equals("channel") || t.equals("plus") || t.equals("and") || digits(t)) continue;
            List<Logo> p = byToken.get(t);
            if (p == null || p.size() > 400) continue;
            for (Logo g : p) {
                Integer had = hits.get(g);
                hits.put(g, had == null ? 1 : had + 1);
            }
        }
        for (Map.Entry<Logo, Integer> e : hits.entrySet()) {
            Logo g = e.getKey();
            if (out.containsKey(g.path) || g.timeshift != n.timeshift) continue;
            Set<String> theirs = new HashSet<>(Arrays.asList(g.tokens));
            if (!numbers(Arrays.asList(g.tokens)).equals(myNumbers)) continue;
            int same = 0;
            for (String t : theirs) if (mine.contains(t)) same++;
            float jaccard = same / (float) (mine.size() + theirs.size() - same);
            if (jaccard < 0.5f) continue;
            float s = Math.min(0.78f, (0.3f + 0.45f * jaccard) * sure(n.country, g.country, false));
            out.put(g.path, new Found(g, s, score(g, n)));
        }
    }

    private static Set<String> numbers(List<String> tokens) {
        Set<String> out = new HashSet<>();
        for (String t : tokens) if (digits(t)) out.add(t);
        return out;
    }

    /** Pure: how sure a logo in folder is for a channel from country (null: it doesn't say); alone: its key in that folder only. */
    static float sure(String country, String folder, boolean alone) {
        if (country == null) {
            if (folder.equals(UK)) return 1f;
            if (folder.equals(INT)) return 0.9f;
            if (folder.equals(IE)) return 0.85f;
            return alone ? 0.85f : 0.7f;
        }
        if (folder.equals(country)) return 1f;
        if (folder.equals(INT)) return 0.85f;
        if (country.equals(UK) && folder.equals(IE) || country.equals(IE) && folder.equals(UK)) return 0.85f;
        return 0.6f;
    }

    /** Pure: among equally sure logos, which comes first: the plain one, then its other versions. */
    static int score(Logo g, Name n) {
        int s = 100, v = g.variant;
        if ((v & UHD) != 0) s += n.uhd ? 8 : -30;
        if ((v & HD) != 0) s -= 5;
        if ((v & WHITE) != 0) s -= 4;
        if ((v & HZ) != 0) s -= 10;
        if ((v & ALT) != 0) s -= 15;
        if ((v & OTHER) != 0) s -= 25;
        if ((v & ICON) != 0) s -= 20;
        if ((v & MONO) != 0) s -= 20;
        if ((v & OLD) != 0) s -= 35;
        if ((v & LIGHT) != 0) s -= 40;
        return s;
    }

    /** A country's folder as the family reads it: "united-kingdom" → "United Kingdom". */
    static String countryName(String folder) {
        if (folder == null || folder.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String w : folder.split("-")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }
}
