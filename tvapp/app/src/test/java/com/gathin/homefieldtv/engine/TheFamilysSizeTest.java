package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Homefield TV at the size of the family's own playlist as 3.3's memory log saw it on the Streamer (22 Nov): 198,769 films
 * and series, 27,056 channels, and a guide of 4,790 channels and 90,192 programmes, all held at once while a channel
 * plays. Each is read back from its disk copy as the app starts, measured as the heap it keeps once the collector has run,
 * printed, and kept under its budget; then the catalogue is read again as the daily refresh does, and must keep its titles
 * (one catalogue in memory, not two). And an M3U playlist with 200,000 films and episodes must go to its file without
 * being held in memory. Every name is invented, every address .invalid.
 *
 * <p>The budgets, MB of the JVM's heap (ART's objects are smaller still): the catalogue 96, the channels 16, the guide
 * 14, all of it 120; the refresh adds at most 12 (it was a second catalogue, about 100).
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class TheFamilysSizeTest {
    static final int TITLES = 198_769, SERIES_EVERY = 6, CHANNELS = 27_056, GUIDE_CHANNELS = 4_790, PROGRAMMES = 90_192;
    static final double CATALOG_MB = 96, CHANNELS_MB = 16, GUIDE_MB = 14, ALL_MB = 120, REFRESH_MB = 12;

    private final Map<String, Double> sizes = new LinkedHashMap<>();
    private Context app;

    @After public void after() {
        StringBuilder b = new StringBuilder("\nTHE FAMILY'S SIZE (MB, JVM heap once made)\n");
        for (Map.Entry<String, Double> e : sizes.entrySet()) b.append(String.format(Locale.ROOT, "  %-64s %8.1f%n", e.getKey(), e.getValue()));
        System.out.println(b);
        Exec.inline = false;
        if (app != null) {
            Catalog.get(app).clear();
            Library.get(app).clear();
            Sources.clear(app);
        }
    }

    private static double mb(long bytes) {
        return bytes / 1048576.0;
    }

    private static final String[] A = {"Silent", "Crimson", "Last", "Hidden", "Golden", "Broken", "Northern", "Electric", "Paper", "Winter",
            "Distant", "Second", "Lucky", "Iron", "Velvet", "Hollow", "Bright", "Final", "Wild", "Lost", "Quiet", "Amber", "Copper"};
    private static final String[] B = {"Harbour", "Orchard", "Signal", "Lantern", "Meridian", "Compass", "Orbit", "Avenue", "Garden",
            "Summit", "Frontier", "Archive", "Station", "Voyage", "Echo", "Bridge", "Canyon", "Letter", "Tide", "Engine", "Ferry"};
    private static final String[] LANDS = {"Ardenvale", "Brookmere", "Calderby", "Dunmarsh", "Elmstead", "Farrowdale", "Glenhaven",
            "Hollinsford", "Ivymoor", "Kelbrook", "Linmouth", "Mossgate"};
    private static final String[] TAGS = {"EN", "UK", "4K", "NF", "AMZ", "MULTI"};

    /** The catalogue's file as an Xtream login's refresh writes it: 400 categories, then the films and the series. */
    private void writeCatalogue(File f, long now) throws Exception {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8), 1 << 16)) {
            w.write("v1\txtream\t" + now + "\n");
            for (int c = 0; c < 300; c++) w.write("c\tm\t" + c + "\t" + TAGS[c % TAGS.length] + " | " + LANDS[c % LANDS.length] + " Films " + c + "\n");
            for (int c = 0; c < 100; c++) w.write("c\ts\t" + c + "\t" + TAGS[c % TAGS.length] + " | " + LANDS[c % LANDS.length] + " Series " + c + "\n");
            for (int i = 0; i < TITLES; i++) {
                int year = 1960 + i % 66;
                String name = TAGS[i % TAGS.length] + " | " + A[i % A.length] + " " + B[(i / A.length) % B.length] + " " + (i / 483 + 1);
                String poster = "http://images.provider.invalid/t/p/w600_and_h900_bestv2/" + Integer.toString(0x10000000 + i * 7919, 36)
                        + "Qx7Lm2Fk.jpg";
                long added = (now / 1000 - (long) i * 900L);
                if (i % SERIES_EVERY == 0) {
                    String plot = "In " + LANDS[i % LANDS.length] + ", the " + B[i % B.length].toLowerCase(Locale.ROOT) + " keepers of a "
                            + A[i % A.length].toLowerCase(Locale.ROOT) + " town face storms, feuds and one long summer that changes them all. "
                            + "Season " + (i % 7 + 1) + " follows the family through a winter of " + LANDS[(i + 3) % LANDS.length]
                            + " weddings, a lost boat and a secret kept for thirty years, as the harbour they love is sold off piece by piece.";
                    String backdrop = i % 3 == 0 ? "http://images.provider.invalid/t/p/w1280/" + Integer.toString(0x20000000 + i, 36) + "Bd.jpg" : "";
                    w.write("s\t" + (500_000 + i) + "\t" + name + " (" + year + ")\t" + (i % 100) + "\t" + poster + "\t" + (6 + i % 4) + ".4\t"
                            + added * 1000 + "\t" + year + "\t" + plot + "\t" + backdrop + "\n");
                } else {
                    w.write("m\t" + (1_000_000 + i) + "\t" + name + " (" + year + ")" + (i % 9 == 0 ? " 4K" : "") + "\t" + (i % 300) + "\t" + poster
                            + "\t" + (5 + i % 5) + ".1\t" + added * 1000 + "\t" + (i % 4 == 0 ? "mp4" : "mkv") + "\t" + year + "\t\t\t\n");
                }
            }
        }
    }

    /** The channels' disk copy (channels.tsv) for an Xtream login. */
    private void writeChannels(String fingerprint) throws Exception {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Library.fileOf(Library.get(app), "channels.tsv")),
                StandardCharsets.UTF_8))) {
            w.write("v2\t" + fingerprint + "\t" + System.currentTimeMillis() + "\thttp://provider.invalid:8080/xmltv.php?username=family&password=made-up\n");
            for (int i = 0; i < CHANNELS; i++) {
                String name = "UK: " + B[i % B.length] + " " + A[(i / 7) % A.length] + " " + (i % 97 + 1) + (i % 3 == 0 ? " FHD" : i % 3 == 1 ? " HD" : "");
                String logo = "http://picons.provider.invalid/logos/" + B[i % B.length].toLowerCase(Locale.ROOT) + "-" + (100_000 + i) + ".png";
                String url = "http://provider.invalid:8080/live/family/made-up-pass/" + (100_000 + i) + ".ts";
                String epg = i < GUIDE_CHANNELS ? "g" + i + ".uk" : "";
                boolean kept = i % 40 == 0;
                w.write("x:" + (100_000 + i) + "\t" + name + "\t" + "UK| " + LANDS[(i / 100) % LANDS.length] + " " + (i / 100) + "\t" + logo + "\t"
                        + url + "\t" + epg + "\t\t\t" + (i + 1) + "\t" + (kept ? "xc" : "") + "\t\t" + (kept ? 7 : 0) + "\t" + (kept ? "Europe/London" : "") + "\n");
            }
        }
    }

    /** The guide's disk copy (guide.tsv): 4,790 channels, 90,192 programmes, descriptions as the guide's budget keeps them. */
    private void writeGuide(String fingerprint, long now) throws Exception {
        long half = 30 * 60_000L, from = now - now % half - half;
        int descChars = 0, made = 0;
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Library.fileOf(Library.get(app), "guide.tsv")),
                StandardCharsets.UTF_8))) {
            w.write("v2\t" + fingerprint + "\t" + now + "\n");
            for (int i = 0; i < GUIDE_CHANNELS; i++) {
                int per = PROGRAMMES / GUIDE_CHANNELS + (i < PROGRAMMES % GUIDE_CHANNELS ? 1 : 0);
                for (int k = 0; k < per; k++, made++) {
                    long at = from + k * half;
                    String title = A[(i + k) % A.length] + " " + B[(i / 3 + k) % B.length] + (k % 3 == 0 ? " Live" : " Tonight");
                    String desc = "";
                    if (descChars < Guide.DESC_BUDGET) {
                        desc = "Series " + (i % 9 + 1) + ", episode " + (k + 1) + ". " + A[k % A.length] + " and " + B[(k + 3) % B.length]
                                + " look back at a week in " + LANDS[i % LANDS.length] + ", with the people who keep its harbour, its market "
                                + "and its football club going through the winter.";
                        descChars += desc.length();
                    }
                    w.write("x:" + (100_000 + i) + "\t" + at + "\t" + (at + half) + "\t" + title + "\t" + desc + "\n");
                }
            }
        }
        assertEquals(PROGRAMMES, made);
    }

    @Test public void heldAtOnceUnderBudget() throws Exception {
        Exec.inline = true;
        Library lib = Library.get(RuntimeEnvironment.getApplication());
        app = Library.appOf(lib);
        lib.clear();
        Catalog cat = Catalog.get(app);
        cat.clear();
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "made-up"));
        String fp = Sources.fingerprint(app);
        long now = System.currentTimeMillis();
        writeChannels(fp);
        writeGuide(fp, now);
        writeCatalogue(Catalog.file(app, "p0"), now);

        long base = MemoryDietTest.used();
        Library.forgetRead(lib);
        lib.ensure();
        assertEquals(CHANNELS, lib.count());
        long withGuide = MemoryDietTest.used();
        long[] g = lib.guideSize();
        assertEquals(PROGRAMMES, g[1]);
        Library.setGuide(lib, java.util.Collections.emptyMap());
        long channelsOnly = MemoryDietTest.used();
        Library.forgetRead(lib);
        lib.ensure();
        assertEquals(GUIDE_CHANNELS, Library.guideOf(lib).size());
        sizes.put("channels: 27,056", mb(channelsOnly - base));
        sizes.put("guide: 4,790 channels, " + g[1] + " programmes, " + g[2] / 1000 + "k chars", mb(withGuide - channelsOnly));

        long beforeCatalog = MemoryDietTest.used();
        // As the app starts (Catalog.ensure reads the files the same way).
        cat.rebuildForTests();
        int titles = cat.films().size() + cat.series().size();
        assertEquals(TITLES, titles);
        long withCatalog = MemoryDietTest.used();
        sizes.put("catalogue: " + cat.films().size() + " films + " + cat.series().size() + " series", mb(withCatalog - beforeCatalog));
        sizes.put("all of it", mb(withCatalog - base));

        // The daily refresh: the same file read again keeps the titles it has (no second catalogue meanwhile).
        Catalog.Title film = cat.films().get(1234), show = cat.series().get(321);
        cat.rebuildForTests();
        assertSame(film, cat.films().get(1234));
        assertSame(show, cat.series().get(321));
        assertSame(film, cat.title(film.key));
        long afterRefresh = MemoryDietTest.used();
        sizes.put("the refresh read again (what it adds)", mb(afterRefresh - withCatalog));

        // Still searched and found.
        List<Catalog.Title> found = cat.search(film.name, film.series, 5);
        assertTrue(found.contains(film));

        assertTrue("catalogue " + sizes, mb(withCatalog - beforeCatalog) < CATALOG_MB);
        assertTrue("channels " + sizes, mb(channelsOnly - base) < CHANNELS_MB);
        assertTrue("guide " + sizes, mb(withGuide - channelsOnly) < GUIDE_MB);
        assertTrue("all " + sizes, mb(withCatalog - base) < ALL_MB);
        assertTrue("refresh " + sizes, mb(afterRefresh - withCatalog) < REFRESH_MB);
    }

    /** An M3U playlist of 200,000 films and episodes: written to its catalogue file as it is read, not kept in memory. */
    @Test public void aBigM3uGoesToItsFileNotToMemory() throws Exception {
        Exec.inline = true;
        app = Library.appOf(Library.get(RuntimeEnvironment.getApplication()));
        StringBuilder m3u = new StringBuilder();
        m3u.append("#EXTM3U\n");
        for (int i = 0; i < 20; i++) {
            m3u.append("#EXTINF:-1 tvg-id=\"c").append(i).append("\" group-title=\"UK\",Channel ").append(i).append('\n')
                    .append("http://provider.invalid/live/u/p/").append(i).append(".ts\n");
        }
        for (int i = 0; i < 200_000; i++) {
            boolean episode = i % 4 != 0;
            String name = episode ? "The " + B[(i / 40) % B.length] + " " + A[(i / 900) % A.length] + " S0" + (i % 3 + 1) + "E" + (i % 40 + 1)
                    : A[i % A.length] + " " + B[(i / A.length) % B.length] + " " + i + " (" + (1970 + i % 50) + ")";
            m3u.append("#EXTINF:-1 tvg-logo=\"http://images.provider.invalid/p/").append(i).append(".jpg\" group-title=\"")
                    .append(episode ? "Series | Drama" : "Films | Action").append("\",").append(name).append('\n')
                    .append("http://provider.invalid/").append(episode ? "series" : "movie").append("/u/p/").append(i).append(".mkv\n");
        }
        String text = m3u.toString();
        m3u = null;
        // As before: every entry held in Result.vod until the playlist was read (measured, then let go).
        MemoryDietTest.used();
        long before = MemoryDietTest.used();
        M3u.Result held = M3u.parse(new StringReader(text));
        long holding = MemoryDietTest.used();
        assertEquals(200_000, held.vod.size());
        sizes.put("an M3U's 200,000 films and episodes held as they were (Result.vod)", mb(holding - before));
        held = null;
        // Now: each written to the file as it comes, none of them held (only the categories' and series' names are).
        M3u.Result res;
        File out = Catalog.file(app, "p9");
        try (Catalog.M3uSink sink = Catalog.m3uSink(app, "p9")) {
            res = M3u.parse(new StringReader(text), sink);
            assertTrue(res.vod.isEmpty());
            sink.save();
            assertTrue(sink.saved());
        }
        assertEquals(20, res.channels.size());
        assertTrue(res.vod.isEmpty());
        assertTrue(out.length() > 10 << 20);
        // Read back: the films, the series, and a series' episodes read from its part of the file when asked.
        Catalog.Read r = Catalog.read(out, "p9", null);
        assertEquals(50_000, r.films.size());
        assertTrue(r.episodes.isEmpty());
        Catalog.Model m = Catalog.merge(java.util.Collections.singletonList(r));
        Catalog.Title s = m.series.get(0);
        List<Catalog.Episode> eps = m.episodes.get(s.key);
        assertNotNull(eps);
        assertTrue(eps.size() > 10);
        assertTrue(eps.get(0).season <= eps.get(eps.size() - 1).season);
        //noinspection ResultOfMethodCallIgnored
        out.delete();
    }
}
