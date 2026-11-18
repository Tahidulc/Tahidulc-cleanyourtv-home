package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
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
 * The scoreboard as a broadcaster's own behaves, from the channel's own scoreboard under it (BugWatch), as on the TV:
 * away while the channel's is (a replay), back with it for live play; whatever grows out of the channel's covered where
 * it is by a plate in the board's look, its words read and written again in the board's letters, the board never moved.
 * From the player's own frames (FrameSampler: the video alone, the board never in them), the place round the board in
 * the stream's own pixels, about three times a second while a match is in play. The sums on a thread of their own.
 */
final class BugCover {
    /** What the player's scoreboard gives it and does with what it says. */
    interface Host {
        /** Whether to watch now: a football match in play on the board, the player full size, nothing being moved. */
        boolean watching();

        /** The stream's picture in its own pixels {width, height}. */
        int[] videoPixels();

        /** The board on the picture now, {x, y, w, h} in the stream's pixels; null when it is not up. */
        int[] boardInStream();

        /** The channel's own scoreboard found on the picture, {l, t, r, b} in the stream's pixels; null when not. */
        int[] bugInStream();

        /** The channel playing, to know another from it. */
        String channelKey();

        /** Part of the picture (src, the stream's pixels) copied into into; false when no copy could be asked for now. */
        boolean copyPart(Rect src, Bitmap into, FrameSampler.Got got);

        /** The board away (a replay) or back. */
        void away(boolean on);

        /** The plates now: each {l, t, r, b} in the stream's pixels with its words (null: none read); none: empty. */
        void plates(List<int[]> rects, List<String> words);
    }

    static final long EVERY_MS = 300L, READ_AFTER_MS = 450L;
    private static final int READ_H = 96;

    private final Context c;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BugCover");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });
    private BugWatch watch;
    private String key = "", bugKey = "";
    private boolean running, away, reading;
    private Bitmap into;
    private final List<int[]> plates = new ArrayList<>();
    private final List<String> words = new ArrayList<>();
    private final List<Long> since = new ArrayList<>();
    private final List<Boolean> asked = new ArrayList<>();
    private ChannelReader reader;
    private String said = "";
    private final Runnable loop = this::loop;

    BugCover(Context c, Host host) {
        this.c = c.getApplicationContext();
        this.host = host;
    }

    void start() {
        if (running) return;
        running = true;
        h.post(loop);
    }

    /** Stopped (the player going): the board not held away, no plates; what was learned is kept for coming back. */
    void stop() {
        running = false;
        h.removeCallbacks(loop);
        idle();
        if (reader != null) {
            reader.close();
            reader = null;
        }
        reading = false;
    }

    void release() {
        stop();
        work.shutdownNow();
    }

    /** Not watching for now: the board back, the plates folded away. */
    private void idle() {
        if (away) {
            away = false;
            host.away(false);
        }
        if (!plates.isEmpty()) {
            plates.clear();
            words.clear();
            since.clear();
            asked.clear();
            host.plates(new ArrayList<>(), new ArrayList<>());
        }
    }

    /** Pure: whether the channel's own scoreboard is watched at all: for the board away in its replays, or its pop-ups covered. */
    static boolean wanted(boolean awayInReplays, boolean coverPopOuts) {
        return awayInReplays || coverPopOuts;
    }

    /**
     * Pure: whether what grows out of the channel's scoreboard is covered now: only with "Cover the channel's score
     * pop-ups" on (whatever "Away in replays" says), and never while the channel's own is gone (a replay).
     */
    static boolean covers(boolean channelsAway, boolean coverPopOuts) {
        return coverPopOuts && !channelsAway;
    }

    private void loop() {
        h.removeCallbacks(loop);
        if (!running) return;
        boolean popOuts = ScoreBoard.coverPopOuts(c);
        // Switched off (here, in Settings or in the player's options): the plates come down at once, not at the next picture.
        if (!popOuts && !plates.isEmpty()) {
            plates.clear();
            words.clear();
            since.clear();
            asked.clear();
            host.plates(new ArrayList<>(), new ArrayList<>());
        }
        boolean wanted = wanted(ScoreBoard.awayInReplays(c), popOuts);
        int[] vp = host.videoPixels();
        int[] b = wanted && host.watching() ? host.boardInStream() : null;
        if (b == null || vp[0] <= 0 || vp[1] <= 0) {
            idle();
            h.postDelayed(loop, 1_000L);
            return;
        }
        int[] bug = host.bugInStream();
        String k = host.channelKey(), bk = Arrays.toString(bug) + vp[0] + "x" + vp[1];
        BugWatch w = watch;
        BugWatch.Rect core = w == null || !k.equals(key) || !bk.equals(bugKey) ? null : BugWatch.coreIn(w.place(), b[0], b[1], b[2], b[3]);
        if (core == null) {
            BugWatch.Place p = BugWatch.place(vp[0], vp[1], b[0], b[1], b[2], b[3], bug);
            if (p == null) {
                idle();
                h.postDelayed(loop, 1_000L);
                return;
            }
            watch = w = new BugWatch(p);
            key = k;
            bugKey = bk;
            idle();
        } else {
            BugWatch.Rect was = w.core();
            BugWatch ww = w;
            if (was.l != core.l || was.t != core.t || was.r != core.r || was.b != core.b) work.execute(() -> ww.setCore(core));
        }
        // A plate's words first (its own copy), the place's picture after.
        readPlates();
        BugWatch.Place p = w.place();
        if (into == null || into.getWidth() != p.fw || into.getHeight() != p.fh) into = Bitmap.createBitmap(p.fw, p.fh, Bitmap.Config.ARGB_8888);
        BugWatch ws = w;
        boolean ok = host.copyPart(new Rect(p.x, p.y, p.x + p.w, p.y + p.h), into, (got, when) -> {
            // On the sampler's thread: the pixels out of the frame (the bitmap is used again for the next).
            int[] px = null;
            if (got != null && got.getWidth() == p.fw && got.getHeight() == p.fh) {
                px = new int[p.fw * p.fh];
                got.getPixels(px, 0, p.fw, 0, 0, p.fw, p.fh);
            }
            int[] pixels = px;
            if (pixels == null) {
                h.post(() -> h.postDelayed(loop, EVERY_MS));
                return;
            }
            long t = SystemClock.uptimeMillis();
            work.execute(() -> {
                ws.add(pixels, t);
                boolean aw = ws.away();
                List<BugWatch.Rect> ex = ws.extras();
                String st = ws.state();
                h.post(() -> {
                    if (running && ws == watch) apply(ws, aw, ex, st);
                    if (running) h.postDelayed(loop, EVERY_MS);
                });
            });
        });
        // No copy now (one under way): a moment later. A copy asked for: on again from its answer (or, lost, from here).
        h.postDelayed(loop, ok ? 2_000L : 80L);
    }

    private void apply(BugWatch w, boolean aw, List<BugWatch.Rect> ex, String st) {
        if (!st.equals(said)) {
            said = st;
            android.util.Log.i("BugCover", st);
        }
        boolean nowAway = aw && ScoreBoard.awayInReplays(c);
        if (nowAway != away) {
            away = nowAway;
            host.away(away);
        }
        List<int[]> rects = new ArrayList<>();
        if (covers(aw, ScoreBoard.coverPopOuts(c))) {
            BugWatch.Place p = w.place();
            int[] core = p.toScreen(w.core());
            int pad = Math.max(2, Math.round((core[3] - core[1]) * 0.08f));
            int[] vp = host.videoPixels();
            for (BugWatch.Rect r : ex) {
                int[] s = p.toScreen(r);
                rects.add(new int[]{Math.max(0, s[0] - pad), Math.max(0, s[1] - pad), Math.min(vp[0], s[2] + pad), Math.min(vp[1], s[3] + pad)});
            }
        }
        if (setPlates(rects)) host.plates(new ArrayList<>(plates), new ArrayList<>(words));
    }

    /** The plates as the rects say (one about where one was keeps its words); true when any changed. */
    private boolean setPlates(List<int[]> rects) {
        boolean changed = rects.size() != plates.size();
        List<int[]> np = new ArrayList<>();
        List<String> nw = new ArrayList<>();
        List<Long> ns = new ArrayList<>();
        List<Boolean> na = new ArrayList<>();
        long now = SystemClock.uptimeMillis();
        for (int[] r : rects) {
            int was = -1;
            for (int i = 0; i < plates.size(); i++) if (overlap(plates.get(i), r) >= 0.5f) was = i;
            np.add(r);
            nw.add(was >= 0 ? words.get(was) : null);
            ns.add(was >= 0 ? since.get(was) : now);
            na.add(was >= 0 && asked.get(was) && overlap(plates.get(was), r) >= 0.8f);
            if (was < 0 || !Arrays.equals(plates.get(was), r)) changed = true;
        }
        plates.clear();
        plates.addAll(np);
        words.clear();
        words.addAll(nw);
        since.clear();
        since.addAll(ns);
        asked.clear();
        asked.addAll(na);
        return changed;
    }

    /** Pure: how much two boxes {l, t, r, b} share, over what either covers. */
    static float overlap(int[] a, int[] b) {
        int il = Math.max(a[0], b[0]), it = Math.max(a[1], b[1]), ir = Math.min(a[2], b[2]), ib = Math.min(a[3], b[3]);
        long in = (long) Math.max(0, ir - il) * Math.max(0, ib - it);
        long all = (long) (a[2] - a[0]) * (a[3] - a[1]) + (long) (b[2] - b[0]) * (b[3] - b[1]) - in;
        return all <= 0 ? 0f : in / (float) all;
    }

    /** A plate held a moment: what the channel's graphic under it says, read off the frame, for its words. */
    private void readPlates() {
        if (reading || plates.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        int todo = -1;
        for (int i = 0; i < plates.size(); i++) if (!asked.get(i) && now - since.get(i) >= READ_AFTER_MS) todo = i;
        if (todo < 0) return;
        int[] r = plates.get(todo);
        int rw = r[2] - r[0], rh = r[3] - r[1];
        if (rw < 4 || rh < 4) return;
        Bitmap b = Bitmap.createBitmap(Math.max(8, Math.min(2048, Math.round(READ_H * rw / (float) rh))), READ_H, Bitmap.Config.ARGB_8888);
        boolean ok = host.copyPart(new Rect(r[0], r[1], r[2], r[3]), b, (got, when) -> h.post(() -> {
            if (got == null) return;
            if (reader == null) reader = new ChannelReader();
            reading = true;
            boolean go = reader.read(got, (lines, boxes) -> {
                reading = false;
                String w = BugWatch.words(lines);
                int i = indexOf(r);
                if (w != null && i >= 0) {
                    words.set(i, w);
                    android.util.Log.i("BugCover", "a graphic out of the channel's scoreboard said: " + w);
                    host.plates(new ArrayList<>(plates), new ArrayList<>(words));
                }
            });
            if (!go) reading = false;
        }));
        if (ok) asked.set(todo, true);
    }

    private int indexOf(int[] r) {
        for (int i = 0; i < plates.size(); i++) if (Arrays.equals(plates.get(i), r)) return i;
        return -1;
    }
}
