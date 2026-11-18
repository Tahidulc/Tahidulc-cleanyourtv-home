package com.gathin.tvhomephone.glass;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Homefield on a tablet: the same app, laid out for the room it has. A phone (the shorter side under 600dp) is left
 * exactly as it is; a tablet gets its type a little larger (Wide.wrap), a floating tab bar no wider than it needs in
 * the middle of the foot, text columns and settings no wider than reads well, poster rows and grids with more across
 * at a sensible size, two columns where a page has the room, and its sheets as cards in the middle of the screen.
 */
public final class Wide {
    private Wide() {}

    /** A tablet: the screen's shorter side at least this wide (a phone on its side never is). */
    public static final int TABLET_DP = 600;
    /** Room for two columns side by side (a tablet held upright at 800dp, or on its side). */
    public static final int TWO_COLUMNS_DP = 720;
    /** How much larger a tablet's type is, and the most that makes of the family's own text size. */
    static final float TYPE = 1.1f, TYPE_CAP = 1.3f;
    /** The floating tab bar's width on a tablet. */
    public static final int BAR_MAX_DP = 560;
    /** A text column's widest (settings, a page's words). */
    public static final int TEXT_MAX_DP = 680;

    public static boolean tablet(Context c) {
        return tablet(c.getResources().getConfiguration());
    }

    static boolean tablet(Configuration cfg) {
        return cfg.smallestScreenWidthDp >= TABLET_DP;
    }

    /** The screen as wide as two columns need (on a tablet). */
    public static boolean twoColumns(Context c) {
        Configuration cfg = c.getResources().getConfiguration();
        return tablet(cfg) && cfg.screenWidthDp >= TWO_COLUMNS_DP;
    }

    /** Room for a list and its page side by side (a tablet on its side): Settings' two panes. */
    public static final int PANES_DP = 900;

    public static boolean panes(Context c) {
        Configuration cfg = c.getResources().getConfiguration();
        return tablet(cfg) && cfg.screenWidthDp >= PANES_DP;
    }

    /** A tablet on its side (wider than tall). */
    public static boolean sideways(Context c) {
        Configuration cfg = c.getResources().getConfiguration();
        return tablet(cfg) && cfg.screenWidthDp > cfg.screenHeightDp;
    }

    // ---- type ---------------------------------------------------------------------------------------------------------

    /**
     * The activity's context with a tablet's type a little larger: the family's own text size times TYPE, at most
     * TYPE_CAP (unless they chose more themselves). A phone's context comes back as it was, untouched.
     */
    public static Context wrap(Context base) {
        if (base == null) return null;
        Configuration cfg = base.getResources().getConfiguration();
        if (!tablet(cfg)) return base;
        Configuration o = new Configuration(cfg);
        o.fontScale = fontScale(cfg.fontScale);
        return base.createConfigurationContext(o);
    }

    /** Pure: a tablet's text size from the family's own: TYPE larger, not past TYPE_CAP unless theirs is already. */
    static float fontScale(float own) {
        float base = own > 0 ? own : 1f;
        return Math.max(base, Math.min(base * TYPE, TYPE_CAP));
    }

    // ---- widths -------------------------------------------------------------------------------------------------------

    /** The floating tab bar's width: the whole width less its margins on a phone (MATCH_PARENT), a set width on a tablet. */
    public static int barWidth(Context c) {
        if (!tablet(c)) return ViewGroup.LayoutParams.MATCH_PARENT;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return barWidth(dm.widthPixels, Glass.dp(c, BAR_MAX_DP), Glass.dp(c, 24));
    }

    /** Pure: the bar no wider than max, with at least margin either side of it. */
    static int barWidth(int screen, int max, int margin) {
        return Math.max(0, Math.min(max, screen - 2 * margin));
    }

    /**
     * Pure: the space either side of a column at most max wide in width, at least min either side: how a page keeps its
     * words to a width that reads well in the middle of a wide screen.
     */
    public static int side(int width, int max, int min) {
        return Math.max(min, (width - max) / 2);
    }

    /**
     * Pure: how many go across width, each at least min wide with gap between them (and gap at the end, as a grid's
     * spacing is), never fewer than least nor more than most.
     */
    public static int across(int width, int min, int gap, int least, int most) {
        int n = min + gap <= 0 ? least : (width - gap) / (min + gap);
        return Math.max(least, Math.min(most, n));
    }

    // ---- sheets as cards ------------------------------------------------------------------------------------------------

    /** A sheet's widest as a card on a tablet. */
    public static final int CARD_MAX_DP = 560;

    /**
     * A sheet (BottomSheetDialog, already shown) as a card in the middle of a tablet's screen, rounded all round, rather
     * than a sheet across the foot: its window as wide as the card and as tall as what it holds, centred, the page
     * dimmed behind it. On a phone it is left as a sheet.
     */
    public static void card(Dialog d) {
        if (d == null || !tablet(d.getContext())) return;
        Window w = d.getWindow();
        if (w == null) return;
        Context c = d.getContext();
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        int width = Math.min(Glass.dp(c, CARD_MAX_DP), dm.widthPixels - 2 * Glass.dp(c, 32));
        w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.CENTER);
        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        w.setDimAmount(0.5f);
        w.setBackgroundDrawable(new ColorDrawable(0));
        // The sheet's own see-through space round it would make the window the screen's height: gone, the window is as
        // tall as the card, and a tap off the card closes it as the dialog's own outside touch.
        View outside = w.findViewById(com.google.android.material.R.id.touch_outside);
        if (outside != null) outside.setVisibility(View.GONE);
        d.setCanceledOnTouchOutside(true);
        if (d instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<FrameLayout> b =
                    ((com.google.android.material.bottomsheet.BottomSheetDialog) d).getBehavior();
            // Most of the screen at most: what it holds scrolls inside it.
            b.setMaxHeight(Math.round(dm.heightPixels * 0.86f));
            b.setDraggable(false);
        }
        View sheet = w.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            // Rounded all round, in the sheet's own colour; put back after the sheet's first layout, which sets its own.
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(Glass.dp(c, 28));
            bg.setColor(cardColour(sheet));
            sheet.setBackground(bg);
            sheet.setClipToOutline(true);
            sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (v.getBackground() != bg) v.setBackground(bg);
            });
        }
    }

    /** The sheet's own colour (its theme's surface), for the card's ground. */
    private static int cardColour(View sheet) {
        android.graphics.drawable.Drawable bg = sheet.getBackground();
        if (bg instanceof com.google.android.material.shape.MaterialShapeDrawable) {
            android.content.res.ColorStateList fill = ((com.google.android.material.shape.MaterialShapeDrawable) bg).getFillColor();
            if (fill != null) return fill.getDefaultColor();
        }
        if (bg instanceof ColorDrawable) return ((ColorDrawable) bg).getColor();
        return 0xFF1B1C22;
    }
}
