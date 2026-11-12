package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Fans' posts: what may go on the family's TV, and Bluesky's and Reddit's answers read. Made-up posts only. */
public class FanPostsTest {
    @Test public void swearingAndSlursAreKeptOffHoweverSpelled() {
        assertTrue(FanFilter.blocked("what a f*cking game"));
        assertTrue(FanFilter.blocked("absolute SH1T defending"));
        assertTrue(FanFilter.blocked("fuuuuck me what a goal"));
        assertTrue(FanFilter.blocked("ref is a w@nker"));
        assertFalse(FanFilter.blocked("Scunthorpe away next, classic assist from the left"));
        assertFalse(FanFilter.blocked("Same eleven as Saturday please, the midfield finally clicked"));
    }

    @Test public void onlyEnglishWordsGetThrough() {
        assertTrue(FanFilter.ok("Same eleven as Saturday please, the midfield finally clicked"));
        assertFalse(FanFilter.ok("Vamos equipo, hoy ganamos seguro con el once titular"));
        assertFalse(FanFilter.ok("⚽⚽⚽ 🔥🔥"));
        assertFalse(FanFilter.ok("short"));
        assertFalse(FanFilter.ok("今日は勝つ、絶対に勝つ、頑張れユナイテッド"));
    }

    @Test public void textIsTidiedAndClipped() {
        assertEquals("Up the Toffees & keep it tight", FanFilter.clean("<p>Up the Toffees &amp;amp; keep it&nbsp;tight</p> https://x.co/abc"));
        String longer = "word ".repeat(60).trim();
        String c = FanFilter.clip(longer, FanFilter.MAX);
        assertTrue(c.length() <= FanFilter.MAX);
        assertTrue(c.endsWith("…"));
        assertTrue(FanFilter.badLabel("porn"));
        assertTrue(FanFilter.badLabel("graphic-media"));
        assertTrue(FanFilter.badLabel("!warn"));
        assertFalse(FanFilter.badLabel("verified"));
    }

    private static JSONObject post(String handle, String text, String lang, String label, boolean reply) throws Exception {
        JSONObject record = new JSONObject().put("text", text).put("createdAt", "2026-11-12T14:03:22.123Z").put("langs", new JSONArray().put(lang));
        if (reply) record.put("reply", new JSONObject());
        JSONObject p = new JSONObject().put("uri", "at://" + handle + "/1").put("record", record)
                .put("author", new JSONObject().put("handle", handle).put("displayName", "Fan " + handle).put("avatar", "https://x/" + handle + ".jpg"));
        if (label != null) p.put("labels", new JSONArray().put(new JSONObject().put("val", label)));
        return p;
    }

    @Test public void blueskyPostsAreReadAndFiltered() throws Exception {
        JSONArray posts = new JSONArray()
                .put(post("one.example", "Big one today, come on United we need this win", "en", null, false))
                .put(post("two.example", "Hoy ganamos seguro, vamos equipo", "es", null, false))
                .put(post("three.example", "Big game today and I am ready for it all", "en", "porn", false))
                .put(post("four.example", "Replying to someone about the game today now", "en", null, true))
                .put(post("five.example", "This ref is a total w4nker today honestly", "en", null, false));
        List<FanPosts.Post> got = FanPosts.bluesky(new JSONObject().put("posts", posts).toString());
        assertEquals(1, got.size());
        FanPosts.Post p = got.get(0);
        assertEquals(FanPosts.BLUESKY, p.source);
        assertEquals("@one.example", p.handle);
        assertEquals("Fan one.example", p.name);
        assertEquals(AdBreakLogic.time("2026-11-12T14:03:22Z"), p.at);
        assertTrue(FanPosts.bluesky("not json").isEmpty());
    }

    private static String entry(String author, String title, String content, String link) {
        return "<entry><author><name>/u/" + author + "</name><uri>https://www.reddit.com/user/" + author + "</uri></author>"
                + "<content type=\"html\">" + content + "</content><id>t1_" + author + "</id><link href=\"" + link + "\" />"
                + "<updated>2026-11-12T14:03:22+00:00</updated><title>" + title + "</title></entry>";
    }

    @Test public void redditThreadsAndCommentsAreReadAndFiltered() {
        String list = "<feed>" + entry("mod", "Post Match Thread: Last week", "", "https://www.reddit.com/r/x/comments/a/post/")
                + entry("mod", "Pre-Match Thread: Man United vs Everton", "", "https://www.reddit.com/r/x/comments/b/pre/")
                + entry("fan", "Some other post", "", "https://www.reddit.com/r/x/comments/c/other/") + "</feed>";
        List<String[]> posts = FanPosts.redditPosts(list);
        assertEquals(3, posts.size());
        assertEquals("https://www.reddit.com/r/x/comments/b/pre/", FanPosts.threadLink(posts));
        assertNull(FanPosts.threadLink(Arrays.asList(new String[][]{{"Transfer news", "l", "i"}})));
        String comments = "<feed>"
                + entry("redfan", "Re", "&lt;div class=&quot;md&quot;&gt;&lt;p&gt;Same eleven as Saturday please, the midfield finally clicked&lt;/p&gt;&lt;/div&gt;", "l")
                + entry("AutoModerator", "Re", "&lt;p&gt;Please keep it civil in the thread today&lt;/p&gt;", "l")
                + entry("angry", "Re", "&lt;p&gt;this ref is a f*cking joke and we all know it&lt;/p&gt;", "l")
                + "</feed>";
        List<FanPosts.Post> got = FanPosts.reddit(comments, false);
        assertEquals(1, got.size());
        assertEquals("u/redfan", got.get(0).handle);
        assertEquals("Same eleven as Saturday please, the midfield finally clicked", got.get(0).text);
        assertEquals(AdBreakLogic.time("2026-11-12T14:03:22Z"), got.get(0).at);
    }

    @Test public void onlyPostsAboutTheGameNotTheCountrysNews() {
        assertFalse(FanPosts.about("UK: Senior doctors in England put strike on hold after union talks", "England", "Croatia"));
        assertFalse(FanPosts.about("It is very late in the year for butterflies to still be out", "England", "Croatia"));
        assertTrue(FanPosts.about("England v Croatia tonight, cannot wait", "England", "Croatia"));
        assertTrue(FanPosts.about("England need a goal early tonight to settle the nerves", "England", "Croatia"));
        assertTrue(FanPosts.about("Man Utd to win this one comfortably, three points", "Manchester United", "Everton"));
        assertTrue(FanPosts.about("Big day #MUFC let's go", "Manchester United", "Everton"));
        assertFalse(FanPosts.about("Manchester United shares rose on the stock market today", "Manchester United", "Everton"));
    }

    @Test public void clubsGoToTheirSubredditsElseRSoccer() {
        assertEquals("reddevils", FanPosts.sub("Manchester United"));
        assertEquals("Everton", FanPosts.sub("Everton"));
        assertEquals("soccer", FanPosts.sub("Some Other FC"));
        assertEquals("now", FanPosts.ago(1_000, 30_000));
        assertEquals("2m", FanPosts.ago(0, 150_000));
        assertEquals("1h", FanPosts.ago(0, 3_700_000));
    }
}
