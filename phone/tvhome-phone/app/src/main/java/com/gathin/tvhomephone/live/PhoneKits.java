package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.KitSheets;
import com.gathin.broadcast.KitTurn;
import com.gathin.broadcast.Moments;
import com.gathin.broadcast.Shirts;
import com.gathin.broadcast.Worn;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.LiveGames;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * The 3D kits on the player's scoreboard (overlays: KitSheets, KitTurn), as the TV has them (KitBoard). The "3D kits"
 * look's two shirts: each side's kit as it wears it, made into frames by the hidden page (in the player's own layout, out
 * of sight, while it works) and handed to the board. A goal's shirt (its own switch, any look): when a goal comes in the
 * scores, the scorer's side's shirt with his name and number on its back is made ahead; as the goal reaches the picture
 * it comes out of the board, turns, and goes back (KitTurn, over the player while it plays). Main thread.
 */
final class PhoneKits {
    private PhoneKits() { }

    private static final String TAG = "PhoneKits";

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

    /** The shirt wanted with a goal: its own switch, or the 3D shirt chosen as the goal animation (GoalLooks). */
    static boolean shirtWanted(Context c) {
        return goalShirtOn(c) || com.gathin.broadcast.GoalLooks.shirt(ScoreBoard.goalLook(c));
    }

    // ---- the hidden page, in the player's layout --------------------------------------------------------------------

    private static WeakReference<FrameLayout> hostRoot = new WeakReference<>(null);
    private static KitSheets.Host hostOf;

    /**
     * Where the hidden page lives: under everything in root, a small square (it draws its frames off the screen). One host
     * a layout (the page stays put while the same player asks), holding the layout weakly (a closed player let go).
     */
    static KitSheets.Host host(FrameLayout root) {
        if (hostOf != null && hostRoot.get() == root) return hostOf;
        WeakReference<FrameLayout> ref = new WeakReference<>(root);
        KitSheets.Host h = new KitSheets.Host() {
            @Override public void attach(View page) {
                FrameLayout r = ref.get();
                if (r == null) throw new IllegalStateException("the player has gone");
                r.addView(page, 0, new FrameLayout.LayoutParams(256, 256));
            }

            @Override public void detach(View page) {
                if (page.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) page.getParent()).removeView(page);
            }
        };
        hostRoot = ref;
        hostOf = h;
        return h;
    }

    // ---- the board's two shirts ---------------------------------------------------------------------------------------

    private static String boardKey;
    /** The board the shirts were last made for (a new player's board has none yet: made, or taken from those kept, again). */
    private static WeakReference<OverlayBug> boardBug = new WeakReference<>(null);
    /** The board's shirts that could not be made: asked for again a minute later, three tries at most. */
    private static final KitSheets.Tries tries = new KitSheets.Tries();

    /** ESPN's hex colour ("ffff00") as RGB; 0 for none. */
    static int rgb(String hex) {
        try {
            return hex == null || hex.length() != 6 ? 0 : Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A side as the page takes it: its ESPN id, name, the kit it wears (ESPN's name for it and its colours) and its crest. */
    static JSONObject team(Game g, boolean home) {
        Worn.Kit[] w = Worn.get(g.id);
        Worn.Kit k = w == null ? null : w[home ? 0 : 1];
        String name = home ? (g.homeFull != null && !g.homeFull.isEmpty() ? g.homeFull : g.homeName)
                : (g.awayFull != null && !g.awayFull.isEmpty() ? g.awayFull : g.awayName);
        return KitSheets.team(home ? g.homeId : g.awayId, name, k == null ? "" : k.type, rgb(home ? g.homeColor : g.awayColor),
                rgb(home ? g.homeAlt : g.awayAlt), home ? LiveGames.homeCrest(g) : LiveGames.awayCrest(g));
    }

    /** Pure: a frame's size for a board this high inside (px): a little taller than the board, in steps of 8, 64 to 224. */
    static int frameSize(int boardInner) {
        int s = Math.round(boardInner * 1.22f / 8f) * 8;
        return Math.max(64, Math.min(224, s));
    }

    /** The "3D kits" look up for g: its shirts made (again when the kits or the board's height change). */
    static void board(Context c, Game g, OverlayBug bug, int boardInner, FrameLayout root) {
        if (g == null || g.id == null || bug == null || boardInner <= 0) return;
        bug.kitColours(rgb(g.homeColor), rgb(g.homeAlt), rgb(g.awayColor), rgb(g.awayAlt));
        if (root == null) return;   // nowhere for the page: its flat shirts
        int size = frameSize(boardInner);
        JSONObject h = team(g, true), a = team(g, false);
        String key = g.id + "|" + h.optString("kitType") + h.optString("color") + "|" + a.optString("kitType") + a.optString("color") + "|" + size;
        boolean again = tries.due(key, android.os.SystemClock.uptimeMillis());
        if (key.equals(boardKey) && boardBug.get() == bug && !again) return;
        // another game: the last one's shirts never shown on this one's board (its flat ones in its colours until they come)
        if (boardKey == null || !boardKey.startsWith(g.id + "|")) bug.kits(null, null);
        boardKey = key;
        boardBug = new WeakReference<>(bug);
        if (tries.given(key)) return;   // tried enough: its flat shirts
        Context app = c.getApplicationContext();
        KitSheets.Host hs = host(root);
        KitSheets.Sheet[] got = new KitSheets.Sheet[2];
        KitSheets.sheet(app, hs, "board|home|" + key, h, size, KitSheets.boardAngles(OverlayBug.kitRest(true)), null, null, s -> {
            got[0] = s;
            if (key.equals(boardKey)) bug.kits(got[0], got[1]);
            if (s == null && key.equals(boardKey)) tries.failed(key, android.os.SystemClock.uptimeMillis());
        });
        KitSheets.sheet(app, hs, "board|away|" + key, a, size, KitSheets.boardAngles(OverlayBug.kitRest(false)), null, null, s -> {
            got[1] = s;
            if (key.equals(boardKey)) bug.kits(got[0], got[1]);
            if (s == null && key.equals(boardKey)) tries.failed(key, android.os.SystemClock.uptimeMillis());
        });
    }

    /**
     * The look picker's "3D kits" tile: the made-up Arsenal v Newcastle (sample()) in 3D, as the TV's tile makes the chosen
     * game's. The hidden page goes in a's own layout, under the settings, while it works; inner: the tile's board height.
     */
    static void preview(android.app.Activity a, OverlayBug bug, int inner) {
        if (a == null || bug == null) return;
        Game g = sample();
        bug.kitColours(rgb(g.homeColor), rgb(g.homeAlt), rgb(g.awayColor), rgb(g.awayAlt));
        View content = a.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;   // nowhere for the page: its flat shirts
        int size = frameSize(Math.max(1, inner));
        JSONObject h = team(g, true), w = team(g, false);
        String key = "preview|" + g.id + "|" + h.optString("color") + "|" + w.optString("color") + "|" + size;
        KitSheets.Sheet[] got = new KitSheets.Sheet[2];
        KitSheets.Host hs = host((FrameLayout) content);
        Context app = a.getApplicationContext();
        KitSheets.sheet(app, hs, key + "|home", h, size, KitSheets.boardAngles(OverlayBug.kitRest(true)), null, null, s -> {
            got[0] = s;
            bug.kits(got[0], got[1]);
            bug.kitsIn();
        });
        KitSheets.sheet(app, hs, key + "|away", w, size, KitSheets.boardAngles(OverlayBug.kitRest(false)), null, null, s -> {
            got[1] = s;
            bug.kits(got[0], got[1]);
        });
    }

    // ---- a goal's shirt ----------------------------------------------------------------------------------------------

    /** The goals whose shirts were asked for (made ahead from the scores, before they reach the picture). */
    private static final Set<String> asked = new HashSet<>();
    /** The games seen: the goals in one when it is first seen have been in the picture already (no shirt made for them). */
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
    static String[] back(Game g, String[] goal) {
        if (goal == null || goal.length < 6) return null;
        String[] s = Shirts.of(g.id, goal[5]);
        if (s == null || s[0].isEmpty()) return null;
        return new String[]{s[0], Shirts.back(s[2], s[1].isEmpty() ? goal[1] : s[1])};
    }

    private static String turnKey(Game g, String[] goal) {
        return "turn|" + g.id + "|" + goal[0];
    }

    /** The scores' goals so far: any new one's shirt made ahead, for its moment on the picture (main thread). */
    static void feed(Context c, Game g, FrameLayout root) {
        if (g == null || g.id == null || root == null || !shirtWanted(c)) return;
        if (seen.size() > 200) {
            seen.clear();
            asked.clear();
        }
        boolean first = seen.add(g.id);
        for (String[] goal : g.goals) {
            if (goal == null || goal.length < 6 || "og".equals(goal[4])) continue;
            String key = turnKey(g, goal);
            if (asked.contains(key)) continue;
            // joined at 3-0: those three were in the picture before; only the goals to come are made ahead
            if (first) { asked.add(key); continue; }
            String[] back = back(g, goal);
            if (back == null) continue;   // his number not known yet: looked at again with the next scores
            asked.add(key);
            KitSheets.sheet(c.getApplicationContext(), host(root), key, team(g, "home".equals(goal[3])), TURN_SIZE, KitSheets.turnAngles(),
                    back[1], back[0], s -> Log.i(TAG, "goal shirt " + (s == null ? "not made" : "ready") + ": " + goal[1]));
        }
    }

    private static KitTurn turn;
    /** The frames the turn on now plays (let go after it). */
    private static String turnKey;

    /** A goal for side (1 home, 2 away) on the picture: its scorer's shirt out of the board and round (main thread). */
    static void goal(Context c, Game g, int side, OverlayBug bug, FrameLayout root) {
        if (g == null || g.id == null || bug == null || root == null || !shirtWanted(c)) return;
        String[] goal = Moments.lastGoal(g.goals, side);
        if (goal == null || goal.length < 6 || "og".equals(goal[4])) return;
        String[] back = back(g, goal);
        if (back == null) {
            Log.i(TAG, "goal shirt: no number for " + goal[1]);
            return;
        }
        String key = turnKey(g, goal);
        String words = caption(com.gathin.broadcast.BroadcastData.minute(goal[2]), goal[1], "pen".equals(goal[4]));
        long at = android.os.SystemClock.uptimeMillis();
        asked.add(key);
        KitSheets.sheet(c.getApplicationContext(), host(root), key, team(g, side == 1), TURN_SIZE, KitSheets.turnAngles(), back[1], back[0], s -> {
            // made in time (the board's goal moment still on), else let go
            if (s == null || android.os.SystemClock.uptimeMillis() - at > 2500) {
                Log.i(TAG, "goal shirt " + (s == null ? "not made" : "too late") + " for " + goal[1]);
                return;
            }
            play(c, s, side, bug, root, words, false);
        });
    }

    /**
     * Plays the turn over the player, from the board's cell for the side (else its end); positions on root's own pixels.
     * loose (a try): from the picture's top left when no board is up; a goal's needs the board up.
     */
    static void play(Context c, KitSheets.Sheet s, int side, OverlayBug bug, FrameLayout root, String words, boolean loose) {
        // a turn whose player went in the middle of it (its layout gone, never drawn again): over
        if (turn != null && !turn.isAttachedToWindow()) turn.stop();
        if (turn != null && turn.playing()) return;
        if (s == null || root == null || !root.isAttachedToWindow()) return;
        boolean board = bug != null && bug.isAttachedToWindow() && bug.isShown() && bug.getWidth() > 0;
        if (!board && !loose) return;
        Context app = c.getApplicationContext();
        float x, y, size;
        float[] cell = null;
        if (board) {
            int[] b = new int[2], r = new int[2];
            bug.getLocationInWindow(b);
            root.getLocationInWindow(r);
            cell = bug.kitCell(side);
            int bw = bug.getWidth(), bh = bug.getHeight();
            if (cell != null) {
                x = b[0] - r[0] + cell[0];
                y = b[1] - r[1] + cell[1];
                size = cell[2];
            } else {
                x = b[0] - r[0] + (side == 1 ? bh : bw - bh);
                y = b[1] - r[1] + bh / 2f;
                size = bh * 0.8f;
            }
        } else {
            // no board up (a try): from the top left of the picture
            float u = Math.max(1, Math.min(root.getWidth(), root.getHeight())) / 1080f;
            x = 260 * u;
            y = 100 * u;
            size = 70 * u;
        }
        boolean down = y < root.getHeight() / 2f;
        KitTurn v = new KitTurn(app);
        root.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        turn = v;
        turnKey = s.key;
        if (cell != null) bug.shirtOut(side, KitTurn.HOME_MS);
        float pan = Math.max(-0.6f, Math.min(0.6f, (x / Math.max(1, root.getWidth()) * 2 - 1) * 0.6f));
        if (PlayerIntro.swishOn(app)) com.gathin.broadcast.Sfx.play(app, "studio-whoosh", pan, 0.55f);
        v.play(s, x, y, size, OverlayBug.kitRest(side == 1), down, words, () -> {
            // the goal's frames let go once it has played (megabytes; a try's too)
            if (turnKey != null) KitSheets.release(turnKey);
            turnKey = null;
            if (v.getParent() == root) root.removeView(v);
            if (turn == v) turn = null;
        });
        Log.i(TAG, "goal shirt: " + words);
    }

    /** The board going (the player closing, its small window): a turn on over root stops. */
    static void stop(FrameLayout root) {
        if (turn != null && root != null && turn.getParent() == root) turn.stop();
    }

    /**
     * A try from the player's tests: the game's last goal's shirt when its scorer's number is known, else a made-up
     * scorer's (HOLLIS 27) on the home side's kit; the made-up Arsenal v Newcastle when no game is on.
     */
    static void tryIt(Context c, Game g, OverlayBug bug, FrameLayout root, IntConsumer note) {
        if (root == null) return;
        Game game = g != null && g.id != null ? g : sample();
        String[] goal = game.goals.isEmpty() ? null : game.goals.get(game.goals.size() - 1);
        String[] back = goal == null || goal.length < 6 || "og".equals(goal[4]) ? null : back(game, goal);
        int side = goal != null && back != null && "away".equals(goal[3]) ? 2 : 1;
        String name = back != null ? back[1] : "HOLLIS", number = back != null ? back[0] : "27";
        String words = back != null ? caption(com.gathin.broadcast.BroadcastData.minute(goal[2]), goal[1], "pen".equals(goal[4]))
                : caption("41", "Jay Hollis (made up)", false);
        Context app = c.getApplicationContext();
        note.accept(R.string.live_shirt_making);
        KitSheets.sheet(app, host(root), "try|" + game.id + "|" + side + "|" + number, team(game, side == 1), TURN_SIZE, KitSheets.turnAngles(),
                name, number, s -> {
                    if (s == null) { note.accept(R.string.live_shirt_failed); return; }
                    play(app, s, side, bug, root, words, true);
                });
    }

    /** A made-up game for a try with none on: Arsenal (their home kit) v Newcastle United. */
    static Game sample() {
        Game g = new Game();
        g.id = "test-shirt";
        g.home = "ARS";
        g.away = "NEW";
        g.homeName = "Arsenal";
        g.awayName = "Newcastle";
        g.homeFull = "Arsenal";
        g.awayFull = "Newcastle United";
        g.homeId = "359";
        g.awayId = "361";
        g.homeColor = "e20520";
        g.homeAlt = "003399";
        g.awayColor = "000000";
        g.awayAlt = "ffffff";
        return g;
    }
}
