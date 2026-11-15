package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.KitSheets;
import com.gathin.broadcast.KitTurn;
import com.gathin.broadcast.TestSheets;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

/**
 * The 3D kits look and a goal's shirt drawn without a phone, Newcastle v Arsenal: the board with its shirts in their cells
 * (at rest, flat ones before their frames come, GOAL on gold) and the away side's shirt turning at six moments for a
 * made-up scorer (HOLLIS 27). The frames
 * are drawn here as flat shirts, or are the page's own when PREVIEW_OUT holds them (kits/board-home.png, board-away.png,
 * turn-away.png and meta.json, as KITS.job makes them); pictures are written only when PREVIEW_OUT names a folder.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class KitsPreviewTest {
    private static final int ARS = 0xE20520, ARS2 = 0xFFFFFF, NEW = 0x111111, NEW2 = 0xFFFFFF;

    private static KitSheets.Sheet sheet(String out, String name, int size, float[] angles, int body, int trim) throws Exception {
        File dir = new File(out, "kits"), png = new File(dir, name + ".png"), meta = new File(dir, "meta.json");
        if (!out.isEmpty() && png.isFile() && meta.isFile()) {
            JSONObject m = new JSONObject(new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8)).getJSONObject(name);
            Bitmap b = BitmapFactory.decodeFile(png.getPath());
            return TestSheets.of(name, b, m.getInt("cols"), m.getInt("rows"), m.getInt("size"), angles);
        }
        return TestSheets.flat(name, size, angles, body, trim);
    }

    private static OverlayBug.Look look() {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "NEW";
        l.away = "ARS";
        l.score = "0–1";
        l.clock = "41:12";
        l.live = true;
        l.tag = "LIVE";
        l.homeColour = 0xFF000000 | NEW;
        l.awayColour = 0xFF000000 | ARS;
        return l;
    }

    /** The board laid out at its own width for height h, drawn into a picture. */
    private static Bitmap draw(FrameLayout root, OverlayBug b, int h) {
        root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), h);
        Bitmap one = Bitmap.createBitmap(Math.max(1, b.getWidth()), h, Bitmap.Config.ARGB_8888);
        b.draw(new Canvas(one));
        return one;
    }

    @Test public void theBoardsShirtsAndTheGoalsTurn() throws Exception {
        String out = System.getProperty("preview.out", "");
        Context ctx = RuntimeEnvironment.getApplication();
        KitSheets.Sheet home = sheet(out, "board-home", 96, KitSheets.boardAngles(OverlayBug.kitRest(true)), NEW, NEW2);
        KitSheets.Sheet away = sheet(out, "board-away", 96, KitSheets.boardAngles(OverlayBug.kitRest(false)), ARS, ARS2);
        KitSheets.Sheet turn = sheet(out, "turn-away", 256, KitSheets.turnAngles(), ARS, ARS2);

        // the board: its frames at rest, its flat shirts before they come, GOAL on gold as one goes in
        int h = 60;
        Bitmap[] boards = new Bitmap[3];
        float[] cellHome = null, cellAway = null;
        for (int i = 0; i < 3; i++) {
            OverlayBug b = new OverlayBug(ctx);
            b.style(OverlayBug.Style.KITS);
            FrameLayout root = new FrameLayout(ctx);
            root.addView(b, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h));
            b.set(look());
            b.kitColours(NEW, NEW2, ARS, ARS2);
            if (i != 1) b.kits(home, away);
            b.animAt(100_000L);
            b.shownAt(1f);
            if (i == 2) b.goalNow(2, OverlayBug.HIT_MS + 400);
            boards[i] = draw(root, b, h);
            if (i == 0) {
                cellHome = b.kitCell(1);
                cellAway = b.kitCell(2);
            }
        }
        assertNotNull(cellHome);
        assertNotNull(cellAway);
        assertTrue("home's cell left of away's", cellHome[0] < cellAway[0]);
        assertTrue(cellHome[2] > h && cellAway[0] < boards[0].getWidth());
        // the away shirt's red where its cell is (the cell itself is navy)
        int px = boards[0].getPixel(Math.round(cellAway[0]), Math.round(cellAway[1]));
        assertTrue(String.format("away shirt red at its cell: %08x", px), Color.red(px) > 120 && Color.blue(px) < 110);
        // another look has no cells
        OverlayBug classic = new OverlayBug(ctx);
        classic.style(OverlayBug.Style.CLASSIC);
        FrameLayout croot = new FrameLayout(ctx);
        croot.addView(classic, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h));
        classic.set(look());
        draw(croot, classic, h);
        assertNull(classic.kitCell(1));

        // the goal's shirt, over a 1280 by 720 picture, out of the away cell of a board in the top left
        int W = 1280, H = 720;
        FrameLayout stage = new FrameLayout(ctx);
        KitTurn t = new KitTurn(ctx);
        stage.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        stage.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        stage.layout(0, 0, W, H);
        float bx = 40, by = 40;
        boolean[] over = {false};
        t.play(turn, bx + cellAway[0], by + cellAway[1], cellAway[2], OverlayBug.kitRest(false), true,
                PhoneKits.caption("41", "Jay Hollis", false), () -> over[0] = true);
        assertTrue(t.playing());
        long[] at = {300, 900, 1600, 2400, 3200, 3800};
        Bitmap[] moments = new Bitmap[at.length];
        long was = 0;
        for (int i = 0; i < at.length; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(at[i] - was));
            was = at[i];
            Bitmap m = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(m);
            cv.drawColor(0xFF2E5E3A);   // a pitch-green stand-in for the picture
            cv.drawBitmap(boards[0], bx, by, null);
            t.draw(cv);
            moments[i] = m;
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(KitTurn.MS - was + 50));
        t.draw(new Canvas(Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)));
        assertFalse("over after its time", t.playing());
        assertTrue(over[0]);

        if (out.isEmpty()) return;
        new File(out).mkdirs();
        int bw = 0;
        for (Bitmap b : boards) bw = Math.max(bw, b.getWidth());
        Bitmap sheetOfBoards = Bitmap.createBitmap(bw + 80, 3 * (h + 40) + 40, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sheetOfBoards);
        sc.drawColor(0xFF2E5E3A);
        for (int i = 0; i < 3; i++) sc.drawBitmap(boards[i], 40, 40 + i * (h + 40), null);
        save(sheetOfBoards, new File(out, "kits-board.png"));
        // the six moments, at half size, three a row
        Bitmap all = Bitmap.createBitmap(3 * W / 2, 2 * H / 2, Bitmap.Config.ARGB_8888);
        Canvas ac = new Canvas(all);
        for (int i = 0; i < moments.length; i++) {
            Bitmap small = Bitmap.createScaledBitmap(moments[i], W / 2, H / 2, true);
            ac.drawBitmap(small, (i % 3) * W / 2f, (i / 3) * H / 2f, null);
        }
        save(all, new File(out, "kits-turn.png"));
    }

    private static void save(Bitmap b, File f) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }
}
