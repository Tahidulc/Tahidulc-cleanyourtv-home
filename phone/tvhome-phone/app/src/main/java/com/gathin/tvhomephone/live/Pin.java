package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;

/**
 * The guide's picture kept in sight as the guide is scrolled down, held upright (the family's ask, 24 Nov: "the preview
 * of what I'm currently playing to still show as I'm going down the TV guide"). Fold keeps it at the top while what is
 * under it (the programme's details, Recently watched, Your channels) folds away beneath it, then shrinks it with the
 * finger to a small preview, so the guide has most of the screen: on a phone a rounded 16:9 tile about 45% of the width
 * at the right, with the channel's logo, what's on and a thin line for how far in it is at its left; on a tablet the
 * band's picture at its left, the details beside it giving way to the same. Back down, it grows again. A tap on the small
 * picture is full screen; on the channel beside it, the picture and the details open out again.
 * <p>
 * Only the same views move and scale, so the player is never made again and the stream never buffers again, and nothing
 * is laid out while it moves. With nothing playing it isn't kept, and folds away with the rest as before.
 */
@SuppressLint("ViewConstructor")
final class Pin extends FrameLayout implements Fold.Shrinking {
    /** The small picture's width: of the screen's, on a phone and on a tablet. */
    static final float PHONE_SHARE = 0.45f, TABLET_SHARE = 0.30f;
    /** How far it has shrunk when a tap on the picture is full screen (and its controls are put away). */
    static final float COMPACT_AT = 0.5f;
    /**
     * On a phone the channel beside it comes in from here, all there by INFO_FULL_AT (under the picture, which stands
     * over it: shown as the picture shrinks off it). On a tablet it stands over the band, so it comes in only once the
     * picture is clear of it (clearAt), all there by the end.
     */
    static final float INFO_FROM = 0.3f, INFO_FULL_AT = 0.8f;
    /** A tablet's channel beside the small picture: at most this wide (dp). */
    static final int TABLET_INFO_DP = 380;
    /** The tablet's details beside the picture: gone by here. */
    static final float DETAILS_GONE_AT = 0.45f;
    /** The picture's corners, as it is and small (dp, as they look). */
    static final float CORNER_SMALL_DP = 12;

    private final Library lib;
    private final Inline picture;
    /** On a tablet: the band (the picture with the details beside it) and its details; null on a phone. */
    private final LinearLayout band;
    private final View details;
    private final LinearLayout info;
    private final FrameLayout logoBox;
    private final ImageView logo;
    private final TextView initials, name, live, title, left;
    private final ProgressBar line;
    private final int padL, padT, padR, padB, gap, slide;
    private final float cornerFull, cornerSmall;
    /** As last measured: the picture's size, the small picture's, how small that is, and this one shrunk all it goes. */
    private int pw, ph, cw, ch, compact;
    private float kMin = 1f;
    /** Where the channel beside the small picture comes in and is all there (how far shrunk). */
    private float infoFrom = INFO_FROM, infoTo = INFO_FULL_AT;
    /** As last shrunk. */
    private float by = -1f;
    private int shown = Integer.MAX_VALUE, bandShown = -1;
    private String logoFor;
    /** What's on, written again (posted: shrinking can come in the middle of laying out). */
    private final Runnable refresher = this::refresh;

    /** A phone held upright: the picture across the top. open: what a tap on the channel beside the small one does. */
    Pin(Context c, Library lib, Inline picture, Runnable open) {
        this(c, lib, picture, null, null, open, 12, 6, 12, 4);
        addView(picture, new LayoutParams(-1, -2));
        addView(info, new LayoutParams(0, 0));
    }

    /** A tablet held upright: band (the picture and details beside it, a panel of glass) across the top. */
    Pin(Context c, Library lib, Inline picture, LinearLayout band, View details, Runnable open) {
        this(c, lib, picture, band, details, open, 12, 6, 12, 8);
        addView(band, new LayoutParams(-1, -2));
        // The band clipped to what shows of it as it shrinks, its corners round (its glass and its shadow with it).
        float r = Ui.dp(c, 24);
        band.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                int h = bandShown < 0 ? v.getHeight() : Math.min(v.getHeight(), bandShown);
                o.setRoundRect(0, 0, v.getWidth(), Math.max(0, h), r);
            }
        });
        band.setClipToOutline(true);
        // Over the band's glass (which stands off the ground), not under it.
        info.setElevation(band.getElevation() + Ui.dp(c, 1));
        addView(info, new LayoutParams(0, 0));
    }

    private Pin(Context c, Library lib, Inline picture, LinearLayout band, View details, Runnable open,
                int l, int t, int r, int b) {
        super(c);
        this.lib = lib;
        this.picture = picture;
        this.band = band;
        this.details = details;
        padL = Ui.dp(c, l);
        padT = Ui.dp(c, t);
        padR = Ui.dp(c, r);
        padB = Ui.dp(c, b);
        gap = Ui.dp(c, band == null ? 12 : 16);
        slide = Ui.dp(c, 14);
        cornerFull = Ui.dp(c, band == null ? 18 : 16);
        cornerSmall = Ui.dp(c, CORNER_SMALL_DP);
        setClipChildren(false);
        setClipToPadding(false);

        // The channel beside the small picture: its logo on its own colour and its name with LIVE, what's on, and how far in.
        info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setAlpha(0f);
        info.setVisibility(INVISIBLE);
        info.setClickable(true);
        info.setFocusable(true);
        info.setOnClickListener(v -> open.run());
        info.setBackground(TvLook.touch(c, Ui.round(c, 0, 14), 14));
        info.setContentDescription(c.getString(R.string.tv07_pin_open));
        int ip = Ui.dp(c, 2);
        info.setPadding(ip, 0, ip, 0);

        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        logoBox = new FrameLayout(c);
        logoBox.setElevation(Ui.dp(c, 1));
        initials = TvLook.text(c, "", 12, TvLook.L1, true);
        initials.setGravity(Gravity.CENTER);
        logoBox.addView(initials, new LayoutParams(-1, -1));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logoBox.addView(logo, new LayoutParams(-1, -1));
        int tile = Ui.dp(c, 38);
        top.addView(logoBox, new LinearLayout.LayoutParams(tile, tile));
        LinearLayout who = new LinearLayout(c);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(Ui.dp(c, 9), 0, 0, 0);
        name = TvLook.text(c, "", 12.5f, TvLook.L1, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        who.addView(name, new LinearLayout.LayoutParams(-1, -2));
        live = TvLook.text(c, c.getString(R.string.tv07_pin_live), 10.5f, TvLook.RED, true);
        live.setLetterSpacing(0.08f);
        live.setSingleLine(true);
        live.setPadding(0, Ui.dp(c, 4), 0, 0);
        // A red dot before LIVE.
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(TvLook.RED);
        int ds = Ui.dp(c, 6);
        dot.setSize(ds, ds);
        live.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        live.setCompoundDrawablePadding(Ui.dp(c, 5));
        who.addView(live, new LinearLayout.LayoutParams(-1, -2));
        top.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
        info.addView(top, new LinearLayout.LayoutParams(-1, -2));
        info.addView(new Space(c), new LinearLayout.LayoutParams(-1, 0, 1f));

        title = TvLook.text(c, "", band == null ? 14 : 15, TvLook.L1, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(Ui.dp(c, 1), 1f);
        info.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout foot = new LinearLayout(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        line = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        line.setMax(1000);
        TvLook.line(line);
        foot.addView(line, new LinearLayout.LayoutParams(0, Math.max(2, Ui.dp(c, 3)), 1f));
        left = TvLook.text(c, "", 11, TvLook.L3, false);
        left.setSingleLine(true);
        left.setPadding(Ui.dp(c, 8), 0, 0, 0);
        foot.addView(left, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = Ui.dp(c, 7);
        fp.bottomMargin = Ui.dp(c, 2);
        info.addView(foot, fp);
    }

    // ---- the pure parts ------------------------------------------------------------------------------------------

    /** Pure: whether the picture is kept in sight: only while a channel plays in it, and it is there to keep. */
    static boolean kept(boolean playing, boolean shown) {
        return playing && shown;
    }

    /** Pure: the small picture's width for a screen width wide, never wider than the picture itself. */
    static int compactWidth(int wide, int picture, boolean tablet) {
        if (wide <= 0 || picture <= 0) return Math.max(0, picture);
        return Math.min(picture, Math.round(wide * (tablet ? TABLET_SHARE : PHONE_SHARE)));
    }

    /** Pure: the picture's scale as it has shrunk by (0 to 1), small being kMin. */
    static float scale(float by, float kMin) {
        float b = Math.max(0f, Math.min(1f, by));
        return 1f - b * (1f - kMin);
    }

    /** Pure: shrunk far enough that a tap is full screen (and its controls are put away). */
    static boolean compact(float by) {
        return by >= COMPACT_AT;
    }

    /** Pure: how much the channel beside the small picture shows (0 to 1), coming in from from, all there by to. */
    static float infoAlpha(float by, float from, float to) {
        if (to <= from) return by >= to ? 1f : 0f;
        return Math.max(0f, Math.min(1f, (by - from) / (to - from)));
    }

    /**
     * Pure: how far a picture picture wide (its scale going down to kMin) has shrunk once it is no wider than room (so
     * clear of what stands beside its small self); 1 when it never is.
     */
    static float clearAt(int picture, int room, float kMin) {
        if (picture <= 0 || kMin >= 1f) return 1f;
        float k = room / (float) picture;
        if (k >= 1f) return 0f;
        return Math.max(0f, Math.min(1f, (1f - k) / (1f - kMin)));
    }

    /** Pure: how much the tablet's details beside the picture show (1 to 0). */
    static float detailsAlpha(float by) {
        return Math.max(0f, Math.min(1f, 1f - by / DETAILS_GONE_AT));
    }

    /**
     * Pure: the picture's corner in its own (unscaled) pixels, for it to look full as it is and small (px, as seen) small,
     * at scale k.
     */
    static float corner(float by, float full, float small, float k) {
        float b = Math.max(0f, Math.min(1f, by));
        float seen = full + (small - full) * b;
        return k <= 0f ? seen : seen / k;
    }

    // ---- measuring and laying out ----------------------------------------------------------------------------------

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), inner = Math.max(0, w - padL - padR);
        int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int full;
        if (band == null) {
            picture.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), free);
            full = padT + picture.getMeasuredHeight() + padB;
        } else {
            band.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), free);
            full = padT + band.getMeasuredHeight() + padB;
        }
        pw = picture.getMeasuredWidth();
        ph = picture.getMeasuredHeight();
        cw = compactWidth(w, pw, band != null);
        kMin = pw > 0 ? Math.min(1f, cw / (float) pw) : 1f;
        ch = Math.round(ph * kMin);
        compact = band == null ? padT + ch + padB : padT + band.getPaddingTop() + ch + band.getPaddingBottom() + padB;
        // The channel beside the small picture: on a phone at its left, under the picture until it shrinks off it; on a
        // tablet at its right in the band, over it, so only once the picture is clear of it.
        int iw = band == null ? inner - cw - gap
                : Math.min(Ui.dp(getContext(), TABLET_INFO_DP), inner - band.getPaddingLeft() - cw - gap - Ui.dp(getContext(), 14));
        if (band == null) {
            infoFrom = INFO_FROM;
            infoTo = INFO_FULL_AT;
        } else {
            infoFrom = Math.min(0.95f, clearAt(pw, cw + gap, kMin));
            infoTo = 1f;
        }
        info.measure(MeasureSpec.makeMeasureSpec(Math.max(0, iw), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, ch), MeasureSpec.EXACTLY));
        setMeasuredDimension(w, full);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x, y;
        if (band == null) {
            picture.layout(padL, padT, padL + pw, padT + ph);
            // Shrinks toward its top right, staying at the right.
            picture.setPivotX(pw);
            x = padL;
            y = padT;
        } else {
            band.layout(padL, padT, padL + band.getMeasuredWidth(), padT + band.getMeasuredHeight());
            // Shrinks toward its top left, where it is in the band.
            picture.setPivotX(0);
            x = padL + band.getPaddingLeft() + cw + gap;
            y = padT + band.getPaddingTop();
        }
        picture.setPivotY(0);
        info.layout(x, y, x + info.getMeasuredWidth(), y + info.getMeasuredHeight());
        // Laid out again (turned, or the details changed): as it was shrunk, on the new sizes.
        float was = by;
        by = -1f;
        if (was >= 0f) shrink(was, shown);
    }

    // ---- shrinking: only moved, scaled and faded -----------------------------------------------------------------

    @Override public int compactHeight() {
        return compact;
    }

    @Override public void shrink(float by, int shown) {
        if (by == this.by && shown == this.shown) return;
        boolean infoWas = this.by >= 0f && infoAlpha(this.by, infoFrom, infoTo) > 0f;
        this.by = by;
        this.shown = shown;
        float k = scale(by, kMin);
        picture.setScaleX(k);
        picture.setScaleY(k);
        picture.corner(corner(by, cornerFull, cornerSmall, k));
        picture.setCompact(compact(by));
        float a = infoAlpha(by, infoFrom, infoTo);
        info.setAlpha(a);
        // From the picture's side, as it comes out from under it (a phone) or as the picture clears it (a tablet).
        info.setTranslationX((band == null ? 1 : -1) * (1f - a) * slide);
        info.setVisibility(a > 0f ? VISIBLE : INVISIBLE);
        // Coming into sight: what's on as it is now.
        if (a > 0f && !infoWas) {
            removeCallbacks(refresher);
            post(refresher);
        }
        if (band != null) {
            float d = detailsAlpha(by);
            details.setAlpha(d);
            details.setImportantForAccessibility(d > 0f ? IMPORTANT_FOR_ACCESSIBILITY_AUTO : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            int bs = by <= 0f ? -1 : shown - padT - padB;
            if (bs != bandShown) {
                bandShown = bs;
                band.invalidateOutline();
            }
        }
    }

    /** Below what shows of it (shrunk, the guide is there) isn't its own; within it, the rest folded beneath isn't touched. */
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return super.dispatchTouchEvent(e);
        if (e.getY() >= shown) return false;
        super.dispatchTouchEvent(e);
        return true;
    }

    /** Shrunk on a tablet, the faded details beside the picture aren't to be touched (the channel over them is). */
    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        if (band != null && e.getActionMasked() == MotionEvent.ACTION_DOWN && by >= DETAILS_GONE_AT
                && !within(info, e) && !within(picture, e)) return true;
        return super.onInterceptTouchEvent(e);
    }

    private boolean within(View v, MotionEvent e) {
        if (v.getVisibility() != VISIBLE) return false;
        float x = e.getX(), y = e.getY();
        for (View p = v; p != this && p != null; p = (View) p.getParent()) {
            x -= p.getLeft();
            y -= p.getTop();
            if (!(p.getParent() instanceof View)) return false;
        }
        // The picture shrinks from its pivot: its scaled box.
        float sx = v.getScaleX(), sy = v.getScaleY();
        float px = v.getPivotX(), py = v.getPivotY();
        float lx = px + (x - px) / (sx == 0 ? 1 : sx), ly = py + (y - py) / (sy == 0 ? 1 : sy);
        return lx >= 0 && ly >= 0 && lx < v.getWidth() && ly < v.getHeight();
    }

    // ---- the channel beside the small picture ---------------------------------------------------------------------

    /** What plays now, its logo, what's on and how far in (as a channel plays, and as the minute moves on). */
    void refresh() {
        Channel c = picture.channel();
        if (c == null) return;
        Context ctx = getContext();
        int n = lib.number(c);
        put(name, n > 0 ? n + "  " + c.name : c.name);
        String url = Ui.logo(c);
        String key = url == null ? "#" + c.key : url;
        if (!key.equals(logoFor)) {
            logoFor = key;
            int p = LogoTiles.pad(ctx, 5);
            logo.setPadding(p, p, p, p);
            if (url == null) {
                int col = GuideLook.nameColour(c.name);
                logoBox.setBackground(LogoTiles.ground(ctx, col, col, 10));
                Glide.with(logo).clear(logo);
                initials.setText(Ui.initials(c.name));
            } else {
                initials.setText("");
                logoBox.setBackground(TvLook.glass(ctx, 10));
                LogoTiles.show(logoBox, 10, false, logo, url, TvLook.GROUND, () -> initials.setText(Ui.initials(c.name)));
            }
        }
        Guide.Schedule s = lib.schedule(c.key);
        long now = System.currentTimeMillis();
        int at = s == null ? -1 : s.at(now);
        if (at >= 0 && !s.title[at].isEmpty()) {
            put(title, s.title[at]);
            line.setVisibility(Theme.progress ? VISIBLE : INVISIBLE);
            line.setProgress(Math.round(1000 * TvLook.progress(s.start[at], s.stop[at], now)));
            put(left, ctx.getString(R.string.live_min_left, Math.max(1, (int) ((s.stop[at] - now + 59_999) / 60_000))));
            info.setContentDescription(ctx.getString(R.string.tv07_pin_open_named, c.name, s.title[at]));
        } else {
            put(title, c.group);
            line.setVisibility(INVISIBLE);
            put(left, "");
            info.setContentDescription(ctx.getString(R.string.tv07_pin_open_named, c.name, c.group));
        }
    }

    /** Writing changed only when it is different (the same again would lay it out again for nothing). */
    private static void put(TextView t, CharSequence s) {
        if (!TextUtils.equals(t.getText(), s)) t.setText(s);
    }
}
