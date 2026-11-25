package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The moments' pop-up pages (kickoff.html, strap.html): what the apps package is the source as it is written, and each page
 * has every moment (Moments.KINDS): after a goal the score, then a beat later its scorer (no word for the moment), full
 * time FULL TIME.
 */
public class PopupPagesTest {
    private static byte[] read(String path) throws IOException {
        return Files.readAllBytes(StrapLooksTest.overlays(path).toPath());
    }

    private static String text(String path) throws IOException {
        return new String(read(path), StandardCharsets.UTF_8);
    }

    /** A number a page sets as "NAME = 1.25" (its timeline's). */
    private static double number(String js, String name) {
        Matcher m = Pattern.compile("\\b" + name + " = ([0-9.]+)").matcher(js);
        assertTrue("no " + name, m.find());
        return Double.parseDouble(m.group(1));
    }

    /** A page's table of one string per moment ({ kickoff: 'KICK-OFF', restart: '', ... }) named name, as written. */
    private static Map<String, String> table(String js, String name) {
        Matcher m = Pattern.compile("const " + name + " = \\{([^}]*)\\};").matcher(js);
        assertTrue("no " + name, m.find());
        Map<String, String> out = new LinkedHashMap<>();
        Matcher e = Pattern.compile("([a-z]+): '([^']*)'").matcher(m.group(1));
        while (e.find()) out.put(e.group(1), e.group(2));
        return out;
    }

    @Test
    public void theAppsGetTheKickOffPageAsItIsWritten() throws IOException {
        // build.sh: kickoff.js is the scene's part then the pop-up's; the host page and its scripts are copied as they are
        ByteArrayOutputStream js = new ByteArrayOutputStream();
        js.write(read("src/kickoff/scene-part.js"));
        js.write(read("src/kickoff/popup-part.js"));
        assertTrue("assets/broadcast/kickoff.js is not src/kickoff's parts", Arrays.equals(js.toByteArray(), read("assets/broadcast/kickoff.js")));
        for (String f : new String[]{"kickoff.html", "kickoff-host.js", "layers.js"}) {
            assertTrue("assets/broadcast/" + f + " is not src/host/" + f, Arrays.equals(read("src/host/" + f), read("assets/broadcast/" + f)));
        }
    }

    @Test
    public void theStrapsWordsAreTheScoreThenTheScorerAfterAGoalAndFullTimeAtTheEnd() throws IOException {
        String js = text("src/strap/strap.js");
        Map<String, String> labels = table(js, "LABELS");
        assertEquals(Arrays.asList(Moments.KINDS), Arrays.asList(labels.keySet().toArray(new String[0])));
        assertEquals("KICK-OFF", labels.get("kickoff"));
        assertEquals("", labels.get("restart"));
        assertEquals("SECOND HALF", labels.get("second"));
        assertEquals("FULL TIME", labels.get("full"));
        // every moment drawn as itself (one the page did not know would be drawn as the kick-off)
        Matcher mo = Pattern.compile("const MOMENT = ([^;]*);").matcher(js);
        assertTrue(mo.find());
        for (String k : Moments.KINDS) if (!"kickoff".equals(k)) assertTrue(k, mo.group(1).contains("C.kind === '" + k + "'"));
        // no word for a goal; its scorer only after a goal, as the board names them (the minute, (og) or (pen) after it)
        assertFalse(js.contains("RESTART"));
        assertTrue(js.contains("const GOAL = MOMENT === 'restart' && C.scorer ?"));
        assertTrue(js.contains("C.goalMark === 'og' || C.goalMark === 'pen'"));
        assertTrue(js.contains("(GOAL.mark ? ' (' + GOAL.mark + ')' : '')"));
        // the scorer a beat after the score is up (the strap in by IN1), and fully out before the strap folds (OUT0)
        double in1 = number(js, "IN1"), sc0 = number(js, "SC0"), sc1 = number(js, "SC1"), out0 = number(js, "OUT0");
        assertTrue("a beat: " + (sc0 - in1), sc0 - in1 >= 1.0 && sc0 - in1 <= 2.0);
        assertTrue(sc1 > sc0 && out0 - sc1 >= 1.5);
        // on the line under the strap where the brand is (and the looks with a line of their own)
        assertTrue(js.contains("goalHTML(14, true, 24"));
        String host = text("src/host/strap-host.js");
        assertTrue(host.contains("scorer: cfg.scorer || ''"));
        assertTrue(host.contains("goalMark: cfg.goalMark || ''"));
        // full time's clock: FT, stopped
        assertTrue(js.contains("if (FT) return 'FT';"));
    }

    @Test
    public void theKickOffPopUpHasEveryMomentAfterAGoalTheScoreThenTheScorer() throws IOException {
        String js = text("src/kickoff/popup-part.js");
        Matcher k = Pattern.compile("const KINDS = \\{\\n(.*?)\\n  \\};", Pattern.DOTALL).matcher(js);
        assertTrue(k.find());
        Map<String, String> kinds = new LinkedHashMap<>();
        for (String line : k.group(1).split("\\n")) {
            Matcher l = Pattern.compile("^\\s*([a-z]+): \\{ pages: (\\[.*\\]), steps:").matcher(line);
            if (l.find()) kinds.put(l.group(1), l.group(2));
        }
        for (String kind : Moments.KINDS) assertTrue(kind, kinds.containsKey(kind));
        // after a goal: the competition's tab over the score, the tab turning to its scorer a beat later, the score kept on
        // the bar (no GOAL word); with no scorer given, the competition's tab alone
        assertEquals("[['comp', 'score'], ['scorer', 'score']]", kinds.get("restart"));
        assertTrue(js.contains("restart: { pages: [['comp', 'score'], ['scorer', 'score']], steps: [[1.6, 'tab']], close: 4.4 },"));
        assertEquals("[['comp', 'score']]", kinds.get("goal"));
        assertTrue(js.contains("if (m.kind === 'restart' && !m.scorer) m.kind = 'goal';"));
        assertTrue(js.contains("else if (kind === 'scorer') {"));
        assertTrue(js.contains("[m.scorer || '', m.goalMin || '', m.goalMark ? '(' + m.goalMark + ')' : '']"));
        assertEquals("[['comp', 'score'], ['full', 'score']]", kinds.get("full"));
        assertTrue(js.contains("else if (kind === 'full') { items.push(house(30)); items.push(text('FULL TIME'"));
        assertFalse(js.contains("GOAL'"));
        // the host: each moment's length and its sound (one the app has)
        String host = text("src/host/kickoff-host.js");
        Map<String, String> sounds = table(host, "SOUND");
        assertEquals(Arrays.asList(Moments.KINDS), Arrays.asList(sounds.keySet().toArray(new String[0])));
        for (String s : sounds.values()) assertTrue(s, StrapLooksTest.overlays("src/sfx/" + s + ".ogg").exists());
        Matcher len = Pattern.compile("const LEN = \\{([^}]*)\\};").matcher(host);
        assertTrue(len.find());
        for (String kind : Moments.KINDS) assertTrue(kind, len.group(1).contains(kind + ":"));
        // the scorer, its minute and mark taken for a goal's only
        assertTrue(host.contains("scorer: kind === 'restart' ? String(cfg.scorer || '') : ''"));
        assertTrue(host.contains("goalMark: kind === 'restart' && /^(og|pen)$/.test(cfg.goalMark || '') ? cfg.goalMark : ''"));
    }
}
