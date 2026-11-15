package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.Penalty;
import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * The penalty card's pages as the board's pop-out draws them (made-up players), checked for what each page says; the
 * picture is written only when PREVIEW_OUT names a folder for it.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class PenaltyPreviewTest {
    private static Game game() {
        Game g = new Game();
        g.id = "preview-penalty";
        g.home = "WHI";
        g.away = "RIV";
        g.homeName = "Whitebrook";
        g.awayName = "Riverside";
        g.homeColor = "1b458f";
        g.awayColor = "c8102e";
        return g;
    }

    private static Penalty.Given given() {
        Penalty.Given p = new Penalty.Given();
        p.team = "Whitebrook";
        p.wonBy = "Sam Okafor";
        p.concededBy = "Theo Marsh";
        p.minute = "63'";
        return p;
    }

    private static Penalty.Records records() {
        Penalty.Records r = new Penalty.Records();
        r.taker = "Leo Brandt";
        r.takerLine = Penalty.takerLine(new int[]{4, 5, 0, 0}, null);
        r.keeper = "Jonas Vale";
        r.keeperLine = Penalty.keeperLine(new int[]{0, 0, 6, 1}, null);
        return r;
    }

    @Test public void itsPages() {
        ScoreBoard.Drop[] pages = ScoreBoard.penaltyDrops(game(), given(), 1, records());
        assertEquals(3, pages.length);
        for (ScoreBoard.Drop d : pages) {
            assertEquals("PENALTY", d.chip);
            assertTrue(d.penalty);
        }
        assertEquals("Whitebrook", pages[0].title);
        assertEquals("Won by Okafor · given away by Marsh", pages[0].line);
        assertEquals("Brandt", pages[1].title);
        assertEquals("Likely taker Brandt · scored 4 of 5 this season", pages[1].line);
        assertEquals(1, pages[1].side);
        assertEquals("Vale", pages[2].title);
        assertEquals("Vale in goal · saved 1 of 6 this season", pages[2].line);
        assertEquals(2, pages[2].side);
        // No records: the one page.
        assertEquals(1, ScoreBoard.penaltyDrops(game(), given(), 1, new Penalty.Records()).length);
    }

    @Test public void picture() throws Exception {
        String out = System.getProperty("preview.out", "");
        assumeTrue(!out.isEmpty());
        Context ctx = RuntimeEnvironment.getApplication();
        ScoreBoard.Drop[] pages = ScoreBoard.penaltyDrops(game(), given(), 1, records());
        // Made-up clubs' crests and players (silhouettes in their kits): the real card has their crests and photos.
        Bitmap home = crest(0xFF1B458F, "W"), away = crest(0xFFC8102E, "R");
        for (ScoreBoard.Drop d : pages) {
            d.homeCrest = home;
            d.awayCrest = away;
        }
        pages[1].photo = player(0xFF1B458F);
        pages[2].photo = player(0xFF2E2E2E);
        int W = 1280, bh = 64, head = Math.round(bh * 0.55f), rowH = 140, H = 40 + rowH * pages.length;
        Bitmap all = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(all);
        cv.drawColor(0xFF2E5E3A);   // a pitch-green stand-in for the picture
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        for (int i = 0; i < pages.length; i++) {
            float x = 40, y = 40 + i * rowH;
            // the board (a stand-in), then the real pop-out beside it
            p.setColor(0xF20B1033);
            cv.drawRoundRect(new RectF(x, y, x + 330, y + bh), 8, 8, p);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(bh * 0.5f);
            cv.drawText("WHI  1 - 1  RIV   63:20", x + 18, y + bh * 0.66f, p);
            OverlayBug.StripLook look = new OverlayBug.StripLook();
            BoardDrop strip = new BoardDrop(ctx);
            int up = pages[i].photo != null ? head : 0;
            strip.place(look, bh * ScoreBoard.TEXT, 0, false, up);
            int w = strip.widthFor(pages[i], bh, W - 420);
            FrameLayout root = new FrameLayout(ctx);
            root.addView(strip, new FrameLayout.LayoutParams(w, bh + up));
            // Out first (it starts hidden, and a hidden view is left out of the layout), then laid out and drawn.
            strip.showNow(pages[i], 0, 0f, 1f);
            root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(bh + up, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, w, bh + up);
            Bitmap one = Bitmap.createBitmap(w, bh + up, Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(one));
            cv.drawBitmap(one, x + 336, y - up, null);
        }
        new File(out).mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(out, "penalty-card.png"))) {
            all.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    /** A made-up club's crest: a disc in its colour with its letter. */
    private static Bitmap crest(int colour, String letter) {
        Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colour);
        c.drawCircle(48, 48, 44, p);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        c.drawCircle(48, 48, 40, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        p.setTextSize(52);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(letter, 48, 66, p);
        return b;
    }

    /** A made-up player, head and shoulders, in his kit's colour (where a real card has his photo). */
    private static Bitmap player(int kit) {
        Bitmap b = Bitmap.createBitmap(120, 150, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(kit);
        c.drawRoundRect(new RectF(10, 92, 110, 190), 44, 44, p);
        p.setColor(0xFFC89A7A);
        c.drawCircle(60, 56, 32, p);
        p.setColor(0xFF2B1D14);
        c.drawArc(new RectF(28, 22, 92, 70), 180, 180, true, p);
        return b;
    }
}
