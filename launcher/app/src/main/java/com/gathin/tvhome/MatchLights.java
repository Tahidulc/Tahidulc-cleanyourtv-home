package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Match mode's lights, through Tuya's cloud (TuyaCloud): down to a level at kick-off and back up at half time and full
 * time; at a goal for the club cheered, every light that is on flashes in its colours (a colour bulb) or bright and
 * dim (a white one) for a few seconds and goes back to just how it was; the other side's goal dims them a moment, a
 * red card is one red pulse. Lights that are off stay off. One thing at a time, off the main thread.
 */
final class MatchLights {
    private MatchLights() { }

    /** A goal's flashes, and how long each colour shows. */
    static final int FLASHES = 6;
    static final long STEP_MS = 650L;
    private static final ExecutorService one = Executors.newSingleThreadExecutor();
    /** Each light's level before kick-off's dimming, put back at half time and full time (this thread only). */
    private static final Map<String, Integer> before = new HashMap<>();
    private static List<TuyaCloud.Light> lights;
    private static long readAt;

    static boolean available(Context c) {
        return TuyaCloud.configured(c);
    }

    /** The account's lights, read again when ten minutes old (this thread only). */
    private static List<TuyaCloud.Light> lights(Context c) {
        long now = System.currentTimeMillis();
        if (lights == null || now - readAt > 10 * 60_000L) {
            try {
                lights = TuyaCloud.lights(c);
                readAt = now;
            } catch (Exception e) {
                AlertLog.note(c, "Match mode lights: Tuya couldn't be read (" + e.getClass().getSimpleName() + ")");
                if (lights == null) lights = new ArrayList<>();
            }
        }
        return lights;
    }

    /** The lights that are on now, each read afresh. */
    private static List<TuyaCloud.Light> lit(Context c) {
        List<TuyaCloud.Light> out = new ArrayList<>();
        for (TuyaCloud.Light l : lights(c)) {
            try {
                TuyaCloud.refresh(c, l);
            } catch (Exception e) {
                continue;
            }
            if (l.online && l.on) out.add(l);
        }
        return out;
    }

    /** Kick-off: every light that is on down to percent, its level kept to put back. */
    static void dim(Context c, int percent) {
        Context app = c.getApplicationContext();
        one.execute(() -> {
            int n = 0;
            for (TuyaCloud.Light l : lit(app)) {
                if (l.brightCode == null) continue;
                if (!before.containsKey(l.id)) before.put(l.id, l.level);
                if (TuyaCloud.level(app, l, percent)) n++;
            }
            AlertLog.note(app, "Match mode: " + n + " lights down to " + percent + "%");
        });
    }

    /** Half time and full time: the lights dimmed at kick-off back to where they were. */
    static void restore(Context c) {
        Context app = c.getApplicationContext();
        one.execute(() -> {
            if (before.isEmpty()) return;
            for (TuyaCloud.Light l : lights(app)) {
                Integer lv = before.remove(l.id);
                if (lv != null && lv > 0) TuyaCloud.level(app, l, lv);
            }
            before.clear();
            AlertLog.note(app, "Match mode: lights back up");
        });
    }

    /** A goal for the club cheered: the lights that are on flash in its colours (or bright and dim), then as they were. */
    static void celebrate(Context c, int[] colours) {
        Context app = c.getApplicationContext();
        one.execute(() -> {
            List<TuyaCloud.Light> on = lit(app);
            List<JSONArray> looks = new ArrayList<>();
            List<Integer> levels = new ArrayList<>();
            for (TuyaCloud.Light l : on) {
                looks.add(TuyaCloud.look(app, l));
                levels.add(l.level);
            }
            for (int step = 0; step < FLASHES; step++) {
                for (TuyaCloud.Light l : on) {
                    if (l.colourCode != null) TuyaCloud.colour(app, l, colours[step % colours.length]);
                    else if (l.brightCode != null) TuyaCloud.level(app, l, step % 2 == 0 ? 100 : 15);
                }
                if (!sleep(STEP_MS)) break;
            }
            putBack(app, on, looks, levels);
            AlertLog.note(app, "Match mode: goal lights on " + on.size() + " lights");
        });
    }

    /** A red card: one red pulse (a white light: a dip); the other side's goal (red false): a dip of a few seconds. */
    static void pulse(Context c, boolean red) {
        Context app = c.getApplicationContext();
        one.execute(() -> {
            List<TuyaCloud.Light> on = lit(app);
            List<JSONArray> looks = new ArrayList<>();
            List<Integer> levels = new ArrayList<>();
            for (TuyaCloud.Light l : on) {
                looks.add(TuyaCloud.look(app, l));
                levels.add(l.level);
            }
            for (TuyaCloud.Light l : on) {
                if (red && l.colourCode != null) TuyaCloud.colour(app, l, 0xFFFF2020);
                else if (l.brightCode != null) TuyaCloud.level(app, l, 10);
            }
            sleep(red ? 1_500L : 4_000L);
            putBack(app, on, looks, levels);
        });
    }

    private static void putBack(Context app, List<TuyaCloud.Light> on, List<JSONArray> looks, List<Integer> levels) {
        for (int i = 0; i < on.size(); i++) {
            TuyaCloud.Light l = on.get(i);
            JSONArray look = looks.get(i);
            if (look.length() > 0 && TuyaCloud.send(app, l, look)) continue;
            int lv = levels.get(i);
            if (lv > 0) TuyaCloud.level(app, l, lv);
        }
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
