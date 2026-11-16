package com.gathin.tvhomephone.glass;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.home.Type;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Homefield's glass, as the family's reference pictures have it: a warm, near-black ground with the page's picture
 * softly blurred behind (AmbientView); cards of frosted glass that let it through, with a fine bright rim where the
 * light catches (GlassDrawable); floating bars that blur whatever scrolls under them (BlurBehind); the bar whose lens
 * follows a finger along it, for the tabs and the segmented pills (LensBar); the cards with three posters fanned in
 * them (FanCard) and Continue watching's landscape card (WatchCard). White words, Outfit for headings, Inter for the
 * rest.
 */
public final class Glass {
    private Glass() {}

    /** The ground every glass page fades into. */
    public static final int GROUND = 0xFF0E0F14;
    public static final int TEXT = 0xFFFFFFFF, SOFT = 0xDEFFFFFF, MUTED = 0xA8FFFFFF, FAINT = 0x70FFFFFF;
    /** The floating tab bar's height and the gap under it. */
    public static final int BAR_DP = 64, BAR_GAP_DP = 10;
    /** Quiet pairs for a picture that hasn't come (or a title with none): warm brown, plum, teal, slate, rust. */
    private static final int[][] PAIRS = {
            {0xFF3A2A1E, 0xFF5C3B22}, {0xFF2B2140, 0xFF4A2D5C}, {0xFF173436, 0xFF24524F}, {0xFF22293A, 0xFF3A4660},
            {0xFF3D2420, 0xFF6A3A2A}, {0xFF1F2B45, 0xFF2E4A72}, {0xFF2E2A22, 0xFF524636}, {0xFF331F33, 0xFF5A3150}};

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static Typeface heading(Context c, int weight) {
        return Type.outfit(c, weight);
    }

    public static Typeface body(Context c) {
        return Type.inter(c);
    }

    public static Typeface bodySemi(Context c) {
        return Type.interSemi(c);
    }

    public static TextView text(Context c, CharSequence s, float sp, int colour, Typeface face) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        if (face != null) t.setTypeface(face);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A card of frosted glass, radius in dp. */
    public static GlassDrawable card(Context c, float radiusDp) {
        return new GlassDrawable(c, radiusDp, GlassDrawable.CARD);
    }

    /** The bright rim alone, for over a picture (a poster's, a card's). */
    public static GlassDrawable rim(Context c, float radiusDp) {
        return new GlassDrawable(c, radiusDp, GlassDrawable.RIM);
    }

    /** A title's own quiet gradient while its picture comes (the same title, the same colours). */
    public static GradientDrawable placeholder(String seed, float radiusPx) {
        int[] pair = PAIRS[Math.floorMod(seed == null ? 0 : seed.hashCode(), PAIRS.length)];
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, pair);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /** Rounded corners that clip a view and what it holds. */
    public static void round(View v, float radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    /** Pure: the greeting for a time of day. */
    public static String greeting(LocalTime t) {
        int h = t.getHour();
        if (h >= 5 && h < 12) return "Good morning";
        if (h >= 12 && h < 18) return "Good afternoon";
        return "Good evening";
    }

    /** Pure: the date as the header shows it ("Monday 16 November"). */
    public static String date(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK));
    }

    /** The insets a glass page fits itself to: the status bar's height, and the room at the foot for the tab bar. */
    public interface Fit {
        void fit(int top, int bottom);
    }

    /** The insets a page filling the window fits itself to on every side (fitSides). */
    public interface FitSides {
        void fit(int left, int top, int right, int bottom);
    }

    /**
     * Calls fit with the status bar's height and the room a page leaves at its foot (the navigation bar, the floating
     * tab bar and a gap), now and whenever they change. A glass page draws under the status bar and the tab bar.
     */
    public static void fit(View v, Fit f) {
        fitSides(v, (left, top, right, bottom) -> f.fit(top, bottom));
    }

    /**
     * As fit, with the system bars' widths at the sides too (the navigation bar sits at a side on a phone turned round
     * with its three buttons), for a page that fills the window edge to edge.
     */
    public static void fitSides(View v, FitSides f) {
        Context c = v.getContext();
        int bar = dp(c, BAR_DP + BAR_GAP_DP + 16);
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, ins) -> {
            Insets s = ins.getInsets(WindowInsetsCompat.Type.systemBars());
            int ime = ins.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            f.fit(s.left, s.top, s.right, Math.max(s.bottom + bar, ime));
            return ins;
        });
        f.fit(0, 0, 0, bar);
        // The insets come round again once it is on the screen (a page made before it was attached gets them then).
        if (v.isAttachedToWindow()) ViewCompat.requestApplyInsets(v);
        else v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                ViewCompat.requestApplyInsets(view);
            }

            @Override public void onViewDetachedFromWindow(View view) { }
        });
    }

    /** The page's header, as the reference has it: the family's mark, the greeting and the name; the date on the right. */
    public static final class Header {
        public final LinearLayout view;
        public final TextView hello, name, date, line;
        public final ImageView mark;

        Header(LinearLayout view, ImageView mark, TextView hello, TextView name, TextView date, TextView line) {
            this.view = view;
            this.mark = mark;
            this.hello = hello;
            this.name = name;
            this.date = date;
            this.line = line;
        }

        /** The greeting and date for now; the line under the date (the weather, the playlists) as given. */
        public void now(String under) {
            hello.setText(greeting(LocalTime.now()) + ",");
            date.setText(Glass.date(LocalDate.now()));
            line.setText(under == null ? "" : under);
            line.setVisibility(under == null || under.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    public static Header header(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // The family's own mark in a rounded square, as the reference's picture sits.
        ImageView mark = new ImageView(c);
        FrameLayout markBox = new FrameLayout(c);
        markBox.setBackground(placeholder("Homefield", dp(c, 12)));
        mark.setImageResource(R.drawable.ic_app_front);
        mark.setScaleType(ImageView.ScaleType.CENTER_CROP);
        View back = new View(c);
        back.setBackgroundResource(R.drawable.ic_app_back);
        markBox.addView(back, new FrameLayout.LayoutParams(-1, -1));
        markBox.addView(mark, new FrameLayout.LayoutParams(-1, -1));
        round(markBox, dp(c, 12));
        markBox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(markBox, new LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView hello = text(c, "Good morning,", 14, MUTED, body(c));
        TextView name = text(c, "Whitebrook Road", 17, TEXT, heading(c, 600));
        name.setSingleLine(true);
        words.addView(hello);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = dp(c, 3);
        words.addView(name, np);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = dp(c, 12);
        row.addView(words, wp);
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView date = text(c, "", 14, TEXT, heading(c, 500));
        date.setGravity(Gravity.END);
        TextView line = text(c, "", 12, MUTED, body(c));
        line.setGravity(Gravity.END);
        right.addView(date);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(c, 4);
        right.addView(line, lp);
        row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        Header h = new Header(row, mark, hello, name, date, line);
        h.now(null);
        return h;
    }

    /** A section's heading as the reference has them: "Continue watching ›", tappable when it leads somewhere. */
    public static TextView section(Context c, String title, View.OnClickListener more) {
        TextView t = text(c, title, 19, SOFT, heading(c, 500));
        t.setSingleLine(true);
        if (more != null) {
            Icons chevron = new Icons(c, Icons.CHEVRON, MUTED);
            int s = dp(c, 18);
            chevron.setBounds(0, 0, s, s);
            t.setCompoundDrawablesRelative(null, null, chevron, null);
            t.setCompoundDrawablePadding(dp(c, 2));
            t.setOnClickListener(more);
        }
        t.setPadding(dp(c, 20), dp(c, 24), dp(c, 20), dp(c, 12));
        // As wide as its words, so the chevron sits just after them.
        t.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return t;
    }

    /**
     * A section's heading row as the family's reference picture draws them: its words in white (first child), then a
     * round dark button with a chevron at the right edge when it leads somewhere (the words lead there too).
     */
    public static LinearLayout sectionRow(Context c, String title, View.OnClickListener more) {
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(dp(c, 20), dp(c, 26), dp(c, 16), dp(c, 14));
        TextView words = text(c, title, 21, TEXT, heading(c, 600));
        words.setSingleLine(true);
        words.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // As tall as the button, so headings with and without one sit the same.
        words.setMinHeight(dp(c, 38));
        words.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (more != null) {
            words.setOnClickListener(more);
            android.widget.ImageView b = new android.widget.ImageView(c);
            b.setImageDrawable(new Icons(c, Icons.CHEVRON, TEXT));
            b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            int pad = dp(c, 10);
            b.setPadding(pad, pad, pad, pad);
            android.graphics.drawable.GradientDrawable round = new android.graphics.drawable.GradientDrawable();
            round.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            round.setColor(0xFF26272C);
            b.setBackground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33FFFFFF), round, null));
            b.setContentDescription("All of " + title);
            b.setOnClickListener(more);
            head.addView(b, new LinearLayout.LayoutParams(dp(c, 38), dp(c, 38)));
        }
        return head;
    }

    /** A poster's tile: the picture filling a rounded card under the glass rim, its own colours until it comes. */
    public static ImageView poster(Context c, String seed, float radiusDp) {
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        float r = dp(c, radiusDp);
        v.setBackground(placeholder(seed, r));
        round(v, r);
        v.setForeground(rim(c, radiusDp));
        return v;
    }
}
