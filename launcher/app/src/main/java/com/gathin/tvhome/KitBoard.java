package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.gathin.broadcast.KitSheets;
import com.gathin.broadcast.KitTurn;
import com.gathin.broadcast.Moments;
import com.gathin.broadcast.Shirts;
import com.gathin.broadcast.Worn;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * The 3D kits on the TV's scoreboard (overlays: KitSheets, KitTurn). The "3D kits" look's two shirts: each side's kit as it
 * wears it, made into frames by the hidden page (in a window of its own, out of sight, while it works) and handed to the
 * board. A goal's shirt (its own switch, any look): when a goal comes in the scores, the scorer's side's shirt with his
 * name and number on its back is made ahead; as the goal reaches the screen it comes out of the board, turns, and goes
 * back (KitTurn, in a window over the whole screen while it plays). Main thread.
 */
final class KitBoard {
    private KitBoard() { }

    private static final String TAG = "KitBoard";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("kit_board", Context.MODE_PRIVATE);
    }

    /** A goal's shirt turning to show the scorer's name and number: off until chosen. */
    static boolean goalShirtOn(Context c) {
        return prefs(c).getBoolean("goal_shirt", false);
    }

    static void setGoalShirtOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("goal_shirt", on).apply();
    }

    // ---- the hidden page's window ------------------------------------------------------------------------------------

    private static FrameLayout pageWin;

    static final KitSheets.Host HOST = new KitSheets.Host() {
        @Override public void attach(View page) {
            Context c = page.getContext().getApplicationContext();
            FrameLayout f = new FrameLayout(c);
            f.addView(page, new FrameLayout.LayoutParams(256, 256));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(256, 256, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.alpha = 0f;   // out of sight: the compositor skips it, the page still draws its frames
            lp.setTitle("KitSheets");
            c.getSystemService(WindowManager.class).addView(f, lp);
            pageWin = f;
        }

        @Override public void detach(View page) {
            FrameLayout f = pageWin;
            pageWin = null;
            if (f == null) return;
            f.removeAllViews();
            try {
                f.getContext().getSystemService(WindowManager.class).removeView(f);
            } catch (RuntimeException ignored) { }
        }
    };

    // ---- the board's two shirts ---------------------------------------------------------------------------------------

    private static String boardKey;
    /** The board the shirts were last made for: a new board (the board's window made again) gets them again. */
    private static java.lang.ref.WeakReference<BoardBug> boardBug = new java.lang.ref.WeakReference<>(null);
    /** The board's shirts that could not be made: asked for again a minute later, three tries at most. */
    private static final KitSheets.Tries tries = new KitSheets.Tries();

    /** A side as the page takes it: its ESPN id, name, the kit it wears (ESPN's name for it and its colours) and its crest. */
    static JSONObject team(ScoresTicker.Game g, boolean home) {
        Worn.Kit[] w = Worn.get(g.id);
        Worn.Kit k = w == null ? null : w[home ? 0 : 1];
        return KitSheets.team(home ? g.homeId : g.awayId, home ? (g.homeName != null ? g.homeName : g.home) : (g.awayName != null ? g.awayName : g.away),
                k == null ? "" : k.type, home ? g.homeColor : g.awayColor, home ? g.homeAlt : g.awayAlt, home ? g.homeLogo : g.awayLogo);
    }

    /** Pure: a frame's size for a board this high inside (px): a little taller than the board, in steps of 8, 64 to 224. */
    static int frameSize(int boardInner) {
        int s = Math.round(boardInner * 1.22f / 8f) * 8;
        return Math.max(64, Math.min(224, s));
    }

    /** The "3D kits" look up for g: its shirts made (again when the kits or the board's height change). */
    static void board(Context c, ScoresTicker.Game g, BoardBug bug, int boardInner) {
        if (g == null || bug == null || boardInner <= 0) return;
        bug.kitColours(g.homeColor, g.homeAlt, g.awayColor, g.awayAlt);
        int size = frameSize(boardInner);
        JSONObject h = team(g, true), a = team(g, false);
        String key = g.id + "|" + h.optString("kitType") + h.optString("color") + "|" + a.optString("kitType") + a.optString("color") + "|" + size;
        boolean again = tries.due(key, SystemClock.uptimeMillis());
        if (key.equals(boardKey) && boardBug.get() == bug && !again) return;
        // another game: its shirts flat until its own frames come, never the last game's
        if (boardKey == null || !boardKey.startsWith(g.id + "|")) bug.kits(null, null);
        boardKey = key;
        boardBug = new java.lang.ref.WeakReference<>(bug);
        if (tries.given(key)) return;   // tried enough: its flat shirts
        Context app = c.getApplicationContext();
        KitSheets.Sheet[] got = new KitSheets.Sheet[2];
        KitSheets.sheet(app, HOST, "board|home|" + key, h, size, KitSheets.boardAngles(BoardBug.kitRest(true)), null, null, s -> {
            got[0] = s;
            if (key.equals(boardKey)) bug.kits(got[0], got[1]);
            if (s == null && key.equals(boardKey)) tries.failed(key, SystemClock.uptimeMillis());
        });
        KitSheets.sheet(app, HOST, "board|away|" + key, a, size, KitSheets.boardAngles(BoardBug.kitRest(false)), null, null, s -> {
            got[1] = s;
            if (key.equals(boardKey)) bug.kits(got[0], got[1]);
            if (s == null && key.equals(boardKey)) tries.failed(key, SystemClock.uptimeMillis());
        });
    }


    /** A look's tile on the Scoreboard card: the game's shirts made for it (its own frames, not the board's). */
    static void preview(Context c, ScoresTicker.Game g, BoardBug bug, int inner) {
        if (g == null || bug == null) return;
        bug.kitColours(g.homeColor, g.homeAlt, g.awayColor, g.awayAlt);
        int size = frameSize(Math.max(1, inner));
        JSONObject h = team(g, true), a = team(g, false);
        String key = "preview|" + g.id + "|" + h.optString("color") + "|" + a.optString("color") + "|" + size;
        KitSheets.Sheet[] got = new KitSheets.Sheet[2];
        Context app = c.getApplicationContext();
        KitSheets.sheet(app, HOST, key + "|home", h, size, KitSheets.boardAngles(BoardBug.kitRest(true)), null, null, s -> {
            got[0] = s;
            bug.kits(got[0], got[1]);
            bug.kitsIn();
        });
        KitSheets.sheet(app, HOST, key + "|away", a, size, KitSheets.boardAngles(BoardBug.kitRest(false)), null, null, s -> {
            got[1] = s;
            bug.kits(got[0], got[1]);
        });
    }

    /** Another look chosen, or the board gone: its shirts made again next time. */
    static void boardGone() {
        boardKey = null;
        boardBug = new java.lang.ref.WeakReference<>(null);
    }

    // ---- a goal's shirt ----------------------------------------------------------------------------------------------

    /** The goals whose shirts were asked for (made ahead from the scores, before they reach the screen). */
    private static final Set<String> asked = new HashSet<>();
    /** The games seen: the goals in one when it is first seen have been on the screen already (no shirt made for them). */
    private static final Set<String> seen = new HashSet<>();
    private static final int TURN_SIZE = 256;

    /** Pure: the words under the disc: "41'  JAY HOLLIS", a penalty's "(PEN)" after. */
    static String caption(String minute, String scorer, boolean pen) {
        String m = minute == null ? "" : minute.trim();
        if (!m.isEmpty() && !m.endsWith("'")) m = m + "'";
        String n = scorer == null ? "" : scorer.trim().toUpperCase(java.util.Locale.ROOT);
        return (m.isEmpty() ? "" : m + "  ") + n + (pen ? " (PEN)" : "");
    }

    /** The scorer's {number, name on his back} for a goal {key, scorer, minute, side, kind, his ESPN id}, or null. */
    static String[] back(ScoresTicker.Game g, String[] goal) {
        if (goal == null || goal.length < 6) return null;
        String[] s = Shirts.of(g.id, goal[5]);
        if (s == null || s[0].isEmpty()) return null;
        return new String[]{s[0], Shirts.back(s[2], s[1].isEmpty() ? goal[1] : s[1])};
    }

    private static String turnKey(ScoresTicker.Game g, String[] goal) {
        return "turn|" + g.id + "|" + goal[0];
    }

    /** The scores' goals so far: any new one's shirt made ahead, for its moment on the screen (main thread). */
    static void feed(Context c, ScoresTicker.Game g) {
        if (g == null || g.id == null || !goalShirtOn(c)) return;
        if (seen.size() > 200) {
            seen.clear();
            asked.clear();
        }
        boolean first = seen.add(g.id);
        for (String[] goal : g.goals) {
            if (goal == null || goal.length < 6 || "og".equals(goal[4])) continue;
            String key = turnKey(g, goal);
            if (asked.contains(key)) continue;
            // joined at 3-0: those three were on the screen before; only the goals to come are made ahead
            if (first) { asked.add(key); continue; }
            String[] back = back(g, goal);
            if (back == null) continue;   // his number not known yet: looked at again with the next scores
            asked.add(key);
            KitSheets.sheet(c.getApplicationContext(), HOST, key, team(g, "home".equals(goal[3])), TURN_SIZE, KitSheets.turnAngles(),
                    back[1], back[0], s -> Log.i(TAG, "goal shirt " + (s == null ? "not made" : "ready") + ": " + goal[1]));
        }
    }

    private static FrameLayout turnWin;
    private static KitTurn turn;
    /** The frames the turn on now plays (let go after it). */
    private static String turnKey;

    /** A goal for side (1 home, 2 away) on the screen: its scorer's shirt out of the board and round (main thread). */
    static void goal(Context c, ScoresTicker.Game g, int side, BoardBug bug) {
        if (g == null || bug == null || !goalShirtOn(c)) return;
        String[] goal = Moments.lastGoal(g.goals, side);
        if (goal == null || goal.length < 6 || "og".equals(goal[4])) return;
        String[] back = back(g, goal);
        if (back == null) {
            Log.i(TAG, "goal shirt: no number for " + goal[1]);
            return;
        }
        String key = turnKey(g, goal);
        String words = caption(BroadcastMinute.of(goal[2]), goal[1], "pen".equals(goal[4]));
        long at = SystemClock.uptimeMillis();
        asked.add(key);
        KitSheets.sheet(c.getApplicationContext(), HOST, key, team(g, side == 1), TURN_SIZE, KitSheets.turnAngles(), back[1], back[0], s -> {
            // made in time (the board's goal moment still on), else let go
            if (s == null || SystemClock.uptimeMillis() - at > 2500) {
                Log.i(TAG, "goal shirt " + (s == null ? "not made" : "too late") + " for " + goal[1]);
                return;
            }
            play(c, s, side, bug, words);
        });
    }

    /** Plays the turn over the whole screen, from the board's cell for the side (else its end). */
    static void play(Context c, KitSheets.Sheet s, int side, BoardBug bug, String words) {
        if (turn != null && turn.playing()) return;
        if (!bug.isAttachedToWindow() || !bug.isShown()) return;
        int[] loc = new int[2];
        bug.getLocationOnScreen(loc);
        float[] cell = bug.kitCell(side);
        float x, y, size;
        int bw = bug.getWidth(), bh = bug.getHeight();
        if (cell != null) {
            x = loc[0] + cell[0];
            y = loc[1] + cell[1];
            size = cell[2];
        } else {
            x = loc[0] + (side == 1 ? bh : bw - bh);
            y = loc[1] + bh / 2f;
            size = bh * 0.8f;
        }
        Context app = c.getApplicationContext();
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        int sh = Math.min(dm.widthPixels, dm.heightPixels);
        boolean down = y < sh / 2f;
        if (!turnWindow(app)) return;
        if (cell != null) bug.shirtOut(side, KitTurn.HOME_MS);
        turnKey = s.key;
        float pan = Math.max(-0.6f, Math.min(0.6f, (x / Math.max(1, dm.widthPixels) * 2 - 1) * 0.6f));
        if (BoardCover.swishOn(app)) com.gathin.broadcast.Sfx.play(app, "studio-whoosh", pan, 0.55f);
        turn.play(s, x, y, size, BoardBug.kitRest(side == 1), down, words, KitBoard::closeTurn);
        Log.i(TAG, "goal shirt: " + words);
    }

    private static void closeTurn() {
        // the goal's frames let go once it has played (megabytes; a try's too)
        if (turnKey != null) KitSheets.release(turnKey);
        turnKey = null;
        FrameLayout f = turnWin;
        turnWin = null;
        turn = null;
        if (f == null) return;
        try {
            f.getContext().getSystemService(WindowManager.class).removeView(f);
        } catch (RuntimeException ignored) { }
    }

    /** The board going (another game, the channel): a turn on stops. */
    static void stop() {
        if (turn != null) turn.stop();
    }

    /**
     * A try from the Scoreboard card (or adb): the game's last goal's shirt when its scorer's number is known, else a
     * made-up scorer's (HOLLIS 27) on the home side's kit, from the board when it is up, else from the screen's top left.
     */
    static void tryIt(Context c, ScoresTicker.Game g, BoardBug bug, java.util.function.Consumer<String> note) {
        if (g == null) { note.accept("No game to take the kits from just now"); return; }
        String[] goal = g.goals.isEmpty() ? null : g.goals.get(g.goals.size() - 1);
        String[] back = goal == null || "og".equals(goal[4]) ? null : back(g, goal);
        int side = goal != null && back != null && "away".equals(goal[3]) ? 2 : 1;
        String name = back != null ? back[1] : "HOLLIS", number = back != null ? back[0] : "27";
        String words = back != null ? caption(BroadcastMinute.of(goal[2]), goal[1], "pen".equals(goal[4])) : caption("41", "Jay Hollis (made up)", false);
        Context app = c.getApplicationContext();
        note.accept("Making the shirt…");
        KitSheets.sheet(app, HOST, "try|" + g.id + "|" + side + "|" + number, team(g, side == 1), TURN_SIZE, KitSheets.turnAngles(), name, number, s -> {
            if (s == null) { note.accept("Couldn't make the shirt just now"); return; }
            if (bug != null && bug.isAttachedToWindow() && bug.isShown()) play(app, s, side, bug, words);
            else playLoose(app, s, side, words);
        });
    }

    /** adb: a made-up game's shirt (Arsenal's home kit, a made-up scorer), from the board when it is up. */
    static void testSample(Context c) {
        ScoresTicker.Game g = new ScoresTicker.Game();
        g.id = "test-shirt";
        g.home = "ARS";
        g.away = "NEW";
        g.homeName = "Arsenal";
        g.awayName = "Newcastle United";
        g.homeId = "359";
        g.awayId = "361";
        g.homeColor = 0xE20520;
        g.homeAlt = 0x003399;
        g.awayColor = 0x000000;
        g.awayAlt = 0xFFFFFF;
        tryIt(c, g, BoardCover.bugNow(), (why) -> Log.i(TAG, why));
    }

    /** A try with no board up: from the top left of the screen. */
    private static void playLoose(Context app, KitSheets.Sheet s, int side, String words) {
        if (turn != null && turn.playing()) return;
        android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
        float u = Math.min(dm.widthPixels, dm.heightPixels) / 1080f;
        if (!turnWindow(app)) return;
        turnKey = s.key;
        if (BoardCover.swishOn(app)) com.gathin.broadcast.Sfx.play(app, "studio-whoosh", -0.4f, 0.55f);
        turn.play(s, 260 * u, 100 * u, 70 * u, BoardBug.kitRest(side == 1), true, words, KitBoard::closeTurn);
    }

    /** The turn's window over the whole screen (made for a turn, taken away after it); false when the TV will not have it. */
    private static boolean turnWindow(Context app) {
        if (turnWin != null) return true;
        FrameLayout f = new FrameLayout(app);
        KitTurn v = new KitTurn(app);
        f.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("KitTurn");
        try {
            app.getSystemService(WindowManager.class).addView(f, lp);
        } catch (RuntimeException e) {
            Log.w(TAG, "no window for the goal's shirt: " + e);
            return false;
        }
        turnWin = f;
        turn = v;
        return true;
    }

    /** The minute as the scores give it ("41'", "90'+3'"), for the words under the disc. */
    static final class BroadcastMinute {
        private BroadcastMinute() { }

        static String of(String m) {
            return com.gathin.broadcast.BroadcastData.minute(m);
        }
    }
}
