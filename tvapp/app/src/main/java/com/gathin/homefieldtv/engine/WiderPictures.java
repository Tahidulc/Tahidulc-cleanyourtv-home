package com.gathin.homefieldtv.engine;

import android.content.Context;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A programme's picture searched wider when Sky's public guide has none (3.4, the family's point 3: "expand your search to
 * find a picture that will fit the programme through the programme's title and description and what channel it's on"):
 * TMDB (as the app already asks it, with the launcher's key and the apps' plain user agent), by the programme's title
 * tidied ("New:", "Live:", its episode's numbers and a year left out), the year or the series its description gives
 * ("(2019)", "Series 3, episode 4", "S3 E4"), and the channel's kind (a film channel's a film first, a children's or
 * entertainment channel's a series first; a live match or a news bulletin isn't searched): a match by the same name, its
 * wide picture (else its poster). What TMDB said is kept on the TV a fortnight (Tmdb) and the answers in memory, so a
 * title is asked about once; at most one search every SPACING_MS and BURST in a few minutes (the guide asks for every
 * programme it shows), never on the main thread; nothing asked for what names no programme of its own (PicQuery.worth,
 * shared with the phone: a game, a bulletin, a closedown, the channel's own name). Null: not asked now (no key, or too
 * soon), "" none found.
 */
public final class WiderPictures {
    private WiderPictures() {}

    /** Searches kept apart this long, and at most BURST of them in WINDOW_MS. */
    static final long SPACING_MS = 1_200L, WINDOW_MS = 5 * 60_000L;
    static final int BURST = 40;

    private static final Map<String, String> found = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) {
            return size() > 600;
        }
    };
    private static long last, windowAt;
    private static int inWindow;

    /** What a programme is, for the search: its name tidied, a year, whether it is a series, whether to search at all. */
    static final class Ask {
        final String name;
        final int year;
        /** 1: a series first, -1: a film first, 0: either. */
        final int series;
        final boolean search;

        Ask(String name, int year, int series, boolean search) {
            this.name = name;
            this.year = year;
            this.series = series;
            this.search = search;
        }

        String key() {
            return name.toLowerCase(Locale.ROOT) + "|" + year + "|" + series;
        }
    }

    private static final Pattern LEAD = Pattern.compile("(?i)^\\s*(?:new|live|premiere|film|movie|brand new|season premiere|"
            + "series premiere|repeat|catch up)\\s*[:\\-–]\\s*");
    private static final Pattern EPISODE = Pattern.compile("(?i)[\\s,:\\-–]*\\(?\\s*(?:s(?:eries)?\\s*\\d{1,2}\\s*,?\\s*e(?:p(?:isode)?)?\\s*\\d{1,3}"
            + "|series\\s+\\d{1,2}|season\\s+\\d{1,2}|episode\\s+\\d{1,3}|ep\\.?\\s*\\d{1,3})\\s*\\)?.*$");
    private static final Pattern YEAR_IN_NAME = Pattern.compile("\\s*[\\[(]((?:19|20)\\d{2})[\\])]\\s*$");
    private static final Pattern YEAR_IN_DESC = Pattern.compile("\\(((?:19|20)\\d{2})\\)|\\b(?:from|in|released in)\\s+((?:19|20)\\d{2})\\b");
    private static final Pattern SERIES_IN_DESC = Pattern.compile("(?i)\\b(?:series|season)\\s+\\d{1,2}\\b|\\bs\\d{1,2}\\s*,?\\s*e\\d{1,3}\\b|\\bepisode\\s+\\d{1,3}\\b");
    private static final Pattern FILM_IN_DESC = Pattern.compile("(?i)\\b(?:film|movie|feature)\\b");
    private static final Pattern MATCH = Pattern.compile("(?i)\\s(?:v|vs\\.?)\\s");

    /** Pure: what to search for a programme (its title and description) on a channel of a kind (Genres). */
    static Ask ask(String title, String desc, String kind) {
        String t = title == null ? "" : title.trim();
        t = LEAD.matcher(t).replaceFirst("");
        int year = 0;
        Matcher y = YEAR_IN_NAME.matcher(t);
        if (y.find()) {
            year = Integer.parseInt(y.group(1));
            t = t.substring(0, y.start()).trim();
        }
        boolean episodeInName = EPISODE.matcher(t).find();
        t = EPISODE.matcher(t).replaceFirst("").trim();
        String d = desc == null ? "" : desc;
        if (year == 0) {
            Matcher m = YEAR_IN_DESC.matcher(d);
            if (m.find()) year = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
        }
        boolean seriesHint = episodeInName || SERIES_IN_DESC.matcher(d).find();
        boolean filmHint = FILM_IN_DESC.matcher(d).find();
        int series;
        if (seriesHint) series = 1;
        else if (Genres.MOVIES.equals(kind) || filmHint) series = -1;
        else if (Genres.KIDS.equals(kind) || Genres.ENTERTAINMENT.equals(kind) || Genres.DOCUMENTARIES.equals(kind)) series = 1;
        else series = 0;
        // A live match or a news bulletin has no picture of its own to find; a name too short to say anything either.
        boolean search = t.length() >= 3 && !(Genres.SPORT.equals(kind) && MATCH.matcher(" " + t + " ").find())
                && !(Genres.NEWS.equals(kind) && t.toLowerCase(Locale.ROOT).contains("news"));
        return new Ask(t, year, series, search);
    }

    /**
     * The picture for a programme with none of its own (off the main thread): its address, "" for none found, null when it
     * can't be asked now (no TMDB key, or the searches are spaced out: asked again another time).
     */
    public static String find(Context c, String title, String desc, String channelName, String group) {
        Ask a = ask(title, desc, Genres.of(group, channelName));
        // Not a game, a bulletin, a closedown or the channel's own name (the shared rule, as the phone has it).
        if (!a.search || !com.gathin.broadcast.PicQuery.worth(title, channelName,
                com.gathin.broadcast.PicSources.kindOf(channelName, group, title, desc, ""))) return "";
        String k = a.key();
        synchronized (found) {
            String had = found.get(k);
            if (had != null) return had;
        }
        if (!Tmdb.ready(c)) return null;
        // Kept from before on the TV (Tmdb's own answers): no search needed, nothing spent.
        String kept = keptFor(c, a);
        if (kept == null) {
            if (!mayAsk(System.currentTimeMillis())) return null;
            kept = search(c, a);
            if (kept == null) return null;
        }
        synchronized (found) {
            found.put(k, kept);
        }
        return kept;
    }

    /** What TMDB said of it before, "" for nothing such, null when it hasn't been asked (or long ago). */
    private static String keptFor(Context c, Ask a) {
        Tmdb.Match first = null, second = null;
        boolean tvFirst = a.series >= 0;
        first = Tmdb.cached(c, tvFirst, a.name, a.year);
        if (first != null && first.id > 0) return pictureOf(first);
        second = Tmdb.cached(c, !tvFirst, a.name, a.year);
        if (second != null && second.id > 0) return pictureOf(second);
        return first != null && second != null ? "" : null;
    }

    /** Asked now: the kind it most likely is first, then the other. Null when it couldn't be asked. */
    private static String search(Context c, Ask a) {
        boolean tvFirst = a.series >= 0;
        Tmdb.Match m = Tmdb.find(c, tvFirst, a.name, a.year);
        if (m == null) return null;
        if (m.id > 0) return pictureOf(m);
        m = Tmdb.find(c, !tvFirst, a.name, a.year);
        if (m == null) return null;
        return m.id > 0 ? pictureOf(m) : "";
    }

    private static String pictureOf(Tmdb.Match m) {
        return !m.backdrop.isEmpty() ? m.backdrop : m.poster;
    }

    /** Whether a search may go now (spaced SPACING_MS, at most BURST in WINDOW_MS); counted when it may. */
    static synchronized boolean mayAsk(long now) {
        if (now - windowAt > WINDOW_MS || now < windowAt) {
            windowAt = now;
            inWindow = 0;
        }
        if (now - last < SPACING_MS && now >= last || inWindow >= BURST) return false;
        last = now;
        inWindow++;
        return true;
    }

    /** Tests: forgotten. */
    static synchronized void forget() {
        last = 0;
        windowAt = 0;
        inWindow = 0;
        synchronized (found) {
            found.clear();
        }
    }
}
