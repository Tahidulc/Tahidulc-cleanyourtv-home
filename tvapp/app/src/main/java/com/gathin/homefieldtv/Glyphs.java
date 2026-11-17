package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * More line icons for Search, Movies and TV shows, Recordings and My list, drawn as Dark's are (strokes and small
 * fills in a box of side s centred on (cx, cy), screen pixels), Homefield's own drawings:
 *
 * <pre>
 *   TRASH      a bin with its lid (delete a recording, clear the search history)
 *   MIC        a microphone on its stand (voice search)
 *   EXTERNAL   a box with an arrow leaving it (open in another app: the trailer's)
 *   TRAILER    a strip of film (the trailer)
 *   BOOKMARK_ADD / BOOKMARK_ON  My list's bookmark with a plus, or filled once on it
 *   SORT       three lines, shorter each (the order of a grid)
 *   SHIFT, BACKSPACE, LEFT, RIGHT, SPACE  the keyboard's keys
 *   REMINDER   a bell (a reminder set)
 * </pre>
 */
final class Glyphs {
    private Glyphs() {}

    static final int TRASH = 0, MIC = 1, EXTERNAL = 2, TRAILER = 3, BOOKMARK_ADD = 4, BOOKMARK_ON = 5, SORT = 6, SHIFT = 7,
            BACKSPACE = 8, LEFT = 9, RIGHT = 10, SPACE = 11, REMINDER = 12;

    private static final Path P = new Path();
    private static final RectF R = new RectF();
    private static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG), FILL = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeCap(Paint.Cap.ROUND);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
    }

    /** An icon in a colour, its lines a thirteenth of its size thick. */
    static void draw(Canvas c, int kind, float cx, float cy, float s, int colour) {
        STROKE.setColor(colour);
        STROKE.setStrokeWidth(Math.max(1f, s * 0.075f));
        FILL.setColor(colour);
        draw(c, kind, cx, cy, s, STROKE, FILL);
    }

    static void draw(Canvas c, int kind, float cx, float cy, float s, Paint stroke, Paint fill) {
        float h = s / 2f;
        Path p = P;
        p.reset();
        switch (kind) {
            case TRASH:
                c.drawLine(cx - h * 0.72f, cy - h * 0.55f, cx + h * 0.72f, cy - h * 0.55f, stroke);
                c.drawLine(cx - h * 0.22f, cy - h * 0.8f, cx + h * 0.22f, cy - h * 0.8f, stroke);
                p.moveTo(cx - h * 0.55f, cy - h * 0.5f);
                p.lineTo(cx - h * 0.46f, cy + h * 0.82f);
                p.lineTo(cx + h * 0.46f, cy + h * 0.82f);
                p.lineTo(cx + h * 0.55f, cy - h * 0.5f);
                c.drawPath(p, stroke);
                c.drawLine(cx - h * 0.17f, cy - h * 0.22f, cx - h * 0.17f, cy + h * 0.52f, stroke);
                c.drawLine(cx + h * 0.17f, cy - h * 0.22f, cx + h * 0.17f, cy + h * 0.52f, stroke);
                break;
            case MIC:
                R.set(cx - h * 0.28f, cy - h * 0.85f, cx + h * 0.28f, cy + h * 0.2f);
                c.drawRoundRect(R, h * 0.28f, h * 0.28f, fill);
                R.set(cx - h * 0.55f, cy - h * 0.45f, cx + h * 0.55f, cy + h * 0.45f);
                c.drawArc(R, 0, 180, false, stroke);
                c.drawLine(cx, cy + h * 0.45f, cx, cy + h * 0.82f, stroke);
                break;
            case EXTERNAL:
                p.moveTo(cx + h * 0.05f, cy - h * 0.7f);
                p.lineTo(cx - h * 0.7f, cy - h * 0.7f);
                p.lineTo(cx - h * 0.7f, cy + h * 0.7f);
                p.lineTo(cx + h * 0.7f, cy + h * 0.7f);
                p.lineTo(cx + h * 0.7f, cy - h * 0.05f);
                c.drawPath(p, stroke);
                c.drawLine(cx - h * 0.1f, cy + h * 0.1f, cx + h * 0.78f, cy - h * 0.78f, stroke);
                c.drawLine(cx + h * 0.3f, cy - h * 0.78f, cx + h * 0.78f, cy - h * 0.78f, stroke);
                c.drawLine(cx + h * 0.78f, cy - h * 0.78f, cx + h * 0.78f, cy - h * 0.3f, stroke);
                break;
            case TRAILER:
                c.drawRect(cx - h * 0.55f, cy - h * 0.82f, cx + h * 0.55f, cy + h * 0.82f, stroke);
                c.drawLine(cx - h * 0.25f, cy - h * 0.82f, cx - h * 0.25f, cy + h * 0.82f, stroke);
                c.drawLine(cx + h * 0.25f, cy - h * 0.82f, cx + h * 0.25f, cy + h * 0.82f, stroke);
                for (int i = -1; i <= 1; i++) {
                    c.drawLine(cx - h * 0.55f, cy + i * h * 0.45f, cx - h * 0.25f, cy + i * h * 0.45f, stroke);
                    c.drawLine(cx + h * 0.25f, cy + i * h * 0.45f, cx + h * 0.55f, cy + i * h * 0.45f, stroke);
                }
                break;
            case BOOKMARK_ADD:
            case BOOKMARK_ON:
                p.moveTo(cx + h * 0.05f, cy - h * 0.78f);
                p.lineTo(cx - h * 0.6f, cy - h * 0.78f);
                p.lineTo(cx - h * 0.6f, cy + h * 0.86f);
                p.lineTo(cx - h * 0.05f, cy + h * 0.42f);
                p.lineTo(cx + h * 0.5f, cy + h * 0.86f);
                p.lineTo(cx + h * 0.5f, cy - h * 0.1f);
                if (kind == BOOKMARK_ON) {
                    p.lineTo(cx + h * 0.5f, cy - h * 0.78f);
                    p.close();
                    c.drawPath(p, fill);
                    c.drawPath(p, stroke);
                } else {
                    c.drawPath(p, stroke);
                    c.drawLine(cx + h * 0.5f, cy - h * 0.92f, cx + h * 0.5f, cy - h * 0.38f, stroke);
                    c.drawLine(cx + h * 0.23f, cy - h * 0.65f, cx + h * 0.77f, cy - h * 0.65f, stroke);
                }
                break;
            case SORT:
                c.drawLine(cx - h * 0.75f, cy - h * 0.5f, cx + h * 0.75f, cy - h * 0.5f, stroke);
                c.drawLine(cx - h * 0.75f, cy, cx + h * 0.3f, cy, stroke);
                c.drawLine(cx - h * 0.75f, cy + h * 0.5f, cx - h * 0.15f, cy + h * 0.5f, stroke);
                break;
            case SHIFT:
                p.moveTo(cx, cy - h * 0.78f);
                p.lineTo(cx + h * 0.72f, cy);
                p.lineTo(cx + h * 0.3f, cy);
                p.lineTo(cx + h * 0.3f, cy + h * 0.6f);
                p.lineTo(cx - h * 0.3f, cy + h * 0.6f);
                p.lineTo(cx - h * 0.3f, cy);
                p.lineTo(cx - h * 0.72f, cy);
                p.close();
                c.drawPath(p, stroke);
                break;
            case BACKSPACE:
                p.moveTo(cx - h * 0.9f, cy);
                p.lineTo(cx - h * 0.45f, cy - h * 0.55f);
                p.lineTo(cx + h * 0.85f, cy - h * 0.55f);
                p.lineTo(cx + h * 0.85f, cy + h * 0.55f);
                p.lineTo(cx - h * 0.45f, cy + h * 0.55f);
                p.close();
                c.drawPath(p, stroke);
                c.drawLine(cx - h * 0.05f, cy - h * 0.25f, cx + h * 0.45f, cy + h * 0.25f, stroke);
                c.drawLine(cx + h * 0.45f, cy - h * 0.25f, cx - h * 0.05f, cy + h * 0.25f, stroke);
                break;
            case LEFT:
            case RIGHT: {
                float d = kind == LEFT ? -1f : 1f;
                p.moveTo(cx + d * h * 0.4f, cy);
                p.lineTo(cx - d * h * 0.25f, cy - h * 0.45f);
                p.lineTo(cx - d * h * 0.25f, cy + h * 0.45f);
                p.close();
                c.drawPath(p, fill);
                break;
            }
            case SPACE:
                p.moveTo(cx - h * 0.8f, cy - h * 0.2f);
                p.lineTo(cx - h * 0.8f, cy + h * 0.2f);
                p.lineTo(cx + h * 0.8f, cy + h * 0.2f);
                p.lineTo(cx + h * 0.8f, cy - h * 0.2f);
                c.drawPath(p, stroke);
                break;
            case REMINDER:
                Dark.bell(c, cx, cy, h * 0.85f, fill);
                break;
            default:
                break;
        }
    }
}
