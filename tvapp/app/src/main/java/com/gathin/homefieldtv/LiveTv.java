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
import androidx.media3.common.Timeline;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Streams;

import java.util.ArrayList;
import java.util.List;

/**
 * The one live picture: the mini TV on Home and in the guide, and full screen when watching. One player, moved and
 * resized rather than started again, because a provider usually allows one stream at a time. A channel that drops goes
 * on to its copy in the family's other playlist, then is tried again a few times. The stream's address is never
 * logged.
 *
 * <p>Behind live (catch-up): a programme from the provider's archive (Catchup), asked for again from wherever the remote
 * moves it to, going on into the next programme when one ends and back to live at the live edge; or, for a stream
 * whose own window reaches back minutes (HLS with a long window), a move inside it.
 */
@OptIn(markerClass = UnstableApi.class)
final class LiveTv {
    static final int IDLE = 0, LOADING = 1, PLAYING = 2, RETRYING = 3, FAILED = 4;
    private static final int RETRIES = 4;
    private static final long STALL_MS = 15_000L;
    /** A stream's own window counts as one to go back in from five minutes long. */
    static final long MIN_WINDOW_MS = 5 * 60_000L;
    /** Within a minute of now is live: the archive's newest minute may not be in it yet. */
    static final long LIVE_EDGE_MS = 60_000L;
    /** Previews: a still picture stands in for the stream, and its format for the player's. */
    static Bitmap previewStill;
    static int previewWidth, previewHeight;
    static float previewFps;
    /** Previews: the time the picture behind live is of. */
    static long previewShown = -1;

    interface Listener {
        void liveChanged();
    }

    /** A programme playing from the archive: the channel, the time the request started at, how far to ask for. */
    private static final class Archive {
        final Channel channel;
        long from, end;
        /** The older Xtream address (timeshift.php) in use; whether it has played (later errors pick up where it got to). */
        boolean older, played;
        int retries;

        Archive(Channel channel, long from, long end) {
            this.channel = channel;
            this.from = from;
            this.end = end;
        }
    }

    private final Context app;
    private final FrameLayout layer;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final View picture;
    private final TextView note;
    private final List<Listener> listeners = new ArrayList<>();
    private final Rect where = new Rect();
    private final Timeline.Window tw = new Timeline.Window();
    private ExoPlayer player;
    private Channel cur;
    private int alt, retries, state = IDLE;
    private boolean asHls;
    private long bufferingSince;
    private int videoW, videoH, rawW, rawH;
    /** Catch-up: the archive playing (null: the live stream), moved back in the live stream's own window, paused. */
    private Archive arc;
    private boolean inWindow, held;

    private final Runnable retry = this::prepare;
    private final Runnable stallCheck = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            if (state == LOADING && !held && bufferingSince > 0 && SystemClock.elapsedRealtime() - bufferingSince > STALL_MS) {
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

    /** The copy playing: the channel's own, or its copy in another playlist. */
    Channel stream() {
        if (cur == null) return null;
        return alt > 0 && alt <= cur.alternates.size() ? cur.alternates.get(alt - 1) : cur;
    }

    /** A channel played live; the same channel already playing live carries on (behind live, it goes back to live). */
    void play(Channel c) {
        if (c == null) return;
        boolean same = cur != null && cur.key.equals(c.key);
        if (same && arc == null && player != null && state != FAILED) {
            cur = c;
            if (inWindow || held) {
                // Moved back in the stream's own window: to its live edge.
                inWindow = false;
                held = false;
                player.seekToDefaultPosition();
                player.setPlayWhenReady(true);
                set(state);
            }
            if (!where.isEmpty()) picture.setVisibility(View.VISIBLE);
            return;
        }
        arc = null;
        inWindow = false;
        held = false;
        cur = c;
        alt = 0;
        retries = 0;
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
        if (arc != null && player != null) {
            // Picked up again where it got to.
            arc.from = Catchup.requestAt(kind(arc.channel), shownAt(), 0, 0);
            arc.played = false;
        }
        inWindow = false;
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
        arc = null;
        held = false;
        cur = null;
    }

    /** Played again after a stop (the same channel, live). */
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
        if (arc != null) {
            arc.retries = 0;
            arc.older = false;
            asHls = false;
        } else {
            asHls = Streams.looksHls(stream().url);
        }
        if (player == null) ensure();
        prepare();
    }

    // ---- behind live -------------------------------------------------------------------------------------------------

    /** Whether the picture is behind live: from the archive, or moved back in the stream's own window. */
    boolean behind() {
        return arc != null || inWindow;
    }

    /** Whether it plays from the provider's archive. */
    boolean archived() {
        return arc != null;
    }

    /** Paused behind live. */
    boolean held() {
        return held;
    }

    /** The time the picture is of (now when live). */
    long shownAt() {
        long now = System.currentTimeMillis();
        if (Exec.inline) return previewShown >= 0 && behind() ? previewShown : now;
        if (arc != null) return arc.from + (player == null ? 0 : Math.max(0, player.getCurrentPosition()));
        if (inWindow && player != null) {
            long[] w = window();
            if (w != null) return Math.min(now, w[0] + Math.max(0, player.getCurrentPosition()));
        }
        return now;
    }

    /** The live stream's own window as times {from, to}, when it reaches back far enough to move in (null: it doesn't). */
    long[] window() {
        if (player == null || arc != null || cur == null) return null;
        Timeline t = player.getCurrentTimeline();
        if (t.isEmpty()) return null;
        t.getWindow(player.getCurrentMediaItemIndex(), tw);
        if (!tw.isLive() || !tw.isSeekable) return null;
        long dur = tw.getDurationMs();
        if (dur == C.TIME_UNSET || dur < MIN_WINDOW_MS) return null;
        // Its own times where the stream gives them (EXT-X-PROGRAM-DATE-TIME), else its end taken as now.
        long from = tw.windowStartTimeMs != C.TIME_UNSET ? tw.windowStartTimeMs : System.currentTimeMillis() - dur;
        return new long[]{from, from + dur};
    }

    /** The earliest time channel c can be shown from (its archive, or its stream's window while it plays); MAX_VALUE: none. */
    long earliest(Channel c) {
        if (c == null) return Long.MAX_VALUE;
        long e = Catchup.earliest(c, System.currentTimeMillis());
        long[] w = cur != null && cur.key.equals(c.key) ? window() : null;
        return w == null ? e : Math.min(e, w[0]);
    }

    /** Whether time t on channel c can be shown: its archive has it, or its stream's window does while it plays. */
    boolean canShow(Channel c, long t) {
        return c != null && t >= earliest(c) && t < System.currentTimeMillis();
    }

    /**
     * Channel c shown from time t: inside its stream's window when that reaches back to it (no new request), else from
     * its archive (asked for again, ending a little after the programme ending at programmeEnd); within a minute of
     * now, live. dir: the way the remote moved it (+1 on, -1 back, 0 neither).
     */
    void showAt(Channel c, long t, long programmeEnd, int dir) {
        if (c == null) return;
        long now = System.currentTimeMillis();
        if (t >= now - LIVE_EDGE_MS) {
            play(c);
            return;
        }
        boolean same = cur != null && cur.key.equals(c.key);
        if (Exec.inline) {
            if (!same) cur = c;
            if (Catchup.source(c) != null) arc = new Archive(c, t, programmeEnd);
            else inWindow = true;
            previewShown = t;
            if (!where.isEmpty()) {
                layout();
                picture.setVisibility(View.VISIBLE);
            }
            set(PLAYING);
            return;
        }
        // Inside the live stream's own window: a move, nothing asked for again.
        long[] w = same && arc == null ? window() : null;
        if (w != null && t >= w[0] && player != null) {
            inWindow = true;
            player.seekTo(Math.max(0, t - w[0]));
            player.setPlayWhenReady(!held);
            set(state);
            return;
        }
        Channel src = Catchup.source(c);
        if (src == null) return;
        // Already playing from an archive whose stream can be moved in (HLS): moved, not asked for again.
        if (same && arc != null && player != null && player.isCurrentMediaItemSeekable()) {
            long d = player.getDuration();
            if (d != C.TIME_UNSET && t >= arc.from && t < arc.from + d - 5000) {
                player.seekTo(t - arc.from);
                player.setPlayWhenReady(!held);
                set(state);
                return;
            }
        }
        long start = Catchup.requestAt(src.catchup, t, shownAt(), dir);
        if (!same) {
            cur = c;
            alt = 0;
            retries = 0;
            Library.get(app).watched(c);
        }
        inWindow = false;
        asHls = false;
        videoW = videoH = rawW = rawH = 0;
        arc = new Archive(c, start, programmeEnd > start ? programmeEnd : programmeEnd(c, start));
        if (!where.isEmpty()) {
            layout();
            picture.setVisibility(View.VISIBLE);
        }
        ensure();
        prepare();
    }

    /** Paused (or played on) behind live. */
    void hold(boolean pause) {
        held = pause;
        if (player != null) player.setPlayWhenReady(!pause);
        if (Exec.inline || player != null) set(state);
    }

    /** After a stop, the archive asked for again from where it got to. */
    void resumeBehind() {
        if (arc == null || player != null || Exec.inline) return;
        ensure();
        prepare();
    }

    private static String kind(Channel c) {
        Channel s = Catchup.source(c);
        return s == null ? "" : s.catchup;
    }

    /** The end of the programme on channel c at time t, from the guide (half an hour on when it has none). */
    private long programmeEnd(Channel c, long t) {
        Guide.Schedule s = Library.get(app).schedule(c.key);
        int i = s == null ? -1 : s.at(t);
        return i >= 0 ? s.stop[i] : t + 30 * 60_000L;
    }

    // ---- the player ------------------------------------------------------------------------------------------------

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
        h.removeCallbacks(retry);
        if (arc != null) {
            // The archive's address, made now from the channel's live one (it holds the login: never logged).
            long end = Catchup.requestEnd(arc.from, arc.end);
            String url = arc.older ? Catchup.olderUrl(arc.channel, arc.from, end) : Catchup.url(arc.channel, arc.from, end,
                    System.currentTimeMillis());
            Channel s = Catchup.source(arc.channel);
            if (url == null || s == null) {
                set(FAILED);
                return;
            }
            player.setMediaSource(Streams.source(url, s.userAgent, s.referrer, asHls || Streams.looksHls(url)));
        } else {
            Channel s = stream();
            player.setMediaSource(Streams.source(s.url, s.userAgent, s.referrer, asHls));
        }
        player.prepare();
        player.setPlayWhenReady(!held);
        bufferingSince = SystemClock.elapsedRealtime();
        set(LOADING);
        h.postDelayed(stallCheck, 2000);
    }

    /** Dropped: on to the same channel in the next playlist when there is one; then tried again 1, 2, 4, 8 s apart. */
    private void dropped() {
        if (cur == null) return;
        h.removeCallbacks(retry);
        if (arc != null) {
            archiveFailed(null);
            return;
        }
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

    /** The archive ran out: on from where it got to (into the next programme), or live when that is now. */
    private void archiveEnded() {
        if (arc == null) return;
        long t = shownAt(), now = System.currentTimeMillis();
        if (t >= now - LIVE_EDGE_MS) {
            play(arc.channel);
            return;
        }
        arc.from = Catchup.requestAt(kind(arc.channel), t + 5000, 0, 0);
        arc.end = programmeEnd(arc.channel, arc.from);
        arc.played = false;
        prepare();
    }

    /**
     * The archive didn't play: once more as HLS for an address that is; before it has played, the older Xtream address,
     * then once more a moment later; after it has, from where it got to, a few times; then it can't.
     */
    private void archiveFailed(PlaybackException e) {
        if (arc == null) return;
        if (e != null && !asHls && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
            asHls = true;
            prepare();
            return;
        }
        if (!arc.played && !arc.older && Catchup.XC.equals(kind(arc.channel))) {
            arc.older = true;
            asHls = false;
            prepare();
            return;
        }
        int most = arc.played ? RETRIES : 1;
        if (arc.retries < most) {
            arc.retries++;
            if (arc.played) arc.from = Catchup.requestAt(kind(arc.channel), shownAt(), 0, 0);
            arc.played = false;
            arc.older = false;
            asHls = false;
            set(RETRYING);
            h.postDelayed(retry, Math.min(8000, 1000L << (arc.retries - 1)));
            return;
        }
        set(FAILED);
    }

    private void set(int s) {
        state = s;
        showNote();
        for (Listener l : new ArrayList<>(listeners)) l.liveChanged();
    }

    private void showNote() {
        String t = state == RETRYING ? "Reconnecting…"
                : state == FAILED ? (arc != null ? "This programme couldn't be played from the provider's archive"
                : "This channel isn't working right now") : "";
        note.setText(t);
        note.setVisibility(t.isEmpty() || where.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (player == null) return;
            if (s == Player.STATE_READY) {
                retries = 0;
                bufferingSince = 0;
                if (arc != null) {
                    arc.played = true;
                    arc.retries = 0;
                }
                set(PLAYING);
            } else if (s == Player.STATE_BUFFERING) {
                bufferingSince = SystemClock.elapsedRealtime();
                if (state == PLAYING) set(LOADING);
            } else if (s == Player.STATE_ENDED) {
                if (arc != null) archiveEnded();
                // A live stream doesn't end: the provider closed it.
                else dropped();
            }
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            // Only the kind of error: the stream's address (with the login in it) is never written out.
            android.util.Log.w("HomefieldTv", (arc != null ? "archive: " : "live: ") + e.getErrorCodeName());
            if (player == null) return;
            if (arc != null) {
                archiveFailed(e);
                return;
            }
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                // Paused or moved back past the start of the stream's window: live again.
                inWindow = false;
                player.seekToDefaultPosition();
                player.prepare();
                set(state);
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

        @Override public void onIsPlayingChanged(boolean playing) {
            // Paused or played on behind live: the bar and its knob drawn again.
            if (behind()) set(state);
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
