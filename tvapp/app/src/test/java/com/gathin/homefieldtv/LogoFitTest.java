package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.EditorialData;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.Resume;
import com.gathin.homefieldtv.engine.Source;
import com.gathin.homefieldtv.engine.Sources;
import com.gathin.homefieldtv.engine.Tmdb;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A title's logo (TMDB's title art) is drawn whole: fitted inside its box at its own shape, never cropped, at the
 * carousel's foot left (760 x 220), on a wide card, on a series' section and at the top of a title's page; a very wide
 * logo, a wide one and a nearly square one each kept to their own aspect, the box's width or height reached.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class LogoFitTest {
    private static final String[] FILMS = {"Midnight Orchard of the Long Harbour (2019)", "Glass Meridian (2021)", "Ember Road (2018)",
            "Paper Kites (2022)"};
    private ActivityController<TvActivity> tv;
    private Context app;

    @After public void after() {
        EditorialPageTest.drain();
        if (tv != null) tv.pause().stop().destroy();
        tv = null;
        EditorialData.clear();
        if (app != null) {
            Library.get(app).clear();
            Sources.clear(app);
            Resume.clear(app);
        }
        Exec.inline = false;
        EditorialData.freshCatalog();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** A logo w x h, solid to its edges (so its whole is what is measured). */
    private File logo(int w, int h) throws Exception {
        File f = new File(app.getCacheDir(), "logo-" + w + "x" + h + ".png");
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFFE8B996);
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        b.recycle();
        return f;
    }

    /** Home with a carousel of made-up films (TMDB made up), the remote down on it. */
    private TvActivity home() {
        EditorialPageTest.drain();
        app = RuntimeEnvironment.getApplication();
        EditorialData.freshCatalog();
        Exec.inline = true;
        Sources.clear(app);
        Sources.put(app, "p0", "Home", new Source(Source.XTREAM, null, null, "http://provider.invalid:8080", "family", "test"));
        List<String> rows = new ArrayList<>();
        rows.add("c\tm\t1\tUK | Comedy Movies");
        long added = System.currentTimeMillis() / 1000;
        for (int i = 0; i < FILMS.length; i++) {
            String year = FILMS[i].substring(FILMS[i].length() - 5, FILMS[i].length() - 1);
            rows.add("m\t" + (i + 1) + "\t" + FILMS[i] + "\t1\tfile:/nowhere/f" + i + ".jpg\t7.1\t" + (added - i * 3600L) * 1000L
                    + "\tmkv\t" + year + "\t\t\t");
        }
        Catalog.get(app).useForPreview(rows, new HashMap<>());
        Resume.clear(app);
        EditorialData.load(Catalog.get(app).films(), Catalog.get(app).series());
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        return tv.get();
    }

    private static void assertWhole(String what, RectF d, float boxW, float boxH, float aspect) {
        assertFalse(what + ": drawn", d.isEmpty());
        assertTrue(what + ": inside the box's width (" + d.width() + " of " + boxW + ")", d.width() <= boxW + 1f);
        assertTrue(what + ": inside the box's height (" + d.height() + " of " + boxH + ")", d.height() <= boxH + 1f);
        assertEquals(what + ": its own shape", aspect, d.width() / d.height(), aspect * 0.03f);
        assertTrue(what + ": as big as the box lets it be", d.width() >= boxW - 2f || d.height() >= boxH - 2f);
    }

    @Test public void aWideLogoIsDrawnWholeInEveryBox() throws Exception {
        home();
        int[][] logos = {{3000, 150}, {1600, 400}, {600, 500}};
        float[][] boxes = {{Editorial.HERO_LOGO_W, Editorial.HERO_LOGO_H}, {Editorial.CARD_LOGO_W, Editorial.CARD_LOGO_H},
                {TitlePage.ART_BOX_W, TitlePage.ART_BOX_H}, {TitlePage.LOGO_W, TitlePage.LOGO_H}};
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        for (int[] l : logos) {
            File f = logo(l[0], l[1]);
            for (float[] box : boxes) {
                Hero07.Mark m = new Hero07.Mark(46f);
                m.set("A Title Far Too Long For Its Box", "file:" + f.getPath(), Ui.f(box[0]), Ui.f(box[1]), Ui.f(box[0]));
                m.draw(c, 100f, 100f, 255, () -> { });
                assertWhole(l[0] + "x" + l[1] + " in " + box[0] + "x" + box[1], m.drawn(), Ui.f(box[0]), Ui.f(box[1]), l[0] / (float) l[1]);
            }
        }
        b.recycle();
    }

    @Test public void theCarouselsLogoIsWholeAtTheFootLeftOfItsWords() throws Exception {
        TvActivity a = home();
        File f = logo(3000, 150);
        EditorialPage page = a.homeScreen().editorial();
        for (Catalog.Title t : Catalog.get(app).films()) page.previewExtras(t, EditorialData.extras("file:" + f.getPath(), "12A"));
        long t0 = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0));
        a.dispatchKeyEvent(new KeyEvent(t0, t0 + 40, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN, 0));
        idle();
        Editorial view = page.view();
        assertEquals(Editorial.HERO, view.area());
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < 3; i++) {
            root.draw(new Canvas(b));
            idle();
        }
        b.recycle();
        RectF d = view.previewHeroMark().drawn();
        assertWhole("the carousel's logo", d, Ui.f(Editorial.HERO_LOGO_W), Ui.f(Editorial.HERO_LOGO_H), 20f);
        // At the left of its words (64 px into the card), inside the card.
        assertEquals(Ui.f(64f), d.left, 0.5f);
        assertTrue(d.top >= 0f && d.bottom <= Ui.f(Editorial.HERO_H));
    }
}
