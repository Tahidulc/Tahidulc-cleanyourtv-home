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
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
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
    }

    @SuppressLint("StaticFieldLeak") // the application's context only
    private static Playback instance;

    private final Context app;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private Channel cur;
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
        cur = ch;
        retries = 0;
        asHls = Streams.looksHls(ch);
        firstFrame = false;
        ensure();
        // Another channel's audio, subtitle or quality choice means nothing here (subtitles off stays off).
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().build());
        remember(ch);
        Picks.watched(app, ch.key);
        prepare();
    }

    /** The stream again from the start (after it stalls or gives up, or to catch up with live). */
    void restart() {
        if (cur == null) return;
        h.removeCallbacks(retry);
        retries = 0;
        firstFrame = false;
        ensure();
        prepare();
    }

    /** Stops and lets the stream go; the channel stays the last one. */
    void stop() {
        h.removeCallbacks(retry);
        h.removeCallbacks(orphan);
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
        player = new ExoPlayer.Builder(app, renderers).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(listener);
        if (front != null) front.view().setPlayer(player);
    }

    private void prepare() {
        if (player == null || cur == null) return;
        player.setMediaSource(Streams.source(cur, asHls));
        player.prepare();
        player.setPlayWhenReady(true);
        set(LOADING);
    }

    private void set(int s) {
        state = s;
        if (front != null) front.onState(s);
    }

    /** Dropped: tried again a few times, a second, two, four, then eight apart; then given up (tap to try again). */
    private void dropped() {
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
                set(PLAYING);
            } else if (s == Player.STATE_BUFFERING) {
                if (state == PLAYING) set(LOADING);
            } else if (s == Player.STATE_ENDED) {
                // A live stream doesn't end: the provider closed it.
                dropped();
            }
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            // Only the kind of error: the stream's address (with the login in it) is never written out.
            android.util.Log.w("LiveTv", "playback: " + e.getErrorCodeName());
            if (player == null) return;
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
