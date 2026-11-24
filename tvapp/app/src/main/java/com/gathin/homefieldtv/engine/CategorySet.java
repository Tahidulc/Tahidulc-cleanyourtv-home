package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The rows of the Editorial Home, Movies and TV Shows, in one table, in the order the family's other film app shows them:
 * each row's page, its id (the remote's place is kept by it), its title, where its titles come from (its source query)
 * and its place on the page. To reorder or rename a row, change it here; nothing else needs to know. A row whose source
 * finds nothing in the family's playlists is left out (Shelves), never shown empty.
 *
 * <p>A source query is one of these, or several joined by "+" (their titles taken in turn: films and shows together)
 * or by "|" (the first that finds anything: TMDB's list, else the playlists' own):
 * <pre>
 *   continue[:movie|:tv]     where the family left off (Resume), newest first
 *   mylist[:movie|:tv]       My List
 *   new:movie|tv|all         the playlists' newest (by when the provider added them)
 *   torbox:movie|tv|all      the family's own TorBox titles that no playlist has
 *   tmdb:PATH                one of TMDB's lists ("trending/movie/day", "movie/top_rated", "discover/movie?with_genres=35"),
 *                            matched to what the playlists hold. In PATH, {today} is today's date, {today-365} a year ago,
 *                            and {a/b} TMDB's keywords named a or b (their ids found by their names, kept a month)
 *   list:ID~WORDS            a TMDB list, while its name has WORDS (a list that changed is never shown as another)
 *   network:ID[/ID]          TMDB's shows of a network (or several), most popular first; {network} in the row's title is
 *                            the first one's name as TMDB gives it
 *   latest:ID[/ID]           the same, the newest first
 *   because:N                TMDB's recommendations for the Nth title the family watched lately ({title}: its name)
 *   foryou:all|movie|tv      TMDB's recommendations for the titles the family watched lately, together
 *   genre:movie=ID;tv=ID;words=a,b   TMDB's popular titles of a genre (films' and shows' ids; either may be left out),
 *                            matched; without TMDB, the playlists' categories whose names have one of the words
 * </pre>
 * A network TMDB names Apple's or Netflix's is left out (Shelves.allowedName), whatever this table says; the other app's
 * rows for those two services are not in the table at all.
 */
public final class CategorySet {
    private CategorySet() {}

    public static final String HOME = "home", MOVIES = "movies", SHOWS = "shows";
    /** How a row draws: the carousel at the top, or a row of titles (the remote's grows into its wide picture). */
    public static final int HERO = 0, ROW = 1;

    /** One row: its page, its id (kept with the remote's place), its title, source, place, look. */
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

    // Themed rows from TMDB's discover: films, the most popular first unless said.
    private static final String FILMS = "tmdb:discover/movie?";
    private static final String TRUE_STORY = FILMS + "with_keywords={based on true story}&sort_by=popularity.desc&vote_count.gte=100",
            ACCLAIMED = FILMS + "sort_by=vote_average.desc&vote_count.gte=3000",
            NINETY = FILMS + "with_runtime.gte=80&with_runtime.lte=95&sort_by=popularity.desc&vote_count.gte=300",
            SCIFI_TOP = FILMS + "with_genres=878&sort_by=popularity.desc&vote_average.gte=6.5&vote_count.gte=500",
            COMEDY = "genre:movie=35;words=comedy,comedies",
            OSCARS = "list:28~best picture",
            VISUAL = FILMS + "with_keywords={visually stunning/cinematography}&sort_by=popularity.desc&vote_count.gte=200|" + FILMS
                    + "with_genres=14,12&sort_by=vote_average.desc&vote_count.gte=3000",
            TWISTS = FILMS + "with_keywords={plot twist/twist ending}&sort_by=popularity.desc&vote_count.gte=200",
            BRITISH_GANGSTERS = FILMS + "with_keywords={gangster/organized crime}&with_origin_country=GB&sort_by=popularity.desc"
                    + "&vote_count.gte=30",
            CULT = FILMS + "with_keywords={cult film}&sort_by=vote_count.desc",
            SURVIVAL = FILMS + "with_keywords={survival}&sort_by=popularity.desc&vote_count.gte=200",
            NOSTALGIA = FILMS + "primary_release_date.gte=1980-01-01&primary_release_date.lte=1999-12-31&sort_by=popularity.desc"
                    + "&vote_count.gte=1000",
            REVENGE = FILMS + "with_keywords={revenge}&sort_by=popularity.desc&vote_count.gte=200",
            ZERO_GRAVITY = FILMS + "with_keywords={space travel/outer space/astronaut}&sort_by=popularity.desc&vote_count.gte=200",
            NO_BRAINERS = FILMS + "with_genres=28%7C35&vote_average.lte=6.5&vote_count.gte=500&sort_by=popularity.desc",
            THRILLER = "genre:movie=53;words=thriller,thrillers",
            CONSPIRACY = FILMS + "with_keywords={conspiracy/conspiracy theory}&sort_by=popularity.desc&vote_count.gte=100",
            CRIME_TOP = FILMS + "with_genres=80&sort_by=popularity.desc&vote_average.gte=6.5&vote_count.gte=500",
            THRILLER_TOP = FILMS + "with_genres=53&sort_by=popularity.desc&vote_average.gte=6.5&vote_count.gte=500",
            CRIME_RATED = FILMS + "with_genres=80&sort_by=vote_average.desc&vote_count.gte=2000",
            BANK_ROBBERY = FILMS + "with_keywords={bank robbery/heist}&sort_by=vote_count.desc",
            LAUGHS = FILMS + "with_genres=35&sort_by=vote_count.desc",
            OLD_FRESH = FILMS + "primary_release_date.lte=1995-12-31&vote_average.gte=7.5&vote_count.gte=1000&sort_by=popularity.desc",
            MORE_THAN_ONCE = FILMS + "vote_count.gte=10000&sort_by=vote_average.desc",
            GOOD_LAUGH = FILMS + "with_genres=35&primary_release_date.gte=2010-01-01&sort_by=popularity.desc&vote_count.gte=300",
            ROMCOM = FILMS + "with_genres=10749,35&sort_by=popularity.desc&vote_count.gte=200",
            COMEDY_TOP = FILMS + "with_genres=35&sort_by=popularity.desc&vote_average.gte=6.8&vote_count.gte=500",
            NINETIES = FILMS + "primary_release_date.gte=1990-01-01&primary_release_date.lte=1999-12-31&sort_by=vote_average.desc"
                    + "&vote_count.gte=2000",
            MYSTERY_THRILLERS = FILMS + "with_genres=9648,53&sort_by=popularity.desc&vote_count.gte=200",
            VACATIONS = FILMS + "with_keywords={vacation/holiday/road trip}&sort_by=popularity.desc&vote_count.gte=100",
            FAMILY = FILMS + "with_genres=10751&sort_by=popularity.desc&vote_count.gte=500";

    /** The networks the TV Shows rows ask for, by TMDB's id: HBO Max (with HBO), Prime Video, Disney+. */
    static final int[] NETWORKS = {3186, 49, 1024, 2739};

    /** The whole table: page, id, title, source, kind; a page's rows in the order listed. */
    private static final Object[][] TABLE = {
            // ---- Home: 23 rows, as the other app has them (no carousel) -------------------------------------------
            {HOME, "continue", "Continue Watching", "continue", ROW},
            {HOME, "latest.releases", "Latest Releases", FILMS + "sort_by=primary_release_date.desc&primary_release_date.lte={today}"
                    + "&with_release_type=4%7C5&vote_count.gte=10|new:movie", ROW},
            {HOME, "trending.movies", "Trending Movies", "tmdb:trending/movie/day", ROW},
            {HOME, "latest.shows", "Latest TV Shows",
                    "tmdb:discover/tv?sort_by=first_air_date.desc&first_air_date.lte={today}&vote_count.gte=10|new:tv", ROW},
            {HOME, "because.0", "Because you watched {title}", "because:0", ROW},
            {HOME, "foryou", "We think you will like these", "foryou:all", ROW},
            {HOME, "because.1", "Because you watched {title}", "because:1", ROW},
            {HOME, "true.story", "Based on True Story", TRUE_STORY, ROW},
            {HOME, "acclaimed", "Critically Acclaimed", ACCLAIMED, ROW},
            {HOME, "ninety", "90 Minute Movies", NINETY, ROW},
            {HOME, "scifi.top", "Sci-Fi Movies: Top Picks", SCIFI_TOP, ROW},
            {HOME, "comedy", "Comedy Movies", COMEDY, ROW},
            {HOME, "oscars", "Oscar Winners: Best Picture", OSCARS, ROW},
            {HOME, "visual", "Visually Pleasing", VISUAL, ROW},
            {HOME, "twists", "Crazy Plot Twists", TWISTS, ROW},
            {HOME, "british.gangsters", "British Gangster Movies", BRITISH_GANGSTERS, ROW},
            {HOME, "cult", "Cult Classics", CULT, ROW},
            {HOME, "survival", "Survival Movies", SURVIVAL, ROW},
            {HOME, "nostalgia", "Nostalgia", NOSTALGIA, ROW},
            {HOME, "revenge", "Revenge Movies", REVENGE, ROW},
            {HOME, "zero.gravity", "Zero Gravity Watchlist", ZERO_GRAVITY, ROW},
            {HOME, "no.brainers", "No brainers", NO_BRAINERS, ROW},
            {HOME, "thriller", "Thriller Movies", THRILLER, ROW},

            // ---- Movies: the carousel, then 22 rows ---------------------------------------------------------------
            {MOVIES, "hero", "", "tmdb:movie/now_playing?region=GB|tmdb:trending/movie/week|new:movie", HERO},
            {MOVIES, "trending", "Trending Movies", "tmdb:trending/movie/day", ROW},
            {MOVIES, "conspiracy", "Conspiracy Theories", CONSPIRACY, ROW},
            {MOVIES, "crime.top", "Crime Movies: Top Picks", CRIME_TOP, ROW},
            {MOVIES, "survival", "Survival Movies", SURVIVAL, ROW},
            {MOVIES, "thriller.top", "Thriller Movies: Top Picks", THRILLER_TOP, ROW},
            {MOVIES, "revenge", "Revenge Movies", REVENGE, ROW},
            {MOVIES, "twists", "Crazy Plot Twists", TWISTS, ROW},
            {MOVIES, "crime.rated", "Top Rated Crime Films", CRIME_RATED, ROW},
            {MOVIES, "foryou", "We know you'll love these", "foryou:movie", ROW},
            {MOVIES, "bank.robbery", "Top Bank Robbery Movies", BANK_ROBBERY, ROW},
            {MOVIES, "laughs", "Laughs & Laughter", LAUGHS, ROW},
            {MOVIES, "old.fresh", "Old But Fresh", OLD_FRESH, ROW},
            {MOVIES, "more.than.once", "Watch More Than Once", MORE_THAN_ONCE, ROW},
            {MOVIES, "good.laugh", "Need a Good Laugh?", GOOD_LAUGH, ROW},
            {MOVIES, "romcom", "Romantic Comedies", ROMCOM, ROW},
            {MOVIES, "comedy.top", "Comedy Movies: Top Picks", COMEDY_TOP, ROW},
            {MOVIES, "nineties", "90s Classics", NINETIES, ROW},
            {MOVIES, "cult", "Cult Classics", CULT, ROW},
            {MOVIES, "mystery.thrillers", "Mystery Thrillers", MYSTERY_THRILLERS, ROW},
            {MOVIES, "vacations", "Vacations Gone Wrong", VACATIONS, ROW},
            {MOVIES, "scifi.top", "Sci-Fi Movies: Top Picks", SCIFI_TOP, ROW},
            {MOVIES, "family", "Family Favorites", FAMILY, ROW},

            // ---- TV Shows: the carousel, Trending, then a row for each streaming service --------------------------
            // The other app also has rows for Apple's and Netflix's services, between Trending and HBO Max's and after it:
            // left out here, as their names and marks may not appear in Homefield.
            {SHOWS, "hero", "", "tmdb:tv/on_the_air|tmdb:trending/tv/week|new:tv", HERO},
            {SHOWS, "trending", "Trending Shows", "tmdb:trending/tv/day", ROW},
            {SHOWS, "network.3186", "Latest {network} Shows", "latest:3186/49", ROW},
            {SHOWS, "network.1024", "Latest {network} Shows", "latest:1024", ROW},
            {SHOWS, "network.2739", "{network} Shows", "network:2739", ROW},
    };

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
            for (String k : TMDB_KINDS) if (p.startsWith(k)) return true;
        }
        return false;
    }

    private static final String[] TMDB_KINDS = {"tmdb:", "list:", "network:", "latest:", "because:", "foryou:", "genre:"};

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
