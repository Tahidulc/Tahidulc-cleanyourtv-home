package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a fan's post must be to go on the family's TV: words (not a bare link or picture), in English, nothing marked
 * as adult or graphic by its site, and none of the words below, however spelled round (f*ck, sh1t). Its text is tidied
 * first: the site's HTML taken out, its entities read, its spaces closed up, cut to MAX letters with an ellipsis.
 * Pure, for the tests.
 */
final class FanFilter {
    private FanFilter() { }

    /** The most of a post shown. */
    static final int MAX = 140;

    /** Words never shown (whole words, after the look-alikes are read back as letters). Swearing and slurs. */
    private static final String[] BLOCKED = {
            "fuck", "fucking", "fucked", "fucker", "fuckers", "fucks", "motherfucker", "fck", "fcking", "fuk", "fuking", "effing",
            "shit", "shite", "shitty", "shits", "bullshit", "horseshit", "sh1t",
            "cunt", "cunts", "twat", "twats", "wanker", "wankers", "wank", "bellend", "knob", "knobhead", "dickhead", "dick", "dicks",
            "prick", "pricks", "cock", "cocks", "cocksucker", "bastard", "bastards", "bitch", "bitches", "slut", "sluts", "whore", "whores",
            "arse", "arsehole", "arseholes", "ass", "asshole", "assholes", "piss", "pissed", "bollocks", "bugger", "tosser", "tossers",
            "nonce", "nonces", "rape", "raped", "rapist", "porn", "nude", "nudes", "sex", "sexy", "onlyfans", "nsfw",
            "nigger", "niggers", "nigga", "niggas", "paki", "pakis", "chink", "chinks", "spic", "spics", "kike", "kikes", "wog", "wogs",
            "coon", "coons", "gypo", "gyppo", "pikey", "pikeys", "faggot", "faggots", "fag", "fags", "dyke", "dykes", "tranny", "trannies",
            "retard", "retards", "retarded", "spaz", "spastic", "mong", "mongs", "yid", "yids",
            "kill yourself", "kys", "die", "suicide"
    };

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern URL = Pattern.compile("(https?://|www\\.)\\S+");
    private static final Pattern BLOCK;

    static {
        StringBuilder b = new StringBuilder("(^|[^a-z])(");
        for (int i = 0; i < BLOCKED.length; i++) {
            if (i > 0) b.append('|');
            b.append(Pattern.quote(BLOCKED[i]));
        }
        b.append(")($|[^a-z])");
        BLOCK = Pattern.compile(b.toString());
    }

    /** The post's words: HTML out, entities read, links out, spaces closed up. */
    static String clean(String html) {
        if (html == null) return "";
        String s = TAGS.matcher(html.replace("<br>", " ").replace("<br/>", " ").replace("</p>", " ")).replaceAll(" ");
        s = entities(s);
        s = URL.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    /** The common HTML entities read back as letters (twice: Reddit's feeds escape their HTML once more). */
    static String entities(String s) {
        for (int round = 0; round < 2; round++) {
            s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
                    .replace("&apos;", "'").replace("&nbsp;", " ").replace("&#32;", " ").replace("&amp;", "&");
        }
        return s;
    }

    /** Cut to max letters at a word's end where it can be, with an ellipsis. */
    static String clip(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        int cut = s.lastIndexOf(' ', max - 1);
        if (cut < max * 0.6) cut = max - 1;
        return s.substring(0, cut).replaceAll("[\\s,.;:!?-]+$", "") + "…";
    }

    /** Whether the words (already cleaned) can go on the TV: enough of them, English, none blocked. */
    static boolean ok(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.length() < 12) return false;
        int letters = 0;
        for (int i = 0; i < t.length(); i++) if (Character.isLetter(t.charAt(i))) letters++;
        if (letters < 8) return false;
        return english(t) && !blocked(t) && !betting(t);
    }

    /**
     * Betting and tipping: odds, tips, prediction models and the like, in a post's words or in who posted it (a name or
     * handle such as "BoxPredict" or "acca_tips"). Not for a family TV, and not what fans are saying.
     */
    private static final Pattern BETTING = Pattern.compile(
            "\\b(bet|bets|betting|bettor|wager|wagers|odds|bookie|bookies|bookmakers?|acca|accas|accumulator|tipster|tipsters|"
                    + "parlay|parlays|punters|each-way|over/under|btts|value bet|free bets?|sportsbook|"
                    + "model verdict|our model|model and market|confidence:? \\d+ ?%|\\d+/\\d+ (?:shot|odds))\\b",
            Pattern.CASE_INSENSITIVE);
    /** Betting words as whole words of a name ("Bet North", "acca_tips", "TheBoxPredict" read as "the box predict"). */
    private static final java.util.Set<String> BETTING_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "bet", "bets", "betting", "odds", "tips", "tipster", "tipsters", "predict", "predicts", "predictor", "prediction",
            "predictions", "punter", "punters", "acca", "accas", "wager", "wagers", "bookie", "bookies", "picks"));
    /** And inside one word of a handle written all together ("theboxpredict"): only those never part of an ordinary name. */
    private static final String[] BETTING_INSIDE = {"predict", "tipster", "betting", "punter", "bookie"};

    /** Whether the words are about betting or tipping (see BETTING). */
    static boolean betting(String text) {
        return text != null && BETTING.matcher(text).find();
    }

    /** Whether a poster's name or handle is a betting or tipping account's (see BETTING_NAME). */
    static boolean bettingName(String nameOrHandle) {
        if (nameOrHandle == null) return false;
        // Words split at spaces, dots, underscores, digits and where a capital follows a small letter.
        String split = nameOrHandle.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("[^A-Za-z]+", " ").toLowerCase(java.util.Locale.ROOT).trim();
        for (String w : split.split(" ")) {
            if (BETTING_WORDS.contains(w)) return true;
            for (String in : BETTING_INSIDE) if (w.contains(in)) return true;
        }
        return false;
    }

    /** Whether any blocked word is in it, read with look-alikes (0 1 3 4 5 7 @ $ !) as letters and repeats squeezed. */
    static boolean blocked(String text) {
        String s = text.toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '0': c = 'o'; break;
                case '1': case '!': c = 'i'; break;
                case '3': c = 'e'; break;
                case '4': case '@': c = 'a'; break;
                case '5': case '$': c = 's'; break;
                case '7': c = 't'; break;
                case '*': c = 'u'; break;
                default: break;
            }
            b.append(c);
        }
        String plain = b.toString();
        if (BLOCK.matcher(plain).find()) return true;
        // "fuuuuck", "shiiit": letters run together squeezed to two, then one.
        String two = plain.replaceAll("([a-z])\\1{2,}", "$1$1"), one = plain.replaceAll("([a-z])\\1+", "$1");
        return BLOCK.matcher(two).find() || BLOCK.matcher(one).find();
    }

    /**
     * Whether it reads as English: nearly all its letters from the English alphabet, and at least one of the commonest
     * English words in it (a post in Spanish or Indonesian has neither).
     */
    static boolean english(String text) {
        int letters = 0, latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetter(c)) continue;
            letters++;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) latin++;
        }
        if (letters == 0 || latin < letters * 0.9f) return false;
        String s = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ") + " ";
        for (String w : new String[]{"the", "and", "is", "it", "to", "we", "a", "of", "in", "for", "this", "that", "on", "be", "with",
                "i", "you", "he", "they", "not", "but", "are", "was", "win", "game", "today", "come", "up", "our", "lads", "get", "can"}) {
            if (s.contains(" " + w + " ")) return true;
        }
        return false;
    }

    /** Bluesky's labels a post must not carry (its own and its author's). */
    static boolean badLabel(String label) {
        if (label == null) return false;
        String l = label.toLowerCase(Locale.ROOT);
        return l.equals("porn") || l.equals("sexual") || l.equals("nudity") || l.equals("graphic-media") || l.equals("gore")
                || l.equals("!hide") || l.equals("!warn") || l.equals("nsfl") || l.contains("sexual") || l.contains("nsfw");
    }
}
