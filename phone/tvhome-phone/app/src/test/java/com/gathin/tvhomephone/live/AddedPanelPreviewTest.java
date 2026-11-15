package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.tvhomephone.sports.Game;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;

/**
 * Added time with the minutes added out beside the board for the rest of the half: the board with no +N tab of its own
 * (the panel says it), the panel tucked under the board's end as the other pop-outs are; and, for comparison, the board
 * with its tab and the panel after it. Made-up clubs. The picture is written only when PREVIEW_OUT names a folder.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class AddedPanelPreviewTest {
    private static Game game() {
        Game g = new Game();
        g.id = "preview-added";
        g.home = "WHI";
        g.away = "RIV";
        g.homeName = "Whitebrook";
        g.awayName = "Riverside";
        g.homeColor = "1b458f";
        g.awayColor = "c8102e";
        return g;
    }

    private static OverlayBug.Look look(boolean tab) {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "WHI";
        l.away = "RIV";
        l.score = "1–1";
        l.clock = "90:41";
        l.live = true;
        l.stoppage = true;
        l.added = tab ? "+4" : "";
        l.tag = "LIVE";
        l.homeColour = 0xFF1B458F;
        l.awayColour = 0xFFC8102E;
        return l;
    }

    /** One row: the board (with or without its tab) and the panel beside it as the board's controller places it. */
    private static Bitmap row(Context ctx, OverlayBug.Style style, boolean tab, int bh) {
        OverlayBug b = new OverlayBug(ctx);
        b.style(style);
        FrameLayout root = new FrameLayout(ctx);
        root.addView(b, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, bh));
        b.set(look(tab));
        b.animAt(100_000L);
        b.shownAt(1f);
        root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(bh, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), bh);
        int bw = b.getWidth();
        OverlayBug.StripLook sl = b.stripLook();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int tuck = sl.apart ? 0 : Math.round(Math.min(8 * dp, bh * sl.round)) + 1;
        boolean tabOut = b.tabIn(bw, bh) > 0;
        int nearTuck = tabOut ? 0 : tuck, nearGap = tabOut ? Math.round(bh * 0.06f) : (sl.apart ? Math.round(bh * 0.12f) : 0);
        ScoreBoard.Drop d = ScoreBoard.addedDrop(game(), "+4");
        BoardDrop strip = new BoardDrop(ctx);
        strip.place(sl, bh * ScoreBoard.TEXT, nearTuck, false, 0);
        int w = strip.widthFor(d, bh, 900);
        FrameLayout sroot = new FrameLayout(ctx);
        sroot.addView(strip, new FrameLayout.LayoutParams(w, bh));
        strip.showNow(d, 0, 0f, 1f);
        // the minutes added counted up to theirs
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        sroot.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(bh, View.MeasureSpec.EXACTLY));
        sroot.layout(0, 0, w, bh);
        int left = bw - nearTuck + nearGap;
        Bitmap out = Bitmap.createBitmap(left + w + 4, bh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        // the panel first, the board over its tucked end
        cv.save();
        cv.translate(left, 0);
        sroot.draw(cv);
        cv.restore();
        b.draw(cv);
        return out;
    }

    @Test public void theBoardHasNoTabOfItsOwnWhileThePanelIsOut() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        int bh = 60;
        OverlayBug.Style[] styles = {OverlayBug.Style.CLASSIC, OverlayBug.Style.FROSTED, OverlayBug.Style.CARD, OverlayBug.Style.STACKED};
        Bitmap[][] rows = new Bitmap[styles.length][2];
        for (int i = 0; i < styles.length; i++) {
            rows[i][0] = row(ctx, styles[i], true, bh);
            rows[i][1] = row(ctx, styles[i], false, bh);
        }
        // the board without the minutes added is narrower: their tab is not there
        OverlayBug b = new OverlayBug(ctx);
        b.style(OverlayBug.Style.CLASSIC);
        b.set(look(false));
        assertEquals(0, b.tabIn(b.natural(bh), bh));
        assertTrue(rows[0][1].getWidth() > 0);

        String out = System.getProperty("preview.out", "");
        if (out.isEmpty()) return;
        int w = 0;
        for (Bitmap[] r : rows) w = Math.max(w, Math.max(r[0].getWidth(), r[1].getWidth()));
        int gap = 36;
        Bitmap all = Bitmap.createBitmap(w * 2 + gap * 3, styles.length * (bh + gap) + gap, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(all);
        cv.drawColor(0xFF2E5E3A);   // a pitch-green stand-in for the picture
        for (int i = 0; i < styles.length; i++) {
            float y = gap + i * (bh + gap);
            cv.drawBitmap(rows[i][0], gap, y, null);
            cv.drawBitmap(rows[i][1], gap * 2 + w, y, null);
        }
        new File(out).mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(out, "added-panel.png"))) {
            all.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }
}
