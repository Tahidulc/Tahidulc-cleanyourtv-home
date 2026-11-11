package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.graphics.drawable.GradientDrawable;
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

import java.util.function.IntConsumer;

/**
 * The scoreboard over the player's picture (ScoreBoard), with its alerts. On a game it covers the channel's own
 * scoreboard where one is found (BugFinder on the picture's frames, FrameSampler), at its size; elsewhere it sits top
 * left of the picture. Its clock is the screen's: the channel's delay (LiveAlerts, set by "It's just happened" or
 * its chips) and, once the channel's own clock has been seen to turn a minute, that moment to the second. Tap it for
 * the match centre; hold it (or the mark over the logo) to move and size them by hand (Move and resize: kept for the
 * channel, BoardPlace).
 */
final class ScoreOverlay implements ScoreBoard.Source {
    /** What the player tells it. */
    interface Host {
        Channel current();

        Game game();

        void openMatch();

        void play(Channel ch);

        /** The channel's sound off (a Homefield break) or back. */
        void mute(boolean on);

        /** Move and resize is opening: the player's own tile and buttons out of the way. */
        default void hideControls() {}
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
    /** The family's own places on this channel (Move and resize), and which channel they are for. */
    private BoardPlace mine = new BoardPlace();
    private String mineKey;
    /** Move and resize is open: the shield over the picture and its panel; the picture's place as last laid out. */
    private boolean editing;
    /** The board or the mark held and being dragged (outside Move and resize). */
    private boolean carrying;
    private View shield, panel;
    private float pfx, pfy, pfw = 1, pfh = 1;
    /** The channel's scoreboard read off the picture: the reader, its frame, when it last read a clock, misses. */
    private final ChannelReader ocr = new ChannelReader();
    private Bitmap ocrFrame;
    private long ocrOkAt;
    private int ocrMisses;

    /** Broadcast mode: the channel's logo watched for its adverts, and the break up while they run. */
    private BreakWatch breaks;
    private BreakScreen breakScreen;
    private boolean manualBreak;
    private final Runnable checkBreak = this::checkBreak;
    /** Burn-in: the board and the mark over the logo moved now and then, dimmed when nothing happens. */
    private final BurnGuard guard;

    /** The classic kick-off pop-up and line-ups over the picture (PlayerIntro). */
    private final PlayerIntro intro;

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
        com.gathin.broadcast.Sfx.warm(a, com.gathin.broadcast.Sfx.SWISH, "goal-" + PlayerIntro.goalSound(a));   // loaded before they are needed
        intro = new PlayerIntro(a, root, () -> started && !inPip && !editing, (on) -> {
            // the classic line-ups fill the picture: the scoreboard and the logo's cover out of their way meanwhile
            board.view.setAlpha(on ? 0f : 1f);
            cover.setAlpha(on ? 0f : 1f);
        });
        // the strap in the board's look folds into the board: where it is over the picture, on a 1920 x 1080 picture
        intro.boardAt(() -> {
            if (pfw <= 10 || pfh <= 10 || board.view.getWidth() <= 0) return null;
            int[] b = new int[2], r = new int[2];
            board.view.getLocationInWindow(b);
            root.getLocationInWindow(r);
            float k = 1920f / pfw, kh = 1080f / pfh;
            return new float[]{(b[0] - r[0] - pfx) * k, (b[1] - r[1] - pfy) * kh, board.view.getWidth() * k, board.view.getHeight() * kh};
        });
        cover.setVisibility(View.GONE);
        // Held: dragged where the finger goes. Tapped twice: Move and resize. (The screen reader's long press: the same.)
        cover.setOnLongClickListener(v -> {
            adjust();
            return true;
        });
        // Just over the picture and its touch layer: the tile, the buttons and the sheets stay over them.
        root.addView(cover, Math.min(2, root.getChildCount()), new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.START));
        root.addView(board.view, Math.min(3, root.getChildCount()), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        guard = new BurnGuard(a, 2f, new View[]{board.view, cover}, new BurnGuard.Dims[]{board.bug, cover});
        board.changed = guard::poke;
        handles(board.bug, true);
        handles(cover, false);
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
        // Switched off in Move and resize: its clock and score go on, unseen, and the mark over the logo is its own.
        board.hidden(!ScoreBoard.boardShown(a));
        guard.start();
        if (sampler == null) sampler = new FrameSampler();
        h.postDelayed(look, 4000);
        place();
    }

    void stop() {
        if (editing) endAdjust(true);
        intro.stop();
        started = false;
        up = false;
        endBreak(true);
        guard.stop();
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
        // Another channel (or game) while places were being set: those are kept for the one they were set on.
        if (editing) endAdjust(true);
        gameId = id;
        box = null;
        readBox = null;
        lastRead = "";
        logo = null;
        breaks = null;
        endBreak(true);
        finder = null;
        reader = null;
        ocrOkAt = 0;
        ocrMisses = 0;
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
        if (in && editing) endAdjust(true);
        if (in) intro.stop();
        inPip = in;
        // In the small window the board stays, small, in the same look, its clock going on: no drops, no covers.
        board.small(in);
        board.view.setVisibility(!ScoreBoard.on(a) ? View.GONE : View.VISIBLE);
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

    /** Whether the board shows in the small window (the old text line there is then not needed). */
    boolean inSmallWindow() {
        return started && inPip && ScoreBoard.on(a) && board.showing();
    }

    /** Whether the channel's clock was read off its scoreboard lately. */
    boolean reading() {
        return System.currentTimeMillis() - ocrOkAt < 30_000L;
    }

    // ---- see-through: the channel's own logo hidden under Whitebrook Road Sports (LogoPatch) ---------------------------

    private boolean patchLoop;
    private Bitmap patchFrame;
    private final Runnable patchRun = this::patchNow;

    /**
     * See-through: the player's own frame copied (FrameSampler), the cover's place and a margin round it cut from it, and
     * its inside made up from that margin (LogoPatch) for the cover to stand on; twice a second while it is wanted.
     */
    private void patchNow() {
        if (!started || inPip || sampler == null || !cover.wantsPatch()) {
            patchLoop = false;
            if (!cover.wantsPatch()) cover.patch(null);
            return;
        }
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cover.getLayoutParams();
        float d = a.getResources().getDisplayMetrics().density;
        float m = 6 * d + cp.height * 0.16f;
        // The cover's place and its margin as fractions of the picture.
        float l = (cp.leftMargin - m - pfx) / pfw, t = (cp.topMargin - m - pfy) / pfh;
        float r = (cp.leftMargin + cp.width + m - pfx) / pfw, b = (cp.topMargin + cp.height + m - pfy) / pfh;
        float cl = (cp.leftMargin - pfx) / pfw, ct = (cp.topMargin - pfy) / pfh;
        float cr = (cp.leftMargin + cp.width - pfx) / pfw, cb = (cp.topMargin + cp.height - pfy) / pfh;
        if (patchFrame == null) patchFrame = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
        Bitmap frame = patchFrame;
        boolean asked = sampler.copy(video.surface(), frame, (got, at) -> {
            Bitmap patch = got == null ? null : patchOf(got, l, t, r, b, cl, ct, cr, cb);
            h.post(() -> {
                if (patch != null && started) cover.patch(patch);
                h.postDelayed(patchRun, 500L);
            });
        });
        if (!asked) h.postDelayed(patchRun, 250L);
    }

    /** The patch from a frame: the part (l, t, r, b) cut out, its inside (the cover, cl to cb) filled from the rest. Pure enough. */
    static Bitmap patchOf(Bitmap frame, float l, float t, float r, float b, float cl, float ct, float cr, float cb) {
        int fw = frame.getWidth(), fh = frame.getHeight();
        int x0 = Math.max(0, Math.round(l * fw)), y0 = Math.max(0, Math.round(t * fh));
        int x1 = Math.min(fw, Math.round(r * fw)), y1 = Math.min(fh, Math.round(b * fh));
        int ix0 = Math.max(x0, Math.round(cl * fw)), iy0 = Math.max(y0, Math.round(ct * fh));
        int ix1 = Math.min(x1, Math.round(cr * fw)), iy1 = Math.min(y1, Math.round(cb * fh));
        int w = x1 - x0, h = y1 - y0;
        if (w < 4 || h < 4 || ix1 <= ix0 || iy1 <= iy0) return null;
        int[] px = new int[w * h];
        frame.getPixels(px, 0, w, x0, y0, w, h);
        int ml = ix0 - x0, mt = iy0 - y0, mr = x1 - ix1, mb = y1 - iy1;
        int[] in = LogoPatch.fill(px, w, h, ml, mt, mr, mb);
        int iw = w - ml - mr, ih = h - mt - mb;
        return in == null || iw <= 0 || ih <= 0 ? null : Bitmap.createBitmap(in, iw, ih, Bitmap.Config.ARGB_8888);
    }

    /** Back over the player: the line-ups beside the board closed if they are out. */
    boolean closeLineups() {
        if (intro.back()) return true;
        return started && board.closeLineups();
    }

    @Override
    public void shown(Game g, long screenNow) {
        if (started) intro.tick(g, screenNow);
    }

    @Override
    public void goalShown(Game g, int side) {
        if (started) intro.goal(g, side);
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
        adjust();
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
        safely(this::placeNow, null);
    }

    private void placeNow() {
        float d = a.getResources().getDisplayMetrics().density;
        View frame = video.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) board.view.getLayoutParams();
        android.view.ViewGroup.LayoutParams bp = board.bug.getLayoutParams();
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
        if (fw > 0 && fh > 0) {
            pfx = fx;
            pfy = fy;
            pfw = fw;
            pfh = fh;
            intro.place(fx, fy, fw, fh);
        }
        BoardPlace own = mine();
        boolean placed = own.board() && !inPip && fw > 0 && fh > 0;
        boolean over = !placed && box != null && ScoreBoard.cover(a) && board.channelGame() && !inPip;
        int left, top;
        if (placed) {
            // Where the family put it, the height they chose, its own width (or the width kept when its height was set
            // apart from it: it then draws to fill that box); kept on the screen.
            bp.width = own.bw > 0 ? Math.max(Math.round(20 * d), Math.round(own.bw * fw)) : LinearLayout.LayoutParams.WRAP_CONTENT;
            bp.height = Math.max(Math.round(12 * d), Math.round(own.bh * fh));
            int w = bp.width > 0 ? bp.width + board.bug.tabFor(bp.width, bp.height)
                    : board.bug.getWidth() > 0 ? board.bug.getWidth() : Math.round(bp.height * 5f);
            left = Math.max(0, Math.min(Math.round(fx + own.bx * fw), root.getWidth() - Math.min(w, root.getWidth())));
            top = Math.max(0, Math.min(Math.round(fy + own.by * fh), root.getHeight() - Math.min(bp.height, root.getHeight())));
        } else if (over) {
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
        if (inPip) {
            // The small window: a sixth of its height, in its top corner.
            bp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            bp.height = Math.max(Math.round(11 * d), Math.round(root.getHeight() * 0.16f));
            left = Math.round(4 * d);
            top = Math.round(4 * d);
        } else if (!over && !placed) {
            boolean upright = root.getHeight() > root.getWidth();
            bp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            // Its own corner, at the size chosen (Medium as it was); Stacked taller, its clubs one above the other.
            bp.height = Math.round((upright ? 24 : 30) * d * ScoreBoard.size(a) * board.bug.style().tall);
            left = Math.round(Math.max(fx, 0) + 14 * d) + (fx <= insetLeft ? insetLeft : 0);
            top = Math.round(Math.max(fy, 0) + 12 * d) + (fy <= insetTop ? insetTop : 0);
            // The channel's own scoreboard found and not covered: just under it.
            if (box != null && board.channelGame() && box.left < 0.5f) top = Math.round(fy + box.bottom * fh + 6 * d);
        }
        // The added time in a tab of its own beside it: a board of a set width grown by the tab, its own part as set
        // (too narrow for the tab: the minutes inside, beside the clock).
        bp.width = board.bug.withTab(bp.width, bp.height);
        board.bug.setLayoutParams(bp);
        int boardLeft = left, boardTop = top;
        // Homefield's mark over the channel's logo: where the family put it, else over the logo found, its size and
        // a little over.
        LogoCover.Look look = ScoreBoard.logoCover(a);
        boolean base = look != LogoCover.Look.OFF && started && !inPip && ScoreBoard.on(a);
        boolean show = base && (own.logo() || logo != null);
        if (show) {
            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cover.getLayoutParams();
            if (own.logo()) {
                cp.width = Math.max(Math.round(12 * d), Math.round(own.lw * fw));
                cp.height = Math.max(Math.round(8 * d), Math.round(own.lh * fh));
                cp.leftMargin = Math.max(0, Math.min(Math.round(fx + own.lx * fw), root.getWidth() - cp.width));
                cp.topMargin = Math.max(0, Math.min(Math.round(fy + own.ly * fh), root.getHeight() - cp.height));
            } else {
                float pad = Math.max(2 * d, logo.height() * fh * 0.12f);
                cp.leftMargin = Math.round(fx + logo.left * fw - pad);
                cp.topMargin = Math.round(fy + logo.top * fh - pad);
                cp.width = Math.round(logo.width() * fw + 2 * pad);
                cp.height = Math.round(logo.height() * fh + 2 * pad);
                show = cp.leftMargin >= 0 && cp.topMargin >= 0 && cp.leftMargin + cp.width <= root.getWidth() && cp.topMargin + cp.height <= root.getHeight();
            }
            if (show) {
                cover.look(look);
                cover.setLayoutParams(cp);
                // Broadcast: the channel's picture tag, LIVE while the game is, the scoreboard's colours.
                Channel ch = host.current();
                cover.tag(ScoreBoard.logoTag(a).on(ch == null ? null : ch.name));
                cover.channelName(ch == null ? null : ch.name);
                cover.sport(board.f1() ? "f1" : "");
                cover.live(board.live());
                cover.boardLook(board.bug.stripLook());
                cover.competition(board.compPic(), board.compName());
                cover.compOn(ScoreBoard.logoComp(a));
                cover.liveBar(ScoreBoard.logoLive(a));
                cover.what(ScoreBoard.whatFor(ScoreBoard.logoWhat(a), board.compName(), board.f1()));
                cover.nameOnly(ScoreBoard.logoNameOnly(a));
                cover.seeThrough(ScoreBoard.logoSeeThrough(a));
                cover.style(ScoreBoard.logoStyle(a));
            }
        }
        cover.setVisibility(show ? View.VISIBLE : View.GONE);
        // See-through: the picture round the channel's logo, a couple of times a second.
        if (show && cover.wantsPatch() && !patchLoop) {
            patchLoop = true;
            h.post(patchRun);
        }
        // The room round the board for what slides out of it: to the screen's edges, short of the logo's mark where
        // it is level with the board; the board's place kept as what is beside it moves the view.
        int bh = bp.height > 0 ? bp.height : Math.round(30 * d);
        int bw = bp.width > 0 ? bp.width : board.bug.getWidth() > 0 ? board.bug.getWidth() : board.bug.natural(bh);
        int margin = Math.round(4 * d);
        int roomL = boardLeft - margin, roomR = root.getWidth() - margin - (boardLeft + bw), roomDown = root.getHeight() - (boardTop + bh);
        if (show) {
            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cover.getLayoutParams();
            boolean level = cp.topMargin < boardTop + bh && cp.topMargin + cp.height > boardTop;
            if (level && cp.leftMargin >= boardLeft + bw) roomR = Math.min(roomR, cp.leftMargin - margin - (boardLeft + bw));
            if (level && cp.leftMargin + cp.width <= boardLeft) roomL = Math.min(roomL, boardLeft - (cp.leftMargin + cp.width) - margin);
        }
        board.room(inPip ? 0 : roomL, inPip ? 0 : roomR, inPip ? 0 : boardTop, inPip ? 0 : roomDown);
        int vl = boardLeft - board.boardDx(), vt = boardTop - board.boardDy();
        if (lp.leftMargin != vl || lp.topMargin != vt) {
            lp.leftMargin = vl;
            lp.topMargin = vt;
            board.view.setLayoutParams(lp);
        }
    }

    /** This channel's own places (loaded as the channel changes; held as they are while being set). */
    private BoardPlace mine() {
        Channel ch = host.current();
        String key = ch == null ? null : ch.key;
        if (!editing && !carrying && (key == null ? mineKey != null : !key.equals(mineKey))) {
            mineKey = key;
            mine = BoardPlace.get(a, key);
        }
        return mine;
    }

    // ---- moving and sizing by hand ----------------------------------------------------------------------------------

    boolean editing() {
        return editing;
    }

    /**
     * Move and resize: the scoreboard and the mark over the logo picked up where they are (a made-up game shows when
     * none is on), outlined, to drag; the panel along the foot for their sizes and looks. The picture under them
     * takes no swipes meanwhile.
     */
    void adjust() {
        if (editing || !started || inPip || !ScoreBoard.on(a)) return;
        Channel ch = host.current();
        if (ch == null) return;
        host.hideControls();
        mine();
        editing = true;
        board.preview(true);
        float d = a.getResources().getDisplayMetrics().density;
        // Started from where they are now.
        boardFromHere();
        if (!mine.logo() && ScoreBoard.logoCover(a) != LogoCover.Look.OFF) logoFromHere();
        shield = new View(a);
        shield.setBackgroundColor(0x40000000);
        shield.setClickable(true);
        root.addView(shield, Math.max(0, root.indexOfChild(cover)), new FrameLayout.LayoutParams(-1, -1));
        boolean wide = root.getWidth() > root.getHeight();
        panel = AdjustPanel.build(a, new AdjustPanel.Actions() {
            @Override public void boardShown(boolean on) {
                ScoreBoard.setBoardShown(a, on);
                board.hidden(!on);
                place();
            }

            @Override public void boardSize(float k) {
                mine.sizeBoard(k);
                board.bug.post(ScoreOverlay.this::place);
                place();
            }

            @Override public void boardHeight(float k) {
                mine.heightBoard(k, pfw > 0 && board.bug.getWidth() > 0 ? board.bug.ownWidth() / pfw : 0.3f);
                board.bug.post(ScoreOverlay.this::place);
                place();
            }

            @Override public void boardWidth(float k) {
                mine.widthBoard(k, pfw > 0 && board.bug.getWidth() > 0 ? board.bug.ownWidth() / pfw : 0.3f);
                board.bug.post(ScoreOverlay.this::place);
                place();
            }

            @Override public void logoShown(boolean on) {
                ScoreBoard.setLogoCover(a, on ? ScoreBoard.lastLogo(a) : LogoCover.Look.OFF);
                if (on && !mine.logo()) {
                    mine.lw = 0.13f;
                    mine.lh = 0.075f;
                    mine.lx = 0;
                    mine.moveLogo(1 - 0.035f - mine.lw, 0.05f);
                }
                restyle();
                outline(true);
            }

            @Override public void logoHeight(float k) {
                if (mine.logo()) mine.heightLogo(k);
                place();
            }

            @Override public void logoWidth(float k) {
                if (mine.logo()) mine.widthLogo(k);
                place();
            }

            @Override public String later(int seconds) {
                Channel ch = host.current();
                if (ch != null) {
                    LiveAlerts.setDelay(a, ch.key, LiveAlerts.delay(a, ch.key) + seconds);
                    board.behind();
                }
                return timingWords();
            }

            @Override public void boardStyle(OverlayBug.Style st) {
                ScoreBoard.setStyle(a, st);
                restyle();
            }

            @Override public void logoSize(float k) {
                if (mine.logo()) mine.sizeLogo(k);
                place();
            }

            @Override public void logoLook(LogoCover.Look l) {
                ScoreBoard.setLogoCover(a, l);
                if (l != LogoCover.Look.OFF && !mine.logo()) {
                    mine.lw = 0.13f;
                    mine.lh = 0.075f;
                    mine.lx = 0;
                    mine.moveLogo(1 - 0.035f - mine.lw, 0.05f);
                }
                restyle();
                outline(true);
            }

            @Override public void still(boolean on) {
                ScoreBoard.setStill(a, on);
            }

            @Override public void reset() {
                mine = new BoardPlace();
                BoardPlace.put(a, mineKey, mine);
                endAdjust(false);
                Toast.makeText(a, "Back to where Homefield puts them", Toast.LENGTH_SHORT).show();
            }

            @Override public void done() {
                endAdjust(true);
            }
        }, ScoreBoard.style(a), ScoreBoard.logoCover(a), ScoreBoard.still(a), ScoreBoard.boardShown(a), timingWords());
        int side = Math.round(12 * d);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(wide ? Math.min(Math.round(600 * d), Math.round(root.getWidth() * 0.72f)) : -1, -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        pp.setMargins(side, 0, side, side + insetTop / 4);
        root.addView(panel, pp);
        dragAll();
        outline(true);
        guard.poke();
        place();
    }

    /**
     * Move and resize's timing line: read off the channel's own clock (the board then has its clock and score to the
     * second, whatever is set by hand), or seen turning a minute, else how long it is held back behind the scores.
     */
    private String timingWords() {
        Channel ch = host.current();
        int held = ch == null ? 0 : LiveAlerts.delay(a, ch.key);
        if (reading()) return "Matched to the channel's clock";
        if (board.screen().matched()) return "Matched to the channel's minute · held " + held + " s";
        return "Held back " + held + " s behind the live scores";
    }

    /** Move and resize closed: kept for this channel (save), or as they were. */
    void endAdjust(boolean save) {
        if (!editing) return;
        editing = false;
        if (save) BoardPlace.put(a, mineKey, mine);
        if (shield != null) root.removeView(shield);
        if (panel != null) root.removeView(panel);
        shield = null;
        panel = null;
        handles(board.bug, true);
        handles(cover, false);
        outline(false);
        board.preview(false);
        place();
    }

    /** The board's own place (none yet: where it is now, at its size there, the width it covers kept). */
    private void boardFromHere() {
        if (mine.board()) return;
        float d = a.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) board.view.getLayoutParams();
        android.view.ViewGroup.LayoutParams bp = board.bug.getLayoutParams();
        int h = board.bug.getHeight() > 0 ? board.bug.getHeight() : Math.round(30 * d);
        mine.bh = BoardPlace.clamp(h / pfh, BoardPlace.MIN_H, BoardPlace.MAX_H);
        // Over the channel's own, at that box's width: kept, so it doesn't change size as it is picked up.
        if (bp.width > 0 && board.bug.getWidth() > 0) mine.bw = BoardPlace.clamp(board.bug.ownWidth() / pfw, BoardPlace.MIN_BW, BoardPlace.MAX_BW);
        mine.bx = 0;
        mine.moveBoard((lp.leftMargin + board.boardDx() - pfx) / pfw, (lp.topMargin + board.boardDy() - pfy) / pfh, board.bug.getWidth() / pfw);
    }

    /** The mark's own place (none yet: over the logo where it is, else the top right, where most channels have theirs). */
    private void logoFromHere() {
        if (mine.logo()) return;
        if (cover.getVisibility() == View.VISIBLE && cover.getWidth() > 0) {
            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cover.getLayoutParams();
            mine.lw = cover.getWidth() / pfw;
            mine.lh = cover.getHeight() / pfh;
            mine.lx = 0;
            mine.moveLogo((cp.leftMargin - pfx) / pfw, (cp.topMargin - pfy) / pfh);
        } else {
            mine.lw = 0.13f;
            mine.lh = 0.075f;
            mine.lx = 0;
            mine.moveLogo(1 - 0.035f - mine.lw, 0.05f);
        }
    }

    /**
     * Outside Move and resize, the board's (and the mark's) touches: held, it is picked up and follows the finger
     * until let go, then kept there for this channel; tapped twice, Move and resize opens for its size, height, look
     * and timing; tapped once, the board's tap (its game's match centre).
     */
    @SuppressLint("ClickableViewAccessibility")
    private void handles(View v, boolean isBoard) {
        float[] start = new float[4];
        boolean[] held = {false};
        GestureDetector g = new GestureDetector(a, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isBoard) v.performClick();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                safely(ScoreOverlay.this::adjust, null);
                return true;
            }

            @Override public void onLongPress(MotionEvent e) {
                if (editing || inPip || !started || host.current() == null) return;
                mine();
                carrying = true;
                if (isBoard) boardFromHere();
                else logoFromHere();
                start[0] = e.getRawX();
                start[1] = e.getRawY();
                start[2] = isBoard ? mine.bx : mine.lx;
                start[3] = isBoard ? mine.by : mine.ly;
                held[0] = true;
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                outlineOne(v, true);
                guard.poke();
                place();
            }
        });
        v.setOnTouchListener((view, e) -> {
            g.onTouchEvent(e);
            if (!held[0]) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    follow(isBoard, start, e);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    held[0] = false;
                    carrying = false;
                    if (mineKey != null) BoardPlace.put(a, mineKey, mine);
                    outlineOne(v, false);
                    place();
                    return true;
                default:
                    return true;
            }
        });
    }

    /** The one dragged where the finger has gone from where it was (start: the finger's and the place's), kept in sight. */
    private void follow(boolean isBoard, float[] start, MotionEvent e) {
        float x = start[2] + (e.getRawX() - start[0]) / pfw, y = start[3] + (e.getRawY() - start[1]) / pfh;
        // Kept where it can be seen: on the picture, and on the screen when the picture is zoomed past it.
        float minX = Math.max(0, -pfx / pfw), minY = Math.max(0, -pfy / pfh);
        float maxX = Math.min(1, (root.getWidth() - pfx) / pfw), maxY = Math.min(1, (root.getHeight() - pfy) / pfh);
        if (isBoard) {
            float w = board.bug.getWidth() / pfw;
            mine.moveBoard(BoardPlace.clamp(x, minX, Math.max(minX, maxX - w)), BoardPlace.clamp(y, minY, Math.max(minY, maxY - mine.bh)), w);
        } else {
            mine.moveLogo(BoardPlace.clamp(x, minX, Math.max(minX, maxX - mine.lw)), BoardPlace.clamp(y, minY, Math.max(minY, maxY - mine.lh)));
        }
        place();
    }

    /** A dashed white edge round each one while it can be moved. */
    private void outline(boolean on) {
        outlineOne(board.bug, on);
        outlineOne(cover, on);
    }

    private void outlineOne(View v, boolean on) {
        if (!on) {
            v.setForeground(null);
            return;
        }
        float d = a.getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(0);
        g.setCornerRadius(4 * d);
        g.setStroke(Math.max(1, Math.round(2 * d)), 0xFFFFFFFF, 6 * d, 4 * d);
        v.setForeground(g);
    }

    /** Both made draggable (a drag isn't a click: the screen reader moves them with the panel's buttons). */
    @SuppressLint("ClickableViewAccessibility")
    private void dragAll() {
        drag(board.bug, true);
        drag(cover, false);
    }

    /** Dragged by a finger while Move and resize is open: its place follows, kept on the picture and the screen. */
    @SuppressLint("ClickableViewAccessibility")
    private void drag(View v, boolean isBoard) {
        float[] start = new float[4];
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawX();
                    start[1] = e.getRawY();
                    start[2] = isBoard ? mine.bx : mine.lx;
                    start[3] = isBoard ? mine.by : mine.ly;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    follow(isBoard, start, e);
                    return true;
                default:
                    return true;
            }
        });
    }

    // ---- finding the channel's scoreboard --------------------------------------------------------------------------

    /** A few seconds of small frames for BugFinder, then the scoreboard placed; looked at again now and then. */
    private void look() {
        h.removeCallbacks(look);
        boolean scoreboard = board.football(),
                logos = ScoreBoard.logoCover(a) != LogoCover.Look.OFF || (ScoreBoard.broadcast(a) && board.football());
        if (!started || inPip || sampler == null) return;
        // F1's tower read for its lap, on an F1 channel.
        // The channel's scoreboard (or F1's tower) read: the reads keep themselves going once started.
        if (ScoreBoard.reads(a) && !screenLoop()) readSoon(1000);
        if (!scoreboard && !logos) {
            h.postDelayed(look, RETRY_MS);
            return;
        }
        if (finder == null) {
            finder = new BugFinder(FIND_W, FIND_H);
            if (small == null) small = Bitmap.createBitmap(FIND_W, FIND_H, Bitmap.Config.ARGB_8888);
        }
        // The finder this look adds to, held here: the game changing meanwhile (or the small window) lets it go, and
        // this frame then counts for nothing instead of being added to no finder at all.
        BugFinder f = finder;
        boolean asked = sampler.copy(video.surface(), small, (b, at) -> {
            if (b != null) f.add(FrameSampler.grey(b, 0, 0, FIND_W, FIND_H));
            boolean done = f.frames() >= BugFinder.ENOUGH;
            BugFinder.Box found = done && scoreboard ? f.find() : null;
            BugFinder.Box mark = done && (logos || ScoreBoard.broadcast(a)) ? f.logo(found) : null;
            boolean[] outline = mark != null && ScoreBoard.broadcast(a) ? f.logoMask(mark) : null;
            h.post(() -> safely(() -> {
                if (!started || f != finder) return;
                if (!done) {
                    h.postDelayed(look, SAMPLE_MS);
                    return;
                }
                finder = null;
                // Kept still: the mark stays over the logo where it was first found.
                if ((mark != null || logo != null) && !(ScoreBoard.still(a) && logo != null)) {
                    logo = mark;
                    place();
                }
                // Broadcast mode: the logo's outline learnt, then watched for the adverts.
                if (mark != null && outline != null) {
                    BreakWatch w = new BreakWatch(outline, FIND_W, FIND_H);
                    if (w.usable() && (breaks == null || !breaks.onBreak())) {
                        breaks = w;
                        h.removeCallbacks(checkBreak);
                        h.postDelayed(checkBreak, 2000);
                    }
                }
                if (found != null) {
                    boolean moved = box == null || Math.abs(found.left - box.left) > 0.02f || Math.abs(found.top - box.top) > 0.02f
                            || Math.abs(found.width() - box.width()) > 0.03f;
                    // Kept still: once over the channel's own, it stays where it was put.
                    if (moved && !(ScoreBoard.still(a) && box != null)) {
                        box = found;
                        reader = null;
                        place();
                    }
                    // Read there first (if nothing is read there, the reader looks along the top itself).
                    if (readBox == null) readBox = found;
                    // Its clock read: its digits read off it, else seen turning a minute.
                    if (reader == null) startReading();
                    if (ScoreBoard.reads(a) && !screenLoop()) readSoon(500);
                }
                h.postDelayed(look, found != null || box != null ? LOOK_AGAIN_MS : RETRY_MS);
            }, () -> h.postDelayed(look, RETRY_MS)));
        });
        // No picture to copy yet: a moment later.
        if (!asked) h.postDelayed(look, 1000);
    }

    /**
     * What came back from a frame, dealt with on the main thread; anything that goes wrong in it is left there (then
     * again: the next try), so the scoreboard's reading never stops the stream.
     */
    private void safely(Runnable r, Runnable then) {
        try {
            r.run();
        } catch (RuntimeException e) {
            android.util.Log.w("LiveTv", "board: " + e.getClass().getSimpleName());
            if (then != null && started) then.run();
        }
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
            h.post(() -> safely(() -> {
                if (!started || r != reader) return;
                if (turned && board.screen().minuteTurned(at)) {
                    // Matched to the second: looked at again in a few minutes, to stay so.
                    h.postDelayed(this::startReading, READ_AGAIN_MS);
                    return;
                }
                h.postDelayed(readClock, SAMPLE_MS);
            }, () -> h.postDelayed(this::startReading, READ_AGAIN_MS)));
        });
        if (!asked) h.postDelayed(readClock, 1000);
    }

    // ---- reading the channel's scoreboard ----------------------------------------------------------------------------

    /** Where on the picture the channel's scoreboard is read (fractions of it); null while it is looked for. */
    private BugFinder.Box readBox;
    /** What was last read off it, and when: Options shows it. */
    private String lastRead = "";
    private long lastReadAt;

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

    /**
     * Every two seconds while a game is on the board: the channel's scoreboard read off the picture (ChannelReader).
     * While it isn't found yet, the top third of the picture is read for a line with a game's clock in it: that is the
     * scoreboard, and what is round it is read with it. Found, that part alone is read, and what it says is what the
     * board shows (OnScreen): the clock as the channel has it, the score it shows, HT and FT. Six reads running with
     * neither a clock nor HT or FT, and it is looked for again. On an F1 channel, the tower's corner for its lap.
     */
    private void readScreen() {
        h.removeCallbacks(readScreen);
        screenAt = System.currentTimeMillis();
        if (!started || inPip || sampler == null || !ScoreBoard.reads(a)) return;
        boolean f1 = host.game() == null && ScoreBoard.f1Channel(host.current());
        if (!f1 && !board.football()) {
            readSoon(5000);
            return;
        }
        BugFinder.Box rb = readBox;
        if (ocrFrame == null) ocrFrame = Bitmap.createBitmap(OCR_W, OCR_H, Bitmap.Config.ARGB_8888);
        boolean asked = sampler.copy(video.surface(), ocrFrame, (frame, at) -> {
            Cut cut = frame == null ? null : f1 ? Cut.of(frame, 0f, 0f, 0.34f, 0.24f)
                    : rb != null ? Cut.of(frame, rb.left, rb.top, rb.right, rb.bottom) : Cut.of(frame, 0f, 0f, 1f, 0.34f);
            h.post(() -> safely(() -> {
                if (!started || cut == null) {
                    readSoon(OCR_MS);
                    return;
                }
                boolean reading = ocr.read(cut.b, (lines, boxes) -> safely(() -> {
                    if (!started) return;
                    if (f1) {
                        ScreenText.Reading r = ScreenText.f1(lines);
                        if (r.any()) board.f1Screen(r.lap, r.laps, r.left, at);
                        readSoon(F1_OCR_MS);
                        return;
                    }
                    if (rb == null) found(lines, boxes, cut, at);
                    else football(lines, at);
                    readSoon(OCR_MS);
                }, () -> readSoon(OCR_MS)));
                if (!reading) readSoon(OCR_MS);
            }, () -> readSoon(OCR_MS)));
        });
        if (!asked) readSoon(1000);
    }

    /**
     * The top of the picture read: the line with a game's clock in it is the scoreboard's. The part round it (names and
     * score sit beside the clock, or just over or under it) is read from now on, and what it says now is taken.
     */
    private void found(java.util.List<String> lines, java.util.List<android.graphics.Rect> boxes, Cut cut, long at) {
        lastRead = lines.isEmpty() ? "" : "looking: " + android.text.TextUtils.join(" · ", lines);
        lastReadAt = System.currentTimeMillis();
        for (int i = 0; i < Math.min(lines.size(), boxes.size()); i++) {
            // A game's clock, and this game's (not a time of day or a countdown elsewhere on the picture).
            int clock = ScreenText.football(java.util.Collections.singletonList(lines.get(i))).clock;
            if (clock < 0 || !board.screen().plausible(clock, at)) continue;
            android.graphics.Rect rr = boxes.get(i);
            if (rr.isEmpty()) continue;
            float l = cut.fx(rr.left), t = cut.fy(rr.top), r = cut.fx(rr.right), b = cut.fy(rr.bottom);
            float lh = b - t, across = lh * OCR_H / OCR_W;
            BugFinder.Box area = new BugFinder.Box(Math.max(0, l - across * 9), Math.max(0, t - lh * 1.8f),
                    Math.min(1, r + across * 9), Math.min(0.5f, b + lh * 1.8f), true);
            // What is in that part now, and the box the lines in it make (to cover, when nothing better is known).
            java.util.List<String> near = new java.util.ArrayList<>();
            float ul = l, ut = t, ur = r, ub = b;
            for (int k = 0; k < Math.min(lines.size(), boxes.size()); k++) {
                android.graphics.Rect q = boxes.get(k);
                float ql = cut.fx(q.left), qt = cut.fy(q.top), qr = cut.fx(q.right), qb = cut.fy(q.bottom);
                if (qr < area.left || ql > area.right || qb < area.top || qt > area.bottom) continue;
                near.add(lines.get(k));
                ul = Math.min(ul, ql);
                ut = Math.min(ut, qt);
                ur = Math.max(ur, qr);
                ub = Math.max(ub, qb);
            }
            readBox = area;
            if (box == null) {
                float pad = lh * 0.45f;
                box = new BugFinder.Box(Math.max(0, ul - pad * OCR_H / OCR_W), Math.max(0, ut - pad),
                        Math.min(1, ur + pad * OCR_H / OCR_W), Math.min(0.5f, ub + pad), true);
            }
            football(near, at);
            place();
            return;
        }
    }

    /** What the channel's scoreboard said at the moment at: what the board shows from now. */
    private void football(java.util.List<String> lines, long at) {
        ScreenText.Reading r = ScreenText.football(lines);
        int[] score = ScreenText.score(lines);
        String state = ScreenText.state(lines);
        long now = System.currentTimeMillis();
        if (!lines.isEmpty()) {
            lastRead = android.text.TextUtils.join(" · ", lines);
            lastReadAt = now;
        }
        if (r.clock < 0 && state == null) {
            // Nothing there (an advert, a replay, the wrong part): six times running, it is looked for again.
            if (++ocrMisses >= 6) {
                ocrMisses = 0;
                readBox = null;
            }
            return;
        }
        ocrMisses = 0;
        com.gathin.tvhomephone.sports.OnScreen s = board.screen();
        // A followed club's game on the board (the channel's own game not known): taken as the channel's when what the
        // channel shows fits it (its score, or its clock within a few minutes).
        if (!board.channelGame()) {
            if (!s.fits(score, r.clock, at)) return;
            board.confirm();
        }
        if (r.clock >= 0) s.screenClock(r.clock, r.added, at);
        if (score != null) s.screenScore(score[0], score[1], at);
        if (state != null) s.screenState(state, at);
        ocrOkAt = now;
        long behind = s.measuredBehind();
        Channel ch = host.current();
        if (behind >= 0 && ch != null) {
            int secs = Math.round(behind / 1000f);
            // The channel's delay from its own clock: its alerts (and the board, between reads) wait just that long.
            if (Math.abs(secs - LiveAlerts.delay(a, ch.key)) >= 3) {
                LiveAlerts.setDelay(a, ch.key, secs);
                board.behind();
            }
        }
    }

    /** Part of a frame (fractions of it), made big enough to read (its lines about 40 px high), and the way back. */
    private static final class Cut {
        final Bitmap b;
        final float x0, y0, sx, sy;

        private Cut(Bitmap b, float x0, float y0, float sx, float sy) {
            this.b = b;
            this.x0 = x0;
            this.y0 = y0;
            this.sx = sx;
            this.sy = sy;
        }

        /** A pixel x (y) in the cut as a fraction of the frame. */
        float fx(int x) { return x0 + x * sx; }

        float fy(int y) { return y0 + y * sy; }

        static Cut of(Bitmap frame, float l, float t, float r, float bt) {
            int fw = frame.getWidth(), fh = frame.getHeight();
            int x0 = Math.max(0, Math.round(l * fw)), y0 = Math.max(0, Math.round(t * fh));
            int x1 = Math.min(fw, Math.round(r * fw)), y1 = Math.min(fh, Math.round(bt * fh));
            if (x1 - x0 < 8 || y1 - y0 < 6) return null;
            try {
                Bitmap c = Bitmap.createBitmap(frame, x0, y0, x1 - x0, y1 - y0);
                // A thin strip is made bigger (the reader wants its words tall); a wide band only a little.
                float k = Math.max(1f, Math.min(4f, (y1 - y0) < 140 ? 120f / (y1 - y0) : 1.5f));
                Bitmap big = k > 1.05f ? Bitmap.createScaledBitmap(c, Math.round((x1 - x0) * k), Math.round((y1 - y0) * k), true)
                        : c.copy(Bitmap.Config.ARGB_8888, false);
                return new Cut(big, x0 / (float) fw, y0 / (float) fh, 1f / (k * fw), 1f / (k * fh));
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    // ---- broadcast mode ---------------------------------------------------------------------------------------------

    /**
     * Every two seconds while the channel's logo is known: is it still on the picture? Gone for about eight seconds
     * before kick-off, at half time or after the game (never in play: football has no adverts then), the channel has
     * gone to its adverts: Homefield's break instead. Back for a few seconds: the channel again.
     */
    private void checkBreak() {
        h.removeCallbacks(checkBreak);
        BreakWatch w = breaks;
        if (!started || w == null || sampler == null || !ScoreBoard.broadcast(a)) return;
        if (inPip) {
            h.postDelayed(checkBreak, 2000);
            return;
        }
        if (small == null) small = Bitmap.createBitmap(FIND_W, FIND_H, Bitmap.Config.ARGB_8888);
        boolean asked = sampler.copy(video.surface(), small, (b, at) -> {
            float p = b == null ? -1 : w.present(FrameSampler.grey(b, 0, 0, FIND_W, FIND_H));
            h.post(() -> safely(() -> {
                if (!started || w != breaks) return;
                if (p >= 0) {
                    boolean on = w.add(p);
                    Game g = ScreenTime.game(host.game(), System.currentTimeMillis());
                    if (on && breakScreen == null && BreakWatch.allowed(g)) startBreak(false);
                    else if (!on && breakScreen != null && !manualBreak) endBreak(false);
                }
                h.postDelayed(checkBreak, 2000);
            }, () -> h.postDelayed(checkBreak, 2000)));
        });
        if (!asked) h.postDelayed(checkBreak, 1000);
    }

    /** Homefield's break up (manual: from Options, to see it; it ends with Back to the channel or after a minute). */
    void startBreak(boolean manual) {
        if (breakScreen != null || !started) return;
        manualBreak = manual;
        Context app = a.getApplicationContext();
        Game watching = host.game();
        BreakScreen screen = new BreakScreen(a, video, new BreakScreen.Host() {
            @Override public void mute(boolean on) {
                host.mute(on);
            }

            @Override public void back() {
                endBreak(true);
            }
        });
        breakScreen = screen;
        root.addView(screen, Math.min(4, root.getChildCount()), new FrameLayout.LayoutParams(-1, -1));
        App.io.execute(() -> {
            BreakScreen.Content k = BreakScreen.load(app, watching);
            App.main.post(() -> {
                if (breakScreen != screen) return;
                screen.post(() -> screen.show(k));
            });
        });
        if (manual) h.postDelayed(() -> {
            if (breakScreen == screen) endBreak(true);
        }, 60_000L);
    }

    /** The break over: the channel back (now: at once, without waiting for its logo). */
    void endBreak(boolean now) {
        BreakScreen s = breakScreen;
        if (s == null) return;
        breakScreen = null;
        manualBreak = false;
        s.end(() -> root.removeView(s));
        // The logo was gone a while: it is learnt again on the next look.
        if (now && breaks != null && breaks.onBreak()) breaks = null;
    }

    boolean onBreak() {
        return breakScreen != null;
    }

    // ---- test notifications (the player's options, PlayerTests) ------------------------------------------------------

    /** Why a test over the picture can't show now (a string), or 0 when it can; board: one on the scoreboard. */
    private int testBlocked(boolean onBoard) {
        if (inPip || editing || intro.showing() || breakScreen != null) return R.string.live7_wait;
        if (onBoard && !started) return R.string.live7_board_off;
        if (onBoard && !ScoreBoard.boardShown(a)) return R.string.live7_board_hidden;
        return 0;
    }

    /** Tests: the kick-off pop-up in a style at a moment, now (PlayerIntro.testPopup). */
    void testPopup(String style, String kind, IntConsumer note) {
        int no = testBlocked(false);
        if (no != 0) note.accept(no);
        else intro.testPopup(style, kind, host.game(), note);
    }

    /** Tests: the line-ups in a style, now (PlayerIntro.testLineups). */
    void testLineups(String style, IntConsumer note) {
        int no = testBlocked(false);
        if (no != 0) note.accept(no);
        else intro.testLineups(style, host.game(), note);
    }

    /** Tests: the matchday intro, now (PlayerIntro.testIntro). */
    void testIntro(IntConsumer note) {
        int no = testBlocked(false);
        if (no != 0) note.accept(no);
        else intro.testIntro(host.game(), note);
    }

    /** Tests: one of the scoreboard's moments (PlayerTests.BOARD_TESTS) now, on its game or a made-up one. */
    void testBoard(String what, IntConsumer note) {
        int no = testBlocked(true);
        if (no != 0) {
            note.accept(no);
            return;
        }
        boolean football = !board.f1();
        boolean ok;
        switch (what) {
            case "goal_home":
            case "goal_away":
                if (!football) {
                    note.accept(R.string.live7_f1);
                    return;
                }
                ok = board.testGoal("goal_home".equals(what) ? 1 : 2);
                break;
            case "scorer":
                ok = board.testScorer(1);
                break;
            case "stats":
                ok = board.testStats();
                break;
            case "card": {
                // a game with its line-ups out (the one watched, else a recent Premier League game): its card beside the board
                note.accept(R.string.live7_looking);
                Game watching = host.game();
                App.io.execute(() -> {
                    Events.Alert al = com.gathin.tvhomephone.sports.TestAlerts.lineupsAlert(watching);
                    App.main.post(() -> {
                        if (a.isFinishing()) return;
                        if (al == null) note.accept(R.string.live7_no_lineups);
                        else if (testBlocked(true) != 0 || !board.testCard(al)) note.accept(R.string.live7_wait);
                    });
                });
                return;
            }
            case "sub":
                ok = board.testSub();
                break;
            case "var":
            case "added":
                if (!football) {
                    note.accept(R.string.live7_f1);
                    return;
                }
                ok = "var".equals(what) ? board.testVar() : board.testAdded();
                break;
            default:
                return;
        }
        if (!ok) note.accept(R.string.live7_wait);
    }

    /** Tests: broadcast mode's break now, for up to a minute (Back to the channel ends it). */
    void testBreak(IntConsumer note) {
        int no = testBlocked(true);
        if (no == R.string.live7_board_hidden) no = 0;
        if (no != 0) note.accept(no);
        else startBreak(true);
    }

    // ---- the options -------------------------------------------------------------------------------------------------

    /** Its rows in the player's options: on, over the channel's own, the alerts, over other apps; how it keeps time. */
    void options(Context c, LinearLayout box, Runnable closeSheet) {
        String state = !ScoreBoard.on(c) ? c.getString(R.string.live4_board_state_off)
                : reading() ? c.getString(R.string.live4_board_state_reading)
                : matched() ? c.getString(R.string.live4_board_state_matched)
                : found() ? c.getString(R.string.live4_board_state_found)
                : c.getString(R.string.live4_board_state_delay, channel() == null ? 0 : LiveAlerts.delay(c, channel().key));
        long ago = (System.currentTimeMillis() - lastReadAt) / 1000;
        if (lastReadAt > 0 && !lastRead.isEmpty()) {
            state += "\n" + c.getString(R.string.live5_last_read, ago, lastRead.length() > 90 ? lastRead.substring(0, 90) + "…" : lastRead);
        } else if (ScoreBoard.reads(c) && board.football()) {
            state += "\n" + c.getString(R.string.live5_not_read);
        }
        TextView t = Ui.text(c, state, 12, Ui.DIM, false);
        t.setPadding(0, Ui.dp(c, 4), 0, 0);
        box.addView(t);
    }
}
