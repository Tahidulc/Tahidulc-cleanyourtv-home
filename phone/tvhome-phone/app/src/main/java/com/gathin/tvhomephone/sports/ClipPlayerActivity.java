package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * A highlight clip full screen (ESPN's own mp4 or HLS): the player's own controls, its title at the top while they show,
 * a close button; nothing kept of it (it isn't a film, so not on Continue watching).
 */
@OptIn(markerClass = UnstableApi.class)
public final class ClipPlayerActivity extends AppCompatActivity {
    private static final String URL = "url", TITLE = "title";

    private ExoPlayer player;
    private PlayerView view;

    public static Intent intent(Context c, String url, String title) {
        return new Intent(c, ClipPlayerActivity.class).putExtra(URL, url).putExtra(TITLE, title == null ? "" : title);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String url = getIntent().getStringExtra(URL);
        if (url == null || !url.startsWith("https://")) {
            finish();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        view = new PlayerView(this);
        view.setShowNextButton(false);
        view.setShowPreviousButton(false);
        view.setKeepScreenOn(true);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 18);
        top.setPadding(pad, pad, pad, pad);
        TextView title = Ui.text(this, getIntent().getStringExtra(TITLE), 16, 0xFFFFFFFF, true);
        title.setMaxLines(2);
        title.setShadowLayer(Ui.dp(this, 4), 0, 0, 0xCC000000);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = Ui.text(this, "✕", 20, 0xFFFFFFFF, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.fill(this, 0x66000000, 20));
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v -> top.setVisibility(v));
        setContentView(root);
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(ClipPlayerActivity.this, "This clip won't play here", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_ENDED) finish();
            }
        });
        view.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.play();
    }

    @Override protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (view != null) view.setPlayer(null);
        super.onDestroy();
    }
}
