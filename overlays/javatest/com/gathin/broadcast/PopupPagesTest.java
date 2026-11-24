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
 * has every moment (Moments.KINDS): after a goal the score alone (no word, no scorer, no minute), full time FULL TIME.
 */
public class PopupPagesTest {
    private static byte[] read(String path) throws IOException {
        return Files.readAllBytes(StrapLooksTest.overlays(path).toPath());
    }

    private static String text(String path) throws IOException {
        return new String(read(path), StandardCharsets.UTF_8);
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
    public void theStrapsWordsAreTheScoreAloneAfterAGoalAndFullTimeAtTheEnd() throws IOException {
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
        // no word for a goal, and no scorer or minute line anywhere on the strap
        assertFalse(js.contains("RESTART"));
        assertFalse(js.contains("C.scorer"));
        assertFalse(text("src/host/strap-host.js").contains("scorer"));
        // full time's clock: FT, stopped
        assertTrue(js.contains("if (FT) return 'FT';"));
    }

    @Test
    public void theKickOffPopUpHasEveryMomentAfterAGoalTheScoreAlone() throws IOException {
        String js = text("src/kickoff/popup-part.js");
        Matcher k = Pattern.compile("const KINDS = \\{\\n(.*?)\\n  \\};", Pattern.DOTALL).matcher(js);
        assertTrue(k.find());
        Map<String, String> kinds = new LinkedHashMap<>();
        for (String line : k.group(1).split("\\n")) {
            Matcher l = Pattern.compile("^\\s*([a-z]+): \\{ pages: (\\[.*\\]), steps:").matcher(line);
            if (l.find()) kinds.put(l.group(1), l.group(2));
        }
        for (String kind : Moments.KINDS) assertTrue(kind, kinds.containsKey(kind));
        // after a goal: the competition's tab over the score, nothing more (no GOAL tab, no scorer on the bar)
        assertEquals("[['comp', 'score']]", kinds.get("restart"));
        assertEquals("[['comp', 'score'], ['full', 'score']]", kinds.get("full"));
        assertTrue(js.contains("else if (kind === 'full') { items.push(house(30)); items.push(text('FULL TIME'"));
        assertFalse(js.contains("kind === 'goal'"));
        assertFalse(js.contains("m.scorer"));
        // the host: each moment's length and its sound (one the app has)
        String host = text("src/host/kickoff-host.js");
        Map<String, String> sounds = table(host, "SOUND");
        assertEquals(Arrays.asList(Moments.KINDS), Arrays.asList(sounds.keySet().toArray(new String[0])));
        for (String s : sounds.values()) assertTrue(s, StrapLooksTest.overlays("src/sfx/" + s + ".ogg").exists());
        Matcher len = Pattern.compile("const LEN = \\{([^}]*)\\};").matcher(host);
        assertTrue(len.find());
        for (String kind : Moments.KINDS) assertTrue(kind, len.group(1).contains(kind + ":"));
        assertFalse(host.contains("scorer"));
    }
}
