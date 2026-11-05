package com.gathin.tvhomephone.live;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The guide's dark look (GuideGrid, the chips): black ground, each channel a rounded tile in its logo's colour, each
 * programme a rounded block in a deep colour of its own, what has been shown a shade darker, the channel's name with
 * its picture's tag (HD, FHD, 4K, UHD) and the times over the titles, and the categories as dark pills with a flag
 * where they name a country. The choices of colour and words are pure, for the tests.
 */
final class GuideLook {
    private GuideLook() {}

    /** The ground, the pills and their rim, the pill chosen, the writing over the titles, and the past's shade. */
    static final int GROUND = 0xFF000000, PILL = 0xFF1C1C1E, PILL_RIM = 0xFF2C2C2E, PILL_ON = 0xFF3A3A3C,
            SMALL = 0xFFA1A1AA, RULER = 0xFF8E8E93, PAST = 0x59000000, PANEL = 0xFF121214;

    /** The programmes' deep colours: green, maroon, purple, olive, navy, brown, teal, plum. */
    static final int[] BLOCKS = {0xFF213B32, 0xFF4A2323, 0xFF3B2550, 0xFF45421F, 0xFF233552, 0xFF4E3220, 0xFF1E3F4A, 0xFF4A2340};
    /** A channel without a logo: its tile in one of these, by its name. */
    static final int[] TILES = {0xFF4D7CFE, 0xFFE8742A, 0xFF2E9D57, 0xFF8A5CF6, 0xFF1F9AA8, 0xFFC0392B, 0xFFB7950B, 0xFF6D7B8D};
    static final int LIGHT_GREY = 0xFFD1D1D6, DARK_GREY = 0xFF2C2C2E;

    /** Pure: a programme's colour, the same for the same title. */
    static int blockColour(String title) {
        int h = title == null ? 0 : title.trim().toLowerCase(Locale.ROOT).hashCode();
        return BLOCKS[Math.floorMod(h, BLOCKS.length)];
    }

    /** Pure: a logo-less channel's tile colour, by its name. */
    static int nameColour(String name) {
        int h = name == null ? 0 : name.trim().toLowerCase(Locale.ROOT).hashCode();
        return TILES[Math.floorMod(h, TILES.length)];
    }

    /**
     * Pure: the tile behind a logo, from its pixels (ARGB, w by h, a small copy of it): the logo's own ground where it
     * has one (its four corners solid and alike), so the logo sits in its own colour; otherwise a pale tint of its main
     * colour behind a dark or coloured logo, a dark one behind a white logo, so it always shows.
     */
    static int tile(int[] px, int w, int h) {
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return LIGHT_GREY;
        int[] corners = {px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1]};
        boolean solid = true;
        for (int c : corners) solid &= (c >>> 24) > 230;
        if (solid && near(corners)) return 0xFF000000 | average(corners);
        // The logo's own pixels: how light they are, and the main colour among the coloured ones.
        long lum = 0;
        int n = 0;
        float[] hueWeight = new float[12];
        for (int c : px) {
            if ((c >>> 24) < 160) continue;
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            lum += (r * 299 + g * 587 + b * 114) / 1000;
            n++;
            int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            if (max < 50 || max - min < 60) continue;
            hueWeight[(int) (hue(r, g, b) / 30f) % 12] += (max - min) / 255f;
        }
        if (n == 0) return LIGHT_GREY;
        boolean light = lum / n > 170;
        int best = -1;
        float bw = 0;
        for (int i = 0; i < 12; i++) if (hueWeight[i] > bw) { bw = hueWeight[i]; best = i; }
        int base = light ? DARK_GREY : LIGHT_GREY;
        if (best < 0 || bw < n * 0.05f) return base;
        int hueColour = fromHue(best * 30 + 15);
        return blend(hueColour, base, light ? 0.45f : 0.30f);
    }

    private static boolean near(int[] cs) {
        for (int a : cs) for (int b : cs) if (distance(a, b) > 40) return false;
        return true;
    }

    private static int distance(int a, int b) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
    }

    private static int average(int[] cs) {
        int r = 0, g = 0, b = 0;
        for (int c : cs) { r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; }
        return (r / cs.length) << 16 | (g / cs.length) << 8 | (b / cs.length);
    }

    private static float hue(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max == min) return 0;
        float d = max - min, h;
        if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h *= 60f;
        return h < 0 ? h + 360f : h;
    }

    /** A strong colour of this hue (saturation 0.75, value 0.85). */
    private static int fromHue(float h) {
        float s = 0.75f, v = 0.85f, c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; } else if (h < 120) { r = x; g = c; b = 0; } else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; } else if (h < 300) { r = x; g = 0; b = c; } else { r = c; g = 0; b = x; }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    /** Pure: a mixed with b, share of a. */
    static int blend(int a, int b, float share) {
        int r = Math.round(((a >> 16) & 0xFF) * share + ((b >> 16) & 0xFF) * (1 - share));
        int g = Math.round(((a >> 8) & 0xFF) * share + ((b >> 8) & 0xFF) * (1 - share));
        int bl = Math.round((a & 0xFF) * share + (b & 0xFF) * (1 - share));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** Pure: the channel's picture's tag from its name ("4K", "UHD", "FHD", "HD"), or "" when it says none. */
    static String quality(String name) {
        if (name == null) return "";
        List<String> t = Names.tokens(name);
        String found = "";
        int rank = 0;
        for (String s : t) {
            String q = null;
            int r = 0;
            switch (s) {
                case "uhd": case "uhd4k": q = "UHD"; r = 4; break;
                case "4k": case "8k": q = s.toUpperCase(Locale.ROOT); r = 4; break;
                case "fhd": case "1080p": case "1080": q = "FHD"; r = 3; break;
                case "hd": case "720p": q = "HD"; r = 2; break;
                default: break;
            }
            if (q != null && r > rank) { rank = r; found = q; }
        }
        return found;
    }

    /** Pure: the name as the guide writes it over the title: its quality tag and country tag left out, in capitals. */
    static String shortName(String name) {
        if (name == null) return "";
        String s = name.trim().replaceAll("^\\s*(\\|[^|]*\\||[A-Za-z]{2,3}\\s*[:|]\\s*)", "");
        s = s.replaceAll("(?i)\\s*\\b(f?hd|uhd|4k|8k|sd|hevc|1080p?|720p?|ᴴᴰ)\\b\\s*", " ").trim();
        return s.toUpperCase(Locale.UK);
    }

    private static final Map<String, String> COUNTRIES = new HashMap<>();
    static {
        String[][] all = {
                {"GB", "uk", "gb", "england", "british", "unitedkingdom", "scotland", "wales"},
                {"US", "us", "usa", "america", "unitedstates"}, {"CA", "ca", "canada"}, {"DE", "de", "germany", "deutschland", "german"},
                {"ES", "es", "spain", "espana", "spanish"}, {"FR", "fr", "france", "french"}, {"IT", "it", "italy", "italia", "italian"},
                {"PT", "pt", "portugal", "portuguese"}, {"NL", "nl", "netherlands", "holland", "dutch"}, {"BE", "be", "belgium"},
                {"IE", "ie", "ireland", "irish"}, {"IN", "in", "india", "indian", "hindi"}, {"BD", "bd", "bangladesh", "bangla", "bengali"},
                {"PK", "pk", "pakistan", "urdu"}, {"TR", "tr", "turkey", "turkiye", "turkish"}, {"SA", "sa", "saudi", "arabic", "arab"},
                {"AE", "ae", "uae", "emirates"}, {"PL", "pl", "poland", "polish"}, {"SE", "se", "sweden", "swedish"},
                {"NO", "no", "norway", "norwegian"}, {"DK", "dk", "denmark", "danish"}, {"FI", "fi", "finland"},
                {"GR", "gr", "greece", "greek"}, {"CH", "ch", "switzerland", "swiss"}, {"AT", "at", "austria"},
                {"AU", "au", "australia"}, {"NZ", "nz", "newzealand"}, {"ZA", "za", "southafrica"}, {"BR", "br", "brazil", "brasil"},
                {"MX", "mx", "mexico"}, {"AR", "argentina"}, {"RO", "ro", "romania"}, {"AL", "al", "albania"}, {"NG", "ng", "nigeria"},
                {"JP", "jp", "japan"}, {"KR", "kr", "korea"}, {"CN", "cn", "china"}, {"PH", "ph", "philippines"}, {"RU", "ru", "russia"},
                {"UA", "ua", "ukraine"}, {"HR", "hr", "croatia"}, {"RS", "rs", "serbia"}, {"HU", "hu", "hungary"}, {"CZ", "cz", "czech"},
                {"IL", "il", "israel"}, {"EG", "eg", "egypt"}, {"MA", "ma", "morocco"}, {"LK", "lk", "srilanka"}, {"NP", "np", "nepal"},
                {"AF", "af", "afghanistan"}, {"IR", "ir", "iran", "persian"}, {"KU", "kurdish"}, {"SO", "so", "somalia"}};
        for (String[] row : all) for (int i = 1; i < row.length; i++) COUNTRIES.put(row[i], row[0]);
    }

    /**
     * Pure: the flag of the country a category names ("UK | Sports", "DE: Movies", "Canada", "Bangla"), or "": a
     * two-letter tag only at the start and in capitals (so "IT Crowd" or "in" is no country), a country's name anywhere.
     */
    static String flag(String group) {
        if (group == null || group.trim().isEmpty()) return "";
        String g = group.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\|?\\s*([A-Z]{2,3})\\b").matcher(g);
        if (m.find()) {
            String iso = COUNTRIES.get(m.group(1).toLowerCase(Locale.ROOT));
            if (iso != null && !"KU".equals(iso)) return emoji(iso);
        }
        for (String w : g.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (w.length() < 4) continue;
            String iso = COUNTRIES.get(w);
            if (iso != null && !"KU".equals(iso)) return emoji(iso);
        }
        // Two words run together ("United Kingdom", "New Zealand").
        String joined = g.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        for (String key : new String[]{"unitedkingdom", "unitedstates", "newzealand", "southafrica", "srilanka"}) {
            if (joined.contains(key)) return emoji(COUNTRIES.get(key));
        }
        return "";
    }

    /** Pure: a country's flag from its two letters (the regional indicator pair). */
    static String emoji(String iso) {
        if (iso == null || iso.length() != 2) return "";
        int a = 0x1F1E6 + (iso.charAt(0) - 'A'), b = 0x1F1E6 + (iso.charAt(1) - 'A');
        return new String(Character.toChars(a)) + new String(Character.toChars(b));
    }

    /** Pure: a category's chip words: its flag before its name where it has one. */
    static String chip(String group, String words) {
        String f = flag(group);
        return f.isEmpty() ? words : f + "  " + words;
    }
}
