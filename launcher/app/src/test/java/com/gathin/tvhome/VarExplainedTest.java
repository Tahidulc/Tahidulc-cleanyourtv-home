package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** VAR explained: decisions and checks worked out from ESPN's commentary and FotMob's events, as they really read. */
public class VarExplainedTest {
    private static List<String[]> lines(String... textThenMinute) {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i + 1 < textThenMinute.length; i += 2) l.add(new String[]{textThenMinute[i], textThenMinute[i + 1]});
        return l;
    }

    private static int last(List<String[]> l) {
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i)[0].contains("VAR Decision:")) return i;
        return -1;
    }

    @Test
    public void aGoalRuledOutForHandballNotTheOtherSidesOffside() {
        // Lecce v Genoa, as ESPN wrote it: Genoa's offside a minute before is not this decision's reason.
        List<String[]> l = lines(
                "Offside, Genoa. Milutin Osmajic is caught offside.", "45'+1'",
                "GOAL OVERTURNED BY VAR: Nikola Stulic (Lecce) scores but the goal is ruled out after a VAR review.", "45'+2'",
                "Attempt saved. Nikola Stulic (Lecce) right footed shot from the left side of the six yard box is saved in the bottom left corner by Justin Bijlow (Genoa).", "45'+2'",
                "Handball by Nikola Stulic (Lecce).", "45'+2'",
                "VAR Decision: No Goal Lecce 1-2 Genoa.", "45'+4'",
                "First Half ends, Lecce 1, Genoa 2.", "45'+4'");
        VarExplained.Call c = VarExplained.decision(l, 4);
        assertNotNull(c);
        assertEquals("no_goal", c.kind);
        assertEquals(VarExplained.HANDBALL, c.reason);
        assertEquals("Nikola Stulic", c.player);
        assertEquals(-1, c.onField);
        assertEquals("No goal", VarExplained.outcome(c));
        assertEquals("overturned · the referee changed it", VarExplained.callLine(c.onField));
        assertEquals(VarExplained.ICON_HANDBALL, VarExplained.icon(c));
    }

    @Test
    public void aGoalRuledOutForOffside() {
        List<String[]> l = lines(
                "Corner, Toulouse. Conceded by Ulisses Garcia.", "68'",
                "GOAL OVERTURNED BY VAR: Casper Tengstedt (Toulouse) scores but the goal is ruled out after a VAR review.", "68'",
                "Offside, Toulouse. Casper Tengstedt is caught offside.", "68'",
                "VAR Decision: No Goal Marseille 2-0 Toulouse.", "68'");
        VarExplained.Call c = VarExplained.decision(l, last(l));
        assertEquals("no_goal", c.kind);
        assertEquals(VarExplained.OFFSIDE, c.reason);
        assertEquals("Casper Tengstedt", c.player);
        assertEquals(-1, c.onField);
        assertTrue(VarExplained.why(c).contains("offside"));
        assertEquals(VarExplained.ICON_OFFSIDE, VarExplained.icon(c));
    }

    @Test
    public void aPenaltyForHandballTheCallUnknownInTheSameMinute() {
        List<String[]> l = lines(
                "Attempt blocked. Charles De Ketelaere (Atalanta) header from the centre of the box is blocked.", "29'",
                "Penalty conceded by Giorgio Cittadini (Frosinone) with a handball in the penalty area.", "29'",
                "VAR Decision: Penalty Atalanta.", "29'");
        VarExplained.Call c = VarExplained.decision(l, 2);
        assertEquals("penalty", c.kind);
        assertEquals("Atalanta", c.team);
        assertEquals(VarExplained.HANDBALL, c.reason);
        assertEquals("Giorgio Cittadini", c.player);
        // Given in the same minute as the check: not known whether the referee had given it.
        assertEquals(0, c.onField);
        assertEquals("after a VAR review", VarExplained.callLine(c.onField));
    }

    @Test
    public void aPenaltyGivenAMinuteBeforeStood() {
        List<String[]> l = lines(
                "Penalty Hoffenheim. Fisnik Asllani draws a foul in the penalty area.", "76'",
                "Penalty conceded by Edmond Tapsoba (Bayer 04 Leverkusen) after a foul in the penalty area.", "76'",
                "VAR Decision: Penalty Hoffenheim.", "77'");
        VarExplained.Call c = VarExplained.decision(l, 2);
        assertEquals("penalty", c.kind);
        assertEquals(VarExplained.FOUL, c.reason);
        assertEquals("Edmond Tapsoba", c.player);
        assertEquals(1, c.onField);
        assertEquals("Penalty given", VarExplained.outcome(c));
        assertEquals("the referee's call stands", VarExplained.callLine(c.onField));
    }

    @Test
    public void noPenaltyForADiveAndFotMobSaysThePenaltyWasCancelled() {
        List<String[]> l = lines(
                "Corentin Tolisso (Lyon) wins a free kick in the defensive half.", "59'",
                "Kaïl Boudache (Lyon) has gone down, but the referee deems it simulation.", "61'",
                "VAR Decision: No Penalty Lyon.", "62'",
                "Kaïl Boudache (Lyon) is shown the yellow card.", "63'");
        VarExplained.Call c = VarExplained.decision(l, 2);
        assertEquals("no_penalty", c.kind);
        assertEquals(VarExplained.SIMULATION, c.reason);
        assertEquals("Kaïl Boudache", c.player);
        assertEquals(0, c.onField);
        // FotMob's event for it: the penalty given on the field was cancelled.
        VarExplained.enrich(c, Collections.singletonList(new String[]{"62", "Kaïl Boudache", "var_penalty_cancelled", "Penalty cancelled", ""}));
        assertEquals(-1, c.onField);
        assertTrue(VarExplained.why(c).contains("simulation"));
    }

    @Test
    public void noPenaltyWithNothingSaidIsTheGeneralWhy() {
        List<String[]> l = lines(
                "Delay in match because of an injury Filip Stankovic (Venezia).", "25'",
                "Delay over. They are ready to continue.", "26'",
                "VAR Decision: No Penalty Venezia.", "28'");
        VarExplained.Call c = VarExplained.decision(l, 2);
        assertEquals("no_penalty", c.kind);
        assertEquals("", c.reason);
        assertEquals(0, c.onField);
        assertEquals(VarExplained.ICON_SCREEN, VarExplained.icon(c));
        assertTrue(VarExplained.why(c).contains("clear and obvious"));
    }

    @Test
    public void goalsConfirmedAndAwarded() {
        List<String[]> stood = lines(
                "Goal! Newcastle United 0, Everton 1. Thierno Barry (Everton) right footed shot from very close range to the centre of the goal following a set piece situation.Goal confirmed following VAR Review.", "49'",
                "Delay in match because of an injury Fabian Schär (Newcastle United).", "50'",
                "Delay over. They are ready to continue.", "51'",
                "VAR Decision: Goal Everton - Newcastle United 0-1 Everton (Thierno Barry).", "51'");
        VarExplained.Call c = VarExplained.decision(stood, 3);
        assertEquals("goal", c.kind);
        assertEquals(1, c.onField);
        assertEquals("Goal stands", VarExplained.outcome(c));
        assertEquals("Thierno Barry", c.player);
        List<String[]> given = lines(
                "Goal! Rennes 1, Strasbourg 0. Boulaye Dia (Rennes) right footed shot from the centre of the box to the centre of the goal. Assisted by Przemyslaw Frankowski.Goal awarded following VAR Review.", "34'",
                "VAR Decision: Goal Rennes 1-0 Strasbourg (Boulaye Dia).", "34'");
        VarExplained.Call g = VarExplained.decision(given, 1);
        assertEquals(-1, g.onField);
        assertEquals("Goal given", VarExplained.outcome(g));
    }

    @Test
    public void aCardUpgradedToRedForViolentConduct() {
        List<String[]> l = lines(
                "Foul by Chema Andrés (Brighton and Hove Albion).", "21'",
                "Chema Andrés (Brighton and Hove Albion) is shown the yellow card.", "22'",
                "VAR Decision: Card upgraded Igor Thiago (Brentford).", "23'",
                "Igor Thiago (Brentford) is shown the red card for violent conduct.", "23'");
        VarExplained.Call c = VarExplained.decision(l, 2);
        assertEquals("red", c.kind);
        assertEquals(VarExplained.VIOLENT, c.reason);
        assertEquals("Igor Thiago", c.player);
        assertEquals(-1, c.onField);
        assertEquals("Red card", VarExplained.outcome(c));
        assertTrue(VarExplained.why(c).startsWith("Violent conduct"));
    }

    @Test
    public void aFoulElsewhereIsNotAPenaltysReason() {
        // Real Madrid v Racing: Cucurella's foul a minute before is not the penalty FotMob says was cancelled.
        List<String[]> l = lines(
                "Íñigo Sainz-Maza (Racing de Santander) wins a free kick in the defensive half.", "71'",
                "Foul by Marc Cucurella (Real Madrid).", "71'",
                "VAR Decision: No Penalty Racing de Santander.", "72'");
        VarExplained.Call c = VarExplained.decision(l, 2);
        assertEquals("no_penalty", c.kind);
        assertEquals("", c.reason);
        assertEquals("", c.player);
        VarExplained.enrich(c, Collections.singletonList(new String[]{"72", "Aurélien Tchouaméni", "var_penalty_cancelled", "Penalty cancelled", ""}));
        assertEquals(-1, c.onField);
        assertTrue(VarExplained.why(c).contains("taken away"));
    }

    @Test
    public void notADecisionItKnows() {
        assertNull(VarExplained.decision(lines("VAR Decision: Other Decision Cancelled.", "40'"), 0));
        assertNull(VarExplained.decision(lines("Foul by A Player (Club).", "40'"), 0));
        assertNull(VarExplained.decision(new ArrayList<>(), 0));
    }

    @Test
    public void reasonsFromOneLine() {
        assertArrayEquals(new String[]{VarExplained.OFFSIDE, "Pablo Durán", "Celta Vigo", VarExplained.FROM_LINE},
                VarExplained.reasonOf("Offside, Celta Vigo. Pablo Durán is caught offside."));
        assertArrayEquals(new String[]{VarExplained.HANDBALL, "Nikola Stulic", "Lecce", VarExplained.FROM_LINE},
                VarExplained.reasonOf("Handball by Nikola Stulic (Lecce)."));
        assertArrayEquals(new String[]{VarExplained.FOUL, "", "Hoffenheim", VarExplained.FROM_PENALTY},
                VarExplained.reasonOf("Penalty Hoffenheim. Fisnik Asllani draws a foul in the penalty area."));
        assertEquals(VarExplained.FROM_FOUL_BY, VarExplained.reasonOf("Foul by Marc Cucurella (Real Madrid).")[3]);
        assertEquals(VarExplained.SERIOUS, VarExplained.reasonOf("Giorgi Kvernadze (Frosinone) is shown the red card for a bad foul.")[0]);
        assertNull(VarExplained.reasonOf("Corner, Como. Conceded by Matías Moreno."));
        assertNull(VarExplained.reasonOf("Kaïl Boudache (Lyon) is shown the yellow card."));
    }

    @Test
    public void whatACheckIsAbout() {
        List<String[]> l = lines(
                "Penalty conceded by Giorgio Cittadini (Frosinone) with a handball in the penalty area.", "29'",
                "Delay in match (Atalanta). VAR checking.", "29'");
        String[] a = VarExplained.checkAbout(l, 1);
        assertEquals("penalty", a[0]);
        assertEquals(VarExplained.HANDBALL, a[1]);
        assertEquals("Checking a penalty", VarExplained.checkTitle(a[0]));
        assertEquals("possible handball · Cittadini", VarExplained.checkLine(a[1], a[2]));
        String[] goal = VarExplained.checkAbout(lines("Goal! Rennes 1, Strasbourg 0. Boulaye Dia (Rennes) right footed shot.", "34'",
                "Delay in match (Rennes). VAR checking.", "34'"), 1);
        assertEquals("goal", goal[0]);
        assertEquals("Checking a goal", VarExplained.checkTitle(goal[0]));
        assertEquals("Checking an incident", VarExplained.checkTitle(""));
        assertEquals("Checking a red card", VarExplained.checkTitle(VarExplained.aboutOf("Possible red card")));
        assertEquals("", VarExplained.checkLine("", ""));
        assertEquals("possible offside", VarExplained.checkLine(VarExplained.OFFSIDE, ""));
    }

    @Test
    public void howLongTheCheckTook() {
        assertEquals("", VarExplained.length(0));
        assertEquals("42 s", VarExplained.length(42_000L));
        assertEquals("1 min 25 s", VarExplained.length(85_000L));
        assertEquals("2 min", VarExplained.length(120_000L));
        assertEquals("Checked for 1 min 25 s · on-field decision stood", VarExplained.foot(85_000L, 1));
        assertEquals("Checked for 42 s · on-field decision overturned", VarExplained.foot(42_000L, -1));
        assertEquals("Checked for 42 s", VarExplained.foot(42_000L, 0));
        assertEquals("On-field decision overturned", VarExplained.foot(0, -1));
        assertEquals("VAR review", VarExplained.foot(0, 0));
    }

    @Test
    public void fotmobsOwnDecisions() {
        VarExplained.Call off = VarExplained.fromFotMob(new String[]{"52", "Joseph Willock", "var_goal_cancelled", "Goal ruled out", "offside"}, null);
        assertEquals("no_goal", off.kind);
        assertEquals(VarExplained.OFFSIDE, off.reason);
        assertEquals(-1, off.onField);
        VarExplained.Call pen = VarExplained.fromFotMob(new String[]{"72", "Aurélien Tchouaméni", "var_penalty_cancelled", "Penalty cancelled", ""}, null);
        assertEquals("no_penalty", pen.kind);
        assertEquals(-1, pen.onField);
        // A yellow taken back and a red two minutes on: upgraded, for the red's reason.
        VarExplained.Call red = VarExplained.fromFotMob(new String[]{"21", "Igor Thiago", "var_yellow_card_removed", "Yellow card cancelled", ""},
                Collections.singletonList(new String[]{"23", "Igor Thiago", "card_desc_violent_conduct"}));
        assertEquals("red", red.kind);
        assertEquals(VarExplained.VIOLENT, red.reason);
        VarExplained.Call taken = VarExplained.fromFotMob(new String[]{"30", "A Player", "var_yellow_card_removed", "Yellow card cancelled", ""}, null);
        assertEquals("card_cancelled", taken.kind);
        assertEquals("Card taken back", VarExplained.outcome(taken));
        VarExplained.Call other = VarExplained.fromFotMob(new String[]{"30", "", "var_something_new", "Something new", ""}, null);
        assertEquals("Something new", VarExplained.outcome(other));
    }

    @Test
    public void fotmobFillsOnlyWhatEspnLacks() {
        VarExplained.Call c = new VarExplained.Call();
        c.kind = "no_goal";
        c.minute = "45'+4'";
        c.reason = VarExplained.HANDBALL;
        VarExplained.enrich(c, Arrays.asList(new String[]{"10", "Someone", "var_goal_cancelled", "Goal ruled out", "offside"},
                new String[]{"47", "Nikola Stulic", "var_goal_cancelled", "Goal ruled out", "foul"}));
        // ESPN's handball kept (FotMob calls it a foul); the on-field call from FotMob.
        assertEquals(VarExplained.HANDBALL, c.reason);
        assertEquals(-1, c.onField);
    }

    @Test
    public void theWhyNeverSaysMoreThanTheReason() {
        for (String kind : new String[]{"penalty", "no_penalty", "goal", "no_goal", "red", "card_cancelled", "other"}) {
            for (String reason : new String[]{"", VarExplained.HANDBALL, VarExplained.OFFSIDE, VarExplained.FOUL, VarExplained.SIMULATION,
                    VarExplained.VIOLENT, VarExplained.SERIOUS, VarExplained.CHANCE, VarExplained.LANGUAGE, VarExplained.OUT, VarExplained.IDENTITY}) {
                for (int on = -1; on <= 1; on++) {
                    VarExplained.Call c = new VarExplained.Call();
                    c.kind = kind;
                    c.reason = reason;
                    c.onField = on;
                    String w = VarExplained.why(c);
                    assertFalse(w.isEmpty());
                    String lower = w.toLowerCase(java.util.Locale.ROOT);
                    assertFalse(w, lower.contains("close to") || lower.contains("deliberate") || lower.contains("odds") || lower.contains("bet"));
                    assertTrue(w, w.length() < 200);
                }
            }
        }
    }

    @Test
    public void scanningTheCommentary() {
        List<String[]> l = new ArrayList<>();
        l.add(new String[]{"Penalty conceded by Giorgio Cittadini (Frosinone) with a handball in the penalty area.", "29'", "40"});
        l.add(new String[]{"Delay in match (Atalanta). VAR checking.", "29'", "41"});
        List<VarExplained.Seen> s = VarExplained.scan(l);
        assertEquals(1, s.size());
        assertNull(s.get(0).call);
        assertEquals("penalty", s.get(0).about[0]);
        assertEquals("41", s.get(0).key);
        // Its decision in: the check is over, the decision is what there is.
        l.add(new String[]{"VAR Decision: Penalty Atalanta.", "30'", "42"});
        s = VarExplained.scan(l);
        assertEquals(1, s.size());
        assertEquals("penalty", s.get(0).call.kind);
        assertEquals("42", s.get(0).key);
        // Nothing on a game's first read (it was before the board came up); a new one after.
        assertTrue(VarExplained.fresh("scan-test", s).isEmpty());
        l.add(new String[]{"GOAL OVERTURNED BY VAR: A Player (Atalanta) scores but the goal is ruled out after a VAR review.", "80'", "90"});
        l.add(new String[]{"VAR Decision: No Goal Atalanta 2-2 Frosinone.", "81'", "91"});
        List<VarExplained.Seen> fresh = VarExplained.fresh("scan-test", VarExplained.scan(l));
        assertEquals(1, fresh.size());
        assertEquals("no_goal", fresh.get(0).call.kind);
        assertTrue(VarExplained.fresh("scan-test", VarExplained.scan(l)).isEmpty());
    }

    @Test
    public void fromTheAlertsCard() {
        assertEquals("no_penalty", VarExplained.fromCard("NO PENALTY", "VAR", "62'").kind);
        assertEquals("goal", VarExplained.fromCard("GOAL", "STANDS", "").kind);
        assertEquals("red", VarExplained.fromCard("RED CARD", "VAR", "").kind);
        assertEquals("penalty", VarExplained.fromCard("PENALTY", "GIVEN", "").kind);
        assertEquals("no_goal", VarExplained.fromCard("NO GOAL", "VAR", "").kind);
    }

    @Test
    public void minutes() {
        assertEquals(45, VarExplained.base("45'+2'"));
        assertEquals(47, VarExplained.at("45'+2'"));
        assertEquals(68, VarExplained.at("68'"));
        assertEquals(-1, VarExplained.at(""));
    }

    @Test
    public void fotmobsVarEventRead() throws Exception {
        JSONObject e = new JSONObject("{\"type\":\"VAR\",\"time\":45,\"overloadTime\":null,\"player\":{\"id\":1,\"name\":\"Nikola Štulić\"},"
                + "\"VAR\":{\"pendingDecision\":false,\"decision\":{\"key\":[\"var_goal_cancelled\",\"foul\"],\"value\":[\"Goal ruled out\",\"foul\"]}}}");
        assertArrayEquals(new String[]{"45", "Nikola Štulić", "var_goal_cancelled", "Goal ruled out", "foul"}, FotMob.varEvent(e));
        JSONObject pending = new JSONObject("{\"type\":\"VAR\",\"time\":45,\"VAR\":{\"pendingDecision\":true,\"decision\":{\"key\":[],\"value\":[]}}}");
        assertNull(FotMob.varEvent(pending));
        assertNull(FotMob.varEvent(new JSONObject("{\"type\":\"Card\",\"VAR\":null}")));
    }

    @Test
    public void dotsBeatInTurn() {
        float[] a = BoardDrop.dots(0), b = BoardDrop.dots(400);
        for (float x : a) assertTrue(x >= 0.3f - 1e-4f && x <= 1f + 1e-4f);
        assertTrue(a[0] > a[2]);
        assertFalse(Arrays.equals(a, b));
    }

    @Test
    public void whyPanelWidth() {
        assertEquals(480, VarWhy.widthFor(48f, 100));
        assertEquals(600, VarWhy.widthFor(48f, 2000));
        assertEquals(520, VarWhy.widthFor(48f, 520));
    }
}
