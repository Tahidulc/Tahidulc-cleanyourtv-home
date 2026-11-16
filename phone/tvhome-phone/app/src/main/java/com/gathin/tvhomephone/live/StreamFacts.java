package com.gathin.tvhomephone.live;

import android.content.Context;

import com.gathin.tvhomephone.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the provider itself says of a film's or series' picture and sound, for the posters' corners when the playlists'
 * names and categories say nothing: an Xtream account's get_vod_info (a series' get_series_info, its first episodes),
 * whose "video" and "audio" are the stream as the provider read it (its width and height, its HDR transfer, Dolby Vision,
 * Atmos). Asked once a title, when its poster shows (the newest shown first, one at a time, at most PER_HOUR an hour),
 * and kept for a month (a title it said nothing of, for a week). An M3U playlist has nobody to ask.
 */
public final class StreamFacts {
    private StreamFacts() {}

    private static final String FILE = "stream-facts.json";
    static final long KEEP = 30L * 24 * 3600_000L, KEEP_NOTHING = 7L * 24 * 3600_000L, HOUR = 3600_000L;
    /** At most this many questions to the provider an hour, so a long scroll through Watch never floods it. */
    static final int PER_HOUR = 120;
    /** Posters waiting to be asked about, at most this many (those scrolled past longest ago let go). */
    private static final int WAITING = 40;
    private static final Pattern MOVIE_ID = Pattern.compile("x:movie:(\\d+)");

    private static final Map<String, long[]> facts = new HashMap<>();
    private static final LinkedHashMap<String, Ask> waiting = new LinkedHashMap<>();
    private static final ArrayDeque<Long> asked = new ArrayDeque<>();
    private static boolean loaded, busy, dirty;

    private static final class Ask {
        final boolean tv;
        final int tmdb;
        final String title, year;
        final List<IntConsumer> done = new ArrayList<>(1);

        Ask(boolean tv, int tmdb, String title, String year) {
            this.tv = tv;
            this.tmdb = tmdb;
            this.title = title;
            this.year = year;
        }
    }

    /** Pure: the title's key: its TMDB id, or its name and year when it has none. */
    static String key(boolean tv, int tmdb, String title, String year) {
        String kind = tv ? "tv:" : "mv:";
        return tmdb > 0 ? kind + tmdb : kind + Vod.key(title == null ? "" : title) + ":" + (year == null ? "" : year);
    }

    /** What was found for the title, at once: SourceQuality's bits (0: the provider said nothing), or -1 when not asked yet. */
    public static synchronized int known(Context c, boolean tv, int tmdb, String title, String year) {
        load(c);
        long[] f = facts.get(key(tv, tmdb, title, year));
        if (f == null) return -1;
        return System.currentTimeMillis() - f[1] > (f[0] == 0 ? KEEP_NOTHING : KEEP) ? -1 : (int) f[0];
    }

    /**
     * A poster of the title shows and nothing is known of it yet: the provider is asked (once, in turn), and done gets
     * what it said on the main thread, when it said something.
     */
    public static void ask(Context c, boolean tv, int tmdb, String title, String year, IntConsumer done) {
        if (title == null || title.isEmpty() || known(c, tv, tmdb, title, year) >= 0) return;
        Context app = c.getApplicationContext();
        String k = key(tv, tmdb, title, year);
        synchronized (StreamFacts.class) {
            Ask a = waiting.remove(k);
            if (a == null) a = new Ask(tv, tmdb, title, year);
            a.done.add(done);
            // Last in, first asked: the posters on the screen now before those scrolled past.
            waiting.put(k, a);
            Iterator<String> oldest = waiting.keySet().iterator();
            while (waiting.size() > WAITING && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
            if (busy) return;
            busy = true;
        }
        App.io.execute(() -> run(app));
    }

    private static void run(Context app) {
        while (true) {
            String k = null;
            Ask a = null;
            synchronized (StreamFacts.class) {
                long now = System.currentTimeMillis();
                while (!asked.isEmpty() && now - asked.peekFirst() > HOUR) asked.pollFirst();
                if (waiting.isEmpty() || asked.size() >= PER_HOUR) {
                    busy = false;
                    save(app);
                    return;
                }
                for (Map.Entry<String, Ask> e : waiting.entrySet()) {
                    k = e.getKey();
                    a = e.getValue();
                }
                waiting.remove(k);
            }
            int bits = 0;
            try {
                int versions = 0;
                for (Vod.Item it : Vod.inXtream(app, a.title, a.year, a.tmdb, a.tv)) {
                    if (versions++ == 2) break;
                    synchronized (StreamFacts.class) {
                        asked.addLast(System.currentTimeMillis());
                    }
                    bits = SourceQuality.best(bits, askProvider(app, it));
                }
            } catch (RuntimeException ignored) {
                // nothing said
            }
            synchronized (StreamFacts.class) {
                facts.put(k, new long[]{bits, System.currentTimeMillis()});
                dirty = true;
            }
            if (bits != 0) {
                int b = bits;
                List<IntConsumer> done = a.done;
                App.main.post(() -> {
                    for (IntConsumer d : done) d.accept(b);
                });
            }
        }
    }

    /** One version's facts from its playlist's provider (0 for none, or when it can't be asked). */
    private static int askProvider(Context c, Vod.Item it) {
        Sources.Entry e = Sources.get(c, it.source);
        if (e == null || e.source == null || !Source.XTREAM.equals(e.source.type)) return 0;
        Source s = e.source;
        String base = Xtream.base(s.server);
        if (base.isEmpty()) return 0;
        boolean series = "series".equals(it.kind);
        String url;
        if (series) {
            if (it.seriesId == null || it.seriesId.isEmpty()) return 0;
            url = Xtream.api(base, s.user, s.pass, "get_series_info") + "&series_id=" + Xtream.enc(it.seriesId);
        } else {
            String id = movieId(it.url);
            if (id.isEmpty()) return 0;
            url = Xtream.api(base, s.user, s.pass, "get_vod_info") + "&vod_id=" + Xtream.enc(id);
        }
        try (InputStream in = Fetch.open(url, null)) {
            JSONObject root = new JSONObject(new String(readAll(in), StandardCharsets.UTF_8));
            return series ? ofSeries(root) : ofFilm(root);
        } catch (Exception x) {
            return 0;
        }
    }

    /** Pure: an Xtream film's id from its address as the index keeps it ("p1|x:movie:123.mkv" → "123"; "" for none). */
    static String movieId(String url) {
        if (url == null) return "";
        Matcher m = MOVIE_ID.matcher(url);
        return m.find() ? m.group(1) : "";
    }

    /** Pure: a get_vod_info answer's picture and sound. */
    static int ofFilm(JSONObject root) {
        JSONObject info = root == null ? null : root.optJSONObject("info");
        return info == null ? 0 : bits(info.optJSONObject("video"), info.optJSONObject("audio"));
    }

    /** Pure: a get_series_info answer's, from its first few episodes the provider read (the best of them). */
    static int ofSeries(JSONObject root) {
        JSONObject eps = root == null ? null : root.optJSONObject("episodes");
        if (eps == null) return 0;
        int best = 0, read = 0;
        for (Iterator<String> it = eps.keys(); it.hasNext() && read < 3; ) {
            JSONArray a = eps.optJSONArray(it.next());
            for (int i = 0; a != null && i < a.length() && read < 3; i++) {
                JSONObject ep = a.optJSONObject(i);
                JSONObject info = ep == null ? null : ep.optJSONObject("info");
                if (info == null || info.optJSONObject("video") == null) continue;
                best = SourceQuality.best(best, bits(info.optJSONObject("video"), info.optJSONObject("audio")));
                read++;
            }
        }
        return best;
    }

    /**
     * Pure: the stream as the provider read it (ffprobe's names): its picture by its width (a film's 3840×1600 is 4K) or
     * height, HDR10 for the PQ transfer (smpte2084), HLG for arib-std-b67, Dolby Vision by its codec tag (dvh1, dvhe,
     * dav1) or its configuration record, and Atmos when the sound's profile says so.
     */
    static int bits(JSONObject video, JSONObject audio) {
        int v = 0, h = 0;
        if (video != null) {
            int w = video.optInt("width"), ht = video.optInt("height");
            if (w >= 3200 || ht >= 2000) v = SourceQuality.UHD;
            else if (w >= 1700 || ht >= 1000) v = SourceQuality.FHD;
            else if (w >= 1100 || ht >= 700) v = SourceQuality.HD720;
            else if (w > 0 || ht > 0) v = SourceQuality.SD;
            String transfer = lower(video, "color_transfer"), tag = lower(video, "codec_tag_string");
            String codec = lower(video, "codec_name"), profile = lower(video, "profile");
            boolean dv = tag.startsWith("dvh") || tag.startsWith("dva") || tag.equals("dav1") || codec.startsWith("dvh")
                    || profile.contains("dolby vision") || sideData(video, "dovi");
            if (dv) h = SourceQuality.DOLBY_VISION;
            else if (transfer.contains("smpte2084")) h = sideData(video, "hdr10+") ? SourceQuality.HDR10_PLUS : SourceQuality.HDR10;
            else if (transfer.contains("arib-std-b67")) h = SourceQuality.HLG;
        }
        boolean atmos = audio != null && (lower(audio, "profile") + " " + lower(audio, "codec_long_name") + " " + lower(audio, "title")).contains("atmos");
        return v | h << 3 | (atmos ? SourceQuality.ATMOS : 0);
    }

    private static String lower(JSONObject o, String k) {
        return o.optString(k, "").toLowerCase(Locale.ROOT);
    }

    private static boolean sideData(JSONObject video, String needle) {
        JSONArray a = video.optJSONArray("side_data_list");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject d = a.optJSONObject(i);
            if (d != null && d.optString("side_data_type", "").toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    // ---- kept on the phone ----------------------------------------------------------------------------------------

    private static void load(Context c) {
        if (loaded) return;
        loaded = true;
        File f = new File(c.getApplicationContext().getFilesDir(), FILE);
        if (!f.exists()) return;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = o.optJSONArray(k);
                if (a != null && a.length() == 2) facts.put(k, new long[]{a.optLong(0), a.optLong(1)});
            }
        } catch (Exception ignored) {
            // asked again
        }
    }

    /** Written when a round of questions ends (those older than two months let go). Holds the class's lock. */
    private static void save(Context c) {
        if (!dirty) return;
        dirty = false;
        long now = System.currentTimeMillis();
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, long[]> e : facts.entrySet()) {
                if (now - e.getValue()[1] > 2 * KEEP) continue;
                o.put(e.getKey(), new JSONArray().put(e.getValue()[0]).put(e.getValue()[1]));
            }
        } catch (Exception ignored) {
            return;
        }
        File f = new File(c.getFilesDir(), FILE), tmp = new File(c.getFilesDir(), FILE + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > 8 << 20) break;
        }
        return out.toByteArray();
    }
}
