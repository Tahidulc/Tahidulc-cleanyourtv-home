package com.gathin.tvhome;

import android.content.Context;
import android.widget.Toast;

/**
 * Match night in one press (Match Day's button, on a TV that puts apps in windows: the Google TV Streamer): TiviMate,
 * as it is (its own last channel: nothing is tuned for it), in a window exactly where the Live Centre leaves its
 * picture (1500 x 900 at the top left), and the Live Centre round it: the table, the other scores and the game's
 * events down the right and along the foot, none of it over the picture. Back closes the Live Centre and TiviMate's
 * window grows back to the whole screen.
 */
final class MatchNight {
    private MatchNight() { }

    private static final String TIVIMATE = "ar.tvplayer.tv";
    private static int task = -1;
    private static Context app;

    static boolean can(Context c) {
        return Windowed.supported(c) && TwoUp.launch(c.getPackageManager(), TIVIMATE) != null;
    }

    static void start(HomeV2 h, ScoresTicker.Game g) {
        Context c = h.a.getApplicationContext();
        app = c;
        Toast.makeText(c, "Match night: TiviMate beside the Live Centre", Toast.LENGTH_SHORT).show();
        Windowed.open(c, TIVIMATE, Windowed.rect(c, 0, 0, 1500, 900), id -> {
            AlertLog.note(c, "match night: " + (id >= 0 ? "TiviMate in its window" : "no window, the Live Centre alone"));
            if (id < 0) { LiveCentre.open(c, g.id); return; }
            GoalWatchService.onAppOpened(c, TIVIMATE);
            LiveCentre.openHere(c, g.id);
            task = id;   // after: opening the Live Centre closes any before it, which would end this one
        });
    }

    /** The Live Centre closed: TiviMate's window back to the whole screen. */
    static void ended() {
        if (task < 0 || app == null) return;
        Windowed.full(app, task);
        task = -1;
    }
}
