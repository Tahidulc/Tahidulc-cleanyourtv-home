package com.gathin.tvhome;

import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * The pop-ups' shared movement, so every one moves the same way: the remote's Back key on a card dips as Back goes
 * down, and as it comes up the card shrinks a little towards that key and fades. Each move is drawn into a layer, so
 * nothing is redrawn while it runs over a playing video.
 */
final class PopupMotion {
    /** The tag on a card's Back key view. */
    static final String BACK_KEY = "backkey";

    private PopupMotion() { }

    static boolean isBack(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.KEYCODE_BACK;
    }

    /** The Back key on the card pressed in, as the remote's button goes down. */
    static void press(View root) {
        View key = root == null ? null : root.findViewWithTag(BACK_KEY);
        if (key == null) return;
        key.animate().cancel();
        key.animate().scaleX(0.78f).scaleY(0.78f).setDuration(90).withLayer().start();
    }

    /**
     * Closed by Back: its key springs back, and the whole window's content shrinks towards the key (at pivotX,
     * pivotY in the root's own pixels) and fades; gone runs at the end.
     */
    static void backOut(View root, float pivotX, float pivotY, Runnable gone) {
        if (root == null) { if (gone != null) gone.run(); return; }
        View key = root.findViewWithTag(BACK_KEY);
        if (key != null) key.animate().scaleX(1f).scaleY(1f).setDuration(80).withLayer().start();
        root.setPivotX(pivotX);
        root.setPivotY(pivotY);
        root.animate().cancel();
        root.animate().scaleX(0.9f).scaleY(0.9f).alpha(0f).setStartDelay(60).setDuration(220)
                .setInterpolator(new AccelerateInterpolator(1.4f)).withLayer()
                .withEndAction(gone).start();
    }

    /** A plain card in: up a little and faded in, drawn into a layer while it moves. */
    static void in(View v, float rise) {
        v.setAlpha(0f);
        v.setTranslationY(rise);
        v.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(new DecelerateInterpolator(2f))
                .withLayer().start();
    }

    /** A plain card out: down a little and faded; gone runs at the end. */
    static void out(View v, float drop, Runnable gone) {
        v.animate().cancel();
        v.animate().alpha(0f).translationY(drop).setDuration(200).setInterpolator(new AccelerateInterpolator(1.4f))
                .withLayer().withEndAction(gone).start();
    }
}
