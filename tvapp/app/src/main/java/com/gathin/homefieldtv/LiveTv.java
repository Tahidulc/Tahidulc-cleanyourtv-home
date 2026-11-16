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
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Streams;
import com.gathin.homefieldtv.prefs.Adaptive;
import com.gathin.homefieldtv.prefs.Afr;
import com.gathin.homefieldtv.prefs.Backups;
import com.gathin.homefieldtv.prefs.Picture;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.prefs.PlayerSetup;

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
    private static final long STALL_MS = 15_000L;
    /** Previews: a still picture stands in for the stream, and its format for the player's. */
    static Bitmap previewStill;
    static int previewWidth, previewHeight;
    static float previewFps;

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
    private int videoW, videoH, rawW, rawH;
    /**
     * Settings → Player → When a stream fails → Switch to a backup copy: the channel's copies in the other playlists and
     * groups, the best picture first, found when first needed (null until then, and always when staying on the channel).
     */
    private List<Channel> copies;
    /** This stream is the provider's HLS form of an MPEG-TS address (Adaptive), not yet seen playing; or it failed. */
    private boolean hlsTried, hlsRefused, everReady;
    /** Settings → Player → Match frame rate: this stream's rate being read, while it is full screen. */
    private Afr.Probe probe;

    private final Runnable retry = this::prepare;
    private final Runnable stallCheck = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            if (state == LOADING && bufferingSince > 0 && SystemClock.elapsedRealtime() - bufferingSince > STALL_MS) {
                // The provider's HLS form that never started: its MPEG-TS (only this channel; a slow start says
                // nothing of the playlist). Else the same stream again.
                if (hlsTried && !everReady) refuseHls(false);
                else dropped();
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
        frameRate();
    }

    /** Whether the picture has the whole screen (its shape and the TV's refresh rate are Settings' only then). */
    private boolean full() {
        int lw = layer.getWidth() > 0 ? layer.getWidth() : layer.getResources().getDisplayMetrics().widthPixels;
        int lh = layer.getHeight() > 0 ? layer.getHeight() : layer.getResources().getDisplayMetrics().heightPixels;
        return !where.isEmpty() && where.width() >= lw - 1 && where.height() >= lh - 1;
    }

    /** Full screen: the TV's refresh rate matched to the stream's (Settings); else the TV's own. */
    private void frameRate() {
        android.app.Activity a = layer.getContext() instanceof android.app.Activity ? (android.app.Activity) layer.getContext() : null;
        if (player == null || !full() || !PlayerPrefs.matchFrameRate(app)) {
            if (probe != null && player != null) player.clearVideoFrameMetadataListener(probe);
            probe = null;
            Afr.reset(a);
            return;
        }
        if (probe != null || a == null) return;
        probe = new Afr.Probe(a);
        player.setVideoFrameMetadataListener(probe);
        probe.format(player.getVideoFormat());
    }

    /** The picture placed again (its shape changed in the player's menu). */
    void refit() {
        layout();
    }

    /** The player now, for the player's menu (its sound tracks); null when nothing plays. */
    ExoPlayer player() {
        return player;
    }

    Rect where() {
        return where;
    }

    /**
     * The picture kept to its own shape inside where it goes (a 4:3 channel with bars at its sides); full screen, in the
     * shape Settings or the player's menu chose for the channel (Fit, Fill, Zoom, 16:9, 4:3).
     */
    private void layout() {
        if (where.isEmpty()) return;
        int x = where.left, y = where.top, w = where.width(), hh = where.height();
        if (!Exec.inline) {
            int mode = full() && cur != null ? PlayerPrefs.aspectFor(app, cur.key) : PlayerPrefs.FIT;
            int[] p = Picture.place(mode, videoW, videoH, x, y, w, hh);
            x = p[0];
            y = p[1];
            w = p[2];
            hh = p[3];
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

    /**
     * The picture as the player has it, for the banner's chips: its size ("1280×720") and frames a second ("25 fps"),
     * each "" when the player doesn't know it (or nothing is playing).
     */
    String[] format() {
        int w = 0, hh = 0;
        float fps = 0f;
        if (Exec.inline) {
            if (cur != null) {
                w = previewWidth;
                hh = previewHeight;
                fps = previewFps;
            }
        } else if (player != null && (state == PLAYING || state == LOADING)) {
            Format f = player.getVideoFormat();
            if (f != null) {
                w = f.width;
                hh = f.height;
                fps = f.frameRate;
            }
            if (w <= 0 || hh <= 0) {
                w = rawW;
                hh = rawH;
            }
        }
        return new String[]{LiveText.size(w, hh), LiveText.fps(fps)};
    }

    /** The copy playing: the channel's own, or (Settings: switch to a backup copy) one of its copies. */
    Channel stream() {
        if (cur == null) return null;
        return copies != null && alt > 0 && alt <= copies.size() ? copies.get(alt - 1) : cur;
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
        copies = null;
        hlsRefused = false;
        asHls = Streams.looksHls(stream().url);
        videoW = videoH = rawW = rawH = 0;
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
        probe = null;
        Afr.reset(layer.getContext() instanceof android.app.Activity ? (android.app.Activity) layer.getContext() : null);
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
        copies = null;
        hlsRefused = false;
        asHls = Streams.looksHls(stream().url);
        if (player == null) ensure();
        prepare();
    }

    private void ensure() {
        if (player != null) return;
        // As Settings → Player and Sound say: the buffer (a channel starts as soon as a second is in, two after a stall,
        // as Homefield came), the TV's own decoders first with FFmpeg for the sound it can't play (AC-3, E-AC-3, MP2).
        player = PlayerSetup.builder(app, true).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.addListener(listener);
        if (picture instanceof SurfaceView) player.setVideoSurfaceView((SurfaceView) picture);
    }

    private void prepare() {
        if (player == null || cur == null) return;
        h.removeCallbacks(stallCheck);
        Channel s = stream();
        // The provider's HLS form of an MPEG-TS channel first, so a drop is ridden out on its segments and the picture can
        // adapt; MPEG-TS again where it doesn't play (Adaptive, Settings → Player).
        String url = s.url;
        boolean hls = asHls;
        String hlsUrl = hlsRefused || !PlayerPrefs.hlsFirst(app) || Adaptive.hlsFailed(app, PlayerPrefs.playlistOf(s.key))
                ? null : Adaptive.hlsFor(s.url);
        hlsTried = hlsUrl != null;
        if (hlsTried) {
            url = hlsUrl;
            hls = true;
        }
        everReady = false;
        // A playlist's own user agent (Settings → Stream problems) over the channel's.
        player.setMediaSource(Streams.source(url, PlayerPrefs.agent(app, s.key, s.userAgent), s.referrer, hls));
        player.prepare();
        player.setPlayWhenReady(true);
        bufferingSince = SystemClock.elapsedRealtime();
        set(LOADING);
        h.postDelayed(stallCheck, 2000);
        // A new stream: its frame rate read again.
        if (probe != null) player.clearVideoFrameMetadataListener(probe);
        probe = null;
        frameRate();
    }

    /**
     * Dropped or stalled. As Settings → Player → When a stream fails says:
     * - Stay on the channel and adapt (the default): the same stream connected again, 1, 2, 4, then 8 s apart, for as
     *   long as Settings → Stream problems say (keep trying, unless chosen otherwise); never another copy.
     * - Switch to a backup copy: this copy tried again a little first (as often as Settings say, twice when that's
     *   "keep trying"), then the channel's copies in the other playlists and groups, the best picture first; round
     *   again when Settings say to keep trying.
     */
    private void dropped() {
        if (cur == null) return;
        h.removeCallbacks(retry);
        int tries = PlayerPrefs.retries(app);
        if (!PlayerPrefs.switchToBackup(app)) {
            if (tries < 0 || retries < tries) reconnect();
            else set(FAILED);
            return;
        }
        int here = tries < 0 ? 2 : tries;
        if (retries < here) {
            reconnect();
            return;
        }
        if (copies == null) copies = Adaptive.bestFirst(cur.alternates, Backups.more(Library.get(app).all(), cur));
        if (alt < copies.size()) {
            alt++;
            retries = 0;
            hlsRefused = false;
            asHls = Streams.looksHls(stream().url);
            set(RETRYING);
            prepare();
            return;
        }
        if (tries < 0) {
            // Every copy tried: round again from the channel's own.
            alt = 0;
            retries = 0;
            asHls = Streams.looksHls(stream().url);
            reconnect();
            return;
        }
        set(FAILED);
    }

    /** This channel back on its MPEG-TS address (the HLS form didn't play); the playlist's HLS skipped a while too if so. */
    private void refuseHls(boolean forPlaylist) {
        Channel s = stream();
        if (forPlaylist && s != null) Adaptive.markHlsFailed(app, PlayerPrefs.playlistOf(s.key));
        hlsRefused = true;
        prepare();
    }

    /** The same stream connected again after a wait (1, 2, 4, then 8 s), "Reconnecting…" on the picture meanwhile. */
    private void reconnect() {
        retries++;
        set(RETRYING);
        h.postDelayed(retry, PlayerPrefs.backoffMs(retries));
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
                everReady = true;
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
            // The provider's HLS form never played: its MPEG-TS at once; HLS left alone for the whole playlist a while
            // when the provider plainly doesn't offer it (refused, not found, not HLS), not for a dropped connection.
            if (hlsTried && !everReady) {
                refuseHls(Adaptive.notOffered(e.errorCode));
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
            rawW = size.width;
            rawH = size.height;
            videoW = Math.round(size.width * size.pixelWidthHeightRatio);
            videoH = size.height;
            layout();
        }
    };
}
