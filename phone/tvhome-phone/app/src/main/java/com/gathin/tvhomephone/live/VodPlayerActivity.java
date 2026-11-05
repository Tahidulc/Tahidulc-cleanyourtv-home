package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * A film or an episode from the family's own playlist (Vod), full screen on its side: the player's own controls (play,
 * pause, the bar to seek along), its name at the top while they show, the same sound as Live TV (FFmpeg for AC-3 and
 * the rest), and where it was left kept, so it carries on from there next time.
 */
@OptIn(markerClass = UnstableApi.class)
public final class VodPlayerActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "url", EXTRA_UA = "ua", EXTRA_REF = "ref", EXTRA_TITLE = "title";
    /** Carried on from where it was left only after a minute in, and not in its last two minutes. */
    private static final long RESUME_AFTER = 60_000L, END_GAP = 120_000L;

    private ExoPlayer player;
    private String url, resumeKey;

    /** The player for a film or episode: its address as the index has it (an Xtream one made from the login here). */
    public static Intent intent(Context c, String url, String ua, String ref, String title) {
        return new Intent(c, VodPlayerActivity.class).putExtra(EXTRA_URL, url).putExtra(EXTRA_UA, ua == null ? "" : ua)
                .putExtra(EXTRA_REF, ref == null ? "" : ref).putExtra(EXTRA_TITLE, title == null ? "" : title);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String stored = getIntent().getStringExtra(EXTRA_URL);
        url = Vod.address(this, stored);
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "This one isn't in your playlist any more", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        resumeKey = Integer.toHexString(stored.hashCode());
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        PlayerView view = new PlayerView(this);
        view.setShowNextButton(false);
        view.setShowPreviousButton(false);
        view.setKeepScreenOn(true);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        TextView title = Ui.text(this, getIntent().getStringExtra(EXTRA_TITLE), 18, Ui.TEXT, true);
        title.setShadowLayer(Ui.dp(this, 4), 0, 0, 0xCC000000);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tp.leftMargin = tp.topMargin = Ui.dp(this, 22);
        root.addView(title, tp);
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v -> title.setVisibility(v));
        setContentView(root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
        player = new ExoPlayer.Builder(this, renderers).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        view.setPlayer(player);
        Channel as = new Channel("vod:" + resumeKey, getIntent().getStringExtra(EXTRA_TITLE), "", "", url, "",
                getIntent().getStringExtra(EXTRA_UA), getIntent().getStringExtra(EXTRA_REF), 0);
        player.setMediaSource(Streams.source(as, url.contains(".m3u8")));
        long at = prefs().getLong(resumeKey, 0);
        if (at > RESUME_AFTER) {
            player.seekTo(at);
            Toast.makeText(this, "Carrying on from " + clock(at), Toast.LENGTH_SHORT).show();
        }
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(@NonNull PlaybackException e) {
                Toast.makeText(VodPlayerActivity.this, "It wouldn't play from your playlist", Toast.LENGTH_LONG).show();
            }

            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_ENDED) prefs().edit().remove(resumeKey).apply();
            }
        });
        player.prepare();
        player.setPlayWhenReady(true);
    }

    @Override protected void onStop() {
        super.onStop();
        if (player == null) return;
        // Where it was left: kept unless it was nearly over.
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        SharedPreferences.Editor e = prefs().edit();
        if (pos > RESUME_AFTER && (dur <= 0 || dur - pos > END_GAP)) e.putLong(resumeKey, pos);
        else if (dur > 0 && dur - pos <= END_GAP) e.remove(resumeKey);
        e.apply();
        player.pause();
    }

    @Override protected void onDestroy() {
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return getApplicationContext().getSharedPreferences("vod_resume", Context.MODE_PRIVATE);
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(java.util.Locale.UK, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.UK, "%d:%02d", s / 60, s % 60);
    }
}
