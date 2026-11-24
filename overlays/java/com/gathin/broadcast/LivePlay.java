package com.gathin.broadcast;

import java.util.Arrays;

/**
 * Whether the picture is back in live play after a goal, the same on the TV and the phone: so what a goal brings (its
 * scorer out of the scoreboard, the restart's pop-up) comes once play is on again, never over the replays of it.
 *
 * <p>From small pictures of the whole frame, two or three a second (the TV's window pictures, the phone's own frames).
 * Each is looked at for what a football broadcast's main camera shows in live play: the wide shot, the pitch filling
 * most of the picture below the stands, its players small. The celebrations, the crowd, the benches and the replays'
 * close angles are not wide (a big shape that is not grass, or little grass at all), nor is the wipe into a replay.
 *
 * <p>After a goal reaches the picture, live play is back once the picture has been away from the wide shot a while (the
 * celebrations, the replays) and then held a wide shot long enough: a long while at first, as a replay from the main
 * camera can run ten seconds, less as the minute after the goal goes on and the replays are over. Where the channel
 * hides its own scoreboard in its replays (BugWatch), its coming back says so at once; a VAR check holds it. Once back,
 * nothing is held until the next goal; never more than MOST_MS in all. With no pictures lately it cannot tell (BLIND),
 * and the callers keep their old timing.
 *
 * <p>Pure: the tests feed it drawn frames. Its clock is the caller's (any that only goes forward), the same for all.
 */
public final class LivePlay {
    /** The small pictures' size: the whole frame. */
    public static final int W = 64, H = 36;
    /** What it says after a goal: back in live play; wait; no pictures to tell by; held its longest without seeing play. */
    public static final int LIVE = 1, WAIT = 0, BLIND = -1, LATE = 2;
    /** Pictures this old: blind. */
    static final long STALE_MS = 3_000L;
    /** Never live sooner than this after the goal; held at most this long. */
    public static final long FIRST_MS = 20_000L, MOST_MS = 120_000L;
    /** The picture away from the wide shot this long in all since the goal: its celebrations or replays seen. */
    static final long AWAY_MS = 6_000L;
    /** From this long after the goal, a wide shot counts without them having been seen. */
    static final long SURE_MS = 60_000L;
    /** The channel's own scoreboard back this long after it went in a replay: live play. */
    static final long BACK_MS = 1_500L;
    /** A wide shot: this much of the picture under the stands grass at least (as learned, between these), and no shape. */
    static final float LEVEL_MOST = 0.45f, LEVEL_LEAST = 0.30f, SHAPE_MOST = 0.10f;
    /** A cut between two pictures: their grey, square by square, this different on average (0 to 255). */
    static final float CUT = 30f;

    // ---- one picture ------------------------------------------------------------------------------------------------

    /** Pure: whether a colour is the pitch's green (lit or in shadow, its stripes, under floodlights). */
    static boolean grass(int p) {
        int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (g != max || max < 30 || max > 235) return false;
        int d = max - min;
        if (d * 100 < max * 18) return false;
        // Its hue between about 75 and 165 degrees: neither yellow nor teal.
        return Math.abs(b - r) * 4 <= d * 3;
    }

    /**
     * Pure: a picture w by h looked at under its top quarter (the stands, the channel's scoreboard): {how much is grass,
     * the biggest shape in it that is not grass}, each a share of that part. A band across the top of it (the far stands,
     * the boards) and a strip along its foot (a ticker) are not shapes.
     */
    static float[] look(int[] px, int w, int h) {
        int top = h / 4, rows = h - top, n = rows * w;
        if (px == null || w <= 0 || rows <= 0 || px.length < w * h) return new float[]{0f, 1f};
        boolean[] other = new boolean[n];
        int green = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < w; x++) {
                boolean gr = grass(px[(top + y) * w + x]);
                if (gr) green++;
                else other[y * w + x] = true;
            }
        }
        // The shapes that are not grass, joined side to side and up and down.
        int biggest = 0;
        int[] stack = new int[n];
        boolean[] seen = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!other[i] || seen[i]) continue;
            int sp = 0, area = 0, minX = w, maxX = -1, minY = rows, maxY = -1;
            stack[sp++] = i;
            seen[i] = true;
            while (sp > 0) {
                int j = stack[--sp], x = j % w, y = j / w;
                area++;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                if (x > 0 && other[j - 1] && !seen[j - 1]) { seen[j - 1] = true; stack[sp++] = j - 1; }
                if (x < w - 1 && other[j + 1] && !seen[j + 1]) { seen[j + 1] = true; stack[sp++] = j + 1; }
                if (y > 0 && other[j - w] && !seen[j - w]) { seen[j - w] = true; stack[sp++] = j - w; }
                if (y < rows - 1 && other[j + w] && !seen[j + w]) { seen[j + w] = true; stack[sp++] = j + w; }
            }
            int bw = maxX - minX + 1, bh = maxY - minY + 1;
            boolean band = minY == 0 && bw * 10 >= w * 4 && bh * 100 <= rows * 35;
            boolean strip = maxY == rows - 1 && bw * 2 >= w && bh <= 3;
            if (!band && !strip) biggest = Math.max(biggest, area);
        }
        return new float[]{green / (float) n, biggest / (float) n};
    }

    /** Pure: a wide shot of the pitch, by its grass and its biggest shape, at this grass level. */
    static boolean wide(float grass, float shape, float level) {
        return grass >= level && shape <= SHAPE_MOST;
    }

    /** Pure: the picture's grey, square by square (16 by 9), for telling a cut. */
    static float[] squares(int[] px, int w, int h) {
        float[] out = new float[16 * 9];
        int[] count = new int[16 * 9];
        if (px == null || w <= 0 || h <= 0 || px.length < w * h) return out;
        for (int y = 0; y < h; y++) {
            int sy = Math.min(8, y * 9 / h);
            for (int x = 0; x < w; x++) {
                int p = px[y * w + x], sx = Math.min(15, x * 16 / w);
                out[sy * 16 + sx] += (((p >> 16) & 0xFF) * 77 + ((p >> 8) & 0xFF) * 150 + (p & 0xFF) * 29) >> 8;
                count[sy * 16 + sx]++;
            }
        }
        for (int i = 0; i < out.length; i++) out[i] = count[i] == 0 ? 0f : out[i] / count[i];
        return out;
    }

    /** Pure: how different two pictures' squares are, on average (0 to 255). */
    static float change(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0f;
        float sum = 0f;
        for (int i = 0; i < a.length; i++) sum += Math.abs(a[i] - b[i]);
        return sum / a.length;
    }

    /** Pure: how long a wide shot must have held, sinceGoal ms after the goal, to be live play. */
    static long needMs(long sinceGoal) {
        if (sinceGoal < FIRST_MS) return Long.MAX_VALUE;
        return Math.max(4_000L, 16_000L - (sinceGoal - FIRST_MS) * 3 / 10);
    }

    /** Pure: the grass level a wide shot needs, from what the wide shots have shown lately (their usual share; NaN: none yet). */
    static float level(float usual) {
        if (Float.isNaN(usual)) return LEVEL_MOST;
        return Math.max(LEVEL_LEAST, Math.min(LEVEL_MOST, usual * 0.62f));
    }

    // ---- the pictures as they come ----------------------------------------------------------------------------------

    private static final int KEEP = 360;
    private final float[] lately = new float[KEEP];
    private int kept, next;
    private float[] lastSquares;
    private long lastAt = -1, goalAt = -1, runFrom = -1, awayMs, bugGoneAt = -1, bugBackAt = -1;
    /** The channel's own scoreboard's last going and coming back, kept whatever a goal does (a VAR check asks: VarCheck). */
    private long lastGoneAt = -1, lastBackAt = -1;
    private boolean lastWide, bugAway, checking;
    private String said = "";

    /** A small picture of the whole frame, w by h, seen at at. */
    public synchronized void frame(int[] px, int w, int h, long at) {
        float[] f = look(px, w, h);
        float[] sq = squares(px, w, h);
        boolean cut = lastSquares != null && change(lastSquares, sq) > CUT;
        lastSquares = sq;
        // The wide shots' usual grass, learned while nothing after a goal is held.
        if (goalAt < 0) {
            lately[next] = f[0];
            next = (next + 1) % KEEP;
            kept = Math.min(KEEP, kept + 1);
        }
        boolean wide = wide(f[0], f[1], level(usual()));
        long dt = lastAt < 0 ? 0 : Math.max(0, Math.min(1_500L, at - lastAt));
        if (goalAt >= 0 && at >= goalAt && !wide) awayMs += dt;
        // A wide shot in a replay the channel's scoreboard is gone for never counts: its run starts after.
        if (!wide || bugAway) runFrom = -1;
        else if (!lastWide || cut || runFrom < 0) runFrom = at;
        lastWide = wide;
        lastAt = Math.max(lastAt, at);
        said = String.format(java.util.Locale.ROOT, "grass %.2f shape %.2f %s%s", f[0], f[1], wide ? "wide" : "not wide", cut ? " cut" : "");
    }

    /** The usual grass of a wide shot lately: the share four in five pictures are under (NaN: too few yet). */
    private float usual() {
        if (kept < 40) return Float.NaN;
        float[] s = Arrays.copyOf(lately, kept);
        Arrays.sort(s);
        return s[Math.min(kept - 1, kept * 4 / 5)];
    }

    /** A goal reached the picture at at: from now what it brings waits for live play. */
    public synchronized void goal(long at) {
        goalAt = at;
        awayMs = 0;
        bugGoneAt = -1;
        bugBackAt = -1;
    }

    /** Nothing held any more (half time, full time, another channel): what waits goes. */
    public synchronized void clear() {
        goalAt = -1;
    }

    /** The channel's own scoreboard gone (a replay) or back (BugWatch), at at. */
    public synchronized void bug(boolean away, long at) {
        if (away == bugAway) return;
        bugAway = away;
        runFrom = -1;
        if (away) {
            bugGoneAt = at;
            lastGoneAt = at;
        } else {
            bugBackAt = at;
            lastBackAt = at;
        }
    }

    /**
     * How long the picture has held the main camera's wide shot now, in ms (live play, as far as a picture can tell); 0
     * when it is not on one, the channel's scoreboard is away (a replay), or there are no pictures lately.
     */
    public synchronized long wideMs(long now) {
        if (lastAt < 0 || now - lastAt > STALE_MS || !lastWide || bugAway || runFrom < 0) return 0;
        return Math.max(0, lastAt - runFrom);
    }

    /** Whether the channel's own scoreboard went (a replay) after from and has been back BACK_MS by now. */
    public synchronized boolean backSince(long from, long now) {
        return !bugAway && lastGoneAt >= from && lastBackAt > lastGoneAt && now - lastBackAt >= BACK_MS;
    }

    /** A VAR check under way on the game (or not): what a goal brings waits for its decision and play after it. */
    public synchronized void var(boolean on) {
        checking = on;
    }

    /**
     * What it says now: LIVE (no goal waiting, or play back since it), WAIT, BLIND (no pictures lately: the old timing),
     * or LATE (held its longest, play never seen: what waited may go, a pop-up for the restart is let go). Once LIVE or
     * LATE after a goal, nothing is held until the next.
     */
    public synchronized int verdict(long now) {
        if (goalAt < 0) return LIVE;
        long since = now - goalAt;
        if (since >= MOST_MS) {
            goalAt = -1;
            return LATE;
        }
        if (bugAway || checking) return WAIT;
        // The channel's scoreboard went in a replay after the goal and is back: live play, as the channel says.
        if (bugGoneAt >= goalAt && bugBackAt > bugGoneAt && now - bugBackAt >= BACK_MS) {
            goalAt = -1;
            return LIVE;
        }
        if (lastAt < 0 || now - lastAt > STALE_MS) return BLIND;
        if (since < FIRST_MS) return WAIT;
        boolean seen = awayMs >= AWAY_MS || since >= SURE_MS;
        long run = lastWide && runFrom >= 0 ? lastAt - runFrom : 0;
        if (seen && run >= needMs(since)) {
            goalAt = -1;
            return LIVE;
        }
        return WAIT;
    }

    /** Whether a goal waits for live play (pictures wanted for it, whatever they say yet). */
    public synchronized boolean pending() {
        return goalAt >= 0;
    }

    /** Whether what a goal brings is held now (WAIT): not when there are no pictures to tell by. */
    public synchronized boolean holding(long now) {
        return goalAt >= 0 && verdict(now) == WAIT;
    }

    /** For the log: the last picture as looked at, and what is held. */
    public synchronized String state() {
        return said + (goalAt >= 0 ? ", after a goal (away " + awayMs / 1000 + " s)" : "");
    }
}
