package com.gathin.broadcast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ask AI: one request to Claude (Anthropic's Messages API) with the family's own API key, for a scoreboard or a logo
 * design held to a JSON schema (structured outputs), never on the main thread.
 *
 * <p>Plain HTTP (HttpURLConnection and the platform's org.json), not Anthropic's Java SDK: this code is shared by the
 * phone app, the TV launcher and Homefield TV, and the SDK (its core jar alone is 45 MB, with Kotlin reflection,
 * Jackson, OkHttp and OpenTelemetry behind it) would be a new dependency in all three, Homefield TV's build included,
 * on a 32-bit TV with about 600 MB free.
 *
 * <p>The request: the model chosen in Settings (Claude Opus 5.5 to begin with), max_tokens 16000, the system prompt and
 * the family's words, output_config.format with the design's schema, and the server-side refusal fallback
 * (anthropic-beta server-side-fallback-2026-07-01, fallbacks "default") on the models that have it. No thinking or
 * sampling settings. The answer's stop_reason is read before its content (a refusal, or cut off at max_tokens), then its
 * JSON is checked. A wrong key (401) says so; a busy or failing service (429, 5xx) is tried again after a wait, then
 * a plain message; no network says so. The key goes in its header only: it is never logged, shown or put in a message.
 */
public final class Claude {
    public static final String URL = "https://api.anthropic.com/v1/messages";
    public static final String VERSION = "2023-06-01";
    public static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";
    public static final String DEFAULT_MODEL = "claude-opus-5-5";
    /** The models Settings offers: {id, name}. */
    public static final String[][] MODELS = {
            {"claude-opus-5-5", "Claude Opus 5.5 (best designs)"},
            {"claude-sonnet-5-5", "Claude Sonnet 5.5 (faster)"},
            {"claude-haiku-5-5", "Claude Haiku 5.5 (quickest, cheapest)"},
    };
    public static final int MAX_TOKENS = 16000;
    /** How long to wait to connect, and for the answer (a design can take a minute or so). */
    static final int CONNECT_MS = 15_000, READ_MS = 180_000;
    /** Tries in all for a busy or failing service, and the first wait between them (doubling), at most MAX_WAIT_MS. */
    static final int TRIES = 3;
    static final long FIRST_WAIT_MS = 2_000L, MAX_WAIT_MS = 20_000L;
    /** At most this much of an answer read. */
    static final int MAX_ANSWER = 2 << 20;

    /** The HTTP layer: HttpURLConnection on the devices, a fake in the tests (which never reach the real API). */
    public interface Http {
        Response post(String url, Map<String, String> headers, byte[] body, int connectMs, int readMs) throws IOException;
    }

    /** An answer: its status, its body, and its retry-after header ("" when none). */
    public static final class Response {
        public final int code;
        public final String body;
        public final String retryAfter;

        public Response(int code, String body, String retryAfter) {
            this.code = code;
            this.body = body == null ? "" : body;
            this.retryAfter = retryAfter == null ? "" : retryAfter;
        }
    }

    /** How a request can fail, each with the words the family sees. */
    public enum Why {
        NO_KEY, BAD_KEY, NOT_ALLOWED, BUSY, DOWN, NO_NETWORK, REFUSED, TOO_LONG, BAD_ANSWER, BAD_REQUEST, CANCELLED
    }

    /** A request that didn't give a design: why, and what to tell the family (never the key). */
    public static final class Failure extends Exception {
        public final Why why;

        public Failure(Why why, String message) {
            super(message);
            this.why = why;
        }
    }

    /** The waits between tries (the tests' clock never really waits). */
    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private final Http http;
    private final Sleeper sleeper;

    public Claude() {
        this(Claude::realPost, Thread::sleep);
    }

    public Claude(Http http, Sleeper sleeper) {
        this.http = http;
        this.sleeper = sleeper;
    }

    /** Whether a model has the server-side refusal fallback (the Opus, Sonnet and Fable lines; not Haiku). */
    public static boolean fallsBack(String model) {
        String m = model == null ? "" : model;
        return !m.startsWith("claude-haiku");
    }

    /** A model id from Settings, or the default when it is not one offered. */
    public static String model(String chosen) {
        for (String[] m : MODELS) if (m[0].equals(chosen)) return chosen;
        return DEFAULT_MODEL;
    }

    /** Pure: the request's body. */
    public static JSONObject body(String model, String system, String user, JSONObject schema) {
        try {
            String m = model(model);
            JSONObject o = new JSONObject();
            o.put("model", m);
            o.put("max_tokens", MAX_TOKENS);
            o.put("system", system);
            o.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", user)));
            o.put("output_config", new JSONObject().put("format", new JSONObject().put("type", "json_schema").put("schema", schema)));
            if (fallsBack(m)) o.put("fallbacks", "default");
            return o;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pure: the request's headers (the key in its own, and nowhere else). */
    public static Map<String, String> headers(String key, String model) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-api-key", key);
        h.put("anthropic-version", VERSION);
        h.put("content-type", "application/json");
        if (fallsBack(model(model))) h.put("anthropic-beta", FALLBACK_BETA);
        return h;
    }

    /**
     * The design asked for, as the schema's JSON object: blocking, so never on the main thread. key: the family's own;
     * model: Settings' choice. Throws a Failure with words for the family.
     */
    public JSONObject ask(String key, String model, String system, String user, JSONObject schema) throws Failure {
        String k = key == null ? "" : key.trim();
        if (k.isEmpty()) throw new Failure(Why.NO_KEY, "Add your Anthropic API key in Settings to use Ask AI.");
        byte[] body = body(model, system, user, schema).toString().getBytes(StandardCharsets.UTF_8);
        Map<String, String> headers = headers(k, model);
        long wait = FIRST_WAIT_MS;
        Failure last = null;
        for (int t = 1; t <= TRIES; t++) {
            if (Thread.currentThread().isInterrupted()) throw new Failure(Why.CANCELLED, "Stopped.");
            Response r;
            try {
                r = http.post(URL, headers, body, CONNECT_MS, READ_MS);
            } catch (UnknownHostException | NoRouteToHostException e) {
                throw new Failure(Why.NO_NETWORK, "No internet connection. Check the Wi-Fi and try again.");
            } catch (ConnectException | SocketTimeoutException e) {
                last = new Failure(Why.NO_NETWORK, "Couldn't reach Claude. Check the internet connection and try again.");
                if (!pause(wait, t)) throw last;
                wait = Math.min(MAX_WAIT_MS, wait * 2);
                continue;
            } catch (IOException e) {
                last = new Failure(Why.NO_NETWORK, "The connection to Claude dropped. Try again.");
                if (!pause(wait, t)) throw last;
                wait = Math.min(MAX_WAIT_MS, wait * 2);
                continue;
            }
            if (r.code == 200) return answer(r.body);
            Failure f = failure(r);
            if (f.why != Why.BUSY && f.why != Why.DOWN) throw f;
            last = f;
            long after = retryAfter(r.retryAfter);
            if (!pause(after > 0 ? Math.min(MAX_WAIT_MS, after) : wait, t)) throw f;
            wait = Math.min(MAX_WAIT_MS, wait * 2);
        }
        throw last != null ? last : new Failure(Why.DOWN, "Claude didn't answer. Try again in a minute.");
    }

    /** A wait before the next try; false when that was the last try (or the wait was stopped). */
    private boolean pause(long ms, int tryNow) throws Failure {
        if (tryNow >= TRIES) return false;
        try {
            sleeper.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure(Why.CANCELLED, "Stopped.");
        }
    }

    /** Pure: a retry-after header's seconds as ms (0 when none or unreadable). */
    static long retryAfter(String h) {
        if (h == null || h.trim().isEmpty()) return 0;
        try {
            double s = Double.parseDouble(h.trim());
            return s > 0 ? Math.round(s * 1000) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Pure: a status other than 200 in the family's words. */
    static Failure failure(Response r) {
        String type = "";
        try {
            JSONObject e = new JSONObject(r.body).optJSONObject("error");
            if (e != null) type = e.optString("type");
        } catch (JSONException ignored) {
            // no body to read
        }
        switch (r.code) {
            case 401:
                return new Failure(Why.BAD_KEY, "That API key wasn't accepted. Check it in Settings (it starts with sk-ant-).");
            case 403:
                return new Failure(Why.NOT_ALLOWED, "This API key isn't allowed to do that. Check its workspace in the Anthropic Console.");
            case 404:
                return new Failure(Why.BAD_REQUEST, "That model isn't available to this key. Pick another in Settings.");
            case 413:
                return new Failure(Why.BAD_REQUEST, "That was too long to send. Try fewer words.");
            case 429:
                return new Failure(Why.BUSY, "Claude is busy or this key has hit its limit. Try again in a minute.");
            case 400:
                if ("invalid_request_error".equals(type) && r.body.toLowerCase(Locale.ROOT).contains("credit")) {
                    return new Failure(Why.NOT_ALLOWED, "This API key's account is out of credit. Top it up in the Anthropic Console.");
                }
                return new Failure(Why.BAD_REQUEST, "Claude couldn't take that request. Try different words.");
            default:
                if (r.code >= 500) return new Failure(Why.DOWN, "Claude isn't answering right now. Try again in a minute.");
                return new Failure(Why.BAD_REQUEST, "Claude couldn't take that request (" + r.code + ").");
        }
    }

    /**
     * Pure: the design in an answer. Its stop_reason first (a refusal; cut off at max_tokens), then the text blocks'
     * JSON (structured outputs put it in the text; thinking and fallback blocks are passed over), which must be an object.
     */
    static JSONObject answer(String body) throws Failure {
        JSONObject o;
        try {
            o = new JSONObject(body);
        } catch (JSONException e) {
            throw new Failure(Why.BAD_ANSWER, "Claude's answer couldn't be read. Try again.");
        }
        String stop = o.optString("stop_reason", "");
        if ("refusal".equals(stop)) {
            throw new Failure(Why.REFUSED, "Claude didn't make that one. Try asking in different words.");
        }
        if ("max_tokens".equals(stop)) {
            throw new Failure(Why.TOO_LONG, "Claude's answer was cut off before it finished. Try a shorter request.");
        }
        JSONArray content = o.optJSONArray("content");
        StringBuilder text = new StringBuilder();
        for (int i = 0; content != null && i < content.length(); i++) {
            JSONObject b = content.optJSONObject(i);
            if (b != null && "text".equals(b.optString("type"))) text.append(b.optString("text", ""));
        }
        String t = text.toString().trim();
        if (t.isEmpty()) throw new Failure(Why.BAD_ANSWER, "Claude sent no design back. Try again.");
        try {
            return new JSONObject(t);
        } catch (JSONException e) {
            throw new Failure(Why.BAD_ANSWER, "Claude's design wasn't complete. Try again.");
        }
    }

    // ---- the real HTTP --------------------------------------------------------------------------------------------------

    private static Response realPost(String url, Map<String, String> headers, byte[] body, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setUseCaches(false);
            c.setFixedLengthStreamingMode(body.length);
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            try (OutputStream out = c.getOutputStream()) {
                out.write(body);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = "";
            if (in != null) {
                try (InputStream s = in) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    for (int n; (n = s.read(buf)) > 0; ) {
                        b.write(buf, 0, n);
                        if (b.size() > MAX_ANSWER) throw new IOException("answer too large");
                    }
                    text = new String(b.toByteArray(), StandardCharsets.UTF_8);
                }
            }
            return new Response(code, text, c.getHeaderField("retry-after"));
        } finally {
            c.disconnect();
        }
    }
}
