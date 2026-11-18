package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Scratch: what the JVM's canvas calls themselves allocate (not committed). */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ScratchAllocTest {
    interface Op {
        void run(Canvas c);
    }

    private static void probe(String name, Canvas c, Op op) {
        for (int i = 0; i < 200; i++) op.run(c);
        long a0 = FrameMeter.allocated();
        for (int i = 0; i < 1000; i++) op.run(c);
        System.out.println("ALLOC " + name + ": " + (FrameMeter.allocated() - a0) / 1000.0 + " bytes a call");
    }

    @Test public void probes() {
        Ui.init(RuntimeEnvironment.getApplication());
        FrameMeter.Counting c = new FrameMeter.Counting(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888));
        Matrix m = new Matrix();
        Paint p = new Paint();
        RectF r = new RectF(0, 0, 10, 10);
        probe("save/restore", c, x -> {
            x.save();
            x.restore();
        });
        probe("translate", c, x -> {
            x.save();
            x.translate(1, 1);
            x.restore();
        });
        probe("scale", c, x -> {
            x.save();
            x.scale(1.1f, 1.1f);
            x.restore();
        });
        probe("scale pivot", c, x -> {
            x.save();
            x.scale(1.1f, 1.1f, 5, 5);
            x.restore();
        });
        probe("concat", c, x -> {
            x.save();
            x.concat(m);
            x.restore();
        });
        probe("clipRect", c, x -> {
            x.save();
            x.clipRect(0, 0, 5, 5);
            x.restore();
        });
        probe("drawRoundRect", c, x -> x.drawRoundRect(r, 2, 2, p));
        probe("paint.setAlpha", c, x -> p.setAlpha(100));
        probe("paint.setColor", c, x -> p.setColor(0xFF112233));
        probe("paint.getTextSize", c, x -> p.getTextSize());
        probe("paint.measureText", c, x -> p.measureText("Hello"));
        probe("shadow", c, x -> Shadows.draw(x, r, 4f, Look07.SH_CARD, 1f));
        probe("frost glass", c, x -> Frost.glass(x, r, 4f, 1f));
        probe("Pills.pill", c, x -> Pills.pill(x, 0, 5, "On now", false, true));
        android.view.View v = new android.view.View(RuntimeEnvironment.getApplication());
        Ground g = new Ground(v);
        probe("ground plain", c, x -> g.draw(x, 1920, 1080));
        g.set("file:/nonexistent.png");
        probe("ground missing", c, x -> g.draw(x, 1920, 1080));
        probe("Pics.get", c, x -> Pics.get("file:/nonexistent2.png", 10, 10, Pics.CROP, null));
        probe("uptime", c, x -> android.os.SystemClock.uptimeMillis());
        probe("anim time", c, x -> android.view.animation.AnimationUtils.currentAnimationTimeMillis());
        probe("motion", c, x -> Motion.lite());
        Glide gl = new Glide(300);
        probe("glide", c, x -> { gl.to(gl.target() + 1); gl.step(); });
        Lift07 lift = new Lift07();
        Object[] items = {new Object(), new Object()};
        int[] n = {0};
        probe("lift", c, x -> lift.step(items[(n[0]++ / 10) % 2]));
        probe("currentTimeMillis", c, x -> System.currentTimeMillis());
        probe("isShown", c, x -> v.isShown());
        probe("postInvalidateOnAnimation", c, x -> v.postInvalidateOnAnimation());
        probe("invalidate", c, x -> v.invalidate());
        android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(22f);
        android.text.StaticLayout lay = Ui.layout("A fisherman's daughter has one autumn to bring the family boat home before the harbour is sold.", tp, 600, 3);
        probe("layout.draw", c, x -> lay.draw(x));
        FrameMeter.Counting big = new FrameMeter.Counting(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888));
        probe("layout.draw big", big, x -> lay.draw(x));
        android.text.StaticLayout one = Ui.layout("Short words", tp, 600, 1);
        probe("layout.draw one line", big, x -> one.draw(x));
        android.text.StaticLayout ell = Ui.layout("A fisherman's daughter has one autumn to bring the family boat home before the harbour is sold, and every one of the town's fishermen has a reason to stop her, from the harbour master to her own brother who wants the money.", tp, 300, 2);
        probe("layout.draw ellipsized", big, x -> ell.draw(x));
        probe("layout.draw big real canvas", new Canvas(Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)), x -> lay.draw(x));
        probe("drawText string", c, x -> x.drawText("Hello there", 0, 10, tp));
    }
}
