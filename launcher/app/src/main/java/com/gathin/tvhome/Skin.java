package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A Home style drawn over launcher v2's engine (HomeStyle: SWIPTV's or UHF's look): its own screen, the launcher's rows
 * and panels underneath. HomeV2 hands it the keys while none of the panels (Control Centre, a title, search) is open,
 * and Home pressed brings it back to its start.
 */
abstract class Skin {
    // The launcher's rows, as SkinHost.row reads them.
    static final String ROW_CONTINUE = "continue", ROW_CONTINUE_TV = "continue_tv", ROW_CONTINUE_FILMS = "continue_films";
    static final String ROW_LIVE = "live", ROW_SPORT = "sport", ROW_TOP_TV = "top_tv", ROW_TOP_FILMS = "top_films";
    static final String ROW_FITS = "fits", ROW_YOUR_SHOWS = "your_shows", ROW_NEW_EPISODES = "new_episodes";
    static final String ROW_DEBRID = "debrid", ROW_BECAUSE = "because", ROW_COMING = "coming", ROW_TRAILERS = "trailers";
    static final String ROW_KIDS_TV = "kids_tv", ROW_KIDS_FILMS = "kids_films", ROW_APPS = "apps", ROW_DOCK = "dock";
    static final String ROW_MY_LIST = "my_list", ROW_YOUTUBE = "youtube", ROW_TRENDING = "trending", ROW_NEW = "new_releases";
    static final String ROW_NEWS = "news";
    // The launcher's panels, as SkinHost.open opens them.
    static final String OPEN_CONTROL_CENTRE = "cc", OPEN_SEARCH = "search", OPEN_STYLES = "styles";
    static final String OPEN_NOTIFICATIONS = "notifications", OPEN_WEATHER = "weather";

    /**
     * One thing on a row, whatever the launcher's own shape for it was (a poster's PagesV2.Title, a tile's HomeV2.Tile):
     * a film or series, a channel on now, a game, an app, a video or a headline.
     */
    static final class Item {
        static final String TITLE = "title", LIVE = "live", SPORT = "sport", APP = "app", VIDEO = "video", NEWS = "news";
        String kind = TITLE;
        /** Its name, the line under it, and a longer line for a hero ("S2:E6 · 31 min left"). */
        String name, sub, line;
        /** Pictures: the 2:3 poster, the 16:9 picture, the title's logo, a small logo (a channel's, a competition's). */
        String poster, backdrop, logo, mark;
        String overview, year, cert;
        /** The app it plays in, by package (Continue watching), and the channel it is on (live, sport). */
        String app, channel;
        double vote;
        /** How far through it is (0 to 1); -1 when not started. */
        float progress = -1;
        /** Its place in a Top 10 (1 to 10); 0 when not ranked. */
        int rank;
        /** A short word on its corner ("NEW", "NEW · S4 E1", "LIVE"); null when none. */
        String flag;
        boolean tv;
        int tmdbId;
        /** Running time in minutes (films); 0 when unknown. */
        int runtime;
        /** An app's own icon. */
        Drawable icon;
        /** A game's or a programme's start and end (ms); 0 when not timed. */
        long start, end;
        /** The launcher's own (PagesV2.Title or HomeV2.Tile), for what OK does. */
        Object src;
    }

    /**
     * A picture cut to a rounded rectangle and cropped to fill it (centred across, `alignY` down: 0.2 keeps faces in a
     * backdrop), its corners drawn with a shader so they are round whether or not the screen is drawn on the graphics
     * chip. Until its picture comes it is a flat `empty` colour.
     */
    static class Pic extends ImageView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint blank = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final RectF box = new RectF();
        private Bitmap bmp;
        private float radius, alignY = 0.5f;
        /** Only the top corners round (a tile whose foot is a caption band). */
        private boolean topOnly;

        Pic(Context c, float radiusPx, int empty) {
            super(c);
            radius = radiusPx;
            blank.setColor(empty);
            setWillNotDraw(false);
        }

        Pic alignY(float y) {
            alignY = y;
            invalidate();
            return this;
        }

        Pic topOnly() {
            topOnly = true;
            return this;
        }

        void radius(float r) {
            radius = r;
            invalidate();
        }

        @Override public void setImageBitmap(Bitmap b) {
            bmp = b;
            paint.setShader(b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            invalidate();
        }

        @Override public void setImageDrawable(android.graphics.drawable.Drawable d) {
            if (d instanceof android.graphics.drawable.BitmapDrawable) { setImageBitmap(((android.graphics.drawable.BitmapDrawable) d).getBitmap()); return; }
            if (d == null) { setImageBitmap(null); return; }
            super.setImageDrawable(d);
        }

        boolean hasPicture() {
            return bmp != null;
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            box.set(0, 0, w, h);
            Paint use = blank;
            if (bmp != null && bmp.getWidth() > 0 && bmp.getHeight() > 0) {
                float k = Math.max(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
                float dx = (w - bmp.getWidth() * k) / 2f, dy = (h - bmp.getHeight() * k) * alignY;
                m.setScale(k, k);
                m.postTranslate(dx, dy);
                paint.getShader().setLocalMatrix(m);
                use = paint;
            }
            canvas.drawRoundRect(box, radius, radius, use);
            if (topOnly && radius > 0) canvas.drawRect(0, h - radius, w, h, use);
        }
    }

    final SkinHost host;
    final Context c;
    /** The style's whole screen, on top of launcher v2's (which is hidden under it). */
    FrameLayout layer;
    private boolean live = true;

    Skin(SkinHost host) {
        this.host = host;
        this.c = host.context();
    }

    /** The style's screen, made once: a full-screen view. */
    abstract View build();

    /** A key while the style has the screen and no panel is open; true when it used it (Back included). */
    abstract boolean key(KeyEvent e);

    /** Home pressed: back to the style's start, the focus on its first thing. */
    abstract void home();

    /** Home back on screen: rows that age (what is on now, Continue watching) read again. */
    void resume() {
    }

    void stop() {
    }

    /** Taken off the screen (another style chosen, or the launcher closing). */
    void destroy() {
        live = false;
    }

    boolean live() {
        return live;
    }

    // ---- rows -------------------------------------------------------------------------------------------------------

    /** A row read off the main thread, then handed over on it (never once the style has gone). */
    void load(String which, Consumer<List<Item>> then) {
        host.io().execute(() -> {
            List<Item> got;
            try {
                got = host.row(which);
            } catch (Exception e) {
                got = new ArrayList<>();
            }
            List<Item> done = got == null ? new ArrayList<>() : got;
            host.main().post(() -> {
                if (live) then.accept(done);
            });
        });
    }

    static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    int px(float design) {
        return host.px(design);
    }

    TextView text(float size, Typeface face, int colour) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(face);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    GradientDrawable fill(float radius, int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(radius));
        g.setColor(colour);
        return g;
    }

    GradientDrawable ring(float radius, int colour, float width) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(radius));
        g.setColor(0);
        g.setStroke(Math.max(1, px(width)), colour);
        return g;
    }

    /** A view's corners cut round (its picture too), on the graphics chip. */
    void round(View v, float radius) {
        final int r = px(radius);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** v at (x, y), w x h design pixels, in parent. */
    void add(FrameLayout parent, View v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? (int) w : px(w), h < 0 ? (int) h : px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        parent.addView(v, lp);
    }

    /** "1h 05m", "48m". */
    static String span(int minutes) {
        return minutes >= 60 ? minutes / 60 + "h " + String.format(java.util.Locale.ROOT, "%02d", minutes % 60) + "m" : minutes + "m";
    }

    /** "19:42" for a time in ms, in the TV's zone. */
    static String hm(long ms) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
    }
}
