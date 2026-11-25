package com.gathin.homefieldtv.engine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Channel names made comparable, so a broadcaster in the sports listings ("Sky Sports Main Event", "TNT Sports 1")
 * finds its channel in the family's own playlist ("UK: SKY SPORTS MAIN EVENT FHD", "|UK| TNT Sport 1 ᴴᴰ"):
 * lower case, small caps and superscripts made plain, country tags at either end and quality tags (HD, FHD, UHD, 4K, SD,
 * RAW, HEVC…) dropped, punctuation and spaces gone, sports as sport, One as 1, and a few old names (BT Sport, Sky F1).
 * A timeshift (+1) stays apart. Streaming services in the listings (Prime Video, Netflix, NOW, DAZN…) are not channels.
 */
public final class Names {
    private Names() {}

    private static final Set<String> QUALITY = new HashSet<>(Arrays.asList("hd", "fhd", "uhd", "4k", "8k", "sd", "raw", "hevc",
            "h265", "h264", "x265", "1080", "1080p", "1080i", "720", "720p", "576p", "50fps", "60fps", "fps", "hdr", "hq", "lq",
            "vip", "multi", "multiaudio", "backup", "bk", "alt", "ott", "direct", "low", "high", "uhd4k", "ᴴᴰ"));
    private static final Set<String> BACKUP = new HashSet<>(Arrays.asList("backup", "bk", "alt", "low", "lq"));
    private static final Set<String> COUNTRY = new HashSet<>(Arrays.asList("uk", "gb", "eng", "england", "ie", "irl", "us",
            "usa", "ca", "au", "nz", "de", "fr", "es", "nl", "pt", "be", "it", "pl", "tr", "ar", "in", "pk", "bd"));
    private static final Set<String> UK = new HashSet<>(Arrays.asList("uk", "gb", "eng", "england"));
    private static final Map<String, String> WORDS = new HashMap<>();
    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Set<String> SERVICES = new HashSet<>(Arrays.asList("amazonprimevideo", "primevideo", "amazonprime",
            "amazon", "netflix", "disney", "disneyplus", "appletv", "appletvplus", "paramount", "paramountplus", "now", "nowtv",
            "discoveryplus", "youtube", "dazn", "peacock", "max", "itvx", "bbciplayer", "iplayer", "channel4streaming", "my5",
            "uefatv", "fifaplus", "f1tv", "nbaleaguepass", "tntsportapp", "skygo"));
    private static final String[] SKY_SPORT = {"mainevent", "premierleague", "football", "cricket", "golf", "f1", "arena",
            "action", "racing", "tennis"};
    /** First words that say little on their own ("Channel 4", "The Sun TV"): the family keeps the next word too. */
    private static final Set<String> GENERIC_FIRST = new HashSet<>(Arrays.asList("channel", "the", "tv", "canal", "radio"));
    private static final Map<String, String> FAMILY_ALIASES = new HashMap<>();

    static {
        WORDS.put("one", "1");
        WORDS.put("two", "2");
        WORDS.put("three", "3");
        WORDS.put("four", "4");
        WORDS.put("five", "5");
        WORDS.put("sports", "sport");
        WORDS.put("&", "and");
        WORDS.put("+1", "plus1");
        for (int i = 1; i <= 8; i++) ALIASES.put("btsport" + i, "tntsport" + i);
        ALIASES.put("btsportultimate", "tntsportultimate");
        ALIASES.put("btsportboxoffice", "tntsportboxoffice");
        ALIASES.put("itv", "itv1");
        ALIASES.put("c4", "channel4");
        ALIASES.put("ch4", "channel4");
        ALIASES.put("ch5", "channel5");
        ALIASES.put("five", "channel5");
        ALIASES.put("5", "channel5");
        FAMILY_ALIASES.put("btsport", "tntsport");
        FAMILY_ALIASES.put("skymovies", "skycinema");
        FAMILY_ALIASES.put("skymovie", "skycinema");
    }

    /**
     * What a name says besides its words: tagged British, its quality (0 SD … 4 UHD), a backup feed, a +1, and its
     * family (the brand it belongs to: "skysport" for every Sky Sports channel, "bbc" for BBC One and BBC Two).
     */
    public static final class Info {
        public final String key;
        public final boolean uk;
        public final int quality;
        public final boolean backup;
        public final String family;

        Info(String key, boolean uk, int quality, boolean backup, String family) {
            this.key = key;
            this.uk = uk;
            this.quality = quality;
            this.backup = backup;
            this.family = family;
        }
    }

    /** The name's comparable key ("" when nothing is left). */
    public static String key(String name) {
        return info(name, "").key;
    }

    /**
     * A word every channel whose name reads as this one most likely has in its plain, lower-case name (the name's longest
     * word that Names keeps as it is: no quality or country tag, no number word, three letters or more), or null when
     * it has none. Lets a name be looked for by reading only the names with that word in them.
     */
    public static String hint(String name) {
        String best = null;
        for (String t : tokens(name)) {
            // "Disney+" is read as "disneyplus": its name has "disney".
            if (t.endsWith("plus") && t.length() > 4) t = t.substring(0, t.length() - 4);
            if (t.length() < 3 || QUALITY.contains(t) || COUNTRY.contains(t) || WORDS.containsKey(t)) continue;
            if (best == null || t.length() > best.length()) best = t;
        }
        return best;
    }

    /** A 4K or UHD channel ("Sky Sports Main Event UHD", "UK: SKY SPORTS F1 4K"). */
    public static boolean uhd(String name) {
        return info(name, null).quality >= 4;
    }

    public static Info info(String name, String group) {
        List<String> all = tokens(name);
        boolean uk = false, backup = false;
        int quality = 1;
        // Quality tags first, wherever they are, so a country tag before one still counts as at the end.
        List<String> tokens = new ArrayList<>(all.size());
        for (String t : all) {
            if (QUALITY.contains(t)) {
                quality = Math.max(quality, quality(t));
                if (t.equals("sd")) quality = Math.min(quality, 0);
                backup |= BACKUP.contains(t);
            } else tokens.add(t);
        }
        // Country tags at either end.
        while (!tokens.isEmpty() && COUNTRY.contains(tokens.get(0))) uk |= UK.contains(tokens.remove(0));
        while (!tokens.isEmpty() && COUNTRY.contains(tokens.get(tokens.size() - 1))) uk |= UK.contains(tokens.remove(tokens.size() - 1));
        StringBuilder key = new StringBuilder();
        for (String t : tokens) {
            String w = WORDS.get(t);
            if (w == null && t.endsWith("plus") && t.length() > 4) {
                // "Sports+" as "Sports Plus".
                String base = t.substring(0, t.length() - 4);
                String bw = WORDS.get(base);
                if (bw != null) w = bw + "plus";
            }
            key.append(w != null ? w : t);
        }
        if (group != null) {
            List<String> g = tokens(group);
            for (String t : g) if (UK.contains(t) || t.equals("british") || t.equals("unitedkingdom")) uk = true;
            String gl = group.toLowerCase(Locale.ROOT);
            if (gl.contains("united kingdom")) uk = true;
        }
        String k = key.toString();
        String alias = ALIASES.get(k);
        if (alias != null) k = alias;
        if (k.startsWith("sky") && !k.startsWith("skysport")) {
            String rest = k.substring(3);
            for (String s : SKY_SPORT) if (rest.equals(s)) { k = "skysport" + rest; break; }
        }
        return new Info(k, uk, quality, backup, family(tokens));
    }

    /** A name's family from its words (tags already set aside): the brand, with the kind for sports and films. */
    static String family(List<String> tokens) {
        if (tokens.isEmpty()) return "";
        String a = word(tokens.get(0));
        if (tokens.size() > 1) {
            String b = word(tokens.get(1));
            if (GENERIC_FIRST.contains(a)) return alias(a + b);
            if (b.startsWith("sport")) return alias(a + "sport");
            if (b.startsWith("cinema")) return alias(a + "cinema");
            if (b.startsWith("movie")) return alias(a + "movies");
            if (a.equals("sky")) for (String s : SKY_SPORT) if (b.equals(s)) return "skysport";
        }
        // "ITV2", "BBC1": the brand without its number (but "E4" stays itself).
        int end = a.length();
        while (end > 0 && Character.isDigit(a.charAt(end - 1))) end--;
        return alias(end >= 2 ? a.substring(0, end) : a);
    }

    private static String word(String t) {
        String w = WORDS.get(t);
        return w != null ? w : t;
    }

    private static String alias(String family) {
        String a = FAMILY_ALIASES.get(family);
        return a != null ? a : family;
    }

    /** A streaming service rather than a channel (the listings say "Amazon Prime Video" for a game only there). */
    public static boolean isService(String name) {
        String k = key(name);
        return k.isEmpty() || SERVICES.contains(k);
    }

    /** The parts of a listing that names several channels ("Sky Sports Main Event / Sky Sports Premier League"). */
    public static List<String> parts(String listing) {
        List<String> out = new ArrayList<>();
        if (listing == null) return out;
        for (String p : listing.split("[/,;]| or ")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static int quality(String t) {
        switch (t) {
            case "4k": case "8k": case "uhd": case "uhd4k": case "hdr": return 4;
            case "fhd": case "1080": case "1080p": case "1080i": case "raw": return 3;
            case "hd": case "720": case "720p": case "ᴴᴰ": case "hq": return 2;
            case "sd": case "576p": case "lq": case "low": return 0;
            default: return 1;
        }
    }

    /** Lower-case words: small caps and superscripts made plain, accents dropped, "+1" kept, other punctuation a gap. */
    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        String t = plain(s).toLowerCase(Locale.ROOT);
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '+' && i + 1 < t.length() && t.charAt(i + 1) == '1' && (i + 2 >= t.length() || !Character.isLetterOrDigit(t.charAt(i + 2)))) {
                flush(cur, out);
                out.add("+1");
                i++;
            } else if (c == '&') {
                flush(cur, out);
                out.add("&");
            } else if (c == '+') {
                // Disney+, Apple TV+: a "plus" of their own.
                flush(cur, out);
                if (!out.isEmpty()) out.set(out.size() - 1, out.get(out.size() - 1) + "plus");
            } else if (Character.isLetterOrDigit(c)) {
                cur.append(c);
            } else {
                flush(cur, out);
            }
        }
        flush(cur, out);
        return out;
    }

    private static void flush(StringBuilder cur, List<String> out) {
        if (cur.length() > 0) {
            out.add(cur.toString());
            cur.setLength(0);
        }
    }

    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀꜱᴛᴜᴠᴡʏᴢ";
    private static final String SMALL_TO = "abcdefghijklmnoprstuvwyz";

    /** Small capitals and modifier letters as plain letters; accents gone. */
    static String plain(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int at = SMALL.indexOf(c);
            b.append(at >= 0 ? SMALL_TO.charAt(at) : c);
        }
        String n = Normalizer.normalize(b, Normalizer.Form.NFKD);
        StringBuilder out = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) out.append(c);
        }
        return out.toString();
    }
}
