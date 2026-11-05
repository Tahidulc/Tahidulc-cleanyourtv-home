package com.gathin.tvhomephone.live;

import android.content.Context;
import android.media.audiofx.DynamicsProcessing;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;

/**
 * Night sound: the quiet parts (the commentary) brought up and the loud ones (the crowd, a goal) held down, so a late
 * game can be watched low. A compressor and a limiter on the player's own sound (Android 9 and later). Kept on this
 * phone, for live TV and the playlist's films alike.
 */
@OptIn(markerClass = UnstableApi.class)
public final class NightSound {
    private NightSound() {}

    private static final String KEY = "night_sound";
    private static DynamicsProcessing fx;
    private static int session;

    public static boolean available() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    public static boolean on(Context c) {
        return available() && c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    public static void set(Context c, boolean on) {
        c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
        apply(c);
    }

    /** The player's sound followed: the effect put on its audio session whenever it has a new one. */
    public static void follow(Context c, ExoPlayer player) {
        Context app = c.getApplicationContext();
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onAudioSessionIdChanged(@NonNull EventTime t, int id) {
                session = id;
                apply(app);
            }
        });
    }

    /** The effect on (or off) the current session, as the setting says. */
    static synchronized void apply(Context c) {
        if (!available()) return;
        if (fx != null) {
            try {
                fx.release();
            } catch (RuntimeException ignored) {
                // already gone with its session
            }
            fx = null;
        }
        if (session == 0 || !on(c)) return;
        try {
            DynamicsProcessing.Config cfg = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, true, 1, false, 0, true).build();
            DynamicsProcessing d = new DynamicsProcessing(0, session, cfg);
            for (int ch = 0; ch < 2; ch++) {
                // Brought up by 8 dB, then everything over -28 dB pressed 4 to 1, and nothing past -3 dB.
                d.setInputGainbyChannel(ch, 8f);
                DynamicsProcessing.MbcBand band = new DynamicsProcessing.MbcBand(true, 20000f, 5f, 150f, 4f, -28f, 6f, -90f, 1f, 0f, 0f);
                d.setMbcBandByChannelIndex(ch, 0, band);
                d.setLimiterByChannelIndex(ch, new DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -3f, 0f));
            }
            d.setEnabled(true);
            fx = d;
        } catch (RuntimeException e) {
            fx = null;
        }
    }
}
