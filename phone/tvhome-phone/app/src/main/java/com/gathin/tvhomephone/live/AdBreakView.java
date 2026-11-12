package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.sports.Espn;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.Glyphs;
import com.gathin.tvhomephone.sports.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pre-match ad-break screen over the channel's adverts (ScoreOverlay puts it up while the channel's logo is gone
 * in the hour before kick-off): along the foot a strip, BEFORE KICK-OFF, the match and the time to kick-off, with
 * cards in the TV's line-up card's style (a navy streaked band with the competition's logo and the widget's name, a
 * pale streaked panel with navy words, the slanted navy wipe as one widget gives way to the next) taking turns through
 * the widgets AdBreakData found; on the right the line-up card going round until the break ends and where the two clubs
 * stand; on the left what fans are saying (Bluesky, Reddit), one post at a time. Sideways it lies over the picture;
 * upright it fills the room under the picture. The channel plays on underneath, sound and all.
 *
 * Touch: tap a card to pick it (a white ring; it holds still), tap it again or swipe it up or down for its next or last
 * widget, tap anywhere else to let go. A remote or keyboard: Down picks the first card, Left and Right move along,
 * Up and Down scroll the card, OK does a widget's action, Back lets go.
 */
@SuppressLint({"ViewConstructor", "SetTextI18n"})
final class AdBreakView extends FrameLayout {
    interface Host {
        /** Another channel (a game kicking off at the same time). */
        void play(Channel ch);

        /** Hide it for this break (the Hide button). */
        void hide();
    }

    static final int NAVY = 0xFF151A63, INK_SOFT = 0xFF5A6094, BAND_TOP = 0xFF1B2591, BAND_FOOT = 0xFF0D1360,
            PALE_TOP = 0xFFF4F5FD, PALE_FOOT = 0xFFC2C5EE, WIN = 0xFF22A447, LOSS = 0xFFD42B2B, DRAW = 0xFFFFFFFF;
    private static final String F1_LOGO = "https://a.espncdn.com/i/teamlogos/leagues/500/f1.png";

    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final float dp;
    private AdBreakData.Content k;
    private boolean landscape;
    private final List<Card> cards = new ArrayList<>();
    private int picked = -1;
    private TextView countdown;
    private BoardLineups lineups;
    private LinearLayout postsBox;
    private int postAt;
    private long postsAt;
    private boolean ending;

    AdBreakView(Context c, Host host) {
        super(c);
        this.host = host;
        dp = c.getResources().getDisplayMetrics().density;
        setClipChildren(false);
    }

    // ---- up and away -------------------------------------------------------------------------------------------------

    /** Up with this content; picture: where the channel's picture is (upright: the room under it is ours). */
    void show(AdBreakData.Content content, boolean sideways, Rect picture) {
        k = content;
        landscape = sideways;
        removeAllViews();
        cards.clear();
        picked = -1;
        if (landscape) buildSideways();
        else buildUpright(picture);
        setAlpha(0f);
        animate().alpha(1f).setDuration(320).start();
        h.post(tick);
        for (int i = 0; i < cards.size(); i++) {
            Card cd = cards.get(i);
            cd.start(AdBreak.firstTurn(i, cards.size()));
        }
        if (lineups != null && k.lineups != null) loopLineups();
        postsAt = System.currentTimeMillis();
        h.postDelayed(nextPost, 600);
    }

    /** Away (the adverts over, kick-off, another channel): slid down and faded; done then. */
    void end(Runnable done) {
        if (ending) return;
        ending = true;
        h.removeCallbacksAndMessages(null);
        for (Card cd : cards) cd.stop();
        if (lineups != null) lineups.gone();
        animate().alpha(0f).translationY(16 * dp).setDuration(260).withEndAction(done).start();
    }

    // ---- sideways: over the picture -----------------------------------------------------------------------------------

    private void buildSideways() {
        int w = getResources().getDisplayMetrics().widthPixels, ht = getResources().getDisplayMetrics().heightPixels;
        if (getWidth() > 0) { w = getWidth(); ht = getHeight(); }
        int margin = px(16), gap = px(12), cardH = px(96);
        // Clear of the camera's cut-out, the bars showing and the screen's rounded corners, so no card runs under them.
        int[] safe = safeSides();
        int padL = margin + safe[0], padR = margin + safe[1];
        w -= safe[0] + safe[1];
        List<List<Widget>> decks = decks();
        int n = Math.max(1, Math.min(decks.size(), w >= px(3 * 290 + 2 * 12 + 32) ? 3 : 2));
        decks = fold(decks, n);
        // The strip along the foot: a dark fade, its line of words, then the cards.
        LinearLayout strip = new LinearLayout(getContext());
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setPadding(padL, px(26), padR, px(12) + safe[2]);
        strip.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF5070912, 0xEB0B0F24, 0x000B0F24}));
        strip.addView(headline(), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(getContext());
        for (int i = 0; i < decks.size(); i++) {
            Card cd = new Card(getContext(), decks.get(i), i);
            cards.add(cd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cardH, 1f);
            if (i > 0) lp.leftMargin = gap;
            row.addView(cd, lp);
        }
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, cardH);
        rl.topMargin = px(8);
        strip.addView(row, rl);
        addView(strip, new LayoutParams(-1, -2, Gravity.BOTTOM));
        // The right: where they stand above the line-up card, the same size.
        int stripH = px(26) + px(26) + px(8) + cardH + px(12) + safe[2];
        int room = ht - stripH - px(12);
        int boxH = Math.max(px(70), Math.min(px(112), (room - px(10)) / 2));
        int boxW = BoardLineups.widthFor(boxH);
        LinearLayout right = new LinearLayout(getContext());
        right.setOrientation(LinearLayout.VERTICAL);
        View stand = standing(boxW, boxH);
        if (stand != null) right.addView(stand, new LinearLayout.LayoutParams(boxW, boxH));
        if (k.lineups != null) {
            lineups = new BoardLineups(getContext());
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(boxW, boxH);
            ll.topMargin = px(10);
            right.addView(lineups, ll);
        }
        LayoutParams rp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        rp.rightMargin = padR;
        rp.topMargin = Math.max(px(10), ht - stripH - px(10) - (stand != null ? boxH : 0) - (k.lineups != null ? boxH + px(10) : 0));
        addView(right, rp);
        // The left, above the strip: the fans' posts, the newest under the older two.
        postsBox = new LinearLayout(getContext());
        postsBox.setOrientation(LinearLayout.VERTICAL);
        postsBox.setGravity(Gravity.BOTTOM);
        int postsW = Math.min(px(320), w - boxW - margin * 2 - px(24));
        LayoutParams pp = new LayoutParams(postsW, -2, Gravity.BOTTOM | Gravity.START);
        pp.leftMargin = padL;
        pp.bottomMargin = stripH + px(10);
        if (postsW > px(180)) addView(postsBox, pp);
    }

    /**
     * The room the phone keeps for itself sideways, left, right and foot: the camera's cut-out (wherever the screen
     * is turned), the system bars while they show, and the screen's rounded corners at the foot (the part of the
     * curve a card's corner would run into).
     */
    private int[] safeSides() {
        int[] s = new int[3];
        android.view.WindowInsets wi = getRootWindowInsets();
        if (wi == null && getContext() instanceof android.app.Activity) {
            wi = ((android.app.Activity) getContext()).getWindow().getDecorView().getRootWindowInsets();
        }
        if (wi == null) return s;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets cut = wi.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.displayCutout());
            android.graphics.Insets bars = wi.getInsets(android.view.WindowInsets.Type.systemBars());
            s[0] = Math.max(cut.left, bars.left);
            s[1] = Math.max(cut.right, bars.right);
            s[2] = Math.max(cut.bottom, bars.bottom);
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 28 && wi.getDisplayCutout() != null) {
                android.view.DisplayCutout dc = wi.getDisplayCutout();
                s[0] = dc.getSafeInsetLeft();
                s[1] = dc.getSafeInsetRight();
                s[2] = dc.getSafeInsetBottom();
            }
            s[0] = Math.max(s[0], wi.getSystemWindowInsetLeft());
            s[1] = Math.max(s[1], wi.getSystemWindowInsetRight());
            s[2] = Math.max(s[2], wi.getSystemWindowInsetBottom());
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            android.view.RoundedCorner bl = wi.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT);
            android.view.RoundedCorner br = wi.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT);
            // A card's corner sits about where the curve is deepest: keep it a third of the radius further in.
            if (bl != null) s[0] = Math.max(s[0], bl.getRadius() / 3);
            if (br != null) s[1] = Math.max(s[1], br.getRadius() / 3);
        }
        return s;
    }

    // ---- upright: the room under the picture --------------------------------------------------------------------------

    private void buildUpright(Rect picture) {
        int top = picture == null ? 0 : picture.bottom;
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF20B0F24);
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(14), px(12), px(14), px(20));
        scroll.addView(col, new ScrollView.LayoutParams(-1, -2));
        col.addView(headline(), new LinearLayout.LayoutParams(-1, -2));
        // The fans' posts: a row to swipe along.
        postsBox = null;
        if (!k.posts.isEmpty()) {
            HorizontalScrollView hs = new HorizontalScrollView(getContext());
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout feed = new LinearLayout(getContext());
            for (FanPosts.Post p : k.posts) {
                View v = post(p);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(270), -2);
                lp.rightMargin = px(10);
                feed.addView(v, lp);
            }
            hs.addView(feed);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
            hl.topMargin = px(10);
            col.addView(hs, hl);
        }
        for (List<Widget> deck : decks()) {
            Card cd = new Card(getContext(), deck, cards.size());
            cards.add(cd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(104));
            lp.topMargin = px(10);
            col.addView(cd, lp);
        }
        int w = getResources().getDisplayMetrics().widthPixels - px(28);
        int boxH = Math.round(w / 2.95f), boxW = BoardLineups.widthFor(boxH);
        View stand = standing(boxW, boxH);
        if (stand != null) {
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(boxW, boxH);
            sl.topMargin = px(10);
            col.addView(stand, sl);
        }
        if (k.lineups != null) {
            lineups = new BoardLineups(getContext());
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(boxW, boxH);
            ll.topMargin = px(10);
            col.addView(lineups, ll);
        }
        LayoutParams lp = new LayoutParams(-1, -1);
        lp.topMargin = top;
        addView(scroll, lp);
    }

    // ---- the line of words over the cards -----------------------------------------------------------------------------

    private View headline() {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = words("BEFORE KICK-OFF", 11, 0xFFFF8A8A, true);
        tag.setLetterSpacing(0.1f);
        r.addView(tag);
        Game g = k.game;
        TextView match = words(g == null ? "" : g.title() + " · " + AdBreakData.hm(g.kickoff), 13, 0xFFFFFFFF, true);
        match.setPadding(px(10), 0, 0, 0);
        r.addView(match, new LinearLayout.LayoutParams(0, -2, 1f));
        countdown = words("", 18, 0xFFFFFFFF, true);
        r.addView(words("Kick-off in ", 11, 0xFFA9B1CC, false));
        r.addView(countdown);
        TextView hide = words("Hide", 12, 0xFFFFFFFF, true);
        hide.setPadding(px(12), px(5), px(12), px(5));
        hide.setBackground(Ui.fill(getContext(), 0x2EFFFFFF, 14));
        hide.setContentDescription("Hide the ad-break screen for this break");
        hide.setOnClickListener(v -> host.hide());
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.leftMargin = px(12);
        r.addView(hide, hl);
        return r;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (k != null && k.game != null && countdown != null) countdown.setText(AdBreak.countdown(k.game.kickoff, System.currentTimeMillis()));
            h.postDelayed(this, 1000);
        }
    };

    // ---- the fans' posts (sideways: one at a time, three at most) ----------------------------------------------------

    private final Runnable nextPost = new Runnable() {
        @Override public void run() {
            if (postsBox != null && !k.posts.isEmpty()) {
                FanPosts.Post p = k.posts.get(postAt % k.posts.size());
                postAt++;
                View v = post(p);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = px(8);
                postsBox.addView(v, lp);
                while (postsBox.getChildCount() > 3) postsBox.removeViewAt(0);
                // The older ones fade a little: the newest is the brightest.
                int n = postsBox.getChildCount();
                for (int i = 0; i < n; i++) postsBox.getChildAt(i).animate().alpha(i == n - 1 ? 1f : i == n - 2 ? 0.82f : 0.62f).setDuration(300).start();
                v.setAlpha(0f);
                v.setTranslationY(14 * dp);
                v.animate().alpha(1f).translationY(0).setDuration(380).setInterpolator(new DecelerateInterpolator(2f)).start();
            }
            // Fresh posts at most once a minute while it is up.
            if (System.currentTimeMillis() - postsAt > 60_000L && k.game != null) {
                postsAt = System.currentTimeMillis();
                Game g = k.game;
                App.io.execute(() -> {
                    List<FanPosts.Post> fresh = FanPosts.load(g, 12);
                    App.main.post(() -> {
                        if (!ending && !fresh.isEmpty()) {
                            k.posts = fresh;
                            postAt = 0;
                        }
                    });
                });
            }
            h.postDelayed(this, 8000);
        }
    };

    /** A post: navy, white words, the poster's picture in a white ring, name, handle and when, where it is from. */
    private View post(FanPosts.Post p) {
        LinearLayout card = new LinearLayout(getContext());
        card.setPadding(px(12), px(10), px(12), px(10));
        card.setBackground(Ui.fill(getContext(), 0xE60B0F24, 14));
        View face = avatar(p.avatar, p.name, 0xFF2B4C9B, 34);
        card.addView(face, new LinearLayout.LayoutParams(px(34), px(34)));
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(10), 0, 0, 0);
        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = words(p.name, 12.5f, 0xFFFFFFFF, true);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView src = words(p.source, 9.5f, 0xFFA9B1CC, true);
        src.setPadding(px(6), px(1), px(6), px(1));
        src.setBackground(Ui.fill(getContext(), 0x26FFFFFF, 6));
        top.addView(src);
        col.addView(top);
        String ago = FanPosts.ago(p.at, System.currentTimeMillis());
        TextView handle = words(p.handle + (ago.isEmpty() ? "" : " · " + ago), 10.5f, 0xFFA9B1CC, false);
        handle.setMaxLines(1);
        handle.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(handle);
        TextView text = words(p.text, 12.5f, 0xFFEEF1FA, false);
        text.setPadding(0, px(3), 0, 0);
        text.setMaxLines(4);
        text.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(text);
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        return card;
    }

    // ---- where they stand -------------------------------------------------------------------------------------------

    /** The two clubs' places, in the line-up card's style, the line-up card's size; null without a table. */
    private View standing(int w, int hgt) {
        if (k.homeRow == null || k.awayRow == null || k.game == null) return null;
        Game g = k.game;
        List<View> lines = new ArrayList<>();
        LinearLayout head = row(4);
        head.addView(spacer(18 + 6 + 15 + 6), new LinearLayout.LayoutParams(px(18 + 6 + 15 + 6), -2));
        head.addView(words(" ", 9, INK_SOFT, true), new LinearLayout.LayoutParams(0, -2, 1f));
        for (String x : new String[]{"P", "GD", "PTS"}) head.addView(cell(x, 9.5f, INK_SOFT, true, 30));
        lines.add(head);
        for (String[] r : new String[][]{k.homeRow, k.awayRow}) {
            LinearLayout l = row(6);
            l.addView(cell(r[AdBreak.T_RANK], 12, INK_SOFT, true, 18));
            boolean home = r == k.homeRow;
            l.addView(crest(home ? AdBreakData.homeCrest(g) : AdBreakData.awayCrest(g), 15));
            TextView n = words(r[AdBreak.T_NAME], 12.5f, NAVY, true);
            n.setMaxLines(1);
            n.setEllipsize(TextUtils.TruncateAt.END);
            l.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
            l.addView(cell(r[AdBreak.T_PLAYED], 12, NAVY, false, 30));
            l.addView(cell(r[AdBreak.T_GD], 12, NAVY, false, 30));
            l.addView(cell(r[AdBreak.T_POINTS], 13, NAVY, true, 30));
            lines.add(l);
        }
        if (k.ifFinished != null) {
            int now = AdBreak.number(k.homeRow[AdBreak.T_RANK]), win = k.ifFinished[0][0];
            String name = g.homeName != null ? g.homeName : g.home;
            if (win > 0 && now > 0 && win < now) lines.add(foot("A win today takes " + name + " up to " + AdBreak.ordinal(win)));
            else if (win > 0) lines.add(foot("A win today keeps " + name + " " + AdBreak.ordinal(win)));
        }
        Card c = new Card(getContext(), null, -1);
        c.still(new Widget(compMark(), "WHERE\nTHEY\nSTAND", ctx -> lines));
        return c;
    }

    // ---- the line-up card, round and round ----------------------------------------------------------------------------

    private void loopLineups() {
        if (ending || lineups == null || k.lineups == null) return;
        lineups.show(k.lineups, () -> h.postDelayed(this::loopLineups, 600));
    }

    // ---- the widgets ------------------------------------------------------------------------------------------------

    /** A widget: its mark and name for the band, its lines (made each time it comes up), how long it stays, its action. */
    static final class Widget {
        final java.util.function.Supplier<View> mark;
        final String caption;
        final java.util.function.Function<Context, List<View>> lines;
        long ms = AdBreak.TURN_MS;
        Runnable action;
        /** A clip to play while it is up: {mp4, still}. */
        String[] clip;

        Widget(java.util.function.Supplier<View> mark, String caption, java.util.function.Function<Context, List<View>> lines) {
            this.mark = mark;
            this.caption = caption;
            this.lines = lines;
        }
    }

    /** The widgets, in three decks: the match, the league, the day. Only ones with something real to show. */
    private List<List<Widget>> decks() {
        List<List<Widget>> all = new ArrayList<>();
        List<Widget> match = new ArrayList<>(), league = new ArrayList<>(), day = new ArrayList<>();
        Game g = k.game;
        if (g == null) return all;
        Espn.Summary sum = k.summary;
        String hc = AdBreakData.homeCrest(g), ac = AdBreakData.awayCrest(g), hn = name(g, true), an = name(g, false);
        String comp = g.leagueName == null ? "" : g.leagueName;

        if (!k.homeForm.isEmpty() || !k.awayForm.isEmpty()) {
            match.add(new Widget(compMark(), "FORM", c -> list(
                    formRow(hc, hn, k.homeForm), formRow(ac, an, k.awayForm),
                    foot((comp.isEmpty() ? "This competition" : comp) + " games only · oldest first"))));
        }
        if (sum != null && !sum.meetings.isEmpty()) {
            match.add(new Widget(compMark(), "HEAD\nTO HEAD", c -> {
                List<String[]> ms = new ArrayList<>(sum.meetings.subList(0, Math.min(5, sum.meetings.size())));
                java.util.Collections.reverse(ms);   // oldest on the left
                LinearLayout chips = row(4);
                int w = 0, l = 0, d = 0;
                for (String[] m : ms) {
                    int hg = AdBreak.number(m[2]), ag = AdBreak.number(m[4]);
                    boolean ourHome = m.length > 7 && g.homeId != null && g.homeId.equals(m[7]);
                    int us = ourHome ? hg : ag, them = ourHome ? ag : hg;
                    char r = us > them ? 'W' : us < them ? 'L' : 'D';
                    if (r == 'W') w++; else if (r == 'L') l++; else d++;
                    LinearLayout col = new LinearLayout(getContext());
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    TextView chip = words(hg + "–" + ag, 12, r == 'D' ? NAVY : 0xFFFFFFFF, true);
                    chip.setGravity(Gravity.CENTER);
                    chip.setBackground(boxBg(r));
                    col.addView(chip, new LinearLayout.LayoutParams(-1, px(20)));
                    col.addView(words(m[0], 9, INK_SOFT, true));
                    chips.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
                }
                LinearLayout sumRow = row(5);
                sumRow.addView(crest(hc, 15));
                sumRow.addView(words(w + (w == 1 ? " win" : " wins"), 12, NAVY, true));
                sumRow.addView(words("·", 12, INK_SOFT, false));
                sumRow.addView(crest(ac, 15));
                sumRow.addView(words(l + (l == 1 ? " win" : " wins"), 12, NAVY, true));
                sumRow.addView(words("·", 12, INK_SOFT, false));
                sumRow.addView(words(d + (d == 1 ? " draw" : " draws"), 12, NAVY, true));
                return list(chips, sumRow, foot(hn + "'s side first · oldest on the left"));
            }));
        }
        if (k.homeRow != null && k.awayRow != null && k.homeRow.length > 10) {
            match.add(new Widget(compMark(), "SEASON\nSO FAR", c -> {
                LinearLayout head = row(4);
                head.addView(spacer(0), new LinearLayout.LayoutParams(0, 1, 1f));
                for (String x : new String[]{"W", "D", "L", "GD", "PTS"}) head.addView(cell(x, 9.5f, INK_SOFT, true, 32));
                List<View> out = list(head);
                for (String[] r : new String[][]{k.homeRow, k.awayRow}) {
                    LinearLayout l = row(4);
                    boolean home = r == k.homeRow;
                    l.addView(clubLabel(home ? hc : ac, home ? hn : an), new LinearLayout.LayoutParams(0, -2, 1f));
                    for (int i : new int[]{8, 9, 10, AdBreak.T_GD, AdBreak.T_POINTS}) l.addView(cell(r[i], 13, NAVY, true, 32));
                    out.add(l);
                }
                return out;
            }));
        }
        if (!k.watch.isEmpty()) {
            match.add(new Widget(compMark(), "PLAYERS\nTO WATCH", c -> {
                List<View> out = new ArrayList<>();
                for (Object[] p : k.watch) {
                    boolean home = "home".equals(p[0]);
                    LinearLayout l = row(8);
                    l.addView(face((Bitmap) p[3], (String) p[1], kit(home), 30));
                    l.addView(crest(home ? hc : ac, 16));
                    LinearLayout col = new LinearLayout(getContext());
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.addView(words((String) p[1], 12.5f, NAVY, true));
                    TextView what = words(((String) p[2]).replace("Matches: ", "").replace(", Goals: ", " games, ") + (((String) p[2]).contains("Goals") ? " goals" : ""), 10.5f, INK_SOFT, false);
                    col.addView(what);
                    l.addView(col);
                    out.add(l);
                }
                return out;
            }));
        }
        if (sum != null && !sum.homeKit.isEmpty() && !sum.awayKit.isEmpty()) {
            match.add(new Widget(compMark(), "KITS\nTODAY", c -> {
                LinearLayout l = row(18);
                l.addView(kitRow(hc, hn, Glyphs.color(sum.homeKit, 0xFFDA291C), Glyphs.color(sum.homeKitAlt, 0xFFFFFFFF)));
                l.addView(kitRow(ac, an, Glyphs.color(sum.awayKit, 0xFF274488), Glyphs.color(sum.awayKitAlt, 0xFFFFFFFF)));
                return list(l, foot("As ESPN lists the shirts for today"));
            }));
        }
        if (sum != null && !sum.referee.isEmpty()) {
            match.add(new Widget(compMark(), "THE\nREFEREE", c -> {
                LinearLayout l = row(10);
                l.addView(face(null, sum.referee, new int[]{0xFF2B2D33, 0xFF111216}, 32));
                LinearLayout col = new LinearLayout(getContext());
                col.setOrientation(LinearLayout.VERTICAL);
                col.addView(words(sum.referee, 13.5f, NAVY, true));
                col.addView(words("Referee today", 10.5f, INK_SOFT, false));
                l.addView(col);
                return list(l);
            }));
        }

        if (!k.homeAround.isEmpty()) league.add(tableWidget(k.homeAround, g.homeId, g.awayId));
        if (!k.awayAround.isEmpty() && !contains(k.homeAround, g.awayId)) league.add(tableWidget(k.awayAround, g.awayId, g.homeId));
        if (k.ifFinished != null) {
            league.add(new Widget(compMark(), "IF IT\nFINISHED\nNOW", c -> {
                LinearLayout head = row(4);
                head.addView(spacer(0), new LinearLayout.LayoutParams(0, 1, 1f));
                for (String x : new String[]{hn.toUpperCase(Locale.UK) + " WIN", "DRAW", an.toUpperCase(Locale.UK) + " WIN"}) {
                    TextView t = cell(x, 8.5f, INK_SOFT, true, 62);
                    t.setMaxLines(2);
                    head.addView(t);
                }
                List<View> out = list(head);
                for (int s = 0; s < 2; s++) {
                    LinearLayout l = row(4);
                    l.addView(clubLabel(s == 0 ? hc : ac, s == 0 ? hn : an), new LinearLayout.LayoutParams(0, -2, 1f));
                    for (int r = 0; r < 3; r++) l.addView(cell(AdBreak.ordinal(k.ifFinished[s][r]), 13, NAVY, true, 62));
                    out.add(l);
                }
                return out;
            }));
        }
        if (!k.alsoNow.isEmpty()) {
            Widget also = new Widget(compMark(), "ALSO\nAT " + AdBreakData.hm(g.kickoff), c -> {
                List<View> out = new ArrayList<>();
                for (int i = 0; i < k.alsoNow.size(); i++) {
                    Game x = k.alsoNow.get(i);
                    Channel ch = i < k.alsoChannels.size() ? k.alsoChannels.get(i) : null;
                    LinearLayout l = fixtureRow(x, null);
                    TextView where = words(ch == null ? "Not in your playlist" : ch.name, 10.5f, INK_SOFT, true);
                    where.setMaxLines(1);
                    where.setEllipsize(TextUtils.TruncateAt.END);
                    l.addView(where, new LinearLayout.LayoutParams(px(96), -2));
                    if (ch != null) {
                        l.setOnClickListener(v -> host.play(ch));
                        l.setContentDescription("Watch " + x.title() + " on " + ch.name);
                    }
                    out.add(l);
                }
                out.add(foot("Tap a game to watch it for the break"));
                return out;
            });
            for (Channel ch : k.alsoChannels) if (ch != null) { also.action = () -> host.play(ch); break; }
            league.add(also);
        }
        if (!k.elsewhere.isEmpty()) {
            league.add(new Widget(compMark(), "ELSE-\nWHERE", c -> {
                List<View> out = new ArrayList<>();
                for (Game x : k.elsewhere) {
                    LinearLayout l = row(6);
                    View dot = new View(getContext());
                    dot.setBackground(Ui.fill(getContext(), x.halftime ? 0xFF8F97B3 : LOSS, 4));
                    l.addView(dot, new LinearLayout.LayoutParams(px(7), px(7)));
                    l.addView(crest(AdBreakData.homeCrest(x), 15));
                    l.addView(words(name(x, true), 12, NAVY, true));
                    TextView sc = words(x.score(), 12.5f, NAVY, true);
                    sc.setPadding(px(5), px(1), px(5), px(1));
                    sc.setBackground(Ui.fill(getContext(), 0x1A151A63, 4));
                    l.addView(sc);
                    l.addView(words(name(x, false), 12, NAVY, true));
                    l.addView(crest(AdBreakData.awayCrest(x), 15));
                    l.addView(spacer(0), new LinearLayout.LayoutParams(0, 1, 1f));
                    l.addView(words(x.halftime ? "HT" : x.displayClock == null || x.displayClock.isEmpty() ? x.detail : x.displayClock, 10.5f, INK_SOFT, true));
                    out.add(l);
                }
                return out;
            }));
        }
        if (!k.homeNext.isEmpty() || !k.awayNext.isEmpty()) {
            league.add(new Widget(compMark(), "COMING\nUP", c -> {
                List<View> out = new ArrayList<>();
                for (int s = 0; s < 2; s++) {
                    List<String[]> next = s == 0 ? k.homeNext : k.awayNext;
                    if (next.isEmpty()) continue;
                    LinearLayout l = row(5);
                    l.addView(crest(s == 0 ? hc : ac, 16));
                    for (String[] f : next) {
                        LinearLayout chip = row(3);
                        chip.setGravity(Gravity.CENTER);
                        chip.setPadding(0, px(2), 0, px(2));
                        chip.setBackground(Ui.fill(getContext(), hardness(f[4]), 4));
                        chip.addView(crest(Espn.crest(f[0]), 13));
                        chip.addView(words(f[2], 10.5f, 0xFFFFFFFF, true));
                        l.addView(chip, new LinearLayout.LayoutParams(0, -2, 1f));
                    }
                    out.add(l);
                }
                out.add(foot("Red: in the top six · amber: mid-table · green: lower down"));
                return out;
            }));
        }
        if (!k.later.isEmpty() || !k.f1Today.isEmpty()) {
            league.add(new Widget(compMark(), "LATER\nTODAY", c -> {
                List<View> out = new ArrayList<>();
                for (Game x : k.later) {
                    LinearLayout l = fixtureRow(x, AdBreakData.hm(x.kickoff));
                    if (x.slug != null && !x.slug.equals(g.slug)) l.addView(crest(safe(com.gathin.tvhomephone.sports.Competitions.logo(x)), 15));
                    out.add(l);
                }
                for (String[] f : k.f1Today) {
                    LinearLayout l = row(6);
                    l.addView(cell(f[1], 12, NAVY, true, 40));
                    l.addView(crest(F1_LOGO, 16));
                    l.addView(words(f[0], 12, NAVY, true));
                    out.add(l);
                }
                while (out.size() > 3) out.remove(out.size() - 1);
                return out;
            }));
        }

        if (!k.headlines.isEmpty()) {
            day.add(new Widget(houseMark(), "HEAD-\nLINES", c -> {
                List<View> out = new ArrayList<>();
                for (String[] hl : k.headlines) {
                    LinearLayout l = row(7);
                    l.addView(crest("home".equals(hl[0]) ? hc : ac, 15));
                    TextView t = words(hl[1], 11.5f, NAVY, true);
                    t.setMaxLines(1);
                    t.setEllipsize(TextUtils.TruncateAt.END);
                    l.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
                    out.add(l);
                    if (out.size() >= 3) break;
                }
                out.add(foot("BBC Sport"));
                return out;
            }));
        }
        if (k.lastMeeting != null) {
            String[] m = k.lastMeeting;
            Widget last = new Widget(houseMark(), "THE LAST\nMEETING", c -> {
                LinearLayout l = row(7);
                boolean ourHome = m.length > 7 && g.homeId != null && g.homeId.equals(m[7]);
                l.addView(crest(ourHome ? hc : ac, 20));
                TextView sc = words(m[2] + "–" + m[4], 16, NAVY, true);
                sc.setPadding(px(7), px(1), px(7), px(1));
                sc.setBackground(Ui.fill(getContext(), 0x1A151A63, 4));
                l.addView(sc);
                l.addView(crest(ourHome ? ac : hc, 20));
                return list(l, words(m[1] + " v " + m[3], 11.5f, NAVY, true), foot(month(m[5]) + (k.clip != null ? " · no sound" : "")));
            });
            if (k.clip != null) {
                last.clip = k.clip;
                last.ms = AdBreak.CLIP_MAX_MS;
            }
            day.add(last);
        }
        if (!match.isEmpty()) all.add(match);
        if (!league.isEmpty()) all.add(league);
        if (!day.isEmpty()) all.add(day);
        return all;
    }

    /** Fewer cards than decks: the last ones go on the last card together. */
    private static List<List<Widget>> fold(List<List<Widget>> decks, int n) {
        if (decks.size() <= n) return decks;
        List<List<Widget>> out = new ArrayList<>(decks.subList(0, n - 1));
        List<Widget> rest = new ArrayList<>();
        for (int i = n - 1; i < decks.size(); i++) rest.addAll(decks.get(i));
        out.add(rest);
        return out;
    }

    private Widget tableWidget(List<String[]> around, String us, String them) {
        return new Widget(compMark(), "THE\nTABLE", c -> {
            List<View> out = new ArrayList<>();
            for (String[] r : around) {
                boolean me = r[AdBreak.T_ID].equals(us) || r[AdBreak.T_ID].equals(them);
                LinearLayout l = row(6);
                l.setPadding(px(5), px(1), px(5), px(1));
                if (me) l.setBackground(Ui.fill(getContext(), 0x29D42B2B, 4));
                l.addView(cell(r[AdBreak.T_RANK], 11, INK_SOFT, true, 16));
                l.addView(crest(r[AdBreak.T_LOGO].isEmpty() ? Espn.crest(r[AdBreak.T_ID]) : r[AdBreak.T_LOGO], 14));
                TextView n = words(r[AdBreak.T_NAME], 11.5f, NAVY, me);
                n.setMaxLines(1);
                n.setEllipsize(TextUtils.TruncateAt.END);
                l.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
                l.addView(cell(r[AdBreak.T_GD], 10.5f, INK_SOFT, true, 28));
                l.addView(cell(r[AdBreak.T_POINTS], 12, NAVY, true, 24));
                out.add(l);
            }
            return out;
        });
    }

    // ---- remote and keyboard -----------------------------------------------------------------------------------------

    /** A key: true when it was for the cards. Down picks the first; nothing is taken until then. */
    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        if (e.getAction() == KeyEvent.ACTION_UP) {
            // The key's lift goes where its press went: a Back that let a card go does not then close the player.
            boolean mine = code == tookDown;
            if (mine) tookDown = -1;
            return mine;
        }
        if (cards.isEmpty() || e.getAction() != KeyEvent.ACTION_DOWN) return false;
        boolean took = press(code);
        if (took) tookDown = code;
        return took;
    }

    /** The last key pressed that the cards took (its lift is theirs too). */
    private int tookDown = -1;

    private boolean press(int code) {
        if (picked < 0) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                pick(0);
                return true;
            }
            return false;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: pick(Math.max(0, picked - 1)); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: pick(Math.min(cards.size() - 1, picked + 1)); return true;
            case KeyEvent.KEYCODE_DPAD_UP: cards.get(picked).turn(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: cards.get(picked).turn(1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                Runnable act = cards.get(picked).action();
                if (act != null) act.run();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                pick(-1);
                return true;
            default:
                return false;
        }
    }

    /** Back: a picked card let go (true), else nothing here (false). */
    boolean back() {
        if (picked < 0) return false;
        pick(-1);
        return true;
    }

    private void pick(int i) {
        picked = i;
        for (int j = 0; j < cards.size(); j++) cards.get(j).ring(j == i);
    }

    /** A touch anywhere but on a card lets a picked card go; the touch carries on to the player underneath. */
    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && picked >= 0) {
            boolean onCard = false;
            int[] at = new int[2];
            for (Card cd : cards) {
                cd.getLocationOnScreen(at);
                if (ev.getRawX() >= at[0] && ev.getRawX() <= at[0] + cd.getWidth() && ev.getRawY() >= at[1] && ev.getRawY() <= at[1] + cd.getHeight()) onCard = true;
            }
            if (!onCard) pick(-1);
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---- a card -----------------------------------------------------------------------------------------------------

    /** One card: a band, a panel showing one widget, the wipe between widgets, the ring when picked. */
    private final class Card extends FrameLayout {
        private final List<Widget> widgets;
        private final FrameLayout band, stage;
        private final Wipe wipe;
        private int at;
        private boolean stopped;
        private final Runnable turnNext = this::auto;
        private MediaPlayer player;
        private final GestureDetector gestures;

        Card(Context c, List<Widget> widgets, int index) {
            super(c);
            this.widgets = widgets;
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 8 * dp);
                }
            });
            band = new FrameLayout(c);
            band.setBackground(new Streaks(BAND_TOP, BAND_FOOT, 0x12FFFFFF, 12 * dp, 2 * dp));
            addView(band, new LayoutParams(px(56), -1));
            stage = new FrameLayout(c);
            stage.setBackground(new Streaks(PALE_TOP, PALE_FOOT, 0x8CFFFFFF, 50 * dp, 21 * dp));
            stage.setClipChildren(true);
            LayoutParams sl = new LayoutParams(-1, -1);
            sl.leftMargin = px(56);
            addView(stage, sl);
            wipe = new Wipe(c);
            wipe.setVisibility(INVISIBLE);
            stage.addView(wipe, new LayoutParams(-1, -1));
            gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override public boolean onSingleTapUp(MotionEvent e) {
                    if (index < 0) return false;
                    if (picked == index) turn(1);
                    else pick(index);
                    return true;
                }

                @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (index < 0 || Math.abs(vy) < Math.abs(vx)) return false;
                    pick(index);
                    turn(vy < 0 ? 1 : -1);
                    return true;
                }
            });
            if (index >= 0) {
                setClickable(true);
                setFocusable(false);
                setContentDescription("Widget card " + (index + 1));
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            return widgets != null && gestures.onTouchEvent(e) || super.onTouchEvent(e);
        }

        @Override public boolean performClick() {
            return super.performClick();
        }

        /** Shown still (where they stand): one widget, no turns. */
        void still(Widget w) {
            put(w, 0, 1, false);
        }

        void start(long first) {
            if (widgets == null || widgets.isEmpty()) return;
            put(widgets.get(0), 0, widgets.size(), true);
            schedule(Math.max(first, widgets.get(0).ms));
        }

        void stop() {
            stopped = true;
            removeCallbacks(turnNext);
            release();
        }

        Runnable action() {
            return widgets == null || widgets.isEmpty() ? null : widgets.get(at).action;
        }

        private void schedule(long ms) {
            removeCallbacks(turnNext);
            if (!stopped) postDelayed(turnNext, ms);
        }

        /** Its own turn: the next widget, unless it is picked (then it holds still and looks again in a while). */
        private void auto() {
            boolean mine = picked >= 0 && picked < cards.size() && cards.get(picked) == this;
            if (mine) schedule(AdBreak.TURN_MS);
            else turn(1);
        }

        /** The next (1) or last (-1) widget, behind the wipe. */
        void turn(int step) {
            if (widgets == null || widgets.size() < 2 || stopped) {
                if (widgets != null && !stopped) schedule(AdBreak.TURN_MS);
                return;
            }
            int next = AdBreak.next(at, widgets.size(), step);
            at = next;
            Widget w = widgets.get(next);
            int pw = stage.getWidth() + Math.round(stage.getHeight() * 0.84f);
            wipe.setVisibility(VISIBLE);
            wipe.bringToFront();
            wipe.setTranslationX(-pw);
            wipe.animate().cancel();
            wipe.animate().translationX(stage.getWidth()).setDuration(AdBreak.WIPE_MS).setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> wipe.setVisibility(INVISIBLE)).start();
            postDelayed(() -> {
                if (!stopped) put(w, next, widgets.size(), true);
            }, AdBreak.WIPE_MS / 2);
            schedule(w.ms);
        }

        private void put(Widget w, int i, int n, boolean animate) {
            release();
            band.removeAllViews();
            LinearLayout b = new LinearLayout(getContext());
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER_HORIZONTAL);
            b.setPadding(0, px(10), 0, px(6));
            View mark = w.mark.get();
            b.addView(mark, new LinearLayout.LayoutParams(px(22), px(22)));
            TextView cap = words(w.caption, 8, 0xE6FFFFFF, true);
            cap.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            cap.setLetterSpacing(0.06f);
            cap.setGravity(Gravity.CENTER);
            cap.setLineSpacing(0, 0.95f);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.topMargin = px(6);
            b.addView(cap, cl);
            if (n > 1) {
                View fill = new View(getContext());
                b.addView(fill, new LinearLayout.LayoutParams(1, 0, 1f));
                TextView pos = words((i + 1) + " / " + n, 8, 0xB3FFFFFF, true);
                pos.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                b.addView(pos);
            }
            band.addView(b, new LayoutParams(-1, -1));
            for (int c = stage.getChildCount() - 1; c >= 0; c--) if (stage.getChildAt(c) != wipe) stage.removeViewAt(c);
            View page;
            List<View> lines = w.lines.apply(getContext());
            if (w.clip != null) {
                LinearLayout split = new LinearLayout(getContext());
                split.addView(clip(w.clip), new LinearLayout.LayoutParams(Math.round((getHeight() > 0 ? getHeight() : px(96)) * 16f / 9f), -1));
                LinearLayout side = column(lines, animate);
                side.setPadding(px(9), px(6), px(8), px(6));
                split.addView(side, new LinearLayout.LayoutParams(0, -1, 1f));
                page = split;
            } else {
                LinearLayout col = column(lines, animate);
                col.setPadding(px(11), px(7), px(10), px(7));
                page = col;
            }
            stage.addView(page, 0, new LayoutParams(-1, -1));
        }

        private LinearLayout column(List<View> lines, boolean animate) {
            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < lines.size(); i++) {
                View v = lines.get(i);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) lp.topMargin = px(5);
                col.addView(v, lp);
                if (animate) {
                    v.setAlpha(0f);
                    v.setTranslationX(26 * dp);
                    v.animate().alpha(1f).translationX(0).setStartDelay(120 + 70L * i).setDuration(420)
                            .setInterpolator(new DecelerateInterpolator(2.2f)).start();
                }
            }
            return col;
        }

        /** The last meeting's clip at the card's height, no sound, never asking for the sound: the channel plays on. */
        private View clip(String[] c) {
            FrameLayout box = new FrameLayout(getContext());
            box.setBackgroundColor(0xFF0B0F24);
            ImageView still = new ImageView(getContext());
            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (c.length > 1 && !c[1].isEmpty()) Ui.load(still, c[1]);
            box.addView(still, new LayoutParams(-1, -1));
            TextureView video = new TextureView(getContext());
            video.setAlpha(0f);
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int ht) {
                    if (stopped) return;
                    try {
                        MediaPlayer mp = new MediaPlayer();
                        player = mp;
                        mp.setDataSource(c[0]);
                        mp.setSurface(new Surface(st));
                        mp.setVolume(0f, 0f);
                        mp.setLooping(true);
                        mp.setOnPreparedListener(p -> {
                            if (player != p) return;
                            p.start();
                            video.animate().alpha(1f).setDuration(300).start();
                        });
                        // Can't play it here (the decoder busy with the channel): the still stays.
                        mp.setOnErrorListener((p, what, extra) -> true);
                        mp.prepareAsync();
                    } catch (Exception e) {
                        release();
                    }
                }

                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int ht) {}

                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                    release();
                    return true;
                }

                @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            });
            box.addView(video, new LayoutParams(-1, -1));
            TextView muted = words("NO SOUND", 8, 0xFFFFFFFF, true);
            muted.setPadding(px(5), px(1), px(5), px(1));
            muted.setBackground(Ui.fill(getContext(), 0x73000000, 3));
            LayoutParams ml = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            ml.setMargins(0, px(5), px(5), 0);
            box.addView(muted, ml);
            return box;
        }

        private void release() {
            MediaPlayer p = player;
            player = null;
            if (p != null) {
                try {
                    p.release();
                } catch (RuntimeException ignored) {
                    // gone already
                }
            }
        }

        void ring(boolean on) {
            if (!on) {
                setForeground(null);
                return;
            }
            GradientDrawable glow = new GradientDrawable();
            glow.setCornerRadius(8 * dp);
            glow.setStroke(px(6), 0x40FFFFFF);
            GradientDrawable line = new GradientDrawable();
            line.setCornerRadius(8 * dp);
            line.setStroke(px(3), 0xFFFFFFFF);
            setForeground(new LayerDrawable(new Drawable[]{glow, line}));
        }
    }

    /** The slanted navy panel, edged light blue and white, crossing a card's panel as its widget changes. */
    private final class Wipe extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Wipe(Context c) {
            super(c);
        }

        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            int ht = getMeasuredHeight();
            setMeasuredDimension(getMeasuredWidth() + Math.round(ht * 0.84f), ht);
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float slant = h * 0.42f, body = w - slant, edge = 7.5f * dp;
            panel(cv, 0, body, h, slant, NAVY);
            panel(cv, body - edge, body, h, slant, 0xFF3D4BD6);
            panel(cv, body - edge * 0.35f, body, h, slant, 0xFFFFFFFF);
            panel(cv, 0, edge, h, slant, 0xFF3D4BD6);
            panel(cv, 0, edge * 0.35f, h, slant, 0xE6FFFFFF);
        }

        private void panel(Canvas cv, float from, float to, int h, float slant, int colour) {
            path.reset();
            path.moveTo(from, h);
            path.lineTo(to, h);
            path.lineTo(to + slant, 0);
            path.lineTo(from + slant, 0);
            path.close();
            p.setColor(colour);
            cv.drawPath(path, p);
        }
    }

    /** A gradient ground with slanted light streaks across it (the line-up card's grounds). */
    static final class Streaks extends Drawable {
        private final int top, foot, streak;
        private final float gap, width;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Streaks(int top, int foot, int streak, float gap, float width) {
            this.top = top;
            this.foot = foot;
            this.streak = streak;
            this.gap = gap;
            this.width = width;
        }

        @Override public void draw(Canvas cv) {
            Rect b = getBounds();
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, foot, Shader.TileMode.CLAMP));
            cv.drawRect(b, p);
            p.setShader(null);
            if (streak == 0) return;
            p.setColor(streak);
            float slant = b.height() * 0.36f;
            for (float x = b.left - slant; x < b.right + slant; x += gap + width) {
                path.reset();
                path.moveTo(x, b.bottom);
                path.lineTo(x + width, b.bottom);
                path.lineTo(x + width + slant, b.top);
                path.lineTo(x + slant, b.top);
                path.close();
                cv.drawPath(path, p);
            }
        }

        @Override public void setAlpha(int alpha) {}

        @Override public void setColorFilter(ColorFilter cf) {}

        @Override public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    // ---- pieces -----------------------------------------------------------------------------------------------------

    private java.util.function.Supplier<View> compMark() {
        String url = k.compLogoOnNavy.isEmpty() ? k.compLogo : k.compLogoOnNavy;
        return () -> {
            ImageView v = new ImageView(getContext());
            v.setScaleType(ImageView.ScaleType.FIT_CENTER);
            if (!url.isEmpty()) Ui.load(v, new String[]{url, k.compLogo}, null, null);
            return v;
        };
    }

    /** The Homefield house with its play mark: for the widgets about the day. */
    private java.util.function.Supplier<View> houseMark() {
        return () -> new View(getContext()) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            @Override protected void onDraw(Canvas cv) {
                float s = Math.min(getWidth(), getHeight()) / 24f;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * s);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setColor(0xFFFFFFFF);
                path.reset();
                path.moveTo(3 * s, 11 * s);
                path.lineTo(12 * s, 3 * s);
                path.lineTo(21 * s, 11 * s);
                path.lineTo(21 * s, 21 * s);
                path.lineTo(3 * s, 21 * s);
                path.close();
                cv.drawPath(path, p);
                p.setStyle(Paint.Style.FILL);
                path.reset();
                path.moveTo(10 * s, 11 * s);
                path.lineTo(15.5f * s, 14.5f * s);
                path.lineTo(10 * s, 18 * s);
                path.close();
                cv.drawPath(path, p);
            }
        };
    }

    private TextView words(String s, float sp, int colour, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        t.setTypeface(bold ? Typeface.create("sans-serif-black", Typeface.NORMAL) : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView cell(String s, float sp, int colour, boolean bold, int widthDp) {
        TextView t = words(s, sp, colour, bold);
        t.setGravity(Gravity.END);
        t.setLayoutParams(new LinearLayout.LayoutParams(px(widthDp), -2));
        return t;
    }

    private TextView foot(String s) {
        TextView t = words(s, 9.5f, INK_SOFT, false);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout row(int gapDp) {
        LinearLayout r = new LinearLayout(getContext()) {
            @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
                if (getChildCount() > 0 && params instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) params).leftMargin += px(gapDp);
                super.addView(child, index, params);
            }
        };
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private View spacer(int dpw) {
        View v = new View(getContext());
        v.setMinimumWidth(px(dpw));
        return v;
    }

    private ImageView crest(String url, int sizeDp) {
        ImageView v = new ImageView(getContext());
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(sizeDp), px(sizeDp)));
        if (url != null && !url.isEmpty()) Ui.load(v, url);
        return v;
    }

    private View clubLabel(String crestUrl, String name) {
        LinearLayout l = row(6);
        l.addView(crest(crestUrl, 16));
        TextView n = words(name, 12.5f, NAVY, true);
        n.setMaxLines(1);
        n.setEllipsize(TextUtils.TruncateAt.END);
        l.addView(n);
        return l;
    }

    private LinearLayout formRow(String crestUrl, String name, String form) {
        LinearLayout l = row(4);
        l.addView(clubLabel(crestUrl, name), new LinearLayout.LayoutParams(0, -2, 1f));
        for (char r : form.toCharArray()) {
            TextView b = words(String.valueOf(r), 11, r == 'D' ? NAVY : 0xFFFFFFFF, true);
            b.setGravity(Gravity.CENTER);
            b.setBackground(boxBg(r));
            l.addView(b, new LinearLayout.LayoutParams(px(19), px(19)));
        }
        if (form.isEmpty()) l.addView(words("No games yet", 10.5f, INK_SOFT, false));
        return l;
    }

    /** A result's box: won green, lost red, drawn white with a thin navy line. */
    private Drawable boxBg(char r) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(4 * dp);
        g.setColor(r == 'W' ? WIN : r == 'L' ? LOSS : DRAW);
        if (r == 'D') g.setStroke(Math.max(1, px(1.5f)), 0x59151A63);
        return g;
    }

    private LinearLayout fixtureRow(Game x, String time) {
        LinearLayout l = row(5);
        if (time != null) l.addView(cell(time, 12, NAVY, true, 40));
        l.addView(crest(AdBreakData.homeCrest(x), 15));
        TextView hn = words(name(x, true), 11.5f, NAVY, true);
        hn.setMaxLines(1);
        l.addView(hn);
        l.addView(words("v", 10, INK_SOFT, true));
        TextView an = words(name(x, false), 11.5f, NAVY, true);
        an.setMaxLines(1);
        l.addView(an);
        l.addView(crest(AdBreakData.awayCrest(x), 15));
        l.addView(spacer(0), new LinearLayout.LayoutParams(0, 1, 1f));
        return l;
    }

    private View kitRow(String crestUrl, String name, int body, int trim) {
        LinearLayout l = row(6);
        l.addView(crest(crestUrl, 20));
        View shirt = new View(getContext()) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();

            @Override protected void onDraw(Canvas cv) {
                float sx = getWidth() / 38f, sy = getHeight() / 34f;
                path.reset();
                float[][] pts = {{12, 2}, {4, 6}, {0, 14}, {6, 17}, {8, 13}, {8, 33}, {30, 33}, {30, 13}, {32, 17}, {38, 14}, {34, 6}, {26, 2}};
                path.moveTo(pts[0][0] * sx, pts[0][1] * sy);
                for (int i = 1; i < pts.length; i++) path.lineTo(pts[i][0] * sx, pts[i][1] * sy);
                path.quadTo(19 * sx, 7 * sy, 12 * sx, 2 * sy);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(body | 0xFF000000);
                cv.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.6f * sx);
                p.setColor(trim | 0xFF000000);
                cv.drawPath(path, p);
            }
        };
        l.addView(shirt, new LinearLayout.LayoutParams(px(32), px(29)));
        l.addView(words(name, 12, NAVY, true));
        return l;
    }

    /** A player (or the referee) in a white ring: his photo, else his initials on the kit's colours. */
    private View face(Bitmap photo, String name, int[] kit, int sizeDp) {
        FrameLayout f = new FrameLayout(getContext());
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(kit[0]);
        ring.setStroke(px(1.5f), 0xFFFFFFFF);
        f.setBackground(ring);
        f.setLayoutParams(new LinearLayout.LayoutParams(px(sizeDp), px(sizeDp)));
        f.setClipToOutline(true);
        f.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        if (photo != null) {
            ImageView iv = new ImageView(getContext());
            iv.setImageBitmap(photo);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LayoutParams il = new LayoutParams(-1, -1);
            il.setMargins(px(1.5f), px(1.5f), px(1.5f), px(1.5f));
            f.addView(iv, il);
        } else {
            TextView t = words(initials(name), sizeDp * 0.36f, 0xFFFFFFFF, true);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new LayoutParams(-1, -1));
        }
        return f;
    }

    /** A poster's picture in a white ring (Bluesky's), else their initials. */
    private View avatar(String url, String name, int colour, int sizeDp) {
        FrameLayout f = (FrameLayout) face(null, name, new int[]{colour, colour}, sizeDp);
        if (url != null && !url.isEmpty()) {
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LayoutParams il = new LayoutParams(-1, -1);
            il.setMargins(px(1.5f), px(1.5f), px(1.5f), px(1.5f));
            f.addView(iv, il);
            Ui.load(iv, url);
        }
        return f;
    }

    private static String initials(String name) {
        if (name == null) return "";
        StringBuilder s = new StringBuilder();
        for (String p : name.trim().split("\\s+")) if (!p.isEmpty() && s.length() < 2 && Character.isLetter(p.charAt(0))) s.append(Character.toUpperCase(p.charAt(0)));
        return s.toString();
    }

    private int[] kit(boolean home) {
        Game g = k.game;
        Espn.Summary s = k.summary;
        int[] kits = Glyphs.kits(s == null ? "" : s.homeKit, s == null ? "" : s.homeKitAlt, g.homeColor, s == null ? "" : s.awayKit, s == null ? "" : s.awayKitAlt, g.awayColor);
        int c = home ? kits[0] : kits[1];
        return new int[]{c, c};
    }

    /** An opponent's place: the top six hard (red), mid-table (amber), lower down easier (green); grey unknown. */
    private static int hardness(String place) {
        int p = AdBreak.number(place);
        if (p <= 0) return 0xFF8F97B3;
        return p <= 6 ? LOSS : p <= 14 ? 0xFFC98A12 : WIN;
    }

    private static boolean contains(List<String[]> rows, String id) {
        for (String[] r : rows) if (r[AdBreak.T_ID].equals(id)) return true;
        return false;
    }

    private static String name(Game g, boolean home) {
        String n = home ? g.homeName : g.awayName;
        return n != null && !n.isEmpty() ? n : home ? safe(g.home) : safe(g.away);
    }

    private static String month(String iso) {
        long at = AdBreak.when(iso);
        if (at <= 0) return iso.length() >= 4 ? iso.substring(0, 4) : "";
        return java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK).format(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static List<View> list(View... vs) {
        List<View> out = new ArrayList<>();
        for (View v : vs) if (v != null) out.add(v);
        return out;
    }

    private int px(float v) {
        return Math.round(v * dp);
    }

    /** Whether there is anything to show at all. */
    static boolean worth(AdBreakData.Content k) {
        return k != null && k.game != null;
    }
}
