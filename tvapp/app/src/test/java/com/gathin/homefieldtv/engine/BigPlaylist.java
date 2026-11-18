package com.gathin.homefieldtv.engine;

import android.content.Context;

import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.Parental;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A family's playlist at its largest, made up for the timing test (BigPlaylistTest): 50,000 live channels in 400
 * groups, a guide of 2,000 of them over 24 hours, 30,000 films in 300 categories and 10,000 series in 100. Every name
 * is invented, every address .invalid; none of it ships.
 */
public final class BigPlaylist {
    private BigPlaylist() {}

    public static final int CHANNELS = 50_000, GROUPS = 400, GUIDE_EVERY = 25, HOURS = 24;
    public static final int FILMS = 30_000, FILM_CATEGORIES = 300, SERIES = 10_000, SERIES_CATEGORIES = 100;

    private static final String[] LANDS = {"Ardenvale", "Brookmere", "Calderby", "Dunmarsh", "Elmstead", "Farrowdale", "Glenhaven",
            "Hollinsford", "Ivymoor", "Kelbrook", "Linmouth", "Mossgate", "Netherby", "Orrinsby", "Pennwick", "Quarrydon", "Rosmarket",
            "Saltmere", "Tarnfield", "Ulverby", "Vexley", "Wraydale", "Yarrowby", "Zennor Cove", "Ashcombe", "Birchholt", "Coldwater",
            "Dovecliff", "Eastmarch", "Fennick", "Greywell", "Hartsmoor", "Inchbrae", "Juniper Bay", "Kingsmere", "Larkhaven",
            "Millbeck", "Northwold", "Oakmere", "Pikestone", "Quillby", "Redbourne", "Stillwater", "Thornbury Vale", "Upperfold",
            "Valebridge", "Westerholm", "Yewdale", "Brackenmoor", "Copperleigh"};
    /** Eight kinds a land: 50 lands x 8 = the 400 groups. */
    private static final String[] KINDS = {"Sports", "News", "Kids", "Movies", "Entertainment", "Documentaries", "Music", "General"};
    private static final String[] BRANDS = {"Kestrel", "Lantern", "Marlowe", "Brightwater", "Copperfield", "Harbourlight", "Fernhill",
            "Oakhurst", "Bluefinch", "Sundial", "Pebblebrook", "Quayside", "Thistledown", "Larkspur", "Redwing", "Moorhen", "Willowby",
            "Stonechat", "Northgate", "Ashgrove"};
    private static final String[] WORDS = {"Sports", "News", "Cinema", "Kids", "Life", "Gold", "Drama", "Arena", "Tunes", "Nature",
            "History", "Comedy", "Plus", "One", "Two", "Max", "Classic", "Action", "Family", "Travel"};
    private static final String[] TAGS = {"", " HD", " FHD", " 4K", " SD", " +1"};
    private static final String[] SHOW = {"Morning", "Evening", "Weekend", "Late", "Coastal", "Kitchen", "Garden", "Mountain", "River",
            "City", "Island", "Harbour", "Village", "Workshop", "Market"};
    private static final String[] PART = {"Live", "Report", "Tales", "Watch", "Rescue", "Challenge", "Journeys", "Club", "Quiz",
            "Diaries", "Stories", "Hour", "Round-up", "Special", "Show"};
    private static final String[] CLUBS = {"Ashby Rovers", "Calderby Town", "Dunmarsh Athletic", "Kelbrook United", "Mossgate City",
            "Redbourne Wanderers"};
    private static final String[] FILM_A = {"Silent", "Crimson", "Last", "Hidden", "Golden", "Broken", "Northern", "Electric", "Paper",
            "Winter", "Distant", "Second", "Lucky", "Iron", "Velvet", "Hollow", "Bright", "Final", "Wild", "Lost"};
    private static final String[] FILM_B = {"Harbour", "Orchard", "Signal", "Lantern", "Meridian", "Compass", "Orbit", "Avenue",
            "Garden", "Summit", "Frontier", "Archive", "Station", "Voyage", "Echo", "Bridge", "Canyon", "Letter", "Tide", "Engine"};

    public static String groupOf(int i) {
        int g = i / (CHANNELS / GROUPS);
        return LANDS[g / KINDS.length] + " | " + KINDS[g % KINDS.length];
    }

    public static String keyOf(int i) {
        return "x:" + (100_000 + i);
    }

    /** The channels, group by group as a provider lists them, numbered 1 on. */
    public static List<Channel> channels() {
        List<Channel> out = new ArrayList<>(CHANNELS);
        for (int i = 0; i < CHANNELS; i++) {
            String name = BRANDS[i % BRANDS.length] + " " + WORDS[(i / 7) % WORDS.length] + " " + (i % 97 + 1) + TAGS[i % TAGS.length];
            out.add(new Channel(keyOf(i), name, groupOf(i), "", "http://provider.invalid/live/" + i + ".ts",
                    i % GUIDE_EVERY == 0 ? "g" + i : "", null, null, i + 1));
        }
        return out;
    }

    /**
     * The same 50,000 channels with each one in three qualities side by side in its group (FHD, HD, SD; One row per
     * channel makes them about 16,700 rows), numbered 1 on, every third with a guide id its versions share.
     */
    public static List<Channel> inQualities() {
        String[] q = {" FHD", " HD", " SD"};
        List<Channel> out = new ArrayList<>(CHANNELS);
        for (int i = 0; i < CHANNELS; i++) {
            int b = i / 3;
            String name = BRANDS[b % BRANDS.length] + " " + WORDS[(b / 7) % WORDS.length] + " " + (b % 97 + 1) + q[i % 3];
            out.add(new Channel(keyOf(i), name, groupOf(i), "", "http://provider.invalid/live/" + i + ".ts",
                    b % GUIDE_EVERY == 0 ? "g" + b : "", null, null, i + 1));
        }
        return out;
    }

    /** 24 hours of half-hour programmes (12 back, 12 on) for every 25th channel: 2,000 channels. */
    public static Map<String, Guide.Schedule> guide(List<Channel> chans, long now) {
        Map<String, Guide.Schedule> out = new HashMap<>();
        long half = 30 * 60_000L, from = now - now % half - HOURS / 2 * 3600_000L;
        for (int i = 0; i < chans.size(); i += GUIDE_EVERY) {
            Guide.Builder b = new Guide.Builder();
            int k = 0;
            for (long at = from; at < from + HOURS * 3600_000L; at += half, k++) {
                boolean sport = groupOf(i).endsWith("Sports") && k % 4 == 0;
                String title = sport ? "Live: " + CLUBS[(i + k) % CLUBS.length] + " v " + CLUBS[(i + k + 1) % CLUBS.length]
                        : SHOW[(i / GUIDE_EVERY + k) % SHOW.length] + " " + PART[(i + 3 * k) % PART.length];
                b.add(at, at + half, title, "A made-up programme about the " + SHOW[k % SHOW.length].toLowerCase(java.util.Locale.ROOT)
                        + " and the people who keep it going.");
            }
            out.put(chans.get(i).key, b.build());
        }
        return out;
    }

    /** The same guide with the sport channels left out of it: nothing sport is on now. */
    public static Map<String, Guide.Schedule> guideWithoutSport(List<Channel> chans, long now) {
        Map<String, Guide.Schedule> out = new HashMap<>(guide(chans, now));
        for (int i = 0; i < chans.size(); i += GUIDE_EVERY) if (groupOf(i).endsWith("Sports")) out.remove(chans.get(i).key);
        return out;
    }

    /** The films and series, in the catalogue's file format (Catalog.useForPreview). */
    public static List<String> catalogue(long now) {
        List<String> rows = new ArrayList<>(FILMS + SERIES + 500);
        for (int c = 0; c < FILM_CATEGORIES; c++) rows.add("c\tm\t" + c + "\t" + LANDS[c % LANDS.length] + " | Films " + (c + 1));
        for (int c = 0; c < SERIES_CATEGORIES; c++) rows.add("c\ts\t" + c + "\t" + LANDS[c % LANDS.length] + " | Series " + (c + 1));
        for (int i = 0; i < FILMS; i++) {
            int year = 1980 + i % 47;
            String name = FILM_A[i % FILM_A.length] + " " + FILM_B[(i / FILM_A.length) % FILM_B.length] + " " + (i / 400 + 1) + " (" + year + ")";
            long added = now - (long) i * 3_600_000L;
            rows.add("m\t" + i + "\t" + name + "\t" + (i % FILM_CATEGORIES) + "\t\t" + (5 + i % 5) + ".1\t" + added + "\tmkv\t" + year + "\t\t\t");
        }
        for (int i = 0; i < SERIES; i++) {
            int year = 1990 + i % 37;
            String name = "The " + FILM_B[i % FILM_B.length] + " " + SHOW[(i / FILM_B.length) % SHOW.length] + " " + (i / 300 + 1);
            long added = now - (long) i * 7_200_000L;
            rows.add("s\t" + i + "\t" + name + "\t" + (i % SERIES_CATEGORIES) + "\t\t7.2\t" + added + "\t" + year + "\tA made-up series.\t");
        }
        return rows;
    }

    /**
     * All of it loaded into the app as if just read from a provider, with what a family sets: 50 favourites, 11 recent
     * channels, two categories hidden, three locked behind a PIN (asked for, not entered), places left in films.
     */
    public static void load(Context c) throws Exception {
        Sources.clear(c);
        Sources.put(c, "p0", "Big", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "made-up"));
        // Sky's list of channels as if read already: nothing asked of the network for programme pictures.
        try (FileOutputStream o = new FileOutputStream(new File(c.getCacheDir(), "sky_services.json"))) {
            o.write("{\"services\":[]}".getBytes(StandardCharsets.UTF_8));
        }
        long now = System.currentTimeMillis();
        List<Channel> chans = channels();
        Library lib = Library.get(c);
        lib.useForPreview(chans, guide(chans, now));
        List<String> favs = new ArrayList<>();
        for (int i = 0; i < 50; i++) favs.add(keyOf(i * 997 % CHANNELS));
        List<String> recent = new ArrayList<>();
        for (int i = 0; i < 11; i++) recent.add(keyOf(CHANNELS - 1 - i * 1_234));
        lib.setPicks(favs, recent);
        Categories.setHidden(c, groupOf(CHANNELS / 2), true);
        Categories.setHidden(c, groupOf(CHANNELS - 1), true);
        Parental.setPin(c, "2468");
        Parental.lockGroup(c, groupOf(1_000), true);
        Parental.lockGroup(c, groupOf(20_000), true);
        Parental.lockGroup(c, groupOf(40_000), true);
        Parental.relock();
        Catalog.get(c).useForPreview(catalogue(now), new HashMap<>());
        Resume.clear(c);
        for (int i = 0; i < 30; i++) {
            Resume.save(c, new Resume.Point("p0|m|" + (i * 911 % FILMS), "", "Film " + i, "", "", 20 * 60_000L, 100 * 60_000L, now - i * 60_000L));
        }
        for (int i = 0; i < 100; i++) {
            String key = "p0|m|" + (i * 37 % FILMS);
            if (!MyList.has(c, key)) MyList.toggle(c, key);
        }
    }

    /** The timing test: the index a load makes (each channel's category, its place, the groups). */
    public static Object index(List<Channel> every) {
        return new Lists.Index(every);
    }

    /** The timing test: the lists made again for what is hidden and locked now, from the index there is. */
    public static Object lists(Context c, List<Channel> every) {
        return new Lists(Library.get(c).index(), com.gathin.homefieldtv.prefs.Shown.filter(c));
    }

    /** The timing test: Home's rows found as the work thread finds them. */
    public static Object onNow(Context c) {
        return Library.get(c).onNowAt(System.currentTimeMillis());
    }

    /** Everything taken away again, so the tests after this one start from nothing. */
    public static void unload(Context c) {
        Parental.clearPin(c);
        Parental.lockGroup(c, groupOf(1_000), false);
        Parental.lockGroup(c, groupOf(20_000), false);
        Parental.lockGroup(c, groupOf(40_000), false);
        Categories.showAll(c);
        Library.get(c).clear();
        Resume.clear(c);
        Parental.forget();
        Categories.forget();
    }
}
