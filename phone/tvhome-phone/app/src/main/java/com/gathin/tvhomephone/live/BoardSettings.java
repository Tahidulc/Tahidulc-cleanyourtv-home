package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Settings' "Scoreboard while you watch": the scoreboard over the player's picture, over the channel's own, which
 * alerts drop from it, over other apps, and how far behind the other apps' pictures run.
 */
public final class BoardSettings {
    private BoardSettings() {}

    private interface Flip {
        void set(boolean on);
    }

    public static void add(Activity a, LinearLayout col) {
        Context c = a;
        col.addView(com.gathin.tvhomephone.sports.Ui.heading(c, c.getString(R.string.live4_settings_title), c.getColor(R.color.text_soft)));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live4_settings_line), 13, c.getColor(R.color.text_dim), false));
        row(c, col, c.getString(R.string.live4_board_on), ScoreBoard.on(c), on -> ScoreBoard.setOn(c, on));
        row(c, col, c.getString(R.string.live4_board_cover), ScoreBoard.cover(c), on -> ScoreBoard.setCover(c, on));
        row(c, col, c.getString(R.string.live4_alerts_football), ScoreBoard.shows(c, ScoreBoard.FOOTBALL), on -> ScoreBoard.setShows(c, ScoreBoard.FOOTBALL, on));
        row(c, col, c.getString(R.string.live4_alerts_sport), ScoreBoard.shows(c, ScoreBoard.SPORT), on -> ScoreBoard.setShows(c, ScoreBoard.SPORT, on));
        row(c, col, c.getString(R.string.live4_alerts_news), ScoreBoard.shows(c, ScoreBoard.NEWS), on -> ScoreBoard.setShows(c, ScoreBoard.NEWS, on));
        MaterialSwitch over = row(c, col, c.getString(R.string.live4_float_start), FloatService.on(c), on -> {
            if (on != FloatService.on(c)) FloatService.toggle(a);
        });
        // Back on the screen from Android's permission page: the switch says what is so.
        over.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View v) {
                over.setChecked(FloatService.on(c));
            }

            @Override public void onViewDetachedFromWindow(android.view.View v) {}
        });

        LinearLayout delay = com.gathin.tvhomephone.sports.Ui.row(c);
        delay.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        delay.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live4_other_delay), 15, c.getColor(R.color.text), false),
                com.gathin.tvhomephone.sports.Ui.weight(1));
        TextView value = Ui.guideChip(c, c.getString(R.string.live4_seconds, ScoreBoard.otherDelay(c)), false);
        TextView minus = Ui.guideChip(c, "− 5", false), plus = Ui.guideChip(c, "+ 5", false);
        minus.setOnClickListener(v -> {
            ScoreBoard.setOtherDelay(c, ScoreBoard.otherDelay(c) - 5);
            value.setText(c.getString(R.string.live4_seconds, ScoreBoard.otherDelay(c)));
        });
        plus.setOnClickListener(v -> {
            ScoreBoard.setOtherDelay(c, ScoreBoard.otherDelay(c) + 5);
            value.setText(c.getString(R.string.live4_seconds, ScoreBoard.otherDelay(c)));
        });
        for (TextView t : new TextView[]{minus, value, plus}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = Ui.dp(c, 6);
            delay.addView(t, lp);
        }
        col.addView(delay);
    }

    private static MaterialSwitch row(Context c, LinearLayout col, String label, boolean on, Flip flip) {
        LinearLayout r = com.gathin.tvhomephone.sports.Ui.row(c);
        r.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        r.addView(com.gathin.tvhomephone.sports.Ui.text(c, label, 16, c.getColor(R.color.text), false), com.gathin.tvhomephone.sports.Ui.weight(1));
        MaterialSwitch sw = new MaterialSwitch(c);
        sw.setChecked(on);
        sw.setContentDescription(label);
        sw.setOnCheckedChangeListener((b, v) -> flip.set(v));
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        col.addView(r);
        return sw;
    }
}
