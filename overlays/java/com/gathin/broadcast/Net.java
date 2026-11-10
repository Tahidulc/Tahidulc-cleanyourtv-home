package com.gathin.broadcast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** The overlays' few downloads: crests, logos, the Premier League's player list and pictures (never the main thread). */
final class Net {
    private Net() { }

    static final String UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    /** An answer other than 200, with its code (a 404 is a plain "there is none", unlike a failed connection). */
    static final class HttpError extends IOException {
        final int code;

        HttpError(int code, String url) {
            super("HTTP " + code + " for " + url);
            this.code = code;
        }
    }

    /** The bytes at url (at most max), or an IOException. */
    static byte[] get(String url, int timeoutMs, int max) throws IOException {
        return get(url, UA, timeoutMs, timeoutMs, max);
    }

    /** The bytes at url (at most max), asked for as ua says, or an IOException (an HttpError for an answer not 200). */
    static byte[] get(String url, String ua, int connectMs, int readMs, int max) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", ua);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new HttpError(code, url);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > max) throw new IOException("too large: " + url);
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    static String text(String url) throws IOException {
        return new String(get(url, 20_000, 16 << 20), StandardCharsets.UTF_8);
    }
}
