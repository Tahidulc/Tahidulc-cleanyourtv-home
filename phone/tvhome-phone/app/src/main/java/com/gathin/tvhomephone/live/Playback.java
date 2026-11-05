package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * Live TV's one player, shared by the guide's own picture and the full screen: one stream at a time (many providers
 * allow no more), handed between them as it plays, so going full screen and back doesn't stop or rebuffer it. Whoever
 * shows the picture is the front; the guide waits behind the full screen as the standby and takes the stream back when
 * the full screen hands it back. A dropped stream is tried again a few times (sooner, then later), an address that hides
 * HLS is tried as HLS, the phone's audio focus is held while it plays and it pauses when headphones come out. The last
 * channel is remembered on this phone, for the guide to offer again (paused until tapped). Main thread only. The stream's
 * address is never logged (it can hold the login).
 */
@OptIn(markerClass = UnstableApi.class)
final class Playback {
    static final int RETRIES = 5;
    /** What the picture is doing: nothing open, opening (or buffering), playing, waiting to try again, given up. */
    static final int IDLE = 0, LOADING = 1, PLAYING = 2, RECONNECTING = 3, FAILED = 4;
    /** How long a stream plays on with no one showing it (a turn of the screen, a hand back) before it is let go. */
    private static final long ORPHAN_MS = 3000;

    /** Somewhere that shows the picture: the guide's inline player or the full screen. */
    interface Host {
        PlayerView view();

        /** The full screen (true), or the guide's picture. */
        boolean full();

        /** The state changed, the channel changed (read it from channel()), or this host just took the picture. */
        void onState(int state);

        default void onFirstFrame() {}

        default void onVideoSize(@NonNull VideoSize size) {}

        /** The guide took the stream from this full screen (in picture in picture): it closes. */
        default void onTakenOver() {}

        /** Something to say in a line (the quality stepped down, 4K held back on mobile data). */
        default void onNotice(String words) {}

        /** The rewind's replay began (true) or went back to live. */
        default void onReplay(boolean replaying) {}
    }

    @SuppressLint("StaticFieldLeak") // the application's context only
    private static Playback instance;

    private final Context app;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private Channel cur;
    /** Which of the channel's streams plays: 0 its own, then its backups from the other playlists (Channel.alternates). */
    private int alt;
    /** The channel's versions in each quality, best first (Library.variants), the one playing, the best it may go to. */
    private java.util.List<Channel> rungs = new java.util.ArrayList<>();
    private int rung, top;
    /** 4K let through on mobile data for this channel (asked for in the options). */
    private boolean fourK;
    /** Adaptive's stalls (their starts, newest last), the one going on, failed tries up, a try up under way. */
    private final long[] stalls = new long[6];
    private int stallCount, failedUps;
    private long bufferingSince, upTriedAt, preparedAt, lastStartMs = -1;
    /** The channel before this one (for flipping back). */
    private Channel previous;
    /** The rewind's replay: on, from when (wall clock), what it plays. */
    private boolean replaying;
    private long replayStart;
    private Timeshift.Snapshot replaySnap;
    private final Runnable stallCheck = this::stallCheck;
    private final Runnable upCheck = this::tryUp;
    private int state = IDLE, retries;
    private boolean asHls, firstFrame;
    private Host front, standby;
    private final Runnable retry = this::prepare;
    private final Runnable orphan = () -> {
        if (front == null) stop();
    };

    private Playback(Context app) {
        this.app = app;
    }

    static synchronized Playback get(Context c) {
        if (instance == null) instance = new Playback(c.getApplicationContext());
        return instance;
    }

    ExoPlayer player() { return player; }

    /** What is (or was last) open; null before anything has played this run. */
    Channel channel() { return cur; }

    int state() { return state; }

    boolean firstFrame() { return firstFrame; }

    /** Open but held (the phone's audio went to another app, or the headphones came out): a tap plays on. */
    boolean paused() { return live() && !player.getPlayWhenReady(); }

    void resume() {
        if (player != null) player.play();
    }

    /** A stream is open on the channel: playing, opening or about to try again (not stopped or given up). */
    boolean live() {
        return player != null && cur != null && (state == LOADING || state == PLAYING || state == RECONNECTING);
    }

    boolean live(Channel ch) {
        return ch != null && live() && cur.key.equals(ch.key);
    }

    boolean isFront(Host host) { return host != null && front == host; }

    boolean fullScreen() { return front != null && front.full(); }

    /** Nobody shows the picture just now (between the guide turning round and coming back). */
    boolean unattended() { return front == null; }

    // ---- who shows it ---------------------------------------------------------------------------------------

    /** A host comes on screen: it takes the picture, except the guide behind the full screen, which waits. */
    void offer(Host host) {
        if (front == host) return;
        if (fullScreen() && !host.full()) {
            standby = host;
            host.onState(IDLE);
            return;
        }
        take(host);
    }

    /** The host shows the picture from now; the full screen it is taken from closes, the guide it is taken from waits. */
    void take(Host host) {
        move(host, true);
    }

    /** The picture straight from the front's view to the host's (no gap without a surface); told: the old front hears. */
    private void move(Host host, boolean told) {
        h.removeCallbacks(orphan);
        Host old = front;
        if (old == host) return;
        front = host;
        if (standby == host) standby = null;
        if (old != null && told) {
            if (old.full() && !host.full()) old.onTakenOver();
            else if (!old.full() && host.full()) standby = old;
        }
        if (player != null) PlayerView.switchTargetView(player, old == null ? null : old.view(), host.view());
        host.onState(state);
        if (firstFrame) host.onFirstFrame();
    }

    /**
     * The host goes (its screen stops or closes). keep: the stream plays on for whoever comes next (the guide waiting
     * behind, or the guide again after turning round), and is let go if no one does; otherwise it stops at once.
     */
    void leave(Host host, boolean keep) {
        if (host == null) return;
        if (standby == host) {
            standby = null;
            return;
        }
        if (front != host) return;
        Host next = standby;
        standby = null;
        if (!keep) stop();
        if (next != null) {
            // The guide waiting behind takes it, from this view straight to its own.
            move(next, false);
            return;
        }
        front = null;
        host.view().setPlayer(null);
        if (keep && player != null) h.postDelayed(orphan, ORPHAN_MS);
    }

    // ---- playing --------------------------------------------------------------------------------------------

    /** Plays a channel afresh (its retries, the HLS guess and its tracks); the same channel already open carries on. */
    void play(Channel ch) {
        if (ch == null || live(ch)) return;
        h.removeCallbacks(retry);
        if (cur != null && !cur.key.equals(ch.key)) previous = cur;
        cur = ch;
        fourK = false;
        failedUps = 0;
        replaying = false;
        ladder();
        retries = 0;
        firstFrame = false;
        ensure();
        // Another channel's audio, subtitle or quality choice means nothing here (subtitles off stays off).
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().build());
        remember(ch);
        Picks.watched(app, ch.key);
        prepare();
    }

    /** The versions of the channel, and the one to start on as the quality settings and the network say. */
    private void ladder() {
        rungs = Library.get(app).variants(cur);
        int[] levels = new int[rungs.size()];
        for (int i = 0; i < levels.length; i++) levels[i] = Library.level(rungs.get(i));
        int chosen = Math.max(0, rungs.indexOf(cur));
        int cap = Quality.cap(app, fourK);
        rung = top = Math.max(0, Quality.start(levels, chosen, Quality.choice(app), cap));
        stallCount = 0;
        bufferingSince = 0;
        upTriedAt = 0;
        h.removeCallbacks(upCheck);
        alt = bestAlt(base());
        asHls = Streams.looksHls(stream());
        if (levels.length > 0 && levels[chosen] >= 4 && levels[rung] < 4 && Quality.metered(app) && !fourK) {
            notice("On mobile data: playing in " + Quality.label(levels[rung]) + ". Choose 4K in the options to play it in 4K.");
        }
    }

    /** The quality settings changed (or 4K was asked for): the channel again at the version they now say. */
    void requality(boolean allow4k) {
        if (cur == null) return;
        fourK = allow4k;
        Channel was = stream();
        ladder();
        if (stream() != was && live()) {
            retries = 0;
            prepare();
        } else {
            applyCaps();
        }
    }

    /** The channel before the current one, still in the playlist (null when none). */
    Channel previous() {
        return previous;
    }

    /** What plays, in a line: its playlist, its picture, how fast it started ("Main · 1080p · started in 1.1 s"). */
    String sourceLine() {
        if (cur == null) return "";
        Channel s = stream();
        Sources.Entry e = Sources.get(app, Library.playlistOf(s.key));
        String line = (e == null ? "" : e.name + " · ") + Quality.label(Library.level(s));
        if (lastStartMs >= 0) line += String.format(java.util.Locale.UK, " · started in %.1f s", lastStartMs / 1000.0);
        if (rungs.size() > 1) line += " · " + (Quality.choice(app) == Quality.ADAPTIVE ? "adaptive" : "fixed");
        return line;
    }

    private void notice(String words) {
        if (front != null) front.onNotice(words);
    }

    /** The stream again from the start (after it stalls or gives up, or to catch up with live). */
    void restart() {
        if (cur == null) return;
        h.removeCallbacks(retry);
        // Tried again after it gave up: from its own stream, then its backups once more.
        if (state == FAILED && alt > 0) {
            alt = 0;
            asHls = Streams.looksHls(stream());
        }
        replaying = false;
        retries = 0;
        firstFrame = false;
        ensure();
        prepare();
    }

    /** Stops and lets the stream go; the channel stays the last one. */
    void stop() {
        h.removeCallbacks(retry);
        h.removeCallbacks(orphan);
        h.removeCallbacks(stallCheck);
        h.removeCallbacks(upCheck);
        Timeshift.get(app).pause();
        if (replaying && front != null) front.onReplay(false);
        replaying = false;
        if (player != null) {
            player.removeListener(listener);
            if (front != null) front.view().setPlayer(null);
            player.release();
            player = null;
        }
        firstFrame = false;
        set(IDLE);
    }

    private void ensure() {
        if (player != null) return;
        // The phone's own decoders first; FFmpeg for the sound they can't play (AC-3, E-AC-3, MP2, DTS), which UK
        // channels often carry: without it those channels played with no sound at all.
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(app)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
        // A channel starts as soon as a second is in (two after a stall), not the usual two and a half (five).
        DefaultLoadControl load = new DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_000, 2_000).build();
        player = new ExoPlayer.Builder(app, renderers).setLoadControl(load).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(listener);
        NightSound.follow(app, player);
        if (front != null) front.view().setPlayer(player);
    }

    private void prepare() {
        if (player == null || cur == null) return;
        if (replaying && front != null) front.onReplay(false);
        replaying = false;
        h.removeCallbacks(stallCheck);
        bufferingSince = 0;
        Channel s = stream();
        Timeshift ts = Timeshift.get(app);
        boolean keep = ts.begin(s.key);
        applyCaps();
        player.setMediaSource(Streams.source(s, asHls, keep ? ts.sink() : null));
        preparedAt = android.os.SystemClock.elapsedRealtime();
        lastStartMs = -1;
        player.prepare();
        player.setPlayWhenReady(true);
        set(LOADING);
    }

    /** An HLS stream's own versions held to the quality allowed: fixed picks the best under it and stays there. */
    private void applyCaps() {
        if (player == null) return;
        int cap = Quality.cap(app, fourK);
        TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon()
                .setMaxVideoSize(Integer.MAX_VALUE, Quality.maxHeight(cap))
                .setForceHighestSupportedBitrate(Quality.choice(app) != Quality.ADAPTIVE);
        player.setTrackSelectionParameters(b.build());
    }

    private void set(int s) {
        state = s;
        if (front != null) front.onState(s);
    }

    /** The version of the channel at the quality playing (the channel itself when it has no other). */
    private Channel base() {
        return rung >= 0 && rung < rungs.size() ? rungs.get(rung) : cur;
    }

    /** The stream playing: that version's own, or one of its backups from another playlist. */
    Channel stream() {
        Channel b = base();
        if (b == null) return null;
        return alt > 0 && alt <= b.alternates.size() ? b.alternates.get(alt - 1) : b;
    }

    /** Which copy of a version to start on: another playlist's when it has started faster and stalled less here. */
    private int bestAlt(Channel b) {
        if (b == null || b.alternates.isEmpty()) return 0;
        double[] scores = new double[1 + b.alternates.size()];
        scores[0] = score(Library.playlistOf(b.key));
        for (int i = 0; i < b.alternates.size(); i++) scores[i + 1] = score(Library.playlistOf(b.alternates.get(i).key));
        return Quality.pickSource(scores);
    }

    // ---- how each playlist does here ------------------------------------------------------------------------

    private SharedPreferences stats() {
        return app.getSharedPreferences("live_source_stats", Context.MODE_PRIVATE);
    }

    /** A playlist's record here: its usual start time plus 3 s for each recent stall; -1 when it hasn't played yet. */
    private double score(String playlist) {
        SharedPreferences p = stats();
        float start = p.getFloat("start." + playlist, -1f);
        if (start < 0) return -1;
        return start + 3000.0 * p.getFloat("stalls." + playlist, 0f);
    }

    private void started(long ms) {
        String id = Library.playlistOf(stream().key);
        SharedPreferences p = stats();
        float was = p.getFloat("start." + id, -1f);
        float stalls = p.getFloat("stalls." + id, 0f) * 0.8f;
        p.edit().putFloat("start." + id, was < 0 ? ms : was * 0.7f + ms * 0.3f).putFloat("stalls." + id, stalls).apply();
    }

    private void stalled() {
        String id = Library.playlistOf(stream().key);
        SharedPreferences p = stats();
        p.edit().putFloat("stalls." + id, Math.min(10f, p.getFloat("stalls." + id, 0f) + 1f)).apply();
    }

    // ---- adaptive: down when it can't keep up, back up once steady -----------------------------------------------

    private boolean adaptive() {
        return Quality.choice(app) == Quality.ADAPTIVE && rungs.size() > 1 && !replaying;
    }

    private void stallBegan() {
        long now = System.currentTimeMillis();
        bufferingSince = now;
        System.arraycopy(stalls, 1, stalls, 0, stalls.length - 1);
        stalls[stalls.length - 1] = now;
        stallCount = Math.min(stalls.length, stallCount + 1);
        stalled();
        h.removeCallbacks(stallCheck);
        h.postDelayed(stallCheck, 5000);
    }

    private void stallEnded() {
        h.removeCallbacks(stallCheck);
        long now = System.currentTimeMillis(), longest = bufferingSince > 0 ? now - bufferingSince : 0;
        bufferingSince = 0;
        long[] recent = java.util.Arrays.copyOfRange(stalls, stalls.length - stallCount, stalls.length);
        if (adaptive() && Quality.struggling(recent, recent.length, now, longest)) down();
    }

    /** Still stalled after 5 s: down a step now rather than wait. */
    private void stallCheck() {
        if (bufferingSince > 0 && adaptive()) down();
    }

    private void down() {
        if (rung >= rungs.size() - 1) return;
        boolean wasTry = upTriedAt > 0 && System.currentTimeMillis() - upTriedAt < 60_000L;
        if (wasTry) failedUps++;
        upTriedAt = 0;
        rung++;
        stallCount = 0;
        alt = bestAlt(base());
        asHls = Streams.looksHls(stream());
        if (!wasTry) notice("Slow connection: " + Quality.label(Library.level(base())) + " for now");
        retries = 0;
        prepare();
        h.removeCallbacks(upCheck);
        h.postDelayed(upCheck, Quality.upAfter(failedUps));
    }

    /** Steady for a while below the best allowed: one step back up (back down at once if it stalls). */
    private void tryUp() {
        if (!adaptive() || rung <= top || !live()) return;
        long now = System.currentTimeMillis();
        long last = stallCount > 0 ? stalls[stalls.length - 1] : 0;
        if (now - last < Quality.upAfter(failedUps)) {
            h.postDelayed(upCheck, Quality.upAfter(failedUps) - (now - last));
            return;
        }
        rung--;
        stallCount = 0;
        upTriedAt = now;
        alt = bestAlt(base());
        asHls = Streams.looksHls(stream());
        retries = 0;
        prepare();
        if (rung > top) h.postDelayed(upCheck, Quality.upAfter(failedUps));
    }

    // ---- the rewind ------------------------------------------------------------------------------------------

    boolean replaying() { return replaying; }

    /** The wall-clock moment on screen: in a replay, where it has got to; live, now less what is buffered ahead. */
    long shownAt() {
        if (player == null) return System.currentTimeMillis();
        if (replaying) return replayStart + player.getCurrentPosition();
        return System.currentTimeMillis() - player.getTotalBufferedDuration();
    }

    /** The earliest moment the rewind holds for what plays now (0: none). */
    long rewindFrom() {
        Channel s = cur == null ? null : stream();
        return s == null ? 0 : Timeshift.get(app).earliest(s.key);
    }

    /** The replay's last moment (live's edge when it began). */
    long replayEnd() {
        return replaySnap == null ? 0 : replaySnap.endWall;
    }

    /** Back to a moment (wall clock) from the rewind; false when it doesn't hold it. */
    boolean replay(long at) {
        if (player == null || cur == null) return false;
        Channel s = stream();
        if (replaying && replaySnap != null && at >= replayStart && at < replaySnap.endWall - 1500
                && player.isCurrentMediaItemSeekable()) {
            player.seekTo(at - replayStart);
            return true;
        }
        if (replaying && replaySnap != null && at >= replaySnap.endWall - 1500) {
            backToLive();
            return true;
        }
        Timeshift.Snapshot snap = Timeshift.get(app).from(s.key, Math.max(at, Timeshift.get(app).earliest(s.key)));
        if (snap == null || snap.length() <= 0) return false;
        h.removeCallbacks(retry);
        h.removeCallbacks(stallCheck);
        h.removeCallbacks(upCheck);
        replaying = true;
        replaySnap = snap;
        replayStart = snap.startWall;
        player.setMediaSource(Streams.replay(snap));
        player.prepare();
        player.setPlayWhenReady(true);
        set(LOADING);
        if (front != null) front.onReplay(true);
        return true;
    }

    /** From the replay back to the live stream. */
    void backToLive() {
        if (!replaying) return;
        replaying = false;
        replaySnap = null;
        if (front != null) front.onReplay(false);
        retries = 0;
        prepare();
    }

    /**
     * Dropped: on to the same channel in the next playlist when there is one (at once); then tried again a few times,
     * a second, two, four, then eight apart; then given up (tap to try again).
     */
    private void dropped() {
        // The replay at the end of what was kept: live again.
        if (replaying) {
            backToLive();
            return;
        }
        Channel b = base();
        if (b != null && alt < b.alternates.size()) {
            alt++;
            retries = 0;
            asHls = Streams.looksHls(stream());
            set(RECONNECTING);
            h.removeCallbacks(retry);
            prepare();
            return;
        }
        if (retries < RETRIES) {
            retries++;
            set(RECONNECTING);
            h.removeCallbacks(retry);
            h.postDelayed(retry, Math.min(8000, 1000L << (retries - 1)));
        } else {
            set(FAILED);
        }
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (player == null) return;
            if (s == Player.STATE_READY) {
                retries = 0;
                if (bufferingSince > 0) stallEnded();
                set(PLAYING);
            } else if (s == Player.STATE_BUFFERING) {
                if (state == PLAYING) {
                    if (!replaying) stallBegan();
                    set(LOADING);
                }
            } else if (s == Player.STATE_ENDED) {
                // A live stream doesn't end: the provider closed it.
                dropped();
            }
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            // Only the kind of error: the stream's address (with the login in it) is never written out.
            android.util.Log.w("LiveTv", "playback: " + e.getErrorCodeName());
            if (player == null) return;
            if (replaying) {
                backToLive();
                return;
            }
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
                return;
            }
            // An address with no extension that is really HLS: once more as HLS.
            if (!asHls && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                    || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                asHls = true;
                prepare();
                return;
            }
            dropped();
        }

        @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (front != null) front.onState(state);
        }

        @Override public void onRenderedFirstFrame() {
            if (!firstFrame && !replaying && preparedAt > 0) {
                lastStartMs = android.os.SystemClock.elapsedRealtime() - preparedAt;
                started(lastStartMs);
                preparedAt = 0;
            }
            firstFrame = true;
            if (front != null) front.onFirstFrame();
        }

        @Override public void onVideoSizeChanged(@NonNull VideoSize size) {
            if (front != null) front.onVideoSize(size);
        }
    };

    // ---- the last channel -------------------------------------------------------------------------------------

    private SharedPreferences prefs() {
        return app.getSharedPreferences("live_player", Context.MODE_PRIVATE);
    }

    private void remember(Channel ch) {
        prefs().edit().putString("last", ch.key).apply();
    }

    /** The channel played last on this phone (this run or before), if it is still in the playlist. */
    Channel last(Library lib) {
        if (cur != null && lib.byKey(cur.key) != null) return lib.byKey(cur.key);
        return lib.byKey(prefs().getString("last", null));
    }

    /** The playlist was removed: nothing to offer again. */
    void forget() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            h.post(this::forget);
            return;
        }
        stop();
        cur = null;
        prefs().edit().remove("last").apply();
    }
}
