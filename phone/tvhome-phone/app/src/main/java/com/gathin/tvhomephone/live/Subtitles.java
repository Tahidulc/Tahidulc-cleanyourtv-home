package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Subtitles for films and episodes: the family's choices (on or off for every film, the languages in the order wanted,
 * the size and look, subtitles for the deaf or not) and what this phone kept of each film's (the file found on
 * OpenSubtitles and its timing). The parts that choose are pure: the search's address, the order the results are
 * offered in, the file's kind and its writing turned to UTF-8, and which lines show at a moment. Kept on this phone.
 */
public final class Subtitles {
    private Subtitles() {}

    private static final String PREFS = "subtitles";
    /** The languages offered to choose from, as OpenSubtitles names them (its codes), most asked for here first. */
    public static final String[] CODES = {"en", "bn", "ar", "ur", "hi", "pa", "fr", "es", "de", "it", "pt-PT", "pt-BR",
            "nl", "pl", "ro", "tr", "el", "ru", "sv", "da", "no", "fi", "fa", "he", "id", "ms", "zh-CN", "ja", "ko", "so"};
    /** The text's size, against the player's own (1.0). */
    public static final float[] SIZES = {0.8f, 1f, 1.25f, 1.5f};
    public static final String[] SIZE_NAMES = {"Small", "Medium", "Large", "Extra large"};
    public static final String[] LOOK_NAMES = {"White outline", "Black box", "Yellow"};
    /** Each nudge of the timing, and how far it may go either way. */
    public static final int STEP_MS = 500, MAX_OFFSET_MS = 60_000;
    /** How many films' downloaded files are kept on the phone. */
    static final int KEEP_FILES = 80;

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- the family's choices -----------------------------------------------------------------------------------

    /** Subtitles on for every film (the player's choice, kept), off unless turned on. */
    public static boolean on(Context c) { return prefs(c).getBoolean("on", false); }

    public static void setOn(Context c, boolean on) { prefs(c).edit().putBoolean("on", on).apply(); }

    /** The languages wanted, in order (English when none were chosen). */
    public static List<String> languages(Context c) {
        List<String> out = new ArrayList<>();
        for (String s : prefs(c).getString("langs", "en").split(",")) if (!s.trim().isEmpty() && !out.contains(s.trim())) out.add(s.trim());
        if (out.isEmpty()) out.add("en");
        return out;
    }

    public static void setLanguages(Context c, List<String> langs) {
        prefs(c).edit().putString("langs", String.join(",", langs)).apply();
    }

    public static int size(Context c) { return clampIndex(prefs(c).getInt("size", 1), SIZES.length); }

    public static void setSize(Context c, int i) { prefs(c).edit().putInt("size", clampIndex(i, SIZES.length)).apply(); }

    public static int look(Context c) { return clampIndex(prefs(c).getInt("look", 0), LOOK_NAMES.length); }

    public static void setLook(Context c, int i) { prefs(c).edit().putInt("look", clampIndex(i, LOOK_NAMES.length)).apply(); }

    /** Subtitles for the deaf (sounds written in too) offered first. */
    public static boolean hearingImpaired(Context c) { return prefs(c).getBoolean("hi", false); }

    public static void setHearingImpaired(Context c, boolean on) { prefs(c).edit().putBoolean("hi", on).apply(); }

    /** When a film has none of its own in the languages wanted, the best one found on OpenSubtitles comes on by itself. */
    public static boolean autoFind(Context c) { return prefs(c).getBoolean("auto", true); }

    public static void setAutoFind(Context c, boolean on) { prefs(c).edit().putBoolean("auto", on).apply(); }

    private static int clampIndex(int i, int n) { return Math.max(0, Math.min(n - 1, i)); }

    /** A language's name in English ("Bengali"), from its code ("bn", "pt-BR", "eng"). */
    public static String languageName(String code) {
        if (code == null || code.isEmpty() || "und".equals(code)) return "";
        if ("pt-BR".equalsIgnoreCase(code)) return "Portuguese (Brazil)";
        if ("pt-PT".equalsIgnoreCase(code)) return "Portuguese";
        if ("zh-CN".equalsIgnoreCase(code)) return "Chinese";
        if ("zh-TW".equalsIgnoreCase(code)) return "Chinese (traditional)";
        String n = Locale.forLanguageTag(code).getDisplayLanguage(Locale.UK);
        return n.isEmpty() ? code : n;
    }

    // ---- what was kept of a film ----------------------------------------------------------------------------------

    /** A file from OpenSubtitles kept for a film: where it is on the phone, what it is, and its timing. */
    public static final class Kept {
        public String path = "", lang = "", release = "";
        public long fileId;
        public int offsetMs;

        public boolean exists() { return !path.isEmpty() && new File(path).isFile(); }

        JSONObject json() {
            JSONObject o = new JSONObject();
            try {
                o.put("path", path).put("lang", lang).put("release", release).put("file", fileId).put("offset", offsetMs);
            } catch (JSONException ignored) {
                // what there is
            }
            return o;
        }

        static Kept from(JSONObject o) {
            Kept k = new Kept();
            k.path = o.optString("path");
            k.lang = o.optString("lang");
            k.release = o.optString("release");
            k.fileId = o.optLong("file");
            k.offsetMs = clampOffset(o.optInt("offset"));
            return k;
        }
    }

    /** The files kept for a film (by its player's key), the one last shown first. */
    public static List<Kept> kept(Context c, String film) {
        List<Kept> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("f." + film, "[]"));
            for (int i = 0; i < a.length(); i++) {
                Kept k = Kept.from(a.getJSONObject(i));
                if (k.exists()) out.add(k);
            }
        } catch (JSONException ignored) {
            // none kept
        }
        return out;
    }

    /** That file kept for the film, first (the one to show next time it plays). */
    public static void keep(Context c, String film, Kept k) {
        List<Kept> l = kept(c, film);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).fileId == k.fileId) l.remove(i);
        l.add(0, k);
        JSONArray a = new JSONArray();
        for (int i = 0; i < l.size() && i < 4; i++) a.put(l.get(i).json());
        prefs(c).edit().putString("f." + film, a.toString()).putString("last." + film, "os").apply();
    }

    /** What the film showed last: "os" (its OpenSubtitles file), "own" (the stream's own), "off", or "" (not chosen). */
    public static String last(Context c, String film) { return prefs(c).getString("last." + film, ""); }

    public static void setLast(Context c, String film, String what) { prefs(c).edit().putString("last." + film, what).apply(); }

    /** Where the downloaded files go, and the oldest let go past KEEP_FILES. */
    static File folder(Context c) {
        File d = new File(c.getFilesDir(), "subtitles");
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

    static int clampOffset(int ms) { return Math.max(-MAX_OFFSET_MS, Math.min(MAX_OFFSET_MS, ms)); }

    /** "+1.5 s", "−0.5 s", "0.0 s": how far the lines are moved (later is +). */
    public static String offsetText(int ms) {
        String n = String.format(Locale.UK, "%.1f s", Math.abs(ms) / 1000.0);
        return ms > 0 ? "+" + n : ms < 0 ? "−" + n : n;
    }

    // ---- the search -----------------------------------------------------------------------------------------------

    /**
     * Pure: the search's query (after "/subtitles?"), its parameters in alphabetical order and their values in lower
     * case as OpenSubtitles asks (any other order is sent round by a redirect). By TMDB id when there is one (the
     * series' for an episode), by name otherwise; languages empty: every language.
     */
    public static String query(int tmdb, boolean tv, int season, int episode, String name, List<String> languages) {
        Map<String, String> p = new TreeMap<>();
        if (tv) {
            if (season > 0) p.put("season_number", String.valueOf(season));
            if (episode > 0) p.put("episode_number", String.valueOf(episode));
            if (tmdb > 0) p.put("parent_tmdb_id", String.valueOf(tmdb));
            else p.put("query", searchName(name));
            p.put("type", "episode");
        } else {
            if (tmdb > 0) p.put("tmdb_id", String.valueOf(tmdb));
            else p.put("query", searchName(name));
            p.put("type", "movie");
        }
        if (languages != null && !languages.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (String s : languages) l.add(s.toLowerCase(Locale.ROOT));
            Collections.sort(l);
            p.put("languages", String.join(",", l));
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : p.entrySet()) {
            if (b.length() > 0) b.append('&');
            b.append(e.getKey()).append('=').append(enc(e.getValue().toLowerCase(Locale.ROOT)));
        }
        return b.toString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8").replace("%2C", ",");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    /** Pure: a playlist's name for a film as a search would want it: no country tag, quality, year or brackets. */
    public static String searchName(String name) {
        if (name == null) return "";
        String s = name.replaceAll("^\\s*(\\|[^|]*\\||\\[[^\\]]*\\]|[A-Z]{2,3}\\s*[:|-])\\s*", "");
        s = s.replaceAll("\\(\\s*(19|20)\\d\\d\\s*\\)", " ").replaceAll("\\[[^\\]]*\\]", " ");
        s = s.replaceAll("(?i)\\b(4k|uhd|fhd|hd|sd|hevc|x265|x264|h\\.?26[45]|2160p|1080p|720p|480p|multi|sub|subs|dub|dubbed|vost(fr)?)\\b", " ");
        s = s.replaceAll("\\b(19|20)\\d\\d\\s*$", " ").replaceAll("[|•·]", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** One subtitle file OpenSubtitles has for it. */
    public static final class Result {
        public long fileId;
        public String lang = "", release = "", fileName = "";
        public int downloads;
        public double fps;
        public boolean hearingImpaired, trusted, machine, foreignOnly;

        /** Pure: a result from its JSON in the search's reply (null when it has no file). */
        public static Result from(JSONObject item) {
            JSONObject a = item == null ? null : item.optJSONObject("attributes");
            if (a == null) return null;
            JSONArray files = a.optJSONArray("files");
            if (files == null || files.length() == 0) return null;
            JSONObject f = files.optJSONObject(0);
            if (f == null || f.optLong("file_id") <= 0) return null;
            Result r = new Result();
            r.fileId = f.optLong("file_id");
            r.fileName = f.optString("file_name");
            r.lang = a.optString("language");
            r.release = a.optString("release");
            if (r.release.isEmpty()) r.release = r.fileName;
            r.downloads = Math.max(a.optInt("download_count"), a.optInt("new_download_count"));
            r.fps = a.optDouble("fps", 0);
            if (Double.isNaN(r.fps)) r.fps = 0;
            r.hearingImpaired = a.optBoolean("hearing_impaired");
            r.trusted = a.optBoolean("from_trusted");
            r.machine = a.optBoolean("machine_translated") || a.optBoolean("ai_translated");
            r.foreignOnly = a.optBoolean("foreign_parts_only");
            return r;
        }
    }

    /** Pure: the results in the search's reply. */
    public static List<Result> results(String body) {
        List<Result> out = new ArrayList<>();
        try {
            JSONArray d = new JSONObject(body).optJSONArray("data");
            if (d == null) return out;
            for (int i = 0; i < d.length(); i++) {
                Result r = Result.from(d.optJSONObject(i));
                if (r != null) out.add(r);
            }
        } catch (JSONException ignored) {
            // nothing usable
        }
        return out;
    }

    /** Pure: whether a subtitle's frame rate fits the picture's (so its lines keep time all the way through). */
    public static boolean fpsFits(double subtitle, float picture) {
        if (subtitle <= 0 || picture <= 0) return false;
        return Math.abs(subtitle - picture) < 0.05;
    }

    /** Pure: whether a release's name says the same quality as the picture playing ("1080p" in both). */
    public static boolean qualityFits(String release, String quality) {
        if (release == null || quality == null || quality.isEmpty()) return false;
        String r = release.toLowerCase(Locale.ROOT);
        switch (quality) {
            case "4K": return r.contains("2160p") || r.contains("4k") || r.contains("uhd");
            case "1080p": return r.contains("1080");
            case "720p": return r.contains("720");
            case "SD": return r.contains("480") || r.contains("dvdrip") || r.contains("xvid");
            default: return false;
        }
    }

    /**
     * Pure: the results in the order offered, best first: the languages in the family's order (any other after), not
     * machine translated, only foreign parts last, the frame rate and quality that fit the picture, SDH as wanted,
     * trusted uploaders, then the most downloaded.
     */
    public static List<Result> rank(List<Result> in, List<String> languages, float pictureFps, String quality, boolean wantHi) {
        List<Result> out = new ArrayList<>(in);
        Collections.sort(out, (a, b) -> {
            int c = Integer.compare(langRank(a.lang, languages), langRank(b.lang, languages));
            if (c != 0) return c;
            c = Boolean.compare(a.foreignOnly, b.foreignOnly);
            if (c != 0) return c;
            c = Boolean.compare(a.machine, b.machine);
            if (c != 0) return c;
            c = Integer.compare(score(b, pictureFps, quality, wantHi), score(a, pictureFps, quality, wantHi));
            if (c != 0) return c;
            return Integer.compare(b.downloads, a.downloads);
        });
        return out;
    }

    private static int score(Result r, float fps, String quality, boolean wantHi) {
        int s = 0;
        if (fpsFits(r.fps, fps)) s += 4;
        if (qualityFits(r.release, quality)) s += 2;
        if (r.hearingImpaired == wantHi) s += 2;
        if (r.trusted) s += 1;
        return s;
    }

    /** Pure: a language's place in the family's order (after all of them when not among them). */
    static int langRank(String lang, List<String> languages) {
        if (languages == null) return 0;
        for (int i = 0; i < languages.size(); i++) if (sameLanguage(lang, languages.get(i))) return i;
        return languages.size();
    }

    /** Pure: "en" and "eng", "pt-BR" and "pt" are the same language. */
    public static boolean sameLanguage(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return base(a).equals(base(b));
    }

    private static String base(String code) {
        String c = code.toLowerCase(Locale.ROOT);
        int dash = c.indexOf('-');
        if (dash > 0) c = c.substring(0, dash);
        if (c.length() == 3) {
            for (String iso : Locale.getISOLanguages()) {
                try {
                    if (new Locale(iso).getISO3Language().equals(c)) return iso;
                } catch (java.util.MissingResourceException ignored) {
                    // not this one
                }
            }
            // The bibliographic codes Locale doesn't know.
            switch (c) {
                case "ger": return "de";
                case "fre": return "fr";
                case "dut": return "nl";
                case "chi": return "zh";
                case "per": return "fa";
                case "gre": return "el";
                case "rum": return "ro";
                default: return c;
            }
        }
        return c;
    }

    // ---- the file -------------------------------------------------------------------------------------------------

    /** Pure: the file's kind by what it starts with: WebVTT, SubStation (ASS/SSA) or SubRip (most of them). */
    public static String mime(byte[] data) {
        String head = new String(data, 0, Math.min(data.length, 2048), StandardCharsets.ISO_8859_1).replace(String.valueOf((char) 0xFEFF), "").replace("\u00EF\u00BB\u00BF", "").trim();
        if (head.startsWith("WEBVTT")) return androidx.media3.common.MimeTypes.TEXT_VTT;
        if (head.contains("[Script Info]") || head.contains("[V4+ Styles]") || head.contains("[V4 Styles]")) return androidx.media3.common.MimeTypes.TEXT_SSA;
        return androidx.media3.common.MimeTypes.APPLICATION_SUBRIP;
    }

    /**
     * Pure: the file's writing as UTF-8: as it is when it already is, otherwise read in the Windows code page its
     * language used (Arabic, Cyrillic, Greek, Turkish, Hebrew, the West's for the rest) and written again.
     */
    public static byte[] utf8(byte[] data, String lang) {
        int start = data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, start, data.length - start));
            return start == 0 ? data : Arrays.copyOfRange(data, start, data.length);
        } catch (CharacterCodingException notUtf8) {
            // UTF-16 with its mark.
            if (data.length >= 2 && ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE || (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF)) {
                return new String(data, StandardCharsets.UTF_16).getBytes(StandardCharsets.UTF_8);
            }
            Charset cs = Charset.forName(codePage(lang));
            CharBuffer chars = cs.decode(ByteBuffer.wrap(data));
            return chars.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    static String codePage(String lang) {
        String b = lang == null ? "" : base(lang);
        switch (b) {
            case "ar": case "fa": case "ur": return "windows-1256";
            case "ru": case "bg": case "uk": case "sr": case "mk": case "be": return "windows-1251";
            case "el": return "windows-1253";
            case "tr": return "windows-1254";
            case "he": return "windows-1255";
            case "pl": case "cs": case "hu": case "ro": case "hr": case "sk": case "sl": return "windows-1250";
            default: return "windows-1252";
        }
    }

    // ---- what shows when ------------------------------------------------------------------------------------------

    /**
     * Pure: the lines showing at a moment (µs into the film, the timing already taken off), as their places in a list
     * sorted by start; a line with no end of its own (its end below 0) lasts until the next one starts.
     */
    public static int[] active(long[] starts, long[] ends, long at) {
        int n = starts.length;
        // The last line that has started by now.
        int lo = 0, hi = n - 1, last = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (starts[mid] <= at) {
                last = mid;
                lo = mid + 1;
            } else hi = mid - 1;
        }
        if (last < 0) return new int[0];
        List<Integer> on = new ArrayList<>();
        // Lines overlap only a little: a few back is enough (a long one is kept by its end).
        for (int i = last; i >= 0 && i >= last - 40; i--) {
            long end = ends[i] >= 0 ? ends[i] : (i + 1 < n ? starts[i + 1] : Long.MAX_VALUE);
            if (at < end) on.add(0, i);
        }
        int[] out = new int[on.size()];
        for (int i = 0; i < out.length; i++) out[i] = on.get(i);
        return out;
    }
}
