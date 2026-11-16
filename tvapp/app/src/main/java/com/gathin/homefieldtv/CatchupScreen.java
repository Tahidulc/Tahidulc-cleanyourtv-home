package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
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
 * Catch-up over the picture: a programme from the provider's archive, or the channel moved back in its own stream.
 * Along the foot, over a dark fade, the channel's logo, the programme's title, its day and times, the channel, and
 * "Catch-up" (a programme that has ended) or how far behind live it is; then a bar across the programme from its start
 * to its end with the part shown filled, the live edge marked where the programme is on now (the part after it not
 * there yet) and the knob where the picture is, play or pause in it; the time over the knob in a bubble while the
 * remote moves it; what's next and the keys under it.
 *
 * Left and Right move 10 seconds a press, more the longer they are held (30 seconds, a minute, three minutes), and the
 * archive is asked for from there a moment after the remote rests; OK plays or pauses (or goes there at once while
 * moving); Up goes live, as does Back. When a programme ends it goes on into the next; at the live edge it is live
 * again. Down shows or hides the bar, which fades after a few seconds while playing.
 */
final class CatchupScreen extends Screen implements LiveTv.Listener {
    private static final long BAR_MS = 5000, APPLY_MS = 700, HALF = 30 * 60_000L, HOUR = 3600_000L;

    private final Channel ch;
    /** Where it starts (a programme's start) and that programme's end; -1 once asked for. */
    private long startAt, startEnd;
    /** Where the remote has moved the bar to and the picture hasn't gone yet (-1: not moving), which way, how many steps. */
    private long target = -1;
    private int dir, steps;
    private Bar bar;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable apply = this::applyMove;
    private final Runnable hideBar = () -> {
        if (!tv.live.held() && target < 0 && tv.live.state() != LiveTv.FAILED) Fade.show(bar, false, 220);
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
        bar = new Bar(tv);
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
        } else if (tv.live.archived()) {
            // Back to the app: the archive asked for again from where it got to.
            tv.live.resumeBehind();
        } else if (!tv.live.behind()) {
            // The stream's own window went with the stream: live.
            h.post(leave);
            return;
        }
        showBar();
    }

    @Override void hidden() {
        tv.live.removeListener(this);
        h.removeCallbacks(apply);
        h.removeCallbacks(hideBar);
        h.removeCallbacks(leave);
    }

    @Override public void liveChanged() {
        if (startAt < 0 && !tv.live.behind()) {
            // Live again by itself (the archive ran into now): the live screen under this one.
            h.removeCallbacks(leave);
            h.post(leave);
            return;
        }
        if (tv.live.state() == LiveTv.FAILED) showBar();
        bar.invalidate();
    }

    @Override void tick() {
        bar.invalidate();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                move(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                move(1);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (e.getRepeatCount() == 0) ok();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                tv.live.hold(false);
                showBar();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                tv.live.hold(true);
                showBar();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                // Live: the live screen under this one plays the channel live again.
                if (e.getRepeatCount() == 0) tv.pop();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
                if (bar.getVisibility() == View.VISIBLE && !tv.live.held() && target < 0) {
                    h.removeCallbacks(hideBar);
                    Fade.show(bar, false, 220);
                } else {
                    showBar();
                }
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN: {
                // Another channel, live.
                Screen under = tv.below(this);
                if (under instanceof LiveScreen) ((LiveScreen) under).next(code == KeyEvent.KEYCODE_CHANNEL_UP ? -1 : 1);
                tv.pop();
                return true;
            }
            default:
                // The number keys change channel from live TV, not here.
                return code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9;
        }
    }

    @Override void ok() {
        if (target >= 0) {
            applyMove();
            return;
        }
        if (tv.live.state() == LiveTv.FAILED) {
            tv.live.tryAgain();
            showBar();
            return;
        }
        tv.live.hold(!tv.live.held());
        showBar();
    }

    /** The bar moved: 10 seconds a press, then 30, a minute and three minutes as the key stays down. */
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
        showBar();
    }

    /** The picture to where the bar was moved: asked for from there (live at the live edge). */
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
        showBar();
    }

    private void showBar() {
        h.removeCallbacks(hideBar);
        Fade.show(bar, true, 160);
        bar.invalidate();
        if (!Exec.inline) h.postDelayed(hideBar, BAR_MS);
    }

    /** The programme on the channel at t: {start, end, its place in the guide (-1: none; then the hour round t)}. */
    private long[] programme(long t) {
        Guide.Schedule s = Library.get(tv).schedule(ch.key);
        int i = s == null ? -1 : s.at(t);
        if (i >= 0) return new long[]{s.start[i], s.stop[i], i};
        long a = t - Math.floorMod(t, HALF);
        return new long[]{a, a + HOUR, -1};
    }

    /** Previews: the bar as the remote moves it to t. */
    void previewMove(long t) {
        target = t;
        dir = -1;
        showBar();
    }

    // ---- the bar ------------------------------------------------------------------------------------------------

    private final class Bar extends View {
        private final Paint scrim = new Paint(), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint gone = Dark.fill(0x1FFFFFFF), track = Dark.fill(0x52FFFFFF), done = Dark.fill(0xF2FFFFFF);
        private final Paint accent = Dark.fill(Dark.ACCENT), ink = Dark.fill(Dark.ACCENT_INK), knobEdge = Dark.line(0xB3FFFFFF, 2f);
        private final Paint pillLine = Dark.line(0x99FFFFFF, 1.5f), keyBack = Dark.fill(0x2EFFFFFF), chev = Dark.line(Dark.TEXT, 2.5f);
        private final TextPaint titleP = Dark.text(54, 600, Dark.TEXT), lineP = Dark.text(28, 500, Dark.SOFT);
        private final TextPaint numP = Dark.text(28, 400, Dark.MUTED), nameP = Dark.text(28, 700, Dark.TEXT);
        private final TextPaint pillInk = Dark.text(22, 700, Dark.ACCENT_INK), pillP = Dark.text(22, 600, Dark.TEXT);
        private final TextPaint endP = Dark.text(26, 500, Dark.SOFT), liveP = Dark.text(18, 700, Dark.ACCENT);
        private final TextPaint atP = Dark.text(24, 600, Dark.TEXT), bubbleP = Dark.text(28, 700, Dark.ACCENT_INK);
        private final TextPaint nextLabel = Dark.text(24, 700, Dark.MUTED), nextP = Dark.text(24, 500, Dark.SOFT);
        private final TextPaint keyP = Dark.text(22, 600, Dark.SOFT), capP = Dark.text(18, 700, Dark.TEXT);
        private final TextPaint logoName = Dark.text(36, 700, Dark.TEXT), noteP = Dark.text(24, 500, Dark.MUTED);
        private final RectF r = new RectF();
        private final Path tri = new Path();
        private float scrimFor = -1f;
        private final Runnable redraw = this::invalidate;

        Bar(Context c) {
            super(c);
            liveP.setLetterSpacing(0.08f);
        }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), hh = getHeight();
            long now = System.currentTimeMillis();
            boolean moving = target >= 0;
            long at = moving ? target : tv.live.shownAt();
            Library lib = Library.get(tv);
            Guide.Schedule s = lib.schedule(ch.key);
            long[] p = programme(at);
            long ps = p[0], pe = p[1];
            int i = (int) p[2];

            if (scrimFor != hh) {
                scrim.setShader(new LinearGradient(0, hh * 0.5f, 0, hh, new int[]{0x00000000, 0xB8000000, 0xF2000000},
                        new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
                scrimFor = hh;
            }
            c.drawRect(0, hh * 0.5f, w, hh, scrim);

            // The channel's logo, the programme's title.
            float lx = Ui.f(96), ly = Ui.f(684), lw = Ui.f(220), lh = Ui.f(110);
            Bitmap logo = Pics.get(ch.logo, Ui.px(220), Ui.px(110), Pics.tiles ? Pics.READY : Pics.FIT, redraw);
            if (logo != null) c.drawBitmap(logo, lx, ly + (lh - logo.getHeight()) / 2f, bmp);
            else c.drawText(Ui.fit(ch.name, logoName, lw), lx, ly + lh / 2f + Ui.f(12), logoName);
            float x0 = Ui.f(360), right = w - Ui.f(96);
            String title = i >= 0 ? s.title[i] : ch.name;
            c.drawText(Ui.fit(title, titleP, right - x0), x0, Ui.f(736), titleP);

            // Its day and times, the channel, and Catch-up or how far behind live.
            float y = Ui.f(788), x = x0;
            String day = Ui.day(ps, now);
            String when = i < 0 ? "No programme information" : ("Today".equals(day) ? "" : day + ", ") + LiveText.span(ps, pe);
            c.drawText(when, x, y, lineP);
            x += lineP.measureText(when) + Ui.f(28);
            String num = String.valueOf(lib.number(ch));
            c.drawText(num, x, y, numP);
            x += numP.measureText(num) + Ui.f(10);
            String name = Ui.fit(ch.name, nameP, Math.max(Ui.f(120), right - x - Ui.f(420)));
            c.drawText(name, x, y, nameP);
            x += nameP.measureText(name) + Ui.f(26);
            boolean over = pe <= now;
            x = pill(c, over ? "Catch-up" : LiveText.behind(now - at), x, y, over);
            int st = tv.live.state();
            String note = st == LiveTv.LOADING ? "Loading…" : st == LiveTv.RETRYING ? "Reconnecting…"
                    : st == LiveTv.FAILED ? "OK to try again" : tv.live.held() ? "Paused" : "";
            if (!note.isEmpty()) c.drawText(note, x + Ui.f(4), y, noteP);

            // The bar across the programme: what isn't there faint, the part shown filled, the live edge, the knob.
            float bx0 = Ui.f(236), bx1 = w - Ui.f(236), by = Ui.f(916), half = Ui.f(3);
            String a = LiveText.hm(ps), b = LiveText.hm(pe);
            c.drawText(a, bx0 - Ui.f(24) - endP.measureText(a), by + Ui.f(9), endP);
            c.drawText(b, bx1 + Ui.f(24), by + Ui.f(9), endP);
            float kx = xAt(at, ps, pe, bx0, bx1), liveX = now < pe ? xAt(now, ps, pe, bx0, bx1) : bx1;
            long earliest = tv.live.earliest(ch);
            float ex = earliest == Long.MAX_VALUE ? bx0 : xAt(earliest, ps, pe, bx0, bx1);
            c.drawRect(bx0, by - half, bx1, by + half, gone);
            if (liveX > ex) c.drawRect(ex, by - half, liveX, by + half, track);
            if (kx > bx0) c.drawRect(bx0, by - half, kx, by + half, done);
            boolean onNow = ps <= now && now < pe;
            if (onNow) {
                c.drawRect(liveX - Ui.f(2), by - Ui.f(15), liveX + Ui.f(2), by + Ui.f(15), accent);
                if (!moving || Math.abs(kx - liveX) > Ui.f(120)) c.drawText("LIVE", liveX - liveP.measureText("LIVE") / 2f, by - Ui.f(26), liveP);
            }
            c.drawCircle(kx, by, Ui.f(22), accent);
            c.drawCircle(kx, by, Ui.f(22), knobEdge);
            boolean playing = !moving && !tv.live.held() && st == LiveTv.PLAYING;
            if (playing) {
                c.drawRect(kx - Ui.f(7), by - Ui.f(9), kx - Ui.f(2), by + Ui.f(9), ink);
                c.drawRect(kx + Ui.f(2), by - Ui.f(9), kx + Ui.f(7), by + Ui.f(9), ink);
            } else {
                tri.reset();
                tri.moveTo(kx - Ui.f(5), by - Ui.f(10));
                tri.lineTo(kx + Ui.f(10), by);
                tri.lineTo(kx - Ui.f(5), by + Ui.f(10));
                tri.close();
                c.drawPath(tri, ink);
            }
            // The time of the picture: in a bubble over the knob while the remote moves it, under it otherwise.
            String t = LiveText.hms(at);
            if (moving) {
                float tw = bubbleP.measureText(t) + Ui.f(36), th = Ui.f(52);
                float left = Math.max(Ui.f(40), Math.min(kx - tw / 2f, w - Ui.f(40) - tw));
                r.set(left, by - Ui.f(90), left + tw, by - Ui.f(90) + th);
                c.drawRoundRect(r, Ui.f(14), Ui.f(14), accent);
                tri.reset();
                tri.moveTo(kx - Ui.f(11), r.bottom - Ui.f(1));
                tri.lineTo(kx + Ui.f(11), r.bottom - Ui.f(1));
                tri.lineTo(kx, r.bottom + Ui.f(11));
                tri.close();
                c.drawPath(tri, accent);
                c.drawText(t, r.left + Ui.f(18), r.bottom - Ui.f(16), bubbleP);
            } else {
                c.drawText(t, kx - atP.measureText(t) / 2f, by + Ui.f(56), atP);
            }

            // What's next, and the keys.
            float hy = Ui.f(1016);
            int n = s == null ? -1 : i >= 0 ? (i + 1 < s.start.length ? i + 1 : -1) : s.after(at);
            if (n >= 0) {
                c.drawText("Next", Ui.f(96), hy, nextLabel);
                float nx = Ui.f(96) + nextLabel.measureText("Next") + Ui.f(14);
                String sp = LiveText.hm(s.start[n]);
                c.drawText(sp, nx, hy, nextP);
                nx += nextP.measureText(sp) + Ui.f(14);
                c.drawText(Ui.fit(s.title[n], nextP, Ui.f(720)), nx, hy, nextP);
            }
            float kr = right;
            kr = key(c, kr, hy, "Live", Ui.UP, null);
            kr = key(c, kr, hy, tv.live.held() || moving ? "Play" : "Pause", -1, "OK");
            key(c, kr, hy, "Move", Ui.LEFT, null);
            if (!Exec.inline && (moving || playing || st == LiveTv.LOADING)) postInvalidateDelayed(moving ? 120 : 500);
        }

        private float xAt(long t, long ps, long pe, float x0, float x1) {
            float f = pe > ps ? (t - ps) / (float) (pe - ps) : 0f;
            return x0 + (x1 - x0) * Math.max(0f, Math.min(1f, f));
        }

        /** "Catch-up" in amber, how far behind live outlined; returns where the line goes on. */
        private float pill(Canvas c, String text, float x, float base, boolean filled) {
            TextPaint p = filled ? pillInk : pillP;
            float pw = p.measureText(text) + Ui.f(28), ph = Ui.f(36);
            r.set(x, base - Ui.f(27), x + pw, base - Ui.f(27) + ph);
            if (filled) c.drawRoundRect(r, ph / 2f, ph / 2f, accent);
            else c.drawRoundRect(r, ph / 2f, ph / 2f, pillLine);
            c.drawText(text, x + Ui.f(14), base - Ui.f(2), p);
            return x + pw + Ui.f(16);
        }

        /** A key and what it does, drawn leftwards from right: its cap (a chevron or a word) and the word after it. */
        private float key(Canvas c, float right, float base, String what, int arrow, String word) {
            float ww = keyP.measureText(what);
            c.drawText(what, right - ww, base, keyP);
            float capW = word != null ? capP.measureText(word) + Ui.f(20) : arrow == Ui.LEFT ? Ui.f(62) : Ui.f(40);
            float cx1 = right - ww - Ui.f(10), cx0 = cx1 - capW, top = base - Ui.f(27);
            r.set(cx0, top, cx1, top + Ui.f(34));
            c.drawRoundRect(r, Ui.f(8), Ui.f(8), keyBack);
            float mid = top + Ui.f(17);
            if (word != null) {
                c.drawText(word, cx0 + Ui.f(10), base - Ui.f(4), capP);
            } else if (arrow == Ui.LEFT) {
                Ui.chevron(c, cx0 + capW * 0.32f, mid, Ui.f(8), Ui.LEFT, chev);
                Ui.chevron(c, cx0 + capW * 0.68f, mid, Ui.f(8), Ui.RIGHT, chev);
            } else {
                Ui.chevron(c, (cx0 + cx1) / 2f, mid, Ui.f(8), arrow, chev);
            }
            return cx0 - Ui.f(30);
        }
    }
}
