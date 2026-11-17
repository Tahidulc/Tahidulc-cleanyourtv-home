package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowSystemClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The TV guide's draw path as a frame of scrolling draws it (the micro-benchmark for the speed work): the grid of 40
 * made-up channels with their programmes, drawn frame by frame while a held Down glides the rows and Right pages the
 * times. Each frame's draw calls are counted, and what the frame makes on the heap measured (the JVM's own count of the
 * thread's allocated bytes): once warm, a frame of scrolling must make (next to) nothing and measure no text again
 * (TextFit's kept widths, Words' kept times), and the glides must move every frame with no step back.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class GuideDrawTest {
    /** A frame of scrolling may make at most this many bytes on the heap (a frame of the guide made ~40 KB before). */
    static final long FRAME_BYTES = 512;

    private Context app;

    @After public void after() {
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
        }
        Exec.inline = false;
    }

    /** A canvas that only counts what is drawn (so what is measured is the guide's own work, not the drawing's). */
    static final class Counting extends Canvas {
        int calls;

        Counting(Bitmap b) {
            super(b);
        }

        @Override public void drawText(String text, float x, float y, Paint paint) {
            calls++;
        }

        @Override public void drawText(String text, int start, int end, float x, float y, Paint paint) {
            calls++;
        }

        @Override public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
            calls++;
        }

        @Override public void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
            calls++;
        }

        @Override public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            calls++;
        }

        @Override public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint paint) {
            calls++;
        }

        @Override public void drawRect(float l, float t, float r, float b, Paint paint) {
            calls++;
        }

        @Override public void drawRect(RectF rect, Paint paint) {
            calls++;
        }

        @Override public void drawCircle(float cx, float cy, float radius, Paint paint) {
            calls++;
        }

        @Override public void drawLine(float x0, float y0, float x1, float y1, Paint paint) {
            calls++;
        }

        @Override public void drawPath(Path path, Paint paint) {
            calls++;
        }

        @Override public void drawArc(RectF oval, float a, float b, boolean c, Paint paint) {
            calls++;
        }

        @Override public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
            calls++;
        }

        @Override public void drawBitmap(Bitmap bitmap, android.graphics.Rect src, RectF dst, Paint paint) {
            calls++;
        }
    }

    /** The JVM's count of the bytes this thread has allocated (com.sun.management.ThreadMXBean, reached by a handle: no boxing). */
    private static java.lang.invoke.MethodHandle allocatedBytes;
    private static long thread;

    private static long allocated() {
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

    /** 40 channels, half-hour programmes from two hours back to eight on, every name invented. */
    private static void load(Context c) {
        Sources.clear(c);
        Sources.put(c, "p0", "Riverside", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        long now = System.currentTimeMillis(), half = 30 * 60_000L, from = now - now % half - 4 * half;
        List<Channel> chans = new ArrayList<>();
        Map<String, Guide.Schedule> guide = new HashMap<>();
        for (int i = 0; i < 40; i++) {
            Channel ch = new Channel("d:" + (5000 + i), "Lantern " + (i % 2 == 0 ? "Sports " : "Drama ") + (i + 1) + (i % 3 == 0 ? " HD" : ""),
                    i < 20 ? "Entertainment" : "Sports", "", "http://provider.invalid/live/" + i + ".ts", "", null, null, 100 + i);
            chans.add(ch);
            int n = 20;
            long[] st = new long[n], en = new long[n];
            for (int k = 0; k < n; k++) {
                st[k] = from + k * half;
                en[k] = st[k] + half;
            }
            guide.put(ch.key, MadeUpGuide.schedule(st, en));
        }
        Library.get(c).useForPreview(chans, guide);
    }

    @Test public void aFrameOfScrollingMakesNothingAndMeasuresNoText() {
        app = RuntimeEnvironment.getApplication();
        Exec.inline = true;
        load(app);
        Exec.inline = false;
        Library lib = Library.get(app);
        GuideGrid grid = new GuideGrid(app, lib);
        grid.setChannels(lib.lineup(), null);
        grid.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        grid.layout(0, 0, 1920, 1080);
        Counting c = new Counting(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888));

        // Warm: the first screens drawn once (their words measured, their times made), held Down through the list and
        // back, the times paged on and back, so every row and cell the scroll shows has been drawn before.
        for (int i = 0; i < 30; i++) frame(grid, c);
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < 39; i++) {
                grid.key(KeyEvent.KEYCODE_DPAD_DOWN);
                frames(grid, c, 3);
            }
            for (int i = 0; i < 39; i++) {
                grid.key(KeyEvent.KEYCODE_DPAD_UP);
                frames(grid, c, 3);
            }
            frames(grid, c, 30);
        }
        int measured = grid.textMeasured(), kept = grid.textKept();

        // Measured: Down held (a repeat every 3 frames, 20 a second) for 30 repeats, the rows gliding all the while.
        float last = grid.rowsShown();
        int calls = 0, frames = 0;
        long bytes = 0;
        boolean backwards = false;
        for (int i = 0; i < 30; i++) {
            grid.key(KeyEvent.KEYCODE_DPAD_DOWN);
            for (int f = 0; f < 3; f++) {
                c.calls = 0;
                ShadowSystemClock.advanceBy(Duration.ofMillis(16));
                long a0 = allocated();
                grid.onDraw(c);
                bytes += allocated() - a0;
                calls += c.calls;
                frames++;
                float now = grid.rowsShown();
                if (now < last - 0.01f) backwards = true;
                last = now;
            }
        }
        int textAfter = grid.textMeasured() - measured, hits = grid.textKept() - kept;
        long perFrame = bytes / frames;
        System.out.println(String.format(Locale.ROOT,
                "%nGUIDE DRAW (a frame of a held Down): %d draw calls, %d bytes made, %d words measured again, %d kept words used a frame",
                calls / frames, perFrame, textAfter, hits / frames));
        assertTrue("a frame made " + perFrame + " bytes", perFrame <= FRAME_BYTES);
        assertEquals("words measured again while scrolling", 0, textAfter);
        assertTrue("kept words used", hits > 0);
        assertTrue("the rows glided back", !backwards);
        assertTrue("the rows moved", grid.rowsShown() > 0f);
    }

    private static void frames(GuideGrid g, Counting c, int n) {
        for (int i = 0; i < n; i++) frame(g, c);
    }

    /** One frame: the clock 16 ms on, the grid drawn as the frame draws it. */
    private static void frame(GuideGrid g, Counting c) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(16));
        g.onDraw(c);
    }
}
