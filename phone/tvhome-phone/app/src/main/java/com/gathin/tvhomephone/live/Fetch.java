package com.gathin.tvhomephone.live;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

/**
 * Streams a playlist, an API reply or a guide from the family's provider: long read timeouts for large files, gzip
 * opened whether or not the server says so, and errors that never repeat the address (it can hold the login).
 */
final class Fetch {
    private Fetch() {}

    static final String AGENT = "TVHome-phone/1.0";

    static InputStream open(String url, String agent) throws IOException {
        HttpURLConnection c;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
        } catch (IllegalArgumentException | java.net.MalformedURLException e) {
            throw new IOException("That address isn't valid");
        }
        c.setConnectTimeout(15_000);
        c.setReadTimeout(60_000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", agent == null || agent.isEmpty() ? AGENT : agent);
        c.setRequestProperty("Accept-Encoding", "gzip");
        int code;
        try {
            code = c.getResponseCode();
        } catch (java.net.UnknownHostException e) {
            c.disconnect();
            throw new IOException("Couldn't find that server");
        } catch (java.net.SocketTimeoutException e) {
            c.disconnect();
            throw new IOException("The server didn't answer in time");
        } catch (IOException e) {
            c.disconnect();
            throw new IOException("Couldn't reach the server");
        }
        if (code >= 400) {
            c.disconnect();
            throw new IOException(code == 401 || code == 403 ? "The server refused the login (HTTP " + code + ")" : "The server answered HTTP " + code);
        }
        InputStream raw = new BufferedInputStream(c.getInputStream(), 64 * 1024);
        raw.mark(4);
        int b1 = raw.read(), b2 = raw.read();
        raw.reset();
        InputStream body = b1 == 0x1f && b2 == 0x8b ? new GZIPInputStream(raw, 64 * 1024) : raw;
        return new FilterInputStream(body) {
            @Override public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    c.disconnect();
                }
            }
        };
    }
}
