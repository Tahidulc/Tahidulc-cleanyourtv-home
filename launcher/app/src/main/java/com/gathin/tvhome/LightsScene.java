package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.Locale;

/**
 * The living room in 3D, as it is: the long white wall with the 98-inch TV on it, the grey sideboard under it and a
 * glazed oak door past it, the
 * ceiling's edge lit by the LED strip along that wall and on across the far wall over its doors (two glazed oak, then a
 * wide clear glass one in white edging), the oak slatted wall across from the TV with its own strip along its top, the
 * grey velvet corner sofa in front of it (its cushions, two mustard and two grey), the long dark coffee table on its
 * light rug, the
 * lit slot in the ceiling beside it, the row of downlights, the oak floor. It comes up when a light is switched on or
 * off, given a colour or dimmed (SmartPanel), and shows what the lights are doing: the strip lighting along its length
 * as it comes on (and going out back along it), a new colour washing along it, its glow on the walls and the ceiling
 * rising and falling with its brightness, the downlights' pools on the floor. Drawn by itself on a Canvas (a small
 * perspective of flat faces, nearest last), turning a little as it shows; gone after a few seconds.
 */
final class LightsScene extends View {
    /** The lights the model has: the strip round the ceiling's edge, the slot in the ceiling, the downlights. */
    static final int STRIP = 0, SLOT = 1, DOWN = 2;
    static final long SHOW_MS = 5600L, CHANGE_MS = 1300L, IN_MS = 380L, OUT_MS = 320L;

    /** What one group of lights is doing: on, how bright (0-1), its colour (RGB; white for a white light). */
    static final class Glow {
        boolean on;
        float level = 1f;
        int colour = WARM;

        Glow copy() {
            Glow g = new Glow();
            g.on = on;
            g.level = level;
            g.colour = colour;
            return g;
        }
    }

    static final int WARM = 0xFFFFD9A8, COOL = 0xFFF2F5FF;

    /** Pure: which of the model's lights a light of this name is: the strip, the slot or the downlights. */
    static int groupOf(String name, boolean colour) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.matches(".*(track|slot|linear|line light|bar).*")) return SLOT;
        if (n.matches(".*(strip|led|cove|tv|ambient|rgb|ceiling edge|bulkhead|mood).*")) return STRIP;
        if (n.matches(".*(spot|down|ceiling|main|big light|lamp|bulb).*")) return DOWN;
        return colour ? STRIP : DOWN;
    }

    /** Pure: a colour's name in words, for the card ("Blue", "Warm white"). */
    static String colourName(int rgb) {
        float[] hsv = new float[3];
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float s = max == 0 ? 0 : (max - min) / (float) max;
        if (s < 0.22f) return r > b + 20 ? "Warm white" : "White";
        if (s < 0.45f && r == max && b == min && g > b) return "Warm white";
        float h;
        float d = max - min;
        if (max == r) h = 60f * (((g - b) / d) % 6f);
        else if (max == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0) h += 360f;
        hsv[0] = h;
        if (h < 15 || h >= 340) return "Red";
        if (h < 40) return "Orange";
        if (h < 65) return "Yellow";
        if (h < 160) return "Green";
        if (h < 200) return "Cyan";
        if (h < 250) return "Blue";
        if (h < 290) return "Purple";
        return "Pink";
    }

    // ---- showing it -------------------------------------------------------------------------------------------------

    /** Whether the room comes up on a change (on unless switched off: smart_lights "scene"). */
    static boolean wanted(Context c) {
        return c.getSharedPreferences("smart_lights", Context.MODE_PRIVATE).getBoolean("scene", true);
    }

    private static LightsScene showing;

    /**
     * Shows the room in root (Home's), its lights going from what they were to what they are now; the light changed
     * named in its title. A change while it shows carries on from where it is.
     */
    static void show(FrameLayout root, Glow[] before, Glow[] after, String title, String line) {
        if (root == null) return;
        LightsScene s = showing;
        if (s != null && s.getParent() == root) {
            s.change(after, title, line);
            return;
        }
        s = new LightsScene(root.getContext());
        s.from = copy(before);
        s.to = copy(after);
        s.title = title;
        s.line = line;
        float k = root.getResources().getDisplayMetrics().widthPixels / 1920f;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(820 * k), Math.round(520 * k), Gravity.TOP | Gravity.START);
        lp.leftMargin = Math.round(80 * k);
        lp.topMargin = Math.round(196 * k);
        root.addView(s, lp);
        showing = s;
    }

    static void hide() {
        LightsScene s = showing;
        if (s != null) s.away = SystemClock.uptimeMillis();
    }

    private static Glow[] copy(Glow[] g) {
        Glow[] o = new Glow[3];
        for (int i = 0; i < 3; i++) o[i] = g != null && i < g.length && g[i] != null ? g[i].copy() : new Glow();
        return o;
    }

    private Glow[] from, to;
    private String title = "", line = "";
    private long born = SystemClock.uptimeMillis(), changed = born, away;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final Typeface heavy, semi, medium;
    /** Previews: the time drawn at, fixed (-1: the clock). */
    private long fixed = -1;

    LightsScene(Context c) {
        super(c);
        heavy = ScoreCard.outfit(c, 800);
        semi = ScoreCard.outfit(c, 600);
        medium = ScoreCard.outfit(c, 500);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setContentDescription("The living room's lights");
    }

    /** A new change while it shows: from the lights as they look now to these. */
    private void change(Glow[] after, String title, String line) {
        long now = SystemClock.uptimeMillis();
        Glow[] mid = new Glow[3];
        float p = progress(now - changed);
        for (int i = 0; i < 3; i++) mid[i] = between(from[i], to[i], p);
        from = mid;
        to = copy(after);
        this.title = title;
        this.line = line;
        changed = now;
        away = 0;
        born = Math.min(born, now - IN_MS);
        invalidate();
    }

    /** Previews: this far in (ms since it came up, the change made at changeAt). */
    void at(long sinceShown, long changeAt) {
        fixed = sinceShown;
        born = 0;
        changed = changeAt;
        invalidate();
    }

    static LightsScene preview(Context c, Glow[] before, Glow[] after, String title, String line) {
        LightsScene s = new LightsScene(c);
        s.from = copy(before);
        s.to = copy(after);
        s.title = title;
        s.line = line;
        return s;
    }

    private static float progress(long t) {
        float x = Math.max(0f, Math.min(1f, t / (float) CHANGE_MS));
        return x * x * (3 - 2 * x);
    }

    /** A light part way from a to b: on as soon as either is, its brightness and colour eased between. */
    private static Glow between(Glow a, Glow b, float p) {
        Glow g = new Glow();
        float la = a.on ? a.level : 0f, lb = b.on ? b.level : 0f;
        g.level = la + (lb - la) * p;
        g.on = g.level > 0.001f;
        g.colour = mix(a.on ? a.colour : b.colour, b.on ? b.colour : a.colour, p);
        return g;
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
        int g = Math.round(((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
        int bl = Math.round((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int alpha(int c, float a) {
        return (Math.round(Math.max(0f, Math.min(1f, a)) * 255) << 24) | (c & 0xFFFFFF);
    }

    // ---- the room (metres: x across from the TV wall, y up, z down the room) -------------------------------------------

    private static final float W = 4.3f, H = 2.55f, D = 6.6f;
    /** The camera's place and where it looks, and the view's focal length (set as it is drawn). */
    private float cx, cy, cz, yaw, pitch, focal, ox, oy;
    private float sinY, cosY, sinP, cosP;
    private final float[] p3 = new float[3];

    private void camera(float t) {
        // Turning a little as it shows.
        float sway = (float) Math.sin(t / 2600.0) * 0.07f, dolly = Math.min(1f, t / 1800f);
        // Up in the corner behind the sofa, looking down the room over it to the TV wall and the doors.
        cx = 3.75f - 0.12f * dolly;
        cy = 2.05f;
        cz = -1.25f + 0.3f * (dolly * dolly * (3 - 2 * dolly));
        yaw = 0.42f + sway;
        pitch = -0.2f;
        sinY = (float) Math.sin(yaw);
        cosY = (float) Math.cos(yaw);
        sinP = (float) Math.sin(pitch);
        cosP = (float) Math.cos(pitch);
    }

    /** A point into the camera's own space (x right, y up, z ahead). */
    private void view(float x, float y, float z, float[] out) {
        float dx = x - cx, dy = y - cy, dz = z - cz;
        float rx = dx * cosY + dz * sinY, rz = -dx * sinY + dz * cosY;
        float ry = dy * cosP - rz * sinP, rz2 = dy * sinP + rz * cosP;
        out[0] = rx;
        out[1] = ry;
        out[2] = rz2;
    }

    private static final float NEAR = 0.08f;

    /**
     * The face with these corners (x, y, z each) as a path on the screen, cut where it passes behind the camera; false
     * when none of it is in front.
     */
    private boolean face(Path into, float... pts) {
        int n = pts.length / 3;
        float[] vx = new float[n * 2 + 2], vy = new float[n * 2 + 2], vz = new float[n * 2 + 2];
        int m = 0;
        float[] a = new float[3], b = new float[3];
        for (int i = 0; i < n; i++) {
            view(pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2], a);
            int j = (i + 1) % n;
            view(pts[j * 3], pts[j * 3 + 1], pts[j * 3 + 2], b);
            boolean ain = a[2] >= NEAR, bin = b[2] >= NEAR;
            if (ain) { vx[m] = a[0]; vy[m] = a[1]; vz[m] = a[2]; m++; }
            if (ain != bin) {
                float t = (NEAR - a[2]) / (b[2] - a[2]);
                vx[m] = a[0] + (b[0] - a[0]) * t;
                vy[m] = a[1] + (b[1] - a[1]) * t;
                vz[m] = NEAR;
                m++;
            }
        }
        if (m < 3) return false;
        into.reset();
        for (int i = 0; i < m; i++) {
            float sx = ox + vx[i] / vz[i] * focal, sy = oy - vy[i] / vz[i] * focal;
            if (i == 0) into.moveTo(sx, sy);
            else into.lineTo(sx, sy);
        }
        into.close();
        return true;
    }

    /** A point on the screen into p3 (x, y, and its distance); false when it is behind the camera. */
    private boolean point(float x, float y, float z) {
        view(x, y, z, p3);
        if (p3[2] < NEAR) return false;
        float d = p3[2];
        p3[0] = ox + p3[0] / d * focal;
        p3[1] = oy - p3[1] / d * focal;
        p3[2] = d;
        return true;
    }

    private void paint(Canvas cv, int colour, float... pts) {
        if (!face(path, pts)) return;
        fill.setShader(null);
        fill.setColor(colour);
        cv.drawPath(path, fill);
    }

    /** A face lit from a line at its top edge (a, b), fading over its depth (to c, d): the wash of a strip on it. */
    private void wash(Canvas cv, int colour, float strength, float ax, float ay, float az, float bx, float by, float bz,
                      float dx, float dy, float dz) {
        if (strength <= 0.003f) return;
        if (!face(path, ax, ay, az, bx, by, bz, bx + dx, by + dy, bz + dz, ax + dx, ay + dy, az + dz)) return;
        float mx = (ax + bx) / 2f, my = (ay + by) / 2f, mz = (az + bz) / 2f;
        if (!point(mx, my, mz)) return;
        float sx = p3[0], sy = p3[1];
        if (!point(mx + dx, my + dy, mz + dz)) return;
        fill.setShader(new LinearGradient(sx, sy, p3[0], p3[1], new int[]{alpha(colour, strength * 0.95f), alpha(colour, strength * 0.45f), alpha(colour, 0f)},
                new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));
        cv.drawPath(path, fill);
        fill.setShader(null);
    }

    /** The lit line from a to b: its core and its glow, lit only as far as on (0-1) along it from a. */
    private void tube(Canvas cv, int colour, float level, float on, float ax, float ay, float az, float bx, float by, float bz) {
        if (level <= 0.003f || on <= 0f) return;
        float ex = ax + (bx - ax) * on, ey = ay + (by - ay) * on, ez = az + (bz - az) * on;
        // Cut to the part in front of the camera.
        float[] a = new float[3], b = new float[3];
        view(ax, ay, az, a);
        view(ex, ey, ez, b);
        if (a[2] < NEAR && b[2] < NEAR) return;
        float t0 = 0f, t1 = 1f;
        if (a[2] < NEAR) t0 = (NEAR - a[2]) / (b[2] - a[2]);
        if (b[2] < NEAR) t1 = (NEAR - a[2]) / (b[2] - a[2]);
        float sx0 = ax + (ex - ax) * t0, sy0 = ay + (ey - ay) * t0, sz0 = az + (ez - az) * t0;
        float sx1 = ax + (ex - ax) * t1, sy1 = ay + (ey - ay) * t1, sz1 = az + (ez - az) * t1;
        if (!point(sx0, sy0, sz0)) return;
        float x0 = p3[0], y0 = p3[1], d0 = p3[2];
        if (!point(sx1, sy1, sz1)) return;
        float x1 = p3[0], y1 = p3[1], d1 = p3[2];
        float width = focal * 0.06f / Math.max(0.5f, Math.min(d0, d1));
        for (int i = 4; i >= 0; i--) {
            stroke.setColor(alpha(colour, level * (i == 0 ? 1f : 0.11f / i)));
            stroke.setStrokeWidth(width * (i == 0 ? 0.28f : 1f + i * 1.6f));
            cv.drawLine(x0, y0, x1, y1, stroke);
        }
        stroke.setColor(alpha(mix(colour, Color.WHITE, 0.65f), level));
        stroke.setStrokeWidth(Math.max(1f, width * 0.12f));
        cv.drawLine(x0, y0, x1, y1, stroke);
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas cv) {
        long now = fixed >= 0 ? fixed : SystemClock.uptimeMillis();
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || from == null) return;
        long since = now - born;
        // In, the change, and away again after a while.
        float in = Math.min(1f, since / (float) IN_MS), out = 0f;
        if (fixed < 0) {
            if (away == 0 && now - changed > SHOW_MS) away = now;
            if (away > 0) out = Math.min(1f, (now - away) / (float) OUT_MS);
            if (out >= 1f) {
                if (showing == this) showing = null;
                ViewGroup p = (ViewGroup) getParent();
                if (p != null) p.post(() -> p.removeView(this));
                return;
            }
        }
        float shown = in * (1f - out);
        float ease = 1f - (1f - shown) * (1f - shown);
        cv.save();
        float sc = 0.94f + 0.06f * ease;
        cv.scale(sc, sc, w * 0.1f, h * 0.1f);
        int a = Math.round(255 * ease);
        cv.saveLayerAlpha(0, 0, w, h, a);
        float rad = h * 0.07f;
        // The card: night glass, the room filling it.
        r.set(0, 0, w, h);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0xF2121626, 0xF2070910, Shader.TileMode.CLAMP));
        cv.drawRoundRect(r, rad, rad, fill);
        fill.setShader(null);
        cv.save();
        path.reset();
        path.addRoundRect(r, rad, rad, Path.Direction.CW);
        cv.clipPath(path);
        long tc = fixed >= 0 ? fixed - changed : now - changed;
        float p = progress(tc);
        room(cv, w, h, since, tc, p);
        cv.restore();
        // A fine edge round it.
        stroke.setColor(0x33FFFFFF);
        stroke.setStrokeWidth(Math.max(1f, h * 0.003f));
        r.inset(0.5f, 0.5f);
        cv.drawRoundRect(r, rad, rad, stroke);
        words(cv, w, h, p);
        cv.restore();
        cv.restore();
        if (fixed < 0) postInvalidateOnAnimation();
    }

    private void room(Canvas cv, int w, int h, long since, long tc, float p) {
        camera(since);
        focal = h * 0.86f;
        ox = w * 0.5f;
        oy = h * 0.47f;
        Glow strip = between(from[STRIP], to[STRIP], p), slot = between(from[SLOT], to[SLOT], p), down = between(from[DOWN], to[DOWN], p);
        // How far along its length the strip is lit: running out from the TV end as it comes on, back as it goes off.
        float run = 1f;
        boolean comingOn = !from[STRIP].on && to[STRIP].on, goingOff = from[STRIP].on && !to[STRIP].on;
        if (comingOn) { run = Math.min(1f, tc / (float) CHANGE_MS * 1.15f); strip.level = to[STRIP].level; strip.on = true; }
        if (goingOff) { run = 1f - Math.min(1f, tc / (float) CHANGE_MS * 1.15f); strip.level = from[STRIP].level; strip.colour = from[STRIP].colour; strip.on = run > 0; }
        // A new colour washing along the strip from the TV end.
        float wave = from[STRIP].on && to[STRIP].on && from[STRIP].colour != to[STRIP].colour ? Math.min(1f, tc / (float) CHANGE_MS) : -1f;
        int stripNew = to[STRIP].colour, stripOld = from[STRIP].colour;

        // The room's own light: dim without the downlights, warm with them.
        float lit = down.on ? 0.35f + 0.65f * down.level : 0f;
        float glow = strip.on ? strip.level * run : 0f;
        int wall = mix(0xFF2E323C, 0xFFB9B7B2, lit * 0.85f + glow * 0.12f);
        int wallDark = mix(0xFF22252D, 0xFF97958F, lit * 0.85f + glow * 0.1f);
        int ceiling = mix(0xFF2A2E38, 0xFFD2D0CA, lit * 0.8f + glow * 0.14f);
        int floor = mix(0xFF2A221B, 0xFFB79A78, lit * 0.85f);
        int floorFar = mix(0xFF221C17, 0xFF9C8263, lit * 0.85f);

        // The floor, its oak boards running down the room.
        paint(cv, floor, 0, 0, -3, W, 0, -3, W, 0, D, 0, 0, D);
        fill.setColor(alpha(floorFar, 0.5f));
        for (float bx = 0.18f; bx < W; bx += 0.18f) {
            if (!point(bx, 0, 0.2f)) continue;
            float x0 = p3[0], y0 = p3[1];
            if (!point(bx, 0, D)) continue;
            stroke.setColor(alpha(0xFF000000, 0.12f));
            stroke.setStrokeWidth(Math.max(1f, h * 0.0015f));
            cv.drawLine(x0, y0, p3[0], p3[1], stroke);
        }
        // The ceiling, the far wall, the right-hand wall, the TV's wall.
        paint(cv, ceiling, 0, H, -3, W, H, -3, W, H, D, 0, H, D);
        paint(cv, wall, 0, 0, D, W, 0, D, W, H, D, 0, H, D);
        paint(cv, wallDark, W, 0, -3, W, 0, D, W, H, D, W, H, -3);
        paint(cv, wall, 0, 0, -3, 0, 0, D, 0, H, D, 0, H, -3);
        // The bulkhead along the TV's wall: the ceiling stepped down where the strip hides, its face along the room.
        float bh = 0.16f, bw = 0.42f;
        paint(cv, mix(ceiling, 0xFF000000, 0.08f), 0, H - bh, -3, bw, H - bh, -3, bw, H - bh, D, 0, H - bh, D);
        paint(cv, mix(wall, 0xFF000000, 0.05f), bw, H - bh, -3, bw, H, -3, bw, H, D, bw, H - bh, D);

        // The strip's glow: up the bulkhead's face and over the ceiling, down the TV's wall, across the far wall.
        int sc = strip.colour;
        float gs = Math.min(1f, glow * 1.15f);
        wash(cv, sc, gs, bw, H - 0.02f, -3, bw, H - 0.02f, D, 0.85f, 0, 0);
        wash(cv, sc, gs * 0.9f, 0, H - bh, -3, 0, H - bh, D, 0, -1.25f, 0);
        wash(cv, sc, gs * 0.8f, 0, H - bh, D - 0.01f, W, H - bh, D - 0.01f, 0, -1.1f, 0);
        wash(cv, sc, gs * 0.55f, 0.02f, H - bh, D - 0.6f, 0.02f, H - bh, D, 0, 0, 0);

        // The far wall's three glazed oak doors.
        door(cv, 0.3f, 0.82f, lit, glow, sc);
        door(cv, 1.17f, 0.82f, lit, glow, sc);
        clearDoor(cv, 2.12f, 1.12f, lit, glow, sc);
        // The single glazed oak door in the TV's wall, past the TV.
        sideDoor(cv, 4.75f, 0.82f, lit, glow, sc);

        // The TV's dark panel, the TV on it, the sideboard under them.
        // The TV, a 98-inch: 2.17 m across, 1.22 m high, on the bare white wall.
        float tz0 = 1.25f, tz1 = tz0 + 2.17f;
        box(cv, 0.02f, 0.07f, 0.72f, 0.72f + 1.22f, tz0, tz1, 0xFF0B0C0F, 0xFF16181D, true, sc, glow);
        box(cv, 0.0f, 0.48f, 0.0f, 0.52f, 0.45f, 4.15f, mix(0xFF2B2D31, 0xFF6C6E73, lit), mix(0xFF1E2024, 0xFF55575C, lit), false, sc, 0f);

        // The far side: the oak slatted wall, its own strip along its top, the corner sofa, the coffee table on the rug.
        slats(cv, lit, glow, sc);
        furniture(cv, lit, glow, sc, h);

        // The strip itself, lit along its length (a new colour running along it); the slot; the downlights.
        float sl = strip.on ? strip.level : 0f;
        float len1 = D + 3f, len2 = W, total = len1 + len2;
        float lit1 = Math.min(1f, run * total / len1), lit2 = Math.max(0f, (run * total - len1) / len2);
        if (wave >= 0f) {
            float w1 = Math.min(1f, wave * total / len1), w2 = Math.max(0f, (wave * total - len1) / len2);
            tube(cv, stripOld, sl, 1f, 0.06f, H - bh + 0.02f, -3, 0.06f, H - bh + 0.02f, D);
            tube(cv, stripNew, sl, w1, 0.06f, H - bh + 0.02f, -3, 0.06f, H - bh + 0.02f, D);
            tube(cv, stripOld, sl, 1f, 0.06f, H - bh + 0.02f, D - 0.05f, W, H - bh + 0.02f, D - 0.05f);
            tube(cv, stripNew, sl, w2, 0.06f, H - bh + 0.02f, D - 0.05f, W, H - bh + 0.02f, D - 0.05f);
        } else {
            tube(cv, sc, sl, lit1, 0.06f, H - bh + 0.02f, -3, 0.06f, H - bh + 0.02f, D);
            tube(cv, sc, sl, lit2, 0.06f, H - bh + 0.02f, D - 0.05f, W, H - bh + 0.02f, D - 0.05f);
        }
        // The slot in the ceiling beside the bulkhead: a dark channel, lit in its own light.
        paint(cv, 0xFF0D0E12, 0.92f, H - 0.001f, -0.6f, 1.0f, H - 0.001f, -0.6f, 1.0f, H - 0.001f, 4.4f, 0.92f, H - 0.001f, 4.4f);
        float sv = slot.on ? slot.level : 0f;
        tube(cv, slot.colour, sv, 1f, 0.96f, H - 0.01f, -0.6f, 0.96f, H - 0.01f, 4.4f);
        wash(cv, slot.colour, sv * 0.35f, 0.96f, H - 0.002f, -0.6f, 0.96f, H - 0.002f, 4.4f, 0.7f, 0, 0);
        // The downlights: a row down the room; their light and its pool on the floor.
        float dl = down.on ? down.level : 0f;
        for (float z = 0.4f; z < D - 0.4f; z += 1.45f) downlight(cv, 2.05f, z, dl, down.colour, h);
        // A soft vignette, as a camera's.
        fill.setShader(new RadialGradient(w * 0.5f, h * 0.45f, Math.max(w, h) * 0.75f, 0x00000000, 0x8C000000, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
    }

    /** A box from (x0..x1, y0..y1, z0..z1), its faces nearest last; the TV's front glossy with the strip's light in it. */
    private void box(Canvas cv, float x0, float x1, float y0, float y1, float z0, float z1, int top, int side, boolean screen,
                     int colour, float glow) {
        // Seen from the camera's side of the room, at its right: the top, the front (x1) and the near end (z0).
        if (cy > y1) paint(cv, top, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        if (cz < z0) paint(cv, mix(side, 0xFF000000, 0.25f), x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        if (cz > z1) paint(cv, mix(side, 0xFF000000, 0.25f), x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        if (cx > x1) {
            if (!face(path, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0)) return;
            fill.setColor(side);
            cv.drawPath(path, fill);
            if (screen) {
                // The screen: black glass, the strip's colour caught along its top and a sheen across it.
                if (point(x1, y1, (z0 + z1) / 2f)) {
                    float ty = p3[1];
                    if (point(x1, y0, (z0 + z1) / 2f)) {
                        fill.setShader(new LinearGradient(0, ty, 0, p3[1], new int[]{alpha(colour, glow * 0.32f), alpha(colour, 0f), 0x14FFFFFF},
                                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
                        cv.drawPath(path, fill);
                        fill.setShader(null);
                    }
                }
            }
        }
    }

    // ---- the sofa side (from the family's photos) -----------------------------------------------------------------

    /** The slatted oak wall behind the sofa, from z 0 to 3.7 on the room's right-hand wall, dark between its slats. */
    private void slats(Canvas cv, float lit, float glow, int colour) {
        float x = W - 0.02f, z0 = 0.05f, z1 = D - 0.2f, top = H - 0.02f;
        paint(cv, mix(0xFF0C0A08, 0xFF2A211A, lit), x, 0, z0, x, 0, z1, x, top, z1, x, top, z0);
        int oak = mix(0xFF3B2516, 0xFFB57A4C, 0.2f + lit * 0.8f);
        oak = mix(oak, colour, glow * 0.12f);
        for (float z = z0 + 0.02f; z < z1 - 0.03f; z += 0.08f)
            paint(cv, oak, x - 0.025f, 0, z, x - 0.025f, 0, z + 0.045f, x - 0.025f, top, z + 0.045f, x - 0.025f, top, z);
        // Its strip's glow down the top of the slats.
        wash(cv, colour, Math.min(1f, glow * 1.1f), x - 0.03f, top, z0, x - 0.03f, top, z1, 0, -0.9f, 0);
        tube(cv, colour, glow, 1f, x - 0.04f, top - 0.02f, z0, x - 0.04f, top - 0.02f, z1);
    }

    /** One soft box, its faces shaded (top lightest): what the sofa, its cushions and the table are made of. */
    private static final class Block {
        float x0, x1, y0, y1, z0, z1;
        int colour;
        float round;

        Block(float x0, float x1, float y0, float y1, float z0, float z1, int colour, float round) {
            this.x0 = x0; this.x1 = x1; this.y0 = y0; this.y1 = y1; this.z0 = z0; this.z1 = z1;
            this.colour = colour;
            this.round = round;
        }
    }

    private final java.util.ArrayList<Block> blocks = new java.util.ArrayList<>();

    /** The corner sofa (grey velvet), its cushions (two mustard, two grey), the long coffee table and the rug under them. */
    private void furniture(Canvas cv, float lit, float glow, int colour, int h) {
        float L = 0.18f + lit * 0.82f;
        int velvet = mix(mix(0xFF2C2E33, 0xFF8E9096, L), colour, glow * 0.1f);
        int velvetTop = mix(velvet, 0xFFFFFFFF, 0.12f), velvetDark = mix(velvet, 0xFF000000, 0.25f);
        int mustard = mix(0xFF4A3A0E, 0xFFC9A030, L), grey = mix(0xFF26282C, 0xFF6C6F76, L);
        int concrete = mix(0xFF1E1F21, 0xFF55575A, L), black = 0xFF0E0F11;
        // The rug, light and mottled.
        paint(cv, mix(0xFF4A4B4E, 0xFFD9D8D3, L), W - 3.25f, 0.003f, 0.55f, W - 0.85f, 0.003f, 0.55f, W - 0.85f, 0.003f, D - 0.45f, W - 3.25f, 0.003f, D - 0.45f);
        blocks.clear();
        float bx = W - 0.08f, depth = 0.98f, seat = 0.44f, back = 0.92f, bt = 0.24f;
        // The long section along the slats, its arm at the far end; the corner and the chaise along the near side.
        // The long section runs all the way to the clear door at the far end.
        float za = 0.15f, zb = D - 0.32f, xc = W - 2.75f;
        blocks.add(new Block(bx - depth, bx, 0.08f, seat - 0.14f, za, zb, velvetDark, 0f));
        blocks.add(new Block(bx - bt, bx, 0.08f, back - 0.18f, za, zb, velvetDark, 0f));
        blocks.add(new Block(bx - depth, bx, 0.08f, 0.64f, zb, zb + 0.24f, velvet, 0.1f));
        blocks.add(new Block(xc, bx - depth, 0.08f, seat - 0.14f, za, za + depth, velvetDark, 0f));
        blocks.add(new Block(xc, bx - depth, 0.08f, back - 0.18f, za, za + bt, velvetDark, 0f));
        // Seat cushions: three along, two on the chaise.
        int seats = Math.max(3, Math.round((zb - za - depth) / 0.8f));
        float seg = (zb - za - depth) / seats;
        for (int i = 0; i < seats; i++) {
            float s0 = za + depth + i * seg;
            blocks.add(new Block(bx - depth + 0.02f, bx - bt, seat - 0.15f, seat, s0 + 0.01f, s0 + seg - 0.01f, velvet, 0.06f));
            blocks.add(new Block(bx - bt - 0.16f, bx - bt + 0.02f, seat, back - 0.02f, s0 + 0.02f, s0 + seg - 0.02f, velvet, 0.08f));
        }
        float cseg = (bx - depth - xc) / 2f;
        for (int i = 0; i < 2; i++) {
            float s0 = xc + i * cseg;
            blocks.add(new Block(s0 + 0.01f, s0 + cseg - 0.01f, seat - 0.15f, seat, za + bt, za + depth, velvet, 0.06f));
            blocks.add(new Block(s0 + 0.02f, s0 + cseg - 0.02f, seat, back - 0.02f, za + bt - 0.02f, za + bt + 0.16f, velvet, 0.08f));
        }
        // The corner seat.
        blocks.add(new Block(bx - depth + 0.02f, bx - bt, seat - 0.15f, seat, za + bt, za + depth, velvet, 0.06f));
        // The cushions: mustard on the long section, grey in the corner.
        blocks.add(new Block(bx - bt - 0.28f, bx - bt - 0.1f, seat + 0.02f, seat + 0.44f, za + depth + 0.2f, za + depth + 0.62f, mustard, 0.12f));
        blocks.add(new Block(bx - bt - 0.28f, bx - bt - 0.1f, seat + 0.02f, seat + 0.44f, zb - 0.55f, zb - 0.13f, mustard, 0.12f));
        blocks.add(new Block(bx - bt - 0.3f, bx - bt - 0.12f, seat + 0.02f, seat + 0.46f, za + bt + 0.08f, za + bt + 0.52f, grey, 0.12f));
        blocks.add(new Block(xc + 0.3f, xc + 0.74f, seat + 0.02f, seat + 0.46f, za + bt + 0.14f, za + bt + 0.32f, grey, 0.12f));
        // The long coffee table, concrete-dark on black legs, along the sofa.
        float tx0 = W - 2.05f, tx1 = W - 1.45f, tz0 = 1.35f, tz1 = D - 0.75f, ty = 0.44f;
        blocks.add(new Block(tx0, tx1, ty - 0.04f, ty, tz0, tz1, concrete, 0f));
        for (float[] leg : new float[][]{{tx0 + 0.04f, tz0 + 0.04f}, {tx1 - 0.09f, tz0 + 0.04f}, {tx0 + 0.04f, tz1 - 0.09f}, {tx1 - 0.09f, tz1 - 0.09f}})
            blocks.add(new Block(leg[0], leg[0] + 0.05f, 0f, ty - 0.04f, leg[1], leg[1] + 0.05f, black, 0f));
        // Farthest first.
        blocks.sort((a, b) -> Float.compare(far(b), far(a)));
        for (Block k : blocks) block(cv, k);
    }

    private float far(Block k) {
        float x = Math.max(k.x0, Math.min(cx, k.x1)), y = Math.max(k.y0, Math.min(cy, k.y1)), z = Math.max(k.z0, Math.min(cz, k.z1));
        float mx = (k.x0 + k.x1) / 2f, my = (k.y0 + k.y1) / 2f, mz = (k.z0 + k.z1) / 2f;
        float dx = (x + mx) / 2f - cx, dy = (y + my) / 2f - cy, dz = (z + mz) / 2f - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** A block's faces the camera sees: its sides darker, its top lighter, soft-edged where it is a cushion. */
    private void block(Canvas cv, Block k) {
        int c = k.colour, side = mix(c, 0xFF000000, 0.22f), end = mix(c, 0xFF000000, 0.35f), top = mix(c, 0xFFFFFFFF, 0.1f);
        fill.setPathEffect(k.round > 0 ? new android.graphics.CornerPathEffect(Math.max(1f, focal * k.round / Math.max(1f, dist(k)))) : null);
        if (cz < k.z0) paint(cv, end, k.x0, k.y0, k.z0, k.x1, k.y0, k.z0, k.x1, k.y1, k.z0, k.x0, k.y1, k.z0);
        if (cz > k.z1) paint(cv, end, k.x0, k.y0, k.z1, k.x1, k.y0, k.z1, k.x1, k.y1, k.z1, k.x0, k.y1, k.z1);
        if (cx < k.x0) paint(cv, side, k.x0, k.y0, k.z0, k.x0, k.y0, k.z1, k.x0, k.y1, k.z1, k.x0, k.y1, k.z0);
        if (cx > k.x1) paint(cv, side, k.x1, k.y0, k.z0, k.x1, k.y0, k.z1, k.x1, k.y1, k.z1, k.x1, k.y1, k.z0);
        if (cy > k.y1) paint(cv, top, k.x0, k.y1, k.z0, k.x1, k.y1, k.z0, k.x1, k.y1, k.z1, k.x0, k.y1, k.z1);
        fill.setPathEffect(null);
    }

    private float dist(Block k) {
        return (float) Math.sqrt(far(k));
    }

    /** A glazed oak door in the far wall from x, wide wd: its frame and its dark panes. */
    private void door(Canvas cv, float x, float wd, float lit, float glow, int colour) {
        float z = D - 0.012f, hgt = 2.05f;
        int oak = mix(0xFF3A2414, 0xFFB7793D, 0.25f + lit * 0.75f);
        paint(cv, oak, x, 0, z, x + wd, 0, z, x + wd, hgt, z, x, hgt, z);
        int glass = mix(0xFF07080A, 0xFF3A3F47, lit * 0.6f);
        glass = mix(glass, colour, glow * 0.12f);
        int cols = 2, rowsN = 5;
        float m = 0.09f, gap = 0.035f, pw = (wd - 2 * m - (cols - 1) * gap) / cols, ph = (hgt - 2 * m - (rowsN - 1) * gap) / rowsN;
        float z2 = z - 0.004f;
        for (int i = 0; i < cols; i++)
            for (int j = 0; j < rowsN; j++) {
                float px = x + m + i * (pw + gap), py = m + j * (ph + gap);
                paint(cv, glass, px, py, z2, px + pw, py, z2, px + pw, py + ph, z2, px, py + ph, z2);
            }
    }

    /** A glazed oak door in the TV's wall from z, wd wide: its frame and its dark panes, as the far wall's. */
    private void sideDoor(Canvas cv, float z, float wd, float lit, float glow, int colour) {
        float x = 0.012f, hgt = 2.05f;
        int oak = mix(0xFF3A2414, 0xFFB7793D, 0.25f + lit * 0.75f);
        paint(cv, oak, x, 0, z, x, 0, z + wd, x, hgt, z + wd, x, hgt, z);
        int glass = mix(mix(0xFF07080A, 0xFF3A3F47, lit * 0.6f), colour, glow * 0.12f);
        int cols = 2, rowsN = 5;
        float m = 0.09f, gap = 0.035f, pw = (wd - 2 * m - (cols - 1) * gap) / cols, ph = (hgt - 2 * m - (rowsN - 1) * gap) / rowsN;
        float x2 = x + 0.004f;
        for (int i = 0; i < cols; i++)
            for (int j = 0; j < rowsN; j++) {
                float pz = z + m + i * (pw + gap), py = m + j * (ph + gap);
                paint(cv, glass, x2, py, pz, x2, py, pz + pw, x2, py + ph, pz + pw, x2, py + ph, pz);
            }
    }

    /** The far wall's third door from x, wd wide: one wide pane of clear glass in a white frame, the room beyond through it. */
    private void clearDoor(Canvas cv, float x, float wd, float lit, float glow, int colour) {
        float z = D - 0.012f, hgt = 2.1f, edge = 0.075f;
        // Its white edging round the opening, a little proud of the wall.
        int white = mix(0xFF4A4C50, 0xFFF4F4F2, 0.3f + lit * 0.7f);
        paint(cv, white, x - edge, 0, z, x + wd + edge, 0, z, x + wd + edge, hgt + edge, z, x - edge, hgt + edge, z);
        // The glass: the dim hall beyond, a pale sheen across it, the strip's colour caught in it.
        float z2 = z - 0.004f;
        int beyond = mix(0xFF101318, 0xFF6B6A66, lit * 0.55f);
        beyond = mix(beyond, colour, glow * 0.1f);
        if (!face(path, x, 0, z2, x + wd, 0, z2, x + wd, hgt, z2, x, hgt, z2)) return;
        fill.setColor(beyond);
        cv.drawPath(path, fill);
        if (point(x, hgt, z2)) {
            float ax = p3[0], ay = p3[1];
            if (point(x + wd, 0, z2)) {
                fill.setShader(new LinearGradient(ax, ay, p3[0], p3[1], new int[]{0x2EFFFFFF, 0x00FFFFFF, 0x1AFFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.35f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                cv.drawPath(path, fill);
                fill.setShader(null);
            }
        }
        // Its thin white frame and the handle.
        float f = 0.035f, z3 = z2 - 0.002f;
        paint(cv, white, x, 0, z3, x + f, 0, z3, x + f, hgt, z3, x, hgt, z3);
        paint(cv, white, x + wd - f, 0, z3, x + wd, 0, z3, x + wd, hgt, z3, x + wd - f, hgt, z3);
        paint(cv, white, x, hgt - f, z3, x + wd, hgt - f, z3, x + wd, hgt, z3, x, hgt, z3);
        paint(cv, mix(0xFF6F737A, 0xFFDADDE2, lit), x + 0.08f, 1.0f, z3 - 0.002f, x + 0.12f, 1.0f, z3 - 0.002f, x + 0.12f, 1.12f, z3 - 0.002f, x + 0.08f, 1.12f, z3 - 0.002f);
    }

    /** A downlight at (x, z) in the ceiling: its lit disc, a soft cone and its pool of light on the floor. */
    private void downlight(Canvas cv, float x, float z, float level, int colour, int h) {
        if (!point(x, H, z)) return;
        float sx = p3[0], sy = p3[1], d = p3[2];
        float rr = focal * 0.07f / d;
        fill.setColor(level > 0 ? alpha(mix(colour, Color.WHITE, 0.7f), 0.35f + 0.65f * level) : 0xFF3A3D45);
        cv.drawOval(sx - rr, sy - rr * 0.38f, sx + rr, sy + rr * 0.38f, fill);
        if (level <= 0.003f) return;
        // Its glow round it.
        fill.setShader(new RadialGradient(sx, sy, rr * 4f, alpha(colour, 0.45f * level), alpha(colour, 0f), Shader.TileMode.CLAMP));
        cv.drawCircle(sx, sy, rr * 4f, fill);
        fill.setShader(null);
        // Its pool on the floor.
        if (point(x, 0, z)) {
            float fx = p3[0], fy = p3[1], fd = p3[2], pr = focal * 0.9f / fd;
            fill.setShader(new RadialGradient(fx, fy, pr, alpha(colour, 0.22f * level), alpha(colour, 0f), Shader.TileMode.CLAMP));
            cv.save();
            cv.scale(1f, 0.32f, fx, fy);
            cv.drawCircle(fx, fy, pr, fill);
            cv.restore();
            fill.setShader(null);
            // The cone between.
            path.reset();
            path.moveTo(sx - rr * 0.8f, sy);
            path.lineTo(sx + rr * 0.8f, sy);
            path.lineTo(fx + pr * 0.55f, fy);
            path.lineTo(fx - pr * 0.55f, fy);
            path.close();
            fill.setShader(new LinearGradient(0, sy, 0, fy, alpha(colour, 0.13f * level), alpha(colour, 0f), Shader.TileMode.CLAMP));
            cv.drawPath(path, fill);
            fill.setShader(null);
        }
    }

    /** The card's words over the room: the room, what changed, and the light's colour and brightness. */
    private void words(Canvas cv, int w, int h, float p) {
        float pad = h * 0.065f;
        // A shade behind the words so they read over any light.
        fill.setShader(new LinearGradient(0, 0, 0, h * 0.34f, 0xB3000000, 0x00000000, Shader.TileMode.CLAMP));
        r.set(0, 0, w, h * 0.34f);
        cv.drawRect(r, fill);
        fill.setShader(null);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(semi);
        text.setLetterSpacing(0.18f);
        text.setTextSize(h * 0.036f);
        text.setColor(0xB3FFFFFF);
        cv.drawText("LIVING ROOM", pad, pad + h * 0.036f, text);
        text.setLetterSpacing(0f);
        text.setTypeface(heavy);
        text.setTextSize(h * 0.075f);
        text.setColor(Color.WHITE);
        cv.drawText(title, pad, pad + h * 0.036f + h * 0.09f, text);
        text.setTypeface(medium);
        text.setTextSize(h * 0.042f);
        text.setColor(0xD9FFFFFF);
        cv.drawText(line, pad, pad + h * 0.036f + h * 0.09f + h * 0.065f, text);
        // The changed light's colour and level, at the top right: a swatch and its brightness.
        Glow g = between(from[STRIP], to[STRIP], p);
        int focus = focusGroup();
        g = between(from[focus], to[focus], p);
        float s = h * 0.085f, x = w - pad - s, y = pad;
        r.set(x, y, x + s, y + s);
        if (g.on) {
            fill.setColor(alpha(g.colour, 0.35f));
            cv.drawCircle(r.centerX(), r.centerY(), s * 0.75f, fill);
            fill.setColor(g.colour);
            cv.drawCircle(r.centerX(), r.centerY(), s * 0.5f, fill);
        } else {
            stroke.setColor(0x80FFFFFF);
            stroke.setStrokeWidth(Math.max(1f, s * 0.06f));
            cv.drawCircle(r.centerX(), r.centerY(), s * 0.45f, stroke);
        }
        text.setTypeface(heavy);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(h * 0.07f);
        text.setColor(Color.WHITE);
        String pct = g.on ? Math.round(g.level * 100) + "%" : "Off";
        cv.drawText(pct, x - s * 0.35f, y + s * 0.72f, text);
        // How bright, as a bar along the card's foot.
        float bar = h * 0.012f, by = h - pad * 0.7f;
        fill.setColor(0x33FFFFFF);
        r.set(pad, by - bar, w - pad, by);
        cv.drawRoundRect(r, bar, bar, fill);
        if (g.on) {
            fill.setColor(mix(g.colour, Color.WHITE, 0.2f));
            r.set(pad, by - bar, pad + (w - 2 * pad) * g.level, by);
            cv.drawRoundRect(r, bar, bar, fill);
        }
    }

    /** The group that changed (the first whose state differs), for the card's swatch; the strip when none does. */
    private int focusGroup() {
        for (int i = 0; i < 3; i++) {
            Glow a = from[i], b = to[i];
            if (a.on != b.on || Math.abs(a.level - b.level) > 0.001f || (b.on && a.colour != b.colour)) return i;
        }
        return STRIP;
    }
}
