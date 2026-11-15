package com.gathin.broadcast;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Scoreboard Lab's looks, logos and animations drawn off any TV or phone (made-up clubs, Whitebrook v Riverside),
 * each checked to draw; the sheets are written only when PREVIEW_OUT names a folder (preview.out).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class LabPreviewTest {
    private static final int PITCH = 0xFF2E5E3A, GAP = 28;
    private static final CompBug.Kind[] LOOKS = {CompBug.Kind.FLOODLIGHT, CompBug.Kind.SPLITKIT, CompBug.Kind.TICKER, CompBug.Kind.DUO,
            CompBug.Kind.LED};

    private static CompBug.Data data() {
        CompBug.Data d = new CompBug.Data();
        d.home = "WHI";
        d.away = "RIV";
        d.homeName = "Whitebrook";
        d.awayName = "Riverside";
        d.score = "2–1";
        d.clock = "74:05";
        d.comp = "Premier League";
        d.homeColour = 0xFF1B458F;
        d.awayColour = 0xFFC8102E;
        return d;
    }

    private static Bitmap look(CompBug bug, CompBug.Kind k, CompBug.Data d, int row) {
        int h = Math.round(row * k.tall), w = Math.max(1, Math.round(bug.natural(k, d, h)));
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bug.draw(new Canvas(b), k, d, w, h);
        return b;
    }

    /** How many of a picture's pixels are drawn on (not clear). */
    private static int inked(Bitmap b) {
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        int n = 0;
        for (int p : px) if ((p >>> 24) > 16) n++;
        return n;
    }

    /** How many pixels two same-sized pictures differ in. */
    private static int differ(Bitmap a, Bitmap b) {
        int w = a.getWidth(), h = a.getHeight();
        int[] pa = new int[w * h], pb = new int[w * h];
        a.getPixels(pa, 0, w, 0, 0, w, h);
        b.getPixels(pb, 0, w, 0, 0, w, h);
        int n = 0;
        for (int i = 0; i < pa.length; i++) if (pa[i] != pb[i]) n++;
        return n;
    }

    /** Rows of pictures with a caption over each, on the pitch's green. */
    private static Bitmap sheet(List<String> names, List<Bitmap[]> rows) {
        int w = 0, h = GAP;
        for (Bitmap[] r : rows) {
            int rw = GAP, rh = 0;
            for (Bitmap b : r) {
                rw += b.getWidth() + GAP;
                rh = Math.max(rh, b.getHeight());
            }
            w = Math.max(w, rw);
            h += rh + GAP + 22;
        }
        Bitmap all = Bitmap.createBitmap(Math.max(w, 400), h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(all);
        cv.drawColor(PITCH);
        Paint stripe = new Paint();
        stripe.setColor(0x0FFFFFFF);
        for (int x = 0; x < all.getWidth(); x += 160) cv.drawRect(x, 0, x + 80, all.getHeight(), stripe);
        Paint cap = new Paint(Paint.ANTI_ALIAS_FLAG);
        cap.setColor(0xCCFFFFFF);
        cap.setTextSize(15);
        cap.setTypeface(Typeface.DEFAULT_BOLD);
        int y = GAP;
        for (int i = 0; i < rows.size(); i++) {
            cv.drawText(names.get(i), GAP, y + 14, cap);
            y += 22;
            int x = GAP, rh = 0;
            for (Bitmap b : rows.get(i)) {
                cv.drawBitmap(b, x, y, null);
                x += b.getWidth() + GAP;
                rh = Math.max(rh, b.getHeight());
            }
            y += rh + GAP;
        }
        return all;
    }

    private static void write(Bitmap b, String name) throws Exception {
        String out = System.getProperty("preview.out", "");
        if (out.isEmpty()) return;
        new File(out).mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(out, name))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    @Test
    public void theLabsLooksDrawAtEverySize() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        CompBug bug = new CompBug(ctx);
        List<String> names = new ArrayList<>();
        List<Bitmap[]> rows = new ArrayList<>();
        for (CompBug.Kind k : LOOKS) {
            CompBug.Data d = data();
            Bitmap big = look(bug, k, d, 64), mid = look(bug, k, d, 40), small = look(bug, k, d, 24);
            assertTrue(k + " draws", inked(big) > big.getWidth() * big.getHeight() / 6);
            assertTrue(k + " natural width", bug.natural(k, d, 40 * k.tall) > 40 * 2.5f);
            assertTrue(k + " first block", bug.first(k, d, 40 * k.tall) > 4f);
            // Added time: the clock red; half time turned over; before kick-off its time; a goal's figure on its way.
            CompBug.Data st = data();
            st.clock = "90:00";
            st.stoppage = true;
            CompBug.Data ht = data();
            ht.clock = "HT";
            ht.htFace = true;
            CompBug.Data pre = data();
            pre.score = "19:45";
            pre.clock = "";
            CompBug.Data roll = data();
            roll.move = CompBug.MOVE_ROLL;
            roll.moveAt = 0.45f;
            roll.before = new String[]{"1", "1"};
            CompBug.Data flip = data();
            flip.move = CompBug.MOVE_FLIP;
            flip.moveAt = 0.5f;
            flip.before = new String[]{"1", "1"};
            CompBug.Data glow = data();
            glow.glow = 0.8f;
            glow.glowSide = 1;
            Bitmap rolled = look(bug, k, roll, 40);
            assertTrue(k + " rolls", differ(rolled, mid) > 0);
            names.add(k.label + ": 64 / 40 / 24 high");
            rows.add(new Bitmap[]{big, mid, small});
            names.add(k.label + ": added time, half time turned, before kick-off, rolling, flipping, the new score's glow");
            rows.add(new Bitmap[]{look(bug, k, st, 40), look(bug, k, ht, 40), look(bug, k, pre, 40), rolled, look(bug, k, flip, 40),
                    look(bug, k, glow, 40)});
        }
        // No competition named: the Ticker's block has the family's mark; a long-coded game still fits.
        CompBug.Data none = data();
        none.comp = "";
        CompBug.Data longer = data();
        longer.home = "WHITE";
        longer.away = "RIVER";
        longer.score = "10–11";
        longer.clock = "118:45";
        names.add("No competition (Ticker, Floodlight); long codes and score (each look)");
        List<Bitmap> extra = new ArrayList<>(Arrays.asList(look(bug, CompBug.Kind.TICKER, none, 40), look(bug, CompBug.Kind.FLOODLIGHT, none, 40)));
        for (CompBug.Kind k : LOOKS) extra.add(look(bug, k, longer, 40));
        rows.add(extra.toArray(new Bitmap[0]));
        write(sheet(names, rows), "lab-looks.png");
    }

    @Test
    public void theLabsLogosDrawForTheFamilysWords() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        LabMarks marks = new LabMarks(ctx);
        List<String> names = new ArrayList<>();
        List<Bitmap[]> rows = new ArrayList<>();
        for (LabMarks.Look l : LabMarks.Look.values()) {
            List<Bitmap> row = new ArrayList<>();
            for (Object[] v : new Object[][]{{54, "SPORTS", ""}, {38, "SPORTS", "HD"}, {54, "", ""}, {26, "SPORTS", ""}}) {
                int h = (Integer) v[0];
                String sports = (String) v[1], tag = (String) v[2];
                float w = marks.width(l, h, "Whitebrook Road", sports, tag);
                assertTrue(l + " width", w > h * 1.5f);
                Bitmap b = Bitmap.createBitmap(Math.round(w), h, Bitmap.Config.ARGB_8888);
                marks.draw(new Canvas(b), l, b.getWidth(), h, "Whitebrook Road", sports, tag);
                assertTrue(l + " draws", inked(b) > b.getWidth() * h / 3);
                row.add(b);
            }
            names.add(l.label + ": 54 high, 38 with HD, a general channel (no SPORTS), 26 high");
            rows.add(row.toArray(new Bitmap[0]));
        }
        // The words follow the family's name.
        float a = marks.width(LabMarks.Look.DUO, 40, "Elm Park", "", ""), b = marks.width(LabMarks.Look.DUO, 40, "Whitebrook Road", "", "");
        assertTrue(a < b);
        write(sheet(names, rows), "lab-logos.png");
    }

    @Test
    public void theLabsAnimationsDrawOverABoard() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        CompBug bug = new CompBug(ctx);
        BoardFx fx = new BoardFx();
        Bitmap crest = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas cc = new Canvas(crest);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF1B458F);
        cc.drawCircle(32, 32, 30, p);
        p.setColor(0xFFFFFFFF);
        cc.drawRect(22, 18, 42, 46, p);
        List<String> names = new ArrayList<>();
        List<Bitmap[]> rows = new ArrayList<>();
        CompBug.Kind base = CompBug.Kind.TICKER;
        int row = 48;

        // The flip: the side's wash, the shine and the new figure turning down, at three moments.
        List<Bitmap> flips = new ArrayList<>();
        for (long t : new long[]{300, 520, 900}) {
            CompBug.Data d = data();
            d.move = CompBug.MOVE_FLIP;
            d.moveAt = BoardFx.flipMove(t);
            d.before = new String[]{"1", "1"};
            Bitmap b = look(bug, base, d, row);
            Bitmap plain = b.copy(Bitmap.Config.ARGB_8888, true);
            Canvas cv = new Canvas(b);
            float half = b.getWidth() * 0.42f;
            fx.sideWash(cv, 0, 0, half, b.getHeight(), 0xFF1B458F, BoardFx.washAlpha(t) * 0.8f);
            fx.shineSweep(cv, b.getWidth(), b.getHeight(), t);
            assertTrue(differ(plain, b) > 0);
            flips.add(b);
        }
        names.add("Goal, the flip: wash + shine + the new figure turning down (300, 520, 900 ms)");
        rows.add(flips.toArray(new Bitmap[0]));

        // The burst: rings from the crest and its pop, the score rolling up, the side's glow.
        List<Bitmap> bursts = new ArrayList<>();
        for (long t : new long[]{150, 700, 1100}) {
            CompBug.Data d = data();
            d.move = CompBug.MOVE_ROLL;
            d.moveAt = BoardFx.rollMove(t);
            d.before = new String[]{"1", "1"};
            Bitmap board = look(bug, CompBug.Kind.DUO, d, row);
            Bitmap b = Bitmap.createBitmap(board.getWidth() + row * 2, board.getHeight() + row * 2, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawBitmap(board, row, row, null);
            fx.sideWash(cv, row, row, row + board.getWidth() * 0.42f, row + board.getHeight(), 0xFF1B458F, BoardFx.glowAlpha(t));
            fx.crestBurst(cv, row + board.getHeight() * 0.5f, row + board.getHeight() * 0.5f, board.getHeight() * 0.36f, crest, 0xFF1B458F, t);
            assertTrue(inked(b) > inked(board));
            bursts.add(b);
        }
        names.add("Goal, the burst: crest pop + rings, the roll, the glow (150, 700, 1100 ms)");
        rows.add(bursts.toArray(new Bitmap[0]));

        // Every goal's net: rising, at its height, settling, over three looks.
        List<Bitmap> nets = new ArrayList<>();
        for (Object[] v : new Object[][]{{CompBug.Kind.FLOODLIGHT, 130L}, {CompBug.Kind.LED, BoardFx.MESH_PEAK}, {CompBug.Kind.SPLITKIT, 480L},
                {CompBug.Kind.TICKER, BoardFx.MESH_PEAK}}) {
            Bitmap b = look(bug, (CompBug.Kind) v[0], data(), row);
            Bitmap plain = b.copy(Bitmap.Config.ARGB_8888, true);
            float[] bar = CompBug.bar((CompBug.Kind) v[0], b.getHeight());
            Canvas cv = new Canvas(b);
            cv.save();
            cv.clipRect(0, bar[0], b.getWidth(), bar[1]);
            cv.translate(0, bar[0]);
            fx.netMesh(cv, b.getWidth(), bar[1] - bar[0], (Long) v[1]);
            cv.restore();
            assertTrue(differ(plain, b) > 0);
            nets.add(b);
        }
        names.add("Every goal's net (rising 130 ms, at its height, settling 480 ms)");
        rows.add(nets.toArray(new Bitmap[0]));

        // The red card swinging up, VAR's dot pulsing, the shootout's dots, half time's face.
        List<Bitmap> moments = new ArrayList<>();
        for (long t : new long[]{120, 300, 800, 1400}) {
            Bitmap b = Bitmap.createBitmap(70, 90, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawColor(0x33000000);
            fx.redCard(cv, 35, 80, 44, t);
            moments.add(b);
        }
        for (long t : new long[]{0, 250, 500}) {
            Bitmap b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawColor(0xFF0B1A2E);
            fx.varDot(cv, 24, 24, 12, t);
            assertTrue(inked(b) > 0);
            moments.add(b);
        }
        Bitmap pens = Bitmap.createBitmap(170, 52, Bitmap.Config.ARGB_8888);
        Canvas pc = new Canvas(pens);
        pc.drawColor(0xFF0B0E16);
        fx.pensDots(pc, 6, 4, 164, 48, Arrays.asList(true, false, true, true), Arrays.asList(true, true, false), 0xFFF5F6FA);
        moments.add(pens);
        Bitmap sudden = Bitmap.createBitmap(170, 52, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(sudden);
        sc.drawColor(0xFF0B0E16);
        fx.pensDots(sc, 6, 4, 164, 48, Arrays.asList(true, true, true, false, true, true, true), Arrays.asList(true, true, true, true, false, true, false),
                0xFFF5F6FA);
        moments.add(sudden);
        CompBug.Data ht = data();
        ht.clock = "HT";
        ht.htFace = true;
        moments.add(look(bug, CompBug.Kind.FLOODLIGHT, ht, 40));
        moments.add(look(bug, CompBug.Kind.SPLITKIT, ht, 40));
        names.add("Red card (120, 300, 800, 1400 ms) · VAR's dot (0, 250, 500 ms) · shootout dots (and sudden death) · half time's face");
        rows.add(moments.toArray(new Bitmap[0]));

        // The substitution card's cross-over: the arrows and the two lines at moments of the round.
        List<Bitmap> swaps = new ArrayList<>();
        Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        for (long t : new long[]{0, 900, 1800, 2300}) {
            float[] s = BoardFx.swapAt(t);
            Bitmap b = Bitmap.createBitmap(190, 64, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawColor(0xF20B1033);
            float em = 17f;
            BoardFx.swapArrow(cv, 18 + s[BoardFx.SW_IN_SHIFT] * em, 20, 12 * s[BoardFx.SW_IN_POP], true, s[BoardFx.SW_IN_ALPHA]);
            name.setColor(0xFFFFFFFF);
            name.setAlpha(Math.round(255 * s[BoardFx.SW_IN_ALPHA]));
            cv.drawText("Leo Brandt", 34 + s[BoardFx.SW_IN_SHIFT] * em, 26, name);
            BoardFx.swapArrow(cv, 18 + s[BoardFx.SW_OUT_SHIFT] * em, 46, 12 * s[BoardFx.SW_OUT_POP], false, s[BoardFx.SW_OUT_ALPHA]);
            name.setAlpha(Math.round(255 * s[BoardFx.SW_OUT_ALPHA]));
            cv.drawText("Theo Marsh", 34 + s[BoardFx.SW_OUT_SHIFT] * em, 52, name);
            swaps.add(b);
        }
        names.add("Substitution cross-over (0, 900, 1800, 2300 ms): on ▲ green, off ▼ red");
        rows.add(swaps.toArray(new Bitmap[0]));

        // The momentum wave being drawn in under a board, and drawn in full.
        List<int[]> values = new ArrayList<>();
        for (int m = 0; m <= 30; m++) values.add(new int[]{m, (int) Math.round(70 * Math.sin(m / 4.0) + (m > 20 ? 25 : -10))});
        List<Bitmap> waves = new ArrayList<>();
        for (long t : new long[]{700, 1400, 4000}) {
            Bitmap b = Bitmap.createBitmap(260, 46, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawColor(0xE60B1033);
            fx.wave(cv, 8, 4, 252, 42, values, BoardFx.waveDrawn(t), 0xFF1B458F, 0xFFC8102E, 0xFFFFFFFF, BoardFx.waveAlpha(t));
            assertTrue(inked(b) > 0);
            waves.add(b);
        }
        names.add("Momentum wave (700, 1400, 4000 ms)");
        rows.add(waves.toArray(new Bitmap[0]));

        // A goal's figure moving in on the boards' own looks (scoreMove), roll and flip.
        List<Bitmap> moves = new ArrayList<>();
        Paint score = new Paint(Paint.ANTI_ALIAS_FLAG);
        score.setTextSize(28);
        score.setTypeface(Typeface.DEFAULT_BOLD);
        score.setColor(0xFFF5F6FA);
        for (Object[] v : new Object[][]{{CompBug.MOVE_ROLL, 0.3f}, {CompBug.MOVE_ROLL, 0.6f}, {CompBug.MOVE_FLIP, 0.35f}, {CompBug.MOVE_FLIP, 0.7f}, {0, 1f}}) {
            Bitmap b = Bitmap.createBitmap(110, 50, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            cv.drawColor(0xFF142A78);
            fx.scoreMove(cv, score, "1–1", "2–1", 55, 25, (Integer) v[0], (Float) v[1]);
            assertTrue(inked(b) > 0);
            moves.add(b);
        }
        names.add("The boards' own score: rolling (0.3, 0.6), flipping (0.35, 0.7), still");
        rows.add(moves.toArray(new Bitmap[0]));
        write(sheet(names, rows), "lab-fx.png");
    }

    @Test
    public void theWeatherTouchDrawsEachSkyLightly() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        CompBug bug = new CompBug(ctx);
        BoardFx fx = new BoardFx();
        List<String> names = new ArrayList<>();
        List<Bitmap[]> rows = new ArrayList<>();
        List<Bitmap> row = new ArrayList<>();
        int n = 0;
        for (BoardFx.Sky s : BoardFx.Sky.values()) {
            Bitmap b = look(bug, CompBug.Kind.FLOODLIGHT, data(), 44);
            Bitmap plain = b.copy(Bitmap.Config.ARGB_8888, true);
            Canvas cv = new Canvas(b);
            long t = s == BoardFx.Sky.STORM ? 3150 : 3500;
            fx.weather(cv, b.getWidth(), b.getHeight(), s, t);
            assertTrue(s + " draws", differ(plain, b) > 0);
            row.add(b);
            if (++n % 3 == 0) {
                names.add("Weather: " + BoardFx.Sky.values()[n - 3] + ", " + BoardFx.Sky.values()[n - 2] + ", " + BoardFx.Sky.values()[n - 1]);
                rows.add(row.toArray(new Bitmap[0]));
                row = new ArrayList<>();
            }
        }
        write(sheet(names, rows), "lab-weather.png");
    }
}
