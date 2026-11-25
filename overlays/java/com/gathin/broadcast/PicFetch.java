package com.gathin.broadcast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The programme pictures' requests, the same in Homefield TV and the phone app: plain HTTP with short timeouts and a
 * generic user agent (nothing about the family, no login, no key but TMDB's own in each app), kept a little apart per
 * host (SPACING_MS, one at a time to a host), the answers kept on disk for as long as each kind stays good (listings
 * for hours, title searches for days), the folder kept under DISK_MAX. Off the main thread only. Tests give made-up
 * pages instead (fake): nothing is fetched then.
 */
public final class PicFetch {
    /** Between two requests to one host, and the most a request waits. */
    public static final long SPACING_MS = 350L;
    public static final int CONNECT_MS = 8_000, READ_MS = 15_000;
    /** The most a page may be (Freeview's day is about 2 MB). */
    public static final int MAX_BYTES = 6 << 20;
    /** The disk folder's most, and what it is pruned to. */
    public static final long DISK_MAX = 40L << 20, DISK_KEEP = 30L << 20;
    /** A browser's plain user agent: nothing about the family or the app. */
    public static final String AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";

    /** Tests: pages by address (a body, or "" for a 404), and HEADs answered "image/jpeg" unless listed in fakeMissing. */
    public static volatile Map<String, String> fake;
    public static volatile java.util.Set<String> fakeMissing;

    private final File dir;
    private final Map<String, Long> last = new HashMap<>();
    private int saved;

    /** A fetcher keeping its answers in dir (made when needed); null: memory only. */
    public PicFetch(File dir) {
        this.dir = dir;
    }

    /**
     * A page as text: kept on disk while younger than keepMs, else asked for now (and kept); null when it can't be had
     * (a network error, or not 200: that is kept too for keepMs / 6, so a missing page isn't asked for again and again).
     */
    public String text(String url, long keepMs) {
        Map<String, String> f = fake;
        if (f != null) {
            String b = f.get(url);
            return b == null || b.isEmpty() ? null : b;
        }
        File kept = file(url);
        if (kept != null && kept.isFile()) {
            long age = System.currentTimeMillis() - kept.lastModified();
            if (age >= 0 && age < (kept.length() == 0 ? keepMs / 6 : keepMs)) return kept.length() == 0 ? null : read(kept);
        }
        byte[] b;
        try {
            b = get(url, false);
        } catch (IOException e) {
            // Away from the network: what was kept, however old.
            return kept != null && kept.isFile() && kept.length() > 0 ? read(kept) : null;
        }
        if (kept != null) write(kept, b == null ? new byte[0] : b);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    /** Whether an address is a picture (a HEAD: 200 and an image's type); false when it can't be told. */
    public boolean isImage(String url) {
        Map<String, String> f = fake;
        if (f != null) {
            java.util.Set<String> m = fakeMissing;
            return m == null || !m.contains(url);
        }
        try {
            return get(url, true) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** One request (pacing by host): the body (empty for a HEAD that is a JPEG or WebP picture), null for anything else. */
    private byte[] get(String url, boolean head) throws IOException {
        pace(host(url));
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", AGENT);
        c.setRequestProperty("Accept", head ? "image/*" : "application/json, text/plain, */*");
        if (head) c.setRequestMethod("HEAD");
        try {
            int code = c.getResponseCode();
            if (code != 200) return null;
            if (head) {
                // A PNG from a programme picture service is its "unavailable" card, never a programme's own picture.
                String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(java.util.Locale.ROOT);
                return type.startsWith("image/jpeg") || type.startsWith("image/webp") ? new byte[0] : null;
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream o = new ByteArrayOutputStream(64 * 1024);
                byte[] buf = new byte[32 * 1024];
                int total = 0;
                for (int n; (n = in.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_BYTES) return null;
                    o.write(buf, 0, n);
                }
                return o.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    /** Pure: how far apart requests to a host go (TheSportsDB's free key: at most 30 a minute). */
    static long spacing(String host) {
        return host.endsWith("thesportsdb.com") ? 2_100L : SPACING_MS;
    }

    private void pace(String host) {
        long wait;
        synchronized (last) {
            long now = System.currentTimeMillis();
            Long at = last.get(host);
            long next = at == null ? now : Math.max(now, at + spacing(host));
            last.put(host, next);
            wait = next - now;
        }
        if (wait > 0) {
            try {
                Thread.sleep(Math.min(wait, 5_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Pure: an address's host. */
    static String host(String url) {
        int a = url.indexOf("://");
        int s = a < 0 ? 0 : a + 3, e = url.indexOf('/', s);
        return e < 0 ? url.substring(s) : url.substring(s, e);
    }

    private File file(String url) {
        if (dir == null) return null;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, name(url));
    }

    /** Pure: a page's file name (its address's SHA-1). */
    static String name(String url) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder(40);
            for (byte x : h) b.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), MAX_BYTES)];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) break;
                n += r;
            }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private void write(File f, byte[] b) {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(b);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
        if (++saved % 20 == 0) prune();
    }

    /** The folder kept under DISK_MAX: the pages written longest ago go first. */
    void prune() {
        File[] all = dir == null ? null : dir.listFiles();
        if (all == null) return;
        long total = 0;
        for (File f : all) total += f.length();
        if (total <= DISK_MAX) return;
        Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : all) {
            if (total <= DISK_KEEP) break;
            total -= f.length();
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
