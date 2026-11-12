package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The commentary's VAR lines and those round them, kept for VAR explained; nothing when VAR is never mentioned. */
public class EspnVarLinesTest {
    private static JSONObject line(int seq, String minute, String text) throws Exception {
        return new JSONObject().put("sequence", seq).put("text", text).put("time", new JSONObject().put("displayValue", minute));
    }

    @Test
    public void keepsTheLinesRoundEachVarLine() throws Exception {
        JSONArray com = new JSONArray();
        for (int i = 0; i < 12; i++) com.put(line(i, i + "'", "Corner, Side A. Conceded by Somebody."));
        com.put(line(12, "45'+2'", "GOAL OVERTURNED BY VAR: Nikola Stulic (Lecce) scores but the goal is ruled out after a VAR review."));
        com.put(line(13, "45'+2'", "Handball by Nikola Stulic (Lecce)."));
        com.put(line(14, "45'+4'", "VAR Decision: No Goal Lecce 1-2 Genoa."));
        for (int i = 15; i < 25; i++) com.put(line(i, "50'", "Attempt missed."));
        List<String[]> into = new ArrayList<>();
        Espn.varLines(com, into);
        // Six before the first VAR line, through two after the last.
        assertEquals("6", into.get(0)[2]);
        assertEquals("16", into.get(into.size() - 1)[2]);
        assertEquals(11, into.size());
        assertEquals("45'+4'", into.get(8)[1]);
        assertTrue(into.get(8)[0].startsWith("VAR Decision"));
    }

    @Test
    public void noneWithoutVar() throws Exception {
        JSONArray com = new JSONArray().put(line(1, "3'", "Foul by A Player (Club)."));
        List<String[]> into = new ArrayList<>();
        Espn.varLines(com, into);
        assertTrue(into.isEmpty());
        Espn.varLines(null, into);
        assertTrue(into.isEmpty());
    }
}
