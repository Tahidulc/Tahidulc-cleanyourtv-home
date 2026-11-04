package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * OK held on a pop-up: while one of the alert cards is up (over any app, on Home or in Ambient mode), holding the
 * remote's OK for HOLD_MS turns Do not disturb on for an hour, takes the cards away and says so in a small pill
 * ("Quiet for 1 hour"). The Control Centre's tile shows it and turns it off.
 *
 * <p>A key helper sees a key's press and release but never its repeats, so a hold is told by time: while a card is up
 * OK's press is kept here. Let go before HOLD_MS and the press goes on as if it had never been kept: through the rest
 * of the helper (a card's Watch, the line-up card's channel), and when nothing there wants it, to whatever has the
 * remote, with Android's own D-pad OK (Android 13 and later; earlier, nothing is kept). A card's first FRESH_MS let OK
 * go straight on, as they do every key: a press already on its way was meant for the app.
 *
 * <p>The cards say when they are up (shown); a card is up while its window is on screen. On a TV without the key
 * helper (the TCL) AppSearchService takes the keys only while a card is up, as filtering keys costs that remote its
 * auto-repeat. The doorbell is not one of them: Do not disturb lets it through, so a hold there changes nothing.
 */
final class QuietHold {
    private QuietHold() { }

    /** How long OK is held for quiet. */
    static final long HOLD_MS = 800L;

    private static final Handler main = new Handler(Looper.getMainLooper());

    private static final class Card {
        final WeakReference<View> window;
        final Runnable close;
        final long at;

        Card(View window, Runnable close, long at) {
            this.window = new WeakReference<>(window);
            this.close = close;
            this.at = at;
        }

        boolean up() {
            View v = window.get();
            // A window's root has the window as its parent from addView until the window goes.
            return v != null && v.getParent() != null;
        }
    }

    private static final List<Card> cards = new ArrayList<>();
    private static Context app;

    /** A card's window has just been added (main thread); close takes it away as its own timer would. */
    static void shown(Context c, View window, Runnable close) {
        if (window == null || Build.VERSION.SDK_INT < 33) return;
        app = c.getApplicationContext();
        cards.add(new Card(window, close, SystemClock.uptimeMillis()));
        prune();
        keysForCards(true);
        main.removeCallbacks(watch);
        main.postDelayed(watch, 1_000L);
    }

    /** Whether one of the cards is on screen. */
    static boolean up() {
        prune();
        return !cards.isEmpty();
    }

    private static void prune() {
        for (int i = cards.size() - 1; i >= 0; i--) if (!cards.get(i).up()) cards.remove(i);
    }

    /** Whether a card has been up long enough for this press to be its (pressed at, in uptime). */
    private static boolean settled(long pressedAt) {
        prune();
        for (Card k : cards) if (pressedAt - k.at >= PopupMotion.FRESH_MS) return true;
        return false;
    }

    /** Once a second while a card is up: the TCL's key filter off again when none is. */
    private static final Runnable watch = new Runnable() {
        @Override public void run() {
            if (up()) { main.postDelayed(this, 1_000L); return; }
            keysForCards(false);
        }
    };

    private static void keysForCards(boolean on) {
        if (!InputKeyService.running) AppSearchService.keysFor("card", on);
    }

    // ---- the remote ------------------------------------------------------------------------------------------------

    /** When OK was last pressed from here (or by AppSearchService.okOn): that press goes by untouched. */
    static long okSentAt;

    /** The OK being held: its press, and the helper it came through. */
    private static KeyEvent held;
    private static AccessibilityService heldBy;
    /** The hold ran out: quiet is on, and the release is only swallowed. */
    private static boolean fired;
    /** A kept press going on to the rest: not kept again. */
    private static boolean passing;

    static boolean isOk(int code) {
        return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    /**
     * A key helper's onKeyEvent: OK while a card is up is kept here (see above); everything else, and OK when no card
     * is up, goes to rest, the helper's own keys, as before.
     */
    static boolean key(AccessibilityService s, KeyEvent e, Predicate<KeyEvent> rest) {
        if (passing || !isOk(e.getKeyCode()) || Build.VERSION.SDK_INT < 33) return rest.test(e);
        if (SystemClock.uptimeMillis() - okSentAt < 400L) return rest.test(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (held != null && held.getDownTime() == e.getDownTime()) return true;
            // A press whose release never came: forgotten.
            clear();
            if (e.getRepeatCount() > 0 || !settled(e.getEventTime())) return rest.test(e);
            held = new KeyEvent(e);
            heldBy = s;
            fired = false;
            main.postDelayed(hold, HOLD_MS);
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_UP || held == null || held.getDownTime() != e.getDownTime()) return rest.test(e);
        KeyEvent down = held;
        boolean quiet = fired;
        clear();
        if (quiet || e.isCanceled()) return true;
        // A short press: on through the helper's keys, as it came; to the app when none of them wants it.
        boolean took;
        passing = true;
        try {
            took = rest.test(down);
            took |= rest.test(e);
        } finally {
            passing = false;
        }
        if (!took) main.post(() -> {
            okSentAt = SystemClock.uptimeMillis();
            s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER);
        });
        return true;
    }

    private static void clear() {
        main.removeCallbacks(hold);
        held = null;
        heldBy = null;
        fired = false;
    }

    private static final Runnable hold = () -> {
        if (held == null) return;
        fired = true;
        Context c = heldBy != null ? heldBy : app;
        if (c != null) quiet(c);
    };

    /** The hold ran out: Do not disturb for an hour, every card away, and the pill. */
    static void quiet(Context c) {
        long[] dnd = NotifyPrefs.dndHeld(c);
        List<Card> now = new ArrayList<>(cards);
        cards.clear();
        for (Card k : now) {
            if (!k.up()) continue;
            try {
                k.close.run();
            } catch (RuntimeException e) {
                AlertLog.note(c, "a card would not close for quiet: " + e);
            }
        }
        keysForCards(false);
        pill(c, DoNotDisturb.heldWords(dnd[1] == 1, dnd[0], java.util.TimeZone.getDefault()));
    }

    // ---- the pill --------------------------------------------------------------------------------------------------

    private static FrameLayout pill;
    private static final long PILL_MS = 3_200L;
    private static final Runnable pillAway = QuietHold::pillAway;

    /** "Quiet for 1 hour" at the top right for a few seconds, drawn by the CPU so the app's video keeps its plane. */
    private static void pill(Context c, String[] words) {
        pillGone();
        Context sw = new GoalWatchService.SoftwareWindow(c.getApplicationContext());
        float d = c.getResources().getDisplayMetrics().density;
        FrameLayout win = new FrameLayout(sw);
        LinearLayout box = new LinearLayout(sw);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int padH = Math.round(22 * d), padV = Math.round(14 * d);
        box.setPadding(padH, padV, Math.round(28 * d), padV);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF7231C4A, 0xF7140F2E});
        glass.setCornerRadius(18 * d);
        glass.setStroke(Math.max(1, Math.round(d)), 0x40C4B5FD);
        box.setBackground(glass);

        ImageView moon = new ImageView(sw);
        moon.setImageResource(R.drawable.ic_moon);
        moon.setColorFilter(0xFFC4B5FD);
        moon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int m = Math.round(30 * d);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(m, m);
        mlp.rightMargin = Math.round(16 * d);
        box.addView(moon, mlp);

        LinearLayout lines = new LinearLayout(sw);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.addView(line(sw, words[0], 20, 0xFFFFFFFF, true));
        TextView under = line(sw, words[1], 14, 0xC7FFFFFF, false);
        under.setPadding(0, Math.round(3 * d), 0, 0);
        lines.addView(under);
        box.addView(lines, new LinearLayout.LayoutParams(-2, -2));
        win.addView(box, new FrameLayout.LayoutParams(-2, -2));
        win.setContentDescription(words[0] + ". " + words[1]);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // As the alerts: never the keys, and touchable so Android 12+ does not cap it at 80% opacity.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(40 * d);
        lp.y = Math.round(40 * d);
        lp.setTitle("QuietHold");
        try {
            c.getApplicationContext().getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(c, "the quiet pill could not open: " + e);
            return;
        }
        pill = win;
        PopupMotion.in(box, 10 * d);
        main.postDelayed(pillAway, PILL_MS);
    }

    private static TextView line(Context c, String s, float sp, int colour, boolean bold) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colour);
        v.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    private static void pillAway() {
        FrameLayout v = pill;
        if (v == null || v.getChildCount() == 0) { pillGone(); return; }
        float d = v.getResources().getDisplayMetrics().density;
        PopupMotion.out(v.getChildAt(0), 8 * d, () -> { if (pill == v) pillGone(); });
        // Its movement interrupted, it never reports its end: taken down all the same.
        main.postDelayed(() -> { if (pill == v) pillGone(); }, 800L);
    }

    private static void pillGone() {
        main.removeCallbacks(pillAway);
        FrameLayout v = pill;
        pill = null;
        if (v == null) return;
        try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
    }
}
