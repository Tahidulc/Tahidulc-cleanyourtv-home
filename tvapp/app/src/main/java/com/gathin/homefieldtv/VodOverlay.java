package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;

/**
 * What the film player draws over the picture (VodActivity), in the 07 look of the live player's overlay:
 *
 * <pre>
 *   the overlay (OK)   at the top, over a shade, the title (46 px bold) and under it "S1 E3 · The Lighthouse" for an
 *                      episode (a film: its year, length and kind), the clock at the right; across the foot, over the
 *                      scrim, where it plays from (TorBox's or the playlist's mark, chips for its picture, HDR and sound,
 *                      "1 of 3 sources"), the seek line (80 to 1840: the accent up to where it is, light grey on to what
 *                      is loaded, the white knob), "52:17 / 2:25:00" at the left and "ends 22:41" at the right; the
 *                      transport in the middle (◀◀ ⏯ ▶▶, a series |◀ and ▶| either side), and under it the row of
 *                      buttons, each an icon over its name and what it is set to: Sources, Subtitles, Audio, Episodes
 *                      (a series), Speed, Picture, Info. The remote's item is white with dark ink.
 *   the scrubber       Left and Right on the plain picture: the foot's seek line alone, its knob lifted and a white
 *                      bubble over it with where it will go ("1:12:05") and how far ("+2:30")
 *   Up next            an episode's end: a card at the right with the next one's picture, "UP NEXT · 8", its number and
 *                      name, "OK plays it now · Back cancels" and the countdown along its foot
 *   a note             a short word at the top (a copy that stopped and the next one playing), gone after a while
 *   what went wrong    in the middle ("It didn't play. OK to try again.")
 * </pre>
 *
 * A frame makes nothing new: its paints and shades are made once (the shades again only for a new height), and its words
 * are made again only when the second (or the minute) they show changes.
 */
final class VodOverlay extends View {
    /** Places (design px). */
    static final float SEEK_X0 = 80f, SEEK_X1 = 1840f, SEEK_Y = 752f, TIME_Y = 812f, TRANSPORT_Y = 800f, PITCH = 104f,
            SOURCE_Y = 700f, BAR_Y = 930f, BAR_PITCH = 172f, FOOT_SCRIM = 520f, TOP_SCRIM = 300f;
    /** Where the overlay's words begin: subtitles stay above this while it is up. */
    static final float TOP = 660f;
    /** Up next's card. */
    static final float NEXT_X = 1232f, NEXT_W = 640f, NEXT_H = 214f, NEXT_FOOT = 1032f, NEXT_PIC_W = 228f;

    private final VodActivity a;
    private final Paint foot = new Paint(), top = new Paint(), fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pic = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titleP = Type.paint(46f, Type.BOLD, Look07.L1), subP = Type.paint(26f, Type.MEDIUM, Look07.L2);
    private final TextPaint clockP = Type.clock(32f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint timeP = Type.clock(32f, Type.REGULAR, Look07.L1), totalP = Type.clock(32f, Type.REGULAR, Look07.L2);
    private final TextPaint endsP = Type.paint(28f, Type.MEDIUM, Look07.L2), countP = Type.paint(22f, Type.MEDIUM, Look07.L3);
    private final TextPaint labelP = Type.paint(Type.CHIP, Type.MEDIUM, Look07.L2), valueP = Type.paint(17f, Type.REGULAR, Look07.L3);
    private final TextPaint bubbleT = Type.clock(30f, Type.BOLD, Look07.INK), bubbleS = Type.clock(19f, Type.SEMIBOLD, 0xCC111111);
    private final TextPaint kickerP = Type.label(Type.LABEL, Look07.AMBER), nextP = Type.paint(26f, Type.SEMIBOLD, Look07.L1);
    private final TextPaint nextSub = Type.paint(20f, Type.REGULAR, Look07.L2), hintP = Type.paint(19f, Type.MEDIUM, Look07.L3);
    private final TextPaint noteP = Type.paint(22f, Type.MEDIUM, Look07.L1), errorP = Type.paint(32f, Type.MEDIUM, Look07.L1);
    private final Frost card = new Frost(Look07.MAT_DARK);
    private final SourcePicker.Mark mark = new SourcePicker.Mark();
    private final RectF r = new RectF();
    private final Path tip = new Path();
    private float footFor = -1f, topFor = -1f;
    /** The words shown, made again only when what they say changes. */
    private long timeFor = Long.MIN_VALUE, durFor = Long.MIN_VALUE, endsFor = Long.MIN_VALUE, bubbleFor = Long.MIN_VALUE, clockFor = Long.MIN_VALUE;
    private String timeW = "", totalW = "", endsW = "", bubbleW = "", bubbleBy = "", clockW = "";
    private int countFor = -1;
    private String countW = "";
    private final Runnable redraw = this::invalidate;

    VodOverlay(Context c, VodActivity a) {
        super(c);
        this.a = a;
        ring.setStyle(Paint.Style.STROKE);
        for (TextPaint p : new TextPaint[]{titleP, subP, clockP, timeP, totalP, endsP, errorP}) p.setShadowLayer(Ui.f(3), 0, 0, 0x99000000);
        Shadows.prepare(Look07.SH_BUTTON, 44f);
        Shadows.prepare(Look07.SH_PANEL, Look07.R_PANEL);
        Pills.prepare();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    // ---- times in words -------------------------------------------------------------------------------------------------

    /** Pure: "0:05", "12:34", "1:12:05". */
    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000, h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        StringBuilder b = new StringBuilder(9);
        if (h > 0) b.append(h).append(':').append(m < 10 ? "0" : "").append(m);
        else b.append(m);
        b.append(':').append(sec < 10 ? "0" : "").append(sec);
        return b.toString();
    }

    /** Pure: how far a jump goes: "+2:30", "−0:30". */
    static String by(long ms) {
        return (ms >= 0 ? "+" : "−") + clock(Math.abs(ms));
    }

    // ---- drawing --------------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), hh = getHeight();
        boolean again = false;
        String error = a.error();
        if (!error.isEmpty()) {
            String m = Ui.fit(error, errorP, Ui.f(1600));
            c.drawText(m, (w - errorP.measureText(m)) / 2f, hh / 2f, errorP);
        }
        boolean over = a.overlayOn(), scrub = !over && a.seekTarget() >= 0 && a.ready();
        if (over || scrub) {
            if (footFor != hh) {
                foot.setShader(new LinearGradient(0, Ui.f(FOOT_SCRIM), 0, hh, new int[]{0x00000000, 0xB0000000, 0xEB000000},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                footFor = hh;
            }
            c.drawRect(0, Ui.f(scrub ? FOOT_SCRIM + 120f : FOOT_SCRIM), w, hh, foot);
        }
        if (over) {
            drawTop(c, w);
            drawSource(c);
        }
        if (over || scrub) drawSeek(c, over);
        if (over) {
            drawTransport(c);
            drawButtons(c);
        }
        if (a.upNextShowing()) again |= drawUpNext(c);
        String note = a.note();
        if (!note.isEmpty()) drawNote(c, w, note);
        if (!Exec.inline && (over || scrub || again || !note.isEmpty())) postInvalidateDelayed(scrub || again ? 100 : 500);
    }

    /** The title, its second line, and the clock, over the top shade. */
    private void drawTop(Canvas c, int w) {
        if (topFor != Ui.f(TOP_SCRIM)) {
            top.setShader(new LinearGradient(0, 0, 0, Ui.f(TOP_SCRIM), 0xA6000000, 0x00000000, Shader.TileMode.CLAMP));
            topFor = Ui.f(TOP_SCRIM);
        }
        c.drawRect(0, 0, w, Ui.f(TOP_SCRIM), top);
        c.drawText(a.headWords(titleP, Ui.f(1400)), Ui.f(90), Ui.f(104), titleP);
        String sub = a.subWords(subP, Ui.f(1400));
        if (!sub.isEmpty()) c.drawText(sub, Ui.f(90), Ui.f(150), subP);
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != clockFor) {
            clockFor = minute;
            clockW = Ui.cornerClock(System.currentTimeMillis());
        }
        if (!clockW.isEmpty()) c.drawText(clockW, w - Ui.f(90) - clockP.measureText(clockW), Ui.f(100), clockP);
    }

    /** Where it plays from: the copy's mark, chips for what its name says, and which of how many. */
    private void drawSource(Canvas c) {
        Catalog.Play p = a.source();
        if (p == null) return;
        float cy = Ui.f(SOURCE_Y), x = Ui.f(SEEK_X0);
        x += mark.draw(c, x, cy, Ui.f(34), p.torbox, p.playlist, Ui.f(320));
        for (String m : a.chips()) x += Ui.f(12) + Pills.chip(c, x + Ui.f(12), cy, m);
        String n = a.copiesWords();
        if (!n.isEmpty()) c.drawText(n, x + Ui.f(20), Type.centred(countP, cy), countP);
    }

    /** The seek line, the time gone and the length, the end time; the bubble while the spot is moved. */
    private void drawSeek(Canvas c, boolean over) {
        long dur = a.duration(), pos = a.position(), target = a.seekTarget();
        boolean known = dur > 0;
        long at = target >= 0 ? target : pos;
        float share = known ? Math.max(0f, Math.min(1f, at / (float) dur)) : 0f;
        float loaded = known ? Math.max(share, Math.min(1f, a.buffered() / (float) dur)) : 0f;
        float x0 = Ui.f(SEEK_X0), x1 = Ui.f(SEEK_X1), y = Ui.f(SEEK_Y);
        Bars.seek(c, x0, x1, y, share, loaded, false);
        boolean onLine = target >= 0 || over && a.focusRow() == VodActivity.ROW_SEEK;
        if (onLine) bubble(c, share, at, pos, target >= 0);
        long sec = at / 1000;
        if (sec != timeFor || dur != durFor) {
            timeFor = sec;
            durFor = dur;
            timeW = clock(at);
            totalW = known ? " / " + clock(dur) : "";
        }
        float base = Ui.f(TIME_Y);
        c.drawText(timeW, x0, base, timeP);
        if (!totalW.isEmpty()) c.drawText(totalW, x0 + timeP.measureText(timeW), base, totalP);
        if (known) {
            // When it ends, at the speed it plays: made again each minute (or as the spot moves).
            long ends = System.currentTimeMillis() + (long) ((dur - at) / Math.max(0.25f, a.speed()));
            long minute = ends / 60_000L;
            if (minute != endsFor) {
                endsFor = minute;
                endsW = "ends " + Ui.clock(ends);
            }
            c.drawText(endsW, x1 - endsP.measureText(endsW), base, endsP);
        }
    }

    /** The knob lifted with a ring, and over it the bubble: where it will go and how far. */
    private void bubble(Canvas c, float share, long at, long pos, boolean moving) {
        float x0 = Ui.f(SEEK_X0), x1 = Ui.f(SEEK_X1), y = Ui.f(SEEK_Y);
        float kx = x0 + (x1 - x0) * share;
        fill.setColor(Look07.L1);
        c.drawCircle(kx, y, Ui.f(14f), fill);
        ring.setColor(Look07.L1);
        ring.setStrokeWidth(Math.max(1f, Ui.f(3f)));
        c.drawCircle(kx, y, Ui.f(22f), ring);
        long sec = at / 1000 * 2 + (moving ? 1 : 0);
        if (sec != bubbleFor) {
            bubbleFor = sec;
            bubbleW = clock(at);
            bubbleBy = moving ? by(at - pos) : "OK to jump";
        }
        float tw = Math.max(bubbleT.measureText(bubbleW), bubbleS.measureText(bubbleBy)) + Ui.f(40f);
        float bh = Ui.f(78f), by = y - Ui.f(36f) - bh;
        float bx = Math.max(x0 - Ui.f(20f), Math.min(x1 + Ui.f(20f) - tw, kx - tw / 2f));
        r.set(bx, by, bx + tw, by + bh);
        Shadows.draw(c, r, Ui.f(18f), Look07.SH_BUTTON, 1f);
        c.drawRoundRect(r, Ui.f(18f), Ui.f(18f), fill);
        tip.reset();
        tip.moveTo(kx - Ui.f(11f), by + bh - 1f);
        tip.lineTo(kx + Ui.f(11f), by + bh - 1f);
        tip.lineTo(kx, by + bh + Ui.f(12f));
        tip.close();
        c.drawPath(tip, fill);
        c.drawText(bubbleW, r.centerX() - bubbleT.measureText(bubbleW) / 2f, by + Ui.f(38f), bubbleT);
        c.drawText(bubbleBy, r.centerX() - bubbleS.measureText(bubbleBy) / 2f, by + Ui.f(64f), bubbleS);
    }

    /** The transport's middles (design px) for n buttons, centred on the screen. */
    static float transportX(int k, int n) {
        return 960f + (k - (n - 1) / 2f) * PITCH;
    }

    private void drawTransport(Canvas c) {
        int[] items = a.transport();
        boolean row = a.focusRow() == VodActivity.ROW_TRANSPORT;
        for (int k = 0; k < items.length; k++) {
            int item = items[k];
            boolean lit = row && a.focusItem() == k, enabled = a.transportEnabled(item);
            float cx = Ui.f(transportX(k, items.length)), cy = Ui.f(TRANSPORT_Y);
            float d = Ui.f(lit ? 88f : 72f);
            if (lit) {
                r.set(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f);
                Shadows.draw(c, r, d / 2f, Look07.SH_BUTTON, 1f);
                fill.setColor(Look07.FOCUS_FILL);
            } else {
                fill.setColor(0x14FFFFFF);
            }
            c.drawCircle(cx, cy, d / 2f, fill);
            int ink = lit ? Look07.INK : enabled ? 0xFFDADADF : Look07.L4;
            RoundButton.glyph(c, VodActivity.transportIcon(item, a.playingNow()), cx, cy, Ui.f(item == VodActivity.T_PLAY ? 38f : 34f), ink);
        }
    }

    /** The row of buttons: each an icon over its name and what it is set to; the remote's in a white circle. */
    private void drawButtons(Canvas c) {
        int[] items = a.buttons();
        boolean row = a.focusRow() == VodActivity.ROW_BUTTONS;
        float cy = Ui.f(BAR_Y);
        for (int k = 0; k < items.length; k++) {
            int item = items[k];
            boolean lit = row && a.focusItem() == k;
            float cx = Ui.f(barX(k, items.length));
            if (lit) {
                float d = Ui.f(68f);
                r.set(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f);
                Shadows.draw(c, r, d / 2f, Look07.SH_BUTTON, 1f);
                fill.setColor(Look07.FOCUS_FILL);
                c.drawCircle(cx, cy, d / 2f, fill);
            }
            RoundButton.glyph(c, VodActivity.buttonIcon(item), cx, cy, Ui.f(36f), lit ? Look07.INK : Look07.L2);
            labelP.setColor(lit ? Look07.L1 : Look07.L2);
            String name = VodActivity.BUTTON_NAMES[item];
            float base = cy + Ui.f(58f);
            c.drawText(name, cx - labelP.measureText(name) / 2f, base, labelP);
            String v = a.buttonValue(item, valueP, Ui.f(BAR_PITCH - 12f));
            if (!v.isEmpty()) {
                valueP.setColor(lit ? Look07.L2 : Look07.L3);
                c.drawText(v, cx - valueP.measureText(v) / 2f, base + Ui.f(26f), valueP);
            }
        }
    }

    /** The buttons' middles (design px) for n of them, centred on the screen. */
    static float barX(int k, int n) {
        return 960f + (k - (n - 1) / 2f) * BAR_PITCH;
    }

    /** Up next's card; true while its countdown runs (draw again soon). */
    private boolean drawUpNext(Canvas c) {
        Catalog.Episode next = a.upNextEpisode();
        if (next == null) return false;
        float x = Ui.f(NEXT_X), y = Ui.f(NEXT_FOOT - NEXT_H), w = Ui.f(NEXT_W), h = Ui.f(NEXT_H);
        r.set(x, y, x + w, y + h);
        Shadows.draw(c, r, Ui.f(Look07.R_PANEL), Look07.SH_PANEL, 1f);
        card.draw(c, r, Ui.f(Look07.R_PANEL), 1f);
        // Its picture at the left, 16:9.
        float pad = Ui.f(22f), pw = Ui.f(NEXT_PIC_W), ph = pw * 9f / 16f, px = x + pad, py = y + pad;
        String url = a.upNextPicture();
        Bitmap b = url.isEmpty() ? null : Pics.get(url, Math.round(pw), Math.round(ph), Pics.CROP, redraw);
        r.set(px, py, px + pw, py + ph);
        fill.setColor(Look07.CARD_FRAME);
        c.drawRoundRect(r, Ui.f(14f), Ui.f(14f), fill);
        if (b != null) {
            c.save();
            c.clipRect(r);
            c.drawBitmap(b, null, r, pic);
            c.restore();
        }
        float tx = px + pw + Ui.f(22f), room = x + w - pad - tx;
        int left = a.upNextSeconds();
        if (left != countFor) {
            countFor = left;
            countW = left >= 0 ? "UP NEXT  ·  " + left : "UP NEXT";
        }
        c.drawText(countW, tx, py + Ui.f(20f), kickerP);
        c.drawText(a.upNextTitle(nextP, room), tx, py + Ui.f(58f), nextP);
        String sub = a.upNextSub(nextSub, room);
        if (!sub.isEmpty()) c.drawText(sub, tx, py + Ui.f(90f), nextSub);
        String hint = left >= 0 ? "OK plays it now  ·  Back cancels" : "OK plays it  ·  Back goes back";
        c.drawText(Ui.fit(hint, hintP, w - pad * 2), px, y + h - Ui.f(38f), hintP);
        float share = a.upNextShare();
        if (share >= 0f) Pills.progress(c, px, y + h - Ui.f(18f), w - pad * 2, share);
        return left >= 0;
    }

    /** A short word at the top, on glass. */
    private void drawNote(Canvas c, int w, String note) {
        String n = Ui.fit(note, noteP, Ui.f(1400));
        float tw = noteP.measureText(n) + Ui.f(56f), hh = Ui.f(60f), x = (w - tw) / 2f, y = Ui.f(44f);
        r.set(x, y, x + tw, y + hh);
        card.draw(c, r, hh / 2f, 1f);
        c.drawText(n, x + Ui.f(28f), Type.centred(noteP, r.centerY()), noteP);
    }
}
