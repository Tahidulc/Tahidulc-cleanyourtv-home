package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Generate's boards: several at once, each readable on a TV at three metres, from the clubs, the competition or a mood. */
public class BoardDesignerTest {
    /** Invented clubs: Harbourside (teal), Millbrook (claret), Fenwick (yellow), Ashgrove (white), Ravens (black). */
    private static final int TEAL = 0xFF0F8B8D, CLARET = 0xFF7A1F3D, YELLOW = 0xFFFFE135, WHITE = 0xFFFFFFFF, BLACK = 0xFF101010;

    private static BoardDesigner.Brief brief(int home, int away, BoardDesigner.Mood mood) {
        BoardDesigner.Brief b = new BoardDesigner.Brief(home, away, "Northern Shield", mood);
        b.homeName = "Harbourside";
        b.awayName = "Millbrook";
        return b;
    }

    private static void assertReadable(BoardSpec s, BoardDesigner.Brief b) {
        String what = s.name + " " + s.toJson();
        assertTrue(what + " " + BoardDesigner.problems(s, b), BoardDesigner.problems(s, b).isEmpty());
        assertTrue(what, BoardColours.contrast(s.text, s.panelMid()) >= BoardDesigner.WORDS);
        assertTrue(what, BoardColours.contrast(s.scoreText, s.scoreGround()) >= BoardDesigner.WORDS);
        assertTrue(what, BoardColours.contrast(0xFFFFFFFF, s.stoppage) >= BoardDesigner.WORDS);
    }

    @Test
    public void severalAtOnceAndAllDifferent() {
        List<BoardSpec> l = BoardDesigner.suggest(brief(TEAL, CLARET, null), 6, 0);
        assertEquals(6, l.size());
        Set<String> seen = new HashSet<>();
        for (BoardSpec s : l) assertTrue(seen.add(s.toJson()));
    }

    @Test
    public void everyMoodEveryRoundPassesTheRules() {
        int[][] pairs = {{TEAL, CLARET}, {YELLOW, WHITE}, {WHITE, BLACK}, {BLACK, BLACK}, {0, 0}, {0xFF0B1640, 0xFF0A1238}};
        for (int[] p : pairs) {
            for (BoardDesigner.Mood m : BoardDesigner.Mood.values()) {
                for (int round = 0; round < 3; round++) {
                    BoardDesigner.Brief b = brief(p[0], p[1], m);
                    for (BoardSpec s : BoardDesigner.suggest(b, 4, round)) assertReadable(s, b);
                }
            }
            BoardDesigner.Brief any = brief(p[0], p[1], null);
            for (BoardSpec s : BoardDesigner.suggest(any, 6, 1)) assertReadable(s, any);
        }
    }

    @Test
    public void theSameBriefGivesTheSameBoardsAndAnotherRoundOthers() {
        BoardDesigner.Brief b = brief(TEAL, CLARET, BoardDesigner.Mood.NIGHT);
        assertEquals(BoardDesigner.suggest(b, 4, 0).get(0).toJson(), BoardDesigner.suggest(b, 4, 0).get(0).toJson());
        assertNotEquals(BoardDesigner.suggest(b, 4, 0).get(0).toJson(), BoardDesigner.suggest(b, 4, 1).get(0).toJson());
    }

    @Test
    public void stripsTheGroundHidesAreLeftOff() {
        // Two black clubs on a near-black minimal board: their strips would not show, so the crests sit on discs instead.
        BoardDesigner.Brief b = brief(BLACK, BLACK, BoardDesigner.Mood.NIGHT);
        for (BoardSpec s : BoardDesigner.suggest(b, 4, 0)) {
            if (BoardColours.contrast(BLACK, s.panelMid()) < BoardDesigner.STRIP) {
                assertFalse(s.name, s.strips);
            }
        }
    }

    @Test
    public void theRulesMendAnUnreadableBoard() {
        BoardSpec bad = BoardSpec.of(BoardSpec.Base.CLASSIC);
        bad.panel = 0xFF333333;
        bad.text = 0xFF444444;
        bad.score = 0xFF343434;
        bad.scoreText = 0xFF3A3A3A;
        bad.clockText = 0xFF222222;
        bad.accent = 0xFF353535;
        bad.stoppage = 0xFFFF9999;
        bad.added = 0xFFFFCCCC;
        BoardDesigner.Brief b = brief(TEAL, CLARET, null);
        assertFalse(BoardDesigner.problems(bad.copy(), b).isEmpty());
        BoardSpec fixed = BoardDesigner.rules(bad, b);
        assertReadable(fixed, b);
        assertEquals(AddedTime.RED, fixed.stoppage);
    }

    @Test
    public void theClubsOwnColourMakesTheClubsBoard() {
        BoardSpec s = BoardDesigner.suggest(brief(TEAL, CLARET, BoardDesigner.Mood.CLUBS), 1, 0).get(0);
        assertTrue(BoardColours.hueGap(BoardColours.hsv(s.score)[0], BoardColours.hsv(TEAL)[0]) < 20);
        assertTrue(s.name.startsWith("Harbourside"));
    }

    @Test
    public void retroIsSquareFlatAndCondensed() {
        for (BoardSpec s : BoardDesigner.suggest(brief(TEAL, CLARET, BoardDesigner.Mood.RETRO), 4, 0)) {
            assertTrue(s.radius <= 0.05f);
            assertEquals(BoardSpec.Finish.FLAT, s.finish);
            assertTrue(s.font == BoardSpec.Font.KHAND || s.font == BoardSpec.Font.BARLOW_HEAVY);
        }
    }

    @Test
    public void theFamilysWordsNameAMood() {
        assertEquals(BoardDesigner.Mood.NIGHT, BoardDesigner.moodOf("something for a night match under the lights"));
        assertEquals(BoardDesigner.Mood.RETRO, BoardDesigner.moodOf("Make it RETRO please"));
        assertEquals(BoardDesigner.Mood.MINIMAL, BoardDesigner.moodOf("simple and clean"));
        assertEquals(BoardDesigner.Mood.BOLD, BoardDesigner.moodOf("big and bold"));
        assertNull(BoardDesigner.moodOf("surprise me"));
    }

    @Test
    public void aKnownCompetitionHasItsOwnColours() {
        int[] pl = BoardDesigner.competitionColours("eng.1", "");
        int[] other = BoardDesigner.competitionColours("", "Northern Shield");
        assertNotEquals(pl[0], other[0]);
        BoardDesigner.Brief b = brief(TEAL, CLARET, BoardDesigner.Mood.COMPETITION);
        b.compSlug = "eng.1";
        BoardSpec s = BoardDesigner.suggest(b, 1, 0).get(0);
        assertEquals(pl[0], s.panel);
    }
}
