package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Home's fans' feed: the clubs it reads, bots left out, the posts' own pages and nothing else opened. */
public class FanFeedTest {
    private static FanPosts.Post by(String name, String handle) {
        return new FanPosts.Post(FanPosts.BLUESKY, name, handle, "", "Big game today, can't wait for kick-off", 1000L);
    }

    @Test public void botsAreLeftOutButNotPeopleCalledTalbot() {
        assertTrue(FanPosts.bot(by("NUFC Bot", "@nufc.example")));
        assertTrue(FanPosts.bot(by("Club News", "@clubnewsbot.bsky.social")));
        assertTrue(FanPosts.bot(by("Goals", "@goal-bot.bsky.social")));
        assertTrue(FanPosts.bot(by("Score updates", "@premscorebot2.bsky.social")));
        assertFalse(FanPosts.bot(by("Sam Talbot", "@talbot.bsky.social")));
        assertFalse(FanPosts.bot(by("Abbot", "@abbot.bsky.social")));
        assertFalse(FanPosts.bot(by("Robotics Rach", "@robotics.bsky.social")));
        assertFalse(FanPosts.bot(by("Goodison Lou", "@lou.bsky.social")));
    }

    @Test public void clubsByNameOnlyEachOnceAtMostThree() {
        List<String[]> starred = Arrays.asList(new String[]{"360", "Manchester United", ""}, new String[]{"368", "368", ""},
                new String[]{"362", "Everton", ""}, new String[]{"999", "everton", ""}, new String[]{"364", "Liverpool", ""},
                new String[]{"359", "Arsenal", ""});
        assertEquals(Arrays.asList("Manchester United", "Everton", "Liverpool"), FanFeed.names(starred));
    }

    @Test public void onlyBlueskyAndRedditPagesOpen() {
        assertEquals("https://bsky.app/profile/lou.bsky.social/post/3kq2", FanFeed.safeLink("https://bsky.app/profile/lou.bsky.social/post/3kq2"));
        assertEquals("https://www.reddit.com/r/reddevils/comments/abc/x/", FanFeed.safeLink("https://www.reddit.com/r/reddevils/comments/abc/x/"));
        assertEquals("", FanFeed.safeLink("http://bsky.app/profile/x"));
        assertEquals("", FanFeed.safeLink("https://bsky.app.evil.example/x"));
        assertEquals("", FanFeed.safeLink("javascript:alert(1)"));
        assertEquals("", FanFeed.safeLink(null));
    }

    @Test public void blueskyPostsLinkToTheirPage() throws Exception {
        JSONObject record = new JSONObject().put("text", "Away end already in full voice. Nothing to lose today.")
                .put("createdAt", "2026-11-12T14:58:00Z").put("langs", new JSONArray().put("en"));
        JSONObject p = new JSONObject().put("uri", "at://did:plc:abc123/app.bsky.feed.post/3kq2xyz")
                .put("author", new JSONObject().put("handle", "lou.bsky.social").put("displayName", "Goodison Lou")).put("record", record);
        List<FanPosts.Post> ps = FanPosts.bluesky(new JSONObject().put("posts", new JSONArray().put(p)));
        assertEquals(1, ps.size());
        FanFeed.Item item = new FanFeed.Item(ps.get(0));
        assertEquals("https://bsky.app/profile/lou.bsky.social/post/3kq2xyz", item.link);
        assertTrue(item.bluesky());
        assertEquals("", item.where);
    }

    @Test public void theSamePostHasTheSameKey() {
        FanFeed.Item a = new FanFeed.Item("Reddit", "fan", "u/fan", "Up the Toffees", "", "r/Everton", 5000L);
        FanFeed.Item b = new FanFeed.Item("Reddit", "fan", "u/fan", "Up the Toffees", "", "r/Everton", 5000L);
        FanFeed.Item c = new FanFeed.Item("Reddit", "fan", "u/fan", "Up the Toffees!", "", "r/Everton", 5000L);
        assertEquals(a.key(), b.key());
        assertNotEquals(a.key(), c.key());
        assertFalse(a.bluesky());
    }
}
