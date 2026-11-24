package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Ask AI's client against a fake HTTP layer: what is sent (model, max_tokens, the schema, the refusal fallback, no
 * thinking or sampling settings, the key only in its header), and what is made of each kind of answer. Nothing here
 * reaches the real API.
 */
public class ClaudeTest {
    /** An invented key (not a real one). */
    private static final String KEY = "sk-ant-test-0000000000000000000000000000";

    /** Answers in turn, and what was asked. */
    private static final class Fake implements Claude.Http {
        final Deque<Object> answers = new ArrayDeque<>();
        final List<JSONObject> bodies = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        final List<String> urls = new ArrayList<>();

        Fake then(int code, String body) {
            answers.add(new Claude.Response(code, body, ""));
            return this;
        }

        Fake then(Claude.Response r) {
            answers.add(r);
            return this;
        }

        Fake fail(IOException e) {
            answers.add(e);
            return this;
        }

        @Override public Claude.Response post(String url, Map<String, String> h, byte[] body, int connectMs, int readMs) throws IOException {
            urls.add(url);
            headers.add(h);
            try {
                bodies.add(new JSONObject(new String(body, StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new IOException(e);
            }
            Object a = answers.poll();
            if (a instanceof IOException) throw (IOException) a;
            if (a == null) throw new IOException("no more answers");
            return (Claude.Response) a;
        }
    }

    private final List<Long> waits = new ArrayList<>();

    private Claude claude(Fake f) {
        return new Claude(f, waits::add);
    }

    /** A Messages API answer with the design as its text block (and a thinking block before it, as Opus sends). */
    private static String ok(String json) throws Exception {
        return new JSONObject().put("id", "msg_test").put("type", "message").put("role", "assistant").put("model", "claude-opus-5-5")
                .put("stop_reason", "end_turn")
                .put("content", new JSONArray()
                        .put(new JSONObject().put("type", "thinking").put("thinking", "").put("signature", "x"))
                        .put(new JSONObject().put("type", "text").put("text", json)))
                .toString();
    }

    private static final String DESIGN = "{\"v\":1,\"name\":\"Harbour lights\",\"base\":\"CLASSIC\",\"colours\":{\"panel\":\"#0A0F24\","
            + "\"panelFoot\":\"\",\"comp\":\"#060A1A\",\"score\":\"#2EC5FF\",\"scoreFoot\":\"#1C9BD0\",\"clock\":\"#04060F\",\"text\":\"#EEF2FF\","
            + "\"scoreText\":\"#0A0F24\",\"clockText\":\"#2EC5FF\",\"dim\":\"#9AA3C0\",\"accent\":\"#2EC5FF\",\"stoppage\":\"#D7192F\","
            + "\"added\":\"#D7192F\"},\"radius\":0.14,\"border\":{\"width\":0,\"colour\":\"#FFFFFF\"},\"finish\":\"GLOSS\",\"font\":\"OUTFIT\","
            + "\"crest\":\"RING\",\"strips\":true,\"accentLine\":true}";

    private static BoardDesigner.Brief match() {
        BoardDesigner.Brief b = new BoardDesigner.Brief(0xFF0F8B8D, 0xFF7A1F3D, "Northern Shield", null);
        b.homeName = "Harbourside";
        b.awayName = "Millbrook";
        return b;
    }

    @Test
    public void theRequestIsAsAskedFor() throws Exception {
        Fake f = new Fake().then(200, ok(DESIGN));
        AskAi.designBoard(claude(f), KEY, "claude-opus-5-5", "Something for a night match", match());
        assertEquals(1, f.bodies.size());
        assertEquals(Claude.URL, f.urls.get(0));
        JSONObject b = f.bodies.get(0);
        assertEquals("claude-opus-5-5", b.getString("model"));
        assertEquals(16000, b.getInt("max_tokens"));
        assertEquals("default", b.getString("fallbacks"));
        JSONObject format = b.getJSONObject("output_config").getJSONObject("format");
        assertEquals("json_schema", format.getString("type"));
        assertEquals(BoardSpec.schema().toString(), format.getJSONObject("schema").toString());
        // No thinking or sampling settings.
        for (String k : new String[]{"thinking", "temperature", "top_p", "top_k"}) assertFalse(k, b.has(k));
        assertEquals("user", b.getJSONArray("messages").getJSONObject(0).getString("role"));
        String user = b.getJSONArray("messages").getJSONObject(0).getString("content");
        assertTrue(user.contains("Something for a night match"));
        assertTrue(user.contains("Harbourside") && user.contains("#0F8B8D") && user.contains("Northern Shield"));
        assertTrue(b.getString("system").contains("4.5:1"));
        Map<String, String> h = f.headers.get(0);
        assertEquals(KEY, h.get("x-api-key"));
        assertEquals("2023-06-01", h.get("anthropic-version"));
        assertEquals("application/json", h.get("content-type"));
        assertEquals("server-side-fallback-2026-07-01", h.get("anthropic-beta"));
        // The key is in its header and nowhere in the body.
        assertFalse(b.toString().contains(KEY));
    }

    @Test
    public void haikuHasNoServerSideFallback() throws Exception {
        Fake f = new Fake().then(200, ok(DESIGN));
        AskAi.designBoard(claude(f), KEY, "claude-haiku-5-5", "", match());
        assertFalse(f.bodies.get(0).has("fallbacks"));
        assertFalse(f.headers.get(0).containsKey("anthropic-beta"));
        // An unknown model from Settings is the default.
        f = new Fake().then(200, ok(DESIGN));
        AskAi.designBoard(claude(f), KEY, "gpt-something", "", match());
        assertEquals(Claude.DEFAULT_MODEL, f.bodies.get(0).getString("model"));
    }

    @Test
    public void theDesignComesBackAsABoard() throws Exception {
        BoardSpec s = AskAi.designBoard(claude(new Fake().then(200, ok(DESIGN))), KEY, null, "night", match());
        assertEquals("Harbour lights", s.name);
        assertEquals(0xFF2EC5FF, s.score);
        assertEquals(BoardSpec.Crest.RING, s.crest);
        assertTrue(BoardDesigner.problems(s, match()).isEmpty());
    }

    @Test
    public void anUnreadableDesignIsMadeReadable() throws Exception {
        String grey = DESIGN.replace("\"text\":\"#EEF2FF\"", "\"text\":\"#0B1029\"");
        BoardSpec s = AskAi.designBoard(claude(new Fake().then(200, ok(grey))), KEY, null, "", match());
        assertTrue(BoardColours.contrast(s.text, s.panelMid()) >= BoardDesigner.WORDS);
    }

    private static Claude.Why why(Claude c, String key) {
        try {
            AskAi.designBoard(c, key, null, "x", match());
            fail("no failure");
            return null;
        } catch (Claude.Failure e) {
            assertFalse(e.getMessage().contains(KEY));
            return e.why;
        }
    }

    @Test
    public void noKeyAsksForOne() {
        Fake f = new Fake();
        assertEquals(Claude.Why.NO_KEY, why(claude(f), "  "));
        assertTrue(f.bodies.isEmpty());
    }

    @Test
    public void aWrongKeySaysSoAtOnce() {
        Fake f = new Fake().then(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}");
        assertEquals(Claude.Why.BAD_KEY, why(claude(f), KEY));
        assertEquals(1, f.bodies.size());
        assertTrue(waits.isEmpty());
    }

    @Test
    public void busyIsTriedAgainAfterAWaitThenSaysSo() throws Exception {
        Fake f = new Fake().then(429, "{}").then(new Claude.Response(529, "{}", "3")).then(200, ok(DESIGN));
        BoardSpec s = AskAi.designBoard(claude(f), KEY, null, "", match());
        assertEquals("Harbour lights", s.name);
        assertEquals(3, f.bodies.size());
        assertEquals(2, waits.size());
        assertEquals(Claude.FIRST_WAIT_MS, (long) waits.get(0));
        assertEquals(3000L, (long) waits.get(1));
        waits.clear();
        f = new Fake().then(503, "").then(500, "").then(502, "");
        assertEquals(Claude.Why.DOWN, why(claude(f), KEY));
        assertEquals(Claude.TRIES, f.bodies.size());
        assertEquals(Claude.TRIES - 1, waits.size());
    }

    @Test
    public void noNetworkSaysSo() {
        Fake f = new Fake().fail(new UnknownHostException("api.anthropic.com"));
        assertEquals(Claude.Why.NO_NETWORK, why(claude(f), KEY));
        assertEquals(1, f.bodies.size());
        f = new Fake().fail(new SocketTimeoutException()).fail(new SocketTimeoutException()).fail(new SocketTimeoutException());
        assertEquals(Claude.Why.NO_NETWORK, why(claude(f), KEY));
        assertEquals(Claude.TRIES, f.bodies.size());
    }

    @Test
    public void aRefusalOrACutOffIsReadBeforeTheContent() throws Exception {
        String refusal = new JSONObject().put("stop_reason", "refusal").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", DESIGN))).toString();
        assertEquals(Claude.Why.REFUSED, why(claude(new Fake().then(200, refusal)), KEY));
        String cut = new JSONObject().put("stop_reason", "max_tokens").put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "{\"v\":1,\"na"))).toString();
        assertEquals(Claude.Why.TOO_LONG, why(claude(new Fake().then(200, cut)), KEY));
    }

    @Test
    public void aBrokenAnswerSaysSo() throws Exception {
        assertEquals(Claude.Why.BAD_ANSWER, why(claude(new Fake().then(200, "not json")), KEY));
        assertEquals(Claude.Why.BAD_ANSWER, why(claude(new Fake().then(200, ok("[1,2]"))), KEY));
        String none = new JSONObject().put("stop_reason", "end_turn").put("content", new JSONArray()).toString();
        assertEquals(Claude.Why.BAD_ANSWER, why(claude(new Fake().then(200, none)), KEY));
    }

    @Test
    public void otherStatusesInPlainWords() {
        assertEquals(Claude.Why.NOT_ALLOWED, Claude.failure(new Claude.Response(403, "", "")).why);
        assertEquals(Claude.Why.BAD_REQUEST, Claude.failure(new Claude.Response(404, "", "")).why);
        assertEquals(Claude.Why.NOT_ALLOWED, Claude.failure(new Claude.Response(400,
                "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Your credit balance is too low\"}}", "")).why);
        assertEquals(0, Claude.retryAfter("soon"));
        assertEquals(1500, Claude.retryAfter("1.5"));
    }

    @Test
    public void theFamilysWordsAreKeptShortAndPlain() {
        StringBuilder lots = new StringBuilder();
        for (int i = 0; i < 200; i++) lots.append("gold ");
        String u = AskAi.boardUser(lots + "\u0007", null);
        assertTrue(u.length() < AskAi.WORDS_MAX + 40);
        assertFalse(u.contains("\u0007"));
        assertTrue(AskAi.boardUser("", null).contains("Surprise"));
    }

    @Test
    public void aKeyIsShownMasked() {
        String m = AskAi.masked(KEY);
        assertTrue(m.startsWith("sk-ant-"));
        assertTrue(m.endsWith("0000"));
        assertFalse(m.contains("test"));
        assertEquals("", AskAi.masked(null));
        assertTrue(AskAi.looksLikeKey(KEY));
        assertFalse(AskAi.looksLikeKey("hello"));
    }
}
