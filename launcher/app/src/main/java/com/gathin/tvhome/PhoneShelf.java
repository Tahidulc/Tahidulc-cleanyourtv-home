package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The newest Homefield (the family's phone and tablet app) kept on the TV, for the phones and tablets to update
 * themselves from at home (AppUpdate there). It is put in the launcher's own folder over ADB (phone/: update.json, the
 * whole APK and, much smaller, what changed against recent versions, made by launcher/update/mkpatch.py), and given to a
 * paired phone by DeliveryLink: GET /phone/update.json, then GET /phone/&lt;file&gt;. A file goes out on a thread of its
 * own, so the link goes on answering meanwhile, and a download that broke off picks up where it stopped (Range).
 */
final class PhoneShelf {
    private PhoneShelf() { }

    /** At most this many phones fetching at once; another is asked to come back in a moment. */
    private static final int SENDS = 2;
    private static final AtomicInteger SENDING = new AtomicInteger();

    /** The shelf (made by the launcher itself at start, so what ADB puts there is the launcher's to read). */
    static File dir(Context c) {
        return c.getExternalFilesDir("phone");
    }

    /** Pure: a name the shelf gives out: letters, digits, dots, dashes and underscores, not starting with a dot. */
    static boolean plainName(String name) {
        if (name == null || name.isEmpty() || name.length() > 96 || name.charAt(0) == '.') return false;
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean ok = ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch >= '0' && ch <= '9' || ch == '.' || ch == '-' || ch == '_';
            if (!ok) return false;
        }
        return true;
    }

    /**
     * Pure: where a request's Range ("bytes=N-") asks the file to start: 0 for none (the whole file), -1 for one this
     * cannot give (a closed or several ranges, or past the end).
     */
    static long rangeStart(String range, long length) {
        if (range == null || range.trim().isEmpty()) return 0;
        String r = range.trim().toLowerCase(Locale.ROOT);
        if (!r.startsWith("bytes=") || !r.endsWith("-") || r.indexOf(',') >= 0) return -1;
        try {
            long from = Long.parseLong(r.substring(6, r.length() - 1).trim());
            return from >= 0 && from < length ? from : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * GET /phone/&lt;name&gt;: update.json answered here; a file sent on a thread of its own, which closes the socket when
     * done. True when the socket was handed on that way (not to be closed by the caller).
     */
    static boolean serve(Context c, Socket s, String name, String range) throws Exception {
        File d = dir(c);
        File f = d == null || !plainName(name) ? null : new File(d, name);
        if (f == null || !f.isFile()) {
            DeliveryLink.reply(s, 404, "{\"error\":\"" + ("update.json".equals(name) ? "no update here" : "not here") + "\"}");
            return false;
        }
        if ("update.json".equals(name)) {
            if (f.length() > 64_000) { DeliveryLink.reply(s, 500, "{\"error\":\"too big\"}"); return false; }
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            try (InputStream in = new FileInputStream(f)) {
                for (int k; n < b.length && (k = in.read(b, n, b.length - n)) > 0; ) n += k;
            }
            DeliveryLink.reply(s, 200, "application/json", new String(b, 0, n, StandardCharsets.UTF_8));
            return false;
        }
        long length = f.length(), from = rangeStart(range, length);
        if (from < 0) {
            head(s.getOutputStream(), 416, "application/json", 2, "Content-Range: bytes */" + length + "\r\n");
            s.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            return false;
        }
        if (SENDING.incrementAndGet() > SENDS) {
            SENDING.decrementAndGet();
            DeliveryLink.reply(s, 503, "{\"error\":\"busy, try again in a moment\"}");
            return false;
        }
        Thread t = new Thread(() -> {
            try {
                send(s, f, from, length);
            } catch (Exception ignored) {
                // the phone went away; it picks up where it stopped next time
            } finally {
                SENDING.decrementAndGet();
                try { s.close(); } catch (Exception ignored) { }
            }
        }, "phone-shelf");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        try {
            t.start();
        } catch (RuntimeException | Error e) {
            SENDING.decrementAndGet();
            throw e;
        }
        return true;
    }

    private static void send(Socket s, File f, long from, long length) throws Exception {
        OutputStream out = s.getOutputStream();
        String type = f.getName().endsWith(".apk") ? "application/vnd.android.package-archive" : "application/octet-stream";
        if (from > 0) head(out, 206, type, length - from, "Content-Range: bytes " + from + "-" + (length - 1) + "/" + length + "\r\n");
        else head(out, 200, type, length, "Accept-Ranges: bytes\r\n");
        byte[] buf = new byte[1 << 16];
        try (FileInputStream in = new FileInputStream(f)) {
            long skipped = 0;
            while (skipped < from) {
                long k = in.skip(from - skipped);
                if (k <= 0) throw new java.io.EOFException("shorter than it was");
                skipped += k;
            }
            for (long left = length - from; left > 0; ) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (n < 0) throw new java.io.EOFException("shorter than it was");
                out.write(buf, 0, n);
                left -= n;
            }
        }
        out.flush();
    }

    private static void head(OutputStream out, int status, String type, long length, String extra) throws Exception {
        String words = status == 200 ? "OK" : status == 206 ? "Partial Content" : status == 416 ? "Range Not Satisfiable" : "OK";
        out.write(("HTTP/1.1 " + status + " " + words + "\r\nContent-Type: " + type + "\r\nContent-Length: " + length + "\r\n" + extra
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    }
}
