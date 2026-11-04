package com.gathin.tvhomephone.live;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Competitions;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.ScoreBug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The full-screen player's info tiles, from the Full-screen TV Info Tiles canvas, over the live picture:
 * <ul>
 * <li>A, Glass ribbon: LIVE and the channel at the top, a frosted ribbon along the foot with the channel's number and
 * logo, what's on now with a progress line and the minutes left, and what's next with its poster and Remind me;</li>
 * <li>B, Card: a card in the corner with a still of the episode, its number, times, description and buttons (Restart,
 * Favourite, Similar channels, Episode info), and the channels either side on the right;</li>
 * <li>D, Match night: the scores box (competition, both crests, the score or kick-off) and the channel's logo at the
 * top; the competition, the fixture, kick-off or the score's story, the buttons, and each club's last five results
 * (before kick-off) or the game so far (goals, possession, shots and on target) at the foot, and the facts in turn
 * under the story (the table, each side's run, earlier meetings, top scorers, the numbers of the game);</li>
 * <li>E, Cinematic: the film's title large over a warm fade, the year, genres, rating and certificate, its description,
 * when it ends, and an Up next card.</li>
 * </ul>
 * Each is laid out across the picture on its side (wide) or stacked to fit the width upright. Built in code; the
 * pictures load as they come (Glide).
 */
final class InfoTile {
    private InfoTile() {}

    /** What the tile's buttons do (the player's own actions). */
    interface Actions {
        void restart();

        void favourite();

        boolean isFavourite();

        void similar();

        void info();

        /** Sets or clears a reminder for the next programme; true when one is set now. */
        boolean remind(TileData.Info i);

        boolean reminded(TileData.Info i);

        void step(int by);

        /** The channel number (or name) by steps either side, "" when none. */
        String neighbour(int by);

        void matchCentre(Game g);

        /** The other channels showing the game (AlsoShowing). */
        void alsoShowing(Game g);
    }

    private static final int WHITE = 0xFFFFFFFF, INK = 0xFF10123A;

    /** The tile for this style (never PANEL or AUTO: the caller picks), wide across the picture or stacked. */
    /** The tile's size, chosen in the player's options: Small (the default, to see the picture), Medium or Large. */
    static final String SIZE = "tile_size";
    static final String[] SIZES = {"SMALL", "MEDIUM", "LARGE"};

    /** How much of its full size the tile is drawn at, for the size chosen. */
    static float size(Context c) {
        String s = c.getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE).getString(SIZE, "SMALL");
        return sizeFactor(s);
    }

    static float sizeFactor(String size) {
        return "LARGE".equals(size) ? 1f : "MEDIUM".equals(size) ? 0.84f : 0.68f;
    }

    static View build(Context c, TileData.Info i, TileKind.Style style, boolean wide, Actions a, boolean animate) {
        Kit k = new Kit(c, wide);
        switch (style) {
            case CARD: return card(k, i, a, animate);
            case MATCH: return match(k, i, a, animate);
            case CINEMA: return cinema(k, i, a, animate);
            default: return ribbon(k, i, a, animate);
        }
    }

    // ---- A: Glass ribbon -------------------------------------------------------------------------------------------

    private static View ribbon(Kit k, TileData.Info i, Actions a, boolean animate) {
        Context c = k.c;
        FrameLayout root = new FrameLayout(c);
        root.addView(new Fade(c, true, new int[]{0x0004050E, 0xBF04050E}, null), k.foot(k.wide ? 190 : 260));
        root.addView(new Fade(c, true, new int[]{0x8C04050E, 0x0004050E}, null), new FrameLayout.LayoutParams(-1, k.dp(72), Gravity.TOP));

        // LIVE, the channel's logo, number and name, top left.
        LinearLayout head = k.row();
        if (i.onNow) head.addView(k.livePill());
        head.addView(k.logo(i, 46, 28), k.margins(10, 0, 0, 0, 46, 28));
        TextView ch = k.text(numberAndName(i), 13, 0xFFE4E6F5, 600);
        ch.setPadding(k.dp(8), 0, 0, 0);
        head.addView(ch);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        hp.leftMargin = k.dp(16);
        hp.topMargin = k.dp(k.topGap(18));
        root.addView(head, hp);

        LinearLayout ribbon = new LinearLayout(c);
        k.glass(ribbon, 0xA60C0E24, 20);
        ribbon.setClickable(true);
        long now = System.currentTimeMillis();
        if (k.wide) {
            ribbon.setOrientation(LinearLayout.HORIZONTAL);
            ribbon.setPadding(k.dp(16), k.dp(14), k.dp(16), k.dp(14));
            // The channel: its number large, its logo, the picture's marks.
            LinearLayout first = k.column();
            first.setGravity(Gravity.CENTER_VERTICAL);
            if (i.number > 0) first.addView(k.text(String.valueOf(i.number), 28, WHITE, 800));
            first.addView(k.logo(i, 64, 30), k.margins(0, 4, 0, 0, 64, 30));
            View marks = k.badges(i);
            if (marks != null) first.addView(marks, k.margins(0, 6, 0, 0, -2, -2));
            ribbon.addView(first, new LinearLayout.LayoutParams(k.dp(86), -1));
            ribbon.addView(k.divider(true), k.dividerParams(true));
            ribbon.addView(nowBlock(k, i, now), new LinearLayout.LayoutParams(0, -2, 1f));
            if (!i.nextTitle.isEmpty()) {
                ribbon.addView(k.divider(true), k.dividerParams(true));
                ribbon.addView(nextBlock(k, i, a, true), new LinearLayout.LayoutParams(k.dp(216), -2));
            }
        } else {
            ribbon.setOrientation(LinearLayout.VERTICAL);
            ribbon.setPadding(k.dp(16), k.dp(14), k.dp(16), k.dp(14));
            ribbon.addView(nowBlock(k, i, now));
            if (!i.nextTitle.isEmpty()) {
                ribbon.addView(k.divider(false), k.dividerParams(false));
                ribbon.addView(nextBlock(k, i, a, false));
            }
        }
        FrameLayout.LayoutParams rp = k.footParams();
        rp.leftMargin = rp.rightMargin = k.dp(12);
        rp.bottomMargin = k.dp(26);
        root.addView(k.fitted(ribbon, 26), rp);
        TextView hint = k.text(c.getString(R.string.tile_hint), 11, 0xB3FFFFFF, 400);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        tp.bottomMargin = k.dp(6);
        root.addView(hint, tp);
        if (animate) k.rise(ribbon);
        return root;
    }

    /** NOW: the title, a line on it, and its progress with the times and the minutes left. */
    private static View nowBlock(Kit k, TileData.Info i, long now) {
        Context c = k.c;
        LinearLayout col = k.column();
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(k.kicker(c.getString(R.string.tile_now), 0xFFFF8FA0));
        TextView t = k.text(i.title, k.wide ? 20 : 19, WHITE, 700);
        t.setMaxLines(k.wide ? 1 : 2);
        col.addView(t, k.margins(0, 4, 0, 0, -1, -2));
        String meta = meta(c, i);
        if (!meta.isEmpty()) {
            TextView m = k.text(meta, 13, 0xFFDFE2F2, 400);
            m.setMaxLines(1);
            col.addView(m, k.margins(0, 3, 0, 0, -1, -2));
        }
        if (i.onNow && i.stop > i.start) {
            LinearLayout line = k.row();
            line.addView(k.text(Ui.clock(i.start), 12, 0xFFCFD3EC, 600));
            line.addView(new Bar(c, fraction(i, now), 0x33FFFFFF, 0xFFFF4F6D, 0xFFFF9A6B, true), k.weightParams(6, 10));
            line.addView(k.text(Ui.clock(i.stop), 12, 0xFFCFD3EC, 600));
            TextView left = k.text(c.getString(R.string.tile_min_left, minutesLeft(i, now)), 12, WHITE, 700);
            left.setPadding(k.dp(9), k.dp(3), k.dp(9), k.dp(3));
            left.setBackground(k.fill(0x24FFFFFF, 11));
            line.addView(left, k.margins(10, 0, 0, 0, -2, -2));
            col.addView(line, k.margins(0, 10, 0, 0, -1, -2));
        }
        return col;
    }

    /** NEXT: its poster, time, title, genres, and Remind me. */
    private static View nextBlock(Kit k, TileData.Info i, Actions a, boolean wide) {
        Context c = k.c;
        LinearLayout row = k.row();
        row.setPadding(wide ? k.dp(16) : 0, 0, 0, 0);
        if (i.nextPoster != null) {
            ImageView p = k.image(i.nextPoster, 8);
            row.addView(p, k.margins(0, 0, 12, 0, wide ? 54 : 40, wide ? 80 : 60));
        }
        LinearLayout col = k.column();
        col.addView(k.kicker(c.getString(R.string.tile_next_at, Ui.clock(i.nextStart)), 0xFFA9C4FF));
        TextView t = k.text(i.nextTitle, wide ? 16 : 15, WHITE, 700);
        t.setMaxLines(wide ? 2 : 1);
        col.addView(t, k.margins(0, 2, 0, 0, -1, -2));
        String meta = join(i.nextGenres, duration(i.nextStop - i.nextStart));
        if (!meta.isEmpty()) col.addView(k.text(meta, 12, 0xFFCFD3EC, 400), k.margins(0, 1, 0, 0, -1, -2));
        TextView remind = k.pill(c.getString(a.reminded(i) ? R.string.tile_reminded : R.string.tile_remind), true, 30);
        remind.setOnClickListener(v -> ((TextView) v).setText(a.remind(i) ? R.string.tile_reminded : R.string.tile_remind));
        if (wide) {
            col.addView(remind, k.margins(0, 8, 0, 0, -2, 30));
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(remind, k.margins(10, 0, 0, 0, -2, 34));
        }
        return row;
    }

    // ---- B: Card ---------------------------------------------------------------------------------------------------

    private static View card(Kit k, TileData.Info i, Actions a, boolean animate) {
        Context c = k.c;
        FrameLayout root = new FrameLayout(c);
        GradientDrawable shade = new GradientDrawable();
        shade.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        shade.setColors(new int[]{0xCC04050E, 0x0004050E});
        shade.setGradientCenter(0.18f, 1f);
        shade.setGradientRadius(k.dp(k.wide ? 520 : 460));
        View sv = new View(c);
        sv.setBackground(shade);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        root.addView(k.channelChip(i), k.corner(Gravity.TOP | Gravity.START, 16, k.topGap(16)));

        LinearLayout card = k.column();
        k.glass(card, 0xAD0C0E24, 22);
        card.setClickable(true);
        card.setPadding(k.dp(14), k.dp(14), k.dp(14), k.dp(14));
        LinearLayout top = k.row();
        top.setGravity(Gravity.TOP);
        top.addView(still(k, i), k.margins(0, 0, 14, 0, k.wide ? 150 : 120, k.wide ? 84 : 68));
        LinearLayout col = k.column();
        LinearLayout when = k.row();
        if (i.episode != null) {
            TextView ep = k.text(i.episode, 11, WHITE, 800);
            ep.setLetterSpacing(0.1f);
            ep.setPadding(k.dp(7), k.dp(2), k.dp(7), k.dp(2));
            ep.setBackground(k.fill(0x24FFFFFF, 6));
            when.addView(ep, k.margins(0, 0, 8, 0, -2, -2));
        }
        long now = System.currentTimeMillis();
        String times = i.onNow && i.stop > i.start ? Ui.range(i.start, i.stop) + " · "
                + c.getString(R.string.tile_min_left, minutesLeft(i, now)) : "";
        TextView tm = k.text(times, 12, 0xFFCFD3EC, 400);
        tm.setMaxLines(1);
        when.addView(tm, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(when);
        TextView title = k.text(TileKind.lookupTitle(i.title).isEmpty() ? i.title : TileKind.lookupTitle(i.title), k.wide ? 19 : 18, WHITE, 800);
        title.setMaxLines(2);
        col.addView(title, k.margins(0, 6, 0, 0, -1, -2));
        String words = !i.desc.isEmpty() ? i.desc : i.overview;
        if (!words.isEmpty()) {
            TextView d = k.text(words, 13, 0xFFE4E6F5, 400);
            d.setMaxLines(k.wide ? 3 : 2);
            d.setLineSpacing(0, 1.12f);
            col.addView(d, k.margins(0, 5, 0, 0, -1, -2));
        }
        top.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(top);
        String meta = join(i.genres, i.rating,
                i.nextTitle.isEmpty() ? "" : c.getString(R.string.tile_next_short, Ui.clock(i.nextStart), i.nextTitle));
        if (!meta.isEmpty()) {
            TextView m = k.text(meta, 12, 0xFFA9AECD, 400);
            m.setMaxLines(1);
            card.addView(m, k.margins(0, 10, 0, 0, -1, -2));
        }
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons = k.row();
        hs.addView(buttons);
        TextView restart = k.pill(c.getString(R.string.tile_restart), true, 40);
        restart.setOnClickListener(v -> a.restart());
        buttons.addView(restart, k.margins(0, 0, 8, 0, -2, 40));
        buttons.addView(favouriteButton(k, a), k.margins(0, 0, 8, 0, -2, 40));
        TextView similar = k.pill(c.getString(R.string.tile_similar), false, 40);
        similar.setOnClickListener(v -> a.similar());
        buttons.addView(similar, k.margins(0, 0, 8, 0, -2, 40));
        TextView info = k.pill(c.getString(i.episode != null ? R.string.tile_episode_info : R.string.tile_info), false, 40);
        info.setOnClickListener(v -> a.info());
        buttons.addView(info, k.margins(0, 0, 0, 0, -2, 40));
        card.addView(hs, k.margins(0, 12, 0, 0, -1, -2));

        int width = k.wide ? Math.min(k.dp(560), (int) (k.screenW() * 0.68f)) : -1;
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(width, -2, Gravity.BOTTOM | Gravity.START);
        cp.leftMargin = k.dp(14);
        cp.rightMargin = k.wide ? 0 : k.dp(14);
        cp.bottomMargin = k.dp(16);
        root.addView(k.fitted(card, 16), cp);

        // The channels either side, on the right (on its side only: upright, swiping is enough).
        if (k.wide) {
            LinearLayout side = k.column();
            side.setGravity(Gravity.CENTER_HORIZONTAL);
            side.addView(k.roundKey("▲", c.getString(R.string.tile_previous), () -> a.step(-1)));
            side.addView(k.text(a.neighbour(-1), 12, 0xBFFFFFFF, 600), k.margins(0, 6, 0, 0, -2, -2));
            View line = new View(c);
            line.setBackgroundColor(0x4DFFFFFF);
            side.addView(line, k.margins(0, 6, 0, 6, 2, 34));
            side.addView(k.text(a.neighbour(1), 12, 0xBFFFFFFF, 600), k.margins(0, 0, 0, 6, -2, -2));
            side.addView(k.roundKey("▼", c.getString(R.string.tile_following), () -> a.step(1)));
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
            sp.rightMargin = k.dp(16);
            root.addView(side, sp);
        }
        if (animate) k.rise(card);
        return root;
    }

    /** The card's picture: the episode's still (or the series' picture), LIVE on it and its progress along the foot. */
    private static View still(Kit k, TileData.Info i) {
        Context c = k.c;
        FrameLayout box = new FrameLayout(c);
        box.setBackground(k.fill(0xFF141A4A, 14));
        k.clip(box, 14);
        String pic = i.still != null ? i.still : i.poster;
        if (pic != null) {
            ImageView img = k.image(pic, 0);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            box.addView(k.logo(i, 80, 40), new FrameLayout.LayoutParams(k.dp(80), k.dp(40), Gravity.CENTER));
        }
        if (i.onNow) {
            TextView live = k.text(c.getString(R.string.tile_live), 10, WHITE, 800);
            live.setLetterSpacing(0.1f);
            live.setPadding(k.dp(6), k.dp(2), k.dp(6), k.dp(2));
            live.setBackground(k.fill(0xFFFF3B4E, 5));
            box.addView(live, k.corner(Gravity.TOP | Gravity.START, 8, 8));
            Bar bar = new Bar(c, fraction(i, System.currentTimeMillis()), 0x40FFFFFF, WHITE, WHITE, false);
            bar.square = true;
            box.addView(bar, new FrameLayout.LayoutParams(-1, k.dp(4), Gravity.BOTTOM));
        }
        return box;
    }

    private static TextView favouriteButton(Kit k, Actions a) {
        TextView fav = k.pill(k.c.getString(a.isFavourite() ? R.string.tile_favourited : R.string.tile_favourite), false, 40);
        fav.setOnClickListener(v -> {
            a.favourite();
            ((TextView) v).setText(a.isFavourite() ? R.string.tile_favourited : R.string.tile_favourite);
        });
        return fav;
    }

    // ---- D: Match night --------------------------------------------------------------------------------------------

    private static View match(Kit k, TileData.Info i, Actions a, boolean animate) {
        Context c = k.c;
        Game g = i.game;
        FrameLayout root = new FrameLayout(c);
        root.addView(new Fade(c, true, new int[]{0x0003060A, 0xD903060A}, null), k.foot(k.wide ? 230 : 330));

        // The scores box and the channel's logo, top left; upright, the box across the space above the picture.
        LinearLayout head = k.wide ? k.row() : k.column();
        if (g != null && k.wide) {
            View bug = k.scoreBug(g, 40, 0);
            ((LinearLayout.LayoutParams) bug.getLayoutParams()).rightMargin = k.dp(10);
            head.addView(bug);
        }
        head.addView(k.channelChip(i));
        if (g != null && !k.wide) {
            View bug = k.scoreBug(g, 64, k.screenW() - k.dp(32));
            ((LinearLayout.LayoutParams) bug.getLayoutParams()).topMargin = k.dp(14);
            head.addView(bug);
        }
        root.addView(head, k.corner(Gravity.TOP | Gravity.START, 16, k.topGap(16)));

        LinearLayout box = new LinearLayout(c);
        k.glass(box, 0xA6080C1A, 22);
        box.setClickable(true);
        box.setClipChildren(true);
        k.clip(box, 22);
        box.setPadding(k.dp(18), k.dp(16), k.dp(18), k.dp(16));
        box.setOrientation(k.wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        LinearLayout story = k.column();
        LinearLayout kick = k.row();
        String compLogo = g == null ? null : Competitions.logo(g);
        if (compLogo != null) kick.addView(k.image(compLogo, 0, ImageView.ScaleType.FIT_CENTER), k.margins(0, 0, 8, 0, 20, 20));
        kick.addView(k.kicker(kicker(c, i), 0xFF8FB2FF));
        story.addView(kick);
        TextView fixture = k.text("", k.wide ? 26 : 20, WHITE, 800);
        fixture.setText(fixture(c, i, k.wide));
        fixture.setMaxLines(2);
        story.addView(fixture, k.margins(0, 6, 0, 0, -1, -2));
        String line = matchLine(c, i, System.currentTimeMillis());
        if (!line.isEmpty()) story.addView(k.text(line, 14, 0xFFD6DBF0, 400), k.margins(0, 4, 0, 0, -1, -2));
        if (g != null && !i.facts.isEmpty()) story.addView(facts(k, g, i.facts), k.margins(0, 12, 0, 0, -1, -2));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons = k.row();
        hs.addView(buttons);
        if (g != null) {
            TextView centre = k.pill(c.getString(R.string.tile_match_centre), true, 40);
            centre.setOnClickListener(v -> a.matchCentre(g));
            buttons.addView(centre, k.margins(0, 0, 8, 0, -2, 40));
            if (g.state != Game.POST) {
                TextView also = k.pill(c.getString(R.string.tile_also_showing), false, 40);
                also.setOnClickListener(v -> a.alsoShowing(g));
                buttons.addView(also, k.margins(0, 0, 8, 0, -2, 40));
            }
        }
        buttons.addView(favouriteButton(k, a), k.margins(0, 0, 8, 0, -2, 40));
        TextView restart = k.pill(c.getString(R.string.tile_restart), g == null, 40);
        restart.setOnClickListener(v -> a.restart());
        buttons.addView(restart, k.margins(0, 0, 8, 0, -2, 40));
        TextView similar = k.pill(c.getString(R.string.tile_similar), false, 40);
        similar.setOnClickListener(v -> a.similar());
        buttons.addView(similar, k.margins(0, 0, 0, 0, -2, 40));
        story.addView(hs, k.margins(0, 12, 0, 0, -1, -2));
        box.addView(story, k.wide ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2));

        View side = g == null ? null : g.state == Game.PRE || (g.goals.isEmpty() && i.stats.isEmpty()) ? form(k, g) : soFar(k, g, i.stats, k.wide ? 3 : 2);
        if (side != null) {
            box.addView(k.divider(k.wide), k.dividerParams(k.wide));
            if (k.wide) {
                side.setPadding(k.dp(18), 0, 0, 0);
                box.addView(side, new LinearLayout.LayoutParams(k.dp(236), -2));
            } else {
                box.addView(side, new LinearLayout.LayoutParams(-1, -2));
            }
        }

        // A light sweeping across the glass now and then, as the TV's score graphics do.
        FrameLayout frame = new FrameLayout(c);
        frame.addView(box, new FrameLayout.LayoutParams(-1, -2));
        View sweep = new Fade(c, false, new int[]{0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF}, null);
        // As tall as the glass once it is laid out (match_parent here would stretch the frame to the whole screen).
        frame.addView(sweep, new FrameLayout.LayoutParams(k.dp(140), 0));
        sweep.setTranslationX(-k.dp(160));
        frame.post(() -> {
            sweep.getLayoutParams().height = box.getHeight();
            sweep.requestLayout();
            ObjectAnimator s = ObjectAnimator.ofFloat(sweep, View.TRANSLATION_X, -k.dp(160), frame.getWidth() + k.dp(20));
            s.setDuration(2200);
            s.setStartDelay(600);
            s.setRepeatCount(ValueAnimator.INFINITE);
            s.setRepeatMode(ValueAnimator.RESTART);
            s.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            s.start();
            sweep.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}

                @Override public void onViewDetachedFromWindow(View v) { s.cancel(); }
            });
        });
        k.clip(frame, 22);
        FrameLayout.LayoutParams fp = k.footParams();
        fp.leftMargin = fp.rightMargin = k.dp(12);
        fp.bottomMargin = k.dp(14);
        root.addView(k.fitted(frame, 14), fp);
        if (animate) k.rise(frame);
        return root;
    }

    /** "CHAMPIONS LEAGUE · LIVE FROM 17:30", "· LIVE", "· FULL TIME"; without a game, the channel's sport. */
    private static String kicker(Context c, TileData.Info i) {
        Game g = i.game;
        if (g == null) {
            String group = i.ch.group.isEmpty() ? c.getString(R.string.tile_sports) : i.ch.group.toUpperCase(Locale.UK);
            return i.onNow && i.start > 0 ? c.getString(R.string.tile_live_from, group, Ui.clock(i.start)) : group;
        }
        String comp = (g.leagueName == null || g.leagueName.isEmpty() ? Espn.name(g.slug) : g.leagueName).toUpperCase(Locale.UK);
        if (g.state == Game.LIVE) return c.getString(R.string.tile_live_now, comp);
        if (g.state == Game.POST) return c.getString(R.string.tile_full_time, comp);
        return i.start > 0 ? c.getString(R.string.tile_live_from, comp, Ui.clock(i.start)) : comp;
    }

    /** "AEK Athens v Real Madrid", the v lighter; the programme's title without a game. */
    private static CharSequence fixture(Context c, TileData.Info i, boolean wide) {
        Game g = i.game;
        if (g == null) return i.title;
        // The full names across the picture; upright, ESPN's short ones ("Man United") so the line fits.
        String home = wide && !g.homeFull.isEmpty() ? g.homeFull : g.homeName, away = wide && !g.awayFull.isEmpty() ? g.awayFull : g.awayName;
        SpannableStringBuilder s = new SpannableStringBuilder(home);
        int at = s.length();
        s.append(g.state == Game.PRE ? "  v  " : "  " + g.homeScore + "–" + g.awayScore + "  ");
        s.setSpan(new ForegroundColorSpan(g.state == Game.PRE ? 0xFFB8C2EA : 0xFFFFFFFF), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.append(away);
        return s;
    }

    /** Where and when: "OPAP Arena · kick-off in 7 min · on until 20:00", or the score's story while it is on. */
    static String matchLine(Context c, TileData.Info i, long now) {
        Game g = i.game;
        List<String> parts = new ArrayList<>();
        if (g == null) {
            if (i.onNow && i.stop > i.start) {
                parts.add(Ui.range(i.start, i.stop));
                parts.add(c.getString(R.string.tile_min_left, minutesLeft(i, now)));
            }
            return TextUtils.join(" · ", parts);
        }
        if (g.venue != null && !g.venue.isEmpty()) parts.add(g.venue);
        if (g.state == Game.PRE) {
            long mins = (g.kickoff - now + 59_999) / 60_000;
            parts.add(mins > 0 && mins <= 120 ? c.getString(R.string.tile_kickoff_in, (int) mins) : c.getString(R.string.tile_kickoff_at, Ui.clock(g.kickoff)));
        } else if (g.state == Game.LIVE) {
            parts.add(g.halftime ? c.getString(R.string.tile_half_time) : g.detail);
            parts.add(story(c, g, false));
        } else {
            parts.add(story(c, g, true));
        }
        if (i.onNow && i.stop > now) parts.add(c.getString(R.string.tile_on_until, Ui.clock(i.stop)));
        return TextUtils.join(" · ", parts);
    }

    private static String story(Context c, Game g, boolean over) {
        String score = Math.max(g.homeScore, g.awayScore) + "–" + Math.min(g.homeScore, g.awayScore);
        if (g.homeScore == g.awayScore) return over ? c.getString(R.string.tile_drew, g.score()) : c.getString(R.string.tile_level, g.score());
        String who = g.homeScore > g.awayScore ? g.homeName : g.awayName;
        return c.getString(over ? R.string.tile_won : R.string.tile_lead, who, score);
    }

    /** Where the facts' turn had got to, so the tile drawn again (the score read again) carries on from there. */
    private static String factsGame = "";
    private static int factsAt;
    private static final long FACT_MS = 6500;

    /**
     * The facts, one at a time in turn: its tag in its colour (STAT, FORM, TABLE, HEAD TO HEAD, PLAYER, FACT), the words
     * beside it, and how many there are as dots; each slides up into the place of the one before.
     */
    private static View facts(Kit k, Game g, List<String[]> facts) {
        if (!factsGame.equals(g.id)) {
            factsGame = g.id == null ? "" : g.id;
            factsAt = 0;
        }
        LinearLayout col = k.column();
        LinearLayout row = k.row();
        row.setGravity(Gravity.TOP);
        TextView tag = k.text("", 10, WHITE, 800);
        tag.setLetterSpacing(0.12f);
        tag.setMaxLines(1);
        tag.setPadding(k.dp(8), k.dp(3), k.dp(8), k.dp(3));
        row.addView(tag, k.margins(0, 1, 10, 0, -2, -2));
        TextView words = k.text("", 14, WHITE, 500);
        words.setMaxLines(2);
        words.setLineSpacing(0, 1.08f);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(row);
        LinearLayout dots = k.row();
        int n = Math.min(facts.size(), 12);
        for (int d = 0; d < n && n > 1; d++) {
            View dot = new View(k.c);
            dots.addView(dot, k.margins(0, 0, 4, 0, 5, 5));
        }
        if (n > 1) col.addView(dots, k.margins(0, 8, 0, 0, -2, -2));
        Runnable[] show = new Runnable[1];
        show[0] = () -> {
            String[] f = facts.get(factsAt % facts.size());
            int color = factColour(f[0]);
            tag.setText(f[0]);
            tag.setTextColor(color);
            tag.setBackground(k.fill((color & 0x00FFFFFF) | 0x2E000000, 8));
            words.setText(f[1]);
            col.setContentDescription(f[0] + ": " + f[1]);
            for (int d = 0; d < dots.getChildCount(); d++) {
                dots.getChildAt(d).setBackground(k.fill(d == factsAt % n ? WHITE : 0x4DFFFFFF, 3));
            }
        };
        show[0].run();
        if (facts.size() > 1) {
            Runnable next = new Runnable() {
                @Override public void run() {
                    if (!col.isAttachedToWindow()) return;
                    row.animate().alpha(0f).translationY(-k.dp(6)).setDuration(220).withEndAction(() -> {
                        factsAt = (factsAt + 1) % facts.size();
                        show[0].run();
                        row.setTranslationY(k.dp(8));
                        row.animate().alpha(1f).translationY(0).setDuration(320).start();
                    }).start();
                    col.postDelayed(this, FACT_MS);
                }
            };
            col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    v.removeCallbacks(next);
                    v.postDelayed(next, FACT_MS);
                }

                @Override public void onViewDetachedFromWindow(View v) { v.removeCallbacks(next); }
            });
            col.setOnClickListener(v -> {
                v.removeCallbacks(next);
                next.run();
            });
        }
        return col;
    }

    private static int factColour(String tag) {
        switch (tag) {
            case "STAT": return 0xFF8FB2FF;
            case "FORM": return 0xFF5CE19A;
            case "TABLE": return 0xFFC9A7FF;
            case "HEAD TO HEAD": return 0xFFFFC266;
            case "PLAYER": return 0xFFFF9EC7;
            case "RATINGS": return 0xFF7FE0A8;
            default: return 0xFFB8C2EA;
        }
    }

    /** FORM, LAST FIVE: each club's crest, short name and its last five as W, D and L squares, oldest first. */
    private static View form(Kit k, Game g) {
        if (g.homeForm.isEmpty() && g.awayForm.isEmpty()) return null;
        LinearLayout col = k.column();
        col.addView(k.kicker(k.c.getString(R.string.tile_form), 0xFFB8C2EA));
        col.addView(formRow(k, g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId), g.home, g.homeForm), k.margins(0, 10, 0, 0, -1, -2));
        col.addView(formRow(k, g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId), g.away, g.awayForm), k.margins(0, 8, 0, 0, -1, -2));
        return col;
    }

    private static View formRow(Kit k, String crest, String name, String form) {
        LinearLayout row = k.row();
        row.addView(k.image(crest, 0, ImageView.ScaleType.FIT_CENTER), k.margins(0, 0, 8, 0, 22, 22));
        TextView n = k.text(name, 13, WHITE, 700);
        row.addView(n, new LinearLayout.LayoutParams(k.dp(44), -2));
        for (char ch : form.toCharArray()) {
            TextView sq = k.text(String.valueOf(ch), 11, WHITE, 800);
            sq.setGravity(Gravity.CENTER);
            sq.setBackground(k.fill(ch == 'W' ? 0xFF2FBF71 : ch == 'L' ? 0xFFE5484D : 0xFF7A80A6, 6));
            row.addView(sq, k.margins(0, 0, 6, 0, 22, 22));
        }
        return row;
    }

    /** THE GAME SO FAR: each side's scorers by its crest, then possession, shots and on target as bars. */
    private static View soFar(Kit k, Game g, List<String[]> stats, int most) {
        LinearLayout col = k.column();
        col.addView(k.kicker(k.c.getString(R.string.tile_so_far), 0xFFB8C2EA));
        for (int side = 0; side < 2; side++) {
            String which = side == 0 ? "home" : "away";
            List<String> scorers = new ArrayList<>();
            for (String[] goal : g.goals) {
                // ESPN gives each goal the side it counts for, an own goal too.
                boolean og = goal.length > 4 && "og".equals(goal[4]);
                if (!which.equals(goal[3])) continue;
                scorers.add(surname(goal[1]) + " " + goal[2] + (goal.length > 4 && "pen".equals(goal[4]) ? " (pen)" : og ? " (og)" : ""));
            }
            LinearLayout row = k.row();
            String crest = side == 0 ? (g.homeLogo != null ? g.homeLogo : Espn.crest(g.homeId)) : (g.awayLogo != null ? g.awayLogo : Espn.crest(g.awayId));
            row.addView(k.image(crest, 0, ImageView.ScaleType.FIT_CENTER), k.margins(0, 0, 8, 0, 20, 20));
            TextView t = k.text(scorers.isEmpty() ? (side == 0 ? g.home : g.away) : TextUtils.join(", ", scorers), 13,
                    scorers.isEmpty() ? 0xFFA9AECD : WHITE, scorers.isEmpty() ? 600 : 500);
            t.setMaxLines(2);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            col.addView(row, k.margins(0, side == 0 ? 10 : 6, 0, 0, -1, -2));
        }
        int shown = 0;
        for (String[] s : stats) {
            if (shown >= most || !("Possession".equals(s[0]) || "Shots".equals(s[0]) || "On target".equals(s[0]))) continue;
            col.addView(statRow(k, s), k.margins(0, 10, 0, 0, -1, -2));
            shown++;
        }
        return col;
    }

    private static View statRow(Kit k, String[] s) {
        LinearLayout col = k.column();
        LinearLayout labels = k.row();
        labels.addView(k.text(s[1], 12, WHITE, 700));
        TextView mid = k.text(s[0].toUpperCase(Locale.UK), 10, 0xFFB8C2EA, 700);
        mid.setGravity(Gravity.CENTER);
        mid.setLetterSpacing(0.12f);
        labels.addView(mid, new LinearLayout.LayoutParams(0, -2, 1f));
        labels.addView(k.text(s[2], 12, WHITE, 700));
        col.addView(labels);
        float h = number(s[1]), w = number(s[2]);
        Bar bar = new Bar(k.c, h + w <= 0 ? 0.5f : h / (h + w), 0xFF8C9BFF, WHITE, WHITE, false);
        col.addView(bar, k.margins(0, 4, 0, 0, -1, 4));
        return col;
    }

    // ---- E: Cinematic ----------------------------------------------------------------------------------------------

    private static View cinema(Kit k, TileData.Info i, Actions a, boolean animate) {
        Context c = k.c;
        FrameLayout root = new FrameLayout(c);
        if (k.wide) root.addView(new Fade(c, false, new int[]{0xEB060402, 0xB3060402, 0x00060402}, new float[]{0f, 0.36f, 0.68f}),
                new FrameLayout.LayoutParams(-1, -1));
        else root.addView(new Fade(c, true, new int[]{0x00060402, 0xB3060402, 0xF2060402}, new float[]{0f, 0.35f, 1f}), k.foot(430));

        LinearLayout col = k.column();
        col.setClickable(true);
        LinearLayout kick = k.row();
        kick.addView(k.logo(i, 40, 22), k.margins(0, 0, 10, 0, 40, 22));
        String channel = (i.number > 0 ? i.number + " · " : "") + i.ch.name.toUpperCase(Locale.UK) + (i.onNow ? " · " + c.getString(R.string.tile_now) : "");
        TextView kt = k.kicker(channel, 0xFFF0C88A);
        kt.setLetterSpacing(0.25f);
        kick.addView(kt);
        col.addView(kick);
        String name = TileKind.lookupTitle(i.title);
        TextView title = k.text(name.isEmpty() ? i.title : name, k.wide ? 46 : 34, WHITE, 900);
        title.setLetterSpacing(-0.02f);
        title.setMaxLines(2);
        title.setLineSpacing(0, 0.92f);
        col.addView(title, k.margins(0, 10, 0, 0, -1, -2));
        String meta = join(i.year, i.genres, i.rating, i.certificate);
        if (meta.isEmpty()) meta = c.getString(R.string.tile_film);
        col.addView(k.text(meta, 14, 0xFFE9DDCB, 500), k.margins(0, 8, 0, 0, -1, -2));
        String words = !i.desc.isEmpty() ? i.desc : i.overview;
        if (!words.isEmpty()) {
            TextView d = k.text(words, k.wide ? 15 : 14, 0xFFF3ECDF, 400);
            d.setMaxLines(3);
            d.setLineSpacing(0, 1.15f);
            col.addView(d, k.margins(0, 10, 0, 0, -1, -2));
        }
        long now = System.currentTimeMillis();
        if (i.onNow && i.stop > i.start) {
            LinearLayout ends = k.row();
            ends.setGravity(Gravity.BOTTOM);
            TextView e = k.text("", k.wide ? 26 : 23, WHITE, 300);
            SpannableStringBuilder s = new SpannableStringBuilder(c.getString(R.string.tile_ends) + " ");
            int at = s.length();
            s.append(Ui.clock(i.stop));
            s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), at, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            e.setText(s);
            ends.addView(e);
            ends.addView(k.text(c.getString(R.string.tile_in_left, duration(now - i.start), minutesLeft(i, now)), 13, 0xFFE0D2BD, 400),
                    k.margins(14, 0, 0, 4, -2, -2));
            col.addView(ends, k.margins(0, 16, 0, 0, -1, -2));
            col.addView(new Bar(c, fraction(i, now), 0x38FFFFFF, 0xFFF0C88A, 0xFFF0C88A, false), k.margins(0, 10, 0, 0, k.wide ? 290 : -1, 3));
        }

        if (k.wide) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (k.screenW() * 0.5f), -2, Gravity.START | Gravity.CENTER_VERTICAL);
            lp.leftMargin = k.dp(32);
            root.addView(col, lp);
        } else {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            lp.leftMargin = lp.rightMargin = k.dp(20);
            lp.bottomMargin = k.dp(i.nextTitle.isEmpty() ? 24 : 120);
            root.addView(k.fitted(col, i.nextTitle.isEmpty() ? 24 : 120), lp);
        }
        if (!i.nextTitle.isEmpty()) {
            LinearLayout next = k.row();
            k.glass(next, 0x8C0A0804, 18);
            next.setClickable(true);
            next.setPadding(k.dp(12), k.dp(12), k.dp(14), k.dp(12));
            if (i.nextPoster != null) next.addView(k.image(i.nextPoster, 8), k.margins(0, 0, 12, 0, 44, 66));
            LinearLayout nc = k.column();
            nc.addView(k.kicker(c.getString(R.string.tile_up_next_at, Ui.clock(i.nextStart)), 0xFFF0C88A));
            TextView nt = k.text(i.nextTitle, 17, WHITE, 700);
            nt.setMaxLines(2);
            nc.addView(nt, k.margins(0, 3, 0, 0, -1, -2));
            String nm = join(i.nextGenres, duration(i.nextStop - i.nextStart));
            if (!nm.isEmpty()) nc.addView(k.text(nm, 12, 0xFFD9CCB8, 400), k.margins(0, 2, 0, 0, -1, -2));
            next.addView(nc, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView remind = k.pill(c.getString(a.reminded(i) ? R.string.tile_reminded : R.string.tile_remind), false, 32);
            remind.setOnClickListener(v -> ((TextView) v).setText(a.remind(i) ? R.string.tile_reminded : R.string.tile_remind));
            next.addView(remind, k.margins(10, 0, 0, 0, -2, 32));
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(k.wide ? k.dp(360) : -1, -2, Gravity.BOTTOM | Gravity.END);
            np.rightMargin = k.dp(k.wide ? 20 : 16);
            np.leftMargin = k.wide ? 0 : k.dp(16);
            np.bottomMargin = k.dp(k.wide ? 20 : 18);
            root.addView(next, np);
            if (animate) k.rise(next);
        }
        if (animate) {
            col.setAlpha(0f);
            col.setTranslationX(-k.dp(24));
            col.animate().alpha(1f).translationX(0).setDuration(480).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        return root;
    }

    // ---- words -----------------------------------------------------------------------------------------------------

    /** "Film · 2026 · Action · Thriller · ★ 7.2", "S2 · E4 · Crime · ★ 8.6", else the description's start. */
    static String meta(Context c, TileData.Info i) {
        String lead = i.kind == TileKind.Kind.FILM ? c.getString(R.string.tile_film) : i.episode == null ? "" : i.episode;
        String m = join(lead, i.year, i.genres, i.rating);
        if (!i.genres.isEmpty() || !i.rating.isEmpty()) return m;
        String words = !i.desc.isEmpty() ? i.desc : i.overview;
        return words.isEmpty() ? m : join(lead.isEmpty() ? "" : lead, words);
    }

    private static String numberAndName(TileData.Info i) {
        return (i.number > 0 ? i.number + " · " : "") + i.ch.name;
    }

    static String join(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(" · ");
            b.append(p.trim());
        }
        return b.toString();
    }

    /** "1h 06m", "48m"; "" for nothing. */
    static String duration(long ms) {
        if (ms <= 0) return "";
        long m = Math.max(1, ms / 60_000);
        return m >= 60 ? String.format(Locale.UK, "%dh %02dm", m / 60, m % 60) : m + "m";
    }

    static int minutesLeft(TileData.Info i, long now) {
        return (int) Math.max(1, (i.stop - now + 59_999) / 60_000);
    }

    static float fraction(TileData.Info i, long now) {
        if (i.stop <= i.start) return 0;
        return Math.max(0f, Math.min(1f, (now - i.start) / (float) (i.stop - i.start)));
    }

    private static String surname(String name) {
        if (name == null) return "";
        String t = name.trim();
        int sp = t.lastIndexOf(' ');
        return sp > 0 ? t.substring(sp + 1) : t;
    }

    private static float number(String s) {
        try {
            return Float.parseFloat(s.replace("%", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ---- the parts ---------------------------------------------------------------------------------------------------

    /** Sizes (a little larger on a tablet), type and the small parts every tile is made of. */
    private static final class Kit {
        final Context c;
        final boolean wide;
        final float scale;

        Kit(Context c, boolean wide) {
            this.c = c;
            this.wide = wide;
            float sw = c.getResources().getConfiguration().smallestScreenWidthDp;
            scale = (sw >= 720 ? 1.25f : sw >= 600 ? 1.12f : 1f) * size(c);
        }

        int dp(float v) {
            return Math.round(v * scale * c.getResources().getDisplayMetrics().density);
        }

        int screenW() {
            return c.getResources().getDisplayMetrics().widthPixels;
        }

        /**
         * A tile along the foot: on its side, all the width at Large, a little over half at Small and about three
         * quarters at Medium, from the left, so the rest of the picture shows; upright, all the width (it sits under
         * the picture).
         */
        FrameLayout.LayoutParams footParams() {
            float f = size(c);
            if (!wide || f >= 1f) return new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            int w = Math.round(screenW() * (f <= 0.7f ? 0.56f : 0.72f));
            return new FrameLayout.LayoutParams(w, -2, Gravity.BOTTOM | Gravity.START);
        }

        /**
         * Upright, a tile goes in the room under the picture (the picture across the width, 16:9, in the middle):
         * held to that height less its margin below, scrolling inside when it is taller, so it never covers the
         * picture or runs off the screen. On its side it is as it was.
         */
        View fitted(View tile, int bottomMarginDp) {
            if (wide) return tile;
            android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
            int picture = dm.widthPixels * 9 / 16;
            int room = (dm.heightPixels - picture) / 2 - dp(bottomMarginDp) - dp(10);
            int max = Math.max(dp(160), room);
            android.widget.ScrollView sv = new android.widget.ScrollView(c) {
                @Override protected void onMeasure(int w, int h) {
                    super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
                }
            };
            sv.setVerticalScrollBarEnabled(false);
            sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            sv.setVerticalFadingEdgeEnabled(true);
            sv.setFadingEdgeLength(dp(28));
            sv.setClipToPadding(false);
            sv.addView(tile, new FrameLayout.LayoutParams(-1, -2));
            return sv;
        }

        /** How far down the chips at the top go: upright, under the player's own ⋯, picture in picture and ✕. */
        int topGap(int dpWide) {
            return wide ? dpWide : 76;
        }

        TextView text(String s, float sp, int color, int weight) {
            TextView t = new TextView(c);
            t.setText(s);
            t.setTextSize(sp * scale);
            t.setTextColor(color);
            t.setTypeface(face(weight));
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setIncludeFontPadding(true);
            return t;
        }

        TextView kicker(String s, int color) {
            TextView t = text(s, 11, color, 800);
            t.setLetterSpacing(0.16f);
            t.setMaxLines(1);
            return t;
        }

        Typeface face(int weight) {
            if (Build.VERSION.SDK_INT >= 28) return Typeface.create(Typeface.SANS_SERIF, weight, false);
            return weight >= 600 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }

        LinearLayout row() {
            LinearLayout l = new LinearLayout(c);
            l.setOrientation(LinearLayout.HORIZONTAL);
            l.setGravity(Gravity.CENTER_VERTICAL);
            return l;
        }

        LinearLayout column() {
            LinearLayout l = new LinearLayout(c);
            l.setOrientation(LinearLayout.VERTICAL);
            return l;
        }

        GradientDrawable fill(int color, float radius) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(color);
            g.setCornerRadius(dp(radius));
            return g;
        }

        /** The frosted ground: dark see-through with a faint light edge. */
        void glass(View v, int color, float radius) {
            GradientDrawable g = fill(color, radius);
            g.setStroke(Math.max(1, dp(1) / 2 + 1), 0x29FFFFFF);
            v.setBackground(g);
            v.setElevation(dp(6));
        }

        void clip(View v, float radius) {
            float r = dp(radius);
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r); }
            });
            v.setClipToOutline(true);
        }

        LinearLayout.LayoutParams margins(int l, int t, int r, int b, int wDp, int hDp) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(wDp < 0 ? wDp : dp(wDp), hDp < 0 ? hDp : dp(hDp));
            p.setMargins(dp(l), dp(t), dp(r), dp(b));
            return p;
        }

        LinearLayout.LayoutParams weightParams(int marginDp, int heightDp) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(heightDp), 1f);
            p.leftMargin = p.rightMargin = dp(marginDp + 4);
            return p;
        }

        FrameLayout.LayoutParams foot(int heightDp) {
            return new FrameLayout.LayoutParams(-1, dp(heightDp), Gravity.BOTTOM);
        }

        FrameLayout.LayoutParams corner(int gravity, int x, int y) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2, gravity);
            if ((gravity & Gravity.START) == Gravity.START) p.leftMargin = dp(x); else p.rightMargin = dp(x);
            if ((gravity & Gravity.TOP) == Gravity.TOP) p.topMargin = dp(y); else p.bottomMargin = dp(y);
            return p;
        }

        View divider(boolean vertical) {
            View v = new View(c);
            v.setBackgroundColor(0x24FFFFFF);
            return v;
        }

        LinearLayout.LayoutParams dividerParams(boolean vertical) {
            LinearLayout.LayoutParams p = vertical ? new LinearLayout.LayoutParams(Math.max(1, dp(1)), -1)
                    : new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
            if (vertical) p.leftMargin = p.rightMargin = dp(16); else p.topMargin = p.bottomMargin = dp(12);
            return p;
        }

        ImageView image(String url, float radius) {
            return image(url, radius, ImageView.ScaleType.CENTER_CROP);
        }

        ImageView image(String url, float radius, ImageView.ScaleType type) {
            ImageView v = new ImageView(c);
            v.setScaleType(type);
            if (radius > 0) clip(v, radius);
            if (url != null) com.gathin.tvhomephone.sports.Ui.load(v, url);
            return v;
        }

        /** The channel's logo, else its initials, in a w by h box. */
        View logo(TileData.Info i, int wDp, int hDp) {
            FrameLayout box = new FrameLayout(c);
            TextView ini = text(Ui.initials(i.ch.name), 12, 0xCCFFFFFF, 800);
            ini.setGravity(Gravity.CENTER);
            box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setContentDescription(i.ch.name);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            if (i.channelLogo != null) com.gathin.tvhomephone.sports.Ui.load(img, i.channelLogo, () -> ini.setText(""), null);
            return box;
        }

        /** The channel's logo, number and name on a frosted chip. */
        View channelChip(TileData.Info i) {
            LinearLayout chip = row();
            glass(chip, 0x8C060A14, 20);
            chip.setPadding(dp(12), 0, dp(14), 0);
            chip.setMinimumHeight(dp(40));
            chip.addView(logo(i, 44, 26), margins(0, 0, 8, 0, 44, 26));
            if (i.number > 0) chip.addView(text(String.valueOf(i.number), 16, WHITE, 800), margins(0, 0, 8, 0, -2, -2));
            TextView n = text(i.ch.name, 14, WHITE, 600);
            n.setMaxLines(1);
            n.setMaxWidth(dp(180));
            chip.addView(n);
            return chip;
        }

        View livePill() {
            LinearLayout p = row();
            glass(p, 0x800A0C1E, 17);
            p.setElevation(0);
            p.setPadding(dp(12), dp(6), dp(12), dp(6));
            View dot = new View(c);
            dot.setBackground(fill(0xFFFF3B4E, 4));
            p.addView(dot, margins(0, 0, 7, 0, 8, 8));
            TextView t = text(c.getString(R.string.tile_live), 12, WHITE, 800);
            t.setLetterSpacing(0.1f);
            p.addView(t);
            ObjectAnimator beat = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f);
            beat.setDuration(800);
            beat.setRepeatMode(ValueAnimator.REVERSE);
            beat.setRepeatCount(ValueAnimator.INFINITE);
            beat.start();
            dot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {}

                @Override public void onViewDetachedFromWindow(View v) { beat.cancel(); }
            });
            return p;
        }

        /** HD and S (subtitles) in outlined boxes, as the TV marks them, from the stream itself; null for neither. */
        View badges(TileData.Info i) {
            if (!i.hd && !i.subtitles) return null;
            LinearLayout b = row();
            if (i.hd) b.addView(mark("HD"), margins(0, 0, 6, 0, -2, -2));
            if (i.subtitles) b.addView(mark("S"), margins(0, 0, 6, 0, -2, -2));
            return b;
        }

        private TextView mark(String s) {
            TextView t = text(s, 10, WHITE, 800);
            t.setPadding(dp(5), dp(1), dp(5), dp(1));
            GradientDrawable g = fill(0, 4);
            g.setStroke(Math.max(1, Math.round(dp(1.5f))), 0xBFFFFFFF);
            t.setBackground(g);
            return t;
        }

        TextView pill(String s, boolean primary, int heightDp) {
            TextView t = text(s, 14, primary ? INK : WHITE, primary ? 700 : 600);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(1);
            t.setPadding(dp(16), 0, dp(16), 0);
            GradientDrawable g = fill(primary ? WHITE : 0x1AFFFFFF, heightDp / 2f);
            if (!primary) g.setStroke(Math.max(1, dp(1)), 0x38FFFFFF);
            android.content.res.ColorStateList ripple = android.content.res.ColorStateList.valueOf(primary ? 0x33000000 : 0x33FFFFFF);
            t.setBackground(new android.graphics.drawable.RippleDrawable(ripple, g, null));
            t.setMinHeight(dp(heightDp));
            t.setClickable(true);
            t.setFocusable(true);
            return t;
        }

        View roundKey(String glyph, String label, Runnable r) {
            TextView t = text(glyph, 14, WHITE, 700);
            t.setGravity(Gravity.CENTER);
            t.setBackground(fill(0x800C0E24, 20));
            t.setContentDescription(label);
            t.setClickable(true);
            t.setOnClickListener(v -> r.run());
            t.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
            return t;
        }

        /**
         * The scores box (ScoreBug) h high, its own proportions kept (ScoreBug.RATIO), no wider than maxWidth pixels (0: any),
         * with its LinearLayout params set (the caller adds margins to them).
         */
        View scoreBug(Game g, int hDp, int maxWidth) {
            Context ctx = c;
            ScoreBug bug = new ScoreBug(ctx);
            bug.show(g);
            float natural = 64 * ctx.getResources().getDisplayMetrics().density;
            float s = dp(hDp) / natural;
            int w = Math.round(natural * ScoreBug.RATIO);
            if (maxWidth > 0 && w * s > maxWidth) s = maxWidth / (float) w;
            FrameLayout box = new FrameLayout(ctx);
            box.addView(bug, new FrameLayout.LayoutParams(w, Math.round(natural)));
            bug.setPivotX(0);
            bug.setPivotY(0);
            bug.setScaleX(s);
            bug.setScaleY(s);
            box.setLayoutParams(new LinearLayout.LayoutParams(Math.round(w * s), Math.round(natural * s)));
            box.setElevation(dp(6));
            box.setClipChildren(true);
            return box;
        }

        /** The card rises into place as it comes. */
        void rise(View v) {
            v.setAlpha(0f);
            v.setTranslationY(dp(18));
            v.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    /** A gradient across (vertical: top to bottom) with its colours at the given stops. */
    @android.annotation.SuppressLint("ViewConstructor")
    static final class Fade extends View {
        private final boolean vertical;
        private final int[] colors;
        private final float[] stops;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Fade(Context c, boolean vertical, int[] colors, float[] stops) {
            super(c);
            this.vertical = vertical;
            this.colors = colors;
            this.stops = stops;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            paint.setShader(new LinearGradient(0, 0, vertical ? 0 : w, vertical ? h : 0, colors, stops, Shader.TileMode.CLAMP));
        }

        @Override protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
    }

    /** A progress line: its track, the part gone in a gradient, and a white knob at the point (optional). */
    @android.annotation.SuppressLint("ViewConstructor")
    static final class Bar extends View {
        private final float f;
        private final int track, from, to;
        private final boolean knob;
        boolean square;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Bar(Context c, float f, int track, int from, int to, boolean knob) {
            super(c);
            this.f = f;
            this.track = track;
            this.from = from;
            this.to = to;
            this.knob = knob;
        }

        private Shader fillShader;

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            fillShader = new LinearGradient(0, 0, Math.max(1, w * f), 0, from, to, Shader.TileMode.CLAMP);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float line = knob ? Math.min(h, getResources().getDisplayMetrics().density * 5) : h;
            float top = (h - line) / 2f, rad = square ? 0 : line / 2f;
            paint.setShader(null);
            paint.setColor(track);
            r.set(0, top, w, top + line);
            canvas.drawRoundRect(r, rad, rad, paint);
            float x = w * f;
            if (x > 0) {
                paint.setShader(fillShader);
                r.set(0, top, x, top + line);
                canvas.drawRoundRect(r, rad, rad, paint);
                paint.setShader(null);
            }
            if (knob) {
                float k = h / 2f;
                paint.setColor(0x59FF4F6D);
                canvas.drawCircle(Math.max(k, Math.min(w - k, x)), h / 2f, k, paint);
                paint.setColor(WHITE);
                canvas.drawCircle(Math.max(k, Math.min(w - k, x)), h / 2f, k * 0.62f, paint);
            }
        }
    }
}
