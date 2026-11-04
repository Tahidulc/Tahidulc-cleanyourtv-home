package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;

/**
 * The guide's small live picture of the selected channel (tablets, and phones on their side): muted, no audio decoded,
 * standard definition, started a moment after the selection settles and stopped whenever its pane isn't on screen or
 * the full player opens (many providers allow one stream at a time). The address is never logged.
 */
@OptIn(markerClass = UnstableApi.class)
final class Preview {
    private static final long SETTLE_MS = 700;

    final FrameLayout view;
    private final Context context;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final PlayerView video;
    private final ImageView logo;
    private final TextView note;
    private final ProgressBar spinner;
    private ExoPlayer player;
    private Channel want, playing;
    private boolean asHls, active;
    private final Runnable start = this::startNow;

    Preview(Context c) {
        context = c;
        view = new FrameLayout(c);
        view.setBackground(Ui.round(c, 0xFF000000, 12));
        view.setClipToOutline(true);
        video = new PlayerView(c);
        video.setUseController(false);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        video.setShutterBackgroundColor(0xFF000000);
        video.setKeepContentOnPlayerReset(false);
        view.addView(video, new FrameLayout.LayoutParams(-1, -1));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 28);
        logo.setPadding(p, p, p, p);
        view.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(c);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.TEXT));
        view.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32), Gravity.CENTER));
        note = Ui.text(c, "", 12, Ui.SOFT, true);
        note.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        np.bottomMargin = Ui.dp(c, 8);
        view.addView(note, np);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    /** The pane is on screen (true) or not: nothing plays while it isn't. */
    void setActive(boolean on) {
        if (active == on) return;
        active = on;
        if (on && want != null) show(want);
        else if (!on) stop();
    }

    /** Shows this channel (after a moment, so scrolling through the guide doesn't open every stream). */
    void show(Channel ch) {
        want = ch;
        if (ch == null) {
            stop();
            return;
        }
        if (ch == playing && player != null) return;
        h.removeCallbacks(start);
        if (player != null) player.stop();
        playing = null;
        cover(ch, "");
        spinner.setVisibility(View.GONE);
        if (active) h.postDelayed(start, SETTLE_MS);
    }

    /** Stops and lets the stream go (the player is opening, or the pane is going). */
    void stop() {
        h.removeCallbacks(start);
        playing = null;
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
            video.setPlayer(null);
        }
        spinner.setVisibility(View.GONE);
        if (want != null) cover(want, "");
    }

    private void cover(Channel ch, String message) {
        logo.setVisibility(View.VISIBLE);
        if (ch.logo.isEmpty()) Glide.with(logo).clear(logo);
        else Glide.with(logo).load(ch.logo).into(logo);
        note.setText(message);
    }

    private void startNow() {
        Channel ch = want;
        if (!active || ch == null) return;
        if (player == null) {
            player = new ExoPlayer.Builder(context).build();
            // Muted, with no audio decoded and no audio focus taken; a small picture is enough.
            player.setVolume(0f);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setMaxVideoSizeSd()
                    .build());
            player.addListener(listener);
            video.setPlayer(player);
        }
        playing = ch;
        asHls = Streams.looksHls(ch);
        prepare();
    }

    private void prepare() {
        if (player == null || playing == null) return;
        player.setMediaSource(Streams.source(playing, asHls));
        player.prepare();
        player.setPlayWhenReady(true);
        spinner.setVisibility(View.VISIBLE);
        note.setText("");
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (s == Player.STATE_READY) {
                spinner.setVisibility(View.GONE);
                note.setText("");
            }
        }

        @Override public void onRenderedFirstFrame() {
            logo.setVisibility(View.GONE);
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            if (!asHls && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                    || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                asHls = true;
                prepare();
                return;
            }
            spinner.setVisibility(View.GONE);
            if (playing != null) cover(playing, context.getString(com.gathin.tvhomephone.R.string.live_preview_unavailable));
        }
    };
}
