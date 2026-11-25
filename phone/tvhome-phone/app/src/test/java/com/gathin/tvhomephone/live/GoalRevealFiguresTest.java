package com.gathin.tvhomephone.live;

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
 * The Premier League's world feed on the phone's board (By competition's look for a Premier League game): its big score
 * figures and the lion stand out of its row, so while a club's goal animation covers the row nothing of the score is
 * drawn round it; the new score's figures come back as the last sweep passes.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class GoalRevealFiguresTest {
    private static OverlayBug.Look look(String score) {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "LIV";
        l.away = "EVE";
        l.homeColour = 0xFFC8102E;
        l.awayColour = 0xFF274488;
        l.clock = "67:12";
        l.live = true;
        l.score = score;
        return l;
    }

    private static int drawnIn(OverlayBug bug, int y0, int y1) {
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
        OverlayBug bug = new OverlayBug(c);
        bug.style(OverlayBug.Style.PL_WORLD);
        bug.set(look("1–1"));
        bug.names("Liverpool", "Everton");
        bug.teams("364", "368");
        bug.competition("eng.1", "English Premier League");
        bug.goalLook(GoalLooks.CLUBS);
        bug.shownAt(1f);
        int h = Math.round(60 * bug.tall()), w = bug.natural(h);
        bug.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        bug.layout(0, 0, w, h);
        int rowTop = Math.round(CompBug.bar(CompBug.Kind.PL, h)[0]);
        assertTrue(drawnIn(bug, 0, rowTop - 1) > 0);
        bug.goal(1);
        bug.set(look("2–1"));
        int covered = 0;
        for (long t = 0; t < GoalReveal.TOTAL_MS; t += 100) {
            bug.goalNow(1, t, "1–1");
            int above = drawnIn(bug, 0, rowTop - 1);
            if (GoalReveal.middleCovered(t)) {
                assertEquals("at " + t + " ms", 0, above);
                covered++;
            }
        }
        assertTrue(covered > 30);
        bug.goalNow(1, GoalReveal.TOTAL_MS + 100, "1–1");
        assertTrue(drawnIn(bug, 0, rowTop - 1) > 0);
        assertEquals("2–1", bug.look().score);
    }
}
