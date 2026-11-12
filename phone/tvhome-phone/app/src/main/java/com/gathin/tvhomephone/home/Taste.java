package com.gathin.tvhomephone.home;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the family likes, from what they watched (pure, for the tests): each film or series watched gives its genres
 * and its people (the leading cast, the director or creator) a weight: more for one watched to the end or loved, less
 * for one only started, and less the longer ago it was. A title is then scored by how much of that it shares, by how
 * many of the titles watched TMDB recommends it for, and a little by its rating and how popular it is now.
 */
final class Taste {
    /** Half the weight after this many days. */
    static final double HALF_LIFE_DAYS = 45;

    /** One film or series watched (or loved): what it gives the taste. */
    static final class Seen {
        final int id;
        final boolean tv;
        final String name;
        double weight;
        int[] genres = new int[0], people = new int[0];

        Seen(int id, boolean tv, String name, double weight) {
            this.id = id;
            this.tv = tv;
            this.name = name == null ? "" : name;
            this.weight = weight;
        }

        String key() {
            return (tv ? "tv:" : "mv:") + id;
        }
    }

    /** A title that could be recommended. */
    static final class Candidate {
        final int id;
        final boolean tv;
        final String title;
        int[] genres = new int[0];
        /** Its people, once its details are read; null until then. */
        int[] people;
        double vote;
        /** Its place in TMDB's trending and popular lists (0 first); large when in none. */
        int popularity = 1000;
        /** The names of the titles watched that TMDB recommends it for. */
        final Set<String> because = new LinkedHashSet<>();
        /** What it shows as (a Pick, for Home). */
        Object shown;

        Candidate(int id, boolean tv, String title) {
            this.id = id;
            this.tv = tv;
            this.title = title == null ? "" : title;
        }

        String key() {
            return (tv ? "tv:" : "mv:") + id;
        }
    }

    final Map<Integer, Double> genres = new HashMap<>(), people = new HashMap<>();
    private double topGenre, topPerson;
    private int titles;

    /**
     * The weight one title gives: loved 1.5, watched to the end 1, else a quarter plus three quarters of how far in;
     * halved every HALF_LIFE_DAYS since it was watched (at 0: now).
     */
    static double weight(boolean finished, float progress, boolean loved, long at, long now) {
        double base = loved ? 1.5 : finished ? 1.0 : 0.25 + 0.75 * Math.max(0, Math.min(1, progress));
        double days = at <= 0 ? 0 : Math.max(0, (now - at) / 86_400_000.0);
        return base * Math.pow(0.5, days / HALF_LIFE_DAYS);
    }

    static Taste of(Collection<Seen> seen) {
        Taste t = new Taste();
        for (Seen s : seen) {
            if (s.weight <= 0) continue;
            t.titles++;
            for (int g : s.genres) t.genres.merge(g, s.weight, Double::sum);
            for (int p : s.people) t.people.merge(p, s.weight, Double::sum);
        }
        for (double v : t.genres.values()) t.topGenre = Math.max(t.topGenre, v);
        for (double v : t.people.values()) t.topPerson = Math.max(t.topPerson, v);
        return t;
    }

    /** Whether there is too little watched to go on (nothing with a genre). */
    boolean empty() {
        return titles == 0 || genres.isEmpty();
    }

    /** The genres liked most, strongest first (at most n). */
    List<Integer> topGenres(int n, Set<Integer> among) {
        List<Map.Entry<Integer, Double>> all = new ArrayList<>(genres.entrySet());
        all.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, Double> e : all) {
            if (out.size() >= n) break;
            if (among == null || among.contains(e.getKey())) out.add(e.getKey());
        }
        return out;
    }

    /**
     * How well a title fits: the genres it shares (as a share of the strongest, evened out over how many it has), the
     * people it shares (each as a share of the strongest, at most two), half a point for each title watched that TMDB
     * recommends it for (at most three), then a little for a good rating and for being popular now.
     */
    double score(Candidate c) {
        double g = 0;
        if (topGenre > 0 && c.genres.length > 0) {
            for (int id : c.genres) g += genres.getOrDefault(id, 0.0);
            g = g / topGenre / Math.sqrt(c.genres.length);
        }
        double p = 0;
        if (topPerson > 0 && c.people != null) {
            for (int id : c.people) p += people.getOrDefault(id, 0.0) / topPerson;
            p = Math.min(2, p);
        }
        double because = 0.5 * Math.min(3, c.because.size());
        double rating = c.vote <= 0 ? 0 : 0.4 * Math.max(-0.5, Math.min(0.5, (c.vote - 6.5) / 3));
        double popular = 0.3 / (1 + c.popularity / 10.0);
        return g + 0.6 * p + because + rating + popular;
    }

    /**
     * The best films (tv false) or series, best first, at most max: none in skip (watched, rated down), each once.
     * With no taste yet, by how popular they are, then their rating.
     */
    List<Candidate> rank(Collection<Candidate> all, boolean tv, Set<String> skip, int max) {
        List<Candidate> pool = new ArrayList<>();
        Set<String> once = new LinkedHashSet<>();
        for (Candidate c : all) if (c.tv == tv && !skip.contains(c.key()) && once.add(c.key())) pool.add(c);
        Map<String, Double> scores = new HashMap<>();
        for (Candidate c : pool) scores.put(c.key(), empty() ? -c.popularity + c.vote / 100 : score(c));
        pool.sort((a, b) -> {
            int s = Double.compare(scores.get(b.key()), scores.get(a.key()));
            return s != 0 ? s : Double.compare(b.vote, a.vote);
        });
        return pool.size() > max ? new ArrayList<>(pool.subList(0, max)) : pool;
    }

    /** Why it is there, for its kicker: the title watched it's recommended for, else its genre most liked; null for none. */
    String why(Candidate c) {
        if (!c.because.isEmpty()) return "Because you watched " + c.because.iterator().next();
        int best = 0;
        double w = 0;
        for (int g : c.genres) {
            double v = genres.getOrDefault(g, 0.0);
            if (v > w) {
                w = v;
                best = g;
            }
        }
        String name = best == 0 ? null : genreName(best);
        return name == null ? null : "More " + name;
    }

    /** TMDB's genre names (as Tmdb.genre, without Android), films' and series' ids together. */
    static String genreName(int id) {
        switch (id) {
            case 28: return "Action";
            case 12: return "Adventure";
            case 16: return "Animation";
            case 35: return "Comedy";
            case 80: return "Crime";
            case 99: return "Documentary";
            case 18: return "Drama";
            case 10751: return "Family";
            case 14: return "Fantasy";
            case 36: return "History";
            case 27: return "Horror";
            case 10402: return "Music";
            case 9648: return "Mystery";
            case 10749: return "Romance";
            case 878: return "Sci-Fi";
            case 53: return "Thriller";
            case 10752: return "War";
            case 37: return "Western";
            case 10759: return "Action & Adventure";
            case 10762: return "Kids";
            case 10763: return "News";
            case 10764: return "Reality";
            case 10765: return "Sci-Fi & Fantasy";
            case 10766: return "Soap";
            case 10767: return "Talk";
            case 10768: return "War & Politics";
            default: return null;
        }
    }
}
