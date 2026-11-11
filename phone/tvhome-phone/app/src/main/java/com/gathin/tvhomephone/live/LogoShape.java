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
 * See-through, with nothing to blur: a channel's own logo learned pixel by pixel from pictures of its place, and taken
 * back out of each fresh picture. A channel's logo is laid over its picture: each of its pixels lets some of the picture
 * through (t: none for a solid letter, most for a see-through box) and adds some light of its own (a). Pictures of the
 * place over time, each unlike all the others kept (so a long-held shot counts once), show both: where the logo is, what
 * is there goes with the picture round it less, and stands off from it by the same amount every time; from how it goes
 * with the picture round it, picture after picture, t and a are worked out for every pixel. Each fresh picture then has
 * the logo taken back out: where it lets enough through, the picture under it is worked back exactly (as sharp as the
 * rest); where it is too solid for that (a bold letter), that pixel is filled in from the picture just beside it. The
 * cover is laid over the picture with only as much of that as it needs (no more covering than the logo itself did), so
 * the live picture shows through it wherever it can. A logo whose solid part is a block (a filled box) cannot be filled
 * in without a smear: the cover then stands on its plate, as it does while a channel's logo is learned. Kept per channel,
 * in the pictures' own pixels; nothing found means still learning, never "nothing to hide".
 */
final class LogoShape {
    private LogoShape() { }

    /** Pictures kept for learning (the latest), how many before the logo is learned, how much one must differ to count. */
    static final int KEEP = 40, ENOUGH = 16, NEW_ENOUGH = 5;
    /** How far apart in time pictures are kept (ms): while it is first learned, and after (learned again over minutes). */
    static final long FIRST_GAP = 300L, AGAIN_GAP = 1_000L;
    /** How alike a logo learned again must be to the one it would replace (else it waits for a second to agree). */
    static final float ALIKE = 0.8f;
    /** The room round the cover's place in the pictures taken, on a 1080-line picture. */
    static final int MARGIN = 24;
    /** How far round the cover's place the logo is looked for too, on a 1080-line picture (it may stick out a little). */
    static final int REACH = 10;
    /** Below LO the logo lets too little through to work the picture back (filled in instead); above HI, worked back. */
    static final float LO = 0.25f, HI = 0.45f;
    /** A logo whose solid part has more than BLOCK_SHARE of its pixels deeper than DEEP (1080 lines) in it is a block. */
    static final float BLOCK_SHARE = 0.3f;
    static final int DEEP = 8;
    /** Fewer of the logo's pixels than this (on a 1080-line picture): nothing found yet. */
    static final int FEW = 40;

    /** Where logos are learned: a quiet thread of their own, so the fills go on meanwhile. */
    private static final Executor LEARN = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LogoShape");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Pictures of one place (w by h, ARGB) kept for learning the logo in it, each unlike all the others kept. */
    static final class Learner {
        final int w, h;
        private final ArrayDeque<int[]> kept = new ArrayDeque<>();
        private long lastAt = Long.MIN_VALUE / 2;
        private int fresh;

        Learner(int w, int h) {
            this.w = w;
            this.h = h;
        }

        /**
         * A picture of the place taken at (ms): kept when it is at least gap after the last one kept and differs enough
         * from every one kept (a shot held for long, as a tennis court's wide shot, counts once, not most of the time).
         */
        boolean offer(int[] px, long at, long gap) {
            if (px == null || px.length != w * h || at - lastAt < gap) return false;
            for (int[] k : kept) if (meanDiff(px, k) < NEW_ENOUGH) return false;
            lastAt = at;
            kept.addLast(px.clone());
            while (kept.size() > KEEP) kept.removeFirst();
            fresh++;
            return true;
        }

        int count() {
            return kept.size();
        }

        /** Pictures kept since the logo was last worked out. */
        int fresh() {
            return fresh;
        }

        /** The pictures kept, for working the logo out (fresh counted from now). */
        List<int[]> take() {
            fresh = 0;
            return new ArrayList<>(kept);
        }
    }

    /**
     * A logo as learned, over a place w by h: which pixels are its own, what each lets through of the picture (t, 0 to 1)
     * and adds of its own (add, by channel: red, green, blue), the box round it, how many pixels it has, and what it is:
     * SEEN (taken out, the cover clear over the picture), BLOCK (the cover on its plate) or NONE (nothing found).
     */
    static final class Model {
        static final int NONE = 0, SEEN = 1, BLOCK = 2;
        final int w, h;
        final boolean[] logo;
        final float[] t;
        final float[][] add;
        final int[] bounds;
        final int count, state;

        Model(int w, int h, boolean[] logo, float[] t, float[][] add, int state) {
            this.w = w;
            this.h = h;
            this.logo = logo;
            this.t = t;
            this.add = add;
            int c = 0;
            for (boolean b : logo) if (b) c++;
            count = c;
            bounds = bounds(logo, w, h);
            this.state = bounds == null ? NONE : state;
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

        /** Nothing to lay over the picture (it is already as it would be without the logo). */
        boolean empty() {
            return w <= 0 || h <= 0;
        }
    }

    /**
     * The logo on now: where its pictures are taken (x, y, w, h, in a space sw by sh: the screen's or the stream's own
     * pixels), the cover's place in them (bl, bt, br, bb, from x and y), the pictures kept for learning it, and the logo
     * once learned (or kept from before, from its file). Made on the main thread; take() on one thread of its own, the
     * learning on another (learnOn), the fills going on with the logo it has meanwhile.
     */
    static final class Here {
        final String key;
        final int sw, sh, x, y, w, h, bl, bt, br, bb;
        private final Learner learner;
        private final File file;
        private final Executor learnOn;
        private volatile Model model;
        private volatile boolean learning;
        /** A logo learned again that did not agree with the one it has, waiting for the next to say the same. */
        private Model pending;
        private boolean loaded;
        /** How many pictures the logo it has was learned from. */
        private volatile int learntFrom;

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
            file = dir == null || this.key.isEmpty() ? null : new File(dir, "m" + Integer.toHexString(this.key.hashCode()) + ".bin");
        }

        /** Whether it is for this channel, space, place and cover. */
        boolean same(String key, int sw, int sh, int x, int y, int w, int h, int bl, int bt, int br, int bb) {
            return this.key.equals(key == null ? "" : key) && this.sw == sw && this.sh == sh && this.x == x && this.y == y
                    && this.w == w && this.h == h && this.bl == bl && this.bt == bt && this.br == br && this.bb == bb;
        }

        /** Whether the logo is learned and can be taken out: the cover then stands clear over the picture. */
        boolean known() {
            Model m = model;
            return m != null && m.state == Model.SEEN;
        }

        /** Whether the logo's solid part is a block: the cover stands on its plate. */
        boolean block() {
            Model m = model;
            return m != null && m.state == Model.BLOCK;
        }

        /** Pictures kept towards learning it (of ENOUGH, the first time). */
        int learnt() {
            return learner.count();
        }

        /**
         * A fresh picture of the place (w by h) taken at (ms): learned from, and the fill for it; null while learning,
         * when nothing was found, or for a block. Learned again now and then (more often until it is known), on its own
         * thread: a logo learned again replaces the one it has only when the two are alike, or when the one learned
         * before it said the same (the logo changed).
         */
        Fill take(int[] px, long at) {
            if (px == null || px.length != w * h) return null;
            if (!loaded) {
                loaded = true;
                load();
            }
            Model m = model;
            if (learner.offer(px, at, m == null ? FIRST_GAP : AGAIN_GAP) && !learning && due(m)) {
                List<int[]> frames = learner.take();
                learning = true;
                learnOn.execute(() -> {
                    try {
                        Model got = learn(frames, w, h, bl, bt, br, bb, sh / 1080f);
                        if (got != null && accept(got)) {
                            model = got;
                            learntFrom = frames.size();
                            if (got.state != Model.NONE) save();
                        }
                    } finally {
                        learning = false;
                    }
                });
            }
            m = model;
            return m == null || m.state != Model.SEEN ? null : fill(px, m);
        }

        /**
         * Whether it is time to learn it (again): first with ENOUGH pictures; then, while more are still being gathered,
         * each time eight more have come (it grows surer quickly); after that each KEEP fresh pictures (ENOUGH while
         * nothing has been found, or for a block, to look again sooner).
         */
        private boolean due(Model m) {
            int c = learner.count(), f = learner.fresh();
            if (m == null) return c >= ENOUGH;
            if (m.state != Model.SEEN) return f >= ENOUGH;
            return learntFrom < KEEP ? f >= 8 : f >= KEEP;
        }

        /** Whether a logo learned again takes the place of the one it has. */
        private boolean accept(Model got) {
            Model m = model;
            boolean yes = m == null || m.state == Model.NONE || overlap(got.logo, m.logo) >= ALIKE
                    || (pending != null && overlap(got.logo, pending.logo) >= ALIKE);
            pending = yes ? null : got;
            return yes;
        }

        private Fill fill(int[] px, Model m) {
            int[] r = m.bounds;
            // With a pixel of the picture round it: all that filling it in needs.
            int l = Math.max(0, r[0] - 1), t = Math.max(0, r[1] - 1), rr = Math.min(w, r[2] + 1), b = Math.min(h, r[3] + 1);
            int[] out = patch(px, w, h, m, l, t, rr, b);
            return out == null ? null : new Fill(out, x + l, y + t, rr - l, b - t);
        }

        /** The logo kept for this channel, if it was learned in this space and lies within this place. */
        private void load() {
            if (file == null) return;
            // The shape kept by 4.67 (a stroke mask, which could be kept empty): let go.
            File old = new File(file.getParentFile(), "s" + Integer.toHexString(key.hashCode()) + ".bin");
            if (old.isFile() && !old.delete()) old.deleteOnExit();
            if (!file.isFile()) return;
            try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(file)))) {
                if (in.readInt() != MAGIC || in.readInt() != sw || in.readInt() != sh) return;
                int sx = in.readInt(), sy = in.readInt(), cw = in.readInt(), ch = in.readInt(), state = in.readInt();
                int ox = sx - x, oy = sy - y;
                if (cw <= 0 || ch <= 0 || cw * ch > 4_000_000 || ox < 0 || oy < 0 || ox + cw > w || oy + ch > h) return;
                int len = in.readInt();
                if (len < 0 || len > 8_000_000) return;
                byte[] flags = new byte[len];
                in.readFully(flags);
                byte[] part = unpack(flags, cw * ch);
                if (part == null) return;
                int n = w * h;
                boolean[] logo = new boolean[n];
                float[] t = ones(n);
                float[][] add = new float[3][n];
                for (int yy = 0; yy < ch; yy++) {
                    for (int xx = 0; xx < cw; xx++) {
                        if (part[yy * cw + xx] == 0) continue;
                        int i = (oy + yy) * w + ox + xx;
                        logo[i] = true;
                        t[i] = (in.readByte() & 0xFF) / 255f;
                        for (int c = 0; c < 3; c++) add[c][i] = in.readShort() / 8f;
                    }
                }
                Model m = new Model(w, h, logo, t, add, state);
                if (m.state != Model.NONE) {
                    model = m;
                    learntFrom = KEEP;
                }
            } catch (IOException | RuntimeException e) {
                // learned again, then
            }
        }

        /** The logo kept for next time: its own box of the space, what each of its pixels lets through and adds. */
        private void save() {
            Model m = model;
            if (file == null || m == null || m.bounds == null) return;
            int[] r = m.bounds;
            int cw = r[2] - r[0], ch = r[3] - r[1];
            byte[] part = new byte[cw * ch];
            for (int yy = 0; yy < ch; yy++) {
                for (int xx = 0; xx < cw; xx++) part[yy * cw + xx] = (byte) (m.logo[(r[1] + yy) * w + r[0] + xx] ? 1 : 0);
            }
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return;
            File tmp = new File(file.getPath() + ".new");
            try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(tmp)))) {
                out.writeInt(MAGIC);
                out.writeInt(sw);
                out.writeInt(sh);
                out.writeInt(x + r[0]);
                out.writeInt(y + r[1]);
                out.writeInt(cw);
                out.writeInt(ch);
                out.writeInt(m.state);
                byte[] packed = pack(part);
                out.writeInt(packed.length);
                out.write(packed);
                for (int yy = 0; yy < ch; yy++) {
                    for (int xx = 0; xx < cw; xx++) {
                        int i = (r[1] + yy) * w + r[0] + xx;
                        if (!m.logo[i]) continue;
                        out.writeByte(Math.round(Math.max(0f, Math.min(1f, m.t[i])) * 255));
                        for (int c = 0; c < 3; c++) out.writeShort(Math.round(Math.max(-4000f, Math.min(4000f, m.add[c][i] * 8))));
                    }
                }
            } catch (IOException e) {
                return;
            }
            if (!tmp.renameTo(file) && !tmp.delete()) tmp.deleteOnExit();
        }
    }

    private static final int MAGIC = 0x4C534832;

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
     * Pure: the logo in pictures of its place (w by h; the cover's box l, t, r, b; k the picture's lines over 1080),
     * looked for in the box and a little round it. Where it may be: what stands out in the pictures' middle picture from
     * that picture filled in over the box from round it (a see-through box stands out as much as its letters), and what
     * stands out from a wide softening of it (fine strokes near the edge). Then, for those pixels and a little round
     * them, what each lets through and adds: from each picture, the picture there as round it (the mean of the pixels
     * about it that are not the logo's) against what is there, picture after picture (a line through them: how steep,
     * what it lets through; where it starts, what it adds). The logo's own pixels are those it changes, by light or by
     * what it lets through; worked out once more with the picture round it known better. Null with fewer than three
     * pictures.
     */
    static Model learn(List<int[]> frames, int w, int h, int bl, int bt, int br, int bb, float k) {
        int nf = frames == null ? 0 : frames.size();
        if (nf < 3 || w <= 3 || h <= 3) return null;
        int n = w * h;
        for (int[] f : frames) if (f == null || f.length != n) return null;
        int reach = Math.max(2, Math.round(REACH * k));
        int pl = clamp(bl - reach, 0, w), pt = clamp(bt - reach, 0, h), pr = clamp(br + reach, pl, w), pb = clamp(bb + reach, pt, h);
        if (pr - pl < 2 || pb - pt < 2) return null;
        boolean[] place = new boolean[n];
        for (int y = pt; y < pb; y++) for (int x = pl; x < pr; x++) place[y * w + x] = true;
        // Each pixel's middle value over the pictures, by channel.
        float[][] med = new float[3][n];
        int[] vals = new int[nf];
        for (int i = 0; i < n; i++) {
            for (int ch = 0; ch < 3; ch++) {
                int s = 16 - 8 * ch;
                for (int f = 0; f < nf; f++) vals[f] = (frames.get(f)[i] >> s) & 0xFF;
                med[ch][i] = median(vals, nf);
            }
        }
        // Where it may be: unlike the middle picture filled in from round the place, or unlike its own wide softening.
        boolean[] maybe = new boolean[n];
        float[][] round = inpaint(med, w, h, place);
        int wide = Math.max(4, Math.round(12 * k));
        float[][] soft = new float[3][];
        for (int ch = 0; ch < 3; ch++) {
            round[ch] = blur(round[ch], w, h, 1);
            soft[ch] = blur(blur(med[ch], w, h, wide), w, h, wide);
        }
        for (int i = 0; i < n; i++) maybe[i] = place[i] && (contrast(med, round, i) > 16 || contrast(med, soft, i) > 18);
        dropSpecks(maybe, w, h, 6);
        int few = Math.max(8, Math.round(FEW * k * k));
        if (count(maybe) < few) return new Model(w, h, new boolean[n], ones(n), new float[3][n], Model.NONE);
        // What each of those pixels lets through and adds; the logo's own; once more with the picture round it known better.
        boolean[] logo = maybe;
        Fit fit = null;
        for (int again = 0; again < 2; again++) {
            boolean[] ex = dilate(logo, w, h, 2);
            for (int i = 0; i < n; i++) ex[i] &= place[i];
            fit = fit(frames, w, h, ex);
            logo = new boolean[n];
            // Its own: it changes the light there plainly (well past what the pictures' own wanderings could), or it
            // lets much less through.
            for (int i = 0; i < n; i++) logo[i] = ex[i] && ((fit.effect[i] >= 6f && fit.sure[i] >= 4f) || fit.t[i] <= 0.6f);
            dropSpecks(logo, w, h, 6);
        }
        if (count(logo) < few) return new Model(w, h, new boolean[n], ones(n), new float[3][n], Model.NONE);
        // A stray pixel (not fitting a logo at all) takes what its neighbours let through; what it adds is worked out again
        // for that. Where it still cannot be worked back (it would add more light than there
        // is, or take light away): solid, filled in from beside it.
        float[] t = mend(fit.t, fit.fits, logo, w, h);
        float[][] add = new float[3][n];
        for (int i = 0; i < n; i++) {
            if (!logo[i]) {
                t[i] = 1f;
                continue;
            }
            boolean fits = true;
            for (int ch = 0; ch < 3; ch++) {
                float a = fit.meanO[ch][i] - t[i] * fit.meanV[ch][i];
                if (a < -30f || a > (1f - t[i]) * 255f + 30f) fits = false;
                add[ch][i] = Math.max(-8f, Math.min((1f - t[i]) * 255f + 8f, a));
            }
            if (!fits) t[i] = 0f;
        }
        // Its solid part (filled in from beside it): a block when much of it is deep in it.
        byte[] solid = new byte[n];
        for (int i = 0; i < n; i++) solid[i] = logo[i] && t[i] < HI ? (byte) 2 : 0;
        boolean block = deepShare(solid, w, h, Math.max(3, Math.round(DEEP * k))) > BLOCK_SHARE;
        return new Model(w, h, logo, t, add, block ? Model.BLOCK : Model.SEEN);
    }

    /**
     * What each pixel of a place lets through (t, shared by its channels), what it adds (by channel), what it changes on
     * the whole and how surely (that change over how far it could wander by chance), and whether it fits a logo at all.
     */
    static final class Fit {
        final float[] t, effect, sure;
        final float[][] add, meanO, meanV;
        final boolean[] fits;

        Fit(int n) {
            t = ones(n);
            effect = new float[n];
            sure = new float[n];
            add = new float[3][n];
            meanO = new float[3][n];
            meanV = new float[3][n];
            fits = new boolean[n];
        }
    }

    /**
     * Pure: for each pixel of ex, what it lets through and adds. What it lets through: how much its light swings from
     * picture to picture against how much the light round it swings (each pixel's own swing, filled in over ex from round
     * it: a see-through box that lets 78% through swings 78% as much). What it adds: picture by picture, what is there less what it lets through of the picture as it is
     * round it (filled in from the nearest pixels outside ex), on the whole. Where those cannot be so (it would add more
     * light than there is, or take light away), it is taken as solid: filled in from beside it.
     */
    static Fit fit(List<int[]> frames, int w, int h, boolean[] ex) {
        int n = w * h, nf = frames.size();
        Fit out = new Fit(n);
        int[] box = bounds(ex, w, h);
        if (box == null || nf < 2) return out;
        // Each pixel's swing over the pictures, by channel; and what it would be without the logo (from round ex).
        double[] s1 = new double[3 * n], s2 = new double[3 * n];
        for (int[] f : frames) {
            for (int i = 0; i < n; i++) {
                int p = f[i];
                for (int ch = 0; ch < 3; ch++) {
                    double v = (p >> (16 - 8 * ch)) & 0xFF;
                    s1[ch * n + i] += v;
                    s2[ch * n + i] += v * v;
                }
            }
        }
        float[][] sd = new float[3][n];
        for (int ch = 0; ch < 3; ch++) {
            for (int i = 0; i < n; i++) {
                double mu = s1[ch * n + i] / nf;
                sd[ch][i] = (float) Math.sqrt(Math.max(0, s2[ch * n + i] / nf - mu * mu));
            }
        }
        float[][] sdRound = inpaint(sd, w, h, ex);
        float[] raw = ones(n);
        for (int i = 0; i < n; i++) {
            if (!ex[i]) continue;
            float a = sd[0][i] + sd[1][i] + sd[2][i], b = sdRound[0][i] + sdRound[1][i] + sdRound[2][i];
            // Round it hardly swings: what it lets through cannot be told; taken as solid (filled in).
            raw[i] = b < 3 * 6f ? 0f : Math.max(0f, Math.min(1f, a / b));
        }
        float[] t = raw;
        // Picture by picture, the picture under ex as round it (from the nearest pixels outside it).
        int l = Math.max(0, box[0] - 1), tp = Math.max(0, box[1] - 1), r = Math.min(w, box[2] + 1), b = Math.min(h, box[3] + 1);
        int cw = r - l, ch2 = b - tp, cn = cw * ch2;
        boolean[] cex = new boolean[cn];
        for (int y = 0; y < ch2; y++) for (int x = 0; x < cw; x++) cex[y * cw + x] = ex[(tp + y) * w + l + x];
        double[] sa = new double[3 * cn], sdiff = new double[3 * cn], sdiff2 = new double[3 * cn], so = new double[3 * cn],
                sv = new double[3 * cn];
        float[][] img = new float[3][cn];
        for (int[] f : frames) {
            for (int y = 0; y < ch2; y++) {
                for (int x = 0; x < cw; x++) {
                    int p = f[(tp + y) * w + l + x], j = y * cw + x;
                    for (int c = 0; c < 3; c++) img[c][j] = (p >> (16 - 8 * c)) & 0xFF;
                }
            }
            float[][] round = inpaint(img, cw, ch2, cex);
            for (int j = 0; j < cn; j++) {
                if (!cex[j]) continue;
                int i = (tp + j / cw) * w + l + j % cw;
                for (int c = 0; c < 3; c++) {
                    double d = img[c][j] - round[c][j];
                    so[c * cn + j] += img[c][j];
                    sv[c * cn + j] += round[c][j];
                    sa[c * cn + j] += img[c][j] - t[i] * round[c][j];
                    sdiff[c * cn + j] += d;
                    sdiff2[c * cn + j] += d * d;
                }
            }
        }
        for (int j = 0; j < cn; j++) {
            if (!cex[j]) continue;
            int i = (tp + j / cw) * w + l + j % cw;
            float eff = 0f, sure = 0f;
            boolean fits = true;
            for (int c = 0; c < 3; c++) {
                int q = c * cn + j;
                float a = (float) (sa[q] / nf);
                double mean = sdiff[q] / nf, var = Math.max(0, sdiff2[q] / nf - mean * mean);
                // How far that mean could wander by chance, picture to picture (never less than half a level).
                double se = Math.max(0.5, Math.sqrt(var / nf));
                if (Math.abs(mean) > eff) {
                    eff = (float) Math.abs(mean);
                    sure = (float) (Math.abs(mean) / se);
                }
                // A logo's light is its own colour by how solid it is: from none to all of white, give or take.
                if (a < -30f || a > (1f - t[i]) * 255f + 30f) fits = false;
                out.add[c][i] = Math.max(-8f, Math.min((1f - t[i]) * 255f + 8f, a));
                out.meanO[c][i] = (float) (so[q] / nf);
                out.meanV[c][i] = (float) (sv[q] / nf);
            }
            out.t[i] = t[i];
            out.fits[i] = fits;
            out.effect[i] = eff;
            out.sure[i] = sure;
        }
        return out;
    }

    /**
     * Pure: what the logo's pixels let through, its strays mended: a pixel not fitting a logo (it would add more light than
     * there is, or take light away) takes the middle of what its fitting logo neighbours let through.
     */
    static float[] mend(float[] t, boolean[] fits, boolean[] logo, int w, int h) {
        float[] out = t.clone(), v = new float[8];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!logo[i]) continue;
                int c = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if ((dx == 0 && dy == 0) || xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int j = yy * w + xx;
                        if (!logo[j] || !fits[j]) continue;
                        v[c++] = t[j];
                    }
                }
                if (c < 3) continue;
                Arrays.sort(v, 0, c);
                float mid = (c & 1) == 1 ? v[c / 2] : (v[c / 2 - 1] + v[c / 2]) / 2f;
                if (!fits[i]) out[i] = mid;
            }
        }
        return out;
    }

    /**
     * Pure: the fill for a fresh picture (ARGB, w by h) over its part l, t, r, b: the picture there without the logo
     * (worked back where the logo lets enough through, filled in from beside it where it is too solid, both mixed
     * between), laid over the picture with as little covering as makes it so (none where nothing changes).
     */
    static int[] patch(int[] px, int w, int h, Model m, int l, int t, int r, int b) {
        if (px == null || m == null || px.length != w * h || m.w != w || m.h != h) return null;
        l = clamp(l, 0, w);
        r = clamp(r, l, w);
        t = clamp(t, 0, h);
        b = clamp(b, t, h);
        int cw = r - l, ch = b - t, n = cw * ch;
        if (n == 0) return new int[0];
        float[][] v = new float[3][n];
        float[][] back = new float[3][n];
        boolean[] gone = new boolean[n];
        for (int yy = 0; yy < ch; yy++) {
            for (int xx = 0; xx < cw; xx++) {
                int i = (t + yy) * w + l + xx, j = yy * cw + xx, p = px[i];
                boolean off = false;
                for (int c = 0; c < 3; c++) {
                    float o = (p >> (16 - 8 * c)) & 0xFF;
                    v[c][j] = o;
                    if (!m.logo[i]) continue;
                    float tt = m.t[i];
                    if (tt >= LO) {
                        float bk = (o - m.add[c][i]) / tt;
                        // Further out than a picture can be: the logo is not as learned here now; filled in instead.
                        if (bk < -24f || bk > 279f) off = true;
                        back[c][j] = clampF(bk);
                    } else {
                        back[c][j] = o;
                    }
                }
                gone[j] = m.logo[i] && (m.t[i] < HI || off);
                if (m.logo[i] && !gone[j]) for (int c = 0; c < 3; c++) v[c][j] = back[c][j];
                if (off) for (int c = 0; c < 3; c++) back[c][j] = -1f;
            }
        }
        float[][] filled = inpaint(v, cw, ch, gone);
        int[] out = new int[n];
        float[] o = new float[3], want = new float[3];
        for (int yy = 0; yy < ch; yy++) {
            for (int xx = 0; xx < cw; xx++) {
                int i = (t + yy) * w + l + xx, j = yy * cw + xx;
                if (!m.logo[i]) continue;
                float tt = m.t[i], s = back[0][j] < 0f ? 0f : tt >= HI ? 1f : tt <= LO ? 0f : (tt - LO) / (HI - LO);
                int p = px[i];
                float cover = 0f;
                for (int c = 0; c < 3; c++) {
                    o[c] = (p >> (16 - 8 * c)) & 0xFF;
                    want[c] = s * back[c][j] + (1 - s) * filled[c][j];
                    // As little covering as brings this channel to what it should be, with a colour that can be shown.
                    if (o[c] > want[c]) cover = Math.max(cover, (o[c] - want[c]) / Math.max(1f, o[c]));
                    else if (want[c] > o[c]) cover = Math.max(cover, (want[c] - o[c]) / Math.max(1f, 255f - o[c]));
                }
                int a = (int) Math.ceil(Math.min(1f, cover) * 255f - 1e-3f);
                if (a <= 0) continue;
                float k = a / 255f;
                int rgb = 0;
                for (int c = 0; c < 3; c++) {
                    int d = Math.round(clampF((want[c] - (1 - k) * o[c]) / k));
                    rgb |= d << (16 - 8 * c);
                }
                out[j] = (a << 24) | rgb;
            }
        }
        return out;
    }

    /** Pure: how alike two logos are (pixels in both over those in either); 1 when neither has any. */
    static float overlap(boolean[] a, boolean[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        int both = 0, either = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] && b[i]) both++;
            if (a[i] || b[i]) either++;
        }
        return either == 0 ? 1f : both / (float) either;
    }

    /** Pure: the box round a mask's pixels {l, t, r, b} (r and b just past it); null when it has none. */
    static int[] bounds(boolean[] mask, int w, int h) {
        int l = w, t = h, r = -1, b = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask[y * w + x]) continue;
                if (x < l) l = x;
                if (x > r) r = x;
                if (y < t) t = y;
                if (y > b) b = y;
            }
        }
        return r < 0 ? null : new int[]{l, t, r + 1, b + 1};
    }

    /**
     * Pure: the share of a shape's marked pixels further than deep from the pixels round it that are not marked (a
     * chamfer distance, near enough the real one): thin strokes have none; a solid block has most of its pixels deep in it.
     */
    static float deepShare(byte[] shape, int w, int h, int deep) {
        int n = w * h, big = 3 * (w + h) + 3, marked = 0, far = 0;
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
            if (shape[i] == 0) continue;
            marked++;
            if (d[i] > 3 * deep) far++;
        }
        return marked == 0 ? 0f : far / (float) marked;
    }

    /** Pure: a run of bytes packed small (runs of a value: the value, then how many, up to 255). */
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

    /** Pure: n packed bytes back; null when they do not come to n. */
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

    private static float[] ones(int n) {
        float[] f = new float[n];
        Arrays.fill(f, 1f);
        return f;
    }

    private static int count(boolean[] m) {
        int c = 0;
        for (boolean b : m) if (b) c++;
        return c;
    }

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

    /** How far the deepest pixel of a mask is from one outside it (steps any way, diagonals too). */
    static int depth(boolean[] m, int w, int h) {
        int n = w * h, big = w + h;
        int[] d = new int[n];
        for (int i = 0; i < n; i++) d[i] = m[i] ? big : 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (d[i] == 0) continue;
                int v = d[i];
                if (x > 0) v = Math.min(v, d[i - 1] + 1);
                if (y > 0) {
                    v = Math.min(v, d[i - w] + 1);
                    if (x > 0) v = Math.min(v, d[i - w - 1] + 1);
                    if (x < w - 1) v = Math.min(v, d[i - w + 1] + 1);
                }
                d[i] = v;
            }
        }
        int most = 0;
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                if (d[i] == 0) continue;
                int v = d[i];
                if (x < w - 1) v = Math.min(v, d[i + 1] + 1);
                if (y < h - 1) {
                    v = Math.min(v, d[i + w] + 1);
                    if (x < w - 1) v = Math.min(v, d[i + w + 1] + 1);
                    if (x > 0) v = Math.min(v, d[i + w - 1] + 1);
                }
                d[i] = v;
                if (v > most) most = v;
            }
        }
        return Math.min(most, big);
    }

    /** One channel softened by a box r each way (running sums), the edges as far as they go. */
    static float[] blur(float[] v, int w, int h, int r) {
        float[] out = new float[v.length];
        blurInto(v, w, h, r, new float[v.length], out);
        return out;
    }

    /** As blur, into out (tmp the same size, for the pass across). */
    static void blurInto(float[] v, int w, int h, int r, float[] tmp, float[] out) {
        for (int y = 0; y < h; y++) {
            int row = y * w, cnt = 0;
            float s = 0;
            for (int x = 0; x <= Math.min(w - 1, r); x++, cnt++) s += v[row + x];
            for (int x = 0; x < w; x++) {
                tmp[row + x] = s / cnt;
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
            for (int y = 0; y <= Math.min(h - 1, r); y++, cnt++) s += tmp[y * w + x];
            for (int y = 0; y < h; y++) {
                out[y * w + x] = s / cnt;
                int add = y + r + 1, drop = y - r;
                if (add < h) {
                    s += tmp[add * w + x];
                    cnt++;
                }
                if (drop >= 0) {
                    s -= tmp[drop * w + x];
                    cnt--;
                }
            }
        }
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

    /**
     * The masked pixels filled in from their unmasked neighbours (all eight), from the outside in, a layer at a time (only
     * the pixels still to fill looked at each time round).
     */
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
        int[] left = new int[todo], front = new int[todo];
        int nl = 0;
        for (int i = 0; i < n; i++) if (mask[i]) left[nl++] = i;
        float[][] vals = new float[chs][todo];
        float[] sums = new float[chs];
        while (nl > 0) {
            int nf = 0, keep = 0;
            for (int k = 0; k < nl; k++) {
                int i = left[k], x = i % w, y = i / w;
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
                if (cnt == 0) {
                    left[keep++] = i;
                    continue;
                }
                front[nf] = i;
                for (int ch = 0; ch < chs; ch++) vals[ch][nf] = sums[ch] / cnt;
                nf++;
            }
            if (nf == 0) break;
            for (int f = 0; f < nf; f++) {
                int i = front[f];
                known[i] = true;
                for (int ch = 0; ch < chs; ch++) out[ch][i] = vals[ch][f];
            }
            nl = keep;
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

    private static float clampF(float v) {
        return Math.max(0f, Math.min(255f, v));
    }
}
