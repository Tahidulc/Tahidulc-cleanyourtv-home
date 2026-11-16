package com.gathin.tvhomephone.cast;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Smart TVs, consoles and media players that take a stream to play (DLNA/UPnP media renderers): found by asking the
 * Wi-Fi (SSDP), each one's description read for its name and the address it takes orders at (AVTransport), then told
 * the stream's address with its title (SetAVTransportURI) and to play. A transport stream goes to them as it is.
 */
public final class Dlna {
    private Dlna() {}

    /** A renderer as its description gives it: its name and where AVTransport's orders go. */
    static final class Renderer {
        final String name, control;

        Renderer(String name, String control) {
            this.name = name;
            this.control = control;
        }
    }

    private static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    private static final String[] LOOK_FOR = {"urn:schemas-upnp-org:device:MediaRenderer:1", AVT};

    /** Asks the Wi-Fi for renderers for about ms; each found reported once (off the main thread). Blocking. */
    static void find(Context c, long ms, Consumer<CastDevice> found) {
        WifiManager wifi = c.getApplicationContext().getSystemService(WifiManager.class);
        WifiManager.MulticastLock lock = wifi == null ? null : wifi.createMulticastLock("homefield-dlna");
        Set<String> seen = new HashSet<>();
        try {
            if (lock != null) lock.acquire();
            try (DatagramSocket s = new DatagramSocket()) {
                s.setSoTimeout(500);
                InetAddress group = InetAddress.getByName("239.255.255.250");
                for (int round = 0; round < 2; round++) {
                    for (String st : LOOK_FOR) {
                        byte[] ask = search(st).getBytes(StandardCharsets.US_ASCII);
                        s.send(new DatagramPacket(ask, ask.length, group, 1900));
                    }
                }
                long end = System.currentTimeMillis() + ms;
                byte[] buf = new byte[4096];
                while (System.currentTimeMillis() < end) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    try {
                        s.receive(p);
                    } catch (SocketTimeoutException again) {
                        continue;
                    }
                    String location = location(new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII));
                    if (location == null || !seen.add(location)) continue;
                    try {
                        Renderer r = parse(get(location), location);
                        if (r != null) found.accept(CastDevice.dlna(r.name, r.control));
                    } catch (IOException ignored) {
                        // that one didn't describe itself
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // no Wi-Fi, or multicast not allowed here
        } finally {
            if (lock != null && lock.isHeld()) lock.release();
        }
    }

    /** The stream on a renderer: what it plays now stopped, the new address with its title, then play. Blocking. */
    static void play(String control, String url, String title, String mime) throws IOException {
        try {
            soap(control, "Stop", "<InstanceID>0</InstanceID>");
        } catch (IOException ignored) {
            // nothing was playing
        }
        soap(control, "SetAVTransportURI", "<InstanceID>0</InstanceID><CurrentURI>" + escape(url) + "</CurrentURI>"
                + "<CurrentURIMetaData>" + escape(didl(title, url, mime)) + "</CurrentURIMetaData>");
        soap(control, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>");
    }

    static void stop(String control) throws IOException {
        soap(control, "Stop", "<InstanceID>0</InstanceID>");
    }

    private static void soap(String control, String action, String args) throws IOException {
        byte[] body = envelope(action, args).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection) new URL(control).openConnection();
        try {
            c.setConnectTimeout(4_000);
            c.setReadTimeout(8_000);
            c.setDoOutput(true);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
            c.setRequestProperty("SOAPACTION", "\"" + AVT + "#" + action + "\"");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(body);
            }
            int code = c.getResponseCode();
            if (code >= 300) throw new IOException("renderer said " + code);
        } finally {
            c.disconnect();
        }
    }

    private static String get(String location) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(location).openConnection();
        try {
            c.setConnectTimeout(3_000);
            c.setReadTimeout(4_000);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && b.size() < 512 * 1024) b.write(buf, 0, n);
                return b.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    // ---- pure ------------------------------------------------------------------------------------------------------

    /** Pure: SSDP's search for one kind of device. */
    static String search(String st) {
        return "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: " + st + "\r\n\r\n";
    }

    /** Pure: where a renderer's description is, from its SSDP answer (null for none). */
    static String location(String answer) {
        for (String line : answer.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("LOCATION")) {
                String v = line.substring(colon + 1).trim();
                return v.toLowerCase(Locale.ROOT).startsWith("http") ? v : null;
            }
        }
        return null;
    }

    /** Pure: a device description's name and AVTransport's control address (made whole); null when it has none. */
    static Renderer parse(String xml, String location) {
        if (xml == null) return null;
        String name = text(xml, "friendlyName");
        String base = text(xml, "URLBase");
        int from = 0;
        while (true) {
            int start = xml.indexOf("<service>", from);
            if (start < 0) return null;
            int end = xml.indexOf("</service>", start);
            if (end < 0) return null;
            String block = xml.substring(start, end);
            String type = text(block, "serviceType");
            String control = text(block, "controlURL");
            if (type != null && type.contains(":AVTransport:") && control != null && !control.isEmpty()) {
                String whole = whole(base != null && !base.isEmpty() ? base : location, control);
                return new Renderer(name == null || name.isEmpty() ? "TV" : name.trim(), whole);
            }
            from = end + 10;
        }
    }

    private static String whole(String base, String ref) {
        try {
            return URI.create(base).resolve(ref.trim()).toString();
        } catch (IllegalArgumentException e) {
            return ref.trim();
        }
    }

    /** Pure: the words inside the first <tag>…</tag> (entities undone); null when it isn't there. */
    static String text(String xml, String tag) {
        int open = xml.indexOf("<" + tag + ">");
        if (open < 0) return null;
        int from = open + tag.length() + 2;
        int close = xml.indexOf("</" + tag + ">", from);
        if (close < 0) return null;
        return unescape(xml.substring(from, close).trim());
    }

    /** Pure: a SOAP envelope for one AVTransport action. */
    static String envelope(String action, String args) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:" + action + " xmlns:u=\"" + AVT + "\">" + args + "</u:" + action + "></s:Body></s:Envelope>";
    }

    /** Pure: the stream described for the renderer (its title, a broadcast, the address with its kind). */
    static String didl(String title, String url, String mime) {
        return "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
                + " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                + "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>" + escape(title) + "</dc:title>"
                + "<upnp:class>object.item.videoItem.videoBroadcast</upnp:class>"
                + "<res protocolInfo=\"http-get:*:" + mime + ":*\">" + escape(url) + "</res></item></DIDL-Lite>";
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
}
