package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.time.LocalTime;
import java.util.List;

/**
 * Kids bedtime (the theme's kids_bedtime, "19:30"; off without it): from then until six in the morning the Kids page
 * is the night scene, the children yawning and waving goodnight (KidsPlay), and anything opened from it starts a
 * gentle timer (kids_bedtime_minutes, 20): then a goodnight card comes up over the cartoon (OK for five more minutes,
 * once), and a minute later whatever is playing is paused.
 */
final class KidsNight {
    private KidsNight() { }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static boolean armed, extended;

    /** Bedtime in minutes after midnight; -1 when the theme sets none. */
    static int bedMinutes(Context c) {
        String s = Config.themeString(c, "kids_bedtime", "").trim();
        if (s.isEmpty()) return -1;
        try {
            LocalTime t = LocalTime.parse(s.length() == 4 ? "0" + s : s);
            return t.getHour() * 60 + t.getMinute();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Past the children's bedtime (and before six in the morning). */
    static boolean isNight(Context c) {
        int bed = bedMinutes(c);
        if (bed < 0) return false;
        int now = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        return now >= bed || now < 6 * 60;
    }

    /** Something was opened from the Kids page: after bedtime, the timer starts (once). */
    static void opened(Context c) {
        if (armed || !isNight(c)) return;
        app = c.getApplicationContext();
        armed = true;
        extended = false;
        int minutes = 20;
        try {
            minutes = Math.max(1, Integer.parseInt(Config.themeString(c, "kids_bedtime_minutes", "20").trim()));
        } catch (NumberFormatException ignored) {
            // twenty
        }
        Toast.makeText(app, "Bedtime: " + minutes + " more minutes, then goodnight", Toast.LENGTH_LONG).show();
        MAIN.removeCallbacks(WRAP);
        MAIN.postDelayed(WRAP, minutes * 60_000L);
    }

    private static final Runnable PAUSE = () -> {
        armed = false;
        pauseAll(app);
        Toast.makeText(app, "Goodnight! Sleep tight 🌙", Toast.LENGTH_LONG).show();
    };

    private static final Runnable WRAP = KidsNight::wrap;

    /** The goodnight card over the cartoon (OK: five more minutes, once), and the pause a minute later. */
    private static void wrap() {
        ScoreCard.Data d = new ScoreCard.Data();
        d.dark = true;
        d.kind = "release";
        d.band = 0xFF141A4A;
        d.bandDeep = 0xFF060928;
        d.labelColour = 0xFF6E7BFF;
        d.label = "BEDTIME";
        d.headline = "Time for bed! Goodnight 🌙";
        d.line = "The cartoon stops in a minute. Sleep tight!";
        if (!extended) {
            d.okWords = "for 5 more minutes";
            d.channel = "5 more minutes";
            d.watch = () -> {
                extended = true;
                MAIN.removeCallbacks(PAUSE);
                MAIN.removeCallbacks(WRAP);
                MAIN.postDelayed(WRAP, 5 * 60_000L);
                Toast.makeText(app, "Five more minutes", Toast.LENGTH_SHORT).show();
            };
        }
        ScoreCard.show(app, d);
        MAIN.removeCallbacks(PAUSE);
        MAIN.postDelayed(PAUSE, 60_000L);
    }

    /** Every app playing now, paused (the children's cartoon). */
    static void pauseAll(Context c) {
        if (c == null) return;
        try {
            MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
            List<MediaController> list = m.getActiveSessions(new ComponentName(c, WatchHistory.class));
            for (MediaController mc : list) {
                PlaybackState st = mc.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING && !mc.getPackageName().contains("com.gathin.tvhome")) {
                    mc.getTransportControls().pause();
                }
            }
        } catch (Exception ignored) {
            // no media sessions to read: the card said goodnight all the same
        }
    }
}
