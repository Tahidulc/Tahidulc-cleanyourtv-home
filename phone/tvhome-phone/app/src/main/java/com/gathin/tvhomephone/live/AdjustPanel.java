package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;


/**
 * The player's "Move and resize" panel, along the foot while the scoreboard and Homefield's mark over the channel's
 * logo are dragged into place: each one on or off, its size (smaller and bigger), its height alone and its width alone;
 * keeping them still, and Reset (back to where Homefield puts them) or Done. Their looks, the timing and the rest are
 * in the board's and the logo's own menus now (a tap on either: BoardMenus), so they aren't here twice.
 *
 * <p>Its settings scroll (upright, on its side, on a tablet), the card never taller than a share of the screen so the
 * board and the logo stay in sight to drag; Keep them still, Reset and Done stay at its foot, always in reach.
 */
final class AdjustPanel {
    private AdjustPanel() {}

    interface Actions {
        void boardShown(boolean on);

        void boardSize(float k);

        void boardHeight(float k);

        void boardWidth(float k);

        void logoShown(boolean on);

        void logoSize(float k);

        void logoHeight(float k);

        void logoWidth(float k);

        void still(boolean on);

        void reset();

        void done();
    }

    /** How much each tap on − or + changes a size. */
    static final float STEP = 1.12f;
    /** The most of the room it is given the card may take: upright, and on its side (the board stays in sight above it). */
    static final float SHARE_UPRIGHT = 0.55f, SHARE_SIDEWAYS = 0.72f;

    /** Pure: the tallest the card may be in room px of height (wide: on its side). */
    static int most(int room, boolean wide) {
        return Math.max(0, Math.round(room * (wide ? SHARE_SIDEWAYS : SHARE_UPRIGHT)));
    }

    /**
     * The card: its settings in a scroll over its foot (Keep them still, Reset, Done), never taller than most() of the room
     * it is given: what does not fit scrolls, the foot stays.
     */
    static final class Card extends LinearLayout {
        final ScrollView scroll;
        final LinearLayout body;
        View foot;

        Card(Context c) {
            super(c);
            setOrientation(VERTICAL);
            scroll = new ScrollView(c) {
                @Override protected void onMeasure(int w, int h) {
                    int cap = bodyMost;
                    if (cap > 0) {
                        int size = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? cap : Math.min(cap, MeasureSpec.getSize(h));
                        h = MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
                    }
                    super.onMeasure(w, h);
                }
            };
            scroll.setFillViewport(false);
            scroll.setVerticalScrollBarEnabled(true);
            scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            body = new LinearLayout(c);
            body.setOrientation(VERTICAL);
            scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
            addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        }

        /** The most the scroll may take this pass. */
        private int bodyMost;

        @Override protected void onMeasure(int w, int h) {
            int room = MeasureSpec.getMode(h) == MeasureSpec.UNSPECIFIED ? getResources().getDisplayMetrics().heightPixels : MeasureSpec.getSize(h);
            int wide = getResources().getDisplayMetrics().widthPixels > getResources().getDisplayMetrics().heightPixels ? 1 : 0;
            int cap = most(room, wide == 1);
            // The foot first (it stays), the settings in what is left.
            int footH = 0;
            if (foot != null) {
                foot.measure(MeasureSpec.makeMeasureSpec(Math.max(0, MeasureSpec.getSize(w) - getPaddingLeft() - getPaddingRight()), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                LinearLayout.LayoutParams fl = (LinearLayout.LayoutParams) foot.getLayoutParams();
                footH = foot.getMeasuredHeight() + (fl == null ? 0 : fl.topMargin + fl.bottomMargin);
            }
            bodyMost = Math.max(Math.round(48 * getResources().getDisplayMetrics().density), cap - footH - getPaddingTop() - getPaddingBottom());
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST));
        }
    }

    static View build(Context c, Actions a, LogoCover.Look look, boolean still, boolean boardOn) {
        Card frame = new Card(c);
        // Everything but the foot goes in the scroll (row() adds to it).
        LinearLayout card = frame.body;
        int pad = Ui.dp(c, 14);
        frame.setPadding(pad, Ui.dp(c, 12), pad, Ui.dp(c, 12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF20B0F24);
        g.setCornerRadius(Ui.dp(c, 18));
        g.setStroke(Math.max(1, Ui.dp(c, 1)), 0x33FFFFFF);
        frame.setBackground(g);
        frame.setElevation(Ui.dp(c, 8));
        frame.setClickable(true);

        TextView title = Ui.text(c, "Move and resize", 16, Ui.TEXT, true);
        card.addView(title);
        TextView line = Ui.text(c, "Drag the scoreboard and the logo where you want them. Kept for this channel. "
                + "Their looks, timing and the rest: tap either one for its menu.", 12, Ui.DIM, false);
        line.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 4));
        card.addView(line);

        // The scoreboard: on or off, its size, its height alone, then its look.
        LinearLayout board = row(c, card, "Scoreboard");
        board.addView(onOff(c, "Scoreboard", boardOn, a::boardShown));
        board.addView(label(c, "Size"));
        board.addView(step(c, "−", "Scoreboard smaller", () -> a.boardSize(1 / STEP)));
        board.addView(step(c, "+", "Scoreboard bigger", () -> a.boardSize(STEP)));
        board.addView(label(c, "Height"));
        board.addView(step(c, "−", "Scoreboard shorter", () -> a.boardHeight(1 / STEP)));
        board.addView(step(c, "+", "Scoreboard taller", () -> a.boardHeight(STEP)));
        board.addView(label(c, "Width"));
        board.addView(step(c, "−", "Scoreboard narrower", () -> a.boardWidth(1 / STEP)));
        board.addView(step(c, "+", "Scoreboard wider", () -> a.boardWidth(STEP)));

        // The mark over the channel's logo: on or off, its size, its height alone, its width alone.
        LinearLayout logo = row(c, card, "Logo");
        TextView logoSwitch = onOff(c, "Logo cover", look != LogoCover.Look.OFF, a::logoShown);
        logo.addView(logoSwitch);
        logo.addView(label(c, "Size"));
        logo.addView(step(c, "−", "Logo smaller", () -> a.logoSize(1 / STEP)));
        logo.addView(step(c, "+", "Logo bigger", () -> a.logoSize(STEP)));
        logo.addView(label(c, "Height"));
        logo.addView(step(c, "−", "Logo shorter", () -> a.logoHeight(1 / STEP)));
        logo.addView(step(c, "+", "Logo taller", () -> a.logoHeight(STEP)));
        logo.addView(label(c, "Width"));
        logo.addView(step(c, "−", "Logo narrower", () -> a.logoWidth(1 / STEP)));
        logo.addView(step(c, "+", "Logo wider", () -> a.logoWidth(STEP)));

        // Keep them still; Reset; Done.
        LinearLayout foot = new LinearLayout(c);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        TextView keep = Ui.guideChip(c, still ? "✓ Keep them still" : "Keep them still", still);
        boolean[] on = {still};
        keep.setOnClickListener(v -> {
            on[0] = !on[0];
            keep.setText(on[0] ? "✓ Keep them still" : "Keep them still");
            Ui.styleChip(c, keep, on[0]);
            a.still(on[0]);
        });
        foot.addView(keep);
        foot.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView reset = Ui.guideChip(c, "Reset", false);
        reset.setContentDescription("Reset: back to where Homefield puts them");
        reset.setOnClickListener(v -> a.reset());
        foot.addView(reset);
        TextView done = Ui.guideChip(c, "Done", true);
        done.setOnClickListener(v -> a.done());
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.leftMargin = Ui.dp(c, 8);
        foot.addView(done, dp);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = Ui.dp(c, 10);
        frame.addView(foot, fp);
        frame.foot = foot;
        return frame;
    }

    /** A line: its name, then (scrolling sideways) what it holds. */
    private static LinearLayout row(Context c, LinearLayout card, String name) {
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(c, name, 13, Ui.TEXT, true);
        line.addView(t, new LinearLayout.LayoutParams(Ui.dp(c, 84), -2));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout holds = new LinearLayout(c);
        holds.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(holds);
        line.addView(hs, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(c, 8);
        card.addView(line, lp);
        return holds;
    }

    /** A switch chip: "✓ On" or "Off", tapped to change. */
    private static TextView onOff(Context c, String what, boolean on, java.util.function.Consumer<Boolean> to) {
        TextView t = Ui.guideChip(c, on ? "✓ On" : "Off", on);
        boolean[] now = {on};
        t.setContentDescription(what + (on ? " on" : " off"));
        t.setOnClickListener(v -> {
            now[0] = !now[0];
            t.setText(now[0] ? "✓ On" : "Off");
            t.setContentDescription(what + (now[0] ? " on" : " off"));
            Ui.styleChip(c, t, now[0]);
            to.accept(now[0]);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 10);
        t.setLayoutParams(lp);
        return t;
    }

    /** A small word before the buttons it names ("Size", "Height"). */
    private static TextView label(Context c, String words) {
        TextView t = Ui.text(c, words, 12, Ui.DIM, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 6);
        lp.leftMargin = Ui.dp(c, 4);
        t.setLayoutParams(lp);
        return t;
    }

    private static TextView step(Context c, String glyph, String label, Runnable r) {
        TextView t = Ui.text(c, glyph, 20, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.touch(c, false, 0x26FFFFFF, 20));
        t.setContentDescription(label);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40));
        lp.rightMargin = Ui.dp(c, 8);
        t.setLayoutParams(lp);
        return t;
    }
}
