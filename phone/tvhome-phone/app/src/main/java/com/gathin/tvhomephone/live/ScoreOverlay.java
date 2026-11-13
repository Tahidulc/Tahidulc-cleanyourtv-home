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

        /** The channel's game when it kicks off within the hour (for the ad-break screen), else null; looked up as needed. */
        default Game soonGame() { return game(); }

        /** The ad-break screen's session on the channel playing (the listings', else a game found), null for none yet. */
        default AdBreak.Session adSession() { return null; }

        /** The programme on now in the channel's own guide, null without one. */
        default String programmeNow() { return null; }

        /** Whether the channel playing may be showing a match (a sports channel, a fixture in its guide). */
        default boolean sportsNow() { return false; }
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
    /** See-through found the logo itself (on this channel): the finder's box no longer moves the cover off it. */
    private boolean logoLearned;
    private final LogoCover cover;
    /** See-through's fill: the channel's logo filled in under the cover (LogoShape), where the logo is in the picture. */
    private final LogoFill fill;
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
    /** The ad-break screen before kick-off (AdBreakView); shown by hand from the options; hidden for this break. */
    private AdBreakView adBreak;
    private boolean manualAdBreak, adBreakHidden;
    private final Runnable checkBreak = this::checkBreak;
    /** Burn-in: the board and the mark over the logo moved now and then, dimmed when nothing happens. */
    private final BurnGuard guard;

    /** The classic kick-off pop-up and line-ups over the picture (PlayerIntro). */
    private final PlayerIntro intro;

    /** The channel's own scoreboard watched under the board (BugCover): away in replays, plates over what grows out of it. */
    private final BugCover bugCover;
    private final BugPlates plates;
    /** The board's place on the root as last laid out {x, y, w, h}; null before (or in the small window). */
    private int[] boardRoot;

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
        fill = new LogoFill(a);
        // A fresh fill several times a second here: each fades in over the one before quickly.
        fill.fade(70L);
        com.gathin.broadcast.Sfx.warm(a, com.gathin.broadcast.Sfx.SWISH, com.gathin.broadcast.Sfx.SCORE, "goal-" + PlayerIntro.goalSound(a),
                "goal-" + com.gathin.broadcast.Sfx.GOAL_DEFAULT);   // loaded before they are needed (the swoosh: a shootout's goals too)
        intro = new PlayerIntro(a, root, () -> started && !inPip && !editing, (on) -> {
            // the classic line-ups fill the picture: the scoreboard and the logo's cover out of their way meanwhile
            board.view.setAlpha(on ? 0f : 1f);
            cover.setAlpha(on || logoAwayNow ? 0f : 1f);
            fill.setAlpha(on || logoAwayNow ? 0f : 1f);
        });
        // the intros: the channel's own sound off while one plays
        intro.hush(host::mute);
        // a goal's restart pop-up waits out the channel's replays (the board away in them), and comes with live play
        // as the whole picture shows it (LivePlay), where the channel's own scoreboard stays up in its replays
        intro.inReplay(board::replayAway);
        intro.liveVerdict(() -> board.play.verdict(System.currentTimeMillis()));
        // what comes out of the scoreboard waits while a pop-up is up, or a goal's restart pop-up is still to come
        board.introBusy(() -> intro.showing() || intro.restartWaiting());
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
        // The fill just under the cover, over the whole picture (it draws only the logo's strokes, where they are).
        root.addView(fill, root.indexOfChild(cover), new FrameLayout.LayoutParams(-1, -1));
        root.addView(board.view, Math.min(3, root.getChildCount()), new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        // The plates over what grows out of the channel's own scoreboard: just under the board, so what slides out of
        // it is always over them.
        plates = new BugPlates(a);
        root.addView(plates, root.indexOfChild(board.view), new FrameLayout.LayoutParams(-1, -1));
        bugCover = new BugCover(a, new BugCover.Host() {
            @Override public boolean watching() {
                if (!started || inPip || editing || carrying || sampler == null || !board.channelGame()) return false;
                Game g = ScoreOverlay.this.host.game();
                if (g == null || g.state != Game.LIVE || g.halftime) return false;
                return board.replayAway() || board.bug.getVisibility() == View.VISIBLE;
            }

            @Override public int[] videoPixels() {
                return video.videoPixels();
            }

            @Override public int[] boardInStream() {
                int[] b = boardRoot, vp = video.videoPixels();
                if (b == null || vp[0] <= 0 || vp[1] <= 0 || pfw <= 1 || pfh <= 1) return null;
                float kx = vp[0] / pfw, ky = vp[1] / pfh;
                int x0 = Math.max(0, Math.round((b[0] - pfx) * kx)), y0 = Math.max(0, Math.round((b[1] - pfy) * ky));
                int x1 = Math.min(vp[0], Math.round((b[0] + b[2] - pfx) * kx)), y1 = Math.min(vp[1], Math.round((b[1] + b[3] - pfy) * ky));
                return x1 - x0 < 4 || y1 - y0 < 4 ? null : new int[]{x0, y0, x1 - x0, y1 - y0};
            }

            @Override public int[] bugInStream() {
                BugFinder.Box bx = box;
                int[] vp = video.videoPixels();
                if (bx == null || vp[0] <= 0 || vp[1] <= 0) return null;
                return new int[]{Math.round(bx.left * vp[0]), Math.round(bx.top * vp[1]), Math.round(bx.right * vp[0]), Math.round(bx.bottom * vp[1])};
            }

            @Override public String channelKey() {
                return chKey();
            }

            @Override public boolean copyPart(android.graphics.Rect src, Bitmap into, FrameSampler.Got got) {
                return sampler != null && sampler.copyPart(video.surface(), src, into, got);
            }

            @Override public void away(boolean on) {
                board.replay(on);
            }

            @Override public void plates(java.util.List<int[]> rects, java.util.List<String> words) {
                showPlates(rects, words);
            }
        });
        guard = new BurnGuard(a, 2f, new View[]{board.view, cover}, new BurnGuard.Dims[]{board.bug, cover});
        board.changed = guard::poke;
        handles(board.bug, true);
        handles(cover, false);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) h.post(this::place);
        });
        video.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> h.post(this::place));
    }

    // ---- up by hand, and each channel's own -------------------------------------------------------------------------

    /** The channel playing's key (null for none). */
    private String chKey() {
        Channel ch = host.current();
        return ch == null ? null : ch.key;
    }

    /** This channel is set to never have them. */
    private boolean neverHere() {
        return ChannelOverlays.NEVER.equals(ChannelOverlays.mode(a, chKey()));
    }

    /** Up by hand here: switched on by hand, or this channel set to always have them (never on one set Never). */
    private boolean byHand() {
        String m = ChannelOverlays.mode(a, chKey());
        return !ChannelOverlays.NEVER.equals(m) && (ScoreBoard.handOn(a) || ChannelOverlays.ALWAYS.equals(m));
    }

    /** Whether the scoreboard and the mark are wanted over this channel at all. */
    private boolean wanted() {
        return !neverHere() && (ScoreBoard.on(a) || byHand());
    }

    /** The board out of sight while its clock and score run: hidden by hand, a channel set Never, up by hand for the mark alone. */
    private boolean boardHidden() {
        return !ScoreBoard.boardShown(a) || neverHere()
                || ScoreBoard.handOn(a) && ScoreBoard.NO_GAME.equals(ScoreBoard.handGame(a));
    }

    /** The mark's look over this channel: its own, else the general one. */
    private LogoCover.Look lookHere() {
        return ChannelOverlays.lookOf(ChannelOverlays.look(a, chKey()), ScoreBoard.logoCover(a));
    }

    /** A look chosen while a channel plays (Move and resize, the options): kept for that channel, else for all. */
    private void setLookHere(LogoCover.Look l) {
        if (chKey() != null) ChannelOverlays.setLook(a, chKey(), l);
        else ScoreBoard.setLogoCover(a, l);
    }

    int styleHere() {
        int own = ChannelOverlays.style(a, chKey());
        return own >= 0 ? own : ScoreBoard.logoStyle(a);
    }

    // The rest of the logo's settings and the scoreboard's size on this channel: its own, else the general ones.

    private String ownOpt(String name) {
        return ChannelOverlays.opt(a, chKey(), name);
    }

    float sizeHere() {
        String o = ownOpt("size");
        try {
            return o != null ? Float.parseFloat(o) : ScoreBoard.size(a);
        } catch (NumberFormatException e) {
            return ScoreBoard.size(a);
        }
    }

    boolean seeThroughHere() {
        String o = ownOpt("see_through");
        return o != null ? "1".equals(o) : ScoreBoard.logoSeeThrough(a);
    }

    boolean nameOnlyHere() {
        String o = ownOpt("name_only");
        return o != null ? "1".equals(o) : ScoreBoard.logoNameOnly(a);
    }

    ChannelMark.Tag tagHere() {
        String o = ownOpt("tag");
        return o != null ? ChannelMark.Tag.of(o) : ScoreBoard.logoTag(a);
    }

    boolean liveHere() {
        String o = ownOpt("live");
        return o != null ? "1".equals(o) : ScoreBoard.logoLive(a);
    }

    boolean compHere() {
        String o = ownOpt("comp");
        return o != null ? "1".equals(o) : ScoreBoard.logoComp(a);
    }

    LogoCover.Look lookNow() {
        return lookHere();
    }

    /**
     * One of the settings changed in the player while a channel plays: kept for that channel (its own from then on),
     * else for every channel; the board and the logo redrawn.
     */
    void setHere(String name, String value) {
        String key = chKey();
        if (key != null) {
            ChannelOverlays.setOpt(a, key, name, value);
        } else {
            switch (name) {
                case "size": ScoreBoard.setSize(a, Float.parseFloat(value)); break;
                case "see_through": ScoreBoard.setLogoSeeThrough(a, "1".equals(value)); break;
                case "name_only": ScoreBoard.setLogoNameOnly(a, "1".equals(value)); break;
                case "tag": ScoreBoard.setLogoTag(a, ChannelMark.Tag.of(value)); break;
                case "live": ScoreBoard.setLogoLive(a, "1".equals(value)); break;
                case "comp": ScoreBoard.setLogoComp(a, "1".equals(value)); break;
                default: break;
            }
        }
        restyle();
    }

    /** The look and the Whitebrook Road style chosen in the player for this channel (else for all). */
    void setLookFromOptions(LogoCover.Look l) {
        if (l == null) ChannelOverlays.setLook(a, chKey(), null);
        else setLookHere(l);
        restyle();
    }

    void setStyleHere(int n) {
        if (chKey() != null) ChannelOverlays.setStyle(a, chKey(), n);
        else ScoreBoard.setLogoStyle(a, n);
        restyle();
    }

    /** Whether the channel playing is known (its settings can be its own). */
    boolean channelKnown() {
        return chKey() != null;
    }

    /** The game put up by hand, read (null: the channel's own, none, or still being read). */
    private Game handGame;
    private String handFor = "";

    /** Up by hand now: for g (null: the channel's own game), or the mark alone (logoOnly). */
    void hand(Game g, boolean logoOnly) {
        String id = logoOnly ? ScoreBoard.NO_GAME : g == null || g.id == null ? "" : g.id;
        String label = g == null || logoOnly ? "" : (g.home + " v " + g.away);
        ScoreBoard.setHand(a, true, id, label);
        handGame = logoOnly ? null : g;
        handFor = id;
        refresh();
    }

    /** Down again: as the general settings and the channel's own say. */
    void handOff() {
        ScoreBoard.setHand(a, false, "", "");
        handGame = null;
        handFor = "";
        refresh();
    }

    boolean handOn() {
        return ScoreBoard.handOn(a);
    }

    /** The channel's own setting changed (the options' "This channel"). */
    void channelChanged() {
        endAdBreak();
        adBreakHidden = false;
        refresh();
        restyle();
    }

    /** Up, hidden or away as by hand and the channel's own setting say now. */
    private void refresh() {
        if (!started && wanted()) start();
        if (!started) return;
        board.view.setVisibility(inPip || !wanted() ? View.GONE : View.VISIBLE);
        board.hidden(boardHidden());
        String id = ScoreBoard.handOn(a) ? ScoreBoard.handGame(a) : "";
        if (!id.equals(handFor) || (handGame == null && !id.isEmpty() && !ScoreBoard.NO_GAME.equals(id))) {
            handFor = id;
            handGame = null;
            readHandGame(id);
        }
        board.changed();
        place();
        h.removeCallbacks(look);
        h.postDelayed(look, 1500);
    }

    /** The game up by hand read again (after the player started afresh): from today's games. */
    private void readHandGame(String id) {
        if (id.isEmpty() || ScoreBoard.NO_GAME.equals(id)) return;
        App.io.execute(() -> {
            Game found = null;
            try {
                for (Game g : com.gathin.tvhomephone.sports.OnAir.today(60_000L)) if (id.equals(g.id)) found = g;
            } catch (RuntimeException ignored) {
                // the channel's own meanwhile
            }
            Game got = found;
            App.main.post(() -> {
                if (got == null || !id.equals(handFor) || !started) return;
                handGame = got;
                board.changed();
            });
        });
    }

    void start() {
        if (started || !wanted()) {
            board.view.setVisibility(wanted() ? View.VISIBLE : View.GONE);
            return;
        }
        started = true;
        up = true;
        board.view.setVisibility(inPip ? View.GONE : View.VISIBLE);
        board.start();
        // Switched off in Move and resize (or a channel set Never, or up by hand for the mark alone): its clock and
        // score go on, unseen, and the mark over the logo is its own.
        board.hidden(boardHidden());
        if (ScoreBoard.handOn(a) && handGame == null) {
            handFor = ScoreBoard.handGame(a);
            readHandGame(handFor);
        }
        guard.start();
        if (sampler == null) sampler = new FrameSampler();
        h.postDelayed(look, 4000);
        place();
        bugCover.start();
        h.postDelayed(playLook, 1_000L);
    }

    /** The whole picture, small, for LivePlay: the board's own copy, made again for each look. */
    private Bitmap playFrame;
    private final Runnable playLook = this::playLook;

    /**
     * Twice a second while the channel's match is in play here (not in PiP): the whole picture, small, from the player's
     * own frames, so LivePlay can tell live play from celebrations and replays after a goal (ScoreBoard.play).
     */
    private void playLook() {
        h.removeCallbacks(playLook);
        if (!started) return;
        Game g = host.game();
        if (inPip || editing || sampler == null || !board.channelGame() || g == null || g.state != Game.LIVE || g.halftime) {
            h.postDelayed(playLook, 2_000L);
            return;
        }
        int w = com.gathin.broadcast.LivePlay.W, ht = com.gathin.broadcast.LivePlay.H;
        if (playFrame == null) playFrame = Bitmap.createBitmap(w, ht, Bitmap.Config.ARGB_8888);
        boolean asked = sampler.copy(video.surface(), playFrame, (b, at) -> {
            if (b == null || b.getWidth() != w || b.getHeight() != ht) return;
            int[] px = new int[w * ht];
            b.getPixels(px, 0, w, 0, 0, w, ht);
            board.play.frame(px, w, ht, at);
        });
        h.postDelayed(playLook, asked ? 500L : 120L);
    }

    /**
     * The plates over what grows out of the channel's own scoreboard (BugCover): each {l, t, r, b} in the stream's
     * pixels, laid on the picture where it is, in the board's look; only while the board is up and still.
     */
    private void showPlates(java.util.List<int[]> rects, java.util.List<String> words) {
        int[] vp = video.videoPixels();
        boolean show = started && !inPip && !editing && board.bug.getVisibility() == View.VISIBLE && !board.bug.closing()
                && vp[0] > 0 && vp[1] > 0 && pfw > 1 && pfh > 1;
        java.util.List<android.graphics.RectF> rs = new java.util.ArrayList<>();
        java.util.List<String> ws = new java.util.ArrayList<>();
        if (show) {
            float sx = pfw / vp[0], sy = pfh / vp[1];
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                rs.add(new android.graphics.RectF(pfx + r[0] * sx, pfy + r[1] * sy, pfx + r[2] * sx, pfy + r[3] * sy));
                ws.add(words.get(i));
            }
        }
        int[] b = boardRoot;
        android.graphics.RectF br = b == null ? null : new android.graphics.RectF(b[0], b[1], b[0] + b[2], b[1] + b[3]);
        float bh = b == null ? 30 * a.getResources().getDisplayMetrics().density : b[3];
        plates.set(rs, ws, br, board.bug.stripLook(), bh);
    }

    void stop() {
        if (editing) endAdjust(true);
        bugCover.stop();
        plates.clear();
        intro.stop();
        endAdBreak();
        started = false;
        up = false;
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
        logoLearned = false;
        breaks = null;
        // Another channel: your logo back at once (its adverts watched afresh).
        if (logoAwayNow) {
            if (logoAnim != null) logoAnim.cancel();
            logoAwayNow = false;
            for (View v : new View[]{cover, fill}) {
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setAlpha(1f);
            }
        }
        finder = null;
        reader = null;
        ocrOkAt = 0;
        ocrMisses = 0;
        board.changed();
        place();
        h.removeCallbacks(look);
        h.removeCallbacks(readClock);
        if (started) h.postDelayed(look, 4000);
        // Another channel: its own setting (never, always, its logo) from now.
        if (!started && wanted()) start();
        else if (started) {
            board.view.setVisibility(inPip || !wanted() ? View.GONE : View.VISIBLE);
            board.hidden(boardHidden());
            cover.look(lookHere());
        }
    }

    /** The channel's delay changed (the alerts' chips, "It's just happened"). */
    void delayChanged() {
        board.behind();
    }

    void pip(boolean in) {
        if (in && editing) endAdjust(true);
        if (in) intro.stop();
        if (in) endAdBreak();
        inPip = in;
        // In the small window the board stays, small, in the same look, its clock going on: no drops, no covers.
        board.small(in);
        board.view.setVisibility(!wanted() ? View.GONE : View.VISIBLE);
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
        } else if (!wanted()) {
            // (Up by hand, or a channel set to always have them, keeps them up.)
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
        return started && inPip && wanted() && board.showing();
    }

    /** Whether the channel's clock was read off its scoreboard lately. */
    boolean reading() {
        return System.currentTimeMillis() - ocrOkAt < 30_000L;
    }

    // ---- see-through: the channel's own logo filled in under Whitebrook Road Sports (LogoShape, LogoFill) ---------------

    private boolean patchLoop;
    private final Runnable patchRun = this::patchNow;
    /** Filling in, on a thread of its own (one for the app: the player comes and goes, the thread stays the one). */
    private static final java.util.concurrent.ExecutorService patchIo = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SeeThrough");
        t.setDaemon(true);
        return t;
    });
    /** The logo under the cover now: its shape (learned, or kept from before) and the frames it is learned from. */
    private LogoShape.Here here;
    /** The cover's part of the frame, in the stream's own pixels; how often that could not be had running; when last asked. */
    private Bitmap patchPart;
    private int partMisses;
    private long patchAt;

    /** What see-through is doing over the channel playing, in a few words for its options (null when it is off). */
    String seeThroughStatus() {
        if (!seeThroughHere()) return null;
        if (!cover.seeThroughLook()) return a.getString(R.string.live53_see_look);
        LogoShape.Here at = here;
        if (at == null || cover.getVisibility() != View.VISIBLE) return a.getString(R.string.live53_see_waiting);
        if (at.known()) return a.getString(R.string.live53_see_known);
        if (at.block()) return a.getString(R.string.live53_see_block);
        return a.getString(R.string.live53_see_learning, Math.min(at.learnt(), LogoShape.ENOUGH), LogoShape.ENOUGH);
    }

    /**
     * The cover moved onto the channel's logo where see-through found it not all under the cover (the box found over the
     * picture set a little off it): over it and a little round it, for this channel (its own place, when it has one).
     * Not while it is being moved by hand. Space: the stream's pixels (vp).
     */
    private void onTheLogo(int[] box, int[] vp, int bx, int by, int bw, int bh) {
        if (box == null || editing || carrying || vp[0] <= 0 || vp[1] <= 0) return;
        logoLearned = true;
        int slack = Math.max(2, Math.round(3f * vp[1] / 1080f));
        if (box[0] >= bx - slack && box[1] >= by - slack && box[2] <= bx + bw + slack && box[3] <= by + bh + slack) return;
        float l = box[0] / (float) vp[0], t = box[1] / (float) vp[1], r = box[2] / (float) vp[0], b = box[3] / (float) vp[1];
        BoardPlace own = mine();
        if (own != null && own.logo() && mineKey != null) {
            float pad = (b - t) * 0.12f, padX = pad * vp[1] / (float) vp[0];
            own.lw = Math.max(BoardPlace.MIN_W, Math.min(BoardPlace.MAX_W, r - l + 2 * padX));
            own.lh = Math.max(BoardPlace.MIN_LH, Math.min(BoardPlace.MAX_LH, b - t + 2 * pad));
            own.lx = Math.max(0f, Math.min(1f - own.lw, l - padX));
            own.ly = Math.max(0f, Math.min(1f - own.lh, t - pad));
            BoardPlace.put(a, mineKey, own);
        } else {
            logo = new BugFinder.Box(l, t, r, b, false);
        }
        place();
    }

    /** No fill under the cover: it stands on its plate. */
    private void unfill() {
        fill.clear();
        cover.filled(false);
    }

    /**
     * See-through: the channel's own logo under the cover learned stroke by stroke from the player's own frames (the
     * cover's part of each, in the stream's pixels; kept per channel) and, about ten times a second, its strokes alone
     * filled in from the live picture just round them (LogoShape), all else under the cover the channel's own picture.
     * The cover stands on its plate while the shape is learned, for a logo that is a solid block, and when the frames
     * cannot be had. Stops when not wanted.
     */
    private void patchNow() {
        if (!started || inPip || sampler == null || !cover.wantsPatch()) {
            patchLoop = false;
            unfill();
            return;
        }
        int[] vp = video.videoPixels();
        FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cover.getLayoutParams();
        if (vp[0] <= 0 || vp[1] <= 0 || pfw <= 1 || pfh <= 1 || cp.width <= 0 || cp.height <= 0) {
            unfill();
            h.postDelayed(patchRun, 500L);
            return;
        }
        if (partMisses >= 3) {
            // The frames could not be had (three times running): on its plate for a while, then tried again.
            partMisses = 0;
            unfill();
            h.postDelayed(patchRun, 3_000L);
            return;
        }
        // The cover's place in the stream's own pixels (the logo is under it), and round it room for what the logo is
        // told from (its softening).
        float kx = vp[0] / pfw, ky = vp[1] / pfh;
        int bx = Math.round((cp.leftMargin - pfx) * kx), by = Math.round((cp.topMargin - pfy) * ky);
        int bw = Math.round(cp.width * kx), bh = Math.round(cp.height * ky);
        // Wide enough round the cover to find the logo when the cover is a little off it: across, a good part of its
        // width; up and down, its own height and more.
        int m = Math.max(8, Math.round(LogoShape.MARGIN * vp[1] / 1080f)), mx = Math.max(m, Math.round(0.4f * bw)), my = Math.max(m, bh + 2 * m);
        int x0 = Math.max(0, bx - mx), y0 = Math.max(0, by - my), x1 = Math.min(vp[0], bx + bw + mx), y1 = Math.min(vp[1], by + bh + my);
        if (x1 - x0 < 8 || y1 - y0 < 8) {
            unfill();
            h.postDelayed(patchRun, 500L);
            return;
        }
        String key = chKey();
        int bl = bx - x0, bt = by - y0, br = bl + bw, bb = bt + bh;
        if (here == null || !here.same(key, vp[0], vp[1], x0, y0, x1 - x0, y1 - y0, bl, bt, br, bb)) {
            // Another channel, another picture size, or the cover moved: its shape from its file, or learned (the plate
            // meanwhile).
            unfill();
            here = new LogoShape.Here(new java.io.File(a.getFilesDir(), "logo_shapes"), key, vp[0], vp[1], x0, y0, x1 - x0, y1 - y0,
                    bl, bt, br, bb);
        }
        LogoShape.Here at = here;
        long now = android.os.SystemClock.uptimeMillis(), wait = (at.known() ? 90L : 300L) - (now - patchAt);
        if (wait > 0) {
            h.postDelayed(patchRun, wait);
            return;
        }
        if (patchPart == null || patchPart.getWidth() != at.w || patchPart.getHeight() != at.h) {
            patchPart = Bitmap.createBitmap(at.w, at.h, Bitmap.Config.ARGB_8888);
        }
        patchAt = now;
        Bitmap into = patchPart;
        boolean asked = sampler.copyPart(video.surface(), new android.graphics.Rect(x0, y0, x1, y1), into, (got, when) -> {
            // On the sampler's thread: the pixels out of the frame, and the frame let go for the next.
            int[] px = null;
            if (got != null && got.getWidth() == at.w && got.getHeight() == at.h) {
                px = new int[at.w * at.h];
                got.getPixels(px, 0, at.w, 0, 0, at.w, at.h);
            }
            int[] pixels = px;
            long taken = android.os.SystemClock.uptimeMillis();
            patchIo.execute(() -> {
                LogoShape.Fill f = null;
                Bitmap made = null;
                if (pixels != null) {
                    try {
                        f = at.take(pixels, taken);
                        if (f != null && !f.empty()) made = Bitmap.createBitmap(f.argb, f.w, f.h, Bitmap.Config.ARGB_8888);
                    } catch (RuntimeException | OutOfMemoryError e) {
                        f = null;
                    }
                }
                LogoShape.Fill done = f;
                Bitmap b = made;
                h.post(() -> {
                    partMisses = pixels == null ? partMisses + 1 : 0;
                    if (started && here == at && cover.wantsPatch()) {
                        if (done == null) {
                            unfill();
                        } else {
                            if (b != null) {
                                // From the stream's pixels to the screen's: where the picture is, where the root is.
                                int[] rl = new int[2];
                                root.getLocationOnScreen(rl);
                                float sx = pfw / vp[0], sy = pfh / vp[1];
                                fill.show(b, rl[0] + pfx + done.x * sx, rl[1] + pfy + done.y * sy,
                                        rl[0] + pfx + (done.x + done.w) * sx, rl[1] + pfy + (done.y + done.h) * sy);
                            } else {
                                fill.clear();
                            }
                            cover.filled(true);
                        }
                        onTheLogo(at.logoBox(), vp, bx, by, bw, bh);
                    }
                    h.postDelayed(patchRun, 10L);
                });
            });
        });
        if (!asked) h.postDelayed(patchRun, 120L);
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
        cover.look(lookHere());
        place();
        if (started && lookHere() != LogoCover.Look.OFF) {
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
        // Up by hand for a game: that one, as the channel's own.
        if (handGame != null && byHand() && ScoreBoard.handOn(a) && handGame.id != null && handGame.id.equals(ScoreBoard.handGame(a))) return handGame;
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
            bp.height = Math.round((upright ? 24 : 30) * d * sizeHere() * board.bug.tall());
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
        LogoCover.Look look = lookHere();
        boolean base = look != LogoCover.Look.OFF && started && !inPip && wanted();
        // Up by hand: the mark shows whether or not the channel's logo was found (top right of the picture then).
        boolean hand = byHand();
        boolean show = base && (own.logo() || logo != null || hand);
        if (show) {
            FrameLayout.LayoutParams cp = (FrameLayout.LayoutParams) cover.getLayoutParams();
            if (own.logo()) {
                cp.width = Math.max(Math.round(12 * d), Math.round(own.lw * fw));
                cp.height = Math.max(Math.round(8 * d), Math.round(own.lh * fh));
                cp.leftMargin = Math.max(0, Math.min(Math.round(fx + own.lx * fw), root.getWidth() - cp.width));
                cp.topMargin = Math.max(0, Math.min(Math.round(fy + own.ly * fh), root.getHeight() - cp.height));
            } else if (logo == null) {
                cp.width = Math.max(Math.round(12 * d), Math.round(0.13f * fw));
                cp.height = Math.max(Math.round(8 * d), Math.round(0.075f * fh));
                cp.leftMargin = Math.max(0, Math.min(Math.round(fx + (1 - 0.035f) * fw) - cp.width, root.getWidth() - cp.width));
                cp.topMargin = Math.max(0, Math.min(Math.round(fy + 0.05f * fh), root.getHeight() - cp.height));
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
                cover.tag(tagHere().on(ch == null ? null : ch.name));
                cover.channelName(ch == null ? null : ch.name);
                cover.sport(board.f1() ? "f1" : "");
                cover.live(board.live());
                cover.boardLook(board.bug.stripLook());
                cover.competition(board.compPic(), board.compName());
                cover.compOn(compHere());
                cover.liveBar(liveHere());
                cover.what(ScoreBoard.whatFor(ScoreBoard.logoWhat(a), board.compName(), board.f1()));
                cover.nameOnly(nameOnlyHere());
                cover.seeThrough(seeThroughHere());
                cover.style(styleHere());
            }
        }
        cover.setVisibility(show ? View.VISIBLE : View.GONE);
        // See-through: the channel's logo learned and filled in under the cover, several times a second.
        if (!show || !cover.wantsPatch()) {
            unfill();
        } else if (!patchLoop) {
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
        // The board's place, for the watch on the channel's own scoreboard under it (BugCover).
        boardRoot = inPip ? null : new int[]{boardLeft, boardTop, bw, bh};
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
        if (editing || !started || inPip || !wanted()) return;
        Channel ch = host.current();
        if (ch == null) return;
        host.hideControls();
        mine();
        editing = true;
        board.preview(true);
        float d = a.getResources().getDisplayMetrics().density;
        // Started from where they are now.
        boardFromHere();
        if (!mine.logo() && lookHere() != LogoCover.Look.OFF) logoFromHere();
        shield = new View(a);
        shield.setBackgroundColor(0x40000000);
        shield.setClickable(true);
        root.addView(shield, Math.max(0, root.indexOfChild(cover)), new FrameLayout.LayoutParams(-1, -1));
        boolean wide = root.getWidth() > root.getHeight();
        panel = AdjustPanel.build(a, new AdjustPanel.Actions() {
            @Override public void boardShown(boolean on) {
                ScoreBoard.setBoardShown(a, on);
                board.hidden(boardHidden());
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
                setLookHere(on ? ScoreBoard.lastLogo(a) : LogoCover.Look.OFF);
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
                setLookHere(l);
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
        }, ScoreBoard.style(a), lookHere(), ScoreBoard.still(a), ScoreBoard.boardShown(a), timingWords());
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
                logos = lookHere() != LogoCover.Look.OFF || (ScoreBoard.adBreak(a) && board.football());
        // The ad-break screen: its session's logo learnt as soon as there is one (so the first break isn't missed), and
        // on a sports channel without a game the broadcaster's scoreboard looked for (a match on: a general session).
        boolean adverts = ScoreBoard.adBreak(a) && (host.adSession() != null || host.sportsNow());
        if (!started || inPip || sampler == null) return;
        // F1's tower read for its lap, on an F1 channel.
        // The channel's scoreboard (or F1's tower) read: the reads keep themselves going once started.
        if (ScoreBoard.reads(a) && !screenLoop()) readSoon(1000);
        if (!scoreboard && !logos && !adverts) {
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
            // Without a game on the board: the broadcaster's scoreboard only looked for, never placed over.
            BugFinder.Box seenBoard = done && !scoreboard && adverts ? f.find() : null;
            // The adverts watched for the ad-break screen, and for your logo stepping aside in them.
            boolean watchAdverts = ScoreBoard.adBreak(a) || ScoreBoard.logoAway(a);
            BugFinder.Box mark = done && (logos || watchAdverts) ? f.logo(found) : null;
            boolean[] outline = mark != null && watchAdverts ? f.logoMask(mark) : null;
            h.post(() -> safely(() -> {
                if (!started || f != finder) return;
                if (!done) {
                    h.postDelayed(look, SAMPLE_MS);
                    return;
                }
                finder = null;
                // A scoreboard on the picture: a match is on this channel (the ad-break screen's general session).
                if (found != null || seenBoard != null) {
                    boardSeenAt = System.currentTimeMillis();
                    boardSeenOn = chKey();
                }
                // Kept still: the mark stays over the logo where it was first found.
                if ((mark != null || logo != null) && !(ScoreBoard.still(a) && logo != null) && !logoLearned) {
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
     * gone to its adverts: the ad-break screen over them (at half time with its half-time widgets: the quiz, fun facts,
     * prayer times). Back for a few seconds: gone again.
     */
    private void checkBreak() {
        h.removeCallbacks(checkBreak);
        BreakWatch w = breaks;
        if (!started || w == null || sampler == null || !ScoreBoard.adBreak(a) && !ScoreBoard.logoAway(a)) {
            logoAway(false);
            return;
        }
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
                    long now = System.currentTimeMillis();
                    // Your logo out while the channel's is gone, back in with it.
                    logoAway(on && ScoreBoard.logoAway(a));
                    if (!on) adBreakHidden = false;
                    // The ad-break screen takes every break of its session (the listings' programme, else the game's,
                    // else a match's scoreboard seen): the build-up, half time, after the game (AdBreak.take).
                    AdBreak.Session sn = ScoreBoard.adBreak(a) ? session() : null;
                    int take = AdBreak.take(ScoreBoard.adBreak(a), sn, now);
                    if (on && take == AdBreak.TAKE_AD_BREAK && adBreak == null && !adBreakHidden) startAdBreak(sn, false);
                    else if (adBreak != null && !manualAdBreak && (!on || take != AdBreak.TAKE_AD_BREAK)) endAdBreak();
                }
                // On a break: looked at twice as often, so your logo is back hardly a moment after the channel's.
                h.postDelayed(checkBreak, w.onBreak() ? 1000 : 2000);
            }, () -> h.postDelayed(checkBreak, 2000)));
        });
        if (!asked) h.postDelayed(checkBreak, 1000);
    }

    // ---- your logo aside in the channel's adverts ---------------------------------------------------------------------

    /** Your logo is aside now (the channel's gone: its adverts). */
    private boolean logoAwayNow;
    private android.animation.AnimatorSet logoAnim;

    /**
     * Your logo popping out as the channel's goes (a little swell, then shrinking away as it fades) or popping back in
     * as the channel's returns (from small, overshooting a touch and settling): the cover and the see-through fill under it.
     */
    private void logoAway(boolean away) {
        if (away == logoAwayNow) return;
        logoAwayNow = away;
        if (logoAnim != null) logoAnim.cancel();
        java.util.List<android.animation.Animator> parts = new java.util.ArrayList<>();
        for (View v : new View[]{cover, fill}) {
            v.setPivotX(v.getWidth() / 2f);
            v.setPivotY(v.getHeight() / 2f);
            if (away) {
                for (android.util.Property<View, Float> pr : java.util.Arrays.asList(View.SCALE_X, View.SCALE_Y)) {
                    android.animation.ObjectAnimator o = android.animation.ObjectAnimator.ofFloat(v, pr, 1f, 1.12f, 0f);
                    o.setDuration(340);
                    o.setInterpolator(new android.view.animation.AccelerateInterpolator(1.6f));
                    parts.add(o);
                }
                android.animation.ObjectAnimator fa = android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, v.getAlpha(), 1f, 0f);
                fa.setDuration(340);
                parts.add(fa);
            } else {
                for (android.util.Property<View, Float> pr : java.util.Arrays.asList(View.SCALE_X, View.SCALE_Y)) {
                    android.animation.ObjectAnimator o = android.animation.ObjectAnimator.ofFloat(v, pr, 0.3f, 1f);
                    o.setDuration(460);
                    o.setInterpolator(new android.view.animation.OvershootInterpolator(2.4f));
                    parts.add(o);
                }
                android.animation.ObjectAnimator fa = android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f);
                fa.setDuration(230);
                parts.add(fa);
            }
        }
        android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(parts);
        logoAnim = set;
        set.start();
    }

    /** Tests: your logo popping out as if the adverts came, and back in five seconds later. */
    boolean testLogoAway() {
        if (cover.getVisibility() != View.VISIBLE) return false;
        logoAway(true);
        h.postDelayed(() -> logoAway(false), 5000);
        return true;
    }

    boolean onBreak() {
        return adBreak != null;
    }

    // ---- the ad-break screen over a match's adverts -------------------------------------------------------------------

    /** The broadcaster's scoreboard last seen on the picture, and on which channel (a general session's sign). */
    private long boardSeenAt;
    private String boardSeenOn;

    /** The ad-break screen's session now: the player's (listings, a game found), else a general one from the scoreboard seen. */
    private AdBreak.Session session() {
        AdBreak.Session s = host.adSession();
        if (s != null) return s;
        String key = chKey();
        if (boardSeenAt <= 0 || key == null || !key.equals(boardSeenOn)) return null;
        return AdBreak.general(host.programmeNow(), boardSeenAt);
    }

    /** Up over the adverts for this game (manual: from the options, to see it; it goes after two minutes or Hide). */
    void startAdBreak(Game g, boolean manual) {
        startAdBreak(AdBreak.fromGame(g, null, System.currentTimeMillis()), manual);
    }

    /** Up over the adverts for this session: its game's build-up, half time or full time; a general one without a game. */
    void startAdBreak(AdBreak.Session sn, boolean manual) {
        if (adBreak != null || !started || sn == null || inPip) return;
        Game g = sn.game;
        String title = sn.title;
        manualAdBreak = manual;
        Context app = a.getApplicationContext();
        AdBreakView view = new AdBreakView(a, new AdBreakView.Host() {
            @Override public void play(Channel ch) {
                endAdBreak();
                host.play(ch);
            }

            @Override public void hide() {
                adBreakHidden = true;
                endAdBreak();
            }
        });
        adBreak = view;
        // The board's half-time and full-time pop-outs wait while it is up (they come after it, not on top of it).
        board.holdBreakMoments(true);
        root.addView(view, Math.min(4, root.getChildCount()), new FrameLayout.LayoutParams(-1, -1));
        App.io.execute(() -> {
            AdBreakData.Content k = g != null ? AdBreakData.load(app, g) : AdBreakData.general(app, title);
            App.main.post(() -> {
                if (adBreak != view) return;
                // Nothing real to show (nothing could be read): away again, and not tried again until the next break.
                if (!AdBreakView.worth(k)) {
                    adBreakHidden = true;
                    endAdBreak();
                    return;
                }
                // Half time as the picture shows it (the board's game in step with the channel), full time, or asked for
                // by hand: the half-time widgets (the quiz, fun facts, prayer times) in among the last card's own, every
                // widget there is.
                Game onScreen = ScreenTime.game(host.game(), System.currentTimeMillis());
                Game ht = onScreen != null && k.game != null && k.game.id != null && k.game.id.equals(onScreen.id) ? onScreen : k.game;
                k.halfTime = !k.general && (adBreakHalf || ht != null && (AdBreak.atHalfTime(ht) || ht.state == Game.POST));
                boolean sideways = a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
                android.graphics.Rect pic = new android.graphics.Rect(Math.round(pfx), Math.round(pfy), Math.round(pfx + pfw), Math.round(pfy + pfh));
                view.post(() -> {
                    if (adBreak == view) view.show(k, sideways, pic);
                });
            });
        });
        if (manual) h.postDelayed(() -> {
            if (adBreak == view) endAdBreak();
        }, 120_000L);
    }

    /** The ad-break screen away (the adverts over, kick-off, another channel, Hide). */
    void endAdBreak() {
        AdBreakView v = adBreak;
        if (v == null) return;
        adBreak = null;
        manualAdBreak = false;
        adBreakHalf = false;
        board.holdBreakMoments(false);
        v.end(() -> root.removeView(v));
    }

    /** A key for the ad-break screen's cards: true when it took it. */
    boolean adBreakKey(android.view.KeyEvent e) {
        return adBreak != null && adBreak.key(e);
    }

    /** Back: a picked card let go. */
    boolean adBreakBack() {
        return adBreak != null && adBreak.back();
    }

    /** The ad-break screen shown by hand as at half time (the options' and the tests' half-time try). */
    private boolean adBreakHalf;

    /** Options: the ad-break screen now, for the channel's game or else the next real game today. */
    void tryAdBreak() {
        tryAdBreak(false);
    }

    /** Options: as tryAdBreak, as at half time when half is asked for (the quiz, fun facts and prayer times). */
    void tryAdBreak(boolean half) {
        if (adBreak != null) return;
        adBreakHalf = half;
        AdBreak.Session sn = session();
        if (sn != null) {
            startAdBreak(sn, true);
            return;
        }
        Game g = host.soonGame();
        if (g == null) g = host.game();
        if (g != null) {
            startAdBreak(g, true);
            return;
        }
        Context app = a.getApplicationContext();
        App.io.execute(() -> {
            Game next = nextGame(app);
            App.main.post(() -> {
                if (next != null) startAdBreak(next, true);
                else android.widget.Toast.makeText(a, R.string.live58_ad_break_none, android.widget.Toast.LENGTH_SHORT).show();
            });
        });
    }

    /** The next game still to kick off today: a followed club's first, else the soonest. Off the main thread. */
    private static Game nextGame(Context c) {
        java.util.Set<String> mine = com.gathin.tvhomephone.sports.Clubs.starred(c);
        Game best = null, bestMine = null;
        long now = System.currentTimeMillis();
        for (Game x : com.gathin.tvhomephone.sports.OnAir.today(5 * 60_000L)) {
            if (x.state != Game.PRE || x.kickoff <= now) continue;
            if (best == null || x.kickoff < best.kickoff) best = x;
            if (x.involves(mine) && (bestMine == null || x.kickoff < bestMine.kickoff)) bestMine = x;
        }
        return bestMine != null ? bestMine : best;
    }

    // ---- test notifications (the player's options, PlayerTests) ------------------------------------------------------

    /** Why a test over the picture can't show now (a string), or 0 when it can; board: one on the scoreboard. */
    private int testBlocked(boolean onBoard) {
        if (inPip || editing || intro.showing()) return R.string.live7_wait;
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

    /** The programmes on this channel now and next (each half minute): F1's, UFC's and boxing's intros as they begin. */
    void programmes(java.util.List<OverMatches.Programme> guide) {
        if (started && !inPip && !editing) intro.sportTick(guide);
    }

    /** Tests: a sport's intro ("f1", "ufc", "boxing", "football"), now. */
    void testSport(String key, IntConsumer note) {
        int no = testBlocked(false);
        if (no != 0) note.accept(no);
        else intro.testSport(key, note);
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
            case "logo_away":
                ok = testLogoAway();
                break;
            case "var":
            case "added":
                if (!football) {
                    note.accept(R.string.live7_f1);
                    return;
                }
                ok = "var".equals(what) ? board.testVar() : board.testAdded();
                break;
            case "pens":
            case "milestones":
                if (!football) {
                    note.accept(R.string.live7_f1);
                    return;
                }
                ok = "pens".equals(what) ? board.testPens() : board.testMilestones();
                break;
            default:
                return;
        }
        if (!ok) note.accept(R.string.live7_wait);
    }

    /** Tests: the ad-break screen now as at half time (the quiz, fun facts and prayer times in its last card). */
    void testBreak(IntConsumer note) {
        int no = testBlocked(false);
        if (no != 0) note.accept(no);
        else tryAdBreak(true);
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
