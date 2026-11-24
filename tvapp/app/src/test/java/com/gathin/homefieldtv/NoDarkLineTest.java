package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * The family's dark line through a programme's picture partway along a wide guide cell ("Day 2: … Live"): a picture's
 * own black bars (an invented 4:3 picture pillarboxed in a 16:9 frame) are cropped before it is laid out, the sharp
 * picture over its blurred copy is feathered at both its edges, and a pixel or two of its edge goes too; the cell keeps
 * its faces whole, and no column of it is black from top to foot.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class NoDarkLineTest {
    /** An invented pillarboxed frame, w x h ARGB: black bars of bar pixels either side (a few grey specks in them), a sky between. */
    private static int[] pillarboxed(int w, int h, int bar) {
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean inBar = x < bar || x >= w - bar;
                px[y * w + x] = inBar ? ((x * 7 + y * 13) % 97 == 0 ? 0xFF404040 : 0xFF050505) : 0xFF000000 | (90 + y) << 16 | (140 + x % 60) << 8 | 200;
            }
        }
        return px;
    }

    @Test public void barsAreFoundAndCroppedWithAPixelOrTwoMore() {
        int[] cut = Pillarbox.crop(pillarboxed(160, 90, 20), 160, 90);
        assertArrayEquals(new int[]{20 + Pillarbox.INSET, Pillarbox.INSET, 20 + Pillarbox.INSET, Pillarbox.INSET}, cut);
        // Letterboxed: rows.
        int w = 160, h = 90;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = y < 12 || y >= h - 12 ? 0xFF000000 : 0xFF3366AA;
        assertArrayEquals(new int[]{Pillarbox.INSET, 12 + Pillarbox.INSET, Pillarbox.INSET, 12 + Pillarbox.INSET}, Pillarbox.crop(px, w, h));
        // A night scene, dark through and through: only the inset.
        int[] night = new int[w * h];
        java.util.Arrays.fill(night, 0xFF080808);
        assertArrayEquals(new int[]{Pillarbox.INSET, Pillarbox.INSET, Pillarbox.INSET, Pillarbox.INSET}, Pillarbox.crop(night, w, h));
    }

    @Test public void theSharpPictureIsFeatheredAtBothEdgesAndItsFacesStayWhole() {
        // A 4:3 picture (its bars gone) in a very wide cell, its faces near its right edge.
        float w = 1100f, h = 110f;
        float[] lay = new float[PicFill.SIZE];
        PicFill.layout(4f / 3f, new float[]{0.62f, 0.18f, 0.97f, 0.62f}, w, h, false, lay);
        assertEquals(PicFill.AMBIENT, lay[PicFill.KIND], 0f);
        float least = Math.max(PicFill.FEATHER_MIN_PX, PicFill.FEATHER_MIN * h);
        assertTrue("left " + lay[PicFill.FEATHER_L], lay[PicFill.FEATHER_L] >= least - 0.01f);
        assertTrue("right " + lay[PicFill.FEATHER_R], lay[PicFill.FEATHER_R] >= least - 0.01f);
        assertTrue(lay[PicFill.FL] >= 0f && lay[PicFill.FR] <= w);
        assertTrue(lay[PicFill.FT] >= 0f && lay[PicFill.FB] <= h);
    }

    @Test public void aPillarboxedPictureDrawsNoDarkLineInAWideCell() throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        Pics.init(app);
        // 640 x 360 with a 4:3 picture in it: 80 px black bars either side, faces toward its right.
        Bitmap b = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, 360, 0xFF6FA8DC, 0xFFF6D7A7, Shader.TileMode.CLAMP));
        c.drawRect(80, 0, 560, 360, p);
        p.setShader(null);
        p.setColor(0xFFE0B090);
        c.drawCircle(420, 130, 40, p);
        c.drawCircle(490, 140, 40, p);
        File dir = new File(app.getCacheDir(), "nodarkline");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, "pillarboxed.png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        String url = "file:" + f.getPath();
        // The bars are gone from what the faces are found in: the trimmed picture is the 4:3 part, less a few pixels.
        Bitmap t = Pics.trimmed(b.copy(Bitmap.Config.ARGB_8888, false));
        assertTrue("trimmed to " + t.getWidth(), t.getWidth() <= 480 && t.getWidth() >= 460);
        assertTrue(t.getHeight() >= 350);
        // Its faces as they are in what is left (toward its right).
        float aspect = t.getWidth() / (float) t.getHeight();
        for (int mode : new int[]{Pics.FILL, Pics.FILL_LITE}) {
            Pics.knownFaces(url, 0.62f, 0.22f, 0.95f, 0.5f, aspect);
            int cw = 1100, ch = 110;
            Bitmap cell = Pics.readNow(url, cw, ch, mode);
            assertNotNull(cell);
            assertEquals(cw, cell.getWidth());
            int[] col = new int[ch];
            for (int x = 0; x < cw; x++) {
                cell.getPixels(col, 0, 1, x, 0, 1, ch);
                int dark = 0;
                for (int v : col) if (Pillarbox.dark(v)) dark++;
                assertTrue("mode " + mode + ": column " + x + " is a dark line", dark < ch * 0.9f);
            }
        }
    }
}
