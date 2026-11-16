package com.gathin.homefieldtv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

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

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Streams;

/**
 * A film or an episode from the family's own playlist, full screen, with the guide's playback bar: the title, a thin
 * line with the play knob where it is, the time gone under it, the length at the end, the day and time. OK pauses and
 * plays, Left and Right go back and on (further the longer they are held), Back stops and keeps the place. At the end
 * of an episode the next one starts. The address is made from the login here and never kept or logged.
 */
@OptIn(markerClass = UnstableApi.class)
public final class VodActivity extends Activity {
    static final String TITLE = "title", EPISODE = "episode", START = "start";
    private static final long BAR_MS = 4500;

    private ExoPlayer player;
    private SurfaceView surface;
    private Bar bar;
    private final Handler h = new Handler(Looper.getMainLooper());
    private Catalog.Title title;
    private Catalog.Episode episode;
    private Catalog.Details details;
    private Catalog.Play play;
    private int backup;
    private boolean asHls, barOn = true, ended, ready, nextQueued;
    /** Previews: where the bar shows it is, as no stream plays there. */
    static long previewPos, previewDur;
    static boolean previewPlaying = true;
    private long startAt, seekTo = -1, seekStarted, lastSave;
    private int seekSteps;
    private String error = "";
    private long videoAspectW, videoAspectH;

    private final Runnable hideBar = () -> {
        barOn = false;
        bar.invalidate();
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            bar.invalidate();
            if (player != null && player.isPlaying() && SystemClock.elapsedRealtime() - lastSave > 15_000) save();
            h.postDelayed(this, 500);
        }
    };
    private final Runnable applySeek = this::applySeek;
    private final Runnable nextEpisode = () -> {
        nextQueued = false;
        playNext();
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        Theme.apply(this);
        Pics.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (Exec.inline && LiveTv.previewStill != null) {
            android.widget.ImageView still = new android.widget.ImageView(this);
            still.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            still.setImageBitmap(LiveTv.previewStill);
            root.addView(still, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        bar = new Bar(this);
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitSurface());
        setContentView(root);
        startAt = getIntent().getLongExtra(START, 0);
        open(getIntent().getStringExtra(TITLE), getIntent().getStringExtra(EPISODE));
    }

    /** The title found (the catalogue read first if the app was started afresh), its details for an episode, then play. */
    private void open(String key, String episodeId) {
        Catalog cat = Catalog.get(this);
        title = cat.title(key);
        if (title == null) {
            if (!cat.ready()) {
                error = "Finding it…";
                cat.addListener(new Runnable() {
                    @Override public void run() {
                        if (cat.title(key) == null) return;
                        cat.removeListener(this);
                        open(key, episodeId);
                    }
                });
                cat.ensure();
            } else {
                error = "That isn't in your playlist any more.";
            }
            bar.invalidate();
            return;
        }
        if (episodeId == null || episodeId.isEmpty()) {
            start();
            return;
        }
        cat.details(title, d -> {
            details = d;
            episode = d == null ? null : d.episode(episodeId);
            if (episode == null) {
                error = "That episode isn't in your playlist any more.";
                bar.invalidate();
                return;
            }
            start();
        });
    }

    private void start() {
        Catalog cat = Catalog.get(this);
        play = episode == null ? cat.play(title) : cat.play(title, episode);
        if (play == null) {
            error = "Its playlist has gone from this TV.";
            bar.invalidate();
            return;
        }
        if (details == null) details = cat.cachedDetails(title);
        backup = 0;
        error = "";
        ended = false;
        ready = false;
        prepare(true);
    }

    private Catalog.Play current() {
        return backup > 0 && backup <= play.backups.size() ? play.backups.get(backup - 1) : play;
    }

    private void prepare(boolean fresh) {
        if (Exec.inline) {
            ready = true;
            showBar();
            return;
        }
        if (player == null) {
            DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true);
            DefaultLoadControl load = new DefaultLoadControl.Builder().setBufferDurationsMs(20_000, 60_000, 1_500, 3_000).build();
            player = new ExoPlayer.Builder(this, renderers).setLoadControl(load)
                    .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true);
            player.setVideoSurfaceView(surface);
            player.addListener(listener);
        }
        Catalog.Play p = current();
        if (fresh) asHls = Streams.looksHls(p.url);
        long at = player.getCurrentPosition() > 0 && !fresh ? player.getCurrentPosition() : startAt;
        player.setMediaSource(Streams.source(p.url, p.userAgent, p.referrer, asHls), at);
        player.prepare();
        player.setPlayWhenReady(true);
        showBar();
    }

    @Override protected void onResume() {
        super.onResume();
        Cast.hold(this);
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        Cast.release();
        h.removeCallbacks(tick);
        save();
        if (player != null) player.pause();
    }

    @Override protected void onStop() {
        super.onStop();
        save();
        release();
        finish();
    }

    @Override protected void onDestroy() {
        release();
        super.onDestroy();
    }

    private void release() {
        h.removeCallbacksAndMessages(null);
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
        }
    }

    /** Where it was left, for Continue watching and Resume. */
    private void save() {
        if (player == null || title == null || !ready) return;
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur <= 0) return;
        lastSave = SystemClock.elapsedRealtime();
        Resume.save(this, point(pos, dur));
    }

    private Resume.Point point(long pos, long dur) {
        String picture = episode != null && !episode.still.isEmpty() ? episode.still
                : details != null && !details.backdrop.isEmpty() ? details.backdrop : title.backdrop;
        String subtitle = episode == null ? "" : "S" + episode.season + " E" + episode.number + (episode.title.isEmpty() ? "" : "  ·  " + episode.title);
        return new Resume.Point(title.key, episode == null ? "" : episode.id, title.name, subtitle, picture, pos, dur, System.currentTimeMillis());
    }

    private long position() {
        return Exec.inline ? previewPos : player == null ? 0 : player.getCurrentPosition();
    }

    private long duration() {
        return Exec.inline ? previewDur : player == null ? C.TIME_UNSET : player.getDuration();
    }

    private boolean playing() {
        return Exec.inline ? previewPlaying : player != null && player.isPlaying();
    }

    private void fitSurface() {
        View root = (View) surface.getParent();
        int w = root.getWidth(), hh = root.getHeight();
        if (w == 0 || hh == 0) return;
        int sw = w, sh = hh;
        if (videoAspectW > 0 && videoAspectH > 0) {
            float va = videoAspectW / (float) videoAspectH, ra = w / (float) hh;
            if (va > ra) sh = Math.round(w / va);
            else sw = Math.round(hh * va);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surface.getLayoutParams();
        if (lp.width != sw || lp.height != sh) {
            lp.width = sw;
            lp.height = sh;
            lp.gravity = android.view.Gravity.CENTER;
            surface.setLayoutParams(lp);
        }
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    private void showBar() {
        barOn = true;
        h.removeCallbacks(hideBar);
        if (player != null && player.isPlaying() && !Exec.inline) h.postDelayed(hideBar, BAR_MS);
        bar.invalidate();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_UP) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MEDIA_REWIND
                    || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                h.removeCallbacks(applySeek);
                h.postDelayed(applySeek, 450);
                return true;
            }
            return code == KeyEvent.KEYCODE_BACK || isOk(code) || super.dispatchKeyEvent(e);
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (e.getRepeatCount() > 0) return true;
                h.removeCallbacks(nextEpisode);
                nextQueued = false;
                save();
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (e.getRepeatCount() > 0) return true;
                if (ended && nextQueued) {
                    h.removeCallbacks(nextEpisode);
                    nextQueued = false;
                    playNext();
                    return true;
                }
                if (player == null) {
                    if (!error.isEmpty() && play != null) start();
                    return true;
                }
                if (seekTo >= 0) {
                    applySeek();
                    return true;
                }
                if (player.isPlaying()) {
                    player.pause();
                    save();
                } else {
                    player.play();
                }
                showBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (player != null) player.play();
                showBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (player != null) player.pause();
                showBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seek(-1, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seek(1, e.getRepeatCount());
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
                if (barOn && player != null && player.isPlaying()) hideBar.run();
                else showBar();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    private static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_SPACE;
    }

    /** Back or on: 10 s a press, more the longer it is held; the jump made once the key is let go. */
    private void seek(int dir, int repeat) {
        if (player == null) return;
        long dur = player.getDuration();
        long from = seekTo >= 0 ? seekTo : player.getCurrentPosition();
        if (repeat == 0 && seekTo < 0) seekSteps = 0;
        seekSteps++;
        long step = seekSteps < 6 ? 10_000 : seekSteps < 16 ? 30_000 : seekSteps < 30 ? 60_000 : 300_000;
        long to = from + dir * step;
        if (to < 0) to = 0;
        if (dur > 0 && dur != C.TIME_UNSET && to > dur - 2000) to = dur - 2000;
        seekTo = to;
        seekStarted = SystemClock.elapsedRealtime();
        h.removeCallbacks(applySeek);
        h.postDelayed(applySeek, 1200);
        showBar();
    }

    private void applySeek() {
        h.removeCallbacks(applySeek);
        if (player != null && seekTo >= 0) player.seekTo(seekTo);
        seekTo = -1;
        seekSteps = 0;
        showBar();
    }

    private void playNext() {
        if (details == null || episode == null) {
            finish();
            return;
        }
        Catalog.Episode next = details.after(episode);
        if (next == null) {
            finish();
            return;
        }
        episode = next;
        startAt = 0;
        if (player != null) player.stop();
        start();
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (s == Player.STATE_READY) {
                ready = true;
                error = "";
            } else if (s == Player.STATE_ENDED) {
                ended = true;
                // Watched to the end: off Continue watching, the next episode on it instead.
                if (title != null) {
                    Resume.save(VodActivity.this, point(Long.MAX_VALUE / 4, Long.MAX_VALUE / 4));
                    Catalog.Episode next = details == null || episode == null ? null : details.after(episode);
                    if (next != null) {
                        String pic = !next.still.isEmpty() ? next.still : details.backdrop;
                        Resume.upNext(VodActivity.this, new Resume.Point(title.key, next.id, title.name,
                                "S" + next.season + " E" + next.number + (next.title.isEmpty() ? "" : "  ·  " + next.title), pic, 0,
                                next.minutes * 60_000L, System.currentTimeMillis()));
                        nextQueued = true;
                        h.postDelayed(nextEpisode, 10_000);
                    } else {
                        h.postDelayed(VodActivity.this::finish, 1500);
                    }
                }
            }
            showBar();
        }

        @Override public void onIsPlayingChanged(boolean playing) {
            showBar();
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            // Only the kind of error: the address (with the login in it) is never written out.
            android.util.Log.w("HomefieldTv", "film: " + e.getErrorCodeName());
            if (player == null) return;
            if (!asHls && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                    || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                asHls = true;
                prepare(false);
                return;
            }
            if (play != null && backup < play.backups.size()) {
                backup++;
                prepare(true);
                return;
            }
            error = e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                    ? "Your provider didn't send it. OK to try again." : "It didn't play. OK to try again.";
            release();
            bar.invalidate();
        }

        @Override public void onVideoSizeChanged(@NonNull VideoSize size) {
            if (size.width <= 0 || size.height <= 0) return;
            videoAspectW = Math.round(size.width * size.pixelWidthHeightRatio);
            videoAspectH = size.height;
            fitSurface();
        }
    };

    // ---- the playback bar ---------------------------------------------------------------------------------------

    private final class Bar extends View {
        private final Paint shade = new Paint(), line = Ui.fill(0x8CFFFFFF), done = Ui.fill(0xE6FFFFFF);
        private final Paint knobRing = Ui.fill(Ui.tint(0xFF1F7FD4)), knob = Ui.fill(Ui.tint(0xFF35B6F7)), knobEdge = Ui.stroke(0x99FFFFFF, 2f);
        private final Paint icon = Ui.fill(Ui.WHITE), cardBack = Ui.fill(Ui.tint(0xE6062A66));
        private final TextPaint titleP = Ui.paint(32, true, Ui.WHITE), endP = Ui.paint(28, false, Ui.WHITE);
        private final TextPaint goneP = Ui.paint(28, true, Ui.CYAN), dayP = Ui.paint(28, false, Ui.DIM), msgP = Ui.paint(34, false, Ui.WHITE);
        private final TextPaint nextP = Ui.paint(30, false, Ui.WHITE), nextSmall = Ui.paint(24, false, Ui.DIM);
        private final RectF r = new RectF();
        private final android.graphics.Path tri = new android.graphics.Path();
        private float shadeFor = -1;

        Bar(Context c) {
            super(c);
            endP.setShadowLayer(Ui.f(3), 0, 0, 0x99000000);
            titleP.setShadowLayer(Ui.f(3), 0, 0, 0x99000000);
            goneP.setShadowLayer(Ui.f(3), 0, 0, 0x99000000);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            if (!error.isEmpty()) {
                String m = error;
                c.drawText(m, (w - msgP.measureText(m)) / 2f, hh / 2f, msgP);
            }
            if (ended && nextQueued && details != null && episode != null) {
                Catalog.Episode next = details.after(episode);
                if (next != null) {
                    r.set(Ui.f(1180), Ui.f(780), Ui.f(1860), Ui.f(960));
                    c.drawRoundRect(r, Ui.f(6), Ui.f(6), cardBack);
                    c.drawText("Up next  ·  OK to start it now", r.left + Ui.f(28), r.top + Ui.f(52), nextSmall);
                    c.drawText(Ui.fit("S" + next.season + " E" + next.number + (next.title.isEmpty() ? "" : "  " + next.title), nextP,
                            r.width() - Ui.f(56)), r.left + Ui.f(28), r.top + Ui.f(104), nextP);
                    c.drawText("Starts in a few seconds", r.left + Ui.f(28), r.top + Ui.f(148), nextSmall);
                }
            }
            if (!barOn || player == null && !Exec.inline) return;
            if (shadeFor != hh) {
                shade.setShader(new LinearGradient(0, hh * 0.62f, 0, hh, 0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
                shadeFor = hh;
            }
            c.drawRect(0, hh * 0.62f, w, hh, shade);
            String name = title == null ? "" : title.name + (episode == null ? "" : "  ·  S" + episode.season + " E" + episode.number
                    + (episode.title.isEmpty() ? "" : "  " + episode.title));
            name = Ui.fit(name, titleP, Ui.f(1400));
            c.drawText(name, (w - titleP.measureText(name)) / 2f, Ui.f(860), titleP);

            long dur = duration();
            long pos = seekTo >= 0 ? seekTo : position();
            boolean known = dur > 0 && dur != C.TIME_UNSET;
            float x0 = Ui.f(300), x1 = Ui.f(1620), y = Ui.f(913);
            c.drawText("0m", x0 - Ui.f(26) - endP.measureText("0m"), y + Ui.f(10), endP);
            if (known) c.drawText(length(dur), x1 + Ui.f(26), y + Ui.f(10), endP);
            c.drawRect(x0, y - Ui.f(2), x1, y + Ui.f(2), line);
            float p = known ? Math.max(0f, Math.min(1f, pos / (float) dur)) : 0f;
            float kx = x0 + (x1 - x0) * p;
            c.drawRect(x0, y - Ui.f(2), kx, y + Ui.f(2), done);
            // The knob: play or pause in it, as the player is now.
            c.drawCircle(kx, y, Ui.f(34), knobRing);
            c.drawCircle(kx, y, Ui.f(26), knob);
            c.drawCircle(kx, y, Ui.f(34), knobEdge);
            if (playing() && seekTo < 0) {
                c.drawRect(kx - Ui.f(9), y - Ui.f(11), kx - Ui.f(3), y + Ui.f(11), icon);
                c.drawRect(kx + Ui.f(3), y - Ui.f(11), kx + Ui.f(9), y + Ui.f(11), icon);
            } else {
                tri.reset();
                tri.moveTo(kx - Ui.f(7), y - Ui.f(12));
                tri.lineTo(kx + Ui.f(12), y);
                tri.lineTo(kx - Ui.f(7), y + Ui.f(12));
                tri.close();
                c.drawPath(tri, icon);
            }
            String gone = gone(pos);
            c.drawText(gone, kx - goneP.measureText(gone) / 2f, y + Ui.f(76), goneP);
            c.drawText(Ui.dayClock(System.currentTimeMillis()), Ui.f(90), Ui.f(1030), dayP);
            if (known && !playing()) {
                String end = "Ends " + Ui.clock(System.currentTimeMillis() + (dur - pos));
                c.drawText(end, w - Ui.f(90) - dayP.measureText(end), Ui.f(1030), dayP);
            }
        }

        /** "2h 3m". */
        private String length(long ms) {
            long m = Math.round(ms / 60_000.0);
            return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + "m";
        }

        /** "1s", "12m", "1h 4m" under the knob. */
        private String gone(long ms) {
            long s = ms / 1000;
            if (s < 60) return s + "s";
            long m = s / 60;
            return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + "m";
        }
    }

}
