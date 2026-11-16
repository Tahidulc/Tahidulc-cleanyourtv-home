package com.gathin.homefieldtv.sports;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The football game a TV programme is showing, for the player's Match night tile: the guide's title and description
 * ("Live Champions League: AEK Athens v Real Madrid") matched to one of today's games in the competitions the app
 * reads, by both clubs' names (ESPN's full and short names, the usual short forms such as "Man Utd") and the kick-off
 * falling inside the programme. Nothing is guessed from one club alone.
 */
public final class OnAir {
    private OnAir() {}

    /** "X v Y", "X vs Y", "X vs. Y", "X versus Y": what a fixture's title looks like. */
    private static final Pattern FIXTURE = Pattern.compile("\\S\\s+(v|vs\\.?|versus)\\s+\\S", Pattern.CASE_INSENSITIVE);

    /** Words that tell two clubs apart badly ("Manchester City", "Leicester City"), left out of a name's own words. */
    private static final Set<String> COMMON = new HashSet<>(java.util.Arrays.asList("city", "united", "town", "real", "club",
            "sporting", "athletic", "athletico", "rovers", "county", "wanderers", "albion", "olympique", "saint", "sport",
            "deportivo", "borussia", "football", "calcio", "association", "de", "la", "le", "the", "and", "fc", "cf", "sc", "ac",
            "afc", "united", "hotspur", "rangers", "celtic", "villa"));

    /** Whether a title reads as a fixture ("Arsenal v Chelsea"). Pure. */
    public static boolean looksLikeFixture(String text) {
        return text != null && FIXTURE.matcher(text).find();
    }

    /** Today's games (and yesterday's, for a programme running past midnight) in every competition read. Off the main thread. */
    public static List<Game> today(long freshMs) {
        List<Game> out = new ArrayList<>();
        LocalDate day = LocalDate.now(ZoneId.systemDefault());
        for (String[] l : Espn.LEAGUES) {
            out.addAll(Espn.scoreboard(l[0], day, freshMs));
            if (java.time.LocalTime.now().getHour() < 4) out.addAll(Espn.scoreboard(l[0], day.minusDays(1), freshMs));
        }
        return out;
    }

    /**
     * The game this programme shows, or null: both clubs named in its title or description, the kick-off from half an
     * hour before the programme starts to its end (start and stop 0: any time today), the nearest kick-off winning.
     * Pure.
     */
    public static Game match(String title, String desc, long start, long stop, List<Game> games) {
        if (games == null || games.isEmpty()) return null;
        String text = words(title, desc);
        Game best = null;
        long bestGap = Long.MAX_VALUE;
        for (Game g : games) {
            if (g == null) continue;
            if (start > 0 && stop > start && (g.kickoff < start - 30 * 60_000L || g.kickoff > stop)) continue;
            if (!named(text, g.homeFull, g.homeName, g.home) || !named(text, g.awayFull, g.awayName, g.away)) continue;
            long gap = start > 0 ? Math.abs(g.kickoff - start) : 0;
            if (best == null || gap < bestGap) {
                best = g;
                bestGap = gap;
            }
        }
        return best;
    }

    /**
     * As match, and failing that one club named in a programme that reads as a fixture ("Live: England v Croatia" where
     * the other side's name reads in a way of its own): the one game in the programme's time naming either club; null
     * when none, or more than one would do. Pure.
     */
    public static Game matchEither(String title, String desc, long start, long stop, List<Game> games) {
        Game both = match(title, desc, start, stop, games);
        if (both != null || games == null) return both;
        if (!looksLikeFixture(title) && !looksLikeFixture(desc)) return null;
        String text = words(title, desc);
        Game one = null;
        for (Game g : games) {
            if (g == null) continue;
            if (start > 0 && stop > start && (g.kickoff < start - 30 * 60_000L || g.kickoff > stop)) continue;
            if (!named(text, g.homeFull, g.homeName, g.home) && !named(text, g.awayFull, g.awayName, g.away)) continue;
            if (one != null && one != g) return null;
            one = g;
        }
        return one;
    }

    /** A programme's title and description as one cleaned line, the usual short forms read as the clubs' names. */
    private static String words(String title, String desc) {
        String text = " " + clean((title == null ? "" : title) + " " + (desc == null ? "" : desc)) + " ";
        for (String[] a : Channels.ALIASES) text = text.replace(" " + a[0] + " ", " " + a[1] + " ");
        return text;
    }

    /** Whether the text names the club: its full or short name as words, else one of its own long words. */
    static boolean named(String text, String full, String shortName, String abbreviation) {
        for (String n : new String[]{full, shortName}) {
            String c = clean(n);
            if (c.length() >= 3 && text.contains(" " + c + " ")) return true;
        }
        // A three-letter short form only as itself in capitals would be too loose ("MAN", "LIV"): words of four letters
        // or more that are the club's own ("madrid", "athens", "arsenal").
        for (String n : new String[]{full, shortName}) {
            for (String w : clean(n).split(" ")) {
                if (w.length() < 4 || COMMON.contains(w)) continue;
                if (text.contains(" " + w + " ")) return true;
            }
        }
        String ab = clean(abbreviation);
        return ab.length() >= 3 && ab.length() <= 4 && text.contains(" " + ab + " ") && ab.equals(clean(shortName));
    }

    /** Lower case, accents and punctuation gone, " fc" left out, single spaces. */
    static String clean(String s) {
        if (s == null) return "";
        String t = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("[^a-z0-9' ]", " ").replace("'", "");
        t = (" " + t + " ").replace(" fc ", " ").replace(" afc ", " ");
        return t.replaceAll("\\s+", " ").trim();
    }
}
