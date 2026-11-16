package com.gathin.tvhomephone.glass;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.resource.bitmap.BitmapTransitionOptions;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.live.SourceQuality;

/**
 * A poster as the family's reference picture draws them: the picture in a rounded card whose foot fades into the
 * poster's own colour (read from the picture, darkened), the title's logo over the fade with its genre and rating under
 * it, where it stands today on the top left ("#1 Today", "Trending", "New") and what the family's own sources have of it
 * on the top right (4K, 1080p, 720p, and Dolby's mark for Dolby Vision or Atmos); the title and its year under the card.
 * Every size follows the card's width, so a row's large cards and a grid's smaller ones look alike. Landscape cards (the
 * family's choice in Settings, "Posters: Landscape") show the title's backdrop at 16:9 with its logo at the foot's left.
 */
public final class PosterCard extends LinearLayout {
    /** What a card shows. */
    public static final class Info {
        public String title = "", year = "", poster, logo, line = "", badge;
        /** The backdrop (w780) for a landscape card; the poster stands in where a title has none. */
        public String backdrop;
        /** The badge's fill for words of its own ("THU 5 · TOMORROW", "3 LEFT"); the known words have their own. */
        public int badgeFill = 0xCC080A1E;
        /** What the sources have (SourceQuality's bits). */
        public int quality;
        /** How far through, 0 to 1; negative for no bar. */
        public float progress = -1;
        /** Whether the logo has been looked for: until then neither it nor the title in words shows over the fade. */
        public boolean logoKnown;
    }

    /** The reference's card is 160dp wide; everything else is in proportion to the width. */
    static final float BASE_DP = 160f;
    /** A landscape card's words and marks are the size of a 160dp portrait card's at this width. */
    static final float WIDE_BASE_DP = 256f;
    private static final String PREFS = "posters", WIDE = "landscape";
    /** The fade's colour for each poster read, so a card scrolled back to has it at once. */
    private static final LruCache<String, Integer> TINTS = new LruCache<>(400);

    /** The picture's card (rounded, 2:3, or 16:9 when landscape), for a shimmer while its row loads. */
    public final FrameLayout art;
    private final ImageView picture, logo;
    private final View bar, barBack;
    private final Fade fade = new Fade();
    private final TextView badge, name, line, title, year, chip;
    private final LinearLayout marks, dolby, corner;
    private final TextView dolbyWords, hdrChip;
    /** What the sources have, as last shown (SourceQuality's bits). */
    private int quality;
    private final float scale, radius;
    private final int widthPx, logoW, logoH;
    /** A landscape card: the backdrop at 16:9, the logo and its line at the foot's left. */
    private final boolean wide;
    private String bound = "";
    private ValueAnimator tintMove;
    private boolean roomForDolby;

    /** widthPx: the card's width; fixed: the card is exactly that wide (a row's), else it fills its cell (a grid's). */
    public PosterCard(Context c, int widthPx, boolean fixed) {
        this(c, widthPx, fixed, false);
    }

    /** As above; wide: a landscape card (the backdrop at 16:9) rather than a portrait poster. */
    public PosterCard(Context c, int widthPx, boolean fixed, boolean wide) {
        super(c);
        setOrientation(VERTICAL);
        this.wide = wide;
        this.widthPx = Math.max(1, widthPx);
        float density = c.getResources().getDisplayMetrics().density;
        scale = this.widthPx / ((wide ? WIDE_BASE_DP : BASE_DP) * density);
        fade.wide = wide;
        radius = Math.max(dp(c, 8), px(12));
        float words = Math.max(0.82f, Math.min(1.1f, scale));

        art = new Art(c);
        Glass.round(art, radius);
        art.setDuplicateParentStateEnabled(true);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(radius);
        art.setForeground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask));
        addView(art, new LayoutParams(fixed ? this.widthPx : LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        picture = new ImageView(c);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        art.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(c);
        shade.setBackground(fade);
        art.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        // The foot: the logo (or the title in words), then "Genre • ★ 8.2"; at the left on a landscape card.
        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(VERTICAL);
        foot.setGravity(wide ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        foot.setPadding(px(wide ? 13 : 10), 0, px(wide ? 13 : 10), px(wide ? 11 : 13));
        logoW = Math.round(this.widthPx * (wide ? 0.5f : 0.8f));
        logoH = Math.round(this.widthPx * (wide ? 0.17f : 0.3f));
        logo = new ImageView(c);
        logo.setAdjustViewBounds(true);
        logo.setMaxWidth(logoW);
        logo.setMaxHeight(logoH);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        foot.addView(logo, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        name = Glass.text(c, "", Math.max(13f, 18f * scale), Glass.TEXT, Glass.heading(c, 700));
        name.setGravity(wide ? Gravity.START : Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setLineSpacing(0, 0.92f);
        name.setShadowLayer(px(6), 0, px(1), 0x99000000);
        name.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        foot.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        line = Glass.text(c, "", Math.max(10f, 13.5f * scale), 0xC4FFFFFF, Glass.bodySemi(c));
        line.setGravity(wide ? Gravity.START : Gravity.CENTER);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(7);
        foot.addView(line, lp);
        art.addView(foot, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Where it stands, on the top left.
        badge = Glass.text(c, "", Math.max(10f, 11.5f * scale), Glass.TEXT, Glass.heading(c, 600));
        badge.setSingleLine(true);
        badge.setPadding(px(7), px(3.5f), px(7), px(3.5f));
        badge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bp.setMargins(px(9), px(9), 0, 0);
        art.addView(badge, bp);

        // What the sources have, on the top right: Dolby's mark, then the picture's chip.
        marks = new LinearLayout(c);
        marks.setOrientation(HORIZONTAL);
        marks.setGravity(Gravity.CENTER_VERTICAL);
        marks.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        dolby = new LinearLayout(c);
        dolby.setOrientation(VERTICAL);
        dolby.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView dd = new ImageView(c);
        dd.setImageResource(R.drawable.fmt_dolby);
        dd.setAdjustViewBounds(true);
        dd.setScaleType(ImageView.ScaleType.FIT_CENTER);
        dolby.addView(dd, new LayoutParams(LayoutParams.WRAP_CONTENT, px(9f)));
        dolbyWords = Glass.text(c, "", Math.max(4.5f, 4.8f * scale), 0xE6FFFFFF, Glass.bodySemi(c));
        dolbyWords.setLetterSpacing(0.1f);
        dolbyWords.setSingleLine(true);
        LayoutParams dw = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        dw.topMargin = px(1.5f);
        dolby.addView(dolbyWords, dw);
        LayoutParams dlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        dlp.rightMargin = px(6);
        marks.addView(dolby, dlp);
        chip = Glass.text(c, "", Math.max(9f, 10.5f * scale), 0xFF15161A, Glass.heading(c, 700));
        chip.setSingleLine(true);
        chip.setPadding(px(4.5f), px(1.5f), px(4.5f), px(1.5f));
        GradientDrawable white = new GradientDrawable();
        white.setColor(0xF2FFFFFF);
        white.setCornerRadius(px(4));
        chip.setBackground(white);
        marks.addView(chip);
        // Under them, the HDR in words ("HDR10", "HDR10+", "HLG", "HDR"; "DV" when Dolby's mark has no room), outlined.
        hdrChip = Glass.text(c, "", Math.max(8.5f, 9.5f * scale), 0xFFFFFFFF, Glass.heading(c, 700));
        hdrChip.setSingleLine(true);
        hdrChip.setLetterSpacing(0.04f);
        hdrChip.setPadding(px(4.5f), px(1.5f), px(4.5f), px(1.5f));
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(0x73000000);
        outline.setStroke(Math.max(1, px(1)), 0xE6FFFFFF);
        outline.setCornerRadius(px(4));
        hdrChip.setBackground(outline);
        hdrChip.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        corner = new LinearLayout(c);
        corner.setOrientation(VERTICAL);
        corner.setGravity(Gravity.END);
        corner.addView(marks, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LayoutParams hp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        hp.topMargin = px(4);
        corner.addView(hdrChip, hp);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        mp.setMargins(0, px(11), px(10), 0);
        art.addView(corner, mp);

        // How far through, along the foot.
        barBack = new View(c);
        barBack.setBackgroundColor(0x40FFFFFF);
        art.addView(barBack, new FrameLayout.LayoutParams(-1, dp(c, 3), Gravity.BOTTOM));
        bar = new View(c);
        bar.setBackgroundColor(0xFFFFFFFF);
        art.addView(bar, new FrameLayout.LayoutParams(0, dp(c, 3), Gravity.BOTTOM | Gravity.START));

        // Under the card: the title, then its year.
        title = Glass.text(c, "", 15f * words, 0xFFF2F2F5, Glass.body(c));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams tp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tp.topMargin = Math.round(dp(c, 10) * words);
        tp.leftMargin = tp.rightMargin = dp(c, 1);
        addView(title, tp);
        year = Glass.text(c, "", 13.5f * words, 0xFF8E8E93, Glass.body(c));
        year.setSingleLine(true);
        year.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams yp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        yp.topMargin = Math.round(dp(c, 4) * words);
        yp.leftMargin = yp.rightMargin = dp(c, 1);
        addView(year, yp);
    }

    private int px(float dpAtBase) {
        return Math.round(dpAtBase * getResources().getDisplayMetrics().density * scale);
    }

    private static int dp(Context c, float v) {
        return Glass.dp(c, v);
    }

    // ---- showing -----------------------------------------------------------------------------------------------

    /** A title: its picture, words, marks and fade (the poster's own colour once its picture is read). */
    public void bind(Info i) {
        Context c = getContext();
        String seed = i.title == null ? "" : i.title;
        GradientDrawable ground = Glass.placeholder(seed, radius);
        art.setBackground(ground);
        int[] pair = ground.getColors();
        int start = pair != null && pair.length > 1 ? shade(pair[1]) : 0xFF16171C;

        String words = i.badge == null ? "" : i.badge.trim();
        badge.setVisibility(words.isEmpty() ? GONE : VISIBLE);
        if (!words.isEmpty()) {
            int[] look = badgeLook(words, i.badgeFill);
            badge.setText(words);
            badge.setTextColor(look[1]);
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(look[0]);
            pill.setCornerRadius(px(6));
            badge.setBackground(pill);
        }

        showQuality(i.quality);

        line.setText(i.line == null ? "" : i.line);
        line.setVisibility(i.line == null || i.line.isEmpty() ? GONE : VISIBLE);
        title.setText(seed);
        year.setText(i.year == null || i.year.isEmpty() ? " " : i.year);
        showLogo(i.logoKnown, i.logo, seed);

        boolean along = i.progress >= 0;
        bar.setVisibility(along ? VISIBLE : GONE);
        barBack.setVisibility(along ? VISIBLE : GONE);
        if (along) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bar.getLayoutParams();
            lp.width = Math.round(widthPx * Math.max(0f, Math.min(1f, i.progress)));
            bar.setLayoutParams(lp);
        }

        String url = wide && i.backdrop != null && !i.backdrop.isEmpty() ? i.backdrop : i.poster == null ? "" : i.poster;
        bound = url;
        if (tintMove != null) tintMove.cancel();
        Integer had = url.isEmpty() ? null : TINTS.get(url);
        fade.setColour(had != null ? had : start);
        if (url.isEmpty()) {
            clear(picture);
            return;
        }
        try {
            Glide.with(picture).asBitmap().load(url).override(widthPx, height(widthPx)).centerCrop()
                    .format(DecodeFormat.PREFER_RGB_565).transition(BitmapTransitionOptions.withCrossFade(180))
                    .into(new BitmapImageViewTarget(picture) {
                        @Override public void onResourceReady(@NonNull Bitmap b, Transition<? super Bitmap> t) {
                            super.onResourceReady(b, t);
                            if (!url.equals(bound)) return;
                            Integer known = TINTS.get(url);
                            int tint = known != null ? known : tintOf(b);
                            if (known == null) TINTS.put(url, tint);
                            moveTint(tint);
                        }
                    });
        } catch (RuntimeException ignored) {
            // the screen went away
        }
    }

    /** What the sources have, learnt after the card showed (the provider's own facts): its corner's marks again. */
    public void setQuality(int q) {
        if (q != quality) showQuality(q);
    }

    /** The corner: the picture's chip, Dolby's mark beside it (room allowing), the HDR in words under them. */
    private void showQuality(int q) {
        quality = q;
        String pic = SourceQuality.picture(q);
        chip.setText(pic);
        chip.setVisibility(pic.isEmpty() ? GONE : VISIBLE);
        String dw = dolbyWords(q);
        roomForDolby = scale >= 0.8f;
        dolbyWords.setText(dw);
        dolby.setVisibility(dw.isEmpty() || !roomForDolby ? GONE : VISIBLE);
        marks.setVisibility(pic.isEmpty() && dolby.getVisibility() == GONE ? GONE : VISIBLE);
        showHdr();
    }

    private void showHdr() {
        String hw = hdrWords(quality, dolby.getVisibility() == VISIBLE);
        hdrChip.setText(hw);
        hdrChip.setVisibility(hw.isEmpty() ? GONE : VISIBLE);
        corner.setVisibility(marks.getVisibility() == GONE && hdrChip.getVisibility() == GONE ? GONE : VISIBLE);
    }

    /** The logo, read after the card showed ("" for none: the title in words instead). */
    public void setLogo(String url) {
        showLogo(true, url, title.getText().toString());
    }

    private void showLogo(boolean known, String url, String words) {
        boolean has = url != null && !url.isEmpty();
        if (has) {
            name.setVisibility(GONE);
            logo.setVisibility(VISIBLE);
            try {
                Glide.with(logo).load(url).override(logoW, logoH).fitCenter().format(DecodeFormat.PREFER_ARGB_8888)
                        .transition(DrawableTransitionOptions.withCrossFade(160)).into(logo);
            } catch (RuntimeException ignored) {
                // the screen went away
            }
        } else {
            clear(logo);
            logo.setVisibility(GONE);
            name.setText(words);
            name.setVisibility(known && !words.isEmpty() ? VISIBLE : GONE);
        }
    }

    /** While its row loads: the shimmer given, and nothing on it (the card keeps its height). */
    public void loading(Drawable shimmer) {
        bound = "";
        art.setBackground(shimmer);
        clear(picture);
        clear(logo);
        fade.setColour(0x00000000);
        badge.setVisibility(GONE);
        corner.setVisibility(GONE);
        quality = 0;
        name.setVisibility(GONE);
        logo.setVisibility(GONE);
        line.setVisibility(GONE);
        bar.setVisibility(GONE);
        barBack.setVisibility(GONE);
        title.setText(" ");
        year.setText(" ");
    }

    private void moveTint(int to) {
        if (tintMove != null) tintMove.cancel();
        int from = fade.colour;
        if (from == to) return;
        tintMove = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        tintMove.setDuration(260);
        tintMove.addUpdateListener(a -> fade.setColour((Integer) a.getAnimatedValue()));
        tintMove.start();
    }

    private static void clear(ImageView v) {
        try {
            Glide.with(v).clear(v);
        } catch (RuntimeException ignored) {
            // the screen went away
        }
        v.setImageDrawable(null);
    }

    /** The picture's height for a width: 2:3 for a poster, 16:9 for a landscape card. */
    private int height(int width) {
        return wide ? width * 9 / 16 : width * 3 / 2;
    }

    /** The card's 2:3 (or 16:9) frame, which gives Dolby's mark up when the badge on the left would run into it. */
    private final class Art extends FrameLayout {
        Art(Context c) {
            super(c);
        }

        @Override protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(height(width), MeasureSpec.EXACTLY));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            if (dolby.getVisibility() == VISIBLE && badge.getVisibility() == VISIBLE
                    && badge.getRight() + px(6) > corner.getLeft() + marks.getLeft()) {
                // Too tight for both: the picture's chip stays, Dolby's mark goes (its Vision said as "DV" under it).
                dolby.setVisibility(GONE);
                if (chip.getVisibility() == GONE) marks.setVisibility(GONE);
                showHdr();
                post(this::requestLayout);
            }
        }
    }

    // ---- pure helpers ---------------------------------------------------------------------------------------------

    /** Pure: "Mystery • ★ 8.2": the genre (in its short name) and the rating, either alone; "" for neither. */
    public static String line(String genre, double vote) {
        String g = shortGenre(genre);
        String v = vote >= 1 ? "★ " + String.format(java.util.Locale.UK, "%.1f", vote) : "";
        if (g.isEmpty()) return v;
        return v.isEmpty() ? g : g + " • " + v;
    }

    /** Pure: a genre's name as short as the posters say it ("Sci-Fi" for "Science Fiction", "Action" for "Action & Adventure"). */
    static String shortGenre(String genre) {
        String g = genre == null ? "" : genre.trim();
        switch (g) {
            case "Science Fiction":
            case "Sci-Fi & Fantasy": return "Sci-Fi";
            case "Action & Adventure": return "Action";
            case "War & Politics": return "War";
            default: return g;
        }
    }

    /** Pure: the words under Dolby's mark: "ATMOS·VISION", "VISION", "ATMOS"; "" for neither. */
    static String dolbyWords(int quality) {
        boolean dv = SourceQuality.dolbyVision(quality), atmos = SourceQuality.atmos(quality);
        if (dv && atmos) return "ATMOS·VISION";
        if (dv) return "VISION";
        return atmos ? "ATMOS" : "";
    }

    /**
     * Pure: the HDR in words for the chip under the picture's: "HDR10+", "HDR10", "HLG", "HDR"; Dolby Vision as "DV"
     * only when Dolby's own mark isn't shown (it says "VISION" itself); "" for none.
     */
    static String hdrWords(int quality, boolean dolbyShown) {
        if (SourceQuality.dolbyVision(quality)) return dolbyShown ? "" : "DV";
        return SourceQuality.hdr(quality);
    }

    /** Pure: a badge's fill and ink: "#1 Today" dark, "Trending" light teal, "New" amber; any other, the fill given in white. */
    static int[] badgeLook(String words, int fill) {
        if (words.startsWith("#")) return new int[]{0xD9161618, 0xFFFFFFFF};
        if (words.equals("Trending")) return new int[]{0xFFC9EBEE, 0xFF0F2A2E};
        if (words.equals("New")) return new int[]{0xFFFFD25A, 0xFF1E1606};
        return new int[]{fill, 0xFFFFFFFF};
    }

    /** The fade's colour from a poster: the average of its bottom third, darkened. */
    static int tintOf(Bitmap b) {
        Bitmap small = Bitmap.createScaledBitmap(b, 8, 12, true);
        int[] px = new int[8 * 4];
        small.getPixels(px, 0, 8, 0, 8, 8, 4);
        if (small != b) small.recycle();
        return shade(average(px));
    }

    /** Pure: the average colour of the pixels given (their alpha left out), opaque. */
    static int average(int[] px) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int p : px) {
            r += p >> 16 & 0xFF;
            g += p >> 8 & 0xFF;
            b += p & 0xFF;
            n++;
        }
        if (n == 0) return 0xFF000000;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /** Pure: a colour as the fade's: its own hue, a touch richer, and dark (a grey stays grey). */
    static int shade(int rgb) {
        float[] hsv = hsv(rgb);
        if (hsv[1] >= 0.08f) hsv[1] = Math.min(0.85f, hsv[1] * 1.15f);
        hsv[2] = 0.14f + 0.14f * hsv[2];
        return rgb(hsv[0], hsv[1], hsv[2]);
    }

    /** Pure: hue (0–360), saturation and value (0–1) of an RGB colour. */
    static float[] hsv(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = 60 * (((g - b) / d) % 6);
        else if (max == g) h = 60 * ((b - r) / d + 2);
        else h = 60 * ((r - g) / d + 4);
        if (h < 0) h += 360;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    /** Pure: an opaque RGB colour from hue, saturation and value. */
    static int rgb(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    /** The fade over a poster's foot: clear above, then into its colour, solid at the bottom (from higher up on a landscape card). */
    static final class Fade extends Drawable {
        private final Paint paint = new Paint();
        int colour = 0xFF16171C;
        boolean wide;
        private int madeFor = 1, madeHeight = -1;

        void setColour(int c) {
            if (c == colour) return;
            colour = c;
            invalidateSelf();
        }

        @Override public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty() || (colour >>> 24) == 0) return;
            if (madeFor != colour || madeHeight != b.height()) {
                int c = colour & 0x00FFFFFF;
                paint.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                        new int[]{c, c | 0x8C000000, c | 0xEB000000, c | 0xFF000000},
                        wide ? new float[]{0.24f, 0.52f, 0.8f, 1f} : new float[]{0.42f, 0.64f, 0.82f, 1f}, Shader.TileMode.CLAMP));
                madeFor = colour;
                madeHeight = b.height();
            }
            canvas.drawRect(b, paint);
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A row's card width for this screen: 37% of a phone's width (two and a bit across, as the reference), 136–176dp; 176dp on a tablet. */
    public static int rowWidth(Context c) {
        return rowWidth(c, false);
    }

    /** As above, or a landscape card's: 66% of a phone's width (one and a half across), 220–300dp; 300dp on a tablet. */
    public static int rowWidth(Context c, boolean wide) {
        int sw = c.getResources().getConfiguration().screenWidthDp;
        float w = wide ? (sw >= 600 ? 300f : Math.max(220f, Math.min(300f, sw * 0.66f)))
                : sw >= 600 ? 176f : Math.max(136f, Math.min(176f, sw * 0.37f));
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, w, c.getResources().getDisplayMetrics()));
    }

    /** Whether the family chose landscape posters (Settings, "Posters"); portrait unless chosen. */
    public static boolean wide(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(WIDE, false);
    }

    /** Portrait or landscape posters from now on: Home and Watch draw them so as they next open. */
    public static void setWide(Context c, boolean wide) {
        c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(WIDE, wide).apply();
    }

    /** Pure: a TMDB picture's address at the card's size (w780) when it was asked for larger (w1280, original). */
    public static String cardSize(String url) {
        if (url == null || url.isEmpty()) return url;
        return url.replace("/w1280/", "/w780/").replace("/original/", "/w780/");
    }
}
