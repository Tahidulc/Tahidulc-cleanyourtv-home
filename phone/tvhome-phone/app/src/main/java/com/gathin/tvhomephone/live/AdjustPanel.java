package com.gathin.tvhomephone.live;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's "Move and resize" panel, along the foot while the scoreboard and Homefield's mark over the channel's
 * logo are dragged into place: each one's size (smaller and bigger), the scoreboard's look and the mark's, keeping
 * them still, and Reset (back to where Homefield puts them) or Done.
 */
final class AdjustPanel {
    private AdjustPanel() {}

    interface Actions {
        void boardSize(float k);

        void boardStyle(OverlayBug.Style s);

        void logoSize(float k);

        void logoLook(LogoCover.Look l);

        void still(boolean on);

        void reset();

        void done();
    }

    /** How much each tap on − or + changes a size. */
    static final float STEP = 1.12f;

    static View build(Context c, Actions a, OverlayBug.Style style, LogoCover.Look look, boolean still) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 14);
        card.setPadding(pad, Ui.dp(c, 12), pad, Ui.dp(c, 12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF20B0F24);
        g.setCornerRadius(Ui.dp(c, 18));
        g.setStroke(Math.max(1, Ui.dp(c, 1)), 0x33FFFFFF);
        card.setBackground(g);
        card.setElevation(Ui.dp(c, 8));
        card.setClickable(true);

        TextView title = Ui.text(c, "Move and resize", 16, Ui.TEXT, true);
        card.addView(title);
        TextView line = Ui.text(c, "Drag the scoreboard and the logo where you want them. Kept for this channel.", 12, Ui.DIM, false);
        line.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 4));
        card.addView(line);

        // The scoreboard: its size, then its look.
        LinearLayout board = row(c, card, "Scoreboard");
        board.addView(step(c, "−", "Scoreboard smaller", () -> a.boardSize(1 / STEP)));
        board.addView(step(c, "+", "Scoreboard bigger", () -> a.boardSize(STEP)));
        List<TextView> styles = new ArrayList<>();
        for (OverlayBug.Style s : OverlayBug.Style.values()) {
            TextView t = Ui.guideChip(c, s.label, s == style);
            t.setOnClickListener(v -> {
                for (TextView x : styles) Ui.styleChip(c, x, x == v);
                a.boardStyle(s);
            });
            styles.add(t);
            chip(board, t);
        }

        // The mark over the channel's logo: its size, then its look.
        LinearLayout logo = row(c, card, "Logo");
        logo.addView(step(c, "−", "Logo smaller", () -> a.logoSize(1 / STEP)));
        logo.addView(step(c, "+", "Logo bigger", () -> a.logoSize(STEP)));
        List<TextView> looks = new ArrayList<>();
        for (LogoCover.Look l : LogoCover.Look.values()) {
            TextView t = Ui.guideChip(c, l.label, l == look);
            t.setOnClickListener(v -> {
                for (TextView x : looks) Ui.styleChip(c, x, x == v);
                a.logoLook(l);
            });
            looks.add(t);
            chip(logo, t);
        }

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
        card.addView(foot, fp);
        return card;
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

    private static void chip(LinearLayout row, TextView t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(row.getContext(), 8);
        row.addView(t, lp);
    }
}
