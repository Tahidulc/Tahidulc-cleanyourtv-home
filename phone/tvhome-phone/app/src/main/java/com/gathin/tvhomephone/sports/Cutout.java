package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;

import java.util.HashMap;
import java.util.Map;

/**
 * A cut-out photo (a player's, a driver's) drawn into its box, its foot fading away so no hard edge shows: fitted whole
 * and sat on the box's foot, or filling the box's width from its top with the rest cut off by the box's foot. Read with
 * the app's picture cache (Glide) at no more than the box's size, off the main thread; the first of several addresses
 * that loads. A stand-in (a drawn bust) shows while it comes and stays when none does, so there is never a broken box.
 * The fade is a shader on the one picture, so moving and scaling the view costs nothing more.
 */
final class Cutout extends View {
    /** How the picture sits: whole, on the box's foot; or as wide as the box from its top. */
    static final int FIT = 0, TOP = 1;

    /** Previews only: pictures by address, drawn at once in place of a download. Empty in the app. */
    static final Map<String, Bitmap> GIVEN = new HashMap<>();

    /** Previews only: the picture given for an address, at whatever width formula1.com's is asked for ("w_0"). */
    static Bitmap given(String url) {
        if (url == null || GIVEN.isEmpty()) return null;
        Bitmap b = GIVEN.get(url);
        return b != null ? b : GIVEN.get(url.replaceFirst("c_lfill,w_[0-9]+", "c_lfill,w_0"));
    }

    /** What became of a load: which address came (its place in the list), or none. */
    interface Loaded {
        void ready(int which);

        void failed();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF dst = new RectF();
    private Bitmap bitmap;
    private Drawable standIn;
    private int mode = FIT;
    private float fade = 0.26f;
    private CustomViewTarget<Cutout, Bitmap> target;
    private int token;

    Cutout(Context c) {
        super(c);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** How it sits (FIT or TOP) and how much of its foot fades (a share of what shows; 0 for none). */
    void setLook(int mode, float fade) {
        this.mode = mode;
        this.fade = fade;
        shade();
        invalidate();
    }

    /** What shows while the picture comes, and in its place when none does. */
    void setStandIn(Drawable d) {
        standIn = d;
        if (d != null) d.setBounds(0, 0, getWidth(), getHeight());
        invalidate();
    }

    boolean hasPicture() {
        return bitmap != null;
    }

    /** The picture: the first of these addresses that loads. */
    void load(String[] urls, Loaded told) {
        clear();
        int my = ++token;
        if (urls == null || urls.length == 0) {
            if (told != null) told.failed();
            return;
        }
        for (int i = 0; i < urls.length; i++) {
            Bitmap given = given(urls[i]);
            if (given != null) {
                set(given);
                if (told != null) told.ready(i);
                return;
            }
        }
        RequestManager glide;
        try {
            glide = Glide.with(this);
        } catch (RuntimeException e) {
            return;
        }
        RequestBuilder<Bitmap> chain = null;
        int last = -1;
        for (int i = urls.length - 1; i >= 0; i--) {
            if (urls[i] == null || urls[i].isEmpty()) continue;
            if (last < 0) last = i;
            int which = i;
            boolean end = chain == null;
            RequestBuilder<Bitmap> b = glide.asBitmap().load(urls[i]).downsample(DownsampleStrategy.CENTER_INSIDE).dontTransform()
                    .listener(new RequestListener<Bitmap>() {
                        @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Bitmap> t, boolean first) {
                            if (end && my == token && told != null) post(told::failed);
                            return false;
                        }

                        @Override public boolean onResourceReady(@NonNull Bitmap r, @NonNull Object model, Target<Bitmap> t,
                                                                 @NonNull DataSource source, boolean first) {
                            if (my == token && told != null) post(() -> told.ready(which));
                            return false;
                        }
                    });
            chain = chain == null ? b : b.error(chain);
        }
        if (chain == null) {
            if (told != null) told.failed();
            return;
        }
        try {
            target = chain.into(new CustomViewTarget<Cutout, Bitmap>(this) {
                @Override protected void onResourceCleared(@Nullable Drawable placeholder) {
                    set(null);
                }

                @Override public void onLoadFailed(@Nullable Drawable error) {
                    set(null);
                }

                @Override public void onResourceReady(@NonNull Bitmap r, @Nullable Transition<? super Bitmap> transition) {
                    set(r);
                }
            });
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    /** No picture, and none still coming (Glide may reuse what it had once it is cleared). */
    void clear() {
        token++;
        if (target != null) {
            try {
                Glide.with(this).clear(target);
            } catch (RuntimeException ignored) {
                // the screen went away
            }
            target = null;
        }
        set(null);
    }

    private void set(Bitmap b) {
        if (b == bitmap) return;
        bitmap = b;
        shade();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (standIn != null) standIn.setBounds(0, 0, w, h);
        shade();
    }

    /** The picture's place and its fade, worked out when the picture or the box changes (never while drawing). */
    private void shade() {
        int w = getWidth(), h = getHeight();
        if (bitmap == null || w <= 0 || h <= 0) {
            paint.setShader(null);
            return;
        }
        float bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float s;
        float left, top;
        if (mode == TOP) {
            s = w / bw;
            left = 0;
            top = 0;
        } else {
            s = Math.min(w / bw, h / bh);
            left = (w - bw * s) / 2f;
            top = h - bh * s;
        }
        dst.set(left, top, left + bw * s, Math.min(h, top + bh * s));
        matrix.setScale(s, s);
        matrix.postTranslate(left, top);
        BitmapShader pic = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        pic.setLocalMatrix(matrix);
        if (fade <= 0f) {
            paint.setShader(pic);
            return;
        }
        float from = dst.bottom - dst.height() * fade;
        LinearGradient foot = new LinearGradient(0, from, 0, dst.bottom, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(pic, foot, PorterDuff.Mode.DST_IN));
    }

    @Override protected void onDraw(@NonNull Canvas c) {
        if (bitmap != null && paint.getShader() != null) {
            c.drawRect(dst, paint);
        } else if (standIn != null) {
            standIn.draw(c);
        }
    }

    /** Reads a picture into the cache ahead of its page (a neighbour's), at about the size it will show. */
    static void preload(Context c, String url, int w, int h) {
        if (url == null || url.isEmpty() || GIVEN.containsKey(url)) return;
        try {
            Glide.with(c.getApplicationContext()).asBitmap().load(url).downsample(DownsampleStrategy.CENTER_INSIDE).dontTransform()
                    .preload(Math.max(1, w), Math.max(1, h));
        } catch (RuntimeException ignored) {
            // not now
        }
    }
}
