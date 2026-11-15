package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.graphics.Bitmap;

import com.gathin.broadcast.ShotMap;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** The shot map: FotMob's shots read (own goals for the side they count for), and drawn (a picture when PREVIEW_OUT is set). */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class ShotMapPreviewTest {
    private static JSONObject shot(int team, String type, double x, double y, double xg, int min, boolean own) throws Exception {
        return new JSONObject().put("teamId", team).put("eventType", type).put("x", x).put("y", y).put("expectedGoals", xg)
                .put("min", min).put("period", min <= 45 ? "FirstHalf" : "SecondHalf").put("isOwnGoal", own).put("lastName", "X");
    }

    @Test public void shotsAreReadAndCounted() throws Exception {
        JSONObject content = new JSONObject().put("shotmap", new JSONObject().put("shots", new JSONArray()
                .put(shot(1, "Goal", 95, 34, 0.5, 10, false))
                .put(shot(1, "AttemptSaved", 88, 30, 0.1, 30, false))
                .put(shot(2, "Miss", 80, 40, 0.05, 50, false))
                .put(shot(2, "Goal", 99, 34, 0.0, 70, true))));
        List<ShotMap.Shot> s = ShotMap.parse(content, 1);
        assertEquals(4, s.size());
        int[] home = ShotMap.count(s, true), away = ShotMap.count(s, false);
        assertEquals(3, home[0]);   // its two and the away side's own goal
        assertEquals(2, home[2]);
        assertEquals(1, away[0]);
        assertEquals(2, ShotMap.of(s, true).size());
        float[] at = ShotMap.place(s.get(2));
        assertTrue(at[0] < 0.5f);   // the away side shoots to the left
    }

    @Test public void drawn() throws Exception {
        String out = System.getProperty("preview.out", "");
        assumeTrue(!out.isEmpty() && new File(out, "match.json").isFile());
        JSONObject d = new JSONObject(new String(Files.readAllBytes(new File(out, "match.json").toPath()), StandardCharsets.UTF_8));
        int homeId = d.getJSONObject("general").getJSONObject("homeTeam").getInt("id");
        List<ShotMap.Shot> all = ShotMap.parse(d.getJSONObject("content"), homeId);
        Bitmap ft = ShotMap.draw(all, 560, 400, 0xF0101421, "Full time", "NOR", 0xFFD52B1E, "WAL", 0xFFE5231B);
        Bitmap ht = ShotMap.draw(ShotMap.of(all, true), 560, 400, 0xF0101421, "First half", "NOR", 0xFFD52B1E, "WAL", 0xFF1B8A3C);
        try (FileOutputStream f = new FileOutputStream(new File(out, "shotmap-ft.png"))) { ft.compress(Bitmap.CompressFormat.PNG, 100, f); }
        try (FileOutputStream f = new FileOutputStream(new File(out, "shotmap-ht.png"))) { ht.compress(Bitmap.CompressFormat.PNG, 100, f); }
    }
}
