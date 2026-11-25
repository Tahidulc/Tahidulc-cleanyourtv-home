package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;

import com.gathin.broadcast.PicSources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The guide's own programme pictures and categories, kept as the guide is read (the first of the programme pictures'
 * sources, as the phone keeps them): XMLTV's icon, a landscape image before it, a poster after it, a few bytes a
 * programme; and what its categories say it is as a byte. Invented channels and programmes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GuidePicturesTest {
    private static final long NOW = Guide.time("20261124120000 +0000");

    @Test public void iconsAndCategoriesAreKeptWithTheirProgrammes() throws Exception {
        String xml = "<?xml version=\"1.0\"?><tv>"
                + "<channel id=\"one.invalid\"><display-name>Harbour One</display-name></channel>"
                + "<programme start=\"20261124113000 +0000\" stop=\"20261124123000 +0000\" channel=\"one.invalid\">"
                + "<title>The Lantern House</title><desc>Mara finds the letter.</desc><category>Drama</category>"
                + "<icon src=\"https://img.invalid/epg/lantern.jpg\"/></programme>"
                + "<programme start=\"20261124123000 +0000\" stop=\"20261124133000 +0000\" channel=\"one.invalid\">"
                + "<title>Match Night</title><category>Sport</category><category>Football</category>"
                + "<image type=\"poster\">https://img.invalid/epg/poster.jpg</image>"
                + "<image type=\"backdrop\">https://img.invalid/epg/wide.jpg</image></programme>"
                + "<programme start=\"20261124133000 +0000\" stop=\"20261124143000 +0000\" channel=\"one.invalid\">"
                + "<title>Evening Headlines</title><category>News</category></programme>"
                + "</tv>";
        Map<String, List<String>> ids = new HashMap<>();
        ids.put("one.invalid", Collections.singletonList("p:1"));
        Map<String, Guide.Schedule> got = Guide.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), ids, new HashMap<>(), NOW);
        Guide.Schedule s = got.get("p:1");
        assertEquals(3, s.start.length);
        assertEquals("https://img.invalid/epg/lantern.jpg", s.pic(0));
        assertEquals("https://img.invalid/epg/wide.jpg", s.pic(1));
        assertEquals("", s.pic(2));
        assertEquals("", s.category(0));
        assertEquals(PicSources.FOOTBALL, s.category(1));
        assertEquals(PicSources.NEWS, s.category(2));
        // Kept through the guide's window and a time shift.
        Guide.Schedule w = Guide.window(s, NOW - 3600_000L, NOW + 3600_000L, NOW + 3600_000L);
        assertEquals("https://img.invalid/epg/wide.jpg", w.pic(1));
        assertEquals(PicSources.NEWS, GuideShift.by(s, 3600_000L).category(2));
    }
}
