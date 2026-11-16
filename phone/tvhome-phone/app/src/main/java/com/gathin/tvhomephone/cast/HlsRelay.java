package com.gathin.tvhomephone.cast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A small web server on the phone that hands a channel from the family's playlist to a device on the home Wi-Fi. A
 * Chromecast reads HLS with the browser's rules and wants CORS headers, which most IPTV servers never send; some sets
 * need the channel's own User-Agent. So the device asks the phone, and the phone asks the provider: every address in the
 * playlists it passes on is sent through itself (and signed, so it fetches only what it handed out), the channel's
 * User-Agent and Referrer go upstream, and every answer carries Access-Control-Allow-Origin: *. It listens only on the
 * Wi-Fi's own address and stops when casting stops. Nothing it passes on is logged: the addresses carry the login.
 */
public final class HlsRelay {
    /** What a published channel is: its HLS address (or null), its plain stream, and how to ask for them. */
    static final class Target {
        final String hls, direct, agent, referrer;
        final byte[] secret = new byte[16];

        Target(String hls, String direct, String agent, String referrer) {
            this.hls = hls;
            this.direct = direct;
            this.agent = agent == null || agent.isEmpty() ? DEFAULT_AGENT : agent;
            this.referrer = referrer == null ? "" : referrer;
            new SecureRandom().nextBytes(secret);
        }
    }

    static final String DEFAULT_AGENT = "TVHome-phone/1.0";
    private static final int MAX_PLAYLIST = 2 * 1024 * 1024;
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]*)\"");
    /** An Xtream Codes live stream: http://host/live/user/pass/1234.ts, and its shorter form http://host/user/pass/1234. */
    private static final Pattern XTREAM_LIVE = Pattern.compile("^(https?://[^/]+)/live/([^/]+)/([^/]+)/([0-9]+)\\.(ts|m3u8)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XTREAM_SHORT = Pattern.compile("^(https?://[^/]+)/([^/]+)/([^/]+)/([0-9]+)(\\.ts)?(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);
    /** First path words that are a server's own folders, not an Xtream login. */
    private static final Set<String> NOT_A_LOGIN = new HashSet<>(java.util.Arrays.asList("live", "movie", "series", "hls",
            "play", "stream", "streams", "channel", "channels", "tv", "timeshift", "get.php", "c", "s"));

    private static HlsRelay running;

    private final ServerSocket server;
    private final String origin;
    private final Map<String, Target> targets = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(0, 24, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
    private volatile boolean open = true;

    private HlsRelay(InetAddress bind) throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(bind, 0), 32);
        origin = "http://" + bind.getHostAddress() + ":" + server.getLocalPort();
        Thread t = new Thread(this::accept, "cast-relay");
        t.setDaemon(true);
        t.start();
    }

    /** The relay on this Wi-Fi address (started when it isn't yet). */
    static synchronized HlsRelay on(InetAddress wifi) throws IOException {
        if (running != null && running.open && running.origin.equals("http://" + wifi.getHostAddress() + ":" + running.server.getLocalPort())) {
            return running;
        }
        stopAll();
        running = new HlsRelay(wifi);
        return running;
    }

    /** Casting has stopped: the relay closes and forgets every channel it was given. */
    public static synchronized void stopAll() {
        if (running == null) return;
        running.close();
        running = null;
    }

    private void close() {
        open = false;
        targets.clear();
        try {
            server.close();
        } catch (IOException ignored) {
            // closed
        }
        pool.shutdownNow();
    }

    /** A channel published: the address of its HLS through the relay ("…/index.m3u8"), or of its plain stream ("…/live.ts"). */
    String publish(String hls, String direct, String agent, String referrer, boolean asHls) {
        // One channel at a time: the last one's addresses stop working.
        targets.clear();
        String token = hex(randomBytes(8));
        targets.put(token, new Target(hls, direct, agent, referrer));
        return origin + "/c/" + token + (asHls && hls != null ? "/index.m3u8" : "/live.ts");
    }

    // ---- serving ---------------------------------------------------------------------------------------------------

    private void accept() {
        while (open) {
            Socket s;
            try {
                s = server.accept();
            } catch (IOException e) {
                if (!open) return;
                continue;
            }
            try {
                pool.execute(() -> serve(s));
            } catch (RuntimeException busy) {
                quietly(s);
            }
        }
    }

    private void serve(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(20_000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            String[] request = readHead(in);
            if (request == null) return;
            String method = request[0], path = request[1], range = request[2];
            OutputStream out = sock.getOutputStream();
            if ("OPTIONS".equals(method)) {
                head(out, 204, "No Content", null, -1, null);
                return;
            }
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                head(out, 405, "Method Not Allowed", null, 0, null);
                return;
            }
            String upstream = route(path);
            Target t = targetOf(path);
            if (upstream == null || t == null) {
                head(out, 404, "Not Found", null, 0, null);
                return;
            }
            relay(out, "HEAD".equals(method), upstream, t, tokenOf(path), range);
        } catch (IOException ignored) {
            // the device hung up, or the provider did
        }
    }

    /** The request line and the Range asked for: {method, path, range}; null for nothing readable. */
    private static String[] readHead(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        String first = null, range = null;
        int total = 0;
        while (true) {
            int b = in.read();
            if (b < 0) return null;
            if (++total > 16 * 1024) return null;
            if (b == '\n') {
                String l = line.toString("ISO-8859-1").trim();
                line.reset();
                if (first == null) {
                    first = l;
                } else if (l.isEmpty()) {
                    break;
                } else if (l.regionMatches(true, 0, "Range:", 0, 6)) {
                    range = l.substring(6).trim();
                }
            } else {
                line.write(b);
            }
        }
        String[] parts = first.split(" ");
        if (parts.length < 2) return null;
        return new String[]{parts[0].toUpperCase(Locale.ROOT), parts[1], range};
    }

    /** Which address a path asks for: the channel's own HLS or stream, or one of the signed addresses handed out. */
    String route(String path) {
        String[] p = path.split("\\?", 2)[0].split("/");
        // "", "c", token, then "index.m3u8" | "live.ts" | "r", sig, b64, name
        if (p.length < 4 || !"c".equals(p[1])) return null;
        Target t = targets.get(p[2]);
        if (t == null) return null;
        if (p.length == 4 && "index.m3u8".equals(p[3])) return t.hls;
        if (p.length == 4 && "live.ts".equals(p[3])) return t.direct;
        if (p.length >= 6 && "r".equals(p[3])) {
            String sig = p[4], b64 = p[5];
            if (!sig.equals(sign(t.secret, b64))) return null;
            try {
                return new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private Target targetOf(String path) {
        String token = tokenOf(path);
        return token == null ? null : targets.get(token);
    }

    private static String tokenOf(String path) {
        String[] p = path.split("\\?", 2)[0].split("/");
        return p.length > 2 && "c".equals(p[1]) ? p[2] : null;
    }

    private void relay(OutputStream out, boolean headOnly, String upstream, Target t, String token, String range) throws IOException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(upstream).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            c.setRequestProperty("User-Agent", t.agent);
            if (!t.referrer.isEmpty()) c.setRequestProperty("Referer", t.referrer);
            if (range != null) c.setRequestProperty("Range", range);
            int code = c.getResponseCode();
            if (code >= 400) {
                head(out, code == 404 ? 404 : 502, code == 404 ? "Not Found" : "Bad Gateway", null, 0, null);
                return;
            }
            String type = c.getContentType();
            InputStream body = new BufferedInputStream(c.getInputStream());
            body.mark(16);
            byte[] peek = new byte[7];
            int got = body.read(peek);
            body.reset();
            boolean playlist = isPlaylist(type, c.getURL().getPath(), got == 7 ? new String(peek, StandardCharsets.US_ASCII) : "");
            if (playlist) {
                String text = new String(readAll(body, MAX_PLAYLIST), StandardCharsets.UTF_8);
                String base = c.getURL().toString();
                Target target = t;
                String rewritten = rewrite(text, base, abs -> through(token, target, abs));
                byte[] bytes = rewritten.getBytes(StandardCharsets.UTF_8);
                head(out, 200, "OK", "application/vnd.apple.mpegurl", bytes.length, "Cache-Control: no-cache\r\n");
                if (!headOnly) out.write(bytes);
                out.flush();
                return;
            }
            long length = c.getContentLengthLong();
            String extra = null;
            if (code == 206 && c.getHeaderField("Content-Range") != null) {
                extra = "Content-Range: " + c.getHeaderField("Content-Range") + "\r\n";
            }
            head(out, code == 206 ? 206 : 200, code == 206 ? "Partial Content" : "OK",
                    type == null || type.isEmpty() ? "video/mp2t" : type, length, extra);
            if (headOnly) return;
            byte[] buf = new byte[64 * 1024];
            int n;
            while (open && (n = body.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } catch (SocketException ignored) {
            // the device stopped reading
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** An address from a playlist, sent through the relay: signed, its name kept at the end for players that look. */
    String through(String token, Target t, String absolute) {
        String lower = absolute.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return absolute;
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(absolute.getBytes(StandardCharsets.UTF_8));
        return origin + "/c/" + token + "/r/" + sign(t.secret, b64) + "/" + b64 + "/" + fileName(absolute);
    }

    private static void head(OutputStream out, int code, String reason, String type, long length, String extra) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        h.append("Access-Control-Allow-Origin: *\r\n");
        h.append("Access-Control-Allow-Headers: *\r\n");
        h.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n");
        h.append("Access-Control-Expose-Headers: Content-Length, Content-Range\r\n");
        if (type != null) h.append("Content-Type: ").append(type).append("\r\n");
        if (length >= 0) h.append("Content-Length: ").append(length).append("\r\n");
        if (extra != null) h.append(extra);
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > max) throw new IOException("too long");
        }
        return b.toByteArray();
    }

    private static void quietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // gone
        }
    }

    // ---- pure ------------------------------------------------------------------------------------------------------

    /** Pure: a playlist with every address in it (the lines, and URI="…" in its tags) resolved and passed to proxify. */
    static String rewrite(String body, String base, Function<String, String> proxify) {
        String[] lines = body.split("\r?\n");
        StringBuilder out = new StringBuilder(body.length() + 512);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                out.append('\n');
                continue;
            }
            if (line.startsWith("#")) {
                Matcher m = URI_ATTR.matcher(line);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    String to = proxify.apply(resolve(base, m.group(1)));
                    m.appendReplacement(sb, Matcher.quoteReplacement("URI=\"" + to + "\""));
                }
                m.appendTail(sb);
                out.append(sb);
            } else {
                out.append(proxify.apply(resolve(base, line)));
            }
            out.append('\n');
        }
        return out.toString();
    }

    /** Pure: an address in a playlist made whole against the playlist's own. */
    static String resolve(String base, String ref) {
        String lower = ref.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) return ref;
        try {
            return URI.create(base).resolve(ref).toString();
        } catch (IllegalArgumentException e) {
            // Characters URI won't take (spaces, |): put together by hand.
            String noQuery = base.split("[?#]", 2)[0];
            if (ref.startsWith("//")) return noQuery.substring(0, noQuery.indexOf(':') + 1) + ref;
            if (ref.startsWith("/")) {
                int slash = noQuery.indexOf('/', noQuery.indexOf("://") + 3);
                return (slash < 0 ? noQuery : noQuery.substring(0, slash)) + ref;
            }
            return noQuery.substring(0, noQuery.lastIndexOf('/') + 1) + ref;
        }
    }

    /**
     * Pure: the address to cast a channel as HLS, or null when it has none. Its own address when it is HLS already; an
     * Xtream Codes live stream asked for in the provider's .m3u8 output instead of .ts.
     */
    public static String hlsFor(String url) {
        if (url == null || url.isEmpty()) return null;
        String path = url.split("[?#]", 2)[0].toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8") || path.endsWith(".m3u")) return url;
        Matcher m = XTREAM_LIVE.matcher(url);
        if (m.matches()) {
            return m.group(1) + "/live/" + m.group(2) + "/" + m.group(3) + "/" + m.group(4) + ".m3u8" + (m.group(6) == null ? "" : m.group(6));
        }
        m = XTREAM_SHORT.matcher(url);
        if (m.matches() && !NOT_A_LOGIN.contains(m.group(2).toLowerCase(Locale.ROOT))) {
            return m.group(1) + "/live/" + m.group(2) + "/" + m.group(3) + "/" + m.group(4) + ".m3u8" + (m.group(6) == null ? "" : m.group(6));
        }
        return null;
    }

    /** Pure: whether an answer is a playlist, by its type, its path or how it starts. */
    static boolean isPlaylist(String type, String path, String start) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("mpegurl")) return true;
        String p = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".m3u8") || p.endsWith(".m3u")) return true;
        return start != null && start.startsWith("#EXTM3U");
    }

    /** Pure: the last part of an address's path, safe to put in one ("s" when there is none). */
    static String fileName(String url) {
        String path = url.split("[?#]", 2)[0];
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder b = new StringBuilder();
        for (char ch : last.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '-' || ch == '_') b.append(ch);
            if (b.length() >= 40) break;
        }
        return b.length() == 0 ? "s" : b.toString();
    }

    /** Pure: an address's signature with its channel's secret (12 hex digits of HMAC-SHA256). */
    static String sign(byte[] secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }
}
