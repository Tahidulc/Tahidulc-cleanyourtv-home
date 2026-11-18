package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.AddedTime;
import com.gathin.broadcast.CompBug;
import com.gathin.homefieldtv.sports.BoardClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Added time on every look of Homefield TV's board by the one rule the launcher's and the phone's take (AddedTime), each
 * in its own style, through a made-up game's second half's end (Whitebrook v Riverside): nothing before the minutes are
 * put up; then the +N in its tab at the board's end at its own width, beside a competition look's main row; kept narrow,
 * a competition's look (which cannot write them by its clock) keeps its tab. Drawn into a picture only when PREVIEW_OUT
 * names a folder.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class AddedTimeLooksTest {
    private static final int H = 60;

    private static BoardBug.Look look(String clock, String added) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = "WHI";
        l.away = "RIV";
        l.score = "1–1";
        l.clock = clock;
        l.live = true;
        l.stoppage = BoardClock.inAddedTime(clock, "90'+2'");
        l.added = added;
        l.tag = "PL";
        l.homeColour = 0xFF1B458F;
        l.awayColour = 0xFFC8102E;
        return l;
    }

    @Test public void everyLookTakesTheSameAddedTime() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        int[] put = {0, 4, 0, 0};
        List<Bitmap> rows = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (BoardBug.Style st : BoardBug.Style.values()) {
            for (String slug : st == BoardBug.Style.COMPETITION ? new String[]{"eng.1", "fifa.friendly"} : new String[]{"eng.1"}) {
                String name = st.name() + (st == BoardBug.Style.COMPETITION ? " " + slug : "");
                BoardBug b = new BoardBug(ctx);
                b.style(st);
                b.competition(slug, "eng.1".equals(slug) ? "Premier League" : "International Friendly");
                b.names("Whitebrook", "Riverside");
                CompBug.Kind k = BoardBug.kindOf(st, CompBug.forGame(slug, "eng.1".equals(slug) ? "Premier League" : "International Friendly"));
                assertEquals(name, k, b.kind());
                int bh = Math.round(H * b.tall());
                // 88:40: nothing put up yet; then from the half's last minute, the minutes put up.
                assertEquals(name, "", AddedTime.boardAdded(BoardClock.addedWords("88:40", "89'", put), false));
                String added = AddedTime.boardAdded(BoardClock.addedWords("89:10", "90'", put), false);
                assertEquals(name, "+4", added);
                b.set(look("88:40", ""));
                int without = b.natural(bh);
                b.set(look("91:20", added));
                int with = b.natural(bh);
                // At its own width the tab stands at the board's end (the board as wide as it needs, the tab beside it).
                assertTrue(name, with > without);
                assertTrue(name, b.addedTabbed(with, bh));
                // Kept narrow: a competition's look keeps the tab; the rest write the minutes by their clock.
                int narrow = Math.round(bh * 1.5f) + (with - without);
                assertEquals(name, !AddedTime.drawsInside(k), b.addedTabbed(narrow, bh));
                // The tab's band inside the board, and beside a competition look's main row.
                float[] band = b.tabBand(bh);
                assertTrue(name, band[0] >= -0.01f && band[0] + band[1] <= bh + 0.01f && band[1] > 0);
                if (k != null) {
                    float[] row = CompBug.bar(k, bh);
                    assertTrue(name, band[0] >= row[0] - 0.01f && band[0] + band[1] <= row[1] + 0.01f);
                }
                // No minutes put up: no tab.
                b.set(look("91:20", ""));
                assertFalse(name, b.addedTabbed(with, bh));
                rows.add(draw(ctx, b, look("91:20", added), bh));
                names.add(name);
            }
        }
        String out = System.getProperty("preview.out", "");
        if (out.isEmpty()) return;
        int w = 0;
        for (Bitmap r : rows) w = Math.max(w, r.getWidth());
        int gap = 16, label = 170, rowH = H * 4;
        Bitmap all = Bitmap.createBitmap(label + w + gap * 2, rows.size() * (rowH + gap) + gap, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(all);
        cv.drawColor(0xFF2E5E3A);   // a pitch-green stand-in for the picture
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(16);
        for (int i = 0; i < rows.size(); i++) {
            float y = gap + i * (rowH + gap);
            cv.drawText(names.get(i), gap, y + 22, p);
            cv.drawBitmap(rows.get(i), label, y, null);
        }
        new File(out).mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(out, "homefield-added-every-look.png"))) {
            all.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    /** The board in its look with the minutes added at its own width, as tall as the look is drawn. */
    private static Bitmap draw(Context ctx, BoardBug b, BoardBug.Look l, int bh) {
        FrameLayout root = new FrameLayout(ctx);
        b.set(l);
        b.animAt(100_000L);
        b.shownAt(1f);
        int w = b.natural(bh);
        root.addView(b, new FrameLayout.LayoutParams(w, bh));
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(bh, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, bh);
        Bitmap out = Bitmap.createBitmap(Math.max(1, w), bh, Bitmap.Config.ARGB_8888);
        b.draw(new Canvas(out));
        return out;
    }
}
