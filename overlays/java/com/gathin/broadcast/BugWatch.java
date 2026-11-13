package com.gathin.broadcast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The channel's own scoreboard watched from under yours, so yours behaves as a broadcaster's does: away while the
 * channel's is (a replay, the wipe into it, a fade), back with it for live play; and whatever grows out of the
 * channel's (a strip dropping under it with the scorer, an added-time tab, a sub or a card, a stats bar, or the box
 * itself wider than yours) found, so it can be covered where it is, your board never moved.
 *
 * From small pictures of the place round your board, a few a second, of the channel's picture alone (the TV's window
 * pictures, the phone's own frames: yours is never in them). The channel's scoreboard is learned first: the pixels of
 * its box that stay the same picture after picture over some ten seconds (its outline, its panels, the clubs' names;
 * the clock's seconds and the video under a see-through panel never do), with their colours. A fresh picture then says
 * how much of that is there: nearly all of it while the box is up, little once the picture runs where it was. Gone for
 * a few pictures running is a replay; back for two, live play. Learned again now and then from pictures with the box
 * plainly there (a new score), and from scratch when something else holds still in its place for a while (another
 * look after half time), or after two minutes with nothing (a look learned wrong never keeps yours away).
 *
 * Round it: a graphic holds still while the picture moves under and round it. Parts of the place outside your board
 * that have held still for the last few pictures, joined, touching your board (or inside the channel's box where it
 * reaches past yours), solid enough, no bigger than a few of your board, while the picture round them moves, and that
 * have just come (the place looked otherwise moments before) or are in the channel's own colours or where one was seen
 * before: what grew out of the channel's scoreboard. Taken as one after two pictures saying so; let go once its place
 * moves again. A still picture all round (a camera held still, a cut to a still shot) says nothing either way.
 *
 * Pure: the tests feed it drawn frames.
 */
public final class BugWatch {
    // ---- the place ------------------------------------------------------------------------------------------------

    /** About this many pixels high your board is in the small pictures. */
    public static final int BOARD_PX = 22;
    /** The small pictures' widest. */
    public static final int MOST_W = 420;

    /** A box in a small picture's pixels: l, t inside it, r, b just past it. */
    public static final class Rect {
        public final int l, t, r, b;

        public Rect(int l, int t, int r, int b) {
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
        }

        public int width() {
            return r - l;
        }

        public int height() {
            return b - t;
        }

        int area() {
            return Math.max(0, r - l) * Math.max(0, b - t);
        }

        Rect union(Rect o) {
            return new Rect(Math.min(l, o.l), Math.min(t, o.t), Math.max(r, o.r), Math.max(b, o.b));
        }

        /** How much the two share, over what either covers (0 to 1). */
        float iou(Rect o) {
            int il = Math.max(l, o.l), it = Math.max(t, o.t), ir = Math.min(r, o.r), ib = Math.min(b, o.b);
            int in = Math.max(0, ir - il) * Math.max(0, ib - it);
            int all = area() + o.area() - in;
            return all <= 0 ? 0f : in / (float) all;
        }

        /** How much of this one the other covers (0 to 1). */
        float within(Rect o) {
            int il = Math.max(l, o.l), it = Math.max(t, o.t), ir = Math.min(r, o.r), ib = Math.min(b, o.b);
            int in = Math.max(0, ir - il) * Math.max(0, ib - it);
            return area() <= 0 ? 0f : in / (float) area();
        }

        @Override public String toString() {
            return "[" + l + "," + t + " - " + r + "," + b + "]";
        }
    }

    /**
     * Where the pictures are taken (x, y, w, h in the screen's pixels, or the stream's), how small (fw by fh), and in
     * them your board (c) and the channel's scoreboard (bug: where it was found, else your board's place).
     */
    public static final class Place {
        public final int x, y, w, h, fw, fh;
        public final Rect core, bug;

        Place(int x, int y, int w, int h, int fw, int fh, Rect core, Rect bug) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.fw = fw;
            this.fh = fh;
            this.core = core;
            this.bug = bug;
        }

        /** The same place and size (a watch made for one goes on for the other). */
        public boolean same(Place o) {
            return o != null && o.x == x && o.y == y && o.w == w && o.h == h && o.fw == fw && o.fh == fh;
        }

        /** A box in the small pictures as the screen's pixels: {left, top, right, bottom}. */
        public int[] toScreen(Rect r) {
            float kx = w / (float) fw, ky = h / (float) fh;
            return new int[]{x + Math.round(r.l * kx), y + Math.round(r.t * ky), x + Math.round(r.r * kx), y + Math.round(r.b * ky)};
        }

        @Override public String toString() {
            return "[" + x + "," + y + " " + w + "x" + h + " as " + fw + "x" + fh + ", board " + core + ", theirs " + bug + "]";
        }
    }

    /**
     * Pure: the place to watch for your board at (bx, by, bw, bh) on a screen sw by sh, the channel's scoreboard found
     * at bug ({left, top, right, bottom}; null: under yours): the boxes and round them, toward the middle of the screen
     * by three of your board's heights and one of its widths (where a broadcaster's strips come out), the other ways a
     * little; small enough that your board is about BOARD_PX high. Null when there is no board.
     */
    public static Place place(int sw, int sh, int bx, int by, int bw, int bh, int[] bug) {
        if (sw <= 0 || sh <= 0 || bw < 4 || bh < 4) return null;
        int ul = bx, ut = by, ur = bx + bw, ub = by + bh;
        if (bug != null && bug.length == 4 && bug[2] > bug[0] && bug[3] > bug[1]) {
            ul = Math.min(ul, bug[0]);
            ut = Math.min(ut, bug[1]);
            ur = Math.max(ur, bug[2]);
            ub = Math.max(ub, bug[3]);
        }
        boolean top = by + bh / 2 < sh / 2, left = bx + bw / 2 < sw / 2;
        int x0 = ul - (left ? Math.round(bw * 0.4f) : bw), x1 = ur + (left ? bw : Math.round(bw * 0.4f));
        int y0 = ut - (top ? Math.round(bh * 0.6f) : bh * 3), y1 = ub + (top ? bh * 3 : Math.round(bh * 0.6f));
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(sw, x1);
        y1 = Math.min(sh, y1);
        int w = x1 - x0, h = y1 - y0;
        if (w < 8 || h < 8) return null;
        float k = Math.max(0.08f, Math.min(0.6f, BOARD_PX / (float) bh));
        int fw = Math.max(16, Math.min(MOST_W, Math.round(w * k)));
        k = fw / (float) w;
        int fh = Math.max(8, Math.round(h * k));
        Rect core = scaled(bx - x0, by - y0, bx + bw - x0, by + bh - y0, k, fw, fh);
        Rect b = bug == null || bug.length != 4 || bug[2] <= bug[0] || bug[3] <= bug[1] ? core
                : scaled(bug[0] - x0, bug[1] - y0, bug[2] - x0, bug[3] - y0, k, fw, fh);
        return new Place(x0, y0, w, h, fw, fh, core, b);
    }

    private static Rect scaled(int l, int t, int r, int b, float k, int fw, int fh) {
        int sl = clamp(Math.round(l * k), 0, fw - 1), st = clamp(Math.round(t * k), 0, fh - 1);
        return new Rect(sl, st, clamp(Math.round(r * k), sl + 1, fw), clamp(Math.round(b * k), st + 1, fh));
    }

    // ---- the channel's scoreboard: learned, then there or not -----------------------------------------------------

    /** Pictures of its box kept for learning it, and how many of them a pixel must agree in to be the box's own. */
    static final int KEEP = 9, STEADY = 7;
    /** A new look while away: from this many pictures, all but one agreeing. */
    static final int NEW_LOOK = 7, NEW_LOOK_STEADY = 6;
    /** How far apart pictures are kept: while it is learned (and for a new look), and after (from the box plainly up). */
    static final long KEEP_GAP = 1_200L, AGAIN_GAP = 3_000L;
    /** Off by at most this much (each colour, 0 to 255) a pixel agrees: learning it, and saying it is there. */
    static final int NEAR = 28, MATCH_NEAR = 42;
    /** The share of its box its own steady pixels must be, and of them how many on an edge (words, an outline). */
    static final float MASK_SHARE = 0.1f, EDGE_SHARE = 0.03f;
    static final int EDGE = 36;
    /** Under GONE it is gone, over BACK there; for this many pictures running. */
    public static final float GONE = 0.45f, BACK = 0.7f;
    static final int GONE_CHECKS = 3, BACK_CHECKS = 2;
    /** Away this long with nothing learned again: the look it has is let go (yours back, learned again). */
    static final long AWAY_LONGEST = 120_000L;

    // ---- what grows out of it ------------------------------------------------------------------------------------

    /** Pictures held: the last STILL agree for a graphic; the ones before them show whether it has just come. */
    static final int STILL = 3, RECENT = 6;
    /** Off by at most this much a pixel holds still; by more than CAME it changed (a graphic came). */
    static final int STILL_NEAR = 16, CAME = 44;
    /** Of the picture round it, at least this share must be moving to tell a graphic from a still shot. */
    static final float MOVING = 0.15f;
    /** A graphic's least share of its box, and of it just come, in the channel's colours, or the channel's box. */
    static final float FILL = 0.45f, CAME_SHARE = 0.35f, PALETTE_SHARE = 0.5f, BUG_SHARE = 0.5f;
    /** Its place moving again over this share of it, two pictures running: gone. */
    static final float GONE_SHARE = 0.35f;
    static final int CONFIRM = 2, MISSES = 2, MOST = 3;

    private final Place p;
    private final int n, boxW, boxH;
    /** Your board in the small pictures (the place's, until it moves a little). */
    private Rect core;
    /** The channel's box as learned: its colours and which of its pixels are its own (steady), how many. */
    private int[] look;
    private boolean[] mask;
    private int maskN;
    /** Its colours (64 bins: four steps of each), the ones it is mostly made of. */
    private final boolean[] palette = new boolean[64];
    private final ArrayDeque<int[]> kept = new ArrayDeque<>(), awayKept = new ArrayDeque<>();
    private long keptAt = Long.MIN_VALUE / 2, awayKeptAt = Long.MIN_VALUE / 2;
    private int fresh;
    private float presence = -1f;
    private int gone, back;
    private boolean away;
    private long awaySince;
    private String said = "learning the channel's scoreboard";

    private final ArrayDeque<int[]> recent = new ArrayDeque<>();
    private final List<Seen> seen = new ArrayList<>(), extras = new ArrayList<>();
    /** Where its strips have come out before (they come out in the same places). */
    private final List<Rect> places = new ArrayList<>();
    private int[] label;
    private boolean[] still;

    /** A graphic seen: where, how many pictures running, and (taken) how many with its place moving again, since when. */
    private static final class Seen {
        Rect r;
        int hits, misses;
        long since;

        Seen(Rect r, long since) {
            this.r = r;
            this.since = since;
        }
    }

    public BugWatch(Place p) {
        this.p = p;
        core = p.core;
        n = p.fw * p.fh;
        boxW = p.bug.width();
        boxH = p.bug.height();
    }

    public Place place() {
        return p;
    }

    /** Whether the channel's scoreboard is learned (until then it is never said to be away). */
    public boolean known() {
        return look != null;
    }

    /** Whether the channel's scoreboard is gone now (a replay): yours away too. */
    public boolean away() {
        return away;
    }

    /** How much of it was there on the last picture (0 to 1; -1 before it is learned). */
    public float presence() {
        return presence;
    }

    /** Your board in the small pictures now (it grows a little with an added-time tab, shrinks again). */
    public Rect core() {
        return core;
    }

    /**
     * Pure: your board at (bx, by, bw, bh) on the screen (or the stream) in place p's small pictures, when it is still well
     * inside the place (room round it of half its height, but where the place meets the screen's edge); null when it is
     * not (a new place to watch, and a new watch).
     */
    public static Rect coreIn(Place p, int bx, int by, int bw, int bh) {
        if (p == null) return null;
        float k = p.fw / (float) p.w;
        int l = Math.round((bx - p.x) * k), t = Math.round((by - p.y) * k), r = Math.round((bx + bw - p.x) * k), b = Math.round((by + bh - p.y) * k);
        int room = Math.max(1, (b - t) / 2);
        boolean edgeL = p.x == 0 && l <= 0, edgeT = p.y == 0 && t <= 0;
        if (r - l < 2 || b - t < 2 || (l < room && !edgeL) || (t < room && !edgeT) || r > p.fw - room || b > p.fh - room) return null;
        return new Rect(Math.max(0, l), Math.max(0, t), r, b);
    }

    /** Your board's place in the small pictures now (coreIn's), all that was learned kept. */
    public void setCore(Rect c) {
        if (c == null) return;
        if (c.l != core.l || c.t != core.t || c.r != core.r || c.b != core.b) {
            core = c;
            seen.clear();
        }
    }

    /** What grows out of it now, outside your board, in the small pictures' pixels (none: empty). */
    public List<Rect> extras() {
        List<Rect> out = new ArrayList<>();
        for (Seen s : extras) out.add(s.r);
        return out;
    }

    /** What it last decided, in a few words (for the log). */
    public String state() {
        return said + (look == null ? "" : String.format(java.util.Locale.ROOT, ", there %.2f", presence)) + (extras.isEmpty() ? "" : ", " + extras.size() + " out of it");
    }

    /** A fresh picture of the place (fw by fh, ARGB) at (ms): kept for a few pictures, so never changed after. */
    public void add(int[] frame, long at) {
        if (frame == null || frame.length != n) return;
        int[] f = frame;
        int[] crop = crop(f);
        if (look == null) {
            presence = -1f;
            if (at - keptAt >= KEEP_GAP) {
                keep(kept, crop, KEEP);
                keptAt = at;
                if (kept.size() >= KEEP && adopt(learn(new ArrayList<>(kept), STEADY))) {
                    said = "learned the channel's scoreboard";
                    fresh = 0;
                }
            }
        } else {
            presence = presence(crop);
            step(at);
            if (!away && presence >= BACK && at - keptAt >= AGAIN_GAP) {
                keep(kept, crop, KEEP);
                keptAt = at;
                // Learned again from pictures with it plainly up (its score changed, its look moved a little).
                if (++fresh >= KEEP && kept.size() >= KEEP) {
                    fresh = 0;
                    adopt(learn(new ArrayList<>(kept), STEADY));
                }
            }
            if (away) {
                if (at - awayKeptAt >= KEEP_GAP) {
                    keep(awayKept, crop, NEW_LOOK);
                    awayKeptAt = at;
                    // Something else holding still in its place (another look): learned as the scoreboard, back.
                    if (awayKept.size() >= NEW_LOOK && adopt(learn(new ArrayList<>(awayKept), NEW_LOOK_STEADY))) {
                        away = false;
                        gone = 0;
                        back = 0;
                        kept.clear();
                        kept.addAll(awayKept);
                        awayKept.clear();
                        presence = presence(crop);
                        said = "the channel's scoreboard has a new look: learned again";
                    }
                }
                if (away && at - awaySince >= AWAY_LONGEST) forget("gone two minutes: learned again");
            }
        }
        outOfIt(f, at);
    }

    /** All forgotten: learned again from the start (another channel, or a look learned wrong). */
    public void forget(String why) {
        look = null;
        mask = null;
        maskN = 0;
        kept.clear();
        awayKept.clear();
        keptAt = Long.MIN_VALUE / 2;
        away = false;
        gone = 0;
        back = 0;
        presence = -1f;
        seen.clear();
        extras.clear();
        said = why;
    }

    private void step(long at) {
        if (presence < GONE) {
            gone++;
            back = 0;
        } else if (presence > BACK) {
            back++;
            gone = 0;
        } else {
            gone = 0;
            back = 0;
        }
        if (!away && gone >= GONE_CHECKS) {
            away = true;
            awaySince = at;
            awayKept.clear();
            awayKeptAt = Long.MIN_VALUE / 2;
            seen.clear();
            extras.clear();
            said = "the channel's scoreboard gone";
        } else if (away && back >= BACK_CHECKS) {
            away = false;
            awayKept.clear();
            said = "the channel's scoreboard back";
        }
    }

    private int[] crop(int[] f) {
        int[] c = new int[boxW * boxH];
        for (int y = 0; y < boxH; y++) System.arraycopy(f, (p.bug.t + y) * p.fw + p.bug.l, c, y * boxW, boxW);
        return c;
    }

    private static void keep(ArrayDeque<int[]> ring, int[] c, int most) {
        ring.addLast(c);
        while (ring.size() > most) ring.removeFirst();
    }

    /** A learned look: its colours (the middle of the pictures', pixel by pixel) and its own steady pixels. */
    private static final class Look {
        final int[] look;
        final boolean[] mask;
        final int count;

        Look(int[] look, boolean[] mask, int count) {
            this.look = look;
            this.mask = mask;
            this.count = count;
        }
    }

    /** Taken as the scoreboard's look when there is one; false (the one it had kept) when not. */
    private boolean adopt(Look l) {
        if (l == null) return false;
        look = l.look;
        mask = l.mask;
        maskN = l.count;
        java.util.Arrays.fill(palette, false);
        int[] bins = new int[64];
        for (int i = 0; i < look.length; i++) if (mask[i]) bins[bin(look[i])]++;
        for (int b = 0; b < 64; b++) palette[b] = bins[b] * 25 >= maskN;
        return true;
    }

    /**
     * Pure enough: the box's look from pictures of it, or null when there is none to learn: too few of its pixels agree
     * in steady pictures (the picture running under an empty place), or what agrees has no edges (a black screen, a
     * still shot of the grass).
     */
    private Look learn(List<int[]> frames, int steady) {
        int nf = frames.size(), m = boxW * boxH;
        if (nf < steady || m == 0) return null;
        int[] med = new int[m];
        boolean[] own = new boolean[m];
        int[] r = new int[nf], g = new int[nf], b = new int[nf];
        int count = 0;
        for (int i = 0; i < m; i++) {
            for (int k = 0; k < nf; k++) {
                int c = frames.get(k)[i];
                r[k] = (c >> 16) & 0xFF;
                g[k] = (c >> 8) & 0xFF;
                b[k] = c & 0xFF;
            }
            int mr = median(r, nf), mg = median(g, nf), mb = median(b, nf);
            med[i] = 0xFF000000 | mr << 16 | mg << 8 | mb;
            int agree = 0;
            for (int k = 0; k < nf; k++) {
                int c = frames.get(k)[i];
                if (near(c, med[i], NEAR)) agree++;
            }
            if (agree >= steady) {
                own[i] = true;
                count++;
            }
        }
        if (count < Math.max(8, MASK_SHARE * m)) return null;
        int edges = 0;
        for (int y = 0; y + 1 < boxH; y++) {
            for (int x = 0; x + 1 < boxW; x++) {
                int i = y * boxW + x;
                if (!own[i]) continue;
                int v = lum(med[i]);
                if (Math.abs(v - lum(med[i + 1])) + Math.abs(v - lum(med[i + boxW])) >= EDGE) edges++;
            }
        }
        if (edges < Math.max(6, EDGE_SHARE * m)) return null;
        return new Look(med, own, count);
    }

    /** The share of the box's own pixels showing its own colours on a picture of the box. */
    private float presence(int[] crop) {
        if (look == null || maskN == 0) return -1f;
        int there = 0;
        for (int i = 0; i < crop.length; i++) if (mask[i] && near(crop[i], look[i], MATCH_NEAR)) there++;
        return there / (float) maskN;
    }

    // ---- what grows out of it --------------------------------------------------------------------------------------

    private void outOfIt(int[] f, long at) {
        recent.addFirst(f);
        while (recent.size() > RECENT) recent.removeLast();
        if (look == null || away || presence < BACK || recent.size() < STILL) {
            if (look == null || away) {
                seen.clear();
                extras.clear();
            }
            return;
        }
        int fw = p.fw, fh = p.fh;
        if (label == null) {
            label = new int[n];
            still = new boolean[n];
        }
        int[][] fr = recent.toArray(new int[0][]);
        Rect c = core;
        // Held still over the last few pictures, outside your board.
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                int i = y * fw + x;
                if (x >= c.l && x < c.r && y >= c.t && y < c.b) {
                    still[i] = false;
                    continue;
                }
                boolean s = true;
                for (int k = 1; k < STILL && s; k++) s = near(fr[k][i], fr[0][i], STILL_NEAR);
                still[i] = s;
            }
        }
        // Its own strips let go once their places move again (whatever the picture round them does).
        for (Iterator<Seen> it = extras.iterator(); it.hasNext(); ) {
            Seen e = it.next();
            if (movingShare(e.r) >= GONE_SHARE) {
                if (++e.misses >= MISSES) {
                    remember(e.r);
                    it.remove();
                }
            } else {
                e.misses = 0;
            }
        }
        List<Rect> found = graphics(fr);
        // The picture round them still too (a camera held still, a cut to a still shot): nothing to go by.
        if (movingRound(found) < MOVING) return;
        List<Seen> next = new ArrayList<>();
        for (Rect r : found) {
            Seen was = null;
            for (Seen s : seen) if (s.r.iou(r) >= 0.4f || r.within(s.r) >= 0.8f || s.r.within(r) >= 0.8f) was = s;
            Seen s = was != null ? was : new Seen(r, at);
            s.r = r;
            s.hits++;
            next.add(s);
            boolean before = false;
            for (Rect pl : places) if (pl.iou(r) >= 0.5f) before = true;
            if (s.hits >= CONFIRM || before) take(s);
        }
        seen.clear();
        seen.addAll(next);
    }

    /** A graphic taken as come out of the channel's scoreboard: added, or the one there grown to it. */
    private void take(Seen s) {
        for (Seen e : extras) {
            if (e.r.iou(s.r) > 0f || e.r.within(s.r) > 0f) {
                e.r = e.r.union(s.r);
                e.misses = 0;
                return;
            }
        }
        if (extras.size() >= MOST) return;
        Seen e = new Seen(s.r, s.since);
        extras.add(e);
        said = "a graphic out of the channel's scoreboard at " + s.r;
    }

    private void remember(Rect r) {
        for (int i = 0; i < places.size(); i++) {
            if (places.get(i).iou(r) >= 0.5f) {
                places.set(i, r);
                return;
            }
        }
        places.add(r);
        if (places.size() > 6) places.remove(0);
    }

    /** The share of a box (outside your board) whose pixels did not hold still. */
    private float movingShare(Rect r) {
        int all = 0, moving = 0;
        Rect c = core;
        for (int y = Math.max(0, r.t); y < Math.min(p.fh, r.b); y++) {
            for (int x = Math.max(0, r.l); x < Math.min(p.fw, r.r); x++) {
                if (x >= c.l && x < c.r && y >= c.t && y < c.b) continue;
                all++;
                if (!still[y * p.fw + x]) moving++;
            }
        }
        return all == 0 ? 0f : moving / (float) all;
    }

    /** The share of the picture round your board and the graphics found (outside both) that moved. */
    private float movingRound(List<Rect> found) {
        int all = 0, moving = 0;
        Rect c = core;
        int pad = 2;
        for (int y = 0; y < p.fh; y++) {
            for (int x = 0; x < p.fw; x++) {
                if (x >= c.l - pad && x < c.r + pad && y >= c.t - pad && y < c.b + pad) continue;
                boolean inside = false;
                for (Rect r : found) if (x >= r.l - pad && x < r.r + pad && y >= r.t - pad && y < r.b + pad) inside = true;
                for (Seen e : extras) if (x >= e.r.l - pad && x < e.r.r + pad && y >= e.r.t - pad && y < e.r.b + pad) inside = true;
                if (inside) continue;
                all++;
                if (!still[y * p.fw + x]) moving++;
            }
        }
        return all == 0 ? 0f : moving / (float) all;
    }

    /** The still parts round your board that look like a broadcaster's graphic come out of the channel's scoreboard. */
    private List<Rect> graphics(int[][] fr) {
        int fw = p.fw, fh = p.fh;
        java.util.Arrays.fill(label, 0);
        Rect c = core, bug = p.bug;
        int ch = Math.max(1, c.height()), cw = Math.max(1, c.width());
        int touch = Math.max(2, Math.round(ch * 0.3f)), least = Math.max(12, ch * ch / 10);
        int[] queue = new int[n];
        List<Rect> out = new ArrayList<>();
        int id = 0;
        for (int s0 = 0; s0 < n; s0++) {
            if (!still[s0] || label[s0] != 0) continue;
            id++;
            int head = 0, tail = 0, area = 0, l = fw, t = fh, r = 0, b = 0, came = 0, inPal = 0, inBug = 0;
            boolean touches = false;
            queue[tail++] = s0;
            label[s0] = id;
            while (head < tail) {
                int i = queue[head++], x = i % fw, y = i / fw;
                area++;
                l = Math.min(l, x);
                t = Math.min(t, y);
                r = Math.max(r, x + 1);
                b = Math.max(b, y + 1);
                if (!touches) {
                    int dx = x < c.l ? c.l - x : x >= c.r ? x - c.r + 1 : 0, dy = y < c.t ? c.t - y : y >= c.b ? y - c.b + 1 : 0;
                    touches = Math.max(dx, dy) <= touch;
                }
                if (x >= bug.l && x < bug.r && y >= bug.t && y < bug.b) inBug++;
                if (palette[bin(fr[0][i])]) inPal++;
                for (int k = STILL; k < fr.length; k++) {
                    if (!near(fr[k][i], fr[0][i], CAME)) {
                        came++;
                        break;
                    }
                }
                for (int d = 0; d < 4; d++) {
                    int j = d == 0 ? (x > 0 ? i - 1 : -1) : d == 1 ? (x + 1 < fw ? i + 1 : -1) : d == 2 ? (y > 0 ? i - fw : -1)
                            : (y + 1 < fh ? i + fw : -1);
                    if (j >= 0 && still[j] && label[j] == 0) {
                        label[j] = id;
                        queue[tail++] = j;
                    }
                }
            }
            if (area < least || !touches) continue;
            int bw = r - l, bh = b - t;
            if (bh > ch * 3.2f || bw > cw * 3f) continue;
            if (area < FILL * bw * bh) continue;
            boolean cameNow = fr.length > STILL && came >= CAME_SHARE * area;
            boolean theirs = inPal >= PALETTE_SHARE * area || inBug >= BUG_SHARE * area;
            boolean before = false;
            Rect box = new Rect(l, t, r, b);
            for (Rect pl : places) if (pl.iou(box) >= 0.5f) before = true;
            if (cameNow || theirs || before) out.add(box);
        }
        // Boxes touching one another as one (a strip and its tab).
        boolean merged = true;
        while (merged && out.size() > 1) {
            merged = false;
            outer:
            for (int i = 0; i < out.size(); i++) {
                for (int j = i + 1; j < out.size(); j++) {
                    Rect a = out.get(i), o = out.get(j);
                    if (a.l <= o.r + 1 && o.l <= a.r + 1 && a.t <= o.b + 1 && o.t <= a.b + 1) {
                        out.set(i, a.union(o));
                        out.remove(j);
                        merged = true;
                        break outer;
                    }
                }
            }
        }
        return out;
    }

    // ---- the words on a plate: what the channel's graphic said ---------------------------------------------------------

    /** Broadcasters' and services' names (and a picture's own tags): never written on your plates. */
    private static final String[] NAMES = {"sky", "tnt", "bt sport", "amazon", "prime video", "dazn", "bbc", "itv", "espn", "nbc",
            "cbs", "fox", "peacock", "paramount", "viaplay", "bein", "canal", "eurosport", "premier sports", "channel 4", "s4c",
            "stv", "rte", "virgin", "now tv", "apple tv", "netflix", "youtube", "hd", "uhd", "4k", "live"};

    /**
     * Pure: the words read off a channel's graphic (a text reader's lines), as your plate writes them: each line tidied
     * (letters, numbers, a few marks), a line with under two letters or numbers, or naming a broadcaster or a service,
     * left out; at most two lines, in capitals, each at most 42 letters. Null when nothing is left.
     */
    public static String words(List<String> lines) {
        if (lines == null) return null;
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (String raw : lines) {
            if (raw == null || n >= 2) continue;
            String s = raw.replaceAll("[^\\p{L}\\p{N} '’+\\-:.%/&()]", " ").replaceAll("\\s+", " ").trim();
            int alnum = 0;
            for (int i = 0; i < s.length(); i++) if (Character.isLetterOrDigit(s.charAt(i))) alnum++;
            if (alnum < 2) continue;
            String low = " " + s.toLowerCase(java.util.Locale.ROOT) + " ";
            boolean named = false;
            for (String name : NAMES) if (low.contains(" " + name + " ")) named = true;
            if (named) continue;
            if (s.length() > 42) s = s.substring(0, 41).trim() + "…";
            if (out.length() > 0) out.append('\n');
            out.append(s.toUpperCase(java.util.Locale.ROOT));
            n++;
        }
        return out.length() == 0 ? null : out.toString();
    }

    // ---- small things ------------------------------------------------------------------------------------------------

    static boolean near(int a, int b, int d) {
        return Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)) <= d && Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)) <= d
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= d;
    }

    static int lum(int c) {
        return (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 150 + (c & 0xFF) * 29) >> 8;
    }

    static int bin(int c) {
        return (((c >> 22) & 3) << 4) | (((c >> 14) & 3) << 2) | ((c >> 6) & 3);
    }

    private static int median(int[] v, int n) {
        int[] s = java.util.Arrays.copyOf(v, n);
        java.util.Arrays.sort(s);
        return s[n / 2];
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
