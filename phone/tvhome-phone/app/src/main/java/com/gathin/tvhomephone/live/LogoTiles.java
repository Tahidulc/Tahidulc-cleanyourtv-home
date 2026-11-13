package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.gathin.broadcast.LogoTile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Channel logos on black tiles, as the TVs draw them (LogoTile): each box black, its logo made ready for black (a white
 * JPEG box taken away, a dark logo turned light), trimmed and fitted in the middle with the same room all round. On
 * until switched off in Live TV's settings; off, the boxes and logos are as they were.
 */
public final class LogoTiles {
    private LogoTiles() {
    }

    /** The tiles' black. */
    public static final int GROUND = LogoTile.GROUND;
    private static final String KEY = "logo_tiles";

    public static boolean on(Context c) {
        return c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    public static void set(Context c, boolean on) {
        c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    /** The fill for a logo's box: the tile's black, else the colour it had. */
    public static int fill(Context c, int was) {
        return on(c) ? GROUND : was;
    }

    /** The room round a logo inside its box, in px: none on a tile (the picture carries the tile's own room), else was dp. */
    public static int pad(Context c, int wasDp) {
        return on(c) ? 0 : Ui.dp(c, wasDp);
    }

    /** A channel's logo into its box (as large as the box: no padding on a tile). */
    public static void load(ImageView img, String url) {
        load(img, url, null);
    }

    /** As load(img, url), telling failed (main thread) when there is no logo to show. */
    public static void load(ImageView img, String url, @Nullable Runnable failed) {
        load(img, url, null, failed);
    }

    /** As load(img, url), telling ready or failed (main thread) as it goes. */
    public static void load(ImageView img, String url, @Nullable Runnable ready, @Nullable Runnable failed) {
        try {
            com.bumptech.glide.RequestBuilder<Drawable> b = Glide.with(img).load(url);
            if (on(img.getContext())) {
                int p = img.getPaddingLeft() + img.getPaddingTop() + img.getPaddingRight() + img.getPaddingBottom();
                if (p != 0) img.setPadding(0, 0, 0, 0);
                b = b.transform(PLACED);
            }
            if (ready != null || failed != null) b = b.listener(new RequestListener<Drawable>() {
                @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean first) {
                    if (failed != null) failed.run();
                    return false;
                }

                @Override public boolean onResourceReady(@NonNull Drawable d, @NonNull Object model, Target<Drawable> target,
                                                         @NonNull DataSource source, boolean first) {
                    if (ready != null) ready.run();
                    return false;
                }
            });
            b.into(img);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    /** A large logo on its own over the dark (the player's cover while a channel starts): made ready for black, trimmed. */
    public static void loadReady(ImageView img, String url) {
        try {
            if (on(img.getContext())) Glide.with(img).load(url).transform(READY).into(img);
            else Glide.with(img).load(url).into(img);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    private static final Placed PLACED = new Placed();
    private static final Ready READY = new Ready();

    /** The logo made ready and fitted in the middle of a clear box the view's size (the view's background is the tile). */
    static final class Placed extends BitmapTransformation {
        private static final String ID = "com.gathin.tvhomephone.live.LogoTiles.Placed.1";

        @Override protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap in, int w, int h) {
            Bitmap ready = LogoTile.ready(in);
            if (ready == null) return in;
            Bitmap out = LogoTile.placed(ready, w, h);
            ready.recycle();
            return out;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Placed;
        }

        @Override public int hashCode() {
            return ID.hashCode();
        }

        @Override public void updateDiskCacheKey(@NonNull MessageDigest md) {
            md.update(ID.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The logo made ready for black and trimmed, nothing round it. */
    static final class Ready extends BitmapTransformation {
        private static final String ID = "com.gathin.tvhomephone.live.LogoTiles.Ready.1";

        @Override protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap in, int w, int h) {
            Bitmap ready = LogoTile.ready(in);
            return ready == null ? in : ready;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Ready;
        }

        @Override public int hashCode() {
            return ID.hashCode();
        }

        @Override public void updateDiskCacheKey(@NonNull MessageDigest md) {
            md.update(ID.getBytes(StandardCharsets.UTF_8));
        }
    }
}
