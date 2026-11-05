package com.gathin.tvhomephone.sports;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.widget.RemoteViews;

import com.gathin.tvhomephone.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Following live"'s scoreboard: each live game's box drawn once a poll (NotifCard.row, the line under the score left
 * empty) with its clock laid over that line as words (sports_notif_live_row), in minutes and seconds as GameClock has
 * it, the same as the app's ("67:23", "45:00" with "+2" in added time, HT). AlertService sends the notification again
 * every second while the screen is on, with only the words new.
 *
 * Why this way. Android's own chronometer costs nothing a second, but it counts "1:07:23" from the hour, and it can't
 * hold at :59 while ESPN's next minute is late as GameClock does, so it would disagree with the app. So the clock is
 * sent as words, once a second, and only while it can be seen: the screen on (AlertService stops at the screen going
 * off), a clock running, and its words changed (nothing goes at half time, in added time between reads, or while the
 * clock holds). What a second costs: each box is 340 dp across at up to 2.75 pixels a dp, 935 by 113 pixels, 413 KiB,
 * and a notification carries the folded box and the opened ones, so its parcel with the pixels in it is 854 KB for
 * one game and 2.1 MB for four (measured). Drawing the boxes again each second, clock and all, would be up to four
 * ScoreBugs built, laid out and drawn, and all those bytes copied, every second. Here nothing is drawn after the
 * poll, and the pictures are kept in shared memory (Bitmap.asShared, Android 12 and later), so a second's
 * notification is a few kilobytes: the words, and the pictures' file descriptors. On Android 8 to 11 the same pixels
 * are copied each time (a millisecond or so). What no way avoids is the phone redrawing the notification (a custom
 * view is inflated afresh on every update), a few milliseconds a second: hence the screen and words checks. Never
 * more than one a second, under Android's limit of five, and silent (setOnlyAlertOnce, the quiet Live score channel).
 */
final class LiveCard {
    /** Opened out, at most this many games. */
    static final int MAX = 4;
    /** The dot before the clock, green while the game is on (ScoreBug's). */
    static final String DOT = "\u2022";
    /** The minutes added: on red, a thin space each side (ScoreBug's chip). */
    private static final int ADDED_RED = 0xFFE0213A;
    private static final char THIN = '\u2009';

    /** The games drawn, the folded one first, and each one's box. */
    final List<Game> games;
    private final List<Bitmap> rows;

    private LiveCard(List<Game> games, List<Bitmap> rows) {
        this.games = Collections.unmodifiableList(games);
        this.rows = rows;
    }

    /** The live games among the followed ones drawn; null when none is live or none could be drawn. Off the main thread. */
    static LiveCard draw(Context c, List<Game> followed) {
        List<Game> games = new ArrayList<>();
        List<Bitmap> rows = new ArrayList<>();
        for (Game g : live(followed)) {
            Bitmap b;
            try {
                b = NotifCard.row(c, g);
            } catch (RuntimeException e) {
                b = null;
            }
            if (b == null) continue;
            // In shared memory: each second's notification then carries the picture's file descriptor, not its pixels.
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    b = b.asShared();
                } catch (RuntimeException e) {
                    // sent as pixels, as on older phones
                }
            }
            games.add(g);
            rows.add(b);
        }
        return games.isEmpty() ? null : new LiveCard(games, rows);
    }

    /** The live games among the followed, in their order, four at most. Pure. */
    static List<Game> live(List<Game> followed) {
        List<Game> out = new ArrayList<>();
        if (followed == null) return out;
        for (Game g : followed) {
            if (g != null && g.state == Game.LIVE && out.size() < MAX) out.add(g);
        }
        return out;
    }

    /**
     * A game's clock for its line, as the app shows it: GameClock's {"67:23", ""} or {"45:00", "+2"}; {"HT", ""} at half
     * time; ESPN's own words when it gives no clock.
     */
    static String[] clock(Game g, long now) {
        String[] shown = GameClock.show(g, now);
        if (shown != null) return shown;
        String words = g.halftime ? "HT" : g.state == Game.POST ? "FT" : g.detail == null ? "" : g.detail;
        return new String[]{words, ""};
    }

    /** The line's words: "\u2022 67:23", "\u2022 45:00 +2" (the minutes added on their chip, a thin space each side). Pure. */
    static String plain(String[] clock) {
        String s = DOT + " " + clock[0];
        return clock[1].isEmpty() ? s : s + " " + THIN + clock[1] + THIN;
    }

    /** Every game's line as it stands at now, together: when it is not what was sent, the notification goes again. */
    String words(long now) {
        return words(games, now);
    }

    static String words(List<Game> games, long now) {
        StringBuilder s = new StringBuilder();
        for (Game g : games) s.append(plain(clock(g, now))).append('\n');
        return s.toString();
    }

    /** Whether a clock is going at now (running, or in added time): AlertService looks again on the next second. */
    boolean ticks(long now) {
        return ticks(games, now);
    }

    static boolean ticks(List<Game> games, long now) {
        for (Game g : games) if (GameClock.show(g, now) != null) return true;
        return false;
    }

    /** Milliseconds to the next whole second, so the clock turns with the app's (LiveClock's). Pure. */
    static long delay(long now) {
        return 1000 - Math.floorMod(now, 1000L);
    }

    /** Folded: the first game's box. */
    RemoteViews folded(Context c, long now) {
        return row(c, 0, now);
    }

    /** Opened out: every game's box, one under another. */
    RemoteViews open(Context c, long now) {
        RemoteViews all = new RemoteViews(c.getPackageName(), R.layout.sports_notif_live);
        for (int i = 0; i < games.size(); i++) all.addView(R.id.sports_notif_live_rows, row(c, i, now));
        return all;
    }

    private RemoteViews row(Context c, int i, long now) {
        Game g = games.get(i);
        String[] clock = clock(g, now);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.sports_notif_live_row);
        v.setImageViewBitmap(R.id.sports_notif_row_card, rows.get(i));
        v.setContentDescription(R.id.sports_notif_row_card, g.homeName + " " + g.score() + " " + g.awayName + ", " + LiveClock.join(clock, ""));
        v.setTextViewText(R.id.sports_notif_row_clock, line(clock, c.getColor(R.color.live_green)));
        return v;
    }

    /** plain(clock) in the box's colours: the dot green (every game here is on), the clock pale, the minutes added white on red. */
    private static CharSequence line(String[] clock, int dot) {
        SpannableStringBuilder t = new SpannableStringBuilder(plain(clock));
        t.setSpan(new ForegroundColorSpan(dot), 0, DOT.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (!clock[1].isEmpty()) {
            int from = t.length() - clock[1].length() - 2, to = t.length();
            t.setSpan(new BackgroundColorSpan(ADDED_RED), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setSpan(new ForegroundColorSpan(Color.WHITE), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setSpan(new RelativeSizeSpan(0.9f), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return t;
    }
}
