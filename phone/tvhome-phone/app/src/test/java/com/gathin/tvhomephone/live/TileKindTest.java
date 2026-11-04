package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class TileKindTest {
    @Test public void aGameOnASportsChannel() {
        assertEquals(TileKind.Kind.MATCH, TileKind.of("TNT Sports 3", "UK Sports", "AEK Athens v Real Madrid", "", false));
        assertEquals(TileKind.Kind.MATCH, TileKind.of("Sky Sports Main Event", "", "Live Champions League", "Arsenal v Slavia Prague", false));
        // Not a game: the news on a sports channel.
        assertEquals(TileKind.Kind.OTHER, TileKind.of("Sky Sports News", "Sports", "Sky Sports News", "The latest", false));
        // A fixture's look alone on another channel is not enough ("Boys v Girls"); a game found for it is.
        assertEquals(TileKind.Kind.OTHER, TileKind.of("ITV2", "Entertainment", "Love Island: Boys v Girls", "", false));
        assertEquals(TileKind.Kind.MATCH, TileKind.of("ITV1", "Entertainment", "England v Wales", "", true));
    }

    @Test public void films() {
        assertEquals(TileKind.Kind.FILM, TileKind.of("Sky Cinema Family", "Movies", "Wildwood", "A girl follows the crows.", false));
        assertEquals(TileKind.Kind.FILM, TileKind.of("BBC Two", "", "Film: Verity", "", false));
        assertEquals(TileKind.Kind.FILM, TileKind.of("Channel 4", "", "The Dog Stars (2026)", "", false));
        assertEquals(TileKind.Kind.FILM, TileKind.of("Channel 5", "", "Paddington", "(2014) Family comedy. A bear from Peru...", false));
        // An episode on a film channel is still an episode.
        assertEquals(TileKind.Kind.SERIES, TileKind.of("Sky Cinema Premiere", "Movies", "The Making Of S1 E2", "", false));
    }

    @Test public void series() {
        assertEquals(TileKind.Kind.SERIES, TileKind.of("Sky Atlantic", "", "MobLand", "S2 E4. Two mob families clash.", false));
        assertEquals(TileKind.Kind.SERIES, TileKind.of("Drama", "", "Nocturne: Episode 1", "", false));
        assertEquals(TileKind.Kind.OTHER, TileKind.of("BBC News", "News", "BBC News at Six", "Series 2 of the news?", false));
        assertEquals(TileKind.Kind.OTHER, TileKind.of("BBC One", "", "The One Show", "Chat and stories.", false));
    }

    @Test public void episodes() {
        assertEquals("S2 · E4", TileKind.episode("MobLand", "S2 E4. Two mob families."));
        assertEquals("S2 · E4", TileKind.episode("MobLand S02E04", ""));
        assertEquals("S3 · E1", TileKind.episode("", "Series 3, Episode 1: the return."));
        assertEquals("E1", TileKind.episode("Nocturne: Episode 1", ""));
        assertEquals("Part 2 of 6", TileKind.episode("Blue Lights (2/6)", ""));
        assertNull(TileKind.episode("The One Show", "Chat."));
    }

    @Test public void lookupTitles() {
        assertEquals("Verity", TileKind.lookupTitle("Film: Verity (2025)"));
        assertEquals("Nocturne", TileKind.lookupTitle("Nocturne: Episode 1"));
        assertEquals("The Repair Shop", TileKind.lookupTitle("New: The Repair Shop"));
        assertEquals("MobLand", TileKind.lookupTitle("MobLand S2 E4"));
        assertEquals("Mission: Impossible", TileKind.lookupTitle("Mission: Impossible"));
        assertEquals("2014", TileKind.year("Paddington", "(2014) Family comedy."));
    }

    @Test public void autoPicks() {
        assertEquals(TileKind.Style.MATCH, TileKind.auto(TileKind.Kind.MATCH));
        assertEquals(TileKind.Style.CINEMA, TileKind.auto(TileKind.Kind.FILM));
        assertEquals(TileKind.Style.CARD, TileKind.auto(TileKind.Kind.SERIES));
        assertEquals(TileKind.Style.RIBBON, TileKind.auto(TileKind.Kind.OTHER));
        assertEquals(TileKind.Style.AUTO, TileKind.Style.of("nonsense"));
        assertEquals(TileKind.Style.PANEL, TileKind.Style.of("PANEL"));
    }

    @Test public void channels() {
        assertTrue(TileKind.sportsChannel("UK: TNT Sports 1 FHD", ""));
        assertTrue(TileKind.sportsChannel("Premier Sports 1", ""));
        assertTrue(TileKind.sportsChannel("Something", "UK | Sports"));
        assertFalse(TileKind.sportsChannel("BBC One", "UK Entertainment"));
        assertTrue(TileKind.filmChannel("Sky Cinema Premiere HD", ""));
        assertTrue(TileKind.newsChannel("Sky News", ""));
    }

    @Test public void tileWords() {
        assertEquals("1h 06m", InfoTile.duration(66 * 60_000L));
        assertEquals("48m", InfoTile.duration(48 * 60_000L));
        assertEquals("", InfoTile.duration(0));
        assertEquals("2026 · Animation · ★ 8.6", InfoTile.join("2026", "", "Animation", null, "★ 8.6"));
        assertEquals("S2", "S" + TileData.seasonEpisode("S2 · E4")[0]);
        assertNull(TileData.seasonEpisode("E4"));
        assertTrue(TileData.same("The Dog Stars", "Dog Stars"));
        assertFalse(TileData.same("Verity", "Verity's Secret"));
    }
}
