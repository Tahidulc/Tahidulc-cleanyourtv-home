package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Half-time hop: at half time in the game being watched (as it shows on screen), a card in the half-time card's look
 * (LineupCard.showHop: the corner card, the band with the competition's mark and HALF TIME) offers the other games on
 * now (the followed ones, still playing, with their crests and scores) and Stay. Up and Down choose, OK hops: that
 * game's channel goes on, and as the first game's second half kicks off on its channel (the scores see it, then its
 * channel's wait, InStep), its channel goes back on by itself. Back, or 45 seconds with nothing chosen, stays. Off in
 * Match mode's card when not wanted.
 */
final class HalfTimeHop {
    private HalfTimeHop() { }

    /** The tune takes a few seconds: back on this long before the second half shows. */
    private static final long TUNE_MS = 6_000L;
    private static Handler main;

    /** The main thread's handler, made when first wanted (the tests use the sums without Android's looper). */
    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static Context app;

    /** Where it hopped from: the game, its channel's names, and that channel's wait. */
    private static String awayGame;
    private static List<String> backNames = new ArrayList<>();
    private static long backHold;
    private static long awayAt;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("half_time_hop", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) { return prefs(c).getBoolean("on", true); }

    static void setOn(Context c, boolean v) { prefs(c).edit().putBoolean("on", v).apply(); }

    /** Pure: the games to offer: on now, not at half time themselves, not the game itself, at most three. */
    static List<ScoresTicker.Game> choices(ScoresTicker.Game g, List<ScoresTicker.Game> all) {
        List<ScoresTicker.Game> out = new ArrayList<>();
        for (ScoresTicker.Game x : all) {
            if (x == null || x.id == null || x.id.equals(g.id) || x.state != 0 || x.halftime) continue;
            out.add(x);
            if (out.size() == 3) break;
        }
        return out;
    }

    /**
     * Half time in the game watched, as it shows (main thread): the card, in the half-time card's look (LineupCard), when
     * another game is on; its crests and the competition's mark read first on the cards' thread.
     */
    static void offer(GoalWatchService s, ScoresTicker.Game g, List<ScoresTicker.Game> all) {
        if (!on(s) || LineupCard.hopShowing()) return;
        List<ScoresTicker.Game> l = choices(g, all);
        if (l.isEmpty()) return;
        app = s.getApplicationContext();
        s.cardRun(() -> {
            LineupCard.Hop hp = new LineupCard.Hop();
            try {
                ScoreCard.Data d = s.footballData(g);
                hp.leagueLogo = d.leagueLogo;
                hp.home = d.homeName == null ? "" : d.homeName;
                hp.away = d.awayName == null ? "" : d.awayName;
                hp.score = d.score == null ? "" : d.score;
                for (ScoresTicker.Game x : l) {
                    ScoreCard.Data o = s.footballData(x);
                    LineupCard.HopOption op = new LineupCard.HopOption();
                    op.homeCrest = o.homeCrest;
                    op.awayCrest = o.awayCrest;
                    op.home = o.homeName == null ? "" : o.homeName;
                    op.away = o.awayName == null ? "" : o.awayName;
                    op.score = o.score == null ? "" : o.score;
                    op.minute = minute(x);
                    hp.options.add(op);
                }
            } catch (RuntimeException e) {
                AlertLog.note(s, "the half-time hop couldn't be built: " + e);
                return;
            }
            hp.chosen = i -> choose(g, l, i);
            main().post(() -> LineupCard.showHop(s, hp));
        });
    }

    /** OK on line i of the card: that game's channel on (the last line: stay). */
    private static void choose(ScoresTicker.Game from, List<ScoresTicker.Game> others, int i) {
        if (i < 0 || i >= others.size() || app == null) return;
        ScoresTicker.Game to = others.get(i);
        TvGuide.Watch w = TvGuide.watch(app.getResources().getStringArray(R.array.football_tv_channels), to);
        if (w == null || w.names.isEmpty()) return;
        awayGame = from.id;
        backNames = InStep.tunedNames(app);
        long h = InStep.current(app);
        backHold = h >= 0 ? h : 30_000L;
        awayAt = System.currentTimeMillis();
        AlertLog.note(app, "half-time hop to " + AlertLog.game(to) + ", back for " + AlertLog.game(from));
        AppSearchService.tune(app, "ar.tvplayer.tv", w.names, null);
    }

    /** Whether the watched game was hopped away from (for its second half). */
    static boolean away(String gameId) {
        if (awayGame == null || !awayGame.equals(gameId)) return false;
        if (System.currentTimeMillis() - awayAt > 2 * 3_600_000L) { awayGame = null; return false; }
        return true;
    }

    /** Its second half seen by the scores (main thread): its channel back on as the second half shows there. */
    static void secondHalf(Context c, ScoresTicker.Game g) {
        if (!away(g.id)) return;
        awayGame = null;
        List<String> names = new ArrayList<>(backNames);
        if (names.isEmpty()) {
            TvGuide.Watch w = TvGuide.watch(c.getResources().getStringArray(R.array.football_tv_channels), g);
            if (w != null) names.addAll(w.names);
        }
        if (names.isEmpty()) return;
        Context a = c.getApplicationContext();
        main().postDelayed(() -> {
            AlertLog.note(a, "half-time hop: back for the second half of " + AlertLog.game(g));
            AppSearchService.tune(a, "ar.tvplayer.tv", names, null);
        }, Math.max(0, backHold - TUNE_MS));
    }

    private static String minute(ScoresTicker.Game g) {
        return g.detail == null ? "Live" : g.detail;
    }

}
