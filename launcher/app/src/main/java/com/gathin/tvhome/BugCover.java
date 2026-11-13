package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.gathin.broadcast.BugWatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Your board as a broadcaster's own behaves, from the channel's own scoreboard under it (BugWatch): away while the
 * channel's is (a replay, the wipe into it), back with it for live play; and whatever grows out of the channel's (a strip
 * with the scorer, an added-time tab, a sub, a card, a stats bar, a box wider than yours) covered where it is by a plate
 * in your board's look, its words read and written again in your board's letters, your board never moved.
 *
 * Its pictures are of TiviMate's window alone (Android 14: yours are never in them), a part of every picture taken for
 * anything else (see-through, the ad-break screen: ScreenShots' ride) and one of its own when none has come for a moment:
 * two or three a second. The sums on a quiet thread of their own; what they say on the main thread to BoardCover, which
 * steps the board aside (as it goes at half time) and back (as it comes in), and lays the plates out under it. Nothing is
 * kept but in memory; nothing is sent anywhere.
 */
final class BugCover {
    private BugCover() {
    }

    /** A picture of its own when none has come this long; looked at this often; let go this long after the last tick. */
    static final long OWN_MS = 600L, LOOP_MS = 200L, GONE_MS = 3_000L;
    /** A plate's words read once it has held this long. */
    static final long READ_AFTER_MS = 450L;
    /** The height the plate's place is made for the reader. */
    private static final int READ_H = 96;
    private static final String TAG = "BugCover";

    private static Handler main;
    private static ExecutorService work;
    private static Context app;
    private static BugWatch watch;
    private static String watchKey = "", watchBug = "";
    private static int sw, sh;
    private static long tickAt, frameAt, lastAt;
    private static boolean looping, away, reading;
    private static String said = "";
    private static ChannelReader reader;

    /** A plate over something grown out of the channel's scoreboard: where (the screen's pixels), its words once read. */
    static final class Plate {
        final int[] r;
        String words;
        final long since;
        boolean asked;

        Plate(int[] r, long since) {
            this.r = r;
            this.since = since;
        }
    }

    private static final List<Plate> plates = new ArrayList<>();

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static ExecutorService work() {
        if (work == null) {
            work = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, TAG);
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY + 1);
                return t;
            });
        }
        return work;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("bug_cover", Context.MODE_PRIVATE);
    }

    /** Your board away in the channel's replays, back for live play: on unless switched off. */
    static boolean replaysOn(Context c) {
        return prefs(c).getBoolean("replays", true);
    }

    static void setReplaysOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("replays", on).apply();
        if (!on && away) {
            away = false;
            BoardCover.bugChanged();
        }
    }

    /** What grows out of the channel's scoreboard covered by plates in your board's look: on unless switched off. */
    static boolean popOutsOn(Context c) {
        return prefs(c).getBoolean("pop_outs", true);
    }

    static void setPopOutsOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("pop_outs", on).apply();
        if (!on && !plates.isEmpty()) {
            plates.clear();
            BoardCover.platesChanged();
        }
    }

    /** Whether the channel's own scoreboard is gone now (a replay): your board away too. */
    static boolean away() {
        return away;
    }

    /** The plates now (a copy). */
    static List<Plate> plates() {
        return new ArrayList<>(plates);
    }

    /** The place watched, {x, y, w, h} in the screen's pixels (the plates are all inside it); null when none. */
    static int[] region() {
        if (testRegion != null) return testRegion;
        BugWatch w = watch;
        if (w == null || !looping) return null;
        BugWatch.Place p = w.place();
        return new int[]{p.x, p.y, p.w, p.h};
    }

    /** What it last decided, for the log and the Scoreboard card. */
    static String state() {
        return said;
    }

    // ---- each second, your board wanted over a live match (BoardCover) ----------------------------------------------------

    /** Your board at board {x, y, w, h} (the screen's pixels) over the match on the channel named: the place under it watched. */
    static void tick(Context c, int[] board, String channel) {
        app = c.getApplicationContext();
        if (board == null || !replaysOn(app) && !popOutsOn(app) || !ScreenShots.available()) {
            stop();
            return;
        }
        tickAt = SystemClock.uptimeMillis();
        screen(app);
        float[] f = ChannelSight.seenBox(app, channel);
        int[] bugPx = f == null ? null : new int[]{Math.round(f[0] * sw), Math.round(f[1] * sh), Math.round(f[2] * sw), Math.round(f[3] * sh)};
        String key = BoardPlace.key(channel), bugKey = Arrays.toString(bugPx);
        BugWatch w = watch;
        BugWatch.Rect core = w == null || !key.equals(watchKey) || !bugKey.equals(watchBug) ? null
                : BugWatch.coreIn(w.place(), board[0], board[1], board[2], board[3]);
        if (core == null) {
            BugWatch.Place p = BugWatch.place(sw, sh, board[0], board[1], board[2], board[3], bugPx);
            if (p == null) {
                stop();
                return;
            }
            fresh(p, key, bugKey);
        } else {
            BugWatch.Rect was = w.core();
            if (was.l != core.l || was.t != core.t || was.r != core.r || was.b != core.b) work().execute(() -> w.setCore(core));
        }
        if (!looping) {
            looping = true;
            ride(watch);
            main().removeCallbacks(loop);
            main().post(loop);
        }
    }

    /** A new place watched (another channel, the board moved, the channel's own box found): learned from the start. */
    private static void fresh(BugWatch.Place p, String key, String bugKey) {
        watch = new BugWatch(p);
        watchKey = key;
        watchBug = bugKey;
        lastAt = 0;
        android.util.Log.i(TAG, "watching " + p + (key.isEmpty() ? "" : " on " + key));
        ride(watch);
        if (away) {
            away = false;
            BoardCover.bugChanged();
        }
        if (!plates.isEmpty()) {
            plates.clear();
            BoardCover.platesChanged();
        }
    }

    private static void ride(BugWatch w) {
        if (w == null) return;
        BugWatch.Place p = w.place();
        RectF part = new RectF(p.x / (float) sw, p.y / (float) sh, (p.x + p.w) / (float) sw, (p.y + p.h) / (float) sh);
        ScreenShots.ride(new ScreenShots.Ride(part, p.fw, p.fh, BugCover::got));
    }

    /** Stopped (the board gone, half time, switched off): your board not held away, no plates. The look learned is kept. */
    static void stop() {
        boolean was = looping;
        looping = false;
        ScreenShots.ride(null);
        if (main != null) main.removeCallbacks(loop);
        if (away) {
            away = false;
            if (was) BoardCover.bugChanged();
        }
        if (!plates.isEmpty()) {
            plates.clear();
            BoardCover.platesChanged();
        }
    }

    private static final Runnable loop = BugCover::loop;

    private static void loop() {
        if (!looping) return;
        long now = SystemClock.uptimeMillis();
        if (now - tickAt > GONE_MS) {
            stop();
            return;
        }
        BugWatch w = watch;
        // None come with anything else's lately: one of its own (its part comes through the ride, as for any other).
        if (w != null && now - frameAt >= OWN_MS && ScreenShots.ready() && ScreenShots.windowOnly()) {
            ScreenShots.take(new RectF[]{new RectF(0, 0, 1, 1)}, new int[][]{{16, 9}}, (parts, at, own) -> {
                if (parts[0] != null) parts[0].recycle();
            });
        }
        readPlates(now);
        main().postDelayed(loop, LOOP_MS);
    }

    /** A part of a window picture (the ride): handed to the watch on its own thread, what it says back on this one. */
    private static void got(Bitmap b, long at) {
        BugWatch w = watch;
        if (!looping || w == null || at == lastAt || b.getWidth() != w.place().fw || b.getHeight() != w.place().fh) {
            b.recycle();
            return;
        }
        lastAt = at;
        frameAt = SystemClock.uptimeMillis();
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        b.recycle();
        long t = frameAt;
        work().execute(() -> {
            w.add(px, t);
            boolean aw = w.away();
            List<BugWatch.Rect> ex = w.extras();
            String st = w.state();
            main().post(() -> {
                if (w == watch && looping) apply(w, aw, ex, st);
            });
        });
    }

    private static void apply(BugWatch w, boolean aw, List<BugWatch.Rect> ex, String st) {
        if (!st.equals(said)) {
            said = st;
            android.util.Log.i(TAG, st);
        }
        boolean nowAway = aw && replaysOn(app);
        if (nowAway != away) {
            away = nowAway;
            BoardCover.bugChanged();
        }
        List<int[]> rects = new ArrayList<>();
        if (!aw && popOutsOn(app)) {
            BugWatch.Place p = w.place();
            int[] core = p.toScreen(w.core());
            int pad = Math.max(3, Math.round((core[3] - core[1]) * 0.08f));
            for (BugWatch.Rect r : ex) {
                int[] s = p.toScreen(r);
                rects.add(new int[]{Math.max(0, s[0] - pad), Math.max(0, s[1] - pad), Math.min(sw, s[2] + pad), Math.min(sh, s[3] + pad)});
            }
        }
        if (setPlates(rects, SystemClock.uptimeMillis())) BoardCover.platesChanged();
    }

    /** The plates as the rects say: one kept (its words with it) where it is about the same; true when any changed. */
    private static boolean setPlates(List<int[]> rects, long now) {
        List<Plate> next = new ArrayList<>();
        boolean changed = rects.size() != plates.size();
        for (int[] r : rects) {
            Plate was = null;
            for (Plate p : plates) if (overlap(p.r, r) >= 0.5f) was = p;
            if (was != null && Arrays.equals(was.r, r)) {
                next.add(was);
                continue;
            }
            Plate p = new Plate(r, was != null ? was.since : now);
            if (was != null) {
                p.words = was.words;
                p.asked = was.asked && overlap(was.r, r) >= 0.8f;
            }
            next.add(p);
            changed = true;
        }
        plates.clear();
        plates.addAll(next);
        return changed;
    }

    /** Pure: how much two boxes {l, t, r, b} share, over what either covers. */
    static float overlap(int[] a, int[] b) {
        int il = Math.max(a[0], b[0]), it = Math.max(a[1], b[1]), ir = Math.min(a[2], b[2]), ib = Math.min(a[3], b[3]);
        long in = (long) Math.max(0, ir - il) * Math.max(0, ib - it);
        long all = (long) (a[2] - a[0]) * (a[3] - a[1]) + (long) (b[2] - b[0]) * (b[3] - b[1]) - in;
        return all <= 0 ? 0f : in / (float) all;
    }

    // ---- the plates' words: what the channel's graphic said, read off it ---------------------------------------------

    private static void readPlates(long now) {
        if (reading || plates.isEmpty()) return;
        Plate todo = null;
        for (Plate p : plates) if (!p.asked && now - p.since >= READ_AFTER_MS) todo = p;
        if (todo == null) return;
        Plate p = todo;
        RectF part = new RectF(p.r[0] / (float) sw, p.r[1] / (float) sh, p.r[2] / (float) sw, p.r[3] / (float) sh);
        boolean asked = ScreenShots.take(new RectF[]{part}, new int[][]{{0, READ_H}}, (parts, at, own) -> {
            Bitmap b = parts[0];
            // Only TiviMate's window: the whole screen has the plate itself in it.
            if (b == null || !own) {
                if (b != null) b.recycle();
                return;
            }
            if (reader == null) reader = new ChannelReader();
            reading = true;
            boolean ok = reader.read(b, (lines, boxes) -> {
                reading = false;
                b.recycle();
                String w = words(lines);
                if (w != null && plates.contains(p)) {
                    p.words = w;
                    android.util.Log.i(TAG, "a graphic out of the channel's scoreboard said: " + w);
                    BoardCover.platesChanged();
                }
            });
            if (!ok) {
                reading = false;
                b.recycle();
            }
        });
        if (asked) p.asked = true;
    }

    /** The words read off a channel's graphic, as your plate writes them (BugWatch.words); null when none. */
    static String words(List<String> lines) {
        return BugWatch.words(lines);
    }

    private static void screen(Context c) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);
    }

    // ---- adb: a test without a match --------------------------------------------------------------------------------------

    /** The place the test's plate is drawn in (no watch then). */
    private static int[] testRegion;

    /** adb (test_bug_cover): your board away for four seconds as if in a replay, then a plate under it for six. */
    static void test(Context c, int[] board) {
        app = c.getApplicationContext();
        if (board == null) return;
        screen(app);
        testRegion = new int[]{Math.max(0, board[0] - board[3]), Math.max(0, board[1] - board[3]),
                Math.min(sw, board[0] + board[2] * 2) - Math.max(0, board[0] - board[3]), Math.min(sh, board[1] + board[3] * 4) - Math.max(0, board[1] - board[3])};
        away = true;
        BoardCover.bugChanged();
        main().postDelayed(() -> {
            away = false;
            BoardCover.bugChanged();
        }, 4_000L);
        main().postDelayed(() -> {
            int h = board[3];
            int[] r = {board[0], board[1] + h, board[0] + Math.round(board[2] * 0.8f), board[1] + h + Math.round(h * 0.9f)};
            Plate p = new Plate(r, SystemClock.uptimeMillis());
            p.asked = true;
            p.words = "GOAL  OKAFOR 33'";
            plates.clear();
            plates.add(p);
            BoardCover.platesChanged();
        }, 6_500L);
        main().postDelayed(() -> {
            plates.clear();
            BoardCover.platesChanged();
        }, 12_500L);
        main().postDelayed(() -> testRegion = null, 13_500L);
    }
}
