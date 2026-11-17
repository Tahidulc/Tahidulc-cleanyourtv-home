package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** The dark screens' pure helpers: the posters' fade, badges and lines, people's initials, trailers, episode facts. */
public class DarkLookTest {
    @Test public void fadeIsTheFootsOwnColourMadeDeep() {
        // A bright red foot: still red, much darker, a little more saturated.
        int c = PosterArt.fadeColour(0xC83C32);
        float[] hsv = PosterArt.hsv(c & 0xFFFFFF);
        assertEquals(0xFF, c >>> 24);
        assertTrue(hsv[0] < 10 || hsv[0] > 350);
        assertEquals(0.14f + 0.14f * (0xC8 / 255f), hsv[2], 0.01f);
        assertEquals(Math.min(0.85f, PosterArt.hsv(0xC83C32)[1] * 1.15f), hsv[1], 0.02f);
        // A grey stays grey, and white becomes a deep grey.
        float[] grey = PosterArt.hsv(PosterArt.fadeColour(0xFFFFFF) & 0xFFFFFF);
        assertEquals(0f, grey[1], 0.001f);
        assertEquals(0.28f, grey[2], 0.01f);
    }

    @Test public void averageOfOpaquePixelsOnly() {
        int[] px = {0xFF000000, 0xFFFFFFFF, 0x00FF0000};
        assertEquals(0x7F7F7F, PosterArt.average(px));
        assertEquals(0x808080, PosterArt.average(new int[]{0x00123456}));
    }

    @Test public void hsvRoundTrip() {
        for (int rgb : new int[]{0x102030, 0xFF8800, 0x00FF7F, 0x7F00FF, 0x808080}) {
            float[] h = PosterArt.hsv(rgb);
            int back = PosterArt.rgb(h[0], h[1], h[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                assertEquals((rgb >> shift) & 0xFF, (back >> shift) & 0xFF, 1);
            }
        }
    }

    @Test public void newOnlyForTheLastFortnight() {
        long now = 1_800_000_000_000L, day = 86_400_000L;
        assertEquals("New", PosterArt.badge(now - 3 * day, now));
        assertEquals("", PosterArt.badge(now - 20 * day, now));
        assertEquals("", PosterArt.badge(0, now));
    }

    @Test public void genresFromCategoriesAndPages() {
        assertEquals("Action", PosterArt.genreOf("UK | Action Movies"));
        assertEquals("Sci-Fi", PosterArt.genreOf("Sci-Fi & Fantasy"));
        assertEquals("Comedy", PosterArt.genreOf("EN - COMEDIES"));
        assertEquals("", PosterArt.genreOf("Netflix Originals"));
        assertEquals("Science Fiction", PosterArt.firstGenre("Science Fiction, Action"));
        assertEquals("Drama", PosterArt.firstGenre("Drama / Crime"));
    }

    @Test public void genresWithoutARegularExpressionAsWithOne() {
        String[] names = {"UK | Action Movies", "Sci-Fi & Fantasy", "sci fi", "EN - COMEDIES", "Netflix Originals", "|DE| Krimi Thrillers",
                "Kids' Animation", "Documentaries 4K", "Romantic-Comedy", "WAR/WESTERN", "Ｃｏｍｅｄｙ", "Café Drama", "", "  ", "1990s",
                "Mystery&Crime", "History+Sport"};
        // 1.10's table and its way: the name split by a regular expression, the first word that names a genre.
        String[][] table = {{"action", "Action"}, {"adventure", "Adventure"}, {"animation", "Animation"}, {"animated", "Animation"},
                {"anime", "Anime"}, {"comedy", "Comedy"}, {"comedies", "Comedy"}, {"crime", "Crime"}, {"documentary", "Documentary"},
                {"documentaries", "Documentary"}, {"drama", "Drama"}, {"family", "Family"}, {"fantasy", "Fantasy"},
                {"history", "History"}, {"horror", "Horror"}, {"kids", "Kids"}, {"music", "Music"}, {"musical", "Musical"},
                {"mystery", "Mystery"}, {"romance", "Romance"}, {"romantic", "Romance"}, {"scifi", "Sci-Fi"}, {"sci", "Sci-Fi"},
                {"thriller", "Thriller"}, {"thrillers", "Thriller"}, {"war", "War"}, {"western", "Western"}, {"sport", "Sport"},
                {"reality", "Reality"}, {"christmas", "Christmas"}};
        for (String n : names) {
            String want = "";
            String low = n.toLowerCase(java.util.Locale.ROOT).replace("sci-fi", "scifi").replace("sci fi", "scifi");
            outer:
            for (String w : low.split("[^a-z]+")) {
                for (String[] g : table) {
                    if (g[0].equals(w)) {
                        want = g[1];
                        break outer;
                    }
                }
            }
            assertEquals(n, want, PosterArt.genreOf(n));
            // Asked again: the same, from what was kept.
            assertEquals(n, want, PosterArt.genreOf(n));
        }
        for (String g : new String[]{"Science Fiction, Action", "Drama / Crime", "Action|Adventure", "Kids & Family", "", ",Drama",
                "An Exceptionally Long Genre Name", "Comedy"}) {
            String[] parts = g.split("[,/|&]");
            String want = parts.length == 0 ? "" : parts[0].trim();
            assertEquals(g, want.length() > 18 ? "" : want, PosterArt.firstGenre(g));
        }
        // Only separators: none, where splitting them used to fail.
        assertEquals("", PosterArt.firstGenre(",,/"));
    }

    @Test public void postersAreMadeOnlyAsTheyAreReached() {
        java.util.List<com.gathin.homefieldtv.engine.Catalog.Title> titles = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) titles.add(null);
        int[] made = {0};
        PosterArt.Items items = new PosterArt.Items(titles, t -> {
            made[0]++;
            return new PosterArt.Item(t);
        });
        assertEquals(1000, items.size());
        assertEquals(0, made[0]);
        PosterArt.Item third = items.get(3);
        assertTrue(third == items.get(3));
        assertEquals(1, made[0]);
    }

    @Test public void theLineOverTheFoot() {
        assertEquals("Mystery • ★ 8.2", PosterArt.line("Mystery", "8.2"));
        assertEquals("★ 7.0", PosterArt.line("", "7"));
        assertEquals("Drama", PosterArt.line("Drama", "0"));
        assertEquals("", PosterArt.line("", "x"));
    }

    @Test public void pictureChips() {
        assertEquals("4K", PosterArt.chip(4));
        assertEquals("1080p", PosterArt.chip(3));
        assertEquals("720p", PosterArt.chip(2));
        assertEquals("", PosterArt.chip(1));
        assertEquals("", PosterArt.chip(0));
    }

    @Test public void initialsAndFaces() {
        assertEquals("LN", Dark.initials("Liam Neeson"));
        assertEquals("MW", Dark.initials("Mary Elizabeth Winstead"));
        assertEquals("C", Dark.initials("Cher"));
        assertEquals("", Dark.initials("  "));
        assertEquals(Dark.face("Ariana Grande"), Dark.face("ariana grande "));
    }

    @Test public void trailerIds() {
        assertEquals("dQw4w9WgXcQ", TitleScreen.trailerId("dQw4w9WgXcQ"));
        assertEquals("dQw4w9WgXcQ", TitleScreen.trailerId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1"));
        assertEquals("dQw4w9WgXcQ", TitleScreen.trailerId("https://youtu.be/dQw4w9WgXcQ"));
        assertEquals("", TitleScreen.trailerId(""));
        assertEquals("", TitleScreen.trailerId("not a trailer"));
    }

    @Test public void episodeFacts() {
        assertEquals("1h 7m, 9 Oct 2022", DetailPage.episodeFacts(67, "2022-10-09"));
        assertEquals("54m", DetailPage.episodeFacts(54, ""));
        assertEquals("9 Oct 2022", DetailPage.episodeFacts(0, "2022-10-09"));
        assertEquals("", DetailPage.episodeFacts(0, "soon"));
    }

    @Test public void billboardPeopleAndRatings() {
        assertEquals(Arrays.asList("A", "B", "C"), Billboard.names("A, B, C, D", 3));
        assertEquals(7.4f, Billboard.rating("7.4"), 0.001f);
        assertEquals(0f, Billboard.rating("0"), 0.001f);
        assertEquals(0f, Billboard.rating("n/a"), 0.001f);
    }

    @Test public void landscapePostersUseTheBackdropAtW780() {
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", PosterArt.w780("https://image.tmdb.org/t/p/w1280/abc.jpg"));
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", PosterArt.w780("https://image.tmdb.org/t/p/original/abc.jpg"));
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", PosterArt.w780("https://image.tmdb.org/t/p/w780/abc.jpg"));
        assertEquals("http://provider.example/images/abc.jpg", PosterArt.w780("http://provider.example/images/abc.jpg"));
        assertEquals("", PosterArt.w780(null));
    }

    @Test public void landscapeFadeRisesHigherAsThePhones() {
        assertTrue(Arrays.equals(new float[]{0f, 0.24f, 0.52f, 0.8f, 1f}, PosterArt.fadeStops(true)));
        assertTrue(Arrays.equals(new float[]{0f, 0.42f, 0.64f, 0.82f, 1f}, PosterArt.fadeStops(false)));
    }

    @Test public void aLandscapeCardIsAsHighAsAPortraitOneIsWide() {
        boolean was = PosterArt.landscape;
        try {
            PosterArt.landscape = false;
            assertEquals(214f, PosterArt.cardWidth(214f), 0.001f);
            PosterArt.landscape = true;
            assertEquals(214f * 16f / 9f, PosterArt.cardWidth(214f), 0.001f);
        } finally {
            PosterArt.landscape = was;
        }
    }
}
