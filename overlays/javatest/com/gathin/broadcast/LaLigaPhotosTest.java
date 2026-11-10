package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** La Liga's own pictures for a La Liga game's line-ups: which games, which page, which player, which picture, and its cut. */
public class LaLigaPhotosTest {
    static String resource(String name) throws IOException {
        try (InputStream in = LaLigaPhotosTest.class.getResourceAsStream(name)) {
            assertNotNull(name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void laLigaGamesAreKnownByTheFeedsIdTheirNameOrFotMobsLeague() throws Exception {
        assertTrue(LaLigaPhotos.isLaLiga("esp.1", "", null));
        assertTrue(LaLigaPhotos.isLaLiga("87", null, null));
        assertTrue(LaLigaPhotos.isLaLiga("", "LALIGA EA SPORTS", null));
        assertTrue(LaLigaPhotos.isLaLiga("", "LaLiga", null));
        assertTrue(LaLigaPhotos.isLaLiga("", "Primera División", null));
        assertTrue(LaLigaPhotos.isLaLiga("", "", new JSONObject().put("general", new JSONObject().put("leagueId", 87).put("leagueName", "LaLiga"))));
        assertTrue(LaLigaPhotos.isLaLiga(null, null, new JSONObject().put("general", new JSONObject().put("parentLeagueId", 87))));
        // the second division, the cup, the Premier League and Europe are not
        assertFalse(LaLigaPhotos.isLaLiga("esp.2", "LaLiga 2", null));
        assertFalse(LaLigaPhotos.isLaLiga("", "LALIGA HYPERMOTION", null));
        assertFalse(LaLigaPhotos.isLaLiga("esp.copa_del_rey", "Copa del Rey", null));
        assertFalse(LaLigaPhotos.isLaLiga("eng.1", "Premier League", new JSONObject().put("general", new JSONObject().put("leagueId", 47))));
        assertFalse(LaLigaPhotos.isLaLiga("uefa.champions", "Champions League", null));
        assertFalse(LaLigaPhotos.isLaLiga(null, null, null));
    }

    @Test
    public void addressesAreTriedLikeliestFirst() {
        assertEquals("julian-alvarez", LaLigaPhotos.slug("Julián Álvarez"));
        assertEquals("fermin-lopez", LaLigaPhotos.slug("Fermín López"));
        assertEquals("odegaard", LaLigaPhotos.slug("Ødegaard"));
        assertEquals("ngolo-kante", LaLigaPhotos.slug("N'Golo Kanté"));
        assertEquals("marc-andre-ter-stegen", LaLigaPhotos.slug(" Marc-André ter Stegen "));
        // a name La Liga knows by one word is tried once; La Liga's own examples: "koke", "pedri", "oblak", "griezmann", "lamine-yamal"
        assertEquals(Arrays.asList("koke"), LaLigaPhotos.slugs("Koke", "Koke"));
        assertEquals(Arrays.asList("pedri"), LaLigaPhotos.slugs("Pedri", ""));
        assertEquals(Arrays.asList("jan-oblak", "oblak"), LaLigaPhotos.slugs("Jan Oblak", "Oblak"));
        assertEquals(Arrays.asList("antoine-griezmann", "griezmann", "antoine"), LaLigaPhotos.slugs("Antoine Griezmann", "Griezmann"));
        assertEquals(Arrays.asList("lamine-yamal", "yamal", "lamine"), LaLigaPhotos.slugs("Lamine Yamal", "Yamal"));
        assertEquals(Arrays.asList("julian-alvarez", "alvarez", "julian"), LaLigaPhotos.slugs("Julián Alvarez", null));
        // a surname of two: its first word, and the first name with it (four at most)
        assertEquals(Arrays.asList("pedro-gonzalez-lopez", "gonzalez-lopez", "gonzalez", "pedro-gonzalez"),
                LaLigaPhotos.slugs("Pedro González López", "González López"));
        // "ter" is not a surname
        assertEquals(Arrays.asList("marc-andre-ter-stegen", "ter-stegen", "marc-andre"), LaLigaPhotos.slugs("Marc-André ter Stegen", "ter Stegen"));
        assertEquals("Lamine", LaLigaPhotos.firstOf("Lamine Yamal", "Yamal"));
        assertEquals("", LaLigaPhotos.firstOf("Koke", "Koke"));
        assertEquals("Dani", LaLigaPhotos.firstOf("Dani Carvajal", ""));
    }

    @Test
    public void kokesPageGivesHisNamesHisClubAndTheBigCutOut() throws Exception {
        LaLigaPhotos.Page p = LaLigaPhotos.parse(resource("laliga-koke.html"));
        assertTrue(p.names.contains("Koke"));
        assertTrue(p.names.contains("Jorge Resurrección Merodio"));
        assertEquals(1, p.squads.size());
        LaLigaPhotos.Squad q = p.squads.get(0);
        assertTrue(q.clubs.contains("Atlético de Madrid"));
        assertTrue(q.clubs.contains("Club Atlético de Madrid SAD"));
        // the waist-up cut-out 1024 across (not the 2048 one, nor the square JPEG), then the square cut-out
        assertEquals(Arrays.asList(
                "https://assets.laliga.com/squad/2026/t175/p77390/1024x1113/p77390_t175_2026_1_001_000.png",
                "https://assets.laliga.com/squad/2026/t175/p77390/1024x1024/p77390_t175_2026_1_003_000.png"), q.cutOuts);
        // FotMob's "Koke" is him, by the name he is known by; at his own club only
        assertTrue(LaLigaPhotos.samePlayer(p.names, "Koke", "Koke"));
        assertFalse(LaLigaPhotos.samePlayer(p.names, "Jan Oblak", "Oblak"));
        assertNotNull(LaLigaPhotos.squadFor(p, new String[]{"Atlético Madrid", "Atletico Madrid"}));
        assertNull(LaLigaPhotos.squadFor(p, new String[]{"Real Madrid", "Real Madrid"}));
    }

    @Test
    public void withoutItsDataAPageIsReadFromItsTitleAndPictureAddresses() throws Exception {
        String html = "<html><head><title>Antoine Griezmann LALIGA EA SPORTS | LALIGA</title>"
                + "<meta property=\"og:title\" content=\"Antoine Griezmann LALIGA EA SPORTS\"/>"
                + "<meta property=\"og:site_name\" content=\"Griezmann\"/></head><body>"
                + "<script id=\"__NEXT_DATA__\" type=\"application/json\">{not json</script>"
                + "<img src=\"https://assets.laliga.com/squad/2026/t175/p76650/512x556/p76650_t175_2026_0_001_000.png\">"
                + "<img src=\"https://assets.laliga.com/squad/2026/t175/p76650/1024x1113/p76650_t175_2026_0_001_000.png\">"
                + "<img src=\"https://assets.laliga.com/squad/2026/t175/p76650/2048x2225/p76650_t175_2026_0_001_000.png\">"
                + "<img src=\"https://assets.laliga.com/squad/2026/t175/p76650/1024x1024/p76650_t175_2026_0_002_000.jpg\">"
                + "<img src=\"https://assets.laliga.com/squad/2026/t175/p76650/512x512/p76650_t175_2026_0_003_000.png\">"
                + "<i data-x='\"team\":{\"id\":2,\"slug\":\"atletico-de-madrid\",\"name\":\"Club Atlético de Madrid SAD\",\"nickname\":\"Atlético de Madrid\",\"color\":\"#f21e27\"}'></i>"
                + "</body></html>";
        LaLigaPhotos.Page p = LaLigaPhotos.parse(html);
        assertTrue(p.names.contains("Antoine Griezmann"));
        assertTrue(p.names.contains("Griezmann"));
        assertEquals(1, p.squads.size());
        assertEquals(Arrays.asList(
                "https://assets.laliga.com/squad/2026/t175/p76650/1024x1113/p76650_t175_2026_0_001_000.png",
                "https://assets.laliga.com/squad/2026/t175/p76650/512x512/p76650_t175_2026_0_003_000.png"), p.squads.get(0).cutOuts);
        assertTrue(LaLigaPhotos.samePlayer(p.names, "Antoine Griezmann", "Griezmann"));
        assertNotNull(LaLigaPhotos.squadFor(p, new String[]{"Atlético Madrid"}));
        // a page with nothing of a player in it
        assertTrue(LaLigaPhotos.parse("<html><body>404</body></html>").squads.isEmpty());
        assertEquals("Koke's & co", LaLigaPhotos.unescape("Koke&#x27;s &amp; co"));
    }

    @Test
    public void playersAreTheSameByTheirSurnameAndFirstNameAccentsAside() {
        List<String> yamal = Arrays.asList("Lamine Yamal", "Lamine Yamal Nasraoui Ebana", "Nasraoui Ebana");
        assertTrue(LaLigaPhotos.samePlayer(yamal, "Lamine Yamal", "Yamal"));
        List<String> alvarez = Arrays.asList("J.alvarez", "Julián Alvarez", "Julián", "Alvarez");
        assertTrue(LaLigaPhotos.samePlayer(alvarez, "Julián Álvarez", "Álvarez"));
        List<String> pedri = Arrays.asList("Pedri", "Pedro González López", "Pedro", "González López");
        assertTrue(LaLigaPhotos.samePlayer(pedri, "Pedri", "Pedri"));
        assertFalse(LaLigaPhotos.samePlayer(pedri, "Pedro Porro", "Porro"));
        // brothers at one club: the first name tells them apart
        List<String> nico = Arrays.asList("Nico Williams", "Nicholas Williams Arthuer");
        assertTrue(LaLigaPhotos.samePlayer(nico, "Nico Williams", "Williams"));
        assertFalse(LaLigaPhotos.samePlayer(nico, "Iñaki Williams", "Williams"));
        assertTrue(LaLigaPhotos.samePlayer(Arrays.asList("Ter Stegen", "Marc-André ter Stegen"), "Marc-André ter Stegen", "ter Stegen"));
        assertTrue(LaLigaPhotos.samePlayer(Arrays.asList("Álex Baena", "Alejandro Baena Rodríguez"), "Alex Baena", "Baena"));
        assertFalse(LaLigaPhotos.samePlayer(Arrays.asList(), "Koke", "Koke"));
    }

    @Test
    public void clubsAreTheSameByTheirNamesNeverOnlyTheirCity() {
        String[] atleti = {"Atlético Madrid"}, barca = {"Barcelona"};
        assertTrue(LaLigaPhotos.sameClub(Arrays.asList("Atlético de Madrid", "Club Atlético de Madrid SAD"), atleti));
        assertTrue(LaLigaPhotos.sameClub(Arrays.asList("FC Barcelona", "Fútbol Club Barcelona"), barca));
        assertFalse(LaLigaPhotos.sameClub(Arrays.asList("RCD Espanyol de Barcelona", "Reial Club Deportiu Espanyol de Barcelona SAD"), barca));
        assertFalse(LaLigaPhotos.sameClub(Arrays.asList("Atlético de Madrid"), new String[]{"Real Madrid"}));
        assertFalse(LaLigaPhotos.sameClub(Arrays.asList("Athletic Club"), atleti));
        assertTrue(LaLigaPhotos.sameClub(Arrays.asList("Real Betis Balompié SAD"), new String[]{"Real Betis"}));
        assertTrue(LaLigaPhotos.sameClub(Arrays.asList("Real Club Celta de Vigo SAD", "RC Celta"), new String[]{"Celta Vigo"}));
        assertTrue(LaLigaPhotos.sameClub(Arrays.asList("Deportivo Alavés SAD"), new String[]{"Alavés"}));
        assertTrue(LaLigaPhotos.sameClub(Arrays.asList("C.A. Osasuna"), new String[]{"", "Osasuna"}));
        assertTrue(LaLigaPhotos.sameClub(Arrays.asList("Real Sociedad de Fútbol SAD"), new String[]{"Real Sociedad"}));
    }

    @Test
    public void theCutIsSquaredToThePremierLeaguesFraming() {
        // Koke's 1024 x 1113 cut-out (measured): head from row 4, its middle at 489: a 794 square, 6 rows of air above
        assertArrayEquals(new int[]{92, -6, 794}, LaLigaPhotos.cut(1024, 1113, 4, 489));
        // the square 1024 x 1024 one is the same picture smaller: the same share of its height
        assertEquals(730, LaLigaPhotos.cut(1024, 1024, 1, 491)[2]);
        // the head found in the top rows: a blob from row 10, 20 wide round x 60 (a stray pixel above does not count)
        int w = 100, h = 100;
        int[] px = new int[w * h];
        px[5 * w + 3] = 0xFF000000;
        for (int y = 10; y < 40; y++) for (int x = 50; x < 70; x++) px[y * w + x] = 0xFF8899AA;
        assertArrayEquals(new int[]{10, 59}, LaLigaPhotos.head(px, w, 40, h));
        assertNull(LaLigaPhotos.head(new int[w * 10], w, 10, h));
    }

    @Test
    public void blackStandInIsASilhouette() {
        // all opaque samples black: La Liga's stand-in for a player not yet photographed
        org.junit.Assert.assertTrue(LaLigaPhotos.silhouette(400, 0));
        // a real face and shirt: mean luma well above
        org.junit.Assert.assertFalse(LaLigaPhotos.silhouette(400, 400L * 120 * 1000));
        // too few opaque samples to say
        org.junit.Assert.assertFalse(LaLigaPhotos.silhouette(10, 0));
    }
}
