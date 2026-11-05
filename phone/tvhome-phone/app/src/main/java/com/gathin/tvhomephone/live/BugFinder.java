package com.gathin.tvhomephone.live;

import java.util.ArrayDeque;

/**
 * Where the channel's own scoreboard is on the picture, from a few seconds of small grey frames. The picture moves (the
 * camera follows the ball) but the broadcaster's box stays put: its edges (the box's outline, the clubs' names, the
 * score) are in the same place frame after frame, and its clock's seconds flip about once a second. The box is the
 * cluster of such still edges, filled out to the still pixels around them, near the top (or the foot) of the picture,
 * wider than it is high; one with a ticking clock in it comes first, then one on the left (a channel's own logo sits
 * top right and never ticks). Pure: the tests feed it drawn frames.
 */
final class BugFinder {
    /** A place on the picture as fractions of its width and height, and whether a clock was seen ticking in it. */
    static final class Box {
        final float left, top, right, bottom;
        final boolean ticking;

        Box(float left, float top, float right, float bottom, boolean ticking) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.ticking = ticking;
        }

        float width() { return right - left; }

        float height() { return bottom - top; }

        @Override public String toString() {
            return String.format(java.util.Locale.UK, "[%.3f,%.3f - %.3f,%.3f%s]", left, top, right, bottom, ticking ? " ticking" : "");
        }
    }

    /** How many frames it wants before it says (four a second for about eight seconds). */
    static final int ENOUGH = 32;
    private static final int STILL = 14, BIG = 56, EDGE = 36;

    private final int w, h;
    private byte[] prev;
    private final int[] still, big, small, edges;
    private int frames;

    BugFinder(int w, int h) {
        this.w = w;
        this.h = h;
        still = new int[w * h];
        big = new int[w * h];
        small = new int[w * h];
        edges = new int[w * h];
    }

    int frames() { return frames; }

    /** Whether a row is looked at: the top 40% and the foot's 22% (scoreboards sit there). */
    private boolean band(int y) {
        return y < h * 0.40f || y >= h * 0.78f;
    }

    /** One frame's grey values, w by h, row by row. */
    void add(byte[] lum) {
        if (lum == null || lum.length < w * h) return;
        for (int y = 0; y < h; y++) {
            if (!band(y)) continue;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x, v = lum[i] & 0xFF;
                if (x + 1 < w && y + 1 < h) {
                    int g = Math.abs(v - (lum[i + 1] & 0xFF)) + Math.abs(v - (lum[i + w] & 0xFF));
                    if (g >= EDGE) edges[i]++;
                }
                if (prev != null) {
                    int d = Math.abs(v - (prev[i] & 0xFF));
                    if (d <= STILL) still[i]++;
                    else if (d >= BIG) big[i]++;
                    else small[i]++;
                }
            }
        }
        if (prev == null) prev = new byte[w * h];
        System.arraycopy(lum, 0, prev, 0, w * h);
        frames++;
    }

    /** The scoreboard found, or null when nothing on the picture looks like one (yet). */
    Box find() {
        int pairs = frames - 1;
        if (pairs < 8) return null;
        int n = w * h;
        boolean[] stillMask = new boolean[n], seed = new boolean[n], tick = new boolean[n];
        for (int y = 0; y < h; y++) {
            if (!band(y)) continue;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                stillMask[i] = still[i] >= pairs * 0.88f;
                // An edge held in place: there in most frames, and the pixel barely changing.
                seed[i] = stillMask[i] && edges[i] >= frames * 0.6f;
                // A clock's digit: flips now and then from one value to another, otherwise still.
                tick[i] = big[i] >= 3 && big[i] <= pairs * 0.5f && small[i] <= pairs * 0.3f;
            }
        }
        // Still edges joined up (a gap of two pixels bridged), then each cluster's box. Flipping pixels never join
        // anything (a crowd flickers too): they only say whether a box has a clock in it.
        boolean[] joined = dilate(seed, 2);
        // The ground a box grows over: still most of the time (a clock's digit changes once a second), or flipping.
        boolean[] ground = new boolean[n];
        for (int i = 0; i < n; i++) ground[i] = still[i] >= pairs * 0.75f || tick[i];
        int[] label = new int[n];
        int[] queue = new int[n];
        Box best = null;
        float bestScore = 0;
        int next = 0;
        for (int start = 0; start < n; start++) {
            if (!joined[start] || label[start] != 0) continue;
            next++;
            int head = 0, tail = 0, x0 = w, y0 = h, x1 = -1, y1 = -1, seeds = 0;
            queue[tail++] = start;
            label[start] = next;
            while (head < tail) {
                int i = queue[head++], x = i % w, y = i / w;
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
                if (seed[i]) seeds++;
                if (x > 0) tail = visit(joined, label, queue, tail, i - 1, next);
                if (x + 1 < w) tail = visit(joined, label, queue, tail, i + 1, next);
                if (y > 0) tail = visit(joined, label, queue, tail, i - w, next);
                if (y + 1 < h) tail = visit(joined, label, queue, tail, i + w, next);
            }
            // The dilation's own two pixels taken off again, then the box filled out to the still pixels about it
            // (and its clock's flipping digits, which belong to it).
            int[] b = grow(ground, Math.min(w - 1, x0 + 2), Math.min(h - 1, y0 + 2), Math.max(0, x1 - 2), Math.max(0, y1 - 2));
            float bw = (b[2] - b[0] + 1) / (float) w, bh = (b[3] - b[1] + 1) / (float) h;
            float aspect = (b[2] - b[0] + 1) / (float) (b[3] - b[1] + 1);
            if (bw < 0.06f || bw > 0.55f || bh < 0.025f || bh > 0.17f || aspect < 1.6f) continue;
            int area = (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
            float density = seeds / (float) area;
            if (density < 0.04f) continue;
            // A clock in it: flipping pixels inside the box, in a patch (not scattered like a crowd's).
            boolean ticking = clock(tick, b);
            float score = area * Math.min(1f, density * 4);
            if (ticking) score *= 4f;
            if (b[1] < h * 0.40f) score *= 1.5f;
            if ((b[0] + b[2]) / 2f < w / 2f) score *= 1.3f;
            if (score > bestScore) {
                bestScore = score;
                best = new Box(b[0] / (float) w, b[1] / (float) h, (b[2] + 1) / (float) w, (b[3] + 1) / (float) h, ticking);
            }
        }
        return best;
    }

    /**
     * The channel's own logo: in a corner (top right first, then top left, then bottom right), the cluster of edges that
     * stay in the same place frame after frame while the picture moves under them. A logo is often see-through, so its
     * pixels need not be still, only its outline. Not the scoreboard (not), and not as wide as a line across the
     * picture. Null when none is found.
     */
    Box logo(Box not) {
        int pairs = frames - 1;
        if (pairs < 8) return null;
        int n = w * h;
        boolean[] seed = new boolean[n];
        for (int y = 0; y < h; y++) {
            boolean top = y < h * 0.30f, foot = y >= h * 0.78f;
            if (!top && !foot) continue;
            for (int x = 0; x < w; x++) {
                boolean right = x >= w * 0.62f, left = x < w * 0.38f;
                if (!(top && (left || right)) && !(foot && right)) continue;
                if (not != null && x >= (not.left - 0.01f) * w && x <= (not.right + 0.01f) * w
                        && y >= (not.top - 0.01f) * h && y <= (not.bottom + 0.01f) * h) continue;
                int i = y * w + x;
                seed[i] = edges[i] >= frames * 0.7f;
            }
        }
        boolean[] joined = dilate(seed, 2);
        int[] label = new int[n], queue = new int[n];
        Box best = null;
        float bestScore = 0;
        int next = 0;
        for (int start = 0; start < n; start++) {
            if (!joined[start] || label[start] != 0) continue;
            next++;
            int head = 0, tail = 0, x0 = w, y0 = h, x1 = -1, y1 = -1, seeds = 0;
            queue[tail++] = start;
            label[start] = next;
            while (head < tail) {
                int i = queue[head++], x = i % w, y = i / w;
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                y0 = Math.min(y0, y);
                y1 = Math.max(y1, y);
                if (seed[i]) seeds++;
                if (x > 0) tail = visit(joined, label, queue, tail, i - 1, next);
                if (x + 1 < w) tail = visit(joined, label, queue, tail, i + 1, next);
                if (y > 0) tail = visit(joined, label, queue, tail, i - w, next);
                if (y + 1 < h) tail = visit(joined, label, queue, tail, i + w, next);
            }
            // The dilation's own two pixels taken off again.
            x0 = Math.min(w - 1, x0 + 2);
            y0 = Math.min(h - 1, y0 + 2);
            x1 = Math.max(x0, x1 - 2);
            y1 = Math.max(y0, y1 - 2);
            float bw = (x1 - x0 + 1) / (float) w, bh = (y1 - y0 + 1) / (float) h;
            float aspect = (x1 - x0 + 1) / (float) (y1 - y0 + 1);
            if (bw < 0.025f || bw > 0.24f || bh < 0.02f || bh > 0.15f || aspect < 0.5f || aspect > 7f) continue;
            int area = (x1 - x0 + 1) * (y1 - y0 + 1);
            if (seeds < area * 0.06f) continue;
            boolean right = (x0 + x1) / 2f >= w / 2f, top = (y0 + y1) / 2f < h / 2f;
            float score = seeds * (top && right ? 1.3f : top ? 1.0f : 0.9f);
            if (score > bestScore) {
                bestScore = score;
                best = new Box(x0 / (float) w, y0 / (float) h, (x1 + 1) / (float) w, (y1 + 1) / (float) h, false);
            }
        }
        return best;
    }

    /** Whether the flipping pixels inside box b sit together as a clock's digits do: four or more, in a short run. */
    private boolean clock(boolean[] tick, int[] b) {
        int count = 0, cx0 = w, cx1 = -1, cy0 = h, cy1 = -1;
        for (int y = b[1]; y <= b[3]; y++) {
            for (int x = b[0]; x <= b[2]; x++) {
                if (!tick[y * w + x]) continue;
                count++;
                cx0 = Math.min(cx0, x);
                cx1 = Math.max(cx1, x);
                cy0 = Math.min(cy0, y);
                cy1 = Math.max(cy1, y);
            }
        }
        if (count < 4) return false;
        // Packed into at most a third of the box's width: the seconds' digits, not the whole box flickering.
        return cx1 - cx0 + 1 <= (b[2] - b[0] + 1) / 3 + 2 && count >= (cx1 - cx0 + 1) * (cy1 - cy0 + 1) / 6;
    }

    private static int visit(boolean[] m, int[] label, int[] queue, int tail, int i, int id) {
        if (m[i] && label[i] == 0) {
            label[i] = id;
            queue[tail++] = i;
        }
        return tail;
    }

    private boolean[] dilate(boolean[] m, int r) {
        boolean[] o = new boolean[m.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!m[y * w + x]) continue;
                for (int dy = -r; dy <= r; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -r; dx <= r; dx++) {
                        int xx = x + dx;
                        if (xx >= 0 && xx < w) o[yy * w + xx] = true;
                    }
                }
            }
        }
        return o;
    }

    /**
     * The box {x0, y0, x1, y1} pushed out a side at a time while the line beyond it is mostly still (the scoreboard's
     * plain ground around its words), by at most a quarter of its size each way.
     */
    private int[] grow(boolean[] still, int x0, int y0, int x1, int y1) {
        if (x1 < x0 || y1 < y0) return new int[]{x0, y0, x0, y0};
        int maxX = Math.max(2, (x1 - x0 + 1) / 4), maxY = Math.max(2, (y1 - y0 + 1) / 4);
        int sx0 = x0, sx1 = x1, sy0 = y0, sy1 = y1;
        while (x0 > 0 && sx0 - x0 < maxX && column(still, x0 - 1, y0, y1) >= 0.85f) x0--;
        while (x1 + 1 < w && x1 - sx1 < maxX && column(still, x1 + 1, y0, y1) >= 0.85f) x1++;
        while (y0 > 0 && sy0 - y0 < maxY && row(still, y0 - 1, x0, x1) >= 0.8f) y0--;
        while (y1 + 1 < h && y1 - sy1 < maxY && row(still, y1 + 1, x0, x1) >= 0.8f) y1++;
        return new int[]{x0, y0, x1, y1};
    }

    private float column(boolean[] m, int x, int y0, int y1) {
        int on = 0;
        for (int y = y0; y <= y1; y++) if (m[y * w + x]) on++;
        return on / (float) (y1 - y0 + 1);
    }

    private float row(boolean[] m, int y, int x0, int x1) {
        int on = 0;
        for (int x = x0; x <= x1; x++) if (m[y * w + x]) on++;
        return on / (float) (x1 - x0 + 1);
    }

    // ---- the clock's minutes ---------------------------------------------------------------------------------------

    /**
     * The broadcaster's clock read for the moments it turns a minute, from grey crops of its scoreboard (cw by ch) a
     * quarter of a second apart. The seconds' last digit flips every second: the columns that change once a second are
     * that digit. Every ten seconds the change runs on to the left over the seconds' first digit too; when it runs on
     * further than those usually do, over the colon into the minutes (no gap wider than a digit), the clock has just
     * turned a minute: mm:59 to mm+1:00. Pure.
     */
    static final class ClockReader {
        private static final int FLIP = 48, HISTORY = 40;
        private final int cw, ch;
        private byte[] prev;
        /** Per column, how many of the recent frames it changed in. */
        private final int[] rate;
        private final ArrayDeque<boolean[]> recent = new ArrayDeque<>();
        private int lastMinuteFrame = -1000, frame;
        /** How far left of the seconds' digit the recent ten-second flips reached, in columns. */
        private final ArrayDeque<Integer> reaches = new ArrayDeque<>();

        ClockReader(int cw, int ch) {
            this.cw = cw;
            this.ch = ch;
            rate = new int[cw];
        }

        /** One crop; true when the clock turned a minute between the last crop and this one. */
        boolean add(byte[] crop) {
            if (crop == null || crop.length < cw * ch) return false;
            frame++;
            if (prev == null) {
                prev = crop.clone();
                return false;
            }
            boolean[] changed = new boolean[cw * ch];
            boolean[] col = new boolean[cw];
            for (int i = 0; i < cw * ch; i++) {
                if (Math.abs((crop[i] & 0xFF) - (prev[i] & 0xFF)) >= FLIP) {
                    changed[i] = true;
                    col[i % cw] = true;
                }
            }
            System.arraycopy(crop, 0, prev, 0, cw * ch);
            recent.addLast(col);
            for (int x = 0; x < cw; x++) if (col[x]) rate[x]++;
            if (recent.size() > HISTORY) {
                boolean[] old = recent.removeFirst();
                for (int x = 0; x < cw; x++) if (old[x]) rate[x]--;
            }
            if (recent.size() < 12) return false;
            int[] units = units();
            if (units == null) return false;
            // The seconds' digit flipped now?
            boolean flipped = false;
            for (int x = units[0]; x <= units[1]; x++) flipped |= col[x];
            if (!flipped) return false;
            int digit = units[1] - units[0] + 1;
            // The rows the digit's change is in: the clock's line.
            int r0 = ch, r1 = -1;
            for (int y = 0; y < ch; y++) {
                for (int x = units[0]; x <= units[1]; x++) {
                    if (changed[y * cw + x]) {
                        r0 = Math.min(r0, y);
                        r1 = Math.max(r1, y);
                    }
                }
            }
            if (r1 < 0) return false;
            // How far the change runs on to the left along that line, a gap of a digit at most.
            int reach = units[0], gap = 0;
            for (int x = units[0] - 1; x >= 0 && gap <= digit + 1; x--) {
                boolean any = false;
                for (int y = r0; y <= r1 && !any; y++) any = changed[y * cw + x];
                if (any) {
                    reach = x;
                    gap = 0;
                } else {
                    gap++;
                }
            }
            int span = units[0] - reach;
            if (span <= 0) return false;
            // A flip of the seconds' first digit as well (every ten seconds) reaches over that digit; at the minute it
            // reaches on over the colon into the minutes: well past how far the ten-second flips usually reach.
            boolean minute = false;
            if (reaches.size() >= 2) {
                minute = span >= median(reaches) + Math.max(2, Math.round(0.8f * digit)) && frame - lastMinuteFrame > 120;
            }
            if (minute) {
                lastMinuteFrame = frame;
            } else {
                reaches.addLast(span);
                if (reaches.size() > 12) reaches.removeFirst();
            }
            return minute;
        }

        private static int median(ArrayDeque<Integer> q) {
            int[] a = new int[q.size()];
            int i = 0;
            for (int v : q) a[i++] = v;
            java.util.Arrays.sort(a);
            return a[a.length / 2];
        }

        /**
         * The seconds' last digit: the run of columns changing in about a quarter of the frames (once a second at four
         * frames a second); {first, last} column, or null when no such run is seen.
         */
        int[] units() {
            int n = recent.size();
            int[] best = null;
            float bestOff = 1f;
            for (int x = 0; x < cw; x++) {
                float r = rate[x] / (float) n;
                if (r < 0.14f || r > 0.45f) continue;
                int e = x;
                while (e + 1 < cw && rate[e + 1] / (float) n >= 0.14f && rate[e + 1] / (float) n <= 0.45f) e++;
                float mean = 0;
                for (int k = x; k <= e; k++) mean += rate[k];
                mean /= (e - x + 1) * (float) n;
                float off = Math.abs(mean - 0.25f);
                // The rightmost of equally good runs: the seconds come last.
                if (e - x + 1 >= 2 && off <= bestOff) {
                    bestOff = off;
                    best = new int[]{x, e};
                }
                x = e;
            }
            return best;
        }
    }
}
