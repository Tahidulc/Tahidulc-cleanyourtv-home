package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.gathin.tvhomephone.R;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Live TV's player, full screen: one of the family's own channels (HLS or MPEG-TS), the guide's now and next over it
 * for a few seconds on each change or tap, swipe up or down for the next or previous channel in the list it was opened
 * from, picture in picture on leaving, the phone's audio focus, the screen kept on, and a few retries when the stream
 * drops. Landscape on phones; any way round on tablets. The stream's address is never logged (it can hold the login).
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    static final String EXTRA_KEY = "live_key", EXTRA_GROUP = "live_group";
    private static final int RETRIES = 5;
    private static final long OVERLAY_MS = 5000;

    private final Handler h = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private PlayerView video;
    private LinearLayout overlay, buttons;
    private TextView name, group, nowLine, nextLine, message;
    private ProgressBar spinner, progress;
    private ImageButton star;
    private List<Channel> order;
    private int pos;
    private Channel cur;
    private int retries;
    private boolean asHls;
    private final Runnable hide = this::hideOverlay;
    private final Runnable retry = () -> play(false);

    /** Opens the player on a channel (by key), swiping through the given group's list (Library.ALL for all). */
    public static void start(Context c, String key, String group) {
        Intent i = new Intent(c, PlayerActivity.class).putExtra(EXTRA_KEY, key).putExtra(EXTRA_GROUP, group == null ? Library.ALL : group);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Phones turn on their side; tablets play whichever way they are held.
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        setContentView(build());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (!take(getIntent())) finish();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (take(intent) && player != null) play(true);
    }

    /** The channel and its list from the intent; false when the channel is gone from the playlist. */
    private boolean take(Intent i) {
        Library lib = Library.get(this);
        Channel c = lib.byKey(i.getStringExtra(EXTRA_KEY));
        if (c == null) {
            Toast.makeText(this, R.string.live_gone, Toast.LENGTH_LONG).show();
            return false;
        }
        String g = i.getStringExtra(EXTRA_GROUP);
        order = lib.list(g == null ? Library.ALL : g, "");
        if (!order.contains(c)) order = lib.list(Library.ALL, "");
        pos = Math.max(0, order.indexOf(c));
        cur = c;
        return true;
    }

    @Override protected void onStart() {
        super.onStart();
        if (player == null && cur != null) {
            player = new ExoPlayer.Builder(this).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true);
            player.addListener(listener);
            video.setPlayer(player);
            play(true);
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (!isInPictureInPictureMode()) release();
    }

    @Override protected void onDestroy() {
        release();
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void release() {
        h.removeCallbacks(retry);
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
            video.setPlayer(null);
        }
    }

    // ---- playing ----------------------------------------------------------------------------------------------

    /** Plays the current channel; fresh: a new channel (its retries and format guess start again). */
    private void play(boolean fresh) {
        if (player == null || cur == null) return;
        h.removeCallbacks(retry);
        if (fresh) {
            retries = 0;
            asHls = cur.url.toLowerCase(Locale.ROOT).contains(".m3u8");
            Picks.watched(this, cur.key);
            showOverlay();
        }
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setUserAgent(cur.userAgent.isEmpty() ? Fetch.AGENT : cur.userAgent);
        if (!cur.referrer.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", cur.referrer);
            http.setDefaultRequestProperties(headers);
        }
        MediaItem.Builder item = new MediaItem.Builder().setUri(cur.url);
        if (asHls) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(item.build()));
        player.prepare();
        player.setPlayWhenReady(true);
        message.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (s == Player.STATE_READY) {
                retries = 0;
                spinner.setVisibility(View.GONE);
                message.setVisibility(View.GONE);
            } else if (s == Player.STATE_BUFFERING) {
                spinner.setVisibility(View.VISIBLE);
            }
        }

        @Override public void onPlayerError(PlaybackException e) {
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
                play(false);
                return;
            }
            if (retries < RETRIES) {
                retries++;
                message.setText(R.string.live_reconnecting);
                message.setVisibility(View.VISIBLE);
                h.postDelayed(retry, Math.min(8000, 1000L << (retries - 1)));
            } else {
                spinner.setVisibility(View.GONE);
                message.setText(R.string.live_not_playing);
                message.setVisibility(View.VISIBLE);
            }
        }

        @Override public void onVideoSizeChanged(VideoSize size) {
            if (Build.VERSION.SDK_INT >= 26 && pipSupported()) setPictureInPictureParams(pipParams());
        }
    };

    private void step(int by) {
        if (order == null || order.isEmpty()) return;
        pos = ((pos + by) % order.size() + order.size()) % order.size();
        cur = order.get(pos);
        play(true);
    }

    // ---- picture in picture -----------------------------------------------------------------------------------

    private boolean pipSupported() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private PictureInPictureParams pipParams() {
        Rational r = new Rational(16, 9);
        if (player != null) {
            VideoSize v = player.getVideoSize();
            if (v.width > 0 && v.height > 0) {
                float ratio = v.width * v.pixelWidthHeightRatio / v.height;
                if (ratio > 0.42f && ratio < 2.38f) r = new Rational(Math.round(v.width * v.pixelWidthHeightRatio), v.height);
            }
        }
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder().setAspectRatio(r);
        // Android 12 and later: into the small window on its own as the app is left.
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(true);
        return b.build();
    }

    private void enterPip() {
        if (!pipSupported() || player == null) return;
        try {
            enterPictureInPictureMode(pipParams());
        } catch (IllegalStateException e) {
            // Not allowed here (a TV, or turned off for the app): it just keeps playing full screen.
        }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT < 31 && player != null && player.isPlaying()) enterPip();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (inPip) {
            hideOverlay();
            message.setVisibility(View.GONE);
        } else if (getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
            // The small window was closed.
            finish();
        }
    }

    // ---- the overlay ------------------------------------------------------------------------------------------

    private void showOverlay() {
        if (cur == null || isInPictureInPictureMode()) return;
        name.setText(cur.number > 0 ? cur.number + "  " + cur.name : cur.name);
        group.setText(cur.group);
        group.setVisibility(cur.group.isEmpty() ? View.GONE : View.VISIBLE);
        star.setImageResource(Picks.isFavourite(this, cur.key) ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
        Guide.Schedule s = Library.get(this).schedule(cur.key);
        long now = System.currentTimeMillis();
        int at = s == null ? -1 : s.at(now), next = s == null ? -1 : s.after(now);
        if (at >= 0) {
            nowLine.setText(getString(R.string.live_now) + "  " + s.title[at] + "  ·  " + Ui.clock(s.start[at]) + "–" + Ui.clock(s.stop[at]));
            progress.setVisibility(View.VISIBLE);
            progress.setProgress((int) (1000 * (now - s.start[at]) / Math.max(1, s.stop[at] - s.start[at])));
        } else {
            nowLine.setText(R.string.live_no_guide);
            progress.setVisibility(View.GONE);
        }
        if (next >= 0) {
            nextLine.setText(getString(R.string.live_next) + "  " + Ui.clock(s.start[next]) + "  " + s.title[next]);
            nextLine.setVisibility(View.VISIBLE);
        } else nextLine.setVisibility(View.GONE);
        overlay.setVisibility(View.VISIBLE);
        buttons.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(160).start();
        buttons.animate().alpha(1f).setDuration(160).start();
        h.removeCallbacks(hide);
        h.postDelayed(hide, OVERLAY_MS);
    }

    private void hideOverlay() {
        h.removeCallbacks(hide);
        overlay.animate().alpha(0f).setDuration(220).withEndAction(() -> overlay.setVisibility(View.GONE)).start();
        buttons.animate().alpha(0f).setDuration(220).withEndAction(() -> buttons.setVisibility(View.GONE)).start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        video = new PlayerView(this);
        video.setUseController(false);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        video.setKeepContentOnPlayerReset(true);
        video.setShutterBackgroundColor(0xFF000000);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));

        // Taps and swipes over the picture.
        View touch = new View(this);
        GestureDetector g = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                if (message.getVisibility() == View.VISIBLE && retries >= RETRIES) {
                    play(true);
                    return true;
                }
                if (overlay.getVisibility() == View.VISIBLE && overlay.getAlpha() > 0.5f) hideOverlay();
                else showOverlay();
                return true;
            }

            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (Math.abs(vy) > Math.abs(vx) && Math.abs(vy) > Ui.dp(PlayerActivity.this, 400)) {
                    step(vy < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        touch.setOnTouchListener((v, e) -> g.onTouchEvent(e));
        root.addView(touch, new FrameLayout.LayoutParams(-1, -1));

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT));
        root.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.CENTER));

        message = Ui.text(this, "", 16, Ui.TEXT, true);
        message.setGravity(Gravity.CENTER);
        message.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        message.setBackground(Ui.round(this, 0xCC0B0C21, 14));
        message.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        mp.leftMargin = mp.rightMargin = Ui.dp(this, 32);
        root.addView(message, mp);

        // Now and next along the foot.
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 24);
        overlay.setPadding(pad, Ui.dp(this, 48), pad, pad);
        GradientDrawable fade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE60B0C21, 0x000B0C21});
        overlay.setBackground(fade);
        name = Ui.text(this, "", 22, Ui.TEXT, true);
        name.setSingleLine(true);
        group = Ui.text(this, "", 13, Ui.DIM, false);
        nowLine = Ui.text(this, "", 15, Ui.GREEN, true);
        nowLine.setSingleLine(true);
        nowLine.setPadding(0, Ui.dp(this, 8), 0, 0);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.LINE));
        nextLine = Ui.text(this, "", 14, Ui.SOFT, false);
        nextLine.setSingleLine(true);
        overlay.addView(name);
        overlay.addView(group);
        overlay.addView(nowLine);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(Ui.dp(this, 280), Ui.dp(this, 6));
        pp.topMargin = Ui.dp(this, 6);
        pp.bottomMargin = Ui.dp(this, 6);
        overlay.addView(progress, pp);
        overlay.addView(nextLine);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Favourite, picture in picture and close, top right.
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        star = button(android.R.drawable.btn_star_big_off, R.string.live_favourite, v -> {
            if (cur == null) return;
            boolean on = Picks.toggle(this, cur.key);
            star.setImageResource(on ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            showOverlay();
        });
        buttons.addView(star);
        if (pipSupported()) buttons.addView(button(android.R.drawable.ic_menu_crop, R.string.live_pip, v -> enterPip()));
        buttons.addView(button(android.R.drawable.ic_menu_close_clear_cancel, R.string.live_close, v -> finish()));
        root.addView(buttons, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        return root;
    }

    private ImageButton button(int icon, int label, View.OnClickListener l) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setContentDescription(getString(label));
        b.setBackground(Ui.touch(this, false, 0x990B0C21, 24));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        lp.leftMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }
}
