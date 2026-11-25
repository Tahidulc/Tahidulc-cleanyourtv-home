package com.gathin.homefieldtv.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The rows of the Editorial Home, Movies and TV Shows, in one table: each row's page, its id (the remote's place is kept
 * by it), its title, where its titles come from (its source query), its look and when it shows. The rows the family's
 * other film app has are all here, with the living ones the family asked for ("the categories for all this should be
 * fluid and ever changing"): Top 10 Today, New This Week, a genre and a mood of the day, the row for the part of the day,
 * seasonal rows, award winners, hidden gems, Coming Soon, and what is newly added to the playlists and TorBox. Which of
 * them a day shows, and in what order, is DayRows'. To rename a row or change where its titles come from, change it here;
 * nothing else needs to know. A row whose source finds nothing in the family's playlists is left out (Shelves), never
 * shown empty.
 *
 * <p>When a row shows (its when): "first" always, at the top (Continue Watching); "head" always, near the top, in the
 * day's order; "season:MM-DD/MM-DD" only in season; "part:morning|afternoon|evening|late" only then; "pick:GROUP" one of
 * the group a day (the genre and the mood of the day); "" one of the rows taken in turn, a few each day.
 *
 * <p>A source query is one of these, or several joined by "+" (their titles taken in turn: films and shows together)
 * or by "|" (the first that finds anything: TMDB's list, else the playlists' own):
 * <pre>
 *   continue[:movie|:tv]     where the family left off (Resume), newest first
 *   mylist[:movie|:tv]       My List
 *   new:movie|tv|all         the playlists' newest (by when the provider added them)
 *   fresh:movie|tv|all       the playlists' titles added in the last week, newest first
 *   torbox:movie|tv|all      the family's own TorBox titles that no playlist has
 *   tmdb:PATH                one of TMDB's lists ("trending/movie/day", "movie/top_rated", "discover/movie?with_genres=35"),
 *                            matched to what the playlists hold. In PATH, {today} is today's date, {today-365} a year ago,
 *                            {today+30} a month on, and {a/b} TMDB's keywords named a or b (their ids found by their
 *                            names, kept a month)
 *   top:PATH                 one of TMDB's lists, matched, its first ten only (a Top 10)
 *   soon:PATH                one of TMDB's lists of what is still to come: every title in it, whether the playlists have
 *                            it yet or not (the only rows with titles the family can't play yet; their row says so)
 *   list:ID~WORDS            a TMDB list, while its name has WORDS (a list that changed is never shown as another)
 *   network:ID[/ID]          TMDB's shows of a network (or several), most popular first; {network} in the row's title is
 *                            the first one's name as TMDB gives it
 *   latest:ID[/ID]           the same, the newest first
 *   because:N[:movie|:tv]    TMDB's recommendations for the Nth title (film or show) the family watched lately ({title}:
 *                            its name)
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
    /**
     * How a row draws: the carousel at the top (the page's featured titles, from what is trending today and what the
     * playlists added this week), or a row of titles (the remote's grows into its wide picture).
     */
    public static final int HERO = 0, ROW = 1;

    /** One row: its page, its id (kept with the remote's place), its title, source, place, look and when it shows. */
    public static final class Entry {
        public final String page, id, title, source, when;
        public final int order, kind;

        Entry(String page, String id, String title, String source, int order, int kind) {
            this(page, id, title, source, order, kind, "");
        }

        Entry(String page, String id, String title, String source, int order, int kind, String when) {
            this.page = page;
            this.id = id;
            this.title = title;
            this.source = source;
            this.order = order;
            this.kind = kind;
            this.when = when == null ? "" : when;
        }

        /** Whether it is one of TMDB's lists of what is still to come (its titles may not be in the playlists yet). */
        public boolean soon() {
            return source.startsWith("soon:") || source.contains("+soon:");
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

    // The living rows: what's new, coming, hidden away, in season and for the part of the day.
    private static final String SERIES = "tmdb:discover/tv?";
    private static final String LATEST_FILMS = FILMS + "sort_by=primary_release_date.desc&primary_release_date.lte={today}"
            + "&with_release_type=4%7C5&vote_count.gte=10|new:movie",
            LATEST_SHOWS = SERIES + "sort_by=first_air_date.desc&first_air_date.lte={today}&vote_count.gte=10|new:tv",
            HIDDEN_FILMS = FILMS + "vote_average.gte=7.3&vote_count.gte=150&vote_count.lte=1500&sort_by=vote_average.desc",
            HIDDEN_SHOWS = SERIES + "vote_average.gte=7.8&vote_count.gte=80&vote_count.lte=800&sort_by=vote_average.desc",
            SOON_FILMS = "soon:discover/movie?primary_release_date.gte={today+1}&primary_release_date.lte={today+120}&region=GB"
                    + "&with_release_type=2%7C3&sort_by=popularity.desc",
            SOON_SHOWS = "soon:discover/tv?first_air_date.gte={today+1}&first_air_date.lte={today+120}&sort_by=popularity.desc",
            CHRISTMAS = FILMS + "with_keywords={christmas}&sort_by=popularity.desc&vote_count.gte=50",
            CHRISTMAS_SHOWS = SERIES + "with_keywords={christmas}&sort_by=popularity.desc",
            HALLOWEEN = FILMS + "with_genres=27&sort_by=popularity.desc&vote_count.gte=300",
            VALENTINE = FILMS + "with_genres=10749&sort_by=popularity.desc&vote_count.gte=300",
            SUMMER = FILMS + "primary_release_date.gte={today-1825}&sort_by=revenue.desc&vote_count.gte=500",
            MORNING = FILMS + "with_genres=10751%7C16%7C35&sort_by=popularity.desc&vote_count.gte=300",
            AFTERNOON = FILMS + "with_genres=12&sort_by=popularity.desc&vote_count.gte=300",
            EVENING = FILMS + "vote_average.gte=7&vote_count.gte=3000&sort_by=popularity.desc",
            LATE = FILMS + "with_genres=53%7C27&sort_by=popularity.desc&vote_count.gte=300",
            MORNING_SHOWS = SERIES + "with_genres=35%7C16&sort_by=popularity.desc&vote_count.gte=50",
            AFTERNOON_SHOWS = SERIES + "with_genres=99&sort_by=popularity.desc&vote_count.gte=30",
            EVENING_SHOWS = SERIES + "vote_count.gte=2000&sort_by=popularity.desc",
            LATE_SHOWS = SERIES + "with_genres=80%7C9648&sort_by=popularity.desc&vote_count.gte=100",
            BINGE = SERIES + "vote_average.gte=8&vote_count.gte=1000&sort_by=vote_count.desc",
            LIMITED = SERIES + "with_keywords={miniseries/limited series}&sort_by=popularity.desc&vote_count.gte=50",
            CRIME_SHOWS = SERIES + "with_genres=80,18&sort_by=popularity.desc&vote_count.gte=100",
            COMEDY_SHOWS = SERIES + "with_genres=35&sort_by=popularity.desc&vote_count.gte=100",
            DOCS_SHOWS = SERIES + "with_genres=99&sort_by=vote_average.desc&vote_count.gte=100";

    /** The networks the TV Shows rows ask for, by TMDB's id: HBO Max (with HBO), Prime Video, Disney+. */
    static final int[] NETWORKS = {3186, 49, 1024, 2739};

    /** The genres of the day, films': TMDB's id, the row's words, the playlists' categories' words. */
    private static final Object[][] FILM_GENRES = {{28, "Action Films", "action"}, {12, "Adventures", "adventure"},
            {16, "Animation", "animation,animated,cartoon"}, {35, "Comedies", "comedy,comedies"}, {80, "Crime Films", "crime"},
            {99, "Documentaries", "documentary,documentaries"}, {18, "Dramas", "drama"}, {10751, "Family Films", "family,kids"},
            {14, "Fantasy", "fantasy"}, {36, "History", "history,historical"}, {27, "Horror", "horror"}, {9648, "Mysteries", "mystery"},
            {10749, "Romance", "romance,romantic"}, {878, "Sci-Fi", "sci-fi,science fiction"}, {53, "Thrillers", "thriller,thrillers"},
            {10752, "War Films", "war"}, {37, "Westerns", "western,westerns"}};
    /** The genres of the day, shows'. */
    private static final Object[][] SHOW_GENRES = {{10759, "Action & Adventure", "action"}, {16, "Animation", "animation,cartoon"},
            {35, "Comedy", "comedy,sitcom"}, {80, "Crime", "crime"}, {99, "Documentary", "documentary,documentaries"}, {18, "Drama", "drama"},
            {10751, "Family", "family"}, {10762, "Kids", "kids,children"}, {9648, "Mystery", "mystery"}, {10764, "Reality", "reality"},
            {10765, "Sci-Fi & Fantasy", "sci-fi,fantasy"}, {10768, "War & Politics", "war,politics"}, {37, "Westerns", "western"}};
    /** The moods of the day: the row's words and its films (a pair of genres, or keywords). */
    private static final String[][] MOODS = {
            {"feel.good", "Feel-Good Films", FILMS + "with_genres=35,10751&vote_average.gte=6.5&vote_count.gte=300&sort_by=popularity.desc"},
            {"edge", "Edge of Your Seat", FILMS + "with_genres=53,9648&vote_count.gte=500&sort_by=popularity.desc"},
            {"mind.bending", "Mind-Bending", FILMS + "with_keywords={time travel/parallel world/dystopia}&vote_count.gte=300"
                    + "&sort_by=popularity.desc"},
            {"cosy", "Cosy Night In", FILMS + "with_genres=10749,18&vote_count.gte=500&sort_by=popularity.desc"},
            {"epic", "Epic Adventures", FILMS + "with_genres=12,14&vote_count.gte=1000&sort_by=popularity.desc"},
            {"real.lives", "Real Lives", FILMS + "with_keywords={biography/based on true story}&vote_count.gte=300&sort_by=popularity.desc"},
            {"coming.of.age", "Coming of Age", FILMS + "with_keywords={coming of age}&vote_count.gte=200&sort_by=popularity.desc"}};

    /** The whole table: page, id, title, source, kind and when (none: taken in turn); a page's rows in the order listed. */
    private static final Object[][] TABLE = {
            // ---- Home: its carousel (what is trending today, with what the playlists added this week), then its rows -----
            {HOME, "hero", "", "tmdb:trending/all/day+fresh:all|new:all", HERO},
            {HOME, "continue", "Continue Watching", "continue", ROW, "first"},
            {HOME, "top10", "Top 10 Today", "top:trending/all/day", ROW, "head"},
            {HOME, "fresh", "New This Week", "fresh:all", ROW, "head"},
            {HOME, "because.0", "Because you watched {title}", "because:0", ROW, "head"},
            {HOME, "christmas", "Christmas Films", CHRISTMAS, ROW, "season:12-01/12-31"},
            {HOME, "halloween", "Halloween Frights", HALLOWEEN, ROW, "season:10-01/10-31"},
            {HOME, "valentine", "Valentine's Romance", VALENTINE, ROW, "season:02-01/02-14"},
            {HOME, "summer", "Summer Blockbusters", SUMMER, ROW, "season:06-21/08-31"},
            {HOME, "morning", "Easy Morning Watching", MORNING, ROW, "part:morning"},
            {HOME, "afternoon", "Afternoon Adventures", AFTERNOON, ROW, "part:afternoon"},
            {HOME, "evening", "Tonight's Big Films", EVENING, ROW, "part:evening"},
            {HOME, "late", "Late-Night Thrillers", LATE, ROW, "part:late"},
            {HOME, "latest.releases", "Latest Releases", LATEST_FILMS, ROW},
            {HOME, "trending.movies", "Trending Movies", "tmdb:trending/movie/day", ROW},
            {HOME, "trending.shows", "Trending TV Shows", "tmdb:trending/tv/day", ROW},
            {HOME, "latest.shows", "Latest TV Shows", LATEST_SHOWS, ROW},
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
            {HOME, "hidden.gems", "Hidden Gems", HIDDEN_FILMS, ROW},
            {HOME, "soon", "Coming Soon", SOON_FILMS + "+" + SOON_SHOWS, ROW},
            {HOME, "torbox.new", "New in Your TorBox", "torbox:all", ROW},

            // ---- Movies: the carousel, then its rows ----------------------------------------------------------------
            {MOVIES, "hero", "", "tmdb:trending/movie/day+fresh:movie|tmdb:movie/now_playing?region=GB|new:movie", HERO},
            {MOVIES, "top10", "Top 10 Films Today", "top:trending/movie/day", ROW, "head"},
            {MOVIES, "trending", "Trending This Week", "tmdb:trending/movie/week", ROW, "head"},
            {MOVIES, "fresh", "New This Week", "fresh:movie", ROW, "head"},
            {MOVIES, "christmas", "Christmas Films", CHRISTMAS, ROW, "season:12-01/12-31"},
            {MOVIES, "halloween", "Halloween Frights", HALLOWEEN, ROW, "season:10-01/10-31"},
            {MOVIES, "valentine", "Valentine's Romance", VALENTINE, ROW, "season:02-01/02-14"},
            {MOVIES, "summer", "Summer Blockbusters", SUMMER, ROW, "season:06-21/08-31"},
            {MOVIES, "morning", "Easy Morning Watching", MORNING, ROW, "part:morning"},
            {MOVIES, "afternoon", "Afternoon Adventures", AFTERNOON, ROW, "part:afternoon"},
            {MOVIES, "evening", "Tonight's Big Films", EVENING, ROW, "part:evening"},
            {MOVIES, "late", "Late-Night Thrillers", LATE, ROW, "part:late"},
            {MOVIES, "latest", "Latest Releases", LATEST_FILMS, ROW},
            {MOVIES, "because.0", "Because you watched {title}", "because:0:movie", ROW},
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
            {MOVIES, "hidden.gems", "Hidden Gems", HIDDEN_FILMS, ROW},
            {MOVIES, "oscars", "Award Winners: Best Picture", OSCARS, ROW},
            {MOVIES, "soon", "Coming Soon", SOON_FILMS, ROW},
            {MOVIES, "torbox.new", "New in Your TorBox", "torbox:movie", ROW},

            // ---- TV Shows: the carousel, then its rows -------------------------------------------------------------
            // The other app also has rows for Apple's and Netflix's services: left out here, as their names and marks may
            // not appear in Homefield.
            {SHOWS, "hero", "", "tmdb:trending/tv/day+fresh:tv|tmdb:tv/on_the_air|new:tv", HERO},
            {SHOWS, "top10", "Top 10 Shows Today", "top:trending/tv/day", ROW, "head"},
            {SHOWS, "trending", "Trending This Week", "tmdb:trending/tv/week", ROW, "head"},
            {SHOWS, "new.episodes", "New Episodes This Week", "tmdb:tv/on_the_air", ROW, "head"},
            {SHOWS, "christmas", "Christmas Specials", CHRISTMAS_SHOWS, ROW, "season:12-01/12-31"},
            {SHOWS, "morning", "Light & Easy", MORNING_SHOWS, ROW, "part:morning"},
            {SHOWS, "afternoon", "Real Life & Documentaries", AFTERNOON_SHOWS, ROW, "part:afternoon"},
            {SHOWS, "evening", "Tonight's Big Shows", EVENING_SHOWS, ROW, "part:evening"},
            {SHOWS, "late", "Late-Night Crime & Mystery", LATE_SHOWS, ROW, "part:late"},
            {SHOWS, "network.3186", "Latest {network} Shows", "latest:3186/49", ROW},
            {SHOWS, "network.1024", "Latest {network} Shows", "latest:1024", ROW},
            {SHOWS, "network.2739", "{network} Shows", "network:2739", ROW},
            {SHOWS, "latest.shows", "Latest TV Shows", LATEST_SHOWS, ROW},
            {SHOWS, "because.0", "Because you watched {title}", "because:0:tv", ROW},
            {SHOWS, "foryou", "We think you'll like these", "foryou:tv", ROW},
            {SHOWS, "hidden.gems", "Hidden Gem Series", HIDDEN_SHOWS, ROW},
            {SHOWS, "binge", "Binge-Worthy", BINGE, ROW},
            {SHOWS, "limited", "Limited Series", LIMITED, ROW},
            {SHOWS, "crime", "Crime Dramas", CRIME_SHOWS, ROW},
            {SHOWS, "comedy", "Comedy Series", COMEDY_SHOWS, ROW},
            {SHOWS, "docs", "Docuseries", DOCS_SHOWS, ROW},
            {SHOWS, "soon", "Coming Soon", SOON_SHOWS, ROW},
            {SHOWS, "fresh", "New in Your Playlist", "fresh:tv", ROW},
            {SHOWS, "torbox.new", "New in Your TorBox", "torbox:tv", ROW},
    };

    /**
     * Every row of a page, in the table's order (HERO first where the page has one), the genres and moods of the day
     * among them; which a day shows, and their order, is DayRows'.
     */
    public static List<Entry> of(String page) {
        List<Entry> out = new ArrayList<>();
        int order = 0;
        for (Object[] r : TABLE) {
            if (!r[0].equals(page)) continue;
            order += 10;
            out.add(new Entry((String) r[0], (String) r[1], (String) r[2], (String) r[3], order, (Integer) r[4], r.length > 5 ? (String) r[5] : ""));
        }
        boolean shows = page.equals(SHOWS);
        for (Object[] g : shows ? SHOW_GENRES : FILM_GENRES) {
            order += 10;
            int id = (Integer) g[0];
            String source = shows ? "genre:tv=" + id + ";words=" + g[2] : "genre:movie=" + id + ";words=" + g[2];
            out.add(new Entry(page, "genre." + id, "Genre of the Day: " + g[1], source, order, ROW, "pick:genre"));
        }
        if (!shows) {
            for (String[] m : MOODS) {
                order += 10;
                out.add(new Entry(page, "mood." + m[0], "Mood of the Day: " + m[1], m[2], order, ROW, "pick:mood"));
            }
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

    private static final String[] TMDB_KINDS = {"tmdb:", "list:", "network:", "latest:", "because:", "foryou:", "genre:", "top:", "soon:"};

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
