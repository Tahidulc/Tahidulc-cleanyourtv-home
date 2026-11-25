package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import com.gathin.broadcast.CompBug;
import com.gathin.broadcast.GoalLooks;
import com.gathin.broadcast.GoalReveal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * The Premier League's world feed, Homefield TV's own look for a Premier League game (By competition): its big score
 * figures and the lion stand out of its row, so while a club's goal animation covers the row nothing of the score is
 * drawn round it; the new score's figures come back as the last sweep passes.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class GoalRevealFiguresTest {
    private static final int EDGE = 2, ROW = 60;

    private static BoardBug.Look look(String score) {
        BoardBug.Look l = new BoardBug.Look();
        l.home = "LIV";
        l.away = "EVE";
        l.homeColour = 0xFFC8102E;
        l.awayColour = 0xFF274488;
        l.clock = "67:12";
        l.live = true;
        l.score = score;
        return l;
    }

    /** Pixels drawn (more than faintly) in rows [y0, y1) of a board drawn on nothing. */
    private static int drawnIn(BoardBug bug, int y0, int y1) {
        Bitmap b = Bitmap.createBitmap(bug.getWidth(), bug.getHeight(), Bitmap.Config.ARGB_8888);
        bug.draw(new Canvas(b));
        int n = 0;
        for (int y = Math.max(0, y0); y < Math.min(b.getHeight(), y1); y++) {
            for (int x = 0; x < b.getWidth(); x++) if ((b.getPixel(x, y) >>> 24) > 40) n++;
        }
        return n;
    }

    @Test public void theWorldFeedsScoreNeverStandsOutOfTheAnimationsRow() {
        Context c = RuntimeEnvironment.getApplication();
        BoardBug bug = new BoardBug(c);
        bug.style(BoardBug.Style.PL_WORLD);
        bug.inset(EDGE);
        bug.set(look("1–1"));
        bug.names("Liverpool", "Everton");
        bug.teams("364", "368");
        bug.competition("eng.1", "English Premier League");
        bug.goalLook(GoalLooks.CLUBS);
        int h = Math.round(ROW * bug.tall()), w = bug.natural(h) + 2 * EDGE;
        bug.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h + 2 * EDGE, View.MeasureSpec.EXACTLY));
        bug.layout(0, 0, w, h + 2 * EDGE);
        int rowTop = EDGE + Math.round(CompBug.bar(CompBug.Kind.PL, h)[0]);
        // At rest the figures (and the lion) stand above the row.
        assertTrue(drawnIn(bug, 0, rowTop - 1) > 0);
        // A goal: from the first sweep over the middle to the last sweep past it, nothing of the score round the row.
        bug.goal(1);
        bug.set(look("2–1"));
        int covered = 0;
        for (long t = 0; t < GoalReveal.TOTAL_MS; t += 100) {
            bug.goalNow(1, t);
            int above = drawnIn(bug, 0, rowTop - 1);
            if (GoalReveal.middleCovered(t)) {
                assertEquals("at " + t + " ms", 0, above);
                covered++;
            }
        }
        assertTrue(covered > 30);
        // After it, the new score's figures stand out of the row again.
        bug.goalNow(1, GoalReveal.TOTAL_MS + 100);
        assertTrue(drawnIn(bug, 0, rowTop - 1) > 0);
        assertEquals("2–1", bug.look().score);
    }
}
