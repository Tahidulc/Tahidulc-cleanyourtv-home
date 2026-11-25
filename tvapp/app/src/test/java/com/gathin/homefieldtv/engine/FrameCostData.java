package com.gathin.homefieldtv.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The frame-cost tests' made-up family playlist (FrameCostTest): a dozen invented channels with an evening's guide, their
 * logos and programme pictures, a shelf of invented films and series with posters, backdrops and episodes, where things
 * were left and My List. Every picture is drawn here into the test's own folder (file: addresses), so nothing is asked of
 * any provider, TMDB or Sky and nothing outside the repository is needed.
 */
public final class FrameCostData {
    private FrameCostData() {}

    private static final String[] CHANNELS = {"Harbour TV", "Riverside One", "Meadow 4", "Northline", "Kestrel Movies",
            "Fieldside Sports", "Granite News", "Pebble Kids", "Tidal", "Velvet", "Lantern", "Copperfield"};
    private static final String[] GROUPS = {"Entertainment", "Entertainment", "Entertainment", "Entertainment", "Movies", "Sports",
            "News", "Kids", "Entertainment", "Entertainment", "Sports", "Entertainment"};
    private static final String[] SHOWS = {"The Evening Table", "Quiz of the Week", "Late Edition", "Kitchen Table", "Gardens Gone Wild",
            "Comedy Hour", "Copper Valley", "Signal Lost", "Match Night Live", "Atlas at Nine", "Planet Pebbles", "Night Swim"};
    private static final String[] FILMS = {"The Long Harbour", "Glass Meridian", "Ember Road", "Paper Kites", "Midnight Orchard",
            "Copper Valley", "Salt & Starlight", "Northbound", "Quiet Meridian", "Lantern Street", "Fern Hollow", "Iron Bay"};
    private static final String[] SERIES = {"Harbour Lights", "Signal Lost", "The Quiet County", "Kitchen Table", "Fern & Fable",
            "Low Tide"};
    private static final int[] COLOURS = {0xFFD7263D, 0xFF1B4FA0, 0xFF1F9E4A, 0xFFF9A825, 0xFF6A1B9A, 0xFF00838F, 0xFFFF7043,
            0xFF283593, 0xFFC2185B, 0xFF2E7D32, 0xFF5D4037, 0xFF455A64};

    public static final List<Channel> MADE = new ArrayList<>();

    /** Loads it all (pictures drawn into dir); the keys {a series, a film}. */
    public static String[] load(Context c, File dir) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        Sources.clear(c);
        Sources.put(c, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L;
        List<Channel> chans = new ArrayList<>();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        MADE.clear();
        for (int i = 0; i < CHANNELS.length; i++) {
            Channel ch = new Channel("f:" + (7000 + i), CHANNELS[i], "UK | " + GROUPS[i], logo(dir, i), "http://provider.invalid/live/" + i + ".ts",
                    "", null, null, 101 + i);
            chans.add(ch);
            MADE.add(ch);
            Guide.Builder b = new Guide.Builder();
            long at = now - (20 + i * 3) * 60_000L;
            for (int k = 0; at < now + 10 * 3600_000L; k++) {
                long stop = at + (k % 2 == 0 ? 2 : 1) * half;
                b.add(at, stop, SHOWS[(i + k) % SHOWS.length], "An evening of " + SHOWS[(i + k) % SHOWS.length].toLowerCase() + " from the coast.");
                at = stop;
            }
            guide.put(ch.key, b.build());
        }
        Library lib = Library.get(c);
        lib.useForPreview(chans, guide);
        for (int i : new int[]{0, 5, 10}) if (!lib.isFavourite(chans.get(i))) lib.toggleFavourite(chans.get(i));
        lib.watched(chans.get(0));

        List<String> rows = new ArrayList<>();
        Map<String, Catalog.Details> pages = new HashMap<>();
        rows.add("c\tm\t1\tNew releases");
        rows.add("c\tm\t2\tDrama and mystery");
        rows.add("c\ts\t1\tDrama and crime");
        rows.add("c\ts\t2\tComedy and kids");
        long added = now / 1000;
        for (int i = 0; i < FILMS.length; i++) {
            String id = String.valueOf(i + 1), cat = i % 2 == 0 ? "1" : "2";
            rows.add("m\t" + id + "\t" + FILMS[i] + " (" + (2015 + i) + ")" + (i % 3 == 0 ? " 4K" : "") + "\t" + cat + "\t" + poster(dir, "f" + i, i)
                    + "\t" + (6.5 + i % 4 * 0.4) + "\t" + (added - i * 3600L) * 1000L + "\tmkv\t" + (2015 + i) + "\t\t\t");
            pages.put("p0|m|" + id, Catalog.previewDetails("A family on the coast has one autumn to bring the boat home.", "Drama, Adventure",
                    "Rowan Vale, Iris Penn, Tom Ashby", "Ada Holt", (2015 + i) + "-05-14", backdrop(dir, "fb" + i, i), "7.4", "12A", 110, null, ""));
        }
        for (int i = 0; i < SERIES.length; i++) {
            String id = String.valueOf(i + 1);
            rows.add("s\t" + id + "\t" + SERIES[i] + "\t" + (i % 2 == 0 ? "1" : "2") + "\t" + poster(dir, "s" + i, i + 5) + "\t" + (7.1 + i % 3 * 0.3)
                    + "\t" + (added - i * 7200L) * 1000L + "\t" + (2019 + i) + "\tA harbour town keeps its secrets.\t" + backdrop(dir, "sb" + i, i + 5));
            List<String[]> eps = new ArrayList<>();
            for (int season = 1; season <= 2; season++) {
                for (int k = 1; k <= 8; k++) {
                    eps.add(new String[]{"s" + i + "-" + season + "-" + k, String.valueOf(season), String.valueOf(k), "Chapter " + k,
                            "The night the lights went out on the harbour wall.", programme(dir, (i * 16 + season * 8 + k) % 12),
                            String.valueOf(44 + k), (2019 + i + season - 1) + "-0" + Math.min(9, k) + "-1" + k});
                }
            }
            pages.put("p0|s|" + id, Catalog.previewDetails("A harbour town keeps its secrets.", "Drama, Crime", "Rowan Vale, Iris Penn, Tom Ashby",
                    "Ada Holt", String.valueOf(2019 + i), backdrop(dir, "sb" + i, i + 5), "7.9", "15", 0, eps.toArray(new String[0][]), ""));
        }
        Catalog.get(c).useForPreview(rows, pages);
        Resume.clear(c);
        Resume.save(c, new Resume.Point("p0|m|8", "", FILMS[7], "", backdrop(dir, "fb7", 7), 52 * 60_000L, 127 * 60_000L, now - 3600_000L));
        Resume.save(c, new Resume.Point("p0|m|2", "", FILMS[1], "", backdrop(dir, "fb1", 1), 40 * 60_000L, 132 * 60_000L, now - 7200_000L));
        Resume.save(c, new Resume.Point("p0|s|1", "s0-2-4", SERIES[0], "S2 E4  ·  Chapter 4", programme(dir, 3), 27 * 60_000L, 48 * 60_000L, now));
        for (String k : new String[]{"p0|m|2", "p0|s|3", "p0|s|1", "p0|m|1", "p0|m|5", "p0|m|7"}) if (!MyList.has(c, k)) MyList.toggle(c, k);
        return new String[]{"p0|s|1", "p0|m|2"};
    }

    /** Channel ch's programme i's drawn picture. */
    public static String programmeOf(File dir, int ch, int i) throws IOException {
        return programme(dir, (ch * 5 + i) % 12);
    }

    // ---- drawn pictures ---------------------------------------------------------------------------------------------

    private static String logo(File dir, int i) throws IOException {
        Bitmap b = Bitmap.createBitmap(220, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(COLOURS[i % COLOURS.length]);
        c.drawRoundRect(new RectF(20, 20, 200, 100), 24, 24, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(70, 60, 24, p);
        c.drawRect(110, 44, 180, 76, p);
        return save(dir, "logo" + i, b);
    }

    /** A poster, opaque as TMDB's and the playlists' are (JPEG): read as the TV reads them, in 16-bit colour. */
    private static String poster(File dir, String name, int i) throws IOException {
        return picture(dir, name, 400, 600, i, true);
    }

    private static String backdrop(File dir, String name, int i) throws IOException {
        return picture(dir, name, 1280, 720, i, true);
    }

    private static String programme(File dir, int i) throws IOException {
        return picture(dir, "prog" + i, 640, 360, i, false);
    }

    private static String picture(File dir, String name, int w, int h, int i, boolean opaque) throws IOException {
        File f = new File(dir, name + ".png");
        if (f.exists()) return "file:" + f.getPath();
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int a = COLOURS[i % COLOURS.length], z = COLOURS[(i + 5) % COLOURS.length];
        p.setShader(new LinearGradient(0, 0, w, h, a, z, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0xFFF1C7A5);
        float d = Math.min(w, h) * 0.22f;
        c.drawCircle(w * 0.66f, h * 0.38f, d, p);
        p.setColor(0x66000000);
        c.drawRect(0, h * 0.7f, w, h, p);
        if (opaque) b.setHasAlpha(false);
        return save(dir, name, b);
    }

    private static String save(File dir, String name, Bitmap b) throws IOException {
        File f = new File(dir, name + ".png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        b.recycle();
        return "file:" + f.getPath();
    }
}
