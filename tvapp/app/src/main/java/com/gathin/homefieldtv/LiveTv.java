package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Streams;

import java.util.ArrayList;
import java.util.List;

/**
 * The one live picture: the mini TV on Home and in the guide, and full screen when watching. One player, moved and
 * resized rather than started again, because a provider usually allows one stream at a time. A channel that drops goes
 * on to its copy in the family's other playlist, then is tried again a few times. The stream's address is never
 * logged.
 */
@OptIn(markerClass = UnstableApi.class)
final class LiveTv {
    static final int IDLE = 0, LOADING = 1, PLAYING = 2, RETRYING = 3, FAILED = 4;
    private static final int RETRIES = 4;
    private static final long STALL_MS = 15_000L;
    /** Previews: a still picture stands in for the stream. */
    static Bitmap previewStill;

    interface Listener {
        void liveChanged();
    }

    private final Context app;
    private final FrameLayout layer;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final View picture;
    private final TextView note;
    private final List<Listener> listeners = new ArrayList<>();
    private final Rect where = new Rect();
    private ExoPlayer player;
    private Channel cur;
    private int alt, retries, state = IDLE;
    private boolean asHls;
    private long bufferingSince;
    private int videoW, videoH;

    private final Runnable retry = this::prepare;
    private final Runnable stallCheck = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            if (state == LOADING && bufferingSince > 0 && SystemClock.elapsedRealtime() - bufferingSince > STALL_MS) {
                dropped();
                return;
            }
            h.postDelayed(this, 2000);
        }
    };

    LiveTv(Context c, FrameLayout layer) {
        this.app = c.getApplicationContext();
        this.layer = layer;
        if (Exec.inline) {
            ImageView still = new ImageView(c);
            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (previewStill != null) still.setImageBitmap(previewStill);
            picture = still;
        } else {
            picture = new SurfaceView(c);
        }
        picture.setVisibility(View.GONE);
        layer.addView(picture, new FrameLayout.LayoutParams(1, 1));
        note = new TextView(c);
        note.setTypeface(Ui.regular);
        note.setTextColor(Ui.WHITE);
        note.setGravity(Gravity.CENTER);
        note.setVisibility(View.GONE);
        layer.addView(note, new FrameLayout.LayoutParams(1, 1));
    }

    void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Where the picture shows, in screen pixels; null or empty hides it (the stream carries on). */
    void place(Rect r) {
        if (r == null || r.isEmpty()) {
            where.setEmpty();
            picture.setVisibility(View.GONE);
            note.setVisibility(View.GONE);
            return;
        }
        where.set(r);
        layout();
        picture.setVisibility(cur == null ? View.GONE : View.VISIBLE);
        showNote();
    }

    Rect where() {
        return where;
    }

    /** The picture kept to its own shape inside where it goes (a 4:3 channel with bars at its sides). */
    private void layout() {
        if (where.isEmpty()) return;
        int x = where.left, y = where.top, w = where.width(), hh = where.height();
        if (videoW > 0 && videoH > 0 && !Exec.inline) {
            float va = videoW / (float) videoH, ra = w / (float) hh;
            if (Math.abs(va - ra) > 0.03f) {
                if (va > ra) {
                    int nh = Math.round(w / va);
                    y += (hh - nh) / 2;
                    hh = nh;
                } else {
                    int nw = Math.round(hh * va);
                    x += (w - nw) / 2;
                    w = nw;
                }
            }
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) picture.getLayoutParams();
        if (lp.width != w || lp.height != hh || lp.leftMargin != x || lp.topMargin != y) {
            lp.width = w;
            lp.height = hh;
            lp.leftMargin = x;
            lp.topMargin = y;
            picture.setLayoutParams(lp);
        }
        FrameLayout.LayoutParams np = (FrameLayout.LayoutParams) note.getLayoutParams();
        np.width = where.width();
        np.height = where.height();
        np.leftMargin = where.left;
        np.topMargin = where.top;
        note.setLayoutParams(np);
        note.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Math.max(Ui.f(22), where.height() / 14f));
    }

    Channel channel() {
        return cur;
    }

    int state() {
        return state;
    }

    /** The copy playing: the channel's own, or its copy in another playlist. */
    Channel stream() {
        if (cur == null) return null;
        return alt > 0 && alt <= cur.alternates.size() ? cur.alternates.get(alt - 1) : cur;
    }

    /** A channel played; the same channel already playing carries on. */
    void play(Channel c) {
        if (c == null) return;
        if (cur != null && cur.key.equals(c.key) && player != null && state != FAILED) {
            cur = c;
            if (!where.isEmpty()) picture.setVisibility(View.VISIBLE);
            return;
        }
        cur = c;
        alt = 0;
        retries = 0;
        asHls = Streams.looksHls(stream().url);
        videoW = videoH = 0;
        Library.get(app).watched(c);
        if (!where.isEmpty()) {
            layout();
            picture.setVisibility(View.VISIBLE);
        }
        if (Exec.inline) {
            set(PLAYING);
            return;
        }
        ensure();
        prepare();
    }

    /** Let go of the stream (another screen, a film, the TV leaving the app); the channel is kept for next time. */
    void stop() {
        h.removeCallbacks(retry);
        h.removeCallbacks(stallCheck);
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
        }
        picture.setVisibility(View.GONE);
        set(IDLE);
    }

    /** The playlists were removed or changed: no channel to go back to. */
    void forget() {
        stop();
        cur = null;
    }

    /** Played again after a stop (the same channel). */
    void resume() {
        if (cur == null || player != null) return;
        Channel c = cur;
        cur = null;
        play(c);
    }

    void tryAgain() {
        if (cur == null) return;
        retries = 0;
        alt = 0;
        asHls = Streams.looksHls(stream().url);
        if (player == null) ensure();
        prepare();
    }

    private void ensure() {
        if (player != null) return;
        // The TV's own decoders first; FFmpeg for the sound it can't play (AC-3, E-AC-3, MP2), which UK channels carry.
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(app)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
        // A channel starts as soon as a second is in (two after a stall).
        DefaultLoadControl load = new DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 50_000, 1_000, 2_000).build();
        player = new ExoPlayer.Builder(app, renderers).setLoadControl(load).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.addListener(listener);
        if (picture instanceof SurfaceView) player.setVideoSurfaceView((SurfaceView) picture);
    }

    private void prepare() {
        if (player == null || cur == null) return;
        h.removeCallbacks(stallCheck);
        Channel s = stream();
        player.setMediaSource(Streams.source(s.url, s.userAgent, s.referrer, asHls));
        player.prepare();
        player.setPlayWhenReady(true);
        bufferingSince = SystemClock.elapsedRealtime();
        set(LOADING);
        h.postDelayed(stallCheck, 2000);
    }

    /** Dropped: on to the same channel in the next playlist when there is one; then tried again 1, 2, 4, 8 s apart. */
    private void dropped() {
        if (cur == null) return;
        h.removeCallbacks(retry);
        if (alt < cur.alternates.size()) {
            alt++;
            retries = 0;
            asHls = Streams.looksHls(stream().url);
            set(RETRYING);
            prepare();
            return;
        }
        if (retries < RETRIES) {
            retries++;
            set(RETRYING);
            h.postDelayed(retry, Math.min(8000, 1000L << (retries - 1)));
        } else {
            set(FAILED);
        }
    }

    private void set(int s) {
        state = s;
        showNote();
        for (Listener l : new ArrayList<>(listeners)) l.liveChanged();
    }

    private void showNote() {
        String t = state == LOADING ? "" : state == RETRYING ? "Reconnecting…" : state == FAILED ? "This channel isn't working right now" : "";
        note.setText(t);
        note.setVisibility(t.isEmpty() || where.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (player == null) return;
            if (s == Player.STATE_READY) {
                retries = 0;
                bufferingSince = 0;
                set(PLAYING);
            } else if (s == Player.STATE_BUFFERING) {
                bufferingSince = SystemClock.elapsedRealtime();
                if (state == PLAYING) set(LOADING);
            } else if (s == Player.STATE_ENDED) {
                // A live stream doesn't end: the provider closed it.
                dropped();
            }
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            // Only the kind of error: the stream's address (with the login in it) is never written out.
            android.util.Log.w("HomefieldTv", "live: " + e.getErrorCodeName());
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

        @Override public void onVideoSizeChanged(@NonNull VideoSize size) {
            if (size.width <= 0 || size.height <= 0) return;
            videoW = Math.round(size.width * size.pixelWidthHeightRatio);
            videoH = size.height;
            layout();
        }
    };
}
