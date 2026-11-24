package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Each programme's picture link read from the guide (XMLTV's icon, Xtream's xmltv.php the same), kept small. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GuidePicturesTest {
    private static final long NOW = Guide.time("20261124120000 +0000");

    private static Map<String, Guide.Schedule> read(String xml) throws Exception {
        Map<String, List<String>> ids = new HashMap<>();
        ids.put("one.invalid", Collections.singletonList("p:1"));
        ids.put("two.invalid", Arrays.asList("p:2", "p:3"));
        return Guide.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), ids, new HashMap<>(), NOW);
    }

    @Test public void iconsAreKeptWithTheirProgrammes() throws Exception {
        String xml = "<?xml version=\"1.0\"?><tv>"
                + "<channel id=\"one.invalid\"><display-name>Harbour One</display-name><icon src=\"https://logos.invalid/one.png\"/></channel>"
                + "<programme start=\"20261124113000 +0000\" stop=\"20261124123000 +0000\" channel=\"one.invalid\">"
                + "<title>The Lantern House</title><desc>Mara finds the letter.</desc>"
                + "<icon src=\"https://img.invalid/epg/lantern.jpg\" width=\"640\" height=\"360\"/></programme>"
                + "<programme start=\"20261124123000 +0000\" stop=\"20261124130000 +0000\" channel=\"one.invalid\">"
                + "<title>Coastal Kitchens</title><icon src=\"//img.invalid/epg/coast.jpg\"/></programme>"
                + "<programme start=\"20261124130000 +0000\" stop=\"20261124140000 +0000\" channel=\"one.invalid\">"
                + "<title>Quiz at Nine</title></programme>"
                + "<programme start=\"20261124140000 +0000\" stop=\"20261124150000 +0000\" channel=\"one.invalid\">"
                + "<title>Night Shift</title><icon src=\"javascript:alert(1)\"/></programme>"
                // XMLTV 1.1's pictures: a backdrop before the icon, the icon before a poster.
                + "<programme start=\"20261124113000 +0000\" stop=\"20261124130000 +0000\" channel=\"two.invalid\">"
                + "<title>Echoes of Iron</title><icon src=\"https://img.invalid/epg/echoes-icon.jpg\"/>"
                + "<image type=\"poster\" orient=\"P\">https://img.invalid/epg/echoes-poster.jpg</image>"
                + "<image type=\"backdrop\" orient=\"L\">https://img.invalid/epg/echoes-wide.jpg</image></programme>"
                + "<programme start=\"20261124130000 +0000\" stop=\"20261124140000 +0000\" channel=\"two.invalid\">"
                + "<title>Pip and the Moon</title><image type=\"poster\">https://img.invalid/epg/pip-poster.jpg</image></programme>"
                + "</tv>";
        Map<String, Guide.Schedule> g = read(xml);
        Guide.Schedule one = g.get("p:1");
        assertEquals(4, one.start.length);
        assertEquals("https://img.invalid/epg/lantern.jpg", one.pic(0));
        assertEquals("https://img.invalid/epg/coast.jpg", one.pic(1));
        assertEquals("", one.pic(2));
        assertEquals("", one.pic(3));
        assertEquals("", one.pic(9));
        // The same programme on both channels with this guide id: one link, kept once.
        Guide.Schedule two = g.get("p:2"), three = g.get("p:3");
        assertEquals("https://img.invalid/epg/echoes-wide.jpg", two.pic(0));
        assertEquals("https://img.invalid/epg/pip-poster.jpg", two.pic(1));
        assertEquals(two.pic(0), three.pic(0));
        assertTrue(two.links == one.links);
        assertEquals(4, one.links.size());
    }

    @Test public void aGuideWithNoPicturesCostsNothing() throws Exception {
        String xml = "<tv><programme start=\"20261124113000 +0000\" stop=\"20261124123000 +0000\" channel=\"one.invalid\">"
                + "<title>Market Day</title></programme></tv>";
        Guide.Schedule s = read(xml).get("p:1");
        assertNull(s.pic);
        assertEquals(0, s.links.size());
        assertEquals("", s.pic(0));
    }

    @Test public void linksArePackedAndCleaned() {
        assertEquals("https://img.invalid/a.jpg", PicLinks.clean("  https://img.invalid/a.jpg "));
        assertEquals("https://img.invalid/b.jpg", PicLinks.clean("//img.invalid/b.jpg"));
        assertEquals("http://img.invalid/c%20d.jpg", PicLinks.clean("http://img.invalid/c d.jpg"));
        assertEquals("", PicLinks.clean("file:///data/x.jpg"));
        assertEquals("", PicLinks.clean("ftp://img.invalid/x.jpg"));
        assertEquals("", PicLinks.clean(null));
        assertEquals("", PicLinks.clean("https://"));

        PicLinks.Builder b = new PicLinks.Builder();
        int a = b.add("https://img.invalid/epg/1.jpg"), c = b.add("https://img.invalid/epg/1.jpg"), d = b.add("https://other.invalid/2.png");
        assertEquals(a, c);
        assertEquals(-1, b.add(""));
        PicLinks l = b.build();
        assertEquals(2, l.size());
        assertEquals("https://img.invalid/epg/1.jpg", l.get(a));
        assertEquals("https://other.invalid/2.png", l.get(d));
        assertEquals("", l.get(-1));
    }

    @Test public void ninetyThousandPicturesStaySmall() {
        // The family's playlists: about 90,000 programmes, each its own picture on a few hosts.
        PicLinks.Builder b = new PicLinks.Builder();
        for (int i = 0; i < 90_000; i++) b.add("https://img" + (i % 4) + ".invalid/epg/images/" + (10_000_000 + i) + ".jpg");
        PicLinks l = b.build();
        assertEquals(90_000, l.size());
        assertEquals("https://img3.invalid/epg/images/10089999.jpg", l.get(89_999));
        // File names packed as bytes and two ints each: under 2 MB, where the addresses as strings would be ten.
        assertTrue("bytes " + l.bytes(), l.bytes() < 2_000_000);
    }

    @Test public void imagesRankLandscapeFirst() {
        assertEquals(3, Guide.imageRank("backdrop", null));
        assertEquals(3, Guide.imageRank("still", "L"));
        assertEquals(2, Guide.imageRank(null, "L"));
        assertEquals(1, Guide.imageRank("poster", "P"));
        assertEquals("https://i.invalid/wide.jpg", Guide.pick("https://i.invalid/icon.jpg", "https://i.invalid/wide.jpg", 3));
        assertEquals("https://i.invalid/icon.jpg", Guide.pick("https://i.invalid/icon.jpg", "https://i.invalid/poster.jpg", 1));
        assertEquals("https://i.invalid/poster.jpg", Guide.pick("", "https://i.invalid/poster.jpg", 1));
    }
}
