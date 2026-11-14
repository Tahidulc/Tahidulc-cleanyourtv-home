package com.gathin.tvhomephone.live;

import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/** Stride's strap under an alert, drawn off the phone (only when PREVIEW_OUT names a folder for the picture). */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class StridePreviewTest {
    @Test public void strapUnderAnAlert() throws Exception {
        String out = System.getProperty("preview.out", "");
        assumeTrue(!out.isEmpty());
        int W = 1280, H = 360, bh = 64;
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(0xFF2E5E3A);   // a pitch-green stand-in for the picture
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int ground = 0xF0101421;
        float x = 60, y = 80;
        // the board
        p.setColor(ground);
        cv.drawRoundRect(new RectF(x, y, x + 330, y + bh), 8, 8, p);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        p.setTextSize(bh * 0.5f);
        cv.drawText("NOR  1 - 1  WAL   90:41", x + 18, y + bh * 0.66f, p);
        // the alert beside it
        float ax = x + 330 + 6, aw = 470;
        p.setColor(ground);
        cv.drawRoundRect(new RectF(ax, y, ax + aw, y + bh), 8, 8, p);
        p.setColor(0xFFE3061B);
        cv.drawRoundRect(new RectF(ax + 12, y + 14, ax + 150, y + bh - 14), 5, 5, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(bh * 0.32f);
        cv.drawText("ADDED TIME", ax + 22, y + bh * 0.61f, p);
        p.setTextSize(bh * 0.46f);
        cv.drawText("+4 minutes", ax + 168, y + bh * 0.66f, p);
        // Stride's strap under it, light half way across
        int sh = com.gathin.broadcast.Stride.strapHeight(bh), g = com.gathin.broadcast.Stride.gap(bh);
        com.gathin.broadcast.Stride.draw(cv, ax, y + bh + g, aw, sh, ground, true, -1f);
        com.gathin.broadcast.Stride.draw(cv, ax, y + bh + g + 120, aw, sh, ground, true, 0.5f);
        new File(out).mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(out, "stride-strap.png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }
}
