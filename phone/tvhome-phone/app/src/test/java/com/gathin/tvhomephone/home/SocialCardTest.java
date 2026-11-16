package com.gathin.tvhomephone.home;

import static org.junit.Assert.assertEquals;

import com.gathin.tvhomephone.live.FanFeed;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The fans' card on Home: authors' initials and colours, and which posts drop in at the top. */
public class SocialCardTest {
    private static FanFeed.Item post(String words, long at) {
        return new FanFeed.Item("Bluesky", "Fan", "@fan.example", words, "", "", at);
    }

    @Test public void initialsFromNamesAndHandles() {
        assertEquals("RD", SocialCard.initials("Red Devil Dan"));
        assertEquals("GL", SocialCard.initials("Goodison Lou"));
        assertEquals("R", SocialCard.initials("u/reddevil_99"));
        assertEquals("L", SocialCard.initials("@lou.bsky.social"));
        assertEquals("RD", SocialCard.initials("⚽ Red Dan"));
        assertEquals("?", SocialCard.initials("   "));
        assertEquals("?", SocialCard.initials(null));
    }

    @Test public void onlyNewerPostsDropIn() {
        FanFeed.Item old = post("a", 1000), shown = post("b", 2000), newer = post("c", 3000), newest = post("d", 4000);
        FanFeed.Item olderUnseen = post("e", 1500);
        Set<String> known = new HashSet<>(Arrays.asList(old.key(), shown.key()));
        List<FanFeed.Item> come = SocialCard.later(Arrays.asList(newest, newer, shown, olderUnseen, old), known, 2000);
        assertEquals(Arrays.asList(newest, newer), come);
    }

    @Test public void eachAuthorKeepsTheirColour() {
        assertEquals(SocialCard.face("@lou.bsky.social"), SocialCard.face("@Lou.bsky.social"));
        assertEquals(SocialCard.face("u/fan"), SocialCard.face("u/fan"));
    }
}
