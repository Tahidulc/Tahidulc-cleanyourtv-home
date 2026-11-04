package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;

/**
 * The TV's scores box as a broadcast bug, at phone width: the competition's block in navy, each club's crest on its
 * own white tile, and the blue middle with the score (or the kick-off time) large and the clock, HT, FT or KICK-OFF
 * under it. The same proportions as the TV's 414 by 50 box (52, 110, 142, 110).
 */
public final class ScoreBug extends LinearLayout {
    private final ImageView comp, homeCrest, awayCrest;
    private final TextView compTag, plate, when;
    private final android.view.View dot;

    public ScoreBug(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setClipToOutline(true);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(android.view.View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(c, 6));
            }
        });
        int h = Ui.dp(c, 64);
        FrameLayout compBox = new FrameLayout(c);
        compBox.setBackground(Ui.vertical(c.getColor(R.color.bug_navy_top), c.getColor(R.color.bug_navy_bottom)));
        comp = new ImageView(c);
        comp.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.dp(c, 9);
        comp.setPadding(pad, pad, pad, pad);
        compBox.addView(comp, new FrameLayout.LayoutParams(-1, -1));
        compTag = Ui.text(c, "", 10, c.getColor(R.color.text), true);
        compTag.setGravity(Gravity.CENTER);
        compBox.addView(compTag, new FrameLayout.LayoutParams(-1, -1));
        addView(compBox, new LayoutParams(0, h, 52));
        homeCrest = crestTile(c, h, 110);
        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.setBackground(Ui.vertical(c.getColor(R.color.bug_blue_top), c.getColor(R.color.bug_blue_bottom)));
        plate = Ui.text(c, "", 26, c.getColor(R.color.text), true);
        plate.setGravity(Gravity.CENTER);
        plate.setIncludeFontPadding(false);
        mid.addView(plate);
        LinearLayout under = Ui.row(c);
        under.setGravity(Gravity.CENTER);
        dot = new android.view.View(c);
        dot.setBackground(Ui.fill(c, c.getColor(R.color.live_green), 4));
        LayoutParams dp = new LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7));
        dp.rightMargin = Ui.dp(c, 5);
        under.addView(dot, dp);
        when = Ui.text(c, "", 11, 0xFFDFE5FF, true);
        when.setLetterSpacing(0.06f);
        under.addView(when);
        mid.addView(under);
        addView(mid, new LayoutParams(0, h, 142));
        awayCrest = crestTile(c, h, 110);
    }

    private ImageView crestTile(Context c, int h, float weight) {
        FrameLayout tile = new FrameLayout(c);
        tile.setBackgroundColor(0xFFFFFFFF);
        ImageView v = new ImageView(c);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = Ui.dp(c, 44);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(s, s, Gravity.CENTER);
        tile.addView(v, p);
        addView(tile, new LayoutParams(0, h, weight));
        return v;
    }

    public void show(Game g) {
        if (g.leagueLogo != null) {
            compTag.setText("");
            Ui.load(comp, g.leagueLogo);
        } else {
            comp.setImageDrawable(null);
            compTag.setText(g.league);
        }
        Ui.load(homeCrest, g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId));
        Ui.load(awayCrest, g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId));
        homeCrest.setContentDescription(g.homeName);
        awayCrest.setContentDescription(g.awayName);
        if (g.state == Game.PRE) {
            plate.setText(java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            when.setText(today(g.kickoff) ? "KICK-OFF" : java.time.Instant.ofEpochMilli(g.kickoff)
                    .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")).toUpperCase(java.util.Locale.ROOT));
            dot.setVisibility(GONE);
        } else {
            plate.setText(g.score());
            when.setText(g.state == Game.POST ? "FT" : g.halftime ? "HT" : g.detail);
            dot.setVisibility(g.state == Game.LIVE ? VISIBLE : GONE);
        }
        setContentDescription(g.homeName + " " + (g.state == Game.PRE ? "v" : g.score()) + " " + g.awayName + ", " + when.getText());
    }

    private static boolean today(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .equals(java.time.LocalDate.now());
    }
}
