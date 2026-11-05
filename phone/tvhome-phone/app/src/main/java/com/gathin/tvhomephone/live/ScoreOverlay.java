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

    private static final int FIND_W = 256, FIND_H = 144, READ_W = 960, READ_H = 540;
    private static final long SAMPLE_MS = 250, LOOK_AGAIN_MS = 3 * 60_000L, RETRY_MS = 20_000L, READ_FOR_MS = 80_000L,
            READ_AGAIN_MS = 4 * 60_000L;

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

    private final Runnable look = this::look;
    private final Runnable readClock = this::readClock;

    ScoreOverlay(Activity a, FrameLayout root, Video video, Host host) {
        this.a = a;
        this.root = root;
        this.video = video;
        this.host = host;
        board = new ScoreBoard(a, this);
        // Just over the picture and its touch layer: the tile, the buttons and the sheets stay over it.
        root.addView(board.view, Math.min(2, root.getChildCount()), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
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
    }

    /** The channel's game from its tile (null: another channel, or none). */
    void game(Game g) {
        String id = g == null || g.id == null ? "" : g.id;
        if (id.equals(gameId) && g != null) return;
        gameId = id;
        box = null;
        finder = null;
        reader = null;
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
        // Back from the small window: the channel's scoreboard looked for, and its clock read, again.
        if (!in && started) {
            reader = null;
            finder = null;
            h.removeCallbacks(look);
            h.removeCallbacks(readClock);
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

    /** Whether its clock runs from the channel's own (seen turning a minute). */
    boolean matched() {
        return board.screen().matched();
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
        boolean cover = box != null && ScoreBoard.cover(a) && board.channelGame();
        int left, top;
        if (cover) {
            // A little over each edge, so none of the channel's shows round it.
            float pad = Math.max(2 * d, box.height() * fh * 0.08f);
            left = Math.round(fx + box.left * fw - pad);
            top = Math.round(fy + box.top * fh - pad);
            int w = Math.round(box.width() * fw + 2 * pad), ht = Math.round(box.height() * fh + 2 * pad);
            // Off the screen (zoomed right in): back to its own place.
            cover = left >= 0 && top >= 0 && left + w <= root.getWidth() && top + ht <= root.getHeight() && w > 20 * d && ht > 10 * d;
            if (cover) {
                bp.width = w;
                bp.height = ht;
            }
        } else {
            left = top = 0;
        }
        if (!cover) {
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
    }

    // ---- finding the channel's scoreboard --------------------------------------------------------------------------

    /** A few seconds of small frames for BugFinder, then the scoreboard placed; looked at again now and then. */
    private void look() {
        h.removeCallbacks(look);
        if (!started || inPip || !ScoreBoard.cover(a) || sampler == null) return;
        if (!board.channelGame()) {
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
            BugFinder.Box found = done ? f.find() : null;
            h.post(() -> {
                if (!started || f != finder) return;
                if (!done) {
                    h.postDelayed(look, SAMPLE_MS);
                    return;
                }
                finder = null;
                if (found != null) {
                    boolean moved = box == null || Math.abs(found.left - box.left) > 0.02f || Math.abs(found.top - box.top) > 0.02f
                            || Math.abs(found.width() - box.width()) > 0.03f;
                    if (moved) {
                        box = found;
                        reader = null;
                        place();
                    }
                    // Its clock read (a box without one simply never turns a minute).
                    if (reader == null) startReading();
                }
                h.postDelayed(look, found != null ? LOOK_AGAIN_MS : RETRY_MS);
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

    // ---- the options -------------------------------------------------------------------------------------------------

    /** Its rows in the player's options: on, over the channel's own, the alerts, over other apps; how it keeps time. */
    void options(Context c, LinearLayout box, Runnable closeSheet) {
        String state = !ScoreBoard.on(c) ? c.getString(R.string.live4_board_state_off)
                : matched() ? c.getString(R.string.live4_board_state_matched)
                : found() ? c.getString(R.string.live4_board_state_found)
                : c.getString(R.string.live4_board_state_delay, channel() == null ? 0 : LiveAlerts.delay(c, channel().key));
        TextView t = Ui.text(c, state, 12, Ui.DIM, false);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        box.addView(t);
    }
}
