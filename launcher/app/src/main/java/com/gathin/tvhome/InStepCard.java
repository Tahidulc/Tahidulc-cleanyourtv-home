package com.gathin.tvhome;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The card that lines the alerts up with the channel (InStep): when the game being watched kicks off (or starts its
 * second half) by the scores and its channel has no wait of its own yet, a small card at the top right asks for OK as
 * the kick-off happens on screen. OK then sets the channel's wait (how far the picture is behind the scores) and the
 * card says so for a few seconds; Back, or four minutes with no OK, takes it away with nothing changed. It says nothing
 * of the game's score: the kick-off's time is no secret. While the scoreboard over the channel (BoardCover) is on for
 * the game it comes at every kick-off, lined up or not, and asks to set the board's clock: OK then also counts the
 * board's clock from that moment (0:00, or 45:00 for the second half), exactly the channel's.
 */
final class InStepCard {
    private InStepCard() { }

    /** How long it waits for OK. */
    private static final long WAIT_MS = 4 * 60_000L, DONE_MS = 4_500L;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static FrameLayout card;
    private static TextView title, under;
    private static String game;
    private static boolean done, forBoard, second;
    private static Context app;
    private static final Runnable away = InStepCard::close;

    /** Asks for OK at this game's kick-off (secondHalf: its second half) on screen (main thread). */
    static void show(Context c, ScoresTicker.Game g, boolean secondHalf) {
        show(c, g, secondHalf, false);
    }

    /** The same; board: the scoreboard is on for this game, and OK sets its clock with the channel's too. */
    static void show(Context c, ScoresTicker.Game g, boolean secondHalf, boolean board) {
        close();
        app = c.getApplicationContext();
        game = g.id;
        done = false;
        forBoard = board;
        second = secondHalf;
        Context sw = new GoalWatchService.SoftwareWindow(app);
        float d = app.getResources().getDisplayMetrics().density;
        FrameLayout win = new FrameLayout(sw);
        LinearLayout box = new LinearLayout(sw);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(22 * d);
        box.setPadding(pad, Math.round(16 * d), Math.round(28 * d), Math.round(16 * d));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xF70E1426, 0xF7080B18});
        glass.setCornerRadius(20 * d);
        glass.setStroke(Math.max(1, Math.round(d)), 0x803DDC84);
        box.setBackground(glass);

        View dot = new View(sw);
        GradientDrawable g1 = new GradientDrawable();
        g1.setShape(GradientDrawable.OVAL);
        g1.setColor(0xFF3DDC84);
        dot.setBackground(g1);
        int ds = Math.round(14 * d);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ds, ds);
        dl.rightMargin = Math.round(18 * d);
        box.addView(dot, dl);

        LinearLayout lines = new LinearLayout(sw);
        lines.setOrientation(LinearLayout.VERTICAL);
        title = line(sw, board ? "Set the scoreboard's clock with your channel's" : "Line up alerts with your channel", 20, 0xFFFFFFFF, true);
        lines.addView(title);
        under = line(sw, "Press OK as the " + (secondHalf ? "second half starts" : "game kicks off") + " on your screen · "
                + names(g), 15, 0xD0FFFFFF, false);
        under.setPadding(0, Math.round(4 * d), 0, 0);
        lines.addView(under);
        box.addView(lines, new LinearLayout.LayoutParams(-2, -2));
        win.addView(box, new FrameLayout.LayoutParams(-2, -2));
        win.setContentDescription(title.getText() + ". " + under.getText());

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // As the alerts: never the keys (the helpers bring them), touchable so it is drawn at full strength.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(40 * d);
        lp.y = Math.round(40 * d);
        lp.setTitle("InStepCard");
        try {
            app.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            AlertLog.note(app, "the line-up card could not open: " + e);
            return;
        }
        card = win;
        PopupMotion.in(box, 10 * d);
        // Up as a card: the TCL's helper takes the keys while it is (QuietHold, and here for an Android before 13),
        // a held OK still means quiet.
        QuietHold.shown(app, win, InStepCard::close);
        if (!InputKeyService.running) AppSearchService.keysFor("instep", true);
        main.postDelayed(away, WAIT_MS);
        AlertLog.note(app, "line-up card up for " + AlertLog.game(g));
    }

    private static String names(ScoresTicker.Game g) {
        String h = g.homeName != null ? g.homeName : g.home, a = g.awayName != null ? g.awayName : g.away;
        return (h == null ? "" : h) + " v " + (a == null ? "" : a);
    }

    /** OK (lines up) and Back (away) while it is up; other keys go on to the app. */
    static boolean key(KeyEvent e) {
        if (card == null) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(InStepCard::close);
            return true;
        }
        if (!QuietHold.isOk(code)) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(InStepCard::ok);
        return true;
    }

    private static void ok() {
        if (card == null || done || app == null) return;
        done = true;
        long pressed = System.currentTimeMillis();
        long h = InStep.lineUp(app, game, pressed);
        if (forBoard) {
            // The kick-off is on the screen now: the board's clock counted from this moment, the channel's own.
            BoardCover.anchor(app, game, second ? 45 * 60 : 0, pressed);
            title.setText("Clock set");
            under.setText("The scoreboard's clock is your channel's from here" + (h >= 0 ? " · pop-ups wait " + InStep.words(h) : ""));
        } else if (h >= 0) {
            title.setText("Lined up");
            String ch = InStep.channel(app);
            under.setText("Pop-ups for this game now wait " + InStep.words(h) + (ch.isEmpty() ? "" : " on " + ch));
        } else {
            title.setText("That didn't line up");
            under.setText("You'll be asked again at the next kick-off");
        }
        main.removeCallbacks(away);
        main.postDelayed(away, DONE_MS);
    }

    static void close() {
        main.removeCallbacks(away);
        FrameLayout v = card;
        card = null;
        if (v == null) return;
        if (!InputKeyService.running) AppSearchService.keysFor("instep", false);
        try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
    }

    static boolean isShowing() {
        return card != null;
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
}
