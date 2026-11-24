package com.gathin.homefieldtv.subs;

import java.util.List;
import java.util.Locale;

/**
 * Languages as subtitles name them: OpenSubtitles' codes ("en", "pt-BR", "zh-CN"), a stream's ("eng", "ger"), and their
 * names in English for the player's lists ("Portuguese (Brazil)"). Pure.
 */
public final class Langs {
    private Langs() {}

    /** The languages offered in Settings › Other › Subtitles, as OpenSubtitles names them, English first. */
    public static final String[] CODES = {"en", "bn", "ar", "ur", "hi", "pa", "fr", "es", "de", "it", "pt-PT", "pt-BR",
            "nl", "pl", "ro", "tr", "el", "ru", "sv", "da", "no", "fi", "fa", "he", "id", "ms", "zh-CN", "ja", "ko", "so"};

    /** A language's name in English ("Bengali"), from its code ("bn", "pt-BR", "eng"); "" for none. */
    public static String name(String code) {
        if (code == null || code.isEmpty() || "und".equals(code)) return "";
        if ("pt-BR".equalsIgnoreCase(code) || "pb".equalsIgnoreCase(code)) return "Portuguese (Brazil)";
        if ("pt-PT".equalsIgnoreCase(code)) return "Portuguese";
        if ("zh-CN".equalsIgnoreCase(code)) return "Chinese";
        if ("zh-TW".equalsIgnoreCase(code)) return "Chinese (traditional)";
        String b = base(code);
        String n = new Locale(b).getDisplayLanguage(Locale.UK);
        return n.isEmpty() || n.equalsIgnoreCase(b) ? code : n;
    }

    /** "en" and "eng", "pt-BR" and "pt" are the same language. */
    public static boolean same(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return base(a).equals(base(b));
    }

    /** A language's place in the family's order (after all of them when not among them). */
    public static int rank(String lang, List<String> languages) {
        if (languages == null) return 0;
        for (int i = 0; i < languages.size(); i++) if (same(lang, languages.get(i))) return i;
        return languages.size();
    }

    /** A code's two letters ("eng" → "en", "pt-BR" → "pt", "ger" → "de"). */
    public static String base(String code) {
        String c = code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
        int dash = c.indexOf('-');
        if (dash < 0) dash = c.indexOf('_');
        if (dash > 0) c = c.substring(0, dash);
        if (c.length() != 3) return c;
        // The bibliographic codes Locale doesn't know, then the rest by their three letters.
        switch (c) {
            case "ger": return "de";
            case "fre": return "fr";
            case "dut": return "nl";
            case "chi": return "zh";
            case "per": return "fa";
            case "gre": return "el";
            case "rum": return "ro";
            case "cze": return "cs";
            case "slo": return "sk";
            case "alb": return "sq";
            case "arm": return "hy";
            case "baq": return "eu";
            case "bur": return "my";
            case "geo": return "ka";
            case "ice": return "is";
            case "mac": return "mk";
            case "mao": return "mi";
            case "may": return "ms";
            case "wel": return "cy";
            default: break;
        }
        for (String iso : Locale.getISOLanguages()) {
            try {
                if (new Locale(iso).getISO3Language().equals(c)) return iso;
            } catch (java.util.MissingResourceException ignored) {
                // not this one
            }
        }
        return c;
    }
}
