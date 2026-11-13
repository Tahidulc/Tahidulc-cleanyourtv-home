package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;
import java.util.Locale;

/**
 * The channel's own scoreboard found on the screen, and its clock read off it, while a match plays in TiviMate under
 * the board (BoardCover's tick, each second), as Homefield does on the phone from its own picture. Pictures of the
 * screen (ScreenShots), a few a second for some seconds, made small and grey for BugFinder: the box whose edges stay put
 * while the picture moves under them, its clock's seconds flipping, is the channel's scoreboard, and the same kind of
 * still outline in a corner its logo. The board is then put over the box and the cover over the logo, on this channel
 * from now on (BoardPlace's seen places, under any the family set by hand), and the box is read (ChannelReader,
 * Google's offline text reader) for the channel's clock, which the board then counts from (BoardCover.anchorSeen).
 * On Android 14 and later the pictures are of TiviMate's window alone, so all of it goes on unseen under the board and
 * the clock is read again every minute. Before that, the pictures show the launcher's own windows too: the board and
 * the cover stand aside while it looks (once a channel, the first time a match is on it), and the clock is read again
 * only when the half on has none, the board stepping aside for a moment each time. Nothing is kept but the places.
 */
final class ChannelSight {
    private ChannelSight() {
    }

    /** The finder's frames: small, for its sums. */
    static final int FIND_W = 192, FIND_H = 108;
    /** A look lasts at most FIND_MS; nothing found, it is not tried again on the channel for RETRY_MS (an advert, a replay). */
    static final long FIND_MS = 24_000L, RETRY_MS = 5 * 60_000L;
    /** The clock read again this often where that is unseen (Android 14), and at most this often with the board aside. */
    static final long READ_MS = 60_000L, READ_ASIDE_MS = 40_000L;
    /** Reads with the board aside, at most, in a half. */
    static final int ASIDES = 4;
    /** The height the box is made for the reader (its words then about 40 px high). */
    private static final int READ_H = 120;
    private static final RectF ALL = new RectF(0, 0, 1, 1);

    private static Handler main;
    private static Context app;
    private static String channel, name = "", gameId, detail = "";
    private static BugFinder finder;
    private static long lookFrom, notBefore, lastTick, readAt;
    private static boolean looking, lookUnseen, reading, readAside;
    private static RectF box, logo;
    private static int asides, asidePeriod, lookTries;
    private static ChannelReader reader;
    private static String note = "";
    /** Asked to look again now (the Scoreboard card): what was seen and set by hand on the channel forgotten. */
    private static boolean again;

    private static Handler main() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("channel_sight", Context.MODE_PRIVATE);
    }

    /** On unless switched off on the Scoreboard card. */
    static boolean on(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static void setOn(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
        if (!on) stop();
    }

    /** Whether pictures of the screen can be had here at all (Android 11 and later, with the helper on). */
    static boolean possible() {
        return ScreenShots.available();
    }

    /** What it last saw, for the Scoreboard card. */
    static String note() {
        return note;
    }

    /** Whether the board and the cover are to stand aside now (a look or a read where the pictures would show them). */
    static boolean aside() {
        return looking && !lookUnseen || reading && readAside;
    }

    /** Looked for again on the channel on now: what was seen there, and set by hand, let go. */
    static void lookAgain(Context c) {
        again = true;
        notBefore = 0;
        box = null;
        logo = null;
        lookTries = 0;
        if (channel != null) {
            BoardPlace.forget(c, name);
            prefs(c).edit().remove("b." + channel).remove("l." + channel).apply();
        }
        BoardCover.sightChanged();
    }

    /** Everything stopped (switched off, or the board gone). */
    static void stop() {
        boolean was = aside();
        looking = false;
        finder = null;
        reading = false;
        readAside = false;
        if (main != null) main.removeCallbacksAndMessages(null);
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (was) BoardCover.sightChanged();
    }

    // ---- each second, with the match on (main thread) ----------------------------------------------------------------

    /** One look while the board is up for game id's live match over TiviMate, on the channel named (if known). */
    static void tick(Context c, String id, String channelName, String espnDetail) {
        app = c.getApplicationContext();
        lastTick = SystemClock.uptimeMillis();
        detail = espnDetail == null ? "" : espnDetail;
        if (!on(app) || !ScreenShots.available() || id == null) {
            if (looking || reading) stop();
            return;
        }
        String key = BoardPlace.key(channelName);
        if (!key.equals(channel) || !id.equals(gameId)) {
            // Another channel (or game): what was seen on the last one let go, this one's taken up.
            stop();
            channel = key;
            name = channelName == null ? "" : channelName;
            gameId = id;
            box = rect(boxOf(prefs(app).getString("b." + key, null)));
            logo = rect(boxOf(prefs(app).getString("l." + key, null)));
            readAt = 0;
            asides = 0;
            notBefore = 0;
            lookTries = 0;
        }
        long now = SystemClock.uptimeMillis();
        // A read that never came back (the reader stuck): let go after a while.
        if (reading && now - readAt > 15_000L) {
            reading = false;
            readAside = false;
            BoardCover.sightChanged();
        }
        if (looking || reading) return;
        if ((box == null || again) && now >= notBefore && lookTries < 3) {
            again = false;
            look();
            return;
        }
        if (box == null) return;
        boolean unseen = ScreenShots.windowOnly();
        if (unseen) {
            if (now - readAt >= READ_MS) read(false);
            return;
        }
        // The pictures show the board: only when the half on has no clock from the channel yet, now and then.
        int period = BoardCover.periodOf(detail);
        if (period != asidePeriod) {
            asidePeriod = period;
            asides = 0;
        }
        if (!BoardCover.clockSet(app, detail) && asides < ASIDES && now - readAt >= READ_ASIDE_MS) {
            asides++;
            read(true);
        }
    }

    // ---- the look: where the channel's own scoreboard and logo are --------------------------------------------------

    private static final Runnable shoot = ChannelSight::shoot;

    private static void look() {
        looking = true;
        lookTries++;
        lookUnseen = ScreenShots.windowOnly();
        lookFrom = SystemClock.uptimeMillis();
        finder = new BugFinder(FIND_W, FIND_H, Math.max(ScreenShots.gap(), 350L));
        note = "Looking for the channel's scoreboard" + (name.isEmpty() ? "" : " on " + name) + "…";
        if (!lookUnseen) BoardCover.sightChanged();
        // Where the pictures show the board, a moment for it to stand aside first.
        main().removeCallbacks(shoot);
        main().postDelayed(shoot, lookUnseen ? 0 : 300L);
    }

    private static void shoot() {
        if (!looking || finder == null) return;
        long now = SystemClock.uptimeMillis();
        // The board gone meanwhile (half time, Home, another app): the look stops with it.
        if (now - lastTick > 3_000L) {
            stop();
            return;
        }
        if (now - lookFrom > FIND_MS) {
            found(null, null);
            return;
        }
        BugFinder f = finder;
        boolean asked = ScreenShots.take(new RectF[]{ALL}, new int[][]{{FIND_W, FIND_H}}, (parts, at, own) -> {
            if (!looking || f != finder) return;
            if (parts[0] != null) {
                // A picture of the whole screen while the board stays put (TiviMate's window paused a moment): the board is in
                // it, so it is not looked at.
                if (own || !lookUnseen) f.add(ScreenShots.grey(parts[0]));
                parts[0].recycle();
            }
            int want = ScreenShots.gap() >= 700L ? 14 : 24;
            if (f.frames() >= want && SystemClock.uptimeMillis() - lookFrom >= 6_000L) {
                BugFinder.Box b = f.find();
                if (b != null) {
                    found(b, f.logo(b));
                    return;
                }
            }
            main().postDelayed(shoot, 60L);
        });
        if (!asked) main().postDelayed(shoot, 120L);
    }

    /** When the channel's own scoreboard was last seen on each channel (found, or its clock read), and for which game. */
    private static final java.util.Map<String, Long> seen = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, String> seenFor = new java.util.concurrent.ConcurrentHashMap<>();

    /** When the channel's own scoreboard was last seen on the channel named (wall clock ms); 0 never since the start. */
    static long seenAt(String channelName) {
        Long t = seen.get(BoardPlace.key(channelName));
        return t == null ? 0 : t;
    }

    /** Where the channel's own scoreboard was found on the channel named, {left, top, right, bottom} as fractions; null when never. */
    static float[] seenBox(Context c, String channelName) {
        return boxOf(prefs(c).getString("b." + BoardPlace.key(channelName), null));
    }

    /** The game the scoreboard seen last on the channel named was being read for; null when none. */
    static String seenFor(String channelName) {
        return seenFor.get(BoardPlace.key(channelName));
    }

    private static void markSeen() {
        if (channel == null) return;
        seen.put(channel, System.currentTimeMillis());
        if (gameId != null) seenFor.put(channel, gameId);
    }

    /** The look over: what it found (b null: nothing), kept for the channel, the board put over it, its clock read. */
    private static void found(BugFinder.Box b, BugFinder.Box l) {
        looking = false;
        finder = null;
        main().removeCallbacks(shoot);
        if (b == null) {
            notBefore = SystemClock.uptimeMillis() + RETRY_MS;
            note = "No scoreboard of the channel's seen" + (name.isEmpty() ? "" : " on " + name) + "; looked for again in 5 minutes";
            if (app != null) AlertLog.note(app, "channel's own scoreboard not seen" + (name.isEmpty() ? "" : " on " + name));
            BoardCover.sightChanged();
            return;
        }
        box = new RectF(b.left, b.top, b.right, b.bottom);
        logo = l == null ? null : new RectF(l.left, l.top, l.right, l.bottom);
        markSeen();
        if (app != null) {
            prefs(app).edit().putString("b." + channel, encode(box)).putString("l." + channel, logo == null ? "" : encode(logo)).apply();
            if (!BoardPlace.setByHand(app, name)) {
                android.util.DisplayMetrics dm = app.getResources().getDisplayMetrics();
                float sw = Math.max(dm.widthPixels, dm.heightPixels), sh = Math.min(dm.widthPixels, dm.heightPixels);
                // The logo's place left alone where see-through has found the logo itself.
                boolean found = BoardPlace.logoFound(app, name);
                BoardPlace p = BoardPlace.over(BoardPlace.get(app, name), arr(box), logo == null || found ? null : arr(logo),
                        BoardCover.tallNow(app), sw, sh);
                BoardPlace.putSeen(app, name, p);
            }
            AlertLog.note(app, "channel's own scoreboard seen" + (name.isEmpty() ? "" : " on " + name) + ": " + b
                    + (l != null ? ", its logo " + l : ""));
        }
        note = "Scoreboard seen " + where(box.centerX(), box.centerY()) + (logo != null ? ", logo " + where(logo.centerX(), logo.centerY()) : "")
                + (name.isEmpty() ? "" : " on " + name);
        // Read at once, while the board still stands aside where the pictures show it.
        read(!lookUnseen);
        BoardCover.sightChanged();
    }

    // ---- the read: the channel's clock -------------------------------------------------------------------------------

    private static void read(boolean stepAside) {
        if (box == null || reading) return;
        reading = true;
        readAside = stepAside;
        readAt = SystemClock.uptimeMillis();
        if (stepAside) {
            BoardCover.sightChanged();
            main().postDelayed(() -> takeRead(0), 300L);
        } else {
            takeRead(0);
        }
    }

    private static void takeRead(int tries) {
        if (!reading || box == null) return;
        float padY = box.height() * 0.15f, padX = padY * 9f / 16f;
        RectF r = new RectF(Math.max(0, box.left - padX), Math.max(0, box.top - padY), Math.min(1, box.right + padX), Math.min(1, box.bottom + padY));
        boolean asked = ScreenShots.take(new RectF[]{r}, new int[][]{{0, READ_H}}, (parts, at, own) -> {
            boolean wasAside = readAside;
            readAside = false;
            if (wasAside) BoardCover.sightChanged();
            Bitmap b = parts[0];
            // Read with the board in place but the whole screen came back (TiviMate's window paused a moment): the board's
            // own clock may be in it, so it is not read.
            if (b != null && !own && !wasAside) {
                b.recycle();
                reading = false;
                return;
            }
            if (b == null || !reading) {
                reading = false;
                return;
            }
            if (reader == null) reader = new ChannelReader();
            boolean ok = reader.read(b, (lines, boxes) -> {
                reading = false;
                b.recycle();
                gotLines(lines, at);
            });
            if (!ok) {
                reading = false;
                b.recycle();
            }
        });
        if (!asked) {
            if (tries < 10) {
                main().postDelayed(() -> takeRead(tries + 1), 150L);
            } else {
                reading = false;
                if (readAside) {
                    readAside = false;
                    BoardCover.sightChanged();
                }
            }
        }
    }

    /** What the channel's scoreboard said at the moment at: its clock, the board's from then on when it is this game's. */
    private static void gotLines(List<String> lines, long at) {
        if (app == null) return;
        int clock = clockOf(lines, BoardCover.firstMinute(detail));
        if (clock < 0) {
            note = "Channel's clock not read" + (lines.isEmpty() ? "" : " (it said " + String.join(" · ", lines) + ")");
            return;
        }
        int half = BoardCover.periodOf(detail);
        markSeen();
        BoardCover.anchorSeen(app, gameId, clock, at, half > 0 ? half : BoardCover.periodOfSecs(clock));
        note = "Channel's clock read: " + mmss(clock) + (name.isEmpty() ? "" : " on " + name);
    }

    /**
     * Pure: the game's clock in the lines read off the channel's scoreboard, in seconds: the first time on it that is
     * within a few minutes of the scores' own minute (espnMinute, 0: not known; the stream runs a little behind them),
     * so an added-time clock beside it ("+2:15") or a time of day is never taken for it; -1 when there is none.
     */
    static int clockOf(List<String> lines, int espnMinute) {
        if (lines == null) return -1;
        java.util.regex.Pattern clock = java.util.regex.Pattern.compile("(?<![\\d:.+])(\\d{1,3})\\s?[.:]\\s?([0-5]\\d)(?![\\d:])");
        for (String raw : lines) {
            java.util.regex.Matcher m = clock.matcher(ScreenText.fix(raw));
            while (m.find()) {
                int min = Integer.parseInt(m.group(1)), sec = Integer.parseInt(m.group(2));
                if (min > 130) continue;
                if (espnMinute > 0 && (min > espnMinute + 3 || min < espnMinute - 8)) continue;
                return min * 60 + sec;
            }
        }
        return -1;
    }

    // ---- small things --------------------------------------------------------------------------------------------------

    static String mmss(int secs) {
        return (secs / 60) + ":" + String.format(Locale.ROOT, "%02d", secs % 60);
    }

    /** Pure: where on the screen a box's middle (cx, cy, fractions) is, in words ("top left"). */
    static String where(float cx, float cy) {
        String v = cy < 0.34f ? "top" : cy > 0.66f ? "bottom" : "middle";
        String h = cx < 0.34f ? "left" : cx > 0.66f ? "right" : "centre";
        return v.equals("middle") && h.equals("centre") ? "in the middle" : v + " " + h;
    }

    private static float[] arr(RectF r) {
        return new float[]{r.left, r.top, r.right, r.bottom};
    }

    static String encode(RectF r) {
        return String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", r.left, r.top, r.right, r.bottom);
    }

    /** Pure: a box written by encode, {left, top, right, bottom}; null for none or a broken one. */
    static float[] boxOf(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] f = s.split(",");
        if (f.length != 4) return null;
        try {
            float l = Float.parseFloat(f[0]), t = Float.parseFloat(f[1]), r = Float.parseFloat(f[2]), b = Float.parseFloat(f[3]);
            if (!(l >= 0 && t >= 0 && r <= 1 && b <= 1 && r > l && b > t)) return null;
            return new float[]{l, t, r, b};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static RectF rect(float[] f) {
        return f == null ? null : new RectF(f[0], f[1], f[2], f[3]);
    }
}
