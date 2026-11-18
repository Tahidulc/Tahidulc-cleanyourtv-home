package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.prefs.Parental;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.Locale;

/**
 * Settings → Parental control's PIN, asked over whatever is on screen: a card with four boxes and a row of number keys
 * (Left and Right along it, OK to press; the remote's own number keys work too). Back clears what was typed, then
 * closes it. Checking: the right PIN opens everything locked until Homefield TV is left, then what was asked for goes
 * ahead; a wrong one says so (five in a row wait 30 seconds). Choosing: a new PIN typed twice.
 */
final class PinScreen extends Screen {
    /** What a new PIN is for once it has been typed twice. */
    interface Chosen {
        void pin(String pin);
    }

    private static final String KEYS = "1234567890<";
    private final String title, about;
    private final Runnable then;
    private final Chosen chosen;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String typed = "", first = "", note = "";
    private boolean noteBad;
    private int at;
    private Pad pad;

    private PinScreen(TvActivity tv, String title, String about, Runnable then, Chosen chosen) {
        super(tv);
        this.title = title;
        this.about = about;
        this.then = then;
        this.chosen = chosen;
    }

    // ---- asked from anywhere ----------------------------------------------------------------------------------------

    /** go at once when nothing is locked (or the PIN was entered since Homefield TV was last left), else after the PIN. */
    static void check(TvActivity tv, String about, Runnable go) {
        if (!Parental.hasPin(tv) || Parental.open()) {
            go.run();
            return;
        }
        tv.push(new PinScreen(tv, "Enter your PIN", about, go, null));
    }

    /** A channel played by go, after the PIN when it is locked. */
    static void forChannel(TvActivity tv, Channel c, Runnable go) {
        if (c != null && Parental.blocks(tv, c)) check(tv, ChannelEdits.name(c) + " is locked", go);
        else go.run();
    }

    /** Settings opened by go, after the PIN when Settings is locked. */
    static void forSettings(TvActivity tv, Runnable go) {
        if (Parental.settingsBlocked(tv)) check(tv, "Settings are locked", go);
        else go.run();
    }

    /** A new PIN typed twice, then handed to done. */
    static void choose(TvActivity tv, String title, Chosen done) {
        tv.push(new PinScreen(tv, title, "Four numbers you'll remember", null, done));
    }

    // ---- the screen -------------------------------------------------------------------------------------------------

    @Override View build() {
        pad = new Pad(tv);
        return pad;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override void hidden() {
        h.removeCallbacksAndMessages(null);
    }

    @Override boolean key(int code, KeyEvent e) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            digit((char) ('0' + code - KeyEvent.KEYCODE_0));
            return true;
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            digit((char) ('0' + code - KeyEvent.KEYCODE_NUMPAD_0));
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                at = (at - 1 + KEYS.length()) % KEYS.length();
                pad.invalidate();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                at = (at + 1) % KEYS.length();
                pad.invalidate();
                return true;
            case KeyEvent.KEYCODE_DEL:
                erase();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_GUIDE:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_LAST_CHANNEL:
            case KeyEvent.KEYCODE_PROG_RED:
            case KeyEvent.KEYCODE_PROG_GREEN:
            case KeyEvent.KEYCODE_PROG_YELLOW:
            case KeyEvent.KEYCODE_PROG_BLUE:
                // The app's own keys do nothing while the PIN is asked (the TV's own, such as the volume, still work).
                return true;
            default:
                return false;
        }
    }

    @Override void ok() {
        char k = KEYS.charAt(at);
        if (k == '<') erase();
        else digit(k);
    }

    @Override void okLong() {
        ok();
    }

    @Override boolean back() {
        if (typed.isEmpty()) return false;
        typed = "";
        pad.invalidate();
        return true;
    }

    private void erase() {
        if (!typed.isEmpty()) typed = typed.substring(0, typed.length() - 1);
        pad.invalidate();
    }

    private void digit(char d) {
        long wait = chosen == null ? Parental.waitMs() : 0;
        if (wait > 0) {
            say(String.format(Locale.UK, "Too many tries: try again in %d seconds", (wait + 999) / 1000), true);
            return;
        }
        if (typed.length() >= Parental.LENGTH) return;
        typed += d;
        if (!note.isEmpty() && noteBad) note = "";
        pad.invalidate();
        if (typed.length() == Parental.LENGTH) h.postDelayed(this::full, 160);
    }

    /** Four numbers typed: checked, or (choosing) asked again and then kept. */
    private void full() {
        String pin = typed;
        typed = "";
        if (chosen != null) {
            if (first.isEmpty()) {
                first = pin;
                say("Now type it again", false);
                return;
            }
            if (!first.equals(pin)) {
                first = "";
                say("Those weren't the same: start again", true);
                return;
            }
            // Kept before the screen under it fills again, so that shows the new PIN's lines.
            chosen.pin(pin);
            tv.pop();
            return;
        }
        if (Parental.check(tv, pin)) {
            Parental.unlock();
            tv.pop();
            if (then != null) then.run();
            return;
        }
        long wait = Parental.waitMs();
        say(wait > 0 ? String.format(Locale.UK, "Too many tries: try again in %d seconds", (wait + 999) / 1000)
                : "That isn't the PIN", true);
    }

    private void say(String words, boolean bad) {
        note = words;
        noteBad = bad;
        pad.invalidate();
    }

    /** The card: a padlock, what is asked, four boxes, the number keys, and a word when it went wrong. */
    private final class Pad extends View {
        private static final float W = 880f, H = 560f, KEY = 64f, GAP = 10f;
        private final Paint shade = Dark.fill(Dark.alpha(Dark.GROUND, 0xD8)), card = Dark.fill(Dark.CARD),
                edge = Dark.line(Dark.LINE, 1.5f), box = Dark.fill(Dark.CARD_HI), boxEdge = Dark.line(Dark.alpha(Dark.TEXT, 0x40), 2f),
                dot = Dark.fill(Dark.TEXT), keyFill = Dark.fill(Dark.alpha(Dark.TEXT, 0x14)), keyOn = Dark.fill(Dark.ACCENT),
                ring = Dark.line(Dark.alpha(Dark.RING, 0xE6), 2.5f), lock = Dark.fill(Dark.ACCENT),
                shackle = Dark.line(Dark.ACCENT, 6f);
        private final TextPaint kicker = Dark.text(24, 600, Dark.ACCENT), head = Dark.text(42, 600, Dark.TEXT),
                soft = Dark.text(26, 400, Dark.SOFT), keyText = Dark.text(30, 600, Dark.TEXT), keyInk = Dark.text(30, 600, Dark.ACCENT_INK),
                good = Dark.text(26, 500, Dark.ACCENT_SOFT), bad = Dark.text(26, 500, 0xFFFF8A80), hint = Dark.text(22, 400, Dark.MUTED);
        private final RectF r = new RectF();
        private final Path path = new Path();

        Pad(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), shade);
            float x = (1920f - W) / 2f, y = (1080f - H) / 2f;
            r.set(Ui.f(x), Ui.f(y), Ui.f(x + W), Ui.f(y + H));
            c.drawRoundRect(r, Ui.f(28), Ui.f(28), card);
            c.drawRoundRect(r, Ui.f(28), Ui.f(28), edge);
            float mid = x + W / 2f;
            padlock(c, mid, y + 70);
            centre(c, "PARENTAL CONTROL", mid, y + 146, kicker);
            centre(c, Ui.fit(first.isEmpty() || chosen == null ? title : "Type it again", head, Ui.f(W - 96)), mid, y + 202, head);
            centre(c, Ui.fit(about == null ? "" : about, soft, Ui.f(W - 96)), mid, y + 244, soft);
            // The four boxes, a dot for each number typed.
            float bw = 76f, bg = 22f, bx = mid - (4 * bw + 3 * bg) / 2f, by = y + 278;
            for (int i = 0; i < Parental.LENGTH; i++) {
                float l = bx + i * (bw + bg);
                r.set(Ui.f(l), Ui.f(by), Ui.f(l + bw), Ui.f(by + 84));
                c.drawRoundRect(r, Ui.f(16), Ui.f(16), box);
                c.drawRoundRect(r, Ui.f(16), Ui.f(16), boxEdge);
                if (i < typed.length()) c.drawCircle(r.centerX(), r.centerY(), Ui.f(11), dot);
            }
            // The number keys, the one the remote is on lit.
            float kw = KEYS.length() * KEY + (KEYS.length() - 1) * GAP, kx = mid - kw / 2f, ky = y + 394;
            for (int i = 0; i < KEYS.length(); i++) {
                float l = kx + i * (KEY + GAP);
                r.set(Ui.f(l), Ui.f(ky), Ui.f(l + KEY), Ui.f(ky + KEY));
                boolean on = i == at;
                c.drawRoundRect(r, Ui.f(KEY / 2f), Ui.f(KEY / 2f), on ? keyOn : keyFill);
                if (on) Dark.ring(c, r, Ui.f(KEY / 2f), ring);
                char k = KEYS.charAt(i);
                if (k == '<') backspace(c, r, on ? keyInk : keyText);
                else {
                    String s = String.valueOf(k);
                    TextPaint p = on ? keyInk : keyText;
                    c.drawText(s, r.centerX() - p.measureText(s) / 2f, r.centerY() + Ui.f(11), p);
                }
            }
            if (!note.isEmpty()) centre(c, Ui.fit(note, noteBad ? bad : good, Ui.f(W - 96)), mid, y + 506, noteBad ? bad : good);
            else centre(c, "The remote's number keys work too  ·  Back to go back", mid, y + 506, hint);
        }

        private void centre(Canvas c, String s, float cx, float baseline, TextPaint p) {
            c.drawText(s, Ui.f(cx) - p.measureText(s) / 2f, Ui.f(baseline), p);
        }

        private void padlock(Canvas c, float cx, float cy) {
            r.set(Ui.f(cx - 22), Ui.f(cy - 6), Ui.f(cx + 22), Ui.f(cy + 30));
            c.drawRoundRect(r, Ui.f(6), Ui.f(6), lock);
            shackle.setStrokeWidth(Ui.f(6));
            r.set(Ui.f(cx - 14), Ui.f(cy - 30), Ui.f(cx + 14), Ui.f(cy + 4));
            c.drawArc(r, 180f, 180f, false, shackle);
            c.drawLine(Ui.f(cx - 14), Ui.f(cy - 13), Ui.f(cx - 14), Ui.f(cy - 4), shackle);
            c.drawLine(Ui.f(cx + 14), Ui.f(cy - 13), Ui.f(cx + 14), Ui.f(cy - 4), shackle);
        }

        /** The key that takes the last number back: an arrow-ended tag with a cross. */
        private void backspace(Canvas c, RectF k, Paint p) {
            float cx = k.centerX(), cy = k.centerY(), u = Ui.f(1);
            path.reset();
            path.moveTo(cx - 16 * u, cy);
            path.lineTo(cx - 6 * u, cy - 11 * u);
            path.lineTo(cx + 16 * u, cy - 11 * u);
            path.lineTo(cx + 16 * u, cy + 11 * u);
            path.lineTo(cx - 6 * u, cy + 11 * u);
            path.close();
            Paint.Style was = p.getStyle();
            float w = p.getStrokeWidth();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f * u);
            c.drawPath(path, p);
            c.drawLine(cx - 1 * u, cy - 5 * u, cx + 9 * u, cy + 5 * u, p);
            c.drawLine(cx + 9 * u, cy - 5 * u, cx - 1 * u, cy + 5 * u, p);
            p.setStyle(was);
            p.setStrokeWidth(w);
        }
    }
}
