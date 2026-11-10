package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The Premier League's pictures matched to FotMob's line-ups: clubs by their names, players by theirs. */
public class PlPhotosTest {
    @Test
    public void namesFoldToPlainLetters() {
        assertEquals("odegaard", PlPhotos.fold("Ødegaard"));
        assertEquals("gyokeres", PlPhotos.fold("Gyökeres"));
        assertEquals("hincapie", PlPhotos.fold("Hincapié"));
        assertEquals("nottmforest", PlPhotos.fold("Nott'm Forest"));
        assertEquals("", PlPhotos.fold(null));
    }

    @Test
    public void clubsAreFoundByTheirFullNames() throws Exception {
        JSONArray teams = new JSONArray();
        String[] names = {"Arsenal", "Brighton", "Hull City", "Man City", "Man Utd", "Newcastle", "Nott'm Forest", "Spurs", "Bournemouth"};
        for (int i = 0; i < names.length; i++) teams.put(new JSONObject().put("id", i + 1).put("name", names[i]));
        assertEquals(1, PlPhotos.teamId(teams, "Arsenal"));
        assertEquals(2, PlPhotos.teamId(teams, "Brighton & Hove Albion"));
        assertEquals(3, PlPhotos.teamId(teams, "Hull City"));
        assertEquals(4, PlPhotos.teamId(teams, "Manchester City"));
        assertEquals(5, PlPhotos.teamId(teams, "Manchester United"));
        assertEquals(6, PlPhotos.teamId(teams, "Newcastle United"));
        assertEquals(7, PlPhotos.teamId(teams, "Nottingham Forest"));
        assertEquals(8, PlPhotos.teamId(teams, "Tottenham Hotspur"));
        assertEquals(9, PlPhotos.teamId(teams, "AFC Bournemouth"));
        assertEquals(-1, PlPhotos.teamId(teams, "Real Madrid"));
    }

    @Test
    public void playersAreMatchedByTheirNamesOrNotAtAll() {
        List<PlPhotos.Element> els = Arrays.asList(
                new PlPhotos.Element(1, 1, "David", "Raya Martín", "Raya"),
                new PlPhotos.Element(2, 1, "Gabriel", "dos Santos Magalhães", "Gabriel"),
                new PlPhotos.Element(3, 1, "Gabriel", "Martinelli Silva", "Martinelli"),
                new PlPhotos.Element(4, 1, "Martin", "Ødegaard", "Ødegaard"),
                new PlPhotos.Element(5, 1, "Jurriën", "Timber", "J.Timber"),
                new PlPhotos.Element(6, 1, "Ben", "White", "White"));
        assertEquals(1, PlPhotos.codeFor(els, "David Raya", "Raya"));
        assertEquals(2, PlPhotos.codeFor(els, "Gabriel Magalhães", "Magalhães"));
        assertEquals(3, PlPhotos.codeFor(els, "Gabriel Martinelli", "Martinelli"));
        assertEquals(4, PlPhotos.codeFor(els, "Martin Ødegaard", "Ødegaard"));
        assertEquals(5, PlPhotos.codeFor(els, "Jurrien Timber", "Timber"));
        assertEquals(6, PlPhotos.codeFor(els, "Ben White", "White"));
        assertEquals(-1, PlPhotos.codeFor(els, "Someone Else", "Else"));
    }
}
