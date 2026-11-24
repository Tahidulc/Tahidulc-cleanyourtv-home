package com.gathin.homefieldtv.subs;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Films' and episodes' subtitles as the family chose them (Settings › Other › Subtitles, and the player's Subtitles):
 * their languages in order (English as it comes), the words' size, colour and background, hearing-impaired files first or
 * not; and each title's own choice, remembered (Off, one of the stream's own tracks by its language and name, or a file
 * from OpenSubtitles with its timing), so a film plays with what was chosen last time and a series' next episode with the
 * same language. The OpenSubtitles login (the API key, the account's name and password, the sign-in's token) is kept apart,
 * in the app's private preferences only: never backed up, synced, logged or shown (the password and key only as dots).
 */
public final class SubsPrefs {
    private SubsPrefs() {}

    static final String PREFS = "vod_subs", ACCOUNT = "opensubtitles";
    public static final String[] SIZE_NAMES = {"Smaller", "Usual", "Bigger", "Biggest"};
    public static final float[] SCALES = {0.8f, 1f, 1.25f, 1.5f};
    public static final String[] COLOUR_NAMES = {"White", "Yellow", "Light blue"};
    public static final int[] COLOURS = {0xFFFFFFFF, 0xFFFFE14D, 0xFF9BE3FF};
    public static final String[] BOX_NAMES = {"Outline", "Black box"};
    /** Each nudge of the timing, and how far it may go either way. */
    public static final int STEP_MS = 500, MAX_OFFSET_MS = 60_000;
    /** How many titles' choices are remembered, and how many downloaded files are kept on the TV. */
    static final int KEEP_TITLES = 200, KEEP_FILES = 60;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static SharedPreferences account(Context c) {
        return c.getApplicationContext().getSharedPreferences(ACCOUNT, Context.MODE_PRIVATE);
    }

    // ---- the family's choices -----------------------------------------------------------------------------------------

    /** The languages wanted, in order (English when none were chosen). */
    public static List<String> languages(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : prefs(c).getString("langs", "en").split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        if (out.isEmpty()) out.add("en");
        return out;
    }

    /** A language turned on (after the others, English staying first) or off (never the last one). */
    public static void setLanguage(Context c, String code, boolean on) {
        List<String> now = languages(c);
        if (on && !now.contains(code)) now.add(code);
        if (!on && now.size() > 1) now.remove(code);
        // In the order Settings lists them (English first), so the first is always the family's first.
        List<String> ordered = new ArrayList<>();
        for (String k : Langs.CODES) if (now.contains(k)) ordered.add(k);
        for (String k : now) if (!ordered.contains(k)) ordered.add(k);
        prefs(c).edit().putString("langs", String.join(",", ordered)).apply();
    }

    public static int size(Context c) {
        return clamp(prefs(c).getInt("size", 1), SIZE_NAMES.length);
    }

    public static void setSize(Context c, int i) {
        prefs(c).edit().putInt("size", clamp(i, SIZE_NAMES.length)).apply();
    }

    public static int colour(Context c) {
        return clamp(prefs(c).getInt("colour", 0), COLOUR_NAMES.length);
    }

    public static void setColour(Context c, int i) {
        prefs(c).edit().putInt("colour", clamp(i, COLOUR_NAMES.length)).apply();
    }

    /** The words' background: 0 an outline round them, 1 a black box behind them. */
    public static int box(Context c) {
        return clamp(prefs(c).getInt("box", 0), BOX_NAMES.length);
    }

    public static void setBox(Context c, int i) {
        prefs(c).edit().putInt("box", clamp(i, BOX_NAMES.length)).apply();
    }

    /** Hearing-impaired files (sounds written in too) first among OpenSubtitles' results. */
    public static boolean hearingFirst(Context c) {
        return prefs(c).getBoolean("hi", false);
    }

    public static void setHearingFirst(Context c, boolean on) {
        prefs(c).edit().putBoolean("hi", on).apply();
    }

    /** The style in words: "Usual · White · Outline". */
    public static String styleWords(Context c) {
        return SIZE_NAMES[size(c)] + "  ·  " + COLOUR_NAMES[colour(c)] + "  ·  " + BOX_NAMES[box(c)];
    }

    /** The languages in words: "English, French". */
    public static String languageWords(Context c) {
        List<String> n = new ArrayList<>();
        for (String l : languages(c)) n.add(Langs.name(l));
        return String.join(", ", n);
    }

    private static int clamp(int i, int n) {
        return Math.max(0, Math.min(n - 1, i));
    }

    /** Pure: "+1.5 s", "−0.5 s", "0.0 s": how far the lines are moved (later is +). */
    public static String offsetWords(int ms) {
        String n = String.format(Locale.UK, "%.1f s", Math.abs(ms) / 1000.0);
        return ms > 0 ? "+" + n : ms < 0 ? "−" + n : n;
    }

    public static int clampOffset(int ms) {
        return Math.max(-MAX_OFFSET_MS, Math.min(MAX_OFFSET_MS, ms));
    }

    // ---- the OpenSubtitles login ----------------------------------------------------------------------------------------

    public static boolean hasKey(Context c) {
        return !key(c).isEmpty();
    }

    static String key(Context c) {
        return account(c).getString("key", "").trim();
    }

    public static String user(Context c) {
        return account(c).getString("user", "").trim();
    }

    public static boolean hasPassword(Context c) {
        return !account(c).getString("pass", "").isEmpty();
    }

    static String password(Context c) {
        return account(c).getString("pass", "");
    }

    /** A new API key (trimmed); the old sign-in let go. */
    public static void setKey(Context c, String key) {
        account(c).edit().putString("key", key == null ? "" : key.trim()).remove("token").remove("token_at").remove("base").apply();
    }

    public static void setUser(Context c, String user) {
        account(c).edit().putString("user", user == null ? "" : user.trim()).remove("token").remove("token_at").remove("base").apply();
    }

    public static void setPassword(Context c, String password) {
        account(c).edit().putString("pass", password == null ? "" : password).remove("token").remove("token_at").remove("base").apply();
    }

    /** The key, the account and its sign-in gone from this TV. */
    public static void forget(Context c) {
        account(c).edit().clear().apply();
    }

    /** The key as Settings shows it: dots and its last four letters ("" for none). */
    public static String maskedKey(Context c) {
        return masked(key(c));
    }

    /** Pure: a key or a name as Settings shows it: its last four letters after dots ("••••••••a1b2"); "" for none. */
    public static String masked(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s.trim();
        return t.length() <= 4 ? "••••" : "••••••••" + t.substring(t.length() - 4);
    }

    // ---- each title's own choice ------------------------------------------------------------------------------------------

    /**
     * What a title showed last: nothing chosen yet (NONE), Off, one of the stream's own tracks (its language and name), or
     * a file from OpenSubtitles (its id, where it is on the TV, its language and release, the film's or episode's it was
     * found for) with its timing. A series keeps one: its next episode looks for the same language.
     */
    public static final class Choice {
        public static final int NONE = -1, OFF = 0, OWN = 1, FOUND = 2;
        public int kind = NONE;
        public String lang = "", label = "", path = "", release = "", episode = "";
        public long fileId;
        public int offsetMs;
        public boolean hearingImpaired;

        public static Choice off() {
            Choice c = new Choice();
            c.kind = OFF;
            return c;
        }

        public static Choice own(String lang, String label) {
            Choice c = new Choice();
            c.kind = OWN;
            c.lang = lang == null ? "" : lang;
            c.label = label == null ? "" : label;
            return c;
        }

        public static Choice found(OpenSubtitles.Result r, String path, String episode) {
            Choice c = new Choice();
            c.kind = FOUND;
            c.fileId = r.fileId;
            c.lang = r.lang;
            c.release = r.release;
            c.hearingImpaired = r.hearingImpaired;
            c.path = path == null ? "" : path;
            c.episode = episode == null ? "" : episode;
            return c;
        }

        /** Whether its file is on the TV and is this film's or episode's (else the language is looked for again). */
        public boolean fileFor(String episodeId) {
            return kind == FOUND && !path.isEmpty() && episode.equals(episodeId == null ? "" : episodeId) && new File(path).isFile();
        }

        JSONObject json(long at) throws JSONException {
            return new JSONObject().put("k", kind).put("lang", lang).put("label", label).put("path", path).put("release", release)
                    .put("ep", episode).put("file", fileId).put("offset", offsetMs).put("hi", hearingImpaired)
                    .put("at", at);
        }

        static Choice from(JSONObject o) {
            Choice c = new Choice();
            c.kind = o.optInt("k", NONE);
            if (c.kind < NONE || c.kind > FOUND) c.kind = NONE;
            c.lang = o.optString("lang");
            c.label = o.optString("label");
            c.path = o.optString("path");
            c.release = o.optString("release");
            c.episode = o.optString("ep");
            c.fileId = o.optLong("file");
            c.offsetMs = clampOffset(o.optInt("offset"));
            c.hearingImpaired = o.optBoolean("hi");
            return c;
        }
    }

    /** The title's choice (its key: Catalog.Title.key; one for a whole series), or one of kind NONE. */
    public static Choice choice(Context c, String title) {
        try {
            JSONObject all = new JSONObject(prefs(c).getString("titles", "{}"));
            JSONObject o = all.optJSONObject(title == null ? "" : title);
            if (o != null) return Choice.from(o);
        } catch (JSONException ignored) {
            // none kept
        }
        return new Choice();
    }

    /** The title's choice kept (the oldest let go past KEEP_TITLES). */
    public static void remember(Context c, String title, Choice choice) {
        if (title == null || title.isEmpty() || choice == null) return;
        try {
            JSONObject all = new JSONObject(prefs(c).getString("titles", "{}"));
            // Newer than every other kept (two in the same millisecond keep their order).
            long newest = 0;
            for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                JSONObject o = all.optJSONObject(it.next());
                if (o != null) newest = Math.max(newest, o.optLong("at"));
            }
            all.put(title, choice.json(Math.max(System.currentTimeMillis(), newest + 1)));
            while (all.length() > KEEP_TITLES) {
                String oldest = null;
                long at = Long.MAX_VALUE;
                for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONObject o = all.optJSONObject(k);
                    long when = o == null ? 0 : o.optLong("at");
                    if (when < at) {
                        at = when;
                        oldest = k;
                    }
                }
                if (oldest == null) break;
                all.remove(oldest);
            }
            prefs(c).edit().putString("titles", all.toString()).apply();
        } catch (JSONException ignored) {
            // not kept
        }
    }

    /** Where the downloaded files go (the app's own storage), the oldest let go past KEEP_FILES. */
    public static File folder(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "subtitles");
        if (!d.isDirectory()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        File[] all = d.listFiles();
        if (all != null && all.length > KEEP_FILES) {
            Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < all.length - KEEP_FILES; i++) //noinspection ResultOfMethodCallIgnored
                all[i].delete();
        }
        return d;
    }
}
