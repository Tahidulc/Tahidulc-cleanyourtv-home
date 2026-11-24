package com.gathin.broadcast;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws a LogoSpec into a crisp see-through picture: its shape filled with its ground, its one mark from the fixed set
 * (a star, a bolt, a ball, play, a wave, a chevron, a crown, a diamond, the sun, a house, rings) in its accent, its words
 * fitted to the room in its bundled typeface (leaning when italic, outlined in the accent when asked), the second line
 * under them. Wide layouts are drawn 2.2 times as wide as high; the monogram and the roundel square.
 */
public final class LogoArt {
    private LogoArt() {
    }

    /** Whether a layout is drawn square. */
    public static boolean square(LogoSpec s) {
        return s.layout == LogoSpec.Layout.MONOGRAM || s.layout == LogoSpec.Layout.ROUNDEL || s.shape == LogoSpec.Shape.CIRCLE;
    }

    /** The logo h pixels high (as wide as its layout needs), see-through where it has nothing. */
    public static Bitmap render(Context c, LogoSpec spec, int h) {
        LogoSpec s = spec.copy();
        int hh = Math.max(32, h), w = square(s) ? hh : Math.round(hh * 2.2f);
        Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        draw(new Canvas(b), s, w, hh, BoardPaint.typeface(c, s.font));
        return b;
    }

    /** Drawn into a canvas w by h with the typeface given (the tests' previews too). */
    public static void draw(Canvas cv, LogoSpec s, int w, int h, Typeface tf) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = h * 0.06f;
        RectF box = new RectF(pad, pad, w - pad, h - pad);
        Path shape = shape(s.shape, box);
        if (shape != null && s.ground != 0) {
            p.setColor(s.ground);
            cv.drawPath(shape, p);
            // A soft light along its top, as a broadcaster's plate has.
            p.setColor(0x1FFFFFFF);
            cv.save();
            cv.clipPath(shape);
            cv.drawRect(box.left, box.top, box.right, box.top + box.height() * 0.42f, p);
            cv.restore();
        }
        if (s.layout == LogoSpec.Layout.ROUNDEL || s.mark == LogoSpec.Mark.RINGS) {
            p.setStyle(Paint.Style.STROKE);
            p.setColor(s.accent);
            float r = Math.min(box.width(), box.height()) / 2f;
            p.setStrokeWidth(Math.max(2f, r * 0.08f));
            cv.drawCircle(box.centerX(), box.centerY(), r * 0.92f, p);
            p.setStrokeWidth(Math.max(1f, r * 0.03f));
            cv.drawCircle(box.centerX(), box.centerY(), r * 0.78f, p);
            p.setStyle(Paint.Style.FILL);
        }
        // Inside: the room the words and the mark share.
        RectF in = new RectF(box);
        float inset = shape == null || s.ground == 0 ? 0 : s.shape == LogoSpec.Shape.CIRCLE ? box.width() * 0.17f : h * 0.12f;
        if (s.shape == LogoSpec.Shape.SLANT && s.ground != 0) inset = h * 0.16f;
        if (s.layout == LogoSpec.Layout.ROUNDEL) inset = Math.max(inset, Math.min(box.width(), box.height()) * 0.24f);
        in.inset(inset, inset * 0.7f);
        boolean marked = s.mark != LogoSpec.Mark.NONE && s.mark != LogoSpec.Mark.RINGS;
        if (marked) {
            float m = s.side == LogoSpec.Side.TOP ? in.height() * 0.38f : Math.min(in.height() * 0.62f, in.width() * 0.28f);
            RectF mr;
            if (s.side == LogoSpec.Side.TOP) {
                mr = new RectF(in.centerX() - m / 2, in.top, in.centerX() + m / 2, in.top + m);
                in.top += m * 1.1f;
            } else if (s.side == LogoSpec.Side.RIGHT) {
                mr = new RectF(in.right - m, in.centerY() - m / 2, in.right, in.centerY() + m / 2);
                in.right -= m * 1.18f;
            } else {
                mr = new RectF(in.left, in.centerY() - m / 2, in.left + m, in.centerY() + m / 2);
                in.left += m * 1.18f;
            }
            mark(cv, p, s.mark, mr, s.accent, s.ground != 0 && shape != null ? s.ground : 0x00000000);
        }
        boolean two = !s.sub.isEmpty();
        float mainShare = s.layout == LogoSpec.Layout.STACKED ? 0.52f : two ? 0.62f : 0.86f;
        RectF line1 = new RectF(in.left, in.top, in.right, in.top + in.height() * (two ? mainShare : 1f));
        RectF line2 = new RectF(in.left, line1.bottom + in.height() * 0.04f, in.right, in.bottom);
        if (!two) line1.inset(0, in.height() * 0.04f);
        boolean left = s.layout == LogoSpec.Layout.STACKED;
        words(cv, s.text, line1, tf, s.ink, s.outline ? s.accent : 0, s.italic, left, 0.02f, two ? 1f : 0.82f);
        if (two) words(cv, s.sub, line2, tf, s.subInk, 0, s.italic, left, s.layout == LogoSpec.Layout.STACKED ? 0.02f : 0.14f, 0.9f);
    }

    /** One line of words fitted into r (as high as r allows, narrower when it must), centred or from the left. */
    private static void words(Canvas cv, String t, RectF r, Typeface tf, int ink, int outline, boolean italic, boolean left, float spacing,
                              float fill) {
        if (t == null || t.isEmpty() || r.width() <= 2 || r.height() <= 2) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(tf);
        p.setLetterSpacing(spacing);
        p.setTextSkewX(italic ? -0.2f : 0f);
        float size = r.height() * fill / 0.74f;
        p.setTextSize(size);
        android.graphics.Rect b = new android.graphics.Rect();
        p.getTextBounds(t, 0, t.length(), b);
        float wNow = p.measureText(t);
        if (wNow > r.width()) {
            size *= r.width() / wNow;
            p.setTextSize(size);
            p.getTextBounds(t, 0, t.length(), b);
            wNow = p.measureText(t);
        }
        float x = left ? r.left : r.centerX() - wNow / 2f;
        float y = r.centerY() - (b.top + b.bottom) / 2f;
        if (outline != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(Math.max(2f, size * 0.09f));
            p.setColor(outline);
            cv.drawText(t, x, y, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(ink);
        cv.drawText(t, x, y, p);
    }

    /** The shape's outline in a box, or null for none. */
    static Path shape(LogoSpec.Shape shape, RectF b) {
        Path p = new Path();
        float h = b.height();
        switch (shape) {
            case ROUNDED:
                p.addRoundRect(b, h * 0.18f, h * 0.18f, Path.Direction.CW);
                return p;
            case PILL:
                p.addRoundRect(b, h / 2f, h / 2f, Path.Direction.CW);
                return p;
            case CIRCLE: {
                float r = Math.min(b.width(), b.height()) / 2f;
                p.addCircle(b.centerX(), b.centerY(), r, Path.Direction.CW);
                return p;
            }
            case SHIELD: {
                float l = b.left, r = b.right, t = b.top, m = b.top + h * 0.55f, bot = b.bottom;
                p.moveTo(l, t);
                p.lineTo(r, t);
                p.lineTo(r, m);
                p.quadTo(r, t + h * 0.82f, b.centerX(), bot);
                p.quadTo(l, t + h * 0.82f, l, m);
                p.close();
                return p;
            }
            case HEXAGON: {
                float k = Math.min(b.width() * 0.25f, h * 0.45f);
                p.moveTo(b.left + k, b.top);
                p.lineTo(b.right - k, b.top);
                p.lineTo(b.right, b.centerY());
                p.lineTo(b.right - k, b.bottom);
                p.lineTo(b.left + k, b.bottom);
                p.lineTo(b.left, b.centerY());
                p.close();
                return p;
            }
            case SLANT: {
                float k = h * 0.28f;
                p.moveTo(b.left + k, b.top);
                p.lineTo(b.right, b.top);
                p.lineTo(b.right - k, b.bottom);
                p.lineTo(b.left, b.bottom);
                p.close();
                return p;
            }
            default:
                return null;
        }
    }

    /** One of the fixed marks in its box, in colour; cut (play's triangle, the house's door) in the ground or clear. */
    static void mark(Canvas cv, Paint p, LogoSpec.Mark m, RectF r, int colour, int cut) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(colour);
        float cx = r.centerX(), cy = r.centerY(), s = Math.min(r.width(), r.height()), h = s / 2f;
        Path path = new Path();
        switch (m) {
            case STAR: {
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI / 2 + i * Math.PI / 5;
                    float rr = i % 2 == 0 ? h : h * 0.42f;
                    float x = cx + (float) Math.cos(a) * rr, y = cy - (float) Math.sin(a) * rr;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
                cv.drawPath(path, p);
                break;
            }
            case BOLT:
                path.moveTo(cx + h * 0.15f, cy - h);
                path.lineTo(cx - h * 0.55f, cy + h * 0.12f);
                path.lineTo(cx - h * 0.02f, cy + h * 0.12f);
                path.lineTo(cx - h * 0.2f, cy + h);
                path.lineTo(cx + h * 0.58f, cy - h * 0.16f);
                path.lineTo(cx + h * 0.05f, cy - h * 0.16f);
                path.close();
                cv.drawPath(path, p);
                break;
            case BALL: {
                cv.drawCircle(cx, cy, h, p);
                p.setColor(cut == 0 ? 0xFF15181F : cut);
                for (int i = 0; i < 5; i++) {
                    double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
                    float x = cx + (float) Math.cos(a) * h * 0.34f, y = cy + (float) Math.sin(a) * h * 0.34f;
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                path.close();
                cv.drawPath(path, p);
                break;
            }
            case PLAY:
                cv.drawCircle(cx, cy, h, p);
                path.moveTo(cx - h * 0.3f, cy - h * 0.45f);
                path.lineTo(cx + h * 0.5f, cy);
                path.lineTo(cx - h * 0.3f, cy + h * 0.45f);
                path.close();
                p.setColor(cut == 0 ? 0xFF15181F : cut);
                cv.drawPath(path, p);
                break;
            case WAVE: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, s * 0.12f));
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int k = 0; k < 2; k++) {
                    Path w = new Path();
                    float y0 = cy - h * 0.25f + k * h * 0.5f;
                    w.moveTo(cx - h, y0);
                    w.cubicTo(cx - h * 0.5f, y0 - h * 0.4f, cx, y0 + h * 0.4f, cx + h * 0.5f, y0);
                    w.quadTo(cx + h * 0.75f, y0 - h * 0.2f, cx + h, y0);
                    cv.drawPath(w, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case CHEVRON:
                for (int k = 0; k < 2; k++) {
                    Path c = new Path();
                    float x0 = cx - h * 0.9f + k * h * 0.85f;
                    c.moveTo(x0, cy - h);
                    c.lineTo(x0 + h * 0.4f, cy - h);
                    c.lineTo(x0 + h * 1.05f, cy);
                    c.lineTo(x0 + h * 0.4f, cy + h);
                    c.lineTo(x0, cy + h);
                    c.lineTo(x0 + h * 0.65f, cy);
                    c.close();
                    cv.drawPath(c, p);
                }
                break;
            case CROWN:
                path.moveTo(cx - h, cy + h * 0.6f);
                path.lineTo(cx - h, cy - h * 0.5f);
                path.lineTo(cx - h * 0.45f, cy);
                path.lineTo(cx, cy - h * 0.8f);
                path.lineTo(cx + h * 0.45f, cy);
                path.lineTo(cx + h, cy - h * 0.5f);
                path.lineTo(cx + h, cy + h * 0.6f);
                path.close();
                cv.drawPath(path, p);
                break;
            case DIAMOND:
                path.moveTo(cx, cy - h);
                path.lineTo(cx + h * 0.75f, cy);
                path.lineTo(cx, cy + h);
                path.lineTo(cx - h * 0.75f, cy);
                path.close();
                cv.drawPath(path, p);
                break;
            case SUN: {
                cv.drawCircle(cx, cy, h * 0.5f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, s * 0.08f));
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    cv.drawLine(cx + (float) Math.cos(a) * h * 0.7f, cy + (float) Math.sin(a) * h * 0.7f,
                            cx + (float) Math.cos(a) * h * 0.98f, cy + (float) Math.sin(a) * h * 0.98f, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
            }
            case HOUSE:
                path.moveTo(cx - h * 0.85f, cy + h * 0.85f);
                path.lineTo(cx - h * 0.85f, cy - h * 0.1f);
                path.lineTo(cx, cy - h * 0.85f);
                path.lineTo(cx + h * 0.85f, cy - h * 0.1f);
                path.lineTo(cx + h * 0.85f, cy + h * 0.85f);
                path.close();
                cv.drawPath(path, p);
                p.setColor(cut == 0 ? 0xFF15181F : cut);
                cv.drawRect(cx - h * 0.22f, cy + h * 0.25f, cx + h * 0.22f, cy + h * 0.85f, p);
                break;
            default:
                break;
        }
    }
}
