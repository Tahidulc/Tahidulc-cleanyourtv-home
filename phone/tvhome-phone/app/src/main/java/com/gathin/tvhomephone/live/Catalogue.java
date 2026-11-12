package com.gathin.tvhomephone.live;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the family's own playlists have, for Home's picks: every film and series in the playlists in use (an episode
 * counts for its series), by TMDB id where the provider gives one and by name and year, read once from their indexes
 * (Vod) and kept while they are unchanged. Home recommends only what this says a playlist has: nothing else is
 * suggested, and nothing is looked for anywhere else. Also the titles of this year and last, for "New in your playlist".
 */
public final class Catalogue {
    /** A film or series the playlists have: its name as given, its TMDB id (0 when not given), its year ("" unknown). */
    public static final class Row {
        public final boolean tv;
        public final String name, year;
        public final int tmdb;

        Row(boolean tv, String name, int tmdb, String year) {
            this.tv = tv;
            this.name = name;
            this.tmdb = tmdb;
            this.year = year;
        }
    }

    private final Set<Integer> films = new HashSet<>(), shows = new HashSet<>();
    /** A name made comparable (Vod.key) → the years it is there with ("" for none given). */
    private final Map<String, Set<String>> filmNames = new HashMap<>(), showNames = new HashMap<>();
    private final List<Row> recent = new ArrayList<>();
    private final Set<String> recentKeys = new HashSet<>();
    private final int fromYear;
    private int count;

    /** An empty catalogue keeping titles from fromYear on as recent. Pure. */
    public Catalogue(int fromYear) {
        this.fromYear = fromYear;
    }

    /** One index row: its kind ("movie", "series" or "episode"), name, TMDB id and year as the index has them. Pure. */
    public void add(String kind, String name, String tmdb, String year) {
        if (name == null || name.isEmpty()) return;
        boolean tv = "series".equals(kind) || "episode".equals(kind);
        if (!tv && !"movie".equals(kind)) return;
        String k = Vod.key(name);
        if (k.isEmpty()) return;
        String y = year == null || year.isEmpty() ? Vod.year(name) : year;
        int id = number(tmdb);
        if (id > 0) (tv ? shows : films).add(id);
        Map<String, Set<String>> names = tv ? showNames : filmNames;
        Set<String> years = names.get(k);
        if (years == null) {
            years = new HashSet<>(2);
            names.put(k, years);
            count++;
        }
        years.add(y);
        if (number(y) >= fromYear && recentKeys.add((tv ? "s:" : "m:") + k)) recent.add(new Row(tv, name, id, y));
    }

    /**
     * Whether a playlist has the title: its TMDB id, else its name (or original name) with a year no more than one
     * apart, either year missing allowed (as Vod.find matches). Pure.
     */
    public boolean has(int tmdb, boolean tv, String title, String original, String year) {
        if (tmdb > 0 && (tv ? shows : films).contains(tmdb)) return true;
        Map<String, Set<String>> names = tv ? showNames : filmNames;
        for (String n : new String[]{title, original}) {
            if (n == null || n.isEmpty()) continue;
            Set<String> years = names.get(Vod.key(n));
            if (years == null) continue;
            int want = number(year);
            for (String y : years) {
                int have = number(y);
                if (want == 0 || have == 0 || Math.abs(want - have) <= 1) return true;
            }
        }
        return false;
    }

    /** The films (tv false) or series of this year and last, newest first, at most max. Pure. */
    public List<Row> newest(boolean tv, int max) {
        List<Row> out = new ArrayList<>();
        for (Row r : recent) if (r.tv == tv) out.add(r);
        // Newest year first; within a year, as the playlist lists them.
        out.sort((a, b) -> Integer.compare(number(b.year), number(a.year)));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** How many films and series it has. */
    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    private static int number(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- the playlists' own, kept while unchanged ---------------------------------------------------------------

    private static Catalogue kept;
    private static String keptFor = "";

    /** The playlists in use now: read from their indexes (Vod) when they changed. Off the main thread. */
    public static Catalogue of(Context c) {
        List<Sources.Entry> use = Sources.inUse(c);
        List<File> files = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (Sources.Entry e : use) {
            File f = Vod.index(c, e);
            files.add(f);
            sig.append(f.getName()).append(':').append(f.lastModified()).append(':').append(f.length()).append(';');
        }
        synchronized (Catalogue.class) {
            if (kept != null && keptFor.contentEquals(sig)) return kept;
        }
        Catalogue cat = new Catalogue(java.time.LocalDate.now().getYear() - 1);
        for (File f : files) read(f, cat);
        synchronized (Catalogue.class) {
            kept = cat;
            keptFor = sig.toString();
        }
        return cat;
    }

    private static void read(File f, Catalogue into) {
        if (!f.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            r.readLine();
            String line, last = "";
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 11) continue;
                // A series' episodes come together: its name is read once.
                if ("episode".equals(p[0]) && p[1].equals(last)) continue;
                last = "episode".equals(p[0]) ? p[1] : "";
                into.add(p[0], p[1], p[8], p[9]);
            }
        } catch (IOException ignored) {
            // what was read
        }
    }
}
