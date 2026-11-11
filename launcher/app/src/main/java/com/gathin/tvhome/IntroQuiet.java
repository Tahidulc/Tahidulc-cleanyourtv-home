package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * The channel silent while an intro plays, and back afterwards. The audio focus is held (as the helper's search holds
 * it: TiviMate pauses, as for a call or the assistant, and plays on by itself once it is given back); a player that
 * plays on regardless is paused through its media session and played again at the end. Muting the stream would take the
 * intro's own sound with it, and a box whose sound goes out over HDMI (the Streamer) does not mute anyway.
 */
final class IntroQuiet {
    private IntroQuiet() { }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static AudioFocusRequest focus;
    private static boolean holding;
    private static final List<MediaController> paused = new ArrayList<>();

    static void hush(Context c) {
        Context app = c.getApplicationContext();
        AudioManager am = app.getSystemService(AudioManager.class);
        if (am == null) return;
        holding = true;
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    // the player asking for it back meanwhile (a channel restarting): taken back at once
                    .setOnAudioFocusChangeListener(change -> {
                        if (holding && change < 0) main.postDelayed(() -> {
                            if (holding && focus != null) am.requestAudioFocus(focus);
                        }, 300L);
                    }, main)
                    .build();
        }
        android.util.Log.i("Sport", "intro: the channel's sound held, focus " + am.requestAudioFocus(focus));
        main.postDelayed(() -> pauseOthers(app), 700L);
    }

    static void release(Context c) {
        if (!holding && focus == null && paused.isEmpty()) return;
        holding = false;
        AudioManager am = c.getApplicationContext().getSystemService(AudioManager.class);
        if (am != null && focus != null) am.abandonAudioFocusRequest(focus);
        focus = null;
        for (MediaController mc : paused) {
            try {
                mc.getTransportControls().play();
            } catch (RuntimeException ignored) {
                // gone
            }
        }
        paused.clear();
        android.util.Log.i("Sport", "intro: the channel's sound given back");
    }

    /** Any other app still playing (it took no notice of the focus): paused by its session, played again at the end. */
    private static void pauseOthers(Context c) {
        if (!holding) return;
        try {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            for (MediaController mc : m.getActiveSessions(new ComponentName(c, WatchHistory.class))) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING && !c.getPackageName().equals(mc.getPackageName())) {
                    mc.getTransportControls().pause();
                    paused.add(mc);
                }
            }
        } catch (Exception ignored) {
            // no media sessions to read: the focus alone, then
        }
    }
}
