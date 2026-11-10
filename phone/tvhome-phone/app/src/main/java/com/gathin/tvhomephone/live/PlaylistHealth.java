package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.ConnectivityManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.gathin.tvhomephone.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How each playlist is doing, checked quietly about once a week (PlaylistWorker) or when Settings' "Check now" is
 * tapped: how long the playlist (or its Xtream account) takes to answer, how long a few of its live channels take to
 * give their first picture bytes, and how many of them fail. Only while nothing plays in the app or anywhere on the
 * phone, the weekly one only on Wi-Fi (or another unmetered network), one stream at a time, each closed after a couple of
 * seconds, and none at all while the account's connections are all in use (the TV may be on it). Kept per playlist on
 * this phone; told only when a playlist turns Struggling. score() lets the player prefer the healthier playlist.
 */
public final class PlaylistHealth {
    private PlaylistHealth() {}

    public static final String HEALTHY = "Healthy", SLOW = "Slow", STRUGGLING = "Struggling";
    static final long WEEK = 7 * 86_400_000L;
    /** At most this many channels sampled per playlist, each read for about this long. */
    static final int SAMPLES = 5;
    static final long SAMPLE_MS = 2_000L, TIMEOUT_MS = 8_000L;

    /** One check's result. answerMs and startMs are -1 when unknown (no answer, nothing sampled). */
    public static final class Result {
        public final long answerMs, startMs, checkedAt;
        public final int sampled, failed;
        public final String verdict;

        Result(long answerMs, long startMs, int sampled, int failed, long checkedAt) {
            this.answerMs = answerMs;
            this.startMs = startMs;
            this.sampled = sampled;
            this.failed = failed;
            this.checkedAt = checkedAt;
            this.verdict = verdict(answerMs, startMs, sampled, failed);
        }

        JSONObject json() throws JSONException {
            return new JSONObject().put("answer", answerMs).put("start", startMs).put("sampled", sampled).put("failed", failed)
                    .put("at", checkedAt).put("verdict", verdict);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("playlist_health", Context.MODE_PRIVATE);
    }

    /**
     * The word for a check (pure): Struggling when the playlist didn't answer, half or more of the sampled channels
     * failed, or they took over 8 s to start; Slow when any failed, they took over 3 s, or the playlist over 6 s to
     * answer; Healthy otherwise.
     */
    static String verdict(long answerMs, long startMs, int sampled, int failed) {
        if (answerMs < 0 && sampled == 0) return STRUGGLING;
        if (sampled > 0 && (failed * 2 >= sampled || startMs > 8_000L)) return STRUGGLING;
        if (failed > 0 || startMs > 3_000L || answerMs > 6_000L) return SLOW;
        return HEALTHY;
    }

    /**
     * A playlist's score for choosing between playlists that have the same channel (pure): lower is better: the time
     * its channels take to start (or the playlist to answer), plus 10 s for every channel in ten that failed, plus 2 s
     * for Slow and 8 s for Struggling.
     */
    static int score(Result r) {
        if (r == null) return -1;
        long base = r.startMs >= 0 ? r.startMs : Math.max(0, r.answerMs);
        long fails = r.sampled > 0 ? 10_000L * r.failed / r.sampled : 0;
        long word = STRUGGLING.equals(r.verdict) ? 8_000L : SLOW.equals(r.verdict) ? 2_000L : 0;
        return (int) Math.min(Integer.MAX_VALUE, base + fails + word);
    }

    /**
     * The player's read: how well a playlist (by its Sources id) did when last checked, lower being better, or -1 when
     * it has not been checked (or not for over a month).
     */
    public static int score(Context c, String id) {
        Result r = read(c, id);
        if (r == null || System.currentTimeMillis() - r.checkedAt > 4 * WEEK) return -1;
        return score(r);
    }

    /** The last check of a playlist, or null when never checked. */
    public static Result read(Context c, String id) {
        String raw = prefs(c).getString("health." + id, null);
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            return new Result(o.optLong("answer", -1), o.optLong("start", -1), o.optInt("sampled"), o.optInt("failed"), o.optLong("at"));
        } catch (JSONException e) {
            return null;
        }
    }

    static void forget(Context c, String id) {
        prefs(c).edit().remove("health." + id).apply();
    }

    /** Whether something is playing, here (Live TV) or anywhere on the phone (a film, another app): then no check. */
    static boolean busy(Context c) {
        if (Playback.get(c).live()) return true;
        AudioManager am = c.getSystemService(AudioManager.class);
        return am != null && am.isMusicActive();
    }

    static boolean unmetered(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        return cm != null && cm.getActiveNetwork() != null && !cm.isActiveNetworkMetered();
    }

    /** The weekly check: each playlist not checked for a week, while the phone is idle on Wi-Fi. Off the main thread. */
    static void weekly(Context c) {
        if (!unmetered(c)) return;
        long now = System.currentTimeMillis();
        for (Sources.Entry e : Sources.list(c)) {
            Result had = read(c, e.id);
            if (had != null && now - had.checkedAt < WEEK) continue;
            if (busy(c)) return;
            check(c, e);
        }
    }

    /**
     * Checks one playlist now and keeps the result (null when something started playing meanwhile). Told when it has
     * just turned Struggling. Off the main thread; network.
     */
    public static Result check(Context c, Sources.Entry e) {
        if (busy(c)) return null;
        Result before = read(c, e.id);
        Source s = e.source;
        long answer = -1;
        boolean full = false;
        try {
            String[] x = Source.XTREAM.equals(s.type) ? new String[]{Xtream.base(s.server), s.user, s.pass} : Sources.xtreamOf(s.m3uUrl);
            long t0 = System.nanoTime();
            if (x != null && !x[0].isEmpty()) {
                // The account's own answer, with its connections now.
                String body = read(Fetch.open(Xtream.api(x[0], x[1], x[2], null), null), 64 * 1024);
                answer = (System.nanoTime() - t0) / 1_000_000L;
                JSONObject ui = new JSONObject(body).optJSONObject("user_info");
                if (ui != null) {
                    Sources.note(c, e.id, ui);
                    int max = parse(ui.optString("max_connections", "0")), active = parse(ui.optString("active_cons", "0"));
                    full = max > 0 && active >= max;
                }
            } else {
                read(Fetch.open(s.m3uUrl, null), 64 * 1024);
                answer = (System.nanoTime() - t0) / 1_000_000L;
            }
        } catch (IOException | JSONException | RuntimeException ignored) {
            // no answer
        }
        int sampled = 0, failed = 0;
        List<Long> starts = new ArrayList<>();
        if (answer >= 0 && !full) {
            for (Channel ch : samples(c, e.id)) {
                if (busy(c)) return null;
                long ms = firstBytes(ch);
                sampled++;
                if (ms < 0) failed++;
                else starts.add(ms);
            }
        }
        long start = -1;
        if (!starts.isEmpty()) {
            Collections.sort(starts);
            start = starts.get(starts.size() / 2);
        }
        Result r = new Result(answer, start, sampled, failed, System.currentTimeMillis());
        try {
            prefs(c).edit().putString("health." + e.id, r.json().toString()).apply();
        } catch (JSONException ignored) {
            // not kept
        }
        if (STRUGGLING.equals(r.verdict) && (before == null || !STRUGGLING.equals(before.verdict))) tell(c, e, r);
        return r;
    }

    /** Up to SAMPLES of the playlist's live channels: the ones watched lately first, then spread through the list. */
    static List<Channel> samples(Context c, String id) {
        Library lib = Library.get(c);
        lib.ensureNow();
        List<Channel> mine = new ArrayList<>();
        for (Channel main : lib.list(Library.ALL, "")) {
            if (Library.playlistOf(main.key).equals(id)) mine.add(main);
            for (Channel alt : main.alternates) if (Library.playlistOf(alt.key).equals(id)) mine.add(alt);
        }
        Set<Channel> out = new LinkedHashSet<>();
        for (String k : Picks.recent(c)) {
            if (out.size() >= 2) break;
            for (Channel ch : mine) if (ch.key.equals(k)) out.add(ch);
        }
        int n = mine.size();
        for (int i = 0; i < SAMPLES && n > 0 && out.size() < SAMPLES; i++) out.add(mine.get((int) ((long) n * (2 * i + 1) / (2 * SAMPLES))));
        return new ArrayList<>(out);
    }

    /**
     * How long a channel takes to give its first picture bytes (ms), read for about two seconds then closed; -1 when
     * it fails. An HLS channel's playlist is followed to its newest segment.
     */
    static long firstBytes(Channel ch) {
        long t0 = System.nanoTime();
        String url = ch.url;
        try {
            for (int hop = 0; hop < 3; hop++) {
                HttpURLConnection h = open(url, ch);
                try {
                    int code = h.getResponseCode();
                    if (code >= 400) return -1;
                    String type = h.getContentType() == null ? "" : h.getContentType().toLowerCase(Locale.ROOT);
                    InputStream in = h.getInputStream();
                    if (url.contains(".m3u8") || type.contains("mpegurl")) {
                        String next = segment(url, read(in, 256 * 1024));
                        if (next == null) return -1;
                        url = next;
                        continue;
                    }
                    byte[] buf = new byte[16 * 1024];
                    int n = in.read(buf);
                    if (n <= 0) return -1;
                    long first = (System.nanoTime() - t0) / 1_000_000L;
                    long until = System.nanoTime() + SAMPLE_MS * 1_000_000L;
                    long got = n;
                    while (System.nanoTime() < until && got < 2_000_000L && (n = in.read(buf)) > 0) got += n;
                    return first;
                } finally {
                    h.disconnect();
                }
            }
        } catch (IOException | RuntimeException e) {
            return -1;
        }
        return -1;
    }

    private static HttpURLConnection open(String url, Channel ch) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout((int) TIMEOUT_MS);
        h.setReadTimeout((int) TIMEOUT_MS);
        h.setInstanceFollowRedirects(true);
        h.setRequestProperty("User-Agent", ch.userAgent == null || ch.userAgent.isEmpty() ? Fetch.AGENT : ch.userAgent);
        if (ch.referrer != null && !ch.referrer.isEmpty()) h.setRequestProperty("Referer", ch.referrer);
        return h;
    }

    /** From an HLS playlist: its first variant (a master playlist) or its newest segment, as a full address. Pure. */
    static String segment(String base, String playlist) {
        String last = null;
        boolean variant = false;
        for (String line : playlist.split("\n")) {
            String l = line.trim();
            if (l.startsWith("#EXT-X-STREAM-INF")) variant = true;
            if (l.isEmpty() || l.startsWith("#")) continue;
            if (variant) return resolve(base, l);
            last = l;
        }
        return last == null ? null : resolve(base, last);
    }

    static String resolve(String base, String ref) {
        try {
            return new URL(new URL(base), ref).toString();
        } catch (IOException e) {
            return ref;
        }
    }

    private static String read(InputStream in, int max) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder b = new StringBuilder();
            char[] buf = new char[8192];
            for (int n; b.length() < max && (n = r.read(buf)) > 0; ) b.append(buf, 0, n);
            return b.toString();
        }
    }

    private static int parse(String v) {
        try {
            return Integer.parseInt(v.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** "1.2 s", "0.4 s", "—". Pure. */
    static String seconds(long ms) {
        if (ms < 0) return "—";
        return String.format(Locale.UK, "%.1f s", ms / 1000.0);
    }

    /** "today", "yesterday", "3 days ago". Pure. */
    static String ago(long at, long now) {
        long days = (now - at) / 86_400_000L;
        if (days <= 0) return "today";
        if (days == 1) return "yesterday";
        return days + " days ago";
    }

    private static void tell(Context c, Sources.Entry e, Result r) {
        tell(c, e.id, e.name, r);
    }

    /**
     * A test of the warning (TestAlerts): a made-up playlist struggling (six of ten channels tried didn't play), under
     * its own id; nothing is kept, so a real check and its warning go on as before.
     */
    public static void test(Context c) {
        tell(c, "TEST|health", c.getString(R.string.live7_test_playlist), new Result(900, 4200, 10, 6, System.currentTimeMillis()));
    }

    private static void tell(Context c, String id, String name, Result r) {
        Expiry.channel(c);
        String text = r.sampled > 0 ? c.getString(R.string.f2_health_bad_text, r.failed, r.sampled, seconds(r.startMs))
                : c.getString(R.string.f2_health_bad_answer);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, Expiry.CHANNEL)
                .setSmallIcon(R.drawable.live_ic_guide)
                .setContentTitle(c.getString(R.string.f2_health_bad_title, name))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setColor(0xFFFF4FA3)
                .setAutoCancel(true)
                .setContentIntent(Expiry.settings(c));
        try {
            NotificationManagerCompat.from(c).notify(("health:" + id).hashCode(), b.build());
        } catch (SecurityException ignored) {
            // notifications not allowed on this phone
        }
    }
}
