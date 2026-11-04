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
import android.view.ViewGroup;
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
        /** A wordmark that must read big (the BBC's blocks and NEWS/SPORT): a wider band, the mark filling it. */
        boolean bigMark;
        /** The end block's colour (0: the navy, or F1's red). */
        int block;
        /** A fight's result in the end block: how it was won ("KO/TKO"), and when ("R2 3:12"). */
        String method, methodSmall;
        /** The words after "Press OK" ("to watch" unless given). */
        String okWords = "to watch";
        /** A headline in place of a name (boxing's news), with its picture. */
        String headline;
        Bitmap picture;
        /** The entries are two fighters, a "v" between them. */
        boolean versus;
        /** F1: the drivers behind the leader, each {"P2", surname, team logo}, shown with their team's logo. */
        final List<Object[]> others = new ArrayList<>();
        /** goal, red, var, pen, ft, f1 or fight: how it moves. */
        String kind = "";
        /**
         * Its kind in the Control Centre's Notifications (NotifyKinds: "tennis", "messages"; NotifyKinds.ALWAYS for a
         * question or a code); null: worked out from kind.
         */
        String notify;
        /** The goal's, red card's or full time's key (ScoresTicker), for Today's alerts; null for anything else. */
        String key;
        /**
         * The game's competition, its ESPN slug ("eng.1"), for its switch in Notifications (GoalWatchService.footballData
         * sets it); null for anything but a game's card, and for a reminder the user set (it always comes).
         */
        String slug;
        /** A game of one of the starred clubs (goal_alert_teams, Man Utd): its goals come through a film night's quiet. */
        boolean starred;
        /** Shown again from Today's alerts: always the full card (Home's bar would be under its panel). */
        boolean again;
        /** For a goal: the score before it, which turns over to the new one. */
        String scoreBefore;
        /** The label's words, a small line under them (or null), and its colour. */
        String label, labelSmall;
        int labelColour;
        /**
         * A dot in this colour before the label's words, pulsing while the card is up (a VAR check under way); 0 for
         * none. A small picture before them instead (the prediction's gold trophy); null for none.
         */
        int pulse;
        Drawable labelIcon;
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
        /** How much larger than a channel's logo it is drawn (Debrid Stream's two-line mark, on the release cards). */
        float channelLogoScale = 1f;
        String channel;
        Runnable watch;
        /**
         * The rest of the alerts in this style (the news, messages, weather, deliveries, the car, tennis): what runs as
         * it comes up (its sound), how long it stays (0: the usual), and a slim bar of progress after its words (an
         * order's stages, the car's charge, the drive home), 0 to 1 in progressColour; -1 for none.
         */
        Runnable onShown;
        /** Runs as the card goes (the live table under Home's bar folds up with it). */
        Runnable onClosed;
        long showMs;
        float progress = -1f;
        int progressColour = 0xFFFFFFFF;
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
    /** When the card came up, and the key it is letting through to the app (PopupMotion.passOn). */
    private static long shownAt;
    private static final int[] passing = {0};
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

    /** This card away (fading) if it is the one showing; taken out of the queue if it is still waiting. */
    static void closeIf(Data d) {
        queue.remove(d);
        if (host != null && showing == d) close(true);
    }

    /** Whether this card is the one on screen now (a VAR decision takes its check's place). */
    static boolean showingNow(Data d) {
        return host != null && showing == d;
    }

    /** As show, ahead of the cards waiting (a message, an order's news: now or never). */
    static void showFirst(Context ctx, Data d) {
        queue.addFirst(d);
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

    /** Since when the next card has waited for another pop-up to go (not past GoalWatchService.WAIT_MAX_MS). */
    private static long waitingSince;
    /** Since when it has waited for Settings (or a permission screen) to close (OverlayGuard). */
    private static long hiddenSince;

    private static void next(Context c) {
        if (host != null || queue.isEmpty()) return;
        // The pause card makes way at once; How was it? is waited for (WatchCards.holds).
        boolean watchCard = WatchCards.holds();
        if (LineupCard.isShowing() || GoalWatchService.alertShowing() || CameraAlert.isShowing() || watchCard) {
            long now = android.os.SystemClock.uptimeMillis();
            if (waitingSince == 0) {
                waitingSince = now;
                AlertLog.note(c, "card waiting: " + (LineupCard.isShowing() ? "the line-up card" : GoalWatchService.alertShowing() ? "an alert"
                        : watchCard ? "How was it?" : "the camera card") + " is up");
            }
            // One left up too long (or never taken down) is not waited for for ever: a goal is not lost behind it.
            if (now - waitingSince < GoalWatchService.WAIT_MAX_MS) {
                main.postDelayed(() -> next(c), 2_000L);
                return;
            }
            AlertLog.note(c, "card waited 90 s for another pop-up to go: shown all the same");
        }
        waitingSince = 0;
        // Over Settings (and the permission screens) Android hides every app's pop-ups: the card waits until it closes.
        boolean homeNow = GoalWatchService.laneHome && GoalWatchService.homeInFront || PopupFrame.isShowing();
        String hiding = homeNow ? null : OverlayGuard.hiding(c);
        if (hiding != null) {
            long now = android.os.SystemClock.uptimeMillis();
            if (hiddenSince == 0) {
                hiddenSince = now;
                AlertLog.note(c, "card waiting: " + OverlayGuard.name(hiding) + " is open (Android hides pop-ups over it)");
            }
            if (now - hiddenSince < OverlayGuard.MAX_MS) {
                main.postDelayed(() -> next(c), 2_000L);
                return;
            }
            AlertLog.note(c, "card waited 10 min for " + OverlayGuard.name(hiding) + " to close: shown all the same");
        }
        hiddenSince = 0;
        Data d = queue.poll();
        // Switched off in the Control Centre's Notifications (where it would show, its kind, or its game's competition):
        // not shown, the next one.
        boolean home = NotifyPrefs.homeNow();
        if (!NotifyPrefs.allowed(c, d, home)) {
            NotifyPrefs.held(c, NotifyKinds.ofCard(d.notify, d.kind, !d.podium.isEmpty()), home, d.key,
                    "card " + d.label + (d.labelSmall != null ? " " + d.labelSmall : "") + " " + d.first + " " + d.last + " " + d.line, d.slug);
            next(c);
            return;
        }
        if (d.f1) { d.dark = true; d.wideMark = true; }
        onHome = !d.again && (GoalWatchService.laneHome && GoalWatchService.homeInFront || PopupFrame.isShowing());
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        AlertLog.note(c, "card shown " + (onHome ? "on Home (in the score bar's place)" : PopupFrame.isShowing() ? "in pop-up mode's frame"
                : "over the app (" + AlertLog.front(c) + ")")
                + ": " + d.label + (d.labelSmall != null ? " " + d.labelSmall : "") + " " + d.first + " " + d.last + " " + d.line);
        if (d.key != null) AlertToday.shown(c, d.key);
        if (onHome) bar(c, d); else card(c, d);
        if (host != null && d.onShown != null) {
            try {
                d.onShown.run();
            } catch (RuntimeException ignored) {
                // its sound only
            }
        }
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
        // Its first moments: Back and OK go on to the app they were pressed for.
        if (PopupMotion.passOn(e, shownAt, passing)) return false;
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
        // The competition's logo alone in the band (no club crest under it): large, filling the band (it was the
        // small size it has above a crest, lost on its own); with a crest under it, as before.
        boolean alone = d.crest == null || d.f1;
        if (d.leagueLogo != null) {
            ImageView l = image(c, d.leagueLogo);
            int lw = d.bigMark ? px(150) : d.wideMark ? px(68) : alone ? px(48) : px(26);
            int lh = d.bigMark ? px(44) : d.wideMark ? px(30) : alone ? px(48) : px(26);
            band.addView(l, new LinearLayout.LayoutParams(lw, lh));
        }
        if (d.crest != null && !d.f1) {
            // The club's crest as large as the band allows, so it reads clearly.
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(40), px(40));
            cl.topMargin = px(4);
            band.addView(image(c, d.crest), cl);
        }
        int bandW = d.bigMark ? px(176) : d.wideMark ? px(86) : px(62);
        FrameLayout bandBox = new FrameLayout(c);
        bandBox.setBackground(band.getBackground());
        band.setBackground(null);
        if (!plainBlack(d)) bandBox.addView(stripes(c, d), new FrameLayout.LayoutParams(bandW + STRIPE_PERIOD_PX(), h));
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
            // The crests as large as the card's height allows, so a detailed one (Arsenal's, PSG's) stays clear.
            int crestPx = "kickoff".equals(d.kind) ? 52 : 46;
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
        if (d.watch != null) stage.addView(watchBlock(c, d, h, 15, 10.5f), watchParams(stage, h));
        card.addView(stage, new LinearLayout.LayoutParams(-2, h));

        FrameLayout box = new DriftBox(c);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        box.setClipToOutline(true);
        // No shadow: over a bright picture it shows as a dark rim round the card.
        box.setBackground(stageFill(d, px(40), px(26)));
        if (!d.dark) box.addView(drifting(c, d, px(40), px(26), h), driftParams(px(40), px(26), h));
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
        // Over Ambient mode: above its bar along the foot, not on it.
        lp.y = px(AmbientScene.showing() ? AmbientScene.ALERT_FOOT : 34);
        lp.setTitle("ScoreCard");
        if (!add(c, win, lp, d)) return;
        enter(box, -px(36), 0);
        alive(c, d, box, h);
        schedule(d);
    }

    // ---- on Home: the bar in the score bar's place ---------------------------------------------------------------

    private static void bar(Context c, Data d) {
        // Option B's top bar: the lane from the score pill leftwards (narrower: the clubs' names beside the score go, the
        // crests stay); else across the score bar's place.
        boolean corner = ScoresTicker.corner();
        int h = px(52), w = corner ? ScoresTicker.laneRight - ScoresTicker.laneLeft : px(1728);
        // A lane narrower than the bar needs: the names beside the score go (the crests stay).
        boolean tight = corner && w < px(1360);
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        // Square-edged as the cards over the apps when it opens out of the Sky top bar; else round-ended.
        float r = ScoresTicker.sky && ScoresTicker.corner() ? SkyStyle.corner(c) : h / 2f;
        bar.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        bar.setClipToOutline(true);
        // Its ground is the box's (below), with the streaks drifting across it on a layer of their own.
        boolean ko = "kickoff".equals(d.kind);
        FrameLayout band = new FrameLayout(c);
        band.setBackground(bandGround(d, false));
        int bandW = d.bigMark ? px(230) : d.wideMark ? px(150) : px(120);
        if (!plainBlack(d)) band.addView(stripes(c, d), new FrameLayout.LayoutParams(bandW + STRIPE_PERIOD_PX(), h));
        if (d.leagueLogo != null) band.addView(image(c, d.leagueLogo), new FrameLayout.LayoutParams(
                d.bigMark ? px(200) : d.wideMark ? px(124) : px(40), d.bigMark ? px(40) : px(40), Gravity.CENTER));
        bar.addView(band, new LinearLayout.LayoutParams(bandW, h));
        // A kick-off's label is KICK-OFF alone: how long until it is said once, after the clubs.
        String small = d.labelSmall;
        if (ko) d.labelSmall = null;
        View label = label(c, d, 23, h);
        d.labelSmall = small;
        bar.addView(label, new LinearLayout.LayoutParams(-2, h));
        // A driver's headshot (ESPN's, head to collar in a square) smaller than a footballer's, so the whole head shows in
        // the bar's height, chin and hair included.
        if (d.photo != null && d.podium.isEmpty()) bar.addView(d.f1 ? photo(c, d.photo, px(66), h, px(71), px(-3), px(-2))
                : photo(c, d.photo, px(66), h, px(86), px(-10), px(-3)), new LinearLayout.LayoutParams(px(66), h));
        if (d.picture != null) {
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(80), px(46));
            pl.leftMargin = px(8);
            bar.addView(picture(c, d.picture, px(6)), pl);
        }
        if (d.podium.isEmpty()) {
            if (d.teamLogo != null && d.f1) {
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(36), px(36));
                tl.leftMargin = px(6);
                bar.addView(teamTile(c, d.teamLogo, px(36)), tl);
            }
            LinearLayout name = new LinearLayout(c);
            name.setGravity(Gravity.CENTER_VERTICAL);
            // Full time: the result big ("Chelsea win"), the scorers and any red cards after it, small, as on the card
            // (its first line is the scorers, not a first name).
            boolean ft = "ft".equals(d.kind) || "ht".equals(d.kind);
            // A kick-off: "Tottenham Hotspur v Crystal Palace", then "Kick-off in 5 mins", then the crests (no names
            // again beside them). A surname ("SALAH") is title-cased; a result or a kick-off reads as it is.
            String big = d.headline != null ? d.headline : ko ? d.homeName + " v " + d.awayName : ft ? d.last
                    : (d.first.isEmpty() ? "" : d.first + " ") + (d.f1 ? d.last : titleCase(d.last));
            String after = ko ? kickoffIn(d) : ft ? join(d.first, d.line) : d.others.isEmpty() ? d.line : "";
            TextView bigText = text(c, big, d.headline != null ? 19 : 22, d.dark ? 0xFFFFFFFF : INK, d.headline != null ? outfit(c, 800) : outfit(c, 600));
            if (d.headline != null) bigText.setMaxWidth(px(tight ? 560 : corner ? 760 : 1000));
            else if (corner && ko) bigText.setMaxWidth(px(520));
            name.addView(bigText);
            if (!after.isEmpty()) {
                TextView after2 = text(c, after, ko ? 18 : 16, d.dark ? 0xCCFFFFFF : INK_SOFT, outfit(c, ko ? 600 : 500));
                if (ft) after2.setMaxWidth(px(tight ? 420 : corner ? 600 : 820));
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
            if (d.progress >= 0) {
                LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(120), px(8));
                pl.leftMargin = px(14);
                name.addView(progress(c, d), pl);
            }
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
                    // The team's logo large enough to read (Ferrari's horse, Mercedes' star).
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(34), px(34));
                    tl.leftMargin = px(8);
                    e.addView(teamTile(c, (Bitmap) p[2], px(34)), tl);
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
        // The rest at the right end: a kick-off's bar as long as the full time's and the red card's (it was only as
        // long as it needed, and stopped short of the others' end), its crests and channel at the end as theirs are.
        View spacer = new View(c);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        if (d.score != null) {
            if (d.homeName != null && !ko && !tight) bar.addView(text(c, d.homeName, 20, INK, outfit(c, 800)), sideName(true));
            bar.addView(scoreBlock(c, d, h, ko ? 40 : 38, 25), new LinearLayout.LayoutParams(-2, h));
            if (d.awayName != null && !ko && !tight) bar.addView(text(c, d.awayName, 20, INK, outfit(c, 800)), sideName(false));
        } else if (d.method != null) {
            bar.addView(scoreBlock(c, d, h, 28, 25), new LinearLayout.LayoutParams(-2, h));
        }
        if (d.watch != null) bar.addView(watchBlock(c, d, h, 16, 12), watchParams(bar, h));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(24), px(24));
        kl.leftMargin = px(12);
        kl.rightMargin = px(14);
        bar.addView(backKey(c, px(24)), kl);

        FrameLayout box = new DriftBox(c);
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        box.setClipToOutline(true);
        box.setBackground(stageFill(d, px(50), px(30)));
        if (!d.dark) box.addView(drifting(c, d, px(50), px(30), h), driftParams(px(50), px(30), h));
        int bw = w;
        box.addView(bar, new FrameLayout.LayoutParams(bw, h));
        FrameLayout win = new FrameLayout(c);
        win.addView(box, new FrameLayout.LayoutParams(bw, h));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(bw, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        ScoresTicker.placeBar(c, lp, h, px(14), ko);
        lp.setTitle("ScoreCard");
        if (!add(c, win, lp, d)) return;
        lane(true);
        // Option B: the bar grows out of the score pill; else in from above.
        if (PillMorph.on()) PillMorph.open(win, lp.x, lp.y); else enter(box, 0, -px(24));
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
        } else if (d.pulse != 0 || d.labelIcon != null) {
            // A VAR check's dot beating while it is under way, or the prediction's trophy, before the words.
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (d.labelIcon != null) {
                ImageView icon = new ImageView(c);
                icon.setImageDrawable(d.labelIcon);
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(size * 1.05f), px(size * 1.05f));
                il.rightMargin = px(9);
                row.addView(icon, il);
            }
            if (d.pulse != 0) {
                View dot = new View(c);
                GradientDrawable dd = new GradientDrawable();
                dd.setShape(GradientDrawable.OVAL);
                dd.setColor(d.pulse);
                dot.setBackground(dd);
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(size * 0.5f), px(size * 0.5f));
                dl.rightMargin = px(10);
                row.addView(dot, dl);
                android.animation.ObjectAnimator beat = android.animation.ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f);
                beat.setDuration(620);
                beat.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                beat.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                beat.start();
                moving.add(beat);
            }
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
        if (plainBlack(d)) return new Streaks(0xFF141519, 0xFF0B0C0F, 0, px(14), px(3));
        int a = d.band != 0 ? d.band : d.f1 ? 0xFFE10600 : 0xFF1B2591;
        int b = d.bandDeep != 0 ? d.bandDeep : d.f1 ? 0xFF8E0400 : 0xFF0D1360;
        return new Streaks(a, b, withStripes ? stripeColour(d) : 0, px(14), px(3));
    }

    /** The fights' pop-ups (UFC, boxing): black all over, band too, with no stripes and nothing moving. */
    private static boolean plainBlack(Data d) {
        return d.dark && !d.f1;
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

    /** The stage's ground without its streaks: pale; plain black for F1 and fights (no streaks, nothing moving). */
    private static Drawable stageFill(Data d, int gap, int width) {
        return d.dark ? new Streaks(0xFF141519, 0xFF0B0C0F, 0, gap, width) : new Streaks(0xFFF6F7FD, 0xFFD3D6F3, 0, gap, width);
    }

    /**
     * The stage's streaks on a layer of their own, a period wider than the pop-up (its params start a period to the
     * left), drifting one period to the right and again while it shows: drawn once, only the layer moves, so the
     * picture under it is not redrawn. Stopped when it closes.
     */
    private static View drifting(Context c, Data d, int gap, int width, int h) {
        View lines = new DriftLines(c, Math.max(4, gap) + Math.max(1, width));
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
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * The streaks' layer: no size of its own, so a pop-up stays as wide as its contents (a layer as wide as its parent
     * made every card over an app stretch across the screen); DriftBox lays it over the whole box.
     */
    static final class DriftLines extends View {
        final int period;

        DriftLines(Context c, int period) {
            super(c);
            this.period = period;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(0, 0);
        }
    }

    /** A pop-up's box, as big as its contents, with its streaks' layer laid over all of it and a period to the left. */
    static final class DriftBox extends FrameLayout {
        DriftBox(Context c) {
            super(c);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                if (v instanceof DriftLines) v.layout(-((DriftLines) v).period, 0, getWidth(), getHeight());
            }
        }
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
            if (d.progress >= 0) {
                LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(px(160), px(7));
                pl.topMargin = px(7);
                v.addView(progress(c, d), pl);
            }
            return v;
        }
        if (!d.first.isEmpty()) v.addView(text(c, d.first, small + 1, d.dark ? 0xFFFFFFFF : INK_SOFT, outfit(c, 500)), wrap());
        v.addView(text(c, d.last.toUpperCase(java.util.Locale.UK), big, ink, outfit(c, 600)), wrap());
        if (!d.others.isEmpty()) {
            // F1: P2 and P3 each with his team's logo.
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            for (int i = 0; i < d.others.size(); i++) row.addView(driver(c, d.others.get(i), line, 22), driverParams(i == 0 ? 0 : 10));
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

    /** A slim rounded bar: its track, and the part done (Data.progress) in Data.progressColour. */
    private static View progress(Context c, Data d) {
        float done = Math.max(0f, Math.min(1f, d.progress));
        int track = d.dark ? 0x33FFFFFF : 0x2E0A0D33;
        return new View(c) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas canvas) {
                float w = getWidth(), h = getHeight(), r = h / 2f;
                paint.setColor(track);
                canvas.drawRoundRect(0, 0, w, h, r, r, paint);
                paint.setColor(d.progressColour);
                canvas.drawRoundRect(0, 0, Math.max(h, w * done), h, r, r, paint);
            }
        };
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
        v.setTag(BLOCK);
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
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(px(40), px(40));
                tl.leftMargin = px(10);
                v.addView(teamTile(c, d.teamLogo, px(40)), tl);
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
        // The team's logo large enough to read beside the name (Ferrari's horse, Mercedes' star), lifted to show on black.
        if (p[2] != null) top.addView(image(c, onBlack((Bitmap) p[2])), new LinearLayout.LayoutParams(px(32), px(32)));
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

    /** The tag on a coloured block (the score's, F1's position) that the channel's block may follow straight on. */
    private static final String BLOCK = "block";

    /**
     * The channel's block straight after a coloured block (the score, the crests, F1's position) tucks its slanted edge
     * over that block's end: side by side, the slant left a pale wedge of the ground between them.
     */
    private static LinearLayout.LayoutParams watchParams(LinearLayout row, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, h);
        View last = row.getChildCount() > 0 ? row.getChildAt(row.getChildCount() - 1) : null;
        if (last != null && BLOCK.equals(last.getTag())) p.leftMargin = -px(14);
        return p;
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
            l.setMaxWidth(px(logoH * 9 * d.channelLogoScale));
            v.addView(l, new LinearLayout.LayoutParams(-2, px(logoH * d.channelLogoScale)));
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
        pill.addView(text(c, d.okWords, textPx, 0xFF111214, outfit(c, 700)));
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
        // Every pop-up but F1's and the fights' (plain black, still): the band's stripes drift along, slightly.
        View lines = box.findViewWithTag("stripes");
        if (lines != null && !d.dark) {
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
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "a score card's window could not open: " + e);
            for (android.animation.Animator a : moving) a.cancel();
            moving.clear();
            main.post(() -> next(c));
            return false;
        }
        host = win;
        showing = d;
        shownAt = android.os.SystemClock.uptimeMillis();
        // The remote's keys taken only after its first moments: a press already on its way was meant for the app.
        if (takeKeys) main.postDelayed(() -> { if (host == win) takeKeys(c, win, d); }, PopupMotion.FRESH_MS);
        return true;
    }

    /** The card's window takes the remote (a TV without the key helper): OK watches, Back or any other key closes. */
    private static void takeKeys(Context c, FrameLayout win, Data d) {
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) win.getLayoutParams();
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            c.getSystemService(WindowManager.class).updateViewLayout(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        {
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
    }

    private static void schedule(Data d) {
        FrameLayout win = host;
        main.postDelayed(() -> { if (host == win) close(true); }, d.showMs > 0 ? d.showMs : d.watch != null ? WATCH_MS : SHOW_MS);
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
        Data was = showing;
        showing = null;
        if (was != null && was.onClosed != null) main.post(was.onClosed);
        if (v == null) return;
        Context c = v.getContext().getApplicationContext();
        boolean wasHome = onHome;
        final boolean[] went = {false};
        Runnable gone = () -> {
            if (went[0]) return;
            went[0] = true;
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
            if (wasHome) lane(false);
            main.postDelayed(() -> next(c), 400L);
        };
        // Its closing movement may be interrupted, and then never reports its end: the window goes all the same.
        main.postDelayed(gone, 900L);
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
        // Option B: the bar folds back into the score pill.
        if (wasHome && PillMorph.close(v, gone)) return;
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
        lane("card", on);
    }

    /** Who holds the pills' place now: the score card, the line-up or F1 board, the older alerts. */
    private static final java.util.Set<String> laneHolders = new java.util.HashSet<>();

    /**
     * One of them takes the pills' place or gives it up (on the main thread). The pills come back only once every one
     * has gone: an alert closing a moment after the next one opened (an F1 board straight after a score card) no
     * longer brings them back behind it, where they showed at its rounded corners.
     */
    static void lane(String who, boolean on) {
        boolean before = !laneHolders.isEmpty();
        if (on) laneHolders.add(who); else laneHolders.remove(who);
        boolean now = !laneHolders.isEmpty();
        java.util.function.Consumer<Boolean> l = GoalWatchService.laneListener;
        if (l != null && (now != before || on)) l.accept(now);
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

    /** An F1 team's logo straight on F1's black, its dark parts lifted to white so it shows (onBlack). */
    private static View teamTile(Context c, Bitmap b, int size) {
        return image(c, onBlack(b));
    }

    private static final java.util.Map<Bitmap, Bitmap> blackSafe = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * A logo made to show on black, with no tile behind it: its black, near-black and very dark parts (Mercedes' and
     * Audi's black, Haas's lettering, a dark navy) turned white, smoothly, while its colours stay as they are
     * (Ferrari's red and yellow, McLaren's orange, Williams' blue). Kept per picture.
     */
    static Bitmap onBlack(Bitmap b) {
        if (b == null) return null;
        Bitmap have = blackSafe.get(b);
        if (have != null) return have;
        Bitmap src = b.getConfig() == Bitmap.Config.ARGB_8888 ? b : b.copy(Bitmap.Config.ARGB_8888, false);
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        // A logo with a bright ground of its own (Ferrari's yellow shield) shows on black as it is, its dark parts
        // (the horse) on that ground: only a logo that is mostly dark or grey is lifted.
        int opaque = 0, dim = 0;
        for (int i = 0; i < px.length; i += 3) {
            if ((px[i] >>> 24) < 128) continue;
            opaque++;
            int r = (px[i] >> 16) & 0xFF, g = (px[i] >> 8) & 0xFF, bl = px[i] & 0xFF;
            int max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
            if (max < (max - min < 28 ? 170 : 110)) dim++;
        }
        if (opaque == 0 || dim < opaque * 0.45) return b;
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a < 8) continue;
            int r = (px[i] >> 16) & 0xFF, g = (px[i] >> 8) & 0xFF, bl = px[i] & 0xFF;
            int max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
            // Dark, or a mid grey (Mercedes' silver star): towards white, all the way well below the limit, a blend
            // between, so the edges stay smooth. Colours are kept unless they are near black.
            int limit = max - min < 28 ? 170 : 110;
            if (max >= limit) continue;
            float t = Math.min(1f, (limit - max) / 60f);
            r = Math.round(r + (242 - r) * t);
            g = Math.round(g + (242 - g) * t);
            bl = Math.round(bl + (245 - bl) * t);
            px[i] = (a << 24) | (r << 16) | (g << 8) | bl;
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        blackSafe.put(b, out);
        return out;
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
            e.addView(teamTile(c, (Bitmap) o[2], px(logoPx)), ll);
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
