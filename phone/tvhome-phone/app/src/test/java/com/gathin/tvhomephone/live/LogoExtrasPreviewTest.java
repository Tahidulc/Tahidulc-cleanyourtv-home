package com.gathin.tvhomephone.live;

import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * The logo's extras drawn without a phone (Event's logo over a stand-in picture: its marks, the clock turned over, Up
 * next, the prayer pill), PNGs in the folder PREVIEW_OUT names. Skipped unless it is set; nothing here goes into the app.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class LogoExtrasPreviewTest {

    private static void shot(String file, boolean clock, int extra) throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0xFF23592F);
        LogoCover cover = new LogoCover(c);
        cover.look(LogoCover.Look.EVENT_WHITE);
        cover.seeThrough(true);
        cover.filled(true);
        cover.sub("main event");
        cover.marks(Marks.logo("4K", "HDR10+", "Dolby Atmos"));
        cover.clockRoom(true);
        if (clock) {
            cover.clock(true, "21:47", LogoExtras.SKY_PARTLY, "9°");
            // Turned over: the flip's half second gone.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(LogoCover.FLIP_MS + 50));
        }
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(330, 120);
        cp.leftMargin = 900;
        cp.topMargin = 30;
        root.addView(cover, cp);
        LogoExtras x = new LogoExtras(c, cover, new LogoExtras.Host() {
            @Override public boolean ready() { return true; }
            @Override public boolean clear(RectF onRoot) { return true; }
            @Override public String[] streamTags() { return null; }
        });
        root.addView(x, new FrameLayout.LayoutParams(-1, -1));
        root.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1280, 300);
        Bitmap first = Bitmap.createBitmap(1280, 300, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(first));   // the cover lays out its name box
        if (extra == LogoExtras.NEXT) x.showNow(LogoExtras.NEXT, "5", "Up next · 21:00", "Live Boxing: Archer v Mendes");
        if (extra == LogoExtras.PRAYER) x.showNow(LogoExtras.PRAYER, "", "Maghrib in 5 min", "16:12");
        Bitmap out = Bitmap.createBitmap(1280, 300, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(out));
        try (FileOutputStream f = new FileOutputStream(new File(System.getenv("PREVIEW_OUT"), file))) {
            out.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    @Test
    public void theLogosExtras() throws Exception {
        String dir = System.getenv("PREVIEW_OUT");
        assumeTrue(dir != null && !dir.isEmpty());
        new File(dir).mkdirs();
        shot("logo-marks.png", false, LogoExtras.NONE);
        shot("logo-clock.png", true, LogoExtras.NONE);
        shot("logo-next.png", false, LogoExtras.NEXT);
        shot("logo-prayer.png", false, LogoExtras.PRAYER);
    }
}
