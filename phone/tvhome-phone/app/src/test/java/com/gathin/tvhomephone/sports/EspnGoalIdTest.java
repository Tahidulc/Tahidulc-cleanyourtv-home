package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

import java.time.ZoneId;

/** A goal off ESPN's scoreboard keeps its scorer's ESPN id (his shirt number comes from the line-ups). Made-up game. */
public class EspnGoalIdTest {
    private static final String EVENT = "{\"id\":\"9100001\",\"date\":\"2026-11-14T15:00Z\",\"competitions\":[{"
            + "\"status\":{\"period\":2,\"displayClock\":\"60'\",\"clock\":3600,\"type\":{\"state\":\"in\",\"shortDetail\":\"60'\","
            + "\"name\":\"STATUS_SECOND_HALF\"}},"
            + "\"competitors\":["
            + "{\"homeAway\":\"home\",\"score\":\"1\",\"team\":{\"id\":\"9201\",\"abbreviation\":\"HLT\",\"displayName\":\"Hollins Town\","
            + "\"shortDisplayName\":\"Hollins\",\"color\":\"c8102e\",\"alternateColor\":\"ffffff\"}},"
            + "{\"homeAway\":\"away\",\"score\":\"1\",\"team\":{\"id\":\"9202\",\"abbreviation\":\"BRK\",\"displayName\":\"Brookfield Rovers\","
            + "\"shortDisplayName\":\"Brookfield\",\"color\":\"1b458f\",\"alternateColor\":\"ffffff\"}}],"
            + "\"details\":["
            + "{\"scoringPlay\":true,\"type\":{\"text\":\"Goal\"},\"clock\":{\"displayValue\":\"41'\"},\"team\":{\"id\":\"9201\"},"
            + "\"athletesInvolved\":[{\"id\":\"9001\",\"displayName\":\"Jay Hollis\"}]},"
            + "{\"scoringPlay\":true,\"penaltyKick\":true,\"type\":{\"text\":\"Penalty - Scored\"},\"clock\":{\"displayValue\":\"58'\"},"
            + "\"team\":{\"id\":\"9202\"},\"athletesInvolved\":[{\"displayName\":\"Sam Okafor\"}]}]}]}";

    @Test public void goalsKeepTheScorersId() throws Exception {
        Game g = Espn.parse(new JSONObject(EVENT), ZoneId.of("Europe/London"));
        assertEquals(2, g.goals.size());
        String[] first = g.goals.get(0), second = g.goals.get(1);
        assertEquals(6, first.length);
        assertEquals("Jay Hollis", first[1]);
        assertEquals("home", first[3]);
        assertEquals("", first[4]);
        assertEquals("9001", first[5]);
        // ESPN without his id: "" (no shirt for him)
        assertEquals("Sam Okafor", second[1]);
        assertEquals("away", second[3]);
        assertEquals("pen", second[4]);
        assertEquals("", second[5]);
    }
}
