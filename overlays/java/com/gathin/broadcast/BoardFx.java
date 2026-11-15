package com.gathin.broadcast;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Scoreboard Lab's animations, the same on the TV's board and the phone's: when each comes and how far it is (pure,
 * for the tests), and how it is drawn over the board (an instance per board, its paints kept, nothing made per frame).
 *
 * <ul>
 *   <li>A goal, in one of three sets a game keeps all through (pick): today's broadcast band; the Lab's flip (the
 *   scorers' side washes in their colour, a shine sweeps the board, the new figure turns down into place); or the Lab's
 *   burst (the scorers' crest pops with rings in their colour, the score rolls up, their side glows). Every goal ends
 *   with the net shivering across the board (netMesh).</li>
 *   <li>A red card swinging up under the board and tucking away; VAR's dot pulsing on its check; the substitution
 *   card's players crossing over with their green and red arrows; half time's face; a shootout's dots in the clock's
 *   place; the momentum wave once or twice a half; the ground's weather two or three times a game.</li>
 * </ul>
 */
public final class BoardFx {
    // ---- a game's own choices ---------------------------------------------------------------------------------------

    /**
     * Pure: one of n for a game's moment, the same every time for that game (on the TV and the phone, after a restart):
     * its id and the moment's name hashed and mixed, so games spread evenly over the n.
     */
    public static int pick(String gameId, String moment, int n) {
        if (n <= 1) return 0;
        int h = ((gameId == null ? "" : gameId) + "|" + (moment == null ? "" : moment)).hashCode();
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return Math.floorMod(h, n);
    }

    /** A goal's sets: today's band, the Lab's flip, the Lab's burst. */
    public static final int GOAL_BAND = 0, GOAL_FLIP = 1, GOAL_BURST = 2;

    /** Pure: the goal set a game keeps for all its goals. */
    public static int goalSet(String gameId) {
        return pick(gameId, "goal", 3);
    }

    /** Pure: whether a game's half time shows the score turned over (each figure in its own small block) after the board turns. */
    public static boolean htFace(String gameId) {
        return pick(gameId, "ht", 2) == 1;
    }

    /** Pure: whether a game's shootout dots stand in the clock's place on the board (else today's strip under it). */
    public static boolean pensInClock(String gameId) {
        return pick(gameId, "pens", 2) == 1;
    }

    // ---- the goal's sets: timings ------------------------------------------------------------------------------------

    /** The flip set, in ms from the goal: the side's wash, the shine, the new figure turning in; FLIP_MS in all. */
    public static final long FLIP_MS = 2600, WASH_MS = 1800, SHINE_MS = 1100, FLIP_FROM = 150, FLIP_TO = 850;
    /** The burst set, in ms from the goal: the rings, the crest's pop, the score's roll, the side's glow; BURST_MS in all. */
    public static final long BURST_MS = 2400, RINGS_MS = 2000, RING_GAP = 300, RING_MS = 1400, POP_MS = 600, ROLL_FROM = 500,
            ROLL_TO = 1300, GLOW_FROM = 300, GLOW_TO = 1800;
    /** When the net shivers in each set, from the goal (the band's sets after their band, as the boards have it). */
    public static final long MESH_AFTER_FLIP = 1200, MESH_AFTER_BURST = 1300;
    /** The net's shiver itself: rising to its height, swelling, fading; MESH_MS long. */
    public static final long MESH_MS = 1100, MESH_PEAK = 260, MESH_SETTLE = 660;

    /** Pure: how far the flip's new figure is on its way (0 before FLIP_FROM, 1 from FLIP_TO), for CompBug's moveAt. */
    public static float flipMove(long t) {
        return span(t, FLIP_FROM, FLIP_TO);
    }

    /** Pure: how far the burst's roll is on its way (0 before ROLL_FROM, 1 from ROLL_TO), for CompBug's moveAt. */
    public static float rollMove(long t) {
        return span(t, ROLL_FROM, ROLL_TO);
    }

    /** Pure: the scorers' side's wash t ms into the flip: up to 0.75 a fifth of the way in, gone by WASH_MS. */
    public static float washAlpha(long t) {
        if (t <= 0 || t >= WASH_MS) return 0f;
        float x = t / (float) WASH_MS;
        return x < 0.2f ? 0.75f * ease(x / 0.2f) : 0.75f * (1f - ease((x - 0.2f) / 0.8f));
    }

    /** Pure: the scorers' side's glow t ms into the burst: up to 0.6 quickly, then fading out by GLOW_TO. */
    public static float glowAlpha(long t) {
        if (t <= GLOW_FROM || t >= GLOW_TO) return 0f;
        float x = (t - GLOW_FROM) / (float) (GLOW_TO - GLOW_FROM);
        return x < 0.25f ? 0.6f * ease(x / 0.25f) : 0.6f * (1f - (x - 0.25f) / 0.75f);
    }

    /** Pure: where the shine is t ms into it, as a share of the board's width (-1.2 to 1.2 over SHINE_MS); NaN when not on. */
    public static float shineAt(long t) {
        if (t < 0 || t >= SHINE_MS) return Float.NaN;
        return -1.2f + 2.4f * ease(t / (float) SHINE_MS);
    }

    /** Pure: the net's strength t ms into its shiver (0 to 0.9 and back to 0), as the Lab's net. */
    public static float meshAlpha(long t) {
        if (t <= 0 || t >= MESH_MS) return 0f;
        if (t < MESH_PEAK) return 0.9f * ease(t / (float) MESH_PEAK);
        if (t < MESH_SETTLE) return 0.9f - 0.6f * ease((t - MESH_PEAK) / (float) (MESH_SETTLE - MESH_PEAK));
        return 0.3f * (1f - ease((t - MESH_SETTLE) / (float) (MESH_MS - MESH_SETTLE)));
    }

    /** Pure: the net's swell t ms into its shiver, {across, up}: to 1.05 by 1.3 at its height, back to 1 as it settles. */
    public static float[] meshSwell(long t) {
        float k = swell(t);
        return new float[]{1f + 0.05f * k, 1f + 0.3f * k};
    }

    /** How far into its swell the net is (0 to 1 and back). */
    private static float swell(long t) {
        if (t <= 0 || t >= MESH_SETTLE) return 0f;
        if (t < MESH_PEAK) return ease(t / (float) MESH_PEAK);
        return 1f - ease((t - MESH_PEAK) / (float) (MESH_SETTLE - MESH_PEAK));
    }

    /** Pure: ring (0 or 1) of the burst t ms in, {scale of its radius 0.4 to 5, its strength 1 to 0}; null when not out. */
    public static float[] ringAt(long t, int ring) {
        float e = ringOut(t, ring);
        return Float.isNaN(e) ? null : new float[]{0.4f + 4.6f * e, 1f - e};
    }

    /** How far out a ring is (0 to 1), NaN when it is not out. */
    private static float ringOut(long t, int ring) {
        long r = t - ring * RING_GAP;
        if (r < 0 || r >= RING_MS) return Float.NaN;
        float x = 1f - r / (float) RING_MS;
        return 1f - x * x * x;
    }

    /** Pure: the burst's crest t ms in, as a share of its size: up to 1.35 an eighth of the way, back to 1 by POP_MS. */
    public static float popAt(long t) {
        if (t <= 0 || t >= POP_MS) return 1f;
        float x = t / (float) POP_MS;
        return x < 0.25f ? 1f + 0.35f * ease(x / 0.25f) : 1f + 0.35f * (1f - ease((x - 0.25f) / 0.75f));
    }

    // ---- red card, VAR, the substitution's swap ------------------------------------------------------------------

    /** The red card's swing: up over RED_UP, held, tucked away from RED_AWAY; RED_MS in all. */
    public static final long RED_MS = 1600, RED_UP = 400, RED_AWAY = 1200;

    /**
     * Pure: the red card's pose t ms in, {how far below its place in its own heights (2 hidden, 0 in place), its turn in
     * degrees about its foot (-30 swung back, 8 in place)}.
     */
    public static float[] redCardPose(long t) {
        if (t <= 0 || t >= RED_MS) return new float[]{2f, -30f};
        float k;
        if (t < RED_UP) {
            float x = t / (float) RED_UP, c1 = 1.4f, c3 = c1 + 1f, u = x - 1f;
            k = 1f + c3 * u * u * u + c1 * u * u;
        } else if (t < RED_AWAY) {
            k = 1f;
        } else {
            float x = (t - RED_AWAY) / (float) (RED_MS - RED_AWAY);
            k = 1f - x * x;
        }
        return new float[]{2f * (1f - k), -30f + 38f * k};
    }

    /** Pure: VAR's dot now, {its scale 1 to 0.55, its strength 1 to 0.4}, a second's pulse. */
    public static float[] varPulse(long now) {
        float k = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * Math.floorMod(now, 1000L) / 1000.0);
        return new float[]{1f - 0.45f * k, 1f - 0.6f * k};
    }

    /** The substitution card's cross-over, as the Lab's: SWAP_MS round, the player off's line SWAP_LAG after the player on's. */
    public static final long SWAP_MS = 2600, SWAP_LAG = 1300;
    /** swapAt's figures, by place: the player off's strength and slide, the player on's, then each one's arrow's pop. */
    public static final int SW_OUT_ALPHA = 0, SW_OUT_SHIFT = 1, SW_IN_ALPHA = 2, SW_IN_SHIFT = 3, SW_IN_POP = 4, SW_OUT_POP = 5;

    /**
     * Pure: the substitution card's two players t ms after it opened, as the Lab's swap: each in turn brightens from 0.35
     * to full and slides the last third of an em into place, holds, and dims back, the player off SWAP_LAG after the
     * player on (sitting at 0.4, still, until his turn comes). {off's strength, off's slide (in ems, negative: to the
     * left), on's strength, on's slide, on's arrow's pop (its size, 1 to 1.25), off's arrow's pop}.
     */
    public static float[] swapAt(long t) {
        float[] in = swapLine(t), out = t < SWAP_LAG ? new float[]{0.4f, 0f, 1f} : swapLine(t - SWAP_LAG);
        return new float[]{out[0], out[1], in[0], in[1], in[2], out[2]};
    }

    /** One line's {strength, slide, arrow's pop} p ms into its round. */
    private static float[] swapLine(long p) {
        float x = Math.floorMod(p, SWAP_MS) / (float) SWAP_MS, k;
        if (x < 0.3f) k = ease(x / 0.3f);
        else if (x < 0.6f) k = 1f;
        else k = 1f - ease((x - 0.6f) / 0.4f);
        float pop = x < 0.3f ? 1f + 0.25f * (float) Math.sin(Math.PI * x / 0.3f) : 1f;
        return new float[]{0.35f + 0.65f * k, -0.33f * (1f - k), pop};
    }

    private static final Paint ARROW = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path ARROW_PATH = new Path();
    /** The arrows' colours: the player on's green, the player off's red. */
    public static final int ON_GREEN = 0xFF3DDC84, OFF_RED = 0xFFFF6B6B;

    /**
     * The substitution's arrow about (cx, cy), size high: ▲ in green for the player coming on, ▼ in red for the player
     * going off, at alpha (0 to 1); drawn as a shape, its corners a little round.
     */
    public static void swapArrow(Canvas cv, float cx, float cy, float size, boolean on, float alpha) {
        if (cv == null || size <= 0f || alpha <= 0f) return;
        synchronized (ARROW) {
            float half = size / 2f, w = size * 0.56f;
            ARROW_PATH.reset();
            if (on) {
                ARROW_PATH.moveTo(cx, cy - half);
                ARROW_PATH.lineTo(cx + w, cy + half);
                ARROW_PATH.lineTo(cx - w, cy + half);
            } else {
                ARROW_PATH.moveTo(cx - w, cy - half);
                ARROW_PATH.lineTo(cx + w, cy - half);
                ARROW_PATH.lineTo(cx, cy + half);
            }
            ARROW_PATH.close();
            ARROW.setStyle(Paint.Style.FILL_AND_STROKE);
            ARROW.setStrokeJoin(Paint.Join.ROUND);
            ARROW.setStrokeWidth(Math.max(1f, size * 0.12f));
            int c = on ? ON_GREEN : OFF_RED;
            ARROW.setColor(c);
            ARROW.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
            cv.drawPath(ARROW_PATH, ARROW);
        }
    }

    // ---- the momentum wave: once or twice a half ---------------------------------------------------------------------

    /** How long the wave is out under the board; drawn in over WAVE_IN, fading out over its last WAVE_OUT. */
    public static final long WAVE_MS = 7000, WAVE_IN = 2000, WAVE_OUT = 600;

    /**
     * Pure: the minutes a game's wave comes out, stable for that game: in the first half one in 20 to 28 and, when the game
     * picks a second, one in 34 to 40; in the second half one in 58 to 66 and maybe one in 74 to 82. Two to four, in order.
     */
    public static int[] waveMinutes(String gameId) {
        List<Integer> m = new ArrayList<>();
        m.add(20 + pick(gameId, "wave1", 9));
        if (pick(gameId, "wave1+", 2) == 1) m.add(34 + pick(gameId, "wave1b", 7));
        m.add(58 + pick(gameId, "wave2", 9));
        if (pick(gameId, "wave2+", 2) == 1) m.add(74 + pick(gameId, "wave2b", 9));
        int[] out = new int[m.size()];
        for (int i = 0; i < out.length; i++) out[i] = m.get(i);
        return out;
    }

    /**
     * Pure (but for shown, which it adds to): the wave due at this minute of the game ("wave24": its slot by its minute),
     * or null: each slot once a game, up to three minutes after its own (a board that came up late shows it then).
     */
    public static String waveDue(String gameId, int minute, Set<String> shown) {
        return due("wave", waveMinutes(gameId), gameId, minute, shown, 3);
    }

    /** Pure: how much of the wave is drawn t ms in (0 to 1 over WAVE_IN). */
    public static float waveDrawn(long t) {
        return ease(span(t, 0, WAVE_IN));
    }

    /** Pure: the wave's strength t ms in: quickly in, held, out over its last WAVE_OUT. */
    public static float waveAlpha(long t) {
        if (t <= 0 || t >= WAVE_MS) return 0f;
        if (t < 300) return t / 300f;
        if (t > WAVE_MS - WAVE_OUT) return (WAVE_MS - t) / (float) WAVE_OUT;
        return 1f;
    }

    /** Pure: the momentum's values {minute, value} from span minutes before now up to now. */
    public static List<int[]> lastMinutes(List<int[]> all, int now, int span) {
        List<int[]> out = new ArrayList<>();
        if (all == null) return out;
        for (int[] v : all) if (v != null && v.length >= 2 && v[0] <= now && v[0] >= now - span) out.add(v);
        return out;
    }

    /** Pure: which side the values say is on top: 1 home, 2 away, 0 neither (their mean within 8 of level). */
    public static int onTop(List<int[]> values) {
        if (values == null || values.isEmpty()) return 0;
        double sum = 0;
        for (int[] v : values) sum += v[1];
        double mean = sum / values.size();
        return mean > 8 ? 1 : mean < -8 ? 2 : 0;
    }

    // ---- the ground's weather: two or three times a game -----------------------------------------------------------------

    /** The skies the touch draws. */
    public enum Sky { CLEAR_DAY, CLEAR_NIGHT, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM, WIND }

    /** How long the touch is over the board; in and out over WEATHER_FADE. */
    public static final long WEATHER_MS = 7000, WEATHER_FADE = 800;

    /**
     * Pure: the sky from FotMob's weather at the ground (its weather.com icon code, the rain and snow falling, the wind in
     * km/h, the cloud in %): falling snow is snow and falling rain at least drizzle, a strong dry wind is wind; else by its
     * icon.
     */
    public static Sky sky(int iconCode, double precipitation, double snow, double windKmh, int cloudCover) {
        Sky s = iconSky(iconCode, cloudCover);
        if (snow > 0) return Sky.SNOW;
        if (precipitation > 0 && (s == Sky.CLEAR_DAY || s == Sky.CLEAR_NIGHT || s == Sky.CLOUDY || s == Sky.FOG || s == Sky.WIND)) {
            return precipitation >= 1.0 ? Sky.RAIN : Sky.DRIZZLE;
        }
        if (windKmh >= 40 && (s == Sky.CLEAR_DAY || s == Sky.CLEAR_NIGHT || s == Sky.CLOUDY)) return Sky.WIND;
        return s;
    }

    private static Sky iconSky(int code, int cloudCover) {
        switch (code) {
            case 3: case 4: case 37: case 38: case 47: return Sky.STORM;
            case 5: case 6: case 7: case 10: case 11: case 12: case 17: case 18: case 35: case 39: case 40: case 45: return Sky.RAIN;
            case 8: case 9: return Sky.DRIZZLE;
            case 13: case 14: case 15: case 16: case 41: case 42: case 43: case 46: return Sky.SNOW;
            case 19: case 20: case 21: case 22: return Sky.FOG;
            case 23: case 24: return Sky.WIND;
            case 26: case 27: case 28: case 29: case 30: return Sky.CLOUDY;
            case 31: case 33: return Sky.CLEAR_NIGHT;
            case 32: case 34: case 36: return Sky.CLEAR_DAY;
            default: return cloudCover >= 60 ? Sky.CLOUDY : Sky.CLEAR_DAY;
        }
    }

    /** Pure: the sky from Open-Meteo's WMO weather code (the TV's fallback), by day or night, the wind in mph. */
    public static Sky skyOfWmo(int code, boolean day, double windMph) {
        Sky s;
        if (code >= 95) s = Sky.STORM;
        else if (code >= 85 && code <= 86 || code >= 71 && code <= 77) s = Sky.SNOW;
        else if (code >= 80 || code >= 61 && code <= 67) s = Sky.RAIN;
        else if (code >= 51 && code <= 57) s = Sky.DRIZZLE;
        else if (code == 45 || code == 48) s = Sky.FOG;
        else if (code >= 2) s = Sky.CLOUDY;
        else s = day ? Sky.CLEAR_DAY : Sky.CLEAR_NIGHT;
        if (windMph >= 25 && (s == Sky.CLEAR_DAY || s == Sky.CLEAR_NIGHT || s == Sky.CLOUDY)) return Sky.WIND;
        return s;
    }

    /**
     * Pure: the minutes a game's weather touch comes, stable for that game: two or three, one in 8 to 20, one in 33 to 42
     * and, when the game has a third, one in 56 to 80.
     */
    public static int[] weatherMinutes(String gameId) {
        boolean third = pick(gameId, "wx", 2) == 1;
        int[] m = new int[third ? 3 : 2];
        m[0] = 8 + pick(gameId, "wx1", 13);
        m[1] = 33 + pick(gameId, "wx2", 10);
        if (third) m[2] = 56 + pick(gameId, "wx3", 25);
        return m;
    }

    /**
     * Pure (but for shown, which it adds to): whether the weather touch is due at this minute: each of its minutes once a
     * game, up to three minutes after its own.
     */
    public static boolean weatherDue(String gameId, int minute, Set<String> shown) {
        return due("wx", weatherMinutes(gameId), gameId, minute, shown, 3) != null;
    }

    /** Pure: the touch's strength t ms in: in over WEATHER_FADE, held, out over its last WEATHER_FADE. */
    public static float weatherAlpha(long t) {
        if (t <= 0 || t >= WEATHER_MS) return 0f;
        if (t < WEATHER_FADE) return ease(t / (float) WEATHER_FADE);
        if (t > WEATHER_MS - WEATHER_FADE) return ease((WEATHER_MS - t) / (float) WEATHER_FADE);
        return 1f;
    }

    /** The slot due now among minutes ("wave24", "wx13"), adding it to shown, or null. */
    private static String due(String kind, int[] minutes, String gameId, int minute, Set<String> shown, int late) {
        if (gameId == null || minute < 0) return null;
        for (int m : minutes) {
            if (minute < m || minute > m + late) continue;
            String slot = kind + m;
            if (shown == null || shown.add(gameId + "|" + slot)) return slot;
        }
        return null;
    }

    // ---- small pure parts ------------------------------------------------------------------------------------------------

    /** Pure: how far t is from a to b, 0 to 1. */
    static float span(long t, long a, long b) {
        if (t <= a) return 0f;
        if (t >= b) return 1f;
        return (t - a) / (float) (b - a);
    }

    /** Pure: CSS's ease, near enough: slow in, quick, slow out. */
    static float ease(float x) {
        float t = Math.max(0f, Math.min(1f, x));
        return t * t * (3f - 2f * t);
    }

    // ---- drawing ---------------------------------------------------------------------------------------------------

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path(), drawnPath = new Path();
    private final android.graphics.PathMeasure measure = new android.graphics.PathMeasure();
    private final RectF r = new RectF();
    private final Rect bounds = new Rect();
    private final Matrix matrix = new Matrix();
    private LinearGradient shine, glint, haze;
    private float shineW = -1, glintW = -1, hazeH = -1;

    public BoardFx() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** The scorers' side in their colour at alpha (washAlpha, glowAlpha) over (l, t, r, b), lit a little from its top. */
    public void sideWash(Canvas cv, float l, float t, float rr, float b, int colour, float alpha) {
        if (alpha <= 0f || rr <= l || b <= t) return;
        fill.setShader(null);
        fill.setColor(0xFF000000 | colour);
        fill.setAlpha(Math.round(255 * Math.min(1f, alpha)));
        cv.drawRect(l, t, rr, b, fill);
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(Math.round(40 * Math.min(1f, alpha)));
        cv.drawRect(l, t, rr, t + (b - t) * 0.42f, fill);
        fill.setAlpha(255);
    }

    /** The shine sweeping the board w by h, t ms in (SHINE_MS): a soft white band leaning a little, left to right. */
    public void shineSweep(Canvas cv, float w, float h, long t) {
        float at = shineAt(t);
        if (Float.isNaN(at) || w <= 0 || h <= 0) return;
        if (shine == null || shineW != w) {
            shine = new LinearGradient(0, 0, w, 0, new int[]{0x00FFFFFF, 0x00FFFFFF, 0xBFFFFFFF, 0x00FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.3f, 0.5f, 0.7f, 1f}, Shader.TileMode.CLAMP);
            shineW = w;
        }
        matrix.reset();
        matrix.setSkew(-0.18f, 0f);
        matrix.postTranslate(at * w + h * 0.09f, 0f);
        shine.setLocalMatrix(matrix);
        fill.setShader(shine);
        fill.setAlpha(255);
        cv.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
    }

    /**
     * The net shivering across the board w by h, t ms into its shiver (MESH_MS): white lines crossing at 45 degrees both
     * ways, as a goal's net, swelling about the middle (1.05 across, 1.3 up) at its height and fading; thin and see-through
     * so the score always reads through it.
     */
    public void netMesh(Canvas cv, float w, float h, long t) {
        float a = meshAlpha(t);
        if (a <= 0f || w <= 0 || h <= 0) return;
        float k = swell(t);
        float gap = Math.max(4f, h * 0.27f), stroke = Math.max(1f, h * 0.06f);
        cv.save();
        cv.clipRect(0, 0, w, h);
        cv.scale(1f + 0.05f * k, 1f + 0.3f * k, w / 2f, h / 2f);
        line.setShader(null);
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStrokeWidth(stroke);
        line.setColor(0xFFFFFFFF);
        line.setAlpha(Math.round(118 * a));
        // Each way's lines across the whole board and a little past it (the swell shows past its edges).
        float reach = w + h * 2f;
        for (float o = -h * 1.2f; o <= reach; o += gap) {
            cv.drawLine(o, -h * 0.3f, o + h * 1.6f, h * 1.3f, line);
            cv.drawLine(o + h * 1.6f, -h * 0.3f, o, h * 1.3f, line);
        }
        line.setAlpha(255);
        line.setStrokeCap(Paint.Cap.ROUND);
        cv.restore();
    }

    /**
     * The burst over the scorers' crest at (cx, cy), r its radius, t ms in (BURST_MS): two rings in their colour running
     * out and fading, the crest (or a disc in their colour) popping and settling.
     */
    public void crestBurst(Canvas cv, float cx, float cy, float rad, Bitmap crest, int colour, long t) {
        if (rad <= 0 || t < 0 || t >= BURST_MS) return;
        line.setShader(null);
        line.setStrokeWidth(Math.max(1.5f, rad * 0.16f));
        for (int k = 0; k < 2; k++) {
            float e = ringOut(t, k);
            if (Float.isNaN(e)) continue;
            line.setColor(0xFF000000 | colour);
            line.setAlpha(Math.round(235 * (1f - e)));
            cv.drawCircle(cx, cy, rad * (0.4f + 4.6f * e), line);
        }
        line.setAlpha(255);
        float k = popAt(t), s = rad * k;
        if (crest != null && !crest.isRecycled() && crest.getWidth() > 0 && crest.getHeight() > 0) {
            float f = Math.min(2 * s / crest.getWidth(), 2 * s / crest.getHeight());
            float bw = crest.getWidth() * f, bh = crest.getHeight() * f;
            r.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
            cv.drawBitmap(crest, null, r, img);
        } else {
            fill.setShader(null);
            fill.setColor(0xFF000000 | colour);
            cv.drawCircle(cx, cy, s, fill);
        }
    }

    /** The red card swinging up about its foot (cx, foot), h high, t ms in (RED_MS), glowing red; hidden below at either end. */
    public void redCard(Canvas cv, float cx, float foot, float h, long t) {
        if (h <= 0 || t <= 0 || t >= RED_MS) return;
        float[] pose = redCardPose(t);
        float w = h * 0.7f;
        cv.save();
        cv.translate(0, pose[0] * h);
        cv.rotate(pose[1], cx, foot);
        r.set(cx - w / 2f, foot - h, cx + w / 2f, foot);
        fill.setShader(null);
        fill.setColor(0xFFE3061B);
        fill.setShadowLayer(h * 0.35f, 0, 0, 0xCCE3061B);
        cv.drawRoundRect(r, h * 0.1f, h * 0.1f, fill);
        fill.clearShadowLayer();
        fill.setColor(0x33FFFFFF);
        r.set(cx - w / 2f + w * 0.12f, foot - h + h * 0.08f, cx - w / 2f + w * 0.3f, foot - h * 0.35f);
        cv.drawRoundRect(r, w * 0.08f, w * 0.08f, fill);
        cv.restore();
    }

    /** VAR's dot at (cx, cy), r its radius: white, pulsing a second's round (varPulse). */
    public void varDot(Canvas cv, float cx, float cy, float rad, long now) {
        if (rad <= 0) return;
        float[] p = varPulse(now);
        fill.setShader(null);
        fill.setColor(0xFFFFFFFF);
        fill.setAlpha(Math.round(255 * p[1]));
        cv.drawCircle(cx, cy, rad * p[0], fill);
        fill.setAlpha(255);
    }

    /**
     * A shootout's kicks in (l, t, r, b), the clock's place: the home side's row over the away side's, five dots a row
     * (more in sudden death, the latest shown when they don't fit): green scored, red missed, an empty ring to come.
     */
    public void pensDots(Canvas cv, float l, float t, float rr, float b, List<Boolean> home, List<Boolean> away, int ink) {
        int nh = home == null ? 0 : home.size(), na = away == null ? 0 : away.size(), cols = pensCols(nh, na);
        float rowH = (b - t) / 2f, w = rr - l;
        float d = Math.min(rowH * 0.62f, w / (cols * 1.45f));
        if (d <= 1f) return;
        float step = Math.min(w / cols, d * 1.6f), start = l + (w - step * cols) / 2f + step / 2f;
        line.setShader(null);
        line.setStrokeWidth(Math.max(1f, d * 0.16f));
        for (int row = 0; row < 2; row++) {
            List<Boolean> kicks = row == 0 ? home : away;
            int n = kicks == null ? 0 : kicks.size(), skip = Math.max(0, n - cols);
            float cy = t + rowH * (row + 0.5f);
            for (int i = 0; i < cols; i++) {
                float cx = start + i * step;
                int k = skip + i;
                Boolean in = kicks != null && k < n ? kicks.get(k) : null;
                if (in == null) {
                    line.setColor(ink);
                    line.setAlpha(180);
                    cv.drawCircle(cx, cy, d / 2f - line.getStrokeWidth() / 2f, line);
                    line.setAlpha(255);
                } else {
                    fill.setShader(null);
                    fill.setColor(in ? ON_GREEN_DOT : MISS_RED);
                    cv.drawCircle(cx, cy, d / 2f, fill);
                }
            }
        }
    }

    /** The shootout dots' colours: scored, missed. */
    public static final int ON_GREEN_DOT = 0xFF3DDC84, MISS_RED = 0xFFE3061B;

    /** Pure: how many dots a row: five, or as many as the longer side has taken in sudden death. */
    public static int pensCols(int homeKicks, int awayKicks) {
        return Math.max(5, Math.max(homeKicks, awayKicks));
    }

    /**
     * The momentum wave in (l, t, r, b), drawn so far (0 to 1, waveDrawn): a fine midline, the line of who is on top drawn
     * in from the left (above: the home side, in its colour lightly under it; below: the away side), at alpha.
     */
    public void wave(Canvas cv, float l, float t, float rr, float b, List<int[]> values, float drawn, int homeColour, int awayColour,
            int ink, float alpha) {
        if (values == null || values.size() < 2 || drawn <= 0f || alpha <= 0f || rr <= l || b <= t) return;
        float mid = (t + b) / 2f, half = (b - t) * 0.44f;
        int first = values.get(0)[0], last = values.get(values.size() - 1)[0];
        float span = Math.max(1, last - first);
        path.reset();
        float px = 0, py = 0;
        for (int i = 0; i < values.size(); i++) {
            int[] v = values.get(i);
            float x = l + (rr - l) * (v[0] - first) / span;
            float y = mid - half * Math.max(-1f, Math.min(1f, v[1] / 100f));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f);
            }
            px = x;
            py = y;
        }
        path.lineTo(px, py);
        float cut = l + (rr - l) * Math.min(1f, drawn);
        // The light fills each side of the midline, up to where the line has been drawn.
        cv.save();
        cv.clipRect(l, t, cut, mid);
        fillUnder(cv, path, l, rr, mid, homeColour, alpha);
        cv.restore();
        cv.save();
        cv.clipRect(l, mid, cut, b);
        fillUnder(cv, path, l, rr, mid, awayColour, alpha);
        cv.restore();
        line.setShader(null);
        line.setStrokeWidth(Math.max(1f, (b - t) * 0.02f));
        line.setColor(ink);
        line.setAlpha(Math.round(80 * alpha));
        cv.drawLine(l, mid, rr, mid, line);
        measure.setPath(path, false);
        drawnPath.reset();
        measure.getSegment(0, measure.getLength() * Math.min(1f, drawn), drawnPath, true);
        line.setStrokeWidth(Math.max(1.5f, (b - t) * 0.06f));
        line.setAlpha(Math.round(255 * alpha));
        cv.drawPath(drawnPath, line);
        line.setAlpha(255);
    }

    /** The area between the line and the midline, in a colour lightly. */
    private void fillUnder(Canvas cv, Path line0, float l, float rr, float mid, int colour, float alpha) {
        drawnPath.reset();
        drawnPath.addPath(line0);
        drawnPath.lineTo(rr, mid);
        drawnPath.lineTo(l, mid);
        drawnPath.close();
        fill.setShader(null);
        fill.setColor(0xFF000000 | colour);
        fill.setAlpha(Math.round(80 * alpha));
        cv.drawPath(drawnPath, fill);
        fill.setAlpha(255);
    }

    // ---- the weather touch: particles from a fixed seed ---------------------------------------------------------------

    /** Each particle's place across (0 to 1), its start in its fall (0 to 1), its speed and its size (each 0.6 to 1.4). */
    private static final float[] PX = new float[24], PP = new float[24], PS = new float[24], PZ = new float[24];

    static {
        long seed = 0x5EEDL;
        for (int i = 0; i < 24; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            PX[i] = ((seed >>> 33) & 0xFFFF) / 65535f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            PP[i] = ((seed >>> 33) & 0xFFFF) / 65535f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            PS[i] = 0.6f + 0.8f * ((seed >>> 33) & 0xFFFF) / 65535f;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            PZ[i] = 0.6f + 0.8f * ((seed >>> 33) & 0xFFFF) / 65535f;
        }
    }

    /**
     * The ground's weather lightly over the board w by h, t ms in (WEATHER_MS): rain's slanting streaks, drizzle's fewer
     * and finer, snow drifting, a storm's rain with one faint flash, fog's haze drifting, cloud's shadow passing, a clear
     * day's warm glint, a clear night's twinkling specks, wind's faint streaks. Never strong: the board always reads.
     */
    public void weather(Canvas cv, float w, float h, Sky sky, long t) {
        float a = weatherAlpha(t);
        if (sky == null || a <= 0f || w <= 0 || h <= 0) return;
        cv.save();
        cv.clipRect(0, 0, w, h);
        switch (sky) {
            case RAIN: streaks(cv, w, h, t, 16, 700, 0.5f, 0.07f, 86 * a); break;
            case DRIZZLE: streaks(cv, w, h, t, 9, 1000, 0.3f, 0.05f, 64 * a); break;
            case STORM: {
                streaks(cv, w, h, t, 20, 520, 0.55f, 0.075f, 90 * a);
                long f = t - 3100;
                float flash = f >= 0 && f < 140 ? 1f - f / 140f : f >= 260 && f < 360 ? 0.5f * (1f - (f - 260) / 100f) : 0f;
                if (flash > 0f) {
                    fill.setShader(null);
                    fill.setColor(0xFFFFFFFF);
                    fill.setAlpha(Math.round(64 * flash * a));
                    cv.drawRect(0, 0, w, h, fill);
                    fill.setAlpha(255);
                }
                break;
            }
            case SNOW: snow(cv, w, h, t, a); break;
            case FOG: fog(cv, w, h, t, a); break;
            case CLOUDY: cloud(cv, w, h, t, a); break;
            case CLEAR_DAY: glint(cv, w, h, t, a); break;
            case CLEAR_NIGHT: stars(cv, w, h, t, a); break;
            case WIND: wind(cv, w, h, t, a); break;
            default: break;
        }
        cv.restore();
    }

    /** n streaks falling and slanting, each period ms to fall, len of h long, stroke of h wide, at alpha (of 255). */
    private void streaks(Canvas cv, float w, float h, long t, int n, long period, float len, float stroke, float alpha) {
        line.setShader(null);
        line.setColor(0xFFDCE8FF);
        float slant = 0.27f;
        for (int i = 0; i < n; i++) {
            float p = ((t / (float) (period * PS[i])) + PP[i]) % 1f;
            float l = h * len * PZ[i], y = -l + (h + l * 2f) * p, x = PX[i] * (w + h * slant) - h * slant * p;
            line.setStrokeWidth(Math.max(1f, h * stroke * 0.25f * PZ[i]));
            line.setAlpha(Math.round(alpha * (0.6f + 0.4f * PZ[i] / 1.4f)));
            cv.drawLine(x, y, x - l * slant, y + l, line);
        }
        line.setAlpha(255);
    }

    private void snow(Canvas cv, float w, float h, long t, float a) {
        fill.setShader(null);
        fill.setColor(0xFFFFFFFF);
        for (int i = 0; i < 14; i++) {
            float p = ((t / (3000f * PS[i])) + PP[i]) % 1f;
            float rad = Math.max(1f, h * 0.035f * PZ[i]), y = -rad + (h + rad * 2f) * p;
            float x = PX[i] * w + (float) Math.sin((t / 700.0 + i) * PS[i]) * h * 0.12f;
            fill.setAlpha(Math.round(90 * a * (0.55f + 0.45f * PZ[i] / 1.4f)));
            cv.drawCircle(x, y, rad, fill);
        }
        fill.setAlpha(255);
    }

    private void fog(Canvas cv, float w, float h, long t, float a) {
        if (haze == null || hazeH != h) {
            haze = new LinearGradient(0, 0, 0, h * 0.5f, new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            hazeH = h;
        }
        fill.setShader(haze);
        for (int k = 0; k < 2; k++) {
            float drift = ((t / 6000f) + k * 0.5f) % 1f;
            matrix.reset();
            matrix.setTranslate(0, h * (0.1f + 0.45f * k));
            haze.setLocalMatrix(matrix);
            fill.setAlpha(Math.round(150 * a));
            float x0 = -w * 0.6f + w * 1.2f * drift;
            cv.drawRect(x0, h * (0.1f + 0.45f * k), x0 + w * 1.1f, h * (0.6f + 0.45f * k), fill);
        }
        fill.setShader(null);
        fill.setAlpha(255);
    }

    private void cloud(Canvas cv, float w, float h, long t, float a) {
        float x = -w * 0.5f + w * 1.5f * (t / (float) WEATHER_MS);
        fill.setShader(new android.graphics.RadialGradient(x, h * 0.5f, Math.max(1f, w * 0.35f), new int[]{0x4D000000, 0x00000000}, null,
                Shader.TileMode.CLAMP));
        fill.setAlpha(Math.round(255 * a));
        cv.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        fill.setAlpha(255);
    }

    private void glint(Canvas cv, float w, float h, long t, float a) {
        if (glint == null || glintW != w) {
            glint = new LinearGradient(0, 0, w * 0.3f, 0, new int[]{0x00FFF2D0, 0x8CFFF2D0, 0x00FFF2D0}, null, Shader.TileMode.CLAMP);
            glintW = w;
        }
        float x = -w * 0.3f + w * 1.6f * ease(t / (float) WEATHER_MS);
        matrix.reset();
        matrix.setSkew(-0.35f, 0f);
        matrix.postTranslate(x, 0f);
        glint.setLocalMatrix(matrix);
        fill.setShader(glint);
        fill.setAlpha(Math.round(200 * a));
        cv.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        // A few faint rays from the top corner, as the sun on the stand's roof.
        line.setShader(null);
        line.setColor(0xFFFFF2D0);
        line.setStrokeWidth(Math.max(1f, h * 0.03f));
        line.setAlpha(Math.round(50 * a));
        for (int k = 0; k < 3; k++) cv.drawLine(w, 0, w - w * (0.18f + 0.12f * k), h * (0.7f + 0.15f * k), line);
        line.setAlpha(255);
        fill.setAlpha(255);
    }

    private void stars(Canvas cv, float w, float h, long t, float a) {
        line.setShader(null);
        line.setColor(0xFFFFFFFF);
        for (int i = 0; i < 5; i++) {
            float x = w * (0.08f + 0.84f * PX[i]), y = h * (0.12f + 0.76f * PP[i]);
            float tw = 0.5f + 0.5f * (float) Math.sin((t / 450.0) * PS[i] + i * 1.7);
            float s = Math.max(1.2f, h * 0.06f * PZ[i]);
            line.setStrokeWidth(Math.max(1f, h * 0.022f));
            line.setAlpha(Math.round(90 * a * tw));
            cv.drawLine(x - s, y, x + s, y, line);
            cv.drawLine(x, y - s, x, y + s, line);
        }
        line.setAlpha(255);
    }

    private void wind(Canvas cv, float w, float h, long t, float a) {
        line.setShader(null);
        line.setColor(0xFFFFFFFF);
        for (int i = 0; i < 6; i++) {
            float p = ((t / (900f * PS[i])) + PP[i]) % 1f, l = w * 0.18f * PZ[i];
            float x = w + l - (w + l * 2f) * p, y = h * (0.15f + 0.7f * PX[i]) + (float) Math.sin(t / 300.0 + i) * h * 0.03f;
            line.setStrokeWidth(Math.max(1f, h * 0.025f));
            line.setAlpha(Math.round(56 * a));
            cv.drawLine(x, y, x + l, y, line);
        }
        line.setAlpha(255);
    }

    // ---- a score's figure moving in, on the boards' own looks ------------------------------------------------------------

    /**
     * A score's words (now) about cx, their capitals' middle on cy, in p as it is set (size, typeface, colour), with a goal's
     * new figure moving in (move: CompBug.MOVE_FLIP or MOVE_ROLL; at 0 to 1): only the part of the words that differs from
     * was moves (rolling up as the old one rolls away, or turning down into place from above), clipped to its line; the rest
     * stays still. With no move (move 0, at 1, nothing changed) it is the words as they are.
     */
    public void scoreMove(Canvas cv, Paint p, String was, String now, float cx, float cy, int move, float at) {
        if (now == null || now.isEmpty()) return;
        p.getTextBounds("H8", 0, 2, bounds);
        float base = cy - bounds.top / 2f, size = p.getTextSize(), full = p.measureText(now), left = cx - full / 2f;
        if (move == 0 || at >= 1f || was == null || was.equals(now)) {
            cv.drawText(now, left, base, p);
            return;
        }
        int pre = 0, maxPre = Math.min(was.length(), now.length());
        while (pre < maxPre && was.charAt(pre) == now.charAt(pre)) pre++;
        int suf = 0;
        while (suf < Math.min(was.length(), now.length()) - pre && was.charAt(was.length() - 1 - suf) == now.charAt(now.length() - 1 - suf)) suf++;
        String head = now.substring(0, pre), tail = now.substring(now.length() - suf), newMid = now.substring(pre, now.length() - suf),
                oldMid = was.substring(pre, was.length() - suf);
        float hw = p.measureText(head), nw = p.measureText(newMid), ow = p.measureText(oldMid);
        if (!head.isEmpty()) cv.drawText(head, left, base, p);
        if (!tail.isEmpty()) cv.drawText(tail, left + hw + nw, base, p);
        float mx = left + hw + nw / 2f, half = Math.max(nw, ow) / 2f + size * 0.2f;
        int colour = p.getColor();
        cv.save();
        cv.clipRect(mx - half, cy - size * 0.66f, mx + half, cy + size * 0.66f);
        if (move == CompBug.MOVE_ROLL) {
            float e = CompBug.rollEase(at), travel = size * 1.15f;
            p.setAlpha(Math.round((colour >>> 24) * Math.max(0f, 1f - Math.min(1f, e))));
            cv.drawText(oldMid, mx - ow / 2f, base - e * travel, p);
            p.setColor(colour);
            cv.drawText(newMid, mx - nw / 2f, base + (1f - e) * travel, p);
        } else if (at <= 0f) {
            cv.drawText(oldMid, mx - ow / 2f, base, p);
        } else {
            cv.translate(0, -CompBug.flipLift(at) * size);
            cv.scale(1f, Math.max(0.04f, CompBug.flipScale(at)), mx, cy);
            p.setAlpha(Math.round((colour >>> 24) * Math.min(1f, at * 1.6f)));
            cv.drawText(newMid, mx - nw / 2f, base, p);
        }
        p.setColor(colour);
        cv.restore();
    }
}
