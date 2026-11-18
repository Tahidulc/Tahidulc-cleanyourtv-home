package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.LogoPack;

/**
 * A channel's cell in its logo's colour, as the family asked ("expand the channel logo colours into where it has the
 * channel number, the channel name"), drawn as the colour-rows mock-ups draw it (.cr): wherever a row shows a channel's
 * number, logo and name (the guide's channel column, the channel list, the mini guide, the shelf guide's labels), the
 * whole cell is filled with the logo's own row colour, a touch lighter and richer than its logo's tile (so the tile
 * still reads as a tile inside it) and always deep enough for white words, falling softly from top to foot, a line of
 * light along its top, a hairline round it and a soft shadow under it. The colours are the shared rule's (LogoColour,
 * the same on the phone and the launcher), worked out once when the logo was read (off the main thread: Logos); a
 * logo with no real colour, or one not read yet, gives the neutral slate's cell. Drawing it makes nothing new.
 *
 * Where the remote is, 07's focus on any colour: the cell lifts (scale 1.035), a 3 px white ring just outside it, a deep
 * shadow and a soft white glow. Settings › Appearance › Logos › Behind logos: None or Black tile give the plain 07 row:
 * nothing behind the words, and the white pill with dark words where the remote is.
 *
 * <pre>
 *   int words = ChannelLook.fillCell(c, cell, channel, focused);   cell: screen px; returns the colour for its number
 *                                                                  and name (white, or 07's dark ink on the white pill)
 *   ChannelLook.fillCell(c, cell, radius, channel, up)             its own corners (Look07.R_CHANNEL as it comes) and
 *                                                                  how far lifted (0 to 1: a Lift07's value, so the
 *                                                                  focus follows 07's springy ease)
 *   ChannelLook.lifted(cell, up, out)                              where the lifted cell is (to draw its words to scale)
 *   ChannelLook.colour(channel)                                    the cell's colour (its middle; 0: the plain row)
 * </pre>
 */
final class ChannelLook {
    private ChannelLook() {}

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), EDGE = new Paint(Paint.ANTI_ALIAS_FLAG),
            RING = new Paint(Paint.ANTI_ALIAS_FLAG), PILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF(), O = new RectF();

    static {
        EDGE.setStyle(Paint.Style.STROKE);
        RING.setStyle(Paint.Style.STROKE);
        PILL.setColor(Look07.FOCUS_FILL);
    }

    /** The cell's colours for a channel: its logo's (the slate's while it is read), or null for the plain 07 row. */
    static Logos.Tint tint(Channel ch) {
        if (Logos.behind() != Logos.COLOUR) return null;
        Logos.Tint t = ch == null ? null : Logos.tint(LogoPack.url(ch));
        return t == null ? Logos.Tint.WAITING : t;
    }

    /** The cell's colour (its middle), or 0 for the plain row (None, Black tile). */
    static int colour(Channel ch) {
        Logos.Tint t = tint(ch);
        return t == null ? 0 : t.cell;
    }

    static int fillCell(Canvas c, RectF cell, Channel ch, boolean focused) {
        return fillCell(c, cell, Ui.f(Look07.R_CHANNEL), ch, focused ? 1f : 0f);
    }

    static int fillCell(Canvas c, RectF cell, float radius, Channel ch, boolean focused) {
        return fillCell(c, cell, radius, ch, focused ? 1f : 0f);
    }

    /** Where the cell is drawn lifted by up (0 to 1): grown about its middle. */
    static RectF lifted(RectF cell, float up, RectF out) {
        float k = Math.max(0f, Math.min(1.2f, up)), g = (Look07.CHANNEL_LIFT - 1f) * k;
        float dx = cell.width() * g / 2f, dy = cell.height() * g / 2f;
        out.set(cell.left - dx, cell.top - dy, cell.right + dx, cell.bottom + dy);
        return out;
    }

    /**
     * The channel's cell, lifted by up (0 resting, 1 where the remote is; a Lift07's value between); returns the colour to
     * write its number and name in.
     */
    static int fillCell(Canvas c, RectF cell, float radius, Channel ch, float up) {
        float k = Math.max(0f, Math.min(1f, up));
        Logos.Tint t = tint(ch);
        if (t == null) {
            // The plain 07 row: the white pill where the remote is.
            if (k <= 0f) return Look07.L1;
            Shadows.draw(c, cell, radius, Look07.SH_ROW, k);
            PILL.setAlpha(Math.round((Look07.FOCUS_FILL >>> 24) * k));
            c.drawRoundRect(cell, radius, radius, PILL);
            return k >= 0.5f ? Look07.INK : Look07.L1;
        }
        RectF r = lifted(cell, up, O);
        float rad = radius * (r.height() / Math.max(1f, cell.height()));
        boolean slate = t.neutral;
        // Under it: its soft shadow, deep and with a white glow as it lifts.
        Shadows.draw(c, r, rad, Look07.SH_CHANNEL, (slate ? 0.8f : 1f) * (1f - k));
        if (k > 0f) {
            Shadows.draw(c, r, rad, Look07.SH_CHANNEL_UP, k);
            Shadows.glow(c, r, rad, Look07.GLOW_CHANNEL, k);
        }
        // The line of light along its top (CSS: inset 0 1px 0): the cell in that light, then its fall a pixel lower.
        float sw = Math.max(1f, Ui.f(1f)), h = Math.max(1f, r.height() - sw);
        FILL.setShader(null);
        FILL.setColor(Theme.mix(t.cellTop, 0xFFFFFFFF, ((slate ? Look07.SLATE_TOP_LIGHT : Look07.CHANNEL_TOP_LIGHT) >>> 24) / 255f) | 0xFF000000);
        c.drawRoundRect(r, rad, rad, FILL);
        // Its fall, stretched from a unit box (its corners kept round).
        FILL.setShader(t.cellFall);
        c.save();
        c.translate(r.left, r.top + sw);
        c.scale(1f, h);
        c.drawRoundRect(0, 0, r.width(), 1f, rad, rad / h, FILL);
        c.restore();
        FILL.setShader(null);
        // Its hairline (inset 0 0 0 1px).
        EDGE.setStrokeWidth(sw);
        EDGE.setColor(slate ? Look07.SLATE_EDGE : Look07.CHANNEL_EDGE);
        R.set(r.left + sw / 2f, r.top + sw / 2f, r.right - sw / 2f, r.bottom - sw / 2f);
        c.drawRoundRect(R, rad, rad, EDGE);
        if (k > 0f) {
            // 07's focus on any colour: a 3 px white ring just outside.
            float rw = Ui.f(Look07.CHANNEL_RING), out = rw / 2f;
            R.set(r.left - out, r.top - out, r.right + out, r.bottom + out);
            RING.setStrokeWidth(rw);
            RING.setColor(Palette.alpha(Look07.L1, Math.round(255 * k)));
            c.drawRoundRect(R, rad + out, rad + out, RING);
        }
        return Look07.L1;
    }
}
