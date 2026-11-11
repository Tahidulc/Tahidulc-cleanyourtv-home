package com.gathin.tvhomephone.live;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * See-through, with nothing to blur: a channel's own logo learned stroke by stroke from pictures of its place, and
 * taken out of each fresh picture by its strokes alone. Pictures over time (one kept only when the picture has moved
 * on) give its middle picture, in which the logo stays sharp while all that moves round it melts away (each pixel's
 * median). What stands out there from what the picture is like round it is the logo: first against a wide softening,
 * then again against the middle picture with what was found filled in from beside it (so a thick letter's middle counts
 * too), with its soft shadow or glow close round it and a fine edge round all of that. Each fresh picture then has only
 * those pixels filled in, from the live picture just beside them, from the outside in: everything else under the cover
 * stays the channel's own picture, as sharp as it is. A logo that is a solid block (much of it far from its edge) cannot
 * be hidden that way without a smear: the cover then stands on its plate. Kept per channel, in the pictures' own pixels.
 */
final class LogoShape {
    private LogoShape() { }

    /** Pictures kept for learning (the latest), how many before a shape is trusted, how much one must differ to count. */
    static final int KEEP = 40, ENOUGH = 16, NEW_ENOUGH = 5;
    /**
     * How far apart in time pictures are kept (ms): while it is first learned, and after (so the pictures it is learned
     * again from span most of a minute, not a few seconds of one slow shot whose own lines would pass for the logo's).
     */
    static final long FIRST_GAP = 400L, AGAIN_GAP = 1_000L;
    /** How alike a shape learned again must be to the one it would replace (else it waits for a second to agree). */
    static final float ALIKE = 0.8f;
    /** The shape's marks: the logo itself (with its soft shadow or glow), and the fine edge round it. */
    static final byte CORE = 2, EDGE = 1;
    /** How solid the edge is drawn over the live picture (the logo's own pixels solid): it meets the picture softly. */
    static final int EDGE_ALPHA = 165;
    /** The room round the cover's place in the pictures taken, on a 1080-line picture: the softening's and the edge's. */
    static final int MARGIN = 24;
    /** A logo with more than BLOCK_SHARE of its pixels deeper in it than DEEP (on a 1080-line picture) is a block. */
    static final float BLOCK_SHARE = 0.3f;
    static final int DEEP = 8;

    /** Pictures of one place (w by h, ARGB) kept for learning the logo in it. */
    static final class Learner {
        final int w, h;
        private final ArrayDeque<int[]> kept = new ArrayDeque<>();
        private int[] last;
        private long lastAt = Long.MIN_VALUE / 2;
        private int fresh;

        Learner(int w, int h) {
            this.w = w;
            this.h = h;
        }

        /**
         * A picture of the place taken at (ms): kept when it is at least gap after the last one kept and differs enough
         * from it (the picture moved on).
         */
        boolean offer(int[] px, long at, long gap) {
            if (px == null || px.length != w * h || at - lastAt < gap) return false;
            if (last != null && meanDiff(px, last) < NEW_ENOUGH) return false;
            lastAt = at;
            last = px.clone();
            kept.addLast(last);
            while (kept.size() > KEEP) kept.removeFirst();
            fresh++;
            return true;
        }

        int count() {
            return kept.size();
        }

        /** Pictures kept since the shape was last worked out. */
        int fresh() {
            return fresh;
        }

        /** The pictures kept, for working the shape out (fresh counted from now). */
        List<int[]> take() {
            fresh = 0;
            return new ArrayList<>(kept);
        }
    }

    /** A fill for a fresh picture: its pixels (ARGB, w by h; clear but for the logo's) at x, y in the pictures' space. */
    static final class Fill {
        final int[] argb;
        final int x, y, w, h;

        Fill(int[] argb, int x, int y, int w, int h) {
            this.argb = argb;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** Nothing to fill in (no logo found there): the cover stands clear over the picture all the same. */
        boolean empty() {
            return w <= 0 || h <= 0;
        }
    }

    /** Where shapes are learned: a quiet thread of their own, so the fills go on meanwhile. */
    private static final Executor LEARN = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LogoShape");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** A shape as learned: its marks, the box round them (null: none), and 1 to see through or 2 a block. */
    private static final class Known {
        final byte[] shape;
        final int[] bounds;
        final int state;

        Known(byte[] shape, int[] bounds, int state) {
            this.shape = shape;
            this.bounds = bounds;
            this.state = state;
        }
    }

    /**
     * The logo on now: where its pictures are taken (x, y, w, h, in a space sw by sh: the screen's or the stream's own
     * pixels), the cover's place in them (bl, bt, br, bb, from x and y), the pictures kept for learning it, and its shape
     * once learned (or kept from before, from its file). Made on the main thread; take() on one thread of its own, the
     * learning on another (learnOn), the fills going on with the shape it has meanwhile.
     */
    static final class Here {
        final String key;
        final int sw, sh, x, y, w, h, bl, bt, br, bb;
        private final Learner learner;
        private final File file;
        private final Executor learnOn;
        private volatile Known known;
        private volatile boolean learning;
        /** A shape learned again that did not agree with the one it has, waiting for the next to say the same. */
        private byte[] pending;
        private boolean loaded;

        Here(File dir, String key, int sw, int sh, int x, int y, int w, int h, int bl, int bt, int br, int bb) {
            this(dir, key, sw, sh, x, y, w, h, bl, bt, br, bb, LEARN);
        }

        Here(File dir, String key, int sw, int sh, int x, int y, int w, int h, int bl, int bt, int br, int bb, Executor learnOn) {
            this.learnOn = learnOn;
            this.key = key == null ? "" : key;
            this.sw = sw;
            this.sh = sh;
            this.x = x;
            this.y = y;
            this.w = Math.max(1, w);
            this.h = Math.max(1, h);
            this.bl = bl;
            this.bt = bt;
            this.br = br;
            this.bb = bb;
            learner = new Learner(this.w, this.h);
            file = dir == null || this.key.isEmpty() ? null : new File(dir, "s" + Integer.toHexString(this.key.hashCode()) + ".bin");
        }

        /** Whether it is for this channel, space, place and cover. */
        boolean same(String key, int sw, int sh, int x, int y, int w, int h, int bl, int bt, int br, int bb) {
            return this.key.equals(key == null ? "" : key) && this.sw == sw && this.sh == sh && this.x == x && this.y == y
                    && this.w == w && this.h == h && this.bl == bl && this.bt == bt && this.br == br && this.bb == bb;
        }

        /** Whether the logo's shape is known and can be seen through: the cover then stands clear over the picture. */
        boolean known() {
            Known k = known;
            return k != null && k.state == 1;
        }

        /** Whether the logo is a solid block: the cover stands on its plate. */
        boolean block() {
            Known k = known;
            return k != null && k.state == 2;
        }

        /**
         * A fresh picture of the place (w by h) taken at (ms): learned from, and the fill for it; null while learning, or
         * for a block. Learned again now and then (more often until it is known), on its own thread: a shape learned
         * again replaces the one it has only when the two are alike, or when the one learned before it said the same (the
         * logo changed).
         */
        Fill take(int[] px, long at) {
            if (px == null || px.length != w * h) return null;
            if (!loaded) {
                loaded = true;
                load();
            }
            Known k = known;
            if (learner.offer(px, at, k == null ? FIRST_GAP : AGAIN_GAP) && !learning
                    && (k == null ? learner.count() >= ENOUGH : learner.fresh() >= (k.state == 1 ? KEEP : ENOUGH))) {
                List<int[]> frames = learner.take();
                learning = true;
                learnOn.execute(() -> {
                    try {
                        byte[] s = learn(frames, w, h, bl, bt, br, bb, sh / 1080f);
                        if (s != null && accept(s)) {
                            known = make(s);
                            save();
                        }
                    } finally {
                        learning = false;
                    }
                });
            }
            k = known;
            return k == null || k.state != 1 ? null : fill(px, k);
        }

        /** Whether a shape learned again takes the place of the one it has. */
        private boolean accept(byte[] s) {
            Known k = known;
            boolean yes = k == null || overlap(s, k.shape) >= ALIKE || (pending != null && overlap(s, pending) >= ALIKE);
            pending = yes ? null : s;
            return yes;
        }

        private Known make(byte[] s) {
            int[] r = bounds(s, w, h);
            return new Known(s, r, r != null && deepShare(s, w, h, Math.max(3, Math.round(DEEP * sh / 1080f))) > BLOCK_SHARE ? 2 : 1);
        }

        private Fill fill(int[] px, Known k) {
            int[] r = k.bounds;
            byte[] shape = k.shape;
            if (r == null) return new Fill(new int[0], x, y, 0, 0);
            // With a pixel of the picture round it: all that filling it in needs.
            int l = Math.max(0, r[0] - 1), t = Math.max(0, r[1] - 1), rr = Math.min(w, r[2] + 1), b = Math.min(h, r[3] + 1);
            int cw = rr - l, ch = b - t;
            int[] sub = new int[cw * ch];
            byte[] ss = new byte[cw * ch];
            for (int yy = 0; yy < ch; yy++) {
                System.arraycopy(px, (t + yy) * w + l, sub, yy * cw, cw);
                System.arraycopy(shape, (t + yy) * w + l, ss, yy * cw, cw);
            }
            int[] out = patch(sub, cw, ch, ss);
            return out == null ? null : new Fill(out, x + l, y + t, cw, ch);
        }

        /** The shape kept for this channel, if it was learned in this space and lies within this place. */
        private void load() {
            if (file == null || !file.isFile()) return;
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                if (in.readInt() != MAGIC || in.readInt() != sw || in.readInt() != sh) return;
                int sx = in.readInt(), sy = in.readInt(), cw = in.readInt(), ch = in.readInt(), n = in.readInt();
                if (n < 0 || n > 4 * 1024 * 1024) return;
                byte[] packed = new byte[n];
                in.readFully(packed);
                byte[] s = new byte[w * h];
                if (cw > 0 && ch > 0) {
                    byte[] part = unpack(packed, cw * ch);
                    int ox = sx - x, oy = sy - y;
                    if (part == null || ox < 0 || oy < 0 || ox + cw > w || oy + ch > h) return;
                    for (int yy = 0; yy < ch; yy++) System.arraycopy(part, yy * cw, s, (oy + yy) * w + ox, cw);
                }
                known = make(s);
            } catch (IOException | RuntimeException e) {
                // learned again, then
            }
        }

        /** The shape kept for next time: its own box of the space, packed small. */
        private void save() {
            Known k = known;
            if (file == null || k == null) return;
            int[] r = k.bounds;
            byte[] shape = k.shape;
            int cl = r == null ? 0 : r[0], ct = r == null ? 0 : r[1], cw = r == null ? 0 : r[2] - r[0], ch = r == null ? 0 : r[3] - r[1];
            byte[] part = new byte[Math.max(0, cw * ch)];
            for (int yy = 0; yy < ch; yy++) System.arraycopy(shape, (ct + yy) * w + cl, part, yy * cw, cw);
            byte[] packed = pack(part);
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(file.getPath() + ".new");
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
                out.writeInt(MAGIC);
                out.writeInt(sw);
                out.writeInt(sh);
                out.writeInt(x + cl);
                out.writeInt(y + ct);
                out.writeInt(cw);
                out.writeInt(ch);
                out.writeInt(packed.length);
                out.write(packed);
            } catch (IOException e) {
                return;
            }
            if (!tmp.renameTo(file) && !tmp.delete()) tmp.deleteOnExit();
        }
    }

    private static final int MAGIC = 0x4C534831;

    /** Pure: how different two pictures are, the mean difference of their channels (every fourth pixel). */
    static int meanDiff(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length) return 255;
        long s = 0;
        int n = 0;
        for (int i = 0; i < a.length; i += 4) {
            int p = a[i], q = b[i];
            s += Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF))
                    + Math.abs((p & 0xFF) - (q & 0xFF));
            n += 3;
        }
        return n == 0 ? 0 : (int) (s / n);
    }

    /**
     * Pure: the logo's shape in pictures of its place (w by h; the box l, t, r, b where it can be; k the picture's lines
     * over 1080): CORE on the logo and its soft shadow, EDGE on the fine edge round it, 0 elsewhere, row by row. Null with
     * fewer than three pictures.
     */
    static byte[] learn(List<int[]> frames, int w, int h, int bl, int bt, int br, int bb, float k) {
        int nf = frames == null ? 0 : frames.size();
        if (nf < 3 || w <= 2 || h <= 2) return null;
        int n = w * h;
        for (int[] f : frames) if (f == null || f.length != n) return null;
        bl = clamp(bl, 0, w);
        br = clamp(br, bl, w);
        bt = clamp(bt, 0, h);
        bb = clamp(bb, bt, h);
        if (br - bl < 2 || bb - bt < 2) return null;
        // Each pixel's middle value over the pictures, by channel: the logo sharp, all that moves melted away.
        float[][] m = new float[3][n];
        int[] vals = new int[nf];
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < 3; ch++) {
                int s = 16 - 8 * ch;
                for (int f = 0; f < nf; f++) vals[f] = (frames.get(f)[i] >> s) & 0xFF;
                m[ch][i] = median(vals, nf);
            }
        }
        boolean[] box = new boolean[n];
        for (int y = bt; y < bb; y++) for (int x = bl; x < br; x++) box[y * w + x] = true;
        // Its surest parts: what stands out from a wide softening.
        int wide = Math.max(4, Math.round(12 * k));
        float[][] soft = new float[3][];
        for (int ch = 0; ch < 3; ch++) soft[ch] = blur(blur(m[ch], w, h, wide), w, h, wide);
        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) mask[i] = box[i] && contrast(m, soft, i) > 18;
        // Again against the middle picture with those filled in from round them: a thick letter's middle counts then.
        float[] c = new float[n];
        for (int it = 0; it < 3; it++) {
            float[][] bg = inpaint(m, w, h, dilate(mask, w, h, 1));
            for (int ch = 0; ch < 3; ch++) bg[ch] = blur(bg[ch], w, h, 1);
            for (int i = 0; i < n; i++) {
                c[i] = contrast(m, bg, i);
                mask[i] = box[i] && c[i] > 12;
            }
        }
        dropSpecks(mask, w, h, 6);
        // Its soft parts too (a drop shadow, a glow): fainter, close round it.
        boolean[] near = dilate(mask, w, h, Math.max(2, Math.round(5 * k)));
        for (int i = 0; i < n; i++) if (!mask[i] && near[i] && box[i] && c[i] > 4) mask[i] = true;
        boolean[] rim = dilate(mask, w, h, 2);
        byte[] shape = new byte[n];
        for (int i = 0; i < n; i++) shape[i] = mask[i] ? CORE : rim[i] ? EDGE : 0;
        return shape;
    }

    /** Pure: the box round a shape's marks {l, t, r, b} (r and b just past it); null when it has none. */
    static int[] bounds(byte[] shape, int w, int h) {
        int l = w, t = h, r = -1, b = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (shape[y * w + x] == 0) continue;
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
            }
        }
        return r < 0 ? null : new int[]{l, t, r + 1, b + 1};
    }

    /**
     * Pure: the share of the logo's own pixels further than deep from the picture round it (a chamfer distance, near
     * enough the real one): thin strokes have none; a solid block has most of its pixels deep in it.
     */
    static float deepShare(byte[] shape, int w, int h, int deep) {
        int n = w * h, big = 3 * (w + h) + 3, core = 0, far = 0;
        int[] d = new int[n];
        for (int i = 0; i < n; i++) d[i] = shape[i] == 0 ? 0 : big;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x, v = d[i];
                if (v == 0) continue;
                if (x > 0) v = Math.min(v, d[i - 1] + 3);
                if (y > 0) {
                    v = Math.min(v, d[i - w] + 3);
                    if (x > 0) v = Math.min(v, d[i - w - 1] + 4);
                    if (x < w - 1) v = Math.min(v, d[i - w + 1] + 4);
                }
                d[i] = v;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x, v = d[i];
                if (v == 0) continue;
                if (x < w - 1) v = Math.min(v, d[i + 1] + 3);
                if (y < h - 1) {
                    v = Math.min(v, d[i + w] + 3);
                    if (x < w - 1) v = Math.min(v, d[i + w + 1] + 4);
                    if (x > 0) v = Math.min(v, d[i + w - 1] + 4);
                }
                d[i] = v;
            }
        }
        for (int i = 0; i < n; i++) {
            if (shape[i] != CORE) continue;
            core++;
            if (d[i] > 3 * deep) far++;
        }
        return core == 0 ? 0f : far / (float) core;
    }

    /** Pure: how alike two shapes are (the logo's pixels in both over those in either); 1 when neither has any. */
    static float overlap(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        int both = 0, either = 0;
        for (int i = 0; i < a.length; i++) {
            boolean p = a[i] == CORE, q = b[i] == CORE;
            if (p && q) both++;
            if (p || q) either++;
        }
        return either == 0 ? 1f : both / (float) either;
    }

    /**
     * Pure: the fill for a fresh picture (ARGB, w by h): the shape's pixels made up from the picture just beside them
     * (from the outside in), the logo's own solid and its edge a little see-through, the rest clear.
     */
    static int[] patch(int[] px, int w, int h, byte[] shape) {
        int n = w * h;
        if (px == null || shape == null || px.length != n || shape.length != n) return null;
        float[][] img = new float[3][n];
        boolean[] gone = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = px[i];
            img[0][i] = (p >> 16) & 0xFF;
            img[1][i] = (p >> 8) & 0xFF;
            img[2][i] = p & 0xFF;
            gone[i] = shape[i] != 0;
        }
        float[][] f = inpaint(img, w, h, gone);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            if (shape[i] == 0) continue;
            int a = shape[i] == CORE ? 255 : EDGE_ALPHA;
            out[i] = (a << 24) | (clamp(Math.round(f[0][i]), 0, 255) << 16) | (clamp(Math.round(f[1][i]), 0, 255) << 8)
                    | clamp(Math.round(f[2][i]), 0, 255);
        }
        return out;
    }

    /** Pure: a shape packed small (runs of a mark: the mark, then how many, up to 255). */
    static byte[] pack(byte[] shape) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int i = 0;
        while (i < shape.length) {
            byte v = shape[i];
            int run = 1;
            while (i + run < shape.length && shape[i + run] == v && run < 255) run++;
            o.write(v);
            o.write(run);
            i += run;
        }
        return o.toByteArray();
    }

    /** Pure: a packed shape of n pixels back; null when it does not come to n. */
    static byte[] unpack(byte[] packed, int n) {
        if (packed == null || n < 0) return null;
        byte[] out = new byte[n];
        int at = 0;
        for (int i = 0; i + 1 < packed.length; i += 2) {
            int run = packed[i + 1] & 0xFF;
            if (at + run > n) return null;
            Arrays.fill(out, at, at + run, packed[i]);
            at += run;
        }
        return at == n ? out : null;
    }

    // ---- the workings ----------------------------------------------------------------------------------------------

    /** The largest difference of a pixel's channels between two pictures. */
    private static float contrast(float[][] a, float[][] b, int i) {
        return Math.max(Math.abs(a[0][i] - b[0][i]), Math.max(Math.abs(a[1][i] - b[1][i]), Math.abs(a[2][i] - b[2][i])));
    }

    /** The middle of the first k values (sorted in place). */
    private static float median(int[] v, int k) {
        for (int i = 1; i < k; i++) {
            int x = v[i], j = i - 1;
            while (j >= 0 && v[j] > x) {
                v[j + 1] = v[j];
                j--;
            }
            v[j + 1] = x;
        }
        return (k & 1) == 1 ? v[k / 2] : (v[k / 2 - 1] + v[k / 2]) / 2f;
    }

    /** One channel softened by a box r each way (running sums), the edges as far as they go. */
    static float[] blur(float[] v, int w, int h, int r) {
        float[] a = new float[v.length], b = new float[v.length];
        for (int y = 0; y < h; y++) {
            int row = y * w, cnt = 0;
            float s = 0;
            for (int x = 0; x <= Math.min(w - 1, r); x++, cnt++) s += v[row + x];
            for (int x = 0; x < w; x++) {
                a[row + x] = s / cnt;
                int add = x + r + 1, drop = x - r;
                if (add < w) {
                    s += v[row + add];
                    cnt++;
                }
                if (drop >= 0) {
                    s -= v[row + drop];
                    cnt--;
                }
            }
        }
        for (int x = 0; x < w; x++) {
            int cnt = 0;
            float s = 0;
            for (int y = 0; y <= Math.min(h - 1, r); y++, cnt++) s += a[y * w + x];
            for (int y = 0; y < h; y++) {
                b[y * w + x] = s / cnt;
                int add = y + r + 1, drop = y - r;
                if (add < h) {
                    s += a[add * w + x];
                    cnt++;
                }
                if (drop >= 0) {
                    s -= a[drop * w + x];
                    cnt--;
                }
            }
        }
        return b;
    }

    /** A mask grown by r pixels (as far as r steps up, down and across take it: a diamond). */
    static boolean[] dilate(boolean[] m, int w, int h, int r) {
        int n = w * h, big = w + h + 1;
        int[] d = new int[n];
        for (int i = 0; i < n; i++) d[i] = m[i] ? 0 : big;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (x > 0 && d[i - 1] + 1 < d[i]) d[i] = d[i - 1] + 1;
                if (y > 0 && d[i - w] + 1 < d[i]) d[i] = d[i - w] + 1;
            }
        }
        boolean[] out = new boolean[n];
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                if (x < w - 1 && d[i + 1] + 1 < d[i]) d[i] = d[i + 1] + 1;
                if (y < h - 1 && d[i + w] + 1 < d[i]) d[i] = d[i + w] + 1;
                out[i] = d[i] <= r;
            }
        }
        return out;
    }

    /** The masked pixels filled in from their unmasked neighbours (all eight), from the outside in, a layer at a time. */
    static float[][] inpaint(float[][] img, int w, int h, boolean[] mask) {
        int n = w * h, chs = img.length;
        float[][] out = new float[chs][];
        for (int ch = 0; ch < chs; ch++) out[ch] = img[ch].clone();
        boolean[] known = new boolean[n];
        int todo = 0;
        for (int i = 0; i < n; i++) {
            known[i] = !mask[i];
            if (mask[i]) todo++;
        }
        if (todo == 0 || todo == n) return out;
        int[] front = new int[todo];
        float[][] vals = new float[chs][todo];
        float[] sums = new float[chs];
        while (todo > 0) {
            int nf = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (known[i]) continue;
                    Arrays.fill(sums, 0f);
                    int cnt = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = y + dy;
                        if (yy < 0 || yy >= h) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx;
                            if ((dx == 0 && dy == 0) || xx < 0 || xx >= w) continue;
                            int j = yy * w + xx;
                            if (!known[j]) continue;
                            for (int ch = 0; ch < chs; ch++) sums[ch] += out[ch][j];
                            cnt++;
                        }
                    }
                    if (cnt == 0) continue;
                    front[nf] = i;
                    for (int ch = 0; ch < chs; ch++) vals[ch][nf] = sums[ch] / cnt;
                    nf++;
                }
            }
            if (nf == 0) break;
            for (int f = 0; f < nf; f++) {
                int i = front[f];
                known[i] = true;
                for (int ch = 0; ch < chs; ch++) out[ch][i] = vals[ch][f];
            }
            todo -= nf;
        }
        return out;
    }

    /** Groups of fewer than min pixels (touching, eight ways) let go. */
    static void dropSpecks(boolean[] m, int w, int h, int min) {
        int n = m.length;
        boolean[] seen = new boolean[n];
        int[] stack = new int[n], group = new int[n];
        for (int s = 0; s < n; s++) {
            if (!m[s] || seen[s]) continue;
            int sp = 0, gn = 0;
            stack[sp++] = s;
            seen[s] = true;
            while (sp > 0) {
                int i = stack[--sp];
                group[gn++] = i;
                int x = i % w, y = i / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int j = yy * w + xx;
                        if (m[j] && !seen[j]) {
                            seen[j] = true;
                            stack[sp++] = j;
                        }
                    }
                }
            }
            if (gn < min) for (int g = 0; g < gn; g++) m[group[g]] = false;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
