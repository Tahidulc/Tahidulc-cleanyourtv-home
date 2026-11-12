package com.gathin.tvhomephone.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.live.BreakingNews;
import com.gathin.tvhomephone.live.BreakingNews.Story;

import org.junit.Test;

import java.util.List;

/** The headlines' reading (an RSS feed with pictures), choice and "time ago". */
public class HeadlinesTest {
    private static final long NOW = 1_800_000_000_000L;

    private static final String FEED = "<?xml version=\"1.0\"?><rss xmlns:media=\"http://search.yahoo.com/mrss/\"><channel>"
            + "<item><title><![CDATA[Lead story &amp; more]]></title><description><![CDATA[Its line]]></description>"
            + "<link>https://www.bbc.co.uk/news/articles/one?at_medium=RSS</link><pubDate>Fri, 15 Jan 2027 08:00:00 GMT</pubDate>"
            + "<media:thumbnail width=\"240\" height=\"135\" url=\"https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/abc.jpg\"/></item>"
            + "<item><title>Watch: A video</title><link>https://www.bbc.co.uk/news/videos/two</link></item>"
            + "<item><title>No picture</title><link>https://www.bbc.co.uk/news/articles/three</link></item>"
            + "<item><title>Lead story &amp; more</title><link>https://www.bbc.co.uk/news/articles/one?at_medium=other</link></item>"
            + "</channel></rss>";

    @Test public void parsesPicturesAtTheLargeSize() {
        List<Story> all = BreakingNews.parse(FEED);
        assertEquals(4, all.size());
        assertEquals("Lead story & more", all.get(0).title);
        assertEquals("https://ichef.bbci.co.uk/ace/standard/976/cpsprodpb/abc.jpg", all.get(0).picture);
        assertTrue(all.get(0).at > 0);
        assertEquals("", all.get(2).picture);
    }

    @Test public void picksStoriesOnceWithoutFeatures() {
        List<Story> all = BreakingNews.parse(FEED);
        long at = all.get(0).at;
        List<Story> out = Headlines.pick(all, at + 3600_000L, 10);
        assertEquals(2, out.size());
        assertEquals("Lead story & more", out.get(0).title);
        assertEquals("No picture", out.get(1).title);
        assertEquals(1, Headlines.pick(all, at, 1).size());
    }

    @Test public void oldStoriesLeftOut() {
        Story old = new Story("Old news", "", "https://www.bbc.co.uk/a", NOW - 3 * 86_400_000L, "");
        Story undated = new Story("Undated", "", "https://www.bbc.co.uk/b", 0, "");
        List<Story> out = Headlines.pick(java.util.Arrays.asList(old, undated), NOW, 10);
        assertEquals(1, out.size());
        assertEquals("Undated", out.get(0).title);
    }

    @Test public void features() {
        assertTrue(Headlines.feature("Watch: Moment the bridge fell"));
        assertTrue(Headlines.feature("Quiz of the week"));
        assertTrue(Headlines.feature("Listen: The podcast"));
        assertFalse(Headlines.feature("Prime minister announces plan"));
        assertFalse(Headlines.feature("Watchdog fines water firm"));
    }

    @Test public void timeAgo() {
        assertEquals("", Headlines.ago(0, NOW));
        assertEquals("Just now", Headlines.ago(NOW - 30_000L, NOW));
        assertEquals("12 min ago", Headlines.ago(NOW - 12 * 60_000L, NOW));
        assertEquals("3 hr ago", Headlines.ago(NOW - 3 * 3600_000L - 5 * 60_000L, NOW));
        assertEquals("Yesterday", Headlines.ago(NOW - 26 * 3600_000L, NOW));
        assertEquals("3 days ago", Headlines.ago(NOW - 3 * 86_400_000L, NOW));
        assertEquals("Just now", Headlines.ago(NOW + 60_000L, NOW));
    }
}
