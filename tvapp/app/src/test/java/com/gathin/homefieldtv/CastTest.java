package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.homefieldtv.engine.Channel;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** The phone's channel found in this TV's own playlists, who may send one, and the receiver's small HTTP pieces. */
public class CastTest {
    private static Channel ch(String key, String name, String tvgId) {
        return new Channel(key, name, "Made up", "", "http://provider.example/" + key, tvgId, "", "", 0);
    }

    private final Channel mainEvent = ch("x:1234", "UK: Fieldside Sports Main Event HD", "FieldsideMain.uk");
    private final Channel newsOne = ch("p2:x:5678", "Northgate News", "northgate.news.uk");
    private final Channel four = ch("m:7f3a", "Riverside Four", "");
    private final List<Channel> all = Arrays.asList(mainEvent, newsOne, four);

    @Test public void byStreamIdFirst() {
        assertSame(newsOne, Cast.match(all, "5678", "", ""));
        // The stream id wins over a guide id and a name that point elsewhere.
        assertSame(mainEvent, Cast.match(all, "1234", "northgate.news.uk", "Riverside Four"));
    }

    @Test public void thenByGuideIdWhateverItsCase() {
        assertSame(mainEvent, Cast.match(all, "999", "fieldsidemain.UK", "Riverside Four"));
    }

    @Test public void thenByTheTidiedName() {
        assertSame(mainEvent, Cast.match(all, "", "", "Fieldside Sports Main Event"));
        assertSame(four, Cast.match(all, null, null, "riverside four"));
    }

    @Test public void nothingWhenThePlaylistsDoNotHaveIt() {
        assertNull(Cast.match(all, "4321", "elsewhere.tv", "A Channel Not Here"));
        assertNull(Cast.match(all, "", "", ""));
        assertNull(Cast.match(null, "1234", "", ""));
    }

    @Test public void streamIdsComeOnlyFromXtreamKeys() {
        assertEquals("1234", Cast.streamIdOf("x:1234"));
        assertEquals("5678", Cast.streamIdOf("p2:x:5678"));
        assertEquals("", Cast.streamIdOf("m:7f3a"));
        assertEquals("", Cast.streamIdOf("m:http://box:8080/live"));
        assertEquals("", Cast.streamIdOf("x:"));
        assertEquals("", Cast.streamIdOf(null));
    }

    private static byte[] v4(int a, int b, int c, int d) {
        return new byte[]{(byte) a, (byte) b, (byte) c, (byte) d};
    }

    @Test public void onlyTheHomeNetworkMaySend() {
        assertTrue(Cast.isPrivate(v4(192, 168, 1, 20)));
        assertTrue(Cast.isPrivate(v4(10, 0, 0, 5)));
        assertTrue(Cast.isPrivate(v4(172, 16, 0, 1)));
        assertTrue(Cast.isPrivate(v4(172, 31, 255, 254)));
        assertTrue(Cast.isPrivate(v4(169, 254, 1, 1)));
        assertTrue(Cast.isPrivate(v4(127, 0, 0, 1)));
        assertFalse(Cast.isPrivate(v4(172, 32, 0, 1)));
        assertFalse(Cast.isPrivate(v4(8, 8, 8, 8)));
        assertFalse(Cast.isPrivate(v4(100, 64, 1, 1)));
        assertFalse(Cast.isPrivate(null));
        assertFalse(Cast.isPrivate(new byte[3]));

        byte[] loop = new byte[16];
        loop[15] = 1;
        assertTrue(Cast.isPrivate(loop));
        byte[] linkLocal = new byte[16];
        linkLocal[0] = (byte) 0xFE;
        linkLocal[1] = (byte) 0x80;
        assertTrue(Cast.isPrivate(linkLocal));
        byte[] unique = new byte[16];
        unique[0] = (byte) 0xFD;
        assertTrue(Cast.isPrivate(unique));
        byte[] global = new byte[16];
        global[0] = 0x20;
        global[1] = 0x01;
        assertFalse(Cast.isPrivate(global));
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xFF;
        mapped[11] = (byte) 0xFF;
        System.arraycopy(v4(192, 168, 1, 20), 0, mapped, 12, 4);
        assertTrue(Cast.isPrivate(mapped));
        System.arraycopy(v4(8, 8, 4, 4), 0, mapped, 12, 4);
        assertFalse(Cast.isPrivate(mapped));
    }

    @Test public void stopAnswers() {
        assertEquals("{\"ok\":true}", Cast.stopReply(true));
        assertEquals("{\"ok\":false}", Cast.stopReply(false));
        assertEquals(47830, Cast.PORT);
    }

    @Test public void jsonStrings() {
        assertEquals("\"Living Room TV\"", Cast.quote("Living Room TV"));
        assertEquals("\"Say \\\"hi\\\" \\\\ now\\u000a\"", Cast.quote("Say \"hi\" \\ now\n"));
        assertEquals("\"\"", Cast.quote(null));
    }

    @Test public void contentLengthFromTheHead() {
        assertEquals(42, Cast.contentLength("POST /play HTTP/1.1\r\nHost: tv\r\nContent-Length: 42\r\n\r\n"));
        assertEquals(7, Cast.contentLength("POST /play HTTP/1.1\r\ncontent-length:7\r\n\r\n"));
        assertEquals(-1, Cast.contentLength("GET /hello HTTP/1.1\r\nHost: tv\r\n\r\n"));
        assertEquals(-1, Cast.contentLength("POST /play HTTP/1.1\r\nContent-Length: lots\r\n\r\n"));
    }
}
