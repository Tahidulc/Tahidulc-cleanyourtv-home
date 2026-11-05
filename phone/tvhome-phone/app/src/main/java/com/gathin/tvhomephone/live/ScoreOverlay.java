package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.Game;

/**
 * The scoreboard over the player's picture (ScoreBoard), with its alerts. On a game it covers the channel's own
 * scoreboard where one is found (BugFinder on the picture's frames, FrameSampler), at its size; elsewhere it sits top
 * left of the picture. Its clock is the screen's: the channel's delay (LiveAlerts, set by "It's just happened" or
 * its chips) and, once the channel's own clock has been seen to turn a minute, that moment to the second. Tap it for
 * the match centre; hold it to put it away (Options brings it back).
 */
final class ScoreOverlay implements ScoreBoard.Source {
    /** What the player tells it. */
    interface Host {
        Channel current();

        Game game();

        void openMatch();

        void play(Channel ch);
    }

    /** Whether the player's scoreboard is up (the one over other apps keeps out of its way). */
    static volatile boolean up;

    private static final int FIND_W = 256, FIND_H = 144, READ_W = 960, READ_H = 540, OCR_W = 1280, OCR_H = 720;
    private static final long SAMPLE_MS = 250, LOOK_AGAIN_MS = 3 * 60_000L, RETRY_MS = 20_000L, READ_FOR_MS = 80_000L,
            READ_AGAIN_MS = 4 * 60_000L, OCR_MS = 2000, F1_OCR_MS = 4000;

    private final Activity a;
    private final FrameLayout root;
    private final Video video;
    private final Host host;
    private final ScoreBoard board;
    private final Handler h = new Handler(Looper.getMainLooper());
    private FrameSampler sampler;
    private Bitmap small, big;
    private BugFinder finder;
    private BugFinder.Box box;
    private BugFinder.ClockReader reader;
    private int cropX, cropY, cropW, cropH;
    private long readUntil;
    private boolean started, inPip;
    private int insetLeft, insetTop;
    private String gameId = "";
    /** The channel's own logo, and Homefield's over it. */
    private BugFinder.Box logo;
    private final LogoCover cover;
    /** The channel's scoreboard read off the picture: the reader, its frame, when it last read a clock, misses. */
    private final ChannelReader ocr = new ChannelReader();
    private Bitmap ocrFrame;
    private long ocrOkAt;
    private int ocrMisses, finds;

    private final Runnable look = this::look;
    private final Runnable readClock = this::readClock;
    private final Runnable readScreen = this::readScreen;

    ScoreOverlay(Activity a, FrameLayout root, Video video, Host host) {
        this.a = a;
        this.root = root;
        this.video = video;
        this.host = host;
        board = new ScoreBoard(a, this);
        cover = new LogoCover(a);
        cover.setVisibility(View.GONE);
        // Just over the picture and its touch layer: the tile, the buttons and the sheets stay over them.
        root.addView(cover, Math.min(2, root.getChildCount()), new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.START));
        root.addView(board.view, Math.min(3, root.getChildCount()), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) h.post(this::place);
        });
        video.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> h.post(this::place));
    }

    void start() {
        if (started || !ScoreBoard.on(a)) {
            board.view.setVisibility(ScoreBoard.on(a) ? View.VISIBLE : View.GONE);
            return;
        }
        started = true;
        up = true;
        board.view.setVisibility(inPip ? View.GONE : View.VISIBLE);
        board.start();
        if (sampler == null) sampler = new FrameSampler();
        h.postDelayed(look, 4000);
        place();
    }

    void stop() {
        started = false;
        up = false;
        board.stop();
        h.removeCallbacksAndMessages(null);
        if (sampler != null) {
            sampler.quit();
            sampler = null;
        }
        ocr.close();
    }

    /** The channel's game from its tile (null: another channel, or none). */
    void game(Game g) {
        String id = g == null || g.id == null ? "" : g.id;
        if (id.equals(gameId) && g != null) return;
        gameId = id;
        box = null;
        logo = null;
        finder = null;
        reader = null;
        ocrOkAt = 0;
        ocrMisses = 0;
        finds = 0;
        board.changed();
        place();
        h.removeCallbacks(look);
        h.removeCallbacks(readClock);
        if (started) h.postDelayed(look, 4000);
    }

    /** The channel's delay changed (the alerts' chips, "It's just happened"). */
    void delayChanged() {
        board.behind();
    }

    void pip(boolean in) {
        inPip = in;
        board.view.setVisibility(in || !ScoreBoard.on(a) ? View.GONE : View.VISIBLE);
        place();
        // Back from the small window: the channel's scoreboard looked for, and its clock read, again.
        if (!in && started) {
            reader = null;
            finder = null;
            h.removeCallbacks(look);
            h.removeCallbacks(readClock);
            h.removeCallbacks(readScreen);
            h.postDelayed(look, 2000);
        }
    }

    void inset(int left, int top) {
        insetLeft = left;
        insetTop = top;
        place();
    }

    boolean on() {
        return ScoreBoard.on(a);
    }

    /** Switched on or off from Options. */
    void set(boolean on) {
        ScoreBoard.setOn(a, on);
        if (on) {
            start();
        } else {
            stop();
            board.view.setVisibility(View.GONE);
        }
    }

    /** Whether its clock runs from the channel's own (seen turning a minute, or read). */
    boolean matched() {
        return board.screen().matched();
    }

    /** Whether the channel's clock was read off its scoreboard lately. */
    boolean reading() {
        return System.currentTimeMillis() - ocrOkAt < 30_000L;
    }

    /** The look or the logo's cover changed in Options. */
    void restyle() {
        board.bug.style(ScoreBoard.style(a));
        cover.look(ScoreBoard.logoCover(a));
        place();
        if (started && ScoreBoard.logoCover(a) != LogoCover.Look.OFF) {
            h.removeCallbacks(look);
            h.post(look);
        }
    }

    boolean found() {
        return box != null;
    }

    // ---- ScoreBoard.Source -----------------------------------------------------------------------------------------

    @Override public Channel channel() {
        return host.current();
    }

    @Override public Game game() {
        return host.game();
    }

    @Override public long behindMs(boolean channelGame) {
        Channel ch = host.current();
        // The channel's own game, or F1 on an F1 channel: its picture's delay. A game elsewhere: as it is.
        boolean own = channelGame || (host.game() == null && ScoreBoard.f1Channel(ch));
        return own && ch != null ? LiveAlerts.delay(a, ch.key) * 1000L : 0;
    }

    @Override public void tapped() {
        if (host.game() != null) host.openMatch();
    }

    @Override public void held() {
        set(false);
        Toast.makeText(a, R.string.live4_board_off, Toast.LENGTH_LONG).show();
    }

    @Override public void alertTapped(Events.Alert alert) {
        Game own = host.game();
        if (alert == null || alert.game == null || (own != null && alert.game.id != null && alert.game.id.equals(own.id))) return;
        Context app = a.getApplicationContext();
        App.io.execute(() -> {
            Channel ch = AlsoShowing.best(app, alert.game);
            App.main.post(() -> {
                if (a.isFinishing()) return;
                if (ch == null) Toast.makeText(a, R.string.live3_not_in_playlist, Toast.LENGTH_SHORT).show();
                else host.play(ch);
            });
        });
    }

    // ---- where it sits ----------------------------------------------------------------------------------------------

    /** Over the channel's scoreboard at its size when found; else top left of the picture, 30 dp high. */
    private void place() {
        float d = a.getResources().getDisplayMetrics().density;
        View frame = video.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) board.view.getLayoutParams();
        LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) board.bug.getLayoutParams();
        float fx = 0, fy = 0, fw = root.getWidth(), fh = root.getHeight();
        if (frame != null && frame.getWidth() > 0) {
            int[] f = new int[2], r = new int[2];
            frame.getLocationInWindow(f);
            root.getLocationInWindow(r);
            fx = f[0] - r[0];
            fy = f[1] - r[1];
            fw = frame.getWidth() * video.getScaleX();
            fh = frame.getHeight() * video.getScaleY();
        }
        boolean over = box != null && ScoreBoard.cover(a) && board.channelGame();
        int left, top;
        if (over) {
            // A little over each edge, so none of the channel's shows round it.
            float pad = Math.max(2 * d, box.height() * fh * 0.08f);
            left = Math.round(fx + box.left * fw - pad);
            top = Math.round(fy + box.top * fh - pad);
            int w = Math.round(box.width() * fw + 2 * pad), ht = Math.round(box.height() * fh + 2 * pad);
            // Off the screen (zoomed right in): back to its own place.
            over = left >= 0 && top >= 0 && left + w <= root.getWidth() && top + ht <= root.getHeight() && w > 20 * d && ht > 10 * d;
            if (over) {
                bp.width = w;
                bp.height = ht;
            }
        } else {
            left = top = 0;
        }
        if (!over) {
            boolean upright = root.getHeight() > root.getWidth();
            bp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            bp.height = Math.round((upright ? 24 : 30) * d);
            left = Math.round(Math.max(fx, 0) + 14 * d) + (fx <= insetLeft ? insetLeft : 0);
            top = Math.round(Math.max(fy, 0) + 12 * d) + (fy <= insetTop ? insetTop : 0);
            // The channel's own scoreboard found and not covered: just under it.
            if (box != null && board.channelGame() && box.left < 0.5f) top = Math.round(fy + box.bottom * fh + 6 * d);
        }
        board.bug.setLayoutParams(bp);
        if (lp.leftMargin != left || lp.topMargin != top) {
            lp.leftMargin = left;
            lp.topMargin = top;
            board.view.setLayoutParams(lp);
        }
        // Homefield's mark over the channel's logo, its size and a little over.
        LogoCover.Look look = ScoreBoard.logoCover(a);
        boolean show = logo != null && look != LogoCover.Look.OFF && started && !inPip && ScoreBoard.on(a);
        if (show) {
            float pad = Math.max(2 * d, logo.height() * fh * 0.12f);
            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cover.getLayoutParams();
            cp.leftMargin = Math.round(fx + logo.left * fw - pad);
            cp.topMargin = Math.round(fy + logo.top * fh - pad);
            cp.width = Math.round(logo.width() * fw + 2 * pad);
            cp.height = Math.round(logo.height() * fh + 2 * pad);
            show = cp.leftMargin >= 0 && cp.topMargin >= 0 && cp.leftMargin + cp.width <= root.getWidth() && cp.topMargin + cp.height <= root.getHeight();
            if (show) {
                cover.look(look);
                cover.setLayoutParams(cp);
            }
        }
        cover.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ---- finding the channel's scoreboard --------------------------------------------------------------------------

    /** A few seconds of small frames for BugFinder, then the scoreboard placed; looked at again now and then. */
    private void look() {
        h.removeCallbacks(look);
        boolean scoreboard = ScoreBoard.cover(a) && board.channelGame(), logos = ScoreBoard.logoCover(a) != LogoCover.Look.OFF;
        if (!started || inPip || sampler == null) return;
        // F1's tower read for its lap, on an F1 channel.
        if (ScoreBoard.reads(a) && host.game() == null && ScoreBoard.f1Channel(host.current()) && !screenLoop()) readSoon(1000);
        if (!scoreboard && !logos) {
            h.postDelayed(look, RETRY_MS);
            return;
        }
        if (finder == null) {
            finder = new BugFinder(FIND_W, FIND_H);
            if (small == null) small = Bitmap.createBitmap(FIND_W, FIND_H, Bitmap.Config.ARGB_8888);
        }
        boolean asked = sampler.copy(video.surface(), small, (b, at) -> {
            if (b != null) finder.add(FrameSampler.grey(b, 0, 0, FIND_W, FIND_H));
            BugFinder f = finder;
            boolean done = f != null && f.frames() >= BugFinder.ENOUGH;
            BugFinder.Box found = done && scoreboard ? f.find() : null;
            BugFinder.Box mark = done && logos ? f.logo(found) : null;
            h.post(() -> {
                if (!started || f != finder) return;
                if (!done) {
                    h.postDelayed(look, SAMPLE_MS);
                    return;
                }
                finder = null;
                if (mark != null || logo != null) {
                    logo = mark;
                    place();
                }
                if (scoreboard && found == null && ++finds >= 2 && box == null) locate();
                if (found != null) {
                    boolean moved = box == null || Math.abs(found.left - box.left) > 0.02f || Math.abs(found.top - box.top) > 0.02f
                            || Math.abs(found.width() - box.width()) > 0.03f;
                    if (moved) {
                        box = found;
                        reader = null;
                        place();
                    }
                    // Its clock read: its digits read off it, else seen turning a minute.
                    if (reader == null) startReading();
                    if (ScoreBoard.reads(a) && !screenLoop()) readSoon(500);
                }
                h.postDelayed(look, found != null || box != null ? LOOK_AGAIN_MS : RETRY_MS);
            });
        });
        // No picture to copy yet: a moment later.
        if (!asked) h.postDelayed(look, 1000);
    }

    // ---- reading the channel's clock -----------------------------------------------------------------------------

    private void startReading() {
        if (box == null) return;
        cropX = Math.max(0, Math.round(box.left * READ_W) - 2);
        cropY = Math.max(0, Math.round(box.top * READ_H) - 2);
        cropW = Math.min(READ_W - cropX, Math.round(box.width() * READ_W) + 4);
        cropH = Math.min(READ_H - cropY, Math.round(box.height() * READ_H) + 4);
        if (cropW < 16 || cropH < 6) return;
        if (big == null) big = Bitmap.createBitmap(READ_W, READ_H, Bitmap.Config.ARGB_8888);
        reader = new BugFinder.ClockReader(cropW, cropH);
        readUntil = System.currentTimeMillis() + READ_FOR_MS;
        h.removeCallbacks(readClock);
        h.post(readClock);
    }

    /** Crops of the channel's scoreboard four times a second, until its clock is seen turning a minute. */
    private void readClock() {
        h.removeCallbacks(readClock);
        BugFinder.ClockReader r = reader;
        if (!started || inPip || r == null || sampler == null) return;
        // Its digits are being read: no need to watch for the minute turning.
        if (reading()) {
            h.postDelayed(this::startReading, READ_AGAIN_MS);
            return;
        }
        if (System.currentTimeMillis() > readUntil) {
            // Not seen this time (a stopped clock, a replay): another go in a while.
            h.postDelayed(this::startReading, READ_AGAIN_MS);
            return;
        }
        int x = cropX, y = cropY, w = cropW, ht = cropH;
        boolean asked = sampler.copy(video.surface(), big, (b, at) -> {
            boolean turned = b != null && r.add(FrameSampler.grey(b, x, y, w, ht));
            h.post(() -> {
                if (!started || r != reader) return;
                if (turned && board.screen().minuteTurned(at)) {
                    // Matched to the second: looked at again in a few minutes, to stay so.
                    h.postDelayed(this::startReading, READ_AGAIN_MS);
                    return;
                }
                h.postDelayed(readClock, SAMPLE_MS);
            });
        });
        if (!asked) h.postDelayed(readClock, 1000);
    }

    // ---- reading the channel's scoreboard ----------------------------------------------------------------------------

    /**
     * Every two seconds: the channel's scoreboard read (ChannelReader). Its clock sets the board's to the second and
     * measures how far the picture runs behind the scores, which the channel's delay is then set to (so the score and
     * the alerts change as the picture does); a newer score it shows is shown at once. On an F1 channel, the tower's
     * corner for the lap or the time left.
     */
    /** The next read in ms (only ever one waiting). */
    private void readSoon(long ms) {
        h.removeCallbacks(readScreen);
        h.postDelayed(readScreen, ms);
    }

    /** Whether the reads are going (one ran in the last ten seconds). */
    private boolean screenLoop() {
        return System.currentTimeMillis() - screenAt < 10_000L;
    }

    private long screenAt;

    private void readScreen() {
        h.removeCallbacks(readScreen);
        screenAt = System.currentTimeMillis();
        if (!started || inPip || sampler == null || !ScoreBoard.reads(a)) return;
        boolean f1 = host.game() == null && ScoreBoard.f1Channel(host.current());
        BugFinder.Box b = box;
        if (!f1 && (b == null || !board.channelGame())) return;
        if (ocrFrame == null) ocrFrame = Bitmap.createBitmap(OCR_W, OCR_H, Bitmap.Config.ARGB_8888);
        boolean asked = sampler.copy(video.surface(), ocrFrame, (frame, at) -> {
            Bitmap crop = frame == null ? null : f1 ? crop(frame, 0f, 0f, 0.34f, 0.24f, 0) : crop(frame, b.left, b.top, b.right, b.bottom, 0.15f);
            h.post(() -> {
                if (!started || crop == null) {
                    readSoon(OCR_MS);
                    return;
                }
                boolean reading = ocr.read(crop, (lines, boxes) -> {
                    if (!started) return;
                    if (f1) {
                        ScreenText.Reading r = ScreenText.f1(lines);
                        if (r.any()) board.f1Screen(r.lap, r.laps, r.left, at);
                        readSoon(F1_OCR_MS);
                        return;
                    }
                    football(lines, at);
                    readSoon(OCR_MS);
                });
                if (!reading) readSoon(OCR_MS);
            });
        });
        if (!asked) readSoon(1000);
    }

    /** What the channel's scoreboard said at the moment at. */
    private void football(java.util.List<String> lines, long at) {
        ScreenText.Reading r = ScreenText.football(lines);
        if (r.clock >= 0 && board.screen().screenClock(r.clock, at)) {
            ocrOkAt = System.currentTimeMillis();
            ocrMisses = 0;
            long behind = board.screen().measuredBehind();
            Channel ch = host.current();
            if (behind >= 0 && ch != null) {
                int s = Math.round(behind / 1000f);
                // The channel's delay from its own clock: the score and the alerts then wait just that long.
                if (Math.abs(s - LiveAlerts.delay(a, ch.key)) >= 3) {
                    LiveAlerts.setDelay(a, ch.key, s);
                    board.behind();
                }
            }
        } else if (++ocrMisses >= 15) {
            // Nothing read for half a minute (a replay, an advert): looked for again.
            ocrMisses = 0;
        }
        board.screen().screenScore((home, away) -> ScreenText.shows(lines, home, away), System.currentTimeMillis());
    }

    /**
     * BugFinder found nothing twice: the top of the picture read instead, for a line with a game's clock in it, which
     * is the scoreboard's (its box widened to the scoreboard's ground round the words).
     */
    private void locate() {
        if (!ScoreBoard.reads(a) || sampler == null) return;
        if (ocrFrame == null) ocrFrame = Bitmap.createBitmap(OCR_W, OCR_H, Bitmap.Config.ARGB_8888);
        sampler.copy(video.surface(), ocrFrame, (frame, at) -> {
            Bitmap band = frame == null ? null : crop(frame, 0f, 0f, 1f, 0.32f, 0);
            h.post(() -> {
                if (!started || band == null) return;
                float sx = band.getWidth() / (float) OCR_W, sy = band.getHeight() / (float) (OCR_H * 0.32f);
                ocr.read(band, (lines, boxes) -> {
                    for (int i = 0; i < lines.size(); i++) {
                        if (ScreenText.football(java.util.Collections.singletonList(lines.get(i))).clock < 0) continue;
                        android.graphics.Rect rr = boxes.get(i);
                        if (rr.isEmpty() || box != null) return;
                        float hh = rr.height() / sy / OCR_H, pad = hh * 0.6f;
                        float l = rr.left / sx / OCR_W - pad, t = rr.top / sy / OCR_H - pad * 0.6f;
                        float r = rr.right / sx / OCR_W + pad, bt = rr.bottom / sy / OCR_H + pad * 0.6f;
                        box = new BugFinder.Box(Math.max(0, l), Math.max(0, t), Math.min(1, r), Math.min(0.4f, bt), true);
                        place();
                        if (!screenLoop()) readSoon(0);
                        return;
                    }
                });
            });
        });
    }

    /**
     * Part of a frame (fractions of it), padded by pad of its height each way, made big enough to read: its words at
     * least about 40 pixels high. Null when it can't be cut.
     */
    private static Bitmap crop(Bitmap frame, float l, float t, float r, float b, float pad) {
        int fw = frame.getWidth(), fh = frame.getHeight();
        float ph = (b - t) * pad;
        int x0 = Math.max(0, Math.round((l - ph * fh / fw) * fw)), y0 = Math.max(0, Math.round((t - ph) * fh));
        int x1 = Math.min(fw, Math.round((r + ph * fh / fw) * fw)), y1 = Math.min(fh, Math.round((b + ph) * fh));
        if (x1 - x0 < 8 || y1 - y0 < 6) return null;
        try {
            Bitmap c = Bitmap.createBitmap(frame, x0, y0, x1 - x0, y1 - y0);
            float k = Math.max(1f, Math.min(4f, 90f / (y1 - y0)));
            return k > 1.05f ? Bitmap.createScaledBitmap(c, Math.round((x1 - x0) * k), Math.round((y1 - y0) * k), true) : c.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- the options -------------------------------------------------------------------------------------------------

    /** Its rows in the player's options: on, over the channel's own, the alerts, over other apps; how it keeps time. */
    void options(Context c, LinearLayout box, Runnable closeSheet) {
        String state = !ScoreBoard.on(c) ? c.getString(R.string.live4_board_state_off)
                : reading() ? c.getString(R.string.live4_board_state_reading)
                : matched() ? c.getString(R.string.live4_board_state_matched)
                : found() ? c.getString(R.string.live4_board_state_found)
                : c.getString(R.string.live4_board_state_delay, channel() == null ? 0 : LiveAlerts.delay(c, channel().key));
        TextView t = Ui.text(c, state, 12, Ui.DIM, false);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        box.addView(t);
    }
}
