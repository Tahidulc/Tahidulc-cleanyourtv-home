package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
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
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the family's biggest playlist keeps in memory once it is read, structure by structure (the memory diet): 50,000
 * channels as a provider's Xtream login gives them (a logo and a stream address each, 400 groups), read back from the
 * disk copy as the app starts; the lists and the name index made from them; a guide of 2,000 channels over 24 hours with
 * the descriptions a provider's XMLTV has (as many as the guide's budget keeps), read back from its disk copy; and
 * 30,000 films and 10,000 series. Each is measured as the heap it adds once made (after the collector has run), printed,
 * and kept under a budget, so what the TV's 384 MB heap holds can't creep up again. Every name is invented, every address
 * .invalid.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class MemoryDietTest {
    static final int CHANNELS = 50_000, GROUPS = 400, GUIDE_EVERY = 25, HOURS = 24;
    /** The budgets (MB on the JVM; ART's objects are a little smaller). */
    static final double CHANNELS_MB = 24, LISTS_MB = 12, NAMES_MB = 8, GUIDE_MB = 20, CATALOG_MB = 30;

    private final Map<String, Double> sizes = new LinkedHashMap<>();
    private Context app;

    @After public void after() {
        StringBuilder b = new StringBuilder("\nRETAINED (MB, JVM heap once made)\n");
        for (Map.Entry<String, Double> e : sizes.entrySet()) b.append(String.format(Locale.ROOT, "  %-58s %8.1f%n", e.getKey(), e.getValue()));
        System.out.println(b);
        Exec.inline = false;
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
        }
    }

    /** The heap in use once the collector has run (bytes). */
    static long used() {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 6; i++) {
            System.gc();
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Runtime r = Runtime.getRuntime();
            best = Math.min(best, r.totalMemory() - r.freeMemory());
        }
        return best;
    }

    private double mb(long bytes) {
        return bytes / 1048576.0;
    }

    private static final String[] LANDS = {"Ardenvale", "Brookmere", "Calderby", "Dunmarsh", "Elmstead", "Farrowdale", "Glenhaven",
            "Hollinsford", "Ivymoor", "Kelbrook"};
    private static final String[] KINDS = {"Sports", "News", "Kids", "Movies", "Entertainment", "Documentaries", "Music", "General"};
    private static final String[] BRANDS = {"Kestrel", "Lantern", "Marlowe", "Brightwater", "Copperfield", "Harbourlight", "Fernhill",
            "Oakhurst", "Bluefinch", "Sundial"};
    private static final String[] WORDS = {"Sports", "News", "Cinema", "Kids", "Life", "Gold", "Drama", "Arena", "Tunes", "Nature"};
    private static final String[] TAGS = {"", " HD", " FHD", " 4K", " SD", " +1"};

    static String group(int i) {
        int g = i / (CHANNELS / GROUPS);
        return "UK| " + LANDS[g % LANDS.length] + " " + KINDS[g % KINDS.length] + " " + (g / 10 + 1);
    }

    /** The channels' disk copy (channels.tsv) as Library writes it for an Xtream login, under the playlist's fingerprint. */
    private void writeChannels(String fingerprint) throws Exception {
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Library.fileOf(Library.get(app), "channels.tsv")),
                StandardCharsets.UTF_8))) {
            w.write("v2\t" + fingerprint + "\t" + System.currentTimeMillis() + "\thttp://provider.invalid:8080/xmltv.php?username=family&password=made-up\n");
            for (int i = 0; i < CHANNELS; i++) {
                String name = "UK: " + BRANDS[i % BRANDS.length] + " " + WORDS[(i / 7) % WORDS.length] + " " + (i % 97 + 1) + TAGS[i % TAGS.length];
                String logo = "http://picons.provider.invalid/logos/" + BRANDS[i % BRANDS.length].toLowerCase(Locale.ROOT) + "-"
                        + (100_000 + i) + ".png";
                String url = "http://provider.invalid:8080/live/family/made-up-pass/" + (100_000 + i) + ".ts";
                String epg = i % GUIDE_EVERY == 0 ? BRANDS[i % BRANDS.length].toLowerCase(Locale.ROOT) + (i / GUIDE_EVERY) + ".uk" : "";
                boolean kept = i % 50 == 0;
                w.write("x:" + (100_000 + i) + "\t" + name + "\t" + group(i) + "\t" + logo + "\t" + url + "\t" + epg + "\t\t\t" + (i + 1)
                        + "\t" + (kept ? "xc" : "") + "\t\t" + (kept ? 7 : 0) + "\t" + (kept ? "Europe/London" : "") + "\n");
            }
        }
    }

    /** The guide's disk copy (guide.tsv): 2,000 channels, half-hour programmes over 24 hours, descriptions as budgeted. */
    private void writeGuide(String fingerprint, long now) throws Exception {
        long half = 30 * 60_000L, from = now - now % half - HOURS / 2 * 3600_000L;
        int descChars = 0;
        try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Library.fileOf(Library.get(app), "guide.tsv")), StandardCharsets.UTF_8))) {
            w.write("v2\t" + fingerprint + "\t" + now + "\n");
            for (int i = 0; i < CHANNELS; i += GUIDE_EVERY) {
                int k = 0;
                for (long at = from; at < from + HOURS * 3600_000L; at += half, k++) {
                    String title = WORDS[(i + k) % WORDS.length] + " " + BRANDS[(i / 7 + k) % BRANDS.length] + " Tonight";
                    // As the guide keeps them: a description each (most of them different) until the budget is spent.
                    String desc = "";
                    if (descChars < Guide.DESC_BUDGET) {
                        // Each its own, as a real guide's are (series and episodes told apart by their numbers).
                        desc = "Series " + (i / GUIDE_EVERY + 1) + ", episode " + (k + 1) + ". " + BRANDS[k % BRANDS.length] + " and "
                                + BRANDS[(k + 3) % BRANDS.length] + " look back at a week in " + LANDS[i % LANDS.length]
                                + ", with the people who keep its harbour, its market and its football club going through the winter.";
                        descChars += desc.length();
                    }
                    w.write("x:" + (100_000 + i) + "\t" + at + "\t" + (at + half) + "\t" + title + "\t" + desc + "\n");
                }
            }
        }
    }

    /** The films and series as the catalogue's file has them (Catalog.useForPreview). */
    static List<String> catalogue(long now) {
        List<String> rows = new ArrayList<>(40_500);
        for (int c = 0; c < 300; c++) rows.add("c\tm\t" + c + "\tEN| " + LANDS[c % LANDS.length] + " Films " + (c + 1));
        for (int c = 0; c < 100; c++) rows.add("c\ts\t" + c + "\tEN| " + LANDS[c % LANDS.length] + " Series " + (c + 1));
        for (int i = 0; i < 30_000; i++) {
            int year = 1980 + i % 47;
            String name = "EN| " + BRANDS[i % BRANDS.length] + " " + WORDS[(i / 10) % WORDS.length] + " " + (i / 100 + 1) + " (" + year + ")";
            String poster = "http://images.provider.invalid/t/p/w600_and_h900_bestv2/m" + (500_000 + i) + "a7Fq2.jpg";
            rows.add("m\t" + (200_000 + i) + "\t" + name + "\t" + (i % 300) + "\t" + poster + "\t" + (5 + i % 5) + ".1\t"
                    + (now - (long) i * 3_600_000L) + "\tmkv\t" + year + "\t\t\t");
        }
        for (int i = 0; i < 10_000; i++) {
            int year = 1990 + i % 37;
            String name = "EN| The " + WORDS[i % WORDS.length] + " " + BRANDS[(i / 10) % BRANDS.length] + " " + (i / 300 + 1);
            String poster = "http://images.provider.invalid/t/p/w600_and_h900_bestv2/s" + (700_000 + i) + "b8Gr3.jpg";
            String plot = "In " + LANDS[i % LANDS.length] + ", a family of " + BRANDS[i % BRANDS.length]
                    + "s keeps the old harbour going through storms, feuds and one long, strange summer.";
            rows.add("s\t" + (300_000 + i) + "\t" + name + "\t" + (i % 100) + "\t" + poster + "\t7.2\t" + (now - (long) i * 7_200_000L)
                    + "\t" + year + "\t" + plot + "\t");
        }
        return rows;
    }

    @Test public void theBiggestPlaylistKeepsToItsBudgets() throws Exception {
        Exec.inline = true;
        // The library is made once for the whole run: its own context, for its files and its playlists.
        Library lib = Library.get(RuntimeEnvironment.getApplication());
        app = Library.appOf(lib);
        lib.clear();
        Sources.put(app, "p0", "Big", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "made-up"));
        String fp = Sources.fingerprint(app);
        long now = System.currentTimeMillis();
        writeChannels(fp);
        writeGuide(fp, now);

        long base = used();
        // As the app starts: the channels and the guide read back from their disk copies (Library.ensure → readCache).
        Library.forgetRead(lib);
        lib.ensure();
        assertEquals(CHANNELS, lib.count());
        long withGuide = used();
        assertEquals(CHANNELS / GUIDE_EVERY, Library.guideOf(lib).size());
        long[] g = lib.guideSize();
        long programmes = g[1], descs = g[2];
        // The guide alone: its schedules let go, measured without them, then read back from the disk copy.
        Library.setGuide(lib, java.util.Collections.emptyMap());
        long channelsOnly = used();
        Library.forgetRead(lib);
        lib.ensure();
        assertEquals(CHANNELS / GUIDE_EVERY, Library.guideOf(lib).size());
        sizes.put("channels: 50,000 (Channel, its strings, Index, lists)", mb(channelsOnly - base));
        sizes.put("guide: 2,000 channels x 24 h (" + programmes + " programmes, " + descs / 1000 + "k chars of descriptions)",
                mb(withGuide - channelsOnly));

        // The lists made again (Settings → Categories), and the name index (a channel's copies in other playlists).
        long beforeNames = used();
        lib.named("Kestrel Sports 1");
        long withNames = used();
        sizes.put("the name index (Library.named, made the first time)", mb(withNames - beforeNames));

        // The films and series.
        long beforeCatalog = used();
        Catalog.get(app).useForPreview(catalogue(now), new java.util.HashMap<>());
        assertTrue(Catalog.get(app).films().size() > 29_000);
        long withCatalog = used();
        sizes.put("catalogue: 30,000 films + 10,000 series", mb(withCatalog - beforeCatalog));
        sizes.put("everything", mb(withCatalog - base));

        assertTrue("channels " + sizes, mb(channelsOnly - base) < CHANNELS_MB + LISTS_MB);
        assertTrue("guide " + sizes, mb(withGuide - channelsOnly) < GUIDE_MB);
        assertTrue("names " + sizes, mb(withNames - beforeNames) < NAMES_MB);
        assertTrue("catalogue " + sizes, mb(withCatalog - beforeCatalog) < CATALOG_MB);
    }
}
