package com.gathin.tvhomephone.live;

import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.SportBoard;
import com.gathin.broadcast.SportBug;
import com.gathin.broadcast.SportFeeds;

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
 * Tennis, boxing and UFC boards drawn in every look without a phone (made-up players and fighters, stand-in flags and
 * photos), each sport's looks one under another in a PNG in the folder PREVIEW_OUT names. Skipped unless it is set;
 * nothing here goes into the app.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class SportBoardPreviewTest {
    private static final float U = 44f;

    private static List<Object[]> looks() {
        List<Object[]> out = new ArrayList<>();
        for (String s : new String[]{"CLASSIC", "GLASS", "LIGHT", "CLUB", "MINIMAL", "CRESTS", "STACKED", "CARD", "FROSTED", "COMPETITION"}) {
            out.add(new Object[]{s, null});
        }
        for (CompBug.Kind k : CompBug.Kind.values()) out.add(new Object[]{k.name(), k});
        return out;
    }

    private static Bitmap flag(int a, int b, int c) {
        Bitmap f = Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(f);
        Paint p = new Paint();
        p.setColor(a);
        cv.drawRect(0, 0, 30, 7, p);
        p.setColor(b);
        cv.drawRect(0, 7, 30, 14, p);
        p.setColor(c);
        cv.drawRect(0, 14, 30, 20, p);
        return f;
    }

    private static Bitmap photo(int shirt) {
        Bitmap f = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF8A96A8);
        cv.drawRect(0, 0, 60, 60, p);
        p.setColor(0xFFD9A07A);
        cv.drawCircle(30, 24, 13, p);
        p.setColor(shirt);
        cv.drawOval(8, 40, 52, 76, p);
        return f;
    }

    private static Bitmap word(String w) {
        Bitmap f = Bitmap.createBitmap(90, 40, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTextSize(34);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        cv.drawText(w, 2, 34, p);
        return f;
    }

    private static SportBoard tennis(boolean live) {
        SportBoard b = new SportBoard();
        b.sport = SportBoard.TENNIS;
        b.state = live ? "in" : "post";
        b.event = "WTA Finals";
        b.stage = "Semifinal";
        b.division = "Women's Singles";
        b.a.name = "Mira Kovalenko";
        b.a.shortName = "Kovalenko";
        b.a.country = "UKR";
        b.a.colour = SportFeeds.colourOf("UKR");
        b.a.seed = "2";
        b.a.flag = flag(0xFF0057B7, 0xFF0057B7, 0xFFFFD700);
        b.b.name = "Lena Hartwell";
        b.b.shortName = "Hartwell";
        b.b.country = "GBR";
        b.b.colour = SportFeeds.colourOf("GBR");
        b.b.seed = "6";
        b.b.flag = flag(0xFF1D3A8A, 0xFFFFFFFF, 0xFFC8102E);
        if (live) {
            b.a.games = new int[]{6, 4, 3};
            b.b.games = new int[]{7, 6, 2};
            b.a.tiebreak = new int[]{-1, -1, -1};
            b.b.tiebreak = new int[]{-1, -1, -1};
            b.a.games[0] = 6;
            b.b.games[0] = 4;
            b.a.games[1] = 4;
            b.b.games[1] = 6;
            b.a.points = "40";
            b.b.points = "15";
            b.a.serving = true;
        } else {
            b.a.games = new int[]{7, 6};
            b.b.games = new int[]{6, 3};
            b.a.tiebreak = new int[]{-1, -1};
            b.b.tiebreak = new int[]{4, -1};
            b.a.winner = true;
        }
        return b;
    }

    private static SportBoard fight(int sport, String state) {
        SportBoard b = sport == SportBoard.BOXING ? SportFeeds.boxing("Live Boxing: Tom Archer v Rio Mendes - Heavyweight", 0L) : new SportBoard();
        b.sport = sport;
        b.state = state;
        if (sport == SportBoard.UFC) {
            b.event = "UFC 900";
            b.stage = "Lightweight";
            b.mainEvent = true;
            b.rounds = 5;
            b.a.name = "Dario Vance";
            b.a.shortName = "Vance";
            b.b.name = "Kenji Morrow";
            b.b.shortName = "Morrow";
            b.a.colour = SportBoard.RED_CORNER;
            b.b.colour = SportBoard.BLUE_CORNER;
            b.a.country = "USA";
            b.b.country = "JPN";
            b.a.record = "24-3-0";
            b.b.record = "19-5-1";
            b.a.photo = photo(0xFF7A1020);
            b.b.photo = photo(0xFF1A3A80);
        } else {
            b.a.country = "GBR";
            b.b.country = "BRA";
            b.a.flag = flag(0xFF1D3A8A, 0xFFFFFFFF, 0xFFC8102E);
            b.b.flag = flag(0xFF009C3B, 0xFFFEDF00, 0xFF009C3B);
        }
        if ("in".equals(state)) {
            b.round = sport == SportBoard.UFC ? 3 : 7;
            b.secsIn = 79;
        } else if ("post".equals(state)) {
            b.round = 3;
            b.secsIn = 134;
            b.method = sport == SportBoard.UFC ? "Submission" : "KO";
            b.finish = "R3 2:14";
            b.a.winner = true;
        }
        return b;
    }

    private static void sheet(SportBug bug, String file, SportBoard[] boards, Bitmap logo, boolean band) throws Exception {
        List<Object[]> looks = looks();
        int width = 0, height = 0;
        List<float[]> sizes = new ArrayList<>();
        for (Object[] l : looks) {
            SportBug.Skin s = SportBug.skin((String) l[0], (CompBug.Kind) l[1]);
            float rowW = 0, rowH = 0;
            for (SportBoard b : boards) {
                float[] z = bug.size(s, b, U);
                rowW += z[0] + 30;
                rowH = Math.max(rowH, z[1]);
            }
            sizes.add(new float[]{rowW, rowH});
            width = Math.max(width, (int) rowW + 220);
            height += (int) rowH + 18;
        }
        Bitmap out = Bitmap.createBitmap(width + 20, height + 20, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(0xFF5B6B57);
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(Color.WHITE);
        label.setTextSize(16);
        float y = 10;
        for (int i = 0; i < looks.size(); i++) {
            Object[] l = looks.get(i);
            SportBug.Skin s = SportBug.skin((String) l[0], (CompBug.Kind) l[1]);
            cv.drawText((String) l[0], 10, y + 30, label);
            float x = 210;
            for (SportBoard b : boards) {
                float[] z = bug.size(s, b, U);
                cv.save();
                cv.translate(x, y);
                bug.draw(cv, s, b, logo, z[0], z[1], U, true);
                if (band) bug.band(cv, s, b, b.fight() ? "ROUND 3" : "SET KOVALENKO", b.a.colour, z[0], U, 0.5f);
                cv.restore();
                x += z[0] + 30;
            }
            y += sizes.get(i)[1] + 18;
        }
        try (FileOutputStream f = new FileOutputStream(new File(System.getenv("PREVIEW_OUT"), file))) {
            out.compress(Bitmap.CompressFormat.PNG, 100, f);
        }
    }

    @Test
    public void everyLookForEverySport() throws Exception {
        String dir = System.getenv("PREVIEW_OUT");
        assumeTrue(dir != null && !dir.isEmpty());
        new File(dir).mkdirs();
        SportBug bug = new SportBug(RuntimeEnvironment.getApplication(), null);
        sheet(bug, "sport-tennis.png", new SportBoard[]{tennis(true), tennis(false)}, null, false);
        sheet(bug, "sport-ufc.png", new SportBoard[]{fight(SportBoard.UFC, "in"), fight(SportBoard.UFC, "post")}, word("UFC"), false);
        sheet(bug, "sport-boxing.png", new SportBoard[]{fight(SportBoard.BOXING, "in"), fight(SportBoard.BOXING, "pre")}, null, false);
        sheet(bug, "sport-moments.png", new SportBoard[]{tennis(true), fight(SportBoard.UFC, "in")}, word("UFC"), true);
    }
}
