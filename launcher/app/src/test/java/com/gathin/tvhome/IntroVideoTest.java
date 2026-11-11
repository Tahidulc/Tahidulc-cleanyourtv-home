package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IntroVideoTest {
    @Test
    public void namesAreShownPlain() {
        assertEquals("matchday", IntroVideo.cleanName("/storage/emulated/0/Movies/matchday.mp4"));
        assertEquals("19556_compressed", IntroVideo.cleanName("19556_compressed.mp4"));
        assertEquals("your video", IntroVideo.cleanName(""));
        assertEquals("your video", IntroVideo.cleanName(null));
        assertEquals("a b", IntroVideo.cleanName("a<>\nb.mov"));
        assertEquals(40, IntroVideo.cleanName("x".repeat(80) + ".mp4").length());
    }
}
