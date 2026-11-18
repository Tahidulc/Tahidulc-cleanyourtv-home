package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

/**
 * 07's small pieces, drawn as the mock-ups draw them (tv.css), each at (x, centre y) in screen pixels, sizes in design
 * pixels, nothing made a frame. The words are drawn as given (write capitals for a label; fit long words first with
 * TextFit). Each returns its width, to lay a row of them out.
 *
 * <pre>
 *   pills and buttons
 *   pill(c, x, cy, text, on, glass)              a section's pill, 52 high: white with ink when on; glass or nothing off
 *   pill(c, x, cy, text, count, on, glass)       with its count after it ("Defenders 9")
 *   button(c, x, cy, text, icon, lit, small)     a button, 70 high (56 small): white with ink (lit: the main one or where
 *                                                the remote is), else white at 16 %; icon: Icons07's, or -1
 *   circle(c, cx, cy, icon, lit, small)          a round button (70, or 56)
 *   key(c, x, cy, key)                           a remote key's chip in a hint ("OK", "▶"), then hint(c, x, cy, text)
 *
 *   chips (HD, 4K, FPS, LIVE, CC, AD)
 *   chip(c, x, cy, text)                         outlined, 26 high: HD, 4K, CC, AD, DOLBY VISION beside a title's facts
 *   chipFilled(c, x, cy, text)                   white at 16 % with a hairline, 28 high: the guide panel's HD · 50 FPS
 *   live(c, x, cy)                               the red LIVE badge with its white dot
 *   liveOn(c, x, cy, text)                       LIVE on a picture: dark glass, a red dot ("LIVE · 75'")
 *   time(c, x, cy, text, bell)                   a time on a picture ("22:00", a bell for a reminder)
 *   quality(c, right, top, text)                 a poster's 4K / 1080p chip (its top right corner given)
 *   badge(c, x, top, text)                       NEW in Homefield's amber
 *   rating(c, x, cy, text)                       a rating on a picture ("★ 7.4")
 *   certificate(c, x, cy, text)                  a certificate among facts ("15")
 *
 *   bars and switches
 *   progress(c, x, cy, w, done)                  the 6 px bar under a programme (white over white 22 %)
 *   progressOn(c, x, y, w, done)                 the 5 px bar along a picture's foot (white over white 32 %)
 *   toggle(c, x, cy, on, onWhite)                a switch, 64 x 38: green when on (onWhite: on the remote's white row)
 *
 *   headers
 *   back(c, x, cy, text)                         "‹ Guide": a screen reached from another (TabBar draws it for screens)
 *   section(c, x, baseline, title, after)        a shelf's title (30 px) and a quieter word after it ("Season 2", "9")
 *   label(c, x, baseline, CAPS, colour)          a section's name in capitals, letter-spaced (15 px)
 * </pre>
 */
final class Pills {
    private Pills() {}

    static final float PILL_H = 52f, PILL_PAD = 24f, BUTTON_H = 70f, BUTTON_PAD = 34f, BUTTON_SM_H = 56f, BUTTON_SM_PAD = 26f,
            CHIP_H = 26f, CHIP_PAD = 7f, CHIP_FILLED_H = 28f, CHIP_FILLED_PAD = 9f, LIVE_H = 30f, GAP = 10f;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG), LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static TextPaint pill, pillOn, count, button, buttonSm, chip, chipFilled, live, liveOn, time, small, rating,
            back, section, after, label, key, hint;
    private static float madeFor = -1f;

    static {
        LINE.setStyle(Paint.Style.STROKE);
    }

    /** The paints, made when the screen's scale or the text size changes (call from a view's constructor). */
    static void prepare() {
        float k = Ui.s * com.gathin.homefieldtv.prefs.Look.text;
        if (pill != null && k == madeFor) return;
        madeFor = k;
        pill = Type.paint(Type.PILL, Type.MEDIUM, Look07.L2);
        pillOn = Type.paint(Type.PILL, Type.SEMIBOLD, Look07.INK);
        count = Type.paint(17f, Type.SEMIBOLD, Look07.L3);
        button = Type.paint(Type.BUTTON, Type.SEMIBOLD, Look07.L1);
        buttonSm = Type.paint(Type.BUTTON_SM, Type.SEMIBOLD, Look07.L1);
        chip = Type.paint(Type.CHIP07, Type.BOLD, Look07.L2);
        chip.setLetterSpacing(0.02f);
        chipFilled = Type.paint(Type.CHIP07, Type.BOLD, Look07.L1);
        chipFilled.setLetterSpacing(0.03f);
        live = Type.paint(16f, Type.BOLD, Look07.L1);
        live.setLetterSpacing(0.06f);
        liveOn = Type.paint(16f, Type.BOLD, Look07.L1);
        liveOn.setLetterSpacing(0.04f);
        time = Type.clock(17f, Type.SEMIBOLD, Look07.L1);
        small = Type.paint(Type.CHIP_SMALL, Type.EXTRABOLD, Look07.INK);
        small.setLetterSpacing(0.02f);
        rating = Type.paint(14f, Type.BOLD, Look07.L1);
        back = Type.paint(Type.BACK, Type.SEMIBOLD, Look07.L3);
        section = Type.paint(Type.H3, Type.BOLD, Look07.L1);
        after = Type.paint(21f, Type.MEDIUM, Look07.L3);
        label = Type.label(Type.LABEL, Look07.L3);
        key = Type.paint(13f, Type.BOLD, Look07.L1);
        hint = Type.paint(19f, Type.MEDIUM, Look07.L2);
    }

    // ---- pills and buttons ----------------------------------------------------------------------------------------------

    static float pillWidth(String text, boolean on) {
        prepare();
        return (on ? pillOn : pill).measureText(text) + Ui.f(PILL_PAD * 2);
    }

    static float pill(Canvas c, float x, float cy, String text, boolean on, boolean glass) {
        return pill(c, x, cy, text, null, on, glass, PILL_H);
    }

    static float pill(Canvas c, float x, float cy, String text, String n, boolean on, boolean glass) {
        return pill(c, x, cy, text, n, on, glass, PILL_H);
    }

    /** A pill height h (design px: 52, Home's 50, a filter's 48). */
    static float pill(Canvas c, float x, float cy, String text, String n, boolean on, boolean glass, float h) {
        prepare();
        TextPaint p = on ? pillOn : pill;
        float pad = Ui.f(PILL_PAD * h / PILL_H), gap = Ui.f(10f);
        float w = p.measureText(text) + pad * 2 + (n == null || n.isEmpty() ? 0f : gap + count.measureText(n));
        float hh = Ui.f(h) / 2f;
        R.set(x, cy - hh, x + w, cy + hh);
        if (on) {
            FILL.setColor(Look07.FOCUS_FILL);
            c.drawRoundRect(R, hh, hh, FILL);
        } else if (glass) {
            Frost.glass(c, R, hh, 1f);
        }
        p.setColor(on ? Look07.INK : Look07.L2);
        c.drawText(text, x + pad, Type.centred(p, cy), p);
        if (n != null && !n.isEmpty()) {
            count.setColor(on ? Look07.INK3 : Look07.L3);
            c.drawText(n, x + pad + p.measureText(text) + gap, Type.centred(count, cy), count);
        }
        return w;
    }

    static float buttonWidth(String text, int icon, boolean small) {
        prepare();
        TextPaint p = small ? buttonSm : button;
        float pad = Ui.f(small ? BUTTON_SM_PAD : BUTTON_PAD);
        return p.measureText(text) + pad * 2 + (icon >= 0 ? Ui.f(small ? 22f : 26f) + Ui.f(14f) : 0f);
    }

    /** A button from x centred on cy: lit, white with ink and its shadow; else white at 16 % with white words. */
    static float button(Canvas c, float x, float cy, String text, int icon, boolean lit, boolean small) {
        prepare();
        TextPaint p = small ? buttonSm : button;
        float h = Ui.f(small ? BUTTON_SM_H : BUTTON_H), pad = Ui.f(small ? BUTTON_SM_PAD : BUTTON_PAD);
        float is = Ui.f(small ? 22f : 26f), w = buttonWidth(text, icon, small);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        if (lit) Shadows.draw(c, R, h / 2f, Look07.SH_BUTTON, 1f);
        FILL.setColor(lit ? Look07.L1 : Look07.BUTTON);
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        int ink = lit ? Look07.INK : Look07.L1;
        float tx = x + pad;
        if (icon >= 0) {
            Icons07.draw(c, icon, tx + is / 2f, cy, is, ink);
            tx += is + Ui.f(14f);
        }
        p.setColor(ink);
        c.drawText(text, tx, Type.centred(p, cy), p);
        return w;
    }

    /** A round button centred on (cx, cy): its icon, white and lifted where the remote is. */
    static void circle(Canvas c, float cx, float cy, int icon, boolean lit, boolean small) {
        float d = Ui.f(small ? BUTTON_SM_H : BUTTON_H);
        R.set(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f);
        if (lit) Shadows.draw(c, R, d / 2f, Look07.SH_BUTTON, 1f);
        FILL.setColor(lit ? Look07.L1 : Look07.BUTTON);
        c.drawCircle(cx, cy, d / 2f, FILL);
        Icons07.draw(c, icon, cx, cy, Ui.f(small ? 24f : 28f), lit ? Look07.INK : Look07.L1);
    }

    /** A remote key's chip in a hint ("OK"), 30 high: white at 18 % with a hairline. */
    static float key(Canvas c, float x, float cy, String k) {
        prepare();
        float h = Ui.f(30f), w = Math.max(Ui.f(38f), key.measureText(k) + Ui.f(18f));
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(0x2EFFFFFF);
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        LINE.setStrokeWidth(Math.max(1f, Ui.f(1f)));
        LINE.setColor(0x4DFFFFFF);
        c.drawRoundRect(R, h / 2f, h / 2f, LINE);
        c.drawText(k, R.centerX() - key.measureText(k) / 2f, Type.centred(key, cy), key);
        return w;
    }

    /** A hint's words after its key (19 px, L2). */
    static float hint(Canvas c, float x, float cy, String text) {
        prepare();
        c.drawText(text, x, Type.centred(hint, cy), hint);
        return hint.measureText(text);
    }

    // ---- chips -------------------------------------------------------------------------------------------------------------

    /** HD, 4K, CC, AD: outlined in white at 55 %, 26 high, its letters L2. */
    static float chip(Canvas c, float x, float cy, String text) {
        return chip(c, x, cy, text, false);
    }

    /** The same, or (ink) on 07's white focus: outlined in black at 40 %, its letters dark. */
    static float chip(Canvas c, float x, float cy, String text, boolean ink) {
        prepare();
        float h = Ui.f(CHIP_H), w = chip.measureText(text) + Ui.f(CHIP_PAD * 2);
        float sw = Math.max(1f, Ui.f(1.5f));
        R.set(x + sw / 2f, cy - h / 2f + sw / 2f, x + w - sw / 2f, cy + h / 2f - sw / 2f);
        LINE.setStrokeWidth(sw);
        LINE.setColor(ink ? 0x66000000 : 0x8CEBEBF5);
        c.drawRoundRect(R, Ui.f(Look07.R_CHIP), Ui.f(Look07.R_CHIP), LINE);
        int was = chip.getColor();
        if (ink) chip.setColor(Look07.INK2);
        c.drawText(text, x + Ui.f(CHIP_PAD), Type.centred(chip, cy), chip);
        chip.setColor(was);
        return w;
    }

    /** The guide panel's chips (HD, 50 FPS, STEREO): white at 16 % with a hairline at 25 %, white letters, 28 high. */
    static float chipFilled(Canvas c, float x, float cy, String text) {
        prepare();
        float h = Ui.f(CHIP_FILLED_H), w = chipFilled.measureText(text) + Ui.f(CHIP_FILLED_PAD * 2);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(0x29FFFFFF);
        c.drawRoundRect(R, Ui.f(7f), Ui.f(7f), FILL);
        LINE.setStrokeWidth(Math.max(1f, Ui.f(1f)));
        LINE.setColor(0x40FFFFFF);
        c.drawRoundRect(R, Ui.f(7f), Ui.f(7f), LINE);
        c.drawText(text, x + Ui.f(CHIP_FILLED_PAD), Type.centred(chipFilled, cy), chipFilled);
        return w;
    }

    /** The red LIVE badge (30 high, its white dot). */
    static float live(Canvas c, float x, float cy) {
        prepare();
        float h = Ui.f(LIVE_H), dot = Ui.f(7f), w = Ui.f(12f) + dot + Ui.f(7f) + live.measureText("LIVE") + Ui.f(12f);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(Look07.RED);
        c.drawRoundRect(R, Ui.f(8f), Ui.f(8f), FILL);
        FILL.setColor(Look07.L1);
        c.drawCircle(x + Ui.f(12f) + dot / 2f, cy, dot / 2f, FILL);
        c.drawText("LIVE", x + Ui.f(12f) + dot + Ui.f(7f), Type.centred(live, cy), live);
        return w;
    }

    /** LIVE on a picture: black at 45 % in a 30 high pill, a red dot, "LIVE" or "LIVE · 75'". */
    static float liveOn(Canvas c, float x, float cy, String text) {
        prepare();
        String t = text == null || text.isEmpty() ? "LIVE" : text;
        float h = Ui.f(LIVE_H), dot = Ui.f(8f), w = Ui.f(12f) + dot + Ui.f(7f) + liveOn.measureText(t) + Ui.f(12f);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(0x73000000);
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        FILL.setColor(Look07.RED);
        c.drawCircle(x + Ui.f(12f) + dot / 2f, cy, dot / 2f, FILL);
        c.drawText(t, x + Ui.f(12f) + dot + Ui.f(7f), Type.centred(liveOn, cy), liveOn);
        return w;
    }

    static float timeWidth(String text, boolean bell) {
        prepare();
        return Ui.f(12f) + (bell ? Ui.f(16f) + Ui.f(6f) : 0f) + time.measureText(text) + Ui.f(12f);
    }

    /** A time on a picture (black at 42 %, 30 high, tabular figures), a bell before it for a reminder. */
    static float time(Canvas c, float x, float cy, String text, boolean bell) {
        prepare();
        float h = Ui.f(LIVE_H), bw = bell ? Ui.f(16f) + Ui.f(6f) : 0f, w = timeWidth(text, bell);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(0x6B000000);
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        if (bell) Icons07.draw(c, Icons07.BELL, x + Ui.f(12f) + Ui.f(8f), cy, Ui.f(16f), Look07.L1);
        c.drawText(text, x + Ui.f(12f) + bw, Type.centred(time, cy), time);
        return w;
    }

    /** A poster's quality chip (4K, 1080p): white at 92 % with ink, 24 high, its top right corner at (right, top). */
    static float quality(Canvas c, float right, float top, String text) {
        prepare();
        float h = Ui.f(24f), w = small.measureText(text) + Ui.f(14f);
        R.set(right - w, top, right, top + h);
        FILL.setColor(0xEBFFFFFF);
        c.drawRoundRect(R, Ui.f(Look07.R_CHIP), Ui.f(Look07.R_CHIP), FILL);
        small.setColor(Look07.INK);
        c.drawText(text, R.left + Ui.f(7f), Type.centred(small, R.centerY()), small);
        return w;
    }

    /** NEW: Homefield's amber with its ink, 24 high, its top left at (x, top). */
    static float badge(Canvas c, float x, float top, String text) {
        prepare();
        float h = Ui.f(24f), w = small.measureText(text) + Ui.f(16f);
        R.set(x, top, x + w, top + h);
        FILL.setColor(Look07.AMBER);
        c.drawRoundRect(R, Ui.f(Look07.R_CHIP), Ui.f(Look07.R_CHIP), FILL);
        small.setColor(Look07.AMBER_INK);
        c.drawText(text, x + Ui.f(8f), Type.centred(small, R.centerY()), small);
        small.setColor(Look07.INK);
        return w;
    }

    /** A rating on a picture: black at 50 %, 26 high. */
    static float rating(Canvas c, float x, float cy, String text) {
        prepare();
        float h = Ui.f(26f), w = rating.measureText(text) + Ui.f(16f);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(0x80000000);
        c.drawRoundRect(R, Ui.f(Look07.R_CHIP), Ui.f(Look07.R_CHIP), FILL);
        c.drawText(text, x + Ui.f(8f), Type.centred(rating, cy), rating);
        return w;
    }

    /** A certificate among a title's facts: white at 20 %, 26 high, white letters. */
    static float certificate(Canvas c, float x, float cy, String text) {
        prepare();
        float h = Ui.f(26f), w = chipFilled.measureText(text) + Ui.f(16f);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(0x33FFFFFF);
        c.drawRoundRect(R, Ui.f(Look07.R_CHIP), Ui.f(Look07.R_CHIP), FILL);
        c.drawText(text, x + Ui.f(8f), Type.centred(chipFilled, cy), chipFilled);
        return w;
    }

    // ---- bars and switches -------------------------------------------------------------------------------------------------

    /** The bar under a programme: 6 high, white over white at 22 %, done 0 to 1. */
    static void progress(Canvas c, float x, float cy, float w, float done) {
        bar(c, x, cy - Ui.f(3f), w, Ui.f(6f), done, 0x38FFFFFF);
    }

    /** The bar along a picture's foot: 5 high, white over white at 32 % (its top at y). */
    static void progressOn(Canvas c, float x, float y, float w, float done) {
        bar(c, x, y, w, Ui.f(5f), done, 0x52FFFFFF);
    }

    private static void bar(Canvas c, float x, float y, float w, float h, float done, int track) {
        float r = h / 2f;
        R.set(x, y, x + w, y + h);
        FILL.setColor(track);
        c.drawRoundRect(R, r, r, FILL);
        float d = Math.max(0f, Math.min(1f, done));
        if (d <= 0f) return;
        R.set(x, y, x + Math.max(h, w * d), y + h);
        FILL.setColor(Look07.L1);
        c.drawRoundRect(R, r, r, FILL);
    }

    /** A switch, 64 x 38 from x centred on cy: green when on; off, white at 22 % (black at 18 % on the white row). */
    static void toggle(Canvas c, float x, float cy, boolean on, boolean onWhite) {
        float w = Ui.f(64f), h = Ui.f(38f), knob = Ui.f(30f);
        R.set(x, cy - h / 2f, x + w, cy + h / 2f);
        FILL.setColor(on ? Look07.GREEN : onWhite ? Palette.SWITCH_OFF_FOCUS : Palette.SWITCH_OFF);
        c.drawRoundRect(R, h / 2f, h / 2f, FILL);
        float kx = on ? x + w - Ui.f(4f) - knob / 2f : x + Ui.f(4f) + knob / 2f;
        FILL.setColor(0x4D000000);
        c.drawCircle(kx, cy + Ui.f(2f), knob / 2f, FILL);
        FILL.setColor(Look07.L1);
        c.drawCircle(kx, cy, knob / 2f, FILL);
    }

    // ---- headers -----------------------------------------------------------------------------------------------------------

    /** "‹ Guide": the way back from a screen reached from another (20 px, L3, the chevron 20). */
    static float back(Canvas c, float x, float cy, String text) {
        prepare();
        float is = Ui.f(Look07.BACK_ICON);
        Icons07.draw(c, Icons07.LEFT, x + is / 2f, cy, is, Look07.L3);
        float tx = x + is + Ui.f(Look07.BACK_GAP);
        c.drawText(text, tx, Type.centred(back, cy), back);
        return tx - x + back.measureText(text);
    }

    /** A shelf's title (30 px bold) and a quieter word after it, on one baseline. */
    static float section(Canvas c, float x, float baseline, String title, String quiet) {
        prepare();
        c.drawText(title, x, baseline, section);
        float w = section.measureText(title);
        if (quiet == null || quiet.isEmpty()) return w;
        c.drawText(quiet, x + w + Ui.f(16f), baseline, after);
        return w + Ui.f(16f) + after.measureText(quiet);
    }

    /** A section's name in capitals (write them so), letter-spaced, in a colour (L3 in menus, amber in Settings). */
    static float label(Canvas c, float x, float baseline, String caps, int colour) {
        prepare();
        label.setColor(colour);
        c.drawText(caps, x, baseline, label);
        return label.measureText(caps);
    }
}
