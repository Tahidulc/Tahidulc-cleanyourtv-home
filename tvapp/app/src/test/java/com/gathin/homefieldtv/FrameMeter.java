package com.gathin.homefieldtv;

import static org.robolectric.Shadows.shadowOf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What a frame of motion costs, measured on the JVM (FrameCostTest, GuideDrawTest): the app drawn frame by frame at
 * 1920 x 1080 while the remote moves, each frame in two passes as the TV's renderer would do it.
 *
 * <ul>
 *   <li>The UI thread's part: only the views that were invalidated since the last frame are drawn again (on the TV, only
 *   those re-record their display lists), into a canvas that counts the drawing calls, the offscreen layers
 *   (saveLayer), the calls drawn in perspective and the bytes the frame made on the heap (the JVM's own count of the
 *   thread's allocations), and how long it took (nanoseconds on this machine: a guide, not the TV's).</li>
 *   <li>The graphics chip's part: the whole screen drawn again within the frame's damage (the bounds of the views drawn
 *   again: the TV redraws only that), each call's area added up: the fill, in whole screens' worth (the overdraw
 *   estimate: on the TCL's Mali-G52 each full screen of fill is about 2 ms).</li>
 * </ul>
 */
final class FrameMeter {
    static final int W = 1920, H = 1080;
    static final double SCREEN = (double) W * H;
    /** FRAME_TRACE set: one frame of each run's fill printed by where it was drawn from. */
    static final boolean TRACE = System.getenv("FRAME_TRACE") != null;

    /** A canvas that draws nothing and counts (nothing made per call, so what is measured is the app's own work). */
    static final class Counting extends Canvas {
        int ops, layers, perspective, nodes;
        double area;
        /** Claims to be the TV's hardware canvas (a RenderNode path is taken: drawRenderNode is counted as one call). */
        boolean hardware;
        private final Matrix m = new Matrix(), m2 = new Matrix();
        private final RectF b = new RectF();
        private final Rect clip = new Rect();
        private final float[] mv = new float[9];
        /** Tracing (FRAME_TRACE): each call's fill added up by where in the app it was drawn from. */
        java.util.Map<String, double[]> trace;

        Counting(Bitmap bitmap) {
            super(bitmap);
        }

        void reset() {
            ops = layers = perspective = nodes = 0;
            area = 0;
        }

        @Override public boolean isHardwareAccelerated() {
            return hardware;
        }

        /** One call filling (l, t, r, b) in the canvas's own coordinates. */
        /** The fill is added up (the chip's pass); the UI passes only count (the canvas's own lookups would be measured). */
        boolean areas;

        private void fill(float l, float t, float r, float bo) {
            ops++;
            if (!areas || !getClipBounds(clip)) return;
            b.set(Math.max(l, clip.left), Math.max(t, clip.top), Math.min(r, clip.right), Math.min(bo, clip.bottom));
            if (b.width() <= 0f || b.height() <= 0f) return;
            //noinspection deprecation
            getMatrix(m);
            m.getValues(mv);
            if (mv[6] != 0f || mv[7] != 0f) perspective++;
            m.mapRect(b);
            if (!b.intersect(0, 0, W, H)) return;
            double a = (double) b.width() * b.height();
            area += a;
            if (trace != null) {
                String where = "?";
                for (StackTraceElement e : new Throwable().getStackTrace()) {
                    String cn = e.getClassName();
                    if (cn.startsWith("com.gathin.homefieldtv") && !cn.contains("FrameMeter")) {
                        where = cn.substring(cn.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
                        break;
                    }
                    if (cn.startsWith("android.view.View") || cn.startsWith("com.android.internal.policy.DecorView")) {
                        where = cn.substring(cn.lastIndexOf('.') + 1) + "." + e.getMethodName();
                        break;
                    }
                }
                double[] tr = trace.computeIfAbsent(where, k -> new double[2]);
                tr[0] += a;
                tr[1]++;
            }
        }

        private void text(int n, float x, float y, Paint p) {
            float size = p.getTextSize();
            fill(x, y - size, x + size * 0.55f * n, y + size * 0.25f);
        }

        @Override public void drawText(String t, float x, float y, Paint p) {
            text(t.length(), x, y, p);
        }

        @Override public void drawText(String t, int start, int end, float x, float y, Paint p) {
            text(end - start, x, y, p);
        }

        @Override public void drawText(CharSequence t, int start, int end, float x, float y, Paint p) {
            text(end - start, x, y, p);
        }

        @Override public void drawText(char[] t, int index, int count, float x, float y, Paint p) {
            text(count, x, y, p);
        }

        @Override public void drawTextRun(char[] t, int index, int count, int cs, int cc, float x, float y, boolean rtl, Paint p) {
            text(count, x, y, p);
        }

        @Override public void drawTextRun(CharSequence t, int start, int end, int cs, int ce, float x, float y, boolean rtl, Paint p) {
            text(end - start, x, y, p);
        }

        @Override public void drawTextOnPath(String t, Path path, float h, float v, Paint p) {
            path.computeBounds(b, true);
            fill(b.left, b.top, b.right, b.bottom);
        }

        @Override public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
            fill(r.left, r.top, r.right, r.bottom);
        }

        @Override public void drawRoundRect(float l, float t, float r, float bo, float rx, float ry, Paint p) {
            fill(l, t, r, bo);
        }

        @Override public void drawRect(float l, float t, float r, float bo, Paint p) {
            fill(l, t, r, bo);
        }

        @Override public void drawRect(RectF r, Paint p) {
            fill(r.left, r.top, r.right, r.bottom);
        }

        @Override public void drawRect(Rect r, Paint p) {
            fill(r.left, r.top, r.right, r.bottom);
        }

        @Override public void drawOval(RectF r, Paint p) {
            fill(r.left, r.top, r.right, r.bottom);
        }

        @Override public void drawOval(float l, float t, float r, float bo, Paint p) {
            fill(l, t, r, bo);
        }

        @Override public void drawCircle(float cx, float cy, float radius, Paint p) {
            fill(cx - radius, cy - radius, cx + radius, cy + radius);
        }

        @Override public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
            float s = Math.max(1f, p.getStrokeWidth()) / 2f;
            fill(Math.min(x0, x1) - s, Math.min(y0, y1) - s, Math.max(x0, x1) + s, Math.max(y0, y1) + s);
        }

        @Override public void drawLines(float[] pts, int offset, int count, Paint p) {
            ops++;
        }

        @Override public void drawLines(float[] pts, Paint p) {
            ops++;
        }

        @Override public void drawPoint(float x, float y, Paint p) {
            ops++;
        }

        @Override public void drawPath(Path path, Paint p) {
            path.computeBounds(b, true);
            fill(b.left, b.top, b.right, b.bottom);
        }

        @Override public void drawArc(RectF oval, float a, float s, boolean c, Paint p) {
            fill(oval.left, oval.top, oval.right, oval.bottom);
        }

        @Override public void drawArc(float l, float t, float r, float bo, float a, float s, boolean c, Paint p) {
            fill(l, t, r, bo);
        }

        @Override public void drawBitmap(Bitmap bm, float left, float top, Paint p) {
            fill(left, top, left + bm.getWidth(), top + bm.getHeight());
        }

        @Override public void drawBitmap(Bitmap bm, Rect src, RectF dst, Paint p) {
            fill(dst.left, dst.top, dst.right, dst.bottom);
        }

        @Override public void drawBitmap(Bitmap bm, Rect src, Rect dst, Paint p) {
            fill(dst.left, dst.top, dst.right, dst.bottom);
        }

        @Override public void drawBitmap(Bitmap bm, Matrix matrix, Paint p) {
            b.set(0, 0, bm.getWidth(), bm.getHeight());
            matrix.mapRect(b);
            fill(b.left, b.top, b.right, b.bottom);
        }

        @Override public void drawPatch(NinePatch patch, RectF dst, Paint p) {
            fill(dst.left, dst.top, dst.right, dst.bottom);
        }

        @Override public void drawPatch(NinePatch patch, Rect dst, Paint p) {
            fill(dst.left, dst.top, dst.right, dst.bottom);
        }

        @Override public void drawColor(int color) {
            fill(-1e6f, -1e6f, 1e6f, 1e6f);
        }

        @Override public void drawColor(int color, android.graphics.PorterDuff.Mode mode) {
            fill(-1e6f, -1e6f, 1e6f, 1e6f);
        }

        @Override public void drawColor(long color, android.graphics.BlendMode mode) {
            fill(-1e6f, -1e6f, 1e6f, 1e6f);
        }

        @Override public void drawColor(int color, android.graphics.BlendMode mode) {
            fill(-1e6f, -1e6f, 1e6f, 1e6f);
        }

        @Override public void drawPaint(Paint p) {
            fill(-1e6f, -1e6f, 1e6f, 1e6f);
        }

        @Override public void drawRenderNode(RenderNode node) {
            ops++;
            nodes++;
        }

        // Offscreen layers: counted (each is a pass of its own on the TV's chip), then kept as a plain save.
        private int layer(float l, float t, float r, float bo) {
            layers++;
            fill(l, t, r, bo);
            return super.save();
        }

        @Override public int saveLayer(RectF bounds, Paint p) {
            return bounds == null ? layer(-1e6f, -1e6f, 1e6f, 1e6f) : layer(bounds.left, bounds.top, bounds.right, bounds.bottom);
        }

        @Override public int saveLayer(RectF bounds, Paint p, int flags) {
            return saveLayer(bounds, p);
        }

        @Override public int saveLayer(float l, float t, float r, float bo, Paint p) {
            return layer(l, t, r, bo);
        }

        @Override public int saveLayer(float l, float t, float r, float bo, Paint p, int flags) {
            return layer(l, t, r, bo);
        }

        @Override public int saveLayerAlpha(RectF bounds, int alpha) {
            return saveLayer(bounds, null);
        }

        @Override public int saveLayerAlpha(RectF bounds, int alpha, int flags) {
            return saveLayer(bounds, null);
        }

        @Override public int saveLayerAlpha(float l, float t, float r, float bo, int alpha) {
            return layer(l, t, r, bo);
        }

        @Override public int saveLayerAlpha(float l, float t, float r, float bo, int alpha, int flags) {
            return layer(l, t, r, bo);
        }
    }

    /** The JVM's count of the bytes this thread has allocated (com.sun.management.ThreadMXBean, reached by a handle). */
    private static java.lang.invoke.MethodHandle allocatedBytes;
    private static long thread;

    static long allocated() {
        try {
            if (allocatedBytes == null) {
                Class<?> mf = Class.forName("java.lang.management.ManagementFactory");
                Object bean = mf.getMethod("getThreadMXBean").invoke(null);
                Class<?> api = Class.forName("com.sun.management.ThreadMXBean");
                allocatedBytes = java.lang.invoke.MethodHandles.publicLookup()
                        .findVirtual(api, "getThreadAllocatedBytes", java.lang.invoke.MethodType.methodType(long.class, long.class))
                        .bindTo(bean);
                thread = Thread.currentThread().getId();
            }
            return (long) allocatedBytes.invokeExact(thread);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    /** One screen's frames of motion, added up. */
    static final class Run {
        final String name;
        int frames, idle;
        /** ops: the calls the app's drawing makes; rec: those recorded again on the TV's canvas (a kept part is one). */
        long ops, rec, bytes, layers, perspective, nodes, sprites;
        double fill, damage;
        final List<Long> nanos = new ArrayList<>();
        /** Tracing: the bytes made by each kind of view. */
        final java.util.Map<String, Long> byView = new java.util.TreeMap<>();

        Run(String name) {
            this.name = name;
        }

        double opsPerFrame() {
            return frames == 0 ? 0 : ops / (double) frames;
        }

        double recPerFrame() {
            return frames == 0 ? 0 : rec / (double) frames;
        }

        long bytesPerFrame() {
            return frames == 0 ? 0 : bytes / frames;
        }

        /** The fill each frame, in whole screens (the overdraw estimate). */
        double fillPerFrame() {
            return frames == 0 ? 0 : fill / frames / SCREEN;
        }

        double damagePerFrame() {
            return frames == 0 ? 0 : damage / frames / SCREEN;
        }

        double layersPerFrame() {
            return frames == 0 ? 0 : layers / (double) frames;
        }

        double perspectivePerFrame() {
            return frames == 0 ? 0 : perspective / (double) frames;
        }

        double medianMs() {
            if (nanos.isEmpty()) return 0;
            long[] n = new long[nanos.size()];
            for (int i = 0; i < n.length; i++) n[i] = nanos.get(i);
            Arrays.sort(n);
            return n[n.length / 2] / 1e6;
        }

        String views() {
            StringBuilder b = new StringBuilder(name);
            for (java.util.Map.Entry<String, Long> e : byView.entrySet()) b.append("  ").append(e.getKey()).append('=').append(frames == 0 ? 0 : e.getValue() / frames);
            return b.toString();
        }

        String row() {
            return String.format(Locale.ROOT, "%-30s %6d %6.0f %6.0f %8d %6.2f %6.0f%% %6.2f %6.2f %6.2f", name, frames, opsPerFrame(),
                    recPerFrame(), bytesPerFrame(), fillPerFrame(), damagePerFrame() * 100, layersPerFrame(), perspectivePerFrame(), medianMs());
        }

        static String header() {
            return String.format(Locale.ROOT, "%-30s %6s %6s %6s %8s %6s %7s %6s %6s %6s", "screen (a frame of motion)", "frames", "ops",
                    "rec", "bytes", "fill", "damage", "layers", "persp", "ui ms");
        }
    }

    private final View root;
    private final Counting ui, sw, gpu;
    private final List<View> dirty = new ArrayList<>();
    private final Rect damage = new Rect(), box = new Rect();
    private final int[] at = new int[2];
    /** Claim the TV's hardware canvas in the UI pass (a RenderNode path, where the app has one). */
    boolean hardware = true;

    FrameMeter(View root) {
        this.root = root;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        ui = new Counting(b);
        sw = new Counting(b);
        gpu = new Counting(b);
        gpu.areas = true;
        // A part kept as a layer (Cached) costs the chip one blit a frame, and its whole drawing only when recorded again.
        Cached.model = (c, w, h, changed) -> {
            if (c != gpu) return true;
            gpu.drawRect(0, 0, w, h, null);
            return changed;
        };
    }

    static void layout(View root) {
        root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, W, H);
    }

    /** The clock a frame on (16 ms), its callbacks and animations run, nothing measured. */
    void tick() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        layout(root);
        root.draw(gpu);
    }

    /** A frame: the clock 16 ms on, then the views invalidated drawn again (measured) and the damage filled. */
    void frame(Run run) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        layout(root);
        dirty.clear();
        collect(root);
        damage.setEmpty();
        for (View v : dirty) {
            v.getLocationInWindow(at);
            box.set(at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight());
            damage.union(box);
        }
        if (dirty.isEmpty()) {
            run.idle++;
            root.draw(gpu);
            return;
        }
        ui.reset();
        ui.hardware = hardware;
        int made = Shadows.made;
        long a0 = allocated(), t0 = System.nanoTime();
        for (View v : dirty) {
            // A group's children are drawn through the framework's own hardware path (not reproduced here): a plain canvas.
            ui.hardware = hardware && !(v instanceof ViewGroup);
            long b0 = TRACE ? allocated() : 0;
            draw(v, ui);
            if (TRACE) run.byView.merge(v.getClass().getSimpleName(), allocated() - b0, Long::sum);
        }
        long t1 = System.nanoTime();
        run.bytes += allocated() - a0;
        run.sprites += Shadows.made - made;
        run.nanos.add(t1 - t0);
        run.rec += ui.ops;
        run.nodes += ui.nodes;
        // The same frame on a plain canvas: every call the drawing makes (a kept part drawn out in full).
        sw.reset();
        for (View v : dirty) draw(v, sw);
        run.ops += sw.ops;
        run.layers += sw.layers;
        // The chip's part: the whole screen within the damage.
        gpu.reset();
        int save = gpu.save();
        gpu.clipRect(damage);
        root.draw(gpu);
        gpu.restoreToCount(save);
        run.fill += gpu.area;
        if (TRACE && run.frames == 20) {
            gpu.reset();
            gpu.trace = new java.util.HashMap<>();
            int s2 = gpu.save();
            gpu.clipRect(damage);
            root.draw(gpu);
            gpu.restoreToCount(s2);
            StringBuilder sb = new StringBuilder("\nTRACE " + run.name + " (screens of fill, calls)\n");
            gpu.trace.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0])).limit(25)
                    .forEach(e -> sb.append(String.format(Locale.ROOT, "  %6.2f %5.0f  %s%n", e.getValue()[0] / SCREEN, e.getValue()[1], e.getKey())));
            System.out.println(sb);
            gpu.trace = null;
        }
        run.perspective += gpu.perspective;
        run.damage += (double) damage.width() * damage.height();
        run.frames++;
    }

    /** View.onDraw, reached once by a handle (calling View.draw from here goes through the test's reflection, which allocates). */
    private static java.lang.invoke.MethodHandle onDraw;

    /** A view drawn as the frame draws it again: a leaf's own onDraw (its background aside), a group's whole draw. */
    private static void draw(View v, Canvas c) {
        if (v instanceof ViewGroup || v.getBackground() != null) {
            v.draw(c);
            return;
        }
        try {
            if (onDraw == null) {
                onDraw = java.lang.invoke.MethodHandles.privateLookupIn(View.class, java.lang.invoke.MethodHandles.lookup())
                        .findVirtual(View.class, "onDraw", java.lang.invoke.MethodType.methodType(void.class, Canvas.class));
            }
            onDraw.invokeExact(v, c);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    /** The views invalidated since they were last drawn (the ones whose own drawing changed: no dirty child below). */
    private boolean collect(View v) {
        if (v.getVisibility() != View.VISIBLE) return false;
        boolean child = false;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) child |= collect(g.getChildAt(i));
        }
        if (child) return true;
        if (v.isDirty() && v.getWidth() > 0) {
            dirty.add(v);
            return true;
        }
        return false;
    }

    /** n frames measured. */
    void frames(Run run, int n) {
        for (int i = 0; i < n; i++) frame(run);
    }

    /** Frames until nothing moves (at most n). */
    void settle(Run run, int n) {
        int still = 0;
        for (int i = 0; i < n && still < 3; i++) {
            int before = run.idle;
            frame(run);
            still = run.idle > before ? still + 1 : 0;
        }
    }

    static void down(android.app.Activity a, int code, int repeat) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t - repeat * 50L, t, KeyEvent.ACTION_DOWN, code, repeat));
    }

    static void up(android.app.Activity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }

    /** A key held: a repeat every 3 frames (20 a second), n of them, each frame measured; then let go and settled. */
    void hold(android.app.Activity a, Run run, int code, int n) {
        for (int i = 0; i < n; i++) {
            down(a, code, i);
            frames(run, 3);
        }
        up(a, code);
        settle(run, 60);
    }

    /** A single press, then its motion to the end. */
    void press(android.app.Activity a, Run run, int code) {
        down(a, code, 0);
        frame(run);
        up(a, code);
        settle(run, 60);
    }
}
