package com.gathin.tvhome;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cinema mode (the theme's cinema_mode, with Tuya's lights set up): a film starts playing in any app (70 minutes or
 * more, not live TV or YouTube) and the room's lights that are on dim (cinema_level, 10%); paused, stopped, or into
 * the credits (the last 6%, at least four minutes), they come back up to where they were. The lights are the TV's own
 * (tuya_lights), narrowed by cinema_lights when set. Watched through the media sessions (WatchHistory), each change
 * acted on after a moment so a quick pause or a seek does not flash the room.
 */
final class Cinema {
    private Cinema() { }

    private static final long FILM_MS = 70 * 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Map<MediaController, MediaController.Callback> watched = new HashMap<>();
    /** The lights dimmed and the level each was at: {light, level before (-1: full)}. */
    private static final List<Object[]> saved = new ArrayList<>();
    private static boolean dimmed, wantDim;
    private static Context app;
    /** When the lights last came back up (paused, stopped or the credits): the pause card says so. 0: not yet. */
    static volatile long raisedAt;

    static boolean on(Context c) {
        return Config.themeFlag(c, "cinema_mode", false) && TuyaCloud.configured(c);
    }

    /** The media sessions now (WatchHistory, each time they change): each one watched for playing and pausing. */
    static void sessions(Context c, List<MediaController> list) {
        if (!on(c)) return;
        app = c.getApplicationContext();
        List<MediaController> gone = new ArrayList<>(watched.keySet());
        for (MediaController mc : list) {
            MediaController known = null;
            for (MediaController w : watched.keySet()) if (w.getSessionToken().equals(mc.getSessionToken())) known = w;
            if (known != null) { gone.remove(known); continue; }
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onPlaybackStateChanged(PlaybackState state) { check(); }
                @Override public void onMetadataChanged(MediaMetadata metadata) { check(); }
                @Override public void onSessionDestroyed() { check(); }
            };
            try {
                mc.registerCallback(cb, MAIN);
                watched.put(mc, cb);
            } catch (RuntimeException ignored) {
                // a session that went as it came
            }
        }
        for (MediaController g : gone) {
            try { g.unregisterCallback(watched.remove(g)); } catch (RuntimeException ignored) { watched.remove(g); }
        }
        check();
    }

    /** Every half minute too (WatchHistory's tick): the credits found by the position. */
    static void tick(Context c, List<MediaController> list) {
        if (!on(c)) return;
        app = c.getApplicationContext();
        check();
    }

    private static long position(PlaybackState st) {
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING && st.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        return pos;
    }

    private static final Runnable CHECK = Cinema::check;
    private static final Runnable APPLY = Cinema::apply;

    /** A film playing and not yet in its credits: the lights down; otherwise up (each after a moment). */
    private static void check() {
        if (app == null) return;
        boolean film = false;
        long toCredits = -1;
        for (MediaController mc : watched.keySet()) {
            String pkg = mc.getPackageName();
            if (ViewingLog.live(pkg) || pkg.contains("youtube") || pkg.contains("com.gathin.tvhome") || pkg.contains("music")) continue;
            PlaybackState st;
            MediaMetadata md;
            try {
                st = mc.getPlaybackState();
                md = mc.getMetadata();
            } catch (RuntimeException e) {
                continue;
            }
            if (st == null || md == null || st.getState() != PlaybackState.STATE_PLAYING) continue;
            long dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (dur < FILM_MS) continue;
            long credits = dur - Math.max(4 * 60_000L, (long) (dur * 0.06));
            long pos = position(st);
            if (pos >= credits) continue;
            film = true;
            float speed = st.getPlaybackSpeed() > 0 ? st.getPlaybackSpeed() : 1f;
            toCredits = (long) ((credits - pos) / speed);
        }
        MAIN.removeCallbacks(CHECK);
        // Looked at again just as the credits start.
        if (toCredits > 0) MAIN.postDelayed(CHECK, Math.min(toCredits + 1_000L, 30 * 60_000L));
        if (film == wantDim) return;
        wantDim = film;
        MAIN.removeCallbacks(APPLY);
        MAIN.postDelayed(APPLY, film ? 3_000L : 2_500L);
    }

    private static void apply() {
        if (wantDim == dimmed || app == null) return;
        boolean down = wantDim;
        dimmed = down;
        Context c = app;
        IO.execute(() -> {
            if (down) dim(c); else restore(c);
        });
    }

    private static void dim(Context c) {
        int level = 10;
        try {
            level = Math.max(1, Math.min(100, Integer.parseInt(Config.themeString(c, "cinema_level", "10").trim())));
        } catch (NumberFormatException ignored) {
            // ten per cent
        }
        List<String> only = Config.themeStrings(c, "cinema_lights", new ArrayList<>());
        List<TuyaCloud.Light> lights;
        try {
            lights = TuyaCloud.lights(c);
        } catch (Exception e) {
            AlertLog.note(c, "cinema mode: the lights could not be read (" + e.getMessage() + ")");
            return;
        }
        synchronized (saved) {
            saved.clear();
            for (TuyaCloud.Light l : lights) {
                if (!l.online || !l.on || l.brightCode == null) continue;
                if (!only.isEmpty() && !only.contains(l.name) && !only.contains(l.id)) continue;
                if (l.level >= 0 && l.level <= level) continue;   // already as low
                saved.add(new Object[]{l, l.level});
                TuyaCloud.level(c, l, level);
            }
        }
        AlertLog.note(c, "cinema mode: a film started, " + saved.size() + " light(s) dimmed to " + level + "%");
    }

    private static void restore(Context c) {
        int n;
        synchronized (saved) {
            n = saved.size();
            for (Object[] s : saved) {
                TuyaCloud.Light l = (TuyaCloud.Light) s[0];
                int before = (Integer) s[1];
                TuyaCloud.level(c, l, before < 0 ? 100 : before);
            }
            saved.clear();
        }
        if (n > 0) {
            raisedAt = System.currentTimeMillis();
            AlertLog.note(c, "cinema mode: paused or the credits, " + n + " light(s) back up");
        }
    }
}
