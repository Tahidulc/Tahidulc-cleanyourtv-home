package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/**
 * A programme's picture searched wider (the family's point 3): what is searched for from its title, its description and
 * the channel's kind, and the searches spaced out. Invented programmes.
 */
public class WiderPicturesTest {
    @After public void after() {
        WiderPictures.forget();
    }

    @Test public void theTitleIsTidiedAndTheDescriptionGivesItsYearOrSeries() {
        WiderPictures.Ask a = WiderPictures.ask("New: The Copper Ferry (2019)", "A drama.", Genres.MOVIES);
        assertEquals("The Copper Ferry", a.name);
        assertEquals(2019, a.year);
        assertEquals(-1, a.series);
        assertTrue(a.search);

        a = WiderPictures.ask("Harbour Lights S3 E4", "Series 3, episode 4. The lighthouse keeper's secret is out.", Genres.ENTERTAINMENT);
        assertEquals("Harbour Lights", a.name);
        assertEquals(1, a.series);

        a = WiderPictures.ask("Quiet Meridian", "Thriller from 1998 about a radio operator.", Genres.ENTERTAINMENT);
        assertEquals(1998, a.year);

        a = WiderPictures.ask("Midnight Orchard", "A feature film about an orchard that blooms at night.", Genres.ENTERTAINMENT);
        assertEquals(-1, a.series);

        a = WiderPictures.ask("Rocket Garden", "Bea and Otto grow a rocket.", Genres.KIDS);
        assertEquals(1, a.series);
    }

    @Test public void aLiveMatchOrANewsBulletinIsntSearched() {
        assertFalse(WiderPictures.ask("Live: Ashby Rovers v Calderby Town", "", Genres.SPORT).search);
        assertFalse(WiderPictures.ask("Evening News", "The day's headlines.", Genres.NEWS).search);
        assertTrue(WiderPictures.ask("Kestrel Golf Classics", "", Genres.SPORT).search);
        assertFalse(WiderPictures.ask("Hi", "", Genres.ENTERTAINMENT).search);
    }

    @Test public void searchesAreSpacedOut() {
        long t = 1_764_000_000_000L;
        assertTrue(WiderPictures.mayAsk(t));
        assertFalse(WiderPictures.mayAsk(t + 100));
        assertTrue(WiderPictures.mayAsk(t + WiderPictures.SPACING_MS));
        long at;
        WiderPictures.forget();
        int inOneWindow = 0;
        at = t;
        for (int i = 0; i < 100; i++, at += WiderPictures.SPACING_MS) if (WiderPictures.mayAsk(at)) inOneWindow++;
        assertEquals(WiderPictures.BURST, inOneWindow);
    }
}
