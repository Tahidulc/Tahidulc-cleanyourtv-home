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
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.homefieldtv.captions.AudioTap;
import com.gathin.homefieldtv.engine.Catchup;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Names;
import com.gathin.homefieldtv.engine.Streams;
import com.gathin.homefieldtv.prefs.Abr;
import com.gathin.homefieldtv.prefs.Adaptive;
import com.gathin.homefieldtv.prefs.Afr;
import com.gathin.homefieldtv.prefs.Ladder;
import com.gathin.homefieldtv.prefs.Picture;
import com.gathin.homefieldtv.prefs.PlayerPrefs;
import com.gathin.homefieldtv.prefs.PlayerSetup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The one live picture: the mini TV on Home and in the guide, and full screen when watching. One player, moved and
 * resized rather than started again, because a provider usually allows one stream at a time. The stream's address is
 * never logged.
 *
 * <p>Settings → Player → Picture quality, as the phone plays it (Homefield 5.22). Adaptive (the default): the channel
 * picked stays on screen (its name, number, logo, guide, favourites, recents, scoreboard) while its picture follows the
 * connection through the channel's own versions (Ladder): a step down before it freezes, back up once steady, each step
 * quiet, with the last picture held on the SurfaceView (no shutter, no spinner, no message); a stream that fails is
 * tried again at once twice, then the same version from another playlist, then a step down, then again with back-off.
 * A fixed quality plays that version and only ever connects the same stream again.
 *
 * <p>Behind live (catch-up): a programme from the provider's archive (Catchup), asked for again from wherever the remote
 * moves it to, going on into the next programme when one ends and back to live at the live edge; or, for a stream
 * whose own window reaches back minutes (HLS with a long window), a move inside it.
 *
 * <p>Subtitles over the full-screen picture (LiveSubs): the channel's own from the player's cues, or automatic ones made
 * on the TV from a copy of the sound taken in the player's sound chain (TAP).
 */
@OptIn(markerClass = UnstableApi.class)
final class LiveTv {
    static final int IDLE = 0, LOADING = 1, PLAYING = 2, RETRYING = 3, FAILED = 4;
    /** How often a programme from the archive is asked for again once it has played. */
    private static final int RETRIES = 4;
    private static final long STALL_MS = 15_000L;
    /** A copy stepped to that hasn't started this soon is taken as failed (the next copy is tried). */
    private static final long STEP_MS = 8_000L;
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
    /** The player's sound, heard by automatic subtitles while they listen (LiveSubs); one player at a time. */
    static final AudioTap TAP = new AudioTap();

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
    /** Subtitles over the full-screen picture: the channel's own, or made on this TV (Automatic). */
    final LiveSubs subs;
    private ExoPlayer player;
    private Channel cur;
    private int retries, state = IDLE;
    private boolean asHls;
    private long bufferingSince;
    private int videoW, videoH, rawW, rawH;
    /** This stream is the provider's HLS form of an MPEG-TS address (Adaptive), not yet seen playing; or it failed. */
    private boolean hlsTried, hlsRefused, everReady;
    /** Settings → Player → Match frame rate: this stream's rate being read, while it is full screen. */
    private Afr.Probe probe;
    /** Catch-up: the archive playing (null: the live stream), moved back in the live stream's own window, paused. */
    private Archive arc;
    private boolean inWindow, held;
    /**
     * Settings → Player → Picture quality: the channel's versions (Ladder; the channel alone until they are found, off
     * the main thread) and the one playing. Null behind live (the archive is the archive's).
     */
    private Ladder ladder;
    private int ladderAsked;
    /** A step between the channel's versions under way: the channel stays on screen, its last picture held, no spinner. */
    private boolean stepping;
    private long steppedAt;
    /** The buffer, a second apart (oldest first), while it plays: a steady drain steps down before the picture freezes. */
    private final long[] buffered = new long[5];
    private int bufferedN;
    /** The stalls while it played (their starts, newest last), the one going on, failed tries up, a try up under way. */
    private final long[] stalls = new long[6];
    private int stallCount, failedUps;
    private long stallSince, upTriedAt;

    private final Runnable retry = this::prepare;
    private final Runnable bufferTick = this::bufferTick;
    private final Runnable upCheck = this::tryUp;
    private final Runnable stallStep = this::stallStep;
    private final Runnable stallCheck = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            long limit = stepping ? STEP_MS : STALL_MS;
            if ((state == LOADING || stepping) && !held && bufferingSince > 0 && SystemClock.elapsedRealtime() - bufferingSince > limit) {
                // The provider's HLS form that never started: its MPEG-TS (only this channel; a slow start says
                // nothing of the playlist). Else the stream failed.
                if (arc == null && hlsTried && !everReady) refuseHls(false);
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
        note.setTypeface(Dark.outfit);
        note.setTextColor(Dark.TEXT);
        note.setGravity(Gravity.CENTER);
        note.setVisibility(View.GONE);
        layer.addView(note, new FrameLayout.LayoutParams(1, 1));
        subs = new LiveSubs(c, layer);
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
            subs.frame(where, 0, 0, 0, 0);
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
        subs.frame(where, x, y, w, hh);
    }

    Channel channel() {
        return cur;
    }

    int state() {
        return state;
    }

    /**
     * The picture as the player has it, for the banner's chips: its quality badge ("4K", "FHD", "HD", "SD": the one
     * thing that changes when Adaptive steps between the channel's versions), its size ("1280×720") and frames a second
     * ("25 fps"), each "" when the player doesn't know it (or nothing is playing).
     */
    String[] format() {
        String[] f = chips();
        int hh = f.length > 3 ? Integer.parseInt(f[3]) : 0;
        Channel s = stream();
        int level = ladder != null ? ladder.level() : s == null ? -1 : Names.info(s.name, s.group).quality;
        return new String[]{s == null ? "" : Ladder.badge(hh, level), f[0], f[1]};
    }

    /** The picture's size and frames a second as chips, and its height ("0" when unknown). */
    private String[] chips() {
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
        return new String[]{LiveText.size(w, hh), LiveText.fps(fps), "", String.valueOf(Math.max(0, hh))};
    }

    /**
     * The stream under the channel: the channel picked, or (Picture quality) one of its versions or their copies, which
     * plays as the channel picked — the screens only ever show channel().
     */
    Channel stream() {
        if (cur == null) return null;
        return ladder != null ? ladder.stream() : cur;
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
        if (!same) subs.channelChanged();
        cur = c;
        retries = 0;
        versions(c);
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
        h.removeCallbacks(bufferTick);
        h.removeCallbacks(upCheck);
        h.removeCallbacks(stallStep);
        stepDone();
        stallSince = 0;
        probe = null;
        Afr.reset(layer.getContext() instanceof android.app.Activity ? (android.app.Activity) layer.getContext() : null);
        if (arc != null && player != null) {
            // Picked up again where it got to.
            arc.from = Catchup.requestAt(kind(arc.channel), shownAt(), 0, 0);
            arc.played = false;
        }
        inWindow = false;
        subs.detach();
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
        hlsRefused = false;
        // Asked to try again: the channel's versions afresh, as when it was picked.
        if (arc == null) versions(cur);
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
            ladder = null;
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
        // As Settings → Player and Sound say (PlayerSetup): the buffer (Normal starts a channel as soon as a second is
        // in, two after a stall, as Homefield came), the TV's own decoders first with FFmpeg for the sound it can't play
        // (AC-3, E-AC-3, MP2), the sound's way out. Automatic subtitles hear a copy while they listen (TAP, after the
        // mixing down to two speakers when that's on).
        player = PlayerSetup.builder(app, true, TAP).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.addListener(listener);
        if (picture instanceof SurfaceView) player.setVideoSurfaceView((SurfaceView) picture);
        subs.attach(player);
    }

    private void prepare() {
        if (player == null || cur == null) return;
        h.removeCallbacks(stallCheck);
        h.removeCallbacks(retry);
        everReady = false;
        hlsTried = false;
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
            player.setMediaSource(Streams.source(url, PlayerPrefs.agent(app, s.key, s.userAgent), s.referrer,
                    asHls || Streams.looksHls(url)));
        } else {
            Channel s = stream();
            // The provider's HLS form of an MPEG-TS channel first, so a drop is ridden out on its segments and the
            // picture can adapt; MPEG-TS again where it doesn't play (Adaptive, Settings → Stream problems).
            String url = s.url;
            boolean hls = asHls;
            String hlsUrl = hlsRefused || !PlayerPrefs.hlsFirst(app) || Adaptive.hlsFailed(app, PlayerPrefs.playlistOf(s.key))
                    ? null : Adaptive.hlsFor(s.url);
            hlsTried = hlsUrl != null;
            if (hlsTried) {
                url = hlsUrl;
                hls = true;
            }
            // A playlist's own user agent (Settings → Stream problems) over the channel's.
            player.setMediaSource(Streams.source(url, PlayerPrefs.agent(app, s.key, s.userAgent), s.referrer, hls));
        }
        player.prepare();
        player.setPlayWhenReady(!held);
        bufferingSince = SystemClock.elapsedRealtime();
        // A step between the channel's versions keeps it playing on screen (its last picture held): no spinner.
        if (!stepping) set(LOADING);
        h.postDelayed(stallCheck, 2000);
        h.removeCallbacks(bufferTick);
        if (arc == null) h.postDelayed(bufferTick, 1000);
        // A new stream: its frame rate read again.
        if (probe != null) player.clearVideoFrameMetadataListener(probe);
        probe = null;
        frameRate();
    }

    // ---- Picture quality: the channel's versions -----------------------------------------------------------------

    /**
     * The channel's versions for Settings → Player → Picture quality: the channel alone at once (its copies in the other
     * playlists behind it), then all of them, found off the main thread from the playlists; a fixed quality or Data
     * saver goes on to its version when that isn't the one picked.
     */
    private void versions(Channel c) {
        stepDone();
        h.removeCallbacks(upCheck);
        h.removeCallbacks(stallStep);
        stallCount = 0;
        stallSince = 0;
        failedUps = 0;
        upTriedAt = 0;
        bufferedN = 0;
        steppedAt = SystemClock.elapsedRealtime();
        ladder = Ladder.single(c);
        if (Exec.inline) return;
        final int asked = ++ladderAsked;
        final Set<String> favourites = new HashSet<>(Library.get(app).favourites());
        Exec.io(() -> {
            // Every copy, in hidden categories too: they are the same channel's quality levels. Only the channels whose
            // names read as this one's are looked at (the library's index of names), not every channel at each change.
            List<Channel> found = Ladder.variants(Library.get(app).named(c.name), c, favourites);
            Exec.main(() -> {
                if (asked != ladderAsked || ladder == null || cur == null || !cur.key.equals(c.key) || arc != null
                        || found.size() <= 1) return;
                Ladder was = ladder, now = Ladder.of(found, c, PlayerPrefs.quality(app));
                int start = now.rung();
                // Already moved on to another playlist's copy, or connecting again: the stream it is on stays.
                if (was.alt() > 0 || retries > 0 || start == now.picked()) {
                    now.at(now.picked(), was.alt());
                    ladder = now;
                    return;
                }
                ladder = now;
                now.at(start, 0);
                hlsRefused = false;
                asHls = Streams.looksHls(stream().url);
                step();
            });
        });
    }

    /** Adaptive (or Data saver): the picture follows the connection through the channel's versions, live, as it plays on. */
    private boolean adaptive() {
        return ladder != null && arc == null && !inWindow && !held && PlayerPrefs.adaptive(PlayerPrefs.quality(app));
    }

    /**
     * A step to another of the channel's versions (down when it can't keep up, back up once steady): the channel stays
     * as it is on screen, its last picture held on the SurfaceView while the stream opens (it has no shutter), with no
     * spinner and no message.
     */
    private void step() {
        stepping = true;
        steppedAt = SystemClock.elapsedRealtime();
        bufferedN = 0;
        retries = 0;
        prepare();
    }

    private void stepDone() {
        stepping = false;
    }

    /** Each second while it plays: the buffer draining steadily (the connection falling behind) steps down at once. */
    private void bufferTick() {
        h.removeCallbacks(bufferTick);
        if (player == null || cur == null || arc != null) return;
        h.postDelayed(bufferTick, 1000);
        if (state != PLAYING || stepping || !adaptive() || !ladder.canDown() || !player.getPlayWhenReady()
                || SystemClock.elapsedRealtime() - steppedAt < Abr.SETTLE_MS) {
            bufferedN = 0;
            return;
        }
        System.arraycopy(buffered, 1, buffered, 0, buffered.length - 1);
        buffered[buffered.length - 1] = player.getTotalBufferedDuration();
        bufferedN = Math.min(buffered.length, bufferedN + 1);
        if (bufferedN == buffered.length && Abr.draining(buffered)) {
            bufferedN = 0;
            down();
        }
    }

    private void stallBegan() {
        long now = System.currentTimeMillis();
        stallSince = now;
        System.arraycopy(stalls, 1, stalls, 0, stalls.length - 1);
        stalls[stalls.length - 1] = now;
        stallCount = Math.min(stalls.length, stallCount + 1);
        h.removeCallbacks(stallStep);
        h.postDelayed(stallStep, Abr.STALL_STEP_MS);
    }

    private void stallEnded() {
        h.removeCallbacks(stallStep);
        long now = System.currentTimeMillis(), longest = stallSince > 0 ? now - stallSince : 0;
        stallSince = 0;
        long[] recent = Arrays.copyOfRange(stalls, stalls.length - stallCount, stalls.length);
        if (adaptive() && Abr.struggling(recent, recent.length, now, longest)) down();
    }

    /** Still stalled after 5 s: down a step now rather than wait. */
    private void stallStep() {
        if (stallSince > 0 && adaptive()) down();
    }

    private void down() {
        if (ladder == null || !ladder.canDown()) return;
        boolean wasTry = upTriedAt > 0 && System.currentTimeMillis() - upTriedAt < Abr.TRY_HOLDS_MS;
        if (wasTry) failedUps++;
        upTriedAt = 0;
        ladder.down();
        stallCount = 0;
        hlsRefused = false;
        asHls = Streams.looksHls(stream().url);
        // No message: the channel stays as it is on screen, only its picture steps down (and back up once steady).
        step();
        h.removeCallbacks(upCheck);
        h.postDelayed(upCheck, Abr.upAfter(failedUps));
    }

    /** Steady for a while below the best allowed: one step back up (back down at once if it stalls). */
    private void tryUp() {
        if (!adaptive() || !ladder.canUp()) return;
        long now = System.currentTimeMillis();
        long last = stallCount > 0 ? stalls[stalls.length - 1] : 0;
        if (state != PLAYING || now - last < Abr.upAfter(failedUps)) {
            long wait = state != PLAYING ? 10_000L : Abr.upAfter(failedUps) - (now - last);
            h.postDelayed(upCheck, wait);
            return;
        }
        ladder.up();
        stallCount = 0;
        upTriedAt = now;
        hlsRefused = false;
        asHls = Streams.looksHls(stream().url);
        step();
        if (ladder.canUp()) h.postDelayed(upCheck, Abr.upAfter(failedUps));
    }

    /**
     * Dropped. Adaptive (the default), the channel staying on screen throughout, as the phone does: its stream tried
     * again at once twice, then the same version from the next playlist, then the version a step down, then tried
     * again 1, 2, 4, 8, then 10 s apart for as long as Settings → Stream problems say. A fixed quality: the exact stream
     * tried again, the same way, never another copy.
     */
    private void dropped() {
        if (cur == null) return;
        h.removeCallbacks(retry);
        if (arc != null) {
            archiveFailed(null);
            return;
        }
        stepDone();
        if (ladder != null && PlayerPrefs.adaptive(PlayerPrefs.quality(app))) {
            if (retries < Abr.QUICK_TRIES) {
                retries++;
                set(RETRYING);
                h.postDelayed(retry, Abr.retryDelay(retries, false));
                return;
            }
            if (ladder.nextAlt()) {
                retries = 0;
                hlsRefused = false;
                asHls = Streams.looksHls(stream().url);
                set(RETRYING);
                prepare();
                return;
            }
            if (ladder.canDown()) {
                ladder.down();
                retries = 0;
                hlsRefused = false;
                asHls = Streams.looksHls(stream().url);
                set(RETRYING);
                prepare();
                h.removeCallbacks(upCheck);
                h.postDelayed(upCheck, Abr.upAfter(failedUps));
                return;
            }
        }
        int tries = PlayerPrefs.retries(app);
        if (tries < 0 || retries < tries) reconnect();
        else set(FAILED);
    }

    /** This channel back on its MPEG-TS address (the HLS form didn't play); the playlist's HLS skipped a while too if so. */
    private void refuseHls(boolean forPlaylist) {
        Channel s = stream();
        if (forPlaylist && s != null) Adaptive.markHlsFailed(app, PlayerPrefs.playlistOf(s.key));
        hlsRefused = true;
        prepare();
    }

    /** The same stream connected again after a wait (1, 2, 4, 8, then 10 s), "Reconnecting…" on the picture meanwhile. */
    private void reconnect() {
        retries++;
        set(RETRYING);
        h.postDelayed(retry, Abr.retryDelay(retries, true));
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
                everReady = true;
                stepDone();
                if (stallSince > 0) stallEnded();
                if (arc != null) {
                    arc.played = true;
                    arc.retries = 0;
                }
                set(PLAYING);
            } else if (s == Player.STATE_BUFFERING) {
                bufferingSince = SystemClock.elapsedRealtime();
                // A step between the channel's versions opening is not a stall: the channel plays on, its picture held.
                if (state == PLAYING && !stepping) {
                    if (arc == null && !inWindow && !held) stallBegan();
                    set(LOADING);
                }
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

        @Override public void onIsPlayingChanged(boolean playing) {
            // Paused or played on behind live: the bar and its knob drawn again.
            if (behind()) set(state);
        }

        @Override public void onTracksChanged(@NonNull Tracks tracks) {
            // Whether the channel sends subtitles of its own: shown, or listened for (LiveSubs).
            if (player != null) subs.tracksChanged();
        }

        @Override public void onCues(@NonNull CueGroup cues) {
            subs.cues(cues.cues);
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
