package com.gathin.tvhomephone.live;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a fan's post must be to show on a family's TV picture: in English, words not just a link, nothing marked adult
 * or graphic, no swearing or slurs; tidied (HTML out, spaces together) and cut to a line or two. Pure: the tests feed it.
 */
final class FanFilter {
    private FanFilter() {}

    static final int MAX = 140;

    /** Bluesky's labels a post is never shown with. */
    private static final String[] BAD_LABELS = {"porn", "sexual", "nudity", "graphic-media", "gore", "!hide", "!warn", "nsfl"};

    /**
     * Words that keep a post off the screen: swearing, slurs and their usual spellings. Whole words (and common
     * endings), so "Scunthorpe" and "classic" pass.
     */
    private static final String[] WORDS = {
            "fuck", "fucks", "fucked", "fucking", "fuckin", "fucker", "fckn", "fck", "fk", "fkn", "fuk", "fking", "motherfucker", "mf",
            "shit", "shite", "shitting", "shitty", "bullshit", "bs", "cunt", "cunts", "twat", "twats", "wanker", "wankers", "wank",
            "bastard", "bastards", "bollocks", "bellend", "dickhead", "dick", "dicks", "cock", "cocks", "prick", "pricks", "piss",
            "pissed", "arse", "arsehole", "asshole", "ass", "bitch", "bitches", "slag", "slut", "whore", "tits", "knob", "knobhead",
            "damn", "goddamn", "crap", "wtf", "stfu", "omfg", "lmfao",
            "nigger", "nigga", "niggas", "paki", "pakis", "chink", "spic", "kike", "faggot", "fag", "fags", "dyke", "tranny",
            "retard", "retarded", "spastic", "spaz", "mong", "gyppo", "pikey", "coon", "wog", "yid", "yids",
            "rape", "raped", "rapist", "nazi", "kill yourself", "kys", "porn", "sex", "nude", "nudes", "onlyfans", "xxx",
    };
    private static final Pattern BAD;

    static {
        StringBuilder sb = new StringBuilder("(?i)(?<![\\p{L}\\p{N}])(?:");
        for (int i = 0; i < WORDS.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(WORDS[i].replace(" ", "\\s+"));   // plain letters: nothing to escape
        }
        sb.append(")(?![\\p{L}\\p{N}])");
        BAD = Pattern.compile(sb.toString());
    }

    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    /** Letters of other alphabets (and none of ours): not English. */
    private static final Pattern FOREIGN = Pattern.compile("[\\u0370-\\u03FF\\u0400-\\u04FF\\u0590-\\u05FF\\u0600-\\u06FF\\u0900-\\u097F\\u0E00-\\u0E7F\\u3040-\\u30FF\\u4E00-\\u9FFF\\uAC00-\\uD7AF]");
    /** Small words most English sentences have one of; a post with none of them (and some length) is likely another language. */
    private static final Pattern ENGLISH = Pattern.compile("(?i)(?<![\\p{L}])(?:the|a|an|and|is|are|was|we|i|it|to|of|in|on|for|this|that|with|be|you|my|our|they|he|his|not|no|just|so|but|at|what|get|got|have|has|win|today|game|lads|come|up|go|can|will|all|team|good|great|back|need|think|hope|first|half|line|starting|bench|vs|v|as|please|been|from|them|if|then|than|why|how|who|when|where|more|very|really|would|could|should|still|same|tonight|week|season|club|match|players|player|goal|goals|eleven|saturday|sunday|lineup|keeper|defence|midfield|fans|ref|our|their|me|do|did|does|don't|can't|it's|i'm|we're|let's|again|only|over|after|before|about|here|there|now|one|two|three)(?![\\p{L}])");

    /** Whether Bluesky's labels on a post keep it off. */
    static boolean badLabels(Iterable<String> labels) {
        if (labels == null) return false;
        for (String l : labels) {
            String v = l == null ? "" : l.toLowerCase(Locale.ROOT);
            for (String b : BAD_LABELS) if (v.equals(b)) return true;
        }
        return false;
    }

    /** HTML taken out (tags, the usual entities), links dropped, spaces put together. */
    static String clean(String s) {
        if (s == null) return "";
        String t = TAGS.matcher(s).replaceAll(" ");
        t = t.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
        t = LINK.matcher(t).replaceAll(" ");
        return SPACE.matcher(t).replaceAll(" ").trim();
    }

    /** Cut to MAX characters at a word, with an ellipsis. */
    static String cut(String s) {
        if (s.length() <= MAX) return s;
        int at = s.lastIndexOf(' ', MAX - 1);
        if (at < MAX / 2) at = MAX - 1;
        return s.substring(0, at).trim() + "…";
    }

    /** Whether a post's words have swearing or a slur in them. */
    static boolean rude(String s) {
        return s != null && BAD.matcher(s).find();
    }

    /** Whether a post reads as English (no other alphabet, and the usual small words where it is long enough). */
    static boolean english(String s) {
        if (s == null || s.isEmpty() || FOREIGN.matcher(s).find()) return false;
        String[] words = s.trim().split("\\s+");
        return words.length < 4 || ENGLISH.matcher(s).find();
    }

    /**
     * The text to show for a post, tidied and cut; null when it must not show: no words (a link or a picture alone),
     * not English, rude, or too short to say anything.
     */
    static String show(String raw) {
        String t = clean(raw);
        int letters = 0;
        for (int i = 0; i < t.length(); i++) if (Character.isLetter(t.charAt(i))) letters++;
        if (letters < 12) return null;
        if (!english(t) || rude(t)) return null;
        return cut(t);
    }

    /** Whether a name or handle is fit to show (no swearing in it). */
    static boolean nameOk(String name) {
        return name != null && !rude(name.replace('_', ' ').replace('.', ' ').replace('-', ' '));
    }
}
