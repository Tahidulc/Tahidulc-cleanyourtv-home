package com.gathin.tvhome;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * adb only: pictures of the launcher's overlays exactly as the screen draws them (rounded corners, shadows and all),
 * without the screen: the views shown on a display of the app's own that draws into a picture (nothing appears on the
 * TV, which may stay asleep), each step of a series photographed in turn to a PNG.
 */
final class Snap {
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Snap() { }

    /**
     * The root shown full screen at w x h; then each step run in turn, and settleMs later the picture taken to its file;
     * done after the last.
     */
    static void series(Context ctx, View root, int w, int h, List<Runnable> steps, List<File> out, long settleMs, Runnable done) {
        Context c = ctx.getApplicationContext();
        ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        VirtualDisplay vd = c.getSystemService(DisplayManager.class).createVirtualDisplay("snap", w, h,
                c.getResources().getDisplayMetrics().densityDpi, reader.getSurface(), 0);
        Presentation p = new Presentation(c, vd.getDisplay(), android.R.style.Theme_DeviceDefault_NoActionBar);
        p.setContentView(root);
        p.show();
        int[] at = {0};
        Runnable next = new Runnable() {
            @Override public void run() {
                if (at[0] >= steps.size()) {
                    try { p.dismiss(); } catch (RuntimeException ignored) { }
                    vd.release();
                    reader.close();
                    if (done != null) done.run();
                    return;
                }
                int i = at[0]++;
                try { steps.get(i).run(); } catch (RuntimeException ignored) { }
                main.postDelayed(() -> {
                    save(reader, w, h, out.get(i));
                    main.post(this);
                }, settleMs);
            }
        };
        main.postDelayed(next, 600L);
    }

    private static void save(ImageReader reader, int w, int h, File f) {
        Image img = reader.acquireLatestImage();
        if (img == null) return;
        try {
            Image.Plane plane = img.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int stride = plane.getRowStride() / plane.getPixelStride();
            Bitmap wide = Bitmap.createBitmap(stride, h, Bitmap.Config.ARGB_8888);
            wide.copyPixelsFromBuffer(buf);
            Bitmap pic = Bitmap.createBitmap(wide, 0, 0, w, h);
            try (FileOutputStream o = new FileOutputStream(f)) { pic.compress(Bitmap.CompressFormat.PNG, 100, o); }
        } catch (Exception ignored) {
        } finally {
            img.close();
        }
    }

    /** A pitch under floodlights, standing in for the channel under an overlay: mown stripes, darker at the edges. */
    static Bitmap pitch(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint pt = new Paint();
        int stripe = w / 12;
        for (int i = 0; i < 12; i++) {
            pt.setColor(i % 2 == 0 ? 0xFF2F7D36 : 0xFF2A7231);
            c.drawRect(i * stripe, 0, (i + 1) * stripe + 1, h, pt);
        }
        Paint shade = new Paint();
        shade.setShader(new RadialGradient(w / 2f, h * 0.55f, w * 0.75f, 0x00000000, 0x99000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, shade);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0x99FFFFFF);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(w / 384f);
        c.drawLine(w / 2f, 0, w / 2f, h, line);
        c.drawCircle(w / 2f, h * 0.55f, h * 0.2f, line);
        return b;
    }
}
