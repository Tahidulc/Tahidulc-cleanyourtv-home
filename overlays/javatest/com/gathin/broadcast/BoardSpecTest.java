package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

/** The family's own scoreboards' spec: read safely, kept legible, written back the same, and its schema for Ask AI. */
public class BoardSpecTest {
    private static final String FULL = "{\"v\":1,\"name\":\"Harbour night\",\"base\":\"GLASS\",\"colours\":{\"panel\":\"#101C3A\","
            + "\"panelFoot\":\"#0A1226\",\"comp\":\"#0A1226\",\"score\":\"#E0B03A\",\"scoreFoot\":\"#B8891C\",\"clock\":\"#060B18\","
            + "\"text\":\"#F2F4FA\",\"scoreText\":\"#101C3A\",\"clockText\":\"#E6E9F2\",\"dim\":\"#A9B4D0\",\"accent\":\"#E0B03A\","
            + "\"stoppage\":\"#D7192F\",\"added\":\"#D7192F\"},\"radius\":0.5,\"border\":{\"width\":0.03,\"colour\":\"#E0B03A\"},"
            + "\"finish\":\"GLOSS\",\"font\":\"BARLOW\",\"crest\":\"RING\",\"strips\":false,\"accentLine\":true}";

    @Test
    public void aFullSpecIsReadAsWritten() {
        BoardSpec s = BoardSpec.parse(FULL);
        assertEquals("Harbour night", s.name);
        assertEquals(BoardSpec.Base.GLASS, s.base);
        assertEquals(0xFF101C3A, s.panel);
        assertEquals(0xFF0A1226, s.panelFoot);
        assertEquals(0xFFE0B03A, s.score);
        assertEquals(0xFF101C3A, s.scoreText);
        assertEquals(0.5f, s.radius, 1e-6);
        assertEquals(0.03f, s.borderWidth, 1e-6);
        assertEquals(0xFFE0B03A, s.border);
        assertEquals(BoardSpec.Finish.GLOSS, s.finish);
        assertEquals(BoardSpec.Font.BARLOW, s.font);
        assertEquals(BoardSpec.Crest.RING, s.crest);
        assertFalse(s.strips);
        assertTrue(s.accentLine);
    }

    @Test
    public void writtenAndReadAgainItIsTheSame() {
        BoardSpec s = BoardSpec.parse(FULL);
        String once = s.toJson();
        assertEquals(once, BoardSpec.parse(once).toJson());
        assertEquals(once, s.copy().toJson());
    }

    @Test
    public void nothingAtAllIsClassic() {
        for (String bad : new String[]{null, "", "not json", "[]", "{", "{\"base\":42}", "{\"colours\":\"red\"}"}) {
            BoardSpec s = BoardSpec.parse(bad);
            assertNotNull(s);
            assertEquals(BoardSpec.Base.CLASSIC, s.base);
            assertEquals(BoardSpec.of(BoardSpec.Base.CLASSIC).panel, s.panel);
            assertTrue(bad, s.leastContrast() >= BoardSpec.FLOOR);
        }
    }

    @Test
    public void aPartialSpecTakesItsBasesOwnForWhatIsMissing() {
        BoardSpec s = BoardSpec.parse("{\"base\":\"LIGHT\",\"colours\":{\"accent\":\"#00A86B\"}}");
        BoardSpec light = BoardSpec.of(BoardSpec.Base.LIGHT);
        assertEquals(BoardSpec.Base.LIGHT, s.base);
        assertEquals(light.panel, s.panel);
        assertEquals(light.text, s.text);
        assertEquals(0xFF00A86B, s.accent);
        // Added time's one red unless the board sets its own.
        assertEquals(AddedTime.RED, s.stoppage);
        assertEquals(AddedTime.RED, s.added);
    }

    @Test
    public void unknownNamesFallBackAndBadColoursAreIgnored() {
        BoardSpec s = BoardSpec.parse("{\"base\":\"HOLOGRAM\",\"finish\":\"CHROME\",\"font\":\"Comic\",\"crest\":\"SPIN\","
                + "\"colours\":{\"panel\":\"#12\",\"score\":\"blue\",\"text\":\"#GGHHII\"}}");
        assertEquals(BoardSpec.Base.CLASSIC, s.base);
        assertEquals(BoardSpec.Finish.FLAT, s.finish);
        assertEquals(BoardSpec.Font.OUTFIT, s.font);
        assertEquals(BoardSpec.Crest.PLAIN, s.crest);
        assertEquals(BoardSpec.of(BoardSpec.Base.CLASSIC).panel, s.panel);
        assertEquals(BoardSpec.of(BoardSpec.Base.CLASSIC).score, s.score);
    }

    @Test
    public void namesAreReadCaseAndLabelAlike() {
        BoardSpec s = BoardSpec.parse("{\"base\":\"frosted glass\",\"font\":\"barlow condensed\",\"finish\":\"frosted\"}");
        assertEquals(BoardSpec.Base.FROSTED, s.base);
        assertEquals(BoardSpec.Font.BARLOW, s.font);
        assertEquals(BoardSpec.Finish.FROSTED, s.finish);
    }

    @Test
    public void numbersAreKeptInRange() {
        BoardSpec s = BoardSpec.parse("{\"radius\":9,\"border\":{\"width\":-3,\"colour\":\"#FFFFFF\"}}");
        assertEquals(0.5f, s.radius, 1e-6);
        assertEquals(0f, s.borderWidth, 1e-6);
        s = BoardSpec.parse("{\"radius\":\"0.2\",\"borderWidth\":0.5}");
        assertEquals(0.2f, s.radius, 1e-6);
        assertEquals(0.1f, s.borderWidth, 1e-6);
        s = BoardSpec.parse("{\"radius\":\"NaN\"}");
        assertEquals(BoardSpec.of(BoardSpec.Base.CLASSIC).radius, s.radius, 1e-6);
    }

    @Test
    public void wordsTheSameColourAsTheirGroundAreMadeReadable() {
        BoardSpec s = BoardSpec.parse("{\"colours\":{\"panel\":\"#1A1A1A\",\"text\":\"#1A1A1A\",\"score\":\"#FFFFFF\","
                + "\"scoreText\":\"#FAFAFA\",\"clock\":\"#202020\",\"clockText\":\"#222222\"}}");
        assertTrue(BoardColours.contrast(s.text, s.panel) >= BoardSpec.FLOOR);
        assertTrue(BoardColours.contrast(s.scoreText, s.scoreGround()) >= BoardSpec.FLOOR);
        assertTrue(BoardColours.contrast(s.clockText, s.clockGround()) >= BoardSpec.FLOOR);
        assertTrue(s.leastContrast() >= BoardSpec.FLOOR);
    }

    @Test
    public void readableWordsAreLeftAsChosen() {
        BoardSpec s = BoardSpec.parse("{\"colours\":{\"panel\":\"#0B1640\",\"text\":\"#F5C518\"}}");
        assertEquals(0xFFF5C518, s.text);
    }

    @Test
    public void thePanelIsAlwaysSolidSoTheChannelsBoardNeverShows() {
        BoardSpec s = BoardSpec.parse("{\"colours\":{\"panel\":\"#200B1640\",\"panelFoot\":\"#10000000\"}}");
        assertEquals(0xFF, BoardColours.alpha(s.panel));
        assertEquals(0xFF, BoardColours.alpha(s.panelFoot));
        // A see-through cell over it is allowed (Glass's score), its figures read on the two together.
        s = BoardSpec.parse("{\"base\":\"GLASS\",\"colours\":{\"score\":\"#40FFFFFF\"}}");
        assertEquals(0x40, BoardColours.alpha(s.score));
    }

    @Test
    public void aNewerVersionIsReadForWhatThisOneKnows() {
        BoardSpec s = BoardSpec.parse("{\"v\":7,\"base\":\"CARD\",\"hologram\":{\"spin\":true},\"colours\":{\"panel\":\"#FFFFFF\"}}");
        assertEquals(BoardSpec.Base.CARD, s.base);
        assertEquals(0xFFFFFFFF, s.panel);
        assertEquals(BoardSpec.VERSION, s.json().optInt("v"));
    }

    @Test
    public void anEmptyFootIsOneColour() {
        BoardSpec s = BoardSpec.parse("{\"colours\":{\"panelFoot\":\"\"}}");
        assertEquals(0, s.panelFoot);
        assertEquals(0, s.groundFoot());
        // Frosted's frost falls deeper at its foot even with none set.
        s = BoardSpec.parse("{\"finish\":\"FROSTED\",\"colours\":{\"panelFoot\":\"\"}}");
        assertTrue(s.groundFoot() != 0);
        assertTrue(BoardColours.luminance(s.groundFoot()) < BoardColours.luminance(s.panel));
    }

    @Test
    public void namesAreShortAndPlain() {
        BoardSpec s = BoardSpec.parse("{\"name\":\"  A\\nvery   long name for a scoreboard that goes on and on and on  \"}");
        assertTrue(s.name.length() <= BoardSpec.NAME_MAX);
        assertFalse(s.name.contains("\n"));
        assertFalse(s.name.contains("  "));
        assertEquals("My scoreboard", BoardSpec.parse("{\"name\":\"   \"}").name);
    }

    @Test
    public void everyBaseStartsReadable() {
        for (BoardSpec.Base b : BoardSpec.Base.values()) {
            BoardSpec s = BoardSpec.of(b);
            assertEquals(b, s.base);
            assertTrue(b + " " + s.leastContrast(), s.leastContrast() >= BoardSpec.FLOOR);
        }
    }

    @Test
    public void eachColourSlotSetsAndReads() {
        BoardSpec s = new BoardSpec();
        for (String k : BoardSpec.EDITABLE) {
            s.colour(k, 0xFF123456);
            assertEquals(k, 0xFF123456, s.colour(k));
            assertFalse(BoardSpec.label(k).equals(k));
        }
    }

    @Test
    public void theStrapGetsCssColours() {
        JSONObject o = BoardSpec.parse(FULL).strapJson();
        assertEquals("#101c3a", o.optString("panel"));
        assertEquals("GLASS", o.optString("base"));
        assertTrue(o.optBoolean("sheen"));
        assertEquals("CBBarlowC", o.optString("font"));
        assertEquals("rgba(255,255,255,0.251)", BoardSpec.css(0x40FFFFFF));
    }

    @Test
    public void theSchemaRequiresEveryPropertyAndNoOthers() {
        JSONObject sc = BoardSpec.schema();
        assertEquals("object", sc.optString("type"));
        assertFalse(sc.optBoolean("additionalProperties", true));
        JSONObject props = sc.optJSONObject("properties");
        JSONArray req = sc.optJSONArray("required");
        assertEquals(props.length(), req.length());
        for (int i = 0; i < req.length(); i++) assertTrue(props.has(req.optString(i)));
        JSONObject colours = props.optJSONObject("colours");
        assertFalse(colours.optBoolean("additionalProperties", true));
        assertEquals(BoardSpec.COLOURS.length, colours.optJSONArray("required").length());
        JSONArray bases = props.optJSONObject("base").optJSONArray("enum");
        assertEquals(BoardSpec.Base.values().length, bases.length());
        assertEquals(BoardSpec.Font.values().length, props.optJSONObject("font").optJSONArray("enum").length());
        // No ranges: the schema has none of what structured outputs leaves out.
        String text = sc.toString();
        for (String k : Arrays.asList("minimum", "maximum", "minLength", "maxLength", "pattern")) assertFalse(k, text.contains("\"" + k + "\""));
        assertTrue(text.contains("\"panelFoot\""));
    }

    @Test
    public void aSpecFromTheSchemaShapeReadsBack() {
        // What Ask AI answers: every property there, as the schema has them.
        BoardSpec s = BoardSpec.parse(FULL);
        JSONObject back = s.json();
        JSONObject props = BoardSpec.schema().optJSONObject("properties");
        for (java.util.Iterator<String> it = props.keys(); it.hasNext(); ) assertTrue(back.has(it.next()));
        assertNull(back.optJSONObject("hologram"));
    }
}
