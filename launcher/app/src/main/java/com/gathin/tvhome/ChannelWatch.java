package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two helps while a match plays in TiviMate under the scoreboard (BoardCover's tick, each second):
 *
 * <ul>
 *   <li>The backup channel: TiviMate's sound stops as its picture freezes or it buffers (the channel tuning already
 *   counts on that: the sound goes while a new channel loads). Silent for FREEZE_MS after it had played for a while,
 *   and not paused by hand (its media session, where it has one, says PAUSED then), a card offers the same match on
 *   another channel the playlist has, OK to switch.</li>
 *   <li>Also in 4K: on a channel whose UHD or 4K form the playlist has too, a chip under the logo's cover says so
 *   for a few seconds, once a channel; OK while it shows switches to it (BoardCover shows it and takes the OK).</li>
 * </ul>
 */
final class ChannelWatch {
    private ChannelWatch() { }

    static final String TIVIMATE = "ar.tvplayer.tv";
    /** Silent this long (a frozen or buffering picture) before the backup is offered: as asked, ten seconds. */
    static final long FREEZE_MS = 10_000L;
    /** It must have played this long first (a channel just tuned is still loading). */
    static final long PLAYED_MS = 20_000L;
    /** One offer at most this often. */
    static final long AGAIN_MS = 5 * 60_000L;

    private static Handler main;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }
    private static ExecutorService io;

    private static ExecutorService io() {
        if (io == null) io = Executors.newSingleThreadExecutor();
        return io;
    }
    private static long soundSince, silentSince, offeredAt;
    private static boolean offering;
    private static String fourKFor;

    // ---- the switches ----------------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("channel_watch", Context.MODE_PRIVATE);
    }

    /** The backup channel offered when the picture stops (on unless switched off on the Scoreboard card). */
    static boolean backupOn(Context c) {
        return prefs(c).getBoolean("backup", true);
    }

    static void setBackupOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("backup", on).apply();
    }

    /** The "Also in 4K" chip on the logo's cover (on unless switched off). */
    static boolean fourKOn(Context c) {
        return prefs(c).getBoolean("4k", true);
    }

    static void setFourKOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("4k", on).apply();
    }

    // ---- each second, with the match on (main thread) ----------------------------------------------------------------

    /** One look while the board is up for g's live match; the 4K channel for the chip, when it should show now (else null). */
    static String tick(Context c, ScoresTicker.Game g) {
        String front = AppSearchService.front();
        if (!TIVIMATE.equals(front) || AppSearchService.tuning()) {
            soundSince = 0;
            silentSince = 0;
            return null;
        }
        if (backupOn(c)) freezeCheck(c, g);
        if (!fourKOn(c)) return null;
        String on = InStep.channel(c);
        if (on.isEmpty() || on.equals(fourKFor)) return null;
        fourKFor = on;
        return uhdFor(on, HomeV2.guideChannels(c));
    }

    private static void freezeCheck(Context c, ScoresTicker.Game g) {
        long now = SystemClock.elapsedRealtime();
        AudioManager am = c.getSystemService(AudioManager.class);
        boolean playing = am != null && am.isMusicActive();
        if (playing) {
            if (soundSince == 0) soundSince = now;
            silentSince = 0;
            return;
        }
        if (silentSince == 0) silentSince = now;
        if (!frozen(soundSince == 0 ? 0 : silentSince - soundSince, now - silentSince)) return;
        if (offering || now - offeredAt < AGAIN_MS && offeredAt != 0) return;
        if (pausedByHand(c)) {
            silentSince = now;
            return;
        }
        offering = true;
        offeredAt = now;
        String on = InStep.channel(c);
        Context app = c.getApplicationContext();
        io().execute(() -> {
            List<String> alt = new ArrayList<>();
            try {
                TvGuide.Watch w = TvGuide.watch(app, app.getResources().getStringArray(R.array.football_tv_channels), g);
                alt = alternatives(on, w == null ? Collections.emptyList() : w.names, HomeV2.guideChannels(app));
            } catch (RuntimeException ignored) {
                // none found
            }
            List<String> names = alt;
            main().post(() -> {
                offering = false;
                if (names.isEmpty()) {
                    AlertLog.note(app, "picture stopped on " + (on.isEmpty() ? "TiviMate" : on) + ": no other channel has the match");
                    return;
                }
                offer(app, g, on, names);
            });
        });
    }

    /** Pure: whether the sound has been gone long enough, after playing long enough, to call the picture frozen. */
    static boolean frozen(long playedFor, long silentFor) {
        return playedFor >= PLAYED_MS && silentFor >= FREEZE_MS;
    }

    /** TiviMate paused by hand (its media session says so); false when it has none or it cannot be read. */
    private static boolean pausedByHand(Context c) {
        try {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            if (m == null) return false;
            for (MediaController mc : m.getActiveSessions(new ComponentName(c, WatchHistory.class))) {
                if (!TIVIMATE.equals(mc.getPackageName())) continue;
                PlaybackState s = mc.getPlaybackState();
                return s != null && s.getState() == PlaybackState.STATE_PAUSED;
            }
        } catch (RuntimeException ignored) {
            // no access to the sessions
        }
        return false;
    }

    /** The card: the picture stopped, the same match on another channel, OK to switch (over the app, the board kept). */
    private static void offer(Context c, ScoresTicker.Game g, String on, List<String> names) {
        ScoreCard.Data d = new ScoreCard.Data();
        d.dark = true;
        d.kind = "backup";
        // The TV's own offer about what is on: never kept back by a switch or Match mode's quiet. Its game named, but no
        // home side, so the board never takes it in (the OK must reach the card).
        d.notify = NotifyKinds.ALWAYS;
        d.gameId = g.id;
        d.slug = g.slug;
        d.band = 0xFF10157E;
        d.bandDeep = 0xFF060928;
        d.labelColour = 0xFFFFC94A;
        d.label = "PICTURE STOPPED";
        d.headline = "The same match on " + names.get(0);
        d.line = (on.isEmpty() ? "Frozen" : on + " frozen") + " for 10 seconds" + (names.size() > 1 ? " · " + (names.size() - 1) + " more to try" : "");
        d.channel = names.get(0);
        d.okWords = "to switch";
        d.watch = () -> ChannelOpen.open(c, TIVIMATE, names, null);
        d.showMs = 20_000L;
        AlertLog.note(c, "picture stopped on " + (on.isEmpty() ? "TiviMate" : on) + ": offered " + names.get(0));
        ScoreCard.show(c, d);
    }

    // ---- the channels ------------------------------------------------------------------------------------------------

    /**
     * Pure: the playlist's other channels with the match on, to try in turn: the channel on now in its other forms
     * (its HD for its UHD, and back) first, then the match's other channels; each the playlist's own name, the one
     * on now left out. Without a playlist list, the match's channels by their names.
     */
    static List<String> alternatives(String on, List<String> gameChannels, List<String[]> guide) {
        List<String> out = new ArrayList<>();
        String onKey = InStep.key(on), onBase = InStep.base(on);
        List<String> wanted = new ArrayList<>();
        if (!onBase.isEmpty()) wanted.add(onBase);
        for (String g : gameChannels) {
            String b = InStep.base(g);
            if (!b.isEmpty() && !wanted.contains(b)) wanted.add(b);
        }
        if (guide == null || guide.isEmpty()) {
            for (String g : gameChannels) if (!InStep.key(g).equals(onKey) && !out.contains(g)) out.add(g);
            return out;
        }
        for (String b : wanted) {
            for (String[] ch : guide) {
                if (ch.length < 2) continue;
                String name = ch[1].trim();
                if (InStep.base(name).equals(b) && !InStep.key(name).equals(onKey) && !out.contains(name)) out.add(name);
            }
        }
        return out;
    }

    /** Pure: the channel's UHD or 4K form in the playlist, when the one on is not that already; null otherwise. */
    static String uhdFor(String on, List<String[]> guide) {
        if (on == null || on.trim().isEmpty() || fourK(on) || guide == null) return null;
        String b = InStep.base(on);
        for (String[] ch : guide) {
            if (ch.length < 2) continue;
            String name = ch[1].trim();
            if (fourK(name) && InStep.base(name).equals(b)) return name;
        }
        return null;
    }

    /** Pure: a channel's name says UHD or 4K. */
    static boolean fourK(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).matches(".*\\b(uhd|4k)\\b.*");
    }

    /** OK on the chip: the 4K channel tuned in TiviMate. */
    static void toFourK(Context c, String name) {
        AlertLog.note(c, "Also in 4K: switching to " + name);
        ChannelOpen.open(c, TIVIMATE, Collections.singletonList(name), null);
    }
}
