package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Sleep after this episode (theme "sleep_prompt", on unless set false): past bedtime (the theme's "bedtime"), while an
 * episode plays in any app, a black card asks once a night; OK says yes. Then, watched every two seconds through the
 * app's media session, the app is paused as soon as the next episode starts (or this one ends) and the TV goes to
 * sleep (the key helper's lock, else the TV's own debugging connection). Fed by WatchHistory's tick.
 */
final class SleepAfter {
    private SleepAfter() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final java.util.regex.Pattern EPISODE = java.util.regex.Pattern.compile("(?i)\\b(s\\d+\\s*:?\\s*e\\d+|episode \\d+)\\b");
    private static String pkg, title, sub;
    private static long until;
    private static WatchHistory svc;

    /** Every half minute from WatchHistory: past bedtime, an episode playing, not asked tonight: ask. */
    static void tick(WatchHistory s, List<MediaController> controllers) {
        svc = s;
        if (pkg != null || !Config.themeFlag(s, "sleep_prompt", true)) return;
        int now = LocalTime.now().toSecondOfDay() / 60, bed = FitsTonight.bedMinutes(s);
        if (now < 5 * 60) now += 24 * 60;
        if (now < bed || now > bed + 5 * 60) return;
        SharedPreferences p = s.getSharedPreferences("sleep", Context.MODE_PRIVATE);
        String night = LocalDate.now().minusDays(LocalTime.now().getHour() < 5 ? 1 : 0).toString();
        if (night.equals(p.getString("asked", ""))) return;
        for (MediaController c : controllers) {
            PlaybackState st = c.getPlaybackState();
            MediaMetadata md = c.getMetadata();
            if (st == null || md == null || st.getState() != PlaybackState.STATE_PLAYING || ViewingLog.live(c.getPackageName())) continue;
            String t = text(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String u = text(md, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST);
            Object[] id = ViewingLog.identify(s, c.getPackageName(), t, u);
            java.util.regex.Matcher m = EPISODE.matcher(t + " " + u);
            String episode = id[4] != null ? (String) id[4] : m.find() ? m.group(1) : null;
            if (episode == null) continue;   // not an episode
            long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION), pos = position(st);
            long left = dur - pos;
            if (dur <= 0 || left < 3 * 60_000L || left > 100 * 60_000L) continue;
            p.edit().putString("asked", night).apply();
            ask(s, c.getPackageName(), t, u, id[1] != null ? (String) id[1] : t, episode, left);
            return;
        }
    }

    private static void ask(Context c, String app, String t, String u, String name, String episode, long left) {
        long end = System.currentTimeMillis() + left;
        LocalTime at = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        ScoreCard.Data d = new ScoreCard.Data();
        d.dark = true;
        d.kind = "release";
        d.band = 0xFF141519;
        d.bandDeep = 0xFF000000;
        d.labelColour = 0xFF6E7BFF;
        // As the mock-up: the time and why it asks, the question, then what happens and when the TV goes off.
        LocalTime now = LocalTime.now();
        LocalTime off = at.plusMinutes(1);
        d.label = String.format(Locale.UK, "%02d:%02d", now.getHour(), now.getMinute()) + " · PAST YOUR BEDTIME";
        d.headline = "Sleep after this episode?";
        d.line = (name == null ? "" : name + " ") + episode.replaceAll("S(\\d+)E(\\d+)", "S$1 E$2") + " ends "
                + String.format(Locale.UK, "%02d:%02d", at.getHour(), at.getMinute()) + " · " + label(c, app)
                + " stops, TV off " + String.format(Locale.UK, "%02d:%02d", off.getHour(), off.getMinute());
        d.okWords = "yes, after this one";
        d.channel = "Sleep after it";
        d.watch = () -> arm(c, app, t, u, left);
        MAIN.post(() -> ScoreCard.show(c, d));
    }

    private static void arm(Context c, String app, String t, String u, long left) {
        pkg = app;
        title = t;
        sub = u;
        until = SystemClock.elapsedRealtime() + left + 15 * 60_000L;
        Toast.makeText(c.getApplicationContext(), "The TV goes off after this episode", Toast.LENGTH_LONG).show();
        MAIN.removeCallbacks(WATCH);
        MAIN.postDelayed(WATCH, 2_000L);
    }

    private static final Runnable WATCH = new Runnable() {
        @Override public void run() {
            if (pkg == null || svc == null) return;
            if (SystemClock.elapsedRealtime() > until) { pkg = null; return; }
            MediaController c = null;
            for (MediaController x : svc.controllers()) if (x.getPackageName().equals(pkg)) c = x;
            if (c == null) { MAIN.postDelayed(this, 2_000L); return; }
            PlaybackState st = c.getPlaybackState();
            MediaMetadata md = c.getMetadata();
            String t = md == null ? "" : text(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String u = md == null ? "" : text(md, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, MediaMetadata.METADATA_KEY_ARTIST);
            long dur = md == null ? 0 : md.getLong(MediaMetadata.METADATA_KEY_DURATION), pos = st == null ? 0 : position(st);
            boolean next = !t.isEmpty() && (!t.equals(title) || !u.equals(sub));
            boolean ended = dur > 0 && pos >= dur - 2_000L;
            if (next || ended) {
                MediaController done = c;
                pkg = null;
                try {
                    done.getTransportControls().pause();
                } catch (RuntimeException ignored) {
                    // the TV goes off anyway
                }
                MAIN.postDelayed(() -> sleep(svc), 1_500L);
                return;
            }
            MAIN.postDelayed(this, 2_000L);
        }
    };

    /** The TV to sleep: the key helper's lock (as the power button does), else the TV's own debugging connection. */
    static void sleep(Context c) {
        if (!AppSearchService.sleepScreen()) LocalAdb.shell(c, "input keyevent KEYCODE_SLEEP");
    }

    private static long position(PlaybackState st) {
        return st.getPosition() + (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
    }

    private static String text(MediaMetadata md, String a, String b) {
        String s = md.getString(a);
        if (s == null || s.trim().isEmpty()) s = md.getString(b);
        return s == null ? "" : s.trim();
    }

    private static String label(Context c, String app) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(app, 0)).toString();
        } catch (Exception e) {
            return "The app";
        }
    }
}
