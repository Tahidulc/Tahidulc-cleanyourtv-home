package com.gathin.tvhomephone.sports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

/** A club's schedule in one competition, and a game's video, as ESPN gives them. */
public class EspnScheduleTest {
    private static JSONObject event(String id, String date, String homeId, String homeScore, String awayId, Object awayScore, String state) throws Exception {
        JSONObject h = new JSONObject().put("homeAway", "home").put("id", homeId).put("team", new JSONObject().put("id", homeId).put("shortDisplayName", "H" + homeId))
                .put("score", new JSONObject().put("value", 1).put("displayValue", homeScore));
        JSONObject a = new JSONObject().put("homeAway", "away").put("id", awayId).put("team", new JSONObject().put("id", awayId).put("shortDisplayName", "A" + awayId))
                .put("score", awayScore);
        JSONObject comp = new JSONObject().put("competitors", new JSONArray().put(a).put(h))
                .put("status", new JSONObject().put("type", new JSONObject().put("state", state)));
        return new JSONObject().put("id", id).put("date", date).put("competitions", new JSONArray().put(comp));
    }

    @Test public void scheduleRowsOldestFirstHomeFirst() throws Exception {
        JSONObject root = new JSONObject().put("events", new JSONArray()
                .put(event("2", "2026-10-31T12:30Z", "363", "2", "360", "0", "post"))
                .put(event("1", "2026-10-25T14:00Z", "360", "0", "349", "3", "post")));
        List<String[]> s = Espn.schedule(root);
        assertEquals(2, s.size());
        assertEquals("1", s.get(0)[Espn.S_ID]);
        assertEquals("360", s.get(0)[Espn.S_HOME]);
        assertEquals("0", s.get(0)[Espn.S_HOME_GOALS]);
        assertEquals("3", s.get(0)[Espn.S_AWAY_GOALS]);
        assertEquals("H360", s.get(0)[Espn.S_HOME_NAME]);
        assertEquals("post", s.get(1)[Espn.S_STATE]);
    }

    @Test public void aGamesVideo() throws Exception {
        JSONObject v = new JSONObject().put("headline", "The winner").put("thumbnail", "https://a.example/t.jpg")
                .put("links", new JSONObject().put("source", new JSONObject().put("HD", new JSONObject().put("href", "https://media.example/v.mp4"))));
        String[] got = Espn.video(new JSONObject().put("videos", new JSONArray().put(v)));
        assertEquals("https://media.example/v.mp4", got[0]);
        assertEquals("https://a.example/t.jpg", got[1]);
        assertNull(Espn.video(new JSONObject()));
    }
}
