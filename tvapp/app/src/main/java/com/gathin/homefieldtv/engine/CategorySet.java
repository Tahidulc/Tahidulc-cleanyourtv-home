package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rows of the Editorial Home, Movies and TV Shows, in one table: each row's page, its id, its title, where its titles
 * come from (its source query) and its place on the page. To reorder or rename a row, change it here; nothing else
 * needs to know. A row whose source finds nothing in the family's playlists is left out (Shelves), never shown empty.
 *
 * <p>A source query is one of these, or several joined by "+" (their titles taken in turn: films and shows together)
 * or by "|" (the first that finds anything: TMDB's list, else the playlists' own):
 * <pre>
 *   continue[:movie|:tv]     where the family left off (Resume), newest first
 *   mylist[:movie|:tv]       My List
 *   new:movie|tv|all         the playlists' newest (by when the provider added them)
 *   torbox:movie|tv|all      the family's own TorBox titles that no playlist has
 *   tmdb:PATH                one of TMDB's lists ("trending/all/day", "movie/popular", "tv/airing_today", "discover/movie?
 *                            with_genres=28"), matched to what the playlists hold
 *   top10:movie|tv           ten in order (TopTen where the app has one; TMDB's trending this week, matched, until then)
 *   network:ID               TMDB's shows of a network (discover/tv?with_networks=ID), the row named as TMDB names it
 *   genre:movie=ID;tv=ID;words=a,b   TMDB's popular titles of a genre (films' and shows' ids; either may be left out),
 *                            matched; without TMDB, the playlists' categories whose names have one of the words
 *   moods:movie|tv|all       the mood tiles (MOODS below): each opens its titles
 *   categories:movie|tv      the playlists' own categories, as tiles that open them
 * </pre>
 * Networks are named as TMDB names them, and any whose name is Apple's or Netflix's is left out (Shelves.allowedName),
 * whatever this table says.
 */
public final class CategorySet {
    private CategorySet() {}

    public static final String HOME = "home", MOVIES = "movies", SHOWS = "shows";
    /** How a row draws: the carousel at the top, posters, wide pictures, ten with their numbers, tiles. */
    public static final int HERO = 0, POSTERS = 1, WIDE = 2, TOP10 = 3, TILES = 4;

    /** One row: its page, its id (kept with the remote's place), its title ("" for TMDB's name), source, place, look. */
    public static final class Entry {
        public final String page, id, title, source;
        public final int order, kind;

        Entry(String page, String id, String title, String source, int order, int kind) {
            this.page = page;
            this.id = id;
            this.title = title;
            this.source = source;
            this.order = order;
            this.kind = kind;
        }

        /** Whether its titles come from TMDB (asked off the main thread), rather than from the family's own lists. */
        public boolean tmdb() {
            return CategorySet.tmdb(source);
        }

        @Override public String toString() {
            return page + "." + id;
        }
    }

    // TMDB's genre ids (films, then shows: TMDB's show genres join some of the films' into one, Sci-Fi & Fantasy).
    private static final String ACTION = "genre:movie=28;tv=10759;words=action,adventure",
            COMEDY = "genre:movie=35;tv=35;words=comedy,comedies,sitcom",
            DRAMA = "genre:movie=18;tv=18;words=drama",
            SCIFI = "genre:movie=878;tv=10765;words=scifi,sci-fi,science fiction,fantasy",
            HORROR = "genre:movie=27;words=horror",
            THRILLER = "genre:movie=53;words=thriller,thrillers",
            CRIME = "genre:movie=80;tv=80;words=crime",
            ANIMATION = "genre:movie=16;tv=16;words=animation,animated,cartoon,anime",
            FAMILY = "genre:movie=10751;tv=10751;words=family,kids",
            DOCUMENTARY = "genre:movie=99;tv=99;words=documentary,documentaries",
            ROMANCE = "genre:movie=10749;words=romance,romantic",
            MYSTERY = "genre:movie=9648;tv=9648;words=mystery";

    /**
     * Streaming networks by TMDB's id, named as TMDB names them: HBO, Disney+, Prime Video, Max, Paramount+, Hulu, Sky
     * Atlantic, BBC One, ITV1, Channel 4. (Netflix's and Apple's are never asked for.)
     */
    static final int[] NETWORKS = {49, 2739, 1024, 3186, 4330, 453, 1063, 4, 9, 26};

    /** The whole table: page, id, title, source, kind; a page's rows in the order listed (order counts in tens). */
    private static final Object[][] TABLE = {
            // ---- Home ----------------------------------------------------------------------------------------------
            {HOME, "hero", "", "tmdb:trending/all/day|new:all", HERO},
            {HOME, "continue", "Continue watching", "continue", WIDE},
            {HOME, "top10.films", "Top 10 films", "top10:movie", TOP10},
            {HOME, "trending", "Trending today", "tmdb:trending/all/day", POSTERS},
            {HOME, "popular.films", "Popular films", "tmdb:movie/popular", POSTERS},
            {HOME, "popular.shows", "Popular shows", "tmdb:tv/popular", POSTERS},
            {HOME, "top10.shows", "Top 10 shows", "top10:tv", TOP10},
            {HOME, "new", "New releases", "new:all", POSTERS},
            {HOME, "top.rated", "Top rated", "tmdb:movie/top_rated+tmdb:tv/top_rated", POSTERS},
            {HOME, "moods", "Moods", "moods:all", TILES},
            {HOME, "torbox", Catalog.TORBOX, "torbox:all", POSTERS},
            {HOME, "network.49", "", "network:49", POSTERS},
            {HOME, "network.2739", "", "network:2739", POSTERS},
            {HOME, "network.1024", "", "network:1024", POSTERS},
            {HOME, "network.3186", "", "network:3186", POSTERS},
            {HOME, "network.4330", "", "network:4330", POSTERS},
            {HOME, "network.1063", "", "network:1063", POSTERS},
            {HOME, "network.4", "", "network:4", POSTERS},
            {HOME, "network.9", "", "network:9", POSTERS},
            {HOME, "genre.action", "Action & adventure", ACTION, POSTERS},
            {HOME, "genre.comedy", "Comedy", COMEDY, POSTERS},
            {HOME, "genre.drama", "Drama", DRAMA, POSTERS},
            {HOME, "genre.scifi", "Sci-Fi & fantasy", SCIFI, POSTERS},
            {HOME, "genre.crime", "Crime", CRIME, POSTERS},
            {HOME, "genre.animation", "Animation", ANIMATION, POSTERS},
            {HOME, "genre.family", "Family", FAMILY, POSTERS},
            {HOME, "genre.documentary", "Documentary", DOCUMENTARY, POSTERS},

            // ---- Movies --------------------------------------------------------------------------------------------
            {MOVIES, "hero", "", "tmdb:trending/movie/day|new:movie", HERO},
            {MOVIES, "continue", "Continue watching", "continue:movie", WIDE},
            {MOVIES, "trending", "Trending", "tmdb:trending/movie/day", POSTERS},
            {MOVIES, "top10", "Top 10 films", "top10:movie", TOP10},
            {MOVIES, "popular", "Popular", "tmdb:movie/popular", POSTERS},
            {MOVIES, "now.playing", "New releases", "tmdb:movie/now_playing?region=GB|new:movie", POSTERS},
            {MOVIES, "recent", "Recently added", "new:movie", POSTERS},
            {MOVIES, "top.rated", "Top rated", "tmdb:movie/top_rated", POSTERS},
            {MOVIES, "upcoming", "Upcoming", "tmdb:movie/upcoming?region=GB", POSTERS},
            {MOVIES, "moods", "Moods", "moods:movie", TILES},
            {MOVIES, "genre.action", "Action", "genre:movie=28;words=action", POSTERS},
            {MOVIES, "genre.comedy", "Comedy", "genre:movie=35;words=comedy,comedies", POSTERS},
            {MOVIES, "genre.drama", "Drama", "genre:movie=18;words=drama", POSTERS},
            {MOVIES, "genre.horror", "Horror", HORROR, POSTERS},
            {MOVIES, "genre.scifi", "Sci-Fi", "genre:movie=878;words=scifi,sci-fi,science fiction", POSTERS},
            {MOVIES, "genre.thriller", "Thriller", THRILLER, POSTERS},
            {MOVIES, "genre.animation", "Animation", "genre:movie=16;words=animation,animated,cartoon,anime", POSTERS},
            {MOVIES, "genre.family", "Family", "genre:movie=10751;words=family,kids", POSTERS},
            {MOVIES, "genre.documentary", "Documentary", "genre:movie=99;words=documentary,documentaries", POSTERS},
            {MOVIES, "genre.romance", "Romance", ROMANCE, POSTERS},
            {MOVIES, "genre.crime", "Crime", "genre:movie=80;words=crime", POSTERS},
            {MOVIES, "genre.adventure", "Adventure", "genre:movie=12;words=adventure", POSTERS},
            {MOVIES, "genre.fantasy", "Fantasy", "genre:movie=14;words=fantasy", POSTERS},
            {MOVIES, "genre.mystery", "Mystery", "genre:movie=9648;words=mystery", POSTERS},
            {MOVIES, "genre.war", "War", "genre:movie=10752;words=war", POSTERS},
            {MOVIES, "genre.history", "History", "genre:movie=36;words=history", POSTERS},
            {MOVIES, "torbox", Catalog.TORBOX, "torbox:movie", POSTERS},
            {MOVIES, "mylist", "My List", "mylist:movie", POSTERS},
            {MOVIES, "categories", "Your playlists' categories", "categories:movie", TILES},

            // ---- TV Shows ------------------------------------------------------------------------------------------
            {SHOWS, "hero", "", "tmdb:trending/tv/day|new:tv", HERO},
            {SHOWS, "continue", "Continue watching", "continue:tv", WIDE},
            {SHOWS, "trending", "Trending", "tmdb:trending/tv/day", POSTERS},
            {SHOWS, "top10", "Top 10 shows", "top10:tv", TOP10},
            {SHOWS, "popular", "Popular", "tmdb:tv/popular", POSTERS},
            {SHOWS, "airing.today", "Airing today", "tmdb:tv/airing_today", POSTERS},
            {SHOWS, "on.the.air", "On the air", "tmdb:tv/on_the_air", POSTERS},
            {SHOWS, "top.rated", "Top rated", "tmdb:tv/top_rated", POSTERS},
            {SHOWS, "recent", "Recently added", "new:tv", POSTERS},
            {SHOWS, "network.49", "", "network:49", POSTERS},
            {SHOWS, "network.2739", "", "network:2739", POSTERS},
            {SHOWS, "network.1024", "", "network:1024", POSTERS},
            {SHOWS, "network.3186", "", "network:3186", POSTERS},
            {SHOWS, "network.4330", "", "network:4330", POSTERS},
            {SHOWS, "network.453", "", "network:453", POSTERS},
            {SHOWS, "network.1063", "", "network:1063", POSTERS},
            {SHOWS, "network.4", "", "network:4", POSTERS},
            {SHOWS, "network.9", "", "network:9", POSTERS},
            {SHOWS, "network.26", "", "network:26", POSTERS},
            {SHOWS, "moods", "Moods", "moods:tv", TILES},
            {SHOWS, "genre.drama", "Drama", "genre:tv=18;words=drama", POSTERS},
            {SHOWS, "genre.comedy", "Comedy", "genre:tv=35;words=comedy,comedies,sitcom", POSTERS},
            {SHOWS, "genre.crime", "Crime", "genre:tv=80;words=crime", POSTERS},
            {SHOWS, "genre.action", "Action & adventure", "genre:tv=10759;words=action,adventure", POSTERS},
            {SHOWS, "genre.scifi", "Sci-Fi & fantasy", "genre:tv=10765;words=scifi,sci-fi,science fiction,fantasy", POSTERS},
            {SHOWS, "genre.mystery", "Mystery", "genre:tv=9648;words=mystery", POSTERS},
            {SHOWS, "genre.animation", "Animation", "genre:tv=16;words=animation,animated,cartoon,anime", POSTERS},
            {SHOWS, "genre.family", "Family", "genre:tv=10751;words=family", POSTERS},
            {SHOWS, "genre.kids", "Kids", "genre:tv=10762;words=kids,children", POSTERS},
            {SHOWS, "genre.documentary", "Documentary", "genre:tv=99;words=documentary,documentaries,docu", POSTERS},
            {SHOWS, "genre.reality", "Reality", "genre:tv=10764;words=reality", POSTERS},
            {SHOWS, "torbox", Catalog.TORBOX, "torbox:tv", POSTERS},
            {SHOWS, "mylist", "My List", "mylist:tv", POSTERS},
            {SHOWS, "categories", "Your playlists' categories", "categories:tv", TILES},
    };

    /**
     * The mood tiles: a name, the colours of its tile (top left, foot right) and its titles' source. On Home the tiles
     * take films and shows; on Movies and TV Shows only theirs (a genre the other has none of simply finds none there).
     */
    public static final class Mood {
        public final String name, source;
        public final int from, to;

        Mood(String name, int from, int to, String source) {
            this.name = name;
            this.from = from;
            this.to = to;
            this.source = source;
        }
    }

    public static final List<Mood> MOODS = Collections.unmodifiableList(java.util.Arrays.asList(
            new Mood("Film night", 0xFF7A3CFF, 0xFF2B0F6B, "tmdb:movie/top_rated"),
            new Mood("Edge of your seat", 0xFFE5484D, 0xFF4A0D1A, "genre:movie=53;tv=80;words=thriller,crime"),
            new Mood("Laugh out loud", 0xFFFFB547, 0xFF8A3B00, COMEDY),
            new Mood("Feel-good", 0xFF30C48D, 0xFF0B4A3A, "genre:movie=10751;tv=10751;words=family"),
            new Mood("Mind-bending", 0xFF2FA4FF, 0xFF0B2A6B, SCIFI),
            new Mood("Scary", 0xFF8E8E9A, 0xFF16161C, HORROR),
            new Mood("True stories", 0xFFD9A066, 0xFF4A2A10, DOCUMENTARY),
            new Mood("Animated", 0xFFFF6FB5, 0xFF5A0F3D, ANIMATION)));

    /** A page's rows, in their order (HERO first where the page has one). */
    public static List<Entry> of(String page) {
        List<Entry> out = new ArrayList<>();
        int order = 0;
        for (Object[] r : TABLE) {
            if (!r[0].equals(page)) continue;
            order += 10;
            out.add(new Entry((String) r[0], (String) r[1], (String) r[2], (String) r[3], order, (Integer) r[4]));
        }
        out.sort((a, b) -> Integer.compare(a.order, b.order));
        return out;
    }

    /** Pure: whether a source asks TMDB anywhere in it (a "|" or "+" of several: any of them). */
    public static boolean tmdb(String source) {
        if (source == null) return false;
        for (String part : source.split("[|+]")) {
            String p = part.trim();
            if (p.startsWith("tmdb:") || p.startsWith("top10:") || p.startsWith("network:") || p.startsWith("genre:")) return true;
        }
        return false;
    }

    /** Pure: a source's parts: its "|" choices, each a list of "+" parts taken in turn. */
    static List<List<String>> parts(String source) {
        List<List<String>> out = new ArrayList<>();
        if (source == null) return out;
        for (String choice : source.split("\\|")) {
            List<String> each = new ArrayList<>();
            for (String p : choice.split("\\+")) if (!p.trim().isEmpty()) each.add(p.trim());
            if (!each.isEmpty()) out.add(each);
        }
        return out;
    }
}
