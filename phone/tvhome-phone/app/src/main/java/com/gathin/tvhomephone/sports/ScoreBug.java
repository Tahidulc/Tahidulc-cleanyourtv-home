package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;

/**
 * The TV's scores box as a broadcast bug, at phone width: the competition's logo on its navy block, each club's crest
 * on its own white tile, and the blue middle with the score (or the kick-off time) large and under it the clock live
 * in minutes and seconds (GameClock, going on second by second between reads, the minutes added beside it in added
 * time), or HT, FT, KICK-OFF. The same proportions as the TV's 414 by 50 box (52, 110, 142, 110).
 */
public final class ScoreBug extends LinearLayout {
    /** How many times as wide as high it is drawn: the TV's box, 414 by 50. */
    public static final float RATIO = 414f / 50f;

    private final ImageView comp, homeCrest, awayCrest;
    private final TextView compTag, plate, when, added;
    private final View dot;
    private Game game;

    /** Once a second while the game runs and the box is on the screen: the clock moved on. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (game == null) return;
            clock(game);
            if (GameClock.show(game, System.currentTimeMillis()) != null && isAttachedToWindow()) postDelayed(this, 1000);
        }
    };

    public ScoreBug(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setClipToOutline(true);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
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
        dot = new View(c);
        dot.setBackground(Ui.fill(c, c.getColor(R.color.live_green), 4));
        LayoutParams dp = new LayoutParams(Ui.dp(c, 7), Ui.dp(c, 7));
        dp.rightMargin = Ui.dp(c, 5);
        under.addView(dot, dp);
        when = Ui.text(c, "", 11, 0xFFDFE5FF, true);
        when.setLetterSpacing(0.06f);
        // The seconds don't make the line jump about as they change.
        when.setFontFeatureSettings("tnum");
        under.addView(when);
        // The minutes added, in added time: "+2" on a red chip beside the clock.
        added = Ui.text(c, "", 10, 0xFFFFFFFF, true);
        added.setBackground(Ui.fill(c, 0xFFE0213A, 3));
        added.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        added.setIncludeFontPadding(false);
        LayoutParams ap = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ap.leftMargin = Ui.dp(c, 5);
        under.addView(added, ap);
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
        game = g;
        // The competition's logo for dark grounds (the scoreboard's, else ESPN's by its id); its tag when none loads.
        String logo = Competitions.logo(g);
        String tag = g.league == null ? "" : g.league;
        comp.setContentDescription(g.leagueName == null || g.leagueName.isEmpty() ? tag : g.leagueName);
        if (logo != null) {
            compTag.setText("");
            Ui.load(comp, logo, null, () -> compTag.setText(tag));
        } else {
            Ui.clear(comp);
            compTag.setText(tag);
        }
        Ui.load(homeCrest, g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId));
        Ui.load(awayCrest, g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId));
        homeCrest.setContentDescription(g.homeName);
        awayCrest.setContentDescription(g.awayName);
        words(g);
        removeCallbacks(tick);
        if (GameClock.show(g, System.currentTimeMillis()) != null && isAttachedToWindow()) postDelayed(tick, 1000);
    }

    /** The score (or kick-off time) and the line under it. */
    private void words(Game g) {
        if (g.state == Game.PRE) {
            plate.setText(java.time.Instant.ofEpochMilli(g.kickoff).atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            when.setText(today(g.kickoff) ? "KICK-OFF" : java.time.Instant.ofEpochMilli(g.kickoff)
                    .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")).toUpperCase(java.util.Locale.ROOT));
            dot.setVisibility(GONE);
            added.setVisibility(GONE);
        } else {
            plate.setText(g.score());
            dot.setVisibility(g.state == Game.LIVE ? VISIBLE : GONE);
            clock(g);
        }
        setContentDescription(g.homeName + " " + (g.state == Game.PRE ? "v" : g.score()) + " " + g.awayName + ", " + when.getText()
                + (added.getVisibility() == VISIBLE ? " " + added.getText() : ""));
    }

    /** The line under the score while it is on: the clock in minutes and seconds, or HT, FT, or ESPN's own words. */
    private void clock(Game g) {
        if (g.state == Game.PRE) return;
        String[] live = GameClock.show(g, System.currentTimeMillis());
        if (live != null) {
            when.setText(live[0]);
            added.setText(live[1]);
            added.setVisibility(live[1].isEmpty() ? GONE : VISIBLE);
        } else {
            when.setText(g.state == Game.POST ? "FT" : g.halftime ? "HT" : g.detail);
            added.setVisibility(GONE);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (game != null && GameClock.show(game, System.currentTimeMillis()) != null) {
            removeCallbacks(tick);
            post(tick);
        }
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    /**
     * The box drawn as a picture w pixels wide, for a notification: the competition's logo given, the crests fetched
     * here (off the main thread), the clock as it stands.
     */
    public static Bitmap picture(Context c, Game g, Bitmap logo, int w) {
        ScoreBug b = new ScoreBug(c);
        b.game = g;
        String tag = g.league == null ? "" : g.league;
        if (logo != null) b.comp.setImageBitmap(logo);
        else b.compTag.setText(tag);
        int crest = Ui.dp(c, 44);
        Bitmap hc = NotifCard.logo(c, g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId), crest, crest);
        Bitmap ac = NotifCard.logo(c, g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId), crest, crest);
        if (hc != null) b.homeCrest.setImageBitmap(hc);
        if (ac != null) b.awayCrest.setImageBitmap(ac);
        b.words(g);
        int h0 = Ui.dp(c, 64), w0 = Math.round(h0 * RATIO);
        b.measure(MeasureSpec.makeMeasureSpec(w0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h0, MeasureSpec.EXACTLY));
        b.layout(0, 0, w0, h0);
        float s = w / (float) w0;
        Bitmap out = Bitmap.createBitmap(w, Math.max(1, Math.round(h0 * s)), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.scale(s, s);
        // Its rounded corners (a picture has no outline to clip to).
        android.graphics.Path round = new android.graphics.Path();
        float r = Ui.dp(c, 6);
        round.addRoundRect(0, 0, w0, h0, r, r, android.graphics.Path.Direction.CW);
        cv.clipPath(round);
        b.draw(cv);
        return out;
    }

    private static boolean today(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                .equals(java.time.LocalDate.now());
    }
}
