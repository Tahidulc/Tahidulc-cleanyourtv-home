package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * The glass pages' line icons, drawn rather than read: on a 24-unit square, in round-capped strokes, so they stay crisp
 * at any size and take any colour (the tab bar's, the pills', the cover page's buttons).
 */
public final class Icons extends Drawable {
    public static final int HOME = 0, SPORTS = 1, LIVE = 2, WATCH = 3, SETTINGS = 4, SEARCH = 5, CHEVRON = 6, BACK = 7,
            MORE = 8, PLAY = 9, PLUS = 10, CHECK = 11, HEART = 12, DOWN = 13, CLOSE = 14, FILM = 15, SHARE = 16, STAR = 17;
    private final int kind;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();
    private final RectF r = new RectF();
    private final int size;

    public Icons(Context c, int kind, int colour) {
        this.kind = kind;
        size = Glass.dp(c, 24);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setColour(colour);
    }

    public void setColour(int colour) {
        stroke.setColor(colour);
        fill.setColor(colour);
        invalidateSelf();
    }

    @Override public int getIntrinsicWidth() {
        return size;
    }

    @Override public int getIntrinsicHeight() {
        return size;
    }

    @Override public void draw(@NonNull Canvas cv) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float u = Math.min(b.width(), b.height()) / 24f;
        int save = cv.save();
        cv.translate(b.exactCenterX() - 12 * u, b.exactCenterY() - 12 * u);
        cv.scale(u, u);
        stroke.setStrokeWidth(1.8f);
        p.reset();
        switch (kind) {
            case HOME:
                p.moveTo(3.5f, 11f);
                p.lineTo(12f, 3.8f);
                p.lineTo(20.5f, 11f);
                p.moveTo(5.8f, 9.4f);
                p.lineTo(5.8f, 19f);
                p.quadTo(5.8f, 20.4f, 7.2f, 20.4f);
                p.lineTo(16.8f, 20.4f);
                p.quadTo(18.2f, 20.4f, 18.2f, 19f);
                p.lineTo(18.2f, 9.4f);
                p.moveTo(10f, 20.4f);
                p.lineTo(10f, 15f);
                p.lineTo(14f, 15f);
                p.lineTo(14f, 20.4f);
                cv.drawPath(p, stroke);
                break;
            case SPORTS: {
                cv.drawCircle(12f, 12f, 8.6f, stroke);
                // The ball's middle panel and its seams out to the edge.
                Path pent = new Path();
                for (int i = 0; i < 5; i++) {
                    double a = Math.toRadians(-90 + i * 72);
                    float x = 12f + (float) Math.cos(a) * 3.2f, y = 12f + (float) Math.sin(a) * 3.2f;
                    if (i == 0) pent.moveTo(x, y);
                    else pent.lineTo(x, y);
                    float ex = 12f + (float) Math.cos(a) * 8.6f, ey = 12f + (float) Math.sin(a) * 8.6f;
                    p.moveTo(x, y);
                    p.lineTo(x + (ex - x) * 0.62f, y + (ey - y) * 0.62f);
                }
                pent.close();
                cv.drawPath(pent, fill);
                stroke.setStrokeWidth(1.4f);
                cv.drawPath(p, stroke);
                break;
            }
            case LIVE:
                r.set(3f, 6.5f, 21f, 18.5f);
                cv.drawRoundRect(r, 2.6f, 2.6f, stroke);
                p.moveTo(8.5f, 2.8f);
                p.lineTo(12f, 6.3f);
                p.lineTo(15.5f, 2.8f);
                p.moveTo(8.5f, 21.2f);
                p.lineTo(15.5f, 21.2f);
                cv.drawPath(p, stroke);
                cv.drawCircle(17.2f, 10.2f, 1.35f, fill);
                break;
            case WATCH:
                r.set(3f, 5f, 21f, 19f);
                cv.drawRoundRect(r, 3.2f, 3.2f, stroke);
                p.moveTo(10.2f, 9.2f);
                p.lineTo(15.2f, 12f);
                p.lineTo(10.2f, 14.8f);
                p.close();
                stroke.setStrokeWidth(1.6f);
                cv.drawPath(p, stroke);
                cv.drawPath(p, fill);
                break;
            case SETTINGS: {
                // A gear: eight teeth round a ring, a hole in the middle.
                int teeth = 8;
                for (int i = 0; i < teeth * 4; i++) {
                    double a = Math.toRadians(i * 360.0 / (teeth * 4) - 90 + 360.0 / (teeth * 8));
                    float rad = (i % 4 == 0 || i % 4 == 1) ? 9.2f : 7.1f;
                    float x = 12f + (float) Math.cos(a) * rad, y = 12f + (float) Math.sin(a) * rad;
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.close();
                stroke.setStrokeWidth(1.6f);
                cv.drawPath(p, stroke);
                cv.drawCircle(12f, 12f, 3f, stroke);
                break;
            }
            case SEARCH:
                cv.drawCircle(10.6f, 10.6f, 6.4f, stroke);
                p.moveTo(15.4f, 15.4f);
                p.lineTo(20.2f, 20.2f);
                stroke.setStrokeWidth(2f);
                cv.drawPath(p, stroke);
                break;
            case CHEVRON:
                p.moveTo(9.5f, 5.5f);
                p.lineTo(15.5f, 12f);
                p.lineTo(9.5f, 18.5f);
                stroke.setStrokeWidth(2f);
                cv.drawPath(p, stroke);
                break;
            case BACK:
                p.moveTo(15f, 4.8f);
                p.lineTo(8f, 12f);
                p.lineTo(15f, 19.2f);
                stroke.setStrokeWidth(2.2f);
                cv.drawPath(p, stroke);
                break;
            case DOWN:
                p.moveTo(6f, 9.2f);
                p.lineTo(12f, 15f);
                p.lineTo(18f, 9.2f);
                stroke.setStrokeWidth(2f);
                cv.drawPath(p, stroke);
                break;
            case MORE:
                cv.drawCircle(5.6f, 12f, 1.8f, fill);
                cv.drawCircle(12f, 12f, 1.8f, fill);
                cv.drawCircle(18.4f, 12f, 1.8f, fill);
                break;
            case PLAY:
                p.moveTo(7.2f, 4.6f);
                p.lineTo(19.6f, 12f);
                p.lineTo(7.2f, 19.4f);
                p.close();
                stroke.setStrokeWidth(1.4f);
                cv.drawPath(p, fill);
                cv.drawPath(p, stroke);
                break;
            case PLUS:
                p.moveTo(12f, 5f);
                p.lineTo(12f, 19f);
                p.moveTo(5f, 12f);
                p.lineTo(19f, 12f);
                stroke.setStrokeWidth(2f);
                cv.drawPath(p, stroke);
                break;
            case CHECK:
                p.moveTo(5f, 12.6f);
                p.lineTo(9.8f, 17.2f);
                p.lineTo(19.2f, 7f);
                stroke.setStrokeWidth(2.1f);
                cv.drawPath(p, stroke);
                break;
            case HEART:
                p.moveTo(12f, 20f);
                p.cubicTo(5f, 15f, 3f, 11.5f, 3.6f, 8.4f);
                p.cubicTo(4.3f, 5f, 8.6f, 3.6f, 12f, 7.6f);
                p.cubicTo(15.4f, 3.6f, 19.7f, 5f, 20.4f, 8.4f);
                p.cubicTo(21f, 11.5f, 19f, 15f, 12f, 20f);
                p.close();
                cv.drawPath(p, stroke);
                break;
            case CLOSE:
                p.moveTo(6.5f, 6.5f);
                p.lineTo(17.5f, 17.5f);
                p.moveTo(17.5f, 6.5f);
                p.lineTo(6.5f, 17.5f);
                stroke.setStrokeWidth(2f);
                cv.drawPath(p, stroke);
                break;
            case FILM:
                r.set(4f, 3.5f, 20f, 20.5f);
                cv.drawRoundRect(r, 2.4f, 2.4f, stroke);
                p.moveTo(8f, 3.5f);
                p.lineTo(8f, 20.5f);
                p.moveTo(16f, 3.5f);
                p.lineTo(16f, 20.5f);
                p.moveTo(4f, 12f);
                p.lineTo(20f, 12f);
                stroke.setStrokeWidth(1.5f);
                cv.drawPath(p, stroke);
                break;
            case SHARE:
                p.moveTo(12f, 14.5f);
                p.lineTo(12f, 3.5f);
                p.moveTo(7.8f, 7.6f);
                p.lineTo(12f, 3.5f);
                p.lineTo(16.2f, 7.6f);
                p.moveTo(8.5f, 10.5f);
                p.lineTo(6.5f, 10.5f);
                p.lineTo(6.5f, 20.5f);
                p.lineTo(17.5f, 20.5f);
                p.lineTo(17.5f, 10.5f);
                p.lineTo(15.5f, 10.5f);
                cv.drawPath(p, stroke);
                break;
            case STAR:
                for (int i = 0; i < 10; i++) {
                    double a = Math.toRadians(-90 + i * 36);
                    float rad = i % 2 == 0 ? 9f : 3.9f;
                    float x = 12f + (float) Math.cos(a) * rad, y = 12.6f + (float) Math.sin(a) * rad;
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                p.close();
                cv.drawPath(p, fill);
                break;
            default:
                break;
        }
        cv.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) {
        stroke.setColorFilter(cf);
        fill.setColorFilter(cf);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
