package com.gathin.tvhomephone.settings;

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

import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.Icons;

/**
 * The glyph beside each of Settings' sections: the glass pages' own line icons (glass.Icons) where one says it (Live
 * TV's screen, the ball, a film), and a few more drawn here the same way, on a 24-unit square in round-capped strokes:
 * a bell for reminders, a palette for the look, rows for the channels, CC for subtitles, a TV and a phone for the TVs
 * and sync, an arrow into a tray for the app's updates.
 */
final class SectionIcons extends Drawable {
    static final int BELL = 100, PALETTE = 101, ROWS = 102, CC = 103, SCREENS = 104, DOWNLOAD = 105;
    private final int kind;
    private final Icons theirs;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();
    private final RectF r = new RectF();
    private final int size;

    SectionIcons(Context c, int kind, int colour) {
        this.kind = kind;
        theirs = kind < BELL ? new Icons(c, kind, colour) : null;
        size = Glass.dp(c, 24);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setColour(colour);
    }

    void setColour(int colour) {
        if (theirs != null) theirs.setColour(colour);
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

    @Override protected void onBoundsChange(@NonNull Rect bounds) {
        if (theirs != null) theirs.setBounds(bounds);
    }

    @Override public void draw(@NonNull Canvas cv) {
        if (theirs != null) {
            theirs.draw(cv);
            return;
        }
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float u = Math.min(b.width(), b.height()) / 24f;
        int save = cv.save();
        cv.translate(b.exactCenterX() - 12 * u, b.exactCenterY() - 12 * u);
        cv.scale(u, u);
        stroke.setStrokeWidth(1.8f);
        p.reset();
        switch (kind) {
            case BELL:
                p.moveTo(5.6f, 17.4f);
                p.lineTo(6.4f, 16.2f);
                p.lineTo(6.4f, 11.2f);
                p.cubicTo(6.4f, 7.8f, 8.8f, 5.2f, 12f, 5.2f);
                p.cubicTo(15.2f, 5.2f, 17.6f, 7.8f, 17.6f, 11.2f);
                p.lineTo(17.6f, 16.2f);
                p.lineTo(18.4f, 17.4f);
                p.close();
                p.moveTo(12f, 3.2f);
                p.lineTo(12f, 5.2f);
                p.moveTo(10f, 20.2f);
                p.quadTo(12f, 21.8f, 14f, 20.2f);
                cv.drawPath(p, stroke);
                break;
            case PALETTE:
                p.moveTo(12f, 3.4f);
                p.cubicTo(7.2f, 3.4f, 3.4f, 7.2f, 3.4f, 12f);
                p.cubicTo(3.4f, 16.8f, 7.2f, 20.6f, 12f, 20.6f);
                p.cubicTo(13.4f, 20.6f, 14.2f, 19.6f, 13.8f, 18.4f);
                p.cubicTo(13.3f, 16.9f, 14.3f, 15.6f, 15.8f, 15.6f);
                p.lineTo(17.2f, 15.6f);
                p.cubicTo(19.2f, 15.6f, 20.6f, 14.1f, 20.6f, 12.2f);
                p.cubicTo(20.6f, 7.4f, 16.7f, 3.4f, 12f, 3.4f);
                p.close();
                cv.drawPath(p, stroke);
                cv.drawCircle(7.8f, 12.2f, 1.45f, fill);
                cv.drawCircle(9.6f, 8f, 1.45f, fill);
                cv.drawCircle(14f, 7.4f, 1.45f, fill);
                cv.drawCircle(17f, 10.6f, 1.45f, fill);
                break;
            case ROWS:
                for (float y : new float[]{6.5f, 12f, 17.5f}) {
                    r.set(3.2f, y - 1.9f, 7f, y + 1.9f);
                    cv.drawRoundRect(r, 1f, 1f, fill);
                    p.moveTo(10f, y);
                    p.lineTo(20.6f, y);
                }
                cv.drawPath(p, stroke);
                break;
            case CC:
                r.set(2.8f, 5.2f, 21.2f, 18.8f);
                cv.drawRoundRect(r, 3.2f, 3.2f, stroke);
                stroke.setStrokeWidth(1.7f);
                r.set(6.4f, 9.4f, 11.2f, 14.6f);
                p.arcTo(r, 45f, 270f, true);
                r.set(12.8f, 9.4f, 17.6f, 14.6f);
                p.arcTo(r, 45f, 270f, true);
                cv.drawPath(p, stroke);
                break;
            case SCREENS:
                r.set(2.4f, 5.2f, 15.2f, 14.8f);
                cv.drawRoundRect(r, 2f, 2f, stroke);
                p.moveTo(6.2f, 18.4f);
                p.lineTo(11.4f, 18.4f);
                p.moveTo(8.8f, 14.8f);
                p.lineTo(8.8f, 18.4f);
                cv.drawPath(p, stroke);
                r.set(17.4f, 8.2f, 21.8f, 18.8f);
                cv.drawRoundRect(r, 1.4f, 1.4f, stroke);
                break;
            case DOWNLOAD:
                p.moveTo(12f, 3.8f);
                p.lineTo(12f, 14.4f);
                p.moveTo(7.8f, 10.4f);
                p.lineTo(12f, 14.6f);
                p.lineTo(16.2f, 10.4f);
                p.moveTo(4.6f, 15.2f);
                p.lineTo(4.6f, 18.4f);
                p.quadTo(4.6f, 20f, 6.2f, 20f);
                p.lineTo(17.8f, 20f);
                p.quadTo(19.4f, 20f, 19.4f, 18.4f);
                p.lineTo(19.4f, 15.2f);
                stroke.setStrokeWidth(1.9f);
                cv.drawPath(p, stroke);
                break;
            default:
                break;
        }
        cv.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        if (theirs != null) theirs.setAlpha(alpha);
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) {
        if (theirs != null) theirs.setColorFilter(cf);
        stroke.setColorFilter(cf);
        fill.setColorFilter(cf);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
