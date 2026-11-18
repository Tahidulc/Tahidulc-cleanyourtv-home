package com.gathin.tvhomephone.sports;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * A picture a drawn page shows (a driver, his car, his team's logo, his flag): the first of its addresses that loads,
 * read with the app's picture cache at no more than the size it shows at, off the main thread, its owner redrawn when
 * it comes. Until then, and when none comes, it has no bitmap, and the page draws its stand-in. The bitmap is Glide's
 * and is let go (never recycled here) when the page shows another driver.
 */
final class PagePic {
    private final View owner;
    private Bitmap bitmap;
    private boolean failed;
    private CustomTarget<Bitmap> target;
    private int token;

    PagePic(View owner) {
        this.owner = owner;
    }

    Bitmap bitmap() {
        return bitmap;
    }

    /** No address loaded (or none was given): the stand-in stays. */
    boolean failed() {
        return failed;
    }

    /** Asks for the first of these addresses that loads, at most w by h pixels (kept in shape). */
    void load(String[] urls, int w, int h) {
        clear();
        int my = ++token;
        if (urls == null) urls = new String[0];
        for (String u : urls) {
            Bitmap given = Cutout.given(u);
            if (given != null) {
                bitmap = given;
                owner.invalidate();
                return;
            }
        }
        RequestManager glide;
        try {
            glide = Glide.with(owner);
        } catch (RuntimeException e) {
            failed = true;
            return;
        }
        RequestBuilder<Bitmap> chain = null;
        for (int i = urls.length - 1; i >= 0; i--) {
            if (urls[i] == null || urls[i].isEmpty() || Cutout.GIVEN.containsKey(urls[i])) continue;
            RequestBuilder<Bitmap> b = glide.asBitmap().load(urls[i]).downsample(DownsampleStrategy.CENTER_INSIDE).dontTransform();
            chain = chain == null ? b : b.error(chain);
        }
        if (chain == null || !Cutout.GIVEN.isEmpty()) {
            failed = true;
            owner.invalidate();
            return;
        }
        target = new CustomTarget<Bitmap>(Math.max(1, w), Math.max(1, h)) {
            @Override public void onResourceReady(@NonNull Bitmap r, @Nullable Transition<? super Bitmap> t) {
                if (my != token) return;
                bitmap = r;
                failed = false;
                owner.invalidate();
            }

            @Override public void onLoadFailed(@Nullable Drawable error) {
                if (my != token) return;
                bitmap = null;
                failed = true;
                owner.invalidate();
            }

            @Override public void onLoadCleared(@Nullable Drawable placeholder) {
                if (my == token) bitmap = null;
            }
        };
        try {
            chain.into(target);
        } catch (RuntimeException e) {
            failed = true;
        }
    }

    /** Lets the picture go (the page no longer shows it). */
    void clear() {
        token++;
        bitmap = null;
        failed = false;
        if (target != null) {
            CustomTarget<Bitmap> t = target;
            target = null;
            try {
                Glide.with(owner).clear(t);
            } catch (RuntimeException ignored) {
                // the screen went away
            }
        }
    }
}
