package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * The shot map beside the scoreboard over the channel at half time and full time (BoardCover, drawn by ShotMap): a small
 * window that slides out from the board's side with the swish, stays SHOW_MS and goes the same way. Never takes the
 * remote or the focus.
 */
final class ShotMapCard {
    private ShotMapCard() { }

    static final long SHOW_MS = 14_000L, SLIDE_MS = 380L;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static ImageView card;
    private static Context app;
    private static final Runnable away = ShotMapCard::slideAway;

    static boolean isShowing() {
        return card != null;
    }

    /** Out beside the board (left or right of it, or under it, where there is room), w by h; nothing when the board is not up. */
    static void show(Context c, Bitmap map, float pan) {
        close();
        app = c.getApplicationContext();
        int w = map.getWidth(), h = map.getHeight();
        int[] at = BoardCover.besideBoard(w, h);
        if (at == null) return;
        ImageView v = new ImageView(new GoalWatchService.SoftwareWindow(app));
        v.setImageBitmap(map);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = at[0];
        lp.y = at[1];
        try {
            app.getSystemService(WindowManager.class).addView(v, lp);
        } catch (RuntimeException e) {
            return;
        }
        card = v;
        boolean fromLeft = at[0] < BoardCover.boardLeft();
        v.setAlpha(0f);
        v.setTranslationX(fromLeft ? w * 0.18f : -w * 0.18f);
        v.animate().alpha(1f).translationX(0f).setDuration(SLIDE_MS).start();
        if (BoardCover.swishOn(app)) com.gathin.broadcast.Sfx.play(app, com.gathin.broadcast.Sfx.SWISH, pan, 0.7f);
        main.postDelayed(away, SHOW_MS);
        AlertLog.note(app, "shot map beside the scoreboard");
    }

    private static void slideAway() {
        ImageView v = card;
        if (v == null) return;
        if (app != null && BoardCover.swishOn(app)) com.gathin.broadcast.Sfx.play(app, com.gathin.broadcast.Sfx.SWISH, 0f, 0.6f);
        v.animate().alpha(0f).translationX(v.getWidth() * -0.12f).setDuration(SLIDE_MS).withEndAction(ShotMapCard::close).start();
    }

    /** Away at once. */
    static void close() {
        main.removeCallbacks(away);
        ImageView v = card;
        card = null;
        if (v == null || app == null) return;
        try {
            app.getSystemService(WindowManager.class).removeView(v);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }
}
