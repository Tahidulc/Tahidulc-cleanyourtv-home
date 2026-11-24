package com.gathin.broadcast;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The family's own Anthropic API key for Ask AI, and the model chosen, on this device only: in the app's private
 * preferences ("ai_key"), a file of its own that no sync, settings list or export reads (the apps have backups off),
 * never logged or shown but masked (AskAi.masked). Typed in Settings on each device, or sent from the phone to a
 * paired TV over the apps' own pairing (the TV's DeliveryLink /ai/key, with the phone's pairing token).
 * Ask AI's requests run on one worker thread of their own, never the main thread.
 */
public final class AiKey {
    private AiKey() {
    }

    private static final String FILE = "ai_key", KEY = "k", MODEL = "model";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** The key, or "" when none has been added. */
    public static String get(Context c) {
        String k = prefs(c).getString(KEY, "");
        return k == null ? "" : k;
    }

    public static boolean has(Context c) {
        return !get(c).isEmpty();
    }

    /** The key set (trimmed; "" or null takes it away). */
    public static void set(Context c, String key) {
        String k = key == null ? "" : key.trim();
        if (k.isEmpty()) prefs(c).edit().remove(KEY).apply();
        else prefs(c).edit().putString(KEY, k).apply();
    }

    /** The model Ask AI uses (Settings; Claude Opus 5.5 until another is chosen). */
    public static String model(Context c) {
        return Claude.model(prefs(c).getString(MODEL, Claude.DEFAULT_MODEL));
    }

    public static void setModel(Context c, String model) {
        prefs(c).edit().putString(MODEL, Claude.model(model)).apply();
    }

    /** The model's name as Settings shows it. */
    public static String modelName(String id) {
        for (String[] m : Claude.MODELS) if (m[0].equals(id)) return m[1];
        return id;
    }

    /** How to add a key, for Ask AI with none (the generator works without one). */
    public static final String HOW = "Ask AI uses your own Anthropic API key. Make one at console.anthropic.com (API keys), "
            + "then add it in Settings, Ask AI. It stays on this device. Generate works without one.";

    private static final ExecutorService WORK = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ask-ai");
        t.setDaemon(true);
        return t;
    });

    /** Ask AI's work, off the main thread (one request at a time). */
    public static java.util.concurrent.Future<?> run(Runnable r) {
        return WORK.submit(r);
    }
}
