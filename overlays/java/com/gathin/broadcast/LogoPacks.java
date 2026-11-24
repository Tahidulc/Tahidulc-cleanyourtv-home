package com.gathin.broadcast;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The logo catalogue: free, openly shared channel logo packs, searched by a channel's name. Each pack's index is read
 * once a week with the apps' generic user agent (never on the main thread), kept small in the app's cache (a line a
 * logo: its name, its country, its address), and searched here; the pictures themselves go through each app's own
 * picture cache. The packs:
 *
 * <ul>
 *   <li>TV Logos (github.com/tv-logo/tv-logos), the logos the apps already use: "free high quality TV logos for dark
 *   backgrounds", by Tapio Sinnertwin; its file list from GitHub's tree of the repository.</li>
 *   <li>iptv-org's database (github.com/iptv-org/database, The Unlicense): each channel's logos as the project lists
 *   them, from iptv-org.github.io/api/logos.json (SVGs left out: the apps draw pictures, not vectors).</li>
 * </ul>
 *
 * The logos remain their channels' own marks; LICENCES says so for the licences page.
 */
public final class LogoPacks {
    private LogoPacks() {
    }

    public enum Pack {
        TV_LOGOS("TV Logos", "tv-logo/tv-logos on GitHub",
                "https://api.github.com/repos/tv-logo/tv-logos/git/trees/main?recursive=1",
                "https://raw.githubusercontent.com/tv-logo/tv-logos/main/"),
        IPTV_ORG("iptv-org", "the iptv-org database (The Unlicense)", "https://iptv-org.github.io/api/logos.json", "");

        public final String label, credit, index, base;

        Pack(String label, String credit, String index, String base) {
            this.label = label;
            this.credit = credit;
            this.index = index;
            this.base = base;
        }
    }

    /** One logo in a pack: its channel's name as the pack has it, its country ("" not known) and its picture's address. */
    public static final class Logo {
        public final Pack pack;
        public final String name, country, url;
        /** Its name's words as the search reads them, worked out once. */
        final String[] words;
        final String joined;

        public Logo(Pack pack, String name, String country, String url) {
            this.pack = pack;
            this.name = name;
            this.country = country == null ? "" : country;
            this.url = url;
            this.words = fast(name);
            this.joined = String.join("", words);
        }
    }

    /** For the licences page: each pack, what it is and its terms, and that the logos are their channels' own. */
    public static final String LICENCES = "Channel logo catalogue\n\n"
            + "TV Logos (github.com/tv-logo/tv-logos) by Tapio Sinnertwin: free high quality TV logos for dark backgrounds, "
            + "shared freely by its author for use like this.\n\n"
            + "iptv-org database (github.com/iptv-org/database and iptv-org.github.io/api): released under The Unlicense "
            + "(public domain). The logos it lists are hosted by their channels and others.\n\n"
            + "Every channel logo remains the trademark of its channel. The apps show them only to mark the family's own "
            + "channels, at home, and never change or sell them.\n\n"
            + "Logos made in the app (Make my own, and Ask AI with Claude by Anthropic, with the family's own key) are drawn "
            + "by the app from a design: the bundled typefaces are under the SIL Open Font Licence 1.1.";

    /** At most this much of an index is read. */
    static final int INDEX_MAX = 24 << 20;
    /** An index is read again after a week. */
    public static final long FRESH_MS = 7L * 24 * 3600_000L;

    // ---- reading the packs' indexes ---------------------------------------------------------------------------------

    /** Pure: TV Logos' entries from GitHub's tree of the repository (its PNGs under countries/). */
    static List<Logo> parseTvLogos(String tree) {
        List<Logo> out = new ArrayList<>();
        FlatJson.objects(tree, o -> {
            String path = o.get("path");
            if (path == null || !"blob".equals(o.get("type"))) return;
            String low = path.toLowerCase(Locale.ROOT);
            if (!low.startsWith("countries/") || !low.endsWith(".png") || low.contains("mosaic")) return;
            String[] parts = path.split("/");
            if (parts.length < 3) return;
            String country = parts[1];
            String file = parts[parts.length - 1];
            out.add(new Logo(Pack.TV_LOGOS, tvLogosName(file), country, Pack.TV_LOGOS.base + encodePath(path)));
        });
        return out;
    }

    /** "sky-sports-main-event-uk.png" to "Sky Sports Main Event" (its country code at the end left off). */
    static String tvLogosName(String file) {
        String b = file.replaceAll("(?i)\\.png$", "");
        String[] w = b.split("[-_]+");
        int n = w.length;
        if (n > 1 && w[n - 1].length() <= 3 && w[n - 1].matches("[A-Za-z]+")) n--;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (w[i].isEmpty()) continue;
            if (s.length() > 0) s.append(' ');
            s.append(Character.toUpperCase(w[i].charAt(0))).append(w[i].substring(1));
        }
        return s.toString();
    }

    private static String encodePath(String path) {
        StringBuilder b = new StringBuilder();
        for (char ch : path.toCharArray()) {
            if (ch == ' ') b.append("%20");
            else if (ch == '+') b.append("%2B");
            else if (ch == '&') b.append("%26");
            else b.append(ch);
        }
        return b.toString();
    }

    /** Pure: iptv-org's entries from its logos.json (an SVG or an address that is not https left out). */
    static List<Logo> parseIptvOrg(String json) {
        List<Logo> out = new ArrayList<>();
        FlatJson.objects(json, o -> {
            String ch = o.get("channel"), url = o.get("url"), format = o.get("format");
            if (ch == null || url == null || !url.startsWith("https://")) return;
            if ("SVG".equalsIgnoreCase(format) || url.toLowerCase(Locale.ROOT).endsWith(".svg")) return;
            int dot = ch.lastIndexOf('.');
            String id = dot > 0 ? ch.substring(0, dot) : ch, country = dot > 0 ? ch.substring(dot + 1) : "";
            out.add(new Logo(Pack.IPTV_ORG, iptvName(id), country, url));
        });
        return out;
    }

    /** "SkySportsPremierLeague" to "Sky Sports Premier League"; "BBCOne" to "BBC One"; "Channel4" to "Channel 4". */
    static String iptvName(String id) {
        String s = id.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2").replaceAll("([0-9])([A-Za-z])", "$1 $2");
        return s.trim().replaceAll("\\s+", " ");
    }

    /** Pure: the cache's lines (name, country, address; tabs and line ends taken out of each). */
    static String toLines(List<Logo> logos) {
        StringBuilder b = new StringBuilder(logos.size() * 80);
        for (Logo l : logos) {
            b.append(clean(l.name)).append('\t').append(clean(l.country)).append('\t').append(clean(l.url)).append('\n');
        }
        return b.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    static List<Logo> fromLines(String text, Pack pack) {
        List<Logo> out = new ArrayList<>();
        if (text == null) return out;
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            String line = text.substring(start, end);
            start = end + 1;
            int a = line.indexOf('\t'), b = a < 0 ? -1 : line.indexOf('\t', a + 1);
            if (a <= 0 || b < 0) continue;
            String url = line.substring(b + 1);
            if (!url.startsWith("https://")) continue;
            out.add(new Logo(pack, line.substring(0, a), line.substring(a + 1, b), url));
        }
        return out;
    }

    /**
     * A pack's index (blocking: never the main thread): the cache in dir while it is under a week old, else read
     * again (with the generic user agent) and kept; the old cache when the pack can't be reached.
     */
    public static List<Logo> index(File dir, Pack pack) throws IOException {
        File f = new File(dir, "logo-pack-" + pack.name().toLowerCase(Locale.ROOT) + ".txt");
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < FRESH_MS) {
            return fromLines(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), pack);
        }
        try {
            String raw = new String(Net.get(pack.index, Net.UA, 15_000, 60_000, INDEX_MAX), StandardCharsets.UTF_8);
            List<Logo> logos = pack == Pack.TV_LOGOS ? parseTvLogos(raw) : parseIptvOrg(raw);
            if (!logos.isEmpty()) {
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("no cache folder");
                File tmp = new File(dir, f.getName() + ".tmp");
                Files.write(tmp.toPath(), toLines(logos).getBytes(StandardCharsets.UTF_8));
                if (!tmp.renameTo(f)) Files.write(f.toPath(), toLines(logos).getBytes(StandardCharsets.UTF_8));
            }
            return logos;
        } catch (IOException e) {
            if (f.exists()) return fromLines(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8), pack);
            throw e;
        }
    }

    // ---- searching ----------------------------------------------------------------------------------------------------

    /** A name as the search reads it: lower case words, & as "and", + as "plus", the playlists' tags left out. */
    static List<String> words(String s) {
        String t = LogoDesigner.plain(s == null ? "" : s).toLowerCase(Locale.ROOT).replace("&", " and ").replace("+", " plus ");
        t = t.replaceAll("[^a-z0-9 ]+", " ").trim();
        List<String> out = new ArrayList<>();
        for (String w : t.split("\\s+")) {
            if (w.isEmpty() || (w.equals("tv") || w.equals("hd") || w.equals("channel")) && t.contains(" ")) continue;
            out.add(w);
        }
        return out;
    }

    /** A pack's name in words, quickly (no patterns: thousands of them are read): lower case letters and digits. */
    static String[] fast(String name) {
        List<String> out = new ArrayList<>(6);
        StringBuilder b = new StringBuilder();
        String s = name == null ? "" : name;
        for (int i = 0; i <= s.length(); i++) {
            char ch = i < s.length() ? Character.toLowerCase(s.charAt(i)) : ' ';
            if (ch == '&') {
                if (b.length() > 0) out.add(b.toString());
                b.setLength(0);
                out.add("and");
            } else if (ch == '+') {
                if (b.length() > 0) out.add(b.toString());
                b.setLength(0);
                out.add("plus");
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                b.append(ch);
            } else if (b.length() > 0) {
                String w = b.toString();
                b.setLength(0);
                if (!w.equals("tv") && !w.equals("hd") && !w.equals("channel")) out.add(w);
            }
        }
        if (out.isEmpty() && s.toLowerCase(Locale.ROOT).contains("tv")) out.add("tv");
        return out.toArray(new String[0]);
    }

    /** Pure: how well a logo's name answers the search (0: not at all). */
    static int score(List<String> want, String name) {
        return score(want, new Logo(Pack.TV_LOGOS, name, "", "https://x"));
    }

    static int score(List<String> want, Logo l) {
        if (want.isEmpty()) return 0;
        List<String> have = java.util.Arrays.asList(l.words);
        if (have.isEmpty()) return 0;
        String joinedHave = l.joined, joinedWant = String.join("", want);
        int hit = 0, s = 0;
        for (String w : want) {
            boolean exact = have.contains(w), prefix = false;
            if (!exact) for (String h : have) if (h.startsWith(w) && w.length() >= 2) prefix = true;
            if (exact) { hit++; s += 10; } else if (prefix) { hit++; s += 6; }
        }
        boolean joined = joinedHave.contains(joinedWant);
        if (joined) s += 8;
        int need = want.size() <= 2 ? want.size() : want.size() - 1;
        if (hit < need && !joined) return 0;
        if (joinedHave.equals(joinedWant)) s += 20;
        s -= Math.max(0, have.size() - want.size()) * 2;
        return Math.max(1, s);
    }

    /** Pure: the logos answering a search, best first, grouped by pack (at most perPack each), packs in order. */
    public static Map<Pack, List<Logo>> search(Map<Pack, List<Logo>> packs, String query, int perPack) {
        List<String> want = words(query);
        Map<Pack, List<Logo>> out = new LinkedHashMap<>();
        for (Map.Entry<Pack, List<Logo>> e : packs.entrySet()) {
            List<int[]> ranked = new ArrayList<>();
            List<Logo> all = e.getValue();
            for (int i = 0; i < all.size(); i++) {
                int sc = score(want, all.get(i));
                if (sc > 0) ranked.add(new int[]{sc, i});
            }
            Collections.sort(ranked, (a, b) -> a[0] != b[0] ? Integer.compare(b[0], a[0]) : Integer.compare(a[1], b[1]));
            List<Logo> best = new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (int[] r : ranked) {
                Logo l = all.get(r[1]);
                if (seen.contains(l.url)) continue;
                seen.add(l.url);
                best.add(l);
                if (best.size() >= perPack) break;
            }
            out.put(e.getKey(), best);
        }
        return out;
    }

    // ---- a small streaming reader for the packs' big JSON -------------------------------------------------------------

    /** Pure: every JSON object's plain values (strings, numbers, true, false; null as absent), handed over in turn. */
    static final class FlatJson {
        private FlatJson() {
        }

        interface Each {
            void object(Map<String, String> values);
        }

        static void objects(String s, Each each) {
            if (s == null) return;
            int n = s.length();
            List<Map<String, String>> stack = new ArrayList<>();
            List<String> pendingKey = new ArrayList<>();
            List<Boolean> isObject = new ArrayList<>();
            String key = null;
            boolean expectKey = false;
            int i = 0;
            while (i < n) {
                char ch = s.charAt(i);
                if (ch == '{') {
                    stack.add(new LinkedHashMap<>());
                    isObject.add(true);
                    pendingKey.add(key);
                    key = null;
                    expectKey = true;
                    i++;
                } else if (ch == '[') {
                    stack.add(null);
                    isObject.add(false);
                    pendingKey.add(key);
                    key = null;
                    expectKey = false;
                    i++;
                } else if (ch == '}' || ch == ']') {
                    if (!stack.isEmpty()) {
                        Map<String, String> m = stack.remove(stack.size() - 1);
                        isObject.remove(isObject.size() - 1);
                        pendingKey.remove(pendingKey.size() - 1);
                        if (m != null && !m.isEmpty()) each.object(m);
                    }
                    key = null;
                    expectKey = !isObject.isEmpty() && isObject.get(isObject.size() - 1);
                    i++;
                } else if (ch == '"') {
                    StringBuilder b = new StringBuilder();
                    i++;
                    while (i < n) {
                        char c = s.charAt(i++);
                        if (c == '"') break;
                        if (c == '\\' && i < n) {
                            char e = s.charAt(i++);
                            switch (e) {
                                case 'n': b.append('\n'); break;
                                case 't': b.append('\t'); break;
                                case 'r': b.append('\r'); break;
                                case 'b': b.append('\b'); break;
                                case 'f': b.append('\f'); break;
                                case 'u':
                                    if (i + 4 <= n) {
                                        try {
                                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                        } catch (NumberFormatException x) {
                                            b.append('?');
                                        }
                                        i += 4;
                                    }
                                    break;
                                default: b.append(e);
                            }
                        } else {
                            b.append(c);
                        }
                    }
                    boolean inObject = !isObject.isEmpty() && isObject.get(isObject.size() - 1);
                    if (inObject && expectKey) {
                        key = b.toString();
                        expectKey = false;
                    } else {
                        if (inObject && key != null) stack.get(stack.size() - 1).put(key, b.toString());
                        key = null;
                    }
                } else if (ch == ',') {
                    expectKey = !isObject.isEmpty() && isObject.get(isObject.size() - 1);
                    key = null;
                    i++;
                } else if (ch == ':' || Character.isWhitespace(ch)) {
                    i++;
                } else {
                    int start = i;
                    while (i < n && ",}] \n\r\t".indexOf(s.charAt(i)) < 0) i++;
                    String v = s.substring(start, i);
                    boolean inObject = !isObject.isEmpty() && isObject.get(isObject.size() - 1);
                    if (inObject && key != null && !"null".equals(v)) stack.get(stack.size() - 1).put(key, v);
                    key = null;
                }
            }
        }
    }
}
