package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * A picture behind a dark screen's writing (the films' billboard, the guide's programme, the live screen's channel),
 * cross-fading into the next one as the remote moves (Fade). It is read once, at the size it is drawn or smaller (a
 * darkened picture behind the writing loses nothing read at half size and drawn at full), and faded once as it loads
 * (Pics); after that only its opacity changes.
 */
final class Backdrop extends View {
    private final Fade fade = new Fade();
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float x, y, w, h, read;
    private final int mode;
    private final RectF dst = new RectF();
    private String url = "";
    private final Runnable redraw = this::invalidate;

    /** Where it goes and how it is faded (design pixels; one of Pics' modes). */
    Backdrop(Context c, float x, float y, float w, float h, int mode) {
        this(c, x, y, w, h, mode, 1f);
    }

    /** read: the share of its size it is read at (0.5: half, drawn at twice that). */
    Backdrop(Context c, float x, float y, float w, float h, int mode, float read) {
        super(c);
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.mode = mode;
        this.read = read;
    }

    /** The picture to show: its address, or "" (or null) for none, the last one fading away. */
    void set(String u) {
        String next = u == null ? "" : u;
        if (next.equals(url)) return;
        url = next;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        Bitmap b = url.isEmpty() ? null : Pics.get(url, Ui.px(w * read), Ui.px(h * read), mode, redraw);
        // While a picture is read, the last one stays; one that can't be read fades away.
        fade.picture(b, b == null && !url.isEmpty() && !Pics.failed(url));
        dst.set(Ui.f(x), Ui.f(y), Ui.f(x + w), Ui.f(y + h));
        fade.draw(c, dst, p);
        if (fade.busy()) postInvalidateOnAnimation();
    }
}
