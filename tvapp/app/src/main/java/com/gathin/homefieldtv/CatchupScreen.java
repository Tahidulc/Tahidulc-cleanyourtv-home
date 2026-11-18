package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
 * OK brings up the overlay: the card at the top; across the foot the seek line (80 to 1839 at y 861–866: the accent up
 * to where the picture is, light grey on to what has been on so far, dark grey after, the white knob); "00:05 / 55:00"
 * at the left; the transport in the middle (|◀ ◀◀ ⏸ ▶▶ ▶|, 72 px circles of white at 8 %, the remote's an 88 px focus
 * circle); at the right, in a pill, ⟲ (from the start again), LIVE and the live dot; the row of tiles peeking in under
 * it. Down brings the tiles up (TV guide, History, the channels last watched) in place of the transport; Up goes back.
 *
 * <p>Keys (TiviMate's "Use RW/FF/Pause for seeking/pause while watching catch-up"; Homefield's Left and Right too):
 * Left and Right, rewind and fast forward move 10 seconds a press, more the longer they are held (30 seconds, a minute,
 * three minutes), the archive asked for from there a moment after the remote rests; play/pause pauses; ◀◀ and ▶▶ in
 * the overlay the same, |◀ the programme's start (or the one before), ▶| the next one (or live), ⟲ from the start,
 * LIVE back to live. Up and Down change channel, live; CH+ and CH− show the next or previous channel's card (OK goes to
 * it); OK held or MENU the player's bar. Back closes the overlay, then goes to the TV guide with the programme still in
 * its picture; Back held stops the picture. When a programme ends it goes on into the next; at the live edge it is live
 * again (the live screen under this one).
 */
final class CatchupScreen extends Screen implements LiveTv.Listener {
    static final long PANELS_MS = 10_000L, APPLY_MS = 700, HALF = 30 * 60_000L, HOUR = 3600_000L;
    static final int PLAIN = 0, BANNER = 1, OVERLAY = 2, TILES = 3, BAR = 4;
    /** The transport and the right pill: their items, and where their middles are (design pixels). */
    static final int SKIP_BACK = 0, REWIND = 1, PLAY = 2, FORWARD = 3, SKIP_NEXT = 4, RESTART = 5, LIVE = 6;
    static final float[] ITEM_X = {760f, 856f, 960f, 1064f, 1160f, 1660f, 1732f};
    static final float SEEK_Y = 863.5f, TRANSPORT_Y = 934f;

    private final Channel ch;
    /** Where it starts (a programme's start) and that programme's end; -1 once asked for. */
    private long startAt, startEnd;
    /** Where the remote has moved the line to and the picture hasn't gone yet (-1: not moving), which way, how many steps. */
    private long target = -1;
    private int dir, steps;
    private int mode = PLAIN, focus = PLAY;
    /** The card's channel on CH+ and CH− (not the one playing); null: this one. */
    private Channel peek;
    private Overlay overlay;
    private PlayerTiles tiles;
    private PlayerBar bar;
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
        bar.close();
    }

    @Override public void liveChanged() {
        if (startAt < 0 && !tv.live.behind()) {
            // Live again by itself (the archive ran into now): the live screen under this one.
            h.removeCallbacks(leave);
            h.post(leave);
            return;
        }
        if (tv.live.state() == LiveTv.FAILED && mode != OVERLAY) setMode(OVERLAY);
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
        if (m == OVERLAY || m == TILES) {
            tiles.set(Library.get(tv).recent(null), PlayerTiles.GUIDE);
            // In the overlay the tiles only peek in under the transport; Down brings them up.
            tiles.peek = m == OVERLAY;
            tiles.seekLine = m == TILES;
            tiles.setTranslationY(m == OVERLAY ? Ui.f(1022f - PlayerTiles.TILE_Y) : 0f);
        }
        Fade.show(tiles, m == OVERLAY || m == TILES, 110);
        Fade.show(overlay, m != PLAIN && m != BAR, 130);
        if (m != PLAIN) h.postDelayed(hide, PANELS_MS);
        float above = m == BANNER ? InfoCard.FOOT_Y - 16f : m == OVERLAY ? SEEK_Y - 40f : m == TILES || m == BAR ? PlayerTiles.SCRIM_Y + 40f : -1f;
        tv.live.subs.room(true, above);
        overlay.invalidate();
    }

    private void poke() {
        h.removeCallbacks(hide);
        if (mode != PLAIN) h.postDelayed(hide, PANELS_MS);
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean holds(int code) {
        if (mode != PLAIN && mode != BANNER) return false;
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
        switch (code) {
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                bar.open();
                setMode(BAR);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mode == OVERLAY) {
                    // Along the transport and the pill.
                    int n = focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    if (n >= SKIP_BACK && n <= LIVE) focus = n;
                    poke();
                    overlay.invalidate();
                    return true;
                }
                move(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                move(-1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                move(1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (e.getRepeatCount() == 0) playPause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                tv.live.hold(false);
                setMode(OVERLAY);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                tv.live.hold(true);
                setMode(OVERLAY);
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (e.getRepeatCount() == 0) tv.pop();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mode == OVERLAY) {
                    setMode(TILES);
                    return true;
                }
                if (e.getRepeatCount() == 0) channel(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (mode == OVERLAY) {
                    poke();
                    return true;
                }
                if (e.getRepeatCount() == 0) channel(-1);
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
        if (target >= 0) {
            applyMove();
            return;
        }
        if (mode == BANNER && peek != null) {
            Screen under = tv.below(this);
            Channel to = peek;
            tv.pop();
            if (under instanceof LiveScreen) ((LiveScreen) under).tuneFromList(to);
            return;
        }
        if (mode != OVERLAY) {
            focus = PLAY;
            setMode(OVERLAY);
            return;
        }
        poke();
        switch (focus) {
            case SKIP_BACK: {
                long at = tv.live.shownAt();
                long[] p = programme(at);
                // To the programme's start; at its start already, the one before.
                long to = at - p[0] > 10_000L ? p[0] : programme(p[0] - 1)[0];
                go(to);
                break;
            }
            case REWIND:
                move(-1);
                break;
            case FORWARD:
                move(1);
                break;
            case SKIP_NEXT: {
                long next = programme(tv.live.shownAt())[1];
                if (next >= System.currentTimeMillis() - LiveTv.LIVE_EDGE_MS) tv.pop();
                else go(next);
                break;
            }
            case RESTART:
                go(programme(tv.live.shownAt())[0]);
                break;
            case LIVE:
                tv.pop();
                break;
            default:
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

    /** The picture to time t now (live at the live edge). */
    private void go(long t) {
        h.removeCallbacks(apply);
        target = -1;
        steps = 0;
        long earliest = tv.live.earliest(ch);
        if (earliest != Long.MAX_VALUE && t < earliest) t = earliest;
        if (t >= System.currentTimeMillis() - LiveTv.LIVE_EDGE_MS) {
            tv.pop();
            return;
        }
        tv.live.showAt(ch, t, programme(t)[1], 0);
        overlay.invalidate();
    }

    /** The line moved: 10 seconds a press, then 30, a minute and three minutes as the key stays down. */
    private void move(int d) {
        long now = System.currentTimeMillis();
        long from = target >= 0 ? target : tv.live.shownAt();
        if (target < 0 || d != dir) steps = 0;
        steps++;
        long step = steps <= 5 ? 10_000L : steps <= 15 ? 30_000L : steps <= 30 ? 60_000L : 180_000L;
        long to = from + d * step;
        long earliest = tv.live.earliest(ch);
        if (earliest != Long.MAX_VALUE && to < earliest) to = earliest;
        if (to > now) to = now;
        target = to;
        dir = d;
        h.removeCallbacks(apply);
        if (!Exec.inline) h.postDelayed(apply, APPLY_MS);
        if (mode != OVERLAY && mode != TILES) {
            focus = d < 0 ? REWIND : FORWARD;
            setMode(OVERLAY);
        } else {
            poke();
        }
        overlay.invalidate();
    }

    /** The picture to where the line was moved: asked for from there (live at the live edge). */
    private void applyMove() {
        h.removeCallbacks(apply);
        if (target < 0) return;
        long t = target;
        int d = dir;
        target = -1;
        steps = 0;
        if (t >= System.currentTimeMillis() - LiveTv.LIVE_EDGE_MS) {
            tv.pop();
            return;
        }
        tv.live.showAt(ch, t, programme(t)[1], d);
        overlay.invalidate();
    }

    /** The programme on the channel at t: {start, end, its place in the guide (-1: none; then the hour round t)}. */
    private long[] programme(long t) {
        Guide.Schedule s = Library.get(tv).schedule(ch.key);
        int i = s == null ? -1 : s.at(t);
        if (i >= 0) return new long[]{s.start[i], s.stop[i], i};
        long a = t - Math.floorMod(t, HALF);
        return new long[]{a, a + HOUR, -1};
    }

    /** Previews: the line as the remote moves it to t. */
    void previewMove(long t) {
        target = t;
        dir = -1;
        focus = REWIND;
        setMode(OVERLAY);
    }

    /** Previews: the overlay with the remote on an item, or the tiles brought up (TILES). */
    void previewMode(int m, int at) {
        focus = at;
        setMode(m);
    }

    /** Pure: a time in a programme as the overlay writes it: "00:05", "55:00", "1:02:03". */
    static String clock(long ms) {
        long s = Math.max(0L, ms / 1000);
        long hh = s / 3600, m = s / 60 % 60, sec = s % 60;
        String mm = (m < 10 ? "0" : "") + m, ss = (sec < 10 ? "0" : "") + sec;
        return hh > 0 ? hh + ":" + mm + ":" + ss : mm + ":" + ss;
    }

    // ---- the bar's and the tiles' hosts -------------------------------------------------------------------------------

    private final PlayerBar.Host barHost = new PlayerBar.Host() {
        @Override public Channel channel() {
            return ch;
        }

        @Override public void act(int item) {
            switch (item) {
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
                    go(programme(tv.live.shownAt())[0]);
                    break;
                default:
                    break;
            }
        }

        @Override public boolean hasBoard() {
            return false;
        }

        @Override public boolean canStartOver() {
            return true;
        }

        @Override public boolean barUp() {
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
            return seekShares();
        }
    };

    /** The seek line's shares of the programme: {where the picture is, what has been on so far, 0 (not live)}. */
    private float[] seekShares() {
        long now = System.currentTimeMillis();
        long at = target >= 0 ? target : tv.live.shownAt();
        long[] p = programme(at);
        float len = Math.max(1L, p[1] - p[0]);
        return new float[]{(at - p[0]) / len, (Math.min(now, p[1]) - p[0]) / len, 0f};
    }

    // ---- the overlay -------------------------------------------------------------------------------------------------

    private final class Overlay extends View {
        private final InfoCard card;
        private final Paint scrim = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint timeP = Type.paint(32f, Type.REGULAR, Palette.TEXT1), totalP = Type.paint(32f, Type.REGULAR, Palette.TEXT2);
        private final TextPaint liveP = Type.paint(18f, Type.BOLD, Palette.TEXT1), noteP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT2);
        private final RectF r = new RectF();
        private float scrimFor = -1f;

        Overlay(Context c) {
            super(c);
            card = new InfoCard(this);
            line.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas c) {
            long at = target >= 0 ? target : tv.live.shownAt();
            if (mode == BANNER) {
                boolean fading = peek != null ? card.draw(c, InfoCard.FOOT_Y, peek, System.currentTimeMillis(), false, null)
                        : card.draw(c, InfoCard.FOOT_Y, ch, at, true, null);
                // The card's picture cross-fading to another programme's.
                if (fading) postInvalidateOnAnimation();
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
            float[] sh = seekShares();
            Bars.seek(c, Ui.f(PlayerTiles.SEEK_X0), Ui.f(PlayerTiles.SEEK_X1), Ui.f(SEEK_Y), sh[0], sh[1], false);
            long[] p = programme(at);
            String t = clock(at - p[0]), total = " / " + clock(p[1] - p[0]);
            float base = Ui.f(947f), tx = Ui.f(PlayerTiles.SEEK_X0);
            timeP.setColor(Palette.TEXT1);
            totalP.setColor(Palette.TEXT2);
            c.drawText(t, tx, base, timeP);
            float x = tx + timeP.measureText(t);
            c.drawText(total, x, base, totalP);
            x += totalP.measureText(total) + Ui.f(28);
            int st = tv.live.state();
            String note = st == LiveTv.LOADING ? "Loading…" : st == LiveTv.RETRYING ? "Reconnecting…"
                    : st == LiveTv.FAILED ? "Couldn't play this · OK to try again" : "";
            if (!note.isEmpty()) {
                noteP.setColor(Palette.TEXT2);
                c.drawText(note, x, base - Ui.f(4), noteP);
            }
            // The transport.
            boolean playing = target < 0 && !tv.live.held() && st == LiveTv.PLAYING;
            int[] icons = {Dark.SKIP_BACK, Dark.REWIND, playing ? Dark.PAUSE : Dark.PLAY, Dark.FORWARD, Dark.SKIP_NEXT};
            for (int k = SKIP_BACK; k <= SKIP_NEXT; k++) {
                RoundButton.transport(c, Ui.f(ITEM_X[k]), Ui.f(TRANSPORT_Y), icons[k], k == focus);
            }
            // At the right, in its pill: from the start again, LIVE, the live dot.
            r.set(Ui.f(1625f), Ui.f(898f), Ui.f(1840f), Ui.f(970f));
            fill.setColor(0x14FFFFFF);
            c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fill);
            float cy = Ui.f(TRANSPORT_Y);
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
