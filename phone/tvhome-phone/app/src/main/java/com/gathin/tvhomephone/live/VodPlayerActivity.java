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
 * the rest), and where it was left kept, so it carries on from there next time. When it won't play from one playlist,
 * the same film or episode in the next one is tried, from where it had got to.
 */
@OptIn(markerClass = UnstableApi.class)
public final class VodPlayerActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "url", EXTRA_UA = "ua", EXTRA_REF = "ref", EXTRA_TITLE = "title",
            EXTRA_ALTS = "alts";
    /** Carried on from where it was left only after a minute in, and not in its last two minutes. */
    private static final long RESUME_AFTER = 60_000L, END_GAP = 120_000L;

    private ExoPlayer player;
    private String url, resumeKey;
    /** Where it plays ({address as the index has it, user agent, referrer}), its backups after; which is playing. */
    private final java.util.List<String[]> plays = new java.util.ArrayList<>();
    private int playing;

    /** The player for a film or episode: its address as the index has it (an Xtream one made from the login here). */
    public static Intent intent(Context c, String url, String ua, String ref, String title) {
        return new Intent(c, VodPlayerActivity.class).putExtra(EXTRA_URL, url).putExtra(EXTRA_UA, ua == null ? "" : ua)
                .putExtra(EXTRA_REF, ref == null ? "" : ref).putExtra(EXTRA_TITLE, title == null ? "" : title);
    }

    /** The same, with where else it plays: {address, user agent, referrer}, its own first (Vod.Item.plays()). */
    public static Intent intent(Context c, java.util.List<String[]> plays, String title) {
        String[] first = plays.get(0);
        Intent i = intent(c, first[0], first[1], first[2], title);
        java.util.ArrayList<String> alts = new java.util.ArrayList<>();
        for (int k = 1; k < plays.size(); k++) {
            String[] p = plays.get(k);
            alts.add(p[0] + "\u001f" + (p[1] == null ? "" : p[1]) + "\u001f" + (p[2] == null ? "" : p[2]));
        }
        return i.putStringArrayListExtra(EXTRA_ALTS, alts);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String stored = getIntent().getStringExtra(EXTRA_URL);
        plays.add(new String[]{stored, getIntent().getStringExtra(EXTRA_UA), getIntent().getStringExtra(EXTRA_REF)});
        java.util.ArrayList<String> alts = getIntent().getStringArrayListExtra(EXTRA_ALTS);
        if (alts != null) for (String a : alts) plays.add(a.split("\u001f", -1));
        // The first that is still in a playlist.
        playing = -1;
        for (int k = 0; k < plays.size() && playing < 0; k++) {
            String u = Vod.address(this, plays.get(k)[0]);
            if (u != null && !u.isEmpty()) playing = k;
        }
        if (stored == null || playing < 0) {
            Toast.makeText(this, "This one isn't in your playlist any more", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        url = Vod.address(this, plays.get(playing)[0]);
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
        load(0);
        long at = prefs().getLong(resumeKey, 0);
        if (at > RESUME_AFTER) {
            player.seekTo(at);
            Toast.makeText(this, "Carrying on from " + clock(at), Toast.LENGTH_SHORT).show();
        }
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(@NonNull PlaybackException e) {
                if (next()) {
                    Toast.makeText(VodPlayerActivity.this, "Trying your other playlist", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(VodPlayerActivity.this, "It wouldn't play from your playlist", Toast.LENGTH_LONG).show();
            }

            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_ENDED) prefs().edit().remove(resumeKey).apply();
            }
        });
        player.prepare();
        player.setPlayWhenReady(true);
    }

    /** The one playing now given to the player, from position (ms). */
    private void load(long position) {
        String[] p = plays.get(playing);
        Channel as = new Channel("vod:" + resumeKey, getIntent().getStringExtra(EXTRA_TITLE), "", "", url, "", p[1], p[2], 0);
        player.setMediaSource(Streams.source(as, url.contains(".m3u8")), position);
    }

    /** On to the next playlist that has it, from where it had got to; false when there is none left. */
    private boolean next() {
        if (player == null) return false;
        long pos = Math.max(0, player.getCurrentPosition());
        for (int k = playing + 1; k < plays.size(); k++) {
            String u = Vod.address(this, plays.get(k)[0]);
            if (u == null || u.isEmpty()) continue;
            playing = k;
            url = u;
            load(pos);
            player.prepare();
            player.setPlayWhenReady(true);
            return true;
        }
        return false;
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
