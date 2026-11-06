package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Guide;
import com.gathin.homefieldtv.engine.Library;

import java.util.List;

/**
 * A channel full screen. OK, or a change of channel, brings up the banner along the foot: the channel's name and
 * number between Up and Down chevrons, the day and time, and what's on now and next. Up and Down in the banner look
 * through the other channels without leaving this one; OK watches the one shown. Left goes to the menu (Home), Back
 * hides the banner.
 */
final class LiveScreen extends Screen implements LiveTv.Listener {
    private static final long SHOW_MS = 6000;

    private Channel start;
    /** The channel in the banner (another one while looking through them). */
    private Channel showing;
    private boolean banner = true;
    /** 0 the programme on now, 1 the next. */
    private int card;
    private String digits = "";
    private Banner view;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable hide = () -> {
        banner = false;
        showing = null;
        card = 0;
        if (view != null) view.invalidate();
    };
    private final Runnable digitsDone = this::tuneDigits;

    LiveScreen(TvActivity tv, Channel c) {
        super(tv);
        this.start = c;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override View build() {
        view = new Banner(tv);
        return view;
    }

    @Override void shown() {
        tv.live.addListener(this);
        tv.live.place(tv.fullScreen());
        if (start != null) {
            tv.live.play(start);
            start = null;
        } else if (tv.live.channel() != null) {
            tv.live.play(tv.live.channel());
        }
        showBanner();
    }

    @Override void hidden() {
        tv.live.removeListener(this);
        h.removeCallbacks(hide);
        h.removeCallbacks(digitsDone);
    }

    @Override public void liveChanged() {
        view.invalidate();
    }

    @Override void tick() {
        view.invalidate();
    }

    @Override void channelsChanged() {
        view.invalidate();
    }

    private void showBanner() {
        banner = true;
        h.removeCallbacks(hide);
        h.postDelayed(hide, SHOW_MS);
        view.invalidate();
    }

    private Channel inBanner() {
        return showing != null ? showing : tv.live.channel();
    }

    private Channel step(Channel from, int dir) {
        Library lib = Library.get(tv);
        List<Channel> all = lib.all();
        if (all.isEmpty() || from == null) return from;
        int i = lib.position(from.key);
        if (i < 0) i = 0;
        return all.get(((i + dir) % all.size() + all.size()) % all.size());
    }

    private void tune(Channel c) {
        if (c == null) return;
        showing = null;
        card = 0;
        tv.live.play(c);
        showBanner();
    }

    private void tuneDigits() {
        h.removeCallbacks(digitsDone);
        String d = digits;
        digits = "";
        if (d.isEmpty()) return;
        Library lib = Library.get(tv);
        int n;
        try {
            n = Integer.parseInt(d);
        } catch (NumberFormatException e) {
            return;
        }
        for (Channel c : lib.all()) {
            if (lib.number(c) == n) {
                tune(c);
                return;
            }
        }
        view.invalidate();
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digits += (char) ('0' + code - KeyEvent.KEYCODE_0);
            h.removeCallbacks(digitsDone);
            if (digits.length() >= 4) tuneDigits();
            else h.postDelayed(digitsDone, 1600);
            showBanner();
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
                tune(step(tv.live.channel(), -1));
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                tune(step(tv.live.channel(), 1));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                // In the banner: through the other channels' now and next, this one still playing.
                showing = step(inBanner(), code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                card = 0;
                showBanner();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!banner) {
                    showBanner();
                    return true;
                }
                card = 1;
                showBanner();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (banner && card == 1) {
                    card = 0;
                    showBanner();
                    return true;
                }
                // "‹ Menu".
                tv.pop();
                return true;
            case KeyEvent.KEYCODE_INFO:
                if (banner) hide.run();
                else showBanner();
                return true;
            default:
                return false;
        }
    }

    @Override void ok() {
        if (!banner) {
            showBanner();
            return;
        }
        if (showing != null && (tv.live.channel() == null || !showing.key.equals(tv.live.channel().key))) {
            tune(showing);
            return;
        }
        if (tv.live.state() == LiveTv.FAILED) {
            tv.live.tryAgain();
            showBanner();
            return;
        }
        hide.run();
    }

    @Override void okLong() {
        Channel c = inBanner();
        if (c == null) return;
        boolean now = Library.get(tv).toggleFavourite(c);
        android.widget.Toast.makeText(tv, now ? c.name + " added to your favourites" : c.name + " taken off your favourites",
                android.widget.Toast.LENGTH_SHORT).show();
        showBanner();
    }

    @Override boolean back() {
        if (banner) {
            hide.run();
            return true;
        }
        return false;
    }

    // ---- the banner ---------------------------------------------------------------------------------------------

    private final class Banner extends View {
        private final Paint band = new Paint(), line = new Paint(), cardBack = Ui.fill(0x1AFFFFFF), thumb = new Paint();
        private final Paint chev = Ui.stroke(Ui.WHITE, 3f), chevDim = Ui.stroke(Ui.DIM, 3f), bar = Ui.fill(Ui.CYAN);
        private final Paint track = Ui.fill(0x55FFFFFF), bmp = new Paint(Paint.FILTER_BITMAP_FLAG), digitBack = Ui.fill(0xCC062A66);
        private final TextPaint menuP = Ui.paint(28, false, Ui.DIM), nameP = Ui.paint(34, false, Ui.WHITE);
        private final TextPaint dayP = Ui.paint(26, false, Ui.DIM), labelP = Ui.paint(24, true, Ui.CYAN), labelDim = Ui.paint(24, true, Ui.DIM);
        private final TextPaint timeP = Ui.paint(24, false, Ui.DIM), titleP = Ui.paint(38, false, Ui.WHITE);
        private final TextPaint descP = Ui.paint(24, false, Ui.DIM), hintP = Ui.paint(26, false, Ui.WHITE), digitP = Ui.paint(64, true, Ui.WHITE);
        private final TextPaint thumbName = Ui.paint(26, true, Ui.WHITE);
        private final Glow glow = new Glow();
        private final RectF r = new RectF();
        private float bandFor = -1, thumbFor = -1;
        private final Runnable redraw = this::invalidate;

        Banner(Context c) {
            super(c);
            line.setColor(0x80CFE3F5);
        }

        @Override protected void onDraw(Canvas c) {
            if (!digits.isEmpty()) {
                r.set(Ui.f(1580), Ui.f(60), Ui.f(1860), Ui.f(170));
                c.drawRoundRect(r, Ui.f(6), Ui.f(6), digitBack);
                StringBuilder b = new StringBuilder(digits);
                while (b.length() < 3) b.append('-');
                String d = b.toString();
                c.drawText(d, r.centerX() - digitP.measureText(d) / 2f, r.centerY() + Ui.f(22), digitP);
            }
            if (!banner) return;
            Channel ch = inBanner();
            if (ch == null) return;
            int h = getHeight(), w = getWidth();
            float top = Ui.f(752);
            if (bandFor != top) {
                band.setShader(new LinearGradient(0, top - Ui.f(60), 0, h, new int[]{0x00000000, 0xB3000814, 0xD9000814},
                        new float[]{0f, 0.2f, 1f}, Shader.TileMode.CLAMP));
                bandFor = top;
            }
            c.drawRect(0, top - Ui.f(60), w, h, band);
            c.drawRect(0, top, w, top + Math.max(1f, Ui.f(2)), line);

            Library lib = Library.get(tv);
            long now = System.currentTimeMillis();
            // The left column: the menu, the channel between its chevrons, the day and time.
            float right = Ui.f(410);
            Ui.chevron(c, Ui.f(106), Ui.f(791), Ui.f(13), Ui.LEFT, chevDim);
            c.drawText("Menu", Ui.f(130), Ui.f(801), menuP);
            Ui.chevron(c, right - Ui.f(20), Ui.f(848), Ui.f(15), Ui.UP, chev);
            String num = String.valueOf(lib.number(ch));
            float nw = nameP.measureText(num);
            c.drawText(num, right - nw, Ui.f(910), nameP);
            String name = Ui.fit(ch.name, nameP, Ui.f(300) - nw);
            c.drawText(name, right - nw - Ui.f(16) - nameP.measureText(name), Ui.f(910), nameP);
            Ui.chevron(c, right - Ui.f(20), Ui.f(954), Ui.f(15), Ui.DOWN, chev);
            String day = Ui.dayClock(now);
            c.drawText(day, right - dayP.measureText(day), Ui.f(1030), dayP);

            // Now and next.
            Guide.Schedule s = lib.schedule(ch.key);
            int i = s == null ? -1 : s.at(now);
            int n = s == null ? -1 : i >= 0 ? (i + 1 < s.start.length ? i + 1 : -1) : s.after(now);
            programme(c, ch, s, i, 0, Ui.f(446), Ui.f(1170), now);
            programme(c, ch, s, n, 1, Ui.f(1194), Ui.f(1890), now);

            boolean other = showing != null && tv.live.channel() != null && !showing.key.equals(tv.live.channel().key);
            if (other) {
                String hint = "OK to watch " + ch.name;
                c.drawText(hint, w - Ui.f(40) - hintP.measureText(hint), top - Ui.f(22), hintP);
            } else if (tv.live.state() == LiveTv.FAILED) {
                String hint = "OK to try again";
                c.drawText(hint, w - Ui.f(40) - hintP.measureText(hint), top - Ui.f(22), hintP);
            }
        }

        private void programme(Canvas c, Channel ch, Guide.Schedule s, int i, int which, float x0, float x1, long now) {
            r.set(x0, Ui.f(774), x1, Ui.f(992));
            if (card == which) glow.draw(c, r);
            else c.drawRect(r, cardBack);
            // The picture: the channel's logo on the guide's blue.
            float tx = x0 + Ui.f(16), ty = Ui.f(790), tw = Ui.f(290), th = Ui.f(163);
            if (thumbFor != ty) {
                thumb.setShader(new LinearGradient(0, ty, 0, ty + th, Ui.CARD_LIGHT, Ui.NAVY, Shader.TileMode.CLAMP));
                thumbFor = ty;
            }
            c.drawRect(tx, ty, tx + tw, ty + th, thumb);
            Bitmap logo = Pics.get(ch.logo, Math.round(tw * 0.7f), Math.round(th * 0.5f), Pics.FIT, redraw);
            if (logo != null) c.drawBitmap(logo, tx + (tw - logo.getWidth()) / 2f, ty + (th - logo.getHeight()) / 2f, bmp);
            else {
                String nm = Ui.fit(ch.name, thumbName, tw - Ui.f(24));
                c.drawText(nm, tx + (tw - thumbName.measureText(nm)) / 2f, ty + th / 2f + Ui.f(9), thumbName);
            }
            float x = tx + tw + Ui.f(24), room = x1 - x - Ui.f(18);
            String label = which == 0 ? "Now" : "Next";
            TextPaint lp = which == 0 ? labelP : labelDim;
            c.drawText(label, x, Ui.f(822), lp);
            if (i < 0) {
                c.drawText(Ui.fit(which == 0 ? ch.name : "", titleP, room), x, Ui.f(874), titleP);
                if (which == 0) c.drawText(Ui.fit(s == null ? "No programme information" : "", descP, room), x, Ui.f(914), descP);
                return;
            }
            c.drawText(Ui.fit(Ui.span(s.start[i], s.stop[i]), timeP, room - lp.measureText(label) - Ui.f(16)),
                    x + lp.measureText(label) + Ui.f(16), Ui.f(822), timeP);
            StaticLayout t = Ui.layout(s.title[i], titleP, Math.round(room), 2);
            Ui.draw(c, t, x, Ui.f(838));
            float after = Ui.f(838) + t.getHeight() + Ui.f(6);
            if (!s.desc[i].isEmpty() && after < Ui.f(950)) {
                StaticLayout d = Ui.layout(s.desc[i], descP, Math.round(room), t.getLineCount() > 1 ? 1 : 2);
                Ui.draw(c, d, x, after);
            }
            if (which == 0 && s.start[i] <= now) {
                float p = (now - s.start[i]) / (float) Math.max(1, s.stop[i] - s.start[i]);
                float by = ty + th + Ui.f(12);
                c.drawRect(tx, by, tx + tw, by + Ui.f(5), track);
                c.drawRect(tx, by, tx + tw * Math.min(1f, p), by + Ui.f(5), bar);
            }
        }
    }
}
