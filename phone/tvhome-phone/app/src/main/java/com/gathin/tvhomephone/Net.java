package com.gathin.tvhomephone;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Plain HTTP reads and posts with short timeouts, for every part (call off the main thread). */
public final class Net {
    private Net() {}

    public static String get(String url) throws IOException {
        return get(url, null, 10_000);
    }

    public static String get(String url, Map<String, String> headers, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "TVHome-phone/1.0");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        try {
            int code = c.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code + " " + url);
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    /** POSTs JSON; returns the reply's body, or throws with its status (401: not paired). */
    public static String post(String url, JSONObject body, Map<String, String> headers, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        byte[] b = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(b.length);
        try {
            try (OutputStream o = c.getOutputStream()) {
                o.write(b);
            }
            int code = c.getResponseCode();
            if (code >= 400) throw new HttpError(code);
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    public static final class HttpError extends IOException {
        public final int code;

        HttpError(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = s.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
