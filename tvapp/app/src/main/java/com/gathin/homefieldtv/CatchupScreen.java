package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

/**
 * Catch-up over the picture, as the family's TiviMate plays a programme from the archive: a programme from the
 * provider's archive, or the channel moved back in its own stream.
 *
 * <p>As it starts, the info card at the foot with the archive's mark and the programme's whole length (InfoCard).
 *
 * <p>Going back and on while it plays (the family's point of 24 Nov: Left and Right used to move a spot that waited for
 * OK, so the picture never went anywhere): Left and Right on the plain picture move at once, 10 seconds a press
 * (Settings › VOD › Skip steps), then 30 seconds, a minute and three minutes as the key stays down. The seek bar comes up
 * by itself at the foot (STRIP: the line across the programme, the accent up to the spot, light grey to what has been on
 * so far, a small mark where the picture still is), its white bubble over the knob saying where ("21:14 · 34:10 of
 * 1:00:00": the clock then, the time into the programme and its length; "Live" at the live edge), the programme's name,
 * channel and times under it with how far it has moved ("−1:30"). The picture follows a moment (APPLY_MS) after the remote
 * rests: one request to the archive, never one a press; OK jumps there at once, Back stays where the picture is. Past the
 * programme's start the line is the programme before's; on to the live edge, live (the live screen under this one). The
 * bar goes a few seconds after the remote rests (STRIP_MS); while it shows, Up and Down bring up the overlay rather than
 * change channel. The rewind and fast forward keys move the same way, play/pause pauses (the overlay up), previous and next
 * go to the programme before and the next one.
 *
 * <p>OK brings up the overlay: the card at the top; across the foot the seek line (80 to 1839 at y 861–866); the time in
 * the programme and its length at the left ("34:10 / 1:00:00"); the transport in the middle with the remote on play/pause
 * (|◀ the programme before, ‹10 back a Skip step, ⏸, 30› on 30 seconds, ▶| the next programme or live; 72 px circles of
 * white at 8 %, the remote's an 88 px focus circle); at the right, in a pill, ⟲ (the programme from its start), LIVE and the
 * live dot; the row of tiles peeking in under it. The buttons that move add up press by press, the picture following once
 * the remote rests, as Left and Right do. Up goes to the seek line itself (lit, its knob larger with a ring and the
 * bubble): Left and Right move the spot there the same way, OK jumps, Down back to the transport. Down from the transport
 * brings the tiles up (TV guide, History, the channels last watched) in its place; Up goes back.
 *
 * <p>Up and Down on the plain picture change channel, live; CH+ and CH− show the next or previous channel's card (OK goes
 * to it); OK held or MENU the player's settings (PlayerBar's pills, by themselves here). Back lets go of a move, closes
 * the bar or the overlay, then goes to the TV guide with the programme still in its picture; Back held stops the picture.
 * When a programme ends it goes on into the next; at the live edge it is live again (the live screen under this one).
 * Nothing is made a frame: the words are made again only when the spot's second or its programme changes.
 */
final class CatchupScreen extends Screen implements LiveTv.Listener {
    static final long PANELS_MS = 10_000L, STRIP_MS = 3_000L, APPLY_MS = 700, HALF = 30 * 60_000L, HOUR = 3600_000L;
    /** The overlay's ▶▶: on at least this far a press (◀◀ goes back a Skip step). */
    static final long FORWARD_MS = 30_000L;
    /** What shows: nothing, the card at the foot, the overlay, its tiles, the player's settings, the seek bar by itself. */
    static final int PLAIN = 0, BANNER = 1, OVERLAY = 2, TILES = 3, BAR = 4, STRIP = 5;
    /** The transport and the right pill: their items, and where their middles are (design pixels). */
    static final int SKIP_BACK = 0, REWIND = 1, PLAY = 2, FORWARD = 3, SKIP_NEXT = 4, RESTART = 5, LIVE = 6;
    /** The seek line itself, with the remote on it (Left and Right move the spot, OK jumps there). */
    static final int SEEK = 7;
    static final float[] ITEM_X = {760f, 856f, 960f, 1064f, 1160f, 1660f, 1732f};
    static final float SEEK_Y = 863.5f, TRANSPORT_Y = 934f;
    /** The seek bar by itself: where its scrim starts, its line, the words under it (design pixels). */
    static final float STRIP_SCRIM_Y = 780f, STRIP_Y = 968f, STRIP_WORDS_Y = 1030f;

    private final Channel ch;
    /** Where it starts (a programme's start) and that programme's end; -1 once asked for. */
    private long startAt, startEnd;
    /** Where the remote has moved the spot to and the picture hasn't gone yet (-1: not moving). */
    private long target = -1;
    private int mode = PLAIN, focus = PLAY;
    /** The card's channel on CH+ and CH− (not the one playing); null: this one. */
    private Channel peek;
    private Overlay overlay;
    private PlayerTiles tiles;
    private PlayerBar bar;
    /** The programme at a time and the line's shares, filled in place as it draws (nothing made a frame). */
    private final long[] prog = new long[3];
    private final float[] shares = new float[3];
    /** Settings › VOD › Skip steps as the overlay came up (its ◀◀ says it). */
    private long skipNow = 10_000L;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable apply = this::applyMove;
    private final Runnable hide = () -> {
        if (!tv.live.held() && target < 0 && tv.live.state() != LiveTv.FAILED) setMode(PLAIN);
    };
    private final Runnable leave = () -> {
        if (tv.isTop(this)) tv.pop();
    };

    CatchupScreen(TvActivity tv, Channel c, long from, long programmeEnd) {
        super(tv);
        this.ch = c;
        this.startAt = from;
        this.startEnd = programmeEnd;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override View build() {
        FrameLayout root = new FrameLayout(tv);
        overlay = new Overlay(tv);
        tiles = new PlayerTiles(tv, tilesHost);
        tiles.setVisibility(View.GONE);
        bar = new PlayerBar(tv, barHost);
        root.addView(tiles, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    @Override void shown() {
        tv.live.addListener(this);
        tv.live.place(tv.fullScreen());
        if (startAt >= 0) {
            long from = startAt;
            startAt = -1;
            tv.live.showAt(ch, from, startEnd, 0);
            // As it starts: the card at the foot, the archive's mark in it.
            setMode(BANNER);
            return;
        } else if (tv.live.archived()) {
            // Back to the app: the archive asked for again from where it got to.
            tv.live.resumeBehind();
        } else if (!tv.live.behind()) {
            // The stream's own window went with the stream: live.
            h.post(leave);
            return;
        }
        setMode(PLAIN);
    }

    @Override void hidden() {
        tv.live.removeListener(this);
        h.removeCallbacks(apply);
        h.removeCallbacks(hide);
        h.removeCallbacks(leave);
        target = -1;
        bar.close();
    }

    @Override public void liveChanged() {
        if (startAt < 0 && !tv.live.behind()) {
            // Live again by itself (the archive ran into now): the live screen under this one.
            h.removeCallbacks(leave);
            h.post(leave);
            return;
        }
        if (tv.live.state() == LiveTv.FAILED && mode != OVERLAY) {
            focus = PLAY;
            setMode(OVERLAY);
        }
        overlay.invalidate();
    }

    @Override void tick() {
        overlay.invalidate();
        tiles.invalidate();
    }

    // ---- what shows ---------------------------------------------------------------------------------------------------

    private void setMode(int m) {
        int was = mode;
        mode = m;
        h.removeCallbacks(hide);
        if (m != BANNER) peek = null;
        if (was == BAR && m != BAR) bar.close();
        if (m == OVERLAY) skipNow = com.gathin.homefieldtv.prefs.OtherPrefs.skipMs(tv);
        if (m == OVERLAY || m == TILES) {
            tiles.set(Library.get(tv).recent(null), PlayerTiles.GUIDE);
            // In the overlay the tiles only peek in under the transport; Down brings them up.
            tiles.peek = m == OVERLAY;
            tiles.seekLine = m == TILES;
            tiles.setTranslationY(m == OVERLAY ? Ui.f(1022f - PlayerTiles.TILE_Y) : 0f);
        }
        Fade.show(tiles, m == OVERLAY || m == TILES, 110);
        Fade.show(overlay, m != PLAIN && m != BAR, m == STRIP ? 90 : 130);
        if (m != PLAIN) h.postDelayed(hide, m == STRIP ? STRIP_MS : PANELS_MS);
        float above = m == BANNER ? InfoCard.FOOT_Y - 16f : m == OVERLAY ? SEEK_Y - 40f : m == TILES ? PlayerTiles.SCRIM_Y + 40f
                : m == BAR ? PlayerBar.HEAD_TOP - 16f : m == STRIP ? STRIP_Y - 110f : -1f;
        tv.live.subs.room(true, above);
        overlay.invalidate();
    }

    private void poke() {
        h.removeCallbacks(hide);
        if (mode != PLAIN) h.postDelayed(hide, mode == STRIP ? STRIP_MS : PANELS_MS);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean holds(int code) {
        if (mode != PLAIN && mode != BANNER && mode != STRIP) return false;
        return code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    @Override boolean keyLong(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                tv.stopPlayback();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                setMode(PLAIN);
                tv.push(new HistoryScreen(tv));
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                setMode(PLAIN);
                tv.openSearch();
                return true;
            default:
                return false;
        }
    }

    @Override boolean key(int code, KeyEvent e) {
        if (mode == BAR) {
            if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
                setMode(PLAIN);
                return true;
            }
            return bar.key(code);
        }
        if (mode == TILES) {
            poke();
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                setMode(OVERLAY);
                return true;
            }
            if (tiles.key(code)) return true;
        }
        int repeat = e == null ? 0 : e.getRepeatCount();
        switch (code) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                bar.open();
                setMode(BAR);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mode == OVERLAY && focus != SEEK) {
                    // Along the transport and the pill.
                    int n = focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    if (n >= SKIP_BACK && n <= LIVE) focus = n;
                    poke();
                    overlay.invalidate();
                    return true;
                }
                // The plain picture, the bar or the seek line: back or on at once, faster the longer the key is held.
                seek(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, repeat);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
            case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD:
                seek(-1, repeat);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD:
                seek(1, repeat);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                if (repeat == 0) programmeStep(-1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (repeat == 0) programmeStep(1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (repeat == 0) playPause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                tv.live.hold(false);
                applyMove();
                focus = PLAY;
                setMode(OVERLAY);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                tv.live.hold(true);
                focus = PLAY;
                setMode(OVERLAY);
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (repeat == 0) tv.pop();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mode == OVERLAY && focus == SEEK) {
                    // From the line down to the transport (a move made goes on by itself).
                    focus = PLAY;
                    poke();
                    overlay.invalidate();
                    return true;
                }
                if (mode == OVERLAY) {
                    setMode(TILES);
                    return true;
                }
                if (mode == STRIP) {
                    // The bar up: the overlay's buttons, not another channel.
                    focus = PLAY;
                    setMode(OVERLAY);
                    return true;
                }
                if (repeat == 0) channel(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mode == OVERLAY || mode == STRIP) {
                    // Up to the line (from the bar: the overlay with the remote on it).
                    focus = SEEK;
                    if (mode == STRIP) setMode(OVERLAY);
                    else poke();
                    overlay.invalidate();
                    return true;
                }
                if (repeat == 0) channel(-1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN: {
                // The next or previous channel's card, without leaving the programme.
                int d = com.gathin.homefieldtv.prefs.RemoteKeys.zapDir(code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1,
                        com.gathin.homefieldtv.prefs.RemoteKeys.reversed(tv));
                Channel from = mode == BANNER && peek != null ? peek : ch;
                Channel to = Library.get(tv).step(from, d);
                Channel keep = to == null || to.key.equals(ch.key) ? null : to;
                setMode(BANNER);
                peek = keep;
                overlay.invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_INFO:
                setMode(mode == BANNER && peek == null ? PLAIN : BANNER);
                return true;
            default:
                // The number keys change channel from live TV, not here.
                return code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9;
        }
    }

    /** Up or Down: the next or previous channel, live (the live screen under this one plays it). */
    private void channel(int d) {
        boolean reversed = com.gathin.homefieldtv.prefs.RemoteKeys.reversed(tv);
        Screen under = tv.below(this);
        if (under instanceof LiveScreen) ((LiveScreen) under).next(com.gathin.homefieldtv.prefs.RemoteKeys.zapDir(d, reversed));
        tv.pop();
    }

    @Override void ok() {
        if (mode == BAR) {
            bar.ok();
            return;
        }
        if (mode == TILES) {
            tiles.ok();
            return;
        }
        if (mode == BANNER && peek != null) {
            Screen under = tv.below(this);
            Channel to = peek;
            tv.pop();
            if (under instanceof LiveScreen) ((LiveScreen) under).tuneFromList(to);
            return;
        }
        if (target >= 0 && (mode == STRIP || mode == OVERLAY && (focus == SEEK || focus == PLAY))) {
            // A move waiting for the remote to rest: the picture jumps there now.
            applyMove();
            return;
        }
        if (mode != OVERLAY) {
            // The overlay, the remote on play/pause.
            focus = PLAY;
            setMode(OVERLAY);
            return;
        }
        poke();
        switch (focus) {
            case SKIP_BACK:
                programmeStep(-1);
                break;
            case REWIND:
                moveBy(-jumps(com.gathin.homefieldtv.prefs.OtherPrefs.skipMs(tv))[0]);
                break;
            case FORWARD:
                moveBy(jumps(com.gathin.homefieldtv.prefs.OtherPrefs.skipMs(tv))[1]);
                break;
            case SKIP_NEXT:
                programmeStep(1);
                break;
            case RESTART:
                go(programme(spot())[0]);
                break;
            case LIVE:
                tv.pop();
                break;
            default:
                // Play/pause, and on the line with nothing moved.
                playPause();
                break;
        }
    }

    @Override void okLong() {
        if (mode == BAR) return;
        bar.open();
        setMode(BAR);
    }

    @Override boolean back() {
        if (mode == BAR) {
            bar.back();
            return true;
        }
        if (target >= 0) {
            // Back lets the move go: the picture stays where it is.
            h.removeCallbacks(apply);
            target = -1;
            if (mode == STRIP) setMode(PLAIN);
            else poke();
            overlay.invalidate();
            return true;
        }
        if (mode != PLAIN) {
            setMode(PLAIN);
            return true;
        }
        // TiviMate: Back from the programme is the TV guide, the programme still in its picture.
        tv.toGuide();
        return true;
    }

    private void playPause() {
        if (tv.live.state() == LiveTv.FAILED) {
            tv.live.tryAgain();
        } else {
            tv.live.hold(!tv.live.held());
        }
        if (mode != OVERLAY) {
            focus = PLAY;
            setMode(OVERLAY);
        }
        overlay.invalidate();
    }

    // ---- moving --------------------------------------------------------------------------------------------------

    /** Pure: how far the n-th step of a key held moves the spot: the Skip step first, then 30 s, a minute, three minutes. */
    static long step(int n, long first) {
        return n <= 5 ? first : n <= 15 ? Math.max(first, 30_000L) : n <= 30 ? Math.max(first, 60_000L) : 180_000L;
    }

    /** Pure: the overlay's ◀◀ and ▶▶ a press: {back a Skip step, on 30 seconds (or the step, when longer)}. */
    static long[] jumps(long skip) {
        return new long[]{skip, Math.max(FORWARD_MS, skip)};
    }

    /** Pure: where the spot can go: no further back than the archive keeps (MAX_VALUE: none known), no later than now. */
    static long spot(long t, long earliest, long now) {
        if (earliest != Long.MAX_VALUE && t < earliest) t = earliest;
        return Math.min(t, now);
    }

    /** Pure: whether time t is the live edge (within LIVE_EDGE_MS of now): letting go there is live. */
    static boolean liveEdge(long t, long now) {
        return t >= now - LiveTv.LIVE_EDGE_MS;
    }

    /** Where the spot is: where it has been moved to, else where the picture is. */
    private long spot() {
        return target >= 0 ? target : tv.live.shownAt();
    }

    /** Left and Right, rewind and fast forward: the key's repeat-th step (faster held), the bar up from the plain picture. */
    private void seek(int d, int repeat) {
        moveBy(d * step(repeat + 1, com.gathin.homefieldtv.prefs.OtherPrefs.skipMs(tv)));
        if (mode == PLAIN || mode == BANNER) setMode(STRIP);
    }

    private void moveBy(long ms) {
        moveTo(spot() + ms);
    }

    /** The spot to t at once (the bar shows it); the picture asked for from there once the remote rests (APPLY_MS). */
    private void moveTo(long t) {
        target = spot(t, tv.live.earliest(ch), System.currentTimeMillis());
        h.removeCallbacks(apply);
        h.postDelayed(apply, APPLY_MS);
        poke();
        overlay.invalidate();
    }

    /** |◀ and ▶|, previous and next: the programme before the spot's (its start), or the next one (live once on now). */
    private void programmeStep(int d) {
        Guide.Schedule s = Library.get(tv).schedule(ch.key);
        long at = spot();
        moveTo(d < 0 ? previousStart(s, at) : nextStart(s, at));
        if (mode == PLAIN || mode == BANNER) setMode(STRIP);
    }

    /** The picture to time t now (live at the live edge). */
    private void go(long t) {
        target = spot(t, tv.live.earliest(ch), System.currentTimeMillis());
        applyMove();
    }

    /** The picture to where the spot was moved: asked for from there (live at the live edge). */
    private void applyMove() {
        h.removeCallbacks(apply);
        if (target < 0) return;
        long t = target;
        target = -1;
        if (liveEdge(t, System.currentTimeMillis())) {
            tv.pop();
            return;
        }
        long shown = tv.live.shownAt();
        tv.live.showAt(ch, t, programme(t)[1], Long.compare(t, shown));
        poke();
        overlay.invalidate();
    }

    /** Pure: the programme at t in s, into out: {start, end, its place in the guide (-1: none; then the hour from the half hour round t)}. */
    static long[] programme(Guide.Schedule s, long t, long[] out) {
        int i = s == null ? -1 : s.at(t);
        if (i >= 0) {
            out[0] = s.start[i];
            out[1] = s.stop[i];
            out[2] = i;
        } else {
            long a = t - Math.floorMod(t, HALF);
            out[0] = a;
            out[1] = a + HOUR;
            out[2] = -1;
        }
        return out;
    }

    /** Pure: |◀: the start of the programme before the one at t. */
    static long previousStart(Guide.Schedule s, long t) {
        long[] p = programme(s, t, new long[3]);
        return programme(s, p[0] - 1, p)[0];
    }

    /** Pure: ▶|: the start of the programme after the one at t (its end). */
    static long nextStart(Guide.Schedule s, long t) {
        return programme(s, t, new long[3])[1];
    }

    /** The programme on the channel at t, into prog (the one array: read it before asking again). */
    private long[] programme(long t) {
        return programme(Library.get(tv).schedule(ch.key), t, prog);
    }

    // ---- words ---------------------------------------------------------------------------------------------------

    /** Pure: a time in a programme as the overlay writes it: "00:05", "55:00", "1:02:03". */
    static String clock(long ms) {
        long s = Math.max(0L, ms / 1000);
        long hh = s / 3600, m = s / 60 % 60, sec = s % 60;
        String mm = (m < 10 ? "0" : "") + m, ss = (sec < 10 ? "0" : "") + sec;
        return hh > 0 ? hh + ":" + mm + ":" + ss : mm + ":" + ss;
    }

    /** Pure: the bubble after the clock: " · 34:10 of 1:00:00" (into the programme, its length), or " · Live". */
    static String bubbleRest(long into, long length, boolean live) {
        return live ? " · Live" : " · " + clock(into) + " of " + clock(length);
    }

    /** Pure: how far the spot is from the picture: "−1:30" back, "+0:10" on, "−1:02:03". */
    static String delta(long ms) {
        long s = Math.abs(ms) / 1000, hh = s / 3600, m = s / 60 % 60, sec = s % 60;
        String ss = (sec < 10 ? ":0" : ":") + sec;
        return (ms < 0 ? "−" : "+") + (hh > 0 ? hh + (m < 10 ? ":0" : ":") + m + ss : m + ss);
    }

    // ---- previews and tests -------------------------------------------------------------------------------------

    /** Previews: the bar by itself as the remote moves the spot to t (from the plain picture). */
    void previewMove(long t) {
        target = t;
        setMode(STRIP);
    }

    /** Previews and tests: the overlay with the remote on the seek line, the spot moved to t. */
    void previewScrub(long t) {
        setMode(OVERLAY);
        target = t;
        focus = SEEK;
        overlay.invalidate();
    }

    /** Tests: where the spot has been moved to (-1: not moving), what shows, where the remote is. */
    long scrubTarget() {
        return target;
    }

    int modeForTests() {
        return mode;
    }

    int focusForTests() {
        return focus;
    }

    /** Tests: the programme the line is drawn across now {start, end}, and the line's shares {the spot, on so far}. */
    long[] lineForTests() {
        long[] p = programme(spot());
        return new long[]{p[0], p[1]};
    }

    float[] sharesForTests() {
        float[] sh = seekShares(spot());
        return new float[]{sh[0], sh[1]};
    }

    /** Previews: the overlay with the remote on an item, or the tiles brought up (TILES). */
    void previewMode(int m, int at) {
        focus = at;
        setMode(m);
    }

    // ---- the bar's and the tiles' hosts -------------------------------------------------------------------------------

    private final PlayerBar.Host barHost = new PlayerBar.Host() {
        @Override public Channel channel() {
            return ch;
        }

        @Override public void act(int item) {
            switch (item) {
                case PlayerBar.GUIDE:
                    tv.toGuide();
                    break;
                case PlayerBar.SEARCH:
                    tv.openSearch();
                    break;
                case PlayerBar.CHANNELS:
                    tv.push(new ChannelListScreen(tv));
                    break;
                case PlayerBar.HISTORY:
                    tv.push(new HistoryScreen(tv));
                    break;
                case PlayerBar.MOVIES:
                    Rail.go(tv, Rail.FILMS, -1);
                    break;
                case PlayerBar.SHOWS:
                    Rail.go(tv, Rail.SHOWS, -1);
                    break;
                case PlayerBar.RECORDINGS:
                    Rail.go(tv, Rail.RECORDINGS, -1);
                    break;
                case PlayerBar.MY_LIST:
                    Rail.go(tv, Rail.MY_LIST, -1);
                    break;
                case PlayerBar.SETTINGS:
                    tv.openSettings();
                    break;
                case PlayerBar.EXIT:
                    tv.moveTaskToBack(true);
                    break;
                case PlayerBar.START_OVER:
                    go(programme(spot())[0]);
                    break;
                default:
                    break;
            }
        }

        @Override public boolean canStartOver() {
            return true;
        }

        @Override public boolean barUp(float x) {
            return false;
        }

        @Override public boolean recentsAbove() {
            return false;
        }

        @Override public boolean underCard() {
            // The pills by themselves here, over a scrim of their own.
            return false;
        }

        @Override public void closed() {
            if (mode == BAR) setMode(PLAIN);
        }
    };

    private final PlayerTiles.Host tilesHost = new PlayerTiles.Host() {
        @Override public void openGuide() {
            tv.toGuide();
        }

        @Override public void openHistory() {
            setMode(PLAIN);
            tv.push(new HistoryScreen(tv));
        }

        @Override public void tune(Channel c) {
            // Another channel, live: the live screen under this one plays it.
            Screen under = tv.below(CatchupScreen.this);
            tv.pop();
            if (under instanceof LiveScreen) ((LiveScreen) under).tuneFromList(c);
        }

        @Override public boolean edge(int dir) {
            if (dir == Ui.UP) setMode(OVERLAY);
            return true;
        }

        @Override public float[] seek() {
            return seekShares(spot());
        }
    };

    /** The seek line's shares of the spot's programme: {the spot, what has been on so far, 0 (not live)}; filled in place. */
    private float[] seekShares(long at) {
        long[] p = programme(at);
        float len = Math.max(1L, p[1] - p[0]);
        shares[0] = (at - p[0]) / len;
        shares[1] = (Math.min(System.currentTimeMillis(), p[1]) - p[0]) / len;
        shares[2] = 0f;
        return shares;
    }

    // ---- the overlay -------------------------------------------------------------------------------------------------

    private final class Overlay extends View {
        private final InfoCard card;
        private final Paint scrim = new Paint(), stripScrim = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
                line = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG),
                knob = new Paint(Paint.ANTI_ALIAS_FLAG), bubbleP = new Paint(Paint.ANTI_ALIAS_FLAG),
                arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint timeP = Type.clock(32f, Type.REGULAR, Palette.TEXT1), totalP = Type.clock(32f, Type.REGULAR, Palette.TEXT2);
        private final TextPaint liveP = Type.paint(18f, Type.BOLD, Palette.TEXT1), noteP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final TextPaint bubbleT = Type.clock(28f, Type.BOLD, Look07.INK), bubbleS = Type.clock(24f, Type.SEMIBOLD, 0xB3111111);
        private final TextPaint titleP = Type.paint(30f, Type.SEMIBOLD, Palette.TEXT1), subP = Type.paint(24f, Type.REGULAR, Palette.TEXT2),
                deltaP = Type.clock(26f, Type.SEMIBOLD, Palette.TEXT1), skipP = Type.paint(15f, Type.BOLD, Palette.TEXT1);
        private final RectF r = new RectF(), oval = new RectF();
        private final Path tip = new Path(), head = new Path();
        private float scrimFor = -1f, stripScrimFor = -1f;
        /** The transport's icons (◀◀ and ▶▶ drawn here, with their seconds). */
        private final int[] icons = {Dark.SKIP_BACK, Dark.REWIND, Dark.PAUSE, Dark.FORWARD, Dark.SKIP_NEXT};

        /** The words, made again only when the spot's second, the picture's (while moving) or the programme changes. */
        private long wordsAt = Long.MIN_VALUE, wordsShown = Long.MIN_VALUE, wordsStart = Long.MIN_VALUE, wordsEnd = Long.MIN_VALUE;
        private boolean wordsLive;
        private long skipFor = -1;
        private String bubbleClock = "", bubbleMore = "", timeWords = "", totalWords = "", deltaWords = "", title = "", sub = "",
                backWords = "", onWords = "";

        Overlay(Context c) {
            super(c);
            card = new InfoCard(this);
            line.setStyle(Paint.Style.STROKE);
            ring.setStyle(Paint.Style.STROKE);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
        }

        private void words(long at, long shown, long[] p, long now) {
            boolean live = target >= 0 && liveEdge(target, now);
            long sec = at / 1000, shownSec = target >= 0 ? shown / 1000 : 0;
            boolean newProgramme = p[0] != wordsStart || p[1] != wordsEnd;
            if (sec == wordsAt && shownSec == wordsShown && !newProgramme && live == wordsLive) return;
            wordsAt = sec;
            wordsShown = shownSec;
            wordsLive = live;
            bubbleClock = LiveText.hm(at);
            bubbleMore = bubbleRest(at - p[0], p[1] - p[0], live);
            timeWords = clock(at - p[0]);
            totalWords = " / " + clock(p[1] - p[0]);
            deltaWords = target >= 0 ? delta(sec * 1000 - shownSec * 1000) : "";
            if (newProgramme) {
                wordsStart = p[0];
                wordsEnd = p[1];
                Guide.Schedule s = Library.get(tv).schedule(ch.key);
                int i = (int) p[2];
                title = s != null && i >= 0 && i < s.title.length && s.title[i] != null ? s.title[i] : ch.name;
                sub = (title.equals(ch.name) ? "" : ch.name + "  ·  ") + Words.span(p[0], p[1]);
            }
        }

        /**
         * The knob larger with a white ring, and over it the bubble: the clock then, bold, and the time into the
         * programme of its length ("21:14 · 34:10 of 1:00:00"), its tip on the knob.
         */
        private void bubble(Canvas c, float share, float y) {
            float x0 = Ui.f(PlayerTiles.SEEK_X0), x1 = Ui.f(PlayerTiles.SEEK_X1);
            float kx = x0 + (x1 - x0) * Math.max(0f, Math.min(1f, share));
            knob.setColor(Look07.L1);
            c.drawCircle(kx, y, Ui.f(14f), knob);
            ring.setColor(Look07.L1);
            ring.setStrokeWidth(Math.max(1f, Ui.f(3f)));
            c.drawCircle(kx, y, Ui.f(22f), ring);
            float cw = bubbleT.measureText(bubbleClock), mw = bubbleS.measureText(bubbleMore);
            float tw = cw + mw + Ui.f(40f);
            float bh = Ui.f(58f), by = y - Ui.f(36f) - bh;
            float bx = Math.max(x0 - Ui.f(20f), Math.min(x1 + Ui.f(20f) - tw, kx - tw / 2f));
            r.set(bx, by, bx + tw, by + bh);
            bubbleP.setColor(Look07.L1);
            c.drawRoundRect(r, Ui.f(16f), Ui.f(16f), bubbleP);
            tip.reset();
            tip.moveTo(kx - Ui.f(11f), by + bh - 1f);
            tip.lineTo(kx + Ui.f(11f), by + bh - 1f);
            tip.lineTo(kx, by + bh + Ui.f(12f));
            tip.close();
            c.drawPath(tip, bubbleP);
            float base = Type.centred(bubbleT, by + bh / 2f), tx = bx + Ui.f(20f);
            c.drawText(bubbleClock, tx, base, bubbleT);
            c.drawText(bubbleMore, tx + cw, base, bubbleS);
        }

        /** Where the picture still is while the spot is moved: a small mark on the line. */
        private void pictureMark(Canvas c, long shown, long[] p, float y) {
            float x0 = Ui.f(PlayerTiles.SEEK_X0), x1 = Ui.f(PlayerTiles.SEEK_X1);
            float share = (shown - p[0]) / (float) Math.max(1L, p[1] - p[0]);
            float x = x0 + (x1 - x0) * Math.max(0f, Math.min(1f, share));
            fill.setColor(0xE6FFFFFF);
            c.drawRoundRect(x - Ui.f(2f), y - Ui.f(11f), x + Ui.f(2f), y + Ui.f(11f), Ui.f(2f), Ui.f(2f), fill);
        }

        /** ◀◀ and ▶▶: a circle arrow round its seconds, anticlockwise back, clockwise on (in the transport's circle). */
        private void skip(Canvas c, float cx, float cy, boolean back, String n, boolean focused) {
            if (focused) {
                Focus.circle(c, cx, cy, Ui.f(RoundButton.TRANSPORT_FOCUS));
            } else {
                fill.setColor(0x14FFFFFF);
                c.drawCircle(cx, cy, Ui.f(RoundButton.TRANSPORT) / 2f, fill);
            }
            int ink = focused ? Palette.TEXT1 : 0xFFB3B0AD;
            float rr = Ui.f(16f);
            arc.setColor(ink);
            arc.setStrokeWidth(Math.max(1f, Ui.f(2.6f)));
            oval.set(cx - rr, cy - rr, cx + rr, cy + rr);
            // The gap at the top on the side the arrow points to, its head on the top pointing the way it goes.
            c.drawArc(oval, back ? -90f : -30f, 300f, false, arc);
            float a = Ui.f(6f), hx = cx, hy = cy - rr, k = back ? -1f : 1f;
            head.reset();
            head.moveTo(hx + k * a, hy);
            head.lineTo(hx - k * a * 0.5f, hy - a * 0.85f);
            head.lineTo(hx - k * a * 0.5f, hy + a * 0.85f);
            head.close();
            fill.setColor(ink);
            c.drawPath(head, fill);
            skipP.setColor(ink);
            c.drawText(n, cx - skipP.measureText(n) / 2f, Type.centred(skipP, cy + Ui.f(1f)), skipP);
        }

        /** The seek bar by itself at the foot: the line, the bubble while moving, the programme under it. */
        private void strip(Canvas c, long at, long shown, long now) {
            int w = getWidth(), hh = getHeight();
            if (stripScrimFor != hh) {
                stripScrim.setShader(new LinearGradient(0, Ui.f(STRIP_SCRIM_Y), 0, hh, 0x00000000, 0xD9000000, Shader.TileMode.CLAMP));
                stripScrimFor = hh;
            }
            c.drawRect(0, Ui.f(STRIP_SCRIM_Y), w, hh, stripScrim);
            long[] p = programme(at);
            words(at, shown, p, now);
            float y = Ui.f(STRIP_Y), x0 = Ui.f(PlayerTiles.SEEK_X0), x1 = Ui.f(PlayerTiles.SEEK_X1);
            float[] sh = seekShares(at);
            Bars.seek(c, x0, x1, y, sh[0], sh[1], false);
            if (target >= 0) pictureMark(c, shown, programme(at), y);
            bubble(c, sh[0], y);
            // Under the line: the archive's mark, the programme, its channel and times; at the right how far it moved.
            float base = Ui.f(STRIP_WORDS_Y);
            Icons07.draw(c, Icons07.CATCHUP, x0 + Ui.f(13f), base - Ui.f(10f), Ui.f(26f), Palette.ACCENT);
            float tx = x0 + Ui.f(40f);
            c.drawText(title, tx, base, titleP);
            c.drawText(sub, tx + titleP.measureText(title) + Ui.f(20f), base, subP);
            int st = tv.live.state();
            String right = target >= 0 ? deltaWords : st == LiveTv.LOADING ? "Loading…" : st == LiveTv.RETRYING ? "Reconnecting…"
                    : tv.live.held() ? "Paused" : "";
            if (!right.isEmpty()) c.drawText(right, x1 - deltaP.measureText(right), base, deltaP);
        }

        @Override protected void onDraw(Canvas c) {
            long now = System.currentTimeMillis(), shown = tv.live.shownAt();
            long at = target >= 0 ? target : shown;
            if (mode == BANNER) {
                boolean fading = peek != null ? card.draw(c, InfoCard.FOOT_Y, peek, now, false, null)
                        : card.draw(c, InfoCard.FOOT_Y, ch, at, true, null);
                // The card's picture cross-fading to another programme's.
                if (fading) postInvalidateOnAnimation();
                return;
            }
            int st = tv.live.state();
            if (mode == STRIP) {
                strip(c, at, shown, now);
                if (!Exec.inline && (target >= 0 || st == LiveTv.PLAYING || st == LiveTv.LOADING)) postInvalidateDelayed(target >= 0 ? 120 : 500);
                return;
            }
            if (mode != OVERLAY && mode != TILES) return;
            if (card.draw(c, InfoCard.TOP_Y, ch, at, true, null)) postInvalidateOnAnimation();
            if (mode == TILES) return;
            int w = getWidth(), hh = getHeight();
            if (scrimFor != hh) {
                scrim.setShader(new LinearGradient(0, Ui.f(PlayerTiles.SCRIM_Y), 0, hh, 0x00000000, 0xE6000000, Shader.TileMode.CLAMP));
                scrimFor = hh;
            }
            c.drawRect(0, Ui.f(PlayerTiles.SCRIM_Y), w, hh, scrim);
            // The seek line, the time in the programme and its length.
            long[] p = programme(at);
            words(at, shown, p, now);
            float[] sh = seekShares(at);
            float y = Ui.f(SEEK_Y);
            Bars.seek(c, Ui.f(PlayerTiles.SEEK_X0), Ui.f(PlayerTiles.SEEK_X1), y, sh[0], sh[1], false);
            if (target >= 0) pictureMark(c, shown, programme(at), y);
            if (focus == SEEK || target >= 0) bubble(c, sh[0], y);
            float base = Ui.f(947f), tx = Ui.f(PlayerTiles.SEEK_X0);
            c.drawText(timeWords, tx, base, timeP);
            float x = tx + timeP.measureText(timeWords);
            c.drawText(totalWords, x, base, totalP);
            x += totalP.measureText(totalWords) + Ui.f(28);
            String note = st == LiveTv.LOADING ? "Loading…" : st == LiveTv.RETRYING ? "Reconnecting…"
                    : st == LiveTv.FAILED ? "Couldn't play this · OK to try again" : "";
            if (!note.isEmpty()) c.drawText(note, x, base - Ui.f(4), noteP);
            // The transport: the programme before, back a Skip step, play/pause, on 30 seconds, the next programme.
            if (skipNow != skipFor) {
                skipFor = skipNow;
                long[] j = jumps(skipNow);
                backWords = String.valueOf(j[0] / 1000);
                onWords = String.valueOf(j[1] / 1000);
            }
            boolean playing = target < 0 && !tv.live.held() && st == LiveTv.PLAYING;
            icons[PLAY] = playing ? Dark.PAUSE : Dark.PLAY;
            float cy = Ui.f(TRANSPORT_Y);
            for (int k = SKIP_BACK; k <= SKIP_NEXT; k++) {
                if (k == REWIND || k == FORWARD) skip(c, Ui.f(ITEM_X[k]), cy, k == REWIND, k == REWIND ? backWords : onWords, k == focus);
                else RoundButton.transport(c, Ui.f(ITEM_X[k]), cy, icons[k], k == focus);
            }
            // At the right, in its pill: from the start again, LIVE, the live dot.
            r.set(Ui.f(1625f), Ui.f(898f), Ui.f(1840f), Ui.f(970f));
            fill.setColor(0x14FFFFFF);
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
            if (focus == RESTART) Focus.circle(c, Ui.f(ITEM_X[RESTART]), cy, Ui.f(64f));
            RoundButton.glyph(c, Dark.RESTART, Ui.f(ITEM_X[RESTART]), cy, Ui.f(34f), focus == RESTART ? Palette.TEXT1 : 0xFFB3B0AD);
            if (focus == LIVE) Focus.circle(c, Ui.f(ITEM_X[LIVE]), cy, Ui.f(64f));
            int liveInk = focus == LIVE ? Palette.TEXT1 : 0xFFB3B0AD;
            liveP.setColor(liveInk);
            float lw = liveP.measureText("LIVE") + Ui.f(14);
            r.set(Ui.f(ITEM_X[LIVE]) - lw / 2f, cy - Ui.f(13), Ui.f(ITEM_X[LIVE]) + lw / 2f, cy + Ui.f(13));
            line.setColor(liveInk);
            line.setStrokeWidth(Math.max(1f, Ui.f(2f)));
            c.drawRoundRect(r, Ui.f(4f), Ui.f(4f), line);
            c.drawText("LIVE", r.centerX() - liveP.measureText("LIVE") / 2f, Type.centred(liveP, cy), liveP);
            fill.setColor(tv.live.behind() ? 0xFFB3B0AD : Palette.ACCENT);
            c.drawCircle(Ui.f(1804f), cy, Ui.f(11f), fill);
            if (!Exec.inline && (target >= 0 || playing || st == LiveTv.LOADING)) postInvalidateDelayed(target >= 0 ? 120 : 500);
        }
    }
}
