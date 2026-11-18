package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.gathin.broadcast.LogoColour;
import com.gathin.broadcast.LogoHalo;
import com.gathin.broadcast.LogoTile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * What is behind the channels' logos, wherever the app shows one (the guide, the channel lists, favourites, search,
 * the player, Also Showing On, the info tile, Recently watched, a game's channels, Sports), chosen in Settings, Live TV
 * ("Behind logos"): Logo's colour (the default), None or Black tile. Logo's colour fills each logo's tile with a rich,
 * deeper shade of the logo's own colour (LogoColour: Channel 4's green on deep green, BBC One's red on deep red; a
 * white, black or grey mark on a neutral dark, never pure black), worked out once per logo off the main thread and kept.
 * None draws each logo as it is with no tile or box behind it; only a logo too dark (or too light) to see on the ground
 * it sits on gets a soft halo behind its own shape (LogoHalo). Black tile is the TVs' 4.98 look: each logo made ready
 * for black (a dark one turned light), trimmed and set in the middle of a black tile (LogoTile). The playlist's logos
 * and the logo sets' are drawn alike.
 *
 * <p>With Logo's colour, a row that shows a channel's number, logo and name together (Live TV's channel list, the
 * player's channels, Also Showing On, a game's channels, the favourites and hidden channels) is filled with the logo's
 * colour too, a touch lighter and richer than its tile, deep enough for the white writing on it (row()); None and Black
 * tile keep each row's own plain ground.
 *
 * <p>The family asked for each logo on its own colour: every phone starts on Logo's colour, switched once (4.75's black
 * tiles, gone since 4.77, are not brought back by their old switch); whatever is chosen since is kept.
 */
public final class LogoTiles {
    private LogoTiles() {
    }

    /** Behind logos: nothing, a black tile, or the logo's own colour (the default). */
    public static final int NONE = 0, TILE = 1, COLOUR = 2;
    /** The tiles' black. */
    public static final int GROUND = LogoTile.GROUND;
    /** The grounds the logos sit on with nothing behind them (for the halo): Live TV's black, the navy pages, white. */
    public static final int ON_BLACK = 0xFF000000, ON_NAVY = 0xFF0D0F2C, ON_WHITE = 0xFFFFFFFF;
    private static final String PREFS = "live_player";
    /** 4.75's switch (black tiles unless turned off; not read since 4.77), and the choice made since the default changed. */
    static final String OLD = "logo_tiles", KEY = "logo_behind";

    /** What is kept, as the switch reads it (the app's preferences; a map in the tests). */
    interface Kept {
        boolean has(String key);

        int get(String key, int otherwise);

        void put(String key, int value);
    }

    /**
     * Pure: what is behind the logos: the choice made since the default changed (chosen: NONE, TILE or COLOUR, null when
     * none was made yet), else Logo's colour, whatever 4.75's old switch says (old: null when never touched): it is
     * switched once, as it cannot say whether a tile was chosen before or after.
     */
    static int behind(Integer chosen, Boolean old) {
        return chosen != null && (chosen == NONE || chosen == TILE || chosen == COLOUR) ? chosen : COLOUR;
    }

    /** What is behind the logos, the one-time switch made on the first read (Logo's colour written, unless chosen since). */
    static int behind(Kept k) {
        Integer chosen = k.has(KEY) ? k.get(KEY, COLOUR) : null;
        Boolean old = k.has(OLD) ? k.get(OLD, 1) != 0 : null;
        int v = behind(chosen, old);
        if (chosen == null) k.put(KEY, v);
        return v;
    }

    private static int cached = -1;

    public static int behind(Context c) {
        int v = cached;
        if (v < 0) {
            v = behind(kept(c));
            cached = v;
        }
        return v;
    }

    /** Black tiles behind the logos. */
    public static boolean tiles(Context c) {
        return behind(c) == TILE;
    }

    /** The family's choice in Settings. */
    public static void set(Context c, int behind) {
        int v = behind(behind, null);
        kept(c).put(KEY, v);
        cached = v;
    }

    private static Kept kept(Context c) {
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Kept() {
            @Override public boolean has(String key) {
                return p.contains(key);
            }

            @Override public int get(String key, int otherwise) {
                Object v = p.getAll().get(key);
                if (v instanceof Integer) return (Integer) v;
                if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
                return otherwise;
            }

            @Override public void put(String key, int value) {
                p.edit().putInt(key, value).apply();
            }
        };
    }

    // ---- the tile -----------------------------------------------------------------------------------------------------

    /**
     * A logo's colours as they come: {tile top, tile foot, row top, row foot}. The tile's {0, 0} is no tile at all; the
     * row's {0, 0} is the row's own plain ground (None and Black tile).
     */
    public interface Tile {
        void colours(int[] c);
    }

    /** Each logo's colours once worked out (off the main thread), by its address. */
    private static final LruCache<String, int[]> COLOURS = new LruCache<>(2000);
    private static final int[] NOTHING = {0, 0, 0, 0}, BLACK = {GROUND, GROUND, 0, 0};

    /** A logo's own colours ({tile top, tile foot, row top, row foot}), once worked out; null until then. */
    public static int[] known(String url) {
        return url == null ? null : COLOURS.get(url);
    }

    /**
     * The colours behind a logo now, as the setting asks: Logo's colour (its own, else the neutral dark while they are
     * worked out), nothing, or black; the row's only with Logo's colour.
     */
    public static int[] now(Context c, String url) {
        int b = behind(c);
        if (b == TILE) return BLACK;
        if (b == NONE) return NOTHING;
        int[] k = known(url);
        return k != null ? k : LogoColour.waiting();
    }

    /**
     * A channel row's ground (its number, logo and name together) in the logo's row colours (a soft gradient with the
     * touch ripple, ringed in ring's colour when selected); null when the row keeps its own plain ground (None, Black
     * tile).
     */
    @Nullable
    public static Drawable row(Context c, int[] cols, boolean selected, int ring, float radiusDp) {
        if (cols == null || cols.length < 4 || (cols[2] == 0 && cols[3] == 0)) return null;
        GradientDrawable g = ground(c, cols[2], cols[3], radiusDp);
        if (selected) g.setStroke(Ui.dp(c, 2), ring);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, Ui.round(c, Ui.TEXT, radiusDp));
    }

    /** A box's ground in a tile's colours (a soft gradient, top to foot), corners round by radiusDp; clear for {0, 0}. */
    public static GradientDrawable ground(Context c, int top, int foot, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, foot});
        g.setCornerRadius(Ui.dp(c, radiusDp));
        return g;
    }

    /** As ground, with the touch ripple the app's boxes have. */
    public static RippleDrawable touchGround(Context c, int top, int foot, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), ground(c, top, foot, radiusDp), Ui.round(c, Ui.TEXT, radiusDp));
    }

    /**
     * A channel's logo into img, and box's ground set as the setting asks (round by radiusDp; with the touch ripple when
     * touch) as its colours come: the logo's own colour, nothing, or black. failed when there is no logo to show.
     */
    public static void show(android.view.View box, float radiusDp, boolean touch, ImageView img, String url, int ground,
                            @Nullable Runnable failed) {
        show(box, radiusDp, touch, img, url, ground, null, failed);
    }

    /** As show, and then told the logo's colours as they come (a row to fill round it: row()). */
    public static void show(android.view.View box, float radiusDp, boolean touch, ImageView img, String url, int ground,
                            @Nullable Tile then, @Nullable Runnable failed) {
        Context c = img.getContext();
        load(img, url, ground, cols -> {
            box.setBackground(touch ? touchGround(c, cols[0], cols[1], radiusDp) : ground(c, cols[0], cols[1], radiusDp));
            if (then != null) then.colours(cols);
        }, failed);
    }

    /** Whether the logos sit on a tile of some kind (their own colour or black), so their box is a little larger. */
    public static boolean boxed(Context c) {
        return behind(c) != NONE;
    }

    /**
     * The tile behind a logo a card draws itself (a notification's), the logo already loaded: its own colours (worked
     * out here: never on the main thread), nothing ({0, 0}), or black.
     */
    public static int[] tileFor(Context c, Bitmap logo) {
        int b = behind(c);
        if (b == TILE) return new int[]{GROUND, GROUND};
        if (b == NONE || logo == null) return new int[]{0, 0};
        int[] t = LogoColour.tile(logo);
        return new int[]{t[0], t[1]};
    }

    /**
     * A channel's logo that never sits on a tile (one in a line of words: Sports' fixtures): as it is, a soft halo behind
     * it when it is too faint on the ground it sits on, whatever Behind logos says.
     */
    public static void loadBare(ImageView img, String url, int ground, @Nullable Runnable failed) {
        try {
            RequestBuilder<Drawable> r = Glide.with(img).load(url).transform(new Halo(ground));
            if (failed != null) r = r.listener(new RequestListener<Drawable>() {
                @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean first) {
                    failed.run();
                    return false;
                }

                @Override public boolean onResourceReady(@NonNull Drawable d, @NonNull Object model, Target<Drawable> target,
                                                         @NonNull DataSource source, boolean first) {
                    return false;
                }
            });
            r.into(img);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    /** The room round a logo inside its box, in px: none on a black tile (the picture carries the tile's room), else wasDp. */
    public static int pad(Context c, int wasDp) {
        return tiles(c) ? 0 : Ui.dp(c, wasDp);
    }

    // ---- the logo -----------------------------------------------------------------------------------------------------

    /** A channel's logo into its view; its ground (for the halo with None) is the one it sits on. */
    public static void load(ImageView img, String url, int ground) {
        load(img, url, ground, null, null);
    }

    /**
     * A channel's logo into its view, as the setting asks: as it is, its own colours worked out and told to tile (Logo's
     * colour); as it is, a soft halo behind it when it is too faint on that ground (None); or made ready and set in the
     * middle of the view's box, with no padding (Black tile). tile is told the box's colours as they come, failed when
     * there is no logo to show; both on the main thread.
     */
    public static void load(ImageView img, String url, int ground, @Nullable Tile tile, @Nullable Runnable failed) {
        Context c = img.getContext();
        int b = behind(c);
        if (tile != null) tile.colours(now(c, url));
        try {
            RequestBuilder<Drawable> r = Glide.with(img).load(url);
            if (b == TILE) {
                if (img.getPaddingLeft() + img.getPaddingTop() + img.getPaddingRight() + img.getPaddingBottom() != 0) img.setPadding(0, 0, 0, 0);
                r = r.transform(PLACED);
            } else if (b == NONE) {
                r = r.transform(new Halo(ground));
            } else {
                // Its colours worked out as it is decoded (off the main thread); the data kept, so a new start works them out again.
                r = r.diskCacheStrategy(DiskCacheStrategy.DATA).transform(new Colour(url));
            }
            r = r.listener(new RequestListener<Drawable>() {
                @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean first) {
                    if (failed != null) failed.run();
                    return false;
                }

                @Override public boolean onResourceReady(@NonNull Drawable d, @NonNull Object model, Target<Drawable> target,
                                                         @NonNull DataSource source, boolean first) {
                    int[] k = b == COLOUR ? known(url) : null;
                    if (tile != null && k != null) tile.colours(k);
                    return false;
                }
            });
            r.into(img);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    /** A large logo on its own over the dark (the player's cover while a channel starts): made ready for black on Black tile. */
    public static void loadReady(ImageView img, String url) {
        try {
            if (tiles(img.getContext())) Glide.with(img).load(url).transform(READY).into(img);
            else Glide.with(img).load(url).transform(new Halo(ON_BLACK)).into(img);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    private static final Placed PLACED = new Placed();
    private static final Ready READY = new Ready();

    /** The logo made ready and fitted in the middle of a clear box the view's size (the view's background is the tile). */
    static final class Placed extends BitmapTransformation {
        private static final String ID = "com.gathin.tvhomephone.live.LogoTiles.Placed.2";

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
        private static final String ID = "com.gathin.tvhomephone.live.LogoTiles.Ready.2";

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

    /** The logo as it is, a soft halo behind its shape when it is too faint on the ground it sits on (LogoHalo). */
    static final class Halo extends BitmapTransformation {
        private static final String ID = "com.gathin.tvhomephone.live.LogoTiles.Halo.1:";
        private final int ground;

        Halo(int ground) {
            this.ground = ground | 0xFF000000;
        }

        @Override protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap in, int w, int h) {
            return LogoHalo.apply(in, ground);
        }

        @Override public boolean equals(Object o) {
            return o instanceof Halo && ((Halo) o).ground == ground;
        }

        @Override public int hashCode() {
            return (ID + ground).hashCode();
        }

        @Override public void updateDiskCacheKey(@NonNull MessageDigest md) {
            md.update((ID + ground).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The logo as it is, its tile's colours worked out from it and kept for its address (LogoColour). */
    static final class Colour extends BitmapTransformation {
        private static final String ID = "com.gathin.tvhomephone.live.LogoTiles.Colour.1";
        private final String url;

        Colour(String url) {
            this.url = url == null ? "" : url;
        }

        @Override protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap in, int w, int h) {
            if (COLOURS.get(url) == null) COLOURS.put(url, LogoColour.tile(in));
            return in;
        }

        @Override public boolean equals(Object o) {
            return o instanceof Colour && ((Colour) o).url.equals(url);
        }

        @Override public int hashCode() {
            return (ID + url).hashCode();
        }

        @Override public void updateDiskCacheKey(@NonNull MessageDigest md) {
            md.update(ID.getBytes(StandardCharsets.UTF_8));
        }
    }
}
