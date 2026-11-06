package com.gathin.homefieldtv.engine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The TV guide's categories (Entertainment, Sport, News, Movies, Kids, Documentaries, Music) for the family's own
 * channels, from what their playlist calls them: the channel's name first ("Sky Sports News", "Cartoon Network"), then
 * its group ("UK | Sports"). Anything else is Entertainment.
 */
public final class Genres {
    private Genres() {}

    public static final String ENTERTAINMENT = "Entertainment", SPORT = "Sport", NEWS = "News", MOVIES = "Movies", KIDS = "Kids",
            DOCUMENTARIES = "Documentaries", MUSIC = "Music";
    public static final List<String> ORDER = Arrays.asList(ENTERTAINMENT, SPORT, NEWS, MOVIES, KIDS, DOCUMENTARIES, MUSIC);

    private static final Set<String> SPORT_WORDS = words("sport sports football soccer f1 formula golf cricket tennis rugby nba nfl "
            + "nhl mlb boxing ufc wwe dazn espn eurosport racing racingtv laliga bein ppv mutv lfctv nbcsn motorsport motogp snooker "
            + "darts cycling");
    private static final Set<String> NEWS_WORDS = words("news cnn bloomberg cnbc msnbc euronews jazeera france24 newsmax");
    private static final Set<String> KIDS_WORDS = words("kids kid children childrens cartoon cartoons cartoonito junior jr disney nick "
            + "nickelodeon nicktoons cbeebies cbbc baby babytv boomerang pop tiny toon toons jimjam kidz");
    private static final Set<String> MOVIE_WORDS = words("movie movies cinema film films film4 premiere tcm cinemax starz horror "
            + "movies24");
    private static final Set<String> DOC_WORDS = words("documentary documentaries docu docs discovery history natgeo geographic "
            + "animal science investigation crime nature smithsonian curiosity");
    private static final Set<String> MUSIC_WORDS = words("music mtv vh1 kerrang kiss magic capital radio clubland 4music vevo hits");

    private static Set<String> words(String list) {
        return new HashSet<>(Arrays.asList(list.split(" ")));
    }

    /** A channel's category. */
    public static String of(String group, String name) {
        String g = byWords(name);
        if (g == null) g = byWords(group);
        return g == null ? ENTERTAINMENT : g;
    }

    private static String byWords(String s) {
        if (s == null || s.isEmpty()) return null;
        String low = s.toLowerCase(Locale.ROOT);
        if (low.contains("premier sports") || low.contains("premier league") || low.contains("tnt sports")) return SPORT;
        if (low.contains("al jazeera")) return NEWS;
        if (low.contains("nat geo") || low.contains("animal planet")) return DOCUMENTARIES;
        String[] w = low.split("[^\\p{L}\\p{N}+]+");
        // The more particular kinds first: "Sky Sports News" is sport, "Disney Cinema" a film channel.
        for (String t : w) if (SPORT_WORDS.contains(t)) return SPORT;
        for (String t : w) if (NEWS_WORDS.contains(t)) return NEWS;
        for (String t : w) if (MOVIE_WORDS.contains(t)) return MOVIES;
        for (String t : w) if (KIDS_WORDS.contains(t)) return KIDS;
        for (String t : w) if (DOC_WORDS.contains(t)) return DOCUMENTARIES;
        for (String t : w) if (MUSIC_WORDS.contains(t)) return MUSIC;
        return null;
    }
}
