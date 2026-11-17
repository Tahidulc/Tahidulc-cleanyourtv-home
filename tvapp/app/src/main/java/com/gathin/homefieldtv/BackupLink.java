package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.homefieldtv.backups.BackupStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Settings → Backup over the link the family's phone already uses (Cast's receiver: the home network only, only while
 * the app is open). GET /backup hands this TV's backup to the phone, but only in the five minutes after "Send to your
 * phone" is chosen on the TV, and only once (404 otherwise). POST /backup takes a backup the phone kept and sends back:
 * it is kept beside this TV's own (BackupStore) and restored only from Settings with the remote, never by itself.
 * Whether a phone uses the link at all (it looked for this TV or sent it a channel) is noted, so Settings offers the
 * phone only where there is one.
 */
final class BackupLink {
    private BackupLink() {}

    static final long OFFER_MS = 5 * 60_000L;
    private static final int MAX_BYTES = 4 << 20;
    private static final String PREFS = "backup_link", SEEN = "phone_seen", SENT = "sent_at";

    private static volatile String offered;
    private static volatile long offeredUntil;

    /**
     * A request on the link: /backup answered here (true); any other noted (a phone is there) and left to Cast (false).
     */
    static boolean handle(Context app, String method, String path, String head, InputStream in, OutputStream out) throws IOException {
        if ("/hello".equals(path) || "/play".equals(path)) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(SEEN, System.currentTimeMillis()).apply();
            return false;
        }
        if (!"/backup".equals(path)) return false;
        if ("GET".equals(method)) {
            String text = take();
            if (text == null) {
                reply(out, 404, "{\"ok\":false}");
            } else {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(SENT, System.currentTimeMillis()).apply();
                reply(out, 200, text);
            }
            return true;
        }
        if ("POST".equals(method)) {
            int length = Cast.contentLength(head);
            if (length <= 0 || length > MAX_BYTES) {
                reply(out, 400, "{\"ok\":false}");
                return true;
            }
            byte[] body = new byte[length];
            int n = 0;
            while (n < length) {
                int r = in.read(body, n, length - n);
                if (r < 0) break;
                n += r;
            }
            boolean kept = n == length && BackupStore.keepFromPhone(app, new String(body, 0, n, StandardCharsets.UTF_8), "your phone");
            reply(out, kept ? 200 : 400, kept ? "{\"ok\":true}" : "{\"ok\":false}");
            return true;
        }
        reply(out, 404, "{\"ok\":false}");
        return true;
    }

    /** The backup offered to the phone for the next five minutes (Send to your phone). */
    static void offer(String text) {
        offered = text;
        offeredUntil = System.currentTimeMillis() + OFFER_MS;
    }

    /** The offer, once, while it lasts; null when there is none. */
    static synchronized String take() {
        String t = offered;
        if (t == null || System.currentTimeMillis() > offeredUntil) {
            offered = null;
            return null;
        }
        offered = null;
        return t;
    }

    /** How long the offer has left (0: none, or the phone has taken it). */
    static long offerLeftMs() {
        return offered == null ? 0 : Math.max(0, offeredUntil - System.currentTimeMillis());
    }

    /** Whether a phone has used the link with this TV. */
    static boolean phoneSeen(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SEEN, 0) > 0;
    }

    /** When the phone last took a backup (0: never). */
    static long sentAt(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SENT, 0);
    }

    private static void reply(OutputStream out, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String status = code == 200 ? "OK" : code == 404 ? "Not Found" : "Bad Request";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: "
                + body.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
