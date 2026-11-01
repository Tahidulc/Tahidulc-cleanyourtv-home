package com.gathin.tvhome;

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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Goals, red cards, VAR, full time, F1 and fights as small cards in the line-up card's style (Sky Sports' Premier
 * League graphics; F1 in Sky Sports F1's red and charcoal; the UFC in its black and red; boxing's news in black and
 * gold). Over an app: a card in the bottom left corner, a navy band with
 * the competition and the club, a slanted label (GOAL, RED, VAR, FULL TIME, LAP 34), the player head and shoulders,
 * his name, the score in a navy block, and where it is on with "Press OK to watch". On Home: the same in a bar along
 * the top in the score bar's place, the pills stepping aside. One at a time; OK watches, Back closes (both through
 * the key helper).
 */
final class ScoreCard {
    static final class Data {
        boolean f1;
        /** Charcoal with white letters rather than pale with navy (F1, fights). */
        boolean dark;
        /** The band's two colours (0: the navy, or F1's red), and whether its logo is a wide wordmark (F1, UFC). */
        int band, bandDeep;
        boolean wideMark;
        /** The end block's colour (0: the navy, or F1's red). */
        int block;
        /** A fight's result in the end block: how it was won ("KO/TKO"), and when ("R2 3:12"). */
        String method, methodSmall;
        /** A headline in place of a name (boxing's news), with its picture. */
        String headline;
        Bitmap picture;
        /** The entries are two fighters, a "v" between them. */
        boolean versus;
        /** F1: the drivers behind the leader, each {"P2", surname, team logo}, shown with their team's logo. */
        final List<Object[]> others = new ArrayList<>();
        /** goal, red, var, pen, ft, f1 or fight: how it moves. */
        String kind = "";
        /** For a goal: the score before it, which turns over to the new one. */
        String scoreBefore;
        /** The label's words, a small line under them (or null), and its colour. */
        String label, labelSmall;
        int labelColour;
        Bitmap leagueLogo, crest;
        /** A kick-off's time (for "Kick-off in 5 mins" on Home's bar); 0 otherwise. */
        long kickoffAt;
        /** The player: head and shoulders (a cut-out), his first name and surname, and a line under them. */
        Bitmap photo;
        String first = "", last = "", line = "";
        /** The score block: both crests, the score and the minute ("FT"); or for F1 "P1" and the team's logo. */
        Bitmap homeCrest, awayCrest, teamLogo;
        String homeName, awayName, score, minute, position;
        /** F1 results: up to three {position, photo, team logo, surname, line}. */
        final List<Object[]> podium = new ArrayList<>();
        /** Where it is on (its logo, or its name), and what OK does; null without anywhere to watch. */
        Bitmap channelLogo;
        String channel;
        Runnable watch;
    }

    private static final int NAVY = 0xFF151A63;
    /**
     * The words on the pale ground: a deeper navy than the blocks' and solid (no see-through), with the line under the
     * name in a lighter but still solid navy, so they read clearly from the sofa.
     */
    private static final int INK = 0xFF0A0D33, INK_SOFT = 0xFF2B3170;
    private static final long SHOW_MS = 9_000L, WATCH_MS = 11_000L;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<Data> queue = new ArrayDeque<>();
    private static FrameLayout host;
    private static Data showing;
    private static boolean onHome;
    private static float k;
    /** The score's tick and Home's light, waiting to run; dropped when the card goes first. */
    private static final List<Runnable> later = new ArrayList<>();
    /** The band's stripes drifting, stopped when the card goes. */
    private static final List<android.animation.Animator> moving = new ArrayList<>();

    private ScoreCard() { }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    /** Queues a card; shown when none is showing (and the line-up card is not). On the main thread. */
    static void show(Context ctx, Data d) {
        queue.add(d);
        next(ctx.getApplicationContext());
    }

    /** The corner card's height while one shows over an app (the camera card goes above it); 0 otherwise. */
    static int shownHeight() {
        return host != null && !onHome ? host.getHeight() : 0;
    }

    /** Whether one is showing (GoalWatchService waits its own alerts meanwhile). */
    static boolean busy() {
        return host != null;
    }

    private static void next(Context c) {
        if (host != null || queue.isEmpty()) return;
        if (LineupCard.isShowing() || GoalWatchService.alertShowing() || CameraAlert.isShowing()) {
            main.postDelayed(() -> next(c), 2_000L);
            return;
        }
        Data d = queue.poll();
        if (d.f1) { d.dark = true; d.wideMark = true; }
        onHome = GoalWatchService.laneHome && GoalWatchService.homeInFront;
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        if (onHome) bar(c, d); else card(c, d);
    }

    /** OK (the channel) and Back (closes) while one shows; true when it took the key. */
    static boolean key(KeyEvent e) {
        Data d = showing;
        if (host == null || d == null) return false;
        int code = e.getKeyCode();
        boolean back = code == KeyEvent.KEYCODE_BACK;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (!back && !(ok && d.watch != null)) return false;
        if (back) {
            // The card's Back key dips as the button goes down; as it comes up the card shrinks away towards it.
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) main.post(() -> PopupMotion.press(host));
            else if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> close(true, true));
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> {
            close(true);
            d.watch.run();
        });
        return true;
    }

    // ---- over an app: the card in the corner ----------------------------------------------------------------------

    private static void card(Context c, Data d) {
        int h = px(80);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        float r = px(12);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);

        // The band: the competition and the club; for F1 its own mark on red.
        LinearLayout band = new LinearLayout(c);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setGravity(Gravity.CENTER);
        band.setBackground(bandGround(d, false));
        if (d.leagueLogo != null) {
            ImageView l = image(c, d.leagueLogo);
            band.addView(l, new LinearLayout.LayoutParams(d.wideMark ? px(68) : px(22), d.wideMark ? px(30) : px(22)));
        }
        if (d.crest != null && !d.f1) {
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(32), px(32));
            cl.topMargin = px(5);
            band.addView(image(c, d.crest), cl);
        }
        int bandW = d.wideMark ? px(86) : px(56);
        FrameLayout bandBox = new FrameLayout(c);
        bandBox.setBackground(band.getBackground());
        band.setBackground(null);
        bandBox.addView(stripes(c, d), new FrameLayout.LayoutParams(bandW + STRIPE_PERIOD_PX(), h));
        bandBox.addView(band, new FrameLayout.LayoutParams(bandW, h));
        card.addView(bandBox, new LinearLayout.LayoutParams(bandW, h));

        // The stage: pale streaks for football, charcoal for F1.
        LinearLayout stage = new LinearLayout(c);
        stage.setOrientation(LinearLayout.HORIZONTAL);
        stage.setGravity(Gravity.CENTER_VERTICAL);
        // Its ground is the box's (below), with the streaks drifting across it on a layer of their own.
        View label = label(c, d, 25, h);
        stage.addView(label, new LinearLayout.LayoutParams(-2, h));
        View photo = null;
        if (d.podium.isEmpty()) {
            if (d.photo != null) {
                photo = photo(c, d.photo, px(74), h, px(102), px(-14), px(-1));
                stage.addView(photo, new LinearLayout.LayoutParams(px(74), h));
            }
            if (d.picture != null) {
                LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(104), px(58));
                pl.leftMargin = px(4);
                stage.addView(picture(c, d.picture, px(8)), pl);
            }
            stage.addView(who(c, d, 12, 21, 12), whoParams(photo == null && d.picture == null));
            // A kick-off's crests a size up: they are the card's picture.
            int crestPx = "kickoff".equals(d.kind) ? 36 : 26;
            if (d.score != null || d.position != null || d.method != null) stage.addView(scoreBlock(c, d, h, crestPx, 26), new LinearLayout.LayoutParams(-2, h));
        } else {
            for (Object[] p : d.podium) {
                if (d.versus && p != d.podium.get(0)) stage.addView(text(c, "v", 20, 0xB3FFFFFF, outfit(c, 600)), new LinearLayout.LayoutParams(-2, -2));
                View e = podiumEntry(c, p, h);
                e.setTag("podium");
                stage.addView(e, new LinearLayout.LayoutParams(-2, h));
            }
            View gap = new View(c);
            stage.addView(gap, new LinearLayout.LayoutParams(px(6), h));
        }
        if (d.watch != null) stage.addView(watchBlock(c, d, h, 15, 10.5f), new LinearLayout.LayoutParams(-2, h));
        card.addView(stage, new LinearLayout.LayoutParams(-2, h));

        FrameLayout box = new FrameLayout(c);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        box.setClipToOutline(true);
        // No shadow: over a bright picture it shows as a dark rim round the card.
        box.setBackground(stageFill(d, px(40), px(26)));
        box.addView(drifting(c, d, px(40), px(26), h), driftParams(px(40), px(26), h));
        box.addView(card, new FrameLayout.LayoutParams(-2, h));
        FrameLayout win = new FrameLayout(c);
        int m = px(10);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-2, h);
        fl.setMargins(m, m, m, m);
        win.addView(box, fl);
        // The remote's Back key on the top right corner: Back closes it.
        win.addView(backKey(c, 2 * m), new FrameLayout.LayoutParams(2 * m, 2 * m, Gravity.TOP | Gravity.END));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, h + 2 * m,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = px(34);
        lp.y = px(34);
        lp.setTitle("ScoreCard");
        if (!add(c, win, lp, d)) return;
        enter(box, -px(36), 0);
        alive(c, d, box, h);
        schedule(d);
    }

    // ---- on Home: the bar in the score bar's place ---------------------------------------------------------------

    private static void bar(Context c, Data d) {
        int h = px(52), w = px(1728);
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        float r = h / 2f;
        bar.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        bar.setClipToOutline(true);
        // Its ground is the box's (below), with the streaks drifting across it on a layer of their own.
        boolean ko = "kickoff".equals(d.kind);
        FrameLayout band = new FrameLayout(c);
        band.setBackground(bandGround(d, false));
        band.addView(stripes(c, d), new FrameLayout.LayoutParams((d.wideMark ? px(150) : px(120)) + STRIPE_PERIOD_PX(), h));
        if (d.leagueLogo != null) band.addView(image(c, d.leagueLogo), new FrameLayout.LayoutParams(d.wideMark ? px(124) : px(40), px(40), Gravity.CENTER));
        bar.addView(band, new LinearLayout.LayoutParams(d.wideMark ? px(150) : px(120), h));
        // A kick-off's label is KICK-OFF alone: how long until it is said once, after the clubs.
        String small = d.labelSmall;
        if (ko) d.labelSmall = null;
        View label = label(c, d, 23, h);
        d.labelSmall = small;
        bar.addView(label, new LinearLayout.LayoutParams(-2, h));
        if (d.photo != null && d.podium.isEmpty()) bar.addView(photo(c, d.photo, px(66), h, px(86), px(-10), px(-3)), new LinearLayout.LayoutParams(px(66), h));
        if (d.picture != null) {
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(80), px(46));
            pl.leftMargin = px(8);
            bar.addView(picture(c, d.picture, px(6)), pl);
        }
        if (d.podium.isEmpty()) {
            if (d.teamLogo != null && d.f1) {
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(30), px(30));
                tl.leftMargin = px(6);
                bar.addView(image(c, d.teamLogo), tl);
            }
            LinearLayout name = new LinearLayout(c);
            name.setGravity(Gravity.CENTER_VERTICAL);
            // Full time: the result big ("Chelsea win"), the scorers and any red cards after it, small, as on the card
            // (its first line is the scorers, not a first name).
            boolean ft = "ft".equals(d.kind);
            // A kick-off: "Tottenham Hotspur v Crystal Palace", then "Kick-off in 5 mins", then the crests (no names
            // again beside them). A surname ("SALAH") is title-cased; a result or a kick-off reads as it is.
            String big = d.headline != null ? d.headline : ko ? d.homeName + " v " + d.awayName : ft ? d.last
                    : (d.first.isEmpty() ? "" : d.first + " ") + titleCase(d.last);
            String after = ko ? kickoffIn(d) : ft ? join(d.first, d.line) : d.others.isEmpty() ? d.line : "";
            TextView bigText = text(c, big, d.headline != null ? 19 : 22, d.dark ? 0xFFFFFFFF : INK, d.headline != null ? outfit(c, 800) : outfit(c, 600));
            if (d.headline != null) bigText.setMaxWidth(px(1000));
            name.addView(bigText);
            if (!after.isEmpty()) {
                TextView after2 = text(c, after, ko ? 18 : 16, d.dark ? 0xCCFFFFFF : INK_SOFT, outfit(c, ko ? 600 : 500));
                if (ft) after2.setMaxWidth(px(820));
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
                sl.leftMargin = px(ko ? 16 : 10);
                name.addView(after2, sl);
                if (ko) {
                    // "Kick-off in 5 mins" fades in slowly once the bar is in.
                    after2.setAlpha(0f);
                    after2.animate().alpha(1f).setStartDelay(700).setDuration(1_400)
                            .setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
                }
            }
            for (Object[] o : d.others) name.addView(driver(c, o, 16, 22), driverParams(14));
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
            nl.leftMargin = px(10);
            bar.addView(name, nl);
        } else {
            for (Object[] p : d.podium) {
                if (d.versus && p != d.podium.get(0)) {
                    LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
                    vl.leftMargin = px(18);
                    bar.addView(text(c, "v", 20, 0xB3FFFFFF, outfit(c, 600)), vl);
                }
                LinearLayout e = new LinearLayout(c);
                e.setGravity(Gravity.CENTER_VERTICAL);
                if (!((String) p[0]).isEmpty()) e.addView(text(c, (String) p[0], 20, 0xFFFFFFFF, outfit(c, 900)));
                if (p[1] != null) {
                    LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(px(44), h);
                    fl.leftMargin = px(6);
                    e.addView(photo(c, (Bitmap) p[1], px(44), h, px(58), px(-7), px(2)), fl);
                }
                if (p[2] != null) {
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(26), px(26));
                    tl.leftMargin = px(8);
                    e.addView(image(c, (Bitmap) p[2]), tl);
                }
                TextView n = text(c, titleCase((String) p[3]), 20, 0xFFFFFFFF, outfit(c, 800));
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
                nl.leftMargin = px(8);
                e.addView(n, nl);
                LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-2, -2);
                el.leftMargin = px(22);
                bar.addView(e, el);
            }
        }
        View spacer = new View(c);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        if (d.score != null) {
            if (d.homeName != null && !ko) bar.addView(text(c, d.homeName, 20, INK, outfit(c, 800)), sideName(true));
            bar.addView(scoreBlock(c, d, h, ko ? 36 : 28, 25), new LinearLayout.LayoutParams(-2, h));
            if (d.awayName != null && !ko) bar.addView(text(c, d.awayName, 20, INK, outfit(c, 800)), sideName(false));
        } else if (d.method != null) {
            bar.addView(scoreBlock(c, d, h, 28, 25), new LinearLayout.LayoutParams(-2, h));
        }
        if (d.watch != null) bar.addView(watchBlock(c, d, h, 16, 12), new LinearLayout.LayoutParams(-2, h));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(24), px(24));
        kl.leftMargin = px(12);
        kl.rightMargin = px(14);
        bar.addView(backKey(c, px(24)), kl);

        FrameLayout box = new FrameLayout(c);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        box.setClipToOutline(true);
        box.setBackground(stageFill(d, px(50), px(30)));
        box.addView(drifting(c, d, px(50), px(30), h), driftParams(px(50), px(30), h));
        box.addView(bar, new FrameLayout.LayoutParams(w, h));
        FrameLayout win = new FrameLayout(c);
        win.addView(box, new FrameLayout.LayoutParams(w, h));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = px(14);
        lp.setTitle("ScoreCard");
        if (!add(c, win, lp, d)) return;
        lane(true);
        enter(box, 0, -px(24));
        alive(c, d, box, h);
        schedule(d);
    }

    private static LinearLayout.LayoutParams sideName(boolean home) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        if (home) p.rightMargin = px(4); else { p.leftMargin = px(14); p.rightMargin = px(22); }
        return p;
    }

    // ---- the parts -----------------------------------------------------------------------------------------------

    /** The slanted label: GOAL, RED, VAR, FULL TIME, LAP 34, with its small line under it. */
    private static View label(Context c, Data d, float size, int h) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(px(14), 0, px(21), 0);
        v.setBackground(new Slant(d.labelColour, px(14), false));
        if ("red".equals(d.kind)) {
            // The card itself, held up: it gives a little shake as the label lands.
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View card = new View(c);
            GradientDrawable cd = new GradientDrawable();
            cd.setColor(0xFFE3061B);
            cd.setCornerRadius(px(3));
            card.setBackground(cd);
            card.setTag("redcard");
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(px(size * 0.6f), px(size * 0.85f));
            rl.rightMargin = px(10);
            row.addView(card, rl);
            row.addView(text(c, d.label, size, 0xFFFFFFFF, outfit(c, 900)));
            v.addView(row, wrap());
        } else {
            v.addView(text(c, d.label, size, 0xFFFFFFFF, outfit(c, 900)), wrap());
        }
        if (d.labelSmall != null) {
            TextView s = text(c, d.labelSmall, 10, 0xD9FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            s.setLetterSpacing(0.16f);
            v.addView(s, wrap());
            // The kick-off's "IN 15 MIN" fades in slowly after the card is in.
            if ("kickoff".equals(d.kind) || "reminder".equals(d.kind)) {
                s.setAlpha(0f);
                s.animate().alpha(1f).setStartDelay(700).setDuration(1_400)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.2f)).withLayer().start();
            }
        }
        return v;
    }

    /** The band's ground: the navy, F1's red, or the card's own colours. */
    /** The band's ground: the navy, F1's red, or the card's own colours; its stripes too, or (stripes false) not. */
    private static Drawable bandGround(Data d, boolean withStripes) {
        int a = d.band != 0 ? d.band : d.f1 ? 0xFFE10600 : 0xFF1B2591;
        int b = d.bandDeep != 0 ? d.bandDeep : d.f1 ? 0xFF8E0400 : 0xFF0D1360;
        return new Streaks(a, b, withStripes ? stripeColour(d) : 0, px(14), px(3));
    }

    private static int stripeColour(Data d) {
        return d.f1 || d.band != 0 ? 0x1AFFFFFF : 0x17FFFFFF;
    }

    /** One stripe and its gap, the distance the band's stripes drift in one beat. */
    private static int STRIPE_PERIOD_PX() {
        return Math.max(4, px(14)) + Math.max(1, px(3));
    }

    /**
     * The band's light stripes on their own, a period wider than the band: drawn once into a layer, so for a goal or
     * an incident they can drift along slightly (alive()) with nothing redrawn.
     */
    private static View stripes(Context c, Data d) {
        View v = new View(c);
        v.setBackground(new Streaks(0, 0, stripeColour(d), px(14), px(3)));
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        v.setTranslationX(-STRIPE_PERIOD_PX());
        v.setTag("stripes");
        return v;
    }

    /** The stage's ground without its streaks: pale, or charcoal for F1 and fights. */
    private static Drawable stageFill(Data d, int gap, int width) {
        return d.dark ? new Streaks(0xFF2A2B33, 0xFF16171D, 0, gap, width) : new Streaks(0xFFF6F7FD, 0xFFD3D6F3, 0, gap, width);
    }

    /**
     * The stage's streaks on a layer of their own, a period wider than the pop-up (its params start a period to the
     * left), drifting one period to the right and again while it shows: drawn once, only the layer moves, so the
     * picture under it is not redrawn. Stopped when it closes.
     */
    private static View drifting(Context c, Data d, int gap, int width, int h) {
        View lines = new View(c);
        lines.setBackground(new Streaks(0, 0, d.dark ? 0x14FFFFFF : 0x4DFFFFFF, gap, width));
        lines.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        int period = Math.max(4, gap) + Math.max(1, width);
        android.animation.ObjectAnimator drift = android.animation.ObjectAnimator.ofFloat(lines, View.TRANSLATION_X, 0f, period);
        drift.setDuration(Math.round(period / (float) px(30) * 1_000));
        drift.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        drift.setInterpolator(new android.view.animation.LinearInterpolator());
        drift.setStartDelay(300);
        moving.add(drift);
        drift.start();
        return lines;
    }

    private static FrameLayout.LayoutParams driftParams(int gap, int width, int h) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, h);
        p.leftMargin = -(Math.max(4, gap) + Math.max(1, width));
        return p;
    }

    /** "Kick-off in 5 mins" from a kick-off's time; its line as it was without one. */
    private static String kickoffIn(Data d) {
        if (d.kickoffAt <= 0) return d.line == null ? "" : d.line;
        long min = Math.round((d.kickoffAt - System.currentTimeMillis()) / 60_000.0);
        if (min <= 0) return "Kicking off now";
        if (min < 60) return "Kick-off in " + min + (min == 1 ? " min" : " mins");
        return "Kick-off " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(new java.util.Date(d.kickoffAt));
    }

    /** A news picture, cropped to fill its box, with rounded corners. */
    private static View picture(Context c, Bitmap b, int radius) {
        ImageView i = new ImageView(c);
        i.setImageBitmap(sharp(b));
        i.setScaleType(ImageView.ScaleType.CENTER_CROP);
        i.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        i.setClipToOutline(true);
        return i;
    }

    /** Head and shoulders, the picture placed so the face sits in the box. */
    private static View photo(Context c, Bitmap b, int w, int h, int imageW, int dx, int dy) {
        FrameLayout f = new FrameLayout(c);
        f.setClipChildren(true);
        ImageView i = new ImageView(c);
        i.setImageBitmap(sharp(b));
        // An upright photo (the Premier League's own, framed closer than a square cut-out) a little narrower, at its
        // own height, so the face is the same size as a square one's.
        boolean upright = b != null && b.getHeight() > b.getWidth() * 1.05f;
        int iw = upright ? Math.round(imageW * 0.86f) : imageW, ih = upright ? Math.round(iw * b.getHeight() / (float) b.getWidth()) : imageW;
        i.setScaleType(upright ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_START);
        if (upright) dx += (imageW - iw) / 2;
        FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(iw, ih);
        il.leftMargin = dx;
        il.topMargin = dy;
        f.addView(i, il);
        return f;
    }

    private static View who(Context c, Data d, float small, float big, float line) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        int ink = d.dark ? 0xFFFFFFFF : INK;
        if (d.headline != null) {
            TextView head = text(c, d.headline, 15.5f, ink, outfit(c, 800));
            head.setSingleLine(false);
            head.setMaxLines(2);
            head.setMaxWidth(px(470));
            head.setLineSpacing(0, 1.05f);
            v.addView(head, wrap());
            if (!d.line.isEmpty()) {
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
                ll.topMargin = px(5);
                v.addView(text(c, d.line, 12, d.dark ? 0xB3FFFFFF : INK_SOFT, outfit(c, 600)), ll);
            }
            return v;
        }
        if (!d.first.isEmpty()) v.addView(text(c, d.first, small + 1, d.dark ? 0xFFFFFFFF : INK_SOFT, outfit(c, 500)), wrap());
        v.addView(text(c, d.last.toUpperCase(java.util.Locale.UK), big, ink, outfit(c, 600)), wrap());
        if (!d.others.isEmpty()) {
            // F1: P2 and P3 each with his team's logo.
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < d.others.size(); i++) row.addView(driver(c, d.others.get(i), line, 15), driverParams(i == 0 ? 0 : 10));
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
            rl.topMargin = px(3);
            v.addView(row, rl);
        } else if (!d.line.isEmpty()) {
            TextView l = text(c, d.line, line, d.dark ? 0xD9FFFFFF : INK_SOFT, outfit(c, 500));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = px(3);
            v.addView(l, ll);
        }
        return v;
    }

    private static LinearLayout.LayoutParams whoParams(boolean noPhoto) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = noPhoto ? px(14) : px(6);
        p.rightMargin = px(14);
        return p;
    }

    /** The score in its navy block (crest, score, crest, minute), or for F1 the position and team logo on red. */
    private static View scoreBlock(Context c, Data d, int h, int crest, float size) {
        LinearLayout v = new LinearLayout(c);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(px(22), 0, px(16), 0);
        v.setBackground(new Slant(d.block != 0 ? d.block : d.f1 ? 0xFFE10600 : NAVY, px(14), true));
        if (d.method != null) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(text(c, d.method.toUpperCase(java.util.Locale.UK), size * 0.78f, 0xFFFFFFFF, outfit(c, 900)), wrap());
            if (d.methodSmall != null && !d.methodSmall.isEmpty()) {
                TextView when = text(c, d.methodSmall, 13, 0xD9FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
                when.setLetterSpacing(0.08f);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
                wl.topMargin = px(2);
                col.addView(when, wl);
            }
            v.addView(col);
            return v;
        }
        if (d.f1) {
            v.addView(text(c, d.position == null ? "" : d.position, 22, 0xFFFFFFFF, outfit(c, 900)));
            if (d.teamLogo != null) {
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(34), px(34));
                tl.leftMargin = px(10);
                v.addView(image(c, d.teamLogo), tl);
            }
            return v;
        }
        if (d.homeCrest != null) v.addView(image(c, d.homeCrest), new LinearLayout.LayoutParams(px(crest), px(crest)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
        sl.leftMargin = sl.rightMargin = px(10);
        v.addView(score(c, d, size), sl);
        if (d.awayCrest != null) v.addView(image(c, d.awayCrest), new LinearLayout.LayoutParams(px(crest), px(crest)));
        if (d.minute != null && !d.minute.isEmpty()) {
            TextView mn = text(c, d.minute, 14, 0xD9FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
            ml.leftMargin = px(10);
            v.addView(mn, ml);
        }
        return v;
    }

    /**
     * The score, each side's number in its own window. After a goal the card opens on the score before it, and the
     * scorer's side holds both numbers, the new one waiting below, to roll up into place (tagged "roll").
     */
    private static View score(Context c, Data d, float size) {
        String[] now = sides(d.score), before = sides(d.scoreBefore);
        if (now == null) {
            TextView s = text(c, d.score, size, 0xFFFFFFFF, outfit(c, 900));
            s.setLetterSpacing(0.04f);
            return s;
        }
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 2; i++) {
            if (i == 1) {
                TextView dash = text(c, "–", size, 0xFFFFFFFF, outfit(c, 900));
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-2, -2);
                row.addView(dash, dl);
            }
            FrameLayout cell = new FrameLayout(c);
            boolean rolls = before != null && !before[i].equals(now[i]);
            TextView shown = text(c, rolls ? before[i] : now[i], size, 0xFFFFFFFF, outfit(c, 900));
            shown.setGravity(Gravity.CENTER);
            shown.setPadding(px(4), 0, px(4), 0);
            cell.addView(shown, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
            if (rolls) {
                TextView next = text(c, now[i], size, 0xFFFFFFFF, outfit(c, 900));
                next.setGravity(Gravity.CENTER);
                next.setPadding(px(4), 0, px(4), 0);
                next.setVisibility(View.INVISIBLE);
                cell.addView(next, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
                cell.setTag("roll");
            }
            row.addView(cell, new LinearLayout.LayoutParams(-2, -2));
        }
        return row;
    }

    /** "2–1" as {"2", "1"}; null for anything else. */
    private static String[] sides(String score) {
        if (score == null) return null;
        String[] p = score.trim().split("\\s*[–—-]\\s*");
        return p.length == 2 && !p[0].isEmpty() && !p[1].isEmpty() ? p : null;
    }

    /** One of an F1 result's top three: his position, head and shoulders, team logo and surname, his gap. */
    private static View podiumEntry(Context c, Object[] p, int h) {
        LinearLayout v = new LinearLayout(c);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(px(10), 0, px(10), 0);
        if (!((String) p[0]).isEmpty()) v.addView(text(c, (String) p[0], 20, 0xFFFFFFFF, outfit(c, 900)), new LinearLayout.LayoutParams(px(23), -2));
        if (p[1] != null) v.addView(photo(c, (Bitmap) p[1], px(54), h, px(78), px(-12), px(2)), new LinearLayout.LayoutParams(px(54), h));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (p[2] != null) top.addView(image(c, (Bitmap) p[2]), new LinearLayout.LayoutParams(px(22), px(22)));
        TextView n = text(c, ((String) p[3]).toUpperCase(java.util.Locale.UK), 16, 0xFFFFFFFF, outfit(c, 800));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.leftMargin = px(6);
        top.addView(n, nl);
        col.addView(top, wrap());
        if (p[4] != null) {
            TextView g = text(c, (String) p[4], 11, 0xB3FFFFFF, outfit(c, 600));
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-2, -2);
            gl.leftMargin = px(30);
            col.addView(g, gl);
        }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
        cl.leftMargin = px(6);
        v.addView(col, cl);
        return v;
    }

    /** Where it is on and "Press OK to watch", on the darkest ground. */
    private static View watchBlock(Context c, Data d, int h, float logoH, float textPx) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(onHome ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setPadding(px(onHome ? 30 : 24), 0, px(onHome ? 22 : 16), 0);
        v.setBackground(new Slant(0xFF0E0F14, px(14), true));
        if (d.channelLogo != null) {
            ImageView l = image(c, d.channelLogo);
            l.setAdjustViewBounds(true);
            l.setMaxWidth(px(logoH * 9));
            v.addView(l, new LinearLayout.LayoutParams(-2, px(logoH)));
        } else if (d.channel != null) {
            v.addView(text(c, d.channel, logoH * 0.8f, 0xFFFFFFFF, outfit(c, 800)));
        }
        LinearLayout pill = new LinearLayout(c);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(px(12));
        pill.setBackground(bg);
        pill.setPadding(px(9), px(2), px(9), px(2));
        pill.addView(text(c, "Press", textPx, 0xFF111214, outfit(c, 700)));
        TextView key = text(c, "OK", textPx * 0.6f, 0xFFFFFFFF, outfit(c, 900));
        key.setGravity(Gravity.CENTER);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFF111214);
        key.setBackground(disc);
        int ks = px(textPx * 1.5f);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ks, ks);
        kl.leftMargin = kl.rightMargin = px(4);
        pill.addView(key, kl);
        pill.addView(text(c, "to watch", textPx, 0xFF111214, outfit(c, 700)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
        if (onHome) pl.leftMargin = px(14); else pl.topMargin = px(7);
        v.addView(pill, pl);
        return v;
    }

    // ---- the movement -------------------------------------------------------------------------------------------

    /** In: one short slide and fade, drawn once into a layer so only that layer moves. */
    private static void enter(View box, float dx, float dy) {
        box.setAlpha(0f);
        box.setTranslationX(dx);
        box.setTranslationY(dy);
        box.animate().alpha(1f).translationX(0).translationY(0).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
    }

    /**
     * The one bit of movement after it lands: a goal's score ticks over from the one before; on Home a band of light
     * also crosses the bar, once. Nothing repeats. Over a playing channel every frame that moves makes the TV compose
     * the picture on the graphics chip, and the video drops frames, so the card holds still once it is in.
     */
    private static void alive(Context c, Data d, FrameLayout box, int h) {
        // A goal: the scorer's number rolls up out of its window and the new one rolls up in, then gives a little pop.
        View roll = box.findViewWithTag("roll");
        if (roll instanceof FrameLayout && ((FrameLayout) roll).getChildCount() == 2) {
            FrameLayout cell = (FrameLayout) roll;
            View old = cell.getChildAt(0), next = cell.getChildAt(1);
            Runnable tick = () -> {
                float up = cell.getHeight();
                next.setTranslationY(up);
                next.setVisibility(View.VISIBLE);
                old.animate().translationY(-up).setDuration(340).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                next.animate().translationY(0).setDuration(340).setInterpolator(new DecelerateInterpolator(1.6f))
                        .withEndAction(() -> next.animate().scaleX(1.22f).scaleY(1.22f).setDuration(110)
                                .withEndAction(() -> next.animate().scaleX(1f).scaleY(1f).setDuration(220)
                                        .setInterpolator(new DecelerateInterpolator(2f)).start()).start()).start();
            };
            later.add(tick);
            main.postDelayed(tick, 800L);
        }
        // Every pop-up: the band's stripes drift along, slightly, while it shows.
        View lines = box.findViewWithTag("stripes");
        if (lines != null) {
            android.animation.ObjectAnimator drift = android.animation.ObjectAnimator.ofFloat(lines, View.TRANSLATION_X,
                    -STRIPE_PERIOD_PX(), 0f);
            drift.setDuration(1_400);
            drift.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            drift.setInterpolator(new android.view.animation.LinearInterpolator());
            moving.add(drift);
            drift.start();
        }
        if (!onHome) return;
        View sheen = new View(c);
        sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF}));
        sheen.setRotation(18);
        sheen.setTranslationX(-px(160));
        box.addView(sheen, new FrameLayout.LayoutParams(px(70), h * 2, Gravity.CENTER_VERTICAL));
        Runnable sweep = () -> sheen.animate().translationX(box.getWidth() + px(80)).setDuration(900)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> box.removeView(sheen)).start();
        later.add(sweep);
        main.postDelayed(sweep, 450L);
    }

    // ---- window, timing, closing ---------------------------------------------------------------------------------

    private static boolean add(Context c, FrameLayout win, WindowManager.LayoutParams lp, Data d) {
        // Without the key helper (the TCL), the corner card over an app takes the remote's keys while it shows: OK
        // watches (when there is a channel), Back or any other key closes it. Home's bar leaves Home its keys.
        boolean takeKeys = !InputKeyService.running && !onHome;
        if (takeKeys) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            for (android.animation.Animator a : moving) a.cancel();
            moving.clear();
            main.post(() -> next(c));
            return false;
        }
        host = win;
        showing = d;
        if (takeKeys) {
            win.setFocusable(true);
            win.setFocusableInTouchMode(true);
            win.requestFocus();
            final boolean[] down = {false};
            win.setOnKeyListener((v, code, e) -> {
                // Only a press that began here: a key held as the card came belongs to the app.
                if (e.getAction() == KeyEvent.ACTION_DOWN) { down[0] = true; return true; }
                if (e.getAction() != KeyEvent.ACTION_UP || !down[0]) return true;
                boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
                close(true, code == KeyEvent.KEYCODE_BACK);
                if (ok && d.watch != null) d.watch.run();
                return true;
            });
        }
        return true;
    }

    private static void schedule(Data d) {
        FrameLayout win = host;
        main.postDelayed(() -> { if (host == win) close(true); }, d.watch != null ? WATCH_MS : SHOW_MS);
    }

    /** The card away (faded, or at once), the score bar back on Home, and the next waiting one. */
    static void close(boolean fade) {
        close(fade, false);
    }

    /** As close(fade); byBack: closed by Back, so it shrinks away towards its Back key instead of sliding out. */
    private static void close(boolean fade, boolean byBack) {
        for (Runnable r : later) main.removeCallbacks(r);
        later.clear();
        for (android.animation.Animator a : moving) a.cancel();
        moving.clear();
        FrameLayout v = host;
        host = null;
        showing = null;
        if (v == null) return;
        Context c = v.getContext().getApplicationContext();
        boolean wasHome = onHome;
        Runnable gone = () -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
            if (wasHome) lane(false);
            main.postDelayed(() -> next(c), 400L);
        };
        if (!fade) { gone.run(); return; }
        View content = v.getChildAt(0);
        if (content == null) { gone.run(); return; }
        // Not focusable while it fades, so the next key already goes to the app.
        v.setOnKeyListener(null);
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            if ((lp.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                v.getContext().getSystemService(WindowManager.class).updateViewLayout(v, lp);
            }
        } catch (RuntimeException ignored) { }
        if (byBack) {
            // Towards the Back key: the card's top right corner, or the bar's right end.
            PopupMotion.backOut(v, v.getWidth(), wasHome ? v.getHeight() / 2f : 0f, gone);
            return;
        }
        content.animate().alpha(0f).translationX(wasHome ? 0 : -px(30)).translationY(wasHome ? -px(18) : 0)
                .setDuration(220).withLayer().withEndAction(gone).start();
    }

    /** Home's score pills step aside while the bar holds their place, and come back after. */
    static void lane(boolean on) {
        java.util.function.Consumer<Boolean> l = GoalWatchService.laneListener;
        if (l != null) l.accept(on);
    }

    // ---- small helpers -------------------------------------------------------------------------------------------

    /**
     * Wrap-content in both directions. A vertical LinearLayout's own default for a child is MATCH_PARENT, which, with
     * the column wrapping its content, cuts a long name to the width of a shorter line given explicit params under it.
     */
    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    /**
     * A picture drawn small from a bigger one (a crest, a logo, a player) stays sharp: with mip-maps the graphics chip
     * scales it down smoothly, where plain filtering at a quarter of its size leaves it jagged and shimmering.
     */
    private static Bitmap sharp(Bitmap b) {
        if (b != null) b.setHasMipMap(true);
        return b;
    }

    /**
     * A crest for the navy: one that is nearly all dark (Spurs' navy cockerel, Everton's) gets a thin white keyline
     * round it, as on TV graphics, or it vanishes into the navy; any other crest as it is. Null for null.
     */
    static Bitmap onNavy(Bitmap b) {
        if (b == null) return null;
        Bitmap small = Bitmap.createScaledBitmap(b, 48, 48, true);
        int opaque = 0, dark = 0;
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 48; x++) {
                int p = small.getPixel(x, y);
                if ((p >>> 24) < 128) continue;
                opaque++;
                double lum = (0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF) + 0.114 * (p & 0xFF)) / 255.0;
                if (lum < 0.28) dark++;
            }
        }
        if (small != b) small.recycle();
        if (opaque == 0 || dark < opaque * 0.55) return b;
        int pad = Math.max(2, Math.round(b.getWidth() / 34f));
        Bitmap out = Bitmap.createBitmap(b.getWidth() + 2 * pad, b.getHeight() + 2 * pad, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Paint white = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        white.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
        // The crest's outline in white, drawn all round it, then the crest on top.
        for (int a = 0; a < 360; a += 30) {
            double r = Math.toRadians(a);
            c.drawBitmap(b, pad + (float) (Math.cos(r) * pad), pad + (float) (Math.sin(r) * pad), white);
        }
        c.drawBitmap(b, pad, pad, null);
        return out;
    }

    /** The remote's Back key (the theme's ok_button remote), drawn at its size. */
    private static View backKey(Context c, int size) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), size));
        v.setTag(PopupMotion.BACK_KEY);
        return v;
    }

    private static ImageView image(Context c, Bitmap b) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(sharp(b));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return v;
    }

    private static TextView text(Context c, String s, float sizePx, int colour, Typeface face) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * k);
        v.setTextColor(colour);
        v.setTypeface(face);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    /** Outfit at each weight the cards use, read once each. */
    private static final java.util.Map<Integer, Typeface> outfits = new java.util.HashMap<>();

    /**
     * Outfit, the launcher's display face, at a weight (500 to 900). Outfit is a variable font: asking the font file
     * itself for a weight (Typeface.create) draws its thin default instance, so each weight is its own font resource
     * with the weight set in it (outfit_medium.xml to outfit_black.xml).
     */
    static Typeface outfit(Context c, int weight) {
        Typeface t = outfits.get(weight);
        if (t != null) return t;
        int res = weight >= 900 ? R.font.outfit_black : weight >= 800 ? R.font.outfit_extrabold : weight >= 700 ? R.font.outfit_bold
                : weight >= 600 ? R.font.outfit_semibold : R.font.outfit_medium;
        try { t = c.getResources().getFont(res); } catch (RuntimeException e) { t = Typeface.create("sans-serif", weight >= 700 ? Typeface.BOLD : Typeface.NORMAL); }
        outfits.put(weight, t);
        return t;
    }

    /** One driver behind the leader: "P2", his team's logo, his surname. */
    private static View driver(Context c, Object[] o, float size, int logoPx) {
        LinearLayout e = new LinearLayout(c);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.addView(text(c, (String) o[0], size, 0xB3FFFFFF, outfit(c, 600)));
        if (o[2] != null) {
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(logoPx), px(logoPx));
            ll.leftMargin = px(5);
            e.addView(image(c, (Bitmap) o[2]), ll);
        }
        TextView n = text(c, (String) o[1], size, 0xFFFFFFFF, outfit(c, 600));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, -2);
        nl.leftMargin = px(5);
        e.addView(n, nl);
        return e;
    }

    private static LinearLayout.LayoutParams driverParams(int left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = px(left);
        return p;
    }

    /** Two lines' words as one, a dot between them when both have any. */
    private static String join(String a, String b) {
        a = a == null ? "" : a.trim();
        b = b == null ? "" : b.trim();
        return a.isEmpty() ? b : b.isEmpty() ? a : a + "  ·  " + b;
    }

    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (String w : s.toLowerCase(java.util.Locale.UK).split(" ")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.equals("v") ? "v" : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return b.toString();
    }

    /** A colour block with one slanted edge: the right (a label) or the left (a block at the end). */
    static final class Slant extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int slant;
        private final boolean left;

        Slant(int colour, int slant, boolean left) {
            p.setColor(colour);
            this.slant = slant;
            this.left = left;
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            Path path = new Path();
            if (left) {
                path.moveTo(r.left + slant, r.top);
                path.lineTo(r.right, r.top);
                path.lineTo(r.right, r.bottom);
                path.lineTo(r.left, r.bottom);
            } else {
                path.moveTo(r.left, r.top);
                path.lineTo(r.right, r.top);
                path.lineTo(r.right - slant, r.bottom);
                path.lineTo(r.left, r.bottom);
            }
            path.close();
            c.drawPath(path, p);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A vertical gradient with diagonal bands, as the line-up card's ground. */
    private static final class Streaks extends Drawable {
        private final Paint fill = new Paint(), band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int top, bottom, gap, width;

        Streaks(int top, int bottom, int bandColour, int gap, int width) {
            this.top = top;
            this.bottom = bottom;
            this.gap = Math.max(4, gap);
            this.width = Math.max(1, width);
            band.setColor(bandColour);
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(r, fill);
            float slant = r.height() * 0.4f;
            Path p = new Path();
            for (float x = r.left - slant; x < r.right + slant; x += gap + width) {
                p.reset();
                p.moveTo(x, r.bottom);
                p.lineTo(x + width, r.bottom);
                p.lineTo(x + width + slant, r.top);
                p.lineTo(x + slant, r.top);
                p.close();
                c.drawPath(p, band);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
