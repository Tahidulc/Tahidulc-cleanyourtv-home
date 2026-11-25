package com.gathin.broadcast;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The scoreboard's own short sounds (broadcast/sfx/NAME.ogg in the shared bundle), played by the app itself, outside the
 * overlays' pages: the line-ups' tab swish as anything slides out beside the board (the scorer, the stats, the alerts),
 * the goal's sound over the board's goal animation, and the family's swish as a goal's new score comes out. Loaded once
 * and kept; played on the media stream, so the TV's or phone's volume sets them with the channel. The same sound is
 * never started twice within a quarter of a second.
 */
public final class Sfx {
    private Sfx() { }

    private static final String TAG = "Sfx";
    /** The line-ups' tab swish. */
    public static final String SWISH = "swish-tab";
    /** The family's own swish (a stock sound they picked), as a goal's new score comes out from under its band. */
    public static final String SCORE = "score-swish";
    /** Its loudest moment, in ms from its start: put on the new score's pop. */
    public static final long SCORE_PEAK_MS = 500;
    /**
     * The Scoreboard Lab moments' own sounds (overlays/tools/lab_synth.py, made from maths, nobody's audio): the net
     * shivering after a goal, the scorer's crest bursting, the score rolling up like an odometer, the new digit
     * flipping in, the red card swinging up, VAR checking, and a substitution's two players crossing over.
     */
    public static final String NET = "lab-net", BURST = "lab-burst", ROLL = "lab-roll", FLIP = "lab-flip", CARD = "lab-card",
            VAR = "lab-var", SWAP = "lab-swap";
    /** Every Lab sound at once, to warm them ahead of their first moment. */
    public static final String[] LAB = {NET, BURST, ROLL, FLIP, CARD, VAR, SWAP};
    /**
     * How loud the Lab's sounds are played: under the tab swish (0.7), and each is made a few dB quieter than it too, so
     * they sit under the commentary and never get in the way.
     */
    public static final float QUIET = 0.6f;
    /**
     * The goal sounds over the board's goal animation (goal-KEY.ogg), and their names, in the order offered. "swoosh" is
     * the family's own pick (a stock swoosh they supplied), cut so its loudest moment lands as the GOAL band does; the
     * last six (tools/reveal_goal_synth.py) are timed to the new goal animations' stages (GoalReveal: the sweep, the
     * crest's turn, the motif, GOAL!'s burst, the sweep off), the rest (tools/goal_synth.py) to the GOAL band's.
     */
    public static final String[] GOALS = {"off", "swoosh", "roar", "hit", "horn", "swish", "drums", "stab", "boom", "rumble", "sting",
            "ting", "brass", "crowd", "riser", "bells", "thunder"};
    public static final String[] GOAL_NAMES = {"Off", "Your swoosh", "Stadium roar", "Broadcast hit", "Goal horn", "Swish and chime",
            "Drum hit", "Synth stab", "Broadcast boom", "Swipe and rumble", "Score sting", "Whoosh and ting", "Brass swell",
            "Crowd rise", "Riser and drop", "Bells", "Thunder and sparkle"};
    /** The goal sounds made for the new goal animations (GoalReveal), timed to their stages. */
    public static final String[] REVEAL_GOALS = {"ting", "brass", "crowd", "riser", "bells", "thunder"};
    /** The goal sound unless another is chosen: the family's swoosh. */
    public static final String GOAL_DEFAULT = "swoosh";
    /** Its loudest moment, in ms from its start (it starts with the goal): the GOAL band landing across the board. */
    public static final long SWOOSH_PEAK_MS = 650;

    /** Pure: the goal sound after this one (off, then each in turn). */
    public static String nextGoal(String key) {
        for (int i = 0; i < GOALS.length; i++) if (GOALS[i].equals(key)) return GOALS[(i + 1) % GOALS.length];
        return GOALS[0];
    }

    /** Pure: the goal sound before this one (round from off to the last). */
    public static String previousGoal(String key) {
        for (int i = 0; i < GOALS.length; i++) if (GOALS[i].equals(key)) return GOALS[(i - 1 + GOALS.length) % GOALS.length];
        return GOALS[0];
    }

    /** Pure: a goal sound's name as offered ("Off" for none or one not known). */
    public static String goalName(String key) {
        for (int i = 0; i < GOALS.length; i++) if (GOALS[i].equals(key)) return GOAL_NAMES[i];
        return GOAL_NAMES[0];
    }

    /** The goal sound key over the board's goal animation now ("off": none). */
    public static void goal(Context c, String key) {
        if (key == null || "off".equals(key)) return;
        play(c, "goal-" + key, 0f, 0.9f);
    }

    private static SoundPool pool;
    private static final Map<String, Integer> ids = new HashMap<>();
    private static final Set<Integer> loaded = new HashSet<>();
    private static final Map<String, Long> lastAt = new HashMap<>();

    /** These loaded now, ahead of their first moment (a sound played while it still loads is lost). Main thread. */
    public static synchronized void warm(Context c, String... names) {
        if (pool == null) {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            pool.setOnLoadCompleteListener((p, id, status) -> {
                synchronized (Sfx.class) {
                    if (status == 0) loaded.add(id);
                }
            });
        }
        for (String n : names) {
            if (n == null || n.isEmpty() || ids.containsKey(n)) continue;
            try (AssetFileDescriptor fd = c.getApplicationContext().getAssets().openFd("broadcast/sfx/" + n + ".ogg")) {
                ids.put(n, pool.load(fd, 1));
            } catch (Exception e) {
                ids.put(n, -1);
                Log.w(TAG, "no " + n + ": " + e);
            }
        }
    }

    /** name now, pan -1 (left) to 1 (right), gain 0 to 1. Returns whether it played (false the first time it is asked for). */
    public static synchronized boolean play(Context c, String name, float pan, float gain) {
        warm(c, name);
        Integer id = ids.get(name);
        if (id == null || id < 0 || !loaded.contains(id)) return false;
        long now = SystemClock.uptimeMillis();
        Long was = lastAt.get(name);
        if (was != null && now - was < 250) return false;
        lastAt.put(name, now);
        float p = Math.max(-1f, Math.min(1f, pan)), g = Math.max(0f, Math.min(1f, gain));
        pool.play(id, g * Math.min(1f, 1f - p), g * Math.min(1f, 1f + p), 1, 0, 1f);
        return true;
    }
}
