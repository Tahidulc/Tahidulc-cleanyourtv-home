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
        row(c, col, c.getString(R.string.live6_still), ScoreBoard.still(c), on -> ScoreBoard.setStill(c, on));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live6_still_line), 13, c.getColor(R.color.text_dim), false));
        row(c, col, c.getString(R.string.live4_read), ScoreBoard.reads(c), on -> ScoreBoard.setReads(c, on));
        row(c, col, c.getString(R.string.live5_broadcast), ScoreBoard.broadcast(c), on -> ScoreBoard.setBroadcast(c, on));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live5_broadcast_line), 13, c.getColor(R.color.text_dim), false));
        // What the break shows: a chip each, on until tapped off.
        col.addView(sub(c, c.getString(R.string.live5_break_shows)));
        android.widget.HorizontalScrollView kinds = new android.widget.HorizontalScrollView(c);
        kinds.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(c);
        kinds.addView(chips);
        for (String[] k : ScoreBoard.BREAK_KINDS) {
            TextView chip = com.gathin.tvhomephone.sports.Ui.text(c, k[1], 14, 0, true);
            chip.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            Runnable paint = () -> {
                boolean on = ScoreBoard.breakShows(c, k[0]);
                chip.setBackground(com.gathin.tvhomephone.sports.Ui.fill(c, on ? 0xFFFFFFFF : 0x1FFFFFFF, 18));
                chip.setTextColor(on ? 0xFF0A0D33 : c.getColor(R.color.text_dim));
                chip.setContentDescription(k[1] + (on ? ", shown in the break" : ", not shown"));
            };
            paint.run();
            chip.setOnClickListener(v -> {
                ScoreBoard.setBreakShows(c, k[0], !ScoreBoard.breakShows(c, k[0]));
                paint.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 8);
            chips.addView(chip, lp);
        }
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(-1, -2);
        kp.bottomMargin = Ui.dp(c, 8);
        col.addView(kinds, kp);
        row(c, col, c.getString(R.string.live5_burn), BurnGuard.on(c), on -> BurnGuard.setOn(c, on));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live5_burn_line), 13, c.getColor(R.color.text_dim), false));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live4_read_line), 13, c.getColor(R.color.text_dim), false));

        // Its look: each drawn as it would be, tap one.
        col.addView(sub(c, c.getString(R.string.live4_board_look)));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout styles = new LinearLayout(c);
        hs.addView(styles);
        java.util.List<android.view.View> frames = new java.util.ArrayList<>();
        for (OverlayBug.Style st : OverlayBug.Style.values()) {
            LinearLayout one = choice(c, st.label, ScoreBoard.style(c) == st);
            OverlayBug b = new OverlayBug(c);
            b.style(st);
            b.set(sample());
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, Ui.dp(c, Math.round(30 * st.tall)));
            one.addView(b, 0, bp);
            one.setOnClickListener(v -> {
                ScoreBoard.setStyle(c, st);
                for (android.view.View f : frames) f.setSelected(f == v);
            });
            frames.add(one);
            styles.addView(one, spaced(c));
        }
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));

        // The channel's logo: Homefield's mark over it, each way drawn, tap one.
        col.addView(sub(c, c.getString(R.string.live4_logo)));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live4_logo_line), 13, c.getColor(R.color.text_dim), false));
        android.widget.HorizontalScrollView hl = new android.widget.HorizontalScrollView(c);
        hl.setHorizontalScrollBarEnabled(false);
        LinearLayout logos = new LinearLayout(c);
        hl.addView(logos);
        java.util.List<android.view.View> lframes = new java.util.ArrayList<>();
        for (LogoCover.Look lk : LogoCover.Look.values()) {
            LinearLayout one = choice(c, lk.label, ScoreBoard.logoCover(c) == lk);
            android.view.View pic;
            if (lk == LogoCover.Look.OFF) {
                TextView t = Ui.text(c, "SPORTS", 13, 0xFFFFFFFF, true);
                t.setGravity(android.view.Gravity.CENTER);
                t.setLetterSpacing(0.1f);
                t.setBackground(Ui.round(c, 0x33FFFFFF, 6));
                pic = t;
            } else {
                LogoCover lc = new LogoCover(c);
                lc.look(lk);
                // The channel and Broadcast looks with the picture tag chosen (a UHD channel's on Auto) and LIVE.
                ChannelMark.Tag tg = ScoreBoard.logoTag(c);
                lc.tag(tg == ChannelMark.Tag.AUTO ? "UHD" : tg.shown);
                lc.live(true);
                pic = lc;
            }
            boolean tall = lk.channel() || lk.broadcast() || lk == LogoCover.Look.COMPETITION || lk.own();
            if (pic instanceof LogoCover && lk.own()) {
                ((LogoCover) pic).style(ScoreBoard.logoStyle(c));
                ((LogoCover) pic).what("premier league");
                ((LogoCover) pic).nameOnly(ScoreBoard.logoNameOnly(c));
            }
            if (lk == LogoCover.Look.COMPETITION && pic instanceof LogoCover) ((LogoCover) pic).competition(null, "Premier League");
            one.addView(pic, 0, new LinearLayout.LayoutParams(Ui.dp(c, tall ? 112 : 104), Ui.dp(c, tall ? 52 : 36)));
            one.setOnClickListener(v -> {
                ScoreBoard.setLogoCover(c, lk);
                for (android.view.View f : lframes) f.setSelected(f == v);
            });
            lframes.add(one);
            logos.addView(one, spaced(c));
        }
        col.addView(hl, new LinearLayout.LayoutParams(-1, -2));
        // The picture tag on it: Auto (from the channel's name), none, or one set.
        col.addView(sub(c, c.getString(R.string.live20_logo_tag)));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live20_logo_tag_line), 13, c.getColor(R.color.text_dim), false));
        android.widget.HorizontalScrollView ht = new android.widget.HorizontalScrollView(c);
        ht.setHorizontalScrollBarEnabled(false);
        LinearLayout tagRow = new LinearLayout(c);
        ht.addView(tagRow);
        java.util.List<android.view.View> tframes = new java.util.ArrayList<>();
        for (ChannelMark.Tag tg : ChannelMark.Tag.values()) {
            LinearLayout one = choice(c, tg == ChannelMark.Tag.AUTO ? "Auto" : tg.label, ScoreBoard.logoTag(c) == tg);
            one.setOnClickListener(v -> {
                ScoreBoard.setLogoTag(c, tg);
                for (android.view.View f : tframes) f.setSelected(f == v);
            });
            tframes.add(one);
            tagRow.addView(one, spaced(c));
        }
        col.addView(ht, new LinearLayout.LayoutParams(-1, -2));
        // The 54 Whitebrook Road styles, each drawn as it would be: tap one.
        col.addView(sub(c, c.getString(R.string.live28_styles)));
        col.addView(com.gathin.tvhomephone.sports.Ui.text(c, c.getString(R.string.live28_styles_line), 13, c.getColor(R.color.text_dim), false));
        android.widget.HorizontalScrollView hb = new android.widget.HorizontalScrollView(c);
        hb.setHorizontalScrollBarEnabled(false);
        LinearLayout brandRow = new LinearLayout(c);
        hb.addView(brandRow);
        java.util.List<android.view.View> bframes = new java.util.ArrayList<>();
        for (int i = 0; i < BrandStyles.COUNT; i++) {
            final int n = i;
            LinearLayout one = choice(c, (i + 1) + ". " + BrandStyles.name(i), ScoreBoard.logoStyle(c) == i);
            LogoCover lc = new LogoCover(c);
            lc.look(LogoCover.Look.STYLES);
            lc.style(i);
            lc.nameOnly(ScoreBoard.logoNameOnly(c));
            lc.tag("HD");
            one.addView(lc, 0, new LinearLayout.LayoutParams(Ui.dp(c, 124), Ui.dp(c, 44)));
            one.setOnClickListener(v -> {
                ScoreBoard.setLogoStyle(c, n);
                if (!ScoreBoard.logoCover(c).own()) ScoreBoard.setLogoCover(c, LogoCover.Look.STYLES);
                for (android.view.View f : bframes) f.setSelected(f == v);
            });
            bframes.add(one);
            brandRow.addView(one, spaced(c));
        }
        col.addView(hb, new LinearLayout.LayoutParams(-1, -2));
        // What the Sports wordmark says is on: from the game, or one chosen.
        col.addView(sub(c, c.getString(R.string.live28_what)));
        android.widget.HorizontalScrollView hw = new android.widget.HorizontalScrollView(c);
        hw.setHorizontalScrollBarEnabled(false);
        LinearLayout whatRow = new LinearLayout(c);
        hw.addView(whatRow);
        java.util.List<android.view.View> wframes = new java.util.ArrayList<>();
        for (String w : SportsMark.WHATS) {
            LinearLayout one = choice(c, SportsMark.label(w), ScoreBoard.logoWhat(c).equals(w));
            one.setOnClickListener(v -> {
                ScoreBoard.setLogoWhat(c, w);
                for (android.view.View f : wframes) f.setSelected(f == v);
            });
            wframes.add(one);
            whatRow.addView(one, spaced(c));
        }
        col.addView(hw, new LinearLayout.LayoutParams(-1, -2));
        row(c, col, c.getString(R.string.live28_name_only), ScoreBoard.logoNameOnly(c), on -> ScoreBoard.setLogoNameOnly(c, on));
        row(c, col, c.getString(R.string.live28_see_through), ScoreBoard.logoSeeThrough(c), on -> ScoreBoard.setLogoSeeThrough(c, on));
        row(c, col, c.getString(R.string.live28_lineups), ScoreBoard.lineupsOn(c), on -> ScoreBoard.setLineupsOn(c, on));
        // The kick-off pop-up and the line-ups before kick-off: each in one of two styles, or off.
        styles(c, col, c.getString(R.string.live33_classic_popup), new String[]{"classic", "strap", "off"},
                new String[]{c.getString(R.string.live35_style_classic), c.getString(R.string.live35_style_strap), c.getString(R.string.live35_style_off)},
                PlayerIntro.popupStyle(c), st -> PlayerIntro.setPopupStyle(c, st));
        styles(c, col, c.getString(R.string.live33_classic_lineups), new String[]{"classic", "stadium", "off"},
                new String[]{c.getString(R.string.live35_style_beam), c.getString(R.string.live35_style_stadium), c.getString(R.string.live35_style_off)},
                PlayerIntro.lineupsStyle(c), st -> PlayerIntro.setLineupsStyle(c, st));
        row(c, col, c.getString(R.string.live34_swish), PlayerIntro.swishOn(c), on -> PlayerIntro.setSwishOn(c, on));
        styles(c, col, c.getString(R.string.live35_goal_sound), com.gathin.broadcast.Sfx.GOALS, com.gathin.broadcast.Sfx.GOAL_NAMES,
                PlayerIntro.goalSound(c), key -> PlayerIntro.setGoalSound(c, key));
        row(c, col, c.getString(R.string.live21_logo_comp), ScoreBoard.logoComp(c), on -> ScoreBoard.setLogoComp(c, on));
        row(c, col, c.getString(R.string.live24_logo_live), ScoreBoard.logoLive(c), on -> ScoreBoard.setLogoLive(c, on));
        row(c, col, c.getString(R.string.live26_fotmob), ScoreBoard.fotmobOn(c), on -> ScoreBoard.setFotmobOn(c, on));
        row(c, col, c.getString(R.string.live26_subs), ScoreBoard.subsOn(c), on -> ScoreBoard.setSubsOn(c, on));
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

    /** A made-up game for the looks' pictures. */
    private static OverlayBug.Look sample() {
        OverlayBug.Look l = new OverlayBug.Look();
        l.home = "HOM";
        l.away = "AWY";
        l.score = "2–1";
        l.clock = "67:23";
        l.live = true;
        l.tag = "LIVE";
        l.homeColour = 0xFFE0213A;
        l.awayColour = 0xFF3D8BFF;
        return l;
    }

    private static TextView sub(Context c, String s) {
        TextView t = Ui.text(c, s.toUpperCase(java.util.Locale.UK), 12, c.getColor(R.color.text_soft), true);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 6));
        return t;
    }

    /** One choice: its picture (added first) over its name, ringed when chosen. */
    /** A heading and a row of choices, one picked (keys[i] shown as words[i]). */
    private static void styles(Context c, LinearLayout col, String title, String[] keys, String[] words, String now,
                               java.util.function.Consumer<String> set) {
        col.addView(sub(c, title));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout line = new LinearLayout(c);
        hs.addView(line);
        java.util.List<android.view.View> frames = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            LinearLayout one = choice(c, words[i], k.equals(now));
            one.setOnClickListener(v -> {
                set.accept(k);
                for (android.view.View f : frames) f.setSelected(f == v);
            });
            frames.add(one);
            line.addView(one, spaced(c));
        }
        col.addView(hs, new LinearLayout.LayoutParams(-1, -2));
    }

    private static LinearLayout choice(Context c, String name, boolean on) {
        LinearLayout one = new LinearLayout(c);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        one.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8));
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_selected}, Ui.ring(c, 0x1FFFFFFF, 0xFFFFFFFF, 2, 14));
        bg.addState(new int[]{}, Ui.round(c, 0x14FFFFFF, 14));
        one.setBackground(bg);
        one.setSelected(on);
        TextView t = Ui.text(c, name, 13, 0xFFFFFFFF, false);
        t.setPadding(0, Ui.dp(c, 8), 0, 0);
        one.addView(t);
        one.setContentDescription(name);
        return one;
    }

    private static LinearLayout.LayoutParams spaced(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 10);
        return lp;
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
