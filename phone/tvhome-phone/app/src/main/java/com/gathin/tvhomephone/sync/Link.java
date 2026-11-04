package com.gathin.tvhomephone.sync;

import com.gathin.tvhomephone.Net;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** One POST to a TV's link (DeliveryLink, port 47820): its JSON reply. Off the main thread. */
final class Link {
    static final int PORT = 47820;

    private Link() {}

    static JSONObject post(String host, int port, String path, JSONObject body, String token, int timeoutMs)
            throws IOException {
        Map<String, String> h = new HashMap<>();
        if (token != null) h.put("X-Token", token);
        String reply = Net.post("http://" + host + ":" + (port <= 0 ? PORT : port) + path, body, h, timeoutMs);
        try {
            return reply == null || reply.isEmpty() ? new JSONObject() : new JSONObject(reply);
        } catch (JSONException e) {
            throw new IOException("not JSON from " + path);
        }
    }

    /** The TV's name, as /hello gives it; null when nothing there answers as a TV. */
    static String hello(String host, int port, int timeoutMs) {
        try {
            String name = post(host, port, "/hello", new JSONObject(), null, timeoutMs).optString("tv");
            return name.isEmpty() ? null : name;
        } catch (IOException e) {
            return null;
        }
    }

    /** The reply's status, when the TV refused (401: no longer paired). */
    static int status(IOException e) {
        return e instanceof Net.HttpError ? ((Net.HttpError) e).code : 0;
    }
}
