package com.gathin.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Make my own logo: the design spec read safely, the offline generator's logos, and Ask AI's schema and prompt. */
public class LogoSpecTest {
    @Test
    public void aSpecIsReadAndWrittenTheSame() {
        String j = "{\"v\":1,\"text\":\"HARBOUR\",\"sub\":\"SPORTS\",\"layout\":\"BADGE\",\"font\":\"MONTSERRAT\","
                + "\"colours\":{\"ground\":\"#0B3D2E\",\"ink\":\"#FFFFFF\",\"accent\":\"#3DDC84\",\"sub\":\"#C8F5DA\"},"
                + "\"shape\":\"ROUNDED\",\"mark\":\"STAR\",\"markSide\":\"LEFT\",\"italic\":false,\"outline\":false}";
        LogoSpec s = LogoSpec.parse(j);
        assertEquals("HARBOUR", s.text);
        assertEquals("SPORTS", s.sub);
        assertEquals(LogoSpec.Layout.BADGE, s.layout);
        assertEquals(LogoSpec.Mark.STAR, s.mark);
        assertEquals(0xFF0B3D2E, s.ground);
        assertEquals(s.toJson(), LogoSpec.parse(s.toJson()).toJson());
    }

    @Test
    public void badOrMissingIsSafe() {
        for (String bad : new String[]{null, "", "{", "[]", "{\"layout\":\"HOLO\",\"mark\":\"DRAGON\",\"text\":\"\"}"}) {
            LogoSpec s = LogoSpec.parse(bad);
            assertFalse(s.text.isEmpty());
            assertEquals(LogoSpec.Mark.NONE, s.mark);
        }
    }

    @Test
    public void theWordsAreShortAndPlain() {
        LogoSpec s = LogoSpec.parse("{\"text\":\"<script>Harbour Sports Network Extra Long</script>\",\"sub\":\"\\u0007HD\"}");
        assertTrue(s.text.length() <= LogoSpec.TEXT_MAX);
        assertFalse(s.text.contains("<"));
        assertEquals("HD", s.sub);
    }

    @Test
    public void wordsReadOnTheirGroundOrOnADarkTile() {
        LogoSpec s = LogoSpec.parse("{\"colours\":{\"ground\":\"#FFFFFF\",\"ink\":\"#FAFAFA\"},\"shape\":\"ROUNDED\"}");
        assertTrue(BoardColours.contrast(s.ink, s.ground) >= 4.5);
        s = LogoSpec.parse("{\"colours\":{\"ground\":\"\",\"ink\":\"#101010\"},\"shape\":\"NONE\"}");
        assertEquals(0, s.ground);
        assertTrue(BoardColours.contrast(s.ink, s.under()) >= 4.5);
    }

    @Test
    public void theGeneratorMakesSeveralFromAChannelsName() {
        List<LogoSpec> l = LogoDesigner.suggest("UK: Harbour Sports Main Event FHD", "UK Sports", 6, 0);
        assertEquals(6, l.size());
        Set<String> seen = new HashSet<>();
        Set<LogoSpec.Layout> layouts = new HashSet<>();
        for (LogoSpec s : l) {
            assertTrue(seen.add(s.toJson()));
            layouts.add(s.layout);
            assertTrue(BoardColours.contrast(s.ink, s.under()) >= 4.5);
        }
        assertTrue(layouts.size() >= 4);
        assertEquals("HSM", LogoDesigner.initials("Harbour Sports Main Event"));
        assertEquals("Harbour Sports Main Event", LogoDesigner.plain("UK: Harbour Sports Main Event FHD"));
        assertFalse(LogoDesigner.suggest("Millbrook One", "", 4, 1).get(0).toJson()
                .equals(LogoDesigner.suggest("Millbrook One", "", 4, 0).get(0).toJson()));
    }

    @Test
    public void theSchemaRequiresEveryPropertyAndNoOthers() {
        JSONObject sc = LogoSpec.schema();
        JSONObject props = sc.optJSONObject("properties");
        JSONArray req = sc.optJSONArray("required");
        assertEquals(props.length(), req.length());
        assertFalse(sc.optBoolean("additionalProperties", true));
        assertFalse(props.optJSONObject("colours").optBoolean("additionalProperties", true));
        assertEquals(LogoSpec.Mark.values().length, props.optJSONObject("mark").optJSONArray("enum").length());
        assertFalse(sc.toString().contains("\"maximum\""));
    }

    @Test
    public void askAiSendsTheChannelAndTheWordsOnly() {
        String u = AskAi.logoUser("make it look like a classic sports channel", "UK: Harbour Sports FHD", "UK Sports");
        assertTrue(u.contains("Harbour Sports"));
        assertTrue(u.contains("classic sports channel"));
        assertFalse(u.contains("FHD"));
        assertTrue(AskAi.logoSystem().contains("LAYOUT") || AskAi.logoSystem().contains("layout"));
    }
}
