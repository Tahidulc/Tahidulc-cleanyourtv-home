package com.gathin.homefieldtv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.broadcast.AddedTime;
import com.gathin.broadcast.BoardSpec;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Game;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.Collections;

/**
 * Homefield TV's board back where the family put it after a pop-up (the strap folds into this channel's place from Move
 * and resize, at its size, whether the board is up, away or not laid out yet: never the strap page's own corner), and
 * added time in one red on every look (AddedTime.RED: Card's middle, the +N tab, the ADDED TIME pop-out), a custom
 * board's own when it sets one. A made-up match (Northfield v Riverside).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class BoardReturnTest {
    private ActivityController<TvActivity> tv;
    private Context app;

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("scoreboard", Context.MODE_PRIVATE).edit().clear().commit();
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        app.getSharedPreferences("scoreboard", Context.MODE_PRIVATE).edit().clear().commit();
        tv = null;
        Exec.inline = false;
    }

    private BoardLayer layer(boolean laidOut) {
        BoardLayer l = new BoardLayer(tv.get());
        if (laidOut) {
            l.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
            l.layout(0, 0, 1920, 1080);
        }
        return l;
    }

    private static Game game() {
        Game g = new Game();
        g.id = "770003";
        g.slug = "made.up";
        g.league = "Northern Shield";
        g.leagueName = "Northern Shield";
        g.home = "NOR";
        g.away = "RIV";
        g.homeName = "Northfield";
        g.awayName = "Riverside";
        g.homeColor = "0f8b8d";
        g.awayColor = "7a1f3d";
        g.state = Game.LIVE;
        g.kickoff = System.currentTimeMillis() - 70 * 60_000L;
        return g;
    }

    /** Moved right and down and made bigger in Move and resize, on the channel the preview plays ("preview"). */
    private BoardPrefs.Place handPlaced() {
        BoardPrefs.Place p = BoardPrefs.Place.standard();
        p.bx = 0.62f;
        p.by = 0.08f;
        p.bh = 0.06f;
        BoardPrefs.setPlace(app, "preview", p);
        return p;
    }

    private static void near(String what, float want, float got) {
        // the board's step against burn-in: two pixels at the most
        assertTrue(what + ": " + got + " for " + want, Math.abs(want - got) <= 2.01f);
    }

    @Test public void theStrapFoldsIntoTheHandPlacedSpotAndTheBoardComesBackThere() throws Exception {
        BoardPrefs.Place p = handPlaced();
        BoardLayer l = layer(true);
        long now = System.currentTimeMillis();
        l.preview(game(), Collections.singletonList(new BoardClock.Reading(now - 3_000L, 1, 0, Game.LIVE, false, 67 * 60, "68'")),
                now, 0, null, false, false);
        int[] place = BoardLayer.placeOf(p, 1920, 1080, l.bug.tall(), l.bug::natural);
        // The family's spot, not the standard one.
        assertEquals(Math.round(0.62f * 1920), place[0]);
        assertEquals(Math.round(0.08f * 1080), place[1]);
        assertEquals(Math.round(0.06f * 1080 * l.bug.tall()), place[3]);
        // The board itself is laid out there (inside its edge).
        FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) l.bug.getLayoutParams();
        assertEquals(place[0] - 2, b.leftMargin);
        assertEquals(place[1] - 2, b.topMargin);
        assertEquals(place[2] + 4, b.width);
        assertEquals(place[3] + 4, b.height);
        // The strap folds into the same place and size, on its 1920 x 1080 page.
        float[] at = l.boardAt();
        near("x", place[0], at[0]);
        near("y", place[1], at[1]);
        assertEquals(place[2], at[2], 0.01f);
        assertEquals(place[3], at[3], 0.01f);
        JSONObject cfg = BoardLayer.strapCfg(game(), BoardLayer.match(game()), "restart", new int[]{1, 0}, "Ayo Bennett", "67'",
                BoardBug.Style.CLASSIC, null, null, at);
        JSONObject board = cfg.getJSONObject("board");
        assertEquals(Math.round(at[0]), board.getInt("x"));
        assertEquals(Math.round(at[1]), board.getInt("y"));
        assertEquals(Math.round(at[2]), board.getInt("w"));
        assertEquals(Math.round(at[3]), board.getInt("h"));
        // Not the strap page's own corner (64, 46), where a board with no place would go.
        assertNotEquals(64, board.getInt("x"));
    }

    @Test public void theHandPlacedSpotEvenBeforeTheBoardIsLaidOut() {
        BoardPrefs.Place p = handPlaced();
        // The live screen just back, the layer not laid out yet: the place (the last set, with none for this channel) on the
        // strap's 1920 x 1080 picture all the same, never none.
        BoardLayer l = layer(false);
        float[] at = l.boardAt();
        assertNotNull(at);
        near("x", Math.round(p.bx * 1920), at[0]);
        near("y", Math.round(p.by * 1080), at[1]);
        assertEquals(Math.round(p.bh * 1080 * l.bug.tall()), at[3], 0.01f);
        // Back to the standard places: the standard spot.
        BoardPrefs.resetAllPlaces(app);
        BoardLayer fresh = layer(false);
        float[] std = fresh.boardAt();
        near("x", Math.round(BoardPrefs.Place.standard().bx * 1920), std[0]);
        near("y", Math.round(BoardPrefs.Place.standard().by * 1080), std[1]);
    }

    @Test public void placeOfKeepsTheBoardOnTheScreen() {
        BoardPrefs.Place p = BoardPrefs.Place.standard();
        p.bx = 0.97f;
        p.by = 0.98f;
        int[] r = BoardLayer.placeOf(p, 1920, 1080, 1f, h -> h * 7);
        assertTrue(r[0] + r[2] <= 1920);
        assertTrue(r[1] + r[3] <= 1080);
        assertArrayEquals(new int[]{86, 49, 343, 49}, BoardLayer.placeOf(BoardPrefs.Place.standard(), 1920, 1080, 1f, h -> h * 7));
    }

    // ---- added time's one red ----------------------------------------------------------------------------------------------

    private static BoardBug.Look addedTime() {
        BoardBug.Look l = new BoardBug.Look();
        l.home = "NOR";
        l.away = "RIV";
        l.score = "1–1";
        l.clock = "92:14";
        l.live = true;
        l.stoppage = true;
        l.added = "+4";
        l.homeColour = 0xFF0F8B8D;
        l.awayColour = 0xFF7A1F3D;
        return l;
    }

    /** How many of a picture's pixels are exactly this colour. */
    private static int count(Bitmap b, int colour) {
        int n = 0;
        int[] row = new int[b.getWidth()];
        for (int y = 0; y < b.getHeight(); y++) {
            b.getPixels(row, 0, b.getWidth(), 0, y, b.getWidth(), 1);
            for (int c : row) if (c == colour) n++;
        }
        return n;
    }

    private static Bitmap draw(Context c, BoardBug b, int h) {
        FrameLayout root = new FrameLayout(c);
        b.animAt(100_000L);
        b.shownAt(1f);
        int w = b.natural(h);
        root.addView(b, new FrameLayout.LayoutParams(w, h));
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.draw(new Canvas(out));
        return out;
    }

    @Test public void addedTimeIsOneRedOnCardAndBesideIt() {
        assertEquals(AddedTime.RED, BoardBug.CARD_RED);
        assertEquals(AddedTime.RED, BoardLayer.ADDED);
        BoardLayer layer = layer(true);
        for (BoardBug.Style st : BoardBug.Style.values()) {
            layer.bug.style(st);
            layer.bug.set(addedTime());
            assertEquals(st.name(), AddedTime.RED, layer.bug.stoppageRed());
            assertEquals(st.name(), AddedTime.RED, layer.bug.addedRed());
        }
        // The ADDED TIME pop-out in the board's own red.
        layer.bug.style(BoardBug.Style.CARD);
        assertEquals(AddedTime.RED, layer.addedDrop(4).colour);
        // Card in added time, drawn: its middle and its +4 tab the same red, nothing else red beside it.
        BoardBug card = new BoardBug(app);
        card.style(BoardBug.Style.CARD);
        card.names("Northfield", "Riverside");
        card.set(addedTime());
        Bitmap b = draw(app, card, Math.round(60 * card.tall()));
        assertTrue("Card's red is AddedTime.RED", count(b, AddedTime.RED) > b.getWidth() * b.getHeight() / 20);
        for (int old : new int[]{0xFFC81E34, 0xFF8E0B1D, 0xFFE3061B, 0xFFFF5A6A}) assertEquals(0, count(b, old));
    }

    @Test public void aCustomBoardPaintsItsOwnStoppageRedEverywhere() {
        BoardSpec s = BoardSpec.of(BoardSpec.Base.CARD);
        s.name = "Floodlit";
        s.stoppage = 0xFFB0122A;
        s.added = 0xFFB0122A;
        BoardLayer layer = layer(true);
        layer.restyle(s);
        layer.bug.set(addedTime());
        assertEquals(0xFFB0122A, layer.bug.stoppageRed());
        assertEquals(0xFFB0122A, layer.addedDrop(3).colour);
        Bitmap b = draw(app, customCard(s), Math.round(60 * layer.bug.tall()));
        assertTrue(count(b, 0xFFB0122A) > b.getWidth() * b.getHeight() / 20);
        assertEquals(0, count(b, AddedTime.RED));
        // Unset, a custom board's stoppage red is AddedTime.RED.
        BoardSpec plain = BoardSpec.of(BoardSpec.Base.CARD);
        assertEquals(AddedTime.RED, plain.stoppage);
        layer.restyle(plain);
        assertEquals(AddedTime.RED, layer.bug.stoppageRed());
    }

    private BoardBug customCard(BoardSpec s) {
        BoardBug b = new BoardBug(app);
        b.custom(s);
        b.names("Northfield", "Riverside");
        b.set(addedTime());
        return b;
    }
}
