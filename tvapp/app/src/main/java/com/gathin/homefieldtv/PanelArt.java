package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Guide;

/**
 * The picture of what a panel is about, behind its words, as the family asked for the guide's details and the player's
 * info card: the programme's own picture (Sky's public guide, as the archive's card has it: ProgrammePictures), else the
 * channel's logo on a deep ground tinted by its own colour with a soft blurred glow of it (Pics.LOGO_ART). Either is
 * read off the main thread, at the size it is drawn (or a share of it), darkened a little, its left part and its foot
 * already faded to nothing and its right-hand corners rounded (Pics.ART), so it sinks into the panel's own colour and the
 * words over it stay readable; drawing it is one picture (two while they cross).
 *
 * <p>Moving to another programme or channel cross-fades to its picture in FADE_MS. Pictures queue rather than cut in:
 * a new one waits for the one fading in to finish (only the newest waits), so a held key cross-fades smoothly from
 * picture to picture and never pops. While a programme's picture is still to come, the channel's stand-in shows if it
 * is ready, the last picture stays for another programme on the same channel, and another channel's panel is left
 * plain rather than showing the wrong picture.
 *
 * <pre>
 *   PanelArt art = new PanelArt(view, 840f, 360f, 0.75f);   its box (design pixels) and the share it is read at
 *   art.show(channel, schedule, i, settled)                  what the panel is about; settled: the remote has rested, so
 *                                                            its picture may be asked for (else only what is known)
 *   art.prefetch(channel, schedule, i)                       a programme it may be about next, read ahead
 *   if (art.draw(canvas, x, y)) postInvalidateOnAnimation(); in the owner's onDraw (screen pixels); true while fading
 * </pre>
 */
final class PanelArt {
    static final long FADE_MS = 200;

    private final View owner;
    private final Runnable redraw;
    /** Its box (design pixels), and the size it is read at (screen pixels). */
    final float w, h;
    final int pw, ph;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    /** What it is about: the channel, and its programme's picture (its address; "" none; null: not known yet). */
    private Channel channel;
    private String pic = "";
    /** The pictures: the one fully in, the one fading in since inAt (null: fading to nothing), the one waiting its turn. */
    private Bitmap base, incoming, next;
    private String baseFor = "", incomingFor = "", nextFor = "";
    private boolean fading, queued;
    private long inAt;
    /** The programme last shown, asked again when its picture is known. */
    private Guide.Schedule askedS;
    private int askedI = -1;
    private final Runnable known = this::known;
    /** decide()'s answer: the picture to show (null: none), or keep what is shown. */
    private Bitmap want;
    private boolean keep;

    PanelArt(View owner, float w, float h, float read) {
        this.owner = owner;
        this.redraw = owner::invalidate;
        this.w = w;
        this.h = h;
        this.pw = Math.max(1, Ui.px(w * read));
        this.ph = Math.max(1, Ui.px(h * read));
    }

    /**
     * What the panel is about now: programme i of channel ch's schedule s (i -1: none), or no channel at all. settled:
     * the remote has rested on it, so its picture is asked for when it isn't known yet (previews and tests only use
     * what is known: nothing is fetched there).
     */
    void show(Channel ch, Guide.Schedule s, int i, boolean settled) {
        show(ch, s, i, settled, true);
    }

    /** As show(), drawing again only when redraw says (not from inside the owner's own drawing). */
    void show(Channel ch, Guide.Schedule s, int i, boolean settled, boolean redraw) {
        channel = ch;
        askedS = s;
        askedI = i;
        if (ch == null || s == null || i < 0 || i >= s.start.length) pic = "";
        else if (settled && !Exec.inline) pic = ProgrammePictures.get(owner.getContext(), ch, s, i, known);
        else pic = ProgrammePictures.peek(ch, s, i);
        if (redraw) owner.invalidate();
    }

    /** A programme's picture became known: the one shown now, if it still is. */
    private void known() {
        if (channel == null || askedS == null || askedI < 0 || askedI >= askedS.start.length) return;
        pic = ProgrammePictures.peek(channel, askedS, askedI);
        owner.invalidate();
    }

    /**
     * A programme the panel may be about next (the one after, the channel below): its picture's address asked for and
     * the picture read, and its channel's stand-in, after everything on screen, and kept; nothing is drawn.
     */
    void prefetch(Channel ch, Guide.Schedule s, int i) {
        if (ch == null || Exec.inline) return;
        if (!ch.logo.isEmpty()) Pics.prefetch(ch.logo, pw, ph, Pics.LOGO_ART);
        if (s == null || i < 0 || i >= s.start.length) return;
        String u = ProgrammePictures.get(owner.getContext(), ch, s, i, () -> {
            String got = ProgrammePictures.peek(ch, s, i);
            if (got != null && !got.isEmpty()) Pics.prefetch(got, pw, ph, Pics.ART);
        });
        if (u != null && !u.isEmpty()) Pics.prefetch(u, pw, ph, Pics.ART);
    }

    /** What it should show now (want, or keep what is shown); the pictures it needs asked for. */
    private void decide() {
        keep = false;
        want = null;
        if (channel == null) return;
        boolean coming = pic == null || !pic.isEmpty() && !Pics.failed(pic);
        if (pic != null && !pic.isEmpty() && !Pics.failed(pic)) {
            Bitmap b = Pics.get(pic, pw, ph, Pics.ART, redraw);
            if (b != null) {
                want = b;
                return;
            }
        }
        String logo = channel.logo;
        boolean same = channel.key.equals(newestFor());
        if (!logo.isEmpty() && !Pics.failed(logo)) {
            // The channel's stand-in: asked for at once when the programme has no picture, else read ahead quietly.
            Bitmap l;
            if (coming) {
                l = Pics.cached(logo, pw, ph, Pics.LOGO_ART);
                if (l == null) Pics.prefetch(logo, pw, ph, Pics.LOGO_ART);
            } else {
                l = Pics.get(logo, pw, ph, Pics.LOGO_ART, redraw);
            }
            if (l != null) {
                want = l;
                return;
            }
            if (!coming) {
                keep = same;
                return;
            }
        }
        // Still to come: the last picture stays for another of this channel's programmes, never for another channel.
        keep = coming && same;
    }

    private String newestFor() {
        return queued ? nextFor : fading ? incomingFor : baseFor;
    }

    /** The picture to show offered: it fades in now, or after the one fading in (only the newest waits). */
    private void offer(Bitmap b, String forKey, long now) {
        Bitmap newest = queued ? next : fading ? incoming : base;
        if (b == newest && (b == null || forKey.equals(newestFor()))) return;
        if (!fading) {
            if (b == base && b != null) {
                baseFor = forKey;
                return;
            }
            incoming = b;
            incomingFor = forKey;
            fading = true;
            inAt = now;
        } else {
            next = b;
            nextFor = forKey;
            queued = true;
        }
    }

    /** Whether a cross-fade is going on (the owner draws again next frame). */
    boolean busy() {
        return fading;
    }

    /**
     * Draws the panel's picture with its box's top left at (x, y) (screen pixels): the one fully in, and the one fading
     * in over it. True while a cross-fade goes on (the owner draws again next frame).
     */
    boolean draw(Canvas c, float x, float y) {
        long now = Exec.inline ? 0 : AnimationUtils.currentAnimationTimeMillis();
        decide();
        if (!keep) offer(want, channel == null ? "" : channel.key, now);
        float k = !fading || Exec.inline ? 1f : (now - inAt) / (float) FADE_MS;
        if (fading && k >= 1f) {
            base = incoming;
            baseFor = incomingFor;
            incoming = null;
            incomingFor = "";
            fading = false;
            if (queued) {
                queued = false;
                Bitmap n = next;
                String f = nextFor;
                next = null;
                nextFor = "";
                if (n != base) {
                    incoming = n;
                    incomingFor = f;
                    fading = true;
                    inAt = now;
                    k = Exec.inline ? 1f : 0f;
                }
                if (Exec.inline && fading) {
                    base = incoming;
                    baseFor = incomingFor;
                    incoming = null;
                    fading = false;
                }
            }
        }
        dst.set(x, y, x + Ui.f(w), y + Ui.f(h));
        float in = fading ? Fade.ease(k) : 1f;
        if (base != null) {
            paint.setAlpha(fading && incoming == null ? Math.round(255 * (1f - in)) : 255);
            c.drawBitmap(base, null, dst, paint);
        }
        if (fading && incoming != null) {
            paint.setAlpha(Math.round(255 * in));
            c.drawBitmap(incoming, null, dst, paint);
        }
        paint.setAlpha(255);
        return fading;
    }
}
