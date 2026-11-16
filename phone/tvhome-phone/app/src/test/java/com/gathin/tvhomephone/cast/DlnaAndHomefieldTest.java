package com.gathin.tvhomephone.cast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhomephone.live.Channel;

import org.json.JSONObject;
import org.junit.Test;

/** DLNA sets found and told what to play, and Homefield TV's link: the words on the wire, without a network. */
public class DlnaAndHomefieldTest {
    @Test public void aRenderersAnswerAndDescriptionAreRead() {
        String answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.168.1.50:1400/xml/device.xml\r\n"
                + "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n";
        assertEquals("http://192.168.1.50:1400/xml/device.xml", Dlna.location(answer));
        assertNull(Dlna.location("HTTP/1.1 200 OK\r\nST: x\r\n\r\n"));

        String xml = "<?xml version=\"1.0\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\"><device>"
                + "<friendlyName>Living Room TV &amp; Sound</friendlyName><serviceList>"
                + "<service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>"
                + "<controlURL>/upnp/control/rc</controlURL></service>"
                + "<service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>"
                + "<controlURL>upnp/control/avt</controlURL></service>"
                + "</serviceList></device></root>";
        Dlna.Renderer r = Dlna.parse(xml, "http://192.168.1.50:1400/xml/device.xml");
        assertNotNull(r);
        assertEquals("Living Room TV & Sound", r.name);
        assertEquals("http://192.168.1.50:1400/xml/upnp/control/avt", r.control);
        // URLBase, when given, is what the address is made whole against.
        Dlna.Renderer b = Dlna.parse(xml.replace("<device>", "<URLBase>http://192.168.1.50:9000/</URLBase><device>"),
                "http://192.168.1.50:1400/xml/device.xml");
        assertEquals("http://192.168.1.50:9000/upnp/control/avt", b.control);
        // No AVTransport: not a renderer.
        assertNull(Dlna.parse(xml.replace("AVTransport", "ConnectionManager"), "http://x/"));
    }

    @Test public void theStreamIsDescribedAndEscaped() {
        String didl = Dlna.didl("Sports & News <1>", "http://h/live/u/p/1.ts?a=1&b=2", "video/mp2t");
        assertTrue(didl, didl.contains("<dc:title>Sports &amp; News &lt;1&gt;</dc:title>"));
        assertTrue(didl, didl.contains("protocolInfo=\"http-get:*:video/mp2t:*\">http://h/live/u/p/1.ts?a=1&amp;b=2</res>"));
        String env = Dlna.envelope("Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
        assertTrue(env, env.contains("<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID>"));
        assertTrue(Dlna.search("urn:x").contains("MAN: \"ssdp:discover\"\r\n"));
    }

    @Test public void homefieldTvIsToldTheChannelPlainly() throws Exception {
        assertEquals("1234", HomefieldLink.streamId("http://p.example:8080/live/u/p/1234.ts"));
        assertEquals("1234", HomefieldLink.streamId("http://p.example:8080/u/p/1234"));
        assertEquals("77", HomefieldLink.streamId("http://p.example/live/u/p/77.m3u8?token=9"));
        assertEquals("", HomefieldLink.streamId("http://h.example/hls/news/index.m3u8"));
        Channel ch = new Channel("k1", "UK: Example Sports 1 FHD", "UK Sports", "", "http://p.example/live/u/p/1234.ts",
                "examplesports1.uk", "Agent/1", "", 101);
        JSONObject body = HomefieldLink.body(ch, "Pixel");
        assertEquals(1, body.getInt("v"));
        assertEquals("live", body.getString("kind"));
        assertEquals("UK: Example Sports 1 FHD", body.getString("name"));
        assertEquals("examplesports1.uk", body.getString("tvgId"));
        assertEquals("1234", body.getString("streamId"));
        assertEquals("Agent/1", body.getString("userAgent"));
        assertEquals("Pixel", body.getString("from"));
        assertEquals("Bedroom TV", HomefieldLink.helloName("{\"app\":\"homefieldtv\",\"v\":1,\"name\":\"Bedroom TV\"}"));
        assertEquals("TV", HomefieldLink.helloName("{\"app\":\"homefieldtv\",\"v\":1}"));
        assertNull(HomefieldLink.helloName("{\"tv\":\"Lounge\"}"));
        assertNull(HomefieldLink.helloName("not json"));
        assertFalse(CastDevice.homefield("A", "1.2.3.4", 1).id.equals(CastDevice.homefield("A", "1.2.3.5", 1).id));
    }
}
