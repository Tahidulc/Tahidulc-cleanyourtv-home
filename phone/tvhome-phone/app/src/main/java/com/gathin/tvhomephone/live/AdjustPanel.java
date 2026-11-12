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
 * logo are dragged into place: each one on or off, its size (smaller and bigger), its height alone and its width alone, its look; the
 * board's timing against the channel (read off the channel's own clock, or put later or sooner by hand); keeping them
 * still, and Reset (back to where Homefield puts them) or Done.
 */
final class AdjustPanel {
    private AdjustPanel() {}

    interface Actions {
        void boardShown(boolean on);

        void boardSize(float k);

        void boardHeight(float k);

        void boardWidth(float k);

        void boardStyle(OverlayBug.Style s);

        void logoShown(boolean on);

        void logoSize(float k);

        void logoHeight(float k);

        void logoWidth(float k);

        void logoLook(LogoCover.Look l);

        /** The board's clock and score put later (seconds above 0) or sooner; the timing line as it is now. */
        String later(int seconds);

        void still(boolean on);

        void reset();

        void done();
    }

    /** How much each tap on − or + changes a size. */
    static final float STEP = 1.12f;

    static View build(Context c, Actions a, OverlayBug.Style style, LogoCover.Look look, boolean still, boolean boardOn,
            String timing) {
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

        // The mark over the channel's logo: on or off, its size, its height alone, then its look.
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
        // Its look, each one drawn as it would stand over the picture (on a pitch's green), the one on now ringed: tap one
        // to put it on (the cover on too, if it was off).
        LinearLayout lookRow = row(c, card, "Look");
        List<View> looks = new ArrayList<>();
        for (LogoCover.Look l : LogoCover.Look.values()) {
            // Off is the switch's.
            if (l == LogoCover.Look.OFF) continue;
            View one = lookPicture(c, l, l == look);
            one.setOnClickListener(v -> {
                for (View x : looks) x.setSelected(x == v);
                a.logoLook(l);
                if (!"✓ On".contentEquals(logoSwitch.getText())) logoSwitch.performClick();
            });
            looks.add(one);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            lookRow.addView(one, lp);
        }

        // The board's timing against the channel's own scoreboard.
        LinearLayout time = row(c, card, "Timing");
        TextView said = Ui.text(c, timing, 12, Ui.DIM, false);
        said.setPadding(0, 0, Ui.dp(c, 10), 0);
        time.addView(said);
        for (int secs : new int[]{-5, -1, 1, 5}) {
            String words = (secs > 0 ? "+" : "−") + Math.abs(secs) + " s";
            TextView t = Ui.guideChip(c, words, false);
            t.setContentDescription(secs > 0 ? "Clock and score " + secs + " seconds later" : "Clock and score " + -secs + " seconds sooner");
            t.setOnClickListener(v -> said.setText(a.later(secs)));
            chip(time, t);
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

    private static void chip(LinearLayout row, TextView t) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(row.getContext(), 8);
        row.addView(t, lp);
    }

    /**
     * One look as a picture: the cover in it on a stand-in for the picture (a pitch's green), as Settings has them, with
     * the picture tag, the style, the sport and the name as chosen there, and its name under it; ringed when chosen.
     */
    private static View lookPicture(Context c, LogoCover.Look l, boolean on) {
        LinearLayout one = new LinearLayout(c);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(c, 6);
        one.setPadding(p, p, p, Ui.dp(c, 5));
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_selected}, Ui.ring(c, 0x1FFFFFFF, 0xFFFFFFFF, 2, 12));
        bg.addState(new int[]{}, Ui.round(c, 0x14FFFFFF, 12));
        one.setBackground(bg);
        one.setSelected(on);
        one.setContentDescription(l.label);
        android.widget.FrameLayout stage = new android.widget.FrameLayout(c);
        GradientDrawable pitch = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2F7A35, 0xFF4C9A4A, 0xFF2A6A2F});
        pitch.setCornerRadius(Ui.dp(c, 8));
        stage.setBackground(pitch);
        LogoCover lc = new LogoCover(c);
        lc.look(l);
        ChannelMark.Tag tg = ScoreBoard.logoTag(c);
        lc.tag(tg == ChannelMark.Tag.AUTO ? "UHD" : tg.shown);
        lc.live(false);
        if (l.own()) {
            lc.style(ScoreBoard.logoStyle(c));
            lc.what("premier league");
            lc.nameOnly(ScoreBoard.logoNameOnly(c));
            lc.sub("main event");
        }
        if (l == LogoCover.Look.COMPETITION) lc.competition(null, "Premier League");
        boolean tall = l.channel() || l.broadcast() || l == LogoCover.Look.COMPETITION || l.own();
        stage.addView(lc, new android.widget.FrameLayout.LayoutParams(Ui.dp(c, 96), Ui.dp(c, tall ? 44 : 32), Gravity.CENTER));
        one.addView(stage, new LinearLayout.LayoutParams(Ui.dp(c, 112), Ui.dp(c, 58)));
        TextView name = Ui.text(c, l.label, 11, 0xFFFFFFFF, false);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(c, 5), 0, 0);
        one.addView(name, new LinearLayout.LayoutParams(Ui.dp(c, 112), -2));
        return one;
    }
}
