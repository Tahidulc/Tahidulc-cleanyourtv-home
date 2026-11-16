package com.gathin.tvhomephone.sports;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;

import java.util.ArrayDeque;

/**
 * The TV's pop-ups over apps, for the phone: an alert drops in from the top of the screen over whatever app is open, as
 * a black card in the TV's style. It shows the kind's coloured label, the competition, the drawn match card (the goal's,
 * the line-ups, F1's podium) or the words, and Watch and Match centre. Swipe it away left, right or up; tap it for the
 * game; it goes by itself after a few seconds. Which alerts pop up is chosen in Settings, kind by kind. While one shows,
 * its notification goes to a quieter channel (Notifier.POPUPS: its sound, no banner) so Android's banner doesn't cover
 * it. Needs Android's "Display over other apps"; never shown while Homefield's own player drops the alert itself.
 */
public final class Popups {
    private Popups() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("popups", Context.MODE_PRIVATE);
    }

    /** Pop-ups on (on until switched off; they need Android's permission too). */
    public static boolean on(Context c) { return prefs(c).getBoolean("on", true); }

    public static void setOn(Context c, boolean v) { prefs(c).edit().putBoolean("on", v).apply(); }

    /** Whether a kind pops up (every kind until switched off). */
    public static boolean kind(Context c, String kind) { return prefs(c).getBoolean("k." + kind, true); }

    public static void setKind(Context c, String kind, boolean v) { prefs(c).edit().putBoolean("k." + kind, v).apply(); }

    /** Android lets Homefield draw over other apps. */
    public static boolean allowed(Context c) { return Settings.canDrawOverlays(c); }

    /** Android's page to allow it. */
    public static Intent permission(Context c) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Whether this alert pops up now. */
    public static boolean wants(Context c, Events.Alert a) {
        if (a == null || !on(c) || !kind(c, a.kind) || !allowed(c)) return false;
        // Homefield's own player drops it from its scoreboard instead.
        return !com.gathin.tvhomephone.live.ScoreBoard.playerUp();
    }

    /** How long it stays: a drawn card longer, to be read. */
    static long showMs(Events.Alert a, boolean drawn) {
        if (a.key != null && a.key.startsWith("L|")) return 16_000L;
        return drawn ? 11_000L : 8_000L;
    }

    // ---- building (off the main thread) ------------------------------------------------------------------------

    /** What one pop-up shows, read off the main thread. */
    static final class Pop {
        Events.Alert alert;
        Bitmap card, comp;
        String head = "", title = "", line = "", watchKey;
    }

    /** The alert's pop-up built (its card drawn, its channel found) and shown. Off the main thread. */
    static void show(Context c, Events.Alert a) {
        Context app = c.getApplicationContext();
        Pop p = new Pop();
        p.alert = a;
        try {
            if (a.key != null && a.key.startsWith("L|") && a.game != null && a.summary != null) {
                p.card = NotifCard.lineups(app, a.game, a.summary);
            } else if (a.kind.startsWith("f1") && "f1result".equals(a.kind)) {
                p.card = NotifCard.podium(app, a);
            } else if (a.game != null && NotifCard.drawn(a)) {
                p.card = NotifCard.match(app, a, null);
            }
        } catch (RuntimeException e) {
            p.card = null;
        }
        int logo = Ui.dp(app, 22);
        if (a.game != null) {
            p.comp = NotifCard.logo(app, Competitions.logo(a.game), logo, logo);
            p.head = a.game.leagueName == null ? "" : a.game.leagueName;
            if (a.game.state != Game.POST && !a.kind.startsWith("f1")) {
                try {
                    com.gathin.tvhomephone.live.Channel ch = com.gathin.tvhomephone.live.AlsoShowing.best(app, a.game);
                    if (ch != null) p.watchKey = ch.key;
                } catch (RuntimeException ignored) {
                    // no Watch
                }
            }
        } else if (a.kind.startsWith("f1")) {
            p.comp = NotifCard.logo(app, Competitions.F1_LOGO, logo, logo);
            p.head = "Formula 1";
        } else if ("news".equals(a.kind)) {
            p.head = a.channel == null ? "News" : a.channel;
            // The source's mark: the BBC's own (BBC News's, BBC Sport's) drawn here, any other's logo from the playlist by
            // its name; the story's own picture as its card.
            com.gathin.tvhomephone.glass.BbcMark bbc = com.gathin.tvhomephone.glass.BbcMark.of(app, a.channel, 20);
            if (bbc == null && a.slug != null) {
                String s = com.gathin.tvhomephone.glass.BbcMark.sectionOfLink(a.slug);
                if (s != null) bbc = com.gathin.tvhomephone.glass.BbcMark.chip(app, s, 20);
            }
            if (bbc != null) {
                p.comp = bbc.bitmap();
                p.head = "";
            } else if (a.channel != null && !a.channel.isEmpty()) {
                p.comp = NotifCard.logo(app, ChannelLogos.find(app, a.channel), logo * 2, logo);
            }
            if (a.picture != null && !a.picture.isEmpty()) p.card = NotifCard.logo(app, a.picture, 960, 540);
        } else if ("weather".equals(a.kind)) {
            p.head = "Weather";
        }
        p.title = title(a);
        p.line = a.text == null ? "" : a.text.split("\n")[0];
        if (a.game != null) {
            try {
                p.title = NotifCard.title(a);
                p.line = NotifCard.line(a);
            } catch (RuntimeException ignored) {
                // its own words
            }
        }
        App.main.post(() -> enqueue(app, p));
    }

    /** Pure: an alert's title without its shouted head ("F1 RESULT · Monaco" is "Monaco", the label says the rest). */
    static String title(Events.Alert a) {
        String t = a.title == null ? "" : a.title;
        int dot = t.indexOf(" · ");
        if (dot > 0 && dot < 24 && t.substring(0, dot).equals(t.substring(0, dot).toUpperCase(java.util.Locale.ROOT))) return t.substring(dot + 3);
        return t;
    }

    // ---- showing (main thread) ----------------------------------------------------------------------------------

    private static final ArrayDeque<Pop> queue = new ArrayDeque<>();
    private static FrameLayout up;

    private static void enqueue(Context app, Pop p) {
        queue.addLast(p);
        while (queue.size() > 5) queue.removeFirst();
        if (up == null) next(app);
    }

    private static void next(Context app) {
        Pop p = queue.pollFirst();
        if (p == null) return;
        WindowManager wm = app.getSystemService(WindowManager.class);
        FrameLayout win = new FrameLayout(app);
        SwipeCard card = card(app, p);
        int w = Math.min(app.getResources().getDisplayMetrics().widthPixels - Ui.dp(app, 20), Ui.dp(app, 440));
        win.addView(card, new FrameLayout.LayoutParams(w, -2));
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = statusBar(app) + Ui.dp(app, 6);
        lp.setTitle("Homefield pop-up");
        try {
            wm.addView(win, lp);
        } catch (RuntimeException e) {
            up = null;
            return;
        }
        up = win;
        card.setTranslationY(-Ui.dp(app, 160));
        card.setAlpha(0f);
        card.animate().translationY(0).alpha(1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
        long ms = showMs(p.alert, p.card != null);
        card.goAfter(ms);
    }

    /** The pop-up gone (swiped, tapped, timed out): the next one. */
    private static void gone(Context app, FrameLayout win) {
        if (up != win) return;
        try {
            app.getSystemService(WindowManager.class).removeView(win);
        } catch (RuntimeException ignored) {
            // gone already
        }
        up = null;
        App.main.postDelayed(() -> {
            if (up == null) next(app);
        }, 250);
    }

    private static int statusBar(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : Ui.dp(c, 24);
    }

    /** The card: label and competition, the drawn card or the words, Watch and Match centre, a handle. */
    static SwipeCard card(Context c, Pop p) {
        Events.Alert a = p.alert;
        SwipeCard card = new SwipeCard(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF5101012);
        bg.setCornerRadius(Ui.dp(c, 22));
        bg.setStroke(Math.max(1, Ui.dp(c, 1)), 0x26FFFFFF);
        card.setBackground(bg);
        card.setElevation(Ui.dp(c, 10));

        int colour = com.gathin.tvhomephone.live.ScoreBoard.colour(a.kind);
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = Ui.text(c, com.gathin.tvhomephone.live.ScoreBoard.chip(a.kind), 11, ink(colour), true);
        chip.setLetterSpacing(0.08f);
        chip.setBackground(Ui.fill(c, colour, 6));
        chip.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 2));
        head.addView(chip);
        if (p.comp != null) {
            ImageView logo = new ImageView(c);
            logo.setImageBitmap(p.comp);
            // A wide mark (the BBC's) as wide as it is, 20dp tall; a competition's logo in its square.
            int lh = Ui.dp(c, 20), lw = p.comp.getHeight() > 0 ? Math.round(lh * p.comp.getWidth() / (float) p.comp.getHeight()) : lh;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(lh, Math.min(lw, lh * 6)), lh);
            lp.leftMargin = Ui.dp(c, 10);
            head.addView(logo, lp);
        }
        TextView where = Ui.text(c, p.head, 12, 0xFFB0B4C4, false);
        where.setMaxLines(1);
        where.setEllipsize(TextUtils.TruncateAt.END);
        where.setPadding(Ui.dp(c, 8), 0, 0, 0);
        head.addView(where, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(Ui.text(c, "now", 11, 0xFF80849A, false));
        card.addView(head);

        if (p.card != null) {
            ImageView pic = new ImageView(c);
            pic.setImageBitmap(p.card);
            pic.setAdjustViewBounds(true);
            pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            pic.setContentDescription(p.title + ". " + p.line);
            pic.setClipToOutline(true);
            pic.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(c, 12));
                }
            });
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
            pl.topMargin = Ui.dp(c, 10);
            card.addView(pic, pl);
        } else {
            TextView title = Ui.text(c, p.title, 16, 0xFFFFFFFF, true);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(0, Ui.dp(c, 8), 0, 0);
            card.addView(title);
            if (!p.line.isEmpty()) {
                TextView line = Ui.text(c, p.line, 14, 0xFFC4C8D8, false);
                line.setMaxLines(3);
                line.setEllipsize(TextUtils.TruncateAt.END);
                line.setPadding(0, Ui.dp(c, 3), 0, 0);
                card.addView(line);
            }
        }

        // Watch and Match centre (or Read, for a story).
        LinearLayout buttons = Ui.row(c);
        buttons.setPadding(0, Ui.dp(c, 10), 0, 0);
        if (p.watchKey != null) {
            TextView watch = button(c, "▶  Watch", true);
            watch.setOnClickListener(v -> {
                card.dismiss(0);
                start(c, com.gathin.tvhomephone.live.PlayerActivity.intent(c, p.watchKey));
            });
            buttons.addView(watch, gap(c));
        }
        if (a.game != null) {
            TextView centre = button(c, c.getString(R.string.sports_notif_match), false);
            centre.setOnClickListener(v -> {
                card.dismiss(0);
                send(Notifier.open(c, a.game));
            });
            buttons.addView(centre, gap(c));
        } else if ("news".equals(a.kind) && a.slug != null && a.slug.startsWith("http")) {
            TextView read = button(c, "Read", false);
            read.setOnClickListener(v -> {
                card.dismiss(0);
                start(c, new Intent(Intent.ACTION_VIEW, Uri.parse(a.slug)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            buttons.addView(read, gap(c));
        }
        if (buttons.getChildCount() > 0) card.addView(buttons);

        // The handle: it can be swiped.
        View handle = new View(c);
        handle.setBackground(Ui.fill(c, 0x40FFFFFF, 2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = Ui.dp(c, 8);
        card.addView(handle, hp);

        card.setContentDescription(com.gathin.tvhomephone.live.ScoreBoard.chip(a.kind) + ". " + p.title + ". " + p.line);
        card.onTap = () -> {
            if (a.game != null) send(Notifier.open(c, a.game));
            else send(Notifier.home(c));
        };
        card.onGone = win -> gone(c, win);
        return card;
    }

    private static TextView button(Context c, String s, boolean primary) {
        TextView b = Ui.text(c, s, 14, primary ? 0xFF0A0D33 : 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.fill(c, primary ? 0xFFFFFFFF : 0x29FFFFFF, 18));
        b.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        return b;
    }

    private static LinearLayout.LayoutParams gap(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(c, 8);
        return lp;
    }

    private static int ink(int colour) {
        int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 150 ? 0xFF0A0D33 : 0xFFFFFFFF;
    }

    private static void send(PendingIntent pi) {
        try {
            pi.send();
        } catch (PendingIntent.CanceledException | RuntimeException ignored) {
            // gone
        }
    }

    private static void start(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // nothing to open it with
        }
    }

    /** Pure: whether a let-go swipe sends the card away (far enough, or flung), by its move and speed in px. */
    static boolean away(float dx, float dy, float vx, float vy, int width, int height, float fling) {
        return Math.abs(dx) > width * 0.3f || dy < -height * 0.35f || (Math.abs(vx) > fling && Math.abs(vx) > Math.abs(vy))
                || (vy < -fling && Math.abs(vy) > Math.abs(vx));
    }

    /** The card that follows a finger: swiped left, right or up and let go far enough (or flung), it goes. */
    static final class SwipeCard extends LinearLayout {
        interface Gone {
            void run(FrameLayout win);
        }

        Runnable onTap;
        Gone onGone;
        private final int slop;
        private final float fling;
        private float downX, downY;
        private boolean dragging, leaving;
        private VelocityTracker velocity;
        private final Runnable timeUp = () -> dismiss(0);
        private long goAfterMs;

        SwipeCard(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            fling = ViewConfiguration.get(c).getScaledMinimumFlingVelocity() * 4f;
        }

        void goAfter(long ms) {
            goAfterMs = ms;
            removeCallbacks(timeUp);
            postDelayed(timeUp, ms);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            // A drag that starts on a button is still the card's.
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getRawX();
                downY = e.getRawY();
                dragging = false;
                return false;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                if (Math.abs(dx) > slop || dy < -slop) {
                    dragging = true;
                    return true;
                }
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (leaving) return true;
            if (velocity == null) velocity = VelocityTracker.obtain();
            velocity.addMovement(e);
            float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    dragging = false;
                    // Held: it waits.
                    removeCallbacks(timeUp);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && (Math.abs(dx) > slop || dy < -slop)) dragging = true;
                    if (dragging) {
                        boolean sideways = Math.abs(dx) >= Math.abs(dy) || dy > 0;
                        setTranslationX(sideways ? dx : 0);
                        setTranslationY(sideways ? 0 : Math.min(0, dy));
                        setAlpha(1f - Math.min(0.8f, Math.max(Math.abs(getTranslationX()) / getWidth(), -getTranslationY() / getHeight())));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    velocity.computeCurrentVelocity(1000);
                    float vx = velocity.getXVelocity(), vy = velocity.getYVelocity();
                    velocity.recycle();
                    velocity = null;
                    if (!dragging) {
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && onTap != null) {
                            dismiss(0);
                            onTap.run();
                        } else {
                            postDelayed(timeUp, Math.max(3000, goAfterMs / 2));
                        }
                        return true;
                    }
                    if (away(getTranslationX(), getTranslationY(), vx, vy, getWidth(), getHeight(), fling)) {
                        dismiss(Math.abs(getTranslationX()) > -getTranslationY() ? (getTranslationX() >= 0 ? 1 : -1) : 0);
                    } else {
                        animate().translationX(0).translationY(0).alpha(1f).setDuration(180).start();
                        postDelayed(timeUp, Math.max(3000, goAfterMs / 2));
                    }
                    return true;
                }
                default:
                    return true;
            }
        }

        /** It goes: sideways (dir -1, 1) or up (0). */
        void dismiss(int dir) {
            if (leaving) return;
            leaving = true;
            removeCallbacks(timeUp);
            FrameLayout win = (FrameLayout) getParent();
            animate().translationX(dir == 0 ? getTranslationX() : dir * getWidth() * 1.2f)
                    .translationY(dir == 0 ? -getHeight() * 1.3f : getTranslationY())
                    .alpha(0f).setDuration(200)
                    .withEndAction(() -> {
                        if (onGone != null && win != null) onGone.run(win);
                    }).start();
        }
    }
}
