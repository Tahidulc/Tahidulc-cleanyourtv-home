package com.gathin.tvhomephone.sports;

import android.view.View;
import android.widget.TextView;

import com.gathin.tvhomephone.R;

/**
 * A live game's clock in minutes and seconds wherever a score is shown ("67:23", "45:00 +2" in added time), going on
 * second by second while the words are on the screen (GameClock), or HT and FT. One ticker per TextView, stopped as it
 * leaves the screen and started again as it comes back.
 */
public final class LiveClock {
    private LiveClock() {}

    /** How the clock's words go into the text ("● 67:23", "LIVE · 67:23"). */
    public interface Words {
        CharSequence of(String clock);
    }

    /** The clock's words as they stand: "67:23", "45:00 +2"; HT, FT, or ESPN's own words; "" before kick-off. */
    public static String words(Game g, long now) {
        if (g == null || g.state == Game.PRE) return "";
        if (g.state == Game.POST) return "FT";
        if (g.halftime) return "HT";
        String[] s = GameClock.show(g, now);
        if (s != null) return s[1].isEmpty() ? s[0] : s[0] + " " + s[1];
        return g.detail == null ? "" : g.detail;
    }

    /** Pure: the words for a clock as GameClock gives it ({"45:00", "+2"}), or the fallback when it is not running. */
    static String join(String[] shown, String otherwise) {
        if (shown == null) return otherwise == null ? "" : otherwise;
        return shown[1].isEmpty() ? shown[0] : shown[0] + " " + shown[1];
    }

    /** t shows w.of(the clock) now, and every second while it is on the screen and the game runs. */
    public static void bind(TextView t, Game g, Words w) {
        Object old = t.getTag(R.id.live_clock);
        if (old instanceof Ticker) ((Ticker) old).unbind();
        Ticker k = new Ticker(t, g, w);
        t.setTag(R.id.live_clock, k);
        t.addOnAttachStateChangeListener(k);
        k.run();
    }

    /** t's ticker stopped (its game gone from it). */
    public static void unbind(TextView t) {
        Object old = t.getTag(R.id.live_clock);
        if (old instanceof Ticker) ((Ticker) old).unbind();
        t.setTag(R.id.live_clock, null);
    }

    private static final class Ticker implements Runnable, View.OnAttachStateChangeListener {
        private final TextView t;
        private final Game g;
        private final Words w;

        Ticker(TextView t, Game g, Words w) {
            this.t = t;
            this.g = g;
            this.w = w;
        }

        @Override public void run() {
            t.removeCallbacks(this);
            long now = System.currentTimeMillis();
            t.setText(w.of(words(g, now)));
            // On the next whole second, so every clock on the screen turns together.
            if (GameClock.show(g, now) != null && t.isAttachedToWindow()) t.postDelayed(this, 1000 - now % 1000);
        }

        void unbind() {
            t.removeCallbacks(this);
            t.removeOnAttachStateChangeListener(this);
        }

        @Override public void onViewAttachedToWindow(View v) {
            run();
        }

        @Override public void onViewDetachedFromWindow(View v) {
            t.removeCallbacks(this);
        }
    }
}
