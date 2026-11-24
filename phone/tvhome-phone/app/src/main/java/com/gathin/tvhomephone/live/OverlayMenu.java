package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The scoreboard's and the channel logo's pop-up menus over the player (the family: "the pop ups for the scoreboard and
 * channel logo, make the pop up menu easier to use and have all the options for it in the pop up menu when I click on the
 * scoreboard and channel logo"). Not a sheet that dims the picture: a panel of 07's dark glass along the foot on a phone
 * held upright (never taller than leaves the board or the logo in sight), down the side on a tablet or on its side (the
 * side away from the one tapped), so each change shows on the picture as it is made. Its title, a close button and the
 * prominent buttons (the match centre) stay at its top with a row of its sections to jump to; under them, short plain
 * sections with their headers, rows a finger can hit (56 dp), a switch for on and off, steppers for sizes and timing,
 * pictures to pick a look from, each row saying whether it is for this channel or every channel. A tap on the picture
 * outside it puts it away (on the board or the logo: that one's menu instead), as Back does.
 */
final class OverlayMenu {
    /** Which one it is. */
    static final int BOARD = 1, LOGO = 2;

    /** What the menu asks of the player. */
    interface Host {
        /** A tap on the picture outside the panel, at (x, y) on the player: true when it was taken (on the board or the logo). */
        boolean tappedAt(float x, float y);

        /** It has gone, however it was put away. */
        void closed(OverlayMenu m);
    }

    /** The side panel's width, and a row's least height (dp). */
    static final int SIDE_DP = 380, ROW_DP = 56;
    /** The sheet's least and most share of the screen's height. */
    static final float SHEET_LEAST = 0.4f, SHEET_MOST = 0.62f;
    static final long SLIDE_MS = 260;

    final int which;
    private final Context c;
    private final FrameLayout root;
    private final Host host;
    /** Where the board or the logo is on the player now (kept in sight); null for none. */
    private final Supplier<Rect> thing;
    private final View catcher;
    final Panel panel;
    final ScrollView scroll;
    final LinearLayout body;
    private final LinearLayout top, jumps;
    private final TextView subtitle;
    private int insetLeft, insetTop;
    private boolean shown, gone, side, right;
    private final View.OnLayoutChangeListener relayout = (v, l, t, r, b, ol, ot, or, ob) -> {
        if (r - l != or - ol || b - t != ob - ot) v.post(this::lay);
    };

    /** The card: its height held to a share of the screen along the foot (0: as the side panel, the screen's height). */
    static final class Panel extends LinearLayout {
        int most;

        Panel(Context c) {
            super(c);
        }

        @Override protected void onMeasure(int w, int h) {
            if (most > 0) {
                int size = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? most : Math.min(most, MeasureSpec.getSize(h));
                h = MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
            }
            super.onMeasure(w, h);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    OverlayMenu(Context c, FrameLayout root, int which, String title, String sub, Supplier<Rect> thing, Host host) {
        this.c = c;
        this.root = root;
        this.which = which;
        this.thing = thing;
        this.host = host;
        // Over the whole picture, unseen: a tap on it puts the menu away (or opens the board's or the logo's), and the
        // picture takes no swipes meanwhile (no channel changed by a stray finger).
        catcher = new View(c);
        catcher.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        float slop = ViewConfiguration.get(c).getScaledTouchSlop();
        float[] down = new float[2];
        catcher.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = e.getX();
                down[1] = e.getY();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP
                    && Math.abs(e.getX() - down[0]) < slop * 2 && Math.abs(e.getY() - down[1]) < slop * 2) {
                if (!host.tappedAt(e.getX(), e.getY())) close();
            }
            return true;
        });

        panel = new Panel(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setElevation(Ui.dp(c, 12));
        panel.setContentDescription(title);

        // The head: the title, what it is on, and close.
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 4));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView t = TvLook.text(c, title, 19, TvLook.L1, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(t);
        subtitle = TvLook.text(c, sub == null ? "" : sub, 12.5f, TvLook.L3, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, Ui.dp(c, 4), 0, 0);
        subtitle.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        words.addView(subtitle);
        head.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView x = new ImageView(c);
        x.setImageResource(R.drawable.live_ic_close);
        x.setImageTintList(ColorStateList.valueOf(TvLook.L1));
        x.setScaleType(ImageView.ScaleType.CENTER);
        x.setBackground(TvLook.glass(c, 24));
        x.setContentDescription(c.getString(R.string.live_close));
        x.setOnClickListener(v -> close());
        head.addView(x, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // The prominent buttons (the match centre), then the sections to jump to.
        top = new LinearLayout(c);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(Ui.dp(c, 16), Ui.dp(c, 6), Ui.dp(c, 16), 0);
        panel.addView(top, new LinearLayout.LayoutParams(-1, -2));
        HorizontalScrollView js = new HorizontalScrollView(c);
        js.setHorizontalScrollBarEnabled(false);
        js.setClipToPadding(false);
        js.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 2));
        jumps = new LinearLayout(c);
        jumps.setGravity(Gravity.CENTER_VERTICAL);
        js.addView(jumps);
        panel.addView(js, new LinearLayout.LayoutParams(-1, -2));
        View line = new View(c);
        line.setBackgroundColor(TvLook.RIM);
        panel.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 0.75f))));

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), Ui.dp(c, 20));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    // ---- where it goes ----------------------------------------------------------------------------------------------

    /** Pure: down the side (true) or along the foot: on a tablet, or any screen wider than it is tall. */
    static boolean side(int w, int h, boolean tablet) {
        return tablet || w > h;
    }

    /** Pure: the side panel's width in px: 380 dp, at most 46% of the width, never under 300 dp (or the width less 24 dp). */
    static int sideWidth(int w, float density) {
        int want = Math.round(SIDE_DP * density), most = Math.round(w * 0.46f), least = Math.round(300 * density);
        return Math.max(Math.min(least, w - Math.round(24 * density)), Math.min(want, most));
    }

    /** Pure: the side panel on the right when the board or the logo sits in the left half (so it stays in sight). */
    static boolean onRight(int thingMid, int w) {
        return thingMid < w / 2;
    }

    /**
     * Pure: the sheet's tallest in px: up to just under the board or the logo (its foot and 12 dp), between 40% and 62% of
     * the height (thingBottom 0: none to keep in sight).
     */
    static int sheetMost(int h, int thingBottom, float density) {
        int most = Math.round(h * SHEET_MOST), least = Math.round(h * SHEET_LEAST);
        if (thingBottom <= 0) return most;
        return Math.max(least, Math.min(most, h - thingBottom - Math.round(12 * density)));
    }

    /** On the player, over everything there, sliding in. */
    void show(int insetLeft, int insetTop) {
        if (shown) return;
        shown = true;
        this.insetLeft = insetLeft;
        this.insetTop = insetTop;
        root.addView(catcher, new FrameLayout.LayoutParams(-1, -1));
        root.addView(panel, params());
        root.addOnLayoutChangeListener(relayout);
        panel.setAlpha(0f);
        panel.post(() -> {
            if (gone) return;
            float d = c.getResources().getDisplayMetrics().density;
            if (side) panel.setTranslationX((right ? 1 : -1) * 48 * d);
            else panel.setTranslationY(Math.max(panel.getHeight(), 120 * d) * 0.35f);
            panel.animate().alpha(1f).translationX(0f).translationY(0f).setDuration(SLIDE_MS).setInterpolator(TvLook.ease2()).start();
        });
    }

    boolean open() {
        return shown && !gone;
    }

    /** Put away (Back, close, a tap outside it, the small window, another channel). */
    void close() {
        if (gone) return;
        gone = true;
        root.removeOnLayoutChangeListener(relayout);
        root.removeView(catcher);
        panel.animate().cancel();
        panel.animate().alpha(0f).setDuration(160).withEndAction(() -> root.removeView(panel)).start();
        if (!shown) root.removeView(panel);
        host.closed(this);
    }

    /** Whether it lies along the foot (false: down the side), as last laid out. */
    boolean sheet() {
        return !side;
    }

    /** Laid out again (turned on its side, or upright). */
    private void lay() {
        if (!open()) return;
        panel.setLayoutParams(params());
    }

    private FrameLayout.LayoutParams params() {
        int w = root.getWidth(), h = root.getHeight();
        float d = c.getResources().getDisplayMetrics().density;
        boolean tablet = c.getResources().getConfiguration().smallestScreenWidthDp >= 600;
        Rect at = thing == null ? null : thing.get();
        side = w > 0 && h > 0 && side(w, h, tablet);
        int m = Ui.dp(c, 12);
        FrameLayout.LayoutParams lp;
        if (side) {
            right = at == null || onRight(at.centerX(), w);
            lp = new FrameLayout.LayoutParams(sideWidth(w, d), -1, Gravity.TOP | (right ? Gravity.END : Gravity.START));
            lp.setMargins(m + (right ? 0 : insetLeft), m + insetTop, m, m);
            panel.most = 0;
            panel.setBackground(TvLook.deep(c, 24));
        } else {
            lp = new FrameLayout.LayoutParams(w > 0 ? Math.min(w, Ui.dp(c, 640)) : -1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            panel.most = h > 0 ? sheetMost(h, at == null ? 0 : at.bottom, d) : 0;
            float r = 24 * d;
            panel.setBackground(TvLook.deep(c, 24).corners(r, r, 0, 0));
        }
        return lp;
    }

    /** What fills it (BoardMenus), kept to fill it again. */
    private Consumer<OverlayMenu> filler;

    /** Filled now (its rows, its buttons along the top), and again by refresh. */
    void fill(Consumer<OverlayMenu> f) {
        filler = f;
        f.accept(this);
    }

    /** Filled again where it was scrolled to (a change that changes other rows: a look as every channel's). */
    void refresh() {
        if (filler == null || gone) return;
        int y = scroll.getScrollY();
        top.removeAllViews();
        jumps.removeAllViews();
        body.removeAllViews();
        filler.accept(this);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    // ---- the top: the prominent buttons ----------------------------------------------------------------------------

    /** The first, prominent button (the match centre): white, the width of the panel. */
    TextView primary(int icon, String words, Runnable r) {
        TextView b = button(icon, words, true);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 52));
        lp.bottomMargin = Ui.dp(c, 6);
        top.addView(b, lp);
        return b;
    }

    /** A button beside the others along the top (the tennis or boxing board's Bell now, Next match, Hide). */
    TextView secondary(int icon, String words, Runnable r) {
        LinearLayout line = top.getChildCount() > 0 && top.getChildAt(top.getChildCount() - 1) instanceof HorizontalScrollView
                ? (LinearLayout) ((HorizontalScrollView) top.getChildAt(top.getChildCount() - 1)).getChildAt(0) : null;
        if (line == null) {
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            line = new LinearLayout(c);
            hs.addView(line);
            top.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        }
        TextView b = button(icon, words, false);
        b.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
        lp.rightMargin = Ui.dp(c, 8);
        lp.bottomMargin = Ui.dp(c, 4);
        line.addView(b, lp);
        return b;
    }

    private TextView button(int icon, String words, boolean white) {
        TextView b = TvLook.text(c, words, 15, white ? TvLook.INK : TvLook.L1, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 20), 0);
        b.setBackground(white ? TvLook.white(c, 26) : TvLook.glass(c, 24));
        b.setSingleLine(true);
        if (icon != 0) {
            Drawable dr = c.getDrawable(icon);
            if (dr != null) {
                dr = dr.mutate();
                int s = Ui.dp(c, 20);
                dr.setBounds(0, 0, s, s);
                dr.setTint(white ? TvLook.INK : TvLook.L1);
                b.setCompoundDrawables(dr, null, null, null);
                b.setCompoundDrawablePadding(Ui.dp(c, 10));
            }
        }
        return b;
    }

    // ---- sections -------------------------------------------------------------------------------------------------

    /** A section: its header (and a short line under it, or none), then its card of rows; a pill to jump to it. */
    LinearLayout section(String title, String line) {
        TextView h = TvLook.text(c, title.toUpperCase(Locale.UK), 12, TvLook.L2, true);
        h.setLetterSpacing(0.1f);
        h.setPadding(Ui.dp(c, 4), Ui.dp(c, 18), 0, Ui.dp(c, line == null ? 8 : 4));
        body.addView(h);
        if (line != null && !line.isEmpty()) {
            TextView n = TvLook.text(c, line, 12.5f, TvLook.L3, false);
            n.setLineSpacing(0, 1.15f);
            n.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 8));
            body.addView(n);
        }
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(new TvLook.Pane(c, 16, 0x1AFFFFFF, 0x0FFFFFFF, true));
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        // Its pill along the top: tapped, the section comes to the top of the panel.
        TextView pill = TvLook.text(c, title, 13, TvLook.L1, true);
        pill.setGravity(Gravity.CENTER);
        pill.setMinHeight(Ui.dp(c, 44));
        TvLook.pill(c, pill, false);
        pill.setPadding(Ui.dp(c, 14), pill.getPaddingTop(), Ui.dp(c, 14), pill.getPaddingBottom());
        pill.setContentDescription(c.getString(R.string.live_menu_jump, title));
        pill.setOnClickListener(v -> scroll.smoothScrollTo(0, Math.max(0, h.getTop() - Ui.dp(c, 6))));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 6);
        jumps.addView(pill, lp);
        return card;
    }

    /** A row in a card (a hairline over it after the first): its words on the left, what it has on the right. */
    private LinearLayout row(LinearLayout card) {
        if (card.getChildCount() > 0) {
            View hair = new View(c);
            hair.setBackgroundColor(0x14FFFFFF);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 0.75f)));
            hp.leftMargin = Ui.dp(c, 16);
            card.addView(hair, hp);
        }
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(c, ROW_DP));
        r.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        card.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    /** A row's words: what it is, and under it where it holds (this channel, every channel) or a few words more. */
    private LinearLayout words(LinearLayout row, String label, String where) {
        LinearLayout w = new LinearLayout(c);
        w.setOrientation(LinearLayout.VERTICAL);
        TextView t = TvLook.text(c, label, 15, TvLook.L1, false);
        t.setLineSpacing(0, 1.1f);
        w.addView(t);
        if (where != null && !where.isEmpty()) {
            TextView s = TvLook.text(c, where, 12, TvLook.L3, false);
            s.setPadding(0, Ui.dp(c, 3), 0, 0);
            s.setLineSpacing(0, 1.1f);
            w.addView(s);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = Ui.dp(c, 10);
        row.addView(w, lp);
        return w;
    }

    /** On or off: a switch (the whole row turns it). */
    MaterialSwitch toggle(LinearLayout card, String label, String where, boolean on, Consumer<Boolean> set) {
        LinearLayout r = row(card);
        words(r, label, where);
        MaterialSwitch sw = new MaterialSwitch(c);
        // 07's chosen: white, its thumb dark (as the pill chosen is white with dark writing); off, glass.
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setTrackTintList(new ColorStateList(states, new int[]{TvLook.L1, 0x26FFFFFF}));
        sw.setThumbTintList(new ColorStateList(states, new int[]{TvLook.INK, TvLook.L2}));
        sw.setTrackDecorationTintList(new ColorStateList(states, new int[]{0x00FFFFFF, 0x4DFFFFFF}));
        sw.setChecked(on);
        sw.setContentDescription(label);
        sw.setOnCheckedChangeListener((b, v) -> set.accept(v));
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        // The screen reader has the switch itself (with its name); the row is the finger's bigger target.
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.setBackground(TvLook.touch(c, null, 0));
        return sw;
    }

    /**
     * Less and more: round − and + buttons (steps of 1, or of 5 too for the timing's ±5 and ±1) round what it is now
     * (value: null for none); step gets each tap's steps, then the value is shown again.
     */
    TextView stepper(LinearLayout card, String label, String where, Supplier<String> value, String less, String more, int[] steps,
                     IntConsumer step) {
        LinearLayout r = row(card);
        boolean wide = steps.length > 2;
        LinearLayout holder = r;
        if (wide) {
            // The words over a line of their own for the buttons.
            r.setOrientation(LinearLayout.VERTICAL);
            r.setGravity(Gravity.START);
            LinearLayout top = new LinearLayout(c);
            r.addView(top, new LinearLayout.LayoutParams(-1, -2));
            words(top, label, where);
            holder = new LinearLayout(c);
            holder.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
            hp.topMargin = Ui.dp(c, 8);
            r.addView(holder, hp);
        } else {
            words(r, label, where);
        }
        TextView shown = value == null ? null : TvLook.text(c, value.get(), 14, TvLook.L1, true);
        int half = 0;
        for (int i = 0; i < steps.length; i++) if (steps[i] < 0) half = i + 1;
        for (int i = 0; i < steps.length; i++) {
            if (i == half && shown != null) addValue(holder, shown, wide);
            int s = steps[i];
            String glyph = Math.abs(s) == 1 && !wide ? (s < 0 ? "−" : "+") : (s < 0 ? "−" : "+") + Math.abs(s);
            TextView b = TvLook.text(c, glyph, Math.abs(s) == 1 && !wide ? 22 : 15, TvLook.L1, true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(TvLook.glass(c, 24));
            b.setContentDescription((s < 0 ? less : more) + (wide ? " " + Math.abs(s) : ""));
            b.setOnClickListener(v -> {
                step.accept(s);
                if (shown != null) shown.setText(value.get());
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wide ? 0 : Ui.dp(c, 48), Ui.dp(c, 48), wide ? 1f : 0f);
            lp.leftMargin = Ui.dp(c, i == 0 && wide ? 0 : 6);
            holder.addView(b, lp);
        }
        if (half >= steps.length && shown != null) addValue(holder, shown, wide);
        return shown;
    }

    private void addValue(LinearLayout holder, TextView shown, boolean wide) {
        shown.setGravity(Gravity.CENTER);
        shown.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(wide ? 0 : -2, -2, wide ? 1.4f : 0f);
        lp.leftMargin = Ui.dp(c, 6);
        if (!wide) shown.setMinWidth(Ui.dp(c, 64));
        holder.addView(shown, lp);
    }

    /** One of a few: the words over a line of pills, the one on white. */
    Pills choice(LinearLayout card, String label, String where, String[] keys, String[] words, String now, Consumer<String> set) {
        LinearLayout r = row(card);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.START);
        LinearLayout head = new LinearLayout(c);
        r.addView(head, new LinearLayout.LayoutParams(-1, -2));
        words(head, label, where);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(line);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = Ui.dp(c, 4);
        r.addView(hs, hp);
        Pills p = new Pills(c, line, label);
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            p.add(words[i], k.equals(now), () -> set.accept(k), -1);
        }
        return p;
    }

    /** A line of pills, one chosen. */
    static final class Pills {
        final LinearLayout line;
        final List<TextView> all = new ArrayList<>();
        private final Context c;
        private final String label;

        Pills(Context c, LinearLayout line, String label) {
            this.c = c;
            this.line = line;
            this.label = label;
        }

        /** One more pill (at: its place among them, -1 for last). */
        TextView add(String words, boolean on, Runnable pick, int at) {
            TextView t = TvLook.text(c, words, 13.5f, TvLook.L1, true);
            t.setGravity(Gravity.CENTER);
            // One line, then its height (a single line set after would take the least height back to a line's).
            t.setSingleLine(true);
            t.setMinHeight(Ui.dp(c, 48));
            TvLook.pill(c, t, on);
            t.setPadding(Ui.dp(c, 16), t.getPaddingTop(), Ui.dp(c, 16), t.getPaddingBottom());
            t.setContentDescription(label + ": " + words);
            t.setOnClickListener(v -> {
                pick.run();
                choose(t);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 6);
            if (at < 0 || at > line.getChildCount()) {
                line.addView(t, lp);
                all.add(t);
            } else {
                line.addView(t, at, lp);
                all.add(Math.min(at, all.size()), t);
            }
            return t;
        }

        /** This one chosen, the rest not. */
        void choose(TextView on) {
            for (TextView x : all) {
                int l = x.getPaddingLeft(), r = x.getPaddingRight();
                TvLook.pill(c, x, x == on);
                x.setPadding(l, x.getPaddingTop(), r, x.getPaddingBottom());
            }
        }
    }

    /** One look to pick, as a picture: drawn as it would stand over the picture, its name under it. */
    static final class Pic {
        final View picture;
        final String name;
        final boolean on;
        final Runnable pick;
        final int w, h;

        Pic(View picture, int wDp, int hDp, String name, boolean on, Runnable pick) {
            this.picture = picture;
            this.name = name;
            this.on = on;
            this.pick = pick;
            this.w = wDp;
            this.h = hDp;
        }
    }

    /** Pictures to pick from (looks), in a line that scrolls sideways, the one on ringed; the tiles, to unring them. */
    List<View> pictures(LinearLayout card, String label, String where, List<Pic> pics) {
        LinearLayout r = row(card);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setGravity(Gravity.START);
        r.setPadding(r.getPaddingLeft(), r.getPaddingTop(), 0, r.getPaddingBottom());
        LinearLayout head = new LinearLayout(c);
        r.addView(head, new LinearLayout.LayoutParams(-1, -2));
        words(head, label, where);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = new LinearLayout(c);
        line.setBaselineAligned(false);
        hs.addView(line);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = Ui.dp(c, 8);
        r.addView(hs, hp);
        List<View> tiles = new ArrayList<>();
        View chosen = null;
        // Every tile the same size: the tallest look's (Stacked, Card), the others in the middle of theirs.
        int tallest = 0, widest = 0;
        for (Pic p : pics) {
            tallest = Math.max(tallest, p.h);
            widest = Math.max(widest, p.w);
        }
        for (Pic p : pics) {
            LinearLayout one = tile(c, p.name, p.on);
            android.widget.FrameLayout stage = new android.widget.FrameLayout(c);
            stage.setBackground(stage(c));
            stage.addView(p.picture, new android.widget.FrameLayout.LayoutParams(Ui.dp(c, p.w), Ui.dp(c, p.h), Gravity.CENTER));
            one.addView(stage, 0, new LinearLayout.LayoutParams(Ui.dp(c, Math.max(104, widest + 12)), Ui.dp(c, Math.max(50, tallest + 14))));
            one.setOnClickListener(v -> {
                for (View x : tiles) x.setSelected(x == v);
                p.pick.run();
            });
            tiles.add(one);
            if (p.on) chosen = one;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            line.addView(one, lp);
        }
        // The one on in sight as the menu opens.
        View in = chosen;
        if (in != null) hs.post(() -> hs.scrollTo(Math.max(0, in.getLeft() - Ui.dp(c, 24)), 0));
        return tiles;
    }

    /** A picture's tile: ringed when chosen, its name under it. */
    static LinearLayout tile(Context c, String name, boolean on) {
        LinearLayout one = new LinearLayout(c);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(c, 5);
        one.setPadding(p, p, p, Ui.dp(c, 6));
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_selected}, Ui.ring(c, 0x1FFFFFFF, TvLook.L1, 2, 14));
        bg.addState(new int[]{}, Ui.round(c, 0x0FFFFFFF, 14));
        one.setBackground(bg);
        one.setSelected(on);
        TextView t = TvLook.text(c, name, 12, TvLook.L2, false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setMaxWidth(Ui.dp(c, 132));
        t.setPadding(0, Ui.dp(c, 6), 0, 0);
        one.addView(t);
        one.setContentDescription(name);
        return one;
    }

    /** A stand-in for the picture under a look: a pitch's green, as Settings had them. */
    static Drawable stage(Context c) {
        android.graphics.drawable.GradientDrawable pitch = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2F7A35, 0xFF4C9A4A, 0xFF2A6A2F});
        pitch.setCornerRadius(Ui.dp(c, 9));
        return pitch;
    }

    /** Something to do (it opens a screen of its own, or puts the menu away to do it): its icon, its words, a chevron. */
    LinearLayout action(LinearLayout card, int icon, String label, String where, Runnable r) {
        LinearLayout row = row(card);
        if (icon != 0) {
            ImageView i = new ImageView(c);
            i.setImageResource(icon);
            i.setImageTintList(ColorStateList.valueOf(TvLook.L1));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22));
            ip.rightMargin = Ui.dp(c, 14);
            row.addView(i, ip);
        }
        words(row, label, where);
        TextView go = TvLook.text(c, "›", 22, TvLook.L3, false);
        row.addView(go);
        row.setBackground(TvLook.touch(c, null, 0));
        row.setContentDescription(where == null || where.isEmpty() ? label : label + ". " + where);
        row.setOnClickListener(v -> r.run());
        return row;
    }

    /** A wide white button in a card ("It's just happened"): the one to find at a glance. */
    TextView big(LinearLayout card, String words, Runnable r) {
        LinearLayout row = row(card);
        TextView b = button(0, words, true);
        b.setOnClickListener(v -> r.run());
        row.addView(b, new LinearLayout.LayoutParams(0, Ui.dp(c, 50), 1f));
        return b;
    }

    /** A few words in a card (how it stands now). */
    TextView note(LinearLayout card, String words) {
        LinearLayout row = row(card);
        row.setMinimumHeight(Ui.dp(c, 40));
        TextView t = TvLook.text(c, words, 12.5f, TvLook.L3, false);
        t.setLineSpacing(0, 1.15f);
        row.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    /** More rows under one that opens and shuts them (Stats beside the board, Ask AI's key): the box they go in. */
    LinearLayout more(LinearLayout card, String label, String where) {
        LinearLayout row = row(card);
        words(row, label, where);
        TextView arrow = TvLook.text(c, "▾", 16, TvLook.L2, false);
        row.addView(arrow);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(View.GONE);
        card.addView(box, new LinearLayout.LayoutParams(-1, -2));
        row.setBackground(TvLook.touch(c, null, 0));
        row.setContentDescription(label);
        row.setOnClickListener(v -> {
            boolean open = box.getVisibility() != View.VISIBLE;
            box.setVisibility(open ? View.VISIBLE : View.GONE);
            arrow.setText(open ? "▴" : "▾");
        });
        return box;
    }

    /** Something of its own in a card (the My scoreboards tiles, a board to try), in a row's padding. */
    void custom(LinearLayout card, View v) {
        LinearLayout row = row(card);
        row.setPadding(row.getPaddingLeft(), Ui.dp(c, 4), 0, Ui.dp(c, 8));
        row.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }
}
