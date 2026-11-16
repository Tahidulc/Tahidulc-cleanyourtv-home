package com.gathin.tvhomephone.watch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.gathin.tvhomephone.glass.BlurBehind;
import com.gathin.tvhomephone.glass.Glass;
import com.gathin.tvhomephone.glass.GlassDrawable;
import com.gathin.tvhomephone.glass.Icons;

/**
 * The top of a cover page, as the family's reference picture has it: the backdrop across the top of the screen edge to
 * edge, under the status bar, moving a little slower than the page as it scrolls and fading at its foot into the glow
 * behind; over its foot the title's logo (its name in Outfit until a logo comes, or without one) and the line under it,
 * centred on a phone and at the left on a tablet (where Play and what it's about sit there too). Bar: the frosted back
 * and "…" buttons that float over it.
 */
final class CoverHero extends FrameLayout {
    final Picture picture;
    /** The words over the picture's foot: the logo or name, the line under it (and on a tablet, Play and the rest). */
    final LinearLayout head;
    private final ImageView logo;
    private final TextView name, meta;
    private final int side;
    private int top;
    private String pictureFor, logoFor;

    CoverHero(Context c, String title, boolean wide, int side) {
        super(c);
        this.side = side;
        picture = new Picture(c, wide, title);
        addView(picture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(wide ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        // Shown (empty, so no room) from the start: a logo that comes then takes its own shape.
        logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(wide ? ImageView.ScaleType.FIT_START : ImageView.ScaleType.FIT_CENTER);
        logo.setMaxWidth(Glass.dp(c, wide ? 380 : 260));
        logo.setMaxHeight(Glass.dp(c, wide ? 130 : 90));
        logo.setContentDescription(title);
        head.addView(logo, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        name = Glass.text(c, title, wide ? 38 : 30, Glass.TEXT, Glass.heading(c, 600));
        name.setGravity(wide ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setShadowLayer(Glass.dp(c, 12), 0, Glass.dp(c, 2), 0x99000000);
        ViewCompat.setAccessibilityHeading(name, true);
        head.addView(name, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        meta = Glass.text(c, "", 14, Glass.SOFT, Glass.body(c));
        meta.setGravity(wide ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        meta.setMaxLines(2);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setLineSpacing(0, 1.15f);
        meta.setShadowLayer(Glass.dp(c, 8), 0, Glass.dp(c, 1), 0x99000000);
        meta.setVisibility(GONE);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.topMargin = Glass.dp(c, 10);
        head.addView(meta, mp);
        int width = wide ? Math.min(Glass.dp(c, 560), c.getResources().getDisplayMetrics().widthPixels - 2 * side) : LayoutParams.MATCH_PARENT;
        LayoutParams hp = new LayoutParams(width, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | (wide ? Gravity.START : Gravity.CENTER_HORIZONTAL));
        hp.leftMargin = side;
        hp.rightMargin = side;
        hp.bottomMargin = Glass.dp(c, wide ? 36 : 16);
        addView(head, hp);
    }

    /**
     * As tall as 58% of the screen, never less than a widescreen picture of its width nor more than most of the screen
     * (a phone on its side); taller only when the words over its foot need the room under the floating buttons.
     */
    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int screen = getResources().getDisplayMetrics().heightPixels;
        int h = Math.max(Math.round(screen * 0.58f), w * 9 / 16);
        h = Math.min(h, Math.round(screen * 0.88f));
        measureChildWithMargins(head, wSpec, 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
        LayoutParams hp = (LayoutParams) head.getLayoutParams();
        h = Math.max(h, head.getMeasuredHeight() + hp.bottomMargin + top + Glass.dp(getContext(), 72));
        super.onMeasure(wSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    /** The status bar's height (the picture goes under it) and the sides' (a phone on its side). */
    void fit(int top, int left, int right) {
        this.top = top;
        LayoutParams hp = (LayoutParams) head.getLayoutParams();
        hp.leftMargin = side + left;
        hp.rightMargin = side + right;
        head.setLayoutParams(hp);
    }

    /** The page scrolled this far: the picture moves at a little under half the speed. */
    void scrolled(int y) {
        picture.image.setTranslationY(Math.max(0, y) * 0.45f);
    }

    /**
     * The backdrop (the same one again does nothing), faded in over the title's own colours: kept at its own size and
     * cropped as it is drawn, rather than enlarged into a bitmap the size of the screen.
     */
    void picture(String url) {
        if (url == null || url.equals(pictureFor)) return;
        pictureFor = url;
        try {
            Glide.with(picture.image).load(url).dontTransform().transition(DrawableTransitionOptions.withCrossFade(360))
                    .into(picture.image);
        } catch (RuntimeException ignored) {
            // the page went away
        }
    }

    /** The title's logo: once it has come, it takes the name's place (a logo that can't be read leaves the name). */
    void logo(String url) {
        if (url == null || url.equals(logoFor)) return;
        logoFor = url;
        Context c = getContext();
        int maxW = logo.getMaxWidth(), maxH = logo.getMaxHeight();
        try {
            Glide.with(logo).load(url).override(maxW > 0 ? maxW : Glass.dp(c, 260), maxH > 0 ? maxH : Glass.dp(c, 90))
                    .listener(new RequestListener<Drawable>() {
                        @Override public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> t,
                                                              boolean first) {
                            return false;
                        }

                        @Override public boolean onResourceReady(@NonNull Drawable d, @NonNull Object model, Target<Drawable> t,
                                                                 @NonNull DataSource s, boolean first) {
                            if (url.equals(logoFor)) name.setVisibility(GONE);
                            return false;
                        }
                    })
                    .transition(DrawableTransitionOptions.withCrossFade(280)).into(logo);
        } catch (RuntimeException ignored) {
            // the page went away
        }
    }

    void name(String words) {
        name.setText(words);
        logo.setContentDescription(words);
    }

    /** The line under the title ("Comedy • Dec 16, 1999"); none for "". A new line in place of another fades in. */
    void meta(String words) {
        boolean has = words != null && !words.isEmpty();
        CharSequence was = meta.getText();
        meta.setText(has ? words : "");
        meta.setVisibility(has ? VISIBLE : GONE);
        if (has && was != null && was.length() > 0 && !was.toString().equals(words) && isAttachedToWindow()) {
            meta.setAlpha(0f);
            meta.animate().alpha(1f).setDuration(320).start();
        }
    }

    /**
     * The backdrop itself: the picture (moved by the parallax), a shade at the top for the status bar's icons and the
     * floating buttons, a deeper one towards the foot for the words over it (and on a tablet, from the left), then the
     * whole of it faded out at its foot, so the page's glow shows through rather than a hard edge.
     */
    static final class Picture extends FrameLayout {
        final ImageView image;
        private final boolean wide;
        private final Paint shade = new Paint(Paint.DITHER_FLAG), fromLeft = new Paint(Paint.DITHER_FLAG), fade = new Paint();

        Picture(Context c, boolean wide, String seed) {
            super(c);
            this.wide = wide;
            image = new ImageView(c);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(Glass.placeholder(seed, 0));
            image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w <= 0 || h <= 0) return;
            shade.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x80000000, 0x00000000, 0x00000000, 0x8C000000},
                    new float[]{0f, 0.24f, 0.46f, 1f}, Shader.TileMode.CLAMP));
            fromLeft.setShader(new LinearGradient(0, 0, w * 0.66f, 0, 0x8C000000, 0x00000000, Shader.TileMode.CLAMP));
            fade.setShader(new LinearGradient(0, h * 0.5f, 0, h, new int[]{0xFF000000, 0x8C000000, 0x00000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override protected void dispatchDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            int save = cv.saveLayer(0, 0, w, h, null);
            super.dispatchDraw(cv);
            cv.drawRect(0, 0, w, h, shade);
            if (wide) cv.drawRect(0, 0, w, h, fromLeft);
            cv.drawRect(0, 0, w, h, fade);
            cv.restoreToCount(save);
        }
    }

    /**
     * The buttons floating over the top of the page, under the status bar: a frosted round back button with "Films" or
     * "Shows" beside it (the word fades once the page scrolls under it), and a frosted round "…" for the rest.
     */
    static final class Bar extends FrameLayout {
        private final BlurBehind back, more;
        private final TextView word;

        Bar(Context c, String words, Runnable onBack, View.OnClickListener onMore) {
            super(c);
            int size = Glass.dp(c, 42);
            LinearLayout group = new LinearLayout(c);
            group.setOrientation(LinearLayout.HORIZONTAL);
            group.setGravity(Gravity.CENTER_VERTICAL);
            group.setMinimumHeight(Glass.dp(c, 48));
            back = circle(c, Icons.BACK);
            // Pressed with the word beside it: the whole of it is the back button.
            back.setDuplicateParentStateEnabled(true);
            group.addView(back, new LinearLayout.LayoutParams(size, size));
            word = Glass.text(c, words, 16, Glass.TEXT, Glass.bodySemi(c));
            word.setShadowLayer(Glass.dp(c, 8), 0, Glass.dp(c, 1), 0x99000000);
            word.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wp.leftMargin = Glass.dp(c, 10);
            wp.rightMargin = Glass.dp(c, 10);
            group.addView(word, wp);
            group.setClickable(true);
            group.setFocusable(true);
            group.setContentDescription("Back to " + words);
            group.setOnClickListener(v -> onBack.run());
            addView(group, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
            more = circle(c, Icons.MORE);
            more.setClickable(true);
            more.setFocusable(true);
            more.setContentDescription("More");
            more.setOnClickListener(onMore);
            addView(more, new LayoutParams(size, size, Gravity.END | Gravity.CENTER_VERTICAL));
        }

        private static BlurBehind circle(Context c, int icon) {
            BlurBehind b = new BlurBehind(c, 21, GlassDrawable.CARD);
            b.setTint(0x7312131A);
            ImageView i = new ImageView(c);
            i.setImageDrawable(new Icons(c, icon, Glass.TEXT));
            i.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            b.addView(i, new LayoutParams(Glass.dp(c, 22), Glass.dp(c, 22), Gravity.CENTER));
            CoverViews.press(b, 0.9f);
            return b;
        }

        /** What lies under the buttons, for their glass to blur. */
        void setSources(View... sources) {
            back.setSources(sources);
            more.setSources(sources);
        }

        /** Just under the status bar, clear of the sides. */
        void fit(int top, int left, int right) {
            setPadding(left + Glass.dp(getContext(), 12), top + Glass.dp(getContext(), 4), right + Glass.dp(getContext(), 12),
                    Glass.dp(getContext(), 4));
        }

        /** The page scrolled this far over a picture this tall: the word beside the back button fades as words near it. */
        void scrolled(int y, int pictureHeight) {
            float f = 1f - Math.max(0f, Math.min(1f, (y - pictureHeight * 0.42f) / (pictureHeight * 0.18f)));
            word.setAlpha(f);
            int show = f <= 0.01f ? GONE : VISIBLE;
            if (word.getVisibility() != show) word.setVisibility(show);
        }
    }
}
