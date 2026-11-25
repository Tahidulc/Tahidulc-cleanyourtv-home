package com.gathin.broadcast;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The goal animations' shapes (GoalReveal), as silhouettes in one colour: what a club is known by where the season
 * intros draw it themselves (season.js: Arsenal's cannon, Brighton's gull, Everton's tower, Leeds' white rose, United's
 * trident, Deportivo's lighthouse, Bologna's two towers, Augsburg's pine cone, Villarreal's submarine), drawn again our
 * own way for a strip a few dozen pixels high: bold outlines, a few holes for their detail; and the generic's football.
 * Each is built once per goal into a path in a box 100 high (build), then fitted to the strip.
 */
public final class GoalShapes {
    private GoalShapes() { }

    /** The shapes there are ("ball" for anything else). */
    public static final String[] NAMES = {"cannon", "gull", "tower", "rose", "trident", "lighthouse", "towers", "pinecone",
            "submarine", "ball"};

    /** Pure: whether a shape is one of ours (else the football stands in). */
    public static boolean has(String name) {
        for (String n : NAMES) if (n.equals(name)) return true;
        return false;
    }

    /**
     * The shape into out (reset first), in a box (0, 0) to (width, 100), facing right where it faces; returns the box's
     * width. Its holes are cut out (Path.op), so it fills in one colour.
     */
    public static float build(String name, Path out) {
        out.reset();
        out.setFillType(Path.FillType.WINDING);
        Path holes = new Path();
        float w;
        switch (has(name) ? name : "ball") {
            case "cannon": w = cannon(out, holes); break;
            case "gull": w = gull(out); break;
            case "tower": w = tower(out, holes); break;
            case "rose": w = rose(out, holes); break;
            case "trident": w = trident(out); break;
            case "lighthouse": w = lighthouse(out, holes); break;
            case "towers": w = towers(out, holes); break;
            case "pinecone": w = pinecone(out, holes); break;
            case "submarine": w = submarine(out, holes); break;
            default: w = ball(out, holes); break;
        }
        if (!holes.isEmpty()) out.op(holes, Path.Op.DIFFERENCE);
        return w;
    }

    /** The shape fitted into a box of w x h about (0, 0), mirrored when flip (to face the other way); returns its size {w, h}. */
    public static float[] fit(String name, Path out, float boxW, float boxH, boolean flip) {
        float bw = build(name, out);
        float k = Math.min(boxW / bw, boxH / 100f);
        Matrix m = new Matrix();
        m.setTranslate(-bw / 2f, -50f);
        m.postScale(flip ? -k : k, k);
        out.transform(m);
        return new float[]{bw * k, 100f * k};
    }

    // ---- the shapes, each in a box 100 high --------------------------------------------------------------------------

    private static void poly(Path p, float... xy) {
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.close();
    }

    private static void circle(Path p, float cx, float cy, float r) {
        p.addCircle(cx, cy, r, Path.Direction.CW);
    }

    private static void rect(Path p, float l, float t, float r, float b) {
        p.addRect(l, t, r, b, Path.Direction.CW);
    }

    private static void round(Path p, float l, float t, float r, float b, float rad) {
        p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW);
    }

    /** A bar from (x0, y0) to (x1, y1), w wide. */
    private static void bar(Path p, float x0, float y0, float x1, float y1, float w) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
        if (len <= 0) return;
        float nx = -dy / len * w / 2f, ny = dx / len * w / 2f;
        poly(p, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny);
    }

    /** Side on, facing right, raised a little: the barrel with its breech knob and muzzle band, the carriage, a spoked wheel. */
    private static float cannon(Path p, Path holes) {
        Path barrel = new Path();
        poly(barrel, 22, 46, 128, 34, 128, 58, 22, 66);
        circle(barrel, 22, 56, 10);
        circle(barrel, 10, 56, 6);
        rect(barrel, 122, 30, 136, 62);
        rect(barrel, 60, 38, 68, 66);
        Matrix m = new Matrix();
        m.setRotate(-11f, 70, 50);
        barrel.transform(m);
        p.addPath(barrel);
        poly(p, 30, 66, 116, 70, 116, 82, 40, 84);
        // the wheel: a ring, six spokes, the hub
        circle(p, 66, 74, 24);
        Path inner = new Path();
        circle(inner, 66, 74, 17);
        Path spokes = new Path();
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * i / 3;
            float dx = (float) Math.cos(a) * 18, dy = (float) Math.sin(a) * 18;
            bar(spokes, 66 - dx, 74 - dy, 66 + dx, 74 + dy, 4.5f);
        }
        circle(spokes, 66, 74, 6);
        inner.op(spokes, Path.Op.DIFFERENCE);
        holes.addPath(inner);
        return 150;
    }

    /** A gull gliding right, wings up in a wide M, its head and beak. */
    private static float gull(Path p) {
        p.moveTo(10, 46);
        p.quadTo(40, 18, 74, 50);
        p.quadTo(84, 58, 92, 52);
        p.quadTo(118, 16, 168, 30);
        p.quadTo(126, 34, 104, 62);
        p.quadTo(98, 72, 84, 72);
        p.quadTo(70, 72, 62, 62);
        p.quadTo(42, 40, 10, 46);
        p.close();
        // the body and the head forward, the beak
        p.addOval(new RectF(62, 54, 122, 76), Path.Direction.CW);
        circle(p, 124, 58, 9);
        poly(p, 131, 55, 146, 60, 131, 63);
        // the tail behind
        poly(p, 64, 62, 44, 58, 48, 70, 66, 70);
        return 170;
    }

    /** A round lock-up tower: the body, a band under a conical roof, three arched windows and a door. */
    private static float tower(Path p, Path holes) {
        rect(p, 10, 38, 50, 100);
        poly(p, 30, 2, 54, 36, 6, 36);
        rect(p, 4, 33, 56, 40);
        for (float x : new float[]{14, 26, 38}) {
            round(holes, x, 48, x + 8, 64, 4);
            rect(holes, x, 56, x + 8, 64);
        }
        round(holes, 23, 78, 37, 100.5f, 7);
        rect(holes, 23, 88, 37, 100.5f);
        return 60;
    }

    /** The white rose: five outer petals, five inner, its heart, the sepals' tips between. */
    private static float rose(Path p, Path holes) {
        float c = 50;
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
            circle(p, c + (float) Math.cos(a) * 24, c + (float) Math.sin(a) * 24, 22);
            double b = a + Math.PI / 5;
            float tx = c + (float) Math.cos(b) * 49, ty = c + (float) Math.sin(b) * 49;
            float bx = c + (float) Math.cos(b - 0.22) * 34, by = c + (float) Math.sin(b - 0.22) * 34;
            float ex = c + (float) Math.cos(b + 0.22) * 34, ey = c + (float) Math.sin(b + 0.22) * 34;
            poly(p, bx, by, tx, ty, ex, ey);
        }
        // the inner petals as a ring of light lines: a gap round the heart, the heart itself
        Path ring = new Path();
        circle(ring, c, c, 19);
        Path heart = new Path();
        circle(heart, c, c, 14);
        for (int i = 0; i < 5; i++) {
            double a = -Math.PI / 2 + Math.PI / 5 + i * 2 * Math.PI / 5;
            bar(heart, c, c, c + (float) Math.cos(a) * 20, c + (float) Math.sin(a) * 20, 3.5f);
        }
        ring.op(heart, Path.Op.DIFFERENCE);
        holes.addPath(ring);
        Path dots = new Path();
        circle(dots, c, c, 5);
        holes.addPath(dots);
        return 100;
    }

    /** A trident upright: the long shaft, the curved crossbar, three barbed prongs. */
    private static float trident(Path p) {
        rect(p, 23, 30, 29, 100);
        p.moveTo(6, 22);
        p.quadTo(8, 40, 26, 41);
        p.quadTo(44, 40, 46, 22);
        p.lineTo(41, 22);
        p.quadTo(39, 35, 26, 35);
        p.quadTo(13, 35, 11, 22);
        p.close();
        float[][] prongs = {{8.5f, 6}, {26, 0}, {43.5f, 6}};
        for (float[] pr : prongs) {
            float x = pr[0], top = pr[1];
            poly(p, x - 2.6f, 24, x - 2.6f, top + 9, x - 7, top + 13, x, top, x + 7, top + 13, x + 2.6f, top + 9, x + 2.6f, 24);
        }
        rect(p, 21, 54, 31, 58);
        rect(p, 21, 90, 31, 94);
        return 52;
    }

    /** A lighthouse: the tower with its bands cut out, the gallery, the lantern lit, its roof, and its beams. */
    private static float lighthouse(Path p, Path holes) {
        float cx = 60;
        poly(p, cx - 10, 30, cx + 10, 30, cx + 16, 100, cx - 16, 100);
        rect(p, cx - 15, 26, cx + 15, 31);
        rect(p, cx - 9, 13, cx + 9, 27);
        poly(p, cx - 11, 14, cx, 3, cx + 11, 14);
        // the bands and the door
        poly(holes, cx - 11.3f, 46, cx + 11.3f, 46, cx + 12.1f, 54, cx - 12.1f, 54);
        poly(holes, cx - 13, 68, cx + 13, 68, cx + 13.9f, 76, cx - 13.9f, 76);
        round(holes, cx - 4, 88, cx + 4, 100.5f, 4);
        rect(holes, cx - 5, 16, cx + 5, 25);
        // the beams either side
        poly(p, cx - 12, 18, 0, 8, 0, 26, cx - 12, 22);
        poly(p, cx + 12, 18, 120, 8, 120, 26, cx + 12, 22);
        return 120;
    }

    /** Two tall towers, the second lower and leaning, their tops crenellated, slit windows. */
    private static float towers(Path p, Path holes) {
        rect(p, 6, 8, 28, 100);
        for (int i = 0; i < 3; i++) rect(p, 5 + i * 8.5f, 2, 10.5f + i * 8.5f, 9);
        Path second = new Path();
        rect(second, 36, 36, 60, 100);
        for (int i = 0; i < 3; i++) rect(second, 35 + i * 9, 30, 41 + i * 9, 37);
        Path slits2 = new Path();
        for (int i = 0; i < 4; i++) rect(slits2, 46, 44 + i * 14, 50, 52 + i * 14);
        second.op(slits2, Path.Op.DIFFERENCE);
        Matrix m = new Matrix();
        m.setRotate(5f, 48, 100);
        second.transform(m);
        p.addPath(second);
        for (int i = 0; i < 6; i++) rect(holes, 15, 16 + i * 13, 19, 24 + i * 13);
        return 68;
    }

    /** The city's pine cone: an egg of scales in rows (each scale's edge cut), its stalk's leaves on top. */
    private static float pinecone(Path p, Path holes) {
        p.addOval(new RectF(6, 18, 64, 100), Path.Direction.CW);
        poly(p, 35, 0, 46, 20, 24, 20);
        poly(p, 22, 6, 36, 20, 20, 18);
        poly(p, 48, 6, 50, 18, 34, 20);
        for (int r = 0; r < 6; r++) {
            float y = 32 + r * 11.5f;
            for (int c = -2; c <= 2; c++) {
                float x = 35 + c * 12 + (r % 2 == 0 ? 0 : 6);
                Path v = new Path();
                v.moveTo(x - 6, y);
                v.quadTo(x, y + 8, x + 6, y);
                v.lineTo(x + 6, y + 2.4f);
                v.quadTo(x, y + 10.4f, x - 6, y + 2.4f);
                v.close();
                holes.addPath(v);
            }
        }
        return 70;
    }

    /** The submarine heading right: its hull, the tower, the periscope, portholes, the propeller behind. */
    private static float submarine(Path p, Path holes) {
        p.addOval(new RectF(14, 40, 150, 84), Path.Direction.CW);
        round(p, 64, 24, 102, 48, 8);
        rect(p, 84, 6, 89, 26);
        rect(p, 84, 6, 100, 11);
        poly(p, 16, 62, 2, 48, 2, 76);
        for (float x : new float[]{44, 70, 96, 122}) circle(holes, x, 61, 6.5f);
        return 152;
    }

    /** The generic's football: a disc, its middle pentagon and the five round its edge cut out. */
    private static float ball(Path p, Path holes) {
        float c = 50, r = 48;
        circle(p, c, c, r);
        pentagon(holes, c, c, 15, -90);
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + 36 + i * 72);
            pentagon(holes, c + (float) Math.cos(a) * 40, c + (float) Math.sin(a) * 40, 14, (float) Math.toDegrees(a) + 90);
        }
        // the seams between: thin bars from the middle pentagon's corners to the edge ones
        Path seams = new Path();
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(-90 + i * 72);
            bar(seams, c + (float) Math.cos(a) * 15, c + (float) Math.sin(a) * 15, c + (float) Math.cos(a) * 30,
                    c + (float) Math.sin(a) * 30, 3f);
        }
        holes.addPath(seams);
        // its rim kept whole, so it stays round
        Path inner = new Path();
        circle(inner, c, c, r * 0.86f);
        holes.op(inner, Path.Op.INTERSECT);
        return 100;
    }

    private static void pentagon(Path p, float cx, float cy, float r, float turnDeg) {
        float[] xy = new float[10];
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(turnDeg + i * 72);
            xy[2 * i] = cx + (float) Math.cos(a) * r;
            xy[2 * i + 1] = cy + (float) Math.sin(a) * r;
        }
        poly(p, xy);
    }
}
