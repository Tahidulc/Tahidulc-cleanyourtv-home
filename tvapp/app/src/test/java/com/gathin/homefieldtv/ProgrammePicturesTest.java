package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import com.gathin.broadcast.PicPlace;
import com.gathin.broadcast.PicSources;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.MadeUpGuide;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * The family's "every channel to have a programme background": a programme's pictures as a chain, the guide's own
 * first, a place taking the first that is large enough there (never enlarged past 1.5 times: a small picture fills a
 * guide cell, the info card takes the next), and one always there (a game's, its kind's, else the channel's own). A
 * guide cell's picture made once for the whole programme, with where it sits and its ground. Invented pictures.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class ProgrammePicturesTest {
    private static String small(String name) throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        Bitmap b = Bitmap.createBitmap(240, 135, Bitmap.Config.ARGB_8888);
        // A small scene (a sky fading into a hill, a face), not a plain card.
        Canvas cv = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0, 0, 240, 135, Color.rgb(60, 120, 200), Color.rgb(230, 160, 90),
                android.graphics.Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, 240, 135, p);
        p.setShader(null);
        p.setColor(Color.rgb(40, 80, 40));
        cv.drawOval(-40, 90, 300, 220, p);
        p.setColor(Color.rgb(240, 200, 170));
        cv.drawCircle(170, 55, 22, p);
        File dir = new File(app.getCacheDir(), "progpics");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, name + ".png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        return "file:" + f.getPath();
    }

    @Test public void aPlaceTakesTheFirstPictureLargeEnoughForIt() throws Exception {
        String own = small("thumb");
        Pics.knownFaces(own, 0.6f, 0.2f, 0.8f, 0.6f, 240 / 135f);
        String url = PicSources.chain(own, PicSources.genre(PicSources.NEWS));
        // A guide cell, 84 high: the thumbnail is large enough (shown whole at the cell's height, after the words).
        Bitmap cell = Pics.readNow(url, 1200, 84, Pics.SLIDE);
        assertNotNull(cell);
        assertEquals(0.6f, Pics.faces(url)[0], 0f);
        Pics.Placed p = Pics.placed(cell);
        assertNotNull(p);
        assertEquals(PicPlace.SHOW, p.lay[PicPlace.KIND], 0f);
        assertTrue("never past 1.5: " + p.lay[PicPlace.K], p.lay[PicPlace.K] <= PicPlace.MAX_UP);
        assertNotNull("its ground: the picture's edges' colours", p.edges);
        assertTrue("the picture alone, not the whole cell", cell.getWidth() < 1200);
        // The info card (1824 x 322): it would be enlarged past 1.5, so the next one (its kind's picture) fills it.
        Bitmap card = Pics.readNow(url, 1824, 322, Pics.FILL_CARD);
        assertNotNull(card);
        assertEquals(1824, card.getWidth());
        assertTrue(PicPlace.none(Pics.faces(url)));
        assertTrue(Pics.placed(card).lay[PicPlace.K] <= PicPlace.MAX_UP);
    }

    @Test public void aPlaceholderCardIsPassedOver() throws Exception {
        // A catalogue's "Image unavailable": one blue, a line of words; the chain's next (its kind's picture) instead.
        Context app = RuntimeEnvironment.getApplication();
        Bitmap b = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(Color.rgb(91, 155, 213));
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(28, 61, 110));
        p.setTextSize(40);
        cv.drawText("Image unavailable", 150, 190, p);
        File f = new File(app.getCacheDir(), "progpics-unavailable.png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        assertTrue(Pics.placeholder(b));
        String url = PicSources.chain("file:" + f.getPath(), PicSources.genre(PicSources.NEWS));
        Bitmap cell = Pics.readNow(url, 1200, 84, Pics.SLIDE);
        assertNotNull(cell);
        assertTrue("the kind's picture, not the card", PicPlace.none(Pics.faces(url)) && Pics.faces(url)[4] > 1.7f);
    }

    @Test public void everyProgrammeHasOneAlwaysThere() {
        long[] st = {0, 3600_000L, 7200_000L}, en = {3600_000L, 7200_000L, 10_800_000L};
        Guide.Schedule s = MadeUpGuide.schedule(st, en);
        Channel sport = new Channel("x:1", "Riverside Sport 1 HD", "UK Sport", "", "http://provider.invalid/1.ts", "", null, null, 401);
        Channel news = new Channel("x:2", "Bluefinch News", "UK News", "", "http://provider.invalid/2.ts", "", null, null, 501);
        Channel drama = new Channel("x:3", "Harbourlight One", "UK Entertainment", "", "http://provider.invalid/3.ts", "", null, null, 101);
        assertEquals(PicSources.genre(PicSources.SPORT), ProgrammePictures.fallback(sport, s, 0));
        assertEquals(PicSources.genre(PicSources.NEWS), ProgrammePictures.fallback(news, s, 1));
        // A channel with no logo and nothing to go by: its name's colour (never blank).
        assertEquals(PicSources.brand(PicSources.nameColour("Harbourlight One")), ProgrammePictures.fallback(drama, s, 2));
        assertEquals(PicSources.brand(PicSources.nameColour("Harbourlight One")), ProgrammePictures.ownPicture(drama));
    }
}
