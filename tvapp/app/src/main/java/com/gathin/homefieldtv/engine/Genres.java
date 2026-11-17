package com.gathin.homefieldtv.engine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The TV guide's categories (Entertainment, Sport, News, Movies, Kids, Documentaries, Music) for the family's own
 * channels, from what their playlist calls them: the channel's name first ("Sky Sports News", "Cartoon Network"), then
 * its group ("UK | Sports"). Anything else is Entertainment. Worked out once for each channel as the playlists are read
 * (Library), never as a screen opens: the words are found by hand, with no regular expression to make for each name.
 */
public final class Genres {
    private Genres() {}

    public static final String ENTERTAINMENT = "Entertainment", SPORT = "Sport", NEWS = "News", MOVIES = "Movies", KIDS = "Kids",
            DOCUMENTARIES = "Documentaries", MUSIC = "Music";
    public static final List<String> ORDER = Arrays.asList(ENTERTAINMENT, SPORT, NEWS, MOVIES, KIDS, DOCUMENTARIES, MUSIC);

    /** Each word's category, the more particular kinds first: "Sky Sports News" is sport, "Disney Cinema" a film channel. */
    private static final Map<String, String> WORDS = new HashMap<>();

    static {
        words(SPORT, "sport sports football soccer f1 formula golf cricket tennis rugby nba nfl nhl mlb boxing ufc wwe dazn espn "
                + "eurosport racing racingtv laliga bein ppv mutv lfctv nbcsn motorsport motogp snooker darts cycling");
        words(NEWS, "news cnn bloomberg cnbc msnbc euronews jazeera france24 newsmax");
        words(MOVIES, "movie movies cinema film films film4 premiere tcm cinemax starz horror movies24");
        words(KIDS, "kids kid children childrens cartoon cartoons cartoonito junior jr disney nick nickelodeon nicktoons cbeebies cbbc "
                + "baby babytv boomerang pop tiny toon toons jimjam kidz");
        words(DOCUMENTARIES, "documentary documentaries docu docs discovery history natgeo geographic animal science investigation "
                + "crime nature smithsonian curiosity");
        words(MUSIC, "music mtv vh1 kerrang kiss magic capital radio clubland 4music vevo hits");
    }

    private static void words(String kind, String list) {
        for (String w : list.split(" ")) WORDS.putIfAbsent(w, kind);
    }

    /** A channel's category. */
    public static String of(String group, String name) {
        String g = byWords(name);
        if (g == null) g = byWords(group);
        return g == null ? ENTERTAINMENT : g;
    }

    /** A category's place in ORDER (0 Entertainment … 6 Music); -1 for none. */
    public static int index(String genre) {
        return ORDER.indexOf(genre);
    }

    /** What a name (or a group's name) says on its own, or null when it says nothing. */
    static String byWords(String s) {
        if (s == null || s.isEmpty()) return null;
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("premier sports") || low.contains("premier league") || low.contains("tnt sports")) return SPORT;
        if (low.contains("al jazeera")) return NEWS;
        if (low.contains("nat geo") || low.contains("animal planet")) return DOCUMENTARIES;
        // The first kind in ORDER's priority (sport, news, movies, kids, documentaries, music) among all its words.
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        int n = low.length(), start = -1;
        for (int i = 0; i <= n; ) {
            int cp = i < n ? low.codePointAt(i) : ' ';
            boolean in = i < n && wordChar(cp);
            if (in && start < 0) start = i;
            if (!in && start >= 0) {
                String kind = WORDS.get(low.substring(start, i));
                int r = kind == null ? Integer.MAX_VALUE : rank(kind);
                if (r < bestRank) {
                    best = kind;
                    bestRank = r;
                    if (r == 0) return best;
                }
                start = -1;
            }
            i += i < n ? Character.charCount(cp) : 1;
        }
        return best;
    }

    /** The kinds' priority among a name's words: sport over news over movies over kids over documentaries over music. */
    private static int rank(String kind) {
        switch (kind) {
            case SPORT: return 0;
            case NEWS: return 1;
            case MOVIES: return 2;
            case KIDS: return 3;
            case DOCUMENTARIES: return 4;
            default: return 5;
        }
    }

    /** Pure: part of a word as the guide reads names: a letter, a number (of any script) or "+". */
    static boolean wordChar(int cp) {
        if (cp < 0x80) return cp >= 'a' && cp <= 'z' || cp >= '0' && cp <= '9' || cp >= 'A' && cp <= 'Z' || cp == '+';
        switch (Character.getType(cp)) {
            case Character.UPPERCASE_LETTER:
            case Character.LOWERCASE_LETTER:
            case Character.TITLECASE_LETTER:
            case Character.MODIFIER_LETTER:
            case Character.OTHER_LETTER:
            case Character.DECIMAL_DIGIT_NUMBER:
            case Character.LETTER_NUMBER:
            case Character.OTHER_NUMBER:
                return true;
            default:
                return false;
        }
    }
}
