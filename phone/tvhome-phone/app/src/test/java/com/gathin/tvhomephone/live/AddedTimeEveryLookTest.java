package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.AddedTime;
import com.gathin.broadcast.CompBug;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Added time the same on every look (the nine first, the 3D kits, the Lab's five, By competition and each competition's
 * own, the four retro boards), each in its own style: a made-up game (Whitebrook v Riverside) through its second half's
 * end. Before the minutes are put up, no tab; once they are, the ADDED TIME pop-out ("+4 minutes") comes out of the board
 * in its look; while they run, with "Added time stays" on the board keeps no +N tab (the panel tucked under its end says
 * them), with it off the +N tab stands at the board's end, beside its main row; however narrow a look is kept, the minutes
 * are never lost (a competition's look, which cannot write them by its clock, keeps its tab). The rows are drawn into a
 * picture only when PREVIEW_OUT names a folder.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class AddedTimeEveryLookTest {
    private static final int H = 60;

    private static Game game() {
        Game g = new Game();
        g.id = "added-every-look";
        g.slug = "eng.1";
        g.home = "WHI";
        g.away = "RIV";
        g.homeName = "Whitebrook";
        g.awayName = "Riverside";
        g.homeColor = "1b458f";
        g.awayColor = "c8102e";
        g.state = Game.LIVE;
        g.period = 2;
        return g;
    }

    private static OverlayBug.Look look(String clock, String added) {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "WHI";
        l.away = "RIV";
        l.score = "1–1";
        l.clock = clock;
        l.live = true;
        l.stoppage = ScoreBoard.minuteOfClock(clock) >= 90;
        l.added = added;
        l.tag = "LIVE";
        l.homeColour = 0xFF1B458F;
        l.awayColour = 0xFFC8102E;
        return l;
    }

    private static OverlayBug board(Context ctx, OverlayBug.Style st, String slug) {
        OverlayBug b = new OverlayBug(ctx);
        b.style(st);
        b.competition(slug, "eng.1".equals(slug) ? "Premier League" : "International Friendly");
        b.names("Whitebrook", "Riverside");
        return b;
    }

    /** Every look, and By competition both with a competition's look (the Premier League's) and without (a friendly: Classic). */
    private static List<Object[]> looks() {
        List<Object[]> out = new ArrayList<>();
        for (OverlayBug.Style st : OverlayBug.Style.values()) out.add(new Object[]{st, "eng.1"});
        out.add(new Object[]{OverlayBug.Style.COMPETITION, "fifa.friendly"});
        return out;
    }

    @Test public void everyLookTakesTheSameAddedTime() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        Game g = game();
        int[] put = {-1, 4, -1, -1};
        List<Bitmap[]> rows = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Object[] one : looks()) {
            OverlayBug.Style st = (OverlayBug.Style) one[0];
            String slug = (String) one[1], name = st.name() + (st == OverlayBug.Style.COMPETITION ? " " + slug : "");
            OverlayBug b = board(ctx, st, slug);
            assertNotNull(name, b.stripLook());

            // 88:40: nothing put up yet, and none before the half's last minute even once it is known: no tab.
            String before = ScoreBoard.addedBox(2, 88, put, "");
            assertEquals(name, "", before);
            b.set(look("88:40", before));
            assertEquals(name, 0, b.tabRoom(H));

            // 89:10: the minutes put up. Out of the board once, as its ADDED TIME pop-out in its own look.
            String added = ScoreBoard.addedBox(2, 89, put, "");
            assertEquals(name, "+4", added);
            ScoreBoard.Drop d = ScoreBoard.addedDrop(g, added);
            assertEquals("ADDED TIME", d.chip);
            assertEquals("+4 minutes", d.title);
            assertEquals(4, d.addedMins);
            BoardDrop strip = new BoardDrop(ctx);
            strip.place(b.stripLook(), H * ScoreBoard.TEXT, 6, false, 0);
            assertTrue(name, strip.widthFor(d, H, 900) > H);

            // Added time stays on: the panel stands out under the board's end, the board keeps no +N tab of its own.
            String standing = ScoreBoard.boardAdded(added, true);
            b.set(look("91:20", standing));
            assertEquals(name, 0, b.tabRoom(H));
            assertEquals(name, 0, b.tabIn(b.natural(H), H));

            // Added time stays off: the +N tab at the board's end, at its own width.
            String tab = ScoreBoard.boardAdded(added, false);
            b.set(look("91:20", tab));
            int room = b.tabRoom(H), natural = b.natural(H);
            assertTrue(name, room > 0);
            assertEquals(name, room, b.tabIn(natural, H));
            // The tab's band inside the board's height, and for a competition's look beside its main row.
            float[] band = b.tabBand(H);
            assertTrue(name, band[0] >= -0.01f && band[0] + band[1] <= H + 0.01f && band[1] > 0);
            CompBug.Kind k = b.kind();
            if (k != null) {
                float[] row = CompBug.bar(k, H);
                assertTrue(name, band[0] >= row[0] - 0.01f && band[0] + band[1] <= row[1] + 0.01f);
            }
            // Kept narrow (its own part one and a half heights): the minutes still shown, beside it or by its clock.
            int narrow = Math.round(H * 1.5f) + room;
            assertTrue(name, b.tabIn(narrow, H) > 0 || AddedTime.drawsInside(k));
            if (!AddedTime.drawsInside(k)) assertEquals(name, room, b.tabIn(narrow, H));

            // Half time, full time, or the next half: the panel goes in.
            assertFalse(ScoreBoard.addedStands(Game.LIVE, true, false, 2, 2));
            assertFalse(ScoreBoard.addedStands(Game.POST, false, false, 2, 2));
            assertTrue(ScoreBoard.addedStands(Game.LIVE, false, true, 2, 2));

            rows.add(new Bitmap[]{draw(ctx, b, look("91:20", tab)), panel(ctx, board(ctx, st, slug), d)});
            names.add(name);
        }
        // The added time's own alert and the board's tab say the same minutes for every look: one rule.
        assertEquals("", AddedTime.boardAdded("+4", true));
        assertEquals("+4", AddedTime.boardAdded("+4", false));
        assertArrayEquals(new float[]{6f, 48f}, AddedTime.band(null, false, 60f), 0.01f);

        String out = System.getProperty("preview.out", "");
        if (out.isEmpty()) return;
        int w = 0;
        for (Bitmap[] r : rows) w = Math.max(w, r[0].getWidth() + r[1].getWidth());
        int gap = 18, label = 150, rowH = H * 4;
        Bitmap all = Bitmap.createBitmap(label + w + gap * 4, rows.size() * (rowH + gap) + gap, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(all);
        cv.drawColor(0xFF2E5E3A);   // a pitch-green stand-in for the picture
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(16);
        for (int i = 0; i < rows.size(); i++) {
            float y = gap + i * (rowH + gap);
            cv.drawText(names.get(i), gap, y + 22, p);
            cv.drawBitmap(rows.get(i)[0], label, y, null);
            cv.drawBitmap(rows.get(i)[1], label + rows.get(i)[0].getWidth() + gap * 2, y, null);
        }
        new File(out).mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(out, "added-every-look.png"))) {
            all.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    /** The board in its look with the minutes added, as tall as the look is drawn. */
    private static Bitmap draw(Context ctx, OverlayBug b, OverlayBug.Look l) {
        int bh = Math.round(H * b.tall());
        FrameLayout root = new FrameLayout(ctx);
        root.addView(b, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, bh));
        b.set(l);
        b.animAt(100_000L);
        b.tabNow(10_000L);
        b.shownAt(1f);
        root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(bh, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), bh);
        Bitmap out = Bitmap.createBitmap(Math.max(1, b.getWidth()), bh, Bitmap.Config.ARGB_8888);
        b.draw(new Canvas(out));
        return out;
    }

    /** The board with no tab (Added time stays) and its ADDED TIME panel tucked under its end, in its look. */
    private static Bitmap panel(Context ctx, OverlayBug b, ScoreBoard.Drop d) {
        Bitmap board = draw(ctx, b, look("91:20", ""));
        int bh = board.getHeight(), unitH = Math.round(bh / Math.max(1f, b.tall()));
        OverlayBug.StripLook sl = b.stripLook();
        int tuck = sl.apart ? 0 : Math.round(Math.min(8, unitH * sl.round)) + 1;
        BoardDrop strip = new BoardDrop(ctx);
        strip.place(sl, unitH * ScoreBoard.TEXT, tuck, false, 0);
        int w = strip.widthFor(d, unitH, 900);
        FrameLayout sroot = new FrameLayout(ctx);
        sroot.addView(strip, new FrameLayout.LayoutParams(w, unitH));
        strip.showNow(d, 0, 0f, 1f);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        sroot.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(unitH, View.MeasureSpec.EXACTLY));
        sroot.layout(0, 0, w, unitH);
        int left = board.getWidth() - tuck;
        Bitmap out = Bitmap.createBitmap(left + w + 4, bh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.save();
        cv.translate(left, 0);
        sroot.draw(cv);
        cv.restore();
        cv.drawBitmap(board, 0, 0, null);
        return out;
    }
}
