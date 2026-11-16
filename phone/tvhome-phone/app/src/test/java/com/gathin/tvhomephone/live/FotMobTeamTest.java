package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** A club's FotMob id found on FotMob's day of matches by one of its games (made-up clubs). */
public class FotMobTeamTest {
    private static JSONArray day() throws Exception {
        JSONObject game = new JSONObject().put("id", 880011)
                .put("home", new JSONObject().put("id", 5001).put("name", "Harbour City").put("longName", "Harbour City Herons"))
                .put("away", new JSONObject().put("id", 5003).put("name", "Ashcombe").put("longName", "Ashcombe Rovers"));
        JSONObject other = new JSONObject().put("id", 880012)
                .put("home", new JSONObject().put("id", 5007).put("name", "Kingsmoor Town"))
                .put("away", new JSONObject().put("id", 5008).put("name", "Port Albany"));
        return new JSONArray().put(new JSONObject().put("matches", new JSONArray().put(other).put(game)));
    }

    @Test public void eitherClubByItsSide() throws Exception {
        assertEquals(5001, FotMob.sideIn(day(), "Harbour City Herons", "Ashcombe Rovers", true));
        assertEquals(5003, FotMob.sideIn(day(), "Harbour City Herons", "Ashcombe Rovers", false));
        assertEquals(5008, FotMob.sideIn(day(), "Kingsmoor Town", "Port Albany", false));
        assertEquals(880011, FotMob.matchIn(day(), "Harbour City Herons", "Ashcombe Rovers"));
    }

    @Test public void noneWhenTheGameIsNotThatDay() throws Exception {
        assertEquals(0, FotMob.sideIn(day(), "Fenwick Vale", "Ashcombe Rovers", true));
        assertEquals(0, FotMob.sideIn(null, "Harbour City Herons", "Ashcombe Rovers", true));
        assertEquals(0, FotMob.teamId(null, "Ashcombe Rovers", 1L, true));
    }
}
