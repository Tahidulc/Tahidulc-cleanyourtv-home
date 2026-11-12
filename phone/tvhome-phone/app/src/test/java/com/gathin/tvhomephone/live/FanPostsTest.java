package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Reading Bluesky's and Reddit's answers into posts, and mixing them. */
public class FanPostsTest {
    private static JSONObject bsky(String handle, String name, String text, String at, String lang, String label) throws Exception {
        JSONObject record = new JSONObject().put("text", text).put("createdAt", at);
        if (lang != null) record.put("langs", new JSONArray().put(lang));
        JSONObject p = new JSONObject().put("author", new JSONObject().put("handle", handle).put("displayName", name).put("avatar", "https://cdn.example/a.jpg"))
                .put("record", record);
        if (label != null) p.put("labels", new JSONArray().put(new JSONObject().put("val", label)));
        return p;
    }

    @Test public void blueskyPostsFitToShow() throws Exception {
        JSONObject root = new JSONObject().put("posts", new JSONArray()
                .put(bsky("lou.bsky.social", "Goodison Lou", "Away end already in full voice. Nothing to lose today.", "2026-11-12T14:58:00Z", "en", null))
                .put(bsky("x.bsky.social", "X", "Vamos equipo hoy ganamos seguro hermanos", "2026-11-12T14:58:00Z", "es", null))
                .put(bsky("y.bsky.social", "Y", "A perfectly nice sentence about the game today", "2026-11-12T14:58:00Z", "en", "porn"))
                .put(bsky("z.bsky.social", "", "What a shit line-up that is for a game like this", "2026-11-12T14:58:00Z", "en", null)));
        List<FanPosts.Post> ps = FanPosts.bluesky(root);
        assertEquals(1, ps.size());
        assertEquals("Goodison Lou", ps.get(0).name);
        assertEquals("@lou.bsky.social", ps.get(0).handle);
        assertEquals(FanPosts.BLUESKY, ps.get(0).source);
        assertTrue(ps.get(0).at > 0);
    }

    private static final String FEED = "<feed><entry><author><name>/u/someone</name></author><title>Pre-Match Thread: Manchester United vs Everton</title>"
            + "<updated>2026-11-12T13:00:00+00:00</updated><link href=\"https://www.reddit.com/r/reddevils/comments/abc123/prematch/\"/>"
            + "<content type=\"html\">&lt;p&gt;Discuss&lt;/p&gt;</content></entry>"
            + "<entry><author><name>/u/other</name></author><title>Post-Match Thread: last week</title><updated>2026-11-05T13:00:00+00:00</updated>"
            + "<link href=\"https://www.reddit.com/r/reddevils/comments/zzz999/post/\"/><content type=\"html\"></content></entry></feed>";

    @Test public void redditsPreMatchThreadIsFound() {
        List<String[]> e = FanPosts.atom(FEED);
        assertEquals(2, e.size());
        assertEquals("/u/someone", e.get(0)[1]);
        assertEquals("abc123", FanPosts.thread(e));
        List<String[]> none = new ArrayList<>();
        none.add(new String[]{"Post-Match Thread", "/u/a", "", "", "https://www.reddit.com/r/x/comments/q1/"});
        assertNull(FanPosts.thread(none));
    }

    @Test public void redditCommentsBecomePosts() {
        List<String[]> e = new ArrayList<>();
        e.add(new String[]{"", "/u/stretford_sam", "2026-11-12T14:57:00+00:00", "<div class=\"md\"><p>Same eleven as Saturday please, midfield finally clicked.</p></div>", ""});
        e.add(new String[]{"", "/u/AutoModerator", "2026-11-12T14:57:00+00:00", "<p>Please keep it civil in here everyone thanks</p>", ""});
        e.add(new String[]{"", "/u/rude1", "2026-11-12T14:57:00+00:00", "<p>Bench the useless bastard already please</p>", ""});
        e.add(new String[]{"[NSFW] something", "/u/x", "2026-11-12T14:57:00+00:00", "<p>Fine words about the match today though</p>", ""});
        List<FanPosts.Post> ps = FanPosts.redditPosts(e, true);
        assertEquals(1, ps.size());
        assertEquals("u/stretford_sam", ps.get(0).handle);
        assertEquals("Same eleven as Saturday please, midfield finally clicked.", ps.get(0).text);
        assertEquals(FanPosts.REDDIT, ps.get(0).source);
    }

    @Test public void mixedNewestFirstNoRepeatsNoLongRunsOfOneSource() {
        List<FanPosts.Post> in = new ArrayList<>();
        for (int i = 0; i < 5; i++) in.add(new FanPosts.Post(FanPosts.BLUESKY, "b" + i, "@b" + i, "", "bluesky words " + i, 100 - i));
        in.add(new FanPosts.Post(FanPosts.REDDIT, "r", "u/r", "", "reddit words", 50));
        in.add(new FanPosts.Post(FanPosts.BLUESKY, "dup", "@d", "", "bluesky words 0", 99));
        List<FanPosts.Post> out = FanPosts.mix(in, 4);
        assertEquals(4, out.size());
        assertEquals("b0", out.get(0).name);
        assertEquals(FanPosts.REDDIT, out.get(2).source);
    }

    @Test public void subsAndAgo() {
        assertEquals("reddevils", FanPosts.sub("Manchester United", "Man United"));
        assertEquals("Everton", FanPosts.sub(null, "Everton"));
        assertEquals("soccer", FanPosts.sub("Some Club"));
        assertEquals("now", FanPosts.ago(1_000_000L, 1_030_000L));
        assertEquals("3m", FanPosts.ago(1_000_000L, 1_180_000L));
        assertEquals("2h", FanPosts.ago(1_000_000L, 1_000_000L + 7_200_000L));
    }
}
