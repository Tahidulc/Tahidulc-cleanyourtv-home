package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class XtreamTest {
    @Test public void serverBecomesABase() {
        assertEquals("http://host.example:8080", Xtream.base("host.example:8080"));
        assertEquals("http://host.example:8080", Xtream.base(" http://host.example:8080/ "));
        assertEquals("https://h.example", Xtream.base("https://h.example/player_api.php?username=a&password=b"));
        assertEquals("http://h:80", Xtream.base("http://h:80/get.php?username=a"));
        assertEquals("", Xtream.base("  "));
    }

    @Test public void apiAndGuideAddresses() {
        String b = "http://h:8080";
        assertEquals("http://h:8080/player_api.php?username=me&password=p%40ss", Xtream.api(b, "me", "p@ss", null));
        assertEquals("http://h:8080/player_api.php?username=me&password=x&action=get_live_streams", Xtream.api(b, "me", "x", "get_live_streams"));
        assertEquals("http://h:8080/xmltv.php?username=me&password=x", Xtream.xmltv(b, "me", "x"));
    }

    @Test public void liveStreamAddress() {
        assertEquals("http://h:8080/live/me/pw/1234.m3u8", Xtream.stream("http://h:8080", "me", "pw", "1234", "m3u8"));
        assertEquals("http://h:8080/live/my%20name/p%26w/9.ts", Xtream.stream("http://h:8080", "my name", "p&w", "9", "ts"));
        assertEquals("http://h/live/a/b/1.ts", Xtream.stream("http://h", "a", "b", "1", ""));
    }

    @Test public void hlsWhenAllowedElseTs() {
        assertEquals("m3u8", Xtream.pickFormat(Arrays.asList("ts", "m3u8", "rtmp")));
        assertEquals("ts", Xtream.pickFormat(Arrays.asList("ts", "rtmp")));
        assertEquals("ts", Xtream.pickFormat(Collections.emptyList()));
        assertEquals("m3u8", Xtream.pickFormat(Collections.singletonList("rtmp")));
    }
}
