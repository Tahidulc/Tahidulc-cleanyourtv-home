package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The guide's categories from what the playlist calls its channels. */
public class GenresTest {
    @Test public void theChannelsNameFirst() {
        assertEquals(Genres.SPORT, Genres.of("UK | News", "Sky Sports News"));
        assertEquals(Genres.KIDS, Genres.of("UK | Entertainment", "Nickelodeon"));
        assertEquals(Genres.MOVIES, Genres.of("UK", "Film4 HD"));
        assertEquals(Genres.NEWS, Genres.of("", "BBC News HD"));
        assertEquals(Genres.DOCUMENTARIES, Genres.of("UK | General", "Nat Geo Wild"));
        assertEquals(Genres.MUSIC, Genres.of("", "MTV Hits"));
        assertEquals(Genres.SPORT, Genres.of("", "Premier Sports 1"));
    }

    @Test public void thenItsGroup() {
        assertEquals(Genres.SPORT, Genres.of("UK | Sports", "Main Event"));
        assertEquals(Genres.KIDS, Genres.of("Kids", "Milkshake!"));
    }

    @Test public void anythingElseIsEntertainment() {
        assertEquals(Genres.ENTERTAINMENT, Genres.of("UK", "BBC One HD"));
        assertEquals(Genres.ENTERTAINMENT, Genres.of("", ""));
        // Words inside other words don't count: Popcorn isn't Pop.
        assertEquals(Genres.ENTERTAINMENT, Genres.of("", "Popcorn TV"));
    }
}
