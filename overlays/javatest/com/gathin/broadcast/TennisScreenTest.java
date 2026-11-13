package com.gathin.broadcast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The channel's own tennis scoreboard read off the picture: its games and points, as the picture has them, at once. */
public class TennisScreenTest {
    /** Two rows 24 high, 36 apart: the first at y 100, the second at y 136. */
    private static final float A = 100f, B = 136f, H = 24f;

    private static TennisScreen.Word w(String s, float l, float r, float top) {
        return new TennisScreen.Word(s, l, top, r, top + H);
    }

    /** A smaller word (a seed), level with a row. */
    private static TennisScreen.Word small(String s, float l, float r, float top) {
        return new TennisScreen.Word(s, l, top + 4, r, top + 20);
    }

    private static SportBoard match(int[] a, int[] b) {
        SportBoard m = new SportBoard();
        m.sport = SportBoard.TENNIS;
        m.state = "in";
        m.id = "1";
        m.a.name = "Aryna Sabalenka";
        m.a.shortName = "Sabalenka";
        m.b.name = "Elina Svitolina";
        m.b.shortName = "Svitolina";
        m.a.games = a;
        m.b.games = b;
        m.a.tiebreak = new int[a.length];
        m.b.tiebreak = new int[b.length];
        Arrays.fill(m.a.tiebreak, -1);
        Arrays.fill(m.b.tiebreak, -1);
        return m;
    }

    /** The board in the family's picture: A. SABALENKA [2] 3 5 15 / E. SVITOLINA [6] 6 1 30. */
    private static List<TennisScreen.Word> picture(String[] rowA, String[] rowB) {
        List<TennisScreen.Word> out = new ArrayList<>();
        out.add(w("A.", 40, 56, A));
        out.add(w("SABALENKA", 62, 220, A));
        out.add(small("[2]", 226, 244, A));
        out.add(w("E.", 40, 56, B));
        out.add(w("SVITOLINA", 62, 214, B));
        out.add(small("[6]", 220, 238, B));
        float[] at = {300, 336, 372, 408, 444};
        for (int i = 0; i < rowA.length; i++) if (!rowA[i].isEmpty()) out.add(w(rowA[i], at[i], at[i] + (rowA[i].length() > 1 ? 24 : 13), A));
        for (int i = 0; i < rowB.length; i++) if (!rowB[i].isEmpty()) out.add(w(rowB[i], at[i], at[i] + (rowB[i].length() > 1 ? 24 : 13), B));
        // Elsewhere on the picture: the channel's clock, a speed.
        out.add(w("21:47", 1100, 1180, 20));
        out.add(w("189", 900, 940, 600));
        return out;
    }

    @Test
    public void theFamilysPictureIsReadPointsAndAll() {
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"}), m);
        assertTrue(r.found);
        assertArrayEquals(new int[]{3, 5}, r.games[0]);
        assertArrayEquals(new int[]{6, 1}, r.games[1]);
        assertEquals("15", r.points[0]);
        assertEquals("30", r.points[1]);
        // Its box takes in both rows, the flags' room to the left, the points column to the right.
        assertTrue(r.l < 40 && r.r >= 392 && r.t < A && r.b > B + H);
        assertEquals(36f, r.row, 0.01f);
        SportBoard shown = TennisScreen.apply(m, r);
        assertEquals("15", shown.a.points);
        assertEquals("30", shown.b.points);
    }

    @Test
    public void thePictureIsAheadOfEspn() {
        // ESPN still has 4-1 in the second set; the picture 5-1, 15-30: the picture's.
        SportBoard m = match(new int[]{3, 4}, new int[]{6, 1});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"}), m);
        assertTrue(r.found);
        assertArrayEquals(new int[]{3, 5}, TennisScreen.apply(m, r).a.games);
        // A set over on the picture and the next begun (no points between games): three sets, ESPN's two.
        SportBoard m2 = match(new int[]{3, 5}, new int[]{6, 4});
        TennisScreen.Reading r2 = TennisScreen.read(picture(new String[]{"3", "6", "0"}, new String[]{"6", "4", "0"}), m2);
        assertTrue(r2.found);
        assertArrayEquals(new int[]{3, 6, 0}, r2.games[0]);
        assertArrayEquals(new int[]{6, 4, 0}, r2.games[1]);
        assertEquals("", r2.points[0]);
        SportBoard shown = TennisScreen.apply(m2, r2);
        assertEquals(3, shown.setColumns());
        assertEquals(-1, shown.a.tiebreak[2]);
    }

    @Test
    public void seedsWithoutTheirBracketsAreNotAColumn() {
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        List<TennisScreen.Word> p = picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"});
        // The reader dropped the brackets and read the seeds as big as the rest.
        p.removeIf(x -> x.text.startsWith("["));
        p.add(w("2", 226, 238, A));
        p.add(w("6", 220, 232, B));
        TennisScreen.Reading r = TennisScreen.read(p, m);
        assertTrue(r.found);
        assertArrayEquals(new int[]{3, 5}, r.games[0]);
        assertEquals("15", r.points[0]);
    }

    @Test
    public void lettersReadForFiguresArePutRight() {
        assertEquals("40", TennisScreen.figure("4O"));
        assertEquals("15", TennisScreen.figure("I5"));
        assertEquals("5", TennisScreen.figure("S"));
        assertEquals("AD", TennisScreen.figure("Ad"));
        assertEquals("0", TennisScreen.figure("O"));
        assertNull(TennisScreen.figure("189"));
        assertNull(TennisScreen.figure("BLR"));
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"3", "S", "4O"}, new String[]{"6", "I", "AD"}), m);
        assertTrue(r.found);
        assertEquals("40", r.points[0]);
        assertEquals("AD", r.points[1]);
    }

    @Test
    public void advantageWithNothingBesideIt() {
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"3", "5", "AD"}, new String[]{"6", "1", ""}), m);
        assertTrue(r.found);
        assertEquals("AD", r.points[0]);
        assertEquals("40", r.points[1]);
    }

    @Test
    public void aTiebreaksCount() {
        SportBoard m = match(new int[]{6, 6}, new int[]{4, 6});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"6", "6", "5"}, new String[]{"4", "6", "3"}), m);
        assertTrue(r.found);
        assertEquals("5", r.points[0]);
        assertEquals("3", r.points[1]);
    }

    @Test
    public void aBoardOfSetsWonThenGames() {
        // SABALENKA 0 5 15 / SVITOLINA 1 1 30: the sets won, the games in the set in play, the points.
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"0", "5", "15"}, new String[]{"1", "1", "30"}), m);
        assertTrue(r.found);
        assertArrayEquals(new int[]{3, 5}, r.games[0]);
        assertArrayEquals(new int[]{6, 1}, r.games[1]);
        assertEquals("30", r.points[1]);
    }

    @Test
    public void aNameMisreadByALetterIsStillFound() {
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        List<TennisScreen.Word> p = picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"});
        p.set(1, w("SABALENIA", 62, 220, A));
        assertTrue(TennisScreen.read(p, m).found);
        assertEquals(1, TennisScreen.distance("SABALENIA", "SABALENKA"));
    }

    @Test
    public void nothingFitsNothingIsRead() {
        // Another match on the picture.
        SportBoard other = match(new int[]{3, 5}, new int[]{6, 1});
        other.a.shortName = "Gauff";
        other.b.shortName = "Swiatek";
        assertFalse(TennisScreen.read(picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"}), other).found);
        // A finished set read wrong (an 8 for a 6): not trusted, ESPN's kept.
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"3", "5", "15"}, new String[]{"8", "1", "30"}), m);
        assertFalse(r.found);
        assertTrue(TennisScreen.apply(m, r) == m);
        // The set in play behind ESPN's: not the picture of now.
        SportBoard ahead = match(new int[]{3, 5}, new int[]{6, 3});
        assertFalse(TennisScreen.read(picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"}), ahead).found);
    }

    /** A picture w by h of the board's ground, with a round blob of colour c at (cx, cy). */
    private static int[] ground(int w, int h) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, 0xFF1E2A26);
        return px;
    }

    private static void blob(int[] px, int w, float cx, float cy, float rad, int c) {
        for (int y = 0; y < px.length / w; y++) {
            for (int x = 0; x < w; x++) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rad * rad) px[y * w + x] = c;
            }
        }
    }

    private static void rect(int[] px, int w, int l, int t, int r, int b, int c) {
        for (int y = t; y < b; y++) for (int x = l; x < r; x++) px[y * w + x] = c;
    }

    @Test
    public void theServersBallIsSeenOnThePicture() {
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        TennisScreen.Reading r = TennisScreen.read(picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"}), m);
        int w = 500, h = 200;
        // Nothing beside either name: the picture doesn't say.
        int[] px = ground(w, h);
        assertEquals(-1, TennisScreen.server(px, w, h, r));
        // A green ball left of the first player's initial (as the family's channel has it): theirs.
        blob(px, w, 25, A + H / 2f, 9, 0xFF3FB06A);
        assertEquals(0, TennisScreen.server(px, w, h, r));
        // On the second row instead.
        int[] px2 = ground(w, h);
        blob(px2, w, 25, B + H / 2f, 9, 0xFFD7F23A);
        assertEquals(1, TennisScreen.server(px2, w, h, r));
        // Flags by both names (red and blue), the ball by the second's only, between the name and the sets.
        int[] px3 = ground(w, h);
        rect(px3, w, 4, (int) A + 4, 34, (int) A + 20, 0xFFC8102E);
        rect(px3, w, 4, (int) B + 4, 34, (int) B + 20, 0xFF1F4FD8);
        blob(px3, w, 268, B + H / 2f, 8, 0xFF7ED957);
        assertEquals(1, TennisScreen.server(px3, w, h, r));
        // The board's green edge, a thin line down both rows: not a ball.
        int[] px4 = ground(w, h);
        rect(px4, w, 6, (int) A - 6, 8, (int) B + 30, 0xFF3FB06A);
        assertEquals(-1, TennisScreen.server(px4, w, h, r));
    }

    @Test
    public void theServersMark() {
        SportBoard m = match(new int[]{3, 5}, new int[]{6, 1});
        List<TennisScreen.Word> p = picture(new String[]{"3", "5", "15"}, new String[]{"6", "1", "30"});
        p.add(w("•", 22, 32, A));
        TennisScreen.Reading r = TennisScreen.read(p, m);
        assertEquals(0, r.server);
        assertTrue(TennisScreen.apply(m, r).a.serving);
        assertFalse(TennisScreen.apply(m, r).b.serving);
    }
}
