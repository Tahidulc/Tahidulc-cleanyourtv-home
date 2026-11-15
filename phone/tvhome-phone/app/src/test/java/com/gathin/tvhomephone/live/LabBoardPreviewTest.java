package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.BoardFx;
import com.gathin.tvhomephone.sports.Game;

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
 * 5.02: the Scoreboard Lab on the phone's board drawn without a phone (made-up clubs WHI v RIV): the five new looks at
 * rest; a goal in the Lab's flip and in its burst (and the net after each) on a board of its own and on a rolling one;
 * half time's face, a shootout beside the clock and the weather touch; the red card swinging up, VAR's pulse and a
 * substitution's players crossing in on their pop-outs, and the momentum wave's stat; the five Lab logos. Checked for
 * what each moment must show; the pictures are written only when PREVIEW_OUT names a folder for them.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class LabBoardPreviewTest {
    private static final int H = 60, GREEN = 0xFF2E5E3A;
    private static final OverlayBug.Style[] LAB = {OverlayBug.Style.FLOODLIGHT, OverlayBug.Style.SPLITKIT, OverlayBug.Style.TICKER,
            OverlayBug.Style.DUO, OverlayBug.Style.LED};

    /** A made-up game's id whose pick is so (the same every time: BoardFx.pick is by the id). */
    private interface Wants {
        boolean ok(String id);
    }

    private static String idWhere(Wants w) {
        for (int i = 0; i < 2000; i++) {
            String id = "lab-preview-" + i;
            if (w.ok(id)) return id;
        }
        throw new AssertionError("no game id found");
    }

    private static OverlayBug.Look look(String score, String clock) {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "WHI";
        l.away = "RIV";
        l.score = score;
        l.clock = clock;
        l.live = !"HT".equals(clock);
        l.tag = "LIVE";
        l.homeColour = 0xFF1B458F;
        l.awayColour = 0xFFC8102E;
        return l;
    }

    /** A made-up club's crest: a disc in its colour, a white ring and its letter. */
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

    private static final Bitmap HOME_CREST = crest(0xFF1B458F, "W"), AWAY_CREST = crest(0xFFC8102E, "R");

    /** A board in this look for game id showing l (with the clubs' crests, as a real game has), at its own width, height H. */
    private static OverlayBug board(Context ctx, FrameLayout[] rootOut, OverlayBug.Style st, String id, OverlayBug.Look l) {
        return board(ctx, rootOut, st, id, l, true);
    }

    private static OverlayBug board(Context ctx, FrameLayout[] rootOut, OverlayBug.Style st, String id, OverlayBug.Look l, boolean crests) {
        OverlayBug b = new OverlayBug(ctx);
        b.style(st);
        FrameLayout root = new FrameLayout(ctx);
        root.addView(b, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, H));
        if (crests) b.crests(HOME_CREST, AWAY_CREST, null);
        b.game(id);
        b.set(l);
        b.animAt(100_000L);
        b.shownAt(1f);
        rootOut[0] = root;
        return b;
    }

    private static Bitmap draw(FrameLayout root, View v, int h) {
        root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, root.getMeasuredWidth(), h);
        Bitmap one = Bitmap.createBitmap(Math.max(1, v.getWidth()), h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(one));
        return one;
    }

    /** How many pixels differ much between two pictures (over the narrower one's width). */
    private static int differ(Bitmap a, Bitmap b) {
        int n = 0, w = Math.min(a.getWidth(), b.getWidth()), h = Math.min(a.getHeight(), b.getHeight());
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int p = a.getPixel(x, y), q = b.getPixel(x, y);
                int d = Math.abs(Color.red(p) - Color.red(q)) + Math.abs(Color.green(p) - Color.green(q)) + Math.abs(Color.blue(p) - Color.blue(q));
                if (d > 60) n++;
            }
        }
        return n;
    }

    /** Pictures stacked into one sheet over a pitch-green ground, each with its label. */
    private static final class Sheet {
        final List<Bitmap> rows = new ArrayList<>();
        final List<String> names = new ArrayList<>();

        void add(String name, Bitmap b) {
            names.add(name);
            rows.add(b);
        }

        Bitmap draw() {
            int w = 0, h = 20;
            for (Bitmap b : rows) {
                w = Math.max(w, b.getWidth() + 260);
                h += b.getHeight() + 24;
            }
            Bitmap all = Bitmap.createBitmap(Math.max(w, 400), h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(all);
            cv.drawColor(GREEN);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(15);
            p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            int y = 20;
            for (int i = 0; i < rows.size(); i++) {
                Bitmap b = rows.get(i);
                cv.drawText(names.get(i), 16, y + b.getHeight() / 2f + 5, p);
                cv.drawBitmap(b, 240, y, null);
                y += b.getHeight() + 24;
            }
            return all;
        }
    }

    private static void save(String out, String name, Bitmap b) throws Exception {
        if (out.isEmpty()) return;
        new File(out).mkdirs();
        try (FileOutputStream f = new FileOutputStream(new File(out, name))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    // ---- the board -----------------------------------------------------------------------------------------------------

    @Test public void theLooksAndTheirMoments() throws Exception {
        String out = System.getProperty("preview.out", "");
        Context ctx = RuntimeEnvironment.getApplication();
        FrameLayout[] root = new FrameLayout[1];
        String band = idWhere(id -> BoardFx.goalSet(id) == BoardFx.GOAL_BAND);
        String flip = idWhere(id -> BoardFx.goalSet(id) == BoardFx.GOAL_FLIP);
        String burst = idWhere(id -> BoardFx.goalSet(id) == BoardFx.GOAL_BURST);

        // The five looks at rest.
        Sheet looks = new Sheet();
        for (OverlayBug.Style st : LAB) {
            OverlayBug b = board(ctx, root, st, band, look("2–1", "67:23"));
            Bitmap rest = draw(root[0], b, Math.round(H * st.tall));
            assertTrue(st + " drawn", rest.getWidth() > H * 2);
            looks.add(st.label, rest);
        }
        save(out, "lab-looks.png", looks.draw());

        // A goal in the flip and in the burst, then the net after each: on a board of its own and on a rolling one.
        Sheet goals = new Sheet();
        OverlayBug.Style[] on = {OverlayBug.Style.CLASSIC, OverlayBug.Style.CRESTS, OverlayBug.Style.STACKED, OverlayBug.Style.FLOODLIGHT,
                OverlayBug.Style.DUO};
        for (OverlayBug.Style st : on) {
            int h = Math.round(H * st.tall);
            OverlayBug rest = board(ctx, root, st, flip, look("2–1", "67:23"));
            Bitmap still = draw(root[0], rest, h);
            long[] at = {300, (BoardFx.FLIP_FROM + BoardFx.FLIP_TO) / 2, BoardFx.MESH_AFTER_FLIP + BoardFx.MESH_MS / 2};
            String[] what = {"flip: wash and shine", "flip: the digit turning", "flip: the net"};
            for (int i = 0; i < at.length; i++) {
                OverlayBug b = board(ctx, root, st, flip, look("2–1", "67:23"));
                b.goalNow(1, at[i], "1–1");
                Bitmap g = draw(root[0], b, h);
                assertTrue(st + " " + what[i] + " shows", differ(still, g) > 20);
                goals.add(st.label + ", " + what[i], g);
            }
            long[] bt = {250, (BoardFx.ROLL_FROM + BoardFx.ROLL_TO) / 2, BoardFx.MESH_AFTER_BURST + BoardFx.MESH_MS / 2};
            String[] bw = {"burst: the crest's rings", "burst: the score rolling", "burst: the net"};
            for (int i = 0; i < bt.length; i++) {
                OverlayBug b = board(ctx, root, st, burst, look("1–2", "67:23"));
                b.goalNow(2, bt[i], "1–1");
                Bitmap g = draw(root[0], b, h);
                assertTrue(st + " " + bw[i] + " shows", differ(still, g) > 20);
                goals.add(st.label + ", " + bw[i], g);
            }
            // today's band, its net after it
            OverlayBug b = board(ctx, root, st, band, look("2–1", "67:23"));
            b.goalNow(1, OverlayBug.RIPPLE_AT + 300, "1–1");
            goals.add(st.label + ", band: the net", draw(root[0], b, h));
        }
        // No crests yet (a look without them): the burst over the scorers' part, fading once it has popped.
        for (long t : new long[]{250, 1000, 1600}) {
            OverlayBug b = board(ctx, root, OverlayBug.Style.CLASSIC, burst, look("1–2", "67:23"), false);
            b.goalNow(2, t, "1–1");
            goals.add("Classic, no crests, burst at " + t + " ms", draw(root[0], b, H));
        }
        assertTrue(OverlayBug.burstFade(BoardFx.POP_MS) == 1f && OverlayBug.burstFade(BoardFx.BURST_MS) == 0f);
        save(out, "lab-goals.png", goals.draw());

        // Half time's face, a shootout beside the clock, the weather touch.
        Sheet moments = new Sheet();
        String ht = idWhere(BoardFx::htFace), pens = idWhere(BoardFx::pensInClock);
        for (OverlayBug.Style st : new OverlayBug.Style[]{OverlayBug.Style.CLASSIC, OverlayBug.Style.FLOODLIGHT, OverlayBug.Style.TICKER}) {
            int h = Math.round(H * st.tall);
            OverlayBug half = board(ctx, root, st, ht, look("1–0", "HT"));
            half.momentsNow(OverlayBug.HT_MS, -1, false);
            moments.add(st.label + ", half time's face", draw(root[0], half, h));
            OverlayBug plain = board(ctx, root, st, pens, look("1–1", "120:00"));
            int was = draw(root[0], plain, h).getWidth();
            OverlayBug shoot = board(ctx, root, st, pens, look("1–1", "120:00"));
            assertTrue(shoot.pensInClock());
            shoot.pens(Arrays.asList(true, false, true, true), Arrays.asList(true, true, false));
            Bitmap ps = draw(root[0], shoot, h);
            assertTrue(st + ": the shootout's tab beside the clock", ps.getWidth() > was + h / 2);
            moments.add(st.label + ", the shootout beside the clock", ps);
        }
        String[] skies = {"RAIN", "SNOW", "CLEAR_NIGHT", "STORM"};
        OverlayBug.Style[] wxOn = {OverlayBug.Style.CLASSIC, OverlayBug.Style.SPLITKIT, OverlayBug.Style.LED, OverlayBug.Style.DUO};
        for (int i = 0; i < skies.length; i++) {
            OverlayBug.Style st = wxOn[i];
            int h = Math.round(H * st.tall);
            OverlayBug still = board(ctx, root, st, band, look("0–0", "23:10"));
            Bitmap s0 = draw(root[0], still, h);
            OverlayBug b = board(ctx, root, st, band, look("0–0", "23:10"));
            b.weatherNow(BoardFx.Sky.valueOf(skies[i]), 2500);
            Bitmap wx = draw(root[0], b, h);
            assertTrue(st + " " + skies[i] + " shows", differ(s0, wx) > 5);
            moments.add(st.label + ", weather: " + skies[i].toLowerCase(java.util.Locale.ROOT).replace('_', ' '), wx);
        }
        save(out, "lab-moments.png", moments.draw());
    }

    // ---- the pop-outs ---------------------------------------------------------------------------------------------------

    private static Game game() {
        Game g = new Game();
        g.id = "lab-preview-drops";
        g.home = "WHI";
        g.away = "RIV";
        g.homeName = "Whitebrook";
        g.awayName = "Riverside";
        g.homeColor = "1b458f";
        g.awayColor = "c8102e";
        return g;
    }

    /** One pop-out drawn at its moment t (-1: at rest), on page pg, beside a board H high. */
    private static Bitmap drop(Context ctx, ScoreBoard.Drop d, int pg, long t) {
        OverlayBug.StripLook look = new OverlayBug.StripLook();
        BoardDrop strip = new BoardDrop(ctx);
        int up = d.subs != null ? Math.round(H * 0.55f) : 0;
        strip.place(look, H * ScoreBoard.TEXT, 0, false, up);
        int w = strip.widthFor(d, H, 1200);
        FrameLayout root = new FrameLayout(ctx);
        root.addView(strip, new FrameLayout.LayoutParams(w, H + up));
        strip.showNow(d, pg, 0f, 1f);
        strip.momentNow(t);
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H + up, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, H + up);
        Bitmap one = Bitmap.createBitmap(w, H + up, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(one));
        return one;
    }

    /** How many strongly red pixels (the red card's own red) a picture has. */
    private static int reds(Bitmap b) {
        int n = 0;
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) > 200 && Color.red(p) > 190 && Color.green(p) < 50 && Color.blue(p) < 60) n++;
            }
        }
        return n;
    }

    @Test public void theRedCardVarAndTheSwap() throws Exception {
        String out = System.getProperty("preview.out", "");
        Context ctx = RuntimeEnvironment.getApplication();
        Game g = game();
        Sheet sheet = new Sheet();

        // The red card swinging up beside its chip, then held there.
        ScoreBoard.Drop red = new ScoreBoard.Drop();
        red.chip = "RED CARD";
        red.colour = ScoreBoard.colour("red");
        red.title = "Theo Marsh";
        red.line = "Second yellow · 71'";
        red.side = 2;
        red.homeColour = OverlayBug.colour(g.homeColor, g.homeAlt);
        red.awayColour = OverlayBug.colour(g.awayColor, g.awayAlt);
        assertTrue(BoardDrop.redCard(red));
        Bitmap redUp = drop(ctx, red, 0, BoardFx.RED_UP / 2), redHeld = drop(ctx, red, 0, -1);
        sheet.add("red card: swinging up", redUp);
        sheet.add("red card: held", redHeld);
        ScoreBoard.Drop plain = new ScoreBoard.Drop();
        plain.chip = "YELLOW CARD";
        plain.colour = 0xFFFFD60A;
        plain.title = red.title;
        plain.line = red.line;
        Bitmap noCard = drop(ctx, plain, 0, -1);
        // its card's own red beside the chip (a yellow card's alert has none)
        assertTrue("red card's card: " + reds(redHeld) + " v " + reds(noCard), reds(redHeld) > reds(noCard) + 40);

        // VAR checking: its dot pulsing in the chip, the three dots after the words.
        ScoreBoard.Drop var = ScoreBoard.varDrop(g, VarExplained.checkTitle("penalty"), VarExplained.checkLine(VarExplained.HANDBALL, "Sam Okafor"));
        var.varCheck = true;
        sheet.add("VAR checking (its pulse)", drop(ctx, var, 0, 200));

        // A substitution: off, then on, its players crossing in as each page comes, then at rest.
        ScoreBoard.Drop sub = ScoreBoard.subDrop(g, new String[]{"Substitution", "71", "1", "Alex Player", "", "Ben Example", ""});
        assertNotNull(sub.subs);
        Bitmap offIn = drop(ctx, sub, 0, 120), offRest = drop(ctx, sub, 0, -1), onIn = drop(ctx, sub, 1, 300), onRest = drop(ctx, sub, 1, -1);
        assertTrue("the off page's players cross in", differ(offIn, offRest) > 10);
        assertTrue("the on page's players cross in", differ(onIn, onRest) > 10);
        sheet.add("sub: off, crossing in", offIn);
        sheet.add("sub: off, at rest", offRest);
        sheet.add("sub: on, crossing in", onIn);
        sheet.add("sub: on, at rest", onRest);
        // three at once from one club
        ScoreBoard.Drop three = ScoreBoard.subDrop(g, Arrays.asList(
                new String[]{"Substitution", "78", "2", "Carl Sample", "", "Dan van Test", ""},
                new String[]{"Substitution", "78", "2", "Eli Demo", "", "Finn Trial", ""}));
        sheet.add("subs: two on, at rest", drop(ctx, three, 1, -1));

        // The momentum wave's stat, drawn whole.
        List<int[]> mom = new ArrayList<>();
        int[] vals = {10, 25, 40, 18, -5, -30, -45, -20, 5, 30, 55, 62, 48, 35, 20, 12};
        for (int i = 0; i < vals.length; i++) mom.add(new int[]{20 + i, vals[i]});
        String[] wave = BoardLab.wave(mom, 35, "WHI", "RIV");
        assertNotNull(wave);
        BoardStats stats = new BoardStats(ctx);
        OverlayBug.StripLook look = new OverlayBug.StripLook();
        stats.place(look, H * ScoreBoard.TEXT, 0, false, 0);
        stats.colours(0xFF1B458F, 0xFFC8102E);
        List<String[]> list = java.util.Collections.singletonList(wave);
        int w = stats.widthFor(list, H, 1200);
        FrameLayout sroot = new FrameLayout(ctx);
        sroot.addView(stats, new FrameLayout.LayoutParams(w, H));
        stats.showNow(list, 0, 1f);
        sroot.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        sroot.layout(0, 0, w, H);
        Bitmap wb = Bitmap.createBitmap(w, H, Bitmap.Config.ARGB_8888);
        sroot.draw(new Canvas(wb));
        sheet.add("momentum wave", wb);
        save(out, "lab-drops.png", sheet.draw());
    }

    // ---- the logos -----------------------------------------------------------------------------------------------------

    @Test public void theFiveLogos() throws Exception {
        String out = System.getProperty("preview.out", "");
        Context ctx = RuntimeEnvironment.getApplication();
        Sheet sheet = new Sheet();
        int[][] boxes = {{210, 60}, {150, 60}, {120, 70}};
        for (LogoCover.Look l : LogoCover.Look.values()) {
            if (l.lab() == null) continue;
            for (int[] box : boxes) {
                for (boolean nameOnly : new boolean[]{false, true}) {
                    LogoCover c = new LogoCover(ctx);
                    c.look(l);
                    c.tag("UHD");
                    c.nameOnly(nameOnly);
                    FrameLayout root = new FrameLayout(ctx);
                    root.addView(c, new FrameLayout.LayoutParams(box[0], box[1]));
                    root.measure(View.MeasureSpec.makeMeasureSpec(box[0], View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(box[1], View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, box[0], box[1]);
                    Bitmap b = Bitmap.createBitmap(box[0], box[1], Bitmap.Config.ARGB_8888);
                    c.draw(new Canvas(b));
                    // something drawn in the box
                    int seen = 0;
                    for (int y = 0; y < b.getHeight(); y += 3) for (int x = 0; x < b.getWidth(); x += 3) if (Color.alpha(b.getPixel(x, y)) > 200) seen++;
                    assertTrue(l + " drawn at " + box[0] + "x" + box[1], seen > 20);
                    if (box == boxes[0] || nameOnly) continue;
                    sheet.add(l.label + " " + box[0] + "x" + box[1] + (nameOnly ? ", name only" : ""), b);
                }
            }
            LogoCover c = new LogoCover(ctx);
            c.look(l);
            c.tag("UHD");
            FrameLayout root = new FrameLayout(ctx);
            root.addView(c, new FrameLayout.LayoutParams(boxes[0][0], boxes[0][1]));
            root.measure(View.MeasureSpec.makeMeasureSpec(boxes[0][0], View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(boxes[0][1], View.MeasureSpec.EXACTLY));
            root.layout(0, 0, boxes[0][0], boxes[0][1]);
            Bitmap b = Bitmap.createBitmap(boxes[0][0], boxes[0][1], Bitmap.Config.ARGB_8888);
            c.draw(new Canvas(b));
            sheet.add(l.label, b);
            LogoCover n = new LogoCover(ctx);
            n.look(l);
            n.tag("");
            n.nameOnly(true);
            root = new FrameLayout(ctx);
            root.addView(n, new FrameLayout.LayoutParams(boxes[0][0], boxes[0][1]));
            root.measure(View.MeasureSpec.makeMeasureSpec(boxes[0][0], View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(boxes[0][1], View.MeasureSpec.EXACTLY));
            root.layout(0, 0, boxes[0][0], boxes[0][1]);
            Bitmap nb = Bitmap.createBitmap(boxes[0][0], boxes[0][1], Bitmap.Config.ARGB_8888);
            n.draw(new Canvas(nb));
            sheet.add(l.label + ", name only, no tag", nb);
        }
        save(out, "lab-logos.png", sheet.draw());
    }
}
