package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class GroundsTest {
    private static JSONObject json(String name) throws Exception {
        return new JSONObject(LaLigaPhotosTest.resource(name));
    }

    private static List<String> searches(List<String[]> qs) {
        List<String> out = new ArrayList<>();
        for (String[] q : qs) out.add(q[0]);
        return out;
    }

    @Test
    public void aViewFromTheStandsBeatsTheOutsideAndOnlyFreePhotosCount() {
        int stands = Grounds.score("File:-2007-05-05 Aston Villa v Sheffield United, Villa Park from the Holt End (11).JPG".replace(".JPG", ".jpg"),
                2560, 1440, "image/jpeg", "CC BY-SA 3.0", "Villa Park", true);
        int facade = Grounds.score("File:Facade of North Stand, Villa Park.jpg", 3264, 2448, "image/jpeg", "CC BY-SA 3.0", "Villa Park", true);
        assertTrue(stands > facade);
        assertTrue(Grounds.score("File:Old Trafford (interior), Manchester United.jpg", 4544, 2452, "image/jpeg", "CC BY 2.0", "Old Trafford", false) > 0);
        // not free, too small, upright, not a JPEG, or (outside the ground's category) not naming it
        assertTrue(Grounds.score("File:Villa Park pitch.jpg", 3000, 1700, "image/jpeg", "Fair use", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Villa Park pitch.jpg", 1000, 600, "image/jpeg", "CC BY 2.0", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Villa Park pitch.jpg", 2000, 3000, "image/jpeg", "CC BY 2.0", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Villa Park pitch.png", 3000, 1700, "image/png", "CC BY 2.0", "Villa Park", true) < 0);
        assertTrue(Grounds.score("File:Interior of Park-Villa Rieter 2.jpg", 11768, 4410, "image/jpeg", "CC BY-SA 4.0", "Villa Park", false) < 0);
        // "design" is not a sign
        assertTrue(Grounds.score("File:Villa Park design of the pitch.jpg", 3000, 1700, "image/jpeg", "CC0", "Villa Park", true) > 0);
    }

    @Test
    public void aSponsorsWordsComeOffTheFrontOnlyWhereANameIsLeft() {
        assertEquals(Arrays.asList("Metropolitano", "Air Metropolitano"), Grounds.variants("Riyadh Air Metropolitano"));
        assertEquals(Arrays.asList("Camp Nou"), Grounds.variants("Spotify Camp Nou"));
        assertEquals(Arrays.asList("Balaídos", "Abanca Balaídos"), Grounds.variants("Estadio Abanca-Balaídos"));
        assertTrue(Grounds.variants("Emirates Stadium").isEmpty());
        assertTrue(Grounds.variants("Gtech Community Stadium").isEmpty());
        assertTrue(Grounds.variants("Anfield").isEmpty());
    }

    @Test
    public void wikidataFindsTheGroundUnderTheNameCommonsFilesItBy() throws Exception {
        // the search: the stadium, not the metro station named after it
        List<String[]> hits = Grounds.searchHits(json("wd-search-metropolitano.json"));
        assertEquals(2, hits.size());
        assertFalse(Grounds.stadiumWords(hits.get(0)[1]));
        assertTrue(Grounds.stadiumWords(hits.get(1)[1]));
        assertEquals("Q20000002", hits.get(1)[0]);
        assertTrue(Grounds.stadiumWords("stadium in Liverpool, home of Everton Football Club"));
        assertTrue(Grounds.stadiumWords("estadio de fútbol en Madrid, España"));
        assertFalse(Grounds.stadiumWords("association football club in Madrid, Spain"));
        assertFalse(Grounds.stadiumWords(""));
        // Wikipedia's search, in its own order
        assertEquals(Arrays.asList("Q20000002", "Q20000003"), Grounds.wikiItems(json("wp-search-metropolitano.json")));
        assertTrue(Grounds.wikiItems(null).isEmpty());
        // the entities: the club passed over for the stadium, with its category, main image and other names
        JSONObject entities = json("wd-entities-metropolitano.json");
        Grounds.Entity e = Grounds.chosen(entities, Arrays.asList("Q20000003", "Q20000002"));
        assertNotNull(e);
        assertEquals("Q20000002", e.id);
        assertEquals("Metropolitano Stadium", e.category);
        assertEquals("Metropolitano Stadium interior 2019.jpg", e.image);
        assertEquals(Arrays.asList("Metropolitano Stadium", "Wanda Metropolitano", "Riyadh Air Metropolitano", "La Peineta"), e.names.subList(0, 4));
        assertTrue(e.names.containsAll(Arrays.asList("Estadio Metropolitano", "Cívitas Metropolitano", "Metropolitano")));
        assertEquals(e.names.size(), new HashSet<>(e.names).size());
        assertNull(Grounds.chosen(entities, Arrays.asList("Q20000003")));
        assertNull(Grounds.chosen(null, Arrays.asList("Q20000002")));
    }

    @Test
    public void theSearchesGoFromTheGroundsCategoryToItsOtherNames() throws Exception {
        Grounds.Entity e = Grounds.chosen(json("wd-entities-metropolitano.json"), Arrays.asList("Q20000002"));
        List<String[]> qs = Grounds.queries("Riyadh Air Metropolitano", "Madrid", e);
        assertEquals(Arrays.asList(
                "incategory:\"Metropolitano Stadium\" filetype:bitmap",
                "\"Metropolitano Stadium\" stadium filetype:bitmap",
                "incategory:\"Riyadh Air Metropolitano\" filetype:bitmap",
                "\"Riyadh Air Metropolitano\" stadium filetype:bitmap",
                "\"Wanda Metropolitano\" stadium filetype:bitmap",
                "\"Riyadh Air Metropolitano\" Madrid filetype:bitmap"), searches(qs));
        assertEquals("cat", qs.get(0)[2]);
        assertEquals("Metropolitano Stadium", qs.get(1)[1]);   // a title must carry the name searched
        assertEquals("text", qs.get(1)[2]);
        // without Wikidata: the old two first, then the name less the sponsor's words, as titles
        assertEquals(Arrays.asList(
                "incategory:\"Riyadh Air Metropolitano\" filetype:bitmap",
                "\"Riyadh Air Metropolitano\" stadium filetype:bitmap",
                "\"Metropolitano\" stadium filetype:bitmap",
                "\"Air Metropolitano\" stadium filetype:bitmap"), searches(Grounds.queries("Riyadh Air Metropolitano", "", null)));
        assertEquals(Arrays.asList(
                "incategory:\"Villa Park\" filetype:bitmap",
                "\"Villa Park\" stadium filetype:bitmap",
                "\"Villa Park\" Birmingham filetype:bitmap"), searches(Grounds.queries("Villa Park", "Birmingham", null)));
        // a ground filed under its own name is searched once
        Grounds.Entity ot = new Grounds.Entity();
        ot.category = "Old Trafford";
        ot.names.add("Old Trafford");
        assertEquals(Arrays.asList(
                "incategory:\"Old Trafford\" filetype:bitmap",
                "\"Old Trafford\" stadium filetype:bitmap",
                "\"Old Trafford\" Manchester filetype:bitmap"), searches(Grounds.queries("Old Trafford", "Manchester", ot)));
    }

    @Test
    public void theGroundsMainImageIsScoredLikeTheRest() throws Exception {
        JSONObject best = Grounds.pick(json("commons-file-info.json"), "Riyadh Air Metropolitano", true);
        assertNotNull(best);
        assertTrue(best.getString("url").contains("/1920px-"));
        assertEquals("Photo: Example, CC BY-SA 4.0 (Wikimedia Commons)", best.getString("credit"));
        assertEquals(5, best.getInt("score"));   // "interior" 3, and 4000 across 2
        assertEquals(8, Grounds.MAX_CALLS);
    }
}
